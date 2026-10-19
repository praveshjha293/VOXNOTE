package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.NoteMode
import com.example.ui.components.GroqApiKeyDialog
import com.example.ui.components.ModeSelectionDialog
import com.example.ui.viewmodel.NotesMakerViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: String,
    viewModel: NotesMakerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val activeNote = notes.firstOrNull { it.id == noteId }

    BackHandler {
        onNavigateBack()
    }

    if (activeNote == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Note not found")
        }
        return
    }

    var titleText by remember(activeNote.id) { mutableStateOf(activeNote.title) }
    var contentText by remember(activeNote.id) { mutableStateOf(activeNote.contentMarkdown) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }
    var showGroqKeyDialog by remember { mutableStateOf(false) }
    var showTranscriptSheet by remember { mutableStateOf(false) }

    // Linked audio recording & transcript
    val linkedRecording = recordings.firstOrNull { it.id == activeNote.recordingId }

    // Autosave debouncer: saves 500ms after user pauses typing
    LaunchedEffect(titleText, contentText) {
        delay(500)
        if (titleText != activeNote.title || contentText != activeNote.contentMarkdown) {
            viewModel.updateNoteContent(activeNote.id, titleText, contentText)
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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("editor_back_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = NoteMode.fromId(activeNote.mode).title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (linkedRecording?.transcriptText != null) {
                    IconButton(
                        onClick = { showTranscriptSheet = true },
                        modifier = Modifier.testTag("view_transcript_btn")
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = "View Transcript")
                    }
                }

                IconButton(
                    onClick = {
                        if (preferences.groqApiKey.isBlank()) {
                            showGroqKeyDialog = true
                        } else {
                            showRegenerateDialog = true
                        }
                    },
                    modifier = Modifier.testTag("regenerate_note_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Regenerate Note", tint = MaterialTheme.colorScheme.primary)
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy to Clipboard") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                copyToClipboard(context, "$titleText\n\n$contentText")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Note") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                shareNoteText(context, titleText, contentText)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as Markdown (.md)") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                exportToFile(context, titleText, contentText, "md")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as Plain Text (.txt)") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                exportToFile(context, titleText, contentText, "txt")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Print / Export to PDF") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                printNote(context, titleText, contentText)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Note") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        }

        // Markdown Quick Formatting Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormatToolbarButton(label = "H1") {
                contentText += "\n\n# "
            }
            FormatToolbarButton(label = "H2") {
                contentText += "\n\n## "
            }
            FormatToolbarButton(label = "H3") {
                contentText += "\n\n### "
            }
            FormatToolbarButton(label = "B", isBold = true) {
                contentText += " **key term** "
            }
            FormatToolbarButton(label = "I", isItalic = true) {
                contentText += " *emphasis* "
            }
            FormatToolbarButton(label = "• Bullet") {
                contentText += "\n- "
            }
            FormatToolbarButton(label = "1. Number") {
                contentText += "\n1. "
            }
            FormatToolbarButton(label = "☑ Check") {
                contentText += "\n- [ ] "
            }
            FormatToolbarButton(label = "Formula") {
                contentText += "\n\n$$ f(x) = ... $$\n"
            }
            FormatToolbarButton(label = "[Action]") {
                contentText += "\n- [Action: Task Name (Owner)]"
            }
        }

        // Title and Content Editor
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Title Field
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_editor_title"),
                placeholder = { Text("Note Title") },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Body Content Field
            OutlinedTextField(
                value = contentText,
                onValueChange = { contentText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_editor_body"),
                placeholder = { Text("Note content (Markdown supported)...") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 26.sp,
                    fontFamily = FontFamily.Default
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(120.dp))
        }
    }

    // View Source Transcript Bottom Sheet
    if (showTranscriptSheet) {
        val transcriptSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showTranscriptSheet = false },
            sheetState = transcriptSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Source Audio Transcript",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Verbatim speech transcribed via Groq Whisper",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = linkedRecording?.transcriptText ?: "(No transcript attached)",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        linkedRecording?.transcriptText?.let {
                            copyToClipboard(context, it)
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Transcript")
                }
            }
        }
    }

    // Regenerate Dialog
    if (showRegenerateDialog) {
        ModeSelectionDialog(
            initialMode = NoteMode.fromId(activeNote.mode),
            onDismiss = { showRegenerateDialog = false },
            onConfirm = { mode ->
                viewModel.regenerateNote(activeNote.id, mode)
                showRegenerateDialog = false
            }
        )
    }

    // Groq API Key Dialog
    if (showGroqKeyDialog) {
        GroqApiKeyDialog(
            currentKey = preferences.groqApiKey,
            onDismiss = { showGroqKeyDialog = false },
            onSaveKey = { key ->
                viewModel.saveGroqApiKey(key)
                showRegenerateDialog = true
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this note?") },
            text = { Text("This document will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNote(activeNote.id)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FormatToolbarButton(
    label: String,
    isBold: Boolean = false,
    isItalic: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

// Helpers for Export, Share, Copy, Print
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Note Content", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

fun shareNoteText(context: Context, title: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "# $title\n\n$content")
    }
    context.startActivity(Intent.createChooser(intent, "Share Note"))
}

fun exportToFile(context: Context, title: String, content: String, extension: String) {
    try {
        val safeTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(exportDir, "$safeTitle.$extension")
        FileOutputStream(file).use {
            it.write("# $title\n\n$content".toByteArray(Charsets.UTF_8))
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (extension == "md") "text/markdown" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $extension"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun printNote(context: Context, title: String, content: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val webView = WebView(context)
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>$title</title>
            <style>
                body { font-family: sans-serif; padding: 24px; line-height: 1.6; color: #1F2937; }
                h1 { color: #1E40AF; border-bottom: 2px solid #E5E7EB; padding-bottom: 8px; }
                h2 { color: #374151; margin-top: 20px; }
                ul, ol { padding-left: 20px; }
                code { background: #F3F4F6; padding: 2px 4px; border-radius: 4px; }
            </style>
        </head>
        <body>
            <h1>$title</h1>
            <div>${content.replace("\n", "<br>")}</div>
        </body>
        </html>
    """.trimIndent()

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printAdapter = webView.createPrintDocumentAdapter(title)
            printManager.print(title, printAdapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
