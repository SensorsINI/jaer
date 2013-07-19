/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.retina.r10y;

import net.sf.jaer.biasgen.Biasgen;

/**
 * The master tuning current source that all biases share.
 * It is a resistive ladder where the bit value is decoded to short out the ladder at a certain point.
 * There are 4 unit resistors to start the line so the total resistance is 4+bitValue.
 * The current generated is then the bandgap voltage divided by the resistance.
 * 
 * @author tobi
 */
public class R10YIRefTuneBias extends R10YBias{

    public static final float VBANDGAP=2f; // TODO correct value
    public static final float RUNIT=7.5e3f; // TODO correct
    
    /** Creates a new instance of IPot passing only the biasgen it belongs to. All other parameters take default values.
     *<p>
     *This IPot also adds itself as an observer for the Masterbias object.
     @param biasgen the biasgen this ipot is part of
     */
    protected R10YIRefTuneBias(Biasgen biasgen) {
        super(biasgen);
        setNumBytes(1);
        setNumBits(4);
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
    public R10YIRefTuneBias(Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
        this(biasgen);
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
        // TODO set bits here based on current
        return Math.round(current);
    }

    @Override
    public float getCurrent() {
        // TODO compute actual current here based on bit value
        return bitValue;
    }

    @Override
    public float getMaxCurrent() {
        return 15; // TODO just max unit resistors now
    }

    @Override
    public float getMinCurrent() {
        return 0;  // TODO
    }

    
  
    

}
