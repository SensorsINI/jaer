/*
 * HoughTracker.java
 *
 * Created on 20.11.2006 Damian
 * based on CircularConvolutionFilter.java
 */

package ch.unizh.ini.jaer.projects.eyetracker;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFrame;
import javax.swing.JPanel;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.EventFilterDataLogger;
import net.sf.jaer.graphics.FrameAnnotater;




/**
 * Implements an eye tracker (really a pupil tracker) using event based hough transform methods.
 *@author Damian Gisler
 */
public class HoughEyeTracker extends EventFilter2D implements FrameAnnotater, Observer {
//    Preferences prefs=Preferences.userNodeForPackage(HoughEyeTracker.class);
    short[][] accumulatorArray;
    boolean[][] eyeMaskArray;
    InvEllipseParameter[] irisBufferArray;
    InvEllipseParameter[] pupilBufferArray;
    int bufferIndex = 0;
    int maxValue =0;
    Coordinate event=new Coordinate();
    Coordinate maxCoordinate =new Coordinate();
    float filteredMaxX = 0;
    float filteredMaxY = 0;
    int angleListLength = 18;
    float[] sinTau = new float[angleListLength];
    float[] cosTau = new float[angleListLength];
    Coordinate[] eyeBall = new Coordinate[angleListLength];
    Coordinate[] irisEllipse = new Coordinate[angleListLength];
    Coordinate[] irisCircle = new Coordinate[angleListLength];
    Coordinate[] pupilEllipse = new Coordinate[angleListLength];
    InvEllipseParameter[][] invIrisParameterArray;
    InvEllipseParameter[][] invPupilParameterArray;
    
    EventFilterDataLogger dataLogger;
    JFrame targetFrame;
    DrawPanel  DrawGazePanel;
    
    //temporary vars
    //Coordinate[] eyeMaskFrame = new Coordinate[6];
    
    public boolean isGeneratingFilter(){ return false;}
    
    /** Creates a new instance of TypeCoincidenceFilter */
    public HoughEyeTracker(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        resetFilter();
    }
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter() {
        initTracker();
    }
    
    final class Coordinate{
        public int x, y;
        Coordinate(){
            x=0;
            y=0;
        }
        Coordinate(int x, int y){
            this.x=x;
            this.y=y;
        }
        public void setCoordinate(int x, int y){
            this.x=x;
            this.y=y;
        }
    }
    
    final class InvEllipseParameter{
        int centerX, centerY, AA, BB, twoC, weight;
        InvEllipseParameter(){
            centerX=0;
            centerY=0;
            AA=0;
            BB=0;
            twoC=0;
            weight=0;
        }
        InvEllipseParameter(int centerX, int centerY, int AA, int BB, int twoC, int weight){
            this.centerX=centerX;
            this.centerY=centerY;
            this.AA=AA;
            this.BB=BB;
            this.twoC=twoC;
            this.weight=weight;
        }
    }
    
    synchronized private void initTracker() {
        if(chip.getSizeX()==0 || chip.getSizeY()==0){
//            log.warning("tried to initTracker in HoughTracker but chip size is 0");
            return;
        }
        accumulatorArray=new short[chip.getSizeX()][chip.getSizeY()];
        for(int i=0;i<chip.getSizeX();i++){
            for(int j=0; j < chip.getSizeY();j++){
                accumulatorArray[i][j]=0;
            }
        }
        irisBufferArray=new InvEllipseParameter[bufferLength];
        pupilBufferArray=new InvEllipseParameter[bufferLength];
        for(int i=0;i<bufferLength;i++){
            irisBufferArray[i]= new InvEllipseParameter(-1,-1,0,0,0,0);
            pupilBufferArray[i]= new InvEllipseParameter(-1,-1,0,0,0,0);
        }
        bufferIndex = 0;
        maxValue =0;
        
        // init variables for displaying eye
        float eyeRadius = eyeRadiusMM*focalLength/(float)Math.sqrt(cameraToEyeDistanceMM*cameraToEyeDistanceMM+2*cameraToEyeDistanceMM*eyeRadiusMM); //eq ok dg
        for (int i = 0;i<angleListLength;i++){
            sinTau[i] = (float)(Math.sin(2*Math.PI/angleListLength*i));
            cosTau[i] = (float)(Math.cos(2*Math.PI/angleListLength*i));
            eyeBall[i] = new Coordinate(Math.round(eyeRadius*cosTau[i]+eyeCenterX),Math.round(eyeRadius*sinTau[i]+eyeCenterY));
            irisEllipse[i] = new Coordinate(0,0);
            irisCircle[i] = new Coordinate(0,0);
            pupilEllipse[i] = new Coordinate(0,0);
        }
        
//craete eyeMaskArray: if inside eyeMask -> eyeMask(i,j)=1 else eyeMask(i,j)=0
        eyeMaskArray=new boolean[chip.getSizeX()][chip.getSizeY()];
        invIrisParameterArray = new InvEllipseParameter[chip.getSizeX()][chip.getSizeY()];
        invPupilParameterArray = new InvEllipseParameter[chip.getSizeX()][chip.getSizeY()];
        float zEyeMM = cameraToEyeDistanceMM+eyeRadiusMM;
        float irisRadiusMM = irisRadius*cameraToEyeDistanceMM/focalLength;  // simplification
        float pupilRadiusMM = pupilRadius*cameraToEyeDistanceMM/focalLength; //simplificaton
        float irisPaternRadiusMM = (float)(irisRadiusMM/eyeRadiusMM*Math.sqrt(eyeRadiusMM*eyeRadiusMM-irisRadiusMM*irisRadiusMM));
        float irisPaternDistanceMM = (eyeRadiusMM*eyeRadiusMM-irisRadiusMM*irisRadiusMM)/eyeRadiusMM;
        
        float pupVal = eyeRadiusMM*eyeRadiusMM-pupilRadiusMM*pupilRadiusMM;
        float pupilPaternRadiusMM = (float)Math.sqrt(pupVal/(pupilRadiusMM+pupVal))*pupilRadiusMM;
        float pupilPaternDistanceMM = pupVal/(float)Math.sqrt(pupilRadiusMM+pupVal);
        
        float zEyeNorm = -(cameraToEyeDistanceMM+eyeRadiusMM)/eyeRadiusMM;
        for(int x=0;x<chip.getSizeX();x++){
            for(int y=0; y < chip.getSizeY();y++){
                int deltaX = x-eyeCenterX;
                int deltaY = y-eyeCenterY;
                float deltaR =(float)Math.sqrt(deltaX*deltaX+deltaY*deltaY);
                if( deltaR < eyeRadius-2) {
                    eyeMaskArray[x][y]=true;
                    // calculate cos(phi), sin(phi)
                    float cosPhi=1; float sinPhi = 0;
                    if(deltaR!=0) {cosPhi= deltaX/deltaR; sinPhi = deltaY/deltaR;}
                    // calculate cos(theta) and sin(theta)
                    float deltaRsqrNorm = (deltaX*deltaX+deltaY*deltaY)/(focalLength*focalLength);
                    float cosTheta = (float)((-deltaRsqrNorm*zEyeNorm+Math.sqrt(deltaRsqrNorm+1-deltaRsqrNorm*zEyeNorm*zEyeNorm))/(deltaRsqrNorm+1));
                    float sinTheta = (float)(Math.sqrt(1-cosTheta*cosTheta));
                    
                    float a = focalLength*cosTheta*irisPaternRadiusMM/(zEyeMM-cosTheta*irisPaternDistanceMM);
                    float b = focalLength*irisPaternRadiusMM/(zEyeMM-cosTheta*irisPaternDistanceMM);
                    float aa = a*a;
                    float bb = b*b;
                    
                    int centerX = Math.round(focalLength*cosPhi*irisPaternDistanceMM*sinTheta/(zEyeMM-cosTheta*irisPaternDistanceMM))+eyeCenterX;
                    int centerY = Math.round(focalLength*sinPhi*irisPaternDistanceMM*sinTheta/(zEyeMM-cosTheta*irisPaternDistanceMM))+eyeCenterY;
                    
                    float AADenom = (float)(1/(aa*sinPhi*sinPhi+bb*cosPhi*cosPhi));
                    float BBDenom = (float)(1/(aa*cosPhi*cosPhi+bb*sinPhi*sinPhi));
                    int twoC = Math.round(-2*(aa-bb)*cosPhi*sinPhi*AADenom*BBDenom*aa*bb);
                    int AA = Math.round(aa*bb*AADenom);
                    int BB = Math.round(aa*bb*BBDenom);
                    invIrisParameterArray[x][y] = new InvEllipseParameter(centerX, centerY,AA,BB,twoC,0);
                    
                    // elipse Parameters for pupil
                    a = focalLength*cosTheta*pupilPaternRadiusMM/(zEyeMM-cosTheta*pupilPaternDistanceMM);
                    b = focalLength*pupilPaternRadiusMM/(zEyeMM-cosTheta*pupilPaternDistanceMM);
                    aa = a*a;
                    bb = b*b;
                    
                    centerX = Math.round(focalLength*cosPhi*pupilPaternDistanceMM*sinTheta/(zEyeMM-cosTheta*pupilPaternDistanceMM))+eyeCenterX;
                    centerY = Math.round(focalLength*sinPhi*pupilPaternDistanceMM*sinTheta/(zEyeMM-cosTheta*pupilPaternDistanceMM))+eyeCenterY;
                    
                    AADenom = (float)(1/(aa*sinPhi*sinPhi+bb*cosPhi*cosPhi));
                    BBDenom = (float)(1/(aa*cosPhi*cosPhi+bb*sinPhi*sinPhi));
                    twoC = Math.round(-2*(aa-bb)*cosPhi*sinPhi*AADenom*BBDenom*aa*bb);
                    AA = Math.round(aa*bb*AADenom);
                    BB = Math.round(aa*bb*BBDenom);
                    invPupilParameterArray[x][y] = new InvEllipseParameter(centerX, centerY,AA,BB,twoC,0);
                } else {
                    eyeMaskArray[x][y]=false;
                    invIrisParameterArray[x][y] = new InvEllipseParameter(0,0,0,0,0,0);
                    invPupilParameterArray[x][y] = new InvEllipseParameter(0,0,0,0,0,0);
                }
            }
        }
    }
    
    public void initFilter() {
        resetFilter();
    }
    
    public void update(Observable o, Object arg) {
        if(!isFilterEnabled()) return;
        initFilter();
    }
    
    private float irisRadius=getPrefs().getFloat("HoughTracker.irisRadius",24f);
    public float getIrisRadius() {
        return irisRadius;
    }
    synchronized public void setirisRadius(float irisRadius) {
        if(irisRadius<0) irisRadius=0; else if(irisRadius>chip.getMaxSize()) irisRadius=chip.getMaxSize();
        getPrefs().putFloat("HoughTracker.irisRadius",irisRadius);
        if(irisRadius!=this.irisRadius) {
            resetFilter();
        }
        this.irisRadius = irisRadius;
    }
    
    private float pupilRadius=getPrefs().getFloat("HoughTracker.pupilRadius",7f);
    public float getpupilRadius() {
        return pupilRadius;
    }
    synchronized public void setpupilRadius(float pupilRadius) {
        if(pupilRadius<0) pupilRadius=0; else if(pupilRadius>chip.getMaxSize()) pupilRadius=chip.getMaxSize();
        getPrefs().putFloat("HoughTracker.pupilRadius",pupilRadius);
        if(pupilRadius!=this.pupilRadius) {
            resetFilter();
        }
        this.pupilRadius = pupilRadius;
    }
    
    private float eyeRadiusMM=getPrefs().getFloat("HoughTracker.eyeRadiusMM",12.5f);
    public float geteyeRadiusMM() {
        return eyeRadiusMM;
    }
    synchronized public void seteyeRadiusMM(float eyeRadiusMM) {
        if(eyeRadiusMM<0) eyeRadiusMM=0;
        getPrefs().putFloat("HoughTracker.eyeRadiusMM",eyeRadiusMM);
        if(eyeRadiusMM!=this.eyeRadiusMM) {
            resetFilter();
        }
        this.eyeRadiusMM = eyeRadiusMM;
    }
    
    private float focalLength=getPrefs().getFloat("HoughTracker.focalLength",144f);
    public float getfocalLength() {
        return focalLength;
    }
    synchronized public void setfocalLength(float focalLength) {
        if(focalLength<0) focalLength=0;
        getPrefs().putFloat("HoughTracker.focalLength",focalLength);
        if(focalLength!=this.focalLength) {
            resetFilter();
        }
        this.focalLength = focalLength;
    }
    
    private float cameraToEyeDistanceMM=getPrefs().getFloat("HoughTracker.cameraToEyeDistanceMM",45.0f);
    public float getcameraToEyeDistanceMM() {
        return cameraToEyeDistanceMM;
    }
    synchronized public void setcameraToEyeDistanceMM(float cameraToEyeDistanceMM) {
        if(cameraToEyeDistanceMM<0) cameraToEyeDistanceMM=0;
        getPrefs().putFloat("HoughTracker.cameraToEyeDistanceMM",cameraToEyeDistanceMM);
        if(cameraToEyeDistanceMM!=this.cameraToEyeDistanceMM) {
            resetFilter();
        }
        this.cameraToEyeDistanceMM = cameraToEyeDistanceMM;
    }
    
    private int eyeCenterX=getPrefs().getInt("HoughTracker.eyeCenterX",40);
    public int geteyeCenterX() {
        return eyeCenterX;
    }
    synchronized public void seteyeCenterX(int eyeCenterX) {
        if(eyeCenterX<0) eyeCenterX=0; else if(eyeCenterX>chip.getMaxSize()) eyeCenterX=chip.getMaxSize();
        getPrefs().putInt("HoughTracker.eyeCenterX",eyeCenterX);
        if(eyeCenterX!=this.eyeCenterX) {
            resetFilter();
        }
        this.eyeCenterX = eyeCenterX;
    }
    
    private int eyeCenterY=getPrefs().getInt("HoughTracker.eyeCenterY",40);
    public int geteyeCenterY() {
        return eyeCenterY;
    }
    public void seteyeCenterY(int eyeCenterY) {
        if(eyeCenterY<0) eyeCenterY=0; else if(eyeCenterY>chip.getMaxSize()) eyeCenterY=chip.getMaxSize();
        getPrefs().putInt("HoughTracker.eyeCenterY",eyeCenterY);
        if(eyeCenterY!=this.eyeCenterY) {
            resetFilter();
        }
        this.eyeCenterY = eyeCenterY;
    }
    
    private boolean ellipseTrackerEnabled=getPrefs().getBoolean("HoughTracker.ellipseTrackerEnabled",false);
    
    public boolean isellipseTrackerEnabled() {
        return ellipseTrackerEnabled;
    }
    synchronized public void setellipseTrackerEnabled(boolean ellipseTrackerEnabled) {
        this.ellipseTrackerEnabled = ellipseTrackerEnabled;
        getPrefs().putBoolean("HoughTracker.ellipseTrackerEnabled",ellipseTrackerEnabled);
        resetFilter();
    }
    
    private int irisWeight=getPrefs().getInt("HoughTracker.irisWeight",1);
    public int getirisWeight() {
        return irisWeight;
    }
    synchronized public void setirisWeight(int irisWeight) {
        if(irisWeight < 0) irisWeight=0;
        getPrefs().putInt("HoughTracker.irisWeight",irisWeight);
        if(irisWeight!=this.irisWeight) {
            resetFilter();
        }
        this.irisWeight = irisWeight;
    }
    
    private int pupilWeight=getPrefs().getInt("HoughTracker.pupilWeight",2);
    public int getpupilWeight() {
        return pupilWeight;
    }
    synchronized public void setpupilWeight(int pupilWeight) {
        if(pupilWeight>chip.getSizeX()) pupilWeight=chip.getSizeX();
        getPrefs().putInt("HoughTracker.pupilWeight",pupilWeight);
        if(pupilWeight!=this.pupilWeight) {
            resetFilter();
        }
        this.pupilWeight = pupilWeight;
    }
    
    private int bufferLength=getPrefs().getInt("HoughTracker.bufferLength",200);
    public int getbufferLength() {
        return bufferLength;
    }
    synchronized public void setbufferLength(int bufferLength) {
        if(bufferLength<0) bufferLength=0; else if(bufferLength>5000) bufferLength=5000;
        this.bufferLength = bufferLength;
        getPrefs().putFloat("HoughTracker.bufferLength",bufferLength);
        resetFilter();
    }
    
    private float maxStepSize=getPrefs().getFloat("HoughTracker.maxStepSize",1.3f);
    public float getmaxStepSize() {
        return maxStepSize;
    }
    synchronized public void setmaxStepSize(float maxStepSize) {
        if(maxStepSize<0) maxStepSize=0; else if(maxStepSize>50) maxStepSize=50;
        this.maxStepSize = maxStepSize;
        getPrefs().putFloat("HoughTracker.maxStepSize",maxStepSize);
    }
    
    private int threshold=getPrefs().getInt("HoughTracker.threshold",30);
    public int getthreshold() {
        return threshold;
    }
    synchronized public void setthreshold(int threshold) {
        if(threshold<0) threshold=0; else if(threshold>2000) threshold=2000;
        this.threshold = threshold;
        getPrefs().putInt("HoughTracker.threshold",threshold);
    }
    
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        GL2 gl=drawable.getGL().getGL2();
        // already in chip pixel context with LL corner =0,0
        
        gl.glPushMatrix();
        gl.glColor3f(0,0,1);
        gl.glLineWidth(2);
        
        // draw the elliptic iris
        gl.glBegin(GL2.GL_LINE_LOOP);
        for (int i = 0;i<angleListLength;i++){
            gl.glVertex2d(irisEllipse[i].x, irisEllipse[i].y);
        }
        gl.glEnd();
//        gl.glBegin(GL2.GL_LINE_LOOP);
//        for (int i = 0;i<angleListLength;i++){
//            gl.glVertex2d(irisCircle[i].x, irisCircle[i].y);
//        }
//        gl.glEnd();
//
        
        // draw the elliptic pupil
        gl.glBegin(GL2.GL_LINE_LOOP);
        for (int i = 0;i<angleListLength;i++){
            gl.glVertex2d(pupilEllipse[i].x, pupilEllipse[i].y);
        }
        gl.glEnd();
        
        //eyeball frame
        gl.glBegin(GL2.GL_LINE_LOOP);
        for (int i = 0;i<angleListLength;i++){
            gl.glVertex2d(eyeBall[i].x, eyeBall[i].y);
        }
        gl.glEnd();
        
        //draw statistics
        gl.glLineWidth(5);
        gl.glColor3f(0,0,1);
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex2f(0,1);
        gl.glVertex2f(maxValue,1);
        gl.glColor3f(1,0,0);
        gl.glVertex2f(threshold-1,1);
        gl.glVertex2f(threshold,1);
        gl.glColor3f(1,1,1);
        gl.glVertex2f(maxCoordinate.x-1,maxCoordinate.y);
        gl.glVertex2f(maxCoordinate.x+1,maxCoordinate.y);
        gl.glEnd();
        
        
        gl.glPopMatrix();
    }
    
    // fast inclined ellipse drawing algorithm; ellipse eqn: A*x^2+B*y^2+C*x*y-1 = 0
    // the algorithm is fast because it uses just integer addition and subtraction
    void weightToAccumulator(InvEllipseParameter invEllipse){
        int centerX = invEllipse.centerX;
        int centerY = invEllipse.centerY;
        int AA = invEllipse.AA;
        int BB = invEllipse.BB;
        int twoC = invEllipse.twoC;
        int weight = invEllipse.weight;
        
        int x = 0;
        int y = Math.round((float)Math.sqrt(BB));
        int twoAA = 2*AA;
        int twoBB = 2*BB;
        int dx =   twoAA*y + twoC*x;   //slope =dy/dx
        int dy = -(twoBB*x + twoC*y);
        int ellipseError = AA*(y*y-BB);
        
// first sector: (dy/dx > 1) -> y+1 (x+1)
// d(x,y+1)   = 2a^2y+a^2+2cx                = dx+AA
// d(x+1,y+1) = 2b^2x+b^2+2cy+2c+2a^2y+a^2+2cx = d(x,y+1)-dy+BB
        while (dy > dx){
            addWightToAccumulator(centerX+x,centerY+y,weight);
            addWightToAccumulator(centerX-x,centerY-y,weight);
            ellipseError = ellipseError + dx + AA;
            dx = dx + twoAA;
            dy = dy - twoC;
            y = y + 1;
            if (2*ellipseError-dy+BB > 0) {
                ellipseError = ellipseError - dy + BB ;
                dx = dx + twoC;
                dy = dy - twoBB;
                x = x + 1;
            }
        }
        
// second sector: (dy/dx > 0) -> x+1 (y+1)
// d(x+1,y)   = 2b^2x+b^2+2cy                = -dy+BB
// d(x+1,y+1) = 2b^2x+b^2+2cy+2c+2a^2y+a^2+2cx = d(x+1,y)+dx+AA
        while (dy > 0){
            addWightToAccumulator(centerX+x,centerY+y,weight);
            addWightToAccumulator(centerX-x,centerY-y,weight);
            ellipseError = ellipseError - dy + BB;
            dx = dx + twoC;
            dy = dy - twoBB;
            x = x + 1;
            if (2*ellipseError + dx + AA < 0){
                ellipseError = ellipseError + dx + AA ;
                dx = dx + twoAA;
                dy = dy - twoC;
                y = y + 1;
            }
        }
        
// third sector: (dy/dx > -1) -> x+1 (y-1)
// d(x+1,y)   = 2b^2x+b^2+2cy                = -dy+BB
// d(x+1,y-1) = 2b^2x+b^2+2cy-2c-2a^2y+a^2-2cx = d(x+1,y)-dx+AA
        while (dy > - dx){
            addWightToAccumulator(centerX+x,centerY+y,weight);
            addWightToAccumulator(centerX-x,centerY-y,weight);
            ellipseError = ellipseError - dy + BB;
            dx = dx + twoC;
            dy = dy - twoBB;
            x = x + 1;
            if (2*ellipseError - dx + AA > 0){
                ellipseError = ellipseError - dx + AA;
                dx = dx - twoAA;
                dy = dy + twoC;
                y = y - 1;
            }
        }
        
// fourth sector: (dy/dx < 0) -> y-1 (x+1)
// d(x,y-1)   = -2a^2y+a^2-2cx               = -dx+AA
// d(x+1,y-1) = 2b^2x+b^2+2cy-2c-2a^2y+a^2-2cx = d(x+1,y)-dy+BB
        while (dx > 0){
            addWightToAccumulator(centerX+x,centerY+y,weight);
            addWightToAccumulator(centerX-x,centerY-y,weight);
            ellipseError = ellipseError - dx + AA;
            dx = dx - twoAA;
            dy = dy + twoC;
            y = y - 1;
            if (2*ellipseError - dy + BB < 0){
                ellipseError = ellipseError - dy + BB;
                dx = dx + twoC;
                dy = dy - twoBB;
                x = x + 1;
            }
        }
        
//fifth sector (dy/dx > 1) -> y-1 (x-1)
// d(x,y-1)   = -2a^2y+a^2-2cx                = -dx+AA
// d(x-1,y-1) = -2b^2x+b^2-2cy+2c-2a^2y+a^2-2cx = d(x+1,y)+dy+BB
        while ((dy < dx)&& (x > 0)){
            addWightToAccumulator(centerX+x,centerY+y,weight);
            addWightToAccumulator(centerX-x,centerY-y,weight);
            ellipseError = ellipseError - dx + AA;
            dx = dx - twoAA;
            dy = dy + twoC;
            y = y - 1;
            if (2*ellipseError + dy + BB > 0){
                ellipseError = ellipseError  + dy + BB;
                dx = dx - twoC;
                dy = dy + twoBB;
                x = x - 1;
            }
        }
        
// sixth sector: (dy/dx > 0) -> x-1 (y-1)
// d(x-1,y)   = -2b^2x+b^2-2cy                = dy+BB
// d(x-1,y-1) = -2b^2x+b^2-2cy+2c-2a^2y+a^2-2cx = d(x+1,y)-dx+AA
        while ((dy < 0)&& (x > 0)){
            addWightToAccumulator(centerX+x,centerY+y,weight);
            addWightToAccumulator(centerX-x,centerY-y,weight);
            ellipseError = ellipseError + dy + BB;
            dx = dx - twoC;
            dy = dy + twoBB;
            x = x - 1;
            if (2*ellipseError - dx + AA < 0){
                ellipseError = ellipseError  - dx + AA;
                dx = dx - twoAA;
                dy = dy + twoC;
                y = y - 1;
            }
        }
        
// seventh sector: (dy/dx > -1) -> x-1 (y+1)
// d(x-1,y)   = -2b^2x+b^2-2cy                = dy+BB
// d(x-1,y+1) = -2b^2x+b^2-2cy-2c+2a^2y+a^2+2cx = d(x+1,y)-dx+AA
        while ((dy < - dx)&& (x > 0)){
            addWightToAccumulator(centerX+x,centerY+y,weight);
            addWightToAccumulator(centerX-x,centerY-y,weight);
            ellipseError = ellipseError + dy + BB;
            dx = dx - twoC;
            dy = dy + twoBB;
            x = x - 1;
            if (2*ellipseError + dx + AA > 0){
                ellipseError = ellipseError  + dx + AA;
                dx = dx + twoAA;
                dy = dy - twoC;
                y = y + 1;
            }
        }
        
// eight sector: (dy/dx < 0) -> y+1 (x-1)
// d(x,y+1)   = 2a^2y+a^2+2cx                 = dx+AA
// d(x-1,y+1) = -2b^2x+b^2-2cy-2c+2a^2y+a^2+2cx = d(x,y+1)+dy+BB
        while ((dy > 0 && dx < 0)&& (x > 0)){
            addWightToAccumulator(centerX+x,centerY+y,weight);
            addWightToAccumulator(centerX-x,centerY-y,weight);
            ellipseError = ellipseError + dx + AA;
            dx = dx + twoAA;
            dy = dy - twoC;
            y = y + 1;
            if (2*ellipseError + dy + BB < 0){
                ellipseError = ellipseError + dy + BB ;
                dx = dx - twoC;
                dy = dy + twoBB;
                x = x - 1;
            }
        }
    }
    
    void addWightToAccumulator(int x, int y, int weight){
        if(x >= 0 && x <= chip.getSizeX()-1 && y >= 0 && y <= chip.getSizeY()-1){
            if(eyeMaskArray[x][y]){
                accumulatorArray[x][y] = (short)(accumulatorArray[x][y]+weight);
                if (maxCoordinate.x == x && maxCoordinate.y == y && weight < 0) {
                    maxValue = maxValue+weight;
                }
                if (accumulatorArray[x][y] >= maxValue && weight > 0) {
                    maxValue = accumulatorArray[x][y];
                    if (maxValue > threshold && (x!=maxCoordinate.x || y!=maxCoordinate.y)) {
                        
                        if (x-filteredMaxX > maxStepSize) filteredMaxX=filteredMaxX+maxStepSize;
                        else if (x-filteredMaxX > - maxStepSize) filteredMaxX = x;
                        else filteredMaxX = filteredMaxX-maxStepSize;
                        
                        if (y-filteredMaxY > maxStepSize) filteredMaxY=filteredMaxY+maxStepSize;
                        else if (y-filteredMaxY>-maxStepSize) filteredMaxY = y;
                        else filteredMaxY = filteredMaxY-maxStepSize;
                        
                        maxCoordinate.setCoordinate(x,y);
                        
                        float eyeRadius = eyeRadiusMM*focalLength/(float)Math.sqrt(cameraToEyeDistanceMM*cameraToEyeDistanceMM+2*cameraToEyeDistanceMM*eyeRadiusMM);
                        int deltaX = (int)filteredMaxX-eyeCenterX;
                        int deltaY = (int)filteredMaxY-eyeCenterY;
                        float rdelta =(float)Math.sqrt(deltaX*deltaX+deltaY*deltaY);
                        float cosPhi=1;
                        float sinPhi = 0;
                        if(rdelta!=0) {cosPhi= deltaX/rdelta; sinPhi = deltaY/rdelta;}
                        float eyeCenterToIrisDistanceSqr = eyeRadius*eyeRadius-irisRadius*irisRadius;
                        float cosThetaSqr = (1-rdelta*rdelta/eyeCenterToIrisDistanceSqr);
                        
                        //                        float irisRadiusMM = irisRadius*cameraToEyeDistanceMM/focalLength;
                        //                        float zEyeNorm = -(cameraToEyeDistanceMM+eyeRadiusMM)/(float)Math.sqrt(eyeRadiusMM*eyeRadiusMM-irisRadiusMM*irisRadiusMM);
                        //                        float deltaRsqrNorm = (deltaX*deltaX+deltaY*deltaY)/(focalLength*focalLength);
                        //                        float cosTheta = (float)((-deltaRsqrNorm*zEyeNorm+Math.sqrt(deltaRsqrNorm+1-deltaRsqrNorm*zEyeNorm*zEyeNorm))/(deltaRsqrNorm+1));
                        //                        float cosThetaSqr = cosTheta*cosTheta;
                        //                        float sinTheta = (float)(Math.sqrt(1-cosTheta*cosTheta));
                        
                        //                        float a = focalLength*cosTheta*irisPaternRadiusMM/(zEyeMM-cosTheta*irisPaternDistanceMM);
                        //                        float b = focalLength*irisPaternRadiusMM/(zEyeMM-cosTheta*irisPaternDistanceMM);
                        
                        for (int i = 0;i<angleListLength;i++){
                            float sinTauMinusPhi = sinTau[i]*cosPhi - cosTau[i]*sinPhi;
                            float r = (float)(1/Math.sqrt(Math.abs((1-(1-cosThetaSqr)*sinTauMinusPhi*sinTauMinusPhi)/cosThetaSqr)));
                            irisEllipse[i] = new Coordinate(Math.round(irisRadius*r*cosTau[i]+(int)filteredMaxX),Math.round(irisRadius*r*sinTau[i]+(int)filteredMaxY));
                            irisCircle[i] = new Coordinate(Math.round(irisRadius*cosTau[i]+(int)filteredMaxX),Math.round(irisRadius*sinTau[i]+(int)filteredMaxY));
                            pupilEllipse[i] = new Coordinate(Math.round(pupilRadius*r*cosTau[i]+(int)filteredMaxX),Math.round(pupilRadius*r*sinTau[i]+(int)filteredMaxY));
                        }
                    }
                }
            }
        }
    }
    
    
    
    
    void removePointFromAccumulator(int x, int y){
        if(x>=0&&x<=chip.getSizeX()-1&&y>=0&&y<=chip.getSizeY()-1){
            if(eyeMaskArray[x][y]){
                accumulatorArray[x][y] = (short)(accumulatorArray[x][y]-1);
                if (maxCoordinate.x == x && maxCoordinate.y == y) {
                    maxValue = maxValue-1;
                }
            }
        }
    }
    
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!isFilterEnabled()) return in;
        if(in==null || in.getSize()==0) return in;
        int AA, BB, twoC=0, centerX=0, centerY=0;
        for(Object o:in){
            BasicEvent ev=(BasicEvent)o;
            event.x = ev.x;
            event.y = ev.y;
            if(event.x < 0 && event.x > chip.getSizeX()-1 && event.y < 0 && event.y > chip.getSizeY()-1) continue;
            if(!eyeMaskArray[event.x][event.y]) continue;
            
            //check if ellipse tracker enabeld -> if not circle tracker
            //load iris parameters for event
            if(isellipseTrackerEnabled()){
                irisBufferArray[bufferIndex]=invIrisParameterArray[event.x][event.y];
            }else{
                irisBufferArray[bufferIndex].AA = Math.round(irisRadius*irisRadius);
                irisBufferArray[bufferIndex].BB = Math.round(irisRadius*irisRadius);
                irisBufferArray[bufferIndex].twoC = 0;
                irisBufferArray[bufferIndex].centerX = event.x;
                irisBufferArray[bufferIndex].centerY = event.y;
            }
            irisBufferArray[bufferIndex].weight=irisWeight;
            
            
            //load pupil parameters for event
            if(isellipseTrackerEnabled()){
                pupilBufferArray[bufferIndex]=invPupilParameterArray[event.x][event.y];
            } else{
                pupilBufferArray[bufferIndex].AA = Math.round(pupilRadius*pupilRadius);
                pupilBufferArray[bufferIndex].BB = Math.round(pupilRadius*pupilRadius);
                pupilBufferArray[bufferIndex].twoC = 0;
                pupilBufferArray[bufferIndex].centerX = event.x;
                pupilBufferArray[bufferIndex].centerY = event.y;
            }
            pupilBufferArray[bufferIndex].weight=pupilWeight;
            
            weightToAccumulator(irisBufferArray[bufferIndex]);
            weightToAccumulator(pupilBufferArray[bufferIndex]);
            bufferIndex = (bufferIndex+1)%bufferLength;
            // wait until the ringbuffer is filled
            if(irisBufferArray[bufferIndex].centerX >= 0||irisBufferArray[bufferIndex].centerY >= 0) {
                irisBufferArray[bufferIndex].weight=-irisWeight;
                weightToAccumulator(irisBufferArray[bufferIndex]);
            }
            // wait until the ringbuffer is filled
            if(pupilBufferArray[bufferIndex].centerX >= 0||pupilBufferArray[bufferIndex].centerY >= 0) {
                pupilBufferArray[bufferIndex].weight=-pupilWeight;
                weightToAccumulator(pupilBufferArray[bufferIndex]);
            }
        }
        if(isLogDataEnabled()) dataLogger.log(String.format("%d %f %f", in.getLastTimestamp(), filteredMaxX, filteredMaxY));
        return in;
    }
    
    
    private boolean logDataEnabled=false;
    
    synchronized public boolean isLogDataEnabled() {
        return logDataEnabled;
    }
    
    synchronized public void setLogDataEnabled(boolean logDataEnabled) {
        this.logDataEnabled = logDataEnabled;
        if(dataLogger==null) dataLogger=new EventFilterDataLogger(this,"# targetX targetY eyeX eyeY");
        dataLogger.setEnabled(logDataEnabled);
        if(logDataEnabled){
            targetFrame=new JFrame("EyeTargget");
            DrawGazePanel= new DrawPanel();
            //targetFrame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
            targetFrame.setLocation( 0, 0 );
            targetFrame.setSize( Toolkit.getDefaultToolkit().getScreenSize() );
            targetFrame.add( DrawGazePanel );
            targetFrame.setVisible(true);
            targetFrame.addKeyListener( new KeyListener() {
                public void keyTyped( KeyEvent e ) {
//                    System.out.println( "typed " + e.getKeyChar() );
//                    System.out.println( "typed " + e.getKeyCode() );
                    DrawGazePanel.newPosition();
                }
                public void keyPressed( KeyEvent e ) {}
                public void keyReleased( KeyEvent e ) { }
            });
        }else{
            targetFrame.setVisible(false);
        }
    }
    
    class DrawPanel extends JPanel {
        int width=targetFrame.getSize().width;
        int height=targetFrame.getSize().height;
        int x = 50;
        int y = (int)(height/2);
        int count = 0;
        int w = 1;
        @Override
        protected void paintComponent( Graphics g ) {
            width=targetFrame.getSize().width;
            height=targetFrame.getSize().height;
            x = 50 + (int)(count*(width-100)/2);;
            y = 50 + (int)(count*(height-100)/2);
            super.paintComponent( g );
            g.fillRect(x,y,10,10);
        }
        public void newPosition() {
            if(isLogDataEnabled() ){
                dataLogger.log(String.format("%d %d %f %f", x, y, filteredMaxX, filteredMaxY));
            }
            
            
            count = (count+w)%3;
            if (count>1) w=-1;
            if (count<1) w=+1;
//            int randX = (int)(Math.random()*3);
//            int randY = (int)(Math.random()*2);
//            x = width/4*(randX-1)+width/2;
//            y = (height/8)*(randY*2-1)+height/2;
            
            repaint();
        }
    }
}
