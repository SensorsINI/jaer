package ch.unizh.ini.jaer.projects.poseestimation;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;

@Description("Class that outputs SpatialEvents when saved from VOR sensor (Phidgets). Used to record retina events and spatial data together for playback later")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SaveSpatialEventFile extends EventFilter2D implements Observer {
    
    // Sensor Outputs gotten from DVS128 Class
    private double[] acceleration = new double[3], 
            gyro = new double[3], 
            compass = new double[3];        // Sensor values 
    private double[] prevAcceleration = new double[3], 
            prevGyro = new double[3], 
            prevCompass = new double[3];    // Previous sensor values 
    private boolean init = false;           // Indicated whether sensor values have been initialized 
    
    private SpatialEvent spatialEvent = 
            new SpatialEvent();             // Holds actual SpatialEvent to be written to out

    /**
     * Constructor
     * @param chip Called with AEChip properties
     */
    public SaveSpatialEventFile(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
    } // END CONSTRUCTOR
    
    /**
     * Called on creation
     */    
    @Override
    public void initFilter() {

    } // END METHOD

    /**
     * Called on filter reset
     */    
    @Override
    public void resetFilter() {

    } // END METHOD
    
    /**
     * Called when objects being observed change and send a message
     * @param o Object that has changed
     * @param arg Message object has sent about change
     */    
    @Override
    public void update(Observable o, Object arg) {
        initFilter();
    } // END METHOD
    
    /**
     * Receives Packets of information and returns output packet with sensor information if available
     * Output packet consists of both Polarity Events and Spatial Events
     * Spatial Events are much less frequent than Polarity Events so only output them if their value has changed
     * NOTE :   Data packet is collected, and only then is it transmitted to this method. 
     *          Sensor data is collected while data packet is being processed 
     *          so there is an offset between time of sensor data collection and time of event 
     *          which would be roughly equivalent to time difference between occurrence of first event and when that data package is received.
     *          However, sensor data packet gets updated much slower than event data so this effect is negligible and ignored
     * @param in Input events, can be null or empty.
     * @return output stream of retina events and spatial events
     */
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        // Check for empty packet
        if (in.getSize() == 0) 
            return in;
        // Check that filtering is in fact enabled
        if (!filterEnabled) 
            return in;
        // If necessary, pre filter input packet 
        if (enclosedFilter!=null) 
            in=enclosedFilter.filterPacket(in);
        // Check that DVS128 chip is selected, otherwise return unfiltered events
        if (chip.getClass() != DVS128Phidget.class) 
            return in;
        
        // Set output package out contents from class (inherited from EventFilter2D) to be SpatialEvents (or its subclasses (PolarityEvent))
        checkOutputPacketEventType(SpatialEvent.class);
        // Pre allocated Output Event Iterator used to set final out package (of SpatialEvent class)
        OutputEventIterator outItr = out.outputIterator();
        
        // Event Iterator - Write events and sensor data to out, also do necessary processing
        for (BasicEvent o : in) {
            // If we have the DVS chip with sensor data, then write it out when there is an update
            if (chip.getClass() == DVS128Phidget.class) {
                acceleration = ((DVS128Phidget) chip).getAcceleration().clone();
                gyro = ((DVS128Phidget) chip).getGyro().clone();
                compass = ((DVS128Phidget) chip).getCompass().clone();
                // If there is a new (different) SpatialEvent to write out, then write it to out
                if ( ( Arrays.equals(prevAcceleration, acceleration) == false ) || 
                        ( Arrays.equals(prevGyro, gyro) == false ) ||
                        ( Arrays.equals(prevCompass, compass) == false ) ) {
                    // Update previous measurements
                    prevAcceleration = acceleration.clone();
                    prevGyro = gyro.clone();
                    prevCompass = compass.clone();
                    // Reuse spatialEvent - update spatialEvent contents
                    // Then write to out 
                    // Accelerometer
                    spatialEvent.setData(o.timestamp, acceleration[0], SpatialEvent.Spatial.AccelX);
                    outItr.nextOutput().copyFrom(spatialEvent);
                    spatialEvent.setData(o.timestamp, acceleration[1], SpatialEvent.Spatial.AccelY);
                    outItr.nextOutput().copyFrom(spatialEvent);
                    spatialEvent.setData(o.timestamp, acceleration[2], SpatialEvent.Spatial.AccelZ);
                    outItr.nextOutput().copyFrom(spatialEvent);
                    // Gyro
                    spatialEvent.setData(o.timestamp, gyro[0], SpatialEvent.Spatial.GyroX);
                    outItr.nextOutput().copyFrom(spatialEvent);
                    spatialEvent.setData(o.timestamp, gyro[1], SpatialEvent.Spatial.GyroY);
                    outItr.nextOutput().copyFrom(spatialEvent);
                    spatialEvent.setData(o.timestamp, gyro[2], SpatialEvent.Spatial.GyroZ);
                    outItr.nextOutput().copyFrom(spatialEvent);
                    // Compass
//                    spatialEvent.setData(o.timestamp, compass[0], SpatialEvent.Spatial.CompassX);
//                    outItr.nextOutput().copyFrom(spatialEvent);
//                    spatialEvent.setData(o.timestamp, compass[1], SpatialEvent.Spatial.CompassY);
//                    outItr.nextOutput().copyFrom(spatialEvent);
//                    spatialEvent.setData(o.timestamp, compass[2], SpatialEvent.Spatial.CompassZ);
//                    outItr.nextOutput().copyFrom(spatialEvent);
                } // END IF - Sensor Value Compare
            } // END IF - Chip Check
            // Write out individual DVS events to out - calls SpatialEvent method copyFrom() which redirects to copyFrom of PolarityEvent
            outItr.nextOutput().copyFrom(o);
//            System.out.println("Event Data Update");
        } // END FOR - Event iterator
        return out;
    } // END METHOD
} // END CLASS