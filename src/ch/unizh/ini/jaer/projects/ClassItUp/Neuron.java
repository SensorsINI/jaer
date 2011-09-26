/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */





package ch.unizh.ini.jaer.projects.ClassItUp;

/**
 *
 * @author tobi
 */
public class Neuron {

    private float vmem=0;       // Membrane Potential at previous spike
    private float tlast=-10000; // Time-stamp of previous vmem update
    public float thresh=500;    // Threshold (arb units)
    public float tau=500;        // Time Constant (real ms)

    public String name="";          // Does your neuron have a name?

    //public boolean trackFF=false;            // Whether to track the firing frequency;
    //public float FF;
    //public float nFFtrack;                   // 

    public boolean spike(float w){
        // Get current time, calculate Vmem decay, update for this input
        //float now=System.nanoTime()/1000000;
        
        update();
        vmem=vmem+w;
        

        // Fire a spike if thresh broken, otherwise don't
        if (vmem>thresh){
            vmem=0;
            return true;
        }
        return false;
    }

    private void update(){
        float now=System.nanoTime()/1000000;
        vmem=(float)(vmem*Math.exp((tlast-now)/tau));
        tlast=now;
    }

    public float get_vmem(){
        // Gets the current membrane voltage
        update();
        return vmem;
    }

}
