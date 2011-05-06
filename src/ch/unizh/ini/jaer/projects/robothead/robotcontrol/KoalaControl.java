/*
 * KoalaControl.java
 *
 * Created on 6. Februar 2008, 11:01
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead.robotcontrol;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;


/**
 * This Class contains all methods used to drive the robot from the ControlFilter. Most methods are similar to the ones in KoalaGui.
 * Semaphor is usead as in the KoalaGui
 * KoalaControl instanciates Koala and also contains an init and close method.
 * Also PanTilt Control is integrated
 *
 * TODO: also Pan-Tilt Control should be implemented here?
 *
 * @author jaeckeld
 */
public class KoalaControl {
    
    public static Koala tester;
    public static PanTilt dreher;
    public static boolean SemaphorRS232;
    volatile public static boolean RobotMoving;      // semaphor for is Robot moving
    volatile public static boolean IsThereObstacle;
    
    static public ThrdIsRobotMoving Checker;
    static Thread chkMoving;
    static public ThrdDetectCollision Detector;
    static Thread detCol;
    
    static public ThrdHandleObstacle Obstacler;
    static Thread driveAround;
    
    public static CoordinatesWriter Writer;
    public static int[] OldSens;
    public static boolean[] ObstacleSens;
    
    public static boolean registerPath;  // save driving Path into File???
    public static boolean movingSemaphor;
    
    public static boolean detCollision;  // detect Collision
    
    public static int QuarterTurn = 5580; // gotoMotorPos(QuarterTurn,-QuarterTurn) = 90 deg turn
    
    public static int tooClose = 200;       // threshold values for wayClear method
    
    public static int toGo;
    public static int toGoLeft;
    public static int toGoRight;
    
    public static int timeToArrive;
    
    /** Creates a new instance of KoalaControl */
    public KoalaControl() {
        
        SemaphorRS232=false;
        RobotMoving=false;
        IsThereObstacle=false;
        movingSemaphor=false;
        
        tester = new Koala(true);
        Checker = new ThrdIsRobotMoving();
        Detector = new ThrdDetectCollision();
        Obstacler = new ThrdHandleObstacle();
        Writer = new CoordinatesWriter();
        
        dreher = new PanTilt();
        
        OldSens = new int[16];
        ObstacleSens = new boolean[16];
    }
    
    // initiate and close
    
    public static void initiate(int port) {
        boolean BoolBuffer;
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        BoolBuffer = tester.init(port,9600,8,0,2);
        SemaphorRS232 = false;
        
        try {                           // always takes some time, wait 1 s
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }
    
    public void close(){
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        tester.close();
        SemaphorRS232 = false;
    }
    
    // ALL methods that access to robot
    
    public static void gotoMotorPos(int a, int b){
        
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        tester.gotoMotorPos(a,b);
        SemaphorRS232 = false;
        
    }

    public static void setMotorPos(int a, int b){
        
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        tester.setMotorPos(a,b);
        System.out.println("Positions SET!!!");
        SemaphorRS232 = false;
    }
    
    public static int[] getMotorPos(){
        
        int[] newPos = new int[2];
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        newPos=tester.getMotorPos();
        SemaphorRS232 = false;
        return newPos;
    }
    
    public static int[] getSpeeds(){
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        int[] speeds=tester.getMotorSpeeds();
        SemaphorRS232 = false;
        return speeds;
    }
    
    public static void setSpeeds(int a, int b){
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        tester.setMotorSpeeds(a,b);
        SemaphorRS232 = false;
    }
    
    public static int[] getSensors(){
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        int[] sensors=tester.getSensors();
        SemaphorRS232 = false;
        return sensors;
   }
    
   // methods used in ControlFilter 
    
    public static void turnRobot(int angle){          // ended movement
            int pos=angle*QuarterTurn/90;
            
            timeToArrive=java.lang.Math.abs(java.lang.Math.round(4000/180*angle));      // more or less
            
            //setMotorPos(0,0);
            //if(registerPath) regCoordTime();    // write pos/time into file
            toGoLeft=pos;
            toGoRight=pos*-1;
            
            gotoMotorPos(toGoLeft,toGoRight);   // turn
            
            RobotMoving=true;
            chkMoving = new Thread(Checker);
            chkMoving.start();
            if(detCollision){
                detCol = new Thread(Detector);
                detCol.start();
            }
        
    }

    public static void goRobot(int position, int speed){   // ended straight movement, position [cm]
            System.out.println("go Robot!");
            toGo=position*222;
            toGoLeft=toGo;
            toGoRight=toGo;
            
            setMotorPos(0,0);
//            if(registerPath) regCoordTime();
            
            timeToArrive = java.lang.Math.abs(10*toGo/(speed));     // time to arrive position in ms!
            System.out.println("Time to arrive = "+timeToArrive);
            
            if(timeToArrive<1500){                  // for small movements this is ok as speed doesn't get to big
                gotoMotorPos(toGo,toGo);                
                if(detCollision){
                    detCol = new Thread(Detector);
                    detCol.start();
                }
                
                try {                           // wait the expected time
                    Thread.sleep(timeToArrive);          
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                while(IsRobotMoving()){
                    int[] speeds = KoalaControl.getSpeeds();
                    if(speeds[0]==0 && speeds[1]==0){           // not moving anymore
                        System.out.println("Thread sagt: stopped moving!");
                        //if(KoalaControl.registerPath)    KoalaControl.regCoordTime();
                        KoalaControl.setRobotNotMoving();
                    }
                    try {
                        Thread.sleep(200);          // ask every 200 ms
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
            else{                               // längere Fahrtzeit, mache im speed-Mode
                while(SemaphorRS232){}
                SemaphorRS232 = true;
                if(toGo>=0) tester.setMotorSpeeds(speed,speed);
                else    tester.setMotorSpeeds(-1*speed,-1*speed);
                SemaphorRS232 = false;
                
                RobotMoving=true;
                chkMoving = new Thread(Checker);
                chkMoving.start();
                if(detCollision){
                    detCol = new Thread(Detector);
                    detCol.start();
                }
            }
        
    }
    
    
    public static boolean wayClear(int[] OldSens) {         // TODO better???
        // Note: wayClear returns "false" if way is blocked and returns actual
        //       sensor values in OldSens[]
        
        int[] Sens = new int[16];
        boolean Clear = true;
        
        Sens = getSensors();
        //for (int i=0;i<16;i++){
            // Stop if object is getting closer and allready passed limit (Thres)
            //if ((Sens[i]-OldSens[i])>0 && Sens[i]>tester.SENSOR_THRES){
            if(Sens[0]>tooClose || Sens[1]>tooClose || Sens[2]>tooClose || Sens[3]>tooClose || Sens[8]>tooClose || Sens[9]>tooClose || Sens[10]>tooClose || Sens[11]>tooClose)
                Clear = false;
                //ObstacleSens[i]=true;       // the ones that had a collision
            //} else ObstacleSens[i]=false;
        //}
        // return sensor values as old values
        OldSens = Sens;
        return Clear;
    }
    
    // Accessor Methods for RobotMoving
    
    static boolean IsRobotMoving(){
        return RobotMoving;
    }
    static void setRobotNotMoving(){
        RobotMoving=false;
    }
    static void setRobotMoving(){
        RobotMoving=true;
    }
    
    // methods for saving drivePath
    
    static void regCoordTime() {
        try {
            Writer.registerCoordinates();
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    static void regStartCoordTime() {
        try {
            Writer.registerStartCoordinates();
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    static void resetRegFile(){
        try {
            Writer.resetFile();
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    
    static void handleObstacle(){
        driveAround = new Thread(Obstacler);
        driveAround.start();
    }
    
    static void dispSensors(){
        
        int[] s = getSensors();
        
        for (int i=0; i<s.length;i++){
            System.out.print(s[i]+" ");
        }
        System.out.println("");
    }
    
    // PAN TILT ACCESS
    
    static void initPanTilt(){
        // initialize Pan-Tilt
        
        int portPT = 7;         // PT parameters
        int baud=38400;
        int databits=8;
        int parity=0;
        int stop=1;
        
        boolean BoolBuf;
        BoolBuf = dreher.init(portPT,baud,databits,parity,stop);
    }
    
    static void closePT(){
        dreher.close();
    }
    
    static void setDegreePT(int servo, int degree){
        
        boolean side;
        if (degree>0) side=false;
        else side=true;
        
        dreher.setDegree(servo,java.lang.Math.abs(degree),side);
    }
    
}


