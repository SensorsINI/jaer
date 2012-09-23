/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.lang.reflect.Array;

/**
 *
 * @author Peter
 */
public class LayerRC extends Layer
{
    public float lambda=.1f;
    public int updateTime=50000; // Time to update, us.  
    
    float tau=50000; // Time-constant of neurons
    
    float capStrength=.25f;
    
    
    Integrator[] unitArray;
    
    SmoothingAxon updateAxon;
    
    public int lastUpdate=0;
    
    SmoothingAxon ourax;
            
    
    boolean bufferstate=false;
    
    public LayerRC(Network network,int ix)
    {   super(network,null,ix);
    
        
    
    }
        
    @Override
    public void initializeUnits(int nUnits)
    {
        // AHAHAH I tricked you Java! 
        units=(Unit[])Array.newInstance(Unit.class, nUnits);
        unitArray=new Integrator[nUnits];
        
        ourax=new SmoothingAxon();
        ourax.buildKernel();
        
        for (int i=0; i<nUnits; i++)
        {   
            unitArray[i]=new Integrator();
            units[i]=unitArray[i];
        }
    }
    
    /** Return a new unit with the given index */
    @Override
    public Unit makeNewUnit(int index)
    {   //return (UnitType) new SpikeStack.Unit(index);  
        return new Integrator(); // TODO: type safety
    }
    
    
    class SmoothingAxon extends AxonSparse
    {   
        public SmoothingAxon()
        {
            super(LayerRC.this,LayerRC.this,new Axon.Globals());
            
            
                        
        }
        
        public void buildKernel()
        {
            // Build the kernel!
            float[][] kern=new float[3][];
            kern[0]=new float[]{1};
            kern[1]=new float[]{1,-4-lambda,1};
            kern[2]=new float[]{1};
            
            this.defineKernel(kern);
            
        }
        
        
//        public void update()
//        {
//            swapBuffer();
//            
//            for (int i=0; i<nUnits(); i++)
//            {
////                tran=new PSP(net.time,i,ixLayer())
//                for (int j=0; j<targets[i].length; j++)
//                {
//                    unitArray[targets[i][j]].fireToNode(w[i][j]);
//                }
//            }
//        }
        
        
        public float sumOfNodeValues(int nodeindex)
        {
            float sum=0;

            for (int i=0; i<targets[nodeindex].length; i++)
                sum+=unitArray[targets[nodeindex][i]].getNodeState()*w[nodeindex][i];

            return sum;
        }
    
        
        
    }
    
    
    
    
    @Override
    public void fireTo(PSP sp,float[] inputCurrents)
    {   updateIfNecessary();
        super.fireTo(sp,inputCurrents);
    }
    
    
    @Override
    public void fireTo(PSP sp,int[] addresses, float[] inputCurrents)
    {   updateIfNecessary();
        super.fireTo(sp,addresses,inputCurrents);
    }
    
    
    public void updateIfNecessary()
    {
        if (net.time-lastUpdate>updateTime)
        {   // Update.
            
            float changeSpeed=(updateTime*.000001f)/capStrength;
        
            // Step 1: calculate new buffer            
            for (int i=0; i<unitArray.length; i++)
                unitArray[i].setInactiveNodeVal(unitArray[i].getNodeState()+changeSpeed*(unitArray[i].currentVmem()*lambda+ourax.sumOfNodeValues(i)));

            // Step 2: swap!        
            bufferstate=!bufferstate;
            
            lastUpdate=net.time;
            
        }
    }
    
     
    
    
    /** Integrator unit 
     * This double-buffering unit has one node state contains two alternating 
     * node-states     
     */
     class Integrator extends Unit
    {

        float vmem;
        int tlast;
        float node1;
        float node2;
        
        
        
        public void refireNode()
        {
            if (bufferstate)
                node1=currentVmem()*lambda;
            else
                node2=currentVmem()*lambda;
            
        }
        
        @Override
        public int fireTo(PSP psp, float current) {
//            vmem*=Math.exp((tlast-transmisson.hitTime)/tau);
            
            
            
            vmem=currentVmem()+psp.sp.act==1?current:-current;
            tlast=psp.hitTime;
            return 0;
        }
        
        public void setInactiveNodeVal(float val)
        {
            if (bufferstate)
                node1=val;
            else
                node2=val;
            
        }
        
        public float currentVmem()
        {
            return vmem*(float)Math.exp((tlast-net.time)/tau);
        }
        
        public void fireToNode(float curr)
        {
            if (bufferstate)
                node2+=curr;
            else 
                node1+=curr;
        }

        @Override
        public int fireFrom(int time) {
            return 0;
        }

        @Override
        public Unit copy() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public float getNodeState() {
            return bufferstate?node2:node1;
        }
        
//        @Override
        public float getState(int time) {
            return currentVmem()-getNodeState();
        }

        @Override
        public void reset() {
            vmem=0;
        }

        @Override
        public StateTracker getStateTracker() {
            return new StateTracker()
            {

                @Override
                public void updatestate(Spike sp) {}

                @Override
                public float getState(int time) {
                    return Integrator.this.getState(time);
                }

                @Override
                public String getLabel(float min,float max) {
                    return "Range: ["+min+" "+max+"]";
                }

                @Override
                public boolean isZeroCentered() {
                    return true;
                }
            };
        }

        
        
    }
    
    
}
