/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.awt.Point;
    import net.sf.jaer.chip.*;
    import net.sf.jaer.event.*;
    import net.sf.jaer.eventprocessing.EventFilter2D;
    import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
    import javax.media.opengl.*;
    import net.sf.jaer.*;
    import net.sf.jaer.graphics.FrameAnnotater;

/**
 * This filter extracts edges by inferring points and connecting lines into a scene
 * 
 * TODO: expand the event counter to infinity by reseting it when it hits the max int value
 *
 * @author christian
 */
@Description("Extracts edges as linear point interpolations")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class EdgeFragments extends EventFilter2D implements Observer, FrameAnnotater{
    
    EdgeConstructor constructor;
    
    int[][] frameBuffer; //0=type, 1=x, 2=y, 3=timestamp, 4=protoClusterID, 5=clusterID
    int[] protoClusterSizes;
    CopyOnWriteArrayList<Integer>[] clusterPixels; 
    float[][] clusterColors;
    LineFragment[] lineFragments;
    int[][] accumArray;
    int bufferPointer;
    int protoPointer;
    int clusterPointer;
    int maxEventCount;
    int nrClusters;
    int minDist;
	
    /**
     * Determines whether events that cannot be assigned to an edge should be filtered out
     */
    private boolean filteringEnabled=getPrefs().getBoolean("EdgeExtractor.filterinEnabled",false);
    {setPropertyTooltip("filteringEnabled","Should the extractor act as filter for unallocated events");}
    
    /**
     * Determines whether edgePixels should be drawn
     */
    private boolean drawEdgePixels=getPrefs().getBoolean("EdgeExtractor.drawEdgePixels",true);
    {setPropertyTooltip("drawEdgePixels","Should the edgePixels be drawn");}
    
    /**
     * Determines whether edgePixels should be drawn
     */
    private boolean drawClusters=getPrefs().getBoolean("EdgeExtractor.drawClusters",true);
    {setPropertyTooltip("drawClusters","Should the clusters be drawn");}
    
    /**
     * Determines whether events that cannot be assigned to an edge should be filtered out
     */
    private int frameBufferSize=getPrefs().getInt("EdgeExtractor.frameBufferSize",500);
    {setPropertyTooltip("frameBufferSize","The number of most recent events allowed to make up the edges");}
    
    /**
     * 
     */
    private int maxClusters=getPrefs().getInt("EdgeExtractor.maxClusters",5);
    {setPropertyTooltip("maxClusters","Maximal number of clusters");}
    
    /**
     * 
     */
    private int minClusterSize=getPrefs().getInt("EdgeExtractor.minClusterSize",5);
    {setPropertyTooltip("minClusterSize","Minimal size for a ");}
    
	/**
     * Selection of the edge detection method
     */
    public enum EdgePixelMethod {
        ProtoClusters, LineSegments, LineFragments
    };
    public EdgePixelMethod edgePixelMethod = EdgePixelMethod.valueOf(getPrefs().get("ITDFilter.edgePixelMethod", "ProtoClusters"));
	{setPropertyTooltip("edgePixelMethod","Method to do the edge detection");}
    
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
        clusterPixels = new CopyOnWriteArrayList[maxClusters];
        for(int i = 0; i<maxClusters;i++)clusterPixels[i] = new CopyOnWriteArrayList<Integer>();
        clusterColors = new float[maxClusters][3];
        for(int i = 0; i<maxClusters;i++)for(int j = 0; j<3;j++)clusterColors[i][j] = (float)Math.random();
        lineFragments = new LineFragment[maxClusters];
        for(int i = 0; i<maxClusters;i++)lineFragments[i] = new LineFragment(i,-1,-1);
        accumArray = new int[chip.getSizeX()][chip.getSizeY()];
        for(int i = 0; i<chip.getSizeX();i++)Arrays.fill(accumArray[i], 0);
        //System.out.println("X "+accumArray.length+" Y "+accumArray[0].length);
        bufferPointer = 0;
        protoPointer = 0;
        clusterPointer = 0;
        maxEventCount = 0;
        nrClusters = 0;
        minDist = (int)Math.pow(minClusterSize, 2);
    }
    
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);    
        switch(edgePixelMethod){
                case ProtoClusters:
                default:
                    return updateProtoClusters(in);
                       
                case LineSegments:
                    return updateLineSegments(in);
                    
                case LineFragments:
                    return updateLineFragments(in);
            }
             
    }
    
    public EventPacket updateProtoClusters(EventPacket in){
        
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        for(int k=0; k<in.getSize(); k++){
            BasicEvent ev = in.getEvent(k);
            TypedEvent newEv = (TypedEvent)ev;
        
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
                    for(int i=0; i<maxClusters; i++){
                        clusterPixels[i].remove(new Integer(bufferPointer));
                    }
                    //System.out.println("Cluster "+frameBuffer[bufferPointer][5]+" - Event "+bufferPointer+" removed - now: "+clusterPixels[frameBuffer[bufferPointer][5]]);
                    if(clusterPixels[frameBuffer[bufferPointer][5]].size()<minClusterSize) removeCluster(frameBuffer[bufferPointer][5]);
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
                            clusterPixels[frameBuffer[bufferPointer][5]].add(bufferPointer);
                            pass = true;
                            if(nrClusters == 1) hasNeighbor = true;
                        }else if(frameBuffer[bufferPointer][5]!=frameBuffer[subPointer][5]){
                            if(clusterPixels[frameBuffer[bufferPointer][5]].size()>clusterPixels[frameBuffer[subPointer][5]].size()){
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
                        if(protoClusterSizes[frameBuffer[bufferPointer][4]]>minClusterSize){
                            boolean clusterAllocated = false;
                            int prPointer = frameBuffer[bufferPointer][4];
                            if(nrClusters<maxClusters){
                                int clPointer = 0;
                                while(!clusterAllocated && clPointer<maxClusters){
                                    if(clusterPixels[clPointer].isEmpty()){
                                        for(int j=0; j<frameBufferSize; j++){
                                            if(frameBuffer[j][4] == prPointer){
                                                frameBuffer[j][5] = clPointer;
                                                if(frameBuffer[bufferPointer][4]>=0){
                                                    //System.out.println("Cluster "+clPointer+" formed - "+j+" Removed from Proto "+frameBuffer[j][4]+", size "+protoClusterSizes[prPointer]);
                                                    protoClusterSizes[frameBuffer[j][4]] = 0;
                                                }
                                                frameBuffer[j][4] = -1;
                                                clusterPixels[clPointer].add(j);
                                            }
                                        }
                                        nrClusters++;
                                        clusterAllocated = true;
                                    }else{
                                        //System.out.println("Cluster "+clPointer+" full: "+clusterPixels[clPointer]);
                                    }
                                    clPointer++;
                                }
                            } 
                            if(nrClusters>=maxClusters || !clusterAllocated){
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
        for(int k=0; k<in.getSize(); k++){
            BasicEvent ev = in.getEvent(k);
            PolarityEvent newEv = (PolarityEvent)ev;
        
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
                    clusterPixels[frameBuffer[bufferPointer][5]].remove(new Integer(bufferPointer));
                    if(clusterPixels[frameBuffer[bufferPointer][5]].size()<2)
                        removeCluster(frameBuffer[bufferPointer][5]);
                }
            } 
            //Add new event to buffer
            frameBuffer[bufferPointer][0] = newEv.getType();
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
                int dist = getDistance(bufferPointer, subPointer);
                if((dist<=minDist)){
                    if(!(frameBuffer[subPointer][1] == frameBuffer[bufferPointer][1] && frameBuffer[subPointer][2] == frameBuffer[bufferPointer][2]) ){
                        if(frameBuffer[subPointer][5]<0){
                            boolean clusterAllocated = false;
                            if(nrClusters<maxClusters){
                                int clPointer = 0;
                                while(!clusterAllocated && clPointer<maxClusters){
                                    if(clusterPixels[clPointer].isEmpty()){
                                        frameBuffer[bufferPointer][5] = clPointer;
                                        frameBuffer[subPointer][5] = clPointer;
                                        clusterPixels[clPointer].add(bufferPointer);
                                        clusterPixels[clPointer].add(subPointer);
                                        nrClusters++;
                                        clusterAllocated = true;
                                    }
                                    clPointer++;
                                }
                            } 
                        }else{
                            int clusterIdx = frameBuffer[subPointer][5];
                            int idx = clusterPixels[clusterIdx].indexOf(new Integer(subPointer));
                            //System.out.println("clusterIdx: "+clusterIdx+", cluster: "+clusterPixels[clusterIdx]+", subPointer: "+subPointer+", idx: "+idx);
                            int distNext = Integer.MAX_VALUE;
                            if(idx <= clusterPixels[clusterIdx].size()-2){
                                int nextPointer = clusterPixels[clusterIdx].get(idx+1);
                                distNext = getDistance(bufferPointer, nextPointer);
                            }
                            if(dist > distNext){
                                if(idx<clusterPixels[clusterIdx].size()){
                                    clusterPixels[clusterIdx].add(idx+1, new Integer(bufferPointer));
                                }else{
                                    clusterPixels[clusterIdx].add(new Integer(bufferPointer));
                                }
                            } else {
                                clusterPixels[clusterIdx].add(idx, new Integer(bufferPointer));
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
    
    public EventPacket updateLineFragments(EventPacket in){
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        for(int k=0; k<in.getSize(); k++){
            BasicEvent ev = in.getEvent(k);
            PolarityEvent newEv = (PolarityEvent)ev;
            
            boolean pass=false;
            //Remove old event from Buffer
            //System.out.println("Next entry - pointer: "+bufferPointer+", polarity: "+frameBuffer[bufferPointer][0]);
            if(frameBuffer[bufferPointer][0] >= 0){
                for(int i = 0; i<maxClusters; i++){
                    if(lineFragments[i].cointains(bufferPointer)){
                        lineFragments[i].deactivate();
                        nrClusters--;
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
            //System.out.println("Pointer "+bufferPointer+" -- Event x: "+frameBuffer[bufferPointer][1]+", y: "+frameBuffer[bufferPointer][2]+" added, new accum: "+accumArray[frameBuffer[bufferPointer][1]][frameBuffer[bufferPointer][2]]);
            boolean hasNeighbor = false;
            int subPointer = bufferPointer-1;
            if(subPointer<0)subPointer=frameBufferSize-1;
            while(!hasNeighbor && subPointer != bufferPointer){
                //System.out.println("bufferPointer: "+bufferPointer+" -- Pointer: "+subPointer);
                //System.out.println("frameBuffer "+subPointer+" ts "+frameBuffer[subPointer][3]);
                if(frameBuffer[subPointer][3]>0){
                    int dist = getDistance(bufferPointer, subPointer);
                    if((dist<=minDist)){
                        if(!(frameBuffer[subPointer][1] == frameBuffer[bufferPointer][1] && frameBuffer[subPointer][2] == frameBuffer[bufferPointer][2]) && 
                            frameBuffer[subPointer][0] == frameBuffer[bufferPointer][0]){
                            boolean fragmentAllocated = false;
                            if(nrClusters<maxClusters){
                                int clPointer = 0;
                                while(!fragmentAllocated && clPointer<maxClusters){
                                    if(!lineFragments[clPointer].on){
                                        lineFragments[clPointer].activate(clPointer, bufferPointer, subPointer);
                                        //System.out.println("New fragment: "+clPointer+", p1: "+bufferPointer+" p1i: "+lineFragments[clPointer].p1+", p2:"+subPointer+", p2i: "+lineFragments[clPointer].p2+", dist: "+getDistance(lineFragments[clPointer].p1,lineFragments[clPointer].p2));
                                        nrClusters++;
                                        fragmentAllocated = true;
                                        pass = true;
                                    }else{
                                        //System.out.println("FULL fragment: "+clPointer+", p1: "+lineFragments[clPointer].p1+", p2:"+lineFragments[clPointer].p2+", dist: "+getDistance(lineFragments[clPointer].p1,lineFragments[clPointer].p2));
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
    
    public class LineFragment{
        
        Point p1, p2;
        int idx, type, i1, i2;
        boolean on;
        
        public LineFragment(int index, int point1, int point2){
            idx = index;
            i1 = point1;
            i2 = point2;
            p1 = new Point();
            p2 = new Point();
            on = false;
            type = -1;         
        }
        
        public boolean cointains(int point){
            if(point == i1 || point == i2){
                return true;
            }else{
                return false;
            }
        }
        
        public void activate(int index, int point1, int point2){
            if(idx != index)System.out.println("ERROR index "+index+" idx "+idx);
            frameBuffer[point1][5] = index;
            frameBuffer[point2][5] = index;
            i1 = point1;
            i2 = point2;
            p1.x = frameBuffer[i1][1];
            p2.x = frameBuffer[i2][1];
            p1.y = frameBuffer[i1][2];
            p2.y = frameBuffer[i2][2];
            if(frameBuffer[i1][0] == frameBuffer[i2][0]){
            type = frameBuffer[i1][0];
            } else {
                type = -1;
            }
            activateAccumArray();
            if(constructor != null){
                constructor.addFragment(this);
            }
            on = true;
        }
        
        public void activateAccumArray(){
            int dX = p2.x-p1.x;
            int dY = p2.y-p1.y;
            if(Math.abs(dX)>Math.abs(dY)){
                for(int iX=0; Math.abs(iX)<=Math.abs(dX); iX+=1*Math.signum(dX)){
                    accumArray[p1.x+iX][p1.y+(int)iX*(dY/dX)]++;
                }
            }else{
                for(int iY=0; Math.abs(iY)<=Math.abs(dY); iY+=1*Math.signum(dY)){
                    accumArray[p1.x+(int)iY*(dX/dY)][p1.y+iY]++;
                }
            }
        }
        
        public void deactivateAccumArray(){
            int dX = p2.x-p1.x;
            int dY = p2.y-p1.y;
            if(Math.abs(dX)>Math.abs(dY)){
                for(int iX=0; Math.abs(iX)<=Math.abs(dX); iX+=1*Math.signum(dX)){
                    accumArray[p1.x+iX][p1.y+(int)iX*(dY/dX)]--;
                }
            }else{
                for(int iY=0; Math.abs(iY)<=Math.abs(dY); iY+=1*Math.signum(dY)){
                    accumArray[p1.x+(int)iY*(dX/dY)][p1.y+iY]--;
                }
            }
        }
        
        public void deactivate(){
            //System.out.println("Frag "+idx+", Distance: "+getDistance(p1, p2)+", Max: "+minDist);
            deactivateAccumArray();
            frameBuffer[i1][5] = -1;
            frameBuffer[i2][5] = -1;
            i1 = -1;
            i2 = -1;
            on = false;
        }
        
        public void draw(GLAutoDrawable drawable){
            GL gl=drawable.getGL();
            gl.glLineWidth(2.0f);
            if(type == 1){
                gl.glColor3f(0.9f,0.9f,0.9f);  
            }else{
                gl.glColor3f(0.1f,0.1f,0.1f);
            }
            //gl.glColor3f(clusterColors[idx][0],clusterColors[idx][1],clusterColors[idx][2]);
            gl.glBegin(GL.GL_LINES);
            //System.out.println("Frag "+idx+", Distance: "+getDistance(p1, p2)+", Max: "+minDist);
            gl.glVertex2i(p1.x,p1.y);
            gl.glVertex2i(p2.x,p2.y);
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
        clusterPixels[cluster1].addAll(clusterPixels[cluster2]);
        clusterPixels[cluster2].clear();
        nrClusters--;
    }
    
    public void removeCluster(int clusterId){
        //System.out.println("Cluster "+clusterId+" removed");
        for(int j=0; j<frameBufferSize; j++){
            if(frameBuffer[j][5] == clusterId){
                frameBuffer[j][5] = -1;
            }
        }
        clusterPixels[clusterId].clear();
        nrClusters--;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        if(drawEdgePixels){
            GL gl=drawable.getGL();
            gl.glPointSize(4);
            int i = 0;
            if(edgePixelMethod == EdgePixelMethod.LineFragments){
                for(int x=0; x<chip.getSizeX(); x++){
                    for(int y=0; y<chip.getSizeY(); y++){
                        if(accumArray[x][y]>0){
                            if(accumArray[x][y]>1){
                                gl.glColor3f(0,1,0);
                            } else {
                                gl.glColor3f(0,0.4f,0);
                            }
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
                    gl.glColor3f(0,1,(float)1.0-(accumArray[x][y])/(maxEventCount)); 
                    gl.glVertex2i(x,y);
                    gl.glEnd();
                    i++;
                }
            }
        }
        if(drawClusters){
            switch(edgePixelMethod){
                case ProtoClusters:
                default:
                    drawClusters(drawable);
                    break;
                case LineSegments:
                    drawLineSegments(drawable);
                    break;
                case LineFragments:
                    drawLineFragments(drawable);
                    break;
            }
        }
    }
    
    public void drawClusters(GLAutoDrawable drawable){
        GL gl=drawable.getGL();
        for(int i = 0; i<maxClusters;i++){
            if(!clusterPixels[i].isEmpty()){
                //System.out.println("CLUSTER: "+i);
                Iterator it = clusterPixels[i].iterator();
                gl.glPointSize(4);
                while(it.hasNext()){
                    int idx = (Integer)it.next();
                    //System.out.println("Pixel-Idx: "+idx);                        
                    //System.out.println("Color: "+clusterColors[i][0]+", "+clusterColors[i][1]+", "+clusterColors[i][2]);
                    //System.out.println("Coord: "+frameBuffer[idx][1]+", "+frameBuffer[idx][2]);
                    gl.glBegin(GL.GL_POINTS);
                    gl.glColor3f(clusterColors[i][0],clusterColors[i][1],clusterColors[i][2]);
                    gl.glVertex2i(frameBuffer[idx][1],frameBuffer[idx][2]);
                    gl.glEnd();
                }                   
            }
        }
    }
    
    public void drawLineSegments(GLAutoDrawable drawable){
        GL gl=drawable.getGL();
        gl.glLineWidth(2.0f);
        for(int i = 0; i<maxClusters;i++){
            if(!clusterPixels[i].isEmpty()){
                // System.out.println("CLUSTER: "+i);
                Iterator it = clusterPixels[i].iterator();
                gl.glColor3f(clusterColors[i][0],clusterColors[i][1],clusterColors[i][2]);
                gl.glBegin(GL.GL_LINES);
                while(it.hasNext()){
                    int idx = (Integer)it.next();                   
                    gl.glVertex2i(frameBuffer[idx][1],frameBuffer[idx][2]);
                }
                gl.glEnd();
                gl.glPointSize(4);
                it = clusterPixels[i].iterator();
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
        
        for(int i = 0; i<maxClusters;i++){
            if(lineFragments[i].on){
                lineFragments[i].draw(drawable);
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
     * @return the maxClusters
     */
    public int getMaxClusters() {
        return maxClusters;
    }

    /**
     * @param maxClusters the maxClusters to set
     */
    public void setMaxClusters(int maxClusters) {
        this.maxClusters = maxClusters;
        prefs().putInt("EdgeExtractor.maxClusters", maxClusters);
        resetFilter();
    }
    
    /**
     * @return the minClusterSize
     */
    public int getMinClusterSize() {
        return minClusterSize;
    }

    /**
     * @param minClusterSize the minClusterSize to set
     */
    public void setMinClusterSize(int minClusterSize) {
        this.minClusterSize = minClusterSize;
        this.minDist = (int)Math.pow(minClusterSize, 2);
        prefs().putInt("EdgeExtractor.minClusterSize", minClusterSize);
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
    }
    
        /**
     * @return the drawEdgePixels
     */
    public boolean isDrawEdgePixels() {
        return drawEdgePixels;
    }

    /**
     * @param drawEdgePixels the drawEdgePixels to set
     */
    public void setDrawEdgePixels(boolean drawEdgePixels) {
        this.drawEdgePixels = drawEdgePixels;
    }
    
        /**
     * @return the drawEdges
     */
    public boolean isDrawEdges() {
        return drawClusters;
    }

    /**
     * @param drawEdges the drawEdges to set
     */
    public void setDrawEdges(boolean drawEdges) {
        this.drawClusters = drawEdges;
    }
	
	public EdgePixelMethod getEdgePixelMethod() {
        return edgePixelMethod;
    }

    synchronized public void setEdgePixelMethod(EdgePixelMethod edgePixelMethod) {
        getSupport().firePropertyChange("edgePixelMethod", this.edgePixelMethod, edgePixelMethod);
        getPrefs().put("EdgeExtractor.edgePixelMethod", edgePixelMethod.toString());
        this.edgePixelMethod = edgePixelMethod;
        resetFilter();
    }
}
