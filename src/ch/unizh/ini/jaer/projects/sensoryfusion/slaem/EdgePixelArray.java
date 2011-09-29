/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.util.*;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import java.util.logging.*;
/**
 *
 * @author braendch
 */
public class EdgePixelArray {
    
    int sizeX, sizeY;
    int deltaTsActive = 1000; 
    EdgePixel[][] array;
    EdgeExtractor extractor;
    Vector<EdgePixel> activePixels;
	
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
        activePixels = new Vector<EdgePixel>(extractor.getActiveEvents(),100);
    }
    
    public void resetArray(){
        initializeArray();
    }
    
    //LastSignificant method
    public boolean addEvent(TypedEvent e, int eventNr){
        return array[e.x][e.y].addEvent(e, eventNr);
    }
    
    public int trimActivePixels(int oldestEventNr){
        int trimmed = 0;
        for(Iterator<EdgePixel> it = activePixels.iterator(); it.hasNext() ;){
            EdgePixel pixel = it.next();
            if(pixel.lastEventNr < oldestEventNr){
                pixel.deactivate();
                it.remove();
                trimmed++;
            }
        }
        return trimmed;
    }
    
    //Voxel
    public int trimActivePixelsV(int oldestEventNr){
        int trimmed = 0;
        for(Iterator<EdgePixel> it = activePixels.iterator(); it.hasNext() ;){
            EdgePixel pixel = it.next();
            if(pixel.newNeighbors>2 || pixel.activeNeighbors==0 || pixel.lastEventNr < oldestEventNr){
                pixel.deactivate();
                it.remove();
                trimmed++;
            }
        }
        return trimmed;
    }
    
    public void setDeltaTsActivity(int deltaTsActive){
        this.deltaTsActive = deltaTsActive;
    }
    
    public class EdgePixel {
        int posX, posY;
        int lastNeighborTs, lastEventNr;
        int newNeighbors, activeNeighbors;
        int generation; 
        boolean isActive;
        
        public EdgePixel(int xPos, int yPos){
            this.posX = xPos;
            this.posY = yPos;
            isActive = false;
            lastEventNr = -1;
            lastNeighborTs = 0;
            newNeighbors = 0;
            activeNeighbors = 0;
        }

        //LastSignificant method
        public boolean addEvent(TypedEvent e, int eventNr){
            if (e.x != posX || e.y != posY){
                log.log(Level.WARNING, "This type ({0} x, {1} y, {2} ts) was added to the wrong array entry", new Object[]{e.x, e.y, e.timestamp});
                return false;
            }
            if((e.timestamp < lastNeighborTs+deltaTsActive) && !isActive){
                activate();
            }
            updateNeighbors(e.timestamp);
            if(isActive){
                lastEventNr = eventNr;
                newNeighbors=0;
                return true;
            } 
            return false;
        }

        private void updateNeighbors(int ts){
            for (int x = posX-1; x<=posX+1; x++){
                for(int y = posY-1; y<=posY+1; y++){
                    if(x>=0 && y >=0 && x<sizeX && y<sizeY && !(x == posX && y ==posY)){
                        array[x][y].lastNeighborTs = ts;
                        array[x][y].newNeighbors++;
                        if(array[x][y].isActive && !isActive){
                            activate();
                        }
                    }
                }
            }
        }

        public void deactivate(){
            isActive = false;
            for (int x = posX-1; x<=posX+1; x++){
                for(int y = posY-1; y<=posY+1; y++){
                    if(x>=0 && y >=0 && x<sizeX && y<sizeY && !(x == posX && y ==posY)){
                        array[x][y].activeNeighbors--;
                    }
                }
            }
        }
        
        public void activate(){
            isActive = true;
            activePixels.add(this);
            for (int x = posX-1; x<=posX+1; x++){
                for(int y = posY-1; y<=posY+1; y++){
                    if(x>=0 && y >=0 && x<sizeX && y<sizeY && !(x == posX && y ==posY)){
                        array[x][y].activeNeighbors++;
                    }
                }
            }
        }
    }
}
