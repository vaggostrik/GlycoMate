package com.glycomate.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.glycomate.app.MainActivity
import com.glycomate.app.data.repository.CgmSyncResult
import com.glycomate.app.data.repository.GlycoRepository
import com.glycomate.app.sos.SosManager
import com.glycomate.app.wear.WearableSender
import com.glycomate.app.widget.GlucoseWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val TAG = "CgmSyncWorker"

// Notification channel IDs
const val CHANNEL_HYPO       = "channel_hypo"         // URGENT — hypo < 70
const val CHANNEL_HYPER      = "channel_hyper"         // HIGH — hyper > threshold
const val CHANNEL_PERSISTENT = "channel_persistent"    // LOW — always-on status bar
const val CHANNEL_CGM        = "channel_cgm_sync"      // LOW — background sync info

// Notification IDs
private const val NOTIF_HYPO       = 2001
private const val NOTIF_HYPER      = 2002
private const val NOTIF_PERSISTENT = 2003

// Worker name
const val WORK_NAME = "cgm_sync_periodic"

class CgmSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ensureChannels()
        Log.d(TAG, "CGM sync starting…")

        val repo = GlycoRepository(context)
        val workResult = when (val result = repo.syncCgm()) {
            is CgmSyncResult.Success -> {
                Log.d(TAG, "Sync OK: ${result.reading.valueMgDl} mg/dL")
                val profile = repo.prefs.userProfile.first()

                // Update persistent notification
                showPersistentNotification(result.reading.valueMgDl, result.reading.trend.arrow)

                // Send to Wear OS watch
                WearableSender.sendGlucose(context, result.reading,
                    profile.targetLow, profile.targetHigh)

                // Alert checks
                when {
                    result.reading.valueMgDl < profile.targetLow -> {
                        fireHypoAlert(result.reading.valueMgDl, profile.targetLow)
                        // Auto-SOS check
                        val autoSos   = repo.prefs.sosAutoTrigger.first()
                        val threshold = repo.prefs.sosThreshold.first()
                        if (autoSos && result.reading.valueMgDl < threshold) {
                            SosManager(context).triggerSos(result.reading.valueMgDl, profile.name)
                        }
                    }
                    result.reading.valueMgDl > profile.targetHigh ->
                        fireHyperAlert(result.reading.valueMgDl, profile.targetHigh)
                }

                Result.success()
            }
            is CgmSyncResult.Error -> {
                Log.w(TAG, "Sync error: ${result.message}")
                Result.success() // Don't use retry() — we schedule next run ourselves
            }
            is CgmSyncResult.NoSourceConfigured -> {
                Log.d(TAG, "No CGM source configured")
                Result.success()
            }
        }

        // Refresh home-screen widget with the latest data
        try { GlucoseWidget().updateAll(context) } catch (_: Exception) { }

        // Schedule next 3-minute sync (WorkManager minimum periodic interval is 15 min,
        // so we chain OneTimeWorkRequests instead)
        scheduleNext(context)

        return workResult
    }

    // ── Hypo alert (URGENT — max priority, full vibration) ────────────────────
    private fun fireHypoAlert(value: Float, target: Float) {
        val diff = target - value
        val urgency = when {
            value < 54f -> "⚠️⚠️ ΕΠΕΙΓΟΝ — Σοβαρή υπογλυκαιμία"
            value < 65f -> "⚠️ Υπογλυκαιμία"
            else        -> "↓ Χαμηλή γλυκόζη"
        }

        val intent = mainActivityIntent()
        val n = NotificationCompat.Builder(context, CHANNEL_HYPO)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(urgency)
            .setContentText("${value.toInt()} mg/dL — Φάε ζάχαρη αμέσως!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Γλυκόζη: ${value.toInt()} mg/dL\n" +
                    "Στόχος: > ${target.toInt()} mg/dL\n" +
                    "Διαφορά: ${diff.toInt()} mg/dL κάτω από στόχο\n\n" +
                    "Πάρε 15γρ γρήγορα σάκχαρα (χυμός, ταμπλέτες γλυκόζης) αμέσως!"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setOnlyAlertOnce(false)         // Alert EVERY time for hypo
            .setFullScreenIntent(intent, true) // Show even on lock screen
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_HYPO, n)
        vibrateUrgent()
    }

    // ── Hyper alert (HIGH priority) ───────────────────────────────────────────
    private fun fireHyperAlert(value: Float, target: Float) {
        val diff = value - target
        val n = NotificationCompat.Builder(context, CHANNEL_HYPER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("↑ Υψηλή γλυκόζη")
            .setContentText("${value.toInt()} mg/dL — Ελέγξτε ινσουλίνη")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Γλυκόζη: ${value.toInt()} mg/dL\n" +
                    "Στόχος: < ${target.toInt()} mg/dL\n" +
                    "Διαφορά: +${diff.toInt()} mg/dL πάνω από στόχο"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(mainActivityIntent())
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_HYPER, n)
        vibrateNormal()
    }

    // ── Persistent notification (always visible in status bar) ────────────────
    fun showPersistentNotification(valueMgDl: Float, trendArrow: String) {
        val color = when {
            valueMgDl < 70f  -> 0xFFF85149.toInt()  // red
            valueMgDl > 180f -> 0xFFE3B341.toInt()  // amber
            else             -> 0xFF3FB950.toInt()  // green
        }
        val statusText = when {
            valueMgDl < 70f  -> "Χαμηλή ⚠"
            valueMgDl > 180f -> "Υψηλή"
            else             -> "Εντός στόχου"
        }
        val n = NotificationCompat.Builder(context, CHANNEL_PERSISTENT)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("GlycoMate  ${valueMgDl.toInt()} $trendArrow")
            .setContentText(statusText)
            .setColor(color)
            .setColorized(true)
            .setOngoing(true)                   // Cannot be dismissed by swipe
            .setSilent(true)                    // No sound/vibration for persistent
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainActivityIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_PERSISTENT, n)
    }

    // ── Vibration patterns ────────────────────────────────────────────────────
    private fun vibrateUrgent() {
        // Long-short-long pattern for hypo (like an alarm)
        val pattern = longArrayOf(0, 500, 200, 500, 200, 800, 200, 800)
        vibrate(pattern)
    }

    private fun vibrateNormal() {
        // Short double vibration for hyper
        val pattern = longArrayOf(0, 200, 150, 200)
        vibrate(pattern)
    }

    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val v = (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        }
    }

    // ── Notification channels ─────────────────────────────────────────────────
    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        // HYPO — max importance, default alarm sound
        NotificationChannel(CHANNEL_HYPO, "🚨 Υπογλυκαιμία", NotificationManager.IMPORTANCE_HIGH).apply {
            description       = "Ειδοποίηση για χαμηλή γλυκόζη"
            enableVibration(true)
            vibrationPattern  = longArrayOf(0, 500, 200, 500, 200, 800)
            val audioAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttr)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)   // Bypass Do Not Disturb for hypo
            nm.createNotificationChannel(this)
        }

        // HYPER — high importance
        NotificationChannel(CHANNEL_HYPER, "↑ Υπεργλυκαιμία", NotificationManager.IMPORTANCE_HIGH).apply {
            description      = "Ειδοποίηση για υψηλή γλυκόζη"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 150, 200)
            val audioAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttr)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(this)
        }

        // PERSISTENT — silent, always-on status bar
        NotificationChannel(CHANNEL_PERSISTENT, "📊 Τρέχουσα γλυκόζη", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Εμφάνιση τρέχουσας γλυκόζης στο status bar"
            setSound(null, null)
            enableVibration(false)
            nm.createNotificationChannel(this)
        }

        // CGM SYNC — silent background
        NotificationChannel(CHANNEL_CGM, "🔄 Συγχρονισμός CGM", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Παρασκηνιακός συγχρονισμός CGM"
            setSound(null, null)
            enableVibration(false)
            nm.createNotificationChannel(this)
        }
    }

    private fun mainActivityIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private val CONSTRAINTS = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()

        /**
         * Starts the 3-minute sync chain. WorkManager enforces a minimum of 15 minutes
         * for PeriodicWorkRequests, so we use self-chaining OneTimeWorkRequests instead.
         */
        fun schedule(context: Context, @Suppress("UNUSED_PARAMETER") intervalMinutes: Long = 3L) {
            val request = OneTimeWorkRequestBuilder<CgmSyncWorker>()
                .setConstraints(CONSTRAINTS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * Schedules the next sync 3 minutes from now. Called at the end of every doWork().
         */
        internal fun scheduleNext(context: Context) {
            val request = OneTimeWorkRequestBuilder<CgmSyncWorker>()
                .setConstraints(CONSTRAINTS)
                .setInitialDelay(3, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<CgmSyncWorker>()
                .setConstraints(CONSTRAINTS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
