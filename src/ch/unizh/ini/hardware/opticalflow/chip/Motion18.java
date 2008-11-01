/*
 * Motion18.java
 *
 * Created on November 1, 2006, 12:24 PM
 *
 *  Copyright T. Delbruck, Inst. of Neuroinformatics, 2006
 */

package ch.unizh.ini.hardware.opticalflow.chip;

import ch.unizh.ini.caviar.biasgen.*;
import ch.unizh.ini.caviar.biasgen.VDAC.*;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.hardware.opticalflow.graphics.OpticalFlowDisplayMethod;
import ch.unizh.ini.hardware.opticalflow.usbinterface.MotionChipInterface;

/**
 * Describes the motion18 chip from Alan Stocker with Pit Gebbers board wrapped around it.
 *
 * @author tobi
 */
public class Motion18 extends Chip2D {
    
    /** power supply for motion chip, volts */
    public static final float VDD=5;
    
    public static final int NUM_ROWS=32;
    public static final int NUM_COLUMNS=32;
    public static final int NUM_MOTION_PIXELS=NUM_COLUMNS*NUM_ROWS;
    public static final int NUM_CHANNELS=3;
    /** A "magic byte" marking the start of each frame */
    public static final byte FRAME_START_MARKER = (byte)0xac;
    
    /** the data to get for the chip */
    private int acquisitionMode=MotionData.GLOBAL_X|MotionData.GLOBAL_Y|MotionData.PHOTO|MotionData.UX|MotionData.UY;
    
    /** can be used to hold reference to last motion data */
    public MotionData lastMotionData=null;
    
    /** Creates a new instance of Motion18 */
    public Motion18() {
        setBiasgen(new Motion18Biasgen(this));
        setSizeX(NUM_COLUMNS);
        setSizeY(NUM_ROWS);
        getCanvas().addDisplayMethod(new OpticalFlowDisplayMethod(this.getCanvas()));
        getCanvas().setOpenGLEnabled(true);
        getCanvas().setScale(22f);
    }
    
    // public DAC(int numChannels, int resolutionBits, float refMinVolts, float refMaxVolts){
    /** The DAC on the board */
    public static DAC dac=new DAC(16,12,0,VDD);
    
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
    public class Motion18Biasgen extends Biasgen{
        
        public Motion18Biasgen(Chip chip){
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
    
}
