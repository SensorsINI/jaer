/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import com.sun.opengl.util.GLUT;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.stereopsis.StereoChipInterface;
import net.sf.jaer.stereopsis.StereoTranslateRotate;

/**
 * Vergence filter for stereo DVS.
 * It estimates the disparity of the closest moving object, and then use it to overlap the events from left and right eyes.
 * A global disparity is obtained from the mass histogram at each point along the x-axis.
 * 
 * @author Jun Haeng Lee
 */
public class StereoVergenceFilter extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {

    StereoChipInterface stereoChip = null;
    StereoTranslateRotate str;

    /**
     * mass histogram of the left eye
     */
    protected HashMap<Integer, ArrayList> histogramLeft = new HashMap<Integer, ArrayList> ();
    /**
     * mass histogram of the right eye
     */
    protected HashMap<Integer, ArrayList> histogramRight = new HashMap<Integer, ArrayList> ();
    /**
     * estimated disparity values
     */
    protected HashMap<Integer, Disparity> disparityValues = new HashMap<Integer, Disparity>();
    /**
     * previous value of the global disparity. this value is used again if the disparity calculation is failed
     */
    protected HashMap<Integer, Disparity> prevDisparity = new HashMap<Integer, Disparity>();
    /**
     * line color of the histogram of the left eye
     */
    protected Color colorLeft = Color.GREEN;
    /**
     * line color of the histogram of the right eye
     */
    protected Color colorRight = Color.ORANGE;
    /**
     * becomes true if this filter is initialized
     */
    protected boolean initialized = false;
    /**
     * Timestamp of the last event ever processed
     */
    protected int prevTimestamp = -1, lastTimestamp = -1;
    /**
     * chip size in x-axis which is obtained by the method stereoChip.getLeft().getSizeX()
     */
    protected int size;
    /**
     * section length when the y-axis is divided into several sections defined by the variable 'numSectionsY'
     */
    protected int deltaY;

    /**
     * disparity limit
     */
    protected int disparityLimit = 0;

    /**
     * uses low limit if true, high limit if false
     */
    protected boolean useLowLimit = true;

    /**
     * if true, considers lowerDisparityLimit as the lower limit of the disparity
     */
    protected boolean enableDisparityLimit = false;

    /**
     *
     */
    int numUpdate;


    private int numSectionsY = getPrefs ().getInt ("StereoVergenceFilter.numSectionsY", 8);
    private float XC_Threshold = getPrefs ().getFloat ("StereoVergenceFilter.XC_Threshold", 0.5f);
    private int massTimeConstantUs = getPrefs ().getInt ("StereoVergenceFilter.massTimeConstantUs", 200000);
    private float massScale = getPrefs ().getFloat ("StereoVergenceFilter.massScale", 0.5f);
    private boolean showHistogram = getPrefs ().getBoolean ("StereoVergenceFilter.showHistogram", true);
    private int subSampleingRatio = getPrefs ().getInt ("StereoVergenceFilter.subSampleingRatio", 4);
    private float massThreshold  = getPrefs ().getFloat ("StereoVergenceFilter.massThreshold", 10.0f);
    private float maxDisparityFractionChipsizeX = getPrefs ().getFloat ("StereoVergenceFilter.maxDisparityFractionChipsizeX", 0.7f);
    private int maxDisparityChangePixels = getPrefs ().getInt ("StereoVergenceFilter.maxDisparityChangePixels", 20);
    private boolean useBipolarDisparity  = getPrefs ().getBoolean ("StereoVergenceFilter.useBipolarDisparity", false);

    /**
     *
     * @param chip
     */
    public StereoVergenceFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        addObserver(this);

        if ( chip != null && chip instanceof StereoChipInterface ){
            this.stereoChip = (StereoChipInterface)chip;
        } else{
            log.log (Level.WARNING, "AEChip {0} is not StereoChipInterface", chip);
        }

        // encloses a StereoTranslateRotate filter to adjust the offset of x-axis
        str = new StereoTranslateRotate (chip);
        FilterChain fc=new FilterChain(chip);
        fc.add(str);
        setEnclosedFilterChain(fc);

        final String display = "Display", histogram = "Histogram", xc = "Cross-correlation", disparity="Disparity";
        setPropertyTooltip (histogram,"numSectionsY","Y-axis is divided into this number of sections to collect bins in each section. Must be one of 1, 4, or 8.");
        setPropertyTooltip (histogram, "massTimeConstantUs","time constant of leaky mass in the mass histogram.");
        setPropertyTooltip (histogram, "massThreshold","mass below this value will be set to zero.");
        setPropertyTooltip (histogram, "subSampleingRatio","sub-sampling ration of event to consruct histogram.");
        setPropertyTooltip (display, "massScale","scales the bins of histogram.");
        setPropertyTooltip (display, "showHistogram","displays histogram on the screen.");
        setPropertyTooltip (xc,"XC_Threshold","Threshold of x-correlation to detect parity.");
        setPropertyTooltip (disparity, "maxDisparityFractionChipsizeX","maximum allowed disparity in fraction of Chipsize.");
        setPropertyTooltip (disparity, "maxDisparityChangePixels","maximum allowed disparity change in pixels per step.");
        setPropertyTooltip (disparity, "useBipolarDisparity","if true, both positive and negative disparities are considered.");
    }

    /**
     * Class to store the statistics of each point of x-axis.
     * It has a mass which increases when there comes an events and decays exponentially when there's no additional event.
     * 
     */
    final public class Bins{
        /**
         * bins of histogram. Mass increases by 1 as every sigle event comes in, and decays exponentially as time passes.
         */
        protected double mass;
        /**
         * Timestamp of the last event constributed to the bins
         */
        protected int lastUpdateTime;

        Bins(){
            mass = 0; // a leaky mass
            lastUpdateTime = 0; // timestamp of the last event
        }

        /** returns the mass at time t
         *
         * @param t : timestamp of the moment requesting the mass
         * @return : mass
         */
        final public double getMassNow(int t) {
            double ret = 0;
            if(lastUpdateTime - t <= 0)
                ret = mass*Math.exp(((float) lastUpdateTime - t) / massTimeConstantUs);

            return ret;
        }

        /** returns the timestamp of the last event
         *
         * @return lastUpdateTime
         */
        final public int getLastUpdateTime() {
            return lastUpdateTime;
        }

        /** save the timestamp of the last event
         *
         * @param lastUpdateTime
         */
        final public void setLastUpdateTime(int lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }

        /** set the mass
         * 
         * @param mass
         */
        final public void setMass(double mass) {
            this.mass = mass;
        }
    }

    /**
     * disparity between left and right eyes
     */
    final public class Disparity{
        /**
         * disparity in Pixels
         */
        protected int disparity;
        /**
         * true if the disparity estimation was a success
         */
        protected boolean valid;

        Disparity(){
            disparity = 0;
            valid = false;
        }

        /**
         * sets the disparity value and its validity
         * @param disparity
         * @param validity
         */
        final void setDisparity(int disparity, boolean validity){
            this.disparity = disparity;
            valid = validity;
        }

        /**
         * returns disparity
         * @return disparity
         */
        final public int getDisparity() {
            return disparity;
        }

        /**
         * sets disparity
         * @param disparity
         */
        final public void setDisparity(int disparity) {
            this.disparity = disparity;
        }

        /**
         * returns true if the disparity estimation was a success
         * @return
         */
        final public boolean isValid() {
            return valid;
        }

        /**
         * sets the disparity validity
         * @param valid
         */
        final public void setValid(boolean valid) {
            this.valid = valid;
        }
    }


    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!initialized){
            initFilter();
        }
        
        // updates the histogram for each event
        int num = 0;
        for(int i=0; i<in.getSize(); i++){
            if(num == subSampleingRatio){
                BinocularEvent be = (BinocularEvent)in.getEvent(i);

                if(prevTimestamp == -1)
                    prevTimestamp = be.timestamp;

                lastTimestamp = be.timestamp;
                updateHistogram(be.eye, be.y/deltaY, be.x, be.timestamp);
                num = 0;

                maybeCallUpdateObservers(in, lastTimestamp);
            }
            num++;            
        }

        callUpdateObservers(in, lastTimestamp);

        out=getEnclosedFilterChain().filterPacket(in);

        return out;
    }

    @Override
    public void update (Observable o,Object arg){
/* // don't do this because it makes the stream consist of alternating eye buffers that can come out of temporal order for the events
 if ( o == getChip() && arg != null && arg instanceof HardwareInterface ){
            if ( chip.getHardwareInterface() instanceof StereoHardwareInterface ){
                ( (StereoHardwareInterface)chip.getHardwareInterface() ).setIgnoreTimestampNonmonotonicity(true);
                log.info("set ignoreTimestampOrdering on chip hardware interface change");
            } else{
                log.warning("can't set ignoreTimestampMonotonicity since this is not a StereoHardwareInterface");
            }
        }
*/

        if (o == this) {
            UpdateMessage msg = (UpdateMessage) arg;
            // estimates disparity
            updateDisparity(msg.timestamp);
        } else if (o instanceof AEChip) {
            initFilter();
        }
    }

    /**
     * Calculate disparities for all sections
     */
    synchronized private void updateDisparity(int timestamp){
        
        // finds disparities for every section
        findDisparity();

        // update disparities
        float updateInterval = (timestamp - prevTimestamp)/1000f;
        prevTimestamp = timestamp;

        float decayRatio = (float) Math.exp((double) -updateInterval/3.0);
        for(int i=0; i<numUpdate; i++){
            if(disparityValues.get(i).isValid()){
                if(prevDisparity.get(i).getDisparity() == 0)
                    prevDisparity.get(i).setDisparity(disparityValues.get(i).getDisparity());
                else{
                    int tmpDisparity = disparityValues.get(i).getDisparity();
                    // checks lowerDisparityLimit
                    if(enableDisparityLimit){
                        if(useLowLimit){
                            if(tmpDisparity < disparityLimit)
                                tmpDisparity = disparityLimit;
                        }else{
                            if(tmpDisparity > disparityLimit)
                                tmpDisparity = disparityLimit;
                        }
                    }
                    int newDisparity = (int) ((prevDisparity.get(i).getDisparity()*decayRatio + tmpDisparity)/(1+decayRatio));

                    prevDisparity.get(i).setDisparity(newDisparity);
                }
                prevDisparity.get(i).setValid(true);
            } else
                prevDisparity.get(i).setValid(false);
        }

        if(prevDisparity.get(numUpdate-1).isValid())
            str.setDx(-prevDisparity.get(numUpdate-1).getDisparity()/2);
    }


    /** Finds the disparities for each y-axis section based on the cross-correlation of histograms
     * It also finds the global disparity
     * @param startDelay
     * @param endDelay
     */
    private void findDisparity(){
        // for global disparity calculation
        ArrayList<Double> leftGlobal = new ArrayList<Double>(size);
        ArrayList<Double> rightGlobal = new ArrayList<Double>(size);
        boolean initializedGlobalLeft = false;
        boolean initializedGlobalRight = false;

        int startDelay = 0, endDelay = 0;
        int positiveLimit = (int) (size*maxDisparityFractionChipsizeX);
        int negativeLimit = 0;
        if(useBipolarDisparity)
            negativeLimit = -positiveLimit;

        for(int i=0; i<numSectionsY; i++){
            int maxPos = 0;
            int preDis = prevDisparity.get(i).getDisparity();

            ArrayList<Double> left = movingAverageFiltering(histogramLeft.get(i), 4);
            ArrayList<Double> right = movingAverageFiltering(histogramRight.get(i), 4);

            // if there's more than one section, add all histogram to get the global disparity
            if(numSectionsY != 1){
                if(left.get(size) != 0){
                    if(!initializedGlobalLeft){
                        for(int k=0; k<size; k++)
                            leftGlobal.add(left.get(k));
                        initializedGlobalLeft = true;
                    }else{
                        for(int k=0; k<size; k++)
                            leftGlobal.set(k, leftGlobal.get(k)+left.get(k));
                    }
                }

                if(right.get(size) != 0){
                    if(!initializedGlobalRight){
                        for(int k=0; k<size; k++)
                            rightGlobal.add(right.get(k));
                        initializedGlobalRight = true;
                    }else{
                        for(int k=0; k<size; k++)
                            rightGlobal.set(k, rightGlobal.get(k)+right.get(k));
                    }
                }
            }

            if(left.get(size) == 0 || right.get(size) == 0){
                disparityValues.get(i).setDisparity(preDis, false);
                continue;
            }

            left.remove(size);
            right.remove(size);


//            System.out.println("prevDisparity("+i+") = " + prevDisparity.get(i).getDisparity()+", "+prevDisparity.get(i).isValid());
            // sets the range of delays to check the cross-correlation
            startDelay = findStartDelay(i, negativeLimit);
            endDelay = findEndDelay(i, positiveLimit);
            maxPos = findMaxXcorrDelay(startDelay, endDelay, left, right, XC_Threshold);
            if(maxPos == -size){
//                maxPos = findMaxXcorrDelay(startDelay, endDelay, left, right, XC_Threshold - 0.3);
//                if(maxPos == -size){
                    startDelay = negativeLimit;
                    endDelay = positiveLimit;
                    maxPos = findMaxXcorrDelay(startDelay, endDelay, left, right, XC_Threshold);
//                }
            }
            if(maxPos == -size)
                disparityValues.get(i).setDisparity(preDis, false);
            else{
                float deltaDis = (float) Math.abs(maxPos - preDis);
//                System.out.println("delta disparity = "+deltaDis +" (curDis = "+ maxPos +", preDis = "+preDis+"), preDisValid = "+prevDisparity.get(i).isValid());
                if(deltaDis < maxDisparityChangePixels || preDis == 0)
                    disparityValues.get(i).setDisparity(maxPos, true);
                else
                    disparityValues.get(i).setDisparity(preDis, false);
            }

            preDis = maxPos;
        }

/*        if(numSectionsY != 1){
            int maxDsp = -size;
            for(int k=0; k<numSectionsY; k++){
                if(disparityValues.get(k).isValid() && disparityValues.get(k).getDisparity() > maxDsp)
                    maxDsp = disparityValues.get(k).getDisparity();
            }
            if(maxDsp == -size)
                disparityValues.get(numSectionsY).setDisparity(0, false);
            else
                disparityValues.get(numSectionsY).setDisparity(maxDsp, true);
        }
*/
        // estimation of global disparity
        if(numSectionsY != 1){
            int maxPos = 0;
            int preDis = prevDisparity.get(numSectionsY).getDisparity();

            if(!leftGlobal.isEmpty() && !rightGlobal.isEmpty()){
                startDelay = findStartDelay(numSectionsY, negativeLimit);
                endDelay = findEndDelay(numSectionsY, positiveLimit);
                maxPos = findMaxXcorrDelay(startDelay, endDelay, leftGlobal, rightGlobal, XC_Threshold);
//                if(maxPos == -size)
//                    maxPos = findMaxXcorrDelay(startDelay, endDelay, leftGlobal, rightGlobal, XC_Threshold - 0.3);
                    if(maxPos == -size){
                        startDelay = negativeLimit;
                        endDelay = positiveLimit;
                        maxPos = findMaxXcorrDelay(startDelay, endDelay, leftGlobal, rightGlobal, XC_Threshold);
                    }
            } else{
                maxPos = -size;
            }
 
            if(maxPos == -size)
                disparityValues.get(numSectionsY).setDisparity(preDis, false);
            else{
                float deltaDis = (float) Math.abs(maxPos - preDis);
                if(deltaDis < maxDisparityChangePixels || preDis == 0)
                    disparityValues.get(numSectionsY).setDisparity(maxPos, true);
                else
                    disparityValues.get(numSectionsY).setDisparity(preDis, false);
            }

            preDis = maxPos;

//            System.out.println("prevDisparity = " + prevDisparity.get(numSectionsY).getDisparity()+", "+prevDisparity.get(numSectionsY).isValid());            
        }
    }

    private int findStartDelay(int section, int negativeLimit){
        int startDelay = negativeLimit;
        int prevDp = prevDisparity.get(section).getDisparity();
        if(prevDisparity.get(section).isValid() && prevDp - size/6 > negativeLimit)
            startDelay = prevDp - size/6;

//        System.out.println(">>>>>Deciding start delay : prevDp = " + prevDp + ", prevDisValid = "+prevDisparity.get(section).isValid()+", sDelay = "+startDelay);
        return startDelay;
    }

    private int findEndDelay(int section, int positiveLimit){
        int endDelay = positiveLimit;
        int prevDp = prevDisparity.get(section).getDisparity();

        if(prevDisparity.get(section).isValid() && prevDp + size/6 < positiveLimit)
            endDelay = prevDp + size/6;

//        System.out.println(">>>>>Deciding end delay : prevDp = " + prevDp + ", prevDisValid = "+prevDisparity.get(section).isValid()+", sDelay = "+endDelay);
        return endDelay;
    }

    private int findMaxXcorrDelay(int startDelay, int endDelay, ArrayList<Double> left, ArrayList<Double> right, double threshold){
        int maxPos = startDelay;
        int delta = 3;
        double prevXc = threshold;
        boolean valid = false;

        for(int j = startDelay; j<endDelay; j+=delta){
            double xc = calXCorrelation(left, right, j);
            if(xc > threshold){
                if(xc > prevXc){
                    prevXc = xc;
                    maxPos = j;
                    valid = true;
                    delta = 3;
                } else{
                    if(delta < 3)
                        delta++;
                    else
                        delta = 1;
                }
            } else{
                delta = 3;
            }
        }

        if(valid)
            return maxPos;
        else
            return -size;
    }


    /** calculates the cross-correlation between histograms of left and right eyes
     *
     * @param histoLeft
     * @param histoRight
     * @param delay
     * @return
     */
     private double calXCorrelation(ArrayList<Double> histoLeft, ArrayList<Double> histoRight, int delay){
       double ret = 0, sumX = 0, sumY = 0, sumXY = 0, sumXS = 0, sumYS = 0;
       int num;
       
       if(delay >= 0)
           num = size - delay;
       else
           num = size + delay;

       double x, y;
       for(int i=0; i<num; i++){
           if(delay >= 0){
               x = histoLeft.get(i+delay);
               y = histoRight.get(i);
           } else {
               x = histoLeft.get(i);
               y = histoRight.get(i-delay);
           }

           if(x != 0){
               sumX += x;
               sumXS += x*x;

               if(y != 0)
                   sumXY += x*y;
           }
           if(y != 0){
               sumY += y;
               sumYS += y*y;
           }
           
       }

       double aveX = sumX/num;
       double aveY = sumY/num;
       double sigmaX = Math.sqrt(sumXS/num - Math.pow(aveX, 2.0));
       double sigmaY = Math.sqrt(sumYS/num - Math.pow(aveY, 2.0));


       ret = (sumXY/num - aveX*aveY)/sigmaX/sigmaY;

       return ret;
   }


     /**
      * do low-pass filtering on the histogram using moving window average filter
      * @param histogram
      * @param winSize
      * @return
      */
    private ArrayList<Double> movingAverageFiltering(ArrayList<Bins> histogram, int winSize){
       ArrayList<Double> ret = new ArrayList<Double>(size+1);

       double massSum = 0, av = 0, avSum = 0;
       int halfWinSize = winSize/2;
       for(int k=0; k<size; k++){
           if(k==0){
               for(int j=0; j<halfWinSize; j++)
                   massSum += histogram.get(j).getMassNow(lastTimestamp);
               av = massSum/halfWinSize;
           } else if (k<=halfWinSize){
               massSum += histogram.get(k+halfWinSize-1).getMassNow(lastTimestamp);
               av = massSum/(k+halfWinSize);
           } else if(k <= size - halfWinSize) {
               massSum += histogram.get(k+halfWinSize-1).getMassNow(lastTimestamp);
               massSum -= histogram.get(k-halfWinSize).getMassNow(lastTimestamp);
               av = massSum/winSize;
           } else {
               massSum -= histogram.get(k-halfWinSize).getMassNow(lastTimestamp);
               av = massSum/(halfWinSize+size-k);
           }

           if(av < massThreshold/numSectionsY)
               av = 0.0;

           ret.add(av);
           avSum += av;
       }

       ret.add(avSum);
       return ret;

   }


   /** reset histogram
    *
    */
    private void resetHistogram(){
        for(int j=0; j< histogramLeft.size(); j++)
            for(int i=0; i<size; i++){
                ((ArrayList<Bins>) histogramLeft.get(j)).set(i, new Bins());
                ((ArrayList<Bins>)histogramRight.get(j)).set(i, new Bins());
            }
    }

    /** increases the mass of points based of the x-axis position of an incoming event
     *
     * @param eye
     * @param yIndex
     * @param xPos
     * @param timestamp
     */
    synchronized private void updateHistogram(BinocularEvent.Eye eye, int yIndex, int xPos, int timestamp){
        Bins tmp;
        switch(eye){
            case LEFT:
                tmp = ((ArrayList<Bins>) histogramLeft.get(yIndex)).get(xPos); // TODO speed up by using arrays rather than arraylist
                tmp.setMass(tmp.getMassNow(timestamp)+1.0);
                tmp.setLastUpdateTime(timestamp);
                break;
            case RIGHT:
                tmp = ((ArrayList<Bins>) histogramRight.get(yIndex)).get(xPos);
                tmp.setMass(tmp.getMassNow(timestamp)+1.0);
                tmp.setLastUpdateTime(timestamp);
                break;
            default:
                log.warning ("BinocularEvent doesn't have Eye type");
        }
    }

    /** returns the bins of the specified x-axis point
     * 
     * @param eye
     * @param yIndex
     * @param xPos
     * @return
     */
    private double getBins(BinocularEvent.Eye eye, int yIndex, int xPos){
        double outBins = 0.0;

        switch(eye){
            case LEFT:
                outBins = ((ArrayList<Bins>) histogramLeft.get(yIndex)).get(xPos).getMassNow(lastTimestamp);
                break;
            case RIGHT:
                outBins = ((ArrayList<Bins>) histogramRight.get(yIndex)).get(xPos).getMassNow(lastTimestamp);
                break;
            default:
                log.warning ("BinocularEvent doesn't have Eye type");
        }

        return outBins;
    }


    @Override
    public void resetFilter() {
        str.resetFilter();
        resetHistogram();

        if(numSectionsY == 1){
            disparityValues.put(0, new Disparity());
            prevDisparity.put(0, new Disparity());
        } else {
            for(int i=0; i<numSectionsY+1; i++){
                disparityValues.put(i, new Disparity());
                prevDisparity.put(i, new Disparity());
            }
        }

        enableDisparityLimit = false;
        disparityLimit = - (int) (size*maxDisparityFractionChipsizeX);
        prevTimestamp = -1;
        lastTimestamp = -1;
    }

    @Override
    synchronized public void initFilter() {
        if(stereoChip == null)
            return;
        
        if(stereoChip.getLeft() == null)
            return;

        if(numSectionsY == 1)
            numUpdate = 1;
        else
            numUpdate = numSectionsY + 1;
        
        size = stereoChip.getLeft().getSizeX();

        histogramLeft.clear();
        histogramRight.clear();
        disparityValues.clear();
        
        for(int i=0; i<numSectionsY; i++){
            ArrayList<Bins> leftElement = new ArrayList<Bins>(size);
            ArrayList<Bins> rightElement = new ArrayList<Bins>(size);

            for(int j=0; j<size; j++){
                leftElement.add(new Bins());
                rightElement.add(new Bins());
            }
            
            histogramLeft.put(i, leftElement);
            histogramRight.put(i, rightElement);
        }
        // last one is for global disparity
        if(numSectionsY == 1){
            prevDisparity.put(0, new Disparity());
            disparityValues.put(0, new Disparity());
        }else{
            for(int i=0; i<numSectionsY+1; i++){
                prevDisparity.put(i, new Disparity());
                disparityValues.put(i, new Disparity());
            }
        }

        deltaY = size/numSectionsY;
        resetHistogram();
        initialized = true;
        prevTimestamp = -1;
        lastTimestamp = -1;
    }


    /**
     * Renders the histograms and disparity values
     * @param drawable
     */
    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (  ! isFilterEnabled () || !showHistogram){
            return;
        }
        GL gl = drawable.getGL (); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if ( gl == null ){
            log.warning ("null GL in StereoVergenceFilter.annotate");
            return;
        }
        
        
        float[] rgb = new float[ 4 ];
        gl.glPushMatrix ();
        try{
            colorLeft.getRGBComponents (rgb);
            gl.glColor3fv (rgb,0);
            gl.glLineWidth (2f);
            for(int j=0; j<numSectionsY; j++){
                for (int i=1; i<size; i++){
                    gl.glBegin(GL.GL_LINES);
                    {
                        gl.glVertex2i(i-1, (int) (massScale*getBins(BinocularEvent.Eye.LEFT, j, i-1)) + j*deltaY);
                        gl.glVertex2i(i, (int) (massScale*getBins(BinocularEvent.Eye.LEFT, j, i)) + j*deltaY);
                    }
                    gl.glEnd();
                }
            }

            for(int j=0; j<numSectionsY; j++){
                colorRight.getRGBComponents (rgb);
                gl.glColor3fv (rgb,0);
                gl.glLineWidth (2f);
                for (int i=1; i<size; i++){
                    gl.glBegin(GL.GL_LINES);
                    {
                        gl.glVertex2i(i-1, (int) (massScale*getBins(BinocularEvent.Eye.RIGHT, j, i-1)) + j*deltaY);
                        gl.glVertex2i(i, (int) (massScale*getBins(BinocularEvent.Eye.RIGHT, j, i)) + j*deltaY);
                    }
                    gl.glEnd();
                }

                int font = GLUT.BITMAP_HELVETICA_18;
                GLUT glut = chip.getCanvas ().getGlut ();
                gl.glColor3f (1,1,1);

                if(prevDisparity==null || prevDisparity.get(j)==null) break; // check for existance

                gl.glRasterPos3f (1,j*deltaY + 5,0);
                glut.glutBitmapString (font,String.format ("Disparity = %d", prevDisparity.get(j).getDisparity()));

                if(j == 0){
                    gl.glRasterPos3f (100,5,0);
                    if(numSectionsY == 1)
                        glut.glutBitmapString (font,String.format ("GD = %d", prevDisparity.get(0).getDisparity()));
                    else
                        glut.glutBitmapString (font,String.format ("GD = %d", prevDisparity.get(numSectionsY).getDisparity()));
                }
            }

        } catch ( java.util.ConcurrentModificationException e ){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning (e.getMessage ());
        }
        gl.glPopMatrix ();
    }

    /**
     * returns the number of sections in y-axis.
     * @return
     */
    public int getNumSectionsY() {
        return numSectionsY;
    }

    /** sets the number of sections in y-axis. Only 1, 4, and 8 are supproted due to technical issues like computational expenses
     *
     * @param numSectionsY
     */
    synchronized public void setNumSectionsY(int numSectionsY) {
        if(numSectionsY >= 8)
            numSectionsY = 8;
        else if(numSectionsY <= 2)
            numSectionsY = 1;
        else
            numSectionsY = 4;

        if(this.numSectionsY != numSectionsY){
            this.numSectionsY = numSectionsY;
            initFilter();
        }
        getPrefs ().putInt ("StereoVergenceFilter.numSectionsY", numSectionsY);
    }

    /** returns the threshold of cross-correlation.
     *Cross-correlation below this value will be not considered in the disparity estimation.
     *
     * @return XC_Threshold
     */
    public float getXC_Threshold() {
        return XC_Threshold;
    }

    /** sets the threshold of cross-correlation.
     *
     * @param XC_Threshold
     */
    synchronized public void setXC_Threshold(float XC_Threshold) {
        if(XC_Threshold < -1.0f)
            this.XC_Threshold = -1.0f;
        else if(XC_Threshold > 1.0f)
            this.XC_Threshold = 1.0f;
        else
            this.XC_Threshold = XC_Threshold;

        getPrefs ().putFloat ("StereoVergenceFilter.XC_Threshold", XC_Threshold);
    }


    /** returns the timeconstant of leaky mass.
     * It increases by 1 if there's an additional event, but decays exponentially without additional events.
     * Cross-correlation between mass histograms of left and right eyes are used to estimate disparity.
     *
     * @return
     */
    public int getMassTimeConstantUs() {
        return massTimeConstantUs;
    }

    /** sets the timeconstant of leaky mass.
     * It increases by 1 if there's an additional event, but decays exponentially without additional events.
     * Cross-correlation between mass histograms of left and right eyes are used to estimate disparity.
     *
     * @param massTimeConstantUs
     */
    synchronized public void setMassTimeConstantUs(int massTimeConstantUs) {
        this.massTimeConstantUs = massTimeConstantUs;
        getPrefs ().putInt ("StereoVergenceFilter.massTimeConstantUs", massTimeConstantUs);
    }

    /** returns the mass scale factor.
     * this parameter is used to control the amplitude of histograms when they are rendered on the screen.
     *
     * @return massScale
     */
    public float getMassScale() {
        return massScale;
    }

    /** sets the mass scale factor.
     * this parameter is used to control the amplitude of histograms when they are rendered on the screen.
     *
     * @param massScale
     */
    synchronized public void setMassScale(float massScale) {
        this.massScale = massScale;
        getPrefs ().putFloat ("StereoVergenceFilter.massScale", massScale);
    }

    /** returns true if histograms are rendered.
     *
     * @return showHistogram
     */
    public boolean isShowHistogram() {
        return showHistogram;
    }

    /** sets true if histograms are rendered.
     *
     * @param showHistogram
     */
    synchronized public void setShowHistogram(boolean showHistogram) {
        this.showHistogram = showHistogram;
        getPrefs ().putBoolean ("StereoVergenceFilter.showHistogram", showHistogram);
    }

    /** returns the ratio of sub-sampling.
     * sub-sampling is introduced to release the computational expenses in histogram construction
     *
     * @return
     */
    public int getSubSampleingRatio() {
        return subSampleingRatio;
    }

    /** sets the ratio of sub-sampling.
     * sub-sampling is introduced to release the computational expenses in histogram construction
     *
     * @param subSampleingRatio
     */
    synchronized public void setSubSampleingRatio(int subSampleingRatio) {
        this.subSampleingRatio = subSampleingRatio;
        getPrefs ().putInt ("StereoVergenceFilter.subSampleingRatio", subSampleingRatio);
    }

    /** returns the threshold of mass.
     * mass below this value in the historgram is set to zero in cross-correlation calculation.
     * This is introduced not to estimate the disparity of sparse random noise events.
     *
     * @return massThreshold
     */
    public float getMassThreshold() {
        return massThreshold;
    }

    /** sets the threshold of mass.
     * mass below this value in the historgram is set to zero in cross-correlation calculation.
     * This is introduced not to estimate the disparity of sparse random noise events.
     *
     * @param massThreshold
     */
    synchronized public void setMassThreshold(float massThreshold) {
        this.massThreshold = massThreshold;
        getPrefs ().putFloat ("StereoVergenceFilter.massThreshold", massThreshold);
    }

    /** returns the maximum allowed value of the disparity in the fraction of chip size in x-axis.
     * disparity outside of this value is not estimated.
     *
     * @return maxDisparityFractionChipsizeX
     */
    public float getMaxDisparityFractionChipsizeX() {
        return maxDisparityFractionChipsizeX;
    }

    /** sets the maximum allowed value of the disparity in the fraction of chip size in x-axis.
     * disparity outside of this value is not estimated.
     *
     * @param maxDisparityFractionChipsizeX
     */
    synchronized public void setMaxDisparityFractionChipsizeX(float maxDisparityFractionChipsizeX) {
        this.maxDisparityFractionChipsizeX = maxDisparityFractionChipsizeX;
        getPrefs ().putFloat ("StereoVergenceFilter.maxDisparityFractionChipsizeX", maxDisparityFractionChipsizeX);
    }

    /**
     * returns maxDisparityChangePixelsPerMs
     * @return
     */
    public int getMaxDisparityChangePixels() {
        return maxDisparityChangePixels;
    }

    /**
     * sets maxDisparityChangePixelsPerMs
     * 
     * @param maxDisparityChangePixels
     */
    public void setMaxDisparityChangePixels(int maxDisparityChangePixels) {
        this.maxDisparityChangePixels = maxDisparityChangePixels;
        getPrefs ().putInt ("StereoVergenceFilter.maxDisparityChangePixels", maxDisparityChangePixels);
    }


    /** returns true if both positive and negative disparities are supported.
     * if it's false, only positive disparity is supported.
     *
     * @return useBipolarDisparity
     */
    public boolean isUseBipolarDisparity() {
        return useBipolarDisparity;
    }

    /** sets true if both positive and negative disparities are supported.
     * if it's false, only positive disparity is supported.
     *
     * @param useBipolarDisparity
     */
    synchronized public void setUseBipolarDisparity(boolean useBipolarDisparity) {
        this.useBipolarDisparity = useBipolarDisparity;
        getPrefs ().putBoolean ("StereoVergenceFilter.useBipolarDisparity", useBipolarDisparity);
    }

    /** returns the global disparity between left and right eyes.
     * it returns prevDisparity rather than currentGlobalDisparity to cover the failure of disparity estimation.
     *
     * @return prevDisparity
     */
    synchronized public int getGlobalDisparity(){
        if(prevDisparity==null) return 0;
        if(prevDisparity.get(0)==null) return 0;

        if(numSectionsY == 1)
            return prevDisparity.get(0).getDisparity();
        else
            return prevDisparity.get(numSectionsY).getDisparity();
    }

    /** returns the disparity at the specified position.
     * Disparity values obtained from mutiples sections are used to get it.
     *
     * @param yPos
     * @return disparity
     */
    synchronized public int getDisparity(int yPos){
        return disparityValues.get(yPos/deltaY).getDisparity();
    }

    /** returns true if the disparity of the specified position is properly updated.
     *
     * @param yPos
     * @return valid
     */
    synchronized public boolean isDisparityValid(int yPos){
        return disparityValues.get(yPos/deltaY).isValid();
    }

    /**
     * returns true if enableLowerDisparityLimit is true
     * @return
     */
    public boolean isEnableLowerDisparityLimit() {
        return enableDisparityLimit;
    }

    /**
     * sets enableDisparityLimit
     * @param enableDisparityLimit
     */
    public void setEnableDisparityLimit(boolean enableDisparityLimit) {
        this.enableDisparityLimit = enableDisparityLimit;
    }

    /**
     * returns disparityLimit
     * @return
     */
    public int getDisparityLimit() {
        return disparityLimit;
    }

    /**
     * returns lowLimit
     * @return
     */
    public boolean isLowLimit(){
        return useLowLimit;
    }

    /**
     * sets lowerDisparityLimit
     * 
     * @param disparityLimit
     * @param useLowLimit
     */
    public void setDisparityLimit(int disparityLimit, boolean useLowLimit) {
        this.disparityLimit = disparityLimit;
        this.useLowLimit = useLowLimit;
    }

}
