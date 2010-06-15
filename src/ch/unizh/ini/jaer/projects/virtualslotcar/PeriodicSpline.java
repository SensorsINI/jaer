
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
    double evaluateSpline(double x, double x0, double[] coeff) {
        double xnorm = x-x0;
        return coeff[3] + xnorm * coeff[2] + Math.pow(xnorm, 2) * coeff[1] +
                Math.pow(xnorm, 3) * coeff[0];
    }

    double evaluateX(double x, int splineIdx) {
        return evaluateSpline(x, Tdata[splineIdx], splineCoefficientsX[splineIdx]);
    }

    double evaluateY(double y, int splineIdx) {
        return evaluateSpline(y, Tdata[splineIdx], splineCoefficientsY[splineIdx]);
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
            while (curT < nextStop) {
                // Add interpolated points
                path.add(new Point2D.Double(evaluateX(curT, splineIdx),
                        evaluateY(curT, splineIdx)));
                curT+= stepsize;
            }
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



    // TODO
    // - approximation of new point
    // - test 2D spline functionality
    // - function for creating a curve with an arbitrary number of points
    // - create a curve with arbitrary number of in-between steps between interpolation points


    /**
     * Pure test function
     * @param args
     */
    public static void main(String[] args) {
        LinkedList<Point2D> testPoints = new LinkedList();
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
