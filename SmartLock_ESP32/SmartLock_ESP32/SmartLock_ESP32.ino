
#include <WiFi.h>
#include <HTTPClient.h>
#include <SPI.h>
#include <MFRC522.h>
#include <Adafruit_Fingerprint.h>
#include <Keypad.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>

// ==================================================
// 1. WIFI & JAVA WEB SERVER
// ==================================================

const char* WIFI_SSID = "?????";
const char* WIFI_PASSWORD = "001007006";

const char* SERVER_BASE_URL =
  "http://192.168.137.1:8080/SmartLockSystemManagement";

// Must exactly match web.xml context-param deviceApiKey.
const char* API_KEY = "SmartLockESP32Key2026";
const int LOCK_ID = 1;

// Remote web unlock polling
const unsigned long POLL_INTERVAL_MS = 5000; // Less frequent blocking HTTP poll
unsigned long lastPollTime = 0;
bool remoteUnlockInProgress = false;

String verifyUrl() {
  return String(SERVER_BASE_URL) + "/api/device/verify";
}

String pollUrl() {
  return String(SERVER_BASE_URL) + "/api/device/poll?lockId=" + String(LOCK_ID);
}

String stateUrl() {
  return String(SERVER_BASE_URL) + "/api/device/state";
}

// ==================================================
// 2. RFID RC522
// ==================================================

#define RFID_SS  5
#define RFID_RST 13

MFRC522 rfid(RFID_SS, RFID_RST);

// ==================================================
// 3. FINGERPRINT AS608
// ==================================================

HardwareSerial fingerSerial(2);
Adafruit_Fingerprint finger(&fingerSerial);

#define FINGER_RX 16
#define FINGER_TX 17

bool fingerprintReady = false;
bool waitingFingerRemoval = false;
unsigned long fingerWaitStart = 0;
unsigned long lastHeartbeat = 0;

// ==================================================
// 4. LCD 1602 I2C
// ==================================================

LiquidCrystal_I2C lcd(0x27, 16, 2);

// ==================================================
// 5. ELECTRIC BOLT LOCK + RELAY + LIMIT SWITCH
// ==================================================

// Relay input uses GPIO4 (P04), as requested.
#define LOCK_RELAY_PIN 4

// Limit switch uses GPIO35. GPIO35 has no internal pull-up.
// Wiring assumption: GPIO35 -- 10k resistor --> 3.3V,
// and switch COM/NO connects GPIO35 to GND when pressed.
#define LIMIT_SWITCH_PIN 35

// Inside exit button on GPIO34.
// GPIO34 has NO internal pull-up: install an external 10k resistor
// from GPIO34 to 3.3V, and connect the button between GPIO34 and GND.
// Released = HIGH; pressed = LOW.
#define EXIT_BUTTON_PIN 34
const unsigned long EXIT_BUTTON_DEBOUNCE_MS = 40;
bool exitButtonRawLast = HIGH;
bool exitButtonStable = HIGH;
bool exitButtonArmed = false;
bool exitButtonHeld = false;
bool enrollmentMode = false;
bool enrollmentComboUsed = false;
unsigned long enrollmentStartedAt = 0;
const unsigned long ENROLLMENT_TIMEOUT_MS = 60000;
unsigned long exitButtonLastChange = 0;

// Set true if the relay is triggered by LOW.
// If your relay's H/L jumper is set to HIGH trigger, change to false.
const bool RELAY_ACTIVE_LOW = false;

// Maximum time to energize the solenoid to avoid overheating.
// The lock's seller/specification takes priority; do not exceed it.
// Coil safety cutoff: set according to the lock manufacturer's duty rating.
// This is NOT the normal auto-lock timer; the door-close switch controls relocking.
const unsigned long MAX_UNLOCK_MS = 8000;

bool unlockSessionActive = false;
bool doorOpenedDuringSession = false;
unsigned long unlockSessionStart = 0;

bool limitRawLast = HIGH;
bool limitStable = HIGH;
unsigned long limitLastChange = 0;
const unsigned long LIMIT_DEBOUNCE_MS = 40;

void setRelay(bool energized) {
  const uint8_t onLevel  = RELAY_ACTIVE_LOW ? LOW : HIGH;
  const uint8_t offLevel = RELAY_ACTIVE_LOW ? HIGH : LOW;
  digitalWrite(LOCK_RELAY_PIN, energized ? onLevel : offLevel);
}

// Reinitialize RC522 once after the lock relay is switched OFF.
// This can recover a reader whose SPI communication was disturbed by the lock.
// It does not replace fixing electrical noise or power problems.
void recoverRFIDAfterUnlock() {
  Serial.println("[RFID] Reinitializing RC522 after relay OFF...");
  delay(50);
  rfid.PCD_Init();
  delay(50);
  rfid.PCD_AntennaOn();
  Serial.println("[RFID] RC522 reinitialized.");
}

bool doorIsClosed() {
  // Assumes the switch is pressed and pulls GPIO35 LOW when door is closed.
  return limitStable == LOW;
}

void startUnlockSession(const char* method);
void updateLockAndDoorState();
void checkExitButton();

// ==================================================
// 6. KEYPAD 4x4
// ==================================================

const byte ROWS = 4;
const byte COLS = 4;

char keys[ROWS][COLS] = {
  {'1', '2', '3', 'A'},
  {'4', '5', '6', 'B'},
  {'7', '8', '9', 'C'},
  {'*', '0', '#', 'D'}
};

byte rowPins[ROWS] = {32, 33, 25, 26};
// NOTE: GPIO0 is a boot-strapping pin. Keep the keypad key on this line released while resetting/uploading.
byte colPins[COLS] = {14, 27, 12, 15}; // C2 moved from GPIO0 to GPIO15; GPIO15 is a boot-strapping pin

Keypad keypad = Keypad(
  makeKeymap(keys),
  rowPins,
  colPins,
  ROWS,
  COLS
);

String enteredPIN = "";

// ==================================================
// 7. LCD READY SCREEN
// ==================================================

void showReady() {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Smart Lock Ready");
  lcd.setCursor(0, 1);
  lcd.print("Scan / Enter PIN");
}

// ==================================================
// 8. VERIFY CREDENTIAL WITH JAVA WEB
// ==================================================

bool verifyCredential(const char* type, String value) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[AUTH] WiFi disconnected.");
    lcd.clear();
    lcd.print("WiFi Offline");
    showReady();
    return false;
  }

  if (value.length() == 0) {
    Serial.println("[AUTH] Empty credential.");
    return false;
  }

  HTTPClient http;
  http.setConnectTimeout(3000);
  http.setTimeout(8000);

  if (!http.begin(verifyUrl())) {
    Serial.println("[AUTH] HTTP initialization failed.");
    return false;
  }

  http.addHeader(
    "Content-Type",
    "application/x-www-form-urlencoded"
  );

  http.addHeader("X-API-Key", API_KEY);

  String body = "lockId=" + String(LOCK_ID);
  body += "&type=" + String(type);
  body += "&value=" + value;

  Serial.println("------------------------------");
  Serial.print("[AUTH] Type: ");
  Serial.println(type);

  int httpCode = http.POST(body);
  String response = "";

  if (httpCode > 0) {
    response = http.getString();
    response.trim();

    Serial.print("[AUTH] HTTP status: ");
    Serial.println(httpCode);

    Serial.print("[AUTH] Response: ");
    Serial.println(response);
  } else {
    Serial.print("[AUTH] HTTP error: ");
    Serial.println(http.errorToString(httpCode));
  }

  http.end();

  // Chi chap nhan ket qua thanh cong tu Java Web
  return httpCode == 200 && response == "OK";
}

void reportLockState(const char* state);

// ==================================================
// 9. REPORT LOCK STATE TO JAVA WEB
// ==================================================

void reportLockState(const char* state) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[STATE] WiFi offline; state not reported.");
    return;
  }

  HTTPClient http;
  http.setConnectTimeout(500);
  http.setTimeout(800);

  if (!http.begin(stateUrl())) {
    Serial.println("[STATE] HTTP initialization failed.");
    return;
  }

  http.addHeader("Content-Type", "application/x-www-form-urlencoded");
  http.addHeader("X-API-Key", API_KEY);

  String body = "lockId=" + String(LOCK_ID);
  body += "&state=" + String(state);

  int code = http.POST(body);
  String response = (code > 0) ? http.getString() : "";
  response.trim();

  Serial.print("[STATE] HTTP status: ");
  Serial.println(code);
  if (response.length() > 0) {
    Serial.print("[STATE] Response: ");
    Serial.println(response);
  }
  http.end();
}

// ==================================================
// 10. CHECK REMOTE UNLOCK COMMAND FROM WEBSITE
// ==================================================

void checkRemoteUnlock() {
  if (WiFi.status() != WL_CONNECTED || remoteUnlockInProgress) {
    return;
  }

  unsigned long now = millis();
  if (now - lastPollTime < POLL_INTERVAL_MS) {
    return;
  }
  lastPollTime = now;

  HTTPClient http;
  http.setConnectTimeout(500);
  http.setTimeout(800);

  if (!http.begin(pollUrl())) {
    Serial.println("[WEB] Poll HTTP initialization failed.");
    return;
  }

  http.addHeader("X-API-Key", API_KEY);
  int code = http.GET();
  String response = (code > 0) ? http.getString() : "";
  response.trim();
  http.end();

  if (code < 0) {
    Serial.print("[WEB] Poll failed, HTTPClient error code: ");
    Serial.println(code);
    return;
  }

  if (code != 200) {
    Serial.print("[WEB] Poll HTTP status: ");
    Serial.println(code);
    return;
  }

  if (response == "OPEN") {
    Serial.println("[WEB] Remote OPEN command received.");
    remoteUnlockInProgress = true;
    startUnlockSession("WEB");
    remoteUnlockInProgress = false;
  }
}

// ==================================================
// UNLOCK SESSION + DOOR/LIMIT SWITCH MONITORING
// ==================================================

void startUnlockSession(const char* method) {
  if (unlockSessionActive) {
    Serial.println("[LOCK] Unlock session already active; request ignored.");
    return;
  }

  Serial.print("[ACCESS] GRANTED: ");
  Serial.println(method);

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Access Granted");
  lcd.setCursor(0, 1);
  lcd.print(method);

  unlockSessionActive = true;
  doorOpenedDuringSession = false;
  unlockSessionStart = millis();
  setRelay(true);

  Serial.println("[LOCK] Relay ON; waiting for door to open/close.");
  reportLockState("UNLOCKED");
}

void checkExitButton() {
  // GPIO34 is input-only and requires an external 10k pull-up to 3.3V.
  const bool raw = digitalRead(EXIT_BUTTON_PIN);
  const unsigned long now = millis();

  if (raw != exitButtonRawLast) {
    exitButtonRawLast = raw;
    exitButtonLastChange = now;
  }

  if (now - exitButtonLastChange >= EXIT_BUTTON_DEBOUNCE_MS &&
      raw != exitButtonStable) {
    exitButtonStable = raw;

    if (exitButtonStable == LOW) {
      // Defer the normal exit action until release. This gives the user
      // time to press A while holding the inside exit button.
      exitButtonHeld = true;
      enrollmentComboUsed = false;
      exitButtonArmed = false;
      Serial.println("[EXIT] Button held; press A now for enrollment mode.");
    } else {
      if (exitButtonHeld) {
        exitButtonHeld = false;
        if (!enrollmentComboUsed && !enrollmentMode) {
          Serial.println("[EXIT] BUTTON PRESSED - requesting unlock.");
          if (!unlockSessionActive) {
            startUnlockSession("EXIT BUTTON");
          } else {
            Serial.println("[EXIT] Unlock session already active; request ignored.");
          }
        }
      }
      exitButtonArmed = true;
      Serial.println("[EXIT] Button released; armed for next press.");
    }
  }
}

void enterEnrollmentMode() {
  if (unlockSessionActive) {
    Serial.println("[ENROLL] Cannot enroll while unlock session is active.");
    return;
  }

  enrollmentMode = true;
  enrollmentStartedAt = millis();
  enteredPIN = "";
  waitingFingerRemoval = false;

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("ENROLL MODE");
  lcd.setCursor(0, 1);
  lcd.print("Scan card/finger");

  Serial.println("========== ENROLLMENT MODE ==========");
  Serial.println("Scan an RFID card OR enroll a fingerprint.");
  Serial.println("Press D to exit; auto-exit after 60 seconds.");
}

void exitEnrollmentMode() {
  enrollmentMode = false;
  waitingFingerRemoval = false;
  Serial.println("[ENROLL] Enrollment mode ended.");
  showReady();
}

void showEnrollmentMessage(const char* line1, const String& line2) {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print(line1);
  lcd.setCursor(0, 1);
  lcd.print(line2.substring(0, 16));
}

void enrollFingerprint() {
  if (!fingerprintReady) {
    showEnrollmentMessage("AS608 NOT READY", "Check sensor");
    Serial.println("[ENROLL][FINGER] AS608 not ready.");
    delay(1200);
    return;
  }

  // IMPORTANT:
  // checkFingerprint() already called finger.getImage() and confirmed
  // FINGERPRINT_OK before entering this function. Do not call getImage()
  // again here, or the first captured image may be consumed/lost.

  // Find a free template slot BEFORE creating the new model.
  // loadModel() uses the sensor's character buffer, so doing this after
  // createModel() could overwrite the newly-created fingerprint template.
  showEnrollmentMessage("Finding free ID", "Please wait...");
  int freeId = -1;

  for (int id = 1; id <= 127; id++) {
    uint8_t loadStatus = finger.loadModel((uint16_t)id);

    if (loadStatus != FINGERPRINT_OK) {
      freeId = id;
      break;
    }
  }

  if (freeId < 0) {
    showEnrollmentMessage("Sensor full", "No free ID");
    Serial.println("[ENROLL][FINGER] Sensor database full.");
    delay(1200);
    return;
  }

  Serial.print("[ENROLL][FINGER] Selected free ID: ");
  Serial.println(freeId);

  // Convert the first image already captured by checkFingerprint().
  showEnrollmentMessage("Place finger", "First scan...");
  uint8_t p = finger.image2Tz(1);

  if (p != FINGERPRINT_OK) {
    showEnrollmentMessage("Scan failed", "Try again");
    Serial.print("[ENROLL][FINGER] First image2Tz error: ");
    Serial.println(p);
    delay(1000);
    return;
  }

  // Reject a fingerprint that is already enrolled in the sensor.
  p = finger.fingerSearch();

  if (p == FINGERPRINT_OK) {
    String msg = "Already ID: " + String(finger.fingerID);
    showEnrollmentMessage("Finger exists", msg);
    Serial.print("[ENROLL][FINGER] Already registered. ID: ");
    Serial.println(finger.fingerID);
    delay(1500);
    return;
  }

  // Wait for the user to remove the finger before the second scan.
  showEnrollmentMessage("Remove finger", "Please wait...");
  unsigned long waitStart = millis();
  bool fingerRemoved = false;

  while (millis() - waitStart < 8000 && enrollmentMode) {
    uint8_t imageStatus = finger.getImage();

    if (imageStatus == FINGERPRINT_NOFINGER) {
      fingerRemoved = true;
      break;
    }

    delay(50);
  }

  if (!fingerRemoved || !enrollmentMode) {
    showEnrollmentMessage("Remove timeout", "Try again");
    Serial.println("[ENROLL][FINGER] Finger was not removed in time.");
    delay(1000);
    return;
  }

  // Capture the same finger for the second time.
  showEnrollmentMessage("Place same finger", "Second scan...");
  waitStart = millis();

  do {
    p = finger.getImage();

    if (p == FINGERPRINT_OK) {
      break;
    }

    delay(50);
  } while (millis() - waitStart < 8000 && enrollmentMode);

  if (p != FINGERPRINT_OK || !enrollmentMode) {
    showEnrollmentMessage("Enroll timeout", "Try again");
    Serial.println("[ENROLL][FINGER] Second scan timeout.");
    delay(1000);
    return;
  }

  p = finger.image2Tz(2);

  if (p != FINGERPRINT_OK) {
    showEnrollmentMessage("Second scan fail", "Try again");
    Serial.print("[ENROLL][FINGER] Second image2Tz error: ");
    Serial.println(p);
    delay(1000);
    return;
  }

  // Merge the two scans into one fingerprint model.
  p = finger.createModel();

  if (p != FINGERPRINT_OK) {
    showEnrollmentMessage("Finger mismatch", "Try again");
    Serial.print("[ENROLL][FINGER] createModel error: ");
    Serial.println(p);
    delay(1200);
    return;
  }

  // Store the newly-created model in the free slot selected earlier.
  p = finger.storeModel((uint16_t)freeId);

  if (p == FINGERPRINT_OK) {
    String idText = "ID: " + String(freeId);
    showEnrollmentMessage("Finger saved", idText);

    Serial.println("========== FINGERPRINT SAVED ==========");
    Serial.print("FINGERPRINT_ID=");
    Serial.println(freeId);
    Serial.println("[ENROLL][FINGER] Model stored in AS608 flash.");
    Serial.println("Enter this Fingerprint ID as a credential on Java Web.");
  } else {
    showEnrollmentMessage("Save failed", "AS608 error");
    Serial.print("[ENROLL][FINGER] Store error: ");
    Serial.println(p);
  }

  delay(1600);
}

void updateLockAndDoorState() {
  // Debounce the limit switch.
  bool raw = digitalRead(LIMIT_SWITCH_PIN);
  if (raw != limitRawLast) {
    limitRawLast = raw;
    limitLastChange = millis();
  }

  if (millis() - limitLastChange >= LIMIT_DEBOUNCE_MS &&
      limitStable != raw) {
    limitStable = raw;
    Serial.print("[DOOR] ");
    Serial.println(doorIsClosed() ? "CLOSED (switch pressed)" :
                                   "OPEN (switch released)");
  }

  if (!unlockSessionActive) {
    return;
  }

  // After access is granted, keep the solenoid energized until the door
  // opens and then closes again, but enforce a hard timeout for coil safety.
  if (!doorIsClosed()) {
    doorOpenedDuringSession = true;
  }

  if (doorOpenedDuringSession && doorIsClosed()) {
    setRelay(false);
    unlockSessionActive = false;
    Serial.println("[LOCK] Door closed after opening; relay OFF.");
    recoverRFIDAfterUnlock();
    reportLockState("LOCKED");
    enteredPIN = "";
    showReady();
    return;
  }

  // Safety cutoff only: never keep a solenoid energized beyond its rated duty time.
  // Normal relocking happens only after the switch reports:
  // door opened (switch released) and then door closed (switch pressed).
  if (millis() - unlockSessionStart >= MAX_UNLOCK_MS) {
    setRelay(false);
    unlockSessionActive = false;
    Serial.println("[LOCK] SAFETY CUTOFF: relay OFF. Check door/switch; not auto-relocked by timer.");
    recoverRFIDAfterUnlock();
    reportLockState(doorIsClosed() ? "LOCKED" : "UNLOCKED");
    enteredPIN = "";
    showReady();
  }
}

// ==================================================
// 10. DENY ACCESS
// ==================================================

void deniedAccess(const char* method) {
  Serial.print("[ACCESS] DENIED: ");
  Serial.println(method);

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("ACCESS DENIED");
  lcd.setCursor(0, 1);
  lcd.print(method);

  enteredPIN = "";
  delay(1500);  // Keep DENIED visible briefly
  showReady();
}

// ==================================================
// 11. RFID AUTHENTICATION
// ==================================================

void checkRFID() {
  if (unlockSessionActive) return;

  if (!rfid.PICC_IsNewCardPresent()) {
    return;
  }

  if (!rfid.PICC_ReadCardSerial()) {
    return;
  }

  String uid = "";

  for (byte i = 0; i < rfid.uid.size; i++) {
    if (rfid.uid.uidByte[i] < 0x10) {
      uid += "0";
    }

    uid += String(rfid.uid.uidByte[i], HEX);
  }

  uid.toUpperCase();

  if (enrollmentMode) {
    Serial.println("========== RFID CARD READ ==========");
    Serial.print("RFID_UID=");
    Serial.println(uid);
    Serial.println("Copy this UID and add it as a CARD credential on Java Web.");
    showEnrollmentMessage("RFID UID captured", uid);
    rfid.PICC_HaltA();
    rfid.PCD_StopCrypto1();
    delay(1800);
    showEnrollmentMessage("ENROLL MODE", "Scan card/finger");
    return;
  }

  Serial.print("[RFID] UID: ");
  Serial.println(uid);

  rfid.PICC_HaltA();
  rfid.PCD_StopCrypto1();

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Checking RFID...");

  if (verifyCredential("CARD", uid)) {
    startUnlockSession("RFID");
  } else {
    deniedAccess("RFID");
  }
}

// ==================================================
// 12. START WAIT FOR FINGER REMOVAL
// ==================================================

void startFingerRemovalWait() {
  waitingFingerRemoval = true;
  fingerWaitStart = millis();
}

// ==================================================
// 13. FINGERPRINT AUTHENTICATION
// ==================================================

void checkFingerprint() {
  if (unlockSessionActive) return;
  if (enrollmentMode) {
    if (!fingerprintReady || waitingFingerRemoval) return;
    uint8_t enrollImage = finger.getImage();
    if (enrollImage == FINGERPRINT_OK) {
      enrollFingerprint();
      startFingerRemovalWait();
    }
    return;
  }
  if (!fingerprintReady || waitingFingerRemoval) {
    return;
  }

  uint8_t p = finger.getImage();

  if (p != FINGERPRINT_OK) {
    return;
  }

  Serial.println("[FINGER] Finger detected.");

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Reading Finger");

  p = finger.image2Tz();

  if (p != FINGERPRINT_OK) {
    Serial.println("[FINGER] Image processing failed.");

    deniedAccess("FINGERPRINT");
    startFingerRemovalWait();
    return;
  }

  p = finger.fingerSearch();

  if (p == FINGERPRINT_OK) {
    uint16_t fingerID = finger.fingerID;

    Serial.print("[FINGER] Matched ID: ");
    Serial.println(fingerID);

    lcd.clear();
    lcd.print("Checking Server");

    if (verifyCredential(
          "FINGERPRINT",
          String(fingerID))) {
      startUnlockSession("FINGERPRINT");
    } else {
      deniedAccess("FINGERPRINT");
    }
  } else if (p == FINGERPRINT_NOTFOUND) {
    Serial.println("[FINGER] Not enrolled on sensor.");

    lcd.clear();
    lcd.print("Finger Unknown");
    showReady();
  } else {
    Serial.print("[FINGER] Search error: ");
    Serial.println(p);
  }

  startFingerRemovalWait();
}

// ==================================================
// 14. KEYPAD PIN AUTHENTICATION
// ==================================================

void checkKeypad() {
  if (unlockSessionActive) return;

  char key = keypad.getKey();

  if (key == NO_KEY) {
    return;
  }

  Serial.print("[KEYPAD] Key: ");
  Serial.println(key);

  if (enrollmentMode) {
    if (key == 'D') {
      exitEnrollmentMode();
    }
    return;
  }

  // Hold GPIO34 and press A together to enter enrollment mode.
  if (key == 'A' && exitButtonHeld && exitButtonStable == LOW) {
    enrollmentComboUsed = true;
    enterEnrollmentMode();
    return;
  }

  // Ignore A/B/C/D outside enrollment mode.
  if (key == 'A' || key == 'B' || key == 'C' || key == 'D') {
    return;
  }

  // Nhap chu so
  if (key >= '0' && key <= '9') {
    if (enteredPIN.length() < 8) {
      enteredPIN += key;
    }

    // Update only the PIN row; avoid unnecessary full-row redraws.
    lcd.setCursor(0, 0);
    lcd.print("Enter PIN:      ");
    lcd.setCursor(0, 1);
    lcd.print("                ");
    lcd.setCursor(0, 1);
    for (unsigned int i = 0; i < enteredPIN.length(); i++) {
      lcd.print('*');
    }
  }

  // Xoa PIN
  else if (key == '*') {
    enteredPIN = "";

    lcd.clear();
    lcd.print("PIN Cleared");
    showReady();
  }

  // Gui PIN len server
  else if (key == '#') {
    String pin = enteredPIN;
    enteredPIN = "";

    if (pin.length() < 4 || pin.length() > 8) {
      deniedAccess("PIN");
      return;
    }

    lcd.clear();
    lcd.print("Checking Server");

    if (verifyCredential("PIN", pin)) {
      startUnlockSession("PIN");
    } else {
      deniedAccess("PIN");
    }
  }
}

// ==================================================
// 15. SETUP
// ==================================================

void setup() {
  Serial.begin(115200);
  delay(500);

  // LCD
  Wire.begin(21, 22);
  lcd.init();
  lcd.backlight();

  lcd.clear();
  lcd.print("Starting Lock...");

  // Relay: set OFF level before configuring output to reduce startup glitches.
  digitalWrite(LOCK_RELAY_PIN, RELAY_ACTIVE_LOW ? HIGH : LOW);
  pinMode(LOCK_RELAY_PIN, OUTPUT);
  setRelay(false);

  // Exit button on GPIO34: external 10k pull-up to 3.3V is mandatory.
  // Button connects GPIO34 to GND when pressed. Do not use INPUT_PULLUP here.
  pinMode(EXIT_BUTTON_PIN, INPUT);
  exitButtonRawLast = digitalRead(EXIT_BUTTON_PIN);
  exitButtonStable = exitButtonRawLast;
  exitButtonArmed = (exitButtonRawLast == HIGH);
  exitButtonLastChange = millis();
  Serial.println("[EXIT] Button on GPIO34 initialized (external pull-up; pressed=LOW).");
  Serial.print("[EXIT] Initial GPIO34 level: ");
  Serial.println(exitButtonRawLast == LOW ? "LOW" : "HIGH");
  if (!exitButtonArmed) {
    Serial.println("[EXIT] Startup level LOW; release button before it can trigger.");
  }

  // Limit switch: GPIO35 is input-only and has no internal pull-up.
  // External 10k pull-up to 3.3V is required with the wiring described above.
  pinMode(LIMIT_SWITCH_PIN, INPUT);
  limitRawLast = digitalRead(LIMIT_SWITCH_PIN);
  limitStable = limitRawLast;
  limitLastChange = millis();
  Serial.print("[DOOR] Initial state: ");
  Serial.println(doorIsClosed() ? "CLOSED" : "OPEN/UNPRESSED");

  // RFID SPI
  SPI.begin(18, 19, 23, RFID_SS);
  rfid.PCD_Init();
  delay(100);

  Serial.println("[RFID] RC522 initialized.");
  rfid.PCD_DumpVersionToSerial();

  // AS608
  fingerSerial.begin(
    57600,
    SERIAL_8N1,
    FINGER_RX,
    FINGER_TX
  );

  finger.begin(57600);
  fingerprintReady = finger.verifyPassword();

  Serial.print("[FINGER] AS608: ");
  Serial.println(
    fingerprintReady ? "READY" : "NOT FOUND"
  );

  // WiFi
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.print("[WIFI] Connecting");

  unsigned long startTime = millis();

  while (WiFi.status() != WL_CONNECTED &&
         millis() - startTime < 15000) {
    delay(300);
    Serial.print(".");
  }

  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("[WIFI] ESP32 IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("[WIFI] Offline.");
  }

  showReady();
  Serial.println("[SYSTEM] Smart Lock ready.");
}

// ==================================================
// 16. MAIN LOOP
// ==================================================

void loop() {
  updateLockAndDoorState();
  checkExitButton();

  if (enrollmentMode && millis() - enrollmentStartedAt >= ENROLLMENT_TIMEOUT_MS) {
    Serial.println("[ENROLL] 60-second timeout.");
    exitEnrollmentMode();
  }

  if (millis() - lastHeartbeat >= 2000) {
    lastHeartbeat = millis();
  }

  if (!unlockSessionActive) {
    // Scan local input first so web polling cannot take priority over keypad.
    checkKeypad();
    checkRFID();

    if (waitingFingerRemoval && fingerprintReady) {
      uint8_t p = finger.getImage();
      if (p == FINGERPRINT_NOFINGER ||
          millis() - fingerWaitStart > 5000) {
        waitingFingerRemoval = false;
      }
    }

    checkFingerprint();

    // Do not run remote polling while the user is enrolling credentials.
    if (!enrollmentMode) {
      checkRemoteUnlock();
    }
  }

  delay(1);
}
