/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.capocaccia.cne.jaer.cne2011;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Suppresses global flicker output from DVS retina input. Synchronous increases of activity from a number of subsampled regions in the retina is detected and suppresses
 * transmission of the retina input to the filter output.
 * 
 * @author Rodolphe Héliot, Tobi Delbruck, CNE 2011
 */
@Description("Suppresses global flicker output from DVS retina input") // this annotation is used for tooltip to this class in the chooser. 
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class FlickerSuppessor extends EventFilter2D implements FrameAnnotater {

    float xmean, ymean;  // we'll compute these
    private float mixingRate = getFloat("mixingRate", 0.01f); // how much we mix the new value into the running means
    private float radiusOfTransmission = getFloat("radiusOfTransmission", 10); // how big around mean location we transmit events

    public FlickerSuppessor(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        for (BasicEvent o : in) { // iterate over all events in input packet
            xmean = (1 - mixingRate) * xmean + o.x * mixingRate; // update means using x and y addresses of input events
            ymean = (1 - mixingRate) * ymean + o.y * mixingRate;
        }
        checkOutputPacketEventType(in); // makes sure that built-in output packet is initialized with input type of events, so we can copy input events to them
        float maxsq = radiusOfTransmission * radiusOfTransmission; // speed up
        OutputEventIterator itr = out.outputIterator(); // important call to construct output event iterator and reset it to the start of the output packet
        for (BasicEvent e : in) { // now iterate input events again, and only copy out events with the radius of the mean 
            float dx = e.x - xmean;
            float dy = e.y - ymean;
            float sq = dx * dx + dy * dy;
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

    /** Called after events are rendered
     * 
     * @param drawable the open GL surface. 
     */
    @Override
    public void annotate(GLAutoDrawable drawable) { // called after events are rendered
        GL gl = drawable.getGL(); // get the openGL context
        gl.glColor4f(1, 1, 0, .3f); // choose RGB color and alpha<1 so we can see through the square
        gl.glRectf(xmean - 4, ymean - 4, xmean + 4, ymean + 4); // draw a little rectangle over the mean location
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
        this.mixingRate = mixingRate;
        putFloat("mixingRate", mixingRate); // stores the last chosen value in java preferences
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
