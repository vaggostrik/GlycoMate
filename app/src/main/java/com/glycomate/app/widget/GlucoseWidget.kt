package com.glycomate.app.widget

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.glycomate.app.MainActivity
import com.glycomate.app.data.db.GlycoDatabase
import com.glycomate.app.data.model.GlucoseReading
import com.glycomate.app.data.model.InsulinType
import com.glycomate.app.data.model.UserProfile
import com.glycomate.app.data.prefs.GlycoPrefs
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class GlucoseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db      = GlycoDatabase.getInstance(context)
        val prefs   = GlycoPrefs(context)
        val profile = prefs.userProfile.first()

        val nowMs = System.currentTimeMillis()
        val diaMs = (profile.dia * 3_600_000L).toLong()

        // Latest reading from the last 24 h (ascending order → last = newest)
        val latestReading = db.glucoseDao()
            .getFrom(nowMs - 24 * 3_600_000L)
            .lastOrNull()

        // IOB — same linear-decay model as GlycoViewModel
        val iob = db.insulinDao().getFrom(nowMs - diaMs)
            .filter { it.type == InsulinType.RAPID }
            .sumOf { entry ->
                val hoursAgo  = (nowMs - entry.timestampMs) / 3_600_000f
                val remaining = (1f - hoursAgo / profile.dia).coerceAtLeast(0f)
                (entry.units * remaining).toDouble()
            }.toFloat()

        provideContent {
            GlucoseWidgetContent(latestReading, iob, profile)
        }
    }
}

@Composable
private fun GlucoseWidgetContent(
    reading: GlucoseReading?,
    iob:     Float,
    profile: UserProfile
) {
    val bgColor    = ColorProvider(Color(AndroidColor.parseColor("#1E1E2E")))
    val textGray   = ColorProvider(Color(AndroidColor.parseColor("#9E9E9E")))
    val colorGreen = ColorProvider(Color(AndroidColor.parseColor("#3FB950")))
    val colorAmber = ColorProvider(Color(AndroidColor.parseColor("#E3B341")))
    val colorRed   = ColorProvider(Color(AndroidColor.parseColor("#F85149")))
    val colorBlue  = ColorProvider(Color(AndroidColor.parseColor("#58A6FF")))

    val glucoseColor = when {
        reading == null                         -> textGray
        reading.valueMgDl < profile.targetLow  -> colorRed
        reading.valueMgDl > profile.targetHigh -> colorAmber
        else                                    -> colorGreen
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App label
            Text(
                text  = "GlycoMate",
                style = TextStyle(
                    color      = textGray,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(GlanceModifier.height(4.dp))

            // Glucose value + trend arrow
            if (reading != null) {
                Text(
                    text  = "${reading.valueMgDl.toInt()} ${reading.trend.arrow}",
                    style = TextStyle(
                        color      = glucoseColor,
                        fontSize   = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text  = "mg/dL",
                    style = TextStyle(color = textGray, fontSize = 11.sp)
                )
            } else {
                Text(
                    text  = "—",
                    style = TextStyle(color = textGray, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text  = "Χωρίς δεδομένα",
                    style = TextStyle(color = textGray, fontSize = 11.sp)
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            // Bottom row: IOB chip + last-seen time
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iob > 0.05f) {
                    Text(
                        text  = "IOB ${String.format("%.1f", iob)}U",
                        style = TextStyle(
                            color      = colorBlue,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(GlanceModifier.width(8.dp))
                }
                if (reading != null) {
                    Text(
                        text  = SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(reading.timestampMs)),
                        style = TextStyle(color = textGray, fontSize = 11.sp)
                    )
                }
            }
        }
    }
}
