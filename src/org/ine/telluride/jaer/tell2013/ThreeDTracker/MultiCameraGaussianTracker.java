/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2013.ThreeDTracker;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 * 
 * @author danny
 */
@Description("Fit a gaussian for each camera") // this annotation is used for tooltip to this class in the chooser.
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MultiCameraGaussianTracker extends EventFilter2D implements FrameAnnotater {

	private float[] xmeans, ymeans;
	private float[] xstds, ystds;
	private float mixingRate = getFloat("mixingRate", 0.01f); // how much we mix the new value into the running means
	private int numCameras = getInt("numCameras", 2); // how many cameras we have

	private final int SEGMENTS = 40;
	private float[] circleX;
	private float[] circleY;
	private float[][] color;

	public MultiCameraGaussianTracker(AEChip chip) {
		super(chip);
		setPropertyTooltip("mixingRate", "rate that mean location is updated in events. 1 means instantaneous and 0 freezes values");
		xmeans = new float[numCameras];
		ymeans = new float[numCameras];
		xstds = new float[numCameras];
		ystds = new float[numCameras];
		for (int i=0; i<numCameras; i++){
			xmeans[i] = chip.getSizeX() / 2;
			ymeans[i] = chip.getSizeY() / 2;
			xstds[i] = 10;
			ystds[i] = 10;
		}

		circleX = new float[SEGMENTS+1];
		circleY = new float[SEGMENTS+1];
		for (int i=0; i<=SEGMENTS; i++) {
			circleX[i] = (float) (Math.cos((i*2.0*Math.PI)/SEGMENTS));
			circleY[i] = (float) (Math.sin((i*2.0*Math.PI)/SEGMENTS));
		}

	}

	/** The main filtering method. It computes the mean location using an event-driven update of location and then
	 * filters out events that are outside this location by more than the radius.
	 * @param in input packet
	 * @return output packet
	 */
	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		for (BasicEvent o : in) { // iterate over all events in input packet
			int camera = ((MultiCameraEvent) o).getCamera();

			xmeans[camera] = ((1 - mixingRate) * xmeans[camera]) + (o.x * mixingRate);
			ymeans[camera] = ((1 - mixingRate) * ymeans[camera]) + (o.y * mixingRate);

			xstds[camera] = ((1 - mixingRate) * xstds[camera]) + (Math.abs(o.x - xmeans[camera]) * mixingRate);
			ystds[camera] = ((1 - mixingRate) * ystds[camera]) + (Math.abs(o.y - ymeans[camera]) * mixingRate);

			if (xstds[camera] >= 30) {
				xstds[camera] = 30;
			}
			if (ystds[camera] >= 30) {
				ystds[camera] = 30;
			}
		}
		return in; // return the output packet
	}

	/** called when filter is reset
	 * 
	 */
	@Override
	public void resetFilter() {
		for (int i=0; i<numCameras; i++){
			xmeans[i] = chip.getSizeX() / 2;
			ymeans[i] = chip.getSizeY() / 2;
		}
	}

	@Override
	public void initFilter() {
	}

	/** Called after events are rendered. Here we just render something to show the mean location.
	 * 
	 * @param drawable the open GL surface.
	 */
	@Override
	public void annotate(GLAutoDrawable drawable) { // called after events are rendered
		GL2 gl = drawable.getGL().getGL2(); // get the openGL context
		Chip2D chip = getChip();
		gl.glPushMatrix();

		// rotate and align viewpoint for filters
		gl.glRotatef(chip.getCanvas().getAngley(), 0, 1, 0); // rotate viewpoint by angle deg around the upvector
		gl.glRotatef(chip.getCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the upvector
		gl.glTranslatef(chip.getCanvas().getOrigin3dx(), chip.getCanvas().getOrigin3dy(), 0);

		for (int k=0; k<numCameras; k++){
			gl.glPushMatrix();

			gl.glColor4f(1.0f, 1.0f, 0.0f, 0.5f);

			if(k == 0) {
				gl.glTranslatef(xmeans[k], ymeans[k], 0.0f);
			}
			else {
				gl.glTranslatef(0.0f, ymeans[k], xmeans[k]);
			}

			gl.glBegin(GL.GL_LINE_LOOP); // start drawing a line loop
			for (int i=0; i<=SEGMENTS; i++) {
				if(k == 0){
					gl.glVertex3f((xstds[k]*2)*circleX[i],
						(ystds[k]*2)*circleY[i],
						0.0f);
				}
				else {
					gl.glVertex3f( 0.0f,
						(ystds[k]*2)*circleY[i],
						(xstds[k]*2)*circleX[i]);
				}
			}
			gl.glEnd();

			gl.glPopMatrix();

			/*
			 * 
			 */
		}

		drawPrism(xmeans[0],(ymeans[1]+ymeans[0])/2, xmeans[1], (ystds[1]+ystds[0]), (xstds[1]+xstds[0]),gl);

		gl.glPopMatrix();

	}

	/**
	 * @return the mixingRate
	 */
	public float getMixingRate() { // the getter and setter beans pattern allows introspection to build the filter GUI
		return mixingRate;
	}

	/**
	 * @param mixingRate the mixingRate to set
	 */
	public void setMixingRate(float mixingRate) {
		float old=this.mixingRate;
		this.mixingRate = mixingRate;
		putFloat("mixingRate", mixingRate); // stores the last chosen value in java preferences
		getSupport().firePropertyChange("mixingRate", old, mixingRate);
	}
	/**
	 * @return the mixingRate
	 */
	public float getNumCameras() { // the getter and setter beans pattern allows introspection to build the filter GUI
		return numCameras;
	}

	/**
	 * @param mixingRate the mixingRate to set
	 */
	public void setNumCameras(int numCameras) {
		int old=this.numCameras;
		this.numCameras = numCameras;
		putFloat("numCameras", numCameras); // stores the last chosen value in java preferences
		getSupport().firePropertyChange("numCameras", old, numCameras);
	}


	void drawPrism(float x, float y, float z, float height, float width, GL2 gl)
	{
		gl.glBegin(GL2.GL_QUADS);

		gl.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
		gl.glVertex3f(x, y, z);
		gl.glVertex3f(x, y, z + width);
		gl.glVertex3f(x, y+height, z + width);
		gl.glVertex3f(x, y+height, z);

		gl.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
		gl.glVertex3f(x, y, z + width);
		gl.glVertex3f(x + width, y, z + width);
		gl.glVertex3f(x + width, y+height, z + width);
		gl.glVertex3f(x, y+height, z + width);

		gl.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
		gl.glVertex3f(x + width, y, z + width);
		gl.glVertex3f(x + width, y, z);
		gl.glVertex3f(x + width, y+height, z);
		gl.glVertex3f(x + width, y+height, z + width);

		gl.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
		gl.glVertex3f(x + width, y, z);
		gl.glVertex3f(x, y, z);
		gl.glVertex3f(x, y+height, z);
		gl.glVertex3f(x + width, y+height, z);

		gl.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
		gl.glVertex3f(x, y+height, z);
		gl.glVertex3f(x, y+height, z + width);
		gl.glVertex3f(x + width, y+height, z + width);
		gl.glVertex3f(x + width, y+height, z);

		gl.glEnd();
	}

}
