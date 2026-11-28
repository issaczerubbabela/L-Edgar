package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.dao.DropdownOptionDao
import com.issaczerubbabel.ledgar.data.local.dao.DropdownUsage
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class DropdownOptionRepositoryImpl @Inject constructor(
    private val dao: DropdownOptionDao
) : DropdownOptionRepository {

    override fun getOptionsByType(optionType: String): Flow<List<DropdownOption>> =
        dao.getOptionsByType(optionType)

    override suspend fun getAllOptionsSnapshot(): List<DropdownOption> = dao.getAllOptionsSnapshot()

    override suspend fun insert(option: DropdownOption): Long = dao.insert(option)

    override suspend fun addOptionIfAbsent(optionType: String, name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null

        val options = dao.getOptionsByType(optionType).first()
        options.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.name }

        dao.insert(
            DropdownOption(
                optionType = optionType,
                name = trimmed,
                displayOrder = (options.maxOfOrNull { it.displayOrder } ?: -1) + 1
            )
        )
        return trimmed
    }

    override suspend fun update(option: DropdownOption) = dao.update(option)

    override suspend fun delete(option: DropdownOption) = dao.delete(option)

    override suspend fun usage(option: DropdownOption): DropdownUsage {
        val name = option.name.trim()
        return when (option.optionType) {
            TransactionType.EXPENSE_CATEGORY_OPTION -> categoryUsage(TransactionType.EXPENSE, name)
                // Only expense Categories go into Buckets.
                .copy(bucketRoutes = dao.countBucketRoutes(name))
            TransactionType.INCOME_CATEGORY_OPTION -> categoryUsage(TransactionType.INCOME, name)
            ACCOUNT_GROUP -> DropdownUsage(accounts = dao.countAccountsInGroup(name))
            // Payment modes are only a list now: no Transaction stores one.
            else -> DropdownUsage()
        }
    }

    private suspend fun categoryUsage(type: String, name: String) = DropdownUsage(
        transactions = dao.countTransactionsInCategory(type, name),
        merchantRules = dao.countMerchantRules(type, name),
        repeatRules = dao.countRepeatRules(type, name)
    )

    override suspend fun updateOptions(options: List<DropdownOption>) = dao.updateOptions(options)

    override suspend fun overwriteAllOptions(options: List<DropdownOption>) = dao.overwriteAllOptions(options)

    private companion object {
        const val ACCOUNT_GROUP = "ACCOUNT_GROUP"
    }
}
