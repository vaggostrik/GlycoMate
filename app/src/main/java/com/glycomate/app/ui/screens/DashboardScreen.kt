package com.glycomate.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.glycomate.app.ui.components.MiniXpBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.glycomate.app.data.model.*
import com.glycomate.app.ui.theme.*
import com.glycomate.app.viewmodel.GlycoViewModel
import com.glycomate.app.R
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: GlycoViewModel,
    onOpenAiScan: () -> Unit = {},
    onOpenBarcodeScan: () -> Unit = {}
) {
    val state        by viewModel.dashboard.collectAsState()
    val allReadings  by viewModel.allReadings.collectAsState()
    val allInsulin   by viewModel.allInsulin.collectAsState()
    val allMeals     by viewModel.allMeals.collectAsState()
    val cgmSource    by viewModel.cgmSource.collectAsState()
    val gamState     by viewModel.gamificationState.collectAsState()

    var showAddGlucose  by remember { mutableStateOf(false) }
    var showAddInsulin  by remember { mutableStateOf(false) }
    var showAddMeal     by remember { mutableStateOf(false) }
    
    var fabExpanded     by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.dashboard_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700))
                        Text(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (cgmSource != "NONE") {
                        IconButton(onClick = { viewModel.syncNow() }) {
                            if (state.isSyncing)
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else
                                Icon(Icons.Filled.Sync, stringResource(R.string.sync_desc))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExpandableFabMenu(
                expanded = fabExpanded,
                onToggle = { fabExpanded = !fabExpanded },
                onAction = { action ->
                    fabExpanded = false
                    when(action) {
                        FabAction.GLUCOSE -> showAddGlucose = true
                        FabAction.INSULIN -> showAddInsulin = true
                        FabAction.MEAL    -> showAddMeal = true
                        FabAction.BARCODE -> onOpenBarcodeScan()
                        FabAction.AI_SCAN -> onOpenAiScan()
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
            ) {
                if (state.lastSyncError != null) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                                Text(state.lastSyncError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (cgmSource == "NONE") {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Sensors, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.no_cgm_connected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item { GlucoseHeroCard(reading = state.latestReading, profile = state.profile) }
                item { MiniXpBar(xp = gamState.xp, level = gamState.level, progressFraction = gamState.progressFraction, streakDays = gamState.streakDays) }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(stringResource(R.string.time_in_range), "${state.todayTir.toInt()}%", if (state.todayTir >= 70f) GlycoGreen else GlycoAmber, modifier = Modifier.weight(1f))
                        InsulinStatCard(iob = state.iob, basalToday = state.basalToday, modifier = Modifier.weight(1f))
                        StatCard(stringResource(R.string.measurements), "${state.todayReadings.size}", MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                    }
                }
                item { Text(stringResource(R.string.today_logs), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) }

                val timeline = buildTimeline(allReadings, allInsulin, allMeals)
                if (timeline.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_logs_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(timeline) { entry ->
                        TimelineEntryCard(entry = entry, onDeleteGlucose = { viewModel.deleteGlucose(it) }, onDeleteInsulin = { viewModel.deleteInsulin(it) }, onDeleteMeal = { viewModel.deleteMeal(it) })
                    }
                }
            }

            if (fabExpanded) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fabExpanded = false })
            }
        }
    }

    if (showAddGlucose) AddGlucoseDialog(onConfirm = { v, ts -> viewModel.logGlucose(v, ts); showAddGlucose = false }, onDismiss = { showAddGlucose = false })
    if (showAddInsulin) AddInsulinDialog(profile = state.profile, onConfirm = { u, t, b, ts -> viewModel.logInsulin(u, t, b, ts); showAddInsulin = false }, onDismiss = { showAddInsulin = false })
    if (showAddMeal) AddMealDialog(calculateBolus = viewModel::calculateBolus, onConfirm = { d, c, ts -> viewModel.logMeal(d, c, ts); showAddMeal = false }, onDismiss = { showAddMeal = false })
}

enum class FabAction { GLUCOSE, INSULIN, MEAL, BARCODE, AI_SCAN }

@Composable
fun ExpandableFabMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: (FabAction) -> Unit
) {
    val rotation by animateFloatAsState(if (expanded) 45f else 0f)

    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FabMenuItem(label = stringResource(R.string.ai_scan), icon = Icons.Filled.AutoAwesome, color = GlycoAmber, onClick = { onAction(FabAction.AI_SCAN) })
                FabMenuItem(label = stringResource(R.string.barcode), icon = Icons.Filled.QrCodeScanner, color = MaterialTheme.colorScheme.tertiary, onClick = { onAction(FabAction.BARCODE) })
                FabMenuItem(label = stringResource(R.string.meal), icon = Icons.Filled.Restaurant, color = MaterialTheme.colorScheme.secondary, onClick = { onAction(FabAction.MEAL) })
                FabMenuItem(label = stringResource(R.string.rapid_insulin), icon = Icons.Filled.Colorize, color = GlycoBlue, onClick = { onAction(FabAction.INSULIN) })
                FabMenuItem(label = stringResource(R.string.glucose), icon = Icons.Filled.Add, color = MaterialTheme.colorScheme.primary, onClick = { onAction(FabAction.GLUCOSE) })
            }
        }

        FloatingActionButton(
            onClick = onToggle,
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
            shape = CircleShape
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.rotate(rotation))
        }
    }
}

@Composable
fun FabMenuItem(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clickable { onClick() }) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
        }
        SmallFloatingActionButton(onClick = onClick, containerColor = color, contentColor = Color.White, shape = CircleShape) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun GlucoseHeroCard(reading: GlucoseReading?, profile: UserProfile) {
    val (value, color, statusRes) = when {
        reading == null                         -> Triple("—", MaterialTheme.colorScheme.onSurfaceVariant, R.string.no_data)
        reading.valueMgDl < profile.targetLow   -> Triple("${reading.valueMgDl.toInt()}", GlycoRed, R.string.low_status)
        reading.valueMgDl > profile.targetHigh  -> Triple("${reading.valueMgDl.toInt()}", GlycoAmber, R.string.high_status)
        else                                    -> Triple("${reading.valueMgDl.toInt()}", GlycoGreen, R.string.in_range_status)
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.glucose_now),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.W700),
                    color = color)
                if (reading != null) {
                    Text("mg/dL ${reading.trend.arrow}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.15f)) {
                Text(stringResource(statusRes), style = MaterialTheme.typography.labelMedium, color = color,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
            }
            if (reading != null) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.source_label, reading.source.name) + "  •  " +
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reading.timestampMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier, subtitle: String? = null) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700),
                color = color)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InsulinStatCard(iob: Float, basalToday: Float, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.insulin), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("⚡", fontSize = 11.sp)
                Text("${String.format("%.1f", iob)}U",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                    color = GlycoBlue)
            }
            Text(stringResource(R.string.iob_label), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("🌙", fontSize = 11.sp)
                Text("${String.format("%.1f", basalToday)}U",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                    color = GlycoAmber)
            }
            Text(stringResource(R.string.basal_today_label), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

sealed class TimelineEntry {
    data class Glucose(val r: GlucoseReading) : TimelineEntry()
    data class Insulin(val e: InsulinEntry)   : TimelineEntry()
    data class Meal(val e: MealEntry)         : TimelineEntry()
}

private fun buildTimeline(
    readings: List<GlucoseReading>,
    insulin:  List<InsulinEntry>,
    meals:    List<MealEntry>
): List<TimelineEntry> {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val entries = mutableListOf<Pair<Long, TimelineEntry>>()
    readings.filter { it.timestampMs >= today }.forEach { entries.add(it.timestampMs to TimelineEntry.Glucose(it)) }
    insulin.filter  { it.timestampMs >= today }.forEach { entries.add(it.timestampMs to TimelineEntry.Insulin(it)) }
    meals.filter    { it.timestampMs >= today }.forEach { entries.add(it.timestampMs to TimelineEntry.Meal(it)) }
    return entries.sortedByDescending { it.first }.map { it.second }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineEntryCard(
    entry: TimelineEntry,
    onDeleteGlucose: (GlucoseReading) -> Unit,
    onDeleteInsulin: (InsulinEntry)   -> Unit,
    onDeleteMeal:    (MealEntry)      -> Unit
) {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    var showDelete by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { showDelete = !showDelete }) {
        Row(modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            when (entry) {
                is TimelineEntry.Glucose -> {
                    val c = when {
                        entry.r.valueMgDl < 70f  -> GlycoRed
                        entry.r.valueMgDl > 180f -> GlycoAmber
                        else                     -> GlycoGreen
                    }
                    Icon(Icons.Filled.MonitorHeart, null, tint = c, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${entry.r.valueMgDl.toInt()} mg/dL  ${entry.r.trend.arrow}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
                        Text(entry.r.source.name, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(sdf.format(Date(entry.r.timestampMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedVisibility(showDelete) {
                        IconButton(onClick = { onDeleteGlucose(entry.r) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
                is TimelineEntry.Insulin -> {
                    Icon(Icons.Filled.Colorize, null, tint = GlycoAmber, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${entry.e.units}U  ${entry.e.type.label} (${entry.e.brand})",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
                        if (entry.e.note.isNotBlank())
                            Text(entry.e.note, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(sdf.format(Date(entry.e.timestampMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedVisibility(showDelete) {
                        IconButton(onClick = { onDeleteInsulin(entry.e) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
                is TimelineEntry.Meal -> {
                    Icon(Icons.Filled.Restaurant, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.e.description,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
                        Text("${entry.e.carbsGrams.toInt()}g carbs" +
                            if (entry.e.suggestedInsulinUnits > 0f)
                                "  •  Πρόταση: ${String.format("%.1f", entry.e.suggestedInsulinUnits)}U"
                            else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(sdf.format(Date(entry.e.timestampMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedVisibility(showDelete) {
                        IconButton(onClick = { onDeleteMeal(entry.e) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerRow(timestampMs: Long, onTimestampChanged: (Long) -> Unit) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val cal = remember(timestampMs) { Calendar.getInstance().apply { timeInMillis = timestampMs } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Ώρα:", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(
            selected = false,
            onClick  = { showDatePicker = true },
            label    = { Text(SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestampMs))) },
            leadingIcon = { Icon(Icons.Filled.DateRange, null, modifier = Modifier.size(16.dp)) }
        )
        FilterChip(
            selected = false,
            onClick  = { showTimePicker = true },
            label    = { Text(String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))) },
            leadingIcon = { Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(16.dp)) }
        )
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour   = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour      = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title            = { Text("Επιλογή ώρας") },
            text             = { TimeInput(state = state) },
            confirmButton    = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply {
                        timeInMillis = timestampMs
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                    }
                    onTimestampChanged(newCal.timeInMillis)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Άκυρο") } }
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestampMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateMs ->
                        val dateCal = Calendar.getInstance().apply { timeInMillis = dateMs }
                        val newCal  = Calendar.getInstance().apply {
                            timeInMillis = timestampMs
                            set(Calendar.YEAR,         dateCal.get(Calendar.YEAR))
                            set(Calendar.MONTH,        dateCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                        }
                        onTimestampChanged(newCal.timeInMillis)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Άκυρο") } }
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGlucoseDialog(onConfirm: (Float, Long) -> Unit, onDismiss: () -> Unit) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor     = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor    = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor   = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor          = MaterialTheme.colorScheme.primary
    )
    var value       by remember { mutableStateOf("") }
    var timestampMs by remember { mutableStateOf(System.currentTimeMillis()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Καταγραφή γλυκόζης") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = value, onValueChange = { value = it },
                    label = { Text("Τιμή mg/dL") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors)
                DateTimePickerRow(timestampMs = timestampMs, onTimestampChanged = { timestampMs = it })
            }
        },
        confirmButton = {
            Button(onClick = { value.toFloatOrNull()?.let { onConfirm(it, timestampMs) } },
                enabled = value.toFloatOrNull() != null) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddInsulinDialog(
    profile: UserProfile,
    onConfirm: (Float, InsulinType, String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor     = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor    = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor   = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor          = MaterialTheme.colorScheme.primary
    )
    var units       by remember { mutableStateOf("") }
    var type        by remember { mutableStateOf(InsulinType.RAPID) }
    var brand       by remember(type) {
        mutableStateOf(if (type == InsulinType.RAPID) profile.rapidInsulinBrand else profile.longInsulinBrand)
    }
    var timestampMs by remember { mutableStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Καταγραφή ινσουλίνης") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = units, onValueChange = { units = it },
                    label = { Text("Μονάδες") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                colors = fieldColors)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InsulinType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t },
                            label = { Text(t.label) })
                    }
                }

                if (type != InsulinType.MIXED) {
                    val brands = if (type == InsulinType.RAPID) UserProfile.RAPID_BRANDS else UserProfile.LONG_BRANDS
                    Text("Επιλογή μάρκας:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        brands.forEach { b ->
                            FilterChip(selected = brand == b, onClick = { brand = b },
                                label = { Text(b) })
                        }
                    }
                }

                DateTimePickerRow(timestampMs = timestampMs, onTimestampChanged = { timestampMs = it })
            }
        },
        confirmButton = {
            Button(onClick = { units.toFloatOrNull()?.let { onConfirm(it, type, brand, timestampMs) } },
                enabled = units.toFloatOrNull() != null) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealDialog(calculateBolus: (Float) -> Float, onConfirm: (String, Float, Long) -> Unit, onDismiss: () -> Unit) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor     = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor    = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor   = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor          = MaterialTheme.colorScheme.primary
    )
    var desc        by remember { mutableStateOf("") }
    var carbs       by remember { mutableStateOf("") }
    var timestampMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val suggested   = carbs.toFloatOrNull()?.let { calculateBolus(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Καταγραφή γεύματος") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text("Περιγραφή") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                colors = fieldColors)
                OutlinedTextField(value = carbs, onValueChange = { carbs = it },
                    label = { Text("Υδατάνθρακες (γρ.)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                colors = fieldColors)
                if (suggested != null && suggested > 0f) {
                    Text("Προτεινόμενη δόση: ${String.format("%.1f", suggested)}U",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                        color = MaterialTheme.colorScheme.primary)
                }
                DateTimePickerRow(timestampMs = timestampMs, onTimestampChanged = { timestampMs = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { if (desc.isNotBlank() && carbs.toFloatOrNull() != null)
                    onConfirm(desc, carbs.toFloat(), timestampMs) },
                enabled = desc.isNotBlank() && carbs.toFloatOrNull() != null
            ) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } }
    )
}
