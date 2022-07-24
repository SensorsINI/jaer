package org.ine.telluride.jaer.tell2022;

import java.io.Serializable;

public class RodPosition implements Serializable {

    int thetaDeg, zDeg;
    long delayMsToNext; // this is delay after we move to the position, first delay should be zero to immediately start the move

    public RodPosition(long delayMsToNext, int thetaDeg, int zDeg) {
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
        this.delayMsToNext = delayMsToNext;
    }

    @Override
    public String toString() {
        return String.format("theta=%d deg, z=%d deg, delayToNext=%,d ms", thetaDeg, zDeg, delayMsToNext);
    }
}
