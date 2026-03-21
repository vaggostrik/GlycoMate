package com.glycomate.wear.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.*

private const val TAG = "WearDataListener"
val Context.wearStore by preferencesDataStore("wear_glucose")
val KEY_GLUCOSE_JSON = stringPreferencesKey("glucose_json")

class WearDataListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            val path = event.dataItem.uri.path
            Log.d(TAG, "Data changed: $path")
            if (path == WearPaths.GLUCOSE_PATH) {
                val json = event.dataItem.data?.let { String(it) } ?: return@forEach
                Log.d(TAG, "Received glucose: $json")
                // Persist to local DataStore for complication + activity
                scope.launch {
                    applicationContext.wearStore.edit { it[KEY_GLUCOSE_JSON] = json }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
