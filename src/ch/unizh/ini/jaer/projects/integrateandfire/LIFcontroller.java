/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;


/**
 *
 * @author Peter
 */
public interface LIFcontroller {
    // Define methods common to all neural network architectures
    // 
    // Note it's perfectly acceptable to make sublasses that do not have functioning 
    // implementations of all these methods.
        
    // Set Methods
    
    public void setThresholds(float thresh);
    
    public void setTaus(float tc);
    
    public void setSats(float tc);
    
    public void setDoubleThresh(boolean tc);
    
    public void reset();
        
    public String networkStatus();
    
}
