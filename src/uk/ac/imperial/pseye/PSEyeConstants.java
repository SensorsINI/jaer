
package uk.ac.imperial.pseye;

import java.util.logging.Logger;
import net.sf.jaer.WOW64Checker;
/*
 * Class to wrap all dll constants and loading.
 * Created as singleton as this ensures dll loaded only once
 * @author mlk11
 */
public class PSEyeConstants {
    protected final static Logger log = Logger.getLogger("PSEye");
    public static final PSEyeConstants INSTANCE = new PSEyeConstants();
    
    // camera modes
    public static final int MONO_PROCESSED = 0;
    public static final int COLOR_PROCESSED = 1;
    public static final int MONO_RAW = 2;
    public static final int COLOR_RAW = 3;
    public static final int BAYER_RAW = 4;
    
    // camera resolution
    public static final int QVGA = 0;
    public static final int VGA = 1;
    
    // camera sensor parameters
    public static final int AUTO_GAIN = 0;                // [false, true]
    public static final int GAIN = 1;                     // [0, 79]
    public static final int AUTO_EXPOSURE = 2;            // [false, true]
    public static final int EXPOSURE = 3;                 // [0, 511]
    public static final int AUTO_WHITEBALANCE = 4;        // [false, true]
    public static final int WHITEBALANCE_RED = 5;         // [0, 255]
    public static final int WHITEBALANCE_GREEN = 6;	// [0, 255]
    public static final int WHITEBALANCE_BLUE = 7;	// [0, 255]

    // camera linear transform parameters (valid for CLEYE_MONO_PROCESSED, CLEYE_COLOR_PROCESSED modes)
    public static final int HFLIP = 8;			// [false, true]
    public static final int VFLIP = 9;			// [false, true]
    public static final int HKEYSTONE = 10;		// [-500, 500]
    public static final int VKEYSTONE = 11;		// [-500, 500]
    public static final int XOFFSET = 12;			// [-500, 500]
    public static final int YOFFSET = 13;			// [-500, 500]
    public static final int ROTATION = 14;		// [-500, 500]
    public static final int ZOOM = 15;			// [-500, 500]

    // camera non-linear transform parameters (valid for CLEYE_MONO_PROCESSED, CLEYE_COLOR_PROCESSED modes)
    public static final int LENSCORRECTION1 = 16;		// [-500, 500]
    public static final int LENSCORRECTION2 = 17;		// [-500, 500]
    public static final int LENSCORRECTION3 = 18;		// [-500, 500]
    public static final int LENSBRIGHTNESS = 19;    
    
    // gain ranges
    public static final int MIN_GAIN = 0;
    public static final int MAX_GAIN = 79;
    // exposure ranges
    public static final int MIN_EXPOSURE = 0;
    public static final int MAX_EXPOSURE = 511;    
    public static int test = 1;
    /** Name of the dll for the Windows native part of the JNI for CLCameraNew. */
    public final static String DLLNAME = "libPSEyeCamera";  
    
    static {
        if (System.getProperty("os.name").startsWith("Windows")) { // && WOW64Checker.isWOW64 == false) {
            try {
                synchronized (PSEyeConstants.class) {
                    System.loadLibrary(DLLNAME);
                }
            } catch (UnsatisfiedLinkError e1) {
                String lp = null;
                try {
                    lp = System.getProperty("java.library.path");
                } catch (Exception e) {
                    log.warning("caught " + e + " when trying to call System.getProperty(\"java.library.path\")");
                }
                log.warning("could not load the " + DLLNAME + " DLL; check native library path which is currently " + lp);
                throw e1;
            }
        }
    } 
   
    
    private PSEyeConstants() {}
}


