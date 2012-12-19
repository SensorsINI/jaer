/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis Goehlsdorf
 *
 */
public class IntegrateAndFire implements FiringModel {
	float sum = 0.0f;
	float threshold = 1.0f;
	/**
	 * 
	 */
	public IntegrateAndFire() {
		// TODO Auto-generated constructor stub
	}
	
	public static FiringModelCreator getCreator() {
		return new FiringModelCreator() {
			public FiringModel createUnit(int x, int y) {
				return new IntegrateAndFire();
			}
		};
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#receiveSpike(double)
	 */
	@Override
	public boolean receiveSpike(double value, int timeInMs) {
		sum+= value;
		if (sum > threshold) {
			sum = 0.0f;
			return true;
		}
		else if (sum < 0.0f) {
			sum = 0.0f;
		}
		return false;
	}

	@Override
	public void reset() {
		sum = 0.0f;
	}

}
