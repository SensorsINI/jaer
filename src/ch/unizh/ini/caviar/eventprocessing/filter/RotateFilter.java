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
    
    Preferences prefs=Preferences.userNodeForPackage(RotateFilter.class);
    
    /** Creates a new instance of RotateFilter */
    public RotateFilter(AEChip chip) {
        super(chip);
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
    
    private boolean mapX2Y_Y2X=prefs.getBoolean("RotateFilter.mapX2Y_Y2X",false);
    
    public boolean isMapX2Y_Y2X() {
        return mapX2Y_Y2X;
    }
    public void setMapX2Y_Y2X(boolean mapX2Y_Y2X) {
        this.mapX2Y_Y2X = mapX2Y_Y2X;
        prefs.putBoolean("RotateFilter.mapX2Y_Y2X",mapX2Y_Y2X);
    }
    
    private boolean rotate90deg=prefs.getBoolean("RotateFilter.rotate90deg",false);
    
    public boolean isRotate90deg() {
        return rotate90deg;
    }
    public void setRotate90deg(boolean rotate90deg) {
        this.rotate90deg = rotate90deg;
        prefs.putBoolean("RotateFilter.rotate90deg",rotate90deg);
    }
    
    private boolean invertY=prefs.getBoolean("RotateFilter.invertY",false);
    
    public boolean isInvertY() {
        return invertY;
    }
    public void setInvertY(boolean invertY) {
        this.invertY = invertY;
        prefs.putBoolean("RotateFilter.invertY",invertY);
    }
}
