/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.ArrayList;

/**
 * @author Dennis
 *
 */
public class SignalHandlerSet implements SignalHandler {

	ArrayList<SignalHandler> spikeHandlers = new ArrayList<SignalHandler>();
	
	/**
	 * 
	 */
	public SignalHandlerSet() {
	}
	
	

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SpikeHandler#spikeAt(int, int, int, net.sf.jaer.event.PolarityEvent.Polarity)
	 */
	@Override
	public void signalAt(int x, int y, int time, double value/*Polarity polarity*/) {
		
		for (SignalHandler sh : spikeHandlers) 
			sh.signalAt(x, y, time, value);
		
	}
	
	@Override
	public void reset() {
		for (SignalHandler sh : spikeHandlers) 
			sh.reset();
	}

	public boolean addSpikeHandler(SignalHandler handler) {
		if (!this.spikeHandlers.contains(handler)) {
			spikeHandlers.add(handler);
			return true;
		}
		else return false;
	}
	
	public boolean contains(SignalHandler handler) {
		return spikeHandlers.contains(handler);
	}

	public boolean removeSpikeHandler(SignalHandler handler) {
		if (this.spikeHandlers.contains(handler)) {
			spikeHandlers.remove(handler);
			return true;
		}
		else return false;
	}



}
