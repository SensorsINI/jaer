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
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
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

    private CochleaAMS1c chip = null;
    boolean registeredChart = false;
    private Series[] activitySeries;
    private Axis timeAxis;
    private Axis activityAxis;
    private Category[] activityCategories;
    private XYChart activityChart;
    private int NUM_ACTIVITY_SAMPLES = 1000;
    private Color[] colors={Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};

    public CochleaAMS1cRollingCochleagramADCDisplayMethod(CochleaAMS1c chip) {
        super(chip.getCanvas());
        this.chip = chip;
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        checkADCDisplay();
        super.display(drawable);
        ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples data = chip.getAdcSamples();
        data.swapBuffers();
        int chan=0;
        for (ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples.ChannelBuffer cb : data.currentReadingDataBuffer.channelBuffers) {
            for (ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples.ADCSample s : cb.samples) {
                activitySeries[chan].add(s.time, s.data);
            }
            chan++;
        }
        timeAxis.setMinimum(getStartTimeUs());
        timeAxis.setMaximum(getStartTimeUs() + getTimeWidthUs());
        activityAxis.setMinimum(0);
        activityAxis.setMaximum(CochleaAMS1cADCSamples.MAX_ADC_VALUE);
        try {
            activityChart.display();
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
            imagePanel.add(activityChart, BorderLayout.SOUTH);
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
}
