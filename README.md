# Notes Maker - Native Android App & ESP32 Audio Transcriber

**Notes Maker** is a production-quality native Android application built with **Kotlin**, **Jetpack Compose**, and **Material 3**. It connects directly to an **ESP32** hardware device over its local Wi-Fi Access Point (AP) or records audio using the phone's microphone, transcribes recordings using **Groq's Whisper** (`whisper-large-v3-turbo`), and synthesizes the transcripts into rich, structured academic and professional notes using **Groq-hosted LLMs** (e.g., `llama-3.3-70b-versatile`).

---

## 🌟 Key Features

1. **ESP32 Wi-Fi AP & HTTP Transfer Protocol**:
   - Single-click connection via Android `WifiNetworkSpecifier` and `bindProcessToNetwork` (ensures traffic routes to local ESP32 without requiring mobile data fallback).
   - Real-time HTTP REST endpoints: `/api/status`, `/api/files`, `/api/files/{id}`, `/api/files/{id}/download`, `/api/transfer/stop`, `/api/device/transfer-mode`.
   - Streaming downloads with live percentage progress, cancellation, retry, and duplicate detection.
2. **ESP32 Hardware 3-Second Button & Blue LED**:
   - Holding the physical button on the ESP32 for 3 seconds activates the Wi-Fi AP and lights up the solid **Blue LED**.
   - Blue LED blinks rapidly during active audio file transfers.
3. **In-App Audio Recording & Playback**:
   - Real native audio recording using Android's `MediaRecorder`.
   - Live amplitude waveform visualizer, pause/resume, timer, and file naming.
   - Built-in audio player with seekbar and duration tracking.
   - Audio file import for WAV, MP3, M4A, and AAC.
4. **Groq Speech-to-Text (Whisper)**:
   - Official Groq endpoint `https://api.groq.com/openai/v1/audio/transcriptions`.
   - Uses `whisper-large-v3-turbo` with multipart upload and file-size validation (25MB limit check).
   - Full rate-limit (HTTP 429) and timeout recovery.
5. **AI Note Generation (Groq LLM)**:
   - Official Groq endpoint `https://api.groq.com/openai/v1/chat/completions`.
   - 6 Note Modes:
     - **Detailed Notes**: Structured sections, definitions, formulas, action items.
     - **Short Revision Notes**: High-yield 2-minute exam/review summary.
     - **Bullet-Point Summary**: Clear nested hierarchical takeaways.
     - **Exam Preparation Notes**: Key topics, Q&A, definitions, pitfalls.
     - **Meeting Minutes & Action Items**: Decisions, discussion points, action items table.
     - **Custom Instructions**: Tailored user prompts and templates.
   - Hallucination prevention: strict factual fidelity, tags ambiguous segments as `[Ambiguous / Unclear in audio]`.
6. **Notes Editor & Export**:
   - Markdown formatting toolbar (H1, H2, H3, Bold, Italic, Bullets, Checklists, Formulas, Action Items).
   - Autosave debouncer.
   - View verbatim source transcript side-by-side.
   - Export to Markdown (`.md`), Plain Text (`.txt`), and Print / PDF export.
7. **Security & Privacy**:
   - Android Keystore AES-256-GCM hardware-backed cryptography for user-entered Groq API keys.
   - Network Security Config restricts cleartext HTTP exclusively to the local ESP32 IP (`192.168.4.1`).
   - Zero telemetry and zero background uploads.
8. **Built-in Simulation Mode**:
   - Fully testable without physical hardware: simulates ESP32 device status and generates playable WAV audio files.

---

## 🔌 ESP32 Hardware Wiring & Setup

The matching ESP32 firmware is located in `/esp32_firmware/notes_maker_esp32.ino`.

### Wiring Diagram:

| Component | ESP32 Pin | Connection Details |
|---|---|---|
| **Momentary Pushbutton** | **GPIO 4** | One side to GPIO 4, other side to **GND** (Internal `INPUT_PULLUP` enabled) |
| **Blue Status LED** | **GPIO 2** | Anode to GPIO 2 through a **220Ω - 330Ω resistor**, Cathode to **GND** (Or use on-board blue LED on ESP32 DevKit) |
| **I2S Microphone (e.g. INMP441)** | **GPIO 14, 15, 32** | SCK -> GPIO 14, WS -> GPIO 15, SD -> GPIO 32, VDD -> 3.3V, GND -> GND |
| **SD Card SPI (Optional)** | **GPIO 23, 19, 18, 5** | MOSI -> 23, MISO -> 19, SCK -> 18, CS -> 5 (or use internal SPIFFS flash) |

### Firmware Flashing Instructions:
1. Open the Arduino IDE.
2. Under **Board Manager**, install `esp32 by Espressif Systems`.
3. Select your board (e.g., `ESP32 Dev Module`).
4. Open `/esp32_firmware/notes_maker_esp32.ino`.
5. Connect your ESP32 via USB and click **Upload**.
6. When uploaded, hold the physical button for **3 seconds**. The Blue LED will turn **ON**, broadcasting the Wi-Fi AP **`NotesMaker-AP`** (Password: `notesmaker123`).

---

## 📱 Android App Setup & Groq Configuration

1. **Enter Groq API Key**:
   - Open **Notes Maker**.
   - Go to **Settings** -> **Groq AI & Keystore Security**.
   - Paste your Groq API key (starts with `gsk_...`) and tap **Save AI Settings**.
   - Alternatively, inject it via `.env` / AI Studio Secrets (`GROQ_API_KEY=gsk_...`).
2. **Connect to ESP32**:
   - Ensure the ESP32 Blue LED is glowing.
   - Tap **Connect ESP32** on the Home screen.
   - Android will prompt to connect to the `NotesMaker-AP` network. Tap **Connect**.
   - Once connected, tap **Browse Files** to download recordings!
3. **Record Audio on Phone**:
   - Tap **Record Audio** on the Home screen.
   - Grant microphone permission, record, pause/resume, view the live amplitude visualizer, and tap **Save**.
4. **Transcribe & Generate Notes**:
   - Go to **Recordings**, tap **Transcribe with Groq**.
   - Once transcribed, tap **Generate Notes**, select your desired format mode, and review your notes in the editor!

---

## 🧪 Testing

To run the local unit and Robolectric tests:
```bash
gradle :app:testDebugUnitTest
```
All database queries, note modes, JSON parsers, and Keystore encryption tests run automatically.
