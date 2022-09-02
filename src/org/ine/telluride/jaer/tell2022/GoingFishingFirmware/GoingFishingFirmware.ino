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
byte bytes[3];

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
      lastAdcVal=adcVal;
    }
  }

  if (Serial.available() < 3) {
    return;
  }
  Serial.readBytes(bytes, 3);
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
        int thetaDeg = bytes[1];
        int zDeg = bytes[2];
        theta.write(thetaDeg);
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
        digitalWrite(LED_BUILTIN, HIGH);
      }
      break;
    case CMD_STOP_POND: {
        digitalWrite(FISHING_POND_MOTOR_NOT_SHUTDOWN, LOW);
        digitalWrite(LED_BUILTIN, LOW);
      }
      break;
  }
}
