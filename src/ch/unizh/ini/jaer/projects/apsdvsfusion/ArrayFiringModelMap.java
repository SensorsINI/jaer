/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.prefs.Preferences;

import net.sf.jaer.chip.AEChip;

/**
 * A simple implementation of {@link FiringModelMap} that uses a 2D array to store a receptive field. 
 *
 * @author Dennis Goehlsdorf
 *
 */
public class ArrayFiringModelMap extends FiringModelMap {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3373722547563031984L;

	FiringModel[][] map = null;
	
	/** 
	 * Creates a simple 2D receptive field.
	 * @param sizeX
	 * @param sizeY
	 * @param spikeHandler An instance of {@link SignalHandler} that should be informed about spikes in this
	 * receptive field.
	 * @param prefs The Preference Node used to store the settings of this FiringModelMap
	 */
	public ArrayFiringModelMap(int sizeX, int sizeY, SignalHandler spikeHandler, Preferences prefs) {
		super(sizeX, sizeY, spikeHandler, prefs);
	}

	/**
	 * Convenience constructor. Extracts the field size from the size of a given {@link AEChip}. 
	 * @param chip 
	 * @param spikeHandler
	 * @param prefs
	 * @see ArrayFiringModelMap#ArrayFiringModelMap(int, int, SignalHandler, Preferences)
	 */
	public ArrayFiringModelMap(AEChip chip, SignalHandler spikeHandler/*, FiringModelCreator fmc*/, Preferences prefs) {
		this(chip.getSizeX(), chip.getSizeY(), spikeHandler/*, fmc*/, prefs);
	}

	@Override
	public void signalAt(int x, int y, double value, int timeInUs) {
		if (enabled && x >= 0 && x < sizeX && y >= 0 && y < sizeY)
			map[x][y].receiveSpike(value, timeInUs);
	}

	
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap#get(int, int)
	 */
	@Override
	public FiringModel get(int x, int y) {
//		if (x+offsetX >= 0 && x+offsetX < sizeX && y+offsetY >= 0 && y+offsetY < sizeY) 
//			return map[x+offsetX][y+offsetY];
		if (enabled && x >= 0 && x < sizeX && y >= 0 && y < sizeY) 
			return map[x][y];
		return null;
	}

	@Override
	public void reset() {
		super.reset();
		for (int x = 0; x < sizeX; x++) {
			for (int y = 0; y < sizeY; y++) {
				if (map[x][y] != null)
					map[x][y].reset();
			}
		}
	}

	@Override
	public void buildUnits() {
		FiringModel[][] newMap = new FiringModel[sizeX][sizeY];
		if (getFiringModelCreator() != null) {
			for (int x = 0; x < sizeX; x++) {
				for (int y = 0; y < sizeY; y++) {
					newMap[x][y] = getFiringModelCreator().createUnit(x, y, this);
				}
			}
		}
		map = newMap;
	}


}
