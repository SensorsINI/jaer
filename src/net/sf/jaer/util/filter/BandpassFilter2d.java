package net.sf.jaer.util.filter;
import java.awt.geom.Point2D;
/**
 * A bandpass filter that operates on 2 scalar inputs with common time constant and time.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class BandpassFilter2d extends BandpassFilter{
    private BandpassFilter x=new BandpassFilter();
    private BandpassFilter y=new BandpassFilter();
    private Point2D.Float point=new Point2D.Float();

    /** Construct a new instance using default values
     *
     */
    public BandpassFilter2d(){
        x.setTauMsLow(getTauMsLow());
        y.setTauMsLow(getTauMsLow());
        x.setTauMsHigh(getTauMsHigh());
        y.setTauMsHigh(getTauMsHigh());
    }

    /** Construct a new instance using a supplied Point2D.Float to store the initial value
     *
     * @param point
     */
    public BandpassFilter2d(Point2D.Float point){
        this.point=point;
        x.setInternalValue(point.x);
        y.setInternalValue(point.y);
    }

    /** Construct a new instance and set the time constants
     */
    public BandpassFilter2d(float tauMsLow, float tauMsHigh){
         setTauMsLow(tauMsLow);
         setTauMsHigh(tauMsHigh);
    }

    @Override
    public void setTauMsLow(float tauMs){
        super.setTauMsLow(tauMs);
        x.setTauMsLow(tauMs);
        y.setTauMsLow(tauMs);
    }    
    
    @Override
    public void setTauMsHigh(float tauMs){
        super.setTauMsHigh(tauMs);
        x.setTauMsHigh(tauMs);
        y.setTauMsHigh(tauMs);
    }

    public Point2D.Float filter2d(float x,float y,int time){
        point.x=this.x.filter(x,time);
        point.y=this.y.filter(y,time);
        return point;
    }    
    
    public Point2D.Float filter2d(Point2D.Float p,int time){
        point.x=this.x.filter(p.x,time);
        point.y=this.y.filter(p.y,time);
        return point;
    }

    public Point2D.Float getValue2d(){
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
        return "BP2d: tauMs="+getTauMs()+" x: "+x.getValue()+", y: "+y.getValue();
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
