/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.occurance;

/**
 *
 * @author matthias
 * 
 * This type of predictor tries to predict the occurance for an on- and for an
 * off-event.
 * 
 */
public interface OccurancePredictor {
    
    /**
     * Indicates whether the instance is able to make a prediction about the 
     * occurance of events.
     * 
     * @return True, if a prediction is possible, false otherwise.
     */
    public boolean isPredictable();
    
    /**
     * The method computes the temporal distance between the given timestamp and
     * the closest prediction for the event matching the given type.
     * 
     * @param type The type of the event.
     * @param timestamp The timestamp used to compute the temporal distance.
     * @return The distance between the given timestamp and the closest 
     * prediction.
     */
    public int getDistance(int type, int timestamp);
    
    /**
     * The method computes the temporal distance between the given relative 
     * timestamp and the closest prediction for the event matching the given 
     * type.
     * 
     * @param type The type of the event.
     * @param timestamp The timestamp used to compute the temporal distance.
     * @return The distance between the given timestamp and the closest 
     * prediction.
     */
    public int getRelativeDistance(int type, int timestamp);
}
