#include "esp_bt_main.h"
#include "esp_bt_device.h"
#include "BluetoothSerial.h"
#include <TinyGPS++.h>

//TinyGPS stuff taken from the official TinyGPSPlusLibrary on github: https://github.com/MrRobotrick/GY-NEO6MV2_GPS_Module/tree/master

//pin-setup
//----GPS module pin----
static const int RXPin = 5;
static const int TXPin = 18;
static const uint32_t GPSBaud = 4800;

//----Button module pin----
static const int buttonPin = 34; //define
static const int LEDPin = 32; //define

int buttonState = 0;
int case_state = 0;
int command = 0;

//The TinyGPS++ object
TinyGPSPlus gps;

//The bluetooth object
BluetoothSerial SerialBT;

//initializer of bluetooth: taken from the official documentation
void printDeviceAddress() {
  const uint8_t* point = esp_bt_dev_get_address();
  for (int i = 0; i < 6; i++){
    char str[3];
    sprintf(str, "%02x", (int)point[i]);
    Serial.print(str);
    if(i < 5){
      Serial.print(":");
    }
  }
}

//The serial connection to the GPS device
HardwareSerial GPS_Serial(2);

void setup() {
  Serial.begin(115200);
  Serial.println("Device started");
  
  //initialize GPS module
  GPS_Serial.begin(GPSBaud, SERIAL_8N1, RXPin, TXPin);
  Serial.println("GPS initialized");

  //initialize Bluetooth module
  SerialBT.begin("Pretty Safe");
  Serial.println("Bluetooth initialized");

  //Pin set-up
  pinMode(buttonPin, INPUT);
  pinMode(LEDPin, OUTPUT);

  Serial.print("Maxaddr: ");
  printDeviceAddress();

  Serial.println(" ");

  Serial.println(F("Sats HDOP  Latitude   Longitude   Fix  Date       Time     Date Alt    Course Speed Card  Distance Course Card  Chars Sentences Checksum"));
  Serial.println(F("           (deg)      (deg)       Age                      Age  (m)    --- from GPS ----  ---- to London  ----  RX    RX        Fail"));
  Serial.println(F("----------------------------------------------------------------------------------------------------------------------------------------"));
}

void loop() {
  //TODO: list of requisits
  //1 - silent alert
  //2 - silent confirmation
  //3 - start sending data if the alert is active
  //4 - cancel pin
  //5 - option buzzer
  readbluetooth();
  //button_evaluator();
  //Serial.write(command);


  //GPS_module();
  /*
  switch(case_state){
    case 1:
      SilentAlert();

      break;
    case 2:
    
      break;
    case 3:

      break;
    case 4:

      break;
    case 5:

      break;
  }
  */

  command = 0;
  //TODO: Make connection with the database to send the GPS data, and receive the necessary data to update the LED
}

int started = 0;
long starting_time = 0;

static long timepassed(long current_time){
  if(started == 0){
    starting_time = millis();
  
    started = 1;
  }

  return current_time - starting_time;
}

long passed_time = 0;

int button_counter(){
  passed_time = timepassed(millis());

  //TODO: make the button interpreter
  //aka detect the button press, during button press count time, else evaluate how many button presses have happened
  if(passed_time > 10){
    digitalWrite(LEDPin, LOW);
    started = 0;
  } else {
    digitalWrite(LEDPin, HIGH);
  }
}

int counter = 0;
int button_State = 0;

static void button_evaluator(){
  //TODO: read if the button is pressed and if yes for how long
  //use milis myb??
  button_State = digitalRead(buttonPin);
  
  if(button_State == 1){
    Serial.println("Button pressed");
    if(counter == 0){
      button_counter();
    }
  }
}

static void LED_state(int state){
  /*//status machine that determines the behaviour of the LED
  switch(state){
    case 1: //nothings happening
      digitalWrite(LEDPin, LOW);
      break;
    case 2:
      //use millis to make it blink
      break;
  }*/
}

static void GPS_module(){
  //Process incoming GPS data
  smartDelay(1000);

  //prints of the data of the GPS
  printInt(gps.satellites.value(), gps.satellites.isValid(), 5);
  printFloat(gps.hdop.hdop(), gps.hdop.isValid(), 6, 1);
  printFloat(gps.location.lat(), gps.location.isValid(), 11, 6);
  printFloat(gps.location.lng(), gps.location.isValid(), 12, 6);
  printInt(gps.location.age(), gps.location.isValid(), 5);
  printDateTime(gps.date, gps.time);
  printFloat(gps.altitude.meters(), gps.altitude.isValid(), 7, 2);
  printFloat(gps.course.deg(), gps.course.isValid(), 6, 2);
  printFloat(gps.speed.kmph(), gps.speed.isValid(), 6, 2);
  printStr(gps.course.isValid() ? TinyGPSPlus::cardinal(gps.course.deg()) : "*** ", 6);

  Serial.println(); //idk why this is here but the program breaks if it's not
}

static void smartDelay(unsigned long ms){
  unsigned long start = millis();
  do{
    while(GPS_Serial.available()){
      gps.encode(GPS_Serial.read());
    } 

  }while(millis() - start < ms);
}

static void printFloat(float val, bool valid, int len, int prec){
  if(!valid){
    while(len-- > 1){
      Serial.print('*');
    }
    Serial.print(' ');
  } else {
    Serial.print(val, prec);
    int vi = abs((int) val);
    int flen = prec + (val < 0.0 ? 2 : 1);
    flen += vi >= 1000 ? 4 : vi >= 100 ? 3 : vi >= 10 ? 2 : 1; //if vi >= 1000, add 4 to current flen, else if vi>100, add 3 to current flen, else if vi >= 10, add 2 else add 1
    
    for(int i = flen ; i < len ; i++){
      Serial.print(' ');
    }
  }
}

static void printInt(unsigned long val, bool valid, int len){
  char sz[32] = "*****************";
  if(valid){
    sprintf(sz, "%lu", val);
  }
  sz[len] = 0;
  for(int i = strlen(sz); i < len; i++){
    sz[i] = ' '; 
  }
  if(len > 0){
    sz[len-1] = ' ';
  } 
  Serial.print(sz);
}

static void printDateTime(TinyGPSDate &d, TinyGPSTime &t){
  if (!d.isValid()){
    Serial.print(F("********** "));
  }
  else{
    char sz[32];
    sprintf(sz, "%02d/%02d/%02d ", d.month(), d.day(), d.year());
    Serial.print(sz);
  }
  if (!t.isValid()){
    Serial.print(F("******** "));
  }
  else{
    char sz[32];
    sprintf(sz, "%02d:%02d:%02d ", t.hour(), t.minute(), t.second());
    Serial.print(sz);
  }
  printInt(d.age(), d.isValid(), 5);
}

static void printStr(const char *str, int len){
  int slen = strlen(str);
  for (int i=0; i<len; ++i){
    Serial.print(i<slen ? str[i] : ' ');
  }
}

int state = 1;
static void change_LED_state(){
  digitalWrite(LEDPin, !state);
}

static void readbluetooth(){
  /*OPTIONAL - outgoing data to bluetooth
  if(Serial.available()) {
    SerialBT.write(Serial.read());
  }
  */
  //incoming data from bluetooth
  if(SerialBT.available()) {
    Serial.write(SerialBT.read()); //reads from bluetooth and imediatly writes it to Serial
    command = SerialBT.read();

    Serial.println("received command");
    Serial.println(command);
    switch(command){
      case -1:
        change_LED_state();
        break;
      case 2:
        change_LED_state();
        break;
      case 50:
        change_LED_state();
        break;
    }
  }
}
