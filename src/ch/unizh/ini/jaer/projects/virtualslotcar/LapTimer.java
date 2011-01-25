package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Measures lap times and lap count.
 */
class LapTimer implements PropertyChangeListener {

    SlotcarTrack track;
    int lastSegment = Integer.MAX_VALUE;
    int startSegment = 0;
    int lastUpdateTime = 0;
    int lapCounter = 0;
    boolean initialized = false;
    int sumTime = 0;
    int bestTime = Integer.MAX_VALUE;
    int quarters = 0;
    private static final int MAX_LAPS_TO_STORE = 3;
    private int lapStartTime = 0;

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
        int[] splitsUs = new int[4];
        int quartersCompleted=0;

        public Lap() {
        }

        public Lap(int timeUs) {
            this.laptimeUs = timeUs;
        }

        public Lap(int[] splitsUs) {
            for (int i = 0; i < 4; i++) {
                this.splitsUs[i] = splitsUs[i];
                laptimeUs += splitsUs[i];
            }
        }

        @Override
        public String toString() {
            return String.format("%6.2f %6.2f %6.2f %6.2f : %7.3fs", split(0), split(1),split(2),split(3), laptime());
        }

        private float t(int t) {
            return (float) t * 1e-6f;
        }
        
        float laptime(){
            return 1e-6f*laptimeUs;
        }

        float split(int n){
            if(n<0 || n>quartersCompleted) return Float.NaN;
            else if(n==0) return 1e-6f*(splitsUs[0]);
            else return 1e-6f*(splitsUs[n]-splitsUs[n-1]);
        }

        void storeSplit(int quarter, int time){
           quartersCompleted=quarter;
           splitsUs[quarter]=time;
        }
    }
    LinkedList<Lap> laps = new LinkedList();
    Lap currentLap = new Lap();

    /** returns true if there was a new lap (crossed finish line - segment 0)
     *
     * @param newSegment - the current track segment spline point.
     * @param timeUs  - the time in us of this measurement.
     * @return true if we just crossed finish line.
     */
    boolean update(int newSegment, int timeUs) {
        int n = track.getNumPoints();
        boolean ret = false;
        if (track == null) {
            return false;
        }
        if (!initialized) {
            lastSegment = newSegment; // grab last segment
            initialized = true;
            return false;
        } else { // initialized
            if (lastSegment == newSegment) { // if segment doesn't change, don't do anything
                return false;
            } else if (quarters == 0 || quarters==4) { // if we haven't passed segment zero, then check if we have
                if (lastSegment >= (3 * n) / 4 && newSegment < n / 4) { // passed segment 0 (the start segment)
                    if (currentLap != null) {
                        currentLap.storeSplit(3, timeUs - lapStartTime);
                        lapCounter++;
                        int deltaTime = timeUs - lapStartTime;
                        currentLap.laptimeUs = deltaTime;
                        sumTime += deltaTime;
                        if (deltaTime < bestTime) {
                            bestTime = deltaTime;
                        }
                        lastUpdateTime = timeUs;
                       ret = true;
                   }
                    quarters = 1; //  next, look to pass 1st quarter of track
                    currentLap = new Lap();
                    laps.add(currentLap);
                    if (laps.size() > MAX_LAPS_TO_STORE) {
                        laps.removeFirst();
                    }
                    lastUpdateTime = timeUs;
                    lapStartTime = timeUs;
                    startSegment = 0;
                }
            } else if (quarters > 0 && quarters < 4) {
                if (newSegment >= (n * quarters) / 4 && newSegment <((n*(quarters+1))/4)) {
                    currentLap.storeSplit(quarters-1,  timeUs - lapStartTime);
                    quarters++;
                }
            }
            lastSegment = newSegment;
            return ret;
        }
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
        bestTime = Integer.MAX_VALUE;
        quarters = 0;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Laps: %d %d/4\nAvg: %.2f, Best: %.2f\n",
                lapCounter, quarters - 1,
                (float) sumTime * 1.0E-6F / lapCounter,
                (float) bestTime * 1.0E-6F)
                );
        int count = 0;

        Iterator<Lap> itr=laps.descendingIterator();
        while(itr.hasNext()){
            Lap l=itr.next();
            sb.append(String.format("\n%6d: %s", -(count++), l.toString()));
        }
        return sb.toString();
    }
}
