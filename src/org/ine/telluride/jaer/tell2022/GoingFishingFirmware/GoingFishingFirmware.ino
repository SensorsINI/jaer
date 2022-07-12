#include <Servo.h>

Servo theta;
Servo z;

bool disabled = true;

void setup() {
  Serial.begin(115200);
}

void loop() {
  if (Serial.available()==2) {
    int thetaDeg = Serial.read();
    int zDeg = Serial.read();
    if (thetaDeg = 0 && zDeg == 0) {
      disabled = true;
      theta.detach();
      z.detach();
    } else {
      if (disabled) {
        theta.attach(12);
        z.attach(9);
        disabled=false;
      }
      theta.write(thetaDeg);
      z.write(zDeg);
    }
  }
}
