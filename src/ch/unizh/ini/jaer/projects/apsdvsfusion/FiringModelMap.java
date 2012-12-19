/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis Goehlsdorf
 *
 */
public interface FiringModelMap {
	public FiringModel get(int x, int y);
	public int getSizeX();
	public int getSizeY();
	public int getOffsetX();
	public int getOffsetY();
	public void changeSize(int sizeX, int sizeY);
	public void reset();
}
