///*
// * To change this template, choose Tools | Templates
// * and open the template in the editor.
// */
//package ch.unizh.ini.jaer.projects.poseestimation;
//
///**
// *
// * @author Haza
// */
//public class MultipleLineDetector {
//    
//}
//
//
//
//
//
//
///**
// * TestTemplate.java
// * Template for all classes
// * 
// * Created on November 14, 2012, 2:24 PM
// *
// * @author Haza
// */
//
//package ch.unizh.ini.jaer.projects.poseestimation;
//
//import java.awt.geom.Point2D;
//import java.util.Observable;
//import java.util.Observer;
//import javax.media.opengl.GL;
//import javax.media.opengl.GLAutoDrawable;
//import net.sf.jaer.Description;
//import net.sf.jaer.DevelopmentStatus;
//import net.sf.jaer.chip.AEChip;
//import net.sf.jaer.event.BasicEvent;
//import net.sf.jaer.event.EventPacket;
//import net.sf.jaer.event.OutputEventIterator;
//import net.sf.jaer.eventprocessing.EventFilter2D;
//import net.sf.jaer.graphics.FrameAnnotater;
//
//@Description("Template")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
//public class TestTemplate extends EventFilter2D implements FrameAnnotater, Observer {
//   
//    // Controls
//    protected int varControl = getPrefs().getInt("TestTemplate.varControl", 1);
//    {
//        setPropertyTooltip("varControl", "varControl description");
//    }
//
//    // Object Variables
//    
//    // Internal variables
//    private float var = 0;
//    
//    // Drawing Points - var description
//    Point2D.Float ptVar = new Point2D.Float(); 
//
//    /**
//     * Constructor
//     * @param chip Called with AEChip properties
//     */
//    public TestTemplate(AEChip chip) {
//        super(chip);
//        chip.addObserver(this);
//        initFilter();
//    }
//    
//    /**
//     * Called on creation
//     */    
//    @Override
//    public void initFilter() {
//    }
//
//    /**
//     * Called on filter reset
//     */    
//    @Override
//    public void resetFilter() {
//    
//    }
//    
//    /**
//     * Called on changes in the chip
//     * @param o 
//     * @param arg 
//     */    
//    @Override
//    public void update(Observable o, Object arg) {
//        initFilter();
//    }
//
//    /**
//     * Receives Packets of information and passes it onto processing
//     * @param in Input events can be null or empty.
//     * @return The filtered events to be rendered
//     */
//    @Override
//    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
//        // Check for empty packet
//        if(in.getSize() == 0) 
//            return in;
//        // Check that filtering is in fact enabled
//        if(!filterEnabled) 
//            return in;
//        // If necessary, pre filter input packet 
//        if(enclosedFilter!=null) 
//            in=enclosedFilter.filterPacket(in);
//        // Checks that output package has correct data type
//        checkOutputPacketEventType(in);
//        
//        // Process events
//        out = process((EventPacket<BasicEvent>) in);
//        return out;
//    }
//
//    /** 
//     * Processes packet information
//     * @param in Input event packet
//     * @return Filtered event packet 
//     */
//    synchronized protected EventPacket<? extends BasicEvent> process(EventPacket<BasicEvent> in) {
//        // Output Iterator
//        OutputEventIterator outItr = out.outputIterator();
//        // Event Iterator
//        for (BasicEvent e : in) {
//            BasicEvent o = (BasicEvent) outItr.nextOutput();// Create new output event
//            o.copyFrom(e);                                  // set output event to input event
//        }
//        return out;
//    }    
//    
//    /** 
//     * Annotation or drawing methos
//     * @param drawable OpenGL Rendering Object
//     */
//    @Override
//    synchronized public void annotate(GLAutoDrawable drawable) {
//        if (!isAnnotationEnabled()) 
//            return;
//        GL gl = drawable.getGL();
//        if (gl == null) 
//            return;
//
//        // Draw point 
//        gl.glPushMatrix();
//        gl.glPointSize(6f);
//        gl.glColor3f(1f,1f,1f);
//        gl.glBegin(GL.GL_POINTS);
//        gl.glVertex2f(ptVar.x, ptVar.y);
//        gl.glEnd();
//        gl.glPopMatrix();
//        
//        // Draw line 
//        gl.glPushMatrix();
//        gl.glLineWidth(6f);
//        gl.glBegin(GL.GL_LINES);
//        gl.glColor3f(1f,1f,1f);
//        gl.glVertex2f(ptVar.x, ptVar.y);
//        gl.glVertex2f(ptVar.x * 2, ptVar.y * 2);
//        gl.glEnd();
//        gl.glPopMatrix();
//    }
//
//    /** 
//     * Sample Method
//     * @param var var
//     * @return var
//     */
//    protected float sampleMethod(float var) {
//        return var;
//    }
//    
//    /**
//     * Getter for varControl
//     * @return varControl 
//     */
//    public int getVarControl() {
//        return this.varControl;
//    }
//
//    /**
//     * Setter for integration time window
//     * @see #getVarControl
//     * @param varControl varControl
//     */
//    public void setVarControl(final int varControl) {
//        getPrefs().putInt("TestTemplate.varControl", varControl);
//        getSupport().firePropertyChange("varControl", this.varControl, varControl);
//        this.varControl = varControl;
//    }
//
//    public int getMinVarControl() {
//        return 1;
//    }
//
//    public int getMaxVarControl() {
//        return 10;
//    }
//
//}