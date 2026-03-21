package com.glycomate.app.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.glycomate.app.data.model.GlucoseReading
import com.glycomate.app.data.model.glucoseStatus
import com.glycomate.app.data.model.GlucoseStatus
import kotlinx.coroutines.tasks.await

private const val TAG = "WearableSender"

object WearableSender {

    /**
     * Sends latest glucose reading to all connected Wear OS watches.
     * This is a fire-and-forget — if no watch is connected, silently succeeds.
     */
    suspend fun sendGlucose(
        context:    Context,
        reading:    GlucoseReading,
        targetLow:  Float,
        targetHigh: Float
    ) {
        try {
            val status = when (reading.glucoseStatus(targetLow, targetHigh)) {
                GlucoseStatus.LOW      -> "LOW"
                GlucoseStatus.IN_RANGE -> "IN_RANGE"
                GlucoseStatus.HIGH     -> "HIGH"
            }
            val json = """{"v":${reading.valueMgDl},"t":"${reading.trend.arrow}","ts":${reading.timestampMs},"s":"$status","lo":$targetLow,"hi":$targetHigh}"""

            val request = PutDataMapRequest.create("/glucose/update").apply {
                dataMap.putString("glucose_json", json)
                dataMap.putLong("timestamp", System.currentTimeMillis()) // force update
            }.asPutDataRequest().setUrgent()

            val client = Wearable.getDataClient(context)
            client.putDataItem(request).await()
            Log.d(TAG, "Sent to watch: ${reading.valueMgDl} mg/dL")
        } catch (e: Exception) {
            // Non-critical — watch might not be connected
            Log.w(TAG, "Could not send to watch (not connected?): ${e.localizedMessage}")
        }
    }
}
