/*
 * TestchipARCsPixelTestArray.java
 *
 * Created on 17 jan 2006 tobi and patrick
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.jaer.chip.retina;

import java.io.Serializable;

import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.hardwareinterface.HardwareInterface;


/**
 * Describes test chip arcs for pixel test array retina and its event extractor and bias generator. 
 * Two constructors ara available, the vanilla constructor is used for event playback and the
 *one with a HardwareInterface parameter is useful for live capture. 
 * {@link #setHardwareInterface} is used when the hardware interface is constructed after the retina object.
 *The constructor that takes a hardware interface also constructs the biasgen interface.
 *
 * @author tobi
 */
public class TestchipARCsPixelTestArray extends AETemporalConstastRetina implements Serializable {
    
    
    /** Creates a new instance of Tmpdiff128. No biasgen is constructed for this constructor, because there is no hardware interface defined. */
    public TestchipARCsPixelTestArray() {
        setName("Tmpdiff128");
        setSizeX(64);
        setSizeY(32);
        setNumCellTypes(2);
        setEventExtractor(new Extractor(this));
//        setBiasgen(new Biasgen());    // don't make a Biasgen unless we have a hardware interface
    }
    
    /** Creates a new instance of Tmpdiff128
     @param hardwareInterface an existing hardware interface. This constructer is preferred. It makes a new Biasgen object to talk to the on-chip biasgen.
     */
    public TestchipARCsPixelTestArray(HardwareInterface hardwareInterface) {
        this();
         setHardwareInterface(hardwareInterface);
    }
    
    /** the event extractor for Tmpdiff128 */
    public class Extractor extends RetinaExtractor implements java.io.Serializable{
        public Extractor(TestchipARCsPixelTestArray chip){
            super(chip);
            setXmask((short)0x007e);
            setXshift((byte)1);
            setYmask((short)0x1f00);
            setYshift((byte)8);
            setTypemask((short)1);
            setTypeshift((byte)0);
            setFlipx(true);
            setFlipy(false);
            setFliptype(true);
        }
     }
    
   /** overrides the Chip setHardware interface to construct a biasgen if one doesn't exist already.
    * Sets the hardware interface and the bias generators hardware interface
     *@param hardwareInterface the interface 
     */
    public void setHardwareInterface(final HardwareInterface hardwareInterface) {
        this.hardwareInterface = hardwareInterface;
        if(getBiasgen()==null) 
            setBiasgen(new Biasgen(this)); 
        else 
            getBiasgen().setHardwareInterface((BiasgenHardwareInterface)hardwareInterface);
    }

    /**
     * Describes IPots on tmpdiff128 retina chip. These are configured by a shift register as shown here:
     *<p>
     *<img src="doc-files/tmpdiff128biasgen.gif" alt="tmpdiff128 shift register arrangement"/>
     *
     * @author tobi
     */
    public class Biasgen extends net.sf.jaer.biasgen.Biasgen {
        
        
        /** Creates a new instance of Biasgen for Tmpdiff128 with a given hardware interface
         *@param chip the hardware interface comes from this chip
         */
        public Biasgen(Chip chip) {
            super(chip);
            setName(this.getClass().getName());
            
            
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

            potArray = new IPotArray(this);
            
            potArray.addPot(new IPot(this,"cas", 1, IPot.Type.CASCODE, IPot.Sex.N, 0, 2, "Photoreceptor cascode"));
            potArray.addPot(new IPot(this, "injGnd", 10, IPot.Type.CASCODE, IPot.Sex.P, 0, 7, "Differentiator switch level, higher to turn on more"));
            potArray.addPot(new IPot(this, "reqPd", 9, IPot.Type.NORMAL, IPot.Sex.N,0, 12, "AER request pulldown"));
            potArray.addPot(new IPot(this, "puX", 8,IPot.Type.NORMAL, IPot.Sex.P,0, 11, "2nd dimension AER static pullup"));
            potArray.addPot(new IPot(this, "diffOff", 5,IPot.Type.NORMAL, IPot.Sex.N,0,6,"OFF threshold, lower to raise threshold"));
            potArray.addPot(new IPot(this, "req", 11, IPot.Type.NORMAL, IPot.Sex.N,0, 8, "OFF request inverter bias"));
            potArray.addPot(new IPot(this, "refr", 6,IPot.Type.NORMAL, IPot.Sex.P,0, 9, "Refractory period"));
            potArray.addPot(new IPot(this, "puY", 7,IPot.Type.NORMAL, IPot.Sex.P,0, 10,"1st dimension AER static pullup"));
            potArray.addPot(new IPot(this, "diffOn", 4,IPot.Type.NORMAL, IPot.Sex.N,0,5,"ON threshold - higher to raise threshold"));
            potArray.addPot(new IPot(this, "diff", 3,IPot.Type.NORMAL, IPot.Sex.N,0,4,"Differentiator"));
            potArray.addPot(new IPot(this, "foll", 2,IPot.Type.NORMAL, IPot.Sex.P,0,3,"Src follower buffer between photoreceptor and differentiator"));
            potArray.addPot(new IPot(this, "Pr", 0,IPot.Type.NORMAL, IPot.Sex.P,0,1,"Photoreceptor"));
            
            loadPreferences();
            
        }
        
    } // Tmpdiff128Biasgen
    
    
}
