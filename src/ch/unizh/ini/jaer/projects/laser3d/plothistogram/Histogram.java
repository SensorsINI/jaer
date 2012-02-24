/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d.plothistogram;

import java.util.Observer;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Thomas Mantel
 */
public interface Histogram {
    
    /**
     * 
     * @return
     */
    boolean isInitialized();
    
    /**
     * 
     * @return
     */
    double[] XData();
    
    /**
     * 
     * @return
     */
    double[][] YData();
    
    /**
     * 
     * @return
     */
    int nBins();
    
    /**
     * 
     * @return
     */
    double maxYVal();
    
    
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