/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.inilabs.jaer.projects.inix.tracker;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

import ch.unizh.ini.jaer.hardware.pantilt.*; 
import com.jogamp.opengl.GL;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.OpticalGyro;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Demonstrates tracking object(s) and targeting them with the pan tilt unit. A laser pointer on the pan tilt
 * can show where it is aimed. Developed for Sardinia Capo Caccia Cognitive Neuromorphic Engineering Workshop, April 2008.
 * Includes a 4 point calibration based on an interactive GUI.
 * 
 * @author tobi, Ken Knoblauch, rjd
 */

@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@Description("Selects, and tracks, a moving object with the DJI RS 4 Pro Gimbal")
public class GimbalController extends OpticalGyro implements FrameAnnotater {
	FlightTracker tracker;
	CalibratedPanTilt panTilt=null;
        Point2D.Float cluster0_xy = null;

	public GimbalController(AEChip chip) {
		super(chip);
                cluster0_xy = new Point2D.Float(100, 100);
		FilterChain filterChain=new FilterChain(chip);
		tracker=new FlightTracker(chip);
//                tracker.getSupport().addPropertyChangeListener(this);
		panTilt=new CalibratedPanTilt(chip);
//                panTilt.getSupport().addPropertyChangeListener(this);
                filterChain.add(tracker);
                filterChain.add(panTilt);
		setEnclosedFilterChain(filterChain);       
	}


	@Override
	public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
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
                                this.cluster0_xy = p;
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
                glAnnotate(drawable.getGL().getGL2());
	}

        
        /** Shows the transform on top of the rendered events.
	 *
	 * @param gl the OpenGL context.
	 */
	private void glAnnotate(GL2 gl) {
		// this whole annotation is translated by the enclosing filter SceneStabilizer so that
		// clusters appear on top of tracked features.
		int sx2 = chip.getSizeX() / 8, sy2 = chip.getSizeY() / 8;

		// draw translation
		gl.glPushMatrix();
                if(cluster0_xy != null) {
                    gl.glTranslatef(cluster0_xy.x + sx2, cluster0_xy.y + sy2, 0);
                }
//		gl.glTranslatef(-translation.x + sx2, -translation.y + sy2, 0);
//		gl.glRotatef((float) ((-rotationAngle * 180) / Math.PI), 0, 0, 1);
		//        gl.glTranslatef(sx2, sy2, 0);
		// draw translation
		gl.glLineWidth(2f);
		gl.glColor3f(0, 1, 1);
		gl.glBegin(GL.GL_LINES);
		gl.glVertex2f(-sx2, 0);
		gl.glVertex2f(sx2, 0);
		gl.glVertex2f(0, -sy2);
		gl.glVertex2f(0, sy2);
		gl.glEnd();
		gl.glPopMatrix();

		if (isUseVelocity()) {
			gl.glBegin(GL.GL_LINES);
			float x = (getVelocityPPt().x / 10) + sx2, y = (getVelocityPPt().y / 10) + sy2;
			gl.glVertex2f(sx2, sy2);
			gl.glVertex2f(x, y);
			gl.glEnd();
		}
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
