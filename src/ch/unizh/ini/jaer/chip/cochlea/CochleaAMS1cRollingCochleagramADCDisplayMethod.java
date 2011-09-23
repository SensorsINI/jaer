/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples.ChannelBuffer;
import ch.unizh.ini.jaer.chip.dvs320.*;
import ch.unizh.ini.jaer.chip.util.externaladc.ADCHardwareInterface;
import ch.unizh.ini.jaer.chip.util.externaladc.ADCHardwareInterfaceProxy;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.prefs.Preferences;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLJPanel;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.util.chart.*;
import net.sf.jaer.util.chart.XYChart;

/**
 * Displays data from CochleaAMS1c using RollingCochleaGramDisplayMethod with additional rolling strip chart of ADC samples.
 * @author Tobi
 */
public class CochleaAMS1cRollingCochleagramADCDisplayMethod extends RollingCochleaGramDisplayMethod {

    private static final Preferences prefs = Preferences.userNodeForPackage(CochleaAMS1cRollingCochleagramADCDisplayMethod.class);
    private CochleaAMS1c chip = null;
    boolean registeredChart = false;
    private Series[] activitySeries;
    private Axis timeAxis;
    private Axis activityAxis;
    private Category[] activityCategories;
    private XYChart activityChart;
    /** Max number of ADC samples to display for each ADC channel */
    public static final int NUM_ACTIVITY_SAMPLES = 50000;
    private Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
    GLJPanel activityPan;
    private final int NCHAN = 4;
    private CochleaAMS1cRollingCochleagramADCDisplayMethodGainGUI[] gainGuis = new CochleaAMS1cRollingCochleagramADCDisplayMethodGainGUI[NCHAN];
    DisplayControl[] displayControl = new DisplayControl[NCHAN];

    public CochleaAMS1cRollingCochleagramADCDisplayMethod(CochleaAMS1c chip) {
        super(chip.getCanvas());
        this.chip = chip;
        for (int i = 0; i < NCHAN; i++) {
            displayControl[i] = new DisplayControl(i);
            gainGuis[i] = new CochleaAMS1cRollingCochleagramADCDisplayMethodGainGUI(this, displayControl[i]);
        }
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        checkADCDisplay();
        super.display(drawable);
        ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples data = chip.getAdcSamples();
        boolean isScannerRunning = data.isSyncDetected();
        data.swapBuffers();
        int chan = 0;
        for (ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples.ChannelBuffer cb : data.currentReadingDataBuffer.channelBuffers) {
            if (isHidden(chan)) {
                activitySeries[chan].clear();
                continue;
            } // TODO does nothing now because clear() doesn't work
            int n = cb.size(); // must find size here since array contains junk outside the count
            int g = getGain(chan);
            int o = getOffset(chan);
            if (isScannerRunning) {
                if (activitySeries[chan].getCapacity() != CochleaAMS1cADCSamples.SCAN_LENGTH) {
                    activitySeries[chan].setCapacity(CochleaAMS1cADCSamples.SCAN_LENGTH);
                }
                activitySeries[chan].clear();
            } else {
                if (activitySeries[chan].getCapacity() != NUM_ACTIVITY_SAMPLES) {
                    activitySeries[chan].setCapacity(NUM_ACTIVITY_SAMPLES);
                }
            }

            for (int i = 0; i < n; i++) {
                ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples.ADCSample s = cb.samples[i];
                if (!isScannerRunning) {
                    activitySeries[chan].add(s.time, (s.data + o) * g);
                } else {
                    activitySeries[chan].add(i, (s.data + o) * g);
                }
            }
            chan++;
        }
        if (!isScannerRunning) {
            timeAxis.setMinimum(getStartTimeUs());
            timeAxis.setMaximum(getStartTimeUs() + getTimeWidthUs());
        } else { // scanner sync
            timeAxis.setMinimum(0);
            timeAxis.setMaximum(chip.getScanner().getMaxScanX());

        }
        activityAxis.setMinimum(0);
        activityAxis.setMaximum(CochleaAMS1cADCSamples.MAX_ADC_VALUE);
        try {
            activityPan.display();
        } catch (Exception e) {
            log.warning("while displaying activity chart caught " + e);
        }
    }

    private void checkADCDisplay() {
        if (registeredChart) {
            return;
        }
        registerChart();
    }

    public void registerChart() {
        try {
            AEChip chip = (AEChip) getChipCanvas().getChip();
            AEViewer viewer = chip.getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();

            timeAxis = new Axis(0, 1000000);
            timeAxis.setTitle("time");
            timeAxis.setUnit("us");

            activityAxis = new Axis(0, CochleaAMS1cADCSamples.MAX_ADC_VALUE);
            activityAxis.setTitle("sample");
            activitySeries = new Series[CochleaAMS1cADCSamples.NUM_CHANNELS];
            activityCategories = new Category[CochleaAMS1cADCSamples.NUM_CHANNELS];
            for (int i = 0; i < CochleaAMS1cADCSamples.NUM_CHANNELS; i++) {
                activitySeries[i] = new Series(2, NUM_ACTIVITY_SAMPLES);
                activityCategories[i] = new Category(activitySeries[i], new Axis[]{timeAxis, activityAxis});
                activityCategories[i].setColor(colors[i].getRGBColorComponents(null));
            }
            //            activityAxis.setUnit("events");


            activityChart = new XYChart("ADC Samples");
            activityChart.setPreferredSize(new Dimension(600, 200));
            activityChart.setBackground(Color.black);
            activityChart.setForeground(Color.white);
            activityChart.setGridEnabled(false);

            for (int i = 0; i < CochleaAMS1cADCSamples.NUM_CHANNELS; i++) {
                activityChart.addCategory(activityCategories[i]);
            }
            activityPan = new GLJPanel(); // holds chart and controls
            activityPan.setLayout(new BorderLayout());
            activityPan.setBackground(Color.black);
            activityPan.add(activityChart, BorderLayout.CENTER);
            JPanel gainPan = new JPanel();
            gainPan.setLayout(new BoxLayout(gainPan, BoxLayout.X_AXIS));
            for (int i = 0; i < NCHAN; i++) {
                CochleaAMS1cRollingCochleagramADCDisplayMethodGainGUI gaingui = new CochleaAMS1cRollingCochleagramADCDisplayMethodGainGUI(this, displayControl[i]);
                gainPan.add(gaingui);
            }
            activityPan.add(gainPan, BorderLayout.SOUTH);
            activityPan.validate();
            imagePanel.add(activityPan, BorderLayout.SOUTH);
            imagePanel.validate();
            registeredChart = true;
        } catch (Exception e) {
            log.warning("could not register display panel: " + e);
        }
    }

    void unregisterChart() {
        try {
            AEChip chip = (AEChip) getChipCanvas().getChip();
            AEViewer viewer = chip.getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();
            imagePanel.remove(activityChart);
            registeredChart = false;
        } catch (Exception e) {
            log.warning("could not unregister control panel: " + e);
        }
    }

    private void drawRectangle(GL gl, float x, float y, float w, float h) {
        gl.glLineWidth(2f);
        gl.glColor3f(1, 1, 1);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(x, y);
        gl.glVertex2f(x + w, y);
        gl.glVertex2f(x + w, y + h);
        gl.glVertex2f(x, y + h);
        gl.glEnd();
    }

    public int getGain(int chan) {
        return displayControl[chan].getGain();
    }

    public void setGain(int chan, int gain) {
        displayControl[chan].setGain(gain);
    }

    public int getOffset(int chan) {
        return displayControl[chan].getOffset();
    }

    public void setOffset(int chan, int offset) {
        displayControl[chan].setOffset(offset);
    }

    public void setHidden(int chan, boolean yes) {
        displayControl[chan].setHidden(yes);
    }

    public boolean isHidden(int chan) {
        return displayControl[chan].isHidden();
    }

    public class DisplayControl {

        private int chan;
        private int gain;
        private int offset;
        private boolean hidden;
        private String name;

        public DisplayControl(int chan) {
            this.chan = chan;
            gain = prefs.getInt("CochleaAMS1cRollingCochleagramADCDisplayMethod.gain" + chan, 1);
            offset = prefs.getInt("CochleaAMS1cRollingCochleagramADCDisplayMethod.offset" + chan, 0);
            hidden = prefs.getBoolean("CochleaAMS1cRollingCochleagramADCDisplayMethod.hidden" + chan, false);
            name = prefs.get("CochleaAMS1cRollingCochleagramADCDisplayMethod.name" + chan, "Chan" + chan);
        }

        /**
         * @return the gain
         */
        public int getGain() {
            return gain;
        }

        /**
         * @param gain the gain to set
         */
        public void setGain(int gain) {
            this.gain = gain;
            prefs.putInt("CochleaAMS1cRollingCochleagramADCDisplayMethod.gain" + chan, gain);
        }

        /**
         * @return the offset
         */
        public int getOffset() {
            return offset;
        }

        /**
         * @param offset the offset to set
         */
        public void setOffset(int offset) {
            this.offset = offset;
            prefs.putInt("CochleaAMS1cRollingCochleagramADCDisplayMethod.offset" + chan, offset);
        }

        /**
         * @return the hidden
         */
        public boolean isHidden() {
            return hidden;
        }

        /**
         * @param hidden the hidden to set
         */
        public void setHidden(boolean hidden) {
            this.hidden = hidden;
            prefs.putBoolean("CochleaAMS1cRollingCochleagramADCDisplayMethod.hidden" + chan, hidden);
        }

        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
            prefs.put("CochleaAMS1cRollingCochleagramADCDisplayMethod.hidden" + chan, name);
        }

        /**
         * @return the chan
         */
        public int getChan() {
            return chan;
        }
    }
}
