/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiotemporalcloseness.util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 */
public class SimpleEventGroup implements EventGroup {

	/** The timestamp of the group. */
	private double timestamp;

	/** The maximal timestamp of the group. */
	private int maxTimestamp;

	/** Stores the type of the group. */
	private int type;

	/** Stores all events belonging to this group. */
	private List<TypedEvent> events;

	/**
	 * Creates a new instance of the class SimpleEventGroup.
	 * 
	 * @param e The first event of the group.
	 */
	public SimpleEventGroup(TypedEvent e) {
		events = new ArrayList<TypedEvent>();

		type = e.type;
		timestamp = 0;
		maxTimestamp = 0;
		this.add(e);
	}

	@Override
	public void add(TypedEvent e) {
		timestamp = ((timestamp * events.size()) + e.timestamp) / (events.size() + 1);
		events.add(e);

		maxTimestamp = Math.max(maxTimestamp, e.timestamp);
	}

	@Override
	public void add(EventGroup group) {
		timestamp = ((timestamp * events.size()) + (group.getTimestamp() * group.getSize())) / (events.size() + group.getSize());
		events.addAll(group.getEvents());

		for (TypedEvent e : group.getEvents()) {
			maxTimestamp = Math.max(maxTimestamp, e.timestamp);
		}
	}

	@Override
	public int getType() {
		return type;
	}

	@Override
	public double getTimestamp() {
		return timestamp;
	}

	@Override
	public int getMaxTimestamp() {
		return maxTimestamp;
	}

	@Override
	public int getSize() {
		return events.size();
	}

	@Override
	public List<TypedEvent> getEvents() {
		return events;
	}

	@Override
	public void draw(GLAutoDrawable drawable, int current, int resolution) {
		GL2 gl = drawable.getGL().getGL2();

		float hue = Math.max(0, 0.7f - ((events.size()) / 10.0f));
		Color c = new Color(Color.HSBtoRGB(hue, 1.0f, 1.0f));
		gl.glColor3f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f);

		TypedEvent[] es = events.toArray(new TypedEvent[0]);

		gl.glPointSize(3);
		gl.glBegin(GL.GL_POINTS);
		{
			for (TypedEvent element : es) {
				gl.glVertex3f(element.x, element.y, (current - element.timestamp) / resolution);
			}
		}
		gl.glEnd();
	}
}
