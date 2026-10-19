package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val filePath: String,
    val source: String, // "PHONE", "ESP32", "IMPORTED"
    val durationSeconds: Int,
    val fileSizeBytes: Long,
    val format: String, // "wav", "mp3", "m4a", "aac"
    val createdAt: Long = System.currentTimeMillis(),
    val transcriptionStatus: String = "IDLE", // "IDLE", "TRANSCRIBING", "COMPLETED", "FAILED"
    val transcriptText: String? = null,
    val transcriptLanguage: String? = null,
    val errorMessage: String? = null
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val recordingId: String? = null,
    val title: String,
    val contentMarkdown: String,
    val mode: String = "DETAILED", // Matches NoteMode.id
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
