package com.issaczerubbabel.ledgar.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

private val asOfDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

/** Parses common sheet/app date formats into LocalDate. */
fun parseFlexibleDate(raw: String): LocalDate? {
    val value = raw.trim()
    if (value.isBlank()) return null

    // Google Sheets numeric serial dates (days since 1899-12-30), e.g. "45628".
    value.toDoubleOrNull()?.let { serial ->
        if (serial in 20000.0..80000.0) {
            val base = LocalDate.of(1899, 12, 30)
            return base.plusDays(serial.toLong())
        }
    }

    // Exact or prefixed ISO date.
    Regex("""(\d{4}-\d{1,2}-\d{1,2})""").find(value)?.groupValues?.getOrNull(1)?.let { iso ->
        runCatching { return LocalDate.parse(iso, DateTimeFormatter.ofPattern("yyyy-M-d")) }
    }

    // Year-first slash/dash date variants, optionally with time suffix in source.
    Regex("""(\d{4}[/-]\d{1,2}[/-]\d{1,2})""").find(value)?.groupValues?.getOrNull(1)?.let { ymd ->
        val normalized = ymd.replace('-', '/')
        runCatching { return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy/M/d")) }
    }

    // Slash or dash dates, with possible time suffix in the original string.
    Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""").find(value)?.groupValues?.getOrNull(1)?.let { short ->
        parseShortDate(short)?.let { return it }
    }

    // ISO date-time variants.
    runCatching { return OffsetDateTime.parse(value).toLocalDate() }
    runCatching { return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate() }

    // Month-name variants.
    listOf("MMM d, yyyy", "d MMM yyyy", "MMMM d, yyyy", "d MMMM yyyy").forEach { pattern ->
        val formatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .toFormatter(Locale.ENGLISH)
        runCatching { return LocalDate.parse(value, formatter) }
    }

    // JavaScript Date#toString style:
    // "Mon Apr 06 2026 00:00:00 GMT+0530 (India Standard Time)"
    Regex("""^[A-Za-z]{3}\s+[A-Za-z]{3}\s+\d{1,2}\s+\d{4}""").find(value)?.let {
        val prefix = value.split(' ').take(4).joinToString(" ")
        val jsPrefixFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE MMM d yyyy")
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .toFormatter(Locale.ENGLISH)
        runCatching { return LocalDate.parse(prefix, jsPrefixFormatter) }
    }

    return null
}

private fun parseShortDate(input: String): LocalDate? {
    val normalized = input.replace('-', '/')
    val parts = normalized.split('/')
    if (parts.size != 3) return null

    val p0 = parts[0].trim().toIntOrNull() ?: return null
    val p1 = parts[1].trim().toIntOrNull() ?: return null
    val yearRaw = parts[2].trim().toIntOrNull() ?: return null
    val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw

    val (month, day) = if (p0 > 12) p1 to p0 else p0 to p1
    if (month !in 1..12 || day !in 1..31) return null

    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

fun formatAsOfDateTime(value: LocalDateTime): String = value.format(asOfDateTimeFormatter)

fun nowAsOfDateTime(): String = formatAsOfDateTime(LocalDateTime.now())

fun parseAsOfDateTime(raw: String): LocalDateTime? {
    runCatching { return LocalDateTime.parse(raw, asOfDateTimeFormatter) }
    runCatching { return LocalDateTime.parse(raw) }

    val normalized = normalizeTimestampKey(raw)
    if (normalized != null) {
        runCatching {
            return LocalDateTime.parse(
                normalized,
                DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss", Locale.ENGLISH)
            )
        }
    }

    parseFlexibleDate(raw)?.let { return it.atStartOfDay() }
    return null
}

fun parseTransactionDateTime(dateRaw: String, timestampRaw: String?): LocalDateTime? {
    val normalized = normalizeTimestampKey(timestampRaw)
    if (normalized != null) {
        runCatching {
            return LocalDateTime.parse(
                normalized,
                DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss", Locale.ENGLISH)
            )
        }
    }

    // For legacy rows that only have day precision, treat the transaction as end-of-day
    // to preserve previous day-inclusive balance behavior.
    parseFlexibleDate(dateRaw)?.let { return it.atTime(23, 59, 59) }
    return null
}
