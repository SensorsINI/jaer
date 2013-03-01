/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.boards;

import ch.unizh.ini.jaer.config.AbstractConfigValue;
import ch.unizh.ini.jaer.config.cpld.CPLDConfigValue;
import ch.unizh.ini.jaer.config.cpld.CPLDShiftRegister;
import ch.unizh.ini.jaer.config.fx2.PortBit;
import ch.unizh.ini.jaer.config.fx2.TriStateablePortBit;
import ch.unizh.ini.jaer.config.onchip.ChipConfigChain;
import ch.unizh.ini.jaer.config.onchip.OnchipConfigBit;
import ch.unizh.ini.jaer.config.onchip.OutputMux;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.*;
import net.sf.jaer.biasgen.AddressedIPot;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;

/**
 *
 * @author Christian
 */
public class LatticeMachFX2config extends Biasgen implements HasPreference{

    public AEChip chip;      
    protected ShiftedSourceBiasCF[] ssBiases = new ShiftedSourceBiasCF[2];
    protected ChipConfigChain chipConfigChain = null;
    protected ArrayList<HasPreference> hasPreferenceList = new ArrayList<HasPreference>();
    
    public LatticeMachFX2config(Chip chip) {
        super(chip);
        this.chip = (AEChip) chip;
    }
    
    /***************************  FX2  *************************/
    
    /** Vendor request command understood by the firmware in connection with  VENDOR_REQUEST_SEND_BIAS_BYTES */
    public final Fx2ConfigCmd CMD_IPOT = new Fx2ConfigCmd(1, "IPOT"),
            CMD_AIPOT = new Fx2ConfigCmd(2, "AIPOT"),
            CMD_SCANNER = new Fx2ConfigCmd(3, "SCANNER"),
            CMD_CHIP_CONFIG = new Fx2ConfigCmd(4, "CHIP"),
            CMD_SETBIT = new Fx2ConfigCmd(5, "SETBIT"),
            CMD_CPLD_CONFIG = new Fx2ConfigCmd(8, "CPLD");
    public final String[] CMD_NAMES = {"IPOT", "AIPOT", "SCANNER", "CHIP", "SET_BIT", "CPLD_CONFIG"};
    final byte[] emptyByteArray = new byte[0];
    
        
    /** List of direct port bits
     * 
     */
    protected ArrayList<PortBit> portBits = new ArrayList();
    
    public static final byte VR_WRITE_CONFIG = (byte) 0xB8;
    
    /** Command sent to firmware by vendor request */
    public class Fx2ConfigCmd {

        short code;
        String name;

        public Fx2ConfigCmd(int code, String name) {
            this.code = (short) code;
            this.name = name;
        }

        @Override
        public String toString() {
            return "ConfigCmd{" + "code=" + code + ", name=" + name + '}';
        }
    }
    
    /** convenience method for sending configuration to hardware. Sends vendor request VR_WRITE_CONFIG with subcommand cmd, index index and bytes bytes.
        * 
        * @param cmd the subcommand to set particular configuration, e.g. CMD_CPLD_CONFIG
        * @param index unused
        * @param bytes the payload
        * @throws HardwareInterfaceException 
        */
    protected void sendFx2ConfigCommand(Fx2ConfigCmd cmd, int index, byte[] bytes) throws HardwareInterfaceException {
        if (bytes == null) {
            bytes = emptyByteArray;
        }
//            log.info(String.format("sending command vendor request cmd=%d, index=%d, and %d bytes", cmd, index, bytes.length));
        if (getHardwareInterface() != null && getHardwareInterface() instanceof CypressFX2) {
            ((CypressFX2) getHardwareInterface()).sendVendorRequest(VR_WRITE_CONFIG, (short) (0xffff & cmd.code), (short) (0xffff & index), bytes); // & to prevent sign extension for negative shorts
        }
    }
    
    protected void sendFx2Config(){
        for (PortBit b : portBits) {
            update(b, null);
        }
    }
    
    
    /***************************  CPLD  *************************/
    
    
    /** Active container for CPLD configuration, which know how to format the data for the CPLD shift register.
     * 
     */
    protected CPLDShiftRegister cpldConfig = new CPLDShiftRegister();
    
    protected void sendCPLDConfig() throws HardwareInterfaceException {
        byte[] bytes = cpldConfig.getBytes();
//        log.info("Send CPLD Config: "+cpldConfig.toString());
        sendFx2ConfigCommand(CMD_CPLD_CONFIG, 0, bytes);
    }
    
    
    /***************************  On Chip config  *************************/
    
     /** Quick addConfigValue of an addressed pot from a string description, comma delimited
    * 
    * @param s , e.g. "Amp,n,normal,DVS ON threshold"; separate tokens for name,sex,type,tooltip\nsex=n|p, type=normal|cascode
    * @throws ParseException Error
    */
    protected void addAIPot(String s) throws ParseException {
        try {
            String d = ",";
            StringTokenizer t = new StringTokenizer(s, d);
            if (t.countTokens() != 4) {
                throw new Error("only " + t.countTokens() + " tokens in pot " + s + "; use , to separate tokens for name,sex,type,tooltip\nsex=n|p, type=normal|cascode");
            }
            String name = t.nextToken();
            String a;
            a = t.nextToken();
            Pot.Sex sex = null;
            if (a.equalsIgnoreCase("n")) {
                sex = Pot.Sex.N;
            } else if (a.equalsIgnoreCase("p")) {
                sex = Pot.Sex.P;
            } else {
                throw new ParseException(s, s.lastIndexOf(a));
            }

            a = t.nextToken();
            Pot.Type type = null;
            if (a.equalsIgnoreCase("normal")) {
                type = Pot.Type.NORMAL;
            } else if (a.equalsIgnoreCase("cascode")) {
                type = Pot.Type.CASCODE;
            } else {
                throw new ParseException(s, s.lastIndexOf(a));
            }
            String tip = t.nextToken();

            int address = getPotArray().getNumPots();
            getPotArray().addPot(new AddressedIPotCF(this, name, address++,
                    type, sex, false, true,
                    AddressedIPotCF.maxCoarseBitValue / 2, AddressedIPotCF.maxFineBitValue, address, tip));
        } catch (Exception e) {
            throw new Error(e.toString());
        }
    }
    
    protected boolean sendAIPot(AddressedIPot pot) throws HardwareInterfaceException{            
        byte[] bytes = pot.getBinaryRepresentation();
        if (bytes == null) {
            return false; // not ready yet, called by super
        }
        String hex = String.format("%02X%02X%02X",bytes[2],bytes[1],bytes[0]);
        //log.info("Send AIPot for "+pot.getName()+" with value "+hex);
        sendFx2ConfigCommand(CMD_AIPOT, 0, bytes); // the usual packing of ipots with other such as shifted sources, on-chip voltage dac, and diagnotic mux output and extra configuration
        return true;
    }
    
    /** Sends everything on the on-chip shift register 
    * 
    * @throws HardwareInterfaceException 
    * @return false if not sent because bytes are not yet initialized
    */
    protected boolean sendOnChipConfig() throws HardwareInterfaceException {
        log.info("Send whole OnChip Config");

        //biases
        if(getPotArray() == null){
            return false;
        }
        AddressedIPotArray ipots = (AddressedIPotArray) potArray;
        Iterator i = ipots.getShiftRegisterIterator();
        while(i.hasNext()){
            AddressedIPot iPot = (AddressedIPot) i.next();
            if(!sendAIPot(iPot))return false;
        }

        //shifted sources
        for (ShiftedSourceBiasCF ss : ssBiases) {
            if(!sendAIPot(ss))return false;
        }   

        //diagnose SR
        sendOnChipConfigChain();
        return true;
    }

    public boolean sendOnChipConfigChain() throws HardwareInterfaceException{

        String onChipConfigBits = chipConfigChain.getBitString();
        byte[] onChipConfigBytes = bitString2Bytes(onChipConfigBits);
        if(onChipConfigBits == null){
            return false;
        } else {
            BigInteger bi = new BigInteger(onChipConfigBits);
            //System.out.println("Send on chip config (length "+onChipConfigBits.length+" bytes): "+String.format("%0"+(onChipConfigBits.length<<1)+"X", bi));
            log.info("Send on chip config: "+onChipConfigBits);
            sendFx2ConfigCommand(CMD_CHIP_CONFIG, 0, onChipConfigBytes);
            return true;
        }
    }
    
    
    /***************************  General  *************************/

    /** List of configuration values
     * 
     */
    protected ArrayList<AbstractConfigValue> configValues = new ArrayList<AbstractConfigValue>();
    
    /** Adds a value, adding it to the appropriate internal containers, and adding this as an observer of the value.
     * 
     * @param value some configuration value
     */
    public void addConfigValue(AbstractConfigValue value) {
        if (value == null) {
            return;
        }
        configValues.add(value);
        value.addToPreferenceList(hasPreferenceList);
        if (value instanceof CPLDConfigValue) {
            cpldConfig.add((CPLDConfigValue) value);
        } else if (value instanceof PortBit) {
            portBits.add((PortBit) value);
        }
        value.addObserver(this);
        log.info("Added " + value);
    }

    /** Clears all lists of configuration values.
     * @see AbstractConfigValue
     * 
     */
    public void clearConfigValues() {
        cpldConfig.clear();
        configValues.clear();
        portBits.clear();
    }
    
    
    /** Sends complete configuration to hardware by calling several updates with objects
    * 
    * @param biasgen this object
    * @throws HardwareInterfaceException on some error
    */
    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        
        if (isBatchEditOccurring()) {
            log.info("batch edit occurring, not sending configuration yet");
            return;
        }

        log.info("sending full configuration");
        if (!sendOnChipConfig()) {
            return;
        }

        sendCPLDConfig();
        sendFx2Config();
    } 
    
    @Override
    public void loadPreference() {
        super.loadPreferences();

        if (hasPreferenceList != null) {
            for (HasPreference hp : hasPreferenceList) {
                hp.loadPreference();
            }
        }

        if (ssBiases != null) {
            for (ShiftedSourceBiasCF ss : ssBiases) {
                ss.loadPreferences();
            }
        }
    }

    @Override
    public void storePreference() {
        super.storePreferences();
        for (HasPreference hp : hasPreferenceList) {
            hp.storePreference();
        }
        if (ssBiases != null) {
            for (ShiftedSourceBiasCF ss : ssBiases) {
                ss.storePreferences();
            }
        }
    }
    
}
