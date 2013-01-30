/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis
 *
 */
public class SchedulableWrapperMap extends SchedulableFiringModelMap implements
		FiringModelCreator {
	
	FiringModelMap map = null;
	SchedulableFiringModelCreator schedulableFiringModelCreator = null;
	
	/**
	 * @param sizeX
	 * @param sizeY
	 * @param spikeHandler
	 */
	public SchedulableWrapperMap(int sizeX, int sizeY, SpikeHandler spikeHandler) {
		super(sizeX, sizeY, spikeHandler);
		// TODO Auto-generated constructor stub
	}
	
	public SchedulableFiringModelCreator getFiringModelMapCreator() {
		return schedulableFiringModelCreator;
	}
	
	@Override
	public synchronized void setFiringModelCreator(FiringModelCreator creator) {
		this.schedulableFiringModelCreator = null;
		super.setFiringModelCreator(creator);
		if (map != null) {
			map.buildUnits();
		}
	}
	
	public synchronized void setFiringModelCreator(SchedulableFiringModelCreator creator) {
		this.schedulableFiringModelCreator = creator;
		super.setFiringModelCreator(null);
		if (map != null) {
			map.buildUnits();
		}
	}
	
	public void setFiringModelMap(FiringModelMap map) {
		this.map = map;
		if (map != null) {
			map.setFiringModelCreator(this);
			map.changeSize(sizeX, sizeY);
			map.buildUnits();
		}
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelCreator#createUnit(int, int, ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap)
	 */
	@Override
	public FiringModel createUnit(int x, int y, FiringModelMap map) {
		if (schedulableFiringModelCreator != null)
			return schedulableFiringModelCreator.createUnit(x, y, this);
		else if (getFiringModelCreator() != null)
			return getFiringModelCreator().createUnit(x, y, map);
		else
			return null;
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelMap#get(int, int)
	 */
	@Override
	public FiringModel get(int x, int y) {
		if (map != null) {
			return map.get(x,y);
		}
		else
			return null;
	}
	
	@Override
	public synchronized void changeSize(int sizeX, int sizeY) {
		if (sizeX != this.sizeX || sizeY != this.sizeY) {
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			if (map != null)
				map.changeSize(sizeX, sizeY);
		}
	}

	@Override
	public void buildUnits() {
		if (map != null)
			map.buildUnits();
	}

	@Override
	public void reset() {
		if (map != null)
			map.reset();
		
	}
}
