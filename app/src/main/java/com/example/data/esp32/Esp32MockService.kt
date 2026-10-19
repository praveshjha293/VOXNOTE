package com.example.data.esp32

import android.content.Context
import com.example.data.model.Esp32FileInfo
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Esp32MockService {

    val mockFiles = listOf(
        Esp32FileInfo(
            id = "esp32_001",
            name = "Physics_Quantum_Mechanics_Lecture.wav",
            durationSeconds = 142,
            sizeBytes = 2504960,
            format = "wav",
            createdAt = "2026-10-18 09:30:00"
        ),
        Esp32FileInfo(
            id = "esp32_002",
            name = "Hardware_Sprint_Planning_Sync.wav",
            durationSeconds = 85,
            sizeBytes = 1500240,
            format = "wav",
            createdAt = "2026-10-18 11:15:00"
        ),
        Esp32FileInfo(
            id = "esp32_003",
            name = "Product_Ideas_Brainstorming.wav",
            durationSeconds = 64,
            sizeBytes = 1128000,
            format = "wav",
            createdAt = "2026-10-18 14:00:00"
        )
    )

    suspend fun simulateDownload(
        context: Context,
        fileInfo: Esp32FileInfo,
        targetFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = runCatching {
        val totalBytes = fileInfo.sizeBytes
        val chunks = 20
        val bytesPerChunk = totalBytes / chunks

        for (i in 1..chunks) {
            delay(75) // realistic streaming simulation
            onProgress(i * bytesPerChunk, totalBytes)
        }

        // Generate a playable WAV file with audible vocal speech cadence
        try {
            com.example.data.audio.SpeechAudioGenerator.generateVocalCadenceWav(
                targetFile,
                durationSeconds = fileInfo.durationSeconds.coerceAtMost(8).coerceAtLeast(3)
            )
        } catch (_: Exception) {
            generatePlayableWavFile(targetFile, durationSeconds = fileInfo.durationSeconds.coerceAtMost(5))
        }
        targetFile
    }

    fun generatePlayableWavFile(file: File, durationSeconds: Int = 3) {
        val sampleRate = 22050
        val numSamples = sampleRate * durationSeconds
        val dataSize = numSamples * 2 // 16-bit mono

        val output = FileOutputStream(file)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt subchunk
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // Subchunk1Size
        header.putShort(1.toShort()) // AudioFormat: 1 = PCM
        header.putShort(1.toShort()) // NumChannels: 1 = Mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2) // ByteRate
        header.putShort(2.toShort()) // BlockAlign
        header.putShort(16.toShort()) // BitsPerSample

        // data subchunk
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        output.write(header.array())

        // Write mild 440Hz sine wave tone
        val buffer = ByteArray(2048)
        var sampleIndex = 0
        var bytesWritten = 0

        while (bytesWritten < dataSize) {
            val toWrite = minOf(buffer.size, dataSize - bytesWritten)
            val byteBuf = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until toWrite / 2) {
                val time = sampleIndex.toDouble() / sampleRate
                val sampleVal = (Math.sin(2.0 * Math.PI * 440.0 * time) * 8000).toInt().toShort()
                byteBuf.putShort(sampleVal)
                sampleIndex++
            }
            output.write(buffer, 0, toWrite)
            bytesWritten += toWrite
        }

        output.flush()
        output.close()
    }
}
