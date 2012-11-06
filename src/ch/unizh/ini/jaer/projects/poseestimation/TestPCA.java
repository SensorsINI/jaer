/*
 * TestPCA.java
 *
 * Created on October 22, 2012, 12:33 PM
 *
 * Test Filter that takes PCA of data input
 */

package ch.unizh.ini.jaer.projects.poseestimation;

import java.awt.Graphics2D;
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
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author Haza
 */
@Description("Finds mean of data and principal components with their magnitudes")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class TestPCA extends EventFilter2D implements Observer, FrameAnnotater {

    // Controls
    protected int dtMin = getPrefs().getInt("TestPCA.dtMin", 30000);
    {
        setPropertyTooltip("dtMin", "Minimum time window before calculations in us");
    }

    protected int eventCountThreshold = getPrefs().getInt("TestPCA.eventCountThreshold", 1000);
    {
        setPropertyTooltip("eventCountThreshold", "Minimum number of events before calculations");
    }

    // Counters
    int numEvents = 0;

    // Temporaries used to calculate Mean and Variance
    float xSum = 0f;    // x value sum accumulation
    float ySum = 0f;    // y value sum accumulation
    float xxSum = 0f;   // x*x value sum accumulation
    float yySum = 0f;   // y*y value sum accumulation
    float xySum = 0f;   // x*y value sum accumulation 

    // Means and Variances
    float xMean = 0f;
    float yMean = 0f;
    float xVar = 0f;
    float yVar = 0f;
    float xyVar = 0f;

    // Eigenvariables
    float eig1 = 0f;    // Eigenvalue
    float eig2 = 0f;    
    float eig1X = 0f;   // Eigenvector coordinates (unnormalized)
    float eig1Y = 0f;
    float eig2X = 0f;
    float eig2Y = 0f;
    float eig1Norm = 1f;// Eigenvalue magnitude (used for normalization) 
    float eig2Norm = 1f;
    float STD1 = 0f;    // Standard Deviation Magnitude
    float STD2 = 0f;
    
    // Timing
    int t0 = 0;         // initial time, used as reference for time comparison
    int ts = 0;         // timestamp
    int dt = ts - t0;   // Time Difference between final time and initial time
    
    // Drawing Points - Mean and STD drawn in Eigenvector direction
    Point2D.Float ptMean = new Point2D.Float();     
    Point2D.Float ptMeanOld = new Point2D.Float();  // previous mean
    Point2D.Float ptSTD1 = new Point2D.Float();
    Point2D.Float ptSTD2 = new Point2D.Float();
    
    // Event Handlers
    short x, y;
    
    // Constructor
    public TestPCA(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
    }

    // Called on creation
    @Override
    public void initFilter() {
        t0 = 0;
        xSum = 0;
        xxSum = 0;
        ySum = 0;
        yySum = 0;
        xySum = 0;
        numEvents = 0;
    }

    // Called when filter is reset
    @Override
    synchronized public void resetFilter() {
        t0 = 0;
        xSum = 0;
        xxSum = 0;
        ySum = 0;
        yySum = 0;
        xySum = 0;
        numEvents = 0;
    }

    // Called whenever chip changes
    @Override
    public void update(Observable o, Object arg) {
        if (arg != null && (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY)) {
            initFilter();
        }
    }

    // Annotation or drawing function
    @Override
    public void annotate (GLAutoDrawable drawable){
        if (!isAnnotationEnabled()) return;
        GL gl = drawable.getGL();
        if (gl == null) return;

        // Draw Line from ptMeanOld to ptMean
        gl.glPushMatrix();
        gl.glLineWidth(6f);
        gl.glBegin(GL.GL_LINES);
        gl.glColor3f(1f,0f,0f);
        gl.glVertex2f(ptMeanOld.x,ptMeanOld.y);
        gl.glVertex2f(ptMean.x,ptMean.y);
        gl.glEnd();
        gl.glPopMatrix();

        // Draw Lines indicating Standard Deviation
        // STD 1
        gl.glPushMatrix();
        gl.glLineWidth(6f);
        gl.glBegin(GL.GL_LINES);
        gl.glColor3f(0f,1f,0f);
        gl.glVertex2f(ptMean.x + ptSTD1.x, ptMean.y + ptSTD1.y);
        gl.glVertex2f(ptMean.x - ptSTD1.x, ptMean.y - ptSTD1.y);
        gl.glEnd();
        gl.glPopMatrix();
        // STD 2
        gl.glPushMatrix();
        gl.glLineWidth(6f);
        gl.glBegin(GL.GL_LINES);
        gl.glColor3f(0f,0f,1f);
        gl.glVertex2f(ptMean.x + ptSTD2.x, ptMean.y + ptSTD2.y);
        gl.glVertex2f(ptMean.x - ptSTD2.x, ptMean.y - ptSTD2.y);
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
        if(in==null) 
            return null;
        if(!filterEnabled) 
            return in;
        if(enclosedFilter!=null) 
            in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);

        // Event Iterator
        OutputEventIterator outItr = out.outputIterator();
        for (Object e : in) {
            BasicEvent i = (BasicEvent) e;
            ts = i.timestamp;
            x = i.x;
            y = i.y;
            if ((x < 0 || y < 0)) {
                continue;
            }
            
            if (ts - t0 > dtMin && numEvents >= eventCountThreshold) {
                // If enough time has passed and enough data has been collected
                // Calculate Mean and STD 
                calculate();
                
                // Update ptSTD for drawing in canvas frame (in Annotate)
                ptSTD1.setLocation((float) Math.sqrt(eig1) * eig1X, (float) Math.sqrt(eig1) * eig1Y);
                ptSTD2.setLocation((float) Math.sqrt(eig2) * eig2X, (float) Math.sqrt(eig2) * eig2Y);

                // Update ptMean for drawing in canvas frame (in Annotate)
                ptMean.setLocation(xMean, yMean);

                // Reset calculation values to current data - make sure not to lose this event
                dt = ts - t0;
                t0 = ts;
                xSum = x;
                xxSum = x*x;
                ySum = y;
                yySum = y*y;
                xySum = x*y;
                numEvents = 1;

            } else {
                // Else keep on collecting data
                xSum += x;
                xxSum += x*x;
                ySum += y;
                yySum += y*y;
                xySum += x*y;
                numEvents++;
            }            
            // Create a new output instance - and pass input to it
            BasicEvent o = (BasicEvent) outItr.nextOutput();
            o.copyFrom(i);
        }
        return out;
    }

    /**
     * Calculates Mean and STD given data set
     */
    void calculate() {
        // Store current data as old
        ptMeanOld.x = ptMean.x;
        ptMeanOld.y = ptMean.y;

        // Mean and Variance calculations
        xMean = xSum/numEvents;
        yMean = ySum/numEvents;
        xVar = (xxSum - xSum*xSum/numEvents)/numEvents;
        yVar = (yySum - ySum*ySum/numEvents)/numEvents; 
        xyVar = (xySum - xSum*ySum/numEvents)/numEvents; 

        // Find Eigenvalues
        // a b c from characteristic quadratic equation det(I*lambda-A) = 0 
        float a = 1;
        float b = -1 * (xVar + yVar);
        float c = xVar * yVar - xyVar * xyVar;
        eig1 = (float) ( -1*b + Math.sqrt(b*b - 4*a*c) ) / (2*a);
        eig2 = (float) ( -1*b - Math.sqrt(b*b - 4*a*c) ) / (2*a);

        // Get Eigenvectors with Magnitude STD
        eig1X = eig1 - yVar;
        eig1Y = xyVar;
        eig2X = eig2 - yVar;
        eig2Y = xyVar;
        eig1Norm = (float) Math.sqrt(eig1X*eig1X + eig1Y*eig1Y);
        eig2Norm = (float) Math.sqrt(eig2X*eig2X + eig2Y*eig2Y);
        eig1X = eig1X/eig1Norm;
        eig1Y = eig1Y/eig1Norm;
        eig2X = eig2X/eig2Norm;
        eig2Y = eig2Y/eig2Norm;
    }
    
    /**
     * Getter for integration time window
     * @return Minimum time window before Mean and STD calculation in us
     */
    public int getDtMin() {
        return this.dtMin;
    }

    /**
     * Setter for integration time window
     * @see #getDt
     * @param dt Minimum time window before Mean and STD calculation in us
     */
    public void setDtMin(final int dtMin) {
        getPrefs().putInt("TestPCA.dt", dtMin);
        getSupport().firePropertyChange("dtMin", this.dtMin, dtMin);
        this.dtMin = dtMin;
    }

    public int getMinDtMin() {
        return 1;
    }

    public int getMaxDtMin() {
        return 100000;
    }

    
    /**
     * Getter for integration event window
     * @return Minimum number of events before mean and STD are calculated
     */
    public int getEventCountThreshold() {
        return this.eventCountThreshold;
    }

    /**
     * Setter for integration event window
     * @see #getEventCountThreshold
     * @param eventCountThreshold Minimum number of events before mean and STD are calculated
     */
    public void setEventCountThreshold(final int eventCountThreshold) {
        getPrefs().putInt("TestPCA.eventCountThreshold", eventCountThreshold);
        getSupport().firePropertyChange("eventCountThreshold", this.eventCountThreshold, eventCountThreshold);
        this.eventCountThreshold = eventCountThreshold;
    }

    public int getMinEventCountThresold() {
        return 1;
    }

    public int getMaxEventCountThreshold() {
        return 100000;
    }
    

}










