/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.tell2011.GaussianTracker;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

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
public class GroupingGaussianTracker extends EventFilter2D implements FrameAnnotater {

	private FilterChain filterChain;  // Enclosed Gaussian Tracker filter
	private GaussianTracker gaussTracker;

	// Number of gaussian trackers
	private int numGaussians = 0;

	// Array of leaky integrator neurons
	private LeakyIntegratorNeuron[] neurons = null;

	// Last two spike times of every neuron
	private long spiketimes[][] = null;

	// Recurrent weights between neurons
	private float w[][] = null;

	private boolean passAllSpikes = getBoolean("passAllSpikes", true);

	/** Time constant for Leaky Integrator Neurons */
	protected float tau=getFloat("tau",20.0f);

	/** Leak potential for Leaky Integrator Neurons */
	protected float vleak=getFloat("vleak",0.0f);

	/** Reset potential for Leaky Integrator Neurons */
	protected float vreset=getFloat("vreset",0.0f);

	/** Scaling of a synaptic input spike */
	protected float spikeAmplitude = getFloat("spikeAmplitude",1.0f);

	/** Start weights for recurrent connections */
	protected float startw = getFloat("startw", 1.0f);

	/** Size of receptive field */
	protected float recField = getFloat("recField",30.0f);

	/** Coherence learning rate */
	protected float coherenceRate = getFloat("coherenceRate", 1e-7f);

	/** Color update rate */
	protected float colorRate = getFloat("colorRate",0.01f);

	/** Synaptic learning rate */
	protected float alpha = getFloat("alpha",0.001f);

	/** Mean activation update rate for neurons */
	protected float lambda = getFloat("lambda",0.01f);

	/** Weight reduction for neurons firing in different receptive fields */
	protected float reduceW = getFloat("reduceW", 0.1f);

	/** Draw connections between cluster centers */
	protected boolean drawConnections = getBoolean("drawConnections", true);

	/** Use learned weights for spring-forces */
	protected boolean useLearning = getBoolean("useLearning",false);

	/** Drawing threshold */
	protected float drawThresh = getFloat("drawThresh", 0.5f);

	/** Kernel time constant for update */
	protected float kernelConst = getFloat("kernelConst", 0.0005f);

	/** Kernel offset to have also negative updates */
	protected float kernelOffset = getFloat("kernelOffset",0.5f);

	/** Learning rate for synaptic update */
	protected float timingAlpha = getFloat("timingAlpha", 1e-5f);

	/** Minimum connectivity for a neuron to not be randomly moved */
	protected float minConnectivity = getFloat("minConnectivity", 0.0f);

	/** Pass output spikes from connected clusters */
	protected boolean spikeConnected = getBoolean("spikeConnected", true);


	// Weight histogram
	private int[] wHist;
	private float[] normHist;
	int numBins = 20;

	public GroupingGaussianTracker(AEChip chip) {
		super(chip);

		// Create Gaussian filter and filterchain
		gaussTracker = new GaussianTracker(chip);
		// Set parameters of the Gaussian filter
		gaussTracker.setPassAllSpikes(false);
		gaussTracker.setDistFactor(0.5f);
		gaussTracker.setDrawCircles(true);
		gaussTracker.setGaussRadius(5);
		gaussTracker.setLearnRate(0.3f);
		gaussTracker.setMaxAge(50000);
		gaussTracker.setMoveDistance(5.0f);
		gaussTracker.setNoiseThreshold(0.0001f);
		gaussTracker.setNumGaussians(100);
		gaussTracker.setQueueLength(100);
		gaussTracker.setMinDistance(2.0f);

		filterChain = new FilterChain(chip);
		filterChain.add(new BackgroundActivityFilter(chip));
		filterChain.add(gaussTracker);

		setEnclosedFilterChain(filterChain);

		final String draw = "Drawing", neurons = "Neuron Parameters", clust = "Clustering Parameters";
		final String timing = "Timing Based Learning";

		setPropertyTooltip(draw,"passAllSpikes", "Draw all spikes, not just filter results");

		setPropertyTooltip(neurons,"tau","Time constant of Leaky Integrator neuron");
		setPropertyTooltip(neurons,"vleak","Leak voltage of Leaky Integrator neuron");
		setPropertyTooltip(neurons,"vreset","Reset potential of Leaky Integrator neuron");
		setPropertyTooltip(neurons,"thresh","Threshold for Leaky Integrator neurons");
		setPropertyTooltip(neurons,"spikeAmplitude","Scaling of synaptic input spikes");
		setPropertyTooltip(neurons,"startw","Start weight for recurrent connections");
		setPropertyTooltip(neurons,"recField","Size of the spatial receptive field");
		setPropertyTooltip(clust,"coherenceRate","Rate at which coherent clusters move together");
		setPropertyTooltip(clust, "colorRate","Rate at which colors of coherent clusters become similar");
		setPropertyTooltip(neurons, "alpha","Synaptic learning rate");
		setPropertyTooltip(neurons, "lambda","Update rate for computing mean neuron activations");
		setPropertyTooltip(neurons, "reduceW","Weight reduction for neurons firing in different"
			+ " receptive fields");
		setPropertyTooltip(draw, "drawConnections","Draw connections between cluster centers");
		setPropertyTooltip(clust,"useLearning", "Use learning for the spring-forces between clusters");
		setPropertyTooltip(draw,"drawThresh","Threshold for drawing connection between clusters");
		setPropertyTooltip(timing,"kernelConst", "Time constant of kernel for spike-timing based update");
		setPropertyTooltip(timing,"kernelOffset","Offset to subtract from time-based kernel");
		setPropertyTooltip(clust,"minConnectivity", "Minimum connectivity for a neuron to"
			+ "not be moved");
		setPropertyTooltip(timing,"timingAlpha","Learning rate for synaptic update");
		setPropertyTooltip(clust, "spikeConnected", "Create spikes for connected regions");

		create_neurons();

		wHist = new int[numBins];
		normHist = new float[numBins];
	}

	// Creates the leaky integrator neurons
	private void create_neurons() {
		numGaussians = gaussTracker.getNumGaussians();

		// System.out.println("GroupGaussianTracker: " + numGaussians + " neurons");

		w = new float[numGaussians][numGaussians];
		for (int i=0; i<numGaussians; i++) {
			for (int j=0; j<numGaussians; j++) {
				if (i== j) {
					w[i][j] = 0.0f;
				}
				else {
					w[i][j] = startw;
				}
			}
		}

		neurons = new LeakyIntegratorNeuron[numGaussians];
		spiketimes = new long[numGaussians][2];
		for (int i=0; i<numGaussians; i++) {
			neurons[i] = new LeakyIntegratorNeuron(tau, vleak, vreset);
			neurons[i].setLambda(lambda);
			spiketimes[i][0] = spiketimes[i][1] = 0;
		}

	}

	private float sigmoid(float x) {
		return (float) Math.tanh(x);
	}

	/** Use BCM rule for weight update */
	private void updateWeightAnalog(int trackID, int k, float ts, boolean increase) {
		if (increase) {
			// Update weights with BCM rule
			float targetV = neurons[trackID].getV(ts);
			float otherV = neurons[k].getV(ts);

			float targetMean = neurons[trackID].getMeanActivation();
			float otherMean  = neurons[k].getMeanActivation();

			// System.out.println("V1 " + targetV + " / " + neurons[trackID].getMeanActivation());
			// System.out.println("V2 " + otherV + " / " + neurons[k].getMeanActivation());

			// BCM update
			float delta1 = otherV * (otherV - otherMean) * targetV;
			float delta2 = targetV * (targetV - targetMean) * otherV;

			// Compute symmetric update
			float mDelta = sigmoid((delta1+delta2)/2.0f);
			w[trackID][k] += alpha * mDelta;
			if (w[trackID][k] > 1.0f) {
				w[trackID][k] = 1.0f;
			}
			w[k][trackID] = w[trackID][k];
		}
		else {
			// Reduce the weight by a constant
			w[trackID][k] -= reduceW;
			if (w[trackID][k] < -1.0f) {
				w[trackID][k] = -1.0f;
			}
			w[k][trackID] = w[trackID][k];

		}
	}

	private void updateWeightTiming(int trackID, int k, float ts, boolean increase) {
		if (increase) {
			double dt = Math.min(ts-spiketimes[k][0], ts-spiketimes[k][1]);
			// System.out.println("dt " + dt);
			// double kernel = (2.0 * Math.exp(-Math.abs(1e-6*dt/kernelConst))) - kernelOffset;
			double kernel = (Math.exp(-Math.abs((1e-6*dt)/kernelConst))) - kernelOffset;
			w[trackID][k] += timingAlpha * kernel;
			//if (w[k][trackID]>drawThresh)
			//    System.out.println("dw " + timingAlpha*kernel + " dt " + dt + " w " + w[k][trackID]);
			if (w[trackID][k] < -1.0f) {
				w[trackID][k] = -1.0f;
			}
			else if (w[trackID][k]>1.0f) {
				w[trackID][k] = 1.0f;
			}

			w[k][trackID] = w[trackID][k];
		} else {
			// Reduce the weight by a constant
			// System.out.println("reduce " + w[trackID][k]);
			w[trackID][k] -= reduceW;
			if (w[trackID][k] < -1.0f) {
				w[trackID][k] = -1.0f;
			}
			w[k][trackID] = w[trackID][k];

		}
	}


	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		if(!filterEnabled) {
			return in;
		}

		EventPacket<?> nextOut = getEnclosedFilterChain().filterPacket(in);

		if ( in == null ){
			return null;
		}
		// checkOutputPacketEventType(in);

		checkOutputPacketEventType(TrackerEvent.class);

		OutputEventIterator outItr=out.outputIterator();

		float max_time = Float.MIN_VALUE;

		int k, kk;

		// Check if number of Gaussians is still the same
		int newNumG = gaussTracker.getNumGaussians();
		if (newNumG != numGaussians) {
			create_neurons();
		}

		for (int i=0; i<numBins; i++) {
			wHist[i] = 0;
			/* for (int i=0; i<numGaussians; i++) {
                int bin = (int) Math.floor(numBins*(w[i][j]+1)/2.0f);
                if (bin >= numBins)
                    bin = numBins-1;
                wHist[bin]++;
        }*/
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
				float ts = te.timestamp;

				if (ts > max_time) {
					max_time = ts;
				}

				if ((trackID >= 0) && (trackID < numGaussians)) {
					// if (neurons[trackID] != null)
					//    neurons[trackID].update(spikeAmplitude, ts);

					// Store spiketimes
					spiketimes[trackID][0] = spiketimes[trackID][1];
					spiketimes[trackID][1] = (long) ts;

					for (k=0; k<numGaussians; k++) {
						float distG = (float) (Math.pow(gaussTracker.getCenterX(trackID)-
							gaussTracker.getCenterX(k), 2) + Math.pow(gaussTracker.getCenterY(trackID) -
								gaussTracker.getCenterY(k), 2));

						if ((k!=trackID) && (distG <= (recField*recField))) {
							//System.out.println(recField + " / " + dist[trackID][k] + " K: " + k + " / " + trackID);
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

							if (randMove == 1) {
								w[k][trackID] = w[trackID][k] = 0.0f;
							}

							gaussTracker.setAge(k, ts);
							gaussTracker.setAge(trackID, ts);

							gaussTracker.updateColors(k, trackID, colorRate*factor);


							if (useLearning) {

								// updateWeightAnalog(trackID, k, ts, true);
								updateWeightTiming(trackID, k, ts, true);

							}

							// Create spikes for connected regions
							/*                            if (this.spikeConnected) {
                                for (kk=0; kk<numGaussians; kk++) {
                                    if (w[k][kk] > 0.9f) {
                                        TrackerEvent new_te = new TrackerEvent();
                                        new_te.setX((short) gaussTracker.getCenterX(kk));
                                        new_te.setY((short) gaussTracker.getCenterY(kk));
                                        new_te.trackerID = kk;
                                        new_te.setTimestamp((int) ts);

                                        BasicEvent no=(BasicEvent)outItr.nextOutput();
                                        no.copyFrom(new_te);
                                    }
                                }
                            } */

						} else if (trackID != k) {
							if (useLearning) {
								// updateWeightAnalog(trackID, k, ts, false);
								updateWeightTiming(trackID, k, ts, false);
							}

							float factor = 1.0f;
							if (useLearning) {
								factor = 1.0f*w[trackID][k];
							}

							// Move clusters away from each other
							gaussTracker.moveTo(k, gaussTracker.getCenterX(trackID),
								gaussTracker.getCenterY(trackID), -coherenceRate*factor);

							// gaussTracker.setAge(k, ts);
							// gaussTracker.setAge(trackID, ts);

							gaussTracker.updateColors(k, trackID, -colorRate*factor);


							// System.out.println("Delta " + mDelta + " / "+w[k][trackID]);
						} // else
					} // for (k=0; ...)
				} // if ((trackid>=0
			} // e instanceof TrackerEvent

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

		if (passAllSpikes) {
			return in;
		}
		else {
			return out;
		}
	}

	@Override
	public void resetFilter() {
		filterChain.reset();
		create_neurons();
	}

	@Override
	public void initFilter() {
		resetFilter();
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
						gl.glColor4f(0.5f * (w[i][j]+1.0f), 0.0f, 0.0f,.5f); // set color red

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

	public float getVleak() {
		return vleak;
	}

	synchronized public void setVleak(float vleak) {
		putFloat("vleak",vleak);

		this.vleak = vleak;
		if (neurons != null) {
			for (int i=0; i<numGaussians; i++) {
				neurons[i].setVleak(vleak);
			}
		}
	}

	public float getVreset() {
		return vreset;
	}

	synchronized public void setVreset(float vreset) {
		putFloat("vreset",vreset);

		this.vreset = vreset;
		if (neurons != null) {
			for (int i=0; i<numGaussians; i++) {
				neurons[i].setVreset(vreset);
			}
		}
	}

	public float getTau() {
		return tau;
	}

	synchronized public void setTau(float tau) {
		putFloat("tau",tau);

		this.tau = tau;
		if (neurons != null) {
			for (int i=0; i<numGaussians; i++) {
				neurons[i].setTau(tau);
			}
		}
	}

	public float getSpikeAmplitude() {
		return spikeAmplitude;
	}

	public void setSpikeAmplitude(float spikeAmplitude) {
		putFloat("spikeAmplitude",spikeAmplitude);

		this.spikeAmplitude = spikeAmplitude;
	}

	public boolean isPassAllSpikes() {
		return passAllSpikes;
	}

	synchronized public void setPassAllSpikes(boolean passAllSpikes) {
		this.passAllSpikes = passAllSpikes;
		putBoolean("passAllSpikes", passAllSpikes);
	}

	public float getRecField() {
		return recField;
	}

	synchronized public void setRecField(float recField) {
		this.recField = recField;
		putFloat("recField", recField);
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

	public float getLambda() {
		return lambda;
	}

	synchronized public void setLambda(float lambda) {
		this.lambda = lambda;
		putFloat("lambda", lambda);
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

	public GaussianTracker getGaussTracker() {
		return gaussTracker;
	}

	public float getTimingAlpha() {
		return timingAlpha;
	}

	synchronized public void setTimingAlpha(float timingAlpha) {
		this.timingAlpha = timingAlpha;
		putFloat("timingAlpha", timingAlpha);
	}

	public boolean isSpikeConnected() {
		return spikeConnected;
	}

	synchronized public void setSpikeConnected(boolean spikeConnected) {
		this.spikeConnected = spikeConnected;
		putBoolean("spikeConnected",spikeConnected);
	}


}
