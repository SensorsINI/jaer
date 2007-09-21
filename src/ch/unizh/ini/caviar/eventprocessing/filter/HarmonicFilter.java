/*
 * HarmonicFilter.java
 *
 * Created on 13 april 2006
 *
 */

package ch.unizh.ini.caviar.eventprocessing.filter;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import java.util.*;

/**
 * An AE filter that filters out boring events caused by global flickering illumination. This filter measures the global event activity to obtain
 * the phase and amplitude of flicker. If the amplitude exceeds a threashold, then events around the peak activity are filtered away.
 *
 * The phase and amplitude are computed by one of two methods.
 *
 * <p>
 * The first uses a global harmonic oscillator with adjustable resonant frequency (set by user
 * to double line frequency) and adjustable quality factor Q. This resonator is driven by ON and OFF events in opposite directions. It resonates with a phase such that
 * it crosses zero at the peak of ON and OFF activities. During a zero crossing, events are filtered away.
 *
 *<p>
 *The second method (planned, not yet implemented) histograms events into a cyclic histogram whose period is set as a parameter (e.g. 10 ms for 50 Hz illumination with line doubling).
 *The histogram peaks tell the filter where to reject events. The histogram is forgotten slowly by periodically decaying all values. This method is not as physical and introduces
 *a kind of 'frame' for forgetting, but it is slightly cheaper to compute.
 *
 * @author tobi
 */
public class HarmonicFilter extends EventFilter2D implements Observer  {
    public boolean isGeneratingFilter(){ return false;}
    
    public HarmonicFilter(AEChip chip){
        super(chip);
        resetFilter();
        chip.addObserver(this);
    }
    
    private boolean printStats=false;
    
    private float threshold=getPrefs().getFloat("HarmonicFilter.threshold",0.1f); // when value is less than this value, then we are crossing zero and don't pass events
    
    private int i;
    int cycle=0;
    
    float[][][] localPhases;
    int[][][] lastEventTimes;
    
//    /**
//     * filters in to out. if filtering is enabled, the number of out may be less
//     * than the number put in
//     *@param in input events can be null or empty.
//     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
//     */
//    synchronized public AEPacket2D filter(AEPacket2D in) {
//        if(in==null) return null;
//        in.setNumCellTypes(2);
//        AEPacket2D out=in;
//        if(!filterEnabled) return in;
//        if(enclosedFilter!=null) in=enclosedFilter.filter(in);
//        if(!filterInPlaceEnabled) out=new AEPacket2D(in.getNumEvents());
//        out.setNumCellTypes(2);
//        // filter
//        
//        int n=in.getNumEvents();
//        if(n==0) return in;
//        
//        short[] xs=in.getXs(), ys=in.getYs();
//        int[] timestamps=in.getTimestamps();
//        byte[] types=in.getTypes();
//        
//        short[] outxs=out.getXs(), outys=out.getYs();
//        int[] outtimestamps=out.getTimestamps();
//        byte[] outtypes=out.getTypes();
//        
//        // for each event only write it to the tmp buffers if it isn't boring
//        // this means only write if the dt is sufficiently different than the previous dt
//        index=0;
//        for(i=0;i<n;i++){
//            ts=timestamps[i];
//            type=types[i];
//            oscillator.update(ts,type);
//            if(Float.isNaN(oscillator.getVelocity())){
//                setFilterEnabled(false);
//                log.warning("oscillator overflowed, disabling filter");
//                resetFilter();
//                return in;
//            }
//            if(!oscillator.isZeroCrossing()){
//                x=xs[i];
//                y=ys[i];
//                out.addEvent(x,y,type,ts);
//            }
//        }
//        if(printStats){
//            float t=1e-6f*ts;
//            System.out.println( (cycle++)+","+t+","+oscillator.getPosition()+","+oscillator.getMeanPower());
//        }
//        return out;
//    }
    
    synchronized public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        resetFilter(); // reset oscillator so that it doesn't immediately go unstable.
    }
    
    /** returns array of last event times, x,y,type,[t0,t1], where t0/t1 are the last two event times, t0 first. */
    public Object getFilterState() {
        return oscillator;
    }
    
    synchronized public void resetFilter() {
        oscillator.reset();
    }
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    public void initFilter() {
        resetFilter();
    }
    
    HarmonicOscillator oscillator=new HarmonicOscillator();
    final float TICK=1e-6f;
    
    public class HarmonicOscillator{
        
        final float GEARRATIO=20; // chop up times between spikes by tau/GEARRATIO timesteps
        final int POWER_AVERAGING_CYCLES=10; // number of cycles to smooth power measurement
        
        boolean wasReset=true;
        float f0=50; // resonant frequency in Hz
        float tau, omega,tauoverq,reciptausq;
        float dtlim;
        float quality=3;
        float amplitude;
        float y=0,x=0;
        private int t=0;  // present time in timestamp ticks, used for dt in update, then stores this last timestamp
        float lastx,lasty;
        float meansq;
        float power=0;
        public HarmonicOscillator(){
            setFreq(f0);
            setQuality(quality);
        }
        synchronized public void update(int ts, byte type){
            if(wasReset) {
                t=ts;
                wasReset=false;
                return;
            }
            lastx=x;
            lasty=y;
            
            // apply the momentum imparted by the event. this directly affects velocity (y)
            // each event kicks velocity by 1 either pos or neg
            y=y+type+type-1;
            
            // compute the delta time since last event.
            // check if it is too long for numerical stability, if so, integrate multiple steps
            float dt=TICK*(ts-t); // dt is in seconds now... if TICK is correct
            if(dt<=0) return;
            int nsteps=(int)Math.ceil(dt/dtlim);
            float ddt=dt/nsteps*reciptausq;
            float ddt2=dt/nsteps;
            for(int i=0;i<nsteps;i++){
                y=y-ddt*(tauoverq*y+x);
                x=x+ddt2*y;
//            System.out.println(ddt2+","+x+","+y);
            }
//            System.out.println(dt+","+x+","+y);
            
            float sq=x*x; // instantaneous power
            // compute avg power by lowpassing instantaneous power over POWER_AVERAGING_CYCLES time
            float alpha=dt*f0/POWER_AVERAGING_CYCLES; // mixing factor, take this much of new, 1-alpha of old
            power=power*(1-alpha)+sq*alpha;
            
            // update timestamp
            t=ts;
        }
        synchronized public void reset(){
            y=0; x=0;
            wasReset=true;
        }
        
        /** @return the current 'position' value of the oscillator */
        public float getPosition(){return x;}
        /** @return the current 'velocity' value of the osciallator */
        public float getVelocity(){return y;}
        
        synchronized void setFreq(float f){
            f0=f; // hz
            omega=(float)(2*Math.PI*f0);  // radians/sec
            tau=1f/omega; // seconds
            tauoverq=tau/quality;
            reciptausq=1f/(tau*tau);
            dtlim=tau/GEARRATIO;  // timestep must be at most this long or unstable numerically
        }
        
        public float getFreq(){ return f0;}
        
        synchronized void setQuality(float q){
            quality=q;
            tauoverq=tau/quality;
        }
        
        public float getQuality(){ return quality;}
        
        /** @return the last amplitude of the oscillator, i.e., the last magnitude of the last peak of activity */
        public float getAmplitude(){
            return amplitude;
        }
        
        // if zero crossing we don't pass event
        private boolean isZeroCrossing() {
            if(x*x/power<getThreshold()) return true;
            return false;
        }
        
        float getMeanPower(){
            return power;
        }
        
        public int getT() {
            return t;
        }
        
    }
    
    public float getThreshold() {
        return threshold;
    }
    
    public void setThreshold(float threshold) {
        this.threshold = threshold;
        getPrefs().putFloat("HarmonicFilter.threshold",threshold);
    }
    
    public float getQuality(){return oscillator.getQuality();}
    public void setQuality(float q){ oscillator.setQuality(q);}
    
    public float getFreq(){ return oscillator.getFreq();}
    public void setFreq(float f){ oscillator.setFreq(f);}
    
    public boolean isPrintStats() {
        return printStats;
    }
    
    public void setPrintStats(boolean printStats) {
        this.printStats = printStats;
    }
    
    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        // filter
        
        int n=in.getSize();
        if(n==0) return in;
        
        // for each event only write it to the tmp buffers if it isn't boring
        // this means only write if the dt is sufficiently different than the previous dt
        OutputEventIterator outItr=out.outputIterator();
        for(Object ein:in){
            PolarityEvent e=(PolarityEvent)ein;
            oscillator.update(e.timestamp, e.type);
            if(Float.isNaN(oscillator.getVelocity())){
                setFilterEnabled(false);
                log.warning("oscillator overflowed, disabling filter");
                resetFilter();
                return in;
            }
            if(!oscillator.isZeroCrossing()){
                outItr.nextOutput().copyFrom(e);
            }
        }
        if(printStats){
            float t=1e-6f*in.getLastTimestamp();
            log.info( (cycle++)+","+t+","+oscillator.getPosition()+","+oscillator.getMeanPower());
        }
        return out;
    }
    
    
}
