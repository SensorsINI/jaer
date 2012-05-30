/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import ch.unizh.ini.jaer.projects.sensoryfusion.slaem.EdgePixelArray.EdgePixel;
import java.util.Iterator;
import java.util.Vector;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

/**
 *
 * @author Christian
 */
public class Edges {
    
    Vector<Edge> edges;
    EdgePixelArray pixelArray;
    int lastEdgeID = 0;
    
    public Edges(EdgePixelArray array){
        this.pixelArray = array;
        edges = new Vector<Edge>(20,5);
    }
    
    public Edge newEdge(EdgePixel pixel){
        Edge newEdge = new Edge(pixel);
        edges.add(newEdge);
        return newEdge;
    }
    
    public void trimEdges(){
        for(Iterator<Edge> edgeIt = edges.iterator(); edgeIt.hasNext() ;){
            Edge edge = edgeIt.next();
            boolean remove = true;
            for(Iterator<Edge.EdgeSegment> segIt = edge.segments.iterator(); segIt.hasNext() ;){
                Edge.EdgeSegment seg = segIt.next();
                if(seg.vertices[0].isActive){
                    remove = false;
                } else {
                    seg.removeFirstVertex();
                }
                if(!seg.isPoint && seg.vertices[1].isActive){
                    remove = false;
                } else {
                    seg.removeSecondVertex();
                }
                for(Iterator<EdgePixel> pixIt = seg.pixels.iterator(); pixIt.hasNext() ;){
                    if(pixIt.next().isActive){
                        remove = false;
                        break;
                    }
                }
                if(!remove)break;
            }
            if(remove){
                edge.clear();
                edgeIt.remove();
            }
        }
    }
    
    public void annotate(GLAutoDrawable drawable){
        Iterator edgeItr = this.edges.iterator();
        while(edgeItr.hasNext()){
            Edge edge = (Edge)edgeItr.next();
            edge.annotate(drawable);
        }
    }
    
    public class Edge{
        
        int id;
        int nextSegmentIdx = 0;
        
        Vector<EdgeSegment> segments;
        
        public Edge(EdgePixel pixel){
            segments = new Vector<EdgeSegment>();
            newVertex(pixel);
            id = lastEdgeID++;
            
        }
        
        public void newVertex(EdgePixel newVertex){
            if(nextSegmentIdx>0){
                segments.get(nextSegmentIdx-1).setSecondVertex(newVertex);
            }
            segments.add(new EdgeSegment(this,newVertex,nextSegmentIdx++));
        }
        
        public void addVertex(EdgePixel newVertex, int segmentIdx){
            //get old vertices
            EdgePixel firstVtx = segments.get(segmentIdx).getFirstVertex();
            EdgePixel secondVtx = segments.get(segmentIdx).getSecondVertex();
            //remove old segment
            segments.get(segmentIdx).clear();
            segments.remove(segmentIdx);
            //insert new segments
            segments.add(segmentIdx, new EdgeSegment(this,firstVtx,newVertex,nextSegmentIdx++));
            segments.add(segmentIdx+1, new EdgeSegment(this,newVertex,secondVtx,nextSegmentIdx++));
            updateSegmentIndices();
        }
        
        public void moveVertex(EdgePixel oldVertex, EdgePixel newVertex){
            int oldIdx = oldVertex.segmentIdx;
            segments.get(oldIdx).moveFirstVertex(newVertex);
            if(oldIdx > 0){
                segments.get(oldIdx-1).moveSecondVertex(newVertex);
            }
        }
        
        public void updateSegmentIndices(){
            nextSegmentIdx = segments.size();
            Iterator segmentItr = this.segments.iterator();
            while(segmentItr.hasNext()){
                EdgeSegment segment = (EdgeSegment)segmentItr.next();
                segment.setIdx(segments.indexOf(segment));
            }
        }
        
        public void clear(){
            Iterator segmentItr = this.segments.iterator();
            while(segmentItr.hasNext()){
                EdgeSegment segment = (EdgeSegment)segmentItr.next();
                segment.clear();
            }
        }
        
        public void annotate(GLAutoDrawable drawable){
            Iterator segmentItr = this.segments.iterator();
            while(segmentItr.hasNext()){
                EdgeSegment segment = (EdgeSegment)segmentItr.next();
                segment.annotate(drawable);
            }
        }
        
        public class EdgeSegment{
            Edge edge;
            int segmentIdx;
            Vector<EdgePixel> pixels;
            EdgePixel[] vertices;
            boolean isPoint;
            
            public EdgeSegment(Edge edge, EdgePixel vertex, int idx){
                this.edge = edge;
                pixels = new Vector<EdgePixel>();
                this.segmentIdx = idx;
                vertices = new EdgePixel[2];
                setFirstVertex(vertex);
                isPoint = true;
            }
            
            public EdgeSegment(Edge edge, EdgePixel firstVertex, EdgePixel secondVertex, int idx){
                this.edge = edge;
                pixels = new Vector<EdgePixel>();
                this.segmentIdx = idx;
                vertices = new EdgePixel[2];
                setFirstVertex(firstVertex);
                setSecondVertex(secondVertex);
                isPoint = false;
                update();
            }
            
            public void update(){
                if(!isPoint){
                    EdgePixel vertexA = vertices[0];
                    EdgePixel vertexB = vertices[1];
                    int dX = vertexA.posX - vertexB.posX;
                    int dY = vertexA.posY - vertexB.posY;
                    if(Math.abs(dX)>Math.abs(dY)){
                        for(int i = (int)(1*Math.signum(dX)); Math.abs(i)<Math.abs(dX); i = i+(int)(1*Math.signum(dX))){
                            EdgePixel pix = pixelArray.array[vertexA.posX+i][vertexA.posY+(int)Math.round(i*(dY/dX))];
                            pix.setEdge(edge);
                            pix.segmentIdx = segmentIdx;
                            pixels.add(pix);
                        }
                    }else{
                        for(int i = (int)(1*Math.signum(dY)); Math.abs(i)<Math.abs(dY); i = i+(int)(1*Math.signum(dY))){
                            EdgePixel pix = pixelArray.array[vertexA.posX+(int)Math.round(i*(dX/dY))][vertexA.posY+i];
                            pix.setEdge(edge);
                            pix.segmentIdx = segmentIdx;
                            pixels.add(pix);
                        }
                    }
                }
            }
            
            public void updateIndices(){
                vertices[0].segmentIdx = segmentIdx;
                Iterator pixeltItr = this.pixels.iterator();
                while(pixeltItr.hasNext()){
                    EdgePixel pixel = (EdgePixel)pixeltItr.next();
                    pixel.segmentIdx = segmentIdx;
                }
            }
            
            public void setIdx(int segIdx){
                segmentIdx = segIdx;
                updateIndices();
            }
            
            public void setFirstVertex(EdgePixel firstVertex){
                vertices[0] = firstVertex;
                firstVertex.isVertex = true;
                firstVertex.setEdge(edge);
                firstVertex.segmentIdx = segmentIdx;
            }
            
            public EdgePixel getFirstVertex(){
                return vertices[0];
            }
            
            public void moveFirstVertex(EdgePixel newFirstVertex){
                EdgePixel secondVertex = vertices[1];
                clear();
                setFirstVertex(newFirstVertex);
                if(!isPoint){
                    setSecondVertex(secondVertex);
                }
            }
            
            public boolean removeFirstVertex(){
                if(isPoint){
                    clear();
                    return true;
                } else {
                    if(pixels.isEmpty()){
                        EdgePixel firstVertex = vertices[1];
                        isPoint = true;
                        clear();
                        setFirstVertex(firstVertex);
                        update();
                        return false;
                    }else{
                        EdgePixel firstVertex = vertices[1];
                        EdgePixel secondVertex = pixels.firstElement();
                        clear();
                        setFirstVertex(firstVertex);
                        setSecondVertex(secondVertex);
                        update();
                        return false;
                    }
                }
            }
            
            public void setSecondVertex(EdgePixel secondVertex){
                vertices[1] = secondVertex;
                secondVertex.isVertex = true;
                secondVertex.segmentIdx = segmentIdx;
                secondVertex.setEdge(edge);
                isPoint=false;
            }
            
            public EdgePixel getSecondVertex(){
                return vertices[1];
            }
            
            public void moveSecondVertex(EdgePixel newSecondVertex){
                EdgePixel firstVertex = vertices[0];
                clear();
                setFirstVertex(firstVertex);
                setSecondVertex(newSecondVertex);
            }
            
            public boolean removeSecondVertex(){
                if(pixels.isEmpty()){
                    EdgePixel firstVertex = vertices[0];
                    isPoint = true;
                    clear();
                    setFirstVertex(firstVertex);
                    update();
                    return false;
                }else{
                    EdgePixel firstVertex = vertices[0];
                    EdgePixel secondVertex = pixels.lastElement();
                    clear();
                    setFirstVertex(firstVertex);
                    setSecondVertex(secondVertex);
                    update();
                    return false;
                }
            }
            
            public void clear(){
                vertices[0].segmentIdx = -1;
                vertices[0].setEdge(null);
                vertices[0].isVertex = false;
                if(!isPoint){
                    vertices[1].segmentIdx = -1;
                    vertices[1].setEdge(null);
                    vertices[1].isVertex = false;
                }
                Iterator pixeltItr = this.pixels.iterator();
                while(pixeltItr.hasNext()){
                    EdgePixel pixel = (EdgePixel)pixeltItr.next();
                    pixel.segmentIdx = -1;
                    pixel.setEdge(null);
                }
            }
            
            public void annotate(GLAutoDrawable drawable){
                GL gl=drawable.getGL();
                gl.glColor3f(0,1,0);
                gl.glPushMatrix();
                
                if(isPoint){
                    gl.glPointSize(5);
                    gl.glBegin(GL.GL_POINTS);
                    gl.glVertex2i(vertices[0].posX,vertices[0].posY);
                    gl.glEnd();
                } else {
                    gl.glPointSize(3);
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2i(vertices[0].posX,vertices[0].posY);
                    gl.glVertex2i(vertices[1].posX,vertices[1].posY);
                }
                
                gl.glPopMatrix();
            }
            
        }
        
    }
    
}
