/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc.multichipviewer;

import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * This is a filter for the retina with enclosed cluster tracker. It can send the tracking information to the panTiltThread
 * 
 * @author Holger
 */
public class MultichipRetinaEventProducer extends EventFilter2D implements FrameAnnotater {

    private ArrayBlockingQueue blockingQueue = null;
    
    public MultichipRetinaEventProducer(AEChip chip) {
        super(chip);
        initFilter();
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
            return in;
        }
        //log.info(String.format("timestamp of Retina:%d",in.getFirstTimestamp()));
        if (blockingQueue!=null) {
            //blockingQueue.offer(getChip().getEventExtractor().reconstructRawPacket(in));
            blockingQueue.offer(in.getRawPacket());
        }
        
        return in;
    }

    @Override
    public void resetFilter() {
        initFilter();
    }

    @Override
    public void initFilter() {
    }

    public void annotate(float[][][] frame) {
        //clusterTracker.annotate(frame);
    }

    public void annotate(Graphics2D g) {
        //clusterTracker.annotate(g);
    }

    public void annotate(GLAutoDrawable drawable) {
        //clusterTracker.annotate(drawable);
    }

    public void doFindAEViewerConsumer(){
        ArrayList<AEViewer> viewers = chip.getAeViewer().getJaerViewer().getViewers();
        for (AEViewer v : viewers) {
            if (v.getBlockingQueueInput()!=null)
            {
                blockingQueue = v.getBlockingQueueInput();
                break;
            }
        }
    }
}
