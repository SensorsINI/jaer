/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.poseestimation;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import com.phidgets.PhidgetException;
import com.phidgets.SpatialPhidget;
import com.phidgets.event.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.logging.Level;
import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer.PlayMode;

/**
 * DVS128 with Phidget Sensor (Accelerometer, Gyroscope, and Compass) attached to it
 * Creates a global field for the three sensor values (with their 3 axes) for filters to use
 * Otherwise sends out a normal PolarityEvent input stream
 * 
 * @author Haza
 */
@Description("DVS128 Dynamic Vision Sensor with Spatial Phidget (Accelerometer, Gyroscope, Compass)")
public class DVS128Phidget extends DVS128 {
 
    // VOR Object
    SpatialPhidget spatial = null;

    // VOR Outputs and Helper Variables
    private double[] accelerationFromPhidget = new double[3], 
            gyroFromPhidget = new double[3], 
            compassFromPhidget = new double[3];     // Sensor values 
    private int timeUsFromPhidget;
    private boolean init = false;                   // Indicates if sensor values have been initialized
    
    // VOR Variables
    private int samplingPeriodMs = 4;                // Sampling Rate - multiple of 8 under 1000 or 4 
    private static double accelDataMin = -1f;       // From User Manual 
    private static double gyroDataMin = -400f;      // From User Manual 
    private static double compassDataMin = -4.1f;   // From User Manual 
    // Outputs
    private double[] acceleration = new double[3],
            gyro = new double[3],
            compass = new double[3];                // Actual values that will be seen by filters
    private int timeUs;
    /** 
     * Constructor 
     */
    public DVS128Phidget() {
        setName("DVS128Phidget");
        setDefaultPreferencesFile("../../biasgenSettings/dvs128/DVS128Phidget.xml"); // Biasgen Settings
        setEventClass(PolarityEvent.class);
        setEventExtractor(new PhidgetExtractor(this));
        
        // Create sensor device variable
        try {
            spatial = new SpatialPhidget();
        } catch (PhidgetException e) {
            log.log(Level.WARNING, "{0}: sensor will not be available", e.toString());
        } // END TRY

        // Creates listener for when device is plugged in 
        spatial.addAttachListener(new AttachListener() {

            // Log device info and set device sampling rate
            @Override
            public void attached(AttachEvent ae) {
                log.log(Level.INFO, "attachment of {0}", ae);
                try {
                    ((SpatialPhidget) ae.getSource()).setDataRate(samplingPeriodMs); 
                    StringBuilder sb = new StringBuilder();
                    sb.append("Serial: ").append(spatial.getSerialNumber()).append("\n");
                    sb.append("Accel Axes: ").append(spatial.getAccelerationAxisCount()).append("\n");
                    sb.append("Gyro Axes: ").append(spatial.getGyroAxisCount()).append("\n");
                    sb.append("Compass Axes: ").append(spatial.getCompassAxisCount()).append("\n");
                    sb.append("Data Rate: ").append(spatial.getDataRate()).append("\n");
//                    spatial.zeroGyro();
                    log.info(sb.toString());
                } catch (PhidgetException pe) {
                    log.log(Level.WARNING, "Problem setting data rate: {0}", pe.toString());
                } // END TRY
            } // END METHOD
        }); // END SCOPE
        
        // Creates listener for when device is unplugged
        spatial.addDetachListener(new DetachListener() {

            // Log detachment and reset filter
            @Override
            public void detached(DetachEvent ae) {
                log.log(Level.INFO, "detachment of {0}", ae);
                // do not close since then we will not get attachment events anymore
            } // END METHOD
        }); // END SCOPE
        
        // Creates listener for device errors
        spatial.addErrorListener(new ErrorListener() {

            // Log error
            @Override
            public void error(ErrorEvent ee) {
                log.warning(ee.toString());
            } // END METHOD
        }); // END SCOPE
        
        // Creates listener for incoming data 
        spatial.addSpatialDataListener(new SpatialDataListener() {

            // Write incoming data to variables 
            @Override
            public void data(SpatialDataEvent sde) {
                if (sde.getData().length == 0) {
                    log.warning("Empty data");
                    return;
                }
                // Update sensor and timestamp values
                accelerationFromPhidget = sde.getData()[0].getAcceleration();
                gyroFromPhidget = sde.getData()[0].getAngularRate();
                compassFromPhidget = sde.getData()[0].getMagneticField();
                timeUsFromPhidget = sde.getData()[0].getTimeSeconds() * 1000000 + sde.getData()[0].getTimeMicroSeconds();

                // Run this only when sensor gets initialized, set final values to sensor values
                if (init == false) {
                    acceleration = accelerationFromPhidget.clone();
                    gyro = gyroFromPhidget.clone();
                    compass = compassFromPhidget.clone();
                    timeUs = timeUsFromPhidget;
                    
                    init = true;
                } // END IF
            } // END METHOD
        }); // END SCOPE

        // Open device anytime
        try {
            spatial.openAny(); // Starts thread to open any device that is plugged in (now or later)
        } catch (PhidgetException ex) {
            log.warning(ex.toString());
        } // END TRY
    }
    
    public void doZeroGyro() {
        try {
            zeroGyro();
        } catch (PhidgetException ex) {
            log.warning(ex.toString());
        }
    }

    // delegated methods
    public void zeroGyro() throws PhidgetException {
        if (spatial != null && spatial.isAttached()) {
            spatial.zeroGyro();
        }
    }

    /** 
     * Getter for acceleration
     * @return acceleration vector from either sensor or file
     */
    public double[] getAcceleration() {
        return acceleration;
    } // END METHOD
    
    /** 
     * Getter for gyro
     * @return gyro vector from either sensor or file
     */
    public double[] getGyro() {
        return gyro;
    } // END METHOD
    
    /** 
     * Getter for compass
     * @return compass vector from either sensor or file
     */
    public double[] getCompass() {
        return compass;
    } // END METHOD
    
    /** 
     * Getter for time
     * @return time in us 
     */
    public int getTimeUs() {
        return timeUs;
    } // END METHOD
    
    
    /** 
     * Extractor class deals with retina and sensor data
     * If extractor is called with data from file then extract sensor data from it and output just camera events
     * If extractor is called with live camera then update sensor data and output just camera events
     */
    public class PhidgetExtractor extends Extractor {

        // Re declare protected variables since this class is outside DVS128's package
        // Masks for data extraction from raw binary data
        final short XMASK = 0xfe, XSHIFT = 1, YMASK = 0x7f00, YSHIFT = 8;
        final int SPECIALSHIFT = 31;

        // Number of decimal places stored for each sensor variable
        // Made public so that whatever class saves Phidget data can see how many decimal places to round to
        public static final int PRECISION_DIGITS=2; 

        // Variable used to indicate what state AEViewer is in (namely getting packets from camera or data file)
        private PlayMode playMode;
        
        /** 
         * Constructor 
         */
        public PhidgetExtractor(DVS128Phidget chip) {
            super(chip);
            setXmask((short) 0x00fe);
            setXshift((byte) 1);
            setYmask((short) 0x7f00);
            setYshift((byte) 8);
            setTypemask((short) 1);
            setTypeshift((byte) 0);
            setFlipx(true);
            setFlipy(false);
            setFliptype(true);
        } // END CONSTRUCTOR

        /** 
         * Extracts meaning of raw events and updates sensor variables that filters can see
         * Handles two different scenarios: 
         * If Playback Mode is LIVE (from camera) then output is simply the input, and sensor values get updated from sensor
         * If Playback Mode is PLAYBACK (reading from logged file) then sensor values get extracted and only camera events are written out 
         * @param in The raw events which might include sensor data, can be null
         * @return out The raw camera events 
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            // Prepare out variable to accept only events of class Polarity Event
            if (out == null) {
                out = new EventPacket<PolarityEvent>(chip.getEventClass());
            } else {
                out.clear();
            } // END IF

            // If empty input packet just return empty output packet
            if (in == null) {
                return out;
            } // END IF
            
            // If chip object doesn't exist, then just return
            if (chip == null) {
                return out;
            } // END IF
            // Defines whether a file is being played back (PLAYBACK) or using camera events (LIVE)
            playMode = chip.getAeViewer().getPlayMode();
            
            int n = in.getNumEvents(); 
            out.systemModificationTimeNs = in.systemModificationTimeNs;

            // If subsampling is enabled and our input packet is too big (bigger than threshold = 50,000) then subsample until length is less than threshold (50,000)
            // Subsampling is disabled for now as it does not have code to accomodate sensor events
            int skipBy = 1;
            //if (isSubSamplingEnabled()) {
            //    while (n / skipBy > getSubsampleThresholdEventCount()) {
            //        skipBy++;
            //    } // END WHILE
            //} // END IF
            
            int sxm = sizeX - 1;
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            
            // Output Stream Iterator Reference
            OutputEventIterator outItr = out.outputIterator();
            // Input Event Stream Loop
            for (int i = 0; i < n; i += skipBy) { // Loses an event every packet if skipBy does not perfectly divide by n
                int addr = a[i]; 
                
                // Note: Use curly brackets for each case to make sure scope of variables is only within particular case statement
                switch (playMode) {
                    // On playback, extract spatial data or write out retina events
                    case PLAYBACK: {
                        if (getSpecial(addr) == true) {
                            setSpatialDataFromAddress(addr);
                        } else {
                            PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                            e.address = addr;
                            e.timestamp = timestamps[i];
                            e.setSpecial(false);
                            e.type = (byte) (1 - addr & 1);
                            e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                            e.x = (short) (sxm - ((short) ((addr & XMASK) >>> XSHIFT)));
                            e.y = (short) ((addr & YMASK) >>> YSHIFT);
                        } // END IF
                        break;
                    } // END SCOPE
                    // On live, update spatial data from VOR Sensor if necessary, and write out retina events 
                    case LIVE: {
                        // Write out spatial data first so we are never missing this information
                        // Note: Will probably be missing spatial information for first few events given that we don't know when Logging is being pressed
                        // Should be easily fixable.. Don't worry about it now
                        if ( ( Arrays.equals(accelerationFromPhidget, acceleration) == false ) || 
                                ( Arrays.equals(gyroFromPhidget, gyro) == false ) ||
                                ( Arrays.equals(compassFromPhidget, compass) == false ) ) {
                            // Update final sensor values from Phidget values
                            acceleration = accelerationFromPhidget.clone();
                            gyro = gyroFromPhidget.clone();
                            compass = compassFromPhidget.clone();
                            timeUs = timeUsFromPhidget;
                        } // END IF
                        PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                        e.address = addr;
                        e.timestamp = timestamps[i];
                        e.setSpecial(false);
                        e.type = (byte) (1 - addr & 1);
                        e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                        e.x = (short) (sxm - ((short) ((addr & XMASK) >>> XSHIFT)));
                        e.y = (short) ((addr & YMASK) >>> YSHIFT);
                        break;
                    } // END SCOPE
                    // Should not get here. If it does then just write out retina events
                    default: {
                        PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                        e.address = addr;
                        e.timestamp = timestamps[i];
                        e.setSpecial(false);
                        e.type = (byte) (1 - addr & 1);
                        e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                        e.x = (short) (sxm - ((short) ((addr & XMASK) >>> XSHIFT)));
                        e.y = (short) ((addr & YMASK) >>> YSHIFT);
                        break;
                    } // END SCOPE
                } // END CASE
            } // END FOR
            return out;
        } // END METHOD
        
        /** 
         * Sets the corresponding final sensor values from raw address
         * 32 bit Address recorded as follows
         * STTT TTTT TTTT TTTV VVVV VVVV VVVV VVVV
         * S indicates Special Bit - Flag indicating it's a Spatial Sensor Event (1) and not a DVS Event (0)
         * T indicates ordinal of Spatial Type 
         * V indicates Spatial Value 
         * T represents the ordinal of the Spatial Type
         * Spatial Value is recorded as 17 bit integer, to do so, double value is rounded to PRECISION_DIGITS,
         * shifted over to turn it into an integer and then offset by the minimum value 
         * so as to only deal with positive integers and not have to deal with 2's complements
         */
        public void setSpatialDataFromAddress(int address){
            // Recover spatialDataType
            // Get ordinal of Spatial type enum recorded in address (bits 18 to 31 - counting from 1 to 32)
            int typeOrd = address << 1 >>> 1 >>> 17; // Lose special bit, shift back, then shift away data stored in first 16 bits
            // Get Spatial data type from ordinal
            SpatialEvent.Spatial spatialDataType=SpatialEvent.Spatial.values()[typeOrd];
            // Recover spatialDataValue stored in first 17 bits
            int intVal=(address&0x1ffff);
            BigDecimal bd=new BigDecimal(intVal);
            double spatialDataValue=bd.movePointLeft(PRECISION_DIGITS).doubleValue();
            // Update final sensor values with correct variable name and index
            switch (spatialDataType) {
                case AccelX: case AccelY: case AccelZ: 
                    acceleration[typeOrd%3] = spatialDataValue + accelDataMin;
                    break;
                case GyroX: case GyroY: case GyroZ:
                    gyro[typeOrd%3] = spatialDataValue + gyroDataMin;
                    break;
                case CompassX: case CompassY: case CompassZ:
                    compass[typeOrd%3] = spatialDataValue + compassDataMin;
                    break;
                default:
                    break;
            } // END CASE
        } // END METHOD

        /** 
         * Gets boolean value of Special from an address
         * Special boolean is stored in last bit of address
         * @returns boolean indicating whether event is of special type 
         */
        public boolean getSpecial(int address) {
            return (address >>> SPECIALSHIFT == 1) ? true : false;
        }
    }
    
    
}