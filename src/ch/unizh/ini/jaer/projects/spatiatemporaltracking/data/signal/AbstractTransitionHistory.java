/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal;


import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;

import com.jogamp.opengl.util.awt.TextRenderer;

/**
 *
 * @author matthias
 * 
 * The abstract class is used to store the Transitions of a signal.
 */
public abstract class AbstractTransitionHistory implements TransitionHistory {

	@Override
	public void reset() {

	}

	@Override
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, Color color, float x, float y, int height, int resolution) {
		if (getSize() <= 0) {
			return;
		}

		GL2 gl = drawable.getGL().getGL2();

		renderer.begin3DRendering();
		renderer.draw3D(toString(), x, y, 0, 0.5f);
		renderer.end3DRendering();

		gl.glColor3d(color.getFloat(0), color.getFloat(1), color.get(2));

		int offset = getTransition(0).time;
		for (int i = 0; i < (getSize() - 1); i++) {
			float start = (getTransition(i).time - offset) / 1000.0f;
			float end = (getTransition(i + 1).time - offset) / 1000.0f;

			if (start > end) {
				return;
			}
			if (start > resolution) {
				return;
			}

			float stateOld = getTransition(i).state;
			float stateNew = getTransition(i + 1).state;

			if (end > resolution) {
				end = resolution;
				stateNew = stateOld;
			}

			gl.glBegin(GL.GL_LINES);
			{
				gl.glVertex2f(x + start, (y - height) + ((height - 4) * stateOld) + 3);
				gl.glVertex2f(x + end, (y - height) + ((height - 4) * stateOld) + 3);
			}
			gl.glEnd();

			gl.glBegin(GL.GL_LINES);
			{
				gl.glVertex2f(x + end, (y - height) + ((height - 4) * stateOld) + 3);
				gl.glVertex2f(x + end, (y - height) + ((height - 4) * stateNew) + 3);
			}
			gl.glEnd();
		}
	}

	@Override
	public String toString() {
		return "transition history [us]: " + getTransition(0).time + " - " + getTransition(getSize() - 1).time + ".";
	}
}

