/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis
 *
 */
public abstract class SchedulableFiringModel extends FiringModel {
	final DynamicHeap<PostponedFireEvent>.Entry heapEntry;
	public final PostponedFireEvent scheduledEvent;
	
	public SchedulableFiringModel(int x, int y, SignalHandler handler, SchedulableFiringModelMap map) {
		super(x,y,handler);
		scheduledEvent = new PostponedFireEvent(x, y, Integer.MAX_VALUE, this);
		heapEntry = map.createEntry(scheduledEvent);
	}
	
	public void unschedule() {
		heapEntry.remove();
	}
	
	public void scheduleEvent(int time) {
		scheduledEvent.setFireTime(time);
		heapEntry.contentChanged();
	}

	protected abstract void executeScheduledEvent(int time);
	protected abstract void processSpike(double value, int timeInUs); 
	
	protected void runScheduledEvents(int uptoTime) {
		while (scheduledEvent.getFireTime() <= uptoTime && heapEntry.getPosition() >= 0) {
			heapEntry.remove();
			executeScheduledEvent(scheduledEvent.getFireTime());
		}
			
	}
	
	public void receiveSpike(double value, int timeInUs) {
		runScheduledEvents(timeInUs);
		processSpike(value,timeInUs);
	}
	
// TODO: receiveSpike from FiringModel needs to be overwritten (check whether there are scheduledEvents before that!)
// Problem: FiringModel needs to be modified (needs to know about its position etc.
// In particular, we need to know about the SpikeHandler that should evaluate the possibly outgoing events!
// Best practice is probably to store a pointer to the map, and the map should store a pointer to its spikeHandler.
}
