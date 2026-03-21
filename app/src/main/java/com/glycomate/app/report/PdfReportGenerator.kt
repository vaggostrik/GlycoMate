package com.glycomate.app.report

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.glycomate.app.data.model.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "PdfReport"

// Uses Android's built-in PdfDocument — no external libraries needed
object PdfReportGenerator {

    fun generate(
        context:     Context,
        readings:    List<GlucoseReading>,
        insulinList: List<InsulinEntry>,
        meals:       List<MealEntry>,
        profile:     com.glycomate.app.data.model.UserProfile,
        daysBack:    Int = 14
    ): File? {
        var doc: PdfDocument? = null
        return try {
            val now   = System.currentTimeMillis()
            val fromMs = now - daysBack * 86_400_000L
            val r     = readings.filter { it.timestampMs >= fromMs }.sortedBy { it.timestampMs }
            val ins   = insulinList.filter { it.timestampMs >= fromMs }
            val m     = meals.filter { it.timestampMs >= fromMs }

            doc = PdfDocument()
            val pageW = 595   // A4 width in points
            val pageH = 842   // A4 height in points
            
            var currentPageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
            var page = doc.startPage(currentPageInfo)
            var canvas = page.canvas
            var y     = 0
            var pageCount = 1

            val dateFmt  = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val shortFmt = SimpleDateFormat("dd/MM", Locale.getDefault())

            // ── Paint styles ──────────────────────────────────────────────
            val titlePaint = Paint().apply {
                textSize  = 20f; isFakeBoldText = true; color = Color.parseColor("#185FA5")
            }
            val headPaint  = Paint().apply {
                textSize  = 14f; isFakeBoldText = true; color = Color.BLACK
            }
            val bodyPaint  = Paint().apply { textSize = 10f; color = Color.DKGRAY }
            val smallPaint = Paint().apply { textSize  = 9f;  color = Color.GRAY }
            val redPaint   = Paint().apply { textSize  = 10f; color = Color.RED }
            val greenPaint = Paint().apply { textSize  = 10f; color = Color.parseColor("#2A6B18") }
            val linePaint  = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

            fun nl(extra: Int = 0): Int { y += 18 + extra; return y }
            fun hLine() { canvas.drawLine(40f, y.toFloat(), (pageW - 40).toFloat(), y.toFloat(), linePaint); nl() }

            fun newPageIfNeeded(needed: Int = 60) {
                if (y + needed > pageH - 40) {
                    doc?.finishPage(page)
                    pageCount++
                    currentPageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageCount).create()
                    page = doc?.startPage(currentPageInfo)!!
                    canvas = page.canvas
                    y      = 40
                }
            }

            // ── Header ────────────────────────────────────────────────────
            y = 50
            canvas.drawText("GlycoMate — Αναφορά Γλυκόζης", 40f, y.toFloat(), titlePaint)
            nl()
            canvas.drawText("Ασθενής: ${profile.name.ifBlank { "—" }}  |  " +
                "Τύπος: ${profile.diabetesType}", 40f, y.toFloat(), bodyPaint)
            nl()
            canvas.drawText("Περίοδος: ${shortFmt.format(Date(fromMs))} — ${shortFmt.format(Date(now))}" +
                "  |  Δημιουργία: ${dateFmt.format(Date(now))}", 40f, y.toFloat(), smallPaint)
            nl()
            hLine()

            // ── Summary stats ─────────────────────────────────────────────
            canvas.drawText("ΣΥΝΟΠΤΙΚΑ ΣΤΑΤΙΣΤΙΚΑ ($daysBack ημέρες)", 40f, y.toFloat(), headPaint)
            nl(6)

            if (r.isNotEmpty()) {
                val values  = r.map { it.valueMgDl }
                val avg     = values.average().toFloat()
                val minV    = values.min()
                val maxV    = values.max()
                val tir     = r.count { it.valueMgDl in profile.targetLow..profile.targetHigh }
                val tirPct  = tir * 100 / r.size
                val estA1c  = (avg + 46.7f) / 28.7f

                val col1 = 40f; val col2 = 200f; val col3 = 360f

                canvas.drawText("Μέσος όρος:", col1, y.toFloat(), bodyPaint)
                canvas.drawText("${avg.toInt()} mg/dL", col2, y.toFloat(), bodyPaint)
                canvas.drawText("Εκτιμ. HbA1c: ${String.format("%.1f", estA1c)}%",
                    col3, y.toFloat(), bodyPaint)
                nl()
                canvas.drawText("Ελάχιστη:", col1, y.toFloat(), bodyPaint)
                canvas.drawText("${minV.toInt()} mg/dL", col2, y.toFloat(),
                    if (minV < profile.targetLow) redPaint else bodyPaint)
                canvas.drawText("Μέγιστη: ${maxV.toInt()} mg/dL",
                    col3, y.toFloat(), if (maxV > profile.targetHigh) redPaint else bodyPaint)
                nl()
                canvas.drawText("Time in Range:", col1, y.toFloat(), bodyPaint)
                canvas.drawText("$tirPct%  ($tir / ${r.size} μετρήσεις)",
                    col2, y.toFloat(), if (tirPct >= 70) greenPaint else redPaint)
                nl()
                canvas.drawText("Στόχοι: ${profile.targetLow.toInt()}–${profile.targetHigh.toInt()} mg/dL" +
                    "  |  ICR: ${profile.icr}  |  ISF: ${profile.isf}",
                    col1, y.toFloat(), smallPaint)
                nl()
            } else {
                canvas.drawText("Δεν υπάρχουν μετρήσεις για αυτή την περίοδο.",
                    40f, y.toFloat(), bodyPaint)
                nl()
            }
            hLine()

            // ── Insulin summary ───────────────────────────────────────────
            if (ins.isNotEmpty()) {
                newPageIfNeeded(80)
                canvas.drawText("ΙΝΣΟΥΛΙΝΗ", 40f, y.toFloat(), headPaint)
                nl(6)
                val totalUnits = ins.sumOf { it.units.toDouble() }.toFloat()
                val avgDaily   = totalUnits / daysBack
                canvas.drawText("Σύνολο: ${String.format("%.1f", totalUnits)} μον.  |  " +
                    "Μ.ο./ημέρα: ${String.format("%.1f", avgDaily)} μον.  |  " +
                    "Καταγραφές: ${ins.size}", 40f, y.toFloat(), bodyPaint)
                nl()
                InsulinType.entries.forEach { t ->
                    val typeEntries = ins.filter { it.type == t }
                    if (typeEntries.isNotEmpty()) {
                        val sum = typeEntries.sumOf { it.units.toDouble() }.toFloat()
                        canvas.drawText("${t.label}: ${String.format("%.1f", sum)} μον. (${typeEntries.size}x)",
                            60f, y.toFloat(), smallPaint)
                        nl()
                    }
                }
                hLine()
            }

            // ── Meals summary ─────────────────────────────────────────────
            if (m.isNotEmpty()) {
                newPageIfNeeded(80)
                canvas.drawText("ΓΕΥΜΑΤΑ", 40f, y.toFloat(), headPaint)
                nl(6)
                val totalCarbs = m.sumOf { it.carbsGrams.toDouble() }.toFloat()
                canvas.drawText("Σύνολο γευμάτων: ${m.size}  |  " +
                    "Σύνολο carbs: ${totalCarbs.toInt()}g  |  " +
                    "Μ.ο./ημέρα: ${(totalCarbs / daysBack).toInt()}g",
                    40f, y.toFloat(), bodyPaint)
                nl()
                hLine()
            }

            // ── Detailed glucose log ──────────────────────────────────────
            newPageIfNeeded(60)
            canvas.drawText("ΑΝΑΛΥΤΙΚΟ ΗΜΕΡΟΛΟΓΙΟ ΓΛΥΚΟΖΗΣ", 40f, y.toFloat(), headPaint)
            nl(8)

            // Table header
            canvas.drawText("Ημ/νια & Ώρα", 40f, y.toFloat(), bodyPaint)
            canvas.drawText("Τιμή", 220f, y.toFloat(), bodyPaint)
            canvas.drawText("Τάση", 290f, y.toFloat(), bodyPaint)
            canvas.drawText("Πηγή", 350f, y.toFloat(), bodyPaint)
            nl()
            hLine()

            r.sortedByDescending { it.timestampMs }.forEach { reading ->
                newPageIfNeeded(20)
                val p = when {
                    reading.valueMgDl < profile.targetLow  -> redPaint
                    reading.valueMgDl > profile.targetHigh -> Paint().apply { textSize = 10f; color = Color.parseColor("#B8860B") }
                    else -> greenPaint
                }
                canvas.drawText(dateFmt.format(Date(reading.timestampMs)),
                    40f, y.toFloat(), smallPaint)
                canvas.drawText("${reading.valueMgDl.toInt()} mg/dL",
                    220f, y.toFloat(), p)
                canvas.drawText(reading.trend.arrow, 290f, y.toFloat(), bodyPaint)
                canvas.drawText(reading.source.name, 350f, y.toFloat(), smallPaint)
                nl()
            }

            // Finish last page
            doc.finishPage(page)

            // Write to file
            val dir  = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            val file = File(dir, "GlycoMate_Report_${System.currentTimeMillis()}.pdf")
            doc.writeTo(FileOutputStream(file))
            Log.d(TAG, "PDF saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "PDF generation failed", e)
            null
        } finally {
            doc?.close()
        }
    }

    fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "GlycoMate — Αναφορά Γλυκόζης")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Κοινοποίηση αναφοράς"))
        } catch (e: Exception) {
            Log.e(TAG, "Sharing PDF failed", e)
        }
    }
}
