/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.tobi.goalie;

import java.awt.Graphics2D;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * For the Goalie; filters in events from a trapezoidal region, discarding those
 * from the edges and end of the table.
 *
 * @author tobi/fope, telluride 2008
 */
@Description("Filters out events outside trapezoidal table shaped region for Goalie")
public class GoalieTableFilter extends EventFilter2D implements FrameAnnotater, Observer {

	private int x0;
	private int x1;
	private int top;
	private int bottom;

	public GoalieTableFilter(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		initFilter();
		setPropertyTooltip("x1", "UR trapezoid X in pixels");
		setPropertyTooltip("x0", "UL trapezoid corner x in pixels");
		setPropertyTooltip("bottom", "bottom of trapezoid in pixels");
		setPropertyTooltip("top", "trapezoid top Y in pixels");
	}

	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		if (!isFilterEnabled()) {
			return in;
		}
		checkOutputPacketEventType(in);
		OutputEventIterator outItr = out.outputIterator();
		for (BasicEvent i : in) {
			if (isInsideTable(i)) {
				BasicEvent o = outItr.nextOutput();
				o.copyFrom(i);
			}
		}
		return out;
	}

	@Override
	public void resetFilter() {
	}

	@Override
	public void initFilter() {
		x0 = getPrefs().getInt("GoalieTableFilter.x0", 0);
		x1 = getPrefs().getInt("GoalieTableFilter.x1", chip.getSizeX());
		top = getPrefs().getInt("GoalieTableFilter.top", chip.getSizeY());
		bottom = getPrefs().getInt("GoalieTableFilter.bottom", 0);
	}

	/**
	 * returns true if the event is inside the trapezoidal table area
	 */
	private boolean isInsideTable(BasicEvent i) {
		// if i.x and i.y is inside the trapezoid then return true
		float xv0;
		float xv1;
		xv0 = ((float) x0 * i.y) / top;
		xv1 = (x0 + x1) - (((float) i.y * x0) / top);
		if ((i.y < top) && (i.y > bottom) && (i.x > xv0) && (i.x < xv1)) {
			return true;
		} else {
			return false;
		}
	}

	public int getX0() {
		return x0;
	}

	public void setX0(int x0) {
		if (x0 < 0) {
			x0 = 0;
		} else if (x0 > x1) {
			x0 = x1;
		}
		this.x0 = x0;
		getPrefs().putInt("GoalieTableFilter.x0", x0);
	}

	public int getX1() {
		return x1;
	}

	public void setX1(int x1) {
		if (x1 > chip.getSizeX()) {
			x1 = chip.getSizeX();
		} else if (x1 < x0) {
			x1 = x0;
		}
		this.x1 = x1;
		getPrefs().putInt("GoalieTableFilter.x1", x1);
	}

	public void setTop(int y) {
		if (y > chip.getSizeY()) {
			y = chip.getSizeY();
		} else if (y < bottom) {
			y = bottom;
		}
		top = y;
		getPrefs().putInt("GoalieTableFilter.top", y);
	}

	public int getTop() {
		return top;
	}

	public void annotate(float[][][] frame) {
	}

	public void annotate(Graphics2D g) {
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if (!isFilterEnabled()) {
			return;
		}
		GL2 gl = drawable.getGL().getGL2();
		gl.glPushMatrix();
		gl.glColor3d(0, 0, 0.5);
		gl.glLineWidth(2f);
		gl.glBegin(GL.GL_LINE_LOOP);
		gl.glVertex2f(0, bottom);
		gl.glVertex2f(x0, top);
		gl.glVertex2f(x1, top);
		gl.glVertex2f(chip.getSizeX(), bottom);
		gl.glEnd();
		gl.glPopMatrix();
	}

	public int getBottom() {
		return bottom;
	}

	public void setBottom(int bottom) {
		if (bottom < 0) {
			bottom = 0;
		} else if (bottom > top) {
			bottom = top;
		}
		this.bottom = bottom;
		getPrefs().putInt("GoalieTableFilter.bottom", bottom);
	}

	@Override
	public void update(Observable o, Object arg) {
		initFilter();
	}
}
