package com.internal.refactorassistant.rules

object TokenUtil {
    private val camelCaseBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])")
    private val nonAlphaNumeric = Regex("[^A-Za-z0-9]+")

    fun tokenize(value: String): List<String> {
        if (value.isBlank()) return emptyList()

        val normalized = value
            .replace(camelCaseBoundary, "_")
            .replace(nonAlphaNumeric, "_")
            .trim('_')

        if (normalized.isBlank()) return emptyList()
        return normalized.split('_').filter { it.isNotBlank() }
    }
}
