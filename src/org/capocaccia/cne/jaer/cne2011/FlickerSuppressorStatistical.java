/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.capocaccia.cne.jaer.cne2011;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
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
 * @author Andrew Dankers, Rodolphe Heliot, Tobi Delbruck CNE 2011
 */
@Description("Suppresses global flicker output from DVS retina input") // this annotation is used for tooltip to this class in the chooser. 
public class FlickerSuppressorStatistical extends EventFilter2D implements FrameAnnotater {

    // we´ll compute these for incoming packets:
    float spatialVariance, temporalVariance;
    int   packetEnergy;

        float x_acc  = 0.0f;
        float y_acc  = 0.0f;
        float t_acc  = 0.0f;
        float mean_x = 0.0f;
        float mean_y = 0.0f;
        float mean_t = 0.0f;
        float dist_acc = 0.0f;
        float time_acc = 0.0f;


    // control settings:
    private float spatialVarianceThresh  = getFloat("spatialVarianceThresh",  48.0f);
    private float temporalVarianceThresh = getFloat("temporalVarianceThresh", 50000.0f);
    private int   packetEnergyThresh     = getInt("packetEnergyThresh",       1000);

    public FlickerSuppressorStatistical(AEChip chip) {
        super(chip);
    }

    int retina_x_size = chip.getSizeX();
    int retina_y_size = chip.getSizeY();
    boolean shouldFilter = false;

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {

        spatialVariance  = 0.0f;
        temporalVariance = 0.0f;
        packetEnergy     = 0;
        shouldFilter     = false;

        x_acc  = 0.0f;
        y_acc  = 0.0f;
        t_acc  = 0.0f;
        mean_x = 0.0f;
        mean_y = 0.0f;
        mean_t = 0.0f;

        // iterate over all events in input packet, get energy, means:
        for (BasicEvent o : in) { 
            //accumulate energy:
            packetEnergy++;
            //get mean in time:
            t_acc += o.timestamp;
            //get mean in space:
            x_acc += o.x;
            y_acc += o.y;
        }
        mean_x = x_acc/packetEnergy;
        mean_y = y_acc/packetEnergy;
        mean_t = t_acc/packetEnergy;

        //now get mean spatial and temporal variances (i.e, mean distances from means):
        dist_acc = 0.0f;
        time_acc = 0.0f;
        for (BasicEvent o : in) {
            dist_acc += Math.sqrt((mean_x-o.x)*(mean_x-o.x) + (mean_y-o.y)*(mean_y-o.y));
            time_acc += Math.abs(mean_t-o.timestamp);
        }
        spatialVariance  = dist_acc/packetEnergy;
        temporalVariance = time_acc/packetEnergy;
        
        //if enough energy and big spatial variance
        //and small temporal variance, need to filter!
        if ( spatialVariance  > spatialVarianceThresh  &&
             temporalVariance < temporalVarianceThresh &&
             packetEnergy     > packetEnergyThresh
             ){
                shouldFilter = true;
        }
        else{
                shouldFilter = false;
        }

        checkOutputPacketEventType(in); // makes sure that built-in output packet is initialized with input type of events, so we can copy input events to them
        OutputEventIterator itr = out.outputIterator(); // important call to construct output event iterator and reset it to the start of the output packet

        // iterate over input events creating filter output
        for (BasicEvent e : in) {
            if ( shouldFilter ) {
                //do nothing for now
            }
            else {
                BasicEvent outEvent = itr.nextOutput(); // get the next output event object
                outEvent.copyFrom(e);    // copy input event fields to out
            }
        }

        return out; // return the output packet
    }

    /** called when filter is reset
     * 
     */
    @Override
    public void resetFilter() {
     //  setSpatialVarianceThresh(48.0f);
     //  setTemporalVarianceThresh(50000.0f);
     //  setPacketEnergyThresh(1000);
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
      /*  GL2 gl = drawable.getGL().getGL2(); // get the openGL context
        gl.glColor4f(1, 0, 0, .3f); // choose RGB color and alpha<1 so we can see through the square
        for (int cptx=0; cptx< Nb_xblocks; cptx++) {
            for (int cpty=0; cpty< Nb_yblocks; cpty++) {
                if (macro_pixels_count[cptx][cpty]>= MB_threshold)
                gl.glRectf((cptx)*xblock_size, (cpty)*yblock_size,(cptx+1)*xblock_size -1, (cpty+1)*yblock_size-1); // draw a rectangle over suppressed macroblocks
            }
        }
        */
    }

    /**
     * @return the spatialVarianceThresh
     */
    public float getSpatialVarianceThresh() {
        return spatialVarianceThresh;
    }

    /**
     * @param spatialVarianceThresh
     */
    public void setSpatialVarianceThresh(float spatialVarianceThresh) {
        float old = this.spatialVarianceThresh; // save the old value
        this.spatialVarianceThresh = spatialVarianceThresh;
        putFloat("spatialVarianceThresh", spatialVarianceThresh);
        getSupport().firePropertyChange("spatialVarianceThresh", old, spatialVarianceThresh); // updates the GUI if some other filter, e.g. changes this parameter
    }


    /**
     * @return temporalVarianceThresh
     */
    public float getTemporalVarianceThresh() {
        return temporalVarianceThresh;
    }

    /**
     * @param temporalVarianceThresh
     */
    public void setTemporalVarianceThresh(float temporalVarianceThresh) {
        float old = this.temporalVarianceThresh; // save the old value
        this.temporalVarianceThresh = temporalVarianceThresh;
        putFloat("temporalVarianceThresh", temporalVarianceThresh);
        getSupport().firePropertyChange("temporalVarianceThresh", old, temporalVarianceThresh); // updates the GUI if some other filter, e.g. changes this parameter
    }

    /**
     * @return PacketEnergyThresh
     */
    public int getPacketEnergyThresh() {
        return packetEnergyThresh;
    }

    /**
     * @param packetEnergyThresh to set
     */
    public void setPacketEnergyThresh(int packetEnergyThresh) {
        float old = this.packetEnergyThresh; // save the old value
        this.packetEnergyThresh = packetEnergyThresh;
        putInt("packetEnergyThresh", packetEnergyThresh);
        getSupport().firePropertyChange("packetEnergyThresh", old, packetEnergyThresh); // updates the GUI if some other filter, e.g. changes this parameter
    }
}
