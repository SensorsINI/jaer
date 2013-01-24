/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import net.sf.jaer.event.PolarityEvent;

/**
 * @author Dennis Goehlsdorf
 *
 */
public interface InputKernel {
	public void apply(int x, int y, int time, PolarityEvent.Polarity polarity, FiringModelMap map, SpikeHandler spikeHandler);
//	public void addOffset(int x, int y);
	public int getOffsetX();
	public int getOffsetY();
	public void setOffsetX(int offsetX);
	public void setOffsetY(int offsetY);
}
