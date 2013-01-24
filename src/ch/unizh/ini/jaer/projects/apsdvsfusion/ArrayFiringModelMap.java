/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import net.sf.jaer.chip.AEChip;

/**
 * @author Dennis Goehlsdorf
 *
 */
public class ArrayFiringModelMap extends FiringModelMap {
//	FiringModelCreator fmc;
//	int sizeX = 0, sizeY = 0;
//	int offsetX = 0, offsetY = 0;
//	public int getOffsetY() {
//		return offsetY;
//	}
//
//	public void setOffsetY(int offsetY) {
//		this.offsetY = offsetY;
//	}
//
//	public int getOffsetX() {
//		return offsetX;
//	}
//
//	public void setOffsetX(int offsetX) {
//		this.offsetX = offsetX;
//	}

	FiringModel[][] map = null;
	
	public ArrayFiringModelMap(int sizeX, int sizeY, SpikeHandler spikeHandler) {
		super(sizeX, sizeY, spikeHandler);
	//	this.fmc = fmc;
	//	changeSize(sizeX, sizeY);
//		this.offsetX = offsetX;
//		this.offsetY = offsetY;
	}
	
	public ArrayFiringModelMap(AEChip chip, SpikeHandler spikeHandler/*, FiringModelCreator fmc*/) {
		this(chip.getSizeX(), chip.getSizeY(), spikeHandler/*, fmc*/);
	}
	
//	public synchronized void changeSize(int sizeX, int sizeY) {
//		if (sizeX != this.sizeX || sizeY != this.sizeY) {
//			super.changeSize(sizeX, sizeY);
////			this.sizeX = sizeX;
////			this.sizeY = sizeY;
//		}
//	}
	
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap#get(int, int)
	 */
	@Override
	public FiringModel get(int x, int y) {
//		if (x+offsetX >= 0 && x+offsetX < sizeX && y+offsetY >= 0 && y+offsetY < sizeY) 
//			return map[x+offsetX][y+offsetY];
		if (x >= 0 && x < sizeX && y >= 0 && y < sizeY) 
			return map[x][y];
		return null;
	}

	@Override
	public void reset() {
		for (int x = 0; x < sizeX; x++) {
			for (int y = 0; y < sizeY; y++) {
				map[x][y].reset();
			}
		}
	}

	@Override
	public void buildUnits() {
		map = new FiringModel[sizeX][sizeY];
		if (getFiringModelCreator() != null) {
			for (int x = 0; x < sizeX; x++) {
				for (int y = 0; y < sizeY; y++) {
					map[x][y] = getFiringModelCreator().createUnit(x, y, this);
				}
			}
		}
	}

//	@Override
//	public int getSizeX() {
//		return sizeX;
//	}
//
//	@Override
//	public int getSizeY() {
//		// TODO Auto-generated method stub
//		return sizeY;
//	}

}
