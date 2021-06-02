/*
 * ChipControlPanel.java
 *
 * Created on Jan 20, 2006
 */

package net.sf.jaer.biasgen;


/**
 * Interface for a control panel or user-friendly interface that affects chip biases or other configuration to influence 
 * general behavior, e.g. threshold, bandwidth, refractory period, balance of complementary channels like ON/OFF.
 *This class extends Biasgen abstractly, so users can fill in methods to change these general characteristics. This class is intended for use
 *in simplified user interfaces and in feedback controllers.
 *<p>
 *This class is mostly a marker interface that only has a single method to return the control panel used in the GUI.
 *
 * @author tobi
 */
public interface ChipControlPanel   {
    
    /** @return the JPanel control panel for this functional control of biases
     */
    public javax.swing.JPanel getControlPanel();
    
//    // these are dimensionless parameters that track the changes
//    private float threshold, refractoryPeriod, bandwidth, onOffBalance;
//    
//    /**
//     *  Constructs a new biasgen. A BiasgenHardwareInterface is constructed when needed. 
//     *@see HardwareInterfaceException
//     */
//    public ChipControlPanel(Chip chip){
//        super(chip);
//    }
//    
////    public ChipControlPanel(BiasgenHardwareInterface hwInterface){
////        super(hwInterface);
////    }
//
//    /** increases neuron thresholds */
//    abstract public void increaseThreshold();
//    /** decreases neuron thresholds */
//    abstract public void decreaseThreshold();
//    
//    public void storeDefaultValues(){
//        storePreferences();
//    }
//    
//    public void revertToDefaultValues(){
//        loadPreferences();
//    }
//    
//    /** increases neuron refractory periods */
//    abstract public void increaseRefractoryPeriod();
//    
//    /** decreaes neuron refractory periods */
//    abstract public void decreaseRefractoryPeriod();
//    
//    /** increases bandwidth */
//    abstract public void increaseBandwidth();
//    
//    /* decreases bandwidth */
//    abstract public void decreaseBandwidth();
//    
//    /** more ON type (less OFF) */
//    abstract public void moreONType();
//    
//    /** more OFF type (less ON) */
//    abstract public void moreOFFType();
//    
//    /**
//     Sets relative bandwidth
//     @param val a value from 0 to 100, with default 50.
//     */
//    abstract public void setBandwidth(int val);
//
//        /**
//     Sets relative threshold
//     @param val a value from 0 to 100, with default 50.
//     */
//    abstract public void setThreshold(int val);
//
//        /**
//     Sets relative maximum firing rate (reciprocal of refractory period).
//     @param val a value from 0 to 100, with default 50.
//     */
//    abstract public void setMaximumFiringRate(int val);

    
    
}
