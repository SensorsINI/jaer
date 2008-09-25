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
 * add a tick-box to flip left/right eye!
 */
public class PencilBalancer extends EventFilter2D implements FrameAnnotater, Observer {

    private double polyAX,  polyBX,  polyCX,  polyDX,  polyEX,  polyFX;
    private double polyAY,  polyBY,  polyCY,  polyDY,  polyEY,  polyFY;
    private double polyDecay = getPrefs().getFloat("PencilBalancer.polyDecay", 0.98f);
    {setPropertyTooltip("polyDecay","");}
    private double polyStddev = getPrefs().getFloat("PencilBalancer.polyStddev", 4.0f);
    {setPropertyTooltip("polyStddev","");}
    private double currentBaseX,  currentSlopeX;
    private double currentBaseY,  currentSlopeY;
    private ServoConnection sc = null;
    private boolean connectServoFlag = false; //getPrefs().getBoolean("PencilBalancer.connectServoFlag", false);
    {setPropertyTooltip("connectServo","enable to connect to servos");}
    
    private float gainAngle = getPrefs().getFloat("PencilBalancer.gainAngle", 200.0f);
    {setPropertyTooltip("gainAngle","controller gain for angle of object");}
    private float gainBase = getPrefs().getFloat("PencilBalancer.gainBase", 1.34f);
    {setPropertyTooltip("gainBase","");}
    private float offsetX = getPrefs().getFloat("PencilBalancer.offsetX", -2.0f);
    {setPropertyTooltip("offsetX","");}
    private float offsetY = getPrefs().getFloat("PencilBalancer.offsetY", -1.0f);
    {setPropertyTooltip("offsetY","");}
    
    private float gainMotion = getPrefs().getFloat("PencilBalancer.gainMotion", 70.0f);
    {setPropertyTooltip("gainMotion","controller gain for motion of object");}
    private float motionDecay = getPrefs().getFloat("PencilBalancer.motionDecay", 0.96f);
    {setPropertyTooltip("motionDecay","");}
    
    private float gain1 = getPrefs().getFloat("PencilBalancer.gain1", 1.0f);
    private float gain2 = getPrefs().getFloat("PencilBalancer.gain2", 1.0f);
    private float gain3 = getPrefs().getFloat("PencilBalancer.gain3", 1.0f);
    private float gain4 = getPrefs().getFloat("PencilBalancer.gain4", 1.0f);
    private float gain5 = getPrefs().getFloat("PencilBalancer.gain5", 1.0f);
    private float gain6 = getPrefs().getFloat("PencilBalancer.gain6", 1.0f);

    private boolean displayXEvents = true;
    private boolean displayYEvents = true;
    
    private boolean ignoreTimestampOrdering=getPrefs().getBoolean("PencilBalancer.ignoreTimestampOrdering",true);
    {setPropertyTooltip("ignoreTimestampOrdering","enable to ignore timestamp non-monotonicity in stereo USB input, just deliver packets as soon as they are available");}
    
    private EventPacket<BinocularEvent> out;
    
    /** Creates a new instance of PencilBalancerFilter */
    public PencilBalancer(AEChip chip) {
        super(chip);

        chip.addObserver(this);  // to update chip size parameters

        out = new EventPacket<BinocularEvent>(BinocularEvent.class);

        sc = ServoConnection.getInstance();
        sc.updateParameter(gainAngle, gainBase, offsetX, offsetY, gainMotion, motionDecay);
        sc.updateParameterGain(1, gain1);
        sc.updateParameterGain(2, gain2);
        sc.updateParameterGain(3, gain3);
        sc.updateParameterGain(4, gain4);
        sc.updateParameterGain(5, gain5);
        sc.updateParameterGain(6, gain6);
    }

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

    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        if(chip.getHardwareInterface()!=null && chip.getHardwareInterface() instanceof StereoHardwareInterface ){
            ((StereoHardwareInterface)chip.getHardwareInterface()).setIgnoreTimestampNonmonotonicity(isIgnoreTimestampOrdering());
        }

        OutputEventIterator outItr = out.outputIterator();
        int lastTimeStamp = 0;
        for (Object o : in) {
            BinocularEvent e = (BinocularEvent) o;
            if(e==null){
                log.warning("null event, skipping");
                continue;
            }
            if (e.eye == BinocularEvent.Eye.RIGHT) {
                polyAddEventX(e.x, e.y, e.timestamp);
                if (displayXEvents) {
                    BinocularEvent eout=(BinocularEvent)outItr.nextOutput();
                    eout.copyFrom(e);
                }
            } else {
                polyAddEventY(e.x, e.y, e.timestamp);
                if (displayYEvents) {
                    BinocularEvent eout=(BinocularEvent)outItr.nextOutput();
                    eout.copyFrom(e);
                }
            }
            lastTimeStamp = e.timestamp;

        }

        if (connectServoFlag) {
            if (lastTimeStamp != 0) {
                sc.setBaseSlope(currentBaseX, currentSlopeX, currentBaseY, currentSlopeY, lastTimeStamp);
            }
        }

        return out;
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

    public float getGainMotion() {
        return (gainMotion);
    }
    synchronized public void setGainMotion(float gainMotion) {
        this.gainMotion = gainMotion;
        sc.updateParameter(gainAngle, gainBase, offsetX, offsetY, gainMotion, motionDecay);
        getPrefs().putFloat("PencilBalancer.gainMotion", gainMotion);
    }
    
    public float getMotionDecay() {
        return (motionDecay);
    }
    synchronized public void setMotionDecay(float motionDecay) {
        this.motionDecay = motionDecay;
        sc.updateParameter(gainAngle, gainBase, offsetX, offsetY, gainMotion, motionDecay);
        getPrefs().putFloat("PencilBalancer.motionDecay", motionDecay);
    }
    
    public float getGainAngle() {
        return (gainAngle);
    }
    synchronized public void setGainAngle(float gainAngle) {
        this.gainAngle = gainAngle;
        sc.updateParameter(gainAngle, gainBase, offsetX, offsetY, gainMotion, motionDecay);
        getPrefs().putFloat("PencilBalancer.gainAngle", gainAngle);
    }

    public float getGainBase() {
        return (gainBase);
    }
    synchronized public void setGainBase(float gainBase) {
        this.gainBase = gainBase;
        sc.updateParameter(gainAngle, gainBase, offsetX, offsetY, gainMotion, motionDecay);
        getPrefs().putFloat("PencilBalancer.centering", gainBase);
    }

    public float getOffsetX() {
        return (offsetX);
    }
    synchronized public void setOffsetX(float offsetX) {
        this.offsetX = offsetX;
        sc.updateParameter(gainAngle, gainBase, offsetX, offsetY, gainMotion, motionDecay);
        getPrefs().putFloat("PencilBalancer.offsetX", offsetX);
    }

    public float getOffsetY() {
        return (offsetY);
    }
    synchronized public void setOffsetY(float offsetY) {
        this.offsetY = offsetY;
        sc.updateParameter(gainAngle, gainBase, offsetX, offsetY, gainMotion, motionDecay);
        getPrefs().putFloat("PencilBalancer.offsetY", offsetY);
    }

    public float getGain1() {
        return (gain1);
    }
    synchronized public void setGain1(float gain) {
        this.gain1 = gain;
        sc.updateParameterGain(1, gain);
        getPrefs().putFloat("PencilBalancer.gain1", gain);
    }

    public float getGain2() {
        return (gain2);
    }
    synchronized public void setGain2(float gain) {
        this.gain2 = gain;
        sc.updateParameterGain(2, gain);
        getPrefs().putFloat("PencilBalancer.gain2", gain);
    }

    public float getGain3() {
        return (gain3);
    }
    synchronized public void setGain3(float gain) {
        this.gain3 = gain;
        sc.updateParameterGain(3, gain);
        getPrefs().putFloat("PencilBalancer.gain3", gain);
    }

    public float getGain4() {
        return (gain4);
    }
    synchronized public void setGain4(float gain) {
        this.gain4 = gain;
        sc.updateParameterGain(4, gain);
        getPrefs().putFloat("PencilBalancer.gain4", gain);
    }

    public float getGain5() {
        return (gain5);
    }
    synchronized public void setGain5(float gain) {
        this.gain5 = gain;
        sc.updateParameterGain(5, gain);
        getPrefs().putFloat("PencilBalancer.gain5", gain);
    }

    public float getGain6() {
        return (gain6);
    }
    synchronized public void setGain6(float gain) {
        this.gain6 = gain;
        sc.updateParameterGain(6, gain);
        getPrefs().putFloat("PencilBalancer.gain6", gain);
    }

    public boolean isConnectServo() {
        return connectServoFlag;
    }
    synchronized public void setConnectServo(boolean connectServoFlag) {
        this.connectServoFlag = connectServoFlag;
        sc.connectServo(connectServoFlag);
    }

    public boolean isDisplayXEvents() {
        return(displayXEvents);
    }
    synchronized public void setDisplayXEvents(boolean displayXEvents) {
        this.displayXEvents = displayXEvents;
    }
    public boolean isDisplayYEvents() {
        return(displayYEvents);
    }
    synchronized public void setDisplayYEvents(boolean displayYEvents) {
        this.displayYEvents = displayYEvents;
    }

    synchronized public void resetFilter() {
        System.out.println("RESET called");
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

    synchronized public void initFilter() {
        resetFilter();
    }

    public void update(Observable o, Object arg) {
    }

    public boolean isIgnoreTimestampOrdering() {
        return ignoreTimestampOrdering;
    }

    synchronized public void setIgnoreTimestampOrdering(boolean ignoreTimestampOrdering) {
        this.ignoreTimestampOrdering = ignoreTimestampOrdering;
        getPrefs().putBoolean("PencilBalancer.ignoreTimestampOrdering",ignoreTimestampOrdering);
    }
}
