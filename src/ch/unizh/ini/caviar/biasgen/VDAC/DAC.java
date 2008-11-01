/*
 * DAC.java
 *
 * Created on November 19, 2006, 9:21 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright November 19, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.biasgen.VDAC;

import ch.unizh.ini.caviar.biasgen.*;

/**
 * Describes a Digital to Analog Coverter (DAC).
 
 * @author tobi
 */
public class DAC {
    
    private int numChannels;
    private int resolutionBits;
    private float refMinVolts;
    private float refMaxVolts;
    
    
    /** Constructs a new DAC object which represents the DAC.
     * 
     * @param numChannels number of output channels
     * @param resolutionBits resolution of each channel in bits
     * @param refMinVolts vref miniumum in volts (same for all channels)
     * @param refMaxVolts vref max in volts (same for all channels)
     */
    public DAC(int numChannels, int resolutionBits, float refMinVolts, float refMaxVolts){
        this.setNumChannels(numChannels);
        this.resolutionBits=resolutionBits;
        this.refMinVolts=refMinVolts;
        this.refMaxVolts=refMaxVolts;
    }
    
//    /** write the digital value to a channel
//     @param channel, the DAC channel, 0 based
//     @param value the value, which is truncated to the resolution
//     */
//    abstract void writeValue(int channel, int value);
    
    public float getResolutionVolts(){
        return (getRefMaxVolts()-getRefMinVolts())/(1<<getResolutionBits());
    }

    public float getRefMinVolts() {
        return refMinVolts;
    }

    public void setRefMinVolts(float refMinVolts) {
        this.refMinVolts = refMinVolts;
    }

    public float getRefMaxVolts() {
        return refMaxVolts;
    }

    public void setRefMaxVolts(float refMaxVolts) {
        this.refMaxVolts = refMaxVolts;
    }

    public int getResolutionBits() {
        return resolutionBits;
    }

    public void setResolutionBits(int resolutionBits) {
        this.resolutionBits = resolutionBits;
    }

    public int getNumChannels() {
        return numChannels;
    }

    public void setNumChannels(int numChannels) {
        this.numChannels = numChannels;
    }
    
}
