/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.util.*;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author Christian Brandli
 */
public class OpticFlowExtractor extends EventFilter2D implements Observer, FrameAnnotater{
    public int sX, sY;
    public int eventNr;
    public OpticFlowVector[][] vectors;
    
    /**
     * Determines the maximal time to neighboring activity for becoming active (us)
     */
    private int maxDeltaTs=getPrefs().getInt("EdgeExtractor.maxDeltaTs",5000);
    {setPropertyTooltip("maxDeltaTs","Determines the maximal time to neighboring activity for contributing to the optic flow vectors (in us)");}
    
    public OpticFlowExtractor(AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
    }

    @Override
    public void resetFilter() {
        eventNr = 1;
        sX = chip.getSizeX();
        sY = chip.getSizeY();
        vectors = new OpticFlowVector[sX][sY];
        for(int x=0; x<sX; x++){
            for(int y=0; y<sY; y++){
                vectors[x][y] = new OpticFlowVector(x,y);
            }
        }
    }

    @Override
    public void initFilter() {
        resetFilter();
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        cleanVectors();
        for (Object o : in) {
            TypedEvent e = (TypedEvent) o;
            updateEvent(e);
            eventNr++;
        }
        return in;
    }
    
    public void updateEvent (TypedEvent e){
        int pX = e.x;
        int pY = e.y;
        vectors[e.x][e.y].lastTs = e.timestamp;
        for(int dx=-1; dx<=1; dx++){
            for(int dy=-1; dy<=1; dy++){
                if(pX+dx>=0 && pY+dy >=0 && pX+dx<sX && pY+dy<sY && !(dx == 0 && dy == 0)){
                    if(e.timestamp-vectors[pX+dx][pY+dy].lastTs<maxDeltaTs){
                        
                    }
                }
            }
        }
    }
    
    public void cleanVectors(){
        for(int x=0; x<sX; x++){
            for(int y=0; y<sY; y++){
                vectors[x][y].reset();
            }
        }
    }
    
    public class OpticFlowVector{
        int lastTs, lastUpdate;
        int xPos, yPos;
        double xComp, yComp;
        
        public OpticFlowVector(int x, int y){
            xPos = x;
            yPos = y;
            xComp = 0;
            yComp = 0;
            lastTs = 0;
            lastUpdate= 0;
        }
        
        public void reset(){
            xComp = 0;
            yComp = 0;
        }
        
    }
    
    @Override
    public void update(Observable o, Object arg) {
        
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        
    }
    
    /**
     * @return the maxDeltaTs
     */
    public int getMaxDeltaTs() {
        return maxDeltaTs;
    }

    /**
     * @param maxDeltaTs the maxDeltaTs to set
     */
    public void setMaxDeltaTs(int maxDeltaTs) {
        this.maxDeltaTs = maxDeltaTs;
        prefs().putInt("OpticFlowExtractor.maxDeltaTs", maxDeltaTs);
    }
    
}
