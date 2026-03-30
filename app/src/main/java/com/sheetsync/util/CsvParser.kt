package com.sheetsync.util

import android.content.Context
import android.net.Uri
import com.sheetsync.data.local.entity.ExpenseRecord

/**
 * Parses a CSV file exported from Google Sheets (Form Responses format).
 *
 * Expected columns (flexible — detects by header):
 *   Timestamp | Date | Expense/Income | Expense Category | Income Category
 *   | Description | Amount | Payment Mode | Remarks | [Synced At — optional]
 *
 * Also handles the raw Google Form export format where there may be slight
 * header name differences.
 */
object CsvParser {

    data class ParseResult(
        val records: List<ExpenseRecord>,
        val skippedLines: Int   // lines that couldn't be parsed
    )

    fun parse(context: Context, uri: Uri): ParseResult {
        val lines = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.readLines()
            ?: return ParseResult(emptyList(), 0)

        if (lines.isEmpty()) return ParseResult(emptyList(), 0)

        // Parse header row to find column indices (case-insensitive, trimmed)
        val headers = splitCsvLine(lines[0]).map { it.lowercase().trim() }
        val idx = ColumnIndex(
            date        = headers.indexOfFirst { it.contains("date") }.takeIf { it >= 0 } ?: 1,
            type        = headers.indexOfFirst { it.contains("expense/income") || it == "type" }.takeIf { it >= 0 } ?: 2,
            expCat      = headers.indexOfFirst { it.contains("expense cat") }.takeIf { it >= 0 } ?: 3,
            incCat      = headers.indexOfFirst { it.contains("income cat") }.takeIf { it >= 0 } ?: 4,
            description = headers.indexOfFirst { it.contains("desc") }.takeIf { it >= 0 } ?: 5,
            amount      = headers.indexOfFirst { it.contains("amount") }.takeIf { it >= 0 } ?: 6,
            paymentMode = headers.indexOfFirst { it.contains("payment") }.takeIf { it >= 0 } ?: 7,
            remarks     = headers.indexOfFirst { it.contains("remark") }.takeIf { it >= 0 } ?: 8
        )

        val records = mutableListOf<ExpenseRecord>()
        var skipped = 0

        for (i in 1 until lines.size) {
            val cols = splitCsvLine(lines[i])
            if (cols.size <= idx.amount) { skipped++; continue }

            val type   = cols.getOrEmpty(idx.type)
            val amount = cols.getOrEmpty(idx.amount).toDoubleOrNull()

            if (type.isBlank() || amount == null) { skipped++; continue }

            val expCat = cols.getOrEmpty(idx.expCat)
            val incCat = cols.getOrEmpty(idx.incCat)
            val category = if (type.equals("Expense", ignoreCase = true)) expCat else incCat

            records.add(
                ExpenseRecord(
                    date        = normaliseDate(cols.getOrEmpty(idx.date)),
                    type        = if (type.equals("Expense", ignoreCase = true)) "Expense" else "Income",
                    category    = category.ifBlank { "Other" },
                    description = cols.getOrEmpty(idx.description),
                    amount      = amount,
                    paymentMode = cols.getOrEmpty(idx.paymentMode).ifBlank { "Other" },
                    remarks     = cols.getOrEmpty(idx.remarks),
                    isSynced    = true, // came from Sheets — don't push back
                    syncAction  = "NONE"
                )
            )
        }

        return ParseResult(records, skipped)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private data class ColumnIndex(
        val date: Int, val type: Int, val expCat: Int, val incCat: Int,
        val description: Int, val amount: Int, val paymentMode: Int, val remarks: Int
    )

    private fun List<String>.getOrEmpty(index: Int) = getOrElse(index) { "" }.trim().removeSurrounding("\"")

    /**
     * Normalise date to YYYY-MM-DD.
     *
     * Google Sheets exports dates as M/D/YYYY (US locale default) which is the
     * most common format in CSV exports. Detection logic:
     *  - If first segment > 12  → must be the day   → DD/MM/YYYY
     *  - Otherwise             → assume month first  → M/D/YYYY (Sheets default)
     */
    private fun normaliseDate(raw: String): String {
        if (raw.isBlank()) return ""
        // Already YYYY-MM-DD — pass through
        if (raw.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return raw
        // Slash-separated (M/D/YYYY or DD/MM/YYYY)
        val slash = raw.split("/")
        if (slash.size == 3) {
            return try {
                val part0 = slash[0].trim().toInt()   // could be month or day
                val part1 = slash[1].trim().toInt()   // could be day or month
                val year  = slash[2].trim().padStart(4, '0')
                val (month, day) = if (part0 > 12) {
                    // part0 can't be a month → DD/MM/YYYY
                    part1 to part0
                } else {
                    // Default: M/D/YYYY (Google Sheets US export)
                    part0 to part1
                }
                "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
            } catch (_: Exception) { raw }
        }
        return raw
    }

    /** Split a CSV line respecting quoted fields with commas inside them. */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
