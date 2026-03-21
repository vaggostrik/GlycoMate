package com.glycomate.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.glycomate.app.data.model.GlucoseReading
import com.glycomate.app.ui.components.DailyGlucoseChart
import com.glycomate.app.ui.components.GlucoseLineChart
import com.glycomate.app.ui.components.TirBar
import com.glycomate.app.ui.theme.*
import com.glycomate.app.viewmodel.GlycoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: GlycoViewModel) {
    val allReadings by viewModel.allReadings.collectAsState()
    val allInsulin  by viewModel.allInsulin.collectAsState()
    val allMeals    by viewModel.allMeals.collectAsState()
    val profile     by viewModel.userProfile.collectAsState()
    val dashboard   by viewModel.dashboard.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Day navigation for tab 0: 0 = today, -1 = yesterday, etc.
    var dayOffset by remember { mutableIntStateOf(0) }

    val now   = System.currentTimeMillis()
    val dayMs = 86_400_000L

    // Compute start-of-day for the selected offset
    val selectedDayStart = remember(dayOffset) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }.timeInMillis
    }
    val selectedDayEnd = selectedDayStart + dayMs

    val selectedDayReadings = remember(allReadings, dayOffset) {
        allReadings.filter { it.timestampMs in selectedDayStart until selectedDayEnd }
            .sortedBy { it.timestampMs }
    }

    val weekReadings  = remember(allReadings) {
        allReadings.filter { it.timestampMs >= now - 7 * dayMs }.sortedBy { it.timestampMs }
    }
    val monthReadings = remember(allReadings) {
        allReadings.filter { it.timestampMs >= now - 30 * dayMs }.sortedBy { it.timestampMs }
    }

    // Day label for navigation header
    val dayLabel = remember(dayOffset) {
        when (dayOffset) {
            0  -> "Σήμερα"
            -1 -> "Χθες"
            else -> SimpleDateFormat("EEE d/M", Locale("el"))
                .format(Date(selectedDayStart)).replaceFirstChar { it.uppercase() }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Στατιστικά",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background)
        )
    }) { padding ->

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            TabRow(selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Ημέρα") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("7 Ημέρες") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("30 Ημέρες") })
            }

            // ── Tab 0: Day-by-day view ─────────────────────────────────
            if (selectedTab == 0) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // Day navigation header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { dayOffset-- }) {
                            Icon(Icons.Filled.ChevronLeft, "Προηγούμενη ημέρα")
                        }
                        Text(dayLabel,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600))
                        IconButton(
                            onClick = { if (dayOffset < 0) dayOffset++ },
                            enabled = dayOffset < 0
                        ) {
                            Icon(Icons.Filled.ChevronRight, "Επόμενη ημέρα",
                                tint = if (dayOffset < 0) MaterialTheme.colorScheme.onSurface
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Chart card
                        item {
                            Card(modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)) {

                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("Γλυκόζη",
                                            style = MaterialTheme.typography.titleSmall
                                                .copy(fontWeight = FontWeight.W600))
                                        Text("${selectedDayReadings.size} μετρήσεις",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    DailyGlucoseChart(
                                        readings    = selectedDayReadings,
                                        targetLow   = profile.targetLow,
                                        targetHigh  = profile.targetHigh,
                                        dayStartMs  = selectedDayStart,
                                        isToday     = dayOffset == 0,
                                        modifier    = Modifier.fillMaxWidth().height(220.dp)
                                    )

                                    TirBar(
                                        readings   = selectedDayReadings,
                                        targetLow  = profile.targetLow,
                                        targetHigh = profile.targetHigh,
                                        modifier   = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Stats summary for the day
                        if (selectedDayReadings.isNotEmpty()) {
                            item {
                                StatSummaryCard(
                                    readings   = selectedDayReadings,
                                    targetLow  = profile.targetLow,
                                    targetHigh = profile.targetHigh
                                )
                            }
                        }
                    }

                    // ── Bottom info bar ────────────────────────────────────────────
                    if (dayOffset == 0) {
                        val latestReading = dashboard.latestReading
                        val todayCarbs = remember(allMeals, selectedDayStart) {
                            allMeals.filter { it.timestampMs >= selectedDayStart && it.timestampMs < selectedDayEnd }
                                .sumOf { it.carbsGrams.toDouble() }.toFloat()
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Current glucose + trend
                                if (latestReading != null) {
                                    val glucoseColor = when {
                                        latestReading.valueMgDl < profile.targetLow  -> Color(0xFFF85149)
                                        latestReading.valueMgDl > profile.targetHigh -> Color(0xFFE3B341)
                                        else                                          -> Color(0xFF3FB950)
                                    }
                                    Text(
                                        "${latestReading.valueMgDl.toInt()} ${latestReading.trend.arrow}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                                        color = glucoseColor
                                    )
                                    Text("mg/dL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("—", style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Spacer(Modifier.weight(1f))

                                // Carbs pill (yellow)
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFFE3B341).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "${todayCarbs.toInt()}g carbs",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                                        color = Color(0xFFE3B341),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                // IOB pill (blue)
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = GlycoBlue.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        "${String.format("%.1f", dashboard.iob)}U IOB",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                                        color = GlycoBlue,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                return@Scaffold
            }

            // ── Tabs 1 & 2: Multi-day views ────────────────────────────
            val readings = if (selectedTab == 1) weekReadings else monthReadings

            if (readings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📊", fontSize = 48.sp)
                        Text("Δεν υπάρχουν δεδομένα",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Καταγραφή ή σύνδεση CGM για να δεις γραφήματα",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Scaffold
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {

                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("Γλυκόζη",
                                    style = MaterialTheme.typography.titleSmall
                                        .copy(fontWeight = FontWeight.W600))
                                Text("${readings.size} μετρήσεις",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            GlucoseLineChart(
                                readings       = readings.takeLast(96),
                                targetLow      = profile.targetLow,
                                targetHigh     = profile.targetHigh,
                                showTimeLabels = false,
                                modifier       = Modifier.fillMaxWidth().height(200.dp)
                            )

                            TirBar(
                                readings   = readings,
                                targetLow  = profile.targetLow,
                                targetHigh = profile.targetHigh,
                                modifier   = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    StatSummaryCard(
                        readings   = readings,
                        targetLow  = profile.targetLow,
                        targetHigh = profile.targetHigh
                    )
                }

                item {
                    DailyPatternCard(readings = allReadings
                        .filter {
                            it.timestampMs >= now - (if (selectedTab == 1) 7 else 30) * dayMs
                        })
                }

                item {
                    DayBreakdownCard(
                        readings   = readings,
                        targetLow  = profile.targetLow,
                        targetHigh = profile.targetHigh
                    )
                }
            }
        }
    }
}

// ── Stats summary card ────────────────────────────────────────────────────────
@Composable
private fun StatSummaryCard(
    readings: List<GlucoseReading>,
    targetLow: Float,
    targetHigh: Float
) {
    val values  = readings.map { it.valueMgDl }
    val avg     = values.average().toFloat()
    val min     = values.min()
    val max     = values.max()
    val stdDev  = values.standardDeviation()
    // Estimated HbA1c from average glucose: (avg + 46.7) / 28.7
    val estA1c  = (avg + 46.7f) / 28.7f

    Card(modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text("Στατιστικά", style = MaterialTheme.typography.titleSmall
                .copy(fontWeight = FontWeight.W600))

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox("Μ.Ο.", "${avg.toInt()}", "mg/dL",
                    colorFor(avg, targetLow, targetHigh))
                StatBox("Ελάχιστη", "${min.toInt()}", "mg/dL",
                    if (min < targetLow) GlycoRed else MaterialTheme.colorScheme.onSurface)
                StatBox("Μέγιστη", "${max.toInt()}", "mg/dL",
                    if (max > targetHigh) GlycoAmber else MaterialTheme.colorScheme.onSurface)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox("Τυπ. Απόκλιση", "${stdDev.toInt()}", "mg/dL",
                    MaterialTheme.colorScheme.primary)
                StatBox("Εκτιμ. HbA1c", String.format("%.1f", estA1c), "%",
                    when {
                        estA1c < 7f  -> GlycoGreen
                        estA1c < 8f  -> GlycoAmber
                        else         -> GlycoRed
                    })
                StatBox("Μετρήσεις", "${readings.size}", "σύνολο",
                    MaterialTheme.colorScheme.tertiary)
            }

            // HbA1c note
            Text("* Εκτιμώμενο HbA1c — δεν αντικαθιστά εργαστηριακή εξέταση",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700),
            color = color)
        Text(unit, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Daily pattern (average by hour) ──────────────────────────────────────────
@Composable
private fun DailyPatternCard(readings: List<GlucoseReading>) {
    if (readings.size < 12) return

    // Average glucose per hour of day
    val byHour = (0..23).map { hour ->
        val cal = Calendar.getInstance()
        readings.filter {
            cal.timeInMillis = it.timestampMs
            cal.get(Calendar.HOUR_OF_DAY) == hour
        }.map { it.valueMgDl }.average().toFloat().let {
            if (it.isNaN()) null else it
        }
    }

    Card(modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Text("Ημερήσιο μοτίβο (μ.ο. ανά ώρα)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600))

            // Simple bar chart per hour
            Row(modifier = Modifier.fillMaxWidth().height(80.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween) {
                byHour.forEachIndexed { hour, avg ->
                    val frac = avg?.let { (it - 40f) / (300f - 40f) } ?: 0f
                    val barColor = avg?.let {
                        when {
                            it < 70f  -> GlycoRed
                            it > 180f -> GlycoAmber
                            else      -> GlycoGreen
                        }
                    } ?: MaterialTheme.colorScheme.surfaceVariant
                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(frac.coerceIn(0.05f, 1f))) {
                        Surface(modifier = Modifier.fillMaxSize().padding(horizontal = 0.5.dp),
                            shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                            color = barColor) {}
                    }
                }
            }

            // Hour labels: 00, 06, 12, 18
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("00:00", "06:00", "12:00", "18:00", "23:00").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Per-day TIR breakdown ─────────────────────────────────────────────────────
@Composable
private fun DayBreakdownCard(
    readings: List<GlucoseReading>,
    targetLow: Float,
    targetHigh: Float
) {
    val sdf = SimpleDateFormat("EEE d/M", Locale("el"))
    val byDay = readings
        .groupBy { r ->
            Calendar.getInstance().apply { timeInMillis = r.timestampMs }.let {
                it.set(Calendar.HOUR_OF_DAY, 0)
                it.set(Calendar.MINUTE, 0)
                it.set(Calendar.SECOND, 0)
                it.set(Calendar.MILLISECOND, 0)
                it.timeInMillis
            }
        }
        .entries.sortedByDescending { it.key }.take(7)

    if (byDay.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Text("Ανά ημέρα",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600))

            byDay.forEach { (dayMs, dayReadings) ->
                val tir = dayReadings.count { it.valueMgDl in targetLow..targetHigh }
                    .toFloat() / dayReadings.size * 100f
                val avg = dayReadings.map { it.valueMgDl }.average().toFloat()

                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(sdf.format(Date(dayMs)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(70.dp))
                    LinearProgressIndicator(
                        progress    = { tir / 100f },
                        modifier    = Modifier.weight(1f).height(6.dp),
                        color       = when {
                            tir >= 70f -> GlycoGreen
                            tir >= 50f -> GlycoAmber
                            else       -> GlycoRed
                        },
                        trackColor  = MaterialTheme.colorScheme.surfaceVariant)
                    Text("${tir.toInt()}% | ${avg.toInt()}mg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(80.dp))
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun List<Float>.standardDeviation(): Float {
    if (size < 2) return 0f
    val avg = average()
    return Math.sqrt(map { (it - avg) * (it - avg) }.average()).toFloat()
}

private fun todayStart(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun colorFor(value: Float, low: Float, high: Float) = when {
    value < low  -> GlycoRed
    value > high -> GlycoAmber
    else         -> GlycoGreen
}
