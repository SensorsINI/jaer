/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class TestFilterTell2010 extends EventFilter2D implements FrameAnnotater{

    float x, y;
    private float updateRate=0.01f;

    public TestFilterTell2010 (AEChip chip){
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket (EventPacket<?> in){

        for(BasicEvent e:in){
            x=updateRate*e.x+(1-updateRate)*x;
            y=updateRate*e.y+(1-updateRate)*y;
        }
        System.out.println("x="+x+" y="+y);
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
        gl.glColor3f(1,1,1);
        gl.glLineWidth(5);


    }

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
    }

}
