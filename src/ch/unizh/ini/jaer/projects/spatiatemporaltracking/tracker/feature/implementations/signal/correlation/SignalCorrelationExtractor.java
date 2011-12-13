/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.correlation;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.correlation.CorrelationItem;
import java.util.List;

/**
 *
 * @author matthias
 * 
 * This type of Extractor compares the extracted signal of the observed
 * object with the extracted transition history. To do this comparision a
 * correlation is used.
 */
public interface SignalCorrelationExtractor {
    
    /**
     * Gets the values of the correlation with the signal and the transition
     * history.
     * 
     * @return The correlation with the signal and the transition history. 
     */
    public List<CorrelationItem> getCorrelation();
}
