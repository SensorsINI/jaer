/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.awt.event.ActionEvent;
import javax.swing.Timer;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeSupport;

/**
 *
 * @author Bjoern
 */
public class PaintableObject implements ActionListener {
    private int origX, origY;
    private boolean Flash = false;
    private int FlashFreqHz = 20;
    
    private Timer timer;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
    public void startFlashing(){
        if(timer!=null)stopFlashing();
        timer = new Timer(1000/FlashFreqHz,this);
        timer.start(); 
    }
    public void stopFlashing() {
        if(timer!=null) {
            timer.stop();
            timer = null;
        }
    }
    @Override public void actionPerformed(ActionEvent e) {
        this.pcs.firePropertyChange("repaint", null, null);
        Flash = !Flash;
    }

    public int getOrigX() {
        return origX;
    }

    public void setOrigX(int origX) {
        this.origX = origX;
        this.pcs.firePropertyChange("repaint", null, null);
    }

    public int getOrigY() {
        return origY;
    }

    public void setOrigY(int origY) {
        this.origY = origY;
        this.pcs.firePropertyChange("repaint", null, null);
    }

    public int getFlashFreqHz() {
        return FlashFreqHz;
    }

    public void setFlashFreqHz(int FlashFreqHz) {
        this.FlashFreqHz = FlashFreqHz;
    }
    
    
    
}
