/*
 * Motion18.java
 *
 * Created on November 1, 2006, 12:24 PM
 *
 *  Copyright T. Delbruck, Inst. of Neuroinformatics, 2006
 */

package ch.unizh.ini.jaer.projects.opticalflow.motion18;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.Chip;
import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.MotionData;
import ch.unizh.ini.jaer.projects.opticalflow.graphics.OpticalFlowDisplayMethod;

/**
 * Describes the motion18 chip from Alan Stocker with Pit Gebbers board wrapped around it.
 *
 * @author tobi
 */
public class Motion18 extends Chip2DMotion {



    /** Creates a new instance of Motion18 */
    public Motion18() {
        CHIPNAME="Motion18";
        VDD=5;
        NUM_ROWS=32;
        NUM_COLUMNS=32;
        NUM_MOTION_PIXELS=NUM_COLUMNS*NUM_ROWS;
        acquisitionMode=MotionData.GLOBAL_X|MotionData.GLOBAL_Y|MotionData.PHOTO|MotionData.UX|MotionData.UY;
        dac=new DAC(16,12,0,VDD,VDD);
        setBiasgen(new Motion18Biasgen(this, dac));
        setSizeX(NUM_COLUMNS);
        setSizeY(NUM_ROWS);
        getCanvas().addDisplayMethod(new OpticalFlowDisplayMethod(this.getCanvas()));
    }


   @Override
public MotionData getEmptyMotionData(){
        return new MotionDataMotion18(this);
    }


    /** describes the biases on the chip */
    public class Motion18Biasgen extends Biasgen{

        public Motion18Biasgen(Chip chip, DAC dac){
            super(chip);
        /* from firmware
                 DAC channel addresses
            #define DAC_ADDR_FBIAS     0x00
            #define DAC_ADDR_HDTP      0x01
            #define DAC_ADDR_HDTM      0x02
            #define DAC_ADDR_PHABIAS   0x03
            #define DAC_ADDR_PHBIAS    0x04
            #define DAC_ADDR_PHFBIAS   0x05
            #define DAC_ADDR_HDBIAS    0x06
            #define DAC_ADDR_VM        0x07
            #define DAC_ADDR_HRES      0x08
            #define DAC_ADDR_VVI2      0x09
            #define DAC_ADDR_OPBIAS    0x0A
            #define DAC_ADDR_VVI1      0x0B
            #define DAC_ADDR_NSCFBIAS  0x0D
            #define DAC_ADDR_PSCFBIAS  0x0E
            #define DAC_ADDR_PDBIAS    0x0F
         */
            /* from biasgen prefs after prelim setup
             <entry key="ipot FollBias" value="881"/>
             <entry key="ipot HD+tweak" value="22"/>
             <entry key="ipot HD-tweak" value="132"/>
             <entry key="ipot Ph Adaptation" value="220"/>
             <entry key="ipot Photoreceptor" value="3457"/>
             <entry key="ipot Ph Foll" value="3258"/>
             <entry key="ipot HD" value="572"/>
             <entry key="ipot VM" value="1596"/>
             <entry key="ipot HRES" value="873"/>
             <entry key="ipot VVI2" value="286"/
             ><entry key="ipot OP" value="286"/>
             <entry key="ipot VVI1" value="705"/>
             <entry key="ipot NSCF" value="638"/>
             <entry key="ipot PSCF" value="3060"/>
             <entry key="ipot PD" value="1255"/>
             */
//    public VPot(String name, DAC dac, int channel, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
//    public VPot(Chip chip, String name, DAC dac, int channel, Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {

            potArray = new PotArray(this);  // create the appropriate PotArray

            getPotArray().addPot(new VPot(Motion18.this,"FollBias",dac,       0,Pot.Type.NORMAL,Pot.Sex.N,881,      99,"Chip pad follower bias"));
            getPotArray().addPot(new VPot(Motion18.this,"HD+tweak",dac,       1,Pot.Type.NORMAL,Pot.Sex.N,22,      4,"current gain knob (sources) of the temporal differentiator."));
            getPotArray().addPot(new VPot(Motion18.this,"HD-tweak",dac,       2,Pot.Type.NORMAL,Pot.Sex.N,132,      5,"current gain knob (sources) of the temporal differentiator."));
            getPotArray().addPot(new VPot(Motion18.this,"Ph Adaptation",dac,  3,Pot.Type.NORMAL,Pot.Sex.N,220,      1,"photoreceptor adaptation"));
            getPotArray().addPot(new VPot(Motion18.this,"Photoreceptor",dac,  4,Pot.Type.NORMAL,Pot.Sex.P,3457,      0,"Photoreceptor bias"));
            getPotArray().addPot(new VPot(Motion18.this,"Ph Foll",dac,        5,Pot.Type.NORMAL,Pot.Sex.P,3258,      2,"Photoreceptor follower bias"));
            getPotArray().addPot(new VPot(Motion18.this,"HD",dac,             6,Pot.Type.NORMAL,Pot.Sex.N,572,      3,"Hysteretic temporal differentiator bias"));
            getPotArray().addPot(new VPot(Motion18.this,"VM",dac,             7,Pot.Type.REFERENCE,Pot.Sex.na,1596,  10,"virtual signal ground for motion read-out voltage. "));
            getPotArray().addPot(new VPot(Motion18.this,"HRES",dac,           8,Pot.Type.NORMAL,Pot.Sex.N,873,      6,"diffuser network bias"));
            getPotArray().addPot(new VPot(Motion18.this,"VVI2",dac,           9,Pot.Type.NORMAL,Pot.Sex.N,286,      8,"outer bias for wider linear range multiplier. above thresold -> increase linear range, but increase power consumption."));
            getPotArray().addPot(new VPot(Motion18.this,"OP",dac,             0xa,Pot.Type.NORMAL,Pot.Sex.N,286,    9,"transamp conductance to V- (virtual singal ground)"));
            getPotArray().addPot(new VPot(Motion18.this,"VVI1",dac,           0xb,Pot.Type.NORMAL,Pot.Sex.N,705,    7,"inner bias for wider linear range multiplier. above thresold -> increase linear range, but increase power consumption."));
            getPotArray().addPot(new VPot(Motion18.this,"NSCF",dac,           0xd,Pot.Type.NORMAL,Pot.Sex.N,638,    20,"source follower bias for scanner"));
            getPotArray().addPot(new VPot(Motion18.this,"PSCF",dac,           0xe,Pot.Type.NORMAL,Pot.Sex.P,3060,    21,"source follower bias for scanner"));
            getPotArray().addPot(new VPot(Motion18.this,"PD",dac,             0xf,Pot.Type.NORMAL,Pot.Sex.N,1255,    22,"scanner wired OR static pulldown"));
        }
    }

}
