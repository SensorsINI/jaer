/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d.plothistogram;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 *
 * @author Thomas
 */
public class HistogramMouselistener implements MouseListener{
    private HistGCanvas histGCanvas;
    
    public HistogramMouselistener(HistGCanvas canvas) {
        this.histGCanvas = canvas;
    }
    
    @Override
    public void mouseClicked(MouseEvent e) {
        histGCanvas.repaint();
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
    
}