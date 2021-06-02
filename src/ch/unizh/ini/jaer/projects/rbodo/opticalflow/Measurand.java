package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import java.awt.geom.Point2D;
import java.util.Random;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Tracks mean and variance statistics for optical flow methods.
 *
 * @author rbodo / tobi delbruck
 */
public class Measurand extends DescriptiveStatistics {

    public static final int WINDOW_SIZE=10000;
    public Measurand() {
        setWindowSize(WINDOW_SIZE);
    }

    @Override
    public String toString() {
        return String.format("Mean %8.2f [%.2f,%.2f] (25%%-75%%) of %4d samples ", getMean(), getPercentile(25), getPercentile(75), getN());
    }

    /**
     * Returns the 25% and 75% percentile values, i.e. between these two values,
     * 50% of the data falls
     *
     * @return x=25%, y=75%
     */
    public Point2D.Float getQuartileErrors() {
        return new Point2D.Float((float) getPercentile(25), (float) getPercentile(75));
    }

    public String graphicsString(String header, String units) {
        Point2D.Float q = getQuartileErrors();
        return String.format("%s %4.2f [%.2f,%.2f] %s", header, getMean(), q.x, q.y, units);
    }

    public static void main(String[] args) {
        Measurand m = new Measurand();
        Random r=new Random();
        for(int i=0;i<1000;i++){
            float v=r.nextFloat();
            m.addValue(v);
//            System.out.print(v+" ");
        }
        for(float p=10;p<=100;p+=10){
            System.out.println(String.format("%f %f",p,m.getPercentile(p)));
        }
        System.out.println("\n"+m.toString());
    }

}
