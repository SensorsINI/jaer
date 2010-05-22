/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.filter;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter;
/**
 * Estimates event rate from the input stream.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class EventRateEstimator extends EventFilter2D {

    public static String getDescription(){ return "Estimates event rate from the input event packets";}

    private LowpassFilter filter=new LowpassFilter();
    private int prevLastT=0;
    private float maxRate=getPrefs().getFloat("EventRateEstimator.maxRate",10e6f);
    private float filteredRate=0, instantaneousRate=0;
    private float eventRateTauMs = getPrefs().getFloat("EventRateEstimator.eventRateTauMs", 10);
    boolean initialized=false;

    public EventRateEstimator (AEChip chip){
        super(chip);
        filter.setTauMs(100);
        setPropertyTooltip("eventRateTauMs","lowpass filter time constant in ms for measuring event rate");
        setPropertyTooltip("maxRate","maximum estimated rate, which is used for zero ISIs between packets");
    }

    @Override
    public EventPacket<?> filterPacket (EventPacket<?> in){
        if(in==null || in.getSize()==0) return in; // if there are no events, don't touch values since we don't have a new update time
        int n=in.getSize();
        int lastT=in.getLastTimestamp();
        if(!initialized){
            prevLastT=lastT;
            initialized=true;
            return in;
        }
        int dt=lastT-prevLastT;
        prevLastT=lastT;
        if(dt<0){
            initialized=false;
            return in;
        }
        if(dt==0){
            instantaneousRate=maxRate; // if the time interval is zero, use the max rate
        }else{
            instantaneousRate=1e6f*(float)n/(dt*AEConstants.TICK_DEFAULT_US);
        }
        filteredRate=filter.filter(instantaneousRate,lastT);
        return in;

    }

    @Override
    public void resetFilter (){
        filter.reset();
    }

    @Override
    public void initFilter (){
    }

    public String toString(){
        return super.toString()+" rate="+filteredRate;
    }

       public float getEventRateTauMs() {
        return eventRateTauMs;
    }


        /** Time constant of event rate lowpass filter in ms */
    public void setEventRateTauMs(float eventRateTauMs) {
        if (eventRateTauMs < 0) {
            eventRateTauMs = 0;
        }
        this.eventRateTauMs = eventRateTauMs;
        filter.setTauMs(eventRateTauMs);
        getPrefs().putFloat("EventRateEstimator.eventRateTauMs", eventRateTauMs);
    }

    /** Returns last instantaneous rate, which is the rate of events from the last packet that was filtered that had a rate.
     * 
     * @return last instantaneous rate.
     */
    public float getInstantaneousEventRate(){
        return instantaneousRate;
    }

    /** Returns measured event rate
     @return lowpass filtered event rate
     */
    public float getFilteredEventRate (){
        return filteredRate;
    }

    /**
     * @return the maxRate
     */
    public float getMaxRate (){
        return maxRate;
    }

    /**
     *  The max rate that will be estimated when events are simultaneous.
     * @param maxRate the maxRate to set
     */
    public void setMaxRate (float maxRate){
        this.maxRate = maxRate;
        getPrefs().putFloat("EventRateEstimator.maxRate",maxRate);
    }


}
