/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import ch.unizh.ini.jaer.config.MuxControlPanel;
import ch.unizh.ini.jaer.config.OutputMap;
import ch.unizh.ini.jaer.config.boards.LatticeLogicConfig;
import ch.unizh.ini.jaer.config.cpld.CPLDByte;
import ch.unizh.ini.jaer.config.cpld.CPLDConfigValue;
import ch.unizh.ini.jaer.config.cpld.CPLDInt;
import ch.unizh.ini.jaer.config.fx2.PortBit;
import ch.unizh.ini.jaer.config.fx2.TriStateablePortBit;
import ch.unizh.ini.jaer.config.onchip.ChipConfigChain;
import ch.unizh.ini.jaer.config.onchip.OnchipConfigBit;
import ch.unizh.ini.jaer.config.onchip.OutputMux;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.border.TitledBorder;
import net.sf.jaer.biasgen.AddressedIPot;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.Masterbias;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotTweakerUtilities;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.ParameterControlPanel;
import net.sf.jaer.util.PropertyTooltipSupport;

/**
 * Base class for configuration of Davis cameras
 *
 * @author tobi
 */
class DavisConfig extends LatticeLogicConfig implements DavisDisplayConfigInterface, DavisTweaks, HasPreference {

    protected ShiftedSourceBiasCF ssn, ssp;
    protected JPanel configPanel;
    protected JTabbedPane configTabbedPane;
    // *********** FX2 *********************
    // portA
    protected PortBit runCpld = new PortBit(chip, "a3", "runCpld",
            "(A3) Set high to run CPLD which enables event capture, low to hold logic in reset", true);
    protected PortBit runAdc = new PortBit(chip, "c0", "runAdc",
            "(C0) High to run ADC. Bound together with adcEnabled.", true);
    // portE
    /**
     * Bias generator power down bit
     */
    protected PortBit powerDown = new PortBit(chip, "e2", "powerDown",
            "(E2) High to disable master bias and tie biases to default rails", false);
    protected PortBit nChipReset = new PortBit(chip, "e3", "nChipReset",
            "(E3) Low to reset AER circuits and hold pixels in reset, High to run", true); // shouldn't need to manipulate

    // *********** CPLD *********************
    // CPLD shift register contents specified here by CPLDInt and CPLDBit
    protected CPLDInt exposureControlRegister = new CPLDInt(
            chip,
            23,
            0,
            (1<<20)-1,
            "exposure",
            "global shutter exposure time between reset and readout phases; interpretation depends on whether rolling or global shutter readout is used.",
            0);
    protected CPLDInt colSettle = new CPLDInt(
            chip,
            39,
            24,
            (1<<7)-1,
            "colSettle",
            "time in 30MHz clock cycles to settle after column select before readout; allows all pixels in column to drive in parallel the row readout lines (like resSettle)",
            0);
    protected CPLDInt rowSettle = new CPLDInt(
            chip,
            55,
            40,
             (1<<6)-1,
           "rowSettle",
            "time in 30MHz clock cycles for pixel source follower to settle after each pixel's row select before ADC conversion; this is the fastest process of readout. In new logic value must be <64.",
            0);
    protected CPLDInt resSettle = new CPLDInt(
            chip,
            71,
            56,
            (1<<7)-1,
            "resSettle",
            "time in 30MHz clock cycles  to settle after column reset before readout; allows all pixels in column to drive in parallel the row readout lines (like colSettle)",
            0);
    protected CPLDInt frameDelay = new CPLDInt(chip, 87, 72,(1<<16)-1,"frameDelay",
            "time between two frames; scaling of this parameter depends on readout logic used", 0);
    /*
     * IMU registers, defined in logic IMUStateMachine
     * constant IMUInitAddr0 : std_logic_vector(7 downto 0) := "01101011"; -- ADDR: (0x6b) IMU power management register
     * and clock selection
     * constant IMUInitAddr1 : std_logic_vector(7 downto 0) := "00011010"; -- ADDR: (0x1A) DLPF (digital low pass
     * filter)
     * constant IMUInitAddr2 : std_logic_vector(7 downto 0) := "00011001"; -- ADDR: (0x19) Sample rate divider
     * constant IMUInitAddr3 : std_logic_vector(7 downto 0) := "00011011"; -- ADDR: (0x1B) Gyro Configuration: Full
     * Scale Range / Sensitivity
     * constant IMUInitAddr4 : std_logic_vector(7 downto 0) := "00011100"; -- ADDR: (0x1C) Accel Configuration: Full
     * Scale Range / Sensitivity
     */
    protected CPLDByte miscControlBits = new CPLDByte(chip, 95, 88, 255,"miscControlBits",
            "Bit0: IMU run (0=stop, 1=run). Bit1: Rolling shutter (0=global shutter, 1=rolling shutter). Bits2-7: unused ",
            (byte) 1);
    // See Invensense MPU-6100 IMU datasheet RM-MPU-6100A.pdf
    protected CPLDByte imu0PowerMgmtClkRegConfig = new CPLDByte(chip, 103, 96, 255, "imu0_PWR_MGMT_1",
            "1=Disable sleep, select x axis gyro as clock source", (byte) 1); // PWR_MGMT_1
    protected CPLDByte imu1DLPFConfig = new CPLDByte(chip, 111, 104, 255, "imu1_CONFIG",
            "1=digital low pass filter DLPF: FS=1kHz, Gyro 188Hz, 1.9ms delay ", (byte) 1); // CONFIG
    protected CPLDByte imu2SamplerateDividerConfig = new CPLDByte(chip, 119, 112, 255, "imu2_SMPLRT_DIV",
            "0=sample rate divider: 1 Khz sample rate when DLPF is enabled", (byte) 0); // SMPLRT_DIV
    protected CPLDByte imu3GyroConfig = new CPLDByte(chip, 127, 120, 255, "imu3_GYRO_CONFIG",
            "8=500 deg/s, 65.5 LSB per deg/s ", (byte) 8); // GYRO_CONFIG:
    protected CPLDByte imu4AccelConfig = new CPLDByte(chip, 135, 128, 255, "imu4_ACCEL_CONFIG",
            "ACCEL_CONFIG: Bits 4:3 code AFS_SEL. 8=4g, 8192 LSB per g", (byte) 8); // ACCEL_CONFIG:
    protected CPLDInt nullSettle = new CPLDInt(chip, 151, 136, (1<<5)-1, "nullSettle",
            "time to remain in NULL state between columns", 0);
    // these pots for DVSTweaks
    protected AddressedIPotCF diffOn, diffOff, refr, pr, sf, diff;

    // subclasses for controlling aspects of camera
    protected DavisConfig.VideoControl videoControl;
    protected DavisConfig.ApsReadoutControl apsReadoutControl;
    protected DavisConfig.ImuControl imuControl;
    protected int aeReaderFifoSize;
    protected int aeReaderNumBuffers;
    protected int autoShotThreshold; // threshold for triggering a new frame snapshot automatically
    // DVSTweasks from DVS128
    protected float bandwidth = 1;
    protected boolean debugControls = false;
    protected float maxFiringRate = 1;
    protected float onOffBalance = 1;
    protected float threshold = 1;
    protected boolean translateRowOnlyEvents;
    JPanel userFriendlyControls;

    public DavisConfig(Chip chip) {
        super(chip);
        setName("DavisConfig");

        // port bits
        addConfigValue(nChipReset);
        addConfigValue(powerDown);
        addConfigValue(runAdc);
        addConfigValue(runCpld);

        // cpld shift register stuff
        addConfigValue(exposureControlRegister);
        addConfigValue(resSettle);
        addConfigValue(rowSettle);
        addConfigValue(colSettle);
        addConfigValue(frameDelay);
        addConfigValue(nullSettle);

        addConfigValue(miscControlBits);

        // imu config values
        addConfigValue(imu0PowerMgmtClkRegConfig);
        addConfigValue(imu1DLPFConfig);
        addConfigValue(imu2SamplerateDividerConfig);
        addConfigValue(imu3GyroConfig);
        addConfigValue(imu4AccelConfig);

        // masterbias
        getMasterbias().setKPrimeNFet(55e-3f); // estimated from tox=42A, mu_n=670 cm^2/Vs // TODO fix for UMC18 process
        getMasterbias().setMultiplier(4); // =45 correct for dvs320
        getMasterbias().setWOverL(4.8f / 2.4f); // masterbias has nfet with w/l=2 at output
        getMasterbias().addObserver(this); // changes to masterbias come back to update() here

        // shifted sources (not used on SeeBetter10/11)
        ssn = new ShiftedSourceBiasCF(this);
        ssn.setSex(Pot.Sex.N);
        ssn.setName("SSN");
        ssn.setTooltipString("n-type shifted source that generates a regulated voltage near ground");
        ssn.addObserver(this);
        ssn.setAddress(21);

        ssp = new ShiftedSourceBiasCF(this);
        ssp.setSex(Pot.Sex.P);
        ssp.setName("SSP");
        ssp.setTooltipString("p-type shifted source that generates a regulated voltage near Vdd");
        ssp.addObserver(this);
        ssp.setAddress(20);

        ssBiases[1] = ssn;
        ssBiases[0] = ssp;

        setPotArray(new AddressedIPotArray(this));

        try {
            // private AddressedIPotCF diffOn, diffOff, refr, pr, sf, diff;
            diff = addAIPot("DiffBn,n,normal,differencing amp");
            diffOn = addAIPot("OnBn,n,normal,DVS brighter threshold");
            diffOff = addAIPot("OffBn,n,normal,DVS darker threshold");
            addAIPot("ApsCasEpc,p,cascode,cascode between APS und DVS");
            addAIPot("DiffCasBnc,n,cascode,differentiator cascode bias");
            addAIPot("ApsROSFBn,n,normal,APS readout source follower bias");
            addAIPot("LocalBufBn,n,normal,Local buffer bias"); // TODO what's this?
            addAIPot("PixInvBn,n,normal,Pixel request inversion static inverter bias");
            pr = addAIPot("PrBp,p,normal,Photoreceptor bias current");
            sf = addAIPot("PrSFBp,p,normal,Photoreceptor follower bias current (when used in pixel type)");
            refr = addAIPot("RefrBp,p,normal,DVS refractory period current");
            addAIPot("AEPdBn,n,normal,Request encoder pulldown static current");
            addAIPot("LcolTimeoutBn,n,normal,No column request timeout");
            addAIPot("AEPuXBp,p,normal,AER column pullup");
            addAIPot("AEPuYBp,p,normal,AER row pullup");
            addAIPot("IFThrBn,n,normal,Integrate and fire intensity neuron threshold");
            addAIPot("IFRefrBn,n,normal,Integrate and fire intensity neuron refractory period bias current");
            addAIPot("PadFollBn,n,normal,Follower-pad buffer bias current");
            addAIPot("apsOverflowLevel,n,normal,special overflow level bias ");
            addAIPot("biasBuffer,n,normal,special buffer bias ");
        } catch (Exception e) {
            throw new Error(e.toString());
        }

        // graphicOptions
        videoControl = new VideoControl();
        videoControl.addObserver(this);

        // on-chip configuration chain
        chipConfigChain = new DavisChipConfigChain(chip);
        chipConfigChain.addObserver(this);

        // control of log readout
        apsReadoutControl = new ApsReadoutControl();

        // imuControl
        imuControl = new ImuControl();

        setBatchEditOccurring(true);
        loadPreferences();
        setBatchEditOccurring(false);
        try {
            sendConfiguration(this);
        } catch (HardwareInterfaceException ex) {
            Logger.getLogger(DAVIS240BaseCamera.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     *
     * Overrides the default to built the custom control panel for configuring
     * the output multiplexers and many other chip, board and display controls.
     *
     * @return a new panel for controlling this chip and board configuration
     */
    @Override
    public JPanel buildControlPanel() {
        // if(displayControlPanel!=null) return displayControlPanel;
        configPanel = new JPanel();
        configPanel.setLayout(new BorderLayout());
        debugControls = chip.getPrefs().getBoolean("debugControls", false);
        // add a reset button on top of everything
        final Action resetChipAction = new AbstractAction("Reset chip") {
            {
                putValue(Action.SHORT_DESCRIPTION, "Resets the pixels and the AER logic momentarily");
            }

            @Override
            public void actionPerformed(ActionEvent evt) {
                resetChip();
            }
        };
        // add action to display user friendly controls toggled either next to expert controls or in another tab
        final Action toggleDebugControlsAction = new AbstractAction("Toggle debug controls") {
            {
                putValue(Action.SHORT_DESCRIPTION, "Toggles display of user friendly controls next to other tabbed panes for debugging");
            }

            @Override
            public void actionPerformed(ActionEvent evt) {
                toggleDebugControls();
            }
        };
        JPanel specialButtons = new JPanel();
        specialButtons.setLayout(new BoxLayout(specialButtons, BoxLayout.X_AXIS));
        specialButtons.add(new JButton(resetChipAction));
        specialButtons.add(new JButton(toggleDebugControlsAction));
        configTabbedPane = new JTabbedPane();
        setBatchEditOccurring(true); // stop updates on building panel
        configPanel.add(specialButtons, BorderLayout.NORTH);
        userFriendlyControls = new DavisUserControlPanel(chip);
        if (debugControls) {
            configPanel.add(userFriendlyControls, BorderLayout.EAST);
        } else {
            // user friendly control panel
            configTabbedPane.add("<html><strong><font color=\"red\">User-Friendly Controls", userFriendlyControls);
        }
        // graphics
        JPanel videoControlPanel = new JPanel();
        videoControlPanel.add(new JLabel("<html>Controls display of APS video frame data"));
        videoControlPanel.setLayout(new BoxLayout(videoControlPanel, BoxLayout.Y_AXIS));
        configTabbedPane.add("Video Control", videoControlPanel);
        videoControlPanel.add(new ParameterControlPanel(getVideoControl()));
        // biasgen
        JPanel combinedBiasShiftedSourcePanel = new JPanel();
        videoControlPanel.add(new JLabel("<html>Low-level control of on-chip bias currents and voltages. <p>These are only for experts!"));
        combinedBiasShiftedSourcePanel.setLayout(new BoxLayout(combinedBiasShiftedSourcePanel, BoxLayout.Y_AXIS));
        combinedBiasShiftedSourcePanel.add(super.buildControlPanel());
        combinedBiasShiftedSourcePanel.add(new ShiftedSourceControlsCF(ssn));
        combinedBiasShiftedSourcePanel.add(new ShiftedSourceControlsCF(ssp));
        configTabbedPane.addTab("Bias Current Control", combinedBiasShiftedSourcePanel);
        // muxes
        configTabbedPane.addTab("Debug Output MUX control", getChipConfigChain().buildMuxControlPanel());
        // aps readout
        JPanel apsReadoutPanel = new JPanel();
        apsReadoutPanel.add(new JLabel("<html>Low-level control of APS frame readout. <p>Hover over value fields to see explanations. <b>Incorrect settings will result in unusable output."));
        apsReadoutPanel.setLayout(new BoxLayout(apsReadoutPanel, BoxLayout.Y_AXIS));
        configTabbedPane.add("APS Readout Control", apsReadoutPanel);
        apsReadoutPanel.add(new ParameterControlPanel(getApsReadoutControl()));
        // IMU control
        JPanel imuControlPanel = new JPanel();
        imuControlPanel.add(new JLabel("<html>Low-level control of integrated inertial measurement unit."));
        imuControlPanel.setLayout(new BoxLayout(imuControlPanel, BoxLayout.Y_AXIS));
        configTabbedPane.add("IMU Control", imuControlPanel);
        imuControlPanel.add(new ImuControlPanel(this));
        // autoexposure
        if (chip instanceof DavisBaseCamera) {
            JPanel autoExposurePanel = new JPanel();
            autoExposurePanel.add(new JLabel("<html>Automatic exposure control.<p>The settings here determine when and by how much the exposure value should be changed. <p> The strategy followed attempts to avoid a sitation <b> where too many pixels are under- or over-exposed. Hover over entry fields to see explanations."));
            autoExposurePanel.setLayout(new BoxLayout(autoExposurePanel, BoxLayout.Y_AXIS));
            configTabbedPane.add("APS Autoexposure Control", autoExposurePanel);
            autoExposurePanel.add(new ParameterControlPanel(((DavisBaseCamera) chip).getAutoExposureController()));
        }
        // chip config
        JPanel chipConfigPanel = getChipConfigChain().getChipConfigPanel();
        configTabbedPane.addTab("Chip configuration", chipConfigPanel);
        configPanel.add(configTabbedPane, BorderLayout.CENTER);
        // only select panel after all added
        try {
            configTabbedPane.setSelectedIndex(chip.getPrefs().getInt("DavisBaseCamera.bgTabbedPaneSelectedIndex", 0));
        } catch (IndexOutOfBoundsException e) {
            configTabbedPane.setSelectedIndex(0);
        }
        // add listener to store last selected tab
        configTabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                tabbedPaneMouseClicked(evt);
            }
        });
        setBatchEditOccurring(false);
        return configPanel;
    }

    /**
     * @return the aeReaderFifoSize
     */
    public int getAeReaderFifoSize() {
        return aeReaderFifoSize;
    }

    /**
     * @return the aeReaderNumBuffers
     */
    public int getAeReaderNumBuffers() {
        return aeReaderNumBuffers;
    }

    public int getAutoShotEventThreshold() {
        return autoShotThreshold;
    }

    public float getBandwidthTweak() {
        return bandwidth;
    }

    public float getBrightness() {
        if (getVideoControl() == null) {
            return 0;
        }
        return getVideoControl().getBrightness();
    }

    public float getContrast() {
        if (getVideoControl() == null) {
            return 1;
        }
        return getVideoControl().getContrast();
    }

    public int getExposureDelayMs() {
        int ed = getExposureControlRegister().get() / 1000;
        return ed;
    }

    public int getFrameDelayMs() {
        int fd = frameDelay.get() / 1000;
        return fd;
    }

    public float getGamma() {
        if (getVideoControl() == null) {
            return 1;
        }
        return getVideoControl().getGamma();
    }

    public float getMaxFiringRateTweak() {
        return maxFiringRate;
    }

    public float getOnOffBalanceTweak() {
        return onOffBalance;
    }

    public float getThresholdTweak() {
        return threshold;
    }

    public boolean isCaptureEventsEnabled() {
        if (nChipReset == null) {
            return false; // only to handle initial call before we are fully constructed, when propertyChangeListeners
            // are called
        }
        return nChipReset.isSet();
    }

    public boolean isCaptureFramesEnabled() {
        if (getApsReadoutControl() == null) {
            return false;
        }
        return getApsReadoutControl().isAdcEnabled();
    }

    public boolean isDisplayEvents() {
        if (getVideoControl() == null) {
            return false;
        }
        return getVideoControl().isDisplayEvents();
    }

    public boolean isDisplayFrames() {
        if (getVideoControl() == null) {
            return false;
        }
        return getVideoControl().isDisplayFrames();
    }

    public boolean isDisplayImu() {
        return getImuControl().isDisplayImu();
    }

    public boolean isImuEnabled() {
        return getImuControl().isImuEnabled();
    }

    public boolean isTranslateRowOnlyEvents() {
        return translateRowOnlyEvents;
    }

    public boolean isUseAutoContrast() {
        if (getVideoControl() == null) {
            return false;
        }
        return getVideoControl().isUseAutoContrast();
    }

    @Override
    public void loadPreference() {
        super.loadPreference(); // To change body of generated methods, choose Tools | Templates.
        setAeReaderFifoSize(getChip().getPrefs().getInt("aeReaderFifoSize", 1 << 15));
        setAeReaderNumBuffers(getChip().getPrefs().getInt("aeReaderNumBuffers", 4));
        setTranslateRowOnlyEvents(getChip().getPrefs().getBoolean("translateRowOnlyEvents", false));
        setCaptureEvents(isCaptureEventsEnabled()); // just to call propertyChangeListener that sets GUI buttons
        setDisplayEvents(isDisplayEvents()); // just to call propertyChangeListener that sets GUI buttons
        setCaptureFramesEnabled(isCaptureFramesEnabled());
        setDisplayFrames(isDisplayFrames()); // calls GUI update listeners
    }

    /**
     * Momentarily puts the pixels and on-chip AER logic in reset and then
     * releases the reset.
     *
     */
    protected void resetChip() {
        log.info("resetting AER communication");
        nChipReset.set(false);
        nChipReset.set(true);
    }

    /**
     * @param aeReaderFifoSize the aeReaderFifoSize to set
     */
    public void setAeReaderFifoSize(int aeReaderFifoSize) {
        if (aeReaderFifoSize < (1 << 8)) {
            aeReaderFifoSize = 1 << 8;
        } else if (((aeReaderFifoSize) & (aeReaderFifoSize - 1)) != 0) {
            int newval = Integer.highestOneBit(aeReaderFifoSize - 1);
            log.warning("tried to set a non-power-of-two value " + aeReaderFifoSize + "; rounding down to nearest power of two which is " + newval);
            aeReaderFifoSize = newval;
        }
        this.aeReaderFifoSize = aeReaderFifoSize;
    }

    /**
     * @param aeReaderNumBuffers the aeReaderNumBuffers to set
     */
    public void setAeReaderNumBuffers(int aeReaderNumBuffers) {
        this.aeReaderNumBuffers = aeReaderNumBuffers;
    }

    public void setAutoShotEventThreshold(int threshold) {
        this.autoShotThreshold = threshold;
    }

    /**
     * Tweaks bandwidth around nominal value.
     *
     * @param val -1 to 1 range
     */
    public void setBandwidthTweak(float val) {
        if (val > 1) {
            val = 1;
        } else if (val < -1) {
            val = -1;
        }
        float old = bandwidth;
        if (old == val) {
            return;
        }
        // log.info("tweak bandwidth by " + val);
        bandwidth = val;
        final float MAX = 30;
        pr.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        sf.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        chip.getSupport().firePropertyChange(DVSTweaks.BANDWIDTH, old, val);
    }

    public void setBrightness(float brightness) {
        if (getVideoControl() == null) {
            return;
        }
        getVideoControl().setBrightness(brightness);
    }

    public void setCaptureEvents(boolean selected) {
        if (nChipReset == null) {
            return;
        }
        boolean old = nChipReset.isSet();
        nChipReset.set(selected);
        getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_CAPTURE_EVENTS_ENABLED, null, selected); // TODO have to
        // set null for
        // old value
        // because
        // nChipReset is
        // a bit but it
        // is not linked
        // to listeners
        // like button,
        // so when
        // preferences
        // are loaded
        // and new value
        // is set, then
        // it may be
        // that the
        // button is not
        // updated
    }

    public void setCaptureFramesEnabled(boolean yes) {
        if (getApsReadoutControl() == null) {
            return;
        }
        getApsReadoutControl().setAdcEnabled(yes);
    }

    public void setContrast(float contrast) {
        if (getVideoControl() == null) {
            return;
        }
        getVideoControl().setContrast(contrast);
    }

    public void setDisplayEvents(boolean displayEvents) {
        if (getVideoControl() == null) {
            return;
        }
        getVideoControl().setDisplayEvents(displayEvents);
    }

    public void setDisplayFrames(boolean displayFrames) {
        if (getVideoControl() == null) {
            return;
        }
        getVideoControl().setDisplayFrames(displayFrames);
    }

    public void setDisplayImu(boolean yes) {
        getImuControl().setDisplayImu(yes);
    }

    public void setExposureDelayMs(int ms) {
        int exp = ms * 1000;
        getExposureControlRegister().set(exp);
    }

    public void setFrameDelayMs(int ms) {
        int fd = ms * 1000;
        frameDelay.set(fd);
    }

    public void setGamma(float gamma) {
        if (getVideoControl() == null) {
            return;
        }
        getVideoControl().setGamma(gamma);
    }

    public void setImuEnabled(boolean yes) {
        getImuControl().setImuEnabled(yes);
    }

    /**
     * Tweaks max firing rate (refractory period), larger is shorter refractory
     * period.
     *
     * @param val -1 to 1 range
     */
    public void setMaxFiringRateTweak(float val) {
        if (val > 1) {
            val = 1;
        } else if (val < -1) {
            val = -1;
        }
        float old = maxFiringRate;
        if (old == val) {
            return;
        }
        maxFiringRate = val;
        final float MAX = 300;
        refr.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        chip.getSupport().firePropertyChange(DVSTweaks.MAX_FIRING_RATE, old, val);
    }

    /**
     * Tweaks balance of on/off events. Increase for more ON events.
     *
     * @param val -1 to 1 range.
     */
    public void setOnOffBalanceTweak(float val) {
        if (val > 1) {
            val = 1;
        } else if (val < -1) {
            val = -1;
        }
        float old = onOffBalance;
        if (old == val) {
            return;
        }
        onOffBalance = val;
        final float MAX = 10;
        diff.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        chip.getSupport().firePropertyChange(DVSTweaks.ON_OFF_BALANCE, old, val);
    }

    /**
     * Tweaks threshold, larger is higher threshold.
     *
     * @param val -1 to 1 range
     */
    public void setThresholdTweak(float val) {
        if (val > 1) {
            val = 1;
        } else if (val < -1) {
            val = -1;
        }
        float old = threshold;
        if (old == val) {
            return;
        }
        final float MAX = 10;
        threshold = val;
        diffOn.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        diffOff.changeByRatioFromPreferred(1 / PotTweakerUtilities.getRatioTweak(val, MAX));
        chip.getSupport().firePropertyChange(DVSTweaks.THRESHOLD, old, val);
    }

    /**
     * If set, then row-only events are transmitted to raw packets from USB
     * interface
     *
     * @param translateRowOnlyEvents true to translate these parasitic events.
     */
    public void setTranslateRowOnlyEvents(boolean translateRowOnlyEvents) {
        boolean old = this.translateRowOnlyEvents;
        this.translateRowOnlyEvents = translateRowOnlyEvents;
        getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_TRANSLATE_ROW_ONLY_EVENTS, old, this.translateRowOnlyEvents);
        if ((getHardwareInterface() != null) && (getHardwareInterface() instanceof CypressFX3)) {
            // Translate Row-only Events is now in the logic.
            try {
                ((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_DVS, (short) 6, (translateRowOnlyEvents) ? (0) : (1));
            } catch (HardwareInterfaceException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public void setUseAutoContrast(boolean useAutoContrast) {
        if (getVideoControl() == null) {
            return;
        }
        getVideoControl().setUseAutoContrast(useAutoContrast);
    }

    @Override
    public void storePreference() {
        super.storePreference(); // To change body of generated methods, choose Tools | Templates.
        getChip().getPrefs().putInt("aeReaderFifoSize", aeReaderFifoSize);
        getChip().getPrefs().putInt("aeReaderNumBuffers", aeReaderNumBuffers);
        getChip().getPrefs().putBoolean("translateRowOnlyEvents", translateRowOnlyEvents);
    }

    protected void tabbedPaneMouseClicked(MouseEvent evt) {
        chip.getPrefs().putInt("DavisBaseCamera.bgTabbedPaneSelectedIndex", configTabbedPane.getSelectedIndex());
    }

    protected void toggleDebugControls() {
        if (debugControls) {
            configPanel.remove(userFriendlyControls);
        } else {
            configTabbedPane.remove(userFriendlyControls);
        }
        debugControls = !debugControls;
        chip.getPrefs().putBoolean("debugControls", debugControls);
        if (debugControls) {
            configPanel.add(userFriendlyControls, BorderLayout.EAST);
        } else {
            // user friendly control panel
            configTabbedPane.add("User-Friendly Controls", userFriendlyControls);
            configTabbedPane.setSelectedComponent(userFriendlyControls);
        }
    }

    /**
     * The central point for communication with HW from biasgen. All objects in
     * SeeBetterConfig are Observables and addConfigValue SeeBetterConfig.this
     * as Observer. They then call notifyObservers when their state changes.
     * Objects such as ADC store preferences for ADC, and update should update
     * the hardware registers accordingly.
     *
     * @param observable IPot, Scanner, etc
     * @param object notifyChange - not used at present
     */
    @Override
    public synchronized void update(Observable observable, Object object) {
        // thread safe to ensure gui cannot
        // retrigger this while it is sending
        // something
        // sends a vendor request depending on type of update
        // vendor request is always VR_CONFIG
        // value is the type of update
        // index is sometimes used for 16 bitmask updates
        // bytes are the rest of data
        if (isBatchEditOccurring()) {
            return;
        }
        // log.info("update with " + observable);
        try {
            if ((observable instanceof IPot) || (observable instanceof VPot)) {
                // must send all of the onchip shift
                // register values to replace shift
                // register contents
                sendOnChipConfig();
            } else if ((observable instanceof OutputMux) || (observable instanceof OnchipConfigBit)) {
                sendOnChipConfigChain();
            } else if (observable instanceof ShiftedSourceBiasCF) {
                sendOnChipConfig();
            } else if (observable instanceof ChipConfigChain) {
                sendOnChipConfigChain();
            } else if (observable instanceof Masterbias) {
                powerDown.set(getMasterbias().isPowerDownEnabled());
            } else if (observable instanceof TriStateablePortBit) {
                // tristateable should come first before configbit
                // since it is subclass
                TriStateablePortBit b = (TriStateablePortBit) observable;
                byte[] bytes = {(byte) ((b.isSet() ? (byte) 1 : (byte) 0) | (b.isHiZ() ? (byte) 2 : (byte) 0))};
                sendFx2ConfigCommand(CMD_SETBIT, b.getPortbit(), bytes); // sends value=CMD_SETBIT, index=portbit with
                // (port(b=0,d=1,e=2)<<8)|bitmask(e.g.
                // 00001000) in MSB/LSB, byte[0]= OR of
                // value (1,0), hiZ=2/0, bit is set if
                // tristate, unset if driving port
            } else if (observable instanceof PortBit) {
                PortBit b = (PortBit) observable;
                byte[] bytes = {b.isSet() ? (byte) 1 : (byte) 0};
                sendFx2ConfigCommand(CMD_SETBIT, b.getPortbit(), bytes); // sends value=CMD_SETBIT, index=portbit with
                // (port(b=0,d=1,e=2)<<8)|bitmask(e.g.
                // 00001000) in MSB/LSB, byte[0]=value (1,0)
            } else if (observable instanceof CPLDConfigValue) {
                sendCPLDConfig();
            } else if (observable instanceof AddressedIPot) {
                sendAIPot((AddressedIPot) observable);
            } else {
                super.update(observable, object); // super (SeeBetterConfig) handles others, e.g. masterbias
            }
        } catch (HardwareInterfaceException e) {
            log.warning("On update() caught " + e.toString());
        }
    }

    /**
     * Controls the APS intensity readout by wrapping the relevant bits
     */
    public class ApsReadoutControl extends Observable implements Observer, HasPropertyTooltips {

        PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();

        public ApsReadoutControl() {
            super();
            rowSettle.addObserver(this);
            colSettle.addObserver(this);
            getExposureControlRegister().addObserver(this);
            resSettle.addObserver(this);
            frameDelay.addObserver(this);
            runAdc.addObserver(this);
            // TODO awkward renaming of properties here due to wrongly named delegator methods
            tooltipSupport.setPropertyTooltip("adcEnabled", runAdc.getDescription());
            tooltipSupport.setPropertyTooltip("rowSettleCC", rowSettle.getDescription());
            tooltipSupport.setPropertyTooltip("colSettleCC", colSettle.getDescription());
            tooltipSupport.setPropertyTooltip("exposureDelayUS", getExposureControlRegister().getDescription());
            tooltipSupport.setPropertyTooltip("resSettleCC", resSettle.getDescription());
            tooltipSupport.setPropertyTooltip("frameDelayUS", frameDelay.getDescription());
            nullSettle.addObserver(this);
            tooltipSupport.setPropertyTooltip("nullSettleCC", nullSettle.getDescription());
            tooltipSupport.setPropertyTooltip("globalShutterMode", "Has no effect on Davis240a camera. On Davis240b/c cameras, enables global shutter readout. If disabled, enables rolling shutter readout.");
        }

        public boolean isAdcEnabled() {
            return runAdc.isSet();
        }

        public void setAdcEnabled(boolean yes) {
            boolean oldval = runAdc.isSet();
            runAdc.set(yes);
            // TODO we must always call listeners because by loading prefs, we maybe have changed runAdc but not been
            // informed of those changes, because
            // we are not registered directly as listeners on the bit itself....
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_CAPTURE_FRAMES_ENABLED, null, runAdc.isSet());
            // if (oldval != yes) {
            setChanged();
            notifyObservers(); // inform ParameterControlPanel
            // }
        }

        public boolean isGlobalShutterMode() {
            return (miscControlBits.get() & 2) == 0; // bit clear is global shutter, bit set is rolling shutter
        }

        public void setGlobalShutterMode(boolean yes) {
            int oldval = miscControlBits.get();
            boolean oldbool = isGlobalShutterMode();
            int newval = (oldval & (~2)) | (yes ? 0 : 2); // set bit1=1 to select rolling shutter mode, 0 for global
            // shutter mode
            miscControlBits.set(newval);
            // Update chip config chain.
            ((DavisConfig.DavisChipConfigChain) getChipConfigChain()).globalShutter.set(yes);
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_GLOBAL_SHUTTER_MODE_ENABLED, oldbool, yes);
            setChanged();
            notifyObservers(); // inform ParameterControlPanel
        }

        public void setColSettleCC(int cc) {
            colSettle.set(cc);
        }

        public void setRowSettleCC(int cc) {
            rowSettle.set(cc);
        }

        public void setResSettleCC(int cc) {
            resSettle.set(cc);
        }

        public void setFrameDelayUS(int cc) {
            frameDelay.set(cc);
        }

        public void setExposureDelayUS(int cc) {
            getExposureControlRegister().set(cc);
        }

        public void setNullSettleCC(int cc) {
            nullSettle.set(cc);
        }

        public int getColSettleCC() {
            return colSettle.get();
        }

        public int getRowSettleCC() {
            return rowSettle.get();
        }

        public int getResSettleCC() {
            return resSettle.get();
        }

        public int getFrameDelayUS() {
            return frameDelay.get();
        }

        public int getExposureDelayUS() {
            return getExposureControlRegister().get();
        }

        public int getNullSettleCC() {
            return nullSettle.get();
        }

        @Override
        public void update(Observable o, Object arg) {
            if (o == runAdc) {
                getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_CAPTURE_FRAMES_ENABLED, null, runAdc.isSet());
            }
        }

        @Override
        public String getPropertyTooltip(String propertyName) {
            return tooltipSupport.getPropertyTooltip(propertyName);
        }
    }

    /**
     * IMU control of Invensense IMU-6100A, encapsulated here.
     */
    public class ImuControl extends Observable implements HasPropertyTooltips, HasPreference, PreferenceChangeListener {

        PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();
        private ImuGyroScale imuGyroScale;
        private ImuAccelScale imuAccelScale;
        private boolean displayImuEnabled;

        public ImuControl() {
            super();
            // imu0PowerMgmtClkRegConfig.addObserver(this);
            // imu1DLPFConfig.addObserver(this);
            // imu2SamplerateDividerConfig.addObserver(this);
            // imu3GyroConfig.addObserver(this);
            // imu4AccelConfig.addObserver(this);
            // TODO awkward renaming of properties here due to wrongly named delegator methods
            hasPreferenceList.add(this);
            loadPreference();
            tooltipSupport.setPropertyTooltip("imu0", imu0PowerMgmtClkRegConfig.getDescription());
            tooltipSupport.setPropertyTooltip("imu1", imu1DLPFConfig.getDescription());
            tooltipSupport.setPropertyTooltip("imu2", imu2SamplerateDividerConfig.getDescription());
            tooltipSupport.setPropertyTooltip("imu3", imu3GyroConfig.getDescription());
            tooltipSupport.setPropertyTooltip("imu4", imu4AccelConfig.getDescription());
            IMUSample.setFullScaleGyroDegPerSec(imuGyroScale.fullScaleDegPerSec);
            IMUSample.setGyroSensitivityScaleFactorDegPerSecPerLsb(1 / imuGyroScale.scaleFactorLsbPerDegPerSec);
            IMUSample.setFullScaleAccelG(imuAccelScale.fullScaleG);
            IMUSample.setAccelSensitivityScaleFactorGPerLsb(1 / imuAccelScale.scaleFactorLsbPerG);
            chip.getPrefs().addPreferenceChangeListener(this);
        }

        public boolean isImuEnabled() {
            return (miscControlBits.get() & 1) == 1;
        }

        public void setImuEnabled(boolean yes) {
            boolean old = (miscControlBits.get() & 1) == 1;
            int oldval = miscControlBits.get();
            int newval = (oldval & (~1)) | (yes ? 1 : 0);
            miscControlBits.set(newval);
            getSupport().firePropertyChange(PROPERTY_IMU_ENABLED, old, yes);
        }

        /**
         * Register 26: CONFIG, digital low pass filter setting DLPF_CFG
         */
        public void setDLPF(int dlpf) {
            if ((dlpf < 0) || (dlpf > 6)) {
                throw new IllegalArgumentException("dlpf=" + dlpf + " is outside allowed range 0-6");
            }
            int old = imu1DLPFConfig.get() & 7;
            int oldval = imu1DLPFConfig.get();
            int newval = (oldval & (~7)) | (dlpf);
            imu1DLPFConfig.set(newval);
            activateNewRegisterValues();
            getSupport().firePropertyChange(PROPERTY_IMU_DLPF_CHANGED, old, dlpf);
        }

        public int getDLPF() {
            return imu1DLPFConfig.get() & 7;
        }

        /**
         * Register 27: Sample rate divider
         */
        public void setSampleRateDivider(int srd) {
            if ((srd < 0) || (srd > 255)) {
                throw new IllegalArgumentException("sampleRateDivider=" + srd + " is outside allowed range 0-255");
            }
            int old = imu2SamplerateDividerConfig.get();
            imu2SamplerateDividerConfig.set(srd & 255);
            activateNewRegisterValues();
            getSupport().firePropertyChange(PROPERTY_IMU_SAMPLE_RATE_CHANGED, old, srd);
        }

        public int getSampleRateDivider() {
            return imu2SamplerateDividerConfig.get();
        }

        public ImuGyroScale getGyroScale() {
            return imuGyroScale;
        }

        public void setGyroScale(ImuGyroScale scale) {
            ImuGyroScale old = this.imuGyroScale;
            this.imuGyroScale = scale;
            setFS_SEL(imuGyroScale.fs_sel);
            IMUSample.setFullScaleGyroDegPerSec(imuGyroScale.fullScaleDegPerSec);
            IMUSample.setGyroSensitivityScaleFactorDegPerSecPerLsb(1 / imuGyroScale.scaleFactorLsbPerDegPerSec);
            getSupport().firePropertyChange(PROPERTY_IMU_GYRO_SCALE_CHANGED, old, this.imuGyroScale);
        }

        /**
         * @return the imuAccelScale
         */
        public ImuAccelScale getAccelScale() {
            return imuAccelScale;
        }

        /**
         * @param imuAccelScale the imuAccelScale to set
         */
        public void setAccelScale(ImuAccelScale scale) {
            ImuAccelScale old = this.imuAccelScale;
            this.imuAccelScale = scale;
            setAFS_SEL(imuAccelScale.afs_sel);
            IMUSample.setFullScaleAccelG(imuAccelScale.fullScaleG);
            IMUSample.setAccelSensitivityScaleFactorGPerLsb(1 / imuAccelScale.scaleFactorLsbPerG);
            getSupport().firePropertyChange(PROPERTY_IMU_ACCEL_SCALE_CHANGED, old, this.imuAccelScale);
        }

        // accel scale bits
        private void setAFS_SEL(int val) {
            // AFS_SEL bits are bits 4:3 in accel register
            if ((val < 0) || (val > 3)) {
                throw new IllegalArgumentException("value " + val + " is outside range 0-3");
            }
            int oldval = imu4AccelConfig.get();
            int newval = (oldval & (~(3 << 3))) | (val << 3);
            setImu4(newval);
        }

        // gyro scale bits
        private void setFS_SEL(int val) {
            // AFS_SEL bits are bits 4:3 in accel register
            if ((val < 0) || (val > 3)) {
                throw new IllegalArgumentException("value " + val + " is outside range 0-3");
            }
            int oldval = imu3GyroConfig.get();
            int newval = (oldval & (~(3 << 3))) | (val << 3);
            setImu3(newval);
        }

        public boolean isDisplayImu() {
            return displayImuEnabled;
        }

        public void setDisplayImu(boolean yes) {
            boolean old = this.displayImuEnabled;
            this.displayImuEnabled = yes;
            getSupport().firePropertyChange(PROPERTY_IMU_DISPLAY_ENABLED, old, displayImuEnabled);
        }

        private void setImu0(int value) throws IllegalArgumentException {
            imu0PowerMgmtClkRegConfig.set(value);
            activateNewRegisterValues();
        }

        private int getImu0() {
            return imu0PowerMgmtClkRegConfig.get();
        }

        private void setImu1(int value) throws IllegalArgumentException {
            imu1DLPFConfig.set(value);
            activateNewRegisterValues();
        }

        private int getImu1() {
            return imu1DLPFConfig.get();
        }

        private void setImu2(int value) throws IllegalArgumentException {
            imu2SamplerateDividerConfig.set(value);
            activateNewRegisterValues();
        }

        private int getImu2() {
            return imu2SamplerateDividerConfig.get();
        }

        private void setImu3(int value) throws IllegalArgumentException {
            imu3GyroConfig.set(value);
            activateNewRegisterValues();
        }

        private int getImu3() {
            return imu3GyroConfig.get();
        }

        private void setImu4(int value) throws IllegalArgumentException {
            imu4AccelConfig.set(value);
            activateNewRegisterValues();
        }

        private int getImu4() {
            return imu4AccelConfig.get();
        }

        private void activateNewRegisterValues() {
            if (isImuEnabled()) {
                setImuEnabled(false);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                setImuEnabled(true);
            }
        }

        @Override
        public String getPropertyTooltip(String propertyName) {
            return tooltipSupport.getPropertyTooltip(propertyName);
        }

        @Override
        public final void loadPreference() {
            try {
                setGyroScale(ImuGyroScale.valueOf(chip.getPrefs().get("ImuGyroScale", ImuGyroScale.GyroFullScaleDegPerSec1000.toString())));
                setAccelScale(ImuAccelScale.valueOf(chip.getPrefs().get("ImuAccelScale", ImuAccelScale.ImuAccelScaleG8.toString())));
                setDisplayImu(chip.getPrefs().getBoolean("IMU.displayEnabled", true));
            } catch (Exception e) {
                log.warning(e.toString());
            }
        }

        @Override
        public void storePreference() {
            chip.getPrefs().put("ImuGyroScale", imuGyroScale.toString());
            chip.getPrefs().put("ImuAccelScale", imuAccelScale.toString());
            chip.getPrefs().putBoolean("IMU.displayEnabled", this.displayImuEnabled);
        }

        @Override
        public void preferenceChange(PreferenceChangeEvent e) {
            if (e.getKey().toLowerCase().contains("imu")) {
                log.info(this + " preferenceChange(): event=" + e + " key=" + e.getKey() + " newValue=" + e.getNewValue());
            }
            if (e.getNewValue() == null) {
                return;
            }
            try {
                // log.info(e.toString());
                switch (e.getKey()) {
                    case "ImuAccelScale":
                        setAccelScale(ImuAccelScale.valueOf(e.getNewValue()));
                        break;
                    case "ImuGyroScale":
                        setGyroScale(ImuGyroScale.valueOf(e.getNewValue()));
                        break;
                    case "IMU.displayEnabled":
                        setDisplayImu(Boolean.valueOf(e.getNewValue()));
                }
            } catch (IllegalArgumentException iae) {
                log.warning(iae.toString() + ": Preference value=" + e.getNewValue() + " for the preferenc with key=" + e.getKey() + " is not a proper enum for an IMU setting");
            }
        }
    }

    public class VideoControl extends Observable implements Observer, HasPreference, HasPropertyTooltips {

        private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        public boolean displayEvents = chip.getPrefs().getBoolean("VideoControl.displayEvents", true);
        public boolean displayFrames = chip.getPrefs().getBoolean("VideoControl.displayFrames", true);
        public boolean useAutoContrast = chip.getPrefs().getBoolean("VideoControl.useAutoContrast", false);
        public float contrast = chip.getPrefs().getFloat("VideoControl.contrast", 1.0F);
        public float brightness = chip.getPrefs().getFloat("VideoControl.brightness", 0.0F);
        private float gamma = chip.getPrefs().getFloat("VideoControl.gamma", 1); // gamma control for improving display
        // on crappy beamer output
        private PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();

        public VideoControl() {
            super();
            hasPreferenceList.add(this);
            tooltipSupport.setPropertyTooltip("displayEvents", "display DVS events");
            tooltipSupport.setPropertyTooltip("displayFrames", "display APS frames");
            tooltipSupport.setPropertyTooltip("useAutoContrast", "automatically set the display contrast for APS frames");
            tooltipSupport.setPropertyTooltip("brightness", "sets the brightness for APS frames, which is the lowest level of display intensity. Default is 0.");
            tooltipSupport.setPropertyTooltip("contrast", "sets the contrast for APS frames, which multiplies sample values by this quantity. Default is 1.");
            tooltipSupport.setPropertyTooltip("gamma", "sets the display gamma for APS frames, which applies a power law to optimize display for e.g. monitors. Default is 1.");
        }

        /**
         * @return the displayFrames
         */
        public boolean isDisplayFrames() {
            return displayFrames;
        }

        /**
         * @param displayFrames the displayFrames to set
         */
        public void setDisplayFrames(boolean displayFrames) {
            boolean old = this.displayFrames;
            this.displayFrames = displayFrames;
            chip.getPrefs().putBoolean("VideoControl.displayFrames", displayFrames);
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_DISPLAY_FRAMES_ENABLED, old, displayFrames);
            if (old != displayFrames) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }
        }

        /**
         * @return the displayEvents
         */
        public boolean isDisplayEvents() {
            return displayEvents;
        }

        /**
         * @param displayEvents the displayEvents to set
         */
        public void setDisplayEvents(boolean displayEvents) {
            boolean old = this.displayEvents;
            this.displayEvents = displayEvents;
            chip.getPrefs().putBoolean("VideoControl.displayEvents", displayEvents);
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_DISPLAY_EVENTS_ENABLED, old, displayEvents);
            if (old != displayEvents) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }
        }

        public boolean isUseAutoContrast() {
            return useAutoContrast;
        }

        public void setUseAutoContrast(boolean useAutoContrast) {
            boolean old = this.useAutoContrast;
            this.useAutoContrast = useAutoContrast;
            chip.getPrefs().putBoolean("VideoControl.useAutoContrast", useAutoContrast);
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_AUTO_CONTRAST_ENABLED, old, this.useAutoContrast);
            if (old != useAutoContrast) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }
        }

        /**
         * @return the contrast
         */
        public float getContrast() {
            return contrast;
        }

        /**
         * @param contrast the contrast to set
         */
        public void setContrast(float contrast) {
            float old = this.contrast;
            this.contrast = contrast;
            chip.getPrefs().putFloat("VideoControl.contrast", contrast);
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_CONTRAST, old, contrast);
            if (old != contrast) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }
        }

        /**
         * @return the brightness
         */
        public float getBrightness() {
            return brightness;
        }

        /**
         * @param brightness the brightness to set
         */
        public void setBrightness(float brightness) {
            float old = this.brightness;
            this.brightness = brightness;
            chip.getPrefs().putFloat("VideoControl.brightness", brightness);
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_BRIGHTNESS, old, this.brightness);
            if (old != brightness) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }
        }

        /**
         * @return the gamma
         */
        public float getGamma() {
            return gamma;
        }

        /**
         * @param gamma the gamma to set
         */
        public void setGamma(float gamma) {
            float old = this.gamma;
            this.gamma = gamma;
            chip.getPrefs().putFloat("VideoControl.gamma", gamma);
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().interruptViewloop();
            }
            notifyObservers();
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_GAMMA, old, this.gamma);
            if (old != gamma) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }
        }

        @Override
        public void update(Observable o, Object arg) {
            setChanged();
            notifyObservers(arg);
            // if (o == ) {
            // propertyChangeSupport.firePropertyChange(EVENT_GRAPHICS_DISPLAY_INTENSITY, null, runAdc.isSet());
            // } // TODO
        }

        /**
         * @return the propertyChangeSupport
         */
        public PropertyChangeSupport getPropertyChangeSupport() {
            return propertyChangeSupport;
        }

        @Override
        public void loadPreference() {
            setDisplayFrames(chip.getPrefs().getBoolean("VideoControl.displayFrames", true)); // use setter to make sure
            // GUIs are updated by
            // property changes
            setDisplayEvents(chip.getPrefs().getBoolean("VideoControl.displayEvents", true));
            setUseAutoContrast(chip.getPrefs().getBoolean("VideoControl.useAutoContrast", false));
            setContrast(chip.getPrefs().getFloat("VideoControl.contrast", 1.0F));
            setBrightness(chip.getPrefs().getFloat("VideoControl.brightness", 0.0F));
            setGamma(chip.getPrefs().getFloat("VideoControl.gamma", 1.0F));
        }

        @Override
        public void storePreference() {
            chip.getPrefs().putBoolean("VideoControl.displayEvents", displayEvents);
            chip.getPrefs().putBoolean("VideoControl.displayFrames", displayFrames);
            chip.getPrefs().putBoolean("VideoControl.useAutoContrast", useAutoContrast);
            chip.getPrefs().putFloat("VideoControl.contrast", contrast);
            chip.getPrefs().putFloat("VideoControl.brightness", brightness);
            chip.getPrefs().putFloat("VideoControl.gamma", gamma);
        }

        @Override
        public String getPropertyTooltip(String propertyName) {
            return tooltipSupport.getPropertyTooltip(propertyName);
        }
    }

    public String[] choices() {
        String[] s = new String[ImuAccelScale.values().length];
        for (int i = 0; i < ImuAccelScale.values().length; i++) {
            s[i] = ImuAccelScale.values()[i].fullScaleString;
        }
        return s;
    }

    /**
     * @return the videoControl
     */
    public DavisConfig.VideoControl getVideoControl() {
        return videoControl;
    }

    /**
     * @return the apsReadoutControl
     */
    public DavisConfig.ApsReadoutControl getApsReadoutControl() {
        return apsReadoutControl;
    }

    /**
     * @return the imuControl
     */
    public DavisConfig.ImuControl getImuControl() {
        return imuControl;
    }

    /**
     * @return the exposureControlRegister
     */
    public CPLDInt getExposureControlRegister() {
        return exposureControlRegister;
    }

    /**
     * Formats bits represented in a string as '0' or '1' as a byte array to be
     * sent over the interface to the firmware, for loading in big endian bit
     * order, in order of the bytes sent starting with byte 0.
     * <p>
     * Because the firmware writes integral bytes it is important that the bytes
     * sent to the device are padded with leading bits (at msbs of first byte)
     * that are finally shifted out of the on-chip shift register.
     *
     * Therefore <code>bitString2Bytes</code> should only be called ONCE, after
     * the complete bit string has been assembled, unless it is known the other
     * bits are an integral number of bytes.
     *
     * @param bitString in msb to lsb order from left end, where msb will be in
     * msb of first output byte
     * @return array of bytes to send
     */
    public class DavisChipConfigChain extends ChipConfigChain {

        // Config Bits
        OnchipConfigBit resetCalib = new OnchipConfigBit(chip, "resetCalib", 0,
                "turns the bias generator integrate and fire calibration neuron off", true),
                typeNCalib = new OnchipConfigBit(
                        chip,
                        "typeNCalib",
                        1,
                        "make the bias generator intgrate and fire calibration neuron configured to measure N type biases; otherwise measures P-type currents",
                        false),
                resetTestpixel = new OnchipConfigBit(chip, "resetTestpixel", 2, "keeps the test pixel in reset", true),
                hotPixelSuppression = new OnchipConfigBit(
                        chip,
                        "hotPixelSuppression",
                        3,
                        "<html>DAViS240a: turns on the hot pixel suppression. <p>DAViS240b: enables test pixel stripes on right side of array <p>DAViS240c: no effect",
                        false),
                nArow = new OnchipConfigBit(chip, "nArow", 4, "use nArow in the AER state machine", false),
                useAout = new OnchipConfigBit(chip, "useAout", 5, "turn the pads for the analog MUX outputs on", true),
                globalShutter = new OnchipConfigBit(
                        chip,
                        "globalShutter",
                        6,
                        "Use the global shutter or not, only has effect on DAVIS240b/c cameras. No effect in DAVIS240a cameras. On-chip control bit that is looded into on-chip shift register.",
                        false);
        // Muxes
        OutputMux[] amuxes = {new AnalogOutputMux(1), new AnalogOutputMux(2), new AnalogOutputMux(3)};
        OutputMux[] dmuxes = {new DigitalOutputMux(1), new DigitalOutputMux(2), new DigitalOutputMux(3),
            new DigitalOutputMux(4)};
        OutputMux[] bmuxes = {new DigitalOutputMux(0)};
        ArrayList<OutputMux> muxes = new ArrayList();
        MuxControlPanel controlPanel = null;

        public DavisChipConfigChain(Chip chip) {
            super(chip);
            this.sbChip = chip;

            TOTAL_CONFIG_BITS = 24;

            hasPreferenceList.add(this);
            configBits = new OnchipConfigBit[7];
            configBits[0] = resetCalib;
            configBits[1] = typeNCalib;
            configBits[2] = resetTestpixel;
            configBits[3] = hotPixelSuppression;
            configBits[4] = nArow;
            configBits[5] = useAout;
            configBits[6] = globalShutter;
            for (OnchipConfigBit b : configBits) {
                b.addObserver(this);
            }

            muxes.addAll(Arrays.asList(bmuxes));
            muxes.addAll(Arrays.asList(dmuxes)); // 4 digital muxes, first in list since at end of chain - bits must be
            // sent first, before any biasgen bits
            muxes.addAll(Arrays.asList(amuxes)); // finally send the 3 voltage muxes

            for (OutputMux m : muxes) {
                m.addObserver(this);
                m.setChip(chip);
            }

            bmuxes[0].setName("BiasOutMux");

            bmuxes[0].put(0, "IFThrBn");
            bmuxes[0].put(1, "AEPuYBp");
            bmuxes[0].put(2, "AEPuXBp");
            bmuxes[0].put(3, "LColTimeout");
            bmuxes[0].put(4, "AEPdBn");
            bmuxes[0].put(5, "RefrBp");
            bmuxes[0].put(6, "PrSFBp");
            bmuxes[0].put(7, "PrBp");
            bmuxes[0].put(8, "PixInvBn");
            bmuxes[0].put(9, "LocalBufBn");
            bmuxes[0].put(10, "ApsROSFBn");
            bmuxes[0].put(11, "DiffCasBnc");
            bmuxes[0].put(12, "ApsCasBpc");
            bmuxes[0].put(13, "OffBn");
            bmuxes[0].put(14, "OnBn");
            bmuxes[0].put(15, "DiffBn");

            dmuxes[0].setName("DigMux3");
            dmuxes[1].setName("DigMux2");
            dmuxes[2].setName("DigMux1");
            dmuxes[3].setName("DigMux0");

            for (int i = 0; i < 4; i++) {
                dmuxes[i].put(0, "AY179right");
                dmuxes[i].put(1, "Acol");
                dmuxes[i].put(2, "ColArbTopA");
                dmuxes[i].put(3, "ColArbTopR");
                dmuxes[i].put(4, "FF1");
                dmuxes[i].put(5, "FF2");
                dmuxes[i].put(6, "Rcarb");
                dmuxes[i].put(7, "Rcol");
                dmuxes[i].put(8, "Rrow");
                dmuxes[i].put(9, "RxarbE");
                dmuxes[i].put(10, "nAX0");
                dmuxes[i].put(11, "nArowBottom");
                dmuxes[i].put(12, "nArowTop");
                dmuxes[i].put(13, "nRxOn");

            }

            dmuxes[3].put(14, "AY179");
            dmuxes[3].put(15, "RY179");
            dmuxes[2].put(14, "AY179");
            dmuxes[2].put(15, "RY179");
            dmuxes[1].put(14, "biasCalibSpike");
            dmuxes[1].put(15, "nRY179right");
            dmuxes[0].put(14, "nResetRxCol");
            dmuxes[0].put(15, "nRYtestpixel");

            amuxes[0].setName("AnaMux2");
            amuxes[1].setName("AnaMux1");
            amuxes[2].setName("AnaMux0");

            for (int i = 0; i < 3; i++) {
                amuxes[i].put(0, "on");
                amuxes[i].put(1, "off");
                amuxes[i].put(2, "vdiff");
                amuxes[i].put(3, "nResetPixel");
                amuxes[i].put(4, "pr");
                amuxes[i].put(5, "pd");
            }

            amuxes[0].put(6, "calibNeuron");
            amuxes[0].put(7, "nTimeout_AI");

            amuxes[1].put(6, "apsgate");
            amuxes[1].put(7, "apsout");

            amuxes[2].put(6, "apsgate");
            amuxes[2].put(7, "apsout");

        }

        class VoltageOutputMap extends OutputMap {

            final void put(int k, int v) {
                put(k, v, "Voltage " + k);
            }

            VoltageOutputMap() {
                put(0, 1);
                put(1, 3);
                put(2, 5);
                put(3, 7);
                put(4, 9);
                put(5, 11);
                put(6, 13);
                put(7, 15);
            }
        }

        class DigitalOutputMap extends OutputMap {

            DigitalOutputMap() {
                for (int i = 0; i < 16; i++) {
                    put(i, i, "DigOut " + i);
                }
            }
        }

        class AnalogOutputMux extends OutputMux {

            AnalogOutputMux(int n) {
                super(sbChip, 4, 8, (new VoltageOutputMap()));
                setName("Voltages" + n);
            }
        }

        class DigitalOutputMux extends OutputMux {

            DigitalOutputMux(int n) {
                super(sbChip, 4, 16, (new DigitalOutputMap()));
                setName("LogicSignals" + n);
            }
        }

        @Override
        public String getBitString() {
            // System.out.print("dig muxes ");
            String dMuxBits = getMuxBitString(dmuxes);
            // System.out.print("config bits ");
            String configBits = getConfigBitString();
            // System.out.print("analog muxes ");
            String aMuxBits = getMuxBitString(amuxes);
            // System.out.print("bias muxes ");
            String bMuxBits = getMuxBitString(bmuxes);

            String chipConfigChain = (dMuxBits + configBits + aMuxBits + bMuxBits);
            // System.out.println("On chip config chain: "+chipConfigChain);

            return chipConfigChain; // returns bytes padded at end
        }

        String getMuxBitString(OutputMux[] muxs) {
            StringBuilder s = new StringBuilder();
            for (OutputMux m : muxs) {
                s.append(m.getBitString());
            }
            // System.out.println(s);
            return s.toString();
        }

        String getConfigBitString() {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < (TOTAL_CONFIG_BITS - getConfigBits().length); i++) {
                s.append("0");
            }
            for (int i = getConfigBits().length - 1; i >= 0; i--) {
                s.append(getConfigBits()[i].isSet() ? "1" : "0");
            }
            // System.out.println(s);
            return s.toString();
        }

        @Override
        public MuxControlPanel buildMuxControlPanel() {
            return new MuxControlPanel(muxes);
        }

        @Override
        public JPanel getChipConfigPanel() {
            JPanel chipConfigPanel = new JPanel();
            chipConfigPanel.setLayout(new BoxLayout(chipConfigPanel, BoxLayout.Y_AXIS));

            // On-Chip config bits
            JPanel extraPanel = new JPanel();
            extraPanel.setLayout(new BoxLayout(extraPanel, BoxLayout.Y_AXIS));
            for (OnchipConfigBit b : getConfigBits()) {
                extraPanel.add(new JRadioButton(b.getAction()));
            }
            extraPanel.setBorder(new TitledBorder("Extra on-chip bits"));
            chipConfigPanel.add(extraPanel);

            // FX2 port bits
            JPanel portBitsPanel = new JPanel();
            portBitsPanel.setLayout(new BoxLayout(portBitsPanel, BoxLayout.Y_AXIS));
            for (PortBit p : portBits) {
                portBitsPanel.add(new JRadioButton(p.getAction()));
            }
            portBitsPanel.setBorder(new TitledBorder("Cypress FX2 port bits"));
            chipConfigPanel.add(portBitsPanel);

            // event translation control
            JPanel eventTranslationControlPanel = new JPanel();
            eventTranslationControlPanel.setBorder(new TitledBorder("DVS event translation control"));
            eventTranslationControlPanel.setLayout(new BoxLayout(eventTranslationControlPanel, BoxLayout.Y_AXIS));
            // add a reset button on top of everything
            final Action translateRowOnlyEventsAction = new AbstractAction("Translate row-only events") {
                {
                    putValue(Action.SHORT_DESCRIPTION,
                            "<html>Controls whether row-only events (row request but no column request) "
                            + "<br>are captured from USB data stream in ApsDvsHardwareInterface. "
                            + "<p>These events are rendered as OFF events at x=239");
                    putValue(Action.SELECTED_KEY, translateRowOnlyEvents);
                }

                @Override
                public void actionPerformed(ActionEvent evt) {
                    setTranslateRowOnlyEvents(!isTranslateRowOnlyEvents());
                }
            };
            final JRadioButton translateRowOnlyEventsButton = new JRadioButton(translateRowOnlyEventsAction);
            eventTranslationControlPanel.add(translateRowOnlyEventsButton);
            getSupport().addPropertyChangeListener(DavisDisplayConfigInterface.PROPERTY_TRANSLATE_ROW_ONLY_EVENTS,
                    new PropertyChangeListener() {

                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            translateRowOnlyEventsButton.setSelected((boolean) evt.getNewValue());
                        }
                    });
            chipConfigPanel.add(eventTranslationControlPanel);

            return chipConfigPanel;
        }
    }
}
