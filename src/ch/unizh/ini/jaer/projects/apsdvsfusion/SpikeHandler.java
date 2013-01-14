/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import net.sf.jaer.event.PolarityEvent;

/**
 * @author Dennis
 *
 */
public interface SpikeHandler {
	public void spikeAt(int x, int y, int time, PolarityEvent.Polarity polarity);
	public void reset();
}	
