/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package capacaccia2015;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Example to compute location
 * @author tobi
 */
@Description("Example to compute mean event location")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MeanTracker extends EventFilter2D implements FrameAnnotater{

    float xMean=0f, yMean=0f;
    protected float alpha=getFloat("alpha", .01f);
    protected float radius=getFloat("radius",10);
    
    public MeanTracker(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        for(BasicEvent e:in){
            if(e.isSpecial() || e.isFilteredOut()) continue;
            xMean=(1-alpha)*xMean+alpha*e.x;
            yMean=(1-alpha)*yMean+alpha*e.y;
            double d=Math.hypot(xMean-e.x, yMean-e.y);
            if(d>radius) e.setFilteredOut(true); else e.setFilteredOut(false);
        }
//        log.info(String.format("%.1f %.1f",xMean,yMean));
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl=drawable.getGL().getGL2();
        gl.glColor3f(1,1,1);
        gl.glLineWidth(4);
        gl.glBegin(GL.GL_LINE_LOOP);
        final int s=(int)radius;
        gl.glVertex2f(xMean-s,yMean-s);
        gl.glVertex2f(xMean+s,yMean-s);
        gl.glVertex2f(xMean+s,yMean+s);
        gl.glVertex2f(xMean-s,yMean+s);
        gl.glEnd();
    }

    /**
     * @return the alpha
     */
    public float getAlpha() {
        return alpha;
    }

    /**
     * @param alpha the alpha to set
     */
    public void setAlpha(float alpha) {
        this.alpha = alpha;
        putFloat("alpha", alpha);
    }

    /**
     * @return the radius
     */
    public float getRadius() {
        return radius;
    }

    /**
     * @param radius the radius to set
     */
    public void setRadius(float radius) {
        this.radius = radius;
        putFloat("radius",radius);
    }
    
}
