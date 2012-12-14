/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import net.sf.jaer.chip.AEChip;

/**
 * @author Dennis Goehlsdorf
 *
 */
public class ArrayFiringModelMap implements FiringModelMap {
	FiringModelCreator fmc;
	int sizeX = 0, sizeY = 0;
	int offsetX = 0, offsetY = 0;
	FiringModel[][] map = null;
	
	public ArrayFiringModelMap(int sizeX, int sizeY, FiringModelCreator fmc) {
		this.fmc = fmc;
		changeSize(sizeX, sizeY);
	}
	
	public ArrayFiringModelMap(AEChip chip, FiringModelCreator fmc) {
		this(chip.getSizeX(), chip.getSizeY(), fmc);
	}
	
	public void changeSize(int sizeX, int sizeY) {
		if (sizeX != this.sizeX || sizeY != this.sizeY) {
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			map = new FiringModel[sizeX][sizeY];
			for (int x = 0; x < sizeX; x++) {
				for (int y = 0; y < sizeY; y++) {
					map[x][y] = fmc.createUnit(x, y);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap#get(int, int)
	 */
	@Override
	public FiringModel get(int x, int y) {
		if (x+offsetX >= 0 && x+offsetX < sizeX && y+offsetY >= 0 && y+offsetY < sizeY) 
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
	public int getSizeX() {
		return sizeX;
	}

	@Override
	public int getSizeY() {
		// TODO Auto-generated method stub
		return sizeY;
	}

}
