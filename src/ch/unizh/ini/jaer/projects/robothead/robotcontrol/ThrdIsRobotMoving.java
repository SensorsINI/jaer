/*
 * ThrdIsRobotMoving.java
 *
 * Created on 7. Februar 2008, 21:15
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead.robotcontrol;

/**
 * new: wait 4/5 of time it takes to arrive 
 *
 * @author jaeckeld
 */
public class ThrdIsRobotMoving implements Runnable {
    
    boolean arrived=false;
    
    public void run() {
        
        while(KoalaControl.IsRobotMoving()){
            System.out.println("thread IsRobotMoving starte;d...");
            long startTime = System.currentTimeMillis();
            try {                           // wait till 4/5 of way passed
                Thread.sleep(java.lang.Math.round(KoalaControl.timeToArrive*0.7));          
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            long diff=System.currentTimeMillis()-startTime;
            System.out.println("took me "+diff+" , should have been "+KoalaControl.timeToArrive*0.7);
            if(!KoalaControl.IsThereObstacle)
                KoalaControl.gotoMotorPos(KoalaControl.toGoLeft,KoalaControl.toGoRight);  // now set position to go
            
            while(!arrived){
                try {                           
                    Thread.sleep(300);              // ask every 300 ms
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                int[] speeds = KoalaControl.getSpeeds();
                System.out.println(" Speeds: "+speeds[0]+" "+speeds[1]);
                if(speeds[0]==0 && speeds[1]==0){           // not moving anymore
                    System.out.println("Thread sagt: stopped moving!");
                    
                    // here: As finnished the movement, register new position in relation to last one
//                    if(KoalaControl.registerPath)
//                        KoalaControl.regCoordTime();
                    
                    System.out.println("lalala");
                    arrived=true;
                }
            }
            KoalaControl.setRobotNotMoving();
                    
        }
    }
    
    
}
