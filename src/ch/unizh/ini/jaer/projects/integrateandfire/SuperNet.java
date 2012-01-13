/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

/**
 *
 * @author Peter
 */
public interface SuperNet {
    // Define methods common to all neural network architectures
    
    public void setThresholds(float thresh);
    
    public void setTaus(float tc);
    
    public void setSats(float tc);
    
    public void setDoubleThresh(boolean tc);
    
    public void reset();
        
    public String networkStatus();
    
}
