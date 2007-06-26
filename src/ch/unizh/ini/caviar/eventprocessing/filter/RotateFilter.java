/*
 * RotateFilter.java
 *
 * Created on July 7, 2006, 6:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.eventprocessing.filter;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import java.util.prefs.*;

/**
 * Transforms the events in various ways, e.g. rotates the events so that x becomes y and y becomes x.
 * @author tobi
 */
public class RotateFilter extends EventFilter2D {
    
    private boolean mapX2Y_Y2X=getPrefs().getBoolean("RotateFilter.mapX2Y_Y2X",false);
    private boolean rotate90deg=getPrefs().getBoolean("RotateFilter.rotate90deg",false);
     private boolean invertY=getPrefs().getBoolean("RotateFilter.invertY",false);
    
   
    
    /** Creates a new instance of RotateFilter */
    public RotateFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("mapX2Y_Y2X","swaps x and y coordinates");
        setPropertyTooltip("rotate90deg","rotates by 90 CCW");
        setPropertyTooltip("invertY","flips Y");
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        short tmp;
        if(!isFilterEnabled()) return in;
        if (isMapX2Y_Y2X()) {
            for(Object o:in){
                BasicEvent e=(BasicEvent)o;
                tmp=e.x;
                e.x=e.y;
                e.y=tmp;
            }
        }else if (isRotate90deg()) {
            for(Object o:in){
                BasicEvent e=(BasicEvent)o;
                tmp=e.x;
                e.x=(short)(chip.getSizeY()-e.y-1);
                e.y=tmp; 
            }
        }else if (isInvertY()) {
            for(Object o:in){
                BasicEvent e=(BasicEvent)o;
                e.y=(short)(chip.getSizeY()-e.y-1);
            }
        }
        return in;
    }

    public Object getFilterState() {
        return null;
    }

    public void resetFilter() {
    }

    public void initFilter() {
    }
    
    
    public boolean isMapX2Y_Y2X() {
        return mapX2Y_Y2X;
    }
    public void setMapX2Y_Y2X(boolean mapX2Y_Y2X) {
        this.mapX2Y_Y2X = mapX2Y_Y2X;
        getPrefs().putBoolean("RotateFilter.mapX2Y_Y2X",mapX2Y_Y2X);
    }
    
    public boolean isRotate90deg() {
        return rotate90deg;
    }
    public void setRotate90deg(boolean rotate90deg) {
        this.rotate90deg = rotate90deg;
        getPrefs().putBoolean("RotateFilter.rotate90deg",rotate90deg);
    }
    
    public boolean isInvertY() {
        return invertY;
    }
    public void setInvertY(boolean invertY) {
        this.invertY = invertY;
        getPrefs().putBoolean("RotateFilter.invertY",invertY);
    }
}
