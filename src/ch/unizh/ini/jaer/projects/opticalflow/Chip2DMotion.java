/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.opticalflow;

import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.MotionChipInterface;
import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.graphics.ChipCanvas;

/**
 *
 * @author 
 */

public  abstract class Chip2DMotion extends Chip2D {
    public static String CHIPNAME =null;
    public static float VDD;
    public static int NUM_ROWS;
    public static int NUM_COLUMNS;
    public static int NUM_MOTION_PIXELS;
    public static int NUM_PIXELCHANNELS; // number of channels read for each pixel
    public static int NUM_GLOBALCHANNELS; // number of global values read
    public static DAC dac;
    /** A "magic byte" marking the start of each frame */
    public static final byte FRAME_START_MARKER = (byte)0xac;
    /** the data to get for the chip */
    public int acquisitionMode;
    /** can be used to hold reference to last motion data */
    public MotionData lastMotionData=null;
    public ChipCanvas[]canvasArray;


    public  Chip2DMotion() {
        super();
    }

    public  boolean isBorder(int x, int y) {
        return (x == 0) || (y == 0) || (x == (this.NUM_COLUMNS - 1)) || (y == (this.NUM_ROWS - 1));
    }

    /** returns the current acquisition mode - some combination of bits from MotionData
    @return mode bits
     */
    public int getCaptureMode() {
        return acquisitionMode;
    }

    public int getNumberChannels(){
        return NUM_PIXELCHANNELS;
    }

    public int getNumberGlobals(){
        return NUM_GLOBALCHANNELS;
    }

    

    public abstract MotionData getEmptyMotionData();

    public void setCaptureMode(int acquisitionMode) {
        this.acquisitionMode = acquisitionMode;
        if (hardwareInterface != null && hardwareInterface.isOpen()) {
            ((MotionChipInterface) hardwareInterface).setCaptureMode(acquisitionMode);
        }
    }

    

    public int convertVtoBitValue (int set_mV){
        int bitvalue= (int)((4095*set_mV)/5000)&0xFF;
        return bitvalue;
    }



    /*public ChipCanvas getCanvas(int number){
        //return canvas[number];
        return canvas[0];
    }*/


    /**
     * Converts 10 bits signed ADC output value to a float ranged 0-1.
     * 0 represents most negative value, .5 is zero value, 1 is most positive value.
     * @param value the 10 bit value.
     * @return the float value, ranges from 0 to 1023/1024 inclusive.
     */
    public float convert10bitToFloat(int value) {
        //     See http://en.wikipedia.org/wiki/Twos_complement
        if ((value & 512) != 0) {
            value |= -1024; // Add the upper sign bits
        }
        float r = value / 1023.0F; // value will range from -512 to 511 - add 512 to it to get 0-1023 and divide by 1023
        return r;
        //        return ((value & 0x03FF)/1023f);
    }
}
