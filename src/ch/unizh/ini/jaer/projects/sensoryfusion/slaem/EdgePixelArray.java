/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;

/**
 *
 * @author braendch
 */
public class EdgePixelArray {
    
    int sizeX, sizeY;
    int deltaTsActive = 1000; 
    EdgePixel[][] array;
    AEChip chip;
    ArrayList<EdgePixel> activePixels;
    
    public EdgePixelArray(AEChip chip){
        this.chip = chip;
        initializeArray();
    }
    
    public EdgePixelArray(AEChip chip, int deltaTsActive){
        this.deltaTsActive = deltaTsActive;
        this.chip = chip;
        initializeArray();
    }
    
    private void initializeArray(){
        sizeX = chip.getSizeX();
        sizeY = chip.getSizeX();
        array = new EdgePixel[sizeX][sizeY];
        for(int x=0; x<sizeX; x++){
            for(int y=0; y<sizeY; y++){
                array[x][y] = new EdgePixel(x,y);
            }
        }
        activePixels = new ArrayList<EdgePixel>();
    }
    
    public void resetArray(){
        initializeArray();
    }
    
    public boolean addEvent(TypedEvent e, int eventNr){
        return array[e.x][e.y].addEvent(e, eventNr);
    }
    
    public int trimActivePixels(int oldestEventNr){
        int trimmed = 0;
        //TODO: Nicer solution for concurrent exception
        ArrayList<EdgePixel> activePixelclone = (ArrayList<EdgePixel>)activePixels.clone();
        Iterator<EdgePixel> activePixelItr = activePixelclone.iterator();
        while(activePixelItr.hasNext()){
            EdgePixel pixel = activePixelItr.next();
            if(pixel.lastEventNr < oldestEventNr){
                pixel.setInactive();
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
        int lastTs, lastEventNr;
        boolean isActive;
        
	
	public EdgePixel(int xPos, int yPos){
		this.posX = xPos;
		this.posY = yPos;
                isActive = false;
                lastEventNr = -1;
                lastTs = 0;
	}

	public boolean addEvent(TypedEvent e, int eventNr){
            if (e.x != posX || e.y != posY){
                return false;
            } 
            if(isActive){
                lastEventNr = eventNr;
                return true;
            } else {
                if(turnActive(e.timestamp)){
                    isActive = true;
                    activePixels.add(this);
                    return true;
                } else {
                    return false;
                }
            }
        }
        
        private boolean turnActive(int ts){
            if(ts < lastTs+deltaTsActive){
                updateNeighbors(ts);
                return true;
            }else{
                return updateNeighbors(ts);
            }
        }
        
        private boolean updateNeighbors(int ts){
            boolean output = false;
            for (int x = posX-1; x<posX+1; x++){
                for(int y = posY-1; y<posY+1; y++){
                    if(x>=0 && y >=0 && x<sizeX && y<sizeY && !(x == posX && y ==posY)){
                        array[x][y].lastTs = ts;
                        if(array[x][y].isActive){
                            output = true;
                            //array[x][y].setInactive();
                        }
                    }
                }
            }
            return output;
        }
        
        public void setInactive(){
            isActive = false;
            activePixels.remove(this);
        }
    }
}
