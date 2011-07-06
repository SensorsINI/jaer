/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2011;

import java.util.Arrays;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Tracks median event location as example for Telluride 2011
 * @author tobi
 */
@Description("Tracks median event location as example for Telluride 2011") // adds this string as description of class for jaer GUIs
public class MedianTrackerTell2011 extends EventFilter2D implements FrameAnnotater {

    /** The length of the median filter. Value is gotten from stored Preferences.
     * 
     */
    private int filterLength = getInt("filterLength", 50);
    private int[] xs, ys;
    private int ptr = 0;
    private int[] x2, y2;
    private int xmed = 0, ymed = 0;

    public MedianTrackerTell2011(AEChip chip) {
        super(chip);
        allocMem();
        // add this string tooltip to FilterPanel GUI control for filterLength
        setPropertyTooltip("filterLength", "length of median filter; median is middle value of x and y address of the filterLength last addresses");
    }

    void allocMem() {
        ptr=0;
        xs = new int[filterLength];
        ys = new int[filterLength];
        x2 = new int[filterLength];
        y2 = new int[filterLength];
    }

    /** Called when events come to us. Thread safe so that we don't enter while resetting or allocating memory.
     * 
     * @param in the input packet
     * @return the filtered packet
     */
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        for (BasicEvent e : in) { // iterate over all input events
            xs[ptr] = e.x;  // store the incoming event's x and y address in our ring buffer
            ys[ptr] = e.y;
            // increment and wrap the the ring buffer pointer
            ptr++;
            if (ptr >= filterLength) {
                ptr = 0;
            }
        }
        // copy the addresses to tmp buffers, sort the tmp buffers to find the median x and y addresses
        System.arraycopy(xs, 0, x2, 0, filterLength);
        System.arraycopy(ys, 0, y2, 0, filterLength);
        Arrays.sort(x2); // TODO we could be much more efficient here by storing into a sorted list
        Arrays.sort(y2);
        xmed = x2[filterLength / 2]; // these are median values
        ymed = y2[filterLength / 2];
//        System.out.println("xmed="+xmed+" ymed="+ymed);
        return in;
    }

    /** Called when "Reset" button is pushed or logged data is rewound
     * Thread safe so that we don't reset while iterating over events.
     */
    @Override
    synchronized public void resetFilter() {
        allocMem();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    /**
     * @return the filterLength
     */
    public int getFilterLength() {
        return filterLength;
    }

    /**
     * @param filterLength the filterLength to set
     */
    synchronized public void setFilterLength(int filterLength) {
        this.filterLength = filterLength;
        putInt("filterLength",filterLength); // store the preference value
        allocMem();
    }

    /** Draws location of median */
    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl=drawable.getGL(); // gets the OpenGL GL context. Coordinates are in chip pixels, 0,0 is LL
        gl.glColor4f(1,0,0,.5f); // set color white
        gl.glLineWidth(3); // set line width in screen pixels (not chip pixels)
        gl.glBegin(GL.GL_LINE_LOOP); // start drawing a line loop
        final int D=5;
        gl.glVertex2f(xmed-D/2, ymed-D/2); // draw the loop TODO we could indicate e.g. the variance by size of box
        gl.glVertex2f(xmed+D/2, ymed-D/2);
        gl.glVertex2f(xmed+D/2, ymed+D/2);
        gl.glVertex2f(xmed-D/2, ymed+D/2);
        gl.glEnd(); // make sure you stop the line loop
    }
}
