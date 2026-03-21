package com.glycomate.app.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.glycomate.app.data.prefs.GlycoPrefs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

private const val TAG = "SosManager"

// ── Contact model ──────────────────────────────────────────────────────────────
data class SosContact(
    val id:    String = java.util.UUID.randomUUID().toString(),
    val name:  String,
    val phone: String,
    val role:  String = ""   // π.χ. "Γιατρός", "Σύζυγος"
)

fun List<SosContact>.toJson(): String {
    val arr = JSONArray()
    forEach { c ->
        arr.put(JSONObject().apply {
            put("id",    c.id)
            put("name",  c.name)
            put("phone", c.phone)
            put("role",  c.role)
        })
    }
    return arr.toString()
}

fun String.toSosContacts(): List<SosContact> {
    return try {
        val arr = JSONArray(this)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SosContact(
                id    = o.optString("id",    java.util.UUID.randomUUID().toString()),
                name  = o.getString("name"),
                phone = o.getString("phone"),
                role  = o.optString("role", "")
            )
        }
    } catch (e: Exception) { emptyList() }
}

// ── Result ────────────────────────────────────────────────────────────────────
sealed class SosResult {
    data class Success(val contactsNotified: Int, val locationIncluded: Boolean) : SosResult()
    data class Error(val message: String)   : SosResult()
    object NoContacts                        : SosResult()
}

// ── SOS Manager ───────────────────────────────────────────────────────────────
class SosManager(private val context: Context) {

    private val prefs = GlycoPrefs(context)

    /**
     * Main SOS trigger:
     * 1. Get GPS location (with timeout)
     * 2. Build message
     * 3. Send SMS to all contacts
     */
    suspend fun triggerSos(
        glucoseValue: Float? = null,
        userName:     String = "Χρήστης GlycoMate"
    ): SosResult {
        val contacts = prefs.sosContacts.first().toSosContacts()
        if (contacts.isEmpty()) return SosResult.NoContacts

        Log.d(TAG, "SOS triggered! Contacts: ${contacts.size}, glucose: $glucoseValue")

        // Try to get location (10 second timeout)
        val location = tryGetLocation()
        Log.d(TAG, "Location: $location")

        // Build SMS
        val message = buildSosMessage(userName, glucoseValue, location)

        // Send SMS to all contacts
        val hasSmsPermission = ContextCompat.checkSelfPermission(context,
            Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

        var sent = 0
        contacts.forEach { contact ->
            try {
                if (hasSmsPermission) {
                    sendSms(contact.phone, message)
                    sent++
                    Log.d(TAG, "SMS sent to ${contact.name} (${contact.phone})")
                } else {
                    Log.w(TAG, "No SMS permission — cannot send to ${contact.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to ${contact.name}", e)
            }
        }

        return SosResult.Success(
            contactsNotified = sent,
            locationIncluded = location != null
        )
    }

    private fun buildSosMessage(name: String, glucose: Float?, location: Location?): String {
        val sb = StringBuilder()
        sb.append("🚨 SOS από GlycoMate\n")
        sb.append("Ο/Η $name χρειάζεται βοήθεια!")
        if (glucose != null) {
            sb.append("\nΓλυκόζη: ${glucose.toInt()} mg/dL")
            if (glucose < 55f) sb.append(" ⚠️ ΕΠΙΚΙΝΔΥΝΑ ΧΑΜΗΛΗ")
            else if (glucose < 70f) sb.append(" ⚠️ ΧΑΜΗΛΗ")
        }
        if (location != null) {
            val lat = String.format("%.5f", location.latitude)
            val lon = String.format("%.5f", location.longitude)
            sb.append("\n📍 Τοποθεσία: https://maps.google.com/?q=$lat,$lon")
        } else {
            sb.append("\n📍 Τοποθεσία: Δεν διατίθεται")
        }
        sb.append("\nΣτείλτε βοήθεια αμέσως.")
        return sb.toString()
    }

    private fun sendSms(phone: String, message: String) {
        val smsManager = context.getSystemService(SmsManager::class.java)
            ?: SmsManager.getDefault()
        // Split if message is too long (>160 chars for single SMS)
        val parts = smsManager.divideMessage(message)
        if (parts.size == 1) {
            smsManager.sendTextMessage(phone, null, message, null, null)
        } else {
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
        }
    }

    private suspend fun tryGetLocation(): Location? {
        val hasPermission = ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                val client   = LocationServices.getFusedLocationProviderClient(context)
                val request  = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                    .setMaxUpdates(1)
                    .build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        client.removeLocationUpdates(this)
                        cont.resume(result.lastLocation)
                    }
                }
                // First try last known location — only if it's fresh (< 2 minutes old)
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null && !cont.isCompleted) {
                        val ageMs = System.currentTimeMillis() - loc.time
                        if (ageMs < 120_000L) {
                            client.removeLocationUpdates(callback)
                            cont.resume(loc)
                        }
                        // else: stale cache — wait for a fresh fix from requestLocationUpdates
                    }
                }
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                cont.invokeOnCancellation { client.removeLocationUpdates(callback) }
            }
        }
    }
}
