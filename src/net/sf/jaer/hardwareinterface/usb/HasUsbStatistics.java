/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.usb;

/**
 * Indicates that class can collect and show USB statistics
 * @author tobi
 */
public interface HasUsbStatistics {
    
    /** Shows statistics graphically
     * 
     * @param yes 
     */
    public void setShowUsbStatistics(boolean yes);
    
    /** Prints statistics to System.out 
     * 
     * @param yes 
     */
    public void setPrintUsbStatistics(boolean yes);
    
    public boolean isShowUsbStatistics();
    public boolean isPrintUsbStatistics();
}
