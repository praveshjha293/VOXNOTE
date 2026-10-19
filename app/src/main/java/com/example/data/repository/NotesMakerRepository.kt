package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.audio.AudioPlayerManager
import com.example.data.audio.AudioRecorderManager
import com.example.data.audio.PlaybackState
import com.example.data.audio.RecordingState
import com.example.data.database.NoteDao
import com.example.data.database.NoteEntity
import com.example.data.database.NotesMakerDatabase
import com.example.data.database.RecordingDao
import com.example.data.database.RecordingEntity
import com.example.data.esp32.Esp32ApiClient
import com.example.data.esp32.Esp32MockService
import com.example.data.esp32.Esp32NetworkManager
import com.example.data.groq.GroqNoteGenerationService
import com.example.data.groq.GroqTranscriptionService
import com.example.data.model.DeviceConnectionState
import com.example.data.model.Esp32FileInfo
import com.example.data.model.NoteMode
import com.example.data.model.RecordingSource
import com.example.data.model.TranscriptionStatus
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class NotesMakerRepository(
    private val context: Context,
    private val database: NotesMakerDatabase = NotesMakerDatabase.getInstance(context),
    val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository(context)
) {
    private val recordingDao: RecordingDao = database.recordingDao()
    private val noteDao: NoteDao = database.noteDao()

    val recorderManager = AudioRecorderManager(context)
    val playerManager = AudioPlayerManager(context)

    val esp32ApiClient = Esp32ApiClient()
    val esp32NetworkManager = Esp32NetworkManager(context, esp32ApiClient)

    val groqTranscriptionService = GroqTranscriptionService()
    val groqNoteGenerationService = GroqNoteGenerationService()

    private val _activeOperation = MutableStateFlow<String?>(null)
    val activeOperation: StateFlow<String?> = _activeOperation.asStateFlow()

    val recordingsFlow: Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()
    val notesFlow: Flow<List<NoteEntity>> = noteDao.getAllNotes()
    val preferencesFlow: Flow<UserPreferences> = preferencesRepository.preferencesFlow

    val recordingState: StateFlow<RecordingState> = recorderManager.recordingState
    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val connectionState: StateFlow<DeviceConnectionState> = esp32NetworkManager.connectionState

    private val storageDir: File
        get() {
            val dir = File(context.filesDir, "audio_recordings")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    // --- Recordings Actions ---

    suspend fun getRecordingById(id: String): RecordingEntity? = withContext(Dispatchers.IO) {
        recordingDao.getRecordingById(id)
    }

    suspend fun savePhoneRecording(
        tempFile: File,
        title: String,
        durationSeconds: Int
    ): RecordingEntity = withContext(Dispatchers.IO) {
        val finalFile = File(storageDir, "phone_${System.currentTimeMillis()}.${tempFile.extension}")
        tempFile.copyTo(finalFile, overwrite = true)
        tempFile.delete()

        val entity = RecordingEntity(
            title = title.ifBlank { "Voice Note ${System.currentTimeMillis() % 10000}" },
            filePath = finalFile.absolutePath,
            source = RecordingSource.PHONE.name,
            durationSeconds = durationSeconds,
            fileSizeBytes = finalFile.length(),
            format = finalFile.extension.lowercase(),
            transcriptionStatus = TranscriptionStatus.IDLE.name
        )
        recordingDao.insertRecording(entity)
        entity
    }

    suspend fun importAudio(uri: Uri, fileName: String): Result<RecordingEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val extension = fileName.substringAfterLast('.', "m4a").lowercase()
            val targetFile = File(storageDir, "import_${System.currentTimeMillis()}.$extension")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Failed to read input audio stream")

            val title = fileName.substringBeforeLast('.')
            val entity = RecordingEntity(
                title = title.ifBlank { "Imported Audio" },
                filePath = targetFile.absolutePath,
                source = RecordingSource.IMPORTED.name,
                durationSeconds = estimateDurationSeconds(targetFile),
                fileSizeBytes = targetFile.length(),
                format = extension,
                transcriptionStatus = TranscriptionStatus.IDLE.name
            )
            recordingDao.insertRecording(entity)
            entity
        }
    }

    suspend fun createSampleVoiceNote(
        title: String = "Physics & Quantum Computing Lecture",
        scenarioIndex: Int = 0
    ): RecordingEntity = withContext(Dispatchers.IO) {
        val targetFile = File(storageDir, "voice_speech_${System.currentTimeMillis()}.wav")
        val result = com.example.data.audio.SpeechAudioGenerator.generateSpeechAudio(context, targetFile, scenarioIndex)
        val scenario = result.getOrNull()

        val entity = RecordingEntity(
            title = scenario?.title ?: title,
            filePath = targetFile.absolutePath,
            source = RecordingSource.PHONE.name,
            durationSeconds = scenario?.durationSeconds ?: 6,
            fileSizeBytes = targetFile.length(),
            format = "wav",
            transcriptionStatus = TranscriptionStatus.IDLE.name
        )
        recordingDao.insertRecording(entity)
        entity
    }

    suspend fun createCustomSpeechVoiceNote(
        text: String,
        title: String
    ): RecordingEntity = withContext(Dispatchers.IO) {
        val targetFile = File(storageDir, "voice_speech_${System.currentTimeMillis()}.wav")
        val result = com.example.data.audio.SpeechAudioGenerator.generateSpeechAudioFromText(context, targetFile, text, title)
        val scenario = result.getOrNull()

        val entity = RecordingEntity(
            title = scenario?.title ?: title.ifBlank { "Voice Note" },
            filePath = targetFile.absolutePath,
            source = RecordingSource.PHONE.name,
            durationSeconds = scenario?.durationSeconds ?: 6,
            fileSizeBytes = targetFile.length(),
            format = "wav",
            transcriptionStatus = TranscriptionStatus.IDLE.name
        )
        recordingDao.insertRecording(entity)
        entity
    }

    suspend fun updateRecordingTitle(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val rec = recordingDao.getRecordingById(id) ?: return@withContext
        recordingDao.updateRecording(rec.copy(title = newTitle.trim()))
    }

    suspend fun deleteRecording(id: String) = withContext(Dispatchers.IO) {
        val rec = recordingDao.getRecordingById(id) ?: return@withContext
        if (playerManager.playbackState.value.currentRecordingId == id) {
            playerManager.stop()
        }
        val file = File(rec.filePath)
        if (file.exists()) file.delete()
        recordingDao.deleteById(id)
    }

    suspend fun deleteMultipleRecordings(ids: List<String>) = withContext(Dispatchers.IO) {
        for (id in ids) {
            deleteRecording(id)
        }
    }

    // --- ESP32 Connection and Downloads ---

    suspend fun connectToEsp32() {
        val prefs = preferencesFlow.first()
        esp32NetworkManager.connectToEsp32(
            ssid = prefs.esp32Ssid,
            password = prefs.esp32Password,
            ip = prefs.esp32Ip,
            port = prefs.esp32Port,
            timeoutSec = prefs.esp32TimeoutSec,
            isSimulationMode = prefs.isSimulationMode
        )
    }

    fun disconnectEsp32() {
        esp32NetworkManager.disconnect()
    }

    suspend fun fetchEsp32Files(): Result<List<Esp32FileInfo>> = withContext(Dispatchers.IO) {
        val prefs = preferencesFlow.first()
        if (prefs.isSimulationMode) {
            Result.success(Esp32MockService.mockFiles)
        } else {
            val baseUrl = "http://${prefs.esp32Ip}:${prefs.esp32Port}"
            esp32ApiClient.listFiles(baseUrl)
        }
    }

    suspend fun downloadEsp32File(
        fileInfo: Esp32FileInfo,
        onProgress: (percent: Int) -> Unit
    ): Result<RecordingEntity> = withContext(Dispatchers.IO) {
        val prefs = preferencesFlow.first()
        _activeOperation.value = "Downloading ${fileInfo.name}..."

        runCatching {
            val targetFile = File(storageDir, "esp32_${fileInfo.id}_${fileInfo.name}")

            if (prefs.isSimulationMode) {
                Esp32MockService.simulateDownload(
                    context = context,
                    fileInfo = fileInfo,
                    targetFile = targetFile,
                    onProgress = { downloaded, total ->
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 50
                        onProgress(percent)
                        _activeOperation.value = "Downloading ${fileInfo.name} ($percent%)..."
                    }
                )
            } else {
                val baseUrl = "http://${prefs.esp32Ip}:${prefs.esp32Port}"
                esp32ApiClient.downloadFile(
                    baseUrl = baseUrl,
                    fileId = fileInfo.id,
                    targetFile = targetFile,
                    onProgress = { downloaded, total ->
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        onProgress(percent)
                        _activeOperation.value = "Downloading ${fileInfo.name} ($percent%)..."
                    }
                ).getOrThrow()
            }

            val entity = RecordingEntity(
                title = fileInfo.name.substringBeforeLast('.').replace('_', ' '),
                filePath = targetFile.absolutePath,
                source = RecordingSource.ESP32.name,
                durationSeconds = fileInfo.durationSeconds,
                fileSizeBytes = targetFile.length(),
                format = fileInfo.format,
                transcriptionStatus = TranscriptionStatus.IDLE.name
            )
            recordingDao.insertRecording(entity)
            _activeOperation.value = null
            entity
        }.also {
            _activeOperation.value = null
        }
    }

    fun cancelActiveTransfer() {
        esp32ApiClient.cancelActiveDownload()
        _activeOperation.value = null
    }

    // --- Groq Transcription ---

    suspend fun transcribeRecording(recordingId: String): Result<String> = withContext(Dispatchers.IO) {
        val recording = recordingDao.getRecordingById(recordingId)
            ?: return@withContext Result.failure(Exception("Recording not found"))

        val file = File(recording.filePath)
        if (!file.exists()) {
            return@withContext Result.failure(Exception("Audio file does not exist on disk."))
        }

        val prefs = preferencesFlow.first()
        val apiKey = prefs.groqApiKey

        // Update status to TRANSCRIBING
        recordingDao.updateRecording(
            recording.copy(
                transcriptionStatus = TranscriptionStatus.TRANSCRIBING.name,
                errorMessage = null
            )
        )
        _activeOperation.value = "Transcribing '${recording.title}' with Groq Whisper..."

        val result = groqTranscriptionService.transcribeAudio(
            apiKey = apiKey,
            audioFile = file,
            model = prefs.transcriptionModel,
            language = prefs.speechLanguage
        )

        _activeOperation.value = null

        if (result.isSuccess) {
            val transcript = result.getOrNull() ?: ""
            recordingDao.updateRecording(
                recording.copy(
                    transcriptionStatus = TranscriptionStatus.COMPLETED.name,
                    transcriptText = transcript,
                    transcriptLanguage = prefs.speechLanguage,
                    errorMessage = null
                )
            )
            Result.success(transcript)
        } else {
            val error = result.exceptionOrNull()?.message ?: "Transcription failed"
            recordingDao.updateRecording(
                recording.copy(
                    transcriptionStatus = TranscriptionStatus.FAILED.name,
                    errorMessage = error
                )
            )
            Result.failure(result.exceptionOrNull() ?: Exception(error))
        }
    }

    // --- Groq AI Note Generation ---

    suspend fun generateNotes(
        recordingId: String?,
        transcript: String,
        mode: NoteMode,
        titleSuggestion: String? = null
    ): Result<NoteEntity> = withContext(Dispatchers.IO) {
        val prefs = preferencesFlow.first()
        val apiKey = prefs.groqApiKey

        _activeOperation.value = "Generating ${mode.title} with Groq AI..."

        val result = groqNoteGenerationService.generateNotes(
            apiKey = apiKey,
            transcript = transcript,
            mode = mode,
            model = prefs.noteModel,
            customInstructions = prefs.customInstructions
        )

        _activeOperation.value = null

        if (result.isSuccess) {
            val markdown = result.getOrNull() ?: ""
            val noteTitle = titleSuggestion?.ifBlank { null }
                ?: extractTitleFromMarkdown(markdown)
                ?: "Notes on ${System.currentTimeMillis() % 1000}"

            val note = NoteEntity(
                recordingId = recordingId,
                title = noteTitle,
                contentMarkdown = markdown,
                mode = mode.id,
                isPinned = false
            )
            noteDao.insertNote(note)
            Result.success(note)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Note generation failed"))
        }
    }

    suspend fun regenerateNote(
        noteId: String,
        mode: NoteMode
    ): Result<NoteEntity> = withContext(Dispatchers.IO) {
        val currentNote = noteDao.getNoteById(noteId)
            ?: return@withContext Result.failure(Exception("Note not found"))

        // Find linked transcript
        val transcript = currentNote.recordingId?.let { recId ->
            recordingDao.getRecordingById(recId)?.transcriptText
        } ?: currentNote.contentMarkdown

        val prefs = preferencesFlow.first()
        _activeOperation.value = "Regenerating ${mode.title}..."

        val result = groqNoteGenerationService.generateNotes(
            apiKey = prefs.groqApiKey,
            transcript = transcript,
            mode = mode,
            model = prefs.noteModel,
            customInstructions = prefs.customInstructions
        )

        _activeOperation.value = null

        if (result.isSuccess) {
            val updated = currentNote.copy(
                contentMarkdown = result.getOrThrow(),
                mode = mode.id,
                updatedAt = System.currentTimeMillis()
            )
            noteDao.updateNote(updated)
            Result.success(updated)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Regeneration failed"))
        }
    }

    // --- Notes CRUD ---

    suspend fun getNoteById(id: String): NoteEntity? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(id)
    }

    suspend fun saveNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        noteDao.insertNote(note)
    }

    suspend fun updateNoteContent(id: String, title: String, contentMarkdown: String) = withContext(Dispatchers.IO) {
        val existing = noteDao.getNoteById(id) ?: return@withContext
        noteDao.updateNote(
            existing.copy(
                title = title.trim(),
                contentMarkdown = contentMarkdown,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleNotePinned(id: String) = withContext(Dispatchers.IO) {
        val existing = noteDao.getNoteById(id) ?: return@withContext
        noteDao.updateNote(existing.copy(isPinned = !existing.isPinned))
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        noteDao.deleteById(id)
    }

    // --- Helpers ---

    private fun extractTitleFromMarkdown(md: String): String? {
        val firstLine = md.lines().firstOrNull { it.isNotBlank() } ?: return null
        return firstLine.removePrefix("#").removePrefix("*").trim().take(60)
    }

    private fun estimateDurationSeconds(file: File): Int {
        val bytes = file.length()
        // Rough estimate for AAC/MP3 ~16KB/s (128kbps)
        return (bytes / 16000).toInt().coerceAtLeast(1)
    }
}
