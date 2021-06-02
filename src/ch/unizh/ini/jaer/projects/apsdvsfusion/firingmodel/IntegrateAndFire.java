/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel;

import java.util.prefs.Preferences;

import ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelCreator;
import ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SignalHandler;

/**
 * @author Dennis Goehlsdorf
 *
 */
public class IntegrateAndFire extends FiringModel {
	float sum = 0.0f;
	float threshold = 1.0f;
	
	public static class Creator extends FiringModelCreator {
		private float threshold = 1.0f;
		public Creator(Preferences prefs) {
			super("IntegrateAndFire", prefs);
		}
		/**
		 * 
		 */
		private static final long serialVersionUID = -9075663597897695761L;

		
		public float getThreshold() {
			return threshold;
		}


		public void setThreshold(float threshold) {
			float before = this.threshold;
			this.threshold = threshold;
			getSupport().firePropertyChange("threshold", before, threshold);
		}


		@Override
		public FiringModel createUnit(int x, int y, FiringModelMap map) {
			IntegrateAndFire integrateAndFire = new IntegrateAndFire(x,y,map.getSignalHandler());
			integrateAndFire.setThreshold(threshold);
			return integrateAndFire;
		}
	}
	/**
	 * 
	 */
//	public IntegrateAndFire(int x, int y, FiringModelMap map) {
	public IntegrateAndFire(int x, int y, SignalHandler handler) {
		super(x,y,handler);
		// TODO Auto-generated constructor stub
	}
	
	public static FiringModelCreator getCreator(Preferences prefs) {
		return new Creator(prefs);
	}


	
	public float getThreshold() {
		return threshold;
	}

	public void setThreshold(float threshold) {
		this.threshold = threshold;
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#receiveSpike(double)
	 */
	@Override
	public void receiveSpike(double value, int timeInUs) {
		sum+= value;
		if (sum > threshold) {
			sum = 0.0f;
			emitSpike(1.0, timeInUs);
		}
		else if (sum < 0.0f) {
			sum = 0.0f;
		}
	}

	@Override
	public void reset() {
		sum = 0.0f;
	}


}
