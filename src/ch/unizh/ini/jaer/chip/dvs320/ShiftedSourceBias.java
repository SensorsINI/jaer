/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dvs320;

import javax.swing.JComponent;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * A shifted source voltage regulator that generates a regulated voltage near the power rail.
 * @author tobi
 */
public class ShiftedSourceBias extends IPot {

    public enum OperatingMode {

        ShiftedSource(0), TiedToRail(1), HiZ(2);
        private final int bits;
        private final int mask = 0xc000; // at head of 16 bit shift register for shifted source

        OperatingMode(int b) {
            this.bits = b;
        }

        private int bits() {
            return bits << Integer.numberOfTrailingZeros(mask);
        }
    };
    protected OperatingMode operatingMode = OperatingMode.ShiftedSource;

    public enum VoltageLevel {

        SplitGate(0), SingleDiode(1), DoubleDiode(2);
        private final int bits;
        private final int mask = 0x00c0; // at start of second byte of 16 bit shift register for shifted source

        VoltageLevel(int b) {
            this.bits = b;
        }

        private int bits() {
            return bits << Integer.numberOfTrailingZeros(mask);
        }
    }
    protected VoltageLevel voltageLevel = VoltageLevel.SplitGate;
    protected static int bitValueMask = 0x003f; // 22 bits at lsb position
    /** Bit mask for buffer bias bits */
    protected static int bufferBiasMask = 0x3f00;
    /** Number of bits used for bias value */
    protected static int numBiasBits = Integer.bitCount(bitValueMask);
    /** The number of bits specifying buffer bias current as fraction of master bias current */
    protected static int numBufferBiasBits = Integer.bitCount(bufferBiasMask);
    /** The bit value of the buffer bias current */
    protected int bufferBitValue = (1 << numBufferBiasBits) - 1;
    /** Maximum buffer bias value (all bits on) */
    public static int maxBuffeBitValue = (1 << numBufferBiasBits) - 1;
    /** Max bias bit value */
    public static int maxBitValue = (1 << numBiasBits) - 1;
    protected final String SETBUFBITVAL = "setbufbitval_", SETVLEVEL = "setvlevel_", SETMODE = "setmode_", SETVBITVAL = "setvbitval_";

    public ShiftedSourceBias(Biasgen biasgen) {
        super(biasgen);
    }

    /** Creates a new instance of IPot
     *@param biasgen
     *@param name
     *@param shiftRegisterNumber the position in the shift register, 0 based, starting on end from which bits are loaded
     *@param type (NORMAL, CASCODE)
     *@param sex Sex (N, P)
     * @param lowCurrentModeEnabled bias is normal (false) or in low current mode (true)
     * @param enabled bias is enabled (true) or weakly tied to rail (false)
     * @param bitValue initial bitValue
     * @param bufferBitValue buffer bias bit value
     *@param displayPosition position in GUI from top (logical order)
     *@param tooltipString a String to display to user of GUI telling them what the pots does
     */
    public ShiftedSourceBias(Biasgen biasgen, String name, int shiftRegisterNumber,
            Type type, Sex sex, boolean lowCurrentModeEnabled, boolean enabled,
            int bitValue, int bufferBitValue, int displayPosition, String tooltipString) {
        this(biasgen);
        numBits = numBiasBits; // should come out 16 bits=2 bytes overrides IPot value of 24
        setName(name);
        this.setType(type);
        this.setSex(sex);
        this.bitValue = bitValue;
        this.bufferBitValue = bufferBitValue;
        this.displayPosition = displayPosition;
        this.tooltipString = tooltipString;
        this.shiftRegisterNumber = shiftRegisterNumber;
        loadPreferences(); // do this after name is set
        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().addCommandListener(this, String.format(SETBUFBITVAL + "%s <bitvalue>", getName()), "Set the bufferBitValue of shifted source " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETVBITVAL + "%s <bitvalue>", getName()), "Set the voltage bit level of shifted source " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETVLEVEL + "%s " + getEnumOptions(VoltageLevel.class), getName()), "Set the type of shifted source " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETMODE + "%s " + getEnumOptions(OperatingMode.class), getName()), "Set the current level of shifted source " + getName());
        }
//        System.out.println(this);
    }

    // returns e.g. <NORMAL|CASCODE>
    protected String getEnumOptions(final Class<? extends Enum> en) {
        StringBuilder sb = new StringBuilder("<");
        Enum[] a = en.getEnumConstants();
        for (int i = 0; i < a.length; i++) {
            Enum e = a[i];
            sb.append(e.toString());
            if (i < a.length - 1) {
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
                if (s.startsWith(SETBUFBITVAL)) {
                    setBufferBitValue(Integer.parseInt(a));
                } else if (s.startsWith(SETVBITVAL)) {
                    setBitValue(Integer.parseInt(a));
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
        return new ShiftedSourceControls(this);
    }

    public int getBufferBitValue() {
        return bufferBitValue;
    }

    /** Set the buffer bias bit value
     * @param bufferBitValue the value which has maxBuffeBitValue as maximum and specifies fraction of master bias
     */
    public void setBufferBitValue(int bufferBitValue) {
        int oldBitValue = this.bufferBitValue;
        this.bufferBitValue = clippedBufferBitValue(bufferBitValue);
        if (bufferBitValue != oldBitValue) {
            setChanged();
            notifyObservers(this);
        }
    }

    /** Change buffer bias current value by ratio, or at least by one bit value.
    @param ratio between new current and old value, e.g. 1.1f or 0.9f
     */
    public void changeBufferBiasByRatio(float ratio) {
        int oldv = getBufferBitValue();
        int v = Math.round(oldv * ratio);
        if (v == oldv) {
            v = v + (ratio >= 1 ? 1 : -1);
        }
        setBufferBitValue(v);
    }

    /** returns clipped value of potential new value for buffer bit value, constrained by limits of hardware.
     *
     * @param o candidate new value.
     * @return allowed value.
     */
    protected int clippedBufferBitValue(int o) {
        int n = o; // new value
        if (o < 0) {
            n = 0;
        }
        if (o > maxBuffeBitValue) {
            n = maxBuffeBitValue;
        }
        return n;
    }

    /** Computes the actual bit pattern to be sent to chip based on configuration values */
    protected int computeBinaryRepresentation() {
        int ret = operatingMode.bits() | bufferBitValue << Integer.numberOfTrailingZeros(bufferBiasMask) | voltageLevel.bits() | bitValue << Integer.numberOfTrailingZeros(bitValueMask);

        return ret;
    }
    private byte[] bytes = null;

    @Override
    public byte[] getBinaryRepresentation() {
        int n = 4;
        if (bytes == null) {
            bytes = new byte[n];
        }
        int val = computeBinaryRepresentation();
        int k = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            bytes[k++] = (byte) (0xff & (val >>> (i * 8)));
        }
        return bytes;
    }

    /** Returns the String key by which this pot is known in the Preferences. For IPot's, this
     * name is the Chip simple class name followed by IPot.<potName>, e.g. "Tmpdiff128.IPot.Pr".
     * @return preferences key
     */
    @Override
    protected String prefsKey() {
        return biasgen.getChip().getClass().getSimpleName() + ".ConfigurableIPot." + name;
    }
    static String KEY_BITVALUE = "BitValue",
            KEY_BUFFER_BITVALUE = "BufferBitValue",
            KEY_VOLTAGELEVEL = "VoltageLevel",
            KEY_OPERATINGMODE = "OperatingMode";
    static String SEP = ".";

    /** stores as a preference the bit value */
    @Override
    public void storePreferences() {
        String s = prefsKey() + SEP;
        prefs.putInt(s + KEY_BITVALUE, getBitValue());
        prefs.putInt(s + KEY_BUFFER_BITVALUE, getBufferBitValue());
        prefs.put(s + KEY_OPERATINGMODE, operatingMode.toString());
        prefs.put(s + KEY_VOLTAGELEVEL, voltageLevel.toString());
        setModified(false);
    }

    /** loads and makes active the preference value. The name should be set before this is called. */
    @Override
    public void loadPreferences() {
        String s = prefsKey() + SEP;
        bitValue = prefs.getInt(s + KEY_BITVALUE, 0);
        bufferBitValue = prefs.getInt(s + KEY_BUFFER_BITVALUE, ConfigurableIPotRev0.maxBuffeBitValue);
        operatingMode = OperatingMode.valueOf(prefs.get(s + KEY_OPERATINGMODE, OperatingMode.ShiftedSource.toString()));
        voltageLevel = VoltageLevel.valueOf(prefs.get(s + KEY_VOLTAGELEVEL, VoltageLevel.SplitGate.toString()));
        setModified(false);
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
    public float setBufferCurrent(float current) {
        float im = masterbias.getCurrent();
        float r = current / im;
        setBufferBitValue(Math.round(r * maxBuffeBitValue));
        return getBufferCurrent();
    }

    /** Computes the estimated current based on the bit value for the current splitter and the {@link #masterbias}
     * @return current in amps */
    public float getBufferCurrent() {
        float im = masterbias.getCurrent();
        float i = im * getBufferBitValue() / maxBuffeBitValue;
        return i;
    }

    public String toString() {
        return super.toString() + " Sex=" + getSex() +" bitValue="+bitValue+ " bufferBitValue=" + bufferBitValue+" operatingMode="+operatingMode+" voltageLevel="+voltageLevel;
    }

    /** Overrides super of type (NORNAL or CASCODE) to call observers */
    @Override
    public void setType(Type type) {
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
    public void setSex(Sex sex) {
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
        if (!(obj instanceof ConfigurableIPotRev0)) {
            return false;
        }
        ShiftedSourceBias other = (ShiftedSourceBias) obj;
        if (!getName().equals(other.getName())) {
            return false;
        }
        if (getBitValue() != other.getBitValue()) {
            return false;
        }
        if (getBufferBitValue() != other.getBufferBitValue()) {
            return false;
        }
        if (getSex() != other.getSex()) {
            return false;
        }
        if (getType() != other.getType()) {
            return false;
        }

        if (operatingMode != other.operatingMode) {
            return false;
        }

        if (voltageLevel != other.voltageLevel) {
            return false;
        }
        return true;
    }

    private void setOperatingMode(OperatingMode operatingMode) {
        if(operatingMode!=this.operatingMode) setChanged();
        this.operatingMode = operatingMode;
        notifyObservers();
    }

    private void setVoltageLevel(VoltageLevel voltageLevel) {
        if(voltageLevel!=this.voltageLevel) setChanged();
        this.voltageLevel = voltageLevel;
        notifyObservers();
    }
}
