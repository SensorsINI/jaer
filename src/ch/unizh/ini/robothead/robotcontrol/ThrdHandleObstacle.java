/*
 * ThrdHandleObstacle.java
 *
 * Created on 13. Februar 2008, 02:20
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.robothead.robotcontrol;

/**
 * This Thread is started if the robot drived against any obstacle.
 * The Robot drives around the obstacle...
 *
 * @author jaeckeld
 */
public class ThrdHandleObstacle implements Runnable {
    
    boolean FL;
    boolean FSL;
    boolean SL;
    boolean BSL;
    boolean BL;
    
    boolean FR;
    boolean FSR;
    boolean SR;
    boolean BSR;
    boolean BR;
    
    int[] sensors;
    int[] turned;
    
    int isNearThres=100;
    int isNearThresSide=80;
    
    int speed=15;
    int step=5000;
    
    String state;
    
    /** Creates a new instance of ThrdHandleObstacle */
    public void run(){
        state="Overview";
        
        System.out.println("I'm in the obstacle THREAD...");
        
        try {                                   // short break
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        
        while(KoalaControl.IsThereObstacle){
            
            if(state=="Overview"){
                getInfo(KoalaControl.getSensors());
                KoalaControl.dispSensors();
                
                state="turn";
            }
            if(state=="turn"){
                boolean finnished=false;
                if(FL || FSL){    // obstacle on the left, start turning
                    finnished=turn("left");
                } else { finnished=turn("right");
                }
                if(finnished)
                    state="drive";
            }
            
            if (state=="drive"){
                boolean finnished=false;
                finnished=drive();
                if(finnished)
                    state="end";
            }
            
            if (state=="end"){
                
                //now turn back
                
                KoalaControl.goRobot(turned[1],turned[0]);
                while(KoalaControl.IsRobotMoving()){}
                
                KoalaControl.goRobot(step,step);
                while(KoalaControl.IsRobotMoving()){}
                
                KoalaControl.IsThereObstacle=false;     // set this when obstacle handling finnished
                //break;  // finnish thread if end is reached
            }
        }
    }
    
    
    void getInfo(int[] sens){
        
        FL=false;       // reset all infos...
        FSL=false;
        SL=false;
        BSL=false;
        BL=false;
        
        FR=false;
        FSR=false;
        SR=false;
        BSR=false;
        BR=false;
        
        if(sens[0]>isNearThres || sens[1]>isNearThres || sens[2]>isNearThres){
            FL=true;
            System.out.println("something front left!");}
        if(sens[3]>isNearThres){
            FSL=true;
            System.out.println("something front side left!");}
        if(sens[4]>isNearThres || sens[5]>isNearThres){
            SL=true;
            System.out.println("something left!");}
        if(sens[6]>isNearThres){
            BSL=true;
            System.out.println("something back side left!");}
        if(sens[7]>isNearThres){
            BL=true;
            System.out.println("something back left!");}
        if(sens[8]>isNearThres || sens[9]>isNearThres || sens[10]>isNearThres){
            FR=true;
            System.out.println("something fron right!");}
        if(sens[11]>isNearThres){
            FSR=true;
            System.out.println("something front side right!");}
        if(sens[12]>isNearThres || sens[13]>isNearThres){
            SR=true;
            System.out.println("something side right!");}
        if(sens[14]>isNearThres){
            BSR=true;
            System.out.println("something back side right!");}
        if(sens[15]>isNearThres){
            BR=true;
            System.out.println("something back right!");}
        
    }
    boolean turn(String side){
        boolean FrontClear=false;
        if(KoalaControl.registerPath){
            KoalaControl.setMotorPos(0,0);
            KoalaControl.regCoordTime();
        }
        if(side=="left")  KoalaControl.moveRobot(speed*-1,speed);
        else    KoalaControl.moveRobot(speed,speed*-1);
        
        while(!FrontClear){
            try {
                Thread.sleep(200);          // ask every 200 ms
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            sensors = KoalaControl.getSensors();
            getInfo(sensors);
            if(!FL && !FSL && !FR && !FSR){                    // front clear, stop turning
                KoalaControl.moveRobot(0,0);
                turned=KoalaControl.getMotorPos();
                if(KoalaControl.registerPath){
                    KoalaControl.regCoordTime();
                }
                System.out.println("Front clear now!!!");
                FrontClear=true;
                
            }
        }
        return true;
        
    }
    boolean drive(){
        boolean SideClear=false;
        if(KoalaControl.registerPath){
            KoalaControl.setMotorPos(0,0);
            KoalaControl.regCoordTime();
        }
        KoalaControl.moveRobot(speed,speed);    // drive foreward
        
        while(!SideClear){
            try {
                Thread.sleep(200);          // ask every 200 ms
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            sensors = KoalaControl.getSensors();
            getInfo(sensors);
            if(!FL || !FR)
                KoalaControl.moveRobot(0,0);
                if(KoalaControl.registerPath){
                    KoalaControl.regCoordTime();
                }
                System.out.println("Stop!! TODO: this case...");
                
                SideClear=true;
            if(!SL && !FSL && !SR && !FSR){                    // front clear, stop turning
                KoalaControl.moveRobot(0,0);
                if(KoalaControl.registerPath){
                    KoalaControl.regCoordTime();
                }
                SideClear=true;
            }
        }
        return true;
    }
    
    
    
}
