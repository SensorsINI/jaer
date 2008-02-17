/*
 * KoalaControl.java
 *
 * Created on 6. Februar 2008, 11:01
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.robothead.robotcontrol;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * This Class contains all methods used to drive the robot from the ControlFilter. Most methods are similar to the ones in KoalaGui.
 * Semaphor is usead as in the KoalaGui
 * KoalaControl instanciates Koala and also contains an init and close method.
 *
 * TODO: also Pan-Tilt Control should be implemented here?
 *
 * @author jaeckeld
 */
public class KoalaControl {
    
    public static Koala tester;
    public static PanTilt dreher;
    public static boolean SemaphorRS232;
    public static boolean RobotMoving;      // semaphor for is Robot moving
    public static boolean IsThereObstacle;
    
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
    public static boolean detCollision;  // detect Collision
    public static boolean dontMove;      // stop any movements
    
    public static int QuarterTurn = 5580; // gotoMotorPos(QuarterTurn,-QuarterTurn) = 90 deg turn
    
    /** Creates a new instance of KoalaControl */
    public KoalaControl() {
        
        SemaphorRS232=false;
        RobotMoving=false;
        IsThereObstacle=false;
        
        tester = new Koala(true);
        Checker = new ThrdIsRobotMoving();
        Detector = new ThrdDetectCollision();
        Obstacler = new ThrdHandleObstacle();
        Writer = new CoordinatesWriter();
        
        OldSens = new int[16];
        ObstacleSens = new boolean[16];
    }
    
    public static void initiate(int port) {
        boolean BoolBuffer;
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        BoolBuffer = tester.init(port,9600,8,0,2);
        SemaphorRS232 = false;
    }
    
    public void close(){
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        tester.close();
        SemaphorRS232 = false;
    }
    
    public static void turnRobot(int angle){          // ended movement
        if(!dontMove){
            int pos=angle*QuarterTurn/90;
            
            while(SemaphorRS232){}
            SemaphorRS232 = true;
            tester.setMotorPos(0,0);
            SemaphorRS232 = false;
            
            if(registerPath) regCoordTime();    // write pos/time into file
            
            while(SemaphorRS232){}
            SemaphorRS232 = true;
            tester.gotoMotorPos(pos*-1,pos);
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
    
    public static void goRobot(int left, int right){   // ended movement, left/right = pos [cm]
        if(!dontMove){
            left=left*222;
            right=right*222;
            
            while(SemaphorRS232){}
            SemaphorRS232 = true;
            tester.setMotorPos(0,0);
            SemaphorRS232 = false;
            
            if(registerPath) regCoordTime();
            
            while(SemaphorRS232){}
            SemaphorRS232 = true;
            tester.gotoMotorPos(left,right);
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
    
    public static void moveRobot(int left, int right){   // continuous movement, left/right = speed ; NO THREADS
        
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        tester.setMotorSpeeds(left,right);
        SemaphorRS232 = false;

    }
    
    
    
    public static boolean wayClear(int[] OldSens) {
        // Note: wayClear returns "false" if way is blocked and returns actual
        //       sensor values in OldSens[]
        
        int[] Sens = new int[16];
        boolean Clear = true;
        
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        Sens = tester.getSensors();
        SemaphorRS232 = false;
        //for (int i=0;i<16;i++){
            // Stop if object is getting closer and allready passed limit (Thres)
            //if ((Sens[i]-OldSens[i])>0 && Sens[i]>tester.SENSOR_THRES){
            if(Sens[0]>100 || Sens[1]>100 || Sens[2]>100 || Sens[3]>150 || Sens[8]>100 || Sens[9]>100 || Sens[10]>100 || Sens[11]>150)
                Clear = false;
                //ObstacleSens[i]=true;       // the ones that had a collision
            //} else ObstacleSens[i]=false;
        //}
        // return sensor values as old values
        OldSens = Sens;
        return Clear;
    }
    
    public static void setMotorPos(int a, int b){
        
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        tester.setMotorPos(a,b);
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
    
    public static int[] getSensors(){
        while(SemaphorRS232){}
        SemaphorRS232 = true;
        int[] sensors=tester.getSensors();
        SemaphorRS232 = false;
        
        return sensors;
   }
    
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
    
    
    static void handelObstacle(){
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
    
    
}


