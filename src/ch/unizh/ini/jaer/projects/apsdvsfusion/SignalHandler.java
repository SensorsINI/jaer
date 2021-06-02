/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;



/**
 * @author Dennis
 *
 */
public interface SignalHandler {
	public void signalAt(int x, int y, int time, double value);
//	public void inputSizeChanged(int newWidth, int newHeight);
	public void reset();
}	
