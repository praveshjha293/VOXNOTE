package com.example.ui.components

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.audio.SpeechAudioGenerator
import com.example.ui.viewmodel.NotesMakerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecordBottomSheet(
    viewModel: NotesMakerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recordingState by viewModel.recordingState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isEmulator = remember {
        Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.MODEL.contains("google_sdk") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for x86") ||
        Build.HARDWARE.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu") ||
        Build.PRODUCT.contains("sdk")
    }

    // Default to Spoken Speech Synthesizer on PC / Emulator so user gets immediate audible sound
    var selectedTab by remember { mutableIntStateOf(if (isEmulator) 0 else 0) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSilenceDetectedDialog by remember { mutableStateOf(false) }
    var isPreviewingSpeech by remember { mutableStateOf(false) }

    var recordingTitle by remember {
        mutableStateOf("Voice Note ${java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
    }

    var customSpeechText by remember {
        mutableStateOf("Welcome to today's physics lecture. In classical computing, bits are either zero or one. However, quantum qubits exist in a linear superposition of states. Entanglement enables exponential speedups for specialized algorithms.")
    }

    // Audio File Picker for uploading recordings directly from PC
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = "Uploaded_PC_Audio_${System.currentTimeMillis()}.mp3"
            viewModel.importAudioFile(it, fileName)
            onDismiss()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && recordingState.isRecording) {
            viewModel.stopRecording(recordingTitle, discard = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (recordingState.isRecording) {
                viewModel.stopRecording(recordingTitle, discard = true)
            }
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("audio_record_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tab Selector: PC Voice Input vs Direct Microphone
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PC Voice Input")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Phone Mic")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0) {
                // PC Voice Input & Synthesizer Mode (Guaranteed audible sound for PC & Emulators)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "🖥️ Recommended for PC Testing",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Web browser emulators cannot capture your PC microphone. Type any speech below to produce real, loud audio you can listen to and transcribe with Groq Whisper!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Recording Title",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = recordingTitle,
                        onValueChange = { recordingTitle = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pc_recording_title_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Spoken Speech Text",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = customSpeechText,
                        onValueChange = { customSpeechText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("pc_custom_speech_text"),
                        placeholder = { Text("Enter words to record as voice audio...") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Scenario Chips
                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SpeechAudioGenerator.scenarios.forEach { scenario ->
                            FilterChip(
                                selected = customSpeechText == scenario.text,
                                onClick = {
                                    customSpeechText = scenario.text
                                    recordingTitle = scenario.title
                                },
                                label = { Text(scenario.title.take(18) + "...", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons: Preview Sound & Generate Voice Recording
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isPreviewingSpeech = true
                                SpeechAudioGenerator.previewSpeech(context, customSpeechText) {
                                    isPreviewingSpeech = false
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("preview_speech_btn"),
                            enabled = customSpeechText.isNotBlank() && !isPreviewingSpeech
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPreviewingSpeech) "Speaking..." else "🔊 Preview Sound")
                        }

                        Button(
                            onClick = {
                                viewModel.createCustomSpeechVoiceNote(customSpeechText, recordingTitle)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("generate_spoken_speech_btn"),
                            enabled = customSpeechText.isNotBlank()
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🎙️ Record Voice Note")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Upload from PC button
                    OutlinedButton(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("upload_audio_file_btn")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📁 Upload Audio File (.mp3, .wav, .m4a) from PC")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                // Direct Physical Phone Microphone Mode
                if (recordingState.error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Microphone Notice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = recordingState.error ?: "Microphone unavailable on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startRecording() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry Mic")
                        }
                        OutlinedButton(
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Use PC Voice Input")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (!recordingState.isRecording) {
                    // Idle state
                    Text(
                        text = "Physical Phone Microphone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap the red microphone to record from a real Android phone mic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Note for PC testing: Browser emulators do not stream your computer's mic into Android (producing silence). For PC testing, switch to 'PC Voice Input' above!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.startRecording() },
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("start_recording_btn"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Start Recording",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                } else {
                    // Active recording state
                    Text(
                        text = if (recordingState.isPaused) "Recording Paused" else "Recording From Microphone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val minutes = recordingState.durationSeconds / 60
                    val seconds = recordingState.durationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = if (recordingState.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AmplitudeWaveform(
                        amplitude = recordingState.currentAmplitude,
                        isPaused = recordingState.isPaused
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Discard Button
                        FilledTonalButton(
                            onClick = {
                                viewModel.stopRecording(recordingTitle, discard = true)
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("discard_recording_btn"),
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Discard Recording"
                            )
                        }

                        // Pause / Resume Button
                        Button(
                            onClick = {
                                if (recordingState.isPaused) {
                                    viewModel.resumeRecording()
                                } else {
                                    viewModel.pauseRecording()
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("pause_resume_btn"),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (recordingState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (recordingState.isPaused) "Resume" else "Pause",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Stop & Save Button
                        Button(
                            onClick = {
                                if (isEmulator && viewModel.wasLastRecordingSilent()) {
                                    showSilenceDetectedDialog = true
                                } else {
                                    showSaveDialog = true
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("save_recording_btn"),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF16A34A),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save Recording"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Silence Detected Prompt (Prevents user getting stuck with blank silence on PC!)
    if (showSilenceDetectedDialog) {
        AlertDialog(
            onDismissRequest = { showSilenceDetectedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD97706)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Silence Detected on PC")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Because you are testing on PC in a web browser, the emulator cannot hear your computer's physical microphone. The recorded file is completely silent.\n\nWould you like to generate this voice note using clear spoken speech instead so you hear real sound and can transcribe with Groq?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSilenceDetectedDialog = false
                        viewModel.stopRecording(recordingTitle, discard = true)
                        viewModel.createCustomSpeechVoiceNote(customSpeechText, recordingTitle)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("convert_silent_to_speech_btn")
                ) {
                    Text("🗣️ Convert to Spoken Voice")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSilenceDetectedDialog = false
                        showSaveDialog = true
                    }
                ) {
                    Text("Save Silence Anyway")
                }
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Name Your Recording") },
            text = {
                OutlinedTextField(
                    value = recordingTitle,
                    onValueChange = { recordingTitle = it },
                    label = { Text("Recording Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recording_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.stopRecording(recordingTitle, discard = false)
                        showSaveDialog = false
                        onDismiss()
                    },
                    modifier = Modifier.testTag("confirm_save_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AmplitudeWaveform(
    amplitude: Int,
    isPaused: Boolean,
    barCount: Int = 24
) {
    val normalizedAmp = (amplitude.toFloat() / 32767f).coerceIn(0.08f, 1.0f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val factor = kotlin.math.sin((i.toDouble() / barCount) * Math.PI).toFloat()
            val heightFraction = if (isPaused) 0.12f else (normalizedAmp * factor).coerceIn(0.12f, 1.0f)
            val animatedHeight by animateFloatAsState(
                targetValue = heightFraction,
                animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
                label = "bar_height"
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((56 * animatedHeight).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isPaused) MaterialTheme.colorScheme.outlineVariant
                        else MaterialTheme.colorScheme.primary
                    )
            )
        }
    }
}
