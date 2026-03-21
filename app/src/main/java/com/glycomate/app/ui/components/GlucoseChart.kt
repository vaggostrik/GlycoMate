package com.glycomate.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import com.glycomate.app.data.model.GlucoseReading
import com.glycomate.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GlucoseLineChart(
    readings:   List<GlucoseReading>,
    targetLow:  Float = 70f,
    targetHigh: Float = 180f,
    modifier:   Modifier = Modifier,
    showTimeLabels: Boolean = true
) {
    if (readings.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Δεν υπάρχουν δεδομένα για το γράφημα",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()

    // Colors
    val lineColor   = MaterialTheme.colorScheme.primary
    val lowColor    = Color(0xFFF85149)
    val highColor   = Color(0xFFE3B341)
    val inRangeColor= Color(0xFF3FB950)
    val gridColor   = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelColor  = MaterialTheme.colorScheme.onSurfaceVariant
    val bgBandColor = Color(0xFF3FB950).copy(alpha = 0.07f)

    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

    Canvas(modifier = modifier) {
        val padLeft   = 48.dp.toPx()
        val padRight  = 12.dp.toPx()
        val padTop    = 12.dp.toPx()
        val padBottom = if (showTimeLabels) 32.dp.toPx() else 12.dp.toPx()

        val chartW = size.width  - padLeft - padRight
        val chartH = size.height - padTop  - padBottom

        // Y axis: show 40–350 mg/dL range
        val yMin = 40f
        val yMax = 350f
        fun yPos(v: Float) = padTop + chartH * (1f - (v - yMin) / (yMax - yMin))
        fun xPos(i: Int)   = padLeft + chartW * (i.toFloat() / (readings.size - 1).coerceAtLeast(1))

        // ── Target range band ──────────────────────────────────────────────
        drawRect(
            color    = bgBandColor,
            topLeft  = Offset(padLeft, yPos(targetHigh)),
            size     = androidx.compose.ui.geometry.Size(
                chartW, yPos(targetLow) - yPos(targetHigh))
        )

        // ── Grid lines + Y labels ──────────────────────────────────────────
        listOf(50f, 70f, 100f, 140f, 180f, 250f, 300f).forEach { v ->
            val y = yPos(v)
            if (y < padTop || y > padTop + chartH) return@forEach
            val isTarget = v == targetLow || v == targetHigh
            drawLine(
                color       = if (isTarget) inRangeColor.copy(alpha = 0.4f) else gridColor,
                start       = Offset(padLeft, y),
                end         = Offset(padLeft + chartW, y),
                strokeWidth = if (isTarget) 1.5.dp.toPx() else 0.5.dp.toPx(),
                pathEffect  = if (isTarget) PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                              else null
            )
            // Y label
            drawYLabel(textMeasurer, "${v.toInt()}", padLeft - 4.dp.toPx(), y, labelColor)
        }

        // ── Glucose line ───────────────────────────────────────────────────
        val path = Path()
        val fillPath = Path()
        readings.forEachIndexed { i, r ->
            val x = xPos(i)
            val y = yPos(r.valueMgDl.coerceIn(yMin, yMax))
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, padTop + chartH)
                fillPath.lineTo(x, y)
            } else {
                // Smooth cubic bezier
                val prev  = readings[i - 1]
                val prevX = xPos(i - 1)
                val prevY = yPos(prev.valueMgDl.coerceIn(yMin, yMax))
                val cpX   = (prevX + x) / 2f
                path.cubicTo(cpX, prevY, cpX, y, x, y)
                fillPath.cubicTo(cpX, prevY, cpX, y, x, y)
            }
            if (i == readings.lastIndex) {
                fillPath.lineTo(x, padTop + chartH)
                fillPath.close()
            }
        }

        // Fill under the line
        drawPath(
            path  = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                startY = padTop, endY = padTop + chartH)
        )

        // Draw the line
        drawPath(path   = path,
            color       = lineColor,
            style       = Stroke(width = 2.dp.toPx(),
                cap = StrokeCap.Round, join = StrokeJoin.Round))

        // ── Data points ────────────────────────────────────────────────────
        readings.forEachIndexed { i, r ->
            val x = xPos(i)
            val y = yPos(r.valueMgDl.coerceIn(yMin, yMax))
            val dotColor = when {
                r.valueMgDl < targetLow  -> lowColor
                r.valueMgDl > targetHigh -> highColor
                else                     -> inRangeColor
            }
            drawCircle(color = dotColor, radius = 3.5.dp.toPx(), center = Offset(x, y))
            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(x, y))
        }

        // ── X time labels ──────────────────────────────────────────────────
        if (showTimeLabels && readings.size > 1) {
            val step = maxOf(1, readings.size / 5)
            readings.forEachIndexed { i, r ->
                if (i % step == 0 || i == readings.lastIndex) {
                    val x = xPos(i)
                    val label = sdf.format(Date(r.timestampMs))
                    drawXLabel(textMeasurer, label, x, padTop + chartH + 4.dp.toPx(), labelColor)
                }
            }
        }
    }
}

/**
 * Gluroo-style daily chart with a fixed 24-hour X axis.
 * Readings are placed at their exact time of day.
 * Includes target zone shading and a "now" marker when showing today.
 */
@Composable
fun DailyGlucoseChart(
    readings:    List<GlucoseReading>,
    targetLow:   Float = 70f,
    targetHigh:  Float = 180f,
    dayStartMs:  Long,   // midnight of the displayed day
    isToday:     Boolean = false,
    modifier:    Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    val lowColor     = Color(0xFFF85149)
    val highColor    = Color(0xFFE3B341)
    val inRangeColor = Color(0xFF3FB950)
    val gridColor    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val labelColor   = MaterialTheme.colorScheme.onSurfaceVariant
    val bgBandColor  = Color(0xFF3FB950).copy(alpha = 0.07f)
    val nowColor     = Color(0xFF58A6FF).copy(alpha = 0.7f)

    val dayMs = 86_400_000L
    val nowMs = System.currentTimeMillis()

    if (readings.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Δεν υπάρχουν δεδομένα για αυτή την ημέρα",
                style = MaterialTheme.typography.bodySmall,
                color = labelColor)
        }
        return
    }

    Canvas(modifier = modifier) {
        val padLeft   = 48.dp.toPx()
        val padRight  = 12.dp.toPx()
        val padTop    = 12.dp.toPx()
        val padBottom = 28.dp.toPx()

        val chartW = size.width  - padLeft - padRight
        val chartH = size.height - padTop  - padBottom

        val yMin = 40f
        val yMax = 350f

        fun yPos(v: Float) = padTop + chartH * (1f - (v - yMin) / (yMax - yMin))
        // X position based on milliseconds offset from day start (0..dayMs)
        fun xPos(ms: Long): Float {
            val offsetMs = (ms - dayStartMs).coerceIn(0L, dayMs)
            return padLeft + chartW * (offsetMs.toFloat() / dayMs)
        }

        // ── Target range band ──────────────────────────────────────────
        drawRect(
            color   = bgBandColor,
            topLeft = Offset(padLeft, yPos(targetHigh)),
            size    = androidx.compose.ui.geometry.Size(chartW, yPos(targetLow) - yPos(targetHigh))
        )

        // ── Y grid lines + labels ──────────────────────────────────────
        listOf(50f, 70f, 100f, 140f, 180f, 250f, 300f).forEach { v ->
            val y = yPos(v)
            if (y < padTop || y > padTop + chartH) return@forEach
            val isTarget = v == targetLow || v == targetHigh
            drawLine(
                color       = if (isTarget) inRangeColor.copy(alpha = 0.4f) else gridColor,
                start       = Offset(padLeft, y),
                end         = Offset(padLeft + chartW, y),
                strokeWidth = if (isTarget) 1.5.dp.toPx() else 0.5.dp.toPx(),
                pathEffect  = if (isTarget) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null
            )
            drawYLabel(textMeasurer, "${v.toInt()}", padLeft - 4.dp.toPx(), y, labelColor)
        }

        // ── X hour grid lines + labels (every 4h) ─────────────────────
        listOf(0, 4, 8, 12, 16, 20, 24).forEach { hour ->
            val offsetMs = hour * 3_600_000L
            val x = padLeft + chartW * (offsetMs.toFloat() / dayMs)
            drawLine(
                color       = gridColor,
                start       = Offset(x, padTop),
                end         = Offset(x, padTop + chartH),
                strokeWidth = 0.5.dp.toPx()
            )
            if (hour < 24) {
                val label = String.format("%02d:00", hour)
                drawXLabel(textMeasurer, label, x, padTop + chartH + 4.dp.toPx(), labelColor)
            }
        }

        // ── "Now" vertical marker (only for today) ────────────────────
        if (isToday && nowMs >= dayStartMs && nowMs < dayStartMs + dayMs) {
            val nowX = xPos(nowMs)
            drawLine(
                color       = nowColor,
                start       = Offset(nowX, padTop),
                end         = Offset(nowX, padTop + chartH),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            )
        }

        // ── Sort readings by time ──────────────────────────────────────
        val sorted = readings.sortedBy { it.timestampMs }

        // ── Glucose line (connect only nearby points ≤ 20 min apart) ──
        var prevX = 0f; var prevY = 0f; var prevMs = 0L
        val path = Path()
        var pathStarted = false
        sorted.forEach { r ->
            val x = xPos(r.timestampMs)
            val y = yPos(r.valueMgDl.coerceIn(yMin, yMax))
            val gapTooLong = pathStarted && (r.timestampMs - prevMs) > 20 * 60_000L
            if (!pathStarted || gapTooLong) {
                path.moveTo(x, y)
                pathStarted = true
            } else {
                val cpX = (prevX + x) / 2f
                path.cubicTo(cpX, prevY, cpX, y, x, y)
            }
            prevX = x; prevY = y; prevMs = r.timestampMs
        }
        drawPath(path  = path,
            color       = inRangeColor.copy(alpha = 0.6f),
            style       = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // ── Dots colored by range ──────────────────────────────────────
        sorted.forEach { r ->
            val x = xPos(r.timestampMs)
            val y = yPos(r.valueMgDl.coerceIn(yMin, yMax))
            val dotColor = when {
                r.valueMgDl < targetLow  -> lowColor
                r.valueMgDl > targetHigh -> highColor
                else                     -> inRangeColor
            }
            drawCircle(color = dotColor, radius = 3.5.dp.toPx(), center = Offset(x, y))
            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(x, y))
        }
    }
}

// ── Draw helpers ──────────────────────────────────────────────────────────────
private fun DrawScope.drawYLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String, x: Float, y: Float, color: Color
) {
    val measured = measurer.measure(text, TextStyle(fontSize = 9.sp, color = color))
    drawText(measured, topLeft = Offset(x - measured.size.width, y - measured.size.height / 2))
}

private fun DrawScope.drawXLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String, x: Float, y: Float, color: Color
) {
    val measured = measurer.measure(text, TextStyle(fontSize = 9.sp, color = color))
    drawText(measured, topLeft = Offset(x - measured.size.width / 2, y))
}

// ── Stats mini bar (TIR breakdown) ────────────────────────────────────────────
@Composable
fun TirBar(
    readings:   List<GlucoseReading>,
    targetLow:  Float = 70f,
    targetHigh: Float = 180f,
    modifier:   Modifier = Modifier
) {
    if (readings.isEmpty()) return

    val total    = readings.size
    val low      = readings.count { it.valueMgDl < targetLow }.toFloat() / total
    val inRange  = readings.count { it.valueMgDl in targetLow..targetHigh }.toFloat() / total
    val high     = readings.count { it.valueMgDl > targetHigh }.toFloat() / total

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Time in Range",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Stacked bar
        Row(modifier = Modifier.fillMaxWidth().height(12.dp)) {
            if (low > 0f) Surface(modifier = Modifier.weight(low).fillMaxHeight(),
                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp),
                color = Color(0xFFF85149)) {}
            if (inRange > 0f) Surface(modifier = Modifier.weight(inRange).fillMaxHeight(),
                shape = if (low == 0f && high == 0f) RoundedCornerShape(6.dp) else RoundedCornerShape(0.dp),
                color = Color(0xFF3FB950)) {}
            if (high > 0f) Surface(modifier = Modifier.weight(high).fillMaxHeight(),
                shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp),
                color = Color(0xFFE3B341)) {}
        }

        // Labels
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("⬇ ${(low * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFFF85149))
            Text("✓ ${(inRange * 100).toInt()}% εντός",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF3FB950),
                fontWeight = FontWeight.W600)
            Text("⬆ ${(high * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFFE3B341))
        }
    }
}
