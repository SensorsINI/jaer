/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.hardware.pantilt;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;


import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
/**
 * Demonstrates tracking object(s) and targeting them with the pan tilt unit. A laser pointer on the pan tilt
 * can show where it is aimed. Developed for Sardinia Capo Cacia Cognitive Neuromorphic Engineering Workshop, April 2008.
 * Includes a 4 point calibration based on an interactive GUI.
 * 
 * @author tobi, Ken Knoblauch
 */
@Description("Trackes a single moving object with the pan tilt unit")
public class PanTiltTracker extends EventFilter2D implements FrameAnnotater {
	RectangularClusterTracker tracker;
	CalibratedPanTilt panTilt=null;

	public PanTiltTracker(AEChip chip) {
		super(chip);
		FilterChain filterChain=new FilterChain(chip);
		setEnclosedFilterChain(filterChain);
		tracker=new RectangularClusterTracker(chip);
		panTilt=new CalibratedPanTilt(chip);
		filterChain.add(panTilt);
		filterChain.add(tracker);
		setEnclosedFilterChain(filterChain);
	}


	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		if(!isFilterEnabled()) {
			return in;
		}
		getEnclosedFilterChain().filterPacket(in);
		if(panTilt.getPanTiltHardware().isLockOwned()) {
			return in;
		}
		if(tracker.getNumClusters()>0) {
			RectangularClusterTracker.Cluster c=tracker.getClusters().get(0);
			if(c.isVisible()) {
				Point2D.Float p=c.getLocation();
				float[] xy={p.x, p.y, 1};
				try {
					panTilt.setPanTiltVisualAim(p.x, p.y);
				} catch(HardwareInterfaceException ex) {
					log.warning(ex.toString());
				}
				panTilt.getPanTiltHardware().setLaserOn(true);
			} else {
				panTilt.getPanTiltHardware().setLaserOn(false);
			}
		} else {
			panTilt.getPanTiltHardware().setLaserOn(false);
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

	public void annotate(float[][][] frame) {
	}

	public void annotate(Graphics2D g) {
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if(!isFilterEnabled()) {
			return;
		}
		tracker.annotate(drawable);


	}

	private void drawBox(GL2 gl, int x, int y, int sx, int sy) {
	}


	public float getJitterAmplitude() {
		return panTilt.getJitterAmplitude();
	}

	/** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
	 * 
	 * @param jitterAmplitude the amplitude
	 */
	 public void setJitterAmplitude(float jitterAmplitude) {
		panTilt.setJitterAmplitude(jitterAmplitude);
	}

	public float getJitterFreqHz() {
		return panTilt.getJitterFreqHz();
	}

	/** The frequency of the jitter
	 * 
	 * @param jitterFreqHz in Hz
	 */
	public void setJitterFreqHz(float jitterFreqHz) {
		panTilt.setJitterFreqHz(jitterFreqHz);
	}
}
