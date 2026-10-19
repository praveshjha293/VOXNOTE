package com.example.data.model

enum class RecordingSource(val label: String) {
    PHONE("Phone Mic"),
    ESP32("ESP32 Device"),
    IMPORTED("Imported Audio")
}

enum class TranscriptionStatus(val label: String) {
    IDLE("Not Transcribed"),
    TRANSCRIBING("Transcribing..."),
    COMPLETED("Transcribed"),
    FAILED("Transcription Failed")
}

enum class NoteMode(val id: String, val title: String, val description: String) {
    DETAILED(
        id = "DETAILED",
        title = "Detailed Notes",
        description = "Comprehensive structured notes with explanations, key points, and terminology."
    ),
    REVISION(
        id = "REVISION",
        title = "Short Revision Notes",
        description = "Concise high-yield summary for quick review before tests or reviews."
    ),
    BULLETS(
        id = "BULLETS",
        title = "Bullet-Point Summary",
        description = "Scannable hierarchy of facts, takeaways, and outlines."
    ),
    EXAM(
        id = "EXAM",
        title = "Exam Preparation Notes",
        description = "Questions, flashcard style Q&A, definitions, formulas, and key concepts."
    ),
    MEETING(
        id = "MEETING",
        title = "Meeting Minutes & Actions",
        description = "Decisions reached, discussion highlights, and clear action items with owners."
    ),
    CUSTOM(
        id = "CUSTOM",
        title = "Custom Instructions",
        description = "Follow personalized prompts, formatting rules, or specialized templates."
    );

    companion object {
        fun fromId(id: String?): NoteMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DETAILED
        }
    }
}

data class Esp32FileInfo(
    val id: String,
    val name: String,
    val durationSeconds: Int,
    val sizeBytes: Long,
    val format: String,
    val createdAt: String
)

data class Esp32Status(
    val status: String,
    val device: String,
    val apSsid: String,
    val ipAddress: String,
    val freeHeap: Long,
    val totalFiles: Int,
    val transferMode: Boolean,
    val batteryPercent: Int? = null
)

data class DeviceConnectionState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isSimulationMode: Boolean = false,
    val deviceStatus: Esp32Status? = null,
    val currentSsid: String? = null,
    val message: String = "Disconnected from ESP32"
)
