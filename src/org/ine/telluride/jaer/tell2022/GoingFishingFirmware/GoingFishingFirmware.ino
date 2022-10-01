// firmware for Arduino Nano used for Gone Fishing Robot
// Tobi Delbruck, Aug/Sept 2023 tobi@ini.uzh.ch

#include <Servo.h>

Servo theta;
Servo z;
//union Adc {unsigned int adcVal; byte adcBuf[2];};
//union Adc adc;
const int FISHING_POND_MOTOR_NOT_SHUTDOWN = 11; // goes to not shutdown pin on LP38841T-1.5 regulator
const byte CMD_SET_SERVOS = 0, CMD_DISABLE_SERVOS = 1, CMD_RUN_POND = 2, CMD_STOP_POND = 3;
const int ADC_CHANGE_THRESHOLD = 1;
int lastAdcVal;

bool disabled = true;
byte bytes[4];
bool led = 0;

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(FISHING_POND_MOTOR_NOT_SHUTDOWN, OUTPUT);
  digitalWrite(FISHING_POND_MOTOR_NOT_SHUTDOWN, LOW); // turn off pond when starting
  //  pinMode(3, INPUT);
  Serial.begin(115200);
  theta.attach(12);
  z.attach(9);
  digitalWrite(LED_BUILTIN, HIGH);
  theta.write(90);
  z.write(90);
  delay(300);
  theta.detach();
  z.detach();
  digitalWrite(LED_BUILTIN, LOW);
}

void loop() {
  if (Serial.availableForWrite() >= 2) {
    int adcVal = analogRead(3);
    int change = adcVal - lastAdcVal;
    if (change >= ADC_CHANGE_THRESHOLD || change <= -ADC_CHANGE_THRESHOLD) {
      byte msb = adcVal / 256;
      Serial.write(msb);
      byte lsb = adcVal % 256;
      Serial.write(lsb);
      lastAdcVal = adcVal;
    }
  }

  if (Serial.available() < 4) {
    return;
  }
  // bytes sent
  // 0: cmd
  // 1-2: msb,lsb of theta pan servo in us from 1000-2000
  // 3: angle in deg of z servo (rod dip)
  Serial.readBytes(bytes, 4);
  byte cmd = bytes[0];
  bool disable = false;
  switch (cmd) {
    case CMD_SET_SERVOS:
      {
        if (disabled) {
          disabled = false;
          theta.attach(12);
          z.attach(9);
        }
        int thetaUs = (bytes[1] * 256) + (bytes[2]);
        theta.writeMicroseconds(thetaUs);

        int zDeg = bytes[3];
        z.write(zDeg);
      }
      break;
    case CMD_DISABLE_SERVOS:
      {
        disabled = true;
        theta.detach();
        z.detach();
      }
      break;
    case CMD_RUN_POND: {
        digitalWrite(FISHING_POND_MOTOR_NOT_SHUTDOWN, HIGH);
      }
      break;
    case CMD_STOP_POND: {
        digitalWrite(FISHING_POND_MOTOR_NOT_SHUTDOWN, LOW);
      }
      break;
  }
  led = !led;
  digitalWrite(LED_BUILTIN, led);

}
