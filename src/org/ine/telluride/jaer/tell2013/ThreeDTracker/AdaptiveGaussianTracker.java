/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.tell2013.ThreeDTracker;

import java.util.Observable;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * A filter that fits several Gaussians to clusters of events.
 * @author Michael Pfeiffer, Ryad Benosman
 */
@Description("Tracks multiple Gaussians with adaptive sizes and learning rates"
	+ "to fit clusters of events") // adds this string as description of class for jaer GUIs
public class AdaptiveGaussianTracker extends EventFilter2D implements FrameAnnotater {

	private int numGaussians = getInt("numGaussians", 2);
	private int gaussRadius = getInt("gaussRadius", 10);
	private float learnRate = getFloat("learnRate", 0.1f);
	private float noiseThreshold = getFloat("noiseThreshold",0.001f);
	private float distFactor = getFloat("distFactor", 1.0f);
	private boolean passAllSpikes = getBoolean("passAllSpikes",true);
	private boolean drawCircles = getBoolean("drawCircles", true);

	private float moveDistance = getFloat("moveDistance", 3.0f);
	private int maxAge = getInt("maxAge", 1000000);

	private int queueLength = getInt("queueLength", 100);

	private float minDistance = getFloat("minDistance", 2.0f);

	private float adaptRate = getFloat("adaptRate", 0.01f);

	private float peripheryBorder = getFloat("peripheryBorder", 0.5f);

	private float[] centerX, centerY;
	private float[] Sigma;
	private float[][] color;
	// Direction of last movement
	private float[] directX, directY;
	// Current learning rate
	private float[] alpha;

	// Storing the coordinates of a circle for drawing
	final int N = 20;
	private float[] circleX;
	private float[] circleY;

	private float[] age;

	// Queue for new target positions
	private int[] targetX;
	private int[] targetY;

	private int queuePointer;

	public AdaptiveGaussianTracker(AEChip chip) {
		super(chip);

		// add this string tooltip to FilterPanel GUI control for filterLength
		setPropertyTooltip("gaussRadius", "Radius of each Gaussian cluster");
		setPropertyTooltip("learnRate", "Update rate of the Gaussian clusters");
		setPropertyTooltip("noiseThreshold","Threshold for Gaussians below which an"
			+ "event is considered to be noise");
		setPropertyTooltip("numGaussians", "Number of Gaussian clusters");
		setPropertyTooltip("distFactor", "Minimum distance for center updates (x radius)");
		setPropertyTooltip("moveDistance", "Distance for random moves of too old clusters");
		setPropertyTooltip("maxAge", "Maximum age of clusters without movement");
		setPropertyTooltip("passAllSpikes", "Draw all spikes, not just filter results");
		setPropertyTooltip("drawCircles","Draw the circles representing the clusters");
		setPropertyTooltip("queueLength","Length of the queue for new target positions");
		setPropertyTooltip("minDistance", "Minimum distance of any two clusters");
		setPropertyTooltip("adaptRate", "Rate of adaptation of learning rate and size");
		setPropertyTooltip("peripheryBorder", "Percentage of radius at which center/periphery"
			+ " border lies");

		alloc_gaussians();

		circleX = new float[N+1];
		circleY = new float[N+1];
		for (int i=0; i<=N; i++) {
			circleX[i] = (float) (Math.cos((i*2.0*Math.PI)/N));
			circleY[i] = (float) (Math.sin((i*2.0*Math.PI)/N));
		}

		init_queue();
		//        targetX = new int[queueLength];
		//        targetY = new int[queueLength];

	}

	// Allocates the memory for the Gaussians
	private void alloc_gaussians() {
		// Compute the number of Gaussians (regular grid)
		int numPerRow = (int) Math.ceil(numGaussians / Math.sqrt(numGaussians));
		int numRows = (int) Math.ceil(numGaussians / numPerRow);

		centerX = new float[numGaussians];
		centerY = new float[numGaussians];
		Sigma = new float[numGaussians];
		age = new float[numGaussians];
		alpha = new float[numGaussians];
		directX = new float[numGaussians];
		directY = new float[numGaussians];

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
				Sigma[idx] = gaussRadius;
				alpha[idx] = learnRate;
				directX[idx] = 0.0f;
				directY[idx] = 0.0f;
				xpos += xdiff;
				idx++;
				if (idx >= numGaussians) {
					break;
				}
			}
			xpos = 2.0f*gaussRadius;
			ypos += ydiff;
			if (idx >= numGaussians) {
				break;
			}
		}

		// Create age matrix
		for (int i=0; i< numGaussians; i++) {
			age[i] = 0;
		}

		// Create colors for each Gaussian
		color = new float[numGaussians][3];
			for (int i=0; i<numGaussians;i++) {
				float H = (i * 360.0f) / (numGaussians);
				float[] tmp = HSV2RGB(H, 1.0f, 1.0f);
				for (int j=0; j<3; j++) {
					color[i][j] = tmp[j];
				}
			}
	}

	// Initialize queue with random
	private void init_queue() {
		queuePointer = 0;

		targetX = new int[queueLength];
		targetY = new int[queueLength];

		for (int i=0; i<queueLength; i++) {
			targetX[i] = (int) (Math.random() * 128);
			targetY[i] = (int) (Math.random() * 128);
		}
	}

	@Override
	synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
		if ( in == null ){
			return null;
		}
		if(!filterEnabled) {
			return in;
		}
		checkOutputPacketEventType(TrackerEvent.class);
		// checkOutputPacketEventType(in);

		OutputEventIterator outItr=out.outputIterator();

		float max_time = Float.MIN_VALUE;

		int k, kk;
		//        System.out.println("Filtering " + in.getSize() + " packets");
		//        float norm_const = (float) (1.0 / Math.sqrt(2*Math.PI*gaussRadius));
		for (BasicEvent e : in) { // iterate over all input events
			if (passAllSpikes) {
				BasicEvent o=outItr.nextOutput();
				o.copyFrom(e);
			}

			float ts = e.timestamp;
			if (ts > max_time) {
				max_time = ts;
			}

			// Compute distance to all Gaussians
			boolean has_moved = false;
			for (k=0; k<numGaussians; k++) {
				float norm_const = (float) (1.0 / Math.sqrt(2*Math.PI*Sigma[k]));
				double dist = Math.pow(e.x - centerX[k],2) + Math.pow(e.y - centerY[k],2);
				double G = norm_const * Math.exp(-dist / (2.0 * gaussRadius));
				// System.out.println("G " + G);
				// Update Gaussians
				if (G>noiseThreshold) {
					// System.out.println("G: "+G);
					float newCenterX = (learnRate*e.x) + ((1-learnRate)*centerX[k]);
					float newCenterY = (learnRate*e.y) + ((1-learnRate)*centerY[k]);

					// Compute minimum distance to other centers
					double minDist = Double.MAX_VALUE;

					for (kk=0; kk<numGaussians; kk++) {
						if (k != kk) {
							dist = Math.sqrt(Math.pow(newCenterX-centerX[kk], 2)+
								Math.pow(newCenterY-centerY[kk],2));
							if (dist < minDist) {
								minDist = dist;
							}
						}
					}

					// Change size of receptive field
					if (dist < Math.pow(peripheryBorder * Sigma[k],2)) {
						// Reduce radius if in center region
						Sigma[k] *= (1.0-adaptRate);
					} else {
						// Increase radius if in periphery region
						Sigma[k] *= (1.0+adaptRate);
						if (Sigma[k] > (2.0f*gaussRadius)) {
							Sigma[k] = 2.0f*gaussRadius;
						}
					}


					// Move center of the Gaussian if it is not too close
					// to another Gaussian
					//                    if (minDist > distFactor * gaussRadius) {
					if (minDist > (distFactor * Sigma[k])) {
						has_moved = true;
						directX[k] = newCenterX-centerX[k];
						directY[k] = newCenterY-centerY[k];
						centerX[k] = newCenterX;
						centerY[k] = newCenterY;
						age[k] = ts;

						/* OrientationEvent oe = new OrientationEvent();
                        oe.setX((short) centerX[k]);
                        oe.setY((short) centerY[k]);
                        oe.orientation = (byte) k;
                        oe.setTimestamp((int) ts);

                        BasicEvent o=(OrientationEvent)outItr.nextOutput();
                        o.copyFrom(oe); */

						TrackerEvent te = new TrackerEvent();
						te.setX((short) centerX[k]);
						te.setY((short) centerY[k]);
						te.trackerID = k;
						te.setTimestamp((int) ts);

						BasicEvent o=outItr.nextOutput();
						o.copyFrom(te);



					} // if (min_dist ...
				} // if (G > noiseThreshold
			} // for (k=0;...

			// Insert new target point into queue
			if (!has_moved ) {
				targetX[queuePointer] = e.x;
				targetY[queuePointer] = e.y;
				queuePointer++;
				if (queuePointer >= queueLength) {
					queuePointer = 0;
				}
			}
		} // for (BasicEvent ...

		// Check which clusters havent been updated for a long time
		for (k=0; k<numGaussians; k++) {
			if ((max_time-age[k]) > maxAge) {
				// Perform Brownian motion
				/*float angle = (float) (2.0*Math.PI*Math.random());
                centerX[k] += moveDistance * Math.cos(angle);
                centerY[k] += moveDistance * Math.sin(angle);
                if (centerX[k]<0) centerX[k] = 0;
                if (centerY[k]<0) centerY[k] = 0;
                if (centerX[k]>128) centerX[k] = 128;
                if (centerY[k]>128) centerY[k] = 128; */

				// Move to position in the target queue
				moveRandom(k);

				// Assume random position
				/* centerX[k] = (float) (Math.random() * 127.99);
                centerY[k] = (float) (Math.random() * 127.99); */

				// for (kk=0; kk<3; kk++)
				//    color[k][kk] = (float) Math.random();

				// Create event for update
				// Maybe not a good idea, since there are no real events there
				/* TrackerEvent te = new TrackerEvent();
                te.setX((short) centerX[k]);
                te.setY((short) centerY[k]);
                te.trackerID = k;
                te.setTimestamp((int) max_time);

                BasicEvent o=(BasicEvent)outItr.nextOutput();
                o.copyFrom(te); */
			}
		}

		//        System.out.println("Finished with filterPacket");
		return out;

	}

	@Override
	public void resetFilter() {
		alloc_gaussians();
		init_queue();
	}

	@Override
	public void initFilter() {
		resetFilter();
	}

	public int getGaussRadius() {
		return gaussRadius;
	}

	synchronized public void setGaussRadius(int gaussRadius) {
		this.gaussRadius = gaussRadius;
		putInt("gaussRadius",gaussRadius); // store the preference value

		alloc_gaussians();

		circleX = new float[N+1];
		circleY = new float[N+1];
		for (int i=0; i<=N; i++) {
			circleX[i] = (float) (Math.cos((i*2.0*Math.PI)/N));
			circleY[i] = (float) (Math.sin((i*2.0*Math.PI)/N));
		}
	}

	public float getLearnRate() {
		return learnRate;
	}

	synchronized public void setLearnRate(float learnRate) {
		if ((learnRate > 0) && (learnRate <=1)) {
			this.learnRate = learnRate;
			putFloat("learnRate",learnRate);
		}
	}

	public float getNoiseThreshold() {
		return noiseThreshold;
	}

	synchronized public void setNoiseThreshold(float noiseThreshold) {
		this.noiseThreshold = noiseThreshold;
		putFloat("noiseThreshold",noiseThreshold);
	}

	public int getNumGaussians() {
		return numGaussians;
	}

	synchronized public void setNumGaussians(int numGaussians) {
		this.numGaussians = numGaussians;
		putInt("numGaussians",numGaussians);
		alloc_gaussians();
	}

	public float getDistFactor() {
		return distFactor;
	}

	synchronized public void setDistFactor(float distFactor) {
		this.distFactor = distFactor;
		putFloat("distFactor",distFactor);
	}

	public int getMaxAge() {
		return maxAge;
	}

	synchronized public void setMaxAge(int maxAge) {
		this.maxAge = maxAge;
		putInt("maxAge",maxAge);
	}

	public float getMoveDistance() {
		return moveDistance;
	}

	synchronized public void setMoveDistance(float moveDistance) {
		this.moveDistance = moveDistance;
		putFloat("moveDistance",moveDistance);
	}

	public boolean isPassAllSpikes() {
		return passAllSpikes;
	}

	synchronized public void setPassAllSpikes(boolean passAllSpikes) {
		this.passAllSpikes = passAllSpikes;
		putBoolean("passAllSpikes",passAllSpikes);
	}

	/** Converts from HSV to RGB  (assuming */
	public static float[] HSV2RGB(float hue, float saturation, float value) {
		int h1 = (int) Math.floor(hue / 60.0f);
		float f = ((hue/60.0f) - h1);
		float p = value*(1-saturation);
		float q = value*(1-(saturation*f));
		float t = value*(1-(saturation*(1-f)));

		float[] tmp = new float[3];
		switch (h1) {
			case 0: tmp[0]=value; tmp[1]=t; tmp[2]=p; break;
			case 6: tmp[0]=value; tmp[1]=t; tmp[2]=p; break;
			case 1: tmp[0]=q; tmp[1]=value; tmp[2]=p; break;
			case 2: tmp[0]=p; tmp[1]=value; tmp[2]=t; break;
			case 3: tmp[0]=p; tmp[1]=q; tmp[2]=value; break;
			case 4: tmp[0]=t; tmp[1]=p; tmp[2]=value; break;
			case 5: tmp[0]=value; tmp[1]=p; tmp[2]=q; break;
		}

		return tmp;
	}



	/** Draws location of Gaussians */
	@Override
	public void annotate(GLAutoDrawable drawable) {
		if (drawCircles) {
			GL2 gl=drawable.getGL().getGL2(); // gets the OpenGL GL context. Coordinates are in chip pixels, 0,0 is LL
			gl.glLineWidth(3); // set line width in screen pixels (not chip pixels)
			int i,k;

			for (k=0; k<numGaussians; k++) {
				gl.glColor4f(color[k][0],color[k][1],color[k][2],.5f); // set color blue
				gl.glBegin(GL.GL_LINE_LOOP); // start drawing a line loop
				for (i=0; i<=N; i++) {
					gl.glVertex2f(centerX[k]+(Sigma[k]*circleX[i]), centerY[k]+(Sigma[k]*circleY[i]));
				}
				gl.glEnd();
			}
		}
	}

	synchronized public void update(Observable o, Object arg) {
		//        if(!isFilterEnabled()) return;
		initFilter();
		resetFilter();
	}

	// Returns the center x-coordinate for a cluster
	synchronized public float getCenterX(int k) {
		if ((k>=0) && (k<numGaussians)) {
			return centerX[k];
		}
		else {
			return -1.0f;
		}
	}

	// Returns the center y-coordinate for a cluster
	synchronized public float getCenterY(int k) {
		if ((k>=0) && (k<numGaussians)) {
			return centerY[k];
		}
		else {
			return -1.0f;
		}
	}

	// Moves a center in the direction of a target point
	synchronized public int moveTo(int k, float targetX, float targetY, float rate) {
		if ((k>=0) && (k<numGaussians)) {
			float diffX = targetX - centerX[k];
			float diffY = targetY - centerY[k];
			centerX[k] += rate * diffX;
			centerY[k] += rate * diffY;

			double dist = Math.pow(centerX[k]-targetX,2) + Math.pow(centerY[k]-targetY,2);
			// System.out.println("di" + dist);
			if (dist< (minDistance*minDistance)) {
				// It too close to target point, move randomly
				moveRandom(k);
				// System.out.println("Move to dist " + dist + " / rate " + rate + " D: " +diffX + " / " + diffY);

				//centerX[k] -= minDistance* diffX;
				//centerY[k] -= minDistance* diffY;
				return 1;
			}
			else {
				return 0;
			}
		} else {
			return 0;
		}
	}

	// Set the center x-coordinate for a cluster
	synchronized public void setCenterX(int k, float newCenterX) {
		if ((k>=0) && (k<numGaussians)) {
			centerX[k] = newCenterX;
		}
	}

	// Returns the center y-coordinate for a cluster
	synchronized public void setCenterY(int k, float newCenterY) {
		if ((k>=0) && (k<numGaussians)) {
			centerY[k] = newCenterY;
		}
	}

	// Makes the colors of two clusters more similar
	public void updateColors(int k, int target, float rate) {
		if ((k>=0) && (k<numGaussians) && (target>=0) && (target<numGaussians)) {
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

	// Returns the age of a cluster
	public float getAge(int k) {
		if ((k>=0) && (k<numGaussians)) {
			return age[k];
		}
		else {
			return -1.0f;
		}
	}

	// Sets the age of a cluster
	public void setAge(int k, float newAge) {
		if ((k>=0) && (k<numGaussians)) {
			age[k] = newAge;
		}
	}

	public boolean isDrawCircles() {
		return drawCircles;
	}

	synchronized public void setDrawCircles(boolean drawCircles) {
		this.drawCircles = drawCircles;
		putBoolean("drawCircles",drawCircles);
	}

	public int getQueueLength() {
		return queueLength;
	}

	synchronized public void setQueueLength(int queueLength) {
		this.queueLength = queueLength;
		putInt("queueLength", queueLength);
		queuePointer = 0;
		init_queue();
	}

	// Sets a cluster to a new random position
	public void moveRandom(int k) {
		// Move to position in the target queue
		int targetIdx = (int) (queueLength * Math.random());
		if (targetIdx >= queueLength) {
			targetIdx = queueLength-1;
		}
		centerX[k] = targetX[targetIdx];
		centerY[k] = targetY[targetIdx];
		directX[k] = 0.0f;
		directY[k] = 0.0f;
		alpha[k] = learnRate;


	}

	public float getMinDistance() {
		return minDistance;
	}

	synchronized public void setMinDistance(float minDistance) {
		this.minDistance = minDistance;
		putFloat("minDistance", minDistance);
	}

	public float getAdaptRate() {
		return adaptRate;
	}

	synchronized public void setAdaptRate(float adaptRate) {
		if ((adaptRate >= 0) && (adaptRate <=1)) {
			this.adaptRate = adaptRate;
			putFloat("adaptRate", adaptRate);
		}
	}

	public float getPeripheryBorder() {
		return peripheryBorder;
	}

	synchronized public void setPeripheryBorder(float peripheryBorder) {
		if ((peripheryBorder >= 0) && (peripheryBorder <=1)) {
			this.peripheryBorder = peripheryBorder;
			putFloat("peripheryBorder",peripheryBorder);
		}
	}

	// Sets the movement direction of a Gaussian
	public void setDirection(int k, float dirX, float dirY) {
		if ((k>=0) && (k<numGaussians)) {
			directX[k] = dirX;
			directY[k] = dirY;
		}
	}

	// Returns the movement in X-direction for a Gaussian
	public float getDirectionX(int k) {
		if ((k>=0) && (k<numGaussians)) {
			return directX[k];
		}
		else {
			return 0f;
		}
	}

	// Returns the movement in Y-direction for a Gaussian
	public float getDirectionY(int k) {
		if ((k>=0) && (k<numGaussians)) {
			return directY[k];
		}
		else {
			return 0f;
		}
	}
}
