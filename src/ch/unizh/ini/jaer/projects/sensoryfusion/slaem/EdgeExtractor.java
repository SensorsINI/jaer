/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

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
public class EdgeExtractor extends EventFilter2D implements Observer, FrameAnnotater{
    
    int[][] frameBuffer; //0=type, 1=x, 2=y, 3=timestamp, 4=protoClusterID, 5=clusterID
    int[] protoClusterSizes;
    CopyOnWriteArrayList<Integer>[] clusterPixels; 
    float[][] clusterColors;
    int[][] accumArray;
    int bufferPointer;
    int protoPointer;
    int clusterPointer;
    int maxEventCount;
    int nrClusters;
	
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
     * Determines the maximal time to neighboring activity for becoming active (us)
     */
    private int deltaTsActivity=getPrefs().getInt("EdgeExtractor.deltaTsActivity",5000);
    {setPropertyTooltip("deltaTsActivity","Determines the maximal time to neighboring activity for becoming active (us)");}
    
	/**
     * Selection of the edge detection method
     */
    public enum EdgePixelMethod {
        ProtoClusters, LineSegments
    };
    public EdgePixelMethod edgePixelMethod = EdgePixelMethod.valueOf(getPrefs().get("ITDFilter.edgePixelMethod", "ProtoClusters"));
	{setPropertyTooltip("edgePixelMethod","Method to do the edge detection");}
    
    public EdgeExtractor(AEChip chip){
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
        accumArray = new int[chip.getSizeX()][chip.getSizeY()];
        for(int i = 0; i<chip.getSizeX();i++)Arrays.fill(accumArray[i], 0);
        bufferPointer = 0;
        protoPointer = 0;
        clusterPointer = 0;
        maxEventCount = 0;
        nrClusters = 0;
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
                    clusterPixels[frameBuffer[bufferPointer][5]].remove(new Integer(bufferPointer));
                    if(clusterPixels[frameBuffer[bufferPointer][5]].size()<2)
                        removeCluster(frameBuffer[bufferPointer][5]);
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
                if((Math.abs(frameBuffer[bufferPointer][1]-frameBuffer[subPointer][1])<=minClusterSize)&&
                        (Math.abs(frameBuffer[bufferPointer][2]-frameBuffer[subPointer][2])<=minClusterSize)){
                    if(!(frameBuffer[subPointer][1] == frameBuffer[bufferPointer][1] && frameBuffer[subPointer][2] == frameBuffer[bufferPointer][2])){
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
                            frameBuffer[bufferPointer][5] = frameBuffer[subPointer][5];
                            clusterPixels[frameBuffer[subPointer][5]].add(bufferPointer);
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
            gl.glPushMatrix();
            gl.glPointSize(4);
            int i = 0;
            while(i<frameBufferSize && frameBuffer[i][0] >= 0){
                int x = frameBuffer[i][1];
                int y = frameBuffer[i][2];
                gl.glBegin(GL.GL_POINTS);
                gl.glColor3f(0,1,(float)1.0-(accumArray[x][y])/(maxEventCount));
                //gl.glColor3f(1,0,0);  
                gl.glVertex2i(x,y);
                gl.glEnd();
                i++;
            }
//            for(int x=0; x<chip.getSizeX(); x++){
//                for(int y=0; y<chip.getSizeY(); y++){
//                    if(accumArray[x][y]>0){
//                        gl.glBegin(GL.GL_POINTS);
//                        gl.glColor3f(0,1,(float)1.0-(accumArray[x][y])/(maxEventCount));
//                        //gl.glColor3f(1,0,0);  
//                        gl.glVertex2i(x,y);
//                        gl.glEnd();
//                    }
//                }
//            }
            gl.glPopMatrix();
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
            }
        }
    }
    
    public void drawClusters(GLAutoDrawable drawable){
        GL gl=drawable.getGL();
        gl.glPushMatrix();
        for(int i = 0; i<maxClusters;i++){
            if(!clusterPixels[i].isEmpty()){
                //System.out.println("CLUSTER: "+i);
                Iterator it = clusterPixels[i].iterator();
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
        gl.glPopMatrix();
    }
    
    public void drawLineSegments(GLAutoDrawable drawable){
        GL gl=drawable.getGL();
        gl.glPushMatrix();
        gl.glLineWidth(2.0f);
        for(int i = 0; i<maxClusters;i++){
            if(!clusterPixels[i].isEmpty()){
                // System.out.println("CLUSTER: "+i);
                Iterator it = clusterPixels[i].iterator();
                gl.glBegin(GL.GL_LINES);
                gl.glColor3f(clusterColors[i][0],clusterColors[i][1],clusterColors[i][2]);
                while(it.hasNext()){
                    int idx = (Integer)it.next();                   
                    gl.glVertex2i(frameBuffer[idx][1],frameBuffer[idx][2]);
                }
                gl.glEnd();
            }
        }
        gl.glPopMatrix();
    }

    @Override
    public void update(Observable o, Object arg) {
        
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
        prefs().putInt("EdgeExtractor.minClusterSize", minClusterSize);
        resetFilter();
    }
    
     /**
     * @return the activeEvents
     */
    public int getDeltaTsActivity() {
        return deltaTsActivity;
    }

    /**
     * @param deltaTsActivity the deltaTsActivity to set
     */
    public void setDeltaTsActivity(int deltaTsActivity) {
        this.deltaTsActivity = deltaTsActivity;
        prefs().putInt("EdgeExtractor.deltaTsActivity", deltaTsActivity);
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
    }
}
