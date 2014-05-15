/*
 * A spiking neuron filter for detecting the median of two parallel lines in an image.
 */

package org.capocaccia.cne.jaer.robotarm;
import java.awt.Graphics2D;
import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsOrientationEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author Michael Pfeiffer, Alex Russell
 */
@Description("A spiking neuron filter for detecting the median of two parallel lines in an image")
public class LIFMedianFilter extends EventFilter2D implements Observer, FrameAnnotater {
    public final int dim_pixels=128;   // Dimensionality of the pixel array

    private LIFNeuron[][][] horizontal_cells=null;   // Orientation selective neurons (horizontal)
    private LIFNeuron[][][] vertical_cells=null;     // Orientation selective neurons (vertical)

    private LIFNeuron[] horizontal_median_cells = null; // Detect median of parallel horizontal lines
    private LIFNeuron[] vertical_median_cells = null; // Detect median of parallel vertical lines

    private int num_rec_cells;    // Number of cells in receptive fields

    /** Time constant for LIF Neurons */
    protected float tau=getPrefs().getFloat("LIFMedianFilter.tau",0.005f);

   /** Leak potential for LIF Neurons */
    protected float vleak=getPrefs().getFloat("LIFMedianFilter.vleak",-70.0f);

   /** Reset potential for LIF Neurons */
    protected float vreset=getPrefs().getFloat("LIFMedianFilter.vreset",-70.0f);

   /** Firing threshold for LIF Neurons */
    protected float thresh=getPrefs().getFloat("LIFMedianFilter.thresh",-20.0f);

    /** Scaling of synaptic input weights */
    protected float scalew = getPrefs().getFloat("LIFMedianFilter.scalew",35.0f);

    /** Size of receptive field */
    protected int recfieldsize = getPrefs().getInt("LIFMedianFilter.recfieldsize",1);

    /** Maximum separation of parallel lines to look for */
    protected int lineseparate = getPrefs().getInt("LIFMedianFilter.lineseparate",20);

    /** Inhibition for events in receptive field */
    protected float recinhibition = getPrefs().getFloat("LIFMedianFilter.recinhibition",70.0f);

    /** Time constant for Median Neurons */
    protected float mediantau=getPrefs().getFloat("LIFMedianFilter.mediantau",0.005f);

   /** Leak potential for Median Neurons */
    protected float medianvleak=getPrefs().getFloat("LIFMedianFilter.medianvleak",-70.0f);

   /** Reset potential for Median Neurons */
    protected float medianvreset=getPrefs().getFloat("LIFMedianFilter.medianvreset",-70.0f);

   /** Firing threshold for Median Neurons */
    protected float medianthresh=getPrefs().getFloat("LIFMedianFilter.medianthresh",-20.0f);

    /** Weight from receptive field to median neuron */
    protected float medianweight = getPrefs().getFloat("LIFMedianFilter.medianweight", 10.0f);

    /** Turn robot on or off */
    protected boolean roboton = getPrefs().getBoolean("LIFMedianFilter.roboton", false);

    /** Last detected median point, X-coordinate*/
    private int lastMedianX;

    /** Last detected median point, Y-coordinate*/
    private int lastMedianY;

    /** Vector of indices to quickly map addresses to neuron indices */
    private Vector map_addresses = null;

    private RobotCommunicator robot = null;

    private int counter = 0;
    private int gripcounter = 0;
    private boolean gripping = false;

    public LIFMedianFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();

        final String median="Median", recfield="Receptive Field", singneurons="Single Neurons";
        setPropertyTooltip(singneurons,"tau","Time constant of LIF neuron");
        setPropertyTooltip(singneurons,"vleak","Leak voltage of LIF neuron");
        setPropertyTooltip(singneurons,"vreset","Reset potential of LIF neuron");
        setPropertyTooltip(singneurons,"thresh","Threshold for LIF neurons");
        setPropertyTooltip(singneurons,"scalew","Scaling of synaptic input weights");
        setPropertyTooltip(recfield,"recfieldsize","Size of receptive field");
        setPropertyTooltip(recfield,"lineseparate","Maximum separation of parallel lines to look for");
        setPropertyTooltip(recfield,"recinhibition","Inhibition for events in receptive field");
        setPropertyTooltip(median,"mediantau","Time constant for Median Neurons");
        setPropertyTooltip(median,"medianvleak","Leak potential for Median Neurons");
        setPropertyTooltip(median,"medianvreset","Reset potential for Median Neurons");
        setPropertyTooltip(median,"medianthresh","Firing threshold for Median Neurons");
        setPropertyTooltip(median,"medianweight","Weight from receptive field to median neuron");
        setPropertyTooltip("roboton","Turn robot on or off");

        lastMedianX = -1;
        lastMedianY = -1;

        try {
            robot = new RobotCommunicator();
            robot.start();
            robot.openGripper();
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            robot = null;
        }
    }

    @Override
	public void finalize() {
        if (robot != null) {
			robot.stop();
		}
    }

    /** Create array of orientation selective neurons */
    private void init_neuron_array() {
        map_addresses = new Vector(dim_pixels);
        int total_recsize = (2*recfieldsize) + 1;
        horizontal_cells = new LIFNeuron[dim_pixels][total_recsize][2];
        vertical_cells = new LIFNeuron[dim_pixels][total_recsize][2];
        horizontal_median_cells = new LIFNeuron[dim_pixels];
        vertical_median_cells = new LIFNeuron[dim_pixels];
        for (int i=0; i<(dim_pixels); i++) {
            // Compute address maps
            // First identify median pixels affected by this pixel
            LinkedList medpixels = new LinkedList();
            LinkedList idxpixels = new LinkedList();
            for (int j=0; j<total_recsize; j++) {
                int med_up_pixel = ((i+lineseparate)-recfieldsize)+j;
                int med_down_pixel = (i-lineseparate-recfieldsize)+j;
                if ((med_up_pixel >= 0) && (med_up_pixel < dim_pixels)) {
                    medpixels.add(new Integer(med_up_pixel));
                    int upmostpixel = Math.max(0, med_up_pixel-lineseparate-recfieldsize);
                    int up_idx = i-upmostpixel;
                    Vector idxObj = new Vector(2);
                    idxObj.add(new Integer(up_idx));
                    idxObj.add(new Integer(0));
                    idxpixels.add(idxObj);
                }
                if ((med_down_pixel >= 0) && (med_down_pixel < dim_pixels)) {
                    medpixels.add(new Integer(med_down_pixel));
                    int upmostpixel = Math.max(0, (med_down_pixel+lineseparate)-recfieldsize);
                    int up_idx = i-upmostpixel;
                    Vector idxObj = new Vector(2);
                    idxObj.add(new Integer(up_idx));
                    idxObj.add(new Integer(1));
                    idxpixels.add(idxObj);
                }
            }
            Vector ad_map = new Vector(2);
            ad_map.add(medpixels);
            ad_map.add(idxpixels);
            map_addresses.add(i, ad_map);

            int upper_rec_field[] = upper_rec_idx(i);
            int lower_rec_field[] = lower_rec_idx(i);

            if ((upper_rec_field.length == 0) || (lower_rec_field.length==0)) {
                // Do not allow median cells where one receptive field is fully outside of image
                horizontal_median_cells[i] = null;
                vertical_median_cells[i] = null;

                for (int j=0; j<num_rec_cells; j++) {
                    horizontal_cells[i][j][0] = null;
                    horizontal_cells[i][j][1] = null;
                    vertical_cells[i][j][0] = null;
                    vertical_cells[i][j][1] = null;
                }
            }
            else {
                horizontal_median_cells[i] = new LIFNeuron(mediantau, medianvleak, medianvreset, medianthresh);
                vertical_median_cells[i] = new LIFNeuron(mediantau, medianvleak, medianvreset, medianthresh);
                int upper_size = upper_rec_field.length;
                int lower_size = lower_rec_field.length;
                for (int j=0; j<upper_size; j++) {
                    horizontal_cells[i][j][0] = new LIFNeuron(tau, vreset, vreset, thresh);
                    vertical_cells[i][j][0] = new LIFNeuron(tau, vreset, vreset, thresh);
                }
                for (int j=upper_size; j<num_rec_cells; j++) {
                    horizontal_cells[i][j][0] = null;
                    vertical_cells[i][j][0] = null;
                }
                for (int j=0; j<lower_size; j++) {
                    horizontal_cells[i][j][1] = new LIFNeuron(tau, vreset, vreset, thresh);
                    vertical_cells[i][j][1] = new LIFNeuron(tau, vreset, vreset, thresh);
                }
                for (int j=lower_size; j<num_rec_cells; j++) {
                    horizontal_cells[i][j][1] = null;
                    vertical_cells[i][j][1] = null;
                }
            }
        }
    }

    /** Compute indices for upper receptive field */
    private int[] upper_rec_idx(int y) {
        int upper_point = Math.min(dim_pixels, Math.max(0, y-lineseparate-recfieldsize));
        int lower_point = Math.min(dim_pixels, Math.max(0, (y-lineseparate)+recfieldsize));

        int num_idx = (lower_point-upper_point)+1;

        int idx[] = new int[num_idx];
        for (int i=0; i<num_idx; i++) {
			idx[i] = upper_point+i;
		}

        return idx;
    }

    /** Compute indices for lower receptive field */
    private int[] lower_rec_idx(int y) {
        int upper_point = Math.min(dim_pixels, Math.max(0, (y+lineseparate)-recfieldsize));
        int lower_point = Math.min(dim_pixels, Math.max(0, y+lineseparate+recfieldsize));

        int num_idx = (lower_point-upper_point)+1;

        int idx[] = new int[num_idx];
        for (int i=0; i<num_idx; i++) {
			idx[i] = upper_point+i;
		}

        return idx;
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
           ApsDvsOrientationEvent i=(ApsDvsOrientationEvent)e;
           float ts=i.timestamp;
           short x=(i.x), y=(i.y);
           byte orientation = i.orientation;

           int h_spike = 0;
           int v_spike = 0;



           // Update the horizontal neurons
           if (orientation == 0) {
               // Process only horizontal line events
               Vector ad_map_y = (Vector) map_addresses.get(y);
               LinkedList median_neuron_idx = (LinkedList) ad_map_y.get(0);
               LinkedList idx_array = (LinkedList) ad_map_y.get(1);

               ListIterator med_itr = median_neuron_idx.listIterator();
               ListIterator idx_itr = idx_array.listIterator();

               while (med_itr.hasNext()) {
                int med_y = ((Integer) med_itr.next()).intValue();
                Vector rec_vec = (Vector) idx_itr.next();
                int loc_y = ((Integer) rec_vec.get(0)).intValue();
                int loc_updown = ((Integer) rec_vec.get(1)).intValue();

                // Now update neuron
//                System.out.println(med_y + " : " + loc_y + " / " + loc_updown);
                h_spike = horizontal_cells[med_y][loc_y][loc_updown].update(scalew, ts);
                if (h_spike==1) {
                    // Inhibit other neurons in same receptive field
                    int total_recfield = (2*recfieldsize)+1;
                    for (int j=0; j<total_recfield; j++) {
                        if (horizontal_cells[med_y][j][loc_updown] != null) {
                            horizontal_cells[med_y][j][loc_updown].update(-recinhibition, ts);
                        }
                    }

                    // Send spike to median neuron
                    int h_median_spike = horizontal_median_cells[med_y].update(medianweight,ts);
                    if (h_median_spike==1) {
                        if ((lastMedianX >= 0) && (lastMedianX < dim_pixels) &&
                                (med_y >= 0) && (med_y < dim_pixels)) {
                            ApsDvsOrientationEvent testi = new ApsDvsOrientationEvent();
                            testi.setX((short)(lastMedianX));
                            testi.setY((short)(med_y));
                            testi.setTimestamp((int)ts);
                            testi.orientation=0;
                            BasicEvent o=outItr.nextOutput();
                            o.copyFrom(testi);
                        }
                            lastMedianY = med_y;


                    }
                } // if (h_spike)
               } // end while
            } // end orientation 0
        if (orientation == 2) {
               // Process only vertical line events
               Vector ad_map_x = (Vector) map_addresses.get(x);
               LinkedList median_neuron_idx = (LinkedList) ad_map_x.get(0);
               LinkedList idx_array = (LinkedList) ad_map_x.get(1);

               ListIterator med_itr = median_neuron_idx.listIterator();
               ListIterator idx_itr = idx_array.listIterator();

               while (med_itr.hasNext()) {
                int med_x = ((Integer) med_itr.next()).intValue();
                Vector rec_vec = (Vector) idx_itr.next();
                int loc_x = ((Integer) rec_vec.get(0)).intValue();
                int loc_updown = ((Integer) rec_vec.get(1)).intValue();

                // Now update neuron
                // System.out.println(med_x + " : " + loc_x + " / " + loc_updown);
                v_spike = vertical_cells[med_x][loc_x][loc_updown].update(scalew, ts);
                if (v_spike==1) {
                    // Inhibit other neurons in same receptive field
                    int total_recfield = (2*recfieldsize)+1;
                    for (int j=0; j<total_recfield; j++) {
                        if (vertical_cells[med_x][j][loc_updown] != null) {
                            vertical_cells[med_x][j][loc_updown].update(-recinhibition, ts);
                        }
                    }

                    // Send spike to median neuron
                    int v_median_spike = vertical_median_cells[med_x].update(medianweight,ts);
                    if (v_median_spike==1) {
                        if ((med_x >= 0) && (med_x < dim_pixels) && (lastMedianY >= 0) &&
                             (lastMedianY < dim_pixels)) {
                            ApsDvsOrientationEvent testi = new ApsDvsOrientationEvent();
                            testi.setX((short)(med_x));
                            testi.setY((short)(lastMedianY));
                            testi.setTimestamp((int)ts);
                            testi.orientation=2;
                            BasicEvent o=outItr.nextOutput();
                            o.copyFrom(testi);
                        }
                        lastMedianX = med_x;
                    }
                } // if (v_spike)
               } // end while

        } // end orientation
    } // end cycle through events
    return out; // Hope this is correct
  }


    final int DEFAULT_TIMESTAMP=Integer.MIN_VALUE;

    public float getvleak() {
        return vleak;
    }

    synchronized public void setvleak(float vleak) {
        getPrefs().putFloat("LIFMedianFilter.vleak",vleak);
        getSupport().firePropertyChange("vleak",this.vleak,vleak);

        this.vleak = vleak;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                for (int j=0; j<num_rec_cells; j++) {
                    if (horizontal_cells[i][j][0] != null) {
                        horizontal_cells[i][j][0].setVleak(vleak);
                        vertical_cells[i][j][0].setVleak(vleak);
                    }
                    if (horizontal_cells[i][j][1] != null) {
                        horizontal_cells[i][j][1].setVleak(vleak);
                        vertical_cells[i][j][1].setVleak(vleak);
                    }
                }
            }
        }
    }

    public float getvreset() {
        return vreset;
    }

    synchronized public void setvreset(float vreset) {
        getPrefs().putFloat("LIFMedianFilter.vreset",vreset);
        getSupport().firePropertyChange("vreset",this.vreset,vreset);

        this.vreset = vreset;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                for (int j=0; j<num_rec_cells; j++) {
                    if (horizontal_cells[i][j][0] != null) {
                        horizontal_cells[i][j][0].setVreset(vreset);
                        vertical_cells[i][j][0].setVreset(vreset);
                    }
                    if (horizontal_cells[i][j][1] != null) {
                        horizontal_cells[i][j][1].setVreset(vreset);
                        vertical_cells[i][j][1].setVreset(vreset);
                    }
                }
            }
        }
    }

    public float getTau() {
        return tau;
    }

    synchronized public void setTau(float tau) {
        getPrefs().putFloat("LIFMedianFilter.tau",tau);
        getSupport().firePropertyChange("tau",this.tau,tau);

        this.tau = tau;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                for (int j=0; j<num_rec_cells; j++) {
                    if (horizontal_cells[i][j][0] != null) {
                        horizontal_cells[i][j][0].setTau(tau);
                        vertical_cells[i][j][0].setTau(tau);
                    }
                    if (horizontal_cells[i][j][1] != null) {
                        horizontal_cells[i][j][1].setTau(tau);
                        vertical_cells[i][j][1].setTau(tau);
                    }
                }
            }
        }
   }

    public float getThresh() {
        return thresh;
    }

    synchronized public void setThresh(float thresh) {
        getPrefs().putFloat("LIFMedianFilter.thresh",thresh);
        getSupport().firePropertyChange("thresh",this.thresh,thresh);

        this.thresh = thresh;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                for (int j=0; j<num_rec_cells; j++) {
                    if (horizontal_cells[i][j][0] != null) {
                        horizontal_cells[i][j][0].setThresh(thresh);
                        vertical_cells[i][j][0].setThresh(thresh);
                    }
                    if (horizontal_cells[i][j][1] != null) {
                        vertical_cells[i][j][1].setThresh(thresh);
                        vertical_cells[i][j][1].setThresh(thresh);
                    }
                }
            }
        }
    }

    public float getScalew() {
        return this.scalew;
    }

    synchronized public void setScalew(float scalew) {
        getPrefs().putFloat("LIFMedianFilter.scalew",scalew);
        getSupport().firePropertyChange("scalew",this.scalew,scalew);

        this.scalew = scalew;
    }

    public int getRecfieldsize() {
        return recfieldsize;
    }

    synchronized public void setRecfieldsize(int recfieldsize) {
        getPrefs().putFloat("LIFMedianFilter.recfieldsize",recfieldsize);
        getSupport().firePropertyChange("recfieldsize",this.recfieldsize,recfieldsize);
        this.recfieldsize = recfieldsize;

        // Re-initialize the filter
        initFilter();
    }

    public int getLineseparate() {
        return lineseparate;
    }

    synchronized public void setLineseparate(int lineseparate) {
        getPrefs().putFloat("LIFMedianFilter.lineseparate",lineseparate);
        getSupport().firePropertyChange("lineseparate",this.lineseparate,lineseparate);
        this.lineseparate = lineseparate;

        // Re-initialize the filter
        initFilter();
    }

    public float getMediantau() {
        return mediantau;
    }

    synchronized public void setMediantau(float mediantau) {
        getPrefs().putFloat("LIFMedianFilter.mediantau",mediantau);
        getSupport().firePropertyChange("mediantau",this.mediantau,mediantau);
        this.mediantau = mediantau;

        for (int i=0; i<dim_pixels; i++) {
            if (horizontal_median_cells[i] != null) {
                horizontal_median_cells[i].setTau(mediantau);
                vertical_median_cells[i].setTau(mediantau);
            }
        }
    }

    public float getMedianthresh() {
        return medianthresh;
    }

    synchronized public void setMedianthresh(float medianthresh) {
        getPrefs().putFloat("LIFMedianFilter.medianthresh",medianthresh);
        getSupport().firePropertyChange("medianthresh",this.medianthresh,medianthresh);
        this.medianthresh = medianthresh;

        for (int i=0; i<dim_pixels; i++) {
            if (horizontal_median_cells[i] != null) {
                horizontal_median_cells[i].setThresh(medianthresh);
                vertical_median_cells[i].setThresh(medianthresh);
            }
        }
    }

    public float getMedianvleak() {
        return medianvleak;
    }

    synchronized public void setMedianvleak(float medianvleak) {
        getPrefs().putFloat("LIFMedianFilter.medianvleak",medianvleak);
        getSupport().firePropertyChange("medianvleak",this.medianvleak,medianvleak);
        this.medianvleak = medianvleak;

        for (int i=0; i<dim_pixels; i++) {
            if (horizontal_median_cells[i] != null) {
                horizontal_median_cells[i].setVleak(medianvleak);
                vertical_median_cells[i].setVleak(medianvleak);
            }
        }
    }

    public float getMedianvreset() {
        return medianvreset;
    }

    synchronized public void setMedianvreset(float medianvreset) {
        getPrefs().putFloat("LIFMedianFilter.medianvreset",medianvreset);
        getSupport().firePropertyChange("medianvreset",this.medianvreset,medianvreset);
        this.medianvreset = medianvreset;

        for (int i=0; i<dim_pixels; i++) {
            if (horizontal_median_cells[i] != null) {
                horizontal_median_cells[i].setVreset(medianvreset);
                vertical_median_cells[i].setVreset(medianvreset);
            }
        }
    }

    public float getMedianweight() {
        return medianweight;
    }

    synchronized public void setMedianweight(float medianweight) {
        getPrefs().putFloat("LIFMedianFilter.medianweight",medianweight);
        getSupport().firePropertyChange("medianweight",this.medianweight,medianweight);
        this.medianweight = medianweight;
    }

    public float getRecinhibition() {
        return recinhibition;
    }

    public void setRecinhibition(float recinhibition) {
        getPrefs().putFloat("LIFMedianFilter.recinhibition",recinhibition);
        getSupport().firePropertyChange("recinhibition",this.recinhibition,recinhibition);
        this.recinhibition = recinhibition;
    }

    public boolean isRoboton() {
        return roboton;
    }

    public void setRoboton(boolean roboton) {
        this.roboton = roboton;
        getPrefs().putBoolean("LIFMedianFilter.roboton",roboton);
        getSupport().firePropertyChange("roboton",this.roboton,roboton);
        robot.stopLeftRight();
        if (roboton) {
            robot.openGripper();
        }
        gripcounter = 0;
        counter = 0;
    }




    @Override
	public void initFilter() {

        init_neuron_array();
        resetFilter();
    }


    /** Reset filter */
    @Override
	synchronized public void resetFilter() {
        if (horizontal_median_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                if (horizontal_median_cells[i] != null) {
                    horizontal_median_cells[i].reset_neuron();
                    vertical_median_cells[i].reset_neuron();
                }
            }
        }
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels; i++) {
                for (int j=0; j<num_rec_cells; j++) {
                    if (horizontal_cells[i][j][0] != null) {
                        horizontal_cells[i][j][0].reset_neuron();
                        horizontal_cells[i][j][1].reset_neuron();
                        vertical_cells[i][j][0].reset_neuron();
                        vertical_cells[i][j][1].reset_neuron();
                    }
                }
            }
        }

        // Reset to no detected median
        lastMedianX = -1;
        lastMedianY = -1;

        counter = 0;
        gripcounter = 0;
        gripping = false;
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



    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }
    /** JOGL annotation */
    @Override
	public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) {
			return;
		}
        if ((lastMedianX >= 0) || (lastMedianY >= 0)) {
            GL2 gl=drawable.getGL().getGL2();
            gl.glPushMatrix();
            gl.glColor3f(1,0,1);
            gl.glLineWidth(3);
            gl.glBegin(GL.GL_LINE_LOOP);
            int left = Math.max(0, lastMedianX-2);
            int right = Math.min(127, lastMedianX+2);
            int up = Math.max(0, lastMedianY-2);
            int down = Math.min(127, lastMedianY+2);
            gl.glVertex2d(left, up);
            gl.glVertex2d(right, up);
            gl.glVertex2d(right, down);
            gl.glVertex2d(left, down);
            gl.glEnd();
            gl.glPopMatrix();
            if (roboton && (counter >= 10)) {
                if (lastMedianX < 50) {
                    if (!gripping) {
                        robot.moveRight();
                        gripcounter = 0;
                    }
                }
                else if (lastMedianX > 78) {
                    if (!gripping) {
                        robot.moveLeft();
                        gripcounter = 0;
                    }
                 }
                 else {
                     robot.stopLeftRight();
                     System.out.println(gripcounter);
                     if (gripcounter > 30) {

                         robot.closeGripper();
                         gripping = true;
                         gripcounter = 0;
                     }
                  }
                 counter = 0;
            } else {
                counter++;
                gripcounter++;
                }
            }
        }

}
