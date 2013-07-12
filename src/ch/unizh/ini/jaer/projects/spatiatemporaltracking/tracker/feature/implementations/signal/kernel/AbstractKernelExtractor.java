/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.kernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
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
 * This abstract class provides some basic operations used by implementations
 * of the interface KernelExtractor.
 */
public abstract class AbstractKernelExtractor extends AbstractFeatureExtractor implements KernelExtractor {

	/** Defiens the interval for the observation used for the visualization. */
	protected int interval = 20000;

	/** Stores the results of the different kernels. */
	protected Storage[] storage;

	/** Stores the results of the different kernels for a visualization. */
	protected List<List<Storage>> visualization;

	/** Stores a set of colors used by the visualization. */
	protected Color[] colors = {new Color(0, 0, 1), new Color(1, 0, 0)};

	/** Flag to reset the extractor. */
	protected boolean isReseted;

	/**
	 * Creates a new instance of the class AbstractKernelExtractor.
	 */
	public AbstractKernelExtractor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.Kernel, Color.getBlue(), chip);
	}

	@Override
	public void init() {
		super.init();

		storage = new Storage[2];

		visualization = new ArrayList<List<Storage>>();
		for (Storage element : storage) {
			visualization.add(Collections.synchronizedList(new ArrayList<Storage>()));
		}

	}

	@Override
	public void reset() {
		super.reset();

		isReseted = true;
	}

	@Override
	public Storage[] getStorage() {
		return storage;
	}

	@Override
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
		if (isReseted) {
			isReseted = false;
			for (int i = 0; i < visualization.size(); i++) {
				visualization.get(i).clear();
			}
		}

		if (isDebugged) {
			GL2 gl = drawable.getGL().getGL2();

			/*
			 * prepare
			 */
			 List<List<Storage>> l = new ArrayList<List<Storage>>();
			for (int i = 0; i < visualization.size(); i++) {
				while (!visualization.get(i).isEmpty() &&
					((visualization.get(i).get(visualization.get(i).size() - 1).timestamp - visualization.get(i).get(0).timestamp) > interval)) {
					visualization.get(i).remove(0);
				}
				l.add(new ArrayList<Storage>());
				l.get(i).addAll(visualization.get(i));
			}

			/*
			 * find start time of visualization
			 */
			 int reference = Integer.MAX_VALUE;
			 for (int i = 0; i < visualization.size(); i++) {
				 if (!l.get(i).isEmpty()) {
					 reference = Math.min(reference, visualization.get(i).get(0).timestamp);
				 }
			 }

			 /*
			  * draw
			  */

			 float pack = 50f / interval;
			 for (int i = 0; i < visualization.size(); i++) {
				 renderer.begin3DRendering();
				 renderer.setColor(0,0,1,0.8f);
				 renderer.draw3D("assigned events of type: " + i, x, y, 0, 0.5f);
				 renderer.end3DRendering();

				 if (!l.get(i).isEmpty()) {
					 float max = 0;
					 for (int j = 0; j < l.get(i).size(); j++) {
						 for (float element : l.get(i).get(j).absolute) {
							 if (max < element) {
								 max = element;
							 }
						 }
					 }

					 float dy = (y - getHeight()) + 3;
					 for (int k = 0; k < l.get(i).get(0).absolute.length; k++) {
						 gl.glColor3f(colors[k].getFloat(0),
							 colors[k].getFloat(1),
							 colors[k].getFloat(2));

						 gl.glBegin(GL.GL_LINE_STRIP);
						 {
							 for (int j = 0; j < l.get(i).size(); j++) {
								 int d = l.get(i).get(j).timestamp - reference;

								 if (d > interval) {
									 j = l.get(i).size();
								 }
								 else {
									 float dx = x + (d * pack);

									 float h = (visualization.get(i).get(j).absolute[k] / max) * (getHeight() - 4);
									 gl.glVertex2f(dx, dy + h);
								 }
							 }
						 }
						 gl.glEnd();
					 }
				 }
				 x += 55;
			 }
		}
		else {
			/*
			 * delete all data for the visualization
			 */
			for (int i = 0; i < visualization.size(); i++) {
				visualization.get(i).clear();
			}
		}
	}

	@Override
	public int getHeight() {
		if (isDebugged) {
			return 10;
		}
		return 0;
	}
}
