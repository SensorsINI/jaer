package net.sf.jaer.biasgen.coarsefine;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.awt.Point;
import java.util.prefs.PreferenceChangeEvent;

import javax.swing.JComponent;

import net.sf.jaer.biasgen.AddressedIPot;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * An IPot with full configurability that uses coarse/fine topology as described
 * in Yang, M., Liu, S.-C., Li, C., and Delbruck, T. (2012).
 * <a href="https://drive.google.com/open?id=0BzvXOhBHjRheQmQtcUhZSl9zd3c">Addressable
 * Current Reference Array with 170dB Dynamic Range</a>. in <i>Proc. IEEE Int.
 * Symp. Circuits Syst. (ISCAS)</i>, 3110–3113. doi:10.1109/ISCAS.2012.6271979.
 *
 * The sex (N/P), type (NORMAL/CASCODE), current level (LOW,NORNAL), enabled
 * state (normal, or weakly tied to rail), buffer bias current, and bias current
 * can all be digitally configured. First implemented on TCVS320, improved on
 * DVS320, further improved in SEEBETTER sensors.
 *
 * @author tobi
 */
public class AddressedIPotCF extends AddressedIPot {
    private static EngineeringFormat engFmt=new EngineeringFormat();

    /**
     * The nominal (designed for) external resistor on the master bias
     */
    public static final float RX = 100e3f;

    /**
     * Estimation of the master bias with 100kOhm external resistor (designed
     * value); 389nA
     */
    public static final float ACTUAL_MASTER_BIAS_CURRENT = 0.000000389f;

    /**
     * Operating current level, defines whether to use shifted-source current
     * mirrors for small currents.
     */
    public enum CurrentLevel {
        Normal, Low
    }

    /**
     * This enum determines whether low-current mode is enabled. In low-current
     * mode, the bias uses shifted n or p source regulated voltages.
     */
    protected CurrentLevel currentLevel = CurrentLevel.Normal;

    /**
     * If enabled=true the bias operates normally, if enabled=false, then the
     * bias is disabled by being weakly tied to the appropriate rail (depending
     * on bias sex, N or P).
     */
    public enum BiasEnabled {
        Enabled, Disabled
    }
    protected BiasEnabled biasEnabled = BiasEnabled.Enabled;

    /**
     * The nominal ratio of coarse current between each coarse bias step change.
     */
    public static final float RATIO_COARSE_CURRENT_STEP = 8f;

    /**
     * Bit mask for flag bias enabled (normal operation) or disabled (tied
     * weakly to rail)
     */
    protected static int enabledMask = 0x0001;

    /**
     * Bit mask for flag for bias sex (N or P)
     */
    protected static int sexMask = 0x0002;

    /**
     * Bit mask for flag for bias type (normal or cascode)
     */
    protected static int typeMask = 0x0004;

    /**
     * Bit mask for flag low current mode enabled
     */
    protected static int lowCurrentModeMask = 0x0008;

    /**
     * Bit mask for fine bias current value bits
     */
    protected static int bitFineMask = 0x0FF0; // 8 bits

    /**
     * Bit mask for coarse bias current value bits
     */
    protected static int bitCoarseMask = 0x7000; // 3  bits

    /**
     * Number of bits used for fine bias value
     */
    protected static int numFineBits = Integer.bitCount(bitFineMask);

    /**
     * the current fine value of the ipot in bits loaded into the shift register
     */
    protected int fineBitValue = 0;

    /**
     * Max fine bias bit value
     */
    public static int maxFineBitValue = (1 << numFineBits) - 1;

    /**
     * Number of bits used for coarse bias value
     */
    protected static int numCoarseBits = Integer.bitCount(bitCoarseMask);

    /**
     * the current coarse value of the ipot in bits loaded into the shift
     * register
     */
    protected int coarseBitValue = 0;

    /**
     * Max bias bit value
     */
    public static int maxCoarseBitValue = (1 << numCoarseBits) - 1;

    /**
     * Number of bits used for bias value
     */
    protected static int numBiasBits = Integer.bitCount(bitFineMask) + Integer.bitCount(bitCoarseMask);

    /**
     * Max bias bit value
     */
    public static int maxBitValue = (1 << numBiasBits) - 1;

    protected final String SETIC = "setic_", SETIF = "setif_", SETSEX = "setsex_", SETTYPE = "settype_", SETLEVEL = "setlevel_", SETENABLED = "setenabled_";

    public AddressedIPotCF(Biasgen biasgen) {
        super(biasgen);
    }

    /**
     * Creates a new instance of IPot
     *
     * @param biasgen
     * @param name
     * @param address the position in the shift register, 0 based, starting on
     * end from which bits are loaded
     * @param type (NORMAL, CASCODE)
     * @param sex Sex (N, P)
     * @param lowCurrentModeEnabled bias is normal (false) or in low current
     * mode (true)
     * @param enabled bias is enabled (true) or weakly tied to rail (false)
     * @param bitValue initial bitValue
     * @param bufferBitValue buffer bias bit value
     * @param displayPosition position in GUI from top (logical order)
     * @param tooltipString a String to display to user of GUI telling them what
     * the pots does
     */
    public AddressedIPotCF(Biasgen biasgen, String name, int address,
            Type type, Sex sex, boolean lowCurrentModeEnabled, boolean enabled, int coarseValue,
            int fineValue, int displayPosition, String tooltipString) {
        this(biasgen);
        numBits = numBiasBits; // overrides IPot value of 24
        setName(name);
        this.setType(type);
        this.setSex(sex);
        this.fineBitValue = fineValue;
        this.coarseBitValue = coarseValue;
        this.currentLevel = lowCurrentModeEnabled ? CurrentLevel.Low : CurrentLevel.Normal;
        this.biasEnabled = enabled ? BiasEnabled.Enabled : BiasEnabled.Disabled;
        this.displayPosition = displayPosition;
        this.tooltipString = tooltipString;
        this.address = address;
        loadPreferences(); // do this after name is set
        updateBitValue(); // must be after preferences are loaded
        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().addCommandListener(this, String.format(SETIC + "%s <bitvalue>", getName()), "Set the bitValue of coarse current " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETIF + "%s <bitvalue>", getName()), "Set the bitValue of fine current " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETSEX + "%s " + getEnumOptions(Sex.class), getName()), "Set the sex (N|P) of " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETTYPE + "%s " + getEnumOptions(Type.class), getName()), "Set the type of IPot " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETLEVEL + "%s " + getEnumOptions(CurrentLevel.class), getName()), "Set the current level of IPot " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETENABLED + "%s " + getEnumOptions(BiasEnabled.class), getName()), "Set the current level of IPot " + getName());
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
            if (i < (a.length - 1)) {
                sb.append("|");
            }
        }
        sb.append(">");
        return sb.toString();
    }

    /**
     * Processes custom RemoteControl commands
     */
    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {

        String[] t = input.split("\\s");
        if (t.length < 2) {
            return "? " + this + "\n";
        } else {
            try {
                String s = t[0], a = t[1];
                if (s.startsWith(SETIC)) {
                    setCoarseBitValue(Integer.parseInt(a));
                } else if (s.startsWith(SETIF)) {
                    setFineBitValue(Integer.parseInt(a));
                } else if (s.startsWith(SETSEX)) {
                    setSex(Sex.valueOf(a));
                } else if (s.startsWith(SETTYPE)) {
                    setType(Type.valueOf(a));
                } else if (s.startsWith(SETLEVEL)) {
                    setCurrentLevel(CurrentLevel.valueOf(a));
                } else if (s.startsWith(SETENABLED)) {
                    setBiasEnabled(BiasEnabled.valueOf(a));
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

    /**
     * Builds the component used to control the IPot. This component is the user
     * interface.
     *
     * @return a JComponent that can be added to a GUI
     */
    @Override
    public JComponent makeGUIPotControl() {
        return new AddressedIPotCFGUIControl(this);
    }

    @Override
    public void setBitValue(int value) {
        log.warning("setting the \"bitValue\" of this coarse-fine pot has no effect");
    }

    /**
     * Minimum fractional accuracy for changing fine bit value from tweak. If
     * accuracy of change from only changing fine bit value is worse than this
     * value, then coarse bit value is also changed
     */
    public final float MIN_TWEAK_ACCURACY = 0.1f;
    
    /** Returns coarse and fine code values the result in current closest to current. The resulting current will always be larger than
     * the desired current (unless the current is larger than the maximum possible), i.e. the fine code will be higher or equal to the 
     * actual floating point fine code value, i.e. the fine code 
     * @param current
     * @return x value is coarse binary value, y value is fine binary value
     */
    public Point computeCoarseFineValueFromCurrent(float current){
        int newCoarseBinaryValue=computeSmallestCoarseBinaryValue(current);
        // fine code is now  round((desired current / coarse current ) * maxFineBitValue)
        int newFineBinaryValue= (int)Math.round((current/computeCoarseCurrent(newCoarseBinaryValue))*maxFineBitValue);
        return new Point(newCoarseBinaryValue,newFineBinaryValue);
    }

    /**
     * Change fine bit value by ratio from preferred value; can be used for a
     * tweak from a nominal value. If resulting fine bit value is too small or
     * too large, changes coarse bit value as well.
     *
     * @param ratio between new current and old value, e.g. 1.1f or 0.9f
     */
    @Override
    public void changeByRatioFromPreferred(float ratio) {
        // the final current we should end up with is by some ratio from the "preferance value"
        // but a fine code value may not get there. So we might have to iterate fine and coarse code values to get to 
        // the final values required. We do that by finding the smallest coarse value that is larger than the desired current, and
        // then computing the fine code to achieve desired value
        final float preferenceCurrent=computeCurrent(getPreferedCoarseBinaryValue(), getPreferedFineBitValue());
        final float desiredCurrent = preferenceCurrent * ratio;
        final float actualCurrent = setCurrent(desiredCurrent);
        final float fractionalDifference = (actualCurrent - desiredCurrent) / desiredCurrent;
        log.info(String.format("tweaking %s by factor %.2f; set coarse / fine binary values to %d/%d to result in current of %sA with error of %.1f%% from ideal value of %sA",
                getName(),ratio,getCoarseBinaryValue(),getFineBitValue(),engFmt.format(getCurrent()),
                        100 * fractionalDifference, engFmt.format(desiredCurrent)));
    }
    
    /** Convenience method to set both binary code values in one method
     * 
     * @param coarseBinaryValue
     * @param fineBinaryValue 
     * @see #setCoarseBinaryValue(int) 
     * @see #setFineBitValue(int) 
     */
    public void setBinaryCoarseAndFineValues(int coarseBinaryValue, int fineBinaryValue){
        setCoarseBinaryValue(coarseBinaryValue);
        setFineBitValue(fineBinaryValue);
    }

    public int getFineBitValue() {
        return fineBitValue;
    }

    /**
     * Set the fine control bias bit value
     *
     * @param bufferBitValue the value which has maxBuffeBitValue as maximum and
     * specifies fraction of master bias
     */
    public void setFineBitValue(int fineBitValue) {
        int oldBitValue = this.fineBitValue;
        this.fineBitValue = clippedFineBitValue(fineBitValue);
        updateBitValue();
        if (fineBitValue != oldBitValue) {
            setChanged();
            notifyObservers(this);
        }
        //log.info("set fine bits: "+getFineBitValue());
    }

    /**
     * returns clipped value of potential new value for buffer bit value,
     * constrained by limits of hardware.
     *
     * @param o candidate new value.
     * @return allowed value.
     */
    protected int clippedFineBitValue(int o) {
        int n = o; // new value
        if (o < 0) {
            n = 0;
        }
        if (o > maxFineBitValue) {
            n = maxFineBitValue;
        }
        return n;
    }

    /**
     * Returns the bit value, which is flipped so that 0 means largest current
     * and 7 means smallest current
     *
     * @return the bit value, which is flipped so that 0 means largest current
     * and 7 means smallest current
     * @see #getCoarseBinaryValue()
     */
    public int getCoarseBitValue() {
        return coarseBitValue;
    }

    /**
     * Set the course bias bit value. <b>Because of an initial design
     * error, the value of coarse current *decreases* as the bit value
     * increases.</b> The current is nominally the master current for a bit value of
     * 2.
     *
     * @param bufferBitValue the value
     * @see #setCoarseBinaryValue(int)
     */
    public void setCoarseBitValue(int coarseBitValue) {
        //log.info("set coarse bits: "+coarseBitValue);
        int oldBitValue = this.coarseBitValue;
        this.coarseBitValue = clippedCoarseBitValue(coarseBitValue);
        updateBitValue();
        if (coarseBitValue != oldBitValue) {
            setChanged();
            notifyObservers(this);
        }
        //log.info("coarse bits set: "+getCoarseBitValue());
    }

    /**
     * Returns the bit value, which is intuitively correct so that 0 means
     * smallest value and 7 means largest value
     *
     * @return the bit value
     */
    public int getCoarseBinaryValue() {
        return maxCoarseBitValue - coarseBitValue;

    }

    /**
     * Set the course bias bit value, where 0 is smallest current and
     * maxCoarseBitValue is largest current. 
     * The current is nominally the master current for a bit value of 5.
     *
     * @param v the value
     * @see #setCoarseBinaryValue(int)
     */
    public void setCoarseBinaryValue(int v) {
        setCoarseBitValue(maxCoarseBitValue-v);
    }

    /**
     * returns clipped value of potential new value for buffer bit value,
     * constrained by limits of hardware.
     *
     * @param o candidate new value.
     * @return allowed value.
     */
    protected int clippedCoarseBitValue(int o) {
        int n = o; // new value
        if (o < 0) {
            n = 0;
        }
        if (o > maxCoarseBitValue) {
            n = maxCoarseBitValue;
        }
        return n;
    }

    @Override
    public int getMaxBitValue() {
        return maxBitValue;
    }

    public int getMaxCoarseBitValue() {
        return maxCoarseBitValue;
    }

    public int getMaxFineBitValue() {
        return maxFineBitValue;
    }

    public void updateBitValue() {
        this.bitValue = fineBitValue + (coarseBitValue << (numFineBits));
    }

    @Override
    public int getBitValue() {
        this.bitValue = fineBitValue + (coarseBitValue << (numFineBits));
        //String hex = String.format("%02X",bitValue);
        //log.info("AIPot "+this.getName()+" bit value "+hex);
        return bitValue;
    }

    /**
     * sets the bit value based on desired current and {@link #masterbias}
     * current. Observers are notified if value changes.
     *
     * @param current in amps
     * @return actual float value of current after resolution clipping.
     */
    public float setCoarseCurrent(float current) {
        double im = ACTUAL_MASTER_BIAS_CURRENT; //TODO real MasterBias
        setCoarseBitValue(7 - (int) Math.round((Math.log(current / im) / Math.log(8)) + 5));
        return getCoarseCurrent();
    }

    /**
     * Returns estimated coarse current based on master bias current and coarse
     * bit setting
     *
     * @return current in amperes
     */
    public float getCoarseCurrent() {
        return computeCoarseCurrent(getCoarseBinaryValue());
    }

    /** Computes the coarse current value for a binary code
     * 
     * @param binaryCoarseValue the binary value, from 0 to maxCoarseBitValue, higher meaning higher current
     * @return current in amps
     */
    private float computeCoarseCurrent(int binaryCoarseValue) {
        double im = ACTUAL_MASTER_BIAS_CURRENT; //TODO real MasterBias
        float i = (float) (im * Math.pow(RATIO_COARSE_CURRENT_STEP, binaryCoarseValue - 5));
        return i;
    }

    /** Computes the smallest coarseBinaryCode that results in coarse current larger than current.
     * If current is larger than largest coarse current then returns maxCoarseBitValue
     * 
     * @param current
     * @return the binary code, from 0 to maxCoarseBitValue
     */
    private int computeSmallestCoarseBinaryValue(float current){
        // brute force
        for(int i=0;i<=maxCoarseBitValue;i++){
            if(computeCoarseCurrent(i)>=current) return i;
        }
        return maxCoarseBitValue;
    }
    
    /**
     * Increments coarse current
     *
     * @return true if change was possible, false if coarse current is already
     * maximum value
     */
    public boolean incrementCoarseCurrent() {
        if (getCoarseBinaryValue() == maxCoarseBitValue) {
            return false;
        }
        setCoarseBinaryValue(coarseBitValue + 1);
        return true;
    }

    /**
     * Decrements coarse current
     *
     * @return true if change was possible, false if coarse current is already
     * maximum value
     */
    public boolean decrementCoarseCurrent() {
        if (getCoarseBinaryValue() == 0) {
            return false;
        }
        setCoarseBinaryValue(coarseBitValue - 1);
        return true;
    }

    /**
     * Sets the value of bias current by changing the only the fine bit value.
     * If the value is out of range of this fine value then the current will
     * clip to minimum or maximum value, and the user must change the coarse
     * value.
     *
     * TODO develop strategy to set both coarse and fine value to achieve final
     * value
     *
     * @param current the current in amps
     */
    public float setFineCurrent(float current) {
        float im = getCoarseCurrent();
        float r = current / im;
        setFineBitValue(Math.round(r * (maxFineBitValue + 1)));
        return getCurrent();
    }

    /**
     * Computes and returns the actual final estimated value of current using
     * coarse and fine bit estimates. Overrides the method from base Biasgen
     * that used a single splitter rather than coarse/fine architecture.
     *
     * @return the actual final estimated value of current using coarse and fine
     * bit estimates
     */
    @Override
    public float getCurrent() {
        return computeCurrent(getCoarseBinaryValue(),getFineBitValue());
    }

    /** Computes estimated bias current from coarse and fine binary code values
     * 
     * @param courseBinaryValue the coarse code 0 to maxCoarseBitValue increasing with code
     * @param fineBinaryValue the fine code 0 to maxFineBitValue increasing with code
     * @return current in amps
     */
    private float computeCurrent(int courseBinaryValue, int fineBinaryValue) {
        float im = computeCoarseCurrent(courseBinaryValue);
        float i = (im * fineBinaryValue) / (maxFineBitValue + 1);
        return i;
    }

    /**
     * Sets a desired current by setting coarse and fine codes
     *
     * @param current - the desired current
     * @return the actual estimated current, which is closet possible value using smallest possible coarse current
     */
    @Override
    public float setCurrent(float current) {
        Point binaryCodes=computeCoarseFineValueFromCurrent(current);
        setBinaryCoarseAndFineValues(binaryCodes.x, binaryCodes.y);
        float actualCurrent=computeCurrent(binaryCodes.x, binaryCodes.y);
        return actualCurrent;
    }

    /**
     * Returns enabled via enum
     *
     * @return appropriate BiasEnabled enum
     */
    public BiasEnabled getBiasEnabled() {
        return biasEnabled;
    }

    /**
     * Sets bias enabled via enum
     *
     * @param biasEnabled
     */
    public void setBiasEnabled(BiasEnabled biasEnabled) {
        setEnabled(biasEnabled == BiasEnabled.Enabled);
        setModified(true);
    }

    /**
     * returns enabled via boolean
     *
     * @return boolean true if enabled
     */
    public boolean isEnabled() {
        return getBiasEnabled() == BiasEnabled.Enabled;
    }

    /**
     * sets enabled via boolean
     *
     * @param enabled
     */
    public void setEnabled(boolean enabled) {
        if (enabled != isEnabled()) {
            setChanged();
        }
        if (enabled) {
            biasEnabled = BiasEnabled.Enabled;
        } else {
            biasEnabled = BiasEnabled.Disabled;
        }
        notifyObservers();
    }

    public boolean isLowCurrentModeEnabled() {
        return currentLevel == CurrentLevel.Low;
    }

    /**
     * Sets the enum currentLevel according to the flag lowCurrentModeEnabled.
     *
     * @param lowCurrentModeEnabled true to set CurrentMode.LowCurrent
     */
    public void setLowCurrentModeEnabled(boolean lowCurrentModeEnabled) {
        if (lowCurrentModeEnabled != isLowCurrentModeEnabled()) {
            setChanged();
        }
        this.currentLevel = lowCurrentModeEnabled ? CurrentLevel.Low : CurrentLevel.Normal;
        notifyObservers();
    }

    /**
     * Computes the actual bit pattern to be sent to chip based on configuration
     * values
     */
    public int computeBinaryRepresentation() {
        int ret = 0;
        if (isEnabled()) {
            ret |= enabledMask;
        }
        if (getType() == Pot.Type.NORMAL) {
            ret |= typeMask;
        }
        if (getSex() == Pot.Sex.N) {
            ret |= sexMask;
        }
        if (!isLowCurrentModeEnabled()) {
            ret |= lowCurrentModeMask;
        }
        int sh;
        sh = Integer.numberOfTrailingZeros(bitFineMask);
        ret |= fineBitValue << sh;
        sh = Integer.numberOfTrailingZeros(bitCoarseMask);
        ret |= computeBinaryInverse(coarseBitValue, numCoarseBits) << sh; // The coarse bits are reversed (this was a mistake) so we need to mirror them here before we sent them.

        //System.out.println(toString() + " byte repres " + Integer.toHexString(ret));
        return ret;
    }

    /**
     * Computes the bit pattern to be sent to chip based on configuration
     * values, without changing the coarse value at all and assuming higher
     * number is higher bias.
     */
    public int computeCleanBinaryRepresentation() {
        int ret = 0;
        if (isEnabled()) {
            ret |= enabledMask;
        }
        if (getType() == Pot.Type.NORMAL) {
            ret |= typeMask;
        }
        if (getSex() == Pot.Sex.N) {
            ret |= sexMask;
        }
        if (!isLowCurrentModeEnabled()) {
            ret |= lowCurrentModeMask;
        }
        int sh;
        sh = Integer.numberOfTrailingZeros(bitFineMask);
        ret |= fineBitValue << sh;
        sh = Integer.numberOfTrailingZeros(bitCoarseMask);
        ret |= (getCoarseBinaryValue()) << sh; // from mistake in encoding value in bias generator so that high / low binary values are flipped (0 is largest coarse current)

        return ret;
    }

    public int computeInverseBinaryRepresentation() {
        int length = 16;
        int ret = computeBinaryRepresentation();
        int out = 0;
        for (int i = 0; i < length; i++) {
            out |= (((ret & (0x0001 << (length - 1 - i))) << i) >> (length - 1 - i));
        }
        return out;
    }

    /**
     * The coarse bits are reversed (this was a mistake) so we need to mirror
     * them here before we sent them.
     *
     * @param value the bits in
     * @param lenth the number of bits
     * @return the bits mirrored
     */
    protected int computeBinaryInverse(int value, int length) {
        int out = 0;
        for (int i = 0; i < length; i++) {
            out |= (((value & (0x0001 << (length - 1 - i))) << i) >> (length - 1 - i));
        }
        return out;
    }

    private byte[] bytes = null;

    /**
     * Computes the actual bit pattern to be sent to chip based on configuration
     * values
     */
    @Override
    public byte[] getBinaryRepresentation() {
        int n = 3;
        if (bytes == null) {
            bytes = new byte[n];
        }
        int val = computeBinaryRepresentation();
        int k = 1;
        for (int i = bytes.length - 2; i >= 0; i--) {
            bytes[k++] = (byte) (0xff & (val >>> (i * 8)));
        }
        bytes[0] = (byte) (0xff & address);
        return bytes;
    }

    public byte[] getCleanBinaryRepresentation() {
        int n = 3;
        if (bytes == null) {
            bytes = new byte[n];
        }
        int val = computeCleanBinaryRepresentation();
        int k = 1;
        for (int i = bytes.length - 2; i >= 0; i--) {
            bytes[k++] = (byte) (0xff & (val >>> (i * 8)));
        }
        bytes[0] = (byte) (0xff & address);
        return bytes;
    }

    /**
     * Returns the String key by which this pot is known in the Preferences. For
     * IPot's, this name is the Chip simple class name followed by
     * IPot.<potName>, e.g. "Tmpdiff128.IPot.Pr".
     *
     * @return preferences key
     */
    @Override
    protected String prefsKey() {
        return biasgen.getChip().getClass().getSimpleName() + ".AddressedIPotCF." + name;
    }

    static String KEY_BITVALUE_COARSE = "BitValueCoarse",
            KEY_BITVALUE_FINE = "BitValueFine",
            KEY_SEX = "Sex",
            KEY_TYPE = "Type",
            KEY_LOWCURRENT_ENABLED = "LowCurrent",
            KEY_ENABLED = "Enabled";
    static String SEP = ".";

    /**
     * stores as a preference the bit value
     */
    @Override
    public void storePreferences() {
        String s = prefsKey() + SEP;
        prefs.putInt(s + KEY_BITVALUE_COARSE, getCoarseBitValue()); // note this value is flipped for historical reasons
        prefs.putInt(s + KEY_BITVALUE_FINE, getFineBitValue());
        prefs.putBoolean(s + KEY_ENABLED, isEnabled());
        prefs.putBoolean(s + KEY_LOWCURRENT_ENABLED, isLowCurrentModeEnabled());
        prefs.put(s + KEY_SEX, getSex().toString());
        prefs.put(s + KEY_TYPE, getType().toString());
        setModified(false);
    }

    /**
     * loads and makes active the preference value. The name should be set
     * before this is called.
     */
    @Override
    public void loadPreferences() {
        String s = prefsKey() + SEP;
        int v = prefs.getInt(s + KEY_BITVALUE_COARSE, 0);
        setCoarseBitValue(v); // note this value is flipped for historical reasons, methods use getCoarseBinaryValue and setCoarseBinaryValue to set rational value with increasing current with binary code
        v = prefs.getInt(s + KEY_BITVALUE_FINE, 0);
        setFineBitValue(v);
        updateBitValue();
        setEnabled(prefs.getBoolean(s + KEY_ENABLED, true));
        setLowCurrentModeEnabled(prefs.getBoolean(s + KEY_LOWCURRENT_ENABLED, false));
        setSex(Pot.Sex.valueOf(prefs.get(s + KEY_SEX, Sex.N.toString())));
        setType(Pot.Type.valueOf(prefs.get(s + KEY_TYPE, Type.NORMAL.toString())));
        setModified(false);
    }

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
            if (key.equals(base + KEY_BITVALUE_FINE)) {
                if (getFineBitValue() != Integer.parseInt(val)) {
                    log.info(base + " fine bit value change from " + getFineBitValue() + " to " + Integer.parseInt(val) + " from preferences");
                }
                setFineBitValue(Integer.parseInt(val));
            } else if (key.equals(base + KEY_BITVALUE_COARSE)) {
                if (getCoarseBitValue() != Integer.parseInt(val)) {
                    log.info("coarse bit value change from preferences");
                }
                setCoarseBitValue(Integer.parseInt(val));
            } else if (key.equals(base + KEY_ENABLED)) {
                setEnabled(Boolean.parseBoolean(val));
            } else if (key.equals(base + KEY_LOWCURRENT_ENABLED)) {
                setLowCurrentModeEnabled(Boolean.parseBoolean(val));
            } else if (key.equals(base + KEY_SEX)) {
                setSex(Pot.Sex.valueOf(val));
            } else if (key.equals(base + KEY_TYPE)) {
                setType(Pot.Type.valueOf(val));
            }
        } catch (Exception ex) {
            log.warning("while responding to preference change event " + e + ", caught " + ex.toString());
        }
    }

    /**
     * Returns the flipped bit value 
     * returns the preference value of the bias current bit value.
     * @see #getPreferedCoarseBinaryValue() 
     */
    public int getPreferedCoarseBitValue() {
        String key = prefsKey() + SEP + KEY_BITVALUE_COARSE;
        int v = prefs.getInt(key, 0);
        return v;
    }
    
    /** Get preference value of coarse current binary value
     * 
     * @return preference value, with 0 as smallest current and maxCoarseBitValue as largest
     * @see #getPreferedCoarseBitValue() 
     */
    public int getPreferedCoarseBinaryValue(){
        return maxCoarseBitValue-getPreferedCoarseBitValue();  // due to flip in biasgen and historical reasons
    }

    /**
     * returns the preference value of the bias current fine bit value.
     */
    public int getPreferedFineBitValue() {
        String key = prefsKey() + SEP + KEY_BITVALUE_FINE;
        int v = prefs.getInt(key, 0);
        return v;
    }

    @Override
    public String toString() {
        return super.toString() + " Sex=" + getSex() + " Type=" + getType() + " enabled=" + isEnabled() + " lowCurrentModeEnabled=" + isLowCurrentModeEnabled() + " coarseBitValue=" + coarseBitValue + " fineBitValue=" + fineBitValue;
    }

    public CurrentLevel getCurrentLevel() {
        return currentLevel;
    }

    /**
     * Sets whether this is a normal type or low current bias which uses shifted
     * source
     */
    public void setCurrentLevel(CurrentLevel currentLevel) {
        if (currentLevel == null) {
            return;
        }
        if (currentLevel != this.currentLevel) {
            setChanged();
        }
        this.currentLevel = currentLevel;
        notifyObservers();
    }

    /**
     * Overrides super of type (NORNAL or CASCODE) to call observers
     */
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

    /**
     * Overrides super of setSex (N or P) to call observers
     */
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

    /**
     * Returns true if all parameters are identical, otherwise false.
     *
     * @param obj another ConfigurableIPotRev0
     * @return true if all parameters are identical, otherwise false.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AddressedIPotCF)) {
            return false;
        }
        AddressedIPotCF other = (AddressedIPotCF) obj;
        if (!getName().equals(other.getName())) {
            return false;
        }
        if (getCoarseBitValue() != other.getCoarseBitValue()) {
            return false;
        }
        if (getFineBitValue() != other.getFineBitValue()) {
            return false;
        }
        if (getSex() != other.getSex()) {
            return false;
        }
        if (getType() != other.getType()) {
            return false;
        }
        if (getCurrentLevel() != other.getCurrentLevel()) {
            return false;
        }
        if (isEnabled() != other.isEnabled()) {
            return false;
        }
        return true;
    }

}
