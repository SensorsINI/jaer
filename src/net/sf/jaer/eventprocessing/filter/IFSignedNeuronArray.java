/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.Font;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Random;
import java.util.logging.Logger;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

import com.jogamp.opengl.util.awt.TextRenderer;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/**
 * A 2D array of signed IF neurons with controllable decay and thresholds. Can
 * be used to filter out low firing pixels.
 *
 * @author tobi
 *
 * This is part of jAER
 * <a href="http://jaerproject.net/">jaerproject.net</a>, licensed under the
 * LGPL
 * (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("A 2D array of signed IF neurons with controllable decay and thresholds.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class IFSignedNeuronArray extends EventFilter2D implements FrameAnnotater {

    private static Random random = new Random();
    private Neurons neurons;
    private float tauMs = getFloat("tauMs", 1000); // decay constant us
    private float tauUs = tauMs * 1000;
    private float thresholdOn = getFloat("thresholdOn", 2); // weight of each input spike on synapse
    private float thresholdOff = getFloat("thresholdOff", 2); // weight of each input spike on synapse
    private boolean showStateAtMouse = false;

    public static DevelopmentStatus getDevelopementStatus() {
        return DevelopmentStatus.Beta;
    }

    public IFSignedNeuronArray(AEChip chip) {
        super(chip); // as usual when we're called here we don't have array size yet. Defer memory allocation to running filter.
        setPropertyTooltip("tauMs", "recorery time constant in ms of depressing synapse; recovers full strength with this 1st order time constant; increase to make filtering last longer");
        setPropertyTooltip("thresholdOn", "ON signed threshold");
        setPropertyTooltip("thresholdOff", "OFF signed threshold");
        setPropertyTooltip("showStateAtMouse", "Shows synaptic adaptation state at mouse position (expensive)");
        setPropertyTooltip("clearState", "Clears the synaptic depression state of all synapses");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkNeuronAllocation();
        int k = 0;
        for (BasicEvent e : in) {
            if (!(e instanceof PolarityEvent)) {
                throw new RuntimeException("event type must be PolarityEvent, got event " + e);
            }
            if (!neurons.stimulate((PolarityEvent) e)) {
                e.setFilteredOut(true);
            }
        }
        return in;
    }

    @Override
    synchronized public void resetFilter() {
        if (neurons != null) {
            neurons.initialize(this);
        }
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if ((showStateAtMouse == false) || (neurons == null)) {
            return;
        }
        Point p = chip.getCanvas().getMousePixel();
        if (!chip.getCanvas().wasMousePixelInsideChipBounds()) {
            return;
        }
        neurons.display(drawable, p);
    }

    private void checkNeuronAllocation() {
        if (chip.getNumCells() == 0) {
            return;
        }
        if ((neurons == null) || (neurons.getNumCells() != chip.getNumCells())) {
            neurons = new Neurons(this);
        }
    }

    public class Neurons implements Serializable {

        Neuron[][] cells;
        private int numCells;
        transient private TextRenderer renderer;
        transient private Logger log = Logger.getLogger("DepressingSynapseFilter.Neurons");
        transient IFSignedNeuronArray filter;

        public Neurons(IFSignedNeuronArray filter) {
            this.filter = filter;
            AEChip chip = filter.getChip();
            numCells = chip.getNumCells();
            cells = new Neuron[chip.getSizeX()][chip.getSizeY()];
            // fill array
            for (int i = 0; i < cells.length; i++) {
                for (int j = 0; j < cells[i].length; j++) {
                    cells[i][j] = new Neuron();
                }
            }
        }

        void reset() {
            for (int j = 0; j < cells.length; j++) {
                for (Neuron n : cells[j]) {
                    if (n != null) {
                        n.reset();
                    }
                }
            }
        }

        void initialize(IFSignedNeuronArray filter) {
            for (int j = 0; j < cells.length; j++) {
                for (Neuron n : cells[j]) {
                    if (n != null) {
                        n.initialize(filter);
                    }
                }
            }
        }
        // returns true if incoming spike caused outgoing spike

        boolean stimulate(PolarityEvent e) {
            return cells[e.x][e.y].stimulate(e);
        }

        private int getNumCells() {
            return numCells;
        }

        private void display(GLAutoDrawable drawable, Point p) {
            if (renderer == null) {
                renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 10), true, true);
            }
            Neuron n = cells[p.x][p.y];
            renderer.begin3DRendering();
            renderer.setColor(0, 0, 1, 0.8f);
            float vmem = n.getState();
            String s = String.format("%5.3f", vmem);
            Rectangle2D rect = renderer.getBounds(s);
            renderer.draw3D(s, p.x, p.y, 0, .7f); // TODO fix string n lines
            renderer.end3DRendering();
            GL2 gl = drawable.getGL().getGL2();
            gl.glRectf(p.x, p.y - 2, p.x + ((float) rect.getWidth() * vmem * .7f), p.y - 1);

        }


        public class Neuron implements Serializable {
            
            private boolean initialized = false; // flag to init delta time correctly
            private float state = 0; // internal state, clips to 0:1 and the larger the value, the more depressed is the synapse. The probability to transmit is linear in (1-state).
            private int lastt = 0; // time of last spike input
            public boolean onSpike=false, offSpike=false;

            /** returns true if neuron spike caused by input spike at time t in us
             * 
             * @param e input event
             * @return true if neuron fired. Interrogate onSpike and offSpike to find type
             */
            public boolean stimulate(PolarityEvent e) {
                if (!initialized) {
                    lastt = e.timestamp;
                    initialized = true;
                    return true;
                }
                if (e.timestamp < lastt) {
                    reset();
                    return true;
                }
                int dt = e.timestamp - lastt;
                float delta = dt / filter.tauUs;
                float exp = delta > 20 ? 0 : (float) Math.exp(-delta);
                float newstate = (state * exp) + (e.polarity==PolarityEvent.Polarity.On?1:-1);
                  boolean spike = random.nextFloat() > state; // spike goes through based on decayed state
              if (newstate > getThresholdOn()) {
                    newstate = 0;
                    onSpike=true; offSpike=false;
                }else if(newstate<-getThresholdOff()){
                     newstate = 0;
                    onSpike=false; offSpike=true;
                }else{
                    onSpike=false; offSpike=false;
                }
                state = newstate;
                lastt = e.timestamp;
                return onSpike||offSpike;
            }

            void reset() {
                initialized = false;
                state = 0;
                onSpike=false; offSpike=false;;
            }

            void initialize(IFSignedNeuronArray filter) {
                initialized = false;
                Neurons.this.filter = filter; // deserialization BS
            }

            float getState() {
                return state;
            }
        }
    }

    /**
     * @return the tauMs
     */
    public float getTauMs() {
        return tauMs;
    }

    /**
     * @param tau the tau to set
     */
    public void setTauMs(float tau) {
        tauMs = tau;
        putFloat("tauMs", tau);
        tauUs = tauMs * 1000;
    }

    /**
     * @return the weight
     */
    public float getThresholdOn() {
        return thresholdOn;
    }

    /**
     * @param thresholdOn the weight to set
     */
    public void setThresholdOn(float thresholdOn) {
        this.thresholdOn = thresholdOn;
        putFloat("thresholdOn", thresholdOn);
    }

    /**
     * @return the thresholdOff
     */
    public float getThresholdOff() {
        return thresholdOff;
    }

    /**
     * @param thresholdOff the thresholdOff to set
     */
    public void setThresholdOff(float thresholdOff) {
        this.thresholdOff = thresholdOff;
        putFloat("thresholdOff", thresholdOff);
    }

    /**
     * @return the showStateAtMouse
     */
    public boolean isShowStateAtMouse() {
        return showStateAtMouse;
    }

    /**
     * @param showStateAtMouse the showStateAtMouse to set
     */
    public void setShowStateAtMouse(boolean showStateAtMouse) {
        this.showStateAtMouse = showStateAtMouse;
    }

    synchronized public void doClearState() {
        if (neurons != null) {
            neurons.reset();
        }
    }
}
