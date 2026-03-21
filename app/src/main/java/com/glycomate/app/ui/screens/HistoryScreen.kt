package com.glycomate.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.glycomate.app.R
import com.glycomate.app.data.model.*
import com.glycomate.app.ui.theme.*
import com.glycomate.app.viewmodel.GlycoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: GlycoViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_glucose),
        stringResource(R.string.tab_insulin),
        stringResource(R.string.tab_meals)
    )

    val allReadings by viewModel.allReadings.collectAsState()
    val allInsulin  by viewModel.allInsulin.collectAsState()
    val allMeals    by viewModel.allMeals.collectAsState()
    val profile     by viewModel.userProfile.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Weekly stats card
            if (allReadings.isNotEmpty()) {
                WeeklyStatsCard(readings = allReadings, profile = profile)
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(t) })
                }
            }

            when (selectedTab) {
                0 -> GlucoseList(readings = allReadings,
                    onDelete = { viewModel.deleteGlucose(it) })
                1 -> InsulinList(entries = allInsulin,
                    onDelete = { viewModel.deleteInsulin(it) })
                2 -> MealList(entries = allMeals,
                    onDelete = { viewModel.deleteMeal(it) })
            }
        }
    }
}

@Composable
private fun WeeklyStatsCard(readings: List<GlucoseReading>, profile: UserProfile) {
    val weekAgo   = System.currentTimeMillis() - 7 * 24 * 3600_000L
    val weekData  = readings.filter { it.timestampMs >= weekAgo }
    if (weekData.isEmpty()) return

    val avg = weekData.map { it.valueMgDl }.average().toFloat()
    val tir = weekData.count { it.valueMgDl in profile.targetLow..profile.targetHigh }
        .toFloat() / weekData.size * 100f
    val low  = weekData.count { it.valueMgDl < profile.targetLow }.toFloat() / weekData.size * 100f
    val high = weekData.count { it.valueMgDl > profile.targetHigh }.toFloat() / weekData.size * 100f

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.last_7_days),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem(stringResource(R.string.avg_label), "${avg.toInt()} mg/dL", MaterialTheme.colorScheme.primary)
                StatItem(stringResource(R.string.tir_label),  "${tir.toInt()}%",       GlycoGreen)
                StatItem(stringResource(R.string.low_label), "${low.toInt()}%",     GlycoRed)
                StatItem(stringResource(R.string.high_label),  "${high.toInt()}%",    GlycoAmber)
            }
            // TIR bar
            LinearProgressIndicator(
                progress = { tir / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color    = GlycoGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text(stringResource(R.string.total_logs_count, weekData.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
            color = color)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GlucoseList(readings: List<GlucoseReading>, onDelete: (GlucoseReading) -> Unit) {
    val sdf = SimpleDateFormat("EEE d MMM  HH:mm", Locale.getDefault())
    if (readings.isEmpty()) {
        EmptyState(stringResource(R.string.empty_glucose))
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(readings, key = { it.id }) { r ->
            val color = when {
                r.valueMgDl < 70f  -> GlycoRed
                r.valueMgDl > 180f -> GlycoAmber
                else               -> GlycoGreen
            }
            SwipeToDeleteCard(onDelete = { onDelete(r) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MonitorHeart, null, tint = color,
                            modifier = Modifier.size(20.dp))
                        Column {
                            Text("${r.valueMgDl.toInt()} mg/dL  ${r.trend.arrow}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
                            Text(r.source.name, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(sdf.format(Date(r.timestampMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InsulinList(entries: List<InsulinEntry>, onDelete: (InsulinEntry) -> Unit) {
    val sdf = SimpleDateFormat("EEE d MMM  HH:mm", Locale.getDefault())
    if (entries.isEmpty()) { EmptyState(stringResource(R.string.empty_insulin)); return }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(entries, key = { it.id }) { e ->
            SwipeToDeleteCard(onDelete = { onDelete(e) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Colorize, null, tint = GlycoAmber,
                            modifier = Modifier.size(20.dp))
                        Column {
                            Text("${e.units}U  ${e.type.label}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
                            if (e.note.isNotBlank())
                                Text(e.note, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(sdf.format(Date(e.timestampMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MealList(entries: List<MealEntry>, onDelete: (MealEntry) -> Unit) {
    val sdf = SimpleDateFormat("EEE d MMM  HH:mm", Locale.getDefault())
    if (entries.isEmpty()) { EmptyState(stringResource(R.string.empty_meals)); return }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(entries, key = { it.id }) { e ->
            SwipeToDeleteCard(onDelete = { onDelete(e) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Restaurant, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column {
                            Text(e.description,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
                            Text("${e.carbsGrams.toInt()}g carbs" +
                                if (e.suggestedInsulinUnits > 0f)
                                    "  •  ${stringResource(R.string.suggested_label, e.suggestedInsulinUnits)}"
                                else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(sdf.format(Date(e.timestampMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteCard(onDelete: () -> Unit, content: @Composable () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { confirmDelete = !confirmDelete }) {
        Box {
            content()
            if (confirmDelete) {
                Row(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(msg: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
