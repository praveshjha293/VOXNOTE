package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Esp32FileInfo
import com.example.ui.viewmodel.NotesMakerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Esp32TransferDialog(
    viewModel: NotesMakerViewModel,
    onDismiss: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val esp32Files by viewModel.esp32Files.collectAsState()
    val isFetching by viewModel.isFetchingEsp32Files.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val existingRecordings by viewModel.recordings.collectAsState()

    val selectedFiles = remember { mutableStateMapOf<String, Boolean>() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        if (connectionState.isConnected) {
            viewModel.fetchEsp32Files()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("esp32_transfer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connectionState.isConnected) Color(0xFF16A34A)
                                    else MaterialTheme.colorScheme.outline
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (connectionState.isSimulationMode) "ESP32 (Simulation Mode)" else "ESP32 Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = connectionState.currentSsid ?: "NotesMaker-AP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.fetchEsp32Files() },
                    enabled = connectionState.isConnected && !isFetching,
                    modifier = Modifier.testTag("refresh_esp32_files_btn")
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Files")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Download Progress Bar
            if (downloadProgress != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Transferring: $downloadProgress%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TextButton(
                                onClick = { viewModel.cancelTransfer() },
                                modifier = Modifier.testTag("cancel_transfer_btn")
                            ) {
                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (downloadProgress ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Connection Check
            if (!connectionState.isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Not Connected to ESP32",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Hold physical button on ESP32 for 3 seconds.\n2. Confirm Blue LED is ON.\n3. Tap 'Connect' below.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Button(
                                onClick = { viewModel.connectToEsp32() },
                                modifier = Modifier.testTag("sheet_connect_esp32_btn")
                            ) {
                                Text("Connect to ESP32")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.toggleSimulationMode(true)
                                    viewModel.connectToEsp32()
                                }
                            ) {
                                Text("Use Simulation")
                            }
                        }
                    }
                }
            } else {
                // File List Header & Multi-select
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available Files (${esp32Files.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            val allSelected = esp32Files.all { selectedFiles[it.id] == true }
                            esp32Files.forEach { selectedFiles[it.id] = !allSelected }
                        }
                    ) {
                        val allSelected = esp32Files.isNotEmpty() && esp32Files.all { selectedFiles[it.id] == true }
                        Text(if (allSelected) "Deselect All" else "Select All")
                    }
                }

                if (esp32Files.isEmpty() && !isFetching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No audio recordings found on ESP32 storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(esp32Files) { file ->
                            val isDownloaded = existingRecordings.any {
                                it.source == "ESP32" && it.filePath.contains(file.id)
                            }
                            val isSelected = selectedFiles[file.id] == true

                            Esp32FileItem(
                                file = file,
                                isDownloaded = isDownloaded,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    selectedFiles[file.id] = !(selectedFiles[file.id] ?: false)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Download Actions
                val countSelected = selectedFiles.count { it.value }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val targets = esp32Files.filter { selectedFiles[it.id] == true }
                            viewModel.downloadEsp32Files(targets) {
                                selectedFiles.clear()
                            }
                        },
                        enabled = countSelected > 0 && downloadProgress == null,
                        modifier = Modifier.testTag("download_selected_esp32_btn")
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download ($countSelected)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun Esp32FileItem(
    file: Esp32FileInfo,
    isDownloaded: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onToggleSelect() }
            .testTag("esp32_file_${file.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val sizeKb = file.sizeBytes / 1024
                val durationText = "${file.durationSeconds / 60}m ${file.durationSeconds % 60}s"
                Text(
                    text = "$durationText • $sizeKb KB • ${file.format.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isDownloaded) {
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF16A34A)
                        )
                    }
                }
            }
        }
    }
}
