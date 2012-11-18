/**
 * TestTemplate.java
 * Template for all classes
 * 
 * Created on November 14, 2012, 2:24 PM
 *
 * @author Haza
 */

package ch.unizh.ini.jaer.projects.poseestimation;

import com.phidgets.PhidgetException;
import com.phidgets.SpatialPhidget;
import com.phidgets.event.*;
import com.sun.opengl.util.GLUT;
import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import org.capocaccia.cne.jaer.cne2012.vor.PhidgetsSpatialEvent;

@Description("Base Class to get and use VOR Sensor data")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class VOR extends EventFilter2D implements FrameAnnotater, Observer {
   
    // Controls
    protected int samplingRateMs = getPrefs().getInt("VOR.samplingRateMs", 400);
    {
        setPropertyTooltip("samplingRateMs", "Set Sampling Rate for VOR Sensor. Must be either 4 or a multiple of 8 under 1000");
    }

    // VOR Handlers
    SpatialPhidget spatial = null;
    SpatialDataEvent spatialData = null;
    
    // VOR Outputs
    private int t0; // Reference Timestamp - last collected timestamp
    private int ts; // Timestamp
    private double[] acceleration, gyro, compass = new double[3];
    
    // Data Structure containing sensor outputs saved as PhidgetsSpatialEvent type
    // Size is 9 * 4 because of 3 sensors with 3 axes each and an additional variable for time stamp for each sensor reading
    private ArrayBlockingQueue<PhidgetsSpatialEvent> spatialDataQueue = new ArrayBlockingQueue<PhidgetsSpatialEvent>(9 * 4);

    // Drawing Points
    Point2D.Float ptVar = new Point2D.Float(); 

    /**
     * Constructor
     * @param chip Called with AEChip properties
     */
    public VOR(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();

        // Create sensor device variable
        try {
            spatial = new SpatialPhidget();
        } catch (PhidgetException e) {
            log.log(Level.WARNING, "{0}: sensor will not be available", e.toString());
        }

        // Creates listener for when device is plugged in 
        spatial.addAttachListener(new AttachListener() {

            // Log device info and set device sampling rate
            @Override
            public void attached(AttachEvent ae) {
                log.log(Level.INFO, "attachment of {0}", ae);
                try {
                    ((SpatialPhidget) ae.getSource()).setDataRate(samplingRateMs); 
                    StringBuilder sb = new StringBuilder();
                    sb.append("Serial: ").append(spatial.getSerialNumber()).append("\n");
                    sb.append("Accel Axes: ").append(spatial.getAccelerationAxisCount()).append("\n");
                    sb.append("Gyro Axes: ").append(spatial.getGyroAxisCount()).append("\n");
                    sb.append("Compass Axes: ").append(spatial.getCompassAxisCount()).append("\n");
                    sb.append("Data Rate: ").append(spatial.getDataRate()).append("\n");
                    log.info(sb.toString());
                } catch (PhidgetException pe) {
                    log.log(Level.WARNING, "Problem setting data rate: {0}", pe.toString());
                }
            }
        });
        
        // Creates listener for when device is unplugged
        spatial.addDetachListener(new DetachListener() {

            // Log detachment and reset filter
            @Override
            public void detached(DetachEvent ae) {
                log.log(Level.INFO, "detachment of {0}", ae);
                // do not close since then we will not get attachment events anymore
                resetFilter();
            }
        });
        
        // Creates listener for device errors
        spatial.addErrorListener(new ErrorListener() {

            // Log error
            @Override
            public void error(ErrorEvent ee) {
                log.warning(ee.toString());
            }
        });
        
        // Creates listener for incoming data 
        spatial.addSpatialDataListener(new SpatialDataListener() {

            // Write incoming data to variables and subsequently to data structure spatialDataQueue
            @Override
            public void data(SpatialDataEvent sde) {
                if (sde.getData().length == 0) {
                    log.warning("Empty data");
                    return;
                }
                acceleration = sde.getData()[0].getAcceleration();
                gyro = sde.getData()[0].getAngularRate();
                compass = sde.getData()[0].getMagneticField();
                ts = sde.getData()[0].getTimeSeconds() * 1000000 + sde.getData()[0].getTimeMicroSeconds();

                if (isFilterEnabled() && spatialDataQueue.remainingCapacity() >= 6) {
                    try {
                        spatialDataQueue.add(new PhidgetsSpatialEvent(ts, (float) acceleration[0], PhidgetsSpatialEvent.SpatialDataType.AccelUp));
                        spatialDataQueue.add(new PhidgetsSpatialEvent(ts, (float) acceleration[1], PhidgetsSpatialEvent.SpatialDataType.AccelRight));
                        spatialDataQueue.add(new PhidgetsSpatialEvent(ts, (float) acceleration[2], PhidgetsSpatialEvent.SpatialDataType.AccelTowards));
                        spatialDataQueue.add(new PhidgetsSpatialEvent(ts, (float) gyro[0], PhidgetsSpatialEvent.SpatialDataType.YawRight));
                        spatialDataQueue.add(new PhidgetsSpatialEvent(ts, (float) gyro[1], PhidgetsSpatialEvent.SpatialDataType.RollClockwise));
                        spatialDataQueue.add(new PhidgetsSpatialEvent(ts, (float) gyro[2], PhidgetsSpatialEvent.SpatialDataType.PitchUp));
//                        spatialDataQueue.add(new PhidgetsSpatialEvent(ts, (float) compass[0], PhidgetsSpatialEvent.SpatialDataType.CompassX));
//                        spatialDataQueue.add(new PhidgetsSpatialEvent(ts, (float) compass[1], PhidgetsSpatialEvent.SpatialDataType.CompassY));
//                        spatialDataQueue.add(new PhidgetsSpatialEvent(ts, (float) compass[2], PhidgetsSpatialEvent.SpatialDataType.CompassZ));
                    } catch (IllegalStateException e) {
                        log.log(Level.WARNING, "{0} Queue full, could not write PhidgetsSpatialEvent with ts=" + ts, e.toString());
                    }
                }
            }
        });

        // Open device anytime
        try {
            spatial.openAny(); // Starts thread to open any device that is plugged in (now or later)
        } catch (PhidgetException ex) {
            log.warning(ex.toString());
        }
    }
    
    /**
     * Called on creation
     */    
    @Override
    public void initFilter() {
    
    }

    /**
     * Called on filter reset
     */    
    @Override
    public void resetFilter() {
    
    }
    
    /**
     * Called on changes in the chip
     * @param o 
     * @param arg 
     */    
    @Override
    public void update(Observable o, Object arg) {
        initFilter();
    }

    /**
     * Receives Packets of information and passes it onto processing
     * @param in Input events can be null or empty.
     * @return The filtered events to be rendered
     */
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        // Check for empty packet
        if(in.getSize() == 0) 
            return in;
        // Check that filtering is in fact enabled
        if(!filterEnabled) 
            return in;
        // If necessary, pre filter input packet 
        if(enclosedFilter!=null) 
            in=enclosedFilter.filterPacket(in);

        // Checks that output package has correct data type
        checkOutputPacketEventType(PhidgetsSpatialEvent.class);
        
        // Create empty spatial event array and fill with incoming sensor data with their timestamp everytime an input camera event is registered
        // Output Iterator
        OutputEventIterator outItr = out.outputIterator();
        PhidgetsSpatialEvent spatialEvent = null;
        // Event Iterator
        for (BasicEvent o : in) {
            for (spatialEvent = spatialDataQueue.poll(); spatialEvent != null; spatialEvent = spatialDataQueue.poll()) {
                PhidgetsSpatialEvent oe = (PhidgetsSpatialEvent) outItr.nextOutput();
                oe.copyFrom(spatialEvent);
            }
            // Call listeners when enough time has passed for update
            maybeCallUpdateObservers(in, o.timestamp); 
        }
        return in;
    }

    /** 
     * Annotation or drawing method
     * @param drawable OpenGL Rendering Object
     */
    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (!isAnnotationEnabled()) 
            return;
        GL gl = drawable.getGL();
        if (gl == null) 
            return;

        
        final int font = GLUT.BITMAP_HELVETICA_18;
        gl.glColor3f(1, 1, 1);
        gl.glRasterPos3f(108, 123, 0);
        // Accelerometer x, y, z info
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("a_x=%+.2f", acceleration[0]));
        gl.glRasterPos3f(108, 118, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("a_y=%+.2f", acceleration[1]));
        gl.glRasterPos3f(108, 113, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("a_z=%+.2f", acceleration[2]));
        // Gyroscope 
        gl.glRasterPos3f(108, 103, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("g_x=%+.2f", gyro[0]));
        gl.glRasterPos3f(108, 98, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("g_y=%+.2f", gyro[1]));
        gl.glRasterPos3f(108, 93, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("g_z=%+.2f", gyro[2]));
        // Magnet
        gl.glRasterPos3f(108, 83, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("c_x=%+.2f", compass[0]));
        gl.glRasterPos3f(108, 78, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("c_y=%+.2f", compass[1]));
        gl.glRasterPos3f(108, 73, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("c_z=%+.2f", compass[2]));

    }

    /** 
     * Setter for reseting current gyro measurements as 'zero'
     * Hold for 2 seconds
     */
    public void setZeroGyro() throws PhidgetException {
        try {
            spatial.zeroGyro();
        } catch (PhidgetException ex) {
            log.warning(ex.toString());
        }
    }
    
    /**
     * Getter for samplingRateMs
     * @return samplingRateMs 
     */
    public int getSamplingRateMs() {
        return this.samplingRateMs;
    }

    /**
     * Setter for integration time window
     * @see #getSamplingRateMs
     * @param samplingRateMs Sampling Rate in ms
     */
    public void setSamplingRateMs(final int samplingRateMs) {
        int correctSamplingRateMs = 4;
        if (samplingRateMs > 6 ) {
            correctSamplingRateMs = samplingRateMs / 8 * 8;
        }
            
        getPrefs().putInt("VOR.samplingRateMs", correctSamplingRateMs);
        getSupport().firePropertyChange("samplingRateMs", this.samplingRateMs, correctSamplingRateMs);
        this.samplingRateMs = correctSamplingRateMs;
    }

    public int getMinSamplingRateMs() {
        return 4;
    }

    public int getMaxSamplingRateMs() {
        return 1000;
    }

}



