/*
 * A resizer trying to calculate the currently best buffer size by counting how
 * many nxn fields are met by the most recent events. After having filtered a 
 * packet, it is up to date and can return the good buffer size by 
 * getBufferSizeEstimate(..). 
 *
 * For a given number of sampled fields and sample size, an estimate is made 
 * (if not the simple version: taken from a table initialized with 
 * getMaxLLHEstimation(int j,int s,int a), else) 
 * what the real number of occupied field is. This number is averaged over the
 * last OLD_PACKETS packets and the average returned in nowWhatToChangeTheSizeTo().
 *
 * Details: Assumes that sx, sy are powers of two; possibly works otherwise as well.
 * Number of sampled pixels is: min(nr of sampling fields, size of event packet).
 * 
 * The events should have been filtered by a BackgroundActivityFilter before.
 */
package ch.unizh.ini.jaer.projects.elised.dynamicBuffer;

import ch.unizh.ini.jaer.projects.elised.dynamicBuffer.ResizeableRingbuffer;
import ch.unizh.ini.jaer.projects.elised.dynamicBuffer.Ringbuffer;
import java.io.IOException;
import java.util.Arrays;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.TobiLogger;

@Description("Gives a buffer size estimate.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class BufferSizeEstimator extends EventFilter2D{

    private int sx;     
    private int sy;     //Assumption: both sx and sy are powers of 2.
    private int maxSampleSize;       //number of sampling fields
    private int fieldSizeExponentX = 2; //2 and 2 -> 4x4-fields (2^this exponent = field size x)
    private int fieldSizeExponentY = 2;
    private final int maxOccupation = 3;  // Upper bound to occupation sizes that are of interest/will be remembered in nrFieldsWithNrOfPixelsEqual[]. If you lower this below 3, you have to change the logging as well.
    private final int nrOldPacketsRemembered = 7; //average over so many packets
    
    private int[] samplingArray; 
    private int[] sizeTable; 
    private int[] nrFieldsWithNrOfPixelsEqual = new int[maxOccupation+1];  
        // ´|` [arrow up] !The only field of this array that is used right now is the one indexed by 1!
        // For improvements of the estimate, one could use the other fields as well (but you'd have 
        // to know the maths).
        // In array field indexed by i, counts the number of sampling fields in which
        // fell >= i events. Zero occupations aren't counted. (zero-indexed field not used)

    
        /* changeable within jAER viewer */
    private int maxSize = getInt("maxSize",20000);
    private int minSize = getInt("minSize",100);
    private int estimatedAvOccupation = getInt("estimatedAvOccupation",13);  // = average occupation (of all occupied fields, don't count the empty ones; 1 <= estimatedAvOccupation)
    private boolean logBufferSize = getBoolean("logBufferSize", false);  //just for testing
    private boolean simple = getBoolean("simple", true);
    private float minResizeDifference = getFloat("minResizeDifference",0.1f);     // in percent of old size
    private int storageTime = getInt("storageTime",500000);  
        //In micro s. There is a test how long it has been since a resize first would have been send 
        // as necessary, and 'storage time' ms is waited before
        // the resize is actually send as neccessary

    
        /* helper variables for functions, have to be global*/
    private int lastUpdateTime = 0;       //in timestamp units (us, micro seconds)
    private int sumFieldNrEstimates;
    private int sumFieldNrEstimatesSimple;
    private float timeResizeFirstNeccessary;        //in us
    private short helpBit = 0;  // if any sx, sy are powers of 2, this isn't needed. Otherwise it makes sure that the number of fields in x and 
                                // in y direction each is 1 higher than sx/sy >> fieldSizeExponent    
    final Ringbuffer<Integer> oldSizeEstimates = new Ringbuffer(Integer.class, nrOldPacketsRemembered);  
    final Ringbuffer<Integer> oldSizeEstimatesSimple = new Ringbuffer(Integer.class, nrOldPacketsRemembered);
        // ´|` [arrow up] this ringbuffer stores the last nrOldPacketsRemembered nr of estimated nr of occupied fields
    
    static final private TobiLogger bufferLogger = new TobiLogger("BufferLog", "  ");  
    
    
    
    public BufferSizeEstimator(AEChip chip){
        super(chip);
        this.chip = chip;
        resetFilter();
        setTooltips();
    }
    
    //second constructor; to be used if field sizes are not 4x4.
    // fieldSizeExponents: 2^fieldSizeExponentX is the x-size (width) of the sampling fields
    public BufferSizeEstimator(AEChip chip,  int fseX, int fseY){
        super(chip);
        this.chip = chip;
        this.fieldSizeExponentX = fseX;
        this.fieldSizeExponentY = fseY;
        resetFilter();
        setTooltips();
    }
    
    private void setTooltips(){
        setPropertyTooltip("Recalculate Table","maxSize","The maximum buffer size.");
        setPropertyTooltip("Recalculate Table","minSize","The minimum buffer size.");
        setPropertyTooltip("Recalculate Table","estimatedAvOccupation","The parameter to change buffer "
                + "size estimates; should be an estimated number of events in an !occupied! sampling "
                + "field (4x4) which it would be good to have buffered.");
        setPropertyTooltip("Performance","storageTime","In micro s; when buffer size should decrease,"
                + " this time is waited until the new buffer size is sent as neccessary.");
        setPropertyTooltip("Performance","minResizeDifference","In fractions of current buffer size."
                + "Any change to current size smaller than this is ignored. Set this big to decrease "
                + "the frequency of buffer resizes.");
        setPropertyTooltip("Performance","simple", "Estimate buffer size in a simple way: just "
                + "estimatedAvOccupation times the number of fields found occupied; 1000*eAO is maximum.");
        setPropertyTooltip("Logging","logBufferSize","Write buffer size estimates of both versions into "
                + "logfile located in jAER/trunk.");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        int lastSampleSize = Math.min(samplingArray.length, in.size);     //varies
        int sampleSizeNotFiltered = lastSampleSize; //needed as constant bound for the for loop
        Arrays.fill(samplingArray,0);
        Arrays.fill(nrFieldsWithNrOfPixelsEqual,0);
        BasicEvent e;
        for(int i = 0; i<sampleSizeNotFiltered; i++){
            e = in.getEvent(in.size-i-1);
            if (e.isSpecial()){    
                lastSampleSize--;  
                continue;
            }
            
            samplingArray[getArrayIndex(e.x, e.y)]++;
            int k = samplingArray[getArrayIndex(e.x, e.y)];
            if(k > maxOccupation) continue;      //but his field has been counted in counter-fields [0] to [maxOccupation-1] already
            nrFieldsWithNrOfPixelsEqual[k]++;
        }
        
        if (!simple){
            int sizeEstimate = sizeTable[(lastSampleSize*(maxSampleSize+1) + nrFieldsWithNrOfPixelsEqual[1])];
            sumFieldNrEstimates -= oldSizeEstimates.getLast();
            sumFieldNrEstimates += sizeEstimate;
            oldSizeEstimates.add(sizeEstimate);
            }
        else {
            int sizeEstimateSimple = nrFieldsWithNrOfPixelsEqual[1];
            sumFieldNrEstimatesSimple -= oldSizeEstimatesSimple.getLast();
            sumFieldNrEstimatesSimple += sizeEstimateSimple;
            oldSizeEstimatesSimple.add(sizeEstimateSimple);
            }
        lastUpdateTime = in.getLastTimestamp();
        
        
            /* Logging */
        if(logBufferSize){
            float goodSize = getEstimatedAvOccupation()*sumFieldNrEstimates/nrOldPacketsRemembered;
            float goodSizeSimple = getEstimatedAvOccupation()*sumFieldNrEstimatesSimple/nrOldPacketsRemembered;
            int newSizeSaresi1 = nrFieldsWithNrOfPixelsEqual[1];
            int newSizeSaresi2 = nrFieldsWithNrOfPixelsEqual[2];
            int newSizeSaresi3 = nrFieldsWithNrOfPixelsEqual[3];
            bufferLogger.log(in.getLastTimestamp()+" "+estimatedAvOccupation+" "+goodSizeSimple+" "+newSizeSaresi1+" "+newSizeSaresi2+
                    " "+newSizeSaresi3+" "+lastSampleSize +" "+goodSize); //has this format to fit my old R scripts
        }
        
        
        
        
        return in;
    }

    
    /* Return preferable buffer size or, if we are already close to that value, return 
    * the current size.
    * Also, if the buffer is supposed to be made smaller, the resizer waits storageTime ms 
    * before it actually returns the command to resize (that is, a different size). If the 
    * required size grows big enough in the meanwhile, the timer is set to 0 again.
    */
    public int getBufferSizeEstimate(int curBufferSize) {
        float newSize;
        int newIntSize;
        if(simple){ newSize = getEstimatedAvOccupation()*sumFieldNrEstimatesSimple/nrOldPacketsRemembered; }
        else
            {newSize = getEstimatedAvOccupation()*sumFieldNrEstimates/nrOldPacketsRemembered;}
        
        newIntSize = Math.min((int)Math.rint(newSize), getMaxSize());
        newIntSize = Math.max(newIntSize, getMinSize());
        if((Math.abs((float)(newIntSize-curBufferSize)/(float)curBufferSize) >= getMinResizeDifference())){      
                float t = lastUpdateTime;
                if (timeResizeFirstNeccessary == 0) {
                    timeResizeFirstNeccessary = t;}
                if (t - timeResizeFirstNeccessary >= getStorageTime()) {
                    timeResizeFirstNeccessary = 0;
                    return newIntSize;
                }
                else return curBufferSize;
        }
        else {      // the change that would have to be made is too small, return current
                timeResizeFirstNeccessary = 0;
                return curBufferSize;}
    }
    
    
        // for a field ranging from coordinates (0,0) to coordinates (w-1,h-1)
    public boolean isBoundaryPixel(int w, int h, int x, int y, int margin) {
        return !(x >= margin && y >= margin && x < w - margin && y < h - margin);
    }
    
    /* Transforms the events' coordinates to (x,y) array coordinates and 
     * then to 1D array coordinates */
    public int getArrayIndex(int x, int y){
        x = x>>fieldSizeExponentX;
        y = y>>fieldSizeExponentY;
        return (x+(sx>>fieldSizeExponentX + helpBit)*y);
    }
    
    public int getStartingAddressOfSamplingFieldX(int x){
        x = x>>fieldSizeExponentX;
        x = x*2^fieldSizeExponentX;
        return x;    
    }
    
    public int getStartingAddressOfSamplingFieldY(int y){
        y = y>>fieldSizeExponentY;
        y = y*2^fieldSizeExponentY;
        return y;    
    }
    
    
    
        /*  ~Source: "Urn Models and Their Application - An approach to modern 
            discrete probability theory_Norman L.Johnson(Wiley 1977 413s); chapter
            on Occupations, 3.2.4 "estimating the number of urns", but adapted for 
            urns that can take at most a balls. Of course, this can be wrong; has
            to be tested whether this gives a better buffer estimate than simply 
            (nr of fields found occupied) * const.
        
            As in R(...) below: 
                j: nr of fields found occupied
                s: number of sampled pixels
                a: average occupation of each field
    
           Should give an explanation here of what has been adapted; have to look
           everything up for that, so will do that later. What I wrote back
    
           Adaptions to literature:
           How does L(a,k)/L(a,k+1) := R(a,k) change? It's again P(exactly j 
           from k urns occupied) = L(a,k)*f(a,j,s), where
    
           f(a,j,s)= sum [over all possibilities to ..|
                         |choose j n_i's, so that ..  | n!*(a choose n_1)*(a choose n_2)*...*(a choose n_j)
                         |sum of all n_i = s, and ..  |
                         |                0 < n_i <= a] 
        */
    public final int getMaxLLHEstimation(int j,int s,int a){ 
        if(s==0||j==0) return getMinSize()/a;   //all pixel filtered out
        if(j==s){ 
            if(s < maxSampleSize/2)
                return getMinSize()/a;
            else return getMaxSize()/a;
        }
        if(j>s) return 0; //just for filling the table, this entry should never be looked up
        if(R(j,j,s,a) <= 1) return j;
        else{for(int k = j+1; k < getMaxSize()/a; k++){
            if(R(j,k,s,a)<1.0f) return k-1;
            }
        }
        return getMaxSize()/a;
    }
    
    
     /* Probability of finding j fields ocupied, given that total (true) number of 
     *  occupied fields = k, average occupation = a and sampled pixels = s */
     public float R(int j, int k, int s, int a){       
        if(k<j){    //shouldn't happen!
                 return -1.0f;}
        float res = (k+1)/(k+1-j);
        for(float i = 0.0f; i < a; i+= 1.0f) 
            {res = res*(1.0f-s/(a*(k+1.0f)-i));}
        return res;
        }
    
    
    
    
    
    @Override
    synchronized public void resetFilter() {
        this.sx = chip.getSizeX();
        this.sy = chip.getSizeY();
        
        
            // determine number of sampling fields
        if((sx % Math.pow(2, fieldSizeExponentX) == 0) && (sy % Math.pow(2, fieldSizeExponentY) == 0)){      //will take an infinity of time, but this happens only at initialization
            samplingArray = new int[getArrayIndex(sx, sy)];
        } else {
            samplingArray = new int[getArrayIndex(sx, sy)]; // shift by fieldSizeExponent once for reduction in x, once for reduction in y direction
            helpBit = 1;
        }
        maxSampleSize = samplingArray.length;
        
        if(!simple && !(maxSampleSize==0)){  // If maxSampleSize==0, sx, sy aren't known yet. There will be another resetFilter() later.
            initSizeProposalTable();
        }
        
//        sizeTable = new int[(maxSampleSize+1)*maxSampleSize*16];
//        
//            //initialize size-proposal table:
//        for(int ac = this.getEstimatedAvOccupation(); ac<this.getEstimatedAvOccupation()+1;ac++){   //you could make a loop over a out of this as well, but initialization takes a lifetime
//            for(int s=0; s<= maxSampleSize; s++){
//                for(int j = 0; j<= maxSampleSize; j++){
//                    sizeTable[s*(maxSampleSize+1) + (j)] = getMaxLLHEstimation(j,s,ac);
//                }
//            }
//        }
        
        oldSizeEstimates.fill(0);
        oldSizeEstimatesSimple.fill(0);
        sumFieldNrEstimates = 0;
        sumFieldNrEstimatesSimple = 0;
        
        try {
            stopLogging();      //closes all opened logging files
            setupLogging();
        } catch (IOException e) {   //crashes here; or rather: after this, after resetFilter, but before filterPacket or addEvent. Strange: nullPointerException is said to have appeared in addEvent
            e.printStackTrace();
        } 
        
    }

    @Override
    public void initFilter() {
        resetFilter();
    }
    
    public void initSizeProposalTable(){
        sizeTable = new int[(maxSampleSize+1)*maxSampleSize*16];
        
            //write size-proposal table:
        for(int ac = this.getEstimatedAvOccupation(); ac<this.getEstimatedAvOccupation()+1;ac++){   //you could make a loop over a out of this as well, but initialization takes a lifetime
            for(int s=0; s<= maxSampleSize; s++){
                for(int j = 0; j<= maxSampleSize; j++){
                    sizeTable[s*(maxSampleSize+1) + (j)] = getMaxLLHEstimation(j,s,ac);
                }
            }
        }
    
    
    }
    

        /* getter for the array nrFieldsWithNrOfPixelsEqual[]; returns number of
            sampling fields in which >= nr pixels were found */
    public int getLatestNrOfFieldsOccupiedBy(int nr){
        if(nr < 0)
            return 0;
        if(nr == 0)
            return samplingArray.length-nrFieldsWithNrOfPixelsEqual[1];
        if(nr > maxOccupation)
            return -1;
        return nrFieldsWithNrOfPixelsEqual[nr];
    }
    
    public void setupLogging() throws IOException {
        if(isLogBufferSize())
            bufferLogger.setEnabled(true);
        bufferLogger.setColumnHeaderLine("|last timestamp -- estimatedAvOccupation -- estimated size (simple) -- "
                + "(fields found occupied in sample) -- occupied by >=2 -- occupied by >= 3 -- size of sample "
                + "-- estimated size (probabilistic)|  maximum buffer size:"+getMaxSize()+
                ", minimum "+ "buffer size: "+getMinSize());
    }
    
    public void stopLogging() {
        if(isLogBufferSize())
            bufferLogger.setEnabled(false);
    }
    
    /**
     * @return the maxSize
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * @param maxSize the maxSize to set
     */
    public void setMaxSize(int maxSize) {
        if(maxSize < getMinSize()) return;
        this.maxSize = maxSize;
        putInt("maxSize", maxSize);
        resetFilter();
    }

    /**
     * @return the minSize
     */
    public int getMinSize() {
        return minSize;
    }

    /**
     * @param minSize the minSize to set
     */
    public void setMinSize(int minSize) {
        if(minSize < 0) return;
        this.minSize = minSize;
        putInt("minSize", minSize);
        resetFilter();
    }

    /**
     * @return the estimatedAvOccupation
     */
    public int getEstimatedAvOccupation() {
        return estimatedAvOccupation;
    }

    /**
     * @param estimatedAvOccupation the estimatedAvOccupation to set
     */
    public void setEstimatedAvOccupation(int estimatedAvOccupation) {
        this.estimatedAvOccupation = estimatedAvOccupation;
        if(!simple)
            resetFilter();
    }

    /**
     * @return the minResizeDifference
     */
    public float getMinResizeDifference() {
        return minResizeDifference;
    }

    /**
     * @param minResizeDifference the minResizeDifference to set
     */
    public void setMinResizeDifference(float minResizeDifference) {
        this.minResizeDifference = minResizeDifference;
    }

    /**
     * @return the storageTime
     */
    public int getStorageTime() {
        return storageTime;
    }

    /**
     * @param storageTime the storageTime to set
     */
    public void setStorageTime(int storageTime) {
        this.storageTime = storageTime;
        putInt("storageTime", storageTime);
    }

    /**
     * @return the simple
     */
    public boolean isSimple() {
        return simple;
    }

    /**
     * @param simple the simple to set
     */
    public void setSimple(boolean simple) {
        if (this.simple && !simple){
            this.simple = simple;
            resetFilter();}
        else
            this.simple = simple;
        putBoolean("simple", simple);
    }

    /**
     * @return the logBufferSize
     */
    public boolean isLogBufferSize() {
        return logBufferSize;
    }

    /**
     * @param logBufferSize the logBufferSize to set
     */
    public void setLogBufferSize(boolean logBufferSize) {
        this.logBufferSize = logBufferSize;
        bufferLogger.setEnabled(logBufferSize);
    }

    
}
