/*
 * PencilBalancer
 *
 *
 *
 *Copyright July 7, 2006 Jorg Conradt, Matt Cook, Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package ch.unizh.ini.jaer.projects.pencilbalancer;

import java.awt.Graphics2D;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.stereopsis.StereoPairHardwareInterface;
import net.sf.jaer.util.TobiLogger;

/**
 * Uses a pair of DVS cameras to control an XY table to balance a pencil.
 *
 * @author jc
 *
 */
@Description("Pencil balancing robot which uses a pair of DVS128 and a USBServoController")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class PencilBalancer extends EventFilter2D implements FrameAnnotater, Observer {

    /* ***************************************************************************************************** */
 /* **  The follwing stuff we need to compute linetracking and desired table position ******************* */
 /* ***************************************************************************************************** */
    private float polyAX, polyBX, polyCX, polyDX, polyEX, polyFX;
    private float polyAY, polyBY, polyCY, polyDY, polyEY, polyFY;
    private float currentBaseX, currentSlopeX;
    private float currentBaseY, currentSlopeY;
    private float desiredTableX, desiredTableY;
    private float currentTableX, currentTableY;
    private float desiredTableXLowPass, desiredTableYLowPass;
    private ServoConnection sc = null;
    private long lastTimeNS = 0;

    /* ***************************************************************************************************** */
 /* **  The follwing stuff are variables displayed on GUI *********************************************** */
 /* ***************************************************************************************************** */
    private float polyDecay = getFloat("polyDecay", 0.98f);
    private float polyStddev = getFloat("polyStddev", 4.0f);
    private boolean connectServoFlag = false;
    private int comPortNumber = getInt("comPortNumber", 3);
    private boolean obtainTrueTablePosition = getBoolean("obtainTrueTablePosition", false);
    private float gainAngle = getFloat("gainAngle", 280.0f);
    private float gainBase = getFloat("gainBase", 1.34f);
    private boolean offsetAutomatic = getBoolean("offsetAutomatic", false);
    private float offsetX = getFloat("offsetX", -2.0f);
    private float offsetY = getFloat("offsetY", -1.0f);
    private float gainMotion = getFloat("gainMotion", 70.0f);
    private float motionMixingFactor = getFloat("motionMixingFactor", 0.04f);
    private boolean displayXEvents = true;
    private boolean displayYEvents = true;
    private boolean ignoreTimestampOrdering = getBoolean("ignoreTimestampOrdering", true);
    private boolean enableLogging = false;
    TobiLogger tobiLogger = null;

    /* ***************************************************************************************************** */
 /* **  The follwing methods belong to the filter as required by jAER *********************************** */
 /* ***************************************************************************************************** */
    /**
     * Creates a new instance of PencilBalancerFilter
     */
    public PencilBalancer(AEChip chip) {
        super(chip);
        chip.addObserver(this);  // to update chip size parameters
        setIgnoreTimestampOrdering(ignoreTimestampOrdering); // to set hardware interface correctly in case we have a hw interface here. 

        desiredTableXLowPass = ((float) 0.0);
        desiredTableYLowPass = ((float) 0.0);
        setPropertyTooltip("polyDecay", "decay rate of tracking polynomial per new event; 0-1 range. Increase to update pencil position more quickly but be more noisy");
        setPropertyTooltip("polyStddev", "standard deviation in pixels around line of basin of attraction for new events; increase to follow faster motion but admit more noise");
        setPropertyTooltip("connectServo", "enable to connect to servos");
        setPropertyTooltip("comPortNumber", "sets the COM port number used on connectServo - check the Windows Device Manager for the actual port");
        setPropertyTooltip("obtainTrueTablePosition", "enable to request true table position when sending new desired position");
        setPropertyTooltip("gainAngle", "controller gain for angle of pencil; increse to more more if angle higher");
        setPropertyTooltip("gainBase", "controller gain for base of object; increase to more center pencil");
        setPropertyTooltip("offsetAutomatic", "find best offset in X- and Y- direction based on past desired motion");
        setPropertyTooltip("offsetX", "offset to compensate misalignment between camera and table");
        setPropertyTooltip("offsetY", "offset to compensate misalignment between camera and table");
        setPropertyTooltip("gainMotion", "derivative controller gain for motion of pecil");
        setPropertyTooltip("motionMixingFactor", "mixing factor to update velocity of pencil x,y position; 0-1 range. Increase to update velocities more quickly");
        setPropertyTooltip("displayXEvents", "show tracking of line in X");
        setPropertyTooltip("ignoreTimestampOrdering", "enable to ignore timestamp non-monotonicity in stereo USB input, just deliver packets as soon as they are available");
        setPropertyTooltip("displayYEvents", "show tracking of line in Y");
        setPropertyTooltip("enableLogging", "log state to logging file; check console output for location and name of file");
    }

    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        int nleft = 0, nright = 0;

        if (!isFilterEnabled()) {
            return in;
        }

        for (Object o : in) {
            if (o == null) {
                log.warning("null event, skipping");
                continue;
            }
            if (o instanceof BinocularEvent) {
                BinocularEvent e = (BinocularEvent) o;
                if (e.eye == BinocularEvent.Eye.RIGHT) {
                    nright++;
                    polyAddEventX(e.x, e.y, e.timestamp);
                } else {
                    nleft++;
                    polyAddEventY(e.x, e.y, e.timestamp);
                }
            } else if (o instanceof PolarityEvent) {
                PolarityEvent e = (PolarityEvent) o;
                if (e.isSpecial() || e.isFilteredOut()) {
                    continue;
                }
                nright++;
                polyAddEventX(e.x, e.y, e.timestamp);
                nleft++;
                polyAddEventY(e.x, e.y, e.timestamp);
            }
        }

        if (connectServoFlag) {
            long currentTimeNS = System.nanoTime();
            if (Math.abs(currentTimeNS - lastTimeNS) > (3 * 1000 * 1000)) {  // send commands at most 330Hz
                // use system time instead of timestamps from events.
                // those might cause problems with two retinas, still under investigation!
                lastTimeNS = currentTimeNS;

                updateCurrentEstimateX();
                updateCurrentEstimateY();
                computeDesiredTablePosition();
                sendDesiredTablePosition();

//                if (obtainTrueTablePosition == true) {
//                    requestAndFetchCurrentTablePosition();
//                }
            }
        }

        if (enableLogging) {
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

        GL2 gl = drawable.getGL().getGL2();    // when we getString this we are already set up with scale 1=1 pixel, at LL corner

        float lowX, highX;

        if (displayXEvents) {        // draw X-line

            updateCurrentEstimateX();
            updateCurrentEstimateY();

            lowX = currentBaseX + 0 * currentSlopeX;
            highX = currentBaseX + chip.getSizeX() * currentSlopeX;

            gl.glLineWidth(5.0f);
            gl.glColor3f(1, 0, 0);
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(lowX, 0);
            gl.glVertex2d(highX, chip.getSizeY());
            gl.glEnd();

            gl.glColor3f(.5f, 0, 0);
            gl.glLineWidth(1.0f);
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(lowX + polyStddev, 0);
            gl.glVertex2d(highX + polyStddev, chip.getSizeY());
            gl.glEnd();
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(lowX - polyStddev, 0);
            gl.glVertex2d(highX - polyStddev, chip.getSizeY());
            gl.glEnd();
        }

        if (displayYEvents) {       // draw Y-line
//            updateCurrentEstimateY();
            lowX = currentBaseY + 0 * currentSlopeY;
            highX = currentBaseY + chip.getSizeX() * currentSlopeY;

            gl.glLineWidth(5.0f);
            gl.glColor3f(0, 1, 0);
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(lowX, 0);
            gl.glVertex2d(highX, chip.getSizeY());
            gl.glEnd();

            gl.glColor3f(0, 0.5f, 0);
            gl.glLineWidth(1.0f);
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(lowX + polyStddev, 0);
            gl.glVertex2d(highX + polyStddev, chip.getSizeY());
            gl.glEnd();
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(lowX - polyStddev, 0);
            gl.glVertex2d(highX - polyStddev, chip.getSizeY());
            gl.glEnd();
        }
    }

    public Object getFilterState() {
        return null;
    }

    synchronized public void resetFilter() {
//        log.info("RESET called");
        resetPolynomial();
        setIgnoreTimestampOrdering(ignoreTimestampOrdering); // to set hardware interface correctly in case we have a hw interface here.
    }

    synchronized public void initFilter() {
        resetFilter();
    }

    public void update(Observable o, Object arg) {
        if (o == getChip() && arg != null && arg instanceof HardwareInterface) {
            if (chip.getHardwareInterface() instanceof StereoPairHardwareInterface) {
                ((StereoPairHardwareInterface) chip.getHardwareInterface()).setIgnoreTimestampNonmonotonicity(isIgnoreTimestampOrdering());
                log.info("set ignoreTimestampOrdering on chip hardware interface change");
            } else {
                log.warning("can't set ignoreTimestampMonotonicity since this is not a StereoHardwareInterface");
            }
        }
    }


    /* ***************************************************************************************************** */
 /* **  The follwing methods belong to the line tracking algorithm ************************************** */
 /* ***************************************************************************************************** */
    private void updateCurrentEstimateX() {
        float denominator;
        denominator = 1f / (4f * polyAX * polyCX - polyBX * polyBX);
        if (denominator != 0.0) {
            currentBaseX = (polyDX * polyBX - 2f * polyAX * polyEX) * denominator;
            currentSlopeX = (polyBX * polyEX - 2f * polyCX * polyDX) * denominator;
        }
    }

    private void updateCurrentEstimateY() {
        float denominator;
        denominator = 1f / (4f * polyAY * polyCY - polyBY * polyBY);
        if (denominator != 0.0) {
            currentBaseY = (polyDY * polyBY - 2f * polyAY * polyEY) * denominator;
            currentSlopeY = (polyBY * polyEY - 2f * polyCY * polyDY) * denominator;
        }

    }

    private void polyAddEventX(short x, short y, int t) { // x,y in pixels, t in microseconds
        updateCurrentEstimateX();

        float proposedX = currentBaseX + y * currentSlopeX;
        float error = x - proposedX;
        float weight = (float) Math.exp(-error * error / (2f * polyStddev * polyStddev));

        float dec = (polyDecay + (1f - polyDecay) * (1f - weight));
        polyAX = dec * polyAX;
        polyBX = dec * polyBX;
        polyCX = dec * polyCX;
        polyDX = dec * polyDX;
        polyEX = dec * polyEX;
//        polyFX = dec * polyFX;

        polyAX += weight * (y * y);
        polyBX += weight * (2f * y);
        polyCX += weight; //* (1f);
        polyDX += weight * (-2f * x * y);
        polyEX += weight * (-2f * x);
//        polyFX += weight * (x * x);
    }

    private void polyAddEventY(short x, short y, int t) { // x,y in pixels, t in microseconds
        updateCurrentEstimateY();
        float proposedX = currentBaseY + y * currentSlopeY;
        float error = x - proposedX;
        float weight = (float) Math.exp(-error * error / (2f * polyStddev * polyStddev));

        float dec = (polyDecay + (1f - polyDecay) * (1f - weight));
        polyAY = dec * polyAY;
        polyBY = dec * polyBY;
        polyCY = dec * polyCY;
        polyDY = dec * polyDY;
        polyEY = dec * polyEY;
//        polyFY = dec * polyFY;

        polyAY += weight * (y * y);
        polyBY += weight * (2.0 * y);
        polyCY += weight * (1.0);
        polyDY += weight * (-2.0 * x * y);
        polyEY += weight * (-2.0 * x);
//        polyFY += weight * (x * x);
    }

    private void resetPolynomial() {
        polyAX = 0;
        polyBX = 0;
        polyCX = 0;
        polyDX = 0;
        polyEX = 0;
        polyFX = 0;
        polyAY = 0;
        polyBY = 0;
        polyCY = 0;
        polyDY = 0;
        polyEY = 0;
        polyFY = 0;

        // appendCopy two "imaginary" events to filter, resulting in an initial vertical line
        float x, y;
        // appendCopy point 64/0
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

        // appendCopy point 64/127
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
    private float slowx0 = 0, slowx1 = 0, slowy0 = 0, slowy1 = 0;
    private int count = 100;

    public synchronized void computeDesiredTablePosition() {

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
        // If we solve these for x0,x1,y0,y1 in terms of b1,s1,b2,s2, we getString
        //   x0 = (b1*yr + b1*b2*xr) / (b1*b2+1)
        //   x1 = (s1 + b1*s2) / (b1*b2+1)
        //   y0 = (b2*xr - b1*b2*yr) / (b1*b2+1)
        //   y1 = (s2 - b2*s1) / (b1*b2+1)
        // Well that is very nice!  Let's calculate it!
        // First, we have to convert to the coordinate system with origin at the
        // crossing point of the axes of the retinas, somewhat above table center.
        float xr = 280;  // was 450...
        float yr = 280; // I hope we can optimize these over time!
        float b1 = ((currentBaseX - 63.5f) + 63.5f * currentSlopeX) / xr;
        float b2 = ((currentBaseY - 63.5f) + 63.5f * currentSlopeY) / yr;
        float s1 = currentSlopeX;
        float s2 = currentSlopeY;
        float den = 1 / (b1 * b2 + 1);
        float x0 = (b1 * yr + b1 * b2 * xr) * den;
        float x1 = (s1 + b1 * s2) * den;
        float y0 = (b2 * xr - b1 * b2 * yr) * den;
        float y1 = (s2 - b2 * s1) * den;
        // There, now we have our 3D line, aren't we happy!
        // It is in units corresponding to pixel widths at the "origin", roughly mm.

        // estimate an average of recent motion (in pixels/call)
        float newx0 = (1 - motionMixingFactor) * slowx0 + (motionMixingFactor) * x0;
        float newx1 = (1 - motionMixingFactor) * slowx1 + (motionMixingFactor) * x1;
        float newy0 = (1 - motionMixingFactor) * slowy0 + (motionMixingFactor) * y0;
        float newy1 = (1 - motionMixingFactor) * slowy1 + (motionMixingFactor) * y1;
        float dx0 = newx0 - slowx0;
//        float dx1 = newx1 - slowx1;    // unused
        float dy0 = newy0 - slowy0;
//        float dy1 = newy1 - slowy1;    // unused
        slowx0 = newx0;
        slowx1 = newx1;
        slowy0 = newy0;
        slowy1 = newy1;

        // Ok, now do some dumb heuristic...
        desiredTableX = +(((x0 + gainAngle * x1) * gainBase - offsetX + gainMotion * dx0));
        desiredTableY = +(((y0 + gainAngle * y1) * gainBase - offsetY + gainMotion * dy0));
        // desiredTableX and desiredTableY are now in [-50,50] (roughly millimeters)

        if (offsetAutomatic) {
            desiredTableXLowPass = ((float) 0.999) * desiredTableXLowPass + ((float) 0.001) * desiredTableX;
            desiredTableYLowPass = ((float) 0.999) * desiredTableYLowPass + ((float) 0.001) * desiredTableY;

            if ((count--) == 0) {
                count = 330;
                setOffsetX(offsetX - ((float) 0.03) * desiredTableXLowPass);
                setOffsetY(offsetY - ((float) 0.03) * desiredTableYLowPass);
                System.out.printf("LP: %8.3f  %8.3f\n", desiredTableXLowPass, desiredTableYLowPass);
            }
        }
    }

    private void sendDesiredTablePosition() {

        String command = String.format("!T%d,%d", Math.round(10.0 * desiredTableX), Math.round(10.0 * desiredTableY));

//        if (obtainTrueTablePosition == true) {
//            fetchTrueTablePositionCounter--;
//            if (fetchTrueTablePositionCounter == 0) {
//                command = command + "\n?C";
//                fetchTrueTablePositionCounter = 3;
//            }
//        }
        //      log.info("Sending " + command);
        sc.sendUpdate(command);
    }

//    private int fetchTrueTablePositionCounter = 1;
    private void requestAndFetchCurrentTablePosition() {

        String r = sc.readLine();
        while (r != null) {
            if (r.length() == 14) {
                if (r.startsWith("-C")) {

                    try {
                        float trueTablePositionXVolt = new Integer((r.substring(2, 7).trim()));
                        float trueTablePositionYVolt = new Integer((r.substring(8, 13).trim()));

//#define X_LEFT_END  		((float) 289.7)
//#define X_RIGHT_END		((float) 15950.3)
//#define X_CENTER		((float) ((X_LEFT_END + X_RIGHT_END) / 2.0))
//#define X_SLOPE_PER_MM	((float) ((X_CENTER-X_LEFT_END) / ((134.8-8.0-30.2)/2.0)))
//#define Y_LEFT_END  		((float) 586.5)
//#define Y_RIGHT_END		((float) 16028.8)
//#define Y_CENTER		((float) ((Y_LEFT_END + Y_RIGHT_END) / 2.0))
//#define Y_SLOPE_PER_MM	((float) ((Y_CENTER-Y_LEFT_END) / ((134.8-8.0-30.2)/2.0)))
                        final float xCenter = 8120.00f, xSlope = 162.111f;
                        final float yCenter = 8307.65f, ySlope = 159.850f;

                        currentTableX = (trueTablePositionXVolt - xCenter) / xSlope;
                        currentTableY = (trueTablePositionYVolt - yCenter) / ySlope;

                        //if exception occurs parsing numbers some invalid data of length 14 arrived, just ignore
                    } catch (Exception e) {/* ** */ }

                }
            }

            r = sc.readLine();  // check for additional input

        }  // end of while

    }

    /* ***************************************************************************************************** */
 /* **  The follwing methods are getter and setters for the filter GUI ********************************** */
 /* ***************************************************************************************************** */
    public float getPolyDecay() {
        return ((float) polyDecay);
    }

    synchronized public void setPolyDecay(float polyDecay) {
        if (polyDecay > 1) {
            polyDecay = 1;
        } else if (polyDecay < 0) {
            polyDecay = 0;
        }
        float old = this.polyDecay;
        this.polyDecay = polyDecay;
        getSupport().firePropertyChange("polyDecay", old, polyDecay);
        putFloat("polyDecay", polyDecay);
    }

    public float getPolyStddev() {
        return ((float) polyStddev);
    }

    synchronized public void setPolyStddev(float polyStddev) {
        this.polyStddev = polyStddev;
        putFloat("polyStddev", polyStddev);
    }

//    public boolean getObtainTrueTablePosition() {
//        return (obtainTrueTablePosition);
//    }
//
//    synchronized public void setObtainTrueTablePosition(boolean obtainTrueTablePosition) {
//        this.obtainTrueTablePosition = obtainTrueTablePosition;
//        putBoolean("obtainTrueTablePosition", obtainTrueTablePosition);
//    }
    public float getGainMotion() {
        return (gainMotion);
    }

    synchronized public void setGainMotion(float gainMotion) {
        this.gainMotion = gainMotion;
        putFloat("gainMotion", gainMotion);
    }

    public float getMotionMixingFactor() {
        return (motionMixingFactor);
    }

    synchronized public void setMotionMixingFactor(float motionMixingFactor) {
        if (motionMixingFactor > 1) {
            motionMixingFactor = 1;
        } else if (motionMixingFactor < 0) {
            motionMixingFactor = 0;
        }
        float old = this.motionMixingFactor;
        this.motionMixingFactor = motionMixingFactor;
        getSupport().firePropertyChange("motionDecay", old, motionMixingFactor);
        putFloat("motionDecay", motionMixingFactor);
    }

    public float getGainAngle() {
        return (gainAngle);
    }

    synchronized public void setGainAngle(float gainAngle) {
        this.gainAngle = gainAngle;
        putFloat("gainAngle", gainAngle);
    }

    public float getGainBase() {
        return (gainBase);
    }

    synchronized public void setGainBase(float gainBase) {
        this.gainBase = gainBase;
        putFloat("centering", gainBase);
    }

    public boolean getOffsetAutomatic() {
        return (offsetAutomatic);
    }

    synchronized public void setOffsetAutomatic(boolean offsetAutomatic) {
        this.offsetAutomatic = offsetAutomatic;
        putBoolean("offsetAutomatic", offsetAutomatic);
        desiredTableXLowPass = ((float) 0.0);
        desiredTableYLowPass = ((float) 0.0);
    }

    public float getOffsetX() {
        return (offsetX);
    }

    synchronized public void setOffsetX(float offsetX) {
        getSupport().firePropertyChange("offsetX", this.offsetX, offsetX); // update GUI
        putFloat("offsetX", offsetX);
        this.offsetX = offsetX;
    }

    public float getOffsetY() {
        return (offsetY);
    }

    synchronized public void setOffsetY(float offsetY) {
        getSupport().firePropertyChange("offsetY", this.offsetY, offsetY);
        putFloat("offsetY", offsetY);
        this.offsetY = offsetY;
    }

    synchronized public void setConnectServo(boolean connectServoFlag) {
        this.connectServoFlag = connectServoFlag;
        if (connectServoFlag == true) {
            if (sc != null) {
                sc.terminate();
            }
            sc = new ServoConnection(getComPortNumber());
        } else {
            sc.terminate();
            sc = null;
        }
    }

    public void doToggleOnConnectServo() {
        setConnectServo(true);
    }

    public void doToggleOffConnectServo() {
        setConnectServo(false);
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
        putBoolean("ignoreTimestampOrdering", ignoreTimestampOrdering);

        if (chip.getHardwareInterface() != null && chip.getHardwareInterface() instanceof StereoPairHardwareInterface) {
            ((StereoPairHardwareInterface) chip.getHardwareInterface()).setIgnoreTimestampNonmonotonicity(isIgnoreTimestampOrdering());
            log.info("ignoreTimestampOrdering set to " + ignoreTimestampOrdering);
        }
    }

    public boolean isEnableLogging() {
        return enableLogging;
    }

    synchronized public void setEnableLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
        if (!enableLogging) {
            if (tobiLogger != null) {
                tobiLogger.setEnabled(false);
                sc.setEnableLogging(false);
            }

        } else {
            if (tobiLogger == null) {
                tobiLogger = new TobiLogger("PencilBalancer", "nanoseconds, currentBaseX, currentSlopeX, currentBaseY, currentSlopeY, desiredPosX, desiredPosY, currentPosX, currentPosY, nEvents, nRight, nLeft"); // fill in fields here to help consumer of data
                tobiLogger.setNanotimeEnabled(true);
                tobiLogger.setAbsoluteTimeEnabled(false);
            }

            tobiLogger.setEnabled(true);
            sc.setEnableLogging(true);
        }
    }

    public int getComPortNumber() {
        return comPortNumber;
    }

    public void setComPortNumber(int comPortNumber) {
        this.comPortNumber = comPortNumber;
        putInt("comPortNumber", comPortNumber);
    }
}
