 /*
  * RotateRetinaFilter.java
  *
  * Created on July 7, 2006, 6:59 AM
  *
  * To change this template, choose Tools | Template Manager
  * and open the template in the editor.
  *
  *
  *Copyright July 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
  */

package ch.unizh.ini.jaer.projects.robothead.retinacochlea;

import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Rotates the Retina events 90 degree, to show nicely RobotHead data. Put this Filter at the beginning of ControlFilter..
 * Leaves Cochlea Events the same form. Use only with RetinaCochlea Chip
 *
 * @author tobi
 */
public class RotateRetinaFilter extends EventFilter2D implements Observer {
    
    private int sx, sy;
    
    
    /**
     * Creates a new instance of RotateRetinaFilter
     */
    public RotateRetinaFilter(AEChip chip) {
        super(chip);
        
        chip.addObserver(this);  // to update chip size parameters
        sx=chip.getSizeX();
        sy=chip.getSizeY();
        
    }
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        short tmp;
        if(!isFilterEnabled()) return in;     // only complicates it...
        
        for(Object o:in){
            
            BasicEvent e=(BasicEvent)o;
            if (e.y<64){            // rotation only affects retinaEvents
                tmp=e.x;
                e.x=(short)(sy-4-e.y-1);        // sy-4 because of the chip...
                e.y=tmp;
            }
            //else System.out.println(e.x+" "+e.y);
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
    
    
    

    public void update(Observable o, Object arg) {
        sx=chip.getSizeX();
        sy=chip.getSizeY();
    }
}
