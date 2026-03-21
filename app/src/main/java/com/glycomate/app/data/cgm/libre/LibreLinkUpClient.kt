package com.glycomate.app.data.cgm.libre

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.glycomate.app.data.model.DataSource
import com.glycomate.app.data.model.GlucoseReading
import com.glycomate.app.data.model.GlucoseTrend
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private const val TAG = "LluClient"

private const val LLU_VERSION = "4.17.0"
private const val LLU_PRODUCT = "llu.android"

enum class LluRegion(val baseUrl: String) {
    EU("https://api-eu.libreview.io"),
    US("https://api-us.libreview.io"),
    DE("https://api-de.libreview.io"),
    FR("https://api-fr.libreview.io"),
    AP("https://api-ap.libreview.io"),
    AU("https://api-au.libreview.io");
    companion object {
        fun fromCode(code: String) = entries.find { it.name == code } ?: EU
    }
}

data class LluLoginRequest(val email: String, val password: String)
data class LluLoginResponse(val status: Int, val data: LluAuthData?, val error: String? = null)
data class LluAuthData(val authTicket: LluTicket, val user: LluUser?)
data class LluTicket(val token: String, val expires: Long, val duration: Long)
data class LluUser(val id: String, val firstName: String = "", val lastName: String = "")

data class LluConnectionsResponse(val status: Int, val data: List<LluConnection>?)
data class LluConnection(
    val id: String = "",
    val patientId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val glucoseMeasurement: LluMeasurement? = null,
    val glucoseItem: LluMeasurement? = null,
    val targetLow: Int = 70,
    val targetHigh: Int = 180
)

data class LluGraphResponse(val status: Int, val data: LluGraphData?)
data class LluGraphData(val connection: LluConnection?, val graphData: List<LluMeasurement>?)

data class LluMeasurement(
    @SerializedName("FactoryTimestamp") val factoryTimestamp: String? = null,
    @SerializedName("Timestamp") val timestamp: String? = null,
    @SerializedName("ValueInMgPerDl") val valueInMgPerDl: Int = 0,
    @SerializedName("Value") val value: Double = 0.0,
    @SerializedName("MeasurementColor") val measurementColor: Int = 4,
    @SerializedName("TrendArrow") val trendArrow: Int? = null,
    @SerializedName("isHigh") val isHigh: Boolean = false,
    @SerializedName("isLow") val isLow: Boolean = false
)

interface LluApi {
    @POST("llu/auth/login")
    suspend fun login(@Body req: LluLoginRequest): Response<LluLoginResponse>

    @GET("llu/connections")
    suspend fun connections(
        @Header("Authorization") bearer: String,
        @Header("Account-Id") accountId: String
    ): Response<LluConnectionsResponse>

    @GET("llu/connections/{pid}/graph")
    suspend fun graph(
        @Header("Authorization") bearer: String,
        @Header("Account-Id") accountId: String,
        @Path("pid") patientId: String
    ): Response<LluGraphResponse>
}

sealed class LluResult<out T> {
    data class Success<T>(val data: T) : LluResult<T>()
    data class Error(val msg: String, val code: Int = -1) : LluResult<Nothing>()
}

data class LluSession(
    val token: String,
    val expiresMs: Long,
    val userId: String,
    val accountId: String,
    val patientId: String = ""
) {
    val isValid: Boolean get() = token.isNotBlank() && System.currentTimeMillis() < expiresMs - 120_000L
}

class LibreLinkUpClient(private val region: LluRegion = LluRegion.EU) {
    private val api: LluApi = buildApi(region.baseUrl)
    private var session: LluSession? = null

    suspend fun login(email: String, password: String): LluResult<LluSession> {
        return try {
            val resp = api.login(LluLoginRequest(email, password))
            if (!resp.isSuccessful) return LluResult.Error("HTTP ${resp.code()}")
            val body = resp.body()
            when (body?.status) {
                0 -> {
                    val data = body.data ?: return LluResult.Error("No data")
                    val userId = data.user?.id ?: ""
                    val accountId = sha256(userId)
                    val newSession = LluSession(data.authTicket.token, data.authTicket.expires * 1000L, userId, accountId)
                    session = newSession
                    LluResult.Success(newSession)
                }
                else -> LluResult.Error("Status ${body?.status}")
            }
        } catch (e: Exception) { LluResult.Error(e.localizedMessage ?: "Error") }
    }

    /**
     * Returns the latest single reading (for compatibility). Internally uses getReadings().
     */
    suspend fun getLatestReading(email: String, password: String): LluResult<Pair<GlucoseReading, String>> {
        return when (val r = getReadings(email, password)) {
            is LluResult.Success -> {
                val latest = r.data.first.maxByOrNull { it.timestampMs }
                    ?: return LluResult.Error("No reading")
                LluResult.Success(Pair(latest, r.data.second))
            }
            is LluResult.Error -> r
        }
    }

    /**
     * Returns ALL recent readings from the graph endpoint + current reading.
     * This gives us every sensor point since the last sync instead of just one.
     */
    suspend fun getReadings(email: String, password: String): LluResult<Pair<List<GlucoseReading>, String>> {
        val s = ensureSession(email, password) ?: return LluResult.Error("Login failed")
        return try {
            val connResp = api.connections("Bearer ${s.token}", s.accountId)
            if (!connResp.isSuccessful) {
                if (connResp.code() == 401) { session = null; return getReadings(email, password) }
                return LluResult.Error("HTTP ${connResp.code()}")
            }
            val conn = connResp.body()?.data?.firstOrNull() ?: return LluResult.Error("No connections")
            val patientId = conn.patientId
            session = s.copy(patientId = patientId)

            val readings = mutableListOf<GlucoseReading>()
            // Authoritative current reading from /connections — added first so it wins distinctBy
            val currentMeasurement = conn.glucoseMeasurement ?: conn.glucoseItem
            currentMeasurement?.toGlucoseReading()?.let { readings.add(it) }

            // Fetch graph data (last few hours) for timeline backfill only.
            // Only include entries with a parseable timestamp to avoid the System.currentTimeMillis()
            // fallback turning historical entries into the "latest" reading and showing a stale trend.
            if (patientId.isNotBlank()) {
                runCatching {
                    val graphResp = api.graph("Bearer ${s.token}", s.accountId, patientId)
                    if (graphResp.isSuccessful) {
                        val graphData = graphResp.body()?.data

                        // Also consider the graph response's own current measurement —
                        // it can be more up-to-date than the /connections response.
                        graphData?.connection?.let { graphConn ->
                            (graphConn.glucoseMeasurement ?: graphConn.glucoseItem)
                                ?.takeIf { it.timestamp != null }
                                ?.toGlucoseReading()
                                ?.let { readings.add(0, it) } // prepend so it wins distinctBy
                        }

                        graphData?.graphData
                            ?.mapNotNull { m ->
                                // Skip entries without a timestamp — toGlucoseReading() would fall
                                // back to System.currentTimeMillis(), making them appear newer than
                                // the real current reading and displaying a stale trend arrow.
                                if (m.timestamp == null) return@mapNotNull null
                                if (m.valueInMgPerDl <= 0 && m.value <= 0.0) return@mapNotNull null
                                m.toGlucoseReading()
                            }
                            ?.let { readings.addAll(it) }
                    }
                }
            }

            if (readings.isEmpty()) return LluResult.Error("No reading")
            // Sort descending so the most-recent entry is first; distinctBy keeps first occurrence,
            // ensuring the genuine latest reading (highest timestamp) survives deduplication.
            LluResult.Success(Pair(readings.sortedByDescending { it.timestampMs }.distinctBy { it.timestampMs }, patientId))
        } catch (e: Exception) { LluResult.Error(e.localizedMessage ?: "Error") }
    }

    private suspend fun ensureSession(email: String, password: String): LluSession? {
        if (session?.isValid == true) return session
        return (login(email, password) as? LluResult.Success)?.data
    }

    private fun buildApi(baseUrl: String): LluApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { it.proceed(it.request().newBuilder().header("product", LLU_PRODUCT).header("version", LLU_VERSION).build()) }
            .build()
        return Retrofit.Builder().baseUrl("$baseUrl/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(LluApi::class.java)
    }

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

private val lluDateFormats = listOf(
    SimpleDateFormat("M/d/yyyy h:mm:ss a", Locale.US),
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
)

fun LluMeasurement.toGlucoseReading(): GlucoseReading {
    val ms = timestamp?.let { r -> lluDateFormats.firstNotNullOfOrNull { runCatching { it.parse(r)?.time }.getOrNull() } } ?: System.currentTimeMillis()
    val finalValue = if (valueInMgPerDl > 0) valueInMgPerDl.toFloat() else value.toFloat()

    // FIXED mapping based on LLU internal values:
    // 1: DoubleDown (↓↓)
    // 2: SingleDown (↓)
    // 3: FortyFiveDown (↘)
    // 4: Flat (→)
    // 5: FortyFiveUp (↗)
    // 6: SingleUp (↑)
    // 7: DoubleUp (↑↑)
    val trend = when (trendArrow) {
        1 -> GlucoseTrend.FALLING_FAST
        2 -> GlucoseTrend.FALLING
        3 -> GlucoseTrend.FALLING_SLOW
        4 -> GlucoseTrend.STABLE
        5 -> GlucoseTrend.RISING_SLOW
        6 -> GlucoseTrend.RISING
        7 -> GlucoseTrend.RISING_FAST
        else -> GlucoseTrend.STABLE
    }

    return GlucoseReading(valueMgDl = finalValue, timestampMs = ms, trend = trend, source = DataSource.LIBRE_LINK_UP)
}
