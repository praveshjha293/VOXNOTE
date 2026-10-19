package com.example.data.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
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

data class PlaybackState(
    val currentRecordingId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0
)

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun play(recordingId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e("AudioPlayerManager", "Audio file not found: $filePath")
            return
        }

        // If toggling same recording
        if (_playbackState.value.currentRecordingId == recordingId && mediaPlayer != null) {
            if (_playbackState.value.isPlaying) {
                pause()
            } else {
                mediaPlayer?.start()
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
                startProgressTracker()
            }
            return
        }

        stop()

        try {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .build()

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.let { am ->
                val currentVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                if (currentVol < (maxVol * 0.4).toInt()) {
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (maxVol * 0.85).toInt(), 0)
                }
            }

            val player = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(context, Uri.fromFile(file))
                setVolume(1.0f, 1.0f)
                prepare()
                setOnCompletionListener {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0
                    )
                    progressJob?.cancel()
                }
                start()
            }

            mediaPlayer = player
            _playbackState.value = PlaybackState(
                currentRecordingId = recordingId,
                isPlaying = true,
                currentPositionMs = 0,
                durationMs = player.duration
            )

            startProgressTracker()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error playing audio file", e)
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
            progressJob?.cancel()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error pausing player", e)
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error seeking player", e)
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error stopping player", e)
        } finally {
            mediaPlayer = null
        }
        _playbackState.value = PlaybackState()
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _playbackState.value.isPlaying) {
                delay(200)
                try {
                    val pos = mediaPlayer?.currentPosition ?: 0
                    val dur = mediaPlayer?.duration ?: _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun release() {
        stop()
    }
}
