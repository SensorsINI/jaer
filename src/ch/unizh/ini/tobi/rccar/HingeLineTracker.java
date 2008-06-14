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
public class HingeLineTracker extends EventFilter2D implements FrameAnnotater, Observer, HingeDetector {

    private float hingeThreshold=getPrefs().getFloat("LineTracker.hingeThreshold",2.5f);
    {setPropertyTooltip("hingeThreshold","the threshold for the hinge to react");}
    private float attentionRadius=getPrefs().getFloat("LineTracker.attentionRadius",12);
    {setPropertyTooltip("attentionRadius","the size of the attention balls");}
    private float attentionFactor=getPrefs().getFloat("LineTracker.attentionFactor",2);
    {setPropertyTooltip("attentionFactor","how much is additionally added to the accumArray if the attention is on a certain spike");}
    private float hingeDecayFactor=getPrefs().getFloat("LineTracker.hingeDecayFactor",0.6f);
    {setPropertyTooltip("hingeDecayFactor","hinge accumulator cells are multiplied by this factor before each frame, 0=no memory, 1=infinite memory");}
    private float attentionDecayFactor=getPrefs().getFloat("LineTracker.attentionDecayFactor",0.6f);
    {setPropertyTooltip("attentionDecayFactor","the slope of attention decay, 0=no memory, 1=infinite memory");}
    private int shiftSpace=getPrefs().getInt("LineTracker.shiftSpace",5);
    {setPropertyTooltip("shiftSpace","minimal distance between paoli hinge and seperation");}
    private int topHinge=getPrefs().getInt("LineTracker.topHinge",80);
    {setPropertyTooltip("topHinge","the horizontal position of the top hinge (in px)");}
    private int bottomHinge=getPrefs().getInt("LineTracker.bottomHinge",40);
    {setPropertyTooltip("bottomHinge","the horizontal position of the bottom hinge (in px)");}
    private int hingeNumber=getPrefs().getInt("LineTracker.hingeNumber",4);
    {setPropertyTooltip("hingeNumber","the number of hinges to be set");}
    private boolean showRowWindow=false;
    {setPropertyTooltip("showRowWindow","");}
    
    
    private float[][] accumArray;
    private float[][] attentionArray;
    private float[] hingeMax;
    private int[] maxIndex;
    private int[] maxIndexHistory;
    private int[] hingeArray;
    private boolean[] isPaoli;
    private float attentionMax;
    private int sx;
    private int sy;
  
    private int height = 5;
    private int width = 4; //should be even
    private float xValue = 0;
    private float phiValue = 0;

    
    FilterChain preFilterChain;
    private PerspecTransform perspecTransform;
    private OrientationCluster orientationCluster;
    

    public HingeLineTracker(AEChip chip) {
        super(chip);
        
         //build hierachy
        preFilterChain = new FilterChain(chip);
        perspecTransform = new PerspecTransform(chip);
        orientationCluster = new OrientationCluster(chip);

        this.setEnclosedFilter(perspecTransform);
        
        perspecTransform.setEnclosed(true, this);

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
            attentionArray= new float[sx+1][sy];
            hingeMax= new float[hingeNumber];
            hingeArray= new int[hingeNumber];
            maxIndex= new int[hingeNumber];
            maxIndexHistory= new int[hingeNumber];
            isPaoli= new boolean[hingeNumber];
        }
        resetFilter();
        
    }
    
    synchronized public void resetFilter() {
        
        if(!isFilterEnabled()) return;

        if(accumArray!=null){
            for(int i=0;i<accumArray.length;i++) Arrays.fill(accumArray[i],0);
            for(int i=0;i<sx+1;i++) Arrays.fill(attentionArray[i],0);
            Arrays.fill(hingeMax,Float.NEGATIVE_INFINITY);
            Arrays.fill(hingeArray,0);
            Arrays.fill(maxIndex, 0);
            Arrays.fill(maxIndexHistory,0);
            xValue = 0;
            phiValue = 0;
            log.info("HingeLineTracker.reset!");
                
            //set the height of the hinges
            for(int i=0; i<hingeNumber; i++){
                float hingeDiff = (topHinge-bottomHinge)/(hingeNumber-1);
                hingeArray[i] = bottomHinge+(int)(i*hingeDiff);
            }        
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
        
        for(BasicEvent e:in){
            //for each event it is checked if it belongs to the rf of an row cell
            for(int i=0;i<hingeNumber;i++){
                if(e.y <= hingeArray[i]+height && e.y >= hingeArray[i]-height ){
                    updateHingeAccumulator(i,e.x/width);
                    if(e.y<40){
                        if(attentionArray[e.x][e.y]>0.01)
                        updateHingeAccumulator(i,e.x/width);
                    }else{
                        updateHingeAccumulator(i,e.x/width);
                    }
                }
            }
        }
        
        decayAttentionArray();
        updateAttention();
        decayAccumArray();
        updatePaoli();

        if(showRowWindow) {
            checkAccumFrame();
            accumCanvas.repaint();
        }
        return in;
    }

    private void updateHingeAccumulator(int hingeNumber, int x) {
        float f=accumArray[hingeNumber][x];
        f++;
        f=f+attentionFactor*attentionArray[x*width][hingeArray[hingeNumber]];
        accumArray[hingeNumber][x]=f; // update the accumulator
    }
    
    private void updateAttention(){
        //the attention has to be actualized
        for(int i=0;i<hingeNumber;i++){
            int virtualX;
            int virtualY;
            if(isPaoli[i]){
                //the attention has to be set to where the hinge could be in the next event packet --> prediction based on where it was before
                int x0 = width*(2*maxIndex[i]-maxIndexHistory[i]);                
                if(x0<0){
                    for(int x=0; x<attentionRadius; x++){
                        for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
                            if((float)(Math.sqrt((x)*(x)+(y-hingeArray[i])*(y-hingeArray[i])))<attentionRadius){
                                    if(x>=0 && y>=0 && x<sx && y<sy)
                                    attentionArray[x][y]=attentionArray[x][y]+1;       
                            }
                        }
                    }
                }else if(x0 > sx){
                    for(int x=(int)(sx-attentionRadius); x<sx; x++){
                        for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
                            if((float)(Math.sqrt((sx-x)*(sx-x)+(y-hingeArray[i])*(y-hingeArray[i])))<attentionRadius){
                                    if(x>=0 && y>=0 && x<sx && y<sy)
                                    attentionArray[x][y]=attentionArray[x][y]+1;       
                            }
                        }
                    }
                }else{
                    for(int x=(int)(x0-attentionRadius); x<(int)(x0+attentionRadius); x++){
                        for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
                            if((float)(Math.sqrt((x-x0)*(x-x0)+(y-hingeArray[i])*(y-hingeArray[i])))<attentionRadius){
                                    if(x>=0 && y>=0 && x<sx && y<sy)
                                    attentionArray[x][y]=attentionArray[x][y]+1;       
                            }
                        }
                    }
                }
                //the attention has to be put on the spot where the next upper hinge might be
                if(i+1<hingeNumber && isPaoli[i+1]){
                     virtualX = width*(2*maxIndex[i]-maxIndex[i+1]);
                     virtualY = 2*hingeArray[i]-hingeArray[i+1];
                     for(int x=(int)(virtualX-attentionRadius); x<(int)(virtualX+attentionRadius); x++){
                        for(int y=(int)(virtualY-attentionRadius); y<(int)(virtualY+attentionRadius); y++){
                            if((float)(Math.sqrt((x-virtualX)*(x-virtualX)+(y-virtualY)*(y-virtualY)))<attentionRadius){
                                if(x>=0 && y>=0 && x<sx && y<sy)
                                attentionArray[x][y]=attentionArray[x][y]+1;       
                            }
                        }
                    }
                }
                // the attention has to be put on the spot where the next lower hinge might be
                if(i-1>=0 && i<hingeNumber && isPaoli[i-1]){
                     virtualX = width*(2*maxIndex[i]-maxIndex[i-1]);
                     virtualY = 2*hingeArray[i]-hingeArray[i-1];
                     for(int x=(int)(virtualX-attentionRadius); x<(int)(virtualX+attentionRadius); x++){
                        for(int y=(int)(virtualY-attentionRadius); y<(int)(virtualY+attentionRadius); y++){
                            if((float)(Math.sqrt((x-virtualX)*(x-virtualX)+(y-virtualY)*(y-virtualY)))<attentionRadius){
                                    if(x>=0 && y>=0 && x<sx && y<sy)
                                    attentionArray[x][y]=attentionArray[x][y]+1;       
                            }
                        }
                    }
                } 
            } else {
                isPaoli[i]=false;
            }
        }
    }
    
    private void decayAccumArray() {
        if(accumArray==null) return;
        for(int hinge=0; hinge<hingeNumber; hinge++){
            hingeMax[hinge]*=hingeDecayFactor;
            float[] f=accumArray[hinge];
            for(int x=0;x<f.length/width;x++){
                float fval=f[x];
                fval*=hingeDecayFactor;
                if(fval>hingeMax[hinge]) {
                        if(isPaoli[hinge]){
                            if(Math.abs(maxIndex[hinge]-x) < shiftSpace){
                                maxIndexHistory[hinge]=maxIndex[hinge];
                                maxIndex[hinge]=x;
                                hingeMax[hinge]=fval;
                                isPaoli[hinge]=true;
                            }
                        }else{
                            maxIndexHistory[hinge]=maxIndex[hinge];
                            maxIndex[hinge]=x;
                            hingeMax[hinge]=fval;
                            isPaoli[hinge]=true;
                        }
                }
                f[x]=fval;
            }
            if(accumArray[hinge][maxIndex[hinge]]<hingeThreshold){
                maxIndex[hinge]=0;
                isPaoli[hinge]=false;
            }
        }
    }
       
        
    private void decayAttentionArray() {        
        if(accumArray==null) return;
        attentionMax=0;
        for(int x=0; x<sx; x++){
            for(int y=0; y<sy; y++){
                    attentionArray[x][y]*=attentionDecayFactor;
                    if(attentionArray[x][y]>attentionMax){
                        attentionMax=attentionArray[x][y];
                    }
                    setAttention(x,y);
                }
            }
    }
    
    public void updatePaoli() {
        for(int i=0; i<hingeNumber; i++){
                //---left---
                //line cutoff
                if(isPaoli[i] && i-1>0 && (maxIndex[i]>(sx/width)-2 || maxIndex[i]<2) && isPaoli[i-1]) isPaoli[i-1]=false;
            }
    }
    
    GLU glu=null;
    GLUquadric wheelQuad;    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        if(hingeArray == null) return;
        GL gl=drawable.getGL();
        
        gl.glColor3f(1,1,1);

        gl.glLineWidth(3);
        
        if(glu==null) glu=new GLU();
        if(wheelQuad==null) wheelQuad = glu.gluNewQuadric();
        gl.glPushMatrix();
        //attention
        gl.glPointSize(2);
        for(int x=0; x<sx; x++){
            for(int y=0; y<sy; y++){
                gl.glBegin(GL.GL_POINTS);
                gl.glColor3f(attentionArray[x][y]/attentionMax,attentionArray[x][y]/attentionMax,attentionArray[x][y]/attentionMax);
                gl.glVertex2i(x,y);
                gl.glEnd();
            }
        }
        //points
         gl.glPointSize(8);
        for(int i=0; i<hingeNumber;i++){
            gl.glColor3f(1,1,1);
            gl.glBegin(GL.GL_POINTS);
            gl.glVertex2f(width*maxIndex[i], hingeArray[i]);
            gl.glEnd();
        }
        // line
        gl.glColor3f(1,1,1);
        gl.glBegin(GL.GL_LINE_STRIP);
        for(int i=0; i<hingeNumber;i++){
            if(isPaoli[i]){
                gl.glVertex2i(width*maxIndex[i],hingeArray[i]);
            }
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
                gl.glScalef(width*drawable.getWidth()/sx,drawable.getHeight()/sy,1);
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                //left
                for(int i=0;i<hingeNumber;i++){
                    for(int j=0;j<accumArray[i].length/width;j++){
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
    
    public void setAttention(int x, int y){
        if(orientationCluster == null) return;
        //if(orientationCluster.attention[x][y] == null) orientationCluster.attention[x][y]=attentionArray[x][y];
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
    
    
    public float getPhi(){
        if(accumArray==null) return 0;
        float phiTotal =0;
        int phiNumber =0;
        for(int i=0; i<hingeNumber-1; i++){
            if (isPaoli[i] && isPaoli[i+1]){
                phiNumber++;
                phiTotal = phiTotal + (float)(phiNumber*Math.atan((width*(maxIndex[i+1]-maxIndex[i]))/(float)(hingeArray[i+1]-hingeArray[i]))*2/(Math.PI));
        }}
        if( phiNumber > 2){
            phiValue = - phiTotal/phiNumber;
            return - phiTotal/phiNumber;}
        else
            return phiValue;
    }

    public float getX(){
        if(accumArray==null) return 0;
        float xTotal = 0;
        float xNumber = 0;
        for(int i=0; i<hingeNumber-1; i++){
            if (isPaoli[i]){
                xNumber++;
                xTotal = xTotal + (float)((2*width*maxIndex[i]/(float)(sx))-1);
            }
        }
        if (xNumber > 2){
            xValue = xTotal/xNumber;
            return xTotal/xNumber;}
        else
            return xValue;
    }

    public int getShiftSpace() {
        return shiftSpace;
    }
    
    public void setShiftSpace(int shiftSpace) {
        this.shiftSpace = shiftSpace;
        getPrefs().putInt("LineTracker.shiftSpace",shiftSpace);
    }
    
    public float getHingeThreshold() {
        return hingeThreshold;
    }
    
    public void setHingeThreshold(float hingeThreshold) {
        this.hingeThreshold = hingeThreshold;
        getPrefs().putFloat("LineTracker.hingeThreshold",hingeThreshold);
        resetFilter();
    }
    
    public int getBottomHinge() {
        return bottomHinge;
    }
    
    public void setBottomHinge(int bottomHinge) {
        this.bottomHinge = bottomHinge;
        getPrefs().putInt("LineTracker.bottomHinge",bottomHinge);
        resetFilter();
    }
    
    public int getTopHinge() {
        return topHinge;
    }
    
    public void setTopHinge(int topHinge) {
        this.topHinge = topHinge;
        getPrefs().putInt("LineTracker.topHinge",topHinge);
        resetFilter();
    }
    
    public int getHingeNumber() {
        return hingeNumber;
    }
    
    public void setHingeNumber(int hingeNumber) {
        this.hingeNumber = hingeNumber;
        getPrefs().putInt("LineTracker.hingeNumber",hingeNumber);
    }
    
    public float getAttentionRadius() {
        return attentionRadius;
    }
    
    public void setAttentionRadius(float attentionRadius) {
        this.attentionRadius = attentionRadius;
        getPrefs().putFloat("LineTracker.attentionRadius",attentionRadius);
    }
    
    public float getHingeDecayFactor() {
        return hingeDecayFactor;
    }
    
    public void setAttentionFactor(float attentionFactor) {
        this.attentionFactor = attentionFactor;
        getPrefs().putFloat("LineTracker.attentionFactor",attentionFactor);
    }
    
    public float getAttentionFactor() {
        return attentionFactor;
    }    
    
    public void setHingeDecayFactor(float hingeDecayFactor) {
        if(hingeDecayFactor<0)hingeDecayFactor=0;else if(hingeDecayFactor>1)hingeDecayFactor=1;
        this.hingeDecayFactor = hingeDecayFactor;
        getPrefs().putFloat("LineTracker.hingeDecayFactor",hingeDecayFactor);
    }

    public float getAttentionDecayFactor() {
        return attentionDecayFactor;
    }
    
    public void setAttentionDecayFactor(float attentionDecayFactor) {
        if(attentionDecayFactor<0)attentionDecayFactor=0;else if(attentionDecayFactor>1)attentionDecayFactor=1;
        this.attentionDecayFactor = attentionDecayFactor;
        getPrefs().putFloat("LineTracker.attentionDecayFactor",attentionDecayFactor);
    }
             
    public PerspecTransform getPerspec() {
        return perspecTransform;
    }

    public void setPerspec(PerspecTransform perspecTransform) {
        this.perspecTransform = perspecTransform;
    }
}



