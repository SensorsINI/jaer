/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import java.util.Arrays;
import java.util.Observable;
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
public class HashHeatNoiseFilter extends AbstractNoiseFilter  {

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
//    protected int heatThr = getInt("heatThr", 3); // heatThr should be less than or equal to numHashFunctions
    protected int loadHashFunctions = getInt("genHashFuncFlag", 1); // load existing hash values, otherwise generate new hash funcitons
    protected float width = getFloat("width", 2); // width: a parameter for calculating hash values,
    protected int mArrayLength = getInt("mArrayLength", 128);
    protected int mArrayBits = getInt("mArrayBits", 16);
    protected int eventCountWindow = getInt("eventCountWindow", 5000);
    protected int changePoint = getInt("changePoint", 2000); // the number of events when changing threshold for mArray, adjustable according to eventCountWindow
//    protected float thresholdCorrelationFactor = getFloat("thresholdCorrelationFactor", .1f);

    private double[][] hashCooeficients; // indexed by [hashfunctoin][coefficient, coefficient for x,y,t,b]
    private int[] mArrays;
    
//    private int[][] mArrays; // indexed by hashfunction output
    private int mArrayResetEventCount = 0; // count of events after which to reset the array
    private final int NUM_HASH_COEFFICIENTS = 4; // x,y,t,b
    private final int MAX_HASH_FUNCTION_COEFFICIENT_VALUE = 16; // TODO what is bound on coefficient? Is it signed?
    private float mArrayThrInit = getFloat("mArrayThrInit", 20); // init threshold, if too much noise, set higher, if too little, set lower
    private float mArrayThr;
    
    public HashHeatNoiseFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("subsampleBy", "Past events are spatially subsampled (address right shifted) by this many bits");
        setPropertyTooltip("letFirstEventThrough", "After reset, let's first event through; if false, first event from each pixel is blocked");
        setPropertyTooltip("randomSeed", "random seed for hash functions");
        setPropertyTooltip("numHashFunctions", "number of hash functions");
        setPropertyTooltip("mArrayLength", "number of bins of hash function output histogram");
        setPropertyTooltip("mArrayBits", "number of bits in each mArrayHistogramBin");
        setPropertyTooltip("eventCountWindow", "constant-count window length in events");
        setPropertyTooltip("thresholdCorrelationFactor", "an event is correlated (not noise) if the hash functeion output histogram bin count is above this fraction of standard");
        
        setPropertyTooltip("genHashFuncFlag", "constant-count window length in events");
        setPropertyTooltip("width", "constant-count window length in events");
        setPropertyTooltip("changePoint", "constant-count window length in events");
        setPropertyTooltip("mArrayThrInit", "constant-count window length in events");
        
    }

    
    
//    private int[] getHashValues(short x, short y, int ts) {
//        int[] hashvalues = new int[numHashFunctions];
//        for(int i=0;i<numHashFunctions;i++){
//            int[] h=hashCooeficients[i];
//            int hash= (int) ((x*h[0]+y*h[1]+ts*h[2]+h[3]) / width) % mArrayLength;
//            hashvalues[i] = hash;
//        }
//        // determine of the event is filtered out or not
//        return hashvalues; // TODO implementation missing
//    }
    
    
    private void updateMArray(int[] hashValues, int updateValue) {
        // TODO compute threshold here?
        // compute hashes and accumulate to m arrays
        final int maxCount=(1<<mArrayBits);
        for(int i=0;i<numHashFunctions;i++){
            int hash = hashValues[i];
            int newValue = mArrays[hash] + updateValue;
            newValue=newValue>maxCount?maxCount:newValue;
            mArrays[hash] = newValue;
        }
    }
    
    /** filter a single event
     * 
     * @param x
     * @param y
     * @param ts
     * @param numEvent
     * @return true to filter out this event 
     */
    private boolean filterEvent(short x, short y, int ts) {
        
        // TODO compute threshold here?
        // compute hashes and accumulate to m arrays
//        int[] hashvalues = getHashValues(x,y,ts);
        int[] hashValues = new int[numHashFunctions];
        
        int flagCount = 0;
        for(int i=0;i<numHashFunctions;i++){
//            int hash = hashvalues[i];
            double[] h=hashCooeficients[i]; // get the hash function with 4 parameters a1, a2, a3, b
            int tmp = (int) ((x*h[0]+y*h[1]+ts*h[2]+h[3]) / width);
//            int c = tmp / mArrayLength;
            int hash = Math.floorMod(tmp, mArrayLength);        
            hashValues[i] = hash;

            int mArrayValue = mArrays[hash];
//            System.out.printf("hash value %d %d %d% .2f\n", i, hash, mArrayValue, mArrayThr);
            
            if (mArrayValue > mArrayThr) {
                flagCount ++;
            }
        }
        
        int heatThr = numHashFunctions - 1;
//        System.out.printf("hash flagcount %d %d %.2f\n", flagCount, heatThr, mArrayThr);
        if (flagCount >= heatThr) {
            updateMArray(hashValues, 2);
            return false;
        }else{
            // this event is regarded as noise, and will be filtered
            updateMArray(hashValues, 1);
            return true;
        }
        // determine of the event is filtered out or not
//        return false; // TODO implementation missing
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
//        totalEventCount = 0;
        filteredOutEventCount = 0;
        int firstts = 0;

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
            
            if (totalEventCount == 0){
                int sum = 0;
                //Advanced for loop
                for( int num : mArrays) {
                    sum = sum+num;
                }
                System.out.printf(" mArray at first event %d %d\n", totalEventCount, sum);
                firstts = e.timestamp;
            } 

            totalEventCount++;
            short x = (short) (e.x >>> subsampleBy), y = (short) (e.y >>> subsampleBy);
            if ((x < 0) || (x > sx) || (y < 0) || (y > sy)) {
                filteredOutEventCount++;
                continue;
            }

            ts = e.timestamp; // TODO how do we get our timestamp for hashing computation?  is it by relative time?
            
            int lastT = lastTimesMap[x][y];
            lastTimesMap[x][y] = ts;
            
            if (totalEventCount == 1){
                continue;
            }
//            if (letFirstEventThrough && lastT == firstts) {
//                continue;
//            }
//            int deltaT = (ts - lastT);
            
            
            if (totalEventCount == changePoint){
                mArrayThr = totalEventCount * numHashFunctions * 2 / mArrayLength;
            }
            boolean filterOut = filterEvent(x, y, (ts-firstts));
    
            if (filterOut) {
                
                e.setFilteredOut(true);
                filteredOutEventCount++;
                
            }
            
            if (totalEventCount == (eventCountWindow)){ // should be the last event of a frame
                // reset the marray
                mArrays = new int[mArrayLength];  
                Arrays.fill(mArrays, 0); 
                mArrayThr = mArrayThrInit;
//                System.out.printf(" filteredOutEventCount %d %d\n", totalEventCount, filteredOutEventCount);
                totalEventCount = 0;
            }
            
            

        }

        return in;
    }

    @Override
    public synchronized final void resetFilter() {
        mArrays = new int[mArrayLength];
        Arrays.fill(mArrays, 0);
//        mArrayThrInit = 20;
        generateHashFunctions();
    }

    @Override
    public final void initFilter() {
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
        allocateMemory(chip);
    }

    private void allocateMemory(AEChip chip) {
        generateHashFunctions();
        mArrays = new int[mArrayLength];
        Arrays.fill(mArrays, 0);
//        mArrays = new int[numHashFunctions][mArrayLength];
        if ((chip != null) && (chip.getNumCells() > 0)) {
            lastTimesMap = new int[chip.getSizeX()][chip.getSizeY()];
            for (int[] arrayRow : lastTimesMap) {
                Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
            }
        }
    }

    private void generateHashFunctions() {
        hashCooeficients = new double[numHashFunctions][NUM_HASH_COEFFICIENTS];
        if ((loadHashFunctions == 1) && (numHashFunctions == 4) ){
            double[][] paralist =   
//            {{0.4517, -0.1303, 0.1837, 0.2963},
//                    {0.8620, -1.3617, 0.4550, 0.1835},
//                    {-0.3349, 0.5528, 1.0391, 0.0811},
//                    {1.2607, 0.6601, -0.0679, 0.4359}};
            
//            {{0.8284, 0.2177, -1.9092, 0.3909},
//                    {-0.3020, 1.8136, 0.9149, 0.4857},
//                    {1.3094, -1.0447, -0.3483, 0.9274},
//                    {1.5024, 0.7304, 0.4908, 0.3433}};
            
            {{-0.1241, 1.4897, 1.4090, 0.9595},
                    {0.6715, -1.2075, 0.7172, 0.9340},
                    {0.4889, 1.0347, 0.7269, 0.3922},
                    {0.2939,-0.7873, 0.8884, 0.0318}};

//            {{0.3714,-0.2256,1.1174,0.2551},
//                    {0.0326,0.5525,1.1006,0.9593},
//                    {0.0859,-1.4916,-0.7423,0.2575},
//                    {2.3505,-0.6156,0.7481,0.2435}};
            
            hashCooeficients = paralist;
        
        }
        else{
        
        Random r = new Random(randomSeed);
        for (int i = 0; i < numHashFunctions; i++) {
            for (int j = 0; j < NUM_HASH_COEFFICIENTS; j++) {
                hashCooeficients[i][j] = Math.random();
//                hashCooeficients[i][j] = r.nextInt(MAX_HASH_FUNCTION_COEFFICIENT_VALUE);
                double tmp = Math.random();
                if (tmp < 0.5){
                    hashCooeficients[i][j] = - hashCooeficients[i][j];
                }
            }
        }
        StringBuilder s = new StringBuilder("Hash functions are\n");
        for (int i = 0; i < numHashFunctions; i++) {
            s.append(String.format("%.4fx + %.4fy +%.4ft+%.4f\n", hashCooeficients[i][0], hashCooeficients[i][1], hashCooeficients[i][2], hashCooeficients[i][3]));
        }
        log.info(s.toString());
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

//    /**
//     * @return the thresholdCorrelationFactor
//     */
//    public float getThresholdCorrelationFactor() {
//        return thresholdCorrelationFactor;
//    }
//
//    /**
//     * @param thresholdCorrelationFactor the thresholdCorrelationFactor to set
//     */
//    public void setThresholdCorrelationFactor(float thresholdCorrelationFactor) {
//        this.thresholdCorrelationFactor = thresholdCorrelationFactor;
//        putFloat("thresholdCorrelationFactor", thresholdCorrelationFactor);
//    }
    
        /**
     * @return the loadHashFunctions
     */
    public int getLoadHashFunctions() {
        return loadHashFunctions;
    }

    /**
     * @param loadHashFunctions the loadHashFunctions to set
     */
    public void setLoadHashFunctions(int loadHashFunctions) {
        this.loadHashFunctions = loadHashFunctions;
        putInt("loadHashFunctions", loadHashFunctions);
    }
    
        /**
     * @return the width
     */
    public float getWidth() {
        return width;
    }

    /**
     * @param width the width to set
     */
    public void setWidth(float width) {
        this.width = width;
        putFloat("width", width);
    }
    
        /**
     * @return the changePoint
     */
    public int getChangePoint() {
        return changePoint;
    }

    /**
     * @param changePoint the changePoint to set
     */
    public void setChangePoint(int changePoint) {
        this.changePoint = changePoint;
        putInt("changePoint", changePoint);
    }
    
    public float getMArrayThr() {
        return mArrayThrInit;
    }

    /**
     * @param width the mArrayThrInit to set
     */
    public void setMArrayThr(float mArrayThrInit) {
        this.mArrayThrInit = mArrayThrInit;
        putFloat("mArrayThrInit", mArrayThrInit);
    }

}
