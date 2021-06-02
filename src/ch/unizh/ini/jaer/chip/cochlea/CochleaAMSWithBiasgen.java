/*
 * CochleaAMSWithBiasgen.java
 *
 * Created on November 7, 2006, 11:29 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright November 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.chip.cochlea;

import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * Extends Shih-Chii's AMS cochlea AER chip to 
 * add bias generator interface, 
 * to be used when using the on-chip bias generator.
 * @author tobi
 * @deprecated replaced by CochleaAMS1b which has a totally new Biasgen for controlling all the digital features of CochleaAMS1b
 */
@Deprecated
public class CochleaAMSWithBiasgen extends CochleaAMSNoBiasgen {
    
    /** Creates a new instance of CochleaAMSWithBiasgen */
    public CochleaAMSWithBiasgen() {
        super();
        setBiasgen(new CochleaAMSWithBiasgen.Biasgen(this));
    }
    
    /** overrides the Chip setHardware interface to construct a biasgen if one doesn't exist already.
     * Sets the hardware interface and the bias generators hardware interface
     *@param hardwareInterface the interface
     */
    @Override public void setHardwareInterface(final HardwareInterface hardwareInterface) {
        this.hardwareInterface = hardwareInterface;
        try{
            if(getBiasgen()==null)
                setBiasgen(new CochleaAMSWithBiasgen.Biasgen(this));
            else
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface)hardwareInterface);
        }catch(ClassCastException e){
            System.err.println(e.getMessage()+": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }
    
    /**
     * Describes IPots. These are configured by a shift register as shown here:
     *<p>
     *<img src="doc-files/tmpdiff128biasgen.gif" alt="tmpdiff128 shift register arrangement"/>
     
     * @author tobi
     */
    public class Biasgen extends net.sf.jaer.biasgen.Biasgen {
        
//        private IPot diffOn, diffOff, refr, pr, sf, diff;
        
        /** Creates a new instance of Biasgen for Tmpdiff128 with a given hardware interface
         *@param chip the chip this biasgen belongs to
         */
        public Biasgen(Chip chip) {
            super(chip);
            setName("CochleaAMSWithBiasgen");
            
            
//  /** Creates a new instance of IPot
            
            
//     *@param biasgen
//     *@param name
//     *@param shiftRegisterNumber the position in the shift register, 0 based, starting on end from which bits are loaded
//     *@param type (NORMAL, CASCODE)
//     *@param sex Sex (N, P)
//     * @param bitValue initial bitValue
//     *@param displayPosition position in GUI from top (logical order)
//     *@param tooltipString a String to display to user of GUI telling them what the pots does
//     */
////    public IPot(Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
            
//            iPotArray.addPot(new IPot(this,"",))
//            iPotArray.addPot(new IPot(this,"cas", 11, IPot.Type.CASCODE, IPot.Sex.N, 0, 2, "Photoreceptor cascode"));
//            iPotArray.addPot(new IPot(this, "injGnd", 10, IPot.Type.CASCODE, IPot.Sex.P, 0, 7, "Differentiator switch level, higher to turn on more"));
//            iPotArray.addPot(new IPot(this, "reqPd", 9, IPot.Type.NORMAL, IPot.Sex.N,0, 12, "AER request pulldown"));
//            iPotArray.addPot(new IPot(this, "puX", 8,IPot.Type.NORMAL, IPot.Sex.P,0, 11, "2nd dimension AER static pullup"));
            
            potArray = new IPotArray(this); //construct IPotArray whit shift register stuff
            
            getPotArray().addPot(new IPot(this, "Vcas", 29,IPot.Type.CASCODE, IPot.Sex.N,0,21,"Cascode biasfor HC and BPF"));
            getPotArray().addPot(new IPot(this, "pdbiasTX", 28,IPot.Type.NORMAL, IPot.Sex.N,0,30,"Pdbias for AER sender"));
            getPotArray().addPot(new IPot(this, "Vin1", 27,IPot.Type.NORMAL, IPot.Sex.P,0,1,"Auditory input to 1st cochlea"));
            getPotArray().addPot(new IPot(this, "VAIO", 26,IPot.Type.NORMAL, IPot.Sex.P,0,24,"CLBT bias for BPF filter"));
            getPotArray().addPot(new IPot(this, "VbiasC", 25,IPot.Type.NORMAL, IPot.Sex.P,0,10,"DC current for setting offset in 2nd order section"));
            getPotArray().addPot(new IPot(this, "Vgain", 24,IPot.Type.NORMAL, IPot.Sex.P,0,15,"Bias current of differentiator"));
            getPotArray().addPot(new IPot(this, "Vioff", 23,IPot.Type.NORMAL, IPot.Sex.P,0,17,"DC Pfet current for differentiator of 2nd order section"));
            getPotArray().addPot(new IPot(this, "Vq", 22,IPot.Type.NORMAL, IPot.Sex.P,0,6,"Feedback time constant of 2nd order section of cochlea"));
            getPotArray().addPot(new IPot(this, "Vclbtgate", 21,IPot.Type.NORMAL, IPot.Sex.P,0,7,"CLBT bias of cochlea"));
            getPotArray().addPot(new IPot(this, "Vrefract", 20,IPot.Type.NORMAL, IPot.Sex.P,0,29,"Refractory period of neurons"));
            getPotArray().addPot(new IPot(this, "Vsetio", 19,IPot.Type.NORMAL, IPot.Sex.P,0,19,"Sets Time constant of cm lpf filter for envelope"));
            getPotArray().addPot(new IPot(this, "Vdc1", 18,IPot.Type.NORMAL, IPot.Sex.P,0,11,"DC input current 1 for resistive tilt across cochlea"));
            getPotArray().addPot(new IPot(this, "Vin2", 17,IPot.Type.NORMAL, IPot.Sex.P,0,2,"Auditory input to second cochlea"));
            getPotArray().addPot(new IPot(this, "Vbpf2", 16,IPot.Type.NORMAL, IPot.Sex.P,0,23,"BPF filter bias 2 for resistive tilt across cochlea"));
            getPotArray().addPot(new IPot(this, "Vioffbpf", 15,IPot.Type.NORMAL, IPot.Sex.P,0,25,"DC Pfet current for differentiator for BPF filter"));
            getPotArray().addPot(new IPot(this, "Vcascode", 14,IPot.Type.NORMAL, IPot.Sex.N,0,9,"Cascode bias in cochlea"));
            getPotArray().addPot(new IPot(this, "Vrefo", 13,IPot.Type.NORMAL, IPot.Sex.P,0,14,"Source output of LPF, sets gain of LPF input to neuron"));
            getPotArray().addPot(new IPot(this, "Vtau", 12,IPot.Type.NORMAL, IPot.Sex.P,0,5,"Feedforward timeconstant of 2nd order section of cochlea"));
            getPotArray().addPot(new IPot(this, "Vbias2", 11,IPot.Type.NORMAL, IPot.Sex.P,0,4,"Bias 2 for resistive tilt across cochlea, sets low freq CF"));
            getPotArray().addPot(new IPot(this, "Vth1", 10,IPot.Type.NORMAL, IPot.Sex.N,0,27,"Threshold 1 for output neurons"));
            getPotArray().addPot(new IPot(this, "Vsetioadap", 9,IPot.Type.NORMAL, IPot.Sex.P,0,20,"Sets Time constant of cm lpf filter for HC adaptation"));
            
            getPotArray().addPot(new IPot(this, "Vbias1", 8,IPot.Type.NORMAL, IPot.Sex.P,0,3,"Bias 1 for resistive tilt across cochlea, sets high freq CF"));
            getPotArray().addPot(new IPot(this, "Vbpf1", 7,IPot.Type.NORMAL, IPot.Sex.P,0,22,"BPF filter bias 1 for resistive tilt across cochlea"));
            getPotArray().addPot(new IPot(this, "Vrefn", 6, IPot.Type.NORMAL, IPot.Sex.N,0, 26, "Source bias for cm bpf filter"));
            getPotArray().addPot(new IPot(this, "Vbamp", 5,IPot.Type.NORMAL, IPot.Sex.N,0, 16, "Bias for follower that conveys BPF input to neuron"));
            getPotArray().addPot(new IPot(this, "Vion", 4,IPot.Type.NORMAL, IPot.Sex.N,0, 18,"Nfet input current for differentiator of HC"));
            getPotArray().addPot(new IPot(this, "Vref", 3,IPot.Type.NORMAL, IPot.Sex.P,0,13,"Source output of input to cm LPF of HC"));
            getPotArray().addPot(new IPot(this, "Vclbtcasc", 2,IPot.Type.NORMAL, IPot.Sex.P,0,8,"Cascode bias for 2nd order section"));
            getPotArray().addPot(new IPot(this, "Vdc2", 1,IPot.Type.NORMAL, IPot.Sex.P,0,12,"DC Bias 2 for resistive tilt of DC input across cochlea"));
            getPotArray().addPot(new IPot(this, "Vth2", 0,IPot.Type.NORMAL, IPot.Sex.N,0,28,"Threshold 2 for output neurons"));
            
            loadPreferences();
            
        }
    }
    
    
}
