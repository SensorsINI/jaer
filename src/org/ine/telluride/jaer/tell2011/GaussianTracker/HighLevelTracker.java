/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.tell2011.GaussianTracker;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * High-level Tracker that tracks multiple large receptive fields that receive input
 * from a GroupingGaussianTracker. Learns to extract connected regions from the image,
 * and models them as Gaussians of different covariances.
 * @author Michael Pfeiffer
 */
public class HighLevelTracker extends EventFilter2D implements FrameAnnotater{

	// Number of high level regions
	private int numRegions = getInt("numRegions", 5);

	// Learning rate for online updates
	private float alpha = getFloat("alpha", 0.01f);

	// Initial radius of regions
	private float initRadius = getFloat("initRadius", 15.0f);

	private float noiseThreshold = getFloat("noiseThreshold",0.0001f);

	// Drawing radius for the Gaussians
	private float gaussRadius = getFloat("gaussRadius", 2.0f);

	// Repell constant for Gaussians
	private float repellConst = getFloat("repellConst", 1e-5f);

	// Tolerance to view a movement as parallel
	private float parallelTolerance = getFloat("parallelTolerance",0.1f);

	// Threshold for two regions to be connected
	private float connectThresh = getFloat("connectThresh", 1e-8f);

	// Strength of joint bond
	private float bondStrength = getFloat("bondStrength", 0.0001f);

	// Length of event queue for random movements
	private int queueLength = getInt("queueLength",50);

	// Maximum age before movement
	private float maxAge = getFloat("maxAge", 50000.0f);

	// Minimum time for a bond to last
	private float minBondTime = getFloat("minBondTime", 1e6f);

	// Learning rate for sigma
	private float sigmaAlpha = getFloat("sigmaAlpha", alpha*0.1f);

	// Learning rate for rotation
	private float rotationAlpha = getFloat("rotationAlpha", alpha*0.1f);

	// Enclosed gaussian tracker (second level)
	FilterChain filterChain = null;
	GroupingGaussianTracker secondLevel = null;

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

	// Connection probability and coordinates of joint
	private float[][] connectProb;
	private float[][] jointX;
	private float[][] jointY;
	private float[][] connectAge;

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

	public HighLevelTracker(AEChip chip) {
		super(chip);

		// Create Gaussian filter and filterchain
		secondLevel = new GroupingGaussianTracker(chip);
		// Set parameters of the Gaussian
		secondLevel.setCoherenceRate(1.0e-8f);
		secondLevel.setColorRate(0.01f);
		secondLevel.setMinConnectivity(0.5f);
		secondLevel.setSpikeConnected(true);
		secondLevel.setUseLearning(true);
		secondLevel.setKernelConst(0.005f);
		secondLevel.setKernelOffset(0.1f);
		secondLevel.setTimingAlpha(0.05f);
		secondLevel.setAlpha(0.01f);
		secondLevel.setLambda(0.1f);
		secondLevel.setRecField(30.0f);
		secondLevel.setReduceW(0.5f);
		secondLevel.setSpikeAmplitude(1.0f);
		secondLevel.setTau(20.0f);
		secondLevel.setVleak(0.0f);
		secondLevel.setVreset(0.0f);
		secondLevel.setDrawConnections(false);
		secondLevel.setDrawThresh(0.5f);
		secondLevel.setPassAllSpikes(false);
		secondLevel.getGaussTracker().setDrawCircles(false);


		filterChain = new FilterChain(chip);
		filterChain.add(secondLevel);

		setEnclosedFilterChain(filterChain);

		final String draw = "Drawing", clust = "Clustering Parameters";

		setPropertyTooltip(clust,"numRegions", "Number of high level regions");
		setPropertyTooltip(clust,"alpha","Learning rate for cluster updates");
		setPropertyTooltip(clust, "noiseThreshold","Threshold for Gaussians below which an"
			+ "event is considered to be noise");
		setPropertyTooltip(draw, "Radius of the Gaussian to draw");
		setPropertyTooltip(clust, "repellConst", "Competition-constant for repelling other Gaussians");
		setPropertyTooltip(clust, "parallelTolerance", "Tolerance to view movements as parallel");
		setPropertyTooltip(clust, "connectThresh", "Threshold for two regions to be connected");
		setPropertyTooltip(clust, "bondStrength", "Strength of the bond between two regions");

		setPropertyTooltip(clust, "queueLength", "Length of queue for random positions");
		setPropertyTooltip(clust, "maxAge", "Maximum age of cluster before random movement");
		setPropertyTooltip(clust, "minBondTime","Minimum time for a bond to survive");
		setPropertyTooltip(clust, "sigmaAlpha", "Learning rate for sigma");
		setPropertyTooltip(clust, "rotationAlpha", "Learning rate for rotation");

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

	// Computes the intersection point of the main axes of two regions
	private float[] mainAxesIntersect(int k, int l) {
		if ((k>=0) && (l>=0) && (k<numRegions) && (l<numRegions)) {
			float[] tmp = new float[2];
			// Determine which is the main axis
			int mainAxis1=0, mainAxis2=0;
			if (eigenValueSigma[k][1]>eigenValueSigma[k][0]) {
				mainAxis1 = 1;
			}
			if (eigenValueSigma[l][1]>eigenValueSigma[l][0]) {
				mainAxis2 = 1;
			}

			int xidx1 = mainAxis1;
			int xidx2 = mainAxis2;
			int yidx1 = 2+mainAxis1;
			int yidx2 = 2+mainAxis2;

			double denom = (eigenVectorSigma[l][xidx2]*eigenVectorSigma[k][yidx1]) -
				(eigenVectorSigma[k][xidx1]*eigenVectorSigma[l][yidx2]);

			if (Math.abs(denom) > 0) {
				double lambda1 = ((eigenVectorSigma[l][xidx2]*(centerY[l]-centerY[k])) -
					(eigenVectorSigma[l][yidx2]*(centerX[l]-centerX[k]))) / denom;

				tmp[0] = (float) (centerX[k] + (lambda1 * eigenVectorSigma[k][xidx1]));
				tmp[1] = (float) (centerY[k] + (lambda1 * eigenVectorSigma[k][yidx1]));
			}

			return tmp;
		}
		else {
			return null;
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

		connectProb = new float[numRegions][numRegions];
		jointX = new float[numRegions][numRegions];
		jointY = new float[numRegions][numRegions];
		connectAge = new float[numRegions][numRegions];

		age = new float[numRegions];

		// Initialize Region positions
		int numGaussians = secondLevel.getNumGaussians();
		for (int i=0; i<numRegions; i++) {
			int idx = (int) Math.floor(Math.random()*numGaussians);
			centerX[i] = secondLevel.getCenterX(idx) + ((float) Math.random() * initRadius);
			centerY[i] = secondLevel.getCenterY(idx) + ((float) Math.random() * initRadius);
			if (centerX[i]<0) {
				centerX[i] = 1.0f;
			}
			if (centerX[i]>128) {
				centerX[i] = 127.0f;
			}
			if (centerY[i]<0) {
				centerY[i] = 1.0f;
			}
			if (centerY[i]>128) {
				centerY[i] = 127.0f;
			}

			Sigma[i] = new float[4];
			Sigma[i][0] = initRadius;
			Sigma[i][1] = 0.1f;
			Sigma[i][2] = 0.1f;
			Sigma[i][3] = initRadius;

			// Compute determinant and inverse of the matrix
			compute_det_inv(i);

			// Compute Cholesky decomposition
			cholesky(i);

			// Compute eigenvalues and eigenvectors
			eigen(i);
			// invEigen(i);
		}

		// Create colors for each Gaussian
		color = new float[numRegions][3];
		for (int i=0; i<numRegions;i++) {
			float H = (i * 360.0f) / (numRegions);
			float[] tmp = GaussianTracker.HSV2RGB(H, 1.0f, 1.0f);
			System.arraycopy(tmp, 0, color[i], 0, 3);
		}

		// Initialize last movement
		for (int i=0; i<numRegions; i++) {
			lastMovement[i][0] = (float) ((0.2 * Math.random()) - 0.1);
			lastMovement[i][1] = (float) ((0.2 * Math.random()) - 0.1);
		}

		// Initialize connection probabilities
		for (int i=0; i<numRegions; i++) {
			for (int j=0; j<numRegions; j++) {
				connectProb[i][j] = 0.0f;
				jointX[i][j] = 0.0f;
				jointY[i][j] = 0.0f;
			}
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
	private double gaussProb(int k, float X, float Y) {
		float dX = X - centerX[k];
		float dY = Y - centerY[k];

		float distX = (invSigma[k][0]*dX) + (invSigma[k][1]*dY);
		float distY = (invSigma[k][2]*dX) + (invSigma[k][3]*dY);

		double dist = (dX*distX) + (dY*distY);
		double G = (norm_const / Math.sqrt(detSigma[k])) *
			Math.exp(-0.5*dist);

		return G;
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

	// Computes the rotation angle for a new (x,y) event around the (k - l) joint
	private float rotationAngle(int k, int l, float x, float y) {
		if ((k>=0) && (k<numRegions) && (l>=0) && (l<numRegions)) {
			float xv1 = x-jointX[k][l];
			float yv1 = y-jointY[k][l];
			float xv2 = centerX[k] - jointX[k][l];
			float yv2 = centerY[k] - jointY[k][l];

			float D1 = (xv1*xv1)+(yv1*yv1);
			float D2 = (xv2*xv2)+(yv2*yv2);

			float cos = 0.0f;
			if ((D1*D2) > 0) {
				cos = ((xv1 * xv2) + (yv1 * yv2)) / (float) (Math.sqrt(D1)*Math.sqrt(D2));
			}
			if (cos < -1) {
				cos =-1f;
			}
			if (cos > 1) {
				cos = 1f;
			}
			return (float) Math.acos(cos);
		}
		else {
			return 0.0f;
		}
	}

	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		if(!filterEnabled) {
			return in;
		}

		EventPacket<?> nextOut = getEnclosedFilterChain().filterPacket(in);
		// out = getEnclosedFilterChain().filterPacket(in);

		if ( in == null ){
			return null;
		}
		// checkOutputPacketEventType(in);

		OutputEventIterator outItr=out.outputIterator();

		float max_time = Float.MIN_VALUE;

		int k, kk;

		float[] mahalanobisD = new float[numRegions];

		for (BasicEvent e : nextOut) { // iterate over all input events
			BasicEvent o=outItr.nextOutput();
			o.copyFrom(e);
			if (e instanceof TrackerEvent) {
				// System.out.println("Found event");
				float ts = e.timestamp;
				if (ts > max_time) {
					max_time = ts;
				}

				// Compute distance to all Gaussians
				double max_dist = Double.MIN_VALUE;
				int min_k = -1;
				float sum_dist = 0.0f;
				for (k=0; k<numRegions; k++) {
					double G = gaussProb(k, e.x, e.y);

					mahalanobisD[k] = (float) G;
					// System.out.println(k + ": " + G + " / " + max_dist);
					if (G >= max_dist) {
						max_dist = G;
						min_k = k;
					}

					sum_dist += G;
				}

				double norm_dist = max_dist / sum_dist;
				if (min_k < 0) {
					System.out.println("Min " + min_k + " / " + max_dist + "/" + norm_dist + "(" + noiseThreshold + ")");
					/* for (kk=0; kk<numRegions; kk++) {
                        System.out.println("k " + kk + " : " + centerX[kk] + " / " + centerY[kk]);
                    } */
				}

				// Update Gaussians
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


					// Move center of the Gaussian if it is not too close
					// to another Gaussian
					centerX[min_k] = newCenterX;
					centerY[min_k] = newCenterY;
					for (kk=0; kk<4; kk++) {
						Sigma[min_k][kk] = newSigma[kk];
					}

					// Rotate the Gaussian around potential pivot points
					for (kk=0; kk<numRegions; kk++) {
						if ((min_k != kk) && (connectProb[min_k][kk]>=connectThresh)) {
							// Compute rotation angle
							//float theta = rotationAngle(min_k, kk, e.x, e.y);
							// System.out.println("Theta " + theta);
							//rotateGauss(min_k, jointX[min_k][kk], jointY[min_k][kk], theta, alpha);
							float theta = rotationAngle(min_k, kk, e.x, e.y);
							// System.out.println("Theta " + theta);
							rotateGauss(min_k, jointX[min_k][kk], jointY[min_k][kk], theta, rotationAlpha);

							// Move other connected Gaussians
							centerX[kk] += bondStrength*dX;
							centerY[kk] += bondStrength*dY;

							age[kk] = e.timestamp;
						}
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

						// Re-compute intersection points
						float[] joint = mainAxesIntersect(min_k,kk);
						double GP1 = gaussProb(min_k, joint[0], joint[1]);
						double GP2 = gaussProb(kk, joint[0], joint[1]);
						jointX[min_k][kk] = jointX[kk][min_k] = joint[0];
						jointY[min_k][kk] = jointY[kk][min_k] = joint[1];
						float conn_prob = (float) Math.min(GP1,GP2);
						if (conn_prob >= connectThresh) {
							connectProb[min_k][kk] = connectProb[kk][min_k] = conn_prob;
							connectAge[min_k][kk] = connectAge[kk][min_k] = e.timestamp;
						} else {
							// Only lose connection after a certain time
							if ((e.timestamp-connectAge[min_k][kk]) > minBondTime) {
								connectAge[min_k][kk] = connectAge[kk][min_k] = e.timestamp;
								connectProb[min_k][kk] = connectProb[kk][min_k] = conn_prob;
							}
						}
					}

					TrackerEvent te = new TrackerEvent();
					te.setX((short) centerX[min_k]);
					te.setY((short) centerY[min_k]);
					te.trackerID = min_k;
					te.setTimestamp((int) ts);

					BasicEvent oe=outItr.nextOutput();
					oe.copyFrom(te);
				} // if (normDist > noiseThresh)
				else {
					System.out.println("Outside event X");
					if (min_k >= 0) {
						System.out.println("Outside event");
						queueX[queuePointer] = e.x;
						queueY[queuePointer] = e.y;
						queuePointer++;
						if (queuePointer >= queueLength) {
							queuePointer=0;
						}
					}

				}
			}
		}

		// Check if random movements are necessary
		for (int i=0; i<numRegions; i++) {
			if ((max_time-age[i]) > maxAge) {
				int randIdx = (int) Math.floor(Math.random()*queueLength);
				if (randIdx >= queueLength) {
					randIdx = queueLength-1;
				}

				centerX[i] = queueX[randIdx];
				centerY[i] = queueY[randIdx];
				age[i] = max_time;

			}
		}

		return in;
	}

	@Override
	public void resetFilter() {
		filterChain.reset();
		createRegions();

		for (int i=0; i<queueLength; i++) {
			queueX[i] = (float) (Math.random() * 128.0);
			queueY[i] = (float) (Math.random() * 128.0);
		}
		queuePointer = 0;

	}

	@Override
	public void initFilter() {
		resetFilter();
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		// Plot the Gaussians
		GL2 gl=drawable.getGL().getGL2(); // gets the OpenGL GL context. Coordinates are in chip pixels, 0,0 is LL
		int i,k,kk;

		gl.glLineWidth(3); // set line width in screen pixels (not chip pixels)
		for (k=0; k<numRegions; k++) {

			gl.glColor4f(color[k][0],color[k][1],color[k][2],.5f); // set color blue
			gl.glBegin(GL.GL_LINE_LOOP); // start drawing a line loop
			for (i=0; i<=N; i++) {
				float x = (cholSigma[k][0]*circleX[i]) + (cholSigma[k][1]*circleY[i]);
				float y = (cholSigma[k][2]*circleX[i]) + (cholSigma[k][3]*circleY[i]);
				gl.glVertex2f(centerX[k]+x, centerY[k]+y);
			}
			gl.glEnd();
			// Draw axes of ellipse
			gl.glBegin(GL.GL_LINES);
			float x1 = -gaussRadius * (float)Math.sqrt(eigenValueSigma[k][0])*eigenVectorSigma[k][0];
			float x2 = -x1;
			float y1 = -gaussRadius * (float)Math.sqrt(eigenValueSigma[k][0])*eigenVectorSigma[k][2];
			float y2 = -y1;
			gl.glVertex2f(centerX[k]+x1, centerY[k]+y1);
			gl.glVertex2f(centerX[k]+x2, centerY[k]+y2);
			gl.glEnd();
			gl.glBegin(GL.GL_LINES);

			float x3 = -gaussRadius * (float)Math.sqrt(eigenValueSigma[k][1])*eigenVectorSigma[k][1];
			float x4 = -x3;
			float y3 = -gaussRadius * (float)Math.sqrt(eigenValueSigma[k][1])*eigenVectorSigma[k][3];
			float y4 = -y3;
			gl.glVertex2f(centerX[k]+x3, centerY[k]+y3);
			gl.glVertex2f(centerX[k]+x4, centerY[k]+y4);
			gl.glEnd();


			// Draw joint points
			for (kk=k+1; kk<numRegions; kk++) {
				if (connectProb[k][kk] >= connectThresh) {
					float[] joint = {jointX[k][kk], jointY[k][kk]};
					gl.glBegin(GL.GL_LINES);
					gl.glVertex2f(joint[0], joint[1]);
					gl.glVertex2f(centerX[k], centerY[k]);
					gl.glEnd();
					gl.glBegin(GL.GL_LINES);
					gl.glVertex2f(joint[0], joint[1]);
					gl.glVertex2f(centerX[kk], centerY[kk]);
					gl.glEnd();

					gl.glBegin(GL.GL_LINE_LOOP);
					gl.glVertex2f(joint[0]-2, joint[1]);
					gl.glVertex2f(joint[0], joint[1]-2);
					gl.glVertex2f(joint[0]+2, joint[1]);
					gl.glVertex2f(joint[0], joint[1]+2);
					gl.glEnd();
				}
			}


			// Draw inverse Gaussian
			/*
            // gl.glColor4f(color[k][0],color[k][1],color[k][2],.5f); // set color blue
            gl.glColor4f(1.0f,0.0f,0.0f,.5f); // set color red
            // Draw axes of ellipse
            gl.glBegin(GL2.GL_LINES);
            float ix1 = -gaussRadius * (float)Math.sqrt(eigenValueInvSigma[k][0])*eigenVectorInvSigma[k][0];
            float ix2 = -ix1;
            float iy1 = -gaussRadius * (float)Math.sqrt(eigenValueInvSigma[k][0])*eigenVectorInvSigma[k][2];
            float iy2 = -iy1;
            gl.glVertex2f((float) (centerX[k]+ix1), (float) (centerY[k]+iy1));
            gl.glVertex2f((float) (centerX[k]+ix2), (float) (centerY[k]+iy2));
            gl.glEnd();
            gl.glBegin(GL2.GL_LINES);

            float ix3 = -gaussRadius * (float)Math.sqrt(eigenValueInvSigma[k][1])*eigenVectorInvSigma[k][1];
            float ix4 = -ix3;
            float iy3 = -gaussRadius * (float)Math.sqrt(eigenValueInvSigma[k][1])*eigenVectorInvSigma[k][3];
            float iy4 = -iy3;
            gl.glVertex2f((float) (centerX[k]+ix3), (float) (centerY[k]+iy3));
            gl.glVertex2f((float) (centerX[k]+ix4), (float) (centerY[k]+iy4));
            gl.glEnd(); */
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

	public float getParallelTolerance() {
		return parallelTolerance;
	}

	synchronized public void setParallelTolerance(float parallelTolerance) {
		this.parallelTolerance = parallelTolerance;
		putFloat("parallelTolerance",parallelTolerance);
	}

	public float getConnectThresh() {
		return connectThresh;
	}

	synchronized public void setConnectThresh(float connectThresh) {
		this.connectThresh = connectThresh;
		putFloat("connectThresh", connectThresh);

	}

	public float getBondStrength() {
		return bondStrength;
	}

	synchronized public void setBondStrength(float bondStrength) {
		this.bondStrength = bondStrength;
		putFloat("bondStrength", bondStrength);
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

	public float getMinBondTime() {
		return minBondTime;
	}

	synchronized public void setMinBondTime(float minBondTime) {
		this.minBondTime = minBondTime;
		putFloat("minBondTime",minBondTime);
	}

	public float getRotationAlpha() {
		return rotationAlpha;
	}

	synchronized public void setRotationAlpha(float rotationAlpha) {
		this.rotationAlpha = rotationAlpha;
		putFloat("rotationAlpha",rotationAlpha);
	}

	public float getSigmaAlpha() {
		return sigmaAlpha;
	}

	synchronized public void setSigmaAlpha(float sigmaAlpha) {
		this.sigmaAlpha = sigmaAlpha;
		putFloat("sigmaAlpha",sigmaAlpha);
	}




}
