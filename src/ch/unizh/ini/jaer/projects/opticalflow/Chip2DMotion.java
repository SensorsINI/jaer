/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.opticalflow;

import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.MotionChipInterface;

/**
 * Abstract class for motion chips. Provides some static variables methods for 
 * motion chips. The class mainly serves to allow late binding.
 * 
 * @author reto
 */

public  abstract class Chip2DMotion extends Chip2D {
    public static String CHIPNAME =null; // the name of the chip. set in subclasses
    public static float VDD;
    public static int NUM_ROWS;
    public static int NUM_COLUMNS;
    public static int NUM_MOTION_PIXELS;
    public static DAC dac;
    /** the data to get for the chip */
    public int acquisitionMode;
    /** can be used to hold reference to last motion data */
    public MotionData lastMotionData=null;
    public ChipCanvas[]canvasArray;
    public OpticalFlowIntegrator integrator= null; // keeps track on accumulative effect of motions values



    public  Chip2DMotion() {
        super();
        setCanvas(new ChipCanvas(this));
        integrator= new OpticalFlowIntegrator(this);
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


    
    //Method to get a empty motionData object
    public abstract MotionData getEmptyMotionData();

    public void setCaptureMode(int acquisitionMode) {
        this.acquisitionMode = acquisitionMode;
        if (hardwareInterface != null && hardwareInterface.isOpen()) {
            try {
                ((MotionChipInterface) hardwareInterface).setCaptureMode(acquisitionMode);
            } catch (HardwareInterfaceException ex) {
                log.warning("cannot set capture mode : " + ex);
            }
        }
    }

    

    
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
