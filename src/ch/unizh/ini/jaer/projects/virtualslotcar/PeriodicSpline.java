
/*
* To change this template, choose Tools | Templates
* and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

//~--- JDK imports ------------------------------------------------------------

import java.awt.geom.Point2D;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * A class for computing periodic cubic splines from a set of 2D points.
 * Algorithms are from H.R. Schwarz, Numerische Mathematik
 *                     B.G. Teubner, Stuttgart, 4th edition, 1997
 * @author Michael Pfeiffer
 */
public class PeriodicSpline {

    // Distances between data points (for 2D parametrization)
    private double[] Hdata = null;

    // Parameter positions of data points
    private double[] Tdata = null;

    // Data points in X and Y dimension
    private double[] Xdata = null;
    private double[] Ydata = null;

    // Number of target points to interpolate
    int numXY = 0;

    // Matrix containing all spline coefficients
    // For X and Y dimension separately
    private double[][] splineCoefficientsX = null;
    private double[][] splineCoefficientsY = null;

    // Sets up all data structures but does not compute splines yet
    public PeriodicSpline() {
        numXY = 0;
    }

    // Computes the spline coefficients from a list of points
    // Does not require the last point to be identical to the first point
    public void computeCoefficients(LinkedList<Point2D> XYdata) {
        numXY = XYdata.size();

        // Create coefficient array
        splineCoefficientsX = new double[numXY][4];
        splineCoefficientsY = new double[numXY][4];
        Xdata               = new double[numXY+1];
        Ydata               = new double[numXY+1];
        Hdata               = new double[numXY];
        Tdata               = new double[numXY+1];

        // Store XY-data in lists and compute distances
        ListIterator<Point2D> it  = XYdata.listIterator();
        int                   idx = 0;
        Point2D               p   = null;

        Tdata[0] = 0.0;

        while (it.hasNext()) {
            p          = it.next();
            Xdata[idx] = p.getX();
            Ydata[idx] = p.getY();

            if (idx > 0) {

                // Compute distance to previous point
                Hdata[idx - 1] = p.distance(Xdata[idx - 1], Ydata[idx - 1]);
                Tdata[idx] = Tdata[idx-1] + Hdata[idx-1];
            }

            idx++;
        }

        // Compute "periodic" distance between first and last point
        Hdata[numXY - 1] = p.distance(Xdata[0], Ydata[0]);
        Xdata[numXY] = Xdata[0];
        Ydata[numXY] = Ydata[0];
        Tdata[numXY] = Tdata[numXY-1]+Hdata[numXY-1];

        // Run spline algorithms to determine both coefficient matrices
        // Use same bow-length parametrization
        spline1D(Hdata, Xdata, splineCoefficientsX);
        spline1D(Hdata, Ydata, splineCoefficientsY);
    }

    /**
     * Computes spline coefficients for a single dimension
     * Spline algorithm from Schwarz to determine coefficients
     * @param T Parametrization values (X or T-coordinates)
     * @param Y Target values to interpolate
     * @param targetCoeff Matrix in which to store coefficients
     */
    public void spline1D(double[] T, double[] Y, double[][] targetCoeff) {
        int N = T.length;

        if ((N+1) != Y.length) {
            System.out.println("ERROR: Size of T and Y dimension does not agree in spline1D");
            System.exit(-1);
        }

        // Number of input points: N+1 (last point == first point)
        // Number of intervals in T: N
        // Number of splines: N

        
        // Solve system of equations (3.136) to find second derivatives
        double[] ddy = new double[N + 1];

        // Compute elements of Cholesky decomposition (3.137)
        double[] L = new double[N];
        double[] M = new double[N - 1];
        double[] E = new double[N - 2];
        double[] D = new double[N];

        // Algorithm from (3.138)
        double s = 0;

        L[0] = Math.sqrt(2.0 * (T[0] + T[N - 1]));
        E[0] = T[N - 1] / L[0];

        for (int i = 0; i < (N - 2); i++) {
            M[i] = T[i] / L[i];

            if (i >= 1) {
                E[i] = -E[i - 1] * M[i - 1] / L[i];
            }

            L[i + 1] = Math.sqrt(2.0 * (T[i] + T[i + 1]) - M[i] * M[i]);
            s        = s + E[i] * E[i];
        }

        M[N - 2] = (T[N - 2] - E[N - 3] * M[N - 3]) / L[N - 2];
        L[N - 1] = Math.sqrt((2.0 * (T[N - 2] + T[N - 1])) - M[N - 2] * M[N - 2] - s);


        D[0]     = -6.0 * (Y[1] - Y[0]) / T[0] + 6.0 * (Y[0] - Y[N - 1]) / T[N - 1];
        // D[N - 1] = -6.0 * (Y[0] - Y[N - 1]) / T[N - 1] + 6.0 * (Y[N - 1] - Y[N - 2]) / T[N - 2];

        for (int i = 1; i < N; i++) {

            // Assume Y[N+1] = Y[0]
            D[i] = -6.0 * (Y[i + 1] - Y[i]) / T[i] + 6.0 * (Y[i] - Y[i - 1]) / T[i - 1];
        }

        // Algorithm from (3.139)
        // Forward insertion for y
        double[] hY = new double[N];

        hY[0] = D[0] / L[0];
        s     = 0;

        for (int i = 1; i < (N - 1); i++) {
            hY[i] = (D[i] - M[i - 1] * hY[i - 1]) / L[i];
            s     = s + E[i - 1] * hY[i - 1];
        }

        hY[N - 1] = (D[N - 1] - M[N - 2] * hY[N - 2] - s) / L[N - 1];

        // Algorithm from (3.140)
        // Backward insertion to solve for second derivatives
        ddy[N - 1] = -hY[N - 1] / L[N - 1];
        ddy[N - 2] = -(hY[N - 2] + M[N - 2] * ddy[N - 1]) / L[N - 2];

        for (int i = N - 3; i >= 0; i--) {
            ddy[i] = -(hY[i] + M[i] * ddy[i + 1] + E[i] * ddy[N - 1]) / L[i];
        }

        // Periodic wrap around
        ddy[N] = ddy[0];

        // Insert second derivatives into (3.124) to determine spline coefficients
        for (int i = 0; i < N; i++) {
            targetCoeff[i][0] = (ddy[i + 1] - ddy[i]) / (6.0 * T[i]);                                 // a_i
            targetCoeff[i][1] = ddy[i] / 2.0;                                                         // b_i
            targetCoeff[i][2] = (Y[i + 1] - Y[i]) / T[i] - (ddy[i + 1] + 2 * ddy[i]) * T[i] / 6.0;    // c_i
            targetCoeff[i][3] = Y[i];
        }


    }

    /** 
     * Evaluates a 1D spline at a given position
     * @param x Position at which to evaluate
     * @param x0 Start point of spline
     * @param coeff Spline coefficients
     * @return Interpolated value at x
     */
    private double evaluateSpline(double x, double x0, double[] coeff) {
        double xnorm = x-x0;
        return coeff[3] + xnorm * coeff[2] + Math.pow(xnorm, 2) * coeff[1] +
                Math.pow(xnorm, 3) * coeff[0];
    }

    private double evaluateX(double x, int splineIdx) {
        return evaluateSpline(x, Tdata[splineIdx], splineCoefficientsX[splineIdx]);
    }

    private double evaluateY(double y, int splineIdx) {
        return evaluateSpline(y, Tdata[splineIdx], splineCoefficientsY[splineIdx]);
    }

    /**
     * Evaluates the orientation of the spline curve at the given position (first derivative)
     * @param x Position at which to evaluate
     * @param x0 Start point of spline
     * @param coeff Spline coefficients
     * @return Orientation at x
     */
    private double orientationSpline(double x, double x0, double[] coeff) {
        double xnorm = x-x0;
        return coeff[2] + 2*xnorm * coeff[1] +
                3.0*Math.pow(xnorm, 2) * coeff[0];

    }

    private double orientationX(double x, int splineIdx) {
        return orientationSpline(x, Tdata[splineIdx], splineCoefficientsX[splineIdx]);
    }

    private double orientationY(double y, int splineIdx) {
        return orientationSpline(y, Tdata[splineIdx], splineCoefficientsY[splineIdx]);
    }


    /**
     * Evaluates the curvature of the spline curve at the given position (second derivative)
     * @param x Position at which to evaluate
     * @param x0 Start point of spline
     * @param coeff Spline coefficients
     * @return Curvature at x
     */
    private double curvatureSpline(double x, double x0, double[] coeff) {
        double xnorm = x-x0;
        return 2.0*coeff[1] +
                6.0*xnorm * coeff[0];

    }

    private double curvatureX(double x, int splineIdx) {
        return curvatureSpline(x, Tdata[splineIdx], splineCoefficientsX[splineIdx]);
    }

    private double curvatureY(double y, int splineIdx) {
        return curvatureSpline(y, Tdata[splineIdx], splineCoefficientsY[splineIdx]);
    }


    /**
     * Returns the radius of the osculating circle of the spline curve
     * @param t Spline parameter
     * @param splineIdx Index of the spline segment
     * @param center Object in which to store the center of the osculating circle
     * @return Radius of the osculating circle
     */
    public double osculatingCircle(double t, int splineIdx, Point2D center) {
        // Determine position and derivatives at point
        double xpos = evaluateX(t, splineIdx);
        double ypos = evaluateY(t, splineIdx);
        double xdiff = orientationX(t, splineIdx);
        double ydiff = orientationY(t, splineIdx);
        double xcurv = curvatureX(t, splineIdx);
        double ycurv = curvatureY(t, splineIdx);
        double orientNorm = xdiff*xdiff + ydiff*ydiff;
        double curveDenom = xdiff*ycurv - xcurv*ydiff;

        // Compute radius of circle
        double radius = Double.POSITIVE_INFINITY;
        if (Math.abs(curveDenom) > 1e-10) {
            // radius = Math.abs(Math.pow(orientNorm, 3.0/2.0) / curveDenom);
            radius = Math.pow(orientNorm, 3.0/2.0) / curveDenom;

            // Store center of circle
            if (center != null) {
                center.setLocation(xpos - ydiff * orientNorm / curveDenom,
                    ypos + xdiff * orientNorm / curveDenom);
            }
        }
        else {
            radius *= Math.signum(curveDenom);
            if (center != null) {
                center.setLocation(Double.NaN, Double.NaN);
            }
        }
        return radius;
    }

    /**
     * Returns the radius and center of the osculating circle at the given position.
     * @param T Spline parameter
     * @param center Point in which to store the center of the circle
     * @return 0 if successful, -1 if not.
     */
    public double getOsculatingCircle(double T, Point2D center) {
        // Find segment in which T lies
        int idx = getInterval(T);

        double radius = osculatingCircle(T, idx, center);

        return radius;
    }



    /**
     * Returns a list of all points including the original points and the
     * interpolated points.
     * @param stepsize Step-size for interpolated points
     * @return A list of all points on the 2D spline
     */
    LinkedList<Point2D> allPoints(double stepsize) {
        if ((stepsize <= 0) || (numXY <= 0)) {
            return null;
        }
        LinkedList<Point2D> path = new LinkedList<Point2D>();
        double curT = stepsize;
        int splineIdx = 0;
        double stopT = Tdata[numXY];

        while ((curT < stopT) && (splineIdx < numXY)) {
            // Add original point
            path.add(new Point2D.Double(Xdata[splineIdx], Ydata[splineIdx]));
            double nextStop = Tdata[splineIdx+1];
            while (curT < nextStop-stepsize) {
                // Add interpolated points
                path.add(new Point2D.Double(evaluateX(curT, splineIdx),
                        evaluateY(curT, splineIdx)));
                curT+= stepsize;
            }
            curT = nextStop+stepsize;
            splineIdx++;
        }

        // Add final point
        path.add(new Point2D.Double(Xdata[numXY], Ydata[numXY]));

        return path;
    }

    /** Returns length of smooth track */
    public double getLength() {
        // TODO: This is just an approximation of the smooth length
        return Tdata[numXY];
    }

    /**
     * Returns the interval in which this parameter value lies.
     * @param T Spline parameter
     * @return The index of the spline interval in which T lies.
     */
    public int getInterval(double T) {
        int idx = 1;
        // Find segment in which T lies
        while ((idx <= numXY) && (T > Tdata[idx])) {
            idx++;
        }
        if (idx > numXY) {
            // T not in parameter range
            return -1;
        }
        else return (idx-1);
    }

    /**
     * Gets the smooth spline position at the parameter value if the spline segment is known.
     * @param t Spline parameter
     * @param idx Index of the spline segment in which T lies.
     * @return Point on 2D spline curve
     */
    public Point2D getPosition(double T, int idx) {
        Point2D.Double p = new Point2D.Double(evaluateX(T, idx), evaluateY(T, idx));
        return p;
    }

    /**
     * Gets the smooth spline position at the parameter value
     * @param t Spline parameter
     * @return Point on 2D spline curve
     */
    public Point2D getPosition(double T) {
        // Find segment in which T lies
        int idx = getInterval(T);
        Point2D.Double p = new Point2D.Double(evaluateX(T, idx), evaluateY(T, idx));
        return p;
    }

    /**
     * Returns the orientation of the spline at the given position
     * @param T Spline parameter
     * @return A normalized orientation vector
     */
    public Point2D getOrientation(double T) {
        // Find segment in which T lies
        int idx = getInterval(T);
        Point2D.Double p = new Point2D.Double(orientationX(T, idx), orientationY(T, idx));
        double norm = p.distance(0,0);
        p.setLocation(p.getX()/norm, p.getY()/norm);
        return p;
    }

    /**
     * Returns the position and orientation of the spline at the given position
     * @param T Spline parameter
     * @param pos Point in which to store the position
     * @param orient Point in which to store the normalized orientation vector
     * @return 0 if successful, -1 if not.
     */
    public int getPositionAndOrientation(double T, Point2D pos, Point2D orient) {
        // Find segment in which T lies
        int idx = getInterval(T);
        return getPositionAndOrientation(T,idx,pos,orient);
    }

    /**
     * Returns the position and orientation of the spline at the given position
     * @param T Spline parameter
     * @param idx Index of the segment of the track
     * @param pos Point in which to store the position
     * @param orient Point in which to store the normalized orientation vector
     * @return 0 if successful, -1 if not.
     */
    public int getPositionAndOrientation(double T, int idx, Point2D pos, Point2D orient) {
        if ((pos==null) || (orient == null))
            return -1;
        if (idx > numXY) {
            // T not in parameter range
            return -1;
        }
        pos.setLocation(evaluateX(T,idx), evaluateY(T,idx));
        double oX = orientationX(T, idx);
        double oY = orientationY(T, idx);
        double norm = Math.sqrt(oX*oX+oY*oY);
        orient.setLocation(oX/norm, oY/norm);
        return 1;
    }


    /**
     * Computes the next parameter value if the arc-length is increased by ds.
     * Approximates the arc-length by sub-dividing into intervals of length int_step.
     * @param t Current parameter value (not arc-length)
     * @param curSegment Current track segment
     * @param ds Arc-length distance to new point
     * @param int_step Integration step
     * @return New parameter value (not arc-length)
     */
    public double advance(double t, int curSegment, double ds, double int_step) {
        double L = 0.0;
        double cur_t = t;
        double old_t = t;

        int interval = curSegment;
        Point2D old_p = getPosition(cur_t, interval);

        int count = 0;
        while (L < ds) {
            // System.out.println("Count " + (count++));
            // Compute next intermediate point
            cur_t+=int_step;
            if (cur_t >= Tdata[numXY]) {
                cur_t -= Tdata[numXY];
                interval = 0;
            } else while (cur_t >= Tdata[interval+1]) {
                interval++;
            }

            Point2D cur_p = getPosition(cur_t, interval);
            double dist = cur_p.distance(old_p);

            // Increase bow-length
            if (L+dist < ds) {
                L+=dist;
                old_p = cur_p;
                old_t = cur_t;
            }
            else break;
        }
        // Interpolate last segment
        cur_t = old_t + int_step * (ds-L);

        // return t+ds;  // non arc-length parametrization
        return cur_t;
    }

    /**
     * Computes the next parameter value if the arc-length is increased by ds.
     * Approximates the arc-length by sub-dividing into intervals of length int_step.
     * @param t Current parameter value (not arc-length)
     * @param ds Arc-length distance to new point
     * @param int_step Integration step
     * @return New parameter value (not arc-length)
     */
    public double advance(double t, double ds, double int_step) {
        int interval = getInterval(t);
        return advance(t,interval,ds,int_step);
    }


    /**
     *  Refines this spline by introducing intermediate points
     * @param stepSize The step size in parameter space
     * @return The new refined spline.
     */
    public PeriodicSpline refine(double stepSize) {
        PeriodicSpline fineSpline = new PeriodicSpline();

        // Compute list of intermediate points
        double t = 0.0;
        int idx = 0;
        LinkedList<Point2D> finePoints = new LinkedList<Point2D>();

        while (t < Tdata[numXY]) {
            Point2D p = getPosition(t, idx);
            finePoints.add(p);

            t+=stepSize;
            while ((idx < numXY) && (t >= Tdata[idx+1])) {
                idx++;
            }
        }

        fineSpline.computeCoefficients(finePoints);
        return fineSpline;
    }
    

    /**
     * Pure test function
     * @param args
     */
    public static void main(String[] args) {
        LinkedList<Point2D> testPoints = new LinkedList<Point2D>();
        double[]            X          = {
            1.0, 2.5, 5.25, 9.5, 12.0, 14.5, 17.0
        };
        double[]            Y          = new double[7];

        for (int i = 0; i < 7; i++) {
            Y[i] = 2.5 * (Math.cos(2.0 * Math.PI * X[i] / 16.0) - Math.sin(4.0 * Math.PI * X[i] / 16.0)) + 5;
            testPoints.add(new Point2D.Double(X[i], Y[i]));
        }

        PeriodicSpline psp         = new PeriodicSpline();
        double[][]     targetCoeff = new double[6][4];

        double[] T = new double[6];
        for (int i=0; i<6; i++)
            T[i] = X[i+1] - X[i];
        

        psp.spline1D(T, Y, targetCoeff);

        for (int i = 0; i < 6; i++) {
            System.out.print(i + ": ");

            for (int j = 0; j < 4; j++) {
                System.out.print(targetCoeff[i][j] + " / ");
            }

            System.out.println();
        }
    }
}


//~ Formatted by Jindent --- http://www.jindent.com
