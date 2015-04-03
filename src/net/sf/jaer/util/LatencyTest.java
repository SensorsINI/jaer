/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Tests DVS latency by drawing boxes on the screen on display updates. A camera can image this display and if the output is recorded, the latency from screen back
 * to screen can be determined from the recorded data.
 *
 * @author tobi
 */
public class LatencyTest extends EventFilter2D implements FrameAnnotater {

	private int delayFrames = 1;
	private float size = .05f;
	private float inset = 3;
	private int corner = 0, delayCount = delayFrames;

	public LatencyTest(AEChip chip) {
		super(chip);
	}

	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		return in;
	}

	@Override
	public void resetFilter() {
	}

	@Override
	public void initFilter() {
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		float csx = chip.getSizeX(), csy = chip.getSizeY();
		final float sx = size * chip.getSizeX(), sy = size * chip.getSizeY();
		final float inx = inset * chip.getSizeX(), iny = inset * chip.getSizeY();
		gl.glColor3f(1, 1, 1);
		switch (getCorner()) {
			case 0:
				gl.glRectf(inx, iny, inx + sx, iny + sy);
				break;
			case 1:
				gl.glRectf(csx - inx, iny, csx - inx - sx, iny + sy);
				break;
				//            case 2:
					//                gl.glRectf(csx-inx, csy-iny, csx-inx - sx, csy-iny - sy);
					//                break;
					//            case 3:
				//                gl.glRectf(inx, csy-iny, inx + sx, csy-iny - sy);
				//                break;
			default:

		}
		delayCount++;
		if (delayCount > delayFrames) {
			delayCount=0;
			corner++;
			if (corner > 1) {
				corner = 0;
			}
		}
	}

	/**
	 * @return the delayFrames
	 */
	public int getDelayFrames() {
		return delayFrames;
	}

	/**
	 * @param delayFrames the delayFrames to set
	 */
	public void setDelayFrames(int delayFrames) {
		this.delayFrames = delayFrames;
	}

	/**
	 * @return the size
	 */
	public float getSize() {
		return size;
	}

	/**
	 * @param size the size to set
	 */
	public void setSize(float size) {
		this.size = size;
	}

	/**
	 * @return the inset
	 */
	public float getInset() {
		return inset;
	}

	/**
	 * @param inset the inset to set
	 */
	public void setInset(float inset) {
		this.inset = inset;
	}

	/**
	 * @return the corner
	 */
	public int getCorner() {
		return corner;
	}

	/**
	 * @param corner the corner to set
	 */
	public void setCorner(int corner) {
		this.corner = corner;
	}
}
