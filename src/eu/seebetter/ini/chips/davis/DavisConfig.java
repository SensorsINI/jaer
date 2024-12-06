/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeSupport;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.StringTokenizer;
import java.util.logging.Level;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import ch.unizh.ini.jaer.config.spi.SPIConfigBit;
import ch.unizh.ini.jaer.config.spi.SPIConfigInt;
import ch.unizh.ini.jaer.config.spi.SPIConfigValue;
import eu.seebetter.ini.chips.davis.DavisVideoContrastController;
import eu.seebetter.ini.chips.davis.imu.ImuAccelScale;
import eu.seebetter.ini.chips.davis.imu.ImuControl;
import eu.seebetter.ini.chips.davis.imu.ImuControlPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotTweakerUtilities;
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.stereopsis.MultiCameraBiasgenHardwareInterface;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.ParameterControlPanel;
import net.sf.jaer.util.PropertyTooltipSupport;
import net.sf.jaer.util.RemoteControl;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

/**
 * Base class for configuration of Davis cameras
 *
 * @author tobi
 */
public class DavisConfig extends Biasgen implements DavisDisplayConfigInterface, DavisTweaks, ChipControlPanel, RemoteControlled {
    // All preferences, excluding biases.

    protected final List<SPIConfigValue> allPreferencesList = new ArrayList<>();

    // Preferences by category.
    protected final List<SPIConfigValue> muxControl = new ArrayList<>();
    protected final List<SPIConfigValue> dvsControl = new ArrayList<>();
    protected final List<SPIConfigValue> apsControl = new ArrayList<>();
    protected final List<SPIConfigValue> imuControl = new ArrayList<>();
    protected final List<SPIConfigValue> extInControl = new ArrayList<>();
    protected final List<SPIConfigValue> chipControl = new ArrayList<>();

    // All bias types.
    protected AddressedIPotArray ipots;
    protected final ShiftedSourceBiasCF[] ssBiases = new ShiftedSourceBiasCF[2];
    protected final ShiftedSourceBiasCF ssp, ssn;

    // these pots for DVSTweaks
    protected AddressedIPotCF diffOn, diffOff, refr, pr, sf, diff;
    protected SPIConfigBit globalShutter, apsRun, dvsRun;
    protected SPIConfigInt apsExposure, apsFrameInterval;

    // subclasses for controlling aspects of camera
    protected DavisConfig.VideoControl videoControl;
    protected ImuControl imuControlGUI;
    protected AutoExposureController autoExposureController;
    protected DavisVideoContrastController contrastController = null;
    EngineeringFormat eng = new EngineeringFormat();

    private boolean dualUserFriendlyAndBiasCurrentsViewEnabled = false;

    public DavisConfig(final Chip chip) {
        super(chip);
        setName("DavisConfig");

        // masterbias
        getMasterbias().setKPrimeNFet(55e-3f); // estimated from tox=42A, mu_n=670 cm^2/Vs
        getMasterbias().setMultiplier(4); // =45 correct for dvs320
        getMasterbias().setWOverL(4.8f / 2.4f); // masterbias has nfet with w/l=2 at output
        getMasterbias().setRExternal(100e3f); // biasgen on all SeeBetter chips designed for 100kOhn resistor that makes
        // 389nA master current
        getMasterbias().setRInternal(0); // no on-chip resistor is used
        getMasterbias().addObserver(this); // changes to masterbias come back to update() here

        ipots = new AddressedIPotArray(this);

        diff = DavisConfig.addAIPot(ipots, this, "DiffBn,0,n,normal,differencing amp");
        diffOn = DavisConfig.addAIPot(ipots, this, "OnBn,1,n,normal,DVS brighter threshold");
        diffOff = DavisConfig.addAIPot(ipots, this, "OffBn,2,n,normal,DVS darker threshold");
        DavisConfig.addAIPot(ipots, this, "ApsCasEpc,3,p,cascode,cascode between APS und DVS");
        DavisConfig.addAIPot(ipots, this, "DiffCasBnc,4,n,cascode,differentiator cascode bias");
        DavisConfig.addAIPot(ipots, this, "ApsROSFBn,5,n,normal,APS readout source follower bias");
        DavisConfig.addAIPot(ipots, this, "LocalBufBn,6,n,normal,Local buffer bias");
        DavisConfig.addAIPot(ipots, this, "PixInvBn,7,n,normal,Pixel request inversion static inverter bias");
        pr = DavisConfig.addAIPot(ipots, this, "PrBp,8,p,normal,Photoreceptor bias current");
        sf = DavisConfig.addAIPot(ipots, this, "PrSFBp,9,p,normal,Photoreceptor follower bias current (when used in pixel type)");
        refr = DavisConfig.addAIPot(ipots, this, "RefrBp,10,p,normal,DVS refractory period current");
        DavisConfig.addAIPot(ipots, this, "AEPdBn,11,n,normal,Request encoder pulldown static current");
        DavisConfig.addAIPot(ipots, this, "LcolTimeoutBn,12,n,normal,No column request timeout");
        DavisConfig.addAIPot(ipots, this, "AEPuXBp,13,p,normal,AER column pullup");
        DavisConfig.addAIPot(ipots, this, "AEPuYBp,14,p,normal,AER row pullup");
        DavisConfig.addAIPot(ipots, this, "IFThrBn,15,n,normal,Integrate and fire intensity neuron threshold");
        DavisConfig.addAIPot(ipots, this, "IFRefrBn,16,n,normal,Integrate and fire intensity neuron refractory period bias current");
        DavisConfig.addAIPot(ipots, this, "PadFollBn,17,n,normal,Follower-pad buffer bias current");
        DavisConfig.addAIPot(ipots, this, "apsOverflowLevel,18,n,normal,special overflow level bias ");
        DavisConfig.addAIPot(ipots, this, "biasBuffer,19,n,normal,special buffer bias ");

        setPotArray(ipots);

        // shifted sources
        ssp = new ShiftedSourceBiasCF(this);
        ssp.setSex(Pot.Sex.P);
        ssp.setName("SSP");
        ssp.setTooltipString("p-type shifted source that generates a regulated voltage near Vdd");
        ssp.setAddress(20);
        ssp.addObserver(this);

        ssn = new ShiftedSourceBiasCF(this);
        ssn.setSex(Pot.Sex.N);
        ssn.setName("SSN");
        ssn.setTooltipString("n-type shifted source that generates a regulated voltage near ground");
        ssn.setAddress(21);
        ssn.addObserver(this);

        ssBiases[0] = ssp;
        ssBiases[1] = ssn;

        // Multiplexer module
        muxControl.add(new SPIConfigBit("Mux.RunChip", "Enable the chip's bias generator, powering it up.", CypressFX3.FPGA_MUX,
                (short) 3, true, this));
        muxControl.add(new SPIConfigBit("Mux.DropExtInputOnTransferStall", "Drop External Input events when USB FIFO is full.",
                CypressFX3.FPGA_MUX, (short) 4, true, this));
        muxControl.add(new SPIConfigBit("Mux.DropDVSOnTransferStall", "Drop DVS events when USB FIFO is full.", CypressFX3.FPGA_MUX,
                (short) 5, true, this));

        muxControl.add(new SPIConfigInt("USB.EarlyPacketDelay", "Ensure a USB packet is committed at least every N x 125µs timesteps.",
                CypressFX3.FPGA_USB, (short) 1, 13, 8, this));

        for (final SPIConfigValue cfgVal : muxControl) {
            cfgVal.addObserver(this);
            allPreferencesList.add(cfgVal);
        }

        // DVS module
        dvsRun = new SPIConfigBit("DVS.Run", "Enable DVS.", CypressFX3.FPGA_DVS, (short) 3, false, this);
        dvsControl.add(dvsRun);
        dvsControl.add(new SPIConfigBit("DVS.WaitOnTransferStall",
                "On event FIFO full, wait to ACK until again empty if true, or just continue ACKing if false.", CypressFX3.FPGA_DVS, (short) 4,
                false, this));
        dvsControl.add(new SPIConfigBit("DVS.ExternalAERControl", "Don't drive AER ACK pin from FPGA (also must disable Event Capture).",
                CypressFX3.FPGA_DVS, (short) 5, false, this));

        // TODO: new boards only.
        dvsControl.add(new SPIConfigBit("DVS.FilterBackgroundActivity", "Filter background events (uncorrelated noise) using hardware filter. Only available on DAVIS dev-kits and BLUE/RED editions.",
                CypressFX3.FPGA_DVS, (short) 31, true, this));
        dvsControl.add(new SPIConfigInt("DVS.FilterBackgroundActivityDeltaTime", "Hardware background events filter delta time (in 250 µs units). Only available on DAVIS dev-kits and BLUE/RED editions.",
                CypressFX3.FPGA_DVS, (short) 32, 12, 80, this));
        dvsControl.add(new SPIConfigBit("DVS.FilterRefractoryPeriod", "Hardware refractory period filter, inhibits events from firing in a given time window. Only available on DAVIS dev-kits and BLUE/RED editions.",
                CypressFX3.FPGA_DVS, (short) 33, false, this));
        dvsControl.add(new SPIConfigInt("DVS.FilterRefractoryPeriodTime", "Hardware refractory period time (in 250 µs units). Only available on DAVIS dev-kits and BLUE/RED editions.",
                CypressFX3.FPGA_DVS, (short) 34, 12, 2, this));

        dvsControl.add(new SPIConfigBit("DVS.FilterSkipEvents", "Drop one event every N. Only available on DAVIS 240C, dev-kits and BLUE/RED editions.",
                CypressFX3.FPGA_DVS, (short) 51, false, this));
        dvsControl.add(new SPIConfigInt("DVS.FilterSkipEventsEvery", "Number of events to pass before dropping one. Only available on DAVIS 240C, dev-kits and BLUE/RED editions.",
                CypressFX3.FPGA_DVS, (short) 52, 8, 1, this));

        dvsControl.add(new SPIConfigBit("DVS.FilterPolarityFlatten", "Flatten all events to one polarity only (OFF). Only available on DAVIS 240C, dev-kits and BLUE/RED editions.",
                CypressFX3.FPGA_DVS, (short) 61, false, this));
        dvsControl.add(new SPIConfigBit("DVS.FilterPolaritySuppress", "Suppress one polarity type. Only available on DAVIS 240C, dev-kits and BLUE/RED editions.",
                CypressFX3.FPGA_DVS, (short) 62, false, this));
        dvsControl.add(new SPIConfigBit("DVS.FilterPolaritySuppressType", "Type of polarity to suppress (false=OFF, true=ON). Only available on DAVIS 240C, dev-kits and BLUE/RED editions.",
                CypressFX3.FPGA_DVS, (short) 63, false, this));

        for (final SPIConfigValue cfgVal : dvsControl) {
            cfgVal.addObserver(this);
            allPreferencesList.add(cfgVal);
        }

        // APS module
        apsRun = new SPIConfigBit("APS.Run", "Enable APS.", CypressFX3.FPGA_APS, (short) 4, false, this);
        apsControl.add(apsRun);
        apsControl.add(new SPIConfigBit("APS.WaitOnTransferStall",
                "On event FIFO full, pause and wait for free space. This ensures no APS pixels are dropped.", CypressFX3.FPGA_APS, (short) 5,
                true, this));
        globalShutter = new SPIConfigBit("APS.GlobalShutter", "Enable global shutter versus rolling shutter.", CypressFX3.FPGA_APS,
                (short) 7, true, this);
        apsControl.add(globalShutter);
        apsExposure = new SPIConfigInt("APS.Exposure", "Set exposure time (in µs).", CypressFX3.FPGA_APS, (short) 12, 22, 4000, this);
        apsControl.add(apsExposure);
        apsFrameInterval = new SPIConfigInt("APS.FrameInterval", "Set time between frames (in µs).", CypressFX3.FPGA_APS, (short) 13, 23,
                50000, this);
        apsControl.add(apsFrameInterval);

        for (final SPIConfigValue cfgVal : apsControl) {
            cfgVal.addObserver(this);
            allPreferencesList.add(cfgVal);
        }

        // Global shutter is special, as in there is often also a chip config bit that needs to be kept in sync.
        globalShutter.addObserver(new Observer() {
            @Override
            public void update(final Observable gsObs, final Object arg) {
                if ((getHardwareInterface() != null) && (getHardwareInterface() instanceof CypressFX3)) {
                    final CypressFX3 fx3HwIntf = (CypressFX3) getHardwareInterface();

                    try {
                        final SPIConfigBit gsBit = (SPIConfigBit) gsObs;

                        fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 142, (gsBit.isSet()) ? (1) : (0));
                    } catch (final HardwareInterfaceException e) {
                        net.sf.jaer.biasgen.Biasgen.log.warning("On GS update() caught " + e.toString());
                    }
                }
            }
        });

        // IMU module
        imuControl.add(new SPIConfigBit("IMU.RunAccel", "Enable IMU accelerometer.", CypressFX3.FPGA_IMU, (short) 2, false, this));
        imuControl.add(new SPIConfigBit("IMU.RunGyro", "Enable IMU gyroscope.", CypressFX3.FPGA_IMU, (short) 3, false, this));
        imuControl.add(new SPIConfigBit("IMU.RunTemp", "Enable IMU temperature sensor.", CypressFX3.FPGA_IMU, (short) 4, false, this));

        imuControl.add(new SPIConfigInt("IMU.SampleRateDivider", "Sample-rate divider value.", CypressFX3.FPGA_IMU, (short) 5, 8, 0, this));
        imuControl.add(new SPIConfigInt("IMU.DigitalLowPassFilter", "Digital low-pass filter configuration.", CypressFX3.FPGA_IMU,
                (short) 6, 3, 1, this));
        imuControl
                .add(new SPIConfigInt("IMU.AccelFullScale", "Accellerometer scale configuration.", CypressFX3.FPGA_IMU, (short) 7, 2, 2, this));
        imuControl.add(new SPIConfigInt("IMU.GyroFullScale", "Gyroscope scale configuration.", CypressFX3.FPGA_IMU, (short) 10, 2, 2, this));

        for (final SPIConfigValue cfgVal : imuControl) {
            cfgVal.addObserver(this);
            allPreferencesList.add(cfgVal);
        }

        // External Input module
        extInControl
                .add(new SPIConfigBit("ExtInput.RunDetector", "Enable signal detector.", CypressFX3.FPGA_EXTINPUT, (short) 0, false, this));
        extInControl.add(new SPIConfigBit("ExtInput.DetectRisingEdges", "Emit special event if a rising edge is detected.",
                CypressFX3.FPGA_EXTINPUT, (short) 1, false, this));
        extInControl.add(new SPIConfigBit("ExtInput.DetectFallingEdges", "Emit special event if a falling edge is detected.",
                CypressFX3.FPGA_EXTINPUT, (short) 2, false, this));
        extInControl.add(new SPIConfigBit("ExtInput.DetectPulses", "Emit special event if a pulse is detected.", CypressFX3.FPGA_EXTINPUT,
                (short) 3, true, this));
        extInControl.add(new SPIConfigBit("ExtInput.DetectPulsePolarity", "Polarity of the pulse to be detected.", CypressFX3.FPGA_EXTINPUT,
                (short) 4, true, this));
        extInControl.add(new SPIConfigInt("ExtInput.DetectPulseLength", "Minimal length of the pulse to be detected.",
                CypressFX3.FPGA_EXTINPUT, (short) 5, 27, 60, this));

        for (final SPIConfigValue cfgVal : extInControl) {
            cfgVal.addObserver(this);
            allPreferencesList.add(cfgVal);
        }

        // Chip diagnostic chain
        chipControl
                .add(new SPIConfigInt("Chip.DigitalMux0", "Digital multiplexer 0 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 128, 4, 0, this));
        chipControl
                .add(new SPIConfigInt("Chip.DigitalMux1", "Digital multiplexer 1 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 129, 4, 0, this));
        chipControl
                .add(new SPIConfigInt("Chip.DigitalMux2", "Digital multiplexer 2 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 130, 4, 0, this));
        chipControl
                .add(new SPIConfigInt("Chip.DigitalMux3", "Digital multiplexer 3 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 131, 4, 0, this));
        chipControl
                .add(new SPIConfigInt("Chip.AnalogMux0", "Analog multiplexer 0 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 132, 4, 0, this));
        chipControl
                .add(new SPIConfigInt("Chip.AnalogMux1", "Analog multiplexer 1 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 133, 4, 0, this));
        chipControl
                .add(new SPIConfigInt("Chip.AnalogMux2", "Analog multiplexer 2 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 134, 4, 0, this));
        chipControl
                .add(new SPIConfigInt("Chip.BiasMux0", "Bias multiplexer 0 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 135, 4, 0, this));

        chipControl.add(new SPIConfigBit("Chip.ResetCalibNeuron", "Turn off the integrate and fire calibration neuron (bias generator).",
                CypressFX3.FPGA_CHIPBIAS, (short) 136, true, this));
        chipControl.add(new SPIConfigBit("Chip.TypeNCalibNeuron",
                "Make the integrate and fire calibration neuron configured to measure N type biases; otherwise measures P-type currents.",
                CypressFX3.FPGA_CHIPBIAS, (short) 137, false, this));
        chipControl.add(
                new SPIConfigBit("Chip.ResetTestPixel", "Keep the text pixel in reset.", CypressFX3.FPGA_CHIPBIAS, (short) 138, true, this));
        chipControl.add(
                new SPIConfigBit("Chip.AERnArow", "Use nArow in the AER state machine.", CypressFX3.FPGA_CHIPBIAS, (short) 140, false, this));
        chipControl.add(new SPIConfigBit("Chip.UseAOut", "Turn the pads for the analog MUX outputs on.", CypressFX3.FPGA_CHIPBIAS,
                (short) 141, false, this));

        for (final SPIConfigValue cfgVal : chipControl) {
            cfgVal.addObserver(this);
            allPreferencesList.add(cfgVal);
        }

        // graphicOptions
        videoControl = new VideoControl();
        videoControl.addObserver(this);

        // imuControl
        imuControlGUI = new ImuControl(this, imuControl);

        autoExposureController = new AutoExposureController((DavisBaseCamera) chip);

        // Link to DavisUserControlPanel to update values there too.
        // TODO add observer for updating computed DVS threshold values with diff/diffOn/diffOff change
        dvsRun.addObserver(new Observer() {
            @Override
            public void update(final Observable o, final Object arg) {
                getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_CAPTURE_EVENTS_ENABLED, null,
                        ((SPIConfigBit) o).isSet());
            }
        });
        apsRun.addObserver(new Observer() {
            @Override
            public void update(final Observable o, final Object arg) {
                getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_CAPTURE_FRAMES_ENABLED, null,
                        ((SPIConfigBit) o).isSet());
            }
        });
        globalShutter.addObserver(new Observer() {
            @Override
            public void update(final Observable o, final Object arg) {
                getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_GLOBAL_SHUTTER_MODE_ENABLED, null,
                        ((SPIConfigBit) o).isSet());
            }
        });
        apsExposure.addObserver(new Observer() {
            @Override
            public void update(final Observable o, final Object arg) {
                getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_EXPOSURE_DELAY_US, null, ((SPIConfigInt) o).get());
            }
        });
        apsFrameInterval.addObserver(new Observer() {
            @Override
            public void update(final Observable o, final Object arg) {
                getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_FRAME_INTERVAL_US, null, ((SPIConfigInt) o).get());
            }
        });

        if (chip.getRemoteControl() != null) {
            RemoteControl control = chip.getRemoteControl();
            control.addCommandListener(this, CMD_BANDWIDTH_TWEAK, CMD_BANDWIDTH_TWEAK + " val - sets DVS bandwidth tweak; range -1:1 range");
            control.addCommandListener(this, CMD_MAXFIRINGRATE_TWEAK, CMD_MAXFIRINGRATE_TWEAK + " val - sets DVS max firing rate (refractory period) tweak; range -1:1 range");
            control.addCommandListener(this, CMD_ONOFFBALANCE_TWEAK, CMD_ONOFFBALANCE_TWEAK + " val - sets DVS On/Off event threshold tweak; range -1:1 range");
            control.addCommandListener(this, CMD_THRESHOLD_TWEAK, CMD_THRESHOLD_TWEAK + " val - sets DVS threshold tweak; range -1:1 range");
            control.addCommandListener(this, CMD_GETOFFTHRSHOLDLOGE, CMD_GETOFFTHRSHOLDLOGE + " - returns estimated DVS OFF threshold value in log_E units");
            control.addCommandListener(this, CMD_GETONTHRESHOLDLOGE, CMD_GETONTHRESHOLDLOGE + " - returns estimated DVS ON threshold value in log_E units");
        }

        setBatchEditOccurring(true);
        loadPreferences();
        setBatchEditOccurring(false);

        try {
            sendConfiguration(this);
        } catch (final HardwareInterfaceException ex) {
            Biasgen.log.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public String processRemoteControlCommand(final RemoteControlCommand command, final String input) {
        log.log(Level.INFO, "processing RemoteControlCommand {0} with input={1}", new Object[]{command, input});

        if (command == null) {
            return null;
        }

        final String[] tokens = input.split(" ");
        if (tokens.length < 2) {
            return input + ": unknown command - did you forget the argument?";
        }
        if ((tokens[1] == null) || (tokens[1].length() == 0)) {
            return input + ": argument too short - need a number";
        }
        float v = 0;
        try {
            v = Float.parseFloat(tokens[1]);
        } catch (final NumberFormatException e) {
            return input + ": bad argument? Caught " + e.toString();
        }
        final String c = command.getCmdName();
        if (c.equals(CMD_BANDWIDTH_TWEAK)) {
            setBandwidthTweak(v);
        } else if (c.equals(CMD_MAXFIRINGRATE_TWEAK)) {
            setMaxFiringRateTweak(v);
        } else if (c.equals(CMD_THRESHOLD_TWEAK)) {
            setThresholdTweak(v);
        } else if (c.equals(CMD_ONOFFBALANCE_TWEAK)) {
            setOnOffBalanceTweak(v);
        } else if (c.equals(CMD_GETOFFTHRSHOLDLOGE)) {
            return String.format("%.5f", getOffThresholdLogE());
        } else if (c.equals(CMD_GETONTHRESHOLDLOGE)) {
            return String.format("%.5f", getOnThresholdLogE());
        }
        return "successfully processed command " + input;
    }

    private ParameterControlPanel videoParameterControlPanel;
    private JPanel userFriendlyControls, combinedBiasShiftedSourcePanel;
    private JPanel configPanel;
    private JTabbedPane configTabbedPane;
    private JPanel dualViewPanel;

    // threshold for triggering a new frame snapshot automatically
    private int autoShotThreshold;

    // DVSTweasks from DVS128
    private float bandwidth = 0;
    private float maxFiringRate = 0;
    private float onOffBalance = 0;
    private float threshold = 0;

    /**
     *
     * Overrides the default to built the custom control panel for configuring
     * the output multiplexers and many other chip, board and display controls.
     *
     * @return a new panel for controlling this chip and board configuration
     */
    @Override
    public JPanel buildControlPanel() {
        setBatchEditOccurring(true); // stop updates on building panel

        configPanel = new JPanel();
        configPanel.setLayout(new BorderLayout());

        makeConfigTabbedPane();

        // only select panel after all added
        configPanel.add(configTabbedPane, BorderLayout.CENTER);

        setBatchEditOccurring(false);
        return configPanel;
    }

    /**
     * Sets dual view of user friendly and bias currents
     */
    void setDualView(boolean selected) {
        if (selected) {
            makeDualViewPanel();
            configPanel.removeAll();
            configPanel.add(dualViewPanel, BorderLayout.CENTER);
        } else {
            makeConfigTabbedPane();
            configPanel.removeAll();
            configPanel.add(configTabbedPane, BorderLayout.CENTER);
        }
        configPanel.validate();
    }

    private void makeDualViewPanel() {
        dualViewPanel = new JPanel();
        dualViewPanel.setLayout(new BoxLayout(dualViewPanel, BoxLayout.X_AXIS));
        dualViewPanel.add(userFriendlyControls); // removes it from configTabbedPane
        JScrollPane biasesSP = new JScrollPane(combinedBiasShiftedSourcePanel);
        dualViewPanel.add(biasesSP); // removes it from configTabbedPane
    }

    private void makeConfigTabbedPane() {
        configTabbedPane = new JTabbedPane();
        userFriendlyControls = new DavisUserControlPanel(getChip());
        configTabbedPane.addTab("<html><strong><font color=\"red\">User-Friendly Controls", userFriendlyControls);
        // biasgen
        combinedBiasShiftedSourcePanel = new JPanel();
        combinedBiasShiftedSourcePanel
                .add(new JLabel("<html>Low-level control of on-chip bias currents and voltages. <p>These are only for experts!"));
        combinedBiasShiftedSourcePanel.setLayout(new BoxLayout(combinedBiasShiftedSourcePanel, BoxLayout.Y_AXIS));
        combinedBiasShiftedSourcePanel.add(super.buildControlPanel());
        combinedBiasShiftedSourcePanel.add(new ShiftedSourceControlsCF(ssp));
        combinedBiasShiftedSourcePanel.add(new ShiftedSourceControlsCF(ssn));
        configTabbedPane.addTab("Bias Current Config", combinedBiasShiftedSourcePanel);
        // Multiplexer
        final JPanel muxPanel = new JPanel();
        muxPanel.setLayout(new BoxLayout(muxPanel, BoxLayout.Y_AXIS));
        configTabbedPane.addTab("Multiplexer Config", muxPanel);
        SPIConfigValue.addGUIControls(muxPanel, muxControl);
        // DVS
        final JPanel dvsPanel = new JPanel();
        dvsPanel.setLayout(new BoxLayout(dvsPanel, BoxLayout.Y_AXIS));
        configTabbedPane.addTab("DVS Config", dvsPanel);
        SPIConfigValue.addGUIControls(dvsPanel, dvsControl);
        // APS
        final JPanel apsPanel = new JPanel();
        apsPanel.setLayout(new BoxLayout(apsPanel, BoxLayout.Y_AXIS));
        configTabbedPane.addTab("APS Config", apsPanel);
        SPIConfigValue.addGUIControls(apsPanel, apsControl);
        // IMU
        final JPanel imuControlPanel = new JPanel();
        imuControlPanel.add(new JLabel("<html>Low-level control of integrated inertial measurement unit."));
        imuControlPanel.setLayout(new BoxLayout(imuControlPanel, BoxLayout.Y_AXIS));
        imuControlPanel.add(new ImuControlPanel(this));
        configTabbedPane.addTab("IMU Config", imuControlPanel);
        // External Input
        final JPanel extPanel = new JPanel();
        extPanel.setLayout(new BoxLayout(extPanel, BoxLayout.Y_AXIS));
        configTabbedPane.addTab("External Input Config", extPanel);
        SPIConfigValue.addGUIControls(extPanel, extInControl);
        // Chip config
        final JPanel chipPanel = new JPanel();
        chipPanel.setLayout(new BoxLayout(chipPanel, BoxLayout.Y_AXIS));
        configTabbedPane.addTab("Chip Config", chipPanel);
        SPIConfigValue.addGUIControls(chipPanel, chipControl);
        // Autoexposure
        if (getChip() instanceof DavisBaseCamera) {
            final JPanel autoExposurePanel = new JPanel();
            autoExposurePanel.add(new JLabel(
                    "<html>Automatic exposure control.<p>The settings here determine when and by how much the exposure value should be changed. <p> The strategy followed attempts to avoid a sitation <b> where too many pixels are under- or over-exposed. Hover over entry fields to see explanations."));
            autoExposurePanel.setLayout(new BoxLayout(autoExposurePanel, BoxLayout.Y_AXIS));
            autoExposurePanel.add(new ParameterControlPanel(((DavisBaseCamera) getChip()).getAutoExposureController()));
            configTabbedPane.addTab("APS Autoexposure Control", autoExposurePanel);
        }
        // Video Control
        final JPanel videoControlPanel = new JPanel();
        videoControlPanel.add(new JLabel("<html>Controls display of APS video frame data"));
        videoControlPanel.setLayout(new BoxLayout(videoControlPanel, BoxLayout.Y_AXIS));
        videoParameterControlPanel = new ParameterControlPanel(getVideoControl());
        videoControlPanel.add(videoParameterControlPanel);
        configTabbedPane.addTab("Video Control", videoControlPanel);
        getVideoControl().addObserver(videoParameterControlPanel);
        getVideoControl().getContrastContoller().addObserver(videoParameterControlPanel);

        // make special dual view panel for seeing effect of userFriendlyControls on bias currents
        try {
            configTabbedPane.setSelectedIndex(getChip().getPrefs().getInt("DavisBaseCamera.bgTabbedPaneSelectedIndex", 0));
        } catch (final IndexOutOfBoundsException e) {
            configTabbedPane.setSelectedIndex(0);
        }
        // add listener to store last selected tab
        configTabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent evt) {
                tabbedPaneMouseClicked(evt);
            }
        });
    }

    @Override
    public int getAutoShotEventThreshold() {
        return autoShotThreshold;
    }

    @Override
    public float getBandwidthTweak() {
        return bandwidth;
    }

    @Override
    public float getMaxFiringRateTweak() {
        return maxFiringRate;
    }

    @Override
    public float getOnOffBalanceTweak() {
        return onOffBalance;
    }

    @Override
    public float getThresholdTweak() {
        return threshold;
    }

    @Override
    public float getBrightness() {
        return getVideoControl().getBrightness();
    }

    @Override
    public float getContrast() {
        return getVideoControl().getContrast();
    }

    @Override
    public float getGamma() {
        return getVideoControl().getGamma();
    }

    @Override
    public boolean isCaptureEventsEnabled() {
        return dvsRun.isSet();
    }

    @Override
    public boolean isCaptureFramesEnabled() {
        return apsRun.isSet();
    }

    @Override
    public boolean isDisplayEvents() {
        return getVideoControl().isDisplayEvents();
    }

    @Override
    public boolean isDisplayFrames() {
        return getVideoControl().isDisplayFrames();
    }

    @Override
    public boolean isDisplayImu() {
        return getImuControl().isDisplayImu();
    }

    @Override
    public boolean isImuEnabled() {
        return getImuControl().isImuEnabled();
    }

    @Override
    public boolean isUseAutoContrast() {
        return getVideoControl().isUseAutoContrast();
    }

    @Override
    public void loadPreferences() {
        super.loadPreferences();

        if (allPreferencesList != null) {
            for (final HasPreference hp : allPreferencesList) {
                hp.loadPreference();
            }
        }

        if (ssBiases != null) {
            for (final ShiftedSourceBiasCF sSrc : ssBiases) {
                sSrc.loadPreferences();
            }
        }

        if (ipots != null) {
            ipots.loadPreferences();
        }

        if (imuControlGUI != null) {
            imuControlGUI.loadPreference();
        }

        if (videoControl != null) {
            videoControl.loadPreference();
        }

        if (autoExposureController != null) {
            autoExposureController.loadPreferences();
        }
    }

    @Override
    public boolean isInitialized() {
        if (ipots == null) {
            log.info("returning true because ipots is null");
            return true;
        }
        for (final Pot b : ipots) {
            if (b instanceof AddressedIPotCF && ((AddressedIPotCF) b).getCurrent() > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setAutoShotEventThreshold(final int threshold) {
        autoShotThreshold = threshold;
    }

    /**
     * Tweaks bandwidth around nominal value.
     *
     * @param val -1 to 1 range
     */
    @Override
    public void setBandwidthTweak(float val) {
        if (val > 1) {
            val = 1;
        } else if (val < -1) {
            val = -1;
        }
        final float old = bandwidth;
        if (old == val) {
            return;
        }
        // log.info("tweak bandwidth by " + val);
        bandwidth = val;
        final float MAX = 30;
//        pr.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        sf.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        getChip().getSupport().firePropertyChange(DVSTweaks.BANDWIDTH, old, val);
    }

    @Override
    public void setBrightness(final float brightness) {
        getVideoControl().setBrightness(brightness);
    }

    @Override
    public void setCaptureEvents(final boolean selected) {
        dvsRun.set(selected);
    }

    @Override
    public void setCaptureFramesEnabled(final boolean yes) {
        apsRun.set(yes);
    }

    @Override
    public void setContrast(final float contrast) {
        getVideoControl().setContrast(contrast);
    }

    @Override
    public void setDisplayEvents(final boolean displayEvents) {
        getVideoControl().setDisplayEvents(displayEvents);
    }

    @Override
    public void setDisplayFrames(final boolean displayFrames) {
        getVideoControl().setDisplayFrames(displayFrames);
    }

    @Override
    public void setDisplayImu(final boolean yes) {
        getImuControl().setDisplayImu(yes);
    }

    /**
     * Returns the quantization value of the frame and exposure delay minimum
     * size step in milliseconds
     *
     * @return
     */
    public float getExposureFrameDelayQuantizationMs() {
        return 1e-3f;
    }

    /**
     * Sets the exposure delay (approximately but not exactly the exposure
     * duration). The true exposure is computed from the returned APS frame
     * exposure start and exposure end timestamps.
     *
     * @param ms exposure delay in ms. If ms<0.001, then it is clipped to 1us to
     * prevent 0 exposures
     */
    public void setExposureDelayMs(final float ms) {
        int expUs = Math.round(ms * 1000);
        if (expUs < 1) {
            expUs = 1;
        }
        apsExposure.set(expUs);
    }

    /**
     * Returns the exposure duration setting in float ms.
     *
     * @return value of exposure delay. It is quantized by apsExposure to be a
     * multiple of quantization.
     * @see #getExposureFrameDelayQuantizationMs()
     */
    public float getExposureDelayMs() {
        return apsExposure.get() * .001f;
    }

    /**
     * Returns the frame interval setting in float ms.
     *
     * @return value of exposure delay. It is quantized to be a multiple of 1us.
     * @see #getExposureFrameDelayQuantizationMs()
     */
    public void setFrameIntervalMs(final float ms) {
        final int fdUs = Math.round(ms * 1000);
        apsFrameInterval.set(fdUs);
    }

    /**
     * Returns the frame interval setting in float ms.
     *
     * @return value of frame interval. It is quantized to be a multiple of 1us.
     * @see #getExposureFrameDelayQuantizationMs()
     */
    public float getFrameIntervalMs() {
        return apsFrameInterval.get() * .001f;

    }

    @Override
    public void setGamma(final float gamma) {
        getVideoControl().setGamma(gamma);
    }

    @Override
    public void setImuEnabled(final boolean yes) {
        getImuControl().setImuEnabled(yes);
    }

    /**
     * Tweaks max firing rate (refractory period), larger is shorter refractory
     * period.
     *
     * @param val -1 to 1 range
     */
    @Override
    public void setMaxFiringRateTweak(float val) {
        if (val > 1) {
            val = 1;
        } else if (val < -1) {
            val = -1;
        }
        final float old = maxFiringRate;
        if (old == val) {
            return;
        }
        maxFiringRate = val;
        final float MAX = 8, MIN = 100; // limit to approx 8 times shorter refr period than default, and 100 times longer
        refr.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MIN, MAX));
        getChip().getSupport().firePropertyChange(DVSTweaks.MAX_FIRING_RATE, old, val);
    }

    /**
     * Tweaks balance of on/off events. Increase for more ON events.
     *
     * @param val -1 to 1 range.
     */
    @Override
    public void setOnOffBalanceTweak(float val) {
        if (val > 1) {
            val = 1;
        } else if (val < -1) {
            val = -1;
        }
        final float old = onOffBalance;
        if (old == val) {
            return;
        }
        onOffBalance = val;
        final float MAX = 10;
        diff.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        getChip().getSupport().firePropertyChange(DVSTweaks.ON_OFF_BALANCE, old, val);
    }

    /**
     * Tweaks threshold, larger is higher threshold.
     *
     * @param val -1 to 1 range
     */
    @Override
    public void setThresholdTweak(float val) {
        log.info("setting threshold tweak to " + val);
        if (val > 1) {
            val = 1;
        } else if (val < -1) {
            val = -1;
        }
        final float old = threshold;
        if (old == val) {
            return;
        }
        final float MAX = 10;
        threshold = val;
        diffOn.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        diffOff.changeByRatioFromPreferred(1 / PotTweakerUtilities.getRatioTweak(val, MAX));
        getChip().getSupport().firePropertyChange(DVSTweaks.THRESHOLD, old, val);
    }

    @Override
    public void setUseAutoContrast(final boolean useAutoContrast) {
        getVideoControl().setUseAutoContrast(useAutoContrast);
    }

    @Override
    public void storePreferences() {
        for (final HasPreference hp : allPreferencesList) {
            hp.storePreference();
        }

        for (final ShiftedSourceBiasCF sSrc : ssBiases) {
            sSrc.storePreferences();
        }

        ipots.storePreferences();

        imuControlGUI.storePreference();

        videoControl.storePreference();

        if (autoExposureController != null) {
            autoExposureController.loadPreferences();
        }

        super.storePreferences();
    }

    protected void tabbedPaneMouseClicked(final MouseEvent evt) {
        getChip().getPrefs().putInt("DavisBaseCamera.bgTabbedPaneSelectedIndex", configTabbedPane.getSelectedIndex());
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
    public synchronized void update(final Observable observable, final Object object) {
        if (getHardwareInterface() != null) {
            try {
                if (getHardwareInterface() instanceof MultiCameraBiasgenHardwareInterface) {
                    for (BiasgenHardwareInterface b : ((MultiCameraBiasgenHardwareInterface) getHardwareInterface()).getBiasgens()) {
                        updateHW(observable, b);
                    }
                } else if (getHardwareInterface() instanceof CypressFX3) {
                    updateHW(observable, getHardwareInterface());
                }
            } catch (HardwareInterfaceException | IllegalStateException e) {
                log.warning(String.format("Got %s", e.toString()));
            }
        }
    }

    private static void updateHW(final Observable observable, final BiasgenHardwareInterface hwInterface) throws HardwareInterfaceException, IllegalStateException {
        if ((hwInterface != null) && (hwInterface instanceof CypressFX3)) {
            final CypressFX3 fx3HwIntf = (CypressFX3) hwInterface;

            if (observable instanceof AddressedIPotCF) {
                final AddressedIPotCF iPot = (AddressedIPotCF) observable;

                fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) iPot.getAddress(), iPot.computeCleanBinaryRepresentation());
            } else if (observable instanceof ShiftedSourceBiasCF) {
                final ShiftedSourceBiasCF iPot = (ShiftedSourceBiasCF) observable;

                fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) iPot.getAddress(), iPot.computeBinaryRepresentation());
            } else if (observable instanceof SPIConfigValue) {
                final SPIConfigValue cfgVal = (SPIConfigValue) observable;

                fx3HwIntf.spiConfigSend(cfgVal.getModuleAddr(), cfgVal.getParamAddr(), cfgVal.get());
            }
        }
    }

    /**  sends complete configuration information to multiple shift registers and off chip DACs */
    public void sendConfiguration() throws HardwareInterfaceException {
        if (!isOpen()) {
            open();
        }

        for (final SPIConfigValue spiCfg : allPreferencesList) {
            spiCfg.setChanged();
            spiCfg.notifyObservers();
        }

        for (final ShiftedSourceBiasCF sSrc : ssBiases) {
            sSrc.setChanged();
            sSrc.notifyObservers();
        }

        for (final Pot iPot : ipots.getPots()) {
            iPot.setChanged();
            iPot.notifyObservers();
        }
    }

    /**
     * see https://ieeexplore.ieee.org/document/7962235 fig 1
     */
    protected float KAPPA_N = .7f, KAPPA_P = 0.7f, CAP_RATIO = 20, THR_FAC = ((KAPPA_N / (KAPPA_P * KAPPA_P)) / CAP_RATIO);

    private float REFR_CAP = 20e-15f, REFR_VOLTAGE = (1.8f - 1f); // guesstimated from NMOSCAP with W/L=3u/1.8u and Tox
    private float C1_CAP = 131.167e-15f, C2_CAP = 5.458e-15f; // guesstimated

    @Override
    public float getOnThresholdLogE() {
        return (float) (THR_FAC * Math.log(diffOn.getCurrent() / diff.getCurrent()));
    }

    @Override
    public float getOffThresholdLogE() {
        return (float) (THR_FAC * Math.log(diffOff.getCurrent() / diff.getCurrent()));
    }

    @Override
    public float getRefractoryPeriodS() {
        float iRefr = refr.getCurrent();
        return (float) (REFR_CAP * REFR_VOLTAGE / iRefr);
    }

    @Override
    public float getPhotoreceptorSourceFollowerBandwidthHz() {
        // computed based on Davis240/346 C1=132fF and estimated SF bias current
        float iSf = sf.getCurrent(), iPr = pr.getCurrent();
        if (iSf > iPr * .5) {
            log.info(String.format("Source follower bias current (%sA) is larger than half of Photoreceptor bias current (%sA); cannot estimate cutoff frequency from SF current alone", eng.format(iSf), eng.format(iPr)));
            return Float.NaN;
        }
        // TODO note from IMU we have temperature, could account for it in thermal voltage
        // f3dB=(1/2pi) g/C=(1/2pi) Ib/UT/C in Hz where Ib is bias current, UT is thermal voltage, and C is load capacitance, and assuming SF is biased in subthreshold.
        float f3dBHz = (float) ((1 / (2 * Math.PI)) * (iSf / U_T_THERMAL_VOLTAGE_ROOM_TEMPERATURE) / C1_CAP);
        return f3dBHz;
    }

    AutoExposureController getAutoExposureController() {
        return autoExposureController;
    }

    public class VideoControl extends Observable implements Observer, HasPreference, HasPropertyTooltips {

        public boolean displayEvents = getChip().getPrefs().getBoolean(getPreferencesHeader() + "displayEvents", true);
        public boolean displayFrames = getChip().getPrefs().getBoolean(getPreferencesHeader() + "displayFrames", true);

        public boolean separateAPSByColor = getChip().getPrefs().getBoolean(getPreferencesHeader() + "separateAPSByColor", false);
        public boolean autoWhiteBalance = getChip().getPrefs().getBoolean(getPreferencesHeader() + "autoWhiteBalance", true);
        public boolean colorCorrection = getChip().getPrefs().getBoolean(getPreferencesHeader() + "colorCorrection", true);
        private boolean monochrome = getChip().getPrefs().getBoolean(getPreferencesHeader() + "monochrome", true);

        private final PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();

        public VideoControl() {
            super();
            tooltipSupport.setPropertyTooltip("displayEvents", "display DVS events");
            tooltipSupport.setPropertyTooltip("displayFrames", "display APS frames");
            tooltipSupport.setPropertyTooltip("useAutoContrast", "automatically set the display contrast for APS frames");
            tooltipSupport.setPropertyTooltip("brightness",
                    "sets the brightness for APS frames, which is the lowest level of display intensity. Default is 0.");
            tooltipSupport.setPropertyTooltip("contrast",
                    "sets the contrast for APS frames, which multiplies sample values by this quantity. Default is 1.");
            tooltipSupport.setPropertyTooltip("gamma",
                    "sets the display gamma for APS frames, which applies a power law to optimize display for e.g. monitors. Default is 1.");
            tooltipSupport.setPropertyTooltip("autoContrastControlTimeConstantMs",
                    "Time constant in ms for autocontrast control. This is the lowpasss filter time constant for min and max image values to automatically scale image to 0-1 range.");
        }

        private String getPreferencesHeader() {
            return "";
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
        public void setDisplayFrames(final boolean displayFrames) {
            final boolean old = this.displayFrames;
            this.displayFrames = displayFrames;
//            getChip().getPrefs().putBoolean(getPreferencesKey() + "displayFrames", displayFrames);
            if (((AEChip) getChip()).getAeViewer() != null) {
                ((AEChip) getChip()).getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_DISPLAY_FRAMES_ENABLED, old, displayFrames);
            if (old != displayFrames) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }
        }

        public boolean isSeparateAPSByColor() {
            return separateAPSByColor;
        }

        public boolean isAutoWhiteBalance() {
            return autoWhiteBalance;
        }

        public boolean isColorCorrection() {
            return colorCorrection;
        }

        public void setSeparateAPSByColor(final boolean separateAPSByColor) {
            boolean old = this.separateAPSByColor;
            this.separateAPSByColor = separateAPSByColor;
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_SEPARATE_COLORS, old, separateAPSByColor);
            if (old != separateAPSByColor) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }

        }

        public void setAutoWhiteBalance(final boolean autoWhiteBalance) {
            boolean old = this.autoWhiteBalance;
            this.autoWhiteBalance = autoWhiteBalance;
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_AUTO_WHITE_BALANCE, old, autoWhiteBalance);
            if (old != autoWhiteBalance) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }

        }

        public void setColorCorrection(final boolean colorCorrection) {
            boolean old = this.colorCorrection;
            this.colorCorrection = colorCorrection;
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_COLOR_CORRECTION, old, colorCorrection);
            if (old != colorCorrection) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }
        }

        public void setMonochrome(boolean monochrome) {
            boolean old = this.monochrome;
            this.monochrome = monochrome;
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_MONOCHROME, old, monochrome);
            if (old != monochrome) {
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
        public void setDisplayEvents(final boolean displayEvents) {
            final boolean old = this.displayEvents;
            this.displayEvents = displayEvents;
//            getChip().getPrefs().putBoolean(getPreferencesKey() + "displayEvents", displayEvents);
            if (((AEChip) getChip()).getAeViewer() != null) {
                ((AEChip) getChip()).getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_DISPLAY_EVENTS_ENABLED, old, displayEvents);
            if (old != displayEvents) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }
        }

        public boolean isUseAutoContrast() {
            return getContrastContoller().isUseAutoContrast();
        }

        public void setUseAutoContrast(final boolean useAutoContrast) {
            getContrastContoller().setUseAutoContrast(useAutoContrast);
        }

        public float getContrast() {
            return getContrastContoller().getContrast();
        }

        public void setContrast(final float contrast) {
            getContrastContoller().setContrast(contrast);
        }

        public float getBrightness() {
            return getContrastContoller().getBrightness();
        }

        public void setBrightness(final float brightness) {
            getContrastContoller().setBrightness(brightness);
        }

        public float getGamma() {
            return getContrastContoller().getGamma();
        }

        public void setGamma(final float gamma) {
            getContrastContoller().setGamma(gamma);
        }

        public float getAutoContrastTimeconstantMs() {
            return getContrastContoller().getAutoContrastTimeconstantMs();
        }

        public void setAutoContrastTimeconstantMs(final float tauMs) {
            getContrastContoller().setAutoContrastTimeconstantMs(tauMs);
        }

        @Override
        public void update(final Observable o, final Object arg) {
            setChanged();
            notifyObservers(arg);
        }

        /**
         * @return the propertyChangeSupport
         */
        public PropertyChangeSupport getPropertyChangeSupport() {
            return getSupport();
        }

        @Override
        public void loadPreference() {
            setDisplayFrames(getChip().getPrefs().getBoolean(getPreferencesHeader() + "displayFrames", true));
            setDisplayEvents(getChip().getPrefs().getBoolean(getPreferencesHeader() + "displayEvents", true));

            setSeparateAPSByColor(getChip().getPrefs().getBoolean(getPreferencesHeader() + "separateAPSByColor", false));
            setAutoWhiteBalance(getChip().getPrefs().getBoolean(getPreferencesHeader() + "autoWhiteBalance", true));
            setColorCorrection(getChip().getPrefs().getBoolean(getPreferencesHeader() + "colorCorrection", true));
            setMonochrome(getChip().getPrefs().getBoolean(getPreferencesHeader() + "monochrome", true));
            setSeparateAPSByColor(getChip().getPrefs().getBoolean(getPreferencesHeader() + "separateAPSByColor", false));

            if (getContrastContoller() != null) { // might not exist until constructor is finished
                getContrastContoller().loadPrefences();
            }else{
                log.warning("Could not load preferences for null ContrastController");
            }
        }

        @Override
        public void storePreference() {
            getChip().getPrefs().putBoolean(getPreferencesHeader() + "displayEvents", isDisplayEvents());
            getChip().getPrefs().putBoolean(getPreferencesHeader() + "displayFrames", isDisplayFrames());

            getChip().getPrefs().putBoolean(getPreferencesHeader() + "separateAPSByColor", isSeparateAPSByColor());
            getChip().getPrefs().putBoolean(getPreferencesHeader() + "autoWhiteBalance", isAutoWhiteBalance());
            getChip().getPrefs().putBoolean(getPreferencesHeader() + "colorCorrection", isColorCorrection());
            getChip().getPrefs().putBoolean(getPreferencesHeader() + "monochrome", isMonochrome());
            if (getContrastContoller() != null) { // might not exist until constructor is finished
                getContrastContoller().storePreferences();
            }
        }

        @Override
        public String getPropertyTooltip(final String propertyName) {
            return tooltipSupport.getPropertyTooltip(propertyName);
        }

        /**
         * @return the contrastContoller
         */
        public DavisVideoContrastController getContrastContoller() {
            if (((AEChip) getChip()).getRenderer() instanceof DavisRenderer) {
                return ((DavisRenderer) (((AEChip) getChip()).getRenderer())).getContrastController();
            }

            return null;
        }

        /**
         * @return the monochrome
         */
        public boolean isMonochrome() {
            return monochrome;
        }

    }

    public static String[] choices() {
        final String[] s = new String[ImuAccelScale.values().length];
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
     * @return the imuControl
     */
    public ImuControl getImuControl() {
        return imuControlGUI;
    }

    /**
     * @return the exposureControlRegister
     */
    public SPIConfigInt getExposureControlRegister() {
        return apsExposure;
    }

    /**
     * @return the frameDelayControlRegister
     */
    public SPIConfigInt getFrameIntervalControlRegister() {
        return apsFrameInterval;
    }

    @Override
    public boolean isSeparateAPSByColor() {
        return getVideoControl().isSeparateAPSByColor();
    }

    @Override
    public boolean isAutoWhiteBalance() {
        return getVideoControl().isAutoWhiteBalance();
    }

    @Override
    public boolean isColorCorrection() {
        return getVideoControl().isColorCorrection();
    }

    @Override
    public boolean isGlobalShutter() {
        return globalShutter.isSet();
    }

    public void setGlobalShutter(final boolean val) {
        globalShutter.set(val);
    }

    @Override
    public void setSeparateAPSByColor(final boolean yes) {
        getVideoControl().setSeparateAPSByColor(yes);
    }

    public void setAutoWhiteBalance(final boolean yes) {
        getVideoControl().setAutoWhiteBalance(yes);
    }

    static final protected AddressedIPotCF addAIPot(final AddressedIPotArray potArray, final Biasgen biasgen, final String s) {
        AddressedIPotCF ret = null;

        try {
            final String delim = ",";
            final StringTokenizer t = new StringTokenizer(s, delim);

            if (t.countTokens() != 5) {
                throw new Error("only " + t.countTokens() + " tokens in pot " + s
                        + "; use , to separate tokens for name,address,sex,type,tooltip\nsex=n|p, type=normal|cascode");
            }

            final String name = t.nextToken();

            final String addressT = t.nextToken();
            final int address = Integer.parseInt(addressT);

            final String sexT = t.nextToken();
            Pot.Sex sex = null;
            if (sexT.equalsIgnoreCase("n")) {
                sex = Pot.Sex.N;
            } else if (sexT.equalsIgnoreCase("p")) {
                sex = Pot.Sex.P;
            } else {
                throw new ParseException(s, s.lastIndexOf(sexT));
            }

            final String typeT = t.nextToken();
            Pot.Type type = null;
            if (typeT.equalsIgnoreCase("normal")) {
                type = Pot.Type.NORMAL;
            } else if (typeT.equalsIgnoreCase("cascode")) {
                type = Pot.Type.CASCODE;
            } else {
                throw new ParseException(s, s.lastIndexOf(typeT));
            }

            final String tooltip = t.nextToken();

            potArray.addPot(ret = new AddressedIPotCF(biasgen, name, address, type, sex, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
                    AddressedIPotCF.maxFineBitValue, (address + 1), tooltip));
        } catch (final Exception e) {
            throw new Error(e.toString());
        }

        return (ret);
    }

    public static SPIConfigValue getConfigValueByName(final List<SPIConfigValue> configList, final String name) {
        for (final SPIConfigValue v : configList) {
            if (v.getName().equals(name)) {
                return v;
            }
        }

        return null;
    }

    /**
     * @return the dualUserFriendlyAndBiasCurrentsViewEnabled
     */
    public boolean isDualUserFriendlyAndBiasCurrentsViewEnabled() {
        return dualUserFriendlyAndBiasCurrentsViewEnabled;
    }

    /**
     * @param dualUserFriendlyAndBiasCurrentsViewEnabled the
     * dualUserFriendlyAndBiasCurrentsViewEnabled to set
     */
    public void setDualUserFriendlyAndBiasCurrentsViewEnabled(boolean dualUserFriendlyAndBiasCurrentsViewEnabled) {
        this.dualUserFriendlyAndBiasCurrentsViewEnabled = dualUserFriendlyAndBiasCurrentsViewEnabled;
    }
}
