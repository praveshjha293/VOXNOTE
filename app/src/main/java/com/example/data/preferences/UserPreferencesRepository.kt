package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.security.KeystoreHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notes_maker_prefs")

data class UserPreferences(
    val esp32Ssid: String,
    val esp32Password: String,
    val esp32Ip: String,
    val esp32Port: Int,
    val esp32TimeoutSec: Int,
    val esp32AutoReconnect: Boolean,
    val isSimulationMode: Boolean,
    val groqApiKey: String,
    val transcriptionModel: String,
    val noteModel: String,
    val defaultNoteMode: String,
    val speechLanguage: String,
    val themeMode: String,
    val customInstructions: String
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val ESP32_SSID = stringPreferencesKey("esp32_ssid")
        val ESP32_PASSWORD = stringPreferencesKey("esp32_password")
        val ESP32_IP = stringPreferencesKey("esp32_ip")
        val ESP32_PORT = intPreferencesKey("esp32_port")
        val ESP32_TIMEOUT = intPreferencesKey("esp32_timeout")
        val ESP32_AUTO_RECONNECT = booleanPreferencesKey("esp32_auto_reconnect")
        val SIMULATION_MODE = booleanPreferencesKey("simulation_mode")
        val GROQ_API_KEY_ENCRYPTED = stringPreferencesKey("groq_api_key_encrypted")
        val TRANSCRIPTION_MODEL = stringPreferencesKey("transcription_model")
        val NOTE_MODEL = stringPreferencesKey("note_model")
        val DEFAULT_NOTE_MODE = stringPreferencesKey("default_note_mode")
        val SPEECH_LANGUAGE = stringPreferencesKey("speech_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CUSTOM_INSTRUCTIONS = stringPreferencesKey("custom_instructions")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val encryptedKey = prefs[PreferencesKeys.GROQ_API_KEY_ENCRYPTED] ?: ""
        val decryptedKey = if (encryptedKey.isNotBlank()) KeystoreHelper.decrypt(encryptedKey) else ""
        val resolvedKey = if (decryptedKey.isNotBlank()) {
            decryptedKey
        } else {
            try {
                val key = com.example.BuildConfig.GROQ_API_KEY
                if (!key.isNullOrBlank() && !key.startsWith("gsk_your_groq") && key != "YOUR_GROQ_API_KEY_HERE") key else ""
            } catch (_: Exception) {
                ""
            }
        }

        UserPreferences(
            esp32Ssid = prefs[PreferencesKeys.ESP32_SSID] ?: "NotesMaker-AP",
            esp32Password = prefs[PreferencesKeys.ESP32_PASSWORD] ?: "notesmaker123",
            esp32Ip = prefs[PreferencesKeys.ESP32_IP] ?: "192.168.4.1",
            esp32Port = prefs[PreferencesKeys.ESP32_PORT] ?: 80,
            esp32TimeoutSec = prefs[PreferencesKeys.ESP32_TIMEOUT] ?: 15,
            esp32AutoReconnect = prefs[PreferencesKeys.ESP32_AUTO_RECONNECT] ?: false,
            isSimulationMode = prefs[PreferencesKeys.SIMULATION_MODE] ?: false,
            groqApiKey = resolvedKey,
            transcriptionModel = prefs[PreferencesKeys.TRANSCRIPTION_MODEL] ?: "whisper-large-v3-turbo",
            noteModel = prefs[PreferencesKeys.NOTE_MODEL] ?: "llama-3.3-70b-versatile",
            defaultNoteMode = prefs[PreferencesKeys.DEFAULT_NOTE_MODE] ?: "DETAILED",
            speechLanguage = prefs[PreferencesKeys.SPEECH_LANGUAGE] ?: "auto",
            themeMode = prefs[PreferencesKeys.THEME_MODE] ?: "SYSTEM",
            customInstructions = prefs[PreferencesKeys.CUSTOM_INSTRUCTIONS] ?: ""
        )
    }

    suspend fun updateEsp32Config(
        ssid: String,
        password: String,
        ip: String,
        port: Int,
        timeoutSec: Int,
        autoReconnect: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ESP32_SSID] = ssid.trim()
            prefs[PreferencesKeys.ESP32_PASSWORD] = password
            prefs[PreferencesKeys.ESP32_IP] = ip.trim()
            prefs[PreferencesKeys.ESP32_PORT] = port
            prefs[PreferencesKeys.ESP32_TIMEOUT] = timeoutSec
            prefs[PreferencesKeys.ESP32_AUTO_RECONNECT] = autoReconnect
        }
    }

    suspend fun updateGroqApiKey(apiKey: String) {
        val encrypted = if (apiKey.isNotBlank()) KeystoreHelper.encrypt(apiKey.trim()) else ""
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.GROQ_API_KEY_ENCRYPTED] = encrypted
        }
    }

    suspend fun updateAiModels(
        transcriptionModel: String,
        noteModel: String,
        defaultNoteMode: String,
        speechLanguage: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.TRANSCRIPTION_MODEL] = transcriptionModel
            prefs[PreferencesKeys.NOTE_MODEL] = noteModel
            prefs[PreferencesKeys.DEFAULT_NOTE_MODE] = defaultNoteMode
            prefs[PreferencesKeys.SPEECH_LANGUAGE] = speechLanguage
        }
    }

    suspend fun setSimulationMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SIMULATION_MODE] = enabled
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME_MODE] = mode
        }
    }

    suspend fun setCustomInstructions(instructions: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.CUSTOM_INSTRUCTIONS] = instructions
        }
    }
}
