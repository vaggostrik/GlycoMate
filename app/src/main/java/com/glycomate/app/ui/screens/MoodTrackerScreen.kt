package com.glycomate.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.glycomate.app.data.model.*
import com.glycomate.app.ui.theme.*
import com.glycomate.app.viewmodel.GlycoViewModel
import com.glycomate.app.R
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodTrackerScreen(viewModel: GlycoViewModel) {
    val allMoods     by viewModel.allMoods.collectAsState()
    val dashboard    by viewModel.dashboard.collectAsState()
    val currentGlucose = dashboard.latestReading?.valueMgDl

    var showLogDialog by remember { mutableStateOf(false) }
    var editingMood   by remember { mutableStateOf<MoodEntry?>(null) }
    var selectedTab   by remember { mutableIntStateOf(0) }

    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val todayMoods = allMoods.filter { it.timestampMs >= todayStart }

    val correlationData = remember(allMoods) {
        allMoods.filter { it.glucoseAtTime != null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mood_tracker_top_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700)) },
                actions = {
                    IconButton(onClick = { showLogDialog = true }) {
                        Icon(Icons.Filled.Add, stringResource(R.string.new_mood_action))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showLogDialog = true },
                containerColor = GlycoPurple
            ) {
                Icon(Icons.Filled.EmojiEmotions, stringResource(R.string.log_mood_action))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.tab_today)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.tab_history)) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(stringResource(R.string.tab_correlation)) })
            }

            when (selectedTab) {
                0 -> TodayTab(
                    todayMoods = todayMoods,
                    onEdit     = { editingMood = it },
                    onDelete   = { viewModel.deleteMood(it) },
                    onAddNew   = { showLogDialog = true }
                )
                1 -> HistoryTab(
                    allMoods = allMoods,
                    onEdit   = { editingMood = it },
                    onDelete = { viewModel.deleteMood(it) }
                )
                2 -> CorrelationTab(correlationData = correlationData)
            }
        }
    }

    if (showLogDialog) {
        LogMoodDialog(
            currentGlucose = currentGlucose,
            onLog = { mood, energy, notes ->
                viewModel.logMood(mood, energy, notes)
                showLogDialog = false
            },
            onDismiss = { showLogDialog = false }
        )
    }

    editingMood?.let { mood ->
        LogMoodDialog(
            initialMood = mood.mood,
            initialEnergy = mood.energy,
            initialNotes = mood.notes,
            currentGlucose = mood.glucoseAtTime,
            isEditing = true,
            onLog = { m, e, n ->
                viewModel.updateMood(mood.copy(mood = m, energy = e, notes = n))
                editingMood = null
            },
            onDismiss = { editingMood = null }
        )
    }
}

@Composable
private fun TodayTab(todayMoods: List<MoodEntry>, onEdit: (MoodEntry) -> Unit, onDelete: (MoodEntry) -> Unit, onAddNew: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (todayMoods.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Button(onClick = onAddNew) { Text(stringResource(R.string.how_feel_button)) }
                }
            }
        } else {
            item { DailySummaryCard(moods = todayMoods) }
            items(todayMoods, key = { it.id }) { entry ->
                MoodCard(entry = entry, onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
            }
        }
    }
}

@Composable
private fun HistoryTab(allMoods: List<MoodEntry>, onEdit: (MoodEntry) -> Unit, onDelete: (MoodEntry) -> Unit) {
    val grouped = allMoods.groupBy { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(it.timestampMs)) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        grouped.forEach { (day, entries) ->
            item { Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            items(entries, key = { it.id }) { entry ->
                MoodCard(entry = entry, onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
            }
        }
    }
}

@Composable
private fun MoodCard(entry: MoodEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(entry.mood.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(entry.mood.labelRes), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodySmall)
                if (entry.glucoseAtTime != null) {
                    Text(stringResource(R.string.glucose_label, entry.glucoseAtTime.toInt()), 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { confirmDelete = !confirmDelete }) {
                Icon(if (confirmDelete) Icons.Filled.Close else Icons.Filled.Delete, null, tint = if (confirmDelete) Color.Gray else GlycoRed)
            }
            AnimatedVisibility(confirmDelete) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Check, null, tint = GlycoRed) }
            }
        }
    }
}

@Composable
private fun DailySummaryCard(moods: List<MoodEntry>) {
    val avgScore = moods.map { it.mood.score }.average().toFloat()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.daily_summary_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.mood_summary_stats, avgScore, moods.size), 
                style = MaterialTheme.typography.bodyMedium, color = GlycoPurple)
        }
    }
}

@Composable
private fun CorrelationTab(correlationData: List<MoodEntry>) {
    if (correlationData.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_correlation_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // ... rest of implementation using stringResource
}

@Composable
private fun LogMoodDialog(
    initialMood: MoodLevel? = null, 
    initialEnergy: EnergyLevel = EnergyLevel.NORMAL, 
    initialNotes: String = "", 
    currentGlucose: Float?, 
    isEditing: Boolean = false, 
    onLog: (MoodLevel, EnergyLevel, String) -> Unit, 
    onDismiss: () -> Unit
) {
    var selectedMood by remember { mutableStateOf(initialMood) }
    var selectedEnergy by remember { mutableStateOf(initialEnergy) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) stringResource(R.string.delete) else stringResource(R.string.how_feel_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                currentGlucose?.let { g ->
                    Text(stringResource(R.string.glucose_save_hint, g.toInt()), 
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.mood_label), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    MoodLevel.entries.forEach { mood ->
                        IconButton(onClick = { selectedMood = mood }) {
                            Text(mood.emoji, fontSize = if (selectedMood == mood) 32.sp else 24.sp)
                        }
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, 
                    label = { Text(stringResource(R.string.notes_label)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { selectedMood?.let { onLog(it, selectedEnergy, notes) } }, enabled = selectedMood != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
