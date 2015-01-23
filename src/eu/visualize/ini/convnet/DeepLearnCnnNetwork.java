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
import javax.swing.JFrame;
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

    private boolean showActivations = false;
    private JFrame apsFrame = null;
    public ImageDisplay activationsDisplay;
    private ImageDisplay.Legend apsDisplayLegend;

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
        for (int i = 2; i < nLayers; i++) {
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

        /**
         * Computes activations from input layer
         */
        public void compute(Layer input) {

        }

        public void display(ImageDisplay imageDisplay) {
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
     * A convolutional layer
     */
    public class ConvLayer extends Layer {

        public ConvLayer(int index) {
            super(index);
        }

        int nInputMaps;
        int nOutputMaps;
        int kernelSize;
        float[] biases;
        float[][] kernels;

        public String toString() {
            return String.format("index=%d CNN   layer; nInputMaps=%d nOutputMaps=%d kernelSize=%d biases=float[%d] kernels=float[%d]",
                    index, nInputMaps, nOutputMaps, kernelSize, biases == null ? 0 : biases.length, kernels == null ? 0 : kernels.length);
        }

        /**
         * Computes convolutions of input kernels with input maps
         *
         * @param input the input to this layer, represented by nInputMaps on
         * the activations array
         */
        @Override
        public void compute(Layer input) {

            applyBiasAndNonlinearity();
        }

        private void applyBiasAndNonlinearity() {
            for (int i = 0; i < activations.length; i++) {
                activations[i] = sigm(activations[i] + biases[i]);
            }

        }

    }

    public class SubsamplingLayer extends Layer {

        public SubsamplingLayer(int index) {
            super(index);
        }

        int averageOver;
        float[] biases;

        public String toString() {
            return String.format("index=%d Subsamp layer; averageOver=%d biases=float[%d]",
                    index, averageOver, biases == null ? 0 : biases.length);
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
                    float[] kflat = layerReader.getBase64FloatArr("kernels");
                    int kNum = l.kernelSize * l.kernelSize;
                    l.kernels = new float[l.nOutputMaps][kNum];
                    int idx = 0;
                    for (int kn = 0; kn < l.nOutputMaps; kn++) {
                        for (int kidx = 0; kidx < kNum; kidx++) {
                            l.kernels[kn][kidx] = kflat[idx];
                        }
                    }
                }
                break;
                case "s": {
                    SubsamplingLayer l = new SubsamplingLayer(index);
                    layers[index] = l;
                    l.averageOver = layerReader.getInt("averageOver");
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

    //    private void showActivations() {
//        if (!isShowActivations()) {
//            return;
//        }
//        if (apsFrame == null) {
//            activationsDisplay = ImageDisplay.createOpenGLCanvas();
//            apsFrame = new JFrame("APS Frame");
//            apsFrame.setPreferredSize(new Dimension(200,200));
//            apsFrame.getContentPane().add(activationsDisplay, BorderLayout.CENTER);
//            apsFrame.pack();
//            apsFrame.addWindowListener(new WindowAdapter() {
//                public void windowClosing(WindowEvent e) {
//                    setShowActivations(false);
//                }
//            });
//            apsDisplayLegend = activationsDisplay.addLegend("", 0, 0);
//            float[] displayColor = new float[3];
//            displayColor[0] = 1.0f;
//            displayColor[1] = 1.0f;
//            displayColor[2] = 1.0f;
//            apsDisplayLegend.color = displayColor;
//        }
//    }
//    /**
//     * @return the showActivations
//     */
//    public boolean isShowActivations() {
//        return showActivations;
//    }
//
//    /**
//     * @param showActivations the showActivations to set
//     */
//    public void setShowActivations(boolean showActivations) {
//        this.showActivations = showActivations;
//    }
}
