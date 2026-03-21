package com.glycomate.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.glycomate.app.R
import com.glycomate.app.sos.*
import com.glycomate.app.ui.theme.*
import com.glycomate.app.viewmodel.GlycoViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(viewModel: GlycoViewModel) {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val prefs         = viewModel.repo.prefs

    val contactsJson by prefs.sosContacts.collectAsState(initial = "[]")
    val autoTrigger  by prefs.sosAutoTrigger.collectAsState(initial = false)
    val threshold    by prefs.sosThreshold.collectAsState(initial = 55f)

    val contacts = remember(contactsJson) { contactsJson.toSosContacts() }

    var sosTriggerState  by remember { mutableStateOf<SosUiState>(SosUiState.Idle) }
    var showAddContact   by remember { mutableStateOf(false) }
    var thresholdStr     by remember(threshold) { mutableStateOf(threshold.toInt().toString()) }

    val locationPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { }
    val smsPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        locationPerm.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
        smsPerm.launch(Manifest.permission.SEND_SMS)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.sos_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background))
    }) { padding ->

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            item {
                AnimatedVisibility(visible = sosTriggerState !is SosUiState.Idle,
                    enter = fadeIn() + slideInVertically()) {
                    when (val s = sosTriggerState) {
                        is SosUiState.Success ->
                            AlertCard(
                                title = stringResource(R.string.sos_sent_success),
                                body  = stringResource(R.string.sos_sent_body, s.contacts, 
                                    if (s.locationSent) stringResource(R.string.gps_location_sent) else "."),
                                color = GlycoGreen,
                                onDismiss = { sosTriggerState = SosUiState.Idle }
                            )
                        is SosUiState.Error ->
                            AlertCard(
                                title = stringResource(R.string.sos_problem),
                                body  = s.message,
                                color = GlycoRed,
                                onDismiss = { sosTriggerState = SosUiState.Idle }
                            )
                        is SosUiState.NoContacts ->
                            AlertCard(
                                title = stringResource(R.string.no_contacts_title),
                                body  = stringResource(R.string.no_contacts_body),
                                color = GlycoAmber,
                                onDismiss = { sosTriggerState = SosUiState.Idle }
                            )
                        else -> {}
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        Text(stringResource(R.string.panic_button_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600))
                        Text(stringResource(R.string.panic_button_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)

                        PulsingSOSButton(
                            isSending = sosTriggerState is SosUiState.Sending,
                            onClick   = {
                                sosTriggerState = SosUiState.Sending
                                scope.launch {
                                    val mgr = SosManager(context)
                                    val glucose = viewModel.dashboard.value.latestReading?.valueMgDl
                                    val profile = viewModel.userProfile.value
                                    val result  = mgr.triggerSos(glucose, profile.name)
                                    sosTriggerState = when (result) {
                                        is SosResult.Success  -> SosUiState.Success(
                                            result.contactsNotified, result.locationIncluded)
                                        is SosResult.Error    -> SosUiState.Error(result.message)
                                        is SosResult.NoContacts -> SosUiState.NoContacts
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.emergency_contacts_header),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showAddContact = true }) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_contact))
                    }
                }
            }

            if (contacts.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👥", fontSize = 36.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.no_contacts_yet),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.add_contact_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(contacts, key = { it.id }) { contact ->
                    ContactCard(
                        contact  = contact,
                        onDelete = {
                            val updated = contacts.filter { it.id != contact.id }
                            scope.launch { prefs.saveSosContacts(updated.toJson()) }
                        }
                    )
                }
            }

            item {
                Text(stringResource(R.string.auto_trigger_header),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.auto_sos_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.W500))
                                Text(stringResource(R.string.auto_sos_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked         = autoTrigger,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        prefs.saveSosSettings(on,
                                            thresholdStr.toFloatOrNull() ?: 55f)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = GlycoGreen)
                            )
                        }

                        AnimatedVisibility(visible = autoTrigger) {
                            OutlinedTextField(
                                value         = thresholdStr,
                                onValueChange = {
                                    thresholdStr = it
                                    scope.launch {
                                        prefs.saveSosSettings(autoTrigger,
                                            it.toFloatOrNull() ?: 55f)
                                    }
                                },
                                label         = { Text(stringResource(R.string.threshold_label)) },
                                modifier      = Modifier.fillMaxWidth(),
                                singleLine    = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText  = { Text(stringResource(R.string.threshold_recommended)) }
                            )
                        }

                        Surface(shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Info, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.sos_info_text),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddContact) {
        AddContactDialog(
            onAdd = { contact ->
                val updated = contacts + contact
                scope.launch { prefs.saveSosContacts(updated.toJson()) }
                showAddContact = false
            },
            onDismiss = { showAddContact = false }
        )
    }
}

@Composable
private fun PulsingSOSButton(isSending: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos")
    val scale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (isSending) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse),
        label = "sos_scale")

    Button(
        onClick  = { if (!isSending) onClick() },
        modifier = Modifier.size(110.dp).scale(scale),
        shape    = CircleShape,
        colors   = ButtonDefaults.buttonColors(containerColor = GlycoRed),
        contentPadding = PaddingValues(0.dp),
        border   = BorderStroke(3.dp, GlycoRed.copy(alpha = 0.4f))
    ) {
        if (isSending) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp, color = Color.White)
        } else {
            Text("SOS",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W900),
                color = Color.White)
        }
    }
}

@Composable
private fun ContactCard(contact: SosContact, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            Surface(modifier = Modifier.size(44.dp), shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
                Text(contact.phone, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (contact.role.isNotBlank()) {
                    Text(contact.role, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            if (confirmDelete) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { confirmDelete = false },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = GlycoRed)) {
                        Text(stringResource(R.string.delete))
                    }
                }
            } else {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AlertCard(
    title: String, body: String,
    color: Color,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = color)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AddContactDialog(onAdd: (SosContact) -> Unit, onDismiss: () -> Unit) {
    var name  by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role  by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_contact_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Person, null) })
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.phone_label)) }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Filled.Phone, null) })
                OutlinedTextField(value = role, onValueChange = { role = it },
                    label = { Text(stringResource(R.string.role_label)) }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.role_placeholder)) })
            }
        },
        confirmButton = {
            Button(
                onClick  = { onAdd(SosContact(name = name.trim(), phone = phone.trim(),
                    role = role.trim())) },
                enabled  = name.isNotBlank() && phone.isNotBlank()
            ) { Text(stringResource(R.string.add_contact)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

sealed class SosUiState {
    object Idle                                                         : SosUiState()
    object Sending                                                      : SosUiState()
    object NoContacts                                                   : SosUiState()
    data class Success(val contacts: Int, val locationSent: Boolean)    : SosUiState()
    data class Error(val message: String)                               : SosUiState()
}
