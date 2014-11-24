/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.calibration;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;

/**
 *
 * @author marc
 */
public class SingleCameraCalibration extends EventFilter2D {
    
    private int sx;
    private int sy;
    
    private ApsFrameExtractor frameExtractor;
    private FilterChain filterChain;
    
    public SingleCameraCalibration(AEChip chip) {
        super(chip);
        frameExtractor = new ApsFrameExtractor(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(frameExtractor);
        frameExtractor.setExtRender(true);
        setEnclosedFilterChain(filterChain);
        initFilter();
    }
    
    /** filters in to out. if filtering is enabled, the number of out may be 
     * less than the number putString in
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. 
     *         filtering may occur in place in the in packet. */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {

        // for each event only keep it if it is within dt of the last time 
        // an event happened in the direct neighborhood
        for (Object eIn : in) {
            if(eIn == null) break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            BasicEvent e = (BasicEvent) eIn;
            if (e.special) continue;
            
            
        }

        return in;
    }
    
    @Override
    public synchronized final void resetFilter() {
        initFilter();
    }

    @Override
    public final void initFilter() {
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
    }
    
}