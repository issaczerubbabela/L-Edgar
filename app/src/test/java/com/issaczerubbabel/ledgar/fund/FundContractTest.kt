package com.issaczerubbabel.ledgar.fund

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.FundPlan
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.remote.FundImportDto
import com.issaczerubbabel.ledgar.data.remote.FundsImportResponse
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The app half of the Funds contract with scripts/AppsScript.gs; the script half is
 * scripts/tests/funds.test.js. Both are pinned to the same golden files, so a field renamed on
 * either side fails a test instead of silently breaking backup or restore.
 */
class FundContractTest {

    private val fixtures = listOf("../scripts/tests/fixtures", "scripts/tests/fixtures").map(::File).first { it.isDirectory }
    private fun fixture(name: String) = File(fixtures, name).readText(Charsets.UTF_8)

    private val accounts = listOf(
        AccountRecord(id = 1, groupName = "Accounts", accountName = "HDFC", initialBalance = 0.0),
        AccountRecord(id = 2, groupName = "Savings", accountName = "Emergency Fund RD", initialBalance = 0.0),
        AccountRecord(id = 3, groupName = "Accounts", accountName = "Ministry Fund", initialBalance = 0.0)
    )
    private val cycles = listOf(
        BudgetCycle(id = 1, startDate = "2026-08-26", endDate = "2026-09-24", spendableAmount = 0.0, closedAt = "2026-09-25"),
        BudgetCycle(id = 2, startDate = "2026-09-25", endDate = "2026-10-24", spendableAmount = 0.0)
    )
    private val snapshot = FundsSnapshot(
        funds = listOf(
            Fund(id = 1, name = "Emergency", emoji = "🛟", colorIndex = 2, targetAmount = 100_000.0, dueDate = "2027-03-31", sortOrder = 0, createdDate = "2026-09-01"),
            Fund(id = 2, name = "Ministry", colorIndex = 5, sortOrder = 1, createdDate = "2026-09-01"),
            Fund(id = 3, name = "Laptop", emoji = "💻", targetAmount = 80_000.0, archivedAt = "2026-10-01", sortOrder = 2, createdDate = "2026-06-01")
        ),
        holdings = listOf(
            FundHolding(id = 1, fundId = 1, accountId = 2, kind = HoldingKind.WHOLE.name, openingAmount = 30_000.0, startDate = "2026-09-01"),
            FundHolding(id = 2, fundId = 1, accountId = 1, kind = HoldingKind.FIXED.name, amount = 70_000.0, openingAmount = 70_000.0, startDate = "2026-09-01"),
            FundHolding(id = 3, fundId = 2, accountId = 3, kind = HoldingKind.WHOLE.name, startDate = "2026-09-15")
        ),
        plans = listOf(FundPlan(1, 2, 15_000.0), FundPlan(1, 1, 15_000.0), FundPlan(2, 2, 5_000.0))
    )

    @Test
    fun theAppSendsExactlyTheRequestTheScriptIsTestedWith() {
        val request = SyncRequest(
            action = "backup",
            target = "funds",
            records = emptyList(),
            funds = FundBackupMapper.toSyncDtos(snapshot, accounts, cycles)
        )

        assertEquals(JsonParser.parseString(fixture("funds-request.json")), Gson().toJsonTree(request))
    }

    @Test
    fun theScriptsReplyRestoresEveryFundAgainstThisPhonesAccountsAndCycles() {
        val response = Gson().fromJson(fixture("funds-response.json"), FundsImportResponse::class.java)
        assertTrue(response.isUnderstoodByScript)

        val restored = FundBackupMapper.fromImportDtos(response.data.orEmpty(), accounts, cycles, TODAY)

        assertEquals(listOf("Emergency", "Ministry", "Laptop"), restored.map { it.name })
        assertEquals(
            listOf(
                RestoredHolding(2, HoldingKind.WHOLE, 0.0, 30_000.0, "2026-09-01"),
                RestoredHolding(1, HoldingKind.FIXED, 70_000.0, 70_000.0, "2026-09-01")
            ),
            restored[0].holdings
        )
        assertEquals(listOf(RestoredPlan(1, 15_000.0), RestoredPlan(2, 15_000.0)), restored[0].plans)
        assertEquals(null, restored[1].targetAmount)
        assertEquals("2026-10-01", restored[2].archivedAt)
        assertEquals(100_000.0, restored[0].targetAmount!!, 0.0)
    }

    @Test
    fun aHandEditedSheetIsCleanedUntilItCantBreakTheDatabase() {
        val restored = FundBackupMapper.fromImportDtos(
            listOf(
                FundImportDto(name = " ", createdDate = "2026-09-01"),
                FundImportDto(
                    name = "Emergency",
                    colorIndex = -1,
                    targetAmount = -5.0,
                    createdDate = "not a date",
                    holdings = listOf(
                        com.issaczerubbabel.ledgar.data.remote.FundHoldingImportDto(accountName = " emergency fund rd ", kind = "whole", amount = 9.0),
                        com.issaczerubbabel.ledgar.data.remote.FundHoldingImportDto(accountName = "Emergency Fund RD", kind = "FIXED", amount = 1.0),
                        com.issaczerubbabel.ledgar.data.remote.FundHoldingImportDto(accountName = "Gone Account", kind = "FIXED", amount = 1.0),
                        com.issaczerubbabel.ledgar.data.remote.FundHoldingImportDto(accountName = "HDFC", kind = "SOMETIMES", amount = 1.0)
                    ),
                    plans = listOf(
                        com.issaczerubbabel.ledgar.data.remote.FundPlanImportDto(cycleStartDate = "2026-09-25", plannedAmount = Double.NaN),
                        com.issaczerubbabel.ledgar.data.remote.FundPlanImportDto(cycleStartDate = "2026-09-25", plannedAmount = 9.0),
                        com.issaczerubbabel.ledgar.data.remote.FundPlanImportDto(cycleStartDate = "2025-01-01", plannedAmount = 9.0)
                    )
                ),
                FundImportDto(
                    name = "Insurance",
                    holdings = listOf(com.issaczerubbabel.ledgar.data.remote.FundHoldingImportDto(accountName = "Emergency Fund RD", kind = "WHOLE"))
                )
            ),
            accounts,
            cycles,
            TODAY
        )

        assertEquals(listOf("Emergency", "Insurance"), restored.map { it.name })
        val emergency = restored[0]
        assertEquals(TODAY.toString(), emergency.createdDate)
        assertEquals(null, emergency.targetAmount)
        assertEquals(Math.floorMod(-1, 9), emergency.colorIndex)
        assertEquals("one holding per Account, a WHOLE one keeps no amount, unknown Accounts and kinds are dropped",
            listOf(RestoredHolding(2, HoldingKind.WHOLE, 0.0, 0.0, TODAY.toString())), emergency.holdings)
        assertEquals("one plan per cycle, unknown cycles dropped", listOf(RestoredPlan(2, 0.0)), emergency.plans)
        assertTrue("the first Fund to hold an Account whole keeps it", restored[1].holdings.isEmpty())
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 27)
    }
}
