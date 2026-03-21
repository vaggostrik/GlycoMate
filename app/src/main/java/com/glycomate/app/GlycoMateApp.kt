package com.glycomate.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.glycomate.app.data.repository.GlycoRepository
import com.glycomate.app.worker.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GlycoMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        
        // Ensure worker is scheduled if a source is configured
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val repo = GlycoRepository(this@GlycoMateApp)
            if (repo.prefs.cgmSource.first() != "NONE") {
                CgmSyncWorker.schedule(this@GlycoMateApp)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            
            // Hypo alerts
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_HYPO, "🚨 Υπογλυκαιμία",
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Ειδοποίηση για χαμηλή γλυκόζη"
                    enableVibration(true)
                })
                
            // Hyper alerts
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_HYPER, "↑ Υπεργλυκαιμία",
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Ειδοποίηση για υψηλή γλυκόζη"
                    enableVibration(true)
                })

            // Persistent status
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PERSISTENT, "📊 Τρέχουσα γλυκόζη",
                    NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Εμφάνιση τρέχουσας γλυκόζης στο status bar"
                })

            // Background sync
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_CGM, "🔄 Συγχρονισμός CGM",
                    NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Παρασκηνιακός συγχρονισμός"
                })
        }
    }
}
