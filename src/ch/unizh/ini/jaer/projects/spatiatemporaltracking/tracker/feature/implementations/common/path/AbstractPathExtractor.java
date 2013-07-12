/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.path;

import java.util.ArrayList;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;
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
 * Concrete instances of this abstract class have to extract the path of the
 * observed object.
 */
public abstract class AbstractPathExtractor extends AbstractFeatureExtractor implements PathExtractor {

	/** Stores the path of the observed object. */
	protected PathLocation path;

	/** Stores the path for a visualization. */
	protected List<PathLocation> visualization;

	/**
	 * Creates a new instance of a AbstractPathExtractor.
	 */
	public AbstractPathExtractor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.Path, Color.getBlue(), chip);

		init();
		reset();
	}

	@Override
	public void init() {
		super.init();

		visualization = new ArrayList<PathLocation>();
	}

	@Override
	public void reset() {
		super.reset();

		visualization.clear();
	}

	@Override
	public PathLocation getPath() {
		return path;
	}

	@Override
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glColor3f(0, 0, 1);

		while (visualization.size() > 100) {
			visualization.remove(0);
		}

		gl.glPointSize(3);
		gl.glBegin(GL.GL_POINTS);
		{
			for (int i = 0; i < visualization.size(); i++) {
				gl.glVertex2f(visualization.get(i).location.get(0),
					visualization.get(i).location.get(1));
			}
		}
		gl.glEnd();

		gl.glBegin(GL.GL_LINE_STRIP);
		{
			for (int i = 0; i < visualization.size(); i++) {
				gl.glVertex2f(visualization.get(i).location.get(0),
					visualization.get(i).location.get(1));
			}
		}
		gl.glEnd();
	}

	@Override
	public int getHeight() {
		return 0;
	}
}
