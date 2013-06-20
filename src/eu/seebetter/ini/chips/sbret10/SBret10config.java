/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.sbret10;

import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import ch.unizh.ini.jaer.config.MuxControlPanel;
import ch.unizh.ini.jaer.config.OutputMap;
import ch.unizh.ini.jaer.config.boards.LatticeMachFX2config;
import static ch.unizh.ini.jaer.config.boards.LatticeMachFX2config.VR_WRITE_CONFIG;
import ch.unizh.ini.jaer.config.cpld.CPLDBit;
import ch.unizh.ini.jaer.config.cpld.CPLDConfigValue;
import ch.unizh.ini.jaer.config.cpld.CPLDInt;
import ch.unizh.ini.jaer.config.fx2.PortBit;
import ch.unizh.ini.jaer.config.fx2.TriStateablePortBit;
import ch.unizh.ini.jaer.config.onchip.ChipConfigChain;
import ch.unizh.ini.jaer.config.onchip.OnchipConfigBit;
import ch.unizh.ini.jaer.config.onchip.OutputMux;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import net.sf.jaer.biasgen.*;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.config.ApsDvsConfig;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.ApsDvsHardwareInterface;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.ParameterControlPanel;
import net.sf.jaer.util.PropertyTooltipSupport;

/**
 * Bias generator, On-chip diagnostic readout, video acquisition and rendering
 * controls for the apsDVS vision sensor.
 *
 * @author Christian/Tobi
 */
public class SBret10config extends LatticeMachFX2config implements ApsDvsConfig, ApsDvsTweaks {

    private static final float EXPOSURE_CONTROL_CLOCK_FREQ_HZ = 30000000 / 1025; // this is actual clock freq in Hz of clock that controls timing of inter-frame delay and exposure delay
    protected ShiftedSourceBiasCF ssn, ssp;
    JPanel configPanel;
    JTabbedPane configTabbedPane;
    //*********** FX2 *********************
    // portA
    protected PortBit runCpld = new PortBit(chip, "a3", "runCpld", "(A3) Set high to run CPLD which enables event capture, low to hold logic in reset", true);
    protected PortBit extTrigger = new PortBit(chip, "a1", "extTrigger", "(A1) External trigger to debug APS statemachine", false);
    // portC
    protected PortBit runAdc = new PortBit(chip, "c0", "runAdc", "(C0) High to run ADC", true);
    // portE
    /**
     * Bias generator power down bit
     */
    protected PortBit powerDown = new PortBit(chip, "e2", "powerDown", "(E2) High to disable master bias and tie biases to default rails", false);
    protected PortBit nChipReset = new PortBit(chip, "e3", "nChipReset", "(E3) Low to reset AER circuits and hold pixels in reset, High to run", true); // shouldn't need to manipulate from host
    //*********** CPLD *********************
    // CPLD shift register contents specified here by CPLDInt and CPLDBit
    protected CPLDInt exposure = new CPLDInt(chip, 15, 0, "exposure", "time between reset and readout of a pixel", 0);
    protected CPLDInt colSettle = new CPLDInt(chip, 31, 16, "colSettle", "time in 30MHz clock cycles to settle after column select before readout", 0);
    protected CPLDInt rowSettle = new CPLDInt(chip, 47, 32, "rowSettle", "time in 30MHz clock cycles to settle after row select before readout", 0);
    protected CPLDInt resSettle = new CPLDInt(chip, 63, 48, "resSettle", "time in 30MHz clock cycles  to settle after column reset before readout", 0);
    protected CPLDInt frameDelay = new CPLDInt(chip, 79, 64, "frameDelay", "time between two frames", 0);
    protected CPLDInt padding = new CPLDInt(chip, 93, 80, "pad", "used to insert necessary unused (but should be zero) bits", 0);
    protected CPLDBit testPixAPSread = new CPLDBit(chip, 94, "testPixAPSread", "enables continuous scanning of testpixel", false);
    protected CPLDBit sbret10 = new CPLDBit(chip, 95, "sbret10", "puts TX high (as needed in SBRet10)", true);
    // DVSTweaks
    private AddressedIPotCF diffOn, diffOff, refr, pr, sf, diff;
    // graphic options for rendering
    protected VideoControl videoControl;
//        private Scanner scanner; 
    protected ApsReadoutControl apsReadoutControl;
    private int autoShotThreshold; // threshold for triggering a new frame snapshot automatically

    /**
     * Creates a new instance of chip configuration
     *
     * @param chip the chip this configuration belongs to
     */
    public SBret10config(Chip chip) {
        super(chip);
        this.chip = (AEChip) chip;
        setName("SBret10 Configuration");


        // port bits
        addConfigValue(nChipReset);
        addConfigValue(powerDown);
        addConfigValue(runAdc);
        addConfigValue(runCpld);
        addConfigValue(extTrigger);

        // cpld shift register stuff
        addConfigValue(exposure);
        addConfigValue(resSettle);
        addConfigValue(rowSettle);
        addConfigValue(colSettle);
        addConfigValue(frameDelay);
        addConfigValue(padding);
        addConfigValue(testPixAPSread);
        addConfigValue(sbret10);

        // masterbias
        getMasterbias().setKPrimeNFet(55e-3f); // estimated from tox=42A, mu_n=670 cm^2/Vs // TODO fix for UMC18 process
        getMasterbias().setMultiplier(4);  // =45  correct for dvs320
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
            //private AddressedIPotCF diffOn, diffOff, refr, pr, sf, diff;
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

        //graphicOptions
        videoControl = new VideoControl();
        videoControl.addObserver(this);

        // on-chip configuration chain
        chipConfigChain = new SBRet10ChipConfigChain(chip);
        chipConfigChain.addObserver(this);

        // control of log readout
        apsReadoutControl = new ApsReadoutControl();

        setBatchEditOccurring(true);
        loadPreference();
        setBatchEditOccurring(false);
        try {
            sendConfiguration(this);
        } catch (HardwareInterfaceException ex) {
            Logger.getLogger(SBret10.class.getName()).log(Level.SEVERE, null, ex);
        }
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
    private boolean debugControls = false;
    JPanel userFriendlyControls;

    /**
     *
     * Overrides the default to built the custom control panel for configuring
     * the SBret10 output multiplexers and many other chip, board and display
     * controls.
     *
     * @return a new panel for controlling this chip and board configuration
     */
    @Override
    public JPanel buildControlPanel() {
//            if(displayControlPanel!=null) return displayControlPanel;
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

        userFriendlyControls = new ApsDVSUserControlPanel(chip);
        if (debugControls) {
            configPanel.add(userFriendlyControls, BorderLayout.EAST);
        } else {

            // user friendly control panel
            configTabbedPane.add("User-Friendly Controls", userFriendlyControls);
        }

        //graphics
        JPanel videoControlPanel = new JPanel();
        videoControlPanel.setLayout(new BoxLayout(videoControlPanel, BoxLayout.Y_AXIS));
        configTabbedPane.add("Video Control", videoControlPanel);
        videoControlPanel.add(new ParameterControlPanel(videoControl));

        //biasgen
        JPanel combinedBiasShiftedSourcePanel = new JPanel();
        combinedBiasShiftedSourcePanel.setLayout(new BoxLayout(combinedBiasShiftedSourcePanel, BoxLayout.Y_AXIS));
        combinedBiasShiftedSourcePanel.add(super.buildControlPanel());
        combinedBiasShiftedSourcePanel.add(new ShiftedSourceControlsCF(ssn));
        combinedBiasShiftedSourcePanel.add(new ShiftedSourceControlsCF(ssp));
        configTabbedPane.addTab("Bias Current Control", combinedBiasShiftedSourcePanel);

        //muxes
        configTabbedPane.addTab("Debug Output MUX control", chipConfigChain.buildMuxControlPanel());

        //aps readout
        JPanel apsReadoutPanel = new JPanel();
        apsReadoutPanel.setLayout(new BoxLayout(apsReadoutPanel, BoxLayout.Y_AXIS));
        configTabbedPane.add("APS Readout Control", apsReadoutPanel);
        apsReadoutPanel.add(new ParameterControlPanel(apsReadoutControl));

        //chip config
        JPanel chipConfigPanel = chipConfigChain.getChipConfigPanel();
        configTabbedPane.addTab("Chip configuration", chipConfigPanel);

        configPanel.add(configTabbedPane, BorderLayout.CENTER);
        // only select panel after all added

        try {
            configTabbedPane.setSelectedIndex(chip.getPrefs().getInt("SBret10.bgTabbedPaneSelectedIndex", 0));
        } catch (IndexOutOfBoundsException e) {
            configTabbedPane.setSelectedIndex(0);
        }
        // add listener to store last selected tab

        configTabbedPane.addMouseListener(
                new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tabbedPaneMouseClicked(evt);
            }
        });
        setBatchEditOccurring(false);
        return configPanel;
    }

    private void toggleDebugControls() {
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
            configTabbedPane.setSelectedComponent(userFriendlyControls);        }
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
    synchronized public void update(Observable observable, Object object) {  // thread safe to ensure gui cannot retrigger this while it is sending something
        // sends a vendor request depending on type of update
        // vendor request is always VR_CONFIG
        // value is the type of update
        // index is sometimes used for 16 bitmask updates
        // bytes are the rest of data
        if (isBatchEditOccurring()) {
            return;
        }
//            log.info("update with " + observable);
        try {
            if (observable instanceof IPot || observable instanceof VPot) { // must send all of the onchip shift register values to replace shift register contents
                sendOnChipConfig();
            } else if (observable instanceof OutputMux || observable instanceof OnchipConfigBit) {
                sendOnChipConfigChain();
            } else if (observable instanceof ShiftedSourceBiasCF) {
                sendOnChipConfig();
            } else if (observable instanceof SBRet10ChipConfigChain) {
                sendOnChipConfigChain();
            } else if (observable instanceof Masterbias) {
                powerDown.set(getMasterbias().isPowerDownEnabled());
            } else if (observable instanceof TriStateablePortBit) { // tristateable should come first before configbit since it is subclass
                TriStateablePortBit b = (TriStateablePortBit) observable;
                byte[] bytes = {(byte) ((b.isSet() ? (byte) 1 : (byte) 0) | (b.isHiZ() ? (byte) 2 : (byte) 0))};
                sendFx2ConfigCommand(CMD_SETBIT, b.getPortbit(), bytes); // sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in MSB/LSB, byte[0]= OR of value (1,0), hiZ=2/0, bit is set if tristate, unset if driving port
            } else if (observable instanceof PortBit) {
                PortBit b = (PortBit) observable;
                byte[] bytes = {b.isSet() ? (byte) 1 : (byte) 0};
                sendFx2ConfigCommand(CMD_SETBIT, b.getPortbit(), bytes); // sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in MSB/LSB, byte[0]=value (1,0)
            } else if (observable instanceof CPLDConfigValue) {
                sendCPLDConfig();
            } else if (observable instanceof AddressedIPot) {
                sendAIPot((AddressedIPot) observable);
            } else {
                super.update(observable, object);  // super (SeeBetterConfig) handles others, e.g. masterbias
            }
        } catch (HardwareInterfaceException e) {
            log.warning("On update() caught " + e.toString());
        }
    }

    private void tabbedPaneMouseClicked(java.awt.event.MouseEvent evt) {
        chip.getPrefs().putInt("SBret10.bgTabbedPaneSelectedIndex", configTabbedPane.getSelectedIndex());
    }

    /**
     * Controls the APS intensity readout by wrapping the relevant bits
     */
    public class ApsReadoutControl extends Observable implements Observer, HasPropertyTooltips {

        int channel = chip.getPrefs().getInt("ADC.channel", 3);
        public final String EVENT_ADC_ENABLED = "adcEnabled", EVENT_ADC_CHANNEL = "adcChannel";
        private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        public final String EVENT_TESTPIXEL = "testpixelEnabled";
        PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();

        public ApsReadoutControl() {
            rowSettle.addObserver(this);
            colSettle.addObserver(this);
            exposure.addObserver(this);
            resSettle.addObserver(this);
            frameDelay.addObserver(this);
            testPixAPSread.addObserver(this);
            runAdc.addObserver(this);
            sbret10.addObserver(this);
            // TODO awkward renaming of properties here due to wrongly named delegator methods
            tooltipSupport.setPropertyTooltip("adcEnabled", runAdc.getDescription());
            tooltipSupport.setPropertyTooltip("rowSettleCC", rowSettle.getDescription());
            tooltipSupport.setPropertyTooltip("colSettleCC", colSettle.getDescription());
            tooltipSupport.setPropertyTooltip("exposureDelayCC", exposure.getDescription());
            tooltipSupport.setPropertyTooltip("resSettleCC", resSettle.getDescription());
            tooltipSupport.setPropertyTooltip("frameDelayCC", frameDelay.getDescription());
            tooltipSupport.setPropertyTooltip("testPixelScanEnabled", testPixAPSread.getDescription());
            tooltipSupport.setPropertyTooltip("sbret10", sbret10.getDescription());
        }

        public boolean isAdcEnabled() {
            return runAdc.isSet();
        }

        public void setAdcEnabled(boolean yes) {
            runAdc.set(yes);
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

        public void setFrameDelayCC(int cc) {
            frameDelay.set(cc);
        }

        public void setExposureDelayCC(int cc) {
            exposure.set(cc);
        }

        public boolean isTestpixelScanEnabled() {
            return testPixAPSread.isSet();
        }

        public void setTestpixelScanEnabled(boolean testpixel) {
            testPixAPSread.set(testpixel);
        }

        public boolean isSbret10() {
            return sbret10.isSet();
        }

        public void setSbret10(boolean useSbret10) {
            sbret10.set(useSbret10);
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

        public int getFrameDelayCC() {
            return frameDelay.get();
        }

        public int getExposureDelayCC() {
            return exposure.get();
        }

        @Override
        public void update(Observable o, Object arg) {
            if (o == runAdc) {
                propertyChangeSupport.firePropertyChange(EVENT_ADC_ENABLED, null, runAdc.isSet());
            } // TODO
            setChanged();
            notifyObservers(o);
        }

        /**
         * @return the propertyChangeSupport
         */
        public PropertyChangeSupport getPropertyChangeSupport() {
            return propertyChangeSupport;
        }

        @Override
        public String getPropertyTooltip(String propertyName) {
            return tooltipSupport.getPropertyTooltip(propertyName);
        }
    }

    public class VideoControl extends Observable implements Observer, HasPreference, HasPropertyTooltips {

        private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        public boolean displayEvents = chip.getPrefs().getBoolean("VideoControl.displayEvents", true);
        public boolean displayFrames = chip.getPrefs().getBoolean("VideoControl.displayFrames", true);
        public boolean useAutoContrast = chip.getPrefs().getBoolean("VideoControl.useAutoContrast", false);
        public float contrast = chip.getPrefs().getFloat("VideoControl.contrast", 1.0f);
        public float brightness = chip.getPrefs().getFloat("VideoControl.brightness", 0.0f);
        private float gamma = chip.getPrefs().getFloat("VideoControl.gamma", 1); // gamma control for improving display on crappy beamer output
        private PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();

        public VideoControl() {
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
            if (this.displayFrames != displayFrames) {
                setChanged();
            }
            this.displayFrames = displayFrames;
            chip.getPrefs().putBoolean("VideoControl.displayFrames", displayFrames);
            chip.getAeViewer().interruptViewloop();
            notifyObservers();
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
            if (this.displayEvents != displayEvents) {
                setChanged();
            }
            this.displayEvents = displayEvents;
            chip.getPrefs().putBoolean("VideoControl.displayEvents", displayEvents);
            chip.getAeViewer().interruptViewloop();
            notifyObservers();
        }

        /**
         * @return the displayEvents
         */
        public boolean isUseAutoContrast() {
            return useAutoContrast;
        }

        /**
         * @param displayEvents the displayEvents to set
         */
        public void setUseAutoContrast(boolean useAutoContrast) {
            if (this.useAutoContrast != useAutoContrast) {
                setChanged();
            }
            this.useAutoContrast = useAutoContrast;
            chip.getPrefs().putBoolean("VideoControl.useAutoContrast", useAutoContrast);
            chip.getAeViewer().interruptViewloop();
            notifyObservers();
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
            if (this.contrast != contrast) {
                setChanged();
            }
            this.contrast = contrast;
            chip.getPrefs().putFloat("VideoControl.contrast", contrast);
            chip.getAeViewer().interruptViewloop();
            notifyObservers();
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
            if (this.brightness != brightness) {
                setChanged();
            }
            this.brightness = brightness;
            chip.getPrefs().putFloat("VideoControl.brightness", brightness);
            chip.getAeViewer().interruptViewloop();
            notifyObservers();
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
            if (gamma != this.gamma) {
                setChanged();
            }
            this.gamma = gamma;
            chip.getPrefs().putFloat("VideoControl.gamma", gamma);
            chip.getAeViewer().interruptViewloop();
            notifyObservers();
        }

        @Override
        public void update(Observable o, Object arg) {
            setChanged();
            notifyObservers(arg);
//            if (o == ) {
//                propertyChangeSupport.firePropertyChange(EVENT_GRAPHICS_DISPLAY_INTENSITY, null, runAdc.isSet());
//            } // TODO
        }

        /**
         * @return the propertyChangeSupport
         */
        public PropertyChangeSupport getPropertyChangeSupport() {
            return propertyChangeSupport;
        }

        @Override
        public void loadPreference() {
            displayFrames = chip.getPrefs().getBoolean("VideoControl.displayFrames", true);
            displayEvents = chip.getPrefs().getBoolean("VideoControl.displayEvents", true);
            useAutoContrast = chip.getPrefs().getBoolean("VideoControl.useAutoContrast", false);
            contrast = chip.getPrefs().getFloat("VideoControl.contrast", 1.0f);
            brightness = chip.getPrefs().getFloat("VideoControl.brightness", 0.0f);
            gamma = chip.getPrefs().getFloat("VideoControl.gamma", 1f);
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

    @Override
    public boolean isDisplayFrames() {
        return videoControl.isDisplayFrames();
    }

    @Override
    public void setDisplayFrames(boolean displayFrames) {
        videoControl.setDisplayFrames(displayFrames);
    }

    @Override
    public boolean isDisplayEvents() {
        return videoControl.isDisplayEvents();
    }

    @Override
    public void setDisplayEvents(boolean displayEvents) {
        videoControl.setDisplayEvents(displayEvents);
    }

    @Override
    public boolean isUseAutoContrast() {
        return videoControl.isUseAutoContrast();
    }

    @Override
    public void setUseAutoContrast(boolean useAutoContrast) {
        videoControl.setUseAutoContrast(useAutoContrast);
    }

    @Override
    public float getContrast() {
        return videoControl.getContrast();
    }

    @Override
    public void setContrast(float contrast) {
        videoControl.setContrast(contrast);
    }

    @Override
    public float getBrightness() {
        return videoControl.getBrightness();
    }

    @Override
    public void setBrightness(float brightness) {
        videoControl.setBrightness(brightness);
    }

    @Override
    public float getGamma() {
        return videoControl.getGamma();
    }

    @Override
    public void setGamma(float gamma) {
        videoControl.setGamma(gamma);
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
     * Therefore
     * <code>bitString2Bytes</code> should only be called ONCE, after the
     * complete bit string has been assembled, unless it is known the other bits
     * are an integral number of bytes.
     *
     * @param bitString in msb to lsb order from left end, where msb will be in
     * msb of first output byte
     * @return array of bytes to send
     */
    public class SBRet10ChipConfigChain extends ChipConfigChain {

        //Config Bits
        OnchipConfigBit resetCalib = new OnchipConfigBit(chip, "resetCalib", 0, "turn the calibration neuron off", true),
                typeNCalib = new OnchipConfigBit(chip, "typeNCalib", 1, "make the calibration neuron N type", false),
                resetTestpixel = new OnchipConfigBit(chip, "resetTestpixel", 2, "keeps the testpixel in reset", true),
                hotPixelSuppression = new OnchipConfigBit(chip, "hotPixelSuppression", 3, "turns on the hot pixel suppression", false),
                nArow = new OnchipConfigBit(chip, "nArow", 4, "use nArow in the AER state machine", false),
                useAout = new OnchipConfigBit(chip, "useAout", 5, "turn the pads for the analog MUX outputs on", true),
                globalShutter = new OnchipConfigBit(chip, "globalShutter", 6, "use the global shutter or not", false);
        //Muxes
        OutputMux[] amuxes = {new AnalogOutputMux(1), new AnalogOutputMux(2), new AnalogOutputMux(3)};
        OutputMux[] dmuxes = {new DigitalOutputMux(1), new DigitalOutputMux(2), new DigitalOutputMux(3), new DigitalOutputMux(4)};
        OutputMux[] bmuxes = {new DigitalOutputMux(0)};
        ArrayList<OutputMux> muxes = new ArrayList();
        MuxControlPanel controlPanel = null;

        public SBRet10ChipConfigChain(Chip chip) {
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
            muxes.addAll(Arrays.asList(dmuxes)); // 4 digital muxes, first in list since at end of chain - bits must be sent first, before any biasgen bits
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
                super(sbChip, 4, 8, (OutputMap) (new VoltageOutputMap()));
                setName("Voltages" + n);
            }
        }

        class DigitalOutputMux extends OutputMux {

            DigitalOutputMux(int n) {
                super(sbChip, 4, 16, (OutputMap) (new DigitalOutputMap()));
                setName("LogicSignals" + n);
            }
        }

        @Override
        public String getBitString() {
            //System.out.print("dig muxes ");
            String dMuxBits = getMuxBitString(dmuxes);
            //System.out.print("config bits ");
            String configBits = getConfigBitString();
            //System.out.print("analog muxes ");
            String aMuxBits = getMuxBitString(amuxes);
            //System.out.print("bias muxes ");
            String bMuxBits = getMuxBitString(bmuxes);

            String chipConfigChain = (dMuxBits + configBits + aMuxBits + bMuxBits);
            //System.out.println("On chip config chain: "+chipConfigChain);

            return chipConfigChain; // returns bytes padded at end
        }

        String getMuxBitString(OutputMux[] muxs) {
            StringBuilder s = new StringBuilder();
            for (OutputMux m : muxs) {
                s.append(m.getBitString());
            }
            //System.out.println(s);
            return s.toString();
        }

        String getConfigBitString() {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < TOTAL_CONFIG_BITS - configBits.length; i++) {
                s.append("0");
            }
            for (int i = configBits.length - 1; i >= 0; i--) {
                s.append(configBits[i].isSet() ? "1" : "0");
            }
            //System.out.println(s);
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

            //On-Chip config bits
            JPanel extraPanel = new JPanel();
            extraPanel.setLayout(new BoxLayout(extraPanel, BoxLayout.Y_AXIS));
            for (OnchipConfigBit b : configBits) {
                extraPanel.add(new JRadioButton(b.getAction()));
            }
            extraPanel.setBorder(new TitledBorder("Extra on-chip bits"));
            chipConfigPanel.add(extraPanel);

            //FX2 port bits
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
                }

                @Override
                public void actionPerformed(ActionEvent evt) {
                    if (getHardwareInterface() != null) {
                        if (getHardwareInterface() instanceof ApsDvsHardwareInterface) {
                            ((ApsDvsHardwareInterface) getHardwareInterface()).setTranslateRowOnlyEvents(((AbstractButton) evt.getSource()).isSelected());
                        }
                        
                        if (getHardwareInterface() instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.ApsDvsHardwareInterface) {
                            ((net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.ApsDvsHardwareInterface) getHardwareInterface()).setTranslateRowOnlyEvents(((AbstractButton) evt.getSource()).isSelected());
                        }
                    };
                }
            };
            eventTranslationControlPanel.add(new JRadioButton(translateRowOnlyEventsAction));
            chipConfigPanel.add(eventTranslationControlPanel);

            return chipConfigPanel;
        }
    }
    // DVSTweasks from DVS128
    private float bandwidth = 1, maxFiringRate = 1, threshold = 1, onOffBalance = 1;

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
        float old = bandwidth;
        if (old == val) {
            return;
        }
//        log.info("tweak bandwidth by " + val);
        bandwidth = val;
        final float MAX = 30;
        pr.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        sf.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        chip.getSupport().firePropertyChange(DVSTweaks.BANDWIDTH, old, val);
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
        float old = onOffBalance;
        if (old == val) {
            return;
        }
        onOffBalance = val;
        final float MAX = 10;
        diff.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        chip.getSupport().firePropertyChange(DVSTweaks.ON_OFF_BALANCE, old, val);
    }

    @Override
    public float getBandwidthTweak() {
        return bandwidth;
    }

    @Override
    public float getThresholdTweak() {
        return threshold;
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
    public void setFrameDelayMs(int ms) {
        int fd = (int) Math.round(ms * EXPOSURE_CONTROL_CLOCK_FREQ_HZ * 1e-3f);
        // frame delay config register is in clock cycles, to to go from ms to clock cycles do ms*(clockcycles/sec)*(sec/1000ms)
        frameDelay.set(fd);
    }

    @Override
    public int getFrameDelayMs() {
        // to get frame delay in ms from register value, 
        // multiply frame delay register value frameDelay in cycles by the ms per clock cycle
        int fd = (int) Math.round(frameDelay.get() / (1e-3f * EXPOSURE_CONTROL_CLOCK_FREQ_HZ));
        return fd;
    }

    @Override
    public void setExposureDelayMs(int ms) {
        int exp = (int) Math.round(ms * EXPOSURE_CONTROL_CLOCK_FREQ_HZ * 1e-3f);
        if (exp <= 0) {
            exp = 1;
        }
        exposure.set(exp);
    }

    @Override
    public int getExposureDelayMs() {
        int ed = (int) Math.round(exposure.get() / (1e-3f * EXPOSURE_CONTROL_CLOCK_FREQ_HZ));
        return ed;
    }

    @Override
    public void setAutoShotEventThreshold(int threshold) {
        this.autoShotThreshold = threshold;
    }

    @Override
    public int getAutoShotEventThreshold() {
        return autoShotThreshold;
    }
}
