package org.ine.telluride.jaer.tell2010.pigtracker;

import net.sf.jaer.event.TypedEvent;

/** Output event from the PigTracker object tracker.
 * 
 * @author tobi
 */
public class PigTrackerEvent extends TypedEvent {

    boolean colorIt = false;

    @Override
    public int getNumCellTypes() {
        return 4;
    }

    @Override
    public int getType() {
        return colorIt ? type + 2 : type;
    }
}
