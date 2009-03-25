/*
 * Tmpdiff128CochleaCommunication.java
 *
 * Created on July 16, 2007, 10:15 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.holger;

import java.util.concurrent.ArrayBlockingQueue;

/**
 *
 * @author Vaibhav Garg
 */

public class Tmpdiff128CochleaCommunication {
   private static ArrayBlockingQueue blockingQ;
    /** Creates a new instance of Tmpdiff128CochleaCommunication */
    /*public Tmpdiff128CochleaCommunication() {
        setBlockingQ(new ArrayBlockingQueue(100));
    }*/
    
    public static ArrayBlockingQueue getBlockingQ() {
        //ArrayBlockingQueue blockingQ=new ArrayBlockingQueue(100);
        return blockingQ;
    }
    
    /*public static void setBlockingQ(ArrayBlockingQueue aBlockingQ) {
        blockingQ = aBlockingQ;
    }*/
    public static void initBlockingQ()
    {
        blockingQ=new ArrayBlockingQueue(100);
    }
    public static CommunicationObject pollBlockingQ()
    {
        return (CommunicationObject)blockingQ.poll();
    }
    
    public static CommunicationObject peekBlockingQ()
    {
        return (CommunicationObject)blockingQ.peek();
    }
    public static int sizeBlockingQ()
    {
        return blockingQ.size();
    }
    public static void putBlockingQ(CommunicationObject co)
    {
        try {
            blockingQ.put(co);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }
}
