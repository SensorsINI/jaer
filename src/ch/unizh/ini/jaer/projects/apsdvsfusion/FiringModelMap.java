/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis Goehlsdorf
 *
 */
public abstract class FiringModelMap {
	int sizeX, sizeY;
	SpikeHandler spikeHandler;
	public FiringModelMap(int sizeX, int sizeY, SpikeHandler spikeHandler) {
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.spikeHandler = spikeHandler;
	}
	
 	public SpikeHandler getSpikeHandler() {
		return spikeHandler;
	}

	public void setSpikeHandler(SpikeHandler spikeHandler) {
		this.spikeHandler = spikeHandler;
	}

	public int getSizeX() {
		return sizeX;
	}
	public int getSizeY() {
		return sizeY;
	}
//	public int getOffsetX();
//	public int getOffsetY();
	
	public void changeSize(int sizeX, int sizeY) {
		this.sizeX = sizeX;
		this.sizeY = sizeY;
	}

	public abstract FiringModel get(int x, int y);
	public abstract void reset();
}
