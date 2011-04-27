/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.geom.Point2D;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;

/**
 *  A virtual ball that generates events and model physics of ball movement.
 * @author Tobi
 */
public class LabyrinthVirtualBall extends EventFilter2DMouseAdaptor{

    public static final String getDescription(){return "Virtual ball for labyrinth game";}
    
    LabyrinthMap map=null;
    LabyrinthBallController controller=null;
    VirtualBall ball=new VirtualBall();
    
//    public LabyrinthVirtualBall(AEChip chip) {
//        super(chip);
//    }

    public LabyrinthVirtualBall(AEChip chip, LabyrinthGame game) {
        super(chip);
        controller=game.controller;
        map=controller.tracker.map;
        checkOutputPacketEventType(BasicEvent.class);
    }

    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        ball.update(in.getLastTimestamp());
        return out;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }
    
    public class VirtualBall {
        public Point2D.Float posPixels=new Point2D.Float();
        public Point2D.Float velPPS=new Point2D.Float(0,0);
        public float radiusPixels=3;
        int lastUpdateTimeUs=0;
        boolean initialized=false;

        public VirtualBall() {
        }
        
        public VirtualBall(Point2D pos) {
            posPixels.setLocation(pos);
        }
        
        public void update(int timeUs){
            if(initialized){
                out.clear();
                Point2D.Float tiltsRad=controller.getTiltsRad();
                float dtSec=AEConstants.TICK_DEFAULT_US*1e-6f*(timeUs-lastUpdateTimeUs);
                float gfac=dtSec*controller.gravConstantPixPerSec2;
                velPPS.x+=tiltsRad.x*gfac;
                velPPS.y+=tiltsRad.y*gfac;
                float dx=velPPS.x*dtSec;
                float dy=velPPS.y*dtSec;
                posPixels.x+=dx;
                posPixels.y+=dy;
                
            }else{
                
            }
            lastUpdateTimeUs=timeUs;
        }
        
    }
    
}
