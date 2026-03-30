package com.sheetsync.data.repository

import com.sheetsync.data.local.dao.ExpenseDao
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.remote.ImportRecordDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao
) : ExpenseRepository {

    override suspend fun save(record: ExpenseRecord): Long = dao.insert(record)

    override fun getAllRecords(): Flow<List<ExpenseRecord>> = dao.getAllRecords()

    override fun getByType(type: String): Flow<List<ExpenseRecord>> = dao.getByType(type)

    override fun getRecordsForAccount(accountId: Long): Flow<List<ExpenseRecord>> = dao.getRecordsForAccount(accountId)

    override fun getAccountBalance(accountId: Long): Flow<Double> = dao.getAccountBalance(accountId)

    override fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<ExpenseRecord>> =
        dao.getRecordsByDateRange(startDate, endDate)

    override suspend fun getUnsynced(): List<ExpenseRecord> = dao.getUnsyncedRecords()

    override suspend fun markSynced(ids: List<Long>) = dao.markAsSynced(ids)

    override suspend fun delete(record: ExpenseRecord) = dao.delete(record)

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun isDuplicate(date: String, type: String, category: String, amount: Double): Boolean =
        dao.findDuplicate(date, type, category, amount) != null

    override suspend fun importRemoteRecords(records: List<ImportRecordDto>): Int {
        repairLegacyRecords()
        if (records.isEmpty()) return 0

        val localComparable = dao.getAllRecordsSnapshot().map { local ->
            ComparableTx(
                date = normalizeDate(local.date, local.remoteTimestamp),
                amount = local.amount,
                type = canonicalType(local.type),
                description = local.description
            )
        }.toMutableList()

        val toInsert = mutableListOf<ExpenseRecord>()

        records.forEach { dto ->
            val resolvedType = canonicalType(dto.type)
            val resolvedDate = normalizeDate(dto.date, dto.timestamp)
            val mappedCategory = when {
                resolvedType.equals("Expense", ignoreCase = true) -> dto.expCategory
                resolvedType.equals("Income", ignoreCase = true) -> dto.incCategory
                else -> dto.expCategory ?: dto.incCategory
            }.orEmpty()

            val timestamp = dto.timestamp?.trim().takeUnless { it.isNullOrBlank() }

            val remoteComparable = ComparableTx(
                date = resolvedDate,
                amount = dto.amount,
                type = resolvedType,
                description = dto.description
            )

            val isDuplicate = localComparable.any { local -> isCompositeDuplicate(local, remoteComparable) }

            if (!isDuplicate) {
                toInsert += ExpenseRecord(
                    date = resolvedDate,
                    type = resolvedType,
                    category = mappedCategory,
                    description = dto.description,
                    amount = dto.amount,
                    paymentMode = dto.paymentMode,
                    remarks = dto.remarks,
                    isSynced = true,
                    remoteTimestamp = timestamp
                )
                localComparable += remoteComparable
            }
        }

        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
        return toInsert.size
    }

    private data class ComparableTx(
        val date: String,
        val amount: Double,
        val type: String,
        val description: String
    )

    private fun isCompositeDuplicate(local: ComparableTx, remote: ComparableTx): Boolean {
        return local.date == remote.date &&
            amountsEqual(local.amount, remote.amount) &&
            local.type == remote.type &&
            local.description.trim().equals(remote.description.trim(), ignoreCase = true)
    }

    private fun amountsEqual(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < 0.000001

    private suspend fun repairLegacyRecords() {
        val snapshot = dao.getAllRecordsSnapshot()
        val normalized = snapshot.map { record ->
            record.copy(
                date = normalizeDate(record.date, null),
                type = canonicalType(record.type)
            )
        }
        if (normalized != snapshot) {
            dao.insertAll(normalized)
        }
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
        val date = rawDate.trim()
        if (date.matches(Regex("""\\d{4}-\\d{2}-\\d{2}"""))) return date

        // Accept common ISO timestamp/date-time and keep date portion.
        if (date.matches(Regex("""\\d{4}-\\d{2}-\\d{2}T.*"""))) return date.substring(0, 10)

        // Handle M/D/YYYY and D/M/YYYY style values.
        val slash = date.split("/")
        if (slash.size == 3) {
            val p0 = slash[0].trim().toIntOrNull()
            val p1 = slash[1].trim().toIntOrNull()
            val year = slash[2].trim().toIntOrNull()
            if (p0 != null && p1 != null && year != null) {
                val (month, day) = if (p0 > 12) p1 to p0 else p0 to p1
                if (month in 1..12 && day in 1..31) {
                    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                }
            }
        }

        // Last fallback: derive date from timestamp if present.
        val ts = rawTimestamp?.trim().orEmpty()
        if (ts.matches(Regex("""\\d{4}-\\d{2}-\\d{2}T.*"""))) return ts.substring(0, 10)
        if (ts.matches(Regex("""\\d{4}-\\d{2}-\\d{2}"""))) return ts

        return date
    }
}
