/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis
 *
 */
public abstract class SustainedActivityFiringModel implements FiringModel {
	final DynamicHeap<PostponedFireEvent>.Entry heapEntry;
	final PostponedFireEvent scheduledEvent;
	
	public SustainedActivityFiringModel(int x, int y, SustainedActivityMap map) {
		scheduledEvent = new PostponedFireEvent(x, y, Integer.MAX_VALUE, this);
		heapEntry = map.createEntry(scheduledEvent);
	}
	
	public void scheduleEvent(int time) {
		scheduledEvent.setFireTime(time);
		heapEntry.contentChanged();
	}

	public abstract void postponedEvent(int time);
	
// TODO: receiveSpike from FiringModel needs to be overwritten (check whether there are scheduledEvents before that!)
// Problem: FiringModel needs to be modified (needs to know about its position etc.
// In particular, we need to know about the SpikeHandler that should evaluate the possibly outgoing events!
// Best practice is probably to store a pointer to the map, and the map should store a pointer to its spikeHandler.
}
