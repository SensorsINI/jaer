/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;
import com.sun.opengl.util.GLUT;
import java.awt.BorderLayout;
import java.awt.geom.Rectangle2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.glu.GLU;
import javax.swing.JFrame;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 * Controls a virtual slot car with a track model.
 *
 * @author Michael Pfeiffer
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class VirtualSlotcarRacer extends EventFilter2D implements FrameAnnotater{
    private boolean showTrack = prefs().getBoolean("VirtualSlotCarRacer.showTrack",true);
    private boolean virtualCar = prefs().getBoolean("VirtualSlotCarRacer.virtualCar",true);
    SlotcarFrame trackFrame = null;
    SlotcarTrack trackModel = null;

    CarTracker tracker = null;
    //    private boolean fillsVertically = false, fillsHorizontally = false;

    public VirtualSlotcarRacer (AEChip chip){
        super(chip);
        //        trackModel=new SlotCarTrackModel(this);
        trackModel = new SlotcarTrack();
        doDesignTrack();

        FilterChain filterChain=new FilterChain(chip);
        tracker=new CarTracker(chip);

        filterChain.add(tracker);
        setEnclosedFilterChain(filterChain);

    }

    public void doDesignTrack (){
        if ( trackFrame == null ){
            trackFrame = new SlotcarFrame();
            trackFrame.setVisible(true);
        }
    }

    @Override
    public EventPacket<?> filterPacket (EventPacket<?> in){
        in=getEnclosedFilterChain().filterPacket(in);

        // Q: Do the control of the car here, in annotate, or in a separate thread?

        // TODO: Retrieve car position and speed from filtered events

        // TODO: Call controller to determine new throttle command

        if (virtualCar) {
            // TODO: In virtual car, send throttle command (with timestep?) and compute
            //       new position on track (+ physics, etc.)
        }
        else {
            // TODO: In real car, send command to hardware
        }


        return in;
    }

    @Override
    public void resetFilter (){
    }

    @Override
    public void initFilter (){
    }

    public void annotate (GLAutoDrawable drawable){
        if ( trackFrame != null ){
            trackFrame.repaint();
        }
    }
}
