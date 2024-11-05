/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.inilabs.jaer.gimbal;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.geom.Point2D;


import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;

import com.inilabs.jaer.gimbal.LaserOnOffControl;
import com.inilabs.jaer.gimbal.GimbalBase;
import com.inilabs.jaer.gimbal.GimbalCalibrator;
import com.inilabs.jaer.gimbal.GimbalInterface;


/**
 * This filter enables calibrated control of a pan tilt laser pointer. CalibratedPanTilt has a method to set the pan tilt
 * to aim at an x,y point in the retinal view. CalibratedPanTilt only processes incoming events during calibration, when
 * it uses its own RectangularClusterTracker to track the jittering laser point spot for aiming calibration.
 * 
 * @author Tobi Delbruck
 */
@Description("Controls a pantilt unit to aim at a calibrated visual location")
public class CalibratedGimbal extends EventFilter2DMouseAdaptor implements FrameAnnotater, GimbalInterface, LaserOnOffControl {

	RectangularClusterTracker tracker;
	private GimbalBase panTiltHardware = null;
	private GimbalCalibrator calibrator=new GimbalCalibrator(this);
                    
                    private String who = "";

	/** Constructs instance of the new 'filter' CalibratedPanTilt. The only time events are actually used
	 * is during calibration. The PanTilt hardware interface is also constructed.
	 * @param chip
	 */
	public CalibratedGimbal(AEChip chip) {
		super(chip);
                                        who = "CalibratedGimbal";
		tracker = new RectangularClusterTracker(chip);
		setEnclosedFilter(tracker);
		panTiltHardware = GimbalBase.getInstance();
	}

	/** Sets the pan and tilt to aim at a particular calibrated x,y visual direction
     @param x the x pixel
     @param y the y pixel
	 */
	public void setPanTiltVisualAim(float x, float y) throws Exception{
		float[] xy={x,y,1};
		float[] pt=calibrator.getTransformedPanTiltFromXY(xy);
		getPanTiltHardware().setPanTiltValues(pt[0], pt[1]);
	}



	@Override
	public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
		if (!isFilterEnabled()) {
			return in;
		}
		if((calibrator==null) || !calibrator.isCalibrating()) {
			return in;
		}
		tracker.filterPacket(in);
		if (panTiltHardware.isLockOwned()) {
			return in;
		}
		if (tracker.getNumClusters() > 0) {
			RectangularClusterTracker.Cluster c = tracker.getClusters().get(0);
			if (c.isVisible()) {
				Point2D.Float p = c.getLocation();
				float[] xy = {p.x, p.y, 1};
				float[] pt = calibrator.getTransformedPanTiltFromXY(xy);

				float pan = pt[0];
				float tilt = pt[1];
				try {
					panTiltHardware.setPanTiltValues(pan, tilt);
				} catch (Exception e) {
					log.warning(e.toString());
				}
				panTiltHardware.setLaserOn(true);
			}else{
				panTiltHardware.setLaserOn(false);
			}
		}else{
			panTiltHardware.setLaserOn(false);
		}
		return in;
	}



	@Override
	public void resetFilter() {
		tracker.resetFilter();
	}

	@Override
	public void initFilter() {
		resetFilter();
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if (!isFilterEnabled()) {
			return;
		}
		tracker.annotate(drawable);

		GL2 gl = drawable.getGL().getGL2(); // when we getString this we are already set up with updateShape 1=1 pixel, at LL corner

			if (gl == null) {
				log.warning("null GL");
				return;
			}
		final float BOX_LINE_WIDTH = 3f; // in pixels

		gl.glColor3f(1, 0, 0);
		gl.glLineWidth(BOX_LINE_WIDTH);

		final int sx = 2,  sy = 2;
		for (GimbalCalibrationPoint p : calibrator.sampleVector) {
			gl.glPushMatrix();
			final int x = (int) p.ret.x,  y = (int) p.ret.y;
			gl.glBegin(GL.GL_LINE_LOOP);
			{
				gl.glVertex2i(x - sx, y - sy);
				gl.glVertex2i(x + sx, y - sy);
				gl.glVertex2i(x + sx, y + sy);
				gl.glVertex2i(x - sx, y + sy);
			}
			gl.glEnd();
			gl.glPopMatrix();
		}
	}

	private void drawBox(GL2 gl, int x, int y, int sx, int sy) {
	}

	/** Invokes the calibration GUI. Calibration values are stored persistently as preferences.
	 * Built automatically into filter parameter panel as an action.
	 */
	public void doCalibrate() {
		if (calibrator == null) {
			calibrator = new GimbalCalibrator(this);
		}
		calibrator.calibrate();
	}

	public GimbalBase getPanTiltHardware() {
		return panTiltHardware;
	}

	public void setPanTiltHardware(GimbalBase panTilt) {
		panTiltHardware=panTilt;
	}

                    public void setLaserOn(boolean yes) {
                            panTiltHardware.setLaserOn(yes);
                            }


	@Override
	public float getJitterAmplitude() {
		return panTiltHardware.getJitterAmplitude();
	}

	/** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
	 * 
	 * @param jitterAmplitude the amplitude
	 */
	@Override
	public void setJitterAmplitude(float jitterAmplitude) {
		panTiltHardware.setJitterAmplitude(jitterAmplitude);
	}

	@Override
	public float getJitterFreqHz() {
		return panTiltHardware.getJitterFreqHz();
	}

	/** The frequency of the jitter
	 * 
	 * @param jitterFreqHz in Hz
	 */
	@Override
	public void setJitterFreqHz(float jitterFreqHz) {
		panTiltHardware.setJitterFreqHz(jitterFreqHz);
	}

	@Override
	public void acquire(String who) {
		getPanTiltHardware().acquire(who);
	}

	@Override
	public float[] getPanTiltValues() {
		return getPanTiltHardware().getPanTiltValues();
	}

	@Override
	public boolean isLockOwned() {
		return getPanTiltHardware().isLockOwned();
	}

	@Override
	public void release(String who) {
		getPanTiltHardware().release(who);
	}

	@Override
	public void startJitter() {
		getPanTiltHardware().startJitter();
	}

	@Override
	public void stopJitter() {
		getPanTiltHardware().stopJitter();
	}

	/** Sets the pan and tilt servo values
     @param pan 0 to 1 value
     @param tilt 0 to 1 value
	 */

	public void setPanTiltValues(float pan, float tilt) {
		getPanTiltHardware().setPanTiltValues(pan, tilt);
	}

	

	public GimbalCalibrator getCalibrator() {
		return calibrator;
	}

                   public void setLaserEnabled(boolean yes) {
		getPanTiltHardware().setLaserEnabled(yes);
	}
                   
}
