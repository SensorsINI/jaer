/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.davisfx3support;

import java.util.ArrayList;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Renders the Misc events from integrated FPGA filters and trackers.
 * 
 * @author alejandro linarese-barranco
 */
@Description("Renders the Misc events from integrated FPGA filters and trackers")
public class DavisFx3MiscEventsRenderer extends EventFilter2D implements FrameAnnotater{

    public DavisFx3MiscEventsRenderer(AEChip chip) {
        super(chip);
    }
    
    private ArrayList<ApsDvsEvent> drawList=null;

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        GL2 gl=drawable.getGL().getGL2();
        for(ApsDvsEvent e:drawList){
            gl.glColor3f(0,0,1);
            gl.glRectf(e.x-1, e.y-1, e.x+1, e.y+1);
        }
        drawList.clear();
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        for(BasicEvent o:in){
            ApsDvsEvent e=(ApsDvsEvent)o;
            if((e.address&0x7ff)==0) continue;
            drawList.add(e);
        }       
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes); 
        if(yes){
            drawList=new ArrayList(1000);
        }else{
            drawList=null;
        }
    }
    
    
}
