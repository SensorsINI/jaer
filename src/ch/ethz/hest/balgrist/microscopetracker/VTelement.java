package ch.ethz.hest.balgrist.microscopetracker;

/**
 * a simple class to save a velocity and the corresponding timestamp
 *
 * @author Niklaus Amrein
 */
public class VTelement {
	private float velocity;
	private int timeNew;
	private int timeOld;

	// constructor
	public VTelement(float v, int tNew, int tOld){
		velocity = v;
		timeNew = tNew;
		timeOld = tOld;
	}

	public float getVelocity() {
		return velocity;
	}

	public void setVelocity(float velocity) {
		this.velocity = velocity;
	}

	public int getTimeNew() {
		return timeNew;
	}

	public void setTimeNew(int time) {
		this.timeNew = time;
	}

	public int getTimeOld() {
		return timeOld;
	}

	public void setTimeOld(int told) {
		this.timeOld = told;
	}
}
