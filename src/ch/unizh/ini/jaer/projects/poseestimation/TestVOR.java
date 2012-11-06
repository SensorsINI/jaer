/*
 * TestClusters.java
 *
 * Created on November 2, 2012, 1:32 PM
 *
 * Test Filter for VOR Sensor
 */

package ch.unizh.ini.jaer.projects.poseestimation;

import com.phidgets.PhidgetException;
import com.phidgets.SpatialEventData;
import com.phidgets.SpatialPhidget;
import com.phidgets.event.*;
import com.sun.opengl.util.GLUT;
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

/**
 *
 * @author Haza
 */
@Description("Test using VOR Sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class TestVOR extends EventFilter2D implements Observer, FrameAnnotater {

    // Controls
    protected int var = getPrefs().getInt("TestClusters.var", 1);
    {
        setPropertyTooltip("var", "var description");
    }

    // VOR
    SpatialPhidget spatial = null;
    SpatialDataEvent spatialData = null;
    private int samplingDataRateVOR = 400; // Can't just be any number .. Not sure why
    
    // Sensor Outputs
    private int ts; // Timestamp
    private double[] acceleration, gyro, compass = new double[3];

    // Drawing Points
    
    
    // Event Handlers
    short x, y;
    
    // Constructor
    public TestVOR(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
 
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
   
    // Annotation or drawing function
    @Override
    public void annotate (GLAutoDrawable drawable){
        if ( !isAnnotationEnabled() ){
            return;
        }
        GL gl = drawable.getGL();

        if ( gl == null ){
            return;
        }
        
        // Output sensor information to canvas screen
        final int font = GLUT.BITMAP_HELVETICA_18;
        gl.glColor3f(1, 1, 1);
        gl.glRasterPos3f(108, 123, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("a_x=%+.2f", acceleration[0]));
        gl.glRasterPos3f(108, 118, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("a_y=%+.2f", acceleration[1]));
        gl.glRasterPos3f(108, 113, 0);
        chip.getCanvas().getGlut().glutBitmapString(font, String.format("a_z=%+.2f", acceleration[2]));

        // Draw Box showing camera angle
        drawOrientation(gl, (float) acceleration[0], (float) acceleration[1], (float) acceleration[2], 50f, 50f);
    }

    protected void drawOrientation(GL gl, float ax, float ay, float az, float rx, float ry) {
        final float r2d = (float) (180 / Math.PI);
        float angle = 0;
        gl.glPushMatrix();
        gl.glTranslatef(128/2, 128/2, 0);
        gl.glLineWidth(6f);
        gl.glBegin(GL.GL_LINES);
        ry = (ax >= 1) ? 1f : (float) Math.asin(ax) * ry;
        rx = (ay >= 1) ? 1f : (float) Math.asin(ay) * rx;
        float edge1, edge2, edge3, edge4;
        
        gl.glPushMatrix();
        gl.glColor3f(0.1f,0.9f,0f);
        gl.glVertex2f(0f, 0f);
        gl.glVertex2f(rx, ry);
        gl.glEnd();
        gl.glPopMatrix();
    }
    /**
     * Filter Operation
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (!filterEnabled) {
            return in;
        }
        // Figure this out - Add a Low Pass Filter
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
        checkOutputPacketEventType(in);

        // Event Iterator
        OutputEventIterator outItr = out.outputIterator();
        for (Object e : in) {
            BasicEvent i = (BasicEvent) e;
            BasicEvent o = (BasicEvent) outItr.nextOutput();
            o.copyFrom(i);
        }
        return out;
    }

    /**
     * Getter for integration time window
     * @return Minimum time window before Mean and STD calculation in us
     */
    public int getVar() {
        return this.var;
    }

    /**
     * Setter for integration time window
     * @see #getDt
     * @param dt Minimum time window before Mean and STD calculation in us
     */
    public void setVar(final int var) {
        getPrefs().putInt("TestVOR.var", var);
        getSupport().firePropertyChange("var", this.var, var);
        this.var = var;
    }

    public int getMinVar() {
        return 0;
    }

    public int getMaxVar() {
        return 100000;
    }

    
    
    // Called when Reset button clicked
    @Override
    synchronized public void resetFilter() {

    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg != null && (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY)) {
            initFilter();
        }
    }

    // Called on creation
    @Override
    public void initFilter() {

    }
}










