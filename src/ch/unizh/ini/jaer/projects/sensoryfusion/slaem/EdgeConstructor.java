/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import ch.unizh.ini.jaer.projects.sensoryfusion.slaem.EdgeFragments.LineFragment;
import java.awt.Point;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author Christian
 */
public class EdgeConstructor extends EventFilter2D implements Observer, FrameAnnotater{

    private FilterChain filterChain;
    public EdgeFragments fragments;
    public CopyOnWriteArrayList<Edge> edges;
    public Edge[] protoEdges;

    public int closestEdgeID1,closestEdgeID2;
    public int closestSegID1,closestSegID2;
    public double distToleranceSqr;
    public int protoEdgePointer;
    
    private boolean drawAlloc=getPrefs().getBoolean("EdgeExtractor.drawAlloc",true);
    {setPropertyTooltip("drawAlloc","Should the allocation pixels be drawn");}
    
    private double oriTolerance=getPrefs().getDouble("EdgeExtractor.oriTolerance",0.2);
    {setPropertyTooltip("oriTolerance","Tolerance in orientation for two Fractions to merge");}
    
    private double distTolerance=getPrefs().getDouble("EdgeExtractor.distTolerance",0.2);
    {setPropertyTooltip("distTolerance","Tolerance for two Fractions to merge");}
    
    private int protoBufferSize=getPrefs().getInt("EdgeExtractor.protoBufferSize",50);
    {setPropertyTooltip("protoBufferSize","Tolerance for two Fractions to merge");}
    
    public EdgeConstructor(AEChip chip){
        super(chip);
        fragments = new EdgeFragments(chip);
        fragments.setChip(chip);
        fragments.resetFilter();
        fragments.setConstructor(this);
        
        filterChain = new FilterChain(chip);
        filterChain.add(new BackgroundActivityFilter(chip));
        filterChain.add(fragments);
        
        setEnclosedFilterChain(filterChain);
        filterChain.reset();
        
        chip.addObserver(this);
        initFilter();
    }
    
    @Override
    public void resetFilter() {
        edges = new CopyOnWriteArrayList<Edge>();
        protoEdges = new Edge[protoBufferSize];
        protoEdgePointer = 0;
        filterChain.reset();
        distToleranceSqr = distTolerance*distTolerance;
    }

    @Override
    public void initFilter() {
        fragments.edgePixelMethod = EdgeFragments.EdgePixelMethod.LineFragments;
        resetFilter();
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        EventPacket<?> nextOut = getEnclosedFilterChain().filterPacket(in);

        if ( in == null ){
            return null;
        }
        
        return nextOut;
    }
    
    public void addFragment(LineFragment fragment){
        DistSearchQuery distances = new DistSearchQuery();
        boolean first = true;
        Iterator edgeItr = edges.iterator();
        while(edgeItr.hasNext()){
            Edge oldEdge = (Edge)edgeItr.next();
            DistSearchQuery edgeDist = oldEdge.getMinDistances(fragment);
                if(first || distances.p1dist < edgeDist.p1dist){
                    distances.p1dist = edgeDist.p1dist;
                    distances.point1 = edgeDist.point1;
                    distances.p1edge = edgeDist.p1edge;
                    distances.p1segment = edgeDist.p1segment;
                }
                if(first || distances.p2dist < edgeDist.p2dist){
                    distances.p2dist = edgeDist.p2dist;
                    distances.point2 = edgeDist.point2;
                    distances.p2edge = edgeDist.p2edge;
                    distances.p2segment = edgeDist.p2segment;
                    first = false;
                }
        }
        if(distances.p1dist<distToleranceSqr){
            if(distances.p2dist<distToleranceSqr){
                //both points allocated
                
            }else{
                //only p1 allocated
            }
        }else if(distances.p2dist<distToleranceSqr){
            //only p2 allocated
        }else{
            //none allcoated 
            int protoP = protoEdgePointer-1;
            boolean protoFound = false;
            while(!protoFound && protoP != protoEdgePointer){
                if(protoEdges[protoP] != null){
                    DistSearchQuery protoDist = protoEdges[protoP].getMinDistances(fragment);
                    if(protoDist.p1dist<distToleranceSqr){
                        edges.add(protoEdges[protoP]);
                    }
                }
            }
        }
    }
    
    public void removeFragment(LineFragment fragment){
        
    }
    
    public class Edge{
        
        CopyOnWriteArrayList<Segment> segments;
        
        public Edge(LineFragment fragment){
            segments = new CopyOnWriteArrayList<Segment>();
            segments.add(new Segment(this,fragment));
        }
        
        public DistSearchQuery getMinDistances(LineFragment fragment){
            DistSearchQuery output = new DistSearchQuery();
            boolean first = true;
            Iterator segItr = segments.iterator();
            while(segItr.hasNext()){
                Segment segment = (Segment) segItr.next();
                DistSearchQuery segmentDist = segment.getDistanceSqr(fragment);
                if(first || output.p1dist < segmentDist.p1dist){
                    output.p1dist = segmentDist.p1dist;
                    output.point1 = segmentDist.point1;
                    output.p1edge = segmentDist.p1edge;
                    output.p1segment = segmentDist.p1segment;
                }
                if(first || output.p2dist < segmentDist.p2dist){
                    output.p2dist = segmentDist.p2dist;
                    output.point2 = segmentDist.point2;
                    output.p2edge = segmentDist.p2edge;
                    output.p2segment = segmentDist.p2segment;
                    first = false;
                }
            }
            return output;
        }
        
        public class Segment{
            Edge edge;
            Point p1, p2;
            public double slope;
            public double isect;
            public double alpha;
            public double lengthSqr;
            
            public Segment(Edge edg, LineFragment fragment){
                edge = edg;
                p1 = fragment.p1;
                p2 = fragment.p2;
                int dX = p2.x-p1.x;
                int dY = p2.y-p1.y;
//                slope = (dY)/(dX+0.0001);
//                isect = p1.y-p1.x*slope;
//                alpha = Math.tanh(slope);
                lengthSqr = dX*dX+dY*dY;
            }
            
            public DistSearchQuery getDistanceSqr(LineFragment fragment){
                DistSearchQuery output = new DistSearchQuery();
                //fragment point 1
                output.p1edge = this.edge;
                output.p1segment = this;
                int dx1 = p1.x-fragment.p1.x, dy1 = p1.y-fragment.p1.y, dx2 = p2.x-fragment.p1.x, dy2 = p2.y-fragment.p1.y;
                double det = (-dx1*dx2)+(-dy1*dy2);
                if(det<0){
                    output.point1 = 1;
                    output.p1dist = dx1*dx1+dy1*dy1;
                }
                if(det>lengthSqr){
                    output.point1 = 2;
                    output.p1dist = dx2*dx2+dy2*dy2;
                }
                det = dx2*dy1-dy2*dx1;
                output.p1dist = (det*det)/lengthSqr;
                if(dx1*dx1+dy1*dy1 <= dx2*dx2+dy2*dy2){
                    output.point1 = 1;
                } else {
                    output.point1 = 2;
                }
                //fragment point 2
                output.p2edge = this.edge;
                output.p2segment = this;
                dx1 = p1.x-fragment.p2.x; 
                dy1 = p1.y-fragment.p2.y;
                dx2 = p2.x-fragment.p2.x;
                dy2 = p2.y-fragment.p2.y;
                det = (-dx1*dx2)+(-dy1*dy2);
                if(det<0){
                    output.point2 = 1;
                    output.p2dist = dx1*dx1+dy1*dy1;
                }
                if(det>lengthSqr){
                    output.point2 = 2;
                    output.p2dist = dx2*dx2+dy2*dy2;
                }
                det = dx2*dy1-dy2*dx1;
                output.p2dist = (det*det)/lengthSqr;
                if(dx1*dx1+dy1*dy1 <= dx2*dx2+dy2*dy2){
                    output.point2 = 1;
                } else {
                    output.point2 = 2;
                }
                return output;
            }
        }
        
    }
    
    public static class DistSearchQuery{
        double p1dist, p2dist;
        Edge p1edge, p2edge;
        Edge.Segment p1segment, p2segment;
        int point1, point2;
    }

    @Override
    public void update(Observable o, Object arg) {
        
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl=drawable.getGL();
        Iterator it = edges.iterator();
        gl.glLineWidth(2);
        while(it.hasNext()){
            Edge edge = (Edge)it.next();
            gl.glBegin(GL.GL_LINES);
            gl.glColor3f(1,0,0);
            Iterator edgeIt = edge.segments.iterator();
            while(edgeIt.hasNext()){
                Edge.Segment segment = (Edge.Segment)edgeIt.next();
                gl.glVertex2i(segment.p1.x,segment.p1.y);
            }
            gl.glVertex2i(edge.segments.get(edge.segments.size()-1).p2.x,edge.segments.get(edge.segments.size()-1).p2.y);
            gl.glEnd();
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
    }
    
    /**
     * @return the oriTolerance
     */
    public double getOriTolerance() {
        return oriTolerance;
    }

    /**
     * @param oriTolerance the oriTolerance to set
     */
    public void setOriTolerance(double oriTolerance) {
        this.oriTolerance = oriTolerance;
    }
    
    /**
     * @return the distTolerance
     */
    public double getDistTolerance() {
        return distTolerance;
    }

    /**
     * @param distTolerance the distTolerance to set
     */
    public void setDistTolerance(double distTolerance) {
        this.distTolerance = distTolerance;
        distToleranceSqr = distTolerance*distTolerance;
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
    }
    
}
