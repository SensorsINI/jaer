/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.util.Arrays;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2GL3;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.DavisRendererOutput;
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
    private boolean hideSubsamplingLayers = true;
    private boolean hideConvLayers = true;

    /**
     * Type of input to process
     */
    public enum DvsOrAps {

        Dvs, Aps
    }

    /**
     * For debug, clamps input image to fixed value
     */
    public boolean isInputClampedTo1() {
        return inputLayer == null ? false : inputLayer.isInputClampedTo1();
    }

    /**
     * For debug, clamps input image to fixed value
     */
    public void setInputClampedTo1(boolean inputClampedTo1) {
        if (inputLayer != null) {
            inputLayer.setInputClampedTo1(inputClampedTo1);
        }
    }

    /**
     * For debug, clamps input image to fixed value
     */
    public boolean isInputClampedToIncreasingIntegers() {
        return inputLayer == null ? false : inputLayer.isInputClampedToIncreasingIntegers();
    }

    /**
     * For debug, clamps input image to fixed value
     */
    public void setInputClampedToIncreasingIntegers(boolean inputClampedTo1) {
        if (inputLayer != null) {
            inputLayer.setInputClampedToIncreasingIntegers(inputClampedTo1);
        }
    }

    /**
     * Computes the output of the network from an input activationsFrame
     *
     * @param frame the image, indexed by y * width + x
     * @param type type of rendered image to process from renderer
     * @return the vector of output values
     * @see #getActivations
     */
    public float[] processFrame(AEFrameChipRenderer frame, DvsOrAps type) {

        inputLayer.processFrame(frame, type);
        for (int i = 1; i < nLayers; i++) {
            layers[i].compute(layers[i - 1]);
        }
        outputLayer.compute(layers[nLayers - 1]);
        return outputLayer.activations;
    }

    void drawActivations() {
        checkActivationsFrame();
        for (Layer l : layers) {
            if (l instanceof ConvLayer && hideConvLayers) {
                continue;
            }
            if (l instanceof SubsamplingLayer && hideSubsamplingLayers) {
                continue;
            }
            l.drawActivations();
        }
        if (outputLayer != null) {
            outputLayer.drawActivations();
        }
        if (!activationsFrame.isVisible()) {
            activationsFrame.setVisible(true);
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
        kernelsFrame.setLayout(new BoxLayout(kernelsFrame.getContentPane(), BoxLayout.Y_AXIS));
        kernelsFrame.setPreferredSize(new Dimension(600, 600));
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
            // override to processFrame constants for layer
        }

        /**
         * Computes activations from input layer
         *
         * @param input the input layer to processFrame from
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
     * image activationsFrame. Order of entries in activations is the same as in
     * matlab, first column on left from row=0 to dimy, 2nd column, etc.
     */
    public class InputLayer extends Layer {

        public InputLayer(int index) {
            super(index);
        }

        private boolean inputClampedTo1 = false; // for debug
        private boolean inputClampedToIncreasingIntegers = false; // debug
        int dimx;
        int dimy;
        int nUnits;
        private ImageDisplay imageDisplay = null;

        /**
         * Computes the output from input frame. The frame can be either a
         * gray-scale frame[] with a single entry per pixel, or it can be an RGB
         * frame[] with 3 sample (RGB) per pixel. The appropriate extraction is
         * done in processFrame by the FrameType parameter.
         *
         * @param frame the input image, indexed by <code>y * width + x</code>.
         * Lower left pixel is pixel x,y=0,0. Next pixel is x,y=1,0, etc
         * @param frameWidth the width of image in pixels
         * @param type either Dvs or Aps type of input to process
         * @return the vector of network output values, which are indexed by
         * y+dimy*x
         * @see #getActivations
         */
        public float[] processFrame(AEFrameChipRenderer renderer, DvsOrAps type) {
//            if (frame == null || frameWidth == 0 || (frame.length / type.samplesPerPixel()) % frameWidth != 0) {
//                throw new IllegalArgumentException("input frame is null or frame array length is not a multiple of width=" + frameWidth);
//            }
            if (activations == null) {
                activations = new float[nUnits];
            }
            int frameHeight = renderer.getChip().getSizeY();
            int frameWidth = renderer.getChip().getSizeX();
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
                    float v=0;
                    switch (type) {
                        case Aps:
                            v = renderer.getApsGrayValueAtPixel((int) Math.floor(x), (int) Math.floor(y));
                            break;
                        case Dvs:
                            float[] fv = renderer.getDvsRenderedValuesAtPixel((int) Math.floor(x), (int) Math.floor(y));
                            switch (renderer.getColorMode()) {
                                case GrayLevel:
                                    v = fv[0];
                                    break;
                                case RedGreen:
                                    v = (fv[1] - fv[0]) / 2;
                                    break;
                                default:
                                    v = (fv[0] + fv[1] + fv[2]);
                                    break;
                            }
                    }
                    // TODO remove only for debug
                    if (inputClampedToIncreasingIntegers) {
                        v = (float) (xo + yo) / (dimx + dimy); // make image that is x+y, for debugging
//                        v = (float) (yo) / (dimy);
                    } else if (inputClampedTo1) {
                        v = .5f;
                    }
                    activations[o(dimy - yo - 1, xo)] = v; // NOTE transpose and flip of image here which is actually the case in matlab code (image must be drawn in matlab as transpose to be correct orientation)
                    xo++;
                }
                yo++;
            }
            return activations;
        }

        int o(int x, int y) {
            return (dimy * x) + y;  // activations of input layer are stored by column and then row, as in matlab array that is taken by (:)
        }

        @Override
        public void compute(Layer input) {
            throw new UnsupportedOperationException("Input layer only computes on input frame, not previous layer output; use compute(frame[] f, ...) method");
        }

        @Override
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
                    imageDisplay.setPixmapGray(x, y, a(0, x, y));
                }
            }
            imageDisplay.repaint();
        }

        @Override
        public final float a(int map, int x, int y) {
            return activations[o(x, y)];
        }

        /**
         * @return the inputClampedTo1
         */
        public boolean isInputClampedTo1() {
            return inputClampedTo1;
        }

        /**
         * @param inputClampedTo1 the inputClampedTo1 to set
         */
        public void setInputClampedTo1(boolean inputClampedTo1) {
            this.inputClampedTo1 = inputClampedTo1; // TODO remove debug
        }

        /**
         * @return the inputClampedToIncreasingIntegers
         */
        public boolean isInputClampedToIncreasingIntegers() {  // only for debug
            return inputClampedToIncreasingIntegers; // TODO remove debug
        }

        /**
         * @param inputClampedToIncreasingIntegers the
         * inputClampedToIncreasingIntegers to set
         */
        public void setInputClampedToIncreasingIntegers(boolean inputClampedToIncreasingIntegers) {
            this.inputClampedToIncreasingIntegers = inputClampedToIncreasingIntegers;
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
     *
     * Note that for each of I input maps there are O kernels corresponding the the O output maps. Therefore there are I*O kernels.
     *
     */
    public class ConvLayer extends Layer {

        int nInputMaps;
        int nOutputMaps; //
        int kernelDim, singleKernelLength, halfKernelDim, kernelWeightsPerOutputMap, nKernels;
        float[] biases;
        /**
         * @see #k(int, int, int, int)
         */
        float[] kernels;
        private int inputMapLength; // length of single input map out of input.activations
        private int inputMapDim; // size of single input map, sqrt of inputMapLength for square input (TODO assumes square input)
        int outputMapLength; // length of single output map vector; biases.length/nOutputMaps, calculated during processFrame()
        int outputMapDim;  // dimension of single output map, calculated during processFrame()
        int activationsLength;
        ImageDisplay[] activationDisplays = null;
        ImageDisplay[][] kernelDisplays = null;

        public ConvLayer(int index) {
            super(index);
        }

        @Override
        public String toString() {
            return String.format("index=%d CNN   layer; nInputMaps=%d nOutputMaps=%d kernelSize=%d biases=float[%d] kernels=float[%d]",
                    index, nInputMaps, nOutputMaps, kernelDim, biases == null ? 0 : biases.length, kernels == null ? 0 : kernels.length);
        }

        @Override
        public void initializeConstants() {
            singleKernelLength = kernelDim * kernelDim;
            halfKernelDim = kernelDim / 2;
            // output size can only be computed once we know our input 
        }

        /**
         * Computes convolutions of input kernels with input maps
         *
         * @param inputLayer the input to this layer, represented by nInputMaps
         * on the activations array
         */
        @Override
        public void compute(Layer inputLayer) {
            if (inputLayer.activations == null) {
                log.warning("input.activations==null");
                return;
            }
            if (inputLayer.activations.length % nInputMaps != 0) {
                log.warning("input.activations.length=" + inputLayer.activations.length + " which is not divisible by nInputMaps=" + nInputMaps);
            }
            inputMapLength = inputLayer.activations.length / nInputMaps; // for computing indexing to input
            double sqrtInputMapLength = Math.sqrt(inputMapLength);
            if (Math.IEEEremainder(sqrtInputMapLength, 1) != 0) {
                log.warning("input map is not square; Math.rint(sqrtInputMapLength)=" + Math.rint(sqrtInputMapLength));
            }
            nKernels = nInputMaps * nOutputMaps;
            inputMapDim = (int) sqrtInputMapLength;
            outputMapDim = inputMapDim - kernelDim + 1;
            outputMapLength = outputMapDim * outputMapDim;
            activationsLength = outputMapLength * nOutputMaps;
            kernelWeightsPerOutputMap = singleKernelLength * nOutputMaps;

            if (nOutputMaps != biases.length) {
                log.warning("nOutputMaps!=biases.length: " + nOutputMaps + "!=" + biases.length);
            }

            if (activations == null || activations.length != activationsLength) {
                activations = new float[activationsLength];
            } else {
                Arrays.fill(activations, 0);  // clear the output, since results from inputMaps will be accumulated
            }

            for (int inputMap = 0; inputMap < nInputMaps; inputMap++) { // for each inputMap
                for (int outputMap = 0; outputMap < nOutputMaps; outputMap++) { // for each kernel/outputMap
                    conv(inputLayer, outputMap, inputMap);
                }
            }

            applyBiasAndNonlinearity();
        }

        // convolves a given kernel over the inputMap and accumulates output to activations
        private void conv(Layer inputLayer, int outputMap, int inputMap) {
            int startx = halfKernelDim, starty = halfKernelDim, endx = inputMapDim - halfKernelDim, endy = inputMapDim - halfKernelDim;
            int xo = 0, yo;
            for (int xi = startx; xi < endx; xi++) { // index to outputMap
                yo = 0;
                for (int yi = starty; yi < endy; yi++) {
                    int outidx = o(outputMap, xo, yo);
                    activations[outidx] += convsingle(inputLayer, outputMap, inputMap, xi, yi);
                    yo++;
                }
                xo++;
            }
        }

        // computes single kernel inner product summed result centered on x,y in inputMap
        // DANGER DANGER - note that in matlab the conv2/convn function do convolutions by flipping the kernel matrix and then doing 2d sum-of-products.
        // So the sum-of-product results are not just the sum of products of corresponding x,y entries.
        private float convsingle(Layer input, int outputMap, int inputMap, int xincenter, int yincenter) {
            float sum = 0;
            // march over kernel y and x
            for (int xx = 0; xx < kernelDim; xx++) { // kernel coordinate
                int inx = xincenter + xx - halfKernelDim; // input coordinate
                for (int yy = 0; yy < kernelDim; yy++) { //yy is kernel coordinate
                    int iny = yincenter + yy - halfKernelDim; // iny is input coordinate
//                    sum += 1;
//                    sum += input.a(inputMap, inx, iny);
                    sum += kernels[k(inputMap, outputMap, kernelDim - xx - 1, kernelDim - yy - 1)] * input.a(inputMap, inx, iny); // NOTE flip of kernel to match matlab convention of reversing kernel as though doing time-based convolution
                    iny++;
                }
                inx++;
            }
//            return 1; //1; // debug
            return sum; //1; // debug
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

        /**
         * Return kernel index corresponding to input map, kernel (output map),
         * x, and y.
         * <p>
         * kernels are stored in this order in the kernels array: y, x,
         * outputMap, inputMap, i.e. for 5x5 kernels, 12 output maps and 6 input
         * maps, the first 12 kernels (25*12=300 weights) are the 12 5x5 weights
         * for the first input map and each output map.
         *
         *
         * @param inputMap the features map to convolve
         * @param outputMap the feature (output map) to accumulate to
         * @param x
         * @param y
         * @return the index into kernels[]
         */
        final int k(int inputMap, int outputMap, int x, int y) {
            return inputMap * kernelWeightsPerOutputMap + singleKernelLength * outputMap + kernelDim * x + y; //(kernelDim - y - 1);
        }

        // output index
        final int o(int outputMap, int x, int y) {
            return outputMap * outputMapLength + outputMapDim * x + y; //(outputMapDim-y-1);
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
                kernelsFrame.getContentPane().add(panel);
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                kernelDisplays = new ImageDisplay[nOutputMaps][nInputMaps];
                int s = (int) Math.ceil(Math.sqrt(nOutputMaps));
                for (int outputMapNumber = 0; outputMapNumber < nOutputMaps; outputMapNumber++) {
                    JPanel outputMapPanel = new JPanel(new GridLayout(s, s));
                    for (int inputFeatureMapNumber = 0; inputFeatureMapNumber < nInputMaps; inputFeatureMapNumber++) {
                        kernelDisplays[outputMapNumber][inputFeatureMapNumber] = ImageDisplay.createOpenGLCanvas();
                        kernelDisplays[outputMapNumber][inputFeatureMapNumber].setBorderSpacePixels(1);
                        kernelDisplays[outputMapNumber][inputFeatureMapNumber].setImageSize(kernelDim, kernelDim);
                        kernelDisplays[outputMapNumber][inputFeatureMapNumber].setSize(100, 100);
                        outputMapPanel.add(kernelDisplays[outputMapNumber][inputFeatureMapNumber]);
                    }
                    panel.add(outputMapPanel);
                }

                kernelsFrame.pack();
            }
            for (int kernel = 0; kernel < nOutputMaps; kernel++) {
                for (int inputFeatureMapNumber = 0; inputFeatureMapNumber < nInputMaps; inputFeatureMapNumber++) {
                    for (int x = 0; x < kernelDim; x++) {
                        for (int y = 0; y < kernelDim; y++) {
                            kernelDisplays[kernel][inputFeatureMapNumber].setPixmapGray(x, y, kernels[k(inputFeatureMapNumber, kernel, x, y)]);
                            kernelDisplays[kernel][inputFeatureMapNumber].setFontSize(12);
                            kernelDisplays[kernel][inputFeatureMapNumber].setTitleLabel(String.format("o%d i%d", kernel, inputFeatureMapNumber));
                        }
                    }
                    kernelDisplays[kernel][inputFeatureMapNumber].display();
                }
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
                                s += convLayer.a(map, xi, yi); // add to sum to processFrame average
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

        // output index function
        final int o(int map, int x, int y) {
            return map * outputMapLength + x * outputMapDim + y;
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

        @Override
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
        /**
         * @see #weight(int, int)
         */
        float[] weights;
        public float maxActivation;
        public int maxActivatedUnit;

        @Override
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
            int aidx = 0;
            for (int unit = 0; unit < biases.length; unit++) {
                for (int w = 0; w < input.activations.length; w++) {
                    activations[unit] += input.activations[aidx] * weight(unit, biases.length, w);
                    aidx++; // the input activations are stored in the feature maps of last layer, column, row, map order
                }
                aidx = 0;
            }

            maxActivation = Float.NEGATIVE_INFINITY;
            for (int unit = 0; unit < biases.length; unit++) {
                activations[unit] = sigm(activations[unit] + biases[unit]);
                if (activations[unit] > maxActivation) {
                    maxActivatedUnit = unit;
                    maxActivation = activations[unit];
                }
            }

        }

        private float weight(int unit, int nUnits, int weight) {
            // ffW in matlab DeepLearnToolbox, a many by few array in XML where there are a few rows each with many columsn to dot with previous layer
            // weight array here is stored by columns; first 4-column has first weight for each of 4 outputs, 2nd column (entries 4-7) has 2nd weights for 4 output units.
            return weights[unit + nUnits * weight]; // e.g 2nd weight for unit 0 is at position 4=0+4*1
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
            float dx = (float) (width) / (activations.length);
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
                        l.kernelDim = layerReader.getInt("kernelSize");
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

    @Override
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

    /**
     * @return the hideSubsamplingLayers
     */
    public boolean isHideSubsamplingLayers() {
        return hideSubsamplingLayers;
    }

    /**
     * @param hideSubsamplingLayers the hideSubsamplingLayers to set
     */
    public void setHideSubsamplingLayers(boolean hideSubsamplingLayers) {
        this.hideSubsamplingLayers = hideSubsamplingLayers;
    }

    /**
     * @return the hideConvLayers
     */
    public boolean isHideConvLayers() {
        return hideConvLayers;
    }

    /**
     * @param hideConvLayers the hideConvLayers to set
     */
    public void setHideConvLayers(boolean hideConvLayers) {
        this.hideConvLayers = hideConvLayers;
    }

}
