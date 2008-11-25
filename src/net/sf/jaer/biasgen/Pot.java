package net.sf.jaer.biasgen;

import net.sf.jaer.chip.*;
import java.util.*;
import java.util.Observable;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.*;
import net.sf.jaer.util.RemoteControlled;


/**
 * Describes an general bias (pot=potentiometer),  This Pot can either be on or off chip. On chip pots are current DACs from Tobi's bias
 generator design kit, programmed over an SPI interface. Off-chip pots are voltage DACs programmed over SPI.
 *<p>
 *This class extends </code>Observer<code> so observers can add themselves to be notified when the pot value changes.
 * @author tobi
 */
abstract public class Pot extends Observable implements PreferenceChangeListener {
    protected static Logger log=Logger.getLogger("Pot");
    
    /** The Chip for this Pot */
    protected Chip chip;
    
    /** an enum for the type of bias, NORMAL or CASCODE or REFERENCE */
    public static enum  Type {NORMAL, CASCODE, REFERENCE};
    
    /** the transistor type for bias, N or P or not available (na) */
    public static enum Sex {N, P, na};
    
    /** Preferences fot this Pot. prefs is assigned in the constructor to the Preferences node for the Chip for this Pot. */
    protected Preferences prefs;
    
    /** type of bias, e.g. normal single FET, or cascode */
    protected Type type=null;
    
    /** n or p type */
    protected  Sex sex=null;
    
    protected int pinNumber=0;
    
    /** A tooltip to show users */
    protected String tooltipString=null;
    
    /** the name of the ipot bias */
    protected String name="IPot (unnamed)";
    
    /** the current value of the ipot in bits loaded into the shift register */
    protected int bitValue=0;
    /** the chip that this ipot sits on in the chain of chips; zero based */
    protected int chipNumber=0;
    
    
    /** the 'group' of this IPot, used for grouping related pots */
    protected String group=null;
    
    /** the display position within a group */
    protected int displayPosition=0;
    
    /** the Masterbias supplying reference current to this IPot */
    transient public Masterbias masterbias=null;
    
    /** the number for bytes of 8 bits for this ipot */
    protected int numBytes=3;
    
    /** the number of bits of resolution for this bias. This number is used to compute the max bit value and also for
     computing the number of bits or bytes to send to a device
     */
    protected int numBits=24;
    
    /** Constructs a new instance of Pot which adds itself as a preference change listener.
     *This Pot adds itself as a PreferenceChangeListener to the
     * Preference object for this preference node (the Biasgen package node),
     * so that preference changes call the method preferenceChange.
     *<p>
     @param chip the chip for this Pot
     */
    public Pot(Chip chip){
        this.chip=chip;
        prefs=chip.getPrefs();
        prefs.addPreferenceChangeListener(this);
 
    }
    
    /** called when there is a preference change for this Preferences node (the Biasgen package node).
     *If the key of the PreferenceChangeEvent matches our own preference key, then the bit value is set
     *@param e the PreferenceChangeEvent, issued when new Preferences are loaded
     */
    public void preferenceChange(PreferenceChangeEvent e) {
        if(e.getKey().equals(prefsKey())){
//            log.info(this+" Pot.preferenceChange(): event="+e+" key="+e.getKey()+" newValue="+e.getNewValue());
            int v=Integer.parseInt(e.getNewValue());
            setBitValue(v);
//            try{Thread.currentThread().sleep(500);}catch(InterruptedException se){}
        }
    }
    
    /** returns the number of shift register/current splitter/DAC bits for the bias value. The total number of bits may be larger if
     *the bias has other configuration bits, e.g. for buffer bias, type of bias, etc.
     @return the number of bits
     *@see ch.unizh.ini.jaer.chip.dvs320.ConfigurableIPot
     */
    public int getNumBits(){
        return numBits;
    }
    
    /** Sets the number of bits representing this bias.
     @param n the number of bits
     */
    public void setNumBits(int n){
        this.numBits=n;
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
        return numBits>>>3;
    }
    
    /** sets the number of bytes that represent this bias
     @param numBytes number of bytes
     */
    public void setNumBytes(final int numBytes) {
        this.numBits=numBytes<<3;
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
        int oldBitValue=bitValue;
        bitValue=value;
        bitValue=clip(bitValue);
        if(bitValue!=oldBitValue) {
//            log.info("changed "+this+"from oldValue="+oldBitValue+" to bitValue="+bitValue);
//            Thread.dumpStack();
            setChanged();
            notifyObservers(this);
        }
    }
    
    
    public int getChipNumber() {
        return this.chipNumber;
    }
    
    /** return the max value representing all stages of current splitter enabled */
    public int getMaxBitValue(){
        return (int)((1L<<(getNumBits()))-1);
    }
    
    /** no current: zero */
    public int getMinBitValue(){
        return 0;
    }
    
    // returns clipped value of potential new value
    int clip(int o){
        int n=o; // new value
        if(o<getMinBitValue()) n=getMinBitValue();
        if(o>getMaxBitValue()) n=getMaxBitValue();
        return n;
    }
    
    /** fraction that pot current changes for increment or decrement */
    public final float CHANGE_FRACTION=0.1f;
    
    /** increment pot value by one count */
    public void incrementBitValue(){
        setBitValue(bitValue+1);
    }
    
    /** decrement pot value by one count */
    public void decrementBitValue(){
        setBitValue(bitValue-1);
    }
    
    public String toString(){
        return "Pot "+getName()+" with bitValue="+getBitValue();
    }
    
    /** @return string of bits for this ipot shift register */
    public String toBitPatternString(){
        //        int k=getNumBits();
        String s="";
        for(int k=getNumBits()-1;k>=0;k--){
            int j=1<<(k);
            if((bitValue&j)!=0){
                s=s+"1";
            }else{
                s=s+"0";
            }
        }
        return s;
    }
    
    /** stores as a preference the bit value */
    public void storePreferences(){
        prefs.putInt(prefsKey(),getBitValue());
    }
    
    /** loads and makes active the preference value. The name should be set before this is called. */
    public void loadPreferences(){
        //        System.out.println("loading value for "+name);
        setBitValue(getPreferedBitValue());
    }
    
    /** returns the preference value bit value using prefsKey as the key
     */
    public int getPreferedBitValue(){
        String key=prefsKey();
        int v=prefs.getInt(key,0);
        return v;
    }
    
    /** Returns the String key by which this pot is known in the Preferences: Pot.<name>.
     Subclasses, e.g. IPot, sometimes override prefsKey to associate the preferences key with a specific chip.
     @return preferences key string
     */
    protected String prefsKey(){
        return "Pot."+name;
    }
    
    
    int suspendValue=0;
    /** set suspend value (zero current), saving old value for resume
     *@see #resume
     */
    public void suspend(){
        suspendValue=getBitValue();
        setBitValue(0);
    }
    
    /** restore suspend value (0 if not suspended before
     *@see #suspend
     */
    public void resume(){
        setBitValue(suspendValue);
    }
    
    public String getGroup() {
        return this.group;
    }
    
    public void setGroup(final String group) {
        this.group = group;
    }
    
    public int getPinNumber() {
        return this.pinNumber;
    }
    
    public void setPinNumber(final int pinNumber) {
        this.pinNumber = pinNumber;
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
    
    /** Contructs the UI control for this Pot. 
     @return the UI component that user uses to control the Pot
     */
    abstract public JComponent makeGUIPotControl();
    
    public Type getType() {
        return type;
    }
    
    public void setType(Type type) {
        this.type = type;
    }
    
    public Sex getSex() {
        return sex;
    }
    
    public void setSex(Sex sex) {
        this.sex = sex;
    }
    
    /** returns physical value of bias, e.g. in current amps or voltage volts
     @return physical value
     */
    abstract public float getPhysicalValue();
    
    /** sets physical value of bias
     @param value the physical value, e.g. in amps or volts
     */
    abstract public void setPhysicalValue(float value);
    
    /** return units (e.g. A, mV) of physical value of bias */
    abstract public String getPhysicalValueUnits();
    
    
    @Override public void addObserver(Observer o){
//        log.info(this+ " added observer "+o);
        super.addObserver(o);
    }
    
    /** Computes and returns a new array of bytes representing the bias to be sent over hardware interface to the device.
     @return array of bytes to be sent, by convention values are ordered in big endian format so that byte 0 is the most significant byte and is sent first to the hardware
     */
    abstract public byte[] getBinaryRepresentation();
    
}
