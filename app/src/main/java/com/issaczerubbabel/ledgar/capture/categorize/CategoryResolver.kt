package com.issaczerubbabel.ledgar.capture.categorize

/**
 * Turns a canonical category key into one of the user's own Categories, or null. It never
 * invents a Category (ADR-0004): a key with no saved mapping, or mapped to a Category the user no
 * longer has, resolves to null and the capture shows as needing a category.
 */
object CategoryResolver {

    fun resolve(canonical: String, mapping: Map<String, String>, userCategories: List<String>): String? {
        val mapped = mapping[canonical]?.trim().orEmpty()
        if (mapped.isEmpty()) return null
        return userCategories.firstOrNull { it.equals(mapped, ignoreCase = true) }
    }
}
