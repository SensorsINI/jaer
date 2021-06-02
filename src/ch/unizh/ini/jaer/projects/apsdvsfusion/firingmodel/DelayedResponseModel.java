/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel;

import java.util.LinkedList;
import java.util.prefs.Preferences;

import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelCreator;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelMap;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SignalHandler;

/**
 * @author Dennis
 *
 */
public class DelayedResponseModel extends SchedulableFiringModel {

	LinkedList<Double> emitQueue = new LinkedList<Double>(); 
	LinkedList<Integer> emitTimeQueue = new LinkedList<Integer>();
	int nextQueueTime = Integer.MIN_VALUE;
	
	int delay = 10000;

	public DelayedResponseModel(int x, int y, SignalHandler handler, SchedulableFiringModelMap map) {
		super(x,y,handler, map);
	}

	/**
	 * @return the delay
	 */
	public int getDelay() {
		return delay;
	}

	/**
	 * @param delay the delay to set
	 */
	public void setDelay(int delay) {
		this.delay = delay;
	}
	
	
	public static class Creator extends SchedulableFiringModelCreator {
		int delay = 10000;
		public Creator(String name, Preferences prefs) {
			super(name, prefs);
		}

		@Override
		public SchedulableFiringModel createUnit(final int x, final int y,
				final SchedulableFiringModelMap map) {
			final DelayedResponseModel delayedResponseModel = new DelayedResponseModel(x, y, map.getSignalHandler(), map);
			delayedResponseModel.setDelay(delay);
			return delayedResponseModel;
		}

		/**
		 * @return the delay
		 */
		public int getDelay() {
			return delay;
		}

		/**
		 * @param delay the delay to set
		 */
		public void setDelay(int delay) {
			int before = delay;
			this.delay = delay;
			getSupport().firePropertyChange("delay", before, delay);
		}
			
	}
	
	
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel#executeScheduledEvent(int)
	 */
	@Override
	protected void executeScheduledEvent(int time) {
		if (!emitQueue.isEmpty() && time == nextQueueTime) {
			emitSpike(emitQueue.removeFirst(), emitTimeQueue.removeFirst());
			if (!emitTimeQueue.isEmpty()) {
				nextQueueTime = emitTimeQueue.getFirst();
				scheduleEvent(nextQueueTime);
			}
		}

	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel#processSpike(double, int)
	 */
	@Override
	protected void processSpike(double value, int time) {
		if (time < 1 || Integer.MAX_VALUE - time > delay) {
			time += delay;
			emitTimeQueue.add(time);
			emitQueue.add(value);
			if (emitTimeQueue.size() == 1) {
				nextQueueTime = time;
				scheduleEvent(time);
			}
		}
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#reset()
	 */
	@Override
	public void reset() {
		emitQueue.clear();
		emitTimeQueue.clear();
	}
	
	
//	FiringModel internalModel = null;
//	
//	public static class Creator extends SchedulableFiringModelCreator {
//
//
//		FiringModelCreator fmCreator;
//		SchedulableFiringModelCreator sfmCreator = null;
//		
//		
//		
//		public Creator(String name, Preferences prefs) {
//			super(name, prefs);
//			fmCreator = new IntegrateAndFire.Creator(prefs.node("internalCreator"));
//		}
//
//		@Override
//		public SchedulableFiringModel createUnit(int x, int y,
//				SchedulableFiringModelMap map) {
//			if (fmCreator != null) 
//				return new DelayedResponseModel(x, y, fmCreator.createUnit(x, y, map), map.getSignalHandler(), map);
//			else 
//				return new DelayedResponseModel(x, y, sfmCreator.createUnit(x, y, map), map.getSignalHandler(), map);
//		}
//		
//	}
//	
//	
//	class SpikeForwarder implements SignalHandler {
//		@Override
//		public void signalAt(int x, int y, int time, double value) {
//			if (time < 1 || Integer.MAX_VALUE - time > delay) {
//				emitTimeQueue.add(time);
//				emitQueue.add(value);
//				if (emitTimeQueue.size() == 1) {
//					nextQueueTime = time;
//					scheduleEvent(time);
//				}
//			}
//		}
//		@Override
//		public void reset() {
//		}
//	}
//	SpikeForwarder mySpikeForwarder = new SpikeForwarder();
//	/**
//	 * @param x
//	 * @param y
//	 * @param map
//	 */
//	public DelayedResponseModel(int x, int y, FiringModel internalModel, SignalHandler handler, SchedulableFiringModelMap map) {
//		super(x, y, handler, map);
//		this.internalModel = internalModel;
//		internalModel.setSignalHandler(mySpikeForwarder);
//	}
//
//	/* (non-Javadoc)
//	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel#executeScheduledEvent(int)
//	 */
//	@Override
//	protected void executeScheduledEvent(int time) {
//		if (!emitQueue.isEmpty() && time == nextQueueTime) {
//			emitSpike(emitQueue.removeFirst(), emitTimeQueue.removeFirst());
//			if (!emitTimeQueue.isEmpty()) {
//				nextQueueTime = emitTimeQueue.getFirst();
//				scheduleEvent(nextQueueTime);
//			}
//		}
//
//	}
//
//	/* (non-Javadoc)
//	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModel#processSpike(double, int)
//	 */
//	@Override
//	protected void processSpike(double value, int timeInUs) {
//		internalModel.receiveSpike(value, timeInUs);
//	}
//
//	/* (non-Javadoc)
//	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModel#reset()
//	 */
//	@Override
//	public void reset() {
//		emitQueue.clear();
//		emitTimeQueue.clear();
//		internalModel.reset();
//	}
//
}

