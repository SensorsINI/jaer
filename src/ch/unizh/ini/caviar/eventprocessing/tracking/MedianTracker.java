/*
 * MedianTracker.java
 *
 * Created on December 4, 2005, 11:04 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.eventprocessing.tracking;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import java.awt.geom.*;
import java.util.Arrays;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;

/**
 * Tracks median event location.
 *
 * @author tobi
 */
public class MedianTracker extends EventFilter2D implements FrameAnnotater {
    
    Point2D medianPoint=new Point2D.Float(),stdPoint=new Point2D.Float(),meanPoint=new Point2D.Float();
    float xmedian=0f;
    float ymedian=0f;
    float xstd=0f;
    float ystd=0f;
    float xmean=0, ymean=0;
    int lastts=0, dt=0;
    int prevlastts=0;
    
    int tauUs=getPrefs().getInt("MedianTracker.tauUs",1000);
    float alpha=1, beta=0; // alpha is current weighting, beta is past value weighting 
    
    /** Creates a new instance of MedianTracker */
    public MedianTracker(AEChip chip) {
        super(chip);
        chip.getCanvas().addAnnotator(this);
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
        getPrefs().putInt("MedianTracker.tauUs", tauUs);
    }

    public void initFilter() {
    }

    
    public EventPacket filterPacket(EventPacket in) {
         if(!isFilterEnabled()) return in;
        int n=in.getSize();
        if(n==0) return in;
//        short[] xs=in.getXs(),ys=in.getYs();
        
        lastts=in.getLastTimestamp();
        dt=lastts-prevlastts;
        prevlastts=lastts;
        // new values are mixed to that if tauUs=0, values are instantaneous
        if(tauUs==0){ //take instaneous values
            alpha=1; 
            beta=0;
        }else{
            beta=(float)Math.exp(-dt/(float)tauUs); // weight of previous value decays exponentially with time since it
            alpha=1-beta; // weight of present value is remainder of weighting
        }
        
        if(n==1){
            xmedian=alpha*in.getEvent(0).x+beta*xmedian;
            ymedian=alpha*in.getEvent(0).y+beta*ymedian;
            xmean=alpha*xmedian+beta*xmean;
            ymean=alpha*ymedian+beta*ymean;
            xstd=beta*xstd;
            ystd=beta*xstd;
        }else{
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
            if(n%2!=0){ // odd number points, take middle one, e.g. n=3, take element 1
                xmedian=alpha*xs[n/2]+beta*xmedian;
                ymedian=alpha*ys[n/2]+beta*ymedian;
            }else{ // even num events, take avg around middle one, eg n=4, take avg of elements 1,2
                float xold=xmedian, yold=ymedian;
                xmedian=(float)(((float)xs[n/2-1]+xs[n/2])/2f);
                ymedian=(float)(((float)ys[n/2-1]+ys[n/2])/2f);
                xmedian=alpha*xmedian+beta*xold;
                ymedian=alpha*ymedian+beta*yold;
            }
            
            int xsum=0,ysum=0;
            for(int i=0;i<n;i++){
                xsum+=xs[i];
                ysum+=ys[i];
            }
            float xold=xmean, yold=ymean;
            xmean=(float)xsum/n;
            ymean=(float)ysum/n;
            xmean=alpha*xmean+beta*xold;
            ymean=alpha*ymean+beta*yold;
            
            
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
            
            xold=xstd;
            yold=ystd;
                 
            xstd=(float)Math.sqrt(xvar);
            ystd=(float)Math.sqrt(yvar);
            
            xstd=alpha*xstd+beta*xold;
            ystd=alpha*ystd+beta*yold;
        }
        
        medianPoint.setLocation(xmedian, ymedian);
        meanPoint.setLocation(xmean,ymean);
        stdPoint.setLocation(xstd,ystd);
        
        return in; // xs and ys will now be sorted, output will be bs because time will not be sorted like addresses
   }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }

    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        Point2D p=medianPoint;
        Point2D s=stdPoint;
        GL gl=drawable.getGL();
        // already in chip pixel context with LL corner =0,0
        gl.glPushMatrix();
        gl.glColor3f(0,0,1);
        gl.glLineWidth(4);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2d(p.getX()-s.getX(), p.getY()-s.getY());
        gl.glVertex2d(p.getX()+s.getX(), p.getY()-s.getY());
        gl.glVertex2d(p.getX()+s.getX(), p.getY()+s.getY());
        gl.glVertex2d(p.getX()-s.getX(), p.getY()+s.getY());
        gl.glEnd();
        gl.glPopMatrix();
    }

}
