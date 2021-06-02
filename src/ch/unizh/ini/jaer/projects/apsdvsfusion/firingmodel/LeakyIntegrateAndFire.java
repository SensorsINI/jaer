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
public class LeakyIntegrateAndFire extends FiringModel {
	float threshold = 1.0f;
	float tau = 1.0f;    
	int refractoryTime = 70000; // refractory time of 70 us

	private float membranePotential = 0.0f;
	private int lastSpikeTime = 0;
	private int refractoredUntil = 0;
	private int lastIncreaseTime = 0;
	private boolean resetted = true;

	IntegerDecayModel expDecay = new IntegerDecayModel();
//	private final static int shifter = 20;
//	private final static int multValueDivisorShift = 10;	
//	private final static int multValueCounterShift = 14;
//
//	private int multiplicator = 1 << shifter;
//	private final int intThreshold = 1 << shifter;
//	private int intTau = (int)(1.0/Math.log(2.0));
//	private int intMembranePotential = 0;
//	
//	private final static int multValueCounter = 1 << multValueCounterShift;
//	private final static int multValues[] = new int[multValueCounter];
//	static {
//		for (int i = 0; i < multValues.length; i++) {
//			multValues[i] = (int)(Math.pow(2.0,((double)(-(i))) / ((double)multValueCounter)) * ((double)(1 << multValueDivisorShift)));
//		}
//	}
	/**
	 * 
	 */
	public LeakyIntegrateAndFire(int x, int y, float tau, int refractoryTime, float threshold, SignalHandler handler) {
//	public LeakyIntegrateAndFire(int x, int y, float tau, int refractoryTime, float threshold, FiringModelMap map) {
		super(x,y,handler);
		this.refractoryTime = refractoryTime;
		this.tau = tau;
		this.threshold = threshold;
		calculateIntValues();
	}

	public synchronized float getThreshold() {
		return threshold;
	}

	public synchronized void setThreshold(float threshold) {
		this.threshold = threshold;
	}

	public synchronized float getTau() {
		return tau;
	}

	public synchronized void setTau(float tau) {
		this.tau = tau;
	}

	public synchronized int getRefractoryTime() {
		return refractoryTime;
	}

	public synchronized void setRefractoryTime(int refractoryTime) {
		this.refractoryTime = refractoryTime;
	}

	protected void calculateIntValues() {
		expDecay.setTimeConstant(tau);
		expDecay.setMultiplicator(1.0f / threshold);
//		intTau = (int)(Math.log(2.0)*tau);
//		//intThreshold = 1 << 20;
//		multiplicator = (int)((1 << shifter) * (1.0 / threshold)); 
	}

	public static class Creator extends FiringModelCreator {
		float threshold;
		float tau;    
		int refractoryTime; // refractory time of 70 us

		/**
		 * 
		 */
		private static final long serialVersionUID = -4257683486443778516L;

		public Creator(float threshold, float tau, int refractoryTime, Preferences prefs) {
			super("LeakyIntegrateAndFireCreator", prefs);
			this.threshold = threshold;
			this.tau = tau;
			this.refractoryTime = refractoryTime;
		}
		
		@Override
		public FiringModel createUnit(int x, int y, FiringModelMap map) {
			return new LeakyIntegrateAndFire(x,y,tau, refractoryTime, threshold,map.getSignalHandler());
		}

		public float getThreshold() {
			return threshold;
		}

		public void setThreshold(float threshold) {
			float before = this.threshold;
			this.threshold = threshold;
			getSupport().firePropertyChange("threshold", before, threshold);
		}

		public float getTau() {
			return tau;
		}

		public void setTau(float tau) {
			float before = this.tau;
			this.tau = tau;
			getSupport().firePropertyChange("tau", before, tau);
		}

		public int getRefractoryTime() {
			return refractoryTime;
		}

		public void setRefractoryTime(int refractoryTime) {
			int before = this.refractoryTime;
			this.refractoryTime = refractoryTime;
			getSupport().firePropertyChange("refractoryTime", before, refractoryTime);
		}
		
	}
	
	@SuppressWarnings("unused")
	public static FiringModelCreator getCreator(final float tau, final int refractoryTime, final float threshold, Preferences prefs) {
		return new Creator(threshold, tau, refractoryTime, prefs);
	}
	
	public static FiringModelCreator getCreator(Preferences prefs) {
		return getCreator(36000, 7000,1.5f, prefs);
	}
	
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#receiveSpike(double, int)
	 */
//	@Override
	public void receiveSpikeFloat(double value, int timeInUs) {
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
        	return;

    	lastIncreaseTime = timeInUs;
    	resetted = false;
    	if (membranePotential > threshold) {
    		membranePotential = 0.0f;
    		lastSpikeTime = timeInUs;
    		refractoredUntil = timeInUs + refractoryTime;
    		emitSpike(1.0, timeInUs);
    	}
    	else if (membranePotential < 0.0f)
    		membranePotential = 0.0f;
	}

	@Override
	public final void receiveSpike(double value, int timeInUs) {
//		final int intValue = (int)(value * multiplicator);
		// this event happened before the last recorded one -> time most likely wrapped around
		if (timeInUs < lastIncreaseTime) {
			lastIncreaseTime = timeInUs;
			refractoredUntil = timeInUs;
			expDecay.setValue(value);
//			intMembranePotential = intValue;
		}
		// normal processing
        if (timeInUs > refractoredUntil) { // Refractory period
        	if (lastIncreaseTime-timeInUs > 0)
        		expDecay.setIntValue(0);
//        		intMembranePotential = 0;
        	else {
        		expDecay.decay(timeInUs - lastIncreaseTime);
//        		final int diff = timeInUs - lastIncreaseTime;
//				final int reductions = diff / intTau;
////        		intMembranePotential *= multValues[(int)(((long)(diff - (reductions * intTau))) << multValueCounterShift) / intTau];
////        		intMembranePotential *= multValues[8000];
//        		intMembranePotential *= multValues[(int)(((diff - (reductions * intTau))) << multValueCounterShift) / intTau];
//        		intMembranePotential >>= (reductions+multValueDivisorShift);
        	}
        	expDecay.add(value);
//        	intMembranePotential += intValue;
        }
        else if (timeInUs < lastSpikeTime || resetted)
        	expDecay.setValue(value);
        // still inside refractory time. Avoid further processing: 
        else return;

    	lastIncreaseTime = timeInUs;
    	resetted = false;
    	if (expDecay.getIntValue() > IntegerDecayModel.ONE) {
    		expDecay.setIntValue(0);
//    		intMembranePotential = 0;
    		lastSpikeTime = timeInUs;
    		refractoredUntil = timeInUs + refractoryTime;
    		emitSpike(1.0, timeInUs);
    	}
    	else if (expDecay.getIntValue() < 0)
    		expDecay.setIntValue(0);
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
		this.expDecay.setIntValue(0);
		this.resetted = true;
	}
	
	public static void main(String[] args) {
		SignalHandler spikeHandler = new SignalHandler() {
			@Override
			public void signalAt(int x, int y, int time, double value) {
			}
			@Override
			public void reset() {
			}
		};
		FiringModelMap map = new FiringModelMap(1,1,spikeHandler, null) {
			@Override
			public FiringModel get(int x, int y) {
				return null;
			}
			@Override
			public void buildUnits() {
			}
		};
		LeakyIntegrateAndFire lifA = new LeakyIntegrateAndFire(0,0,10000, 6, 1.0f,map.getSignalHandler());
//		LeakyIntegrateAndFire lifB = new LeakyIntegrateAndFire(0,0,10000, 6, 1.0f,map);
//		Random r = new Random(0);
		int time = 0;
		long startTime = System.nanoTime();
//		double a = 0.0;
//		int a = 0;
//		for (int i = 0; i < 1000000000; i++) {
////			a += 2.3;
//			a += 2;
////			printf("hm\n\r");
//		}
		int counter = 0;
		for (int i = 0; i < 100000000; i++) {
//			double d = r.nextDouble()/2.0;
//			if (lifA.receiveSpike(d, time)) 
//				counter++;
//			time += r.nextInt(lifA.intTau * 2);
			double d = 0.5;
			
			lifA.receiveSpike(d, time); 

			time += lifA.expDecay.getIntTimeConstant() / 2;
		}
		long endTime = System.nanoTime();
		System.out.println("Total time in ms: "+(endTime-startTime)/1000000);
		System.out.println("Spikes: "+counter);
	}

	

}
