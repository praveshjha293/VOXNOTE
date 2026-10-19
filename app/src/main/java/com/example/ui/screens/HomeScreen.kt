package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.database.NoteEntity
import com.example.data.database.RecordingEntity
import com.example.data.model.NoteMode
import com.example.data.model.TranscriptionStatus
import com.example.ui.components.AudioRecordBottomSheet
import com.example.ui.components.Esp32ConnectDialog
import com.example.ui.components.Esp32TransferDialog
import com.example.ui.components.GroqApiKeyDialog
import com.example.ui.components.ModeSelectionDialog
import com.example.ui.viewmodel.NotesMakerViewModel

@Composable
fun HomeScreen(
    viewModel: NotesMakerViewModel,
    onNavigateToRecordings: () -> Unit,
    onNavigateToNotes: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenNote: (String) -> Unit
) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val activeOp by viewModel.activeOperation.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    var showRecordSheet by remember { mutableStateOf(false) }
    var showEsp32Sheet by remember { mutableStateOf(false) }
    var showEsp32ConnectDialog by remember { mutableStateOf(false) }
    var showGroqKeyDialog by remember { mutableStateOf(false) }
    var pendingTranscribeRecordingId by remember { mutableStateOf<String?>(null) }
    var noteGenTargetRecording by remember { mutableStateOf<RecordingEntity?>(null) }

    // Microphone Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showRecordSheet = true
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required to record audio. You can also tap 'Sample Audio' to test.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val homeAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = "Uploaded_PC_Audio_${System.currentTimeMillis()}.mp3"
            viewModel.importAudioFile(it, fileName)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Notes Maker",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Audio recordings to structured intelligence",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Connection Pill Status
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (connectionState.isConnected) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (connectionState.isConnected) Color(0xFFBBF7D0) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .clickable {
                            if (connectionState.isConnected) {
                                showEsp32Sheet = true
                            } else {
                                showEsp32ConnectDialog = true
                            }
                        }
                        .testTag("connection_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        connectionState.isConnected -> Color(0xFF16A34A)
                                        connectionState.isConnecting -> Color(0xFFD97706)
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                connectionState.isConnected -> if (connectionState.isSimulationMode) "ESP32 (Sim)" else "ESP32 Connected"
                                connectionState.isConnecting -> "Connecting..."
                                else -> "ESP32 Idle"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (connectionState.isConnected) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Groq API Key Setup Banner (Shown if key is missing)
        if (preferences.groqApiKey.isBlank()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable { showGroqKeyDialog = true }
                        .testTag("groq_key_setup_banner"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Groq API Key Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Tap here to configure your Groq key for Whisper voice transcription and AI note synthesis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB45309)
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Active Operation Progress Banner
        if (activeOp != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("active_operation_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Processing",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = activeOp ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Primary Action Cards Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Record Button Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                showRecordSheet = true
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        .testTag("home_record_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Record Audio",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Record Audio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Direct phone microphone",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Connect to ESP32 Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (connectionState.isConnected) {
                                showEsp32Sheet = true
                            } else {
                                showEsp32ConnectDialog = true
                            }
                        }
                        .testTag("home_esp32_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connectionState.isConnected) Color(0xFFDCFCE7)
                                    else MaterialTheme.colorScheme.surface
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (connectionState.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = "ESP32",
                                tint = if (connectionState.isConnected) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (connectionState.isConnected) "ESP32 Files" else "Connect ESP32",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (connectionState.isConnected) "Browse & transfer" else "Set IP, port & connect",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Quick Emulator & PC Testing Shortcuts
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.createSampleVoiceNote("Lecture: Quantum Wavefunctions") },
                        modifier = Modifier.weight(1f).testTag("sample_voice_note_btn")
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Spoken Note")
                    }
                    OutlinedButton(
                        onClick = { homeAudioPickerLauncher.launch("audio/*") },
                        modifier = Modifier.weight(1f).testTag("home_upload_audio_btn")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload PC Audio")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.toggleSimulationMode(true)
                            viewModel.connectToEsp32()
                        },
                        modifier = Modifier.weight(1f).testTag("quick_sim_esp32_btn")
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate ESP32")
                    }
                    OutlinedButton(
                        onClick = { showEsp32ConnectDialog = true },
                        modifier = Modifier.weight(1f).testTag("quick_esp32_config_btn")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ESP32 Config")
                    }
                }
            }
        }

        // Quick Stats Strip
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem(label = "Recordings", value = "${recordings.size}")
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    val transcribedCount = recordings.count { it.transcriptionStatus == TranscriptionStatus.COMPLETED.name }
                    StatItem(label = "Transcribed", value = "$transcribedCount")
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    StatItem(label = "AI Notes", value = "${notes.size}")
                }
            }
        }

        // Recent Recordings Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Audio Recordings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToRecordings) {
                    Text("See All")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (recordings.isEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No recordings yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Record with phone mic, tap 'Add Sample Audio Note', or connect ESP32.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recordings.take(3)) { recording ->
                HomeRecordingCard(
                    recording = recording,
                    isPlaying = playbackState.currentRecordingId == recording.id && playbackState.isPlaying,
                    currentPosMs = if (playbackState.currentRecordingId == recording.id) playbackState.currentPositionMs else 0,
                    durationMs = if (playbackState.currentRecordingId == recording.id) playbackState.durationMs else (recording.durationSeconds * 1000),
                    onTogglePlay = { viewModel.togglePlayback(recording.id, recording.filePath) },
                    onSeek = { viewModel.seekPlayback(it) },
                    onTranscribe = {
                        if (preferences.groqApiKey.isBlank()) {
                            pendingTranscribeRecordingId = recording.id
                            showGroqKeyDialog = true
                        } else {
                            viewModel.transcribeRecording(recording.id)
                        }
                    },
                    onGenerateNotes = {
                        if (preferences.groqApiKey.isBlank()) {
                            showGroqKeyDialog = true
                        } else {
                            noteGenTargetRecording = recording
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Recent Notes Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved AI Notes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToNotes) {
                    Text("See All")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (notes.isEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No notes generated yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Transcribe a recording and convert it to structured notes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(notes.take(3)) { note ->
                HomeNoteCard(
                    note = note,
                    onClick = { onOpenNote(note.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Groq API Key Configuration Dialog
    if (showGroqKeyDialog) {
        GroqApiKeyDialog(
            currentKey = preferences.groqApiKey,
            onDismiss = {
                showGroqKeyDialog = false
                pendingTranscribeRecordingId = null
            },
            onSaveKey = { key ->
                viewModel.saveGroqApiKey(key)
                val targetId = pendingTranscribeRecordingId
                if (targetId != null) {
                    viewModel.transcribeRecording(targetId)
                    pendingTranscribeRecordingId = null
                }
            }
        )
    }

    // ESP32 IP, Port & Credentials Dialog
    if (showEsp32ConnectDialog) {
        Esp32ConnectDialog(
            initialIp = preferences.esp32Ip,
            initialPort = preferences.esp32Port,
            initialSsid = preferences.esp32Ssid,
            initialPassword = preferences.esp32Password,
            onDismiss = { showEsp32ConnectDialog = false },
            onConnect = { ip, port, ssid, password ->
                viewModel.saveEsp32Settings(
                    ssid = ssid,
                    password = password,
                    ip = ip,
                    port = port,
                    timeout = preferences.esp32TimeoutSec,
                    autoReconnect = preferences.esp32AutoReconnect
                )
                viewModel.connectToEsp32()
            },
            onUseSimulationMode = {
                viewModel.toggleSimulationMode(true)
                viewModel.connectToEsp32()
            }
        )
    }

    // Audio Recording Bottom Sheet
    if (showRecordSheet) {
        AudioRecordBottomSheet(
            viewModel = viewModel,
            onDismiss = { showRecordSheet = false }
        )
    }

    // ESP32 Transfer Dialog
    if (showEsp32Sheet) {
        Esp32TransferDialog(
            viewModel = viewModel,
            onDismiss = { showEsp32Sheet = false }
        )
    }

    // Mode Selection Dialog for AI Notes
    noteGenTargetRecording?.let { rec ->
        val transcript = rec.transcriptText ?: ""
        ModeSelectionDialog(
            onDismiss = { noteGenTargetRecording = null },
            onConfirm = { mode ->
                viewModel.generateNotes(
                    recordingId = rec.id,
                    transcript = transcript,
                    mode = mode,
                    titleSuggestion = rec.title,
                    onComplete = { note ->
                        onOpenNote(note.id)
                    }
                )
                noteGenTargetRecording = null
            }
        )
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HomeRecordingCard(
    recording: RecordingEntity,
    isPlaying: Boolean,
    currentPosMs: Int,
    durationMs: Int,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onTranscribe: () -> Unit,
    onGenerateNotes: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_rec_${recording.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${recording.durationSeconds / 60}m ${recording.durationSeconds % 60}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = recording.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (recording.transcriptionStatus) {
                        TranscriptionStatus.COMPLETED.name -> Color(0xFFF0FDF4)
                        TranscriptionStatus.TRANSCRIBING.name -> MaterialTheme.colorScheme.primaryContainer
                        TranscriptionStatus.FAILED.name -> Color(0xFFFEF2F2)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when (recording.transcriptionStatus) {
                            TranscriptionStatus.COMPLETED.name -> "Transcribed"
                            TranscriptionStatus.TRANSCRIBING.name -> "Transcribing..."
                            TranscriptionStatus.FAILED.name -> "Failed"
                            else -> "Audio Ready"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (recording.transcriptionStatus) {
                            TranscriptionStatus.COMPLETED.name -> Color(0xFF16A34A)
                            TranscriptionStatus.TRANSCRIBING.name -> MaterialTheme.colorScheme.onPrimaryContainer
                            TranscriptionStatus.FAILED.name -> Color(0xFFDC2626)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = if (durationMs > 0) currentPosMs.toFloat() / durationMs.toFloat() else 0f,
                    onValueChange = { fraction -> onSeek((fraction * durationMs).toInt()) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val posSec = currentPosMs / 1000
                Text(
                    text = String.format("%02d:%02d", posSec / 60, posSec % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (recording.transcriptionStatus != TranscriptionStatus.COMPLETED.name) {
                    Button(
                        onClick = onTranscribe,
                        enabled = recording.transcriptionStatus != TranscriptionStatus.TRANSCRIBING.name,
                        modifier = Modifier.testTag("transcribe_btn_${recording.id}")
                    ) {
                        Text(if (recording.transcriptionStatus == TranscriptionStatus.TRANSCRIBING.name) "Transcribing..." else "Transcribe with Groq")
                    }
                } else {
                    FilledTonalButton(
                        onClick = onGenerateNotes,
                        modifier = Modifier.testTag("gen_notes_btn_${recording.id}")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate AI Notes")
                    }
                }
            }
        }
    }
}

@Composable
fun HomeNoteCard(
    note: NoteEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("home_note_${note.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = NoteMode.fromId(note.mode).title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = note.contentMarkdown.replace("#", "").trim(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
