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
    {setPropertyTooltip("x0","UL trapezoid corner x in pixels");}
    private int x1;
    {setPropertyTooltip("x1","UR trapezoid X in pixels");}
    private int height;
    {setPropertyTooltip("height","trapezoid height Y in pixels");}
    @Override
    public String getDescription() {
        return "Filters out events outside trapezoidal table shaped region for Goalie";
    }
    
    
    
    public GoalieTableFilter(AEChip chip) {
        super(chip);
        initFilter();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();
        for(BasicEvent i : in) {
            if(isInsideTable(i)) {
                BasicEvent o=(BasicEvent) outItr.nextOutput();
                o.copyFrom(i);
            }
        }
        return out;
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
        x0=getPrefs().getInt("GoalieTableFilter.x0", 0);
        x1=getPrefs().getInt("GoalieTableFilter.x1", chip.getSizeX());
        height=getPrefs().getInt("GoalieTableFilter.height",chip.getSizeY());
    }

    private boolean isInsideTable(BasicEvent i) {
        // if i.x and i.y is inside the trapezoid then return true
        float xv0;
        float xv1; 
        xv0 = (float)x0*i.y/height;
        xv1 = x0+x1-((float)i.y*x0/height);
       if (i.y<height && i.x>xv0 && i.x<xv1) 
           return true;
       else
           return false;
    }

    public int getX0() {
        return x0;
    }

    public void setX0(int x0) {
        if(x0<0) x0=0; else if(x0>x1) x0=x1;
        this.x0=x0;
        getPrefs().putInt("GoalieTableFilter.x0",x0);
    }

    public int getX1() {
        return x1;
    }

    public void setX1(int x1) {
        if(x1>chip.getSizeX()) x1=chip.getSizeX(); else if(x1<x0) x1=x0;
        this.x1=x1;
        getPrefs().putInt("GoalieTableFilter.x1",x1);
   }
    
    public void setHeight(int y) {
        if(y>chip.getSizeY()) y=chip.getSizeY(); else if(y<0) y=0;
        this.height=y;
        getPrefs().putInt("GoalieTableFilter.height",y);
    }

    public int getHeight() {
        return height;
    }
}
