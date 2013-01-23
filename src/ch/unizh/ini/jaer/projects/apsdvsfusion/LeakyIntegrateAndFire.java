/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.Iterator;
import java.util.Random;

import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 * @author Dennis Goehlsdorf
 *
 */
public class LeakyIntegrateAndFire extends FiringModel {
	float threshold = 1.0f;
	float tau = 1.0f;    
	int refractoryTime = 70000; // refractory time of 70 us

	private float membranePotential = 0.0f;
	private int lastSpikeTime = 0;
	private int refractoredUntil = 0;
	private int lastIncreaseTime = 0;
	private boolean resetted = true;
	
	private final static int shifter = 20;
	private final static int multValueDivisorShift = 10;	
	private final static int multValueCounterShift = 14;

	private int multiplicator = 1 << shifter;
	private final int intThreshold = 1 << shifter;
	private int intTau = (int)(1.0/Math.log(2.0));
	private int intMembranePotential = 0;
	
	private final static int multValueCounter = 1 << multValueCounterShift;
	private final static int multValues[] = new int[multValueCounter];
	static {
		for (int i = 0; i < multValues.length; i++) {
			multValues[i] = (int)(Math.pow(2.0,((double)(-(i))) / ((double)multValueCounter)) * ((double)(1 << multValueDivisorShift)));
		}
	}
	/**
	 * 
	 */
	public LeakyIntegrateAndFire(int x, int y, float tau, int refractoryTime, float threshold, FiringModelMap map) {
		super(x,y,map);
		this.refractoryTime = refractoryTime;
		this.tau = tau;
		this.threshold = threshold;
		calculateIntValues();
	}
	
	protected void calculateIntValues() {
		intTau = (int)(Math.log(2.0)*tau);
		//intThreshold = 1 << 20;
		multiplicator = (int)((1 << shifter) * (1.0 / threshold)); 
	}

	public static FiringModelCreator getCreator(final float tau, final int refractoryTime, final float threshold) {
		return new FiringModelCreator() {
			@Override
			public FiringModel createUnit(int x, int y, FiringModelMap map) {
				return new LeakyIntegrateAndFire(x,y,tau, refractoryTime, threshold,map);
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
			refractoredUntil = timeInUs;
			membranePotential = (float)value;
		}
		// normal processing
        if (timeInUs > refractoredUntil) { // Refractory period
        	if (lastIncreaseTime-timeInUs > 0)
        		membranePotential = 0.0f;
        	else
        		membranePotential *= Math.exp(((float)(lastIncreaseTime - timeInUs)) / tau);
//        	float dummy = (float)Math.exp((lastIncreaseTime - timeInUs)/(tau*2.0));
        	membranePotential += value;
        }
        else if (timeInUs < lastSpikeTime || resetted) 
        	membranePotential = (float)value;
        // still inside refractory time. Avoid further processing: 
        else 
        	return false;

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
	public final void receiveSpike(double value, int timeInUs) {
		final int intValue = (int)(value * multiplicator);
		// this event happened before the last recorded one -> time most likely wrapped around
		if (timeInUs < lastIncreaseTime) {
			lastIncreaseTime = timeInUs;
			refractoredUntil = timeInUs;
			intMembranePotential = intValue;
		}
		// normal processing
        if (timeInUs > refractoredUntil) { // Refractory period
        	if (lastIncreaseTime-timeInUs > 0)
        		intMembranePotential = 0;
        	else {
        		final int diff = timeInUs - lastIncreaseTime;
				final int reductions = diff / intTau;
//        		intMembranePotential *= multValues[(int)(((long)(diff - (reductions * intTau))) << multValueCounterShift) / intTau];
//        		intMembranePotential *= multValues[8000];
        		intMembranePotential *= multValues[(int)(((diff - (reductions * intTau))) << multValueCounterShift) / intTau];
        		intMembranePotential >>= (reductions+multValueDivisorShift);
        		
        	}
        	intMembranePotential += intValue;
        }
        else if (timeInUs < lastSpikeTime || resetted) 
        	intMembranePotential = intValue;
        // still inside refractory time. Avoid further processing: 
        else return;

    	lastIncreaseTime = timeInUs;
    	resetted = false;
    	if (intMembranePotential > intThreshold) {
    		intMembranePotential = 0;
    		lastSpikeTime = timeInUs;
    		refractoredUntil = timeInUs + refractoryTime;
    		emitSpike(1.0, timeInUs);
    	}
    	else if (intMembranePotential < 0)
    		intMembranePotential = 0;
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
		SpikeHandler spikeHandler = new SpikeHandler() {
			@Override
			public void spikeAt(int x, int y, int time, Polarity polarity) {
			}
			@Override
			public void reset() {
			}
		};
		FiringModelMap map = new FiringModelMap(1,1,spikeHandler) {
			@Override 
			public void reset() {
			}
			@Override
			public FiringModel get(int x, int y) {
				return null;
			}
		};
		LeakyIntegrateAndFire lifA = new LeakyIntegrateAndFire(0,0,10000, 6, 1.0f,map);
		LeakyIntegrateAndFire lifB = new LeakyIntegrateAndFire(0,0,10000, 6, 1.0f,map);
		Random r = new Random(0);
		int time = 0;
		long startTime = System.nanoTime();
		int counter = 0;
		for (int i = 0; i < 100000000; i++) {
//			double d = r.nextDouble()/2.0;
//			if (lifA.receiveSpike(d, time)) 
//				counter++;
//			time += r.nextInt(lifA.intTau * 2);
			double d = 0.5;
			
			lifA.receiveSpike(d, time); 

			time += lifA.intTau / 2;
		}
		long endTime = System.nanoTime();
		System.out.println("Total time in ms: "+(endTime-startTime)/1000000);
		System.out.println("Spikes: "+counter);
	}

	

}
