
package ch.unizh.ini.jaer.projects.rccar;


import java.awt.Graphics2D;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AESocket;

import net.sf.jaer.eventprocessing.EventFilter2D;

import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.toradex.ToradexOakG3AxisAccelerationSensor;
import net.sf.jaer.hardwareinterface.usb.toradex.ToradexOakG3AxisAccelerationSensorGUI;
import net.sf.jaer.util.TobiLogger;

/**
 * The FancyDriver controls the RcCar 

 
 * @author Christian Braendli, Robin Ritz
 */
public class FancyDriver extends EventFilter2D implements FrameAnnotater{
    
    private boolean usePIDController=getPrefs().getBoolean("Driver.usePIDController",true);
    {setPropertyTooltip("usePIDController","use the PID controller");}
    
    private boolean useLQRController=getPrefs().getBoolean("Driver.useLQRController",false);
    {setPropertyTooltip("useLQRController","use the LQR controller");}
    
    private boolean useUserK=getPrefs().getBoolean("Driver.useUserK",false);
    {setPropertyTooltip("useUserK","use the user defined K matrix");}
    
    private float kp=getPrefs().getFloat("Driver.kp",10);
    {setPropertyTooltip("kp","proportinal gain of the pid controller");}

    private float ki=getPrefs().getFloat("Driver.ki",0);
    {setPropertyTooltip("ki","integral gain of the pid controller");}

    private float kd=getPrefs().getFloat("Driver.kd",0);
    {setPropertyTooltip("kd","derivative gain of the pid controller");}
    
    private float xK=getPrefs().getFloat("Driver.xK",10);
    {setPropertyTooltip("xK","element for x of the K matrix");}
    
    private float xpK=getPrefs().getFloat("Driver.xpK",10);
    {setPropertyTooltip("xpK","element for xp of the K matrix");}
    
    private float phiK=getPrefs().getFloat("Driver.phiK",10);
    {setPropertyTooltip("phiK","element for phi of the K matrix");}
    
    private float phipK=getPrefs().getFloat("Driver.phipK",10);
    {setPropertyTooltip("phipK","element for phip of the K matrix");}

    private float lateralGain=getPrefs().getFloat("Driver.lateralGain",0.5f);
    {setPropertyTooltip("lateralGain","gain for moving back to the line");}

    private float angleGain=getPrefs().getFloat("Driver.angleGain",0.4f);
    {setPropertyTooltip("angleGain","gain for aligning with the line");}
    
    private boolean flipSteering=getPrefs().getBoolean("Driver.flipSteering",true);
    {setPropertyTooltip("flipSteering","flips the steering command for use with mirrored scene");}
    
    private boolean useHingeLineTracker=getPrefs().getBoolean("Driver.useHingeLineTracker",false);
    {setPropertyTooltip("useHingeLineTracker","enable to use HingeLineTracker, disable to use HingeLaneTracker");}
    
    private float defaultSpeed=getPrefs().getFloat("Driver.defaultSpeed",0.1f); // speed of car when filter is turned on
    {setPropertyTooltip("defaultSpeed","Car will drive with this fwd speed when filter is enabled");}
    
    private float defaultSteeringAngle=getPrefs().getFloat("Driver.defaultSteeringAngle",0f);
    {setPropertyTooltip("defaultSteeringAngle","Steering angle if no controller is activated");}
    
    private boolean sendControlToBlenderEnabled=true;
    {setPropertyTooltip("sendControlToBlenderEnabled","sends steering (controlled) and speed (from radio) to albert's blender client");}
    
    private boolean useRouteFromBlender=false;
    {setPropertyTooltip("getRouteFromBlenderEnabled","get the route which has been captured in blender before");}
    
    private boolean showAccelerometerGUI = false;
    {setPropertyTooltip("showAccelerometerGUI", "shows the GUI output for the accelerometer");}
    
    private boolean loggingEnabled = false;
    {setPropertyTooltip("loggingEnabled", "enables logging to driverLog.txt in startup folder (java)");}
    
    TobiLogger tobiLogger = new TobiLogger("driverLog", "#data from Driver\n#timems radioSpeed radioSteering accelTime xAccel yAccel zAccel");
    
    // Variables for the fancy controller
    private float intError=0f; // integral of the weighted error
    private float lastWeightedError=0f; // weighted error of last call
    private int lastTimestamp=0; // timestamp of last package
    private float steeringRange=(float)Math.PI/2; // range of the servo steering
    private float normalizationFactorX=1f; // normlization x
    private float normalizationFactorPhi=(float)Math.PI/8; // normlization phi
    private float normalizationFactorU=(float)Math.PI/8; // normlization steering angle
    
    DrivingController controller;
    //static Logger log=Logger.getLogger("net.sf.jaer");
    private SiLabsC8051F320_USBIO_CarServoController servo;
    private ToradexOakG3AxisAccelerationSensor accelerometer;
    private ToradexOakG3AxisAccelerationSensorGUI acceleromterGUI=null;
    private EventFilter2D lineTracker;
    private PIDController pidController;
    private LQRController lqrController;
    private float radioSteer=0.5f;
    private float radioSpeed=0.5f;
    private float steerCommand = 0.5f;
    private float servoSteerCommand;
    private float speedCommand = 0.5f;
    private float u = 0;
    private float lateralError = 0;
    private float angularError = 0;
    private float maxDeltaT = 0.1f;
    
    // Paths of the text files
    private String routeFromBlenderPath = "C:/Documents and Settings/rritz/Desktop/Temporäre Dateien/blender-2.45-windows/test.txt";
    //private String observerMatrizesPath = "Regler/LQR-Regler/Controller";
    private String observerMatrizesPath = "C:/Documents and Settings/rritz/Eigene Dateien/ETH Zürich/Bachelorarbeit/Dynamisches Modell des RC Monstertrucks/Regler/LQR-Regler/Controller";

    /** Creates a new instance of FancyDriver */
    public FancyDriver(AEChip chip) {
        super(chip);
        initFilter();
        controller=new DrivingController();
        accelerometer = new ToradexOakG3AxisAccelerationSensor();
        try {
            accelerometer.open();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }
    
    private class DrivingController{
        
        void control(EventPacket in){
            // getString values from radio receiver (user sets speed or steers)
            if(servo!=null){
                radioSteer=servo.getRadioSteer();
                radioSpeed=servo.getRadioSpeed();
            }
                 
            // Calculate u
            if (usePIDController) {
                u = pidController.getSteeringAngle(in.getLastTimestamp(), lineTracker);
            } else if (useLQRController) {
                u = lqrController.getSteeringAngle(in.getLastTimestamp(), lineTracker);
            } else {
                u = defaultSteeringAngle;
            }

            // Normalize u
            u = u/steeringRange;
            
            // Check for limits
            if(u < -1){
                u = -1f;
            }
            if(u > 1){
                u = 1f;
            }
            
            // Send steering command
            steerCommand = u;
            speedCommand = getDefaultSpeed()+0.5f;
            servoSteerCommand = - (u/2) + 0.5f;
            float servoSpeedCommand = speedCommand;
            if(servo!=null && servo.isOpen()){
                servo.setSteering(getServoSteerCommand()); // 1 steer right, 0 steer left
                servo.setSpeed(servoSpeedCommand); // set fwd speed
            }
            
            // Send controls over socket to blender
            sendControlToBlender();
            
        }
        
    }
    
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        tobiLogger.log(radioSpeed + " " + radioSteer + " " + accelerometer.getAcceleration());
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
        steerCommand=0.5f;
    }
    
    synchronized public void initFilter() {
        if (useHingeLineTracker){
            lineTracker = new HingeLineTracker(chip);
        } else{
            lineTracker = new HingeLaneTracker(chip);
        }
        setEnclosedFilter(lineTracker);
        pidController = new PIDController(chip);
        lqrController = new LQRController(chip);
        //setEnclosedFilter(pidController);
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    GLU glu=null;
    GLUquadric wheelQuad;
    
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isAnnotationEnabled()) return;
        
        GL2 gl=drawable.getGL().getGL2();
        if(gl==null) return;
        final int radius=10;
        
        // draw steering wheel
        if(glu==null) glu=new GLU();
        if(wheelQuad==null) wheelQuad = glu.gluNewQuadric();
        gl.glPushMatrix();
        {
            gl.glTranslatef(chip.getSizeX()/2, (chip.getSizeY())*4/5,0);
            gl.glLineWidth(6f);
            glu.gluQuadricDrawStyle(wheelQuad,GLU.GLU_FILL);
            glu.gluDisk(wheelQuad,radius,radius+1,16,1);
        }
        gl.glPopMatrix();
        
        // draw steering vector, including external radio input value
        
        gl.glPushMatrix();
        {
            gl.glColor3f(1,1,1);
            gl.glTranslatef(chip.getSizeX()/2, (chip.getSizeY())*4/5,0);
            gl.glLineWidth(6f);
            gl.glBegin(GL2.GL_LINES);
            {
                gl.glVertex2f(0, 0);
                double a = 2 * (servoSteerCommand - 0.5f); // -1 to 1
                a = Math.atan(a);
                float x = radius * (float) Math.sin(a);
                float y = radius * (float) Math.cos(a);
                gl.glVertex2f(x, y);
                if (servo != null && servo.isOpen()) {
                    gl.glColor3f(1, 0, 0);
                    gl.glVertex2f(0, 0);
                    a = 2 * (radioSteer - 0.5f); // -1 to 1
                    a = Math.atan(a);
                    x = radius * (float) Math.sin(a);
                    y = radius * (float) Math.cos(a);
                    gl.glVertex2f(x, y);
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
                gl.glBegin(GL2.GL_LINES);
                {
                    gl.glVertex2f(0,0);
                    gl.glVertex2f(0,chip.getSizeY()*(radioSpeed-0.5f));
                }
                gl.glEnd();
            }
            gl.glPopMatrix();
        }
        
        
    }
    
    public boolean isFlipSteering() {
        return flipSteering;
    }
    
    /** If set true, then drive towards events (road is textured), if false, drive away from events (side is textured). */
    public void setFlipSteering(boolean flipSteering) {
        this.flipSteering = flipSteering;
        getPrefs().putBoolean("Driver.flipSteering",flipSteering);
    }
    
    /** Gets the actual steering command based on flipSteering
     */
    
    public boolean isUseHingeLineTracker() {
        return useHingeLineTracker;
    }
    
    synchronized public void setUseHingeLineTracker(boolean useHingeLineTracker) {
        boolean init=useHingeLineTracker!=this.useHingeLineTracker;
        this.useHingeLineTracker = useHingeLineTracker;
        if(init) {
            // should remove previous filters annotator
            initFilter(); // must rebuild enclosed filter
            if(getChip().getFilterFrame()!=null){
                getChip().getFilterFrame().rebuildContents(); // new enclosed filter, rebuild gui
            }
        }
        getPrefs().putBoolean("Driver.useHingeLineTracker",useHingeLineTracker);
    }
    
    public float getServoSteerCommand() {
         if (flipSteering) return 1 - servoSteerCommand;
        return servoSteerCommand;
    }
    
    public float getDefaultSpeed() {
        return defaultSpeed;
    }
    
    public float getDefaultSteeringAngle() {
        return defaultSteeringAngle;
    }
    
    public void setDefaultSpeed(float defaultSpeed) {
        this.defaultSpeed = defaultSpeed;
        getPrefs().putFloat("Driver.defaultSpeed",defaultSpeed);
    }
    
    public void setDefaultSteeringAngle(float defaultSteeringAngle) {
        this.defaultSteeringAngle = defaultSteeringAngle;
        getPrefs().putFloat("Driver.defaultSteeringAngle",defaultSteeringAngle);
    }
    
    DataOutputStream dos;
    
    /** sends current speed and steering to alberto cardona's blender over the a stream socket opened to blender
     */
    synchronized private void sendControlToBlender(){
        if(!sendControlToBlenderEnabled) return;
        try{
            if(dos==null){
                net.sf.jaer.graphics.AEViewer v=chip.getAeViewer();
                if(v==null) throw new RuntimeException("no viewer");
                AESocket aeSocket=v.getAeSocket();
                if(aeSocket==null) throw new RuntimeException("no aeSocket has been opened to a server of events, no one to send controls to");
                Socket s=aeSocket.getSocket();
                if(s==null) throw new RuntimeException("socket inside AESocket is null, something funny");
                dos=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            }
            dos.writeFloat(2f); // header for albert
            dos.writeFloat(steerCommand);
            dos.writeFloat(speedCommand);
            dos.flush();
//            System.out.println("sent controls steer="+getSteerCommand());
        }catch(Exception e){
            log.warning(e.toString()+": disabling sendControlToBlenderEnabled");
            sendControlToBlenderEnabled=false;
            getSupport().firePropertyChange("sendControlToBlenderEnabled",true,false);
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
    
    public boolean isShowAccelerometerGUI() {
        return showAccelerometerGUI;
    }

    public void setShowAccelerometerGUI(boolean showAccelerometerGUI) {
        this.showAccelerometerGUI = showAccelerometerGUI;
        if (showAccelerometerGUI) {
            if(acceleromterGUI==null){
                acceleromterGUI=new ToradexOakG3AxisAccelerationSensorGUI(accelerometer);
            }
            acceleromterGUI.setVisible(true);
        }else{
            if(acceleromterGUI!=null) acceleromterGUI.setVisible(false);
        }
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
        tobiLogger.setEnabled(loggingEnabled);
    }

    public void setUsePIDController(boolean usePIDController) {
        this.usePIDController = usePIDController;
        pidController = new PIDController(chip);
        getPrefs().putBoolean("Driver.usePIDController",usePIDController);
    }
    
    public boolean getUsePIDController() {
        return usePIDController;
    }
    
    public void setUseLQRController(boolean useLQRController) {
        this.useLQRController = useLQRController;
        lqrController = new LQRController(chip);
        getPrefs().putBoolean("Driver.useLQRController",useLQRController);
    }
    
    public boolean getUseLQRController() {
        return useLQRController;
    }
    
    public void setUseUserK(boolean useUserK) {
        this.useUserK = useUserK;
        getPrefs().putBoolean("Driver.useUserK",useUserK);
    }
    
    public boolean getUseUserK() {
        return useUserK;
    }
    
    public void setUseRouteFromBlender(boolean useRouteFromBlender) {
        this.useRouteFromBlender = useRouteFromBlender;
        getPrefs().putBoolean("Driver.useRouteFromBlender",useRouteFromBlender);
    }
    
    public boolean getUseRouteFromBlender() {
        return useRouteFromBlender;
    }
    
    public void setKi(float ki) {
        this.ki = ki;
        getPrefs().putFloat("Driver.ki",ki);
    }

    public float getKi() {
        return ki;
    }

    public void setKd(float kd) {
        this.kd = kd;
        getPrefs().putFloat("Driver.kd",kd);
    }

    public float getKd() {
        return kd;
    }

    public void setKp(float kp) {
        this.kp = kp;
        getPrefs().putFloat("Driver.kp",kp);
    }

    public float getKp() {
        return kp;
    }
    
    public void setXK(float xK) {
        this.xK = xK;
        getPrefs().putFloat("Driver.xK",xK);
    }

    public float getXK() {
        return xK;
    }
    
    public void setXpK(float xpK) {
        this.xpK = xpK;
        getPrefs().putFloat("Driver.xpK",xpK);
    }

    public float getXpK() {
        return xpK;
    }
    
    public void setPhiK(float phiK) {
        this.phiK = phiK;
        getPrefs().putFloat("Driver.phiK",phiK);
    }

    public float getPhiK() {
        return phiK;
    }
    
    public void setPhipK(float phipK) {
        this.phipK = phipK;
        getPrefs().putFloat("Driver.phipK",phipK);
    }

    public float getPhipK() {
        return phipK;
    }

    public float getLateralGain() {
        return lateralGain;
    }

    public void setLateralGain(float lateralGain) {
        this.lateralGain = lateralGain;
        getPrefs().putFloat("Driver.lateralGain",lateralGain);
    }

    public float getAngleGain() {
        return angleGain;
    }

    public void setAngleGain(float angleGain) {
        this.angleGain = angleGain;
        getPrefs().putFloat("Driver.angleGain",angleGain);
    }
    
    private class PIDController {
        
        public PIDController(AEChip chip) {
            
        }

        public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
            return in;
        }

        public float getSteeringAngle(int currentTimestamp, EventFilter2D lineTracker) {

            // Get weighted error
            float weightedError=getWeightedError(lineTracker);

            // Calculate time since last packet in seconds
            float deltaT = (currentTimestamp - lastTimestamp)/1000000f; // time in seconds since last packet
            lastTimestamp = currentTimestamp;
            if (deltaT == 0.0) return u;

            // Calculate proportional component
            float ComponentKp = -kp*weightedError;

            // Calculate integral component
            intError = intError+deltaT*(lastWeightedError+weightedError)/2;
            float ComponentKi = -ki*intError;

            // Calculate derivative component
            float Derivative = (weightedError-lastWeightedError)/deltaT;
            float ComponentKd = -kd*Derivative;

            // Calculate u
            return (ComponentKp+ComponentKi+ComponentKd)*normalizationFactorU;

        }

        private float getWeightedError(EventFilter2D lineTracker) {

            // Get Filter Data
            float localPhi=(float) ((HingeDetector) lineTracker).getPhi();
            float localX=(float) ((HingeDetector) lineTracker).getX();
            
            if (useRouteFromBlender) {
                getRouteFromBlender();
                localPhi=angularError;
                localX=lateralError;
            }

            // Calculate weighted error
            return lateralGain*localX-angleGain*localPhi;
        }
    }
    
    private class LQRController {
        
        SystemStates observerStates = new SystemStates();
        float observerA[][] = new float[4][4];
        float observerB[][] = new float[4][2];
        float observerC[][] = new float[1][4];
        float observerD[][] = new float[1][2];
        boolean controllerInitialized = false;
        
        public LQRController(AEChip chip) {
            
        }

        public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
            return in;
        }

        public float getSteeringAngle(int currentTimestamp, EventFilter2D lineTracker) {

            // Load system matixes on first call
            if (!controllerInitialized) {
                loadObserverMatrizes();
                observerStates.x = 0f;
                observerStates.xp = 0f;
                observerStates.phi = 0f;
                observerStates.phip = 0f;
                controllerInitialized = true;
            }
            
            // Update errors
            updateErrors(lineTracker);
            if (useRouteFromBlender) {
                getRouteFromBlender();
            }
            
            // Update observer states
            observerStates = updateObserverStates2(currentTimestamp, observerStates);
            //System.out.println("x: "+observerStates.x+" xp:"+observerStates.xp+"phi: "+observerStates.phi+" phip:"+observerStates.phip);
            
            // Calculate steering angle
            float steeringAngle;
            if (useUserK) {
                steeringAngle = observerC[0][0]*observerStates.x + observerC[0][1]*observerStates.xp + observerC[0][2]*observerStates.phi + observerC[0][3]*observerStates.phip;
                steeringAngle = steeringAngle + observerD[0][0]*lateralError/normalizationFactorX + observerD[0][1]*-angularError/normalizationFactorPhi;
            } else {
                steeringAngle = -(xK*observerStates.x + xpK*observerStates.xp + phiK*observerStates.phi + phipK*observerStates.phip);
            }
            // Return steering angle
            return steeringAngle*normalizationFactorU;

        }
        
        private SystemStates updateObserverStates(int currentTimestamp, SystemStates oldObserverStates) {
            
            // Initialize variable
            SystemStates newObserverStates = new SystemStates();
            
            // Calculate time since last packet in seconds
            float deltaT = (currentTimestamp - lastTimestamp)/1000000f; // time in seconds since last packet
            lastTimestamp = currentTimestamp;
            
            // Calculate Deltas
            float deltaX = deltaT * (observerA[0][0]*oldObserverStates.x + observerA[0][1]*oldObserverStates.xp + observerA[0][2]*oldObserverStates.phi + observerA[0][3]*oldObserverStates.phip);
            float deltaXp = deltaT * (observerA[1][0]*oldObserverStates.x + observerA[1][1]*oldObserverStates.xp + observerA[1][2]*oldObserverStates.phi + observerA[1][3]*oldObserverStates.phip);
            float deltaPhi = deltaT * (observerA[2][0]*oldObserverStates.x + observerA[2][1]*oldObserverStates.xp + observerA[2][2]*oldObserverStates.phi + observerA[2][3]*oldObserverStates.phip);
            float deltaPhip = deltaT * (observerA[3][0]*oldObserverStates.x + observerA[3][1]*oldObserverStates.xp + observerA[3][2]*oldObserverStates.phi + observerA[3][3]*oldObserverStates.phip);
            
            deltaX = deltaX + deltaT * (observerB[0][0]*lateralError/normalizationFactorX + observerB[0][1]*-angularError/normalizationFactorPhi);
            deltaXp = deltaXp + deltaT * (observerB[1][0]*lateralError/normalizationFactorX + observerB[1][1]*-angularError/normalizationFactorPhi);
            deltaPhi = deltaPhi + deltaT * (observerB[2][0]*lateralError/normalizationFactorX + observerB[2][1]*-angularError/normalizationFactorPhi);
            deltaPhip = deltaPhip + deltaT * (observerB[3][0]*lateralError/normalizationFactorX + observerB[3][1]*-angularError/normalizationFactorPhi);
            
            // Calculate new states
            if (deltaT<maxDeltaT) {
                newObserverStates.x = oldObserverStates.x + deltaX;
                newObserverStates.xp = oldObserverStates.xp + deltaXp;
                newObserverStates.phi = oldObserverStates.phi + deltaPhi;
                newObserverStates.phip = oldObserverStates.phip + deltaPhip;
            } else {
                newObserverStates.x = 0f;
                newObserverStates.xp = 0f;
                newObserverStates.phi = 0f;
                newObserverStates.phip = 0f;
            }
            
            // Return new states
            return newObserverStates;
            
        }
        
        private SystemStates updateObserverStates2(int currentTimestamp, SystemStates oldObserverStates) {
            
            // Initialize variable
            SystemStates newObserverStates = new SystemStates();
            
            // Calculate time since last packet in seconds
            float deltaT = (currentTimestamp - lastTimestamp)/1000000f; // time in seconds since last packet
            lastTimestamp = currentTimestamp;
            if (deltaT == 0.0) return oldObserverStates;
            
            // Calculate new states
            newObserverStates.x = lateralError;
            newObserverStates.xp = (newObserverStates.x - oldObserverStates.x)/deltaT;
            newObserverStates.phi = -angularError;
            newObserverStates.phip = (newObserverStates.phi - oldObserverStates.phi)/deltaT;

            // Return new states
            return newObserverStates;
            
        }
        
        private void updateErrors(EventFilter2D lineTracker) {

            // Get Filter Data
            lateralError = (float) ((HingeDetector) lineTracker).getX();
            angularError = (float) ((HingeDetector) lineTracker).getPhi();

        }
        
        private void loadObserverMatrizes() {
            try {
                
                String line = null;
                String temp[] = null;
                
                BufferedReader fileA = new BufferedReader(new FileReader(observerMatrizesPath+"/A.txt"));
                for(int i=0; i<4; i++){
                    line = String.valueOf(fileA.readLine()).toString();
                    line = line.trim();
                    line = line.replace("  ", " ");
                    temp = line.split(" ");
                    observerA[i][0] = Float.valueOf(temp[0].trim()).floatValue();
                    observerA[i][1] = Float.valueOf(temp[1].trim()).floatValue();
                    observerA[i][2] = Float.valueOf(temp[2].trim()).floatValue();
                    observerA[i][3] = Float.valueOf(temp[3].trim()).floatValue();
                }
                
                BufferedReader fileB = new BufferedReader(new FileReader(observerMatrizesPath+"/B.txt"));
                for(int i=0; i<4; i++){
                    line = String.valueOf(fileB.readLine()).toString();
                    line = line.trim();
                    line = line.replace("  ", " ");
                    temp = line.split(" ");
                    observerB[i][0] = Float.valueOf(temp[0].trim()).floatValue();
                    observerB[i][1] = Float.valueOf(temp[1].trim()).floatValue();
                }
                
                BufferedReader fileC = new BufferedReader(new FileReader(observerMatrizesPath+"/C.txt"));
                for(int i=0; i<1; i++){
                    line = String.valueOf(fileC.readLine()).toString();
                    line = line.trim();
                    line = line.replace("  ", " ");
                    temp = line.split(" ");
                    observerC[i][0] = Float.valueOf(temp[0].trim()).floatValue();
                    observerC[i][1] = Float.valueOf(temp[1].trim()).floatValue();
                    observerC[i][2] = Float.valueOf(temp[2].trim()).floatValue();
                    observerC[i][3] = Float.valueOf(temp[3].trim()).floatValue();
                }
                
                BufferedReader fileD = new BufferedReader(new FileReader(observerMatrizesPath+"/D.txt"));
                for(int i=0; i<1; i++){
                    line = String.valueOf(fileD.readLine()).toString();
                    line = line.trim();
                    line = line.replace("  ", " ");
                    temp = line.split(" ");
                    observerD[i][0] = Float.valueOf(temp[0].trim()).floatValue();
                    observerD[i][1] = Float.valueOf(temp[1].trim()).floatValue();
                }
        
            } catch (IOException e) {
		e.printStackTrace();
            }
            
        }
        
        private class SystemStates {
            float x;
            float xp;
            float phi;
            float phip;
        }
        
    }
    
    //DataInputStream mydis;
    
    synchronized private void getRouteFromBlender(){
        try {
            BufferedReader in = new BufferedReader(new FileReader(routeFromBlenderPath));
            lateralError = Float.valueOf(in.readLine()).floatValue();
            angularError = Float.valueOf(in.readLine()).floatValue();          
	} catch (IOException e) {
            e.printStackTrace();
	}
        
//        try{
//            if(mydis==null){
//                Socket mySocket = new Socket("localhost",1234);
//                mydis = new DataInputStream(new BufferedInputStream(mySocket.getInputStream()));
//                long test = mydis.readLong();
//                System.out.println(test);
//            }
////            
//        }catch(Exception e){
//            log.warning(e.toString()+": disabling sendControlToBlenderEnabled");
//            sendControlToBlenderEnabled=false;
//            support.firePropertyChange("sendControlToBlenderEnabled",true,false);
//        }
        
    }
 
}
