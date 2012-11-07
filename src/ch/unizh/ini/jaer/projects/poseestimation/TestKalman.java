/*
 * TestKalman.java
 *
 * Created on November 5, 2012, 12:18 PM
 *
 * Test Kalman Filter used with PCA Tracking
 */

package ch.unizh.ini.jaer.projects.poseestimation;

import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.Matrix;


/**
 *
 * @author Haza
 */
@Description("PCA Tracking with Kalman Filter")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class TestKalman extends EventFilter2D implements FrameAnnotater, Observer {
   
    // Controls
    protected int measurementSTD = getPrefs().getInt("TestKalman.measurementSTD", 3);
    {
        setPropertyTooltip("measurementSTD", "Measurement Standard Deviation");
    }

    protected int processSTD = getPrefs().getInt("TestKalman.processSTD", 100);
    {
        setPropertyTooltip("processSTD", "Process Standard Deviation");
    }

    // PCA Object used as observation for Kalman Filter
    private TestPCA pca;
    // Kalman Structure used to track mean of PCA
    private KalmanStructure kalmanMean;
    
    // Kalman dt
    private float dt;
    // Kalman Measurement / Observation comes from PCA Mean 
    private float xMeasure;
    private float yMeasure;
    // Kalman corrected output values for previous time step
    private float xPrev;
    private float yPrev;
    
    // Drawing Points - Corrected Mean using Kalman Filter
    Point2D.Float ptKalmanMean = new Point2D.Float(); 
    Point2D.Float ptKalmanMeanVelocity = new Point2D.Float(); 

    // Constructor
    public TestKalman(AEChip chip) {
        super(chip);
        this.chip=chip;
        chip.addObserver(this);
        pca = new TestPCA(chip);
        initFilter();
        resetFilter();
    }
    
    // Called on creation
    @Override
    public void initFilter() {
        setEnclosedFilter(pca);
        // Reset Kalman matrices
        xMeasure = pca.xMean;
        yMeasure = pca.yMean;
        xPrev = pca.xMean;
        yPrev = pca.yMean;
        dt = pca.dt * 1.e-6f;
        kalmanMean = new KalmanStructure();
    }

    // Called when filter is reset
    @Override
    public void resetFilter() {
        xMeasure = pca.xMean;
        yMeasure = pca.yMean;
        xPrev = pca.xMean;
        yPrev = pca.yMean;
        dt = pca.dt * 1.e-6f;
        kalmanMean = new KalmanStructure();
    }
    
    // Called whenever chip changes
    @Override
    public void update(Observable o, Object arg) {
        initFilter();
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

        // FIX TIMING - Check value of dt ... Is most likely wrong
        // Update Kalman Filter every time an Event Packet comes in by using latest timestamp (might need fixing?)
        // Update dt
        dt = pca.dt * 1.e-6f;
        // Store previous Kalman outputs
        xPrev = kalmanMean.x[0];
        yPrev = kalmanMean.x[3];
        // Set measurement / observation to current Mean data
        xMeasure = pca.xMean;
        yMeasure = pca.yMean;
        // Do Kalman Filtering
        kalmanMean.predict();
        kalmanMean.update();

        // Update Values for drawing in canvas frame (in Annotate) 
        ptKalmanMean.x = kalmanMean.x[0]; 
        ptKalmanMean.y = kalmanMean.x[3];
        ptKalmanMeanVelocity.x = kalmanMean.x[1]; 
        ptKalmanMeanVelocity.y = kalmanMean.x[4];

        // Return input as output
        return in;
    }

    // Annotation or drawing function
    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (!isAnnotationEnabled()) return;
        GL gl = drawable.getGL();
        if (gl == null) return;

        // Draw point indicating Kalman Position Output
        gl.glPushMatrix();
        gl.glPointSize(20f);
        gl.glColor3f(.25f,.75f,0.75f);
        gl.glBegin(GL.GL_POINTS);
        gl.glVertex2f(ptKalmanMean.x, ptKalmanMean.y);
        gl.glEnd();
        gl.glPopMatrix();
        
        // Draw line indicating Kalman velocity
        gl.glPushMatrix();
        gl.glLineWidth(6f);
        gl.glBegin(GL.GL_LINES);
        gl.glColor3f(0f,1f,0f);
        gl.glVertex2f(ptKalmanMean.x, ptKalmanMean.y);
        //gl.glVertex2f(ptKalmanMean.x + ptKalmanMeanVelocity.x, ptKalmanMean.y + ptKalmanMeanVelocity.y);
        gl.glEnd();
        gl.glPopMatrix();
        
        
    }

    /**
     * Kalman Data Structure
     */
    final public class KalmanStructure {
        
        // TODO Problem Statement
        
        // Variables
        private int dimState = 6;           // Dimension of state vector - position, velocity, and acceleration in the x and y direction
        private int dimMeasurement = 2;     // Dimension of measurement matrix - x and y measurements
        
        // Matrices
        private float[][] A;    // State Transition x(t-1) -> x(t)
        //private float[][] B;    // Input Control - unused
        private float[][] Q;    // Process Noise Variance
        private float[][] H;    // Measurement
        private float[][] R;    // Measurement Noise Variance
        private float[][] Pp;   // Error covariance (without observation)
        private float[][] P;    // Error covariance
        
        // Vectors
        float[] x;              // Current state
        private float[] xp;     // Predicted state (without observation)
        private float[] z;      // Observation
        //private float[] u;    // Input control - unused
        private float[] y;      // Error
        
        // Time
//        private float t;       // Timestep = dt = ts - t0 from PCA

        /**
        * Data Structure for Kalman Filtering
        */
        KalmanStructure(){
            initData();
        }

        // Initialize all variables
        private void initData(){
            A = new float[dimState][dimState];
            Q = new float[dimState][dimState];
            //B = new float[dimState][dimMeasurement];
            H = new float[dimMeasurement][dimState];
            R = new float[dimMeasurement][dimMeasurement];
            Pp = new float[dimState][dimState];
            P = new float[dimState][dimState];
            xp = new float[dimState];
            x = new float[dimState];
            z = new float[dimMeasurement];
            //u = new float[dimMeasurement];
            y = new float[dimMeasurement];

            Matrix.identity(A);

            Matrix.zero(Q);
            
            Matrix.zero(H);
            H[0][0] = 1; 
            H[1][3] = 1;
            
            Matrix.zero(R);
            R[0][0] = measurementSTD * measurementSTD; 
            R[1][1] = measurementSTD * measurementSTD;
            
            Matrix.identity(P);
            
            // Initial state
            // State vector x from previous time step
            x[0] = xMeasure; 
            x[1] = 0; 
            x[2] = 0;
            x[3] = yMeasure; 
            x[4] = 0; 
            x[5] = 0; 

            // Observation / Measurement from PCA Mean
            z[0] = xMeasure;
            z[1] = yMeasure;
            
        }
        
        /**
         * Prediction of KalmanFilter 
         */
        void predict() {
            
            // In case this value has changed
            R[0][0] = measurementSTD*measurementSTD; R[1][1] = measurementSTD*measurementSTD;
            
            // Since dt changes everytime, we need to update this value every time we do the filter
            
            // Update with accelerations
            A[0][4]= dt*dt/2; 
            A[1][5]= dt*dt/2;
            // Update with velocities
            A[0][2]= dt; 
            A[1][3]= dt;
            A[2][4]= dt;
            A[3][5]= dt;

            // Update with covariances of acceleration
            Q[0][0] = dt*dt*dt*dt/4;  
            Q[1][1] = dt*dt*dt*dt/4;  
            // Update with covariances of acceleration and velocity
            Q[0][2] = dt*dt*dt/2;  
            Q[1][3] = dt*dt*dt/2;  
            Q[2][0] = dt*dt*dt/2;  
            Q[3][1] = dt*dt*dt/2;  
            // Update with covariances of acceleration and position
            Q[0][4] = dt*dt/2;  
            Q[1][5] = dt*dt/2;  
            Q[4][0] = dt*dt/2;  
            Q[5][1] = dt*dt/2;  
            // Update with covariances of velocity
            Q[2][2] = dt*dt;  
            Q[3][3] = dt*dt;  
            // Update with covariances of velocity and position
            Q[2][4] = dt;  
            Q[3][5] = dt;  
            Q[4][2] = dt;  
            Q[5][3] = dt;  
            // Update with covariances of position
            Q[4][4] = 1;  
            Q[5][5] = 1;  
            // Scale by process variance
            Q = Matrix.multMatrix(Q, processSTD*processSTD);
            //System.out.println("Q:"); Matrix.print(Q);

            // Predicted state vector without observation
            // xp = A*x + B*u ... But we have no user input
            xp =  Matrix.multMatrix(A,x);
            //System.out.println("xp:"); Matrix.print(xp);
            
            // Predicted Covariance Error Matrix without observation
            // Pp = F*P*F' + Q
            Pp = Matrix.addMatrix( Matrix.multMatrix( Matrix.multMatrix(A,P) , Matrix.transposeMatrix(A) ) , Q);
            //System.out.println("Pp:"); Matrix.print(Pp);
        }
        
        /**
         * Update of Kalman Filter
         */
        void update() {
            // Observation of measurement is mean of PCA
            z[0] = xMeasure; 
            z[1] = yMeasure;
            
            // Residual - Difference between observation (z) and prediction (H*xp)
            // y = z - H*xp
            Matrix.subtract( z , Matrix.multMatrix(H,xp) , y );
            
            // S = H*Pp*H'+R
            float[][] S = new float[dimMeasurement][dimMeasurement];
            float[][] STemp1 = new float[dimMeasurement][dimState];
            float[][] STemp2 = new float[dimMeasurement][dimMeasurement];
            Matrix.multiply( H , Pp , STemp1 );
            Matrix.multiply( STemp1 , Matrix.transposeMatrix(H) , STemp2 ); 
            Matrix.add( STemp2 , R , S );
            
            // Kalman Gain minimizing Error Covariance Matrix
            // K = Pp*H'*inv(S)
            float[][] K = new float[dimState][dimMeasurement];
            float[][] KTemp = new float[dimState][dimMeasurement];
            Matrix.multiply( Pp , Matrix.transposeMatrix(H) , KTemp );
            Matrix.invert(S);
            Matrix.multiply( KTemp , S , K );
            
            // Updated Error Covariance Matrix
            // P = (I-K*H)*Pp
            float[][] I = new float[dimState][dimState];
            float[][] PTemp = new float[dimState][dimState];
            Matrix.identity(I);
            Matrix.multiply( K , H , P );
            Matrix.subtract( I , P , PTemp );
            Matrix.multiply( PTemp , Pp , P );
            
            // Final State Vector
            // x = xp + K*y
            float[] xTemp = new float[dimState];
            Matrix.multiply( K , y , xTemp );
            Matrix.add( xp , xTemp , x);
        }
    };
    
    /**
     * Getter for measurement variance
     * @return Measurement Variance 
     */
    public int getMeasurementSTD() {
        return this.measurementSTD;
    }

    /**
     * Setter for integration time window
     * @see #getMeasurementSTD
     * @param measurementSTD Measurement Standard Deviation
     */
    public void setMeasurementSTD(final int measurementSTD) {
        getPrefs().putInt("TestKalman.measurementSTD", measurementSTD);
        getSupport().firePropertyChange("measurementSTD", this.measurementSTD, measurementSTD);
        this.measurementSTD = measurementSTD;
    }

    public int getMinMeasurementSTD() {
        return 1;
    }

    public int getMaxMeasurementSTD() {
        return 1000;
    }

    /**
     * Getter for process standard deviation
     * @return Process Standard Deviation 
     */
    public int getProcessSTD() {
        return this.processSTD;
    }

    /**
     * Setter for process standard deviation
     * @see #getProcessSTD
     * @param processSTD Process Standard Deviation
     */
    public void setProcessSTD(final int processSTD) {
        getPrefs().putInt("TestKalman.processSTD", processSTD);
        getSupport().firePropertyChange("processSTD", this.processSTD, processSTD);
        this.processSTD = processSTD;
    }

    public int getMinProcessSTD() {
        return 1;
    }

    public int getMaxProcessSTD() {
        return 1000;
    }

}