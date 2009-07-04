/*
 * Demonstrates how to build an event processing filter.
 *
 */

package org.ine.telluride.jaer.tell2009;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 * Demonstratexs how to build a filter.
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class TellDemoFilter extends EventFilter2D implements FrameAnnotater {

    private int xcool=getPrefs().getInt("TellDemoFilter.xcool",0);

    public TellDemoFilter (AEChip chip){
        super(chip);
        setPropertyTooltip("xcool","the x address we're looking for");
    }



    @Override
    public EventPacket<?> filterPacket (EventPacket<?> in){
        if(!isFilterEnabled()) return in;
        for(BasicEvent e:in){
            if(e.x==xcool) System.out.println(e.timestamp);
        }
        return in;
    }

    @Override
    public Object getFilterState (){
        return null;
    }

    @Override
    public void resetFilter (){

    }

    @Override
    public void initFilter (){

    }

    /**
     * @return the xcool
     */
    public int getXcool (){
        return xcool;
    }

    /**
     * @param xcool the xcool to set
     */
    public void setXcool (int xcool){
        this.xcool = xcool;
        getPrefs().putInt("TellDemoFilter.xcool",xcool);
    }

    public void annotate (float[][][] frame){
    }

    public void annotate (Graphics2D g){
    }

    public void annotate (GLAutoDrawable drawable){
        if(!isAnnotationEnabled()) return;
        GL gl=drawable.getGL();
        gl.glBegin(GL.GL_LINES);
        gl.glColor3f(.5f,.5f,0);
        gl.glVertex2f(xcool,0);
        gl.glVertex2f(xcool,chip.getSizeY());
        gl.glEnd();
    }

}
