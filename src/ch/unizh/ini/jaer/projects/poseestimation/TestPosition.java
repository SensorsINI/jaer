/**
 * TestPosition.java
 * Test class for VOR Sensor Integration to find position in space
 * 
 * Created on November 16, 2012, 3:37 PM
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
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;


@Description("Pose Estimation using OptigalGyro and VOR Sensor information")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class TestPosition extends TestVOR implements Observer, FrameAnnotater {
   
    // Controls
    protected int varControl = getPrefs().getInt("TestPosition.varControl", 1);
    {
        setPropertyTooltip("varControl", "varControl description");
    }

    // VOR Handlers
    SpatialPhidget spatial = null;
    SpatialDataEvent spatialData = null;
    private int samplingDataRateVOR = 400; // Can't just be any number .. Not sure why
    
    // VOR Outputs
    private int ts; // Timestamp
    private double[] acceleration, gyro, compass = new double[3];

    // Calculated values
    
    // Drawing Points
    GLU glu = new GLU();
    Point2D.Float ptVar = new Point2D.Float(); 

    /**
     * Constructor
     * @param chip Called with AEChip properties
     */
    public TestPosition(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
    
        // Create sensor device variable
        try {
            spatial = new SpatialPhidget();
        } catch (PhidgetException e) {
            log.log(Level.WARNING, "{0}: gyro will not be available", e.toString());
        }

        // Creates listener for when device is plugged in 
        spatial.addAttachListener(new AttachListener() {

            // Log device info and set device sampling rate
            @Override
            public void attached(AttachEvent ae) {
                log.log(Level.INFO, "attachment of {0}", ae);
                try {
                    ((SpatialPhidget) ae.getSource()).setDataRate(samplingDataRateVOR); 
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

            // Write incoming data to variables
            @Override
            public void data(SpatialDataEvent sde) {
                if (sde.getData().length == 0) {
                    log.warning("empty data");
                    return;
                }
                acceleration = sde.getData()[0].getAcceleration();
                gyro = sde.getData()[0].getAngularRate();
                compass = sde.getData()[0].getMagneticField();
                ts = sde.getData()[0].getTimeMicroSeconds();
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
    synchronized public void resetFilter() {
    
    }
    
    /**
     * Called on changes in the chip
     * @param o 
     * @param arg 
     */    
    @Override
    public void update(Observable o, Object arg) {

    }

    /**
     * Receives Packets of information and passes it onto processing
     * @param in Input events can be null or empty.
     * @return The filtered events to be rendered
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
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
        
        // Output Iterator
        OutputEventIterator outItr = out.outputIterator();
        // Event Iterator
        for (Object e : in) {
            BasicEvent i = (BasicEvent) e;
            BasicEvent o = (BasicEvent) outItr.nextOutput();
            o.copyFrom(i);
        }
        // Process events
        process(in);

        return out;
    }

    /** 
     * Processes packet information
     * @param in Input event packet
     * @return Filtered event packet 
     */
    synchronized private void process(EventPacket ae) {
        // Event Iterator
//        for (Object e : ae) {
//            BasicEvent i = (BasicEvent) e; 
//        }
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

//        // Set camera.
//        // Change to projection matrix.
//        gl.glMatrixMode(GL.GL_PROJECTION);
//        gl.glLoadIdentity();
//
//        // Perspective.
//        float widthHeightRatio = 1;
//        glu.gluPerspective(45, widthHeightRatio, 1, 1000);
//        glu.gluLookAt(0, 0, 30, 0, 0, 0, 0, 1, 0);
//
//        // Change back to model view matrix.
//        gl.glMatrixMode(GL.GL_MODELVIEW);
//        gl.glLoadIdentity();
//
//        // Prepare light parameters.
//        float SHINE_ALL_DIRECTIONS = 1;
//        float[] lightPos = {-30, 0, 0, SHINE_ALL_DIRECTIONS};
//        float[] lightColorAmbient = {0.2f, 0.2f, 0.2f, 1f};
//        float[] lightColorSpecular = {0.8f, 0.8f, 0.8f, 1f};
//
//        // Set light parameters.
//        gl.glLightfv(GL.GL_LIGHT1, GL.GL_POSITION, lightPos, 0);
//        gl.glLightfv(GL.GL_LIGHT1, GL.GL_AMBIENT, lightColorAmbient, 0);
//        gl.glLightfv(GL.GL_LIGHT1, GL.GL_SPECULAR, lightColorSpecular, 0);
//
//        // Enable lighting in GL.
//        gl.glEnable(GL.GL_LIGHT1);
//        gl.glEnable(GL.GL_LIGHTING);
//
//        // Set material properties.
//        float[] rgba = {0.3f, 0.5f, 1f};
//        gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT, rgba, 0);
//        gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR, rgba, 0);
//        gl.glMaterialf(GL.GL_FRONT, GL.GL_SHININESS, 0.5f);
//
//        // Draw sphere (possible styles: FILL, LINE, POINT).
//        GLUquadric arrow = glu.gluNewQuadric();
//        glu.gluQuadricDrawStyle(arrow, GLU.GLU_FILL);
//        glu.gluQuadricNormals(arrow, GLU.GLU_FLAT);
//        glu.gluQuadricOrientation(arrow, GLU.GLU_OUTSIDE);
//        final float baseRadius = 0.2f;
//        final float topRadius = 0f;
//        float magnitude = 10f;
//        final int slices = 16;
//        final int stacks = 16;
//        glu.gluCylinder(arrow, baseRadius, topRadius, magnitude, slices, stacks);
////        gl.glRotatef((float)gyro[0], 1, 0, 0);
//        gl.glRotatef(45f, 45, 45, 0);
////        glu.gluDeleteQuadric(arrow);
    
//        final int font = GLUT.BITMAP_HELVETICA_18;
//        gl.glColor3f(1, 1, 1);
//        gl.glRasterPos3f(108, 123, 0);
//        // Accelerometer x, y, z info
//        chip.getCanvas().getGlut().glutBitmapString(font, String.format("a_x=%+.2f", acceleration[0]));
//        gl.glRasterPos3f(108, 118, 0);
//        chip.getCanvas().getGlut().glutBitmapString(font, String.format("a_y=%+.2f", acceleration[1]));
//        gl.glRasterPos3f(108, 113, 0);
//        chip.getCanvas().getGlut().glutBitmapString(font, String.format("a_z=%+.2f", acceleration[2]));
//        // Gyroscope 
//        gl.glRasterPos3f(108, 103, 0);
//        chip.getCanvas().getGlut().glutBitmapString(font, String.format("g_x=%+.2f", gyro[0]));
//        gl.glRasterPos3f(108, 98, 0);
//        chip.getCanvas().getGlut().glutBitmapString(font, String.format("g_y=%+.2f", gyro[1]));
//        gl.glRasterPos3f(108, 93, 0);
//        chip.getCanvas().getGlut().glutBitmapString(font, String.format("g_z=%+.2f", gyro[2]));
//        // Magnet
//        gl.glRasterPos3f(108, 83, 0);
//        chip.getCanvas().getGlut().glutBitmapString(font, String.format("c_x=%+.2f", compass[0]));
//        gl.glRasterPos3f(108, 78, 0);
//        chip.getCanvas().getGlut().glutBitmapString(font, String.format("c_y=%+.2f", compass[1]));
//        gl.glRasterPos3f(108, 73, 0);
//        chip.getCanvas().getGlut().glutBitmapString(font, String.format("c_z=%+.2f", compass[2]));

        // Save old state.
        gl.glPushMatrix();

        // Compute satellite position.
        float satelliteAngle = 50;
        final float distance = 5.000f;
        final float x = (float) Math.sin(Math.toRadians(satelliteAngle)) * distance;
        final float y = (float) Math.cos(Math.toRadians(satelliteAngle)) * distance;
        final float z = 0;
        gl.glTranslatef(x, y, z);
        gl.glRotatef(satelliteAngle, 0, 0, -1);
        gl.glRotatef(45f, 0, 1, 0);

        // Set silver color, and disable texturing.
        gl.glDisable(GL.GL_TEXTURE_2D);
        float[] ambiColor = {0.3f, 0.3f, 0.3f, 1f};
        float[] specColor = {0.8f, 0.8f, 0.8f, 1f};
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT, ambiColor, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR, specColor, 0);
        gl.glMaterialf(GL.GL_FRONT, GL.GL_SHININESS, 90f);

        // Draw satellite body.
        final int slices = 10;
        final int stacks = 10;
        final int cylinderSlices = 10;
        final float cylinderRadius = .5f;
        final float cylinderHeight = 1f;
        GLUquadric body = glu.gluNewQuadric();
        glu.gluQuadricTexture(body, false);
        glu.gluQuadricDrawStyle(body, GLU.GLU_FILL);
        glu.gluQuadricNormals(body, GLU.GLU_FLAT);
        glu.gluQuadricOrientation(body, GLU.GLU_OUTSIDE);
        gl.glTranslatef(0, 0, -cylinderHeight / 2);
        glu.gluDisk(body, 0, cylinderRadius, cylinderSlices, 2);
        glu.gluCylinder(body, cylinderRadius, cylinderRadius, cylinderHeight, slices, stacks);
        gl.glTranslatef(0, 0, cylinderHeight);
        glu.gluDisk(body, 0, cylinderRadius, cylinderSlices, 2);
        glu.gluDeleteQuadric(body);
        gl.glTranslatef(0, 0, -cylinderHeight / 2);
    }

    /** 
     * Sample Method
     * @param var var
     * @return var
     */
    protected float sampleMethod(float var) {
        return var;
    }
    
    /**
     * Getter for varControl
     * @return varControl 
     */
    public int getVarControl() {
        return this.varControl;
    }

    /**
     * Setter for integration time window
     * @see #getVarControl
     * @param varControl varControl
     */
    public void setVarControl(final int varControl) {
        getPrefs().putInt("TestPosition.varControl", varControl);
        getSupport().firePropertyChange("varControl", this.varControl, varControl);
        this.varControl = varControl;
    }

    public int getMinVarControl() {
        return 1;
    }

    public int getMaxVarControl() {
        return 10;
    }

}