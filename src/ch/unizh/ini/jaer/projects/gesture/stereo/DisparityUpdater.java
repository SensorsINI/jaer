/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import com.sun.opengl.util.GLUT;
import java.awt.Color;
import java.awt.Rectangle;
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
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.stereopsis.StereoChipInterface;

/**
 *
 * @author Jun Haeng Lee
 */
public class DisparityUpdater extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/  {

    /**
     * time constant of leaky mass in the mass histogram
     */
    private int massTimeConstantUs = getPrefs ().getInt ("StereoVergenceFilter.massTimeConstantUs", 100000);

    /**
     * scales the bins of histogram
     */
    private float massScale = getPrefs ().getFloat ("StereoVergenceFilter.massScale", 1f);

    /**
     * displays histogram on the screen
     */
    private boolean showHistogram = getPrefs ().getBoolean ("StereoVergenceFilter.showHistogram", false);

    /**
     * mass below this value will be set to zero
     */
    private float massThreshold  = getPrefs ().getFloat ("StereoVergenceFilter.massThreshold", 15.0f);

    /**
     * maximum allowed disparity in fraction of Chipsize
     */
    private float maxDisparityFractionChipsizeX = getPrefs ().getFloat ("StereoVergenceFilter.maxDisparityFractionChipsizeX", 0.7f);


    /**
     * line color of the histogram of the left eye
     */
    protected Color colorLeft = Color.GREEN;
    /**
     * line color of the histogram of the right eye
     */
    protected Color colorRight = Color.ORANGE;
    /**
     * SV ID for Annotation
     * -1 for global disparity, cluster number for cluster disparity
     */
    protected int IDforAnnotation = -1;
    /**
     * ID of histogram to be annotated
     */
    StereoDisparity disparityForAnnotation = null;


    /**
     * becomes true if this filter is initialized
     */
    protected boolean initialized = false;
    /**
     * Timestamp of the last event ever processed
     */
    protected int lastTimestamp = -1;
    /**
     * chip size in x-axis which is obtained by the method stereoChip.getLeft().getSizeX()
     */
    protected int size;

    /**
     * stereo vergence for global disparity
     */
    protected StereoDisparity globalDisparity = null;

    /**
     * stereo vergence for cluster disparity
     */
    protected HashMap<Integer, StereoDisparity> clusterDisparity = new HashMap<Integer, StereoDisparity>();

    /**
     * reference ID for StereoTranslateRotate filter
     */
    protected int IDforVergence = -1;
    /**
     * reference disparity for StereoTranslateRotate filter
     */
    StereoDisparity disparityForVergence = null;


    /**
     *
     * @param chip
     */
    public DisparityUpdater(AEChip chip) {
        super(chip);
        chip.addObserver(this);

        if ( !(chip != null && chip instanceof StereoChipInterface) ){
            log.log (Level.WARNING, "AEChip {0} is not StereoChipInterface", chip);
        }

        globalDisparity = new StereoDisparity(-1, size, massTimeConstantUs, massThreshold, maxDisparityFractionChipsizeX, 0, -1);
        disparityForAnnotation = globalDisparity;
        disparityForVergence = globalDisparity;

        final String display = "Display", histogram = "Histogram", disparity="Disparity";
        setPropertyTooltip (histogram, "massTimeConstantUs","time constant of leaky mass in the mass histogram.");
        setPropertyTooltip (histogram, "massThreshold","mass below this value will be set to zero.");
        setPropertyTooltip (histogram, "subSampleingRatio","sub-sampling ration of event to consruct histogram.");
        setPropertyTooltip (display, "massScale","scales the bins of histogram.");
        setPropertyTooltip (display, "showHistogram","displays histogram on the screen.");
        setPropertyTooltip (disparity, "maxDisparityFractionChipsizeX","maximum allowed disparity in fraction of Chipsize.");
    }

    @Override
    public void resetFilter() {
        globalDisparity.reset(0, -1);
        disparityForAnnotation = globalDisparity;
        disparityForVergence = globalDisparity;
        lastTimestamp = -1;
    }

    @Override
    synchronized public void initFilter() {
        if(chip == null)
            return;

        size = chip.getSizeX();
        globalDisparity.setSize(size);
        lastTimestamp = -1;

        initialized = true;
    }



    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        System.out.println("filterPacket in  DisparityUpdater");
        return in;
    }

    /**
     * adds an event to update histograms
     * @param be
     */
    public void addEvent(BinocularEvent be){
        lastTimestamp = be.timestamp;
        globalDisparity.updateHistogram(be);
        for(StereoDisparity sd : clusterDisparity.values()){
            sd.updateHistogram(be);
        }
    }

    @Override
    public void update (Observable o,Object arg){
        if (o instanceof AEChip) {
            initFilter();
        } else if ( o instanceof BlurringFilterStereo ) {
            UpdateMessage msg = (UpdateMessage) arg;

            // estimates disparity
            globalDisparity.updateDisparity(msg.timestamp);
            for(StereoDisparity sd : clusterDisparity.values()){
                sd.updateDisparity(msg.timestamp);
                // if the updated disparity if not valid, replaces it with the global disparity
                if(sd.getDisparity() == -size)
                    sd.setDisparity(globalDisparity.getDisparity(), msg.timestamp);
            }
        } else {
            // do nothing
        }
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
            for (int i=1; i<size; i++){
                gl.glBegin(GL.GL_LINES);
                {
                    gl.glVertex2i(i-1, (int) (massScale*disparityForAnnotation.getBins(BinocularEvent.Eye.LEFT, i-1)));
                    gl.glVertex2i(i, (int) (massScale*disparityForAnnotation.getBins(BinocularEvent.Eye.LEFT, i)));
                }
                gl.glEnd();
            }

            colorRight.getRGBComponents (rgb);
            gl.glColor3fv (rgb,0);
            for (int i=1; i<size; i++){
                gl.glBegin(GL.GL_LINES);
                {
                    gl.glVertex2i(i-1, (int) (massScale*disparityForAnnotation.getBins(BinocularEvent.Eye.RIGHT, i-1)));
                    gl.glVertex2i(i, (int) (massScale*disparityForAnnotation.getBins(BinocularEvent.Eye.RIGHT, i)));
                }
                gl.glEnd();
            }

            int font = GLUT.BITMAP_HELVETICA_18;
            GLUT glut = chip.getCanvas ().getGlut ();
            gl.glColor3f (1,1,1);

            gl.glRasterPos3f (85,5,0);
            glut.glutBitmapString (font,String.format ("GD = %.1f", globalDisparity.getDisparity()));

            // shows cluster disparities
            for(StereoDisparity sd:clusterDisparity.values()){
                Rectangle rc = sd.getCheckAreaRectangle();
                gl.glRasterPos3f (rc.x,rc.y - 5,0);
                glut.glutBitmapString (font,String.format ("D = %.1f", sd.getDisparity()));
            }
        } catch ( java.util.ConcurrentModificationException e ){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning (e.getMessage ());
        }
        gl.glPopMatrix ();
    }


    /** returns the global disparity between left and right eyes.
     * @return
     */
    public float getGlobalDisparity(){
        return globalDisparity.getDisparity();
    }

    /**
     * returns the disparity used for stereo vergence
     * @return
     */
    public float getVergenceDisparity(){
        return disparityForVergence.getDisparity();
    }

    /**
     * returns the disparity with the given id
     * @param id
     * @return
     */
    public float getDisparity(int id){
        float ret = 0;

        if(id >= 0 && clusterDisparity.get(id) != null){
            ret = clusterDisparity.get(id).getDisparity();
        } else
            ret = globalDisparity.getDisparity();

        return ret;
    }

    /**
     * adds a new cluster SV
     *
     * @param id
     * @param area
     * @param initialDisparity
     * @param timestamp
     */
    public void addClusterSV(int id, Rectangle area, float initialDisparity, int timestamp){
        StereoDisparity sv = new StereoDisparity(id, size, massTimeConstantUs, 2, maxDisparityFractionChipsizeX, initialDisparity, timestamp);
        sv.setCheckAreaRectangle(area);
        sv.setUsingCheckAreaRectangle(true);
//        sv.setMaxDisparityChangePixels(5);
//        sv.setMaxDisparityChangeEnabled(true);

        clusterDisparity.put(id, sv);
    }

    /**
     * returns true if clusterDisparity contains the id
     * @param id
     * @return
     */
    public boolean containsClusterSV(int id){
        return clusterDisparity.containsKey(id);
    }

    /**
     * updates CheckAreaRectangle of cluster SV
     * @param id
     * @param area
     */
    public void updateClusterSVArea(int id, Rectangle area){
        if(clusterDisparity.get(id) != null)
            clusterDisparity.get(id).setCheckAreaRectangle(area);
    }

    /**
     * removes cluster SV
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public void removeClusterSV(int id){
        if(id == IDforVergence){
            setIDforVergence(-1);
        }

        if(id == IDforAnnotation){
            setIDforAnnotation(-1);
        }

        clusterDisparity.remove(id);
    }

    /**
     * gets IDforAnnotation
     * @return
     * id : -1 for global disparity, cluster number for cluster disparity
     */
    public int getIDforAnnotation(){
        return IDforAnnotation;
    }

    /**
     * sets IDforAnnotation
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public void setIDforAnnotation(int id){
        if(id >= 0 && clusterDisparity.get(id) != null){
            disparityForAnnotation = clusterDisparity.get(id);
            IDforAnnotation = id;
        }else{
            disparityForAnnotation = globalDisparity;
            IDforAnnotation = -1;
        }
    }

    /**
     * gets IDforVergence
     * @return
     * id : -1 for global disparity, cluster number for cluster disparity
     */
    public int getIDforVergence(){
        return IDforVergence;
    }

    /**
     * sets IDforVergence
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public void setIDforVergence(int id){
        if(id >= 0 && clusterDisparity.get(id) != null){
            disparityForVergence = clusterDisparity.get(id);
            IDforVergence = id;
        }else{
            disparityForVergence = globalDisparity;
            IDforVergence = -1;
        }
    }

    /**
     * returns true if enableLowerDisparityLimit is true
     * @return
     *
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public boolean isDisparityLimitEnabled(int id) {
        boolean ret = false;

        if(id == -1){
            ret = globalDisparity.isDisparityLimitEnabled();
        } else if(id >= 0){
            if(clusterDisparity.get(id) != null)
                ret = clusterDisparity.get(id).isDisparityLimitEnabled();
        } else {

        }

        return ret;
    }

    /**
     * sets enableDisparityLimit
     * @param disparityLimitEnabled
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public void setDisparityLimitEnabled(boolean disparityLimitEnabled, int id) {
        if(id == -1){
            globalDisparity.setDisparityLimitEnabled(disparityLimitEnabled);
        } else if(id >= 0){
            if(clusterDisparity.get(id) != null)
                clusterDisparity.get(id).setDisparityLimitEnabled(disparityLimitEnabled);
        } else {
            globalDisparity.setDisparityLimitEnabled(disparityLimitEnabled);
            for(StereoDisparity sv:clusterDisparity.values()){
                sv.setDisparityLimitEnabled(disparityLimitEnabled);
            }
        }
    }

    /**
     * returns disparityLimit
     * @return
     *
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public int getDisparityLimit(int id) {
        int ret = -size;

        if(id == -1){
            ret = globalDisparity.getDisparityLimit();
        } else if(id >= 0){
            if(clusterDisparity.get(id) != null)
                ret = clusterDisparity.get(id).getDisparityLimit();
        } else {

        }

        return ret;
    }

    /**
     * returns lowLimit
     * @return
     *
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public boolean isLowLimit(int id){
        boolean ret = false;

        if(id == -1){
            ret = globalDisparity.isLowLimit();
        } else if(id >= 0){
            if(clusterDisparity.get(id) != null)
                ret = clusterDisparity.get(id).isLowLimit();
        } else {

        }

        return ret;
    }

    /**
     * sets lowerDisparityLimit
     *
     * @param disparityLimit
     * @param useLowLimit
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public void setDisparityLimit(int disparityLimit, boolean useLowLimit, int id) {
        if(id == -1){
            globalDisparity.setDisparityLimit(disparityLimit, useLowLimit);
        } else if(id >= 0){
            if(clusterDisparity.get(id) != null)
                clusterDisparity.get(id).setDisparityLimit(disparityLimit, useLowLimit);
        } else {
            globalDisparity.setDisparityLimit(disparityLimit, useLowLimit);
            for(StereoDisparity sv:clusterDisparity.values()){
                sv.setDisparityLimit(disparityLimit, useLowLimit);
            }
        }
    }

    /**
     * returns maxDisparityChangePixelsPerMs
     * @return
     *
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public int getMaxDisparityChangePixels(int id) {
        int ret = -size;

        if(id == -1){
            ret = globalDisparity.getMaxDisparityChangePixels();
        } else if(id >= 0){
            if(clusterDisparity.get(id) != null)
                ret = clusterDisparity.get(id).getMaxDisparityChangePixels();
        } else {

        }

        return ret;
    }

    /**
     * sets maxDisparityChangePixelsPerMs
     *
     * @param maxDisparityChangePixels
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public void setMaxDisparityChangePixels(int maxDisparityChangePixels, int id) {
        if(id == -1){
            globalDisparity.setMaxDisparityChangePixels(maxDisparityChangePixels);
        } else if(id >= 0){
            if(clusterDisparity.get(id) != null)
                clusterDisparity.get(id).setMaxDisparityChangePixels(maxDisparityChangePixels);
        } else {
            globalDisparity.setMaxDisparityChangePixels(maxDisparityChangePixels);
            for(StereoDisparity sv:clusterDisparity.values()){
                sv.setMaxDisparityChangePixels(maxDisparityChangePixels);
            }
        }
    }

    /**
     *  sets enableMaxDisparityChange
     * @param id : -1 for global disparity, cluster number for cluster disparity
     * @return
     */
    public boolean isMaxDisparityChangeEnabled(int id) {
        boolean ret = false;

        if(id == -1){
            ret = globalDisparity.isMaxDisparityChangeEnabled();
        } else if(id >= 0){
            if(clusterDisparity.get(id) != null)
                ret = clusterDisparity.get(id).isMaxDisparityChangeEnabled();
        } else {

        }

        return ret;
    }

    /**
     * sets maxDisparityChangeEnabled
     * @param maxDisparityChangeEnabled
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public void setMaxDisparityChangeEnabled(boolean maxDisparityChangeEnabled, int id) {
        if(id == -1){
            globalDisparity.setMaxDisparityChangeEnabled(maxDisparityChangeEnabled);
        } else if(id >= 0){
            if(clusterDisparity.get(id) != null)
                clusterDisparity.get(id).setMaxDisparityChangeEnabled(maxDisparityChangeEnabled);
        } else {
            globalDisparity.setMaxDisparityChangeEnabled(maxDisparityChangeEnabled);
            for(StereoDisparity sv:clusterDisparity.values()){
                sv.setMaxDisparityChangeEnabled(maxDisparityChangeEnabled);
            }
        }
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
        globalDisparity.setMassTimeConstantUs(massTimeConstantUs);
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
        globalDisparity.setMassThreshold(massThreshold);
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
        globalDisparity.setMaxDisparityFractionChipsizeX(maxDisparityFractionChipsizeX);
        getPrefs ().putFloat ("StereoVergenceFilter.maxDisparityFractionChipsizeX", maxDisparityFractionChipsizeX);
    }
}
