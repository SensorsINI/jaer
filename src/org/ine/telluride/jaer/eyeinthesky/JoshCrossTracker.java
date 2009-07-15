package org.ine.telluride.jaer.eyeinthesky;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GL;
import java.awt.*;
import java.util.*;

/**
 * User: jauerbac
 * Date: Jul 14, 2009
 * Time: 12:54:54 PM
 */
public class JoshCrossTracker extends EventFilter2D implements FrameAnnotater {

    private static int WINDOW = 10000;

    private float threshold = getPrefs().getFloat("JoshCrossTracker.threshold", 0.5f);
    private int power = getPrefs().getInt("JoshCrossTracker.power", 8);
    private int ignoreRadius = getPrefs().getInt("JoshCrossTracker.ignoreRadius", 10);

    private double coefficients[][];
    private double m=1,x1=1, y1=1;

//    MyQueue line1Q = new MyQueue(WINDOW);
//    MyQueue line2Q = new MyQueue(WINDOW);

//    private double numEvents;
    private double center = 50;
    private double length = 50;    //really is a quarter of the length of the whole segment

    private boolean xHorizontal = true;
    private double maxDist;
    private final Object lock = new Object();

    public JoshCrossTracker(AEChip chip) {
        super(chip);

        resetFilter();
    }

    public Object getFilterState() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void resetFilter() {
        maxDist = Math.sqrt(128 * 128 + 128 * 128);
        coefficients = new double[3][9];
        for(int i = 0; i < coefficients.length; i++) {
            for(int j = 0; j < coefficients[i].length; j++) {
                coefficients[i][j] = Math.random();
            }
        }
//        numEvents = 0;

//        for(int i = 0; i < 10000; i++) {
//            double x = Math.random() * 128;
//
//            updateCoefficients(x,2*x - 3);
//            updateCoefficients(x,(x-4)/(-2));
//        }
    }

    public void initFilter() {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    public EventPacket<?> filterPacket(EventPacket<?> in) {
//        log.info("*************** FILTERING ********************");
        if(!isFilterEnabled()) return in;
        if(maxDist == 0)
            maxDist = Math.sqrt(chip.getSizeX() * chip.getSizeX() + chip.getSizeY() * chip.getSizeY());
        for(BasicEvent event : in) {
                updateCoefficients(event.x, event.y);
        }
        return in;

    }

    public void annotate(float[][][] frame) {}
    public void annotate(Graphics2D g) {}

    public void annotate(GLAutoDrawable drawable) {
         if (!isAnnotationEnabled()) {
            return;
        }
        GL gl = drawable.getGL();
        gl.glBegin((GL.GL_LINES));
        gl.glColor3d(1, 0, 0);


        double xBottomIntercept,xTopIntercept,yLeftIntercept,yRightIntercept;
//        double xBottom,xTop,yLeft,yRight;

        double xCenter,yCenter;

        if(xHorizontal) {
            xBottomIntercept = -y1/m;
            xTopIntercept = (chip.getSizeY() - y1)/m;
            yLeftIntercept = y1;
            yRightIntercept = m*chip.getSizeX() + y1;
        } else {
            xBottomIntercept = y1;
            xTopIntercept = m*chip.getSizeX() + y1;
            yLeftIntercept = -y1 / m;
            yRightIntercept = (chip.getSizeY() - y1)/m;
        }

        if(yLeftIntercept >= 0 && yLeftIntercept<=chip.getSizeY()) {
            gl.glVertex2d(0, yLeftIntercept);
        }
        if(yRightIntercept >= 0 && yRightIntercept<=chip.getSizeY()) {
            gl.glVertex2d(chip.getSizeX(), yRightIntercept);
        }
        if(xBottomIntercept > 0 && xBottomIntercept<chip.getSizeY()) {
            gl.glVertex2d(xBottomIntercept, 0);
        }
        if(xTopIntercept > 0 && xTopIntercept < chip.getSizeY()) {
            gl.glVertex2d(xTopIntercept, chip.getSizeY());
        }
        gl.glEnd();

        gl.glBegin((GL.GL_LINES));
        gl.glColor3d(0, 0, 1);

        if(xHorizontal) {
            xBottomIntercept = x1;
            xTopIntercept = -m * chip.getSizeY() + x1;
            yLeftIntercept = x1 / m;
            yRightIntercept = -(chip.getSizeX() - x1)/m;
        } else {
            xBottomIntercept = x1/m;
            xTopIntercept = -(chip.getSizeX() - x1)/m;
            yLeftIntercept = x1;
            yRightIntercept = -m * chip.getSizeY() + x1;
        }

        if(yLeftIntercept >= 0 && yLeftIntercept<=chip.getSizeY()) {
            gl.glVertex2d(0, yLeftIntercept);
        }
        if(yRightIntercept >= 0 && yRightIntercept<=chip.getSizeY()) {
            gl.glVertex2d(chip.getSizeX(), yRightIntercept);
        }
        if(xBottomIntercept > 0 && xBottomIntercept<chip.getSizeY()) {
            gl.glVertex2d(xBottomIntercept, 0);
        }
        if(xTopIntercept > 0 && xTopIntercept < chip.getSizeY()) {
            gl.glVertex2d(xTopIntercept, chip.getSizeY());
        }
        gl.glEnd();
        double x0,y0;
        x0 = (x1 - m*y1)/(1 + m*m);
        y0 = m*x0+y1;
//        drawCircle(gl, xHorizontal ? x0 : y0, xHorizontal ? y0 : x0);




/*
        gl.glVertex2d(xBottom < 0 ? 0 : (xBottom > chip.getSizeX() ? chip.getSizeX() : xBottom),
                yLeft < 0 ? 0 : (yLeft > chip.getSizeY() ? chip.getSizeY() : yLeft));

        gl.glVertex2d(xTop < 0 ? 0 : (xTop > chip.getSizeX() ? chip.getSizeX() : xTop),
                yRight < 0 ? 0 : (yRight > chip.getSizeY() ? chip.getSizeY() : yRight));
*/

/*
        gl.glBegin((GL.GL_LINES));
        gl.glColor3d(1, 0, 0);
        gl.glVertex2d(Math.max(xCenter - 5, 0), yCenter);
        gl.glVertex2d(Math.min(xCenter + 5, chip.getSizeX()), yCenter);
        gl.glEnd();
        gl.glBegin((GL.GL_LINES));
        gl.glColor3d(1, 0, 0);
        gl.glVertex2d(xCenter,Math.max(yCenter - 5, 0));
        gl.glVertex2d(xCenter,Math.min(yCenter + 5, chip.getSizeY()));
        gl.glEnd();
        */

    }

    private void updateCoefficients(double x, double y) {
//        numEvents++;
        double var1 = xHorizontal ? x : y;
        double var2 = xHorizontal ? y : x;


        double A1 = m;
        double B1 = -1;
        double C1 = xHorizontal ? y1 : x1;
        double A2 = 1;
        double B2 = m;
        double C2 = xHorizontal ? -x1 : -y1;
        double dist1 = Math.abs(A1 * var1 + B1 * var2 + C1)/Math.sqrt(A1*A1 + B1*B1);
        double dist2 = Math.abs(A2 * var1 + B2 * var2 + C2)/Math.sqrt(A2*A2 + B2*B2);

        double x0,y0;
        x0 = (x1 - m*y1)/(1 + m*m);
        y0 = m*x0+y1;
        double distC = Math.sqrt((x - x0) * (x - x0) + (y - y0) * (y - y0));

        if(distC < ignoreRadius || distC > 100)
            return;


//        double weight = 0.01 * Math.pow(gaussian(distC/maxDist,0,0.1),4);

//        if(numEvents > 900000)
//            threshold = 0.9;
//        else
//            threshold = numEvents/1000000;


//        if(numEvents % 10000 == 0)
//            log.info(threshold+"");

//        double theta = Math.atan(m);

//        double distX = Math.abs(((x-x0)*Math.cos(theta) - (y-y0)*Math.sin(theta)));
//        double distY = Math.abs(((x-x0)*Math.sin(theta) + (y-y0)*Math.cos(theta)));
        if(dist1 <= dist2) {
            double weight = 0.01 * Math.pow((maxDist - dist1)/maxDist,power);
//            if(((maxDist - distC)/maxDist) < threshold)
//                weight = 0;
            double iWeight = 1.0 - weight;

//            line1Q.offer(weight);

            //double denom = 1;//var1*var1 + 1;

            coefficients[2][0] = iWeight * coefficients[2][0] + (weight * var1 * var1) ;// denom;
            coefficients[2][1] = iWeight * coefficients[2][1] + (weight * -2 * var1 * var2) ;// denom;
            coefficients[2][2] = iWeight * coefficients[2][2];
            coefficients[2][3] = iWeight * coefficients[2][3];
            coefficients[2][4] = iWeight * coefficients[2][4] + weight ;// denom;
            coefficients[2][5] = iWeight * coefficients[2][5] + (weight * -2 * var2) ;// denom;
            coefficients[2][6] = iWeight * coefficients[2][6];
            coefficients[2][7] = iWeight * coefficients[2][7] + (weight * 2 * var1) ;// denom;
            coefficients[2][8] = iWeight * coefficients[2][8] + (weight * var2 * var2) ;// denom;
        } else {
            double weight = 0.01 * Math.pow((maxDist - dist2)/maxDist,power);
//            double weight = 0.01 * Math.pow((maxDist - distC)/maxDist,power);// * Math.pow((maxDist - dist2)/maxDist,power);
//            if(((maxDist - distC)/maxDist) < threshold)
//                weight = 0;
            double iWeight = 1.0 - weight;
//            line2Q.offer(weight);

            double denom = 1;//var2*var2 + 1;

            coefficients[2][0] = iWeight * coefficients[2][0] + (weight * var2 * var2) ;// denom;
            coefficients[2][1] = iWeight * coefficients[2][1] + (weight * 2 * var1 * var2) ;// denom;
            coefficients[2][2] = iWeight * coefficients[2][2] + weight / denom;
            coefficients[2][3] = iWeight * coefficients[2][3] + (weight * -2 * var1) ;// denom;
            coefficients[2][4] = iWeight * coefficients[2][4];
            coefficients[2][5] = iWeight * coefficients[2][5];
            coefficients[2][6] = iWeight * coefficients[2][6] + (weight * -2  * var2) ;// denom;
            coefficients[2][7] = iWeight * coefficients[2][7];
            coefficients[2][8] = iWeight * coefficients[2][8] + (weight * var1 * var1) ;// denom;
        }

//        if(Math.abs(m) > 1.5) {
//            log.info(("FLIP!"));
//
//            double temp = coefficients[0][0];
//            coefficients[0][0] = coefficients[0][8];
//            coefficients[0][8] = temp;
//            temp = coefficients[0][5];
//            coefficients[0][5] = -coefficients[0][7];
//            coefficients[0][7] = -temp;
//
//            temp = coefficients[1][0];
//            coefficients[1][0] = coefficients[1][8];
//            coefficients[1][8] = temp;
//            temp = coefficients[1][3];
//            coefficients[1][3] = coefficients[1][6];
//            coefficients[1][6] = temp;
//
//            xHorizontal = !xHorizontal;
//        }

//        double sum = line1Q.getSum() + line2Q.getSum();
//        double weight1 = 0.5;//line1Q.getSum()/sum;
//        double weight2 = 0.5;//line2Q.getSum()/sum;

//        for(int i = 0; i<coefficients[0].length; i++) {
//            coefficients[2][i] = weight1*coefficients[0][i] + weight2*coefficients[1][i];
//        }

        m = -(2*coefficients[2][1]*coefficients[2][2]*coefficients[2][4] - coefficients[2][2]*coefficients[2][5]*coefficients[2][7] - coefficients[2][3]*coefficients[2][4]*coefficients[2][6])/(4*coefficients[2][0]*coefficients[2][2]*coefficients[2][4] - coefficients[2][2]*coefficients[2][7]*coefficients[2][7] - coefficients[2][4]*coefficients[2][6]*coefficients[2][6]);
        x1 = -1.0/2.0*(4*coefficients[2][0]*coefficients[2][3]*coefficients[2][4] - 2*coefficients[2][1]*coefficients[2][4]*coefficients[2][6] - coefficients[2][3]*Math.pow(coefficients[2][7],2) + coefficients[2][5]*coefficients[2][6]*coefficients[2][7])/(4*coefficients[2][0]*coefficients[2][2]*coefficients[2][4] - coefficients[2][2]*Math.pow(coefficients[2][7],2) - coefficients[2][4]*Math.pow(coefficients[2][6],2));
        y1 = -1.0/2.0*(4*coefficients[2][0]*coefficients[2][2]*coefficients[2][5] - coefficients[2][5]*Math.pow(coefficients[2][6],2) - (2*coefficients[2][1]*coefficients[2][2] - coefficients[2][3]*coefficients[2][6])*coefficients[2][7])/(4*coefficients[2][0]*coefficients[2][2]*coefficients[2][4] - coefficients[2][2]*Math.pow(coefficients[2][7],2) - coefficients[2][4]*Math.pow(coefficients[2][6],2));
    }

    private static double ONE_OVER_SQRT_TWO_PI = 1.0/Math.sqrt(2*Math.PI);

    private double gaussian(double x, double mu, double sigma) {
        return ONE_OVER_SQRT_TWO_PI * (1.0/sigma) * Math.exp(-0.5*Math.pow(x-mu,2)/Math.pow(sigma,2));
    }

    private static void drawCircle( GL gl, double xc, double yc ) {
        double r = 2; // Radius.

        gl.glBegin( GL.GL_TRIANGLE_FAN );
        gl.glColor3d(0,1,1);

        gl.glVertex2d( xc, yc ); // Center.

        for(double a = 0;  a <= 360; a+=10 ) {
            double ang = Math.toRadians( a );
            double x = xc + (r*Math.cos( ang ));
            double y = yc + (r*Math.sin( ang ));
            gl.glVertex2d( x, y );
        }
        gl.glEnd();
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    public int getPower() {
        return power;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public int getIgnoreRadius() {
        return ignoreRadius;
    }

    public void setIgnoreRadius(int ignoreRadius) {
        this.ignoreRadius = ignoreRadius;
    }
/*
    private class MyQueue extends ArrayList<Double> implements java.util.Queue<Double> {
        private int maxCapacity;
        private int currentIndex;
        private double sum;

        public MyQueue(int maxCapacity) {
            super();
            this.maxCapacity = maxCapacity;
            for(int i = 0; i<maxCapacity; i++) {
                super.add(0.0);
            }
            currentIndex = 0;
            sum = 0;
        }

        public boolean offer(Double e) {
            sum -= super.get(currentIndex);
            sum += e;
            super.set(currentIndex, e);
            currentIndex = (currentIndex + 1) % maxCapacity;
            return true;
        }

        public Double remove() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Double poll() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Double element() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Double peek() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public double getSum() {
            return sum;
        }

    }
*/


}

