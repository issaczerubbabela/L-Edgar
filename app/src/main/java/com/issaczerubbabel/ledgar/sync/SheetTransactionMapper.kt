package com.issaczerubbabel.ledgar.sync

import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import com.issaczerubbabel.ledgar.util.normalizeTimestampKey
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import javax.inject.Inject

/** Turns a Sheet row into a Transaction, matching its account names to this phone's Accounts. */
class SheetTransactionMapper @Inject constructor(private val accountDao: AccountDao) {

    data class AccountContext(
        val accountsByName: Map<String, AccountRecord>,
        val accountsByGroup: Map<String, AccountRecord>,
        val fallbackAccountId: Long?
    )

    data class MappedImportRecord(
        val record: ExpenseRecord,
        val normalizedTimestamp: String?,
        val discarded: Boolean
    )

    suspend fun resolveAccountContext(): AccountContext {
        val initialAccounts = accountDao.getAllAccountsSnapshot().toMutableList()
        if (initialAccounts.isEmpty()) {
            val fallbackId = accountDao.insert(
                AccountRecord(
                    groupName = "Cash",
                    accountName = "Cash",
                    initialBalance = 0.0,
                    initialBalanceDate = "1970-01-01",
                    isHidden = false
                )
            )
            initialAccounts += AccountRecord(
                id = fallbackId,
                groupName = "Cash",
                accountName = "Cash",
                initialBalance = 0.0,
                initialBalanceDate = "1970-01-01",
                isHidden = false
            )
        }

        val accountsByName = initialAccounts.associateBy { normalizeAccountKey(it.accountName) }
        val accountsByGroup = initialAccounts.associateBy { normalizeAccountKey(it.groupName) }
        val fallbackAccountId = initialAccounts
            .firstOrNull { it.accountName.equals("Cash", ignoreCase = true) || it.groupName.equals("Cash", ignoreCase = true) }
            ?.id
            ?: initialAccounts.firstOrNull()?.id

        return AccountContext(
            accountsByName = accountsByName,
            accountsByGroup = accountsByGroup,
            fallbackAccountId = fallbackAccountId
        )
    }

    fun mapImportRecord(
        dto: ImportRecordDto,
        accountsByName: Map<String, AccountRecord>,
        accountsByGroup: Map<String, AccountRecord>,
        fallbackAccountId: Long?
    ): MappedImportRecord {
        val resolvedType = canonicalType(dto.type)
        val resolvedDate = normalizeDate(dto.date, dto.timestamp)
        val mappedCategoryRaw = when {
            resolvedType.equals("Expense", ignoreCase = true) -> dto.expCategory
            resolvedType.equals("Income", ignoreCase = true) -> dto.incCategory
            else -> dto.expCategory ?: dto.incCategory
        }
        val mappedCategory = mappedCategoryRaw
            ?.trim()
            ?.takeUnless { it.isBlank() }
            ?: if (resolvedType.equals("Transfer", ignoreCase = true)) "Transfer" else ""

        val remoteAccountName = dto.accountName?.trim().takeUnless { it.isNullOrBlank() }
            ?: dto.paymentMode?.trim().takeUnless { it.isNullOrBlank() }
        val legacyTransferParts = remoteAccountName
            ?.split("->")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val explicitFromName = dto.fromAccountName?.trim().takeUnless { it.isNullOrBlank() }
            ?: legacyTransferParts.getOrNull(0)
        val explicitToName = dto.toAccountName?.trim().takeUnless { it.isNullOrBlank() }
            ?: legacyTransferParts.getOrNull(1)

        val fromAccountId = explicitFromName
            ?.let { token ->
                val key = normalizeAccountKey(token)
                accountsByName[key]?.id ?: accountsByGroup[key]?.id
            }
        val toAccountId = explicitToName
            ?.let { token ->
                val key = normalizeAccountKey(token)
                accountsByName[key]?.id ?: accountsByGroup[key]?.id
            }

        val mappedAccountId = when {
            resolvedType.equals("Transfer", ignoreCase = true) -> null
            remoteAccountName == null -> fallbackAccountId
            else -> {
                val key = normalizeAccountKey(remoteAccountName)
                accountsByName[key]?.id ?: accountsByGroup[key]?.id ?: fallbackAccountId
            }
        }

        if (!resolvedType.equals("Transfer", ignoreCase = true) && mappedAccountId == null) {
            return MappedImportRecord(
                record = ExpenseRecord(
                    date = resolvedDate,
                    type = resolvedType,
                    category = mappedCategory,
                    description = dto.description,
                    amount = dto.amount,
                    accountId = null,
                    remarks = dto.remarks,
                    isBookmarked = dto.isBookmarked ?: false,
                    isSynced = true,
                    remoteTimestamp = normalizeTimestampKey(dto.timestamp),
                    syncAction = "NONE"
                ),
                normalizedTimestamp = normalizeTimestampKey(dto.timestamp),
                discarded = true
            )
        }

        val fallbackAccountName = remoteAccountName?.trim().takeUnless { it.isNullOrBlank() }
        val fallbackFromName = explicitFromName?.trim().takeUnless { it.isNullOrBlank() }
        val fallbackToName = explicitToName?.trim().takeUnless { it.isNullOrBlank() }
        val timestamp = normalizeTimestampKey(dto.timestamp)

        return MappedImportRecord(
            record = ExpenseRecord(
                date = resolvedDate,
                type = resolvedType,
                category = mappedCategory,
                description = dto.description,
                amount = dto.amount,
                accountId = mappedAccountId,
                remarks = dto.remarks,
                isBookmarked = dto.isBookmarked ?: false,
                fromAccountId = when {
                    resolvedType.equals("Expense", ignoreCase = true) -> mappedAccountId
                    resolvedType.equals("Transfer", ignoreCase = true) -> fromAccountId
                    else -> null
                },
                toAccountId = when {
                    resolvedType.equals("Income", ignoreCase = true) -> mappedAccountId
                    resolvedType.equals("Transfer", ignoreCase = true) -> toAccountId
                    else -> null
                },
                accountName = when {
                    resolvedType.equals("Transfer", ignoreCase = true) -> null
                    else -> fallbackAccountName
                },
                fromAccountName = when {
                    resolvedType.equals("Expense", ignoreCase = true) -> fallbackAccountName
                    resolvedType.equals("Transfer", ignoreCase = true) -> fallbackFromName
                    else -> null
                },
                toAccountName = when {
                    resolvedType.equals("Income", ignoreCase = true) -> fallbackAccountName
                    resolvedType.equals("Transfer", ignoreCase = true) -> fallbackToName
                    else -> null
                },
                isSynced = true,
                remoteTimestamp = timestamp,
                syncAction = "NONE"
            ),
            normalizedTimestamp = timestamp,
            discarded = false
        )
    }

    private fun canonicalType(rawType: String): String {
        val t = rawType.trim().lowercase()
        return when {
            t == "expense" -> "Expense"
            t == "income" -> "Income"
            t == "transfer" -> "Transfer"
            else -> rawType.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun normalizeDate(rawDate: String, rawTimestamp: String?): String {
        parseFlexibleDate(rawDate)?.let { return it.toString() }
        rawTimestamp?.let { ts ->
            parseFlexibleDate(ts)?.let { return it.toString() }
        }
        return rawDate.trim()
    }

    private fun normalizeAccountKey(raw: String): String = raw.trim().lowercase()
}
