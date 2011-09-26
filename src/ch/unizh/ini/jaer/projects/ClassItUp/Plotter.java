/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.ClassItUp;

/**
 *
 * @author tobi
 */
public interface Plotter {


    void update();          // Update the plots
    
    void init(Network N);   // Initialize the plotter on a network

    
}
