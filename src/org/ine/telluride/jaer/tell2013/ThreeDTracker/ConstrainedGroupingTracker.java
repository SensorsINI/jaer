/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.tell2013.ThreeDTracker;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * A filter that groups inputs from a lower-level Gaussian Tracker, and via
 * Hebbian learning learns to connect clusters into higher-level groups that
 * represent body parts, objects, etc.
 *
 * @author Michael Pfeiffer, Ryad Benosman
 */
@Description("Groups multiple Gaussian Trackers") // adds this string as description of class for jaer GUIs
public class ConstrainedGroupingTracker extends EventFilter2D implements FrameAnnotater {

	private FilterChain filterChain;  // Enclosed Gaussian Tracker filter
	private FullGaussianTracker gaussTracker;

	// Number of gaussian trackers
	private int numGaussians = 0;

	// Last two spike times of every neuron
	private long spiketimes[][] = null;

	// Recurrent weights between neurons
	private float w[][] = null;

	// Number of spikes per cluster
	private float nSpikes[] = null;

	private boolean passAllSpikes = getBoolean("passAllSpikes", true);

	/** Start weights for recurrent connections */
	protected float startw = getFloat("startw", 0.0f);

	/** Size of receptive field */
	protected float overlapProb = getFloat("overlapProb",1.0f);

	/** Coherence learning rate */
	protected float coherenceRate = getFloat("coherenceRate", 1e-7f);

	/** Color update rate */
	protected float colorRate = getFloat("colorRate",0.01f);

	/** Synaptic learning rate */
	protected float alpha = getFloat("alpha",0.001f);

	/** Weight reduction for neurons firing in different receptive fields */
	protected float reduceW = getFloat("reduceW", 0.001f);

	/** Draw connections between cluster centers */
	protected boolean drawConnections = getBoolean("drawConnections", true);

	/** Use learned weights for spring-forces */
	protected boolean useLearning = getBoolean("useLearning",true);

	/** Drawing threshold */
	protected float drawThresh = getFloat("drawThresh", 0.5f);

	/** Kernel time constant for update */
	protected float kernelConst = getFloat("kernelConst", 0.005f);

	/** Kernel offset to have also negative updates */
	protected float kernelOffset = getFloat("kernelOffset",0.2f);

	/** Learning rate for synaptic update */
	protected float timingAlpha = getFloat("timingAlpha", 1e-3f);

	/** Minimum connectivity for a neuron to not be randomly moved */
	protected float minConnectivity = getFloat("minConnectivity", 0.0f);

	/** Use bistable synapses */
	protected boolean useBistable = getBoolean("useBistable", true);

	/** Time constant for convergence to bistable extreme values */
	protected float bistableTau = getFloat("bistableTau", 0.01f);

	/** Block size for individual parts */
	protected int blockSize = getInt("blockSize", 10);

	/** Number of blocks */
	protected int numBlocks = getInt("numBlocks", 3);

	/** Weight between blocks */
	protected float betweenBlock = getFloat("betweenBlock", -1.0f);

	/** Stop-learning threshold */
	protected float weightThresh = getFloat("weightThresh", 0.5f);

	/** Maximum Mahalanobis-distance before connection is cut */
	protected float distThresh = getFloat("distThresh", 5.0f);

	/** Enable reset */
	protected boolean enableReset = getBoolean("enableReset", false);

	/** Minimum cos of angle between movement vectors to fix connection */
	protected float directionThresh = getFloat("directionThresh", 0.8f);

	/** Maximum distance between connected centers */
	protected float maxDist = getFloat("maxDist", 50.0f);

	// Weight histogram
	private int[] wHist;
	private float[] normHist;
	int numBins = 20;

	/** Constraints matrix */
	private int[][] blockConstraints;

	/** Fixed strong connections */
	private int[][] fixForever;

	/** Time of connection */
	private float[][] connTime;

	/** Minimum connection time before establishing permanent connection */
	private float minConntime = getFloat("minConntime", 3.0f);

	public ConstrainedGroupingTracker(AEChip chip) {
		super(chip);

		// Create Gaussian filter and filterchain
		gaussTracker = new FullGaussianTracker(chip);
		// Set parameters of the Gaussian filter
		gaussTracker.setPassAllSpikes(false);
		gaussTracker.setRepellConst(1e-5f);
		gaussTracker.setDrawEllipses(false);
		gaussTracker.setDrawOnlyMain(false);
		gaussTracker.setGaussRadius(1.0f);
		gaussTracker.setInitRadius(5.0f);
		gaussTracker.setAlpha(0.1f);
		gaussTracker.setMaxAge(1000000.0f);
		gaussTracker.setNoiseThreshold(0.001f);
		gaussTracker.setNumRegions(50);
		gaussTracker.setQueueLength(50);
		gaussTracker.setSigmaAlpha(0.01f);
		gaussTracker.setMinSigma(0.01f);
		gaussTracker.setMaxSigma(500.0f);
		gaussTracker.setMaxEigenProduct(50000f);

		filterChain = new FilterChain(chip);
		filterChain.add(new BackgroundActivityFilter(chip));
		filterChain.add(gaussTracker);

		setEnclosedFilterChain(filterChain);

		final String draw = "Drawing", neurons = "Neuron Parameters", clust = "Clustering Parameters";
		final String timing = "Timing Based Learning";
		final String block = "Block Structure Constraints";

		setPropertyTooltip(draw,"passAllSpikes", "Draw all spikes, not just filter results");

		setPropertyTooltip(neurons,"startw","Start weight for recurrent connections");
		setPropertyTooltip(neurons,"overlapProb","Overlap threshold for two regions");
		setPropertyTooltip(clust,"coherenceRate","Rate at which coherent clusters move together");
		setPropertyTooltip(clust, "colorRate","Rate at which colors of coherent clusters become similar");
		setPropertyTooltip(clust, "distThresh", "Maximum distance to other cluster before connection is cut");
		setPropertyTooltip(timing, "alpha","Synaptic learning rate");
		setPropertyTooltip(timing, "reduceW","Weight reduction for neurons firing in different"
			+ " receptive fields");
		setPropertyTooltip(neurons, "useBistable", "Use bistable synapses");
		setPropertyTooltip(neurons, "bistableTau", "Time constant for convergence to bistability extremes");
		setPropertyTooltip(neurons, "weightThresh", "Threshold to stop learning");
		setPropertyTooltip(draw, "drawConnections","Draw connections between cluster centers");
		setPropertyTooltip(clust,"useLearning", "Use learning for the spring-forces between clusters");
		setPropertyTooltip(draw,"drawThresh","Threshold for drawing connection between clusters");
		setPropertyTooltip(timing,"kernelConst", "Time constant of kernel for spike-timing based update");
		setPropertyTooltip(timing,"kernelOffset","Offset to subtract from time-based kernel");
		setPropertyTooltip(clust,"minConnectivity", "Minimum connectivity for a neuron to"
			+ "not be moved");
		setPropertyTooltip(clust, "minConntime", "Minimum connection time until permanent connection"
			+ "is established");
		setPropertyTooltip(clust,"enableReset","Enable reseting of weight matrix after sequence ends");
		setPropertyTooltip(clust,"directionThresh", "Minimum cos of angle between movement directions"
			+ "to fix connection between clusters");
		setPropertyTooltip(timing,"timingAlpha","Learning rate for synaptic update");
		setPropertyTooltip(block, "numBlocks", "Number of connected blocks");
		setPropertyTooltip(block, "blockSize", "Number of groups within a block");
		setPropertyTooltip(block, "betweenBlock", "Weight between blocks");
		setPropertyTooltip(clust,"maxDist", "Maximum distance between connected centers");

		createWeightMatrix();

		wHist = new int[numBins];
		normHist = new float[numBins];
	}

	// Creates the leaky integrator neurons
	private void createWeightMatrix() {
		numGaussians = blockSize * numBlocks;
		gaussTracker.setNumRegions(numGaussians);
		blockConstraints = new int[numGaussians][numGaussians];
		fixForever = new int[numGaussians][numGaussians];
		connTime = new float[numGaussians][numGaussians];

		nSpikes = new float[numGaussians];

		w = new float[numGaussians][numGaussians];
		int ix = 0;
		int iy = 0;
		for (int i=0; i<numBlocks; i++) {
			for (int j=0; j<numBlocks; j++) {
				iy = i*blockSize;
				for (int k=0; k<blockSize; k++) {
					ix = j*blockSize;
					for (int l=0; l<blockSize; l++) {
						if (i == j) {
							// Weights inside a block
							if (k==l) {
								w[ix][iy] = 0.0f;
							}
							else {
								w[ix][iy] = startw;
							}
							blockConstraints[ix][iy] = i+1;
						} else {
							w[ix][iy] = betweenBlock;
							blockConstraints[ix][iy] = 0;
						}
						ix++;
					}
					iy++;
				}
			}
		}

		spiketimes = new long[numGaussians][2];
		for (int i=0; i<numGaussians; i++) {
			spiketimes[i][0] = spiketimes[i][1] = 0;
			nSpikes[i] = 0.0f;
			for (int j=0; j<numGaussians;j++) {
				fixForever[i][j] = 0;
				connTime[i][j] = -1.0f;
			}
		}

		/*        numGaussians = gaussTracker.getNumRegions();

        // System.out.println("GroupGaussianTracker: " + numGaussians + " neurons");

        w = new float[numGaussians][numGaussians];
        for (int i=0; i<numGaussians; i++) {
            for (int j=0; j<numGaussians; j++) {
                if (i== j)
                    w[i][j] = 0.0f;
                else
                    w[i][j] = startw;
            }
        }

        spiketimes = new long[numGaussians][2];
        for (int i=0; i<numGaussians; i++) {
            spiketimes[i][0] = spiketimes[i][1] = 0;
        }
		 */
	}

	private float sigmoid(float x) {
		return (float) Math.tanh(x);
	}

	/** Updates the weights based on the firing times of two regions */
	private void updateWeightTiming(int trackID, int k, float ts, boolean increase) {
		if (blockConstraints[trackID][k] > 0) {
			// Update only within the same block
			if (increase) {
				// System.out.println(ts + " / " + spiketimes[k][0] + " / " + spiketimes[k][1]);
				double dt = Math.min(ts-spiketimes[k][0], ts-spiketimes[k][1]);
				//System.out.println(dt + " / " + 1e-6*dt/kernelConst);
				float oldW = w[trackID][k];
				// double kernel = (2.0 * Math.exp(-Math.abs(1e-6*dt/kernelConst))) - kernelOffset;
				double kernel = (Math.exp(-Math.abs((1e-6*dt)/kernelConst))) - kernelOffset;
				w[trackID][k] += timingAlpha * kernel;
				// System.out.println("Increase from " + oldW + " to " + w[trackID][k]);
				//if (w[k][trackID]>drawThresh)
				//    System.out.println("dw " + timingAlpha*kernel + " dt " + dt + " w " + w[k][trackID]);
				if (w[trackID][k] < -1.0f) {
					w[trackID][k] = -1.0f;
				}
				else if (w[trackID][k]>1.0f) {
					w[trackID][k] = 1.0f;
				}

				w[k][trackID] = w[trackID][k];
				// System.out.println("Weight increase: " + oldW + " --> " + w[k][trackID] + " / " + w[trackID][k]);
			} else {
				//if (w[trackID][k] < weightThresh) {
				// Reduce the weight by a constant
				//System.out.println("reduce " + w[trackID][k]);
				w[trackID][k] -= reduceW;
				if (w[trackID][k] < -1.0f) {
					w[trackID][k] = -1.0f;
				}
				w[k][trackID] = w[trackID][k];
				//}

			}

			if (useBistable) {
				// Let -1 / + 1 act as attractors for weights
				if (w[trackID][k] <0 ) {
					float oldW = w[trackID][k];
					w[trackID][k] -= bistableTau * (w[trackID][k] + 1.0f);
				}
				else {
					float oldW = w[trackID][k];
					w[trackID][k] += bistableTau * (1.0f-w[trackID][k]);
					// System.out.println("Increase from "+oldW+ " to " + w[trackID][k]);
				}
				w[k][trackID] = w[trackID][k];
			}

			// Fix connections if they have been above the threshold for long enough
			if (w[trackID][k] > weightThresh) {
				// Fix connection if long enough above threshold
				if (fixForever[trackID][k] == 0) {
					//System.out.println("Fixed " + trackID + "," + k + " - " + ts + " - " + connTime[trackID][k] +
					//        " = " + (ts-connTime[trackID][k]) + " / " + (minConntime*1e6));
					if (connTime[trackID][k] > 0) {
						if ((ts-connTime[trackID][k]) > (minConntime*1e6)) {
							// Make connection permanent
							fixForever[trackID][k] = fixForever[k][trackID] = 1;
							w[trackID][k] = w[k][trackID] = 1.0f;
							//System.out.println("Fixed " + trackID + "," + k + " - " + (ts-connTime[trackID][k]));
							//System.out.println("("+gaussTracker.getCenterX(trackID) +","+gaussTracker.getCenterY(trackID)+
							//"), ("+ gaussTracker.getCenterX(k)+","+gaussTracker.getCenterY(k)+")");
						}
					} else {
						// Store first connection time
						connTime[trackID][k] = connTime[k][trackID] = ts;
					}
				}
			} else {
				connTime[trackID][k] = connTime[k][trackID] = -1.0f;
			}

		}
	}


	/** Updates weights of two regions based on similarity of movement directions */
	private void updateWeightAngle(int trackID, int k, float ts, float cosAngle) {
		if (blockConstraints[trackID][k] > 0) {
			double dt = Math.min(ts-spiketimes[k][0], ts-spiketimes[k][1]);
			double kernel = (Math.exp(-Math.abs((1e-6*dt)/kernelConst))) - kernelOffset;
			float oldW = w[trackID][k];
			if ((cosAngle > directionThresh) && (dt < kernelConst)) {
				// Fix weights if movement direction is similar enough
				w[trackID][k] = 1.0f;
				//fixForever[trackID][k] = fixForever[k][trackID] = 1;
			}
			else {
				w[trackID][k] += timingAlpha * kernel * cosAngle;
			}

			if (w[trackID][k] < -1.0f) {
				w[trackID][k] = -1.0f;
			}
			else if (w[trackID][k]>1.0f) {
				w[trackID][k] = 1.0f;
			}

			w[k][trackID] = w[trackID][k];

			// System.out.println("Update W from " + oldW + " to " + w[trackID][k] + "(" + kernel + ")");
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

		EventPacket<?> nextOut = getEnclosedFilterChain().filterPacket(in);

		if (nextOut != null) {

			Class inputClass = nextOut.getEventClass();
			if ( !( inputClass == TrackerEvent.class)){
				log.warning("wrong input event class "+in.getEventClass()+" in the input packet" + in + ", disabling filter");
				setFilterEnabled(false);
				return in;
			}

			// checkOutputPacketEventType(in);


			float max_time = Float.MIN_VALUE;

			int k, kk;

			// Check if number of Gaussians is still the same
			int newNumG = gaussTracker.getNumRegions();
			if (newNumG != numGaussians) {
				createWeightMatrix();
			}

			for (int i=0; i<numBins; i++) {
				wHist[i] = 0;
			}
			for (int i=0; i<numGaussians; i++) {
				for (int j=0; j<numGaussians; j++) {
					int bin = (int) Math.floor((numBins*(w[i][j]+1))/2.0f);
					if (bin >= numBins) {
						bin = numBins-1;
					}
					else if (bin < 0) {
						bin = 0;
					}
					wHist[bin]++;
				}
			}


			for (BasicEvent e : nextOut) { // iterate over all input events
				if (passAllSpikes) {
					BasicEvent o=outItr.nextOutput();
					o.copyFrom(e);
				}

				if (e instanceof TrackerEvent) {
					TrackerEvent te = (TrackerEvent) e;

					// Copy event to output
					BasicEvent o=outItr.nextOutput();
					o.copyFrom(te);

					// Update the neuron whose Gaussian created a spike
					int trackID = te.trackerID;
					int randomMove = te.randomMove;
					float ts = te.timestamp;

					if (ts > max_time) {
						max_time = ts;
					}

					if (randomMove == 1) {

						// Re-initialize weights for this cluster after random movement
						for (k=0; k<numGaussians; k++) {
							w[trackID][k] = w[k][trackID] = startw;
							fixForever[trackID][k] = fixForever[k][trackID] = 0;
							connTime[trackID][k] = connTime[k][trackID] = -1.0f;
						}
					}


					if ((trackID >= 0) && (trackID < numGaussians)) {
						// Store spiketimes
						spiketimes[trackID][0] = spiketimes[trackID][1];
						spiketimes[trackID][1] = (long) ts;

						float moveX = gaussTracker.getLastMovementX(trackID);
						float moveY = gaussTracker.getLastMovementY(trackID);

						nSpikes[trackID]++;

						float sumW = 0.0f;
						float minD = Float.MAX_VALUE;

						for (k=0; k<numGaussians; k++) {
							// Check all other clusters for overlap
							float distG = (float) gaussTracker.mahalanobisDist(k,gaussTracker.getCenterX(trackID),
								gaussTracker.getCenterY(trackID));

							float dX = gaussTracker.getCenterX(trackID)-gaussTracker.getCenterX(k);
							float dY = gaussTracker.getCenterY(trackID)-gaussTracker.getCenterY(k);
							float distE = (float) Math.sqrt((dX*dX) + (dY*dY));

							if ((distG < minD) && (k != trackID)) {
								minD = distG;
							}

							// Add weights of connected regions
							if (w[trackID][k] > 0) {
								sumW += w[trackID][k];
							}

							if ((distG > distThresh) && (fixForever[trackID][k]==0)){
								// Weaken connection if too far away
								//System.out.println("DTh");
								w[trackID][k] *= 0.99f;
								w[k][trackID] *= 0.99f;
							}

							if ((k!=trackID) && (distG <= overlapProb) && (distE <=maxDist)) {
								// System.out.println(overlapProb + " / " + distG + " K: " + k + " / " + trackID);
								// Other neuron in same receptive field
								// Update weights and let them move together

								float factor = 1.0f;
								if (useLearning) {
									factor = w[trackID][k];
								}

								/*                            System.out.println("K " + gaussTracker.getCenterX(k) + " / " + gaussTracker.getCenterY(k) +
                                    " ID: " + gaussTracker.getCenterX(trackID) + " / " + gaussTracker.getCenterY(trackID) +
                                    " Dist: " + distG); */
								int randMove =
									gaussTracker.moveTo(k, gaussTracker.getCenterX(trackID),
										gaussTracker.getCenterY(trackID), coherenceRate*factor);

								if (randMove == 0) {
									// Illegal index used
									System.out.println("Random move");
									w[k][trackID] = w[trackID][k] = startw;
								}

								if (fixForever[trackID][k] == 0) {
									float otherMoveX = gaussTracker.getLastMovementX(k);
									float otherMoveY = gaussTracker.getLastMovementY(k);

									float cosAngle = movementAngle(moveX, moveY, otherMoveX, otherMoveY);

									updateWeightAngle(trackID, k, ts, cosAngle);

								}

								gaussTracker.setAge(k, ts);
								gaussTracker.setAge(trackID, ts);

								if (blockConstraints[k][trackID]>0) {
									gaussTracker.updateColors(k, trackID, colorRate*factor);
								}


								if ((useLearning) && (fixForever[trackID][k]==0)) {

									// updateWeightAnalog(trackID, k, ts, true);
									updateWeightTiming(trackID, k, ts, true);

								}


							} else if (trackID != k) {
								// Not enough overlapping with event producing region --> reduce weight
								//System.out.println("Red: " + trackID + " - " + k);
								if ((useLearning) && (fixForever[trackID][k]==0)) {
									// updateWeightAnalog(trackID, k, ts, false);
									updateWeightTiming(trackID, k, ts, false);
								}

								float factor = 1.0f;
								if (useLearning) {
									factor = 1.0f*w[trackID][k];
								}

								// Move clusters away from each other
								gaussTracker.moveTo(k, gaussTracker.getCenterX(trackID),
									gaussTracker.getCenterY(trackID), coherenceRate*factor);

								// Disconnect if too far away
								if (distE > maxDist) {
									fixForever[trackID][k] = fixForever[k][trackID] = 0;
									w[trackID][k] = w[k][trackID] = startw;
									// w[trackID][k] = w[k][trackID] = -1.0f;
									connTime[trackID][k] = connTime[k][trackID] = -1.0f;
								}

								// gaussTracker.setAge(k, ts);
								// gaussTracker.setAge(trackID, ts);

								//if (this.blockConstraints[k][trackID] > 0)
								//    gaussTracker.updateColors(k, trackID, -colorRate*factor);


								// System.out.println("Delta " + mDelta + " / "+w[k][trackID]);
							} // else
						} // for (k=0; ...)

						// If region is strongly connected, avoid random moves
						if (sumW > minConnectivity) {
							//gaussTracker.setAge(trackID, ts);
							gaussTracker.setAge(trackID, ts+10e7f);
						}
						/*else if (minD > distThresh) {
                        System.out.println("minD " + minD);
                        // Region is too far away from others, resample to random position
                        for (k=0; k<numGaussians; k++) {
                            w[trackID][k]=0.0f;
                        }
                        // gaussTracker.moveRandom(trackID, ts);
                    } */
					} // if ((trackid>=0
				} // e instanceof TrackerEvent
				else {
					System.out.println("No TrackerEvent");
				}
			} // BasicEvent e

			// Check which clusters to move randomly
			/*
        for (k=0; k<numGaussians; k++) {
            float sumW = 0.0f;
            for (int j=0; j<numGaussians; j++)
                sumW += w[k][j];
            sumW /= numGaussians;
            if (((max_time - gaussTracker.getAge(k)) > gaussTracker.getMaxAge()) &&
                (sumW < minConnectivity)) {
                // System.out.println("moveRandom");
                gaussTracker.moveRandom(k);
                // gaussTracker.setCenterX(k, (float) (Math.random()*127.99));
                // gaussTracker.setCenterX(k, (float) (Math.random()*127.99));
                for (int j=0; j<numGaussians; j++) {
                    w[k][j] = w[j][k] = 0.0f;
                }
            }
            // Update age of cluster
            //if (sumW > minConnectivity) {
                gaussTracker.setAge(k, sumW);
            }
        } */

			//System.out.println("Avg. Conn " + sumW / (numGaussians*numGaussians));
		} // if nextout != null

		if (passAllSpikes) {
			return in;
		}
		else {
			return out;
		}
	}

	@Override
	public void resetFilter() {
		if (enableReset) {
			filterChain.reset();
			createWeightMatrix();
		} else {
			System.out.println("WARNING: Reset disabled!");
		}
	}

	@Override
	public void initFilter() {
		filterChain.reset();
		createWeightMatrix();
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if (drawConnections) {
			GL2 gl=drawable.getGL().getGL2(); // gets the OpenGL GL context. Coordinates are in chip pixels, 0,0 is LL
			gl.glLineWidth(2); // set line width in screen pixels (not chip pixels)
			// gl.glColor4f(1.0f, 0.0f, 0.0f,.5f); // set color red
			for (int i=0; i<numGaussians; i++) {
				for (int j=i+1; j<numGaussians; j++) {
					if (w[i][j] >= drawThresh) {
						if (fixForever[i][j] == 0) {
							gl.glColor4f(0.5f * (w[i][j]+1.0f), 0.0f, 0.0f,.5f); // set color red
						}
						else {
							gl.glColor4f(0.0f, 1.0f, 0.0f,.5f); // set color green
						}

						// Draw connections with large weights
						gl.glBegin(GL.GL_LINES); // start drawing a line loop
						// System.out.println(i+" " + j + " : " + gaussTracker.getCenterX(i) + " _ " + w[i][j]);
						gl.glVertex2f(gaussTracker.getCenterX(i), gaussTracker.getCenterY(i));
						gl.glVertex2f(gaussTracker.getCenterX(j), gaussTracker.getCenterY(j));
						gl.glEnd();
					}
				}
			}

			float histHeight = 20.0f;
			float histWidth = 100.0f / numBins;
			float x,y;
			int sumH = 0;
			for (int i=0; i<numBins; i++) {
				sumH += wHist[i];
			}
			for (int i=0; i<numBins; i++) {
				if (wHist[i]>0) {
					gl.glBegin(GL.GL_LINE_LOOP);
					x = 1.0f + (histWidth*i);
					y = 1.0f + (histHeight * ((float) wHist[i] / (float) sumH));
					gl.glVertex2f(x,1.0f);
					gl.glVertex2f(x,y);
					gl.glVertex2f(x+histWidth, y);
					gl.glVertex2f(x+histWidth, 1.0f);
					gl.glEnd();
				}
			}
		}

		// System.out.println(this.coherenceRate);
	}


	final int DEFAULT_TIMESTAMP=Integer.MIN_VALUE;

	public boolean isPassAllSpikes() {
		return passAllSpikes;
	}

	synchronized public void setPassAllSpikes(boolean passAllSpikes) {
		this.passAllSpikes = passAllSpikes;
		putBoolean("passAllSpikes", passAllSpikes);
	}

	public float getOverlapProb() {
		return overlapProb;
	}

	synchronized public void setOverlapProb(float overlapProb) {
		this.overlapProb = overlapProb;
		putFloat("overlapProb", overlapProb);
	}

	public float getCoherenceRate() {
		return coherenceRate;
	}

	synchronized public void setCoherenceRate(float coherenceRate) {
		this.coherenceRate = coherenceRate;
		putFloat("coherenceRate",coherenceRate);
	}

	public float getColorRate() {
		return colorRate;
	}

	synchronized public void setColorRate(float colorRate) {
		this.colorRate = colorRate;
		putFloat("colorRate", colorRate);
	}

	public float getAlpha() {
		return alpha;
	}

	synchronized public void setAlpha(float alpha) {
		this.alpha = alpha;
		putFloat("alpha",alpha);
	}

	public float getReduceW() {
		return reduceW;
	}

	synchronized public void setReduceW(float reduceW) {
		this.reduceW = reduceW;
		putFloat("reduceW", reduceW);
	}

	public boolean isDrawConnections() {
		return drawConnections;
	}

	synchronized public void setDrawConnections(boolean drawConnections) {
		this.drawConnections = drawConnections;
		putBoolean("drawConnection", drawConnections);
	}

	public boolean isUseLearning() {
		return useLearning;
	}

	synchronized public void setUseLearning(boolean useLearning) {
		this.useLearning = useLearning;
		putBoolean("useLearning", useLearning);
	}

	public float getDrawThresh() {
		return drawThresh;
	}

	synchronized public void setDrawThresh(float drawThresh) {
		this.drawThresh = drawThresh;
		putFloat("drawThresh",drawThresh);
	}

	public float getKernelConst() {
		return kernelConst;
	}

	synchronized public void setKernelConst(float kernelConst) {
		this.kernelConst = kernelConst;
		putFloat("kernelConst", kernelConst);
	}

	public float getKernelOffset() {
		return kernelOffset;
	}

	synchronized public void setKernelOffset(float kernelOffset) {
		this.kernelOffset = kernelOffset;
		putFloat("kernelOffset", kernelOffset);
	}

	public float getMinConnectivity() {
		return minConnectivity;
	}

	synchronized public void setMinConnectivity(float minConnectivity) {
		this.minConnectivity = minConnectivity;
		putFloat("minConnectivity", minConnectivity);
	}


	// Returns the x-coordinate of the center of the k-th cluster
	public float getCenterX(int k) {
		return gaussTracker.getCenterX(k);
	}
	// Returns the y-coordinate of the center of the k-th cluster
	public float getCenterY(int k) {
		return gaussTracker.getCenterY(k);
	}

	// Returns the number of Gaussians
	public int getNumGaussians() {
		return numGaussians;
	}

	public FullGaussianTracker getGaussTracker() {
		return gaussTracker;
	}

	public float getTimingAlpha() {
		return timingAlpha;
	}

	synchronized public void setTimingAlpha(float timingAlpha) {
		this.timingAlpha = timingAlpha;
		putFloat("timingAlpha", timingAlpha);
	}

	public float getBistableTau() {
		return bistableTau;
	}

	synchronized public void setBistableTau(float bistableTau) {
		this.bistableTau = bistableTau;
		putFloat("bistableTau", bistableTau);
	}

	public boolean isUseBistable() {
		return useBistable;
	}

	synchronized public void setUseBistable(boolean useBistable) {
		this.useBistable = useBistable;
		putBoolean("useBistable", useBistable);
	}

	public float getBetweenBlock() {
		return betweenBlock;
	}

	synchronized public void setBetweenBlock(float betweenBlock) {
		this.betweenBlock = betweenBlock;
		putFloat("betweenBlock", betweenBlock);
		createWeightMatrix();
	}

	public int getBlockSize() {
		return blockSize;
	}

	synchronized public void setBlockSize(int blockSize) {
		if (blockSize >= 1) {
			this.blockSize = blockSize;
			putInt("blockSize", blockSize);
			createWeightMatrix();
		}
	}

	public int getNumBlocks() {
		return numBlocks;
	}

	synchronized public void setNumBlocks(int numBlocks) {
		if (numBlocks >= 1) {
			this.numBlocks = numBlocks;
			putInt("numBlocks", numBlocks);
			createWeightMatrix();
		}
	}

	public float getStartw() {
		return startw;
	}

	synchronized public void setStartw(float startw) {
		this.startw = startw;
		putFloat("startw",startw);
	}

	public float getWeightThresh() {
		return weightThresh;
	}

	synchronized public void setWeightThresh(float weightThresh) {
		this.weightThresh = weightThresh;
		putFloat("weightThresh", weightThresh);
	}

	public float getDistThresh() {
		return distThresh;
	}

	synchronized public void setDistThresh(float distThresh) {
		this.distThresh = distThresh;
		putFloat("distThresh",distThresh);
	}

	public boolean isEnableReset() {
		return enableReset;
	}

	synchronized public void setEnableReset(boolean enableReset) {
		this.enableReset = enableReset;
		putBoolean("enableReset", enableReset);
	}

	// Computes angle between two movement vectors
	private float movementAngle(float moveX, float moveY, float otherMoveX, float otherMoveY) {
		float scalarProd = (moveX * otherMoveX) + (moveY * otherMoveY);
		float normA = (moveX*moveX) + (moveY * moveY);
		float normB = (otherMoveX*otherMoveX) + (otherMoveY * otherMoveY);
		return (float)(scalarProd / Math.sqrt(normA*normB));
	}

	public float getDirectionThresh() {
		return directionThresh;
	}

	synchronized public void setDirectionThresh(float directionThresh) {
		this.directionThresh = directionThresh;
		putFloat("directionThresh", directionThresh);
	}

	public float getMinConntime() {
		return minConntime;
	}

	synchronized public void setMinConntime(float minConntime) {
		this.minConntime = minConntime;
		putFloat("minConntime",minConntime);
	}

	public float getMaxDist() {
		return maxDist;
	}

	synchronized public void setMaxDist(float maxDist) {
		this.maxDist = maxDist;
		putFloat("maxDist",maxDist);
	}


	public void setWeight(int i, int j, float newW) {
		if ((i>=0) && (i<numGaussians) && (j>=0) && (j<numGaussians)) {
			w[i][j] = w[j][i] = newW;
		}
	}

	public float getWeight(int i, int j) {
		if ((i>=0) && (i<numGaussians) && (j>=0) && (j<numGaussians)) {
			return w[i][j];
		}
		else {
			return Float.NaN;
		}
	}

	public void setPosition(int k, float x, float y) {
		if ((k>=0) && (k<numGaussians)) {
			gaussTracker.moveTo(k, x, y, 1.0f);
		}
	}

	public void setColor(int k, float r, float g, float b) {
		gaussTracker.setColor(k, r, g, b);
	}

	public void setFixForever(int i, int j, int value) {
		if ((i>=0) && (i<numGaussians) && (j>=0) && (j<numGaussians)) {
			fixForever[i][j] = fixForever[j][i] = value;
		}
	}

	// Move cluster center towards a point
	public void moveTo(int k, float x, float y, float rate) {
		if ((k>=0) && (k<numGaussians)) {
			gaussTracker.moveTo(k, x, y, rate);
		}
	}
}
