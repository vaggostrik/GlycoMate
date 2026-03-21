package com.glycomate.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.wear.compose.material.*
import com.glycomate.wear.data.WearGlucoseData
import com.glycomate.wear.data.wearStore
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlucoseWatchScreen(context = this)
        }
    }
}

@Composable
fun GlucoseWatchScreen(context: android.content.Context) {
    val glucoseFlow = remember {
        context.wearStore.data.map { prefs ->
            prefs[stringPreferencesKey("glucose_json")]?.let { WearGlucoseData.fromJson(it) }
        }
    }
    val glucoseData by glucoseFlow.collectAsState(initial = null)

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (glucoseData == null) {
                NoDataScreen()
            } else {
                GlucoseScreen(data = glucoseData!!)
            }
        }
    }
}

@Composable
private fun GlucoseScreen(data: WearGlucoseData) {
    val glColor = when {
        data.isLow  -> Color(0xFFF85149)
        data.isHigh -> Color(0xFFE3B341)
        else        -> Color(0xFF3FB950)
    }
    val statusText = when {
        data.isLow  -> "⚠ Χαμηλή"
        data.isHigh -> "↑ Υψηλή"
        else        -> "✓ Εντός"
    }
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glucose value — big and visible
        Text(
            text      = "${data.valueMgDl.toInt()}",
            fontSize  = 52.sp,
            fontWeight= FontWeight.W900,
            color     = glColor,
            textAlign = TextAlign.Center
        )

        // Trend + unit
        Text(
            text      = "${data.trendArrow}  mg/dL",
            fontSize  = 16.sp,
            color     = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        // Status badge
        Text(
            text      = statusText,
            fontSize  = 13.sp,
            color     = glColor,
            fontWeight= FontWeight.W600,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        // Last updated
        Text(
            text      = sdf.format(Date(data.timestampMs)),
            fontSize  = 11.sp,
            color     = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        // Target range indicator
        Text(
            text      = "Στόχος: ${data.targetLow.toInt()}–${data.targetHigh.toInt()}",
            fontSize  = 10.sp,
            color     = Color.White.copy(alpha = 0.3f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NoDataScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📱", fontSize = 28.sp)
        Spacer(Modifier.height(8.dp))
        Text("Άνοιξε το\nGlycoMate\nστο κινητό",
            fontSize  = 13.sp,
            color     = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center)
    }
}
