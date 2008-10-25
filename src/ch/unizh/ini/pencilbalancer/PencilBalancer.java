 /*
 * RotateFilter.java
 *
 * Created on July 7, 2006, 6:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package ch.unizh.ini.pencilbalancer;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.stereopsis.StereoHardwareInterface;
import ch.unizh.ini.caviar.util.TobiLogger;
import java.awt.Graphics2D;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;

/**
 * Uses a pair of DVS cameras to control an XY table to balance a pencil.
 * 
 * @author jc
 * 
 */
public class PencilBalancer extends EventFilter2D implements FrameAnnotater, Observer {

    /* ***************************************************************************************************** */
    /* **  The follwing stuff we need to compute linetracking and desired table position ******************* */
    /* ***************************************************************************************************** */
    private double polyAX,  polyBX,  polyCX,  polyDX,  polyEX,  polyFX;
    private double polyAY,  polyBY,  polyCY,  polyDY,  polyEY,  polyFY;

    private double currentBaseX,  currentSlopeX;
    private double currentBaseY,  currentSlopeY;

    private double desiredTableX,  desiredTableY;
    private double currentTableX,  currentTableY;

    private ServoConnection sc = null;
    private long lastTimeNS = 0;

    /* ***************************************************************************************************** */
    /* **  The follwing stuff are variables displayed on GUI *********************************************** */
    /* ***************************************************************************************************** */
    private double polyDecay = getPrefs().getFloat("PencilBalancer.polyDecay", 0.98f);
    {setPropertyTooltip("polyDecay", "decay rate of tracking polynomial per new event");}
    private double polyStddev = getPrefs().getFloat("PencilBalancer.polyStddev", 4.0f);
    {setPropertyTooltip("polyStddev", "standard deviation of basin of attraction for new events");}
    private boolean connectServoFlag = false; //getPrefs().getBoolean("PencilBalancer.connectServoFlag", false);
    {setPropertyTooltip("connectServo", "enable to connect to servos");}
    private boolean obtainTrueTablePosition = getPrefs().getBoolean("PencilBalancer.obtainTrueTablePosition", false);
    {setPropertyTooltip("obtainTrueTablePosition", "enable to request true table position when sending new desired position");}
    private float gainAngle = getPrefs().getFloat("PencilBalancer.gainAngle", 200.0f);
    {setPropertyTooltip("gainAngle", "controller gain for angle of object");}
    private float gainBase = getPrefs().getFloat("PencilBalancer.gainBase", 1.34f);
    {setPropertyTooltip("gainBase", "controller gain for base of object");}
    private float offsetX = getPrefs().getFloat("PencilBalancer.offsetX", -2.0f);
    {setPropertyTooltip("offsetX", "offset to compensate misalignment between camera and table");}
    private float offsetY = getPrefs().getFloat("PencilBalancer.offsetY", -1.0f);
    {setPropertyTooltip("offsetY", "offset to compensate misalignment between camera and table");}
    private float gainMotion = getPrefs().getFloat("PencilBalancer.gainMotion", 70.0f);
    {setPropertyTooltip("gainMotion", "controller gain for motion of object");}
    private float motionDecay = getPrefs().getFloat("PencilBalancer.motionDecay", 0.96f);
    {setPropertyTooltip("motionDecay", "time constant to compute motion decay");}
    private boolean displayXEvents = true;
    {setPropertyTooltip("displayXEvents", "show tracking of line in X");}
    private boolean displayYEvents = true;
    {setPropertyTooltip("displayYEvents", "show tracking of line in Y");}
    private boolean ignoreTimestampOrdering = getPrefs().getBoolean("PencilBalancer.ignoreTimestampOrdering", true);
    {setPropertyTooltip("ignoreTimestampOrdering", "enable to ignore timestamp non-monotonicity in stereo USB input, just deliver packets as soon as they are available");}

    private boolean enableLogging = false;
    TobiLogger tobiLogger = null;

    
    /* ***************************************************************************************************** */
    /* **  The follwing methods belong to the filter as required by jAER *********************************** */
    /* ***************************************************************************************************** */
    /** Creates a new instance of PencilBalancerFilter */
    public PencilBalancer(AEChip chip) {
        super(chip);

        chip.addObserver(this);  // to update chip size parameters

        out = new EventPacket<BinocularEvent>(BinocularEvent.class);

        sc = ServoConnection.getInstance();
    }
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        int nleft = 0, nright = 0;

        if (!isFilterEnabled()) {
            return in;
        }
        if (chip.getHardwareInterface() != null && chip.getHardwareInterface() instanceof StereoHardwareInterface) {
            ((StereoHardwareInterface) chip.getHardwareInterface()).setIgnoreTimestampNonmonotonicity(isIgnoreTimestampOrdering());
        }

        for (Object o : in) {
            BinocularEvent e = (BinocularEvent) o;
            if (e == null) {
                log.warning("null event, skipping");
                continue;
            }
            if (e.eye == BinocularEvent.Eye.RIGHT) {
                nright++;
                polyAddEventX(e.x, e.y, e.timestamp);
            } else {
                nleft++;
                polyAddEventY(e.x, e.y, e.timestamp);
            }
        }

        if (connectServoFlag) {
            long currentTimeNS = System.nanoTime();
            if (currentTimeNS - lastTimeNS > 2000000) {     // use system time instead of timestamps from events.
                // those might cause problems with two retinas, still under investigation!
                updateTablePosition();
                lastTimeNS = currentTimeNS;
            }
        }

        if (isEnableLogging()) {
            tobiLogger.log(String.format("%f,%f,%f,%f,%f,%f,%f,%f,%d,%d,%d",
                    currentBaseX, currentSlopeX,
                    currentBaseY, currentSlopeY,
                    desiredTableX, desiredTableY,
                    currentTableX, currentTableY,
                    in == null ? 0 : in.getSize(), nright, nleft));
        }

        return in;
    }
    public void annotate(float[][][] frame) {
    }
    public void annotate(Graphics2D g) {
    }
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        updateCurrentEstimate();

        GL gl = drawable.getGL();    // when we get this we are already set up with scale 1=1 pixel, at LL corner

        double lowX, highX;

        if (displayXEvents) {        // draw X-line
            lowX = computeXgivenY_inX(0);
            highX = computeXgivenY_inX(127);

            gl.glLineWidth(5.0f);
            gl.glColor3f(1, 0, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX, 0);
            gl.glVertex2d(highX, 127);
            gl.glEnd();

            gl.glColor3f(.5f, 0, 0);
            gl.glLineWidth(1.0f);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX + polyStddev, 0);
            gl.glVertex2d(highX + polyStddev, 127);
            gl.glEnd();
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX - polyStddev, 0);
            gl.glVertex2d(highX - polyStddev, 127);
            gl.glEnd();
        }

        if (displayYEvents) {       // draw Y-line
            lowX = computeXgivenY_inY(0);
            highX = computeXgivenY_inY(127);

            gl.glLineWidth(5.0f);
            gl.glColor3f(0, 1, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX, 0);
            gl.glVertex2d(highX, 127);
            gl.glEnd();

            gl.glColor3f(0, 0.5f, 0);
            gl.glLineWidth(1.0f);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX + polyStddev, 0);
            gl.glVertex2d(highX + polyStddev, 127);
            gl.glEnd();
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX - polyStddev, 0);
            gl.glVertex2d(highX - polyStddev, 127);
            gl.glEnd();
        }
    }
    public Object getFilterState() {
        return null;
    }
    synchronized public void resetFilter() {
        System.out.println("RESET called");
        resetPolynomial();
    }
    synchronized public void initFilter() {
        resetFilter();
    }
    public void update(Observable o, Object arg) {
    }


    /* ***************************************************************************************************** */
    /* **  The follwing methods belong to the line tracking algorithm ************************************** */
    /* ***************************************************************************************************** */
    private void updateCurrentEstimate() {
        double denominator;

        denominator = 4.0 * polyAX * polyCX - polyBX * polyBX;
        if (denominator != 0.0) {
            currentBaseX = (polyDX * polyBX - 2.0 * polyAX * polyEX) / denominator;
            currentSlopeX = (polyBX * polyEX - 2.0 * polyCX * polyDX) / denominator;
        }

        denominator = 4.0 * polyAY * polyCY - polyBY * polyBY;
        if (denominator != 0.0) {
            currentBaseY = (polyDY * polyBY - 2.0 * polyAY * polyEY) / denominator;
            currentSlopeY = (polyBY * polyEY - 2.0 * polyCY * polyDY) / denominator;
        }

    }
    private double computeXgivenY_inX(double y) {
        updateCurrentEstimate();
        return (currentBaseX + y * currentSlopeX);
    }
    private double computeXgivenY_inY(double y) {
        updateCurrentEstimate();
        return (currentBaseY + y * currentSlopeY);
    }
    private void polyAddEventX(short x, short y, int t) { // x,y in pixels, t in microseconds
        double proposedX = computeXgivenY_inX(y);
        double error = x - proposedX;
        double weight = Math.exp(-error * error / (2.0 * polyStddev * polyStddev));

        double dec = (polyDecay + (1.0 - polyDecay) * (1.0 - weight));
        polyAX = dec * polyAX;
        polyBX = dec * polyBX;
        polyCX = dec * polyCX;
        polyDX = dec * polyDX;
        polyEX = dec * polyEX;
        polyFX = dec * polyFX;

        polyAX += weight * (y * y);
        polyBX += weight * (2.0 * y);
        polyCX += weight * (1.0);
        polyDX += weight * (-2.0 * x * y);
        polyEX += weight * (-2.0 * x);
        polyFX += weight * (x * x);
    }
    private void polyAddEventY(short x, short y, int t) { // x,y in pixels, t in microseconds
        double proposedX = computeXgivenY_inY(y);
        double error = x - proposedX;
        double weight = Math.exp(-error * error / (2.0 * polyStddev * polyStddev));

        double dec = (polyDecay + (1.0 - polyDecay) * (1.0 - weight));
        polyAY = dec * polyAY;
        polyBY = dec * polyBY;
        polyCY = dec * polyCY;
        polyDY = dec * polyDY;
        polyEY = dec * polyEY;
        polyFY = dec * polyFY;

        polyAY += weight * (y * y);
        polyBY += weight * (2.0 * y);
        polyCY += weight * (1.0);
        polyDY += weight * (-2.0 * x * y);
        polyEY += weight * (-2.0 * x);
        polyFY += weight * (x * x);
    }
    private void resetPolynomial() {
        polyAX = 0.0;
        polyBX = 0.0;
        polyCX = 0.0;
        polyDX = 0.0;
        polyEX = 0.0;
        polyFX = 0.0;
        polyAY = 0.0;
        polyBY = 0.0;
        polyCY = 0.0;
        polyDY = 0.0;
        polyEY = 0.0;
        polyFY = 0.0;

        // add two "imaginary" events to filter, resulting in an initial vertical line
          double x, y;
        // add point 64/0
        x = 64;
        y = 0;
        polyAX += (y * y);
        polyBX += (2.0 * y);
        polyCX += (1.0);
        polyDX += (-2.0 * x * y);
        polyEX += (-2.0 * x);
        polyFX += (x * x);
        polyAY += (y * y);
        polyBY += (2.0 * y);
        polyCY += (1.0);
        polyDY += (-2.0 * x * y);
        polyEY += (-2.0 * x);
        polyFY += (x * x);
        
        // add point 64/127
        x = 64;
        y = 127;
        polyAX += (y * y);
        polyBX += (2.0 * y);
        polyCX += (1.0);
        polyDX += (-2.0 * x * y);
        polyEX += (-2.0 * x);
        polyFX += (x * x);
        polyAY += (y * y);
        polyBY += (2.0 * y);
        polyCY += (1.0);
        polyDY += (-2.0 * x * y);
        polyEY += (-2.0 * x);
        polyFY += (x * x);
    }

    
    
    /* ***************************************************************************************************** */
    /* **  The follwing methods compute the desired table position ***************************************** */
    /* ***************************************************************************************************** */
    private double slowx0 = 0,  slowx1 = 0,  slowy0 = 0,  slowy1 = 0;
    public synchronized void updateTablePosition() {

        // First, let's compensate for the perspective problem.
        // The stick is really at (x,y,z)=(x0+t*x1,y0+t*y1,t)
        // (there are many formulas, but this is a nice one when not horizontal)
        // and when we see it from (0,-yr,0), we see
        // every point projected onto (x0+t*x1,y0+t*y1+yr,t)/(y0+t*y1+yr).
        // So, we are given four numbers from the two retinas,
        // which is exactly the number of degrees of freedom of a line.
        // Specifically, from retinas at (0,-yr,0) and from (xr,0,0) we see
        //       (x,z)=  ( (x0+t*x1)/(y0+t*y1+yr) ,  t/(y0+t*y1+yr) )
        //   and (y,z)=  ( (y0+t*y1)/(xr-x0-t*x1) ,  t/(xr-x0-t*x1) )
        // which we receive as a base and slope, for example
        //   base b1 = (x0/(y0+yr), 0)  and slope s1=dx/dz = x1-x0*y1/(y0+yr)
        //   base b2 = (y0/(xr-x0), 0)  and slope s2=dy/dz = y1+y0*x1/(xr-x0)
        // If we solve these for x0,x1,y0,y1 in terms of b1,s1,b2,s2, we get
        //   x0 = (b1*yr + b1*b2*xr) / (b1*b2+1)
        //   x1 = (s1 + b1*s2) / (b1*b2+1)
        //   y0 = (b2*xr - b1*b2*yr) / (b1*b2+1)
        //   y1 = (s2 - b2*s1) / (b1*b2+1)
        // Well that is very nice!  Let's calculate it!
        // First, we have to convert to the coordinate system with origin at the
        // crossing point of the axes of the retinas, somewhat above table center.
        double xr = 450;
        double yr = 450; // I hope we can optimize these over time!
        double b1 = ((currentBaseX - 63.5) + 63.5 * currentSlopeX) / xr;
        double b2 = ((currentBaseY - 63.5) + 63.5 * currentSlopeY) / yr;
        double s1 = currentSlopeX;
        double s2 = currentSlopeY;
        double den = 1.0 / (b1 * b2 + 1.0);
        double x0 = (b1 * yr + b1 * b2 * xr) * den;
        double x1 = (s1 + b1 * s2) * den;
        double y0 = (b2 * xr - b1 * b2 * yr) * den;
        double y1 = (s2 - b2 * s1) * den;
        // There, now we have our 3D line, aren't we happy!
        // It is in units corresponding to pixel widths at the "origin", roughly mm.
        //if (++printcounter % 100 == 0) {
        //    System.out.printf("x0 = %f, x1 = %f, y0 = %f, y1 = %f\n", x0,x1,y0,y1);
        //}

        // estimate an average of recent motion (in pixels/call)
        double newx0 = motionDecay * slowx0 + (1 - motionDecay) * x0;
        double newx1 = motionDecay * slowx1 + (1 - motionDecay) * x1;
        double newy0 = motionDecay * slowy0 + (1 - motionDecay) * y0;
        double newy1 = motionDecay * slowy1 + (1 - motionDecay) * y1;
        double dx0 = newx0 - slowx0;
        double dx1 = newx1 - slowx1;    // unused
        double dy0 = newy0 - slowy0;
        double dy1 = newy1 - slowy1;    // unused
        slowx0 = newx0;
        slowx1 = newx1;
        slowy0 = newy0;
        slowy1 = newy1;

        // Ok, now do some dumb heuristic...
        desiredTableX = (x0 + gainAngle * x1) * gainBase + offsetX + gainMotion * dx0;
        desiredTableY = -((y0 + gainAngle * y1) * gainBase + offsetY + gainMotion * dy0);

        // tableX and tableY are now in [-50,50] (roughly millimeters)
        String command = String.format("!T%d,%d", Math.round(10.0 * desiredTableX), Math.round(10.0 * desiredTableY));
//      log.info("Sending " + command);
        sc.sendCommand(command);

        if (obtainTrueTablePosition) {
            requestAndFetchCurrentTablePosition();
        } else {
            String r = "";              // empty data from RS232 to avoid port blocking
            while (r != null) {
                r = sc.readLine();
            }
        }
    }

    private int fetchTrueTablePositionCounter = 1;
    private void requestAndFetchCurrentTablePosition() {
        fetchTrueTablePositionCounter--;
        if (fetchTrueTablePositionCounter == 0) {
            sc.sendCommand("?C");
            fetchTrueTablePositionCounter = 5;
        }

        String r = "";
        while (r != null) {
            r = sc.readLine();

            if (r != null) {
                if (r.toUpperCase().startsWith("-C")) {
                    if (r.length() == 14) {

                        try {
                            double trueTablePositionXVolt = new Integer((r.substring(2, 7).trim()));
                            double trueTablePositionYVolt = new Integer((r.substring(8, 13).trim()));

//#define X_LEFT_END  		((float) 289.7)
//#define X_RIGHT_END		((float) 15950.3)
//#define X_CENTER		((float) ((X_LEFT_END + X_RIGHT_END) / 2.0))
//#define X_SLOPE_PER_MM	((float) ((X_CENTER-X_LEFT_END) / ((134.8-8.0-30.2)/2.0)))

//#define Y_LEFT_END  		((float) 586.5)
//#define Y_RIGHT_END		((float) 16028.8)
//#define Y_CENTER		((float) ((Y_LEFT_END + Y_RIGHT_END) / 2.0))
//#define Y_SLOPE_PER_MM	((float) ((Y_CENTER-Y_LEFT_END) / ((134.8-8.0-30.2)/2.0)))

                            final double xCenter = 8120.00, xSlope = 162.111;
                            final double yCenter = 8307.65, ySlope = 159.850;

                            currentTableX = (trueTablePositionXVolt - xCenter) / xSlope;
                            currentTableY = (trueTablePositionYVolt - yCenter) / ySlope;

                        //if exception occurs parsing numbers some invalid data of length 14 arrived, just ignore
                        } catch (Exception e) {/* ** */ }

                    }
                }
            }
        }
    }

    
    /* ***************************************************************************************************** */
    /* **  The follwing methods are getter and setters for the filter GUI ********************************** */
    /* ***************************************************************************************************** */
    public float getPolyDecay() {
        return ((float) polyDecay);
    }
    synchronized public void setPolyDecay(float polyDecay) {
        this.polyDecay = polyDecay;
        getPrefs().putFloat("PencilBalancer.polyDecay", polyDecay);
    }

    public float getPolyStddev() {
        return ((float) polyStddev);
    }
    synchronized public void setPolyStddev(float polyStddev) {
        this.polyStddev = polyStddev;
        getPrefs().putFloat("PencilBalancer.polyStddev", polyStddev);
    }

    public boolean getObtainTrueTablePosition() {
        return (obtainTrueTablePosition);
    }
    synchronized public void setObtainTrueTablePosition(boolean obtainTrueTablePosition) {
        this.obtainTrueTablePosition = obtainTrueTablePosition;
        getPrefs().putBoolean("PencilBalancer.obtainTrueTablePosition", obtainTrueTablePosition);
    }

    public float getGainMotion() {
        return (gainMotion);
    }
    synchronized public void setGainMotion(float gainMotion) {
        this.gainMotion = gainMotion;
        getPrefs().putFloat("PencilBalancer.gainMotion", gainMotion);
    }

    public float getMotionDecay() {
        return (motionDecay);
    }
    synchronized public void setMotionDecay(float motionDecay) {
        this.motionDecay = motionDecay;
        getPrefs().putFloat("PencilBalancer.motionDecay", motionDecay);
    }

    public float getGainAngle() {
        return (gainAngle);
    }
    synchronized public void setGainAngle(float gainAngle) {
        this.gainAngle = gainAngle;
        getPrefs().putFloat("PencilBalancer.gainAngle", gainAngle);
    }

    public float getGainBase() {
        return (gainBase);
    }
    synchronized public void setGainBase(float gainBase) {
        this.gainBase = gainBase;
        getPrefs().putFloat("PencilBalancer.centering", gainBase);
    }

    public float getOffsetX() {
        return (offsetX);
    }
    synchronized public void setOffsetX(float offsetX) {
        this.offsetX = offsetX;
        getPrefs().putFloat("PencilBalancer.offsetX", offsetX);
    }

    public float getOffsetY() {
        return (offsetY);
    }
    synchronized public void setOffsetY(float offsetY) {
        this.offsetY = offsetY;
        getPrefs().putFloat("PencilBalancer.offsetY", offsetY);
    }

    public boolean isConnectServo() {
        return connectServoFlag;
    }
    synchronized public void setConnectServo(boolean connectServoFlag) {
        this.connectServoFlag = connectServoFlag;
        sc.connectServo(connectServoFlag);
    }

    public boolean isDisplayXEvents() {
        return (displayXEvents);
    }
    synchronized public void setDisplayXEvents(boolean displayXEvents) {
        this.displayXEvents = displayXEvents;
    }

    public boolean isDisplayYEvents() {
        return (displayYEvents);
    }
    synchronized public void setDisplayYEvents(boolean displayYEvents) {
        this.displayYEvents = displayYEvents;
    }

    public boolean isIgnoreTimestampOrdering() {
        return ignoreTimestampOrdering;
    }
    synchronized public void setIgnoreTimestampOrdering(boolean ignoreTimestampOrdering) {
        this.ignoreTimestampOrdering = ignoreTimestampOrdering;
        getPrefs().putBoolean("PencilBalancer.ignoreTimestampOrdering", ignoreTimestampOrdering);
    }

    public boolean isEnableLogging() {
        return enableLogging;
    }
    synchronized public void setEnableLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
        if (!enableLogging) {
            if (tobiLogger != null) {
                tobiLogger.setEnabled(false);
            }

        } else {
            if (tobiLogger == null) {
                tobiLogger = new TobiLogger("PencilBalancer", "timeInNS, currentBaseX, currentSlopeX, currentBaseY, currentSlopeY, sc.getCurrentTablePosX(),sc.getCurrentTablePosY(), sc.getTrueTablePositionX(), sc.getTrueTablePositionY(), nEvents, nRight,nLeft"); // fill in fields here to help consumer of data
                tobiLogger.setNanotimeEnabled(true);
                tobiLogger.setAbsoluteTimeEnabled(false);
            }

            tobiLogger.setEnabled(true);
        }
    }
}
