/*
 * RollingLinearRegression.java
 *
 * Created on October 16, 2007, 7:30 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright October 16, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.util;

import java.awt.geom.Point2D;
import java.util.LinkedList;

/**
 * Does a moving or rolling linear regression (a linear fit) on updated data.
 The new data point replaces the oldest data point. Summary statistics holds the rollling values
 and are updated by subtracting the oldest point and adding the newest one.
 From <a href="http://en.wikipedia.org/wiki/Ordinary_least_squares#Summarizing_the_data">Wikipedia article on Ordinary least squares</a>.
 
 * @author tobi
 */
public class RollingLinearRegression {
    
    public static final int LENGTH_DEFAULT=3;
    
    private int length=LENGTH_DEFAULT;
    private float sx, sy, sxx, sxy; // summary stats
    private LinkedList<Point2D.Float> points=new LinkedList<Point2D.Float>();
    private float intercept, slope;
    
    /** Creates a new instance of RollingLinearRegression */
    public RollingLinearRegression() {
    }
    
    /** Adds a new point.
     @param p the point
     */
    synchronized public void addPoint(Point2D.Float p){
        removeOldestPoint();
        points.add(p);
        int n=points.size();
        sx+=p.x;
        sy+=p.y;
        sxx+=p.x*p.x;
        sxy+=p.x*p.y;
        slope=(n*sxy-sx*sy)/(n*sxx-sx*sx);
        intercept=(sy-slope*sx)/n;
    }
    
    private void removeOldestPoint() {
        if(points.size()>length-1){
            Point2D.Float p=points.removeFirst();
            int n=points.size();
            sx-=p.x;
            sy-=p.y;
            sxx-=p.x*p.x;
            sxy-=p.x*p.y;
        }
    }
    
    public int getLength() {
        return length;
    }
    
    /** Sets the window length.  Clears the accumulated data.
     @param length the number of points to fit 
     @see #LENGTH_DEFAULT
     */
    synchronized public void setLength(int length) {
        this.length = length;
        points=new LinkedList<Point2D.Float>();
        sx=0;
        sy=0;
        sxx=0;
        sxy=0;
    }
    
    public LinkedList<Point2D.Float> getPoints() {
        return points;
    }
    
    public float getIntercept() {
        return intercept;
    }
    
    public float getSlope() {
        return slope;
    }
    
    private static void add(RollingLinearRegression r, float x, float y){
        r.addPoint(new Point2D.Float(x,y));
        System.out.println(String.format("x,y=%.1f,%.1f, slope=%.1f, intercept=%.1f",x,y,r.getSlope(),r.getIntercept()));
    }
    
    public static void main(String[] args){
        int n;
        n=10;
        System.out.println("length="+n);
        RollingLinearRegression r=new RollingLinearRegression();
        r.setLength(10);
        add(r,0,0);
        add(r,1,1);
        add(r,2,2);
        add(r,3,4);
        add(r,3,2);
        n=2;
        System.out.println("length="+n);
        r=new RollingLinearRegression();
        r.setLength(2);
        add(r,0,0);
        add(r,1,1);
        add(r,2,2);
        add(r,3,4);
        add(r,3,2);
    }
    
    
}
