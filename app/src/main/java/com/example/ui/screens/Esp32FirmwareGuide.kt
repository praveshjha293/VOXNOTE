package com.example.ui.screens

object Esp32FirmwareGuide {
    const val WIRING_INSTRUCTIONS = """
HARDWARE WIRING & SETUP:
1. ESP32 Board: Any ESP32 DevKit V1, NodeMCU-32S, or ESP32-WROOM board.
2. 3-Second Action Button:
   - Connect one leg of a momentary push button to GPIO 4 (or GPIO 0).
   - Connect the other leg to GND (Ground).
   - Uses internal pull-up resistor (INPUT_PULLUP).
3. Blue Indicator LED:
   - Built-in LED on GPIO 2 (most boards), or external blue LED on GPIO 2.
   - External: Anode -> GPIO 2 via 220Ω resistor; Cathode -> GND.
4. Behavior:
   - Press and hold button for 3 seconds.
   - Blue LED turns ON solid.
   - Wi-Fi AP starts: "NotesMaker-AP" (Password: "notesmaker123").
   - REST API starts at http://192.168.4.1:80.
   - While downloading a recording, the Blue LED blinks rapidly.
"""

    const val FIRMWARE_CODE = """
// ESP32 Notes Maker Firmware (Arduino IDE compatible)
// Include WiFi, WebServer, SPIFFS
#include <WiFi.h>
#include <WebServer.h>
#include <FS.h>
#include <SPIFFS.h>

#define PIN_BUTTON   4
#define PIN_BLUE_LED 2

const char* AP_SSID     = "NotesMaker-AP";
const char* AP_PASSWORD = "notesmaker123";
const IPAddress AP_IP(192, 168, 4, 1);

WebServer server(80);
bool isApActive = false;
bool isTransferMode = false;
unsigned long buttonStartTime = 0;

void setup() {
  Serial.begin(115200);
  pinMode(PIN_BUTTON, INPUT_PULLUP);
  pinMode(PIN_BLUE_LED, OUTPUT);
  digitalWrite(PIN_BLUE_LED, LOW);
  SPIFFS.begin(true);
}

void loop() {
  if (digitalRead(PIN_BUTTON) == LOW) {
    if (buttonStartTime == 0) buttonStartTime = millis();
    else if (!isApActive && (millis() - buttonStartTime >= 3000)) {
      startAP();
    }
  } else {
    buttonStartTime = 0;
  }
  if (isApActive) server.handleClient();
}

void startAP() {
  WiFi.mode(WIFI_AP);
  WiFi.softAPConfig(AP_IP, AP_IP, IPAddress(255, 255, 255, 0));
  WiFi.softAP(AP_SSID, AP_PASSWORD);
  isApActive = true;
  digitalWrite(PIN_BLUE_LED, HIGH);

  server.on("/api/status", HTTP_GET, []() {
    server.send(200, "application/json", "{\"status\":\"ready\",\"device\":\"ESP32-NotesMaker\",\"transfer_mode\":true,\"ip\":\"192.168.4.1\"}");
  });
  server.on("/api/files", HTTP_GET, []() {
    server.send(200, "application/json", "[{\"id\":\"rec_1\",\"name\":\"meeting_sync.wav\",\"size_bytes\":102400,\"duration_seconds\":60,\"format\":\"wav\"}]");
  });
  server.begin();
}
"""
}
