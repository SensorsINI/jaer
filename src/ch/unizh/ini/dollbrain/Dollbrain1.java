/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.dollbrain;

import ch.unizh.ini.caviar.biasgen.*;
import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.biasgen.VDAC.*;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.chip.TypedEventExtractor;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.graphics.*;
import java.io.*;


/**
 *  Chip description for of conv32 imse raphael/bernabe convolution chip.
 * @author patrick/raphael
 */
public class Dollbrain1 extends AEChip implements Serializable  {
    
    /** Creates a new instance of Dollbrain */
    public Dollbrain1() {
        setSizeX(13);
        setSizeY(4);
        setNumCellTypes(8);
        setEventClass(ColorEvent.class);
        setEventExtractor(new Extractor(this));
        setBiasgen(new DollBrainBiasgen(this));
        setRenderer(new Dollbrain1Renderer(this));
    }
    
    public static final float VDD=5;
    public static DAC dac=new DAC(16,12,0,VDD);
    
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{
        
        protected short colormask;
        protected byte colorshift;
        
        public Extractor(AEChip chip){
            super(chip);
            setEventClass(ColorEvent.class);
            setXmask((short)0x07);
            setXshift((byte)0);
            setYmask((short)0x18);
            setYshift((byte)3);
            setTypemask((short)0x00E0);
            setTypeshift((byte)5);
            setColormask((short)0xFF00);
            setColorshift((byte)8);
            //setFlipx(true);
        }
        
        public short getColormask() {
            return this.colormask;
        }
        
        public void setColormask(final short cmask) {
            this.colormask = cmask;
        }
        
        public byte getColorshift() {
            return this.colorshift;
        }
        
        public void setColorshift(final byte cshift) {
            this.colorshift = cshift;
        }
        
        public short getColorFromAddress(int addr){
            return (short)((addr&colormask)>>>colorshift);
        }
        
        
        /** extracts the meaning of the raw events. This form is used to supply an output packet. This method is used for real time event filtering using
     a buffer of output events local to data acquisition.
         *@param in the raw events, can be null
         *@param out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        synchronized public void extractPacket(AEPacketRaw in, EventPacket out) {
            out.clear();
            if(in==null) return;
            int n=in.getNumEvents(); //addresses.length;
            
            int skipBy=1;
            
            int[] a=in.getAddresses();
            int[] timestamps=in.getTimestamps();
            boolean hasTypes=false;
            if(chip!=null) hasTypes=chip.getNumCellTypes()>1;
            OutputEventIterator outItr=out.outputIterator();
            for(int i=0;i<n;i+=skipBy){ // bug here?
                int addr=a[i];
                BasicEvent e=(BasicEvent)outItr.nextOutput();
                e.timestamp=(timestamps[i]);
                e.x=getXFromAddress(addr);
                e.y=getYFromAddress(addr);
                if(hasTypes){
                    ((TypedEvent)e).type=getTypeFromAddress(addr);
                }
                ((ColorEvent)e).color=this.getColorFromAddress(addr);
//            System.out.println("a="+a[i]+" t="+e.timestamp+" x,y="+e.x+","+e.y);
            }
        }
    }
    
      /** describes the biases  */
    public class DollBrainBiasgen extends Biasgen{
        
        public DollBrainBiasgen(Chip chip){
            super(chip);
        /* from firmware

         */
//    public VPot(Chip chip, String name, DAC dac, int channel, Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
            
            potArray = new PotArray(this);
            
            getPotArray().addPot(new VPot(Dollbrain1.this,"VBinv",dac,  1,Pot.Type.NORMAL,Pot.Sex.N,550,      10,"Current limiter for inverter for SI pulse."));
            getPotArray().addPot(new VPot(Dollbrain1.this,"VreqPD",dac,  2,Pot.Type.NORMAL,Pot.Sex.N,650,      8,"request pull up"));
            getPotArray().addPot(new VPot(Dollbrain1.this,"VAbias",dac,  3,Pot.Type.NORMAL,Pot.Sex.N,650,      9,"VAS and VCS OTA bias"));
            getPotArray().addPot(new VPot(Dollbrain1.this,"VdischargeBias",dac,  4,Pot.Type.NORMAL,Pot.Sex.N,500,      11,"Capacitor discharge bias (controls the length of the SI pulse)"));
            getPotArray().addPot(new VPot(Dollbrain1.this,"PixelBufferBias",dac,  5,Pot.Type.NORMAL,Pot.Sex.P,3500,      12,"Source follower bias"));
            getPotArray().addPot(new VPot(Dollbrain1.this,"VcompBias",dac,   6,Pot.Type.NORMAL,Pot.Sex.N,600,      13,"bias for all comparators"));
            getPotArray().addPot(new VPot(Dollbrain1.this,"VbusyPU",dac,             7,Pot.Type.NORMAL,Pot.Sex.P,3100,  7,"nBusy line pull-up."));
            getPotArray().addPot(new VPot(Dollbrain1.this,"VreqPU",dac,           8,Pot.Type.NORMAL,Pot.Sex.P,3100,      6,"Request pull-up"));
            getPotArray().addPot(new VPot(Dollbrain1.this,"VCS",dac,           9,Pot.Type.NORMAL,Pot.Sex.na,3000,      0,"Start voltage for common node of vertacolor diode."));
            getPotArray().addPot(new VPot(Dollbrain1.this,"VAS",dac,             0xa,Pot.Type.NORMAL,Pot.Sex.na,1650,    1,"Start voltage for active node of vertacolor diode"));
            getPotArray().addPot(new VPot(Dollbrain1.this,"VClo",dac,           0xb,Pot.Type.NORMAL,Pot.Sex.na,1650,    2,"Lower comparator reference value."));
            getPotArray().addPot(new VPot(Dollbrain1.this,"VChi",dac,           0xc,Pot.Type.NORMAL,Pot.Sex.na,2800,    3,"Upper comparator reference value"));
            getPotArray().addPot(new VPot(Dollbrain1.this,"Vsample",dac,           0xd,Pot.Type.NORMAL,Pot.Sex.na,2050,    4,"Sample voltage for eye features."));
            getPotArray().addPot(new VPot(Dollbrain1.this,"FollBias",dac,             0xe,Pot.Type.NORMAL,Pot.Sex.N,600,    5,"Widepad follower bias"));
        }
    }
}
