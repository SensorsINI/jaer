package ch.unizh.ini.jaer.projects.poseestimation;

import java.math.BigDecimal;
import java.math.MathContext;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/**
 * Special event stream mixing DVS and Phidget Sensor data
 *
 * @author haza
 */
public class SpatialEvent extends PolarityEvent {
    
    // Spatial datatype to define different inputs from sensor
    // Accel is measured in units of g (9.81 m/s/s) from -1.0 to 1.0
    // Gyro is measured in degrees / second - Roll, Pan, Tilt respectively from -400 to 400 
    // Compass is measured in Gauss units from -4.1 to 4.1
    public enum Spatial {AccelX, AccelY, AccelZ, GyroX, GyroY, GyroZ, CompassX, CompassY, CompassZ};

    // From User Manual - could also be gotten from Phidget method itself
    private static double accelDataMin = -1f;
    private static double gyroDataMin = -400f;
    private static double compassDataMin = -4.1f;
    
    private Spatial spatialDataType;
    private double spatialDataValue;
    
    // Fields used for rounding spatialDataValues
    // Be VERY careful when trying to change this - explained in setData
    private static final int PRECISION_DIGITS=2; 
    private static final MathContext rounding=new MathContext(PRECISION_DIGITS);

    /** 
     * Constructor 
     */
    public SpatialEvent() {

    }

    /** 
     * Constructor 
     * 
     * @param timestamp in ns, from event NOT from sensor
     * @param spatialDataValue sensor reading
     * @param type Spatial Type - Accelerometer, Gyroscope, or Compass and respective axis
     */
    public SpatialEvent(int timestamp, float value, Spatial type) {
        setData(timestamp, value, type);
    }
     
    /** 
     * Setter for the data fields and the raw address spatialDataValue from explicit values
     * 
     * @param timestamp in ns from coinciding event, NOT from sensor
     * @param spatialDataValue sensor reading
     * @param type Spatial Type 
     */
    public final void setData(int timestamp, double value, Spatial type) {
        this.timestamp=timestamp;
        spatialDataType = type;
        spatialDataValue = value;

        // Convert value from sensor into an unsigned integer to write into address 
        
        // Subtract minimum data value to offset sensor reading
        // Do this so we don't have to deal with negative numbers or two's complement
        // (To reconstruct just subtract again this value)
        switch (type) {
            case AccelX: case AccelY: case AccelZ:
                value -= accelDataMin;
                break;
            case GyroX: case GyroY: case GyroZ:
                value -= gyroDataMin;
                break;
            case CompassX: case CompassY: case CompassZ:
                value -= compassDataMin;
                break;
            default:
                break;
        }
        
        // Convert spatial value into an integer to write into address 
        // by rounding to PRECISION_DIGITS decimal places and then shifting that number of decimal places
        BigDecimal bd = new BigDecimal(value);
        BigDecimal roundedScaled=bd.round(rounding).movePointRight(PRECISION_DIGITS);
        // 32 bit Address recorded as follows
        // STTT TTTT TTTT TTTV VVVV VVVV VVVV VVVV
        // S indicates Special Bit - Flag indicating it's a Spatial Sensor Event (1) and not a DVS Event (0)
        // T indicates Spatial Type 
        // V indicates Spatial Value 
        // Needs to record values from MIN_SENSOR << PRECISION_DIGITS to MAX_SENSOR << PRECISION_DIGITS 
        // Uses 17 bits since needs to record values from -400<<2 to 400<<2 = 80001 different possible values
        int intValue = roundedScaled.intValue();
        address=(type.ordinal()<<17|(intValue&0x1ffff));
        setSpecial(true);
    }
    
    /** 
     * Sets the spatial event main fields from the raw address
     * Refer to setData to see how data in address field is stored
     */
    public void reconstructDataFromRawAddress(){
        // Recover spatialDataType
        // Get ordinal of Spatial type enum recorded in address (bits 18 to 31 - counting from 1 to 32)
        int typeOrd=address << 1 >>> 1 >>> 17; // Lose special bit, shift back, then shift away data stored in first 16 bits
        spatialDataType=Spatial.values()[typeOrd];
        // Recover spatialDataValue stored in first 17 bits
        int intVal=(address&0x1ffff);
        BigDecimal bd=new BigDecimal(intVal);
        spatialDataValue=bd.movePointLeft(PRECISION_DIGITS).floatValue();
        switch (spatialDataType) {
            case AccelX: case AccelY: case AccelZ: 
                spatialDataValue += accelDataMin;
                break;
            case GyroX: case GyroY: case GyroZ:
                spatialDataValue += gyroDataMin;
                break;
            case CompassX: case CompassY: case CompassZ:
                spatialDataValue += compassDataMin;
                break;
            default:
                break;
        }
    }

    /** 
     * Copies event data into class using copyFrom from super
     * Has to deal with both SpatialEvent and PolarityEvent
     * PolarityEvent case is already handled in Super
     * For SpatialEvent just copy additional fields into class from src
     * 
     * @param src SpatialEvent or PolarityEvent
     */
    @Override
    public void copyFrom(BasicEvent src) {
        super.copyFrom(src);
        if(src instanceof SpatialEvent) {
            SpatialEvent e = (SpatialEvent) src;
            spatialDataValue = e.spatialDataValue;
            spatialDataType = e.spatialDataType;
        }
    }

    /** 
     * This is a special event so it will return true
     */
    @Override
    public boolean isSpecial() {
        return true;
    }
    
    @Override
    public String toString() {
        return "PhidgetsSpatialEvent{" + "timestamp=" + timestamp + ", spatialDataValue=" + spatialDataValue + ", spatialDataType=" + spatialDataType + '}';
    }

}
