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
public class HingeLaneTracker extends EventFilter2D implements FrameAnnotater, Observer, HingeDetector {

    private float hingeThreshold=getPrefs().getFloat("LineTracker.hingeThreshold",2.5f);
    {setPropertyTooltip("hingeThreshold","the threshold for the hinge to react");}
    private float attentionRadius=getPrefs().getFloat("LineTracker.attentionRadius",12);
    {setPropertyTooltip("attentionRadius","the size of the attention balls");}
    private float seperatorOffset=getPrefs().getFloat("LineTracker.seperatorOffset",5);
    {setPropertyTooltip("seperatorOffset","handles the width of the seperator line");}
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
    private float[] hingeMax;
    private int[] maxIndex;
    private int[] maxIndexHistory;
    private int[] hingeArray;
    private int[] seperator ;
    private boolean[] isPaoli;
    private boolean[] isWaiting;
    private float attentionMax;
    private int sx;
    private int sy;
    
    private int hingeNumber = 12;
    private int height = 4;
    private int width = 4; //should be even

    
    FilterChain preFilterChain;
    private OrientationCluster orientationCluster;
    

    public HingeLaneTracker(AEChip chip) {
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
            hingeArray= new int[hingeNumber];
            maxIndex= new int[hingeNumber];
            maxIndexHistory= new int[hingeNumber];
            seperator = new int[hingeNumber/2+1];
            isPaoli= new boolean[hingeNumber];
            isWaiting = new boolean[2];
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
            Arrays.fill(maxIndexHistory,0);
            Arrays.fill(seperator,chip.getSizeX()/(2*width));
            Arrays.fill(isWaiting, false);
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
                
        //the distance between the hinge-rows must be constant!
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
                if((!isWaiting[0] && !isWaiting[1]) || ((isWaiting[0] && e.x>sx/2) || (isWaiting[1] && e.x<sx/2))){
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
                }}
            }
        }
        
        decayAttentionArray();
        updateAttention();
        decayAccumArray();
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
                                    attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;       
                            }
                        }
                    }
                }else if(x0 > sx){
                    for(int x=(int)(sx-attentionRadius); x<sx; x++){
                        for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
                            if((float)(Math.sqrt((sx-x)*(sx-x)+(y-hingeArray[i])*(y-hingeArray[i])))<attentionRadius){
                                    if(x>=0 && y>=0 && x<sx && y<sy)
                                    attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;       
                            }
                        }
                    }
                }else{
                    for(int x=(int)(x0-attentionRadius); x<(int)(x0+attentionRadius); x++){
                        for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
                            if((float)(Math.sqrt((x-x0)*(x-x0)+(y-hingeArray[i])*(y-hingeArray[i])))<attentionRadius){
                                    if(x>=0 && y>=0 && x<sx && y<sy)
                                    attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;       
                            }
                        }
                    }
                }
                //the attention has to be put on the spot where the next upper hinge might be
                if(i+2<hingeNumber && isPaoli[i+2]){
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
                // the attention has to be put on the spot where the next lower hinge might be
                if(i-2>=0 && i<hingeNumber && isPaoli[i-2]){
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
                                hingeMax[hinge+1-hinge%2*2]=fval;
                            }
                        }else{
                            maxIndexHistory[hinge]=maxIndex[hinge];
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
            if(isPaoli[hinge] && ((hinge-2>0 && isPaoli[hinge-2]) || (hinge+2<hingeNumber && isPaoli[hinge+2]))) isWaiting[hinge%2]=false;
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
    
    
    public void updateSeperation(){
       
        for(int i=0; i<hingeNumber/2; i++){
            //check if seperator should be pulled back to the middle
            //if both hinges are not part of the line the seperator slowly goes back to the middle
            if(!isPaoli[2*i] && !isPaoli[2*i+1] &&!isWaiting[0] && !isWaiting[1]) seperator[i]=(int)(sx/(2*width) + 0.9*(seperator[i]-sx/(2*width)));
            //if the hinge is in on the right side of the 
            if(maxIndex[2*i]*width<sx/2 && isPaoli[2*i]){
                seperator[i]= sx/(2*width);
            }
            if(maxIndex[2*i+1]*width>sx/2 && isPaoli[2*i+1]){
                seperator[i]= sx/(2*width);
            }
            //check if seperator should be pushed away
            while(attentionArray[width*seperator[i]][hingeArray[2*i]][0]-0.01>attentionArray[width*seperator[i]][hingeArray[2*i]][1] && (seperator[i])*width<sx ){
                seperator[i]=seperator[i]+1;
            }
            while(attentionArray[width*seperator[i]][hingeArray[2*i]][0]<attentionArray[width*seperator[i]][hingeArray[2*i]][1]-0.01 && seperator[i]>0){
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
            //waiting seperator point pull their neighbors
            if(isWaiting[i%2] && i%2 == 0 && seperator[i]==sx/width){
                for(int j=0; j<hingeNumber/width; j++) seperator[j]=sx/width;
            }
            if(isWaiting[i%2] && i%2 == 1 && seperator[i]==0){
                for(int j=0; j<hingeNumber/width; j++) seperator[j]=0;
            }
        }
    }
    
    public void updatePaoli() {
        for(int i=0; i<hingeNumber; i++){
            if(i%2 == 1){
                //---left---
                //line cutoff
                if(isPaoli[i] && i-2>0 && (maxIndex[i]>(sx/width)-2 || maxIndex[i]<2) && isPaoli[i-2]) isPaoli[i-2]=false;
                //leaving line
                if(maxIndex[i]<=1){
                    for(int j=0; j<hingeNumber/2; j++) seperator[j]=0;
                    isWaiting[1]=true;
                }
                
            } else {
                //---right---
                //line cutoff
                if(isPaoli[i] && i-2>0 && (maxIndex[i]>(sx/width)-2 || maxIndex[i]<2) && isPaoli[i-2]) isPaoli[i-2]=false;
                //leaving line
                if(maxIndex[i]>=(sx/width)-2){
                    for(int j=0; j<hingeNumber/2; j++) seperator[j]=sx/width;
                    isWaiting[0]=true;
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
        //right line
        gl.glColor3f(1,0,1);
        gl.glBegin(GL.GL_LINE_STRIP);
        for(int i=0; i<hingeNumber;i+=2){
            if(isPaoli[i]){
                gl.glVertex2i(width*maxIndex[i],hingeArray[i]);
            }
        }
        //left line
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
    
    public float getPhi(){
        if(accumArray == null) return 0;
        return getRightPhi()+getLeftPhi();
    }
    
    public float getRightPhi(){
        float phiTotal =0;
        int phiNumber =0;
        for(int i=1; i<hingeNumber-2; i+=2){
            if (isPaoli[i] && isPaoli[i+2]){
                phiNumber++;
                phiTotal = phiTotal + (float)(phiNumber*Math.tanh((width*(maxIndex[i+2]-maxIndex[i]))/(float)(hingeArray[i+2]-hingeArray[i]))*2/(Math.PI));
        }}
        if( phiNumber != 0)
            return - phiTotal/phiNumber;
        else
            return 0;
    }
    
    public float getLeftPhi(){
        float phiTotal =0;
        int phiNumber =0;
        for(int i=0; i<hingeNumber-2; i+=2){
            if(isPaoli[i] && isPaoli[i+2]){
                phiNumber++;
                phiTotal = phiTotal + (float)(phiNumber*Math.tanh((width*(maxIndex[i+2]-maxIndex[i]))/(float)(hingeArray[i+2]-hingeArray[i]))*2/(Math.PI));
        }}
        if(phiNumber != 0)
            return  - phiTotal/phiNumber;
        else
            return 0;
    }
    
    public float getX(){
        if(accumArray == null) return 0;
        return getRightX()+getLeftX();
    }
    
    public float getRightX(){
        float xTotal = 0;
        float xNumber = 0;
        for(int i=1; i<hingeNumber-2; i+=2){
            if (isPaoli[i]){
                xNumber++;
                xTotal = xTotal + (float)((2*width*maxIndex[i]/(float)(sx))-1);
            }
        }
        if (xNumber != 0)
            return xTotal/xNumber;
        else
            return 1;
    }

        public float getLeftX(){
        float xTotal = 0;
        float xNumber = 0;
        for(int i=0; i<hingeNumber-2; i+=2){
            if (isPaoli[i]){
                xNumber++;
                xTotal = xTotal + (float)((2*width*maxIndex[i]/(float)(sx))-1);
            }
        }
        if (xNumber != 0)
            return xTotal/xNumber;
        else
            return -1;
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
    
    public float getAttentionRadius() {
        return attentionRadius;
    }
    
    public void setAttentionRadius(float attentionRadius) {
        this.attentionRadius = attentionRadius;
        getPrefs().putFloat("LineTracker.attentionRadius",attentionRadius);
    }
    
    public float getSeperatorOffset() {
        return seperatorOffset;
    }
    
    public void setSepteratorOffset(float seperatorOffset) {
        this.seperatorOffset = seperatorOffset;
        getPrefs().putFloat("LineTracker.seperatorOffset",seperatorOffset);
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


