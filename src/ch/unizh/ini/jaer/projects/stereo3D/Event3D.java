/*
 * Event3D.java
 *
 * Created on December 7, 2007, 10:32 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.jaer.projects.stereo3D;

import net.sf.jaer.aemonitor.EventRaw;

/**
 * A raw address-event, having a timestamp and raw address
 *
 * @author tobi
 */
public class Event3D extends EventRaw {

	// type, INDIRECT3D means it needs to be reconstructed, DIRECT3D mean we can use its x,y,z directly
	static public int INDIRECT3D = 0;
	static public int DIRECT3D = 1;

	public int type;
	public int x;
	public int y;
	public int d;
	public int method;
	public int lead_side;
	public float value;
	public float score;
	public int x0;
	public int y0;
	public int z0;

	/** Creates a new instance of EventRaw */
	public Event3D() {
		super();
	}

	@Override
	public String toString() {
		return "Event3D type:" + type + " at x:" + x + " y:" + y + " d:" + d + " lead_side:" + lead_side + " at time:"
			+ timestamp;
	}

}
