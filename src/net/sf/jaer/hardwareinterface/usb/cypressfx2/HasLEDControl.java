/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx2;

/**
 * Interface for devices that can control one or more LEDs (Light emitting diodes)
 * 
 * @author tobi
 */
public interface HasLEDControl {
    
    public enum LEDState {OFF,ON,FLASHING,UNKNOWN}
    
    /** Returns number of LEDs 
     * 
     * @return number of LEDs. 0 based index. 
     */
    public int getNumLEDs();
    
    /** Sets the LED state.
     * 
     * @param led the LED number, 0 based
     * @param state the state
     */
    public void setLEDState(int led, LEDState state);
    
    /** Returns the LED state.
     * 
     * @param led the LED number, 0 based
     * @return the state
     */
    public LEDState getLEDState(int led);
    
}
