/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay;

import java.io.Serializable;

/**
 *
 * @author Bjoern
 */
public class MouseTrajectoryPoint implements Serializable{
    private final long timeElapsedNanos, timeDifferenceToLastNanos;
    private final float x, y;

    public MouseTrajectoryPoint(long timeElapsedNanos, long timeDifferenceToLastNanos, float x, float y) {
        this.timeElapsedNanos = timeElapsedNanos;
        this.timeDifferenceToLastNanos = timeDifferenceToLastNanos;
        this.x = x;
        this.y = y;
    }

    public long getElapsedTime() { return timeElapsedNanos; }
    public long getDifferenceTimeNanos() { return timeDifferenceToLastNanos; }
    public long getDifferenceTimeMillis() { return (long) (timeDifferenceToLastNanos/1e6); }
    public float getX() { return x; }
    public float getY() { return y; }
}
