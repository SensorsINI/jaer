/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.tobi.rccar;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import java.util.*;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;
import javax.swing.*;
import ch.unizh.ini.caviar.graphics.*;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import java.lang.Math;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import java.io.*;


/**
 *
 * @author braendch
 */    
    public class OrientationCluster extends EventFilter2D implements Observer, FrameAnnotater {
    public boolean isGeneratingFilter(){ return true;}
    
    private boolean showAll=getPrefs().getBoolean("OrientationCluster.showAll",false);
    {setPropertyTooltip("showAll","shows all events");}
    
    private float tolerance=getPrefs().getFloat("OrientationCluster.tolerance",10);
    {setPropertyTooltip("Tolerance","Percentage of deviation tolerated");}
    
    private float neighborThr=getPrefs().getFloat("OrientationCluster.neighborThr",10);
    {setPropertyTooltip("Neighbor Threshold","Minimum for Neighbor Vector to be accepted");}
    
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
    
    private boolean showVectorsEnabled=getPrefs().getBoolean("SimpleOrientationFilter.showVectorsEnabled",false);
    {setPropertyTooltip("showVectorsEnabled","shows local orientation segments");}
    
    private boolean oriHistoryEnabled=getPrefs().getBoolean("OrientationCluster.oriHistoryEnabled",false);
    {setPropertyTooltip("oriHistoryEnabled","enable use of prior orientation values to filter out events not consistent with history");}
   
    // VectorMap[x][y][data] -->data: 0=x-component, 1=y-component, 2=timestamp, 3=polarity (0=off, 1=on, 4=theta
    // OriHistoryMap [x][y][data] --> data 0=x-component, 1=y-component, 2/3 = components neighborvector
    private float[][][] vectorMap;
    private float[][][] oriHistoryMap;
    
    public OrientationCluster(AEChip chip) {
        super(chip);
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
            vectorMap=new float[chip.getSizeX()][chip.getSizeY()][5];
            oriHistoryMap=new float[chip.getSizeX()][chip.getSizeY()][5];
        }
        
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
        
        for(Object ein:in){
            PolarityEvent e=(PolarityEvent)ein;
            int x=e.x;
            int y=e.y;
            int xx=0;
            int yy=0;
            float t=0;
            double vectorLength;
            float neighborX=0;
            float neighborY=0;
            float neighborTheta=0;
            float neighborLength=0;
            
            
            //calculate the actual vector and the neighborhood vector
            vectorMap[x][y][0]=0;
            vectorMap[x][y][1]=0;
            vectorMap[x][y][2]=(float)e.timestamp;
            
            //get the polarity of the vector
            
            if(e.polarity == PolarityEvent.Polarity.Off){
                vectorMap[x][y][3] = 0;
            } else {
                vectorMap[x][y][3] = 1;
            }
            
            //iteration trough the whole RF
            for(int h=-height; h<=height; h++){
                //System.out.println("row");
                //System.out.println(h);
                for(int w=-width; w<=width; w++){
                    if(0<x+w && x+w<sizex && 0<y+h && y+h<sizey){
                        //calculattion of timestampdifference (+1 to avoid division trough 0)
                        t=e.timestamp-vectorMap[x+w][y+h][2]+1;
                        //System.out.println("Delta Time");
                        //System.out.println(t);
                        if(t<dt){
                            /*System.out.println("position");
                            System.out.println(w);
                            System.out.println(h);
                            *///one has to check if the events are of the same polarity
                        if(vectorMap[x][y][3] != vectorMap[x+w][y+h][3]){
                            //if they are of a different polarity, the values have to be rotated
                            //System.out.println("different");
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
                            //System.out.println("same");
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
                        }
                        /*System.out.println("VectorComponent");
                        System.out.println(t);
                        System.out.println(xx);
                        System.out.println(yy);
                        System.out.println(vectorLength);
                        System.out.println(vectorMap[x][y][0]);
                        System.out.println(vectorMap[x][y][1]);
                        */
                        
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
            
            //the selection if a spike can pass the filter
            /*System.out.println("Vector");
            System.out.println(vectorMap[x][y][0]);
            System.out.println(vectorMap[x][y][1]);
            //System.out.println(Math.tanh(vectorMap[x][y][0]/vectorMap[x][y][1]));
            
             /*System.out.println("Neighbor");
            System.out.println(neighborX);
            System.out.println(neighborY);
            System.out.println(Math.tanh(neighborX/neighborY));
            */
            neighborLength = (float)Math.sqrt(neighborX*neighborX+neighborY*neighborY);
            neighborTheta = (float)Math.tanh(neighborX/neighborY);
            
            if(oriHistoryEnabled){
                vectorMap[x][y][4] = (float)(Math.tanh((vectorMap[x][y][0]+historyFactor*oriHistoryMap[x][y][0])
                        /(vectorMap[x][y][1]+historyFactor*oriHistoryMap[x][y][1])));
                oriHistoryMap[x][y][0] = vectorMap[x][y][0];
                oriHistoryMap[x][y][1] = vectorMap[x][y][1];
            } else {
                vectorMap[x][y][4] = (float)(Math.tanh(vectorMap[x][y][0]/vectorMap[x][y][1]));
                
            }
            
            if(vectorMap[x][y][0]!=0 && vectorMap[x][y][1]!=0){
                    if(Math.abs(vectorMap[x][y][4]-neighborTheta)<Math.PI*tolerance/180 &&
                            Math.abs(vectorMap[x][y][4])<ori*Math.PI/180 &&
                            neighborLength > neighborThr){
                        
                        if (vectorMap[x][y][4] < 0.0){
                            OrientationEvent eout=(OrientationEvent)outItr.nextOutput();
                            eout.copyFrom(e);
                            eout.orientation=(byte)1;
                            eout.hasOrientation=true;
                            
                        } else {
                            
                            OrientationEvent eout=(OrientationEvent)outItr.nextOutput();
                            eout.copyFrom(e);
                            eout.orientation=(byte)0;
                            eout.hasOrientation=true;
                            
                        }
                        
                        /*System.out.println("-->clustered");
                        System.out.println(Math.abs(vectorMap[x][y][4]-neighborTheta));
                        System.out.println(vectorMap[x][y][4]);
                        System.out.println(Math.abs(vectorMap[x][y][4]));
                    */}
            } else {
                if(showAll){
                    OrientationEvent eout=(OrientationEvent)outItr.nextOutput();
                    eout.copyFrom(e);
                    eout.hasOrientation=false;
                }
            }
           }
        
        return out;
        
    }
    
    public void resetFilter(){
        System.out.println("reset!");
        
        if(!isFilterEnabled()) return;
        
        if(vectorMap!=null){
            for(int i=0;i<vectorMap.length;i++)
                for(int j=0;j<vectorMap[i].length;j++)
                    Arrays.fill(vectorMap[i][j],0);
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
    
    private void drawOrientationVector(GL gl, OrientationEvent e){
        if(!e.hasOrientation) return;
        OrientationEvent.UnitVector d=OrientationEvent.unitVectors[e.orientation];
        gl.glVertex2f(e.x-d.x,e.y-d.y);
        gl.glVertex2f(e.x+d.x,e.y+d.y);
    }
    
    //  not used 
    public void annotate(float[][][] frame) {
    }

    // not used 
    public void annotate(Graphics2D g) {
    }
    
    public boolean isOriHistoryEnabled() {
        return oriHistoryEnabled;
    }
    
    public void setOriHistoryEnabled(boolean oriHistoryEnabled) {
        this.oriHistoryEnabled = oriHistoryEnabled;
        getPrefs().putBoolean("OrientationCluster.oriHistoryEnabled",oriHistoryEnabled);
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
     
     public float getNeighborThr() {
        return neighborThr;
    }
   
    synchronized public void setNeighborThr(float neighborThr) {
        this.neighborThr = neighborThr;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.neightborThr",neighborThr);
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
    
    public int getHeight() {
        return height;
    }
    
    synchronized public void setFactor(float factor) {
        this.factor = factor;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.factor",factor);
    }
    
    public float getFactor() {
        return factor;
    }
    
    public float getHistoryFactor() {
        return historyFactor;
    }
    
    synchronized public void setHistoryFactor(float historyFactor) {
        this.historyFactor = historyFactor;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.historyFactor",historyFactor);
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
      
    }