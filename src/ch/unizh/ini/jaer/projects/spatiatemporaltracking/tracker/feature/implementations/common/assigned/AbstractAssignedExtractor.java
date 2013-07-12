/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.assigned;

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
 * This abstract class provides some basic methods used by implementations
 * of the interface AssignedExtractor.
 */
public abstract class AbstractAssignedExtractor extends AbstractFeatureExtractor implements AssignedExtractor {

	/** Defines the resolution with which the events are stores. */
	protected int resolution = 200;

	/** Defiens the number of elements used for the visualization. */
	protected int nVisualization = 100;

	/** Stores the events assigned to the object. */
	protected List<List<EventStorage>> storage;

	/** Stores the events for a visualization. */
	protected List<List<EventStorage>> visualization;

	/** Flag to reset the extractor. */
	protected boolean isReseted;

	/**
	 * Creates a new instance of a AbstractAssignedExtractor.
	 */
	public AbstractAssignedExtractor (Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.Assigned, Color.getBlue(), chip);

		init();
		reset();
	}

	@Override
	public void init() {
		super.init();

		storage = new ArrayList<List<EventStorage>>();
		for (int i = 0; i < 2; i++) {
			storage.add(new ArrayList<EventStorage>());
		}

		visualization = new ArrayList<List<EventStorage>>();
		for (int i = 0; i < 2; i++) {
			visualization.add(Collections.synchronizedList(new ArrayList<EventStorage>()));
		}
	}

	@Override
	public void reset() {
		super.reset();

		for (int i = 0; i < storage.size(); i++) {
			storage.get(i).clear();
		}

		isReseted = true;
	}

	@Override
	public List<List<EventStorage>> getAssignedEvents() {
		return storage;
	}

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
			for (int i = 0; i < visualization.size(); i++) {
				while (visualization.get(i).size() > nVisualization) {
					visualization.get(i).remove(0);
				}
			}

			/*
			 * draw
			 */
			float pack = 50f / nVisualization;
			for (int i = 0; i < visualization.size(); i++) {
				List<EventStorage> l = new ArrayList<EventStorage>(); l.addAll(visualization.get(i));
				int to = Math.min(l.size(), nVisualization);

				renderer.begin3DRendering();
				renderer.setColor(0,0,1,0.8f);
				renderer.draw3D("assigned events of type: " + i, x, y, 0, 0.5f);
				renderer.end3DRendering();

				float max = 0;
				for (int j = 0; j < to; j++) {
					if (max < l.get(j).count) {
						max = l.get(j).count;
					}
				}

				for (int j = 0; j < to; j++) {
					float h = (l.get(j).count / max) * (getHeight() - 4);

					float dx = x + (j * pack);
					float dy = (y - getHeight()) + 3;

					gl.glBegin(GL.GL_LINE_LOOP);
					{

						gl.glVertex2f(dx, dy);
						gl.glVertex2f(dx, dy + h);

						gl.glVertex2f(dx + pack, dy + h);
						gl.glVertex2f(dx + pack, dy);
					}
					gl.glEnd();
				}
				x += 55;
			}
		}
		else {
			/*
			 * delete all data for the visualization
			 */
			for (int i = 0; i < visualization.size(); i++) {
				while (visualization.get(i).size() > nVisualization) {
					visualization.get(i).clear();
				}
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
