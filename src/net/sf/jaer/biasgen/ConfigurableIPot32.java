package net.sf.jaer.biasgen;

/*
 * Part of jAER.
 */
import net.sf.jaer.biasgen.Biasgen;

/**
 * Second generation IPot with full configurability and 32 bits of configuration, sharing a pair of global shifted voltage sources near the ground and power rails.
 * Compared with ConfigurableIPotRev0, the bit format has changed slightly and the buffer bias is no longer used to set each pot's shifted source
 * value and buffer current, since these are globally shared by separate shifted n and p voltage regulators.
 * <p>
 * The sex (N/P), type (NORMAL/CASCODE), current level (LOW,NORNAL), enabled state (normal,
 * or weakly tied to rail), buffer bias current, and bias current can
 * all be digitally configured. First implemented on TCVS320, improved on DVS320, improved again on cDVSTest chips.
 *
 * @author tobi
 */
public class ConfigurableIPot32 extends ConfigurableIPotRev0 {

    public ConfigurableIPot32(Biasgen biasgen) {
        super(biasgen);
        /** Bit mask for flag bias enabled (normal operation) or disabled (tied weakly to rail) */
        enabledMask = 0x00000001; // TODO change for pots on cdvstest

        /** Bit mask for flag low current mode enabled */
        lowCurrentModeMask = 0x00000008;

        /** Bit mask for flag for bias sex (N or P) */
        sexMask = 0x00000002;

        /** Bit mask for flag for bias type (normal or cascode) */
        typeMask = 0x00000004;

        /** Bit mask for bias current value bits */
        bitValueMask = 0xfffffc00; // 22 bits at msb position

        /** Bit mask for buffer bias bits */
        bufferBiasMask = 0x000003f0; // 6 bits just to right of bias value bits

        /** Number of bits used for bias value */
        numBiasBits = Integer.bitCount(bitValueMask);

        /** The number of bits specifying buffer bias current as fraction of master bias current */
        numBufferBiasBits = Integer.bitCount(bufferBiasMask);

        /** The bit value of the buffer bias current */
        bufferBitValue = (1 << numBufferBiasBits) - 1;

        /** Maximum buffer bias value (all bits on) */
        maxBuffeBitValue = (1 << numBufferBiasBits) - 1;

        /** Max bias bit value */
        maxBitValue = (1 << numBiasBits) - 1;
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
    public ConfigurableIPot32(Biasgen biasgen, String name, int shiftRegisterNumber,
            Type type, Sex sex, boolean lowCurrentModeEnabled, boolean enabled,
            int bitValue, int bufferBitValue, int displayPosition, String tooltipString) {
        this(biasgen);
        numBytes=4;
        numBits=32; // modify these fields from default, accourding to DVSTEst pots
//        numBits = numBiasBits; // should be 32 bits overrides IPot value of 24
        setName(name);
        this.setType(type);
        this.setSex(sex);
        this.bitValue = bitValue;
        this.bufferBitValue = bufferBitValue;
        this.currentLevel = lowCurrentModeEnabled ? CurrentLevel.Low : CurrentLevel.Normal;
        this.biasEnabled = enabled ? BiasEnabled.Enabled : BiasEnabled.Disabled;
        this.displayPosition = displayPosition;
        this.tooltipString = tooltipString;
        this.shiftRegisterNumber = shiftRegisterNumber;
        loadPreferences(); // do this after name is set
        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().addCommandListener(this, String.format(SETI + "%s <bitvalue>", getName()), "Set the bitValue of IPot " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETIBUF + "%s <bitvalue>", getName()), "Set the bufferBitValue of IPot " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETSEX + "%s " + getEnumOptions(Sex.class), getName()), "Set the sex (N|P) of " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETTYPE + "%s " + getEnumOptions(Type.class), getName()), "Set the type of IPot " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETLEVEL + "%s " + getEnumOptions(CurrentLevel.class), getName()), "Set the current level of IPot " + getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETENABLED + "%s " + getEnumOptions(BiasEnabled.class), getName()), "Set the current level of IPot " + getName());
        }
//        System.out.println(this);
    }
}
