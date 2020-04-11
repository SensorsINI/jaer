/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;

/**
 * An filter that filters out noise according to Guo, S., Z. Kang, L. Wang, S.
 * Li, and W. Xu. 2020. “HashHeat: An O(C) Complexity Hashing-Based Filter for
 * Dynamic Vision Sensor.” In 2020 25th Asia and South Pacific Design Automation
 * Conference (ASP-DAC), 452–57. ieeexplore.ieee.org.
 * https://doi.org/10.1109/ASP-DAC47756.2020.9045268.
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
    private final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;

    protected int randomSeed = getInt("randomSeed", 0); // seed for hash functions
    protected int numHashFunctions = getInt("numHashFunctions", 4);
    protected int mArrayLength = getInt("mArrayLength", 128);
    protected int mArrayBits = getInt("mArrayBits", 8);
    protected int eventCountWindow = getInt("eventCountWindow", 5000);
    protected float thresholdCorrelationFactor = getFloat("thresholdCorrelationFactor", .1f);

    private int[][] hashCooeficients; // indexed by [hashfunctoin][coefficient, coefficient for x,y,t,b]
    private int[][] mArrays; // indexed by hashfunction output
    private int mArrayResetEventCount = 0; // count of events after which to reset the array
    private final int NUM_HASH_COEFFICIENTS = 4; // x,y,t,b
    private final int MAX_HASH_FUNCTION_COEFFICIENT_VALUE=16; // TODO what is bound on coefficient? Is it signed?

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
        setPropertyTooltip("thresholdCorrelationFactor", "an event is correlated (not noise) if the hash functeion output histogram bin count is above this fraction of standard");
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
 
    }

    @Override
    public void update(Observable o, Object arg) {
        if ((arg != null) && ((arg == Chip2D.EVENT_SIZEX) || (arg == Chip2D.EVENT_SIZEY))) {
            resetFilter();
        }
    }

    @Override
    public final void initFilter() {
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
        allocateMemory(chip);
    }

    private void allocateMemory(AEChip chip) {
        generateHashFunctions();
        mArrays = new int[numHashFunctions][mArrayLength];
        if ((chip != null) && (chip.getNumCells() > 0)) {
            lastTimesMap = new int[chip.getSizeX()][chip.getSizeY()];
            for (int[] arrayRow : lastTimesMap) {
                Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
            }
        }
    }

    private void generateHashFunctions() {
        hashCooeficients = new int[numHashFunctions][NUM_HASH_COEFFICIENTS];
        Random r = new Random(randomSeed);
        for(int i=0;i<numHashFunctions;i++){
            for(int j=0;j<NUM_HASH_COEFFICIENTS;j++){
                hashCooeficients[i][j]=r.nextInt(MAX_HASH_FUNCTION_COEFFICIENT_VALUE);
            }
        }
        StringBuilder s=new StringBuilder("Hash functions are\n");
        for(int i=0;i<numHashFunctions;i++){
                s.append(String.format("%dx + %dy +%dt+%d\n",hashCooeficients[i][0], hashCooeficients[i][1], hashCooeficients[i][2], hashCooeficients[i][3])) ;
        }
        log.info(s.toString());
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

    /**
     * @return the randomSeed
     */
    public int getRandomSeed() {
        return randomSeed;
    }

    /**
     * @param randomSeed the randomSeed to set
     */
    public void setRandomSeed(int randomSeed) {
        this.randomSeed = randomSeed;
        putInt("randomSeed", randomSeed);
        generateHashFunctions();
    }

    /**
     * @return the numHashFunctions
     */
    public int getNumHashFunctions() {
        return numHashFunctions;
    }

    /**
     * @param numHashFunctions the numHashFunctions to set
     */
    synchronized public void setNumHashFunctions(int numHashFunctions) {
        this.numHashFunctions = numHashFunctions;
        putInt("numHashFunctions", numHashFunctions);
        allocateMemory(chip);
    }

    /**
     * @return the mArrayLength
     */
    public int getmArrayLength() {
        return mArrayLength;
    }

    /**
     * @param mArrayLength the mArrayLength to set
     */
    synchronized public void setmArrayLength(int mArrayLength) {
        this.mArrayLength = mArrayLength;
        putInt("mArrayLength", mArrayLength);
        allocateMemory(chip);
    }

    /**
     * @return the mArrayBits
     */
    public int getmArrayBits() {
        return mArrayBits;
    }

    /**
     * @param mArrayBits the mArrayBits to set
     */
    public void setmArrayBits(int mArrayBits) {
        this.mArrayBits = mArrayBits;
        putInt("mArrayBits", mArrayBits);
    }

    /**
     * @return the eventCountWindow
     */
    public int getEventCountWindow() {
        return eventCountWindow;
    }

    /**
     * @param eventCountWindow the eventCountWindow to set
     */
    public void setEventCountWindow(int eventCountWindow) {
        this.eventCountWindow = eventCountWindow;
        putInt("eventCountWindow", eventCountWindow);
    }

    /**
     * @return the thresholdCorrelationFactor
     */
    public float getThresholdCorrelationFactor() {
        return thresholdCorrelationFactor;
    }

    /**
     * @param thresholdCorrelationFactor the thresholdCorrelationFactor to set
     */
    public void setThresholdCorrelationFactor(float thresholdCorrelationFactor) {
        this.thresholdCorrelationFactor = thresholdCorrelationFactor;
        putFloat("thresholdCorrelationFactor", thresholdCorrelationFactor);
    }

}
