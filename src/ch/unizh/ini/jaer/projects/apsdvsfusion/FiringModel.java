/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis Goehlsdorf
 *
 */
public interface FiringModel {
	public boolean receiveSpike(double value, int timeInUs);
	public void reset();
}
