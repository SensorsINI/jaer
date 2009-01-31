/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * DVSActivityMonitor.java
 *
 * Created on Jan 29, 2009, 10:52:40 AM
 */
package net.sf.jaer.jaerappletviewer;

import ch.unizh.ini.jaer.chip.retina.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.io.IOException;
import java.util.logging.Logger;
import javax.swing.JApplet;
import javax.swing.JFrame;
import javax.swing.border.TitledBorder;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEUnicastInput;
import net.sf.jaer.eventio.AEUnicastOutput;
import net.sf.jaer.eventio.AEUnicastSettings;
import net.sf.jaer.graphics.*;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.Series;
import net.sf.jaer.util.chart.XYChart;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Shows a stream of AE events from a retina and plots the recent activitySeries over msTime as a rolling chart.
 * Used in the INI foyer to show kitchen activitySeries.
 * @author tobi
 */
public class DVSActApplet extends javax.swing.JApplet {

    private AEChip liveChip;
    private ChipCanvas liveCanvas;
    private Logger log = Logger.getLogger("JAERAppletViewer");
    private AEUnicastInput aeLiveInputStream; // network input stream, from ARC TDS
    private AEUnicastOutput aeLiveOutputStream; // streams to client, on same host
    volatile boolean stopflag = false;
    private long frameDelayMs = 100;
    private int unicastInputPort = AEUnicastSettings.ARC_TDS_STREAM_PORT;
    private int unicastOutputPort = unicastInputPort + 1;
    // activity
    private final int NUM_ACTIVITY_SAMPLES = 10000;
    private final int RESET_SCALE_COUNT = NUM_ACTIVITY_SAMPLES;
    private final int ACTVITY_SECONDS_TO_SHOW = 300;
    private final int RESET_FILTER_STARTUP_COUNT = 10;
    private final int TITLE_UPDATE_INTERVAL = 5;
    private int sampleCount = 0;
    private Series activitySeries;
    private Axis timeAxis;
    private Axis activityAxis;
    private Category activityCategory;
    private XYChart activityChart;
    private float msTime = 0, lastMsTime=0;
//    private long nstime;
    private LowpassFilter filter;
    private float maxActivity = 0;
//    Random random = new Random();
//    static HardwareInterface dummy=HardwareInterfaceFactory.instance().getFirstAvailableInterface(); // applet classloader problems, debug test

    @Override
    public String getAppletInfo() {
        return "ActivityMonitorApplet";
    }

    private void setCanvasDefaults(ChipCanvas canvas) {
//        canvas.setScale(2);
    }

    /** Initializes the applet DVSActivityMonitor */
    @Override
    public void init() {
        try {
            log.info("applet init, receiving from TDS on port "+unicastInputPort+", retranmitting on port "+unicastOutputPort);
            initComponents();

            activitySeries = new Series(2, NUM_ACTIVITY_SAMPLES);

            timeAxis = new Axis(0, ACTVITY_SECONDS_TO_SHOW);
            timeAxis.setTitle("time");
            timeAxis.setUnit(ACTVITY_SECONDS_TO_SHOW / 60 + " minutes");

            activityAxis = new Axis(0, 1); // will be normalized
            activityAxis.setTitle("activity");
//            activityAxis.setUnit("events");

            activityCategory = new Category(activitySeries, new Axis[]{timeAxis, activityAxis});
            activityCategory.setColor(new float[]{0.0f, 0.0f, 1.0f});

            activityChart = new XYChart("");
            activityChart.setBackground(Color.black);
            activityChart.setForeground(Color.white);
            activityChart.setGridEnabled(false);
            activityChart.addCategory(activityCategory);
//            activityChart.setToolTipText("Shows recent activity");
            activityPanel.add(activityChart, BorderLayout.CENTER);
            ((TitledBorder) activityPanel.getBorder()).setTitle("Recent kitchen activity");

            filter = new LowpassFilter();
            filter.set3dBFreqHz(0.5f);

            try {
                liveChip = new DVS128();
                liveChip.setName("Live DVS");

                liveCanvas = liveChip.getCanvas();
                liveCanvas.setOpenGLEnabled(true);
                liveCanvas.setBorderSpacePixels(1);

                liveChip.getRenderer().setColorScale(2);
                liveChip.getRenderer().setColorMode(AEChipRenderer.ColorMode.GrayLevel);

                livePanel.add(liveCanvas.getCanvas(), BorderLayout.CENTER);
            } catch (Exception e) {
                log.warning("couldn't construct DVS128 chip object: " + e);
                e.printStackTrace();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    synchronized public void start() {
        super.start();
        log.info("applet starting with unicastInputPort=" + unicastInputPort);
        openNetworkInputStream();
        lastMsTime=System.nanoTime() / 1000000;
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
        if (aeLiveOutputStream != null) {
            aeLiveOutputStream.close();
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

            aeLiveInputStream.readPacket();


            if (aeLiveOutputStream != null) {
                aeLiveOutputStream.close();
            }
            aeLiveOutputStream = new AEUnicastOutput();
            aeLiveOutputStream.setHost("localhost");
            aeLiveOutputStream.setPort(unicastOutputPort);
            aeLiveOutputStream.set4ByteAddrTimestampEnabled(AEUnicastSettings.ARC_TDS_4_BYTE_ADDR_AND_TIMESTAMPS);
            aeLiveOutputStream.setAddressFirstEnabled(AEUnicastSettings.ARC_TDS_ADDRESS_BYTES_FIRST_ENABLED);
            aeLiveOutputStream.setSequenceNumberEnabled(AEUnicastSettings.ARC_TDS_SEQUENCE_NUMBERS_ENABLED);
            aeLiveOutputStream.setSwapBytesEnabled(AEUnicastSettings.ARC_TDS_SWAPBYTES_ENABLED);
            aeLiveOutputStream.setTimestampMultiplier(AEUnicastSettings.ARC_TDS_TIMESTAMP_MULTIPLIER);

            log.info("opened AEUnicastOutput " + aeLiveOutputStream);

            aeLiveInputStream.readPacket();


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
                try {
                    aeLiveOutputStream.writePacket(aeRaw);
                } catch (IOException e) {
                    log.warning("writing input packet to output " + e);
                }
                EventPacket ae = liveChip.getEventExtractor().extractPacket(aeRaw);
                if (ae != null) {
                    liveChip.getRenderer().render(ae);
                    liveChip.getCanvas().paintFrame();
                    int nevents = ae.getSize();
                    if (isVisible() && sampleCount % TITLE_UPDATE_INTERVAL == 0) {
                        ((TitledBorder) livePanel.getBorder()).setTitle("Kitchen live: " + nevents + " events");
                    }
                    msTime = System.nanoTime() / 1000000;
                    float dt=msTime-lastMsTime;
                    if(dt<1) dt=1;
                    lastMsTime=msTime;
                    float instantaneousActivity=nevents/dt;
                    float activity = filter.filter(instantaneousActivity, ae.getLastTimestamp());
//                    activity=activity*activity; // power
                    activitySeries.add(msTime, activity);
//                    activitySeries.add(msTime, random.nextFloat()); // debug
                    timeAxis.setMaximum(msTime);
                    timeAxis.setMinimum(msTime - 1000 * ACTVITY_SECONDS_TO_SHOW);
                    sampleCount++;
                    // startup
                    if (sampleCount == RESET_FILTER_STARTUP_COUNT) {
                        filter.setInternalValue(activity);
                        maxActivity = 0;
                    }
                    if (sampleCount % RESET_SCALE_COUNT == 0) {
                        maxActivity = 0;
                    }
                    if (activity > maxActivity) {
                        maxActivity = activity;
                    }
                    activityAxis.setMaximum(maxActivity);
//                    activityCategory.getDataTransformation()[12] = -msTime;  // hack: shift progress curve back
                } else {
                    ((TitledBorder) livePanel.getBorder()).setTitle("Live: " + "null packet");
                }
            }
        }
        /* update display data */
        activityChart.display();
        try {
            Thread.sleep(frameDelayMs);
        } catch (InterruptedException e) {
        }
        repaint(); // recurse
    }

    /**
     * For testing in JFrame
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        JApplet applet = new DVSActApplet();
        JFrame frame = new ActivityMonitorTest(applet);
        applet.init();
        applet.start();
        frame.setVisible(true);
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

        setBackground(new java.awt.Color(0, 0, 0));

        livePanel.setBackground(new java.awt.Color(0, 0, 0));
        livePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Live view", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 11), new java.awt.Color(255, 255, 255))); // NOI18N
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
