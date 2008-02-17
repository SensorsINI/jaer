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
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
//import com.sun.org.apache.xpath.internal.operations.Mod;
import java.util.*;

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
    
    CorrelatorFilter Listener;
    private int lowPass=getPrefs().getInt("ControlFilter.lowPass",20);
    private int driveSteps=getPrefs().getInt("ControlFilter.driveSteps",1);
    private int minAngle=getPrefs().getInt("ControlFilter.minAngle",15);
    private boolean registerPath=getPrefs().getBoolean("ControlFilter.registerPath",false);
    private boolean detectCollision=getPrefs().getBoolean("ControlFilter.detectCollision",false);
    private boolean stopRobot=getPrefs().getBoolean("ControlFilter.stopRobot",false);
    private boolean connectKoala=getPrefs().getBoolean("ControlFilter.connectKoala",false);
    
    
    public int[] lastAngles;
    Angle myAngle = new Angle(3);
    int actualAngle;
    KoalaControl Controller;
    int countBufferPos;
    int port;
    String state;          // hearing / turning / driving
    
    /** Creates a new instance of ControlFilter */
    public ControlFilter(AEChip chip) {
        
        super(chip);
        
        setPropertyTooltip("lowPass", "Parameter for lowpass filtering");
        setPropertyTooltip("driveSteps", "Robot drives this distance and then listens again [cm]");
        setPropertyTooltip("minAngle", "If detected Angle is below this angle, drive there");
        setPropertyTooltip("registerPath","set to write drive path into file");
        setPropertyTooltip("detectCollision","set to write drive path into file");
        setPropertyTooltip("stopRobot","STOP ROBOT");
        setPropertyTooltip("connectKoala","Connect to the Koala");
        
        Listener = new CorrelatorFilter(chip);
        setEnclosedFilter(Listener);
        
        Controller = new KoalaControl();
        port=6;
        
        resetFilter();
        
        if(isConnectKoala()) 
            Controller.initiate(port);
                
    }
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(!isFilterEnabled()){
            //Controller.close();
            return in;       // only use if filter enabled
        }
        //if(in.getSize()==0) return in;       // do nothing if no spikes came in...., this means empty EventPacket
        checkOutputPacketEventType(in);
        
        if(state=="stop")   return in;
        if(!isConnectKoala()) return in;
        
        if(state=="hearing"){            // STATE HEARING
            
            if(Bins.getSumOfBins()==Listener.getNumberOfPairs()){     // wait till Correlation Buffer is filled
                
                int ANG=Listener.getAngle();                    // get Angle and fill into LowPassBuffer lastAngles
                for(int i=0; i<lastAngles.length-1; i++){
                    lastAngles[i]=lastAngles[i+1];
                }
                lastAngles[lastAngles.length-1]=ANG;
                countBufferPos++;
                
                dispAngles();
                System.out.println("Low passed Angle: "+ getLowPassedAngle());
                
                
                if(countBufferPos==this.lowPass){               // LowPass Buffer filled!
                    actualAngle=this.getLowPassedAngle();       // Angle decided, go now to next state
                    
                    if(Math.abs(actualAngle)>this.minAngle){         //TODO HERE: maybe check if angle = 90 deg, turn and listen again, , first do this in state diagram
                        Controller.turnRobot(actualAngle);
                        state="turning";
                    }else{
                        Controller.goRobot(this.driveSteps,this.driveSteps);
                        state="driving";
                    }
                }
            }
        }
        
        if(state=="turning"){       // waiting for the robot to end moving
            if(!Controller.IsRobotMoving()){        // robot ended moving
                if(Controller.IsThereObstacle)
                    state="obstacle";
                else{
                    resetHearing();     // I like to reset Hearing before starting hearing
                    state="hearing";
                }
            }
        }

        if(state=="driving"){       // waiting for the robot to end moving
            if(!Controller.IsRobotMoving()){        // robot ended moving
                if(Controller.IsThereObstacle)
                    state="obstacle";
                else{
                    resetHearing();     // I like to reset Hearing before starting hearing
                    state="hearing";
                }
            }
        }
        
        if(state=="obstacle"){      // have an obstacle in front of me and need to drive around
            Controller.handelObstacle();
            if(!Controller.IsThereObstacle){    // when it drove around obstacle
                resetHearing();
                state="hearing";
            }
            
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
        
        Controller.registerPath=isRegisterPath();
        Controller.detCollision=isDetectCollision();
        Controller.dontMove=isStopRobot();
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
        Controller.registerPath=this.registerPath;
        if(isRegisterPath())              // reset File every time it is clicked...
            Controller.resetRegFile();
    }
    
    public boolean isDetectCollision(){
        return this.detectCollision;
    }
    public void setDetectCollision(boolean detectCollision){
        this.detectCollision=detectCollision;
        getPrefs().putBoolean("ControlFilter.detectCollision",detectCollision);
        support.firePropertyChange("ControlFilter.detectCollision",this.detectCollision,detectCollision);
        Controller.detCollision=this.detectCollision;
    }
    
    public boolean isStopRobot(){
        return this.stopRobot;
    }
    public void setStopRobot(boolean stopRobot){
        this.stopRobot=stopRobot;
        getPrefs().putBoolean("ControlFilter.stopRobot",stopRobot);
        support.firePropertyChange("ControlFilter.stopRobot",this.stopRobot,stopRobot);
        Controller.dontMove=this.stopRobot;
        Controller.moveRobot(0,0);  // make robot stop
    }
    
    public boolean isConnectKoala(){
        return this.connectKoala;
    }
    public void setConnectKoala(boolean connectKoala){
        this.connectKoala=connectKoala;
        getPrefs().putBoolean("ControlFilter.connectKoala",connectKoala);
        support.firePropertyChange("ControlFilter.connectKoala",this.connectKoala,connectKoala);
        
        if(isConnectKoala()) 
            Controller.initiate(port);
        else Controller.close();
        
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
        Listener.resetFilter();         // reset
        
    }
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    EngineeringFormat fmt=new EngineeringFormat();
    
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL();
        gl.glPushMatrix();
        final GLUT glut=new GLUT();
        gl.glColor3f(1,1,1);
        gl.glRasterPos3f(0,3,3);
        
        if (state=="hearing")  glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("HEARING "));
        if (state=="turning")  glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("TURNING "));
        if (state=="driving")  glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("DRIVING "));
        if (state=="obstacle")  glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("DRIVE AROUND OBSTACLE"));
        
        
        //glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  Angle=%s",ANG));
        gl.glPopMatrix();
    }
    
}
