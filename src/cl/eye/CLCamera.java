//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// This file is part of CL-EyeMulticam SDK
//
// Java JNI CLEyeMulticam wrapper
//
// It allows the use of multiple CL-Eye cameras in your own Java applications
//
// For updates and file downloads go to: http://codelaboratories.com/research/view/cl-eye-muticamera-sdk
//
// Copyright 2008-2010 (c) Code Laboratories, Inc. All rights reserved.
//
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package cl.eye;
//import processing.core.*;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/** Interface to Code Laboratories driver to Playstation Eye (PS Eye) camera.
 * See <a href="http://codelaboratories.com/research/view/cl-eye-muticamera-api">CodeLaboratories DL Eye Multicam C++ API</a> for the native API
 * 
 * @author CodeLaboratories / jaer adaptation by tobi
 */
public class CLCamera implements HardwareInterface {

    protected final static Logger log = Logger.getLogger("CLEye");
    protected static Preferences prefs=Preferences.userNodeForPackage(CLCamera.class);
    /** Set true if library was loaded successfully. */
    private static boolean libraryLoaded = false;
    private final static String DLLNAME = "CLEyeMulticam";
    private int cameraIndex = 0; // index of camera to open
    private int cameraInstance = 0;
    private boolean isOpened = false;
    /* removed by mlk as frameRate incorporated into mode
    private int frameRateHz = prefs.getInt("CLCamera.frameRateHz",60);
     * 
     */
    
    public  final static int numModes = CameraMode.values().length;
    private CameraMode cameraMode = CameraMode.QVGA_MONO_60; // default camera mode
    // static methods

    static {
        if (!isLibraryLoaded() && System.getProperty("os.name").startsWith("Windows")) {
            try {
                synchronized (CLCamera.class) { // prevent multiple access in class initializers like hardwareInterfaceFactory and SubClassFinder
//                    log.info("loading library " + System.mapLibraryName(DLLNAME));
//                    try {
//                        Thread.sleep(200);
//                    } catch (InterruptedException ex) {
//                    }
                    System.loadLibrary(DLLNAME);
                    setLibraryLoaded(true);
                }
                log.info("CLEyeMulticam available");
            } catch (UnsatisfiedLinkError e1) {
                String lp = null;
                try {
                    lp = System.getProperty("java.library.path");
                } catch (Exception e) {
                    log.warning("caught " + e + " when trying to call System.getProperty(\"java.library.path\")");
                }
                log.warning("could not load the " + DLLNAME + " DLL; check native library path which is currently " + lp);
                setLibraryLoaded(false);
            }
        }
    }

    // Possible camera modes
    public enum CameraMode {
        QVGA_MONO_15(CLEYE_QVGA, CLEYE_MONO_PROCESSED, 15),
        QVGA_MONO_30(CLEYE_QVGA, CLEYE_MONO_PROCESSED, 30),
        QVGA_MONO_60(CLEYE_QVGA, CLEYE_MONO_PROCESSED, 60),
        QVGA_MONO_75(CLEYE_QVGA, CLEYE_MONO_PROCESSED, 75),
        QVGA_MONO_100(CLEYE_QVGA, CLEYE_MONO_PROCESSED, 100),
        QVGA_MONO_125(CLEYE_QVGA, CLEYE_MONO_PROCESSED, 125),
        QVGA_COLOR_15(CLEYE_QVGA, CLEYE_COLOR_PROCESSED, 15),
        QVGA_COLOR_30(CLEYE_QVGA, CLEYE_COLOR_PROCESSED, 30),
        QVGA_COLOR_60(CLEYE_QVGA, CLEYE_COLOR_PROCESSED, 60),
        QVGA_COLOR_75(CLEYE_QVGA, CLEYE_COLOR_PROCESSED, 75),
        QVGA_COLOR_100(CLEYE_QVGA, CLEYE_COLOR_PROCESSED, 100),
        QVGA_COLOR_125(CLEYE_QVGA, CLEYE_COLOR_PROCESSED, 125);
        
        int resolution;
        int color;
        int frameRateHz;

        CameraMode(int resolution, int color, int frameRateHz) {
            this.resolution = resolution;
            this.color = color;
            this.frameRateHz = frameRateHz;
        }
    }
    // camera color mode
    public static final int CLEYE_MONO_PROCESSED = 0;
    public static final int CLEYE_COLOR_PROCESSED = 1;
    public static final int CLEYE_MONO_RAW = 2;
    public static final int CLEYE_COLOR_RAW = 3;
    public static final int CLEYE_BAYER_RAW = 4;
    // camera resolution
    public static final int CLEYE_QVGA = 0;
    public static final int CLEYE_VGA = 1;
    // camera sensor parameters
    public static final int CLEYE_AUTO_GAIN = 0;  	// [0, 1]
    public static final int CLEYE_GAIN = 1;	// [0, 79]
    public static final int CLEYE_AUTO_EXPOSURE = 2;    // [0, 1]
    public static final int CLEYE_EXPOSURE = 3;    // [0, 511]
    public static final int CLEYE_AUTO_WHITEBALANCE = 4;	// [0, 1]
    public static final int CLEYE_WHITEBALANCE_RED = 5;	// [0, 255]
    public static final int CLEYE_WHITEBALANCE_GREEN = 6;   	// [0, 255]
    public static final int CLEYE_WHITEBALANCE_BLUE = 7;    // [0, 255]
    // camera linear transform parameters
    public static final int CLEYE_HFLIP = 8;    // [0, 1]
    public static final int CLEYE_VFLIP = 9;    // [0, 1]
    public static final int CLEYE_HKEYSTONE = 10;   // [-500, 500]
    public static final int CLEYE_VKEYSTONE = 11;   // [-500, 500]
    public static final int CLEYE_XOFFSET = 12;   // [-500, 500]
    public static final int CLEYE_YOFFSET = 13;   // [-500, 500]
    public static final int CLEYE_ROTATION = 14;   // [-500, 500]
    public static final int CLEYE_ZOOM = 15;   // [-500, 500]
    // camera non-linear transform parameters
    public static final int CLEYE_LENSCORRECTION1 = 16;	// [-500, 500]
    public static final int CLEYE_LENSCORRECTION2 = 17;	// [-500, 500]
    public static final int CLEYE_LENSCORRECTION3 = 18;	// [-500, 500]
    public static final int CLEYE_LENSBRIGHTNESS = 19;	// [-500, 500]
    public static final int[] CLEYE_FRAME_RATES = {15, 30, 60, 75, 100, 125}; // TODO only QVGA now
    static{Arrays.sort(CLEYE_FRAME_RATES);}

    native static int CLEyeGetCameraCount();

    native static String CLEyeGetCameraUUID(int index);

    native static int CLEyeCreateCamera(int cameraIndex, int mode, int resolution, int framerate);

    native static boolean CLEyeDestroyCamera(int cameraInstance);

    native static boolean CLEyeCameraStart(int cameraInstance);

    native static boolean CLEyeCameraStop(int cameraInstance);

    native static boolean CLEyeSetCameraParameter(int cameraInstance, int param, int val);

    native static int CLEyeGetCameraParameter(int cameraInstance, int param);

    native static boolean CLEyeCameraGetFrame(int cameraInstance, int[] imgData, int waitTimeout);

    /**
     * @return the libraryLoaded
     */
    synchronized public static boolean isLibraryLoaded() {
        return libraryLoaded;
    }

    /**
     * @param aLibraryLoaded the libraryLoaded to set
     */
    synchronized public static void setLibraryLoaded(boolean aLibraryLoaded) {
        libraryLoaded = aLibraryLoaded;
    }

    public static int cameraCount() {
        return CLEyeGetCameraCount();
    }

    public static String cameraUUID(int index) {
        return CLEyeGetCameraUUID(index);
    }

    /** Constructs instance for first camera
     * 
     */
    public CLCamera() {
    }

    /** Constructs instance to open the cameraIndex camera
     * 
     * @param cameraIndex 0 based index of cameras.
     */
    CLCamera(int cameraIndex) {
        this.cameraIndex = cameraIndex;
    }
    
    CLCamera(int cameraIndex, CameraMode cameraMode) {
        this.cameraIndex = cameraIndex;
        this.cameraMode = cameraMode;
    }

    synchronized private boolean createCamera(int cameraIndex, int mode, int resolution, int framerate) {
        cameraInstance = CLEyeCreateCamera(cameraIndex, mode, resolution, framerate);
        return cameraInstance != 0;
    }

    synchronized private boolean destroyCamera() {
        if (this.cameraInstance == 0) return true;
        return CLEyeDestroyCamera(this.cameraInstance);
    }
    
    protected boolean cameraStarted = false;

    /** Starts the camera
     * 
     * @return true if successful or if already started
     */
    synchronized public boolean startCamera() {
        if (cameraStarted) {
            return true;
        }
        cameraStarted = CLEyeCameraStart(cameraInstance);
        return cameraStarted;
    }

    /** Stops the camera
     * 
     * @return true if successful or if not started
     */
    synchronized public boolean stopCamera() {
        if (!cameraStarted) {
            return true;
        }
        boolean stopped = CLEyeCameraStop(cameraInstance);
        cameraStarted = false;
        return stopped;
    }

    /** Gets frame data
     * 
     * @param imgData
     * @param waitTimeout in ms
     * @return true if successful
     * @throws HardwareInterfaceException if there is an error
     */
    synchronized public void getCameraFrame(int[] imgData, int waitTimeout) throws HardwareInterfaceException {
        if (!cameraStarted || !CLEyeCameraGetFrame(cameraInstance, imgData, waitTimeout)) {
            try {
                // Added to give external thread time to catch up as cannot synchronize directly
                Thread.sleep(500);
            } catch (InterruptedException e) {}
            throw new HardwareInterfaceException("capturing frame");
        }
    }

    synchronized public boolean setCameraParam(int param, int val) {
        if (cameraInstance == 0) {
            return false;
        }
        return CLEyeSetCameraParameter(cameraInstance, param, val);
    }

    public int getCameraParam(int param) {
        return CLEyeGetCameraParameter(cameraInstance, param);
    }

    @Override
    public String getTypeName() {
        return "CLEye PS Eye camera";
    }

    synchronized private void dispose() {
        stopCamera();
        destroyCamera();
    }

    /** Stops the camera.
     * 
     */
    @Override
    synchronized public void close() {
        if(!isOpened) return;
        isOpened = false;
        boolean stopped=stopCamera();
        if(!stopped){
            log.warning("stopCamera returned an error");
        }
        boolean destroyed=destroyCamera();
        if(!destroyed){
            log.warning("destroyCamera returned an error");
        }
        cameraInstance=0;
    }

    /** Opens the cameraIndex camera with some default settings and starts the camera. Set the frameRateHz before calling open().
     * 
     * @throws HardwareInterfaceException 
     */
    @Override
    synchronized public void open() throws HardwareInterfaceException {
        if (isOpened) {
            return;
        }
//        if (cameraInstance == 0) { // only make one instance, don't destroy it on close
            /* removed by mlk as settings included in mode
            boolean gotCam = createCamera(cameraIndex, colorMode.code, CLEYE_QVGA, getFrameRateHz()); // TODO fixed settings now
            */
            boolean gotCam = createCamera(cameraIndex, this.cameraMode.color, this.cameraMode.resolution, 
                    this.cameraMode.frameRateHz);
            if (!gotCam) {
                throw new HardwareInterfaceException("couldn't get camera");
            }
//        }
        if (!startCamera()) {
            throw new HardwareInterfaceException("couldn't start camera");
        }
        isOpened = true;
    }

    @Override
    public boolean isOpen() {
        return isOpened;
    }

    public CameraMode getCameraMode() {
        return this.cameraMode;
    }
    
    synchronized public void setCameraMode(CameraMode cameraMode) {
        this.cameraMode = cameraMode;
    }
    
    synchronized public void setCameraMode(int cameraModeIndex) {
        if (cameraModeIndex < 0 || cameraModeIndex >= numModes) {
            log.warning("Invalid Mode index " + cameraModeIndex + " leaving mode unchanged.");
        }
        else this.cameraMode = CameraMode.values()[cameraModeIndex];
    }
    
    /**
     * @return the frameRateHz
     */
    /* removed by mlk as frameRate incorporated into mode
    public int getFrameRateHz() {
        return frameRateHz;
    }
     * 
     */

    /**
     * @param frameRateHz the frameRateHz to set
     */
    /* removed by mlk as frameRate incorporated into mode
    synchronized public void setFrameRateHz(int frameRateHz) throws HardwareInterfaceException {
        int old = this.frameRateHz;

        int closest = closestRateTo(frameRateHz);
        if (closest != frameRateHz) {
            log.warning("returning closest allowed frame rate of " + closest + " from desired rate " + frameRateHz);
            frameRateHz = closest;
        }
        if (old != frameRateHz && isOpen()) {
            log.warning("new frame rate of " + frameRateHz + " will only take effect when camera is next opened");
        }
        this.frameRateHz = frameRateHz;
        prefs.putInt("CLCamera.frameRateHz", this.frameRateHz);
    }

    private int closestRateTo(int rate) {
        int ind = Arrays.binarySearch(CLEYE_FRAME_RATES, rate);
        if(-ind>=CLEYE_FRAME_RATES.length){
            return CLEYE_FRAME_RATES[CLEYE_FRAME_RATES.length];
        }
        if (ind <0) {
            return CLEYE_FRAME_RATES[-ind-1];
        }
        return CLEYE_FRAME_RATES[ind];
    }
     * 
     */

    // http://codelaboratories.com/research/view/cl-eye-muticamera-api
    /** Thrown for invalid parameters */
    public class InvalidParameterException extends Exception {

        public InvalidParameterException(String message) {
            super(message);
        }
    }

      /** Sets the gain value.
     * 
     * @param gain gain value, range 0-79
     * @throws HardwareInterfaceException if there is a hardware exception signaled by false return from driver
     * @throws cl.eye.CLCamera.InvalidParameterException if parameter is invalid (outside range)
     */
    synchronized public void setGain(int gain) throws HardwareInterfaceException, InvalidParameterException {
        if (gain < 0) {
            throw new InvalidParameterException("tried to set gain<0 (" + gain + ")");
        }
        if (gain > 79) {
            throw new InvalidParameterException("tried to set gain>79 (" + gain + ")");
        }
        if (!setCameraParam(CLEYE_GAIN, gain)) {
            throw new HardwareInterfaceException("setting gain to " + gain);
        }
    }

    /** Asks the driver for the gain value.
     * 
     * @return gain value 
     */
    public int getGain() {
        int gain = getCameraParam(CLEYE_GAIN);
        return gain;
    }

    /** Sets the exposure value.
     * 
     * @param exp exposure value, range 0-511
     * @throws HardwareInterfaceException if there is a hardware exception signaled by false return from driver
     * @throws cl.eye.CLCamera.InvalidParameterException if parameter is invalid (outside range)
     */
    synchronized public void setExposure(int exp) throws HardwareInterfaceException, InvalidParameterException {
        if (exp < 0) {
            throw new InvalidParameterException("tried to set exposure<0 (" + exp + ")");
        }
        if (exp > 511) {
            throw new InvalidParameterException("tried to set exposure>511 (" + exp + ")");
        }
        if (!setCameraParam(CLEYE_EXPOSURE, exp)) {
            throw new HardwareInterfaceException("setting exposure to " + exp);
        }
    }

   /** Asks the driver for the exposure value.
     * 
     * @return exposure value 
     */
    public int getExposure() {
        int gain = getCameraParam(CLEYE_EXPOSURE);
        return gain;
    }

    /** Enables auto gain
     * 
     * @param yes
     * @throws HardwareInterfaceException 
     */
    synchronized public void setAutoGain(boolean yes) throws HardwareInterfaceException {
        if (!setCameraParam(CLEYE_AUTO_GAIN, yes ? 1 : 0)) {
            throw new HardwareInterfaceException("setting auto gain=" + yes);
        }
    }

    public boolean isAutoGain() {
        return getCameraParam(CLEYE_AUTO_GAIN) != 0;
    }

    /** Enables auto exposure
     * 
     * @param yes
     * @throws HardwareInterfaceException 
     */
    synchronized public void setAutoExposure(boolean yes) throws HardwareInterfaceException {
        if (!setCameraParam(CLEYE_AUTO_EXPOSURE, yes ? 1 : 0)) {
            throw new HardwareInterfaceException("setting auto exposure=" + yes);
        }
    }

    public boolean isAutoExposure() {
        return getCameraParam(CLEYE_AUTO_EXPOSURE) != 0;
    }
}
