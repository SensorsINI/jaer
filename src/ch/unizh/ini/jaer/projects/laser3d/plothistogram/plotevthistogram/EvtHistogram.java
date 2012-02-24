/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d.plothistogram.plotevthistogram;

import ch.unizh.ini.jaer.projects.laser3d.plothistogram.Histogram;
import java.util.Arrays;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 *
 * @author Thomas
 */
public class EvtHistogram implements Histogram {

    private int BUFFER_SIZE = 1000;
    private int BIN_SIZE = 50; // us
    private int DEFAULT_TIMESTAMP = 0; //Integer.MIN_VALUE;
    private int triggerPeriod = 0;
    private int lastTriggerTimestamp = DEFAULT_TIMESTAMP;
    boolean initialized = false;
    private int nBins;
    private double[] xData;
    private double[][] yData;
    private double maxYVal;
    private int[][] onBins;
    private int[][] offBins;
    private int binIndx = 0;
    private int xLowerbound = 55;
    private int xUpperbound = 75;
    private int yLowerbound = 64;
    private int yUpperbound = 64;

    public EvtHistogram() {
        this.initialized = false;
    }

    @Override
    public void processEvent(PolarityEvent ev) {
        if (ev.special) {
            if (lastTriggerTimestamp != DEFAULT_TIMESTAMP & triggerPeriod == 0) {
                triggerPeriod = ev.timestamp - lastTriggerTimestamp;
                if (triggerPeriod < 0) {
                    triggerPeriod = 0;
                }
            }
            lastTriggerTimestamp = ev.timestamp;

            if (initialized) {
                updateBins();
            }
        } else {
            if (triggerPeriod > 0 & !initialized) { // initialize this instance as soon as a tiggerPeriod is registered
                initHistogram();
            }
            if (initialized) {
                if (ev.x >= xLowerbound & ev.x <= xUpperbound & ev.y >= yLowerbound & ev.y <= yUpperbound) {
                    int curBin = (ev.timestamp - lastTriggerTimestamp) / BIN_SIZE;
                    if (curBin >= nBins) {
                        curBin = nBins - 1;
                    }
                    if (ev.polarity == Polarity.On) {
                        onBins[curBin][0]++;

                    }
                    if (ev.polarity == Polarity.Off) {
                        offBins[curBin][0]++;

                    }
                }
            }
        }

    }

    @Override
    public void initHistogram() {
        nBins = (triggerPeriod / BIN_SIZE) +1 ;
        xData = new double[nBins];
        yData = new double[2][nBins];

        onBins = new int[nBins][BUFFER_SIZE];
        offBins = new int[nBins][BUFFER_SIZE];

        resetHistogram();
        initialized = true;
    }

    @Override
    public void resetHistogram() {
        binIndx = 0;
        Arrays.fill(yData[0], 0d);
        Arrays.fill(yData[1], 0d);


        for (int i = 0; i < nBins; i++) {
            Arrays.fill(onBins[i], 0);
            Arrays.fill(offBins[i], 0);
            xData[i] = i * BIN_SIZE;
        }

    }

    @Override
    public double[] XData() {
        return xData;
    }

    @Override
    public double[][] YData() {
        return yData;
    }

    @Override
    public double maxYVal() {
        return maxYVal;
    }

    @Override
    public int nBins() {
        return nBins;
    }

    private void updateBins() {
        maxYVal = 0.0;
        if (binIndx < BUFFER_SIZE - 1) {
            binIndx++;
        }
        for (int i = 0; i < nBins; i++) {

            // Add new events to data
            yData[0][i] += (double) onBins[i][0];
            yData[1][i] += (double) offBins[i][0];

            // subtract events that are removed from buffer
            if (binIndx == BUFFER_SIZE - 1) {
                yData[0][i] -= (double) onBins[i][BUFFER_SIZE - 1];
                yData[1][i] -= (double) offBins[i][BUFFER_SIZE - 1];
            }

            // rewrite buffer (shift everything) e.g. [1 3 5 7] -> [1 1 3 5]
            for (int j = binIndx; j > 0; j--) {
                onBins[i][j] = onBins[i][j - 1];
                offBins[i][j] = offBins[i][j - 1];
            }



            // set newest buffer items to 0      e.g. [1 1 3 5] -> [0 1 3 5]
            onBins[i][0] = 0;
            offBins[i][0] = 0;



            if (maxYVal < yData[0][i]) {
                maxYVal = yData[0][i];
            }
            if (maxYVal < yData[1][i]) {
                maxYVal = yData[1][i];
            }
        }
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}
