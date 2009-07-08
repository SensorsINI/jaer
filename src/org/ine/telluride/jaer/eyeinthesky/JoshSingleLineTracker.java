package org.ine.telluride.jaer.eyeinthesky;

import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.FrameAnnotater;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GL;
import java.awt.*;

/**
 * User: jauerbac
 * Date: Jul 7, 2009
 * Time: 2:06:46 PM
 */
public class JoshSingleLineTracker extends EventFilter2D implements FrameAnnotater {

    private double A=1,B=1,C=1,D=1,E =1, F=1;
    private double m=1,b=1;

    private double center = 50;
    private double length = 50;    //really is a quarter of the length of the whole segment


    private boolean xHorizontal = true;

    private double maxDist;

    private final Object lock = new Object();

    public JoshSingleLineTracker(AEChip chip) {
        super(chip);
        maxDist = Math.sqrt(chip.getSizeX() * chip.getSizeX() + chip.getSizeY() * chip.getSizeY());
    }

    public Object getFilterState() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void resetFilter() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void initFilter() {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(maxDist == 0)
            maxDist = Math.sqrt(chip.getSizeX() * chip.getSizeX() + chip.getSizeY() * chip.getSizeY());
        for(BasicEvent event : in) {
//            if(((PolarityEvent) event).polarity == PolarityEvent.Polarity.On) {


                updateCoefficients(event.x, event.y);
//            }


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


//        double xBottomIntercept,xTopIntercept,yLeftIntercept,yRightIntercept;
        double xBottom,xTop,yLeft,yRight;

        double xCenter,yCenter;

        synchronized(lock) {
//            System.out.println(length);
            if(xHorizontal) {
                //xBottomIntercept = -b/m;
//                xTopIntercept = (chip.getSizeY() - b)/m;
//                yLeftIntercept = b;
//                yRightIntercept = m*chip.getSizeX() + b;

                xCenter = center;
                yCenter = m*center + b;
                xBottom = center - 2*length;
                xTop = center + 2*length;
                yLeft = m * xBottom + b;
                yRight = m * xTop + b;

            } else {
//                xBottomIntercept = b;
//                xTopIntercept = m*chip.getSizeY() + b;
//                yLeftIntercept = -b/m;
//                yRightIntercept = (chip.getSizeX() - b)/m;

                xCenter = m*center + b;
                yCenter = center;
                yLeft = center - 2*length;
                yRight = center + 2*length;
                xBottom = m * yLeft + b;
                xTop = m * yRight + b;

            }
        }

//        if(yLeftIntercept >= 0 && yLeftIntercept<=chip.getSizeY()) {
//            gl.glVertex2d(0, yLeftIntercept);
//        }
//        if(yRightIntercept >= 0 && yRightIntercept<=chip.getSizeY()) {
//            gl.glVertex2d(chip.getSizeX(), yRightIntercept);
//        }
//        if(xBottomIntercept > 0 && xBottomIntercept<chip.getSizeY()) {
//            gl.glVertex2d(xBottomIntercept, 0);
//        }
//        if(xTopIntercept > 0 && xTopIntercept < chip.getSizeY()) {
//            gl.glVertex2d(xTopIntercept, chip.getSizeY());
//        }

        gl.glVertex2d(xBottom < 0 ? 0 : (xBottom > chip.getSizeX() ? chip.getSizeX() : xBottom),
                yLeft < 0 ? 0 : (yLeft > chip.getSizeY() ? chip.getSizeY() : yLeft));

        gl.glVertex2d(xTop < 0 ? 0 : (xTop > chip.getSizeX() ? chip.getSizeX() : xTop),
                yRight < 0 ? 0 : (yRight > chip.getSizeY() ? chip.getSizeY() : yRight));

        gl.glEnd();

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

    }

    private void updateCoefficients(double x, double y) {

        double dist = (xHorizontal ? Math.abs(m * x - y + b) : Math.abs(m * y - x + b)) / Math.sqrt(m*m + 1);
        double weight = 0.01 * Math.pow((maxDist - dist)/maxDist,10);

//        System.out.println(weight);
        double iWeight = 1.0 - weight;

        double var1 = xHorizontal ? x : y;
        double var2 = xHorizontal ? y : x;


        A = iWeight * A + weight * var1 * var1;
        B = iWeight * B + weight * 2 * var1;
        C = iWeight * C + weight;
        D = iWeight * D - weight * 2 * var1 * var2;
        E = iWeight * E - weight * 2 * var2;
        F = iWeight * F + weight * var2 * var2;

        double center1 = m*center+b;
        if(xHorizontal)
            dist += Math.sqrt(Math.pow(x - center,2) + Math.pow(y - center1,2));
        else
            dist += Math.sqrt(Math.pow(y - center,2) + Math.pow(x - center1,2));

        weight = 0.01 * Math.pow((2*maxDist - dist)/(2*maxDist),2);
        iWeight = 1.0 - weight;


        center = iWeight * center + weight * var1;
        length = iWeight * length + weight * Math.abs(var1 - center);

        double denominator = 4*A*C - (B * B);

        b = (B*D - 2*A*E)/denominator;
        m = (B*E - 2*C*D)/denominator;

        synchronized(lock) {
            if(Math.abs(m) > 1.1) {
                System.out.print("FLIP! -- xHorizontal=" +xHorizontal+" m=" + m);

                double temp = A;
                A = F;
                F = temp;
                temp = B;
                B = -E;
                E = -temp;

                denominator = 4*A*C - (B * B);
                b = (B*D - 2*A*E)/denominator;
                m = (B*E - 2*C*D)/denominator;


//                    b = -b/m;
//                    m = 1.0/m;
                    System.out.println("\tm=" + m);

                xHorizontal = !xHorizontal;
            }
        }

    }


}
