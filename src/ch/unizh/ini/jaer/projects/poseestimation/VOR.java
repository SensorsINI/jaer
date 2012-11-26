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
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

@Description("Base Class to get and use VOR Sensor data")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class VOR extends EventFilter2D implements FrameAnnotater, Observer {
   
    // Controls
    protected int samplingRateMs = getInt("samplingRateMs", 128);
    {
        setPropertyTooltip("samplingRateMs", "Set Sampling Rate (in ms) for VOR Sensor. Must be either 4 or a multiple of 8 under 1000");
    }

    protected int tauMs = getInt("tauMs", 100);
    {
        setPropertyTooltip("tauMs", "Set time constant (in ms) for complimentary filter");
    }

    // VOR Handlers
    SpatialPhidget spatial = null;
    
    // VOR Outputs
    private int ts;                     // Sensor Timestamp
    double[] acceleration, 
            gyro, 
            compass = new double[3];    // Sensor Values 
    private boolean biasInit = false;   // defines whether sensor bias / offset has been initialized
    double[] biasAcceleration, 
            biasGyro, 
            biasCompass = new double[3];// Zero/Bias/Offset Sensor Values 
    
    // Complimentary Filter Variables
    private int t0;                 // Reference Event Timestamp - used as initial point for calculating when new sensor data is available
    private boolean t0Init = false; // defines whether t0 has been initialized, so it doesn't get reset with every new input packet
    private double dt;              // Time difference (in seconds) indicating difference between t0 and last event before needing to update transformation
                                    // Should be close to samplingRateMs
    double alpha;                   // Mixing factor related to tau defining how gyro and accelerometer data are fused
                                    
    // Outputs
    // In degrees
    double angleRoll = 0;   
    double anglePitch = 0;  
    double angleYaw = 0;    
    // Units?
    double distanceX = 0;   
    double distanceY = 0;  
    double distanceZ = 0;    
    
    // Drawing Points
    float originX;  // Center point for drawing cube 
    float originY;
    float radiusX;  // Radius of cube
    float radiusY; 
    float radiusZ; 
    float rectX;    // Rectangle Radius 
    float rectY; 
    
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
        rectX = (float)(chip.getSizeX()) / 2;
        rectY = (float)(chip.getSizeY()) / 2;
        radiusX = (float)(chip.getSizeX()) / 5;
        radiusY = (float)(chip.getSizeY()) / 5;
        radiusZ = (float)(chip.getSizeY()) / 5;

    }

    /**
     * Called on filter reset
     */    
    @Override
    public void resetFilter() {
        // Reset Zero/Offset/Bias values for sensor data only if they have already been set
        if (biasInit == true) {
            setBias();
        }
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

        // Update transformation values only after sensor sampling rate has passed
        // Use event timestamps to find out when this time has passed
        // For fist run, initialize t0 to reference time - so that if doesn't get reset with every input package
        if (t0Init == false) {
            t0 = in.getFirstTimestamp();
            t0Init = true;
        }
        // Event Iterator
        // When enough time has passed to get new sensor reading, 
        // Update transformation and reset t0
        // Remember to use same time units
        for (BasicEvent o : in) {
            if ((o.timestamp - t0)*1e3 >= samplingRateMs) {
                dt = (o.timestamp - t0)*1e-6; // dt is in seconds
                updateAngle();
//                updateDistance();
                t0 = o.timestamp;
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
        GLUT glut = chip.getCanvas().getGlut();
        gl.glColor3f(1, 1, 1);
        gl.glRasterPos3f(108, 123, 0);
        // Accelerometer x, y, z info
        glut.glutBitmapString(font, String.format("a_x=%+.2f", acceleration[0]));
        gl.glRasterPos3f(108, 118, 0);
        glut.glutBitmapString(font, String.format("a_y=%+.2f", acceleration[1]));
        gl.glRasterPos3f(108, 113, 0);
        glut.glutBitmapString(font, String.format("a_z=%+.2f", acceleration[2]));
        // Gyroscope 
        gl.glRasterPos3f(108, 103, 0);
        glut.glutBitmapString(font, String.format("g_x=%+.2f", gyro[0]));
        gl.glRasterPos3f(108, 98, 0);
        glut.glutBitmapString(font, String.format("g_y=%+.2f", gyro[1]));
        gl.glRasterPos3f(108, 93, 0);
        glut.glutBitmapString(font, String.format("g_z=%+.2f", gyro[2]));
        // Magnet
        gl.glRasterPos3f(108, 83, 0);
        glut.glutBitmapString(font, String.format("c_x=%+.2f", compass[0]));
        gl.glRasterPos3f(108, 78, 0);
        glut.glutBitmapString(font, String.format("c_y=%+.2f", compass[1]));
        gl.glRasterPos3f(108, 73, 0);
        glut.glutBitmapString(font, String.format("c_z=%+.2f", compass[2]));

        // Draw Box
        // Fix Transformation Matrix 
        gl.glPushMatrix();
        gl.glTranslatef(originX, originY, 0);
        gl.glRotatef((float)(angleRoll * 180 / Math.PI), 0, 0, 1);
        gl.glLineWidth(2f);
        gl.glColor3f(1, 0, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
        // Draw rectangle around transform
        gl.glVertex2f(-rectX, -rectY);
        gl.glVertex2f(rectX, -rectY);
        gl.glVertex2f(rectX, rectY);
        gl.glVertex2f(-rectX, rectY);
        gl.glEnd();
        gl.glPopMatrix();

        // Draw Cube
        // Fix Transformation Matrix 
        gl.glPushMatrix();
        gl.glTranslated(originX, originY, 0);
//        gl.glTranslated((float) originX + (float) distanceX, (float) originY + (float) distanceY, (float) 0 + (float) distanceZ);
        gl.glRotatef((float)(angleRoll * 180 / Math.PI), 0, 0, 1);
        gl.glRotatef((float)(angleYaw * 180 / Math.PI), 1, 0, 0);
        gl.glRotatef((float)(anglePitch * 180 / Math.PI), 0, 1, 0);
        gl.glLineWidth(2f);
        // Draw cube around transform
        // Side Face
        gl.glColor3f(.5f, .5f, 1);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3f(-radiusX, -radiusY, radiusZ);
        gl.glVertex3f(radiusX, -radiusY, radiusZ);
        gl.glVertex3f(radiusX, radiusY, radiusZ);
        gl.glVertex3f(-radiusX, radiusY, radiusZ);
        gl.glEnd();
        // Side face
        gl.glColor3f(.5f, .5f, 1);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3f(-radiusX, -radiusY, -radiusZ);
        gl.glVertex3f(radiusX, -radiusY, -radiusZ);
        gl.glVertex3f(radiusX, radiusY, -radiusZ);
        gl.glVertex3f(-radiusX, radiusY, -radiusZ);
        gl.glEnd();
        // Back face
        gl.glColor3f(.5f, .5f, 1);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3f(radiusX, -radiusY, -radiusZ);
        gl.glVertex3f(radiusX, -radiusY, radiusZ);
        gl.glVertex3f(radiusX, radiusY, radiusZ);
        gl.glVertex3f(radiusX, radiusY, -radiusZ);
        gl.glEnd();
        // Front face
        gl.glLineWidth(3f);
        gl.glColor3f(0, 0, 1);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3f(-radiusX, -radiusY, -radiusZ);
        gl.glVertex3f(-radiusX, -radiusY, radiusZ);
        gl.glVertex3f(-radiusX, radiusY, radiusZ);
        gl.glVertex3f(-radiusX, radiusY, -radiusZ);
        gl.glEnd();
        gl.glPopMatrix();
    
//        gl.glPushMatrix();
//        gl.glPointSize(5f);
//        gl.glColor3f(.25f,.75f,0.75f);
//        gl.glBegin(GL.GL_POINTS);
//        gl.glVertex3f((float) originX + (float) distanceX, (float) originY + (float) distanceY, (float) 0 + (float) distanceZ);
//        gl.glEnd();
//        gl.glPopMatrix();

    
    }

    /**
     * Computes transform using current gyro outputs based on timestamp supplied
     * and returns a TransformAtTime object. Should be called by update in
     * enclosing processor.
     *
     * @param timestamp the timestamp in us.
     * @return the transform object representing the camera rotation
     */
    synchronized public void updateAngle() {
        
        double rollFromAccel = Math.acos(acceleration[1]-biasAcceleration[1]);        
        double rollFromGyro = -(gyro[2]-biasGyro[2]) * Math.PI / 180.0 * dt;
        double pitchFromAccel = Math.acos(acceleration[2]-biasAcceleration[2]);        
        double pitchFromGyro = -(gyro[1]-biasGyro[1]) * Math.PI / 180.0 * dt;
        double yawFromGyro = -(gyro[0]-biasGyro[0]) * Math.PI / 180.0 * dt;
        
        double tau = (double)tauMs/1000.0;
        alpha = tau / (tau + dt);

        if (Double.isNaN(angleRoll)) {
            angleRoll = rollFromAccel;
        } else {
            angleRoll = alpha * (angleRoll + rollFromGyro) + (1 - alpha) * rollFromAccel;
        }

        if (Double.isNaN(anglePitch)) {
            anglePitch = rollFromAccel;
        } else {
            anglePitch = alpha * (anglePitch + pitchFromGyro) + (1 - alpha) * pitchFromAccel;
        }

        angleYaw += yawFromGyro;
        
    }    

    /**
     * Computes transform using current gyro outputs based on timestamp supplied
     * and returns a TransformAtTime object. Should be called by update in
     * enclosing processor.
     *
     * @param timestamp the timestamp in us.
     * @return the transform object representing the camera rotation
     */
    synchronized public void updateDistance() {
        
        double xFromAccel = 0.5 * (acceleration[1]-biasAcceleration[1]) * dt * dt;        
//        double xFromGyro = -(gyro[1]-biasGyro[1]) * Math.PI / 180.0 * dt;
        double yFromAccel = 0.5 * (acceleration[2]-biasAcceleration[2]) * dt * dt;        
//        double yFromGyro = -(gyro[2]-biasGyro[2]) * Math.PI / 180.0 * dt;
        double zFromAccel = 0.5 * (acceleration[0]-biasAcceleration[0]) * dt * dt;        
//        double zFromGyro = -(gyro[0]-biasGyro[0]) * Math.PI / 180.0 * dt;
        
        double tau = (double)tauMs/1000.0;
        alpha = tau / (tau + dt);

        distanceX += xFromAccel;
        distanceY += yFromAccel;
        distanceZ += zFromAccel;
    }    

    /** 
     * Setter for reseting current gyro measurements as 'zero'
     * Hold for 2 seconds
     */
    synchronized public void setBias()  {
        biasAcceleration[0] = acceleration[0];
        biasAcceleration[1] = acceleration[1];
        biasAcceleration[2] = acceleration[2];
        biasGyro[0] = gyro[0];
        biasGyro[1] = gyro[1];
        biasGyro[2] = gyro[2];
        biasCompass[0] = compass[0];
        biasCompass[1] = compass[1];
        biasCompass[2] = compass[2];
        angleYaw = 0;
        distanceX = 0;
        distanceY = 0;
        distanceZ = 0;
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
    public void setSamplingRateMs(int samplingRateMs) {
        int old=this.samplingRateMs;
        int correctSamplingRateMs = 4;
        if (samplingRateMs > 6 ) {
            correctSamplingRateMs = samplingRateMs / 8 * 8;
        }
            
       putInt("samplingRateMs", correctSamplingRateMs);
        this.samplingRateMs = correctSamplingRateMs;
        getSupport().firePropertyChange("samplingRateMs", old, correctSamplingRateMs);
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
        putInt("tauMs", tauMs);
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