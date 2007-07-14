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
 <p>
 Driver uses the detected line to control steering and speed to drive along the line.
 The line is parameritized by its normal form (rho, theta), where rho is the angle of the normal to the
 line from the center of the chip image and theta is the length of the normal vector (closest passge to
 the origin at the chip image center). The angle rho is 0 or 180 for a vertical line and is 90 or 270
 for a horizontal line.
 <p>
 (rho,theta) control the steering
 
 * @author tobi
 */
public class Driver extends EventFilter2D implements FrameAnnotater{
    
    /** This filter chain is a common preprocessor for Driver line detectors */
    public class DriverPreFilter extends EventFilter2D implements PropertyChangeListener {
        private SimpleOrientationFilter oriFilter;
        private OnOffProximityLineFilter lineFilter;
        private BackgroundActivityFilter backgroundFilter;
        private XYTypeFilter xyTypeFilter;
        private RotateFilter rotateFilter;
        FilterChain filterChain;
        
        public DriverPreFilter(AEChip chip){
            super(chip);
            
            // DriverPreFilter has a filter chain but DriverPreFilter overrides setFilterEnabled
            // so that it has private settings for the preferred enabled states of the enclosed
            // filters in the filter chain
            
            filterChain=new FilterChain(chip);
            xyTypeFilter=new XYTypeFilter(chip);
            oriFilter=new SimpleOrientationFilter(chip);
            backgroundFilter=new BackgroundActivityFilter(chip);
            lineFilter=new OnOffProximityLineFilter(chip);
            rotateFilter=new RotateFilter(chip);
            
            xyTypeFilter.setEnclosed(true,this);
            oriFilter.setEnclosed(true,this);
            backgroundFilter.setEnclosed(true,this);
            lineFilter.setEnclosed(true,this);
            rotateFilter.setEnclosed(true,this);
            
            xyTypeFilter.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);
            oriFilter.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);
            backgroundFilter.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);
            lineFilter.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);
            rotateFilter.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);
            
            filterChain.add(rotateFilter);
            filterChain.add(xyTypeFilter);
            filterChain.add(backgroundFilter);
            filterChain.add(lineFilter);
            filterChain.add(oriFilter);
            
            setEnclosedFilterEnabledAccordingToPref(rotateFilter,null);
            setEnclosedFilterEnabledAccordingToPref(xyTypeFilter,null);
            setEnclosedFilterEnabledAccordingToPref(oriFilter,null);
            setEnclosedFilterEnabledAccordingToPref(backgroundFilter,null);
            setEnclosedFilterEnabledAccordingToPref(lineFilter,null);
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
    private boolean flipSteering=getPrefs().getBoolean("Driver.flipSteering",false);
    {setPropertyTooltip("flipSteering","flips the steering command for use with mirrored scene");}
    
    private boolean useMultiLineTracker=getPrefs().getBoolean("Driver.useMultiLineTracker",true);
    {setPropertyTooltip("useMultiLineTracker","enable to use MultiLineClusterTracker, disable to use HoughLineTracker");}
    
    private float translateFunction=getPrefs().getFloat("Driver.translateFunction", 0.5f);
    {setPropertyTooltip("translateFunction","to convert rad of steer angle in the steer command");}
    private float steerAngleRad=0.0f; //the steering angle subject to the control dynamics
    private float tauDynUs=getPrefs().getFloat("Driver.tauDynUs",100000);
    {setPropertyTooltip("tauDynUs","time constant for driving to far-away line");}
    private float lambdaFar=getPrefs().getFloat("Driver.lambdaFar",1);
    {setPropertyTooltip("lambdaFar","strength of the 'driving to the far away line' contribution to the dynamical control");}
    private float lambdaNear=getPrefs().getFloat("Driver.lambdaNear",1);
    {setPropertyTooltip("lambdaNear","strength of the 'driving close to the line' contribution to the dynamical control");}
    private float rhoMaxPixels=getPrefs().getFloat("Driver.rhoMaxPixel", 64);
    {setPropertyTooltip("rhoMaxPixel","scaling of the distance to the line for dynamical control");}
    int lastt=0;
    DrivingController controller;
    
    /** Creates a new instance of Driver */
    public Driver(AEChip chip) {
        super(chip);
        chip.getCanvas().addAnnotator(this);
        initFilter();
        controller=new DrivingController();
        
    }
    
    private class DrivingController{
        
        void control(EventPacket in){
            // get values from radio receiver (user sets speed or steers)
            radioSteer=servo.getRadioSteer();
            radioSpeed=servo.getRadioSpeed();
            
            sizex=getChip().getSizeX();// must do this here in case chip has changed
            // compute instantaneous position of line according to hough line tracker (which has its own lowpass filter)
            int deltaTUs = in.getLastTimestamp() - lastt;
            lastt=in.getLastTimestamp();
            double rhoPixels=(float)((LineDetector)lineTracker).getRhoPixelsFiltered();  // distance of line from center of image
            double thetaRad=(float)((LineDetector)lineTracker).getThetaDegFiltered()/180*Math.PI; // angle of line, pi/2 is horizontal
            double hDistance=rhoPixels*Math.cos(thetaRad); // horizontal distance of line from center in pixels
            steerInstantaneous=(float)(hDistance/sizex); // as fraction of image
            if(Math.abs(rhoPixels)>rhoMaxPixels)
            	rhoPixels=rhoMaxPixels;
            System.out.println("rhoPixels= "+rhoPixels+"thetaRad= "+thetaRad);
            
//            steerAngleRad = steerAngleRad + (float)(deltaTUs/tauDynUs*(lambdaFar*(thetaRad-steerAngleRad)*(Math.abs(rhoPixels)/rhoMaxPixels) + lambdaNear*(thetaRad - steerAngleRad-Math.PI/2)*(1-Math.abs(rhoPixels)/rhoMaxPixels)));
            if (rhoPixels>0){
            	if (thetaRad<Math.PI/2)
            		steerAngleRad = steerAngleRad + (float)(deltaTUs/tauDynUs)*(-steerAngleRad);
            	if (thetaRad>Math.PI/2)
            		steerAngleRad = steerAngleRad + (float)(deltaTUs/tauDynUs)*(-steerAngleRad);
            }
            if (rhoPixels<=0){
            	if (thetaRad<Math.PI/2)
            		steerAngleRad = steerAngleRad + (float)(deltaTUs/tauDynUs)*(-steerAngleRad +1.0f);
            	if (thetaRad>Math.PI/2)
            		steerAngleRad = steerAngleRad + (float)(deltaTUs/tauDynUs)*(-steerAngleRad +1.0f);
            }
            	
            float speedFactor=(radioSpeed-0.5f)*speedGain; // is zero for halted, positive for fwd, negative for reverse
            if(speedFactor<0)
                speedFactor= 0;
            else if(speedFactor<0.1f) {
                speedFactor=10; // going slowly, limit factor
            }else
                speedFactor=1/speedFactor; // faster, then reduce steering more
            
            // apply proportional gain setting, reduce by speed of car, center at 0.5f
            steerInstantaneous=steerAngleRad; //*speedFactor; //*translateFunction; 
            //steerInstantaneous=(steerInstantaneous*speedFactor)*gain+0.5f;
            setSteerCommand(steeringFilter.filter(steerInstantaneous,in.getLastTimestamp())); // lowpass filter
            if(servo.isOpen()){
                servo.setSteering(getSteerCommand()); // 1 steer right, 0 steer left
            }
        }
        
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
//        in=getEnclosedFilterChain().filterPacket(in);
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
    }
    
    synchronized public void initFilter() {
        steeringFilter.set3dBFreqHz(lpCornerFreqHz);
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
                gl.glVertex2f(0,0);
                double a=2*(getSteerCommand()-0.5f); // -1 to 1
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
    
    public boolean isFlipSteering() {
        return flipSteering;
    }
    
    /** If set true, then drive towards events (road is textured), if false, drive away from events (side is textured). */
    public void setFlipSteering(boolean flipSteering) {
        this.flipSteering = flipSteering;
        getPrefs().putBoolean("Driver.flipSteering",flipSteering);
    }
    
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
       /** Gets the actual steering command based on flipSteering
     */
    public float getSteerCommand() {
        if(flipSteering) return 1-steerCommand;
        return steerCommand;
    }
    
    public void setSteerCommand(float steerCommand) {
        this.steerCommand = steerCommand;
    }
    
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

    public float getTranslateFunction() {
        return translateFunction;
    }
    
    public void setTranslateFunction(float translateFunction) {
        this.translateFunction = translateFunction;
        getPrefs().putFloat("Driver.translateFunction",translateFunction);
    }
    
    public float getTauDynUs() {
        return tauDynUs;
    }
    
    public void setTauDynUs(float tauDynUs) {
        this.tauDynUs = tauDynUs;
        getPrefs().putFloat("Driver.tauDynUs",tauDynUs);
    }
    
    public float getLambdaFar() {
    	return lambdaFar;
    }
    
    public void setLambdaFar(float lambdaFar) {
    	this.lambdaFar = lambdaFar;
    	getPrefs().putFloat("Driver.lambdaFar",lambdaFar);
    }
    
    public float getLambdaNear() {
    	return lambdaNear;
    }
    
    public void setLambdaNear(float lambdaNear) {
    	this.lambdaNear = lambdaNear;
    	getPrefs().putFloat("Driver.lambdNear", lambdaNear);
    }

    public float getRhoMaxPixels() {
    	return rhoMaxPixels;
    }
    
    public void setRhoMaxPixels(float rhoMaxPixels) {
    	this.rhoMaxPixels = rhoMaxPixels;
    	getPrefs().putFloat("Driver.rhoMaxPixels", rhoMaxPixels);
    }
}

