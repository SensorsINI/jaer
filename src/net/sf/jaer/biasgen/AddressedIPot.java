/*
 * IPot.java
 *
 * Created on September 20, 2005, 9:09 PM
 */

package net.sf.jaer.biasgen;

import java.util.Observable;
import java.util.Observer;

import javax.swing.JComponent;

import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

/**
 * Describes an IPot, Bernabe Linares Barranco's name for a programamble current source.
 These are different in their detailed circuit implementation, being based
 * on a Bult&Geelen current splitter whose input current is directly split, insread of being mirrored and then split.
 I.e., the ones implemented here use a voltage reference for the current splitter that is externally derived instead of
 coming from the current mirror gate voltage.
 <p>
 In any case, these are integrated on-chip, e.g.
 *on retina test chip testchipARCS and temporal contrast dynamic vision sensor Tmpdiff128,
 and are programmed over an SPI (serial peripherl interconnect) link from
 *an off-chip microcontroller. This class holds the state of the IPot and describes it.
 *<p>
 * Addressed IPots are different from conventional IPot since they are not configured as part of a long shift register chain 
 * but buy addressing them and loading their value.
 *This class extends </code>Observer<code> so observers can add themselves to be notified when the pot value changes.
 * @author tobi, chbraen (addressign)
 */
public class AddressedIPot extends Pot implements Cloneable, Observer, RemoteControlled {
    
    /** The enclosing bias generator */
    protected Biasgen biasgen;
    
    /** The address of ipot 
     */
    protected int address=0;
    
    /** the number of bits of resolution for this bias. This number is used to compute the max bit value and also for
    computing the number of bits or bytes to send to a device (only bits for )
     */
    protected int valueBits = 24;
    
    /** Creates a new instance of IPot passing only the biasgen it belongs to. All other parameters take default values.
     *<p>
     *This IPot also adds itself as an observer for the Masterbias object.
     @param biasgen the biasgen this ipot is part of
     */
    protected AddressedIPot(Biasgen biasgen) {
        super(biasgen.getChip());
        this.biasgen=biasgen;
        masterbias=biasgen.getMasterbias();
        masterbias.addObserver(this);
        setNumBits(valueBits);
    }
    
    /** Creates a new instance of IPot
     *@param biasgen the containing Biasgen.
     *@param name displayed and used to return by name.
     *@param address the position in the shift register,      
     * 0 based, starting on end from which bits are loaded. 
     * This order determines how the bits are sent to the shift register, 
     * lower address are loaded later, so that they end up at the start of the shift register.
     * The last bit on the shift register is loaded first and is the msb of the last bias 
     * on the shift register.
     * The last bit loaded into the shift register is the lsb of the first bias on the shift register.
     *@param type (NORMAL, CASCODE) - for user information.
     *@param sex Sex (N, P). User tip.
     * @param bitValue initial bitValue.
     *@param displayPosition position in GUI from top (logical order).
     *@param tooltipString a String to display to user of GUI telling them what the pots does.
     */
    public AddressedIPot(Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
        this(biasgen);
        setName(name);
        this.setType(type);
        this.setSex(sex);
        this.bitValue=bitValue;
        this.displayPosition=displayPosition;
        this.tooltipString=tooltipString;
        this.address=shiftRegisterNumber;
        loadPreferences(); // do this after name is set
       if(chip.getRemoteControl()!=null){
            chip.getRemoteControl().addCommandListener(this, String.format("seti_%s bitvalue",getName()), "Set the bitValue of IPot "+getName());
        }
    }
    
    public String toString(){
        return "IPot "+getName()+" with bitValue="+getBitValue()+" current="+getCurrent();
    }
    
    
    /** sets the bit value based on desired current and {@link #masterbias} current.
     * Observers are notified if value changes.
     *@param current in amps
     *@return actual float value of current after resolution clipping.
     */
    public float setCurrent(float current){
        float im=masterbias.getCurrent();
        float r=current/im;
        setBitValue(Math.round(r*getMaxBitValue()));
        return getCurrent();
    }
    
    /** Computes the estimated current based on the bit value for the current splitter and the {@link #masterbias}
     * @return current in amps */
    public float getCurrent(){
        float im=masterbias.getCurrent();
        float i=im*getBitValue()/getMaxBitValue();
        return i;
    }
    
    
    public void setChipNumber(final int chipNumber) {
        this.chipNumber = chipNumber;
    }
    
    /**
     *  The shift register number is ordered so that the lowest address is the bias at the start of the shift register and must be loaded *last*.
     * The highest number should go to the end of the shift register. This bias needs to be loaded first.
     * 
     * @return  The shift register number which is ordered so that the lowest address is the bias at the start of the shift register.
     */
    public int getAddress() {
        return this.address;
    }
    
    /**
     * The shift register number is ordered so that the lowest address is the bias at the start of the shift register and must be loaded *last*.
     * The highest number should go to the end of the shift register. This bias needs to be loaded first.
     * @param address which lower towards the input side and starts with 0 by convention.
     */
    public void setAddress(final int shiftRegisterNumber) {
        this.address = shiftRegisterNumber;
    }
    
//    public void setAddress(int address){
//        this.address = address;
//    }
    
    /** @return max possible current (master current) */
    public float getMaxCurrent(){
        return masterbias.getCurrent();
    }
    
    /** @return min possible current (presently zero, although in reality limited by off-current and substrate leakage). */
    public float getMinCurrent(){
        return 0f;
    }
    
    /** return resolution of pot in current. This is just Im/2^nbits.
     *@return smallest possible current change -- in principle
     */
    public float getCurrentResolution(){
        return 1f/((1<<getNumBits())-1);
    }
    
    /** increment pot value by {@link #CHANGE_FRACTION} ratio */
    public void incrementCurrent(){
        int v=Math.round(1+((1+CHANGE_FRACTION)*bitValue));
        setBitValue(v);
    }
    
    /** decrement pot value by  ratio */
    public void decrementCurrent(){
        int v=Math.round(-1+((1-CHANGE_FRACTION)*bitValue));
        setBitValue(v);
    }
    
    /** Change current value by ratio, or at least by one bit value.
     @param ratio between new current and old value, e.g. 1.1f or 0.9f
     */
    public void changeByRatio(float ratio){
        int oldv=getBitValue();
        int v=Math.round(getBitValue()*ratio);
        if(v==oldv){
            v = v + (ratio >= 1 ? 1 : -1);
        }
        setBitValue(v);
    }

    /** Change by ratio from preferred value; can be used for a tweak from a nominal value.
     @param ratio between new current and old value, e.g. 1.1f or 0.9f
     */
    public void changeByRatioFromPreferred(float ratio){
        int v=Math.round(getPreferedBitValue()*ratio);
        v=v+(ratio>=1?1:-1);
        log.info("changing bit value from "+getBitValue()+" to "+v);
        setBitValue(v);
    }
    
    /** Responds to updates from Masterbias to notify observers 
     * 
     * @param observable ignored
     * @param obj ignored
     */
    public void update(Observable observable, Object obj) {
        if(observable instanceof Masterbias){
            if(obj!=Masterbias.EVENT_POWERDOWN){ // only inform listening pots to update their GUI when needed
                setChanged();
                notifyObservers();
            }
        }
    }
    
    /** Builds the component used to control the IPot. This component is the user interface.
     @return a JComponent that can be added to a GUI
     */
    @Override
    public JComponent makeGUIPotControl() {
        return new AddressedIPotGUIControl(this);
    }
    
    @Override
    public float getPhysicalValue() {
        return getCurrent();
    }
    
    @Override
    public String getPhysicalValueUnits() {
        return "A";
    }
    
    public void setPhysicalValue(float value) {
        setCurrent(value);
    }
    
    
    /** Returns the String key by which this pot is known in the Preferences. For IPot's, this
     name is the Chip simple class name followed by IPot.<potName>, e.g. "Tmpdiff128.IPot.Pr".
     @return preferences key
     */
    @Override
    protected String prefsKey(){
        return biasgen.getChip().getClass().getSimpleName()+".IPot."+name;
    }
    
    private byte[] bytes=null;
    
    /** Computes and returns a the reused array of bytes representing 
     * the bias to be sent over hardware interface to the device.
     * The first byte contains the address of the value
     @return array of bytes to be sent, by convention values are ordered in
     * big endian format so that byte 0 is the most significant
     * byte and is sent first to the hardware.
     */
    @Override
    public byte[] getBinaryRepresentation() {
        int n=getNumBytes();
        if(bytes==null) bytes=new byte[n];
        int val=getBitValue();
        int k=0;
        for(int i=bytes.length-1;i>=0;i--){
            bytes[k++]=(byte)(0xff&(val>>>(i*8)));
        }
        bytes[0]=(byte)(0xff&address);
        return bytes;
   }

    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        String[] t=input.split("\\s");
        if(t.length<2){
            return "? "+this+"\n";
        }else{
            try{
                int bv=Integer.parseInt(t[1]);
                setBitValue(bv);
                return this+"\n";
            }catch(NumberFormatException e){
                log.warning(input+" caused "+e);
                return e.toString()+"\n";
            }
        }
    }
    
}
