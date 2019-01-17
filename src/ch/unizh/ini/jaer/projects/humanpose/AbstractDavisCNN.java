/* 
 * Copyright (C) 2017 Tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.humanpose;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES3;
import java.awt.Color;
import java.awt.Dimension;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.util.EngineeringFormat;
import org.tensorflow.Tensor;

/**
 * Supertype for CNNs. Subclasses override methods for loading and running the
 * CNN.
 *
 * @author Tobi, Gemma, Enrico
 */
public abstract class AbstractDavisCNN {

    /**
     * Handle to enclosing EventFilter2D that processes the CNN; useful for
     * accessing the fields
     */
    protected AbstractDavisCNNProcessor processor = null;

    /**
     * Height of final output layer histogram as fraction of AEChip display
     * height
     */
    public static final float HISTOGRAM_HEIGHT_FRACTION = 0.9f;
    protected static final Logger log = Logger.getLogger("AbstractDavisCNN");
    protected String netname;
    /**
     * This PropertyChange is emitted when either APS or DVS net outputs. The
     * new value is the network. The old value is null.
     */
    public static final String EVENT_MADE_DECISION = "networkMadeDecision";
    protected String notes;
    protected String dob;
    protected String nettype;
    protected String filename = null;
    protected List<String> labels = null;
    public boolean lastInputTypeProcessedWasApsFrame = false;
    protected long startProcessingTimeNs = 0;
    protected boolean softMaxOutput = false;
    protected boolean zeroPadding = false; // tobi changed to make default so that roshambo just runs out of box
    protected EngineeringFormat engFmt = new EngineeringFormat();
    /**
     * This flag is set true once the network has run once. Some constants are
     * not set until then
     */
    protected boolean networkRanOnce = false; // since some constants are not set until network has run
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);
    protected int operationCounter = 0; // counter for ops during update. MAC should be 2 Op
    protected JFrame activationsFrame = null;
    protected boolean hideConvLayers = true;
    protected boolean hideSubsamplingLayers = true;
    protected JFrame kernelsFrame = null;
    protected boolean printActivations = false;
    protected boolean printWeights = false;
    protected long processingTimeNs;

    public AbstractDavisCNN(AbstractDavisCNNProcessor processor) {
        this.processor = processor;
    }

    /**
     * Process a DVS frame
     *
     * @param subsampler
     * @return the output activations vector
     */
    abstract public float[] processDvsFrame(DvsFramer.DvsFrame subsampler);

    /**
     * Computes the output of the network from an input activationsFrame
     *
     * @param frame the renderer that rendered the APS output
     * @return the vector of output values
     * @see #getActivations
     */
    abstract public float[] processAPSFrame(ApsFrameExtractor frame);

    /**
     * Computes the output of the network from an input for a 2-channel APS-DVS
     * frame
     *
     * @param frame the 2-channel APS-DVS frame
     * @return the vector of output values
     * @see #getActivations
     */
    abstract public Tensor processAPSDVSFrame(APSDVSFrame frame);
    
    /**
     * Computes the output of the network from an input for a 3-channel (APS-DVS
     * - 0 padding)
     * frame
     *
     * @param frame the 2-channel APS-DVS frame
     * @param array the output array containing output 4D tensor 1*90*120*1
     * @see #getActivations*
     */
    abstract public void processAPSDVSFrameArray(APSDVSFrame frame, float[] array);

    /**
     * Computes the output of the network from an input activationsFrame
     *
     * @param frame the renderer that rendered the APS output
     * @param offX x offset of the patch
     * @param offY y offset of the patch
     * @return the vector of output values
     * @see #getActivations
     */
    abstract public float[][][] processInputPatchFrame(AEFrameChipRenderer frame, int offX, int offY);

    abstract public InputLayer getInputLayer();

    abstract public OutputLayer getOutputLayer();

    abstract public void drawActivations();

    /**
     * @return the filename
     */
    public String getFilename() {
        return filename;
    }

    /**
     * @param filename the filename to set
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * Net fires event EVENT_MADE_DECISION when it makes a decision. New value
     * is the net itself.
     *
     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }

    /**
     * @param lastInputTypeProcessedWasApsFrame the
     * lastInputTypeProcessedWasApsFrame to set
     */
    public void setLastInputTypeProcessedWasApsFrame(boolean lastInputTypeProcessedWasApsFrame) {
        this.lastInputTypeProcessedWasApsFrame = lastInputTypeProcessedWasApsFrame;
    }

    /**
     * Return a string representation of the cost of computing the network
     *
     * @return %d operations in %d ns: %s ops/sec
     */
    public String getPerformanceString() {
        return String.format("%s ops in %s s: %s ops/sec", engFmt.format(operationCounter), engFmt.format(processingTimeNs * 1e-9f), engFmt.format(operationCounter / (1e-9f * processingTimeNs)));
    }

    /**
     * @param softMaxOutput the softMaxOutput to set
     */
    public void setSoftMaxOutput(boolean softMaxOutput) {
        this.softMaxOutput = softMaxOutput;
    }

    /**
     * Must be set properly according to the loaded CNN!
     *
     * @param zeroPadding the zeroPadding to set
     */
    public void setZeroPadding(boolean zeroPadding) {
        this.zeroPadding = zeroPadding;
    }

    protected void checkActivationsFrame() {
        if (activationsFrame != null) {
            return;
        }
        String windowName = (filename == null ? "null XML" : filename.substring(filename.lastIndexOf(File.separatorChar) + 1)) + "CNN Activations";
        activationsFrame = new JFrame(windowName);
        activationsFrame.setLayout(new BoxLayout(activationsFrame.getContentPane(), BoxLayout.Y_AXIS));
        activationsFrame.setPreferredSize(new Dimension(600, 600));
    }

    protected void checkKernelsFrame() {
        if (kernelsFrame != null) {
            return;
        }
        kernelsFrame = new JFrame("Kernels: DeepLearnCNNNetwork");
        kernelsFrame.setLayout(new BoxLayout(kernelsFrame.getContentPane(), BoxLayout.Y_AXIS));
        kernelsFrame.setPreferredSize(new Dimension(600, 600));
    }

    /**
     * Close extra graphics windows and dispose of them
     *
     */
    public abstract void cleanup();

    public abstract JFrame drawKernels();

    /**
     * @return the hideConvLayers
     */
    public boolean isHideConvLayers() {
        return hideConvLayers;
    }

    /**
     * @return the hideSubsamplingLayers
     */
    public boolean isHideSubsamplingLayers() {
        return hideSubsamplingLayers;
    }

    /**
     * @return the lastInputTypeProcessedWasApsFrame
     */
    public boolean isLastInputTypeProcessedWasApsFrame() {
        return lastInputTypeProcessedWasApsFrame;
    }

    /**
     * @return the printActivations
     */
    public boolean isPrintActivations() {
        return printActivations;
    }

    /**
     * @return the printWeights
     */
    public boolean isPrintWeights() {
        return printWeights;
    }

    /**
     * @return the softMaxOutput
     */
    public boolean isSoftMaxOutput() {
        return softMaxOutput;
    }

    /**
     * @return the zeroPadding
     */
    public boolean isZeroPadding() {
        return zeroPadding;
    }

    /**
     * Loads the network from a protobuf binary file
     *
     * @param f
     * @throws IOException
     */
    public abstract void loadNetwork(File f) throws IOException;

    /**
     * Loads the output labels
     *
     * @param f the file, usually one label per line
     * @throws IOException
     * @see #labels
     */
    public void loadLabels(File f) throws IOException {
        setLabels(Files.readAllLines(Paths.get(f.getAbsolutePath())));
        log.info("loaded " + getLabels().size() + " labels");
    }

    public abstract void printActivations();

    public void printPerformance() {
        System.out.println("\n\n\n****************************************************\nActivations");
        System.out.println(getPerformanceString());
    }

    public abstract void printWeights();

    /**
     * @param hideConvLayers the hideConvLayers to set
     */
    public void setHideConvLayers(boolean hideConvLayers) {
        this.hideConvLayers = hideConvLayers;
    }

    /**
     * @param hideSubsamplingLayers the hideSubsamplingLayers to set
     */
    public void setHideSubsamplingLayers(boolean hideSubsamplingLayers) {
        this.hideSubsamplingLayers = hideSubsamplingLayers;
    }

    /**
     * @param printActivations the printActivations to set
     */
    public void setPrintActivations(boolean printActivations) {
        this.printActivations = printActivations;
    }

    /**
     * @param printWeights the printWeights to set
     */
    public void setPrintWeights(boolean printWeights) {
        this.printWeights = printWeights;
    }

    public static class APSDVSFrame {

        final int NUM_CHANNELS = 2;
        private final int width;
        private final int height;
        final float[] values;

        public APSDVSFrame(int width, int height) {
            this.width = width;
            this.height = height;
            values = new float[NUM_CHANNELS * width * height];
        }

        private int getIndex(int channel, int x, int y) {
            int idx = x * channel + y * (NUM_CHANNELS * getWidth()) + y;
            if (idx < 0 || idx >= values.length) {
                throw new java.lang.ArrayIndexOutOfBoundsException(String.format("index channel=%, x=%d, y=%d is out of bounds of frame which has channels=%d, width=%d, height=%d", channel, x, y, NUM_CHANNELS, getWidth(), getHeight()));
            }
            return idx;
        }

        /**
         * @return the width
         */
        public int getWidth() {
            return width;
        }

        /**
         * @return the height
         */
        public int getHeight() {
            return height;
        }

        public void setValue(int channel, int x, int y, float v) {
            values[getIndex(channel, x, y)] = v;
        }

        public float getValue(int channel, int x, int y) {
            return values[getIndex(channel, x, y)];
        }
    }

    abstract public class Layer {

        /**
         * Layer index
         */
        int index;

        private boolean visible = true;

        public Layer(int index) {
            this.index = index;
        }

        public void initializeConstants() {
            // override to processAPSFrame constants for layer
        }

        public void drawActivations() {

        }

        abstract public void cleanupGraphics();

        /**
         * @return the visible
         */
        public boolean isVisible() {
            return visible;
        }

        /**
         * Sets whether to drawHistogram this layer.
         *
         * @param visible the visible to set
         */
        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public void printActivations() {
            if (!printActivations) {
                return;
            }
            System.out.println(String.format("Activations of Layer %s", toString()));
        }

        public void printWeights() {
            if (!isPrintWeights()) {
                return;
            }
            System.out.println(String.format("Weights of Layer %s", toString()));
        }

    }

    public interface InputLayer {

        public int getWidth();

        public int getHeight();

        public int getNumChannels();
    }

    public interface OutputLayer {

        public int getNumUnits();

        //public int getMaxActivatedUnit();
        //public void setMaxActivatedUnit(int unit);
        //public float getMaxActivation();
        //public void drawHistogram(GL2 gl, int width, int height, float lineWidth, Color color);

        public float[][] getMaxActAndLocPerMap(); // to return max activation and its pixel position for each map.
        
        public float[] getActivations();
    }

    public static void drawHistogram(GL2 gl, float[][][] activations, int width, int height, float lineWidth, Color color) {
        // TODO: all commented out because activations is now a 3D array, no longer a vector.
        /*if (activations == null) {
            return;
        }
        float[] ca = color.getColorComponents(null);
        gl.glPushAttrib(GL2ES3.GL_COLOR | GL.GL_LINE_WIDTH);
        gl.glLineWidth(lineWidth);
        gl.glColor4fv(ca, 0);
        float dx = (float) (width) / (activations.length);
        float sy = (float) HISTOGRAM_HEIGHT_FRACTION * (height);

        gl.glBegin(GL.GL_LINE_STRIP);
        for (int i = 0; i < activations.length; i++) {
            float y = 1 + (sy * activations[i]);  // TODO debug hack
            float x1 = 1 + (dx * i), x2 = x1 + dx;
            gl.glVertex2f(x1, 1);
            gl.glVertex2f(x1, y);
            gl.glVertex2f(x2, y);
            gl.glVertex2f(x2, 1);
        }
        gl.glEnd();
        gl.glPopAttrib();*/
    }

    /**
     * Draw with default width and color
     *
     * @param gl
     * @param width width of (chip) area in gl pixels
     * @param height of (chip) area in gl pixels
     */
    private void drawHistogram(GL2 gl, float[] activations, int width, int height) { // width and height are of AEchip size in pixels of chip (not screen pixels)

    }

    /**
     * @return the netname
     */
    public String getNetname() {
        return netname;
    }

    /**
     * @param netname the netname to set
     */
    public void setNetname(String netname) {
        this.netname = netname;
    }

    /**
     * @return the nettype
     */
    public String getNettype() {
        return nettype;
    }

    /**
     * @param nettype the nettype to set
     */
    public void setNettype(String nettype) {
        this.nettype = nettype;
    }

    /**
     * @return the labels
     */
    public List<String> getLabels() {
        return labels;
    }

    /**
     * @param labels the labels to set
     */
    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

}
