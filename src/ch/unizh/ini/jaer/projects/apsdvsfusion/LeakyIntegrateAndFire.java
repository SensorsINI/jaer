/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis Goehlsdorf
 *
 */
public class LeakyIntegrateAndFire implements FiringModel {
	float threshold = 1.0f;
	float tau = 1.0f;    
	int refractoryTime = 70000; // refractory time of 70 us

	private float membranePotential = 0.0f;
	private int lastSpikeTime = 0;
	private int lastIncreaseTime = 0;
	
	/**
	 * 
	 */
	public LeakyIntegrateAndFire(float tau, int refractoryTime, float threshold) {
		this.refractoryTime = refractoryTime;
		this.tau = tau;
		this.threshold = threshold;
	}

	public static FiringModelCreator getCreator(final float tau, final int refractoryTime, final float threshold) {
		return new FiringModelCreator() {
			public FiringModel createUnit(int x, int y) {
				return new LeakyIntegrateAndFire(tau, refractoryTime, threshold);
			}
		};
	}
	
	
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#receiveSpike(double, int)
	 */
	@Override
	public boolean receiveSpike(double value, int timeInUs) {
		if (timeInUs < lastIncreaseTime) {
			lastIncreaseTime = timeInUs;
			membranePotential = (float)value;
		}
        if (timeInUs > lastSpikeTime) { // Refractory period
        	membranePotential *= Math.exp(((float)(lastIncreaseTime - timeInUs)) / tau);
        	membranePotential += value;
        	lastIncreaseTime = timeInUs;
        	if (membranePotential > threshold) {
        		membranePotential = 0.0f;
        		lastSpikeTime = timeInUs + refractoryTime;
        		return true;
        	}
        	else if (membranePotential < 0.0f)
        		membranePotential = 0.0f;
        }
        // time wrapped around...
        else if (timeInUs + Integer.MAX_VALUE/2 < lastSpikeTime) {
        	//membranePotential *= Math.exp(((float)(lastIncreaseTime - timeInUs)) / tau);
        	membranePotential = (float)value;
        	lastIncreaseTime = timeInUs;
        	if (membranePotential > threshold) {
        		membranePotential = 0.0f;
        		lastSpikeTime = timeInUs + refractoryTime;
        		return true;
        	}
        	else if (membranePotential < 0.0f)
        		membranePotential = 0.0f;
        }
		return false;
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#reset()
	 */
	@Override
	public void reset() {
		this.lastSpikeTime = 0;
		this.membranePotential = 0.0f;
		this.lastIncreaseTime = 0;
	}

}
