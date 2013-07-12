/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.acceleration;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;

import com.jogamp.opengl.util.awt.TextRenderer;

/**
 *
 * @author matthias
 * 
 * Provides some basic methods for concrete implementations of the interface
 * AccelerationPredictor.
 */
public abstract class AbstractAccelerationPredictor extends AbstractFeatureExtractor implements AccelerationPredictor {

	/** Stores the predicted acceleration of the object. */
	protected Vector acceleration;

	/**
	 * Creates a new instance of AbstractAccelerationPredictor.
	 */
	public AbstractAccelerationPredictor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.AccelerationPredictor, Color.getYellow(), chip);

		init();
		reset();
	}

	@Override
	public void init() {
		super.init();

		acceleration = Vector.getDefault(3);
	}

	@Override
	public void reset() {
		super.reset();

		acceleration.reset();
	}

	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
		if (isDebugged) {
			renderer.begin3DRendering();
			renderer.setColor(color.getFloat(0),
				color.getFloat(1),
				color.getFloat(2),
				0.8f);
			renderer.draw3D(toString(), x, y, 0, 0.5f);
			renderer.end3DRendering();
		}

		GL2 gl = drawable.getGL().getGL2();
		gl.glColor3f(color.getFloat(0),
			color.getFloat(1),
			color.getFloat(2));
		gl.glLineWidth(3);

		Vector p = ((PositionExtractor)features.get(Features.Position)).getPosition();
		gl.glBegin(GL.GL_LINES);
		{
			gl.glVertex2f(p.get(0),
				p.get(1));
			gl.glVertex2f(p.get(0) + (acceleration.get(0) * SECOND),
				p.get(1));

			gl.glVertex2f(p.get(0),
				p.get(1));
			gl.glVertex2f(p.get(0),
				p.get(1) + (acceleration.get(1) * SECOND));
		}
		gl.glEnd();

		gl.glLineWidth(1);
	}

	@Override
	public int getHeight() {
		if (isDebugged) {
			return 4;
		}
		return 0;
	}

	@Override
	public String toString() {
		return "acceleration: " + acceleration.toString();
	}
}
