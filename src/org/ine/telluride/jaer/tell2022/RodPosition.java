package org.ine.telluride.jaer.tell2022;

import java.io.Serializable;

public class RodPosition implements Serializable {

    int thetaDeg, zDeg;
    long timeMs;

    public RodPosition(long timeMs, int thetaDeg, int zDeg) {
        if (thetaDeg > 180) {
            thetaDeg = 180;
        } else if (thetaDeg < 0) {
            thetaDeg = 0;
        }
        if (zDeg > 180) {
            zDeg = 180;
        } else if (zDeg < 0) {
            zDeg = 0;
        }
        this.thetaDeg = thetaDeg;
        this.zDeg = zDeg;
        this.timeMs = timeMs;
    }

    @Override
    public String toString() {
        return String.format("theta=%d deg, z=%d deg, time=%,d ms", thetaDeg, zDeg, timeMs);
    }
}
