/*
 * HeadTracker.java
 *
 * Created on October 19, 2007, 10:50 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.headtracker;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.eventprocessing.FilterChain;
import ch.unizh.ini.caviar.eventprocessing.tracking.RectangularClusterTracker;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import javax.media.opengl.GLAutoDrawable;

/**
 * Tracks head and applies active appearance models to extract face parameters.
 *<p>
 *
 * @author alex tureczek
 */
public class HeadTracker extends EventFilter2D implements FrameAnnotater{
    
    RectangularClusterTracker rectangularClusterTracker;
    
    /** Creates a new instance of HeadTracker */
    public HeadTracker(AEChip chip) {
        super(chip);
        rectangularClusterTracker=new RectangularClusterTracker(chip);
        FilterChain chain=new FilterChain(chip);
        setEnclosedFilterChain(chain);
        getEnclosedFilterChain().add(rectangularClusterTracker);
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        rectangularClusterTracker.filterPacket(in);
        return in;
    }


    public void resetFilter() {
    }

    public void initFilter() {
    }

    public Object getFilterState() {
        return null;
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }

    public void annotate(GLAutoDrawable drawable) {
    }
    
}
