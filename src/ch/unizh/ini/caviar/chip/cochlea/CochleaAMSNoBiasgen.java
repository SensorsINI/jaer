/*
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.chip.cochlea;

import ch.unizh.ini.caviar.biasgen.BiasgenHardwareInterface;
import ch.unizh.ini.caviar.biasgen.IPot;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.chip.Chip;
import ch.unizh.ini.caviar.chip.TypedEventExtractor;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterface;


/**
 * For Shih-Chii's AMS cochlea with binaraul 64 stage cochlea each tap with 8 ganglion cells, 4 of LPF type and 4 of BPF type.
 *The bits in the raw address and the histogram display are arranged as shown in the following sketch. This class doesn't define the bias generator
 so that it can be used when another besides the on-chip source supplies the bias voltages or inputs to the on-chip bias generator.
 *<p>
 *<img src="doc-files/cochleaAMSSketch.jpg" />
 *</p>
 * @author tobi
 */
public class CochleaAMSNoBiasgen extends CochleaChip  {
    
    /** Creates a new instance of Tmpdiff128 */
    public CochleaAMSNoBiasgen() {
        setName("CochleaAMSNoBiasgen");
        setSizeX(64);
        setSizeY(4);
        setNumCellTypes(2); // right,left cochlea
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
        setEventClass(TypedEvent.class);
    }
    
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{
        public Extractor(AEChip chip){
            super(chip);
//            setXmask((short)(63<<2)); // tap bits are bits 2-7
//            setXshift((byte)2); // shift them right to get back to 0-63 after AND masking
//            setYmask((short)0x300); // we don't need y or type because these are overridden below
//            setYshift((byte)8);
//            setTypemask((short)2);
//            setTypeshift((byte)0);
//            setFliptype(true); // no 'type' so make all events have type 1=on type
        }
        @Override public short getXFromAddress(short addr){
            short tap=(short)((addr&0xfc)>>>2);
            return tap;
        }
        
        /** Overrides default extract to define type of event as the left or right cochlea. All other
         *information about other event type is ignored.
         *@param addr the raw address
         *@return the type, where 0 is LEFT cochlea, 1 is RIGHT cochlea
         */
        @Override public byte getTypeFromAddress(short addr){
            return (byte)((addr&0x02)>>>1);
        }
        
        /** Overrides default extractor to spread all outputs from a tap (left/right, ganglion cell, LPF/HPF) into a
         *single y address that can be displayed in the 2d histogram. 
         * The y returned goes like this from 0-15: left LPF(4)/BPF(4), right LPF(4)/BPF(4).
         *@param addr the raw address
         *@return the Y address
         */
        @Override public short getYFromAddress(short addr){
//            int gangCell=(addr&0x300)>>>8; // each tap has 8 ganglion cells, 4 of each of LPF/BPF type
//            int lpfBpf=(addr&0x01)<<2; // lowpass/bandpass ganglion cell type
//            int leftRight=(addr&0x02)<<2; // left/right cochlea. see javadoc jpg scan for layout
//            short v=(short)(gangCell+lpfBpf+leftRight);
            int lpfBpf=(addr&0x01); // lowpass/bandpass ganglion cell type
            int leftRight=(addr&0x02); // left/right cochlea. see javadoc jpg scan for layout
            short v=(short)(lpfBpf+leftRight);
            return v;
        }
        
    }
}
