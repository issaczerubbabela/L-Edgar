package com.issaczerubbabel.ledgar.util

import android.content.Context
import com.issaczerubbabel.ledgar.data.local.entity.Budget
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ExcelExportService {

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ENGLISH)

    suspend fun exportToCsv(
        context: Context,
        records: List<ExpenseRecord>,
        budgets: List<Budget>,
        title: String
    ): File = withContext(Dispatchers.IO) {
        val exportsDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }

        val fileName = "${sanitize(title)}_${LocalDateTime.now().format(dateFormat)}.csv"
        val file = File(exportsDir, fileName)

        file.bufferedWriter().use { writer ->
            writer.appendLine("SheetSync Export")
            writer.appendLine("Title,${escapeCsv(title)}")
            writer.appendLine()

            writer.appendLine("Transactions")
            writer.appendLine("Date,Type,Category,Description,Amount,PaymentMode,Remarks")
            records.forEach { record ->
                writer.appendLine(
                    listOf(
                        escapeCsv(record.date),
                        escapeCsv(record.type),
                        escapeCsv(record.category),
                        escapeCsv(record.description),
                        record.amount.toString(),
                        escapeCsv(record.paymentMode),
                        escapeCsv(record.remarks)
                    ).joinToString(",")
                )
            }

            writer.appendLine()
            writer.appendLine("Budgets")
            writer.appendLine("Category,Amount")
            budgets.forEach { budget ->
                writer.appendLine("${escapeCsv(budget.category)},${budget.amount}")
            }
        }

        file
    }

    private fun sanitize(input: String): String =
        input.lowercase(Locale.ENGLISH).replace(" ", "_").replace(Regex("[^a-z0-9_\\-]"), "")

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
