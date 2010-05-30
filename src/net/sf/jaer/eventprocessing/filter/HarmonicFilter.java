/*
 * HarmonicFilter.java
 *
 * Created on 13 april 2006
 *
 */
package net.sf.jaer.eventprocessing.filter;
import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.*;
import net.sf.jaer.graphics.FrameAnnotater;
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
public class HarmonicFilter extends EventFilter2D implements Observer,FrameAnnotater{
    public static String getDescription (){
        return "An AE filter that filters out boring events caused by global flickering illumination. This filter measures the global event activity to obtain the phase and amplitude of flicker. If the amplitude exceeds a threashold, then events around the peak activity are filtered away.";
    }
    private boolean printStats = false;
    private float threshold = getPrefs().getFloat("HarmonicFilter.threshold",0.1f); // when value is less than this value, then we are crossing zero and don't pass events
//    private int cycle = 0;
    private float[][][] localPhases; // measures phase of pixel relative to oscillator
//    private int[][][] lastEventTimes;
    private float[][][] localCorrelation;  // local pixel correlation with global harmonic oscillator
    HarmonicOscillator oscillator = new HarmonicOscillator();
    final float TICK = 1e-6f;
    TextRenderer renderer;

    public HarmonicFilter (AEChip chip){
        super(chip);
        resetFilter();
        chip.addObserver(this);
        setPropertyTooltip("threshold","increase to reduce # of events; when oscillator instantaneous value is within this value as fraction of amplitude, then events are blocked ");
        setPropertyTooltip("quality","quality factor of osciallator, increase to sharpen around best freq");
        setPropertyTooltip("freq","resonant frequency of oscillator in Hz; choose at observed fundamental");
    }

    synchronized public void setFilterEnabled (boolean yes){
        super.setFilterEnabled(yes);
        resetFilter(); // reset oscillator so that it doesn't immediately go unstable.
    }

    synchronized public void resetFilter (){
        oscillator.reset();
    }

    public void update (Observable o,Object arg){
        initFilter();
    }

    public void initFilter (){
        resetFilter();
    }
 
    public void annotate (GLAutoDrawable drawable){
        if ( !printStats ){
            return;
        }
        if ( renderer == null ){
            renderer = new TextRenderer(new Font("SansSerif",Font.PLAIN,16),true,true);
        }
        renderer.beginRendering(drawable.getWidth(),drawable.getHeight());
        renderer.draw(oscillator.toString(),10,10);
        renderer.endRendering();
        oscillator.draw(drawable.getGL());
    }
    public class HarmonicOscillator{
        final float GEARRATIO = 20; // chop up times between spikes by tau/GEARRATIO timesteps
        final int POWER_AVERAGING_CYCLES = 10; // number of cycles to smooth power measurement
        boolean wasReset = true;
        float f0 = prefs().getFloat("HarmonicFilter.frequency",100); // resonant frequency in Hz
        float tau, omega, tauoverq, reciptausq;
        float dtlim;
        float quality = prefs().getFloat("HarmonicFilter.quality",3);
        float amplitude;
        float y = 0, x = 0;  // x =posiiton, y=velocity
        private int t = 0;  // present time in timestamp ticks, used for dt in update, then stores this last timestamp
        float lastx, lasty;
        float meansq;
        float power = 0;
        float maxx, minx, maxy, miny;
        private float maxPower = 0;

        public HarmonicOscillator (){
            setFreq(f0); // needed to init vars
        }

        synchronized public void reset (){
            y = 0;
            x = 0;
            power = 0;
            maxx = 0;
            minx = 0;
            maxy = 0;
            miny = 0;
            wasReset = true;
        }

        synchronized public void update (int ts,byte type){
            if ( wasReset ){
                t = ts;
                wasReset = false;
                return;
            }
            lastx = x;
            lasty = y;

            // apply the momentum imparted by the event. this directly affects velocity (y)
            // each event kicks velocity by 1 either pos or neg
            y = y + type + type - 1;

            // compute the delta time since last event.
            // check if it is too long for numerical stability, if so, integrate multiple steps
            float dt = TICK * ( ts - t ); // dt is in seconds now... if TICK is correct
            if ( dt <= 0 ){
                return;
            }
            int nsteps = (int)Math.ceil(dt / dtlim); // dtlim comes from natural freq; if dt is too large, then nsteps>1
            float ddt = dt / nsteps * reciptausq;  // dimensionless timestep
            float ddt2 = dt / nsteps;  // real timestep
            for ( int i = 0 ; i < nsteps ; i++ ){
                y = y - ddt * ( tauoverq * y + x );
                x = x + ddt2 * y;
//            System.out.println(ddt2+","+x+","+y);
            }
//            System.out.println(dt+","+x+","+y);

            float sq = x * x; // instantaneous power
            // compute avg power by lowpassing instantaneous power over POWER_AVERAGING_CYCLES time
            // TODO is this a valid measure of instantaneous power?  shouldn't we be using amplitude and current position to filter events?
            float alpha = dt * f0 / POWER_AVERAGING_CYCLES; // mixing factor, take this much of new, 1-alpha of old
            power = power * ( 1 - alpha ) + sq * alpha;
            if ( Float.isNaN(power) ){
                log.warning("power is NaN, resetting oscillator");
                reset();
            }
            if ( power > maxPower ){
                maxPower = power;
            }

            if ( x > maxx ){
                maxx = x;
            } else if ( x < minx ){
                minx = x;
            }
            if ( y > maxy ){
                maxy = y;
            } else if ( y < miny ){
                miny = y;
            }

            // update timestamp
            t = ts;

        }

        public void draw (GL gl){
            gl.glPushMatrix();
            gl.glTranslatef(chip.getSizeX() / 2,chip.getSizeY() / 2,0);
            float w = oscillator.maxx - oscillator.minx;
            float h = oscillator.maxy - oscillator.miny;
            gl.glScalef(chip.getSizeX() / w,chip.getSizeY() / h,1);
            if ( isNearZeroCrossing() ){
                gl.glColor3f(1,0,0);
            } else{
                gl.glColor3f(0,1,0);
            }
            final float r = .01f;
            gl.glRectf(oscillator.x - r * w,oscillator.y - r * h,oscillator.x + r * w,oscillator.y + r * h);
            gl.glPopMatrix();

        }

        /** @return the current 'position' value of the oscillator */
        public float getPosition (){
            return x;
        }

        /** @return the current 'velocity' value of the osciallator */
        public float getVelocity (){
            return y;
        }

        synchronized void setFreq (float f){
            f0 = f; // hz
            omega = (float)( 2 * Math.PI * f0 );  // radians/sec
            tau = 1f / omega; // seconds
            tauoverq = tau / quality;
            reciptausq = 1f / ( tau * tau );
            dtlim = tau / GEARRATIO;  // timestep must be at most this long or unstable numerically
            prefs().putFloat("HarmonicFilter.frequency",f0);
        }

        public float getFreq (){
            return f0;
        }

        synchronized void setQuality (float q){
            quality = q;
            tauoverq = tau / quality;
            prefs().putFloat("HarmonicFilter.quality",quality);
        }

        public float getQuality (){
            return quality;
        }

        /** @return the last amplitude of the oscillator, i.e., the last magnitude of the last peak of activity */
        public float getAmplitude (){
            return amplitude;
        }

        // if zero crossing we don't pass event
        private boolean isNearZeroCrossing (){
            if ( x * x / power < threshold ){
                return true;
            }
            return false;
        }

        float getMeanPower (){
            return power;
        }

        public int getT (){
            return t;
        }

        public String toString (){
            String s = String.format("bestFreq=%.1f Q=%.2g pos=%.1g vel=%.1g meanPower=%.1g maxPower=%.1g",f0,quality,x,y,power,maxPower);
            return s;
//            return  "bestFreq="+f0+" t=" + t + " pos=" +x+" vel="+y+" ampl="+amplitude + " meanPower=" + getMeanPower();
        }

        /**
         * @return the maxPower
         */
        public float getMaxPower (){
            return maxPower;
        }

        /**
         * @param maxPower the maxPower to set
         */
        public void setMaxPower (float maxPower){
            this.maxPower = maxPower;
        }
    }

    public float getThreshold (){
        return threshold;
    }

    public void setThreshold (float threshold){
        float old = this.threshold;
//        if(threshold>1) threshold=1; else if(threshold<0) threshold=0;
        this.threshold = threshold;
        getPrefs().putFloat("HarmonicFilter.threshold",threshold);
        support.firePropertyChange("threshold",old,threshold);
    }

    public float getQuality (){
        return oscillator.getQuality();
    }

    public void setQuality (float q){
        oscillator.setQuality(q);
    }

    public float getFreq (){
        return oscillator.getFreq();
    }

    public void setFreq (float f){
        oscillator.setFreq(f);
    }

    public boolean isPrintStats (){
        return printStats;
    }

    public void setPrintStats (boolean printStats){
        this.printStats = printStats;
    }

    public EventPacket filterPacket (EventPacket in){
        int n = in.getSize();
        if ( n == 0 ){
            return in;
        }
        checkOutputPacketEventType(in.getEventClass());

        // for each event only write it to the tmp buffers if it isn't boring
        // this means only write if the dt is sufficiently different than the previous dt
        OutputEventIterator outItr = out.outputIterator();
        for ( Object ein:in ){
            PolarityEvent e = (PolarityEvent)ein;
            oscillator.update(e.timestamp,e.type);
            if ( Float.isNaN(oscillator.getVelocity()) ){
                setFilterEnabled(false);
                log.warning("oscillator overflowed, disabling filter");
                resetFilter();
                return in;
            }
            if ( !oscillator.isNearZeroCrossing() ){
                outItr.nextOutput().copyFrom(e);
            }
        }
//        if ( printStats ){
//            float t = 1e-6f * in.getLastTimestamp();
//            log.info("cycle=" + ( cycle++ ) + oscillator);
//        }
        return out;
    }
}
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

