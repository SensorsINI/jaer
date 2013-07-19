/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.capocaccia.cne.jaer.multilinetracking;
import java.util.Observable;
import java.util.Observer;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.TobiLogger;
/**
 * Tracks a single line using a pair of trackers at cross orientation, each based on the pencil balancer tracker from Matthew Cook.
 *
 * 
 * @author tobi delbruck, matthew cook, jorg conradt
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("Tracks line in continous Hough space")
public class SingleLineTracker extends EventFilter2D implements FrameAnnotater,Observer{

    public static EventFilter.DevelopmentStatus getDevelopmentStatus (){
        return EventFilter.DevelopmentStatus.Alpha;
    }
    /* ***************************************************************************************************** */
    /* **  The follwing stuff we need to compute linetracking and desired table position ******************* */
    /* ***************************************************************************************************** */
    private float polyAX, polyBX, polyCX, polyDX, polyEX; // , polyFX;
    private float polyAY, polyBY, polyCY, polyDY, polyEY; //, polyFY;
    private float currentBaseX, currentSlopeX;
    private float currentBaseY, currentSlopeY;
    private float maxX = 0, maxY = 0; // cached from chip

    public SingleLineTracker (AEChip chip){
        super(chip);
        setPropertyTooltip("polyDecay","Influence of new events on line - increase to make each new event have more influence");
        setPropertyTooltip("polyStddev","Standard deviation of basin of attraction for new events in pixels - increase to make line model wider");
        setPropertyTooltip("enableLogging","Logs line estimates to a file");
        chip.addObserver(this);
        addObserver(this);
    }
    private float polyDecay = getPrefs().getFloat("SingleLineTracker.polyDecay",0.02f);
    private float polyStddev = getPrefs().getFloat("SingleLineTracker.polyStddev",4.0f);
//    private float motionDecay = getPrefs().getFloat("SingleLineTracker.motionDecay",0.96f);
    private boolean enableLogging = false;
    TobiLogger tobiLogger = null;

    synchronized public EventPacket<?> filterPacket (EventPacket<?> in){
        int nleft = 0, nright = 0;

        for ( Object o:in ){
            if ( o == null ){
                log.warning("null event, skipping");
                continue;
            }
            if ( o instanceof BinocularEvent ){
                BinocularEvent e = (BinocularEvent)o;
                // branch here on whoever should getString updated
                nright++;
                polyAddEventV(e.x,e.y,e.timestamp);
                nleft++;
                polyAddEventH(e.x,e.y,e.timestamp);
            } else if ( o instanceof PolarityEvent ){
                PolarityEvent e = (PolarityEvent)o;
                nright++;
                polyAddEventV(e.x,e.y,e.timestamp);
                nleft++;
                polyAddEventH(e.x,e.y,e.timestamp);
            }
            maybeCallUpdateObservers(in,( (BasicEvent)o ).timestamp);
        }

        return in;
    }

    public void annotate (GLAutoDrawable drawable){
        updateCurrentEstimateH();
        updateCurrentEstimateV();
        GL2 gl = drawable.getGL().getGL2();    // when we getString this we are already set up with scale 1=1 pixel, at LL corner
        float loX, hiX, loY, hiY;

        if ( true ){ // draw vertical line model
//            updateCurrentEstimateX();
//            updateCurrentEstimateY();

            loX = currentBaseX + 0 * currentSlopeX;
            hiX = currentBaseX + maxY * currentSlopeX;

            gl.glLineWidth(5.0f);
            gl.glColor3f(1,0,0);
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(loX,0);
            gl.glVertex2d(hiX,maxY);
            gl.glEnd();

            gl.glColor3f(.5f,0,0);
            gl.glLineWidth(1.0f);
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(loX + polyStddev,0);
            gl.glVertex2d(hiX + polyStddev,maxY);
            gl.glEnd();
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(loX - polyStddev,0);
            gl.glVertex2d(hiX - polyStddev,maxY);
            gl.glEnd();
        }

        if ( true ){       // horizontal line model
//            updateCurrentEstimateY();
            loY = currentBaseY + 0 * currentSlopeY;
            hiY = currentBaseY + maxX * currentSlopeY;

            gl.glLineWidth(5.0f);
            gl.glColor3f(0,1,0); // green
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(0,loY);
            gl.glVertex2d(maxX,hiY);
            gl.glEnd();

            gl.glColor3f(0,0.5f,0);
            gl.glLineWidth(1.0f);
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(0,loY + polyStddev);
            gl.glVertex2d(maxX,hiY + polyStddev);
            gl.glEnd();
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2d(0,loY - polyStddev);
            gl.glVertex2d(maxX,hiY - polyStddev);
            gl.glEnd();
        }
    }

    synchronized public void resetFilter (){
//        log.info("RESET called");
        maxX = chip.getSizeX() - 1;
        maxY = chip.getSizeY() - 1;
        resetPolynomial();
    }

    synchronized public void initFilter (){
        resetFilter();
    }

    /* ***************************************************************************************************** */
    /* **  The follwing methods belong to the line tracking algorithm ************************************** */
    /* ***************************************************************************************************** */
    private void updateCurrentEstimateV (){
        float denominator;
        denominator = 1f / ( 4f * polyAX * polyCX - polyBX * polyBX );
        if ( denominator != 0.0 ){
            currentBaseX = ( polyDX * polyBX - 2f * polyAX * polyEX ) * denominator;
            currentSlopeX = ( polyBX * polyEX - 2f * polyCX * polyDX ) * denominator;
        }
    }

    private void updateCurrentEstimateH (){
        float denominator;
        denominator = 1f / ( 4f * polyAY * polyCY - polyBY * polyBY );
        if ( denominator != 0.0 ){
            currentBaseY = ( polyDY * polyBY - 2f * polyAY * polyEY ) * denominator;
            currentSlopeY = ( polyBY * polyEY - 2f * polyCY * polyDY ) * denominator;
        }
    }

    // update poly for vertical line estimate
    private void polyAddEventV (short x,short y,int t){ // x,y in pixels, t in microseconds
        updateCurrentEstimateV();

        float proposedX = currentBaseX + y * currentSlopeX;
        float error = x - proposedX;
        float weight = (float)Math.exp(-error * error / ( 2f * polyStddev * polyStddev ));

        float dec = ( ( 1 - polyDecay ) + ( polyDecay ) * ( 1f - weight ) );
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

    // update poly for horizontal line estimate
    private void polyAddEventH (short x,short y,int t){ // x,y in pixels, t in microseconds
        updateCurrentEstimateH();
        float proposedY = currentBaseY + x * currentSlopeY; // take x of event, estimate y
        float error = y - proposedY; // compute distance from line vertically
        float weight = (float)Math.exp(-error * error / ( 2f * polyStddev * polyStddev ));  // Gaussian weight of event based on distance from model

        float dec = ( ( 1 - polyDecay ) + ( polyDecay ) * ( 1f - weight ) );
        polyAY = dec * polyAY;
        polyBY = dec * polyBY;
        polyCY = dec * polyCY;
        polyDY = dec * polyDY;
        polyEY = dec * polyEY;
//        polyFY = dec * polyFY;

        polyAY += weight * ( x * x );
        polyBY += weight * ( 2f * x );
        polyCY += weight;//* ( 1.0f );
        polyDY += weight * ( -2f * x * y );
        polyEY += weight * ( -2f * y );
//        polyFY += weight * (x * x);
    }

    private void resetPolynomial (){
        polyAX = 0;
        polyBX = 0;
        polyCX = 0;
        polyDX = 0;
        polyEX = 0;
//        polyFX = 0;
        polyAY = 0;
        polyBY = 0;
        polyCY = 0;
        polyDY = 0;
        polyEY = 0;
//        polyFY = 0;

        // add two "imaginary" events to filter, resulting in an initial vertical line
        float x, y;
        // add point 64/0
        x = maxX / 2;
        y = maxY / 2;
        polyAX += ( y * y );
        polyBX += ( 2.0 * y );
        polyCX += ( 1.0 );
        polyDX += ( -2.0 * x * y );
        polyEX += ( -2.0 * x );
//        polyFX += ( x * x );
        polyAY += ( y * y );
        polyBY += ( 2.0 * y );
        polyCY += ( 1.0 );
        polyDY += ( -2.0 * x * y );
        polyEY += ( -2.0 * x );
//        polyFY += ( x * x );

        // add point 64/127
        x = maxX / 2;
        y = maxY - 1;
        polyAX += ( y * y );
        polyBX += ( 2.0 * y );
        polyCX += ( 1.0 );
        polyDX += ( -2.0 * x * y );
        polyEX += ( -2.0 * x );
//        polyFX += ( x * x );
        polyAY += ( y * y );
        polyBY += ( 2.0 * y );
        polyCY += ( 1.0 );
        polyDY += ( -2.0 * x * y );
        polyEY += ( -2.0 * x );
//        polyFY += ( x * x );
    }

    /* ***************************************************************************************************** */
    /* **  The follwing methods are getter and setters for the filter GUI ********************************** */
    /* ***************************************************************************************************** */
    public float getPolyDecay (){
        return polyDecay;
    }

    synchronized public void setPolyDecay (float polyDecay){
        if ( polyDecay > 1 ){
            polyDecay = 1;
        } else if ( polyDecay < 0 ){
            polyDecay = 0;
        }
        float old = this.polyDecay;
        this.polyDecay = polyDecay;
        getSupport().firePropertyChange("polyDecay",old,polyDecay);
        getPrefs().putFloat("SingleLineTracker.polyDecay",polyDecay);
    }

    public float getPolyStddev (){
        return ( (float)polyStddev );
    }

    synchronized public void setPolyStddev (float polyStddev){
        this.polyStddev = polyStddev;
        getPrefs().putFloat("SingleLineTracker.polyStddev",polyStddev);
    }

//    public float getMotionDecay (){
//        return ( motionDecay );
//    }
//
//    synchronized public void setMotionDecay (float motionDecay){
//        if ( motionDecay > 1 ){
//            motionDecay = 1;
//        } else if ( motionDecay < 0 ){
//            motionDecay = 0;
//        }
//        float old = this.motionDecay;
//        this.motionDecay = motionDecay;
//        support.firePropertyChange("motionDecay",old,motionDecay);
//        getPrefs().putFloat("SingleLineTracker.motionDecay",motionDecay);
//    }
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
                tobiLogger = new TobiLogger("SingleLineTracker","nanoseconds, currentBaseX, currentSlopeX, currentBaseY, currentSlopeY, desiredPosX, desiredPosY, currentPosX, currentPosY, nEvents, nRight, nLeft"); // fill in fields here to help consumer of data
                tobiLogger.setNanotimeEnabled(true);
                tobiLogger.setAbsoluteTimeEnabled(false);
            }

            tobiLogger.setEnabled(true);
        }
    }

    /** Updates state.
     *
     * @param o if o instanceof AEChip, updates parameters like chip size, if o instanceof EventFilter, updates line estimates
     * @param arg the UpdateMessage if o is EventPacket
     */
    public void update (Observable o,Object arg){
        if ( o instanceof AEChip ){
            initFilter();
        } else if ( o instanceof EventFilter ){
            updateCurrentEstimateH();
            updateCurrentEstimateV();
        }
    }

    public class SingleLineCluster{

    }
}
