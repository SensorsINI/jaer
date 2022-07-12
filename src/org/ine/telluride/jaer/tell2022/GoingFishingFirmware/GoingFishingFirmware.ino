#include <Servo.h>

Servo theta;
Servo z;

bool disabled = true;
byte bytes[3];

void setup() {
  theta.attach(12);
  z.attach(9);

  Serial.begin(115200);
  delay(1000);
  theta.detach();
  z.detach();

}

void loop() {
  while (Serial.available() < 3)
    return;
  if (Serial.readBytes(bytes, 3) == 3) {
    bool disable = bytes[0];
    int thetaDeg = bytes[1];
    int zDeg = bytes[2];
    if (disable) {
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
