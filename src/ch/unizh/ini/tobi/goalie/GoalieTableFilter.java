/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.tobi.goalie;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.BasicEvent;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.event.OutputEventIterator;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
/**
 * For the Goalie; filters in events from a trapezoidal region, discarding those from the edges and end of the table.
 * @author tobi/fope, telluride 2008
 */
public class GoalieTableFilter extends EventFilter2D {
    private int x0;
    {setPropertyTooltip("x0","UL trapezoid corner x");}
    private int x1;
    {setPropertyTooltip("x1","UR trapezoid X");}
    
    public GoalieTableFilter(AEChip chip) {
        super(chip);
        x0=getPrefs().getInt("GoalieTableFilter.x0", 0);
        x1=getPrefs().getInt("GoalieTableFilter.x1", chip.getSizeX());
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();
        for(BasicEvent i : in) {
            if(isInsideTable(i)) {
                BasicEvent o=(BasicEvent) outItr.nextOutput();
                o.copyFrom(i);
            }
        }
        return in;
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    private boolean isInsideTable(BasicEvent i) {
        // if i.x and i.y is inside the trapezoid then return true
        return true;
    }
}
