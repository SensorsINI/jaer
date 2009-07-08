/*
 */
package org.ine.telluride.jaer.tell2009.leah;
import ch.unizh.ini.jaer.chip.cochlea.CochleaChip;
import ch.unizh.ini.jaer.projects.cochsoundloc.ITDBins;
import ch.unizh.ini.jaer.projects.cochsoundloc.ITDFilter;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.*;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 * Filters incoming events with "bumps" of bearing angle
 * that come from cochlea ITD processing.
 *
 *
 *
 * @author leah/tobidelbruck
 */
public class BearingBumpFilter extends EventFilter2D implements Observer,FrameAnnotater{
    private ITDBins itdBins = null;
    private Random random = new Random();
    private ITDFilter itdFilter = null;
        float[] probs=null;

    public static String getDescription (){
        return "Filters incoming events with \"bumps\" of bearing angle that come from ITDBins binaural cochlea sound localization";
    }

    public BearingBumpFilter (AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
        setPropertyTooltip("ConnectToCochleaITDFilter","connects to existing ITDFilter on another AEViewer with a cochlea that is running ITDFilter");
    }

    /**
     * Filters in to out, according to the probabilities coming from ITDBins.
     *@param in input events can be null or empty.
     *@return the the filtered events.
     */
    synchronized public EventPacket filterPacket (EventPacket in){
        if ( itdBins == null ){
            log.warning("ITDBins is null, not filtering");
            return in;
        }
        if ( in == null || in.getSize() == 0 ){
            return in;
        }
        
        int sx=chip.getSizeX();

        // get normalized probabilities
        float max=0;
        float[] bins=itdBins.getBins();
        int nbins=bins.length;
        for(float f:bins){
            if(f>max)max=f;  // get max bin
        }
        if(probs==null){
            probs=new float[sx];
        }
        for(int i=0;i<sx;i++){
            int ind=(int)((float)i/sx*nbins);
            probs[i]=bins[ind]/max;
        }
        checkOutputPacketEventType(in);
        // for each event only write it to the out buffers if it is within dt of the last time an event happened in neighborhood
        OutputEventIterator outItr = out.outputIterator();
        for ( Object e:in ){
            BasicEvent i = (BasicEvent)e;
            float r = random.nextFloat();
            if ( r < probs[i.x] ){
                BasicEvent o = (BasicEvent)outItr.nextOutput();
                o.copyFrom(i);
            }
        }
        return out;
    }

    public Object getFilterState (){
        return null;
    }

    synchronized public void resetFilter (){
    }

    public void update (Observable o,Object arg){
    }

    public void initFilter (){
    }

    public void annotate (float[][][] frame){
    }

    public void annotate (Graphics2D g){
    }

    /** Shows the ITD bins at as trace along bottom of display.
     *
     * @param drawable
     */
    public void annotate (GLAutoDrawable drawable){
        if(!isFilterEnabled()||probs==null) return;
        GL gl = drawable.getGL();
        gl.glPushMatrix();
        gl.glLineWidth(2f);
        gl.glColor3f(0,0,1);
        int sx = chip.getSizeX();
        int sy = chip.getSizeY();
        gl.glBegin(GL.GL_LINE_STRIP);
        for ( int i = 0 ; i < sx ; i++ ){
            gl.glVertex2f(i,sy * probs[i]);
        }
        gl.glEnd();
        gl.glPopMatrix();
    }

    /**
     * Finds the ITDBins object by looking for another AEViewer
     * in the same JVM that has a cochlea
     * chip, then iterates over this chip's filterchain
     * to find ITDFilter. From this ITDFilter it gets the
     * ITDBins, which it uses to filter this filter's events.
     */
    public void doConnectToCochleaITDFilter (){
        try{
            AEViewer myViewer = chip.getAeViewer();
            ArrayList<AEViewer> viewers = myViewer.getJaerViewer().getViewers();
            for ( AEViewer v:viewers ){
                if ( v == myViewer ){
                    continue;
                }
                AEChip c = v.getChip();
                FilterChain fc = c.getFilterChain();
                if ( v.getChip() instanceof CochleaChip ){
                    itdFilter = (ITDFilter)fc.findFilter(ITDFilter.class);
                    itdBins = itdFilter.getITDBins();
                    log.info("found cochlea chip=" + c + " in AEViewer=" + v + " with ITDFilter=" + itdFilter);
                    break;
                }
            }
            if ( itdFilter == null ){
                log.warning("couldn't find ITDFilter anywhere");
            }
        } catch ( Exception e ){
            log.warning("while trying to find ITDFilter caught " + e);
        }
    }
}
