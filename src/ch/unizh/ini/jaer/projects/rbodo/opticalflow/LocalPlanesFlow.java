package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Level;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.util.jama.Matrix;

/**
 * Draws individual optical flow vectors and computes global motion, rotation
 * and expansion. Algorithm is based on the method presented in R. Benosman, C.
 * Clercq, X. Lagorce, S.-H. Ieng, and C. Bartolozzi, “Event-Based Visual Flow,”
 * IEEE Transactions on Neural Networks and Learning Systems, vol. Early Access
 * Online, 2013. It uses the local properties of events' spatiotemporal space by
 * fitting a plane to an incoming event's neighborhood on the surface of active
 * events. The gradient of this local tangential plane corresponds to the
 * inverse velocity.
 *
 * @author rbodo
 */
@Description("Computes normal optical flow events with speed and vector direction using robust gradient of last events timestamp surface.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class LocalPlanesFlow extends AbstractMotionFlow {

    // Magnitude of accuracy from iterative estimation algorithm. 
    // In each iteration of the local plane-fitting, this threshold is compared 
    // to the distance of the most recent plane estimate to the previous.
    private float th1 = getFloat("th1", 1e-5f);
    private float eps;
    private boolean change;

    // When an event in the neighborhood is farther away from the local plane estimate 
    // than this threshold value, it is discarded from the data set.
    private float th2 = getFloat("th2", 5e-2f);

    // Threshold for flat planes causing events to be assigned an unrealistically
    // high velocity.
    private float th3 = getFloat("th3", 1e-3f);

    private ArrayList<double[]> neighborhood;
    private final float[] planeParameters;
    private Matrix planeEstimate, planeEstimate_old, A;

    private float sx2, sy2, st2, sxy, sxt, syt, sxx, syy, stt;
    private int xx, yy;

    public enum PlaneEstimator {
        OriginalLP, RobustLP, SingleFit, LinearSavitzkyGolay
    };
    private PlaneEstimator planeEstimator;
    private boolean originalLP, robustLP, singleFit, linearSavitzkyGolay;

    // First timestamp of input packet.
    private int firstTs;

    private String neighb;

    private float tmp;

    private int t1, t2;

    public LocalPlanesFlow(AEChip chip) {
        super(chip);
        planeParameters = new float[3];
        planeEstimate = new Matrix(4, 1);
        try {
            planeEstimator = PlaneEstimator.valueOf(getString("planeEstimator", "RobustLP"));
        } catch (IllegalArgumentException ex) {
            log.log(Level.WARNING, "bad preference {0} for preferred PlaneEstimator, choosing default RobustLP",
                    getString("planeEstimator", "RobustLP"));
            planeEstimator = LocalPlanesFlow.PlaneEstimator.RobustLP;
            putString("planeEstimator", "RobustLP");
        }
        setPlaneEstimator(planeEstimator);
        numInputTypes = 2;
        resetFilter();
        setPropertyTooltip("Local Planes", "th1", "Accuracy of iterative estimation. The 'IterativeFit' "
                + "and 'HomogeneousCoordinates' algorithm will reject outliers and recompute "
                + "the plane as long as the summed difference between old and new fit parameters "
                + "is greater than this threshold. Usually not set lower than 1e-5.");
        setPropertyTooltip("Local Planes", "th2", "In 'IterativeFit' and 'HomogeneousCoordinates', events that are further away"
                + "from the fitted plane than this threshold are discarded. Usually not set lower than 0.01.");
        setPropertyTooltip("Local Planes", "th3", "When the gradient of the fitted plane is below this threshold, "
                + "the corresponding velocity component is set to zero (unrealistically high speed due to flat plane). Usually not set higher than 0.01.");
        setPropertyTooltip("Local Planes", "planeEstimator", "<html>Select method to fit plane to most-recent timestamps map in neighborhood of event<ul><li>OriginalLP:Robust iterative least squares fit using th1 and th2 but with biased derivative estimate that skews vector angles<li>RobustLP: least squares fit iterative least squares fit using th1 and th2 and disregarding events older than maxDtThreshold and computing velocity using homogenous coordinates that properly handles small x or y derivatives<li>SingleFit: single linear fit including outliers (timestamps that are obsolute); disregards th1 th2 and th3<li>LinearSavitzkyGolay: feedforward computation of slopes using smoothing of derivatives in perpindicular direction and not including events older than maxDtThreshold");
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
            sx2 += n[0] * n[0];
            sy2 += n[1] * n[1];
            st2 += n[2] * n[2];
            sxy += n[0] * n[1];
            sxt += n[0] * n[2];
            syt += n[1] * n[2];
            sxx += n[0];
            syy += n[1];
            stt += n[2];
        }
    }

    synchronized private void computeFittingParameters() {
        /**
         * This function computes the parameters that fit in the Least-Squares
         * sense a polynomial of order "fitOrder" to the data, which in this
         * case consists of the most recent timestamps "lastTimesMap" as a
         * function of pixel location (x,y). The underlying method is the
         * convolution of a patch of the timesmap with a Savitzky-Golay
         * smoothing kernel. Important assumption for calculating the fitting
         * parameters: All points in the neighborhood must exist and be valid,
         * e.g. not too old or negative or zero. Because this is not always
         * satisfied for our timesmap, the Savitzky-Golay kernel cannot be
         * applied directly. However, for the 2D linear fit (plane), we can
         * instead perform the low-level derivative computations, which as a
         * whole constitute the kernel, by hand and thus control the inclusion
         * of each point individually.
         */

        jj = 0;
        if (fitOrder == 1) {
            a[1][0] = 0;
            a[0][1] = 0;
            ii = 0;
            jj = 0;
            for (i = -searchDistance; i <= searchDistance; i++) {
                for (j = -searchDistance; j <= searchDistance; j++) {
                    t1 = lastTimesMap[x + i][y + j][type];
                    if (t1 != Integer.MIN_VALUE && ts - t1 < maxDtThreshold) {
                        for (xx = i + 1; xx <= searchDistance; xx++) {
                            t2 = lastTimesMap[x + xx][y + j][type];
                            if (t2 != Integer.MIN_VALUE && ts - t2 < maxDtThreshold) {
                                a[1][0] += (float) (t2 - t1) / (xx - i);
                                ii++;
                            }
                        }
                        for (yy = j + 1; yy <= searchDistance; yy++) {
                            t2 = lastTimesMap[x + i][y + yy][type];
                            if (t2 != Integer.MIN_VALUE && ts - t2 < maxDtThreshold) {
                                a[0][1] += (float) (t2 - t1) / (yy - j);
                                jj++;
                            }
                        }
                    }
                }
            }
            a[1][0] = ii == 0 ? 0 : a[1][0] / ii;
            a[0][1] = jj == 0 ? 0 : a[0][1] / jj;
        } else { // While mathematically correct, this higher order smoothing
            // should not be used in flow computation because (unlike the 
            // first order filter above) in the present form it does not 
            // take into account invalid / old events on the
            // surface of most recent events.
            ii = 0;
            for (j = 0; j <= fitOrder; j++) {
                for (i = 0; i <= fitOrder - j; i++) {
                    a[i][j] = 0;
                    for (jjj = -searchDistance; jjj <= searchDistance; jjj++) {
                        for (iii = -searchDistance; iii <= searchDistance; iii++) {
                            a[i][j] += C[ii][jj++] * lastTimesMap[x + iii][y + jjj][type];
                        }
                    }
                    ii++;
                    jj = 0;
                }
            }
        }
    }

    synchronized private void smoothTimesmap() {
        /**
         * This function convolves a patch of the timesmap with a Savitzky-Golay
         * smoothing kernel. In other words, it applies a Least-Squares
         * polynomial fit to the surface of most recent events to decrease
         * noise. Important assumption for calculating the fitting parameters:
         * All points in the neighborhood must exist and be valid. This function
         * is at present not used in the Local Plane method, because a direct
         * computation of the plane fit using the first order Savitzky- Golay
         * filter is faster.
         */
        computeFittingParameters();
        ii = 0;
        jj = 0;
        for (jjj = -searchDistance; jjj <= searchDistance; jjj++) {
            for (iii = -searchDistance; iii <= searchDistance; iii++) {
                lastTimesMap[ii][jj][type] = Integer.MIN_VALUE; // I don't think this is the correct initialization here (Bodo)
                for (j = 0; j <= fitOrder; j++) {
                    for (i = 0; i <= fitOrder - j; i++) {
                        lastTimesMap[ii][jj++][type] += a[i][j] * Math.pow(iii, i) * Math.pow(jjj, j);
                    }
                }
                ii++;
                jj = 0;
            }
        }
    }

    synchronized void initializeNeighborhood() {
        neighborhood = new ArrayList<>();
        for (i = -searchDistance; i <= searchDistance; i++) {
            for (j = -searchDistance; j <= searchDistance; j++) {
                t1 = lastTimesMap[x + i][y + j][type];
                if (t1 != Integer.MIN_VALUE && ts - t1 < maxDtThreshold) {
                    neighborhood.add(new double[]{x + i, y + j,
                        (t1 - firstTs) * 1e-6f, 1});
                }
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Various plane estimation methods">
    private void velFromPar(float a, float b, float c, float thr) {
        /**
         * Let the plane that fits a patch of the surface of most recent events
         * be given by the normal vector (a,b,c), i.e. the timestamps t(x,y) as
         * a function of pixel location (x,y) satisfy the equation ax+by+ct+d ==
         * 0. Then the gradient on the surface t(x,y) is given by g == (-a/c,
         * -b/c) and the velocity vector by v == g/|g|^2 == -c/(a^2+b^2) (a, b).
         * The threshold th3 asserts non-vanishing divisors. Vanishing a and b,
         * i.e. vanishing gradients on approximately flat planes, correspond to
         * unrealistically fast motion. Since time resolution is one
         * microsecond, a neighbor-event should only then be taken as the result
         * of motion if the gradient in that direction is at least 1e-6.
         * Otherwise set the velocity to zero.
         */
        if (Math.abs(a) < thr && Math.abs(b) < thr) {
            vx = 0;
            vy = 0;
        } else {
            tmp = -c / (a * a + b * b);
            vx = a * tmp;
            vy = b * tmp;
        }
    }

    synchronized void computePlaneEstimate() {
        if (linearSavitzkyGolay) {
            // Calculate motion flow from the gradient of the timesmap smoothed
            // with a first order Savitzky-Golay filter.
            computeFittingParameters();
            a[1][0] *= 1e-6;
            a[0][1] *= 1e-6;
            velFromPar((float) a[1][0], (float) a[0][1], -1, th3);
        } else if (singleFit) {
            // Calculate motion flow by fitting a plane to the event's neighborhood.
            initializeNeighborhood();
            // Underdetermined system (need at least 3 equations):
            if (neighborhood.size() < 3) {
                vx = 0;
                vy = 0;
                return;
            }
            initializeDataMatrix();
            // Matrix degenerate (detA == 0):
            if (sx2 * sy2 * st2 + 2 * sxy * sxt * syt - sxt * sxt * sy2 - sx2 * syt * syt - sxy * sxy * st2 == 0) {
                vx = 0;
                vy = 0;
                return;
            }
            planeParameters[0] = sxx * (syt * syt - sy2 * st2) + syy * (sxy * st2 - sxt * syt) + stt * (sxt * sy2 - sxy * syt);
            planeParameters[1] = sxx * (sxy * st2 - syt * sxt) + syy * (sxt * sxt - sx2 * st2) + stt * (sx2 * syt - sxy * sxt);
            planeParameters[2] = sxx * (sxt * sy2 - sxy * syt) + syy * (sx2 * syt - sxy * sxt) + stt * (sxy * sxy - sx2 * sy2);
            velFromPar(planeParameters[0], planeParameters[1], planeParameters[2], th3 * 1e4f);
        } else { // Iterative fit
            // <editor-fold defaultstate="collapsed" desc="Comment">
            /**
             * The plane that fits the active surface of events locally is given
             * by equation a*x+b*y+c*ts+d*1=0. We fill in the data matrix
             * neighborhood = [x,y,t,1] to get an overdetermined homogeneous
             * linear system of equations. We then apply Least Squares Linear
             * Regression to data A (neighborhood) to estimate plane parameters
             * (a b c d). As we deal with a homogeneous linear system of
             * equations, the least squares solution is the Eigenvector of
             * matrix A'A corresponding to the smallest Eigenvalue (smallest
             * error). To speed up the process, we do many calculatios below "by
             * hand" because the built-in jama-functions (times, minus, copy,
             * getMatrix, etc) involve new matrix initializations. At first, the
             * data matrix has as many rows as there are pixels in the
             * neighborhood. However, at initialization and during the iterative
             * improvement, we check if an event in the neighborhood is
             * unreasonably far away in time. If this is the case, remove it
             * from our system of equations. In oscillating motion, this
             * prevents old timestamps from motion in one direction to
             * contribute to the plane fitting on the way back. Concerning the
             * timecoordinate: Results are best when the timestamps are mapped
             * to the interval [100,500] seconds. Smaller timestamps result in
             * much noise; for bigger timestamps events are more and more
             * filtered out.
             */
            // </editor-fold>
            initializeNeighborhood();
            if (neighborhood.size() < 4) {
                vx = 0;
                vy = 0;
                return;
            }

            // Initial fit
            A = new Matrix(neighborhood.toArray(new double[neighborhood.size()][4]));
            planeEstimate_old = A.transpose().times(A).eig().getV().getMatrix(0, 3, 0, 0);

            // Iterative improvement
            planeEstimate.set(0, 0, planeEstimate_old.get(0, 0));
            planeEstimate.set(1, 0, planeEstimate_old.get(1, 0));
            planeEstimate.set(2, 0, planeEstimate_old.get(2, 0));
            planeEstimate.set(3, 0, planeEstimate_old.get(3, 0));
            eps = 1e6f;
            while (eps > th1) {
                change = false;
                for (i = 0; i < neighborhood.size(); i++) // Discard events too far away from plane
                {
                    if (Math.abs(planeEstimate_old.get(0, 0) * neighborhood.get(i)[0]
                            + planeEstimate_old.get(1, 0) * neighborhood.get(i)[1]
                            + planeEstimate_old.get(2, 0) * neighborhood.get(i)[2]
                            + planeEstimate_old.get(3, 0) * neighborhood.get(i)[3])
                            > th2) {
                        neighborhood.remove(i);
                        i--;
                        change = true;
                    }
                }
                if (!change) {
                    eps = 0;
                } else if (neighborhood.size() > 3) {
                    // Calculate new plane fit with reduced neighborhood
                    A = new Matrix(neighborhood.toArray(new double[neighborhood.size()][4]));
                    planeEstimate = A.transpose().times(A).eig().getV().getMatrix(0, 3, 0, 0);
                    // Update convergence parameter (Euklidean distance of plane)
                    eps = (float) Math.sqrt((planeEstimate.get(0, 0) - planeEstimate_old.get(0, 0))
                            * (planeEstimate.get(0, 0) - planeEstimate_old.get(0, 0))
                            + (planeEstimate.get(1, 0) - planeEstimate_old.get(1, 0))
                            * (planeEstimate.get(1, 0) - planeEstimate_old.get(1, 0))
                            + (planeEstimate.get(2, 0) - planeEstimate_old.get(2, 0))
                            * (planeEstimate.get(2, 0) - planeEstimate_old.get(2, 0))
                            + (planeEstimate.get(3, 0) - planeEstimate_old.get(3, 0))
                            * (planeEstimate.get(3, 0) - planeEstimate_old.get(3, 0)));
                    planeEstimate_old.set(0, 0, planeEstimate.get(0, 0));
                    planeEstimate_old.set(1, 0, planeEstimate.get(1, 0));
                    planeEstimate_old.set(2, 0, planeEstimate.get(2, 0));
                    planeEstimate_old.set(3, 0, planeEstimate.get(3, 0));
                } else {
                    vx = 0;
                    vy = 0;
                    return;
                }
            }
            if (robustLP) {
                velFromPar((float) planeEstimate.get(0, 0),
                        (float) planeEstimate.get(1, 0),
                        (float) planeEstimate.get(2, 0), th3);
            } else {
                // <editor-fold defaultstate="collapsed" desc="Comment">
                /**
                 * When an edge is moving, we have a whole line of events with
                 * similar timestamp, so the local plane will have zero gradient
                 * along the edge orientation. This is falsely interpreted as an
                 * infinitely high velocity, though there really is none. Since
                 * time resolution is one microsecond, a neighbor-event should
                 * only then be taken as the result of motion if the gradient in
                 * that direction is at least 1e-6. Otherwise set velocity to
                 * zero. If th3 < 1e-5, those fast flow vectors appear in parallel to the
                 *  moving edge. If th3 > 1e-3, many events are falsely filtered
                 * out. The gradient (dt/dx,dt/dy) of the fitted plane a1*x +
                 * a2*y + a3*t = -1 is (-a1/a3,-a2/a3). The velocity in x and y
                 * direction is given by the inverse of its entries: v =
                 * (dx/dt,dy/dt) = (-a3/a1,-a3/a2).
                 */
                // </editor-fold>
                vx = Math.abs(planeEstimate.get(0, 0)) < th3 ? 0 : (float) (-planeEstimate.get(2, 0) / planeEstimate.get(0, 0));
                vy = Math.abs(planeEstimate.get(1, 0)) < th3 ? 0 : (float) (-planeEstimate.get(2, 0) / planeEstimate.get(1, 0));
            }
        }
        v = (float) Math.sqrt(vx * vx + vy * vy);
    }

    // Prints the surface of active events in the neighborhood of a certain pixel.
    // Used for debugging and visualization of the algorithm in MATLAB.
    synchronized private void printNeighborhood() {
        if (linearSavitzkyGolay) {
            neighb = "[";
            for (i = -searchDistance; i <= searchDistance; i++) {
                for (j = -searchDistance; j <= searchDistance; j++) {
                    neighb += String.format(Locale.ENGLISH, "[%1$d %2$d %3$2.2f];", x + i, y + j,
                            lastTimesMap[x + i][y + j][type] * 1e-6f);
                }
            }
            neighb += "]";
            log.log(Level.INFO, String.format(Locale.ENGLISH, "T = %1$s; pe = [%2$2.2f "
                    + "%3$2.2f %4$2.2f]; v = [%5$2.2f %6$2.2f]; vIMU = [%7$2.2f %8$2.2f];",
                    new Object[]{neighb, a[1][0], a[0][1], a[0][0], vx, vy, vxGT, vyGT}));
        } else {
            if (neighborhood == null) {
                return;
            }
            neighb = "[";
            for (i = 0; i < neighborhood.size(); i++) {
                neighb += Arrays.toString(neighborhood.get(i)) + "; ";
            }
            neighb += "]";
            log.log(Level.INFO, String.format(Locale.ENGLISH, "T = %1$s; pe = [%2$2.2f "
                    + "%3$2.2f %4$2.2f %5$2.2f]; v = [%6$2.2f %7$2.2f]; vIMU = [%8$2.2f %9$2.2f];",
                    new Object[]{neighb, planeEstimate.get(0, 0), planeEstimate.get(1, 0),
                        planeEstimate.get(2, 0), planeEstimate.get(3, 0), vx, vy, vxGT, vyGT}));
        }
    }

//    private int lastImuSampleTs = 0; // debug
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        setupFilter(in);
        firstTs = in.getFirstTimestamp();

         // following awkward block needed to deal with DVS/DAVIS and IMU/APS events
        // block STARTS
        Iterator i = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            i = ((EventPacket) in).inputIterator();
        }

        while (i.hasNext()) {
            Object o=i.next();
             if (o == null) {
                log.warning("null event passed in, returning input packet");
                return in;
            }
             if ((o instanceof ApsDvsEvent) && ((ApsDvsEvent)o).isApsData()) {
                continue;
            }
            PolarityEvent ein = (PolarityEvent) o;
           
            if (!extractEventInfo(o)) {
                continue;
            }
            if ( measureAccuracy || discardOutliersForStatisticalMeasurementEnabled) {
                if(imuFlowEstimator.calculateImuFlow(o)) continue;
            }
            // block ENDS
            if (isInvalidAddress(searchDistance)) {
                continue;
            }
            if (isInvalidTimestamp()) {
                continue;
            }
            if (xyFilter()) {
                continue;
            }
            countIn++;
            computePlaneEstimate();
            if (accuracyTests()) {
                continue;
            }
            processGoodEvent();
        }
        getMotionFlowStatistics().updatePacket(countIn, countOut, ts);
        return isDisplayRawInput() ? in : dirPacket;
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --th1--">
    public float getTh1() {
        return this.th1;
    }

    public void setTh1(final float th1) {
        this.th1 = th1;
        putFloat("th1", th1);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --th2--">
    public float getTh2() {
        return this.th2;
    }

    public void setTh2(final float th2) {
        this.th2 = th2;
        putFloat("th2", th2);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --th3--">
    public float getTh3() {
        return this.th3;
    }

    public void setTh3(final float th3) {
        this.th3 = th3;
        putFloat("th3", th3);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --planeEstimator--">
    public PlaneEstimator getPlaneEstimator() {
        return planeEstimator;
    }

    synchronized public void setPlaneEstimator(PlaneEstimator planeEstimator) {
        PlaneEstimator old = planeEstimator;
        this.planeEstimator = planeEstimator;
        putString("planeEstimator", planeEstimator.toString());
        originalLP = false;
        robustLP = false;
        singleFit = false;
        linearSavitzkyGolay = false;
        switch (planeEstimator) {
            case OriginalLP:
                originalLP = true;
                resetFilter();
                break;
            case RobustLP:
                robustLP = true;
                resetFilter();
                break;
            case SingleFit:
                singleFit = true;
                resetFilter();
                break;
            case LinearSavitzkyGolay:
                linearSavitzkyGolay = true;
                resetFilter();
                break;
            default:
                robustLP = true;
                resetFilter();
                break;
        }
        getSupport().firePropertyChange("planeEstimator", old, planeEstimator);
    }
    // </editor-fold>
}
