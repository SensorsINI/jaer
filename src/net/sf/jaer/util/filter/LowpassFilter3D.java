package net.sf.jaer.util.filter;
/**
 * A lowpass filter that operates on 3 scalar inputs with common time constant and time.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class LowpassFilter3D extends LowpassFilter{

   /**
     * The <code>Float</code> class defines a point specified in float
     * precision.
     */
    public static class Point3D  {
	/**
	 * The X coordinate of this <code>Point2D</code>.
	 */
	public float x;

	/**
	 * The Y coordinate of this <code>Point2D</code>.
	 */
	public float y;

        /**
	 * The Z coordinate of this <code>Point2D</code>.
	 */
	public float z;

	/**
	 * Constructs and initializes a <code>Point2D</code> with
         * coordinates (0,&nbsp;0).
	 * @since 1.2
	 */
	public Point3D() {
	}

	/**
	 * Constructs and initializes a <code>Point2D</code> with
         * the specified coordinates.
         *
         * @param x the X coordinate of the newly
         *          constructed <code>Point2D</code>
         * @param y the Y coordinate of the newly
         *          constructed <code>Point2D</code>
	 * @since 1.2
	 */
	public Point3D(float x, float y, float z) {
	    this.x = x;
	    this.y = y;
            this.z=z;
	}

	/**
	 */
	public float getX() {
	    return (float) x;
	}

	/**
	 */
	public double getY() {
	    return (double) y;
	}

	/**
	 */
	public double getZ() {
	    return (double) z;
	}

	/**
	 */
	public void setLocation(double x, double y) {
	    this.x = (float) x;
	    this.y = (float) y;
	}

	/**
	 * Sets the location of this <code>Point2D</code> to the
         * specified <code>float</code> coordinates.
         *
         * @param x the new X coordinate of this {@code Point2D}
         * @param y the new Y coordinate of this {@code Point2D}
	 */
	public void setLocation(float x, float y) {
	    this.x = x;
	    this.y = y;
	}

	/**
	 * Returns a <code>String</code> that represents the value
         * of this <code>Point2D</code>.
         * @return a string representation of this <code>Point2D</code>.
	 */
	public String toString() {
	    return "Point3D["+x+", "+y+", "+z+"]";
	}

    }

    private LowpassFilter xf = new LowpassFilter();
    private LowpassFilter yf = new LowpassFilter();
    private LowpassFilter zf = new LowpassFilter();
    private Point3D point = new Point3D();

    /** Construct a new instance using an internal Point3D
     *
     */
    public LowpassFilter3D (){
        xf.setTauMs(getTauMs());
        yf.setTauMs(getTauMs());
        zf.setTauMs(getTauMs());
    }

    /** Construct a new instance using a supplied Point3D to store the value
     *
     * @param point
     */
    public LowpassFilter3D (Point3D point){
        this.point = point;
        xf.setInternalValue(point.x);
        yf.setInternalValue(point.y);
    }

    /** Construct a new instance using an internal Point3D.
     *@param tauMs the time constant in ms.
     */
    public LowpassFilter3D (float tauMs){
        xf.setTauMs(getTauMs());
        yf.setTauMs(getTauMs());
    }

    /** Construct a new instance using a supplied Point3D to store the value.
     *
     * @param point
     * @param tauMs the time constant in ms
     */
    public LowpassFilter3D (Point3D point,float tauMs){
        this.point = point;
        xf.setInternalValue(point.x);
        yf.setInternalValue(point.y);
    }

    public void setTauMs (float tauMs){
        super.setTauMs(tauMs);
        xf.setTauMs(tauMs);
        yf.setTauMs(tauMs);
        zf.setTauMs(tauMs);
    }

    /** Apply filter to incoming data.
     *
     * @param x
     * @param y
     * @param z
     * @param time in us of this sample
     * @return output of filter
     */
    public Point3D filter (float x,float y,float z,int time){
        point.x = this.xf.filter(x,time);
        point.y = this.yf.filter(y,time);
        point.z = this.zf.filter(z,time);
        return point;
    }

    public Point3D filter (Point3D p, int time){
        return filter(p.x,p.y,p.z,time);
    }

    /** Returns internal Point3D present value. */
    public Point3D getValue3D (){
        point.x = xf.getValue();
        point.y = yf.getValue();
        point.z = zf.getValue();
        return point;
    }

    public void setInternalValue3D (float x,float y,float z){
        this.xf.setInternalValue(x);
        this.yf.setInternalValue(y);
        this.zf.setInternalValue(z);
    }

    @Override
    public String toString (){
        return "LP2d: tauMs=" + getTauMs() + " x: " + xf.lastVal + "->" + xf.lpVal + ", y: " + yf.lastVal + "->" + yf.lpVal;
    }

    @Override
    public float filter (float val,int time){
        throw new UnsupportedOperationException("this is a 3d filter");
    }

    @Override
    public float getValue (){
        throw new UnsupportedOperationException("this is a 3d filter");
    }

    @Override
    public void setInternalValue (float value){
        throw new UnsupportedOperationException("this is a 3d filter");
    }
}
