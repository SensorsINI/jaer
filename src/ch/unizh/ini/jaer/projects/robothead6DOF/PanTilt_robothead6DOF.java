/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import ch.unizh.ini.jaer.projects.cochsoundloc.CommObjForPanTilt;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import org.ine.telluride.jaer.tell2011.head6axis.Head6DOF_ServoController;

/**
 * This class holds all the static variables which are used to establish a
 * communication between the panTiltThread and the AdressEvent-Filters.
 *
 * @author Holger
 * see original at ch.unizh.ini.jaer.projects.cochsoundloc
 */
public class PanTilt_robothead6DOF {

    public PanTiltThread_robothead6DOF panTiltThread = null;
    private static final Logger log = Logger.getLogger("PanTilt_robothead6DOF");
    private ArrayBlockingQueue blockingQ;
    private ArrayBlockingQueue blockingQForITDFilter;
    public Head6DOF_ServoController headControl = null;
    public ITDFilter_robothead6DOF iTDFilter;

    public void initPanTilt(ITDFilter_robothead6DOF ITDFilter) {
        iTDFilter = ITDFilter;
        headControl = ITDFilter.headControl;
        if (panTiltThread == null) {
            panTiltThread = new PanTiltThread_robothead6DOF(this);
        }
        if (panTiltThread.isAlive() == false) {
            panTiltThread.start();
        }
        if (blockingQ == null) {
            initBlockingQ();
        }
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
    }
    
    public void doStopPanTiltThread() {
        panTiltThread.exitThread = true;
        panTiltThread = null;
    }

    public ArrayBlockingQueue getBlockingQForITDFilter() {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return blockingQForITDFilter;
    }

    public void initBlockingQForITDFilter() {
        blockingQForITDFilter = new ArrayBlockingQueue(4);
    }

    public CommObjForITDFilter_robothead6DOF pollBlockingQForITDFilter() {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return (CommObjForITDFilter_robothead6DOF) blockingQForITDFilter.poll();
    }

    public CommObjForITDFilter_robothead6DOF peekBlockingQForITDFilter() {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return (CommObjForITDFilter_robothead6DOF) blockingQForITDFilter.peek();
    }

    public CommObjForITDFilter_robothead6DOF takeBlockingQForITDFilter() throws InterruptedException {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return (CommObjForITDFilter_robothead6DOF) blockingQForITDFilter.take();
    }

    public int sizeBlockingQForITDFilter() {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return blockingQForITDFilter.size();
    }

    public void putBlockingQForITDFilter(CommObjForITDFilter_robothead6DOF co) {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        try {
            blockingQForITDFilter.put(co);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    public boolean offerBlockingQForITDFilter(CommObjForITDFilter_robothead6DOF co) {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return blockingQForITDFilter.offer(co);
    }

    public ArrayBlockingQueue getBlockingQ() {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return blockingQ;
    }

    public void initBlockingQ() {
        blockingQ = new ArrayBlockingQueue(4);
    }

    public CommObjForPanTilt pollBlockingQ() {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (CommObjForPanTilt) blockingQ.poll();
    }

    public CommObjForPanTilt peekBlockingQ() {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (CommObjForPanTilt) blockingQ.peek();
    }

    public CommObjForPanTilt takeBlockingQ() throws InterruptedException {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (CommObjForPanTilt) blockingQ.take();
    }

    public int sizeBlockingQ() {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return blockingQ.size();
    }

    public void putBlockingQ(CommObjForPanTilt co) {
        if (blockingQ == null) {
            initBlockingQ();
        }
        try {
            blockingQ.put(co);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    public boolean offerBlockingQ(CommObjForPanTilt co) {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return blockingQ.offer(co);
    }

    /**
     * Finds existing PanTiltThread_robothead6DOF
     *
     * @param myViewer
     * @return PanTilt_robothead6DOF
     */
    public static PanTilt_robothead6DOF findExistingPanTiltThread(AEViewer myViewer) {

        PanTilt_robothead6DOF panTilt = null;
        ArrayList<AEViewer> viewers = myViewer.getJaerViewer().getViewers();
        for (AEViewer v : viewers) {
            AEChip c = v.getChip();
            FilterChain fc = c.getFilterChain();

            //Check for ITDFilters:
            ITDFilter_robothead6DOF itdFilter = (ITDFilter_robothead6DOF) fc.findFilter(ITDFilter_robothead6DOF.class);
            if (itdFilter != null && itdFilter.panTilt != null) {
                panTilt = itdFilter.panTilt;
            }
        }
        return panTilt;
    }
}
