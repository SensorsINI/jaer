/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.awt.geom.Point2D;

import com.jogamp.opengl.GL2;

/**
 * Implements the movement and drawing of a slotcar
 * @author Michael Pfeiffer
 */
public class Slotcar implements Runnable, ThrottleBrakeInterface {

    // The race track
    SlotcarTrack theTrack;

    // The current state of the car
    SlotcarState curState;

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

    // Whether to draw the osculating circle of the track at the current car position
    boolean drawCircle;

    // Whether to draw the centrifugal force
    boolean drawForce;

    // Throttle value
    ThrottleBrake throttle;


    // Open GL context
    GL2 gl;


    /**
     * Creates a new Slotcar object with a pointer to OpenGL context and the track
     * @param theTrack The race track on which to drive
     */
    public Slotcar(SlotcarTrack theTrack) {
        this.theTrack = theTrack;
        lastTime = -1;
        driveCar = false;
        gl = null;
        drawCircle = true;
        if (theTrack != null)
            curState = theTrack.getCarState();
        else
            curState = null;
        throttle.throttle = 0.0f;
        throttle.brake=false;
    }

    /**
     * Main method of the thread. Moves the car and updates the speed.
     */
    public void run() {

        driveCar = true;

        while (driveCar) {
            // Display car
            
            // Get current position and timestamp
            long newTime = System.nanoTime();


            // Move car
            if (lastTime >= 0) {
                long sinceTime = newTime - lastTime;
                // Compute new state of the car
                
                curState = theTrack.advance(throttle.throttle, sinceTime / 1.0e9f);
                
                // TODO: Let controller change throttle
                //System.out.println("Here the controller should take over...");
            }
            lastTime = newTime;

            if (curState.onTrack == false) {
                // Car fell off track
                driveCar = false;
                System.out.println("Car OFF TRACK!!!");
            }
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
        // curSpeed = CONST_SPEED;

        if (driveCar) {

            // Get current position and timestamp
            long newTime = System.nanoTime();


            // Move car
            if (lastTime >= 0) {
                long sinceTime = newTime - lastTime;
                // Compute new position
                curState = theTrack.advance(throttle.throttle, sinceTime / 1.0e9f);

                // TODO: Let controller change speed
                //System.out.println("Here the controller should take over...");
            }
            lastTime = newTime;

            if (curState.onTrack == false) {
                // Car fell off track
                driveCar = false;
                System.out.println("Car OFF TRACK!!!");
            }
        }
    }


    /** Draws the car as a rectangle */
    private void drawCar(GL2 g, Point2D p, Point2D orient) {
        //gl.glEnable(GL2.GL_COLOR_LOGIC_OP);
        //gl.glLogicOp(GL.GL_XOR);
        gl.glLineWidth(1.0f);
        gl.glColor3f(carColorR,carColorG,carColorB);
        
        // TODO: Rotate the view to reflect orientation of car

        gl.glTranslated(2*(p.getX())-1, 2*p.getY()-1, 0);

        double angle = (180.0 * Math.atan2(orient.getX(), orient.getY())) / Math.PI;
        angle += 180.0 * curState.relativeOrientation / Math.PI;

        // System.out.println("Draw Angle: " + curState.relativeOrientation);
        gl.glRotated(-angle, 0, 0, 1);

        gl.glRectd(-2*carSizeX, 0.5*carSizeY,
                   2*carSizeX, -3.5*carSizeY);
        /*
        gl.glBegin(GL.GL_POLYGON);
        gl.glVertex2d(2*(p.getX()-carSizeX)-1, 2*(p.getY()-carSizeY)-1);
        gl.glVertex2d(2*(p.getX()+carSizeX)-1, 2*(p.getY()-carSizeY)-1);
        gl.glVertex2d(2*(p.getX()+carSizeX)-1, 2*(p.getY()+carSizeY)-1);
        gl.glVertex2d(2*(p.getX()-carSizeX)-1, 2*(p.getY()+carSizeY)-1);
        gl.glEnd();
         */
        //gl.glFlush();
        //gl.glDisable(GL2.GL_COLOR_LOGIC_OP);

        gl.glLoadIdentity();

    }

    /** Draws the osculating circle of the track at the current car position */
    private void drawOsculatingCircle(GL2 g, Point2D p, double radius, Point2D center) {
        radius = Math.abs(radius);

        gl.glLineWidth(1.0f);

        // Draw line to connect center of circle and car
        gl.glColor3f(1.0f, 1.0f, 1.0f);

        gl.glBegin(GL2.GL_LINES);
        gl.glVertex2d(2*(p.getX())-1, 2*(p.getY())-1);
        gl.glVertex2d(2*(center.getX())-1, 2*(center.getY())-1);
        gl.glEnd();

        // Draw circle
        gl.glColor3f(1.0f, 0.0f, 1.0f);
        gl.glBegin(GL2.GL_LINE_LOOP);
        for (int i=0; i<90; i++) {
            gl.glVertex2d(2*(center.getX()+radius*Math.cos(4.0*i*Math.PI/180.0))-1,
                    2*(center.getY()+radius*Math.sin(4.0*i*Math.PI/180.0))-1);
        }
        gl.glEnd();

        gl.glLoadIdentity();

    }


    // Paints the car on the drawable surface
    public void displayCar(GL2 gl)  {
        // System.out.println("Displaying Car!");

        // System.out.println("CurPos: " + p);
        if (curState.XYpos != null) {
            drawCar(gl,curState.XYpos, curState.absoluteOrientation);
            if (drawCircle) {
                Point2D center = new Point2D.Double();
                double radius = theTrack.getOsculatingCircle(curState.pos, center);
                radius = Math.abs(radius);
                if ((radius >= 0) && !(Double.isInfinite(radius)))
                    drawOsculatingCircle(gl, curState.XYpos, radius, center);
            }
            
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
    public void setGL(GL2 gl) {
        this.gl = gl;
    }

    public boolean isDrawCircle() {
        return drawCircle;
    }

    public void setDrawCircle(boolean drawCircle) {
        this.drawCircle = drawCircle;
    }

    public boolean isDrawForce() {
        return drawForce;
    }

    public void setDrawForce(boolean drawForce) {
        this.drawForce = drawForce;
    }

    public SlotcarState getState() {
        return this.curState;
    }

    public ThrottleBrake getThrottle() {
        return throttle;
    }

     @Override
    public void setThrottle(ThrottleBrake throttle) {
        this.throttle=throttle;
    }


     public void setThrottleValue(float throttle) {
        this.throttle.throttle=throttle;
    }

    public void setTrack(SlotcarTrack newTrack) {
        theTrack = newTrack;
        curState = newTrack.getCarState();
    }


}
