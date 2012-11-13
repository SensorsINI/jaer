/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import ch.unizh.ini.jaer.projects.sensoryfusion.slaem.EdgeFragments.Snakelet;
import ch.unizh.ini.jaer.projects.util.RingBuffer;
import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.FrameAnnotater;

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
    public RingBuffer<EdgeSegment> protoSegments;
    
    public int matureEdges;
    
    private boolean drawAlloc=getPrefs().getBoolean("EdgeConstructor.drawAlloc",true);
    {setPropertyTooltip("drawAlloc","Should the allocation pixels be drawn");}
    
    private float oriTolerance=getPrefs().getFloat("EdgeConstructor.oriTolerance",0.2f);
    {setPropertyTooltip("oriTolerance","Tolerance in orientation for two Snakelets to merge");}
    
    private float distTolerance=getPrefs().getFloat("EdgeConstructor.distTolerance",0.2f);
    {setPropertyTooltip("distTolerance","Tolerance for two Snakelets to merge");}
    
    private int protoBufferSize=getPrefs().getInt("EdgeConstructor.protoBufferSize",10);
    {setPropertyTooltip("protoBufferSize","Tolerance for two Fractions to merge");}
    
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
        
        matureEdges = 0;
    }
    
    @Override
    public void resetFilter() {
        edges = new CopyOnWriteArrayList<Edge>();
        protoSegments = new RingBuffer(EdgeSegment.class, protoBufferSize);
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
        EdgeSegment segment = new EdgeSegment(snakelet,null);
        for(Object edg : edges){
            Edge edge = (Edge)edg;
            if(edge.checkOverlap(segment,oriTolerance,distTolerance)){
                return;
            }
        }
        for(Object sgm:protoSegments){
            EdgeSegment protoSgmt = (EdgeSegment) sgm;
            if(protoSgmt.touches(segment, distTolerance) && protoSgmt.aligns(segment, oriTolerance)){
                protoSgmt.merge(segment);
                if(protoSgmt.evidence>5){
                    edges.add(new Edge(protoSgmt));
                    if(edges.size()+matureEdges > protoBufferSize) edges.remove(0);
                    protoSegments.remove();
                }
                return;
            }
        }
        protoSegments.add(segment);
    }
    
    private void checkEdges(){
        for(Object edg : edges){
            Edge edge = (Edge) edg;
            if(!edge.checkAge()){
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
    
}
