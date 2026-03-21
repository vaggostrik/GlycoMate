package com.glycomate.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

// Foreground service stub — actual polling done via WorkManager (CgmSyncWorker)
// This class exists only because it is declared in AndroidManifest.xml
class CgmPollingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }
}
