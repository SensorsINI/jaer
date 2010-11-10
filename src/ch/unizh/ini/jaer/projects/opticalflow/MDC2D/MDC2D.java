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
import net.sf.jaer.graphics.ChipCanvas;

/**
 * Describes the MDC2D chip from Shih-Chii Liu with Pit Gebbers board wrapped around it.
 *
 * @author reto
 */
public class MDC2D extends Chip2DMotion {

    IPotArray ipotArray;

    
    /** Creates a new instance of Motion18 */
    public MDC2D() {
        //Biasgen internalBiasGen = new MDC2DintBiasgen(this);
        VDD=(float)3.3;
        NUM_ROWS=20;
        NUM_COLUMNS=20;
        NUM_MOTION_PIXELS=NUM_COLUMNS*NUM_ROWS;
        NUM_PIXELCHANNELS = 3; // number of channels read for each pixel
        NUM_GLOBALCHANNELS = 0;
        acquisitionMode=MotionDataMDC2D.PHOTO|MotionDataMDC2D.BIT5|MotionDataMDC2D.BIT6;
        dac=new DAC(16,12,0,5,VDD);
        setBiasgen(new MDC2DBiasgen(this, dac));
        setSizeX(NUM_COLUMNS);
        setSizeY(NUM_ROWS);

        /*canvas = new ChipCanvas[NUM_PIXELCHANNELS];
        for(int i=0; i<canvas.length;i++){
            canvas[i]=new ChipCanvas(this);
            canvas[i].addDisplayMethod(new OpticalFlowDisplayMethod(this.canvas[i]));
            canvas[i].setOpenGLEnabled(true);
            canvas[i].setScale(22f);
        }*/

        getCanvas().addDisplayMethod(new OpticalFlowDisplayMethod(this.getCanvas()));
        getCanvas().setOpenGLEnabled(true);
        getCanvas().setScale(22f);



        

    }


    // Returns a empty MotionData MDC2D Object
    public MotionData getEmptyMotionData(){
        return new MotionDataMDC2D(this);
    }



        /**
     * Converts 10 bits single ended ADC output value to a float ranged 0-1.
     * 0 represents GND, 1 is most positive value (VDD).
     * @param value the 10 bit value.
     * @return the float value, ranges from 0 to 1023/1024 inclusive.
     */
    public float convert10bitToFloat(int value) {
        return (float)value/10230;
    }
    

    
    /** describes the biases on the chip */
    public class MDC2DBiasgen extends Biasgen{
        
        public MDC2DBiasgen(Chip chip, DAC dac){
            super(chip);

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

/*
            //ipotArray = new IPotArray(this); //construct IPotArray whit shift register stuff
            getPotArray().addPot(new IPot(this, "VRegRefBiasAmp", 0, IPot.Type.NORMAL, Pot.Sex.P, 1, 21, "sets bias of feedback follower in srcbias"));
            getPotArray().addPot(new IPot(this,"VRegRefBiasMain",      1,Pot.Type.NORMAL,Pot.Sex.P,1,      22,"sets bias of pfet which sets ref to srcbias"));
            getPotArray().addPot(new IPot(this,"VprBias",              2,Pot.Type.NORMAL,Pot.Sex.P,1,      23,"bias current for pr"));
            getPotArray().addPot(new IPot(this,"Vlmcfb",               3,Pot.Type.NORMAL,Pot.Sex.N,1,      24,"bias current for diffosor"));
            getPotArray().addPot(new IPot(this,"Vprbuff",              4,Pot.Type.NORMAL,Pot.Sex.P,1,      25,"bias current for pr scr foll to lmc1"));
            getPotArray().addPot(new IPot(this,"Vprlmcbias",           5,Pot.Type.NORMAL,Pot.Sex.P,1,      26,"bias current for lmc1"));
            getPotArray().addPot(new IPot(this,"Vlmcbuff",             6,Pot.Type.NORMAL,Pot.Sex.P,1,      27,"bias current for lmc2"));
            getPotArray().addPot(new IPot(this,"Screfpix",            7,Pot.Type.NORMAL,Pot.Sex.N,1,       28,"sets scr bias for lmc2"));
            getPotArray().addPot(new IPot(this,"FollBias",            8,Pot.Type.NORMAL,Pot.Sex.N,1,      29,"sets bias for follower in pads"));
            getPotArray().addPot(new IPot(this,"Vpscrcfbias",          9,Pot.Type.NORMAL,Pot.Sex.P,1,      30,"sets bias for ptype src foll in scanner readout"));
            getPotArray().addPot(new IPot(this,"VADCbias",             0xa,Pot.Type.NORMAL,Pot.Sex.P,1,    31,"sets bias current for comperator in ADC"));
            getPotArray().addPot(new IPot(this,"Vrefminbias",          0xb,Pot.Type.NORMAL,Pot.Sex.N,1,    32,"sets bias for Srcrefmin follower from resis divider"));
            getPotArray().addPot(new IPot(this,"Srcrefmin",           0xc,Pot.Type.NORMAL,Pot.Sex.P,1,    33,"sets half Vdd for ADC"));
            getPotArray().addPot(new IPot(this,"refnegDAC",           0xd,Pot.Type.NORMAL,Pot.Sex.na,1,    34,"description"));
            getPotArray().addPot(new IPot(this,"refposDAC",           0xe,Pot.Type.NORMAL,Pot.Sex.na,1,    35,"description"));
       */ }

        public IPotArray getIpotArray(){
            return ipotArray;
        }
    }



    /** describes the biases on the chip */
    public class MDC2DinternalBiasgen extends Biasgen{

        public MDC2DinternalBiasgen(Chip chip){
            super(chip);

            potArray = new IPotArray(this); //construct IPotArray whit shift register stuff

            // create the appropriate PotArray
            getPotArray().addPot(new IPot(this, "VRegRefBiasAmp", 0, IPot.Type.NORMAL, Pot.Sex.P, 1, 1, "sets bias of feedback follower in srcbias"));
            getPotArray().addPot(new IPot(this,"VRegRefBiasMain",      1,Pot.Type.NORMAL,Pot.Sex.P,1,      2,"sets bias of pfet which sets ref to srcbias"));
            getPotArray().addPot(new IPot(this,"VprBias",              2,Pot.Type.NORMAL,Pot.Sex.P,1,      3,"bias current for pr"));
            getPotArray().addPot(new IPot(this,"Vlmcfb",               3,Pot.Type.NORMAL,Pot.Sex.N,1,      4,"bias current for diffosor"));
            getPotArray().addPot(new IPot(this,"Vprbuff",              4,Pot.Type.NORMAL,Pot.Sex.P,1,      5,"bias current for pr scr foll to lmc1"));
            getPotArray().addPot(new IPot(this,"Vprlmcbias",           5,Pot.Type.NORMAL,Pot.Sex.P,1,      6,"bias current for lmc1"));
            getPotArray().addPot(new IPot(this,"Vlmcbuff",             6,Pot.Type.NORMAL,Pot.Sex.P,1,      7,"bias current for lmc2"));
            getPotArray().addPot(new IPot(this,"Screfpix",            7,Pot.Type.NORMAL,Pot.Sex.N,1,       8,"sets scr bias for lmc2"));
            getPotArray().addPot(new IPot(this,"FollBias",            8,Pot.Type.NORMAL,Pot.Sex.N,1,      9,"sets bias for follower in pads"));
            getPotArray().addPot(new IPot(this,"Vpscrcfbias",          9,Pot.Type.NORMAL,Pot.Sex.P,1,      10,"sets bias for ptype src foll in scanner readout"));
            getPotArray().addPot(new IPot(this,"VADCbias",             0xa,Pot.Type.NORMAL,Pot.Sex.P,1,    11,"sets bias current for comperator in ADC"));
            getPotArray().addPot(new IPot(this,"Vrefminbias",          0xb,Pot.Type.NORMAL,Pot.Sex.N,1,    12,"sets bias for Srcrefmin follower from resis divider"));
            getPotArray().addPot(new IPot(this,"Srcrefmin",           0xc,Pot.Type.NORMAL,Pot.Sex.P,1,    13,"sets half Vdd for ADC"));
            getPotArray().addPot(new IPot(this,"refnegDAC",           0xd,Pot.Type.NORMAL,Pot.Sex.na,1,    14,"description"));
            getPotArray().addPot(new IPot(this,"refposDAC",           0xe,Pot.Type.NORMAL,Pot.Sex.na,1,    15,"description"));

            loadPreferences();
        }
    }


    
}
