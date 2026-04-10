package com.internal.refactorassistant.suggest

object BuiltinDictionary {
    val mappings: Map<String, List<String>> = mapOf(
        "main" to listOf("home", "dashboard", "landing", "entry"),
        "setting" to listOf("preference", "option", "config"),
        "settings" to listOf("preferences", "options", "config"),
        "profile" to listOf("account", "user", "member"),
        "detail" to listOf("info", "overview", "summary"),
        "details" to listOf("info", "overview", "summary"),
        "list" to listOf("feed", "catalog", "collection"),
        "shop" to listOf("store", "catalog", "market"),
        "cart" to listOf("bag", "basket", "checkout"),
        "login" to listOf("signin", "auth", "access"),
        "search" to listOf("discover", "explore", "lookup"),
        "news" to listOf("updates", "feed", "stories"),
        "offer" to listOf("promo", "deal", "campaign"),
        "splash" to listOf("launch", "welcome", "intro"),
    )
}
