package com.issaczerubbabel.ledgar.sync

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import com.issaczerubbabel.ledgar.data.remote.SheetTransactionDto
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

/**
 * A Transaction reduced to what the Sheet stores, normalised so the phone's copy and the Sheet's
 * copy compare equal exactly when they agree. Merging uses it to tell whether the two already agree;
 * whether the Sheet changed is told by the row's revision instead.
 */
data class TransactionContent(
    val date: String,
    val type: String,
    val category: String,
    val description: String,
    val amount: String,
    /** The Account's name, or "From -> To" for a Transfer. */
    val account: String,
    val remarks: String,
    val isBookmarked: Boolean
) {
    val fingerprint: String by lazy {
        val joined = listOf(date, type, category, description, amount, account, remarks, isBookmarked.toString())
            .joinToString(FIELD_SEPARATOR)
        MessageDigest.getInstance("SHA-256").digest(joined.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val FIELD_SEPARATOR = "\u001F"

        fun of(record: ExpenseRecord, accountNames: Map<Long, String>): TransactionContent {
            val type = canonicalType(record.type)
            val isTransfer = type == TRANSFER
            val account = if (isTransfer) {
                transferAccount(
                    record.fromAccountId?.let(accountNames::get) ?: record.fromAccountName,
                    record.toAccountId?.let(accountNames::get) ?: record.toAccountName
                )
            } else {
                record.accountId?.let(accountNames::get)
                    ?: record.accountName ?: record.fromAccountName ?: record.toAccountName ?: ""
            }
            return TransactionContent(
                date = normalizeDate(record.date),
                type = type,
                category = if (isTransfer) "" else record.category.trim(),
                description = record.description.trim(),
                amount = amountText(record.amount),
                account = account.trim(),
                remarks = record.remarks.trim(),
                isBookmarked = record.isBookmarked
            )
        }

        fun of(dto: ImportRecordDto): TransactionContent {
            val type = canonicalType(dto.type)
            val combined = (dto.accountName?.takeIf { it.isNotBlank() } ?: dto.paymentMode).orEmpty().trim()
            val account = when (type) {
                TRANSFER -> {
                    val parts = combined.split("->").map { it.trim() }
                    transferAccount(
                        dto.fromAccountName?.takeIf { it.isNotBlank() } ?: parts.getOrNull(0),
                        dto.toAccountName?.takeIf { it.isNotBlank() } ?: parts.getOrNull(1)
                    )
                }
                else -> combined
            }
            return TransactionContent(
                date = normalizeDate(dto.date.ifBlank { dto.timestamp.orEmpty() }),
                type = type,
                category = when (type) {
                    EXPENSE -> dto.expCategory.orEmpty().trim()
                    INCOME -> dto.incCategory.orEmpty().trim()
                    else -> ""
                },
                description = dto.description.trim(),
                amount = amountText(dto.amount),
                account = account,
                remarks = dto.remarks.trim(),
                isBookmarked = dto.isBookmarked ?: false
            )
        }

        fun canonicalType(raw: String): String = when (raw.trim().lowercase()) {
            "expense" -> EXPENSE
            "income" -> INCOME
            "transfer" -> TRANSFER
            else -> raw.trim()
        }

        private fun transferAccount(from: String?, to: String?) =
            listOf(from.orEmpty().trim(), to.orEmpty().trim()).joinToString(" -> ")

        private fun normalizeDate(raw: String): String = parseFlexibleDate(raw)?.toString() ?: raw.trim()

        /** Amounts are compared to the paisa: a Sheet round trip can change the last bits of a Double. */
        private fun amountText(amount: Double): String =
            BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toPlainString()

        private const val EXPENSE = "Expense"
        private const val INCOME = "Income"
        private const val TRANSFER = "Transfer"
    }
}

/** What an upsert sends for [record], keyed by its Transaction ID. */
fun ExpenseRecord.toSheetDto(accountNames: Map<Long, String>): SheetTransactionDto {
    val syncId = requireNotNull(syncId) { "Transaction $id has no Transaction ID yet" }
    val type = TransactionContent.canonicalType(type)
    val fromName = fromAccountId?.let(accountNames::get) ?: fromAccountName
    val toName = toAccountId?.let(accountNames::get) ?: toAccountName
    val accountName = when (type) {
        "Transfer" -> listOf(fromName.orEmpty(), toName.orEmpty()).filter { it.isNotBlank() }.joinToString(" -> ")
        else -> accountId?.let(accountNames::get) ?: accountName ?: fromAccountName ?: toAccountName ?: ""
    }
    return SheetTransactionDto(
        id = syncId,
        timestamp = remoteTimestamp,
        date = date,
        type = type,
        expCategory = if (type == "Expense") category else "",
        incCategory = if (type == "Income") category else "",
        description = description,
        amount = amount,
        accountName = accountName,
        fromAccountName = if (type == "Transfer") fromName else null,
        toAccountName = if (type == "Transfer") toName else null,
        remarks = remarks,
        isBookmarked = isBookmarked,
        base = syncedRevision
    )
}
