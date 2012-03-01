/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Filter a pulsed laserline
 *
 * @author Thomas Mantel
 */
@Description("Filters a pulsed laserline using a histogram over several past periods")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class FilterLaserline extends EventFilter2D implements FrameAnnotater {

    /*
     * Variables
     */
    private boolean isInitialized = false;
    private int laserPeriod = 0;
    private int lastTriggerTimestamp = 0;
    private int nBins = 0;
    private HistogramData onHist;
    private HistogramData offHist;

    PxlScoreMap pxlScoreMap;
    private ArrayList laserline;

    private double[][] curBinWeights;
    /**
     * Options
     */
    protected int histogramHistorySize = getPrefs().getInt("FilterLaserline.histogramHistorySize", 1000);
    /**
     * 
     */
    protected int pxlScoreHistorySize = getPrefs().getInt("FilterLaserline.pxlScoreHistorySize", 5);
    /**
     * 
     */
    protected int binSize = getPrefs().getInt("FilterLaserline.binSize", 50);
    /**
     * 
     */
    protected boolean showDebugPixels = getPrefs().getBoolean("FilterLaserline.showDebugPixels", false);
    protected boolean useWeightedOnOff = getPrefs().getBoolean("FilterLaserline.useWeightedOnOff", true);
    protected float pxlScoreThreshold = getPrefs().getFloat("FilterLaserline.pxlScoreThreshold", 0f);
    protected boolean subtractAverage = getPrefs().getBoolean("FilterLaserline.subtractAverage", false);
    protected boolean allowNegativeScores = getPrefs().getBoolean("FilterLaserline.allowNegativeScores", true);
    
   /**
     * 
     * @param chip
     */
    public FilterLaserline(AEChip chip) {
        super(chip);
        
        setPropertyTooltip("Event Histogram", "histogramHistorySize", "How many periods should be used for event histogram?");
        setPropertyTooltip("Event Histogram", "binSize", "Bin size of event histogram in us");
        setPropertyTooltip("Pixel Scoring", "pxlScoreHistorySize", "For how many periods should the pixel scores be saved?");
        setPropertyTooltip("Pixel Scoring", "useWeightedOnOff", "Use different weights for on and off events based on their pseudo-SNR?");
        setPropertyTooltip("Pixel Scoring", "subtractAverage", "Subtract average to get bin weight?");
        setPropertyTooltip("Pixel Scoring", "allowNegativeScores", "Allow negativ scores?");
        setPropertyTooltip("Pixel Scoring", "pxlScoreThreshold", "Minimum score of pixel to be on laserline");
        setPropertyTooltip("Debugging", "showDebugPixels", "Display score of each pixel?");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        /*
         * do nothing if filter is not enabled
         */
        if (!isFilterEnabled()) {
            return in;
        }
        
        OutputEventIterator outItr = out.outputIterator();

        // iterate over each event in packet
        for (Object e : in) {
            // check if filter is initialized
            if (!isInitialized) {
                BasicEvent ev = (BasicEvent) e;
                if (ev.special) {
                    // if new and last laserPeriod do not differ too much from each other 
                    if (Math.abs(laserPeriod - (ev.timestamp - lastTriggerTimestamp)) < 10) {
                        // Init filter
                        laserPeriod = ev.timestamp - lastTriggerTimestamp;
                        initFilter();
                    } else if (lastTriggerTimestamp > 0) {
                        laserPeriod = ev.timestamp - lastTriggerTimestamp;
                    }
                    lastTriggerTimestamp = ev.timestamp;
                }
            }
            if (isInitialized) {
                PolarityEvent ev = (PolarityEvent) e;
                if (ev.special) {
                    /*
                     * Update pxlScoreMap, histograms, curBinWeights, laserline
                     */
                    pxlScoreMap.updatePxlScore();
                    
                    onHist.updateBins();
                    offHist.updateBins();
                    
                    updateBinWeights();
                    
                    laserline = pxlScoreMap.updateLaserline(laserline);
                    
                    // save timestamp
                    lastTriggerTimestamp = ev.timestamp;
                    if (pxlScoreMap.getScore((short) 121, (short) 124) > getPxlScoreThreshold()) {
                        log.log(Level.FINE, "pxlScore(121, 124) > threshold");
                    }
                    // write laserline to out
                    writeLaserlineToOutItr(outItr);
                } else {
                    // add to histogram
                    if (ev.polarity == Polarity.On) {
                        onHist.addToData((double) (ev.timestamp-lastTriggerTimestamp));
                    } else if (ev.polarity == Polarity.Off) {
                        offHist.addToData((double) (ev.timestamp-lastTriggerTimestamp));
                    }
                    
                    
                    // get score of event
                    double pxlScore = scoreEvent(ev);
                    // write score to pxlScoreMap
                    pxlScoreMap.addToScore(ev.x, ev.y, pxlScore);
                }
            }
        }
        return out;
    }

    /**
     * 
     */
    public void allocateMaps() {
        pxlScoreMap = new PxlScoreMap(chip.getSizeX(), chip.getSizeY(), this);
        laserline = new ArrayList(2*chip.getSizeX());
        onHist  = new HistogramData(histogramHistorySize, binSize,  nBins, this);
        offHist = new HistogramData(histogramHistorySize, binSize,  nBins, this);
        curBinWeights = new double[2][nBins];
    }

    @Override
    public void resetFilter() {
        /*
         * only reset when filter is initialized to avoid nullpointer exceptions
         */
        if (isInitialized) {
            pxlScoreMap.resetMap();
            laserline.clear();
            laserline.ensureCapacity(chip.getSizeX());
            onHist.resetHistData();
            offHist.resetHistData();
            Arrays.fill(curBinWeights[0],0d);
            Arrays.fill(curBinWeights[0],0d);
        }
    }

    @Override
    public void initFilter() {
        log.log(Level.INFO, "Initializing FilterLaserline, laserPeriod = {0}us", Integer.toString(laserPeriod));
        if (laserPeriod > 0 & binSize > 0) {
            nBins = (int) Math.ceil((double) laserPeriod / binSize);
        } else {
            log.severe("either laserPeriod or binSize is not greater than 0");
        }
        allocateMaps();
        pxlScoreMap.initMap();
        isInitialized = true;
        resetFilter();
    }

    private double scoreEvent(PolarityEvent ev) {
        double pxlScore = 0d;
        int curBin = (int) ((ev.timestamp-lastTriggerTimestamp) / binSize);

        // check bin to avoid nullpointer exception
        if (curBin >= nBins) {
//            log.warning("bin number to big, omitting event");
            return pxlScore;
        } else if (curBin < 0) {
//            log.warning("bin number to small, omitting event");
            return pxlScore;
        } 
        
        short pol;
        if (ev.polarity == Polarity.On) {
            pol = 0;
        } else {
            pol = 1;
        }
        
        pxlScore = curBinWeights[pol][curBin];
        return pxlScore;
    }
    

    private void updateBinWeights() {
        curBinWeights[0] = onHist.getNormalized(curBinWeights[0],subtractAverage);
        curBinWeights[1] = offHist.getNormalized(curBinWeights[1],subtractAverage);
        
        double divisor = onHist.getMaxVal()+offHist.getMaxVal();
        if (divisor == 0) divisor = 1.0;
        double onFactor  = onHist.getMaxVal()/divisor;
        double offFactor = offHist.getMaxVal()/divisor;
        
        for (int i = 0; i < nBins; i++) {
            if (useWeightedOnOff) {
                curBinWeights[0][i] *= onFactor;
                curBinWeights[1][i] *= offFactor;
            }
            if (!allowNegativeScores & curBinWeights[0][i] < 0) {
                curBinWeights[0][i] = 0;
            }
            if (!allowNegativeScores & curBinWeights[1][i] < 0) {
                curBinWeights[1][i] = 0;
            }
        }
    }

    private void writeLaserlineToOutItr(OutputEventIterator outItr) {
        for (Object o : laserline) {
            short[] pxl = (short[]) o;
            BasicEvent outEvent = (BasicEvent) outItr.nextOutput();
            outEvent.setTimestamp(lastTriggerTimestamp);
            outEvent.setX(pxl[0]);
            outEvent.setY(pxl[1]);
        }
    }
    
    int getLastTriggerTimestamp() {
        return this.lastTriggerTimestamp;
    }

    int getLaserPeriod() {
        return this.laserPeriod;
    }

    int getNBins() {
        return this.nBins;
    }

    /**
     * **************************
     * Methods for filter options
     * **************************
     */

    /**
     * gets binSize
     *
     * @return
     */
    public int getBinSize() {
        return this.binSize;
    }

    /**
     * sets binSize
     *
     * @see #getBinSize
     * @param binSize
     */
    public void setBinSize(int binSize) {
        getPrefs().putInt("FilterLaserline.binSize", binSize);
        getSupport().firePropertyChange("binSize", this.binSize, binSize);
        isInitialized = false;
        this.binSize = binSize;
        initFilter();
    }

    /**
     * 
     * @return
     */
    public int getMinBinSize() {
        return 1;
    }

    /**
     * 
     * @return
     */
    public int getMaxBinSize() {
        return 1000;
    }

    /**
     * gets histogramWindowSize
     *
     * @return
     */
    public int getHistogramHistorySize() {
        return this.histogramHistorySize;
    }

    /**
     * sets histogramWindowSize
     *
     * @param histogramHistorySize 
     * @see #getBinSize
     */
    public void setHistogramHistorySize(int histogramHistorySize) {
        getPrefs().putInt("FilterLaserline.histogramWindowSize", histogramHistorySize);
        getSupport().firePropertyChange("histogramWindowSize", this.histogramHistorySize, histogramHistorySize);
        isInitialized = false;
        this.histogramHistorySize = histogramHistorySize;
        initFilter();
    }

    /**
     * 
     * @return
     */
    public int getMinHistogramHistorySize() {
        return 1;
    }

    /**
     * 
     * @return
     */
    public int getMaxHistogramHistorySize() {
        return 10000;
    }
    
    
    /**
     * gets useWeightedOnOff
     *
     * @return 
     */
    public boolean getUseWeightedOnOff() {
        return this.useWeightedOnOff;
    }

    /**
     * sets useWeightedOnOff
     *
     * @see #getUseWeightedOnOff
     * @param useWeightedOnOff boolean
     */
    public void setUseWeightedOnOff(boolean useWeightedOnOff) {
        getPrefs().putBoolean("FilterLaserline.useWeightedOnOff", useWeightedOnOff);
        getSupport().firePropertyChange("useWeightedOnOff", this.useWeightedOnOff, useWeightedOnOff);
        this.useWeightedOnOff = useWeightedOnOff;
    }
    
    /**
     * gets showDebugPixels
     *
     * @return 
     */
    public boolean getShowDebugPixels() {
        return this.showDebugPixels;
    }

    /**
     * sets showDebugPixels
     *
     * @see #getShowDebugPixels
     * @param showDebugPixels boolean
     */
    public void setShowDebugPixels(boolean showDebugPixels) {
        getPrefs().putBoolean("FilterLaserline.showDebugPixels", showDebugPixels);
        getSupport().firePropertyChange("showDebugPixels", this.showDebugPixels, showDebugPixels);
        this.showDebugPixels = showDebugPixels;
    }
    
     /**
     * gets pxlScoreThreshold
     *
     * @return pxlScoreThreshold
     */
    public float getPxlScoreThreshold() {
        return this.pxlScoreThreshold;
    }

    /**
     * sets pxlScoreThreshold
     *
     * @see #getPxlScoreThreshold
     * @param pxlScoreThreshold
     */
    public void setPxlScoreThreshold(float pxlScoreThreshold) {
        getPrefs().putFloat("FilterLaserline.pxlScoreThreshold", pxlScoreThreshold);
        getSupport().firePropertyChange("pxlScoreThreshold", this.pxlScoreThreshold, pxlScoreThreshold);
        this.pxlScoreThreshold = pxlScoreThreshold;
    }

    /**
     * 
     * @return
     */
    public double getMinPxlScoreThreshold() {
        return 0d;
    }

    /**
     * 
     * @return
     */
    public double getMaxPxlScoreThreshold() {
        return 2d;
    }
    
    /**
     * gets subtractAverage
     *
     * @return 
     */
    public boolean getSubtractAverage() {
        return this.subtractAverage;
    }

    /**
     * sets subtractAverage
     *
     * @see #getSubtractAverage
     * @param subtractAverage boolean
     */
    public void setSubtractAverage(boolean subtractAverage) {
        getPrefs().putBoolean("FilterLaserline.subtractAverage", subtractAverage);
        getSupport().firePropertyChange("subtractAverage", this.subtractAverage, subtractAverage);
        this.subtractAverage = subtractAverage;
    }
    
        /**
     * gets allowNegativeScores
     *
     * @return 
     */
    public boolean getAllowNegativeScores() {
        return this.allowNegativeScores;
    }

    /**
     * sets allowNegativeScores
     *
     * @see #getShowDebugPixels
     * @param allowNegativeScores boolean
     */
    public void setAllowNegativeScores(boolean allowNegativeScores) {
        getPrefs().putBoolean("FilterLaserline.allowNegativeScores", allowNegativeScores);
        getSupport().firePropertyChange("allowNegativeScores", this.allowNegativeScores, allowNegativeScores);
        this.allowNegativeScores = allowNegativeScores;
    }
    
        /**
     * gets pxlScoreHistorySize
     *
     * @return
     */
    public int getPxlScoreHistorySize() {
        return this.pxlScoreHistorySize;
    }

    /**
     * sets pxlScoreHistorySize
     *
     * @see #getPxlScoreHistorySize
     * @param pxlScoreHistorySize
     */
    public void setPxlScoreHistorySize(int pxlScoreHistorySize) {
        getPrefs().putInt("FilterLaserline.pxlScoreHistorySize", pxlScoreHistorySize);
        getSupport().firePropertyChange("pxlScoreHistorySize", this.pxlScoreHistorySize, pxlScoreHistorySize);
        isInitialized = false;
        this.pxlScoreHistorySize = pxlScoreHistorySize;
        initFilter();
    }

    /**
     * 
     * @return
     */
    public int getMinPxlScoreHistorySize() {
        return 0;
    }

    /**
     * 
     * @return
     */
    public int getMaxPxlScoreHistorySize() {
        return 10;
    }
    
    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled() | pxlScoreMap == null | showDebugPixels == false) {
        } else {
            GL gl = drawable.getGL();
            gl.glPointSize(4f);
            gl.glBegin(GL.GL_POINTS);
            {
                double pxlScore;
                for (short x = 0; x < chip.getSizeX(); x++) {
                    for (short y = 0; y < chip.getSizeY(); y++) {
                        pxlScore = pxlScoreMap.getScore(x, y);

                        if (pxlScore > 0) {
                            gl.glColor3d(0.0, 0.0, pxlScore);
                            gl.glVertex2f(x, y);
                        } else if (pxlScore < 0) {
                            gl.glColor3d(0.0, pxlScore, 0.0);
                            gl.glVertex2f(x, y);
                        }
                    }

                }
            }
            gl.glEnd();
        }
    }
}