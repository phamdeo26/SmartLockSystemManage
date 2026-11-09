#include <WiFi.h>
#include <HTTPClient.h>
#include <SPI.h>
#include <MFRC522.h>
#include <Adafruit_Fingerprint.h>
#include <Keypad.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>


const char* WIFI_SSID = "?????";
const char* WIFI_PASSWORD = "001007006";

const char* SERVER_BASE_URL =
  "http://192.168.137.1:8080/SmartLockSystemManagement";


const char* API_KEY = "SmartLockESP32Key2026";
const int LOCK_ID = 1;


const unsigned long POLL_INTERVAL_MS = 5000;
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


#define RFID_SS  5
#define RFID_RST 13

MFRC522 rfid(RFID_SS, RFID_RST);


HardwareSerial fingerSerial(2);
Adafruit_Fingerprint finger(&fingerSerial);

#define FINGER_RX 16
#define FINGER_TX 17

bool fingerprintReady = false;
bool waitingFingerRemoval = false;
unsigned long fingerWaitStart = 0;
unsigned long lastHeartbeat = 0;


LiquidCrystal_I2C lcd(0x27, 16, 2);


#define LOCK_RELAY_PIN 4


#define LIMIT_SWITCH_PIN 35


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


const bool RELAY_ACTIVE_LOW = false;


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


void recoverRFIDAfterUnlock() {
  Serial.println("[RFID] Reinitializing RC522 after relay OFF...");
  delay(50);
  rfid.PCD_Init();
  delay(50);
  rfid.PCD_AntennaOn();
  Serial.println("[RFID] RC522 reinitialized.");
}

bool doorIsClosed() {

  return limitStable == LOW;
}

void startUnlockSession(const char* method);
void updateLockAndDoorState();
void checkExitButton();


const byte ROWS = 4;
const byte COLS = 4;

char keys[ROWS][COLS] = {
  {'1', '2', '3', 'A'},
  {'4', '5', '6', 'B'},
  {'7', '8', '9', 'C'},
  {'*', '0', '#', 'D'}
};

byte rowPins[ROWS] = {32, 33, 25, 26};

byte colPins[COLS] = {14, 27, 12, 15};

Keypad keypad = Keypad(
  makeKeymap(keys),
  rowPins,
  colPins,
  ROWS,
  COLS
);

String enteredPIN = "";


void showReady() {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Smart Lock Ready");
  lcd.setCursor(0, 1);
  lcd.print("Scan / Enter PIN");
}


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


  return httpCode == 200 && response == "OK";
}

void reportLockState(const char* state);


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


  showEnrollmentMessage("Place finger", "First scan...");
  uint8_t p = finger.image2Tz(1);

  if (p != FINGERPRINT_OK) {
    showEnrollmentMessage("Scan failed", "Try again");
    Serial.print("[ENROLL][FINGER] First image2Tz error: ");
    Serial.println(p);
    delay(1000);
    return;
  }


  p = finger.fingerSearch();

  if (p == FINGERPRINT_OK) {
    String msg = "Already ID: " + String(finger.fingerID);
    showEnrollmentMessage("Finger exists", msg);
    Serial.print("[ENROLL][FINGER] Already registered. ID: ");
    Serial.println(finger.fingerID);
    delay(1500);
    return;
  }


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


  p = finger.createModel();

  if (p != FINGERPRINT_OK) {
    showEnrollmentMessage("Finger mismatch", "Try again");
    Serial.print("[ENROLL][FINGER] createModel error: ");
    Serial.println(p);
    delay(1200);
    return;
  }


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


void deniedAccess(const char* method) {
  Serial.print("[ACCESS] DENIED: ");
  Serial.println(method);

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("ACCESS DENIED");
  lcd.setCursor(0, 1);
  lcd.print(method);

  enteredPIN = "";
  delay(1500);
  showReady();
}


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


void startFingerRemovalWait() {
  waitingFingerRemoval = true;
  fingerWaitStart = millis();
}


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


  if (key == 'A' && exitButtonHeld && exitButtonStable == LOW) {
    enrollmentComboUsed = true;
    enterEnrollmentMode();
    return;
  }


  if (key == 'A' || key == 'B' || key == 'C' || key == 'D') {
    return;
  }


  if (key >= '0' && key <= '9') {
    if (enteredPIN.length() < 8) {
      enteredPIN += key;
    }


    lcd.setCursor(0, 0);
    lcd.print("Enter PIN:      ");
    lcd.setCursor(0, 1);
    lcd.print("                ");
    lcd.setCursor(0, 1);
    for (unsigned int i = 0; i < enteredPIN.length(); i++) {
      lcd.print('*');
    }
  }


  else if (key == '*') {
    enteredPIN = "";

    lcd.clear();
    lcd.print("PIN Cleared");
    showReady();
  }


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


void setup() {
  Serial.begin(115200);
  delay(500);


  Wire.begin(21, 22);
  lcd.init();
  lcd.backlight();

  lcd.clear();
  lcd.print("Starting Lock...");


  digitalWrite(LOCK_RELAY_PIN, RELAY_ACTIVE_LOW ? HIGH : LOW);
  pinMode(LOCK_RELAY_PIN, OUTPUT);
  setRelay(false);


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


  pinMode(LIMIT_SWITCH_PIN, INPUT);
  limitRawLast = digitalRead(LIMIT_SWITCH_PIN);
  limitStable = limitRawLast;
  limitLastChange = millis();
  Serial.print("[DOOR] Initial state: ");
  Serial.println(doorIsClosed() ? "CLOSED" : "OPEN/UNPRESSED");


  SPI.begin(18, 19, 23, RFID_SS);
  rfid.PCD_Init();
  delay(100);

  Serial.println("[RFID] RC522 initialized.");
  rfid.PCD_DumpVersionToSerial();


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


    if (!enrollmentMode) {
      checkRemoteUnlock();
    }
  }

  delay(1);
}
