package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.util.jama.Matrix;

/**
 * Draws individual optical flow vectors and computes global motion, 
 * rotation and expansion. Algorithm is based on the method presented in
 * R. Benosman, C. Clercq, X. Lagorce, S.-H. Ieng, and C. Bartolozzi, 
 * “Event-Based Visual Flow,” IEEE Transactions on Neural Networks and Learning Systems, 
 * vol. Early Access Online, 2013.
 * It uses the local properties of events' spatiotemporal space by fitting a plane 
 * to an incoming event's neighborhood on the surface of active events. 
 * The gradient of this local tangential plane corresponds to the inverse velocity.
 * @author rbodo
 */

@Description("Class for amplitude and orientation of local motion optical flow using surface of active events.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class LocalPlanesFlow extends AbstractMotionFlow {
    // Magnitude of accuracy from iterative estimation algorithm. 
    // In each iteration of the local plane-fitting, this threshold is compared 
    // to the distance of the most recent plane estimate to the previous.
    private float th1 = getFloat("th1",50e-3f); 
    private float eps;
    private boolean change;
    
    // When an event in the neighborhood is farther away from the local plane estimate 
    // than this threshold value, it is discarded from the data set.
    private float th2 = getFloat("th2",50e-6f); 
    
    // Threshold for flat planes (events that are wrongly interpreted as having
    // infinite velocity).
    private float th3 = getFloat("th3",5e-3f);
    
    private ArrayList<double[]> neighborhood;
    private final float[] planeParameters;
    private Matrix planeEstimate, planeEstimate_old, A;
    
    private float sx2, sy2, st2, sxy, sxt, syt, sxx, syy, stt;
    
    public enum PlaneEstimator {SingleFit, IterativeFit, LinearSavitzkyGolay, HomogeneousCoordinates};
    private PlaneEstimator planeEstimator;
    private boolean singleFit, iterativeFit, linearSavitzkyGolay, homogeneousCoordinates;
    
    // First timestamp of input packet.
    private int firstTs; 
    
    private String neighb;
    
    private double tmp;
    
    public LocalPlanesFlow(AEChip chip) {
        super(chip);
        planeParameters = new float[3];
        planeEstimate = new Matrix(4,1);
        planeEstimator = PlaneEstimator.IterativeFit;
        iterativeFit = true;
        numInputTypes = 2;
        resetFilter();
        setPropertyTooltip("Local Planes","th1","accuracy of iterative estimation");
        setPropertyTooltip("Local Planes","th2","threshold of discarding events too far away from fitted plane");
        setPropertyTooltip("Local Planes","th3","threshold for flat planes");
        setPropertyTooltip("Local Planes","planeEstimator","select method to fit plane to neighborhood of event");
    }    
    
    synchronized void initializeDataMatrix() {
        sx2 = 0;
        sy2 = 0;
        st2 = 0;
        sxy = 0;
        sxt = 0;
        syt = 0;
        sxx = 0;
        syy = 0;
        stt = 0;

        for (double[] n : neighborhood) {
            sx2 += n[0]*n[0];
            sy2 += n[1]*n[1];
            st2 += n[2]*n[2];
            sxy += n[0]*n[1];
            sxt += n[0]*n[2];
            syt += n[1]*n[2];
            sxx += n[0];
            syy += n[1];
            stt += n[2];
        }
    }
    
    synchronized private void computeFittingParameters() {
        jj = 0;
        if (fitOrder == 1) {
            a[0][0] = 0;
            a[0][1] = 0;
            a[1][0] = 0;
            for (sy = -searchDistance; sy <= searchDistance; sy++)
                for (sx = -searchDistance; sx <= searchDistance; sx++) {
                    a[0][0] += C[0][jj]*(lastTimesMap[x+sx][y+sy][type]-firstTs);
                    a[0][1] += C[2][jj]*(lastTimesMap[x+sx][y+sy][type]-firstTs);
                    a[1][0] += C[1][jj]*(lastTimesMap[x+sx][y+sy][type]-firstTs);
                    jj++;
                }
        } else {
            ii = 0;
            for (j = 0; j <= fitOrder; j++)
                for (i = 0; i <= fitOrder-j; i++) {
                    a[i][j] = 0;
                    for (sy = -searchDistance; sy <= searchDistance; sy++)
                        for (sx = -searchDistance; sx <= searchDistance; sx++) 
                            a[i][j] += C[ii][jj++]*(lastTimesMap[x+sx][y+sy][type]-firstTs);
                    ii++;
                    jj = 0;
                }
        }
    }
    
    synchronized void smoothTimesmap() {
        computeFittingParameters();
        ii = 0;
        jj = 0;
        for (sy = -searchDistance; sy <= searchDistance; sy++)
            for (sx = -searchDistance; sx <= searchDistance; sx++) {
                lastTimesMap[ii][jj][type] = 0;
                for (j = 0; j <= fitOrder; j++)
                    for (i = 0; i <= fitOrder-j; i++)
                        lastTimesMap[ii][jj++][type] += a[i][j]*Math.pow(sx,i)*Math.pow(sy,j);
                ii++;
                jj = 0;
            }
    }

    synchronized void initializeNeighborhood() {
        neighborhood = new ArrayList<>();
        for (sx = -searchDistance; sx <= searchDistance; sx++) 
            for (sy = -searchDistance; sy <= searchDistance; sy++)
                if (ts - lastTimesMap[x+sx][y+sy][type] < maxDtThreshold)
                    neighborhood.add(new double[] {x+sx,y+sy,
                    (lastTimesMap[x+sx][y+sy][type]-firstTs)*1e-6f,1});
    }
    
    // <editor-fold defaultstate="collapsed" desc="Various plane estimation methods">
    synchronized void computePlaneEstimate() {
        if (linearSavitzkyGolay) {
            // Calculate motion flow from the gradient of the timesmap smoothed
            // with a first order Savitzky-Golay filter.
            computeFittingParameters();
            a[1][0] /= a[0][0];
            a[0][1] /= a[0][0];
            vx = Math.abs(a[1][0]) < th3 ? 0 : (float) (20/a[1][0]);
            vy = Math.abs(a[0][1]) < th3 ? 0 : (float) (20/a[0][1]);
        } else if (singleFit) { 
            // Calculate motion flow by fitting a plane to the event's neighborhood.
            initializeNeighborhood();
            // Underdetermined system (need at least 3 equations):
            if (neighborhood.size() < 3) {
                vx = 0;
                vy = 0;
                v = 0;
                return;
            }
            initializeDataMatrix();
            // Matrix degenerate (detA == 0):
            if (sx2*sy2*st2+2*sxy*sxt*syt-sxt*sxt*sy2-sx2*syt*syt-sxy*sxy*st2 == 0) {
                vx = 0;
                vy = 0;
                v = 0;
                return;
            }
            planeParameters[0] = sxx*(syt*syt-sy2*st2)+syy*(sxy*st2-sxt*syt)+stt*(sxt*sy2-sxy*syt);
            planeParameters[1] = sxx*(sxy*st2-syt*sxt)+syy*(sxt*sxt-sx2*st2)+stt*(sx2*syt-sxy*sxt);
            planeParameters[2] = sxx*(sxt*sy2-sxy*syt)+syy*(sx2*syt-sxy*sxt)+stt*(sxy*sxy-sx2*sy2);                
            // <editor-fold defaultstate="collapsed" desc="Comment">
            /** When an edge is moving, we have a whole line of events with 
             *  similar timestamp, so the local plane will have zero gradient 
             *  along the edge orientation. This is falsely interpreted as an 
             *  infinitely high velocity, though there really is none. Since 
             *  time resolution is one microsecond, a neighbor-event should 
             *  only then be taken as the result of motion if the gradient in 
             *  that direction is at least 1e-6. Otherwise set velocity to zero.
             *  If th3 < 1e-5, those fast flow vectors appear in parallel to the
             *  moving edge. If th3 > 1e-3, many events are falsely filtered out.
             *  The gradient (dt/dx,dt/dy) of the fitted plane 
             *  a1*x + a2*y + a3*t = -1 is (-a1/a3,-a2/a3). 
             *  The velocity in x and y direction is given by the inverse 
             *  of its entries: v = (dx/dt,dy/dt) = (-a3/a1,-a3/a2).
             */
            // </editor-fold>
            vx = planeParameters[0]==0 ? 0 : -planeParameters[2]/planeParameters[0];
            vy = planeParameters[1]==0 ? 0 : -planeParameters[2]/planeParameters[1];
            vx = Math.abs(vx) >  1/th3 ? 0 : vx;
            vy = Math.abs(vy) >  1/th3 ? 0 : vy;
        } else { // Iterative fit
            // <editor-fold defaultstate="collapsed" desc="Comment">
            /** 
             *  The plane that fits the active surface of events locally is 
             *  given by equation a*x+b*y+c*ts+d*1=0. 
             *  We fill in the data matrix neighborhood = [x,y,t,1] to get an
             *  overdetermined homogeneous linear system of equations. We then
             *  apply Least Squares Linear Regression to data A (neighborhood) 
             *  to estimate plane parameters (a b c d). As we deal with a
             *  homogeneous linear system of equations, the least squares
             *  solution is the Eigenvector of matrix A'A corresponding to the 
             *  smallest Eigenvalue (smallest error).
             *  To speed up the process, we do many calculatios below "by hand" 
             *  because the built-in jama-functions (times, minus, copy,
             *  getMatrix, etc) involve new matrix initializations.
             *  At first, the data matrix has as many rows as there are pixels 
             *  in the neighborhood. However, at initialization and during the 
             *  iterative improvement, we check if an event in the neighborhood 
             *  is unreasonably far away in time. If this is the case, remove it 
             *  from our system of equations. In oscillating motion, this prevents 
             *  old timestamps from motion in one direction to contribute to the 
             *  plane fitting on the way back.
             *  Concerning the timecoordinate: Results are best when the timestamps
             *  are mapped to the interval [100,500] seconds. Smaller timestamps
             *  result in much noise; for bigger timestamps events are more and
             *  more filtered out.
             */       
            // </editor-fold>
            initializeNeighborhood();
            if (neighborhood.size() < 4) {
                vx = 0;
                vy = 0;
                v = 0;
                return;
            }
            
            // Initial fit
            A = new Matrix(neighborhood.toArray(new double[neighborhood.size()][4]));
            planeEstimate_old = A.transpose().times(A).eig().getV().getMatrix(0,3,0,0);

            // Iterative improvement
            planeEstimate.set(0,0,planeEstimate_old.get(0,0)); 
            planeEstimate.set(1,0,planeEstimate_old.get(1,0));
            planeEstimate.set(2,0,planeEstimate_old.get(2,0));
            planeEstimate.set(3,0,planeEstimate_old.get(3,0));
            eps = 1e6f;
            while (eps > th1) {
                change = false;
                for (i = 0; i < neighborhood.size(); i++)
                    // Discard events too far away from plane
                    if (Math.abs(planeEstimate_old.get(0,0)*neighborhood.get(i)[0]+ 
                                 planeEstimate_old.get(1,0)*neighborhood.get(i)[1]+
                                 planeEstimate_old.get(2,0)*neighborhood.get(i)[2]+
                                 planeEstimate_old.get(3,0)*neighborhood.get(i)[3])
                        > th2) {
                        neighborhood.remove(i);
                        i--;
                        change = true;
                    }
                if (!change) eps = 0;
                else if (neighborhood.size() > 3) {
                    // Calculate new plane fit with reduced neighborhood
                    A = new Matrix(neighborhood.toArray(new double[neighborhood.size()][4]));
                    planeEstimate = A.transpose().times(A).eig().getV().getMatrix(0,3,0,0);
                    // Update convergence parameter (Euklidean distance of plane)
                    eps = (float) Math.sqrt((planeEstimate.get(0,0)-planeEstimate_old.get(0,0))*
                                            (planeEstimate.get(0,0)-planeEstimate_old.get(0,0))+
                                            (planeEstimate.get(1,0)-planeEstimate_old.get(1,0))*
                                            (planeEstimate.get(1,0)-planeEstimate_old.get(1,0))+
                                            (planeEstimate.get(2,0)-planeEstimate_old.get(2,0))*
                                            (planeEstimate.get(2,0)-planeEstimate_old.get(2,0))+
                                            (planeEstimate.get(3,0)-planeEstimate_old.get(3,0))*
                                            (planeEstimate.get(3,0)-planeEstimate_old.get(3,0)));
                    planeEstimate_old.set(0,0,planeEstimate.get(0,0)); 
                    planeEstimate_old.set(1,0,planeEstimate.get(1,0));
                    planeEstimate_old.set(2,0,planeEstimate.get(2,0));
                    planeEstimate_old.set(3,0,planeEstimate.get(3,0));
                } else {
                    vx = 0;
                    vy = 0;
                    v = 0;
                    return;
                }
            } 
            if (homogeneousCoordinates) {
                if (planeEstimate.get(0,0) < th3 && planeEstimate.get(1,0) < th3) {
                    vx = 0;
                    vy = 0;
                } else {
                    tmp = -planeEstimate.get(2,0)/(planeEstimate.get(0,0)*planeEstimate.get(0,0)
                                                + planeEstimate.get(1,0)*planeEstimate.get(1,0));
                    vx = (float) (planeEstimate.get(0,0)*tmp);
                    vx = (float) (planeEstimate.get(1,0)*tmp);
                }
            } else {
                // <editor-fold defaultstate="collapsed" desc="Comment">
                /** When an edge is moving, we have a whole line of events with 
                 *  similar timestamp, so the local plane will have zero gradient 
                 *  along the edge orientation. This is falsely interpreted as an 
                 *  infinitely high velocity, though there really is none. Since 
                 *  time resolution is one microsecond, a neighbor-event should 
                 *  only then be taken as the result of motion if the gradient in 
                 *  that direction is at least 1e-6. Otherwise set velocity to zero.
                 *  If th3 < 1e-5, those fast flow vectors appear in parallel to the
                 *  moving edge. If th3 > 1e-3, many events are falsely filtered out.
                 *  The gradient (dt/dx,dt/dy) of the fitted plane 
                 *  a1*x + a2*y + a3*t = -1 is (-a1/a3,-a2/a3). 
                 *  The velocity in x and y direction is given by the inverse 
                 *  of its entries: v = (dx/dt,dy/dt) = (-a3/a1,-a3/a2).
                 */
                // </editor-fold>
                vx = Math.abs(planeEstimate.get(0,0)) < th3 ? 0 : (float) (-planeEstimate.get(2,0)/planeEstimate.get(0,0));
                vy = Math.abs(planeEstimate.get(1,0)) < th3 ? 0 : (float) (-planeEstimate.get(2,0)/planeEstimate.get(1,0));
            }
        }
        v = (float) Math.sqrt(vx*vx+vy*vy);
    }
    
    public PlaneEstimator getPlaneEstimator() {return planeEstimator;}
    
    synchronized public void setPlaneEstimator(PlaneEstimator planeEstimator) {
        this.planeEstimator = planeEstimator;
        putString("planeEstimator", planeEstimator.toString());
        singleFit = false;
        iterativeFit = false;
        linearSavitzkyGolay = false;
        homogeneousCoordinates = false;
        switch (planeEstimator) {
            case SingleFit:
                singleFit = true;
                resetFilter();
                break;
            case IterativeFit:
                iterativeFit = true;
                resetFilter();
                break;
            case LinearSavitzkyGolay: 
                linearSavitzkyGolay = true;
                resetFilter();
                break;
            case HomogeneousCoordinates:
                homogeneousCoordinates = true;
                resetFilter();
                break;
            default: 
                iterativeFit = true;
                resetFilter();
                break;
        }
    }
    // </editor-fold>
    
    // Prints the surface of active events in the neighborhood of a certain pixel.
    // Used for debugging and visualization of algorithm in MATLAB.
    synchronized private void printNeighborhood() {
        if (neighborhood == null) return;
        neighb = "[";
        for (i = 0; i < neighborhood.size(); i++)
            neighb += Arrays.toString(neighborhood.get(i))+"; ";
        neighb += "]";
        log.log(Level.INFO,String.format(Locale.ENGLISH,"T = %1$s; pe = [%2$2.2f "
            + "%3$2.2f %4$2.2f %5$2.2f]; v = [%6$2.2f %7$2.2f]; vIMU = [%8$2.2f %9$2.2f];", 
            new Object[]{neighb, planeEstimate.get(0,0), planeEstimate.get(1,0), 
            planeEstimate.get(2,0), planeEstimate.get(3,0), vx, vy, vxGT, vyGT}));
    }
    
    @Override synchronized public EventPacket filterPacket(EventPacket in) {
        setupFilter(in);
        firstTs = in.getFirstTimestamp();
        
        for (Object ein : in) {
            extractEventInfo(ein);
            if (measureAccuracy || discardOutliersEnabled) {
                imuFlowEstimator.calculateImuFlow((PolarityEvent) inItr.next());
                setGroundTruth();
            }
            if (isInvalidAddress(searchDistance)) continue;
            if (!updateTimesmap()) continue;
            if (xyFilter()) continue;
            countIn++;
            computePlaneEstimate();
            if (accuracyTests()) continue;
            writeOutputEvent();
            if (measureAccuracy) motionFlowStatistics.update(vx,vy,v,vxGT,vyGT,vGT);
        }
        motionFlowStatistics.updatePacket(measureProcessingTime,showGlobalEnabled,
                                          countIn,countOut);
        return isShowRawInputEnabled() ? in : dirPacket;
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --th1--">
    public float getTh1() {return this.th1;}

    public void setTh1(final float th1) {
        this.th1 = th1;
        putFloat("th1",th1);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --th2--">
    public float getTh2() {return this.th2;}

    public void setTh2(final float th2) {
        this.th2 = th2;
        putFloat("th2",th2);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --th3--">
    public float getTh3() {return this.th3;}

    public void setTh3(final float th3) {
        this.th3 = th3;
        putFloat("th3",th3);
    }
    // </editor-fold>
}