/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc.multichipviewer;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;


import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent.Ear;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent;
import com.jogamp.opengl.GLAutoDrawable;

/**
 * This is a filter for the retina with enclosed cluster tracker. It can send the tracking information to the panTiltThread
 * 
 * @author Holger
 */
public class MultichipAMS1bEventProducer extends EventFilter2D implements FrameAnnotater {

    private ArrayBlockingQueue blockingQueue = null;
    
    public MultichipAMS1bEventProducer(AEChip chip) {
        super(chip);
        initFilter();
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
            return in;
        }
        //log.info(String.format("timestamp of Cochlea:%d",in.getFirstTimestamp()));

        AEPacketRaw send = new AEPacketRaw();
        send.setNumEvents(in.getSize());
        send.setTimestamps(in.getRawPacket().timestamps);
        int[] sendAddr = new int[in.getSize()];

                //EventPacket outEventPacket = new EventPacket<PolarityEvent>(PolarityEvent.class);
        //OutputEventIterator outItr = outEventPacket.outputIterator();
        int i=0;
        for (Object e : in) {
                CochleaAMSEvent camsevent = ((CochleaAMSEvent) e);
//                sendAddr[i] = 0;
//                sendAddr[i] = sendAddr[i] | (camsevent.x << 1);
//                sendAddr[i] = sendAddr[i] | ((camsevent.y*8) << 8);
//                sendAddr[i] = sendAddr[i] | 0x8000;
//
                
                //PolarityEvent outevt = (PolarityEvent) outItr.nextOutput();
                //outevt.timestamp = camsevent.getTimestamp();
                //outevt.type = (byte) camsevent.getType();
                //outevt.polarity = camsevent.getEar() == Ear.RIGHT ? 1 : 0;
                //outevt.x = camsevent.getX();
                //outevt.y = (short) (camsevent.getY() << 0); //0x63;

                //New Address ranges:
                sendAddr[i] = 0;
                int horzAxis = camsevent.getThreshold();
                if(camsevent.getFilterType() == ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent.FilterType.LPF)
                {
                    horzAxis+=4;
                }
                if(camsevent.getEar() == Ear.RIGHT)
                {
                    horzAxis+=64;
                }
                sendAddr[i] = sendAddr[i] | (horzAxis << 1);
                sendAddr[i] = sendAddr[i] | ((camsevent.x+64) << 8);
                sendAddr[i] = sendAddr[i] | 0x8000;

                i++;
        }
        send.setAddresses(sendAddr);
        if (blockingQueue!=null) {
            //new DVS128
            //EventExtractor2D eventExtractor = new Extractor();
            //blockingQueue.offer(outEventPacket.getRawPacket());
            blockingQueue.offer(send);
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
