package ch.ethz.hest.balgrist.microscopetracker;

/**
 * a simple class to save a velocity and the corresponding timestamp
 *
 * @author Niklaus Amrein
 */
public class VTelement {
	public float velocity;
	public int time;

	// constructor
	public VTelement(float v, int t){
		velocity = v;
		time = t;
	}
}
