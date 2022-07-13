#include <Servo.h>

Servo theta;
Servo z;

bool disabled = true;
byte bytes[3];

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
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
  while (Serial.available() < 3)
    return;
  if (Serial.readBytes(bytes, 3) < 3)
    return;
  bool disable = bytes[0];
  int thetaDeg = bytes[1];
  int zDeg = bytes[2];
  if (disable) {
    disabled = true;
    theta.detach();
    z.detach();
    digitalWrite(LED_BUILTIN, LOW);
  } else {
    if (disabled) {
      theta.attach(12);
      z.attach(9);
      disabled = false;
      digitalWrite(LED_BUILTIN, HIGH);
    }
    theta.write(thetaDeg);
    z.write(zDeg);
  }
}
