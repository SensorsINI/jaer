/*
 * A spiking neuron filter for detecting lines in an image.
 */

package org.capocaccia.cne.jaer.robotarm;
import java.awt.Graphics2D;
import java.util.Observable;
import java.util.Observer;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OrientationEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *  Extracts lines in Manhatten directions using LIF neurons along rows / columns.
 * 
 * @author Michael Pfeiffer, Alex Russell
 */
@Description("Detects parallel lines via a LIF-Neuron Model")
public class LIFLineFilter extends EventFilter2D implements Observer, FrameAnnotater {
    public final int dim_pixels=128;   // Dimensionality of the pixel array

    private LIFNeuron[] horizontal_cells=null;   // Orientation selective neurons (horizontal)
    private LIFNeuron[] vertical_cells=null;     // Orientation selective neurons (vertical)

    /** Time constant for LIF Neurons */
    protected float tau=getPrefs().getFloat("LIFLineFilter.tau",0.005f);

   /** Leak potential for LIF Neurons */
    protected float vleak=getPrefs().getFloat("LIFLineFilter.vleak",-70.0f);

   /** Reset potential for LIF Neurons */
    protected float vreset=getPrefs().getFloat("LIFLineFilter.vreset",-70.0f);

   /** Firing threshold for LIF Neurons */
    protected float thresh=getPrefs().getFloat("LIFLineFilter.thresh",-20.0f);

    /** Scaling of synaptic input weights */
    protected float scalew = getPrefs().getFloat("LIFLineFilter.scalew",0.1f);

    /** Size of receptive field */
    protected int recfieldsize = getPrefs().getInt("LIFLineFilter.recfieldsize",3);

    /** Last horizontal line detected */
    private int lastHorizLine;

    /** Last vertical line detected */
    private int lastVertLine;

    public LIFLineFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();

        setPropertyTooltip("tau","Time constant of LIF neuron");
        setPropertyTooltip("vleak","Leak voltage of LIF neuron");
        setPropertyTooltip("vreset","Reset potential of LIF neuron");
        setPropertyTooltip("thresh","Threshold for LIF neurons");
        setPropertyTooltip("scalew","Scaling of synaptic input weights");
        setPropertyTooltip("recfieldsize","Size of receptive field");


    }

    /** Create array of orientation selective neurons */
    private void init_neuron_array() {
        horizontal_cells = new LIFNeuron[dim_pixels];
        vertical_cells = new LIFNeuron[dim_pixels];
        for (int i=0; i<(dim_pixels); i++) {
            horizontal_cells[i] = new LIFNeuron(tau, vreset, vreset, thresh);

            vertical_cells[i] = new LIFNeuron(tau, vreset, vreset, thresh);
        }
    }

    /** Filters events */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(ApsDvsOrientationEvent.class);

        OutputEventIterator outItr=out.outputIterator();

        if (horizontal_cells == null) {
            // Initialize cells
            System.out.println("Not ready yet!");
            initFilter();
        }

        // Cycle through events
        for(Object e:in){
           ApsDvsOrientationEvent i=(ApsDvsOrientationEvent)e;
           float ts=i.timestamp;
           short x=(short)(i.x), y=(short)(i.y);
           byte orientation = i.orientation;

           int h_spike = 0;
           int v_spike = 0;
           int loc_x = 0;
           int loc_y = 0;

           int offset = (int) Math.round((recfieldsize-1)/2.0);
           // Update the horizontal neurons
           if (orientation == 0) {
               // Process only horizontal events
               for (int nx=0; nx <recfieldsize; nx++) {
                   loc_x = x-offset+nx;
                if ((loc_x >= 0) && (loc_x < dim_pixels)) {
                    h_spike = horizontal_cells[loc_x].update(scalew, ts);
                    if (h_spike==1) {
                        ApsDvsOrientationEvent testi = new ApsDvsOrientationEvent();
                        testi.setX((short)(x-offset+nx));
                        testi.setY((short)(y));
                        testi.setTimestamp((int)ts);
                        testi.orientation=0;
                        BasicEvent o=(BasicEvent)outItr.nextOutput();
                        o.copyFrom(testi);
                        lastVertLine = loc_x;
                       }
                    } 
                }  // end for
           } // end if (orientation)
           if (orientation == 2) {
               // Process only vertical events
               for (int ny=0; ny < recfieldsize; ny++) {
                   loc_y = y-offset+ny;
                    if ((loc_y >= 0) && (loc_y < dim_pixels)) {
                        v_spike = vertical_cells[loc_y].update(scalew, ts);
                        if (v_spike==1) {
                        ApsDvsOrientationEvent testi = new ApsDvsOrientationEvent();
                        testi.setX((short)(x));
                        testi.setY((short)(y-offset+ny));
                        testi.setTimestamp((int)ts);
                        testi.orientation=2;
                        BasicEvent o=(BasicEvent)outItr.nextOutput();
                        o.copyFrom(testi);

                        lastHorizLine = loc_y;
                       }
                    }
                } // end for
           }  // end if (orientation)
          } // End of cycle through receptive field
        return out; // Hope this is correct
    }

    final int DEFAULT_TIMESTAMP=Integer.MIN_VALUE;

    public float getVleak() {
        return vleak;
    }

    synchronized public void setVleak(float vleak) {
        getPrefs().putFloat("LIFLineFilter.vleak",vleak);
        getSupport().firePropertyChange("vleak",this.vleak,vleak);

        this.vleak = vleak;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                horizontal_cells[i].setVleak(vleak);
                vertical_cells[i].setVleak(vleak);
            }
        }
    }

    public float getVreset() {
        return vreset;
    }

    synchronized public void setVreset(float vreset) {
        getPrefs().putFloat("LIFLineFilter.vreset",vreset);
        getSupport().firePropertyChange("vreset",this.vreset,vreset);

        this.vreset = vreset;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                horizontal_cells[i].setVreset(vreset);
                vertical_cells[i].setVreset(vreset);
            }
        }
    }

    public float getTau() {
        return tau;
    }

    synchronized public void setTau(float tau) {
        getPrefs().putFloat("LIFLineFilter.tau",tau);
        getSupport().firePropertyChange("tau",this.tau,tau);

        this.tau = tau;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                horizontal_cells[i].setTau(tau);
                vertical_cells[i].setTau(tau);
            }
        }
   }

    public float getThresh() {
        return thresh;
    }

    synchronized public void setThresh(float thresh) {
        getPrefs().putFloat("LIFLineFilter.thresh",thresh);
        getSupport().firePropertyChange("thresh",this.thresh,thresh);

        this.thresh = thresh;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                horizontal_cells[i].setThresh(thresh);
                vertical_cells[i].setThresh(thresh);
            }
        }
    }

    public float getScalew() {
        return this.scalew;
    }

    public void setScalew(float scalew) {
        getPrefs().putFloat("LIFLineFilter.scalew",scalew);
        getSupport().firePropertyChange("scalew",this.scalew,scalew);

        this.scalew = scalew;
    }

    public int getRecfieldsize() {
        return recfieldsize;
    }

    public void setRecfieldsize(int recfieldsize) {
        getPrefs().putFloat("LIFLineFilter.recfieldsize",recfieldsize);
        getSupport().firePropertyChange("recfieldsize",this.recfieldsize,recfieldsize);
        this.recfieldsize = recfieldsize;
    }


    public void initFilter() {

        init_neuron_array();
        resetFilter();
    }


    /** Reset filter */
    synchronized public void resetFilter() {
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                horizontal_cells[i].reset_neuron();
                vertical_cells[i].reset_neuron();
            }
        }

        // Reset to no detected line yet
        lastHorizLine = -1;
        lastVertLine = -1;
    }

    public Object getFilterState() {
        return null;
    }

    synchronized public void update(Observable o, Object arg) {
//        if(!isFilterEnabled()) return;
        initFilter();
        resetFilter();
    }



    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }
    /** JOGL annotation */
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        if ((lastHorizLine >= 0) || (lastVertLine >= 0)) {
            GL2 gl=drawable.getGL().getGL2();
        if (lastHorizLine >= 0) {
            // At least one horizontal line detected
            gl.glPushMatrix();
            gl.glColor3f(1,1,0);
            gl.glLineWidth(3);
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glVertex2d(0, lastHorizLine);
            gl.glVertex2d(dim_pixels, lastHorizLine);
            gl.glEnd();
            gl.glPopMatrix();
        }

        if (lastVertLine >= 0) {
            // At least one vertical line detected
            gl.glPushMatrix();
            gl.glColor3f(0,1,1);
            gl.glLineWidth(3);
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glVertex2d(lastVertLine, 0);
            gl.glVertex2d(lastVertLine, dim_pixels);
            gl.glEnd();
            gl.glPopMatrix();
            }
        }
    }

}
