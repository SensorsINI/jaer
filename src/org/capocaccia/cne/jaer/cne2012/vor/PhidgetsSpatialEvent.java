/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.capocaccia.cne.jaer.cne2012.vor;

import java.math.BigDecimal;
import java.math.MathContext;

import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;

/**
 * Special events that allow including the Phidgets Spatial sensor events in a
 * data stream.
 *
 * @author tobi
 */
public class PhidgetsSpatialEvent extends ApsDvsEvent {

    /** The type of Spatial sensor data. */
    public enum SpatialDataType {RollClockwise, PitchUp, YawRight, AccelUp, AccelRight, AccelTowards, Unknown};

    /**
     * Roll pan and tilt rates are in deg/sec and represent, respectively
     * clockwise roll, rightwards pan, and upwards tilt for positive values.
     * Acceleration data are in g.
     */
    public float spatialDataValue=0;

    /** The type of spatial sensor data */
    public SpatialDataType spatialDataType;

    /** Default constructor with zero data and unknown data type
     * 
     */
    public PhidgetsSpatialEvent() {
        spatialDataType=SpatialDataType.Unknown;
    }
      
    
     
    @Override
    public boolean isSpecial() {
        return true;
    }
    
    private static final int PRECISION_DIGITS=3;
    private static final MathContext rounding=new MathContext(PRECISION_DIGITS);
    
    /** Constructs a new spatial event from timestamp, float sensor spatialDataValue, and type of data
     * 
     * @param timestamp in us, starting point arbitrary
     * @param spatialDataValue
     * @param type 
     */
    public PhidgetsSpatialEvent(int timestamp, float value, SpatialDataType type) {
        this.timestamp=timestamp;
        this.spatialDataValue = value;
        this.spatialDataType = type;
        BigDecimal bd=new BigDecimal(value);
        BigDecimal roundedScaled=bd.round(rounding).movePointRight(PRECISION_DIGITS);
        int intval=roundedScaled.intValue();
        this.address=(type.ordinal()<<16|(intval&0xffff));
    }
    
    /** Sets the data fields and the raw address spatialDataValue from explicit values
     * 
     * @param timestamp in us
     * @param spatialDataValue 
     * @param type 
     */
    public void setData(int timestamp, float value, SpatialDataType type) {
        this.timestamp=timestamp;
        this.spatialDataValue = value;
        this.spatialDataType = type;
        BigDecimal bd=new BigDecimal(value);
        BigDecimal roundedScaled=bd.round(rounding).movePointRight(PRECISION_DIGITS);
        int intval=roundedScaled.intValue();
        this.address=(type.ordinal()<<16|(intval&0xffff));
        setSpecial(true);
    }
    
    /** Sets the Phidgets spatial event fields from the raw address spatialDataValue.
     * 
     */
    public void reconstructDataFromRawAddress(){
        int typeOrd=(address>>>16)&3;
        SpatialDataType[] types=SpatialDataType.values();
        this.spatialDataType=types[typeOrd];
        int intVal=(address&0xFFFF);
        BigDecimal bd=new BigDecimal(intVal);
        this.spatialDataValue=bd.movePointLeft(PRECISION_DIGITS).floatValue();
    }

    /** Copies event fields from another one.
     * 
     * @param src if the type is PhidgetsSpatialEvent then fields are copied.
     */
    @Override
    public void copyFrom(BasicEvent src) {
        super.copyFrom(src);
        if(src instanceof PhidgetsSpatialEvent){
            PhidgetsSpatialEvent from=(PhidgetsSpatialEvent)src;
            this.spatialDataValue=from.spatialDataValue;
            this.spatialDataType=from.spatialDataType;
        }
    }

    @Override
    public String toString() {
        return "PhidgetsSpatialEvent{" +"timestamp="+ timestamp + "spatialDataValue=" + spatialDataValue + ", spatialDataType=" + spatialDataType + '}';
    }

    
}
