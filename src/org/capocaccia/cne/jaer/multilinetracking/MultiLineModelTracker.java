/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.capocaccia.cne.jaer.multilinetracking;
import javax.media.opengl.*;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.TobiLogger;
/**
 * Tracks low level linear features (edges) using model-based approach, where events update ongoing models of linear features, based on algorithms for line tracking
 * from Matthew Cook as used in the PencilBalancer
 * 
 * @author tobi delbruck, matthew cook, jorg conradt
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("Continuously tracks linear features")
public class MultiLineModelTracker extends EventFilter2D implements FrameAnnotater{

    public static EventFilter.DevelopmentStatus getDevelopmentStatus(){
        return EventFilter.DevelopmentStatus.Alpha;
    }
    
    /* ***************************************************************************************************** */
    /* **  The follwing stuff we need to compute linetracking and desired table position ******************* */
    /* ***************************************************************************************************** */
    private float polyAX,  polyBX,  polyCX,  polyDX,  polyEX,  polyFX;
    private float polyAY,  polyBY,  polyCY,  polyDY,  polyEY,  polyFY;
    private float currentBaseX,  currentSlopeX;
    private float currentBaseY,  currentSlopeY;
    private float desiredTableX,  desiredTableY;
    private float currentTableX,  currentTableY;
    private float desiredTableXLowPass,  desiredTableYLowPass;
    private long lastTimeNS = 0;

    public MultiLineModelTracker (AEChip chip){
        super(chip);
         setPropertyTooltip("polyDecay","decay rate of tracking polynomial per new event");
           setPropertyTooltip("polyStddev","standard deviation of basin of attraction for new events");
         setPropertyTooltip("connectServo","enable to connect to servos");
        setPropertyTooltip("comPortNumber","sets the COM port number used on connectServo - check the Windows Device Manager for the actual port");
        setPropertyTooltip("obtainTrueTablePosition","enable to request true table position when sending new desired position");
        setPropertyTooltip("gainAngle","controller gain for angle of object");
        setPropertyTooltip("gainBase","controller gain for base of object");
  }
    /* ***************************************************************************************************** */
    /* **  The follwing stuff are variables displayed on GUI *********************************************** */
    /* ***************************************************************************************************** */
    private float polyDecay = getPrefs().getFloat("PencilBalancer.polyDecay",0.98f);
    private float polyStddev = getPrefs().getFloat("PencilBalancer.polyStddev",4.0f);
    private boolean connectServoFlag = false;
    private int comPortNumber = getPrefs().getInt("PencilBalancer.comPortNumber",3);
    private boolean obtainTrueTablePosition = getPrefs().getBoolean("PencilBalancer.obtainTrueTablePosition",false);
    private float gainAngle = getPrefs().getFloat("PencilBalancer.gainAngle",280.0f);
    private float motionDecay = getPrefs().getFloat("PencilBalancer.motionDecay",0.96f);
    private boolean enableLogging = false;
    TobiLogger tobiLogger = null;

    synchronized public EventPacket<?> filterPacket (EventPacket<?> in){
        int nleft = 0, nright = 0;
        if(in==null) return in;

        for ( Object o:in ){
            if ( o == null ){
                log.warning("null event, skipping");
                continue;
            }
            if ( o instanceof BinocularEvent ){
                BinocularEvent e = (BinocularEvent)o;
                if ( e.eye == BinocularEvent.Eye.RIGHT ){
                    nright++;
                    polyAddEventX(e.x,e.y,e.timestamp);
                } else{
                    nleft++;
                    polyAddEventY(e.x,e.y,e.timestamp);
                }
            } else if ( o instanceof PolarityEvent ){
                PolarityEvent e = (PolarityEvent)o;
                nright++;
                polyAddEventX(e.x,e.y,e.timestamp);
                nleft++;
                polyAddEventY(e.x,e.y,e.timestamp);
            }
        }

        if ( connectServoFlag ){
            long currentTimeNS = System.nanoTime();
            if ( Math.abs(currentTimeNS - lastTimeNS) > ( 3 * 1000 * 1000 ) ){
                // use system time instead of timestamps from events.
                // those might cause problems with two retinas, still under investigation!
                lastTimeNS = currentTimeNS;

                updateCurrentEstimateX();
                updateCurrentEstimateY();

//                if (obtainTrueTablePosition == true) {
//                    requestAndFetchCurrentTablePosition();
//                }
            }
        }

        if ( enableLogging ){
            tobiLogger.log(String.format("%f,%f,%f,%f,%f,%f,%f,%f,%d,%d,%d",
                    currentBaseX,currentSlopeX,
                    currentBaseY,currentSlopeY,
                    desiredTableX,desiredTableY,
                    currentTableX,currentTableY,
                    in == null ? 0 : in.getSize(),nright,nleft));
        }

        return in;
    }


    public void annotate (GLAutoDrawable drawable){
        if ( !isFilterEnabled() ){
            return;
        }

        GL gl = drawable.getGL();    // when we getString this we are already set up with scale 1=1 pixel, at LL corner

        float lowX, highX;

        if ( true ){        // draw X-line // TODO

            updateCurrentEstimateX();
            updateCurrentEstimateY();

            lowX = currentBaseX + 0 * currentSlopeX;
            highX = currentBaseX + 127 * currentSlopeX;

            gl.glLineWidth(5.0f);
            gl.glColor3f(1,0,0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX,0);
            gl.glVertex2d(highX,127);
            gl.glEnd();

            gl.glColor3f(.5f,0,0);
            gl.glLineWidth(1.0f);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX + polyStddev,0);
            gl.glVertex2d(highX + polyStddev,127);
            gl.glEnd();
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX - polyStddev,0);
            gl.glVertex2d(highX - polyStddev,127);
            gl.glEnd();
        }

        if ( true ){       // draw Y-line // TODO
//            updateCurrentEstimateY();
            lowX = currentBaseY + 0 * currentSlopeY;
            highX = currentBaseY + 127 * currentSlopeY;

            gl.glLineWidth(5.0f);
            gl.glColor3f(0,1,0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX,0);
            gl.glVertex2d(highX,127);
            gl.glEnd();

            gl.glColor3f(0,0.5f,0);
            gl.glLineWidth(1.0f);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX + polyStddev,0);
            gl.glVertex2d(highX + polyStddev,127);
            gl.glEnd();
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(lowX - polyStddev,0);
            gl.glVertex2d(highX - polyStddev,127);
            gl.glEnd();
        }
    }

    public Object getFilterState (){
        return null;
    }

    synchronized public void resetFilter (){
//        log.info("RESET called");
        resetPolynomial();
    }

    synchronized public void initFilter (){
        resetFilter();
    }

    /* ***************************************************************************************************** */
    /* **  The follwing methods belong to the line tracking algorithm ************************************** */
    /* ***************************************************************************************************** */
    private void updateCurrentEstimateX (){
        float denominator;
        denominator = 1f / ( 4f * polyAX * polyCX - polyBX * polyBX );
        if ( denominator != 0.0 ){
            currentBaseX = ( polyDX * polyBX - 2f * polyAX * polyEX ) * denominator;
            currentSlopeX = ( polyBX * polyEX - 2f * polyCX * polyDX ) * denominator;
        }
    }

    private void updateCurrentEstimateY (){
        float denominator;
        denominator = 1f / ( 4f * polyAY * polyCY - polyBY * polyBY );
        if ( denominator != 0.0 ){
            currentBaseY = ( polyDY * polyBY - 2f * polyAY * polyEY ) * denominator;
            currentSlopeY = ( polyBY * polyEY - 2f * polyCY * polyDY ) * denominator;
        }

    }

    private void polyAddEventX (short x,short y,int t){ // x,y in pixels, t in microseconds
        updateCurrentEstimateX();

        float proposedX = currentBaseX + y * currentSlopeX;
        float error = x - proposedX;
        float weight = (float)Math.exp(-error * error / ( 2f * polyStddev * polyStddev ));

        float dec = ( polyDecay + ( 1f - polyDecay ) * ( 1f - weight ) );
        polyAX = dec * polyAX;
        polyBX = dec * polyBX;
        polyCX = dec * polyCX;
        polyDX = dec * polyDX;
        polyEX = dec * polyEX;
//        polyFX = dec * polyFX;

        polyAX += weight * ( y * y );
        polyBX += weight * ( 2f * y );
        polyCX += weight; //* (1f);
        polyDX += weight * ( -2f * x * y );
        polyEX += weight * ( -2f * x );
//        polyFX += weight * (x * x);
    }

    private void polyAddEventY (short x,short y,int t){ // x,y in pixels, t in microseconds
        updateCurrentEstimateY();
        float proposedX = currentBaseY + y * currentSlopeY;
        float error = x - proposedX;
        float weight = (float)Math.exp(-error * error / ( 2f * polyStddev * polyStddev ));

        float dec = ( polyDecay + ( 1f - polyDecay ) * ( 1f - weight ) );
        polyAY = dec * polyAY;
        polyBY = dec * polyBY;
        polyCY = dec * polyCY;
        polyDY = dec * polyDY;
        polyEY = dec * polyEY;
//        polyFY = dec * polyFY;

        polyAY += weight * ( y * y );
        polyBY += weight * ( 2.0 * y );
        polyCY += weight * ( 1.0 );
        polyDY += weight * ( -2.0 * x * y );
        polyEY += weight * ( -2.0 * x );
//        polyFY += weight * (x * x);
    }

    private void resetPolynomial (){
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

        // add two "imaginary" events to filter, resulting in an initial vertical line
        float x, y;
        // add point 64/0
        x = 64;
        y = 0;
        polyAX += ( y * y );
        polyBX += ( 2.0 * y );
        polyCX += ( 1.0 );
        polyDX += ( -2.0 * x * y );
        polyEX += ( -2.0 * x );
        polyFX += ( x * x );
        polyAY += ( y * y );
        polyBY += ( 2.0 * y );
        polyCY += ( 1.0 );
        polyDY += ( -2.0 * x * y );
        polyEY += ( -2.0 * x );
        polyFY += ( x * x );

        // add point 64/127
        x = 64;
        y = 127;
        polyAX += ( y * y );
        polyBX += ( 2.0 * y );
        polyCX += ( 1.0 );
        polyDX += ( -2.0 * x * y );
        polyEX += ( -2.0 * x );
        polyFX += ( x * x );
        polyAY += ( y * y );
        polyBY += ( 2.0 * y );
        polyCY += ( 1.0 );
        polyDY += ( -2.0 * x * y );
        polyEY += ( -2.0 * x );
        polyFY += ( x * x );
    }

    /* ***************************************************************************************************** */
    /* **  The follwing methods are getter and setters for the filter GUI ********************************** */
    /* ***************************************************************************************************** */
    public float getPolyDecay (){
        return ( (float)polyDecay );
    }

    synchronized public void setPolyDecay (float polyDecay){
        if(polyDecay>1)polyDecay=1;else if(polyDecay<0)polyDecay=0;
        float old=this.polyDecay;
        this.polyDecay = polyDecay;
        getSupport().firePropertyChange("polyDecay",old,polyDecay);
        getPrefs().putFloat("PencilBalancer.polyDecay",polyDecay);
    }

    public float getPolyStddev (){
        return ( (float)polyStddev );
    }

    synchronized public void setPolyStddev (float polyStddev){
        this.polyStddev = polyStddev;
        getPrefs().putFloat("PencilBalancer.polyStddev",polyStddev);
    }

    public float getMotionDecay (){
        return ( motionDecay );
    }

    synchronized public void setMotionDecay (float motionDecay){
        if(motionDecay>1)motionDecay=1; else if(motionDecay<0)motionDecay=0;
        float old=this.motionDecay;
        this.motionDecay = motionDecay;
        getSupport().firePropertyChange("motionDecay",old,motionDecay);
        getPrefs().putFloat("PencilBalancer.motionDecay",motionDecay);
    }

    public boolean isEnableLogging (){
        return enableLogging;
    }

    synchronized public void setEnableLogging (boolean enableLogging){
        this.enableLogging = enableLogging;
        if ( !enableLogging ){
            if ( tobiLogger != null ){
                tobiLogger.setEnabled(false);
            }

        } else{
            if ( tobiLogger == null ){
                tobiLogger = new TobiLogger("PencilBalancer","nanoseconds, currentBaseX, currentSlopeX, currentBaseY, currentSlopeY, desiredPosX, desiredPosY, currentPosX, currentPosY, nEvents, nRight, nLeft"); // fill in fields here to help consumer of data
                tobiLogger.setNanotimeEnabled(true);
                tobiLogger.setAbsoluteTimeEnabled(false);
            }

            tobiLogger.setEnabled(true);
        }
    }


}
