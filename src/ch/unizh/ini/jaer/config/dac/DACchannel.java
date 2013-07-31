/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.dac;

import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import javax.swing.JComponent;

import net.sf.jaer.chip.Chip;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

/**
 *
 * @author Minhao
 */
public class DACchannel extends Observable implements PreferenceChangeListener, RemoteControlled{

    protected static Logger log = Logger.getLogger("DACchannel");
    /** The Chip for this Pot */
    protected Chip chip;

//    private static boolean modificationPreferenceTrackingEnabled=false; // this flag can be set to track modifications globally
    /**
     * Flags that value or some other property has been modified from its Preference value.
     * @return true if modified.
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Sets the flag that signals a modifictaion away from the preferred value or property.
     * @param modified true to signal that this has been modified.
     */
    public void setModified(boolean modified) {
        this.modified = modified;
    }
    /** Preferences fot this Pot. prefs is assigned in the constructor to the Preferences node for the Chip for this Pot. */
    protected Preferences prefs;
    /** A tooltip to show users */
    protected String tooltipString = null;
    /** the name of the ipot bias */
    protected String name = "DACchannel (unnamed)";
    /** the current value of the ipot in bits loaded into the shift register */
    protected int bitValue = 0;
    /** the display position within a group */
    protected int displayPosition = 0;
    /** the number of bits of resolution for this bias. This number is used to compute the max bit value and also for
    computing the number of bits or bytes to send to a device
     */
    protected int numBits;
    /** flag for modified (from preference value). */
    private boolean modified = false;

    /** Constructs a new instance of Pot which adds itself as a preference change listener.
     *This Pot adds itself as a PreferenceChangeListener to the
     * Preference object for this preference node (the Biasgen package node),
     * so that preference changes call the method preferenceChange.
     *<p>
    @param chip the chip for this Pot
     */
    public DACchannel(Chip chip, String name, DAC dac, int channel, int bitValue, int displayPosition, String tooltipString) {
        this.chip = chip;
        prefs = chip.getPrefs();
        prefs.addPreferenceChangeListener(this);
        setDac(dac);
        setNumBits(dac.getResolutionBits());
        setChannel(channel);
        this.bitValue = bitValue;
        this.displayPosition = displayPosition;
        this.tooltipString = tooltipString;
        setName(name);
        loadPreferences();
       if(chip.getRemoteControl()!=null){
            chip.getRemoteControl().addCommandListener(this, String.format("setv%s bitvalue",getName()), "Set the bitValue of VPot "+getName());
       }
    }

    /** called when there is a preference change for this Preferences node (the Biasgen package node).
     *If the key of the PreferenceChangeEvent matches our own preference key, then the bit value is set
     *@param e the PreferenceChangeEvent, issued when new Preferences are loaded
     */
    @Override
    public void preferenceChange(PreferenceChangeEvent e) {
        if (e.getKey().equals(prefsKey())) {
//            log.info(this+" Pot.preferenceChange(): event="+e+" key="+e.getKey()+" newValue="+e.getNewValue());
            int v = Integer.parseInt(e.getNewValue());
            setBitValue(v);
//            try{Thread.currentThread().sleep(500);}catch(InterruptedException se){}
        }
    }

    /** returns the number of shift register/current splitter/DAC bits for the bias value. The total number of bits may be larger if
     *the bias has other configuration bits, e.g. for buffer bias, type of bias, etc.
    @return the number of bits
     *@see ch.unizh.ini.jaer.chip.dvs320.ConfigurableIPotRev0
     */
    public int getNumBits() {
        return numBits;
    }

    /** Sets the number of bits representing this bias.
    @param n the number of bits
     */
    public void setNumBits(int n) {
        this.numBits = n;
    }

    public String getName() {
        return this.name;
    }

    /** sets the pot name.
    @param name the string name that is used for the preferences key
     */
    public void setName(final String name) {
        this.name = name;
    }

    /** bytes for this bias. This is numBits/8. */
    public int getNumBytes() {
        return numBits >>> 3;
    }

    /** sets the number of bytes that represent this bias
    @param numBytes number of bytes
     */
    public void setNumBytes(final int numBytes) {
        this.numBits = numBytes << 3;
    }

    /** Returns the value of the bias current or voltage in binary form, i.e. this value will scale the actual value by the reference value.
     *For an on-chip bias current generator the minimum current is about 0, and the maximum is the master current. For an
     *off-chip voltage DAC, the limits are set the by the DAC references.
     *@return the binary value of the bias
     *@see #getBinaryRepresentation
     **/
    public int getBitValue() {
        return this.bitValue;
    }

    /** set the bit value. Observers are notified if value changes. Value is clipped to hard limits.
     *Every change to bias value must flow through this method.
     *@param value the new bitValue
     */
    public void setBitValue(final int value) {
        //// every change to value flows through here!!!!
        int clippedValue = clip(value);
        if (bitValue != clippedValue) {
            setChanged();
            bitValue = clippedValue;
        }
        notifyObservers(this);
    }

    /** Overrides Observable.setChanged() to setModified(true).
     *
     */
    @Override
    public synchronized void setChanged() {
        setModified(true);
        super.setChanged();
    }

    public Chip getChip(){
        return chip;
    }

    /** return the max value representing all stages of current splitter enabled */
    public int getMaxBitValue() {
        return (int) ((1L << (getNumBits())) - 1);
    }

    /** no current: zero */
    public int getMinBitValue() {
        return 0;
    }

    // returns clipped value of potential new value
    int clip(int o) {
        int n = o; // new value
        if (o < getMinBitValue()) {
            n = getMinBitValue();
        }
        if (o > getMaxBitValue()) {
            n = getMaxBitValue();
        }
        return n;
    }
    /** fraction that pot current changes for increment or decrement */
    public final float CHANGE_FRACTION = 0.1f;

    /** increment pot value by one count */
    public void incrementBitValue() {
        setBitValue(bitValue + 1);
    }

    /** decrement pot value by one count */
    public void decrementBitValue() {
        setBitValue(bitValue - 1);
    }

    @Override
	public String toString() {
        return "Pot " + getName() + " with bitValue=" + getBitValue();
    }

    /** @return string of bits for this ipot shift register */
    public String toBitPatternString() {
        //        int k=getNumBits();
        String s = "";
        for (int k = getNumBits() - 1; k >= 0; k--) {
            int j = 1 << (k);
            if ((bitValue & j) != 0) {
                s = s + "1";
            } else {
                s = s + "0";
            }
        }
        return s;
    }

    /** stores as a preference the bit value, and calls setModified(false).
     */
    public void storePreferences() {
        prefs.putInt(prefsKey(), getBitValue());
        setModified(false);
    }

    /** loads and makes active the preference value. The name should be set before this is called.
    Also calls setModified(false).
     */
    public void loadPreferences() {
        //        System.out.println("loading value for "+name);
        setBitValue(getPreferedBitValue());
//        if(!iseModificationTrackingEnabled())
        setModified(false);
    }

    /** Returns the preference value bit value using prefsKey as the key.
     * @return the preferred bit value.
     */
    public int getPreferedBitValue() {
        String key = prefsKey();
        int v = prefs.getInt(key, bitValue);
        return v;
    }
    int suspendValue = 0;

    /** set suspend value (zero current), saving old value for resume
     *@see #resume
     */
    public void suspend() {
        suspendValue = getBitValue();
        setBitValue(0);
    }

    /** restore suspend value (0 if not suspended before
     *@see #suspend
     */
    public void resume() {
        setBitValue(suspendValue);
    }

    public int getDisplayPosition() {
        return this.displayPosition;
    }

    public void setDisplayPosition(final int displayPosition) {
        this.displayPosition = displayPosition;
    }

    public String getTooltipString() {
        return this.tooltipString;
    }

    public void setTooltipString(final String tooltipString) {
        this.tooltipString = tooltipString;
    }

    @Override
    public synchronized void addObserver(Observer o) {
//        log.info(this+ " added observer "+o);
        super.addObserver(o);
    }

    /** Checks name, sex, type, chipNumber and bitValue for equality.
     *
     * @param obj another Pot - if not a Pot or null, returns false.
     * @return true if equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof DACchannel)) {
            return false;
        }
        DACchannel pot = (DACchannel) obj;
        return pot.getName().equals(getName())
                && (pot.getBitValue() == getBitValue());
    }

    /** the delta voltage to change by in increment and decrement methods */
    public static final float VOLTAGE_CHANGE_VALUE_VOLTS = 0.005f;
//    public static final float VOLTAGE_CHANGE_VALUE_VOLTS = 0.01f;
    private int dacNumber = 0;
    private int channel = 0;
    private DAC dac = null;
    /** sets the bit value based on desired voltage, clipped the DAC's vdd.
     * Observers are notified if value changes.
     *@param voltage in volts
     *@return actual float value of voltage after resolution rounding and vdd clipping.
     */
    public float setVoltage(float voltage) {
        if(voltage>dac.getVdd()) {
			voltage=dac.getVdd();
		}
        setBitValue(Math.round(voltage * getMaxBitValue()));
        return getVoltage();
    }

    public byte[] getByteRepresentation() {
        return null;
    }

    /** gets the voltage output by this VPot according the bit value times the difference between ref min and ref max, clipped
     * to the DAC's vdd.
     * @return voltage in volts.
     */
    public float getVoltage() {
        float v=getMinVoltage() + (getVoltageResolution() * getBitValue());
        if(v>dac.getVdd()) {
			v=dac.getVdd();
		}
        return v;
    }

    /** @return max possible voltage */
    public float getMaxVoltage() {
        return getDac().getRefMaxVolts();
    }

    /** @return min possible voltage. */
    public float getMinVoltage() {
        return getDac().getRefMinVolts();
    }

    /** return resolution of pot in voltage.
     *@return smallest possible voltage change -- in principle.
     */
    public float getVoltageResolution() {
        return (getDac().getRefMaxVolts() - getDac().getRefMinVolts()) / ((1 << getNumBits()) - 1);
    }

    /** increment pot value  */
    public void incrementVoltage() {
        setVoltage(getVoltage() + VOLTAGE_CHANGE_VALUE_VOLTS);
    }

    /** decrement pot value  */
    public void decrementVoltage() {
        setVoltage(getVoltage() - VOLTAGE_CHANGE_VALUE_VOLTS);
    }

    public int getDacNumber() {
        return dacNumber;
    }

    public void setDacNumber(int dacNumber) {
        this.dacNumber = dacNumber;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        if (channel > (dac.getNumChannels() - 1)) {
            throw new RuntimeException("VPot channel " + channel + " higher than number of channels in DAC (" + dac.getNumChannels() + ")");
        }
        this.channel = channel;
    }

    public DAC getDac() {
        return dac;
    }

    public void setDac(DAC dac) {
        this.dac = dac;
    }

    public float getPhysicalValue() {
        return getVoltage();
    }

    public String getPhysicalValueUnits() {
        return "V";
    }

    public void setPhysicalValue(float value) {
        setVoltage(value);
    }

    public JComponent makeGUIPotControl() {
        return new DACchannelControl(this);
    }

    /** changes VPot value by a fraction of full scale, e.g. -0.05f for a -5% decrease of full-scale value
     * @param fraction of full scale value
     */
    public void changeByFractionOfFullScale(float fraction) {
        int change = (int) (getMaxBitValue() * fraction);
        setBitValue(getBitValue() + change);
    }
    private byte[] bytes = null;

    /** Computes and returns a the reused array of bytes representing the bias to be sent over hardware interface to the device
    @return array of bytes to be sent, by convention values are ordered in big endian format so that byte 0 is the most significant byte and is sent first to the hardware
     */
    public byte[] getBinaryRepresentation() {
        int n = getNumBytes();
        if (bytes == null) {
            bytes = new byte[n];
        }
        int val = getBitValue();
        int k = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            bytes[k++] = (byte) (0xff & (val >>> (i * 8)));
        }
        return bytes;
    }

    /** Returns the String key by which this pot is known in the Preferences. For VPot's, this
    name is the Chip simple class name followed by VPot.<potName>, e.g. "Tmpdiff128.VPot.VRefAmp".
    @return preferences key
     */
    protected String prefsKey() {
        return chip.getClass().getSimpleName() + ".VPot." + name;
    }

    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        String[] t = input.split("\\s");
        if (t.length < 2) {
            return "? " + this + "\n";
        } else {
            try {
                int bv = Integer.parseInt(t[1]);
                setBitValue(bv);
                return this + "\n";
            } catch (NumberFormatException e) {
                log.warning(input + " caused " + e);
                return e.toString() + "\n";
            }
        }
    }
}
