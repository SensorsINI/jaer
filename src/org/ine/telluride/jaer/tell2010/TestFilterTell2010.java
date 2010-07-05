/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.tell2010;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 *  This is a demo filter written just for Telluride 2010 participants.
 *  This filter just computes a running average of the x and y event addresses.
 * 
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class TestFilterTell2010 extends EventFilter2D implements FrameAnnotater{

    float x, y;
    private float updateRate=prefs().getFloat("TestFilterTell2010.updateRate",0.01f);
    BackgroundActivityFilter bgFilter;
    FilterChain filterChain;

    public TestFilterTell2010 (AEChip chip){
        super(chip);
        filterChain=new FilterChain(chip);
        setEnclosedFilterChain(filterChain);
        bgFilter=new BackgroundActivityFilter(chip);
        filterChain.add(bgFilter);
    }

    @Override
    public EventPacket<?> filterPacket (EventPacket<?> in){
        out=bgFilter.filterPacket(in);
        // compute running average
        for(BasicEvent e:in){
            x=updateRate*e.x+(1-updateRate)*x;
            y=updateRate*e.y+(1-updateRate)*y;
        }

        // use the built in EventFilter2D logger to log some output to the console
//        log.info("x="+x+" y="+y);
//        System.out.println("x="+x+" y="+y);
        return in;
        
    }

    @Override
    public void resetFilter (){
    }

    @Override
    public void initFilter (){
    }

    public void annotate (GLAutoDrawable drawable){
        GL gl=drawable.getGL();


        // draw a cross at the average location
        gl.glColor3f(1,1,0);
        gl.glLineWidth(5);
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(x-10, y);
        gl.glVertex2f(x+10,y);
        gl.glVertex2f(x, y-10);
        gl.glVertex2f(x,y+10);
        gl.glEnd();


    }


    // implement a getter/setter pair to expose the property in the Filter GUI

    /**
     * @return the updateRate
     */
    public float getUpdateRate (){
        return updateRate;
    }

    /**
     * @param updateRate the updateRate to set
     */
    public void setUpdateRate (float updateRate){
        this.updateRate = updateRate;
        prefs().putFloat("TestFilterTell2010.updateRate",updateRate);
    }

}
