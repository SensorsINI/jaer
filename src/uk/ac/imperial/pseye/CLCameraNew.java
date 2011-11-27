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

/*
 * jAER modifications performed by mlk
 */
package uk.ac.imperial.pseye;
/* omit line - mlk
 * import processing.core.*;
 */

// add line - mlk
import java.util.Observable;
// add line - mlk
import java.util.logging.Logger;
import cl.eye.CLCamera;

// modified line - mlk
abstract class CLCameraNew extends Observable
{
    // add line - mlk
    protected final static Logger log = Logger.getLogger("PSEye");
    // camera color mode
    public static int CLEYE_GRAYSCALE		= 0;
    public static int CLEYE_COLOR		= 1;

    // camera resolution
    public static int CLEYE_QVGA		= 0;	// Allowed frame rates: 15, 30, 60, 75, 100, 125
    public static int CLEYE_VGA			= 1;	// Allowed frame rates: 15, 30, 40, 50, 60, 75

    // camera sensor parameters
    public static int CLEYE_AUTO_GAIN 		= 0;  	// [0, 1]
    public static int CLEYE_GAIN		= 1;	// [0, 79]
    public static int CLEYE_AUTO_EXPOSURE	= 2;    // [0, 1]
    public static int CLEYE_EXPOSURE		= 3;    // [0, 511]
    public static int CLEYE_AUTO_WHITEBALANCE	= 4;	// [0, 1]
    public static int CLEYE_WHITEBALANCE_RED	= 5;	// [0, 255]
    public static int CLEYE_WHITEBALANCE_GREEN	= 6;   	// [0, 255]
    public static int CLEYE_WHITEBALANCE_BLUE	= 7;    // [0, 255]
    // camera linear transform parameters
    public static int CLEYE_HFLIP		= 8;    // [0, 1]
    public static int CLEYE_VFLIP		= 9;    // [0, 1]
    public static int CLEYE_HKEYSTONE		= 10;   // [-500, 500]
    public static int CLEYE_VKEYSTONE		= 11;   // [-500, 500]
    public static int CLEYE_XOFFSET		= 12;   // [-500, 500]
    public static int CLEYE_YOFFSET		= 13;   // [-500, 500]
    public static int CLEYE_ROTATION		= 14;   // [-500, 500]
    public static int CLEYE_ZOOM		= 15;   // [-500, 500]
    // camera non-linear transform parameters
    public static int CLEYE_LENSCORRECTION1	= 16;	// [-500, 500]
    public static int CLEYE_LENSCORRECTION2	= 17;	// [-500, 500]
    public static int CLEYE_LENSCORRECTION3	= 18;	// [-500, 500]
    public static int CLEYE_LENSBRIGHTNESS	= 19;	// [-500, 500]

    protected static int CLEyeGetCameraCount() {
        return CLCamera.CLEyeGetCameraCount();
    }
    protected static String CLEyeGetCameraUUID(int index) {
        return CLCamera.CLEyeGetCameraUUID(index);
    }
    
    protected static int CLEyeCreateCamera(int cameraIndex, int mode, int resolution, int framerate) {
        return CLCamera.CLEyeCreateCamera(cameraIndex, mode, resolution, framerate);
    }
    
    protected static boolean CLEyeDestroyCamera(int cameraIndex) {
        return CLCamera.CLEyeDestroyCamera(cameraIndex);
    }
    
    protected static boolean CLEyeCameraStart(int cameraInstance) {
        return CLCamera.CLEyeCameraStart(cameraInstance);
    }
    
    protected static boolean CLEyeCameraStop(int cameraInstance) {
        return CLCamera.CLEyeCameraStop(cameraInstance);
    }
    
    protected static boolean CLEyeSetCameraParameter(int cameraInstance, int param, int val) {
        return CLCamera.CLEyeSetCameraParameter(cameraInstance, param, val);
    }
    
    protected static int CLEyeGetCameraParameter(int cameraInstance, int param) {
        return CLCamera.CLEyeGetCameraParameter(cameraInstance, param);
    }
    
    protected static boolean CLEyeCameraGetFrame(int cameraInstance, int[] imgData, int waitTimeout) {
        return CLCamera.CLEyeCameraGetFrame(cameraInstance, imgData, waitTimeout);
    }
    
    /*
    native static int CLEyeGetCameraCount();
    native static String CLEyeGetCameraUUID(int index);
    native static int CLEyeCreateCamera(int cameraIndex, int mode, int resolution, int framerate);
    native static boolean CLEyeDestroyCamera(int cameraIndex);
    native static boolean CLEyeCameraStart(int cameraInstance);
    native static boolean CLEyeCameraStop(int cameraInstance);
    native static boolean CLEyeSetCameraParameter(int cameraInstance, int param, int val);
    native static int CLEyeGetCameraParameter(int cameraInstance, int param);
    native static boolean CLEyeCameraGetFrame(int cameraInstance, int[] imgData, int waitTimeout);
     */

    // modified - mlk
    protected int cameraInstance = 0;
    /* omit line - mlk
     * private PApplet parent;
     */
    // modified - mlk
    protected volatile static boolean libraryLoaded = false;
    /* omit lines - mlk
     * private static String dllpathx32 = "C://Program Files//Code Laboratories//CL-Eye Platform SDK//Bin//CLEyeMulticam.dll";
     * private static String dllpathx64 = "C://Program Files (x86)//Code Laboratories//CL-Eye Platform SDK//Bin//CLEyeMulticam.dll";
     */
    // add line - mlk
    /** Name of the dll for the Windows native part of the JNI for CLCameraNew. */
    private final static String DLLNAME = "CLEyeMulticam";
    
    // modified method - mlk
    // static methods
    static
    {
        if (!libraryLoaded && System.getProperty("os.name").startsWith("Windows")) {
            // prevent multiple access in class initializers like hardwareInterfaceFactory and SubClassFinder
            synchronized (CLCameraNew.class) { 
                try {
                    System.loadLibrary(DLLNAME);
                    libraryLoaded = true;
                } catch (UnsatisfiedLinkError e1) {
                    String lp = null;
                    try {
                        lp = System.getProperty("java.library.path");
                    } catch (Exception e) {
                        log.warning("caught " + e + " when trying to call System.getProperty(\"java.library.path\")");
                    }
                    log.warning("could not load the " + DLLNAME + " DLL; check native library path which is currently " + lp);
                    libraryLoaded = false;
                }
            }
        }
    }
    public static boolean IsLibraryLoaded()
    {
        return libraryLoaded;
    }
    /*
     * omit ability to change dll library
     * public static void loadLibrary(String libraryPath)
     * {
     *     if(libraryLoaded)   return;
     *     try
     *     {
     *         System.load(libraryPath);
     *         System.out.println("CLEyeMulticam.dll loaded");
     *     }
     *     catch(UnsatisfiedLinkError e1)
     *     {
     *         System.out.println("(3) Could not find the CLEyeMulticam.dll (Custom Path)");
     *     }
     * }
     */
    public static int cameraCount()
    {
        return CLEyeGetCameraCount();
    }
    public static String cameraUUID(int index)
    {
        return CLEyeGetCameraUUID(index);
    }
    // public methods    
    /*
     * omit constructor - mlk
     * public CLCameraNew(PApplet parent)
     * {
     *     this.parent = parent;
     *     parent.registerDispose(this);
     * }
     */
    public void dispose()
    {
        stopCamera();
        destroyCamera();
    }
    public boolean createCamera(int cameraIndex, int mode, int resolution, int framerate)
    {
        cameraInstance = CLEyeCreateCamera(cameraIndex, mode, resolution, framerate);
        return cameraInstance != 0;
    }
    public boolean destroyCamera()
    {
        return CLEyeDestroyCamera(cameraInstance);
    }
    public boolean startCamera()
    {
        return CLEyeCameraStart(cameraInstance);
    }
    public boolean stopCamera()
    {
        return CLEyeCameraStop(cameraInstance);
    }
    public boolean getCameraFrame(int[] imgData, int waitTimeout)
    {
        return CLEyeCameraGetFrame(cameraInstance, imgData, waitTimeout);
    }
    public boolean setCameraParam(int param, int val)
    {
        return CLEyeSetCameraParameter(cameraInstance, param, val);
    }
    public int getCameraParam(int param)
    {
        return CLEyeGetCameraParameter(cameraInstance, param);
    }
}
