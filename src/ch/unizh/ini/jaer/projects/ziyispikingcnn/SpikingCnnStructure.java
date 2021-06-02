package ch.unizh.ini.jaer.projects.ziyispikingcnn;


import java.util.ArrayList;
import java.util.List;

/**
 * Created by ziyihua on 29/03/16.
 *
 * The class SpikingCnnStructure builds a structure that stores kernels, biases, spikes, membrane potential and
 * refractory period for each layer of neurons.
 *
 */
public class SpikingCnnStructure {

    /**
     * The class Network represents a spiking ConvNet. It comprises of class Layer, which can be adapted to input layer,
     * convolutional layer and subsampling layer.
     *
     * The output layer comprises of fcBias (fully connected bias), fcWeights (fully connected weights), fVec (feature vector), outMemPot
     * (membrane potential of output neurons), outRefracEnd (time point when the refractory period ends for output neurons),
     * outSumSpikes (sum of spikes in the past for output neurons) and outSpikes (spikes of output neurons).
     *
     * The output layer has a much simpler structure than the other layers so it does not belong to the class Layer.
     *
     */

    public static class Network{

        public Network(){layers = new ArrayList<Layer>();}

        List<Layer> layers;

        float[] fcBias;
        float[][] fcWeights;
        float[] fVec;
        float[] outMemPot;
        float[] outRefracEnd;
        double[] outSumSpikes;
        int[] outSpikes;
    }

    /**
     * The class Layer stores b (bias), kernel (list of kernels), memPot (membrane potential), refracEnd (time point
     * when the refractory period ends) and spikes (list of spikes for neurons) in a certain layer.
     *
     * This layer cound be input layer, convolutional layer or subsampling layer depending on information given.
     *
     * Variables:
     *
     * membranePot -- stores membrane potential for each neuron in a layer. membranePot is used in propogateSpikingCnn
     * while memPot is used in propogateBatchSpikingCnn.
     *
     * type -- "i" = input, "c" = convolutional, "s" = subsampling.
     *
     * outMaps -- number of output maps
     *
     * inMaps -- number of input maps
     *
     * kernelSize -- size of kernels e.g. 5 means 5x5
     *
     * scale -- size of the subsampling window e.g. 2 means 2x2
     *
     * dimX, dimY -- dimensions of output maps, normally dimX=dimY
     *
     */
    public static class Layer{

        public Layer(){
            kernel = new ArrayList<>();
            refracEnd = new ArrayList<>();
            memPot = new ArrayList<>();
            spikes = new ArrayList<>();
        }

        float[] bias;
        List<float[][]> kernel;
        List<float[][]> memPot;
        List<float[][]> refracEnd;
        List<float[][]> spikes;

        float[][] membranePot;
        String type;
        int outMaps;
        int inMaps;
        int kernelSize;
        int scale;
        int dimX;
        int dimY;

    }
}
