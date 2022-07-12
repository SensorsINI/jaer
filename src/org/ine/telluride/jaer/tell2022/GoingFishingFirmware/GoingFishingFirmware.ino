#include <Servo.h>

Servo theta;
Servo z;

bool disabled = true;
byte bytes[2];

void setup() {
  theta.attach(12);
  z.attach(9);

  Serial.begin(115200);
  delay(1000);
  theta.detach();
  z.detach();

}

void loop() {
  if (Serial.readBytes(bytes, 2) == 2) {
    int thetaDeg = bytes[0];
    int zDeg = bytes[1];
    if (thetaDeg = 0 && zDeg == 0) {
      disabled = true;
      theta.detach();
      z.detach();
    } else {
      if (disabled) {
        theta.attach(12);
        z.attach(9);
        disabled = false;
      }
      theta.write(thetaDeg);
      z.write(zDeg);
    }
  }
}
