/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.tell2013.ThreeDTracker;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Low-level Tracker for small Gaussians with full covariance matrix.
 * @author Michael Pfeiffer
 */
public class FullGaussianTracker extends EventFilter2D implements FrameAnnotater{

	// Number of regions
	private int numRegions = getInt("numRegions", 50);

	// Learning rate for online updates
	private float alpha = getFloat("alpha", 0.1f);

	// Initial radius of regions
	private float initRadius = getFloat("initRadius", 5.0f);

	private float noiseThreshold = getFloat("noiseThreshold",0.001f);

	// Drawing radius for the Gaussians
	private float gaussRadius = getFloat("gaussRadius", 2.0f);

	// Repell constant for Gaussians
	private float repellConst = getFloat("repellConst", 1e-5f);

	// Length of event queue for random movements
	private int queueLength = getInt("queueLength",50);

	// Maximum age before movement
	private float maxAge = getFloat("maxAge", 1000000.0f);

	// Learning rate for sigma
	private float sigmaAlpha = getFloat("sigmaAlpha", alpha*0.1f);

	// Pass all spikes to next level or just centers
	private boolean passAllSpikes = getBoolean("passAllSpikes",true);

	// Minimum and maximum covariance
	private float minSigma = getFloat("minSigma", 0.01f);
	private float maxSigma = getFloat("maxSigma", 500.0f);

	// Maximum area for Gaussian (computed as product of eigenvalues
	private float maxEigenProduct = getFloat("maxEigenProduct", 1000f);

	// Draw individual regions
	private boolean drawEllipses = getBoolean("drawEllipses", true);

	// Draw only main axes of Gaussians
	private boolean drawOnlyMain = getBoolean("drawOnlyMain", false);

	// Enable reset of regions
	private boolean enableReset = getBoolean("enableReset", true);

	// Centers and covariances of the regions
	private float[] centerX, centerY;
	private float[][] Sigma;
	private float[] detSigma;
	private float[][] invSigma;
	private float[][] color;
	private float[][] cholSigma;
	private float[][] eigenValueSigma;
	private float[][] eigenVectorSigma;
	private float[][] eigenValueInvSigma;
	private float[][] eigenVectorInvSigma;

	// Last movements of each cluster
	private float[][] lastMovement;

	// Storing the coordinates of a circle for drawing
	final int N = 20;
	private float[] circleX;
	private float[] circleY;

	// Queue of random positions
	float[] queueX;
	float[] queueY;
	int queuePointer = 0;

	// Age of each cluster
	float[] age;

	public FullGaussianTracker(AEChip chip) {
		super(chip);

		final String draw = "Drawing", clust = "Clustering Parameters";

		setPropertyTooltip(clust,"numRegions", "Number of high level regions");
		setPropertyTooltip(clust,"alpha","Learning rate for cluster updates");
		setPropertyTooltip(clust, "noiseThreshold","Threshold for Gaussians below which an"
			+ "event is considered to be noise");
		setPropertyTooltip(draw, "initRadius", "Radius of the Gaussian to draw");
		setPropertyTooltip(draw, "passAllSpikes", "Draw all spikes, not just filter results");
		setPropertyTooltip(draw, "drawEllipses", "Draw ellipses pf Gaussians");
		setPropertyTooltip(draw, "drawOnlyMain", "Draw only main axes of Gaussians");
		setPropertyTooltip(clust, "repellConst", "Competition-constant for repelling other Gaussians");

		setPropertyTooltip(clust, "queueLength", "Length of queue for random positions");
		setPropertyTooltip(clust, "maxAge", "Maximum age of cluster before random movement");
		setPropertyTooltip(clust, "sigmaAlpha", "Learning rate for sigma");

		setPropertyTooltip(clust, "minSigma", "Minimum tolerated covariance");
		setPropertyTooltip(clust, "maxSigma", "Maximum tolerated covariance");
		setPropertyTooltip(clust, "maxEigenProduct", "Maximum area for Gaussians before random movement");
		setPropertyTooltip(clust, "enableReset", "Enable reset of regions");

		createRegions();

		queueX = new float[queueLength];
		queueY = new float[queueLength];
		queuePointer = 0;
	}

	// Computes the determinant and inverse of a matrix
	private void compute_det_inv(int k) {
		if ((k>=0) && (k<numRegions)) {
			detSigma[k] = (Sigma[k][0]*Sigma[k][3])-(Sigma[k][1]*Sigma[k][2]);
			invSigma[k][0] = Sigma[k][3] / detSigma[k];
			invSigma[k][1] = -Sigma[k][1] / detSigma[k];
			invSigma[k][2] = -Sigma[k][2] / detSigma[k];
			invSigma[k][3] = Sigma[k][0] / detSigma[k];
		}
	}

	// Computes the Cholesky Matrix of Sigma
	private void cholesky(int k) {
		if ((k>=0) && (k<numRegions)) {
			cholSigma[k][0] = (float) Math.sqrt(Sigma[k][0]);
			cholSigma[k][1] = 0.0f;
			cholSigma[k][2] = Sigma[k][2] / cholSigma[k][0];
			cholSigma[k][3] = (float) Math.sqrt(Sigma[k][3] - ((Sigma[k][1]*Sigma[k][1])/Sigma[k][0]));
		}
	}

	// Computes the eigenvalues and eigenvectors of Sigma
	private void eigen(int k) {
		if ((k>=0) && (k<numRegions)) {
			float T = Sigma[k][0]+Sigma[k][3];
			float D = ((T*T)/4.0f)-detSigma[k];
			if (D<0) {
				D = 0f;
			}
			eigenValueSigma[k][0] = (T/2.0f) + (float)Math.sqrt(D);
			eigenValueSigma[k][1] = (T/2.0f) - (float)Math.sqrt(D);

			// System.out.println("InEV " + eigenValueSigma[k][0] + " / " + eigenValueSigma[k][1]);
			// System.out.println("InEVDet " + detSigma[k] + " / " + (T*T/4.0f - detSigma[k]));

			if (Math.abs(Sigma[k][2])>0) {
				eigenVectorSigma[k][0] = eigenValueSigma[k][0]-Sigma[k][3];
				eigenVectorSigma[k][1] = eigenValueSigma[k][1]-Sigma[k][3];
				eigenVectorSigma[k][2] = Sigma[k][2];
				eigenVectorSigma[k][3] = Sigma[k][2];
			} else if (Math.abs(Sigma[k][1])>0) {
				eigenVectorSigma[k][0] = Sigma[k][1];
				eigenVectorSigma[k][1] = Sigma[k][1];
				eigenVectorSigma[k][2] = eigenValueSigma[k][0]-Sigma[k][0];
				eigenVectorSigma[k][3] = eigenValueSigma[k][1]-Sigma[k][0];
			} else {
				eigenVectorSigma[k][0] = eigenVectorSigma[k][3] = 1.0f;
				eigenVectorSigma[k][1] = eigenVectorSigma[k][2] = 0.0f;
			}
			// Normalize the vector
			float d1 = (float) Math.sqrt(Math.pow(eigenVectorSigma[k][0],2)+
				Math.pow(eigenVectorSigma[k][2],2));
			float d2 = (float) Math.sqrt(Math.pow(eigenVectorSigma[k][1],2)+
				Math.pow(eigenVectorSigma[k][3],2));
			eigenVectorSigma[k][0] /= d1;
			eigenVectorSigma[k][2] /= d1;
			eigenVectorSigma[k][1] /= d2;
			eigenVectorSigma[k][3] /= d2;
		}
	}

	// Computes the eigenvalues and eigenvectors of the inverse of Sigma
	private void invEigen(int k) {
		if ((k>=0) && (k<numRegions)) {
			float T = invSigma[k][0]+invSigma[k][3];
			float D = (invSigma[k][0]*invSigma[k][3])-(invSigma[k][1]*invSigma[k][2]);
			eigenValueInvSigma[k][0] = 1.0f / eigenValueSigma[k][0];
			eigenValueInvSigma[k][1] = 1.0f / eigenValueSigma[k][1];

			if (Math.abs(invSigma[k][2])>0) {
				eigenVectorInvSigma[k][0] = eigenValueInvSigma[k][0]-invSigma[k][3];
				eigenVectorInvSigma[k][1] = eigenValueInvSigma[k][1]-invSigma[k][3];
				eigenVectorInvSigma[k][2] = invSigma[k][2];
				eigenVectorInvSigma[k][3] = invSigma[k][2];
			} else if (Math.abs(invSigma[k][1])>0) {
				eigenVectorInvSigma[k][0] = invSigma[k][1];
				eigenVectorInvSigma[k][1] = invSigma[k][1];
				eigenVectorInvSigma[k][2] = eigenValueInvSigma[k][0]-invSigma[k][0];
				eigenVectorInvSigma[k][3] = eigenValueInvSigma[k][1]-invSigma[k][0];
			} else {
				eigenVectorInvSigma[k][0] = eigenVectorInvSigma[k][3] = 1.0f;
				eigenVectorInvSigma[k][1] = eigenVectorInvSigma[k][2] = 0.0f;
			}
			// Normalize the vector
			float d1 = (float) Math.sqrt(Math.pow(eigenVectorInvSigma[k][0],2)+
				Math.pow(eigenVectorInvSigma[k][2],2));
			float d2 = (float) Math.sqrt(Math.pow(eigenVectorInvSigma[k][1],2)+
				Math.pow(eigenVectorInvSigma[k][3],2));
			eigenVectorInvSigma[k][0] /= d1;
			eigenVectorInvSigma[k][2] /= d1;
			eigenVectorInvSigma[k][1] /= d2;
			eigenVectorInvSigma[k][3] /= d2;
		}
	}


	// Creates the variables defining the regions
	private void createRegions() {

		centerX = new float[numRegions];
		centerY = new float[numRegions];
		Sigma = new float[numRegions][4];
		detSigma = new float[numRegions];
		invSigma = new float[numRegions][4];
		cholSigma = new float[numRegions][4];
		eigenValueSigma = new float[numRegions][2];
		eigenVectorSigma = new float[numRegions][4];
		eigenValueInvSigma = new float[numRegions][2];
		eigenVectorInvSigma = new float[numRegions][4];

		lastMovement = new float[numRegions][2];

		age = new float[numRegions];

		// Initialize Region positions

		// Compute the number of Gaussians (regular grid)
		int numPerRow = (int) Math.ceil(numRegions / Math.sqrt(numRegions));
		int numRows = (int) Math.ceil(numRegions / (float) numPerRow);

		// Initialize Gaussian positions
		float xpos = 2.0f*gaussRadius;
		float ypos = 2.0f*gaussRadius;

		float xdiff = (128.0f - (4.0f*gaussRadius)) / numPerRow;
		float ydiff = (128.0f - (4.0f*gaussRadius)) / numRows;

		int idx = 0;

		for (int i=0; i<numRows; i++) {
			for (int j=0; j<numPerRow; j++) {
				centerX[idx] = xpos;
				centerY[idx] = ypos;

				age[idx] = 0;

				xpos += xdiff;

				Sigma[idx] = new float[4];
				Sigma[idx][0] = initRadius;
				Sigma[idx][1] = 0.1f;
				Sigma[idx][2] = 0.1f;
				Sigma[idx][3] = initRadius;

				// Compute determinant and inverse of the matrix
				compute_det_inv(idx);

				// Compute Cholesky decomposition
				cholesky(idx);

				// Compute eigenvalues and eigenvectors
				eigen(idx);
				// invEigen(i);

				idx++;
				if (idx >= numRegions) {
					break;
				}
			}
			xpos = 2.0f*gaussRadius;
			ypos += ydiff;
			if (idx >= numRegions) {
				break;
			}


		}

		// System.out.println("Idx " + idx + "NR: " + numRows + "/" + numPerRow);

		// Create colors for each Gaussian
		color = new float[numRegions][3];
		for (int i=0; i<numRegions;i++) {
			float H = (i * 360.0f) / (numRegions);
			float[] tmp = AdaptiveGaussianTracker.HSV2RGB(H, 1.0f, 1.0f);
			System.arraycopy(tmp, 0, color[i], 0, 3);
		}

		// Initialize last movement
		for (int i=0; i<numRegions; i++) {
			lastMovement[i][0] = (float) ((0.2 * Math.random()) - 0.1);
			lastMovement[i][1] = (float) ((0.2 * Math.random()) - 0.1);
		}

		circleX = new float[N+1];
		circleY = new float[N+1];
		for (int i=0; i<=N; i++) {
			circleX[i] = gaussRadius * (float) (Math.cos((i*2.0*Math.PI)/N));
			circleY[i] = gaussRadius * (float) (Math.sin((i*2.0*Math.PI)/N));
		}
	}

	final float norm_const = (float) (1.0 / Math.sqrt(2*Math.PI));

	// Compute Gaussian probability
	public double gaussProb(int k, float X, float Y) {
		float dX = X - centerX[k];
		float dY = Y - centerY[k];

		float distX = (invSigma[k][0]*dX) + (invSigma[k][1]*dY);
		float distY = (invSigma[k][2]*dX) + (invSigma[k][3]*dY);

		double dist = (dX*distX) + (dY*distY);
		double G = (norm_const / Math.sqrt(detSigma[k])) *
			Math.exp(-0.5*dist);

		return G;
	}

	// Computes Mahalanobis distance between Gaussian and a point
	public double mahalanobisDist(int k, float X, float Y) {
		float dX = X - centerX[k];
		float dY = Y - centerY[k];

		float distX = (invSigma[k][0]*dX) + (invSigma[k][1]*dY);
		float distY = (invSigma[k][2]*dX) + (invSigma[k][3]*dY);

		double dist = (dX*distX) + (dY*distY);
		return dist;

	}


	// Rotates a Gaussian abround a pivot point
	private void rotateGauss(int k, float X, float Y, float theta, float rate) {
		if ((k>=0) && (k<numRegions)) {
			double dX = centerX[k] - X;
			double dY = centerY[k] - Y;
			double cosTheta = Math.cos(theta*rate);
			double sinTheta = Math.sqrt(1-(cosTheta*cosTheta));
			double[] R = {cosTheta, -sinTheta, sinTheta, cosTheta};


			// Rotate center
			float nX = (float) ((R[0]*dX) + (R[1]*dY));
			float nY = (float) ((R[2]*dX) + (R[3]*dY));

			// System.out.println("Before: " + centerX[k] + " / " +centerY[k]);
			centerX[k] = X+nX;
			centerY[k] = Y+nY;
			// System.out.println("After: " + centerX[k] + " / " +centerY[k]);

			// Rotate covariance matrix: R^-1 * Sigma * R
			Sigma[k][0] = (float)((Sigma[k][0]*R[0]) + (Sigma[k][1]*R[2]));
			Sigma[k][1] = (float)((Sigma[k][0]*R[1]) + (Sigma[k][1]*R[3]));
			Sigma[k][2] = (float)((Sigma[k][2]*R[0]) + (Sigma[k][3]*R[2]));
			Sigma[k][3] = (float)((Sigma[k][2]*R[1]) + (Sigma[k][3]*R[3]));

			Sigma[k][0] = (float)((R[0]*Sigma[k][0]) + (R[2]*Sigma[k][2]));
			Sigma[k][1] = (float)((R[0]*Sigma[k][1]) + (R[2]*Sigma[k][3]));
			Sigma[k][2] = (float)((R[1]*Sigma[k][0]) + (R[3]*Sigma[k][2]));
			Sigma[k][3] = (float)((R[1]*Sigma[k][1]) + (R[3]*Sigma[k][3]));


		}
	}


	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		if(!filterEnabled) {
			return in;
		}

		if ( in == null ){
			return null;
		}
		checkOutputPacketEventType(TrackerEvent.class);

		OutputEventIterator outItr=out.outputIterator();

		float max_time = Float.MIN_VALUE;

		int k, kk;

		float[] mahalanobisD = new float[numRegions];

		for (Object ein : in) { // iterate over all input events
			BasicEvent e = (BasicEvent) ein;
			if (passAllSpikes) {
				BasicEvent o=outItr.nextOutput();
				o.copyFrom(e);
			}

			// System.out.println("Found event");
			float ts = e.timestamp;
			if (ts > max_time) {
				max_time = ts;
			}

			// Compute distance to all Gaussians
			double max_dist = Double.MIN_VALUE;
			// double min_dist = Double.MAX_VALUE;

			int min_k = -1;
			float sum_dist = 0.0f;
			for (k=0; k<numRegions; k++) {
				double G = gaussProb(k, e.x, e.y);
				// double G = this.mahalanobisDist(k, e.x, e.y);

				mahalanobisD[k] = (float) G;
				if (Double.isNaN(G)) {
					System.out.println("Invalid Distance: " + k + ": " + Sigma[k][0] + " / " + Sigma[k][1] + " / " +
						Sigma[k][2] + " / " + Sigma[k][3] + " : " + detSigma[k]);
				}
				// System.out.println(k + ": " + G + " / " + max_dist);
				if (G >= max_dist) {
					max_dist = G;
					min_k = k;
				}

				/* if (G <= min_dist) {
                    min_dist = G;
                    min_k = k;
                } */

				if (!Double.isNaN(G)) {
					sum_dist += G;
				}
			}

			double norm_dist = max_dist / sum_dist;
			if (min_k < 0) {
				System.out.println("Min <0: " + min_k + " / " + max_dist + "/" + sum_dist + "/" + norm_dist + "(" + noiseThreshold + ")");
			}

			// Update Gaussians
			//            if ((norm_dist>noiseThreshold) && (min_k>=0)) {
			if ((norm_dist>noiseThreshold) && (min_k>=0)) {
				// System.out.println(norm_dist);
				float dX = e.x - centerX[min_k];
				float dY = e.y - centerY[min_k];

				age[min_k] = e.timestamp;

				float newCenterX = (alpha*e.x) + ((1-alpha)*centerX[min_k]);
				float newCenterY = (alpha*e.y) + ((1-alpha)*centerY[min_k]);

				// Store last movement
				lastMovement[min_k][0] += alpha*dX;
				lastMovement[min_k][1] += alpha*dY;

				float[] newSigma = new float[4];

				newSigma[0] = (sigmaAlpha*(dX*dX)) + ((1-sigmaAlpha) * Sigma[min_k][0]);
				newSigma[3] = (sigmaAlpha*(dY*dY)) + ((1-sigmaAlpha) * Sigma[min_k][3]);
				newSigma[1] = (sigmaAlpha*(dX*dY)) + ((1-sigmaAlpha) * Sigma[min_k][1]);
				newSigma[2] = newSigma[1];

				// Check bounds of sigma
				/*for (kk=0; kk<4; kk++) {
                    //System.out.println(newSigma[kk]);
                    if ((Math.abs(newSigma[kk])<minSigma) && ((kk==0) || (kk==3)))
                            newSigma[kk]=minSigma*Math.signum(newSigma[kk]);
                    if (newSigma[kk]>maxSigma) {
                        float oldSigma = newSigma[kk];
                        newSigma[kk]=maxSigma;
                        if ((kk==0) || (kk==3)) {
                            newSigma[1] = newSigma[2] = newSigma[1]*maxSigma / oldSigma;
                        }
                    }
                } */


				// Move center of the Gaussian if it is not too close
				// to another Gaussian
				centerX[min_k] = newCenterX;
				centerY[min_k] = newCenterY;
				for (kk=0; kk<4; kk++) {
					Sigma[min_k][kk] = newSigma[kk];
				}

				compute_det_inv(min_k);
				cholesky(min_k);
				eigen(min_k);
				// invEigen(k);

				// Push other Gaussians away
				for (kk=0; kk<numRegions; kk++) {
					if (kk != k) {
						dX = ((float) Math.sqrt(Math.abs(eigenValueSigma[min_k][0])) * eigenVectorSigma[min_k][0]) +
							((float) Math.sqrt(Math.abs(eigenValueSigma[min_k][1])) * eigenVectorSigma[min_k][1]);
						dY = ((float) Math.sqrt(Math.abs(eigenValueSigma[min_k][0])) * eigenVectorSigma[min_k][2]) +
							((float) Math.sqrt(Math.abs(eigenValueSigma[min_k][1])) * eigenVectorSigma[min_k][3]);
						centerX[kk] += repellConst * dX;
						centerY[kk] += repellConst * dY;

						if (centerX[kk] < 0) {
							centerX[kk] = 0.0f;
						}
						if (centerY[kk] < 0) {
							centerY[kk] = 0.0f;
						}
						if (centerX[kk] > 128) {
							centerX[kk] = 127.0f;
						}
						if (centerY[kk] > 128) {
							centerY[kk] = 127.0f;
						}

					}

				}

				TrackerEvent te = new TrackerEvent();
				te.randomMove = 0;

				// Check if region becomes too large, then move randomly
				if (detSigma[min_k] > maxEigenProduct) {
					// System.out.println("E: " + eigenValueSigma[min_k][0] + "/" + eigenValueSigma[min_k][1] +
					//        " D:" + detSigma[min_k]);
					moveRandom(min_k, max_time);
					te.randomMove = 1;
				}

				//                te.setX((short) centerX[min_k]);
				//                te.setY((short) centerY[min_k]);
				te.setX(e.x);
				te.setY(e.y);
				te.trackerID = min_k;
				te.setTimestamp((int) ts);

				TrackerEvent oe=(TrackerEvent)outItr.nextOutput();
				oe.copyFrom(te);

				//                BasicEvent oe=(BasicEvent)outItr.nextOutput();
				//                oe.copyFrom(te);

			} // if (normDist > noiseThresh)
			else {
				//System.out.println("Outside event X");
				if (min_k >= 0) {
					// System.out.println("Outside event");
					queueX[queuePointer] = e.x;
					queueY[queuePointer] = e.y;
					queuePointer++;
					if (queuePointer >= queueLength) {
						queuePointer=0;
					}
				}

			} // else
		}  // for (BasicEvent...

		// Check if random movements are necessary
		for (int i=0; i<numRegions; i++) {
			if ((max_time-age[i]) > maxAge) {
				moveRandom(i, max_time);
				TrackerEvent te = new TrackerEvent();
				te.randomMove = 1;
				te.setX((short) centerX[i]);
				te.setY((short) centerY[i]);
				te.trackerID = i;
				te.setTimestamp((int) max_time);

				TrackerEvent oe=(TrackerEvent)outItr.nextOutput();
				oe.copyFrom(te);
			}
		}


		return out;
	}

	// Sets a cluster to a new random position
	public void moveRandom(int k, float max_time) {
		// Move to position in the target queue
		int targetIdx = (int) (queueLength * Math.random());
		if (targetIdx >= queueLength) {
			targetIdx = queueLength-1;
		}
		centerX[k] = queueX[targetIdx];
		centerY[k] = queueY[targetIdx];

		age[k] = max_time;

		Sigma[k][0] = initRadius;
		Sigma[k][1] = 0.1f;
		Sigma[k][2] = 0.1f;
		Sigma[k][3] = initRadius;

		compute_det_inv(k);
		cholesky(k);
		eigen(k);

		lastMovement[k][0] = (float) ((0.2 * Math.random()) - 0.1);
		lastMovement[k][1] = (float) ((0.2 * Math.random()) - 0.1);

	}


	@Override
	public void resetFilter() {
		if (enableReset) {
			createRegions();

			for (int i=0; i<queueLength; i++) {
				queueX[i] = (float) (Math.random() * 128.0);
				queueY[i] = (float) (Math.random() * 128.0);
			}
			queuePointer = 0;
		}
	}

	@Override
	public void initFilter() {
		resetFilter();
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if (drawEllipses) {
			// Plot the Gaussians
			GL2 gl=drawable.getGL().getGL2(); // gets the OpenGL GL context. Coordinates are in chip pixels, 0,0 is LL
			int i,k,kk;

			gl.glLineWidth(3); // set line width in screen pixels (not chip pixels)
			for (k=0; k<numRegions; k++) {

				if (!drawOnlyMain) {
					gl.glColor4f(color[k][0],color[k][1],color[k][2],.5f); // set color blue
					gl.glBegin(GL.GL_LINE_LOOP); // start drawing a line loop
					for (i=0; i<=N; i++) {
						float x = (cholSigma[k][0]*circleX[i]) + (cholSigma[k][1]*circleY[i]);
						float y = (cholSigma[k][2]*circleX[i]) + (cholSigma[k][3]*circleY[i]);
						gl.glVertex2f(centerX[k]+x, centerY[k]+y);
					}
					gl.glEnd();
				}
				// Draw axes of ellipse
				gl.glBegin(GL.GL_LINES);
				float x1 = -gaussRadius * (float)Math.sqrt(eigenValueSigma[k][0])*eigenVectorSigma[k][0];
				float x2 = -x1;
				float y1 = -gaussRadius * (float)Math.sqrt(eigenValueSigma[k][0])*eigenVectorSigma[k][2];
				float y2 = -y1;
				gl.glVertex2f(centerX[k]+x1, centerY[k]+y1);
				gl.glVertex2f(centerX[k]+x2, centerY[k]+y2);
				gl.glEnd();

				if (!drawOnlyMain) {
					gl.glBegin(GL.GL_LINES);
					float x3 = -gaussRadius * (float)Math.sqrt(eigenValueSigma[k][1])*eigenVectorSigma[k][1];
					float x4 = -x3;
					float y3 = -gaussRadius * (float)Math.sqrt(eigenValueSigma[k][1])*eigenVectorSigma[k][3];
					float y4 = -y3;
					gl.glVertex2f(centerX[k]+x3, centerY[k]+y3);
					gl.glVertex2f(centerX[k]+x4, centerY[k]+y4);
					gl.glEnd();
				}
			}
		}
	}

	public float getAlpha() {
		return alpha;
	}

	synchronized public void setAlpha(float alpha) {
		this.alpha = alpha;
		putFloat("alpha", alpha);
	}

	public float getInitRadius() {
		return initRadius;
	}

	synchronized public void setInitRadius(float initRadius) {
		this.initRadius = initRadius;
		putFloat("initRadius", initRadius);
	}

	public int getNumRegions() {
		return numRegions;
	}

	synchronized public void setNumRegions(int numRegions) {
		this.numRegions = numRegions;
		putInt("numRegions",numRegions);
		createRegions();
	}

	public float getNoiseThreshold() {
		return noiseThreshold;
	}

	public void setNoiseThreshold(float noiseThreshold) {
		this.noiseThreshold = noiseThreshold;
		putFloat("noiseThreshold", noiseThreshold);
	}

	public float getGaussRadius() {
		return gaussRadius;
	}

	synchronized public void setGaussRadius(float gaussRadius) {
		this.gaussRadius = gaussRadius;
		putFloat("gaussRadius", gaussRadius);

		if ((circleX != null) && (circleY != null)) {
			for (int i=0; i<=N; i++) {
				circleX[i] = gaussRadius * (float) (Math.cos((i*2.0*Math.PI)/N));
				circleY[i] = gaussRadius * (float) (Math.sin((i*2.0*Math.PI)/N));
			}
		}
	}

	public float getRepellConst() {
		return repellConst;
	}

	synchronized public void setRepellConst(float repellConst) {
		this.repellConst = repellConst;
		putFloat("repellConst", repellConst);
	}


	public int getQueueLength() {
		return queueLength;
	}

	synchronized public void setQueueLength(int queueLength) {
		this.queueLength = queueLength;
		putInt("queueLength", queueLength);

		queueX = new float[queueLength];
		queueY = new float[queueLength];

		for (int i=0; i<queueLength; i++) {
			queueX[i] = (float) (Math.random() * 128.0);
			queueY[i] = (float) (Math.random() * 128.0);
		}
		queuePointer = 0;
	}

	public float getMaxAge() {
		return maxAge;
	}

	synchronized public void setMaxAge(float maxAge) {
		this.maxAge = maxAge;
		putFloat("maxAge",maxAge);
	}


	public float getSigmaAlpha() {
		return sigmaAlpha;
	}

	synchronized public void setSigmaAlpha(float sigmaAlpha) {
		this.sigmaAlpha = sigmaAlpha;
		putFloat("sigmaAlpha",sigmaAlpha);
	}


	public boolean isPassAllSpikes() {
		return passAllSpikes;
	}

	synchronized public void setPassAllSpikes(boolean passAllSpikes) {
		this.passAllSpikes = passAllSpikes;
		putBoolean("passAllSpikes",passAllSpikes);
	}

	public float getMaxSigma() {
		return maxSigma;
	}

	synchronized public void setMaxSigma(float maxSigma) {
		this.maxSigma = maxSigma;
		putFloat("maxSigma",maxSigma);
	}

	public float getMinSigma() {
		return minSigma;
	}

	synchronized public void setMinSigma(float minSigma) {
		this.minSigma = minSigma;
		putFloat("minSigma",minSigma);
	}

	public float getMaxEigenProduct() {
		return maxEigenProduct;
	}

	synchronized public void setMaxEigenProduct(float maxEigenProduct) {
		this.maxEigenProduct = maxEigenProduct;
		putFloat("maxEigenProduct",maxEigenProduct);
	}

	public boolean isDrawEllipses() {
		return drawEllipses;
	}

	synchronized public void setDrawEllipses(boolean drawEllipses) {
		this.drawEllipses = drawEllipses;
		putBoolean("drawEllipses",drawEllipses);
	}

	// Returns the center x-coordinate for a cluster
	synchronized public float getCenterX(int k) {
		if ((k>=0) && (k<numRegions)) {
			return centerX[k];
		}
		else {
			return -1.0f;
		}
	}

	// Returns the center y-coordinate for a cluster
	synchronized public float getCenterY(int k) {
		if ((k>=0) && (k<numRegions)) {
			return centerY[k];
		}
		else {
			return -1.0f;
		}
	}

	// Moves a center in the direction of a target point
	synchronized public int moveTo(int k, float targetX, float targetY, float rate) {
		if ((k>=0) && (k<numRegions)) {
			float diffX = targetX - centerX[k];
			float diffY = targetY - centerY[k];
			centerX[k] += rate * diffX;
			centerY[k] += rate * diffY;

			lastMovement[k][0] += alpha * rate * diffX;
			lastMovement[k][1] += alpha * rate * diffY;
			return 1;
		} else {
			return 0;
		}
	}

	// Sets the age of a cluster
	synchronized public void setAge(int k, float newAge) {
		if ((k>=0) && (k<numRegions)) {
			age[k] = newAge;
		}
	}

	// Makes the colors of two clusters more similar
	public void updateColors(int k, int target, float rate) {
		if ((k>=0) && (k<numRegions) && (target>=0) && (target<numRegions)) {
			for (int j=0; j<3; j++) {
				float randRate = 0.01f;
				color[k][j] += rate * (((color[target][j]-color[k][j])+
					(randRate*Math.random()))-(0.5*randRate));
				if (color[k][j] < 0) {
					color[k][j]=0.0f;
				}
				if (color[k][j] > 1) {
					color[k][j]=1.0f;
				}
			}
		}
	}

	public boolean isDrawOnlyMain() {
		return drawOnlyMain;
	}

	synchronized public void setDrawOnlyMain(boolean drawOnlyMain) {
		this.drawOnlyMain = drawOnlyMain;
		putBoolean("drawOnlyMain",drawOnlyMain);
	}

	public float getLastMovementX(int k) {
		if ((k>=0) && (k<numRegions)) {
			return lastMovement[k][0];
		}
		else {
			return 0.0f;
		}
	}

	public float getLastMovementY(int k) {
		if ((k>=0) && (k<numRegions)) {
			return lastMovement[k][1];
		}
		else {
			return 0.0f;
		}
	}

	public void setColor(int k, float r, float g, float b) {
		if ((k>=0) && (k<numRegions)) {
			color[k][0] = r;
			color[k][1] = g;
			color[k][2] = b;
		}
	}

	public boolean isEnableReset() {
		return enableReset;
	}

	synchronized public void setEnableReset(boolean enableReset) {
		this.enableReset = enableReset;
		putBoolean("enableReset", enableReset);
	}


}
