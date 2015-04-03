/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips;


import java.awt.Font;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;

import com.jogamp.opengl.util.awt.TextRenderer;

/**
 * A specialized AE chip rendering method that handles chips with global average intensity output.
 *
 * @author tobi
 */
public class DVSWithIntensityDisplayMethod extends ChipRendererDisplayMethod {

	private HasIntensity hasIntensity = null;
	private TextRenderer renderer = null;
	private float intensityScale = 1f;
	private boolean intensityDisplayEnabled=true;

	public DVSWithIntensityDisplayMethod(ChipCanvas chipCanvas) {
		super(chipCanvas);
		renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 10), true, true);
		intensityDisplayEnabled=chipCanvas.getChip().getPrefs().getBoolean("intensityDisplayEnabled", true);
		intensityScale=chipCanvas.getChip().getPrefs().getFloat("intensityScale", 1f);
	}

	/** Sets the source of the "intensity" value.
	 *
	 * @param hasIntensity
	 */
	public void setIntensitySource(HasIntensity hasIntensity) {
		this.hasIntensity = hasIntensity;
	}

	/** Displays intensity along with normal 2d histograms.
	 *
	 * @param drawable
	 */
	@Override
	public void display(GLAutoDrawable drawable) {
		super.display(drawable);
		if (intensityDisplayEnabled && (hasIntensity != null)) {
			GL2 gl = drawable.getGL().getGL2();
			gl.glLineWidth(10);
			gl.glColor3f(0, 1, 0);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(-1, 0);
			float f = hasIntensity.getIntensity()*intensityScale;
			f = f * getChipCanvas().getChip().getSizeY();
			gl.glVertex2f(-1, f);
			gl.glEnd();
			{
				renderer.begin3DRendering();
				renderer.setColor(1, 1, 1, 1f);
				renderer.draw3D("Intensity", -10, 3, 0, .2f); // TODO fix string n lines
				renderer.draw3D(String.format("%.2f", hasIntensity.getIntensity()), -10, 0, 0, .2f); // TODO fix string n lines
				renderer.end3DRendering();
			}

		}
	}

	/**
	 * @return the intensityScale
	 */
	public float getIntensityScale() {
		return intensityScale;
	}

	/**
	 * @param intensityScale the intensityScale to set
	 */
	public void setIntensityScale(float intensityScale) {
		this.intensityScale = intensityScale;
		getChipCanvas().getChip().getPrefs().putFloat("intensityScale",intensityScale);
	}

	/**
	 * @return the intensityDisplayEnabled
	 */
	public boolean isIntensityDisplayEnabled() {
		return intensityDisplayEnabled;
	}

	/**
	 * @param intensityDisplayEnabled the intensityDisplayEnabled to set
	 */
	public void setIntensityDisplayEnabled(boolean intensityDisplayEnabled) {
		this.intensityDisplayEnabled = intensityDisplayEnabled;
		getChipCanvas().getChip().getPrefs().putBoolean("intensityDisplayEnabled",intensityDisplayEnabled);
	}
}
