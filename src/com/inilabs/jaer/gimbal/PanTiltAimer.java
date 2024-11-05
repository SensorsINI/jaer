package com.inilabs.jaer.gimbal;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.EngineeringFormat;
import org.slf4j.LoggerFactory;

/** This filter enables aiming the pan-tilt using a GUI and allows controlling
 * jitter of the pan-tilt when not moving it.
 * @author Tobi Delbruck */
@Description("Rev 30Oct24 rjd: Provides control of **RS4 Gimbal** using a panel to aim it and parameters to control the jitter")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class PanTiltAimer extends EventFilter2DMouseAdaptor implements GimbalInterface, LaserOnOffControl, PropertyChangeListener {

     private static final ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PanTiltAimer.class);
    
     private GimbalBase gimbalbase;
    private GimbalAimerGUI gui;
    private boolean jitterEnabled   = getBoolean("jitterEnabled", false);
    private float   jitterFreqHz    = getFloat("jitterFreqHz", 1);
    private float   jitterAmplitude = getFloat("jitterAmplitude", .02f);
    private int     panServoNumber  = getInt("panServoNumber", 1);
    private int     tiltServoNumber = getInt("tiltServoNumber", 2);
    private boolean invertPan       = getBoolean("invertPan", false);
    private boolean invertTilt      = getBoolean("invertTilt", false);
    private boolean linearMotion    = getBoolean("linearMotion", false);
    private float   limitOfPan      = getFloat("limitOfPan", 0.5f);
    private float   limitOfTilt     = getFloat("limitOfTilt", 0.5f);
    private float   PanValue        = getFloat("panValue", 0.5f);
    private float   tiltValue       = getFloat("tiltValue", 0.5f);
    private float   maxMovePerUpdate= getFloat("maxMovePerUpdate", .1f);
    private float   minMovePerUpdate= getFloat("minMovePerUpdate", .001f);
    private int     moveUpdateFreqHz= getInt("moveUpdateFreqHz", 1000);
    
    private final PropertyChangeSupport supportPanTilt = new PropertyChangeSupport(this);
    private boolean recordingEnabled = false; // not used
    Trajectory mouseTrajectory;
    Trajectory targetTrajectory = new Trajectory();
    Trajectory jitterTargetTrajectory = new Trajectory();
    EngineeringFormat fmt = new EngineeringFormat();
     private Point2D.Float targetLocation = null;
    private float [] rgb = {0, 0, 0, 0};
    
    private String who ="";

    public class Trajectory extends ArrayList<TrajectoryPoint> { 
        long lastTime;
        
        void start() { start(System.nanoTime()); }
        void start(long startTime) {
            if(!isEmpty()) super.clear();
            lastTime = startTime;
        }
                
        void add(float pan, float tilt) {
            if (isEmpty()) start();
            
            long now = System.nanoTime(); //We want this in nanotime, as the panTilt values do change very fast and millis is often not accurate enough.
            add(new TrajectoryPoint(now-lastTime, pan, tilt));
            lastTime = now;
        }
    }

    public class TrajectoryPoint {
        long timeNanos;
        float pan, tilt;

        public TrajectoryPoint(long timeNanos, float pan, float tilt) {
            this.timeNanos = timeNanos;
            this.pan = pan;
            this.tilt = tilt;
        }
        
        public long getTime() { return timeNanos; }
        public float getPan() { return pan; }
        public float getTilt() { return tilt; }
    }

    @Override public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(Message.SetRecordingEnabled.name())) {
            recordingEnabled = (Boolean) evt.getNewValue();
        } else if (evt.getPropertyName().equals(Message.AbortRecording.name())) {
            recordingEnabled = false;
            if (mouseTrajectory != null) {
                mouseTrajectory.clear();
            }
        } else if (evt.getPropertyName().equals(Message.ClearRecording.name())) {
            if (mouseTrajectory != null) {
                mouseTrajectory.clear();
            }
        } else if (evt.getPropertyName().equals(Message.PanTiltSet.name())) {
            supportPanTilt.firePropertyChange(evt);
        } else if (evt.getPropertyName().equals("PanTiltValues")) {
            float[] NewV = (float[])evt.getNewValue();
            float[] OldV = (float[])evt.getOldValue();
            
            this.PanValue = NewV[0];
            this.tiltValue = NewV[1];
            setPanValue(NewV[0]);
            setTiltValue(NewV[1]);
            support.firePropertyChange("panValue",OldV[0],this.PanValue);
            support.firePropertyChange("tiltValue",OldV[1],this.tiltValue);
            supportPanTilt.firePropertyChange("panTiltValues", OldV, NewV);
        } else if(evt.getPropertyName().equals("Target")){
            float[] NewV = (float[])evt.getNewValue();
            this.targetTrajectory.add(NewV[0], NewV[1]);
        } else if(evt.getPropertyName().equals("JitterTarget")) {
            float[] NewV = (float[])evt.getNewValue();
            this.jitterTargetTrajectory.add(NewV[0], NewV[1]);
        }
    }
    
    public enum Message {
        AbortRecording,
        ClearRecording,
        SetRecordingEnabled,
        PanTiltSet
    }
    
    /** Constructs instance of the new 'filter' CalibratedPanTilt. The only time
     * events are actually used is during calibration. The PanTilt hardware
     * interface is also constructed.
     * @param chip */
    public PanTiltAimer(AEChip chip) {
        this(chip, GimbalBase.getInstance());
    }
    
    /** If a panTilt unit is already used by implementing classes it can be 
     * handed to the PanTiltAimer for avoiding initializing multiple pantilts
     * @param chip 
     * @param pt the panTilt unit to be used*/
    public PanTiltAimer(AEChip chip, GimbalBase gb) {
        super(chip);
        who="PanTiltAimer";
        gimbalbase = gb;
        getGimbalBase().setJitterAmplitude(jitterAmplitude);
        getGimbalBase().setJitterFreqHz(jitterFreqHz);
        getGimbalBase().setJitterEnabled(jitterEnabled);
        getGimbalBase().setPanInverted(invertPan);
        getGimbalBase().setTiltInverted(invertTilt);
        getGimbalBase().setLimitOfPan(limitOfPan);
        getGimbalBase().setLimitOfTilt(limitOfTilt);
        
        getGimbalBase().addPropertyChangeListener(this); //We want to know the current position of the panTilt as it changes
        
        // <editor-fold defaultstate="collapsed" desc="-- Property Tooltips --">
        setPropertyTooltip("Jitter","jitterEnabled", "enables servo jitter to produce microsaccadic movement");
        setPropertyTooltip("Jitter","jitterAmplitude", "Jitter of pantilt amplitude for circular motion");
        setPropertyTooltip("Jitter","jitterFreqHz", "Jitter frequency in Hz of circular motion");
        
        setPropertyTooltip("Pan","panInverted", "flips the pan");
        setPropertyTooltip("Pan","limitOfPan", "limits pan around 0.5 by this amount to protect hardware");
        setPropertyTooltip("Pan","panServoNumber", "servo channel for pan (0-3)");
        setPropertyTooltip("Pan","panValue", "The current value of the pan");
        
        setPropertyTooltip("Tilt","tiltServoNumber", "servo channel for tilt (0-3)");
        setPropertyTooltip("Tilt","tiltInverted", "flips the tilt");
        setPropertyTooltip("Tilt","limitOfTilt", "limits tilt around 0.5 by this amount to protect hardware");
        setPropertyTooltip("Tilt","tiltValue", "The current value of the tilt");
        
        setPropertyTooltip("CamMove","maxMovePerUpdate", "Maximum change in ServoValues per update");
        setPropertyTooltip("CamMove","minMovePerUpdate", "Minimum change in ServoValues per update");
        setPropertyTooltip("CamMove","MoveUpdateFreqHz", "Frequenzy of updating the Servo values");
        setPropertyTooltip("CamMove","followEnabled", "Whether the PanTilt should automatically move towards the target or not");
        setPropertyTooltip("CamMove","linearMotion","Wheather the panTilt should move linearly or exponentially towards the target");
        
        setPropertyTooltip("center", "centers pan and tilt");
        setPropertyTooltip("disableServos", "disables servo PWM output. Servos should relax but digital servos may store last value and hold it.");
        setPropertyTooltip("aim", "show GUI for controlling pan and tilt");
        
          targetLocation = new Point2D.Float(100, 100);	
        // </editor-fold>
    }

    @Override public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        return in;
    }
    
    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }

        fmt.setPrecision(1); // digits after decimel point
        GL2 gl = drawable.getGL().getGL2(); // when we getString this we are already set up with updateShape 1=1 pixel,
        // at LL corner
        if (gl == null) {
            log.warn("null GL in RectangularClusterTracker.annotate");
            return;
        }
        
        drawGimbalPoseCrossHair(gl) ;
  
        // current gimbal target
        try {
            gl.glPushMatrix();
            {
                    drawTargetLocation(gl);
            }
        } catch (java.util.ConcurrentModificationException e) {
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warn("concurrent modification of target list while drawing ");
        } finally {
            gl.glPopMatrix();
        }
    }
        
        
         private GL2 drawGimbalPoseCrossHair(GL2 gl) {
                     int sx2 = chip.getSizeX() / 8, sy2 = chip.getSizeY() / 8;
                     int midX = chip.getSizeX()/2, midY = chip.getSizeY()/2;
                    gl.glPushMatrix();
                    gl.glTranslatef(midX, midY, 0);
                    gl.glLineWidth(2f);
	gl.glColor3f(0, 1, 1);
	gl.glBegin(GL.GL_LINES);
	gl.glVertex2f(-sx2, 0);
	gl.glVertex2f(sx2, 0);
	gl.glVertex2f(0, -sy2);
	gl.glVertex2f(0, sy2);
	gl.glEnd();
	gl.glPopMatrix();
             
          // text annoations on clusters, setup
                GLUT cGLUT = chip.getCanvas().getGlut();
                final int font = GLUT.BITMAP_HELVETICA_18;
                gl.glRasterPos3f(midX, midY + sy2, 0);
                cGLUT.glutBitmapString(font, String.format("GIMBAL(yaw,roll,pitch)=%.1f, %.1f, %.1f deg ", 
                        getGimbalBase().getYaw(), 
                        getGimbalBase().getRoll(),
                        getGimbalBase().getPitch()) );
                
                //  DEBUGGING rjd
//              gl.glRasterPos3f(midX, midY + 2*sy2-10f, 0);
//              cGLUT.glutBitmapString(font, String.format("TARGET(width, mixingF)=%.1f, %.3f", tracker.getTargetWidth(), tracker.getMinMixingFactor()));
   
                
        return gl;
         }
        
                             
         private GL2 drawTargetLocation(GL2 gl) {
                                        float sx = chip.getSizeX() / 32;
	                  	// draw gimbal pose cross-hair 
		gl.glPushMatrix();         
                                        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
                                        gl.glTranslatef(targetLocation.x, targetLocation.y, 0);  
                                         rgb[3] = .5f;
                                         gl.glColor4fv(rgb, 0);
                                        drawBox(gl, 0.0f,  0.0f,  10.0f, 10.0f);
                                         gl.glPopAttrib();
		gl.glPopMatrix();
        return gl;
         }
         
          private void drawBox(GL2 gl, float x, float y, float sx, float sy) {
                       DrawGL.drawBox(gl, x, y, sx, sy, 0); 
	}
         
         
         private void drawCircle(GL2 gl, float cx, float cy, float radius, int segments) {
        gl.glBegin(GL2.GL_LINE_LOOP); // Use GL_LINE_LOOP to draw the outline of the circle
        for (int i = 0; i < segments; i++) {
            double theta = 2.0 * Math.PI * i / segments; // Calculate the angle for each segment
            float x = (float)(radius * Math.cos(theta));
            float y = (float)(radius * Math.sin(theta));
            gl.glVertex2f(x + cx, y + cy); // Set vertex positions relative to the center
        }
        gl.glEnd();
    }
        


    @Override public void resetFilter() {
        getGimbalBase().close();
    }

    @Override public void initFilter() {
        resetFilter();
    }

    // <editor-fold defaultstate="collapsed" desc="GUI button --Aim--">
    /** Invokes the calibration GUI
     * Calibration values are stored persistently as preferences.
     * Built automatically into filter parameter panel as an action. */
    public void doAim() {
        getGui().setVisible(true);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="GUI button --Center--">
    public void doCenter() {
        if (getGimbalBase() != null) {
//            if(!getGimbalBase().isFollowEnabled()) getGimbalBase().setFollowEnabled(true);
            getGimbalBase().setTarget(.5f,.5f);
        }
    }
    // </editor-fold>

    
    @Override public void acquire(String who) {
        getGimbalBase().acquire(who);
    }

    @Override public boolean isLockOwned() {
        return getGimbalBase().isLockOwned();
    }

    @Override public void release(String who) {
        getGimbalBase().release(who);
    }
    
    @Override public void startJitter() {
        getGimbalBase().startJitter();
    }

    @Override public void stopJitter() {
        getGimbalBase().stopJitter();
    }

    @Override public void setLaserEnabled(boolean yes) {
        getGimbalBase().setLaserEnabled(yes);
    }

    @Override public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            getGimbalBase().setJitterAmplitude(jitterAmplitude);
            getGimbalBase().setJitterFreqHz(jitterFreqHz);
            getGimbalBase().setJitterEnabled(jitterEnabled);
            getGimbalBase().setPanInverted(invertPan);
            getGimbalBase().setTiltInverted(invertTilt);
            getGimbalBase().setLimitOfPan(limitOfPan);
            getGimbalBase().setLimitOfTilt(limitOfTilt);
        } else {
            try {
                getGimbalBase().stopJitter();
                getGimbalBase().close();
            } catch (Exception e) {
                log.warn("Jitter setting error: {}", e.getMessage(), e);
            }
        }
    }
       
    
    /* * @return the gui */
    public GimbalAimerGUI getGui() {
        if(gui == null) {
            gui = new GimbalAimerGUI(gimbalbase);
            gui.getSupport().addPropertyChangeListener(this);
        }
        return gui;
    }


    /**
     * @return the support */
    @Override public PropertyChangeSupport getSupport() {
        return supportPanTilt;
    }
    
   
   
    public GimbalBase getGimbalBase() {
        if(gimbalbase == null) {
            log.warn("No Pan-Tilt Hardware found. Initialising new PanTilt");
            gimbalbase = GimbalBase.getInstance();
        }
        return gimbalbase;
    }

  
    public void setPanTiltHardware(GimbalBase panTilt) {
        this.gimbalbase = panTilt;
    }
    // </editor-fold>
    
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --jitterEnabled--">
    /** checks if jitter is enabled
    * @return the jitterEnabled */
    public boolean isJitterEnabled() {
        return getGimbalBase().isJitterEnabled();
    }

    /** sets the jitter flag true or false
     * @param jitterEnabled the jitterEnabled to set */
    public void setJitterEnabled(boolean jitterEnabled) {
        putBoolean("jitterEnabled", jitterEnabled);
        boolean OldValue = this.jitterEnabled;
//        if(!isFollowEnabled()) setFollowEnabled(true); //To start jittering the pantilt must follow target
        
        this.jitterEnabled = jitterEnabled;
        getGimbalBase().setJitterEnabled(jitterEnabled);
        support.firePropertyChange("jitterEnabled",OldValue,jitterEnabled);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --jitterAmplitude--">
    /** gets the amplitude of the jitter
     * @return the amplitude of the jitter */
    @Override
    public float getJitterAmplitude() {
        return getGimbalBase().getJitterAmplitude();
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt
     * during jittering
     * @param jitterAmplitude the amplitude */
    @Override
    public void setJitterAmplitude(float jitterAmplitude) {
        putFloat("jitterAmplitude", jitterAmplitude);
        float OldValue = this.jitterAmplitude;
        
        this.jitterAmplitude = jitterAmplitude;
        getGimbalBase().setJitterAmplitude(jitterAmplitude);
        support.firePropertyChange("jitterAmplitude",OldValue,jitterAmplitude);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --jitterFreqHz--">
    /** gets the frequency of the jitter
     * @return the frequency of the jitter */
    @Override
    public float getJitterFreqHz() {
        return getGimbalBase().getJitterFreqHz();
    }

    /** sets the frequency of the jitter
     * @param jitterFreqHz in Hz */
    @Override
    public void setJitterFreqHz(float jitterFreqHz) {
        putFloat("jitterFreqHz", jitterFreqHz);
        float OldValue = this.jitterFreqHz;
        
        this.jitterFreqHz = jitterFreqHz;
        getGimbalBase().setJitterFreqHz(jitterFreqHz);
        support.firePropertyChange("jitterFreqHz",OldValue,jitterFreqHz);
    }
     // </editor-fold>
    
 
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --MinMovePerUpdate--">
    public float getMinMovePerUpdate() {
        return getGimbalBase().getMinMovePerUpdate();
    }
    
    public void setMinMovePerUpdate(float MinMove) {
        putFloat("minMovePerUpdate", MinMove);
        float OldValue = getMinMovePerUpdate();
        getGimbalBase().setMinMovePerUpdate(MinMove);
        this.minMovePerUpdate=MinMove;
        support.firePropertyChange("minMovePerUpdate",OldValue,MinMove);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --MaxMovePerUpdate--">
    public float getMaxMovePerUpdate() {
        return getGimbalBase().getMaxMovePerUpdate();
    }
    
    public void setMaxMovePerUpdate(float MaxMove) {
        putFloat("maxMovePerUpdate", MaxMove);
        float OldValue = getMaxMovePerUpdate();
        getGimbalBase().setMaxMovePerUpdate(MaxMove);
        this.maxMovePerUpdate=MaxMove;
        support.firePropertyChange("maxMovePerUpdate",OldValue,MaxMove);
    }
    // </editor-fold>    

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --MoveUpdateFreqHz--">
    public int getMoveUpdateFreqHz() {
        return getGimbalBase().getMoveUpdateFreqHz();
    }
    
    public void setMoveUpdateFreqHz(int UpdateFreq) {
        putFloat("moveUpdateFreqHz", UpdateFreq);
        float OldValue = getMoveUpdateFreqHz();
        getGimbalBase().setMoveUpdateFreqHz(UpdateFreq);
        this.moveUpdateFreqHz=UpdateFreq;
        support.firePropertyChange("moveUpdateFreqHz",OldValue,UpdateFreq);
    }
    // </editor-fold> 
    
      
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PanTiltTarget--">
    public float[] getPanTiltTarget() {
        return getGimbalBase().getTarget();
    }
    
    public void setPanTiltTarget(float PanTarget, float TiltTarget) {
        getGimbalBase().setTarget(PanTarget, TiltTarget);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PanTiltValues--">
    @Override
    public float[] getPanTiltValues() {
        return getGimbalBase().getPanTiltValues();
    }
    
    /** Sets the pan and tilt servo values
     * @param pan 0 to 1 value
     * @param tilt 0 to 1 value */
    @Override
    public void setPanTiltValues(float pan, float tilt) throws HardwareInterfaceException {
        getGimbalBase().setPanTiltValues(pan, tilt);
    }
    // </editor-fold>
    
     
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --TiltInverted--">
    /** checks if tilt is inverted
     * @return tiltinverted */
    public boolean isTiltInverted() {
        return getGimbalBase().getTiltInverted();
    }
      
    /** sets weather tilt is inverted
     * @param tiltInverted value to be set*/
    public void setTiltInverted(boolean tiltInverted) {
        putBoolean("invertTilt", tiltInverted);
        boolean OldValue = isTiltInverted();
        getGimbalBase().setTiltInverted(tiltInverted);
        this.invertTilt = tiltInverted;
        getSupport().firePropertyChange("invertTilt",OldValue,tiltInverted);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PanInverted--">
    /** checks if pan is inverted
     * @return paninverted */
    public boolean isPanInverted() {
        return getGimbalBase().getPanInverted();
    }
    
    /** sets weather pan is inverted
     * @param panInverted value to be set*/
    public void setPanInverted(boolean panInverted) {
        putBoolean("invertPan", panInverted);
        boolean OldValue = isPanInverted();
        getGimbalBase().setPanInverted(panInverted);
        this.invertPan = panInverted;
        getSupport().firePropertyChange("invertPan",OldValue,panInverted);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --LimitOfTilt--">
    /** gets the limit of the tilt for the hardware
     * @return the tiltLimit */
    public float getLimitOfTilt() {
        return getGimbalBase().getLimitOfTilt();
    }

    /** sets the limit of the tilt for the hardware
     * @param TiltLimit the TiltLimit to set */
    public void setLimitOfTilt(float TiltLimit) {
        putFloat("limitOfTilt", TiltLimit);
        float OldValue = getLimitOfTilt();
        getGimbalBase().setLimitOfTilt(TiltLimit);
        this.limitOfTilt=TiltLimit;
        getSupport().firePropertyChange("limitOfTilt",OldValue,TiltLimit);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --LimitOfPan--">
    /** gets the limit of the pan for the hardware
     * @return the panLimit */
    public float getLimitOfPan() {
        return getGimbalBase().getLimitOfPan();
    }

    /** sets the limit of the pan for the hardware
     * @param PanLimit the PanLimit to set */
    public void setLimitOfPan(float PanLimit) {
        putFloat("limitOfPan", PanLimit);
        float OldValue = getLimitOfPan();
        getGimbalBase().setLimitOfPan(PanLimit);
        this.limitOfPan=PanLimit;
        getSupport().firePropertyChange("limitOfPan",OldValue,PanLimit);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --TiltValue--">
    public float getTiltValue() {
        return this.tiltValue;
    }
    
    public void setTiltValue(float TiltValue) {
        putFloat("tiltValue",TiltValue);
        float OldValue = this.tiltValue;
        this.tiltValue = TiltValue;
        support.firePropertyChange("tiltValue",OldValue,TiltValue);  
        getGimbalBase().setTarget(this.PanValue, TiltValue);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PanValue--">
    public float getPanValue() {
        return this.PanValue;
    }
    
    public void setPanValue(float PanValue) {
        putFloat("panValue",PanValue);
        float OldValue = this.PanValue;
        this.PanValue = PanValue;
        support.firePropertyChange("panValue",OldValue,PanValue);
        getGimbalBase().setTarget(PanValue,this.tiltValue);
    }

    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --linearMotion--">
    public boolean isLinearMotion() {
        return getGimbalBase().isLinearSpeedEnabled();
    }

    public void setLinearMotion(boolean linearMotion) {
        putBoolean("linearMotion",linearMotion);
        boolean OldValue = isLinearMotion();
        getGimbalBase().setLinearSpeedEnabled(linearMotion);
        this.linearMotion = linearMotion;
        getSupport().firePropertyChange("linearMotion", OldValue, linearMotion);
    }
    // </editor-fold>
    
}
