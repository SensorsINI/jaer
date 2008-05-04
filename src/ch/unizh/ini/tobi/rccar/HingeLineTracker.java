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

    private float hingeThreshold=getPrefs().getFloat("LineTracker.hingeThreshold",2.5f);
    {setPropertyTooltip("hingeThreshold","the threshold for the hinge to react");}
    private float ellipseFactor=getPrefs().getFloat("LineTracker.ellipseFactor",1.1f);
    {setPropertyTooltip("ellipseFactor","the size of the attention ellipse");}
    private float attentionFactor=getPrefs().getFloat("LineTracker.attentionFactor",2);
    {setPropertyTooltip("attentionFactor","how much is additionally added to the accumArray if the attention is on a certain spike");}
    private float hingeDecayFactor=getPrefs().getFloat("LineTracker.hingeDecayFactor",0.6f);
    {setPropertyTooltip("hingeDecayFactor","hinge accumulator cells are multiplied by this factor before each frame, 0=no memory, 1=infinite memory");}
    private float attentionDecayFactor=getPrefs().getFloat("LineTracker.attentionDecayFactor",0.6f);
    {setPropertyTooltip("attentionDecayFactor","the slope of attention decay, 0=no memory, 1=infinite memory");}
    private int shiftSpace=getPrefs().getInt("LineTracker.shiftSpace",5);
    {setPropertyTooltip("shiftSpace","minimal distance between paoli hinge and seperation");}
    private boolean showRowWindow=true;
    {setPropertyTooltip("showRowWindow","");}
    
    
    private float[][] accumArray;
    private float[][][] attentionArray;
    private float[] sideMax;
    private float[] hingeMax;
    private int[] maxIndex;
    private int[] enterMaxIndex;
    private int[] leaveMaxIndex;
    private int[] hingeArray;
    private int[] seperator ;
    private boolean[] isPaoli;
    private float attentionMax;
    private int sx;
    private int sy;
    private int hingeNumber = 12;
    private int attentionRadius = 12;
    private int height = 4;
    private int width = 4; //should be even

    
    FilterChain preFilterChain;
    private OrientationCluster orientationCluster;
    

    public HingeLineTracker(AEChip chip) {
        super(chip);
        
         //build hierachy
        preFilterChain = new FilterChain(chip);
        orientationCluster = new OrientationCluster(chip);

        this.setEnclosedFilter(orientationCluster);
        
        orientationCluster.setEnclosed(true, this);

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
            attentionArray= new float[sx+1][sy][2];
            hingeMax= new float[hingeNumber];
            sideMax= new float[2];
            enterMaxIndex= new int[2];
            leaveMaxIndex= new int[2];
            hingeArray= new int[hingeNumber];
            maxIndex= new int[hingeNumber];
            seperator = new int[hingeNumber/2+1];
            isPaoli= new boolean[hingeNumber];
        }
        resetFilter();
        
    }
    
    synchronized public void resetFilter() {
        
        if(!isFilterEnabled()) return;

        if(accumArray!=null){
            for(int i=0;i<accumArray.length;i++) Arrays.fill(accumArray[i],0);
            for(int i=0;i<sx+1;i++) for(int j=0; j<sy; j++) Arrays.fill(attentionArray[i][j],0);
            Arrays.fill(hingeMax,Float.NEGATIVE_INFINITY);
            Arrays.fill(hingeArray,0);
            Arrays.fill(maxIndex, 0);
            Arrays.fill(sideMax,0);
            Arrays.fill(enterMaxIndex,0);
            Arrays.fill(leaveMaxIndex,0);
            Arrays.fill(seperator,chip.getSizeX()/(2*width));
            log.info("HingeLineTracker.reset!");
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
        
        hingeArray[0] = 15;
        hingeArray[1] = 15;
        hingeArray[2] = 30;
        hingeArray[3] = 30;
        hingeArray[4] = 45;
        hingeArray[5] = 45;
        hingeArray[6] = 60;
        hingeArray[7] = 60;
        hingeArray[8] = 75;
        hingeArray[9] = 75;
        hingeArray[10] = 90;
        hingeArray[11] = 90;
        

        for(BasicEvent e:in){
            //for each event it is checked if it belongs to the rf of an row cell
            for(int i=0;i<hingeNumber;i+=2){
                if(e.y <= hingeArray[i]+height && e.y >= hingeArray[i]-height ){
                    if(e.y<40){
                        if(e.x/width<(seperator[i/2])){
                            if(attentionArray[e.x][e.y][0]>0.01)
                            updateHingeAccumulator(i,e.x/width,0);                                 
                        } else {
                            if(attentionArray[e.x][e.y][1]>0.01)
                            updateHingeAccumulator(i+1,e.x/width,1);
                        }
                    }else{
                        if(e.x/width<(seperator[i/2])){
                          updateHingeAccumulator(i,e.x/width,0);                                 
                        } else {
                           updateHingeAccumulator(i+1,e.x/width,1);
                        }
                    }
                }
            }
        }
        
        decayAttentionArray();
        //the attention has to be actualized
        for(int i=0;i<hingeNumber;i++){
            int virtualX;
            int virtualY;
            if(isPaoli[i]){
                for(int x=(int)(width*maxIndex[i]-attentionRadius); x<(int)(width*maxIndex[i]+attentionRadius); x++){
                        for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
                            if((float)(Math.sqrt((x-width*maxIndex[i])*(x-width*maxIndex[i])+(y-hingeArray[i])*(y-hingeArray[i])))<attentionRadius){
                                    if(x>=0 && y>=0 && x<sx && y<sy)
                                    attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;       
                            }
                        }
                    }
                 if(i>=0 && i+2<hingeNumber && isPaoli[i+2]){
                     virtualX = width*(2*maxIndex[i]-maxIndex[i+2]);
                     virtualY = 2*hingeArray[i]-hingeArray[i+2];
                     for(int x=(int)(virtualX-attentionRadius); x<(int)(virtualX+attentionRadius); x++){
                        for(int y=(int)(virtualY-attentionRadius); y<(int)(virtualY+attentionRadius); y++){
                            if((float)(Math.sqrt((x-virtualX)*(x-virtualX)+(y-virtualY)*(y-virtualY)))<attentionRadius){
                                if(x>=0 && y>=0 && x<sx && y<sy)
                                attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;       
                            }
                        }
                    }
                }
                if(i-2>=0 && i+2<hingeNumber && isPaoli[i-2]){
                     virtualX = width*(2*maxIndex[i]-maxIndex[i-2]);
                     virtualY = 2*hingeArray[i]-hingeArray[i-2];
                     for(int x=(int)(virtualX-attentionRadius); x<(int)(virtualX+attentionRadius); x++){
                        for(int y=(int)(virtualY-attentionRadius); y<(int)(virtualY+attentionRadius); y++){
                            if((float)(Math.sqrt((x-virtualX)*(x-virtualX)+(y-virtualY)*(y-virtualY)))<attentionRadius){
                                    if(x>=0 && y>=0 && x<sx && y<sy)
                                    attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;       
                            }
                        }
                    }
                } 
            } else {
                isPaoli[i]=false;
            }
        }
        decayAccumArray();
        //modulateAttention();
        updateSeperation();
        updatePaoli();

        
        if(showRowWindow) {
            checkAccumFrame();
            accumCanvas.repaint();
        }
        return in;
    }

    private void updateHingeAccumulator(int hingeNumber, int x, int leori) {
        float f=accumArray[hingeNumber][x];
        f++;
        f=f+attentionFactor*attentionArray[x][hingeArray[hingeNumber]][leori];
        accumArray[hingeNumber][x]=f; // update the accumulator
    }
    
    private void decayAccumArray() {
        if(accumArray==null) return;
        sideMax[0]*=hingeDecayFactor;
        sideMax[1]*=hingeDecayFactor;
        enterMaxIndex[0]=0;
        enterMaxIndex[1]=0;
        leaveMaxIndex[0]=0;
        leaveMaxIndex[1]=0;
        for(int hinge=0; hinge<hingeNumber; hinge++){
            hingeMax[hinge]*=hingeDecayFactor;
            float[] f=accumArray[hinge];
            for(int x=0;x<f.length/width;x++){
                float fval=f[x];
                fval*=hingeDecayFactor;
                if(fval>hingeMax[hinge]) {
                        if(isPaoli[hinge]){
                            if(Math.abs(maxIndex[hinge])-x < shiftSpace){
                                maxIndex[hinge]=x;
                                hingeMax[hinge]=fval;
                                isPaoli[hinge]=true;
                                hingeMax[hinge+1-hinge%2*2]=fval;
                            }
                        }else{
                            maxIndex[hinge]=x;
                            hingeMax[hinge]=fval;
                            isPaoli[hinge]=true;
                            hingeMax[hinge+1-hinge%2*2]=fval;    
                        }
                }
                f[x]=fval;
            }
            if(accumArray[hinge][maxIndex[hinge]]<hingeThreshold){
                maxIndex[hinge]=hinge%2*sx/width;
                isPaoli[hinge]=false;
            }
            //enter-leave update
            if(accumArray[hinge][1]>sideMax[0]){
                sideMax[0]=accumArray[hinge][1];
                if(hinge%2==0){
                    enterMaxIndex[0]=hingeArray[hinge];
                }else{
                    leaveMaxIndex[1]=hingeArray[hinge];
                }
            }
            if(accumArray[hinge][sx/width-1]>sideMax[1]){
                sideMax[1]=accumArray[hinge][sx/width-1];
                if(hinge%2==1){
                    enterMaxIndex[1]=hingeArray[hinge];
                }else{
                    leaveMaxIndex[0]=hingeArray[hinge];
                }
            }
        }
    }
       
        
    private void decayAttentionArray() {        
        if(accumArray==null) return;
        attentionMax=0;
        for(int x=0; x<sx; x++){
            for(int y=0; y<sy; y++){
                for(int leori=0; leori<2; leori++){
                    attentionArray[x][y][leori]*=attentionDecayFactor;
                    if(attentionArray[x][y][leori]>attentionMax){
                        attentionMax=attentionArray[x][y][leori];
                    }
                    setAttention(x,y);
                }
            }
        }
    }
    
    private void modulateAttention(){
        for(int i=0; i<hingeNumber; i++){
            if(isPaoli[i]){
                 int left;
                 int right;
                 int leftY;
                 int rightY;
                 if(i>=0 && i+2<hingeNumber && isPaoli[i+2]){
                     float distance = (float)Math.sqrt((maxIndex[i]-maxIndex[i+2])*(maxIndex[i]-maxIndex[i+2])+(hingeArray[i]-hingeArray[i+2])*(hingeArray[i]-hingeArray[i+2]));
                     float radius = distance*ellipseFactor;
                     if(maxIndex[i]<maxIndex[i+2]){
                          left = 2*maxIndex[i]-maxIndex[i+2];
                          leftY = 2*hingeArray[i]-hingeArray[i+2];
                          right = maxIndex[i];
                          rightY = hingeArray[i];
                     } else {
                          left = maxIndex[i];
                          leftY = hingeArray [i];
                          right = 2*maxIndex[i]-maxIndex[i+2];
                          rightY = 2*hingeArray[i]-hingeArray[i+2];
                     }
                     for(int x=(int)(left-radius/2); x<(int)(right+radius/2); x++){
                        for(int y=(int)(2*hingeArray[i]-hingeArray[i+2]-radius/2); y<hingeArray[i]+radius/2; y++){
                            if((float)(Math.sqrt((x-left)*(x-left)+(y-leftY)*(y-leftY))+
                                Math.sqrt((x-right)*(x-right)+(y-rightY)*(y-rightY)))<radius){
                                for(int px=0; px<width; px++){
                                    if(width*x+px>=0 && y>=0 && width*x+px<sx && y<sy)
                                    attentionArray[width*x+px][y][i%2]=attentionArray[width*x+px][y][i%2]+1;       
                                }
                            }
                        }
                    }
                }
                if(i-2>=0 && i+2<hingeNumber && isPaoli[i-2]){
                     float distance = (float)Math.sqrt((maxIndex[i]-maxIndex[i-2])*(maxIndex[i]-maxIndex[i-2])+(hingeArray[i]-hingeArray[i-2])*(hingeArray[i]-hingeArray[i-2]));
                     float radius = distance*ellipseFactor;
                     if(maxIndex[i]<maxIndex[i-2]){
                          left = 2*maxIndex[i]-maxIndex[i-2];
                          leftY = 2*hingeArray[i]-hingeArray[i-2];
                          right = maxIndex[i];
                          rightY = hingeArray[i];
                     } else {
                          left = maxIndex[i];
                          leftY = hingeArray [i];
                          right = 2*maxIndex[i]-maxIndex[i-2];
                          rightY = 2*hingeArray[i]-hingeArray[i-2];
                     }
                     for(int x=(int)(left-radius/2); x<(int)(right+radius/2); x++){
                        for(int y=(int)(hingeArray[i]-radius/2); y<(int)(2*hingeArray[i]-hingeArray[i-2]+radius/2); y++){
                            if((float)(Math.sqrt((x-left)*(x-left)+(y-leftY)*(y-leftY))+
                                Math.sqrt((x-right)*(x-right)+(y-rightY)*(y-rightY)))<radius){
                                for(int px=0; px<width; px++){
                                    if(width*x+px>=0 && y>=0 && width*x+px<sx && y<sy)
                                    attentionArray[width*x+px][y][i%2]=attentionArray[width*x+px][y][i%2]+1;       
                                }
                            }
                        }
                    }
                } 
            }
        }
    }
    
    public void updateSeperation(){
        for(int i=0; i<hingeNumber/2; i++){
            //check if seperator should be pulled back to the middle
            if(maxIndex[2*i]*width<sx/2 && isPaoli[2*i]){
                seperator[i]= sx/(2*width);
            }
            if(maxIndex[2*i+1]*width>sx/2 && isPaoli[2*i+1]){
                seperator[i]= sx/(2*width);
            }
            //check if seperator is on wrong side of attention
            /*if(2*i-2>0 && 2*i-4>0 && isPaoli[2*i-2] && isPaoli[2*i-4] && seperator){
                
            }*/
            
            //check if seperator should be pushed away
            while(attentionArray[width*seperator[i]][hingeArray[2*i]][0]-0.01>attentionArray[width*seperator[i]][hingeArray[2*i]][1]+0.01 && (seperator[i])*width<sx ){
                seperator[i]=seperator[i]+1;
            }
            while(attentionArray[width*seperator[i]][hingeArray[2*i]][0]+0.01<attentionArray[width*seperator[i]][hingeArray[2*i]][1] && seperator[i]>0){
                seperator[i]=seperator[i]-1;
            }
            //check if seperator is inbetween two parts of a line
            if(2*i+2 < hingeNumber && 2*i-2>0 && isPaoli[2*i-2] && isPaoli[2*i+2] && seperator[i]<(maxIndex[2*i-2]+maxIndex[2*i+2])/2){
                maxIndex[2*i]=(maxIndex[2*i-2]+maxIndex[2*i+2])/2;
                seperator[i]=(seperator[i-1]+seperator[i+1])/2;
                isPaoli[2*i+2]=true;
            }
            if(2*i+3 < hingeNumber && 2*i-1>0 && isPaoli[2*i-1] && isPaoli[2*i+3] && seperator[i]>(maxIndex[2*i-1]+maxIndex[2*i+3])/2){
                maxIndex[2*i+1]=(maxIndex[2*i-1]+maxIndex[2*i+3])/2;
                isPaoli[2*i+1]=true;
                seperator[i]=(seperator[i-1]+seperator[i+1])/2;
            }
        }
    }
    
    public void updatePaoli() {
        if(enterMaxIndex[0]!=0){
            for(int i=0; i<hingeNumber; i+=2){
                if(hingeArray[i]<enterMaxIndex[0])
                    isPaoli[i]=false;
            }
        }
        if(enterMaxIndex[1]!=0){
            for(int i=1; i<hingeNumber; i+=2){
                if(hingeArray[i]<enterMaxIndex[0])
                    isPaoli[i]=false;
            }
        }
        if(leaveMaxIndex[0]!=0){
            for(int i=0; hingeArray[i]<leaveMaxIndex[0]; i+=2){
                for(int j=0; j<2*sx/(3*width);j++){
                    accumArray[i][j]=0;
                }
            }
        }
        if(leaveMaxIndex[1]!=0){
            for(int i=0; hingeArray[i]<leaveMaxIndex[0]; i+=2){
                for(int j=sx/(3*width); j<sx/width;j++){
                    accumArray[i][j]=0;
                }
            }
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
                gl.glColor3f(attentionArray[x][y][0]/attentionMax,attentionArray[x][y][1]/attentionMax,(attentionArray[x][y][0]+attentionArray[x][y][1])/attentionMax);
                gl.glVertex2i(x,y);
                gl.glEnd();
            }
        }
        //seperator
        gl.glPointSize(8);
        gl.glColor3f(1,1,1);
        for(int i=0; i<hingeNumber/2;i++){
            gl.glBegin(GL.GL_POINTS);
            gl.glVertex2f(width*seperator[i], hingeArray[2*i]);
            gl.glEnd();
        }
        gl.glBegin(GL.GL_LINE_STRIP);
        for(int i=0; i<hingeNumber/2;i++){
            gl.glVertex2i(width*seperator[i], hingeArray[2*i]);
        }
        gl.glEnd();
        //points
        for(int i=0; i<hingeNumber;i++){
            gl.glColor3f(1-i%2,i%2,1);
            gl.glBegin(GL.GL_POINTS);
            gl.glVertex2f(width*maxIndex[i], hingeArray[i]);
            gl.glEnd();
        }
        //left line
        gl.glColor3f(1,0,1);
        gl.glBegin(GL.GL_LINE_STRIP);
        for(int i=0; i<hingeNumber;i+=2){
            if(isPaoli[i]){
                gl.glVertex2i(width*maxIndex[i],hingeArray[i]);
            }
        }
        //right line
        gl.glEnd();
        gl.glColor3f(0,1,1);
        gl.glBegin(GL.GL_LINE_STRIP);
        for(int i=1; i<hingeNumber;i+=2){
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
                for(int i=0;i<hingeNumber;i+=2){
                    for(int j=0;j<seperator[i/2];j++){
                        float f=accumArray[i][j]/hingeMax[i];
                        gl.glColor3f(f,0,f);
                        gl.glRectf(j,hingeArray[i]-height,j+1,hingeArray[i]+height);
                    }
                    gl.glColor3f(1,0,0);
                    gl.glRectf(maxIndex[i],hingeArray[i]-height,maxIndex[i]+1,hingeArray[i]+height);
                }
                //right
                for(int i=1;i<hingeNumber;i+=2){
                    for(int j=seperator[(i-1)/2];j<accumArray[i].length/width;j++){
                        float f=accumArray[i][j]/hingeMax[i];
                        gl.glColor3f(0,f,f);
                        gl.glRectf(j,hingeArray[i]-height,j+1,hingeArray[i]+height);
                    }
                    gl.glColor3f(1,0,0);
                    gl.glRectf(maxIndex[i],hingeArray[i]-height,maxIndex[i]+1,hingeArray[i]+height);
                }
                //seperator
                for(int i=0;i<hingeNumber/2;i++){
                    gl.glColor3f(1,1,1);
                    gl.glRectf(seperator[i],hingeArray[2*i]-height,seperator[i]+1,hingeArray[2*i]+height);
                }
                //enter,leaveMax
                // left side
                if(sideMax[0]>hingeThreshold)
                if(enterMaxIndex[0]!=0){
                    gl.glColor3f(0.5f,0,1);
                    gl.glRectf(0,enterMaxIndex[0]-height,1,enterMaxIndex[0]+height);
                }
                if(leaveMaxIndex[1]!=0){
                    gl.glColor3f(0,0.5f,1);
                    gl.glRectf(0,leaveMaxIndex[1]-height,1,leaveMaxIndex[1]+height);
                }
                //right side
                if(sideMax[1]>hingeThreshold)
                    if(enterMaxIndex[1]!=0){
                        gl.glColor3f(0,1,0.5f);
                        gl.glRectf((sx/width),enterMaxIndex[1]-height,(sx/width)+1,enterMaxIndex[1]+height);
                    }
                if(leaveMaxIndex[0]!=0){
                        gl.glColor3f(1,0,0.5f);
                        gl.glRectf((sx/width),leaveMaxIndex[0]-height,(sx/width)+1,leaveMaxIndex[0]+height);
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
        orientationCluster.attention[x][y]=attentionArray[x][y][0]+attentionArray[x][y][1];
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
    
    public float getLeftPhi(){
        int lowID = hingeNumber-2;
        int upID = 0;
        for(int i=0; i<hingeNumber; i+=2){
            if(isPaoli[i]) upID=i;
            if(isPaoli[hingeNumber-i-2]) lowID=hingeNumber-i-2;
        }
        if(lowID<upID){
            System.out.println("leftPhi");
            System.out.println(maxIndex[upID]);
            System.out.println(maxIndex[lowID]);
            System.out.println(hingeArray[upID]);
            System.out.println(hingeArray[lowID]);
            System.out.println(Math.tan((width*(maxIndex[upID]-maxIndex[lowID]))/(hingeArray[upID]-hingeArray[lowID])));
            return (float)(Math.tan((width*(maxIndex[upID]-maxIndex[lowID]))/(hingeArray[upID]-hingeArray[lowID])));
        }
        else return 0;
    }
    
    public float getRightPhi(){
        int lowID = hingeNumber-1;
        int upID = 0;
        for(int i=1; i<hingeNumber; i+=2){
            if(isPaoli[i]) upID=i;
            if(isPaoli[hingeNumber-i]) lowID=hingeNumber-i;
        }
        if(lowID<upID){
            System.out.println("rightPhi");
            System.out.println(maxIndex[upID]);
            System.out.println(maxIndex[lowID]);
            System.out.println(hingeArray[upID]);
            System.out.println(hingeArray[lowID]);
            System.out.println(Math.tan((width*(maxIndex[upID]-maxIndex[lowID]))/(hingeArray[upID]-hingeArray[lowID])));
            return (float)(Math.tan((width*(maxIndex[upID]-maxIndex[lowID]))/(hingeArray[upID]-hingeArray[lowID])));
        }
        else return 0;
    }
    
    public float getLeftX(){
        int lowID = hingeNumber-2;
        for(int i=0; i<hingeNumber; i+=2){
            if(isPaoli[hingeNumber-i-2]) lowID=hingeNumber-i-2;
        }
        if(lowID == hingeNumber-2){
            return -1;
        } else {
            System.out.println("rightX");
            System.out.println(maxIndex[lowID]);
            System.out.println((float)(width*maxIndex[lowID]/(sx)));
            return (float)(width*maxIndex[lowID]/(sx));
        }
    }
    
    public float getRightX(){
        int lowID = hingeNumber-1;
        for(int i=1; i<hingeNumber; i+=2){
            if(isPaoli[hingeNumber-i]) lowID=hingeNumber-i;
        }
        if(lowID == hingeNumber-1){
            return 1;
        } else {
            System.out.println("leftX");
            System.out.println(maxIndex[lowID]);
            System.out.println((float)((width*maxIndex[lowID]/(sx))-1));
            return (float)((width*maxIndex[lowID]/(sx))-1);
        }
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
    }
    
    public float getEllipseFactor() {
        return ellipseFactor;
    }
    
    public void setEllipseFactor(float ellipseFactor) {
        if(ellipseFactor<1)ellipseFactor=1;else if(ellipseFactor>10)ellipseFactor=10;
        this.ellipseFactor = ellipseFactor;
        getPrefs().putFloat("LineTracker.ellipseFactor",ellipseFactor);
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
}


