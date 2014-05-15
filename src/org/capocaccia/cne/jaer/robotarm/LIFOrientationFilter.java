/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.capocaccia.cne.jaer.robotarm;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsOrientationEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Orientation extractor / labeler using LIF neurons.
 *
 * @author Michael Pfeiffer, Alex Russell
 */
@Description("Computes Orientations via a LIF-Neuron Model")
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
    protected float vleak=getPrefs().getFloat("LIFOrientationFilter.vleak",-70.0f);

   /** Reset potential for LIF Neurons */
    protected float vreset=getPrefs().getFloat("LIFOrientationFilter.vreset",-70.0f);

   /** Firing threshold for LIF Neurons */
    protected float thresh=getPrefs().getFloat("LIFOrientationFilter.thresh",-20.0f);

    /** Scaling of synaptic input weights */
    protected float scalew = getPrefs().getFloat("LIFOrientationFilter.scalew",10.0f);

    /** Inhibition between horizontal and vertical events */
    protected float inhibitw = getPrefs().getFloat("LIFOrientationFilter.inhibitw",20.0f);

    /** Spatial scaling of filter window */
    protected int spatialscale = getPrefs().getInt("LifOrientationFilter.spatialscale",10);

    public LIFOrientationFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();

        setPropertyTooltip("tau","Time constant of LIF neuron");
        setPropertyTooltip("vleak","Leak voltage of LIF neuron");
        setPropertyTooltip("vreset","Reset potential of LIF neuron");
        setPropertyTooltip("thresh","Threshold for LIF neurons");
        setPropertyTooltip("scalew","Scaling of synaptic input weights");
        setPropertyTooltip("spatialscale","Spatial scaling of filter window");
        setPropertyTooltip("inhibitw","Inhibition between horizontal and vertical events");

        w_horiz = make_horizontal_filter(3);
        w_vert = make_vertical_filter(3);
    }

    /** Creates the filter mask for the horizontal filter */
    public float[] make_horizontal_filter(int dim) {
        float w[] = {-1.0f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f, -1.0f, -1.0f, -1.0f};
        return w;
    }

    /** Creates the filter mask for the vertical filter */
    public float[] make_vertical_filter(int dim) {
        float w[] = {-1.0f, 2.0f, -1.0f, -1.0f, 2.0f, -1.0f, -1.0f, 2.0f, -1.0f};
        return w;
    }

    /** Create array of orientation selective neurons */
    private void init_neuron_array(int dim_neurons) {
        horizontal_cells = new LIFNeuron[dim_neurons*dim_neurons];
        vertical_cells = new LIFNeuron[dim_neurons*dim_neurons];
        for (int i=0; i<(dim_neurons*dim_neurons); i++) {
            horizontal_cells[i] = new LIFNeuron(tau, vreset, vreset, thresh);
            horizontal_cells[i].setweights(w_horiz);

            vertical_cells[i] = new LIFNeuron(tau, vreset, vreset, thresh);
            vertical_cells[i].setweights(w_vert);
        }
    }

    /** Filters events */
    @Override
	synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) {
			return in;
		}
        if(enclosedFilter!=null) {
			in=enclosedFilter.filterPacket(in);
		}
        checkOutputPacketEventType(ApsDvsOrientationEvent.class);

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
           short x=(i.x), y=(i.y);


           int idx = 0;
           int local_idx = 0;
           int h_spike = 0;
           int v_spike = 0;

           int offset = ((weight_size+(2*(spatialscale-1)))-1)/2;
           // Update the horizontal and vertical neurons
           for (int nx=0; nx <weight_size; nx++) {
               if ((((x-offset)+(spatialscale*nx)) >= 0) && (((x-offset)+(spatialscale*nx)) < dim_pixels)) {
                for (int ny=0; ny < weight_size; ny++) {
                    if ((((y-offset)+(spatialscale*ny)) >= 0) && (((y-offset)+(spatialscale*ny)) < dim_pixels)) {
                        // Now the exciting part!!
                       idx = ((x-offset)+(spatialscale*nx)) + (((y-offset)+(spatialscale*ny))*dim_pixels);
                       local_idx = nx + (ny*weight_size);

                       h_spike = horizontal_cells[idx].update(scalew*w_horiz[local_idx], ts);

                       if (h_spike==1) {
                        ApsDvsOrientationEvent testi = new ApsDvsOrientationEvent();
                        testi.setX((short)((x-offset)+(spatialscale*nx)));
                        testi.setY((short)((y-offset)+(spatialscale*ny)));
                        testi.setTimestamp((int)ts);
                        testi.orientation=0;
                        BasicEvent o=outItr.nextOutput();
                        o.copyFrom(testi);
                        vertical_cells[idx].update(-inhibitw, ts);
                       }

                       v_spike = vertical_cells[idx].update(scalew*w_vert[local_idx], ts);
                       if (v_spike==1) {
                        ApsDvsOrientationEvent testi = new ApsDvsOrientationEvent();
                        testi.setX((short)((x-offset)+(spatialscale*nx)));
                        testi.setY((short)((y-offset)+(spatialscale*ny)));
                        testi.setTimestamp((int)ts);
                        testi.orientation=2;
                        BasicEvent o=outItr.nextOutput();
                        o.copyFrom(testi);
                        horizontal_cells[idx].update(-inhibitw, ts);
                       }
                    }
               }
            }
          } // End of cycle through receptive field
        }

        return out; // Hope this is correct
   }


    final int DEFAULT_TIMESTAMP=Integer.MIN_VALUE;

    public float getVleak() {
        return vleak;
    }

    synchronized public void setVleak(float vleak) {
        getPrefs().putFloat("LIFOrientationFilter.vleak",vleak);
        getSupport().firePropertyChange("vleak",this.vleak,vleak);

        this.vleak = vleak;
        if (horizontal_cells != null) {
            for (int i=0; i<(dim_pixels*dim_pixels); i++) {
                horizontal_cells[i].setVleak(vleak);
                vertical_cells[i].setVleak(vleak);
            }
        }
    }

    public float getVreset() {
        return vreset;
    }

    synchronized public void setVreset(float vreset) {
        getPrefs().putFloat("LIFOrientationFilter.vreset",vreset);
        getSupport().firePropertyChange("vreset",this.vreset,vreset);

        this.vreset = vreset;
        if (horizontal_cells != null) {
            for (int i=0; i<(dim_pixels*dim_pixels); i++) {
                horizontal_cells[i].setVreset(vreset);
                vertical_cells[i].setVreset(vreset);
            }
        }
    }

    public float getTau() {
        return tau;
    }

    synchronized public void setTau(float tau) {
        getPrefs().putFloat("LIFOrientationFilter.tau",tau);
        getSupport().firePropertyChange("tau",this.tau,tau);

        this.tau = tau;
        if (horizontal_cells != null) {
            for (int i=0; i<(dim_pixels*dim_pixels); i++) {
                horizontal_cells[i].setTau(tau);
                vertical_cells[i].setTau(tau);
            }
        }
   }

    public float getThresh() {
        return thresh;
    }

    synchronized public void setThresh(float thresh) {
        getPrefs().putFloat("LIFOrientationFilter.thresh",thresh);
        getSupport().firePropertyChange("thresh",this.thresh,thresh);

        this.thresh = thresh;
        if (horizontal_cells != null) {
            for (int i=0; i<(dim_pixels*dim_pixels); i++) {
                horizontal_cells[i].setThresh(thresh);
                vertical_cells[i].setThresh(thresh);
            }
        }
    }

    public float getScalew() {
        return this.scalew;
    }

    public void setScalew(float scalew) {
        getPrefs().putFloat("LIFOrientationFilter.scalew",scalew);
        getSupport().firePropertyChange("scalew",this.scalew,scalew);

        this.scalew = scalew;
    }

    public int getSpatialscale() {
        return spatialscale;
    }

    public void setSpatialscale(int spatialscale) {
        this.spatialscale = spatialscale;
       getPrefs().putFloat("LIFOrientationFilter.spatialscale",spatialscale);
        getSupport().firePropertyChange("spatialscale",this.spatialscale,spatialscale);
    }

    public float getInhibitw() {
        return inhibitw;
    }

    public void setInhibitw(float inhibitw) {
        this.inhibitw = inhibitw;
       getPrefs().putFloat("LIFOrientationFilter.inhibitw",inhibitw);
        getSupport().firePropertyChange("inhibitw",this.inhibitw,inhibitw);
    }



    @Override
	public void initFilter() {
        w_horiz = make_horizontal_filter(3);
        w_vert = make_vertical_filter(3);

        init_neuron_array(dim_pixels);
    }


    /** Reset filter */
    @Override
	synchronized public void resetFilter() {
        if (horizontal_cells != null) {
            for (int i=0; i<(dim_pixels*dim_pixels); i++) {
                horizontal_cells[i].reset_neuron();
                vertical_cells[i].reset_neuron();
            }
        }

    }

    public Object getFilterState() {
        return null;
    }

    @Override
	synchronized public void update(Observable o, Object arg) {
//        if(!isFilterEnabled()) return;
        initFilter();
        resetFilter();
    }

}
