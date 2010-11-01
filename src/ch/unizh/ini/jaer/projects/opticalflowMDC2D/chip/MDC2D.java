/*
 * Motion18.java
 *
 * Created on November 1, 2006, 12:24 PM
 *
 *  Copyright T. Delbruck, Inst. of Neuroinformatics, 2006
 */

package ch.unizh.ini.jaer.projects.opticalflowMDC2D.chip;

import net.sf.jaer.biasgen.*;
import net.sf.jaer.biasgen.VDAC.*;
import net.sf.jaer.chip.*;
import ch.unizh.ini.jaer.projects.opticalflowMDC2D.graphics.OptFlowDisplayMethod;
import ch.unizh.ini.jaer.projects.opticalflowMDC2D.usbinterface.MotionChipInterface;

/**
 * Describes the MDC2D chip from Shih-Chii Liu with Pit Gebbers board wrapped around it.
 *
 * @author reto
 */
public class MDC2D extends Chip2D {
    
    /** power supply for motion chip, volts */
    public static final float VDD=(float)3.3;
    
    public static final int NUM_ROWS=20;
    public static final int NUM_COLUMNS=20;
    public static final int NUM_MOTION_PIXELS=NUM_COLUMNS*NUM_ROWS;
    public static final int NUM_CHANNELS=3;
    /** A "magic byte" marking the start of each frame */
    public static final byte FRAME_START_MARKER = (byte)0xac;
    
    /** the data to get for the chip */
    private int acquisitionMode=MotionDataMDC2D.RECEP|MotionDataMDC2D.LMC1|MotionDataMDC2D.LMC2;
    
    /** can be used to hold reference to last motion data */
    public MotionDataMDC2D lastMotionData=null;
    
    /** Creates a new instance of Motion18 */
    public MDC2D() {
        setBiasgen(new MDC2DBiasgen(this));
        setSizeX(NUM_COLUMNS);
        setSizeY(NUM_ROWS);
        getCanvas().addDisplayMethod(new OptFlowDisplayMethod(this.getCanvas()));
        getCanvas().setOpenGLEnabled(true);
        getCanvas().setScale(22f);
    }
    
    // public DAC(int numChannels, int resolutionBits, float refMinVolts, float refMaxVolts){
    /** The DAC on the board */
    public static DAC dac=new DAC(16,12,0,5,VDD);
    
    public static boolean isBorder(int x, int y) {
        return ((x==0) || (y == 0) || (x == (NUM_COLUMNS-1)) || (y==(NUM_ROWS-1)));
    }
    
    /** Converts 10 bits signed ADC output value to a float ranged 0-1.
     * 0 represents most negative value, .5 is zero value, 1 is most positive value.
      *@param value the 10 bit value.
     *@return the float value, ranges from 0 to 1023/1024 inclusive.
     */
    public static float convert10bitToFloat(int value) {
//     See http://en.wikipedia.org/wiki/Twos_complement
        if((value & 0x200)!=0) // Value is negative (2's complement representation)
            value |= 0xFFFFFC00;  // Add the upper sign bits
        float r= (value+512)/1023f; // value will range from -512 to 511 - add 512 to it to get 0-1023 and divide by 1023
        return r;
//        return ((value & 0x03FF)/1023f);
    }
    
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

            getPotArray().addPot(new VPot(MDC2D.this,"VRegRefBiasAmp",dac,       0,Pot.Type.NORMAL,Pot.Sex.P,1,      1,"sets bias of feedback follower in srcbias"));
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

    /** returns the current acquisition mode - some combination of bits from MotionData
     @return mode bits
     */
    public int getCaptureMode() {
        return acquisitionMode;
    }

    /** sets the acquisition mode. Sends a command to the hardware interface to 
     set the data that is sent from the chip, if the hardware interface exists and is open.
     @param acquisitionMode a bit mask telling the hardware what to acquire from the chip
     */
    public void setCaptureMode(int acquisitionMode) {
        this.acquisitionMode = acquisitionMode;
        if(hardwareInterface!=null && hardwareInterface.isOpen()){
            ((MotionChipInterface)hardwareInterface).setCaptureMode(acquisitionMode);
        }
    }

    public int convertVtoBitValue (int set_mV){
        int bitvalue= (int)((4095*set_mV)/5000)&0xFF;
        return bitvalue;
    }
    
}
