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
import ch.unizh.ini.caviar.eventprocessing.filter.XYTypeFilter;
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
 * This Filter creates for each event an orientation vector and calculates the common orientation of its neighbors.
 * To pass the filter difference of these two orientations has to be smaller than a centain tolerance (in degrees), 
 * further on the orientation must be within a certain range around vertical (ori) and the neighborhoodvector has to 
 * be big enough (neighborThr) to ensure that it doesn't have the right orientation just because of random.
 * To create the orientation vector for each event the receptive (width*height) field is investigated and the
 * normalized orientation vectors to each past event in the receptive field that satisfies a certain actuality 
 * (dt) is divided by the time past between the two events.
 * If two events are of different polarity (data index 3) the orientation is roatated by 90° - this is because the contrast gradient
 * is perpendicular to an edge.
 * To simplify calculation all vectors have an positive y-component.
 * The orientation History takes account of the past orientaions of the events and of the neighbors.
 * The paoli value (complexMap data index 0) is a fuzzy value to determine if an event is part of a line 
 * --> 0 = absolutely not part of a line, 1 = absolutely part of a line
 */    
    public class OrientationCluster extends EventFilter2D implements Observer, FrameAnnotater {
        
    public boolean isGeneratingFilter(){ return true;}
    
    private float paoliThr=getPrefs().getFloat("OrientationCluster.paoliThr",2000);
    {setPropertyTooltip("Paoli Threshold","Minimum of Paoli value to be accepted");}
    
    private float paoliTau=getPrefs().getFloat("OrientationCluster.paoliTau",2000);
    {setPropertyTooltip("Paoli Tau","The value with which the paoli decays");}
    
    private float tolerance=getPrefs().getFloat("OrientationCluster.tolerance",10);
    {setPropertyTooltip("Tolerance","Percentage of deviation tolerated");}
    
    private float neighborThr=getPrefs().getFloat("OrientationCluster.neighborThr",10);
    {setPropertyTooltip("Neighbor Threshold","Minimum Length of Neighbor Vector to be accepted");}
    
    private float historyFactor=getPrefs().getFloat("OrientationCluster.historyFactor",1);
    {setPropertyTooltip("historyFactor","if oriHistoryEnabled this determines how strong the actual vector gets influenced by the previous one");}
    
    private float ori=getPrefs().getFloat("OrientationCluster.ori",45);
    {setPropertyTooltip("Orientation","Orientation tolerated");}
    
    private float dt=getPrefs().getFloat("OrientationCluster.dt",10000);
    {setPropertyTooltip("Delta","Time Criteria for selection");} 
    
    private float factor=getPrefs().getFloat("OrientationCluster.factor",1000);
    {setPropertyTooltip("Excitatory Factor","Determines the excitatory synapse weight");}
    
    private int width=getPrefs().getInt("OrientationCluster.width",1);
    private int height=getPrefs().getInt("OrientationCluster.height",1);
    {
        setPropertyTooltip("width","width of RF, total is 2*width+1");
        setPropertyTooltip("height","length of RF, total length is height*2+1");
    }
    
    private boolean showAll=getPrefs().getBoolean("OrientationCluster.showAll",false);
    {setPropertyTooltip("showAll","shows all events");}
    
    private boolean showVectorsEnabled=getPrefs().getBoolean("SimpleOrientationFilter.showVectorsEnabled",false);
    {setPropertyTooltip("showVectorsEnabled","shows local orientation segments");}
    
     private boolean showOriEnabled=getPrefs().getBoolean("SimpleOrientationFilter.showOriEnabled",true);
    {setPropertyTooltip("showOriEnabled","Shows Orientation with color code");}
     
     private boolean showPaoliEnabled=getPrefs().getBoolean("SimpleOrientationFilter.showPaoliEnabled",false);
    {setPropertyTooltip("showPaoliEnabled","shows if an Event is part of a Line");}
    
    private boolean oriHistoryEnabled=getPrefs().getBoolean("OrientationCluster.oriHistoryEnabled",false);
    {setPropertyTooltip("oriHistoryEnabled","enable use of prior orientation values to filter out events not consistent with history");}
    
    private boolean paoliWindowEnabled=getPrefs().getBoolean("OrientationCluster.paoliWindowEnabled",false);
    {setPropertyTooltip("paoliWindowEnabled","enables the window of the paoli values");}
   
    // VectorMap[x][y][data] -->data: 0=x-component, 1=y-component, 2=timestamp, 3=polarity (0=off, 1=on, 4=theta
    // OriHistoryMap [x][y][data] --> data 0=x-component, 1=y-component, 2/3 = components neighborvector
    private float[][][] vectorMap;
    private float[][][] oriHistoryMap;
    private float[][][] paoliArray;
    
    //paoliShrinkFactor
    private int psf = 3;
    
    FilterChain preFilterChain;
    private XYTypeFilter xYFilter;
    
    public OrientationCluster(AEChip chip) {
        super(chip);
        
         //build hierachy
        preFilterChain = new FilterChain(chip);
        xYFilter = new XYTypeFilter(chip);

        this.setEnclosedFilter(xYFilter);
        
        xYFilter.setEnclosed(true, this);
        
        //xYFilter.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);

        chip.getCanvas().addAnnotator(this);
        
        initFilter();
        resetFilter();
        }
        
    @Override synchronized public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        if(yes){
            resetFilter();
        } else{
            vectorMap=null;
            out=null;
        }
    }
    
    private void checkMaps(){
        //it has to be checked if the VectorMap fits on the actual chip
        if(vectorMap==null
                || vectorMap.length!=chip.getSizeX()
                || vectorMap[0].length!=chip.getSizeY()) {
            allocateMaps();
        }
    }
    
    
    synchronized private void allocateMaps() {
        //the VectorMap is fitted on the chip size
        if(!isFilterEnabled()) return;
        log.info("OrientationCluster.allocateMaps()");
        if(chip!=null){
            vectorMap=new float[chip.getSizeX()][chip.getSizeY()][7];
            oriHistoryMap=new float[chip.getSizeX()][chip.getSizeY()][7];
        }
        resetFilter();
        
    }
    
    synchronized public EventPacket filterPacket(EventPacket in) {
        //Check if the filter should be active
        if(in==null) return null;
        if(!filterEnabled) return in; 
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        int n=in.getSize();
        if(n==0) return in;
        
        //Check if the input for the filter is the right one
        Class inputClass=in.getEventClass();
        if(inputClass!=PolarityEvent.class){
            log.warning("Wrong input event type "+in.getEventClass()+", disabling filter");
            setFilterEnabled(false);
            return in;
        }
     
        checkOutputPacketEventType(OrientationEvent.class);
        
        OutputEventIterator outItr=out.outputIterator();
        
        int sizex=chip.getSizeX()-1;
        int sizey=chip.getSizeY()-1;
        
        checkMaps();
        
        for(int i=0;i<sizex/psf;i++){
              for(int j=0;j<sizey/psf;j++){
                   paoliArray[i][j][0]=0; 
              }
        }
        
        for(Object ein:in){
            PolarityEvent e=(PolarityEvent)ein;
            int x=e.x;
            int y=e.y;
            int xx=0;
            int yy=0;
            double vectorLength;
            float neighborX=0;
            float neighborY=0;
            float neighborTheta=0;
            float neighborLength=0;
            
            //calculate the actual vector and the neighborhood vector
            vectorMap[x][y][0]=0;
            vectorMap[x][y][1]=0;
            
            //get the polarity of the vector
            if(e.polarity == PolarityEvent.Polarity.Off){
                vectorMap[x][y][3] = 0;
            } else {
                vectorMap[x][y][3] = 1;
            }
            
            //iteration trough the whole RF
            for(int h=-height; h<=height; h++){
                for(int w=-width; w<=width; w++){
                    if(0<x+w && x+w<sizex && 0<y+h && y+h<sizey){
                        //calculation of timestampdifference (+1 to avoid division trough 0)
                        float t=e.timestamp-vectorMap[x+w][y+h][2]+1;
                        
                        if(t<dt){
                            //one has to check if the events are of the same polarity
                        if(vectorMap[x][y][3] != vectorMap[x+w][y+h][3]){
                            //if they are of a different polarity, the values have to be rotated
                            if (w<0){
                                //different polarity - left side --> 90° CW
                                xx = h;
                                yy = -w;
                            } else {
                                //different polarity - right side --> 90° CCW
                                xx = -h;
                                yy = w;
                            }
                        } else {
                            //if they are of the same kind this doesn't have to be done
                            if (h<0){
                                //same polarity - down (unwanted) --> point inversion
                                xx = -w;
                                yy = -h;
                            } else {
                                //same polarity - up --> nothing
                                xx = w;
                                yy = h;
                            }
                        }
                        //The normalized value of the vector component gets multiplied by a factor and "decayed" (1/t) and added
                        vectorLength = Math.sqrt(xx*xx+yy*yy);
                        if (vectorLength != 0.0){ 
                        vectorMap[x][y][0] = (float)(vectorMap[x][y][0]+(xx/(vectorLength))*(factor/t));
                        vectorMap[x][y][1] = (float)(vectorMap[x][y][1]+(yy/(vectorLength))*(factor/t));    
                        
                        //Neighborhood vector calculation
                        if(oriHistoryEnabled){
                            neighborX = neighborX + (vectorMap[x+w][y+h][0]+historyFactor*oriHistoryMap[x+w][y+h][0]);
                            neighborY = neighborY + (vectorMap[x+w][y+h][1]+historyFactor*oriHistoryMap[x+w][y+h][1]);
                        } else {
                            neighborX = neighborX + vectorMap[x+w][y+h][0];
                            neighborY = neighborY + vectorMap[x+w][y+h][1];
                        }
                        }
                        }
                        
                    }
                }
            }
            
            neighborLength = (float)Math.sqrt(neighborX*neighborX+neighborY*neighborY);
            neighborTheta = (float)Math.tanh(neighborX/neighborY);
            
            if(oriHistoryEnabled){
                vectorMap[x][y][4] = (float)(Math.tanh((vectorMap[x][y][0]+historyFactor*oriHistoryMap[x][y][0])
                        /(vectorMap[x][y][1]+historyFactor*oriHistoryMap[x][y][1])));

            } else {
                vectorMap[x][y][4] = (float)(Math.tanh(vectorMap[x][y][0]/vectorMap[x][y][1]));
                
            }
            
            //The historyMap is upgraded
            oriHistoryMap[x][y][0] = vectorMap[x][y][0];
            oriHistoryMap[x][y][1] = vectorMap[x][y][1];
            oriHistoryMap[x][y][2] = vectorMap[x][y][2];
            oriHistoryMap[x][y][4] = vectorMap[x][y][4];
            
            
            //---------------------------------------------------------------------------
            //Create Output 
            if(vectorMap[x][y][0]!=0 && vectorMap[x][y][1]!=0){
                    if(Math.abs(vectorMap[x][y][4]-neighborTheta)<Math.PI*tolerance/180 &&
                            Math.abs(vectorMap[x][y][4])<ori*Math.PI/180 &&
                            neighborLength > neighborThr){
                        //the paoli value of the neighbors in the direction of the orientation vector has to be increased
                        //for each line above and below the actual event it is checked which the x value on the line (xl) is
                        
                        paoliArray[(int)(x/psf)][(int)(y/psf)][1] = paoliArray[(int)(x/psf)][(int)(y/psf)][1]/(e.timestamp-vectorMap[x][y][2]);
                        
                        
                        for(int yl=1; yl<=height; yl++){
                            int xl =(int)(yl*(vectorMap[x][y][0]/vectorMap[x][y][1]));
                            
                            
                            if( x+xl<sizex && y+yl<sizey && 0<x+xl && 0<y+yl){
                                if(e.timestamp-vectorMap[x+xl][y+yl][2]<dt){
                                    //1/t decay of paoli
                                    paoliArray[(int)((x+xl)/psf)][(int)((y+yl)/psf)][1] = paoliArray[(int)((x+xl)/psf)][(int)((y+yl)/psf)][1]/(e.timestamp-vectorMap[x][y][2]);                                    
                                    paoliArray[(int)(x/psf)][(int)(y/psf)][1] = paoliArray[(int)(x/psf)][(int)(y/psf)][1] +paoliArray[(int)((x+xl)/psf)][(int)((y+yl)/psf)][1] +paoliTau;
                                }
                            }
                            
                            if( x-xl<sizex && y-yl<sizey && 0<x-xl && 0<y-yl){
                                if(e.timestamp-vectorMap[x-xl][y-yl][2]<dt){
                                    //1/t decay of paoli
                                    paoliArray[(int)((x-xl)/psf)][(int)((y-yl)/psf)][1] = paoliArray[(int)((x-xl)/psf)][(int)((y-yl)/psf)][1]/(e.timestamp-vectorMap[x][y][2]);                                   
                                    paoliArray[(int)(x/psf)][(int)(y/psf)][1] = paoliArray[(int)(x/psf)][(int)(y/psf)][1] +paoliArray[(int)((x-xl)/psf)][(int)((y-yl)/psf)][1] +paoliTau;
                                }
                            }
                        }
                        
                        if(paoliArray[(int)(x/psf)][(int)(y/psf)][1]>paoliThr){
                            paoliArray[(int)(x/psf)][(int)(y/psf)][0] = 1;    
                        }
                        
                        if(showOriEnabled){
                            OrientationEvent eout=(OrientationEvent)outItr.nextOutput();
                            eout.copyFrom(e);
                            eout.orientation=(byte)Math.abs(4*vectorMap[x][y][4]);
                            eout.hasOrientation=true;
                        }
                    }
            } else {
                if(showAll){
                    OrientationEvent eout=(OrientationEvent)outItr.nextOutput();
                    eout.copyFrom(e);
                    eout.hasOrientation=false;
                }
            }
            
           vectorMap[x][y][2]=(float)e.timestamp; 
           }
        
        if(paoliWindowEnabled) {
                checkPaoliFrame();
                paoliCanvas.repaint();
            }
        
        return out;
        
    }
    
    
    
    
    
    
    public void resetFilter(){
        System.out.println("reset!");
        
        if(!isFilterEnabled()) return;
        
        paoliArray=new float[chip.getSizeX()][chip.getSizeY()][3];
        
        if(vectorMap!=null){
            for(int i=0;i<vectorMap.length;i++)
                for(int j=0;j<vectorMap[i].length;j++){
                    Arrays.fill(vectorMap[i][j],0);
                }
        }
        if(oriHistoryMap!=null){
            for(int i=0;i<oriHistoryMap.length;i++)
                for(int j=0;j<oriHistoryMap[i].length;j++)
                    Arrays.fill(oriHistoryMap[i][j],0);
        }
    }
    
    public Object getFilterState() {
        return vectorMap;
    }
    
    
    public void initFilter(){
        System.out.println("init!");
        resetFilter();
        
    }
    
    public void update(Observable o, Object arg){
        initFilter();
    }
    
    public boolean isShowVectorsEnabled() {
        return showVectorsEnabled;
    }
    
    public void setShowVectorsEnabled(boolean showVectorsEnabled) {
        this.showVectorsEnabled = showVectorsEnabled;
        getPrefs().putBoolean("SimpleOrientationFilter.showVectorsEnabled",showVectorsEnabled);
    }
    
    public void annotate(GLAutoDrawable drawable) {

    if(!isAnnotationEnabled() ) return;
        GL gl=drawable.getGL();
        
        if(isShowVectorsEnabled()){
            // draw individual orientation vectors
            gl.glPushMatrix();
            gl.glColor3f(1,1,1);
            gl.glLineWidth(1f);
            gl.glBegin(GL.GL_LINES);
            for(Object o:out){
                OrientationEvent e=(OrientationEvent)o;
                drawOrientationVector(gl,e);
            }
            gl.glEnd();
            gl.glPopMatrix();
        }
    }
    
    /** not used */
    public void annotate(float[][][] frame) {
    }

    /** not used */
    public void annotate(Graphics2D g) {
    }
    
    private void drawOrientationVector(GL gl, OrientationEvent e){
        if(!e.hasOrientation) return;
        OrientationEvent.UnitVector d=OrientationEvent.unitVectors[e.orientation];
        gl.glVertex2f(e.x-d.x,e.y-d.y);
        gl.glVertex2f(e.x+d.x,e.y+d.y);
    }

    void checkPaoliFrame(){
        if(paoliFrame==null || (paoliFrame!=null && !paoliFrame.isVisible())) createPaoliFrame();
    }
    
    JFrame paoliFrame=null;
    GLCanvas paoliCanvas=null;
    GLU glu=null;
    
    GLUquadric wheelQuad;
    
    void createPaoliFrame(){
        
        paoliFrame=new JFrame("Paoli cells");
        paoliFrame.setPreferredSize(new Dimension(400,400));
        paoliCanvas=new GLCanvas();
        paoliCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
            
            synchronized public void display(GLAutoDrawable drawable) {
                int sizex=chip.getSizeX()-1;
                int sizey=chip.getSizeY()-1;
                
                if(paoliArray==null) return;
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                gl.glScalef(psf*drawable.getWidth()/(sizex),psf*drawable.getHeight()/(sizey),1);
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                for(int i=0;i<sizex/psf;i++){
                    for(int j=0;j<sizey/psf;j++){
                        if(paoliArray[i][j][0]==1){
                            gl.glColor3f(1,0,0);
                            gl.glRectf(i,j,i+1,j+1);
                        }
                    }
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
        paoliFrame.getContentPane().add(paoliCanvas);
        paoliFrame.pack();
        paoliFrame.setVisible(true);
    }

    public boolean isOriHistoryEnabled() {
        return oriHistoryEnabled;
    }
    
    public void setOriHistoryEnabled(boolean oriHistoryEnabled) {
        this.oriHistoryEnabled = oriHistoryEnabled;
        getPrefs().putBoolean("OrientationCluster.oriHistoryEnabled",oriHistoryEnabled);
    }    

    public boolean isShowOriEnabled() {
        return showOriEnabled;
    }
    
    public void setShowOriEnabled(boolean showOriEnabled) {
        this.showOriEnabled = showOriEnabled;
        getPrefs().putBoolean("OrientationCluster.showOriEnabled",showOriEnabled);
    }    
    
    public boolean isShowPaoliEnabled() {
        return showPaoliEnabled;
    }
    
    public void setPaoliWindowEnabled(boolean paoliWindowEnabled) {
        this.paoliWindowEnabled = paoliWindowEnabled;
        getPrefs().putBoolean("OrientationCluster.paoliWindowEnabled",paoliWindowEnabled);
    }
    
        public boolean isPaoliWindowEnabled() {
        return paoliWindowEnabled;
    }
    
    public void setShowPaoliEnabled(boolean showPaoliEnabled) {
        this.showPaoliEnabled = showPaoliEnabled;
        getPrefs().putBoolean("OrientationCluster.showPaoliEnabled",showPaoliEnabled);
    }
    
    public boolean isShowAll() {
        return showAll;
    }
    
    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
        getPrefs().putBoolean("OrientationCluser.showAll",showAll);
    }
    
     public float getTolerance() {
        return tolerance;
    }
   
    synchronized public void setTolerance(float tolerance) {
        this.tolerance = tolerance;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.tolerance",tolerance);
    }
     
    public float getPaoliThr() {
        return paoliThr;
    }
   
    synchronized public void setPaoliThr(float paoliThr) {
        this.paoliThr = paoliThr;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.paoliThr",paoliThr);
    } 

    public float getPaoliTau() {
        return paoliTau;
    }
   
    synchronized public void setPaoliTau(float paoliTau) {
        this.paoliTau = paoliTau;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.paoliTau",paoliTau);
    }
    
     public float getNeighborThr() {
        return neighborThr;
    }
   
    synchronized public void setNeighborThr(float neighborThr) {
        this.neighborThr = neighborThr;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.neighborThr",neighborThr);
    }
     
    public float getOri() {
        return ori;
    }
   
    synchronized public void setOri(float ori) {
        this.ori = ori;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.ori",ori);
    }
     
     public float getDt() {
        return dt;
    }
   
    synchronized public void setDt(float dt) {
        this.dt = dt;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.dt",dt);
    }
       
    public float getFactor() {
        return factor;
    }
    
    synchronized public void setFactor(float factor) {
        this.factor = factor;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.factor",factor);
    }
    
    public float getHistoryFactor() {
        return historyFactor;
    }
    
    synchronized public void setHistoryFactor(float historyFactor) {
        this.historyFactor = historyFactor;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.historyFactor",historyFactor);
    }
        
    public int getHeight() {
        return height;
    }
 
    synchronized public void setHeight(int height) {
        this.height = height;
        allocateMaps();
        getPrefs().putInt("OrientationCluster.height",height);
    }
   
    public int getWidth() {
        return width;
    }
    
    synchronized public void setWidth(int width) {
        this.width = width;
        allocateMaps();
        getPrefs().putInt("OrientationCluster.width",width);
    }
    
    public XYTypeFilter getXYFilter() {
        return xYFilter;
    }

    public void setXYFilter(XYTypeFilter xYFilter) {
        this.xYFilter = xYFilter;
    }
      
    }