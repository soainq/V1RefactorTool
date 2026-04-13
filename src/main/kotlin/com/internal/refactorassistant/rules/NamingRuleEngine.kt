package com.internal.refactorassistant.rules

import com.internal.refactorassistant.model.CustomNamingRule
import com.internal.refactorassistant.model.RefactorRequest
import com.internal.refactorassistant.model.RenameTargetKind
import com.internal.refactorassistant.model.SynonymDictionary
import java.util.Locale

class NamingRuleEngine(
    request: RefactorRequest,
    synonymDictionary: SynonymDictionary,
) {
    private val request: RefactorRequest = request
    private val rules: List<CustomNamingRule> = request.customNamingRules.filter { it.enabled }
    private val tokenMap: Map<String, String> = buildTokenMap(request, synonymDictionary)

    fun renamePackageName(value: String): String {
        val updated = if (value == request.oldPackagePrefix) {
            request.newPackagePrefix
        } else {
            value.replace(request.oldPackagePrefix, request.newPackagePrefix)
        }
        return updated.split('.')
            .map { replaceTokensInTokenStream(listOf(it)).singleOrNull().orEmpty() }
            .joinToString(".") { it.lowercase(Locale.US) }
            .let { applyRegexRules(it, RenameTargetKind.PACKAGE) }
    }

    fun renameClassName(value: String): String = toPascalCase(replaceTokens(value, RenameTargetKind.KOTLIN_CLASS))

    fun renameFileStem(value: String): String = toPascalCase(replaceTokens(value, RenameTargetKind.KOTLIN_FILE))

    fun renameVariableOrFunction(value: String, targetKind: RenameTargetKind): String =
        toCamelCase(replaceTokens(value, targetKind))

    fun renameResourceName(value: String, targetKind: RenameTargetKind): String =
        toSnakeCase(replaceTokens(value, targetKind))

    fun wouldChange(value: String, renamed: String): Boolean = value.isNotBlank() && renamed.isNotBlank() && value != renamed

    private fun replaceTokens(value: String, targetKind: RenameTargetKind): String {
        val direct = applyTokenMap(value)
        return applyRegexRules(direct, targetKind)
    }

    private fun applyRegexRules(value: String, targetKind: RenameTargetKind): String {
        return rules
            .filter { targetKind in it.targets || it.targets.isEmpty() }
            .fold(value) { current, rule ->
                current.replace(Regex(rule.pattern), rule.replacement)
            }
    }

    private fun applyTokenMap(value: String): String {
        if (value.isBlank()) return value
        val normalized = replaceTokensInTokenStream(TokenUtil.tokenize(value))
        return when {
            value.contains('_') || value.contains('-') -> normalized.joinToString("_")
            value.contains('.') -> normalized.joinToString(".")
            else -> normalized.joinToString("")
        }
    }

    private fun replaceTokensInTokenStream(tokens: List<String>): List<String> {
        return tokens.map { token ->
            tokenMap.entries.firstOrNull { (oldToken, _) -> token.equals(oldToken, ignoreCase = true) }
                ?.let { (_, replacement) -> replacement.adaptTo(token) }
                ?: token
        }
    }

    private fun buildTokenMap(request: RefactorRequest, synonymDictionary: SynonymDictionary): Map<String, String> {
        val defaults = linkedMapOf(
            request.oldFeatureName to request.newFeatureName,
            request.oldDisplayName to request.newDisplayName,
        )
        return defaults + synonymDictionary.tokens
    }

    companion object {
        private fun String.adaptTo(template: String): String = when {
            template.all { it.isUpperCase() } -> uppercase(Locale.US)
            template.firstOrNull()?.isUpperCase() == true -> replaceFirstChar { it.uppercase(Locale.US) }
            else -> lowercase(Locale.US)
        }

        fun toPascalCase(value: String): String =
            TokenUtil.tokenize(value).joinToString("") { token -> token.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) } }

        fun toCamelCase(value: String): String =
            TokenUtil.tokenize(value).mapIndexed { index, token ->
                val normalized = token.lowercase(Locale.US)
                if (index == 0) normalized else normalized.replaceFirstChar { it.uppercase(Locale.US) }
            }.joinToString("")

        fun toSnakeCase(value: String): String =
            TokenUtil.tokenize(value).joinToString("_") { token -> token.lowercase(Locale.US) }
    }
}
