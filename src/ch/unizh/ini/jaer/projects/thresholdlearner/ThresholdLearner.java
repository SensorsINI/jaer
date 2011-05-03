/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.thresholdlearner;

import java.util.Observable;
import java.util.Observer;
import java.util.prefs.Preferences;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author tobi
 */
public class ThresholdLearner extends EventFilter2D implements Observer {

    float[] thresholds;
    int[][][] lastEventTimes;

    public ThresholdLearner(AEChip chip) {
        super(chip);
        //TODO load thresholds
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkOutputPacketEventType(TemporalContrastEvent.class);
        checkArrays();
        if (in.getEventClass() != PolarityEvent.class) {
            return in;
        }
        OutputEventIterator outItr = out.outputIterator();
        final int ntypes = 2;
        final int sx = chip.getSizeX();
        for (BasicEvent be : in) {
            PolarityEvent pe = (PolarityEvent) be;
            lastEventTimes[pe.x][pe.y][pe.type] = pe.timestamp;
            TemporalContrastEvent tce = (TemporalContrastEvent) outItr.nextOutput();
            tce.copyFrom(pe);
            tce.contrast = thresholds[pe.type + pe.x * ntypes + pe.y + sx];
        }
        return out;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    synchronized public void update(Observable o, Object arg) {
        if (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY || arg == AEChip.EVENT_NUM_CELL_TYPES) {
            checkArrays();
        }

    }

    private void checkArrays() {
        int n = chip.getNumCells();
        if (n > 0) {
            if ((thresholds == null || n != thresholds.length)) {
                thresholds = new float[n];
            }
            if (lastEventTimes == null || lastEventTimes.length != chip.getSizeX() || lastEventTimes[0].length != chip.getSizeY() || lastEventTimes[0][0].length != chip.getNumCellTypes()) {
                lastEventTimes = new int[chip.getSizeX()][chip.getSizeY()][chip.getNumCellTypes()];
            }
        }
    }
}
