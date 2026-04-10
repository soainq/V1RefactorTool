package com.internal.refactorassistant.suggest

object BuiltinDictionary {
    private val defaultData: SuggestionData by lazy { SuggestionDataLoader().loadDefault() }

    val exactPhraseSynonyms: Map<String, List<String>>
        get() = defaultData.exactPhraseSynonyms

    val tokenSynonyms: Map<String, List<String>>
        get() = defaultData.tokenSynonyms

    val abbreviationSynonyms: Map<String, List<String>>
        get() = defaultData.abbreviationSynonyms

    val wholePhraseReplacements: Map<String, List<String>>
        get() = defaultData.wholePhraseReplacements

    val doNotReplaceTokens: Set<String>
        get() = defaultData.doNotReplaceTokens
}
