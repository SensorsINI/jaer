/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.awt.Component;

/**
 * A class implementing this interface has some means to display its state to the screen.
 * 
 * Generally, a GUI class with either implement this class or contain object(s) 
 * of this class as fields.
 * 
 * @TODO: delete - this class is obselete.
 * @author Peter
 */
public interface DisplayWriter {    
    
    /** Give the object a panel to display to */
    void setPanel(javax.swing.JPanel imagePanel);
    
    /** Get the panel that the object is displaying to */
    Component getPanel();
    
    /** Enable/disable the display */
    public void setDisplayEnabled(boolean state);
    
    
}
