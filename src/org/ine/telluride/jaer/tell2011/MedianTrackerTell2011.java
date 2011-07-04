/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2011;

import java.util.Arrays;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Tracks median event location
 * @author tobi
 */
public class MedianTrackerTell2011 extends EventFilter2D {

    private int filterLength = getInt("filterLength", 50);
    private int[] xs, ys;
    private int ptr = 0;
    int[] x2, y2;
    int xmed = 0, ymed = 0;

    public MedianTrackerTell2011(AEChip chip) {
        super(chip);
        allocMem();
    }

    void allocMem() {
        ptr=0;
        xs = new int[filterLength];
        ys = new int[filterLength];
        x2 = new int[filterLength];
        y2 = new int[filterLength];
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        for (BasicEvent e : in) {
            xs[ptr] = e.x;
            ys[ptr] = e.y;
            ptr++;
            if (ptr >= filterLength) {
                ptr = 0;
            }
        }
        System.arraycopy(xs, 0, x2, 0, filterLength);
        System.arraycopy(ys, 0, y2, 0, filterLength);
        Arrays.sort(x2);
        Arrays.sort(y2);
        xmed = x2[filterLength / 2];
        ymed = y2[filterLength / 2];
        System.out.println("xmed="+xmed+" ymed="+ymed);
        return in;
    }

    @Override
    public void resetFilter() {
        allocMem();
    }

    @Override
    public void initFilter() {
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
        putInt("filterLength",filterLength);
        allocMem();
    }
}
