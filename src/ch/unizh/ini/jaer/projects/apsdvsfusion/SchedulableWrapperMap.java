/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.prefs.Preferences;

/**
 * @author Dennis
 *
 */
public class SchedulableWrapperMap extends SchedulableFiringModelMap /*implements
		FiringModelCreator */{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3135959582771085761L;

	public class SchedulableWrapperFiringModelCreator extends FiringModelCreator {

		/**
		 * 
		 */
		private static final long serialVersionUID = -3674544035563757031L;

		SchedulableWrapperFiringModelCreator(Preferences parentPrefs, String nodeName) {
			super("SchedulableWrapperFiringModel", parentPrefs, nodeName);
		}


		@Override
		public FiringModel createUnit(int x, int y, FiringModelMap map) {
			if (schedulableFiringModelCreator != null)
				return schedulableFiringModelCreator.createUnit(x, y, SchedulableWrapperMap.this);
			else if (getFiringModelCreator() != null)
				return getFiringModelCreator().createUnit(x, y, map);
			else
				return null;
		}
		
	}
	
	FiringModelMap map = null;
	SchedulableFiringModelCreator schedulableFiringModelCreator = null;
	SchedulableWrapperFiringModelCreator myCreatorProxy = new SchedulableWrapperFiringModelCreator(getPrefs(), "creatorProxy");
	
	/**
	 * @param sizeX
	 * @param sizeY
	 * @param spikeHandler
	 */
	public SchedulableWrapperMap(int sizeX, int sizeY, SpikeHandler spikeHandler, Preferences parentPrefs, String nodeName) {
		super(sizeX, sizeY, spikeHandler, parentPrefs, nodeName);
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
			map.setFiringModelCreator(myCreatorProxy);
			map.changeSize(sizeX, sizeY);
			map.buildUnits();
		}
	}

//	/* (non-Javadoc)
//	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelCreator#createUnit(int, int, ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap)
//	 */
//	@Override
//	public FiringModel createUnit(int x, int y, FiringModelMap map) {
//	}

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
