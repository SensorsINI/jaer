/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import com.sun.opengl.util.GLUT;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.stereopsis.StereoChipInterface;
import net.sf.jaer.stereopsis.StereoTranslateRotate;

/**
 * Vergence filter for stereo DVS.
 * It estimates the disparity of the closest moving object, and then use it to overlap the events from left and right eyes.
 * A global disparity is obtained using the histogram of leaky mass at each point along the x-axis.
 * 
 * @author Jun Haeng Lee
 */
public class StereoVergenceFilter extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {

    StereoChipInterface stereoChip = null;
    StereoTranslateRotate str;

    /**
     * Mass histogram of the left eye
     */
    protected HashMap<Integer, ArrayList> histogramLeft = new HashMap<Integer, ArrayList> ();
    /**
     * Mass histogram of the right eye
     */
    protected HashMap<Integer, ArrayList> histogramRight = new HashMap<Integer, ArrayList> ();
    /**
     * Found disparities
     */
    protected HashMap<Integer, Disparity> disparityValues = new HashMap<Integer, Disparity>();
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
    protected int lastTimestamp;
    /**
     * chip size in x-axis which is obtained by the method stereoChip.getLeft().getSizeX()
     */
    protected int size;
    /**
     * section length when the y-axis is divided into several sections defined by the variable 'numSectionsY'
     */
    protected int deltaY;
    /**
     * previous value of the global disparity. Used again when the disparity calculation is failed
     */
    protected int prevDisparity;

    private int numSectionsY = getPrefs ().getInt ("StereoVergenceFilter.numSectionsY", 1);
    private float XC_Threshold = getPrefs ().getFloat ("StereoVergenceFilter.XC_Threshold", 0.5f);
    private int massTimeConstantUs = getPrefs ().getInt ("StereoVergenceFilter.massTimeConstantUs", 50000);
    private float massScale = getPrefs ().getFloat ("StereoVergenceFilter.massScale", 0.5f);
    private boolean showHistogram = getPrefs ().getBoolean ("StereoVergenceFilter.showHistogram", true);


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
            log.warning ("AEChip " + chip + " is not StereoChipInterface");
        }

        // encloses a StereoTranslateRotate filter to adjust the offset of x-axis
        str = new StereoTranslateRotate (chip);
        setEnclosedFilter(str);

        prevDisparity = 0;

        final String display = "Display", histogram = "Histogram", xc = "Cross-correlation";
        setPropertyTooltip (histogram,"numSectionsY","Y-axis is divided into this number of sections to collect bins in each section. Must be one of 1,2, or 4.");
        setPropertyTooltip (xc,"XC_Threshold","Threshold of x-correlation to detect parity.");
        setPropertyTooltip (histogram, "massTimeConstantUs","time constant of leaky mass in the mass histogram.");
        setPropertyTooltip (display, "massScale","scales the bins of histogram.");
        setPropertyTooltip (display, "showHistogram","displays histogram on the screen.");
    }

    /**
     * Class to store the statistics of each point of x-axis.
     * It has a mass which increases when there comes an events and decays exponentially when there's no additional event.
     * 
     */
    public class Bins{
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
        public double getMassNow(int t) {
            return mass*Math.exp(((float) lastUpdateTime - t) / massTimeConstantUs);
        }

        /** returns the timestamp of the last event
         *
         * @return lastUpdateTime
         */
        public int getLastUpdateTime() {
            return lastUpdateTime;
        }

        /** save the timestamp of the last event
         *
         * @param lastUpdateTime
         */
        public void setLastUpdateTime(int lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }

        /** set the mass
         * 
         * @param mass
         */
        public void setMass(double mass) {
            this.mass = mass;
        }
    }

    public class Disparity{
        protected int disparity;
        protected boolean valid;

        Disparity(){
            disparity = 0;
            valid = false;
        }

        public void setDisparity(int disparity, boolean validity){
            this.disparity = disparity;
            valid = validity;
        }

        public int getDisparity() {
            return disparity;
        }

        public void setDisparity(int disparity) {
            this.disparity = disparity;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }
    }


    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if ( in == null ){
            return null;
        }
        if (  ! filterEnabled ){
            return in;
        }
        if(!initialized){
            initFilter();
        }
        

        // updates the histogram for each event
        for(BasicEvent e:in){
            BinocularEvent be = (BinocularEvent)e;
            lastTimestamp = e.timestamp;
            updateHistogram(be.eye, be.y/deltaY, be.x, e.timestamp);
            maybeCallUpdateObservers(in, e.timestamp);
        }

        updateDisparity();

        // filters
        out = str.filterPacket(in);
        return out;
    }

    private void updateDisparity(){
        // sets the range of delays to check the cross-correlation
        int startDelay = 0, endDelay = 0;
        if(prevDisparity == 0){
            endDelay = size/2;
        } else{
            startDelay = prevDisparity - 10;
            if(startDelay < 0) startDelay = 0;
            endDelay = prevDisparity + 10;
            if(endDelay > size/2) endDelay = size/2;
        }

        // finds disparities for every section
        findDisparity(startDelay, endDelay);

        // finds the maximum value of the disparities
        int maxDisparity = 0;
        for(int i=0; i<numSectionsY; i++){
            if(disparityValues.get(i).isValid() && disparityValues.get(i).getDisparity() > maxDisparity)
                maxDisparity = disparityValues.get(i).getDisparity();
        }

        // sets the global disparity
        if(maxDisparity != 0 && maxDisparity <= size/2){
            if(prevDisparity == 0)
                prevDisparity = maxDisparity;
            else
                prevDisparity = (prevDisparity+maxDisparity)/2;
        }

        // sets the x-axis offset
        str.setDx(-prevDisparity/2);
    }


    /** Finds the disparities for each y-axis section based on the cross-correlation of histograms
     * 
     * @param startDelay
     * @param endDelay
     */
    synchronized private void findDisparity(int startDelay, int endDelay){

        for(int i=0; i<numSectionsY; i++){
            boolean valid = false;
            double prevXc = (float) XC_Threshold;
            int maxPos = 0;
            for(int j = startDelay; j<endDelay; j++){
                double xc = calXCorrelation(histogramLeft.get(i), histogramRight.get(i), j);
                if(xc > prevXc){
                    prevXc = xc;
                    maxPos = j;
                    valid = true;
                }
            }
            disparityValues.get(i).setDisparity(maxPos, valid);
        }
    }


    /** calculates the cross-correlation between histograms of left and right eyes
     *
     * @param histoLeft
     * @param histoRight
     * @param delay
     * @return
     */
   private double calXCorrelation(ArrayList<Bins> histoLeft, ArrayList<Bins> histoRight, int delay){
       double ret = 0, sumX = 0, sumY = 0, sumXY = 0, sumXS = 0, sumYS = 0;
       double num = histoLeft.size()-delay;

       for(int i=0; i<histoLeft.size()-delay; i++){
           double x = histoLeft.get(i+delay).getMassNow(lastTimestamp);
           double y = histoRight.get(i).getMassNow(lastTimestamp);

           sumX += x;
           sumY += y;
           sumXY += x*y;
           sumXS += x*x;
           sumYS += y*y;
       }

       double aveX = sumX/num;
       double aveY = sumY/num;
       double sigmaX = Math.sqrt(sumXS/num - Math.pow(aveX, 2.0));
       double sigmaY = Math.sqrt(sumYS/num - Math.pow(aveY, 2.0));


       ret = (sumXY/num - aveX*aveY)/sigmaX/sigmaY;

       return Math.abs(ret);
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
                tmp = ((ArrayList<Bins>) histogramLeft.get(yIndex)).get(xPos);
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
        for(int i=0; i<numSectionsY; i++)
            disparityValues.put(i, new Disparity());
        prevDisparity = 0;
    }

    @Override
    public void initFilter() {
        if(stereoChip.getLeft() == null)
            return;
        
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
            disparityValues.put(i, new Disparity());
        }
        deltaY = size/numSectionsY;
        resetHistogram();
        initialized = true;
    }

    public void update(Observable o, Object arg) {
        if (o == this) {
        }
        if (o instanceof AEChip) {
            initFilter();
        }
    }

    public void annotate(float[][][] frame) {
        
    }

    public void annotate(Graphics2D g) {
        
    }

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

                gl.glRasterPos3f (1,j*deltaY + 5,0);
                glut.glutBitmapString (font,String.format ("Disparity = %d", disparityValues.get(j).getDisparity()));
            }

        } catch ( java.util.ConcurrentModificationException e ){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning (e.getMessage ());
        }
        gl.glPopMatrix ();
    }

    /**
     *
     * @return
     */
    public int getNumSectionsY() {
        return numSectionsY;
    }

    /**
     *
     * @param numSectionsY
     */
    synchronized public void setNumSectionsY(int numSectionsY) {
        if(numSectionsY >= 4)
            numSectionsY = 4;
        else if(numSectionsY <= 1)
            numSectionsY = 1;
        else
            numSectionsY = 2;

        if(this.numSectionsY != numSectionsY){
            this.numSectionsY = numSectionsY;
            initFilter();
        }
        getPrefs ().putInt ("StereoVergenceFilter.numSectionsY", numSectionsY);
    }

    /**
     *
     * @return
     */
    public float getXC_Threshold() {
        return XC_Threshold;
    }

    /**
     *
     * @param XC_Threshold
     */
    synchronized public void setXC_Threshold(float XC_Threshold) {
        if(XC_Threshold < 0f)
            this.XC_Threshold = 0f;
        else if(XC_Threshold > 1.0f)
            this.XC_Threshold = 1.0f;
        else
            this.XC_Threshold = XC_Threshold;

        getPrefs ().putFloat ("StereoVergenceFilter.XC_Threshold", XC_Threshold);
    }


    /**
     *
     * @return
     */
    public int getMassTimeConstantUs() {
        return massTimeConstantUs;
    }

    /**
     *
     * @param massTimeConstantUs
     */
    public void setMassTimeConstantUs(int massTimeConstantUs) {
        this.massTimeConstantUs = massTimeConstantUs;
        getPrefs ().putInt ("StereoVergenceFilter.massTimeConstantUs", massTimeConstantUs);
    }

    /**
     *
     * @return
     */
    public float getMassScale() {
        return massScale;
    }

    /**
     *
     * @param massScale
     */
    public void setMassScale(float massScale) {
        this.massScale = massScale;
        getPrefs ().putFloat ("StereoVergenceFilter.massScale", massScale);
    }

    /**
     *
     * @return
     */
    public boolean isShowHistogram() {
        return showHistogram;
    }

    /**
     *
     * @param showHistogram
     */
    public void setShowHistogram(boolean showHistogram) {
        this.showHistogram = showHistogram;
        getPrefs ().putBoolean ("StereoVergenceFilter.showHistogram", showHistogram);
    }

}
