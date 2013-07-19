/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.dvs320;

import java.util.ArrayList;

import net.sf.jaer.biasgen.Biasgen;

/**
 * A ConfigurableIPotRev0 constrained to share a buffer current in common with other ConstrainedConfigurableIPots.
 * Used to ensure that a set of biases have the same buffer current, which is neccessary in the case of a chip that
 * uses multiple biases to generate a shared shifted psrc or nsrc. Since the biases all generate the same shared shifted source
 * voltage references, they must all also be set to be either normal or low current biases; otherwise the source voltage outputs will
 * fight each other. Thus the lowCurrentModeEnabled flag is also constrained to be identical.
 * @author tobi
 */
public class ConstrainedConfigurableIPot extends ConfigurableIPotRev0 {
    ArrayList<ConstrainedConfigurableIPot> shared=new ArrayList<ConstrainedConfigurableIPot>();
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
    public ConstrainedConfigurableIPot(Biasgen biasgen, String name, int shiftRegisterNumber,
            Type type, Sex sex, boolean lowCurrentModeEnabled, boolean enabled,
            int bitValue, int bufferBitValue, int displayPosition, String tooltipString, ArrayList<ConstrainedConfigurableIPot> list) {
            super(biasgen, name, shiftRegisterNumber, type, sex, lowCurrentModeEnabled, enabled, bitValue, bufferBitValue, displayPosition, tooltipString);
           this.shared=list; 
    }
    
       /** Set the buffer bias bit value of all biases in the shared of shared biases.
     * @param bufferBitValue the value which has maxBuffeBitValue as maximum and specifies fraction of master bias
     */
    @Override
    public void setBufferBitValue(int bufferBitValue) {
        if(shared==null) return; // TODO won't set at all if shared still null
        bufferBitValue=clippedBufferBitValue(bufferBitValue);
        for(ConstrainedConfigurableIPot b:shared){
            b.bufferBitValue=bufferBitValue; // don't call the setter to avoid ???
            b.setChanged();
            b.notifyObservers();
        }
        notifyObservers();
    }

    /** Constrains all the shared biases to have the same lowCurrentModeEnabled flag setting
     *
     * @param lowCurrentModeEnabled
     */
    @Override
    public void setLowCurrentModeEnabled(boolean lowCurrentModeEnabled) {
        if(shared==null) {
            super.setLowCurrentModeEnabled(lowCurrentModeEnabled);
            return;
        }
        for(ConstrainedConfigurableIPot b:shared){
            // avoid callback storm
             if( b.isLowCurrentModeEnabled()!=lowCurrentModeEnabled) {
                 b.setChanged();
                   b.currentLevel=lowCurrentModeEnabled?CurrentLevel.Low:CurrentLevel.Normal;
             } // only setChanged if the new lowCurrentModeEnabled is different than the biases value to avoid callback storm from GUI
             b.notifyObservers();
         }
         notifyObservers();
    }



}
