package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.NoteEntity
import com.example.data.database.RecordingEntity
import com.example.data.model.DeviceConnectionState
import com.example.data.model.Esp32FileInfo
import com.example.data.model.NoteMode
import com.example.data.preferences.UserPreferences
import com.example.data.repository.NotesMakerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class NotesMakerViewModel(application: Application) : AndroidViewModel(application) {

    val repository = NotesMakerRepository(application)

    val recordings: StateFlow<List<RecordingEntity>> = repository.recordingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = repository.notesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<UserPreferences> = repository.preferencesFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            UserPreferences(
                esp32Ssid = "NotesMaker-AP",
                esp32Password = "notesmaker123",
                esp32Ip = "192.168.4.1",
                esp32Port = 80,
                esp32TimeoutSec = 15,
                esp32AutoReconnect = false,
                isSimulationMode = false,
                groqApiKey = "",
                transcriptionModel = "whisper-large-v3-turbo",
                noteModel = "llama-3.3-70b-versatile",
                defaultNoteMode = "DETAILED",
                speechLanguage = "auto",
                themeMode = "SYSTEM",
                customInstructions = ""
            )
        )

    val connectionState: StateFlow<DeviceConnectionState> = repository.connectionState
    val recordingState = repository.recordingState
    val playbackState = repository.playbackState
    val activeOperation = repository.activeOperation

    private val _esp32Files = MutableStateFlow<List<Esp32FileInfo>>(emptyList())
    val esp32Files: StateFlow<List<Esp32FileInfo>> = _esp32Files.asStateFlow()

    private val _isFetchingEsp32Files = MutableStateFlow(false)
    val isFetchingEsp32Files: StateFlow<Boolean> = _isFetchingEsp32Files.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _activeNoteForEditor = MutableStateFlow<NoteEntity?>(null)
    val activeNoteForEditor: StateFlow<NoteEntity?> = _activeNoteForEditor.asStateFlow()

    // --- Audio Recording Actions ---

    fun startRecording(): Boolean {
        val result = repository.recorderManager.startRecording()
        return if (result.isSuccess) {
            true
        } else {
            _uiMessage.value = "Failed to start recording: ${result.exceptionOrNull()?.message}"
            false
        }
    }

    fun pauseRecording() {
        repository.recorderManager.pauseRecording()
    }

    fun resumeRecording() {
        repository.recorderManager.resumeRecording()
    }

    fun stopRecording(title: String, discard: Boolean = false) {
        val duration = repository.recordingState.value.durationSeconds
        val file = repository.recorderManager.stopRecording(discard)
        if (!discard && file != null && file.exists()) {
            viewModelScope.launch {
                repository.savePhoneRecording(file, title, duration)
                _uiMessage.value = "Voice recording saved successfully"
            }
        }
    }

    fun importAudioFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            val result = repository.importAudio(uri, fileName)
            if (result.isSuccess) {
                _uiMessage.value = "Imported '${result.getOrNull()?.title}'"
            } else {
                _uiMessage.value = "Import failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun createSampleVoiceNote(title: String = "Sample Meeting Audio", scenarioIndex: Int = 0) {
        viewModelScope.launch {
            val note = repository.createSampleVoiceNote(title, scenarioIndex)
            _uiMessage.value = "Created sample recording: '${note.title}'"
        }
    }

    fun createCustomSpeechVoiceNote(text: String, title: String) {
        viewModelScope.launch {
            val note = repository.createCustomSpeechVoiceNote(text, title)
            _uiMessage.value = "Created spoken voice note: '${note.title}'"
        }
    }

    fun wasLastRecordingSilent(): Boolean = repository.recorderManager.wasLastRecordingSilent()

    // --- Audio Playback Actions ---

    fun togglePlayback(recordingId: String, filePath: String) {
        repository.playerManager.play(recordingId, filePath)
    }

    fun pausePlayback() {
        repository.playerManager.pause()
    }

    fun seekPlayback(positionMs: Int) {
        repository.playerManager.seekTo(positionMs)
    }

    fun stopPlayback() {
        repository.playerManager.stop()
    }

    // --- ESP32 Actions ---

    fun connectToEsp32() {
        viewModelScope.launch {
            repository.connectToEsp32()
        }
    }

    fun disconnectEsp32() {
        repository.disconnectEsp32()
    }

    fun fetchEsp32Files() {
        viewModelScope.launch {
            _isFetchingEsp32Files.value = true
            val result = repository.fetchEsp32Files()
            _isFetchingEsp32Files.value = false
            if (result.isSuccess) {
                _esp32Files.value = result.getOrNull() ?: emptyList()
            } else {
                _uiMessage.value = "Failed to load files from ESP32: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun downloadEsp32Files(selectedFiles: List<Esp32FileInfo>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            var successCount = 0
            for (fileInfo in selectedFiles) {
                _downloadProgress.value = 0
                val result = repository.downloadEsp32File(fileInfo) { pct ->
                    _downloadProgress.value = pct
                }
                if (result.isSuccess) {
                    successCount++
                }
            }
            _downloadProgress.value = null
            _uiMessage.value = "Downloaded $successCount / ${selectedFiles.size} recording(s) from ESP32"
            onComplete()
        }
    }

    fun cancelTransfer() {
        repository.cancelActiveTransfer()
        _downloadProgress.value = null
        _uiMessage.value = "Transfer cancelled"
    }

    // --- Transcription Actions ---

    fun transcribeRecording(recordingId: String, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = repository.transcribeRecording(recordingId)
            if (result.isSuccess) {
                val transcript = result.getOrNull() ?: ""
                _uiMessage.value = "Transcription completed!"
                onComplete?.invoke(transcript)
            } else {
                _uiMessage.value = "Transcription error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    // --- Note Generation Actions ---

    fun generateNotes(
        recordingId: String?,
        transcript: String,
        mode: NoteMode,
        titleSuggestion: String? = null,
        onComplete: ((NoteEntity) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.generateNotes(
                recordingId = recordingId,
                transcript = transcript,
                mode = mode,
                titleSuggestion = titleSuggestion
            )
            if (result.isSuccess) {
                val note = result.getOrThrow()
                _uiMessage.value = "Notes created successfully!"
                _activeNoteForEditor.value = note
                onComplete?.invoke(note)
            } else {
                _uiMessage.value = "Note generation failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun regenerateNote(noteId: String, mode: NoteMode) {
        viewModelScope.launch {
            val result = repository.regenerateNote(noteId, mode)
            if (result.isSuccess) {
                _activeNoteForEditor.value = result.getOrThrow()
                _uiMessage.value = "Notes regenerated in ${mode.title} format"
            } else {
                _uiMessage.value = "Regeneration failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun createManualNote(title: String = "Untitled Note", initialContent: String = "") {
        viewModelScope.launch {
            val note = NoteEntity(
                title = title,
                contentMarkdown = initialContent,
                mode = NoteMode.CUSTOM.id
            )
            repository.saveNote(note)
            _activeNoteForEditor.value = note
        }
    }

    fun selectNoteForEditor(noteId: String) {
        viewModelScope.launch {
            val note = repository.getNoteById(noteId)
            _activeNoteForEditor.value = note
        }
    }

    fun updateNoteContent(id: String, title: String, content: String) {
        viewModelScope.launch {
            repository.updateNoteContent(id, title, content)
            _activeNoteForEditor.value = _activeNoteForEditor.value?.copy(
                title = title,
                contentMarkdown = content,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun toggleNotePinned(id: String) {
        viewModelScope.launch {
            repository.toggleNotePinned(id)
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            repository.deleteNote(id)
            if (_activeNoteForEditor.value?.id == id) {
                _activeNoteForEditor.value = null
            }
            _uiMessage.value = "Note deleted"
        }
    }

    fun deleteRecording(id: String) {
        viewModelScope.launch {
            repository.deleteRecording(id)
            _uiMessage.value = "Recording deleted"
        }
    }

    fun renameRecording(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateRecordingTitle(id, newTitle)
        }
    }

    // --- Settings Actions ---

    fun saveEsp32Settings(
        ssid: String,
        password: String,
        ip: String,
        port: Int,
        timeout: Int,
        autoReconnect: Boolean
    ) {
        viewModelScope.launch {
            repository.preferencesRepository.updateEsp32Config(
                ssid, password, ip, port, timeout, autoReconnect
            )
            _uiMessage.value = "ESP32 settings saved"
        }
    }

    fun saveGroqApiKey(apiKey: String) {
        viewModelScope.launch {
            repository.preferencesRepository.updateGroqApiKey(apiKey)
            _uiMessage.value = "Groq API Key saved securely"
        }
    }

    fun saveAiModelSettings(
        transcriptionModel: String,
        noteModel: String,
        defaultMode: String,
        speechLang: String
    ) {
        viewModelScope.launch {
            repository.preferencesRepository.updateAiModels(
                transcriptionModel, noteModel, defaultMode, speechLang
            )
            _uiMessage.value = "Model settings updated"
        }
    }

    fun toggleSimulationMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setSimulationMode(enabled)
            _uiMessage.value = if (enabled) "Simulation Mode enabled (Mock ESP32)" else "Hardware Mode enabled"
        }
    }

    fun setTheme(themeMode: String) {
        viewModelScope.launch {
            repository.preferencesRepository.setThemeMode(themeMode)
        }
    }

    fun setCustomInstructions(instructions: String) {
        viewModelScope.launch {
            repository.preferencesRepository.setCustomInstructions(instructions)
            _uiMessage.value = "Custom instructions updated"
        }
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        repository.playerManager.release()
    }
}
