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

 */    
    public class OrientationCluster extends EventFilter2D implements Observer, FrameAnnotater {
        
    public boolean isGeneratingFilter(){ return true;}
    
    private float yGradient=getPrefs().getFloat("OrientationCluster.yGradient",0);
    {setPropertyTooltip("yGradient","The slope of the neighbor-vector-gradient");}    
    
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
    
    private boolean useOppositePolarity=getPrefs().getBoolean("OrientationCluster.useOpositePolarity",true);
    {setPropertyTooltip("useOpositePolarity","should events be used for the calculation of the orientation vector");}
    
     private boolean showOriEnabled=getPrefs().getBoolean("SimpleOrientationFilter.showOriEnabled",true);
    {setPropertyTooltip("showOriEnabled","Shows Orientation with color code");}
    
    private boolean oriHistoryEnabled=getPrefs().getBoolean("OrientationCluster.oriHistoryEnabled",false);
    {setPropertyTooltip("oriHistoryEnabled","enable use of prior orientation values to filter out events not consistent with history");}
   
    // VectorMap[x][y][data] -->data: 0=x-component, 1=y-component, 2=timestamp, 3=polarity (0=off, 1=on, 4=theta
    // OriHistoryMap [x][y][data] --> data 0=x-component, 1=y-component, 2/3 = components neighborvector
    private float[][][] vectorMap;
    private float[][][] oriHistoryMap;
    
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
        int sizex=chip.getSizeX()-1;
        int sizey=chip.getSizeY()-1;
        
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
      
        
        checkMaps();
        
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
                            if(useOppositePolarity){
                                if (w<0){
                                    //different polarity - left side --> 90° CW
                                    xx = h;
                                    yy = -w;
                                } else {
                                    //different polarity - right side --> 90° CCW
                                    xx = -h;
                                    yy = w;
                                }
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
                            (neighborLength+e.y*neighborLength*yGradient) > neighborThr){

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
        //-------------------------------------------------------

        return out;
        
    }
    
    public void resetFilter(){
        log.info("OrientationCluster.reset!");
        
        if(!isFilterEnabled()) return;
        
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
//        System.out.println("init!");
        resetFilter();
        
    }
    
    public void update(Observable o, Object arg){
        initFilter();
    }
    
    public void annotate(GLAutoDrawable drawable) {

    }
    
    /** not used */
    public void annotate(float[][][] frame) {
    }

    /** not used */
    public void annotate(Graphics2D g) {
    }

    public boolean isOriHistoryEnabled() {
        return oriHistoryEnabled;
    }
    
    public void setOriHistoryEnabled(boolean oriHistoryEnabled) {
        this.oriHistoryEnabled = oriHistoryEnabled;
        getPrefs().putBoolean("OrientationCluster.oriHistoryEnabled",oriHistoryEnabled);
    }    

    public boolean isUseOppositePolarity() {
        return useOppositePolarity;
    }
    
    public void setUseOppositePolarity(boolean useOppositePolarity) {
        this.useOppositePolarity = useOppositePolarity;
        getPrefs().putBoolean("OrientationCluster.useOppositePolarity",useOppositePolarity);
    }  
    
    public boolean isShowOriEnabled() {
        return showOriEnabled;
    }
    
    public void setShowOriEnabled(boolean showOriEnabled) {
        this.showOriEnabled = showOriEnabled;
        getPrefs().putBoolean("OrientationCluster.showOriEnabled",showOriEnabled);
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

     public float getYGradient() {
        return yGradient;
    }
   
    synchronized public void setYGradient(float yGradient) {
        this.yGradient = yGradient;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.yGradient",yGradient);
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