/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface;

import javax.swing.JDialog;

import net.sf.jaer.chip.AEChip;

/**
 * A HardwareInterfaceFactory that offers a chooser to allow selection of a desired hardware interface 
 * should implement this interface to allow a GUI (e.g. AEViewer) to construct
 * a chooser to make the interface.
 * @author tobi
 */
public interface HardwareInterfaceChooserFactory extends HardwareInterfaceFactoryInterface {
    
    /** Returns a chooser component.
     * 
     * @param chip  AEChip object being chosen for.
     * @return the chooser dialog.
     */
    public JDialog getInterfaceChooser(AEChip chip);
    
    /** Returns the chosen HardwareInterface from the chooser.
     * 
     * @return the HardwareInterface 
     */
    public HardwareInterface getChosenHardwareInterface();
    
}
