package com.internal.refactorassistant.history

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object JsonStorage {
    @PublishedApi
    internal val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun <T> read(path: Path, clazz: Class<T>): T = mapper.readValue(path.toFile(), clazz)

    inline fun <reified T> read(path: Path): T = mapper.readValue(path.toFile())

    fun <T> readOrDefault(path: Path, defaultValue: T, clazz: Class<T>): T {
        if (!Files.exists(path)) return defaultValue
        return runCatching { read(path, clazz) }.getOrElse { defaultValue }
    }

    inline fun <reified T> readOrDefault(path: Path, defaultValue: T): T {
        if (!Files.exists(path)) return defaultValue
        return runCatching { read<T>(path) }.getOrElse { defaultValue }
    }

    fun write(path: Path, value: Any) {
        Files.createDirectories(path.parent)
        Files.writeString(
            path,
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value),
            StandardCharsets.UTF_8,
        )
    }
}
