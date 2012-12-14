/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.ArrayList;

import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 * @author Dennis
 *
 */
public class SpikeHandlerSet implements SpikeHandler {

	ArrayList<SpikeHandler> spikeHandlers = new ArrayList<SpikeHandler>();
	
	/**
	 * 
	 */
	public SpikeHandlerSet() {
	}
	
	

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SpikeHandler#spikeAt(int, int, int, net.sf.jaer.event.PolarityEvent.Polarity)
	 */
	@Override
	public void spikeAt(int x, int y, int time, Polarity polarity) {
		for (SpikeHandler sh : spikeHandlers) 
			sh.spikeAt(x, y, time, polarity);
		
	}
	
	public boolean addSpikeHandler(SpikeHandler handler) {
		if (!this.spikeHandlers.contains(handler)) {
			spikeHandlers.add(handler);
			return true;
		}
		else return false;
	}

	public boolean removeSpikeHandler(SpikeHandler handler) {
		if (this.spikeHandlers.contains(handler)) {
			spikeHandlers.remove(handler);
			return true;
		}
		else return false;
	}
}
