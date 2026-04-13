package com.internal.refactorassistant.suggest

object BuiltinDictionary {
    private val loadResult: SuggestionDataLoadResult by lazy { SuggestionDataLoader().loadDefaultSafely() }
    private val defaultData: SuggestionData
        get() = loadResult.data

    val loadWarnings: List<String>
        get() = loadResult.warnings

    val exactPhraseSynonyms: Map<String, List<String>>
        get() = defaultData.exactPhraseSynonyms

    val tokenSynonyms: Map<String, List<String>>
        get() = defaultData.tokenSynonyms

    val abbreviationSynonyms: Map<String, List<String>>
        get() = defaultData.abbreviationSynonyms

    val wholePhraseReplacements: Map<String, List<String>>
        get() = defaultData.wholePhraseReplacements

    val localeAliases: Map<String, List<String>>
        get() = defaultData.localeAliases

    val doNotReplaceTokens: Set<String>
        get() = defaultData.doNotReplaceTokens
}
