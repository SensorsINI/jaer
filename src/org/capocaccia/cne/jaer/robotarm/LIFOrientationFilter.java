/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.capocaccia.cne.jaer.robotarm;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.*;

/**
 *
 * @author Michael Pfeiffer, Alex Russell
 */
public class LIFOrientationFilter extends EventFilter2D implements Observer {

    public final int dim_pixels=128;   // Dimensionality of the pixel array
    public final int weight_size=3;  // Size of receptive field

    private float[] w_horiz;    // Input weights for horizontal filters
    private float[] w_vert;     // Input weights for vertical filters

    private LIFNeuron[] horizontal_cells=null;   // Orientation selective neurons (horizontal)
    private LIFNeuron[] vertical_cells=null;     // Orientation selective neurons (vertical)

    /** Time constant for LIF Neurons */
    protected float tau=getPrefs().getFloat("LIFOrientationFilter.tau",0.005f);

   /** Leak potential for LIF Neurons */
    protected float Vleak=getPrefs().getFloat("LIFOrientationFilter.Vleak",-70.0f);

   /** Reset potential for LIF Neurons */
    protected float Vreset=getPrefs().getFloat("LIFOrientationFilter.Vreset",-70.0f);

   /** Firing threshold for LIF Neurons */
    protected float thresh=getPrefs().getFloat("LIFOrientationFilter.thresh",-50.0f);

    /** Scaling of synaptic input weights */
    protected float scale_w = getPrefs().getFloat("LIFOrientationFilter.scale_w",10);

    public LIFOrientationFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();

        setPropertyTooltip("tau","Time constant of LIF neuron");
        setPropertyTooltip("Vleak","Leak voltage of LIF neuron");
        setPropertyTooltip("Vreset","Reset potential of LIF neuron");
        setPropertyTooltip("thresh","Threshold for LIF neurons");
        setPropertyTooltip("scale_w","Scaling of synaptic input weights");

        w_horiz = make_horizontal_filter(3);
        w_vert = make_vertical_filter(3);
    }

    /** Creates the filter mask for the horizontal filter */
    public float[] make_horizontal_filter(int dim) {
        float w[] = {-1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f};
        return w;
    }

    /** Creates the filter mask for the vertical filter */
    public float[] make_vertical_filter(int dim) {
        float w[] = {-1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f};
        return w;
    }

    /** Create array of orientation selective neurons */
    private void init_neuron_array(int dim_neurons) {
        horizontal_cells = new LIFNeuron[dim_neurons*dim_neurons];
        vertical_cells = new LIFNeuron[dim_neurons*dim_neurons];
        for (int i=0; i<(dim_neurons*dim_neurons); i++) {
            horizontal_cells[i] = new LIFNeuron(tau, Vreset, Vreset, thresh);
            horizontal_cells[i].setweights(w_horiz);

            vertical_cells[i] = new LIFNeuron(tau, Vreset, Vreset, thresh);
            vertical_cells[i].setweights(w_vert);
        }
    }

    /** Filters events */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(OrientationEvent.class);

        OutputEventIterator outItr=out.outputIterator();

        if (horizontal_cells == null) {
            // Initialize cells
            System.out.println("Not ready yet!");
            initFilter();
        }
        
        // Cycle through events
        for(Object e:in){
           BasicEvent i=(BasicEvent)e;
           float ts=i.timestamp;
           short x=(short)(i.x), y=(short)(i.y);


           int idx = 0;
           int local_idx = 0;
           int h_spike = 0;
           int v_spike = 0;
           
           int offset = (weight_size-1)/2;
           // Update the horizontal and vertical neurons
           for (int nx=0; nx <weight_size; nx++) {
               if (((x-offset+nx) >= 0) && ((x-offset+nx) < dim_pixels)) {
                for (int ny=0; ny < weight_size; ny++) {
                    if (((y-offset+ny) >= 0) && ((y-offset+ny) < dim_pixels)) {
                        // Now the exciting part!!
                       idx = (x-offset+nx) + (y-offset+ny)*dim_pixels;
                       local_idx = nx + ny*weight_size;

                       h_spike = horizontal_cells[idx].update(scale_w*w_horiz[local_idx], ts);
                       v_spike = vertical_cells[idx].update(scale_w*w_vert[local_idx], ts);
                       
                       if (h_spike==1) {
                        OrientationEvent testi = new OrientationEvent();
                        testi.setX((short)(x-offset+nx));
                        testi.setY((short)(y-offset+ny));
                        testi.setTimestamp((int)ts);
                        testi.orientation=0;
                        BasicEvent o=(BasicEvent)outItr.nextOutput();
                        o.copyFrom(testi);
                        
                       }
                       if (v_spike==1) {
                        OrientationEvent testi = new OrientationEvent();
                        testi.setX((short)(x-offset+nx));
                        testi.setY((short)(y-offset+ny));
                        testi.setTimestamp((int)ts);
                        testi.orientation=2;
                        BasicEvent o=(BasicEvent)outItr.nextOutput();
                        o.copyFrom(testi);
                       }
                    }
               }
            }
          } // End of cycle through receptive field
        }

        return out; // Hope this is correct
   }


    public static String getDescription() {
        return "Computes Orientations via a LIF-Neuron Model";
    }

    final int DEFAULT_TIMESTAMP=Integer.MIN_VALUE;

    public float getVleak() {
        return Vleak;
    }

    synchronized public void setVleak(float Vleak) {
        getPrefs().putDouble("LIFOrientationFilter.Vleak",Vleak);
        support.firePropertyChange("Vleak",this.Vleak,Vleak);

        this.Vleak = Vleak;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels*dim_pixels; i++) {
                horizontal_cells[i].setVleak(Vleak);
                vertical_cells[i].setVleak(Vleak);
            }
        }
    }

    public float getVreset() {
        return Vreset;
    }

    synchronized public void setVreset(float Vreset) {
        getPrefs().putDouble("LIFOrientationFilter.Vreset",Vreset);
        support.firePropertyChange("Vreset",this.Vreset,Vreset);

        this.Vreset = Vreset;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels*dim_pixels; i++) {
                horizontal_cells[i].setVreset(Vreset);
                vertical_cells[i].setVreset(Vreset);
            }
        }
    }

    public float getTau() {
        return tau;
    }

    synchronized public void setTau(float tau) {
        getPrefs().putDouble("LIFOrientationFilter.tau",tau);
        support.firePropertyChange("tau",this.tau,tau);

        this.tau = tau;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels*dim_pixels; i++) {
                horizontal_cells[i].setTau(tau);
                vertical_cells[i].setTau(tau);
            }
        }
   }

    public float getThresh() {
        return thresh;
    }

    synchronized public void setThresh(float thresh) {
        getPrefs().putDouble("LIFOrientationFilter.thresh",thresh);
        support.firePropertyChange("thresh",this.thresh,thresh);

        this.thresh = thresh;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels*dim_pixels; i++) {
                horizontal_cells[i].setThresh(thresh);
                vertical_cells[i].setThresh(thresh);
            }
        }
    }

    public float getScale_w() {
        return scale_w;
    }

    public void setScale_w(float scale_w) {
        getPrefs().putDouble("LIFOrientationFilter.scale_w",scale_w);
        support.firePropertyChange("scale_w",this.scale_w,scale_w);

        this.scale_w = scale_w;
    }



    public void initFilter() {
        w_horiz = make_horizontal_filter(3);
        w_vert = make_vertical_filter(3);

        init_neuron_array(dim_pixels);
    }


    /** Reset filter */
    synchronized public void resetFilter() {
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels*dim_pixels; i++) {
                horizontal_cells[i].reset_neuron();
                vertical_cells[i].reset_neuron();
            }
        }

    }

    public Object getFilterState() {
        return null;
    }

    synchronized public void update(Observable o, Object arg) {
//        if(!isFilterEnabled()) return;
        initFilter();
        resetFilter();
    }

}
