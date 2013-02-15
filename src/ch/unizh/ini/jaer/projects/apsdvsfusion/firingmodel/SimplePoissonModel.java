/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel;

import java.util.Random;
import java.util.prefs.Preferences;

import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelCreator;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelMap;

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
	// at the maximum logPotential of 2.0, the spike rate should be 100 Hz: 
	float lambda = 1.0f/40000f;
	Random random = new Random();
	/**
	 * @param x
	 * @param y
	 * @param map
	 */
	public SimplePoissonModel(int x, int y, SchedulableFiringModelMap map) {
		super(x, y, map);
		// timeconstant = 1s
		logPotential.setTimeConstant(1e7f);
		// We never want to exceed IntegerDecayModel.ONE. Since we want to max out at +- 2.0, the maximum number
		// we might ask is +- 4.0. In the worst case, an intermediate value would then be +- 6.0.
		// I set the multiplicator to 1/8 to make sure +-6.0 is in the Integer range.
		logPotential.setMultiplicator(0.125f);
		// TODO Auto-generated constructor stub
	}
	
	public void setTimeConstant(float timeConstant) {
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
				int newIntervall = (int)(-Math.log(random.nextDouble()) / (lambda * logPotential.getValue()));
				lastSpikeSchedulingTime = newIntervall;
				nextSpikeTime= time+lastSpikeSchedulingTime;
//			}
//			lastSpikeSchedulingTime = newIntervall;
			scheduleEvent(nextSpikeTime);
		}
		else unschedule();
	}
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel#executeScheduledEvent(int)
	 */
	@Override
	protected void executeScheduledEvent(int time) {
		updateToTime(time);
		emitSpike(1.0, time);
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
	
	public static SchedulableFiringModelCreator getCreator(Preferences prefs) {
		return new SchedulableFiringModelCreator("SimplePoissonModel",prefs) {
			@Override
			public SchedulableFiringModel createUnit(int x, int y,
					SchedulableFiringModelMap map) {
				// TODO Auto-generated method stub
				return new SimplePoissonModel(x, y, map);
			}
		};
	}

}
