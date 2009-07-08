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
                double dist = (xHorizontal ? Math.abs(m * event.x - event.y + b) : Math.abs(m * event.y - event.x + b)) / Math.sqrt(m*m + 1);
                //System.out.println("m: " + m + " md: " + maxDist + " d: " + dist);
                updateCoefficients(event.x, event.y, 0.01 * (maxDist - dist)/maxDist);
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


        double xBottomIntercept,xTopIntercept,yLeftIntercept,yRightIntercept;

        synchronized(lock) {
            if(xHorizontal) {
                xBottomIntercept = -b/m;
                xTopIntercept = (chip.getSizeY() - b)/m;
                yLeftIntercept = b;
                yRightIntercept = m*chip.getSizeX() + b;
            } else {
                xBottomIntercept = b;
                xTopIntercept = m*chip.getSizeY() + b;
                yLeftIntercept = -b/m;
                yRightIntercept = (chip.getSizeX() - b)/m;
            }
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

    }

    private void updateCoefficients(double x, double y, double weight) {
//        if(weight < 0.01)
//            weight = 0.01;
//        if(weight > 0.5)
//            weight = 0.5;
        double iWeight = 1.0 - weight;

        double var1 = xHorizontal ? x : y;
        double var2 = xHorizontal ? y : x;


        A = iWeight * A + weight * var1 * var1;
        B = iWeight * B + weight * 2 * var1;
        C = iWeight * C + weight;
        D = iWeight * D - weight * 2 * var1 * var2;
        E = iWeight * E - weight * 2 * var2;
        F = iWeight * F + weight * var2 * var2;

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
