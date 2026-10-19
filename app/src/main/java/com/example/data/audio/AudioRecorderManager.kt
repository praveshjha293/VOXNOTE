package com.example.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val durationSeconds: Int = 0,
    val currentAmplitude: Int = 0,
    val currentFilePath: String? = null,
    val hasDetectedSound: Boolean = false,
    val maxAmplitudeRecorded: Int = 0,
    val error: String? = null
)

class AudioRecorderManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var recordingStartTime = 0L
    private var accumulatedPauseDurationMs = 0L
    private var lastPauseStartTime = 0L
    private var soundEverDetected = false
    private var maxRecordedAmp = 0

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    fun wasLastRecordingSilent(): Boolean = !soundEverDetected || maxRecordedAmp < 150

    private val recordingsDir: File
        get() {
            val dir = File(context.filesDir, "audio_recordings")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun startRecording(): Result<File> {
        return try {
            stopRecordingInternal(discard = true)

            val file = File(recordingsDir, "rec_${System.currentTimeMillis()}.m4a")
            currentFile = file

            var recorder: MediaRecorder? = null
            var lastException: Exception? = null

            // Try first with MIC, then fallback to DEFAULT if emulator audio HAL requires DEFAULT
            val audioSources = listOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            )

            for (source in audioSources) {
                try {
                    val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }

                    r.apply {
                        setAudioSource(source)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(128000)
                        setAudioSamplingRate(44100)
                        setOutputFile(file.absolutePath)
                        prepare()
                        start()
                    }
                    recorder = r
                    break
                } catch (e: Exception) {
                    lastException = e
                    try { recorder?.release() } catch (_: Exception) {}
                    recorder = null
                }
            }

            if (recorder == null) {
                throw lastException ?: Exception("Could not initialize audio recorder")
            }

            mediaRecorder = recorder
            recordingStartTime = System.currentTimeMillis()
            accumulatedPauseDurationMs = 0L
            lastPauseStartTime = 0L
            soundEverDetected = false
            maxRecordedAmp = 0

            _recordingState.value = RecordingState(
                isRecording = true,
                isPaused = false,
                durationSeconds = 0,
                currentAmplitude = 500,
                currentFilePath = file.absolutePath,
                hasDetectedSound = false,
                maxAmplitudeRecorded = 0,
                error = null
            )

            startTicker()
            Result.success(file)
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error starting recording", e)
            cleanup()
            val userMsg = when {
                e is SecurityException -> "Microphone permission required to record audio."
                e.message?.contains("start failed") == true -> "Microphone busy or unavailable on this device."
                else -> e.message ?: "Failed to start audio recording"
            }
            _recordingState.value = RecordingState(error = userMsg)
            Result.failure(Exception(userMsg))
        }
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                lastPauseStartTime = System.currentTimeMillis()
                _recordingState.value = _recordingState.value.copy(isPaused = true)
            } catch (e: Exception) {
                Log.e("AudioRecorderManager", "Error pausing recording", e)
            }
        }
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                if (lastPauseStartTime > 0L) {
                    accumulatedPauseDurationMs += (System.currentTimeMillis() - lastPauseStartTime)
                    lastPauseStartTime = 0L
                }
                _recordingState.value = _recordingState.value.copy(isPaused = false)
            } catch (e: Exception) {
                Log.e("AudioRecorderManager", "Error resuming recording", e)
            }
        }
    }

    fun stopRecording(discard: Boolean = false): File? {
        val file = currentFile
        stopRecordingInternal(discard)
        return if (discard) null else file
    }

    private fun stopRecordingInternal(discard: Boolean) {
        tickerJob?.cancel()
        tickerJob = null

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error releasing recorder", e)
        } finally {
            mediaRecorder = null
        }

        if (discard) {
            currentFile?.delete()
            currentFile = null
        }

        _recordingState.value = RecordingState()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var waveCounter = 0
            while (isActive && _recordingState.value.isRecording) {
                delay(100)
                if (!_recordingState.value.isPaused) {
                    val rawAmp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (_: Exception) {
                        0
                    }

                    if (rawAmp > 200) {
                        soundEverDetected = true
                        maxRecordedAmp = maxOf(maxRecordedAmp, rawAmp)
                    }

                    // Provide slight animated pulse if hardware mic amplitude is 0 (common in emulators)
                    waveCounter = (waveCounter + 1) % 20
                    val syntheticAmp = if (rawAmp == 0) {
                        (4000 + (Math.sin(waveCounter.toDouble() * 0.3) * 3500).toInt())
                    } else rawAmp

                    val now = System.currentTimeMillis()
                    val elapsedMs = now - recordingStartTime - accumulatedPauseDurationMs
                    val elapsedSec = (elapsedMs / 1000).toInt().coerceAtLeast(0)

                    _recordingState.value = _recordingState.value.copy(
                        currentAmplitude = syntheticAmp,
                        durationSeconds = elapsedSec,
                        hasDetectedSound = soundEverDetected,
                        maxAmplitudeRecorded = maxRecordedAmp
                    )
                }
            }
        }
    }

    private fun cleanup() {
        tickerJob?.cancel()
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        currentFile?.delete()
        currentFile = null
        _recordingState.value = RecordingState()
    }
}
