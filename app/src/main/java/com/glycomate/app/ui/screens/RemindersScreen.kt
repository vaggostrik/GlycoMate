package com.glycomate.app.ui.screens

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.*
import com.glycomate.app.data.prefs.dataStore
import com.glycomate.app.ui.theme.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

// ── Data model ────────────────────────────────────────────────────────────────
enum class ReminderType(val emoji: String, val label: String) {
    GLUCOSE("🩸", "Μέτρηση γλυκόζης"),
    MEDICATION("💊", "Φάρμακο/Ινσουλίνη"),
    EXERCISE("🏃", "Άσκηση"),
    MEAL("🍽️", "Γεύμα"),
    CUSTOM("⏰", "Προσαρμοσμένο")
}

enum class RepeatInterval(val label: String, val minutes: Long) {
    ONCE("Μία φορά", 0L),
    DAILY("Κάθε μέρα", 1440L),
    EVERY_8H("Κάθε 8 ώρες", 480L),
    EVERY_12H("Κάθε 12 ώρες", 720L)
}

data class Reminder(
    val id:       Int    = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
    val type:     ReminderType,
    val title:    String,
    val hour:     Int,
    val minute:   Int,
    val repeat:   RepeatInterval,
    val enabled:  Boolean = true
) {
    fun timeStr() = String.format("%02d:%02d", hour, minute)

    fun toJson() = JSONObject().apply {
        put("id", id); put("type", type.name); put("title", title)
        put("hour", hour); put("minute", minute)
        put("repeat", repeat.name); put("enabled", enabled)
    }.toString()
}

fun String.toReminder(): Reminder? = runCatching {
    val o = JSONObject(this)
    Reminder(
        id      = o.getInt("id"),
        type    = ReminderType.valueOf(o.getString("type")),
        title   = o.getString("title"),
        hour    = o.getInt("hour"),
        minute  = o.getInt("minute"),
        repeat  = RepeatInterval.valueOf(o.getString("repeat")),
        enabled = o.optBoolean("enabled", true)
    )
}.getOrNull()

private val KEY_REMINDERS = stringPreferencesKey("reminders_json")

// ── Alarm receiver ────────────────────────────────────────────────────────────
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title   = intent.getStringExtra("title") ?: "Υπενθύμιση"
        val typeStr = intent.getStringExtra("type")  ?: "CUSTOM"
        val type    = runCatching { ReminderType.valueOf(typeStr) }.getOrDefault(ReminderType.CUSTOM)

        val nm = context.getSystemService(NotificationManager::class.java)
        // Ensure channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(
                "reminders", "Υπενθυμίσεις", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            })
        }
        nm.notify(intent.getIntExtra("id", 0),
            NotificationCompat.Builder(context, "reminders")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("${type.emoji} $title")
                .setContentText(type.label)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build())
    }
}

// ── Alarm manager helper ──────────────────────────────────────────────────────
private fun scheduleAlarm(context: Context, reminder: Reminder) {
    if (!reminder.enabled) return
    val am = context.getSystemService(AlarmManager::class.java)

    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("id",    reminder.id)
        putExtra("title", reminder.title)
        putExtra("type",  reminder.type.name)
    }
    val pi = PendingIntent.getBroadcast(context, reminder.id, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, reminder.hour)
        set(Calendar.MINUTE,      reminder.minute)
        set(Calendar.SECOND,      0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }

    when (reminder.repeat) {
        RepeatInterval.ONCE ->
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        RepeatInterval.DAILY ->
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        else -> {
            val intervalMs = reminder.repeat.minutes * 60_000L
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, intervalMs, pi)
        }
    }
}

private fun cancelAlarm(context: Context, reminderId: Int) {
    val am = context.getSystemService(AlarmManager::class.java)
    val pi = PendingIntent.getBroadcast(context, reminderId,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    pi?.let { am.cancel(it) }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val remindersFlow = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_REMINDERS] ?: "[]"
        runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { arr.getString(it).toReminder() }
        }.getOrDefault(emptyList())
    }
    val reminders by remindersFlow.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }

    // SCHEDULE_EXACT_ALARM permission launcher (Android 12+)
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { }

    suspend fun saveReminders(list: List<Reminder>) {
        val arr = JSONArray(list.map { it.toJson() })
        context.dataStore.edit { it[KEY_REMINDERS] = arr.toString() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Υπενθυμίσεις",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Filled.Add, "Νέα υπενθύμιση") }
        }
    ) { padding ->

        if (reminders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⏰", fontSize = 56.sp)
                    Text("Δεν υπάρχουν υπενθυμίσεις",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Πάτα + για να προσθέσεις",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder  = reminder,
                        onToggle  = { enabled ->
                            scope.launch {
                                val updated = reminders.map {
                                    if (it.id == reminder.id) it.copy(enabled = enabled) else it
                                }
                                saveReminders(updated)
                                if (enabled) scheduleAlarm(context, reminder.copy(enabled = true))
                                else cancelAlarm(context, reminder.id)
                            }
                        },
                        onDelete  = {
                            scope.launch {
                                cancelAlarm(context, reminder.id)
                                saveReminders(reminders.filter { it.id != reminder.id })
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            onAdd = { reminder ->
                scope.launch {
                    saveReminders(reminders + reminder)
                    // Request exact alarm permission on Android 12+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val am = context.getSystemService(AlarmManager::class.java)
                        if (!am.canScheduleExactAlarms()) {
                            exactAlarmLauncher.launch(
                                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        } else {
                            scheduleAlarm(context, reminder)
                        }
                    } else {
                        scheduleAlarm(context, reminder)
                    }
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

// ── Reminder card ─────────────────────────────────────────────────────────────
@Composable
private fun ReminderCard(
    reminder: Reminder,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            Text(reminder.type.emoji, fontSize = 26.sp)

            Column(modifier = Modifier.weight(1f)) {
                Text(reminder.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = if (reminder.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${reminder.timeStr()}  •  ${reminder.repeat.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Toggle
            Switch(
                checked         = reminder.enabled,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(checkedTrackColor = GlycoGreen)
            )

            // Delete
            if (confirmDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Add reminder dialog ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(
    onAdd: (Reminder) -> Unit,
    onDismiss: () -> Unit
) {
    var type     by remember { mutableStateOf(ReminderType.GLUCOSE) }
    var title    by remember(type) { mutableStateOf(type.label) }
    var hour     by remember { mutableIntStateOf(8) }
    var minute   by remember { mutableIntStateOf(0) }
    var repeat   by remember { mutableStateOf(RepeatInterval.DAILY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Νέα Υπενθύμιση") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Type selection
                Text("Τύπος:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick  = { type = t; title = t.label },
                            label    = { Text(t.emoji) }
                        )
                    }
                }

                // Title
                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = { Text("Τίτλος") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )

                // Time picker
                Text("Ώρα:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value         = String.format("%02d", hour),
                        onValueChange = { it.toIntOrNull()?.let { h -> hour = h.coerceIn(0, 23) } },
                        label         = { Text("ΩΩ") },
                        modifier      = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value         = String.format("%02d", minute),
                        onValueChange = { it.toIntOrNull()?.let { m -> minute = m.coerceIn(0, 59) } },
                        label         = { Text("ΛΛ") },
                        modifier      = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }

                // Repeat
                Text("Επανάληψη:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RepeatInterval.entries.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = repeat == r, onClick = { repeat = r })
                            Text(r.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    onAdd(Reminder(type = type, title = title.ifBlank { type.label },
                        hour = hour, minute = minute, repeat = repeat))
                },
                enabled  = title.isNotBlank()
            ) { Text("Προσθήκη") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } }
    )
}
