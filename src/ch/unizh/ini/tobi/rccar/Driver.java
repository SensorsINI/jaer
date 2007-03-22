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
 * Drives the RC car by either centering event activity or non activity, depending on flipSteering switch.
 
 * @author tobi
 */
public class Driver extends EventFilter2D implements FrameAnnotater{
    
    static Logger log=Logger.getLogger("Driver");
    static Preferences prefs=Preferences.userNodeForPackage(Driver.class);
    private SiLabsC8051F320_USBIO_CarServoController servo;
    private LowpassFilter filter=new LowpassFilter();
    private float gain=prefs.getFloat("Driver.gain",1);
    private float lpCornerFreqHz=prefs.getFloat("Driver.lpCornerFreqHz",1);
    private boolean flipSteering=prefs.getBoolean("Driver.flipSteering",false);
    
    private SimpleOrientationFilter oriFilter;
    private boolean useOrientation=prefs.getBoolean("Driver.useOrientation",false);
    private int orientationToUse=prefs.getInt("Driver.orientationToUse",2);
    
    /** Creates a new instance of Driver */
    public Driver(AEChip chip) {
        super(chip);
        chip.getCanvas().addAnnotator(this);
        initFilter();
    }
    
    EventPacket oriPacket=null, outOri=new EventPacket<OrientationEvent>(OrientationEvent.class);
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(useOrientation){
            oriPacket=oriFilter.filterPacket(in);
        }
        // for now, just steer car to put average activity in retina in the middle
        int sizex=getChip().getSizeX();
        int n=in.getSize();
        if(n==0) return in;
        
        int sumx=0;
        if(!useOrientation){
            for(BasicEvent e:in){
                sumx+=e.x;
            }
        }else if(oriPacket!=null){
            OutputEventIterator oi=outOri.outputIterator();
            for(Object e:oriPacket){
                OrientationEvent oe=(OrientationEvent)e;
                if(oe.getType()==orientationToUse) {
                    sumx+=oe.x;
                    oi.nextOutput().copyFrom(oe);
                }
            }
            n=outOri.getSize();
        }
        
        if(n>0){
            float steer;
            if(!flipSteering){
                steer=(sizex-(float)sumx/n)/sizex; // flip sign to steer away from activity
            }else{
                steer=((float)sumx/n)/sizex; // flip sign to steer away from activity
            }
            steer=(steer-0.5f)*gain+0.5f;
            
            float lpSteer=filter.filter(steer,in.getLastTimestamp());
            checkServo();
            try{
                servo.setServoValue(0,lpSteer); // 1 steer right, 0 steer left
            }catch(HardwareInterfaceException e){
                e.printStackTrace();
            }
        }
        if(!useOrientation){
            return in;
        }else{
            return outOri;
        }
    }
    
    private void checkServo(){
        if(servo==null){
            servo=new SiLabsC8051F320_USBIO_CarServoController();
        }
    }
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter() {
    }
    
    public void initFilter() {
        filter.set3dBFreqHz(lpCornerFreqHz);
        oriFilter=new SimpleOrientationFilter(chip);
        setEnclosedFilter(oriFilter);
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    GLU glu=null;
    GLUquadric wheelQuad;
    
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
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
        
        float radioSteer=0.5f, radioSpeed=0.5f;
        if(servo!=null && servo.isOpen()){
            float[] radioValues=servo.getExternalServoValues();
            if(radioValues!=null){
                radioSteer=radioValues[1];
                radioSpeed=radioValues[0];
            }
        }
        gl.glPushMatrix();
        {
            gl.glColor3f(1,1,1);
            gl.glTranslatef(chip.getSizeX()/2,chip.getSizeY()/2,0);
            gl.glLineWidth(6f);
            gl.glBegin(GL.GL_LINES);
            {
                gl.glVertex2f(0,0);
                double a=2*(filter.getValue()-0.5f); // -1 to 1
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
        prefs.putFloat("Driver.gain",gain);
    }
    
    public float getLpCornerFreqHz() {
        return lpCornerFreqHz;
    }
    
    public void setLpCornerFreqHz(float lpCornerFreqHz) {
        this.lpCornerFreqHz = lpCornerFreqHz;
        prefs.putFloat("Driver.lpCornerFreqHz",lpCornerFreqHz);
        filter.set3dBFreqHz(lpCornerFreqHz);
    }
    
    public boolean isFlipSteering() {
        return flipSteering;
    }
    
    /** If set true, then drive towards events (road is textured), if false, drive away from events (side is textured). */
    public void setFlipSteering(boolean flipSteering) {
        this.flipSteering = flipSteering;
        prefs.putBoolean("Driver.flipSteering",flipSteering);
    }
    
    public boolean isUseOrientation() {
        return useOrientation;
    }
    
    public void setUseOrientation(boolean useOrientation) {
        this.useOrientation = useOrientation;
        prefs.putBoolean("Driver.useOrientation",useOrientation);
    }
    
    public int getOrientationToUse() {
        return orientationToUse;
    }
    
    public void setOrientationToUse(int orientationToUse) {
        int nOri=new OrientationEvent().getNumCellTypes();
        if(orientationToUse<0) orientationToUse=0; else if(orientationToUse>nOri-1) orientationToUse=nOri-1;
        this.orientationToUse = orientationToUse;
    }
    
}
