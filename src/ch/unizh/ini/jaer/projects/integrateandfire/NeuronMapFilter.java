/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.event.TypedEvent;

/**
 * @Description This filter serves as a user-interface to a map of integrate and
 * fire neurons.  
 * @author Peter
 */
public class NeuronMapFilter  extends SuperLIFFilter {
    /* Put a layer of neurons on the input to act as a filter.  These integrate-
     * and fire neurons have exponential decau functions.  
     * 
     * 
     */
    /*
    float thresh=2;         // Neuron threshold
    float tau= .02f;        // Neuron time-constant (s)
    int downsamp=1;         // Downsampling factor
    int polarityPass=0;       // <1: just off events, >1: Just on events, 0: both;
    */
    
    NeuronMap.builtFilt inputFilter=NeuronMap.builtFilt.buffer;
    NeuronMap.builtFilt autoFilter=NeuronMap.builtFilt.none;
    
    short collength;        // To save time: length of a column: determined by downsamp
    
    float autoStrength=1;
    
    NeuronMap NM;           // Alias of superclass field NM.  To make the compiler happy.
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> P)  
    {
        if (!filterEnabled) return P;
        
        if (NM==null) resetFilter();
        
        //System.out.println(P.getFirstEvent().timestamp);
        
        OutputEventIterator outItr=out.outputIterator();
        try
        {
            out.setEventClass(TypedEvent.class);
            for (Object p:P)
            { // iterate over the input packet**
                
                PolarityEvent e=(PolarityEvent) p;// P.getEvent(i);
                                
                float vv=1;
                switch (polarityPass)
                {   case on: 
                        if (e.polarity==Polarity.Off) continue;
                        break;
                    case off:
                        if (e.polarity==Polarity.On) continue;
                        break;
                    case diff:
                        if (e.polarity==Polarity.Off) vv=-1;
                }
                NM.inputSig(vv,(short)(e.x),(short)(e.y),e.timestamp,outItr);
                /*
                if ((polarityPass==0) || (polarityPass<0 && (e.polarity==e.polarity.Off)) || (polarityPass>0 && (e.polarity==e.polarity.On)))
                {   
                    //int ix=xy2ind(e.x,e.y);
                    NM.inputSpike((short)(e.x/downsamp),(short)(e.y/downsamp),1,outItr);

                }*/
                
            }
            return out;
        }
        catch(Exception ME)
        {
            setFilterEnabled(false);
            ME.printStackTrace();
            //System.out.println();
            return out;
        }
        
    }

        /*
    public int xy2ind(short x, short y)
    {   // Given input coordiates x,y, return the index
        
        return (x)*collength+y;
    }
    
    class coord
    {   short x;
        short y;
    }
    
    private coord ind2xy(int ix)
    {
        coord c=new coord();
        c.x=(short) Math.floor(ix/collength);
        c.y=(short)(ix%collength);
        return c;
    }
           */
            
    //==========================================================================
    // Initialization, UI
    
    public NeuronMapFilter(AEChip  chip)
    {   super(chip);
                /*
        setPropertyTooltip("downsamp", "Downsampling factor");
        setPropertyTooltip("polarityPass", "<1 for OFF, >1 for ON, 0 for both");
        setPropertyTooltip("filterChoice", "Which filter to use?");*/
        setPropertyTooltip("Mapping","inputFilter","Input Filter to use");
        setPropertyTooltip("Mapping","autoFilter","Auto Filter to use");
        setPropertyTooltip("Mapping","autoStrength","Modulate Strength of Auto-Connections");
        
    }
    
    
    @Override
    public void resetFilter() {
        initFilter();
    }

    @Override
    public void initFilter() {
        
        short dimx=(short) Math.ceil((float) chip.getSizeX());
        short dimy=(short) Math.ceil((float) chip.getSizeY());
        
        float wmag=1;        
        
        collength=dimy;
        NM=new NeuronMap();
        NM.setInputFilter(NeuronMap.builtFilt.buffer);
        NM.build(dimx, dimy);
        NM.maxdepth=20;
        NM.setAllOutputStates(true);
        NM.setTaus(tau);
        NM.setThresholds(thresh);
        
        NN=NM; 
        
        // Define Initial Preferences.
        setInputFilter(NeuronMap.builtFilt.buffer);
        setAutoFilter(NeuronMap.builtFilt.none);
        setAutoStrength(2);
        setTau(.05f);
        setSat(.001f);
        setThresh(1.2f);
        setPolarityPass(polarityOpts.off);
        
    }
    
          /*
    public int getDownsamp() {
        return this.downsamp;
    }
    
    public void setDownsamp(int dt) {
        getPrefs().putFloat("NeurSparse.Downsamp",dt);
        support.firePropertyChange("downsamp",this.downsamp,dt);
        this.downsamp = dt;
        this.resetFilter();
    }   */
    
    /*
    public filter_types getColorToDrawRF() {
        return filterChoice;
    }*/

   
    public void setInputFilter(NeuronMap.builtFilt choice) {
        
        NM.setInputFilter(choice);
        inputFilter=choice;
        getPrefs().put("NeuronMapFilter.inputFilter",choice.toString());
        System.out.println("Input Filter Reset to: "+choice.toString());
    }

    public NeuronMap.builtFilt getInputFilter()
    {   return inputFilter;
    }
        
    public void setAutoFilter(NeuronMap.builtFilt choice) {
        
        NM.setAutoFilter(choice);
        autoFilter=choice;
        getPrefs().put("NeuronMapFilter.autoFilter",choice.toString());
        System.out.println("Auto Filter Reset to: "+choice.toString());
    }

    public NeuronMap.builtFilt getAutoFilter()
    {   return autoFilter;
    }
        
    public void setAutoStrength(float v) {
        this.autoStrength=v;
        NM.autoStrength=v;
        getPrefs().putFloat("NeuronMapFilter.autoStrength",v);
        support.firePropertyChange("autoStrength",this.autoStrength,v);
        
    }
    
    public float getAutoStrength() {
        return this.NM.autoStrength;
    }
}

        