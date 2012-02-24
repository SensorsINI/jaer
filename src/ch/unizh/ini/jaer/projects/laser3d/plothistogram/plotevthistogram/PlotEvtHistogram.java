/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d.plothistogram.plotevthistogram;

import ch.unizh.ini.jaer.projects.laser3d.plothistogram.PlotHistogram;
import java.util.Observable;
import java.util.Observer;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;

/**
 *
 * @author Thomas Mantel
 */
@Description("Creates a histogram of events during several periods (distinguished with special events)")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class PlotEvtHistogram extends PlotHistogram implements Observer {

    public PlotEvtHistogram(AEChip chip) {
        super(chip);
        histogramName = "Event Histogram";
    }
    
    @Override
    public void update(Observable o, Object arg) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

//    private JFrame histogramFrame;
//    private HistGCanvas histogramCanvas;
//    private EvtHistogram evtHistogram;
//    private HistogramMouselistener histoMouselistener;
//    int lastUpdate = 0;
//
//    public PlotEvtHistogram(AEChip chip) {
//        super(chip);
////        initFilter();
//    }
//
//    @Override
//    public void update(Observable o, Object arg) {
//        throw new UnsupportedOperationException("Not supported yet.");
//    }
//
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        if (histogram == null) {
            initFilter();
        }
        if (histogramFrame == null) {
            createHistogramFrame();
        } else if (!histogramFrame.isVisible()) {
            histogramFrame.setVisible(true);
        }
                

        for (Object e : in) {
            histogram.processEvent((PolarityEvent) e);
        }
        return in;
    }
//
    @Override
    public void resetFilter() {
        if (this.isFilterEnabled()) {
            if (histogram == null) {
                histogram = new EvtHistogram();
            } else if (histogram.isInitialized()) {
                histogram.resetHistogram();
            }
            if (histogramFrame == null) {
                createHistogramFrame();
            }
        }
    }

    @Override
    public void initFilter() {
        histogram = new EvtHistogram();
    }
}






