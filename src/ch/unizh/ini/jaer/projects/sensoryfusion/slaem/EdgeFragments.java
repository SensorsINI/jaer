/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.awt.geom.Line2D;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * This filter extracts edges by inferring points and connecting lines into a scene
 * 
 * TODO: expand the event counter to infinity by reseting it when it hits the max int value
 *
 * @author christian
 */
@Description("Extracts protoclusters / line segments / snakelets from events with spatio-temporal proximity")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class EdgeFragments extends EventFilter2D implements Observer, FrameAnnotater{
    
    EdgeConstructor constructor;
    
    int[][] frameBuffer; //0=type, 1=x, 2=y, 3=timestamp, 4=protoClusterID, 5=clusterID
    int[] protoClusterSizes;
    CopyOnWriteArrayList<Integer>[] elementPixels; 
    float[][] colors;
    Snakelet[] snakelets;
    int[][] accumArray;
    int bufferPointer;
    int protoPointer;
    int elementPointer;
    int maxEventCount;
    int nrElements;
    int maxDist;
	
    /**
     * Determines whether events that cannot be assigned to an edge should be filtered out
     */
    private boolean filteringEnabled=getPrefs().getBoolean("EdgeExtractor.filterinEnabled",false);
    {setPropertyTooltip("filteringEnabled","Should the extractor act as filter for unallocated events");}
    
    /**
     * Determines whether edgePixels should be drawn
     */
    private boolean drawAssocPixels=getPrefs().getBoolean("EdgeExtractor.drawAssocPixels",true);
    {setPropertyTooltip("drawAssocPixels","Should the associated pixels be drawn");}
    
    /**
     * Determines whether edgePixels should be drawn
     */
    private boolean drawElements=getPrefs().getBoolean("EdgeExtractor.drawElements",true);
    {setPropertyTooltip("drawElements","Should the protoclusters/line segments/snakelets be drawn");}
    
    /**
     * Determines whether events that cannot be assigned to an edge should be filtered out
     */
    private int frameBufferSize=getPrefs().getInt("EdgeExtractor.frameBufferSize",500);
    {setPropertyTooltip("frameBufferSize","The number of most recent events allowed to make up the edges");}
    
    /**
     * 
     */
    private int maxElements=getPrefs().getInt("EdgeExtractor.maxElements",5);
    {setPropertyTooltip("maxElements","Maximal number of protoclusters/line segments/snakelets");}
    
    /**
     * 
     */
    private int maxProximity=getPrefs().getInt("EdgeExtractor.maxProximity",5);
    {setPropertyTooltip("maxProximity","Maximal distance for two elements to form an element");}
    
	/**
     * Selection of the edge detection method
     */
    public enum ElementMethod {
        ProtoClusters, LineSegments, SnakeletsA, SnakeletsB
    };
    public ElementMethod elementMethod = ElementMethod.valueOf(getPrefs().get("EdgeExtractor.elementMethod", "ProtoClusters"));
	{setPropertyTooltip("elementMethod","Method to do create a protocluster/line segment/snakelet");}
    
    public EdgeFragments(AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
    }
	
    @Override
    public void initFilter() {

        resetFilter();
    }
    
    @Override
    public void resetFilter() {
        frameBuffer = new int[frameBufferSize][6];
        for(int i = 0; i<frameBufferSize;i++)Arrays.fill(frameBuffer[i], -1);
        protoClusterSizes = new int[frameBufferSize];
        Arrays.fill(protoClusterSizes, 0); 
        elementPixels = new CopyOnWriteArrayList[maxElements];
        for(int i = 0; i<maxElements;i++)elementPixels[i] = new CopyOnWriteArrayList<Integer>();
        colors = new float[maxElements][3];
        for(int i = 0; i<maxElements;i++)for(int j = 0; j<3;j++)colors[i][j] = (float)Math.random();
        snakelets = new Snakelet[maxElements];
        for(int i = 0; i<maxElements;i++)snakelets[i] = new Snakelet(i,-1,-1,0);
        accumArray = new int[chip.getSizeX()][chip.getSizeY()];
        for(int i = 0; i<chip.getSizeX();i++)Arrays.fill(accumArray[i], 0);
        //System.out.println("X "+accumArray.length+" Y "+accumArray[0].length);
        bufferPointer = 0;
        protoPointer = 0;
        elementPointer = 0;
        maxEventCount = 0;
        nrElements = 0;
        maxDist = (int)Math.pow(maxProximity, 2);
    }
    
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) return in;
        if(enclosedFilterChain!=null){
            in=enclosedFilterChain.filterPacket(in);
        }
        checkOutputPacketEventType(in);    
        switch(elementMethod){
                case ProtoClusters:
                default:
                    return updateProtoClusters(in);
                       
                case LineSegments:
                    return updateLineSegments(in);
                    
                case SnakeletsA:
                    return updateSnakeletsA(in);
                    
                case SnakeletsB:
                    return updateSnakeletsB(in);
            }
             
    }
    
    public EventPacket updateProtoClusters(EventPacket in){
        
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        for(Object evt:in){
            PolarityEvent newEv = (PolarityEvent)evt;
        
            boolean pass=false;
            //Remove old event from Buffer
            //System.out.println("Next entry - pointer: "+bufferPointer+", polarity: "+frameBuffer[bufferPointer][0]);
            if(frameBuffer[bufferPointer][0] >= 0){
                accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]--;
                //System.out.println("Pointer "+bufferPointer+" -- Event x: "+frameBuffer[bufferPointer][1]+", y: "+frameBuffer[bufferPointer][2]+" removed, new accum: "+accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]);
                if(frameBuffer[bufferPointer][4]>=0){
                    protoClusterSizes[frameBuffer[bufferPointer][4]]--;
                    //System.out.println("Removed "+bufferPointer+" from Proto "+frameBuffer[bufferPointer][4]+", size "+protoClusterSizes[frameBuffer[bufferPointer][4]]);
                }
                if(frameBuffer[bufferPointer][5]>=0){
                    for(int i=0; i<maxElements; i++){
                        elementPixels[i].remove(new Integer(bufferPointer));
                    }
                    //System.out.println("Cluster "+frameBuffer[bufferPointer][5]+" - Event "+bufferPointer+" removed - now: "+clusterPixels[frameBuffer[bufferPointer][5]]);
                    if(elementPixels[frameBuffer[bufferPointer][5]].size()<maxProximity) removeCluster(frameBuffer[bufferPointer][5]);
                    frameBuffer[bufferPointer][5] = -1;
                }
            } 
            //Add new event to buffer
            frameBuffer[bufferPointer][0] = newEv.type;
            frameBuffer[bufferPointer][1] = newEv.x;
            frameBuffer[bufferPointer][2] = newEv.y;
            frameBuffer[bufferPointer][3] = newEv.timestamp;
            frameBuffer[bufferPointer][4] = -1;
            frameBuffer[bufferPointer][5] = -1;
            accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]++;
            if(accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]>maxEventCount){
                maxEventCount = accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]];
            }
            //System.out.println("Pointer "+bufferPointer+" -- Event x: "+frameBuffer[bufferPointer][1]+", y: "+frameBuffer[bufferPointer][2]+" added, new accum: "+accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]);
            boolean hasNeighbor = false;
            int subPointer = bufferPointer-1;
            if(subPointer<0)subPointer=frameBufferSize-1;
            while(!hasNeighbor && subPointer != bufferPointer){
                //System.out.println("bufferPointer: "+bufferPointer+" -- Pointer: "+subPointer);
                if((Math.abs(frameBuffer[bufferPointer][1]-frameBuffer[subPointer][1])<=1)&&
                        (Math.abs(frameBuffer[bufferPointer][2]-frameBuffer[subPointer][2])<=1)){
                    if(frameBuffer[subPointer][5]>=0){
                        //System.out.println("Allocated to old cluster "+frameBuffer[subPointer][5]);
                        if(frameBuffer[bufferPointer][5]<0){
                            frameBuffer[bufferPointer][5]=frameBuffer[subPointer][5];
                            elementPixels[frameBuffer[bufferPointer][5]].add(bufferPointer);
                            pass = true;
                            if(nrElements == 1) hasNeighbor = true;
                        }else if(frameBuffer[bufferPointer][5]!=frameBuffer[subPointer][5]){
                            if(elementPixels[frameBuffer[bufferPointer][5]].size()>elementPixels[frameBuffer[subPointer][5]].size()){
                                mergeClusters(frameBuffer[bufferPointer][5], frameBuffer[subPointer][5]);
                            }else{
                                mergeClusters(frameBuffer[subPointer][5], frameBuffer[bufferPointer][5]);
                            }
                            hasNeighbor = true;
                        }
                    }else if(frameBuffer[subPointer][4]>=0){
                        frameBuffer[bufferPointer][4]=frameBuffer[subPointer][4];
                        protoClusterSizes[frameBuffer[bufferPointer][4]]++;
                        //System.out.println("Added to Proto "+frameBuffer[bufferPointer][4]+", size "+protoClusterSizes[frameBuffer[bufferPointer][4]]);
                        if(protoClusterSizes[frameBuffer[bufferPointer][4]]>maxProximity){
                            boolean clusterAllocated = false;
                            int prPointer = frameBuffer[bufferPointer][4];
                            if(nrElements<maxElements){
                                int clPointer = 0;
                                while(!clusterAllocated && clPointer<maxElements){
                                    if(elementPixels[clPointer].isEmpty()){
                                        for(int j=0; j<frameBufferSize; j++){
                                            if(frameBuffer[j][4] == prPointer){
                                                frameBuffer[j][5] = clPointer;
                                                if(frameBuffer[bufferPointer][4]>=0){
                                                    //System.out.println("Cluster "+clPointer+" formed - "+j+" Removed from Proto "+frameBuffer[j][4]+", size "+protoClusterSizes[prPointer]);
                                                    protoClusterSizes[frameBuffer[j][4]] = 0;
                                                }
                                                frameBuffer[j][4] = -1;
                                                elementPixels[clPointer].add(j);
                                            }
                                        }
                                        nrElements++;
                                        clusterAllocated = true;
                                    }else{
                                        //System.out.println("Cluster "+clPointer+" full: "+clusterPixels[clPointer]);
                                    }
                                    clPointer++;
                                }
                            } 
                            if(nrElements>=maxElements || !clusterAllocated){
                                //reset protoCluster
                                for(int j=0; j<frameBufferSize; j++){
                                    if(frameBuffer[j][4] == prPointer){
                                        if(frameBuffer[bufferPointer][4]>=0){
                                            protoClusterSizes[frameBuffer[j][4]] = 0;
                                            //System.out.println("Cluster formed - Removed from Proto "+frameBuffer[j][4]+", size "+protoClusterSizes[frameBuffer[j][4]]);
                                        }
                                        frameBuffer[j][4] = -1;
                                    }
                                }
                            }
                        }
                        hasNeighbor = true;
                    }

                }
                if(!hasNeighbor){
                    frameBuffer[bufferPointer][4]=protoPointer;
                    protoClusterSizes[protoPointer] = 1;
                    //System.out.println("New Proto "+protoPointer+", size "+protoClusterSizes[protoPointer]);
                    do{
                        protoPointer++;
                        //System.out.println("Proto-Pointer "+protoPointer);
                        if(protoPointer == frameBufferSize)protoPointer = 0;
                    }while(protoClusterSizes[protoPointer]>1);
                }
                subPointer--;
                if(subPointer<0)subPointer=frameBufferSize-1;
            }
            bufferPointer++;
            if(bufferPointer == frameBufferSize) bufferPointer = 0;
            if(pass){
                BasicEvent o=(BasicEvent)outItr.nextOutput();
                o.copyFrom(newEv);
            }
        }
        if(filteringEnabled){ 
            return out;
        }else{
            return in;
        }
    }
    
    public EventPacket updateLineSegments(EventPacket in){
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        for(Object evt:in){
            PolarityEvent newEv = (PolarityEvent)evt;
        
            boolean pass=false;
            //Remove old event from Buffer
            //System.out.println("Next entry - pointer: "+bufferPointer+", polarity: "+frameBuffer[bufferPointer][0]);
            if(frameBuffer[bufferPointer][0] >= 0){
                accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]--;
                //System.out.println("Pointer "+bufferPointer+" -- Event x: "+frameBuffer[bufferPointer][1]+", y: "+frameBuffer[bufferPointer][2]+" removed, new accum: "+accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]);
                if(frameBuffer[bufferPointer][4]>=0){
                    protoClusterSizes[frameBuffer[bufferPointer][4]]--;
                    //System.out.println("Removed "+bufferPointer+" from Proto "+frameBuffer[bufferPointer][4]+", size "+protoClusterSizes[frameBuffer[bufferPointer][4]]);
                }
                if(frameBuffer[bufferPointer][5]>=0){
                    elementPixels[frameBuffer[bufferPointer][5]].remove(new Integer(bufferPointer));
                    if(elementPixels[frameBuffer[bufferPointer][5]].size()<2)
                        removeCluster(frameBuffer[bufferPointer][5]);
                }
            } 
            //Add new event to buffer
            frameBuffer[bufferPointer][0] = newEv.getType();
            frameBuffer[bufferPointer][1] = newEv.x;
            frameBuffer[bufferPointer][2] = newEv.y;
            frameBuffer[bufferPointer][3] = newEv.timestamp;
            frameBuffer[bufferPointer][4] = -1;//protocluster
            frameBuffer[bufferPointer][5] = -1;//edgefragment
            accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]++;
            if(accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]>maxEventCount){
                maxEventCount = accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]];
            }
            //System.out.println("Pointer "+bufferPointer+" -- Event x: "+frameBuffer[bufferPointer][1]+", y: "+frameBuffer[bufferPointer][2]+" added, new accum: "+accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]);
            boolean hasNeighbor = false;
            int subPointer = bufferPointer-1;
            if(subPointer<0)subPointer=frameBufferSize-1;
            while(!hasNeighbor && subPointer != bufferPointer){
                //System.out.println("bufferPointer: "+bufferPointer+" -- Pointer: "+subPointer);
                int dist = getDistance(bufferPointer, subPointer);
                if((dist<=maxDist)){
                    if(!(frameBuffer[subPointer][1] == frameBuffer[bufferPointer][1] && frameBuffer[subPointer][2] == frameBuffer[bufferPointer][2]) ){
                        if(frameBuffer[subPointer][5]<0){
                            boolean clusterAllocated = false;
                            if(nrElements<maxElements){
                                int clPointer = 0;
                                while(!clusterAllocated && clPointer<maxElements){
                                    if(elementPixels[clPointer].isEmpty()){
                                        frameBuffer[bufferPointer][5] = clPointer;
                                        frameBuffer[subPointer][5] = clPointer;
                                        elementPixels[clPointer].add(bufferPointer);
                                        elementPixels[clPointer].add(subPointer);
                                        nrElements++;
                                        clusterAllocated = true;
                                    }
                                    clPointer++;
                                }
                            } 
                        }else{
                            int clusterIdx = frameBuffer[subPointer][5];
                            int idx = elementPixels[clusterIdx].indexOf(new Integer(subPointer));
                            //System.out.println("clusterIdx: "+clusterIdx+", cluster: "+clusterPixels[clusterIdx]+", subPointer: "+subPointer+", idx: "+idx);
                            int distNext = Integer.MAX_VALUE;
                            if(idx <= elementPixels[clusterIdx].size()-2){
                                int nextPointer = elementPixels[clusterIdx].get(idx+1);
                                distNext = getDistance(bufferPointer, nextPointer);
                            }
                            if(dist > distNext){
                                if(idx<elementPixels[clusterIdx].size()){
                                    elementPixels[clusterIdx].add(idx+1, new Integer(bufferPointer));
                                }else{
                                    elementPixels[clusterIdx].add(new Integer(bufferPointer));
                                }
                            } else {
                                elementPixels[clusterIdx].add(idx, new Integer(bufferPointer));
                            }
                            frameBuffer[bufferPointer][5] = clusterIdx;
                        }
                        
                        hasNeighbor = true;
                    }

                }
                subPointer--;
                if(subPointer<0)subPointer=frameBufferSize-1;
            }
            bufferPointer++;
            if(bufferPointer == frameBufferSize) bufferPointer = 0;
            if(pass){
                BasicEvent o=(BasicEvent)outItr.nextOutput();
                o.copyFrom(newEv);
            }
        }
        if(filteringEnabled){ 
            return out;
        }else{
            return in;
        }
    }
    
    public EventPacket updateSnakeletsA(EventPacket in){
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        for(Object evt:in){
            PolarityEvent newEv = (PolarityEvent)evt;
            
            boolean pass=false;
            //Remove old event from Buffer
            //System.out.println("Next entry - pointer: "+bufferPointer+", polarity: "+frameBuffer[bufferPointer][0]);
            if(frameBuffer[bufferPointer][0] >= 0){
                for(int i = 0; i<maxElements; i++){
                    if(snakelets[i].contains(bufferPointer)){
                        snakelets[i].remove();
                        nrElements--;
                    }
                }
            } 
            //Add new event to buffer
            frameBuffer[bufferPointer][0] = newEv.getType();
            frameBuffer[bufferPointer][1] = newEv.x;
            frameBuffer[bufferPointer][2] = newEv.y;
            frameBuffer[bufferPointer][3] = newEv.timestamp;
            frameBuffer[bufferPointer][4] = -1;
            frameBuffer[bufferPointer][5] = -1;          
            //System.out.println("Pointer "+bufferPointer+" -- Event x: "+frameBuffer[bufferPointer][1]+", y: "+frameBuffer[bufferPointer][2]);
            boolean hasNeighbor = false;
            int subPointer = bufferPointer-1;
            if(subPointer<0)subPointer=frameBufferSize-1;
            while(!hasNeighbor && subPointer != bufferPointer){
                //System.out.println("bufferPointer: "+bufferPointer+" -- Pointer: "+subPointer);
                //System.out.println("frameBuffer "+subPointer+" ts "+frameBuffer[subPointer][3]);
                if(frameBuffer[subPointer][3]>0){
                    int dist = getDistance(bufferPointer, subPointer);
                    if((dist<=maxDist)){
                        //System.out.println("Close point found: "+subPointer+" -- x: "+frameBuffer[subPointer][1]+", y: "+frameBuffer[subPointer][2]);
                        if(!(frameBuffer[subPointer][1] == frameBuffer[bufferPointer][1] && frameBuffer[subPointer][2] == frameBuffer[bufferPointer][2]) && 
                            frameBuffer[subPointer][0] == frameBuffer[bufferPointer][0]){
                            boolean fragmentAllocated = false;
                            if(nrElements<maxElements){
                                int clPointer = 0;
                                while(!fragmentAllocated && clPointer<maxElements){
                                    if(!snakelets[clPointer].on){
                                        snakelets[clPointer].set(clPointer, bufferPointer, subPointer, newEv.timestamp);
                                        nrElements++;
                                        fragmentAllocated = true;
                                        pass = true;
                                    }
                                    clPointer++;
                                }
                            } 
                            hasNeighbor = true;
                        }

                    }
                }
                subPointer--;
                if(subPointer<0)subPointer=frameBufferSize-1;
            }
            bufferPointer++;
            if(bufferPointer == frameBufferSize) bufferPointer = 0;
            if(pass){
                BasicEvent o=(BasicEvent)outItr.nextOutput();
                o.copyFrom(newEv);
            }
        }
        if(filteringEnabled){ 
            return out;
        }else{
            return in;
        }
    }
    
    
    public EventPacket updateSnakeletsB(EventPacket in){
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        for(Object evt:in){
            PolarityEvent newEv = (PolarityEvent)evt;
            
            boolean pass=false;
            //Remove old event from Buffer
            //System.out.println("Next entry - pointer: "+bufferPointer+", polarity: "+frameBuffer[bufferPointer][0]);
            if(frameBuffer[bufferPointer][0] >= 0){
                for(int i = 0; i<maxElements; i++){
                    if(snakelets[i].contains(bufferPointer)){
                        snakelets[i].remove();
                        nrElements--;
                    }
                }
            } 
            //Add new event to buffer
            frameBuffer[bufferPointer][0] = newEv.getType();
            frameBuffer[bufferPointer][1] = newEv.x;
            frameBuffer[bufferPointer][2] = newEv.y;
            frameBuffer[bufferPointer][3] = newEv.timestamp;
            frameBuffer[bufferPointer][4] = -1;
            frameBuffer[bufferPointer][5] = -1;          
            //System.out.println("Pointer "+bufferPointer+" -- Event x: "+frameBuffer[bufferPointer][1]+", y: "+frameBuffer[bufferPointer][2]);
            boolean hasNeighbor = false;
            int subPointer = bufferPointer-1;
            if(subPointer<0)subPointer=frameBufferSize-1;
            while(!hasNeighbor && subPointer != bufferPointer){
                //System.out.println("bufferPointer: "+bufferPointer+" -- Pointer: "+subPointer);
                //System.out.println("frameBuffer "+subPointer+" ts "+frameBuffer[subPointer][3]);
                if(frameBuffer[subPointer][3]>0 && frameBuffer[subPointer][5] < 0){
                    int dist = getDistance(bufferPointer, subPointer);
                    if((dist<=maxDist)){
                        //System.out.println("Close point found: "+subPointer+" -- x: "+frameBuffer[subPointer][1]+", y: "+frameBuffer[subPointer][2]);
                        if(!(frameBuffer[subPointer][1] == frameBuffer[bufferPointer][1] && frameBuffer[subPointer][2] == frameBuffer[bufferPointer][2]) && 
                            frameBuffer[subPointer][0] == frameBuffer[bufferPointer][0]){
                            if(nrElements>=maxElements){
                                nrElements++;
                            }else if(snakelets[elementPointer].on){
                                snakelets[elementPointer].remove();
                            }
                            snakelets[elementPointer].set(elementPointer, bufferPointer, subPointer, newEv.timestamp);
                            pass = true;
                            elementPointer++;
                            if(elementPointer==maxElements)elementPointer=0;
                            hasNeighbor = true;
                        }

                    }
                }
                subPointer--;
                if(subPointer<0)subPointer=frameBufferSize-1;
            }
            bufferPointer++;
            if(bufferPointer == frameBufferSize) bufferPointer = 0;
            if(pass){
                BasicEvent o=(BasicEvent)outItr.nextOutput();
                o.copyFrom(newEv);
            }
        }
        if(filteringEnabled){ 
            return out;
        }else{
            return in;
        }
    }
    
    public class Snakelet{
        
        Line2D.Float line;
        int idx, type, i1, i2, timestamp;
        float phi; 
        boolean on;
        
        public Snakelet(int index, int point1, int point2, int ts){
            idx = index;
            i1 = point1;
            i2 = point2;
            line = new Line2D.Float();
            on = false;
            type = -1;
            timestamp = ts;
        }
        
        public boolean contains(int point){
            if(point == i1 || point == i2){
                return true;
            }else{
                return false;
            }
        }
        
        public void set(int index, int point1, int point2, int ts){
            if(idx != index)System.out.println("ERROR index "+index+" idx "+idx);
            frameBuffer[point1][5] = index;
            frameBuffer[point2][5] = index;
            i1 = point1;
            i2 = point2;
            line.setLine(frameBuffer[i1][1],frameBuffer[i1][2],frameBuffer[i2][1],frameBuffer[i2][2]);
            phi = (float)Math.atan2((line.x1-line.x2),(line.y1-line.y2));
            timestamp = ts;
            if(frameBuffer[i1][0] == frameBuffer[i2][0]){
            type = frameBuffer[i1][0];
            } else {
                type = -1;
            }
            if(elementMethod == ElementMethod.SnakeletsA) activateAccumArray();
            if(constructor != null){
                constructor.addSnakelet(this);
            }
            on = true;
        }
        
        public void remove(){
            //System.out.println("Frag "+idx+", Distance: "+getDistance(p1, p2)+", Max: "+minDist);
            if(elementMethod == ElementMethod.SnakeletsA) deactivateAccumArray();
            frameBuffer[i1][5] = -1;
            frameBuffer[i2][5] = -1;
            i1 = -1;
            i2 = -1;
            if(constructor != null){
                constructor.removeSnakelet(this);
            }
            on = false;
        }
        
        public void activateAccumArray(){
            double dX = (double)line.x2-line.x1;
            double dY = (double)line.y2-line.y1;
            if(Math.abs(dX)>Math.abs(dY)){
                for(int iX=0; Math.abs(iX)<=Math.abs(dX); iX+=1*Math.signum(dX)){
                    accumArray[(int)line.x1+iX][(int)line.y1+(int)Math.round(iX*(dY/dX))]++;
                }
            }else{
                for(int iY=0; Math.abs(iY)<=Math.abs(dY); iY+=1*Math.signum(dY)){
                    accumArray[(int)line.x1+(int)Math.round(iY*(dX/dY))][(int)line.y1+iY]++;
                }
            }
        }
        
        public void deactivateAccumArray(){
            double dX = (double)line.x2-line.x1;
            double dY = (double)line.y2-line.y1;
            if(Math.abs(dX)>Math.abs(dY)){
                for(int iX=0; Math.abs(iX)<=Math.abs(dX); iX+=1*Math.signum(dX)){
                    accumArray[(int)line.x1+iX][(int)line.y1+(int)Math.round(iX*(dY/dX))]--;
                }
            }else{
                for(int iY=0; Math.abs(iY)<=Math.abs(dY); iY+=1*Math.signum(dY)){
                    accumArray[(int)line.x1+(int)Math.round(iY*(dX/dY))][(int)line.y1+iY]--;
                }
            }
        }
        
        public void draw(GLAutoDrawable drawable){
            GL2 gl=drawable.getGL().getGL2();
            gl.glLineWidth(2.0f);
            if(type == 1){
                gl.glColor3f(0.9f,0.9f,0.9f);  
            }else{
                gl.glColor3f(0.1f,0.1f,0.1f);
            }
            //gl.glColor3f(clusterColors[idx][0],clusterColors[idx][1],clusterColors[idx][2]);
            gl.glBegin(GL2.GL_LINES);
            //System.out.println("Frag "+idx+", Distance: "+getDistance(p1, p2)+", Max: "+minDist);
            gl.glVertex2f(line.x1,line.y1);
            gl.glVertex2f(line.x2,line.y2);
            gl.glEnd();
        }
    }
    
    public int getDistance(int p1, int p2){
        int dist = (int)(Math.pow(frameBuffer[p1][1] - frameBuffer[p2][1],2) + Math.pow(frameBuffer[p1][2] - frameBuffer[p2][2],2));
        if(frameBuffer[p1][0]!=frameBuffer[p2][0])dist = Integer.MAX_VALUE;
        return dist;
    }
    
    public void mergeClusters(int cluster1, int cluster2){
        //System.out.println("Cluster "+cluster1+" merged with "+cluster2);
        for(int j=0; j<frameBufferSize; j++){
            if(frameBuffer[j][5] == cluster2){
                frameBuffer[j][5] = cluster1;
            }
        }
        elementPixels[cluster1].addAll(elementPixels[cluster2]);
        elementPixels[cluster2].clear();
        nrElements--;
    }
    
    public void removeCluster(int clusterId){
        //System.out.println("Cluster "+clusterId+" removed");
        for(int j=0; j<frameBufferSize; j++){
            if(frameBuffer[j][5] == clusterId){
                frameBuffer[j][5] = -1;
            }
        }
        elementPixels[clusterId].clear();
        nrElements--;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        if(drawAssocPixels){
            GL2 gl=drawable.getGL().getGL2();
            gl.glPointSize(4);
            int i = 0;
            if(elementMethod == ElementMethod.SnakeletsA || elementMethod == ElementMethod.SnakeletsB){
                int oldEventCount = maxEventCount;
                maxEventCount = 0;
                for(int x=0; x<chip.getSizeX(); x++){
                    for(int y=0; y<chip.getSizeY(); y++){
                        if(accumArray[x][y]>0){
                            if(maxEventCount<accumArray[x][y])maxEventCount=accumArray[x][y];
//                            if(accumArray[x][y]>1){
//                                gl.glColor3f(0,1,0);
//                            } else {
//                                gl.glColor3f(0,0.4f,0);
//                            }
                            gl.glColor3f(0.45f-0.6f*((float)accumArray[x][y])/((float)oldEventCount),0.45f+0.6f*((float)accumArray[x][y])/((float)oldEventCount),0.45f-0.6f*((float)accumArray[x][y])/((float)oldEventCount)); 
                            gl.glBegin(GL.GL_POINTS); 
                            gl.glVertex2i(x,y);
                            gl.glEnd();
                        }
                    }
                }
            } else {
                while(i<frameBufferSize && frameBuffer[i][0] >= 0){
                    int x = frameBuffer[i][1];
                    int y = frameBuffer[i][2];
                    gl.glBegin(GL.GL_POINTS);
                    gl.glColor3f(0.45f-0.6f*((float)accumArray[x][y])/((float)maxEventCount),0.45f+0.6f*((float)accumArray[x][y])/((float)maxEventCount),0.45f-0.6f*((float)accumArray[x][y])/((float)maxEventCount)); 
                    gl.glVertex2i(x,y);
                    gl.glEnd();
                    i++;
                }
            }
        }
        if(drawElements){
            switch(elementMethod){
                case ProtoClusters:
                default:
                    drawClusters(drawable);
                    break;
                case LineSegments:
                    drawLineSegments(drawable);
                    break;
                case SnakeletsA:
                case SnakeletsB:
                    drawLineFragments(drawable);
                    break;
            }
        }
    }
    
    public void drawClusters(GLAutoDrawable drawable){
        GL2 gl=drawable.getGL().getGL2();
        for(int i = 0; i<maxElements;i++){
            if(!elementPixels[i].isEmpty()){
                //System.out.println("CLUSTER: "+i);
                Iterator it = elementPixels[i].iterator();
                gl.glPointSize(4);
                while(it.hasNext()){
                    int idx = (Integer)it.next();
                    //System.out.println("Pixel-Idx: "+idx);                        
                    //System.out.println("Color: "+clusterColors[i][0]+", "+clusterColors[i][1]+", "+clusterColors[i][2]);
                    //System.out.println("Coord: "+frameBuffer[idx][1]+", "+frameBuffer[idx][2]);
                    gl.glBegin(GL.GL_POINTS);
                    gl.glColor3f(colors[i][0],colors[i][1],colors[i][2]);
                    gl.glVertex2i(frameBuffer[idx][1],frameBuffer[idx][2]);
                    gl.glEnd();
                }                   
            }
        }
    }
    
    public void drawLineSegments(GLAutoDrawable drawable){
        GL2 gl=drawable.getGL().getGL2();
        gl.glLineWidth(2.0f);
        for(int i = 0; i<maxElements;i++){
            if(!elementPixels[i].isEmpty()){
                // System.out.println("CLUSTER: "+i);
                Iterator it = elementPixels[i].iterator();
                gl.glColor3f(colors[i][0],colors[i][1],colors[i][2]);
                gl.glBegin(GL2.GL_LINES);
                while(it.hasNext()){
                    int idx = (Integer)it.next();                   
                    gl.glVertex2i(frameBuffer[idx][1],frameBuffer[idx][2]);
                }
                gl.glEnd();
                gl.glPointSize(4);
                it = elementPixels[i].iterator();
                while(it.hasNext()){
                    int idx = (Integer)it.next(); 
                    gl.glBegin(GL.GL_POINTS);
                    gl.glVertex2i(frameBuffer[idx][1],frameBuffer[idx][2]);
                    gl.glEnd();
                }
                
            }
        }
    }
    
    public void drawLineFragments(GLAutoDrawable drawable){
        
        for(int i = 0; i<maxElements;i++){
            if(snakelets[i].on){
                snakelets[i].draw(drawable);
            }
        }
        
    }

    @Override
    public void update(Observable o, Object arg) {
        
    }
    
    public void setConstructor(EdgeConstructor constr){
       constructor = constr;
    }
    
     /**
     * @return the frameBufferSize
     */
    public int getFrameBufferSize() {
        return frameBufferSize;
    }

    /**
     * @param setActiveEvents the setActiveEvents to set
     */
    public void setFrameBufferSize(int frameBufferSize) {
        this.frameBufferSize = frameBufferSize;
        prefs().putInt("EdgeExtractor.frameBufferSize", frameBufferSize);
        resetFilter();
    }
    
    /**
     * @return the maxElements
     */
    public int getMaxElements() {
        return maxElements;
    }

    /**
     * @param maxElements the maxElements to set
     */
    public void setMaxElements(int maxElements) {
        this.maxElements = maxElements;
        prefs().putInt("EdgeExtractor.maxElements", maxElements);
        resetFilter();
    }
    
    /**
     * @return the maxProximity
     */
    public int getMaxProximity() {
        return maxProximity;
    }

    /**
     * @param maxProximity the maxProximity to set
     */
    public void setMaxProximity(int maxProximity) {
        this.maxProximity = maxProximity;
        this.maxDist = (int)Math.pow(maxProximity, 2);
        prefs().putInt("EdgeExtractor.minClusterSize", maxProximity);
        resetFilter();
    }
    
    /**
     * @return the filteringEnabled
     */
    public boolean isFilteringEnabled() {
        return filteringEnabled;
    }

    /**
     * @param filteringEnabled the filteringEnabled to set
     */
    public void setFilteringEnabled(boolean filteringEnabled) {
        this.filteringEnabled = filteringEnabled;
        prefs().putBoolean("EdgeExtractor.filteringEnabled", filteringEnabled);
    }
    
        /**
     * @return the drawAssocPixels
     */
    public boolean isDrawAssocPixels() {
        return drawAssocPixels;
    }

    /**
     * @param drawAssocPixels the drawAssocPixels to set
     */
    public void setDrawAssocPixels(boolean drawAssocPixels) {
        this.drawAssocPixels = drawAssocPixels;
        prefs().putBoolean("EdgeExtractor.drawAssocPixels", drawAssocPixels);
    }
    
        /**
     * @return the drawElements
     */
    public boolean isDrawElements() {
        return drawElements;
    }

    /**
     * @param drawElements the drawElements to set
     */
    public void setDrawElements(boolean drawElements) {
        this.drawElements = drawElements;
        prefs().putBoolean("EdgeExtractor.drawElements", drawElements);
    }
	
    public ElementMethod getElementMethod() {
        return elementMethod;
    }

    synchronized public void setElementMethod(ElementMethod elementMethod) {
        getSupport().firePropertyChange("elementMethod", this.elementMethod, elementMethod);
        getPrefs().put("EdgeExtractor.edgePixelMethod", elementMethod.toString());
        this.elementMethod = elementMethod;
        resetFilter();
    }
}
