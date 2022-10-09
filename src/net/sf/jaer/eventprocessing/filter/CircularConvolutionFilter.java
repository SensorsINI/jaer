/*
 * CircularConvolutionFilter.java
 *
 * Created on 24.2.2006 Tobi
 *
 */
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Computes circular convolutions by splatting out events and checking receiving
 * pixels to see if they exceed a threshold. A behavioral model of
 * Raphael/Bernabe convolution chip, but limited in that it presently only
 * allows positive binary kernel weights, thus the output events can be
 * triggered by lots of input activity.
 *
 * @author tobi
 */
@Description("Computes circular convolutions by splatting out events and checking receiving pixels to see if they exceed a threshold")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public final class CircularConvolutionFilter extends EventFilter2D implements Observer, FrameAnnotater {

    static final int NUM_INPUT_CELL_TYPES = 1;
    protected boolean useBalancedKernel = getBoolean("useBalancedKernel", false);
    protected float negativeKernelDimMultiple = getFloat("negativeKernelDimMultiple", 2);
    private float negativeWeight = 0;
    private int negativeKernelRadius = 0;
    private int radius = getInt("radius", 3);
    private int width = getInt("width", 1);
    private float tauMs = getFloat("tauMs", 10f);
    private float threshold = getFloat("threshold", 1f);
    protected boolean partialReset = getBoolean("partialReset", false);

    /**
     * the number of cell output types
     */
    public final int NUM_OUTPUT_TYPES = 1; // we make it big so rendering is in color

    /**
     * Creates a new instance of CircularConvolutionFilter
     */
    public CircularConvolutionFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        resetFilter();
        setFilterEnabled(false);
        setPropertyTooltip("useBalancedKernel", "balances kernel to zero sum with positive and negative weights");
        setPropertyTooltip("radius", "radius in pixels of kernal");
        setPropertyTooltip("width", "width in pixels of kernel (for radius > 2)");
        setPropertyTooltip("tauMs", "time constant in ms of integrator neuron potential decay");
        setPropertyTooltip("threshold", "threahold on ms for firing output event from integrating neuron");
        setPropertyTooltip("negativeKernelDimMultiple", "multiple of radius*2 is the field of negative splatts to counterbalance the positive circular kernel");
        setPropertyTooltip("ignorePolarity", "treat all input events as ON events (maybe better for detecting moving circular shapes)");
        setPropertyTooltip("partialReset", "if input results in superthreshold membrane potential Vm, emit quantized number of events and reset Vm by -nspikes*threshold. If disabled, emit at most 1 event and reset membrane to zero (faster)");
//        buildexptable(-10, 0, .01f);// interpolation for exp approximation, up to 3 time tauMs

    }

    @Override
    final synchronized public EventPacket filterPacket(EventPacket in) {
        checkOutputPacketEventType(in);
        int sx = chip.getSizeX() - 1;
        int sy = chip.getSizeY() - 1;
        int n = in.getSize();

        OutputEventIterator oi = out.outputIterator();
        for (Object o : in) {
            if (o == null) {
                continue;
            }
            PolarityEvent e = (PolarityEvent) o;
            if (e.isSpecial() || e.isFilteredOut()) {
                PolarityEvent oe = (PolarityEvent) oi.nextOutput();
                oe.copyFrom(e);
                continue;
            }
            splatt(e, sx, sy, oi);
        }
        return out;
    }

 
    @Override
    synchronized public void resetFilter() {
        allocateMap();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) { // TODO make this a displaylist
        GL2 gl = drawable.getGL().getGL2();
        final int xp = -20, yp = 10; // where to draw it
        final int sz = 1; // how big each one, 1 for actual size
        // draw negative part as big rectangle
        gl.glColor4f(-negativeWeight, 0, 0, 1);
        gl.glRectf(xp + ((-negativeKernelRadius - .5f) * sz),
                yp + ((-negativeKernelRadius - .5f) * sz),
                xp + ((negativeKernelRadius + .5f) * sz),
                yp + ((negativeKernelRadius + .5f) * sz));
        gl.glColor4f(0, 0, 1, .5f);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(xp + ((-negativeKernelRadius - .5f) * sz), yp + ((-negativeKernelRadius - .5f) * sz));
        gl.glVertex2f(xp + ((negativeKernelRadius - .5f) * sz), yp + ((-negativeKernelRadius - .5f) * sz));
        gl.glVertex2f(xp + ((negativeKernelRadius - .5f) * sz), yp + ((negativeKernelRadius - .5f) * sz));
        gl.glVertex2f(xp + ((-negativeKernelRadius - .5f) * sz), yp + ((negativeKernelRadius - .5f) * sz));
        gl.glEnd();

        // draw each positive kernel pixel on top
        for (Splatt s : splatts) {
            if (s.weight > 0) {
                gl.glColor4f(0, s.weight, 0, 1);
                gl.glRectf(xp + ((s.x - .5f) * sz), yp + ((s.y - .5f) * sz), xp + ((s.x + .5f) * sz), yp + ((s.y + .5f) * sz));
            } else {
//                gl.glColor4f(-s.weight, 0, 0,.5f);
            }
        }
    }

    /**
     * @return the useBalancedKernel
     */
    public boolean isUseBalancedKernel() {
        return useBalancedKernel;
    }

    /**
     * @param useBalancedKernel the useBalancedKernel to set
     */
    synchronized public void setUseBalancedKernel(boolean useBalancedKernel) {
        this.useBalancedKernel = useBalancedKernel;
        putBoolean("useBalancedKernel", useBalancedKernel);
        computeSplattLookup();
    }

    /**
     * @return the negativeKernelDimMultiple
     */
    public float getNegativeKernelDimMultiple() {
        return negativeKernelDimMultiple;
    }

    /**
     * @param negativeKernelDimMultiple the negativeKernelDimMultiple to set
     */
    public void setNegativeKernelDimMultiple(float negativeKernelDimMultiple) {
        this.negativeKernelDimMultiple = negativeKernelDimMultiple;
        putFloat("negativeKernelDimMultiple", negativeKernelDimMultiple);
        computeSplattLookup();
    }

    final class Splatt {

        final int x, y;
        float weight;

        Splatt(int x, int y) {
            this.x = x;
            this.y = y;
            this.weight = 1;
        }

        Splatt(int x, int y, float w) {
            this.x = x;
            this.y = y;
            this.weight = w;
        }

        @Override
        public String toString() {
            return "Splatt: " + x + "," + y + "," + String.format("%.1f", weight);
        }
    }

    private Splatt[] splatts;
    
       // splatt out all effects from this event to neighbors
    private void splatt(PolarityEvent e, int sx, int sy, OutputEventIterator oi) {
        final int x = e.x;
        final int y = e.y;
        final int ts = e.timestamp;
        for (final Splatt s : splatts) {
            final int xoff = x + s.x;
            if ((xoff < 0) || (xoff > sx)) {
                continue; //precheck array access
            }
            final int yoff = y + s.y;
            if ((yoff < 0) || (yoff > sy)) {
                continue;
            }

            if (s.weight < 0) {
                // negative weight can only inhibit
                final float vmnew = convolutionVm[xoff][yoff] - s.weight;
                convolutionVm[xoff][yoff] = vmnew;
            } else { // positive weight, first decay Vm, then add event, then check for spikes
                final float dtMs = (ts - convolutionLastEventTime[xoff][yoff]) * 1e-3f;
                if (dtMs < 0) { // nonmonotonic, update time and ignore
                    convolutionLastEventTime[xoff][yoff] = ts;
                    continue; // ignore negative dt
                }
                float vmold = convolutionVm[xoff][yoff];
//                float v = 1-fastexp((float) - dtMs / tauMs);  // Compute exp(-dt/tau) that decays to zero for very old events in NNb
//                vmold = (float) (vmold * v); // (1 - dtMs / tauMs)); // (Math.exp(-dtMs / tauMs)));
                if (dtMs > tauMs) {
                    vmold = 0;
                } else {
                    vmold = (float) (vmold * (1 - dtMs / tauMs)); // (Math.exp(-dtMs / tauMs)));
                }
                final float vm = vmold + s.weight;
                convolutionVm[xoff][yoff] = vm;
                convolutionLastEventTime[xoff][yoff] = ts;
                if (vm > threshold) {
                    if (partialReset) {
                        int nspikes = (int) Math.floor(vm / threshold);
                        convolutionVm[xoff][yoff] = vm - nspikes * threshold;
                        for (int i = 0; i < nspikes; i++) {
                            final PolarityEvent oe = (PolarityEvent) oi.nextOutput();
                            oe.copyFrom(e);
                            oe.x = (short) xoff;
                            oe.y = (short) yoff;
                            oe.polarity = PolarityEvent.Polarity.On;
                        }
                    } else {
                        convolutionVm[xoff][yoff] = 0;
                        final PolarityEvent oe = (PolarityEvent) oi.nextOutput();
                        oe.copyFrom(e);
                        oe.x = (short) xoff;
                        oe.y = (short) yoff;
                        oe.polarity = PolarityEvent.Polarity.On;

                    }
                }
            }
        }
    }

//    // https://gist.github.com/Alrecenk/55be1682fe46cdd89663
//    public static float fastexp(float x) {
//        final int temp = (int) (12102203 * x + 1065353216);
//        return Float.intBitsToFloat(temp) * expadjust[(temp >> 15) & 0xff];
//    }
//
//    static float expadjust[];
//
//    /**
//     * build correction table to improve result in region of interest. If region
//     * of interest is large enough then improves result everywhere
//     */
//    public static void buildexptable(double min, double max, double step) {
//        expadjust = new float[256];
//        int amount[] = new int[256];
//        //calculate what adjustments should have been for values in region
//        for (double x = min; x < max; x += step) {
//            double exp = Math.exp(x);
//            int temp = (int) (12102203 * x + 1065353216);
//            int index = (temp >> 15) & 0xff;
//            double fexp = Float.intBitsToFloat(temp);
//            expadjust[index] += exp / fexp;
//            amount[index]++;
//        }
//        //average them out to get adjustment table
//        for (int k = 0; k < amount.length; k++) {
//            expadjust[k] /= amount[k];
//        }
//    }

    // computes the indices to splatt to from a source event
    // these are octagonal around a point to the neighboring pixels at a certain radius
    // eg, radius=0, impulse kernal=identity kernel
    // radius=1, nearest neighbors in 8 directions
    // radius=2 octagon?
    // TODO include negative weights where there is no kernel circle to end with zero net kernel sum
    synchronized void computeSplattLookup() {
        ArrayList<Splatt> list = new ArrayList<Splatt>();
        double circum = 2 * Math.PI * radius; // num pixels
        int xlast = -1, ylast = -1;
        int n = (int) Math.ceil(circum);
        if (radius < 3) {

            switch (radius) {
                case 0: // identity
                    list.add(new Splatt(0, 0, 1));
                    break;
                case 1:
                    list.add(new Splatt(1, 0, .25f));
                    list.add(new Splatt(0, 1, .25f));
                    list.add(new Splatt(-1, 0, .25f));
                    list.add(new Splatt(0, -1, .25f));
//					if (isUseBalancedKernel()) {
//						list.appendCopy(new Splatt(0, 0, -1));
//					}
                    break;
                case 2:
                    list.add(new Splatt(1, 0));
                    list.add(new Splatt(0, 1));
                    list.add(new Splatt(-1, 0));
                    list.add(new Splatt(0, -1));
                    list.add(new Splatt(1, 1));
                    list.add(new Splatt(-1, 1));
                    list.add(new Splatt(-1, -1));
                    list.add(new Splatt(1, -1));
//					if (isUseBalancedKernel()) {
//						list.appendCopy(new Splatt(0, 0, -8));
//					}
                    float f = 1f / list.size();
                    for (Splatt s : list) {
                        s.weight *= f;
                    }

                    break;
                default:
            }
        } else {
            for (float r = radius - 0.5f * width; r <= radius + .5f * width; r++) {
                circum = 2 * Math.PI * r; // num pixels
                xlast = -1;
                ylast = -1;
                n = (int) Math.ceil(circum);

                for (int i = 0; i < n; i++) {
                    double theta = (2 * Math.PI * i) / circum;
                    double xoff = Math.cos(theta) * r;
                    double yoff = Math.sin(theta) * r;
                    double xround = Math.round(xoff);
                    double yround = Math.round(yoff);
                    if ((xlast != xround) || (ylast != yround)) { // dont make multiple copies of the same splatt around the circle
                        Splatt s = new Splatt((int) xround, (int) yround);
                        s.weight = 1; //(float)(1-Math.sqrt((x-xround)*(x-xround)+(y-yround)*(y-yround)));
                        xlast = s.x;
                        ylast = s.y;
                        list.add(s);
                    }
                }
            }
        }

        float sum = 0;
        for (Splatt s : list) {
            sum += s.weight;
        }
        if (isUseBalancedKernel()) {
            negativeKernelRadius = (int) Math.round(radius * negativeKernelDimMultiple);
            final int numNegWeights = (2 * negativeKernelRadius + 1) * (2 * negativeKernelRadius + 1);
            negativeWeight = -sum / numNegWeights;
            for (int x = -negativeKernelRadius; x <= negativeKernelRadius; x++) {
                for (int y = -negativeKernelRadius; y <= negativeKernelRadius; y++) {
                    list.add(new Splatt(x, y, negativeWeight));
                }
            }
        }
        Object[] oa = list.toArray();
        splatts = new Splatt[oa.length];
        sum = 0;
        for (int i = 0; i < oa.length; i++) {
            splatts[i] = (Splatt) oa[i];
            sum += splatts[i].weight;
        }
        log.info("splatt total positive weight = " + sum + " final total weight = " + sum + " num weights=" + list.size());

    }

    private float[][] convolutionVm;

    private int[][] convolutionLastEventTime;

    private void allocateMap() {
        if ((chip.getSizeX() == 0) || (chip.getSizeY() == 0)) {
            //            log.warning("tried to allocateMap in CircularConvolutionFilter but chip size is 0");
            return;
        }
        //        PADDING=2*radius;
        //        P=radius;
        convolutionVm = new float[chip.getSizeX()][chip.getSizeY()];
        convolutionLastEventTime = new int[chip.getSizeX()][chip.getSizeY()];
        computeSplattLookup();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public void update(Observable o, Object arg) {
        if (!isFilterEnabled()) {
            return;
        }
        initFilter();
    }

    public float getTauMs() {
        return tauMs;
    }

    synchronized public void setTauMs(float tauMs) {
        if (tauMs < 0) {
            tauMs = 0;
        } else if (tauMs > 10000) {
            tauMs = 10000f;
        }
        this.tauMs = tauMs;
        getPrefs().putFloat("CircularConvolutionFilter.tauMs", tauMs);
    }

    public float getThreshold() {
        return threshold;
    }

    synchronized public void setThreshold(float threshold) {
        if (threshold < 0) {
            threshold = 0;
        } else if (threshold > 100) {
            threshold = 100;
        }
        this.threshold = threshold;
        putFloat("threshold", threshold);
    }

    public int getRadius() {
        return radius;
    }

    synchronized public void setRadius(int radius) {
        if (radius < 0) {
            radius = 0;
        } else if (radius > chip.getMaxSize()) {
            radius = chip.getMaxSize();
        }
        if (radius != this.radius) {
            this.radius = radius;
            putInt("radius", radius);
            setWidth(getWidth()); // constrain width
            resetFilter();
        }
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * @param width the width to set
     */
    public void setWidth(int width) {
        if (width < 1) {
            width = 1;
        }
        if (radius < 3) {
            width = 1;
        }
        if (width > radius) {
            width = radius - 1;
        }
        if (width != this.width) {
            int old = this.width;
            this.width = width;
            putInt("width", width);
            getSupport().firePropertyChange("width", old, width);
            resetFilter();
        }
    }

    /**
     * @return the partialReset
     */
    public boolean isPartialReset() {
        return partialReset;
    }

    /**
     * @param partialReset the partialReset to set
     */
    public void setPartialReset(boolean partialReset) {
        this.partialReset = partialReset;
        putBoolean("partialReset", partialReset);
    }

}
