/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2013.ThreeDTracker;

import java.awt.geom.Point2D;
import java.util.Random;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Tracker for a single jointed object
 * @author Michael Pfeiffer
 */
public class JointTracker extends EventFilter2D implements FrameAnnotater{

	private FilterChain filterChain;  // Enclosed Gaussian Tracker filter
	private ConstrainedGroupingTracker partsTracker;

	// Number of parts
	private final int numParts = 2;

	// Gaussians per Block
	private int blockSize = getInt("blockSize", 15);

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

	// Initial positions of the two clusters
	private float initX1 = getFloat("initX1", 50.0f);
	private float initX2 = getFloat("initX2", 100.0f);
	private float initY1 = getFloat("initY1", 64.0f);
	private float initY2 = getFloat("initY2", 64.0f);

	// Main axes of the two clusters
	private float dirX1 = getFloat("dirX1", 1.0f);
	private float dirX2 = getFloat("dirX2", 1.0f);
	private float dirY1 = getFloat("dirY1", 0.0f);
	private float dirY2 = getFloat("dirY2", 0.0f);

	// Eigenvalues of the two clusters
	private float eigen11 = getFloat("eigen11", 2.0f);
	private float eigen12 = getFloat("eigen12", 1.0f);
	private float eigen21 = getFloat("eigen21", 2.0f);
	private float eigen22 = getFloat("eigen22", 1.0f);

	// Joint points
	private float jointX1 = getFloat("jointX1",1.5f);
	private float jointY1 = getFloat("jointY1",0f);
	private float jointX2 = getFloat("jointY2",-1.5f);
	private float jointY2 = getFloat("jointY2",0f);

	// Learning rate
	private float alpha = getFloat("alpha", 0.01f);
	private float sigmaAlpha = getFloat("sigmaAlpha", 0.01f);

	// Threshold for updates
	private float noiseThreshold = getFloat("noiseThreshold", 3.0f);

	// Draw outline of parts
	private boolean drawEllipses = getBoolean("drawEllipses", true);

	/** Centering force */
	protected float centerForce = getFloat("centerForce", 0.1f);

	/** Force for joining joints */
	protected float jointForce = getFloat("jointForce", 0.1f);

	// Storing the coordinates of a circle for drawing
	final int N = 20;
	private float[] circleX;
	private float[] circleY;

	private Random rand = new Random();

	public JointTracker(AEChip chip) {
		super(chip);

		partsTracker = new ConstrainedGroupingTracker(chip);
		partsTracker.setNumBlocks(numParts);
		partsTracker.setBlockSize(blockSize);
		partsTracker.setCoherenceRate(1e-5f);
		partsTracker.setColorRate(0.1f);
		partsTracker.setDirectionThresh(0.95f);
		partsTracker.setEnableReset(false);
		partsTracker.setMaxDist(30.0f);
		partsTracker.setMinConnectivity(1.0f);
		partsTracker.setMinConntime(3.0f);
		partsTracker.setUseLearning(true);
		partsTracker.setAlpha(0.1f);
		partsTracker.setKernelConst(0.05f);
		partsTracker.setKernelOffset(-0.5f);
		partsTracker.setReduceW(0.001f);
		partsTracker.setTimingAlpha(0.1f);
		partsTracker.setBetweenBlock(0.0f);
		partsTracker.setBistableTau(0.05f);
		partsTracker.setOverlapProb(9.0f);
		partsTracker.setStartw(0.0f);
		partsTracker.setUseBistable(true);
		partsTracker.setWeightThresh(0.1f);
		partsTracker.setDrawConnections(true);
		partsTracker.setDrawThresh(0.5f);
		partsTracker.setPassAllSpikes(false);


		filterChain = new FilterChain(chip);
		filterChain.add(new BackgroundActivityFilter(chip));
		filterChain.add(partsTracker);

		setEnclosedFilterChain(filterChain);


		final String part1 = "Part 1";
		final String part2 = "Part 2";
		final String draw = "Drawing", clust = "Clustering Parameters";

		setPropertyTooltip(clust,"alpha","Learning rate for cluster center updates");
			setPropertyTooltip(clust,"sigmaAlpha", "Learning rate for cluster shape updates");
			setPropertyTooltip(clust, "noiseThreshold","Threshold for Gaussians below which an"
				+ "event is considered to be noise");
			setPropertyTooltip(clust, "centerForce", "Centering force for clusters");
			setPropertyTooltip(clust, "jointForce", "Force for joining joints");

			setPropertyTooltip(part1, "initX1", "X-Position of Part 1");
			setPropertyTooltip(part1, "initY1", "Y-Position of Part 1");
			setPropertyTooltip(part1, "dirX1", "First eigenvector-X for Part 1");
			setPropertyTooltip(part1, "dirY1", "First eigenvector-Y for Part 1");
			setPropertyTooltip(part1, "eigen11", "First eigenvalue for Part 1");
			setPropertyTooltip(part1, "eigen12", "Second eigenvalue for Part 1");
			setPropertyTooltip(part1, "jointX1", "Joint point of first part (X)");
			setPropertyTooltip(part1, "jointY1", "Joint point of first part (Y)");

			setPropertyTooltip(part2, "initX2", "X-Position of Part 2");
			setPropertyTooltip(part2, "initY2", "Y-Position of Part 2");
			setPropertyTooltip(part2, "dirX2", "First eigenvector-X for Part 2");
			setPropertyTooltip(part2, "dirY2", "First eigenvector-Y for Part 2");
			setPropertyTooltip(part2, "eigen21", "First eigenvalue for Part 2");
			setPropertyTooltip(part2, "eigen22", "Second eigenvalue for Part 2");
			setPropertyTooltip(part2, "jointX2", "Joint point of second part (X)");
			setPropertyTooltip(part2, "jointY2", "Joint point of second part (Y)");

			setPropertyTooltip(draw, "drawEllipses", "Draw Ellipses of Parts");


	}

	// Computes the determinant and inverse of a matrix
	private void compute_det_inv(int k) {
		if ((k>=0) && (k<numParts)) {
			detSigma[k] = (Sigma[k][0]*Sigma[k][3])-(Sigma[k][1]*Sigma[k][2]);
			invSigma[k][0] = Sigma[k][3] / detSigma[k];
			invSigma[k][1] = -Sigma[k][1] / detSigma[k];
			invSigma[k][2] = -Sigma[k][2] / detSigma[k];
			invSigma[k][3] = Sigma[k][0] / detSigma[k];
		}
	}

	// Computes the Cholesky Matrix of Sigma
	private void cholesky(int k) {
		if ((k>=0) && (k<numParts)) {
			cholSigma[k][0] = (float) Math.sqrt(Sigma[k][0]);
			cholSigma[k][1] = 0.0f;
			cholSigma[k][2] = Sigma[k][2] / cholSigma[k][0];
			cholSigma[k][3] = (float) Math.sqrt(Sigma[k][3] - ((Sigma[k][1]*Sigma[k][1])/Sigma[k][0]));
		}
	}

	// Computes the eigenvalues and eigenvectors of Sigma
	private void eigen(int k) {
		if ((k>=0) && (k<numParts)) {
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
		if ((k>=0) && (k<numParts)) {
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

	// Generates the matrix Sigma from the eigenvectors and eigenvalues
	private void eigenGenerate(int k) {
		Sigma[k][0] = (eigenValueSigma[k][0] * eigenVectorSigma[k][0] * eigenVectorSigma[k][0]) +
			(eigenValueSigma[k][1] * eigenVectorSigma[k][1] * eigenVectorSigma[k][1]);
		Sigma[k][1] = (eigenValueSigma[k][0] * eigenVectorSigma[k][0] * eigenVectorSigma[k][2]) +
			(eigenValueSigma[k][1] * eigenVectorSigma[k][1] * eigenVectorSigma[k][3]);
		Sigma[k][2] = (eigenValueSigma[k][0] * eigenVectorSigma[k][2] * eigenVectorSigma[k][0]) +
			(eigenValueSigma[k][1] * eigenVectorSigma[k][1] * eigenVectorSigma[k][3]);
		Sigma[k][3] = (eigenValueSigma[k][0] * eigenVectorSigma[k][2] * eigenVectorSigma[k][2]) +
			(eigenValueSigma[k][1] * eigenVectorSigma[k][3] * eigenVectorSigma[k][3]);
	}

	// Samples a point from one of the Gaussians
	private Point2D sampleGaussian(int k) {
		if ((k>=0) && (k<numParts)) {
			double x = rand.nextGaussian();
			double y = rand.nextGaussian();
			Point2D p = new Point2D.Float();
			p.setLocation(centerX[k]+(cholSigma[k][0]*x)+(cholSigma[k][1]*y),
				centerY[k]+(cholSigma[k][2]*x)+(cholSigma[k][3]*y));
			return p;

		}
		else {
			return null;
		}
	}


	// Creates the variables defining the regions
	private void createRegions() {

		centerX = new float[numParts];
		centerY = new float[numParts];
		Sigma = new float[numParts][4];
		detSigma = new float[numParts];
		invSigma = new float[numParts][4];
		cholSigma = new float[numParts][4];
		eigenValueSigma = new float[numParts][2];
		eigenVectorSigma = new float[numParts][4];
		eigenValueInvSigma = new float[numParts][2];
		eigenVectorInvSigma = new float[numParts][4];

		centerX[0] = initX1;
		centerY[0] = initY1;
		centerX[1] = initX2;
		centerY[1] = initY2;

		eigenValueSigma[0][0] = eigen11;
		eigenValueSigma[0][1] = eigen12;
		eigenValueSigma[1][0] = eigen21;
		eigenValueSigma[1][1] = eigen22;

		float norm1 = (float) Math.sqrt((dirX1*dirX1) + (dirY1*dirY1));
		eigenVectorSigma[0][0] = dirX1 / norm1;
		eigenVectorSigma[0][2] = dirY1 / norm1;
		eigenVectorSigma[0][1] = -dirY1 / norm1;
		eigenVectorSigma[0][3] = dirX1 / norm1;

		float norm2 = (float) Math.sqrt((dirX2*dirX2) + (dirY2*dirY2));
		eigenVectorSigma[1][0] = dirX2 / norm2;
		eigenVectorSigma[1][2] = dirY2 / norm2;
		eigenVectorSigma[1][1] = -dirY2 / norm2;
		eigenVectorSigma[1][3] = dirX2 / norm2;

		// Create covariance matrices
		eigenGenerate(0);
		eigenGenerate(1);

		for (int i=0; i<2; i++) {
			// Compute determinant and inverse of the matrix
			compute_det_inv(i);

			// Compute Cholesky decomposition
			cholesky(i);

		}

		// System.out.println("Idx " + idx + "NR: " + numRows + "/" + numPerRow);

		// Create colors for each Gaussian
		color = new float[numParts][3];
		for (int i=0; i<numParts;i++) {
			float H = (i * 360.0f) / (numParts);
			float[] tmp = AdaptiveGaussianTracker.HSV2RGB(H, 1.0f, 1.0f);
			System.arraycopy(tmp, 0, color[i], 0, 3);
		}

		// Create drawing circle coordinates
		circleX = new float[N+1];
		circleY = new float[N+1];
		for (int i=0; i<=N; i++) {
			circleX[i] = (float) (Math.cos((i*2.0*Math.PI)/N));
			circleY[i] = (float) (Math.sin((i*2.0*Math.PI)/N));
		}

		// Initialize positions of Gaussians
		int idx = 0;
		for (int k=0; k<numParts; k++) {
			for (int i=0; i<blockSize; i++) {
				Point2D p = sampleGaussian(k);
				partsTracker.setPosition(idx, (float) p.getX(), (float) p.getY());
				if (k==0) {
					partsTracker.setColor(idx, 1, 0, 0);
				}
				else {
					partsTracker.setColor(idx, 0, 1, 0);
				}

				// Initialize weights
				int idx2 = k*blockSize;
				for (int j=0; j<blockSize; j++) {
					partsTracker.setWeight(idx, idx2, 1.0f);
					partsTracker.setFixForever(idx, idx2, 1);
					idx2++;
				}
				idx++;
			}
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

	@Override
	public void resetFilter() {
		filterChain.reset();
		createRegions();
	}

	@Override
	public void initFilter() {
		filterChain.reset();
		resetFilter();
	}

	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		if(!filterEnabled) {
			return in;
		}

		if ( in == null ){
			return null;
		}

		EventPacket<?> nextOut = getEnclosedFilterChain().filterPacket(in);

		checkOutputPacketEventType(TrackerEvent.class);

		OutputEventIterator outItr=out.outputIterator();

		int k,kk;
		float max_time = Float.MIN_VALUE;
		float[] mahalanobisD = new float[numParts];

		for (BasicEvent e : nextOut) { // iterate over all input events
			BasicEvent o=outItr.nextOutput();
		o.copyFrom(e);

		// System.out.println(e.getType());

		if (e instanceof TrackerEvent) {
			//System.out.println("Found event");
			float ts = e.timestamp;
			if (ts > max_time) {
				max_time = ts;
			}

			// Compute distance to all Gaussians
			double min_dist = Double.MAX_VALUE;
			int min_k = -1;
			for (k=0; k<numParts; k++) {
				// Check all other clusters for overlap
				float G = mahalanobisD[k] = (float) mahalanobisDist(k,e.x, e.y);

				// System.out.println(k + ": " + G + " / " + max_dist);
				if (G <= min_dist) {
					min_dist = G;
					min_k = k;
				}

			}
			if (min_k < 0) {
				System.out.println("Min " + min_k + " / " + min_dist);
			}

			TrackerEvent te = (TrackerEvent) e;

			min_k = (int) Math.floor((float) te.trackerID / blockSize);

			// Update Gaussians
			if ((min_dist>noiseThreshold) && (min_k>=0)) {
				// System.out.println(norm_dist);
				float dX = e.x - centerX[min_k];
				float dY = e.y - centerY[min_k];

				float newCenterX = (alpha*e.x) + ((1-alpha)*centerX[min_k]);
				float newCenterY = (alpha*e.y) + ((1-alpha)*centerY[min_k]);

				float[] newSigma = new float[4];

				newSigma[0] = (sigmaAlpha*(dX*dX)) + ((1-sigmaAlpha) * Sigma[min_k][0]);
				newSigma[3] = (sigmaAlpha*(dY*dY)) + ((1-sigmaAlpha) * Sigma[min_k][3]);
				newSigma[1] = (sigmaAlpha*(dX*dY)) + ((1-sigmaAlpha) * Sigma[min_k][1]);
				newSigma[2] = newSigma[1];

				// Move joints closer together
				Point2D J1 = jointPosition(0);
				Point2D J2 = jointPosition(1);

				if (min_k==0) {
					dX = (float)(J2.getX()-J1.getX());
					dY = (float)(J2.getY()-J1.getY());
				}
				else {
					dX = (float)(J1.getX()-J2.getX());
					dY = (float)(J1.getY()-J2.getY());
				}

				// Project force on both eigenvectors to obtain translation and rotation components

				// Apply translation towards joint
				float transF = (dX * eigenVectorSigma[min_k][0]) + (dY * eigenVectorSigma[min_k][2]);

				newCenterX += jointForce * transF * dX;
				newCenterY += jointForce * transF * dY;

				//System.out.println("Moved region " + min_k + " to " + newCenterX + " / " + newCenterY
				//        + " with force " + transF);

				// Apply rotation to covariance matrix
				float rotF = (dX * eigenVectorSigma[min_k][1]) + (dY * eigenVectorSigma[min_k][2]);
				rotF = rotF / (float) Math.sqrt((dX*dX)+(dY*dY));
				for (kk=0; kk<4; kk++)
				{
					Sigma[min_k][kk] = newSigma[kk];
					//rotateGauss(min_k, rotF, jointForce);
				}

				// Move center of the Gaussian if it is not too close
				// to another Gaussian
				centerX[min_k] = newCenterX;
				centerY[min_k] = newCenterY;
				compute_det_inv(min_k);
				cholesky(min_k);
				eigen(min_k);





				// Move cluster towards center of Gaussian
				partsTracker.moveTo(te.trackerID, centerX[min_k], centerY[min_k], centerForce);


			} // end update Gaussians
		}

		}

		return in;
	}

	// Rotates a Gaussian around a pivot point
	private void rotateGauss(int k, float theta, float rate) {
		if ((k>=0) && (k<numParts)) {
			double cosTheta = Math.cos(theta*rate);
			double sinTheta = Math.sqrt(1-(cosTheta*cosTheta));
			double[] R = {cosTheta, -sinTheta, sinTheta, cosTheta};

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


	private Point2D jointPosition(int k) {
		if ((k>=0) && (k<numParts)) {
			float x = 0.0f;
			if (k==0) {
				x = ((float)Math.sqrt(eigenValueSigma[k][0])*eigenVectorSigma[k][0]*jointX1) +
					((float)Math.sqrt(eigenValueSigma[k][1])*eigenVectorSigma[k][1]*jointY1);
			}
			else {
				x = ((float)Math.sqrt(eigenValueSigma[k][0])*eigenVectorSigma[k][0]*jointX2) +
					((float)Math.sqrt(eigenValueSigma[k][1])*eigenVectorSigma[k][1]*jointY2);
			}

			float y = 0.0f;
			if (k==0) {
				y = ((float)Math.sqrt(eigenValueSigma[k][0])*eigenVectorSigma[k][2]*jointX1) +
					((float)Math.sqrt(eigenValueSigma[k][1])*eigenVectorSigma[k][3]*jointY1);
			}
			else {
				y = ((float)Math.sqrt(eigenValueSigma[k][0])*eigenVectorSigma[k][2]*jointX2) +
					((float)Math.sqrt(eigenValueSigma[k][1])*eigenVectorSigma[k][3]*jointY2);
			}

			return new Point2D.Float(centerX[k]+x, centerY[k]+y);
		}
		else {
			return null;
		}
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if (drawEllipses) {
			// Plot the Gaussians
			GL2 gl=drawable.getGL().getGL2(); // gets the OpenGL GL context. Coordinates are in chip pixels, 0,0 is LL
			int i,k;

			gl.glLineWidth(3); // set line width in screen pixels (not chip pixels)
			for (k=0; k<numParts; k++) {

				//System.out.println("Ann " + k);
				gl.glColor4f(color[k][0],color[k][1],color[k][2],.5f); // set color blue
				gl.glBegin(GL.GL_LINE_LOOP); // start drawing a line loop
				for (i=0; i<=N; i++) {
					float x = (cholSigma[k][0]*circleX[i]) + (cholSigma[k][1]*circleY[i]);
					float y = (cholSigma[k][2]*circleX[i]) + (cholSigma[k][3]*circleY[i]);

					gl.glVertex2f(centerX[k]+x, centerY[k]+y);
				}
				gl.glEnd();

				// Plot joint point
				gl.glColor4f(1.0f, 1.0f, 0.0f, 0.5f);
				Point2D joint = jointPosition(k);
				float x = (float) joint.getX();
				float y = (float) joint.getY();

				gl.glBegin(GL.GL_LINE_LOOP);
				gl.glVertex2f(x-1.0f,y);
				gl.glVertex2f(x,y+1.0f);
				gl.glVertex2f(x+1.0f,y);
				gl.glVertex2f(x,y-1.0f);
				gl.glEnd();
			}
		}
	}


	public float getDirX1() {
		return dirX1;
	}

	synchronized public void setDirX1(float dirX1) {
		this.dirX1 = dirX1;
		putFloat("dirX1", dirX1);
		createRegions();
	}

	public float getDirX2() {
		return dirX2;
	}

	synchronized public void setDirX2(float dirX2) {
		this.dirX2 = dirX2;
		putFloat("dirX2", dirX2);
		createRegions();
	}

	public float getDirY1() {
		return dirY1;
	}

	synchronized public void setDirY1(float dirY1) {
		this.dirY1 = dirY1;
		putFloat("dirY1", dirY1);
		createRegions();
	}

	public float getDirY2() {
		return dirY2;
	}

	synchronized public void setDirY2(float dirY2) {
		this.dirY2 = dirY2;
		putFloat("dirY2", dirY2);
		createRegions();
	}

	public float getEigen11() {
		return eigen11;
	}

	synchronized public void setEigen11(float eigen11) {
		this.eigen11 = eigen11;
		putFloat("eigen11", eigen11);
		createRegions();
	}

	public float getEigen12() {
		return eigen12;
	}

	synchronized public void setEigen12(float eigen12) {
		this.eigen12 = eigen12;
		putFloat("eigen12", eigen12);
		createRegions();
	}

	public float getEigen21() {
		return eigen21;
	}

	synchronized public void setEigen21(float eigen21) {
		this.eigen21 = eigen21;
		putFloat("eigen21", eigen21);
		createRegions();
	}

	public float getEigen22() {
		return eigen22;
	}

	synchronized public void setEigen22(float eigen22) {
		this.eigen22 = eigen22;
		putFloat("eigen22", eigen22);
		createRegions();
	}

	public float getInitX1() {
		return initX1;
	}

	synchronized public void setInitX1(float initX1) {
		this.initX1 = initX1;
		putFloat("initX1", initX1);
		createRegions();
	}

	public float getInitX2() {
		return initX2;
	}

	synchronized public void setInitX2(float initX2) {
		this.initX2 = initX2;
		putFloat("initX2", initX2);
		createRegions();
	}

	public float getInitY1() {
		return initY1;
	}

	synchronized public void setInitY1(float initY1) {
		this.initY1 = initY1;
		putFloat("initY1", initY1);
		createRegions();
	}

	public float getInitY2() {
		return initY2;
	}

	synchronized public void setInitY2(float initY2) {
		this.initY2 = initY2;
		putFloat("initY2", initY2);
		createRegions();
	}

	public boolean isDrawEllipses() {
		return drawEllipses;
	}

	synchronized public void setDrawEllipses(boolean drawEllipses) {
		this.drawEllipses = drawEllipses;
		putBoolean("drawEllipses", drawEllipses);
	}

	public float getSigmaAlpha() {
		return sigmaAlpha;
	}

	synchronized public void setSigmaAlpha(float sigmaAlpha) {
		this.sigmaAlpha = sigmaAlpha;
		putFloat("sigmaAlpha", sigmaAlpha);
	}

	public float getNoiseThreshold() {
		return noiseThreshold;
	}

	synchronized public void setNoiseThreshold(float noiseThreshold) {
		this.noiseThreshold = noiseThreshold;
		putFloat("noiseThreshold", noiseThreshold);
	}

	public float getAlpha() {
		return alpha;
	}

	synchronized public void setAlpha(float alpha) {
		this.alpha = alpha;
		putFloat("alpha", alpha);
	}

	public float getCenterForce() {
		return centerForce;
	}

	synchronized public void setCenterForce(float centerForce) {
		this.centerForce = centerForce;
		putFloat("centerForce", centerForce);
	}

	public float getJointX1() {
		return jointX1;
	}

	synchronized public void setJointX1(float jointX1) {
		this.jointX1 = jointX1;
		putFloat("jointX1", jointX1);
	}

	public float getJointX2() {
		return jointX2;
	}

	synchronized public void setJointX2(float jointX2) {
		this.jointX2 = jointX2;
		putFloat("jointX2", jointX2);
	}

	public float getJointY1() {
		return jointY1;
	}

	synchronized public void setJointY1(float jointY1) {
		this.jointY1 = jointY1;
		putFloat("jointY1", jointY1);
	}

	public float getJointY2() {
		return jointY2;
	}

	synchronized public void setJointY2(float jointY2) {
		this.jointY2 = jointY2;
		putFloat("jointY2", jointY2);
	}

	public float getJointForce() {
		return jointForce;
	}

	synchronized public void setJointForce(float jointForce) {
		this.jointForce = jointForce;
		putFloat("jointForce", jointForce);
	}


}
