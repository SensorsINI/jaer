/*
 * TestKalman.java
 *
 * Created on November 5, 2012, 12:18 PM
 *
 * Test Kalman Filter used with PCA Tracking
 */

package ch.unizh.ini.jaer.projects.poseestimation;

import com.sun.opengl.util.GLUT;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.util.*;
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
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.*;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.Matrix;


/**
 *
 * @author Haza
 */
@Description("PCA Tracking with Kalman Filter")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class TestKalman extends EventFilter2D implements FrameAnnotater, Observer {
   
    // PCA Object used as observation for Kalman Filter
    private TestPCA pca;
    private KalmanStructure kalman;
    
    private float xObs;
    private float yObs;
    private float xPrev;
    private float yPrev;
    
    
    // Drawing Points - Mean and Old Mean using Kalman Filter
    Point2D.Float ptKalmanMean = new Point2D.Float();     
    Point2D.Float ptKalmanMeanOld = new Point2D.Float();  // previous mean

    // Constructor
    public TestKalman(AEChip chip) {
        super(chip);
        this.chip=chip;
        chip.addObserver(this);
        pca = new TestPCA(chip);
        setEnclosedFilter(pca);
        initFilter();
        resetFilter();
    }
    
    // Called on creation
    @Override
    public void initFilter() {
        kalman = new KalmanStructure();
        xObs = pca.xMean;
        yObs = pca.yMean;
        xPrev = pca.xMean;
        yPrev = pca.yMean;
    }

    // Called when filter is reset
    @Override
    public void resetFilter() {
        kalman = new KalmanStructure();
        xObs = pca.xMean;
        yObs = pca.yMean;
        xPrev = pca.xMean;
        yPrev = pca.yMean;
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

        xPrev = kalman.x[0];
        yPrev = kalman.x[2];
        xObs = pca.xMean;
        yObs = pca.yMean;
        kalman.predict();
        kalman.update();
        //System.out.println(pca.xMean + " " + pca.yMean + " // " +  kalman.x[0] + " " + kalman.x[2]);
        ptKalmanMeanOld = ptKalmanMean;
        ptKalmanMean.x = kalman.x[0]; 
        ptKalmanMean.y = kalman.x[2]; 
        return in;
    }

    // TODO Comments
    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (!isAnnotationEnabled()) return;
        GL gl = drawable.getGL();
        if (gl == null) return;

        // Draw Line from ptMeanOld to ptMean
        //gl.glPushMatrix();
        //gl.glLineWidth(6f);
        //gl.glBegin(GL.GL_LINES);
        //gl.glColor3f(.25f,.75f,0.75f);
        //gl.glVertex2f(ptKalmanMeanOld.x, ptKalmanMeanOld.y);
        //gl.glVertex2f(ptKalmanMean.x, ptKalmanMean.y);
        //gl.glEnd();
        //gl.glPopMatrix();

        gl.glPushMatrix();
        gl.glPointSize(20f);
        gl.glColor3f(.25f,.75f,0.75f);
        gl.glBegin(GL.GL_POINTS);
        gl.glVertex2f(ptKalmanMean.x, ptKalmanMean.y);
        gl.glEnd();
        gl.glPopMatrix();
    }

    /**
     * Kalman Data Structure
     */
    final public class KalmanStructure {
        
        // TODO Problem Statement
        
        // Variables
        private int dimState = 4;           // Dimension of state vector
        private int dimMeasurement = 2;     // Dimension of measurement matrix
        private float processVariance;      // Variance by which to multiply process noise matrix
        private float measurementVariance;  // Variance by which to multiply measurement noise matrix
        
        // Matrices
        private float[][] A;    // State Transition x(t-1) -> x(t)
        //private float[][] B;    // Input Control - unused
        private float[][] Q;    // Process Noise Variance
        private float[][] H;    // Observation
        private float[][] R;    // Observation Noise Variance
        private float[][] Pp;   // Error covariance (without observation)
        private float[][] P;    // Error covariance
        
        // Vectors
        float[] x;      // Current state
        private float[] xp;     // Predicted state (without observation)
        private float[] z;      // Observation
        //private float[] u;      // Input control - unused
        private float[] y;      // Error
        
        // Time
        private float dt;       // Timestep = ts - t0 from PCA

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
            //B = new float[dimState][1];
            H = new float[dimMeasurement][dimState];
            R = new float[dimMeasurement][dimMeasurement];
            Pp = new float[dimState][dimState];
            P = new float[dimState][dimState];
            xp = new float[dimState];
            x = new float[dimState];
            z = new float[dimMeasurement];
            //u = new float[dimState];
            y = new float[dimMeasurement];
            
            measurementVariance = 1;                    // TODO
            processVariance = 8;  // TODO
            
            Matrix.identity(A);
            Matrix.zero(Q);
            
            H[0][0] = 1; H[0][1] = 0; H[0][2] = 0;H[0][3] = 0;
            H[1][0] = 0; H[1][1] = 0; H[1][2] = 1;H[1][3] = 0;
            
            Matrix.zero(R);
            R[0][0] = measurementVariance; R[1][1] = measurementVariance;
            
            Matrix.identity(P);
            
            x[0] = xPrev; // Data from PCA
            x[1] = 0; 
            x[2] = yPrev;
            x[3] = 0; 

            z[0] = xObs; // the observation
            z[1] = yObs;

        }
        
        /**
         * Prediction of KalmanFilter 
         */
        void predict() {
            // Since dt changes everytime, we need to update this value every time we do the filter
            dt = pca.dt * 1.e-6f;
            
            A[0][1]= dt; A[2][3]= dt; 
            
            Q[0][0] = (float)Math.pow(dt,4)/4;  Q[0][1] = (float)Math.pow(dt,3)/2;
            Q[1][0] = Q[0][1];                  Q[1][1] = (float)Math.pow(dt,2);
            System.out.println("Q:"); Matrix.print(Q);
            Q = Matrix.multMatrix(Q, processVariance);
            
            // Predicted state vector without observation
            // xp = A*x + B*u ... We ignore the user input .. Maybe add later?
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
            z[0] = xObs; 
            z[1] = yObs;
            
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
    
}