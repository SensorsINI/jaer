/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d.plothistogram;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JFrame;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Thomas
 */
public class PlotHistogram extends EventFilter2D {

    public String histogramName = "Histogram";
    
    public JFrame histogramFrame;
    public HistGCanvas histogramCanvas;
    public HistogramMouselistener histoMouselistener; 
    public Histogram histogram;
    
    public PlotHistogram(AEChip chip) {
        super(chip);
    }

    public void createHistogramFrame() {        
        histogramFrame = new JFrame(histogramName);
        histogramFrame.setPreferredSize(new Dimension(820, 620));
        histogramFrame.getContentPane().setBackground(Color.white);

        histogramCanvas = new HistGCanvas(histogram);
        histogramFrame.getContentPane().add(histogramCanvas);
        histoMouselistener = new HistogramMouselistener(histogramCanvas);
        histogramFrame.addMouseListener(histoMouselistener);
        histogramCanvas.addMouseListener(histoMouselistener);
        histogramFrame.setVisible(true);
    }    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void initFilter() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
