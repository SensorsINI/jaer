/*
 * Driver.java
 *
 * Created on February 27, 2007, 9:51 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright February 27, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.tobi.rccar;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.label.*;
import ch.unizh.ini.caviar.eventprocessing.label.SimpleOrientationFilter;
import ch.unizh.ini.caviar.eventprocessing.tracking.*;
import ch.unizh.ini.caviar.eventprocessing.tracking.HoughLineTracker;
import ch.unizh.ini.caviar.eventprocessing.tracking.MultiLineClusterTracker;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import java.awt.Graphics2D;
import java.util.logging.*;
import java.util.prefs.*;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;

/**
 * Drives the RC car using the output from an enclosed line detector filter.
 The enclosed filter chain is a MultiLineClusterTracker which itself encloses
 a chain of XYTypeFilter - BackgroundActivityFilter - OnOffProximityFilter - SimpleOrientationFilter.
 <p>
 Preference values for these enclosed filters are stored by keys based on the enclosing Driver filter.
 
 * @author tobi
 */
public class Driver extends EventFilter2D implements FrameAnnotater{
    
    static Logger log=Logger.getLogger("Driver");
    private SiLabsC8051F320_USBIO_CarServoController servo;
    private LowpassFilter steeringFilter=new LowpassFilter(); // steering command filter
    private float gain=getPrefs().getFloat("Driver.gain",1);
    {setPropertyTooltip("gain","gain for steering control");}
    private float lpCornerFreqHz=getPrefs().getFloat("Driver.lpCornerFreqHz",1);
    {setPropertyTooltip("lpCornerFreqHz","corner freq in Hz for steering control");}
    private EventFilter2D lineTracker;
    private MultiLineClusterTracker multiLineTracker;
    private float steerInstantaneous=0.5f; // instantaneous value, before filtering
    private float steerCommand=0.5f; // actual command, as modified by filtering
    private float speed;
    private int sizex;
    private float radioSteer=0.5f, radioSpeed=0.5f;
    private float speedGain=getPrefs().getFloat("Driver.speedGain",1);
    {setPropertyTooltip("speedGain","gain for reducing steering with speed");}
    
    /** Creates a new instance of Driver */
    public Driver(AEChip chip) {
        super(chip);
        chip.getCanvas().addAnnotator(this);
        initFilter();
    }
    
    /** Applies the enclosed LineDetector filter to extract the lowpassed dominant line feature
     in the scene, then computes steering and speed based on the filter output.
     The driver only controls the car speed via the throttle control, but the speed is reduced automatically
     by the controller according to the controller steering command (more steer = lower speed).
     The instantaneous steering command is based on the horizontal position of the line in the scene; if the line 
     is to the right, steer left, and vice versa. In addition, the instantaneous steering command is lowpass filtered
     to produce the actual steering command.
     @param in the input packet
     @return the output packet, which is the output of the enclosed filter chain.
     */
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        checkServo();
        in=getEnclosedFilter().filterPacket(in);
        
        int n=in.getSize();
        if(n==0) return in;
        
        // get values from radio receiver (user sets speed or steers)
        radioSteer=servo.getRadioSteer();
        radioSpeed=servo.getRadioSpeed();

        sizex=getChip().getSizeX();// must do this here in case chip has changed
        // compute instantaneous position of line according to hough line tracker (which has its own lowpass filter)
        double rhoPixels=(float)((LineDetector)lineTracker).getRhoPixelsFiltered();  // distance of line from center of image
        double thetaRad=(float)((LineDetector)lineTracker).getThetaDegFiltered()/180*Math.PI; // angle of line, pi/2 is horizontal
        double hDistance=rhoPixels*Math.cos(thetaRad); // horizontal distance of line from center in pixels
        steerInstantaneous=(float)(hDistance/sizex); // as fraction of image
        
        float speedFactor=(radioSpeed-0.5f)*speedGain; // is zero for halted, positive for fwd, negative for reverse
        if(speedFactor<0) 
            speedFactor= 0;
        else if(speedFactor<0.1f) {
            speedFactor=10; // going slowly, limit factor
        }else 
            speedFactor=1/speedFactor; // faster, then reduce steering more
        
        // apply proportional gain setting, reduce by speed of car, center at 0.5f
        steerInstantaneous=(steerInstantaneous*speedFactor)*gain+0.5f; 
        steerCommand=steeringFilter.filter(steerInstantaneous,in.getLastTimestamp()); // lowpass filter

        if(servo.isOpen()){
            servo.setSteering(steerCommand); // 1 steer right, 0 steer left
        }
        return in;
    }
    
    long lastWarningMessageTime=System.currentTimeMillis();
    
    private void checkServo(){
        if(servo==null){
            servo=new SiLabsC8051F320_USBIO_CarServoController();
        }
        if(!servo.isOpen()) {
            try{
                servo.open();
            }catch(HardwareInterfaceException e){
                if(System.currentTimeMillis()>lastWarningMessageTime+20000){
                    log.warning(e.getMessage());
                    lastWarningMessageTime=System.currentTimeMillis();
                }
            }
        }
    }
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter() {
    }
    
    public void initFilter() {
        steeringFilter.set3dBFreqHz(lpCornerFreqHz);
        lineTracker=new MultiLineClusterTracker(chip);
        
//        lineTracker=(HoughLineTracker)(chip.getFilterChain().findFilter(HoughLineTracker.class));
        
        setEnclosedFilter(lineTracker);
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    GLU glu=null;
    GLUquadric wheelQuad;
    
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isAnnotationEnabled()) return;
        
//        ((FrameAnnotater)lineTracker).annotate(drawable);
        
        GL gl=drawable.getGL();
        if(gl==null) return;
        final int radius=30;
        
        // draw steering wheel
        if(glu==null) glu=new GLU();
        if(wheelQuad==null) wheelQuad = glu.gluNewQuadric();
        gl.glPushMatrix();
        {
            gl.glTranslatef(chip.getSizeX()/2, (chip.getSizeY())/2,0);
            gl.glLineWidth(6f);
            glu.gluQuadricDrawStyle(wheelQuad,GLU.GLU_FILL);
            glu.gluDisk(wheelQuad,radius,radius+1,16,1);
        }
        gl.glPopMatrix();
        
        // draw steering vector, including external radio input value
        
        gl.glPushMatrix();
        {
            gl.glColor3f(1,1,1);
            gl.glTranslatef(chip.getSizeX()/2,chip.getSizeY()/2,0);
            gl.glLineWidth(6f);
            gl.glBegin(GL.GL_LINES);
            {
                gl.glVertex2f(0,0);
                double a=2*(steeringFilter.getValue()-0.5f); // -1 to 1
                a=Math.atan(a);
                float x=radius*(float)Math.sin(a);
                float y=radius*(float)Math.cos(a);
                gl.glVertex2f(x,y);
                if(servo!=null && servo.isOpen()){
                    gl.glColor3f(1,0,0);
                    gl.glVertex2f(0,0);
                    a=2*(radioSteer-0.5f); // -1 to 1
                    a=Math.atan(a);
                    x=radius*(float)Math.sin(a);
                    y=radius*(float)Math.cos(a);
                    gl.glVertex2f(x,y);
                }
            }
            gl.glEnd();
        }
        gl.glPopMatrix();
        
        // draw external speed value
        if(servo!=null && servo.isOpen()){
            gl.glPushMatrix();
            {
                gl.glColor3f(1,1,1);
                gl.glTranslatef(1,chip.getSizeY()/2,0);
                gl.glLineWidth(15f);
                gl.glBegin(GL.GL_LINES);
                {
                    gl.glVertex2f(0,0);
                    gl.glVertex2f(0,chip.getSizeY()*(radioSpeed-0.5f));
                }
                gl.glEnd();
            }
            gl.glPopMatrix();
        }
        
        
    }
    
    public float getGain() {
        return gain;
    }
    
    /** Sets steering gain */
    public void setGain(float gain) {
        this.gain = gain;
        getPrefs().putFloat("Driver.gain",gain);
    }
    
    public float getLpCornerFreqHz() {
        return lpCornerFreqHz;
    }
    
    public void setLpCornerFreqHz(float lpCornerFreqHz) {
        this.lpCornerFreqHz = lpCornerFreqHz;
        getPrefs().putFloat("Driver.lpCornerFreqHz",lpCornerFreqHz);
        steeringFilter.set3dBFreqHz(lpCornerFreqHz);
    }
    
//    public boolean isFlipSteering() {
//        return flipSteering;
//    }
//    
//    /** If set true, then drive towards events (road is textured), if false, drive away from events (side is textured). */
//    public void setFlipSteering(boolean flipSteering) {
//        this.flipSteering = flipSteering;
//        getPrefs().putBoolean("Driver.flipSteering",flipSteering);
//    }

    public float getSpeedGain() {
        return speedGain;
    }

    /** Sets the gain for reducing steering with speed. 
     The higher this value, the more steering is reduced by speed.
     @param speedGain - higher is more reduction in steering with speed
     */
    public void setSpeedGain(float speedGain) {
        if(speedGain<1e-1f) speedGain=1e-1f; else if(speedGain>100) speedGain=100;
        this.speedGain = speedGain;
        getPrefs().putFloat("Driver.speedGain",speedGain);
    }
    
}
