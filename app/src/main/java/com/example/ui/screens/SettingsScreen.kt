package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteMode
import com.example.ui.viewmodel.NotesMakerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: NotesMakerViewModel) {
    val prefs by viewModel.preferences.collectAsState()
    val context = LocalContext.current

    // ESP32 state
    var ssid by remember(prefs.esp32Ssid) { mutableStateOf(prefs.esp32Ssid) }
    var password by remember(prefs.esp32Password) { mutableStateOf(prefs.esp32Password) }
    var ip by remember(prefs.esp32Ip) { mutableStateOf(prefs.esp32Ip) }
    var port by remember(prefs.esp32Port) { mutableStateOf(prefs.esp32Port.toString()) }
    var timeout by remember(prefs.esp32TimeoutSec) { mutableStateOf(prefs.esp32TimeoutSec.toString()) }
    var autoReconnect by remember(prefs.esp32AutoReconnect) { mutableStateOf(prefs.esp32AutoReconnect) }
    var showPassword by remember { mutableStateOf(false) }

    // Groq API Key state
    var apiKeyInput by remember(prefs.groqApiKey) { mutableStateOf(prefs.groqApiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    // AI Models state
    var transcriptionModel by remember(prefs.transcriptionModel) { mutableStateOf(prefs.transcriptionModel) }
    var noteModel by remember(prefs.noteModel) { mutableStateOf(prefs.noteModel) }
    var defaultMode by remember(prefs.defaultNoteMode) { mutableStateOf(prefs.defaultNoteMode) }
    var speechLang by remember(prefs.speechLanguage) { mutableStateOf(prefs.speechLanguage) }
    var customPrompt by remember(prefs.customInstructions) { mutableStateOf(prefs.customInstructions) }

    // Firmware & Dialog state
    var showFirmwareDialog by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ESP32 Wi-Fi hardware, Groq STT/LLM, and privacy preferences",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 1: ESP32 Hardware Configuration
        item {
            SettingsSectionHeader(title = "ESP32 Wi-Fi & Hardware Integration", icon = Icons.Default.Wifi)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Physical button instruction callout
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "ESP32 3-Second Button: Press and hold the physical button for 3s to turn on the Blue LED and broadcast the Wi-Fi AP.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulation Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Simulation Mode (Mock ESP32)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Simulates ESP32 connection & audio files when hardware is not present.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.isSimulationMode,
                            onCheckedChange = { viewModel.toggleSimulationMode(it) },
                            modifier = Modifier.testTag("simulation_mode_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // SSID Field
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = { Text("Wi-Fi SSID") },
                        modifier = Modifier.fillMaxWidth().testTag("esp32_ssid_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Wi-Fi Password") },
                        modifier = Modifier.fillMaxWidth().testTag("esp32_pwd_input"),
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it },
                            label = { Text("Device IP Address") },
                            modifier = Modifier.weight(2f).testTag("esp32_ip_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f).testTag("esp32_port_input"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = timeout,
                        onValueChange = { timeout = it },
                        label = { Text("Connection Timeout (seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showFirmwareDialog = true },
                            modifier = Modifier.testTag("view_firmware_btn")
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Firmware Code")
                        }

                        Button(
                            onClick = {
                                viewModel.saveEsp32Settings(
                                    ssid = ssid,
                                    password = password,
                                    ip = ip,
                                    port = port.toIntOrNull() ?: 80,
                                    timeout = timeout.toIntOrNull() ?: 15,
                                    autoReconnect = autoReconnect
                                )
                            },
                            modifier = Modifier.testTag("save_esp32_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save ESP32")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 2: Groq Speech-to-Text & LLM Configuration
        item {
            SettingsSectionHeader(title = "Groq AI & Keystore Security", icon = Icons.Default.Psychology)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF16A34A))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Android Keystore Encrypted: Your private Groq API key is stored securely with AES-256-GCM hardware-backed cryptography.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF166534)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // API Key Field
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Groq API Key (gsk_...)") },
                        modifier = Modifier.fillMaxWidth().testTag("groq_api_key_input"),
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Transcription Model
                    OutlinedTextField(
                        value = transcriptionModel,
                        onValueChange = { transcriptionModel = it },
                        label = { Text("Transcription Model") },
                        supportingText = { Text("Recommended: whisper-large-v3-turbo") },
                        modifier = Modifier.fillMaxWidth().testTag("transcription_model_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Note LLM Model
                    OutlinedTextField(
                        value = noteModel,
                        onValueChange = { noteModel = it },
                        label = { Text("Note Generation LLM Model") },
                        supportingText = { Text("Recommended: llama-3.3-70b-versatile or llama-3.1-8b-instant") },
                        modifier = Modifier.fillMaxWidth().testTag("llm_model_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Speech Language
                    OutlinedTextField(
                        value = speechLang,
                        onValueChange = { speechLang = it },
                        label = { Text("Audio Speech Language") },
                        supportingText = { Text("e.g. 'auto' for auto-detection, 'en', 'es', 'fr', 'de'") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom Instructions Prompt
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        label = { Text("Custom Prompt Guidance (Optional)") },
                        supportingText = { Text("Appended when using 'Custom Instructions' note mode") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = {
                                viewModel.saveGroqApiKey(apiKeyInput)
                                viewModel.saveAiModelSettings(
                                    transcriptionModel, noteModel, defaultMode, speechLang
                                )
                                viewModel.setCustomInstructions(customPrompt)
                            },
                            modifier = Modifier.testTag("save_groq_settings_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save AI Settings")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 3: Storage & Local Privacy
        item {
            SettingsSectionHeader(title = "Local Storage & Privacy", icon = Icons.Default.Storage)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val recordingsDir = File(context.filesDir, "audio_recordings")
                    val audioBytes = if (recordingsDir.exists()) {
                        recordingsDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                    } else 0L
                    val audioMb = String.format("%.2f", audioBytes / (1024.0 * 1024.0))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Local Audio Storage",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$audioMb MB stored on device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showClearCacheConfirm = true },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Cache")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Zero Telemetry Policy",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your recordings, transcripts, and notes reside strictly on your local device. Data is transmitted to Groq exclusively when you press Transcribe or Generate Notes. We never collect analytics, tracks, or upload background telemetry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 4: Theme Preference
        item {
            SettingsSectionHeader(title = "Appearance", icon = Icons.Default.LightMode)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    listOf("SYSTEM" to "System Default", "LIGHT" to "Light Theme", "DARK" to "Dark Theme").forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.themeMode == code,
                                onClick = { viewModel.setTheme(code) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    // Firmware Code Modal
    if (showFirmwareDialog) {
        AlertDialog(
            onDismissRequest = { showFirmwareDialog = false },
            title = { Text("ESP32 Firmware & Wiring") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    Text(
                        text = Esp32FirmwareGuide.WIRING_INSTRUCTIONS,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = Esp32FirmwareGuide.FIRMWARE_CODE,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    copyToClipboard(context, Esp32FirmwareGuide.FIRMWARE_CODE)
                    showFirmwareDialog = false
                }) {
                    Text("Copy Code")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFirmwareDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear Cache Confirm Dialog
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear Temporary Cache?") },
            text = { Text("This will remove temporary export files and cached audio downloads. Saved recordings in your library will remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            File(context.cacheDir, "exports").deleteRecursively()
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                        showClearCacheConfirm = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
