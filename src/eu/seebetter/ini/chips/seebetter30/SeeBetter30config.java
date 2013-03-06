/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.seebetter30;

import ch.unizh.ini.jaer.config.MuxControlPanel;
import ch.unizh.ini.jaer.config.OutputMap;
import ch.unizh.ini.jaer.config.boards.LatticeMachFX2config;
import ch.unizh.ini.jaer.config.cpld.CPLDBit;
import ch.unizh.ini.jaer.config.cpld.CPLDConfigValue;
import ch.unizh.ini.jaer.config.cpld.CPLDInt;
import ch.unizh.ini.jaer.config.dac.DAC;
import ch.unizh.ini.jaer.config.dac.DAC_AD5391;
import ch.unizh.ini.jaer.config.dac.DACchannel;
import ch.unizh.ini.jaer.config.dac.DACchannelArray;
import ch.unizh.ini.jaer.config.dac.DACpanel;
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
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.config.ApsDvsConfig;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.ParameterControlPanel;

/**
 *
 * @author Christian
 */
public class SeeBetter30config extends LatticeMachFX2config{
    
    protected ShiftedSourceBiasCF ssn, ssp;
    
    JPanel configPanel;
    JTabbedPane configTabbedPane;
    
    //*********** FX2 *********************
    // portA
    protected PortBit runCpld = new PortBit(chip, "a3", "runCpld", "(A3) Set high to run CPLD which enables event capture, low to hold logic in reset", true);
    protected PortBit extTrigger = new PortBit(chip, "a1", "extTrigger", "(A1) External trigger to debug APS statemachine", false);
    // portE
    /** Bias generator power down bit */
    protected PortBit powerDown = new PortBit(chip, "e2", "powerDown", "(E2) High to disable master bias and tie biases to default rails", false);
    protected PortBit nChipReset = new PortBit(chip, "e3", "nChipReset", "(E3) Low to reset AER circuits and hold pixels in reset, High to run", true); // shouldn't need to manipulate from host

    /** Creates a new instance of SeeBetterConfig for cDVSTest with a given hardware interface
        *@param chip the chip this biasgen belongs to
        */
    public SeeBetter30config(Chip chip) {
        super(chip);
        this.chip = (AEChip)chip;
        setName("SeeBetter30 Configuration");

        
        // port bits
        addConfigValue(nChipReset);
        addConfigValue(powerDown);
        addConfigValue(runCpld);
        addConfigValue(extTrigger);

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
            addAIPot("VBP0,p,normal,photoreceptor"); 
            addAIPot("VBP1,p,normal,source follower1");
            addAIPot("VBP2,p,normal,first stage amplifier");
            addAIPot("VBP3,p,normal,second stage amplifier"); 
            addAIPot("VBP4,p,normal,comparator");
            addAIPot("VBN1,n,normal,source follower2");
            addAIPot("VBN2,n,normal,AER delay");
            addAIPot("PixInvBn,n,normal,Pixel request inversion static inverter bias");
            addAIPot("LocalTimeOutBn,n,normal,No column request timeout");
            addAIPot("LocaBufBn,n,normal,Local buffer bias");
            addAIPot("AEPuYBp,p,normal,AER row pullup");
            addAIPot("AEPuXBp,p,normal,AER column pullup");
            addAIPot("AEPdBn,n,normal,Request encoder pulldown static current");
            addAIPot("PadFollBn,n,normal,Follower-pad buffer bias current");
//            addAIPot("AEPuYBp,p,normal,AER row pullup");
//            addAIPot("IFThrBn,n,normal,Integrate and fire intensity neuron threshold");
//            addAIPot("IFRefrBn,n,normal,Integrate and fire intensity neuron refractory period bias current");
//            addAIPot("PadFollBn,n,normal,Follower-pad buffer bias current");
//            addAIPot("apsOverflowLevel,n,normal,special overflow level bias ");
//            addAIPot("biasBuffer,n,normal,special buffer bias ");
        } catch (Exception e) {
            throw new Error(e.toString());
        }
        
        setDACchannelArray(new DACchannelArray(this));
        setDAC(new DAC_AD5391(0.0f, 3.3f, 3.3f));

        try {
            addDACchannel("DACvalue1,very important DAC value");
        } catch (Exception e) {
            throw new Error(e.toString());
        }
        
        // on-chip configuration chain
        chipConfigChain = new SeeBetter30ChipConfigChain(chip);
        chipConfigChain.addObserver(this);

        setBatchEditOccurring(true);
        loadPreference();
        setBatchEditOccurring(false);
        try {
            sendConfiguration(this);
        } catch (HardwareInterfaceException ex) {
            Logger.getLogger(SeeBetter30.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /** Momentarily puts the pixels and on-chip AER logic in reset and then releases the reset.
    * 
    */
    protected void resetChip() {
        log.info("resetting AER communication");
        nChipReset.set(false);
        nChipReset.set(true);
    }
    
    
    /**
    *
    * Overrides the default method to addConfigValue the custom control panel for configuring the SBret10 output muxes
    * and many other chip and board controls.
    *
    * @return a new panel for controlling this chip and board configuration
    */
    @Override
    public JPanel buildControlPanel() {
//            if(displayControlPanel!=null) return displayControlPanel;
        configPanel = new JPanel();
        configPanel.setLayout(new BorderLayout());
        // add a reset button on top of everything
        final Action resetChipAction = new AbstractAction("Reset chip") {
            {putValue(Action.SHORT_DESCRIPTION, "Resets the pixels and the AER logic momentarily");}

            @Override
            public void actionPerformed(ActionEvent evt) {
                resetChip();
            }
        };

        JPanel specialButtons = new JPanel();
        specialButtons.setLayout(new BoxLayout(specialButtons, BoxLayout.X_AXIS));
        specialButtons.add(new JButton(resetChipAction));
        configPanel.add(specialButtons, BorderLayout.NORTH);

        configTabbedPane = new JTabbedPane();
        setBatchEditOccurring(true); // stop updates on building panel
        
        //biasgen
        JPanel combinedBiasShiftedSourcePanel = new JPanel();
        combinedBiasShiftedSourcePanel.setLayout(new BoxLayout(combinedBiasShiftedSourcePanel, BoxLayout.Y_AXIS));
        combinedBiasShiftedSourcePanel.add(super.buildControlPanel());
        combinedBiasShiftedSourcePanel.add(new ShiftedSourceControlsCF(ssn));
        combinedBiasShiftedSourcePanel.add(new ShiftedSourceControlsCF(ssp));
        configTabbedPane.addTab("Bias Current Control", combinedBiasShiftedSourcePanel);
        
        //DAC
        JPanel dacPanel = new JPanel();
        dacPanel.setLayout(new BoxLayout(dacPanel, BoxLayout.Y_AXIS));
        dacPanel.add(new DACpanel(dacChannels));
        configTabbedPane.addTab("DAC Control", dacPanel);
        
        //muxes
        configTabbedPane.addTab("Debug Output MUX control", chipConfigChain.buildMuxControlPanel());

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
    
    /** The central point for communication with HW from biasgen. All objects in SeeBetterConfig are Observables
    * and addConfigValue SeeBetterConfig.this as Observer. They then call notifyObservers when their state changes.
    * Objects such as ADC store preferences for ADC, and update should update the hardware registers accordingly.
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
            } else if (observable instanceof DACchannel){
                DACchannel p = (DACchannel) observable;
                sendDACchannel(p);
            } else if (observable instanceof OutputMux || observable instanceof OnchipConfigBit) {
                sendOnChipConfigChain();
            } else if (observable instanceof SeeBetter30ChipConfigChain) {
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
                sendAIPot((AddressedIPot)observable);
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
    * Formats bits represented in a string as '0' or '1' as a byte array to be sent over the interface to the firmware, for loading
    * in big endian bit order, in order of the bytes sent starting with byte 0.
    * <p>
    * Because the firmware writes integral bytes it is important that the 
    * bytes sent to the device are padded with leading bits 
    * (at msbs of first byte) that are finally shifted out of the on-chip shift register.
    * 
    * Therefore <code>bitString2Bytes</code> should only be called ONCE, after the complete bit string has been assembled, unless it is known
    * the other bits are an integral number of bytes.
    * 
    * @param bitString in msb to lsb order from left end, where msb will be in msb of first output byte
    * @return array of bytes to send
    */

    public class SeeBetter30ChipConfigChain extends ChipConfigChain {

        //Config Bits
        OnchipConfigBit resetCalib = new OnchipConfigBit(chip, "resetCalib", 0, "turn the calibration neuron off", true),
                typeNCalib = new OnchipConfigBit(chip, "typeNCalib", 1, "make the calibration neuron N type", false),
                resetTestpixel = new OnchipConfigBit(chip, "resetTestpixel", 2, "keeps the testpixel in reset", true),
                hotPixelSuppression = new OnchipConfigBit(chip, "hotPixelSuppression", 3, "turns on the hot pixel suppression", false),
                nArow = new OnchipConfigBit(chip, "nArow", 4, "use nArow in the AER state machine", false),
                useAout = new OnchipConfigBit(chip, "useAout", 5, "turn the pads for the analog MUX outputs on", true)
                ;

        //Muxes
        OutputMux[] amuxes = {new AnalogOutputMux(1), new AnalogOutputMux(2), new AnalogOutputMux(3)};
        OutputMux[] dmuxes = {new DigitalOutputMux(1), new DigitalOutputMux(2), new DigitalOutputMux(3), new DigitalOutputMux(4)};
        OutputMux[] bmuxes = {new DigitalOutputMux(0)};
        ArrayList<OutputMux> muxes = new ArrayList();
        MuxControlPanel controlPanel = null;

        public SeeBetter30ChipConfigChain(Chip chip){  
            super(chip);
            this.sbChip = chip;

            TOTAL_CONFIG_BITS = 24;
            
            hasPreferenceList.add(this);
            configBits = new OnchipConfigBit[6];
            configBits[0] = resetCalib;
            configBits[1] = typeNCalib;
            configBits[2] = resetTestpixel;
            configBits[3] = hotPixelSuppression;
            configBits[4] = nArow;
            configBits[5] = useAout;
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

            bmuxes[0].put(0,"IFThrBn");
            bmuxes[0].put(1,"AEPuYBp");
            bmuxes[0].put(2,"AEPuXBp");
            bmuxes[0].put(3,"LColTimeout");
            bmuxes[0].put(4,"AEPdBn");
            bmuxes[0].put(5,"RefrBp");
            bmuxes[0].put(6,"PrSFBp");
            bmuxes[0].put(7,"PrBp");
            bmuxes[0].put(8,"PixInvBn");
            bmuxes[0].put(9,"LocalBufBn");
            bmuxes[0].put(10,"ApsROSFBn");
            bmuxes[0].put(11,"DiffCasBnc");
            bmuxes[0].put(12,"ApsCasBpc");
            bmuxes[0].put(13,"OffBn");
            bmuxes[0].put(14,"OnBn");
            bmuxes[0].put(15,"DiffBn");

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
                super(sbChip, 4, 8, (OutputMap)(new VoltageOutputMap()));
                setName("Voltages" + n);
            }
        }

        class DigitalOutputMux extends OutputMux {

            DigitalOutputMux(int n) {
                super(sbChip, 4, 16, (OutputMap)(new DigitalOutputMap()));
                setName("LogicSignals" + n);
            }
        }

        @Override
        public String getBitString(){
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

        String getMuxBitString(OutputMux[] muxs){
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
        public JPanel getChipConfigPanel(){
            JPanel chipConfigPanel = new JPanel(new BorderLayout());

            //On-Chip config bits
            JPanel extraPanel = new JPanel();
            extraPanel.setLayout(new BoxLayout(extraPanel, BoxLayout.Y_AXIS));
            for (OnchipConfigBit b : configBits) {
                extraPanel.add(new JRadioButton(b.getAction()));
            }
            extraPanel.setBorder(new TitledBorder("Extra on-chip bits"));
            chipConfigPanel.add(extraPanel, BorderLayout.NORTH);

            //FX2 port bits
            JPanel portBitsPanel = new JPanel();
            portBitsPanel.setLayout(new BoxLayout(portBitsPanel, BoxLayout.Y_AXIS));
            for (PortBit p : portBits) {
                portBitsPanel.add(new JRadioButton(p.getAction()));
            }
            portBitsPanel.setBorder(new TitledBorder("Cypress FX2 port bits"));
            chipConfigPanel.add(portBitsPanel, BorderLayout.CENTER);

            return chipConfigPanel;
        }
    }
}
