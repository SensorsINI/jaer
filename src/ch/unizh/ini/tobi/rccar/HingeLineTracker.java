/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.tobi.rccar;


import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.FilterChain;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;
import javax.swing.*;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.util.*;
import java.util.Observable;
import java.util.Observer;
import java.beans.*;
import java.io.*;
import com.sun.opengl.util.*;

/**
 * 
 * @author braendch
 * 
 */
public class HingeLineTracker extends EventFilter2D implements FrameAnnotater, Observer {
    
    private float positionMixingFactor=getPrefs().getFloat("LineTracker.positionMixingFactor",0.005f);
    {setPropertyTooltip("positionMixingFactor","how much line position gets moved per packet");}
    private float hingeDecayFactor=getPrefs().getFloat("LineTracker.hingeDecayFactor",0.6f);
    {setPropertyTooltip("hingeDecayFactor","hinge accumulator cells are multiplied by this factor before each frame, 0=no memory, 1=infinite memory");}
    private boolean showRowWindow=true;
    {setPropertyTooltip("showRowWindow","");}
    
    private float[][] accumArray;
    private float[] hingeMax;
    private int[] maxIndex;
    private int[] hingeArray;
    private int sx;
    private int sy;
    private int hingeNumber = 3;
    private int height = 3;

    

    FilterChain preFilterChain;
    private OrientationCluster orientationCluster;
    

    public HingeLineTracker(AEChip chip) {
        super(chip);
        
         //build hierachy
        preFilterChain = new FilterChain(chip);
        orientationCluster = new OrientationCluster(chip);

        this.setEnclosedFilter(orientationCluster);
        
        orientationCluster.setEnclosed(true, this);
        //xYFilter.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);

        chip.getCanvas().addAnnotator(this);
        
        initFilter();
        resetFilter();
    }
    
    private void checkMaps(){
        //it has to be checked if the VectorMap fits on the actual chip
        if(accumArray==null
                || accumArray.length!=hingeNumber
                || accumArray[0].length!=chip.getSizeX()) {
            allocateMaps();
        }
    }    
    
    synchronized private void allocateMaps() {
        //the VectorMap is fitted on the chip size
        if(!isFilterEnabled()) return;
        log.info("HingeLineTracker.allocateMaps()");
        
        sx=chip.getSizeX();
        sy=chip.getSizeY();
        
        if(chip!=null){
            accumArray= new float[hingeNumber][sx];
            hingeMax= new float[hingeNumber];
            hingeArray= new int[hingeNumber];
            maxIndex= new int[hingeNumber];
        }
        resetFilter();
        
    }
    
    synchronized public void resetFilter() {
        
        if(!isFilterEnabled()) return;

        if(accumArray!=null){
            for(int i=0;i<accumArray.length;i++) Arrays.fill(accumArray[i],0);    
            Arrays.fill(hingeMax,Float.NEGATIVE_INFINITY);
            Arrays.fill(hingeArray,0);
            Arrays.fill(maxIndex, 0);

            System.out.println("HingeLineTracker.reset!");
        }else{
            return;
        }
        
       
    }
    
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        if(!isFilterEnabled()) return in;
        if(getEnclosedFilter()!=null) in=getEnclosedFilter().filterPacket(in);
        if(getEnclosedFilterChain()!=null) in=getEnclosedFilterChain().filterPacket(in);
        
        int n=in.getSize();
        if(n==0) return in;
        
        checkMaps();
        
        hingeArray[0] = 0;
        hingeArray[1] = 40;
        hingeArray[2] = 80;
        
        for(BasicEvent e:in){
            for(int i=0;i<hingeNumber;i++){
                if(e.y <= hingeArray[i]+height && e.y >= hingeArray[i]-height ){
                    updateHingeAccumulator(i,e.x);
                }
            }
        }
        
        decayAccumArray();

        if(showRowWindow) {
            checkAccumFrame();
            accumCanvas.repaint();
        }
        
        return in;
    }

    private void updateHingeAccumulator(int hingeNumber, int x) {
        float f=accumArray[hingeNumber][x];
        f++;
        accumArray[hingeNumber][x]=f; // update the accumulator
    }
    
        private void decayAccumArray() {
        if(accumArray==null) return;
        for(int hinge=0; hinge<hingeNumber; hinge++){
            hingeMax[hinge]*=hingeDecayFactor;
            float[] f=accumArray[hinge];
            for(int y=0;y<f.length;y++){
                float fval=f[y];
                fval*=hingeDecayFactor;
                if(fval>hingeMax[hinge]){
                    maxIndex[hinge]=y;
                    hingeMax[hinge]=fval;
                }
                f[y]=fval;
            }
        }
    }
    
    GLU glu=null;
    GLUquadric wheelQuad;    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        if(hingeArray == null) return;
        float Radius = 3; 
        float lineWidth = 6;
        
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        
        gl.glColor3f(1,1,1);
        gl.glPointSize(6);
        gl.glLineWidth(3);
        
        if(glu==null) glu=new GLU();
        if(wheelQuad==null) wheelQuad = glu.gluNewQuadric();
        gl.glPushMatrix();
        gl.glColor3f(1,1,1);
        for(int i=0; i<hingeNumber;i++){
            gl.glBegin(GL.GL_POINTS);
            gl.glVertex2f(maxIndex[i], hingeArray[i]);
            gl.glEnd();
        }
        gl.glBegin(GL.GL_LINE_STRIP);
        for(int i=0; i<hingeNumber;i++){
            gl.glVertex2i(maxIndex[i],hingeArray[i]);
        }
        gl.glEnd();
        gl.glPopMatrix();
        
    }
    
    void checkAccumFrame(){
        if(showRowWindow && (accumFrame==null || (accumFrame!=null && !accumFrame.isVisible()))) createAccumFrame();
    }
    
    JFrame accumFrame=null;
    GLCanvas accumCanvas=null;
    
    void createAccumFrame(){
        accumFrame=new JFrame("Hinge accumulator");
        accumFrame.setPreferredSize(new Dimension(400,400));
        accumCanvas=new GLCanvas();
        accumCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
            
            synchronized public void display(GLAutoDrawable drawable) {
                if(accumArray==null) return;
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                gl.glScalef(drawable.getWidth()/sx,drawable.getHeight()/sy,1);
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                for(int i=0;i<hingeNumber;i++){
                    for(int j=0;j<accumArray[i].length;j++){
                        float f=accumArray[i][j]/hingeMax[i];
                        gl.glColor3f(f,f,f);
                        gl.glRectf(j,hingeArray[i]-height,j+1,hingeArray[i]+height);
                    }
                    gl.glColor3f(1,0,0);
                    gl.glRectf(maxIndex[i],hingeArray[i]-height,maxIndex[i]+1,hingeArray[i]+height);
                }
                int error=gl.glGetError();
                if(error!=GL.GL_NO_ERROR){
                    if(glu==null) glu=new GLU();
                    log.warning("GL error number "+error+" "+glu.gluErrorString(error));
                }
            }
            
            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL gl=drawable.getGL();
                final int B=10;
                gl.glMatrixMode(GL.GL_PROJECTION);
                gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
                gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glViewport(0,0,width,height);
            }
            
            public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
            }
        });
        accumFrame.getContentPane().add(accumCanvas);
        accumFrame.pack();
        accumFrame.setVisible(true);
    }
    
    public Object getFilterState() {
        return null;
    }
    
    
    public void initFilter() {
        resetFilter();
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    public boolean isShowRowWindow() {
        return showRowWindow;
    }
    
    synchronized public void setShowRowWindow(boolean showRowWindow) {
        this.showRowWindow = showRowWindow;
    }
    
    public void update(Observable o, Object arg){
        resetFilter();
    }
 
    public float getHingeDecayFactor() {
        return hingeDecayFactor;
    }
    
    public void setHingeDecayFactor(float hingeDecayFactor) {
        if(hingeDecayFactor<0)hingeDecayFactor=0;else if(hingeDecayFactor>1)hingeDecayFactor=1;
        this.hingeDecayFactor = hingeDecayFactor;
        getPrefs().putFloat("LineTracker.hingeDecayFactor",hingeDecayFactor);
    }
    
}


