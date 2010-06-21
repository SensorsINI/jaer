/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import javax.media.opengl.*;
import java.awt.geom.Point2D;

/**
 * Implements the movement and drawing of a slotcar
 * @author Michael Pfeiffer
 */
public class Slotcar implements Runnable {

    // The race track
    SlotcarTrack theTrack;

    // Current position on track
    double curPos;

    // Current speed of car
    double curSpeed;

    // Last time step of update
    long lastTime;

    // Time to wait until next update
    long sleepTime = 100;

    // Size of car
    double carSizeX = 0.0255;
    double carSizeY = 0.05;

    // Color of car
    float carColorR = 1.0f;
    float carColorG = 0.0f;
    float carColorB = 0.0f;

    // Drive car or stop
    boolean driveCar;

    // Open GL context
    GL gl;

    // Old position of the car to draw
    Point2D oldPos;

    // Constant driving speed
    public final double CONST_SPEED = 0.5;

    /**
     * Creates a new Slotcar object with a pointer to OpenGL context and the track
     * @param gl OpenGL context to draw on
     * @param theTrack The race track on which to drive
     */
    public Slotcar(SlotcarTrack theTrack) {
        this.theTrack = theTrack;
        lastTime = -1;
        driveCar = false;
        gl = null;
        oldPos = null;
    }

    /**
     * Main method of the thread. Moves the car and updates the speed.
     */
    public void run() {
        double TL = theTrack.getTrackLength();
        curSpeed = 0.5;

        driveCar = true;

        while (driveCar) {
            // Display car
            
            // Get current position and timestamp
            long newTime = System.nanoTime();


            // Move car
            if (lastTime >= 0) {
                long sinceTime = newTime - lastTime;
                // Compute new position
                curPos = theTrack.advance(curPos, curSpeed, sinceTime / 1.0e9);
                // curPos += curSpeed * (sinceTime / 1.0e9);

                if (curPos >= TL)
                    curPos = curPos - TL;

                // TODO: Let controller change speed
                //System.out.println("Here the controller should take over...");
            }
            lastTime = newTime;

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Drives the car a single step.
     */
    public void drive() {
        double TL = theTrack.getTrackLength();
        curSpeed = CONST_SPEED;

        if (driveCar) {

            // Get current position and timestamp
            long newTime = System.nanoTime();


            // Move car
            if (lastTime >= 0) {
                long sinceTime = newTime - lastTime;
                // Compute new position
                curPos = theTrack.advance(curPos, curSpeed, sinceTime / 1.0e9);
                // curPos += curSpeed * (sinceTime / 1.0e9);

                if (curPos >= TL)
                    curPos = curPos - TL;

                // TODO: Let controller change speed
                //System.out.println("Here the controller should take over...");
            }
            lastTime = newTime;

        }
    }


    /** Draws the car as a rectangle */
    private void drawCar(GL g, Point2D p, Point2D orient) {
        //gl.glEnable(GL.GL_COLOR_LOGIC_OP);
        //gl.glLogicOp(GL.GL_XOR);
        gl.glLineWidth(1.0f);
        gl.glColor3f(carColorR,carColorG,carColorB);
        
        // TODO: Rotate the view to reflect orientation of car

        gl.glTranslated(2*(p.getX())-1, 2*p.getY()-1, 0);

        double angle = (180.0 * Math.atan2(orient.getX(), orient.getY())) / Math.PI;
        gl.glRotated(-angle, 0, 0, 1);

        gl.glRectd(-2*carSizeX, -2*carSizeY,
                   2*carSizeX, 2*carSizeY);
        /*
        gl.glBegin(GL.GL_POLYGON);
        gl.glVertex2d(2*(p.getX()-carSizeX)-1, 2*(p.getY()-carSizeY)-1);
        gl.glVertex2d(2*(p.getX()+carSizeX)-1, 2*(p.getY()-carSizeY)-1);
        gl.glVertex2d(2*(p.getX()+carSizeX)-1, 2*(p.getY()+carSizeY)-1);
        gl.glVertex2d(2*(p.getX()-carSizeX)-1, 2*(p.getY()+carSizeY)-1);
        gl.glEnd();
         */
        //gl.glFlush();
        //gl.glDisable(GL.GL_COLOR_LOGIC_OP);

        gl.glLoadIdentity();

    }

    // Paints the car on the drawable surface
    public void displayCar(GL gl)  {
        System.out.println("Displaying Car!");
        Point2D p = new Point2D.Double();
        Point2D orient = new Point2D.Double();
        theTrack.getPositionAndOrientation(curPos, p, orient);

         System.out.println("CurPos: " + p);
        if (p != null) {
            drawCar(gl,p, orient);
            oldPos = p;
            
        }
        else {
            System.out.println("ERROR: Invalid position!");
        }
    }

    /**
     * Sets the display size of the car
     * @param xsize Width of car
     * @param ysize Length of car
     */
    public void setCarSize(double xsize, double ysize) {
        carSizeX = xsize;
        carSizeY = ysize;
    }

    /**
     * Sets the display color of the car
     * @param r Red value
     * @param g Green value
     * @param b Blue value
     */
    public void setCarColor(float r, float g, float b) {
        carColorR = r;
        carColorG = g;
        carColorB = b;
    }

    /**
     * Set whether to drive or stop
     * @param drive Drive or stop?
     */
    public void setDriveCar(boolean drive) {
        driveCar = drive;
    }

    /**
     * Sets the Open GL device on which to draw
     * @param gl The new OpenGL device
     */
    public void setGL(GL gl) {
        this.gl = gl;
    }

}
