package com.internal.refactorassistant.suggest

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.model.ScannedRefactorItem
import com.internal.refactorassistant.model.SafetyLevel
import com.internal.refactorassistant.model.SuggestionCandidate
import com.internal.refactorassistant.model.UsedNameEntry
import com.internal.refactorassistant.model.UsedNameMetadata
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.rules.NamingRules
import java.util.Locale

class SuggestionService {
    fun buildReviewItems(
        items: List<ScannedRefactorItem>,
        registry: UsedNamesRegistry,
        showPreviouslyUsedNames: Boolean,
    ): List<ReviewItemState> {
        return items.map { item ->
            val suggestions = generateSuggestions(item, registry, showPreviouslyUsedNames)
            val selected = suggestions.firstOrNull { !it.usedMetadata.usedBefore }?.value
                ?: suggestions.firstOrNull()?.value
                ?: ""
            val applyByDefault = item.safetyLevel == SafetyLevel.SAFE_AUTO &&
                selected.isNotBlank() &&
                suggestions.firstOrNull { it.value == selected }?.usedMetadata?.usedBefore != true

            ReviewItemState(
                item = item,
                suggestions = suggestions,
                selectedNewName = selected,
                applySelected = applyByDefault && item.safetyLevel != SafetyLevel.DO_NOT_TOUCH,
                status = when (item.safetyLevel) {
                    SafetyLevel.SAFE_AUTO -> "Ready"
                    SafetyLevel.REVIEW_REQUIRED -> "Review"
                    SafetyLevel.DO_NOT_TOUCH -> "Blocked"
                },
                warning = when {
                    item.safetyLevel == SafetyLevel.DO_NOT_TOUCH -> "Protected item."
                    suggestions.isEmpty() -> "No safe suggestion available from the dictionary."
                    suggestions.all { it.usedMetadata.usedBefore } && !showPreviouslyUsedNames -> "All suggestions were filtered because they were used before."
                    else -> ""
                },
            )
        }
    }

    private fun generateSuggestions(
        item: ScannedRefactorItem,
        registry: UsedNamesRegistry,
        showPreviouslyUsedNames: Boolean,
    ): List<SuggestionCandidate> {
        val rawSuggestions = when (item.type) {
            RefactorItemType.ACTIVITY,
            RefactorItemType.FRAGMENT,
            RefactorItemType.VIEWMODEL,
            RefactorItemType.ADAPTER,
            RefactorItemType.KOTLIN_FILE,
            RefactorItemType.KOTLIN_CLASS,
            -> suggestTypeName(item.oldName, item.type)

            RefactorItemType.LAYOUT,
            RefactorItemType.DRAWABLE,
            RefactorItemType.STRING,
            RefactorItemType.DIMEN,
            -> suggestResourceName(item.oldName, item.type)

            RefactorItemType.PACKAGE_CHILD,
            -> suggestPackageName(item.oldName, item.details.rootPackage.orEmpty())
        }

        return rawSuggestions
            .map { NamingRules.normalizeSuggestion(item.type, it) }
            .filter { it.isNotBlank() && it != item.oldName && NamingRules.isValidName(item.type, it) }
            .distinct()
            .map { suggestion ->
                val usedEntry = registry.namesByType[item.type].orEmpty().firstOrNull { it.name.equals(suggestion, ignoreCase = true) }
                SuggestionCandidate(
                    value = suggestion,
                    usedMetadata = usedEntry.toMetadata(),
                )
            }
            .filter { showPreviouslyUsedNames || !it.usedMetadata.usedBefore }
            .take(5)
    }

    private fun suggestTypeName(oldName: String, type: RefactorItemType): List<String> {
        val suffix = listOf("Activity", "Fragment", "ViewModel", "Adapter").firstOrNull { oldName.endsWith(it) }.orEmpty()
        val base = if (suffix.isNotBlank()) oldName.removeSuffix(suffix) else oldName
        val replacements = replaceUsingDictionary(base)
        return replacements.map { replacement ->
            when (type) {
                RefactorItemType.KOTLIN_FILE -> if (suffix.isNotBlank()) replacement + suffix else replacement
                else -> replacement + suffix
            }
        }
    }

    private fun suggestResourceName(oldName: String, type: RefactorItemType): List<String> {
        val tokens = oldName.split('_')
        return replaceSnakeTokens(tokens).map { replacement ->
            when (type) {
                RefactorItemType.LAYOUT,
                RefactorItemType.DRAWABLE,
                RefactorItemType.STRING,
                RefactorItemType.DIMEN,
                -> replacement

                else -> replacement
            }
        }
    }

    private fun suggestPackageName(oldPackage: String, rootPackage: String): List<String> {
        val suffix = if (rootPackage.isNotBlank() && oldPackage.startsWith("$rootPackage.")) {
            oldPackage.removePrefix("$rootPackage.")
        } else {
            oldPackage
        }
        return replacePackageTokens(suffix).map { replacement ->
            if (rootPackage.isNotBlank()) "$rootPackage.$replacement" else replacement
        }
    }

    private fun replaceUsingDictionary(value: String): List<String> {
        val words = NamingRules.splitWords(value)
        return replaceWords(words).map { NamingRules.toPascalCase(it) }
    }

    private fun replaceSnakeTokens(tokens: List<String>): List<String> =
        replaceWords(tokens).map { NamingRules.toSnakeCase(it) }

    private fun replacePackageTokens(value: String): List<String> {
        val segments = value.split('.')
        return replaceWords(segments).map { segmentsList ->
            segmentsList.joinToString(".") { it.lowercase(Locale.US) }
        }
    }

    private fun replaceWords(words: List<String>): List<List<String>> {
        if (words.isEmpty()) return emptyList()
        val suggestions = mutableListOf<List<String>>()
        words.forEachIndexed { index, word ->
            val candidates = BuiltinDictionary.mappings[word.lowercase(Locale.US)].orEmpty()
            candidates.forEach { candidate ->
                val updated = words.toMutableList()
                updated[index] = candidate
                suggestions += updated
            }
        }
        return suggestions
    }

    private fun UsedNameEntry?.toMetadata(): UsedNameMetadata = if (this == null) {
        UsedNameMetadata(false, null, null)
    } else {
        UsedNameMetadata(true, lastUsedVersion, lastUsedTimestamp)
    }
}
