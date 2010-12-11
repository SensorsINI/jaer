/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author tobi
 */
public class MedianTracker extends EventFilter2D implements FrameAnnotater{

    float xm, ym;
    private float rate=getFloat("rate",.1f);
    private int size=getInt("size",10);

    public MedianTracker(AEChip chip) {
        super(chip);
        setPropertyTooltip("rate", "sets update rate for location");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();
        for(BasicEvent e:in){
            xm=(1-rate)*xm+rate*e.x;
            ym=(1-rate)*ym+rate*e.y;
            if(Math.abs(e.x-xm)<size && Math.abs(e.y-ym)<size){
                BasicEvent oe=outItr.nextOutput();
                oe.copyFrom(e);
            }
        }
        return out;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl=drawable.getGL();
        gl.glColor3f(1,1,1);
        gl.glRectf(xm, ym, xm+10, ym+10);
    }

    /**
     * @return the rate
     */
    public float getRate() {
        return rate;
    }

    /**
     * @param rate the rate to set
     */
    public void setRate(float rate) {
        if(rate>1)rate=1;
        this.rate = rate;
        putFloat("rate",rate);
    }

    /**
     * @return the size
     */
    public int getSize() {
        return size;
    }

    /**
     * @param size the size to set
     */
    public void setSize(int size) {
        this.size = size;
        putInt("size",size);
    }

}
