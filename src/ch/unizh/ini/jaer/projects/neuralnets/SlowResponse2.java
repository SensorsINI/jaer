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
public class SlowResponse2 extends MultiSourceProcessor {
    
    int dimx;
    int dimy;
    
    private boolean eventBased=false;
    
    ImageDisplay im;
    
    public float[] state1;
    public float[] state2;
    public int[] lastUpdateTime;
    
    public boolean autokern;
    
    float[][] autoKernel;
    
    private float timeConst= getFloat("timeConst", 500000);
    
    boolean isTimeInitialized=false;
            
    public float getTimeConst() {
        return timeConst;
    }

    public void setTimeConst(float tcEPSC) {
        this.timeConst = tcEPSC;
        updateEpscScale();
    }
   
    
    float epscDecayRate;
    
    /** We'd like to make it so each epsc, everall, adds has an area of 1 under it. */
    public void updateEpscScale()
    {   epscDecayRate=1/timeConst;        
    }

    @Override
    public String[] getInputNames() {
        return new String[] {"retina"};
    }
    
    public void updateState(int toTime)
    {
        for (int i=0; i<state1.length; i++)
        {
            updateState(toTime,i);
        }       
    }
    
    public void updateState(int toTime,int ixUnit)
    {
        state1[ixUnit]*=Math.exp((lastUpdateTime[ixUnit]-toTime)/timeConst);
        lastUpdateTime[ixUnit]=toTime;
    }
    
    public void fireEventToUnit(PolarityEvent evin)
    {
        int addr=dim2addr(evin.x,evin.y);
        updateState(evin.timestamp,addr);
        
        state1[addr]+=evin.getPolarity()==PolarityEvent.Polarity.On?epscDecayRate:-epscDecayRate;
        
    }
                
    public SlowResponse2(AEChip chip)
    {   super(chip);
    
    
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
            fireEventToUnit((PolarityEvent)ev);
        }
        
        if (in.isEmpty()) 
            return out;
        
        
        
        OutputEventIterator outItr=out.outputIterator();
               
        
        int time=in.getLastTimestamp();
        
        updateState(time);
        
        updateDisplay();
        
        return in;
        
    }

    @Override
    public void resetFilter() {
    }
    
    public void doStartDisplay()
    {
        
        
        initDisplay();
        
    }
    
    public void build()
    {
        
        dimx=getChip().getSizeX();
        dimy=getChip().getSizeY();
        
        int nUnits=dimx*dimy;
        
        state1=new float[nUnits];
        state2=new float[nUnits];
        lastUpdateTime=new int[nUnits];
               
        
        
    }
    
    
    
    public void initDisplay()
    {
        im=ImageDisplay.createOpenGLCanvas();
        
        im.setImageSize(dimx,dimy);
            
        im.setSize(400,400);
        
        
        JPanel p=new JPanel();
        p.add(im);
        
        this.addDisplay(p);
        
//        JFrame j=new JFrame();
//        j.getContentPane().add(im);        
//        j.setVisible(true);
        
    }
    
    float displayMin;
    float displayMax;
    float displayAdaptationRate=0.1f;
    
    public void updateDisplay()
    {
        if (im==null)
        {
            return;
        }
            
        
        
        float minAct=Float.MAX_VALUE;
        float maxAct=Float.MIN_VALUE;
        
        float del=displayMax-displayMin;
        
        for (int i=0; i<state1.length; i++)
        {
            
            float vmem=state1[i];
            float level=(vmem-displayMin)/del;
            
            
            
            im.setPixmapGray(i/dimy, i%dimy, level);
            
            
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
        for (int i=0; i<lastUpdateTime.length; i++)
        {   lastUpdateTime[i]=timestamp;
        }
    }
    
    public int dim2addr(short xloc, short yloc)
    {   return yloc+xloc*dimy;        
    }
    
    
    
    
    
}
