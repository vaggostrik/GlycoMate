package com.glycomate.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.glycomate.app.data.food.*
import com.glycomate.app.ui.components.CameraBarcodeScannerView
import com.glycomate.app.ui.theme.*
import com.glycomate.app.data.model.InsulinType
import com.glycomate.app.viewmodel.GlycoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    viewModel: GlycoViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offClient = remember { OpenFoodFactsClient() }

    var screenState  by remember { mutableStateOf<BarcodeScreenState>(BarcodeScreenState.Scanning) }
    var hasCamPerm   by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf("") }

    // Camera permission
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        hasCamPerm = granted
        if (!granted) screenState = BarcodeScreenState.NoPermission
    }

    LaunchedEffect(Unit) { permLauncher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barcode Scanner",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Πίσω") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            when (val state = screenState) {

                // ── Camera scanning ───────────────────────────────────────────
                is BarcodeScreenState.Scanning -> {
                    if (hasCamPerm) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Camera preview takes most of screen
                            CameraBarcodeScannerView(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                onBarcodeDetected = { barcode ->
                                    screenState = BarcodeScreenState.Loading(barcode)
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            offClient.getProduct(barcode)
                                        }
                                        screenState = when (result) {
                                            is FoodResult.Success  -> BarcodeScreenState.Found(result.data)
                                            is FoodResult.NotFound -> BarcodeScreenState.NotFound(barcode)
                                            is FoodResult.Error    -> BarcodeScreenState.Error(result.message)
                                        }
                                    }
                                }
                            )

                            // Manual entry option at bottom
                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("ή πληκτρολόγησε barcode χειροκίνητα:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value         = manualBarcode,
                                        onValueChange = { manualBarcode = it },
                                        label         = { Text("Barcode") },
                                        modifier      = Modifier.weight(1f),
                                        singleLine    = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    Button(
                                        onClick  = {
                                            if (manualBarcode.isNotBlank()) {
                                                val bc = manualBarcode.trim()
                                                screenState = BarcodeScreenState.Loading(bc)
                                                scope.launch {
                                                    val result = withContext(Dispatchers.IO) {
                                                        offClient.getProduct(bc)
                                                    }
                                                    screenState = when (result) {
                                                        is FoodResult.Success  -> BarcodeScreenState.Found(result.data)
                                                        is FoodResult.NotFound -> BarcodeScreenState.NotFound(bc)
                                                        is FoodResult.Error    -> BarcodeScreenState.Error(result.message)
                                                    }
                                                }
                                            }
                                        },
                                        enabled  = manualBarcode.isNotBlank(),
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) { Text("Αναζήτηση") }
                                }
                            }
                        }
                    } else {
                        // Asking for permission
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Filled.CameraAlt, null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Text("Απαιτείται άδεια κάμερας",
                                    style = MaterialTheme.typography.titleMedium)
                                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text("Παροχή άδειας")
                                }
                            }
                        }
                    }
                }

                // ── Loading ───────────────────────────────────────────────────
                is BarcodeScreenState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Αναζήτηση: ${state.barcode}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Ανάκτηση δεδομένων από Open Food Facts…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ── Product found ─────────────────────────────────────────────
                is BarcodeScreenState.Found -> {
                    ProductFoundContent(
                        product     = state.product,
                        viewModel   = viewModel,
                        onScanAgain = { screenState = BarcodeScreenState.Scanning },
                        onDone      = onBack
                    )
                }

                // ── Not found ─────────────────────────────────────────────────
                is BarcodeScreenState.NotFound -> {
                    ManualEntryContent(
                        barcode     = state.barcode,
                        viewModel   = viewModel,
                        onScanAgain = { screenState = BarcodeScreenState.Scanning },
                        onDone      = onBack
                    )
                }

                // ── Error ─────────────────────────────────────────────────────
                is BarcodeScreenState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Error, null,
                                modifier = Modifier.size(48.dp), tint = GlycoRed)
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { screenState = BarcodeScreenState.Scanning }) {
                                Text("Δοκίμασε ξανά")
                            }
                        }
                    }
                }

                BarcodeScreenState.NoPermission -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)) {
                            Text("Δεν δόθηκε άδεια κάμερας.",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Μπορείς να σκανάρεις χειροκίνητα:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(value = manualBarcode,
                                onValueChange = { manualBarcode = it },
                                label = { Text("Βarcode") }, modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            Button(onClick = {
                                if (manualBarcode.isNotBlank()) {
                                    val bc = manualBarcode.trim()
                                    screenState = BarcodeScreenState.Loading(bc)
                                    scope.launch {
                                        screenState = when (val r = withContext(Dispatchers.IO) {
                                            offClient.getProduct(bc) }) {
                                            is FoodResult.Success  -> BarcodeScreenState.Found(r.data)
                                            is FoodResult.NotFound -> BarcodeScreenState.NotFound(bc)
                                            is FoodResult.Error    -> BarcodeScreenState.Error(r.message)
                                        }
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Αναζήτηση") }
                        }
                    }
                }
            }
        }
    }
}

// ── Product found UI ─────────────────────────────────────────────────────────

@Composable
private fun ProductFoundContent(
    product:    FoodProduct,
    viewModel:  GlycoViewModel,
    onScanAgain: () -> Unit,
    onDone:     () -> Unit
) {
    var weightGrams   by remember { mutableStateOf("100") }
    var customCarbs   by remember { mutableStateOf("") }
    val scope         = rememberCoroutineScope()

    val weightFloat  = weightGrams.toFloatOrNull() ?: 100f
    val calculatedCarbs = product.carbsForWeight(weightFloat)
    val carbsToUse   = customCarbs.toFloatOrNull() ?: calculatedCarbs
    val suggestedDose = viewModel.calculateBolus(carbsToUse)

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Product info card
        Card(modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = GlycoGreen.copy(alpha = 0.15f)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = GlycoGreen,
                            modifier = Modifier.padding(6.dp).size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(product.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600))
                        if (product.brand.isNotBlank())
                            Text(product.brand, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider()

                // Nutritional table
                NutritionRow("Υδατάνθρακες / 100g", "${product.carbs100g.toInt()}g",
                    MaterialTheme.colorScheme.primary)
                NutritionRow("Σάκχαρα / 100g",       "${product.sugars100g.toInt()}g",
                    GlycoAmber)
                NutritionRow("Θερμίδες / 100g",      "${product.calories100g.toInt()} kcal",
                    MaterialTheme.colorScheme.onSurface)
                NutritionRow("Γλυκαιμικός Δείκτης",  product.glycemicIndex,
                    when (product.glycemicIndex) {
                        "Υψηλό" -> GlycoRed
                        "Χαμηλό" -> GlycoGreen
                        else -> GlycoAmber
                    })

                if (product.servingSize.isNotBlank()) {
                    Text(product.servingInfo, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Weight input
        Card(modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Text("Πόσο θα φας;",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value         = weightGrams,
                        onValueChange = { weightGrams = it; customCarbs = "" },
                        label         = { Text("Βάρος (γρ.)") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value         = if (customCarbs.isBlank()) "${calculatedCarbs.toInt()}" else customCarbs,
                        onValueChange = { customCarbs = it },
                        label         = { Text("Carbs (γρ.) — επεξεργ.") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                // Result
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Υδατάνθρακες για $weightFloat g",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (suggestedDose > 0f)
                                Text("Πρόταση ινσουλίνης: ${String.format("%.1f", suggestedDose)}U",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${carbsToUse.toInt()}g",
                            style = MaterialTheme.typography.headlineSmall
                                .copy(fontWeight = FontWeight.W700),
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Actions
        Button(
            onClick = {
                val desc = "${product.name} (${weightFloat.toInt()}g)"
                viewModel.logMeal(desc, carbsToUse)
                if (suggestedDose > 0f)
                    viewModel.logInsulin(suggestedDose,
                        InsulinType.RAPID,
                        "Barcode scan")
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = GlycoGreen)
        ) {
            Icon(Icons.Filled.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("Καταγραφή γεύματος" +
                if (suggestedDose > 0f) " + ${String.format("%.1f", suggestedDose)}U" else "")
        }

        OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.QrCodeScanner, null)
            Spacer(Modifier.width(8.dp))
            Text("Νέο σκανάρισμα")
        }
    }
}

// ── Not found — manual entry ──────────────────────────────────────────────────

@Composable
private fun ManualEntryContent(
    barcode: String,
    viewModel: GlycoViewModel,
    onScanAgain: () -> Unit,
    onDone: () -> Unit
) {
    var description by remember { mutableStateOf("") }
    var carbsStr    by remember { mutableStateOf("") }

    val carbs         = carbsStr.toFloatOrNull() ?: 0f
    val suggestedDose = viewModel.calculateBolus(carbs)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {

        Card(colors = CardDefaults.cardColors(
            containerColor = GlycoAmber.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.SearchOff, null, tint = GlycoAmber,
                    modifier = Modifier.size(20.dp))
                Column {
                    Text("Προϊόν δεν βρέθηκε",
                        style = MaterialTheme.typography.labelLarge, color = GlycoAmber)
                    Text("Barcode: $barcode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Μπορείς να καταγράψεις χειροκίνητα:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        OutlinedTextField(value = description, onValueChange = { description = it },
            label = { Text("Περιγραφή") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        OutlinedTextField(value = carbsStr, onValueChange = { carbsStr = it },
            label = { Text("Υδατάνθρακες (γρ.)") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = if (suggestedDose > 0f && carbs > 0f)
                ({ Text("Πρόταση: ${String.format("%.1f", suggestedDose)}U ινσουλίνης") })
            else null)

        Button(
            onClick  = {
                if (description.isNotBlank() && carbs > 0f) {
                    viewModel.logMeal(description, carbs)
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled  = description.isNotBlank() && carbs > 0f
        ) { Text("Καταγραφή") }

        OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Σκανάρισμα άλλου barcode")
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun NutritionRow(
    label: String, value: String,
    color: Color
) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
            color = color)
    }
}

// ── Screen state ──────────────────────────────────────────────────────────────

sealed class BarcodeScreenState {
    object Scanning                                     : BarcodeScreenState()
    object NoPermission                                 : BarcodeScreenState()
    data class Loading(val barcode: String)             : BarcodeScreenState()
    data class Found(val product: FoodProduct)          : BarcodeScreenState()
    data class NotFound(val barcode: String)            : BarcodeScreenState()
    data class Error(val message: String)               : BarcodeScreenState()
}
