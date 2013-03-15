/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 * A stack of event-driven layers of event-driven units.
 * 
 * All time-variables are considered to be in milliseconds
 * (This matters when doing real-time I/O).
 *  
 * @author oconnorp
 */
public class Network<AxonType extends Axon> implements Serializable {
    
    // <editor-fold defaultstate="collapsed" desc=" Properties ">
    
//    AxonBundle.AbstractFactory<AxonType> layerFactory;
    Axon.AbstractFactory axonFactory;    
    Unit.AbstractFactory unitFactory;
    
    Thread netThread;
    
    ArrayList<Layer> layers=new ArrayList();
    
    ArrayList<Axon> axons=new ArrayList();
    
    
//    transient Queue<PSP> inputBuffer = new LinkedList();
//    transient Queue<SpikeType> internalBuffer= new LinkedList();
//    transient Queue<SpikeType> internalBuffer= new PriorityBlockingQueue();
    
    // These are now set in "implementQueues"    
    transient LinkedBlockingQueue<PSP> inputBuffer;// = new LinkedBlockingQueue();
    transient PriorityQueue<PSP> internalBuffer;//= new PriorityQueue();
    transient MultiReaderQueue<Spike> outputQueue;//=new MultiReaderQueue();
        
//    public int delay;
    
    public int time=0;    // Current time (millis) (avoids having to pass around time reference)
            
//    boolean enabled=true;
    
    public boolean liveMode=false;     // Live-mode.  If true, it prevents the network from advancing as long as the input buffer is empty
    
    /* True if you'd like to interpret input events as currents coming into the 
     * input layer.  False if you'd like input events to directly cause spikes
     * in the input layer. 
     */
//    public boolean inputCurrents=false;  // sfdafds
    
    
    public int spikecount;
    
    volatile boolean enable=true;
    
//    public float inputCurrentStrength=1; 
    /* If inputCurrents==true, this is the strength with which input events drive
     * input layer units.
     */
        
    transient public NetReader<? extends Network> read;    // An object for I/O
    //transient public NetPlotter plot;   // An object for displaying the state of the network
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Builder Functions ">
    
    
    
    
    public Network (Axon.AbstractFactory axonFac,Unit.AbstractFactory unitFac)
    {   //plot=new NetPlotter(this);
        read=new NetReader(this);
        
        axonFactory=axonFac;
        unitFactory=unitFac;
                
        implementQueues();
        
    };
    
    final void implementQueues()
    {
        inputBuffer = new LinkedBlockingQueue();
        internalBuffer= new PriorityQueue();
        outputQueue=new MultiReaderQueue();
        
    }
    
    
    
    public ArrayList<Layer> getLayers()
    {
        return layers;
    }
    
    public ArrayList<Axon> getAxons()
    {
        return axons;
    }
       
        
    /** Add a layer to the network at the specified index */
    public Layer addLayer(int index,Layer lay)
    {
        // If index is higher than list-size, fill with null elements
        for(int i=layers.size(); i<=index; i++)
            layers.add(null);
        
        layers.set(index,lay);
        
        return layers.get(index);
    }
    
    /** Add a layer to the network at the specified index */
    public Layer addLayer(Layer lay)
    {   addLayer(lay.ixLayer,lay);
        return lay;
    }
    
    /** Add a new layer at the specified index.*/
    public Layer addLayer(int index)
    {   return addLayer(index,new Layer(this, unitFactory, index));
    }
    
    /** Add a new layer, define the number of units.*/
    public Layer addLayer(int index,int nUnits)
    {   Layer layy= addLayer(index);
        layy.initializeUnits(nUnits);
        return layy;
    }
    
    /** Remove the specified layer from the network, along with all associated axons */
    public void removeLayer(Layer layer)
    {
        for (Axon ax:axons)
            if (ax.preLayer==layer || ax.postLayer==layer)
                removeAxon(ax);       
        
        int ix=layers.indexOf(layer);
        
        if (ix!=-1)
            layers.set(ix,null);        
    }
    
    /** Remove the specified axon from the network */
    public void removeAxon(Axon ax)
    {
        axons.remove(ax);
    }
    
    
    
    /** Add a new layer, define the x,y dimensions (and therefore nUnits) */
    public Layer addLayer(int index,int dimx,int dimy)
    {   addLayer(index);        
        lay(index).initializeUnits(dimx,dimy);
        return lay(index);
    }
        
    /** Add a new axon based on the indeces of the pre- and post- layers */
    public AxonType addAxon(int preLayer,int postLayer)
    {   
        if (layers.size()<=preLayer || layers.get(preLayer)==null)
            throw new RuntimeException("Cannot add axon: Layer "+preLayer+" does not exist.");
        
        if (layers.size()<=postLayer || layers.get(postLayer)==null)
            throw new RuntimeException("Cannot add axon: Layer "+postLayer+" does not exist.");
        
        Axon ax=axonFactory.make(lay(preLayer),lay(postLayer));
        axons.add(ax);
        return (AxonType) ax;
    }
    
    
    public AxonType addAxon(Layer preSyn, Layer postSyn)
    {
        // Note, the axon constructor takes care of linking the layers to the axon.
        Axon ax=axonFactory.make(preSyn,postSyn);
        axons.add(ax);
        return (AxonType)ax;
        
    }
    
    public Axon addReverseAxon(Axon fwdAx)
    {
        if (fwdAx.hasReverseAxon() && axons.contains(fwdAx.reverse))
        {   System.out.println("Warning: Axon: '"+fwdAx.reverse.toString()+"' has already been added.  Doing nothing.");
            return fwdAx.getReverseAxon();
        }
        
        Axon ax=fwdAx.getReverseAxon();
        axons.add(ax);
        return ax;
    }
    
    public void addAllReverseAxons()
    {
        ArrayList<Axon> oldAxons=(ArrayList<Axon>)axons.clone();
        
        for (Axon old:oldAxons)
            addReverseAxon(old);
        
        
    }
    
    
    public void rbmify(boolean unRoll)
    {
        addAllReverseAxons();
        
        if (unRoll)
            unrollRBMs();
                
        
    }
    
    
    /** Unroll this network so that just the top two layers are symmetrically
     * connected.  Lower layers are duplicated, with one being up-connected, the
     * other being down-connected.
     */
    public void unrollRBMs()
    {
        int nLay=nLayers();
        for (int i=0; i<nLay; i++)
        {   Layer upLayer=lay(i);
            if (upLayer.nForwardAxons()>0 && upLayer.ax(0).postLayer.nForwardAxons()>0 && upLayer.ax(0).postLayer.ax(0).postLayer.nForwardAxons()==0) // If layer is below a pair of top-level rbms, start a recursive unzipping chain
                copyAndAdopt(upLayer);                
        }
    }
    
    /** Recursive function for copying layers and adopting them to new parent layers */
    public void copyAndAdopt(Layer source)
    {
        int ix=nLayers();
        addLayer(ix);
        
        
        Layer copy=lay(ix);
//
        copy.initializeUnits(source.nUnits());
//        
        for (int i=0; i<source.nUnits(); i++)
        {   copy.units[i]=source.getUnit(i).copy();
        }
        
        Axon revcon=source.ax(0).getReverseAxon();
        
//        AxonBundle revcon=source.ax(0).postLayer.axByLayer(source.ixLayer);
        
        revcon.postLayer=copy;
        
//
//        copy.w=source.w;
//        copy.wLat=source.wLat;
//
//        source.fwdSend=1;
//        source.backSend=0;
//
//        copy.fwdSend=0;
//        copy.backSend=1;
//        
//        copy.postLayer=newParent;
//        newParent.preLayer.add(copy);
//        
//        /* Now, the recursive part, if source-layer has children, copy them and assign the new copy as their parent */     
//        for (Object kid:source.preLayer)
//        {    copyAndAdopt((AxonBundle)kid,copy);
//        }
    }
    
    /** Copy the structure of the network, but leave the state blank */
    public Network copy()
    {
        Network net=this.read.copy();
        
        net.internalBuffer.clear();
        net.inputBuffer.clear();
        net.outputQueue.clear();
        
//        for (int i=0; i<net.nLayers(); i++)
//            net.lay(i).outBuffer.clear();
        
        return net;
    }
    
    /** Create an EvtStack based on an Initializer Object */
    public void buildFromInitializer (Initializer ini)
    {  
        
        // Initial pass, instantiating layers and unit arrays
        for (int i=0; i<ini.layers.size(); i++)
        {   
            addLayer(i);
            
            if (ini.lay(i).nUnits>0 && ini.lay(i).dimx>0 && ini.lay(i).dimx*ini.lay(i).dimy!=ini.lay(i).nUnits)
                throw new RuntimeException("You specified both dimensions and units for layer "+i+", but the product of the dimensions ("+ini.lay(i).dimx*ini.lay(i).dimy+" does not add up to the number of units "+ini.lay(i).nUnits);
            else if(ini.lay(i).dimx>0)
            {    ini.lay(i).nUnits=ini.lay(i).dimx*ini.lay(i).dimy;
                lay(i).dimx=(short)ini.lay(i).dimx;
                lay(i).dimy=(short)ini.lay(i).dimy;
            }                
            
            lay(i).initializeUnits(ini.lay(i).nUnits);
        }
        
        Random rnd=new Random();
                
        // Second pass, wire together layers
        for (Initializer.AxonInitializer ax:ini.axons)
        {
            Axon axon=addAxon(lay(ax.inLayer),lay(ax.outLayer));
            
            // Assign random initial weights based on Gaussian distributions with specified parameters
            if (!Float.isNaN(ax.wMean))
            {   for (int u=0; u<axon.w.length; u++)
                {   //ax.w[u]=new float[ax.postLayer.nUnits()];
                    for (int w=0; w<axon.w[u].length;w++)
                        axon.w[u][w]=(float)(ax.wMean+ax.wStd*rnd.nextGaussian());
                }
            }            
        }
    }
    
    /** Initializer Object - Use this to enter properties with which to instantiate EvtStack */
    public static class Initializer
    {   // Class for initializing an evtstack
                
        ArrayList<Initializer.LayerInitializer> layers=new ArrayList();        
        ArrayList<Initializer.AxonInitializer> axons=new ArrayList();
        
        public Initializer.LayerInitializer lay(int n)
        {   
//            if (layers.size()<n)
            for (int i=layers.size(); i<=n; i++)
                layers.add(new Initializer.LayerInitializer());
            return layers.get(n);            
        }
        
        public Initializer.AxonInitializer ax(int preLayer,int postLayer)
        {
            if (preLayer>=layers.size() || postLayer>=layers.size())
                throw new RuntimeException("You're trying to wire an axon to layer "+Math.max(preLayer,postLayer)+", which don't exist yet!  First define the layers.");
            
            // Seach for axon with described signatures
            for (Initializer.AxonInitializer ax:axons)
                if (ax.isConnectedTo(preLayer, postLayer))
                    return ax;
            
            // If axon not found, make a new one..
            Initializer.AxonInitializer ax=new Initializer.AxonInitializer(preLayer,postLayer);
            axons.add(ax);
            return ax;
                    
        }
        
        public static class AxonInitializer
        {            
            public int inLayer;
            public int outLayer;
            
            public float wMean=Float.NaN; // NaN indicates "don't make a weight matrix"
            public float wStd=0;    
            
            public AxonInitializer(int pre,int post)
            {
                inLayer=pre;
                outLayer=post;
            }
            
            public boolean isConnectedTo(int pre, int post)
            {   return (inLayer==pre && outLayer==post);            
            }
        }
                
        public static class LayerInitializer
        {
            public String name;
            public int nUnits;
            public int dimx;
            public int dimy;
            
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Feeding and Eating Events ">
    /** Add an event to the input queue */
    public void addToQueue(PSP ev)            
    {   // TODO: confirm timestamp monotonicity
        inputBuffer.add(ev);
    }    
    
    /** Feed an array of input events to the network and let 'er rip */
    public void feedEventsAndGo(List<? extends PSP> inputEvents)
    {   feedEvents(inputEvents);
        eatEvents();
    }
        
    /** Feed an array of input events to the network and let 'er rip */
    public void feedEventsAndGo(List<PSP> inputEvents,int timeout)
    {   
        feedEvents(inputEvents);
        eatEvents(timeout);
    }
    
    /** Feed an array of events into the network */
    public void feedEvents(List<? extends PSP> inputEvents)
    {   
        for (PSP ev: inputEvents)
            addToQueue(ev);
    }
        
    
    /** Check if the network is ready to run.  This will throw an exception if 
     * it is not.  The point of this method is to quickly detect things like 
     * un-initialized units, etc.
     */
    public void check()
    {
        try
        {
            for (Layer l:layers)
                l.check();

            for (Axon a: axons)
                a.check();
        }
        catch(RuntimeException me)
        {
            JOptionPane.showMessageDialog(null,
            me.getMessage(),
            me.toString(),
            JOptionPane.ERROR_MESSAGE);
            
            throw me;
        }
        
        
        
    }
    
    
    /** Eat up the events in the input queue */
    public void eatEvents()
    {   eatEvents(Integer.MAX_VALUE);        
    }
    
    
    
    /** Does the network have any events to process> */
    public boolean hasEvents()
    {
        return !(inputBuffer.isEmpty() && (liveMode || internalBuffer.isEmpty()));
    }
    
    
//    public Modes whenInputsRunOut=Modes.KEEPGOING;
//    public enum Modes {WAIT,TERMINATE,KEEPGOING};
//    /** Eat up the events in the input queue until some timeout */
//    PSP nextInput;
//    public void eatEvents(int timeout)
//    {   
//        try {
//            
//            // If in liveMode, go til inputBuffer is empty, otherwise go til both buffers are empty (or timeout).
//            while (!(inputBuffer.isEmpty()&&(internalBuffer.isEmpty() || liveMode )) && enable)
//            {   
//                
//                switch (whenInputsRunOut)
//                {   case WAIT:
//                        nextInput=inputBuffer.take();
//                        break;
//                    case TERMINATE:
//                        
//                        
//                            
//                }
//                                
//                
//                // Determine whether to read from input or buffer
//                boolean readInput=!inputBuffer.isEmpty() && (internalBuffer.isEmpty() || inputBuffer.peek().hitTime<internalBuffer.peek().hitTime);
//                
//                int newtime=readInput?inputBuffer.peek().hitTime:internalBuffer.peek().hitTime;
//                
//                // Update current time to time of this event
//                if (newtime<time)
//                {   System.out.println("Input Spike time Decrease detected!  ("+time+"-->"+newtime+")  Resetting network...");
//                    reset();            
//                    break;
//                }
//                
//                if (newtime > timeout)
//                    break;
//                            
//                time=newtime;
//                            
//                PSP psp=readInput?inputBuffer.poll():internalBuffer.poll();
//                            
//                psp.affect(this);
//                
//                spikecount++;
//                
//                // Post Spike-Feed Actions
//                digest();
//                
//                
//                nextEvent=inputBuffer.take();
//                
//                
//            }
//            
//            enable=true;  // Re-enable network when done.
//            
//            
//        } catch (InterruptedException ex) {
//            Logger.getLogger(Network.class.getName()).log(Level.SEVERE, null, ex);
//        }
//    }
    
    PSP nextInput;
    
    /** This method feeds events into the network.  It waits for the input queue. */
    public Thread startEventFeast()
    {
        if (isRunning())
            throw new RuntimeException("Can't start new network simulation: the old one has not been killed yet!");
        
        
        netThread=new Thread()
        {
            @Override
            public void run()
            {
                        
                try {

                    if (nextInput==null)
                        nextInput=inputBuffer.take();

                    // If in liveMode, go til inputBuffer is empty, otherwise go til both buffers are empty (or timeout).
                    while (true)
                    {   
                                      

                        boolean readInput=internalBuffer.isEmpty() || (nextInput.hitTime<internalBuffer.peek().hitTime);

                        PSP psp=readInput?nextInput:internalBuffer.poll();              

                        // Update current time to time of this event
                        if (psp.hitTime<time)
                        {   System.out.println("Input Spike time Decrease detected!  ("+time+"-->"+psp.hitTime+")  Resetting network...");
                            reset();            
//                            break;
                        }

                        // Process the spike
                        time=psp.hitTime;
                        psp.affect(Network.this);
                        digest(); // Post Spike-Feed Actions
                        spikecount++;

                        if (!enable)
                            break;  
                        
                        // Get next input, waiting if necessary
                        if (readInput)
                            nextInput=inputBuffer.take();

                    }
                    enable=true;  // Re-enable network when done.

                } catch (InterruptedException ex) {
                    
                }
                finally
                {   System.out.println("Network killed at timestamp "+time);
                    synchronized(Network.this)
                    {
                        Network.this.notify();
                    }
                }
            }
            
            
            
        };
        
        netThread.start();
        
        netThread.setName("JSpikeStack Network");
        
        return netThread;
    }
    
    boolean isRunning()
    {
        return netThread!=null && netThread.isAlive();
    }
    
    /** Kill the simulation thread, wait for it to stop */
    public void kill()
    {
        if(!isRunning())
            return;
        
        synchronized(this) // Wait for network thread to be killed
        {   enable=false;
            netThread.interrupt();
            try {
                this.wait();
            } catch (InterruptedException ex) {
                Logger.getLogger(Network.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
    }
    
    
        
    /** Eat up the events in the input queue until some timeout */
    public void eatEvents(int timeout)
    {   
        // If in liveMode, go til inputBuffer is empty, otherwise go til both buffers are empty (or timeout).
        while (!(inputBuffer.isEmpty()&&(internalBuffer.isEmpty() || liveMode )) && enable)
        {            
            // Determine whether to read from input or buffer
            boolean readInput=!inputBuffer.isEmpty() && (internalBuffer.isEmpty() || inputBuffer.peek().hitTime<internalBuffer.peek().hitTime);
            
            int newtime=readInput?inputBuffer.peek().hitTime:internalBuffer.peek().hitTime;
            
            // Update current time to time of this event
            if (newtime-time<0)
            {   System.out.println("Input Spike time Decrease detected!  ("+time+"-->"+newtime+")  Resetting network...");
                reset(newtime);            
                break;
            }
            
            if (newtime > timeout)
                break;
                        
            time=newtime;
                        
            PSP psp=readInput?inputBuffer.poll():internalBuffer.poll();
                        
            psp.affect(this);
            
            spikecount++;
            
            // Post Spike-Feed Actions
            digest();
            
        }
                
        enable=true;  // Re-enable network when done.
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
    public Layer lay(int i)
    {   return layers.get(i);        
    }
    
    public AxonType ax(int sourceLayer,int destLayer)
    {        
        return (AxonType) lay(sourceLayer).axByLayer(destLayer);
    }
    
    public Axon rax(int sourceLayer,int destLayer)
    {
        return lay(sourceLayer).axByLayer(destLayer);
    }
    
    public int nLayers()
    {   return layers.size();        
    }
    
    /* Actions to perform after feeding event.  Yours to overwrite */
    public void digest()
    {   
        for (Layer l : layers) {
            l.updateActions();
        }
        
    }
    
    
    
    
//    /** Scale the thresholds of all units. */
//    public void scaleThresholds(float sc)
//    {   for (Layer l:layers)
//            l.scaleThresholds(sc);
//    }
       
    public void reset(int zeroTime)
    {
        nextInput=null;
        inputBuffer.clear();
        internalBuffer.clear();
        time=zeroTime;
        //time=0;
        for (Layer l:layers)
            l.reset();
//        plot.reset();        
        
    }
    
    /** Reset Network */ 
    public void reset()
    {   
        reset(0);
        
    }
    
    @Override
    public String toString()
    {
        return "SpikeStack with "+nLayers()+" layers@"+hashCode();
    }
    
    
//    /** Set strength of forward connections for each layer */
//    public void setForwardStrength(float[] st)
//    {   for (int i=0; i<st.length; i++)
//            lay(i).fwdSend=st[i];
//    }
//    
//    /** Set strength of backward connections for each layer */
//    public void setBackwardStrength(float[] st)
//    {   for (int i=0; i<st.length; i++)
//            lay(i).backSend=st[i];
//    }
    
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
            Logger.getLogger(Network.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(Network.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public void addToOutputQueue(Spike ev)
    {
        outputQueue.add(ev);
    }
    
    public void addToInternalQueue(PSP ev)
    {        
        internalBuffer.add(ev);
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
        void readFromXML(Network net);
        
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
    
//    public interface LayerFactory<NetType extends SpikeStack,LayerType extends AxonBundle> extends Serializable
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
    
    // <editor-fold defaultstate="collapsed" desc=" Controls ">
    
//    public Controls getControls()
//    {
//        return new Controls();
//    }
    
    
//    public class Controls extends Controllable
//    {
//        @Override
//        public String getName() {
//            return "Network Controls";
//        }
        
        
        /** Spike Propagation Delay (milliseconds) */
//        public int getDelay() {
//            return delay;
//        }
//
//        /** Spike Propagation Delay (milliseconds) */
//        public void setDelay(int delay) {
//            SpikeStack.this.delay = delay;
//        }        
        
//        public void doSTOP()
//        {
//            enabled=false;
//        }
                
//    }
    
    // </editor-fold>
}
