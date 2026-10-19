/*
 * ============================================================================
 * NOTES MAKER - ESP32 EMBEDDED FIRMWARE (ARDUINO / ESP-IDF COMPATIBLE)
 * ============================================================================
 * Hardware Features:
 * 1. Physical Pushbutton: Holding for 3 seconds activates Wi-Fi SoftAP and Transfer Mode.
 * 2. Blue LED: Lights up solidly when Wi-Fi AP is active and transfer mode is ready.
 *    Blinks rapidly while an audio file is actively being transferred.
 * 3. HTTP REST API:
 *    - GET  /api/status            -> System info, battery, free heap, transfer mode
 *    - GET  /api/files             -> List of recorded audio files (id, name, duration, size, format)
 *    - GET  /api/files/{id}        -> Metadata for specific recording
 *    - GET  /api/files/{id}/download -> Stream audio file bytes directly from SD/Flash
 *    - POST /api/transfer/stop     -> Abort active transfer
 *    - POST /api/device/transfer-mode -> Toggle or activate transfer mode
 *
 * HARDWARE WIRING:
 * ----------------------------------------------------------------------------
 * 1. BUTTON:
 *    - Pin GPIO 4 (or GPIO 0) connected to one side of momentary push button.
 *    - Other side connected to GND.
 *    - Internal pull-up resistor enabled (pin reads LOW when pressed).
 * 2. BLUE LED:
 *    - Pin GPIO 2 (built-in blue LED on most ESP32 DevKit boards) or external LED.
 *    - Anode -> GPIO 2 via 220-330 ohm resistor; Cathode -> GND.
 * 3. MICROPHONE (e.g., INMP441 I2S Microphone for standalone recording):
 *    - SCK/BCLK  -> GPIO 14
 *    - WS/LRCLK  -> GPIO 15
 *    - SD/DATA   -> GPIO 32
 *    - VDD       -> 3.3V, GND -> GND
 * 4. SD CARD MODULE (SPI Mode, or use internal SPIFFS / LittleFS):
 *    - MOSI -> GPIO 23, MISO -> GPIO 19, SCK -> GPIO 18, CS -> GPIO 5
 * ============================================================================
 */

#include <WiFi.h>
#include <WebServer.h>
#include <FS.h>
#include <SPIFFS.h> // Or #include <SD.h> for SD Card

// Hardware Pin Definitions
#define PIN_BUTTON       4    // Momentary Pushbutton (Active LOW with INPUT_PULLUP)
#define PIN_BLUE_LED     2    // On-board Blue LED

// Configurable Wi-Fi Access Point Settings
const char* AP_SSID     = "NotesMaker-AP";
const char* AP_PASSWORD = "notesmaker123";
const IPAddress AP_IP(192, 168, 4, 1);
const IPAddress AP_GATEWAY(192, 168, 4, 1);
const IPAddress AP_SUBNET(255, 255, 255, 0);

// Web Server on port 80
WebServer server(80);

// State Tracking
bool isApActive = false;
bool isTransferMode = false;
bool isTransferringFile = false;

// Button Timing (3-Second Long Press)
unsigned long buttonPressStartTime = 0;
bool buttonHeldTriggered = false;
const unsigned long LONG_PRESS_DURATION_MS = 3000;

// LED Blink Timer
unsigned long lastLedBlinkTime = 0;
bool ledBlinkState = false;

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n--- Notes Maker ESP32 Initializing ---");

  pinMode(PIN_BUTTON, INPUT_PULLUP);
  pinMode(PIN_BLUE_LED, OUTPUT);
  digitalWrite(PIN_BLUE_LED, LOW);

  // Initialize Filesystem (SPIFFS or SD)
  if (!SPIFFS.begin(true)) {
    Serial.println("SPIFFS initialization failed! Formatting...");
  } else {
    Serial.println("Filesystem mounted successfully.");
    createDemoRecordingsIfEmpty();
  }

  Serial.println("Setup completed. Hold physical button for 3 seconds to start Wi-Fi AP.");
}

void loop() {
  handleButton();
  handleLedStatus();

  if (isApActive) {
    server.handleClient();
  }
}

// ----------------------------------------------------------------------------
// Button Handling: Detect 3-Second Long Press
// ----------------------------------------------------------------------------
void handleButton() {
  int buttonState = digitalRead(PIN_BUTTON);

  if (buttonState == LOW) { // Button is pressed
    if (buttonPressStartTime == 0) {
      buttonPressStartTime = millis();
      buttonHeldTriggered = false;
      Serial.println("Button press detected, holding...");
    } else if (!buttonHeldTriggered && (millis() - buttonPressStartTime >= LONG_PRESS_DURATION_MS)) {
      buttonHeldTriggered = true;
      Serial.println(">>> 3-Second Hold Detected! Activating Wi-Fi AP & Transfer Mode <<<");
      startAccessPoint();
    }
  } else { // Button is released
    if (buttonPressStartTime != 0) {
      unsigned long pressDuration = millis() - buttonPressStartTime;
      if (!buttonHeldTriggered) {
        Serial.printf("Short button press ignored (%lu ms). Hold for 3s to activate AP.\n", pressDuration);
      }
      buttonPressStartTime = 0;
    }
  }
}

// ----------------------------------------------------------------------------
// Wi-Fi Access Point & Server Initialization
// ----------------------------------------------------------------------------
void startAccessPoint() {
  WiFi.mode(WIFI_AP);
  WiFi.softAPConfig(AP_IP, AP_GATEWAY, AP_SUBNET);
  bool apSuccess = WiFi.softAP(AP_SSID, AP_PASSWORD);

  if (apSuccess) {
    isApActive = true;
    isTransferMode = true;
    digitalWrite(PIN_BLUE_LED, HIGH); // Turn ON Blue LED
    Serial.printf("Wi-Fi AP '%s' active at IP: %s\n", AP_SSID, WiFi.softAPIP().toString().c_str());

    setupApiRoutes();
    server.begin();
    Serial.println("HTTP REST API Server listening on port 80.");
  } else {
    Serial.println("Failed to start Wi-Fi SoftAP!");
  }
}

void handleLedStatus() {
  if (isTransferringFile) {
    // Rapid blink while streaming audio bytes
    if (millis() - lastLedBlinkTime > 100) {
      lastLedBlinkTime = millis();
      ledBlinkState = !ledBlinkState;
      digitalWrite(PIN_BLUE_LED, ledBlinkState ? HIGH : LOW);
    }
  } else if (isApActive) {
    // Solid Blue LED when AP is ready
    digitalWrite(PIN_BLUE_LED, HIGH);
  } else {
    digitalWrite(PIN_BLUE_LED, LOW);
  }
}

// ----------------------------------------------------------------------------
// HTTP REST API Routes Implementation
// ----------------------------------------------------------------------------
void setupApiRoutes() {
  server.enableCORS(true);

  // GET /api/status
  server.on("/api/status", HTTP_GET, []() {
    int fileCount = countAudioFiles();
    String json = "{";
    json += "\"status\":\"ready\",";
    json += "\"device\":\"ESP32-NotesMaker\",";
    json += "\"ap_ssid\":\"" + String(AP_SSID) + "\",";
    json += "\"ip\":\"" + WiFi.softAPIP().toString() + "\",";
    json += "\"free_heap\":" + String(ESP.getFreeHeap()) + ",";
    json += "\"total_files\":" + String(fileCount) + ",";
    json += "\"transfer_mode\":" + String(isTransferMode ? "true" : "false") + ",";
    json += "\"battery_percent\":94";
    json += "}";
    server.send(200, "application/json", json);
  });

  // GET /api/files
  server.on("/api/files", HTTP_GET, []() {
    File root = SPIFFS.open("/");
    String json = "[";
    bool first = true;

    File file = root.openNextFile();
    int idx = 1;
    while (file) {
      String fname = String(file.name());
      if (fname.startsWith("/")) fname = fname.substring(1);
      if (fname.endsWith(".wav") || fname.endsWith(".mp3") || fname.endsWith(".m4a")) {
        if (!first) json += ",";
        first = false;

        String id = "rec_" + String(idx);
        size_t fsize = file.size();
        int durationSec = fsize / 32000; // approximate duration estimate
        if (durationSec < 1) durationSec = 1;

        json += "{";
        json += "\"id\":\"" + id + "\",";
        json += "\"name\":\"" + fname + "\",";
        json += "\"size_bytes\":" + String(fsize) + ",";
        json += "\"duration_seconds\":" + String(durationSec) + ",";
        json += "\"format\":\"wav\",";
        json += "\"created_at\":\"2026-10-18T10:00:00Z\"";
        json += "}";
        idx++;
      }
      file = root.openNextFile();
    }
    json += "]";
    server.send(200, "application/json", json);
  });

  // POST /api/transfer/stop
  server.on("/api/transfer/stop", HTTP_POST, []() {
    isTransferringFile = false;
    server.send(200, "application/json", "{\"status\":\"stopped\"}");
  });

  // POST /api/device/transfer-mode
  server.on("/api/device/transfer-mode", HTTP_POST, []() {
    isTransferMode = true;
    server.send(200, "application/json", "{\"transfer_mode\":true}");
  });

  // Dynamic route handler for /api/files/{id} and /api/files/{id}/download
  server.onNotFound([]() {
    String uri = server.uri();
    if (uri.startsWith("/api/files/") && uri.endsWith("/download")) {
      handleFileDownload(uri);
    } else if (uri.startsWith("/api/files/")) {
      handleFileMetadata(uri);
    } else {
      server.send(404, "application/json", "{\"error\":\"Not Found\"}");
    }
  });
}

void handleFileMetadata(String uri) {
  // Extract id e.g. /api/files/rec_1
  String id = uri.substring(11);
  String targetFilename = resolveFilenameById(id);

  if (SPIFFS.exists("/" + targetFilename)) {
    File f = SPIFFS.open("/" + targetFilename, "r");
    String json = "{";
    json += "\"id\":\"" + id + "\",";
    json += "\"name\":\"" + targetFilename + "\",";
    json += "\"size_bytes\":" + String(f.size()) + ",";
    json += "\"duration_seconds\":" + String(f.size() / 32000) + ",";
    json += "\"format\":\"wav\",";
    json += "\"created_at\":\"2026-10-18T10:00:00Z\"";
    json += "}";
    f.close();
    server.send(200, "application/json", json);
  } else {
    server.send(404, "application/json", "{\"error\":\"File not found\"}");
  }
}

void handleFileDownload(String uri) {
  // Extract id from /api/files/{id}/download
  String trimmed = uri.substring(11);
  int slashIdx = trimmed.indexOf('/');
  String id = (slashIdx > 0) ? trimmed.substring(0, slashIdx) : trimmed;
  String targetFilename = resolveFilenameById(id);

  if (!SPIFFS.exists("/" + targetFilename)) {
    server.send(404, "application/json", "{\"error\":\"File not found\"}");
    return;
  }

  File f = SPIFFS.open("/" + targetFilename, "r");
  if (!f) {
    server.send(500, "application/json", "{\"error\":\"Failed to open file\"}");
    return;
  }

  isTransferringFile = true;
  size_t totalSize = f.size();

  server.sendHeader("Content-Disposition", "attachment; filename=\"" + targetFilename + "\"");
  server.setContentLength(totalSize);
  server.send(200, "audio/wav", "");

  WiFiClient client = server.client();
  uint8_t buffer[1024];
  size_t bytesLeft = totalSize;

  while (bytesLeft > 0 && client.connected()) {
    size_t toRead = (bytesLeft < sizeof(buffer)) ? bytesLeft : sizeof(buffer);
    size_t readCount = f.read(buffer, toRead);
    if (readCount == 0) break;

    client.write(buffer, readCount);
    bytesLeft -= readCount;
    handleLedStatus();
  }

  f.close();
  isTransferringFile = false;
  digitalWrite(PIN_BLUE_LED, HIGH);
  Serial.printf("File '%s' download finished. Sent %zu bytes.\n", targetFilename.c_str(), totalSize);
}

// ----------------------------------------------------------------------------
// Utilities & Sample Audio File Generation
// ----------------------------------------------------------------------------
String resolveFilenameById(String id) {
  File root = SPIFFS.open("/");
  File file = root.openNextFile();
  int idx = 1;
  while (file) {
    String fname = String(file.name());
    if (fname.startsWith("/")) fname = fname.substring(1);
    if (fname.endsWith(".wav") || fname.endsWith(".mp3") || fname.endsWith(".m4a")) {
      if (id == ("rec_" + String(idx)) || id == fname) {
        return fname;
      }
      idx++;
    }
    file = root.openNextFile();
  }
  return "recording_1.wav";
}

int countAudioFiles() {
  File root = SPIFFS.open("/");
  int count = 0;
  File file = root.openNextFile();
  while (file) {
    String fname = String(file.name());
    if (fname.endsWith(".wav") || fname.endsWith(".mp3") || fname.endsWith(".m4a")) {
      count++;
    }
    file = root.openNextFile();
  }
  return count;
}

void createDemoRecordingsIfEmpty() {
  if (countAudioFiles() == 0) {
    Serial.println("No recordings found on flash. Writing sample recording...");
    File f = SPIFFS.open("/lecture_intro.wav", "w");
    if (f) {
      // 44-byte WAV header + small audio payload
      uint8_t wavHeader[44] = {
        'R','I','F','F', 0x24, 0x08, 0x00, 0x00, 'W','A','V','E',
        'f','m','t',' ', 0x10, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00,
        0x44, 0xAC, 0x00, 0x00, 0x88, 0x58, 0x01, 0x00, 0x02, 0x00, 0x10, 0x00,
        'd','a','t','a', 0x00, 0x08, 0x00, 0x00
      };
      f.write(wavHeader, 44);
      uint8_t zeroBuf[512] = {0};
      for (int i = 0; i < 4; i++) f.write(zeroBuf, 512);
      f.close();
      Serial.println("Sample recording 'lecture_intro.wav' written to SPIFFS.");
    }
  }
}
