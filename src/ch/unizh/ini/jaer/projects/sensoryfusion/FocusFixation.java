/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.sensoryfusion;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.label.DirectionSelectiveFilter;

/**
 *
 * @author Christian
 */
public class FocusFixation extends EventFilter2D{

    private DirectionSelectiveFilter directionFilter;
    private PidPanTiltControllerUSB panTiltControl;

    public FocusFixation (AEChip chip){
        super(chip);

        directionFilter = new DirectionSelectiveFilter(chip);
        panTiltControl = new PidPanTiltControllerUSB(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
            return in;
        }
        in = directionFilter.filterPacket(in);



        return in;
    }

    @Override
    public void resetFilter() {
        initFilter();
    }

    @Override
    public void initFilter() {
        directionFilter.resetFilter();
    }

}
