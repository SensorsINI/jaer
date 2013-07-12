/*
 * Created at Telluride Neuromophic Engineering Workshop 2009, July 2009.
 * http://neuromorphs.net
 */
package org.ine.telluride.jaer.tell2009.leah;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.chip.cochlea.CochleaChip;
import ch.unizh.ini.jaer.projects.cochsoundloc.ITDFilter;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Filters incoming events with "bumps" of bearing angle
 * that come from cochlea ITD processing.
 *
 * @author leah/tobidelbruck
 */
@Description("Filters incoming events with \"bumps\" of bearing angle that come from ITDBins binaural cochlea sound localization")
public class BearingBumpFilter extends EventFilter2D implements Observer, FrameAnnotater {

	private Random random = new Random();
	private ITDFilter itdFilter = null;
	private float[] probs = null;
	private float threshold = getPrefs().getFloat("BearingBumpFilter.threshold", .5f);
	private boolean normalizeToZero = getPrefs().getBoolean("BearingBumpFilter.normalizeToZero", false);
	private float fovRetinaDeg = getPrefs().getFloat("BearingBumpFilter.fovRetinaDeg", 60);
	private float itdBinShift = getPrefs().getFloat("BearingBumpFilter.itdBinShift", 0);

	/**
	 * @return the RetinalShiftPix
	 */
	public float getITDBinShift() {
		return itdBinShift;
	}

	/**
	 * @param itdBinShift the RetinalShiftPix to set
	 */
	public void setITDBinShift(float itdBinShift) {
		int nbins = itdFilter.getITDBins().getNumOfBins();
		int range = (int) ((fovRetinaDeg / 180) * nbins);
		//        int b = (int) (nbins - range) / 2;
		if (itdBinShift < -range) {
			itdBinShift = -range;
		} else if (itdBinShift > range) {
			itdBinShift = range;
		}
		this.itdBinShift = itdBinShift;
		getPrefs().putFloat("BearingBumpFilter.itdBinShift", itdBinShift);
	}

	public enum Method {

		Probabilistic, Thresholded
	};
	private Method method = Method.valueOf(getPrefs().get("BearingBumpFilter.method", Method.Probabilistic.toString()));


	public BearingBumpFilter(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		initFilter();
		resetFilter();
		setPropertyTooltip("ConnectToCochleaITDFilter", "connects to existing ITDFilter on another AEViewer with a cochlea that is running ITDFilter. Do this if histogram doesn't appear or stops changing.");
		setPropertyTooltip("normalizeToZero", "normalizes transmission probability to zero for min ITD histogram value");
		setPropertyTooltip("method", "Probabilistic: pass spikes according to analog value of ITD histogram. Thresholded: pass if histogram is above threshold");
		setPropertyTooltip("fovRetinaDeg", "The Field of View (FOV) of the retina in degrees. The ITD range is assumed to be 180 deg.");
	}

	/**
	 * Filters in to out, according to the probabilities coming from ITDBins.
	 *@param in input events can be null or empty.
	 *@return the the filtered events.
	 */
	@Override
	synchronized public EventPacket filterPacket(EventPacket in) {
		if (itdFilter == null) {
			return in;
		}
		if ((in == null) || (in.getSize() == 0)) {
			return in;
		}

		final int sx = chip.getSizeX();

		// getString normalized probabilities
		float max = 0, min = Float.MAX_VALUE;
		final float[] bins = itdFilter.getITDBins().getBins();
		int nbins = bins.length;
		for (float f : bins) {
			if (f > max) {
				max = f;  // getString max bin
			}
			if (f < min) {
				min = f; // and min
			}
		}
		final float diff = max - min;
		if (probs == null) {
			probs = new float[sx];
		}
		for (int i = 0; i < sx; i++) {
			int ind = getBin(i, nbins, sx);
			if (normalizeToZero) {
				probs[i] = (bins[ind] - min) / diff;
			} else {
				probs[i] = bins[ind] / max;
			}
		}

		checkOutputPacketEventType(in);
		// for each event only write it to the out buffers if it is within dt of the last time an event happened in neighborhood
		OutputEventIterator outItr = out.outputIterator();
		for (Object e : in) {
			BasicEvent i = (BasicEvent) e;
			switch (method) {
				case Probabilistic:
					float r = random.nextFloat();
					if (r <= probs[i.x]) {
						BasicEvent o = outItr.nextOutput();
						o.copyFrom(i);
					}
					break;
				case Thresholded:
					if (probs[i.x] > threshold) {
						BasicEvent o = outItr.nextOutput();
						o.copyFrom(i);
					}
			}
		}
		return out;
	}

	/** Returns histogram bin number corresponding to a retina pixel number, taking account
	 * of FOV of retina compared with 180 deg of ITD.
	 * @param pixel
	 * @return itd bin
	 */
	private int getBin(int pixel, int nbins, int sx) {
		int range = (int) ((fovRetinaDeg / 180) * nbins);
		int b = (nbins - range) / 2;
		float m = (float) range / sx;
		return (int) ((m * pixel) + b + itdBinShift);
	}

	public Object getFilterState() {
		return null;
	}

	@Override
	synchronized public void resetFilter() {
		doConnectToCochleaITDFilter();
	}

	@Override
	public void update(Observable o, Object arg) {
	}

	@Override
	public void initFilter() {
	}

	public void annotate(float[][][] frame) {
	}

	public void annotate(Graphics2D g) {
	}

	/** Shows the ITD bins at as trace along bottom of display.
	 *
	 * @param drawable
	 */
	@Override
	public void annotate(GLAutoDrawable drawable) {
		if (!isFilterEnabled()) {
			return;
		}
		final GL2 gl = drawable.getGL().getGL2();
		final int font = GLUT.BITMAP_HELVETICA_18;
		{
			gl.glPushMatrix();
			final GLUT glut = new GLUT();
			gl.glLineWidth(2f);
			gl.glColor3f(0, 0, 1);
			if (probs == null) {
				gl.glRasterPos3f(0, chip.getSizeY() / 2, 0);
				glut.glutBitmapString(font, "No ITDFilter found to get bumps from");
				gl.glPopMatrix();
				return;
			}
			final int sx = chip.getSizeX();
			final int sy = chip.getSizeY();
			gl.glBegin(GL.GL_LINE_STRIP);
			for (int i = 0; i < sx; i++) {
				gl.glVertex2f(i, sy * probs[i]);
			}
			gl.glEnd();
			if (getMethod() == Method.Thresholded) {
				gl.glColor3f(1, 0, 0);
				gl.glBegin(GL.GL_LINES);
				gl.glVertex2f(0, sy * getThreshold());
				gl.glVertex2f(sx, sy * getThreshold());
				gl.glEnd();
				gl.glRasterPos3f(0, sy * threshold, 0);
				glut.glutBitmapString(font, "Threshold");
			}
			gl.glPopMatrix();
		}
	}

	/**
	 * Finds the ITDBins object by looking for another AEViewer
	 * in the same JVM that has a cochlea
	 * chip, then iterates over this chip's filterchain
	 * to find ITDFilter. From this ITDFilter it gets the
	 * ITDBins, which it uses to filter this filter's events.
	 */
	public void doConnectToCochleaITDFilter() {
		try {
			AEViewer myViewer = chip.getAeViewer();
			ArrayList<AEViewer> viewers = myViewer.getJaerViewer().getViewers();
			for (AEViewer v : viewers) {
				if (v == myViewer) {
					continue;
				}
				AEChip c = v.getChip();
				FilterChain fc = c.getFilterChain();
				if (v.getChip() instanceof CochleaChip) {
					itdFilter = (ITDFilter) fc.findFilter(ITDFilter.class);
					log.info("found cochlea chip=" + c + " in AEViewer=" + v + " with ITDFilter=" + itdFilter);
					break;
				}
			}
			if (itdFilter == null) {
				log.warning("couldn't find ITDFilter anywhere");
			}
		} catch (Exception e) {
			log.warning("while trying to find ITDFilter caught " + e);
		}
	}

	/**
	 * @return the method
	 */
	public Method getMethod() {
		return method;
	}

	/**
	 * Sets method, either threshold on ITD histogram or probabilistic based on histogram of ITDs.
	 * @param method the method to set
	 */
	public void setMethod(Method method) {
		this.method = method;
		getPrefs().put("BearingBumpFilter.method", method.toString());
	}

	/**
	 * @return the threshold
	 */
	public float getThreshold() {
		return threshold;
	}

	/**
	 * @param threshold the threshold to set
	 */
	public void setThreshold(float threshold) {
		if (threshold < 0) {
			threshold = 0;
		} else if (threshold > 1) {
			threshold = 1;
		}
		this.threshold = threshold;
		getPrefs().putFloat("BearingBumpFilter.threshold", threshold);
	}

	public float getMinThreshold() {
		return 0;
	}

	public float getMaxThreshold() {
		return 1;
	}

	/**
	 * @return the normalizeToZero
	 */
	public boolean isNormalizeToZero() {
		return normalizeToZero;
	}

	/**
	 * @param normalizeToZero the normalizeToZero to set
	 */
	public void setNormalizeToZero(boolean normalizeToZero) {
		this.normalizeToZero = normalizeToZero;
		getPrefs().putBoolean("BearingBumpFilter.normalizeToZero", normalizeToZero);
	}

	/**
	 * @return the fovRetinaDeg
	 */
	public float getFovRetinaDeg() {
		return fovRetinaDeg;
	}

	/**
	 * @param fovRetinaDeg the fovRetinaDeg to set
	 */
	public void setFovRetinaDeg(float fovRetinaDeg) {
		if (fovRetinaDeg < 10) {
			fovRetinaDeg = 10;
		} else if (fovRetinaDeg > 180) {
			fovRetinaDeg = 180;
		}
		this.fovRetinaDeg = fovRetinaDeg;
		getPrefs().putFloat("BearingBumpFilter.fovRetinaDeg", fovRetinaDeg);

	}
}
