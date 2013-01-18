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
	private int refractoredUntil = 0;
	private int lastIncreaseTime = 0;
	private boolean resetted = true;
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
		// this event happened before the last recorded one -> time most likely wrapped around
 		if (timeInUs < lastIncreaseTime) {
			lastIncreaseTime = timeInUs;
			membranePotential = (float)value;
		}
		// normal processing
        if (timeInUs > refractoredUntil) { // Refractory period
        	if (lastIncreaseTime-timeInUs > 0)
        		membranePotential = 0.0f;
        	else {
        		membranePotential *= Math.exp(((float)(lastIncreaseTime - timeInUs)) / tau);
//        		if (membranePotential == Float.NaN) {
//        			membranePotential = 0.0f;
//        		}
        	}
        	membranePotential += value;
        }
        else if (timeInUs < lastSpikeTime || resetted) {
        	lastSpikeTime = timeInUs;
        	refractoredUntil = timeInUs;
        	membranePotential = (float)value;
        }
        // still inside refractory time. Avoid further processing: 
        else return false;

    	lastIncreaseTime = timeInUs;
    	resetted = false;
    	if (membranePotential > threshold) {
    		membranePotential = 0.0f;
    		lastSpikeTime = timeInUs;
    		refractoredUntil = timeInUs + refractoryTime;
    		return true;
    	}
    	else if (membranePotential < 0.0f)
    		membranePotential = 0.0f;
   		return false;
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#reset()
	 */
	@Override
	public void reset() {
		this.lastSpikeTime = Integer.MIN_VALUE;
		this.membranePotential = 0.0f;
		this.lastIncreaseTime = Integer.MIN_VALUE;
		this.refractoredUntil = Integer.MIN_VALUE;
		this.resetted = true;
	}

}
