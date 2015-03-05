/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.label;

import java.util.Random;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.orientation.ApsDvsOrientationEvent;
import net.sf.jaer.event.OutputEventIterator;

/**
 * Experimental labeler which only outputs endstopped cell activity. The outputs from an enclosed SimpleOrientationFilter are used to filter
 the events to only pass events with past orientation events in one of the two directions along the orientation.
 *
 * @author tobi

 This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("End-stopped orientation labeler")
public class EndStoppedOrientationLabeler extends SimpleOrientationFilter {

    private float minActivityDifference = getPrefs().getFloat("EndStoppedOrientationLabeler.minActivityDifference", .4f);
    private int maxDtToUse = getPrefs().getInt("EndStoppedOrientationLabeler.maxDtToUse", 100000);
    private boolean disableEndstopping = false;
    private boolean probabilisitcFiltering = getPrefs().getBoolean("EndStoppedOrientationLabeler.probabilisitcFiltering", false);
    private int esLength = getPrefs().getInt("EndStoppedOrientationLabeler.length", 3);
    private int esWidth = getPrefs().getInt("EndStoppedOrientationLabeler.width", 0);
    /** holds the times of the last output orientation events that have been generated */
    private int[][][] lastOutputTimesMap;
    private Random random = new Random();
    /**
    
     Offsets from a pixel to pixels forming the receptive field (RF) for an endstopped orientation response.
     * They are computed whenever the RF size changes.
     * First index is orientation 0-NUM_TYPES, second is index over array.
     * The two sets of offsets are for the two directions for the endstopping.
     */
    protected Dir[][] offsets0 = null,

    /**
     * Offsets from a pixel to pixels forming the receptive field (RF) for an endstopped orientation response.They are computed whenever the RF size changes.
 First index is orientation 0-NUM_TYPES, second is index over array.
 The two sets of offsets are for the two directions for the endstopping.
     */
    offsets1 = null;

    public EndStoppedOrientationLabeler(AEChip chip) {
        super(chip);
        final String endstop = "End Stopping";
        setPropertyTooltip(endstop, "minActivityDifference", "<html>min activity difference (fraction of pixels with recent events) betweeen <br> two sides of endstopped orientation cell to pass events");
        setPropertyTooltip(endstop, "maxDtToUse", "orientation event delta times larger than this in us are ignored and assumed to come from another edge");
        setPropertyTooltip(endstop, "disableEndstopping", "disables endstopping filtering so you can see the orientation filter output");
        setPropertyTooltip(endstop, "endStoppedWidth", "width of RF, total is 2*width+1");
        setPropertyTooltip(endstop, "endStoppedLength", "length of half of RF, total length is length on each side");
        setPropertyTooltip(endstop, "probabilisitcFiltering", "orientation event is passed probabilisitcally based on measured activity ratio: 0 ratio difference=don't pass, 1 ratio difference=pass.");
    }
    EventPacket esOut = new EventPacket(ApsDvsOrientationEvent.class);

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        int sss = getSubSampleShift();

        int sx = chip.getSizeX(), sy = chip.getSizeY(); // for bounds checking
//        checkOutputPacketEventType(in);
        checkMaps();
        EventPacket filt = super.filterPacket(in);
        for (Object o : filt) {
            ApsDvsOrientationEvent e = (ApsDvsOrientationEvent) o;
        }

        OutputEventIterator outItr = esOut.outputIterator();
        for (Object o : filt) {
            ApsDvsOrientationEvent ie = (ApsDvsOrientationEvent) o;
            int thisori=0; // ie.orientation;
            lastOutputTimesMap[ie.x >>> sss][ie.y >>> sss][thisori] = ie.timestamp;
            if (!ie.hasOrientation) {
                continue;
            }
            // For this event, we look in the past times map in each direction. If we find events in one direction but not the other,
            // then the event is output, otherwise it is not.
            // Actually we need to generate a fan-like RF for this which extends out in the orientation direction and perpindicular to 
            // the ori direction so that we can 
            // count past events in the neighborhood, not just the line.
            boolean pass = false;
            int n0 = 0, n1 = 0; // prior event counts, each side
            Dir[] d = offsets0[ie.orientation];
            for (int i = 0; i < d.length; i++) {
                int x = (ie.x + d[i].x) >>> sss, y = (ie.y + d[i].y) >>> sss;
                if (x < 0 || x >= sx || y < 0 || y >= sy) {
                    continue;
                }
                int dt = ie.timestamp - lastOutputTimesMap[x][y][thisori];
                if (dt > maxDtToUse || dt <= 0) {
                    continue;
                }
                n0++;
            }
            d = offsets1[ie.orientation];
            for (int i = 0; i < d.length; i++) {
                int x = (ie.x + d[i].x) >>> sss, y = (ie.y + d[i].y) >>> sss;
                if (x < 0 || x >= sx || y < 0 || y >= sy) {
                    continue;
                }
                int dt = ie.timestamp - lastOutputTimesMap[x][y][thisori];
                if (dt > maxDtToUse || dt <= 0) {
                    continue;
                }
                n1++;
            }
            float activityRatioDifference = (float) Math.abs(n0 - n1) / d.length;
            if (!probabilisitcFiltering) {
                pass = (disableEndstopping) || (activityRatioDifference > getMinActivityDifference());
            } else {
                pass = (disableEndstopping) || activityRatioDifference > random.nextFloat()*getMinActivityDifference();
            }
//            System.out.println(String.format(" n0=%d n1=%d pass=%s",n0,n1,pass));
            if (pass) {
                ApsDvsOrientationEvent oe = (ApsDvsOrientationEvent) outItr.nextOutput();
                oe.copyFrom(ie);
            }
        }
        return esOut;
    }

    /** precomputes offsets for iterating over neighborhoods */
    protected void computeRFOffsets() {
        // compute array of Dir for each orientation and each of two endstopping directions.

        rfSize = getEndStoppedLength() * (2 * getEndStoppedWidth() + 1);
        offsets0 = new Dir[NUM_TYPES][rfSize];
        offsets1 = new Dir[NUM_TYPES][rfSize];
        for (int ori = 0; ori < NUM_TYPES; ori++) {
//            System.out.println("\nori="+ori);
            Dir d = baseOffsets[ori];
            int ind = 0;
            for (int s = 1; s <= getEndStoppedLength(); s++) {
                Dir pd = baseOffsets[(ori + 2) % NUM_TYPES]; // this is offset in perpindicular direction
                for (int w = -getEndStoppedWidth(); w <= getEndStoppedWidth(); w++) {
                    // for each line of RF
                    offsets0[ori][ind++] = new Dir(s * d.x + w * pd.x, s * d.y + w * pd.y);
//                    System.out.print(offsets0[ori][ind-1]+" ");
                }
            }
            ind = 0;
            for (int s = -getEndStoppedLength(); s < 0; s++) {
                Dir pd = baseOffsets[(ori + 2) % NUM_TYPES]; // this is offset in perpindicular direction
                for (int w = -getEndStoppedWidth(); w <= getEndStoppedWidth(); w++) {
                    // for each line of RF
                    offsets1[ori][ind++] = new Dir(s * d.x + w * pd.x, s * d.y + w * pd.y);
                }
            }
        }
    }

    synchronized protected void allocateMaps() {
        if (!isFilterEnabled()) {
            return;
        }
        if (chip != null) {
            lastOutputTimesMap = new int[chip.getSizeX()][chip.getSizeY()][NUM_TYPES];
        }
        computeRFOffsets();
    }

    private void checkMaps() {
        if (lastOutputTimesMap == null) {
            allocateMaps();
        }
    }

    /**
     * @return the minActivityDifference
     */
    public float getMinActivityDifference() {
        return minActivityDifference;
    }

    /**
     * @param minActivityDifference the minActivityDifference to set
     */
    public void setMinActivityDifference(float minActivityDifference) {
        if (minActivityDifference < 0) {
            minActivityDifference = 0;
        } else if (minActivityDifference > 1) {
            minActivityDifference = 1;
        }
        this.minActivityDifference = minActivityDifference;
        getPrefs().putFloat("EndStoppedOrientationLabeler.minActivityDifference", minActivityDifference);
    }

    public float getMinMinActivityDifference() {
        return 0;
    }

    public float getMaxMinActivityDifference() {
        return 1;
    }

    /**
     * @return the maxDtToUse
     */
    public int getMaxDtToUse() {
        return maxDtToUse;
    }

    /**
     * @param maxDtToUse the maxDtToUse to set
     */
    public void setMaxDtToUse(int maxDtToUse) {
        this.maxDtToUse = maxDtToUse;
        getPrefs().putInt("EndStoppedOrientationLabeler.maxDtToUse", maxDtToUse);
    }

    /**
     * @return the disableEndstopping
     */
    public boolean isDisableEndstopping() {
        return disableEndstopping;
    }

    /**
     * @param disableEndstopping the disableEndstopping to set
     */
    public void setDisableEndstopping(boolean disableEndstopping) {
        this.disableEndstopping = disableEndstopping;
    }

    public int getEndStoppedLength() {
        return esLength;
    }
    public static final int MAX_LENGTH = 6;

    public int getEndStoppedWidth() {
        return esWidth;
    }

    /** @param width the width of the RF, 0 for a single line of pixels, 1 for 3 lines, etc
     */
    synchronized public void setEndStoppedWidth(int width) {
        if (width < 0) {
            width = 0;
        }
        if (width > esLength - 1) {
            width = esLength - 1;
        }
        this.esWidth = width;
        allocateMaps();
        getPrefs().putInt("EndStoppedOrientationLabeler.width", width);
    }

    /** Sets the length of the receptive field.
     * @param length the width of the RF, 0 for a single line of pixels, 1 for 3 lines, etc
     */
    synchronized public void setEndStoppedLength(int length) {
        if (length < 0) {
            length = 0;
        }
        this.esLength = length;
        allocateMaps();
        getPrefs().putInt("EndStoppedOrientationLabeler.length", length);
    }

    /**
     * @return the probabilisitcFiltering
     */
    public boolean isProbabilisitcFiltering() {
        return probabilisitcFiltering;
    }

    /**
     * @param probabilisitcFiltering the probabilisitcFiltering to set
     */
    public void setProbabilisitcFiltering(boolean probabilisitcFiltering) {
        this.probabilisitcFiltering = probabilisitcFiltering;
        getPrefs().putBoolean("EndStoppedOrientationLabeler.probabilisitcFiltering", probabilisitcFiltering);
    }
}
