package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

/**
 * Tracks mean and variance statistics for optical flow methods.
 *
 * @author rbodo / tobi delbruck
 */
public class Measurand {

    /** Number of samples. */
    private int n=0;

    /** Current sum of values */
    private float sum=0;

    // Sum of the square values.
    private float sum2=0;

    // sum of absolute deviations from mean, for more robust statistic
    private float sumdev=0;
    
    public float getMean() {
        return sum / n;
    }

    public float getStdDev() {
        float m=getMean();
        return (float) Math.sqrt((sum2 /n)- m*m);
    }
    
    /** Computes the mean absolute deviation of samples from mean value 
     * 
     * @return the mean absolute deviation
     */
    public float getMeanAbsDev(){
        return sumdev/n;
    }

    public void reset() {
        n = 0;
        sum = 0;
        sum2 = 0;
        sumdev=0;
    }

    public void update(float x) {
    // another possibility is to compute running average with https://stackoverflow.com/questions/28820904/how-to-efficiently-compute-average-on-the-fly-moving-average
        n++;
        sum += x;
        sum2 += x * x;
        sumdev+=Math.abs(x-getMean());
    }

    @Override
    public String toString() {
        return String.format("Mean %8.2f +/- %6.2f (SD) of %4d samples ", getMean(), getStdDev(), n);
    }

    /**
     * @return the n
     */
    public int getN() {
        return n;
    }
}
