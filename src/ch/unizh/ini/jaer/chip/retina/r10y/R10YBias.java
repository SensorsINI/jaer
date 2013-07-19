/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.retina.r10y;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.IPot;

/**
 * Bias on R10Y chip. Each bias has only 3 bits of control. They share a 4 bit master current which they individually scale by 3 bits.
 * 
 * @author tobi
 */
public class R10YBias extends IPot{

    private R10YIRefTuneBias tuneBias;
    
    /** Creates a new instance of IPot passing only the biasgen it belongs to. All other parameters take default values.
     *<p>
     *This IPot also adds itself as an observer for the Masterbias object.
     @param biasgen the biasgen this ipot is part of
     */
    protected R10YBias(Biasgen biasgen) {
        super(biasgen);
        setNumBytes(1);
        setNumBits(3);
    }
    
    /** Creates a new instance of IPot
     *@param biasgen the containing Biasgen.
     *@param name displayed and used to return by name.
     *@param shiftRegisterNumber the position in the shift register,      
     * 0 based, starting on end from which bits are loaded. 
     * This order determines how the bits are sent to the shift register, 
     * lower shiftRegisterNumber are loaded later, so that they end up at the start of the shift register.
     * The last bit on the shift register is loaded first and is the msb of the last bias 
     * on the shift register.
     * The last bit loaded into the shift register is the lsb of the first bias on the shift register.
     *@param type (NORMAL, CASCODE) - for user information.
     *@param sex Sex (N, P). User tip.
     * @param bitValue initial bitValue.
     *@param displayPosition position in GUI from top (logical order).
     *@param tooltipString a String to display to user of GUI telling them what the pots does.
     */
    public R10YBias(R10YIRefTuneBias tuneBias, Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
        this(biasgen);
        this.tuneBias=tuneBias;
        setName(name);
        this.setType(type);
        this.setSex(sex);
        this.bitValue=bitValue;
        this.displayPosition=displayPosition;
        this.tooltipString=tooltipString;
        this.shiftRegisterNumber=shiftRegisterNumber;
        loadPreferences(); // do this after name is set
       if(chip.getRemoteControl()!=null){
            chip.getRemoteControl().addCommandListener(this, String.format("seti_%s bitvalue",getName()), "Set the bitValue of IPot "+getName());
        }
    }

    @Override
    public float setCurrent(float current) {
        float itune=tuneBias.getCurrent();
        // TODO set bits according to desired current 
        return super.setCurrent(current);
    }

    @Override
    public float getCurrent() {
        // TODO use tunebias to compute actual current here
        float itune=tuneBias.getCurrent();
        float current=itune*(2+2*(bitValue&1)+4*((bitValue&2)>>1)+8*((bitValue&4)>>2));
        return current;
    }

  
    

}
