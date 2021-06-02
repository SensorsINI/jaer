package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import java.util.Arrays;
import java.util.Locale;

/**
 * Basic histogram functionality
 * @author rbodo
 */
public class Histogram {
    private final int NUM_BINS;
    private final float SIZE_BINS;
    private final float START;
    private int[] sampleCount;
    private int nTotal;
    private int nX;
    private int i;
    
    public Histogram(int start, int nBins, int sizeBins) {
        NUM_BINS = nBins;
        SIZE_BINS = sizeBins;
        START = start;
        reset();
    }
    
    public final void reset() {
        sampleCount = new int[NUM_BINS];
        nTotal = 0;
        nX = 0;
    }
    
    public void update(float datum) {
        nTotal++;
        for (i = 0; i < NUM_BINS; i++)
            if (datum < START + SIZE_BINS*i) {
                sampleCount[i]++;
                break;
            }
    }
    
    /**
     * Robustness Statistics.
     * @param X
     * @return percentage of data that has a value above x.
     */
    public float getPercentageAboveX(float X) {
        nX = 0;
        for (i = 0; i < NUM_BINS; i++)
            if (X <= START + SIZE_BINS*(i-1)) nX += sampleCount[i];
        return nTotal == 0 ? 0 : (float) 100*nX/nTotal;
    }
    
    @Override public String toString() {
        return String.format(Locale.ENGLISH,"Histogram of %1$d samples starting "
                            + "at %2$2.2f with step sizes of %3$6.2f: %4$s",
                             nTotal,START,SIZE_BINS,Arrays.toString(sampleCount));
    }
    
}
