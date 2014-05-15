/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.brainfair;

import java.util.Observable;
import javax.media.opengl.GL;
import net.sf.jaer.event.ApsDvsOrientationEvent;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.label.DvsOrientationFilter;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * A simple filter that keeps track of the orientations present in the field
 * of view over some time and displays statistics.
 * @author Michael Pfeiffer
 */

@Description("Displays statistics over recently observed orientations") // adds this string as description of class for jaer GUIs
public class OrientationVisualizer extends EventFilter2D implements FrameAnnotater {

    // Update factor for history of orientations
    private float historyFactor = getFloat("historyFactor", 0.001f);
    
    // History of orientations
    private float[] orientHistory;
    private float[] normOrientHistory;

    private FilterChain filterChain;  // Enclosed Gaussian Tracker filter
    private DvsOrientationFilter orientFilter;

    public OrientationVisualizer(AEChip chip) {
        super(chip);
        
        // Create enclosed filter and filter chain
        orientFilter = new DvsOrientationFilter(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(orientFilter);
        setEnclosedFilterChain(filterChain);
    
        // add this string tooltip to FilterPanel GUI control for filterLength
        setPropertyTooltip("historyFactor", "Update rate for history");
        
        orientHistory = new float[8];
        normOrientHistory = new float[8];
    }
    
    
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!filterEnabled) return in;

        // Helper variables
        int i;
        
        EventPacket<?> nextOut = getEnclosedFilterChain().filterPacket(in);

        if ( in == null ){
            return null;
        }
        
        checkOutputPacketEventType(ApsDvsOrientationEvent.class);

        OutputEventIterator outItr=out.outputIterator();
        
        for (BasicEvent e : nextOut) { // iterate over all input events
            BasicEvent o=(BasicEvent)outItr.nextOutput();
            o.copyFrom(e);

            if (e instanceof ApsDvsOrientationEvent) {
                ApsDvsOrientationEvent oe = (ApsDvsOrientationEvent) e;

                byte orient = oe.orientation;
                if ((orient>=0) && (orient<8)) {
                    for (i=0; i<4; i++)
                        orientHistory[i]*=(1-historyFactor);
                    orientHistory[orient]++;
                }
                
            } // e instanceof ApsDvsOrientationEvent

        } // BasicEvent e

        return in;
        
    }

    @Override
    public void resetFilter() {
        filterChain.reset();
        // Reset history
        for (int i=0; i<8; i++) {
            orientHistory[i]=0.0f;
        }
       
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        float sumHist = 0.0f;
        int i;
        for (i=0; i<8; i++)
            sumHist+=orientHistory[i];
         
            GL gl=drawable.getGL(); // gets the OpenGL GL context. Coordinates are in chip pixels, 0,0 is LL
            float x, y;
            float histWidth = 10;
            float histHeight = 50;
            for (i=0; i<8; i++) {
                gl.glBegin(GL.GL_LINE_LOOP);
                    x = -50.0f + histWidth*i;
                    y = 1.0f + histHeight * ((float) orientHistory[i] / (float) sumHist);
                    gl.glVertex2f(x,1.0f);
                    gl.glVertex2f(x,y);
                    gl.glVertex2f(x+histWidth, y);
                    gl.glVertex2f(x+histWidth, 1.0f);
                    gl.glEnd();
            }
    
    }

    public float getHistoryFactor() {
        return historyFactor;
    }

    public void setHistoryFactor(float historyFactor) {
        this.historyFactor = historyFactor;
        putFloat("historyFactor", historyFactor);
    }
    
    
    
}
