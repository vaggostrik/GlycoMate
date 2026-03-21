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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.glycomate.app.data.model.*
import com.glycomate.app.ui.theme.*
import com.glycomate.app.viewmodel.GlycoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodTrackerScreen(viewModel: GlycoViewModel) {
    val allMoods     by viewModel.allMoods.collectAsState()
    val dashboard    by viewModel.dashboard.collectAsState()
    val currentGlucose = dashboard.latestReading?.valueMgDl

    var showLogDialog by remember { mutableStateOf(false) }
    var selectedTab   by remember { mutableIntStateOf(0) }

    // Today's moods
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val todayMoods = allMoods.filter { it.timestampMs >= todayStart }

    // Correlation data
    val correlationData = remember(allMoods) {
        allMoods.filter { it.glucoseAtTime != null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood Tracker",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700)) },
                actions = {
                    IconButton(onClick = { showLogDialog = true }) {
                        Icon(Icons.Filled.Add, "Νέο mood")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showLogDialog = true },
                containerColor = GlycoPurple
            ) {
                Icon(Icons.Filled.EmojiEmotions, "Καταγραφή mood")
            }
        }
    ) { padding ->

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Current glucose context
            currentGlucose?.let { g ->
                val color = when {
                    g < 70f  -> GlycoRed
                    g > 180f -> GlycoAmber
                    else     -> GlycoGreen
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape    = RoundedCornerShape(10.dp),
                    color    = color.copy(alpha = 0.1f)
                ) {
                    Row(modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MonitorHeart, null, tint = color,
                            modifier = Modifier.size(16.dp))
                        Text("Γλυκόζη τώρα: ${g.toInt()} mg/dL — θα συσχετιστεί με το mood σου",
                            style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Σήμερα") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Ιστορικό") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("Συσχέτιση") })
            }

            when (selectedTab) {
                0 -> TodayTab(
                    todayMoods    = todayMoods,
                    onDelete      = { viewModel.deleteMood(it) },
                    onAddNew      = { showLogDialog = true }
                )
                1 -> HistoryTab(
                    allMoods = allMoods,
                    onDelete = { viewModel.deleteMood(it) }
                )
                2 -> CorrelationTab(
                    correlationData = correlationData
                )
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
}

// ── Today tab ────────────────────────────────────────────────────────────────

@Composable
private fun TodayTab(
    todayMoods: List<MoodEntry>,
    onDelete:   (MoodEntry) -> Unit,
    onAddNew:   () -> Unit
) {
    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (todayMoods.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("😊", fontSize = 48.sp)
                        Text("Δεν έχεις καταγράψει mood σήμερα",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onAddNew) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Πώς νιώθεις τώρα;")
                        }
                    }
                }
            }
        } else {
            // Daily summary
            item { DailySummaryCard(moods = todayMoods) }

            item {
                Text("ΚΑΤΑΓΡΑΦΕΣ ΣΗΜΕΡΑ",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items(todayMoods, key = { it.id }) { entry ->
                MoodCard(entry = entry, onDelete = { onDelete(entry) })
            }
        }
    }
}

// ── Daily summary card ────────────────────────────────────────────────────────

@Composable
private fun DailySummaryCard(moods: List<MoodEntry>) {
    val avgScore = moods.map { it.mood.score }.average().toFloat()
    val dominant = moods.groupBy { it.mood }.maxByOrNull { it.value.size }?.key
        ?: moods.first().mood

    Card(modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Σημερινή εικόνα",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(dominant.emoji, fontSize = 40.sp)
                Column {
                    Text(dominant.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700))
                    Text("Μέσος όρος: ${String.format("%.1f", avgScore)}/5  •  ${moods.size} καταγραφές",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Mood score bar
            LinearProgressIndicator(
                progress   = { avgScore / 5f },
                modifier   = Modifier.fillMaxWidth().height(8.dp),
                color      = when {
                    avgScore >= 4f -> GlycoGreen
                    avgScore >= 3f -> GlycoAmber
                    else           -> GlycoRed
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant)

            // Glucose correlation if available
            val glucoseValues = moods.mapNotNull { it.glucoseAtTime }
            if (glucoseValues.isNotEmpty()) {
                val avgGlucose = glucoseValues.average().toFloat()
                Text("Μέση γλυκόζη κατά τις καταγραφές: ${avgGlucose.toInt()} mg/dL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── History tab ───────────────────────────────────────────────────────────────

@Composable
private fun HistoryTab(
    allMoods: List<MoodEntry>,
    onDelete: (MoodEntry) -> Unit
) {
    if (allMoods.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center) {
            Text("Δεν υπάρχουν καταγραφές ακόμα",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val grouped = allMoods.groupBy { entry ->
        SimpleDateFormat("EEE d MMM yyyy", Locale("el"))
            .format(Date(entry.timestampMs))
    }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        grouped.forEach { (day, entries) ->
            item {
                Text(day,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                    color = MaterialTheme.colorScheme.primary)
            }
            items(entries, key = { it.id }) { entry ->
                MoodCard(entry = entry, onDelete = { onDelete(entry) })
            }
        }
    }
}

// ── Correlation tab ───────────────────────────────────────────────────────────

@Composable
private fun CorrelationTab(correlationData: List<MoodEntry>) {
    if (correlationData.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📊", fontSize = 48.sp)
                Text("Δεν υπάρχουν δεδομένα για συσχέτιση",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Καταγράψτε mood ενώ έχετε ενεργό CGM για να δείτε\n" +
                    "πώς η γλυκόζη επηρεάζει την ψυχολογία σας.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    // Bucket moods by glucose range and calculate average mood per range
    val lowMoods     = correlationData.filter { it.glucoseAtTime != null && it.glucoseAtTime < 70f }
    val normalMoods  = correlationData.filter { it.glucoseAtTime != null &&
        it.glucoseAtTime in 70f..180f }
    val highMoods    = correlationData.filter { it.glucoseAtTime != null && it.glucoseAtTime > 180f }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("ΓΛΥΚΟΖΗ & ΨΥΧΟΛΟΓΙΑ",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Text("Σύγκριση διάθεσης ανά επίπεδο γλυκόζης (${correlationData.size} δεδομένα)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (lowMoods.isNotEmpty()) {
            item {
                CorrelationBucket(
                    label    = "Χαμηλή γλυκόζη (< 70)",
                    moods    = lowMoods,
                    color    = GlycoRed,
                    emoji    = "📉"
                )
            }
        }
        if (normalMoods.isNotEmpty()) {
            item {
                CorrelationBucket(
                    label    = "Εντός στόχου (70–180)",
                    moods    = normalMoods,
                    color    = GlycoGreen,
                    emoji    = "✅"
                )
            }
        }
        if (highMoods.isNotEmpty()) {
            item {
                CorrelationBucket(
                    label    = "Υψηλή γλυκόζη (> 180)",
                    moods    = highMoods,
                    color    = GlycoAmber,
                    emoji    = "📈"
                )
            }
        }

        // Insight
        item {
            val insight = buildInsight(lowMoods, normalMoods, highMoods)
            if (insight.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = GlycoPurple.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("💡", fontSize = 18.sp)
                        Text(insight, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CorrelationBucket(
    label: String, moods: List<MoodEntry>,
    color: Color, emoji: String
) {
    val avgScore   = moods.map { it.mood.score }.average().toFloat()
    val avgGlucose = moods.mapNotNull { it.glucoseAtTime }.average().toFloat()
    val topMood    = moods.groupBy { it.mood }.maxByOrNull { it.value.size }?.key

    Card(modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 16.sp)
                Text(label,
                    style = MaterialTheme.typography.labelLarge, color = color)
                Spacer(Modifier.weight(1f))
                Text("${moods.size} καταγρ.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format("%.1f", avgGlucose),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                        color = color)
                    Text("mg/dL μ.ο.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${topMood?.emoji ?: "?"} ${String.format("%.1f", avgScore)}/5",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700))
                    Text("mood μ.ο.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LinearProgressIndicator(
                progress   = { avgScore / 5f },
                modifier   = Modifier.fillMaxWidth().height(6.dp),
                color      = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

private fun buildInsight(
    low: List<MoodEntry>, normal: List<MoodEntry>, high: List<MoodEntry>
): String {
    if (normal.isEmpty()) return ""
    val normalAvg = normal.map { it.mood.score }.average()
    val lowAvg    = if (low.isNotEmpty()) low.map { it.mood.score }.average() else null
    val highAvg   = if (high.isNotEmpty()) high.map { it.mood.score }.average() else null

    return buildString {
        if (lowAvg != null && lowAvg < normalAvg - 0.5) {
            append("Η διάθεσή σου επηρεάζεται αρνητικά κατά τη χαμηλή γλυκόζη. ")
        }
        if (highAvg != null && highAvg < normalAvg - 0.5) {
            append("Η υψηλή γλυκόζη σχετίζεται με χειρότερη διάθεση. ")
        }
        if (normalAvg >= 3.5) {
            append("Όταν η γλυκόζη είναι εντός στόχου, νιώθεις καλύτερα!")
        }
    }.trim()
}

// ── Mood entry card ───────────────────────────────────────────────────────────

@Composable
private fun MoodCard(entry: MoodEntry, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val sdf           = SimpleDateFormat("HH:mm", Locale.getDefault())

    Card(modifier = Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { confirmDelete = !confirmDelete }) {
        Row(modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically) {

            // Mood emoji
            Text(entry.mood.emoji, fontSize = 28.sp)

            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.mood.label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
                    Text(entry.energy.emoji, fontSize = 14.sp)
                    Text(entry.energy.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.notes.isNotBlank()) {
                    Text(entry.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.glucoseAtTime != null) {
                    Text("Γλυκόζη: ${entry.glucoseAtTime.toInt()} mg/dL",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            entry.glucoseAtTime < 70f  -> GlycoRed
                            entry.glucoseAtTime > 180f -> GlycoAmber
                            else -> GlycoGreen
                        })
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(sdf.format(Date(entry.timestampMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedVisibility(visible = confirmDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ── Log mood dialog ───────────────────────────────────────────────────────────

@Composable
private fun LogMoodDialog(
    currentGlucose: Float?,
    onLog:          (MoodLevel, EnergyLevel, String) -> Unit,
    onDismiss:      () -> Unit
) {
    var selectedMood   by remember { mutableStateOf<MoodLevel?>(null) }
    var selectedEnergy by remember { mutableStateOf(EnergyLevel.NORMAL) }
    var notes          by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Πώς νιώθεις;") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Current glucose context
                currentGlucose?.let { g ->
                    Text("Γλυκόζη τώρα: ${g.toInt()} mg/dL — θα αποθηκευτεί με την καταγραφή",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Mood selection
                Text("Διάθεση:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    MoodLevel.entries.forEach { mood ->
                        val isSelected = selectedMood == mood
                        Surface(
                            onClick = { selectedMood = mood },
                            shape   = RoundedCornerShape(12.dp),
                            color   = if (isSelected) GlycoPurple.copy(alpha = 0.2f)
                                      else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(mood.emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
                if (selectedMood != null) {
                    Text(selectedMood!!.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = GlycoPurple,
                        modifier = Modifier.fillMaxWidth())
                }

                // Energy selection
                Text("Ενέργεια:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    EnergyLevel.entries.forEach { energy ->
                        val isSelected = selectedEnergy == energy
                        Surface(
                            onClick  = { selectedEnergy = energy },
                            shape    = RoundedCornerShape(12.dp),
                            color    = if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(energy.emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value         = notes,
                    onValueChange = { notes = it },
                    label         = { Text("Σημειώσεις (προαιρετικό)") },
                    modifier      = Modifier.fillMaxWidth(),
                    maxLines      = 2,
                    placeholder   = { Text("π.χ. Κουρασμένος μετά το φαγητό…") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    selectedMood?.let { mood ->
                        onLog(mood, selectedEnergy, notes)
                    }
                },
                enabled  = selectedMood != null
            ) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } }
    )
}
