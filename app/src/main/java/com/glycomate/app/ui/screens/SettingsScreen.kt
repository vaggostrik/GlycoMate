package com.glycomate.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.edit
import com.glycomate.app.data.cgm.libre.LluRegion
import com.glycomate.app.data.cgm.dexcom.DexcomResult
import com.glycomate.app.data.cgm.dexcom.DexcomRegion
import com.glycomate.app.data.cgm.libre.LluResult
import com.glycomate.app.data.cgm.nightscout.NsResult
import com.glycomate.app.data.model.UserProfile
import com.glycomate.app.data.prefs.PrefKeys
import com.glycomate.app.data.prefs.dataStore
import com.glycomate.app.worker.CgmSyncWorker
import com.glycomate.app.report.PdfReportGenerator
import com.glycomate.app.ui.theme.GlycoGreen
import com.glycomate.app.ui.theme.GlycoRed
import com.glycomate.app.ui.theme.GlycoAmber
import com.glycomate.app.ui.theme.GlycoBlue
import com.glycomate.app.viewmodel.GlycoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GlycoViewModel,
    onOpenMoodTracker: () -> Unit = {},
    onOpenReminders:   () -> Unit = {},
    onOpenHistory:     () -> Unit = {}
) {
    var expandProfile by remember { mutableStateOf(false) }
    var expandCgm     by remember { mutableStateOf(false) }
    var expandAlerts  by remember { mutableStateOf(false) }

    val scope     = rememberCoroutineScope()
    val profile   by viewModel.userProfile.collectAsState()
    val cgmSource by viewModel.cgmSource.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Ρυθμίσεις",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background))
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Profile section
            SectionCard(
                title    = "Προφίλ & Παράμετροι",
                icon     = Icons.Filled.Person,
                expanded = expandProfile,
                onToggle = { expandProfile = !expandProfile }
            ) {
                ProfileEditor(profile = profile, onSave = { viewModel.saveProfile(it) })
            }

            // CGM section
            SectionCard(
                title    = "Σύνδεση CGM — ${if (cgmSource == "NONE") "Ανενεργή" else cgmSource}",
                icon     = Icons.Filled.Sensors,
                badge    = cgmSource != "NONE",
                expanded = expandCgm,
                onToggle = { expandCgm = !expandCgm }
            ) {
                CgmEditor(viewModel = viewModel, currentSource = cgmSource)
            }

            // Alerts section
            SectionCard(
                title    = "Όρια ειδοποιήσεων",
                icon     = Icons.Filled.Notifications,
                expanded = expandAlerts,
                onToggle = { expandAlerts = !expandAlerts }
            ) {
                AlertEditor(viewModel = viewModel)
            }

            // Quick links
            val context = LocalContext.current
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    listOf<Triple<String, String, () -> Unit>>(
                        Triple("😊 Mood Tracker",
                            "Καταγραφή διάθεσης & συσχέτιση με γλυκόζη",
                            onOpenMoodTracker),
                        Triple("⏰ Υπενθυμίσεις",
                            "Φάρμακα, μέτρηση, άσκηση",
                            onOpenReminders),
                        Triple("📄 Αναφορά PDF",
                            "Δημιουργία & κοινοποίηση αναφοράς για τον γιατρό",
                            {
                                scope.launch(Dispatchers.IO) {
                                    val readings = viewModel.allReadings.value
                                    val insulin  = viewModel.allInsulin.value
                                    val meals    = viewModel.allMeals.value
                                    val userProfile = viewModel.userProfile.value
                                    val file = PdfReportGenerator
                                        .generate(context, readings, insulin, meals, userProfile)
                                    file?.let {
                                        withContext(Dispatchers.Main) {
                                            PdfReportGenerator.shareFile(context, it)
                                        }
                                    }
                                }
                            })
                    ).forEach { (title, subtitle, action) ->
                        Row(modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.bodyMedium
                                    .copy(fontWeight = FontWeight.W500))
                                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = action) {
                                Icon(Icons.Filled.ChevronRight, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant
                            .copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: Boolean = false,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()
                .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, null,
                        tint = if (badge) GlycoGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp))
                    Text(title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600))
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(profile: UserProfile, onSave: (UserProfile) -> Unit) {
    var name   by remember(profile.name)              { mutableStateOf(profile.name) }
    var type   by remember(profile.diabetesType)      { mutableStateOf(profile.diabetesType) }
    var low    by remember(profile.targetLow)         { mutableStateOf(profile.targetLow.toString()) }
    var high   by remember(profile.targetHigh)        { mutableStateOf(profile.targetHigh.toString()) }
    var icr    by remember(profile.icr)               { mutableStateOf(profile.icr.toString()) }
    var isf    by remember(profile.isf)               { mutableStateOf(profile.isf.toString()) }
    var basal  by remember(profile.basalUnits)        { mutableStateOf(profile.basalUnits.toString()) }
    var rapidBrand by remember(profile.rapidInsulinBrand) { mutableStateOf(profile.rapidInsulinBrand) }
    var longBrand  by remember(profile.longInsulinBrand)  { mutableStateOf(profile.longInsulinBrand) }
    var saved  by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor     = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor    = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor   = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor          = MaterialTheme.colorScheme.primary
    )

    OutlinedTextField(value = name, onValueChange = { name = it; saved = false },
        label = { Text("Όνομα") }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, colors = fieldColors)

    Text("Τύπος διαβήτη", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Τύπος 1", "Τύπος 2", "LADA").forEach { t ->
            FilterChip(selected = type == t, onClick = { type = t; saved = false },
                label = { Text(t) })
        }
    }

    Text("Ινσουλίνη Ταχείας", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UserProfile.RAPID_BRANDS.forEach { b ->
            FilterChip(selected = rapidBrand == b, onClick = { rapidBrand = b; saved = false },
                label = { Text(b) })
        }
    }

    Text("Ινσουλίνη Μακράς Διάρκειας (Βραδείας)", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UserProfile.LONG_BRANDS.forEach { b ->
            FilterChip(selected = longBrand == b, onClick = { longBrand = b; saved = false },
                label = { Text(b) })
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = low, onValueChange = { low = it; saved = false },
            label = { Text("Κατώτατο mg/dL") }, modifier = Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = fieldColors)
        OutlinedTextField(value = high, onValueChange = { high = it; saved = false },
            label = { Text("Ανώτατο mg/dL") }, modifier = Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = fieldColors)
    }

    OutlinedTextField(value = icr, onValueChange = { icr = it; saved = false },
        label = { Text("ICR (γρ. carbs/μονάδα)") }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = fieldColors)

    OutlinedTextField(value = isf, onValueChange = { isf = it; saved = false },
        label = { Text("ISF (mg/dL ανά μονάδα)") }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = fieldColors)

    OutlinedTextField(value = basal, onValueChange = { basal = it; saved = false },
        label = { Text("Βασική ινσουλίνη (μον./ημ.)") }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = fieldColors)

    Button(
        onClick = {
            onSave(UserProfile(
                name               = name,
                diabetesType       = type,
                targetLow          = low.toFloatOrNull()  ?: 70f,
                targetHigh         = high.toFloatOrNull() ?: 180f,
                icr                = icr.toFloatOrNull()  ?: 10f,
                isf                = isf.toFloatOrNull()  ?: 40f,
                basalUnits         = basal.toFloatOrNull() ?: 0f,
                rapidInsulinBrand  = rapidBrand,
                longInsulinBrand   = longBrand
            ))
            saved = true
        },
        modifier = Modifier.fillMaxWidth(),
        colors = if (saved) ButtonDefaults.buttonColors(containerColor = GlycoGreen)
        else ButtonDefaults.buttonColors()
    ) { Text(if (saved) "✓ Αποθηκεύτηκε" else "Αποθήκευση αλλαγών") }
}

@Composable
private fun CgmEditor(viewModel: GlycoViewModel, currentSource: String) {
    var tab      by remember { mutableIntStateOf(if (currentSource == "NIGHTSCOUT") 1 else 0) }
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var region   by remember { mutableStateOf(LluRegion.EU) }
    var loading  by remember { mutableStateOf(false) }
    var msg      by remember { mutableStateOf<String?>(null) }
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

    // Pre-fill email from saved prefs
    val savedEmail by viewModel.repo.prefs.lluEmail.collectAsState(initial = "")
    LaunchedEffect(savedEmail) { if (email.isBlank()) email = savedEmail }

    if (currentSource != "NONE") {
        Card(colors = CardDefaults.cardColors(
            containerColor = GlycoGreen.copy(alpha = 0.1f))) {
            Row(modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = GlycoGreen,
                    modifier = Modifier.size(18.dp))
                Text("Συνδεδεμένο: $currentSource",
                    style = MaterialTheme.typography.bodySmall, color = GlycoGreen)
            }
        }
    }

    TabRow(selectedTabIndex = tab) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("LibreLinkUp") })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Dexcom") })
        Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Nightscout") })
        Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Χειροκίνητα") })
    }

    Spacer(Modifier.height(4.dp))

    when (tab) {
        0 -> {
            OutlinedTextField(value = email, onValueChange = { email = it },
                label = { Text("Email LibreView") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        colors = fieldColors)
            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text("Κωδικός") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                    }
                },
        colors = fieldColors)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(LluRegion.EU, LluRegion.US, LluRegion.DE).forEach { r ->
                    FilterChip(selected = region == r, onClick = { region = r },
                        label = { Text(r.name) })
                }
            }
            msg?.let {
                Text(it,
                    color = if (it.startsWith("✓")) GlycoGreen else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    loading = true; msg = null
                    scope.launch {
                        when (val r = viewModel.repo.lluLogin(email, password, region)) {
                            is LluResult.Success -> {
                                // Schedule background polling + immediate sync
                                CgmSyncWorker.schedule(
                                    viewModel.repo.prefs.context)
                                viewModel.syncNow()
                                msg = "✓ Συνδέθηκε! Φορτώνω δεδομένα…"
                                loading = false
                            }
                            is LluResult.Error   -> {
                                msg = "✗ ${r.msg}"
                                loading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = email.isNotBlank() && password.isNotBlank() && !loading
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(if (loading) "Σύνδεση…" else "Αποθήκευση & Σύνδεση")
            }

            if (currentSource == "LLU") {
                OutlinedButton(
                    onClick = { scope.launch { viewModel.repo.prefs.clearCgm() } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Αποσύνδεση") }
            }
        }
        1 -> {
            // Dexcom Share
            var dexUser    by remember { mutableStateOf("") }
            var dexPass    by remember { mutableStateOf("") }
            var dexShowPass by remember { mutableStateOf(false) }
            var dexRegion  by remember { mutableStateOf(DexcomRegion.OUS) }
            var dexMsg     by remember { mutableStateOf<String?>(null) }
            var dexLoading by remember { mutableStateOf(false) }

            val savedDexUser by viewModel.repo.prefs.dexcomUsername.collectAsState(initial = "")
            LaunchedEffect(savedDexUser) { if (dexUser.isBlank()) dexUser = savedDexUser }

            Spacer(Modifier.height(4.dp))
            Text("Χρησιμοποίησε τα στοιχεία σύνδεσης της εφαρμογής Dexcom G6/G7.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(value = dexUser, onValueChange = { dexUser = it },
                label = { Text("Username Dexcom") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = fieldColors)

            OutlinedTextField(value = dexPass, onValueChange = { dexPass = it },
                label = { Text("Κωδικός Dexcom") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (dexShowPass) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { dexShowPass = !dexShowPass }) {
                        Icon(if (dexShowPass) Icons.Filled.VisibilityOff
                             else Icons.Filled.Visibility, null)
                    }
                },
                colors = fieldColors)

            // Region
            Text("Περιοχή:", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    DexcomRegion.OUS to "Ευρώπη (OUS)",
                    DexcomRegion.US  to "ΗΠΑ (US)"
                ).forEach { (r, label) ->
                    FilterChip(selected = dexRegion == r, onClick = { dexRegion = r },
                        label = { Text(label) })
                }
            }

            dexMsg?.let {
                Text(it,
                    color = if (it.startsWith("✓")) GlycoGreen
                            else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    dexLoading = true; dexMsg = null
                    scope.launch {
                        when (val r = viewModel.repo.dexcomLogin(dexUser, dexPass, dexRegion)) {
                            is DexcomResult.Success -> {
                                CgmSyncWorker.schedule(viewModel.repo.prefs.context)
                                viewModel.syncNow()
                                dexMsg = "✓ Συνδέθηκε Dexcom!"; dexLoading = false
                            }
                            is DexcomResult.Error -> {
                                dexMsg = "✗ ${r.msg}"; dexLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = dexUser.isNotBlank() && dexPass.isNotBlank() && !dexLoading
            ) {
                if (dexLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(if (dexLoading) "Σύνδεση…" else "Σύνδεση Dexcom")
            }

            if (currentSource == "DEXCOM") {
                OutlinedButton(
                    onClick = { scope.launch { viewModel.repo.prefs.clearCgm() } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Αποσύνδεση") }
            }
        }

        2 -> {
            // Nightscout
            var nsUrl    by remember { mutableStateOf("https://") }
            var nsSecret by remember { mutableStateOf("") }
            var nsToken  by remember { mutableStateOf("") }
            var showSecret by remember { mutableStateOf(false) }
            var nsMsg    by remember { mutableStateOf<String?>(null) }
            var nsLoading by remember { mutableStateOf(false) }

            val savedUrl    by viewModel.repo.prefs.nsUrl.collectAsState(initial = "")
            val savedSecret by viewModel.repo.prefs.nsSecret.collectAsState(initial = "")
            val savedToken  by viewModel.repo.prefs.nsToken.collectAsState(initial = "")
            LaunchedEffect(savedUrl)    { if (nsUrl == "https://") nsUrl = savedUrl }
            LaunchedEffect(savedSecret) { if (nsSecret.isBlank()) nsSecret = savedSecret }
            LaunchedEffect(savedToken)  { if (nsToken.isBlank()) nsToken = savedToken }

            OutlinedTextField(value = nsUrl, onValueChange = { nsUrl = it },
                label = { Text("Nightscout URL") },
                placeholder = { Text("https://mysite.fly.dev") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
        colors = fieldColors)

            OutlinedTextField(value = nsSecret, onValueChange = { nsSecret = it },
                label = { Text("API Secret (προαιρετικό)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = if (showSecret) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(if (showSecret) Icons.Filled.VisibilityOff
                             else Icons.Filled.Visibility, null)
                    }
                },
        colors = fieldColors)

            OutlinedTextField(value = nsToken, onValueChange = { nsToken = it },
                label = { Text("Access Token (εναλλακτικά)") },
                placeholder = { Text("glyco-xxxxxxxxxxxx") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
        colors = fieldColors)

            Text("Χρησιμοποίησε API Secret ή Access Token — όχι και τα δύο.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            nsMsg?.let {
                Text(it,
                    color = if (it.startsWith("✓")) GlycoGreen
                            else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    nsLoading = true; nsMsg = null
                    scope.launch {
                        when (val r = viewModel.repo.nightscoutTest(nsUrl, nsSecret, nsToken)) {
                            is NsResult.Success ->
                                { nsMsg = "✓ Συνδέθηκε: ${r.data}"; nsLoading = false }
                            is NsResult.Error ->
                                { nsMsg = "✗ ${r.msg}"; nsLoading = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = nsUrl.length > 8 && !nsLoading
            ) {
                if (nsLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(if (nsLoading) "Σύνδεση…" else "Δοκιμή & Αποθήκευση")
            }
        }
        3 -> {
            Text("Χειροκίνητη λειτουργία — χωρίς αυτόματο συγχρονισμό.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { scope.launch { viewModel.repo.prefs.clearCgm() } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Μετάβαση σε χειροκίνητη λειτουργία") }
        }
    }
}

@Composable
private fun AlertEditor(viewModel: GlycoViewModel) {
    val hypo          by viewModel.repo.prefs.alertHypo.collectAsState(initial = 70f)
    val hyper         by viewModel.repo.prefs.alertHyper.collectAsState(initial = 250f)
    val hypoEnabled   by viewModel.repo.prefs.alertHypoEnabled.collectAsState(initial = true)
    val hyperEnabled  by viewModel.repo.prefs.alertHyperEnabled.collectAsState(initial = true)
    val watchEnabled  by viewModel.repo.prefs.watchEnabled.collectAsState(initial = true)

    var hypoStr   by remember(hypo)  { mutableStateOf(hypo.toInt().toString()) }
    var hyperStr  by remember(hyper) { mutableStateOf(hyper.toInt().toString()) }
    var saved     by remember { mutableStateOf(false) }
    val scope     = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor     = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor    = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor   = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor          = MaterialTheme.colorScheme.primary
    )

    // ── Hypo alert ────────────────────────────────────────────────────────────
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("🚨 Ειδοποίηση Υπογλυκαιμίας",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
            Text("Ήχος alarm + vibration + bypass σίγαση",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked         = hypoEnabled,
            onCheckedChange = { on ->
                scope.launch {
                    viewModel.repo.prefs.context.dataStore.edit {
                        it[PrefKeys.ALERT_HYPO_ENABLED] = on
                    }
                }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = GlycoRed)
        )
    }

    AnimatedVisibility(visible = hypoEnabled) {
        OutlinedTextField(
            value           = hypoStr,
            onValueChange   = { hypoStr = it; saved = false },
            label           = { Text("Κατώφλι (mg/dL)") },
            modifier        = Modifier.fillMaxWidth(),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText  = { Text("Προτεινόμενο: 70 mg/dL") },
            colors          = fieldColors
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // ── Hyper alert ───────────────────────────────────────────────────────────
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("↑ Ειδοποίηση Υπεργλυκαιμίας",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
            Text("Notification με ήχο + vibration",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked         = hyperEnabled,
            onCheckedChange = { on ->
                scope.launch {
                    viewModel.repo.prefs.context.dataStore.edit {
                        it[PrefKeys.ALERT_HYPER_ENABLED] = on
                    }
                }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = GlycoAmber)
        )
    }

    AnimatedVisibility(visible = hyperEnabled) {
        OutlinedTextField(
            value           = hyperStr,
            onValueChange   = { hyperStr = it; saved = false },
            label           = { Text("Κατώφλι (mg/dL)") },
            modifier        = Modifier.fillMaxWidth(),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText  = { Text("Προτεινόμενο: 180–250 mg/dL") },
            colors          = fieldColors
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // ── Smartwatch ────────────────────────────────────────────────────────────
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("⌚ Wear OS Smartwatch",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
            Text("Αποστολή γλυκόζης στο ρολόι σου",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked         = watchEnabled,
            onCheckedChange = { on ->
                scope.launch {
                    viewModel.repo.prefs.context.dataStore.edit {
                        it[PrefKeys.WATCH_ENABLED] = on
                    }
                }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = GlycoBlue)
        )
    }

    AnimatedVisibility(visible = watchEnabled) {
        Surface(shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("📲 Εγκατάσταση στο watch:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("1. Android Studio → Build → wear\n" +
                    "2. Σύνδεσε Wear OS watch μέσω WiFi\n" +
                    "3. Watchface → Complications → GlycoMate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // ── Save button ───────────────────────────────────────────────────────────
    if (!saved || hypoStr.isNotBlank() || hyperStr.isNotBlank()) {
        Button(
            onClick = {
                scope.launch {
                    viewModel.repo.prefs.context.dataStore.edit { p ->
                        hypoStr.toFloatOrNull()?.let  { p[PrefKeys.ALERT_HYPO]  = it }
                        hyperStr.toFloatOrNull()?.let { p[PrefKeys.ALERT_HYPER] = it }
                    }
                }
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
            colors   = if (saved) ButtonDefaults.buttonColors(containerColor = GlycoGreen)
                       else ButtonDefaults.buttonColors()
        ) { Text(if (saved) "✓ Αποθηκεύτηκε" else "Αποθήκευση ορίων") }
    }
}
