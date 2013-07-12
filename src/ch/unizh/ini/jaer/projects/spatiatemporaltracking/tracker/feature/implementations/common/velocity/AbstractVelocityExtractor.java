/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.velocity;

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
 * This abstract class provides some basic methods used by concrete
 * implementations of the interface VelocityExtractor.
 */
public abstract class AbstractVelocityExtractor extends AbstractFeatureExtractor implements VelocityExtractor {

	/** Stores the velocity of the object. */
	protected Vector velocity;

	/**
	 * Creates a new instance of a AbstractVelocityExtractor.
	 */
	public AbstractVelocityExtractor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.Velocity, Color.getBlue(), chip);

		init();
		reset();
	}

	@Override
	public void init() {
		super.init();

		velocity = Vector.getDefault(3);
	}

	@Override
	public void reset() {
		super.reset();

		velocity.reset();
	}

	@Override
	public Vector getVelocity() {
		return velocity;
	}

	@Override
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
		if (isDebugged) {
			renderer.begin3DRendering();
			renderer.setColor(0,0,1,0.8f);
			renderer.draw3D(toString(), x, y, 0, 0.5f);
			renderer.end3DRendering();
		}

		GL2 gl = drawable.getGL().getGL2();
		gl.glColor3f(0, 0, 1);

		Vector p = ((PositionExtractor)features.get(Features.Position)).getPosition();

		gl.glLineWidth(3);
		gl.glBegin(GL.GL_LINES);
		{
			gl.glVertex2f(p.get(0),
				p.get(1));
			gl.glVertex2f(p.get(0) + ((velocity.get(0) / 5) * SECOND),
				p.get(1) + ((velocity.get(1) / 5) * SECOND));
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
		return "velocity: " + velocity.toString();
	}
}
