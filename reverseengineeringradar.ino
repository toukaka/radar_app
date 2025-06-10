// Uncomment to enable debug messages
//#define DEBUG
#include <SoftwareSerial.h>

#define SENSOR1_ADDR 0b10001000
#define SENSOR2_ADDR 0b10001110
#define SENSOR3_ADDR 0b11101000
#define SENSOR4_ADDR 0b11101110


// SoftwareSerial BTSerial(0, 1); // RX, TX

const int inputPin = 3;

unsigned long pulseDuration = 0;

byte lowByte = 0;
byte highByte = 0;

void setup() {
  pinMode(inputPin, INPUT);
  Serial.begin(9600);
  // BTSerial.begin(9600);
}

void loop() {
  pulseDuration = pulseIn(inputPin, HIGH, 20000);

  if (pulseDuration > 990 && pulseDuration < 1020) {
    ignoreNextNPulses(6);

    byte DecodedAdressValue = decodeAddress();
    int sensorNumber = checkSensor(DecodedAdressValue);

    if (sensorNumber > 0) {
      uint8_t distance = readSensorData();

      // Store distances in static variables
      static uint8_t sensor1Distance = 0;
      static uint8_t sensor2Distance = 0;
      static uint8_t sensor3Distance = 0;
      static uint8_t sensor4Distance = 0;
      distance = calculateRiskPercentage(distance);
      // Update corresponding sensor distance
      if (sensorNumber == 1) sensor1Distance = distance;
      if (sensorNumber == 2) sensor2Distance = distance;
      if (sensorNumber == 3) sensor3Distance = distance;
      if (sensorNumber == 4) sensor4Distance = distance;

      // When sensor 4 is updated, print all distances on one line
      if (sensorNumber == 4) {
        String dataString = String(sensor1Distance) + "," + String(sensor2Distance) + "," + String(sensor3Distance) + "," + String(sensor4Distance);
        Serial.println(dataString);
        // BTSerial.print(dataString);
      }
    }
  }
}


// ================= Helper Functions ===================


int calculateRiskPercentage(float distanceCm) {
  if (distanceCm == 0) {
    distanceCm += 30;  // Ajoute 30 cm si distance = 0
  }

  int risk = 113.333 - 0.4444 * distanceCm;

  if (risk > 100) {
    risk = 100.0;
  } else if (risk < 0) {
    risk = 0;
  }

  return risk;
}


void ignoreNextNPulses(int N) {
  for (int i = 0; i < N; i++) {
    pulseIn(inputPin, HIGH);
  }
}

byte decodeAddress() {
  unsigned long addr_lsb = pulseIn(inputPin, HIGH, 2000);
  if (addr_lsb > 75 && addr_lsb < 90) {
    lowByte = 0b1000;
  } else if (addr_lsb > 240 && addr_lsb < 260) {
    lowByte = 0b1110;
  }

  unsigned long addr_msb = pulseIn(inputPin, HIGH, 20000);
  if (addr_msb > 75 && addr_msb < 90) {
    highByte = 0b1000;
  } else if (addr_msb > 230 && addr_msb < 260) {
    highByte = 0b1110;
  }

  byte address = (lowByte << 4) | (highByte & 0x0F);

#ifdef DEBUG
  Serial.print("Sensor Address: ");
  for (int i = 7; i >= 0; i--) {
    Serial.print(bitRead(address, i));
  }
  Serial.println();
#endif

  return address;
}

int checkSensor(byte DecodedAdressValue) {
  switch (DecodedAdressValue) {
    case SENSOR1_ADDR: return 1;
    case SENSOR2_ADDR: return 2;
    case SENSOR3_ADDR: return 3;
    case SENSOR4_ADDR: return 4;
    default:
      Serial.println("Unknown sensor address");
      return 0;
  }
}

uint8_t readSensorData() {
  uint8_t packet = 0;

  for (int i = 0; i < 8; i++) {
    pulseDuration = pulseIn(inputPin, HIGH, 3000);

    if (pulseDuration > 230 && pulseDuration < 260) {
      bitSet(packet, i);
    } else {
      bitClear(packet, i);
    }
  }

  return reverseBits(packet);
}

byte reverseBits(byte b) {
  b = (b & 0xF0) >> 4 | (b & 0x0F) << 4;
  b = (b & 0xCC) >> 2 | (b & 0x33) << 2;
  b = (b & 0xAA) >> 1 | (b & 0x55) << 1;
  return b;
}
