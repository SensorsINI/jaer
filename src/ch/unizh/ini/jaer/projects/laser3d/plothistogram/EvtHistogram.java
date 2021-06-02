/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d.plothistogram;

import ch.unizh.ini.jaer.projects.laser3d.HistogramData;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Thomas Mantel
 */
public class EvtHistogram implements Histogram {

    private int BUFFER_SIZE = 1000;
    private int BIN_SIZE = 20; // us
    private int DEFAULT_TIMESTAMP = 0; //Integer.MIN_VALUE;
    private int triggerPeriod = 0;
    private int lastTriggerTimestamp = DEFAULT_TIMESTAMP;
    boolean initialized = false;
    private int nBins;
    private float[] xData;
    private HistogramData[] data;
    private int xLowerbound = 0;
    private int xUpperbound = 127;
    private int yLowerbound = 0;
    private int yUpperbound = 127;
    EventFilter2D filter;
    private String histogramName;

    public EvtHistogram(EventFilter2D filter) {
        this.initialized = false;
        this.filter = filter;
        histogramName = "Event Histogram";
    }

    public void setup(int periodWindowLength, int binSize, int xLowerbound, int xUpperbound, int yLowerbound, int yUpperbound) {
        if (this.initialized == false) {
            if ((periodWindowLength > 0) & (periodWindowLength < 10000)) {
                this.BUFFER_SIZE = periodWindowLength;
            } else {
                EventFilter.log.warning("EvtHistogram.setup(): invalid periodWindowLength!");
            }
            if ((binSize > 0) & (binSize < 10000)) {
                this.BIN_SIZE = binSize;
            } else {
                EventFilter.log.warning("EvtHistogram.setup(): invalid binSize!");
            }

            if (xLowerbound <= xUpperbound) {
                this.xLowerbound = xLowerbound;
                this.xUpperbound = xUpperbound;
            } else {
                EventFilter.log.warning("EvtHistogram.setup(): xUpperbound must be greater or equal xLowerbound!");
            }

            if (yLowerbound <= yUpperbound) {
                this.yLowerbound = yLowerbound;
                this.yUpperbound = yUpperbound;
            } else {
                EventFilter.log.warning("EvtHistogram.setup(): yUpperbound must be greater or equal yLowerbound!");
            }
        } else {
            EventFilter.log.warning("EvtHistogram.setup(): Histogram is already initialized, can not change settings!");
        }

    }

    @Override
    public void processEvent(PolarityEvent ev) {
        if (!initialized) {
            if(ev.isSpecial()) {
                // if new and last laserPeriod do not differ too much from each other
                if (Math.abs(triggerPeriod - (ev.timestamp - lastTriggerTimestamp)) < 10) {
                    // Init filter
                    triggerPeriod = ev.timestamp - lastTriggerTimestamp;
                    initHistogram();
                } else if (lastTriggerTimestamp > 0) {
                    triggerPeriod = ev.timestamp - lastTriggerTimestamp;
                }
                lastTriggerTimestamp = ev.timestamp;
            }
        }
        if (initialized) {
            if (ev.isSpecial()) {
                updateBins();

                // save timestamp
                lastTriggerTimestamp = ev.timestamp;
            } else {
                // check if in region of interest
                if((ev.x >= xLowerbound) & (ev.x <= xUpperbound) & (ev.y >= yLowerbound) & (ev.y <= yUpperbound)) {
                    // add to histogram
                    if (ev.polarity == Polarity.On) {
                        data[0].addToData(ev.timestamp-lastTriggerTimestamp);
                    } else if (ev.polarity == Polarity.Off) {
                        data[1].addToData(ev.timestamp-lastTriggerTimestamp);
                    }
                }
            }
        }

    }

    @Override
    public void initHistogram() {
        nBins = (triggerPeriod / BIN_SIZE) + 1;
        xData = new float[nBins];
        for (int i = 0; i < nBins; i++) {
            xData[i] = i * BIN_SIZE;
        }

        data = new HistogramData[2];
        data[0] = new HistogramData(BUFFER_SIZE,BIN_SIZE,nBins(),filter);
        data[1] = new HistogramData(BUFFER_SIZE,BIN_SIZE,nBins(),filter);

        resetHistogram();
        initialized = true;
    }

    @Override
    public void resetHistogram() {
        data[0].resetHistData();
        data[1].resetHistData();
    }

    @Override
    public float[] XData() {
        return xData;
    }

    @Override
    public float[][] YData() {
        float[][] yData = new float[2][BUFFER_SIZE];
        yData[0] = data[0].getData(yData[0]);
        yData[1] = data[1].getData(yData[1]);
        return yData;
    }

    @Override
    public float maxYVal() {
        if(data[0].getMaxVal() > data[1].getMaxVal()) {
            return data[0].getMaxVal();
        } else {
            return data[1].getMaxVal();
        }

    }

    @Override
    public int nBins() {
        return nBins;
    }

    public float onSum() {
        return data[0].getSumOfBins();
    }

    public float offSum() {
        return data[1].getSumOfBins();
    }

    private void updateBins() {
        data[0].updateBins();
        data[1].updateBins();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public String getHistogramName() {
        return histogramName;
    }
}
