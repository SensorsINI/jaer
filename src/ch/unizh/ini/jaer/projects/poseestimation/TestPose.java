/**
 * TestPose.java
 * Test class for basic pose estimation using Optical Gyro and VOR Sensor data
 * 
 * Created on November 14, 2012, 2:24 PM
 *
 * @author Haza
 */

package ch.unizh.ini.jaer.projects.poseestimation;

import com.phidgets.SpatialPhidget;
import com.phidgets.event.SpatialDataEvent;
import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;

@Description("Pose Estimation using OptigalGyro and VOR Sensor information")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class TestPose extends EventFilter2D implements Observer, FrameAnnotater {
   
    // Controls
    protected int varControl = getPrefs().getInt("TestPose.varControl", 1);
    {
        setPropertyTooltip("varControl", "varControl description");
    }

    // 
    private VOR vorSensor = null; // used when tracking features
    private EventPacket ffPacket = null;
    private FilterChain filterChain = null;

    // VOR Handlers
    SpatialPhidget spatial = null;
    SpatialDataEvent spatialData = null;
    
    // VOR Outputs
    private int ts; // Timestamp
    private double[] acceleration, gyro, compass = new double[3];

    // Complimentary Filter Variables
    float a;
    float tau = 0.01f;
    
    // Annotation Variables
    private int originX;
    private int originY;
    Point2D.Float ptVar = new Point2D.Float(); 

    int angle = 0;
    /**
     * Constructor
     * @param chip Called with AEChip properties
     */
    public TestPose(AEChip chip) {
        super(chip);
        chip.addObserver(this);
    
        filterChain = new FilterChain(chip);

        vorSensor = new VOR(chip);
        vorSensor.setAnnotationEnabled(true); 
        vorSensor.addObserver(this);
        filterChain.add(vorSensor);

        setEnclosedFilterChain(filterChain);

        initFilter();
    }
    
    /**
     * Called on creation
     */    
    @Override
    public void initFilter() {
        originX = chip.getSizeX() / 2;
        originY = chip.getSizeY() / 2;
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
//        initFilter();
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
        
        // Do I need this?
        getEnclosedFilterChain().filterPacket(in); // issues callbacks to us periodically via update based on 
        
        OutputEventIterator outItr = out.outputIterator();
        for (Object e : in) {
            BasicEvent i = (BasicEvent) e;
            BasicEvent o = (BasicEvent) outItr.nextOutput();
            o.copyFrom(i);
        }
        // Process events
//        process(in);
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

        calculateAngle();

        gl.glPushMatrix();
        gl.glTranslatef((float)chip.getSizeX()/2-1, (float)chip.getSizeY()/2-1, 0);
        gl.glRotatef((float)(angle * 180 / Math.PI), 0, 0, 1);
        gl.glLineWidth(2f);
        gl.glColor3f(1, 0, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
        // rectangle around transform
        
        float sx2 = 64; 
        float sy2 = 64; 
        gl.glVertex2f(-sx2, -sy2);
        gl.glVertex2f(sx2, -sy2);
        gl.glVertex2f(sx2, sy2);
        gl.glVertex2f(-sx2, sy2);
        gl.glEnd();
        gl.glPopMatrix();
        
    }

    /** 
     * Sample Method
     * @param var var
     * @return var
     */
    protected void calculateAngle() {
//        a = tau / (tau + dt);
//        angle = a * (angle +  + (1-a) * angle
        
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
        getPrefs().putInt("TestPose.varControl", varControl);
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





