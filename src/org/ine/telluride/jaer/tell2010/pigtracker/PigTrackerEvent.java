package org.ine.telluride.jaer.tell2010.pigtracker;

import net.sf.jaer.event.PolarityEvent;

/** Output event from the PigTracker object tracker.
 * 
 * @author tobi
 */
public class PigTrackerEvent extends PolarityEvent {

    boolean colorIt = false;

    @Override
    public int getNumCellTypes() {
        return 4;
    }

    @Override
    public int getType() {
        return colorIt ? super.getType() + 2 : super.getType();
    }
}
