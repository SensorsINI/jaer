/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import ch.unizh.ini.jaer.projects.sensoryfusion.slaem.EdgeFragments.Snakelet;
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
    public UpdatedStack<EdgeSegment> protoSegments;

    public int closestEdgeID1,closestEdgeID2;
    public int closestSegID1,closestSegID2;
    
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
    }
    
    @Override
    public void resetFilter() {
        edges = new CopyOnWriteArrayList<Edge>();
        protoSegments = new UpdatedStack(EdgeSegment.class, protoBufferSize);
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
        //checkEdges();
        return nextOut;
    }
    
    public void addSnakelet(Snakelet snakelet){
        for(Object edg : edges){
            Edge edge = (Edge)edg;
            if(edge.contains(snakelet,oriTolerance,distTolerance)){
                return;
            }
        }
        EdgeSegment segment = new EdgeSegment(snakelet,null);
        if(protoSegments.addElement(segment,oriTolerance,distTolerance)){
            EdgeSegment last = protoSegments.getLastElement();
            if(last.mature){
                if(!checkOverlaps(last)){
                    edges.add(new Edge(segment));
                }
            }
        }
//        }else{
//            protoSegments.add(new EdgeSegment(snakelet),null);
//        }
    }
    
    private boolean checkOverlaps(EdgeSegment newEdgeSegment){
        boolean edgeFound = false;
        for(Object edg : edges){
            Edge edge = (Edge) edg;
            if(edge.overlaps(newEdgeSegment,oriTolerance,distTolerance)){
                edgeFound=true;
                break;
            }
        }
        return edgeFound;
    }
    
    private void checkEdges(){
        for(Object edg : edges){
            Edge edge = (Edge) edg;
            //edge.checkAge();
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
                edge.draw(drawable);
            }
            protoSegments.draw(drawable);
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
    
        
//   ::::::::::::::OLD CODE::::::::::  
//   elegant way to measure closest segment
//    
//    public void addFragment(LineFragment fragment){
//        DistSearchQuery distances = new DistSearchQuery();
//        boolean first = true;
//        Iterator edgeItr = edges.iterator();
//        while(edgeItr.hasNext()){
//            Edge oldEdge = (Edge)edgeItr.next();
//            DistSearchQuery edgeDist = oldEdge.getMinDistances(fragment);
//                if(first || distances.p1dist < edgeDist.p1dist){
//                    distances.p1dist = edgeDist.p1dist;
//                    distances.point1 = edgeDist.point1;
//                    distances.p1edge = edgeDist.p1edge;
//                    distances.p1segment = edgeDist.p1segment;
//                }
//                if(first || distances.p2dist < edgeDist.p2dist){
//                    distances.p2dist = edgeDist.p2dist;
//                    distances.point2 = edgeDist.point2;
//                    distances.p2edge = edgeDist.p2edge;
//                    distances.p2segment = edgeDist.p2segment;
//                    first = false;
//                }
//        }
//        if(distances.p1dist<distToleranceSqr){
//            if(distances.p2dist<distToleranceSqr){
//                //both points allocated
//                
//            }else{
//                //only p1 allocated
//            }
//        }else if(distances.p2dist<distToleranceSqr){
//            //only p2 allocated
//        }else{
//            //none allcoated 
//            int protoP = protoEdgePointer-1;
//            boolean protoFound = false;
//            while(!protoFound && protoP != protoEdgePointer){
//                if(protoEdges[protoP] != null){
//                    DistSearchQuery protoDist = protoEdges[protoP].getMinDistances(fragment);
//                    if(protoDist.p1dist<distToleranceSqr){
//                        edges.add(protoEdges[protoP]);
//                    }
//                }
//            }
//        }
//    }
//    
//    public class Edge{
//        
//        CopyOnWriteArrayList<Segment> segments;
//        
//        public Edge(LineFragment fragment){
//            segments = new CopyOnWriteArrayList<Segment>();
//            segments.add(new Segment(this,fragment));
//        }
//        
//        public DistSearchQuery getMinDistances(LineFragment fragment){
//            DistSearchQuery output = new DistSearchQuery();
//            boolean first = true;
//            Iterator segItr = segments.iterator();
//            while(segItr.hasNext()){
//                Segment segment = (Segment) segItr.next();
//                DistSearchQuery segmentDist = segment.getDistanceSqr(fragment);
//                if(first || output.p1dist < segmentDist.p1dist){
//                    output.p1dist = segmentDist.p1dist;
//                    output.point1 = segmentDist.point1;
//                    output.p1edge = segmentDist.p1edge;
//                    output.p1segment = segmentDist.p1segment;
//                }
//                if(first || output.p2dist < segmentDist.p2dist){
//                    output.p2dist = segmentDist.p2dist;
//                    output.point2 = segmentDist.point2;
//                    output.p2edge = segmentDist.p2edge;
//                    output.p2segment = segmentDist.p2segment;
//                    first = false;
//                }
//            }
//            return output;
//        }
//        
//        public class Segment{
//            Edge edge;
//            Point p1, p2;
//            public double slope;
//            public double isect;
//            public double alpha;
//            public double lengthSqr;
//            
//            public Segment(Edge edg, LineFragment fragment){
//                edge = edg;
//                p1 = fragment.p1;
//                p2 = fragment.p2;
//                int dX = p2.x-p1.x;
//                int dY = p2.y-p1.y;
////                slope = (dY)/(dX+0.0001);
////                isect = p1.y-p1.x*slope;
////                alpha = Math.tanh(slope);
//                lengthSqr = dX*dX+dY*dY;
//            }
//            
//            public DistSearchQuery getDistanceSqr(LineFragment fragment){
//                DistSearchQuery output = new DistSearchQuery();
//                //fragment point 1
//                output.p1edge = this.edge;
//                output.p1segment = this;
//                int dx1 = p1.x-fragment.p1.x, dy1 = p1.y-fragment.p1.y, dx2 = p2.x-fragment.p1.x, dy2 = p2.y-fragment.p1.y;
//                double det = (-dx1*dx2)+(-dy1*dy2);
//                if(det<0){
//                    output.point1 = 1;
//                    output.p1dist = dx1*dx1+dy1*dy1;
//                }
//                if(det>lengthSqr){
//                    output.point1 = 2;
//                    output.p1dist = dx2*dx2+dy2*dy2;
//                }
//                det = dx2*dy1-dy2*dx1;
//                output.p1dist = (det*det)/lengthSqr;
//                if(dx1*dx1+dy1*dy1 <= dx2*dx2+dy2*dy2){
//                    output.point1 = 1;
//                } else {
//                    output.point1 = 2;
//                }
//                //fragment point 2
//                output.p2edge = this.edge;
//                output.p2segment = this;
//                dx1 = p1.x-fragment.p2.x; 
//                dy1 = p1.y-fragment.p2.y;
//                dx2 = p2.x-fragment.p2.x;
//                dy2 = p2.y-fragment.p2.y;
//                det = (-dx1*dx2)+(-dy1*dy2);
//                if(det<0){
//                    output.point2 = 1;
//                    output.p2dist = dx1*dx1+dy1*dy1;
//                }
//                if(det>lengthSqr){
//                    output.point2 = 2;
//                    output.p2dist = dx2*dx2+dy2*dy2;
//                }
//                det = dx2*dy1-dy2*dx1;
//                output.p2dist = (det*det)/lengthSqr;
//                if(dx1*dx1+dy1*dy1 <= dx2*dx2+dy2*dy2){
//                    output.point2 = 1;
//                } else {
//                    output.point2 = 2;
//                }
//                return output;
//            }
//        }
//        
//    }
//    
//    public static class DistSearchQuery{
//        double p1dist, p2dist;
//        Edge p1edge, p2edge;
//        Edge.Segment p1segment, p2segment;
//        int point1, point2;
//    }
    
}
