package com.glycomate.app.data.cgm.nightscout

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.glycomate.app.data.model.DataSource
import com.glycomate.app.data.model.GlucoseReading
import com.glycomate.app.data.model.GlucoseTrend
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val TAG = "NightscoutClient"

// ── Models ────────────────────────────────────────────────────────────────────
data class NsEntry(
    @SerializedName("_id")        val id:          String = "",
    @SerializedName("type")       val type:        String = "sgv",
    @SerializedName("sgv")        val sgv:         Int    = 0,
    @SerializedName("date")       val date:        Long   = 0L,   // epoch ms
    @SerializedName("dateString") val dateString:  String = "",
    @SerializedName("trend")      val trend:       Int    = 4,
    @SerializedName("direction")  val direction:   String = "Flat",
    @SerializedName("device")     val device:      String = ""
) {
    fun toGlucoseReading() = GlucoseReading(
        valueMgDl   = sgv.toFloat(),
        timestampMs = date,
        source      = DataSource.NIGHTSCOUT,
        trend       = direction.toNsTrend()
    )
}

data class NsStatus(
    @SerializedName("status")   val status:   String = "",
    @SerializedName("name")     val name:     String = "Nightscout",
    @SerializedName("version")  val version:  String = "",
    @SerializedName("settings") val settings: NsSettings? = null
)

data class NsSettings(
    @SerializedName("units") val units: String = "mg/dl"
)

// ── Retrofit interface ────────────────────────────────────────────────────────
interface NsApi {
    @GET("api/v1/status.json")
    suspend fun status(
        @Header("api-secret") hash: String? = null,
        @Query("token")       token: String? = null
    ): Response<NsStatus>

    @GET("api/v1/entries/sgv.json")
    suspend fun sgvLatest(
        @Header("api-secret") hash:  String? = null,
        @Query("count")       count: Int     = 1,
        @Query("token")       token: String? = null
    ): Response<List<NsEntry>>

    @GET("api/v1/entries/sgv.json")
    suspend fun sgvRange(
        @Header("api-secret")       hash:  String? = null,
        @Query("find[date]\$gte")   from:  Long,
        @Query("find[date]\$lte")   to:    Long    = System.currentTimeMillis(),
        @Query("count")             count: Int     = 288,
        @Query("token")             token: String? = null
    ): Response<List<NsEntry>>
}

// ── Result ────────────────────────────────────────────────────────────────────
sealed class NsResult<out T> {
    data class Success<T>(val data: T)                        : NsResult<T>()
    data class Error(val msg: String, val code: Int = -1)     : NsResult<Nothing>()
}

// ── Client ────────────────────────────────────────────────────────────────────
class NightscoutClient(
    baseUrl:             String,
    private val secret:  String = "",   // plain-text API secret
    private val token:   String = ""    // read-only access token
) {
    private val cleanUrl = baseUrl.trimEnd('/') + "/"

    // Nightscout needs SHA1 hash of plain-text API secret in header
    private val secretHash: String? = secret.ifBlank { null }?.let { sha1(it) }
    private val queryToken: String? = token.ifBlank { null }

    private val api: NsApi = buildApi()

    // ── Test connection ───────────────────────────────────────────────────────
    suspend fun testConnection(): NsResult<NsStatus> {
        return try {
            val resp = api.status(secretHash, queryToken)
            if (resp.isSuccessful && resp.body() != null)
                NsResult.Success(resp.body()!!)
            else
                handleError(resp.code(), resp.message())
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed", e)
            NsResult.Error("Αδύνατη σύνδεση: ${e.localizedMessage}")
        }
    }

    // ── Latest single reading ─────────────────────────────────────────────────
    suspend fun getLatestReading(): NsResult<GlucoseReading> {
        return try {
            val resp = api.sgvLatest(secretHash, 1, queryToken)
            if (resp.isSuccessful) {
                val entry = resp.body()?.firstOrNull { it.sgv > 0 }
                    ?: return NsResult.Error("Δεν βρέθηκαν δεδομένα. " +
                        "Ελέγξτε αν το Nightscout λαμβάνει δεδομένα από CGM.")
                Log.d(TAG, "Latest: ${entry.sgv} mg/dL @ ${entry.dateString}")
                NsResult.Success(entry.toGlucoseReading())
            } else handleError(resp.code(), resp.message())
        } catch (e: Exception) {
            NsResult.Error("Σφάλμα δικτύου: ${e.localizedMessage}")
        }
    }

    // ── History (last N hours) ────────────────────────────────────────────────
    suspend fun getHistory(hoursBack: Int = 12): NsResult<List<GlucoseReading>> {
        val from = System.currentTimeMillis() - hoursBack * 3_600_000L
        return try {
            val resp = api.sgvRange(
                hash  = secretHash,
                from  = from,
                count = hoursBack * 12,   // 5-min readings
                token = queryToken
            )
            if (resp.isSuccessful) {
                val readings = resp.body()
                    ?.filter { it.sgv > 0 }
                    ?.map { it.toGlucoseReading() }
                    ?.sortedBy { it.timestampMs }
                    ?: emptyList()
                NsResult.Success(readings)
            } else handleError(resp.code(), resp.message())
        } catch (e: Exception) {
            NsResult.Error("Σφάλμα δικτύου: ${e.localizedMessage}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun <T> handleError(code: Int, msg: String): NsResult<T> = when (code) {
        401  -> NsResult.Error("Λάθος API Secret / token", 401)
        403  -> NsResult.Error("Απαγορεύεται η πρόσβαση — ελέγξτε δικαιώματα", 403)
        404  -> NsResult.Error("Nightscout URL δεν βρέθηκε — ελέγξτε τη διεύθυνση", 404)
        else -> NsResult.Error("HTTP $code: $msg", code)
    }

    private fun buildApi(): NsApi {
        val logging = HttpLoggingInterceptor { Log.d(TAG, it) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NsApi::class.java)
    }

    companion object {
        fun sha1(input: String): String {
            val md    = MessageDigest.getInstance("SHA-1")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

// ── direction → GlucoseTrend ──────────────────────────────────────────────────
fun String.toNsTrend() = when (lowercase()) {
    "doubleup", "rapidlyincreasing"   -> GlucoseTrend.RISING_FAST
    "singleup", "increasing"          -> GlucoseTrend.RISING
    "fortyfiveup"                     -> GlucoseTrend.RISING_SLOW
    "flat", "steady"                  -> GlucoseTrend.STABLE
    "fortyfivedown"                   -> GlucoseTrend.FALLING_SLOW
    "singledown", "decreasing"        -> GlucoseTrend.FALLING
    "doubledown", "rapidlydecreasing" -> GlucoseTrend.FALLING_FAST
    else                              -> GlucoseTrend.STABLE
}
