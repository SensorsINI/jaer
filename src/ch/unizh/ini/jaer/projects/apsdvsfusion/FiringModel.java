/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;


/**
 * Abstract class defining a single unit that can receive and fire signals. 
 * 
 * @author Dennis Goehlsdorf
 *
 */
public abstract class FiringModel {
	
	
	/**
	 * The coordinate of this unit.
	 */
	int x, y;

	/**
	 * Defines who should handle outgoing signals.
	 */
	SignalHandler signalHandler;

	public FiringModel(int x, int y, SignalHandler handler) { //FiringModelMap map) {
		this.x = x;
		this.y = y;
		setSignalHandler(handler);
	}

	/**
	 * Defines who should handle outgoing signals.
	 * @param handler
	 */
	public void setSignalHandler(SignalHandler handler) {
		this.signalHandler = handler;
	}
	
	/**
	 * Sets the coordinate of this unit.
	 * @param x
	 * @param y
	 */
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

	/**
	 * Emits a spike at a defined time with defined value.
	 * @param value
	 * @param timeInUs
	 */
	protected void emitSpike(double value, int timeInUs) {
		signalHandler.signalAt(x, y, timeInUs, value);
	}
	
	/**
	 * This function will be called whenever this unit receives an input from another unit.
	 * @param value
	 * @param timeInUs
	 */
	public abstract void receiveSpike(double value, int timeInUs);
	
	/**
	 * Will be called whenever the unit should be resetted to base values.
	 * (For example, reset any potential that was summed over time.)
	 */
	public abstract void reset();
}
