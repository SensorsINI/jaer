/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import ch.unizh.ini.jaer.projects.sensoryfusion.slaem.EdgeExtractor.EdgePixelMethod;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import java.util.logging.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
/**
 *
 * @author braendch
 */
public class EdgePixelArray {
    
    int sizeX, sizeY;
    int deltaTsActive = 1000; 
    EdgePixel[][] array;
    EdgeExtractor extractor;
    CopyOnWriteArrayList<EdgePixel> activePixels;
    CopyOnWriteArrayList<ProtoCluster> clusters;
	
    private static final Logger log=Logger.getLogger("EdgePixelArray"); //?
    
    public EdgePixelArray(EdgeExtractor extractor){
        this.extractor = extractor;
        initializeArray();
    }
    
    public EdgePixelArray(EdgeExtractor extractor, int deltaTsActive){
        this.deltaTsActive = deltaTsActive;
        this.extractor = extractor;
        initializeArray();
    }
    
    private void initializeArray(){
        sizeX = extractor.getChip().getSizeX();
        sizeY = extractor.getChip().getSizeY();
        array = new EdgePixel[sizeX][sizeY];
        for(int x=0; x<sizeX; x++){
            for(int y=0; y<sizeY; y++){
                array[x][y] = new EdgePixel(x,y);
            }
        }
        activePixels = new CopyOnWriteArrayList<EdgePixel>();
        clusters = new CopyOnWriteArrayList<ProtoCluster>();
    }
    
    public void resetArray(){
        initializeArray();
    }
    
    public boolean addEvent(TypedEvent e, int eventNr){
        return array[e.x][e.y].addEvent(e, eventNr);
    }
    
    public int trimActivePixels(int oldestEventNr){
        int trimmed = 0;
        ArrayList<EdgePixel> trimmedPixels = new ArrayList<EdgePixel>();
        switch(extractor.edgePixelMethod){
            case LastSignificants:
                for(Iterator<EdgePixel> it = activePixels.iterator(); it.hasNext() ;){
                    EdgePixel pixel = it.next();
                    if(pixel.lastEventNr < oldestEventNr){
                        pixel.deactivate();
                        trimmedPixels.add(pixel);
                        trimmed++;
                    }
                }
                activePixels.removeAll(trimmedPixels);
                break;
            case LineSegments:
                for(Iterator<EdgePixel> it = activePixels.iterator(); it.hasNext() ;){
                    EdgePixel pixel = it.next();
                    if(pixel.lastEventNr < oldestEventNr ){
                        pixel.deactivate();
                        trimmedPixels.add(pixel);
                        trimmed++;
                    }
                }
                activePixels.removeAll(trimmedPixels);
                break;
        }
        return trimmed;
    }
    
    public void setDeltaTsActivity(int deltaTsActive){
        this.deltaTsActive = deltaTsActive;
    }
    
    public void annotate(GLAutoDrawable drawable){
        for(Iterator<EdgePixel> it = activePixels.iterator(); it.hasNext() ;){
            EdgePixel pixel = it.next();
            pixel.annotate(drawable);
        }
    }
    
    public class EdgePixel {
        int posX, posY;
        int lastNeighborTs, lastEventNr;
        int generation; 
        boolean isActive;
        Edges.Edge edge;
        boolean isVertex;
        int segmentIdx;
        ProtoCluster cluster;
        
        public EdgePixel(int xPos, int yPos){
            this.posX = xPos;
            this.posY = yPos;
            isActive = false;
            lastEventNr = -1;
            lastNeighborTs = 0;
            isVertex = false;
            segmentIdx = -1;
        }

        //LastSignificant method
        public boolean addEvent(TypedEvent e, int eventNr){
            if (e.x != posX || e.y != posY){
                log.log(Level.WARNING, "This type ({0} x, {1} y, {2} ts) was added to the wrong array entry", new Object[]{e.x, e.y, e.timestamp});
                return false;
            }
            boolean returnValue = false;
            switch(extractor.edgePixelMethod){
                
                case LastSignificants:
                    if((e.timestamp < lastNeighborTs+deltaTsActive) && !isActive){
                        activate();
                    }
                    updateNeighbors(e.timestamp);
                    if(isActive){
                        lastEventNr = eventNr;
                        returnValue = true;
                    }
                    break;
                
                case LineSegments:
                    updateNeighbors(e.timestamp);
                    if((e.timestamp < lastNeighborTs+deltaTsActive)){
                        if(edge == null){
                            extractor.edges.newEdge(this);
                        }
                        activate();
                    }
                    if(isActive){
                        lastEventNr = eventNr;
                        returnValue = true;
                    }
                    break;
                case ProtoClusters:
                    if((e.timestamp < lastNeighborTs+deltaTsActive) && !isActive){
                        activate();
                    }
                    break;
            } 
            return returnValue;
        }

        private void updateNeighbors(int ts){
            for (int x = posX-1; x<=posX+1; x++){
                for(int y = posY-1; y<=posY+1; y++){
                    if(x>=0 && y >=0 && x<sizeX && y<sizeY && !(x == posX && y ==posY)){
                        EdgePixel neighbor = array[x][y];
                        neighbor.lastNeighborTs = ts;
                        switch(extractor.edgePixelMethod){

                            case LastSignificants:
                                if(neighbor.isActive){
                                    if(!isActive){
                                        activate();
                                    }
                                }
                                break;

                            case LineSegments:
                                if(edge == null && neighbor.edge != null){
                                    if(neighbor.isVertex){
                                        if(neighbor.segmentIdx<0)System.out.println("Vertex without index");
                                        if(neighbor.edge.segments.size()<=1){
                                            neighbor.edge.newVertex(this);
                                        }else{
                                            neighbor.edge.moveVertex(neighbor, this);
                                        }
                                    }else{
                                        neighbor.edge.addVertex(this, neighbor.segmentIdx);
                                    }
                                    activate();
                                }
                                break;
                        } 
                    }
                }
            }
        }

        public void deactivate(){
            isActive = false;
            activePixels.remove(this);
        }
        
        public void activate(){       
            isActive = true;
            activePixels.add(this);
            if(extractor.edgePixelMethod == EdgePixelMethod.ProtoClusters){
                clusters.add(new ProtoCluster(this));
            }
        }
        
        public void setEdge(Edges.Edge edge){
            this.edge = edge;
        }
        
        public void annotate(GLAutoDrawable drawable){
        GL gl=drawable.getGL();
        if(isVertex){
            if(edge==null){
                //???
                gl.glColor3f(1,0,1);
            }else{
                //Vertex
                gl.glColor3f(0,1,1);
            }
        }else{
            if(edge==null){
                //Proto-Events
                gl.glColor3f(1,0,0);
            }else{
                //edges
                gl.glColor3f(1,1,0);
            }
        }
        gl.glPushMatrix();
        gl.glPointSize(4);
        gl.glBegin(GL.GL_POINTS);
        gl.glVertex2i(posX,posY);
        gl.glEnd();
        gl.glPopMatrix();
    }
        
    }
    
    public class ProtoCluster{
        
        float[] color;
        Vector<EdgePixel> pixels;
        
        public ProtoCluster(EdgePixel pixel){
            color = new float[3];
            color[0] = (float)Math.random();
            color[1] = (float)Math.random();
            color[2] = (float)Math.random();
            pixels = new Vector<EdgePixel>();
            pixels.add(pixel);
        }
        
    }
}
