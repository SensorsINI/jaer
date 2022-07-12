package org.ine.telluride.jaer.tell2022;

import java.io.Serializable;



public class RodPosition implements Serializable{

    int thetaDeg, zDeg;
    long timeMs;

    public RodPosition(long timeMs, int thetaDeg, int zDeg) {
        this.thetaDeg = thetaDeg;
        this.zDeg = zDeg;
        this.timeMs = timeMs;
    }
    
    @Override
    public String toString(){
        return String.format("theta=%d deg, z=%d deg, time=%,d ms", thetaDeg, zDeg, timeMs);
    }
}