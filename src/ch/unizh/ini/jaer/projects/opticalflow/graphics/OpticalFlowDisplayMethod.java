/*
 * OpticalFlowDisplayMethod.java
 *
 * Created on December 7, 2006, 1:48 PM
 *
 *  Copyright T. Delbruck, Inst. of Neuroinformatics, 2006
 */

package ch.unizh.ini.jaer.projects.opticalflow.graphics;

import ch.unizh.ini.jaer.projects.opticalflow.*;
import net.sf.jaer.graphics.*;
import java.util.prefs.*;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;

/**
 * Displays output from an OpticalFlowChip. Optionally displays the photoreceptor and the local motion vectors. The local motion vectors are displayed
 either as color (red/blue) or as motion vector arrows.
 The global motion vector that is displayed is computed from the vector sum of local motion vectors. (The "global motion" vector captured from the chip is actually 
 just a corner pixel.)
 
 *
 * @author tobi
 */
public class OpticalFlowDisplayMethod extends DisplayMethod {
    
//    HighpassFilter uxFilter, uyFilter, phFilter;
//    long startTime=System.currentTimeMillis();
//    final int TAU_MS=1000;
    Preferences prefs=Preferences.userNodeForPackage(OpticalFlowDisplayMethod.class);
    final int GAIN_FACTOR=10; // gain value from 0-1 is scaled by this amount
    final int MOTION_VECTOR_FACTOR=10; // additional scaling of motion vectors
    
    private float photoGain=prefs.getFloat("OpticalFlowDisplayMethod.photoGain",1f);
    private float photoOffset=prefs.getFloat("OpticalFlowDisplayMethod.photoOffset",0);
    private float localMotionGain=prefs.getFloat("OpticalFlowDisplayMethod.localMotionGain",1f);
    private float localMotionOffset=prefs.getFloat("OpticalFlowDisplayMethod.localMotionOffset",0.5f);
    private float globalMotionGain=prefs.getFloat("OpticalFlowDisplayMethod.globalMotionGain",1f);
    private float globalMotionOffset=prefs.getFloat("OpticalFlowDisplayMethod.globalMotionOffset",0.5f);
    private float vectorLengthScale=prefs.getFloat("OpticalFlowDisplayMethod.vectorLengthScale",.5f);
    
    private boolean photoDisplayEnabled=prefs.getBoolean("OpticalFlowDisplayMethod.photoDisplayEnabled",true),
            localDisplayEnabled=prefs.getBoolean("OpticalFlowDisplayMethod.localDisplayEnabled",true),
            globalDisplayEnabled=prefs.getBoolean("OpticalFlowDisplayMethod.globalDisplayEnabled",true),
            localMotionColorsEnabled=prefs.getBoolean("OpticalFlowDisplayMethod.localMotionColorsEnabled",true);

    private int rawChannelToDisplay=0;
    
    /** Creates a new instance of OpticalFlowDisplayMethod
     @param canvas the canvas this display method will render on
     */
    public OpticalFlowDisplayMethod(ChipCanvas canvas) {
        super(canvas);
//        uxFilter=new HighpassFilter();
//        uyFilter=new HighpassFilter();
//        phFilter=new HighpassFilter();
//        uxFilter.setTauMs(TAU_MS);
//        uyFilter.setTauMs(TAU_MS);
//        phFilter.setTauMs(TAU_MS);
    }
    
    float[] xvec=null, yvec=null;
    
    public void display(GLAutoDrawable drawable) {
        float gx=0, gy=0;
        super.setupGL(drawable);
        GL gl=drawable.getGL();
        float x=0,y=0,p=0; // drawn motion and photo values
        int nRows=chip.getSizeY();
        int nCols=chip.getSizeX();
        int sx=nCols-1;
        int sy=nRows-1; // for not drawing motion pixels on edge
        gl.glPushMatrix();
        // chip is displayed upside down if we just render row as row, we subtract from num rows to flip it insteead of trying to figure out
        // graphics transformation
        gl.glTranslatef(0,nRows,0);
        gl.glScalef(1,-1,1);
        // get dt for filters
//        long time=System.currentTimeMillis();
//        int us=(int)((time-startTime)<<10); // to us from ms
        
        gl.glClearColor(0,0,0,0);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glLineWidth(1f);
        MotionData motionData;
        if(chip.getLastData()==null) return;
        motionData=(MotionData)chip.getLastData(); // relies on capture or file input in MotionViewer or elsewhere to fill this field
        
        boolean hasGlobal=isGlobalDisplayEnabled() ; // global is just avg of local vectors
        boolean hasPhoto=isPhotoDisplayEnabled() && motionData.hasPhoto(); // marks display of photo
        boolean hasLocal=(isLocalMotionColorsEnabled()||isLocalDisplayEnabled()) && motionData.hasLocalX()&&motionData.hasLocalY(); // marks to show any local motion
        
        float scale=10f/chip.getRenderer().getColorScale(); // values are scaled down when color scale increased
        // local motion vectors
        float[][] rawChannel=motionData.extractRawChannel(this.getRawChannelDisplayed());
        float[][] ux=motionData.getUx();//rawChannel
        float[][] uy=motionData.getUy();//ux

        
        if(xvec==null){
            xvec=new float[(nRows-2)*(nCols-2)]; // cache motion vector components
            yvec=new float[(nRows-2)*(nCols-2)]; // cache motion vector components
        }
        
        // first draw pixel colors, then arrows above
        int index=0;
        for(int row=0;row<nRows;row++){
            for(int col=0;col<nCols;col++){
                // color photo value
                boolean onBorder=(row==0|col==0|row==sy|col==sx);
                if(hasLocal && !onBorder){
                    x=scale*localMotionGain*GAIN_FACTOR*(ux[row][col]-localMotionOffset); // motion signal should be signed around zero
                    y=scale*localMotionGain*GAIN_FACTOR*(uy[row][col]-localMotionOffset);
                    xvec[index]=x;
                    yvec[index]=y;
                    index++;
                }
                if(hasPhoto){
                    p=scale*GAIN_FACTOR*photoGain*(rawChannel[row][col]-photoOffset);
                }else{
                    p=0;
                }
                // rendering pixel color. if we have photo but no local, drawn photo in white for all
                float red=0, green=0, blue=0;
                if(hasPhoto && !hasLocal){
                    red=p; green=p; blue=p; // draw gray photo for all
                }else{
                    if(localMotionColorsEnabled && hasLocal && !onBorder){ // local motion and not on border
                        red=y+.5f; blue=x+.5f; // color is .5 when there's no motion
                    }
                    if(hasPhoto){
                        green=p; // color photo if we want to draw it
                    }
                }
                gl.glColor3f(red,green,blue);
                gl.glRectf(col, row, col+1, (row+1));
            }
        }

        // get the global X and Y from motionData and rescale
        gx=motionData.getGlobalX()*globalMotionGain*GAIN_FACTOR*MOTION_VECTOR_FACTOR;
        gy=motionData.getGlobalY()*globalMotionGain*GAIN_FACTOR*MOTION_VECTOR_FACTOR;
        
        // now draw motion vector arrows on top of pixel colors
        if(hasLocal && isLocalDisplayEnabled()){
            index=0;
            for(int row=0;row<nRows;row++){
                for(int col=0;col<nCols;col++){
                    // color photo value
                    boolean onBorder=(row==0|col==0|row==sy|col==sx);
                    if(onBorder) continue;
                    arrow(gl,col+.5f,row+.5f,xvec[index],yvec[index]);
                    index++;
                }
            }
        }
        // global vector
        if(hasGlobal){
            gl.glColor3f(1,1,1);
            gl.glLineWidth(4f);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex3f(nCols/2+.5f,nRows/2+.5f,1); // put at z=1 to draw above pixels
            gl.glVertex3f(nCols/2+.5f+gx,nRows/2+.5f+gy,1);
            gl.glEnd();
        }
        gl.glPopMatrix();
    }
    
// draws motion vector arrow from point x,y with magnitude ux, uy
    private void arrow(GL gl, float x,float y,float ux, float uy){
        gl.glColor3f(1,1,1);
        gl.glPointSize(3);
        gl.glBegin(GL.GL_POINTS);
        gl.glVertex3f(x,y,0);
        gl.glEnd();
        gl.glBegin(GL.GL_LINES);
        float ex=x+vectorLengthScale*MOTION_VECTOR_FACTOR*ux, ey=y+vectorLengthScale*MOTION_VECTOR_FACTOR*uy;
        gl.glVertex2f(x,y);
        gl.glVertex2f(ex,ey);
        gl.glEnd();
    }
    
    public float getPhotoGain() {
        return photoGain;
    }
    
    public void setPhotoGain(float photoGain) {
        this.photoGain = photoGain;
        prefs.putFloat("OpticalFlowDisplayMethod.photoGain",photoGain);
    }
    
    public float getPhotoOffset() {
        return photoOffset;
    }
    
    public void setPhotoOffset(float photoOffset) {
        this.photoOffset = photoOffset;
        prefs.putFloat("OpticalFlowDisplayMethod.photoOffset",photoOffset);
    }
    
    public float getLocalMotionGain() {
        return localMotionGain;
    }
    
    public void setLocalMotionGain(float localMotionGain) {
        this.localMotionGain = localMotionGain;
        prefs.putFloat("OpticalFlowDisplayMethod.localMotionGain",localMotionGain);
    }
    
    public float getLocalMotionOffset() {
        return localMotionOffset;
    }
    
    public void setLocalMotionOffset(float localMotionOffset) {
        this.localMotionOffset = localMotionOffset;
        prefs.putFloat("OpticalFlowDisplayMethod.localMotionOffset",localMotionOffset);
    }
    
    public float getGlobalMotionGain() {
        return globalMotionGain;
    }
    
    public void setGlobalMotionGain(float globalMotionGain) {
        this.globalMotionGain = globalMotionGain;
        prefs.putFloat("OpticalFlowDisplayMethod.globalMotionGain",globalMotionGain);
    }
    
    public float getGlobalMotionOffset() {
        return globalMotionOffset;
    }
    
    public void setGlobalMotionOffset(float globalMotionOffset) {
        this.globalMotionOffset = globalMotionOffset;
        prefs.putFloat("OpticalFlowDisplayMethod.globalMotionOffset",globalMotionOffset);
    }
    
    public boolean isPhotoDisplayEnabled() {
        return photoDisplayEnabled;
    }
    
    public void setPhotoDisplayEnabled(boolean photoDisplayEnabled) {
        this.photoDisplayEnabled = photoDisplayEnabled;
        prefs.putBoolean("OpticalFlowDisplayMethod.photoDisplayEnabled",photoDisplayEnabled);
    }
    
    public boolean isLocalDisplayEnabled() {
        return localDisplayEnabled;
    }
    
    public void setLocalDisplayEnabled(boolean localDisplayEnabled) {
        this.localDisplayEnabled = localDisplayEnabled;
        prefs.putBoolean("OpticalFlowDisplayMethod.localDisplayEnabled",localDisplayEnabled);
    }
    
    public boolean isGlobalDisplayEnabled() {
        return globalDisplayEnabled;
    }
    
    public void setGlobalDisplayEnabled(boolean globalDisplayEnabled) {
        this.globalDisplayEnabled = globalDisplayEnabled;
        prefs.putBoolean("OpticalFlowDisplayMethod.globalDisplayEnabled",globalDisplayEnabled);
    }
    
    public float getVectorLengthScale() {
        return vectorLengthScale;
    }
    
    /** Sets the scale for drawing the motion vectors: the motion color value is multipled by this scale times 4 to give the motion vector length
     @param vectorLengthScale the scale value
     */
    public void setVectorLengthScale(float vectorLengthScale) {
        this.vectorLengthScale = vectorLengthScale;
        prefs.putFloat("OpticalFlowDisplayMethod.vectorLengthScale",vectorLengthScale);
    }

    public boolean isLocalMotionColorsEnabled() {
        return localMotionColorsEnabled;
    }

    public void setLocalMotionColorsEnabled(boolean localMotionColorsEnabled) {
        this.localMotionColorsEnabled = localMotionColorsEnabled;
        prefs.putBoolean("OpticalFlowDisplayMethod.localMotionColorsEnabled",localMotionColorsEnabled);
    }

    public int getRawChannelDisplayed(){
        return this.rawChannelToDisplay;
    }

    public void setRawChannelDisplayed(int channelNumber){
        this.rawChannelToDisplay=channelNumber;
    }
}
