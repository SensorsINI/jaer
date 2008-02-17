/*
 * ThrdIsRobotMoving.java
 *
 * Created on 7. Februar 2008, 21:15
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.robothead.robotcontrol;

/**
 * If Robot is in moving-State, Thread checks every z.B. 500 ms if still moving
 *
 * @author jaeckeld
 */
public class ThrdIsRobotMoving implements Runnable {
    
    public void run() {
        System.out.println("thread started...");
        while(KoalaControl.IsRobotMoving()){
            
            try {                           // wait before checking the speed in order not to get the 0/0 before it started!
                Thread.sleep(500);          // ask every 300 ms
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            int[] speeds = KoalaControl.getSpeeds();
            System.out.println(" Speeds: "+speeds[0]+" "+speeds[1]);
            if(speeds[0]==0 && speeds[1]==0){           // not moving anymore
                KoalaControl.setRobotNotMoving();
                System.out.println("Thread sagt: stopped moving!");
                
                // here: As finnished the movement, register new position in relation to last one
                if(KoalaControl.registerPath)
                    KoalaControl.regCoordTime();                
                
            }
            
        }
    }
    
    
}
