package com.glycomate.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.glycomate.app.data.cgm.dexcom.DexcomRegion
import com.glycomate.app.data.cgm.dexcom.DexcomResult
import com.glycomate.app.data.cgm.libre.LluRegion
import com.glycomate.app.data.cgm.libre.LluResult
import com.glycomate.app.data.model.UserProfile
import com.glycomate.app.ui.theme.*
import com.glycomate.app.viewmodel.GlycoViewModel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(viewModel: GlycoViewModel, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    when (step) {
        0 -> WelcomeStep(onNext  = { step = 1 })
        1 -> ProfileStep(viewModel = viewModel, onNext = { step = 2 })
        2 -> CgmStep(viewModel = viewModel, onDone = {
            viewModel.completeOnboarding()
            onDone()
        })
    }
}

// ── Welcome ───────────────────────────────────────────────────────────────────
@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Text("🩺", fontSize = 72.sp)
        Spacer(Modifier.height(24.dp))
        Text("GlycoMate",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.W700))
        Spacer(Modifier.height(8.dp))
        Text("Η ψηφιακή διαχείριση του διαβήτη σου",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Ξεκινάμε  →")
        }
    }
}

// ── Profile ───────────────────────────────────────────────────────────────────
@Composable
private fun ProfileStep(viewModel: GlycoViewModel, onNext: () -> Unit) {
    var name         by remember { mutableStateOf("") }
    var diabetesType by remember { mutableStateOf("Τύπος 1") }
    var targetLow    by remember { mutableStateOf("70") }
    var targetHigh   by remember { mutableStateOf("180") }
    var icr          by remember { mutableStateOf("10") }
    var isf          by remember { mutableStateOf("40") }
    var basal        by remember { mutableStateOf("0") }
    val scope        = rememberCoroutineScope()

    // Explicit text colors for all fields (fixes invisible text in light mode)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor    = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor  = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor   = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor  = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor= MaterialTheme.colorScheme.outline,
        cursorColor         = MaterialTheme.colorScheme.primary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        Text("Προφίλ ασθενούς",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W700))
        Text("Συμπλήρωσε τα παρακάτω για τον υπολογισμό δόσης ινσουλίνης.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Name
        OutlinedTextField(
            value         = name,
            onValueChange = { name = it },
            label         = { Text("Όνομα") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            leadingIcon   = { Icon(Icons.Filled.Person, null) },
            colors        = fieldColors
        )

        // Diabetes type chips
        SectionLabel("Τύπος διαβήτη")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Τύπος 1", "Τύπος 2", "LADA").forEach { type ->
                FilterChip(
                    selected = diabetesType == type,
                    onClick  = { diabetesType = type },
                    label    = { Text(type) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SectionLabel("Στόχοι γλυκόζης (mg/dL)")

        // Each on its own row to avoid label cutoff
        OutlinedTextField(
            value           = targetLow,
            onValueChange   = { targetLow = it },
            label           = { Text("Κατώτατο (mg/dL)") },
            modifier        = Modifier.fillMaxWidth(),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors          = fieldColors,
            placeholder     = { Text("70") }
        )
        OutlinedTextField(
            value           = targetHigh,
            onValueChange   = { targetHigh = it },
            label           = { Text("Ανώτατο (mg/dL)") },
            modifier        = Modifier.fillMaxWidth(),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors          = fieldColors,
            placeholder     = { Text("180") }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SectionLabel("Παράμετροι ινσουλίνης")

        OutlinedTextField(
            value           = icr,
            onValueChange   = { icr = it },
            label           = { Text("ICR — γρ. carbs ανά μονάδα") },
            modifier        = Modifier.fillMaxWidth(),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText  = { Text("π.χ. 10 = 1 μονάδα για 10γρ carbs") },
            colors          = fieldColors
        )
        OutlinedTextField(
            value           = isf,
            onValueChange   = { isf = it },
            label           = { Text("ISF — mg/dL μείωση ανά μονάδα") },
            modifier        = Modifier.fillMaxWidth(),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText  = { Text("π.χ. 40 = μείωση 40 mg/dL ανά μονάδα") },
            colors          = fieldColors
        )
        OutlinedTextField(
            value           = basal,
            onValueChange   = { basal = it },
            label           = { Text("Βασική ινσουλίνη (μον./ημέρα)") },
            modifier        = Modifier.fillMaxWidth(),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors          = fieldColors
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                scope.launch {
                    viewModel.repo.prefs.saveUserProfile(UserProfile(
                        name         = name,
                        diabetesType = diabetesType,
                        targetLow    = targetLow.toFloatOrNull()  ?: 70f,
                        targetHigh   = targetHigh.toFloatOrNull() ?: 180f,
                        icr          = icr.toFloatOrNull()        ?: 10f,
                        isf          = isf.toFloatOrNull()        ?: 40f,
                        basalUnits   = basal.toFloatOrNull()      ?: 0f
                    ))
                }
                onNext()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled  = name.isNotBlank()
        ) {
            Text("Επόμενο  →")
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── CGM Step ──────────────────────────────────────────────────────────────────
@Composable
private fun CgmStep(viewModel: GlycoViewModel, onDone: () -> Unit) {
    var tab      by remember { mutableIntStateOf(0) }

    // LibreLinkUp state
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var region   by remember { mutableStateOf(LluRegion.EU) }
    var loading  by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Dexcom state
    var dexUser    by remember { mutableStateOf("") }
    var dexPass    by remember { mutableStateOf("") }
    var dexShowPass by remember { mutableStateOf(false) }
    var dexRegion  by remember { mutableStateOf(DexcomRegion.OUS) }
    var dexLoading by remember { mutableStateOf(false) }
    var dexErrorMsg by remember { mutableStateOf<String?>(null) }

    val scope    = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor     = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor    = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor   = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor          = MaterialTheme.colorScheme.primary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        Text("Σύνδεση CGM",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W700))
        Text("Σύνδεσε τον αισθητήρα σου για αυτόματη λήψη μετρήσεων.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 },
                text = { Text("LibreLinkUp") })
            Tab(selected = tab == 1, onClick = { tab = 1 },
                text = { Text("Dexcom") })
            Tab(selected = tab == 2, onClick = { tab = 2 },
                text = { Text("Χειροκίνητα") })
        }

        if (tab == 0) {
            // Info card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ℹ️", fontSize = 16.sp)
                    Text("Χρησιμοποίησε τα ίδια στοιχεία με την εφαρμογή LibreLink / LibreView.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            OutlinedTextField(
                value           = email,
                onValueChange   = { email = it },
                label           = { Text("Email LibreView") },
                modifier        = Modifier.fillMaxWidth(),
                singleLine      = true,
                leadingIcon     = { Icon(Icons.Filled.Email, null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next),
                colors          = fieldColors
            )

            OutlinedTextField(
                value               = password,
                onValueChange       = { password = it },
                label               = { Text("Κωδικός") },
                modifier            = Modifier.fillMaxWidth(),
                singleLine          = true,
                leadingIcon         = { Icon(Icons.Filled.Lock, null) },
                trailingIcon        = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Filled.VisibilityOff
                             else Icons.Filled.Visibility, null)
                    }
                },
                visualTransformation = if (showPass) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors               = fieldColors
            )

            // Region
            SectionLabel("Server περιοχή (Ελλάδα = EU)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(LluRegion.EU, LluRegion.US, LluRegion.DE).forEach { r ->
                    FilterChip(
                        selected = region == r,
                        onClick  = { region = r },
                        label    = { Text(r.name) }
                    )
                }
            }

            // Error message
            errorMsg?.let {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp))
                }
            }

            // Connect button
            Button(
                onClick = {
                    loading  = true
                    errorMsg = null
                    scope.launch {
                        when (val r = viewModel.repo.lluLogin(email, password, region)) {
                            is LluResult.Success -> {
                                loading = false
                                viewModel.syncNow()
                                onDone()
                            }
                            is LluResult.Error -> {
                                loading  = false
                                errorMsg = r.msg
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = email.isNotBlank() && password.isNotBlank() && !loading,
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = MaterialTheme.colorScheme.primary,
                    contentColor           = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Σύνδεση…")
                } else {
                    Icon(Icons.Filled.Sensors, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Σύνδεση & Εκκίνηση",
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (tab == 1) {
            // Info card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ℹ️", fontSize = 16.sp)
                    Text("Χρησιμοποίησε τα στοιχεία σύνδεσης της εφαρμογής Dexcom G6/G7.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            OutlinedTextField(
                value           = dexUser,
                onValueChange   = { dexUser = it },
                label           = { Text("Username Dexcom") },
                modifier        = Modifier.fillMaxWidth(),
                singleLine      = true,
                leadingIcon     = { Icon(Icons.Filled.Person, null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next),
                colors          = fieldColors
            )

            OutlinedTextField(
                value                = dexPass,
                onValueChange        = { dexPass = it },
                label                = { Text("Κωδικός Dexcom") },
                modifier             = Modifier.fillMaxWidth(),
                singleLine           = true,
                leadingIcon          = { Icon(Icons.Filled.Lock, null) },
                trailingIcon         = {
                    IconButton(onClick = { dexShowPass = !dexShowPass }) {
                        Icon(if (dexShowPass) Icons.Filled.VisibilityOff
                             else Icons.Filled.Visibility, null)
                    }
                },
                visualTransformation = if (dexShowPass) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors               = fieldColors
            )

            // Region
            SectionLabel("Περιοχή (Ελλάδα = Ευρώπη OUS)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    DexcomRegion.OUS to "Ευρώπη (OUS)",
                    DexcomRegion.US  to "ΗΠΑ (US)"
                ).forEach { (r, label) ->
                    FilterChip(
                        selected = dexRegion == r,
                        onClick  = { dexRegion = r },
                        label    = { Text(label) }
                    )
                }
            }

            dexErrorMsg?.let {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp))
                }
            }

            Button(
                onClick = {
                    dexLoading  = true
                    dexErrorMsg = null
                    scope.launch {
                        when (val r = viewModel.repo.dexcomLogin(dexUser, dexPass, dexRegion)) {
                            is DexcomResult.Success -> {
                                dexLoading = false
                                viewModel.syncNow()
                                onDone()
                            }
                            is DexcomResult.Error -> {
                                dexLoading  = false
                                dexErrorMsg = r.msg
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = dexUser.isNotBlank() && dexPass.isNotBlank() && !dexLoading,
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = MaterialTheme.colorScheme.primary,
                    contentColor           = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (dexLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Σύνδεση…")
                } else {
                    Icon(Icons.Filled.Sensors, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Σύνδεση Dexcom & Εκκίνηση",
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (tab == 2) {
            Text(
                "Θα καταγράφεις τις μετρήσεις χειροκίνητα. " +
                "Μπορείς να συνδέσεις CGM αργότερα από τις Ρυθμίσεις.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor   = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Συνέχεια χωρίς CGM")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.onSurface
    )
}
