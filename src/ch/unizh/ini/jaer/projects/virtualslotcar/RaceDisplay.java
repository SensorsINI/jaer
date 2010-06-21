/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.util.Observer;
import javax.media.opengl.*;
import java.awt.geom.Point2D;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * OpenGL display of race track
 * @author Michael Pfeiffer
 */
public class RaceDisplay extends GLCanvas implements GLEventListener {

    // The race track to display
    SlotcarTrack raceTrack;

    // The car object
    Slotcar myCar = null;

    // Step size of track
    double stepsize = 0.01;

    public RaceDisplay(GLCapabilities caps) {
        super(caps);

        raceTrack = null;

        this.setLocale(java.util.Locale.US); // to avoid problems with other language support in JOGL

        // this.setSize(300,200);
        this.setVisible(true);

        addGLEventListener(this);

    }

    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
            System.out.println("displayChanged");
    }

    public synchronized void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        System.out.println("reshape");
    }

    public synchronized void display(GLAutoDrawable drawable) {
        System.out.println("display");
        if (raceTrack != null) {
            LinkedList<Point2D> allpoints = raceTrack.getSmoothPoints(stepsize);
            ListIterator<Point2D> it = allpoints.listIterator();

            GL gl = this.getGL();
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        
            gl.glLineWidth(1.0f);
            gl.glColor3f(0,1,0);
            gl.glBegin(GL.GL_LINE_LOOP);


            while (it.hasNext()) {
                Point2D p = it.next();
                gl.glVertex2d(2*p.getX()-1, 2*p.getY()-1);
            }
            gl.glEnd();
  
            System.out.println("Before car display");
            if (myCar != null) {
                // Move car
                myCar.drive();
                // Display car
                myCar.displayCar(gl);
            }

            gl.glFlush();
        }

    }
    public void init(GLAutoDrawable drawable) {

        System.out.println("init");

        GL gl = getGL();

        gl.setSwapInterval(1);
        gl.glShadeModel(GL.GL_FLAT);

        gl.glClearColor(0, 0, 0, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glLoadIdentity();

        gl.glRasterPos3f(0, 0, 0);
        gl.glColor3f(1, 1, 1);
    }

    /**
     * Sets a new track for the race.
     * @param newTrack The new race track
     */
    public void setTrack(SlotcarTrack newTrack, double stepsize) {
        raceTrack = newTrack;
        this.stepsize = stepsize;
    }


    /**
     * Defines the driver and car for this race
     * @param myCar The new car.
     */
    public void setCar(Slotcar myCar) {
        System.out.println("Setting Car!");
        this.myCar = myCar;
        myCar.setGL(getGL());
    }
}
