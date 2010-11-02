/*
 * Motion18.java
 *
 * Created on November 1, 2006, 12:24 PM
 *
 *  Copyright T. Delbruck, Inst. of Neuroinformatics, 2006
 */

package ch.unizh.ini.jaer.projects.opticalflow.MDC2D;

import ch.unizh.ini.jaer.projects.opticalflow.*;
import ch.unizh.ini.jaer.projects.opticalflow.graphics.OpticalFlowDisplayMethod;
import net.sf.jaer.biasgen.*;
import net.sf.jaer.biasgen.VDAC.*;
import net.sf.jaer.chip.*;

/**
 * Describes the MDC2D chip from Shih-Chii Liu with Pit Gebbers board wrapped around it.
 *
 * @author reto
 */
public class MDC2D extends Chip2DMotion {
    
    
    /** Creates a new instance of Motion18 */
    public MDC2D() {
        setBiasgen(new MDC2DBiasgen(this));
        VDD=(float)3.3;
        NUM_ROWS=20;
        NUM_COLUMNS=20;
        NUM_MOTION_PIXELS=NUM_COLUMNS*NUM_ROWS;
        NUM_CHANNELS=3;
        acquisitionMode=MotionDataMDC2D.PHOTO|MotionDataMDC2D.LMC1|MotionDataMDC2D.LMC2;
        dac=new DAC(16,12,0,5,VDD);
        setSizeX(NUM_COLUMNS);
        setSizeY(NUM_ROWS);
        getCanvas().addDisplayMethod(new OpticalFlowDisplayMethod(this.getCanvas()));
        getCanvas().setOpenGLEnabled(true);
        getCanvas().setScale(22f);

    }

    DAC dac=new DAC(16,12,0,VDD,VDD);
    

    
    /** describes the biases on the chip */
    public class MDC2DBiasgen extends Biasgen{
        
        public MDC2DBiasgen(Chip chip){
            super(chip);
        /* from firmware
                 DAC channel addresses
            #define DAC_ADDR_VREGREFBIASAMP     	0x00
            #define DAC_ADDR_VREGREFBIASMAIN      	0x01
            #define DAC_ADDR_PRBIAS      		0x02
            #define DAC_ADDR_VLMCFB   			0x03
            #define DAC_ADDR_VPRBUFF    		0x04
            #define DAC_ADDR_VPRLMCBIAS   		0x05
            #define DAC_ADDR_VLMCBUFF    		0x06
            #define DAC_ADDR_SRCREFPIX      		0x07
            #define DAC_ADDR_FOLLBIAS      		0x08
            #define DAC_ADDR_VPSRCFBIAS                 0x09
            #define DAC_ADDR_VADCBIAS    		0x0A
            #define DAC_ADDR_VREFMINBIAS      		0x0B
            #define DAC_ADDR_SCREFMIN  			0x0D
            #define DAC_ADDR_VREFNEGDAC  		0x0E
            #define DAC_ADDR_VREFPOSDAC    		0x0F
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

            // create the appropriate PotArray
            getPotArray().addPot(new VPot(MDC2D.this, "VRegRefBiasAmp", dac, 0, Pot.Type.NORMAL, Pot.Sex.P, 1, 1, "sets bias of feedback follower in srcbias"));
            getPotArray().addPot(new VPot(MDC2D.this,"VRegRefBiasMain",dac,      1,Pot.Type.NORMAL,Pot.Sex.P,1,      2,"sets bias of pfet which sets ref to srcbias"));
            getPotArray().addPot(new VPot(MDC2D.this,"VprBias",dac,              2,Pot.Type.NORMAL,Pot.Sex.P,1,      3,"bias current for pr"));
            getPotArray().addPot(new VPot(MDC2D.this,"Vlmcfb",dac,               3,Pot.Type.NORMAL,Pot.Sex.N,1,      4,"bias current for diffosor"));
            getPotArray().addPot(new VPot(MDC2D.this,"Vprbuff",dac,              4,Pot.Type.NORMAL,Pot.Sex.P,1,      5,"bias current for pr scr foll to lmc1"));
            getPotArray().addPot(new VPot(MDC2D.this,"Vprlmcbias",dac,           5,Pot.Type.NORMAL,Pot.Sex.P,1,      6,"bias current for lmc1"));
            getPotArray().addPot(new VPot(MDC2D.this,"Vlmcbuff",dac,             6,Pot.Type.NORMAL,Pot.Sex.P,1,      7,"bias current for lmc2"));
            getPotArray().addPot(new VPot(MDC2D.this,"Screfpix",dac,            7,Pot.Type.NORMAL,Pot.Sex.N,1,       8,"sets scr bias for lmc2"));
            getPotArray().addPot(new VPot(MDC2D.this,"FollBias",dac,            8,Pot.Type.NORMAL,Pot.Sex.N,1,      9,"sets bias for follower in pads"));
            getPotArray().addPot(new VPot(MDC2D.this,"Vpscrcfbias",dac,          9,Pot.Type.NORMAL,Pot.Sex.P,1,      10,"sets bias for ptype src foll in scanner readout"));
            getPotArray().addPot(new VPot(MDC2D.this,"VADCbias",dac,             0xa,Pot.Type.NORMAL,Pot.Sex.P,1,    11,"sets bias current for comperator in ADC"));
            getPotArray().addPot(new VPot(MDC2D.this,"Vrefminbias",dac,          0xb,Pot.Type.NORMAL,Pot.Sex.N,1,    12,"sets bias for Srcrefmin follower from resis divider"));
            getPotArray().addPot(new VPot(MDC2D.this,"Srcrefmin",dac,           0xc,Pot.Type.NORMAL,Pot.Sex.P,1,    13,"sets half Vdd for ADC"));
            getPotArray().addPot(new VPot(MDC2D.this,"refnegDAC",dac,           0xd,Pot.Type.NORMAL,Pot.Sex.na,1,    14,"description"));
            getPotArray().addPot(new VPot(MDC2D.this,"refposDAC",dac,           0xe,Pot.Type.NORMAL,Pot.Sex.na,1,    15,"description"));
        }
    }




    
}
