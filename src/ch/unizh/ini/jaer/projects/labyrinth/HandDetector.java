/*
 *Tobi Delbruck, CapoCaccia 2011
 */
package ch.unizh.ini.jaer.projects.labyrinth;


import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Font;

//import com.jogamp.opengl.GL2;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.filter.EventRateEstimator;
import net.sf.jaer.graphics.FrameAnnotater;

import com.jogamp.opengl.util.awt.TextRenderer;

/**
 * Detects hand by setting a threshold on the average event activity. Very high activity says probably a hand is picking up the ball or putting it down.
 * @author tobi
 */
public class HandDetector extends EventRateEstimator implements FrameAnnotater {

	protected float handEventRateThresholdKEPS = getFloat("handEventRateThresholdKEPS", 100);
	TextRenderer renderer;
	boolean handDetected = false;
	long lastTimeHandDetected = 0;
	private int timeoutMs = getInt("timeoutMs", 2000);

	public HandDetector(AEChip chip) {
		super(chip);
		setPropertyTooltip("handEventRateThresholdKEPS", "threshold avg event rate in kilo event per second to count as hand detection");
		setPropertyTooltip("timeoutMs", "hand is held detected for this time in ms after threshold is crossed");
	}

	@Override
	public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
		super.filterPacket(in);
		boolean detectedNow = ((getFilteredEventRate() * 1e-3f) > handEventRateThresholdKEPS);
		if (detectedNow) {
			handDetected = true;
			lastTimeHandDetected = System.currentTimeMillis();
		}else if (handDetected && !detectedNow && ((System.currentTimeMillis() - lastTimeHandDetected) > getTimeoutMs())) {
			handDetected = false;
		}
		return in;
	}

	/**
	 * Get the value of handEventRateThresholdKEPS
	 *
	 * @return the value of handEventRateThresholdKEPS
	 */
	public float getHandEventRateThresholdKEPS() {
		return handEventRateThresholdKEPS;
	}

	public boolean isHandDetected() {
		return handDetected;

	}

	/**
	 * Set the value of handEventRateThresholdKEPS
	 *
	 * @param handEventRateThresholdKEPS new value of handEventRateThresholdKEPS
	 */
	public void setHandEventRateThresholdKEPS(float thresholdKEPS) {
		handEventRateThresholdKEPS = thresholdKEPS;
		putFloat("handEventRateThresholdKEPS", thresholdKEPS);
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if (renderer == null) {
			renderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 24));
		}
		GL2 gl = drawable.getGL().getGL2();
		if (isHandDetected() && (renderer != null)) {
			renderer.beginRendering(chip.getSizeX(), chip.getSizeY());
			gl.glColor4f(1, 1, 0, .7f);
			renderer.draw("Hand!", 5, chip.getSizeY() / 2);
			renderer.endRendering();
		} else {
			gl.glColor4f(0, 1, 0, .5f);
			gl.glRectf(0, -3, ((chip.getSizeX() * getFilteredEventRate()) / handEventRateThresholdKEPS) * 1e-3f, -1);
		}
	}

	/**
	 * @return the timeoutMs
	 */
	public int getTimeoutMs() {
		return timeoutMs;
	}

	/**
	 * @param timeoutMs the timeoutMs to set
	 */
	public void setTimeoutMs(int timeoutMs) {
		this.timeoutMs = timeoutMs;
	}

}
