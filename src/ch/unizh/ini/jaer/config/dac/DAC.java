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

package ch.unizh.ini.jaer.config.dac;



/**
 * Describes a Digital to Analog Coverter (DAC).
 
 * @author tobi
 */
public class DAC {
    
    protected int numChannels;
    protected int resolutionBits;
    protected float refMinVolts;
    protected float refMaxVolts;
    protected float vdd;
    
    /** Constructs a new DAC object which represents the DAC.
     * 
     * @param numChannels number of output channels.
     * @param resolutionBits resolution of each channel in bits.
     * @param refMinVolts vref miniumum in volts (same for all channels).
     * @param refMaxVolts vref max in volts (same for all channels).
     * @param vdd the positive supply voltage (the largest voltage the DAC could output). A DAC may use an internal or external reference
     * voltage such that some counts would output a voltage larger than vdd. VPot uses this vdd to limit its displayed voltage.
     */
    public DAC(int numChannels, int resolutionBits, float refMinVolts, float refMaxVolts, float vdd){
        this.setNumChannels(numChannels);
        this.resolutionBits=resolutionBits;
        this.refMinVolts=refMinVolts;
        this.refMaxVolts=refMaxVolts;
        this.vdd=vdd;
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

    /** Gets the largest possible voltage this DAC can output.
     */
    public float getVdd() {
        return vdd;
    }

    /** Sets the largest possible voltage (the positive supply) this DAC can output */
    public void setVdd(float vdd) {
        this.vdd = vdd;
    }

    }
