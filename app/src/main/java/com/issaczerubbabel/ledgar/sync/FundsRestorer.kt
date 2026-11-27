package com.issaczerubbabel.ledgar.sync

import android.util.Log
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.FundImportDto
import com.issaczerubbabel.ledgar.data.remote.FundSyncDto
import com.issaczerubbabel.ledgar.data.repository.FundRepository
import com.issaczerubbabel.ledgar.fund.FundBackupMapper
import java.time.LocalDate
import javax.inject.Inject

/**
 * Brings Funds back from the Sheet. Import rebuilds Account and cycle ids, and deleting the old
 * Accounts and cycles takes their holdings and plans with them, so Funds are restored last: their
 * holdings found again by Account name and their plans by cycle start date (ADR-0011).
 */
class FundsRestorer @Inject constructor(
    private val api: ApiService,
    private val funds: FundRepository,
    private val database: SheetSyncDatabase
) {
    /** The phone's Funds by name, taken before Import replaces Accounts and cycles. */
    suspend fun keepLocal(): List<FundSyncDto> = funds.getBackup()

    /**
     * The Sheet's Funds, or null when the deployed script predates Funds and its reply can't be trusted.
     * Throws when the request fails.
     */
    suspend fun readSheet(scriptUrl: String): List<FundImportDto>? {
        val response = api.importFunds(scriptUrl)
        val body = response.body()
        check(response.isSuccessful && body?.status.equals("ok", ignoreCase = true)) {
            body?.message ?: "Reading Funds from the Sheet failed (HTTP ${response.code()})"
        }
        return if (body?.isUnderstoodByScript == true) body.data.orEmpty() else null
    }

    /**
     * Restores the Sheet's Funds after Accounts and cycles. When the Sheet has none, or the script
     * predates Funds, the phone's own Funds ([localBeforeImport]) are put back instead of being lost.
     * If reading the Sheet fails, the phone's Funds are still put back before the error is rethrown.
     * Returns how many Funds came from the Sheet.
     */
    suspend fun restore(scriptUrl: String, localBeforeImport: List<FundSyncDto>, today: LocalDate = LocalDate.now()): Int {
        val sheet = try {
            readSheet(scriptUrl)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            replaceWith(localBeforeImport.map(FundBackupMapper::asImportDto), today)
            throw e
        }
        if (sheet == null) Log.w(TAG, "Skipping Fund restore: the deployed Apps Script predates Funds")
        if (!sheet.isNullOrEmpty()) return replaceWith(sheet, today)
        replaceWith(localBeforeImport.map(FundBackupMapper::asImportDto), today)
        return 0
    }

    /** Takes in the Sheet's Funds only when the phone has none, as the first Backup's merge does. */
    suspend fun mergeIfNone(sheet: List<FundImportDto>, today: LocalDate = LocalDate.now()) {
        if (sheet.isNotEmpty() && funds.getSnapshot().funds.isEmpty()) replaceWith(sheet, today)
    }

    private suspend fun replaceWith(dtos: List<FundImportDto>, today: LocalDate): Int {
        val restored = FundBackupMapper.fromImportDtos(
            dtos,
            database.accountDao().getAllAccountsSnapshot(),
            database.bucketBudgetDao().getAllCycles(),
            today
        )
        funds.replaceAllFromBackup(restored)
        return restored.size
    }

    private companion object {
        const val TAG = "FundsRestorer"
    }
}
