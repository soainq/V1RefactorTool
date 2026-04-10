package com.internal.refactorassistant.rules

import com.internal.refactorassistant.model.RefactorItemType
import java.util.Locale

object NamingRules {
    val androidResFolders: Set<String> = setOf("drawable", "layout", "values", "xml", "mipmap", "font")
    val protectedPackageSegments: Set<String> = setOf(
        "core", "common", "base", "data", "domain", "di", "model", "models", "util", "utils", "repository", "repositories", "network"
    )

    fun splitWords(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value
            .replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_")
            .replace(Regex("[^A-Za-z0-9.]+"), "_")
            .trim('_')
            .split('_')
            .flatMap { part -> part.split('.') }
            .filter { it.isNotBlank() }
    }

    fun toPascalCase(words: List<String>): String =
        words.joinToString("") { word -> word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) } }

    fun toSnakeCase(words: List<String>): String =
        words.joinToString("_") { it.lowercase(Locale.US) }

    fun isValidName(type: RefactorItemType, value: String): Boolean = when (type) {
        RefactorItemType.LAYOUT,
        RefactorItemType.DRAWABLE,
        RefactorItemType.STRING,
        RefactorItemType.DIMEN,
        -> Regex("^[a-z][a-z0-9_]*$").matches(value)

        RefactorItemType.PACKAGE_CHILD -> Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$").matches(value)
        else -> Regex("^[A-Z][A-Za-z0-9_]*$").matches(value)
    }

    fun normalizeSuggestion(type: RefactorItemType, value: String): String {
        val words = splitWords(value)
        return when (type) {
            RefactorItemType.LAYOUT,
            RefactorItemType.DRAWABLE,
            RefactorItemType.STRING,
            RefactorItemType.DIMEN,
            -> toSnakeCase(words)

            RefactorItemType.PACKAGE_CHILD -> words.joinToString(".") { it.lowercase(Locale.US) }
            else -> toPascalCase(words)
        }
    }
}
