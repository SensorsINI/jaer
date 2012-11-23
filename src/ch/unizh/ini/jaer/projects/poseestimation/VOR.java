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

@Description("Base Class to get and use VOR Sensor data")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class VOR extends EventFilter2D implements FrameAnnotater, Observer {
   
    // Controls
    protected int samplingRateMs = getPrefs().getInt("VOR.samplingRateMs", 128);
    {
        setPropertyTooltip("samplingRateMs", "Set Sampling Rate (in ms) for VOR Sensor. Must be either 4 or a multiple of 8 under 1000");
    }

    protected int tauMs = getPrefs().getInt("VOR.tauMs", 100);
    {
        setPropertyTooltip("tauMs", "Set time constant (in ms) for complimentary filter");
    }

    // VOR Handlers
    SpatialPhidget spatial = null;
    SpatialDataEvent spatialData = null;
    
    // VOR Outputs
    private int t0;                 // Reference Timestamp - last collected timestamp
    private boolean t0Init = false; // defines whether t0 has been initialized, so it doesn't get reset with every input packet
    private int ts;                 // Timestamp
    private double dt;                 // Timestamp
    double[] acceleration, gyro, compass = new double[3]; // Sensor Values 
    private boolean biasInit = false; // defines whether t0 has been initialized, so it doesn't get reset with every input packet
    double[] biasAcceleration, biasGyro, biasCompass = new double[3]; // Zero/Bias Sensor Values 
    
    // Complimentary Filter Variables
    double alpha;
    // Outputs
    double angle = 0;        // In Degrees
    double angleRoll = 0;    // In Degrees        
    double anglePitch = 0;   // In Degrees
    double angleYaw = 0;     // In Degrees
    
    // Drawing Points
    Point2D.Float ptVar = new Point2D.Float();
    float originX;
    float originY;
    float radiusX;
    float radiusY; 
    float cubeX = 10;
    float cubeY = 10; 
    float cubeZ = 10; 
    
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

            // Write incoming data to variables and subsequently collect data into structure spatialDataQueue
            @Override
            public void data(SpatialDataEvent sde) {
                if (sde.getData().length == 0) {
                    log.warning("Empty data");
                    return;
                }
                if (biasInit == false) {
                    biasAcceleration = sde.getData()[0].getAcceleration();
                    biasGyro = sde.getData()[0].getAngularRate();
                    biasCompass = sde.getData()[0].getMagneticField();
                    biasInit = true;
                } else {            
                    acceleration = sde.getData()[0].getAcceleration();
                    gyro = sde.getData()[0].getAngularRate();
                    compass = sde.getData()[0].getMagneticField();
                }
                ts = sde.getData()[0].getTimeSeconds() * 1000000 + sde.getData()[0].getTimeMicroSeconds();
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
        originX = (float)(chip.getSizeX() - 1) / 2;
        originY = (float)(chip.getSizeY() - 1) / 2;
        radiusX = (float)(chip.getSizeX()) / 2;
        radiusY = (float)(chip.getSizeY()) / 2;

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
     * Clean up for after filter is finalized
     */    
    @Override
    public synchronized void cleanup() {
        super.cleanup();
        try {
            spatial.close();
        } catch (PhidgetException ex) {
            log.warning(ex.toString());
        }
        spatial = null;
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
        checkOutputPacketEventType(in);
        
        // Update transformation values only after sensor sampling rate has passed
        // Use event timestamps to find out when this time has passed
        // Remember to use same time units
        // For fist run, initialize t0 to reference time 
        if (t0Init == false) {
            t0 = in.getFirstTimestamp();
            t0Init = true;
        }
        // Event Iterator
        for (BasicEvent o : in) {
            if (1000*(o.getTimestamp() - t0) >= getSamplingRateMs()) {
                dt = (o.getTimestamp() - t0)*1e-6;
                updateTransformation();
                t0 = o.getTimestamp();
            }
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

        // Draw Box
        // Fix Transformation Matrix 
        gl.glPushMatrix();
        gl.glTranslatef(originX, originY, 0);
        gl.glRotatef((float)(angle * 180 / Math.PI), 0, 0, 1);
        gl.glLineWidth(2f);
        gl.glColor3f(1, 0, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
        // Draw rectangle around transform
        gl.glVertex2f(-radiusX, -radiusY);
        gl.glVertex2f(radiusX, -radiusY);
        gl.glVertex2f(radiusX, radiusY);
        gl.glVertex2f(-radiusX, radiusY);
        gl.glEnd();
        gl.glPopMatrix();

        // Draw Cube
        // Fix Transformation Matrix 
        gl.glPushMatrix();
        gl.glTranslatef(originX, originY, 0);
        gl.glRotatef((float)(angle * 180 / Math.PI), 0, 0, 1);
        gl.glLineWidth(2f);
        gl.glColor3f(1, 0, 0);
        // Draw cube around transform
        // Front Face
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3f(-cubeX, -cubeY, 0);
        gl.glVertex3f(cubeX, -cubeY, 0);
        gl.glVertex3f(cubeX, cubeY, 0);
        gl.glVertex3f(-cubeX, cubeY, 0);
        gl.glEnd();
        // Back face
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3f(-cubeX, -cubeY, -cubeZ);
        gl.glVertex3f(cubeX, -cubeY, -cubeZ);
        gl.glVertex3f(cubeX, cubeY, -cubeZ);
        gl.glVertex3f(-cubeX, cubeY, -cubeZ);
        gl.glEnd();

        gl.glPopMatrix();
    
        
    
    }

    /**
     * Computes transform using current gyro outputs based on timestamp supplied
     * and returns a TransformAtTime object. Should be called by update in
     * enclosing processor.
     *
     * @param timestamp the timestamp in us.
     * @return the transform object representing the camera rotation
     */
    synchronized public void updateTransformation() {
        
        double angleFromAccel = Math.acos(acceleration[1]-biasAcceleration[1]);        
        double angleFromGyro = -(gyro[2]-biasGyro[2]) * Math.PI / 180.0 * dt;
        double tau = (double)tauMs/1000.0;
        alpha = tau / (tau + dt);
        //angle += angleFromGyro; 
        angle = alpha * (angle + angleFromGyro) + (1 - alpha) * angleFromAccel; 
        
    }    

//    /** 
//     * Setter for reseting current gyro measurements as 'zero'
//     * Hold for 2 seconds
//     */
//    synchronized public void setBias()  {
//        biasAcceleration[0] = acceleration[0];
//        biasAcceleration[1] = acceleration[1];
//        biasAcceleration[2] = acceleration[2];
//        biasGyro[0] = gyro[0];
//        biasGyro[1] = gyro[1];
//        biasGyro[2] = gyro[2];
//        biasCompass[0] = compass[0];
//        biasCompass[1] = compass[1];
//        biasCompass[2] = compass[2];
//    }
    
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

    /**
     * Getter for tauMs
     * @return tauMs 
     */
    public int getTauMs() {
        return this.tauMs;
    }

    /**
     * Setter for integration time window
     * @see #getTauMs
     * @param tauMs time constant in ms for complimentary filter
     */
    public void setTauMs(final int tauMs) {
        getPrefs().putInt("VOR.tauMs", tauMs);
        getSupport().firePropertyChange("tauMs", this.tauMs, tauMs);
        this.tauMs = tauMs;
    }

    public int getMinTauMs() {
        return 10;
    }

    public int getMaxTauMs() {
        return 2000;
    }
}