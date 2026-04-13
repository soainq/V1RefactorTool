package com.internal.refactorassistant.suggest

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.model.ScannedRefactorItem
import com.internal.refactorassistant.model.SafetyLevel
import com.internal.refactorassistant.model.SuggestionCandidate
import com.internal.refactorassistant.model.SuggestionSource
import com.internal.refactorassistant.model.UsedNameEntry
import com.internal.refactorassistant.model.UsedNameMetadata
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.rules.NamingRules
import java.util.LinkedHashSet
import java.util.Locale

class SuggestionService {
    private val normalizer = CandidateNormalizer()
    private val qualityValidator = CandidateQualityValidator()
    private val selector = SuggestionCandidateSelector()

    fun buildReviewItems(
        items: List<ScannedRefactorItem>,
        registry: UsedNamesRegistry,
        existingNamesByType: Map<RefactorItemType, Set<String>>,
        showPreviouslyUsedNames: Boolean,
    ): List<ReviewItemState> {
        val reservedNamesByType = mutableMapOf<RefactorItemType, MutableSet<String>>()
        val groupedItems = items.groupBy(::groupKeyFor)
        return groupedItems.entries.flatMap { (groupKey, groupItems) ->
            val representative = groupItems.first()
            val allSuggestions = generateSuggestions(representative, registry)
            val visibleSuggestions = allSuggestions
                .filter { showPreviouslyUsedNames || !it.usedMetadata.usedBefore }
                .take(6)
            val reservedNames = reservedNamesByType.getOrPut(representative.type) { linkedSetOf() }
            val canonicalCandidate = selector.pickBestCandidate(representative, allSuggestions, existingNamesByType, reservedNames)
            val canonicalName = canonicalCandidate?.value.orEmpty()
            val canonicalSource = canonicalCandidate?.source
            if (canonicalName.isNotBlank()) {
                reservedNames += canonicalName.lowercase(Locale.US)
            }

            groupItems.map { item ->
                val selectedCandidate = selector.pickBestCandidate(item, allSuggestions, existingNamesByType, emptySet(), preferredName = canonicalName)
                    ?: selector.pickBestCandidate(item, allSuggestions, existingNamesByType, emptySet())
                val selected = selectedCandidate?.value.orEmpty()
                val selectedSource = selectedCandidate?.source ?: canonicalSource
                val applyByDefault = item.safetyLevel == SafetyLevel.SAFE_AUTO &&
                    selected.isNotBlank() &&
                    selectedCandidate?.usedMetadata?.usedBefore != true

                ReviewItemState(
                    item = item,
                    suggestions = visibleSuggestions,
                    groupKey = groupKey,
                    canonicalNewName = canonicalName,
                    groupSize = groupItems.size,
                    overrideApplied = canonicalName.isNotBlank() && selected.isNotBlank() && !selected.equals(canonicalName, ignoreCase = true),
                    selectedNewName = selected,
                    selectedSuggestionSource = selectedSource,
                    applySelected = applyByDefault && item.safetyLevel != SafetyLevel.DO_NOT_TOUCH,
                    status = when (item.safetyLevel) {
                        SafetyLevel.DO_NOT_TOUCH -> "SKIPPED"
                        else -> if (selected.isNotBlank()) "READY" else "BLOCKED"
                    },
                    warning = when {
                        item.safetyLevel == SafetyLevel.DO_NOT_TOUCH -> "Protected item."
                        allSuggestions.isEmpty() -> "No acceptable replacement candidate"
                        selected.isBlank() -> "Blocked after all acceptable candidates conflicted with existing names, path targets, or history."
                        visibleSuggestions.isEmpty() && !showPreviouslyUsedNames -> "All suggestions were filtered because they were used before."
                        selectedCandidate?.normalizationNote != null -> selectedCandidate.normalizationNote
                        selectedSource == SuggestionSource.TOKEN_ABBREVIATION -> "Compressed by abbreviation"
                        selectedSource == SuggestionSource.EXACT_PHRASE ||
                            selectedSource == SuggestionSource.TOKEN_SYNONYM ||
                            selectedSource == SuggestionSource.WHOLE_PHRASE_REPLACEMENT -> "Replaced business phrase"
                        canonicalName.isNotBlank() && !selected.equals(canonicalName, ignoreCase = true) -> "Using a non-canonical candidate because the canonical name conflicts for this item."
                        selectedSource == SuggestionSource.RULE_FALLBACK -> "No acceptable replacement candidate"
                        else -> ""
                    },
                    providerUsed = "rule-based",
                )
            }
        }
    }

    private fun generateSuggestions(
        item: ScannedRefactorItem,
        registry: UsedNamesRegistry,
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
            -> suggestResourceName(item.oldName)

            RefactorItemType.PACKAGE_CHILD,
            -> suggestPackageName(item.oldName, item.details.rootPackage.orEmpty())
        }

        val bestByValue = linkedMapOf<String, SuggestionCandidate>()
        rawSuggestions.forEach { candidate ->
            val normalizedCandidate = normalizer.normalize(item.type, candidate.value)
            val normalized = normalizedCandidate.value
            if (normalized.isBlank() || normalized == item.oldName || !NamingRules.isValidName(item.type, normalized)) return@forEach
            val quality = qualityValidator.validate(item.type, item.oldName, normalized, candidate.source)
            if (!quality.acceptable) return@forEach
            val usedEntry = registry.namesByType[item.type].orEmpty().firstOrNull { it.name.equals(normalized, ignoreCase = true) }
            val suggestion = SuggestionCandidate(
                value = normalized,
                rawValue = candidate.value,
                normalizationNote = normalizedCandidate.note,
                usedMetadata = usedEntry.toMetadata(),
                source = candidate.source,
            )
            val existing = bestByValue[normalized]
            if (existing == null || suggestion.source.priority < existing.source.priority) {
                bestByValue[normalized] = suggestion
            }
        }
        return bestByValue.values
            .sortedBy { it.source.priority }
    }

    private fun suggestTypeName(oldName: String, type: RefactorItemType): List<RawSuggestion> {
        val words = NamingRules.splitWords(oldName)
        if (words.isEmpty()) return emptyList()
        val nameParts = NameStructureRules.splitByType(type, words)
        val businessWords = nameParts.business
        if (businessWords.isEmpty()) return emptyList()

        return buildSuggestions(
            businessWords = businessWords,
            protectedLeadingWords = nameParts.leading,
            protectedTrailingWords = nameParts.trailing,
            format = NameFormat.PASCAL,
            fallbackWords = fallbackWordsForType(type),
            respectDoNotReplace = false,
        )
    }

    private fun suggestResourceName(oldName: String): List<RawSuggestion> {
        val words = NamingRules.splitWords(oldName)
        if (words.isEmpty()) return emptyList()
        val nameParts = NameStructureRules.splitByType(RefactorItemType.LAYOUT, words)
        val businessWords = nameParts.business
        if (businessWords.isEmpty()) return emptyList()

        return buildSuggestions(
            businessWords = businessWords,
            protectedLeadingWords = nameParts.leading,
            protectedTrailingWords = nameParts.trailing,
            format = NameFormat.SNAKE,
            fallbackWords = emptyList(),
            respectDoNotReplace = false,
        )
    }

    private fun suggestPackageName(oldPackage: String, rootPackage: String): List<RawSuggestion> {
        val suffix = if (rootPackage.isNotBlank() && oldPackage.startsWith("$rootPackage.")) {
            oldPackage.removePrefix("$rootPackage.")
        } else {
            oldPackage
        }
        val words = NamingRules.splitWords(suffix)
        if (words.isEmpty()) return emptyList()
        val protectedLeading = words.takeWhile { it.lowercase(Locale.US) in NamingRules.protectedPackageSegments }
        val businessWords = words.drop(protectedLeading.size)
        if (businessWords.isEmpty()) return emptyList()

        val suggestions = buildSuggestions(
            businessWords = businessWords,
            protectedLeadingWords = protectedLeading,
            protectedTrailingWords = emptyList(),
            format = NameFormat.PACKAGE,
            fallbackWords = listOf("feature", "module", "flow"),
            respectDoNotReplace = true,
        )
        return suggestions.map { suggestion ->
            if (rootPackage.isBlank()) suggestion else suggestion.copy(value = "$rootPackage.${suggestion.value}")
        }
    }

    private fun buildSuggestions(
        businessWords: List<String>,
        protectedLeadingWords: List<String>,
        protectedTrailingWords: List<String>,
        format: NameFormat,
        fallbackWords: List<String>,
        respectDoNotReplace: Boolean,
    ): List<RawSuggestion> {
        val sink = linkedSetOf<RawSuggestion>()

        addExactPhraseCandidates(sink, businessWords, protectedLeadingWords, protectedTrailingWords, format, respectDoNotReplace)
        addTokenSynonymCandidates(sink, businessWords, protectedLeadingWords, protectedTrailingWords, format, respectDoNotReplace)
        addTokenAbbreviationCandidates(sink, businessWords, protectedLeadingWords, protectedTrailingWords, format, respectDoNotReplace)
        addWholePhraseCandidates(sink, businessWords, protectedLeadingWords, protectedTrailingWords, format, respectDoNotReplace)
        if (fallbackWords.isNotEmpty()) {
            addFallbackCandidates(sink, businessWords, protectedLeadingWords, protectedTrailingWords, format, fallbackWords)
        }

        return sink.toList()
    }

    private fun addExactPhraseCandidates(
        sink: MutableSet<RawSuggestion>,
        businessWords: List<String>,
        protectedLeadingWords: List<String>,
        protectedTrailingWords: List<String>,
        format: NameFormat,
        respectDoNotReplace: Boolean,
    ) {
        buildPhraseRangeVariants(
            businessWords = businessWords,
            replacements = BuiltinDictionary.exactPhraseSynonyms,
            respectDoNotReplace = respectDoNotReplace,
        ).forEach { candidateWords ->
            sink += RawSuggestion(
                value = formatWords(protectedLeadingWords + candidateWords + protectedTrailingWords, format),
                source = SuggestionSource.EXACT_PHRASE,
            )
        }
    }

    private fun addTokenSynonymCandidates(
        sink: MutableSet<RawSuggestion>,
        businessWords: List<String>,
        protectedLeadingWords: List<String>,
        protectedTrailingWords: List<String>,
        format: NameFormat,
        respectDoNotReplace: Boolean,
    ) {
        val options = businessWords.map { word ->
            if (respectDoNotReplace && isDoNotReplace(word)) {
                emptyList()
            } else {
                (BuiltinDictionary.tokenSynonyms[word.lowercase(Locale.US)].orEmpty() +
                    BuiltinDictionary.localeAliases[word.lowercase(Locale.US)].orEmpty())
                .map { replacement -> NamingRules.splitWords(replacement) }
                .filter { it.isNotEmpty() }
            }
        }

        if (options.none { it.isNotEmpty() }) return

        val combinations = LinkedHashSet<List<String>>()
        buildTokenCombinations(
            words = businessWords,
            options = options,
            index = 0,
            replacementsUsed = 0,
            current = emptyList(),
            sink = combinations,
            limit = 12,
        )

        combinations
            .filter { isNaturalTokenSuggestion(it.originalWordCount(businessWords), it.replacedWordCount(businessWords)) }
            .forEach { candidateWords ->
                sink += RawSuggestion(
                    value = formatWords(protectedLeadingWords + candidateWords + protectedTrailingWords, format),
                    source = SuggestionSource.TOKEN_SYNONYM,
                )
            }
    }

    private fun addWholePhraseCandidates(
        sink: MutableSet<RawSuggestion>,
        businessWords: List<String>,
        protectedLeadingWords: List<String>,
        protectedTrailingWords: List<String>,
        format: NameFormat,
        respectDoNotReplace: Boolean,
    ) {
        buildPhraseRangeVariants(
            businessWords = businessWords,
            replacements = BuiltinDictionary.wholePhraseReplacements,
            respectDoNotReplace = respectDoNotReplace,
        ).forEach { candidateWords ->
            sink += RawSuggestion(
                value = formatWords(protectedLeadingWords + candidateWords + protectedTrailingWords, format),
                source = SuggestionSource.WHOLE_PHRASE_REPLACEMENT,
            )
        }
    }

    private fun addTokenAbbreviationCandidates(
        sink: MutableSet<RawSuggestion>,
        businessWords: List<String>,
        protectedLeadingWords: List<String>,
        protectedTrailingWords: List<String>,
        format: NameFormat,
        respectDoNotReplace: Boolean,
    ) {
        val options = businessWords.map { word ->
            if (respectDoNotReplace && isDoNotReplace(word)) {
                emptyList()
            } else {
                BuiltinDictionary.abbreviationSynonyms[word.lowercase(Locale.US)]
                .orEmpty()
                .map { replacement -> NamingRules.splitWords(replacement) }
                .filter { it.isNotEmpty() }
            }
        }

        if (options.none { it.isNotEmpty() }) return

        val combinations = LinkedHashSet<List<String>>()
        buildTokenCombinations(
            words = businessWords,
            options = options,
            index = 0,
            replacementsUsed = 0,
            current = emptyList(),
            sink = combinations,
            limit = 12,
        )

        combinations.forEach { candidateWords ->
            sink += RawSuggestion(
                value = formatWords(protectedLeadingWords + candidateWords + protectedTrailingWords, format),
                source = SuggestionSource.TOKEN_ABBREVIATION,
            )
        }
    }

    private fun addFallbackCandidates(
        sink: MutableSet<RawSuggestion>,
        businessWords: List<String>,
        protectedLeadingWords: List<String>,
        protectedTrailingWords: List<String>,
        format: NameFormat,
        fallbackWords: List<String>,
    ) {
        fallbackWords.forEach { fallback ->
            sink += RawSuggestion(
                value = formatWords(protectedLeadingWords + businessWords + listOf(fallback) + protectedTrailingWords, format),
                source = SuggestionSource.RULE_FALLBACK,
            )
        }
    }

    private fun buildTokenCombinations(
        words: List<String>,
        options: List<List<List<String>>>,
        index: Int,
        replacementsUsed: Int,
        current: List<String>,
        sink: MutableSet<List<String>>,
        limit: Int,
    ) {
        if (sink.size >= limit) return
        if (index >= words.size) {
            if (replacementsUsed > 0) {
                sink += current
            }
            return
        }

        buildTokenCombinations(
            words = words,
            options = options,
            index = index + 1,
            replacementsUsed = replacementsUsed,
            current = current + words[index],
            sink = sink,
            limit = limit,
        )

        options[index].forEach { replacementWords ->
            if (sink.size >= limit) return
            buildTokenCombinations(
                words = words,
                options = options,
                index = index + 1,
                replacementsUsed = replacementsUsed + 1,
                current = current + replacementWords,
                sink = sink,
                limit = limit,
            )
        }
    }

    private fun isNaturalTokenSuggestion(originalCount: Int, replacedCount: Int): Boolean {
        if (replacedCount <= 0) return false
        if (originalCount <= 2) return true
        return replacedCount * 2 >= originalCount
    }

    private fun List<String>.originalWordCount(originalWords: List<String>): Int = originalWords.size

    private fun List<String>.replacedWordCount(originalWords: List<String>): Int =
        originalWords.zip(this).count { (oldWord, newWord) -> !oldWord.equals(newWord, ignoreCase = true) }

    private fun buildPhraseRangeVariants(
        businessWords: List<String>,
        replacements: Map<String, List<String>>,
        respectDoNotReplace: Boolean,
    ): Set<List<String>> {
        val results = LinkedHashSet<List<String>>()
        val ranges = replaceableRanges(businessWords, respectDoNotReplace)
        ranges.forEach { range ->
            for (start in range.first..range.last) {
                for (endInclusive in start..range.last) {
                    val phraseWords = businessWords.subList(start, endInclusive + 1)
                    phraseKeys(phraseWords).forEach { key ->
                        replacements[key].orEmpty().forEach { replacement ->
                            val replacementWords = NamingRules.splitWords(replacement)
                            if (replacementWords.isNotEmpty()) {
                                results += businessWords.replaceRange(start, endInclusive + 1, replacementWords)
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    private fun replaceableRanges(words: List<String>, respectDoNotReplace: Boolean): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var rangeStart: Int? = null
        words.forEachIndexed { index, word ->
            if (respectDoNotReplace && isDoNotReplace(word)) {
                if (rangeStart != null) {
                    ranges += rangeStart!!..(index - 1)
                    rangeStart = null
                }
            } else if (rangeStart == null) {
                rangeStart = index
            }
        }
        if (rangeStart != null) {
            ranges += rangeStart!!..words.lastIndex
        }
        return ranges
    }

    private fun List<String>.replaceRange(start: Int, endExclusive: Int, replacementWords: List<String>): List<String> =
        buildList {
            this@replaceRange.forEachIndexed { index, word ->
                when {
                    index < start -> add(word)
                    index == start -> addAll(replacementWords)
                    index >= endExclusive -> add(word)
                }
            }
        }

    private fun isDoNotReplace(word: String): Boolean =
        word.lowercase(Locale.US) in BuiltinDictionary.doNotReplaceTokens

    private fun groupKeyFor(item: ScannedRefactorItem): String =
        "${item.type.name}:${NamingRules.normalizeSuggestion(item.type, item.oldName).lowercase(Locale.US)}"

    private fun phraseKeys(words: List<String>): List<String> = buildList {
        add(words.joinToString("_") { it.lowercase(Locale.US) })
        add(words.joinToString("") { it.lowercase(Locale.US) })
    }.distinct()

    private fun formatWords(words: List<String>, format: NameFormat): String = when (format) {
        NameFormat.PASCAL -> NamingRules.toPascalCase(words)
        NameFormat.SNAKE -> NamingRules.toSnakeCase(words)
        NameFormat.PACKAGE -> words.joinToString(".") { it.lowercase(Locale.US) }
    }

    private fun fallbackWordsForType(type: RefactorItemType): List<String> = when (type) {
        RefactorItemType.ACTIVITY,
        RefactorItemType.FRAGMENT,
        RefactorItemType.VIEWMODEL,
        RefactorItemType.ADAPTER,
        RefactorItemType.KOTLIN_CLASS,
        RefactorItemType.KOTLIN_FILE,
        RefactorItemType.LAYOUT,
        RefactorItemType.DRAWABLE,
        RefactorItemType.STRING,
        RefactorItemType.DIMEN,
        -> emptyList()
        RefactorItemType.PACKAGE_CHILD -> listOf("feature", "module", "flow")
    }

    private fun UsedNameEntry?.toMetadata(): UsedNameMetadata = if (this == null) {
        UsedNameMetadata(false, null, null)
    } else {
        UsedNameMetadata(true, lastUsedVersion, lastUsedTimestamp)
    }

    private data class RawSuggestion(
        val value: String,
        val source: SuggestionSource,
    )

    private enum class NameFormat {
        PASCAL,
        SNAKE,
        PACKAGE,
    }

    private val SuggestionSource.priority: Int
        get() = when (this) {
            SuggestionSource.GEMINI_SEMANTIC -> 0
            SuggestionSource.EXACT_PHRASE -> 1
            SuggestionSource.TOKEN_SYNONYM -> 2
            SuggestionSource.TOKEN_ABBREVIATION -> 3
            SuggestionSource.WHOLE_PHRASE_REPLACEMENT -> 4
            SuggestionSource.RULE_FALLBACK -> 5
            SuggestionSource.MANUAL -> 6
        }
}
