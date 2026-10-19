package com.example.data.esp32

import android.util.Log
import com.example.data.model.Esp32FileInfo
import com.example.data.model.Esp32Status
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class Esp32ApiClient(timeoutSeconds: Int = 15) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var activeCall: okhttp3.Call? = null
    private val isCancelled = AtomicBoolean(false)

    suspend fun getStatus(baseUrl: String): Result<Esp32Status> = runCatching {
        val url = cleanUrl(baseUrl) + "/api/status"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string() ?: throw Exception("Empty response from ESP32")
            val json = JSONObject(body)
            Esp32Status(
                status = json.optString("status", "ready"),
                device = json.optString("device", "ESP32-NotesMaker"),
                apSsid = json.optString("ap_ssid", "NotesMaker-AP"),
                ipAddress = json.optString("ip", "192.168.4.1"),
                freeHeap = json.optLong("free_heap", 0),
                totalFiles = json.optInt("total_files", 0),
                transferMode = json.optBoolean("transfer_mode", false),
                batteryPercent = if (json.has("battery_percent")) json.getInt("battery_percent") else null
            )
        }
    }

    suspend fun listFiles(baseUrl: String): Result<List<Esp32FileInfo>> = runCatching {
        val url = cleanUrl(baseUrl) + "/api/files"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string() ?: throw Exception("Empty response from ESP32")
            val jsonArray = if (body.trim().startsWith("[")) {
                JSONArray(body)
            } else {
                JSONObject(body).optJSONArray("files") ?: JSONArray()
            }

            val list = mutableListOf<Esp32FileInfo>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                list.add(
                    Esp32FileInfo(
                        id = item.optString("id", "file_$i"),
                        name = item.optString("name", "recording_$i.wav"),
                        durationSeconds = item.optInt("duration_seconds", 0),
                        sizeBytes = item.optLong("size_bytes", 0),
                        format = item.optString("format", "wav"),
                        createdAt = item.optString("created_at", "")
                    )
                )
            }
            list
        }
    }

    suspend fun getFileMetadata(baseUrl: String, fileId: String): Result<Esp32FileInfo> = runCatching {
        val url = cleanUrl(baseUrl) + "/api/files/$fileId"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string() ?: throw Exception("Empty response from ESP32")
            val item = JSONObject(body)
            Esp32FileInfo(
                id = item.optString("id", fileId),
                name = item.optString("name", "recording.wav"),
                durationSeconds = item.optInt("duration_seconds", 0),
                sizeBytes = item.optLong("size_bytes", 0),
                format = item.optString("format", "wav"),
                createdAt = item.optString("created_at", "")
            )
        }
    }

    suspend fun downloadFile(
        baseUrl: String,
        fileId: String,
        targetFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = runCatching {
        isCancelled.set(false)
        val url = cleanUrl(baseUrl) + "/api/files/$fileId/download"
        val request = Request.Builder().url(url).get().build()

        val call = client.newCall(request)
        activeCall = call

        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download failed with HTTP ${response.code}: ${response.message}")
            }

            val responseBody = response.body ?: throw Exception("Response body is empty")
            val totalBytes = responseBody.contentLength()

            val tempFile = File(targetFile.parentFile, "${targetFile.name}.download")
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                inputStream = responseBody.byteStream()
                outputStream = FileOutputStream(tempFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled.get()) {
                        throw Exception("Download cancelled by user")
                    }
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    onProgress(downloadedBytes, totalBytes)
                }

                outputStream.flush()
                // Move temp to target
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                targetFile
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
                try { outputStream?.close() } catch (_: Exception) {}
                activeCall = null
            }
        }
    }

    fun cancelActiveDownload() {
        isCancelled.set(true)
        try {
            activeCall?.cancel()
        } catch (e: Exception) {
            Log.e("Esp32ApiClient", "Error cancelling call", e)
        }
    }

    suspend fun stopTransfer(baseUrl: String): Result<Boolean> = runCatching {
        val url = cleanUrl(baseUrl) + "/api/transfer/stop"
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { it.isSuccessful }
    }

    suspend fun activateTransferMode(baseUrl: String): Result<Boolean> = runCatching {
        val url = cleanUrl(baseUrl) + "/api/device/transfer-mode"
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { it.isSuccessful }
    }

    private fun cleanUrl(baseUrl: String): String {
        var clean = baseUrl.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return clean.trimEnd('/')
    }
}
