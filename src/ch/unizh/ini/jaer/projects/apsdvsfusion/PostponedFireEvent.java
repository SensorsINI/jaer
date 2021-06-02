/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis
 *
 */
public class PostponedFireEvent implements Comparable<PostponedFireEvent> {
	int fireTime;
	int x, y;
	//DynamicHeap<PostponedFireEvent>.Entry myEntry = null;
	SchedulableFiringModel firingModel;
	/**
	 * 
	 */
	public PostponedFireEvent(int x, int y, int fireTime, SchedulableFiringModel firingModel) {
		this.x = x;
		this.y = y;
		this.fireTime = fireTime;
		this.firingModel = firingModel;
		//this.myEntry = map.createEntry(this);
	}

	public void changePosition(int x, int y) {
		this.x = x;
		this.y = y;
	}
	public int getFireTime() {
		return fireTime;
	}
	public void setFireTime(int time) {
		this.fireTime = time;
	}
	
	public void setFiringModel(SchedulableFiringModel firingModel) { 
		this.firingModel = firingModel;
	}
	
	public SchedulableFiringModel getFiringModel() {
		return firingModel;
	}
	
	@Override
	public int compareTo(PostponedFireEvent other) {
		return this.fireTime - other.fireTime;
	}
	
	

}
