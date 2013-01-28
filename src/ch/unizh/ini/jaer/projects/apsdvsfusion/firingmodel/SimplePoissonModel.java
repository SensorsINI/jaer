/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel;

import java.util.Random;

import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelCreator;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelMap;

/**
 * @author Dennis
 *
 */
public class SimplePoissonModel extends SchedulableFiringModel {
	IntegerDecayModel currentPotential = new IntegerDecayModel();
	int lastSpikeSchedulingTime = -1;
	int lastTime = Integer.MIN_VALUE;
	int nextSpikeTime = Integer.MIN_VALUE;
	// after 10 successive spikes, the average spike rate should be 100 Hz:  
	float lambda = 1.0f/100000f;
	Random random = new Random();
	/**
	 * @param x
	 * @param y
	 * @param map
	 */
	public SimplePoissonModel(int x, int y, SchedulableFiringModelMap map) {
		super(x, y, map);
		// timeconstant = 1s
		currentPotential.setTimeConstant(1000000f);
		// TODO Auto-generated constructor stub
	}
	
	public void setTimeConstant(float timeConstant) {
		currentPotential.setTimeConstant(timeConstant);
	}
	
	public float getTimeConstant() {
		return currentPotential.getTimeConstant();
	}

	private void updateToTime(int time) {
		if (time > lastTime) {
			int timePassed = time - lastTime;
			if (timePassed > 0)
				currentPotential.decay(timePassed);
			else
				currentPotential.setIntValue(0);
		}
		else
			lastSpikeSchedulingTime = Integer.MIN_VALUE;
		lastTime = time;
	}
	

	private void scheduleNextSpike(int time) {
		if (currentPotential.getIntValue() > 0) {
			int newIntervall = (int)(Math.log(random.nextDouble()) / (lambda * currentPotential.getValue()));
			if (lastSpikeSchedulingTime >= 0) {
				if (nextSpikeTime - (lastSpikeSchedulingTime - newIntervall) < time) {
					emitSpike(1.0,time);
					lastSpikeSchedulingTime = -1;
					scheduleNextSpike(time);
					return;
				}
				else {
					nextSpikeTime -= lastSpikeSchedulingTime - newIntervall;
				}
			}
			else {
				lastSpikeSchedulingTime = newIntervall;
				nextSpikeTime= time+lastSpikeSchedulingTime;
			}
			lastSpikeSchedulingTime = newIntervall;
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
		currentPotential.add(value);
		scheduleNextSpike(timeInUs);
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#reset()
	 */
	@Override
	public void reset() {
		currentPotential.setIntValue(0);
		lastTime = Integer.MIN_VALUE;
		lastSpikeSchedulingTime = -1; 
	}
	
	public static SchedulableFiringModelCreator getCreator() {
		return new SchedulableFiringModelCreator() {
			@Override
			public SchedulableFiringModel createUnit(int x, int y,
					SchedulableFiringModelMap map) {
				// TODO Auto-generated method stub
				return new SimplePoissonModel(x, y, map);
			}
		};
	}

}
