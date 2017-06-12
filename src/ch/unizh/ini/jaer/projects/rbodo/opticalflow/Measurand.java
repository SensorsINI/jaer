package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

/**
 * Tracks mean and variance statistics for optical flow methods.
 *
 * @author rbodo
 */
public class Measurand {

    /** Number of samples. */
    private int n;

    /** Current mean. */
    private float sum;

    // Sum of the square values.
    private float sum2;

    public float getMean() {
        return sum / n;
    }

    public float getStdDev() {
        float m=getMean();
        return (float) Math.sqrt((sum2 /n)- m*m);
    }

    public void reset() {
        n = 0;
        sum = 0;
        sum2 = 0;
    }

    public void update(float x) {
        n++;
        sum += x;
        sum2 += x * x;
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

    /**
     * @param n the n to set
     */
    private void setN(int n) {
        this.n = n;
    }
}
