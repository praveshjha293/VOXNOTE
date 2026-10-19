package com.example.data.groq

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class GroqTranscriptionService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun transcribeAudio(
        apiKey: String,
        audioFile: File,
        model: String = "whisper-large-v3-turbo",
        language: String? = null
    ): Result<String> = runCatching {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException(
                "Groq API Key is not configured. Please open Settings and enter your Groq API key."
            )
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            throw IllegalArgumentException("Audio file is missing or empty.")
        }

        // Groq audio upload limit is 25MB
        val maxSizeBytes = 25 * 1024 * 1024
        if (audioFile.length() > maxSizeBytes) {
            val sizeMb = String.format("%.1f", audioFile.length() / (1024.0 * 1024.0))
            throw IllegalArgumentException(
                "Audio file size ($sizeMb MB) exceeds Groq's 25 MB limit. Please record a shorter audio segment."
            )
        }

        val mimeType = when (audioFile.extension.lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            else -> "audio/*"
        }

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody(mimeType.toMediaTypeOrNull())
            )
            .addFormDataPart("model", model.ifBlank { "whisper-large-v3-turbo" })
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0.0")

        if (!language.isNullOrBlank() && language != "auto") {
            requestBodyBuilder.addFormDataPart("language", language.trim().lowercase())
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBodyBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: response.message
                } catch (_: Exception) {
                    response.message
                }
                when (response.code) {
                    401 -> throw Exception("Invalid Groq API key. Check your key in Settings.")
                    429 -> throw Exception("Groq rate limit reached. Please wait a moment before trying again.")
                    else -> throw Exception("Groq transcription error (${response.code}): $errorMsg")
                }
            }

            val json = JSONObject(responseBody)
            val text = json.optString("text", "").trim()
            if (text.isEmpty()) {
                throw Exception("No speech detected in audio file.")
            }
            text
        }
    }
}
