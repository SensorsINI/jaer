/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;

/**
 * This class holds all the static variables which are used to establish a
 * communication between the panTiltThread and the AdressEvent-Filters.
 * 
 * @author Holger
 */
public class PanTilt {
    private PanTiltThread panTiltThread;
    private ArrayBlockingQueue blockingQ;
    private ArrayBlockingQueue blockingQForITDFilter;

    public void initPanTilt()
    {
        if (panTiltThread == null) {
            panTiltThread = new PanTiltThread(this);
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
        if (panTiltThread.panTiltFrame.isVisible()==false) {
            panTiltThread.panTiltFrame.setVisible(true);
        }
    }

    public  ArrayBlockingQueue getBlockingQForITDFilter() {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return blockingQForITDFilter;
    }

    public void initBlockingQForITDFilter()
    {
        blockingQForITDFilter=new ArrayBlockingQueue(4);
    }

    public CommObjForITDFilter pollBlockingQForITDFilter()
    {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return (CommObjForITDFilter)blockingQForITDFilter.poll();
    }

    public CommObjForITDFilter peekBlockingQForITDFilter()
    {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return (CommObjForITDFilter)blockingQForITDFilter.peek();
    }

    public CommObjForITDFilter takeBlockingQForITDFilter() throws InterruptedException
    {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return (CommObjForITDFilter)blockingQForITDFilter.take();
    }

    public int sizeBlockingQForITDFilter()
    {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return blockingQForITDFilter.size();
    }

    public void putBlockingQForITDFilter(CommObjForITDFilter co)
    {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        try {
            blockingQForITDFilter.put(co);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    public boolean offerBlockingQForITDFilter(CommObjForITDFilter co) {
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

    public void initBlockingQ()
    {
        blockingQ=new ArrayBlockingQueue(4);
    }

    public CommObjForPanTilt pollBlockingQ()
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (CommObjForPanTilt)blockingQ.poll();
    }

    public CommObjForPanTilt peekBlockingQ()
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (CommObjForPanTilt)blockingQ.peek();
    }

    public CommObjForPanTilt takeBlockingQ() throws InterruptedException
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (CommObjForPanTilt)blockingQ.take();
    }

    public int sizeBlockingQ()
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return blockingQ.size();
    }

    public void putBlockingQ(CommObjForPanTilt co)
    {
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
     * @return the wasMoving
     */
    public boolean isWasMoving() {
        if (panTiltThread.panTiltFrame.panTiltControl != null)
            return panTiltThread.panTiltFrame.panTiltControl.isWasMoving();
        else
            return false;
    }

    /**
     * @return the isMoving
     */
    public boolean isMoving() {
        if (panTiltThread.panTiltFrame.panTiltControl != null)
            return panTiltThread.panTiltFrame.panTiltControl.isMoving();
        else
            return false;
    }

    /**
     * Finds existing PanTiltThread
     *
     * @param myViewer
     * @return PanTilt
     */
    public static PanTilt findExistingPanTiltThread(AEViewer myViewer) {
        
        PanTilt panTilt = null;
        ArrayList<AEViewer> viewers = myViewer.getJaerViewer().getViewers();
        for (AEViewer v : viewers) {
            AEChip c = v.getChip();
            FilterChain fc = c.getFilterChain();

            //Check for ITDFilters:
            ITDFilter itdFilter = (ITDFilter) fc.findFilter(ITDFilter.class);
            if (itdFilter != null && itdFilter.panTilt != null) {
                panTilt = itdFilter.panTilt;
            }

            //Check for DetectMovementFilters:
            DetectMovementFilter detectMovementFilter = (DetectMovementFilter) fc.findFilter(DetectMovementFilter.class);
            if (detectMovementFilter != null && detectMovementFilter.panTilt != null) {
                panTilt = detectMovementFilter.panTilt;
            }
        }
        return panTilt;
    }
}
