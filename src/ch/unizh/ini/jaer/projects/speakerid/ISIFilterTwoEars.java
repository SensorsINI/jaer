/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.speakerid;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.Series;
import net.sf.jaer.util.chart.XYChart;
import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent;
import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent.Ear;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent;
import java.util.logging.Logger;
import net.sf.jaer.DevelopmentStatus;

/**
 * extended ISI Filter. now splits ISIs into 2 different streams, one for each ear. resulting in 2*nBins bins.
 *
 * original from Holger (some parts are from tobi's ISIhistogrammer) see: ISIFilter
 * 
 * @author Philipp
 *
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ISIFilterTwoEars extends EventFilter2D implements Observer {

    protected static final Logger log = Logger.getLogger("ISIFilterTwoEars");
    private int nBins = getPrefs().getInt("nBins", 50);
    private int maxIsiUs = getPrefs().getInt("maxIsiUs", 10000);
    private int minIsiUs = getPrefs().getInt("minIsiUs", 0);
    private int neighborhoodRangeLower = getPrefs().getInt("neighborhoodRangeLower", 0);
    private int neighborhoodRangeUpper = getPrefs().getInt("neighborhoodRangeUpper", 0);
    private boolean useLeftEar = getPrefs().getBoolean("useLeftEar", true);
    private boolean useRightEar = getPrefs().getBoolean("useRightEar", true);
    private boolean sameChannelAndNeuron = getPrefs().getBoolean("sameChannelAndNeuron", true);
    private String useChannels = getPrefs().get("useChannels", "1-64");
    private boolean[] useChannelsBool = new boolean[64];
    private float[] leftBins = new float[nBins];
    private int maxLeftBinIndex = 0;
    private float[] rightBins = new float[nBins];
    private int maxRightBinIndex = 0;
    int nChans = 1;
    int ear;
    int[][][] lastTs = new int[64][4][2];
    JFrame isiFrame = null;
    int nextDecayTimestamp = 0, lastDecayTimestamp = 0;
    private int tauDecayMs = getPrefs().getInt("tauDecayMs", 1000);

    public ISIFilterTwoEars(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        setPropertyTooltip("Plot properties", "maxIsiUs", "maximim ISI in us, larger ISI's are discarded");
        setPropertyTooltip("Plot properties", "minIsiUs", "minimum ISI in us, smaller ISI's are discarded");
        setPropertyTooltip("Plot properties", "NBins", "number of histogram bins");
        setPropertyTooltip("Plot properties", "tauDecayMs", "histogram bins are decayed to zero with this time constant in ms");
        setPropertyTooltip("Plot properties", "logPlotEnabled", "enable to plot log histogram counts, disable for linear scale");
        setPropertyTooltip("Include spikes from ...", "useChannels", "channels to use for the histogram seperated by ; (i.e. '1-5;10-15;20-25')");
        setPropertyTooltip("Include spikes from ...", "useLeftEar", "Use the left ear");
        setPropertyTooltip("Include spikes from ...", "useRightEar", "Use the right ear");
        setPropertyTooltip("ISI to spikes in ...", "neighborhoodRangeLower", "The number of lower neighboring channels to use for ISIs");
        setPropertyTooltip("ISI to spikes in ...", "neighborhoodRangeUpper", "The number of upper neighboring channels to use for ISIs");
        setPropertyTooltip("ISI to spikes in ...", "sameChannelAndNeuron", "If ISIs should be computed to the last spike in the same neuron of the same channel");
        parseUseChannel();
        resetBins();
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        for (BasicEvent e : in) {
            try {
                BinauralCochleaEvent i = (BinauralCochleaEvent) e;
                if (i.getEar() == Ear.LEFT) {
                    ear = 0;
                    if (useLeftEar == false) {
                        break;
                    }
                } else {
                    ear = 1;
                    if (useRightEar == false) {
                        break;
                    }
                }
                CochleaAMSEvent camsevent = ((CochleaAMSEvent) e);
                int neuron = camsevent.getThreshold();
                int ts = e.timestamp;
                int ch = e.x;
                if (!useChannelsBool[ch]) {
                    continue;
                }
                int start = ch - this.neighborhoodRangeLower;
                if (start < 0) {
                    start = 0;
                }
                for (int compareChan = start; compareChan <= ch + this.neighborhoodRangeUpper && compareChan < 64; compareChan++) {
                    if (ch == compareChan) {
                        if (this.sameChannelAndNeuron) {
                            addIsi(ts - lastTs[ch][neuron][ear]);
                        }
                    } else {
                        for (int compareNeuron = 1; compareNeuron < 4; compareNeuron++) {
                            addIsi(ts - lastTs[ch][compareNeuron][ear]);
                        }
                    }
                }

                lastTs[ch][neuron][ear] = ts;
                decayHistogram(ts);
            } catch (Exception e1) {
                log.warning("In for-loop in filterPacket caught exception " + e1);
                e1.printStackTrace();
            }
        }
        isiFrame.repaint();
        return in;
    }

    synchronized public void resetBins() {
        if (leftBins.length != nBins) {
            leftBins = new float[nBins];
        }
        Arrays.fill(leftBins, 0);
        maxLeftBinIndex = 0;

        if (rightBins.length != nBins) {
            rightBins = new float[nBins];
        }
        Arrays.fill(rightBins, 0);
        maxRightBinIndex = 0;
        lastTs = new int[64][4][2];
        if (activitySeriesLeft != null) {
            activitySeriesLeft.setCapacity(nBins);
        }
        if (activitySeriesRight != null) {
            activitySeriesRight.setCapacity(nBins);
        }
        if (isiFrame != null) {
            isiFrame.repaint(0);
        }
    }

    private void addIsi(int isi) {
        if (isi < minIsiUs) {
            return;
        }
        if (isi >= maxIsiUs) {
            return;
        }
        int bin = (((isi - minIsiUs) * nBins) / (maxIsiUs - minIsiUs));
        if (ear == 0) {
            leftBins[bin]++;
            if (leftBins[bin] > leftBins[maxLeftBinIndex]) {
                maxLeftBinIndex = bin;
            }
        } else {
            rightBins[bin]++;
            if (rightBins[bin] > rightBins[maxRightBinIndex]) {
                maxRightBinIndex = bin;
            }
        }
    }

    public void doPrintBins() {
        for (float i : leftBins) {
            System.out.print(String.format("%.0f ", i));
        }
        for (float i : rightBins) {
            System.out.print(String.format("%.0f ", i));
        }
        System.out.println("");
    }

    @Override
    public void resetFilter() {
        resetBins();
    }

    @Override
    public void initFilter() {
        resetBins();
    }

    public void update(Observable o, Object arg) {

        // THIS FUNCTION CAUSES A NULL POINTER EXCEPTION WHEN CHANGING THE HARDWARE, SO I COMMENTED IT OUT
        // -Peter
//        if (arg.equals(Chip2D.EVENT_SIZEX) || arg.equals(Chip2D.EVENT_SIZEY)) {
//            resetBins();
//        }
    }

    /**
     * @return the nBins
     */
    public int getNBins() {
        return nBins;
    }

    /**
     * @param nBins the nBins to set
     */
    public void setNBins(int nBins) {
        int old = this.nBins;
        if (nBins < 1) {
            nBins = 1;
        }
        this.nBins = nBins;
        getPrefs().putInt("nBins", nBins);
        resetBins();
        getSupport().firePropertyChange("nBins", old, this.nBins);
    }

    /**
     * @return the neighborhoodRangeLower
     */
    public int getNeighborhoodRangeLower() {
        return neighborhoodRangeLower;
    }

    /**
     * @param neighborhoodRangeLower the neighborhoodRangeLower to set
     */
    public void setNeighborhoodRangeLower(int neighborhoodRangeLower) {
        int oldneighborhoodRangeLower = this.neighborhoodRangeLower;
        this.neighborhoodRangeLower = neighborhoodRangeLower;
        getPrefs().putFloat("neighborhoodRangeLower", neighborhoodRangeLower);
        getSupport().firePropertyChange("neighborhoodRangeLower", oldneighborhoodRangeLower, this.neighborhoodRangeLower);
    }

    /**
     * @return the neighborhoodRangeUpper
     */
    public int getNeighborhoodRangeUpper() {
        return neighborhoodRangeUpper;
    }

    /**
     * @param neighborhoodRangeUpper the neighborhoodRangeUpper to set
     */
    public void setNeighborhoodRangeUpper(int neighborhoodRangeUpper) {
        int oldneighborhoodRangeUpper = this.neighborhoodRangeUpper;
        this.neighborhoodRangeUpper = neighborhoodRangeUpper;
        getPrefs().putFloat("neighborhoodRangeUpper", neighborhoodRangeUpper);
        getSupport().firePropertyChange("neighborhoodRangeUpper", oldneighborhoodRangeUpper, this.neighborhoodRangeUpper);
    }

    /**
     * @return the maxIsiUs
     */
    public int getMaxIsiUs() {
        return maxIsiUs;
    }

    /**
     * @param maxIsiUs the maxIsiUs to set
     */
    synchronized public void setMaxIsiUs(int maxIsiUs) {
        int old = this.maxIsiUs;
        if (maxIsiUs < minIsiUs) {
            maxIsiUs = minIsiUs;
        }
        this.maxIsiUs = maxIsiUs;
        getPrefs().putInt("maxIsiUs", maxIsiUs);
        resetBins();
        if (binAxis != null) {
            binAxis.setUnit(String.format("%d,%d us", minIsiUs, maxIsiUs));
        }
        if (isiFrame != null) {
            isiFrame.repaint();
        }
        getSupport().firePropertyChange("maxIsiUs", old, maxIsiUs);
    }

    /**
     * @return the minIsiUs
     */
    public int getMinIsiUs() {
        return minIsiUs;
    }

    /**
     * @param minIsiUs the minIsiUs to set
     */
    public void setMinIsiUs(int minIsiUs) {
        int old = this.minIsiUs;
        if (minIsiUs > maxIsiUs) {
            minIsiUs = maxIsiUs;
        }
        this.minIsiUs = minIsiUs;
        getPrefs().putInt("minIsiUs", minIsiUs);
        resetBins();
        if (binAxis != null) {
            binAxis.setUnit(String.format("%d,%d us", minIsiUs, maxIsiUs));
        }
        if (isiFrame != null) {
            isiFrame.repaint();
        }
        getSupport().firePropertyChange("minIsiUs", old, minIsiUs);
    }

    /**
     * @return the tauDecayMs
     */
    public int getTauDecayMs() {
        return tauDecayMs;
    }

    /**
     * @param tauDecayMs the tauDecayMs to set
     */
    public void setTauDecayMs(int tauDecayMs) {
        int oldtau = this.tauDecayMs;
        this.tauDecayMs = tauDecayMs;
        getPrefs().putFloat("tauDecayMs", tauDecayMs);
        getSupport().firePropertyChange("tauDecayMs", oldtau, this.tauDecayMs);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        setIsiDisplay(yes);
    }

    @Override
    public synchronized void cleanup() {
        super.cleanup();
        setIsiDisplay(false);
    }

    public void decayHistogram(int timestamp) {
        if (timestamp > lastDecayTimestamp) {
            float decayconstant = (float) java.lang.Math.exp(-(float) (timestamp - lastDecayTimestamp) / (float) (tauDecayMs * 1000));
            for (int i = 0; i < leftBins.length; i++) {
                leftBins[i] = leftBins[i] * decayconstant;
            }
            for (int i = 0; i < rightBins.length; i++) {
                rightBins[i] = rightBins[i] * decayconstant;
            }
        }
        lastDecayTimestamp = timestamp;
    }

    private Axis binAxis;
    private XYChart chart;
    public Series activitySeriesLeft;
    private Axis activityAxisLeft;
    private Category activityCategoryLeft;
    public Series activitySeriesRight;
    private Axis activityAxisRight;
    private Category activityCategoryRight;
    private boolean logPlotEnabled = getPrefs().getBoolean("logPlotEnabled", false);

    private void setIsiDisplay(boolean yes) {
        if (!yes) {
            if (isiFrame != null) {
                isiFrame.dispose();
                isiFrame = null;
            }
        } else {
            isiFrame = new JFrame("ISIs") {

                @Override
                synchronized public void paint(Graphics g) {
                    super.paint(g);
                    try {
                        if (leftBins != null && rightBins != null) {
                            activitySeriesLeft.clear();
                            activitySeriesRight.clear();

                            //log.info("numbins="+myBins.numOfBins);
                            for (int i = 0; i < nBins; i++) {
                                if (isLogPlotEnabled()) {
                                    activitySeriesLeft.add(i, (float) Math.log(leftBins[i]));
                                    activitySeriesRight.add(i, (float) Math.log(rightBins[i]));
                                } else {
                                    activitySeriesLeft.add(i, leftBins[i]);
                                    activitySeriesRight.add(i, rightBins[i]);
                                }
                            }
                            binAxis.setMaximum(nBins);
                            binAxis.setMinimum(0);
                            if (isLogPlotEnabled()) {
                                activityAxisLeft.setMaximum((float) Math.log(leftBins[maxLeftBinIndex]));
                                activityAxisRight.setMaximum((float) Math.log(rightBins[maxRightBinIndex]));
                            } else {
                                activityAxisLeft.setMaximum(leftBins[maxLeftBinIndex]);
                                activityAxisRight.setMaximum(rightBins[maxRightBinIndex]);
                            }
                        } else {
                            log.warning("bins==null");
                        }
                    } catch (Exception e) {
                        log.warning("while displaying bins chart caught " + e);
                    }
                }
            };
            isiFrame.setPreferredSize(new Dimension(200, 100));
            Container pane = isiFrame.getContentPane();
            chart = new XYChart();
            activitySeriesLeft = new Series(2, nBins);
            activitySeriesRight = new Series(2, nBins);

            binAxis = new Axis(0, nBins);
            binAxis.setTitle("bin");
            binAxis.setUnit(String.format("%d,%d us", minIsiUs, maxIsiUs));

            activityAxisLeft = new Axis(0, 1); // will be normalized
            activityAxisLeft.setTitle("left count");

            activityAxisRight = new Axis(0, 1); // will be normalized
            activityAxisRight.setTitle("right count");

            activityCategoryLeft = new Category(activitySeriesLeft, new Axis[]{binAxis, activityAxisLeft});
            activityCategoryLeft.setColor(new float[]{0f, 1f, 0f}); // green for left
            activityCategoryLeft.setLineWidth(3f);

            activityCategoryRight = new Category(activitySeriesRight, new Axis[]{binAxis, activityAxisRight});
            activityCategoryRight.setColor(new float[]{1f, 0f, 0f}); // red for right
            activityCategoryRight.setLineWidth(3f);

            chart = new XYChart("ISIs");
            chart.setBackground(Color.black);
            chart.setForeground(Color.white);
            chart.setGridEnabled(false);
            chart.addCategory(activityCategoryLeft);
            chart.addCategory(activityCategoryRight);

            pane.setLayout(new BorderLayout());
            pane.add(chart, BorderLayout.CENTER);

            isiFrame.setVisible(yes);
        }
    }

    /**
     * @return the logPlotEnabled
     */
    public boolean isLogPlotEnabled() {
        return logPlotEnabled;
    }

    /**
     * @param logPlotEnabled the logPlotEnabled to set
     */
    public void setLogPlotEnabled(boolean logPlotEnabled) {
        this.logPlotEnabled = logPlotEnabled;
        getPrefs().putBoolean("logPlotEnabled", logPlotEnabled);
    }

    public boolean isUseLeftEar() {
        return this.useLeftEar;
    }

    public void setUseLeftEar(boolean useLeftEar) {
        getPrefs().putBoolean("useLeftEar", useLeftEar);
        this.useLeftEar = useLeftEar;
    }

    public boolean isUseRightEar() {
        return this.useRightEar;
    }

    public void setUseRightEar(boolean useRightEar) {
        getPrefs().putBoolean("useRightEar", useRightEar);
        this.useRightEar = useRightEar;
    }

    public boolean isSameChannelAndNeuron() {
        return this.sameChannelAndNeuron;
    }

    public void setSameChannelAndNeuron(boolean sameChannelAndNeuron) {
        getPrefs().putBoolean("sameChannelAndNeuron", sameChannelAndNeuron);
        this.sameChannelAndNeuron = sameChannelAndNeuron;
    }

    /**
     * @return the useChannels
     */
    public String getUseChannels() {
        return this.useChannels;
    }

    /**
     * @param useChannels the channels to use
     */
    public void setUseChannels(String useChannels) {
        getSupport().firePropertyChange("useChannels", this.useChannels, useChannels);
        this.useChannels = useChannels;
        getPrefs().put("useChannels", useChannels);
        parseUseChannel();
    }

    public float[] getLeftBins() {
        return leftBins;
    }

    public float getLeftMaxBin() {
        return leftBins[maxLeftBinIndex];
    }

    public float[] getRightBins() {
        return rightBins;
    }

    public float getRightMaxBin() {
        return rightBins[maxRightBinIndex];
    }

    private void parseUseChannel() {
        for (int i = 0; i < 64; i++) {
            useChannelsBool[i] = false;
        }
        String[] temp = useChannels.split(";");
        for (int i = 0; i < temp.length; i++) {
            String[] temp2 = temp[i].split("-");
            if (temp2.length == 1) {
                useChannelsBool[Integer.parseInt(temp2[0])] = true;
            } else if (temp2.length == 2) {
                for (int j = Integer.parseInt(temp2[0]) - 1; j < Integer.parseInt(temp2[1]); j++) {
                    useChannelsBool[j] = true;
                }
            }
        }
    }
}
