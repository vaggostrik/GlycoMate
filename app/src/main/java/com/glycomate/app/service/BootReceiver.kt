package com.glycomate.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.glycomate.app.data.prefs.GlycoPrefs
import com.glycomate.app.worker.CgmSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("BootReceiver", "Boot completed — checking if CGM was active")
        CoroutineScope(Dispatchers.IO).launch {
            val prefs  = GlycoPrefs(context)
            val source = prefs.cgmSource.first()
            if (source != "NONE") {
                Log.d("BootReceiver", "Re-scheduling CGM sync (source=$source)")
                CgmSyncWorker.schedule(context)
            }
        }
    }
}
