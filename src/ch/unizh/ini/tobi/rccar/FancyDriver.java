
package ch.unizh.ini.tobi.rccar;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventio.*;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.hardwareinterface.usb.toradex.ToradexOakG3AxisAccelerationSensor;
import ch.unizh.ini.caviar.hardwareinterface.usb.toradex.ToradexOakG3AxisAccelerationSensor.Acceleration;
import ch.unizh.ini.caviar.hardwareinterface.usb.toradex.ToradexOakG3AxisAccelerationSensorGUI;
//import ch.unizh.ini.caviar.eventio.AEServerSocket;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.filter.*;
import ch.unizh.ini.caviar.eventprocessing.label.*;
//import ch.unizh.ini.caviar.eventprocessing.label.SimpleOrientationFilter;
import ch.unizh.ini.caviar.eventprocessing.tracking.*;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.*;
//import ch.unizh.ini.caviar.util.filter.LowpassFilter;
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
import ch.unizh.ini.caviar.util.TobiLogger;

/**
 * The FancyDriver controls the RcCar 

 
 * @author Christian Braendli, Robin Ritz
 */
public class FancyDriver extends EventFilter2D implements FrameAnnotater{
    
    private boolean usePIDController=getPrefs().getBoolean("Driver.usePIDController",true);
    {setPropertyTooltip("usePIDController","use the PID controller");}
    
    private boolean useLQRController=getPrefs().getBoolean("Driver.useLQRController",false);
    {setPropertyTooltip("useLQRController","use the LQR controller");}
    
    private float kp=getPrefs().getFloat("Driver.kp",10);
    {setPropertyTooltip("kp","proportinal gain of the pid controller");}

    private float ki=getPrefs().getFloat("Driver.ki",0);
    {setPropertyTooltip("ki","integral gain of the pid controller");}

    private float kd=getPrefs().getFloat("Driver.kd",0);
    {setPropertyTooltip("kd","derivative gain of the pid controller");}

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
    
    private boolean sendControlToBlenderEnabled=true;
    {setPropertyTooltip("sendControlToBlenderEnabled","sends steering (controlled) and speed (from radio) to albert's blender client");}
    
    private boolean showAccelerometerGUI = false;
    {setPropertyTooltip("showAccelerometerGUI", "shows the GUI output for the accelerometer");}
    
    private boolean loggingEnabled = false;
    {setPropertyTooltip("loggingEnabled", "enables logging to driverLog.txt in startup folder (java)");}
    
    TobiLogger tobiLogger = new TobiLogger("driverLog", "#data from Driver\n#timems radioSpeed radioSteering accelTime xAccel yAccel zAccel");
    
    // Variables for the fancy pid controller
    private float intError=0f; // integral of the weighted error
    private float lastWeightedError=0f; // weighted error of last call
    private int lastTimestamp=0; // timestamp of last package
    private float steeringRange=(float)Math.PI/2; // range of the servo steering
    
    DrivingController controller;
    //static Logger log=Logger.getLogger("FancyDriver");
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
    
    // Paths of the text files
    private String routeFromBlenderPath = "C:/Documents and Settings/rritz/Desktop/Temporäre Dateien/blender-2.45-windows/test.txt";
    private String observerMatrizesPath = "";


    /** Creates a new instance of FancyDriver */
    public FancyDriver(AEChip chip) {
        super(chip);
        chip.getCanvas().addAnnotator(this);
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
            // get values from radio receiver (user sets speed or steers)
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
                u = 0f;
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
            //getRouteFromBlender();
        }
        
    }
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
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
        
        GL gl=drawable.getGL();
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
            gl.glBegin(GL.GL_LINES);
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
            chip.getCanvas().removeAnnotator((FrameAnnotater)lineTracker);
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
            dos.writeFloat(steerCommand);
            dos.writeFloat(speedCommand);
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
        getPrefs().putBoolean("Driver.usePIDController",usePIDController);
    }
    
    public boolean getUsePIDController() {
        return usePIDController;
    }
    
    public void setUseLQRController(boolean useLQRController) {
        this.useLQRController = useLQRController;
        getPrefs().putBoolean("Driver.useLQRController",useLQRController);
    }
    
    public boolean getUseLQRController() {
        return useLQRController;
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

        public EventPacket<?> filterPacket(EventPacket<?> in) {
            return in;
        }

        public float getSteeringAngle(int currentTimestamp, EventFilter2D lineTracker) {

            // Get weighted error
            float weightedError=getWeightedError(lineTracker);

            // Calculate time since last packet in seconds
            float DeltaT = (currentTimestamp - lastTimestamp)/1000000f; // time in seconds since last packet
            lastTimestamp = currentTimestamp;

            // Calculate proportional component
            float ComponentKp = -kp*weightedError;

            // Calculate integral component
            intError = intError+DeltaT*(lastWeightedError+weightedError)/2;
            float ComponentKi = -ki*intError;

            // Calculate derivative component
            float Derivative = (weightedError-lastWeightedError)/DeltaT;
            float ComponentKd = -kd*Derivative;

            // Calculate u
            return ComponentKp+ComponentKi+ComponentKd;

        }

        private float getWeightedError(EventFilter2D lineTracker) {

            // Get Filter Data
            float localPhi=(float) ((HingeDetector) lineTracker).getPhi();
            float localX=(float) ((HingeDetector) lineTracker).getX();

            // Calculate weighted error
            return lateralGain*localX-angleGain*localPhi;
        }
    }
    
    private class LQRController {
        
        SystemStates observerStates;
        float observerA[][] = new float[4][4];
        float observerB[][] = new float[4][2];
        float observerC[][] = new float[1][4];
        float observerD[][] = new float[1][2];
        
        public LQRController(AEChip chip) {
            
        }

        public EventPacket<?> filterPacket(EventPacket<?> in) {
            return in;
        }

        public float getSteeringAngle(int currentTimestamp, EventFilter2D lineTracker) {

            // Load system matixes on first call
            if (observerA == null || observerB == null || observerC == null || observerD == null) {
                loadObserverMatrizes();
            }
            
            // Update errors
            updateErrors(lineTracker);
            
            // Update observer states
            observerStates = updateObserverStates(currentTimestamp, observerStates);
            
            // Calculate steering angle
            float steeringAngle = observerC[0][0]*observerStates.x + observerC[0][1]*observerStates.xp + observerC[0][2]*observerStates.phi + observerC[0][3]*observerStates.phip;
            steeringAngle = steeringAngle + observerD[0][0]*lateralError + observerD[0][1]*angularError;
            
            // Return steering angle
            return steeringAngle;

        }
        
        private SystemStates updateObserverStates(int currentTimestamp, SystemStates oldObserverStates) {
            
            // Initialize variable
            SystemStates newObserverStates=null;
            
            // Calculate time since last packet in seconds
            float DeltaT = (currentTimestamp - lastTimestamp)/1000000f; // time in seconds since last packet
            lastTimestamp = currentTimestamp;
              
            // Calculate Deltas
            float deltaX = DeltaT * (observerA[0][0]*oldObserverStates.x + observerA[0][1]*oldObserverStates.xp + observerA[0][2]*oldObserverStates.phi + observerA[0][3]*oldObserverStates.phip);
            float deltaXp = DeltaT * (observerA[1][0]*oldObserverStates.x + observerA[1][1]*oldObserverStates.xp + observerA[1][2]*oldObserverStates.phi + observerA[1][3]*oldObserverStates.phip);
            float deltaPhi = DeltaT * (observerA[2][0]*oldObserverStates.x + observerA[2][1]*oldObserverStates.xp + observerA[2][2]*oldObserverStates.phi + observerA[2][3]*oldObserverStates.phip);
            float deltaPhip = DeltaT * (observerA[3][0]*oldObserverStates.x + observerA[3][1]*oldObserverStates.xp + observerA[3][2]*oldObserverStates.phi + observerA[3][3]*oldObserverStates.phip);
            
            deltaX = deltaX + DeltaT * (observerB[0][0]*lateralError + observerB[0][1]*angularError);
            deltaXp = deltaXp + DeltaT * (observerB[1][0]*lateralError + observerB[1][1]*angularError);
            deltaPhi = deltaPhi + DeltaT * (observerB[2][0]*lateralError + observerB[2][1]*angularError);
            deltaPhip = deltaPhip + DeltaT * (observerB[3][0]*lateralError + observerB[3][1]*angularError);
            
            // Calculate new states
            newObserverStates.x = oldObserverStates.x + deltaX;
            newObserverStates.xp = oldObserverStates.xp + deltaXp;
            newObserverStates.phi = oldObserverStates.phi + deltaPhi;
            newObserverStates.phip = oldObserverStates.phip + deltaPhip;
            
            // Return new states
            return newObserverStates;
            
        }
        
        private void updateErrors(EventFilter2D lineTracker) {

            // Get Filter Data
            lateralError = (float) ((HingeDetector) lineTracker).getX();
            angularError = (float) ((HingeDetector) lineTracker).getPhi();

        }
        
        private void loadObserverMatrizes() {
            
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
        if(!sendControlToBlenderEnabled) return;
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
