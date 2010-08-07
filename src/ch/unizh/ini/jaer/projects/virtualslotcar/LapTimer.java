package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;

/**
 * Measures lap times and lap count.
 */
class LapTimer implements PropertyChangeListener {

    SlotcarTrack track;
    int lastSegment = Integer.MAX_VALUE;
    int startSegment = 0;
    int lastLapTime = 0;
    int lapCounter = 0;
    boolean initialized = false;
    int sumTime = 0;
    int bestTime = Integer.MAX_VALUE;
    int quarters = 0;
    private static final int MAX_LAPS_TO_STORE=15;

    /** Constructs a new LapTimer for a track with numSegments points.
     * 
     * @param numSegments
     */
    public LapTimer(SlotcarTrack track) {
        this.track = track;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(SlotcarTrack.EVENT_TRACK_CHANGED)) {
            track = (SlotcarTrack) evt.getNewValue();
        }
    }

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
        boolean ret = false;
        if (track == null) {
            return false;
        }
        if (!initialized ) {
            quarters=(4*newSegment)/track.getNumPoints();
            lastSegment = newSegment;
            lastLapTime = timeUs;
            lapCounter = 0;
            startSegment = 0;
            initialized = true;
        } else if (newSegment != lastSegment) {
            final int numPoints = track.getNumPoints();
            if ( (quarters <= 3 && newSegment >= (numPoints * quarters) / 4)  ||  (quarters==4 && newSegment < numPoints /  4 ) ) {
                quarters++;
                if (quarters > 4) {
                    quarters = 0;
                    lapCounter++;
                    int deltaTime = timeUs - lastLapTime;
                    sumTime += deltaTime;
                    if (deltaTime < bestTime) {
                        bestTime = deltaTime;
                    }
                    laps.add(new Lap(deltaTime));
                    if(laps.size()>MAX_LAPS_TO_STORE) laps.removeFirst();
                    initialized = true;
                    lastLapTime = timeUs;
                    ret = true;
                }
            }
            // only when segment changes
            lastSegment = newSegment;
        }
        return ret;
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
        quarters = 0;
    }

    public String toString() {
        StringBuilder  sb=new StringBuilder();
       sb.append(String.format("Laps: %d %d/4\nAvg: %.2f, Best: %.2f, Last: %s",
                lapCounter, quarters-1,
                (float) sumTime * 1.0E-6F / lapCounter,
                (float) bestTime * 1.0E-6F,
                getLastLap())
                );
       int count=0;
       for(Lap l:laps){
           sb.append(String.format("\n%3d: %8.3f",count++,l.laptimeUs*1e-6f));
       }
       return sb.toString();
    }
}
