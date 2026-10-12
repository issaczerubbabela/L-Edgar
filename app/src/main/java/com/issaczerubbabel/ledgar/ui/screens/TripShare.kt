package com.issaczerubbabel.ledgar.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.issaczerubbabel.ledgar.trip.ReportBlock
import java.io.File
import java.io.FileOutputStream

/** Hands plain text to Android's share sheet. */
internal fun shareTripText(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
}

/** Writes [content] to a file in the cache and shares it, so apps receive a real .csv or .txt file. */
internal fun shareTripFile(context: Context, fileName: String, mime: String, content: String) {
    val file = exportFile(context, fileName)
    file.writeText(content)
    shareExportedFile(context, file, mime)
}

internal fun shareTripPdf(context: Context, fileName: String, blocks: List<ReportBlock>) {
    val file = exportFile(context, fileName)
    TripPdf.write(file, blocks)
    shareExportedFile(context, file, "application/pdf")
}

internal fun safeFileName(tripName: String, suffix: String): String =
    tripName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "trip" } + "-" + suffix

private fun exportFile(context: Context, fileName: String): File =
    File(File(context.cacheDir, "exports").apply { mkdirs() }, fileName)

private fun shareExportedFile(context: Context, file: File, mime: String) {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Share"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

/** Lays [ReportBlock]s out on A4 pages with Android's PdfDocument. */
internal object TripPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun write(file: File, blocks: List<ReportBlock>) {
        val doc = PdfDocument()
        val body = paint(11f, bold = false)
        val bold = paint(11f, bold = true)
        val heading = paint(14f, bold = true)
        val title = paint(22f, bold = true)
        val muted = paint(11f, bold = false).apply { color = 0xFF555555.toInt() }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN
        val contentWidth = PAGE_WIDTH - 2 * MARGIN

        fun ensureRoom(height: Float) {
            if (y + height <= PAGE_HEIGHT - MARGIN) return
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun fit(text: String, paint: Paint, width: Float): String {
            val count = paint.breakText(text, true, width, null)
            return if (count >= text.length) text else text.take((count - 1).coerceAtLeast(0)) + "…"
        }

        blocks.forEach { block ->
            when (block) {
                is ReportBlock.Title -> { ensureRoom(34f); y += 24f; canvas.drawText(fit(block.text, title, contentWidth), MARGIN, y, title); y += 10f }
                is ReportBlock.Subtitle -> { ensureRoom(22f); y += 14f; canvas.drawText(fit(block.text, muted, contentWidth), MARGIN, y, muted); y += 8f }
                is ReportBlock.Heading -> { ensureRoom(40f); y += 30f; canvas.drawText(block.text, MARGIN, y, heading); y += 4f }
                is ReportBlock.Line -> { ensureRoom(20f); y += 16f; canvas.drawText(fit(block.text, body, contentWidth), MARGIN, y, body) }
                is ReportBlock.Row -> {
                    ensureRoom(20f)
                    y += 16f
                    val total = block.weights.sum()
                    var x = MARGIN
                    block.cells.forEachIndexed { i, cell ->
                        val width = contentWidth * block.weights[i] / total
                        val p = if (block.bold) bold else body
                        canvas.drawText(fit(cell, p, width - 8f), x, y, p)
                        x += width
                    }
                }
            }
        }
        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun paint(size: Float, bold: Boolean) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = 0xFF111111.toInt()
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}
