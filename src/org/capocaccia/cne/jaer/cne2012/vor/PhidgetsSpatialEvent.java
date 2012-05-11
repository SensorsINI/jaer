/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.capocaccia.cne.jaer.cne2012.vor;

import java.math.BigDecimal;
import java.math.MathContext;
import net.sf.jaer.event.PolarityEvent;

/**
 * Special events that allow including the Phidgets Spatial sensor events in a
 * data stream.
 *
 * @author tobi
 */
public class PhidgetsSpatialEvent extends PolarityEvent {

    public enum Type {Roll, Pitch, Yaw};
    
    /**
     * Roll pan and tilt rates are in deg/sec and represent, respectively
     * clockwise roll, rightwards pan, and upwards tilt for positive values.
     */
    public float value;

    public Type spatialDataType;
    
    @Override
    public boolean isSpecial() {
        return true;
    }
    
    private static final int PRECISION_DIGITS=3;
    private static final MathContext rounding=new MathContext(PRECISION_DIGITS);
    
    public PhidgetsSpatialEvent(int timestamp, float value, Type type) {
        this.timestamp=timestamp;
        this.value = value;
        this.spatialDataType = type;
        BigDecimal bd=new BigDecimal(value);
        BigDecimal roundedScaled=bd.round(rounding).movePointRight(PRECISION_DIGITS);
        int intval=roundedScaled.intValue();
        this.address=(type.ordinal()<<16|(intval&0xffff));
    }
    
    public void setFromAddress(){
        int typeOrd=(address>>>16)&3;
        int intVal=(address&0xFFFF);
        BigDecimal bd=new BigDecimal(intVal);
        this.value=bd.movePointLeft(PRECISION_DIGITS).floatValue();
    }

    
    
    
    
    
           
}
