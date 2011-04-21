/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.sensoryfusion;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Christian
 */
public class SensoryFusion extends EventFilter2D{

    public static final String getDescription(){ return "Allows localization, focussing and recognition of audiovisual objects";}
    private FocusFixation focusFixation;
    private SoundLocalization soundLocalization;

    public SensoryFusion (AEChip chip){
        super(chip);

        focusFixation = new FocusFixation(chip);
        this.setEnclosedFilter(focusFixation);

        soundLocalization = new SoundLocalization(chip);
        this.setEnclosedFilter(soundLocalization);

        initFilter();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
            return in;
        }

        in = focusFixation.filterPacket(in);
        in = soundLocalization.filterPacket(in);

        return in;
    }

    @Override
    public void resetFilter() {

    }

    @Override
    public void initFilter() {
        focusFixation.resetFilter();
        soundLocalization.resetFilter();
    }

}
