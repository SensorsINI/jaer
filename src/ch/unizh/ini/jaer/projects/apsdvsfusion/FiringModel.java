/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 * @author Dennis Goehlsdorf
 *
 */
public abstract class FiringModel {
	int x, y;
	FiringModelMap firingModelMap;

	public FiringModelMap getFiringModelMap() {
		return firingModelMap;
	}
	public void setFiringModelMap(FiringModelMap firingModelMap) {
		this.firingModelMap = firingModelMap;
	}
	public FiringModel(int x, int y, FiringModelMap map) {
		this.x = x;
		this.y = y;
		this.firingModelMap = map;
	}
	public void setCoordinate(int x, int y) {
		this.x = x;
		this.y = y;
	}
	public int getX() {
		return x;
	}
	public void setX(int x) {
		this.x = x;
	}
	public int getY() {
		return y;
	}
	public void setY(int y) {
		this.y = y;
	}

	public void emitSpike(double value, int timeInUs) {
		firingModelMap.getSpikeHandler().spikeAt(x, y, timeInUs, Polarity.On);
	}
	
	public abstract void receiveSpike(double value, int timeInUs);
	public abstract void reset();
}
