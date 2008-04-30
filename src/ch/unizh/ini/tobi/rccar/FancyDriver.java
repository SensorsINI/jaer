
package ch.unizh.ini.tobi.rccar;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventio.*;
import ch.unizh.ini.caviar.eventio.AEServerSocket;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.filter.*;
import ch.unizh.ini.caviar.eventprocessing.label.*;
import ch.unizh.ini.caviar.eventprocessing.label.SimpleOrientationFilter;
import ch.unizh.ini.caviar.eventprocessing.tracking.*;
import ch.unizh.ini.caviar.eventprocessing.tracking.HoughLineTracker;
import ch.unizh.ini.caviar.eventprocessing.tracking.MultiLineClusterTracker;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import java.awt.Graphics2D;
import java.beans.*;
import java.io.*;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.logging.*;
import java.util.prefs.*;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;

/**
 * The FancyDriver controls the RcCar 

 
 * @author 
 */
public class FancyDriver extends EventFilter2D implements FrameAnnotater{
    
    /** This filter chain is a common preprocessor for FancyDriver line detectors */
    public class DriverPreFilter extends EventFilter2D implements PropertyChangeListener {
        
        @Override
        public String getDescription() {
            return "Drives an RC car using retina spikes";
        }

        private HingeLineTracker hingeLineTracker;
        FilterChain filterChain;
        
        public DriverPreFilter(AEChip chip){
            super(chip);
            
            // DriverPreFilter has a filter chain but DriverPreFilter overrides setFilterEnabled
            // so that it has private settings for the preferred enabled states of the enclosed
            // filters in the filter chain
            
            filterChain=new FilterChain(chip);
            
            hingeLineTracker=new HingeLineTracker(chip);
            
            hingeLineTracker.setEnclosed(true,this);
            
            hingeLineTracker.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);
            
            filterChain.add(hingeLineTracker);
            
            setEnclosedFilterEnabledAccordingToPref(hingeLineTracker,null);
            
            setEnclosedFilterChain(filterChain);
        }
        
        public void propertyChange(PropertyChangeEvent evt) {
            if(!evt.getPropertyName().equals("filterEnabled")) return;
            try{
                setEnclosedFilterEnabledAccordingToPref((EventFilter)(evt.getSource()),(Boolean)(evt.getNewValue()));
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        
        /** Sets the given filter enabled state according to a
         private key based on DriverPreFilter
         and the enclosed filter class name.
         If enb is null, filter enabled state is set according
         to stored preferenc value.
         Otherwise, filter is set enabled according to enb.
         */
        private void setEnclosedFilterEnabledAccordingToPref(EventFilter filter, Boolean enb){
            String key="DriverPreFilter."+filter.getClass().getSimpleName()+".filterEnabled";
            if(enb==null){
                // set according to preference
                boolean en=getPrefs().getBoolean(key,true); // default enabled
                filter.setFilterEnabled(en);
            }else{
                boolean en=enb.booleanValue();
                getPrefs().putBoolean(key,en);
            }
        }
        
        public EventPacket<?> filterPacket(EventPacket<?> in) {
            if(!isFilterEnabled()) return in;
            return filterChain.filterPacket(in);
        }
        
        public Object getFilterState() {
            return null;
        }
        
        public void resetFilter() {
            filterChain.reset();
        }
        
        public void initFilter() {
            filterChain.reset();
        }
        
        /** Overrides to avoid setting preferences for the enclosed filters */
        @Override public void setFilterEnabled(boolean yes){
            this.filterEnabled=yes;
            getPrefs().putBoolean("filterEnabled",yes);
        }
        
        @Override public boolean isFilterEnabled(){
            return true; // force active
        }
    }
    
    private boolean flipSteering=getPrefs().getBoolean("Driver.flipSteering",true);
    {setPropertyTooltip("flipSteering","flips the steering command for use with mirrored scene");}
    
    private boolean useMultiLineTracker=getPrefs().getBoolean("Driver.useMultiLineTracker",true);
    {setPropertyTooltip("useMultiLineTracker","enable to use MultiLineClusterTracker, disable to use HoughLineTracker");}
    
    private float tauDynMs=getPrefs().getFloat("Driver.tauDynMs",100);
    {setPropertyTooltip("tauDynMs","time constant in ms for driving to far-away line");}
    
    private float defaultSpeed=getPrefs().getFloat("Driver.defaultSpeed",0f); // speed of car when filter is turned on
    {setPropertyTooltip("defaultSpeed","Car will drive with this fwd speed when filter is enabled");}
    
    private boolean sendControlToBlenderEnabled=true;
    {setPropertyTooltip("sendControlToBlenderEnabled","sends steering (controlled) and speed (from radio) to albert's blender client");}
    
    private float offsetGain=getPrefs().getFloat("Driver.offsetGain",0.005f);
    {setPropertyTooltip("offsetGain","gain for moving back to the line");}
    
    private float angleGain=getPrefs().getFloat("Driver.angleGain",0.5f);
    {setPropertyTooltip("angleGain","gain for aligning with the line");}
    
    DrivingController controller;
    static Logger log=Logger.getLogger("FancyDriver");
    private SiLabsC8051F320_USBIO_CarServoController servo;
    private EventFilter2D lineTracker;
    private float radioSteer=0.5f;
    private float radioSpeed=0.5f;


    /** Creates a new instance of FancyDriver */
    public FancyDriver(AEChip chip) {
        super(chip);
        chip.getCanvas().addAnnotator(this);
        initFilter();
        controller=new DrivingController();
        
    }
    
    private class DrivingController{
        
        void control(EventPacket in){
            // get values from radio receiver (user sets speed or steers)
            if(servo!=null){
                radioSteer=servo.getRadioSteer();
                radioSpeed=servo.getRadioSpeed();
            }
            
            
            
            
            
            
            if(servo!=null && servo.isOpen()){
                servo.setSteering(0.5f); 
                servo.setSpeed(getDefaultSpeed()+0.5f); // set fwd speed
            }
            // send controls over socket to blender
            sendControlToBlender();
        }
        
    }
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(!isSendControlToBlenderEnabled()) checkServo(); // don't bother with servo if in simulation
        in=getEnclosedFilter().filterPacket(in);
        
        int n=in.getSize();
        if(n==0) return in;
        
        controller.control(in);
        
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
        getEnclosedFilter().resetFilter();
    }
    
    synchronized public void initFilter() {
//        steeringFilter.set3dBFreqHz(lpCornerFreqHz);
        lineTracker=null;
        if(useMultiLineTracker){
            lineTracker=new MultiLineClusterTracker(chip);
        }else{
            lineTracker=new HoughLineTracker(chip);
        }
        lineTracker.setEnclosedFilter(new DriverPreFilter(chip));
//        lineTracker.getEnclosedFilter().setEnclosed(true, lineTracker);
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
    
    public float getOffsetGain() {
        return offsetGain;
    }
    
    /** Sets steering offsetGain */
    public void setOffsetGain(float offsetGain) {
        this.offsetGain = offsetGain;
        getPrefs().putFloat("Driver.offsetGain",offsetGain);
    }
    
    public float getAngleGain() {
        return angleGain;
    }
    
    /** Sets steering angleGain */
    public void setAngleGain(float angleGain) {
        this.angleGain = angleGain;
        getPrefs().putFloat("Driver.angleGain",angleGain);
    }
    
//    public float getLpCornerFreqHz() {
//        return lpCornerFreqHz;
//    }
//
//    public void setLpCornerFreqHz(float lpCornerFreqHz) {
//        this.lpCornerFreqHz = lpCornerFreqHz;
//        getPrefs().putFloat("FancyDriver.lpCornerFreqHz",lpCornerFreqHz);
//        steeringFilter.set3dBFreqHz(lpCornerFreqHz);
//    }
    
    public boolean isFlipSteering() {
        return flipSteering;
    }
    
    /** If set true, then drive towards events (road is textured), if false, drive away from events (side is textured). */
    public void setFlipSteering(boolean flipSteering) {
        this.flipSteering = flipSteering;
        getPrefs().putBoolean("Driver.flipSteering",flipSteering);
    }
    
//    public float getSpeedGain() {
//        return speedGain;
//    }
//
//    /** Sets the gain for reducing steering with speed.
//     The higher this value, the more steering is reduced by speed.
//     @param speedGain - higher is more reduction in steering with speed
//     */
//    public void setSpeedGain(float speedGain) {
//        if(speedGain<1e-1f) speedGain=1e-1f; else if(speedGain>100) speedGain=100;
//        this.speedGain = speedGain;
//        getPrefs().putFloat("FancyDriver.speedGain",speedGain);
//    }
    
    /** Gets the actual steering command based on flipSteering
     */
    
    public boolean isUseMultiLineTracker() {
        return useMultiLineTracker;
    }
    
    synchronized public void setUseMultiLineTracker(boolean useMultiLineTracker) {
        boolean init=useMultiLineTracker!=this.useMultiLineTracker;
        this.useMultiLineTracker = useMultiLineTracker;
        if(init) {
            // should remove previous filters annotator
            chip.getCanvas().removeAnnotator((FrameAnnotater)lineTracker);
            initFilter(); // must rebuild enclosed filter
            if(getChip().getFilterFrame()!=null){
                getChip().getFilterFrame().rebuildContents(); // new enclosed filter, rebuild gui
            }
        }
        getPrefs().putBoolean("Driver.useMultiLineTracker",useMultiLineTracker);
    }
    
//        /** Overrides to set enclosed filters enabled according to prefs.
//     When this is enabled, all enclosed
//     filters are automatically enabled, thus generating
//     propertyChangeEvents and setting the prefs.
//     To get around this we set the flag for filterEnabled and
//     don't call the super which sets the enclosed filter chain enabled.
//     */
//    @Override public void setFilterEnabled(boolean yes) {
//        if(!isEnclosed()){
//            String key=prefsEnabledKey();
//            getPrefs().putBoolean(key, yes);
//        }
//        getPropertyChangeSupport().firePropertyChange("filterEnabled",new Boolean(filterEnabled),new Boolean(yes));
////        setEnclosedFilterEnabledAccordingToPref(xyTypeFilter,null);
////        setEnclosedFilterEnabledAccordingToPref(oriFilter,null);
////        setEnclosedFilterEnabledAccordingToPref(backgroundFilter,null);
////        setEnclosedFilterEnabledAccordingToPref(lineFilter,null);
//        filterEnabled=yes;
//    }
    
    public float getTauDynMs() {
        return tauDynMs;
    }
    
    public void setTauDynMs(float tauDynMs) {
        this.tauDynMs = tauDynMs;
        getPrefs().putFloat("Driver.tauDynMs",tauDynMs);
    }
    
    public float getDefaultSpeed() {
        return defaultSpeed;
    }
    
    public void setDefaultSpeed(float defaultSpeed) {
        this.defaultSpeed = defaultSpeed;
        getPrefs().putFloat("Driver.defaultSpeed",defaultSpeed);
    }
    
    DataOutputStream dos;
    
    /** sends current speed and steering to alberto cardona's blender over the a stream socket opened to blender
     */
    synchronized private void sendControlToBlender(){
        if(!sendControlToBlenderEnabled) return;
        try{
            if(dos==null){
                ch.unizh.ini.caviar.graphics.AEViewer v=chip.getAeViewer();
                if(v==null) throw new RuntimeException("no viewer");
                AESocket aeSocket=v.getAeSocket();
                if(aeSocket==null) throw new RuntimeException("no aeSocket has been opened to a server of events, no one to send controls to");
                Socket s=aeSocket.getSocket();
                if(s==null) throw new RuntimeException("socket inside AESocket is null, something funny");
                dos=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            }
            dos.writeFloat(2f); // header for albert
            //dos.writeFloat(getSteerCommand());
            dos.writeFloat(radioSpeed);
            dos.flush();
//            System.out.println("sent controls steer="+getSteerCommand());
        }catch(Exception e){
            log.warning(e.toString()+": disabling sendControlToBlenderEnabled");
            sendControlToBlenderEnabled=false;
            support.firePropertyChange("sendControlToBlenderEnabled",true,false);
        }
    }

    public boolean isSendControlToBlenderEnabled() {
        return sendControlToBlenderEnabled;
    }

    synchronized public void setSendControlToBlenderEnabled(boolean sendControlToBlenderEnabled) {
        this.sendControlToBlenderEnabled = sendControlToBlenderEnabled;
        if(!sendControlToBlenderEnabled){
            if(dos!=null){
		// don't close the outputstream (which would close that of the socket),
		// just set the thin wrapper to null as a flag to recreate it later (Albert)
		dos = null;
            }
        }
    }
}

