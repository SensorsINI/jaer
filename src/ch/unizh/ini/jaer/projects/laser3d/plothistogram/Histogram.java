/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d.plothistogram;

import net.sf.jaer.event.PolarityEvent;

/**
 *
 * @author Thomas Mantel
 */
public interface Histogram {
    
    String getHistogramName();
    
    /**
     * 
     * @return
     */
    boolean isInitialized();
    
    /**
     * 
     * @return
     */
    float[] XData();
    
    /**
     * 
     * @return
     */
    float[][] YData();
    
    /**
     * 
     * @return
     */
    int nBins();
    
    /**
     * 
     * @return
     */
    float maxYVal();
    
    
    /**
     * 
     * @param ev
     */
    void processEvent(PolarityEvent ev);
    
    /**
     * 
     */
    void initHistogram();
    
    /**
     * 
     */
    void resetHistogram();
    
}