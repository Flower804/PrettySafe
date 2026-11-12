
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <TinyGPS++.h>
#include <string>

// BLE service and characteristic UUIDs
#define SERVICE_UUID        "19b10000-e8f2-537e-4f6c-d104768a1214"
#define CHARACTERISTIC_UUID "19b10001-e8f2-537e-4f6c-d104768a1214"

// BLE
BLECharacteristic* switchCharacteristic = nullptr;
BLEServer* bleServer = nullptr;

volatile bool deviceConnected = false;

// Command received from Android
int pendingCommand = -1;
bool commandReceived = false;

// Protect shared command data between BLE callback and loop
portMUX_TYPE commandMux = portMUX_INITIALIZER_UNLOCKED;

// GPS pins
static const int RXPin = 5;
static const int TXPin = 18;
static const uint32_t GPSBaud = 4800;

// Button and LED pins
static const int buttonPin = 34;
static const int LEDPin = 32;

int buttonState = 0;
int case_state = 0;

// TinyGPS++
TinyGPSPlus gps;
HardwareSerial GPS_Serial(2);

// ------------------------------------
// BLE characteristic callbacks
// ------------------------------------

class SwitchCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    std::string value = characteristic->getValue();

    if (value.length() > 0) {
      int receivedCommand = (uint8_t)value[0];

      portENTER_CRITICAL(&commandMux);
      pendingCommand = receivedCommand;
      commandReceived = true;
      portEXIT_CRITICAL(&commandMux);

      Serial.print("BLE command received: ");
      Serial.println(receivedCommand);
    }
  }
};

// ------------------------------------
// BLE connection callbacks
// ------------------------------------

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override {
    deviceConnected = true;
    Serial.println("Android connected!");
  }

  void onDisconnect(BLEServer* server) override {
    deviceConnected = false;
    Serial.println("Android disconnected!");

    // Allow Android to find the ESP32 again
    BLEDevice::startAdvertising();
    Serial.println("BLE advertising restarted");
  }
};

// ------------------------------------
// Setup
// ------------------------------------

void setup() {
  Serial.begin(115200);
  Serial.println("PrettySafe started");

  // Pin setup
  pinMode(buttonPin, INPUT);
  pinMode(LEDPin, OUTPUT);
  digitalWrite(LEDPin, LOW);

  // Initialize GPS
  GPS_Serial.begin(GPSBaud, SERIAL_8N1, RXPin, TXPin);
  Serial.println("GPS initialized");

  // Initialize BLE
  BLEDevice::init("Pretty Safe");

  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new ServerCallbacks());

  BLEService* ledService = bleServer->createService(SERVICE_UUID);

  switchCharacteristic = ledService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_READ |
    BLECharacteristic::PROPERTY_WRITE
  );

  switchCharacteristic->setCallbacks(new SwitchCallbacks());
  switchCharacteristic->setValue("0");

  ledService->start();

  // Start BLE advertising
  BLEAdvertising* advertising = BLEDevice::getAdvertising();

  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();

  Serial.println("BLE initialized");
  Serial.println("Device name: Pretty Safe");
  Serial.println("Waiting for Android connection...");

  Serial.println();
}

// ------------------------------------
// Main loop
// ------------------------------------

void loop() {

  // Check if Android sent a command
  int receivedCommand = -1;
  bool newCommand = false;

  portENTER_CRITICAL(&commandMux);

  if (commandReceived) {
    receivedCommand = pendingCommand;
    commandReceived = false;
    newCommand = true;
  }

  portEXIT_CRITICAL(&commandMux);

  // Process command outside the BLE callback
  if (newCommand) {

    switch (receivedCommand) {

      case 0:
        digitalWrite(LEDPin, LOW);
        Serial.println("LED OFF");
        break;

      case 1:
        digitalWrite(LEDPin, HIGH);
        Serial.println("LED ON");
        break;

      default:
        Serial.println("Unknown BLE command");
        break;
    }
  }

  // GPS and button functionality can be added here later.

}

// ------------------------------------
// Existing button functions
// ------------------------------------

int started = 0;
long starting_time = 0;

static long timepassed(long current_time) {
  if (started == 0) {
    starting_time = millis();
    started = 1;
  }

  return current_time - starting_time;
}

long passed_time = 0;

void button_counter() {
  passed_time = timepassed(millis());

  if (passed_time > 10) {
    digitalWrite(LEDPin, LOW);
    started = 0;
  } else {
    digitalWrite(LEDPin, HIGH);
  }
}

int counter = 0;
int button_State = 0;

static void button_evaluator() {
  button_State = digitalRead(buttonPin);

  if (button_State == 1) {
    Serial.println("Button pressed");

    if (counter == 0) {
      button_counter();
    }
  }
}

static void LED_state(int state) {
  switch (state) {
    case 1:
      digitalWrite(LEDPin, LOW);
      break;
 
    case 2:
      // LED blinking logic can be added later.
      break;
  }
}
