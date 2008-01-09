/*
 * IPot.java
 *
 * Created on September 20, 2005, 9:09 PM
 */

package ch.unizh.ini.caviar.biasgen;

import java.io.Serializable;
import java.util.*;
import java.util.prefs.*;
import javax.swing.JComponent;

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
 *This class extends </code>Observer<code> so observers can add themselves to be notified when the pot value changes.
 * @author tobi
 */
public class IPot extends Pot implements Cloneable, Observer, Serializable {
    
    /** The enclosing bias generator */
    protected Biasgen biasgen;

    /** the position of this ipot in the chain of shift register cells; zero based and starting at the end where the bits are loaded.
     The order is very important because the bits for the FIRST bias in the shift register are loaded LAST.
     */
    protected int shiftRegisterNumber=0;
    
   /** Creates a new instance of IPot passing only the biasgen it belongs to. All other parameters take default values.
    *<p>
    *This IPot also adds itself as an observer for the Masterbias object.
     @param biasgen the biasgen this ipot is part of
     */
    protected IPot(Biasgen biasgen) {
        super(biasgen.getChip());        
        this.biasgen=biasgen;
        masterbias=biasgen.getMasterbias();
        masterbias.addObserver(this);
    }
    
    /** Creates a new instance of IPot
     *@param biasgen
     *@param name
     *@param shiftRegisterNumber the position in the shift register, 0 based, starting on end from which bits are loaded
     *@param type (NORMAL, CASCODE)
     *@param sex Sex (N, P)
     * @param bitValue initial bitValue
     *@param displayPosition position in GUI from top (logical order)
     *@param tooltipString a String to display to user of GUI telling them what the pots does
     */
    public IPot(Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
        this(biasgen);
        setName(name);
        this.setType(type);
        this.setSex(sex);
        this.bitValue=bitValue;
        this.displayPosition=displayPosition;
        this.tooltipString=tooltipString;
        this.shiftRegisterNumber=shiftRegisterNumber;
        loadPreferences(); // do this after name is set
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
    
    public int getShiftRegisterNumber() {
        return this.shiftRegisterNumber;
    }
    
    public void setShiftRegisterNumber(final int shiftRegisterNumber) {
        this.shiftRegisterNumber = shiftRegisterNumber;
    }
    
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
    
    /** change ipot current value by ratio
     @param ratio between new current and old value, e.g. 1.1f or 0.9f
     */
    public void changeByRatio(float ratio){
        int v=Math.round(getBitValue()*ratio);
        v=v+(ratio>=1?1:-1);
        setBitValue(v);
    }
    
    public void update(Observable observable, Object obj) {
        if(observable instanceof Masterbias){
            setChanged();
            notifyObservers();
        }
    }

    /** Builds the component used to control the IPot. This component is the user interface.
     @return a JComponent that can be added to a GUI
     @param frame the BiasgenFrame in which it sits
     */
    public JComponent makeGUIPotControl(BiasgenFrame frame) {
         return new IPotGUIControl(this,frame);
    }

    public float getPhysicalValue() {
        return getCurrent();
    }

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
    

    
}

