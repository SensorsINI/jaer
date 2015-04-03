/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.projects.sensoryfusion.slaem.EdgeFragments.Snakelet;
import ch.unizh.ini.jaer.projects.util.TrailingRingBuffer;

/**
 *
 * @author Christian
 */
@Description("Extracts edges from snakelets")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class EdgeConstructor extends EventFilter2D implements Observer, FrameAnnotater{

    private FilterChain filterChain;
    private BackgroundActivityFilter baFilter;
    public EdgeFragments snakelets;
    public CopyOnWriteArrayList<Edge> edges;
    public TrailingRingBuffer<EdgeSegment> protoSegments;
    public CopyOnWriteArrayList<Corner> corners; 
    
    private boolean drawAlloc=getPrefs().getBoolean("EdgeConstructor.drawAlloc",true);
    {setPropertyTooltip("drawAlloc","Should the allocation pixels be drawn");}
    
    private float oriTolerance=getPrefs().getFloat("EdgeConstructor.oriTolerance",0.2f);
    {setPropertyTooltip("oriTolerance","Tolerance in orientation for two Snakelets to merge");}
    
    private float distTolerance=getPrefs().getFloat("EdgeConstructor.distTolerance",0.2f);
    {setPropertyTooltip("distTolerance","Tolerance for two Snakelets to merge");}
    
    private int protoBufferSize=getPrefs().getInt("EdgeConstructor.protoBufferSize",10);
    {setPropertyTooltip("protoBufferSize","Tolerance for two Fractions to merge");}
    
    /**
     * 
     */
    private int threshold=getPrefs().getInt("EdgeConstructor.threshold",5);
    {setPropertyTooltip("threshold","threshold a ");}
    
    public EdgeConstructor(AEChip chip){
        super(chip);
        snakelets = new EdgeFragments(chip);
        snakelets.setChip(chip);
        snakelets.resetFilter();
        snakelets.setConstructor(this);
        
        baFilter = new BackgroundActivityFilter(chip);
        
        filterChain = new FilterChain(chip);

        filterChain.add(baFilter);
        filterChain.add(snakelets);
        
        setEnclosedFilterChain(filterChain);
        filterChain.reset();
        
        chip.addObserver(this);
        initFilter();
    }
    
    @Override
    public void resetFilter() {
        edges = new CopyOnWriteArrayList<Edge>();
        corners = new CopyOnWriteArrayList<Corner>();
        protoSegments = new TrailingRingBuffer(EdgeSegment.class, protoBufferSize);
        filterChain.reset();
    }

    @Override
    public void initFilter() {
        snakelets.elementMethod = EdgeFragments.ElementMethod.SnakeletsA;
        resetFilter();
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        EventPacket<?> nextOut = getEnclosedFilterChain().filterPacket(in);

        if ( in == null ){
            return null;
        }
        checkEdges();
        return nextOut;
    }
    
    public void addSnakelet(Snakelet snakelet){
        boolean newCorner=true;
        for(Corner corner:corners){
            if(corner.toAdd(snakelet))newCorner = false;
            
        }
        if(newCorner){
            corners.add(new Corner(snakelet.line.getP1(), snakelet, this));
            corners.add(new Corner(snakelet.line.getP2(), snakelet, this));
        }
    }
    
    public void removeSnakelet(Snakelet snakelet){
        for(Corner corner:corners){
            if(corner.toRemove(snakelet)){
                if(corner.hasEdge()){
                    Edge edge = corner.edge;
                    if(edge.removeCorner(corner))edges.remove(edge);
                }
                corners.remove(corner);
            }
        }
    }
    
    public void newEdge(Corner c1, Corner c2){
        edges.add(new Edge(c1, c2, this));
    }
    
    private void checkEdges(){
        for(Object edg : edges){
            Edge edge = (Edge) edg;
            if(!edge.checkValidity()){
                edges.remove(edge);
            }
//            int idx = edges.indexOf(edg);
//            for(int i = idx; i<edges.size()-1; i++){
//                edge.merge(edges.get(i));
//            }
        }
    }
    
    @Override
    public void update(Observable o, Object arg) {
        
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if(drawAlloc){
            for(Object edg : edges){
                Edge edge = (Edge)edg;
                if(edge != null)edge.draw(drawable);
            }
            for(Object sgmt : protoSegments){
                EdgeSegment segment = (EdgeSegment) sgmt;
                segment.draw(drawable);
            }
            for(Corner corner : corners)
                corner.draw(drawable);
        }
    }
    
    /**
     * @return the drawAlloc
     */
    public boolean isDrawAlloc() {
        return drawAlloc;
    }

    /**
     * @param drawAlloc the drawAlloc to set
     */
    public void setDrawAlloc(boolean drawAlloc) {
        this.drawAlloc = drawAlloc;
        prefs().putBoolean("EdgeConstructor.drawAlloc", drawAlloc);
    }
    
    /**
     * @return the oriTolerance
     */
    public float getOriTolerance() {
        return oriTolerance;
    }

    /**
     * @param oriTolerance the oriTolerance to set
     */
    public void setOriTolerance(float oriTolerance) {
        this.oriTolerance = oriTolerance;
        prefs().putFloat("EdgeConstructor.oriTolerance", oriTolerance);
        resetFilter();
    }
    
    /**
     * @return the distTolerance
     */
    public float getDistTolerance() {
        return distTolerance;
    }

    /**
     * @param distTolerance the distTolerance to set
     */
    public void setDistTolerance(float distTolerance) {
        this.distTolerance = distTolerance;
        prefs().putFloat("EdgeConstructor.distTolerance", distTolerance);
        resetFilter();
    }
    
    /**
     * @return the protoBufferSize
     */
    public int getProtoBufferSize() {
        return protoBufferSize;
    }

    /**
     * @param protoBufferSize the protoBufferSize to set
     */
    public void setProtoBufferSize(int protoBufferSize) {
        this.protoBufferSize = protoBufferSize;
        prefs().putInt("EdgeConstructor.protoBufferSize", protoBufferSize);
        resetFilter();
    }
    
    /**
     * @return the threshold
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * @param threshold the threshold to set
     */
    public void setThreshold(int threshold) {
        this.threshold = threshold;
        prefs().putInt("EdgeConstructor.threshold", threshold);
        resetFilter();
    }
    
}
