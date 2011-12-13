/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal;

/**
 *
 * @author matthias
 * 
 * The interface Signal provides methods to store an observed signal.
 */
public interface Signal extends TransitionHistory {
    
    /**
     * Initializes the signal.
     */
    public void init();
    
    /**
     * Gets the original Transition at the given position.
     * 
     * @param index The index corresponding to the position of the desired
     * Transition.
     * @return The Transition at the given position.
     */
    public Transition getOriginalTransition(int index);
    
    /**
     * Gets the period of the signal.
     * 
     * @return The period of the signal.
     */
    public int getPeriod();
    
    /**
     * Gets the phase of the signal.
     * 
     * @return The phase of the signal.
     */
    public int getPhase();
    
    /**
     * Sets the phase of the signal.
     * 
     * @param phase The phase of the signal.
     */
    public void setPhase(int phase);
    
    /**
     * Evaluates the transition in order to create a valid signal.
     */
    public void update();
    
    /**
     * Indicates whether the signal is valid or not.
     * 
     * @return True, if the signal is valid, false otherwise.
     */
    public boolean isValid();
}
