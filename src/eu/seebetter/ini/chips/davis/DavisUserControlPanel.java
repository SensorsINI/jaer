/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.Color;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;

import javax.swing.JSpinner;

import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.biasgen.PotTweaker;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Wraps some key apsDVS sensor control in more user-friendly control panel.
 *
 * @author tobi
 */
public class DavisUserControlPanel extends javax.swing.JPanel implements PropertyChangeListener, Observer {

    private static final long serialVersionUID = 3894988983329773402L;

    protected DavisChip chip = null;
    protected DavisTweaks apsDvsTweaks;
    DavisConfig.VideoControl videoControl;
    DavisVideoContrastController contrastController;
    AEChipRenderer renderer = null;
    private static EngineeringFormat engFmt = new EngineeringFormat();

    private static final Logger log = Logger.getLogger("DVSFunctionalControlPanel");

    /**
     * Creates new form ApsDVSUserControlPanel
     */
    public DavisUserControlPanel(final Chip chip) {
        this.chip = (DavisChip) chip; // will throw ClassCastException if not right kind of chip.
        renderer = this.chip.getRenderer();
        apsDvsTweaks = (DavisTweaks) chip.getBiasgen();
        initComponents();
        // code must be after initComponents so that these components exist
        final PotTweaker[] tweakers = {thresholdTweaker, onOffBalanceTweaker, maxFiringRateTweaker, bandwidthTweaker};
        for (final PotTweaker tweaker : tweakers) {
            chip.getBiasgen().getSupport().addPropertyChangeListener(tweaker); // to reset sliders on load/save of
        }

        histCB.setSelected(this.chip.isShowImageHistogram());
        fdSp.setValue(1000.0f / getConfig().getFrameIntervalMs());
        edSp.setValue(getConfig().getExposureDelayMs());
        glShutterCB.setSelected(((DavisBaseCamera) chip).getDavisConfig().isGlobalShutter());
        displayEventsCheckBox.setSelected(getConfig().isDisplayEvents());
        displayFramesCheckBox.setSelected(getConfig().isDisplayFrames());
        captureFramesCheckBox.setSelected(getConfig().isCaptureFramesEnabled());
        captureEventsCB.setSelected(getConfig().isCaptureEventsEnabled());
        autoshotThresholdSp.setValue(this.chip.getAutoshotThresholdEvents() >> 10);
        final int[] vals = {10, 100, 1000}, mults = {1, 10, 100};
        final float[] fvals = {.02f, .2f, 2, 20, 200, 2000}, fmults = {.001f, .01f, .1f, 1, 10, 100};
        autoshotThresholdSp.addMouseWheelListener(new SpinnerMouseWheelIntHandler(vals, mults));
        autoExpCB.setSelected(this.chip.isAutoExposureEnabled());
        fdSp.addMouseWheelListener(new SpinnerMouseWheelFloatHandler(fvals, fmults));
        edSp.addMouseWheelListener(new SpinnerMouseWheelFloatHandler(fvals, fmults));
        dvsContSp.addMouseWheelListener(new SpinnerMouseWheelIntHandler(new int[]{10, 20}, new int[]{1, 2}));
        dvsContSp.setValue(renderer.getColorScale());
        contrastSp.addMouseWheelListener(new SpinnerMouseWheelFloatHandler(new float[]{1, 2}, new float[]{.05f, .1f}));
        setDvsColorModeRadioButtons();
        renderer.getSupport().addPropertyChangeListener(this);
        if (getConfig() instanceof DavisConfig) {
            // add us as observer for various property changes
            final DavisConfig config = getConfig();
            videoControl = config.getVideoControl();
            videoControl.addObserver(this); // display of various events/frames enabled comes here
            // config.exposureControlRegister.addObserver(this);
            // config.frameDelayControlRegister.addObserver(this); // TODO generates loop that resets the spinner if the
            // new spinner
            // value does not change the exposureControlRegister in ms according to new register value
            imuVisibleCB.setSelected(config.isDisplayImu());
            imuEnabledCB.setSelected(config.isImuEnabled());
        }
        contrastController = ((DavisRenderer) ((AEChip) chip).getRenderer()).getContrastController();
        contrastController.addObserver(this); // get updates from contrast controller
        contrastSp.setValue(contrastController.getContrast());
        contrastSp.setEnabled(!contrastController.isUseAutoContrast());
        autoContrastCB.setSelected(contrastController.isUseAutoContrast());
        this.chip.addObserver(this);
        chip.getSupport().addPropertyChangeListener(this);
        chip.getBiasgen().getSupport().addPropertyChangeListener(this);
        setEstimatedThresholdValues();
        setEstimatedRefractoryPeriod();
        setEstimatedBandwidth();
        
        // TODO add property change listener support so that if bias is changed (outside of tweaks) the computed values are updated. 
    }

    private void setDvsColorModeRadioButtons() {
        switch (renderer.getColorMode()) {
            case RedGreen:
                dvsRedGrRB.setSelected(true);
                break;
            case GrayLevel:
                dvsGrayRB.setSelected(true);
                break;
            default:
                dvsColorButGrp.clearSelection();
        }
    }

    private DavisConfig getConfig() {
        return ((DavisBaseCamera) chip).getDavisConfig();
    }

    private class SpinnerMouseWheelIntHandler implements MouseWheelListener {

        private final int[] vals, mults;

        /**
         * Constructs a new instance
         *
         * @param vals the threshold values
         * @param mults the multipliers for spinner values less than or equal to
         * that number
         */
        SpinnerMouseWheelIntHandler(final int[] vals, final int[] mults) {
            if ((vals == null) || (mults == null)) {
                throw new RuntimeException("vals or mults is null");
            }
            if (vals.length != mults.length) {
                throw new RuntimeException("vals and mults array must be same length and they are not: vals.length=" + vals.length
                        + " mults.length=" + mults.length);
            }
            this.vals = vals;
            this.mults = mults;
        }

        @Override
        public void mouseWheelMoved(final MouseWheelEvent mwe) {
            final JSpinner spinner = (JSpinner) mwe.getSource();
            if (mwe.getScrollType() != MouseWheelEvent.WHEEL_UNIT_SCROLL) {
                return;
            }
            try {
                int value = (Integer) spinner.getValue();

                int i = 0;
                for (i = 0; i < vals.length; i++) {
                    if (value < vals[i]) {
                        break;
                    }
                }
                if (i >= vals.length) {
                    i = vals.length - 1;
                }
                final int mult = mults[i];
                value -= mult * mwe.getWheelRotation();
                if (value < 0) {
                    value = 0;
                }
                spinner.setValue(value);
            } catch (final Exception e) {
                DavisUserControlPanel.log.warning(e.toString());
                return;
            }
        }
    }

    private class SpinnerMouseWheelFloatHandler implements MouseWheelListener {

        private final float[] vals, mults;

        /**
         * Constructs a new instance
         *
         * @param vals the threshold values
         * @param mults the multipliers for spinner values less than or equal to
         * that number
         */
        SpinnerMouseWheelFloatHandler(final float[] vals, final float[] mults) {
            if ((vals == null) || (mults == null)) {
                throw new RuntimeException("vals or mults is null");
            }
            if (vals.length != mults.length) {
                throw new RuntimeException("vals and mults array must be same length and they are not: vals.length=" + vals.length
                        + " mults.length=" + mults.length);
            }
            this.vals = vals;
            this.mults = mults;
        }

        @Override
        public void mouseWheelMoved(final MouseWheelEvent mwe) {
            final JSpinner spinner = (JSpinner) mwe.getSource();
            if (mwe.getScrollType() != MouseWheelEvent.WHEEL_UNIT_SCROLL) {
                return;
            }
            try {
                float value = (Float) spinner.getValue();
                int i = 0;
                // int rot = mwe.getWheelRotation(); // >0 is roll down, want smaller value
                if (true) {
                    for (i = 0; i < vals.length; i++) {
                        if (value <= vals[i]) {
                            break; // take value from vals array that is just above our current value, e.g. if our
                            // current value is 11, then take 20 (if that is next higher vals) as next value and
                            // mult of 20 value as decrement amount
                        }
                    }
                    if (i > (vals.length - 1)) {
                        i = vals.length - 1;
                    }
                }
                // else { // roll up, want larger value
                // for (i = vals.length-1; i >=0 ; i--) {
                // if (value >= vals[i]) {
                // break; // now start at highest vals value and go down, until we find next lower or equal vals, e.g.
                // if we are at 11, then take 2 (if that is next lower value) and then choose that mult that goes with 2
                // to decr
                // }
                // }
                // if (i<0) {
                // i = 0;
                // }
                // }
                final float mult = mults[i];
                value -= mult * mwe.getWheelRotation();
                if (value < 0) {
                    value = 0;
                }
                spinner.setValue(value);
            } catch (final Exception e) {
                DavisUserControlPanel.log.warning(e.toString());
            }
        }
    }

    private void setFileModified() {
        if ((getChip() != null) && (getChip().getAeViewer() != null) && (getChip().getAeViewer().getBiasgenFrame() != null)) {
            getChip().getAeViewer().getBiasgenFrame().setFileModified(true);
        }
    }

    /**
     * Handles property changes from PotTweakers and measured
     * exposureControlRegister and frame rate from chip
     *
     * @param evt
     */
    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
        final String name = evt.getPropertyName();
        try {
            switch (name) {
                case DavisVideoContrastController.AGC_VALUES: {
                    contrastSp.setValue(contrastController.getAutoContrast());
                }
                break;
                case DVSTweaks.THRESHOLD: // fired from Davis240Config setBandwidthTweak
                {
                    final float v = (Float) evt.getNewValue();
                    thresholdTweaker.setValue(v);
                }
                break;
                case DVSTweaks.BANDWIDTH: // log.info(evt.toString());
                {
                    final float v = (Float) evt.getNewValue();
                    bandwidthTweaker.setValue(v);
                }
                break;
                case DVSTweaks.MAX_FIRING_RATE: {
                    final float v = (Float) evt.getNewValue();
                    maxFiringRateTweaker.setValue(v);
                }
                break;
                case DVSTweaks.ON_OFF_BALANCE: {
                    final float v = (Float) evt.getNewValue();
                    onOffBalanceTweaker.setValue(v);
                }
                break;
                case DavisChip.PROPERTY_MEASURED_EXPOSURE_MS: {
                    exposMsTF.setText(String.format("%.3f", (Float) evt.getNewValue()));
                }
                break;
                case DavisChip.PROPERTY_FRAME_RATE_HZ: {
                    fpsTF.setText(String.format("%.3f", (Float) evt.getNewValue()));
                }
                break;
                case AEChipRenderer.EVENT_COLOR_MODE_CHANGE: {
                    setDvsColorModeRadioButtons();
                }
                break;
                case AEChipRenderer.EVENT_COLOR_SCALE_CHANGE: {
                    dvsContSp.setValue(renderer.getColorScale());
                }
                break;
                case DavisDisplayConfigInterface.PROPERTY_IMU_ENABLED: {
                    imuEnabledCB.setSelected((boolean) evt.getNewValue());
                }
                break;
                case DavisDisplayConfigInterface.PROPERTY_IMU_DISPLAY_ENABLED: {
                    imuVisibleCB.setSelected((boolean) evt.getNewValue());
                }
                break;
                case DavisDisplayConfigInterface.PROPERTY_DISPLAY_FRAMES_ENABLED: {
                    displayFramesCheckBox.setSelected((boolean) evt.getNewValue());
                }
                break;
                case DavisDisplayConfigInterface.PROPERTY_CAPTURE_FRAMES_ENABLED: {
                    captureFramesCheckBox.setSelected((boolean) evt.getNewValue());
                }
                break;
                case DavisDisplayConfigInterface.PROPERTY_CAPTURE_EVENTS_ENABLED: {
                    captureEventsCB.setSelected((boolean) evt.getNewValue());
                }
                break;
                case DavisDisplayConfigInterface.PROPERTY_DISPLAY_EVENTS_ENABLED: {
                    displayEventsCheckBox.setSelected((boolean) evt.getNewValue());
                }
                break;
                case DavisVideoContrastController.PROPERTY_CONTRAST: {
                    contrastSp.setValue((float) evt.getNewValue());
                }
                break;
                case DavisDisplayConfigInterface.PROPERTY_EXPOSURE_DELAY_US: {
                    edSp.setValue((Integer) evt.getNewValue() * .001f);
                }
                break;
                case DavisDisplayConfigInterface.PROPERTY_FRAME_INTERVAL_US: {
                    fdSp.setValue(1000.0f / ((Integer) evt.getNewValue() * .001f));
                }
                break;
                case DavisDisplayConfigInterface.PROPERTY_GLOBAL_SHUTTER_MODE_ENABLED: {
                    glShutterCB.setSelected((Boolean) evt.getNewValue());
                }
                break;
                case DavisChip.PROPERTY_AUTO_EXPOSURE_ENABLED: {
                    autoExpCB.setSelected((Boolean) evt.getNewValue());
                }
                break;
            }
        } catch (final Exception e) {
            DavisUserControlPanel.log.warning("responding to property change, caught " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        dvsColorButGrp = new javax.swing.ButtonGroup();
        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        dvsPanel = new javax.swing.JPanel();
        displayEventsCheckBox = new javax.swing.JCheckBox();
        dvsRedGrRB = new javax.swing.JRadioButton();
        dvsGrayRB = new javax.swing.JRadioButton();
        dvsContLabel = new javax.swing.JLabel();
        dvsContSp = new javax.swing.JSpinner();
        captureEventsCB = new javax.swing.JCheckBox();
        bwPanel = new javax.swing.JPanel();
        bwEstimatePanel = new javax.swing.JPanel();
        bwEstLabel = new javax.swing.JLabel();
        bwEstTF = new javax.swing.JTextField();
        bandwidthTweaker = new net.sf.jaer.biasgen.PotTweaker();
        thrPanel = new javax.swing.JPanel();
        thresholdTweaker = new net.sf.jaer.biasgen.PotTweaker();
        onOffBalanceTweaker = new net.sf.jaer.biasgen.PotTweaker();
        jPanel4 = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        onThrTF = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        offThrTF = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        onMinusOffTF = new javax.swing.JTextField();
        refrPanel = new javax.swing.JPanel();
        maxFiringRateTweaker = new net.sf.jaer.biasgen.PotTweaker();
        refrEstimatePanel = new javax.swing.JPanel();
        refrPerLabel = new javax.swing.JLabel();
        refrPerTF = new javax.swing.JTextField();
        apsPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        fdSp = new javax.swing.JSpinner();
        jLabel2 = new javax.swing.JLabel();
        edSp = new javax.swing.JSpinner();
        autoExpCB = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        fpsTF = new javax.swing.JTextField();
        exposMsTF = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        autoshotThresholdSp = new javax.swing.JSpinner();
        contrastSp = new javax.swing.JSpinner();
        autoContrastCB = new javax.swing.JCheckBox();
        displayFramesCheckBox = new javax.swing.JCheckBox();
        histCB = new javax.swing.JCheckBox();
        snapshotButton = new javax.swing.JButton();
        captureFramesCheckBox = new javax.swing.JCheckBox();
        glShutterCB = new javax.swing.JCheckBox();
        imuPanel = new javax.swing.JPanel();
        imuVisibleCB = new javax.swing.JCheckBox();
        imuEnabledCB = new javax.swing.JCheckBox();
        toggleDualViewJB = new javax.swing.JToggleButton();

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        setLayout(new java.awt.BorderLayout());

        dvsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "DVS Events", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N
        dvsPanel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N

        displayEventsCheckBox.setText("Display events");
        displayEventsCheckBox.setToolTipText("Enables rendering of DVS events");
        displayEventsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                displayEventsCheckBoxActionPerformed(evt);
            }
        });
        dvsPanel.add(displayEventsCheckBox);

        dvsColorButGrp.add(dvsRedGrRB);
        dvsRedGrRB.setText("Red/Green");
        dvsRedGrRB.setToolTipText("Show DVS events rendered as green (ON) and red (OFF) 2d histogram");
        dvsRedGrRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dvsRedGrRBActionPerformed(evt);
            }
        });
        dvsPanel.add(dvsRedGrRB);

        dvsColorButGrp.add(dvsGrayRB);
        dvsGrayRB.setText("Gray");
        dvsGrayRB.setToolTipText("Show DVS events rendered as gray scale 2d histogram");
        dvsGrayRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dvsGrayRBActionPerformed(evt);
            }
        });
        dvsPanel.add(dvsGrayRB);

        dvsContLabel.setText("Contrast");
        dvsPanel.add(dvsContLabel);

        dvsContSp.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
        dvsContSp.setToolTipText("Rendering contrast  of DVS events (1 is default). This many events makes full scale color, either black/white or red/green.");
        dvsContSp.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                dvsContSpStateChanged(evt);
            }
        });
        dvsPanel.add(dvsContSp);

        captureEventsCB.setText("Capture events");
        captureEventsCB.setToolTipText("Enables capture of DVS events. Disabling capture turns off AER output of sensor.");
        captureEventsCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                captureEventsCBActionPerformed(evt);
            }
        });
        dvsPanel.add(captureEventsCB);

        bwPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        bwPanel.setPreferredSize(new java.awt.Dimension(500, 139));

        bwEstLabel.setText("Estimated maximum pixel bandwidth");
        bwEstLabel.setToolTipText("<html> Estimated DVS cutoff frequency based solely on source follower bias current.<p> Does not account for finite photoreceptor bandwidth either from photoreceptor bias or low photocurrent.");

        bwEstTF.setEditable(false);
        bwEstTF.setColumns(12);
        bwEstTF.setToolTipText("<html> Estimated DVS cutoff frequency based solely on source follower bias current.\n<p> Does not account for finite photoreceptor bandwidth either from photoreceptor bias or low photocurrent.\n<p> Assumes source follower runs in subthreshold, so will only be valid for bias curreents below the specific current");

        javax.swing.GroupLayout bwEstimatePanelLayout = new javax.swing.GroupLayout(bwEstimatePanel);
        bwEstimatePanel.setLayout(bwEstimatePanelLayout);
        bwEstimatePanelLayout.setHorizontalGroup(
            bwEstimatePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(bwEstimatePanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(bwEstLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bwEstTF, javax.swing.GroupLayout.PREFERRED_SIZE, 276, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(182, 182, 182))
        );
        bwEstimatePanelLayout.setVerticalGroup(
            bwEstimatePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(bwEstimatePanelLayout.createSequentialGroup()
                .addGroup(bwEstimatePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bwEstLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(bwEstTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        bandwidthTweaker.setToolTipText("<html>Adjust the source follower bandwidth by changing PRSf bias current.\n<p>\nFor minimum shot noise under low illumination, the photoreceptor bias should be large and the bandwidth should be limited\nby the source follower buffer. See <a href=\"https://arxiv.org/abs/2304.04019\">Optimal biasing and physical limits of DVS event noise</a>. ");
        bandwidthTweaker.setLessDescription("Slower");
        bandwidthTweaker.setMinimumSize(new java.awt.Dimension(80, 30));
        bandwidthTweaker.setMoreDescription("Faster");
        bandwidthTweaker.setName("Adjusts maximum photoreceptor bandwidth"); // NOI18N
        bandwidthTweaker.setPreferredSize(new java.awt.Dimension(250, 47));
        bandwidthTweaker.setTweakDescription("");
        bandwidthTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                bandwidthTweakerStateChanged(evt);
            }
        });

        javax.swing.GroupLayout bwPanelLayout = new javax.swing.GroupLayout(bwPanel);
        bwPanel.setLayout(bwPanelLayout);
        bwPanelLayout.setHorizontalGroup(
            bwPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(bwPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(bwPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(bwEstimatePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 481, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bandwidthTweaker, javax.swing.GroupLayout.PREFERRED_SIZE, 481, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(11, Short.MAX_VALUE))
        );
        bwPanelLayout.setVerticalGroup(
            bwPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, bwPanelLayout.createSequentialGroup()
                .addContainerGap(24, Short.MAX_VALUE)
                .addComponent(bandwidthTweaker, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bwEstimatePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        dvsPanel.add(bwPanel);

        thrPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        thrPanel.setPreferredSize(new java.awt.Dimension(500, 202));

        thresholdTweaker.setToolTipText("<html>Adjusts DVS event temporal contrast thresholds magnitude <br>\nby changing current ratios diffOn/diff and diffOff/diff. \n<p>Limited to only adjusting fine current value; <br>\nwhen this limit is reached, user must manually change the coarse current value.");
        thresholdTweaker.setLessDescription("Lower & More events");
        thresholdTweaker.setMinimumSize(new java.awt.Dimension(80, 30));
        thresholdTweaker.setMoreDescription("Higher &  Fewer events");
        thresholdTweaker.setName("Adjusts event threshold"); // NOI18N
        thresholdTweaker.setPreferredSize(new java.awt.Dimension(250, 47));
        thresholdTweaker.setTweakDescription("");
        thresholdTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                thresholdTweakerStateChanged(evt);
            }
        });

        onOffBalanceTweaker.setToolTipText("<html>Adjusts DVS event temporal contrast thresholds balance<br>\nby changing current ratios diffOn/diff and diffOff/diff. \n<p>Limited to only adjusting fine current value; <br>\nwhen this limit is reached, user must manually change the coarse current value.");
        onOffBalanceTweaker.setLessDescription("More Off events");
        onOffBalanceTweaker.setMinimumSize(new java.awt.Dimension(80, 30));
        onOffBalanceTweaker.setMoreDescription("More On events");
        onOffBalanceTweaker.setName("Adjusts balance bewteen On and Off events"); // NOI18N
        onOffBalanceTweaker.setPreferredSize(new java.awt.Dimension(250, 47));
        onOffBalanceTweaker.setTweakDescription("");
        onOffBalanceTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                onOffBalanceTweakerStateChanged(evt);
            }
        });

        jPanel4.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        jLabel9.setText("Est. DVS thresholds:");
        jLabel9.setToolTipText("<html>Displays computed values of DVS event temporal contrast thresholds<br> \nbased on paper\n<a href=\"https://ieeexplore.ieee.org/document/7962235\">Temperature and\n Parasitic Photocurrent <br> Effects in Dynamic Vision Sensors, <br>Y Nozaki, T\nDelbruck. <br>IEEE Trans. on Electron Devices, 2018</a>");
        jPanel4.add(jLabel9);

        jLabel7.setText("ON");
        jPanel4.add(jLabel7);

        onThrTF.setEditable(false);
        onThrTF.setColumns(12);
        onThrTF.setToolTipText("Estimated DVS  temporal contrast threshold  (log base e units)");
        jPanel4.add(onThrTF);

        jLabel8.setText("OFF");
        jPanel4.add(jLabel8);

        offThrTF.setEditable(false);
        offThrTF.setColumns(12);
        offThrTF.setToolTipText("Estimated DVS  temporal contrast threshold  (log base e units)");
        offThrTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                offThrTFActionPerformed(evt);
            }
        });
        jPanel4.add(offThrTF);

        jLabel10.setText("ON+OFF");
        jLabel10.setToolTipText("difference ON to OFF thresholds (nominal balance)");
        jPanel4.add(jLabel10);

        onMinusOffTF.setEditable(false);
        onMinusOffTF.setColumns(7);
        jPanel4.add(onMinusOffTF);

        javax.swing.GroupLayout thrPanelLayout = new javax.swing.GroupLayout(thrPanel);
        thrPanel.setLayout(thrPanelLayout);
        thrPanelLayout.setHorizontalGroup(
            thrPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, thrPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(thrPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(thrPanelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, thrPanelLayout.createSequentialGroup()
                        .addGroup(thrPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(onOffBalanceTweaker, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 472, Short.MAX_VALUE)
                            .addComponent(thresholdTweaker, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addGap(231, 231, 231))
        );
        thrPanelLayout.setVerticalGroup(
            thrPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(thrPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(thresholdTweaker, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(onOffBalanceTweaker, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(12, Short.MAX_VALUE))
        );

        dvsPanel.add(thrPanel);

        refrPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        refrPanel.setPreferredSize(new java.awt.Dimension(500, 118));

        maxFiringRateTweaker.setToolTipText("<html>Adjusts the refractory period after each event by changing Refr bias.\n<p>This current sets the rate at which the reset switch voltage is recharged.\n<p>Limited to changing fine current value. For more control, adjust these currents directly.");
        maxFiringRateTweaker.setLessDescription("Slower");
        maxFiringRateTweaker.setMinimumSize(new java.awt.Dimension(80, 30));
        maxFiringRateTweaker.setMoreDescription("Faster");
        maxFiringRateTweaker.setName("Adjusts maximum pixel firing rate (1/refactory period)"); // NOI18N
        maxFiringRateTweaker.setPreferredSize(new java.awt.Dimension(250, 47));
        maxFiringRateTweaker.setTweakDescription("");
        maxFiringRateTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                maxFiringRateTweakerStateChanged(evt);
            }
        });

        refrPerLabel.setText("Estimated refractory period");

        refrPerTF.setEditable(false);
        refrPerTF.setColumns(12);
        refrPerTF.setToolTipText("Estimated DVS  temporal contrast threshold  (log base e units)");
        refrPerTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refrPerTFActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout refrEstimatePanelLayout = new javax.swing.GroupLayout(refrEstimatePanel);
        refrEstimatePanel.setLayout(refrEstimatePanelLayout);
        refrEstimatePanelLayout.setHorizontalGroup(
            refrEstimatePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(refrEstimatePanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(refrPerLabel)
                .addGap(5, 5, 5)
                .addComponent(refrPerTF, javax.swing.GroupLayout.PREFERRED_SIZE, 348, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        refrEstimatePanelLayout.setVerticalGroup(
            refrEstimatePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(refrEstimatePanelLayout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addComponent(refrPerLabel))
            .addGroup(refrEstimatePanelLayout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(refrPerTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        javax.swing.GroupLayout refrPanelLayout = new javax.swing.GroupLayout(refrPanel);
        refrPanel.setLayout(refrPanelLayout);
        refrPanelLayout.setHorizontalGroup(
            refrPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(refrPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(refrPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(refrEstimatePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 481, Short.MAX_VALUE)
                    .addComponent(maxFiringRateTweaker, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(17, Short.MAX_VALUE))
        );
        refrPanelLayout.setVerticalGroup(
            refrPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(refrPanelLayout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(maxFiringRateTweaker, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(refrEstimatePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(9, Short.MAX_VALUE))
        );

        dvsPanel.add(refrPanel);

        apsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Frames", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N
        apsPanel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N

        jLabel1.setText("Target Frame rate (Hz)");
        jLabel1.setToolTipText("Set the target frame rate; actual rate will be displayed on right and depends on quantization and possible other delays");

        fdSp.setModel(new javax.swing.SpinnerNumberModel(0.0f, 0.0f, null, 1.0f));
        fdSp.setToolTipText("Delay of starting new frame capture after last frame");
        fdSp.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                fdSpStateChanged(evt);
            }
        });

        jLabel2.setText("Exposure delay (ms)");

        edSp.setModel(new javax.swing.SpinnerNumberModel(0.0f, null, null, 1.0f));
        edSp.setToolTipText("The exposure delay; affects actual exposure time");
        edSp.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                edSpStateChanged(evt);
            }
        });

        autoExpCB.setText("Auto exposure");
        autoExpCB.setToolTipText("Automatically set pixel exposure delay. See AutoExposureController panel for full parameter set.");
        autoExpCB.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        autoExpCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        autoExpCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoExpCBActionPerformed(evt);
            }
        });

        jLabel3.setText("Auto-Shot kevents/frame");

        jLabel4.setText("Actual frame rate (Hz)");
        jLabel4.setToolTipText("The actual frame rate measured from camera event timing");

        jLabel5.setText("Expos. (ms)");

        fpsTF.setEditable(false);
        fpsTF.setToolTipText("Measured frame rate in Hz");

        exposMsTF.setEditable(false);
        exposMsTF.setToolTipText("Measured exposure time in ms");
        exposMsTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exposMsTFActionPerformed(evt);
            }
        });

        jLabel6.setText("Contrast");

        autoshotThresholdSp.setModel(new javax.swing.SpinnerNumberModel(0, 0, null, 1));
        autoshotThresholdSp.setToolTipText("<html>Set non-zero to automatically trigger APS frame captures every this many thousand DVS events. <br>For better control of automatic frame capture,<br> including the use of pre-filtering of the DVS events, use the filter ApsDvsAutoShooter.");
        autoshotThresholdSp.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                autoshotThresholdSpStateChanged(evt);
            }
        });

        contrastSp.setModel(new javax.swing.SpinnerNumberModel(Float.valueOf(1.0f), Float.valueOf(0.2f), Float.valueOf(5.0f), Float.valueOf(0.05f)));
        contrastSp.setToolTipText("Sets rendering contrast gain (1 is default). See Video Control panel for full set of controls.");
        contrastSp.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                contrastSpStateChanged(evt);
            }
        });

        autoContrastCB.setText("Auto contrast");
        autoContrastCB.setToolTipText("Uses DavisVideoContrastController to automatically set rendering contrast so that brightness (offset) and contrast (gain) scale min and max values to 0 and 1 respectively");
        autoContrastCB.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        autoContrastCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        autoContrastCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoContrastCBActionPerformed(evt);
            }
        });

        displayFramesCheckBox.setText("Display Frames");
        displayFramesCheckBox.setToolTipText("Enables display of APS imager output");
        displayFramesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                displayFramesCheckBoxActionPerformed(evt);
            }
        });

        histCB.setText("histogram");
        histCB.setToolTipText("Display hisogram of captured pixel values from ADC (reset - signal values)");
        histCB.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        histCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        histCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                histCBActionPerformed(evt);
            }
        });

        snapshotButton.setText("Take Snapshot");
        snapshotButton.setToolTipText("Triggers a single frame capture to onscreen buffer");
        snapshotButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                snapshotButtonActionPerformed(evt);
            }
        });

        captureFramesCheckBox.setText("Capture Frames");
        captureFramesCheckBox.setToolTipText("Enables capture of APS imager output (turns on ADC state machine)");
        captureFramesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                captureFramesCheckBoxActionPerformed(evt);
            }
        });

        glShutterCB.setText("Global shutter");
        glShutterCB.setToolTipText("Enables global (synchronous) electronic shutter. Unchecked enables rolling shutter readout.");
        glShutterCB.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        glShutterCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        glShutterCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                glShutterCBActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout apsPanelLayout = new javax.swing.GroupLayout(apsPanel);
        apsPanel.setLayout(apsPanelLayout);
        apsPanelLayout.setHorizontalGroup(
            apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(apsPanelLayout.createSequentialGroup()
                .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(apsPanelLayout.createSequentialGroup()
                        .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, apsPanelLayout.createSequentialGroup()
                                .addComponent(jLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(edSp))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, apsPanelLayout.createSequentialGroup()
                                .addGap(13, 13, 13)
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(fdSp, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel4)
                            .addComponent(jLabel5))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(fpsTF, javax.swing.GroupLayout.DEFAULT_SIZE, 71, Short.MAX_VALUE)
                            .addComponent(exposMsTF)))
                    .addGroup(apsPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(captureFramesCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(displayFramesCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(snapshotButton))
                    .addGroup(apsPanelLayout.createSequentialGroup()
                        .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(apsPanelLayout.createSequentialGroup()
                                .addGap(4, 4, 4)
                                .addComponent(jLabel6)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(contrastSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(autoContrastCB))
                            .addGroup(apsPanelLayout.createSequentialGroup()
                                .addComponent(jLabel3)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(autoshotThresholdSp, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(18, 18, 18)
                        .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(autoExpCB)
                            .addGroup(apsPanelLayout.createSequentialGroup()
                                .addComponent(glShutterCB)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(histCB)))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        apsPanelLayout.setVerticalGroup(
            apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(apsPanelLayout.createSequentialGroup()
                .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(apsPanelLayout.createSequentialGroup()
                        .addGap(4, 4, 4)
                        .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(captureFramesCheckBox)
                            .addComponent(displayFramesCheckBox)))
                    .addComponent(snapshotButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(fdSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4)
                    .addComponent(fpsTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(edSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5)
                    .addComponent(exposMsTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(autoshotThresholdSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(autoExpCB))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(contrastSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(autoContrastCB)
                    .addComponent(histCB)
                    .addComponent(glShutterCB)))
        );

        snapshotButton.getAccessibleContext().setAccessibleName("snapshotButton");

        imuPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("IMU"));
        imuPanel.setToolTipText("Controls for Inertial Measurement Unit (Gyro/Accelometer)");

        imuVisibleCB.setText("Display");
        imuVisibleCB.setToolTipText("show the IMU output if it is available");
        imuVisibleCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        imuVisibleCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                imuVisibleCBActionPerformed(evt);
            }
        });

        imuEnabledCB.setText("Enable");
        imuEnabledCB.setToolTipText("show the IMU output if it is available");
        imuEnabledCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        imuEnabledCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                imuEnabledCBActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout imuPanelLayout = new javax.swing.GroupLayout(imuPanel);
        imuPanel.setLayout(imuPanelLayout);
        imuPanelLayout.setHorizontalGroup(
            imuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, imuPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(imuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(imuVisibleCB)
                    .addComponent(imuEnabledCB))
                .addContainerGap())
        );
        imuPanelLayout.setVerticalGroup(
            imuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(imuPanelLayout.createSequentialGroup()
                .addComponent(imuEnabledCB, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(imuVisibleCB, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        toggleDualViewJB.setMnemonic('D');
        toggleDualViewJB.setText("Dual view");
        toggleDualViewJB.setToolTipText("Toggles dual view of user-friendly and low level bias currents (to learn effects)");
        toggleDualViewJB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleDualViewJBActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dvsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 569, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(apsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(imuPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(toggleDualViewJB))))
                .addGap(10, 10, 10))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(apsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(toggleDualViewJB)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(imuPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(27, 27, 27)
                .addComponent(dvsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 532, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(158, Short.MAX_VALUE))
        );

        add(jPanel2, java.awt.BorderLayout.PAGE_START);
    }// </editor-fold>//GEN-END:initComponents

    private void toggleDualViewJBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleDualViewJBActionPerformed
        getConfig().setDualView(toggleDualViewJB.isSelected());
    }//GEN-LAST:event_toggleDualViewJBActionPerformed

    private void imuEnabledCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_imuEnabledCBActionPerformed
        getConfig().setImuEnabled(imuEnabledCB.isSelected());
    }//GEN-LAST:event_imuEnabledCBActionPerformed

    private void imuVisibleCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_imuVisibleCBActionPerformed
        getConfig().setDisplayImu(imuVisibleCB.isSelected());
    }//GEN-LAST:event_imuVisibleCBActionPerformed

    private void glShutterCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_glShutterCBActionPerformed
        ((DavisBaseCamera) chip).getDavisConfig().setGlobalShutter(glShutterCB.isSelected());
    }//GEN-LAST:event_glShutterCBActionPerformed

    private void captureFramesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_captureFramesCheckBoxActionPerformed
        getConfig().setCaptureFramesEnabled(captureFramesCheckBox.isSelected());
    }//GEN-LAST:event_captureFramesCheckBoxActionPerformed

    private void snapshotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_snapshotButtonActionPerformed
        chip.takeSnapshot();
    }//GEN-LAST:event_snapshotButtonActionPerformed

    private void histCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_histCBActionPerformed
        chip.setShowImageHistogram(histCB.isSelected());
    }//GEN-LAST:event_histCBActionPerformed

    private void displayFramesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_displayFramesCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_displayFramesCheckBoxActionPerformed

    private void autoContrastCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoContrastCBActionPerformed
        contrastController.setUseAutoContrast(autoContrastCB.isSelected());
    }//GEN-LAST:event_autoContrastCBActionPerformed

    private void contrastSpStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_contrastSpStateChanged
        if (contrastController.isUseAutoContrast()) {
            return; // don't update if only used for display of contrast
        }
        try {
            final float f = (float) contrastSp.getValue();
            contrastController.setContrast(f);
            contrastSp.setBackground(Color.GRAY);
        } catch (final Exception e) {
            DavisUserControlPanel.log.warning(e.toString());
            contrastSp.setBackground(Color.red);
        }
    }//GEN-LAST:event_contrastSpStateChanged

    private void autoshotThresholdSpStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_autoshotThresholdSpStateChanged
        try {
            chip.setAutoshotThresholdEvents((Integer) autoshotThresholdSp.getValue() << 10);
        } catch (final Exception e) {
            DavisUserControlPanel.log.warning(e.toString());
        }
    }//GEN-LAST:event_autoshotThresholdSpStateChanged

    private void exposMsTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exposMsTFActionPerformed
        try {
            getConfig().setExposureDelayMs((float) edSp.getValue());
        } catch (final Exception e) {
            DavisUserControlPanel.log.warning(e.toString());
        }
    }//GEN-LAST:event_exposMsTFActionPerformed

    private void autoExpCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoExpCBActionPerformed
        chip.setAutoExposureEnabled(autoExpCB.isSelected());
    }//GEN-LAST:event_autoExpCBActionPerformed

    private void edSpStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_edSpStateChanged
        try {
            getConfig().setExposureDelayMs((float) edSp.getValue());
            edSp.setBackground(Color.WHITE);
        } catch (final Exception e) {
            DavisUserControlPanel.log.warning(e.toString());
            edSp.setBackground(Color.RED);
        }
    }//GEN-LAST:event_edSpStateChanged

    private void fdSpStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_fdSpStateChanged
        try {
            getConfig().setFrameIntervalMs(1000.0f / (float) fdSp.getValue());
            fdSp.setBackground(Color.WHITE);
        } catch (final Exception e) {
            DavisUserControlPanel.log.warning(e.toString());
            fdSp.setBackground(Color.RED);
        }
    }//GEN-LAST:event_fdSpStateChanged

    private void refrPerTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refrPerTFActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_refrPerTFActionPerformed

    private void maxFiringRateTweakerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_maxFiringRateTweakerStateChanged
        getDvsTweaks().setMaxFiringRateTweak(maxFiringRateTweaker.getValue());
        setEstimatedRefractoryPeriod();
        setFileModified();
    }//GEN-LAST:event_maxFiringRateTweakerStateChanged

    private void offThrTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offThrTFActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_offThrTFActionPerformed

    private void onOffBalanceTweakerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_onOffBalanceTweakerStateChanged
        getDvsTweaks().setOnOffBalanceTweak(onOffBalanceTweaker.getValue());
        setEstimatedThresholdValues();
        setFileModified();
    }//GEN-LAST:event_onOffBalanceTweakerStateChanged

    private void thresholdTweakerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_thresholdTweakerStateChanged
        getDvsTweaks().setThresholdTweak(thresholdTweaker.getValue());
        setEstimatedThresholdValues();
        setFileModified();
    }//GEN-LAST:event_thresholdTweakerStateChanged

    private void bandwidthTweakerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_bandwidthTweakerStateChanged
        getDvsTweaks().setBandwidthTweak(bandwidthTweaker.getValue());
        setEstimatedBandwidth();
        setFileModified();
    }//GEN-LAST:event_bandwidthTweakerStateChanged

    private void captureEventsCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_captureEventsCBActionPerformed
        getConfig().setCaptureEvents(captureEventsCB.isSelected());
    }//GEN-LAST:event_captureEventsCBActionPerformed

    private void dvsContSpStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_dvsContSpStateChanged
        renderer.setColorScale((Integer) dvsContSp.getValue());
    }//GEN-LAST:event_dvsContSpStateChanged

    private void dvsGrayRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dvsGrayRBActionPerformed
        renderer.setColorMode(AEChipRenderer.ColorMode.GrayLevel);
    }//GEN-LAST:event_dvsGrayRBActionPerformed

    private void dvsRedGrRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dvsRedGrRBActionPerformed
        renderer.setColorMode(AEChipRenderer.ColorMode.RedGreen);
    }//GEN-LAST:event_dvsRedGrRBActionPerformed

    private void displayEventsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_displayEventsCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_displayEventsCheckBoxActionPerformed


    private void setEstimatedThresholdValues() {
        final float onThresholdLogE = apsDvsTweaks.getOnThresholdLogE();
        final float offThresholdLogE = apsDvsTweaks.getOffThresholdLogE();
        final float onPerCent = (float) (100 * (Math.exp(onThresholdLogE) - 1));
        final float offPerCent = (float) (100 * (Math.exp(offThresholdLogE) - 1));
        onThrTF.setText(String.format("%.3f e-folds (%.1f%%)", onThresholdLogE, onPerCent));
        offThrTF.setText(String.format("%.3f e-folds (%.1f%%)", offThresholdLogE, offPerCent));
        onMinusOffTF.setText(String.format("%.3f", onThresholdLogE + offThresholdLogE));
    }

    private void setEstimatedBandwidth() {
        final float bwHz = apsDvsTweaks.getPhotoreceptorSourceFollowerBandwidthHz();
        bwEstTF.setText(String.format("%sHz", engFmt.format(bwHz)));
    }

    private void setEstimatedRefractoryPeriod() {
        final float refrPer = apsDvsTweaks.getRefractoryPeriodS();
        refrPerTF.setText(String.format("%ss (%sHz max firing rate)", engFmt.format(refrPer), engFmt.format(1f / refrPer)));
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel apsPanel;
    private javax.swing.JCheckBox autoContrastCB;
    private javax.swing.JCheckBox autoExpCB;
    private javax.swing.JSpinner autoshotThresholdSp;
    private net.sf.jaer.biasgen.PotTweaker bandwidthTweaker;
    private javax.swing.JLabel bwEstLabel;
    private javax.swing.JTextField bwEstTF;
    private javax.swing.JPanel bwEstimatePanel;
    private javax.swing.JPanel bwPanel;
    private javax.swing.JCheckBox captureEventsCB;
    private javax.swing.JCheckBox captureFramesCheckBox;
    private javax.swing.JSpinner contrastSp;
    private javax.swing.JCheckBox displayEventsCheckBox;
    private javax.swing.JCheckBox displayFramesCheckBox;
    private javax.swing.ButtonGroup dvsColorButGrp;
    private javax.swing.JLabel dvsContLabel;
    private javax.swing.JSpinner dvsContSp;
    private javax.swing.JRadioButton dvsGrayRB;
    private javax.swing.JPanel dvsPanel;
    private javax.swing.JRadioButton dvsRedGrRB;
    private javax.swing.JSpinner edSp;
    private javax.swing.JTextField exposMsTF;
    private javax.swing.JSpinner fdSp;
    private javax.swing.JTextField fpsTF;
    private javax.swing.JCheckBox glShutterCB;
    private javax.swing.JCheckBox histCB;
    private javax.swing.JCheckBox imuEnabledCB;
    private javax.swing.JPanel imuPanel;
    private javax.swing.JCheckBox imuVisibleCB;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel4;
    private net.sf.jaer.biasgen.PotTweaker maxFiringRateTweaker;
    private javax.swing.JTextField offThrTF;
    private javax.swing.JTextField onMinusOffTF;
    private net.sf.jaer.biasgen.PotTweaker onOffBalanceTweaker;
    private javax.swing.JTextField onThrTF;
    private javax.swing.JPanel refrEstimatePanel;
    private javax.swing.JPanel refrPanel;
    private javax.swing.JLabel refrPerLabel;
    private javax.swing.JTextField refrPerTF;
    private javax.swing.JButton snapshotButton;
    private javax.swing.JPanel thrPanel;
    private net.sf.jaer.biasgen.PotTweaker thresholdTweaker;
    private javax.swing.JToggleButton toggleDualViewJB;
    // End of variables declaration//GEN-END:variables

    /**
     * @return the chip
     */
    public DavisChip getChip() {
        return chip;
    }

    /**
     * @return the getConfig()
     */
    public DavisDisplayConfigInterface getApsDvsConfig() {
        return getConfig();
    }

    /**
     * @return the apsDvsTweaks
     */
    public DVSTweaks getDvsTweaks() {
        return apsDvsTweaks;
    }

    /**
     * Handles Observable updates from Davis240Config, and inner classes
     * VideoControl and ApsReadoutControl
     *
     * @param o
     * @param arg
     */
    @Override
    public void update(final Observable o, final Object arg) {
        // updates to user friendly controls come from low level properties here
        if (o == ((DavisBaseCamera) chip).getDavisConfig().getExposureControlRegister()) {
            edSp.setValue(getConfig().getExposureDelayMs());
        } else if (o == ((DavisBaseCamera) chip).getDavisConfig().getFrameIntervalControlRegister()) {
            fdSp.setValue(1000.0f / getConfig().getFrameIntervalMs());
        } else if (o == videoControl) {
            displayFramesCheckBox.setSelected(getConfig().isDisplayFrames());
            displayEventsCheckBox.setSelected(getConfig().isDisplayEvents());
        } else if (o == contrastController) {
            autoContrastCB.setSelected(contrastController.isUseAutoContrast());
            contrastSp.setEnabled(!contrastController.isUseAutoContrast());
            contrastSp.setValue(contrastController.getContrast());
        }
    }

}
