/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis Goehlsdorf
 *
 */
public abstract class FiringModelMap {
	int sizeX = -1, sizeY = -1;
	FiringModelCreator firingModelCreator = null;
	SpikeHandler spikeHandler;
	public FiringModelMap(int sizeX, int sizeY, SpikeHandler spikeHandler) {
		this.spikeHandler = spikeHandler;
		changeSize(sizeX, sizeY);
	}
	
	public FiringModelCreator getFiringModelCreator() {
		return firingModelCreator;
	}

	public void setFiringModelCreator(FiringModelCreator firingModelCreator) {
		this.firingModelCreator = firingModelCreator;
		buildUnits();
	}
	
	public abstract void buildUnits(); 

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
	
	public synchronized void changeSize(int sizeX, int sizeY) {
		if (sizeX != this.sizeX || sizeY != this.sizeY) {
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			buildUnits();
		}
	}

	public abstract FiringModel get(int x, int y);
	public abstract void reset();
}
