package com.internal.refactorassistant.util

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object FileUtilEx {
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun createReportFile(baseDirectory: Path, prefix: String, extension: String): Path {
        Files.createDirectories(baseDirectory)
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        return baseDirectory.resolve("$prefix-$timestamp.$extension")
    }

    fun writeUtf8(path: Path, content: String): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, content, StandardCharsets.UTF_8)
        return path
    }
}
