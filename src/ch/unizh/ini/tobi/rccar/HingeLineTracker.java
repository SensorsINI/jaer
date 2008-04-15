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
    
    private float [][] accumArray;
    private int [] hingeArray;
    private int [] hingeMax;
    private int sx;
    private int sy;
    private int hingeNumber = 1;
    private int height = 2;
    float[] cos=null, sin=null;
    int rhoMaxIndex, thetaMaxIndex;
    float accumMax;
    int[][] accumUpdateTime;
    

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
    
    synchronized public void resetFilter() {
        
        if(!isFilterEnabled()) return;
        
        sx=chip.getSizeX();
        sy=chip.getSizeY();
        if(chip!=null){
            accumArray= new float[hingeNumber][sx];
            hingeMax= new int[hingeNumber];
            hingeArray= new int[hingeNumber];
            hingeArray[0] = 60;
            accumMax=Float.NEGATIVE_INFINITY;
        
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
        
        resetFilter();
        
        for(BasicEvent e:in){
            for(int i=0;i<hingeNumber;i++){
                if(e.y <= hingeArray[i]+height && e.y >= hingeArray[i]-height ){
                    updateHingeAccumulator(i,e.y);
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

    private void updateHingeAccumulator(int hingeNumber, int y) {
        if(accumArray==null) return;
        float f=accumArray[hingeNumber][y];
        f++;
        accumArray[hingeNumber][y]=f; // update the accumulator
    }
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        final float LINE_WIDTH=5f;
        GL gl=drawable.getGL();
        gl.glLineWidth(LINE_WIDTH);
        gl.glColor3f(0,0,1);
        gl.glBegin(GL.GL_LINES);
        gl.glEnd();
    }
    
    void checkAccumFrame(){
        if(showRowWindow && (accumFrame==null || (accumFrame!=null && !accumFrame.isVisible()))) createAccumFrame();
    }
    
    JFrame accumFrame=null;
    GLCanvas accumCanvas=null;
    GLU glu=null;
    
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
                        float f=accumArray[i][j]/accumMax;
                        gl.glColor3f(f,f,f);
                        gl.glRectf(j,hingeArray[i]-height,j+1,hingeArray[i]+height);
                    }
                    gl.glColor3f(1,0,0);
                    gl.glRectf(hingeMax[i],hingeArray[i]-height,hingeMax[i]+1,hingeArray[i]+height);
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

    private void decayAccumArray() {
        if(accumArray==null) return;
        accumMax=0;
        for(int hinge=0; hinge<hingeNumber; hinge++){
            float[] f=accumArray[hinge];
            for(int y=0;y<f.length;y++){
                float fval=f[y];
                fval*=hingeDecayFactor;
                if(fval>accumMax){
                    accumMax=fval;
                    hingeMax[hinge]=y;
                }
                f[y]=fval;
            }
        }
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


