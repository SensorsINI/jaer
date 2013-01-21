/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.Iterator;
import java.util.Random;

import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;

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
	
	private int multiplicator = 1 << 20;
	private final int intThreshold = 1 << 20;
	private int intTau = (int)(1.0/Math.log(2.0));
	private int intMembranePotential = 0;
	
	private final static int multValueShift = 14;
	private final static int multValueCounter = 1 << multValueShift;
	private final static int multValues[] = new int[multValueCounter];
	static {
		for (int i = 0; i < multValues.length; i++) {
			multValues[i] = (int)(Math.exp(-((double)i) / ((double)multValueCounter)) * (1 << 10));
		}
	}
	/**
	 * 
	 */
	public LeakyIntegrateAndFire(float tau, int refractoryTime, float threshold) {
		this.refractoryTime = refractoryTime;
		this.tau = tau;
		this.threshold = threshold;
		calculateIntValues();
	}
	
	protected void calculateIntValues() {
		intTau = (int)(1.0/Math.log(2.0)*tau);
		//intThreshold = 1 << 20;
		multiplicator = (int)((1 << 20) * (1.0 / threshold)); 
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
//	@Override
	public boolean receiveSpikeFloat(double value, int timeInUs) {
		// this event happened before the last recorded one -> time most likely wrapped around
		if (timeInUs < lastIncreaseTime) {
			lastIncreaseTime = timeInUs;
			membranePotential = (float)value;
		}
		// normal processing
        if (timeInUs > refractoredUntil) { // Refractory period
        	if (lastIncreaseTime-timeInUs > 0)
        		membranePotential = 0.0f;
        	else
        		membranePotential *= Math.exp(((float)(lastIncreaseTime - timeInUs)) / tau);
        	membranePotential += value;
        }
        else if (timeInUs < lastSpikeTime || resetted) 
        	membranePotential = (float)value;
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

//	@Override
	public boolean receiveSpike(double value, int timeInUs) {
		int intValue = (int)(value * multiplicator);
		// this event happened before the last recorded one -> time most likely wrapped around
		if (timeInUs < lastIncreaseTime) {
			lastIncreaseTime = timeInUs;
			intMembranePotential = intValue;
		}
		// normal processing
        if (timeInUs > refractoredUntil) { // Refractory period
        	if (lastIncreaseTime-timeInUs > 0)
        		intMembranePotential = 0;
        	else {
        		int diff = timeInUs - lastIncreaseTime;
				int reductions = diff / intTau;
        		intMembranePotential *= multValues[((diff - (reductions * intTau)) << multValueShift) / intTau];
        		intMembranePotential >>= (reductions+10);
        	}
// TODO        		intMembranePotential *= Math.exp(((float)(lastIncreaseTime - timeInUs)) / tau);
        	intMembranePotential += intValue;
        }
        else if (timeInUs < lastSpikeTime || resetted) 
        	intMembranePotential = intValue;
        // still inside refractory time. Avoid further processing: 
        else return false;

    	lastIncreaseTime = timeInUs;
    	resetted = false;
    	if (intMembranePotential > intThreshold) {
    		intMembranePotential = 0;
    		lastSpikeTime = timeInUs;
    		refractoredUntil = timeInUs + refractoryTime;
    		return true;
    	}
    	else if (intMembranePotential < 0)
    		intMembranePotential = 0;
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
		this.intMembranePotential = 0;
		this.resetted = true;
	}
	
	public static void main(String[] args) {
		LeakyIntegrateAndFire lif = new LeakyIntegrateAndFire(10000, 6000, 1.0f);
		Random r = new Random();
		int time = 0;
		long startTime = System.nanoTime();
		int counter = 0;
		for (int i = 0; i < 10000000; i++) {
			double d = r.nextDouble()/10.0;
			if (lif.receiveSpike(d, time) && (!lif.receiveSpikeFloat(d,time))) counter++;
			time += r.nextInt(500);
		}
		long endTime = System.nanoTime();
		System.out.println("Total time in ms: "+(endTime-startTime)/1000000);
		System.out.println("Spikes: "+counter);
	}

	

}
