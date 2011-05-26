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

import java.util.logging.Level;
import java.util.logging.Logger;
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
    /** Set true if library was loaded successfully. */
    private static boolean libraryLoaded = false;
    private final static String DLLNAME = "CLEyeMulticam";
    private int cameraIndex = 0; // index of camera to open

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
    // camera color mode
    public static int CLEYE_MONO_PROCESSED = 0;
    public static int CLEYE_COLOR_PROCESSED = 1;
    public static int CLEYE_MONO_RAW = 2;
    public static int CLEYE_COLOR_RAW = 3;
    public static int CLEYE_BAYER_RAW = 4;
    // camera resolution
    public static int CLEYE_QVGA = 0;
    public static int CLEYE_VGA = 1;
    // camera sensor parameters
    public static int CLEYE_AUTO_GAIN = 0;  	// [0, 1]
    public static int CLEYE_GAIN = 1;	// [0, 79]
    public static int CLEYE_AUTO_EXPOSURE = 2;    // [0, 1]
    public static int CLEYE_EXPOSURE = 3;    // [0, 511]
    public static int CLEYE_AUTO_WHITEBALANCE = 4;	// [0, 1]
    public static int CLEYE_WHITEBALANCE_RED = 5;	// [0, 255]
    public static int CLEYE_WHITEBALANCE_GREEN = 6;   	// [0, 255]
    public static int CLEYE_WHITEBALANCE_BLUE = 7;    // [0, 255]
    // camera linear transform parameters
    public static int CLEYE_HFLIP = 8;    // [0, 1]
    public static int CLEYE_VFLIP = 9;    // [0, 1]
    public static int CLEYE_HKEYSTONE = 10;   // [-500, 500]
    public static int CLEYE_VKEYSTONE = 11;   // [-500, 500]
    public static int CLEYE_XOFFSET = 12;   // [-500, 500]
    public static int CLEYE_YOFFSET = 13;   // [-500, 500]
    public static int CLEYE_ROTATION = 14;   // [-500, 500]
    public static int CLEYE_ZOOM = 15;   // [-500, 500]
    // camera non-linear transform parameters
    public static int CLEYE_LENSCORRECTION1 = 16;	// [-500, 500]
    public static int CLEYE_LENSCORRECTION2 = 17;	// [-500, 500]
    public static int CLEYE_LENSCORRECTION3 = 18;	// [-500, 500]
    public static int CLEYE_LENSBRIGHTNESS = 19;	// [-500, 500]

    native static int CLEyeGetCameraCount();

    native static String CLEyeGetCameraUUID(int index);

    native static int CLEyeCreateCamera(int cameraIndex, int mode, int resolution, int framerate);

    native static boolean CLEyeDestroyCamera(int cameraIndex);

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
    private int cameraInstance = 0;
    private boolean isOpened = false;

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

    private void dispose() {
        stopCamera();
        destroyCamera();
    }

    private boolean createCamera(int cameraIndex, int mode, int resolution, int framerate) {
        cameraInstance = CLEyeCreateCamera(cameraIndex, mode, resolution, framerate);
        return cameraInstance != 0;
    }

    private boolean destroyCamera() {
        if(cameraInstance==0) return true;
        return CLEyeDestroyCamera(cameraInstance);
    }

    protected boolean cameraStarted=false;
    
    /** Starts the camera
     * 
     * @return true if successful or if already started
     */
    public boolean startCamera() {
        if(cameraStarted) return true;
        cameraStarted= CLEyeCameraStart(cameraInstance);
        return cameraStarted;
    }

    /** Stops the camera
     * 
     * @return true if successful or if not started
     */
    public boolean stopCamera() {
        if(!cameraStarted) return true;
        boolean stopped=CLEyeCameraStop(cameraInstance);
        cameraStarted=false;
        return stopped;
    }

    /** Gets frame data
     * 
     * @param imgData
     * @param waitTimeout in ms
     * @return true if successful
     * @throws HardwareInterfaceException if there is an error
     */
    public void getCameraFrame(int[] imgData, int waitTimeout) throws HardwareInterfaceException{
        if(!CLEyeCameraGetFrame(cameraInstance, imgData, waitTimeout)) throw new HardwareInterfaceException("capturing frame");
    }

    public boolean setCameraParam(int param, int val) {
        return CLEyeSetCameraParameter(cameraInstance, param, val);
    }

    public int getCameraParam(int param) {
        return CLEyeGetCameraParameter(cameraInstance, param);
    }

    @Override
    public String getTypeName() {
        return "CLEye PS Eye camera";
    }

    @Override
    public void close() {
        isOpened = false;
        dispose();
    }

    /** Opens the cameraIndex camera with some default settings
     * 
     * @throws HardwareInterfaceException 
     */
    @Override
    public void open() throws HardwareInterfaceException {
        if (isOpened) {
            return;
        }
        boolean gotCam = createCamera(cameraIndex, CLEYE_MONO_RAW, CLEYE_QVGA, 60);
        if (!gotCam) {
            throw new HardwareInterfaceException("couldn't get camera");
        }
        if (!startCamera()) {
            throw new HardwareInterfaceException("couldn't start camera");
        }
        isOpened = true;
    }

    @Override
    public boolean isOpen() {
        return isOpened;
    }


}
