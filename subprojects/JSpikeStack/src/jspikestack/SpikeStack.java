/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A stack of event-driven layers of event-driven units.
 * 
 * All time-variables are considered to be in milliseconds
 * (This matters when doing real-time I/O).
 *  
 * @author oconnorp
 */
public class SpikeStack<NetType extends SpikeStack,LayerType extends SpikeStack.Layer> implements Serializable {
    
    // <editor-fold defaultstate="collapsed" desc=" Properties ">
    
    ArrayList<LayerType> layers=new ArrayList<LayerType>();
    
    transient Queue<Spike> inputBuffer = new LinkedList<Spike>();
    transient Queue<Spike> internalBuffer= new LinkedList<Spike>();
            
    public double time;    // Current time (millis) (avoids having to pass around time reference)
        
    public float tau;      // Decay rate (seconds)
        
    public float tref=0;   // Abs-Refractory period
        
    public float delay=0;  // 
    
    public boolean liveMode=false;     // Live-mode.  If true, it prevents the network from advancing as long as the input buffer is empty
    
    public boolean inputCurrents=false;  
    /* True if you'd like to interpret input events as currents coming into the 
     * input layer.  False if you'd like input events to directly cause spikes
     * in the input layer. 
     */
    
    public float inputCurrentStrength=1; 
    /* If inputCurrents==true, this is the strength with which input events drive
     * input layer units.
     */
        
    transient public NetReader<NetType> read;    // An object for I/O
    transient public NetPlotter plot;   // An object for displaying the state of the network
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Builder Functions ">
    
    public SpikeStack ()
    {   plot=new NetPlotter(this);
        read=new NetReader(this);
    };
        
    /** Add a new layer.*/
    public void addLayer(int index)
    {   layers.add((LayerType)new SpikeStack.Layer(this,index));
    }
    
    public NetType copy()
    {
        return this.read.copy();
    }
    
    /** Create an EvtStack based on an Initializer Object */
    public SpikeStack (Initializer ini)
    {
        this();
        
//        layers=new Layer[ini.layers.length];
        //layers=new ArrayList<T>();
        
        
        this.tref=ini.tref;
        this.tau=ini.tau;
        
        // Initial pass, instantiating layers and unit arrays
        for (int i=0; i<ini.layers.length; i++)
        {   
            addLayer(i);
            //lay(i).units=new Layer.Unit[ini.layers[i].nUnits];
            lay(i).initializeUnits(ini.layers[i].nUnits);
//            layers[i]=new Layer(i);
//            layers[i].units=new Layer.Unit[ini.layers[i].nUnits];
        }
        
        Random rnd=new Random();
        
        // Second pass, filling in values and linking layers
        for (int i=0; i<layers.size(); i++)
        {   Layer li=lay(i);
            
            // Connect output layers
            if (ini.layers[i].targ!=-1)
                li.Lout=layers.get(ini.layers[i].targ);
            
            // Connect input layers
            li.Lin=new ArrayList();
            for (int j=0; j<ini.layers.length; j++)
                if (ini.layers[j].targ==i)
                    li.Lin.add(layers.get(j));
            
            // Initialize Units
            for (int u=0; u<ini.layers[i].nUnits; u++)
            {   //li.units[u]=li.new Unit(u);
                li.units[u]=li.makeNewUnit(u);
            
                li.units[u].thresh=ini.thresh;
                
                // Assign random initial weights based on Gaussian distributions with specified parameters
                if (!Float.isNaN(ini.layers[i].WoutMean))
                {   li.units[u].Wout=new float[li.Lout.units.length];
                    for (int w=0; w<li.units[u].Wout.length;w++)
                        li.units[u].Wout[w]=(float)(ini.layers[i].WoutMean+ini.layers[i].WoutStd*rnd.nextGaussian());
                }
                
                if (!Float.isNaN(ini.layers[i].WlatMean))
                {   li.units[u].Wlat=new float[li.units.length];
                    for (int w=0; w<li.units[u].Wlat.length;w++)
                        li.units[u].Wlat[w]=(float)(ini.layers[i].WlatMean+ini.layers[i].WlatStd*rnd.nextGaussian());
                }
            }
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Feeding and Eating Events ">
    
    /** Add an event to the input queue */
    public void addToQueue(Spike ev)
    {   // TODO: confirm timestamp monotonicity
        inputBuffer.add(ev);
    }    
    
    /** Feed an array of input events to the network and let 'er rip */
    public void feedEventsAndGo(List<Spike> inputEvents)
    {   feedEvents(inputEvents);
        eatEvents();
    }
        
    /** Feed an array of input events to the network and let 'er rip */
    public void feedEventsAndGo(List<Spike> inputEvents,double timeout)
    {   
        feedEvents(inputEvents);
        eatEvents(timeout);
    }
    
    /** Feed an array of events into the network */
    public void feedEvents(List<Spike> inputEvents)
    {   
        for (Spike ev: inputEvents)
            addToQueue(ev);
    }
        
    
    /** Eat up the events in the input queue */
    public void eatEvents()
    {   eatEvents(Double.POSITIVE_INFINITY);        
    }
    
    /** Eat up the events in the input queue until some timeout */
    public void eatEvents(double timeout)
    {   
//        inputBuffer=inputEvents;
        
        int k=1;
        k++;
        
        plot.followState();
        
        // If in liveMode, go til inputBuffer is empty, otherwise go til both buffers are empty (or timeout).
        while (!(inputBuffer.isEmpty()&&(internalBuffer.isEmpty() || liveMode )))
        {
            
            // Determine whether to read from input or buffer
            boolean readInput=!inputBuffer.isEmpty() && (internalBuffer.isEmpty() || inputBuffer.peek().time<internalBuffer.peek().time);
            Spike ev=readInput?inputBuffer.poll():internalBuffer.poll();
            
            // Update current time to time of this event
            time=ev.time;
            if (time > timeout)
                break;
            
            // Feed Spike to network
            if (inputCurrents && readInput)     // 1: Input event drives current
                lay(ev.layer).units[ev.addr].fireTo(inputCurrentStrength);
            else if (readInput)                 // 2: Input Spike fires unit
                lay(ev.layer).units[ev.addr].fireFrom();
            else                                // 3: Internally buffered event propagated
                lay(ev.layer).units[ev.addr].propagateFrom();
            
            // Post Spike-Feed Actions
            postFeed();
            
        }
    }
    
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Access Methods ">
    
    /** Return the layer from its index */
    public LayerType lay(int i)
    {   return layers.get(i);        
    }
    
    public int nLayers()
    {   return layers.size();        
    }
    
    /* Actions to perform after feeding event.  Yours to overwrite */
    public void postFeed()
    {   
    }
    
    /** Scale the thresholds of all units. */
    public void scaleThresholds(float sc)
    {   for (Layer l:layers)
            l.scaleThresholds(sc);
    }
    
            
    /** Reset Network */ 
    public void reset()
    {   internalBuffer.clear();
        time=Double.NEGATIVE_INFINITY;
        //time=0;
        for (LayerType l:layers)
            l.reset();
        
    }
    
    
    /** Set strength of forward connections for each layer */
    public void setForwardStrength(float[] st)
    {   for (int i=0; i<st.length; i++)
            lay(i).fwdSend=st[i];
    }
    
    /** Set strength of backward connections for each layer */
    public void setBackwardStrength(float[] st)
    {   for (int i=0; i<st.length; i++)
            lay(i).backSend=st[i];
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Internal Classes and Interfaces ">
    
    
    
//    public static class NetFactory<NetType>
//    {   NetType factory();
//    }
    
    public <NetType> NetType instance()
    {   
        try {
            return (NetType) this.getClass().newInstance();
        } catch (InstantiationException ex) {
            Logger.getLogger(SpikeStack.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(SpikeStack.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    
    /** Initializer Object - Use this to enter properties with which to instantiate EvtStack */
    public static class Initializer
    {   // Class for initializing an evtstack
        
        public Initializer(){};
        public Initializer(int nLayers)
        {   layers=new LayerInitializer[nLayers];
            for (int i=0; i<layers.length; i++)
                layers[i]=new LayerInitializer();
        }
        
        float tref=0f;         // Absolute refractory period
        float tau;          // Time-constant (millis)
        float thresh;       // Threshold (currently set uniformly for all untis)
        double delay=0;     // Delay time
        
        LayerInitializer[] layers;
        
        
        
        public LayerInitializer lay(int n)
        {   return layers[n];            
        }
                
        public static class LayerInitializer
        {
            String name;
            int targ=-1;
            int nUnits;
            
            float WoutMean=Float.NaN; // NaN indicates "don't make a weight matrix"
            float WoutStd=0;
            float WlatMean=Float.NaN;
            float WlatStd=0;
        }
    }
    
    /** Interface for plotting the network */
    public interface Plotter
    {           
        public void raster();
        
        public void state();
        
    }
    
    /** Interface fro reading the network */
    public static interface NetworkReader
    {
        void readFromXML(SpikeStack net);
        
    }
    
    
    public static <NetType extends SpikeStack> NetType makeNet()
    {
        return (NetType) new SpikeStack();
    }
    
    
    
    
    
//    public static class StackFac<SpikeStack> implements NetFactory<SpikeStack>
//    {
//        @Override
//        public SpikeStack factory() {
//            
//            SpikeStack st= new SpikeStack();
//            
//        }
//        
//    }
    
    public static interface NetFactory<NetworkType> 
    {   NetworkType factory();        
    }
    
    
    
    /** A Layer of LIF Neurons */
    public static class Layer <NetType extends SpikeStack,LayerType extends SpikeStack.Layer, UnitType extends SpikeStack.Layer.Unit>  implements Serializable 
    {
        NetType net;
        
        int ixLayer;
        UnitType[] units;
        //EvtStack g;     // Pointer to the 'global' stack
        ArrayList<LayerType> Lin=new ArrayList();
        LayerType Lout=null;
        
        public short dimx;     // For display, purposes.
        public short dimy;     // dimx,dimy must multiply to units.length
        
        float fwdSend=1;    // Strength of feedforward connections
        float backSend=0;   // Strength of feedback connenctions
        float latSend=0;    // Strength of lateral connections
        
        transient public ArrayList<Spike> outBuffer=new ArrayList<Spike>();
        
        /** Instantiate Layer with index */
        public Layer(NetType network,int ix)
        {   net=network;
            ixLayer=ix;            
        }
        
        /** Fire Currents to this layer... */
        public void fireTo(float[] inputCurrents)
        {
            for (int i=0; i<units.length; i++)
            {   units[i].fireTo(inputCurrents[i]);
            }
        }

        /** Reset Layer */
        public void reset()
        {   outBuffer.clear();
            for (Unit u:units)
            {   u.tlast=Double.NEGATIVE_INFINITY;
                u.clast=Double.NEGATIVE_INFINITY;

                u.reset();                    
            }
        }
        
        /** Get Reverse connection weights */
        public float[] getBackWeights(int destinationUnit)
        {   // Get reverse connection weights (THERE HAS GOT TO BE A BETTER WAY)
            float[] w=new float[units.length];
            for (int i=0; i<units.length; i++)
                w[i]=units[i].getOutWeight(destinationUnit);
            return w;
        }
        
        /** Initialize the array of units */
        public void initializeUnits(int nUnits)
        {   // AHAHAH I tricked you Java! 
            units=(UnitType[])Array.newInstance(Unit.class, nUnits);
        }
        
        /** Return a new unit with the given index */
        public UnitType makeNewUnit(int index)
        {   return (UnitType) new Unit(index);            
        }
        
        /** Scale thresholds of all units */
        public void scaleThresholds(float sc)
        {   for (Unit u: units)
                u.thresh*=sc;            
        }
        
        /** A LIF Neuron */
        public class Unit implements Serializable
        {
            int ixUnit; 
            boolean resetAfterFire=true;
            float[] Wout;
            float[] Wlat;
            
            float vmem=0;
            float thresh;
            
            double tlast=Double.NEGATIVE_INFINITY;   // Last spike time
            double clast=Double.NEGATIVE_INFINITY;   // Last update time

            public Unit(int index)
            {   ixUnit=index;
            }
                        
            /* Send a current to this unit.  If it spikes it will add it to the buffer */
            public void fireTo(float current)
            {   updateMem(current);
                if (doSpike())
                    fireFrom();
            }
                        
            /** Get the forward weights from a given source.  Note: must be kept consistent with getOutWeight */
            public float[] getForwardWeights()
            {   return Wout;                
            }
            
            /** Get the lateral weights from a given source */
            public float[] getLateralWeights()
            {   return Wlat;                
            }
                        
            /** Over-writable method to get output weight.  Note: Must be kept consistent with getForwardWeights */
            float getOutWeight(int index)
            {   return Wout[index];
            }
            
            /** Fire a neuron and add the spike to the internal buffer */
            public void fireFrom()
            {   
                // Add to output buffer at THIS time
                outBuffer.add(new Spike(ixUnit,net.time,ixLayer));
                     
                // Add to internal buffer at delay time.  (Wasteful repetition?)
                net.internalBuffer.add(new Spike(ixUnit,net.time+net.delay,ixLayer));

                tlast=net.time; 
                
                if (resetAfterFire)
                    reset();
            }
            
            /** Carry out the effects of the firing */
            public void propagateFrom()
            {
                // Fire Ahead!
                if (Lout!=null && fwdSend!=0) 
                    if (fwdSend==1)
                        Lout.fireTo(getForwardWeights());
                    else
                        throw new UnsupportedOperationException("Scaling of fwd connections not yet supported");
                        
                // Fire Behind!
//                if (Lin!=null) 
                for (LayerType l:Lin)
                    if (l.backSend==1)
                        l.fireTo(l.getBackWeights(ixUnit));
                    else if (l.backSend==0)
                        break;
                    else
                        throw new UnsupportedOperationException("Scaling of reverse connections not yet supported");
                
                // Fire Sideways!
                if (Wlat!=null && latSend!=0)
                {   
                    if (latSend==1)
                        Layer.this.fireTo(getLateralWeights()); 
                    else
                        throw new UnsupportedOperationException("Scaling of lateral connections not yet supported");
                
                }               
            }
            
            /** Set the output Weight vector */
            public void setWout(float[] wvec)
            {   Wout=wvec;                
            }
                    
            /** Updates the membrane voltage given an input current */
            public void updateMem(float current){
                if (net.time>tlast+net.tref) // Refractory period
                {   vmem=(float)(vmem*Math.exp((clast-net.time)/net.tau)+current);
                    clast=net.time;
                }
            }
            
            /** Boolean.. determines whether to spike */
            public boolean doSpike(){
                return vmem>thresh;                
            }

            /** Reset the unit to a baseline state */
            public void reset()
            {
                vmem=0;
                
            }

        }
        
        
        
        public int nUnits()
        {   return units.length;
        }

        public Spike getOutputEvent(int outputBufferLocation)
        {   return outBuffer.get(outputBufferLocation);
        }
        
    }
    
    // </editor-fold>
}
