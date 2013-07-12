/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.position;

import java.util.ArrayList;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;

import com.jogamp.opengl.util.awt.TextRenderer;

/**
 *
 * @author matthias
 * 
 * Concrete implementations of this abstract class have to provide methods to
 * predict the position of the observed object in the future.
 */
public abstract class AbstractPositionExtractor extends AbstractFeatureExtractor implements PositionPredictor {

	/** Stores a history of the predicted positions. */
	protected List<Vector> history;

	/**
	 * Creates a new instance of AbstractPositionExtractor.
	 */
	public AbstractPositionExtractor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.PositionPredictor, Color.getYellow(), chip);
	}

	@Override
	public void init() {
		super.init();

		history = new ArrayList<Vector>();
	}

	@Override
	public void reset() {
		super.reset();

		history.clear();
	}

	@Override
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glLineWidth(3);

		while (history.size() > 10000) {
			history.remove(0);
		}
		Vector[] v = history.toArray(new Vector[0]);

		gl.glColor3f(1, 1, 1);
		gl.glBegin(GL.GL_POINTS);
		{
			for (Vector element : v) {
				if (element != null) {
					gl.glVertex2f(element.get(0), element.get(1));
				}
			}
		}
		gl.glEnd();

		gl.glColor3f(color.getFloat(0),
			color.getFloat(1),
			color.getFloat(2));


		gl.glBegin(GL.GL_POINTS);
		{
			for (int i = 10000; i < 100000; i+= 10000) {
				Vector p = getPositionRelative(i);

				gl.glVertex2f(p.get(0), p.get(1));
			}
		}
		gl.glEnd();

		gl.glLineWidth(1);
	}

	@Override
	public int getHeight() {
		return 0;
	}
}
