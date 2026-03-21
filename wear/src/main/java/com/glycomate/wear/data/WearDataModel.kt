package com.glycomate.wear.data

// ── Shared constants (phone ↔ watch) ─────────────────────────────────────────
object WearPaths {
    const val GLUCOSE_PATH = "/glucose/update"
    const val GLUCOSE_KEY  = "glucose_json"
}

// ── Glucose snapshot sent to watch ───────────────────────────────────────────
data class WearGlucoseData(
    val valueMgDl:  Float,
    val trendArrow: String,
    val timestampMs: Long,
    val status:     String,   // "LOW", "IN_RANGE", "HIGH"
    val targetLow:  Float,
    val targetHigh: Float
) {
    val isLow:   Boolean get() = valueMgDl < targetLow
    val isHigh:  Boolean get() = valueMgDl > targetHigh
    val color:   Long    get() = when {
        isLow  -> 0xFFF85149L
        isHigh -> 0xFFE3B341L
        else   -> 0xFF3FB950L
    }

    fun toJson(): String =
        """{"v":$valueMgDl,"t":"$trendArrow","ts":$timestampMs,"s":"$status","lo":$targetLow,"hi":$targetHigh}"""

    companion object {
        fun fromJson(json: String): WearGlucoseData? = runCatching {
            fun get(key: String): String = Regex(""""$key":"?([^",}]+)"?""").find(json)!!.groupValues[1]
            WearGlucoseData(
                valueMgDl   = get("v").toFloat(),
                trendArrow  = get("t"),
                timestampMs = get("ts").toLong(),
                status      = get("s"),
                targetLow   = get("lo").toFloat(),
                targetHigh  = get("hi").toFloat()
            )
        }.getOrNull()
    }
}
