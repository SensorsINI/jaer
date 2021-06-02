/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.prefs.Preferences;

/**
 * @author Dennis
 *
 */
public abstract class SchedulableFiringModelMap extends FiringModelMap {

	
	DynamicHeap<PostponedFireEvent> heap = new DynamicHeap<PostponedFireEvent>();
	
	/**
	 * 
	 */
//	public SchedulableFiringModelMap(int sizeX, int sizeY, SignalHandler spikeHandler, Preferences parentPrefs, String nodeName) {
//		super(sizeX, sizeY, spikeHandler, parentPrefs, nodeName);
//	}
	public SchedulableFiringModelMap(int sizeX, int sizeY, SignalHandler spikeHandler, Preferences prefs) {
		super(sizeX, sizeY, spikeHandler, prefs);
	}
	
	public void clearHeap() {
		if (heap != null) {
			synchronized (heap) {
				heap.clear();
			}
			
		}
	}
	
	public void processScheduledEvents(int uptoTime) {
		if (enabled) {
			while (!heap.isEmpty() && heap.peek().getContent().getFireTime() <= uptoTime) {
				PostponedFireEvent event = heap.poll().getContent();
				// TODO: 
				event.getFiringModel().executeScheduledEvent(event.getFireTime());
			}
		} 
	}
	
	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		clearHeap();
	}

	public DynamicHeap<PostponedFireEvent>.Entry createEntry(PostponedFireEvent event) {
		return heap.createEntry(event);
	}
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap#get(int, int)
	 */
////	@Override
//	public abstract SchedulableFiringModel get(int x, int y);

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap#getSizeX()
	 */

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap#reset()
	 */
//	@Override
//	public void reset() {
//		super.reset();
//		// TODO Auto-generated method stub
//
//	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
