/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import ch.unizh.ini.jaer.chip.retina.DvsDisplayConfigInterface;

/**
 * Configuration interface for APS-DVS DAVIS vision sensor output rendering.
 *
 * @author Christian
 */
public interface DavisDisplayConfigInterface extends DvsDisplayConfigInterface {

	public static final String PROPERTY_IMU_DISPLAY_ENABLED = "IMU_DISPLAY_ENABLED", PROPERTY_IMU_ENABLED = "IMU_ENABLED",
		PROPERTY_IMU_DLPF_CHANGED = "IMU_DLPF_CHANGED", PROPERTY_IMU_SAMPLE_RATE_CHANGED = "IMU_SAMPLE_RATE_CHANGED",
		PROPERTY_IMU_GYRO_SCALE_CHANGED = "IMU_GYRO_SCALE_CHANGED", PROPERTY_IMU_ACCEL_SCALE_CHANGED = "IMU_ACCEL_SCALE_CHANGED";
	public static final String PROPERTY_CAPTURE_FRAMES_ENABLED = "PROPERTY_CAPTURE_FRAMES_ENABLED",
		PROPERTY_DISPLAY_FRAMES_ENABLED = "PROPERTY_DISPLAY_FRAMES_ENABLED";
	public static final String PROPERTY_CAPTURE_EVENTS_ENABLED = "PROPERTY_CAPTURE_EVENTS_ENABLED",
		PROPERTY_DISPLAY_EVENTS_ENABLED = "PROPERTY_DISPLAY_EVENTS_ENABLED";

	public static final String PROPERTY_GLOBAL_SHUTTER_MODE_ENABLED = "PROPERTY_GLOBAL_SHUTTER_MODE_ENABLED";

	public static final String PROPERTY_EXPOSURE_DELAY_US = "PROPERTY_EXPOSURE_DELAY_US";
	public static final String PROPERTY_FRAME_INTERVAL_US = "PROPERTY_FRAME_INTERVAL_US";

	// following should not be part of display, rather they are control of capture
	// abstract public void setFrameDelayMs(float ms); // TODO these are not rendering controls, they are capture
	// controls....
	//
	// abstract public float getFrameDelayMs();
	//
	// abstract public void setExposureDelayMs(float us);
	//
	// abstract public float getExposureDelayMs();

	/**
	 * Sets threshold number of events to trigger new image frame.
	 *
	 * @param threshold
	 *            number of events for frame capture trigger. 0 to
	 *            disable.
	 */
	abstract public void setAutoShotEventThreshold(int threshold);

	/**
	 * Gets threshold number of events to trigger new image frame.
	 *
	 * @return threshold number of events for frame capture trigger. 0 to
	 *         disable.
	 */
	abstract public int getAutoShotEventThreshold();

	/**
	 * Determines if ADC is enabled to capture images from APS output.
	 *
	 * @return true if enabled
	 */
	abstract public boolean isCaptureFramesEnabled();

	/**
	 * Sets whether ADC is enabled to capture images from APS output.
	 *
	 * @param yes
	 *            true to enable
	 */
	abstract public void setCaptureFramesEnabled(boolean yes);

	/**
	 * Determines if IMU is enabled to capture inertial data.
	 *
	 * @return true if enabled
	 */
	abstract public boolean isImuEnabled();

	/**
	 * Sets whether IMU is enabled to capture inertial data.
	 *
	 * @param yes
	 *            true to enable
	 */
	abstract public void setImuEnabled(boolean yes);

	abstract public boolean isDisplayImu();

	abstract public void setDisplayImu(boolean yes);

	abstract public boolean isSeparateAPSByColor();

	abstract public boolean isAutoWhiteBalance();

	abstract public boolean isColorCorrection();

	abstract public boolean isGlobalShutter();

	abstract public void setSeparateAPSByColor(boolean yes);

	/**
	 * Sets whether AER monitor interface runs to capture DVS output from sensor
	 *
	 * @param selected
	 *            true to enable DVS event capture
	 */
	public void setCaptureEvents(boolean selected);

	/**
	 * Checks whether AER monitor interface runs to capture DVS output from
	 * sensor
	 *
	 * @return true if enabled
	 */
	public boolean isCaptureEventsEnabled();
}
