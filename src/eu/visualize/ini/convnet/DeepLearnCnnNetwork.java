/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.awt.Dimension;
import java.io.File;
import java.util.Arrays;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2GL3;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.ImageDisplay;

/**
 * Simple convolutional neural network (CNN) data structure to hold CNN from
 * Matlab DeepLearnToolbox. Replicates the computation in matlab function
 * cnnff.m, which is as follows:
 * <pre>
 *
 * function net = cnnff(net, x)
 * n = numel(net.layers);
 * net.layers{1}.activations{1} = x;
 * inputmaps = 1;
 *
 * for l = 2 : n   %  for each layer
 * if strcmp(net.layers{l}.type, 'c')
 * %  !!below can probably be handled by insane matrix operations
 * for j = 1 : net.layers{l}.outputmaps   %  for each output map
 * %  create temp output map
 * z = zeros(size(net.layers{l - 1}.activations{1}) - [net.layers{l}.kernelsize - 1 net.layers{l}.kernelsize - 1 0]);
 * for i = 1 : inputmaps   %  for each input map
 * %  convolve with corresponding kernel and add to temp output map
 * z = z + convn(net.layers{l - 1}.activations{i}, net.layers{l}.k{i}{j}, 'valid');
 * end
 * %  add bias, pass through nonlinearity
 * net.layers{l}.activations{j} = sigm(z + net.layers{l}.b{j});
 * end
 * %  set number of input maps to this layers number of outputmaps
 * inputmaps = net.layers{l}.outputmaps;
 * elseif strcmp(net.layers{l}.type, 's')
 * %  downsample
 * for j = 1 : inputmaps
 * z = convn(net.layers{l - 1}.activations{j}, ones(net.layers{l}.scale) / (net.layers{l}.scale ^ 2), 'valid');   %  !! replace with variable
 * net.layers{l}.activations{j} = z(1 : net.layers{l}.scale : end, 1 : net.layers{l}.scale : end, :);
 * end
 * end
 * end
 *
 * %  concatenate all end layer feature maps into vector
 * net.fv = [];
 * for j = 1 : numel(net.layers{n}.activations)
 * sa = size(net.layers{n}.activations{j});
 * net.fv = [net.fv; reshape(net.layers{n}.activations{j}, sa(1) * sa(2), sa(3))];
 * end
 * %  feedforward into output perceptrons
 * net.o = sigm(net.ffW * net.fv + repmat(net.ffb, 1, size(net.fv, 2)));
 *
 * end
 *
 *
 * </pre>
 *
 * @author tobi
 */
public class DeepLearnCnnNetwork {

    int nLayers;
    String netname;
    String notes;
    String dob;
    String nettype;
    Layer[] layers;
    InputLayer inputLayer;
    OutputLayer outputLayer;
    JFrame activationsFrame = null, kernelsFrame = null;

    /**
     * Computes the output of the network from an input activationsFrame
     *
     * @param frame the image, indexed by y * width + x
     * @param width the width of image in pixels
     * @return the vector of output values
     * @see #getActivations
     */
    public float[] compute(double[] frame, int width) {

        inputLayer.compute(frame, width);
        for (int i = 1; i < nLayers; i++) {
            layers[i].compute(layers[i - 1]);
        }
        outputLayer.compute(layers[nLayers - 2]);
//        showActivations();
        return outputLayer.activations;
    }

    void drawActivations() {
        checkActivationsFrame();
        for (Layer l : layers) {
            l.drawActivations();
        }
        if (outputLayer != null) {
            outputLayer.drawActivations();
        }
        if (!activationsFrame.isVisible()) {
            activationsFrame.setVisible(true);
            return;
        }
    }

    void drawKernels() {
        checkKernelsFrame();
        for (Layer l : layers) {
            if (l instanceof ConvLayer) {
                ((ConvLayer) l).drawKernels();
//                break; // DEBUG only first layer
            }
        }
        if (!kernelsFrame.isVisible()) {
            kernelsFrame.setVisible(true);
            return;
        }
        kernelsFrame.repaint();
    }

    private void checkActivationsFrame() {
        if (activationsFrame != null) {
            return;
        }

        activationsFrame = new JFrame("Activations: DeepLearnCNNNetwork");
        activationsFrame.setLayout(new BoxLayout(activationsFrame.getContentPane(), BoxLayout.Y_AXIS));
        activationsFrame.setPreferredSize(new Dimension(600, 600));
    }

    private void checkKernelsFrame() {
        if (kernelsFrame != null) {
            return;
        }

        kernelsFrame = new JFrame("Kernels: DeepLearnCNNNetwork");
        kernelsFrame.setLayout(new BoxLayout(activationsFrame.getContentPane(), BoxLayout.Y_AXIS));
        kernelsFrame.setPreferredSize(new Dimension(600, 600));
//        activationsFrame.setVisible(true);

//            activationsFrame.addWindowListener(new WindowAdapter() {
//                public void windowClosing(WindowEvent e) {
//                    setVisible(false);
//                }
//            });
    }

    abstract public class Layer {

        public Layer(int index) {
            this.index = index;
        }

        /**
         * Layer index
         */
        int index;
        /**
         * Activations
         */
        float[] activations;

        private boolean visible = true;

        public void initializeConstants() {
            // override to compute constants for layer
        }

        /**
         * Computes activations from input layer
         *
         * @param input the input layer to compute from
         */
        abstract public void compute(Layer input);

        public void drawActivations() {

        }

        /**
         * @return the visible
         */
        public boolean isVisible() {
            return visible;
        }

        /**
         * Sets whether to annotateHistogram this layer.
         *
         * @param visible the visible to set
         */
        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        /**
         * Return the activation of this layer
         *
         * @param map the output map
         * @param x
         * @param y
         * @return activation from 0-1
         */
        abstract public float a(int map, int x, int y);

    }

    /**
     * The logistic function
     *
     * @param v
     * @return
     */
    private float sigm(float v) {
        return (float) (1.0 / (1.0 + Math.exp(-v)));
    }

    /**
     * Represents input to network; computes the sub/down sampled input from
     * image activationsFrame.
     */
    public class InputLayer extends Layer {

        public InputLayer(int index) {
            super(index);
        }

        int dimx;
        int dimy;
        int nUnits;
        private ImageDisplay imageDisplay = null;

        /**
         * Computes the output from input activationsFrame
         *
         * @param frame the input image, indexed by <code>y * width + x</code>.
         * Lower left pixel is pixel x,y=0,0. Next pixel is x,y=1,0, etc
         * @param frameWidth the width of image in pixels
         * @return the vector of output values, which are indexed by y+dimy*x
         * @see #getActivations
         */
        public float[] compute(double[] frame, int frameWidth) {
            if (frame == null || frameWidth == 0 || frame.length % frameWidth != 0) {
                throw new IllegalArgumentException("input frame is null or frame vector dimension not a multiple of width=" + frameWidth);
            }
            if (activations == null) {
                activations = new float[nUnits];
            }
            int frameHeight = frame.length / frameWidth;
            // subsample input activationsFrame to dimx dimy 
            // activationsFrame has width*height pixels
            // for first pass we just downsample every width/dimx pixel in x and every height/dimy pixel in y
            // TODO change to subsample (averaging)
            float xstride = (float) frameWidth / dimx, ystride = (float) frameHeight / dimy;
            int xo, yo = 0;
            loop:
            for (float y = 0; y < frameHeight; y += ystride) {
                xo = 0;
                for (float x = 0; x < frameWidth; x += xstride) {  // take every xstride, ystride pixels as output
                    int fridx = (int) (Math.floor(y) * frameWidth + Math.floor(x)); // nearest pixel, for cheapest downsampling
//                    if (fridx >= activationsFrame.length) {
//                        break loop;
//                    }
//                    if (aidx >= activations.length) {
//                        break loop;
//                    }
                    activations[o(xo, yo)] = (float) frame[fridx];
                    xo++;
                }
                yo++;
            }
            return activations;
        }

        int o(int x, int y) {
            return y + (dimy * x); // activations of input layer are stored by column and then row, as in matlab array that is taken by (:)
        }

        @Override
        public void compute(Layer input) {
            throw new UnsupportedOperationException("Input layer only computes on input frame, not previous layer output; use compute(frame[] f, ...) method");
        }

        public String toString() {
            return String.format("index=%d Input layer; dimx=%d dimy=%d nUnits=%d",
                    index, dimx, dimy, nUnits);
        }

        @Override
        public void drawActivations() {
            if (!isVisible() || activations == null) {
                return;
            }
            checkActivationsFrame();
            if (imageDisplay == null) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                imageDisplay = ImageDisplay.createOpenGLCanvas();
                imageDisplay.setBorderSpacePixels(0);
                imageDisplay.setImageSize(dimx, dimy);
                imageDisplay.setSize(200, 200);
                panel.add(imageDisplay);

                activationsFrame.getContentPane().add(panel);
                activationsFrame.pack();
            }
            for (int x = 0; x < dimx; x++) {
                for (int y = 0; y < dimy; y++) {
                    imageDisplay.setPixmapGray(x, y, activations[o(x, y)]);
                }
            }
            imageDisplay.display();
        }

        @Override
        public final float a(int map, int x, int y) {
            return activations[o(x, y)];
        }

    }

    /**
     * A convolutional layer. Does this operation:
     * <pre>
     * for j = 1 : net.layers{l}.outputmaps   %  for each output map
     * %  create temp output map
     *
     *
     * %the output map dimension is reduced from input map dimension by the (dim of kernel-1).
     * %That is because the kernel starts convolving inset by kernel dim/2, and it ends at kernel dim/2 from other side.
     *
     * %Another way to say it is that kernel.0 starts on left at input.0 and kernel.n starts at n.
     * %kernel.n ends at input.m. Therefore m-n+1 is the number of steps, or m-(n-1) is the number of steps.
     *
     * z = zeros(size(net.layers{l - 1}.activations{1}) - [net.layers{l}.kernelsize - 1 net.layers{l}.kernelsize - 1 0]);
     * for i = 1 : inputmaps   %  for each input map
     * %  convolve with corresponding kernel and add to temp output map
     * z = z + convn(net.layers{l - 1}.activations{i}, net.layers{l}.k{i}{j}, 'valid');
     * end
     * %  add bias, pass through nonlinearity
     * net.layers{l}.activations{j} = sigm(z + net.layers{l}.b{j});
     *
     * <pre>
     */
    public class ConvLayer extends Layer {

        public ConvLayer(int index) {
            super(index);
        }

        int nInputMaps;
        int nOutputMaps; // same as number of kernels
        int kernelSize, kernelLength, halfKernelSize;
        float[] biases;
        float[] kernels;
        private int inputMapLength; // length of single input map out of input.activations
        private int inputMapDim; // size of single input map, sqrt of inputMapLength for square input (TODO assumes square input)
        int outputMapLength; // length of single output map vector; biases.length/nOutputMaps, calculated during compute()
        int outputMapDim;  // dimension of single output map, calculated during compute()
        int activationsLength;
        ImageDisplay[] activationDisplays = null, kernelDisplays = null;

        public String toString() {
            return String.format("index=%d CNN   layer; nInputMaps=%d nOutputMaps=%d kernelSize=%d biases=float[%d] kernels=float[%d]",
                    index, nInputMaps, nOutputMaps, kernelSize, biases == null ? 0 : biases.length, kernels == null ? 0 : kernels.length);
        }

        @Override
        public void initializeConstants() {
            kernelLength = kernelSize * kernelSize;
            halfKernelSize = kernelSize / 2;
            // output size can only be computed once we know our input 
        }

        /**
         * Computes convolutions of input kernels with input maps
         *
         * @param input the input to this layer, represented by nInputMaps on
         * the activations array
         */
        @Override
        public void compute(Layer input) {
            // TODO convolve activations of input with kernels
            if (input.activations == null) {
                log.warning("input.activations==null");
                return;
            }
            if (input.activations.length % nInputMaps != 0) {
                log.warning("input.activations.length=" + input.activations.length + " which is not divisible by nInputMaps=" + nInputMaps);
            }
            inputMapLength = input.activations.length / nInputMaps; // for computing indexing to input
            double sqrtInputMapLength = Math.sqrt(inputMapLength);
            if (Math.IEEEremainder(sqrtInputMapLength, 1) != 0) {
                log.warning("input map is not square; Math.rint(sqrtInputMapLength)=" + Math.rint(sqrtInputMapLength));
            }
            inputMapDim = (int) sqrtInputMapLength;
            outputMapDim = inputMapDim - kernelSize + 1;
            outputMapLength = outputMapDim * outputMapDim;
            activationsLength = outputMapLength * nOutputMaps;

            if (nOutputMaps != biases.length) {
                log.warning("nOutputMaps!=biases.length: " + nOutputMaps + "!=" + biases.length);
            }

            if (activations == null || activations.length != activationsLength) {
                activations = new float[activationsLength];
            } else {
                Arrays.fill(activations, 0);  // clear the output, since results from inputMaps will be accumulated
            }

            for (int kernel = 0; kernel < nOutputMaps; kernel++) { // for each kernel/outputMap
                for (int inputMap = 0; inputMap < nInputMaps; inputMap++) { // for each inputMap
                    conv(input, kernel, inputMap);
                }
            }

            applyBiasAndNonlinearity();
        }

        // convolves a given kernel over the inputMap and accumulates output to activations
        private void conv(Layer input, int kernel, int inputMap) {
            int startx = halfKernelSize, starty = halfKernelSize, endx = inputMapDim - halfKernelSize, endy = inputMapDim - halfKernelSize;
            int xo = 0, yo;
            for (int xi = startx; xi < endx; xi++) { // index to outputMap
                yo = 0;
                for (int yi = starty; yi < endy; yi++) {
                    int outidx = o(kernel, xo, yo);
                    activations[outidx] += convsingle(input, kernel, inputMap, xi, yi);
                    yo++;
                }
                xo++;
            }
        }

        // computes single kernal location summed result centered on x,y in inputMap
        private float convsingle(Layer input, int kernel, int inputMap, int x, int y) {
            float sum = 0;
            for (int yy = 0; yy < kernelSize; yy++) {
                int iny = y - halfKernelSize;
                for (int xx = 0; xx < kernelSize; xx++) {
                    int inx = x - halfKernelSize;
                    sum += kernels[k(kernel, xx, yy)] * input.a(inputMap, inx, iny);
                    inx++;
                }
                iny++;
            }
            return sum;
        }

        private void applyBiasAndNonlinearity() {
            if (activations == null) {
                return;
            }
            for (int b = 0; b < biases.length; b++) {
                for (int x = 0; x < outputMapDim; x++) {
                    for (int y = 0; y < outputMapDim; y++) {
                        int idx = o(b, x, y);
                        activations[idx] = sigm(activations[idx] + biases[b]);
                    }
                }
            }

        }

        // input index
        final int i(int map, int x, int y) {
            return map * inputMapLength + x * inputMapDim + (outputMapDim-y-1); // TODO check x,y
        }

        // kernel index
        final int k(int kernel, int x, int y) {
            return kernelLength * kernel + kernelSize * x + (kernelSize - y - 1);
        }

        // output index
        final int o(int outputMap, int x, int y) {
            return outputMap * outputMapLength + outputMapDim * x + (outputMapDim-y-1);
        }

        @Override
        public void drawActivations() {
            if (!isVisible() || activations == null) {
                return;
            }
            checkActivationsFrame();
            if (activationDisplays == null) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                activationDisplays = new ImageDisplay[nOutputMaps];
                for (int i = 0; i < nOutputMaps; i++) {
                    activationDisplays[i] = ImageDisplay.createOpenGLCanvas();
                    activationDisplays[i].setBorderSpacePixels(0);
                    activationDisplays[i].setImageSize(outputMapDim, outputMapDim);
                    activationDisplays[i].setSize(100, 100);
                    panel.add(activationDisplays[i]);
                }

                activationsFrame.getContentPane().add(panel);
                activationsFrame.pack();
            }
            for (int map = 0; map < nOutputMaps; map++) {
                for (int x = 0; x < outputMapDim; x++) {
                    for (int y = 0; y < outputMapDim; y++) {
                        activationDisplays[map].setPixmapGray(x, y, activations[o(map, x, y)]);
                    }
                }
                activationDisplays[map].display();
            }
        }

        private void drawKernels() {
            if (!isVisible() || kernels == null) {
                return;
            }
            checkKernelsFrame();
            if (kernelDisplays == null) {
                JPanel panel = new JPanel();
                panel.setPreferredSize(new Dimension(900, 200));
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                kernelDisplays = new ImageDisplay[nOutputMaps];
                for (int i = 0; i < nOutputMaps; i++) {
                    kernelDisplays[i] = ImageDisplay.createOpenGLCanvas();
                    kernelDisplays[i].setBorderSpacePixels(5);
                    kernelDisplays[i].setImageSize(kernelSize, kernelSize);
                    kernelDisplays[i].setSize(200, 200);
                    panel.add(kernelDisplays[i]);
                }

                kernelsFrame.getContentPane().add(panel);
                kernelsFrame.pack();
            }
            for (int kernel = 0; kernel < nOutputMaps; kernel++) {
                for (int x = 0; x < kernelSize; x++) {
                    for (int y = 0; y < kernelSize; y++) {
                        kernelDisplays[kernel].setPixmapGray(x, y, kernels[k(kernel, x, y)]);
                    }
                }
                kernelDisplays[kernel].display();
            }
        }

        @Override
        public float a(int map, int x, int y) {
            return activations[o(map, x, y)];
        }

    }

    public class SubsamplingLayer extends Layer {

        int averageOverDim, averageOverNum; // we average over averageOverDim*averageOverDim inputs
        float[] biases;
        int inputMapLength, inputMapDim, outputMapLength, outputMapDim;
        float averageOverMultiplier;
        int nOutputMaps;
        private int activationsLength;
        ImageDisplay[] activationDisplays = null;

        public SubsamplingLayer(int index) {
            super(index);
        }

        @Override
        public void compute(Layer input) {
            if (!(input instanceof ConvLayer)) {
                log.warning("Input to SubsamplingLayer is not ConvLayer; it is actaully " + input.toString());
                return;
            }
            ConvLayer convLayer = (ConvLayer) input;
            nOutputMaps = convLayer.nOutputMaps;
            inputMapDim = convLayer.outputMapDim;
            inputMapLength = convLayer.outputMapLength;
            outputMapDim = inputMapDim / averageOverDim;
            averageOverNum = (averageOverDim * averageOverDim);
            averageOverMultiplier = 1f / averageOverNum;
            outputMapLength = inputMapLength / averageOverNum;
            activationsLength = outputMapLength * nOutputMaps;

            if (activations == null || activations.length != activationsLength) {
                activations = new float[activationsLength];
            }

            for (int map = 0; map < nOutputMaps; map++) {
                for (int xo = 0; xo < outputMapDim; xo++) { // output map index
                    for (int yo = 0; yo < outputMapDim; yo++) { // output map
                        float s = 0; // sum
                        // input indices
                        int startx = xo * averageOverDim, endx = startx + averageOverDim, starty = yo * averageOverDim, endy = starty + averageOverDim;
                        for (int xi = startx; xi < endx; xi++) { // iterate over input
                            for (int yi = starty; yi < endy; yi++) {
                                s += convLayer.a(map, xi, yi); // add to sum to compute average
                            }
                        }
                        // debug
                        int idx = o(map, xo, yo);
                        if (idx >= activations.length) {
                            log.warning("overrun output activations");
                        }
                        activations[o(map, xo, yo)] = s * averageOverMultiplier;  //average
                    }
                }
            }
        }

        final int i(int map, int x, int y) {
            return map * inputMapLength + x * inputMapDim + (outputMapDim - y - 1); // TODO check x,y
        }

        final int o(int map, int x, int y) {
            return map * outputMapLength + x * outputMapDim + (outputMapDim - y - 1);
        }

        @Override
        public void drawActivations() {
            if (!isVisible() || activations == null) {
                return;
            }
            checkActivationsFrame();
            if (activationDisplays == null) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                activationDisplays = new ImageDisplay[nOutputMaps];
                for (int i = 0; i < nOutputMaps; i++) {
                    activationDisplays[i] = ImageDisplay.createOpenGLCanvas();
                    activationDisplays[i].setBorderSpacePixels(0);
                    activationDisplays[i].setImageSize(outputMapDim, outputMapDim);
                    activationDisplays[i].setSize(100, 100);
                    panel.add(activationDisplays[i]);
                }

                activationsFrame.getContentPane().add(panel);
                activationsFrame.pack();
            }
            for (int map = 0; map < nOutputMaps; map++) {
                for (int x = 0; x < outputMapDim; x++) {
                    for (int y = 0; y < outputMapDim; y++) {
                        activationDisplays[map].setPixmapGray(x, y, activations[o(map, x, y)]);
                    }
                }
                activationDisplays[map].display();
            }
        }

        public String toString() {
            return String.format("index=%d Subsamp layer; averageOver=%d biases=float[%d]",
                    index, averageOverDim, biases == null ? 0 : biases.length);
        }

        @Override
        public float a(int map, int x, int y) {
            return activations[o(map, x, y)];
        }
    }

    public class OutputLayer extends Layer {

        private ImageDisplay imageDisplay;

        public OutputLayer(int index) {
            super(index);
        }

        float[] biases;  //ffb in matlab DeepLearnToolbox
        float[] weights; // ffW in matlab DeepLearnToolbox, a many by few array in XML where there are a few rows each with many columsn to dot with previous layer
        public float maxActivation;
        public int maxActivatedUnit;
        public int cols, rows;

        public String toString() {
            return String.format("Output: bias=float[%d] outputWeights=float[%d]", biases.length, weights.length);
        }

        /**
         * Computes following perceptron output:
         * <pre>
         * concatenate all end layer feature maps into vector
         * net.fv = [];
         * for j = 1 : numel(net.layers{n}.activations)
         * sa = size(net.layers{n}.activations{j});
         * net.fv = [net.fv; reshape(net.layers{n}.activations{j}, sa(1) * sa(2), sa(3))];
         * end
         * %  feedforward into output perceptrons
         * net.o = sigm(net.ffW * net.fv + repmat(net.ffb, 1, size(net.fv, 2)));
         * </pre>
         */
        @Override
        public void compute(Layer input) {
            if (activations == null || activations.length != biases.length) {
                activations = new float[biases.length];
            } else {
                Arrays.fill(activations, 0);
            }
            maxActivation = Float.NEGATIVE_INFINITY;
            cols = input.activations.length / biases.length; // weights for each output unit
            rows=biases.length; // number of output units
            int idx = 0;
            for (int unit = 0; unit < biases.length; unit++) {
                for (int i = 0; i < cols; i++) {
                    activations[unit] += input.activations[idx] * weight(unit,i); // the input activations are stored in the feature maps of last layer, column, row, map order
                    idx++;
                }
                activations[unit] = sigm(activations[unit] + biases[unit]);
                if (activations[unit] > maxActivation) {
                    maxActivatedUnit = unit;
                    maxActivation = activations[unit];
                }
            }

        }
        
        private float weight(int unit, int weight){
            return weights[unit+rows*weight];
        }

        /**
         * Draw with default width and color
         *
         * @param gl
         * @param width width of annotateHistogram (chip) area in gl pixels
         * @param height of annotateHistogram (chip) area in gl pixels
         */
        public void annotateHistogram(GL2 gl, int width, int height) { // width and height are of AEchip annotateHistogram size in pixels of chip (not screen pixels)

            if (activations == null) {
                return;
            }
            float dx = (float) (width) / (activations.length + 2);
            float sy = (float) (height) / 1;

            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2f(1, 1);
            gl.glVertex2f(width - 1, 1);
            gl.glEnd();

            gl.glBegin(GL2.GL_LINE_STRIP);
            for (int i = 0; i < activations.length; i++) {
                float y = 1 + (sy * activations[i]);
                float x1 = 1 + (dx * i), x2 = x1 + dx;
                gl.glVertex2f(x1, 1);
                gl.glVertex2f(x1, y);
                gl.glVertex2f(x2, y);
                gl.glVertex2f(x2, 1);
            }
            gl.glEnd();
        }

        public void annotateHistogram(GL2 gl, int width, int height, float lineWidth, float[] color) {
            gl.glPushAttrib(GL2GL3.GL_COLOR | GL2.GL_LINE_WIDTH);
            gl.glLineWidth(lineWidth);
            gl.glColor4fv(color, 0);
            OutputLayer.this.annotateHistogram(gl, width, height);
            gl.glPopAttrib();
        }

        @Override
        public void drawActivations() {
            if (!isVisible() || activations == null) {
                return;
            }
            checkActivationsFrame();
            if (imageDisplay == null) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                imageDisplay = ImageDisplay.createOpenGLCanvas();
                imageDisplay.setBorderSpacePixels(0);
                imageDisplay.setImageSize(biases.length, 1);
                imageDisplay.setSize(200, 200);
                panel.add(imageDisplay);

                activationsFrame.getContentPane().add(panel);
                activationsFrame.pack();
            }
            for (int x = 0; x < biases.length; x++) {
                imageDisplay.setPixmapGray(x, 0, activations[x]);
            }
            imageDisplay.display();
        }

        @Override
        public float a(int map, int x, int y) { // map and y are ignored
            return activations[x];
        }

    }

    public void setNetworkToUniformValues(float weight, float bias) {
        if (layers == null) {
            return;
        }
        for (Layer l : layers) {
            if (l == null) {
                continue;
            }
            if (l instanceof ConvLayer) {
                ConvLayer c = (ConvLayer) l;
                if (c.kernels != null) {
                    Arrays.fill(c.kernels, weight);
                }
                if (c.biases != null) {
                    Arrays.fill(c.biases, bias);
                }

            } else if (l instanceof OutputLayer) {
                OutputLayer o = (OutputLayer) l;
                if (o.biases != null) {
                    Arrays.fill(o.biases, bias);
                }
                if (o.weights != null) {
                    Arrays.fill(o.weights, weight);
                }

            }
        }
    }

    public void loadFromXMLFile(File f) {
        try {
            EasyXMLReader networkReader = new EasyXMLReader(f);
            if (!networkReader.hasFile()) {
                log.warning("No file for reader; file=" + networkReader.getFile());
                return;
            }

            if (activationsFrame != null) {
                activationsFrame.dispose();
                activationsFrame = null;
            }

            netname = networkReader.getRaw("name");
            notes = networkReader.getRaw("notes");
            dob = networkReader.getRaw("dob");
            nettype = networkReader.getRaw("type");
            if (!nettype.equals("cnn")) {
                log.warning("network type is not cnn");
            }
            nLayers = networkReader.getNodeCount("Layer");
            if (layers != null) {
                for (int i = 0; i < layers.length; i++) {
                    layers[i] = null;
                }
            }
            layers = new Layer[nLayers];

            for (int i = 0; i < nLayers; i++) {
                EasyXMLReader layerReader = networkReader.getNode("Layer", i);
                int index = layerReader.getInt("index");
                String type = layerReader.getRaw("type");
                switch (type) {
                    case "i": {
                        inputLayer = new InputLayer(index);
                        layers[index] = inputLayer;
                        inputLayer.dimx = layerReader.getInt("dimx");
                        inputLayer.dimy = layerReader.getInt("dimy");
                        inputLayer.nUnits = layerReader.getInt("nUnits");
                    }
                    break;
                    case "c": {
                        ConvLayer l = new ConvLayer(index);
                        layers[index] = l;
                        l.nInputMaps = layerReader.getInt("inputMaps");
                        l.nOutputMaps = layerReader.getInt("outputMaps");
                        l.kernelSize = layerReader.getInt("kernelSize");
                        l.biases = layerReader.getBase64FloatArr("biases");
                        l.kernels = layerReader.getBase64FloatArr("kernels");
                        l.initializeConstants();
                    }
                    break;
                    case "s": {
                        SubsamplingLayer l = new SubsamplingLayer(index);
                        layers[index] = l;
                        l.averageOverDim = layerReader.getInt("averageOver");
                        l.biases = layerReader.getBase64FloatArr("biases");

                    }
                    break;
                }
            }
            outputLayer = new OutputLayer(nLayers);
           
            outputLayer.weights = networkReader.getBase64FloatArr("outputWeights"); // stored in many cols and few rows: one row per output unit
            outputLayer.biases = networkReader.getBase64FloatArr("outputBias");
            log.info(toString());
        } catch (RuntimeException e) {
            log.warning("couldn't load net from file: caught " + e.toString());
            e.printStackTrace();
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("DeepLearnCnnNetwork: \n");
        sb.append(String.format("name=%s, dob=%s, type=%s\nnotes=%s\n", netname, dob, nettype, notes));
        sb.append(String.format("nLayers=%d\n", nLayers));
        for (Layer l : layers) {
            sb.append((l == null ? "null layer" : l.toString()) + "\n");
        }
        sb.append(outputLayer == null ? "null outputLayer" : outputLayer.toString());
        return sb.toString();

    }

}
