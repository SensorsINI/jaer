package ch.unizh.ini.jaer.projects.poseestimation;

import java.math.BigDecimal;
import java.math.MathContext;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/**
 * Special events that allow including the Phidgets Spatial sensor events in a
 * data stream.
 *
 * @author tobi
 * @author haza
 */
public class SpatialEvent extends PolarityEvent {
    
    // Spatial datatype to define different inputs from sensor
    // Accel is measured in units of g (9.81 m/s/s)
    // Gyro is measured in degrees / second - Roll, Pan, Tilt respectively
    // Compass is measured in Gauss units
    public enum Spatial {AccelX, AccelY, AccelZ, GyroX, GyroY, GyroZ, CompassX, CompassY, CompassZ, Unknown};

    public Spatial spatialDataType;
    public float spatialDataValue=0;
    
    private static final int PRECISION_DIGITS=3;
    private static final MathContext rounding=new MathContext(PRECISION_DIGITS);

    /** 
     * Constructor 
     */
    public SpatialEvent() {
        spatialDataType=Spatial.Unknown;
    }

    /** 
     * Constructor 
     * 
     * @param timestamp in us, using AttachEvent as reference
     * @param spatialDataValue sensor reading
     * @param type Spatial Type - Accelerometer, Gyroscope, or Compass and respective axis
     */
    public SpatialEvent(int timestamp, float value, Spatial type) {
        this.timestamp=timestamp;
        this.spatialDataValue = value;
        this.spatialDataType = type;
        BigDecimal bd=new BigDecimal(value);
        BigDecimal roundedScaled=bd.round(rounding).movePointRight(PRECISION_DIGITS);
        int intval=roundedScaled.intValue();
        this.address=(type.ordinal()<<16|(intval&0xffff));
    }
     
    /** 
     * Setter for the data fields and the raw address spatialDataValue from explicit values
     * 
     * @param timestamp in us
     * @param spatialDataValue sensor reading
     * @param type Spatial Type 
     */
    public void setData(int timestamp, float value, Spatial type) {
        this.timestamp=timestamp;
        this.spatialDataValue = value;
        this.spatialDataType = type;
        BigDecimal bd=new BigDecimal(value);
        BigDecimal roundedScaled=bd.round(rounding).movePointRight(PRECISION_DIGITS);
        int intval=roundedScaled.intValue();
        this.address=(type.ordinal()<<16|(intval&0xffff));
        setSpecial(true);
    }
    
    /** 
     * Sets the Phidgets spatial event fields from the raw address spatialDataValue.
     */
    public void reconstructDataFromRawAddress(){
        int typeOrd=(address>>>16)&3;
        Spatial[] types=Spatial.values();
        this.spatialDataType=types[typeOrd];
        int intVal=(address&0xFFFF);
        BigDecimal bd=new BigDecimal(intVal);
        this.spatialDataValue=bd.movePointLeft(PRECISION_DIGITS).floatValue();
    }

    /** 
     * Copies event fields from another one.
     * @param src if the type is PhidgetsSpatialEvent then fields are copied.
     */
    @Override
    public void copyFrom(BasicEvent src) {
        super.copyFrom(src);
        if(src instanceof SpatialEvent){
            SpatialEvent from=(SpatialEvent)src;
            this.spatialDataValue=from.spatialDataValue;
            this.spatialDataType=from.spatialDataType;
        }
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
    
    @Override
    public String toString() {
        return "PhidgetsSpatialEvent{" + "timestamp=" + timestamp + ", spatialDataValue=" + spatialDataValue + ", spatialDataType=" + spatialDataType + '}';
    }

}
