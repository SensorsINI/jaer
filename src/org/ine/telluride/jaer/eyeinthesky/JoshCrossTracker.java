package org.ine.telluride.jaer.eyeinthesky;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GL;
import java.awt.*;

/**
 * User: jauerbac
 * Date: Jul 14, 2009
 * Time: 12:54:54 PM
 */
public class JoshCrossTracker extends EventFilter2D implements FrameAnnotater {

    private double coefficients[];
    private double m=1,x1=1, y1=1;

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
        coefficients = new double[9];
        for(int i = 0; i < coefficients.length; i++) { coefficients[i] = Math.random();}

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
            xBottomIntercept = x1;
            xTopIntercept = -m * chip.getSizeY() + x1;
            yLeftIntercept = x1 / m;
            yRightIntercept = -(chip.getSizeX() - x1)/m;
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
            xBottomIntercept = -y1/m;
            xTopIntercept = (chip.getSizeY() - y1)/m;
            yLeftIntercept = y1;
            yRightIntercept = m*chip.getSizeX() + y1;;
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
        double var1 = xHorizontal ? x : y;
        double var2 = xHorizontal ? y : x;


        double A1 = m;
        double B1 = -1;
        double C1 = y1;
        double A2 = 1;
        double B2 = m;
        double C2 = -x1;

        double x0 = (x1 - m*y1)/(1 + m*m);
        double y0 = m*x0+y1;

        double dist1 = Math.abs(A1 * var1 + B1 * var2 + C1)/Math.sqrt(A1*A1 + B1*B1);
        double dist2 = Math.abs(A2 * var1 + B2 * var2 + C2)/Math.sqrt(A2*A2 + B2*B2);

        double distC = Math.sqrt((x - x0) * (x - x0) + (y - y0) * (y - y0));


        if(dist1 <= dist2) {
            double weight = 0.01 * Math.pow((maxDist - distC)/maxDist,2);
            double iWeight = 1.0 - weight;

            coefficients[0] = iWeight * coefficients[0] + weight * var1 * var1;
            coefficients[1] = iWeight * coefficients[1] + weight * -2 * var1 * var2;
            coefficients[2] = iWeight * coefficients[2];
            coefficients[3] = iWeight * coefficients[3];
            coefficients[4] = iWeight * coefficients[4] + weight;
            coefficients[5] = iWeight * coefficients[5] + weight * -2 * var2;
            coefficients[6] = iWeight * coefficients[6];
            coefficients[7] = iWeight * coefficients[7] + weight * 2 * var1;
            coefficients[8] = iWeight * coefficients[8] + weight * var2 * var2;

//            m = -(2*coefficients[1]*coefficients[4] - coefficients[5]*coefficients[7])/(4*coefficients[0]*coefficients[4] - coefficients[7]*coefficients[7]);
//            y1 = -(2*coefficients[0]*coefficients[5] -coefficients[1]*coefficients[7])/(4*coefficients[0]*coefficients[4] - coefficients[7]*coefficients[7]);

        } else {
            double weight = 0.01 * Math.pow(((maxDist - distC)/maxDist) * ((maxDist - dist2)/maxDist),10);
            double iWeight = 1.0 - weight;

            coefficients[0] = iWeight * coefficients[0] + weight * var2 * var2;
            coefficients[1] = iWeight * coefficients[1] + weight * 2 * var1 * var2;
            coefficients[2] = iWeight * coefficients[2] + weight;
            coefficients[3] = iWeight * coefficients[3] + weight * -2 * var1;
            coefficients[4] = iWeight * coefficients[4];
            coefficients[5] = iWeight * coefficients[5];
            coefficients[6] = iWeight * coefficients[6] + weight * -2  * var2;
            coefficients[7] = iWeight * coefficients[7];
            coefficients[8] = iWeight * coefficients[8] + weight * var1 * var1;

//            m = -(2*coefficients[1]*coefficients[2] - coefficients[3]*coefficients[6])/(4*coefficients[0]*coefficients[2] - coefficients[6]*coefficients[6]);
//            x1 = -(2*coefficients[0]*coefficients[3] -  coefficients[1]*coefficients[6])/(4*coefficients[0]*coefficients[2] - coefficients[6]*coefficients[6]);

        }

        m = -(2*coefficients[1]*coefficients[2]*coefficients[4] - coefficients[2]*coefficients[5]*coefficients[7] - coefficients[3]*coefficients[4]*coefficients[6])/(4*coefficients[0]*coefficients[2]*coefficients[4] - coefficients[2]*coefficients[7]*coefficients[7] - coefficients[4]*coefficients[6]*coefficients[6]);
        x1 = -1.0/2.0*(4*coefficients[0]*coefficients[3]*coefficients[4] - 2*coefficients[1]*coefficients[4]*coefficients[6] - coefficients[3]*Math.pow(coefficients[7],2) + coefficients[5]*coefficients[6]*coefficients[7])/(4*coefficients[0]*coefficients[2]*coefficients[4] - coefficients[2]*Math.pow(coefficients[7],2) - coefficients[4]*Math.pow(coefficients[6],2));
        y1 = -1.0/2.0*(4*coefficients[0]*coefficients[2]*coefficients[5] - coefficients[5]*Math.pow(coefficients[6],2) - (2*coefficients[1]*coefficients[2] - coefficients[3]*coefficients[6])*coefficients[7])/(4*coefficients[0]*coefficients[2]*coefficients[4] - coefficients[2]*Math.pow(coefficients[7],2) - coefficients[4]*Math.pow(coefficients[6],2));

        if(Math.abs(m) > 1.5) {
//                log.info("FLIP! -- xHorizontal=" +xHorizontal+" m=" + m);
                m = -1.0/m;
                double temp = y1;
                y1 = x1;
                x1 = temp;

                xHorizontal = !xHorizontal;
            }

//        log.info("m = " + m + ", x1 = " + x1 + ", y1 = " + y1);

        }


}

