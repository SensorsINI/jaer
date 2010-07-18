package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.util.LinkedList;

/**
 * Measures lap times and lap count.
 */
class LapTimer {

    int lastSegment = Integer.MAX_VALUE;
    int startSegment = 0;
    int lastLapTime = 0;
    int lapCounter = 0;
    boolean initialized = false;
    int sumTime = 0;
    int bestTime = Integer.MAX_VALUE;

    class Lap {

        int laptimeUs = 0;

        public Lap(int timeUs) {
            this.laptimeUs = timeUs;
        }

        @Override
        public String toString() {
            return String.format("%.2fs", (float) laptimeUs / 1000000.0F);
        }
    }
    LinkedList<Lap> laps = new LinkedList();

    /** returns true if there was a new lap (crossed finish line - segment 0)
     *
     * @param newSegment - the current track segment spline point.
     * @param timeUs  - the time in us of this measurement.
     * @return true if we just crossed finish line.
     */
    boolean update(int newSegment, int timeUs) {
        if (!initialized  && newSegment==0) {
            lastSegment = 0;
            lastLapTime = timeUs;
            lapCounter = 0;
            startSegment = 0;
            initialized = true;
        } else if (newSegment != lastSegment) {
            // only when segment changes
            lastSegment = newSegment;
            if (newSegment == startSegment) {
                lapCounter++;
                int deltaTime = timeUs - lastLapTime;
                sumTime += deltaTime;
                if (deltaTime < bestTime) {
                    bestTime = deltaTime;
                }
                laps.add(new Lap(deltaTime));
                initialized = true;
                lastLapTime = timeUs;
                return true;
            }
        }
        return false;
    }

    Lap getLastLap() {
        if (laps.isEmpty()) {
            return null;
        }
        return laps.getLast();
    }

    void reset() {
        lapCounter = 0;
        initialized = false;
        laps.clear();
        lastSegment = Integer.MAX_VALUE;
        sumTime = 0;
        lapCounter = 0;
        bestTime = Integer.MAX_VALUE;
    }

    public String toString() {
        return String.format("Laps: %d\nAvg: %.2f, Best: %.2f, Last: %s", lapCounter, (float) sumTime * 1.0E-6F / lapCounter, (float) bestTime * 1.0E-6F, getLastLap());
    }
}
