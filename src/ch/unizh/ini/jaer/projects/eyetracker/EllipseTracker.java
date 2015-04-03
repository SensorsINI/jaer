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
 * 
 * Tracks ellipsoidal shapes for eye tracking
 * 
 */
public class EllipseTracker extends EventFilter2D implements FrameAnnotater, Observer {
    //private GazeTarget target;
    JFrame targetFrame; 
    DrawPanel  DrawGazePanel;
    short[][] accumulatorArray;
    boolean[][] eyeMaskArray;
    Coordinate[] bufferArray;
    int bufferIndex = 0;
    int maxConv =0;
    int stepSize = 0;
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
    Coordinate[] eyeMaskFrame = new Coordinate[6];
    InvEllipseParameter[][] invIrisParameterArray;
    InvEllipseParameter[][] invPupilParameterArray;
    EventFilterDataLogger dataLogger;
    
    //temporary vars
    //int invEllipseLength = 200;
    //Coordinate[] invEllipse = new Coordinate[invEllipseLength];
    //int invEllipseIndex = 0;
    
    public boolean isGeneratingFilter(){ return false;}
    
    /** Creates a new instance of TypeCoincidenceFilter */
    public EllipseTracker(AEChip chip) {
        super(chip);
        chip.addObserver(this);
//        target=new GazeTarget();
        resetFilter();
        setFilterEnabled(false);
    }
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter() {
        allocateMap();
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
        int centerX, centerY, AA, BB, twoC;
        InvEllipseParameter(){
            centerX=0;
            centerY=0;
            AA=0;
            BB=0;
            twoC=0;
        }
        InvEllipseParameter(int centerX, int centerY, int AA, int BB, int twoC){
            this.centerX=centerX;
            this.centerY=centerY;
            this.AA=AA;
            this.BB=BB;
            this.twoC=twoC;
        }
    }
    
    private void allocateMap() {
        if(chip.getSizeX()==0 || chip.getSizeY()==0){
//            log.warning("tried to allocateMap in HoughTracker but chip size is 0");
            return;
        }
        accumulatorArray=new short[chip.getSizeX()][chip.getSizeY()];
        for(int i=0;i<chip.getSizeX();i++){
            for(int j=0; j < chip.getSizeY();j++){
                accumulatorArray[i][j]=0;
            }
        }
        bufferArray=new Coordinate[bufferLength];
        for(int i=0;i<bufferLength;i++){
            Coordinate coord = new Coordinate(-1,-1);
            bufferArray[i]= coord;
        }
        bufferIndex = 0;
        maxConv =0;
        float eyeRadius = eyeRadiusMM*focus/(float)Math.sqrt(cameraToEyeDistanceMM*cameraToEyeDistanceMM+2*cameraToEyeDistanceMM*eyeRadiusMM);
        for (int i = 0;i<angleListLength;i++){
            sinTau[i] = (float)(Math.sin(2*Math.PI/angleListLength*i));
            cosTau[i] = (float)(Math.cos(2*Math.PI/angleListLength*i));
            eyeBall[i] = new Coordinate(Math.round(eyeRadius*cosTau[i]+eyeCenterX),Math.round(eyeRadius*sinTau[i]+eyeCenterY));
            irisEllipse[i] = new Coordinate(0,0);
            irisCircle[i] = new Coordinate(0,0);
            pupilEllipse[i] = new Coordinate(0,0);
        }
        
//craete eyeMaskFrame
        if (eyeMaskTop > (eyeMaskXright-eyeMaskXleft)/2) eyeMaskTop=Math.round((eyeMaskXright-eyeMaskXleft)/2-1);
        if (eyeMaskBottom > (eyeMaskXright-eyeMaskXleft)/2) eyeMaskBottom=Math.round((eyeMaskXright-eyeMaskXleft)/2-1);
        eyeMaskFrame[0] = new Coordinate(eyeMaskXleft,eyeMaskY);
        eyeMaskFrame[1] = new Coordinate(eyeMaskXleft+eyeMaskTop,eyeMaskY+eyeMaskTop);
        eyeMaskFrame[2] = new Coordinate(eyeMaskXright-eyeMaskTop,eyeMaskY+eyeMaskTop);
        eyeMaskFrame[3] = new Coordinate(eyeMaskXright,eyeMaskY);
        eyeMaskFrame[4] = new Coordinate(eyeMaskXright-eyeMaskBottom,eyeMaskY-eyeMaskBottom);
        eyeMaskFrame[5] = new Coordinate(eyeMaskXleft+eyeMaskBottom,eyeMaskY-eyeMaskBottom);
        
//craete eyeMaskArray: if inside eyeMask -> eyeMask(i,j)=1 else eyeMask(i,j)=0
        eyeMaskArray=new boolean[chip.getSizeX()][chip.getSizeY()];
        invIrisParameterArray = new InvEllipseParameter[chip.getSizeX()][chip.getSizeY()];
        invPupilParameterArray = new InvEllipseParameter[chip.getSizeX()][chip.getSizeY()];
        float zEyeMM = cameraToEyeDistanceMM+eyeRadiusMM;
        float irisRadiusMM = irisRadius*cameraToEyeDistanceMM/focus;
        float pupilRadiusMM = pupilRadius*cameraToEyeDistanceMM/focus;
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
                if(    y >= eyeMaskY-eyeMaskBottom 
                    && y <= eyeMaskY+eyeMaskTop
                    && x-eyeMaskXleft >= Math.abs(y-eyeMaskY) 
                    && eyeMaskXright-x >= Math.abs(y-eyeMaskY)
                    && deltaR < eyeRadius-1)
                { 
                    eyeMaskArray[x][y]=true;
                    // calculate cos(phi), sin(phi)
                    float cosPhi=1; float sinPhi = 0; 
                    if(deltaR!=0) {cosPhi= deltaX/deltaR; sinPhi = deltaY/deltaR;}
                    // calculate cos(theta) and sin(theta)
                    float deltaRsqrNorm = (deltaX*deltaX+deltaY*deltaY)/(focus*focus);
                    float cosTheta = (float)((-deltaRsqrNorm*zEyeNorm+Math.sqrt(deltaRsqrNorm+1-deltaRsqrNorm*zEyeNorm*zEyeNorm))/(deltaRsqrNorm+1));
                    float sinTheta = (float)(Math.sqrt(1-cosTheta*cosTheta));
                  
                    float a = focus*cosTheta*irisPaternRadiusMM/(zEyeMM-cosTheta*irisPaternDistanceMM);
                    float b = focus*irisPaternRadiusMM/(zEyeMM-cosTheta*irisPaternDistanceMM);
                    float aa = a*a;
                    float bb = b*b;
                    
                    int centerX = Math.round(focus*cosPhi*irisPaternDistanceMM*sinTheta/(zEyeMM-cosTheta*irisPaternDistanceMM))+eyeCenterX;
                    int centerY = Math.round(focus*sinPhi*irisPaternDistanceMM*sinTheta/(zEyeMM-cosTheta*irisPaternDistanceMM))+eyeCenterY;
                    
                    float AADenom = (float)(1/(aa*sinPhi*sinPhi+bb*cosPhi*cosPhi));
                    float BBDenom = (float)(1/(aa*cosPhi*cosPhi+bb*sinPhi*sinPhi));
                    int twoC = Math.round(-2*(aa-bb)*cosPhi*sinPhi*AADenom*BBDenom*aa*bb);
                    int AA = Math.round(aa*bb*AADenom);
                    int BB = Math.round(aa*bb*BBDenom);
                    invIrisParameterArray[x][y] = new InvEllipseParameter(centerX, centerY,AA,BB,twoC);
                    
                    if (deltaX >= 24 && deltaY >=24){
                        int stop = 1;
                    }
                    // elipse Parameters for pupil
                    a = focus*cosTheta*pupilPaternRadiusMM/(zEyeMM-cosTheta*pupilPaternDistanceMM);
                    b = focus*pupilPaternRadiusMM/(zEyeMM-cosTheta*pupilPaternDistanceMM);
                    aa = a*a;
                    bb = b*b;
                    
                    centerX = Math.round(focus*cosPhi*pupilPaternDistanceMM*sinTheta/(zEyeMM-cosTheta*pupilPaternDistanceMM))+eyeCenterX;
                    centerY = Math.round(focus*sinPhi*pupilPaternDistanceMM*sinTheta/(zEyeMM-cosTheta*pupilPaternDistanceMM))+eyeCenterY;
                    
                    AADenom = (float)(1/(aa*sinPhi*sinPhi+bb*cosPhi*cosPhi));
                    BBDenom = (float)(1/(aa*cosPhi*cosPhi+bb*sinPhi*sinPhi));
                    twoC = Math.round(-2*(aa-bb)*cosPhi*sinPhi*AADenom*BBDenom*aa*bb);
                    AA = Math.round(aa*bb*AADenom);
                    BB = Math.round(aa*bb*BBDenom);
                    invPupilParameterArray[x][y] = new InvEllipseParameter(centerX, centerY,AA,BB,twoC);               
                }
                else {
                    eyeMaskArray[x][y]=false;
                    invIrisParameterArray[x][y] = new InvEllipseParameter(0,0,0,0,0);
                    invPupilParameterArray[x][y] = new InvEllipseParameter(0,0,0,0,0);
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
    public float getirisRadius() {
        return irisRadius;
    }   
    public void setirisRadius(float irisRadius) {
        if(irisRadius<0) irisRadius=0; else if(irisRadius>chip.getMaxSize()) irisRadius=chip.getMaxSize();
        if(irisRadius!=this.irisRadius) {
            this.irisRadius = irisRadius;
            getPrefs().putFloat("HoughTracker.irisRadius",irisRadius);
            resetFilter();
        }
    }
    
    private float pupilRadius=getPrefs().getFloat("HoughTracker.pupilRadius",7f);
    public float getpupilRadius() {
        return pupilRadius;
    }
    public void setpupilRadius(float pupilRadius) {
        if(pupilRadius<0) pupilRadius=0; else if(pupilRadius>chip.getMaxSize()) pupilRadius=chip.getMaxSize();
        if(pupilRadius!=this.pupilRadius) {
            this.pupilRadius = pupilRadius;
            getPrefs().putFloat("HoughTracker.pupilRadius",pupilRadius);
            resetFilter();
        }
    }
    
    private float eyeRadiusMM=getPrefs().getFloat("HoughTracker.eyeRadiusMM",12.5f);   
    public float geteyeRadiusMM() {
        return eyeRadiusMM;
    }   
    public void seteyeRadiusMM(float eyeRadiusMM) {
        if(eyeRadiusMM<0) eyeRadiusMM=0;
        if(eyeRadiusMM!=this.eyeRadiusMM) {
            this.eyeRadiusMM = eyeRadiusMM;
            getPrefs().putFloat("HoughTracker.eyeRadiusMM",eyeRadiusMM);
            resetFilter();
        }
    }

    private float focus=getPrefs().getFloat("HoughTracker.focus",144f);   
    public float getfocus() {
        return focus;
    }   
    public void setfocus(float focus) {
        if(focus<0) focus=0;
        if(focus!=this.focus) {
            this.focus = focus;
            getPrefs().putFloat("HoughTracker.focus",focus);
            resetFilter();
        }
    }
    
    private float cameraToEyeDistanceMM=getPrefs().getFloat("HoughTracker.cameraToEyeDistanceMM",45.0f);   
    public float getcameraToEyeDistanceMM() {
        return cameraToEyeDistanceMM;
    }   
    public void setcameraToEyeDistanceMM(float cameraToEyeDistanceMM) {
        if(cameraToEyeDistanceMM<0) cameraToEyeDistanceMM=0;
        if(cameraToEyeDistanceMM!=this.cameraToEyeDistanceMM) {
            this.cameraToEyeDistanceMM = cameraToEyeDistanceMM;
            getPrefs().putFloat("HoughTracker.cameraToEyeDistanceMM",cameraToEyeDistanceMM);
            resetFilter();
        }
    }

    private int eyeCenterX=getPrefs().getInt("HoughTracker.eyeCenterX",40);   
    public int geteyeCenterX() {
        return eyeCenterX;
    }   
    public void seteyeCenterX(int eyeCenterX) {
        if(eyeCenterX<0) eyeCenterX=0; else if(eyeCenterX>chip.getMaxSize()) eyeCenterX=chip.getMaxSize();
        if(eyeCenterX!=this.eyeCenterX) {
            this.eyeCenterX = eyeCenterX;
            getPrefs().putInt("HoughTracker.eyeCenterX",eyeCenterX);
            resetFilter();
        }
    }
    
    private int eyeCenterY=getPrefs().getInt("HoughTracker.eyeCenterY",40);   
    public int geteyeCenterY() {
        return eyeCenterY;
    }   
    public void seteyeCenterY(int eyeCenterY) {
        if(eyeCenterY<0) eyeCenterY=0; else if(eyeCenterY>chip.getMaxSize()) eyeCenterY=chip.getMaxSize();
        if(eyeCenterY!=this.eyeCenterY) {
            this.eyeCenterY = eyeCenterY;
            getPrefs().putInt("HoughTracker.eyeCenterY",eyeCenterY);
            resetFilter();
        }
    }
    
    private boolean ellipseTrackerEnabled=false;
    
    public boolean isellipseTrackerEnabled() {
        return ellipseTrackerEnabled;
    }
    public void setellipseTrackerEnabled(boolean ellipseTrackerEnabled) {
        this.ellipseTrackerEnabled = ellipseTrackerEnabled;
        resetFilter();
    }
    
    private int eyeMaskXleft=getPrefs().getInt("HoughTracker.eyeMaskXleft",30);
    public int geteyeMaskXleft() {
        return eyeMaskXleft;
    }
    public void seteyeMaskXleft(int eyeMaskXleft) {
        if(eyeMaskXleft < 0) eyeMaskXleft=0; 
        else if(eyeMaskXleft > eyeMaskXright) eyeMaskXleft=eyeMaskXright-1;
        if(eyeMaskXleft!=this.eyeMaskXleft) {
            this.eyeMaskXleft = eyeMaskXleft;
            getPrefs().putInt("HoughTracker.eyeMaskXleft",eyeMaskXleft);
            resetFilter();
        }
    }
    
    private int eyeMaskXright=getPrefs().getInt("HoughTracker.eyeMaskXright",120);
    public int geteyeMaskXright() {
        return eyeMaskXright;
    }    
    public void seteyeMaskXright(int eyeMaskXright) {
        if(eyeMaskXright>chip.getSizeX()) eyeMaskXright=chip.getSizeX();
        else if(eyeMaskXright < eyeMaskXleft) eyeMaskXright=eyeMaskXleft+1;
        if(eyeMaskXright!=this.eyeMaskXright) {
            this.eyeMaskXright = eyeMaskXright;
            getPrefs().putInt("HoughTracker.eyeMaskXright",eyeMaskXright);
            resetFilter();
        }
    }
    
   private int eyeMaskY=getPrefs().getInt("HoughTracker.eyeMaskY",30);
    public int geteyeMaskY() {
        return eyeMaskY;
    }
    public void seteyeMaskY(int eyeMaskY) {
        if(eyeMaskY<0) eyeMaskY=0; 
        else if(eyeMaskY>chip.getSizeX()) eyeMaskY=chip.getSizeX(); 
        if(eyeMaskY!=this.eyeMaskY) {
            this.eyeMaskY = eyeMaskY;
            getPrefs().putInt("HoughTracker.eyeMaskY",eyeMaskY);
            resetFilter();
        }
    }
    
    private int eyeMaskTop=getPrefs().getInt("HoughTracker.eyeMaskTop",5);
    public int geteyeMaskTop() {
        return eyeMaskTop;
    }    
    public void seteyeMaskTop(int eyeMaskTop) {
        if(eyeMaskTop<0) eyeMaskTop=1; 
        else if(eyeMaskTop+eyeMaskY > chip.getSizeX()) eyeMaskTop=chip.getSizeX()-eyeMaskY;
        if(eyeMaskTop!=this.eyeMaskTop) {
            this.eyeMaskTop = eyeMaskTop;
            getPrefs().putInt("HoughTracker.eyeMaskTop",eyeMaskTop);
            resetFilter();
        }
    }
    
    private int eyeMaskBottom=getPrefs().getInt("HoughTracker.eyeMaskBottom",5);
    public int geteyeMaskBottom() {
        return eyeMaskBottom;
    }    
    public void seteyeMaskBottom(int eyeMaskBottom) {
        if(eyeMaskBottom<0) eyeMaskBottom=0; 
        else if(eyeMaskY-eyeMaskBottom<0) eyeMaskBottom=eyeMaskY;
        if(eyeMaskBottom!=this.eyeMaskBottom) {
            this.eyeMaskBottom = eyeMaskBottom;
            getPrefs().putInt("HoughTracker.eyeMaskBottom",eyeMaskBottom);
            resetFilter();
        }
    }
    
    private int bufferLength=getPrefs().getInt("HoughTracker.bufferLength",200);
    public int getbufferLength() {
        return bufferLength;
    }
    public void setbufferLength(int bufferLength) {
        if(bufferLength<0) bufferLength=0; else if(bufferLength>5000) bufferLength=5000;
        this.bufferLength = bufferLength;
        getPrefs().putFloat("HoughTracker.bufferLength",bufferLength);
        resetFilter();
    }
    
    private float maxStepSize=getPrefs().getFloat("HoughTracker.maxStepSize",1.3f);
    public float getmaxStepSize() {
        return maxStepSize;
    }
    public void setmaxStepSize(float maxStepSize) {
        if(maxStepSize<0) maxStepSize=0; else if(maxStepSize>50) maxStepSize=50;
        this.maxStepSize = maxStepSize;
        getPrefs().putFloat("HoughTracker.maxStepSize",maxStepSize);
    }
    
    private int treshold=getPrefs().getInt("HoughTracker.treshold",30);
    public int gettreshold() {
        return treshold;
    }
    public void settreshold(int treshold) {
        if(treshold<0) treshold=0; else if(treshold>2000) treshold=2000;
        this.treshold = treshold;
        getPrefs().putFloat("HoughTracker.treshold",treshold);
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
        gl.glLineWidth(1);
        
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
        
        //draw eyeMask
        gl.glBegin(GL2.GL_LINE_LOOP);
        for (int i = 0;i<6;i++){
            gl.glVertex2d(eyeMaskFrame[i].x, eyeMaskFrame[i].y);
        }
        gl.glEnd();
        
        //eyeball frame       
        gl.glBegin(GL2.GL_LINE_LOOP);
        for (int i = 0;i<angleListLength;i++){
            gl.glVertex2d(eyeBall[i].x, eyeBall[i].y);
        }
        gl.glEnd();
        
        //inverse Ellipse
//        gl.glColor3f(0,1,0);
//        gl.glBegin(GL2.GL_LINE_LOOP);
//        for (int i = 0;i<invEllipseLength;i++){
//            gl.glVertex2d(invEllipse[i].x, invEllipse[i].y);
//        }
//        gl.glEnd();
//        gl.glColor3f(0,0,1);
////      
        //draw statistics
        gl.glLineWidth(5);
        gl.glColor3f(0,0,1);
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex2f(0,1);
        gl.glVertex2f(maxConv,1);
        gl.glVertex2f(0,5);
        gl.glVertex2f(stepSize,5);
        gl.glColor3f(1,0,0);
        gl.glVertex2f(treshold-1,1);
        gl.glVertex2f(treshold,1);
        gl.glColor3f(1,1,1);
        gl.glVertex2f(maxCoordinate.x-1,maxCoordinate.y);
        gl.glVertex2f(maxCoordinate.x+1,maxCoordinate.y);
        gl.glEnd();
   
        
        gl.glPopMatrix();
    }
    
    // fast inclined ellipse drawing algorithm; ellipse eqn: A*x^2+B*y^2+C*x*y-1 = 0
    // the algorithm is fast because it uses just integer addition and subtraction
    void addPatternToAccumulator(int eventX, int eventY){
        for (int i=1;i<2;i++){
            int AA = 0;
            int BB=0;
            int twoC=0;
            int centerX=0;
            int centerY=0;
            //check if ellipse tracker enabeld -> if not circle tracker
            if (i==0){
                if(isellipseTrackerEnabled()){
                    AA = invIrisParameterArray[eventX][eventY].AA;
                    BB = invIrisParameterArray[eventX][eventY].BB;
                    twoC = invIrisParameterArray[eventX][eventY].twoC;
                    centerX = invIrisParameterArray[eventX][eventY].centerX;
                    centerY = invIrisParameterArray[eventX][eventY].centerY;
                }
                else{
                    AA = Math.round(irisRadius*irisRadius);
                    BB = Math.round(irisRadius*irisRadius);
                    twoC = 0;
                    centerX = eventX;
                    centerY = eventY;
                }
            } 
                
            if (i==1){ //load pupil parameters
                 if(isellipseTrackerEnabled()){
                    AA = invPupilParameterArray[eventX][eventY].AA;
                    BB = invPupilParameterArray[eventX][eventY].BB;
                    twoC = invPupilParameterArray[eventX][eventY].twoC;
                    centerX = invPupilParameterArray[eventX][eventY].centerX;;
                    centerY = invPupilParameterArray[eventX][eventY].centerY;;
                }
                else{
                    AA = Math.round(pupilRadius*pupilRadius);
                    BB = Math.round(pupilRadius*pupilRadius);
                    twoC = 0;
                    centerX = eventX;
                    centerY = eventY;
                }
            }
            int x = 0;
            int y = Math.round((float)Math.sqrt(BB));
            int twoAA = 2*AA;
            int twoBB = 2*BB;

            int slopeX =   twoAA*y + twoC*x;   //slope dy/dx = slopeY/slopeX
            int slopeY = -(twoBB*x + twoC*y);

            int ellipseError = 0;
            
// first sector: (dy/dx > 1) -> y+1 (x+1)
// d(x,y+1)   = 2a^2y+a^2+2cx                = slopeX+AA 
// d(x+1,y+1) = 2b^2x+b^2+2cy+2c+2a^2y+a^2+2cx = d(x,y+1)-slopeY+BB
            while (slopeY > slopeX){
                addPointToAccumulator(centerX+x,centerY+y);
                addPointToAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError + slopeX + AA;
                slopeX = slopeX + twoAA;
                slopeY = slopeY - twoC;
                y = y + 1;
                if (2*ellipseError-slopeY+BB > 0) {
                    ellipseError = ellipseError - slopeY + BB ;
                    slopeX = slopeX + twoC;
                    slopeY = slopeY - twoBB;
                    x = x + 1;
                }
            }

// second sector: (dy/dx > 0) -> x+1 (y+1)
// d(x+1,y)   = 2b^2x+b^2+2cy                = -slopeY+BB 
// d(x+1,y+1) = 2b^2x+b^2+2cy+2c+2a^2y+a^2+2cx = d(x+1,y)+slopeX+AA 
            while (slopeY > 0){
                addPointToAccumulator(centerX+x,centerY+y);
                addPointToAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError - slopeY + BB;
                slopeX = slopeX + twoC;
                slopeY = slopeY - twoBB;
                x = x + 1;
                if (2*ellipseError + slopeX + AA < 0){
                    ellipseError = ellipseError + slopeX + AA ;
                    slopeX = slopeX + twoAA;
                    slopeY = slopeY - twoC;
                    y = y + 1;
                }
            }

// third sector: (dy/dx > -1) -> x+1 (y-1)
// d(x+1,y)   = 2b^2x+b^2+2cy                = -slopeY+BB 
// d(x+1,y-1) = 2b^2x+b^2+2cy-2c-2a^2y+a^2-2cx = d(x+1,y)-slopeX+AA
            while (slopeY > - slopeX){
                addPointToAccumulator(centerX+x,centerY+y);
                addPointToAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError - slopeY + BB;
                slopeX = slopeX + twoC;
                slopeY = slopeY - twoBB;
                x = x + 1;
                if (2*ellipseError - slopeX + AA > 0){
                    ellipseError = ellipseError - slopeX + AA;
                    slopeX = slopeX - twoAA;
                    slopeY = slopeY + twoC;
                    y = y - 1;
                }
            }

// fourth sector: (dy/dx < 0) -> y-1 (x+1)
// d(x,y-1)   = -2a^2y+a^2-2cx               = -slopeX+AA 
// d(x+1,y-1) = 2b^2x+b^2+2cy-2c-2a^2y+a^2-2cx = d(x+1,y)-slopeY+BB
            while (slopeX > 0){
                addPointToAccumulator(centerX+x,centerY+y);
                addPointToAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError - slopeX + AA;
                slopeX = slopeX - twoAA;
                slopeY = slopeY + twoC;
                y = y - 1;
                if (2*ellipseError - slopeY + BB < 0){
                    ellipseError = ellipseError - slopeY + BB;
                    slopeX = slopeX + twoC;
                    slopeY = slopeY - twoBB;
                    x = x + 1;
                }
            }

//fifth sector (dy/dx > 1) -> y-1 (x-1)
// d(x,y-1)   = -2a^2y+a^2-2cx                = -slopeX+AA 
// d(x-1,y-1) = -2b^2x+b^2-2cy+2c-2a^2y+a^2-2cx = d(x+1,y)+slopeY+BB 
            while ((slopeY < slopeX)&& (x > 0)){
                addPointToAccumulator(centerX+x,centerY+y);
                addPointToAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError - slopeX + AA;
                slopeX = slopeX - twoAA;
                slopeY = slopeY + twoC;
                y = y - 1;
                if (2*ellipseError + slopeY + BB > 0){
                    ellipseError = ellipseError  + slopeY + BB;
                    slopeX = slopeX - twoC;
                    slopeY = slopeY + twoBB;
                    x = x - 1;
                }
            }

// sixth sector: (dy/dx > 0) -> x-1 (y-1)
// d(x-1,y)   = -2b^2x+b^2-2cy                = slopeY+BB 
// d(x-1,y-1) = -2b^2x+b^2-2cy+2c-2a^2y+a^2-2cx = d(x+1,y)-slopeX+AA 
            while ((slopeY < 0)&& (x > 0)){
                addPointToAccumulator(centerX+x,centerY+y);
                addPointToAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError + slopeY + BB;
                slopeX = slopeX - twoC;
                slopeY = slopeY + twoBB;
                x = x - 1;
                if (2*ellipseError - slopeX + AA < 0){
                    ellipseError = ellipseError  - slopeX + AA;
                    slopeX = slopeX - twoAA;
                    slopeY = slopeY + twoC;
                    y = y - 1;
                }
            }

// seventh sector: (dy/dx > -1) -> x-1 (y+1)
// d(x-1,y)   = -2b^2x+b^2-2cy                = slopeY+BB 
// d(x-1,y+1) = -2b^2x+b^2-2cy-2c+2a^2y+a^2+2cx = d(x+1,y)-slopeX+AA
            while ((slopeY < - slopeX)&& (x > 0)){
                addPointToAccumulator(centerX+x,centerY+y);
                addPointToAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError + slopeY + BB;
                slopeX = slopeX - twoC;
                slopeY = slopeY + twoBB;
                x = x - 1;
                if (2*ellipseError + slopeX + AA > 0){
                    ellipseError = ellipseError  + slopeX + AA;
                    slopeX = slopeX + twoAA;
                    slopeY = slopeY - twoC;
                    y = y + 1;
                }
            }

// eight sector: (dy/dx < 0) -> y+1 (x-1)
// d(x,y+1)   = 2a^2y+a^2+2cx                 = slopeX+AA 
// d(x-1,y+1) = -2b^2x+b^2-2cy-2c+2a^2y+a^2+2cx = d(x,y+1)+slopeY+BB
            while ((slopeY > 0 && slopeX < 0)&& (x > 0)){
                addPointToAccumulator(centerX+x,centerY+y);
                addPointToAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError + slopeX + AA;
                slopeX = slopeX + twoAA;
                slopeY = slopeY - twoC;
                y = y + 1;
                if (2*ellipseError + slopeY + BB < 0){
                    ellipseError = ellipseError + slopeY + BB ;
                    slopeX = slopeX - twoC;
                    slopeY = slopeY + twoBB;
                    x = x - 1;
                }
            }
        }
    }
    
    void addPointToAccumulator(int x, int y){
        if(x>=0&&x<=chip.getSizeX()-1&&y>=0&&y<=chip.getSizeY()-1){
            if(eyeMaskArray[x][y]){
                accumulatorArray[x][y] = (short)(accumulatorArray[x][y]+1);
                if (accumulatorArray[x][y] >= maxConv) {
                    maxConv = accumulatorArray[x][y];
                    if (maxConv > treshold && (x!=maxCoordinate.x || y!=maxCoordinate.y)) {
                        if (x-filteredMaxX > maxStepSize) filteredMaxX=filteredMaxX+maxStepSize;
                        else if (x-filteredMaxX > - maxStepSize) filteredMaxX = x;
                        else filteredMaxX = filteredMaxX-maxStepSize;
                        
                        if (y-filteredMaxY > maxStepSize) filteredMaxY=filteredMaxY+maxStepSize;
                        else if (y-filteredMaxY>-maxStepSize) filteredMaxY = y;
                        else filteredMaxY = filteredMaxY-maxStepSize;
                        
                        maxCoordinate.setCoordinate(x,y);
                        
                        float eyeRadius = eyeRadiusMM*focus/(float)Math.sqrt(cameraToEyeDistanceMM*cameraToEyeDistanceMM+2*cameraToEyeDistanceMM*eyeRadiusMM);
                        int deltaX = (int)filteredMaxX-eyeCenterX;
                        int deltaY = (int)filteredMaxY-eyeCenterY;
                        float rdelta =(float)Math.sqrt(deltaX*deltaX+deltaY*deltaY);
                        float cosPhi=1; 
                        float sinPhi = 0;
                        if(rdelta!=0) {cosPhi= deltaX/rdelta; sinPhi = deltaY/rdelta;}
                        float eyeCenterToIrisDistanceSqr = eyeRadius*eyeRadius-irisRadius*irisRadius;
                        float cosThetaSqr = (1-rdelta*rdelta/eyeCenterToIrisDistanceSqr);
                        
//                        float irisRadiusMM = irisRadius*cameraToEyeDistanceMM/focus;
//                        float zEyeNorm = -(cameraToEyeDistanceMM+eyeRadiusMM)/(float)Math.sqrt(eyeRadiusMM*eyeRadiusMM-irisRadiusMM*irisRadiusMM);
//                        float deltaRsqrNorm = (deltaX*deltaX+deltaY*deltaY)/(focus*focus);
//                        float cosTheta = (float)((-deltaRsqrNorm*zEyeNorm+Math.sqrt(deltaRsqrNorm+1-deltaRsqrNorm*zEyeNorm*zEyeNorm))/(deltaRsqrNorm+1));
//                        float cosThetaSqr = cosTheta*cosTheta;
//                        float sinTheta = (float)(Math.sqrt(1-cosTheta*cosTheta));

//                        float a = focus*cosTheta*irisPaternRadiusMM/(zEyeMM-cosTheta*irisPaternDistanceMM);
//                        float b = focus*irisPaternRadiusMM/(zEyeMM-cosTheta*irisPaternDistanceMM);
                        
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
    
    // fast inclined ellipse drawing algorithm; ellipse eqn: A*x^2+B*y^2+C*x*y-1 = 0
    // the algorithm is fast because it uses just integer addition and subtraction
    void removePatternFromAccumulator(int eventX, int eventY){
        for (int i=1;i<2;i++){
            int AA=0;
            int BB=0;
            int twoC=0;
            int centerX=0;
            int centerY=0;
            //check if ellipse tracker enabeld -> if not circle tracker
            if (i==0){
                if(isellipseTrackerEnabled()){
                    AA = invIrisParameterArray[eventX][eventY].AA;
                    BB = invIrisParameterArray[eventX][eventY].BB;
                    twoC = invIrisParameterArray[eventX][eventY].twoC;
                    centerX = invIrisParameterArray[eventX][eventY].centerX;
                    centerY = invIrisParameterArray[eventX][eventY].centerY;
                }
                else{
                    AA = Math.round(irisRadius*irisRadius);
                    BB = Math.round(irisRadius*irisRadius);
                    twoC = 0;
                    centerX = eventX;
                    centerY = eventY;
                }
            } 
                
            if (i==1){ //load pupil parameters
                 if(isellipseTrackerEnabled()){
                    AA = invPupilParameterArray[eventX][eventY].AA;
                    BB = invPupilParameterArray[eventX][eventY].BB;
                    twoC = invPupilParameterArray[eventX][eventY].twoC;
                    centerX = invPupilParameterArray[eventX][eventY].centerX;;
                    centerY = invPupilParameterArray[eventX][eventY].centerY;;
                }
                else{
                    AA = Math.round(pupilRadius*pupilRadius);
                    BB = Math.round(pupilRadius*pupilRadius);
                    twoC = 0;
                    centerX = eventX;
                    centerY = eventY;
                }
            }
            int x = 0;
            int y = Math.round((float)Math.sqrt(BB));
            int twoAA = 2*AA;
            int twoBB = 2*BB;

            int slopeX =   twoAA*y + twoC*x;   //slope dy/dx = slopeY/slopeX
            int slopeY = -(twoBB*x + twoC*y);

            int ellipseError = 0;
            
// first sector: (dy/dx > 1) -> y+1 (x+1)
// d(x,y+1)   = 2a^2y+a^2+2cx                = slopeX+AA 
// d(x+1,y+1) = 2b^2x+b^2+2cy+2c+2a^2y+a^2+2cx = d(x,y+1)-slopeY+BB
            while (slopeY > slopeX){
                removePointFromAccumulator(centerX+x,centerY+y);
                removePointFromAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError + slopeX + AA;
                slopeX = slopeX + twoAA;
                slopeY = slopeY - twoC;
                y = y + 1;
                if (2*ellipseError-slopeY+BB > 0) {
                    ellipseError = ellipseError - slopeY + BB ;
                    slopeX = slopeX + twoC;
                    slopeY = slopeY - twoBB;
                    x = x + 1;
                }
            }

// second sector: (dy/dx > 0) -> x+1 (y+1)
// d(x+1,y)   = 2b^2x+b^2+2cy                = -slopeY+BB 
// d(x+1,y+1) = 2b^2x+b^2+2cy+2c+2a^2y+a^2+2cx = d(x+1,y)+slopeX+AA 
            while (slopeY > 0){
                removePointFromAccumulator(centerX+x,centerY+y);
                removePointFromAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError - slopeY + BB;
                slopeX = slopeX + twoC;
                slopeY = slopeY - twoBB;
                x = x + 1;
                if (2*ellipseError + slopeX + AA < 0){
                    ellipseError = ellipseError + slopeX + AA ;
                    slopeX = slopeX + twoAA;
                    slopeY = slopeY - twoC;
                    y = y + 1;
                }
            }

// third sector: (dy/dx > -1) -> x+1 (y-1)
// d(x+1,y)   = 2b^2x+b^2+2cy                = -slopeY+BB 
// d(x+1,y-1) = 2b^2x+b^2+2cy-2c-2a^2y+a^2-2cx = d(x+1,y)-slopeX+AA
            while (slopeY > - slopeX){
                removePointFromAccumulator(centerX+x,centerY+y);
                removePointFromAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError - slopeY + BB;
                slopeX = slopeX + twoC;
                slopeY = slopeY - twoBB;
                x = x + 1;
                if (2*ellipseError - slopeX + AA > 0){
                    ellipseError = ellipseError - slopeX + AA;
                    slopeX = slopeX - twoAA;
                    slopeY = slopeY + twoC;
                    y = y - 1;
                }
            }

// fourth sector: (dy/dx < 0) -> y-1 (x+1)
// d(x,y-1)   = -2a^2y+a^2-2cx               = -slopeX+AA 
// d(x+1,y-1) = 2b^2x+b^2+2cy-2c-2a^2y+a^2-2cx = d(x+1,y)-slopeY+BB
            while (slopeX > 0){
                removePointFromAccumulator(centerX+x,centerY+y);
                removePointFromAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError - slopeX + AA;
                slopeX = slopeX - twoAA;
                slopeY = slopeY + twoC;
                y = y - 1;
                if (2*ellipseError - slopeY + BB < 0){
                    ellipseError = ellipseError - slopeY + BB;
                    slopeX = slopeX + twoC;
                    slopeY = slopeY - twoBB;
                    x = x + 1;
                }
            }

//fifth sector (dy/dx > 1) -> y-1 (x-1)
// d(x,y-1)   = -2a^2y+a^2-2cx                = -slopeX+AA 
// d(x-1,y-1) = -2b^2x+b^2-2cy+2c-2a^2y+a^2-2cx = d(x+1,y)+slopeY+BB 
            while ((slopeY < slopeX)&& (x > 0)){
                removePointFromAccumulator(centerX+x,centerY+y);
                removePointFromAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError - slopeX + AA;
                slopeX = slopeX - twoAA;
                slopeY = slopeY + twoC;
                y = y - 1;
                if (2*ellipseError + slopeY + BB > 0){
                    ellipseError = ellipseError  + slopeY + BB;
                    slopeX = slopeX - twoC;
                    slopeY = slopeY + twoBB;
                    x = x - 1;
                }
            }

// sixth sector: (dy/dx > 0) -> x-1 (y-1)
// d(x-1,y)   = -2b^2x+b^2-2cy                = slopeY+BB 
// d(x-1,y-1) = -2b^2x+b^2-2cy+2c-2a^2y+a^2-2cx = d(x+1,y)-slopeX+AA 
            while ((slopeY < 0)&& (x > 0)){
                removePointFromAccumulator(centerX+x,centerY+y);
                removePointFromAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError + slopeY + BB;
                slopeX = slopeX - twoC;
                slopeY = slopeY + twoBB;
                x = x - 1;
                if (2*ellipseError - slopeX + AA < 0){
                    ellipseError = ellipseError  - slopeX + AA;
                    slopeX = slopeX - twoAA;
                    slopeY = slopeY + twoC;
                    y = y - 1;
                }
            }

// seventh sector: (dy/dx > -1) -> x-1 (y+1)
// d(x-1,y)   = -2bx+b-2cy                = slopeY+BB 
// d(x-1,y+1) = -2bx+b-2cy-2c+2ay+as+2cx = d(x+1,y)-slopeX+AA
            while ((slopeY < - slopeX)&& (x > 0)){
                removePointFromAccumulator(centerX+x,centerY+y);
                removePointFromAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError + slopeY + BB;
                slopeX = slopeX - twoC;
                slopeY = slopeY + twoBB;
                x = x - 1;
                if (2*ellipseError + slopeX + AA > 0){
                    ellipseError = ellipseError  + slopeX + AA;
                    slopeX = slopeX + twoAA;
                    slopeY = slopeY - twoC;
                    y = y + 1;
                }
            }

// eight sector: (dy/dx < 0) -> y+1 (x-1)
// d(x,y+1)   = 2a^2y+a^2+2cx                 = slopeX+AA 
// d(x-1,y+1) = -2b^2x+b^2-2cy-2c+2a^2y+a^2+2cx = d(x,y+1)+slopeY+BB
            while ((slopeY > 0 && slopeX < 0)&& (x > 0)){
                removePointFromAccumulator(centerX+x,centerY+y);
                removePointFromAccumulator(centerX-x,centerY-y);
                ellipseError = ellipseError + slopeX + AA;
                slopeX = slopeX + twoAA;
                slopeY = slopeY - twoC;
                y = y + 1;
                if (2*ellipseError + slopeY + BB < 0){
                    ellipseError = ellipseError + slopeY + BB ;
                    slopeX = slopeX - twoC;
                    slopeY = slopeY + twoBB;
                    x = x - 1;
                }
            }
        }
    }
    
    
    void removePointFromAccumulator(int x, int y){
        if(x>=0&&x<=chip.getSizeX()-1&&y>=0&&y<=chip.getSizeY()-1){
            if(eyeMaskArray[x][y]){    
                accumulatorArray[x][y] = (short)(accumulatorArray[x][y]-1);
                if (maxCoordinate.x == x && maxCoordinate.y == y) {
                    maxConv = maxConv-1;
                }
            }
        }
    }
    
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!isFilterEnabled()) return in;  
        for(Object o:in){
            BasicEvent ev=(BasicEvent)o;
            ev.x=(short)(chip.getSizeX()-ev.x-1);
            ev.y=(short)(chip.getSizeY()-ev.y-1);
            event.x = ev.x;      
            event.y = ev.y; 
            if(event.x < 0 && event.x > chip.getSizeX()-1 && event.y < 0 && event.y > chip.getSizeY()-1) continue;
            if(!eyeMaskArray[event.x][event.y]) continue;
            bufferArray[bufferIndex] = event;
            addPatternToAccumulator(event.x,event.y);
            bufferIndex = (bufferIndex+1)%bufferLength;
            event = bufferArray[bufferIndex];
            if(event.x<0||event.y<0) continue; // wait until the ringbuffer is filled
            removePatternFromAccumulator(event.x,event.y);
        }
        return in;
    }
    
    
    private boolean logDataEnabled=false;
    
    public boolean isLogDataEnabled() {
        return logDataEnabled;
    }
    
    synchronized public void setLogDataEnabled(boolean logDataEnabled) {
        this.logDataEnabled = logDataEnabled;
        if(dataLogger==null) dataLogger=new EventFilterDataLogger(this,"targetX targetY eyeX eyeY");
        dataLogger.setEnabled(logDataEnabled);
        if(logDataEnabled){
            targetFrame=new JFrame("EyeTargget");
            DrawGazePanel= new DrawPanel();
            //targetFrame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );             
            targetFrame.setLocation( 0, 0 ); 
            targetFrame.setSize( Toolkit.getDefaultToolkit().getScreenSize() );
            targetFrame.add( DrawGazePanel );
            targetFrame.setVisible(true);
            targetFrame.addKeyListener( new KeyListener() 
            { 
              public void keyTyped( KeyEvent e ) {                 
//                System.out.println( "typed " + e.getKeyChar() ); 
//                System.out.println( "typed " + e.getKeyCode() ); 
                DrawGazePanel.newPosition();
              } 
              public void keyPressed( KeyEvent e ) {} 
              public void keyReleased( KeyEvent e ) { } 
            });
        }else{
            targetFrame.setVisible(false);
        }
    }
   
    class DrawPanel extends JPanel
    {
        public int x = 100;
        public int y = 200;
        @Override
        protected void paintComponent( Graphics g )
        {
            super.paintComponent( g );
            g.fillRect(x,y,10,10);
        }
        public void newPosition()
        {
            if(isLogDataEnabled() ){
                dataLogger.log(String.format("%d %d %f %f", x, y, filteredMaxY, filteredMaxY));
            }        
            int width=targetFrame.getSize().width;
            int height=targetFrame.getSize().height;
            int randX = (int)(Math.random()*3);
            int randY = (int)(Math.random()*2);
            x = (width-200)/2*(randX)+100;
            y = (height-400)*(randY)+200;
            repaint();
        }
    }
 }
