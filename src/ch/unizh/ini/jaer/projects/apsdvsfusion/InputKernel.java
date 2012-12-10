/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;

/**
 * @author Dennis Goehlsdorf
 *
 */
public interface InputKernel {
	public void apply(int x, int y, int time, PolarityEvent.Polarity polarity, FiringModelMap map, EventPacket<?> out);
}
