
#include <TinyGPS++.h>

//IMPORTANT!!!!!
//It is assumed that it's used Hardware Serial 2


//TinyGPS stuff taken from the official TinyGPSPlusLibrary on github: https://github.com/MrRobotrick/GY-NEO6MV2_GPS_Module/tree/master

//pin-setup
//----GPS module pin----
static const int RXPin = 5;
static const int TXPin = 18;
static const uint32_t GPSBaud = 4800;

//----Button module pin----
static const int buttonPin = 0; //define
static const int LEDPin = 0; //define

int buttonState = 0;
int case_state = 0;

//The TinyGPS++ object
TinyGPSPlus gps;

//The serial connection to the GPS device
HardwareSerial GPS_Serial(2);

void setup() {
  Serial.begin(115200);
  GPS_Serial.begin(GPSBaud, SERIAL_8N1, RXPin, TXPin);

  //This is not really essential is it? It's just occupying space
  //Pin set-up
  pinMode(buttonPin, INPUT);
  pinMode(LEDPin, OUTPUT);

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

  switch(){
    case 1:
      
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

  //TODO: Make connection with the database to send the GPS data, and receive the necessary data to update the LED
}

static void button_evaluator(){
  //TODO: read if the button is pressed and if yes for how long
  //use milis myb??
}

static void LED_state(int state){
  //status machine that determines the behaviour of the LED
  switch(state){
    case 1: //nothings happening
      digitalWrite(LEDPin, LOW);
      break;
    case 2:
      //use millis to make it blink
      break;
  }
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

static void readbluethooth(){
  
}
