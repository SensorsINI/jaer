/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedList;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 *
 * @author Jun Haeng Lee
 */
public class StereoDisparity {
    /**
     * id
     */
    int id;

    /**
     * mass histogram of the left eye
     */
    protected ArrayList<Bins> histogramLeft;
    /**
     * mass histogram of the right eye
     */
    protected ArrayList<Bins> histogramRight;
    /**
     * estimated disparity values
     */
    protected Disparity currentDisparity = new Disparity();
    /**
     * previous value of the global disparity. this value is used again if the disparity calculation is failed
     */
    protected LinkedList<Disparity> prevDisparities = new LinkedList<Disparity>();
    /**
     * maximum number of previous disparity
     */
    protected int maxNumPrevDisparity = 10;

    /**
     * Timestamp of the last event ever processed
     */
    protected int lastTimestamp = -1;

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
    protected boolean DisparityLimitEnabled = false;

    /**
     * if true, enables maximum allowed disparity change
     */
    protected boolean enableMaxDisparityChange = false;

    /**
     * maximum allowed disparity change in pixels per step
     */
    protected int maxDisparityChangePixels = 100;

    /**
     * if true, filters the events out of this area out
     * this is used for cluster SV
     */
    protected boolean usingCheckAreaRectangle = false;

    /**
     * event within this area will be used for histogram
     * this is used for cluster SV
     */
    protected Rectangle checkAreaRectangle = null;

    /**
     * low pass filter
     */
    LowpassFilter lpf = new LowpassFilter();


    /**
     * chip size in x-axis
     */
    protected int size;

    /**
     * time constant of leaky mass in the mass histogram
     */
    protected int massTimeConstantUs;

    /**
     * mass below this value will be set to zero
     */
    protected float massThreshold;

    /**
     * maximum allowed disparity in fraction of Chipsize
     */
    protected float maxDisparityFractionChipsizeX;

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
        public float disparity;
        /**
         * timestamp
         */
        public int timestamp;

        /**
         * true if the disparity estimation was a success
         */
        public boolean valid;

        Disparity(){
            disparity = -size;
            timestamp = 0;
            valid = false;
        }

        Disparity(float disparity, int timestamp, boolean validity){
            super();
            setDisparity(disparity, timestamp, validity);
        }

        Disparity(Disparity disp){
            super();
            setDisparity(disp.disparity, disp.timestamp, disp.valid);
        }

        /**
         * sets the disparity value and its validity
         * @param disparity
         * @param timestamp
         * @param validity
         */
        final void setDisparity(float disparity, int timestamp, boolean validity){
            this.disparity = disparity;
            this.timestamp = timestamp;
            valid = validity;
        }

        /**
         * returns disparity
         * @return disparity
         */
        final public float getDisparity() {
            return disparity;
        }

        /**
         * returns timestamp
         * @return
         */
        final public int getTimestamp(){
            return timestamp;
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

    StereoDisparity(int id, int chipSize, int massTimeConstantUs, float massThreshold, float maxDisparityFractionChipsizeX, float initialDisparity, int initialTimestamp){
        this.id = id;
        size = chipSize;

        histogramLeft = new ArrayList<Bins>(size);
        histogramRight = new ArrayList<Bins>(size);

        this.massTimeConstantUs = massTimeConstantUs;
        this.massThreshold = massThreshold;
        this.maxDisparityFractionChipsizeX = maxDisparityFractionChipsizeX;

        checkAreaRectangle = new Rectangle(0, 0, size, size);
        lpf.setTauMs(50);

        reset(initialDisparity, initialTimestamp);
    }

    /**
     * resets the instance
     *
     * @param initialDisparity
     * @param initialTimestamp
     */
    public void reset(float initialDisparity, int initialTimestamp) {
        resetHistogram();

        checkAreaRectangle = new Rectangle(0, 0, size, size);
        prevDisparities.clear();
        setDisparity(initialDisparity, initialTimestamp);

        DisparityLimitEnabled = false;
        disparityLimit = - (int) (size*maxDisparityFractionChipsizeX);
        lastTimestamp = -1;
    }

    /**
     * calculates disparity
     *
     * @param timestamp
     */
    public void updateDisparity(int timestamp){
        // calculates disparity
        findDisparity(timestamp);

        if(currentDisparity.disparity != -size && !prevDisparities.isEmpty()){
            float tmpDisparity = currentDisparity.disparity;

            // checks DisparityLimit
            if(DisparityLimitEnabled){
                if(useLowLimit){ // low disparity limit
                    if(tmpDisparity < disparityLimit)
                        tmpDisparity = disparityLimit;
                }else{ // high disparity limit
                    if(tmpDisparity > disparityLimit)
                        tmpDisparity = disparityLimit;
                }
            }
            
            // updates current disparity with low-pass filter one
            currentDisparity.disparity = lpf.filter(tmpDisparity, timestamp);

        }

        prevDisparities.add(new Disparity(currentDisparity));
        if(prevDisparities.size() > maxNumPrevDisparity)
            prevDisparities.removeFirst();
    }


    /** Finds the disparities for each y-axis section based on the cross-correlation of histograms
     * It also finds the global disparity
     * @param startDelay
     * @param endDelay
     */
    private void findDisparity(int timestamp){
        Disparity preDis = null;
        if(prevDisparities.isEmpty())
            preDis = new Disparity();
        else
            preDis = prevDisparities.getLast();

        ArrayList<Double> left = null;
        ArrayList<Double> right = null;

        // smoothes histograms using moving average filter
        left = movingAverageFiltering(histogramLeft, 1);
        right = movingAverageFiltering(histogramRight, 1);

        // if there's too small number of valid data, do not update the disparity
        // movingAvergaeFiltering method adds information of valid number of data at the end of the output arraylist
        if(left.get(size) < checkAreaRectangle.width/10 || right.get(size) < checkAreaRectangle.width/10){
            currentDisparity.setDisparity(preDis.disparity, timestamp, false);
        } else {
            // removes the last data which represents the number of valid data
            left.remove(size);
            right.remove(size);

            // gets the median distance bewteen histograms
            float md = (float)findMedianDistance();

            //if there is no previous data, sets the current disparity with maxXC.x
            if(preDis.getDisparity() == -size){
                currentDisparity.setDisparity(md, timestamp, true);
            } else { // if there's previous data
                if(md == size) // not valid because the cluster is  hitting edge
                    md = preDis.disparity;
                else if(md == -size){ // not valid because large mass difference between left and right
                    currentDisparity.setDisparity(md, timestamp, true);
                    return;
                } else {

                }

                float deltaDisMD = (float) Math.abs(md - preDis.disparity);
                if(deltaDisMD > maxDisparityChangePixels && enableMaxDisparityChange)
                    currentDisparity.setDisparity(preDis.disparity, timestamp, false);
                else
                    currentDisparity.setDisparity(md, timestamp, true);
            }
        }
    }

    /**
     * finds the start delay for xcorrelation calculation
     * @param negativeLimit
     * @return
     */
    private int findStartDelay(int negativeLimit){
        int startDelay = negativeLimit;
        int wnd = Math.min(Math.min(size/6, checkAreaRectangle.width/2), maxDisparityChangePixels);

        float refDisp = 0;
        boolean valid = false;
        if(!prevDisparities.isEmpty()){
            Disparity prevDp = prevDisparities.getLast();
            refDisp = prevDp.getDisparity();
            valid = prevDp.isValid();
        }

        if(valid && refDisp - wnd > negativeLimit)
            startDelay = (int) refDisp - wnd;

//        System.out.println(">>>>>Deciding start delay : prevDp = " + prevDp + ", prevDisValid = "+prevDisparity.get(section).isValid()+", sDelay = "+startDelay);
        return startDelay;
    }

    /**
     * finds the end delay for xcorrelation calculation
     * @param positiveLimit
     * @return
     */
    private int findEndDelay(int positiveLimit){
        int endDelay = positiveLimit;
        int wnd = Math.min(Math.min(size/6, checkAreaRectangle.width/2), maxDisparityChangePixels);

        float refDisp = 0;
        boolean valid = false;
        if(!prevDisparities.isEmpty()){
            Disparity prevDp = prevDisparities.getLast();
            refDisp = prevDp.getDisparity();
            valid = prevDp.isValid();
        }

        if(valid && refDisp + wnd < positiveLimit)
            endDelay = (int) refDisp + wnd;

//        System.out.println(">>>>>Deciding end delay : prevDp = " + prevDp + ", prevDisValid = "+prevDisparity.get(section).isValid()+", sDelay = "+endDelay);
        return endDelay;
    }

    /**
     * returns the median distance bewteen histograms
     * @return
     *   size : not valid because the cluster is  hitting edge
     *  -size : not valid because large mass difference between left and right
     *  otherwise : disparity between left and right
     */
    private double findMedianDistance(){
        double lsum = 0;
        double rsum = 0;
        double lpsum = 0;
        double rpsum = 0;

        int leftStartPos = Math.max(0, checkAreaRectangle.x + (int) (currentDisparity.disparity/2));
        int leftStartTrimedSize = 0;
        if(leftStartPos == 0)
            leftStartTrimedSize = Math.abs(checkAreaRectangle.x + (int) (currentDisparity.disparity/2));

        int leftEndPos = Math.min(size, (int) checkAreaRectangle.getMaxX() + (int) (currentDisparity.disparity/2));
        int leftEndTrimedSize = 0;
        if(leftEndPos == size)
            leftEndTrimedSize = Math.abs((int) checkAreaRectangle.getMaxX() + (int) (currentDisparity.disparity/2) - size);
        
        int rightStartPos = Math.max(0, checkAreaRectangle.x - (int) (currentDisparity.disparity/2));
        int rightStartTrimedSize = 0;
        if(rightStartPos == 0)
            rightStartTrimedSize = Math.abs(checkAreaRectangle.x - (int) (currentDisparity.disparity/2));

        int rightEndPos = Math.min(size, (int) checkAreaRectangle.getMaxX() - (int) (currentDisparity.disparity/2));
        int rightEndTrimedSize = 0;
        if(rightEndPos == size)
            rightEndTrimedSize = Math.abs((int) checkAreaRectangle.getMaxX() - (int) (currentDisparity.disparity/2) - size);

        // returns size if the cluster is hitting edge
        int maxTrimmedSize =Math.max( Math.max(leftStartTrimedSize, leftEndTrimedSize), Math.max(rightStartTrimedSize, rightEndTrimedSize));
        if(id != -1 && maxTrimmedSize > checkAreaRectangle.width/3)
            return size;

        // calibrate the start position for scanning
        if(leftStartTrimedSize > rightStartTrimedSize)
            rightStartPos += leftStartTrimedSize - rightStartTrimedSize;
        else
            leftStartPos += rightStartTrimedSize - leftStartTrimedSize;

        // calibrate the end position for scanning
        if(leftEndTrimedSize > rightEndTrimedSize)
            rightEndPos -= (leftEndTrimedSize - rightEndTrimedSize);
        else
            leftEndPos -= (rightEndTrimedSize - leftEndTrimedSize);

        // calculates the median value of the histogram of left eye
        for(int i=leftStartPos; i<leftEndPos; i++){
            double lmass = histogramLeft.get(i).getMassNow(lastTimestamp);
            lsum += lmass;
            lpsum += lmass*i;
        }

        // calculates the median value of the histogram of right eye
        for(int i=rightStartPos; i<rightEndPos; i++){
            double rmass = histogramRight.get(i).getMassNow(lastTimestamp);
            rsum += rmass;
            rpsum += rmass*i;
        }

        // returns -size if there is big mismatch between left and right
        // don't have to do this for global disparity updater
        if(id != -1){
            double massRatio = rsum/lsum;
            massRatio = massRatio>1?massRatio:1/massRatio;
            if(massRatio > 5)
                return -size;
        }

        // gets median distance
        double distance = lpsum/lsum - rpsum/rsum;

        return distance;
    }

    /**
     * finds the delay that makes maximum xcorrelation
     *
     * @param startDelay
     * @param endDelay
     * @param left
     * @param right
     * @param threshold
     * @return
     */
    private Point2D.Float findMaxXcorrDelay(int startDelay, int endDelay, ArrayList<Double> left, ArrayList<Double> right){
        Point2D.Float ret = new Point2D.Float();

        double[] xc = new double[endDelay - startDelay];
        for(int j = startDelay; j<endDelay; j++)
            xc[j-startDelay] = calXCorrelation(left, right, j)/(1+Math.abs(getDisparity() - j)/checkAreaRectangle.width);

        int maxPos = startDelay;
        double prevXc = 0;
        for(int i=0; i<endDelay - startDelay; i++){
            double sum = 0;
            double num = 3;
            
            if(i==0 || i==endDelay - startDelay -1)
                num = 2;
            
            if(i != 0)
                sum += xc[i-1];
            
            sum += xc[i];
            
            if(i != endDelay - startDelay -1)
                sum += xc[i+1];
            
            sum /= num;
            if(sum > prevXc){
                prevXc = sum;
                maxPos = i+startDelay;
                ret.setLocation(maxPos, (float) sum);
            }
        }

        return ret;
    }


    /**
     * calculates the cross-correlation between histograms of left and right eyes
     *
     * @param histoLeft
     * @param histoRight
     * @param delay
     * @return
     */
    private double calXCorrelation(ArrayList<Double> histoLeft, ArrayList<Double> histoRight, int delay){
       double ret = 0, sumX = 0, sumY = 0, sumXY = 0, sumXS = 0, sumYS = 0;
       int num = 0;
       int upperBound = 0;
       int lowerBound = 0;

       if(currentDisparity.disparity*delay < 0){
           upperBound = Math.min((int) checkAreaRectangle.getMaxX() + (int) Math.abs(currentDisparity.disparity/2) - Math.abs(delay), (int) checkAreaRectangle.getMaxX() - (int) Math.abs(currentDisparity.disparity/2));
           lowerBound = Math.max((int) checkAreaRectangle.x + (int) Math.abs(currentDisparity.disparity/2) - Math.abs(delay), (int) checkAreaRectangle.x - (int) Math.abs(currentDisparity.disparity/2));
       }else{
           upperBound = (int) checkAreaRectangle.getMaxX() - (int) Math.abs(currentDisparity.disparity/2) - Math.abs(delay) - 1;
           lowerBound = (int) checkAreaRectangle.x + (int) Math.abs(currentDisparity.disparity/2);
       }
       
       if(lowerBound < 0 || upperBound + Math.abs(delay) >= size)
           return 0;

       num = upperBound - lowerBound;
       if(num <= 0)
           return 0;

       double x, y;
       for(int i=lowerBound; i<upperBound; i++){
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

   private ArrayList<Double> getBinValues(ArrayList<Bins> histogram){
       ArrayList<Double> ret = new ArrayList<Double>(size+1);

       int numValid = 0;
       for(int k=0; k<size; k++){
           double val = histogram.get(k).getMassNow(lastTimestamp);
           if(val < massThreshold)
               val = 0.0;
           else
               numValid++;

           ret.add(val);
       }

       ret.add((double) numValid);
       return ret;

   }


     /**
      * do low-pass filtering on the histogram using moving window average filter
      *
      * @param histogram
      * @param winSize
      * @return
      */
    private ArrayList<Double> movingAverageFiltering(ArrayList<Bins> histogram, int winSize){
        ArrayList<Double> ret = new ArrayList<Double>(size+1);

        int numValid = 0;
        double massSum = 0, av = 0;
        int halfWinSize = winSize/2 + 1;

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

           if(av > massThreshold)
               numValid ++;

           ret.add(av);
       }

       ret.add((double) numValid);
       return ret;

   }


   /**
    * resets histogram
    */
    private void resetHistogram(){
        for(int i=0; i<size; i++){
            histogramLeft.add(new Bins());
            histogramRight.add(new Bins());
        }
    }

    /**
     * increases the mass of points based of the x-axis position of an incoming event
     *
     * @param ev
     */
    public void updateHistogram(BinocularEvent ev){
        Bins bins;

        lastTimestamp = ev.timestamp;

        switch(ev.eye){
            case LEFT:
                if(!usingCheckAreaRectangle || checkAreaRectangle.contains(ev.x - currentDisparity.disparity/2, ev.y)){
                    bins = histogramLeft.get(ev.x);
                    bins.setMass(bins.getMassNow(lastTimestamp)+1);
                    bins.setLastUpdateTime(lastTimestamp);
                }
                break;
            case RIGHT:
                if(!usingCheckAreaRectangle || checkAreaRectangle.contains(ev.x + currentDisparity.disparity/2, ev.y)){
                    bins = histogramRight.get(ev.x);
                    bins.setMass(bins.getMassNow(lastTimestamp)+1);
                    bins.setLastUpdateTime(lastTimestamp);
                }
                break;
            default:
                break;
        }
    }

    /**
     * returns the bins of the specified x-axis point
     * 
     * @param eye
     * @param xPos
     * @return
     */
    public double getBins(BinocularEvent.Eye eye, int xPos){
        double outBins = 0.0;

        switch(eye){
            case LEFT:
                outBins = histogramLeft.get(xPos).getMassNow(lastTimestamp);
                break;
            case RIGHT:
                outBins = histogramRight.get(xPos).getMassNow(lastTimestamp);
                break;
            default:
                break;
        }
        
        return outBins;
    }

    /**
     * returns id
     * @return
     */
    public int getId(){
        return id;
    }

    /**
     * returns size
     *
     * @return
     */
    public int getSize() {
        return size;
    }

    /**
     * sets size
     * @param size
     */
    public void setSize(int size) {
        this.size = size;

        reset(currentDisparity.disparity, currentDisparity.timestamp);
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
    public void setMassTimeConstantUs(int massTimeConstantUs) {
        this.massTimeConstantUs = massTimeConstantUs;
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
    public void setMassThreshold(float massThreshold) {
        this.massThreshold = massThreshold;
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
    public void setMaxDisparityFractionChipsizeX(float maxDisparityFractionChipsizeX) {
        this.maxDisparityFractionChipsizeX = maxDisparityFractionChipsizeX;
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
    }

    /**
     * returns enableMaxDisparityChange
     *
     * @return
     */
    public boolean isMaxDisparityChangeEnabled() {
        return enableMaxDisparityChange;
    }

    /**
     * sets enableMaxDisparityChange
     *
     * @param enableMaxDisparityChange
     */
    public void setMaxDisparityChangeEnabled(boolean enableMaxDisparityChange) {
        this.enableMaxDisparityChange = enableMaxDisparityChange;
    }

    /** returns the latest disparity between left and right eyes.
     *
     * @return prevDisparity
     */
    public float getDisparity(){
        if(prevDisparities == null) return 0;
        if(prevDisparities.isEmpty()) return 0;
        if(prevDisparities.getLast() == null) return 0;

        return prevDisparities.getLast().getDisparity();
    }

    /**
     * sets current disparity manually
     * 
     * @param disparity
     * @param timestamp
     */
    public void setDisparity(float disparity, int timestamp){
        if(timestamp == -1)
            return;

        currentDisparity.disparity = disparity;
        currentDisparity.timestamp = timestamp;

        if(prevDisparities.isEmpty()){
            prevDisparities.add(currentDisparity);
        } else {
             if(prevDisparities.getLast().timestamp < timestamp){
                 prevDisparities.add(currentDisparity); // adds
             } else if(prevDisparities.getLast().timestamp == timestamp) {
                 prevDisparities.getLast().disparity = disparity; // replace
             } else {
                 // ignores it
             }
        }
    }

    /**
     * returns true if enableLowerDisparityLimit is true
     * @return
     */
    public boolean isDisparityLimitEnabled() {
        return DisparityLimitEnabled;
    }

    /**
     * sets enableDisparityLimit
     * @param DisparityLimitEnabled
     */
    public void setDisparityLimitEnabled(boolean DisparityLimitEnabled) {
        this.DisparityLimitEnabled = DisparityLimitEnabled;
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

    /**
     * returns checkAreaRectangle
     * @return
     */
    public Rectangle getCheckAreaRectangle() {
        return checkAreaRectangle;
    }

    /**
     * sets checkAreaRectangle
     * @param CheckAreaRectangle
     */
    public void setCheckAreaRectangle(Rectangle CheckAreaRectangle) {
        this.checkAreaRectangle.setRect(CheckAreaRectangle);
    }

    /**
     * returns usingCheckAreaRectangle
     * @return
     */
    public boolean isUsingCheckAreaRectangle() {
        return usingCheckAreaRectangle;
    }

    /**
     * sets useCheckAreaRectangle
     * @param usingCheckAreaRectangle
     */
    public void setUsingCheckAreaRectangle(boolean usingCheckAreaRectangle) {
        this.usingCheckAreaRectangle = usingCheckAreaRectangle;
    }
}
