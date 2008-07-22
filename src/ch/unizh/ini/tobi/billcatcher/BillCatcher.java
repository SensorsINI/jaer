/*
 * BillCatcher.java
 *
 * Created on September 17, 2007, 4:26 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright September 17, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.tobi.billcatcher;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.label.DirectionSelectiveFilter;
import ch.unizh.ini.caviar.eventprocessing.tracking.*;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.hardwareinterface.usb.ServoInterfaceFactory;
import com.sun.opengl.util.*;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;

/**
 * Catches ppper money dropped between fingers. The game is that a person holds a bill and then releases it at a random moment.
 The catcher has his/her/its fingers ready to close on the bill. It is impossible for humans to do this task. This classes uses
 the silicon retina, cluster tracking, and the usb servo interface to build a robot to catch the bill.
 
 * @author tobi
 */
public class BillCatcher extends EventFilter2D implements FrameAnnotater {

    public static String getDescription() {
        return "Catches a bill (money) when sufficient motion is detected";
    }
    
    ServoInterface servo=null;
    Logger log=Logger.getLogger("BillCatcher");
    FilterChain chain=null;
    RectangularClusterTracker tracker=null;
    DirectionSelectiveFilter motionFilter=null;
    
    private float motionThresholdPixelsPerSec=getPrefs().getFloat("BillCatcher.motionThresholdPixelsPerSec",20f);
    {setPropertyTooltip("motionThresholdPixelsPerSec","threshold in pixels/sec to initiate grab");}
    
    private float servoOpenValue=getPrefs().getFloat("BillCatcher.servoOpenValue",.3f);
    {setPropertyTooltip("servoOpenValue","value of servo position while waiting, 0-1 range");}
    
    private float servoClosedValue=getPrefs().getFloat("BillCatcher.servoClosedValue",0);
    {setPropertyTooltip("servoClosedValue","value of servo position while grabbing, 0-1 range");}
    
    private int grabLengthMs=getPrefs().getInt("BillCatcher.grabLengthMs",500);
    {setPropertyTooltip("grabLengthMs","how long bill is grabbed in ms before dropping it again");}
    
    private float minkEPSToGrab=getPrefs().getFloat("BillCatcher.minkEPSToGrab",10);
    {setPropertyTooltip("minkEPSToGrab","minimum kEPS (kilo events per second)) to initiate grab - filters out motion sensing outliers");}
    
    enum State {WAITING,GRABBING};
    State state=State.WAITING;
    
    /** Creates a new instance of BillCatcher */
    public BillCatcher(AEChip chip) {
        super(chip);
        chain=new FilterChain(chip);
//        tracker=new RectangularClusterTracker(chip);
        motionFilter=new DirectionSelectiveFilter(chip);
//        chain.add(tracker);
        chain.add(motionFilter);
        setEnclosedFilterChain(chain);
    }
    
    long lastGrabStartTime=0, lastGrabStopTime=0; // 1970...
    private Point2D.Float translationalMotion=null;
    
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(in==null || in.getSize()==0) return in;
        chain.filterPacket(in);
        
        checkServo();
        
        boolean isFalling=isBillFalling(in);
        
        switch(state){
            case WAITING:
                if(isFalling && System.currentTimeMillis()-lastGrabStopTime>getGrabLengthMs()){
                    // only start grab if waiting and if it has been long enough since last grab
                    grab();
                }
                break;
            case GRABBING:
                if(!isFalling && System.currentTimeMillis()-lastGrabStartTime>getGrabLengthMs()){
                    // only end grab if bill has stopped falling and we have grabbed for long enough
                    endGrab();
                }
                break;
        }
        return in;
    }
    
    boolean isBillFalling(EventPacket<?> in){
        translationalMotion=motionFilter.getTranslationVector();
        boolean moving=(translationalMotion.y<-motionThresholdPixelsPerSec);
        float rate=in.getEventRateHz();
        return moving && rate>minkEPSToGrab*1e3f;
    }
    
    private void grab(){
        if(state==State.GRABBING) return;
        log.info("grab");
        lastGrabStartTime=System.currentTimeMillis();
        state=State.GRABBING;
        if(servo==null) return;
        try {
            servo.setServoValue(0, getServoClosedValue());
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }
    
    private void endGrab(){
        log.info("endGrab");
        state=State.WAITING;
        lastGrabStopTime=System.currentTimeMillis();
        if(servo==null) return;
        try {
            servo.setServoValue(0, getServoOpenValue());
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }
    
    int servoMissingWarningNumber=0;
    
    private void checkServo(){
        if(servo==null || !servo.isOpen()){
            if(ServoInterfaceFactory.instance().getNumInterfacesAvailable()==0){
                if(servoMissingWarningNumber++%1000==0){
                    log.warning("No servo found");
                }
                return;
            }
            try{
                servo=(ServoInterface)(ServoInterfaceFactory.instance().getInterface(0));
                if(servo==null) return;
                servo.open();
            }catch(HardwareInterfaceException e){
                servo=null;
                if(servoMissingWarningNumber++%1000==0){
                    log.warning(e.toString());
                }
            }
        }
    }
    
    public Object getFilterState() {
        return null;
    }
    
    synchronized public void resetFilter() {
        if(chain!=null) chain.reset();
    }
    
    public void initFilter() {
    }
    
//    @Override
//    synchronized public void setFilterEnabled(boolean yes){
//        super.setFilterEnabled(yes);
//    }
    
    public float getMotionThresholdPixelsPerSec() {
        return motionThresholdPixelsPerSec;
    }
    
    public void setMotionThresholdPixelsPerSec(float motionThresholdPixelsPerSec) {
        this.motionThresholdPixelsPerSec = motionThresholdPixelsPerSec;
        getPrefs().putFloat("BillCatcher.motionThresholdPixelsPerSec",motionThresholdPixelsPerSec);
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    public void annotate(GLAutoDrawable drawable) {
        GL gl=drawable.getGL();
        gl.glColor3f(1,1,1);
        if(translationalMotion!=null){
            gl.glRasterPos3f(0,chip.getSizeY()-2,0);
            chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("Yvel=%.1f",translationalMotion.y));
        }
        if(state==State.GRABBING){
            gl.glRasterPos3f(0,0,0);
            chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("GRAB!!!"));
        }
    }
    
    public int getGrabLengthMs() {
        return grabLengthMs;
    }
    
    public void setGrabLengthMs(int grabLengthMs) {
        if(grabLengthMs<100) grabLengthMs=100; else if(grabLengthMs>3000) grabLengthMs=3000;
        this.grabLengthMs = grabLengthMs;
        getPrefs().putInt("BillCatcher.grabLengthMs",grabLengthMs);
    }

    public float getServoOpenValue() {
        return servoOpenValue;
    }

    public void setServoOpenValue(float servoOpenValue) {
        if(servoOpenValue<0)servoOpenValue=0; else if(servoOpenValue>1) servoOpenValue=1;
        this.servoOpenValue = servoOpenValue;
        getPrefs().putFloat("BillCatcher.servoOpenValue",servoOpenValue);
    }

    public float getServoClosedValue() {
        return servoClosedValue;
    }

    public void setServoClosedValue(float servoClosedValue) {
        if(servoClosedValue<0)servoClosedValue=0; else if(servoClosedValue>1) servoClosedValue=1;
        this.servoClosedValue = servoClosedValue;
        getPrefs().putFloat("BillCatcher.servoClosedValue",servoClosedValue);
    }

    public float getMinkEPSToGrab() {
        return minkEPSToGrab;
    }

    public void setMinkEPSToGrab(float minkEPSToGrab) {
        this.minkEPSToGrab = minkEPSToGrab;
        getPrefs().putFloat("BillCatcher.minkEPSToGrab",minkEPSToGrab);
    }
    
}
