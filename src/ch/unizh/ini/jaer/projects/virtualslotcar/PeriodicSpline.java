
/*
* To change this template, choose Tools | Templates
* and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

//~--- JDK imports ------------------------------------------------------------

import java.awt.geom.Point2D;
import java.util.LinkedList;

/**
 * A class for computing periodic cubic splines from a set of 2D points.
 * <p>
 *  The spline curve is parameterized by a single parameter (call it T),
     * which gives you the points on the track as (x(T), y(T)).
     * T can have a rather arbitrary scale, so if your car runs for a distance of S meters,
     * you cannot simply use the point (x(T+S), y(T+S)), but you have to find the corresponding parameter
     * value T* for your new position. What PeriodicSpline.advance does is to start from the current
     * parameter value t (curSegment just tells you in which segment of the spline curve this parameter t lies),
     * and then computes the new parameter T* that corresponds to a point on the spline curve with distance of ds
     * on the curve (this is typically different from a straight line distance). It does so by
     * partitioning the curve into smaller segments (with lengths int_step in T-space).
 * <p>
 * Algorithms are from H.R. Schwarz, Numerische Mathematik
 *                     B.G. Teubner, Stuttgart, 4th edition, 1997
 * @author Michael Pfeiffer
 */
public class PeriodicSpline implements java.io.Serializable {

    // Distances between data points (for 2D parametrization)
    private float[] Hdata = null;

    // Parameter positions of data points
    private float[] Tdata = null;

    // Data points in X and Y dimension
    private float[] Xdata = null;
    private float[] Ydata = null;

    // Number of target points to interpolate
    int numXY = 0;

    // Matrix containing all spline coefficients
    // For X and Y dimension separately
    private float[][] splineCoefficientsX = null;
    private float[][] splineCoefficientsY = null;

    // Sets up all data structures but does not compute splines yet
    public PeriodicSpline() {
        numXY = 0;
    }

    // Computes the spline coefficients from a list of points
    // Does not require the last point to be identical to the first point
    public void computeCoefficients(LinkedList<Point2D.Float> XYdata) {
        numXY = XYdata.size();

        // Create coefficient array
        splineCoefficientsX = new float[numXY][4];
        splineCoefficientsY = new float[numXY][4];
        Xdata               = new float[numXY+1];
        Ydata               = new float[numXY+1];
        Hdata               = new float[numXY];
        Tdata               = new float[numXY+1];

        double[][] DsplineCoefficientsX = new double[numXY][4];
        double[][] DsplineCoefficientsY = new double[numXY][4];
        double[] DXdata               = new double[numXY+1];
        double[] DYdata               = new double[numXY+1];
        double[] DHdata               = new double[numXY];
        double[] DTdata               = new double[numXY+1];


        // Store XY-data in lists and compute distances
        int                   idx = 0;

        DTdata[0] = 0;
        Point2D.Float lastPoint=new Point2D.Float();
        for(Point2D.Float p:XYdata) {
            lastPoint=p;
            DXdata[idx] = p.getX();
            DYdata[idx] = p.getY();

            if (idx > 0) {

                // Compute distance to previous point
                DHdata[idx - 1] = p.distance(DXdata[idx - 1], DYdata[idx - 1]);
                DTdata[idx] = DTdata[idx-1] + DHdata[idx-1];
            }

            idx++;
        }

        // Compute "periodic" distance between first and last point
        DHdata[numXY - 1] = (lastPoint.distance(DXdata[0], DYdata[0]));
        DXdata[numXY] = DXdata[0];
        DYdata[numXY] = DYdata[0];
        DTdata[numXY] = DTdata[numXY-1]+DHdata[numXY-1];

        // Run spline algorithms to determine both coefficient matrices
        // Use same bow-length parametrization
        spline1D(DHdata, DXdata, DsplineCoefficientsX);
        spline1D(DHdata, DYdata, DsplineCoefficientsY);


        // Convert double values to float
        for (int i=0; i<numXY; i++) {
            for (int j=0; j<4; j++) {
                splineCoefficientsX[i][j] = (float) DsplineCoefficientsX[i][j];
                splineCoefficientsY[i][j] = (float) DsplineCoefficientsY[i][j];
            }
            Xdata[i] = (float)DXdata[i];
            Ydata[i] = (float)DYdata[i];
            Hdata[i] = (float)DHdata[i];
            Tdata[i] = (float)DTdata[i];
        }
        Xdata[numXY] = (float)DXdata[numXY];
        Ydata[numXY] = (float)DYdata[numXY];
        Tdata[numXY] = (float)DTdata[numXY];

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

        L[0] = (double)Math.sqrt(2.0 * (T[0] + T[N - 1]));
        E[0] = T[N - 1] / L[0];

        for (int i = 0; i < (N - 2); i++) {
            M[i] = T[i] / L[i];

            if (i >= 1) {
                E[i] = -E[i - 1] * M[i - 1] / L[i];
            }

            L[i + 1] = (double)Math.sqrt(2.0 * (T[i] + T[i + 1]) - M[i] * M[i]);
            s        = s + E[i] * E[i];
        }

        M[N - 2] = (T[N - 2] - E[N - 3] * M[N - 3]) / L[N - 2];
        L[N - 1] = (double)Math.sqrt((2.0 * (T[N - 2] + T[N - 1])) - M[N - 2] * M[N - 2] - s);


        D[0]     = -6 * (Y[1] - Y[0]) / T[0] + 6 * (Y[0] - Y[N - 1]) / T[N - 1];
        // D[N - 1] = -6.0 * (Y[0] - Y[N - 1]) / T[N - 1] + 6.0 * (Y[N - 1] - Y[N - 2]) / T[N - 2];

        for (int i = 1; i < N; i++) {

            // Assume Y[N+1] = Y[0]
            D[i] = -6 * (Y[i + 1] - Y[i]) / T[i] + 6 * (Y[i] - Y[i - 1]) / T[i - 1];
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
            targetCoeff[i][0] = (ddy[i + 1] - ddy[i]) / (6 * T[i]);                                 // a_i
            targetCoeff[i][1] = ddy[i] / 2;                                                         // b_i
            targetCoeff[i][2] = (Y[i + 1] - Y[i]) / T[i] - (ddy[i + 1] + 2 * ddy[i]) * T[i] / 6;    // c_i
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
    private float evaluateSpline(float x, float x0, float[] coeff) {
        float xnorm = x-x0;
//        return coeff[3] + xnorm * coeff[2] + (float)Math.pow(xnorm, 2) * coeff[1] +
//                (float)Math.pow(xnorm, 3) * coeff[0];
        return coeff[3] + xnorm * (coeff[2] + xnorm * (coeff[1] + coeff[0] * xnorm));
    }

    private float evaluateX(float x, int splineIdx) {
        if(splineIdx==-1){
            throw new RuntimeException("invalid splineIdx="+splineIdx+" at pos="+x);
        }
        return evaluateSpline(x, Tdata[splineIdx], splineCoefficientsX[splineIdx]);
    }

    private float evaluateY(float y, int splineIdx) {
        return evaluateSpline(y, Tdata[splineIdx], splineCoefficientsY[splineIdx]);
    }

    /**
     * Evaluates the orientation of the spline curve at the given position (first derivative)
     * @param x Position at which to evaluate
     * @param x0 Start point of spline
     * @param coeff Spline coefficients
     * @return Orientation at x
     */
    private float orientationSpline(float x, float x0, float[] coeff) {
        float xnorm = x-x0;
        return coeff[2] + 2*xnorm * coeff[1] +
                3*(float)Math.pow(xnorm, 2) * coeff[0];

    }

    private float orientationX(float x, int splineIdx) {
        return orientationSpline(x, Tdata[splineIdx], splineCoefficientsX[splineIdx]);
    }

    private float orientationY(float y, int splineIdx) {
        return orientationSpline(y, Tdata[splineIdx], splineCoefficientsY[splineIdx]);
    }


    /**
     * Evaluates the curvature of the spline curve at the given position (second derivative)
     * @param x Position at which to evaluate
     * @param x0 Start point of spline
     * @param coeff Spline coefficients
     * @return Curvature at x
     */
    private float curvatureSpline(float x, float x0, float[] coeff) {
        float xnorm = x-x0;
        return 2*coeff[1] +
                6*xnorm * coeff[0];

    }

    private float curvatureX(float x, int splineIdx) {
        return curvatureSpline(x, Tdata[splineIdx], splineCoefficientsX[splineIdx]);
    }

    private float curvatureY(float y, int splineIdx) {
        return curvatureSpline(y, Tdata[splineIdx], splineCoefficientsY[splineIdx]);
    }


    /**
     * Returns the radius of the osculating circle of the spline curve
     * @param t Spline parameter
     * @param splineIdx Index of the spline segment
     * @param center Object in which to store the center of the osculating circle
     * @return Radius of the osculating circle
     */
    public float osculatingCircle(float t, int splineIdx, Point2D center) {
        // Determine position and derivatives at point
        float xpos = evaluateX(t, splineIdx);
        float ypos = evaluateY(t, splineIdx);
        float xdiff = orientationX(t, splineIdx);
        float ydiff = orientationY(t, splineIdx);
        float xcurv = curvatureX(t, splineIdx);
        float ycurv = curvatureY(t, splineIdx);
        float orientNorm = xdiff*xdiff + ydiff*ydiff;
        float curveDenom = xdiff*ycurv - xcurv*ydiff;

        // Compute radius of circle
        float radius = Float.POSITIVE_INFINITY;
        if (Math.abs(curveDenom) > 1e-10) {
            // radius = Math.abs(Math.pow(orientNorm, 3.0/2.0) / curveDenom);
            radius = (float)Math.pow(orientNorm, 3.0/2.0) / curveDenom;

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
    public float getOsculatingCircle(float T, Point2D center) {
        // Find segment in which T lies
        int idx = getInterval(T);

        float radius = osculatingCircle(T, idx, center);

        return radius;
    }

    /**
     * Returns the radius and center of the osculating circle at the given position.
     * @param T Spline parameter
     * @param idx The index of the spline segment in which T falls.
     * @param center Point in which to store the center of the circle
     * @return 0 if successful, -1 if not.
     */
    public float getOsculatingCircle(float T, int idx, Point2D center) {
        float radius = osculatingCircle(T, idx, center);

        return radius;
    }


    /**
     * Returns a list of all points including the original points and the
     * interpolated points.
     * @param stepsize Step-size for interpolated points
     * @return A list of all points on the 2D spline
     */
    LinkedList<Point2D.Float> allPoints(float stepsize) {
        if ((stepsize <= 0) || (numXY <= 0)) {
            return null;
        }
        LinkedList<Point2D.Float> path = new LinkedList<Point2D.Float>();
        float curT = stepsize;
        int splineIdx = 0;
        float stopT = Tdata[numXY];

        while ((curT < stopT) && (splineIdx < numXY)) {
            // Add original point
            path.add(new Point2D.Float(Xdata[splineIdx], Ydata[splineIdx]));
            float nextStop = Tdata[splineIdx+1];
            while (curT < nextStop-stepsize) {
                // Add interpolated points
                path.add(new Point2D.Float(evaluateX(curT, splineIdx),
                        evaluateY(curT, splineIdx)));
                curT+= stepsize;
            }
            curT = nextStop+stepsize;
            splineIdx++;
        }

        // Add final point
        path.add(new Point2D.Float(Xdata[numXY], Ydata[numXY]));

        return path;
    }

    /**
     * Returns a list of all original spline points.
     * @return A list of all spline points on the 2D spline
     */
    LinkedList<Point2D.Float> getSplinePoints() {
        LinkedList<Point2D.Float> path = new LinkedList<Point2D.Float>();

        for (int i=0; i<numXY; i++) {
            // Add original point
            path.add(new Point2D.Float(Xdata[i], Ydata[i]));
        }

        return path;
    }


    /** Returns length of smooth track */
    public float getLength() {
        // TODO: This is just an approximation of the smooth length
        return Tdata[numXY];
    }

    /**
     * Returns the interval in which this parameter value lies.
     * @param T Spline parameter
     * @return The index of the spline interval in which T lies.
     */
    public int getInterval(float T) {
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
     * Returns the interval in which this parameter value lies, starting the search
     * from a given start interval and increasing the index; note that this search will not find intervals before the current position, only after.
     * @param T Spline parameter
     * @param startIdx The index at which to start the search
     * @return The index of the spline interval in which T lies, or 0 if if T is not in the total parameter range to start over.
     */
    public int newInterval(float T, int startIdx) {
        int idx = startIdx;
        // Find segment in which T lies
        while ((idx <= numXY) && (T > Tdata[idx])) {
            idx++;
        }
        if (idx > numXY) {
            // T not in parameter range so wrap around to start of spline // TODO check michael is this correct behavior?
            return 0;
        }
        if(idx==0) return numXY-1; // wrap arond to end of closed spline
        else return idx-1; // TODO check if we return startIdx if we are in the same segment
//        else return (idx-1); // TODO returns -1 if we start with 0, should return last index?
    }

    /**
     * Returns the parameter value corresponding to the point index. This is the
     * "inverse function" to getInterval.
     * @param idx The index of the spline point.
     * @return The parameter value corresponding to the spline point, or NaN if the
     * index is out of bounds.
     */
    public float getParam(int idx) {
        if ((idx < 0) || (idx > this.numXY) || Tdata==null)
            return Float.NaN;
        else {
            return this.Tdata[idx];
        }
    }

    /**
     * Gets the smooth spline position at the parameter value if the spline segment is known.
     * @param T Spline parameter
     * @param idx Index of the spline segment in which T lies.
     * @return Point on 2D spline curve
     */
    public Point2D.Float getPosition(float T, int idx) {
        Point2D.Float p = new Point2D.Float(evaluateX(T, idx), evaluateY(T, idx));
        return p;
    }

    /**
     * Gets the smooth spline position at the parameter value
     * @param T Spline parameter
     * @return Point on 2D spline curve
     */
    public Point2D.Float getPosition(float T) {
        // Find segment in which T lies
        int idx = getInterval(T);
        Point2D.Float p = new Point2D.Float(evaluateX(T, idx), evaluateY(T, idx));
        return p;
    }

    /**
     * Returns the orientation of the spline at the given position
     * @param T Spline parameter
     * @return A normalized orientation vector
     */
    public Point2D getOrientation(float T) {
        // Find segment in which T lies
        int idx = getInterval(T);
        Point2D.Double p = new Point2D.Double(orientationX(T, idx), orientationY(T, idx));
        float norm = (float)p.distance(0,0);
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
    public int getPositionAndOrientation(float T, Point2D pos, Point2D orient) {
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
    public int getPositionAndOrientation(float T, int idx, Point2D pos, Point2D orient) {
        if ((pos==null) || (orient == null))
            return -1;
        if (idx > numXY) {
            // T not in parameter range
            return -1;
        }
        pos.setLocation(evaluateX(T,idx), evaluateY(T,idx));
        float oX = orientationX(T, idx);
        float oY = orientationY(T, idx);
        float norm = (float)Math.sqrt(oX*oX+oY*oY);
        orient.setLocation(oX/norm, oY/norm);
        return 1;
    }


    /**
     * Computes the next parameter value if the arc-length is increased by ds.
     * Approximates the arc-length by sub-dividing into intervals of length int_step.
     * <p>
     * The spline curve is parameterized by a single parameter (call it T),
     * which gives you the points on the track as (x(T), y(T)).
     * T can have a rather arbitrary scale, so if your car runs for a distance of S meters,
     * you cannot simply use the point (x(T+S), y(T+S)), but you have to find the corresponding parameter
     * value T* for your new position. What PeriodicSpline.advance does is to start from the current
     * parameter value t (curSegment just tells you in which segment of the spline curve this parameter t lies),
     * and then computes the new parameter T* that corresponds to a point on the spline curve with distance of ds
     * on the curve (this is typically different from a straight line distance). It does so by
     * partitioning the curve into smaller segments (with lengths int_step in T-space).
    <p>
     * This function computes the next position on the track in
     * the simulation of the virtual car. For the real slotcar you need it to
     * compute from your track model the curvature that lies ahead, as it computes
     * the spline parameters corresponding to points on the track with a certain distance to your current position.
     *
     * @param t Current parameter value (not arc-length)
     * @param curSegment Current track segment
     * @param ds Arc-length distance to new point
     * @param int_step Integration step
     * @return New parameter value (not arc-length)
     */
    public float advance(float t, int curSegment, float ds, float int_step) {
        float L = 0;
        float cur_t = t;
        float old_t = t;

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
            float dist = (float)cur_p.distance(old_p);

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
     * @return New t parameter value (not arc-length or time)
     */
    public float advance(float t, float ds, float int_step) {
        int interval = getInterval(t);
        return advance(t,interval,ds,int_step);
    }

    /**
     *  Refines this spline by introducing intermediate points
     * @param stepSize The step size in parameter space
     * @return The new refined spline.
     */
    public PeriodicSpline refine(float stepSize) {
        PeriodicSpline fineSpline = new PeriodicSpline();

        // Compute list of intermediate points
        float t = 0;
        int idx = 0;
        LinkedList<Point2D.Float> finePoints = new LinkedList<Point2D.Float>();

        while (t < (Tdata[numXY]-0.5*stepSize)) {
            Point2D.Float p = getPosition(t, idx);
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
/*        LinkedList<Point2D> testPoints = new LinkedList<Point2D>();
        float[]            X          = {
            1, 2.5f, 5.25f, 9.5f, 12.0f, 14.5f, 17.0f
        };
        float[]            Y          = new float[7];

        for (int i = 0; i < 7; i++) {
            Y[i] = 2.5f * ((float)Math.cos(2.0 * Math.PI * X[i] / 16f) - (float)Math.sin(4.0 * Math.PI * X[i] / 16.0)) + 5;
            testPoints.add(new Point2D.Double(X[i], Y[i]));
        }

        PeriodicSpline psp         = new PeriodicSpline();
        float[][]     targetCoeff = new float[6][4];

        float[] T = new float[6];
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

    */
    }

}


//~ Formatted by Jindent --- http://www.jindent.com
