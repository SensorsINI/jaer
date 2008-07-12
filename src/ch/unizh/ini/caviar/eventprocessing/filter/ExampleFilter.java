/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.eventprocessing.filter;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.BasicEvent;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.event.OutputEventIterator;
import ch.unizh.ini.caviar.event.PolarityEvent;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import java.util.Random;
/**
 *
 * @author tobi
 */
public class ExampleFilter extends EventFilter2D{

    private float passProb=getPrefs().getFloat("TestFilter.passProb",.5f);
    Random r=new Random();
    
    public ExampleFilter(AEChip chip){
        super(chip);
        
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();
        for(Object o:in){
            PolarityEvent e=(PolarityEvent)o;
            if(r.nextFloat()<getPassProb()){
                PolarityEvent oe=(PolarityEvent) outItr.nextOutput();
                oe.copyFrom(e);
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
        
    }

    public

    float getPassProb() {
        return passProb;
    }

    public void setPassProb(float passProb) {
        this.passProb=passProb;
        getPrefs().putFloat("TestFilter.passProb", passProb);
    }

}
