package com.issaczerubbabel.ledgar.capture

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.issaczerubbabel.ledgar.capture.categorize.Categorization
import com.issaczerubbabel.ledgar.capture.categorize.DescriptionGuess

/**
 * What the predictor decided for a Captured transaction, kept in its `traceJson`: the step that
 * chose the Category and why, the Suggested description with its alternatives, and an Account
 * guessed from the source app. Only guesses live here; what the user changes on a card stays in
 * the inbox until Confirm, so re-guessing this never overwrites a user's choice.
 *
 * Built and read with Gson's tree API rather than reflection, so R8 can't break it.
 */
data class CaptureTrace(
    val rule: String? = null,
    val why: String? = null,
    val unmappedCanonical: String? = null,
    val descriptionGuess: String? = null,
    val descriptionAlternatives: List<String> = emptyList(),
    val guessedAccountId: Long? = null,
    /** See [DescriptionGuess.perPayment]. */
    val descriptionPerPayment: Boolean = false
) {
    /** Every guessed Description, the title first. */
    val descriptionGuesses: List<String> get() = listOfNotNull(descriptionGuess) + descriptionAlternatives

    /**
     * Whether the Suggested description is the title rather than the merchant's [rename]: always
     * when there is no rename, and over one once the merchant has had different Descriptions.
     */
    fun guessWinsOver(rename: String?): Boolean =
        descriptionGuess != null && (rename.isNullOrBlank() || descriptionPerPayment)

    /** The capture's title before the bank's own text: the winning one of the guess and the [rename]. */
    fun titleBeforeBankText(rename: String?): String? =
        if (guessWinsOver(rename)) descriptionGuess else rename?.takeIf { it.isNotBlank() }

    fun toJson(): String = JsonObject().apply {
        rule?.let { addProperty("rule", it) }
        why?.let { addProperty("why", it) }
        unmappedCanonical?.let { addProperty("unmappedCanonical", it) }
        descriptionGuess?.let { addProperty("description", it) }
        if (descriptionAlternatives.isNotEmpty()) {
            add("alternatives", JsonArray().apply { descriptionAlternatives.forEach { add(it) } })
        }
        guessedAccountId?.let { addProperty("guessedAccountId", it) }
        if (descriptionPerPayment) addProperty("perPayment", true)
    }.toString()

    companion object {
        fun of(result: Categorization, description: DescriptionGuess, guessedAccountId: Long?) = CaptureTrace(
            rule = result.decidedBy,
            why = result.why,
            unmappedCanonical = result.unmappedCanonical,
            descriptionGuess = description.title,
            descriptionAlternatives = description.alternatives,
            guessedAccountId = guessedAccountId,
            descriptionPerPayment = description.perPayment
        )

        /** An empty trace for a missing or unreadable one. */
        fun parse(json: String?): CaptureTrace {
            if (json.isNullOrBlank()) return CaptureTrace()
            val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return CaptureTrace()
            fun string(key: String) = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
            return CaptureTrace(
                rule = string("rule"),
                why = string("why"),
                unmappedCanonical = string("unmappedCanonical"),
                descriptionGuess = string("description"),
                descriptionAlternatives = obj.get("alternatives")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                    .orEmpty(),
                guessedAccountId = runCatching { obj.get("guessedAccountId")?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull(),
                descriptionPerPayment = runCatching { obj.get("perPayment")?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull() == true
            )
        }
    }
}
