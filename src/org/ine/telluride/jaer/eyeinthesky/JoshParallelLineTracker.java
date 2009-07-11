package org.ine.telluride.jaer.eyeinthesky;

import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.BasicEvent;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GL;
import java.awt.*;

/**
 * User: jauerbac
 * Date: Jul 7, 2009
 * Time: 2:06:46 PM
 */
public class JoshParallelLineTracker extends EventFilter2D implements FrameAnnotater {

    private double A[], B[], C[], D[], E[], F[];
    private double m;
    private double b[];


    private boolean xHorizontal;

    private double maxDist;

    private final Object lock = new Object();

    public JoshParallelLineTracker(AEChip chip) {
        super(chip);
        maxDist = Math.sqrt(chip.getSizeX() * chip.getSizeX() + chip.getSizeY() * chip.getSizeY());
    }

    public Object getFilterState() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void resetFilter() {
        log.info("RESETTING RESETTING RESETTING RESETTING RESETTING");
        A = new double[]{1,1};
        B = new double[]{1,1};
        C = new double[]{1,1};
        D = new double[]{1,1};
        E = new double[]{1,1};
        F = new double[]{1,1};
        m = 0.0001;
        b = new double[]{0, chip.getSizeY()};
        xHorizontal = true;
    }

    public void initFilter() {
        resetFilter();
    }


    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(maxDist == 0)
            maxDist = Math.sqrt(chip.getSizeX() * chip.getSizeX() + chip.getSizeY() * chip.getSizeY());
        for(BasicEvent event : in) {

                double dist[] = new double[2];
                for(int i = 0; i<2; i++)
                    dist[i] = ((xHorizontal ? Math.abs(m * event.x - event.y + b[i]) : Math.abs(m * event.y - event.x + b[i])) / Math.sqrt(m*m + 1))/maxDist;


                boolean coinFlip = Math.random() < 0.5;


                if(dist[0] <= dist[1] && (dist[0] < 0.1 || coinFlip))
                    updateCoefficients(event.x, event.y, 0, 0.01 * Math.pow( 1 - dist[0] , 10));
                else if(dist[1] < 0.1 || !coinFlip)
                    updateCoefficients(event.x, event.y, 1, 0.01 * Math.pow( 1 - dist[1] , 10));



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
        for(int i=0; i<b.length; i++) {

            gl.glBegin((GL.GL_LINES));
            gl.glColor3d(1, 0, 0);

            double xBottomIntercept,xTopIntercept,yLeftIntercept,yRightIntercept;

            synchronized(lock) {
                if(xHorizontal) {
                    xBottomIntercept = -b[i]/m;
                    xTopIntercept = (chip.getSizeY() - b[i])/m;
                    yLeftIntercept = b[i];
                    yRightIntercept = m*chip.getSizeX() + b[i];
                } else {
                    xBottomIntercept = b[i];
                    xTopIntercept = m*chip.getSizeY() + b[i];
                    yLeftIntercept = -b[i]/m;
                    yRightIntercept = (chip.getSizeX() - b[i])/m;
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



    }

    private void updateCoefficients(double x, double y, int num, double weight) {

        double iWeight = 1.0 - weight;

        double var1 = xHorizontal ? x : y;
        double var2 = xHorizontal ? y : x;


        A[num] = iWeight * A[num] + weight * var1 * var1;
        B[num] = iWeight * B[num] + weight * 2 * var1;
        C[num] = iWeight * C[num] + weight;
        D[num] = iWeight * D[num] - weight * 2 * var1 * var2;
        E[num] = iWeight * E[num] - weight * 2 * var2;
        F[num] = iWeight * F[num] + weight * var2 * var2;




        synchronized(lock) {
            if(Math.abs(m) > 1.1) {
                for(int i =0; i <b.length; i++) {
                    double temp = A[i];
                    A[i] = F[i];
                    F[i] = temp;
                    temp = B[i];
                    B[i] = -E[i];
                    E[i] = -temp;

                    double denominator = 4*A[i]*C[i] - (B[i] * B[i]);
                    b[i] = (B[i]*D[i] - 2*A[i]*E[i])/denominator;
                }

                xHorizontal = !xHorizontal;
            } else {
                double denominator = 4*A[num]*C[num] - (B[num] * B[num]);

                b[num] = (B[num]*D[num] - 2*A[num]*E[num])/denominator;
            }

            double mTotal = 0;
            for(int i=0; i<b.length; i++) {
                mTotal += (B[i]*E[i] - 2*C[i]*D[i])/ (4*A[i]*C[i] - (B[i] * B[i]));
            }
            m = mTotal/b.length;

        }

    }


}
