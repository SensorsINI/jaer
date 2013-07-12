/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.capocaccia.cne.jaer.cne2011;

import javax.media.opengl.GL2;
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
 * @author Rodolphe Heliot, Tobi Delbruck, Andrew Dankers CNE 2011
 */
@Description("Suppresses global flicker output from DVS retina input") // this annotation is used for tooltip to this class in the chooser.
public class FlickerSuppessor extends EventFilter2D implements FrameAnnotater {

	int[][] macro_pixels_count; //we will compute...
	int Nb_active_MBs; //same here...

	private int Nb_xblocks=4;//getInt("Nb_xblocks", 4);
	private int Nb_yblocks=4;//getInt("Nb_yblocks", 4);
	private int MB_threshold = 250;//getInt("MB_threshold", 300);
	private int flickr_NB_MB_thres = (Nb_xblocks*Nb_yblocks)/4;//getInt("flickr_NB_MB_thres", 4);

	int retina_x_size = 128;
	int retina_y_size = 128;
	int xblock_size = retina_x_size/Nb_xblocks;
	int yblock_size = retina_y_size/Nb_yblocks;


	public FlickerSuppessor(AEChip chip) {
		super(chip);
	}


	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {

		macro_pixels_count = new int[Nb_xblocks][Nb_yblocks];  // create an array of integers
		Nb_active_MBs = 0;  //init

		for (int cptx=0; cptx<Nb_xblocks; cptx++) {
			for (int cpty=0; cpty<Nb_yblocks; cpty++) {
				macro_pixels_count[cptx][cpty]=0;
			}
		}

		for (BasicEvent o : in) { // iterate over all events in input packet
			macro_pixels_count[o.x / xblock_size][o.y / xblock_size]++; //increase by one the macro_pixel count where the event belongs to
		}

		for (int cptx=0; cptx<Nb_xblocks; cptx++) {
			for (int cpty=0; cpty<Nb_yblocks; cpty++) {
				if (macro_pixels_count[cptx][cpty]>= MB_threshold) {
					Nb_active_MBs++;
				}
			}
		}

		checkOutputPacketEventType(in); // makes sure that built-in output packet is initialized with input type of events, so we can copy input events to them
		OutputEventIterator itr = out.outputIterator(); // important call to construct output event iterator and reset it to the start of the output packet
		if (Nb_active_MBs < flickr_NB_MB_thres) { // if few macro-pixels are active == no flicker
			for (BasicEvent e : in) { // now iterate input events again, and only copy out events with the radius of the mean
				//if (macro_pixels_count[(int)(e.x / xblock_size)][(int)(e.y / xblock_size)]< MB_threshold) { // if the number of events within the corresponding macroblock is not too high
				BasicEvent outEvent = itr.nextOutput(); // get the next output event object
				outEvent.copyFrom(e); // copy input event fields to it
				//}
			}
		}

		return out; // return the output packet
	}

	/** called when filter is reset
	 * 
	 */
	@Override
	public void resetFilter() {

		//Nb_xblocks=getInt("Nb_xblocks", 4);
		//Nb_yblocks=getInt("Nb_yblocks", 4);


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
		GL2 gl = drawable.getGL().getGL2(); // get the openGL context
		gl.glColor4f(1, 0, 0, .3f); // choose RGB color and alpha<1 so we can see through the square
		/*   for (int cptx=0; cptx< Nb_xblocks; cptx++) {
            for (int cpty=0; cpty< Nb_yblocks; cpty++) {
                if (macro_pixels_count[cptx][cpty]>= MB_threshold)
                gl.glRectf((cptx)*xblock_size, (cpty)*yblock_size,(cptx+1)*xblock_size -1, (cpty+1)*yblock_size-1); // draw a rectangle over suppressed macroblocks
            }
        }*/

	}

	/**
	 * @return the num blocks, x-dir
	 */
	public int getNXBlocks() {
		return Nb_xblocks;
	}

	/**
	 * @param Nb_xblocks the num blocks to set
	 */
	public void setNXBlocks(int Nb_xblocks) {
		this.Nb_xblocks = Nb_xblocks;
		putInt("Nb_xblocks", Nb_xblocks);
	}


	/**
	 * @return the number of blocks, y-dir
	 */
	public int getNYBlocks() {
		return Nb_yblocks;
	}

	/**
	 * @param Nb_yblocks the num of blocks (y-dir) to set, max retina width
	 */
	public void setNYBlocks(int Nb_yblocks) {
		this.Nb_yblocks = Nb_yblocks;
		putInt("Nb_yblocks", Nb_yblocks);
	}

	/**
	 * @return the number of active blocks needed to shut down all the events
	 */
	public int getflickr_NB_MB_thres() {
		return flickr_NB_MB_thres;
	}

	/**
	 * @param number of active blocks needed to shut down all the events
	 */
	public void setflickr_NB_MB_thres(int flickr_NB_MB_thres) {
		this.flickr_NB_MB_thres = flickr_NB_MB_thres;
		putInt("flickr_NB_MB_thres", flickr_NB_MB_thres);
	}

	/**
	 * @return the block event threshold considered flicker
	 */
	public int getBlockEventsThresh() {
		return MB_threshold;
	}

	/**
	 * @param MB_threshold to set
	 */
	public void setBlockEventsThresh(int MB_threshold) {
		int old = this.MB_threshold; // save the old value
		this.MB_threshold = MB_threshold;
		putInt("MB_threshold", MB_threshold);
		getSupport().firePropertyChange("MB_threshold", old, MB_threshold);
	}
}
