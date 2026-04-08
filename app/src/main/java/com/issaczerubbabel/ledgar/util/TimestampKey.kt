package com.issaczerubbabel.ledgar.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timestampOutputFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss", Locale.ENGLISH)

private val localTimestampParsers: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss", Locale.ENGLISH)
)

private val zonedTimestampParsers: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss zzz", Locale.ENGLISH)
)

fun normalizeTimestampKey(raw: String?): String? {
    val input = raw?.trim().orEmpty()
    if (input.isBlank()) return null

    val compacted = input
        .replace(Regex("\\s+\\([^)]*\\)$"), "")
        .replace(Regex("\\s+"), " ")

    localTimestampParsers.forEach { formatter ->
        runCatching { LocalDateTime.parse(compacted, formatter) }
            .getOrNull()
            ?.let { return it.format(timestampOutputFormatter) }
    }

    zonedTimestampParsers.forEach { formatter ->
        runCatching { ZonedDateTime.parse(compacted, formatter) }
            .getOrNull()
            ?.let { return it.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime().format(timestampOutputFormatter) }
    }

    runCatching { Instant.parse(compacted) }
        .getOrNull()
        ?.let { return it.atZone(ZoneId.systemDefault()).toLocalDateTime().format(timestampOutputFormatter) }

    runCatching { ZonedDateTime.parse(compacted) }
        .getOrNull()
        ?.let { return it.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime().format(timestampOutputFormatter) }

    runCatching { LocalDateTime.parse(compacted) }
        .getOrNull()
        ?.let { return it.format(timestampOutputFormatter) }

    return null
}

fun generateTimestampKey(now: LocalDateTime): String = now.format(timestampOutputFormatter)
