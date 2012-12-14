/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import net.sf.jaer.event.PolarityEvent;

/**
 * @author Dennis
 *
 */
public abstract class KernelProcessor implements SpikeHandler {

	boolean enabled = true;
	/**
	 * 
	 */
	public KernelProcessor() {
	}

	protected abstract void processSpike(int x, int y, int time, PolarityEvent.Polarity polarity);
	
	public void spikeAt(int x, int y, int time, PolarityEvent.Polarity polarity) {
		if (enabled) {
			processSpike(x,y,time, polarity);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
