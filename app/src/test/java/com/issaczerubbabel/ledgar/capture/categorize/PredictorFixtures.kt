package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.util.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime

/** Builders for the predictor tests. 2026-11-23 is a Monday. */
internal object PredictorFixtures {

    val MONDAY: LocalDate = LocalDate.of(2026, 11, 23)

    private var nextId = 1L

    fun past(
        category: String,
        description: String = category,
        amount: Double = 100.0,
        merchant: String? = null,
        time: String? = null,
        date: LocalDate = MONDAY.minusDays(1),
        type: String = TransactionType.EXPENSE
    ) = PastTransaction(
        id = nextId++,
        type = type,
        category = category,
        description = description,
        amount = amount,
        date = date,
        merchantNorm = merchant,
        minuteOfDay = time?.let { val (h, m) = it.split(":").map(String::toInt); h * 60 + m }
    )

    fun facts(
        merchant: String,
        amount: Double = 100.0,
        time: String = "12:00",
        date: LocalDate = MONDAY,
        type: String = TransactionType.EXPENSE
    ): CaptureFacts {
        val (h, m) = time.split(":").map(String::toInt)
        return CaptureFacts(merchant, type, amount, LocalDateTime.of(date, java.time.LocalTime.of(h, m)))
    }
}
