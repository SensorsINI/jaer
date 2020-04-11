/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * An filter that filters out noise according to Guo, S., Z. Kang, L. Wang, S. Li, and W. Xu. 2020. 
 * “HashHeat: An O(C) Complexity Hashing-Based Filter for Dynamic Vision Sensor.” 
 * In 2020 25th Asia and South Pacific Design Automation Conference (ASP-DAC), 452–57. 
 * ieeexplore.ieee.org. https://doi.org/10.1109/ASP-DAC47756.2020.9045268.
 *
 * @author Shssha Guo, tobi
 */
@Description("Filters out uncorrelated background activity noise according to "
        + "Guo, S., Z. Kang, L. Wang, S. Li, and W. Xu. 2020. 'HashHeat: An O(C) Complexity Hashing-Based Filter for Dynamic Vision Sensor.' "
        + "In 2020 25th Asia and South Pacific Design Automation Conference (ASP-DAC), 452–57. ieeexplore.ieee.org. https://doi.org/10.1109/ASP-DAC47756.2020.9045268..")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class HashHeatNoiseFilter extends AbstractNoiseFilter implements Observer {


    /**
     * the time in timestamp ticks (1us at present) that a spike needs to be
     * supported by a prior event in the neighborhood by to pass through
     */
    private boolean letFirstEventThrough = getBoolean("letFirstEventThrough", true);

    /**
     * the amount to subsample x and y event location by in bit shifts when
     * writing to past event times map. This effectively increases the range of
     * support. E.g. setting subSamplingShift to 1 quadruples range because both
     * x and y are shifted right by one bit
     */
    private int subsampleBy = getInt("subsampleBy", 0);

    int[][] lastTimesMap;
    private int ts = 0; // used to reset filter
    private int sx;
    private int sy;
    private final int DEFAULT_TIMESTAMP=Integer.MIN_VALUE;
    
    private int randomSeed=getInt("randomSeed",0); // seed for hash functions
    private int numHashFunctions=getInt("numHashFunctions",4);
    private int mArrayLength=getInt("mArrayLength",128);
    private int mArrayBits=getInt("mArrayBits",8);
    private int eventCountWindow=getInt("eventCountWindow",5000);

    private int[][] hashCooeficients;
    private int[][] mArrays;
    private int mArrayResetEventCount=0;
    
    public HashHeatNoiseFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        setPropertyTooltip("subsampleBy", "Past events are spatially subsampled (address right shifted) by this many bits");
        setPropertyTooltip("letFirstEventThrough", "After reset, let's first event through; if false, first event from each pixel is blocked");
        setPropertyTooltip("randomSeed", "random seed for hash functions");
        setPropertyTooltip("numHashFunctions", "number of hash functions");
        setPropertyTooltip("mArrayLength", "number of bins of hash function output histogram");
        setPropertyTooltip("mArrayBits", "number of bits in each mArrayHistogramBin");
        setPropertyTooltip("eventCountWindow", "constant-count window length in events");
    }

    
    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. filtering may occur
     * in place in the in packet.
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (lastTimesMap == null) {
            allocateMemory(chip);
        }
        totalEventCount = 0;
        filteredOutEventCount = 0;

        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        for (Object eIn : in) {
            if (eIn == null) {
                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            }
            BasicEvent e = (BasicEvent) eIn;
            if (e.isSpecial()) {
                continue;
            }

            totalEventCount++;
            short x = (short) (e.x >>> subsampleBy), y = (short) (e.y >>> subsampleBy);
            if ((x < 0) || (x > sx) || (y < 0) || (y > sy)) {
                filteredOutEventCount++;
                continue;
            }

            ts = e.timestamp;
            int lastT = lastTimesMap[x][y];
            int deltaT = (ts - lastT);

            if (false) {
                e.setFilteredOut(true);
                filteredOutEventCount++;
            }

            // For each event write the event's timestamp into the
            // lastTimesMap array at neighboring locations lastTimesMap[x][y]=ts;
            // Don't write to ourselves, we need support from neighbor for
            // next event.
            // Bounds checking here to avoid throwing expensive exceptions.
            if (((x > 0) && (x < sx)) && ((y > 0) && (y < sy))) {
                lastTimesMap[x - 1][y] = ts;
                lastTimesMap[x + 1][y] = ts;
                lastTimesMap[x][y - 1] = ts;
                lastTimesMap[x][y + 1] = ts;
                lastTimesMap[x - 1][y - 1] = ts;
                lastTimesMap[x + 1][y + 1] = ts;
                lastTimesMap[x - 1][y + 1] = ts;
                lastTimesMap[x + 1][y - 1] = ts;
            }
        }

        return in;
    }

    @Override
    public synchronized final void resetFilter() {
        initFilter();
    }

    @Override
    public void update(Observable o, Object arg) {
        if ((arg != null) && ((arg == Chip2D.EVENT_SIZEX) || (arg == Chip2D.EVENT_SIZEY))) {
            resetFilter();
        }
    }

    @Override
    public final void initFilter() {
        allocateMemory(chip);
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
    }

    private void allocateMemory(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0)) {
            lastTimesMap = new int[chip.getSizeX()][chip.getSizeY()];
            for (int[] arrayRow : lastTimesMap) {
                Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
            }
        }
    }

    public Object getFilterState() {
        return lastTimesMap;
    }


    // <editor-fold defaultstate="collapsed" desc="getter-setter for --SubsampleBy--">
    public int getSubsampleBy() {
        return subsampleBy;
    }

    /**
     * Sets the number of bits to subsample by when storing events into the map
     * of past events. Increasing this value will increase the number of events
     * that pass through and will also allow passing events from small sources
     * that do not stimulate every pixel.
     *
     * @param subsampleBy the number of bits, 0 means no subsampling, 1 means
     * cut event time map resolution by a factor of two in x and in y
     */
    public void setSubsampleBy(int subsampleBy) {
        if (subsampleBy < 0) {
            subsampleBy = 0;
        } else if (subsampleBy > 4) {
            subsampleBy = 4;
        }
        this.subsampleBy = subsampleBy;
        putInt("subsampleBy", subsampleBy);
    }
    // </editor-fold>

    /**
     * @return the letFirstEventThrough
     */
    public boolean isLetFirstEventThrough() {
        return letFirstEventThrough;
    }

    /**
     * @param letFirstEventThrough the letFirstEventThrough to set
     */
    public void setLetFirstEventThrough(boolean letFirstEventThrough) {
        this.letFirstEventThrough = letFirstEventThrough;
        putBoolean("letFirstEventThrough", letFirstEventThrough);
    }

}
