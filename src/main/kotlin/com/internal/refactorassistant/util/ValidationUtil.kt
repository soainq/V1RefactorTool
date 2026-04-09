package com.internal.refactorassistant.util

import java.util.regex.Pattern

object ValidationUtil {
    private val packageSegmentPattern = Pattern.compile("[a-z][a-z0-9_]*")
    private val identifierPattern = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*")
    private val resourcePattern = Pattern.compile("[a-z][a-z0-9_]*")

    fun isValidPackageName(value: String): Boolean {
        if (value.isBlank()) return false
        return value.split('.').all { segment -> packageSegmentPattern.matcher(segment).matches() }
    }

    fun isValidIdentifier(value: String): Boolean = identifierPattern.matcher(value).matches()

    fun isValidAndroidResourceName(value: String): Boolean = resourcePattern.matcher(value).matches()

    fun sanitizePathSegment(value: String): String = value.trim().replace('\\', '/')
}
