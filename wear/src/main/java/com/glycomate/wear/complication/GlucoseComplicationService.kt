package com.glycomate.wear.complication

import android.content.Context
import android.graphics.drawable.Icon
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.glycomate.wear.data.WearGlucoseData
import com.glycomate.wear.data.wearStore
import kotlinx.coroutines.flow.first

class GlucoseComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val preview = WearGlucoseData(112f, "→", System.currentTimeMillis(),
            "IN_RANGE", 70f, 180f)
        return buildComplication(type, preview)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val json = try {
            applicationContext.wearStore.data.first()[stringPreferencesKey("glucose_json")]
        } catch (e: Exception) { null }

        val data = json?.let { WearGlucoseData.fromJson(it) }
            ?: return buildNoDataComplication(request.complicationType)

        return buildComplication(request.complicationType, data)
    }

    private fun buildComplication(type: ComplicationType, data: WearGlucoseData): ComplicationData {
        val valueText   = "${data.valueMgDl.toInt()} ${data.trendArrow}"
        val titleText   = when {
            data.isLow  -> "Χαμηλή ⚠"
            data.isHigh -> "Υψηλή"
            else        -> "Εντός"
        }

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    text  = PlainComplicationText.Builder(valueText).build(),
                    contentDescription = PlainComplicationText.Builder("Γλυκόζη $valueText").build()
                )
                .setTitle(PlainComplicationText.Builder(titleText).build())
                .setTapAction(null)
                .build()

            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("${data.valueMgDl.toInt()} mg/dL ${data.trendArrow}").build(),
                    contentDescription = PlainComplicationText.Builder("Γλυκόζη ${data.valueMgDl.toInt()}").build()
                )
                .setTitle(PlainComplicationText.Builder(titleText).build())
                .build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value    = data.valueMgDl,
                    min      = data.targetLow,
                    max      = data.targetHigh.coerceAtMost(300f),
                    contentDescription = PlainComplicationText.Builder("Γλυκόζη").build()
                )
                .setText(PlainComplicationText.Builder(valueText).build())
                .setTitle(PlainComplicationText.Builder(titleText).build())
                .build()

            else -> buildNoDataComplication(type)
        }
    }

    private fun buildNoDataComplication(type: ComplicationType): ComplicationData =
        when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("—").build(),
                    contentDescription = PlainComplicationText.Builder("Δεν υπάρχουν δεδομένα").build()
                ).setTitle(PlainComplicationText.Builder("Γλυκ.").build()).build()
            else -> NoDataComplicationData()
        }
}
