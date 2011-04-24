/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.*;
import net.sf.jaer.eventprocessing.filter.*;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Specialized tracker for ball location.
 *
 * @author tobi
 */
public class LabyrinthBallTracker extends EventFilter2D implements FrameAnnotater, Observer{

    public static String getDescription(){ return "Ball tracker for labyrinth game";}

    FilterChain filterChain;
    private RectangularClusterTracker.Cluster ball=null;
    RectangularClusterTracker tracker;
    private Point2D.Float startingLocation=new Point2D.Float(getFloat("startingX",50), getFloat("startingY",100));

    // private fields, not properties
    BasicEvent startingEvent=new BasicEvent();
    int lastTimestamp=0;
    GLCanvas glCanvas=null;
    private ChipCanvas canvas;

    public LabyrinthBallTracker(AEChip chip) {
        super(chip);
        filterChain=new FilterChain(chip);
        filterChain.add(new BackgroundActivityFilter(chip));
        filterChain.add((tracker=new RectangularClusterTracker(chip)));
        tracker.addObserver(this);
        setEnclosedFilterChain(filterChain);
        String s=" Labyrinth Tracker";
        setPropertyTooltip(s, "startingLocation", "pixel location of starting location for tracker cluster");
     if (chip.getCanvas() != null && chip.getCanvas().getCanvas() != null) {
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out=getEnclosedFilterChain().filterPacket(in);
        if(tracker.getNumClusters()>0){
            Cluster c=tracker.getClusters().get(0);
            if(c.isVisible()) ball=c;
        }else{
            ball=null;
        }
        if(!in.isEmpty()) lastTimestamp=in.getLastTimestamp();
        return out;
    }

    @Override
    public void resetFilter() {
        getEnclosedFilterChain().reset();
        // TODO somehow need to spawn an initial cluster at the starting location
        Cluster b=tracker.createCluster(new BasicEvent(lastTimestamp, (short)startingLocation.x, (short)startingLocation.y, (byte)0));
        b.setMass(10000); // some big number
        tracker.getClusters().add(b);
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl=drawable.getGL();

        // annotate starting ball location
      gl.glColor3f(0,0,1);
      gl.glLineWidth(3f);
      gl.glBegin(GL.GL_LINES);
      final int CROSS_SIZE=3;
      gl.glVertex2f(startingLocation.x-CROSS_SIZE, startingLocation.y-CROSS_SIZE);
      gl.glVertex2f(startingLocation.x+CROSS_SIZE, startingLocation.y+CROSS_SIZE);
      gl.glVertex2f(startingLocation.x-CROSS_SIZE, startingLocation.y+CROSS_SIZE);
      gl.glVertex2f(startingLocation.x+CROSS_SIZE, startingLocation.y-CROSS_SIZE);
      gl.glEnd();

      canvas = chip.getCanvas();
        glCanvas = (GLCanvas) canvas.getCanvas();

    }

    /**
     * @return the ball
     */
    public RectangularClusterTracker.Cluster getBall() {
        return ball;
    }

    /**
     * @return the startingLocation
     */
    public Point2D.Float getStartingLocation() {
        return startingLocation;
    }

    /**
     * @param startingLocation the startingLocation to set
     */
    public void setStartingLocation(Point2D.Float startingLocation) {
        this.startingLocation = startingLocation;
        putFloat("startingX",startingLocation.x);
        putFloat("startingY",startingLocation.y);
    }

    @Override
    public void update(Observable o, Object arg) {
        if(arg instanceof UpdateMessage){
            UpdateMessage m=(UpdateMessage)arg;
            callUpdateObservers(m.packet, m.timestamp); // pass on updates from tracker
        }
    }

}
