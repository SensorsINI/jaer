/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.awt.Component;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;

import javax.swing.JPanel;

import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.DisplayWriter;

/**
 * This is an equivalent to the filter-chain that can include multi-input filters.
 * 
 * 
 * @author Peter
 */
public class ProcessingNetwork {
    
    int[] executionOrder;
    
    ArrayList<PacketStream> inputStreams=new ArrayList();
    
    ArrayList<Node> nodes=new ArrayList();
            
    /** Compute! */
    public void crunch()
    {   int i=0;
        try {            
            // Mark all nodes as needing to be computed
            for (Node n : nodes) {
                n.ready = false;
            }

            // Compute all nodes
            for (i=0;i<nodes.size();i++) {
                nodes.get(i).process();
            }
            
        } catch (Exception ME) {
            nodes.get(i).setEnabled(false);
            nodes.get(i).filt.setFilterEnabled(false);
            ME.printStackTrace();
        }
    }   
    
    /** Return the node corresponding to the given filter, or null if none found. */
    Node getNodeFromFilter(EventFilter filt)
    {   Node returnNode=null;
        for (Node n:nodes)
        {   if (n.filt==filt)
            {   returnNode=n;
                break;
            }
        }
        
        return returnNode;
    }
    
    public MultiInputPanel getControlPanelFromFilter(EventFilter filt)
    {
        Node n=getNodeFromFilter(filt); 
        
        if (n!=null)
            return n.controlPanel;
        else
        {   return null;
            //throw new RuntimeException("Specified Filter not found!  Could not add controls");
        }
    }
    
    /** Add controls to the control panel of the specified filter */
//    public void addControlsToFilter(JPanel controls,EventFilter filt)
//    {
//        Node n=getNodeFromFilter(filt);
//        
//        if (n!=null)
//            n.addControls(controls);
//        else
//        {   throw new RuntimeException("Specified Filter not found!  Could not add controls");
//            
//        
//        }
//    }
    
    /** Mainly for back-compatibility.  Build the processing network out of a 
     * filter-chain.
     * @param ch 
     */
    public void buildFromFilterChain(FilterChain ch)
    {
        nodes.clear();
        
        for (int i=0; i<ch.size(); i++)
        {   EventFilter2D philly=ch.get(i);
            nodes.add(new Node(philly,i));
        }
    }
    
    /** Set the list of input streams */
    public void setInputStreams(ArrayList<PacketStream> ins)
    {
        inputStreams=ins;
    }
    
    
    class Node implements PacketStream, DisplayWriter
    {   
        int nodeID;
        EventFilter2D filt;
        boolean isMultiInput=false;
        private boolean enabled=false;
        PacketStream[] sources;
        EventPacket outputPacket;
        boolean ready=false;
        MultiInputPanel controlPanel;
        
        public JPanel getControlPanel()
        {
            return controlPanel;
        }
        
        public void setControlPanel(MultiInputPanel cp)
        {   
            controlPanel=cp;
        }
        
        public void addControls(JPanel cont)
        {
            controlPanel.addCustomControls(cont);
        }
        
        public Node(EventFilter2D philt,int id)
        {
            filt=philt;
            
            nodeID=id;
            // This is a sinful use of instanceof, but it's all in the name of 
            // backwards-compatibility
            isMultiInput=philt instanceof MultiSourceProcessor;
            
            sources=new PacketStream[nInputs()];
        }
        
        /** Return a list of possible input sources */
        public ArrayList<PacketStream> getSourceOptions()
        {   
            ArrayList<PacketStream> arr=new ArrayList();
            
            for (PacketStream p:inputStreams)
                arr.add(p);
            
            
            // TODO: modify list to prevent circular dependencies
            for (PacketStream p:nodes)
                if (p!=this)
                    arr.add(p);
            
            return arr;
        }
        
        
        /** Do the processing for this node */
        @Override
        public boolean process() {
            
            if (isReady()) return true;
            else if (!isEnabled())
                return false;            
            
            // Step 1: Prepare all inputs
            for (PacketStream p:sources)
                if (!p.isReady())
                {   
                    boolean status=p.process();
                    
                    // No longer wait for filters to be ready
                    //if (!status) return false;
                }
                
            // Step 2: Compute the output packet.
            if (isMultiInput) {
                ArrayList<EventPacket> inputs=new ArrayList();
                for (PacketStream n :sources)
                    inputs.add(n.getPacket());
                outputPacket=((MultiSourceProcessor)filt).filterPackets(inputs);
            } else {
                outputPacket=filt.filterPacket(sources[0].getPacket());
            }

            return true;
        }

        @Override
        public EventPacket getPacket() {
            return outputPacket;
        }
        
        
        /** Set the input source at the given index */
        public void setSource(int sourceNumber,PacketStream src) throws Exception
        {   
//            if (sourceNumber>nInputs())
//                throw new Exception("Warning: You set a source number higher than the allowable limit.  Nothing is being done.");
            
//            sources.set(sourceNumber,src);
            sources[sourceNumber]=src;
        }

        @Override
        public void setSemaphore(CyclicBarrier barr) {
        }

        @Override
        public String getName() {
            String name= filt.getClass().getName();
            return "N"+nodeID+": "+name.substring(name.lastIndexOf('.') + 1);
        }
        
        public int nInputs()
        {
            if (filt instanceof MultiSourceProcessor)
            {   return ((MultiSourceProcessor)filt).nInputs();
            }
            else
            {   return 1;
            }
        }
        
        /** Get the names of the inputs */
        public String[] getInputNames()
        {
            if (filt instanceof MultiSourceProcessor)
            {   return ((MultiSourceProcessor)filt).getInputNames();
            }
            else
            {   return new String[] {"input"};
            }
            
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        /**
         * @return the enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled the enabled to set
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void setPanel(JPanel imagePanel) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Component getPanel() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

//        @Override
//        public void display() {
//            throw new UnsupportedOperationException("Not supported yet.");
//        }

        @Override
        public void setDisplayEnabled(boolean state) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }
    
    /** 
     * Get the order in which to execute the filters such that all dependent 
     * filters have been run before the filter which requires them.
     * @TODO: implement
     */
    int[] defineExecutionOrder()
    {
        executionOrder=new int[nodes.size()];
        
        
        
        return executionOrder;
        
    }
    
    /**
     * Filter packets, returning an array containing the output packet for each filter
     * @param packets
     * @return 
     */
//    public ArrayList<EventPacket> filterPackets(ArrayList<EventPacket> packets)
//    {
//        for (int i:executionOrder)
//            nodes.get(i).process();
//        
//        ArrayList<EventPacket> outputs=new ArrayList();
//        for (Node n:nodes)
//            outputs.add(n.outputPacket);
//        
//        return outputs;
//        
//    }
    
    
    
}
