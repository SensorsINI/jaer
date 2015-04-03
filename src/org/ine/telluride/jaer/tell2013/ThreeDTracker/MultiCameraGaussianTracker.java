/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.tell2013.ThreeDTracker;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Low-level Tracker for small Gaussians with full covariance matrix.
 * @author Michael Pfeiffer, modified by Dan Neil
 */
public class MultiCameraGaussianTracker extends EventFilter2D implements FrameAnnotater{
    // Geometry Builder
    // ----------------------------------------------------------------------------------

    private float xGeoMean, yGeoMean, zGeoMean;
    private float xGeoStd, yGeoStd, zGeoStd;

    // FIXME - make user editable
    private float geoMixRate = 0.01f;

    // Add in cluster destroy
    private boolean geometryLocked = false;
    private int geoWeight = 0;
    private int geoThresh = 1000000;


    // Orientation builder
    // ---------------------------------------------------

    // Number of regions per camera
    private int numRegions = getInt("numRegions", 1);

    // Learning rate for online updates
    private float alpha = getFloat("alpha", 0.001f);

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

    // Check the number of cameras
    private final int NUMCAMS = 2;

    // Centers and covariances of the regions
    // Three dimensions: cameras * clusters * dimensionality
    // Centers and covariances of the regions
    private float[][] centerX, centerY;
    private float[][][] Sigma;
    private float[][] detSigma;
    private float[][][] invSigma;
    private float[][] color;
    private float[][][] cholSigma;
    private float[][][] eigenValueSigma;
    private float[][][] eigenVectorSigma;
    private float[][][] eigenValueInvSigma;
    private float[][][] eigenVectorInvSigma;

    // Last movements of each cluster
    private float[][] lastMovement;

    // Storing the coordinates of a circle for drawing
    final int N = 20;
    private float[] circleX;
    private float[] circleY;

    // Queue of random positions
    float[][] queueX;
    float[][] queueY;
    int[] queuePointer;

    // Age of each cluster
    float[][] age;

    // Save orientation
    float xrot, yrot, zrot;

    private float rotMixRate = 0.001f;

    public MultiCameraGaussianTracker(AEChip chip) {
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

        queueX = new float[2][];
        queueY = new float[2][];
        queuePointer = new int[NUMCAMS];
        for (int c=0; c<NUMCAMS; c++){
            queueX[c] = new float[queueLength];
            queueY[c] = new float[queueLength];
            queuePointer[c] = 0;
        }


        // Initialize Geometry
        xGeoMean = chip.getSizeX()/2;
        yGeoMean = chip.getSizeY()/2;
        xGeoMean = chip.getSizeX()/2;
        xGeoStd = 10;
        yGeoStd = 10;
        zGeoStd = 10;

    }

    // Computes the determinant and inverse of a matrix
    private void compute_det_inv(int k, int camera) {
        if ((k>=0) && (k<numRegions)  && (camera < NUMCAMS)) {
            detSigma[camera][k] = (Sigma[camera][k][0]*Sigma[camera][k][3])-(Sigma[camera][k][1]*Sigma[camera][k][2]);
            invSigma[camera][k][0] = Sigma[camera][k][3] / detSigma[camera][k];
            invSigma[camera][k][1] = -Sigma[camera][k][1] / detSigma[camera][k];
            invSigma[camera][k][2] = -Sigma[camera][k][2] / detSigma[camera][k];
            invSigma[camera][k][3] = Sigma[camera][k][0] / detSigma[camera][k];
        }
    }

    // Computes the Cholesky Matrix of Sigma
    private void cholesky(int k, int camera) {
        if ((k>=0) && (k<numRegions) && (camera < NUMCAMS)) {
            cholSigma[camera][k][0] = (float) Math.sqrt(Sigma[camera][k][0]);
            cholSigma[camera][k][1] = 0.0f;
            cholSigma[camera][k][2] = Sigma[camera][k][2] / cholSigma[camera][k][0];
            cholSigma[camera][k][3] = (float) Math.sqrt(Sigma[camera][k][3] - ((Sigma[camera][k][1]*Sigma[camera][k][1])/Sigma[camera][k][0]));
        }
    }

    // Computes the eigenvalues and eigenvectors of Sigma
    private void eigen(int k, int camera) {
        if ((k>=0) && (k<numRegions) && (camera < NUMCAMS)) {
            float T = Sigma[camera][k][0]+Sigma[camera][k][3];
            float D = ((T*T)/4.0f)-detSigma[camera][k];
            if (D<0) {
				D = 0f;
			}
            eigenValueSigma[camera][k][0] = (T/2.0f) + (float)Math.sqrt(D);
            eigenValueSigma[camera][k][1] = (T/2.0f) - (float)Math.sqrt(D);

            if (Math.abs(Sigma[camera][k][2])>0) {
                eigenVectorSigma[camera][k][0] = eigenValueSigma[camera][k][0]-Sigma[camera][k][3];
                eigenVectorSigma[camera][k][1] = eigenValueSigma[camera][k][1]-Sigma[camera][k][3];
                eigenVectorSigma[camera][k][2] = Sigma[camera][k][2];
                eigenVectorSigma[camera][k][3] = Sigma[camera][k][2];
            } else if (Math.abs(Sigma[camera][k][1])>0) {
                eigenVectorSigma[camera][k][0] = Sigma[camera][k][1];
                eigenVectorSigma[camera][k][1] = Sigma[camera][k][1];
                eigenVectorSigma[camera][k][2] = eigenValueSigma[camera][k][0]-Sigma[camera][k][0];
                eigenVectorSigma[camera][k][3] = eigenValueSigma[camera][k][1]-Sigma[camera][k][0];
            } else {
                eigenVectorSigma[camera][k][0] = eigenVectorSigma[camera][k][3] = 1.0f;
                eigenVectorSigma[camera][k][1] = eigenVectorSigma[camera][k][2] = 0.0f;
            }
            // Normalize the vector
            float d1 = (float) Math.sqrt(Math.pow(eigenVectorSigma[camera][k][0],2)+
                        Math.pow(eigenVectorSigma[camera][k][2],2));
            float d2 = (float) Math.sqrt(Math.pow(eigenVectorSigma[camera][k][1],2)+
                        Math.pow(eigenVectorSigma[camera][k][3],2));
            eigenVectorSigma[camera][k][0] /= d1;
            eigenVectorSigma[camera][k][2] /= d1;
            eigenVectorSigma[camera][k][1] /= d2;
            eigenVectorSigma[camera][k][3] /= d2;
        }
    }

    // Computes the eigenvalues and eigenvectors of the inverse of Sigma
    private void invEigen(int k, int camera) {
        if ((k>=0) && (k<numRegions) && (camera < NUMCAMS)) {
            float T = invSigma[camera][k][0]+invSigma[camera][k][3];
            float D = (invSigma[camera][k][0]*invSigma[camera][k][3])-(invSigma[camera][k][1]*invSigma[camera][k][2]);
            eigenValueInvSigma[camera][k][0] = 1.0f / eigenValueSigma[camera][k][0];
            eigenValueInvSigma[camera][k][1] = 1.0f / eigenValueSigma[camera][k][1];

            if (Math.abs(invSigma[camera][k][2])>0) {
                eigenVectorInvSigma[camera][k][0] = eigenValueInvSigma[camera][k][0]-invSigma[camera][k][3];
                eigenVectorInvSigma[camera][k][1] = eigenValueInvSigma[camera][k][1]-invSigma[camera][k][3];
                eigenVectorInvSigma[camera][k][2] = invSigma[camera][k][2];
                eigenVectorInvSigma[camera][k][3] = invSigma[camera][k][2];
            } else if (Math.abs(invSigma[camera][k][1])>0) {
                eigenVectorInvSigma[camera][k][0] = invSigma[camera][k][1];
                eigenVectorInvSigma[camera][k][1] = invSigma[camera][k][1];
                eigenVectorInvSigma[camera][k][2] = eigenValueInvSigma[camera][k][0]-invSigma[camera][k][0];
                eigenVectorInvSigma[camera][k][3] = eigenValueInvSigma[camera][k][1]-invSigma[camera][k][0];
            } else {
                eigenVectorInvSigma[camera][k][0] = eigenVectorInvSigma[camera][k][3] = 1.0f;
                eigenVectorInvSigma[camera][k][1] = eigenVectorInvSigma[camera][k][2] = 0.0f;
            }
            // Normalize the vector
            float d1 = (float) Math.sqrt(Math.pow(eigenVectorInvSigma[camera][k][0],2)+
                        Math.pow(eigenVectorInvSigma[camera][k][2],2));
            float d2 = (float) Math.sqrt(Math.pow(eigenVectorInvSigma[camera][k][1],2)+
                        Math.pow(eigenVectorInvSigma[camera][k][3],2));
            eigenVectorInvSigma[camera][k][0] /= d1;
            eigenVectorInvSigma[camera][k][2] /= d1;
            eigenVectorInvSigma[camera][k][1] /= d2;
            eigenVectorInvSigma[camera][k][3] /= d2;
        }
    }


    // Creates the variables defining the regions
    private void createRegions() {

        centerX = new float[NUMCAMS][numRegions];
        centerY = new float[NUMCAMS][numRegions];
        Sigma = new float[NUMCAMS][numRegions][4];
        detSigma = new float[NUMCAMS][numRegions];
        invSigma = new float[NUMCAMS][numRegions][4];
        cholSigma = new float[NUMCAMS][numRegions][4];
        eigenValueSigma = new float[NUMCAMS][numRegions][2];
        eigenVectorSigma = new float[NUMCAMS][numRegions][4];
        eigenValueInvSigma = new float[NUMCAMS][numRegions][2];
        eigenVectorInvSigma = new float[NUMCAMS][numRegions][4];

        lastMovement = new float[numRegions][2];

        age = new float[NUMCAMS][numRegions];

        // Initialize Region positions

        // Compute the number of Gaussians (regular grid)
        int numPerRow = (int) Math.ceil(numRegions / Math.sqrt(numRegions));
        int numRows = (int) Math.ceil(numRegions / (float) numPerRow);

        // Initialize Gaussian positions
        float xpos = 2.0f*gaussRadius;
        float ypos = 2.0f*gaussRadius;

        float xdiff = (128.0f - (4.0f*gaussRadius)) / numPerRow;
        float ydiff = (128.0f - (4.0f*gaussRadius)) / numRows;

        for (int c=0; c<NUMCAMS; c++){

            int idx = 0;

            for (int i=0; i<numRows; i++) {
                for (int j=0; j<numPerRow; j++) {
                    centerX[c][idx] = xpos;
                    centerY[c][idx] = ypos;

                    age[c][idx] = 0;

                    xpos += xdiff;

                    Sigma[c][idx] = new float[4];
                    Sigma[c][idx][0] = initRadius;
                    Sigma[c][idx][1] = 0.1f;
                    Sigma[c][idx][2] = 0.1f;
                    Sigma[c][idx][3] = initRadius;

                    // Compute determinant and inverse of the matrix
                    compute_det_inv(idx, c);

                    // Compute Cholesky decomposition
                    cholesky(idx, c);

                    // Compute eigenvalues and eigenvectors
                    eigen(idx, c);

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
        }

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
    public double gaussProb(int k, float X, float Y, int camera) {
        float dX = X - centerX[camera][k];
        float dY = Y - centerY[camera][k];

        float distX = (invSigma[camera][k][0]*dX) + (invSigma[camera][k][1]*dY);
        float distY = (invSigma[camera][k][2]*dX) + (invSigma[camera][k][3]*dY);

        double dist = (dX*distX) + (dY*distY);
        double G = (norm_const / Math.sqrt(detSigma[camera][k])) *
                        Math.exp(-0.5*dist);

        return G;
    }

    // Computes Mahalanobis distance between Gaussian and a point
    public double mahalanobisDist(int k, float X, float Y, int camera) {
        float dX = X - centerX[camera][k];
        float dY = Y - centerY[camera][k];

        float distX = (invSigma[camera][k][0]*dX) + (invSigma[camera][k][1]*dY);
        float distY = (invSigma[camera][k][2]*dX) + (invSigma[camera][k][3]*dY);

        double dist = (dX*distX) + (dY*distY);
        return dist;

    }


    // Rotates a Gaussian abround a pivot point
    private void rotateGauss(int k, float X, float Y, float theta, float rate, int camera) {
        if ((k>=0) && (k<numRegions) && (camera < NUMCAMS)) {
            double dX = centerX[camera][k] - X;
            double dY = centerY[camera][k] - Y;
            double cosTheta = Math.cos(theta*rate);
            double sinTheta = Math.sqrt(1-(cosTheta*cosTheta));
            double[] R = {cosTheta, -sinTheta, sinTheta, cosTheta};


            // Rotate center
            float nX = (float) ((R[0]*dX) + (R[1]*dY));
            float nY = (float) ((R[2]*dX) + (R[3]*dY));

            centerX[camera][k] = X+nX;
            centerY[camera][k] = Y+nY;

            // Rotate covariance matrix: R^-1 * Sigma * R
            Sigma[camera][k][0] = (float)((Sigma[camera][k][0]*R[0]) + (Sigma[camera][k][1]*R[2]));
            Sigma[camera][k][1] = (float)((Sigma[camera][k][0]*R[1]) + (Sigma[camera][k][1]*R[3]));
            Sigma[camera][k][2] = (float)((Sigma[camera][k][2]*R[0]) + (Sigma[camera][k][3]*R[2]));
            Sigma[camera][k][3] = (float)((Sigma[camera][k][2]*R[1]) + (Sigma[camera][k][3]*R[3]));

            Sigma[camera][k][0] = (float)((R[0]*Sigma[camera][k][0]) + (R[2]*Sigma[camera][k][2]));
            Sigma[camera][k][1] = (float)((R[0]*Sigma[camera][k][1]) + (R[2]*Sigma[camera][k][3]));
            Sigma[camera][k][2] = (float)((R[1]*Sigma[camera][k][0]) + (R[3]*Sigma[camera][k][2]));
            Sigma[camera][k][3] = (float)((R[1]*Sigma[camera][k][1]) + (R[3]*Sigma[camera][k][3]));
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
        checkOutputPacketEventType(MultiCameraEvent.class);

        OutputEventIterator outItr=out.outputIterator();

        float max_time = Float.MIN_VALUE;

        int k, kk;

        float[] mahalanobisD = new float[numRegions];

        for (Object ein : in) { // iterate over all input events
            BasicEvent e = (BasicEvent) ein;

            if(!(e instanceof MultiCameraEvent)) {
				continue;
			}

            // First, pass through all spikes as required
            if (passAllSpikes) {
                BasicEvent o=outItr.nextOutput();
                o.copyFrom(e);
            }

            float ts = e.timestamp;
            if (ts > max_time) {
				max_time = ts;
			}

            // Compute distance to all Gaussians
            double max_dist = Double.MIN_VALUE;

            // FIXME K SWEEP
            int min_k = 0;
            float sum_dist = 0.0f;

            int camera = ((MultiCameraEvent) e).getCamera();

            // Update Gaussians
            float dX = e.x - centerX[camera][min_k];
            float dY = e.y - centerY[camera][min_k];

            age[camera][min_k] = e.timestamp;

            float newCenterX = (alpha*e.x) + ((1-alpha)*centerX[camera][min_k]);
            float newCenterY = (alpha*e.y) + ((1-alpha)*centerY[camera][min_k]);

            // Store last movement
            lastMovement[min_k][0] += alpha*dX;
            lastMovement[min_k][1] += alpha*dY;

            float[] newSigma = new float[4];

            newSigma[0] = (sigmaAlpha*(dX*dX)) + ((1-sigmaAlpha) * Sigma[camera][min_k][0]);
            newSigma[3] = (sigmaAlpha*(dY*dY)) + ((1-sigmaAlpha) * Sigma[camera][min_k][3]);
            newSigma[1] = (sigmaAlpha*(dX*dY)) + ((1-sigmaAlpha) * Sigma[camera][min_k][1]);
            newSigma[2] = newSigma[1];

            // Move center of the Gaussian if it is not too close
            // to another Gaussian
            centerX[camera][min_k] = newCenterX;
            centerY[camera][min_k] = newCenterY;
            for (kk=0; kk<4; kk++) {
				Sigma[camera][min_k][kk] = newSigma[kk];
			}

            compute_det_inv(min_k, camera);
            cholesky(min_k, camera);
            eigen(min_k, camera);

            // Check if region becomes too large, then move randomly
            /*
            if (detSigma[camera][min_k] > maxEigenProduct) {
                moveRandom(min_k, max_time, camera);
            }
            */

            // Update Geometry
            if(!geometryLocked){
                geoWeight = geoWeight + 1;
                if(camera == 0){
                    xGeoStd  = ((1 - geoMixRate) * xGeoStd) + (Math.abs(e.x - xGeoMean) * geoMixRate);
                }
                else{
                    zGeoStd  = ((1 - geoMixRate) * zGeoStd) + (Math.abs(e.x - zGeoMean) * geoMixRate);
                }
                yGeoStd  = ((1 - geoMixRate) * yGeoStd) + (Math.abs(e.y - yGeoMean) * geoMixRate);

                xGeoStd = (xGeoStd > 30) ? 30 : xGeoStd;
                yGeoStd = (yGeoStd > 30) ? 30 : yGeoStd;
                zGeoStd = (zGeoStd > 30) ? 30 : zGeoStd;

                if(geoWeight > geoThresh){
                    geometryLocked = false;


                }
            }

            // Update point location
            if(camera == 0) {
				xGeoMean = ((1 - geoMixRate) * xGeoMean) + (e.x * geoMixRate);
			}
			else {
				zGeoMean = ((1 - geoMixRate) * zGeoMean) + (e.x * geoMixRate);
			}
            yGeoMean = ((1 - geoMixRate) * yGeoMean) + (e.y * geoMixRate);

            // Update rotations
            /*if(Math.abs(e.x - xGeoMean) > 2){
                if(camera == 0)
                    zrot = (float) ( (1-rotMixRate) * zrot + rotMixRate * Math.toDegrees(Math.atan((e.y-yGeoMean) / (e.x - xGeoMean))));
                else
                    xrot = (float) ( (1-rotMixRate) * xrot + rotMixRate * Math.toDegrees(Math.atan((e.y-yGeoMean) / (e.x - xGeoMean))));
            }*/
        }

        // Destroy cluster if events stop
        /*
        for(int c=0; c<NUMCAMS; c++){
            for (int i=0; i<numRegions; i++) {
                if ((max_time-age[c][i]) > maxAge) {
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
        }
        */
        return out;
    }

    // Sets a cluster to a new random position
    public void moveRandom(int k, float max_time, int camera) {
        // Move to position in the target queue
        int targetIdx = (int) (queueLength * Math.random());
        if (targetIdx >= queueLength) {
			targetIdx = queueLength-1;
		}
        centerX[camera][k] = queueX[camera][targetIdx];
        centerY[camera][k] = queueY[camera][targetIdx];

        age[camera][k] = max_time;

        Sigma[camera][k][0] = initRadius;
        Sigma[camera][k][1] = 0.1f;
        Sigma[camera][k][2] = 0.1f;
        Sigma[camera][k][3] = initRadius;

        compute_det_inv(k, camera);
        cholesky(k, camera);
        eigen(k, camera);

        lastMovement[k][0] = (float) ((0.2 * Math.random()) - 0.1);
        lastMovement[k][1] = (float) ((0.2 * Math.random()) - 0.1);

    }


    @Override
    public void resetFilter() {
        if (enableReset) {
            // Reset the geoemtry
            geometryLocked = false;
            geoWeight = 0;

            createRegions();

            for (int c=0; c < NUMCAMS; c++){
                for (int i=0; i<queueLength; i++) {
                    queueX[c][i] = (float) (Math.random() * 128.0);
                    queueY[c][i] = (float) (Math.random() * 128.0);
                }
                queuePointer[c] = 0;
            }
        }
    }

    @Override
    public void initFilter() {
        resetFilter();
        // Initialize Geometry
        xGeoMean = chip.getSizeX()/2;
        yGeoMean = chip.getSizeY()/2;
        xGeoMean = chip.getSizeX()/2;
        xGeoStd = 10;
        yGeoStd = 10;
        zGeoStd = 10;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        // Plot the Gaussians
        GL2 gl = drawable.getGL().getGL2(); // gets the OpenGL GL context. Coordinates are in chip pixels, 0,0 is LL
        Chip2D chip = this.getChip();
        gl.glPushMatrix();

        if (drawEllipses) {
            // rotate and align viewpoint for filters
            gl.glRotatef(chip.getCanvas().getAngley(), 0, 1, 0); // rotate viewpoint by angle deg around the upvector
            gl.glRotatef(chip.getCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the upvector
            gl.glTranslatef(chip.getCanvas().getOrigin3dx(), chip.getCanvas().getOrigin3dy(), 0);

            int i,k,kk;

            gl.glLineWidth(3); // set line width in screen pixels (not chip pixels)

            float x,y,z;
            for (int camera=0; camera<2; camera++){
                k = 0;
                gl.glPushMatrix();

                // Orient Properly for this camera
                gl.glColor4f(1.0f, 1.0f, 0.0f, 0.5f);
                if(camera == 0) {
					gl.glTranslatef(centerX[camera][k], centerY[camera][k], 0.0f);
				}
				else {
					gl.glTranslatef(0.0f, centerY[camera][k], centerX[camera][k]);
				}

                // Draw Ellipse
                gl.glColor4f(1.0f, 1.0f, 0.0f,.5f);
                gl.glBegin(GL.GL_LINE_LOOP); // start drawing a line loop
                for (i=0; i<=N; i++) {
                    x = (cholSigma[camera][k][0]*circleX[i]) + (cholSigma[camera][k][1]*circleY[i]);
                    y = (cholSigma[camera][k][2]*circleX[i]) + (cholSigma[camera][k][3]*circleY[i]);

                    if (camera == 0) {
						gl.glVertex3f(x, y, 0.0f);
					}
					else {
						gl.glVertex3f(0.0f, y, x);
					}
                }
                gl.glEnd();

                // Draw axes of ellipse
                // Major Axis
                gl.glBegin(GL.GL_LINES);
                float x1 = gaussRadius * (float)Math.sqrt(eigenValueSigma[camera][k][0])*eigenVectorSigma[camera][k][0];
                float y1 = gaussRadius * (float)Math.sqrt(eigenValueSigma[camera][k][0])*eigenVectorSigma[camera][k][2];
                x = (camera==0) ?   x1 : 0.0f;
                y = (camera==0) ?   y1 : y1;
                z = (camera==0) ? 0.0f : x1;
                //gl.glVertex3f(x, y, z);
                //gl.glVertex3f(-x, -y, -z);
                gl.glEnd();

                // Minor Axis
                gl.glBegin(GL.GL_LINES);
                float x2 = gaussRadius * (float)Math.sqrt(eigenValueSigma[camera][k][1])*eigenVectorSigma[camera][k][1];
                float y2 = gaussRadius * (float)Math.sqrt(eigenValueSigma[camera][k][1])*eigenVectorSigma[camera][k][3];
                x = (camera==0) ?   x2 : 0.0f;
                y = (camera==0) ?   y2 : y2;
                z = (camera==0) ? 0.0f : x2;
                //gl.glVertex3f(x, y, z);
                //gl.glVertex3f(-x, -y, -z);
                gl.glEnd();

                gl.glPopMatrix();
            }
            k = 0;
            x = centerX[0][k];
            y = (centerY[1][k]+centerY[0][k])/2;
            z = centerX[1][k];

            float objWidth, objHeight, objDepth;

            // Major axis is x
            objWidth  = 2 * (float) Math.sqrt(eigenValueSigma[0][0][0]);
            objHeight = 2 * (float) Math.sqrt(eigenValueSigma[0][0][1]);
            objDepth  = 2 * (float) Math.sqrt(eigenValueSigma[1][0][0]);

            drawPrism(x, y, z,
                      objWidth, objHeight, objDepth,
                      (float) Math.toDegrees(-Math.atan(eigenVectorSigma[1][0][2]/eigenVectorSigma[1][0][0])),
                      0.0f,
                      (float) Math.toDegrees(Math.atan(eigenVectorSigma[0][0][2]/eigenVectorSigma[0][0][0])),
                      gl);
        }
        gl.glPopMatrix();
    }

    // Match the sizes of the ellipses and pick out whether minor or major is new on second cam
    public int findUnique(){
        float amajx = gaussRadius * (float)Math.sqrt(eigenValueSigma[0][0][0])*eigenVectorSigma[0][0][0];
        float amajy = gaussRadius * (float)Math.sqrt(eigenValueSigma[0][0][0])*eigenVectorSigma[0][0][2];
        float bmajx = gaussRadius * (float)Math.sqrt(eigenValueSigma[1][0][0])*eigenVectorSigma[1][0][0];
        float bmajy = gaussRadius * (float)Math.sqrt(eigenValueSigma[1][0][0])*eigenVectorSigma[1][0][2];

        float aminx = gaussRadius * (float)Math.sqrt(eigenValueSigma[0][0][1])*eigenVectorSigma[0][0][1];
        float aminy = gaussRadius * (float)Math.sqrt(eigenValueSigma[0][0][1])*eigenVectorSigma[0][0][3];
        float bminx = gaussRadius * (float)Math.sqrt(eigenValueSigma[1][0][1])*eigenVectorSigma[1][0][1];
        float bminy = gaussRadius * (float)Math.sqrt(eigenValueSigma[1][0][1])*eigenVectorSigma[1][0][3];

        return 0;
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

    synchronized public void setQueueLength(int queueLength, int camera) {
        this.queueLength = queueLength;
        putInt("queueLength", queueLength);

        queueX[camera] = new float[queueLength];
        queueY[camera] = new float[queueLength];

        for (int i=0; i<queueLength; i++) {
            queueX[camera][i] = (float) (Math.random() * 128.0);
            queueY[camera][i] = (float) (Math.random() * 128.0);
        }
        queuePointer[camera] = 0;
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
    synchronized public float getCenterX(int k, int camera) {
        if ((k>=0) && (k<numRegions)) {
			return centerX[camera][k];
		}
		else {
			return -1.0f;
		}
    }

    // Returns the center y-coordinate for a cluster
    synchronized public float getCenterY(int k, int camera) {
        if ((k>=0) && (k<numRegions)) {
			return centerY[camera][k];
		}
		else {
			return -1.0f;
		}
    }

    // Moves a center in the direction of a target point
    synchronized public int moveTo(int k, float targetX, float targetY, float rate, int camera) {
        if ((k>=0) && (k<numRegions) && (camera < NUMCAMS)) {
            float diffX = targetX - centerX[camera][k];
            float diffY = targetY - centerY[camera][k];
            centerX[camera][k] += rate * diffX;
            centerY[camera][k] += rate * diffY;

            lastMovement[k][0] += alpha * rate * diffX;
            lastMovement[k][1] += alpha * rate * diffY;
            return 1;
        } else {
            return 0;
        }
    }

        // Sets the age of a cluster
    synchronized public void setAge(int k, float newAge, int camera) {
        if ((k>=0) && (k<numRegions)) {
			age[camera][k] = newAge;
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
        if ((k>=0) && (k<this.numRegions)) {
			return lastMovement[k][0];
		}
		else {
			return 0.0f;
		}
    }

    public float getLastMovementY(int k) {
        if ((k>=0) && (k<this.numRegions)) {
			return lastMovement[k][1];
		}
		else {
			return 0.0f;
		}
    }

    public void setColor(int k, float r, float g, float b) {
        if ((k>=0) && (k<this.numRegions)) {
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


    void drawSphere(float x, float y, float z,
                    float xscale, float yscale, float zscale)
    {


    }


     void drawPrism(float x, float y, float z,
                    float width, float height, float depth,
                    float xang, float yang, float zang,
                    GL2 gl)
     {
        width  = width/2;
        height = height/2;
        depth  = depth/2;
        gl.glPushMatrix();
        gl.glTranslatef(x,y,z);
        gl.glRotatef(xang, 1, 0, 0); // rotate viewpoint by angle deg around the upvector
        gl.glRotatef(yang, 0, 1, 0); // rotate viewpoint by angle deg around the upvector
        gl.glRotatef(zang, 0, 0, 1); // rotate viewpoint by angle deg around the upvector
        gl.glBegin(GL2.GL_QUADS);

        gl.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
        gl.glVertex3f(-width, -height, -depth);
        gl.glVertex3f(-width, -height, depth);
        gl.glVertex3f(-width, height, depth);
        gl.glVertex3f(-width, height, -depth);

        gl.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
        gl.glVertex3f(-width, -height, depth);
        gl.glVertex3f(width, -height, depth);
        gl.glVertex3f(width, height, depth);
        gl.glVertex3f(-width, height, depth);

        gl.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
        gl.glVertex3f(width, -height, depth);
        gl.glVertex3f(width, -height, -depth);
        gl.glVertex3f(width, height, -depth);
        gl.glVertex3f(width, height, depth);

        gl.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
        gl.glVertex3f(width, -height, -depth);
        gl.glVertex3f(-width, -height, -depth);
        gl.glVertex3f(-width, height, -depth);
        gl.glVertex3f(width, height, -depth);

        gl.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
        gl.glVertex3f(-width, height, -depth);
        gl.glVertex3f(-width, height, depth);
        gl.glVertex3f(width, height, depth);
        gl.glVertex3f(width, height, -depth);

        gl.glEnd();
        gl.glPopMatrix();
    }
}