/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.brainfair;

import java.util.Observable;
import javax.media.opengl.GL;
import net.sf.jaer.event.OrientationEvent;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.label.SimpleOrientationFilter;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Computes statistics of the event input and displays them in a new window.
 * @author Michael Pfeiffer
 */

@Description("Displays statistics over recently observed orientations") // adds this string as description of class for jaer GUIs
public class VisualizerFilter extends EventFilter2D implements FrameAnnotater {

    // Update factor for history of orientations
    private float orientHistoryFactor = getFloat("orientHistoryFactor", 0.001f);
    
    // Update factor for history of overall firing rate
    private float overallHistoryFactor = getFloat("overallHistoryFactor", 10.0f);
    // Range to display for overall firing rate
    private float overallRange = getFloat("overallRange", 10.0f);
    
    
    // Number of bins for ISI histogram
    private int nBins = getInt("nBins", 50);
    private int maxIsiUs = getInt("maxIsiUs",10000);
    private int minIsiUs = getInt("minIsiUs",3000);
    // Update factor for history of overall firing rate
    private float ISIHistoryFactor = getFloat("ISIHistoryFactor", 40.0f);

    private float pixelHistoryFactor = getFloat("pixelHistoryFactor", 2000.0f);

    private int maxTime = getInt("maxTime", 5000);

    private FilterChain filterChain;  // Enclosed Gaussian Tracker filter
    private StatisticsCalculator statistics;
    
    // Display frame for statistics
    private StatisticsVisualizer statFrame = null;
    

    public VisualizerFilter(AEChip chip) {
        super(chip);
        
        // Create enclosed filter and filter chain
        statistics = new StatisticsCalculator(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(statistics);
        setEnclosedFilterChain(filterChain);
    
        final String orient = "Orientation", isi = "Interspike Interval", pixel="Pixel Histogram";
        final String time = "Last Spike Time", overall="Overall Rate";
        
        // add this string tooltip to FilterPanel GUI control for filterLength
        setPropertyTooltip(orient, "orientHistoryFactor", "Update rate for orientation history");
        setPropertyTooltip(overall, "overallHistoryFactor", "Update rate for overall firing rate");
        setPropertyTooltip(overall, "overallRange", "Display range for overall firing rate (sec)");
        setPropertyTooltip(isi, "nBins", "Number of bins for ISI histogram");
        setPropertyTooltip(isi, "maxIsiUs","maximim ISI in us, larger ISI's are discarded");
        setPropertyTooltip(isi, "minIsiUs","minimum ISI in us, smaller ISI's are discarded");
        setPropertyTooltip(isi, "ISIHistoryFactor","Update rate for ISI history");
        setPropertyTooltip(pixel, "PixelHistoryFactor","Update rate for Pixel history");
        setPropertyTooltip(time, "maxTime","Maximum time window length");
    }
    
    
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!filterEnabled) return in;

        // Helper variables
        int i;
        
        EventPacket<?> nextOut = getEnclosedFilterChain().filterPacket(in);

        if ( in == null ){
            return null;
        }
        
        return in;
        
    }

    @Override
    public void resetFilter() {
        filterChain.reset();
        statistics.resetFilter();
        statistics.setOrientTau(orientHistoryFactor);
        statistics.setOverallTau(overallHistoryFactor);
        statistics.setOverallRange(overallRange);
        statistics.setMaxIsiUs(maxIsiUs);
        statistics.setMinIsiUs(minIsiUs);
        statistics.setnBins(nBins);
        statistics.setISITau(ISIHistoryFactor);
        statistics.setPixelTau(pixelHistoryFactor);
        statistics.setMaxTime(maxTime);
    }

    @Override
    public void initFilter() {
        resetFilter();

        createVisualizerFrame();
    }
    
    private void createVisualizerFrame() {
        if (statFrame != null) {
            statFrame.dispose();
            statFrame = null;
        }
            
        // Create display window
        System.out.println("Creating Window!");
        statFrame = new StatisticsVisualizer();
        System.out.println("Finished creating window!");
        statFrame.setVisible(true);        
        
        statFrame.setDataSource(statistics);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (statFrame != null) {
            statFrame.repaint();
        }
        else {
            System.out.println("Creating...");
            createVisualizerFrame();
        }
    }

    public float getOrientHistoryFactor() {
        return orientHistoryFactor;
    }

    public void setOrientHistoryFactor(float orientHistoryFactor) {
        this.orientHistoryFactor = orientHistoryFactor;
        putFloat("orientHistoryFactor", orientHistoryFactor);
        statistics.setOrientTau(orientHistoryFactor);
    }

    public float getOverallHistoryFactor() {
        return overallHistoryFactor;
    }

    public void setOverallHistoryFactor(float overallHistoryFactor) {
        this.overallHistoryFactor = overallHistoryFactor;
        putFloat("overallHistoryFactor", overallHistoryFactor);
        statistics.setOverallTau(overallHistoryFactor);
    }

    public float getOverallRange() {
        return overallRange;
    }

    public void setOverallRange(float overallRange) {
        this.overallRange = overallRange;
        putFloat("overallRange", overallRange);
        statistics.setOverallRange(overallRange);
    }

    public int getnBins() {
        return nBins;
    }

    public void setnBins(int nBins) {
        this.nBins = nBins;
        putFloat("nBins", nBins);
        statistics.setnBins(nBins);
    }

    public float getISIHistoryFactor() {
        return ISIHistoryFactor;
    }

    public void setISIHistoryFactor(float ISIHistoryFactor) {
        this.ISIHistoryFactor = ISIHistoryFactor;
        putFloat("ISIHistoryFactor", ISIHistoryFactor);
        statistics.setISITau(ISIHistoryFactor);
    }

    public int getMaxIsiUs() {
        return maxIsiUs;
    }

    public void setMaxIsiUs(int maxIsiUs) {
        this.maxIsiUs = maxIsiUs;
        putFloat("maxIsiUs", maxIsiUs);
        statistics.setMaxIsiUs(maxIsiUs);
    }

    public int getMinIsiUs() {
        return minIsiUs;
    }

    public void setMinIsiUs(int minIsiUs) {
        this.minIsiUs = minIsiUs;
        putFloat("minIsiUs", minIsiUs);
        statistics.setMinIsiUs(minIsiUs);
    }

    public float getPixelHistoryFactor() {
        return pixelHistoryFactor;
    }

    public void setPixelHistoryFactor(float pixelHistoryFactor) {
        this.pixelHistoryFactor = pixelHistoryFactor;
        putFloat("pixelHistoryFactor", pixelHistoryFactor);
        statistics.setPixelTau(pixelHistoryFactor);
    }

    public int getMaxTime() {
        return maxTime;
    }

    public void setMaxTime(int maxTime) {
        this.maxTime = maxTime;
        putInt("maxTime", maxTime);
        statistics.setMaxTime(maxTime);
    }
    
    
    
}
