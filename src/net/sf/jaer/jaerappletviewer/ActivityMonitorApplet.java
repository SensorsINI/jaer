/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * ActivityMonitorApplet.java
 *
 * Created on Jan 29, 2009, 10:52:40 AM
 */
package net.sf.jaer.jaerappletviewer;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;
import javax.swing.border.TitledBorder;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventio.AEUnicastInput;
import net.sf.jaer.eventio.AEUnicastSettings;
import net.sf.jaer.graphics.*;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.Series;
import net.sf.jaer.util.chart.XYChart;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Shows a stream of AE events from a retina and plots the recent activitySeries over time as a rolling chart.
 * Used in the INI foyer to show kitchen activitySeries.
 * @author tobi
 */
public class ActivityMonitorApplet extends javax.swing.JApplet {

    AEChip liveChip;
    ChipCanvas liveCanvas;
    Logger log = Logger.getLogger("JAERAppletViewer");
    EngineeringFormat fmt = new EngineeringFormat();
    AEUnicastInput aeLiveInputStream; // network input stream
    AEInputStream his; // url input stream
    volatile boolean stopflag = false;
    private long frameDelayMs = 100;
    private int unicastInputPort = AEUnicastSettings.ARC_TDS_STREAM_PORT;
    // activity
    private final int NUM_ACTIVITY_SAMPLES = 1000;
    Series activitySeries;
    Axis timeAxis;  // display 1000 event packets
    Axis activityAxis;
    Category activityCategory;
    XYChart activityChart;
    float time = 0;

    LowpassFilter filter;
    float maxActivity=0;

    Random random=new Random();

    @Override
    public String getAppletInfo() {
        return "ActivityMonitorApplet";
    }

    private void setCanvasDefaults(ChipCanvas canvas) {
//        canvas.setScale(2);
        canvas.setOpenGLEnabled(true);
    }

    /** Initializes the applet ActivityMonitorApplet */
    @Override
    public void init() {
        try {
            log.info("applet init");
            initComponents();

            activitySeries = new Series(2, NUM_ACTIVITY_SAMPLES);
            timeAxis = new Axis(0,NUM_ACTIVITY_SAMPLES);  // display 1000 event packets
            timeAxis.setTitle("time");
            timeAxis.setUnit("sample");
            activityAxis = new Axis(0, 1); // will be normalized
            activityAxis.setTitle("activity");
            activityAxis.setUnit("events");
            activityCategory = new Category(activitySeries, new Axis[]{timeAxis, activityAxis});
            activityCategory.setColor(new float[]{0.0f, 0.0f, 1.0f});
            activityChart = new XYChart("");
            activityChart.addCategory(activityCategory);
            activityChart.setToolTipText("Shows recent activity");
            activityPanel.add(activityChart, BorderLayout.CENTER);
            filter=new LowpassFilter();
            filter.setTauMs(3000);

            liveChip = new DVS128();
            liveChip.setName("Live DVS");
            liveCanvas = liveChip.getCanvas();
            liveChip.getRenderer().setColorScale(2);
            liveChip.getRenderer().setColorMode(AEChipRenderer.ColorMode.GrayLevel);
            livePanel.add(liveCanvas.getCanvas(), BorderLayout.CENTER);
            setCanvasDefaults(liveCanvas);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    synchronized public void start() {
        super.start();
        log.info("applet starting with unicastInputPort=" + unicastInputPort);
        openNetworkInputStream();
        repaint();  // starts recursive repaint, finishes when paint returns without calling repaint itself
    }

    @Override
    synchronized public void stop() {
        super.stop();
        log.info("applet stop, setting stopflag=true and closing input stream");
        stopflag = true;
        if (aeLiveInputStream != null) {
            aeLiveInputStream.close();
        }
    }

    private void openNetworkInputStream() {
        try {
            if (aeLiveInputStream != null) {
                aeLiveInputStream.close();
            }
            aeLiveInputStream = new AEUnicastInput();
            aeLiveInputStream.setHost("localhost");
            aeLiveInputStream.setPort(unicastInputPort);
            aeLiveInputStream.set4ByteAddrTimestampEnabled(AEUnicastSettings.ARC_TDS_4_BYTE_ADDR_AND_TIMESTAMPS);
            aeLiveInputStream.setAddressFirstEnabled(AEUnicastSettings.ARC_TDS_ADDRESS_BYTES_FIRST_ENABLED);
            aeLiveInputStream.setSequenceNumberEnabled(AEUnicastSettings.ARC_TDS_SEQUENCE_NUMBERS_ENABLED);
            aeLiveInputStream.setSwapBytesEnabled(AEUnicastSettings.ARC_TDS_SWAPBYTES_ENABLED);
            aeLiveInputStream.setTimestampMultiplier(AEUnicastSettings.ARC_TDS_TIMESTAMP_MULTIPLIER);

            aeLiveInputStream.start();
            log.info("opened AEUnicastInput " + aeLiveInputStream);

            stopflag = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    synchronized public void paint(Graphics g) {
        super.paint(g);
        if (stopflag) {
            log.info("stop set, not painting again or calling repaint");
            return;
        }
        if (aeLiveInputStream != null) {
            AEPacketRaw aeRaw = aeLiveInputStream.readPacket();
            if (aeRaw != null) {
                EventPacket ae = liveChip.getEventExtractor().extractPacket(aeRaw);
                if (ae != null) {
                    liveChip.getRenderer().render(ae);
                    liveChip.getCanvas().paintFrame();
                    ((TitledBorder) livePanel.getBorder()).setTitle("Kitchen Live: " + aeRaw.getNumEvents() + " events");
                    time += 1;
                    float activity=filter.filter(ae.getSize(), ae.getLastTimestamp());
                    if(activity>maxActivity) maxActivity=activity;
                    activitySeries.add(time, activity);
//                    activitySeries.add(time, random.nextFloat());
                    timeAxis.setMaximum(time);
                    timeAxis.setMinimum(time-NUM_ACTIVITY_SAMPLES);
                    activityAxis.setMaximum(maxActivity);
//                    activityCategory.getDataTransformation()[12] = -time;  // hack: shift progress curve back
                } else {
                    ((TitledBorder) livePanel.getBorder()).setTitle("Live: " + "null packet");
                }
            }
        }
        /* update display data */
        activityChart.paint(g);
        try {
            Thread.sleep(frameDelayMs);
        } catch (InterruptedException e) {
        }
        repaint(); // recurse
    }

    /** This method is called from within the init() method to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        livePanel = new javax.swing.JPanel();
        activityPanel = new javax.swing.JPanel();

        livePanel.setBackground(new java.awt.Color(0, 0, 0));
        livePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Live - the INI kitchen", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 11), new java.awt.Color(255, 255, 255))); // NOI18N
        livePanel.setLayout(new java.awt.BorderLayout());

        activityPanel.setBackground(new java.awt.Color(0, 0, 0));
        activityPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Recent activity", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 11), new java.awt.Color(255, 255, 255))); // NOI18N
        activityPanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(livePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(activityPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 378, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(livePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 209, Short.MAX_VALUE)
            .addComponent(activityPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 209, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel activityPanel;
    private javax.swing.JPanel livePanel;
    // End of variables declaration//GEN-END:variables
}
