/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

//import net.sf.jaer.event.PolarityEvent;

/**
 * @author Dennis
 *
 */
public interface SignalHandler {
	public void signalAt(int x, int y, int time, double value);
//	public void inputSizeChanged(int newWidth, int newHeight);
	public void reset();
}	
