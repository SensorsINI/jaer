package ch.ethz.hest.balgrist.microscopetracker;

/**
 * a simple class to save a velocity and the corresponding clusterID
 *
 * @author Niklaus Amrein
 */
public class ClusterElement {

	private float velocityPPT;
	private float velocityPPS;
	private int clusterID;

	// constructor
	public ClusterElement(float vPPT, float vPPS, int id){
		velocityPPT = vPPT;
		velocityPPS = vPPS;
		clusterID = id;
	}

	public float getVelocityPPT() {
		return velocityPPT;
	}

	public void setVelocityPPT(float velocityPPT) {
		this.velocityPPT = velocityPPT;
	}

	public float getVelocityPPS() {
		return velocityPPS;
	}

	public void setVelocityPPS(float velocityPPS) {
		this.velocityPPS = velocityPPS;
	}

	public int getClusterID() {
		return clusterID;
	}

	public void setClusterID(int clusterID) {
		this.clusterID = clusterID;
	}
}
