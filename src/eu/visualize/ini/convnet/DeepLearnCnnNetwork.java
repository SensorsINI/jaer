/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.io.File;
import static net.sf.jaer.eventprocessing.EventFilter.log;

/**
 * Simple convolutional neural network (CNN) data structure to hold CNN from
 * Matlab DeepLearnToolbox. Replicates the computation in matlab function
 * cnnff.m, which is as follows:
 * <pre>
 
    function net = cnnff(net, x)
    n = numel(net.layers);
    net.layers{1}.a{1} = x;
    inputmaps = 1;

    for l = 2 : n   %  for each layer
        if strcmp(net.layers{l}.type, 'c')
            %  !!below can probably be handled by insane matrix operations
            for j = 1 : net.layers{l}.outputmaps   %  for each output map
                %  create temp output map
                z = zeros(size(net.layers{l - 1}.a{1}) - [net.layers{l}.kernelsize - 1 net.layers{l}.kernelsize - 1 0]);
                for i = 1 : inputmaps   %  for each input map
                    %  convolve with corresponding kernel and add to temp output map
                    z = z + convn(net.layers{l - 1}.a{i}, net.layers{l}.k{i}{j}, 'valid');
                end
                %  add bias, pass through nonlinearity
                net.layers{l}.a{j} = sigm(z + net.layers{l}.b{j});
            end
            %  set number of input maps to this layers number of outputmaps
            inputmaps = net.layers{l}.outputmaps;
        elseif strcmp(net.layers{l}.type, 's')
            %  downsample
            for j = 1 : inputmaps
                z = convn(net.layers{l - 1}.a{j}, ones(net.layers{l}.scale) / (net.layers{l}.scale ^ 2), 'valid');   %  !! replace with variable
                net.layers{l}.a{j} = z(1 : net.layers{l}.scale : end, 1 : net.layers{l}.scale : end, :);
            end
        end
    end

    %  concatenate all end layer feature maps into vector
    net.fv = [];
    for j = 1 : numel(net.layers{n}.a)
        sa = size(net.layers{n}.a{j});
        net.fv = [net.fv; reshape(net.layers{n}.a{j}, sa(1) * sa(2), sa(3))];
    end
    %  feedforward into output perceptrons
    net.o = sigm(net.ffW * net.fv + repmat(net.ffb, 1, size(net.fv, 2)));

end

 
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
        for(int i=2;i<nLayers;i++){
            layers[i].compute(layers[i-1]);
        }
        outputLayer.compute(layers[nLayers-2]);
        return outputLayer.a;
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
        float[] a;

        /**
         * Computes activations from input layer
         */
        public void compute(Layer input) {

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
         * @param frame the image, indexed by y * width + x
         * @param width the width of image in pixels
         * @return the vector of output values
         * @see #getActivations
         */
        public float[] compute(float[] frame, int width) {
            if (frame == null || width==0 || frame.length % width != 0) {
                throw new IllegalArgumentException("input frame is null or frame vector dimension not a multiple of width=" + width);
            }
            throw new UnsupportedOperationException();
        // subsample frame to input size

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

    public class ConvLayer extends Layer {

        public ConvLayer(int index) {
            super(index);
        }

        int nInputMaps;
        int nOutputMaps;
        int kernelSize;
        float[] biases;
        float[] kernels;

        public String toString() {
            return String.format("index=%d CNN   layer; nInputMaps=%d nOutputMaps=%d kernelSize=%d biases=float[%d] kernels=float[%d]",
                    index, nInputMaps, nOutputMaps, kernelSize, biases == null ? 0 : biases.length, kernels == null ? 0 : kernels.length);
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

    public class OutputLayer extends Layer{

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
                    l.kernels = layerReader.getBase64FloatArr("kernels");
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

}
