package com.issaczerubbabel.ledgar.util

import android.content.Context
import android.net.Uri
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord

/**
 * Parses a CSV of Transactions: either this app's own Export to CSV, or a Google Sheets
 * "Form Responses" export. Either way every row comes in like it was typed on this phone —
 * unsynced, with its own Transaction ID — so it Pushes to the Sheet instead of being treated
 * as something that "came from Sheets" and quietly deleted on the next Pull (issue #56).
 *
 * Expected columns (flexible — detects by header):
 *   This app's export: Date | Type | Category | Amount | Account | From account | To account
 *     | Description | Remarks | Bookmarked | Transaction ID
 *   Google Form Responses (legacy): Timestamp | Date | Expense/Income | Expense Category
 *     | Income Category | Description | Amount | Payment Mode | Remarks
 */
object CsvParser {

    data class ParseResult(
        val records: List<ExpenseRecord>,
        val skippedLines: Int   // lines that couldn't be parsed
    )

    fun parse(context: Context, uri: Uri, accounts: List<AccountRecord>): ParseResult {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: return ParseResult(emptyList(), 0)
        return parse(text, accounts)
    }

    /** Pure so the parser can be unit-tested without a [Context] or a [Uri]. */
    fun parse(text: String, accounts: List<AccountRecord>): ParseResult {
        val rows = tokenizeCsv(text.removePrefix("﻿"))
        if (rows.isEmpty()) return ParseResult(emptyList(), 0)

        val headers = rows[0].map { it.lowercase().trim() }
        val idx = ColumnIndex(
            date          = headers.indexOfFirst { it.contains("date") }.takeIf { it >= 0 } ?: 1,
            type          = headers.indexOfFirst { it.contains("expense/income") || it == "type" }.takeIf { it >= 0 } ?: 2,
            category      = headers.indexOfFirst { it == "category" }.takeIf { it >= 0 } ?: -1,
            expCat        = headers.indexOfFirst { it.contains("expense cat") }.takeIf { it >= 0 } ?: -1,
            incCat        = headers.indexOfFirst { it.contains("income cat") }.takeIf { it >= 0 } ?: -1,
            amount        = headers.indexOfFirst { it.contains("amount") }.takeIf { it >= 0 } ?: 6,
            account       = headers.indexOfFirst { it == "account" }.takeIf { it >= 0 } ?: -1,
            fromAccount   = headers.indexOfFirst { it.contains("from account") }.takeIf { it >= 0 } ?: -1,
            toAccount     = headers.indexOfFirst { it.contains("to account") }.takeIf { it >= 0 } ?: -1,
            description   = headers.indexOfFirst { it.contains("desc") }.takeIf { it >= 0 } ?: 5,
            remarks       = headers.indexOfFirst { it.contains("remark") }.takeIf { it >= 0 } ?: -1,
            bookmarked    = headers.indexOfFirst { it.contains("bookmark") }.takeIf { it >= 0 } ?: -1,
            transactionId = headers.indexOfFirst { it.contains("transaction id") }.takeIf { it >= 0 } ?: -1
        )

        val accountsByName = accounts.associateBy { it.accountName.trim().lowercase() }

        val records = mutableListOf<ExpenseRecord>()
        var skipped = 0

        for (i in 1 until rows.size) {
            val cols = rows[i]
            if (cols.size <= idx.amount) { skipped++; continue }

            val type   = cols.getOrEmpty(idx.type)
            val amount = cols.getOrEmpty(idx.amount).toDoubleOrNull()

            if (type.isBlank() || amount == null) { skipped++; continue }

            val resolvedType = when {
                type.equals(TransactionType.EXPENSE, ignoreCase = true) -> TransactionType.EXPENSE
                type.equals(TransactionType.TRANSFER, ignoreCase = true) -> TransactionType.TRANSFER
                type.equals(TransactionType.ADJUSTMENT, ignoreCase = true) -> TransactionType.ADJUSTMENT
                else -> TransactionType.INCOME
            }

            val category = when {
                idx.category >= 0 -> cols.getOrEmpty(idx.category)
                resolvedType == TransactionType.EXPENSE -> cols.getOrEmpty(idx.expCat).ifBlank { "Other" }
                resolvedType == TransactionType.INCOME -> cols.getOrEmpty(idx.incCat).ifBlank { "Other" }
                else -> "" // a Transfer or Balance adjustment has no Category
            }

            val (accountId, accountName) = resolveAccount(cols.getOrEmpty(idx.account), accountsByName)
            val (fromAccountId, fromAccountName) = resolveAccount(cols.getOrEmpty(idx.fromAccount), accountsByName)
            val (toAccountId, toAccountName) = resolveAccount(cols.getOrEmpty(idx.toAccount), accountsByName)

            val bookmarkedText = cols.getOrEmpty(idx.bookmarked)
            val transactionId = cols.getOrEmpty(idx.transactionId).ifBlank { null }

            records.add(
                ExpenseRecord(
                    date        = normaliseDate(cols.getOrEmpty(idx.date)),
                    type        = resolvedType,
                    category    = category,
                    description = cols.getOrEmpty(idx.description),
                    amount      = amount,
                    accountId   = accountId,
                    accountName = accountName,
                    remarks     = cols.getOrEmpty(idx.remarks),
                    fromAccountId   = fromAccountId,
                    fromAccountName = fromAccountName,
                    toAccountId     = toAccountId,
                    toAccountName   = toAccountName,
                    isBookmarked = bookmarkedText.equals("yes", ignoreCase = true) || bookmarkedText.equals("true", ignoreCase = true),
                    isSynced    = false,
                    syncAction  = "INSERT",
                    syncId      = transactionId
                )
            )
        }

        return ParseResult(records, skipped)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private data class ColumnIndex(
        val date: Int, val type: Int, val category: Int, val expCat: Int, val incCat: Int, val amount: Int,
        val account: Int, val fromAccount: Int, val toAccount: Int,
        val description: Int, val remarks: Int, val bookmarked: Int, val transactionId: Int
    )

    private fun List<String>.getOrEmpty(index: Int) = getOrElse(index) { "" }.trim()

    /** Resolves an Account by name (case-insensitive); an unknown name is kept as text, not guessed at. */
    private fun resolveAccount(nameRaw: String, accountsByName: Map<String, AccountRecord>): Pair<Long?, String?> {
        val name = nameRaw.trim()
        if (name.isBlank()) return null to null
        val match = accountsByName[name.lowercase()]
        return if (match != null) match.id to null else null to name
    }

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

    /**
     * Tokenizes RFC 4180 CSV text into rows of fields. A doubled `""` inside a quoted field is an
     * escaped quote, and a quoted field may itself contain commas or line breaks.
     */
    private fun tokenizeCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i += 2 }
                    c == '"' -> { inQuotes = false; i++ }
                    else -> { field.append(c); i++ }
                }
                continue
            }
            when (c) {
                '"' -> { inQuotes = true; i++ }
                ',' -> { row.add(field.toString()); field = StringBuilder(); i++ }
                '\r' -> i++
                '\n' -> { row.add(field.toString()); rows.add(row); row = mutableListOf(); field = StringBuilder(); i++ }
                else -> { field.append(c); i++ }
            }
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
