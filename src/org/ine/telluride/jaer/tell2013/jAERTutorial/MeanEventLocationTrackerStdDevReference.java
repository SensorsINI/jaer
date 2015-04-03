/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2013.jAERTutorial;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Example filter used in Capo Caccia Neuromorphic Cognition Workshop 2011.
 * This filter computes a running mean event location and only transmits
 * events within some chosen radius of the mean.
 * It also draws a rectangle over the mean location and optionally
 * only transmits events that are within a desired radius of the mean
 * location.
 * 
 * @author tobi
 */
@Description("Example class for CNE 2011") // this annotation is used for tooltip to this class in the chooser.
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MeanEventLocationTrackerStdDevReference extends EventFilter2D implements FrameAnnotater {

	float xmean, ymean;  // we'll compute these
	float xstd, ystd;
	private float mixingRate = getFloat("mixingRate", 0.01f); // how much we mix the new value into the running means
	private float radiusOfTransmission = getFloat("radiusOfTransmission", 10); // how big around mean location we transmit events

	public MeanEventLocationTrackerStdDevReference(AEChip chip) {
		super(chip);
		setPropertyTooltip("mixingRate", "rate that mean location is updated in events. 1 means instantaneous and 0 freezes values");
		setPropertyTooltip("radiusOfTransmission","radius in pixels around the mean that events are tranmitted out");
	}

	/** The main filtering method. It computes the mean location using an event-driven update of location and then
	 * filters out events that are outside this location by more than the radius.
	 * @param in input packet
	 * @return output packet
	 */
	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		for (BasicEvent o : in) { // iterate over all events in input packet
			xmean = ((1 - mixingRate) * xmean) + (o.x * mixingRate); // update means using x and y addresses of input events
			ymean = ((1 - mixingRate) * ymean) + (o.y * mixingRate);

			xstd = ((1 - mixingRate) * xstd) + (Math.abs(o.x - xmean) * mixingRate);
			ystd = ((1 - mixingRate) * ystd) + (Math.abs(o.y - ymean) * mixingRate);
		}
		checkOutputPacketEventType(in); // makes sure that built-in output packet is initialized with input type of events, so we can copy input events to them
		float maxsq = radiusOfTransmission * radiusOfTransmission; // speed up
		OutputEventIterator itr = out.outputIterator(); // important call to construct output event iterator and reset it to the start of the output packet
		for (BasicEvent e : in) { // now iterate input events again, and only copy out events with the radius of the mean
			float dx = e.x - xmean;
			float dy = e.y - ymean;
			float sq = (dx * dx) + (dy * dy);
			if (sq < maxsq) { // if the event is within the radius
				BasicEvent outEvent = itr.nextOutput(); // get the next output event object
				outEvent.copyFrom(e); // copy input event fields to it
			}
		}

		return out; // return the output packet
	}

	/** called when filter is reset
	 * 
	 */
	@Override
	public void resetFilter() {
		xmean = chip.getSizeX() / 2; // initialize to center of chip coordinates, LL is 0,0
		ymean = chip.getSizeY() / 2;
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
		gl.glColor4f(1.0f, 1.0f, 0.0f, 0.1f); // choose RGB color and alpha<1 so we can see through the square
		gl.glBegin(GL.GL_LINE_LOOP);
		gl.glVertex2f(xmean - xstd, ymean - ystd);
		gl.glVertex2f(xmean + xstd, ymean - ystd);
		gl.glVertex2f(xmean + xstd, ymean + ystd);
		gl.glVertex2f(xmean - xstd, ymean + ystd);
		gl.glVertex2f(xmean - xstd, ymean - ystd);
		gl.glEnd();
		//gl.glRectf(xmean - xstd, ymean - ystd, xmean + xstd, ymean + ystd); // draw a little rectangle over the mean location
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
	 * @return the radiusOfTransmission
	 */
	public float getRadiusOfTransmission() {
		return radiusOfTransmission;
	}

	/**
	 * @param radiusOfTransmission the radiusOfTransmission to set
	 */
	public void setRadiusOfTransmission(float radiusOfTransmission) {
		float old = this.radiusOfTransmission; // save the old value
		this.radiusOfTransmission = radiusOfTransmission;
		putFloat("radiusOfTransmission", radiusOfTransmission);
		getSupport().firePropertyChange("radiusOfTransmission", old, radiusOfTransmission); // updates the GUI if some other filter, e.g. changes this parameter
	}
}
