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
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Renders the Misc events from integrated FPGA filters and trackers.
 * 
 * @author alejandro linarese-barranco
 */
@Description("Renders the Misc events from integrated FPGA filters and trackers")
public class DavisFx3MiscEventsRenderer extends EventFilter2D implements FrameAnnotater{
    private ArrayList<ApsDvsEvent> drawList=null;
    private int myeventcounter;
    private boolean ShowDVS = getBoolean("ShowDVS", true);
    private boolean ShowTrackerCM = getBoolean("ShowTrackerCM", true);
    private boolean ShowTrackerCluster = getBoolean("ShowTrackerCluster", true);

    public DavisFx3MiscEventsRenderer(AEChip chip) {
        super(chip);
        setPropertyTooltip("ShowDVS", "Show or hide DVS output.");
	setPropertyTooltip("ShowTrackerCM", "Show or hide Center of Mass output from Tracker filters.");
	setPropertyTooltip("ShowTrackerCluster", "Show or hide highlighted events in the Cluster of the Tracker.");
    }
    
    public boolean isShowDVS() {
	return ShowDVS;
    }

    public void setShowDVS(final boolean ShowDVS) {
        this.ShowDVS = ShowDVS;
	putBoolean("ShowDVS", ShowDVS);
    }

    public boolean isShowTrackerCM() {
	return ShowTrackerCM;
    }

    public void setShowTrackerCM(final boolean ShowTrackerCM) {
        this.ShowTrackerCM = ShowTrackerCM;
	putBoolean("ShowTrackerCM", ShowTrackerCM);
    }

    public boolean isShowTrackerCluster() {
	return ShowTrackerCluster;
    }

    public void setShowTrackerCluster(final boolean ShowTrackerCluster) {
        this.ShowTrackerCluster = ShowTrackerCluster;
	putBoolean("ShowTrackerCluster", ShowTrackerCluster);
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        GL2 gl=drawable.getGL().getGL2();
        for(ApsDvsEvent e:drawList){
            if((e.address&0x7ff)>>8==6) {
                if (isShowTrackerCM()) {
                    gl.glColor3f(0,0,(e.address&0x3)+1);
                    gl.glRectf(e.x-2, e.y-2, e.x+2, e.y+2);
                }
            }
            else if((e.address&0x7ff)>>8==7){
                if (isShowTrackerCluster()) {
                    gl.glColor3f((e.address&0x3)+1,0,(e.address&0x3)+1);
                    gl.glRectf(e.x-2, e.y-2, e.x+2, e.y+2);
                }
            }
            else e.special=false; // this is to do nothing but make a breakpoint to stop.
       
        }
        drawList.clear();
        myeventcounter = 0;
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        OutputEventIterator outItr = out.outputIterator();
        for(BasicEvent o:in){
            BasicEvent oe = (BasicEvent) outItr.nextOutput(); 
            ApsDvsEvent e=(ApsDvsEvent)o;
            if(e.isDVSEvent() && (e.address&0x7ff)!=0) {
                drawList.add(e);
                myeventcounter++;
                oe.copyFrom(o);
            } 
        }       
        return isShowDVS() ? in : out;
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
            drawList=new ArrayList(100000);
        }else{
            drawList=null;
        }
        myeventcounter = 0;
    }
    
    
}
