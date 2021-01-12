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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JApplet;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.border.TitledBorder;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEUnicastInput;
import net.sf.jaer.eventio.AEUnicastSettings;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.FilterFrame;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.Series;
import net.sf.jaer.util.chart.XYChart;
import net.sf.jaer.util.filter.LowpassFilter;
import ch.unizh.ini.jaer.chip.retina.DVS128;

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
//    private AEUnicastOutput aeLiveOutputStream; // streams to client, on same host
    volatile boolean stopflag = false;
    private int unicastInputPort = AEUnicastSettings.ARC_TDS_STREAM_PORT;
//    private int unicastOutputPort = unicastInputPort + 1;
    // activity
    private final int NUM_ACTIVITY_SAMPLES = 10000;
    private final int RESET_SCALE_COUNT = NUM_ACTIVITY_SAMPLES;
    private final int ACTVITY_SECONDS_TO_SHOW = 300;
    private final int RESET_FILTER_STARTUP_COUNT = 10;
    private final int TITLE_UPDATE_INTERVAL = 1;
    private int sampleCount = 0;
    private Series activitySeries;
    private Axis timeAxis;
    private Axis activityAxis;
    private Category activityCategory;
    private XYChart activityChart;
    private float msTime = 0, lastMsTime = 0;
//    private long nstime;
    private LowpassFilter activityChartLowpassFilter;
    private float maxActivity = 0;
//    Random random = new Random();
//    static HardwareInterface dummy=HardwareInterfaceFactory.instance().getFirstAvailableInterface(); // applet classloader problems, debug test
    FrameRater frameRater = new FrameRater();
    BackgroundActivityFilter backgroundActivityFilter;
    AutomaticReplayPlayer automaticReplayPlayer;
    Thread repaintThread = null;
    static Preferences prefs = Preferences.userNodeForPackage(DVSActApplet.class);
    private int fps = prefs.getInt("DVSActApplet.fps", 15);
    FilterFrame filterFrame = null;
//    private class Animator extends Thread{
//        public void run(){
//            while(!stopme){
//            frameRater.takeBefore();
//            }
//        }
//    }
//    Animator frameRater;

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
            log.info("applet init, receiving from TDS on port " + unicastInputPort);
            initComponents();

            activitySeries = new Series(2, NUM_ACTIVITY_SAMPLES);

            timeAxis = new Axis(0, ACTVITY_SECONDS_TO_SHOW);
            timeAxis.setTitle("time");
            timeAxis.setUnit((ACTVITY_SECONDS_TO_SHOW / 60) + " minutes");

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

            activityChartLowpassFilter = new LowpassFilter();
            activityChartLowpassFilter.set3dBFreqHz(0.25f);

            try {
                liveChip = new DVS128();
                liveChip.setName("Live DVS");

                liveCanvas = liveChip.getCanvas();
                liveCanvas.setBorderSpacePixels(1);

                liveChip.getRenderer().setColorScale(2);
                liveChip.getRenderer().setColorMode(AEChipRenderer.ColorMode.GrayLevel);

                livePanel.add(liveCanvas.getCanvas(), BorderLayout.CENTER);

            } catch (Exception e) {
                log.warning("couldn't construct DVS128 chip object: " + e);
                e.printStackTrace();
            }
            frameRater.setDesiredFPS(getFps());
            backgroundActivityFilter = new BackgroundActivityFilter(liveChip);
            backgroundActivityFilter.setPreferredEnabledState();
//            backgroundActivityFilter.setDt(getBgFilterDt()); // set from own preferences
//            backgroundActivityFilter.setFilterEnabled(true);
            automaticReplayPlayer = new AutomaticReplayPlayer(liveChip);
            automaticReplayPlayer.setPreferredEnabledState();
//            automaticReplayPlayer.setFilterEnabled(true);
            FilterChain fc = new FilterChain(liveChip);
            fc.add(backgroundActivityFilter);
            fc.add(automaticReplayPlayer);
            fc.setFilteringEnabled(true);
            liveChip.setFilterChain(fc);
            fc.setFilteringEnabled(true);
            validate();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    synchronized public void start() {
        super.start();
        log.info("applet starting with unicastInputPort=" + unicastInputPort);
        openNetworkInputStream();
        lastMsTime = System.nanoTime() / 1000000;

        stopflag = false;
        repaintThread = new Thread() {

            @Override
			public void run() {
                frameRater.takeAfter();
                while (!stopflag) {
                    frameRater.delayForDesiredFPS();
                    repaint(); // calls for repaint, but these calls may be coalesced into a single paint
                }
                log.info("stopflag set, stopping repaint thread");
                if (aeLiveInputStream != null) {
                    aeLiveInputStream.close();
                }
//              if (aeLiveOutputStream != null) {
//                  aeLiveOutputStream.close();
//              }
            }
        };
//        repaintThread.setPriority(Thread.NORM_PRIORITY+1);
        repaintThread.start();
    }

    @Override
    synchronized public void stop() {
        super.stop();
        log.info("applet stop, setting stopflag=true and closing input stream");
        stopflag = true;

    }

    private void openNetworkInputStream() {
        try {
            if (aeLiveInputStream != null) {
                aeLiveInputStream.close();
            }
            aeLiveInputStream = new AEUnicastInput(liveChip);
            aeLiveInputStream.setPort(unicastInputPort);
            aeLiveInputStream.set4ByteAddrTimestampEnabled(AEUnicastSettings.ARC_TDS_4_BYTE_ADDR_AND_TIMESTAMPS);
            aeLiveInputStream.setAddressFirstEnabled(AEUnicastSettings.ARC_TDS_ADDRESS_BYTES_FIRST_ENABLED);
            aeLiveInputStream.setSequenceNumberEnabled(AEUnicastSettings.ARC_TDS_SEQUENCE_NUMBERS_ENABLED);
            aeLiveInputStream.setSwapBytesEnabled(AEUnicastSettings.ARC_TDS_SWAPBYTES_ENABLED);
            aeLiveInputStream.setTimestampMultiplier(AEUnicastSettings.ARC_TDS_TIMESTAMP_MULTIPLIER);
            aeLiveInputStream.setBufferSize(AEUnicastSettings.ARC_TDS_BUFFER_SIZE); // max packet size is 1500 bytes according to ARC

//            aeLiveInputStream.setPriority(Thread.NORM_PRIORITY+2);
            aeLiveInputStream.open();
            log.info("opened AEUnicastInput " + aeLiveInputStream);

            aeLiveInputStream.readPacket();


//            if (aeLiveOutputStream != null) {
//                aeLiveOutputStream.close();
//            }
//            aeLiveOutputStream = new AEUnicastOutput();
//            aeLiveOutputStream.setHost("localhost");
//            aeLiveOutputStream.setPort(unicastOutputPort);
//            aeLiveOutputStream.set4ByteAddrTimestampEnabled(AEUnicastSettings.ARC_TDS_4_BYTE_ADDR_AND_TIMESTAMPS);
//            aeLiveOutputStream.setAddressFirstEnabled(AEUnicastSettings.ARC_TDS_ADDRESS_BYTES_FIRST_ENABLED);
//            aeLiveOutputStream.setSequenceNumberEnabled(AEUnicastSettings.ARC_TDS_SEQUENCE_NUMBERS_ENABLED);
//            aeLiveOutputStream.setSwapBytesEnabled(AEUnicastSettings.ARC_TDS_SWAPBYTES_ENABLED);
//            aeLiveOutputStream.setTimestampMultiplier(AEUnicastSettings.ARC_TDS_TIMESTAMP_MULTIPLIER);
//
//            log.info("opened AEUnicastOutput " + aeLiveOutputStream);

            aeLiveInputStream.readPacket();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    synchronized public void paint(Graphics g) {
        super.paint(g);
        /** paint calls may be coalesced, causing a miss in frame rating?*/
        if (stopflag) {
            log.info("stop set, not painting.");
            return;
        }
        try {
            if (aeLiveInputStream != null) {

                AEPacketRaw aeRaw = aeLiveInputStream.readPacket(); // gets all data since last call to paint
                if (aeRaw != null) {
                    //                    try {
//                        aeLiveOutputStream.writePacket(aeRaw);
//                    } catch (IOException e) {
//                        log.warning("writing input packet to output " + e);
//                    }
                    EventPacket ae = liveChip.getEventExtractor().extractPacket(aeRaw);
                    updateActivityChart(ae); // update chart with live input data

                    ae = liveChip.getFilterChain().filterPacket(ae);
                    if (ae != null) {
                        liveChip.getRenderer().render(ae);
                        try {
                            liveChip.getCanvas().paintFrame();
                        } catch (Exception pf) {
                            log.warning("caught while painting canvas " + pf);
                        }
                        int nevents = ae.getSize();
                        if (isVisible() && ((sampleCount % TITLE_UPDATE_INTERVAL) == 0)) {
                            ((TitledBorder) livePanel.getBorder()).setTitle("Kitchen: " + nevents + " events" + ", FPS=" + String.format("%.1f", frameRater.getAverageFPS()));
                        }
//                    activityCategory.getDataTransformation()[12] = -msTime;  // hack: shift progress curve back
                    } else {
                        ((TitledBorder) livePanel.getBorder()).setTitle("Live: " + "null packet");
                    }
                }
            }
            try {
                if (activityChart != null) {
                    activityChart.display();
                }
            } catch (Exception e) {
                log.warning("while displaying activity chart caught " + e);
            }
        } catch (Exception e) {
            log.warning("while repainting, caught exception " + e);
            e.printStackTrace();
        }
        frameRater.takeAfter();
    }

    /**
     * For testing in JFrame
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        JApplet applet = new DVSActApplet();
        JFrame frame = new ActivityMonitorTest(applet);
        frame.setVisible(true);
        applet.init();
        applet.start();
    }

    /** This method is called from within the init() method to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        controlPopupMenu = new javax.swing.JPopupMenu();
        fpsMenuItem = new javax.swing.JMenuItem();
        ffMenuItem = new javax.swing.JMenuItem();
        livePanel = new javax.swing.JPanel();
        activityPanel = new javax.swing.JPanel();

        controlPopupMenu.setLightWeightPopupEnabled(false);

        fpsMenuItem.setText("Choose target FPS rendering rate...");
        fpsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                fpsMenuItemActionPerformed(evt);
            }
        });
        controlPopupMenu.add(fpsMenuItem);

        ffMenuItem.setText("Show filter parameter controls");
        ffMenuItem.setToolTipText("Allows control of processing of data");
        ffMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                ffMenuItemActionPerformed(evt);
            }
        });
        controlPopupMenu.add(ffMenuItem);

        setBackground(new java.awt.Color(0, 0, 0));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
			public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });

        livePanel.setBackground(new java.awt.Color(0, 0, 0));
        livePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Live view", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 11), new java.awt.Color(255, 255, 255))); // NOI18N
        livePanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
			public void mousePressed(java.awt.event.MouseEvent evt) {
                livePanelMousePressed(evt);
            }
            @Override
			public void mouseReleased(java.awt.event.MouseEvent evt) {
                livePanelMouseReleased(evt);
            }
        });
        livePanel.setLayout(new java.awt.BorderLayout());
        getContentPane().add(livePanel, java.awt.BorderLayout.WEST);

        activityPanel.setBackground(new java.awt.Color(0, 0, 0));
        activityPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Recent activity", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 11), new java.awt.Color(255, 255, 255))); // NOI18N
        activityPanel.setLayout(new java.awt.BorderLayout());
        getContentPane().add(activityPanel, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void maybeShowPopup(MouseEvent evt) {
        if (evt.isPopupTrigger()) {
            controlPopupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
        }
    }
    private void livePanelMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_livePanelMousePressed
        maybeShowPopup(evt);
    }//GEN-LAST:event_livePanelMousePressed

    private void livePanelMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_livePanelMouseReleased
        maybeShowPopup(evt);
    }//GEN-LAST:event_livePanelMouseReleased

    private void fpsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fpsMenuItemActionPerformed
        String fpsString = JOptionPane.showInputDialog("<html>Desired frames per second? (Currently " + getFps() + ")");
        if ((fpsString == null) || fpsString.equals("")) {
            log.info("canceled fps");
            return;
        }
        try {
            setFps(Integer.parseInt(fpsString));
        } catch (Exception e) {
            log.warning(e.toString());
        }
        controlPopupMenu.setVisible(false);
}//GEN-LAST:event_fpsMenuItemActionPerformed

    private void formComponentResized (java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        log.info("resized: evt=" + evt.toString());
        livePanel.revalidate();
        activityPanel.revalidate();
        repaint();
    }//GEN-LAST:event_formComponentResized

    private void ffMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ffMenuItemActionPerformed
        if (filterFrame == null) {
            filterFrame = new FilterFrame(liveChip);
        }
        filterFrame.setVisible(true);
    }//GEN-LAST:event_ffMenuItemActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel activityPanel;
    private javax.swing.JPopupMenu controlPopupMenu;
    private javax.swing.JMenuItem ffMenuItem;
    private javax.swing.JMenuItem fpsMenuItem;
    private javax.swing.JPanel livePanel;
    // End of variables declaration//GEN-END:variables

 
    private void updateActivityChart(EventPacket ae) {
        if (ae == null) {
            return;
        }
        msTime = System.nanoTime() / 1000000;
        float dt = msTime - lastMsTime;
        if (dt < 1) {
            dt = 1;
        }
        lastMsTime = msTime;
        int nevents = ae.getSize();
        float instantaneousActivity = nevents / dt;
        float activity = activityChartLowpassFilter.filter(instantaneousActivity, ae.getLastTimestamp());
//                    activity=activity*activity; // power
        activitySeries.add(msTime, activity);
//                    activitySeries.appendCopy(msTime, random.nextFloat()); // debug
        timeAxis.setMaximum(msTime);
        timeAxis.setMinimum(msTime - (1000 * ACTVITY_SECONDS_TO_SHOW));
        sampleCount++;
        // startup
        if (sampleCount == RESET_FILTER_STARTUP_COUNT) {
            activityChartLowpassFilter.setInternalValue(activity);
            maxActivity = 0;
        }
        if ((sampleCount % RESET_SCALE_COUNT) == 0) {
            maxActivity = 0;
        }
        if (activity > maxActivity) {
            maxActivity = activity;
        }
        activityAxis.setMaximum(maxActivity);
    }
    // End of variables declaration

    /** Computes and executes appropriate delayForDesiredFPS to
     * try to maintain constant rendering rate.
     */
    private class FrameRater {

        final int MAX_FPS = 120;
        int desiredFPS = 10;
        final int nSamples = 10;
        long[] samplesNs = new long[nSamples]; // samples of intervals
        int index = 0;
        int delayMs = 1;
        int desiredPeriodMs = (int) (1000f / desiredFPS);
        private long beforeTimeNs = System.nanoTime(), lastdt;

        final void setDesiredFPS(int fps) {
            if (fps < 1) {
                fps = 1;
            } else if (fps > MAX_FPS) {
                fps = MAX_FPS;
            }
            desiredFPS = fps;
            desiredPeriodMs = 1000 / fps;
        }

        final int getDesiredFPS() {
            return desiredFPS;
        }

        final float getAveragePeriodNs() {
            long sum = 0;
            for (int i = 0; i < nSamples; i++) {
                sum += samplesNs[i];
            }
            return (float) sum / nSamples;
        }

        final float getAverageFPS() {
            return 1f / (getAveragePeriodNs() / 1e9f);
        }

        final float getLastFPS() {
            return 1f / (lastdt / 1e9f);
        }

        final int getLastDelayMs() {
            return delayMs;
        }

        final long getLastDtNs() {
            return lastdt;
        }

        /**  Call this ONCE after capture/render. It will store the time since the last call. */
        synchronized public final void takeAfter() {
            long afterTimeNs = System.nanoTime();
            lastdt = afterTimeNs - beforeTimeNs;
            samplesNs[index++] = lastdt;
            if (index >= nSamples) {
                index = 0;
            }
            beforeTimeNs = afterTimeNs;
        }

        /** Call this to delayForDesiredFPS enough to make the total time including
         *  last sample period equal to desiredPeriodMs.
         */
        public final void delayForDesiredFPS() {
            synchronized (this) {
                delayMs = Math.round(desiredPeriodMs - ((float) lastdt / 1000000));
            }
            if (delayMs <= 0) {
                delayMs = 1; // don't hog all cycles
            }
            try {
//                System.out.println("lastdt="+lastdt+", delaying "+delayMs+"ms");
                Thread.sleep(delayMs);
            } catch (java.lang.InterruptedException e) {
            }
        }
    }

    public void setFps(int fps) {
        this.fps = fps;
        if (frameRater != null) {
            frameRater.setDesiredFPS(fps);
        }
        prefs.putInt("DVSActApplet.fps", fps);
    }

    public int getFps() {
        return fps;
    }
}
