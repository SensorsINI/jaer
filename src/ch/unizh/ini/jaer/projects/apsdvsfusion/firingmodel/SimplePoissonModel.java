/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel;

import java.util.Random;
import java.util.prefs.Preferences;

import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelCreator;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelMap;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SignalHandler;

/**
 * @author Dennis
 *
 */
public class SimplePoissonModel extends SchedulableFiringModel {
	IntegerDecayModel logPotential = new IntegerDecayModel();
//	// stores the difference between the last and the new potential  
//	private float changeFactor = 1.0f;
	int lastSpikeSchedulingTime = -1;
	int lastTime = Integer.MIN_VALUE;
	int nextSpikeTime = Integer.MIN_VALUE;
	
	private float nextSpikeValue = 1.0f;
	// at the maximum logPotential of 2.0, the spike rate should be 100 Hz: 
	float lambda = 1.0f/40000f;
	float timeConstant = 0.0f;
	
	boolean negativeSpikesOn = false;
	Random random = new Random();
	/**
	 * @param x
	 * @param y
	 * @param map
	 */
	public SimplePoissonModel(int x, int y, SignalHandler handler, SchedulableFiringModelMap map) {
		super(x, y, handler, map);
		// timeconstant = 1s
		logPotential.setTimeConstant(1e7f);
		// We never want to exceed IntegerDecayModel.ONE. Since we want to max out at +- 2.0, the maximum number
		// we might ask is +- 4.0. In the worst case, an intermediate value would then be +- 6.0.
		// I set the multiplicator to 1/8 to make sure +-6.0 is in the Integer range.
		logPotential.setMultiplicator(0.125f);
	}
	
	public synchronized float getLambda() {
		return lambda;
	}

	public synchronized void setLambda(float lambda) {
		this.lambda = lambda;
	}

	public void setTimeConstant(float timeConstant) {
		this.timeConstant = timeConstant;
		logPotential.setTimeConstant(timeConstant);
	}
	
	public float getTimeConstant() {
		return logPotential.getTimeConstant();
	}

	private void updateToTime(int time) {
		if (time > lastTime) {
			int timePassed = time - lastTime;
			if (timePassed > 0)
				logPotential.decay(timePassed);
			else
				logPotential.setIntValue(0);
		}
		else
			lastSpikeSchedulingTime = Integer.MIN_VALUE;
		lastTime = time;
	}
	

	private void scheduleNextSpike(int time) {
		if (logPotential.getIntValue() > 0) {
			int newIntervall = (int)(-Math.log(random.nextDouble()) / (lambda * logPotential.getValue()));
			lastSpikeSchedulingTime = newIntervall;
			nextSpikeTime= time+lastSpikeSchedulingTime;
			nextSpikeValue = 1.0f;
			scheduleEvent(nextSpikeTime);
		}
		else if (negativeSpikesOn && logPotential.getIntValue() < 0) {
			int newIntervall = (int)(-Math.log(random.nextDouble()) / (lambda * -logPotential.getValue()));
			lastSpikeSchedulingTime = newIntervall;
			nextSpikeTime= time+lastSpikeSchedulingTime;
			nextSpikeValue = -1.0f;
			scheduleEvent(nextSpikeTime);
		}
		else unschedule();
//			// if there was a spike scheduled before (meaning this function call was triggered by a potential change),
//			// take the time it should take until this scheduled spike and multiply it by the potential change.
//			if (lastSpikeSchedulingTime >= 0) {
//				lastSpikeSchedulingTime = (int)((nextSpikeTime - time) * changeFactor);
//				nextSpikeTime = lastSpikeSchedulingTime + time;
////				if (nextSpikeTime - (lastSpikeSchedulingTime - newIntervall) < time) {
////					emitSpike(1.0,time);
////					lastSpikeSchedulingTime = -1;
////					scheduleNextSpike(time);
////					return;
////				}
////				else {
////					nextSpikeTime -= lastSpikeSchedulingTime - newIntervall;
////				}
//			}
//			else {
//			}
//			lastSpikeSchedulingTime = newIntervall;
	}
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel#executeScheduledEvent(int)
	 */
	@Override
	protected void executeScheduledEvent(int time) {
		updateToTime(time);
		emitSpike(nextSpikeValue, time);
		lastSpikeSchedulingTime = -1;
		scheduleNextSpike(time);
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel#processSpike(double, int)
	 */
	@Override
	protected void processSpike(double value, int timeInUs) {
		updateToTime(timeInUs);
//		int before = logPotential.getIntValue();
		if (value > 0) {
			// assure bounds of +- 2.0, which corresponds to IntegerDecayModel.HALF, giving us an additional bit
			// for preventing overflow 
			if (value > 4.0)
				value = 4.0;
			logPotential.add(value);
			if (logPotential.getIntValue() > IntegerDecayModel.QUARTER)
				logPotential.setIntValue(IntegerDecayModel.QUARTER);
		}
		else if (value < 0) {
			if (value < -4.0)
				value = -4.0;			
			logPotential.add(value);
			if (logPotential.getIntValue() < IntegerDecayModel.MINUSQUARTER)
				logPotential.setIntValue(IntegerDecayModel.MINUSQUARTER);
		}
//		if (before > 0) {
//			changeFactor = (float)logPotential.getIntValue() / (float)before;
//		}
		scheduleNextSpike(timeInUs);
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#reset()
	 */
	@Override
	public void reset() {
		logPotential.setIntValue(0);
		lastTime = Integer.MIN_VALUE;
		lastSpikeSchedulingTime = -1; 
		unschedule();
	}
	
	public synchronized boolean isNegativeSpikesOn() {
		return negativeSpikesOn;
	}

	public synchronized void setNegativeSpikesOn(boolean negativeSpikesOn) {
		this.negativeSpikesOn = negativeSpikesOn;
	}

	public static class Creator extends SchedulableFiringModelCreator {
		private float timeConstant = 1e7f;
		private float maxSpikesPerSecond = 50f;
//		private float lambda = maxSpikesPerSecond / 2e-6f;
		private boolean negativeSpikesOn = false;

		public Creator(Preferences prefs) {
			super("SimplePoissonModel",prefs);
		}
		
		@Override
		public SchedulableFiringModel createUnit(int x, int y,
				SchedulableFiringModelMap map) {
			SimplePoissonModel simplePoissonModel = new SimplePoissonModel(x, y, map.getSignalHandler(), map);
			simplePoissonModel.setTimeConstant(timeConstant);
			simplePoissonModel.setLambda(maxSpikesPerSecond / 2e6f);
			simplePoissonModel.setNegativeSpikesOn(negativeSpikesOn);
			return simplePoissonModel;
		}
		public float getTimeConstant() {
			return timeConstant;
		}
		public void setTimeConstant(float timeConstant) {
			float before = this.timeConstant;
			this.timeConstant = timeConstant;
			getSupport().firePropertyChange("timeConstant", before, timeConstant);
		}
//		public float getLambda() {
//			return lambda;
//		}
//		public void setLambda(float lambda) {
//			getSupport().firePropertyChange("lambda", this.lambda, lambda);
//			this.lambda = lambda;
//		}
		public boolean isNegativeSpikesOn() {
			return negativeSpikesOn;
		}
		public void setNegativeSpikesOn(boolean negativeSpikesOn) {
			boolean before = this.negativeSpikesOn;
			this.negativeSpikesOn = negativeSpikesOn;
			getSupport().firePropertyChange("negativeSpikesOn", before, negativeSpikesOn);
		}

		/**
		 * @param maxSpikesPerSecond the maxSpikesPerSecond to set
		 */
		public void setMaxSpikesPerSecond(float maxSpikesPerSecond) {
			float before = this.maxSpikesPerSecond;
			this.maxSpikesPerSecond = maxSpikesPerSecond;
			getSupport().firePropertyChange("maxSpikesPerSecond", before, maxSpikesPerSecond);
//			this.lambda = maxSpikesPerSecond / 2e-6f;
		}

		/**
		 * @return the maxSpikesPerSecond
		 */
		public float getMaxSpikesPerSecond() {
			return maxSpikesPerSecond;
		}
		
	}
	
	public static SchedulableFiringModelCreator getCreator(Preferences prefs) {
		return new Creator(prefs);
	}
//	public static void main(String[] args) {
//		SchedulableWrapperMap map = new SchedulableWrapperMap(100, 100, null, null);
//		SimplePoissonModel.Creator creator = new SimplePoissonModel.Creator(null);
//		creator.setMaxSpikesPerSecond(100);
//		SchedulableFiringModel m = creator.createUnit(0, 0, map);
//		m.receiveSpike(2.0, 0);
//		int avg = 0;
//		for (int i = 0; i < 100000; i++) {
//			m.receiveSpike(0.0, 0);
//			avg += m.scheduledEvent.getFireTime();
////			System.out.println(m.scheduledEvent.getFireTime());
//		}
//		avg /= 100000;
//		System.out.println(avg);
//	}

}
