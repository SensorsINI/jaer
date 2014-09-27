/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.config;

/**
 * Configuration interface for apsDVS vision sensor output rendering.
 * 
 * @author Christian
 */
public interface ApsDvsConfig {
    
       public static final String PROPERTY_IMU_DISPLAY_ENABLED = "IMU_DISPLAY_ENABLED", PROPERTY_IMU_ENABLED="IMU_ENABLED", PROPERTY_IMU_DLPF_CHANGED="IMU_DLPF_CHANGED", PROPERTY_IMU_SAMPLE_RATE_CHANGED="IMU_SAMPLE_RATE_CHANGED", PROPERTY_IMU_GYRO_SCALE_CHANGED="IMU_GYRO_SCALE_CHANGED", PROPERTY_IMU_ACCEL_SCALE_CHANGED="IMU_ACCEL_SCALE_CHANGED";
    
       public static String PROPERTY_CAPTURE_FRAMES_ENABLED="PROPERTY_CAPTURE_FRAMES_ENABLED", PROPERTY_DISPLAY_FRAMES_ENABLED="PROPERTY_DISPLAY_FRAMES_ENABLED";
       
       public static String PROPERTY_CAPTURE_EVENTS_ENABLED="PROPERTY_CAPTURE_EVENTS_ENABLED", PROPERTY_DISPLAY_EVENTS_ENABLED="PROPERTY_DISPLAY_EVENTS_ENABLED";
       public static String PROPERTY_CONTRAST="PROPERTY_CONTRAST";
       public static String PROPERTY_BRIGHTNESS="PROPERTY_BRIGHTNESS";
       public static String PROPERTY_GAMMA="PROPERTY_GAMMA";
       public static String PROPERTY_AUTO_CONTRAST_ENABLED="PROPERTY_AUTO_CONTRAST_ENABLED";
       public static String PROPERTY_GLOBAL_SHUTTER_MODE_ENABLED="PROPERTY_GLOBAL_SHUTTER_MODE_ENABLED";
       public static String PROPERTY_TRANSLATE_ROW_ONLY_EVENTS="PROPERTY_TRANSLATE_ROW_ONLY_EVENTS";

    
    public abstract boolean isDisplayFrames();
    
    public abstract void setDisplayFrames(boolean displayFrames);
    
    public abstract boolean isDisplayEvents();
    
    public abstract void setDisplayEvents(boolean displayEvents);
    
    public abstract boolean isUseAutoContrast();
    
    public abstract void setUseAutoContrast(boolean useAutoContrast);
    
    public abstract float getContrast();
    
    public abstract void setContrast(float contrast);
    
    public abstract float getBrightness();
    
    public abstract void setBrightness(float brightness);
    
    public abstract float getGamma();
    public abstract void setGamma(float gamma);
    
    abstract public void setFrameDelayMs(int ms);
    abstract public int getFrameDelayMs();
    
    abstract public void setExposureDelayMs(int us);
    abstract public int getExposureDelayMs();
    
    /** Sets threshold number of events to trigger new image frame.
     * 
     * @param threshold number of events for frame capture trigger. 0 to disable.
     */
    abstract public void setAutoShotEventThreshold(int threshold);
     /** Gets threshold number of events to trigger new image frame.
     * 
     * @return threshold number of events for frame capture trigger. 0 to disable.
     */
    abstract public int getAutoShotEventThreshold();

    /** Determines if ADC is enabled to capture images from APS output.
     * 
     * @return true if enabled
     */
    abstract public boolean isCaptureFramesEnabled();
    /** Sets whether ADC is enabled to capture images from APS output.
     * 
     * @param yes true to enable
     */
    abstract public void setCaptureFramesEnabled(boolean yes);
    
    /** Determines if IMU is enabled to capture inertial data.
     * 
     * @return true if enabled
     */
    abstract public boolean isImuEnabled();
    /** Sets whether IMU is enabled to capture inertial data.
     * 
     * @param yes true to enable
     */
    abstract public void setImuEnabled(boolean yes);
    
    abstract public boolean isDisplayImu();
    abstract public void setDisplayImu(boolean yes);

    /** Sets whether AER monitor interface runs to capture DVS output from sensor
     * 
     * @param selected true to enable DVS event capture 
     */
    public void setCaptureEvents(boolean selected);

    /** Checks whether AER monitor interface runs to capture DVS output from sensor
     * 
     * @return true if enabled
     */
    public boolean isCaptureEventsEnabled();
    
      /**
     * @return the aeReaderFifoSize
     */
    public int getAeReaderFifoSize();
    /**
     * @param aeReaderFifoSize the aeReaderFifoSize to set
     */
    public void setAeReaderFifoSize(int aeReaderFifoSize);

    /**
     * @return the aeReaderNumBuffers
     */
    public int getAeReaderNumBuffers();

    /**
     * @param aeReaderNumBuffers the aeReaderNumBuffers to set
     */
    public void setAeReaderNumBuffers(int aeReaderNumBuffers);
    
       /**
     * If set, then row-only events are transmitted to raw packets from USB
     * interface
     *
     * @param translateRowOnlyEvents true to translate these parasitic events.
     */
    public void setTranslateRowOnlyEvents(boolean translateRowOnlyEvents);

    public boolean isTranslateRowOnlyEvents();

    
}
