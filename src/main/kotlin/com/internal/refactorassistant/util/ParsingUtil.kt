package com.internal.refactorassistant.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.internal.refactorassistant.model.CustomNamingRule
import com.internal.refactorassistant.model.RefactorPlan
import com.internal.refactorassistant.model.SynonymDictionary

object ParsingUtil {
    private val jsonMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun parseSynonymDictionary(raw: String): SynonymDictionary {
        val content = raw.trim()
        if (content.isEmpty()) {
            return SynonymDictionary()
        }

        val parsed = when {
            content.startsWith("{") -> parseMap(jsonMapper, content)
            content.startsWith("tokens:") || content.contains(":") -> parseMap(yamlMapper, content)
            else -> emptyMap()
        }

        val tokens = when {
            parsed["tokens"] is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (parsed["tokens"] as Map<String, Any?>).mapValues { (_, value) -> value?.toString().orEmpty() }
            }
            else -> parsed.filterValues { it != null }.mapValues { it.value.toString() }
        }

        return SynonymDictionary(tokens = tokens.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() })
    }

    fun parseCustomRules(raw: String): List<CustomNamingRule> {
        val content = raw.trim()
        if (content.isEmpty()) {
            return emptyList()
        }

        return runCatching {
            jsonMapper.readValue(content, object : TypeReference<List<CustomNamingRule>>() {})
        }.getOrElse {
            yamlMapper.readValue(content, object : TypeReference<List<CustomNamingRule>>() {})
        }
    }

    fun toPrettyJson(value: Any): String = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)

    fun planToJson(plan: RefactorPlan): String = toPrettyJson(plan)

    private fun parseMap(mapper: ObjectMapper, content: String): Map<String, Any?> {
        return mapper.readValue(content, object : TypeReference<Map<String, Any?>>() {})
    }
}
