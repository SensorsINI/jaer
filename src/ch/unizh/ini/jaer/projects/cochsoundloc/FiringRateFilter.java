/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
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

/**
 *  ???? what does it do? TODO
 * @author Holger
 *
 */
public class FiringRateFilter extends EventFilter2D implements Observer {

    private boolean normalizePlot = getPrefs().getBoolean("FiringRateFilter.normalizePlot", true);
    private boolean useLeftEar = getPrefs().getBoolean("FiringRateFilter.useLeftEar", true);
    private boolean useRightEar = getPrefs().getBoolean("FiringRateFilter.useRightEar", true);
    private boolean useNeuron1 = getPrefs().getBoolean("FiringRateFilter.useNeuron1", true);
    private boolean useNeuron2 = getPrefs().getBoolean("FiringRateFilter.useNeuron2", true);
    private boolean useNeuron3 = getPrefs().getBoolean("FiringRateFilter.useNeuron3", true);
    private boolean useNeuron4 = getPrefs().getBoolean("FiringRateFilter.useNeuron4", true);
    private boolean sumAllNeurons = getPrefs().getBoolean("FiringRateFilter.sumAllNeurons", false);
    private boolean sumAllEars = getPrefs().getBoolean("FiringRateFilter.sumAllEars", false);
    private boolean showIID = getPrefs().getBoolean("FiringRateFilter.showIID", true);
    private float fractionNN = getPrefs().getFloat("FiringRateFilter.fractionNN", 0.1f);
    private float fractionNNN = getPrefs().getFloat("FiringRateFilter.fractionNNN", 0.05f);
    private String useChannels = getPrefs().get("ISIFilter.useChannels", "1-64");
    private boolean[] useChannelsBool = new boolean[64];
    private float[][][] channelRates = new float[64][4][2];
    JFrame rateFrame = null;
    int nextDecayTimestamp = 0, lastDecayTimestamp = 0;
    private int tauDecayMs = getPrefs().getInt("FiringRateFilter.tauDecayMs", 1000);
    float[][] colors = new float[3][8];
    public Series[] activitySeries;
    public Series IIDSeries;
    private Axis channelAxis;
    private Axis activityAxis;
    private Axis IIDAxis;
    private Category[] activityCategory;
    private Category IIDCategory;
    private XYChart chart;
    private float lastMaxActivity;
    private float lastMaxIID;

    public FiringRateFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        for (int k = 0; k < 8; k++) {
            colors[0][k] = 0.5f + 0.5f * ((k / 4) % 2); // 0 0 0 0 1 1 1 1
            colors[1][k] = 0.5f + 0.5f * ((k / 2) % 2); // 0 0 1 1 0 0 1 1
            colors[2][k] = 0.5f + 0.5f * ((k / 1) % 2); // 0 1 0 1 0 1 0 1
        }
        setPropertyTooltip("Plot properties", "normalizePlot", "Normalizes the activities before plotting.");
        setPropertyTooltip("Plot properties", "tauDecayMs", "histogram bins are decayed to zero with this time constant in ms");
        setPropertyTooltip("Include spikes from ...", "useLeftEar", "Use the left ear");
        setPropertyTooltip("Include spikes from ...", "useRightEar", "Use the right ear");
        setPropertyTooltip("Include spikes from ...", "useNeuron1", "Use neuron 1");
        setPropertyTooltip("Include spikes from ...", "useNeuron2", "Use neuron 2");
        setPropertyTooltip("Include spikes from ...", "useNeuron3", "Use neuron 3");
        setPropertyTooltip("Include spikes from ...", "useNeuron4", "Use neuron 4");
        setPropertyTooltip("Include spikes from ...", "useChannels", "channels to use for the histogram seperated by ; (i.e. '1-5;10-15;20-25')");
        setPropertyTooltip("Local Suppression ...", "fractionNN", "How much the next neighbour suppresses the channel.");
        setPropertyTooltip("Local Suppression ...", "fractionNNN", "How much the second next neighbour suppresses the channel.");
        parseUseChannel();
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        for (BasicEvent e : in) {
            try {
                BinauralCochleaEvent i = (BinauralCochleaEvent) e;
                int ear;
                if (i.getEar() == Ear.LEFT) {
                    ear = 0;
                } else {
                    ear = 1;
                }
                CochleaAMSEvent camsevent = ((CochleaAMSEvent) e);
                int neuron = camsevent.getThreshold();
                int ts = e.timestamp;
                int ch = e.x;
                if (!useChannelsBool[ch]) {
                    continue;
                }
                channelRates[ch][neuron][ear]++;
                decayHistogram(ts);
            } catch (Exception e1) {
                log.warning("In for-loop in filterPacket caught exception " + e1);
                e1.printStackTrace();
            }
        }
        rateFrame.repaint();
        return in;
    }



    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public void update(Observable o, Object arg) {
        if (arg.equals(Chip2D.EVENT_SIZEX) || arg.equals(Chip2D.EVENT_SIZEY)) {
        }
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
        getPrefs().putFloat("FiringRateFilter.tauDecayMs", tauDecayMs);
        getSupport().firePropertyChange("tauDecayMs", oldtau, this.tauDecayMs);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        setDisplay(yes);
    }

    @Override
    public synchronized void cleanup() {
        super.cleanup();
        setDisplay(false);
    }

    public void decayHistogram(int timestamp) {
        if (timestamp > lastDecayTimestamp) {
            float decayconstant = (float) java.lang.Math.exp(-(float) (timestamp - lastDecayTimestamp) / (float) (tauDecayMs * 1000));
            for (int ch = 0; ch < 64; ch++) {
                for (int neuron = 0; neuron < 4; neuron++) {
                    for (int ear = 0; ear < 2; ear++) {
                        channelRates[ch][neuron][ear] *= decayconstant;
                    }
                }
            }
        }
        lastDecayTimestamp = timestamp;
    }

    private void setDisplay(boolean yes) {
        if (!yes) {
            if (rateFrame != null) {
                rateFrame.dispose();
                rateFrame = null;
            }
        } else {
            rateFrame = new JFrame("FiringRates") {

                @Override
                synchronized public void paint(Graphics g) {
                    super.paint(g);
                    try {
                        activitySeries[0].clear();
                        activitySeries[1].clear();
                        float maxActivity = 0f;
                        float maxIID = 0f;
                        float[][] sums = new float[8][64];
                        for (int ch = 0; ch < 64; ch++) {
                            float[] IID = new float[2];
                            for (int ear = 0; ear < 2; ear++) {
                                if (ear == 0 && !useLeftEar) {
                                    continue;
                                }
                                if (ear == 1 && !useRightEar) {
                                    continue;
                                }
                                for (int neuron = 0; neuron < 4; neuron++) {
                                    if (neuron == 0 && !useNeuron1) {
                                        continue;
                                    }
                                    if (neuron == 1 && !useNeuron2) {
                                        continue;
                                    }
                                    if (neuron == 2 && !useNeuron3) {
                                        continue;
                                    }
                                    if (neuron == 3 && !useNeuron4) {
                                        continue;
                                    }
                                    IID[ear] += channelRates[ch][neuron][ear];
                                    if (sumAllEars) {
                                        if (sumAllNeurons) {
                                            sums[0][ch] += channelRates[ch][neuron][ear];
                                        }
                                        else {
                                            sums[neuron][ch] += channelRates[ch][neuron][ear];
                                        }
                                    }
                                    else
                                    {
                                        if (sumAllNeurons) {
                                            sums[ear][ch] += channelRates[ch][neuron][ear];
                                        }
                                        else {
                                            sums[2*neuron+ear][ch] += channelRates[ch][neuron][ear];
                                        }
                                    }
                                }
                            }
                            
                            if (showIID) {
                                float IIDdiff = IID[1]-IID[0];
                                IIDSeries.add(ch, IIDdiff);
                                if (maxIID < java.lang.Math.abs(IIDdiff)) {
                                    maxIID = java.lang.Math.abs(IIDdiff);
                                }
                            }
                        }

                        for (int ch=0;ch<64;ch++) {
                            for (int k=0;k<8;k++) {
                                float temp = sums[k][ch];
                                if (ch>0) {
                                    temp -= fractionNN*sums[k][ch-1];
                                    if (ch>1) {
                                        temp -= fractionNNN*sums[k][ch-2];
                                    }
                                }
                                if (ch<63) {
                                    temp -= fractionNN*sums[k][ch+1];
                                    if (ch<62) {
                                        temp -= fractionNNN*sums[k][ch+2];
                                    }
                                }
                                if (temp<0) {
                                    temp=0;
                                }
                                activitySeries[k].add(ch, temp );
                                if (maxActivity < sums[k][ch]) {
                                    maxActivity = sums[k][ch];
                                }
                            }
                        }

                        channelAxis.setMaximum(63);
                        channelAxis.setMinimum(0);
                        if (normalizePlot) {
                            activityAxis.setMaximum(maxActivity);
                            IIDAxis.setMaximum(maxIID);
                            IIDAxis.setMinimum(-maxIID);
                        }
                        else {
                            if (maxActivity > lastMaxActivity) {
                                lastMaxActivity = maxActivity;
                            }
                            if (maxIID > lastMaxIID) {
                                lastMaxIID = maxIID;
                            }
                            activityAxis.setMaximum(lastMaxActivity);
                            IIDAxis.setMaximum(lastMaxIID);
                            IIDAxis.setMinimum(-lastMaxIID);
                        }
                    } catch (Exception e) {
                        log.warning("while displaying chart caught " + e);
                    }
                }
            };
            rateFrame.setPreferredSize(new Dimension(200, 100));
            Container pane = rateFrame.getContentPane();
            chart = new XYChart();
            activitySeries = new Series[8];
            for (int k = 0; k < 8; k++) {
                activitySeries[k] = new Series(2, 64);
            }
            
            IIDSeries = new Series(2, 64);

            channelAxis = new Axis(0, 63);
            channelAxis.setTitle("channel");

            activityAxis = new Axis(0, 1);
            activityAxis.setTitle("count");
            IIDAxis = new Axis(-1, 1);
            IIDAxis.setTitle("count");

            activityCategory = new Category[8];

            for (int k = 0; k < 8; k++) {
                activityCategory[k] = new Category(activitySeries[k], new Axis[]{channelAxis, activityAxis});
                activityCategory[k].setColor(new float[]{colors[0][k], colors[1][k], colors[2][k]});
                activityCategory[k].setLineWidth(1f);
            }
            
            IIDCategory = new Category(IIDSeries, new Axis[]{channelAxis, IIDAxis});
            IIDCategory.setColor(new float[]{1f, 1f, 1f});
            IIDCategory.setLineWidth(3f);
            
            chart = new XYChart("FiringRates");
            chart.setBackground(Color.black);
            chart.setForeground(Color.white);
            chart.setGridEnabled(false);
            for (int k = 0; k < 8; k++) {
                chart.addCategory(activityCategory[k]);
            }
            chart.addCategory(IIDCategory);
            
            pane.setLayout(new BorderLayout());
            pane.add(chart, BorderLayout.CENTER);

            rateFrame.setVisible(yes);
        }

    }

    public void setNormalizePlot(boolean normalizePlot) {
        getPrefs().putBoolean("FiringRateFilter.normalizePlot", normalizePlot);
        this.normalizePlot = normalizePlot;
        lastMaxActivity = 0;
        lastMaxIID = 0;
    }

    public boolean isNormalizePlot() {
        return this.normalizePlot;
    }

    public void setUseLeftEar(boolean useLeftEar) {
        getPrefs().putBoolean("FiringRateFilter.useLeftEar", useLeftEar);
        this.useLeftEar = useLeftEar;
    }

    public boolean isUseLeftEar() {
        return this.useLeftEar;
    }

    public void setUseRightEar(boolean useRightEar) {
        getPrefs().putBoolean("FiringRateFilter.useRightEar", useRightEar);
        this.useRightEar = useRightEar;
    }

    public boolean isUseRightEar() {
        return this.useRightEar;
    }

    public void setUseNeuron1(boolean useNeuron1) {
        getPrefs().putBoolean("FiringRateFilter.useNeuron1", useNeuron1);
        this.useNeuron1 = useNeuron1;
    }

    public boolean isUseNeuron1() {
        return this.useNeuron1;
    }

    public void setUseNeuron2(boolean useNeuron2) {
        getPrefs().putBoolean("FiringRateFilter.useNeuron2", useNeuron2);
        this.useNeuron2 = useNeuron2;
    }

    public boolean isUseNeuron2() {
        return this.useNeuron2;
    }

    public void setUseNeuron3(boolean useNeuron3) {
        getPrefs().putBoolean("FiringRateFilter.useNeuron3", useNeuron3);
        this.useNeuron3 = useNeuron3;
    }

    public boolean isUseNeuron3() {
        return this.useNeuron3;
    }

    public void setUseNeuron4(boolean useNeuron4) {
        getPrefs().putBoolean("FiringRateFilter.useNeuron4", useNeuron4);
        this.useNeuron4 = useNeuron4;
    }

    public boolean isUseNeuron4() {
        return this.useNeuron4;
    }

    public void setSumAllNeurons(boolean sumAllNeurons) {
        getPrefs().putBoolean("FiringRateFilter.sumAllNeurons", sumAllNeurons);
        this.sumAllNeurons = sumAllNeurons;
    }

    public boolean isSumAllNeurons() {
        return this.sumAllNeurons;
    }

    public void setSumAllEars(boolean sumAllEars) {
        getPrefs().putBoolean("FiringRateFilter.sumAllEars", sumAllEars);
        this.sumAllEars = sumAllEars;
    }

    public boolean isSumAllEars() {
        return this.sumAllEars;
    }

    public void setShowIID(boolean showIID) {
        getPrefs().putBoolean("FiringRateFilter.showIID", showIID);
        this.showIID = showIID;
    }

    public boolean isShowIID() {
        return this.showIID;
    }

    public float getFractionNN() {
        return this.fractionNN;
    }

    public void setFractionNN(float fractionNN) {
        getPrefs().putDouble("ITDFilter.fractionNN", fractionNN);
        getSupport().firePropertyChange("fractionNN", this.fractionNN, fractionNN);
        this.fractionNN = fractionNN;
    }

    public float getFractionNNN() {
        return this.fractionNNN;
    }

    public void setFractionNNN(float fractionNNN) {
        getPrefs().putDouble("ITDFilter.fractionNNN", fractionNNN);
        getSupport().firePropertyChange("fractionNNN", this.fractionNNN, fractionNNN);
        this.fractionNNN = fractionNNN;
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
        getPrefs().put("ISIFilter.useChannels", useChannels);
        parseUseChannel();
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
