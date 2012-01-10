/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;

/**
 *
 * @author Peter
 */
public class BackgroundActivityExtended extends BackgroundActivityFilter {
    
    int dtm;
    
    //  Initialize the filter
    public  BackgroundActivityExtended(AEChip  chip){
        super(chip);

        setPropertyTooltip("N", "Weight");
    }
    
    
    
    public int getDm() {
        return this.dt;
    }

    /**
     * sets the background delay in us
    <p>
    Fires a PropertyChangeEvent "dt"
    
     * @see #getDt
     * @param dt delay in us
     */
    public void setDtm(final int dt) {
        getPrefs().putInt("BackgroundActivityFilter.dt", dt);
        getSupport().firePropertyChange("dt", this.dt, dt);
        this.dt = dt;
    }
    
    
    
    
}
