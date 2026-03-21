package com.glycomate.app.data.cgm.dexcom

import android.util.Log
import com.glycomate.app.data.model.DataSource
import com.glycomate.app.data.model.GlucoseReading
import com.glycomate.app.data.model.GlucoseTrend
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

private const val TAG = "DexcomShare"

/**
 * Dexcom Share API — unofficial, community-documented.
 * Used by xDrip, Nightscout, pydexcom and many others.
 *
 * Flow: username+password → accountId → sessionId → readings
 *
 * NOTE: This is NOT the official Dexcom Web API (which requires developer approval
 * and has a 3-hour delay outside the US). This is the Share/Follow API used by
 * the Dexcom Follow app — it gives real-time readings.
 */

// ── Regions ───────────────────────────────────────────────────────────────────
enum class DexcomRegion(val baseUrl: String) {
    US ("https://share2.dexcom.com/ShareWebServices/Services"),
    OUS("https://shareous1.dexcom.com/ShareWebServices/Services"),   // Outside US (Europe, etc.)
    JP ("https://share.dexcom.jp/ShareWebServices/Services");

    companion object {
        fun fromCode(code: String) = entries.find { it.name == code } ?: OUS
    }
}

// App ID — same for US and OUS (from community documentation)
private const val DEXCOM_APP_ID = "d89443d2-327c-4a6f-89e5-496bbb0317db"

// ── API models ────────────────────────────────────────────────────────────────
data class DexcomAuthRequest(
    val accountName:   String,
    val password:      String,
    val applicationId: String = DEXCOM_APP_ID
)

data class DexcomSessionRequest(
    val accountId:     String,
    val password:      String,
    val applicationId: String = DEXCOM_APP_ID
)

data class DexcomGlucoseValue(
    val WT:    String = "",   // wt timestamp format: "Date(1234567890000)"
    val ST:    String = "",
    val DT:    String = "",
    val Value: Int    = 0,
    val Trend: String = "Flat"
)

// ── Retrofit interface ────────────────────────────────────────────────────────
interface DexcomShareApi {

    @POST("General/AuthenticatePublisherAccount")
    suspend fun getAccountId(@Body req: DexcomAuthRequest): Response<String>

    @POST("General/LoginPublisherAccountById")
    suspend fun getSessionId(@Body req: DexcomSessionRequest): Response<String>

    @GET("Publisher/ReadPublisherLatestGlucoseValues")
    suspend fun getLatestGlucose(
        @Query("sessionId") sessionId: String,
        @Query("minutes")   minutes:   Int = 1440,
        @Query("maxCount")  maxCount:  Int = 1
    ): Response<List<DexcomGlucoseValue>>
}

// ── Result ────────────────────────────────────────────────────────────────────
sealed class DexcomResult<out T> {
    data class Success<T>(val data: T)                        : DexcomResult<T>()
    data class Error(val msg: String, val code: Int = -1)     : DexcomResult<Nothing>()
}

// ── Client ────────────────────────────────────────────────────────────────────
class DexcomShareClient(private val region: DexcomRegion = DexcomRegion.OUS) {

    private val api = buildApi(region.baseUrl)

    // In-memory session cache
    private var sessionId:      String = ""
    private var sessionExpiry:  Long   = 0L

    private val sessionValid: Boolean
        get() = sessionId.isNotBlank() && System.currentTimeMillis() < sessionExpiry

    suspend fun login(username: String, password: String): DexcomResult<String> {
        return try {
            Log.d(TAG, "Dexcom login (${region.name})…")

            // Step 1: Get Account ID from username+password
            val accountResp = api.getAccountId(DexcomAuthRequest(username, password))
            if (!accountResp.isSuccessful) {
                val err = accountResp.errorBody()?.string() ?: ""
                return when (accountResp.code()) {
                    500 -> DexcomResult.Error("Λάθος username ή password Dexcom")
                    else -> DexcomResult.Error("HTTP ${accountResp.code()}: $err")
                }
            }

            // accountId comes back as a quoted string: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            val accountId = accountResp.body()?.trim('"') ?: ""
            if (accountId.isBlank() || accountId == "00000000-0000-0000-0000-000000000000") {
                return DexcomResult.Error("Λάθος username ή password Dexcom")
            }
            Log.d(TAG, "AccountId: ${accountId.take(8)}…")

            // Step 2: Get Session ID from Account ID
            val sessionResp = api.getSessionId(DexcomSessionRequest(accountId, password))
            if (!sessionResp.isSuccessful) {
                return DexcomResult.Error("Αποτυχία δημιουργίας session: HTTP ${sessionResp.code()}")
            }

            val sid = sessionResp.body()?.trim('"') ?: ""
            if (sid.isBlank() || sid == "00000000-0000-0000-0000-000000000000") {
                return DexcomResult.Error("Άκυρο session ID — δοκίμασε ξανά")
            }

            sessionId     = sid
            sessionExpiry = System.currentTimeMillis() + 4 * 3600_000L  // sessions last ~4h
            Log.d(TAG, "Session OK: ${sid.take(8)}…")
            DexcomResult.Success(sid)

        } catch (e: Exception) {
            Log.e(TAG, "Dexcom login exception", e)
            DexcomResult.Error("Σφάλμα δικτύου: ${e.localizedMessage}")
        }
    }

    suspend fun getLatestReading(
        username: String,
        password: String
    ): DexcomResult<GlucoseReading> {
        // Auto re-login if session expired
        if (!sessionValid) {
            when (val r = login(username, password)) {
                is DexcomResult.Error -> return r
                else -> { /* session refreshed */ }
            }
        }

        return try {
            val resp = api.getLatestGlucose(sessionId, minutes = 1440, maxCount = 1)
            when {
                resp.isSuccessful -> {
                    val value = resp.body()?.firstOrNull()
                        ?: return DexcomResult.Error(
                            "Δεν υπάρχουν πρόσφατες μετρήσεις.\n" +
                            "Βεβαιώσου ότι:\n" +
                            "• Ο αισθητήρας Dexcom είναι ενεργός\n" +
                            "• Το Sharing είναι ενεργοποιημένο στην εφαρμογή Dexcom"
                        )
                    DexcomResult.Success(value.toGlucoseReading())
                }
                resp.code() == 500 -> {
                    // Session likely expired
                    sessionId = ""
                    getLatestReading(username, password)
                }
                else -> DexcomResult.Error("HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            DexcomResult.Error("Σφάλμα δικτύου: ${e.localizedMessage}")
        }
    }

    private fun buildApi(baseUrl: String): DexcomShareApi {
        val logging = HttpLoggingInterceptor { Log.d(TAG, it) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Dexcom Share/3.0.2.11 CFNetwork/711.2.23 Darwin/14.0.0")
                    .build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DexcomShareApi::class.java)
    }
}

// ── DexcomGlucoseValue → GlucoseReading ──────────────────────────────────────
fun DexcomGlucoseValue.toGlucoseReading(): GlucoseReading {
    // Dexcom timestamp format: "Date(1234567890123)"
    val ms = runCatching {
        WT.removePrefix("Date(").removeSuffix(")").toLong()
    }.getOrDefault(System.currentTimeMillis())

    return GlucoseReading(
        valueMgDl   = Value.toFloat(),
        timestampMs = ms,
        trend       = Trend.toDexcomTrend(),
        source      = DataSource.DEXCOM
    )
}

private fun String.toDexcomTrend() = when (lowercase()) {
    "doubleup", "rapidlyincreasing"    -> GlucoseTrend.RISING_FAST
    "singleup", "increasing"           -> GlucoseTrend.RISING
    "fortyfiveup"                      -> GlucoseTrend.RISING_SLOW
    "flat", "steady"                   -> GlucoseTrend.STABLE
    "fortyfivedown"                    -> GlucoseTrend.FALLING_SLOW
    "singledown", "decreasing"         -> GlucoseTrend.FALLING
    "doubledown", "rapidlydecreasing"  -> GlucoseTrend.FALLING_FAST
    else                               -> GlucoseTrend.STABLE
}
