/*
 * ControlFilter.java
 *
 * Created on 4. Februar 2008, 10:36
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.robothead.robotcontrol;

import ch.unizh.ini.robothead.*;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.FilterChain;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
//import com.sun.org.apache.xpath.internal.operations.Mod;
import java.util.*;
import ch.unizh.ini.robothead.retinacochlea.*;
import ch.unizh.ini.robothead.*;

import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import ch.unizh.ini.caviar.util.EngineeringFormat;
import java.io.*;






/**
 *
 * @author Administrator
 */
public class ControlFilter extends EventFilter2D implements Observer, FrameAnnotater {
    
    private int lowPass=getPrefs().getInt("ControlFilter.lowPass",5);
    private int driveSteps=getPrefs().getInt("ControlFilter.driveSteps",20);
    private int driveSpeed=getPrefs().getInt("ControlFilter.driveSpeed",20);
    private int minAngle=getPrefs().getInt("ControlFilter.minAngle",15);
    private boolean registerPath=getPrefs().getBoolean("ControlFilter.registerPath",false);
    private boolean detectCollision=getPrefs().getBoolean("ControlFilter.detectCollision",false);
    private boolean stopRobot=getPrefs().getBoolean("ControlFilter.stopRobot",false);
    private boolean connectKoala=getPrefs().getBoolean("ControlFilter.connectKoala",false);
    
    KoalaControl controller;
    PanTilt dreher;
    
    
    public int[] lastAngles;
    Angle myAngle = new Angle(3);
    int actualAngle;
    int countBufferPos;
    int port;
    static String state;          // hearing / turning / driving
    
    // all the Filters needed
    
    RotateRetinaFilter rotator;
    
    FilterChain soundFilterChain;
    FilterChain lightFilterChain;
    
    RetinaExtractorFilter eye;
    LEDTracker tracker;
    
    CochleaExtractorFilter ear;
    HmmFilter selector;
    CorrelatorFilter correlator;
    
    // calibrator:
    
    CalibrationMachine calibrator;
    
    // Lokking Machine:
    
    LookingMachine viewer;
    
    
    /** Creates a new instance of ControlFilter */
    public ControlFilter(AEChip chip) {
        
        super(chip);
        
        setPropertyTooltip("lowPass", "Parameter for lowpass filtering");
        setPropertyTooltip("driveSteps", "Robot drives this distance and then listens again [cm]");
        setPropertyTooltip("driveSpeed", "Robot drives this distance and then listens again [cm]");
        
        setPropertyTooltip("minAngle", "If detected Angle is below this angle, drive there");
        setPropertyTooltip("registerPath","set to write drive path into file");
        setPropertyTooltip("detectCollision","set to write drive path into file");
        setPropertyTooltip("stopRobot","STOP ROBOT");
        setPropertyTooltip("connectKoala","Connect to the Koala");
        
        // build filter hierarchy:
        
        soundFilterChain = new FilterChain(chip);
        lightFilterChain = new FilterChain(chip);
        
        rotator=new RotateRetinaFilter(chip);
        rotator.setFilterEnabled(true);
        
        eye = new RetinaExtractorFilter(chip) ;
        tracker= new LEDTracker(chip);
        
        ear= new CochleaExtractorFilter(chip);
        selector= new HmmFilter(chip);
        correlator= new CorrelatorFilter(chip);
        
       
        soundFilterChain.add(ear);         // create Filterchain for cochlea
//        soundFilterChain.add(selector);     // uncomment this to insert HmmFilter
        soundFilterChain.add(correlator);
        
        lightFilterChain.add(eye);      // create Filterchain for retina
        lightFilterChain.add(tracker);

        setEnclosedFilterChain(soundFilterChain);
        setEnclosedFilterChain(lightFilterChain);
        
        // initialize Koala
        
        controller = new KoalaControl();
        port=6;
        
        // initialize Pan-Tilt
        
        dreher=new PanTilt();
        int portPT = 7;
        int baud=38400;
        int databits=8;
        int parity=0;
        int stop=1;
        
//        boolean BoolBuf;
//        BoolBuf = dreher.init(portPT,baud,databits,parity,stop);
//        
//        resetFilter();
//        
//        if(isConnectKoala()) 
//            controller.initiate(port);
                
    }
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        if(!isFilterEnabled()){
            //controller.close();
            return in;       // only use if filter enabled
        }
        checkOutputPacketEventType(in);
        
        in=rotator.filterPacket(in);    // this filter rotates only the retina Events for visualization and is always applied 
        
        if(state=="stop")   return in;
        
        if(state=="hearing"){            // STATE HEARING
            soundFilterChain.filterPacket(in);
            if(Bins.getSumOfBins()==correlator.getNumberOfPairs()){     // wait till Correlation Buffer is filled
                
                int ANG=correlator.getAngle();                    // get Angle and fill into LowPassBuffer lastAngles
                for(int i=0; i<lastAngles.length-1; i++){
                    lastAngles[i]=lastAngles[i+1];
                }
                lastAngles[lastAngles.length-1]=ANG;
                countBufferPos++;
                
                dispAngles();
                System.out.println("Low passed Angle: "+ getLowPassedAngle());
                
                
                if(countBufferPos==this.lowPass){               // LowPass Buffer filled!
                    actualAngle=this.getLowPassedAngle();       // Angle decided, go now to next state
                    
                    startLooking();     // always reset before 
                    state="looking";    // always look after hearing

                }
            }
        }
        
        if(state=="looking"){
            lightFilterChain.filterPacket(in);
            boolean finnished;
            finnished=viewer.running(tracker.getLED());
            if(finnished=true){                     // finnished looking
                if (viewer.finalPos==0){    //if LED not found
                                            // do Nothing, stay with actualAngle
                    
                }else{              // LED found!!
                    actualAngle=calkAngle(viewer.finalPos);     // take angle from LED !
                    
                }if(Math.abs(actualAngle)>this.minAngle){         //TODO HERE: maybe check if angle = 90 deg, turn and listen again, , first do this in state diagram
                    controller.regStartCoordTime();
                    controller.turnRobot(actualAngle);
                    state="turning";
                }else{
                    controller.regStartCoordTime();
                    
                    controller.goRobot(this.driveSteps,this.driveSteps);
                    state="driving";
                }
            }
        }
        
        if(state=="turning"){       // waiting for the robot to end moving
            if(!controller.IsRobotMoving()){        // robot ended moving
                controller.regCoordTime();
                        
                if(controller.IsThereObstacle){
                    state="obstacle";
                    controller.handleObstacle();
                }
                else{
                    resetHearing();     // I like to reset Hearing before starting hearing
                    state="hearing";
                }
            }
        }

        if(state=="driving"){       // waiting for the robot to end moving
            if(!controller.IsRobotMoving()){        // robot ended moving
                controller.regCoordTime();
                if(controller.IsThereObstacle){
                    state="obstacle";
                    controller.handleObstacle();
                }
                else{
                    resetHearing();     // I like to reset Hearing before starting hearing
                    state="hearing";
                }
            }
        }
        
        if(state=="obstacle"){      // have an obstacle in front of me and need to drive around
            
            if(!controller.IsThereObstacle){    // when it drove around obstacle
                resetHearing();
                state="hearing";
            }
            
        }
        
        if(state=="calibrate"){
            
            lightFilterChain.filterPacket(in);
            calibrator.running(tracker.getLED());
        }
        
        

        
        return in;
        
    }
    
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter(){
        System.out.println("reset!");
        lastAngles=new int[lowPass];
        
        
        state="hearing";
        resetHearing();
        
        controller.registerPath=isRegisterPath();
        controller.detCollision=isDetectCollision();
        
    }
    
    public void initFilter(){
        System.out.println("init!");
    }
    
    public void update(Observable o, Object arg){
        initFilter();
    }
    
    public int getLowPass(){
        return this.lowPass;
    }
    public void setLowPass(int lowPass){
        getPrefs().putInt("ControlFilter.lowPass",lowPass);
        support.firePropertyChange("lowPass",this.lowPass,lowPass);
        this.lowPass=lowPass;
        
    }
    
    public int getDriveSteps(){
        return this.driveSteps;
    }
    public void setDriveSteps(int driveSteps){
        getPrefs().putInt("ControlFilter.driveSteps",driveSteps);
        support.firePropertyChange("driveSteps",this.driveSteps,driveSteps);
        this.driveSteps=driveSteps;
        
    }
    public int getDriveSpeed(){
        return this.driveSpeed;
    }
    public void setDriveSpeed(int driveSpeed){
        getPrefs().putInt("ControlFilter.driveSpeed",driveSpeed);
        support.firePropertyChange("driveSpeed",this.driveSpeed,driveSpeed);
        this.driveSpeed=driveSpeed;
        
    }
    public int getMinAngle(){
        return this.minAngle;
    }
    public void setMinAngle(int minAngle){
        getPrefs().putInt("ControlFilter.minAngle",minAngle);
        support.firePropertyChange("minAngle",this.minAngle,minAngle);
        this.minAngle=minAngle;
        
    }
    
    public boolean isRegisterPath(){
        return this.registerPath;
    }
    public void setRegisterPath(boolean registerPath){
        this.registerPath=registerPath;
        getPrefs().putBoolean("ControlFilter.registerPath",registerPath);
        support.firePropertyChange("ControlFilter.registerPath",this.registerPath,registerPath);
        controller.registerPath=this.registerPath;
        if(isRegisterPath())              // reset File every time it is clicked...
            controller.resetRegFile();
    }
    
    public boolean isDetectCollision(){
        return this.detectCollision;
    }
    public void setDetectCollision(boolean detectCollision){
        this.detectCollision=detectCollision;
        getPrefs().putBoolean("ControlFilter.detectCollision",detectCollision);
        support.firePropertyChange("ControlFilter.detectCollision",this.detectCollision,detectCollision);
        controller.detCollision=this.detectCollision;
    }
    
    public boolean isStopRobot(){
        return this.stopRobot;
    }
    public void setStopRobot(boolean stopRobot){
        this.stopRobot=stopRobot;
        getPrefs().putBoolean("ControlFilter.stopRobot",stopRobot);
        support.firePropertyChange("ControlFilter.stopRobot",this.stopRobot,stopRobot);
        controller.setSpeeds(0,0);  // make robot stop
        if(isStopRobot())
            state="stop";
        else state="hearing";
    }
    
    public boolean isConnectKoala(){
        return this.connectKoala;
    }
    public void setConnectKoala(boolean connectKoala){
        this.connectKoala=connectKoala;
        getPrefs().putBoolean("ControlFilter.connectKoala",connectKoala);
        support.firePropertyChange("ControlFilter.connectKoala",this.connectKoala,connectKoala);
        
        if(isConnectKoala()) 
            controller.initiate(port);
        else controller.close();
        
    }
        
    public void dispAngles(){
        for (int i=0; i<lastAngles.length; i++){
            System.out.print(lastAngles[i]+" ");
        }
        System.out.print("  ==>  "+getLowPassedAngle()+"\n");
    }
    
    public int getLowPassedAngle(){         // TODO: do better low-passing....
        int[] ang=myAngle.getAngArray();
        int[] storedAngles=new int[ang.length];
        
        for (int i=0; i<lastAngles.length; i++){
            int index=0;
            for(int j=0;j<ang.length;j++){
                if (lastAngles[i]==ang[j]){
                    index=j;
                    storedAngles[j]++;
                }
            }
        }
        int maxInd=0;
        for(int i=0;i<storedAngles.length;i++){
            if (storedAngles[i]>=storedAngles[maxInd])
                maxInd=i;
        }
        return ang[maxInd];
    }
    public void resetHearing(){
        countBufferPos=0;
        lastAngles=new int[lowPass];    // empty LowPass Buffer
        correlator.resetFilter();         // reset
        
    }
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    EngineeringFormat fmt=new EngineeringFormat();
    
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL();
        //gl.glPushMatrix();
        final GLUT glut=new GLUT();
        gl.glColor3f(1,1,1);
        gl.glRasterPos3f(0,3,3);
        
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format(state));
        
        
        //glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  Angle=%s",ANG));
        //gl.glPopMatrix();
    }
    
    public void doCalibration(){
        
        dreher.close();
        calibrator=new CalibrationMachine();
        state="calibrate";
        
    }
    static public void setState(String wyw){
        state=wyw;
    }
    public void startLooking(){
        viewer=new LookingMachine();
    }
    public int calkAngle(double pixelPos){
        
        double minVal=100;
        int minValInd=0;
        for(int i=0;i<calibrator.LUT[0].length;i++){
            double diff=pixelPos-calibrator.LUT[1][i];
            if(java.lang.Math.abs(diff)<minVal){
                minVal=diff;
                minValInd=i;
            }
        }
        return (int)calibrator.LUT[0][minValInd];
        
    }
}

