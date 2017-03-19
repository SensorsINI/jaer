package net.sf.jaer.util.filter;
import java.awt.geom.Point2D;
/**
 * A lowpass filter that operates on 2 scalar inputs with common time constant and time.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class LowpassFilter2D extends LowpassFilter{
    private LowpassFilter x=new LowpassFilter();
    private LowpassFilter y=new LowpassFilter();
    private Point2D.Float point=new Point2D.Float();

    /** Construct a new instance using an internal Point2D.Float
     *
     */
    public LowpassFilter2D(){
        x.setTauMs(getTauMs());
        y.setTauMs(getTauMs());
    }

    /** Construct a new instance using a supplied Point2D.Float to store the value
     *
     * @param point
     */
    public LowpassFilter2D(Point2D.Float point){
        this.point=point;
        x.setInternalValue(point.x);
        y.setInternalValue(point.y);
    }

    /** Construct a new instance using an internal Point2D.Float.
     *@param tauMs the time constant in ms.
     */
    public LowpassFilter2D(float tauMs){
        x.setTauMs(getTauMs());
        y.setTauMs(getTauMs());
    }

    /** Construct a new instance using a supplied Point2D.Float to store the value.
     *
     * @param point
     * @param tauMs the time constant in ms
     */
    public LowpassFilter2D(Point2D.Float point, float tauMs){
        this.point=point;
        x.setInternalValue(point.x);
        y.setInternalValue(point.y);
    }

    public void setTauMs(float tauMs){
        super.setTauMs(tauMs);
        x.setTauMs(tauMs);
        y.setTauMs(tauMs);
    }

    /** Filter a 2D value with a specified timestamp in us
     * 
     * @param x
     * @param y
     * @param time in us
     * @return the 2D filter state
     */
    public Point2D.Float filter(float x,float y,int time){
        point.x=this.x.filter(x,time);
        point.y=this.y.filter(y,time);
        return point;
    }

    public Point2D.Float getValue2D(){
        point.x=x.getValue();
        point.y=y.getValue();
        return point;
    }

    public void setInternalValue2d(float x,float y){
        this.x.setInternalValue(x);
        this.y.setInternalValue(y);
    }

    @Override
    public String toString(){
        return "LP2d: tauMs="+getTauMs()+" x: "+x.lastVal+"->"+x.lpVal+", y: "+y.lastVal+"->"+y.lpVal;
    }

    @Override
    public float filter(float val,int time){
        throw new UnsupportedOperationException("this is a 2d filter");
    }

    @Override
    public float getValue(){
        throw new UnsupportedOperationException("this is a 2d filter");
    }

    @Override
    public void setInternalValue(float value){
        throw new UnsupportedOperationException("this is a 2d filter");
    }

}
