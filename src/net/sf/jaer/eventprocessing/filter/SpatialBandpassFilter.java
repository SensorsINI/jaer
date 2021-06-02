/*
 * SpatialBandpassFilter.java
 *
 * Created on May 13, 2006, 7:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 13, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.eventprocessing.filter;

import java.util.ArrayList;
import java.util.Observable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Does an event-based spatial high pass filter, so that only small objects pass
 * through.
 *
 * @author tobi
 */
@Description("Does an event-based spatial highpass filter, so that only small objects pass through. <br>"
        + "if ((e.timestamp - surroundTimestamps[e.x][e.y]) > dtSurround) {\n" +
"                o.nextOutput().copyFrom(e);\n" +
"            }")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class SpatialBandpassFilter extends EventFilter2D {

    private int centerRadius = getPrefs().getInt("SpatialBandpassFilter.centerRadius", 0);
    private int surroundRadius = getPrefs().getInt("SpatialBandpassFilter.surroundRadius", 1);
    int sizex, sizey;
    /**
     * the time in timestamp ticks (1us at present) that a spike in surround
     * will inhibit a spike from center passing through.
     */
    private int dtSurround = getPrefs().getInt("SpatialBandpassFilter.dtSurround", 8000);

    int[][] surroundTimestamps, centerTimestamps;

    /**
     * Creates a new instance of SpatialBandpassFilter
     */
    public SpatialBandpassFilter(AEChip c) {
        super(c);
        setPropertyTooltip("dtSurround", "");
        setPropertyTooltip("centerRadius", "radius of center");
        setPropertyTooltip("surroundRadius", "radius of surrounding region");
    }

    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (!yes) {
            // free memory
            surroundTimestamps = null;
            centerTimestamps = null;
        } else {
            initFilter();
        }
    }

    public Object getFilterState() {
        return null;
    }

    public void resetFilter() {
        initFilter();
    }

    synchronized public void initFilter() {
        computeOffsets();
    }

    void checkMaps() {
        if (surroundTimestamps == null || surroundTimestamps.length != chip.getSizeX() || surroundTimestamps[0].length != chip.getSizeY()) {
            allocateMaps();
        }
    }

    void allocateMaps() {
        sizex = chip.getSizeX() - 1;
        sizey = chip.getSizeY() - 1; // minus 1 to avoid -1 in loop
        surroundTimestamps = new int[sizex + 1][sizey + 1];
        centerTimestamps = new int[sizex + 1][sizey + 1];
    }

    // Offset is a relative position
    final class Offset {

        int x, y;

        Offset(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // these arrays hold relative offsets to write to for center and surround timestamp splatts
    Offset[] centerOffsets, surroundOffsets;

    // computes an array of offsets that we write to when we getString an event
    synchronized void computeOffsets() {
        ArrayList<Offset> surList = new ArrayList<Offset>();
        ArrayList<Offset> cenList = new ArrayList<Offset>();

        for (int x = -surroundRadius; x <= surroundRadius; x++) {
            for (int y = -surroundRadius; y <= surroundRadius; y++) {
                if ((x <= centerRadius && x >= -centerRadius && y <= centerRadius && y >= -centerRadius)) {
                    // if we are in center we are not surround
                    cenList.add(new Offset(x, y));
                } else {
                    surList.add(new Offset(x, y));
                }
            }
        }
        centerOffsets = new Offset[1];
        centerOffsets = (Offset[]) cenList.toArray(centerOffsets);
        surroundOffsets = new Offset[1];
        surroundOffsets = (Offset[]) surList.toArray(surroundOffsets);
//        log.info("splatting "+surroundOffsets.length+" neighbors for each event");
    }

//    public int getDtCenter() {
//        return dtCenter;
//    }
//
//    public void setDtCenter(int dtCenter) {
//        this.dtCenter = dtCenter;
//        prefs.putInt("SpatialBandpassFilter.dtCenter",dtCenter);
//    }
    public int getDtSurround() {
        return dtSurround;
    }

    /**
     * sets the time in timestamp ticks (1us at present) that a spike in
     * surround will inhibit a spike from center passing through.
     *
     * @param dtSurround the time in us
     */
    public void setDtSurround(int dtSurround) {
        this.dtSurround = dtSurround;
        getPrefs().putInt("SpatialBandpassFilter.dtSurround", dtSurround);
    }

    public int getCenterRadius() {
        return centerRadius;
    }

    /**
     * sets the center radius, 0 meaning a single pixel. This value is clipped
     * to min 0.
     *
     * @param centerRadius the radius in pixels for a square area. 0 is 1 pixel,
     * 1 is 9 pixels (3x3), etc.
     */
    synchronized public void setCenterRadius(int centerRadius) {
        if (centerRadius < 0) {
            centerRadius = 0;
        } else if (centerRadius >= surroundRadius) {
            centerRadius = surroundRadius - 1;
        }
        this.centerRadius = centerRadius;
        getPrefs().putInt("SpatialBandpassFilter.centerRadius", centerRadius);
        computeOffsets();
    }

    public int getSurroundRadius() {
        return surroundRadius;
    }

    /**
     * sets the surround radius. This value is clipped to be at least the center
     * radius plus 1.
     *
     * @param surroundRadius the radius in pixels for a square area. 1 is 9
     * pixels (3x3), etc.
     */
    synchronized public void setSurroundRadius(int surroundRadius) {
        if (surroundRadius < centerRadius + 1) {
            surroundRadius = centerRadius + 1;
        }
        this.surroundRadius = surroundRadius;
        getPrefs().putInt("SpatialBandpassFilter.surroundRadius", surroundRadius);
        computeOffsets();
    }

    synchronized public EventPacket filterPacket(EventPacket in) {
        if (in == null) {
            return null;
        }
        checkOutputPacketEventType(in);
        checkMaps();
        int n = in.getSize();
        if (n == 0) {
            return in;
        }
        OutputEventIterator o = out.outputIterator();
        for (Object obj : in) {
            PolarityEvent e = (PolarityEvent) obj;
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }

            // if the event occurred too close after a surround spike don't pass it.
            if ((e.timestamp - surroundTimestamps[e.x][e.y]) > dtSurround) {
                o.nextOutput().copyFrom(e);
            }
            writeSurround(e);
//            if(writeSurround(i)==false) return in;
        }
//        if(true) throw new RuntimeException("fake exception");
        return out;
    }

    final void writeSurround(PolarityEvent i) {// write surround
        for (int k = 0; k < surroundOffsets.length; k++) {
            Offset d = surroundOffsets[k];
//            if(d==null){
//                log.warning("null surroundOffset, disabling SpatialBandpassFilter");
//                setFilterEnabled(false);
//                return false;
//            }
            int kx = i.x + d.x;
            if (kx < 0 || kx > sizex) {
                continue;
            }
            int ky = i.y + d.y;
            if (ky < 0 || ky > sizey) {
                continue;
            }
            surroundTimestamps[kx][ky] = i.timestamp;
        }
//        return true;
    }

}
