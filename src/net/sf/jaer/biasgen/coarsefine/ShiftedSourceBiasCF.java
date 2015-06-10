/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.biasgen.coarsefine;

import java.util.prefs.PreferenceChangeEvent;

import javax.swing.JComponent;

import net.sf.jaer.biasgen.AddressedIPot;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * A shifted source voltage regulator that generates a regulated voltage near the power rail.
 * @author tobi
 */
public class ShiftedSourceBiasCF extends AddressedIPot {

    public enum OperatingMode {

        ShiftedSource(0), HiZ(1), TiedToRail(2);
        private final int bits;
        public static final int mask = 0x0003; // at head of 16 bit shift register for shifted source

        OperatingMode(int b) {
            this.bits = b;
        }

        private int bits() {
            return bits << Integer.numberOfTrailingZeros(mask);
        }
    };
    private OperatingMode operatingMode = OperatingMode.ShiftedSource;

    public enum VoltageLevel {

        SplitGate(0), SingleDiode(1), DoubleDiode(2);
        private final int bits;
        public static final int mask = 0x000C; // at end of first byte of 16 bit shift register for shifted source

        VoltageLevel(int b) {
            this.bits = b;
        }

        private int bits() {
            return bits << Integer.numberOfTrailingZeros(mask);
        }
    }
    private VoltageLevel voltageLevel = VoltageLevel.SplitGate;
    protected int refBiasMask = 0x03F0; // 6 bits for level of shifted source
    /** Bit mask for buffer bias bits */
    protected int regBiasMask = 0xFC00; // 6 bits for bias current for shifted source buffer amplifier
    /** Number of bits used for bias value */
    protected int numRefBiasBits = Integer.bitCount(refBiasMask);
    /** The number of bits specifying buffer bias current as fraction of master bias current */
    protected int numRegBiasBits = Integer.bitCount(regBiasMask);
    /** The bit value of the buffer bias current */
    protected int regBitValue = (1 << numRegBiasBits) - 1;
    /** Maximum buffer bias value (all bits on) */
    public int maxRegBitValue = (1 << numRegBiasBits) - 1;
    /** The bit value of the buffer bias current */
    protected int refBitValue = (1 << numRegBiasBits) - 1;
    /** Max bias bit value */
    public int maxRefBitValue = (1 << numRefBiasBits) - 1;

    protected final String SETREGBITVAL = "setregbitval_", SETVLEVEL = "setvlevel_", SETMODE = "setmode_", SETREFBITVAL = "setrefbitval_";

    public ShiftedSourceBiasCF(Biasgen biasgen) {
        super(biasgen);

    }

    /** Creates a new instance of IPot
     *@param biasgen
     *@param name
     *@param address the position in the shift register, 0 based, starting on end from which bits are loaded
     *@param type (NORMAL, CASCODE)
     *@param sex Sex (N, P)
     * @param lowCurrentModeEnabled bias is normal (false) or in low current mode (true)
     * @param enabled bias is enabled (true) or weakly tied to rail (false)
     * @param bitValue initial bitValue
     * @param regBitValue buffer bias bit value
     *@param displayPosition position in GUI from top (logical order)
     *@param tooltipString a String to display to user of GUI telling them what the pots does
     */
    public ShiftedSourceBiasCF(Biasgen biasgen, String name, int address,
            Type type, Sex sex, boolean lowCurrentModeEnabled, boolean enabled,
            int refBitValue, int regBitValue, int displayPosition, String tooltipString) {
        this(biasgen);
        numBits = numRefBiasBits; // should come out 16 bits=2 bytes overrides IPot value of 24
        setName(name);
        this.setType(type);
        this.setSex(sex);
        this.refBitValue = refBitValue;
        this.regBitValue = regBitValue;
        updateBitValue();
        this.displayPosition = displayPosition;
        this.tooltipString = tooltipString;
        this.address = address;
        loadPreferences(); // do this after name is set

//        System.out.println(this);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().addCommandListener(this, String.format(SETREGBITVAL + "%s <bitvalue>", getName()), "Set the bufferBitValue of shifted source " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETREFBITVAL + "%s <bitvalue>", getName()), "Set the voltage bit level of shifted source " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETVLEVEL + "%s " + getEnumOptions(VoltageLevel.class), getName()), "Set the type of shifted source " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETMODE + "%s " + getEnumOptions(OperatingMode.class), getName()), "Set the current level of shifted source " + getName());
        }
    }

    // returns e.g. <NORMAL|CASCODE>
    protected String getEnumOptions(final Class<? extends Enum> en) {
        StringBuilder sb = new StringBuilder("<");
        Enum[] a = en.getEnumConstants();
        for (int i = 0; i < a.length; i++) {
            Enum e = a[i];
            sb.append(e.toString());
            if (i < (a.length - 1)) {
                sb.append("|");
            }
        }
        sb.append(">");
        return sb.toString();
    }

    /** Processes custom RemoteControl commands
     */
    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {

        String[] t = input.split("\\s");
        if (t.length < 2) {
            return "? " + this + "\n";
        } else {
            try {
                String s = t[0], a = t[1];
                if (s.startsWith(SETREGBITVAL)) {
                    setRegBitValue(Integer.parseInt(a));
                } else if (s.startsWith(SETREFBITVAL)) {
                    setRefBitValue(Integer.parseInt(a));
                } else if (s.startsWith(SETMODE)) {
                    setOperatingMode(OperatingMode.valueOf(a));
                } else if (s.startsWith(SETVLEVEL)) {
                    setVoltageLevel(VoltageLevel.valueOf(a));
                }
                return this + "\n";
            } catch (NumberFormatException e) {
                log.warning("Bad number format: " + input + " caused " + e);
                return e.toString() + "\n";
            } catch (IllegalArgumentException iae) {
                log.warning("Bad command: " + input + " caused " + iae);
                return iae.toString() + "\n";
            }
        }
    }

    /** Builds the component used to control the IPot. This component is the user interface.
     * @return a JComponent that can be added to a GUI
     */
    @Override
    public JComponent makeGUIPotControl() {
        return new ShiftedSourceControlsCF(this);
    }

    public int getRefBitValue() {
        return refBitValue;
    }

    /** Set the buffer bias bit value
     * @param refBitValue the value which has maxRegBitValue as maximum and specifies fraction of master bias
     */
    public void setRefBitValue(int refBitValue) {
        int oldBitValue = this.refBitValue;
        this.refBitValue = clippedRefBitValue(refBitValue);
        updateBitValue();
        if (refBitValue != oldBitValue) {
            setChanged();
            notifyObservers(this);
        }
    }

    public int getRegBitValue() {
        return regBitValue;
    }

    /** Set the buffer bias bit value
     * @param regBitValue the value which has maxRegBitValue as maximum and specifies fraction of master bias
     */
    public void setRegBitValue(int regBitValue) {
        int oldBitValue = this.regBitValue;
        this.regBitValue = clippedRegBitValue(regBitValue);
        updateBitValue();
        if (regBitValue != oldBitValue) {
            setChanged();
            notifyObservers(this);
        }
    }

    /** returns clipped value of potential new value for buffer bit value, constrained by limits of hardware.
     *
     * @param o candidate new value.
     * @return allowed value.
     */
    protected int clippedRegBitValue(int o) {
        int n = o; // new value
        if (o < 0) {
            n = 0;
        }
        if (o > maxRegBitValue) {
            n = maxRegBitValue;
        }
        return n;
    }

    /** returns clipped value of potential new value for buffer bit value, constrained by limits of hardware.
     *
     * @param o candidate new value.
     * @return allowed value.
     */
    protected int clippedRefBitValue(int o) {
        int n = o; // new value
        if (o < 0) {
            n = 0;
        }
        if (o > maxRefBitValue) {
            n = maxRefBitValue;
        }
        return n;
    }

    /** Computes the actual bit pattern to be sent to chip based on configuration values.
     * The order of the bits from the input end of the shift register is
     * operating mode config bits, buffer bias current code bits, voltage level config bits, voltage level code bits.
     */
    public int computeBinaryRepresentation() {
        int ret = ((OperatingMode.mask & getOperatingMode().bits()) //0x0003
                | (regBiasMask& (regBitValue << Integer.numberOfTrailingZeros(regBiasMask))) // 0xfc00
                | (VoltageLevel.mask & (getVoltageLevel().bits())) // 0x0300
                | ((refBiasMask& (bitValue << Integer.numberOfTrailingZeros(refBiasMask)))
                &0xffff)); // 0x00fc
     //   System.out.println("bit value = "+Integer.toBinaryString(bitValue << Integer.numberOfTrailingZeros(refBiasMask))+" for "+this);
//        log.info("binary value="+Integer.toBinaryString(ret)+" for "+this);
        return ret;
    }
    private byte[] bytes = null;

    protected int computeInverseBinaryRepresentation(){
        int length = 16;
        int ret=computeBinaryRepresentation();
        int out=0;
        for(int i=0; i<length; i++){
            out |= (((ret&(0x0001<<(length-1-i)))<<i)>>(length-1-i));
        }
        return out;
    }


    /** returns a byte[] with the short binary representation in big endian order (MSB to LSB) of the binary representation
     * of the shifted source to be written to the SPI port.
     * The SPI routine writes bytes in the order passed from here. The bits in each byte are written in big endian order, msb to lsb.
     * @return byte[] of length 2.
     */
    @Override
    public byte[] getBinaryRepresentation() {
        int n = 3; //two plus address
        if (bytes == null) {
            bytes = new byte[n];
        }
        int val = computeBinaryRepresentation();
        int k = 1;
        for (int i = bytes.length - 2; i >= 0; i--) {
            bytes[k++] = (byte) (0xff & (val >>> (i * 8)));
        }
        bytes[0]=(byte)(0xFF & address);
        return bytes;
    }

    /** Returns the String key by which this pot is known in the Preferences. For IPot's, this
     * name is the Chip simple class name followed by IPot.<potName>, e.g. "Tmpdiff128.IPot.Pr".
     * @return preferences key
     */
    @Override
    protected String prefsKey() {
        return biasgen.getChip().getClass().getSimpleName() + ".ShiftedSourceBias." + name;
    }
    static String KEY_REFVALUE = "RefValue",
            KEY_REGVALUE = "RegBitValue",
            KEY_VOLTAGELEVEL = "VoltageLevel",
            KEY_OPERATINGMODE = "OperatingMode";
    static String SEP = ".";

    @Override
    public void preferenceChange(PreferenceChangeEvent e) {
        // TODO we get pref change events here but by this time the new values have already been set and there is no change in value so the GUI elements are not updated
        try {
            String base = prefsKey() + SEP;
            String key = e.getKey();
            if (!key.startsWith(base)) {
                return;
            }
            String val = e.getNewValue();
//            log.info("key="+key+" value="+val);
            if (key.equals(base + KEY_REFVALUE)) {
                if (getRefBitValue()!= Integer.parseInt(val)) {
                    log.info("reference voltage bit value change from preferences");
                }
                setRefBitValue(Integer.parseInt(val));
            } else if (key.equals(base + KEY_REGVALUE)) {
                if (getRegBitValue()!= Integer.parseInt(val)) {
                    log.info("regulator bit value changed from preferences");
                }
                setRegBitValue(Integer.parseInt(val));
            } else if (key.equals(base + KEY_OPERATINGMODE)) {
                setOperatingMode(OperatingMode.valueOf(val));
            } else if (key.equals(base + KEY_VOLTAGELEVEL)) {
                setVoltageLevel(VoltageLevel.valueOf(val));
            }
        } catch (Exception ex) {
            log.warning("while responding to preference change event " + e + ", caught " + ex.toString());
        }
    }

    /** stores as a preference the bit value */
    @Override
    public void storePreferences() {
        String s = prefsKey() + SEP;
        prefs.putInt(s + KEY_REFVALUE, getRefBitValue());
        prefs.putInt(s + KEY_REGVALUE, getRegBitValue());
        prefs.put(s + KEY_OPERATINGMODE, getOperatingMode().toString());
        prefs.put(s + KEY_VOLTAGELEVEL, getVoltageLevel().toString());
        setModified(false);
    }

    /** loads and makes active the preference value. The name should be set before this is called. */
    @Override
    public final void loadPreferences() {
        String s = prefsKey() + SEP;

        int bv = prefs.getInt(s + KEY_REFVALUE, 0);
        setRefBitValue(bv);
        int bbv = prefs.getInt(s + KEY_REGVALUE, maxRegBitValue);
        setRegBitValue(bbv);
        setOperatingMode(OperatingMode.valueOf(prefs.get(s + KEY_OPERATINGMODE, OperatingMode.ShiftedSource.toString())));
        setVoltageLevel(VoltageLevel.valueOf(prefs.get(s + KEY_VOLTAGELEVEL, VoltageLevel.SplitGate.toString())));
        setModified(false);
        updateBitValue();
    }

    /** returns the preference value */
    @Override
    public int getPreferedBitValue() {
        String key = prefsKey();
        int v = prefs.getInt(key, 0);
        return v;
    }



    /** sets the bit value based on desired current and {@link #masterbias} current.
     * Observers are notified if value changes.
     *@param current in amps
     *@return actual float value of current after resolution clipping.
     */
    public float setRegCurrent(float current) {
        float im = masterbias.getCurrent();
        float r = current / im;
        setRegBitValue(Math.round(r * maxRegBitValue));
        return getRegCurrent();
    }

    /** Computes the estimated current based on the bit value for the current splitter and the {@link #masterbias}
     * @return current in amps */
    public float getRegCurrent() {
        float im = masterbias.getCurrent();
        float i = (im * getRegBitValue()) / maxRegBitValue;
        return i;
    }

    /** sets the bit value based on desired current and {@link #masterbias} current.
     * Observers are notified if value changes.
     *@param current in amps
     *@return actual float value of current after resolution clipping.
     */
    public float setRefCurrent(float current) {
        float im = masterbias.getCurrent();
        float r = current / im;
        setRefBitValue(Math.round(r * maxRefBitValue));
        return getRefCurrent();
    }

    /** Computes the estimated current based on the bit value for the current splitter and the {@link #masterbias}
     * @return current in amps */
    public float getRefCurrent() {
        float im = masterbias.getCurrent();
        float i = (im * getRefBitValue()) / maxRefBitValue;
        return i;
    }

    public void updateBitValue(){
        this.bitValue = refBitValue+(regBitValue << (numRefBiasBits));
    }

    @Override
    public String toString() {
        return "ShiftedSourceBias name="+name+" Sex=" + getSex() + " bitValue=" + bitValue + " regBitValue=" + regBitValue + " refBitValue="+ refBitValue + " operatingMode=" + getOperatingMode() + " voltageLevel=" + getVoltageLevel()+ " maxRefValue="+maxRefBitValue+" maxRegBitValue="+maxRegBitValue;
    }

        /** return the max value representing all stages of current splitter enabled */
    @Override
    public int getMaxBitValue(){
        return maxRefBitValue;
    }

    /** no current: zero */
    @Override
    public int getMinBitValue(){
        return 0;
    }

    /** Overrides super of type (NORNAL or CASCODE) to call observers */
    @Override
    public final void setType(Type type) {
        if (type == null) {
            return;
        }
        if (type != this.type) {
            setChanged();
        }
        this.type = type;
        notifyObservers();
    }

    /** Overrides super of setSex (N or P) to call observers */
    @Override
    public final void setSex(Sex sex) {
        if (sex == null) {
            return;
        }
        if (sex != this.sex) {
            setChanged();
        }
        this.sex = sex;
        notifyObservers();
    }

    /** Returns true if all parameters are identical, otherwise false.
     *
     * @param obj another ConfigurableIPotRev0
     * @return true if all parameters are identical, otherwise false.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ShiftedSourceBiasCF)) {
            return false;
        }
        ShiftedSourceBiasCF other = (ShiftedSourceBiasCF) obj;
        if (!getName().equals(other.getName())) {
            return false;
        }
        if (getRefBitValue() != other.getRefBitValue()) {
            return false;
        }
        if (getRegBitValue() != other.getRegBitValue()) {
            return false;
        }
        if (getSex() != other.getSex()) {
            return false;
        }
        if (getType() != other.getType()) {
            return false;
        }

        if (getOperatingMode() != other.getOperatingMode()) {
            return false;
        }

        if (getVoltageLevel() != other.getVoltageLevel()) {
            return false;
        }
        return true;
    }

    public void setOperatingMode(OperatingMode operatingMode) {
        if (operatingMode != this.operatingMode) {
            setChanged();
        }
        this.operatingMode = operatingMode;
        notifyObservers();
    }

    public void setVoltageLevel(VoltageLevel voltageLevel) {
        if (voltageLevel != this.voltageLevel) {
            setChanged();
        }
        this.voltageLevel = voltageLevel;
        notifyObservers();
    }

    /**
     * @return the voltageLevel
     */
    public VoltageLevel getVoltageLevel() {
        return voltageLevel;
    }

    /**
     * @return the operatingMode
     */
    public OperatingMode getOperatingMode() {
        return operatingMode;
    }

}
