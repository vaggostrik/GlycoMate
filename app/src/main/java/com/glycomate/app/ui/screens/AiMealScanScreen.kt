package com.glycomate.app.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.glycomate.app.ui.theme.*
import com.glycomate.app.data.model.InsulinType
import com.glycomate.app.viewmodel.GlycoViewModel
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

// ── Data class for parsed AI result ──────────────────────────────────────────
data class FoodItem(
    val name:       String,
    val carbsGrams: Float,
    val weightGrams: Float,
    val giLevel:    String
)

data class MealScanResult(
    val foods:          List<FoodItem>,
    val totalCarbs:     Float,
    val totalCalories:  Float,
    val suggestedDose:  Float,
    val aiConfidence:   Int,
    val rawDescription: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMealScanScreen(
    viewModel: GlycoViewModel,
    onBack: () -> Unit
) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()

    var photoUri    by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var statusMsg   by remember { mutableStateOf("") }
    var scanResult  by remember { mutableStateOf<MealScanResult?>(null) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var apiKey      by remember { mutableStateOf("") }
    var showApiKeyInput by remember { mutableStateOf(false) }

    val savedKey by viewModel.repo.prefs.openAiKey.collectAsState(initial = "")
    LaunchedEffect(savedKey) { if (apiKey.isBlank()) apiKey = savedKey }

    // We use a temporary URI to avoid caching issues with the same filename
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri = tempCameraUri
            scanResult = null
            errorMsg = null
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // Generate a unique filename for each photo to bust Coil's cache
            val file = File(context.cacheDir, "meal_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            errorMsg = "Απαιτείται άδεια κάμερας"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { photoUri = it; scanResult = null; errorMsg = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Σκανάρισμα γεύματος", fontWeight = FontWeight.W700) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Πίσω") } },
                actions = {
                    IconButton(onClick = { showApiKeyInput = !showApiKeyInput }) {
                        Icon(Icons.Filled.Key, null, tint = if (apiKey.isNotBlank()) GlycoGreen else MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {

            AnimatedVisibility(visible = showApiKeyInput || apiKey.isBlank()) {
                ApiKeyCard(currentKey = apiKey, onSave = { key ->
                    apiKey = key
                    scope.launch { viewModel.repo.prefs.saveOpenAiKey(key) }
                    showApiKeyInput = false
                })
            }

            PhotoArea(photoUri = photoUri, onCamera = {
                permLauncher.launch(Manifest.permission.CAMERA)
            }, onGallery = { galleryLauncher.launch("image/*") }, onClearPhoto = { photoUri = null; scanResult = null })

            if (photoUri != null && scanResult == null) {
                Button(
                    onClick = {
                        if (apiKey.isBlank()) { errorMsg = "Βάλε το Groq API key (πάτα το 🔑)"; return@Button }
                        isAnalyzing = true; statusMsg = "Ανάλυση..."; errorMsg = null
                        scope.launch(Dispatchers.IO) {
                            val result = analyzeMealPhoto(context, photoUri!!, apiKey, viewModel::calculateBolus) { msg ->
                                scope.launch(Dispatchers.Main) { statusMsg = msg }
                            }
                            withContext(Dispatchers.Main) {
                                isAnalyzing = false; statusMsg = ""
                                result.fold(onSuccess = { scanResult = it }, onFailure = { errorMsg = it.localizedMessage ?: it.message ?: "Σφάλμα ανάλυσης AI. Δοκίμασε ξανά." })
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !isAnalyzing
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp)); Text(statusMsg.ifBlank { "Ανάλυση..." })
                    } else {
                        Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Ανάλυση γεύματος με AI")
                    }
                }
            }

            errorMsg?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            scanResult?.let { result ->
                ScanResultCard(result = result, onLog = {
                    viewModel.logMeal(result.rawDescription, result.totalCarbs)
                    if (result.suggestedDose > 0f) viewModel.logInsulin(result.suggestedDose, InsulinType.RAPID, "AI scan")
                    onBack()
                }, onCancel = { scanResult = null; photoUri = null })
            }
        }
    }
}

@Composable
private fun ApiKeyCard(currentKey: String, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf(currentKey) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Groq API Key", fontWeight = FontWeight.W600)
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API Key (gsk_...)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(key.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("Αποθήκευση") }
        }
    }
}

@Composable
private fun PhotoArea(photoUri: Uri?, onCamera: () -> Unit, onGallery: () -> Unit, onClearPhoto: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        if (photoUri != null) {
            Box {
                Image(painter = rememberAsyncImagePainter(photoUri), contentDescription = null, modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                IconButton(onClick = onClearPhoto, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))) {
                    Icon(Icons.Filled.Close, null, tint = Color.White)
                }
            }
        } else {
            Column(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCamera, modifier = Modifier.weight(1f)) { Text("Κάμερα") }
                    OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) { Text("Γκαλερί") }
                }
            }
        }
    }
}

@Composable
private fun ScanResultCard(result: MealScanResult, onLog: () -> Unit, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Αποτέλεσμα AI (${result.aiConfidence}% εμπιστοσύνη)", fontWeight = FontWeight.W600)
            result.foods.forEach { food ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(food.name, modifier = Modifier.weight(1f))
                    Text("${food.carbsGrams.toInt()}g carbs", fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider()
            Text("Σύνολο: ${result.totalCarbs.toInt()}g υδατάνθρακες", fontWeight = FontWeight.W700)
            if (result.suggestedDose > 0f) {
                Text("Προτεινόμενη δόση: ${String.format("%.1f", result.suggestedDose)}U", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.W700)
            }
            Button(onClick = onLog, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GlycoGreen)) { Text("Καταγραφή") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Ακύρωση") }
        }
    }
}

private suspend fun analyzeMealPhoto(context: Context, photoUri: Uri, apiKey: String, suggestDose: (Float) -> Float, onStatus: suspend (String) -> Unit = {}): Result<MealScanResult> = runCatching {
    val base64 = context.contentResolver.openInputStream(photoUri)?.use { stream ->
        val bitmap = BitmapFactory.decodeStream(stream)
        val out = ByteArrayOutputStream()
        val maxDim = 768
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = maxDim.toFloat() / maxDim.coerceAtLeast(maxOf(bitmap.width, bitmap.height))
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } ?: throw Exception("Σφάλμα ανάγνωσης εικόνας")

    val prompt = """
        You are a nutrition expert. Carefully examine the food image and identify ALL visible food items.
        For each item, estimate the carbohydrate content in grams based on typical portion sizes.
        Respond ONLY with a valid JSON object — no markdown, no extra text — in exactly this format:
        {"foods":[{"name":"<food name>","carbs_grams":<number>}],"total_carbs":<number>,"confidence":<0-100>,"description":"<brief summary>"}
        Be specific with food names and realistic with carb estimates. If the image is unclear, still try your best.
    """.trimIndent()

    // Groq uses OpenAI-compatible format with base64 image_url
    val requestBody = JSONObject().apply {
        put("model", "meta-llama/llama-4-scout-17b-16e-instruct")
        put("max_tokens", 1024)
        put("temperature", 0.1)
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$base64")
                        })
                    })
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                })
            })
        })
    }

    val client = OkHttpClient.Builder().connectTimeout(45, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    val url = "https://api.groq.com/openai/v1/chat/completions"
    val reqBody = requestBody.toString().toRequestBody("application/json".toMediaType())

    // Retry loop for 429 rate-limit errors (free tier: 30 req/min)
    val maxAttempts = 3
    var lastError = ""
    for (attempt in 1..maxAttempts) {
        onStatus(if (attempt == 1) "Ανάλυση..." else "Προσπάθεια $attempt/$maxAttempts...")
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(reqBody)
            .build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: ""

        if (resp.code == 429) {
            lastError = "Υπέρβαση ορίου Groq (30 αιτήματα/λεπτό) — προσπάθεια $attempt/$maxAttempts"
            if (attempt == maxAttempts) break
            val waitSec = resp.header("Retry-After")?.toLongOrNull() ?: (attempt * 10L)
            for (remaining in waitSec downTo 1) {
                onStatus("Αναμονή ${remaining}δ... ($attempt/$maxAttempts)")
                kotlinx.coroutines.delay(1_000L)
            }
            continue
        }

        if (resp.code == 401) throw Exception("Λάθος Groq API key. Έλεγξε το key στο console.groq.com")

        if (!resp.isSuccessful) {
            val detail = body.take(300).ifBlank { "χωρίς λεπτομέρειες" }
            throw Exception("Σφάλμα API (${resp.code}): $detail")
        }

        val rawText = try {
            JSONObject(body)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message")
                .getString("content").trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        } catch (e: Exception) {
            throw Exception("Μη αναμενόμενη απάντηση από AI. Δοκίμασε ξανά.")
        }

        val parsed = try {
            JSONObject(rawText)
        } catch (e: Exception) {
            throw Exception("Σφάλμα ανάλυσης απάντησης AI. Δοκίμασε ξανά.")
        }

        val foodsArray = parsed.getJSONArray("foods")
        val foods = (0 until foodsArray.length()).map { i ->
            val f = foodsArray.getJSONObject(i)
            FoodItem(f.getString("name"), f.getDouble("carbs_grams").toFloat(), 100f, "Μέτριο")
        }
        val totalCarbs = parsed.getDouble("total_carbs").toFloat()
        return@runCatching MealScanResult(foods, totalCarbs, 0f, suggestDose(totalCarbs), parsed.optInt("confidence", 80), parsed.optString("description", ""))
    }

    throw Exception("$lastError. Δοκίμασε ξανά σε λίγο.")
}
