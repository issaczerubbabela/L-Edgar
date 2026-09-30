package com.issaczerubbabel.ledgar.capture.categorize

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class KeywordMatch(val canonical: String, val keyword: String)

/**
 * A bundled list of merchant words per canonical category key (e.g. "food_delivery"). It only
 * names a canonical key; turning that into one of the user's own Categories is
 * [CategoryResolver]'s job. Matching is whole-word, and the longest matching keyword wins, so
 * "instamart" beats "swiggy" for "SWIGGY INSTAMART".
 */
class KeywordDictionary(keywordsByCanonical: Map<String, List<String>>) {

    private class Entry(val canonical: String, val keyword: String, val pattern: Regex)

    private val entries: List<Entry> = keywordsByCanonical.flatMap { (canonical, words) ->
        words.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .map { Entry(canonical, it, Regex("""(?<![a-z0-9])${Regex.escape(it)}(?![a-z0-9])""")) }
    }

    fun match(merchantNorm: String, merchantRaw: String?): KeywordMatch? {
        val haystack = "${merchantNorm.lowercase()} ${merchantRaw.orEmpty().lowercase()}"
        return entries
            .filter { it.pattern.containsMatchIn(haystack) }
            .maxByOrNull { it.keyword.length }
            ?.let { KeywordMatch(it.canonical, it.keyword) }
    }

    companion object {
        fun fromJson(json: String): KeywordDictionary {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            return KeywordDictionary(Gson().fromJson(json, type))
        }
    }
}
