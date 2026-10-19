package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.NoteEntity
import com.example.data.database.NotesMakerDatabase
import com.example.data.database.RecordingEntity
import com.example.data.model.NoteMode
import com.example.data.security.KeystoreHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: NotesMakerDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotesMakerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `verify app name resource is Notes Maker`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Notes Maker", appName)
    }

    @Test
    fun `verify database recording insertion and retrieval`() = runBlocking {
        val recording = RecordingEntity(
            id = "rec_test_1",
            title = "Physics Lecture",
            filePath = "/fake/path/audio.wav",
            source = "ESP32",
            durationSeconds = 120,
            fileSizeBytes = 204800,
            format = "wav"
        )
        db.recordingDao().insertRecording(recording)

        val retrieved = db.recordingDao().getRecordingById("rec_test_1")
        assertNotNull(retrieved)
        assertEquals("Physics Lecture", retrieved?.title)
        assertEquals("ESP32", retrieved?.source)

        val list = db.recordingDao().getAllRecordings().first()
        assertEquals(1, list.size)
    }

    @Test
    fun `verify database note insertion, update, and deletion`() = runBlocking {
        val note = NoteEntity(
            id = "note_test_1",
            recordingId = "rec_test_1",
            title = "Physics Quantum Notes",
            contentMarkdown = "# Quantum Mechanics\n- Wave-particle duality",
            mode = "DETAILED",
            isPinned = false
        )
        db.noteDao().insertNote(note)

        val retrieved = db.noteDao().getNoteById("note_test_1")
        assertNotNull(retrieved)
        assertEquals("Physics Quantum Notes", retrieved?.title)

        // Update note
        db.noteDao().updateNote(retrieved!!.copy(isPinned = true, title = "Updated Title"))
        val updated = db.noteDao().getNoteById("note_test_1")
        assertTrue(updated!!.isPinned)
        assertEquals("Updated Title", updated.title)

        // Delete note
        db.noteDao().deleteById("note_test_1")
        val deleted = db.noteDao().getNoteById("note_test_1")
        assertEquals(null, deleted)
    }

    @Test
    fun `verify note modes parsing and fallback`() {
        assertEquals(NoteMode.DETAILED, NoteMode.fromId("DETAILED"))
        assertEquals(NoteMode.REVISION, NoteMode.fromId("revision"))
        assertEquals(NoteMode.EXAM, NoteMode.fromId("EXAM"))
        assertEquals(NoteMode.MEETING, NoteMode.fromId("MEETING"))
        assertEquals(NoteMode.BULLETS, NoteMode.fromId("BULLETS"))
        assertEquals(NoteMode.CUSTOM, NoteMode.fromId("CUSTOM"))
        assertEquals(NoteMode.DETAILED, NoteMode.fromId("UNKNOWN_MODE"))
    }

    @Test
    fun `verify ESP32 API status parsing`() {
        val jsonString = """
            {
              "status": "ready",
              "device": "ESP32-NotesMaker",
              "ap_ssid": "NotesMaker-AP",
              "ip": "192.168.4.1",
              "free_heap": 172900,
              "total_files": 4,
              "transfer_mode": true,
              "battery_percent": 95
            }
        """.trimIndent()

        val json = JSONObject(jsonString)
        assertEquals("ready", json.getString("status"))
        assertEquals("ESP32-NotesMaker", json.getString("device"))
        assertEquals("192.168.4.1", json.getString("ip"))
        assertEquals(4, json.getInt("total_files"))
        assertTrue(json.getBoolean("transfer_mode"))
    }

    @Test
    fun `verify speech audio generator creates valid playable WAV file`() {
        val testFile = java.io.File(context.cacheDir, "test_speech.wav")
        com.example.data.audio.SpeechAudioGenerator.generateVocalCadenceWav(testFile, durationSeconds = 2)
        assertTrue(testFile.exists())
        assertTrue(testFile.length() > 1000)

        // Verify RIFF header
        val bytes = testFile.readBytes()
        assertEquals('R'.code.toByte(), bytes[0])
        assertEquals('I'.code.toByte(), bytes[1])
        assertEquals('F'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
        testFile.delete()
    }
}
