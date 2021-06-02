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
 * @author Dennis
 *
 */
public class LinearThresholdIF extends FiringModel {
	int refractoryTime = 70000; // refractory time of 70 us
	// frequency in Hz:
	float thresholdFrequency = 5f;
	float threshold = 1.0f;
	
	
	private float membranePotential = 0.0f;
	private int lastSpikeTime = 0;
	private int refractoredUntil = 0;
	private int lastIncreaseTime = 0;
	private boolean resetted = true;
	
	public static class Creator extends FiringModelCreator {
		private float threshold = 1.0f;
		private float thresholdFrequency = 5f;
		int refractoryTime = 70000;
		public Creator(Preferences prefs) {
			super("LinearThresholdIF", prefs);
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
			getSupport().firePropertyChange("threshold", before, this.threshold);
		}

		
		

		public float getThresholdFrequency() {
			return thresholdFrequency;
		}


		public void setThresholdFrequency(float thresholdFrequency) {
			float before = this.thresholdFrequency;
			this.thresholdFrequency = thresholdFrequency;
			getSupport().firePropertyChange("thresholdFrequency", before, this.thresholdFrequency);
		}

		

		public int getRefractoryTime() {
			return refractoryTime;
		}


		public void setRefractoryTime(int refractoryTime) {
			int before = this.refractoryTime;
			this.refractoryTime = refractoryTime;
			getSupport().firePropertyChange("refractoryTime", before, this.refractoryTime);
		}


		@Override
		public FiringModel createUnit(int x, int y, FiringModelMap map) {
			LinearThresholdIF linearThresholdIF = new LinearThresholdIF(x,y,map.getSignalHandler());
			linearThresholdIF.setThreshold(threshold);
			linearThresholdIF.setRefractoryTime(refractoryTime);
			linearThresholdIF.setThresholdFrequency(thresholdFrequency);
			return linearThresholdIF;
		}
	}

	/**
	 * @param x
	 * @param y
	 * @param handler
	 */
	public LinearThresholdIF(int x, int y, SignalHandler handler) {//FiringModelMap map) {
		super(x, y, handler);
	}

	
	
	public int getRefractoryTime() {
		return refractoryTime;
	}



	public void setRefractoryTime(int refractoryTime) {
		this.refractoryTime = refractoryTime;
	}



	public float getThresholdFrequency() {
		return thresholdFrequency;
	}



	public void setThresholdFrequency(float thresholdFrequency) {
		this.thresholdFrequency = thresholdFrequency;
	}

	public void setThreshold(float threshold) {
		this.threshold = threshold;
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#receiveSpike(double, int)
	 */
	@Override
	public void receiveSpike(double value, int timeInUs) {
		// this event happened before the last recorded one -> time most likely wrapped around
		if (timeInUs < lastIncreaseTime) {
			lastIncreaseTime = timeInUs;
			refractoredUntil = timeInUs;
			membranePotential = (float)value;
		}
		// normal processing
        if (timeInUs > refractoredUntil) { // Refractory period
        	if (lastIncreaseTime-timeInUs > 0)
        		membranePotential = (float)value;
        	else 
        		membranePotential += value - (((float)(timeInUs - lastIncreaseTime)*1e-6f) * thresholdFrequency);
        }
        else if (timeInUs < lastSpikeTime  || resetted)
        	membranePotential = 0.0f;
        // still inside refractory time. Avoid further processing: 
        else return;

    	resetted = false;
    	lastIncreaseTime = timeInUs;
    	if (membranePotential >= threshold) {
    		membranePotential = 0.0f;
    		lastSpikeTime = timeInUs;
    		refractoredUntil = timeInUs + refractoryTime;
    		emitSpike(1.0, timeInUs);
    	}
    	else if (membranePotential < 0)
			membranePotential = 0.0f;
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

	
	public static FiringModelCreator getCreator(Preferences prefs) {
		return new Creator(prefs);
	}

}
