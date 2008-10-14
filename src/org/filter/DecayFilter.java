/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.orangesky.visuals;
import ch.unizh.ini.caviar.aemonitor.AEPacketRaw;
import ch.unizh.ini.caviar.aemonitor.EventRaw;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.event.OutputEventIterator;
import ch.unizh.ini.caviar.event.PolarityEvent;
import ch.unizh.ini.caviar.event.PolarityEvent.Polarity;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import java.util.Random;
import java.lang.Math.*;
/**
 * This example event filter lets through events with some fixed probablity.
 * @author tobi
 */
public class DecayFilter extends EventFilter2D{

    private float tWindow=getPrefs().getFloat("DecayFilter.tWindow",500000);
    private boolean Decay=getPrefs().getBoolean("DecayFilter.Decay",false);
//    private float tau=getPrefs().getFloat("DecayFilter.tau",200000);
    Random r=new Random();
//    protected AEPacketRaw win=new AEPacketRaw();
    protected final int RingBufSize = 5000000;
    protected int[]a=new int[RingBufSize];
    protected int[]t=new int[RingBufSize];
    protected int index=0,length=0;
    
    public DecayFilter(AEChip chip){
        super(chip);
//        getPrefs().putFloat("DecayFilter.tWindow",tWindow);
//        getPrefs().putFloat("DecayFilter.tau",tau);       
        setPropertyTooltip("tWindow","time window in milliseconds");
        setPropertyTooltip("Decay","enables decay (fading out of events)");
//        setPropertyTooltip("tau","exponential decay time constant");
    }
    
    public float gettWindow(){return tWindow;}
    public void settWindow(float tWindow){this.tWindow=tWindow;}
//    public float gettau(){return tau;}
//    public void settau(float tau){this.tau=tau;}
    public boolean getDecay(){return Decay;}
    public void setDecay(boolean Decay){this.Decay=Decay;}
    
    /** This filterPacket method assumes the events have PolarityEvent type
     * 
     * @param in the input packet
     * @return the output packet, where events have possibly been deleted from the input
     */
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        //copy all new events into time window buffer
        //todo: ringbuffer and backwards out copying
        long t0 = System.nanoTime();
        checkOutputPacketEventType(in); // make sure the built-in output packet has same event type as input packet
//        log.info(""+in.getSize()+" IN EVENTS");
        for(int i=0;i<in.getSize();i++)
        { 
            PolarityEvent oe=(PolarityEvent)in.getEvent(i);
            a[index] = ((short)oe.getX()+(short)(oe.getY()<<7)+(short)(oe.getType()<<15))&0xffff;
            t[index] = oe.getTimestamp();
            index=cAdd(index,1);
            length++;
        }
//        log.info("t1="+(System.nanoTime()-t0));
        
        int j=cAdd(index,-1);
        int tlast=t[j];
        int timelimit = tlast-(int)tWindow;
        int firstindex= cAdd(index,-length);
        try{
        int i=0,N=100;while (i<length && t[j]>=timelimit) {j=cAdd(j,-N);i+=N;}; cAdd(j,N);
        while (j!=firstindex && t[j]>=timelimit) j=cAdd(j,-1);
        }catch(java.lang.ArrayIndexOutOfBoundsException e){
        log.info("java.lang.ArrayIndexOutOfBoundsException: "+j+","+e.getMessage());}
        j=cAdd(j,1);firstindex=j;length=cAdd(index,-firstindex);
//        log.info("t2="+(System.nanoTime()-t0));
        OutputEventIterator outItr=out.outputIterator(); // get the built in iterator for output events
        PolarityEvent e = new PolarityEvent();
        while(j!=index){ // iterate over input events
        {  
            boolean b;
            if(Decay)
                b=(r.nextFloat()<((float)-tlast + t[j] + tWindow)/tWindow);
            else
                b=true;
            if(b)//(java.lang.Math.exp(-dt/tau))))
            {
                //copy event
                short adr = (short) (0xffff & a[j]);
                e.x = (short)(0x7f  & ((adr << 9) >> 9));
                e.y = (short)(0x7f  & ((adr << 2) >> 9));
                if ((short)(0x7f  & (adr >> 15))==1)
                    e.polarity=Polarity.On;
                else
                    e.polarity=Polarity.Off;
                e.timestamp = t[j];
                ((PolarityEvent)outItr.nextOutput()).copyFrom(e);
            }
            j = cAdd(j,1);
        } 
        }
//        log.info("t3="+(System.nanoTime()-t0));
//        log.info("last packet dt: "+ in.getDurationUs()+", time window dt: "
//                + (tlast-t[firstindex])+"  -  "+out.getSize()+"/"+length+" events output");
        return out;
    }
    
    public int cAdd(int n, int dn)
    { return (n+dn+RingBufSize)%RingBufSize;
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

}
