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
public class SpikeStack<LayerType extends BasicLayer,SpikeType extends Spike> implements Serializable {
    
    // <editor-fold defaultstate="collapsed" desc=" Properties ">
    
    BasicLayer.Factory<LayerType> layerFactory;
    Unit.Factory unitFactory;
    
    ArrayList<LayerType> layers=new ArrayList();
    
    transient Queue<SpikeType> inputBuffer = new LinkedList();
    transient Queue<SpikeType> internalBuffer= new LinkedList();
            
    int delay;
    
    public int time=Integer.MIN_VALUE;    // Current time (millis) (avoids having to pass around time reference)
            
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
        
    transient public NetReader<? extends SpikeStack> read;    // An object for I/O
    transient public NetPlotter plot;   // An object for displaying the state of the network
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Builder Functions ">
    
    public SpikeStack (BasicLayer.Factory<LayerType> layerFac,Unit.Factory unitFac)
    {   plot=new NetPlotter(this);
        read=new NetReader(this);
        
        layerFactory=layerFac;
        unitFactory=unitFac;
        
//        layerClass=layClass;
//        unitClass=unClass;
        
        
    };
        
    /** Add a new layer.*/
    public void addLayer(int index)
    {   
        layers.add((LayerType)layerFactory.make(this, unitFactory, index));
    }
    
    /** Copy the structure of the network, but leave the state blank */
    public SpikeStack copy()
    {
        SpikeStack net=this.read.copy();
        
        net.internalBuffer.clear();
        net.inputBuffer.clear();
        
        for (int i=0; i<net.nLayers(); i++)
            net.lay(i).outBuffer.clear();
        
        return net;
    }
    
    /** Create an EvtStack based on an Initializer Object */
    public SpikeStack (Initializer ini,BasicLayer.Factory layerFac,Unit.Factory unitFac)
    {   this(layerFac,unitFac);
        
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
        {   LayerType li=lay(i);
            
            // Connect output layers
            if (ini.layers[i].targ!=-1)
                li.Lout=layers.get(ini.layers[i].targ);
            
            // Connect input layers
            li.Lin=new ArrayList();
            for (int j=0; j<ini.layers.length; j++)
                if (ini.layers[j].targ==i)
                    li.Lin.add(layers.get(j));
            
            
//            li.wOut=new float[ini.lay(ini.lay(i).targ).nUnits][];
            
            // Initialize Units
            for (int u=0; u<ini.layers[i].nUnits; u++)
            {   //li.units[u]=li.new Unit(u);
                li.units[u]=li.makeNewUnit(u);
                
                // Assign random initial weights based on Gaussian distributions with specified parameters
                if (!Float.isNaN(ini.layers[i].WoutMean))
                {   li.wOut[u]=new float[li.Lout.units.length];
                    for (int w=0; w<li.wOut[u].length;w++)
                        li.wOut[u][w]=(float)(ini.layers[i].WoutMean+ini.layers[i].WoutStd*rnd.nextGaussian());
                }
                
                if (!Float.isNaN(ini.layers[i].WlatMean))
                {   li.wLat[u]=new float[li.units.length];
                    for (int w=0; w<li.wLat[u].length;w++)
                        li.wLat[u][w]=(float)(ini.layers[i].WlatMean+ini.layers[i].WlatStd*rnd.nextGaussian());
                }
            }
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Feeding and Eating Events ">
    
    /** Add an event to the input queue */
    public void addToQueue(SpikeType ev)
    {   // TODO: confirm timestamp monotonicity
        inputBuffer.add(ev);
    }    
    
    /** Feed an array of input events to the network and let 'er rip */
    public void feedEventsAndGo(List<SpikeType> inputEvents)
    {   feedEvents(inputEvents);
        eatEvents();
    }
        
    /** Feed an array of input events to the network and let 'er rip */
    public void feedEventsAndGo(List<SpikeType> inputEvents,double timeout)
    {   
        feedEvents(inputEvents);
        eatEvents(timeout);
    }
    
    /** Feed an array of events into the network */
    public void feedEvents(List<SpikeType> inputEvents)
    {   
        for (SpikeType ev: inputEvents)
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
        
//        plot.followState();
        
        
        
        // If in liveMode, go til inputBuffer is empty, otherwise go til both buffers are empty (or timeout).
        while (!(inputBuffer.isEmpty()&&(internalBuffer.isEmpty() || liveMode )))
        {
                        
            // Determine whether to read from input or buffer
            boolean readInput=!inputBuffer.isEmpty() && (internalBuffer.isEmpty() || inputBuffer.peek().hitTime<internalBuffer.peek().hitTime);
            Spike ev=readInput?inputBuffer.poll():internalBuffer.poll();
            
            // Update current time to time of this event
            if (ev.hitTime<time)
            {   System.out.println("Input Spike time Decrease detected!  Resetting network...");
                reset();                
            }
            
            time=ev.hitTime;
            
            
            
            if (time > timeout)
                break;
            
            try{
            
                // Feed Spike to network
                if (inputCurrents && readInput)     // 1: Input event drives current
                    lay(ev.layer).fireTo(ev.addr,inputCurrentStrength);
                else if (readInput)                 // 2: Input Spike fires unit
                    lay(ev.layer).fireFrom(ev.addr);
                else                                // 3: Internally buffered event propagated
                    lay(ev.layer).propagateFrom(ev.addr);

                // Post Spike-Feed Actions
                postFeed();
            
            }
            catch (java.lang.ArrayIndexOutOfBoundsException ex)
            {   
//                System.out.println("You tried firing an event at address with address "+ev.addr+" to Layer "+ev.layer+", which has just "+lay(ev.layer).nUnits()+" units.");
                throw new java.lang.ArrayIndexOutOfBoundsException("You tried firing an event at address with address "+ev.addr+" to Layer "+ev.layer+", which has just "+lay(ev.layer).nUnits()+" units.");
            }
            
            
        }
    }
    
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Access Methods ">
    
    /** Return a boolean indicating whether the network is built.  Currently, 
     * the network is considered built if it has a non-empty set of layers 
     * @return 
     */
    public boolean isBuilt()
    {   return layers.size()>0;
    }
    
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
        for (BasicLayer l : layers) {
            l.updateActions();
        }
        
    }
    
//    /** Scale the thresholds of all units. */
//    public void scaleThresholds(float sc)
//    {   for (Layer l:layers)
//            l.scaleThresholds(sc);
//    }
    
            
    /** Reset Network */ 
    public void reset()
    {   internalBuffer.clear();
        time=Integer.MIN_VALUE;
        //time=0;
        for (LayerType l:layers)
            l.reset();
        
        plot.reset();
        
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
    
    
    public void addToInternalQueue(SpikeType ev)
    {
        internalBuffer.add(ev);
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
    
//    
//    public static <NetType extends SpikeStack> NetType makeNet()
//    {
//        return (NetType) new SpikeStack();
//    }
//    
    
        
//    public static interface NetFactory<NetworkType> 
//    {   NetworkType factory();        
//    }
    
//    public interface LayerFactory<NetType extends SpikeStack,LayerType extends BasicLayer> extends Serializable
//    {
//        public LayerType make(NetType network, int ix);
//    }
//    
//    public interface UnitFactory<UnitType>
//    {
//        public UnitType make(int index);
//    }
    
//    
//    public interface Layer<NetType extends SpikeStack,UnitType extends SpikeStack.Unit> extends Serializable
//    {
//        
//        public void reset();
//        
//        public Layer create(NetType network,int ix);
//                        
//        /** Fire Currents to this layer... */
//        public void fireTo(float[] inputCurrents);
//        
//        /** Fire current to particular unit */
//        public void fireTo(int index, float current);
//        
//        /** Set some a default value for dimx,dimy based on the number of units. */
//        public void setDefaultDims();
//        
//        /** Get Reverse connection weights */
//        public float[] getBackWeights(int destinationUnit);
//        
//        /** Initialize the array of units */
//        public void initializeUnits(int nUnits);
//        
//        /** Return a new unit with the given index */
//        public UnitType makeNewUnit(int index);
//        
//        /** Scale thresholds of all units */
//        public void scaleThresholds(float sc);
//        
//        public String getUnitName(int index);        
//        
//        public int nUnits();
//
//        public Spike getOutputEvent(int outputBufferLocation);
//        
//    }
    
    
//    public static interface Unit<LayerType extends SpikeStack.Layer> extends Serializable
//    {
//        Unit create(int index);
//
//        /* Send a current to this unit.  If it spikes it will add it to the buffer */
//        public void fireTo();
//
//        /** Fire a neuron and add the spike to the internal buffer */
//        public void fireFrom();
//
//        /** Get the name of this particular unit */
//        public String getName();
//
//        /** Set the output Weight vector */
//        public void setWout(float[] wvec);
//
//        /** Updates the membrane voltage given an input current */
//        public void updateMem(float current);
//
//        /** Boolean.. determines whether to spike */
//        public boolean doSpike();
//
//        /** Reset the unit to a baseline state */
//        public void reset();
//
//    }
    
    
    
    
    // </editor-fold>
}
