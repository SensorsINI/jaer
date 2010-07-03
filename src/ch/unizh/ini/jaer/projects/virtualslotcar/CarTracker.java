/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;
import java.util.List;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.tracking.ClusterInterface;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class CarTracker extends EventFilter2D implements FrameAnnotater{

    RectangularClusterTracker tracker;
    public CarTracker (AEChip chip){
        super(chip);
        FilterChain filterChain=new FilterChain(chip);
        tracker=new RectangularClusterTracker(chip);

        //probably don't want hardcoded parameters here since this will overwrite user preferences
//        tracker.setMaxNumClusters(1);
//        tracker.setHighwayPerspectiveEnabled(false);
//        tracker.setDynamicSizeEnabled(true);

        filterChain.add(new BackgroundActivityFilter(chip));
        filterChain.add(tracker);
        setEnclosedFilterChain(filterChain);

    }

    @Override
    public EventPacket<?> filterPacket (EventPacket<?> in){
        in=getEnclosedFilterChain().filterPacket(in);
        return in;
    }

    @Override
    public void resetFilter (){
        getEnclosedFilterChain().reset();
    }

    @Override
    public void initFilter (){
    }

    /** Returns the putative car cluster.
     *
     * @return the car cluster, or null if there is no good cluster
     */
    public ClusterInterface getCarCluster(){
        // defines the criteria for a visible car cluster
        // very simple now
        List<RectangularClusterTracker.Cluster> clusters=tracker.getClusters();
        ClusterInterface carCluster=null;
        for(ClusterInterface c:clusters){
            if(c!=null && c.isVisible()) return c;
        }
        return null;
    }

    public void annotate (GLAutoDrawable drawable){
        tracker.annotate(drawable);
    }


}
