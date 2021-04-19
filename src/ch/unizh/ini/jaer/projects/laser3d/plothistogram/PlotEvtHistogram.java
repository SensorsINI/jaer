/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d.plothistogram;


import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Thomas Mantel
 */
@Description("Creates a histogram of events during several periods (distinguished with special events)")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class PlotEvtHistogram extends EventFilter2D {

    Histogram histogram;
    PlotHistogram histogramPlot;
    boolean isInitialized = false;

    public PlotEvtHistogram(AEChip chip) {
        super(chip);

    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        checkOutputPacketEventType(in);
        for (Object e : in) {
            if (!isInitialized) {
                initFilter();
            }
            histogram.processEvent((PolarityEvent) e);
        }
        return in;
    }

    @Override
    public void resetFilter() {
        if (isFilterEnabled()) {
            if (histogram == null) {
                histogram = new EvtHistogram(this);
            } else {
                histogram.initHistogram();
            }
            if (histogramPlot != null) {
                histogramPlot.setHistogramPlotVisible(true);
            }
        }
    }

    @Override
    public void initFilter() {
        histogram = new EvtHistogram(this);
        histogramPlot = new PlotHistogram(histogram);
        histogramPlot.createHistogramFrame();
        isInitialized = true;
    }

    public void setHistogramFrameVisible(boolean yes) {
        if (histogramPlot != null) {
            histogramPlot.setHistogramPlotVisible(yes);
        }
    }

}
