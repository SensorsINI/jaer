/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * This class holds all the static variables which are used to establish a
 * communication between the panTiltThread and the AdressEvent-Filters.
 * 
 * @author Holger
 */
public class PanTilt {
    private static PanTiltThread panTiltThread;
    private static ArrayBlockingQueue blockingQ;
    private static ArrayBlockingQueue blockingQForITDFilter;

    public static void initPanTilt()
    {
        if (panTiltThread == null) {
            panTiltThread = new PanTiltThread();
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

    public static ArrayBlockingQueue getBlockingQForITDFilter() {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return blockingQForITDFilter;
    }

    public static void initBlockingQForITDFilter()
    {
        blockingQForITDFilter=new ArrayBlockingQueue(4);
    }

    public static CommObjForITDFilter pollBlockingQForITDFilter()
    {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return (CommObjForITDFilter)blockingQForITDFilter.poll();
    }

    public static CommObjForITDFilter peekBlockingQForITDFilter()
    {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return (CommObjForITDFilter)blockingQForITDFilter.peek();
    }

    public static CommObjForITDFilter takeBlockingQForITDFilter() throws InterruptedException
    {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return (CommObjForITDFilter)blockingQForITDFilter.take();
    }

    public static int sizeBlockingQForITDFilter()
    {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return blockingQForITDFilter.size();
    }

    public static void putBlockingQForITDFilter(CommObjForITDFilter co)
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

    public static boolean offerBlockingQForITDFilter(CommObjForITDFilter co) {
        if (blockingQForITDFilter == null) {
            initBlockingQForITDFilter();
        }
        return blockingQForITDFilter.offer(co);
    }

    public static ArrayBlockingQueue getBlockingQ() {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return blockingQ;
    }

    public static void initBlockingQ()
    {
        blockingQ=new ArrayBlockingQueue(4);
    }

    public static CommObjForPanTilt pollBlockingQ()
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (CommObjForPanTilt)blockingQ.poll();
    }

    public static CommObjForPanTilt peekBlockingQ()
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (CommObjForPanTilt)blockingQ.peek();
    }

    public static CommObjForPanTilt takeBlockingQ() throws InterruptedException
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (CommObjForPanTilt)blockingQ.take();
    }

    public static int sizeBlockingQ()
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return blockingQ.size();
    }

    public static void putBlockingQ(CommObjForPanTilt co)
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

    public static boolean offerBlockingQ(CommObjForPanTilt co) {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return blockingQ.offer(co);
    }

    /**
     * @return the wasMoving
     */
    public static boolean isWasMoving() {
        return PanTilt.panTiltThread.panTiltFrame.panTiltControl.isWasMoving();
    }

    /**
     * @return the isMoving
     */
    public static boolean isMoving() {
        return PanTilt.panTiltThread.panTiltFrame.panTiltControl.isMoving();
    }

}
