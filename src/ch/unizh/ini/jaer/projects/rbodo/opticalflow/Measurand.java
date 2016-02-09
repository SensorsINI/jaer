package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

/**
 * Tracks mean and variance statistics for optical flow methods.
 * 
 * @author rbodo
 */
public class Measurand {
    // Number of samples.
    public int n;

    // Current mean.
    private float mean; 

    // Sum of the square of the differences of the datum from the current mean.
    private float sumSqDiff;

    private float delta;

    public float getMean()   {return mean;}
    public float getStdDev() {return (float) Math.sqrt(sumSqDiff/(n-1));}
    public float getStdErr() {return (float) Math.sqrt(sumSqDiff/((n-1)*n));}

    public void reset() {
        n = 0;
        mean = 0;
        sumSqDiff = 0;
        delta = 0;
    }

    public void update(float x) {
        n++;
        delta = x - mean;
        mean += delta/n;
        sumSqDiff += delta*(x - mean);
    }

    @Override public String toString() {
        return String.format("Mean %8.2f +/- %6.2f (SD) of %4d samples ", mean, getStdDev(), n);
    }
}