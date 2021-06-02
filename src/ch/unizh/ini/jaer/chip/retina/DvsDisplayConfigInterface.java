/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.retina;

/**
 * Configuration interface for DVS vision sensor output rendering using the "new" texture based rendering.
 * 
 * @author Christian
 */
public interface DvsDisplayConfigInterface { 

    
    public abstract boolean isDisplayFrames();
    
    public abstract void setDisplayFrames(boolean displayFrames);
    
    public abstract boolean isDisplayEvents();
    
    public abstract void setDisplayEvents(boolean displayEvents);
    
    public abstract boolean isUseAutoContrast();
    
    public abstract void setUseAutoContrast(boolean useAutoContrast);
    
    public abstract float getContrast();
    
    public abstract void setContrast(float contrast);
    
    public abstract float getBrightness();
    
    public abstract void setBrightness(float brightness);
    
    public abstract float getGamma();
    public abstract void setGamma(float gamma);

}
