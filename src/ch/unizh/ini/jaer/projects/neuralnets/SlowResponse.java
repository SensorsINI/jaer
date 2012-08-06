/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import javax.swing.JFrame;
import javax.swing.JPanel;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.MultiSourceProcessor;
import net.sf.jaer.graphics.ImageDisplay;

/**
 *
 * @Desc This filter attempts to fill in background shading information by 
 * remembering the most recent events that passed.  For instance, if an edge passes,
 * indicating a transition from light to dark, and you've got no on events since 
 * then, it's a good assumption that things are still dark.
 * 
 * The implementation is reasonable biologically plausible.  It uses an array of
 * Leaky Integrate-and-Fire neurons (one neuron per pixel).  The recent
 * shading information is stored in the form of slowly post-synaptic currents.  
 * When an edge passes, it adds to these currents.  The currents continue to 
 * stimulate a neuron long after the edge passes.
 * 
 * 
 * @author Peter
 */
public class SlowResponse extends MultiSourceProcessor {
    
    int dimx;
    int dimy;
    
    private boolean eventBased=false;
    
    ImageDisplay im;
    
    
    private float thresh= getFloat("thresh", 0.1f);   
    private float tcEPSC= getFloat("tcEPSC", 500000);
    private float tcMem= getFloat("tcMem", 30000);;
    
    boolean isTimeInitialized=false;
    
    Neuron[] neurons;

    public float getThresh() {
        return thresh;
    }

    public void setThresh(float thresh) {
        this.thresh = thresh;
    }

    public float getTcEPSC() {
        return tcEPSC;
    }

    public void setTcEPSC(float tcEPSC) {
        this.tcEPSC = tcEPSC;
        updateEpscScale();
    }

    public float getTcMem() {
        return tcMem;
    }

    public void setTcMem(float tcMem) {
        this.tcMem = tcMem;
    }
    
    
    float epscDecayRate;
    
    /** We'd like to make it so each epsc, everall, adds has an area of 1 under it. */
    public void updateEpscScale()
    {   epscDecayRate=1/tcEPSC;        
    }

    @Override
    public String[] getInputNames() {
        return new String[] {"retina"};
    }

    public boolean isEventBased() {
        return eventBased;
    }

    public void setEventBased(boolean eventBased) {
        this.eventBased = eventBased;
    }
    
    
    /** Simple unit with an ON threshold and an OFF threshold.  Inputs add to an
     * EPSC, which is added to the membrane potential over time.  
     */
    class Neuron
    {
        short locx;
        short locy;
        
        int lastUpdateTime;
        float epsc;
                
        float vmem;
        
        public Neuron(short xloc,short yloc)
        {   locx=xloc;
            locy=yloc;            
        }
        
        void fireTo(PolarityEvent p)
        {
            update(p.timestamp);           
            
            epsc+=p.getPolarity()==PolarityEvent.Polarity.On?epscDecayRate:-epscDecayRate;
            
        }
        
        /** Update the state of the membrane potential and the epsc */
        public void update(int toTime)
        {
            int ndt=lastUpdateTime-toTime;
            float epscDecay= (float) Math.exp(ndt/getTcEPSC());
            
            // Add the area under the epsc function since the last update
            // NOTE: This is not exact, because it doesn't factor additional decay due to added current in between.... Fix this!
            vmem*=(float)Math.exp(ndt/getTcMem());
            vmem+=(1-epscDecay)*epsc*tcEPSC; 
            
            // Update the EPSC
            epsc*=epscDecay;            
            
            lastUpdateTime=toTime;
            
        }
        
        
        public PolarityEvent testFire(int time)
        {
            update(time);
            
            if (vmem>getThresh())
            {   vmem=0;
                return makeEvent(time,PolarityEvent.Polarity.On);
            }
            else if (vmem<-getThresh())
            {   vmem=0;
                return makeEvent(time,PolarityEvent.Polarity.Off);
            }
            else
                return null;
            
        }
        
        public PolarityEvent makeEvent(int time,PolarityEvent.Polarity pol)
        {
            PolarityEvent p=new PolarityEvent();
            p.x=locx;
            p.y=locy;
            p.timestamp=time;
            p.polarity=pol;
            
            return p;
        }
        
        
        
    }
        
    public SlowResponse(AEChip chip)
    {   super(chip,1);
    
    
        setPropertyTooltip("thresh", "Firing threshold of slow-response neurons");
        setPropertyTooltip("tcEPSC", "Time-Constant of the EPSCs, in microseconds.  Longer TC means shading is more persistent");
        setPropertyTooltip("tcMem", "Time-Constant of the neuron membrane potentials, in microseconds.");
        setPropertyTooltip("eventBased", "Generate events indicating slow-response activity.");
    
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        if (!isTimeInitialized)
        {   if (in.isEmpty())
                return out;     
        
            build();
            initNeuronsOnTimeStamp(in.getFirstTimestamp());    
            isTimeInitialized=true;    
                        
        }
        
        for (Object ev:in)
        {
            PolarityEvent evin=(PolarityEvent)ev;
            
            neurons[dim2addr(evin.x,evin.y)].fireTo(evin);
                            
        }
        
        if (in.isEmpty()) 
            return out;
        
        
        
        OutputEventIterator outItr=out.outputIterator();
               
        
        int time=in.getLastTimestamp();
        for (Neuron n:neurons)
        {
            
            n.update(time);
            
            if (isEventBased())
            {
                PolarityEvent evout=n.testFire(time);

                if (evout!=null)
                    outItr.writeToNextOutput(evout);      
            }
        }
//        
        updateDisplay();
        
        if (isEventBased())
            return out;
        else
            return in;
    }

    @Override
    public void resetFilter() {
        
        
        
        
    }
    
    public void build()
    {
        
        dimx=getChip().getSizeX();
        dimy=getChip().getSizeY();
        
        neurons=new Neuron[dimx*dimy];        
        
        for (int i=0; i<neurons.length; i++)
        {   neurons[i]=new Neuron((short)(i/dimy),(short)(i%dimy));
        }
        
        updateEpscScale();
        
        initDisplay();
        
        
        
        
    }
    
    
    
    public void initDisplay()
    {
        im=ImageDisplay.createOpenGLCanvas();
        
        im.setImageSize(dimx,dimy);
            
        im.setSize(400,400);
        
        
        JPanel p=new JPanel();
        p.add(im);
        
        this.addDisplayWriter(p);
        
//        JFrame j=new JFrame();
//        j.getContentPane().add(im);        
//        j.setVisible(true);
        
    }
    
    float displayMin;
    float displayMax;
    float displayAdaptationRate=0.1f;
    
    public void updateDisplay()
    {
        
        float minAct=Float.MAX_VALUE;
        float maxAct=Float.MIN_VALUE;
        
        float del=displayMax-displayMin;
        
        for (int i=0; i<neurons.length; i++)
        {
            
            float vmem=neurons[i].vmem;
            float level=(vmem-displayMin)/del;
            
            im.setPixmapGray(neurons[i].locx, neurons[i].locy, level);
            
            
            minAct=minAct<vmem?minAct:vmem;
            maxAct=maxAct>vmem?maxAct:vmem;
        }
        
        if (displayMin==0 && displayMax==0)
        {
            displayMin=minAct;
            displayMax=maxAct;
        }
        else
        {
            displayMin=displayAdaptationRate*minAct+(1-displayAdaptationRate)*displayMin;
            displayMax=displayAdaptationRate*maxAct+(1-displayAdaptationRate)*displayMax;
        }
        
        im.repaint();
    }
    
    

    @Override
    public void initFilter() {        
    }
    
    void initNeuronsOnTimeStamp(int timestamp)
    {        
        for (Neuron n:neurons)
        {   n.lastUpdateTime=timestamp;
        }
    }
    
    public int dim2addr(short xloc, short yloc)
    {   return yloc+xloc*dimy;        
    }
    
    
    
    
    
}
