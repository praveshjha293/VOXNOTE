package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.database.NoteEntity
import com.example.data.model.NoteMode
import com.example.ui.viewmodel.NotesMakerViewModel

@Composable
fun NotesScreen(
    viewModel: NotesMakerViewModel,
    onOpenNote: (String) -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedModeFilter by remember { mutableStateOf("ALL") }
    var deleteConfirmNote by remember { mutableStateOf<NoteEntity?>(null) }
    val context = LocalContext.current

    val filteredNotes = notes.filter { note ->
        val matchesSearch = note.title.contains(searchQuery, ignoreCase = true) ||
                note.contentMarkdown.contains(searchQuery, ignoreCase = true)
        val matchesMode = if (selectedModeFilter == "ALL") true else note.mode == selectedModeFilter
        matchesSearch && matchesMode
    }

    val pinnedNotes = filteredNotes.filter { it.isPinned }
    val otherNotes = filteredNotes.filter { !it.isPinned }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Screen Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filteredNotes.size} structured document(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("notes_search_input"),
                placeholder = { Text("Search title, headings, and notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Mode Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedModeFilter == "ALL",
                        onClick = { selectedModeFilter = "ALL" },
                        label = { Text("All Modes") }
                    )
                }
                NoteMode.entries.forEach { mode ->
                    item {
                        FilterChip(
                            selected = selectedModeFilter == mode.id,
                            onClick = { selectedModeFilter = mode.id },
                            label = { Text(mode.title) }
                        )
                    }
                }
            }

            // Empty state
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching notes found" else "No notes created yet",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Transcribe an audio recording or tap '+' below to create a blank note.",
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
                    if (pinnedNotes.isNotEmpty()) {
                        item {
                            Text(
                                text = "PINNED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(pinnedNotes, key = { it.id }) { note ->
                            NoteCardItem(
                                note = note,
                                onClick = { onOpenNote(note.id) },
                                onTogglePin = { viewModel.toggleNotePinned(note.id) },
                                onDelete = { deleteConfirmNote = note }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    if (otherNotes.isNotEmpty()) {
                        if (pinnedNotes.isNotEmpty()) {
                            item {
                                Text(
                                    text = "ALL NOTES",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                        items(otherNotes, key = { it.id }) { note ->
                            NoteCardItem(
                                note = note,
                                onClick = { onOpenNote(note.id) },
                                onTogglePin = { viewModel.toggleNotePinned(note.id) },
                                onDelete = { deleteConfirmNote = note }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        // FAB to create a blank note
        FloatingActionButton(
            onClick = {
                viewModel.createManualNote(title = "New Document")
                val active = viewModel.activeNoteForEditor.value
                active?.let { onOpenNote(it.id) }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 20.dp)
                .testTag("create_new_note_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = androidx.compose.ui.graphics.Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Blank Note")
        }
    }

    // Delete Confirmation Dialog
    deleteConfirmNote?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteConfirmNote = null },
            title = { Text("Delete Note?") },
            text = { Text("Are you sure you want to delete '${note.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNote(note.id)
                        deleteConfirmNote = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmNote = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NoteCardItem(
    note: NoteEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("note_item_${note.id}"),
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (note.isPinned) "Unpin" else "Pin to Top") },
                            leadingIcon = {
                                Icon(
                                    if (note.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onTogglePin()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Excerpt snippet
            val snippet = note.contentMarkdown
                .lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .take(3)
                .joinToString(" ")
                .replace("*", "")
                .trim()

            Text(
                text = if (snippet.isNotBlank()) snippet else "(Empty note content)",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer metadata
            val wordCount = note.contentMarkdown.split("\\s+".toRegex()).count { it.isNotBlank() }
            val dateStr = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(note.updatedAt))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$wordCount words • Updated $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (note.recordingId != null) {
                    Text(
                        text = "Linked to Audio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
