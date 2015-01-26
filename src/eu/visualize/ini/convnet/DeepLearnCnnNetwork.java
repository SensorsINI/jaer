/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Arrays;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2GL3;
import javax.swing.JFrame;
import net.sf.jaer.event.BasicEvent;
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

    /**
     * Computes the output of the network from an input frame
     *
     * @param frame the image, indexed by y * width + x
     * @param width the width of image in pixels
     * @return the vector of output values
     * @see #getActivations
     */
    public float[] compute(float[] frame, int width) {

        inputLayer.compute(frame, width);
        for (int i = 1; i < nLayers; i++) {
            layers[i].compute(layers[i - 1]);
        }
        outputLayer.compute(layers[nLayers - 2]);
//        showActivations();
        return outputLayer.activations;
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

        public void initializeConstants() {
            // override to compute constants for layer
        }

        /**
         * Computes activations from input layer
         *
         * @param input the input layer to compute from
         */
        public void compute(Layer input) {

        }

        public void draw(ImageDisplay imageDisplay) {
            imageDisplay.setPixmapGreyArray(activations);
        }

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
     * image frame.
     */
    public class InputLayer extends Layer {

        public InputLayer(int index) {
            super(index);
        }

        int dimx;
        int dimy;
        int nUnits;

        /**
         * Computes the output from input frame
         *
         * @param frame the input image, indexed by <code>y * width + x</code>
         * @param width the width of image in pixels
         * @return the vector of output values, which are indexed by y+dimx*x
         * @see #getActivations
         */
        public float[] compute(float[] frame, int width) {
            if (frame == null || width == 0 || frame.length % width != 0) {
                throw new IllegalArgumentException("input frame is null or frame vector dimension not a multiple of width=" + width);
            }
            if (activations == null) {
                activations = new float[nUnits];
            }
            int height = frame.length / width;
            // subsample input frame to dimx dimy 
            // frame has width*height pixels
            // for first pass we just downsample every width/dimx pixel in x and every height/dimy pixel in y
            // TODO change to subsample (averaging)
            int xstride = (int) Math.ceil((double) width / dimx), ystride = (int) Math.ceil((double) height / dimy);
            int nx = width / xstride, ny = height / ystride;
            int aidx = 0;
            loop:
            for (int y = 0; y < height; y += ystride) {
                for (int x = 0; x < width; x += xstride) {  // take every xstride, ystride pixels as output
                    int fridx = y * width + x;
                    if (fridx >= frame.length) {
                        break loop;
                    }
                    if (aidx >= activations.length) {
                        break loop;
                    }
                    activations[aidx++] = frame[fridx];
                }
            }
            return activations; //indexed by y+dimx*x in the downsampled image
        }

        @Override
        public void compute(Layer input) {
            throw new UnsupportedOperationException("Input layer only computes on input frame, not previous layer output");
        }

        public String toString() {
            return String.format("index=%d Input layer; dimx=%d dimy=%d nUnits=%d",
                    index, dimx, dimy, nUnits);
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
                    conv(input.activations, kernel, inputMap);
                }
            }

            applyBiasAndNonlinearity();
        }

        // convolves a given kernel over the inputMap and accumulates output to activations
        private void conv(float[] input, int kernel, int inputMap) {
            int startx = halfKernelSize, starty = halfKernelSize, endx = outputMapDim - halfKernelSize, endy = outputMapDim - halfKernelSize;
            for (int xo = startx; xo < endx; xo++) { // index to outputMap
                for (int yo = starty; yo < endy; yo++) {
                    activations[o(kernel, xo, yo)] += convsingle(input, kernel, inputMap, xo, yo);
                }
            }
        }

        // computes single kernal location summed result centered on x,y in inputMap
        private float convsingle(float[] input, int kernel, int inputMap, int x, int y) {
            float sum = 0;
            for (int yy = 0; yy < kernelSize; yy++) {
                int iny = y - halfKernelSize;
                for (int xx = 0; xx < kernelSize; xx++) {
                    int inx = x - halfKernelSize;
                    // debug
                    int idx = i(inputMap, inx, iny);
                    if (idx >= input.length) {
                        log.warning("big index");
                    }
                    sum += kernels[k(kernel, xx, yy)] * input[i(inputMap, inx, iny)];
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
            return map * inputMapLength + y * inputMapDim + x; // TODO check x,y
        }

        // kernel index
        final int k(int kernel, int x, int y) {
            return kernelLength * kernel + kernelSize * y + x;
        }

        // output index
        final int o(int outputMap, int x, int y) {
            return outputMap * outputMapLength + y * outputMapDim + x; // TODO fix
        }

    }

    public class SubsamplingLayer extends Layer {
        int averageOverDim, averageOverNum; // we average over averageOverDim*averageOverDim inputs
        float[] biases;
        int inputMapLength, inputMapDim, outputMapLength, outputMapDim;
        float averageOverMultiplier;
        int nOutputMaps;
        private int activationsLength;

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
            activationsLength=outputMapLength*nOutputMaps;

            if (activations == null || activations.length != activationsLength) {
                activations = new float[activationsLength];
            }

            for (int map = 0; map < nOutputMaps; map++) {
                for (int xo = 0; xo < outputMapDim; xo++) {
                    for (int yo = 0; yo < outputMapDim; yo++) {
                        float s = 0;
                        int startx = xo * averageOverDim, endx = xo * averageOverDim, starty = yo * averageOverDim, endy = yo * averageOverDim;
                        for (int xi = startx; xi < endx; xi++) {
                            for (int yi = starty; yi < endy; yi++) {
                                s += convLayer.activations[convLayer.o(map, xi, yi)];
                            }
                        }
                        // debug
                        int idx=o(map, xo, yo);
                        if(idx>=activations.length){
                            log.warning("overrun output activations");
                        }
                        activations[o(map, xo, yo)] = s * averageOverMultiplier;
                    }
                }
            }
        }

        final int i(int map, int x, int y) {
            return map * inputMapLength + y * inputMapDim + x; // TODO check x,y
        }

        final int o(int map, int x, int y) {
            return map * outputMapLength + y * outputMapDim + x;
        }

        public String toString() {
            return String.format("index=%d Subsamp layer; averageOver=%d biases=float[%d]",
                    index, averageOverDim, biases == null ? 0 : biases.length);
        }
    }

    public class OutputLayer extends Layer {

        public OutputLayer(int index) {
            super(index);
        }

        float[] outputBias;
        float[] outputWeights;

        public String toString() {
            return String.format("Output: bias=float[%d] outputWeights=float[%d]", outputBias.length, outputWeights.length);
        }

        /**
         * Draw with default width and color
         *
         * @param gl
         * @param width width of draw (chip) area in gl pixels
         * @param height of draw (chip) area in gl pixels
         */
        public void draw(GL2 gl, int width, int height) { // width and height are of AEchip draw size in pixels of chip (not screen pixels)

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

        public void draw(GL2 gl, int width, int height, float lineWidth, float[] color) {
            gl.glPushAttrib(GL2GL3.GL_COLOR | GL2.GL_LINE_WIDTH);
            gl.glLineWidth(lineWidth);
            gl.glColor4fv(color, 0);
            draw(gl, width, height);
            gl.glPopAttrib();
        }

    }

    public void loadFromXMLFile(File f) {
        EasyXMLReader networkReader = new EasyXMLReader(f);
        if (!networkReader.hasFile()) {
            log.warning("No file for reader; file=" + networkReader.getFile());
            return;
        }

        netname = networkReader.getRaw("name");
        notes = networkReader.getRaw("notes");
        dob = networkReader.getRaw("dob");
        nettype = networkReader.getRaw("type");
        if (!nettype.equals("cnn")) {
            log.warning("network type is not cnn");
        }
        nLayers = networkReader.getNodeCount("Layer");
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

        outputLayer.outputBias = networkReader.getBase64FloatArr("outputBias");
        outputLayer.outputWeights = networkReader.getBase64FloatArr("outputWeights");
        log.info(toString());
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
