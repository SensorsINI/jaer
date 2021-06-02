/*
 * MedianTrackingFilter.java
 *
 * Modified July 9, 2010
 * David Mascarenas
 *
 * This is a modified version of tobi's  MedianTracker.java changed so it would filter
 * out any event that falls outside of some scaled region of the stddev
 * The preserved region will be circular with a radius scaled by the std dev.  
 *
 * Created on December 4, 2005, 11:04 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.eventprocessing.filter;

import java.awt.geom.Point2D;
import java.util.Arrays;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Tracks median event location.
 *
 * @author tobi
 */
@Description("Tracks a single object by median event location")
public class MedianTrackingFilter extends EventFilter2D implements FrameAnnotater {

    Point2D medianPoint=new Point2D.Float(),stdPoint=new Point2D.Float(),meanPoint=new Point2D.Float();
    float xmedian=0f;
    float ymedian=0f;
    float xstd=0f;
    float ystd=0f;
    float xmean=0, ymean=0;
    int lastts=0, dt=0;
    int prevlastts=0;

    float scale_inc = .1f;

    LowpassFilter xFilter=new LowpassFilter(), yFilter=new LowpassFilter();
    LowpassFilter xStdFilter=new LowpassFilter(), yStdFilter=new LowpassFilter();
    LowpassFilter xMeanFilter=new LowpassFilter(), yMeanFilter=new LowpassFilter();

    int tauUs=getPrefs().getInt("MedianTrackingFilter.tauUs",1000);
    {setPropertyTooltip("tauUs","Time constant in us (microseonds) of median location lowpass filter, 0 for instantaneous");}
    float alpha=1, beta=0; // alpha is current weighting, beta is past value weighting

    //====================================================
    //This is to set the scale value for the std dev
     int stddev_scale=getPrefs().getInt("MedianTrackingFilter.stddev_scale",10);
    {setPropertyTooltip("stddev_scale","Scaling factor for the standard deviation circle (1/10).  ");}
    //====================================================



    /** Creates a new instance of MedianTracker */
    public MedianTrackingFilter(AEChip chip) {
        super(chip);
        xFilter.setTauMs(tauUs/1000f);
        yFilter.setTauMs(tauUs/1000f);
        xStdFilter.setTauMs(tauUs/1000f);
        yStdFilter.setTauMs(tauUs/1000f);
        xMeanFilter.setTauMs(tauUs/1000f);
        yMeanFilter.setTauMs(tauUs/1000f);
    }


    public Object getFilterState() {
        return null;
    }

    public boolean isGeneratingFilter() {
        return false;
    }

    public void resetFilter() {
        medianPoint.setLocation(chip.getSizeX()/2,chip.getSizeY()/2);
        meanPoint.setLocation(chip.getSizeX()/2,chip.getSizeY()/2);
        stdPoint.setLocation(1,1);
    }


    public Point2D getMedianPoint() {
        return this.medianPoint;
    }

    public Point2D getStdPoint() {
        return this.stdPoint;
    }

    public Point2D getMeanPoint() {
        return this.meanPoint;
    }

    public int getTauUs() {
        return this.tauUs;
    }

    /**@param tauUs the time constant of the 1st order lowpass filter on median location */
    public void setTauUs(final int tauUs) {
        this.tauUs = tauUs;
        getPrefs().putInt("MedianTrackingFilter.tauUs", tauUs);
        xFilter.setTauMs(tauUs/1000f);
        yFilter.setTauMs(tauUs/1000f);
        xStdFilter.setTauMs(tauUs/1000f);
        yStdFilter.setTauMs(tauUs/1000f);
        xMeanFilter.setTauMs(tauUs/1000f);
        yMeanFilter.setTauMs(tauUs/1000f);
    }


    public int getStdDev_Scale() {
        return this.stddev_scale;
    }

    /**@param stddev_scale the scale to show  stdDev; scaling factor for the standard deviation circle (1/10). */
    public void setStdDev_Scale(final int stddev_scale) {
        this.stddev_scale = stddev_scale;
        getPrefs().putInt("FastCardID.stddev_scale", stddev_scale);

    }

    

    public void initFilter() {
    }


    public EventPacket filterPacket(EventPacket in) {
        int n=in.getSize();
        if(n==0) return in;

        lastts=in.getLastTimestamp();
        dt=lastts-prevlastts;
        prevlastts=lastts;

            int[] xs=new int[n], ys=new int[n];
            int index=0;
            for(Object o:in){
                BasicEvent e=(BasicEvent)o;
                xs[index]=e.x;
                ys[index]=e.y;
                index++;
            }
            Arrays.sort(xs,0,n-1);
            Arrays.sort(ys,0,n-1);
            float x,y;
            if(n%2!=0){ // odd number points, take middle one, e.g. n=3, take element 1
                x=xs[n/2];
                y=ys[n/2];
            }else{ // even num events, take avg around middle one, eg n=4, take avg of elements 1,2
                x=(float)(((float)xs[n/2-1]+xs[n/2])/2f);
                y=(float)(((float)ys[n/2-1]+ys[n/2])/2f);
            }
            xmedian=xFilter.filter(x,lastts);
            ymedian=yFilter.filter(y,lastts);
            int xsum=0,ysum=0;
            for(int i=0;i<n;i++){
                xsum+=xs[i];
                ysum+=ys[i];
            }
            xmean=xMeanFilter.filter(xsum/n,lastts);
            ymean=yMeanFilter.filter(ysum/n,lastts);

            float xvar=0,yvar=0;
            float tmp;
            for(int i=0;i<n;i++){
                tmp=xs[i]-xmean;
                tmp*=tmp;
                xvar+=tmp;

                tmp=ys[i]-ymean;
                tmp*=tmp;
                yvar+=tmp;
            }
            xvar/=n;
            yvar/=n;

            xstd=xStdFilter.filter((float)Math.sqrt(xvar),lastts);
            ystd=yStdFilter.filter((float)Math.sqrt(yvar),lastts);
            
            //==================================================================
            //Some code just to mix these two values in a squared sense for the
            //sake of filtering
               double xystd = Math.sqrt(xstd*xstd + ystd*ystd);
            //==================================================================


        medianPoint.setLocation(xmedian, ymedian);
        meanPoint.setLocation(xmean,ymean);
        stdPoint.setLocation(stddev_scale * scale_inc * xstd, stddev_scale * scale_inc * ystd);

        //======================================================================
        //Here we filter points out based on std dev location.  

        checkOutputPacketEventType(in); //This is what actually initiates the out EventPacket of the correct type.
        OutputEventIterator outItr=out.outputIterator();

        for(Object o:in){
                BasicEvent e=(BasicEvent)o;

                double L2dist = Math.sqrt((e.x-xmedian)*(e.x-xmedian) + (e.y-ymedian)*(e.y-ymedian));

                if (L2dist <= stddev_scale * scale_inc * xystd ) {
                    BasicEvent i=(BasicEvent)outItr.nextOutput();
                    i.copyFrom(e);
                }
            }
       //======================================================================
        return out; // xs and ys will now be sorted, output will be bs because time will not be sorted like addresses
    }

    /** JOGL annotation */
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        Point2D p=medianPoint;
        Point2D s=stdPoint;
        GL2 gl=drawable.getGL().getGL2();
        // already in chip pixel context with LL corner =0,0
        
        gl.glPushMatrix();
        gl.glColor3f(0,0,1);
        gl.glLineWidth(4);
        gl.glBegin(GL2.GL_LINE_LOOP);
        gl.glVertex2d(p.getX()-s.getX(), p.getY()-s.getY());
        gl.glVertex2d(p.getX()+s.getX(), p.getY()-s.getY());
        gl.glVertex2d(p.getX()+s.getX(), p.getY()+s.getY());
        gl.glVertex2d(p.getX()-s.getX(), p.getY()+s.getY());
        gl.glEnd();
        gl.glPopMatrix();

    }

}
