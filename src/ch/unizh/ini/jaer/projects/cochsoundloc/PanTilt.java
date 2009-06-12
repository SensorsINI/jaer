/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.util.concurrent.ArrayBlockingQueue;

/**
 *
 * @author Holger
 */
public class PanTilt {
    private static PanTiltThread panTiltThread;
    private static ArrayBlockingQueue blockingQ;

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
        if (panTiltThread.panTiltFrame.isVisible()==false) {
            panTiltThread.panTiltFrame.setVisible(true);
        }
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

    public static FilterOutputObject pollBlockingQ()
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (FilterOutputObject)blockingQ.poll();
    }

    public static FilterOutputObject peekBlockingQ()
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (FilterOutputObject)blockingQ.peek();
    }

    public static FilterOutputObject takeBlockingQ() throws InterruptedException
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return (FilterOutputObject)blockingQ.take();
    }

    public static int sizeBlockingQ()
    {
        if (blockingQ == null) {
            initBlockingQ();
        }
        return blockingQ.size();
    }

    public static void putBlockingQ(FilterOutputObject co)
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

    public static boolean offerBlockingQ(FilterOutputObject co) {
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
