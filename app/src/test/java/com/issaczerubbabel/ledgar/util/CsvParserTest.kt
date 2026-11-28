package com.issaczerubbabel.ledgar.util

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {

    private val cash = AccountRecord(id = 1, groupName = "Cash", accountName = "Cash", initialBalance = 0.0)
    private val savings = AccountRecord(id = 2, groupName = "Bank", accountName = "Savings", initialBalance = 0.0)
    private val accounts = listOf(cash, savings)

    private val exportHeader =
        listOf("Date", "Type", "Category", "Amount", "Account", "From account", "To account", "Description", "Remarks", "Bookmarked", "Transaction ID")
            .joinToString(",")

    /** Builds one export-format CSV row, defaulting every column not explicitly overridden to blank. */
    private fun exportRow(
        date: String = "2026-10-07",
        type: String = "Expense",
        category: String = "",
        amount: String = "0",
        account: String = "",
        fromAccount: String = "",
        toAccount: String = "",
        description: String = "",
        remarks: String = "",
        bookmarked: String = "",
        transactionId: String = ""
    ): String = listOf(date, type, category, amount, account, fromAccount, toAccount, description, remarks, bookmarked, transactionId)
        .joinToString(",")

    private fun csv(vararg lines: String): String = (listOf(exportHeader) + lines).joinToString("\n")

    @Test
    fun `imported rows are unsynced inserts with no Transaction ID, so they Push instead of being deleted`() {
        val result = CsvParser.parse(csv(exportRow(category = "Food", amount = "120.5", account = "Cash", description = "Lunch")), accounts)

        assertEquals(1, result.records.size)
        val record = result.records[0]
        assertFalse(record.isSynced)
        assertEquals("INSERT", record.syncAction)
        assertNull(record.syncId)
    }

    @Test
    fun `a Transaction ID column round-trips into syncId so a restored backup re-links instead of duplicating`() {
        val record = CsvParser.parse(
            csv(exportRow(category = "Food", amount = "120.5", account = "Cash", transactionId = "abc-123")),
            accounts
        ).records.single()

        assertEquals("abc-123", record.syncId)
        assertFalse(record.isSynced)
    }

    @Test
    fun `a Transfer resolves From and To accounts by name, leaving Category blank`() {
        val record = CsvParser.parse(
            csv(exportRow(type = "Transfer", amount = "500", fromAccount = "Cash", toAccount = "Savings", description = "Move money")),
            accounts
        ).records.single()

        assertEquals(TransactionType.TRANSFER, record.type)
        assertEquals("", record.category)
        assertEquals(cash.id, record.fromAccountId)
        assertEquals(savings.id, record.toAccountId)
        assertNull(record.accountId)
    }

    @Test
    fun `an Account name with no match on this phone is kept as text instead of being guessed at`() {
        val record = CsvParser.parse(
            csv(exportRow(category = "Food", amount = "120.5", account = "Credit Card", description = "Lunch")),
            accounts
        ).records.single()

        assertNull(record.accountId)
        assertEquals("Credit Card", record.accountName)
    }

    @Test
    fun `an Adjustment round-trips with no Category`() {
        val record = CsvParser.parse(csv(exportRow(type = "Adjustment", amount = "50", account = "Savings")), accounts).records.single()

        assertEquals(TransactionType.ADJUSTMENT, record.type)
        assertEquals("", record.category)
        assertEquals(savings.id, record.accountId)
    }

    @Test
    fun `Bookmarked Yes round-trips to isBookmarked true`() {
        val record = CsvParser.parse(
            csv(exportRow(category = "Food", amount = "120.5", account = "Cash", bookmarked = "Yes")),
            accounts
        ).records.single()

        assertTrue(record.isBookmarked)
    }

    @Test
    fun `a quoted field keeps a comma and a doubled quote`() {
        val row = exportRow(
            category = "Food",
            amount = "120.5",
            account = "Cash",
            description = "\"Lunch, with \"\"friends\"\"\""
        )

        val record = CsvParser.parse(csv(row), accounts).records.single()

        assertEquals("Lunch, with \"friends\"", record.description)
    }

    @Test
    fun `a quoted field keeps an embedded line break`() {
        val row = exportRow(category = "Food", amount = "120.5", account = "Cash", remarks = "\"line one\nline two\"")

        val record = CsvParser.parse(csv(row), accounts).records.single()

        assertEquals("line one\nline two", record.remarks)
    }

    @Test
    fun `a UTF-8 BOM at the start of the file is stripped, not treated as part of the header`() {
        val textWithBom = "﻿" + csv(exportRow(category = "Food", amount = "120.5", account = "Cash"))

        val result = CsvParser.parse(textWithBom, accounts)

        assertEquals(1, result.records.size)
        assertEquals("2026-10-07", result.records[0].date)
    }

    @Test
    fun `a Google Sheets Form Responses export still imports, now as an unsynced row that Pushes`() {
        val legacyCsv = listOf(
            "Timestamp,Date,Expense/Income,Expense Category,Income Category,Description,Amount,Payment Mode,Remarks",
            "10/7/2026 9:00:00,10/7/2026,Expense,Food,,Lunch,120.5,Cash,"
        ).joinToString("\n")

        val record = CsvParser.parse(legacyCsv, accounts).records.single()

        assertEquals("2026-10-07", record.date)
        assertEquals("Food", record.category)
        assertFalse(record.isSynced)
        assertEquals("INSERT", record.syncAction)
        assertNull(record.syncId)
    }

    @Test
    fun `a row missing the Amount column is skipped, not crashed on`() {
        val result = CsvParser.parse(csv("2026-10-07,Expense,Food"), accounts)

        assertEquals(0, result.records.size)
        assertEquals(1, result.skippedLines)
    }

    @Test
    fun `an empty file parses to no records`() {
        val result = CsvParser.parse("", accounts)

        assertEquals(0, result.records.size)
        assertEquals(0, result.skippedLines)
    }
}
