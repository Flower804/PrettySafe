
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <TinyGPS++.h>
#include <string>

// BLE service and characteristic UUIDs
#define SERVICE_UUID        "19b10000-e8f2-537e-4f6c-d104768a1214"
#define CHARACTERISTIC_UUID "19b10001-e8f2-537e-4f6c-d104768a1214"
#define MESSAGE_UUID "19b10002-e8f2-537e-4f6c-d104768a1214"

BLECharacteristic* messageCharacteristic = nullptr;

// BLE
BLECharacteristic* switchCharacteristic = nullptr;
BLEServer* bleServer = nullptr;

volatile bool deviceConnected = false;

// Command received from Android
int pendingCommand = -1;
bool commandReceived = false;

// Protect shared command data between BLE callback and loop
portMUX_TYPE commandMux = portMUX_INITIALIZER_UNLOCKED;

// Button and LED pins
static const int buttonPin = 27;
static const int LEDPin = 32;

int buttonState = 0;
int case_state = 0;

//cisjordania flower
//pink roses
//country blues

//cutes
//gerbera


//dates should consist of an activity

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

  // Initialize BLE
  BLEDevice::init("Pretty Safe");

  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new ServerCallbacks());
  BLEService* ledService = bleServer->createService(SERVICE_UUID);

  // Characteristic 1: Android -> ESP32
  switchCharacteristic = ledService->createCharacteristic(
      CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_READ |
      BLECharacteristic::PROPERTY_WRITE
  );

  switchCharacteristic->setCallbacks(new SwitchCallbacks());
  switchCharacteristic->setValue("0");

  // Characteristic 2: ESP32 -> Android
  messageCharacteristic = ledService->createCharacteristic(
      MESSAGE_UUID,
      BLECharacteristic::PROPERTY_READ |
      BLECharacteristic::PROPERTY_NOTIFY
  );

  messageCharacteristic->setValue("0");

  // Start service AFTER creating both characteristics
  ledService->start();

  // Start advertising AFTER starting service
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
  buttonState = digitalRead(buttonPin);

  if(buttonState == HIGH){
    button_counter();
  }

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

  if (passed_time > 3000) {
    //Serial.println("time has passed");
    send_message(1);
    started = 1;
  }
}

static void send_message(int value){
  messageCharacteristic->setValue(std::to_string(value));

  messageCharacteristic->notify();
  Serial.println("message sent");
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
