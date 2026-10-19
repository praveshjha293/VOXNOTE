package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.database.RecordingEntity
import com.example.data.model.TranscriptionStatus
import com.example.ui.components.Esp32ConnectDialog
import com.example.ui.components.Esp32TransferDialog
import com.example.ui.components.GroqApiKeyDialog
import com.example.ui.components.ModeSelectionDialog
import com.example.ui.viewmodel.NotesMakerViewModel

enum class RecordingSortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    TITLE_ASC("Title A-Z"),
    DURATION_DESC("Longest"),
    SIZE_DESC("Largest File")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    viewModel: NotesMakerViewModel,
    onOpenNote: (String) -> Unit
) {
    val recordings by viewModel.recordings.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSourceFilter by remember { mutableStateOf("ALL") } // ALL, PHONE, ESP32, IMPORTED
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // ALL, TRANSCRIBED, PENDING
    var sortOption by remember { mutableStateOf(RecordingSortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedRecordingIds = remember { mutableStateMapOf<String, Boolean>() }

    var renameTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var newTitleInput by remember { mutableStateOf("") }
    var deleteConfirmTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    var noteGenTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var showEsp32Dialog by remember { mutableStateOf(false) }
    var showEsp32ConnectDialog by remember { mutableStateOf(false) }
    var showGroqKeyDialog by remember { mutableStateOf(false) }
    var pendingTranscribeRecId by remember { mutableStateOf<String?>(null) }

    // Audio file picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast('/') ?: "imported_audio.m4a"
            viewModel.importAudioFile(it, fileName)
        }
    }

    // Filter & Sort
    val filteredRecordings = recordings.filter { rec ->
        val matchesSearch = rec.title.contains(searchQuery, ignoreCase = true) ||
                (rec.transcriptText?.contains(searchQuery, ignoreCase = true) == true)
        val matchesSource = when (selectedSourceFilter) {
            "PHONE" -> rec.source == "PHONE"
            "ESP32" -> rec.source == "ESP32"
            "IMPORTED" -> rec.source == "IMPORTED"
            else -> true
        }
        val matchesStatus = when (selectedStatusFilter) {
            "TRANSCRIBED" -> rec.transcriptionStatus == TranscriptionStatus.COMPLETED.name
            "PENDING" -> rec.transcriptionStatus != TranscriptionStatus.COMPLETED.name
            else -> true
        }
        matchesSearch && matchesSource && matchesStatus
    }.sortedWith { a, b ->
        when (sortOption) {
            RecordingSortOption.DATE_DESC -> b.createdAt.compareTo(a.createdAt)
            RecordingSortOption.DATE_ASC -> a.createdAt.compareTo(b.createdAt)
            RecordingSortOption.TITLE_ASC -> a.title.compareTo(b.title, ignoreCase = true)
            RecordingSortOption.DURATION_DESC -> b.durationSeconds.compareTo(a.durationSeconds)
            RecordingSortOption.SIZE_DESC -> b.fileSizeBytes.compareTo(a.fileSizeBytes)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                val countSelected = selectedRecordingIds.count { it.value }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        isMultiSelectMode = false
                        selectedRecordingIds.clear()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Exit Selection")
                    }
                    Text(
                        text = "$countSelected Selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    TextButton(onClick = {
                        val allSelected = filteredRecordings.all { selectedRecordingIds[it.id] == true }
                        filteredRecordings.forEach { selectedRecordingIds[it.id] = !allSelected }
                    }) {
                        Text("Select All")
                    }
                    IconButton(
                        onClick = { showBatchDeleteConfirm = true },
                        enabled = countSelected > 0
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Text(
                    text = "Recordings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        modifier = Modifier.testTag("import_audio_btn")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import Audio")
                    }
                    IconButton(
                        onClick = {
                            if (connectionState.isConnected) {
                                showEsp32Dialog = true
                            } else {
                                showEsp32ConnectDialog = true
                            }
                        },
                        modifier = Modifier.testTag("recordings_esp32_btn")
                    ) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = "ESP32 Transfer",
                            tint = if (connectionState.isConnected) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            RecordingSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        sortOption = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("recording_search_input"),
            placeholder = { Text("Search recordings & transcripts...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Filter Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedSourceFilter == "ALL",
                    onClick = { selectedSourceFilter = "ALL" },
                    label = { Text("All Sources") }
                )
            }
            item {
                FilterChip(
                    selected = selectedSourceFilter == "PHONE",
                    onClick = { selectedSourceFilter = "PHONE" },
                    label = { Text("Phone Mic") }
                )
            }
            item {
                FilterChip(
                    selected = selectedSourceFilter == "ESP32",
                    onClick = { selectedSourceFilter = "ESP32" },
                    label = { Text("ESP32") }
                )
            }
            item {
                FilterChip(
                    selected = selectedSourceFilter == "IMPORTED",
                    onClick = { selectedSourceFilter = "IMPORTED" },
                    label = { Text("Imported") }
                )
            }
            item {
                FilterChip(
                    selected = selectedStatusFilter == "TRANSCRIBED",
                    onClick = {
                        selectedStatusFilter = if (selectedStatusFilter == "TRANSCRIBED") "ALL" else "TRANSCRIBED"
                    },
                    label = { Text("Transcribed Only") }
                )
            }
        }

        // Recordings List
        if (filteredRecordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching recordings" else "No recordings found",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Record using the microphone, connect an ESP32, or import audio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
            ) {
                items(filteredRecordings, key = { it.id }) { recording ->
                    val isPlaying = playbackState.currentRecordingId == recording.id && playbackState.isPlaying
                    val currentPos = if (playbackState.currentRecordingId == recording.id) playbackState.currentPositionMs else 0
                    val duration = if (playbackState.currentRecordingId == recording.id) playbackState.durationMs else (recording.durationSeconds * 1000)
                    val isSelected = selectedRecordingIds[recording.id] == true

                    RecordingCardItem(
                        recording = recording,
                        isPlaying = isPlaying,
                        currentPosMs = currentPos,
                        durationMs = duration,
                        isMultiSelectMode = isMultiSelectMode,
                        isSelected = isSelected,
                        onToggleSelect = {
                            selectedRecordingIds[recording.id] = !(selectedRecordingIds[recording.id] ?: false)
                        },
                        onLongClick = {
                            isMultiSelectMode = true
                            selectedRecordingIds[recording.id] = true
                        },
                        onTogglePlay = { viewModel.togglePlayback(recording.id, recording.filePath) },
                        onSeek = { viewModel.seekPlayback(it) },
                        onTranscribe = {
                            if (preferences.groqApiKey.isBlank()) {
                                pendingTranscribeRecId = recording.id
                                showGroqKeyDialog = true
                            } else {
                                viewModel.transcribeRecording(recording.id)
                            }
                        },
                        onGenerateNotes = {
                            if (preferences.groqApiKey.isBlank()) {
                                showGroqKeyDialog = true
                            } else {
                                noteGenTarget = recording
                            }
                        },
                        onRename = {
                            renameTarget = recording
                            newTitleInput = recording.title
                        },
                        onDelete = { deleteConfirmTarget = recording }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }

    // Rename Dialog
    renameTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Recording") },
            text = {
                OutlinedTextField(
                    value = newTitleInput,
                    onValueChange = { newTitleInput = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_input")
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newTitleInput.isNotBlank()) {
                        viewModel.renameRecording(rec.id, newTitleInput)
                    }
                    renameTarget = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Single Confirmation Dialog
    deleteConfirmTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Delete Recording?") },
            text = { Text("Are you sure you want to delete '${rec.title}'? The audio file will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRecording(rec.id)
                        deleteConfirmTarget = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteConfirm) {
        val count = selectedRecordingIds.count { it.value }
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("Delete $count Recordings?") },
            text = { Text("This will permanently delete all $count selected recordings and their audio files.") },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedRecordingIds.filter { it.value }.keys.toList()
                        for (id in idsToDelete) {
                            viewModel.deleteRecording(id)
                        }
                        selectedRecordingIds.clear()
                        isMultiSelectMode = false
                        showBatchDeleteConfirm = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ESP32 Transfer Browser Sheet
    if (showEsp32Dialog) {
        Esp32TransferDialog(
            viewModel = viewModel,
            onDismiss = { showEsp32Dialog = false }
        )
    }

    // Mode Selection Dialog
    noteGenTarget?.let { rec ->
        val transcript = rec.transcriptText ?: ""
        ModeSelectionDialog(
            onDismiss = { noteGenTarget = null },
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
                noteGenTarget = null
            }
        )
    }

    // Groq API Key Dialog
    if (showGroqKeyDialog) {
        GroqApiKeyDialog(
            currentKey = preferences.groqApiKey,
            onDismiss = {
                showGroqKeyDialog = false
                pendingTranscribeRecId = null
            },
            onSaveKey = { key ->
                viewModel.saveGroqApiKey(key)
                val id = pendingTranscribeRecId
                if (id != null) {
                    viewModel.transcribeRecording(id)
                    pendingTranscribeRecId = null
                }
            }
        )
    }

    // ESP32 Connection Dialog
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
}

@Composable
fun RecordingCardItem(
    recording: RecordingEntity,
    isPlaying: Boolean,
    currentPosMs: Int,
    durationMs: Int,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onTranscribe: () -> Unit,
    onGenerateNotes: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rec_item_${recording.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleMedium,
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
                            text = "${recording.fileSizeBytes / 1024} KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = recording.source,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Options Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playback Bar
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

            // Transcript preview if available
            recording.transcriptText?.let { transcript ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "\"$transcript\"",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
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
                        modifier = Modifier.testTag("transcribe_item_${recording.id}")
                    ) {
                        Text(if (recording.transcriptionStatus == TranscriptionStatus.TRANSCRIBING.name) "Transcribing..." else "Transcribe (Whisper)")
                    }
                } else {
                    FilledTonalButton(
                        onClick = onGenerateNotes,
                        modifier = Modifier.testTag("generate_notes_item_${recording.id}")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Notes")
                    }
                }
            }
        }
    }
}
