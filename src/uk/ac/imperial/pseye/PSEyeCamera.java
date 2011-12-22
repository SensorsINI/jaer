
package uk.ac.imperial.pseye;

import java.util.EnumMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.nio.ByteBuffer;
import java.util.Observable;
import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Wrapper class for camera driver - currently CL
 * All underlying variables / methods are overridden 
 * 
 * IMPORTANT HARDWARE NOTES:
 * 
 * All functions that use camera instance will cause JVM crash if instance invalid.
 * 
 * Calling PSEyeCameraStart/Stop on a camera that is already started/stopped
 * returns true.
 * 
 * Calling PSEyeCameraGetFrame with an undersized data array causes JVM crash
 * (oversized is fine), returns false if camera stopped or no frames available
 * (and wait time out exceeded).
 * 
 * When setting auto gain or exposure to false the gain/exposure
 * (from the cameras get function) is not accurate (i.e. they remain at
 * the values set automatically but these values are not returned when queried)!
 *
 * Strange flicker when setting new gain if auto gain enabled??
 * 
 * Parameters (gain, exposure, auto-gain, auto-exposure): 
 * 1. Setting/Getting parameters on invalid cameraInstance causes JVM crash (see above).
 * 2. Setting/Getting parameters on a stopped camera works fine.
 * 3. Parameter values are NOT preserved if camera closed().
 * 4. Parameter values preserved if camera stopped().
 * 
 * Camera defaults to MONO, QVGA, 15FPS, 
 * gain=0, auto_gain=true, exposure=0, auto_exposure=true
 * 
 * @author mlk
 */
public class PSEyeCamera extends Observable implements HardwareInterface {
    protected final static Logger log = Logger.getLogger("PSEye");
    public final static PSEyeConstants constants = PSEyeConstants.INSTANCE;
    private int index = -1; // index of camera to open
    private boolean isOpened = false;
  
   /* Observable events; This event enum is fired when the parameter is changed. */
    public static enum EVENT { 
        MODE,
        RESOLUTION,
        FRAMERATE,
        GAIN,
        EXPOSURE,
        AUTO_GAIN,
        AUTO_EXPOSURE;
    }

    /* Enums representing possible colour and resolution modes */
    /* taken from driver documentation */
    public static enum Mode {MONO, COLOUR}
    public static enum Resolution {VGA, QVGA}
    
    /* Camera hardware parameters - updates require restart */
    private Mode mode = Mode.MONO;
    private Resolution resolution = Resolution.QVGA;
    private int frameRate = 15;
    
    /* store parameters so can be re-set after camera closed() */
    private int gain = PSEyeConstants.MIN_GAIN;
    private int exposure = PSEyeConstants.MIN_EXPOSURE;
    private boolean autoGainEnabled = true;
    private boolean autoExposureEnabled = false;
    
    /* supported modes */
    public static final ArrayList<Mode> supportedModes =
            new ArrayList<PSEyeCamera.Mode>(Arrays.asList(Mode.values()));
    
    
    /* Map of local colour modes to CL colour modes */
    private static final EnumMap<Mode, Integer> CLModeMap = 
            new EnumMap<Mode, Integer>(Mode.class) {{
                put(Mode.MONO, PSEyeConstants.MONO_RAW);
                put(Mode.COLOUR, PSEyeConstants.COLOR_RAW);
    }};
    
    /* supported resolutions */
    public static final ArrayList<Resolution> supportedResolutions = 
            new ArrayList<Resolution>(Arrays.asList(Resolution.values()));
    
    /* Map of local resolutions to CL resolutions */
    private static final EnumMap<Resolution, Integer> CLResolutionMap = 
            new EnumMap<Resolution, Integer>(Resolution.class) {{
                put(Resolution.QVGA, PSEyeConstants.QVGA);
                put(Resolution.VGA, PSEyeConstants.VGA);
    }};
    
    /* Map of frame size */
    public static final EnumMap<Resolution, Integer> FrameSizeMap = 
            new EnumMap<Resolution, Integer>(Resolution.class) {{
                put(Resolution.QVGA, 320 * 240);
                put(Resolution.VGA, 640 * 480);
    }};    
    
    /* Map of supported frame rates for each resolution */
    public static final EnumMap<Resolution, List<Integer>> supportedFrameRates = 
            new EnumMap<Resolution, List<Integer>>(Resolution.class) {{
                put(Resolution.VGA, Arrays.asList(1,1));
                put(Resolution.QVGA, Arrays.asList(1, 2));
    }};        

    // Wrapper class for frame producer/consumer thread
    protected PSEyeFrameManager frameManager = PSEyeFrameManager.INSTANCE;
    protected boolean cameraStarted = false;

    public native static int PSEyeGetCameraCount();
    public native static String PSEyeGetCameraUUID(int index);
    public native static boolean PSEyeCreateCamera(int index, int mode, int resolution, float framerate);
    public native static boolean PSEyeDestroyCamera(int index);
    public native static boolean PSEyeCameraStart(int index);
    public native static boolean PSEyeCameraStop(int index);
    public native static boolean PSEyeCameraLED(int index, boolean on);
    public native static boolean PSEyeSetCameraParameter(int index, int param, int val);
    public native static int PSEyeGetCameraParameter(int index, int param);
    public native static boolean PSEyeCameraGetFrameDimensions(int index, int[] dimensions);
    public native static boolean PSEyeCameraGetFrame(int index, ByteBuffer imgData, int timeout);
    
    PSEyeCamera() {}
    
    /* Constructs instance to open the cameraIndex camera */
    PSEyeCamera(int index) {
        this();
        // check index valid and make singleton? mlk
        this.index = index;
    }
    
    @Override
    public String toString() {
        return "PSEyeCamera(" + index + ")";
    }
    
    /* 
     * Returns number of cameras
     * Does not require a valid camera instance
     */
    public static int cameraCount() {
        return PSEyeGetCameraCount();
    }

    /* 
     * Returns UUID of camera or all 0's if invalid index given 
     * Does not require a valid camera instance
     */
    public static String cameraUUID(int index)
    {
        return PSEyeGetCameraUUID(index);
    }

    synchronized public boolean createCamera(int index, int mode, int resolution, int framerate)
    {
        this.index = index;
        if (PSEyeCreateCamera(index, mode, resolution, framerate)) {
            // set all parameters to initial values
            setCameraParam(PSEyeConstants.GAIN, gain);
            setCameraParam(PSEyeConstants.EXPOSURE, exposure);
            
            setCameraParam(PSEyeConstants.AUTO_GAIN, autoGainEnabled ? 1 : 0);
            setCameraParam(PSEyeConstants.AUTO_EXPOSURE, autoExposureEnabled ? 1 : 0);
            
            // add this camera to frame manager
            frameManager.setCamera(this);
            return true;
        }
        else
        {
            this.index = -1;
            return false;
        }
    }
    
    synchronized public boolean createCamera(int index, Mode mode, Resolution resolution, int framerate) {
        return createCamera(index, CLModeMap.get(mode), CLResolutionMap.get(resolution), framerate);
    }

    synchronized public boolean destroyCamera() {
        if (index < 0) return true;
        return PSEyeDestroyCamera(index);
    }
    
    /* Starts the camera. */
    synchronized public boolean startCamera() {
        if (index < 0) return false;
        if (cameraStarted) {
            return true;
        }
        cameraStarted = PSEyeCameraStart(index);
        
        // start producer thread
        if (cameraStarted)
            frameManager.start();

        return cameraStarted;
    }

    /* Stops the camera */
    synchronized public boolean stopCamera() {
        if (index < 0) return false;
        if (!cameraStarted) {
            return true;
        }
        boolean stopped = PSEyeCameraStop(index);
        cameraStarted = false;
        
        // stop producer thread
        frameManager.stop();
        return stopped;
    }
    
    /* Gets non-timestamped frame data from capture thread */
    public boolean getCameraFrame(int[] imgData, int waitTimeout)
    {
        // return if camera not started
        if (!cameraStarted) return false;
        
        PSEyeFrame frame = null;
        // keep checking for frame until timeout reached
        for (int i = 0; i < waitTimeout / 50; i++) {
            frame = frameManager.popFrame();
            if (frame == null) 
                return false;
            
            // copy frame data to passed array
            frame.copyData(imgData, 0);
            
            // put frame back into producer queue
            frameManager.pushFrame(frame);
            return true;
        }
        // if no luck sleep thread to allow producer to catch up
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }
    
    public boolean getCameraRawFrame(int[] imgData, int waitTimeout) {
        // check camera created and started and that passed array big enough to store data
        if (index < 0 || !cameraStarted || 
                FrameSizeMap.get(resolution) > imgData.length) return false;
        //return PSEyeCameraGetFrame(index, imgData, waitTimeout);
        return true;
    }

    public boolean setCameraParam(int param, int val)
    {
        if (index < 0) return false;
        return PSEyeSetCameraParameter(index, param, val);
    }
    
    public int getCameraParam(int param)
    {
        if (index < 0) return -1;
        return PSEyeGetCameraParameter(index, param);
    }
    
    @Override
    public String getTypeName() {
        return toString();
    }

    /* Closes the camera. */
    @Override
    synchronized public void close() {
        if(!isOpened) return;
        isOpened = false;
        boolean stopped = stopCamera();
        if(!stopped){
            log.warning("stopCamera returned an error");
        }
        boolean destroyed = destroyCamera();
        if(!destroyed){
            log.warning("destroyCamera returned an error");
        }
        index = -1;
    }

    /* Opens the cameraIndex camera with some default settings and starts the camera. Set the frameRateHz before calling open(). */
    @Override
    synchronized public void open() throws HardwareInterfaceException {
        if (isOpened) {
            return;
        }
        boolean gotCam = createCamera(index, mode, resolution, frameRate);
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
    
    private void reset() throws HardwareInterfaceException {
        close();
        open();
    }

    synchronized public Mode setMode(Mode cameraMode) throws HardwareInterfaceException {
        if(getMode() != cameraMode) {
            mode = cameraMode;
            if(isOpen()) reset();
            setChanged();
            notifyObservers(EVENT.MODE);
        }
        
        return mode;
    }
    
    public Mode getMode() {
        // cannot query camera as only set during creation
        return mode;
    }
    
    synchronized public Resolution setResolution(Resolution cameraResolution) throws HardwareInterfaceException {
        if(getResolution() != cameraResolution) {
            resolution = cameraResolution;
            // find closest frame rate supported by this resolution
            int fr = getClosestFrameRate(frameRate);
            if (fr != frameRate) {
                frameRate = fr;
                notifyObservers(EVENT.FRAMERATE);
            }
            if(isOpen()) reset();
            setChanged();            
            notifyObservers(EVENT.RESOLUTION);
        }
        
        return resolution;
    }

    public Resolution getResolution() {
        // cannot query camera as only set during creation
        return resolution;
    }
    
    /* Get the nearest supported frame rate above that passed */
    private int getClosestFrameRate(int frameRate) {
        Integer[] frameRates = supportedFrameRates.get(resolution).toArray(new Integer[0]);
        Arrays.sort(frameRates);
        int index = Arrays.binarySearch(frameRates, frameRate);
        if (index >= 0) {
            return frameRates[index];
        }
        else {
            index = -(index + 1);
            return index < frameRates.length ? frameRates[index] : frameRates[index - 1];
        } 
    }
    
    synchronized public int setFrameRate(int cameraFrameRate) throws HardwareInterfaceException {
        // get a supported frame rate
        cameraFrameRate = getClosestFrameRate(cameraFrameRate);
        if(getFrameRate() != cameraFrameRate) {
            frameRate = cameraFrameRate;
            if(isOpen()) reset();
            setChanged();
            notifyObservers(EVENT.FRAMERATE);
        }
        
        return cameraFrameRate;
    }
    
    public int getFrameRate() {
        // cannot query camera as only set during creation
        return frameRate;
    }
    
    /* Thrown for invalid parameters */
    public class InvalidParameterException extends Exception {

        public InvalidParameterException(String message) {
            super(message);
        }
    }

    /* Sets the gain value. */
    synchronized public int setGain(int gain) throws HardwareInterfaceException, InvalidParameterException {
        if(gain != getGain()) {
            if (gain < PSEyeConstants.MIN_GAIN) {
                throw new InvalidParameterException("tried to set gain < " + PSEyeConstants.MIN_GAIN + " (" + gain + ")");
            }
            if (gain > PSEyeConstants.MAX_GAIN) {
                throw new InvalidParameterException("tried to set gain < " + PSEyeConstants.MAX_GAIN + " (" + gain + ")");
            }
            int lastGain = this.gain;
            this.gain = gain;
            try {
                setGain();
            } catch (HardwareInterfaceException ex) {
                // unable to set exposure in hardware so fall back to last value
                this.gain = lastGain;
                throw ex;
            }
        }
        return this.gain;
    }

    /* separated out as need to force a set when auto gain turned off */
    synchronized private void setGain() throws HardwareInterfaceException {
        // if camera exists and autogain not turned on set it's gain
        if (index >= 0 && !isAutoGain()) {
            if (!setCameraParam(PSEyeConstants.GAIN, gain)) {
                throw new HardwareInterfaceException("setting gain to " + gain);
            }
            // read gain of camera
            this.gain = getGain();
        }
        // parameter changed so set flag and notify
        setChanged();
        notifyObservers(EVENT.GAIN);
    }
    
    /* Asks the driver for the gain value. */
    public int getGain() {
        // if camera exists query
        if (index >= 0)
            gain = getCameraParam(PSEyeConstants.GAIN);
        return gain;
    }

    /* Sets the exposure value. */
    synchronized public int setExposure(int exp) throws HardwareInterfaceException, InvalidParameterException {
        if(exp != getExposure()) {
            if (exp < PSEyeConstants.MIN_EXPOSURE) {
                throw new InvalidParameterException("tried to set exposure < " + PSEyeConstants.MIN_EXPOSURE + " (" + exp + ")");
            }
            if (exp > PSEyeConstants.MAX_EXPOSURE) {
                throw new InvalidParameterException("tried to set exposure < " + PSEyeConstants.MAX_EXPOSURE + " (" + exp + ")");
            }
            int lastExposure = exposure;
            exposure = exp;
            try {
                setExposure();
            } catch (HardwareInterfaceException ex) {
                // unable to set exposure in hardware so fall back to last value
                exposure = lastExposure;
                throw ex;
            }
        }
        return exposure;
    }
    
    /* separated out as need to force a set when auto exposure turned off */
    synchronized private void setExposure() throws HardwareInterfaceException {
        // if camera exists and autoexposure not turned on set it's exposure
        if (index >= 0 && !isAutoExposure()) {        
            if (!setCameraParam(PSEyeConstants.EXPOSURE, exposure)) {
                throw new HardwareInterfaceException("setting exposure to " + exposure);
            }
            // read exposure of camera
            exposure = getExposure();
        }
        // parameter changed so set flag and notify
        setChanged();
        notifyObservers(EVENT.EXPOSURE);
    }

   /* Asks the driver for the exposure value. */
    public int getExposure() {
        // if camera exists query
        if (index >= 0)
            exposure = getCameraParam(PSEyeConstants.EXPOSURE);
        return exposure;
    }

    /* Enables auto gain */
    synchronized public boolean setAutoGain(boolean yes) throws HardwareInterfaceException, InvalidParameterException {
        if(yes != isAutoGain()) {
            // if camera exists set it's auto gain
            if (index >= 0) {
                if (!setCameraParam(PSEyeConstants.AUTO_GAIN, yes ? 1 : 0)) {
                    throw new HardwareInterfaceException("setting auto gain=" + yes);
                }
                // read auto gain of camera
                autoGainEnabled = isAutoGain();
                // re-set gain as not respected when autogain turned off
                if (!autoGainEnabled) 
                        setGain();
            }
            else
                autoGainEnabled = yes;

            setChanged();
            notifyObservers(EVENT.AUTO_GAIN);
        }
        return autoGainEnabled;
    }

    public boolean isAutoGain() {
        // if camera exists query
        if (index >= 0)
            autoGainEnabled = getCameraParam(PSEyeConstants.AUTO_GAIN) != 0;
        return autoGainEnabled;
    }

    /* Enables auto exposure */
    synchronized public boolean setAutoExposure(boolean yes) throws HardwareInterfaceException, InvalidParameterException {
        if(yes != isAutoExposure()) {
            // if camera exists set it's auto exposure
            if (index >= 0) {
                if (!setCameraParam(PSEyeConstants.AUTO_EXPOSURE, yes ? 1 : 0)) {
                    throw new HardwareInterfaceException("setting auto exposure=" + yes);
                }
                // read auto exposure of camera
                autoExposureEnabled = isAutoExposure();
                // re-set exposure as not respected when autoexposure turned off
                if (!autoExposureEnabled) 
                        setExposure();
            }
            else
                autoExposureEnabled = yes;
            setChanged();            
            notifyObservers(EVENT.AUTO_EXPOSURE);
        }
        return autoExposureEnabled;
    }

    public boolean isAutoExposure() {
        // if camera exists query
        if (index >= 0)
            autoExposureEnabled = getCameraParam(PSEyeConstants.AUTO_EXPOSURE) != 0;
        return autoExposureEnabled; 
    }

    public static void main (String arg[]) {
        log.info(""+cameraCount());
    }
    
}
