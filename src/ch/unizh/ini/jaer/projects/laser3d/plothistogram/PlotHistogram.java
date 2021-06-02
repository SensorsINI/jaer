/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d.plothistogram;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JFrame;

/**
 *
 * @author Thomas
 */
public class PlotHistogram {
    
    String histogramName;
    JFrame histogramFrame;
    HistGCanvas histogramCanvas;
    HistogramMouselistener histoMouselistener;
    Histogram histogram;
    
    public PlotHistogram(Histogram histogram) {
        if (histogram != null) {
            this.histogram = histogram;
            histogramName = histogram.getHistogramName();
        }            
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
    
    public void setHistogramPlotVisible(boolean yes) {
        if (histogramFrame != null) {
            histogramFrame.setVisible(yes);
        }
    }
        
}
