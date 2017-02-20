package ch.unizh.ini.jaer.projects.rnnfilter;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import static net.sf.jaer.eventprocessing.EventFilter.log;

import java.io.File;
import java.io.IOException;

import org.jblas.MatrixFunctions;

import eu.visualize.ini.convnet.EasyXMLReader;
import java.util.logging.Level;
import org.jblas.FloatMatrix;

/**
 * Implements an recurrent neural network framework
 * The framework currently supports GRU layer, LSTM layer and Dense layer
 * @author jithendar
 */
public class RNNetwork {

    String netname;
    String notes;
    String dob;
    String nettype;
    
    boolean initialized;

    /**
     * Represents the number of layers in the network
     */
    int nLayers;
    /**
     * Array of layers in the network
     */
    Layer[] layers;
    InputLayer inputLayer; // the first layer in the above array of layers will be this layer
    OutputLayer outputLayer;// the last layer in the layer array will be this layer
    private String xmlFileName = null;

    public RNNetwork() {
        this.initialized = false;
    }
    /**
     * Constructor when given the number of layers, will not be used mostly
     * @param nLayers1 - number of layers in the network
     */
    public RNNetwork(int nLayers1) {
        this.initialized = false;
        this.nLayers = nLayers1;
        this.layers = new Layer[nLayers1];
        this.layers[0] = new InputLayer(0);
        this.layers[nLayers1-1] = new OutputLayer(nLayers1-1);
    }

    /**
     * Computes the output of the network when given an input frame
     * @param input - A one dimensional float array as the input frame
     * @return - the activation of the last layer as a DoubleMatrix (jblas)
     */
    public FloatMatrix output(float[] input) {
        FloatMatrix tempOutput = new FloatMatrix(input);
        this.layers[0].computeFromInput(tempOutput);
        for(int i=1;i<this.nLayers;i++) {
            this.layers[i].compute(this.layers[i-1]);
        }
        return this.layers[this.nLayers-1].output;
    }
    /**
     * Loads the network from an XML file, presently incomplete
     * @param f - the XML file holding the network
     * @throws IOException
     */
    public void loadFromXML(File f) throws IOException{
        EasyXMLReader networkReader;
        networkReader = new EasyXMLReader(f);
        if (!networkReader.hasFile()) {
            log.log(Level.WARNING, "No file for reader; file={0}", networkReader.getFile());
            throw new IOException("Exception thrown in EasyXMLReader for file " + f);
        }
        this.netname = networkReader.getRaw("name");
        this.notes = networkReader.getRaw("notes");
        this.dob = networkReader.getRaw("dob");
        this.nettype = networkReader.getRaw("type");

        log.info(String.format("Reading a network with name=%s, notes=%s, dob=%s nettype=%s", netname, notes, dob, nettype));
        
        this.nLayers = networkReader.getNodeCount("Layer");
        log.log(Level.INFO, "The network has {0} layers", this.nLayers);
        if (this.layers != null) {
            for (int i = 0; i < this.layers.length; i++) {
                this.layers[i] = null;
            }
        }
        this.layers = new Layer[this.nLayers];
        for(int i = 0;i < this.nLayers;i++) {
            //log.log(Level.INFO, "Loading the layer {0}", i);
            EasyXMLReader layerReader = networkReader.getNode("Layer", i);
            int index = layerReader.getInt("index");
            String type = layerReader.getRaw("type");
            switch (type) {
            	case "input": {
                    log.log(Level.INFO, "Loading the input layer {0}", index);
                    this.inputLayer = new InputLayer(index);
                    this.inputLayer.initialize(layerReader.getInt("dim"));
                    this.layers[index] = inputLayer;
                }
                break;
                case "gru":  {
                    log.log(Level.INFO, "Loading the gru layer {0}", index);
                    GRU tmpLayer = new GRU(index);
                    int tmpInput = layerReader.getInt("input_dim");
                    int tmpHidden = layerReader.getInt("output_dim");
                    try {
                        String tmpAct = layerReader.getRaw("activation");
                        if (tmpAct.equalsIgnoreCase("tanh")) {
                            tmpLayer.setActivationFunction(new Tanh());
                        }
                        else if (tmpAct.equalsIgnoreCase("hard_sigmoid")) {
                            tmpLayer.setActivationFunction(new HardSigmoid());
                        } 
                        else if (tmpAct.equalsIgnoreCase("sigmoid")) {
                            tmpLayer.setActivationFunction(new Sigmoid());
                        }
                        else {
                            log.log(Level.WARNING, "An unknown activation function {0} for cadidate activation function encountered in {1}", new Object[]{tmpAct, layerReader.toString()});
                        }   
                    } catch (NullPointerException e) {
                        throw new IOException("Caught " + e.toString() + " while parsing for activationFunction in GRU layer; probably none defined; please define tanh, sigmoid, or relu.");
                    }
                    try {
                        String tmpAct = layerReader.getRaw("inner_activation");
                        if (tmpAct.equalsIgnoreCase("tanh")) {
                            tmpLayer.setUpdateActivation(new Tanh());
                            tmpLayer.setResetActivation(new Tanh());
                        }
                        else if (tmpAct.equalsIgnoreCase("hard_sigmoid")) {
                            tmpLayer.setUpdateActivation(new HardSigmoid());
                            tmpLayer.setResetActivation(new HardSigmoid());
                        }
                        else if (tmpAct.equalsIgnoreCase("sigmoid")) {
                            tmpLayer.setUpdateActivation(new Sigmoid());
                            tmpLayer.setResetActivation(new Sigmoid());
                        }
                        else {
                            log.log(Level.WARNING, "An unknown activation function {0} for inner activation function encountered in {1}", new Object[]{tmpAct, layerReader.toString()});
                        }
                    } catch (NullPointerException e) {
                        throw new IOException("Caught " + e.toString() + " while parsing for activation");
                    }
                    float[] tmpFloat;
                    tmpFloat = readFloatArray(layerReader, "W_z");
                    float[][] tmpWz = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "U_z");
                    float[][] tmpUz = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "W_r");
                    float[][] tmpWr = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "U_r");
                    float[][] tmpUr = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "W_h");
                    float[][] tmpWh = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "U_h");
                    float[][] tmpUh = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "b_z");
                    float[] tmpBz = tmpFloat;
                    tmpFloat = readFloatArray(layerReader, "b_r");
                    float[] tmpBr = tmpFloat;
                    tmpFloat = readFloatArray(layerReader, "b_h");
                    float[] tmpBh = tmpFloat;
                    tmpLayer.initialize(tmpWh, tmpUh, tmpWz, tmpUz, tmpWr, tmpUr, tmpBh, tmpBz, tmpBr);
                    this.layers[index] = tmpLayer;
                }
            	break;
                case "dense": {
                    log.log(Level.INFO, "Loading the dense layer {0}", index);
                    Dense tmpLayer = new Dense(index);
                    int tmpSize = layerReader.getInt("output_dim");
                    try {
                        String tmpAct = layerReader.getRaw("activation");
                        if (tmpAct.equalsIgnoreCase("tanh")) {
                            tmpLayer.setActivationFunction(new Tanh());
                        }
                        else if (tmpAct.equalsIgnoreCase("hard_sigmoid")) {
                            tmpLayer.setActivationFunction(new HardSigmoid());
                        } 
                        else if (tmpAct.equalsIgnoreCase("sigmoid")) {
                            tmpLayer.setActivationFunction(new Sigmoid());
                        }
                        else if (tmpAct.equalsIgnoreCase("relu")) {
                            tmpLayer.setActivationFunction(new Relu());
                        }
                        else {
                            log.log(Level.WARNING, "An unknown activation function {0} for dense layer encountered in {1}", new Object[]{tmpAct, layerReader.toString()});
                        }
                    } catch (NullPointerException e) {
                        throw new IOException("Caught " + e.toString() + " while parsing for activation");
                    }
                    float[] tmpFloat;
                    tmpFloat = this.readFloatArray(layerReader, "W");
                    float[][] tmpW = RNNetwork.convertFlatToDim2(tmpFloat, tmpSize);
                    tmpFloat = this.readFloatArray(layerReader, "b");
                    float[] tmpB = tmpFloat;
                    tmpLayer.initialize(tmpW, tmpB);
                    this.layers[index] = tmpLayer;
                }
                break;
                case "output": {
                    log.log(Level.INFO, "Loading the output layer {0}", index);
                    OutputLayer tmpLayer = new OutputLayer(index);
                    int tmpSize = layerReader.getInt("output_dim");
                    try {
                        String tmpAct = layerReader.getRaw("activation");
                        if (tmpAct.equalsIgnoreCase("softmax")) {
                            tmpLayer.setActivationFunction(new Softmax());
                        }
                        else if (tmpAct.equalsIgnoreCase("softsign")) {
                            tmpLayer.setActivationFunction(new Softsign());
                        }
                        else {
                            log.log(Level.WARNING, "An unknown activation function {0} for dense layer encountered in {1}", new Object[]{tmpAct, layerReader.toString()});
                        }
                    } catch (NullPointerException e) {
                        throw new IOException("Caught " + e.toString() + " while parsing for activation");
                    }
                    float[] tmpFloat;
                    tmpFloat = this.readFloatArray(layerReader, "W");
                    float[][] tmpW = RNNetwork.convertFlatToDim2(tmpFloat, tmpSize);
                    tmpFloat = this.readFloatArray(layerReader, "b");
                    float[] tmpB = tmpFloat;
                    tmpLayer.initialize(tmpW, tmpB);
                    this.outputLayer = tmpLayer;
                    this.layers[index] = this.outputLayer;
                }
                break;
                default:
                    throw new IOException("An unknown layer type \"" + type + "\"");
            }
            log.log(Level.INFO, "Succesfully loaded the layer {0}", i);
        }
        this.setXmlFileName(f.toString());
        this.initialized = true;
        log.log(Level.INFO, "Succesfully loaded the network");
    }


    /**
     * Implements an activation interface to create new activation function classes
     */
    public interface Activation {
        /**
         * Applying the activation on a float matrix
         * @param input - DoubleMatrix containing the linear combination of the previous layer activations/relevant expression
         * @return DoubleMatrix with the activations
         */
        public FloatMatrix apply(FloatMatrix input);
        /**
         * Applying the activation on a float element
         * @param input - FloatMatrix containing the linear combination of the previous layer activations/relevant expression
         * @return DoubleMatrix with the activations
         */
        public float apply(float input);
    }

    /**
     * Implements the sigmoid activation function.
     * The function is 1/(1+exp(x))
     */
    public class Sigmoid implements Activation {

        public Sigmoid() {
        }

        @Override
        public FloatMatrix apply(FloatMatrix input) {
            FloatMatrix denom = new FloatMatrix().copy(input);
            denom.muli(-1);
            MatrixFunctions.expi(denom);
            denom.addi(1);
            FloatMatrix num = FloatMatrix.ones(denom.rows, denom.columns);
            return num.divi(denom);
        }
        
        @Override
        public float apply(float input) {
            return (float) (1.0 / (1.0 + Math.exp(-input)));
        }
    }
    
    public class HardSigmoid implements Activation {
         public HardSigmoid() {
         }

        @Override
        public FloatMatrix apply(FloatMatrix input) {
            FloatMatrix output = new FloatMatrix().copy(input);
            output.muli((float) 0.2);
            output.addi((float) 0.5);
            output.mini(1);
            output.maxi(0);
            return output;
        }

        @Override
        public float apply(float input) {
            return (float) Math.max(0, Math.min(1, input*0.2 + 0.5));
        }
    }
    /**
     * Implements the tanh activation function.
     * The function is (exp(x)-exp(-x))/(exp(x)+exp(-x))
     */
    public class Tanh implements Activation {

        public Tanh() {
        }

        @Override
        public FloatMatrix apply(FloatMatrix input) {
            return MatrixFunctions.tanhi(input);
        }

        @Override
        public float apply(float input) {
            return MatrixFunctions.tanh(input);
        }
    }
    /**
     * Implements the relu activation function.
     * The function is f(x) = x if x > 0, 0 else
     */
    public class Relu implements Activation {

        public Relu() {
        }

        @Override
        public FloatMatrix apply(FloatMatrix input) {
            FloatMatrix output = new FloatMatrix().copy(input);
            output.maxi(0);
            return output;
        }

        @Override
        public float apply(float input) {
            if (input < 0) {
                return 0;
            }
            return input;
        }
    }
    /**
     * Implements the softmax activation function.
     * The function is out_j = exp(in_j)/exp(in).sum
     */
    public class Softmax implements Activation {

        public Softmax() {
        }

        @Override
        public FloatMatrix apply(FloatMatrix input) {
            FloatMatrix exp = new FloatMatrix().copy(input);
            MatrixFunctions.expi(exp);
            return exp.divi(exp.sum());
        }

        @Override
        public float apply(float input) {
            return 1;
        }

    }
    /**
     * Implements the softsign activation function.
     * The function is x/(1+abs(x))
     */
    public class Softsign implements Activation {

        public Softsign() {
        }

        @Override
        public FloatMatrix apply(FloatMatrix input) {
            FloatMatrix denom = new FloatMatrix().copy(input);
            FloatMatrix num = new FloatMatrix().copy(input);
            MatrixFunctions.absi(denom);
            denom.addi(1);
            return num.divi(denom);
        }

        @Override
        public float apply(float input) {
            return (float) (input / (1.0 + Math.abs(input)));
        }

    }
    /**
     * Implements the linear activation function
     */
    public class Linear implements Activation {
        public Linear() {
        }

        @Override
        public FloatMatrix apply(FloatMatrix input) {
            return input;
        }

        @Override
        public float apply(float input) {
            return input;
        }
        
    }

    /**
     * Implements the abstract public class layer, which would then be extended to form different layers
     * The basic attributes are the index of the layer,
     * and the output activations of the layer.
     * Note that the layer is defined to be a flat layer and hence care must be taken to extend Layer to ConvolutionLayer
     */
    abstract public class Layer {

        public Layer (int index) {
            this.index = index;
        }
        /**
         *  Layer index
         */
        int index;
        /**
         *  Activations of the layer
         */
        FloatMatrix output;
        /**
         * Computes the output activations of the layer for a given input activation,
         * @param input - for the case of the InputLayer, it should throw an exception for there is no previous layer, for every other layer it would be activations of the previous layer
         * Internally updates the output variable on arrival of the input
         */
        abstract public void compute(Layer input);

        /**
         * Sets the input to the network as the output of the InputLayer
         * @param input - a FloatMatrix column vector which represents the binned cochlea data, it should throw an exception when called on a different layer
         * Internally updates the output variable of the InputLayer on the arrival of input
         */
        abstract public void computeFromInput(FloatMatrix input);
        
        abstract public void resetLayer();
        
    }
    /**
     * Extends the Layer class to InputLayer,
     * which is the first layer
     */
    public class InputLayer extends Layer {

        public InputLayer(int index) {
            super(index);
        }

        @Override
        public void compute(Layer input) {
           throw new UnsupportedOperationException("Input layer only computes on input frame");
        }
        /**
         * Computes the activations of the InputLayer from the inputs to the network
         * @param input - the input to the network, expectedly the binned frames from the cochlea
         * Updates the activation of the Layer by directly copying the input to it
         */
        @Override
        public void computeFromInput(FloatMatrix input) {
                this.output = input;
        }
        /**
         * Initializes an input layer given the input dimension, sets layer index to 0 and creates a FloatMatrix of size inputDimension x 1
         * @param inputDimension
         */
        public void initialize(int inputDimension) {
                this.output = FloatMatrix.zeros(inputDimension);
        }

        @Override
        public void resetLayer() {
        }

    }
    /**
     * Extends the Layer class to OutputLayer,
     * which is the final layer and would usually be the softmax classifier.
     * In this case, we define it to be a softmax classifier layer.
     */
    public class OutputLayer extends Layer {

        public OutputLayer(int index) {
            super(index);
        }
        /**
         * Stores the activation function for the layer,
         * usually the softmax activation and hence initialised
         */
        private Activation activationFunction = new Softmax();
        /**
         * Stores the connection weights from the previous layer outputs to the present layer
         */
        FloatMatrix weightMatrix;
        /**
         * Stores the biases for the neurons in the present layer
         */
        FloatMatrix biases;
        /**
         * Computes the activation for the final layer
         */
        @Override
        public void compute(Layer input) {
            FloatMatrix temp = this.weightMatrix.mmul(input.output);
            temp.addi(biases);
            this.output = this.getActivationFunction().apply(temp);
        }
        
        
        public void annotateHistogram (GL2 gl, int width, int height) {
            if (this.output == null) {
            } else {
                float dx = (float) (width)/this.output.length;
                float dy = (float) 0.8f * (height);
                
                gl.glBegin(GL.GL_LINE_STRIP);
                for (int i = 0; i < this.output.length; i++) {
                    float tmpOutput = this.output.get(i);
                    float tmpOutputMax = this.output.max();
                    float y_end = (float) (1 + (dy*tmpOutput)/tmpOutputMax); // draws the relative activations of the neurons in the layer
                    float x_start = 1 + (dx * i);
                    float x_end = x_start + dx;
                    gl.glVertex2f(x_start, 1);
                    gl.glVertex2f(x_start, y_end);
                    gl.glVertex2f(x_end, y_end);
                    gl.glVertex2f(x_end, 1);
                }
                gl.glEnd();
            }
        }
        
        /**
         * @return - the weightMatrix of the OutputLayer
         */
        public FloatMatrix getWeightMatrix() {
            return weightMatrix;
        }
        /**
         *
         * @param weightMatrix FloatMatrix of appropriate size
         * Internally sets the weightMatrix of the OutputLayer to the input FloatMatrix
         */
        public void setWeightMatrix(FloatMatrix weightMatrix) {
            this.weightMatrix = weightMatrix;
        }
        /**
         *
         * @return - the biases of the OutputLayer
         */
        public FloatMatrix getBiases() {
            return biases;
        }
        /**
         *
         * @param biases - a float matrix of appropriate size
         */
        public void setBiases(FloatMatrix biases) {
            this.biases = biases;
        }
        /**
         * Initializes the OutputLayer given the weightMatrix and the biases
         * @param weightMatrix1 - 2 dimensional float array containing the weights
         * @param biases1 - 1 dimensional float array containing the biases of the neurons
         */
        public void initialize(float[][] weightMatrix1, float[] biases1) {
            this.weightMatrix = new FloatMatrix(weightMatrix1);
            this.biases = new FloatMatrix(biases1);
            this.output = FloatMatrix.zeros(this.weightMatrix.rows);
        }
        /**
         * Initializes the OutputLayer given the weightMatrix, the biases and the ActivationFunction of the layer
         * @param weightMatrix1 - 2 dimensional float array containing the weights
         * @param biases1 - 1 dimensional float array containing the biases
         * @param activationFunction1 - activation function of the layer
         */
        public void initialize(float[][] weightMatrix1, float[] biases1, Activation activationFunction1) {
            this.weightMatrix = new FloatMatrix(weightMatrix1);
            this.biases = new FloatMatrix(biases1);
            this.output = FloatMatrix.zeros(this.weightMatrix.rows);
            this.setActivationFunction(activationFunction1);
        }

        @Override
        public void computeFromInput(FloatMatrix input) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        /**
         * @return the activationFunction
         */
        public Activation getActivationFunction() {
            return activationFunction;
        }

        /**
         * @param activationFunction the activationFunction to set
         */
        public void setActivationFunction(Activation activationFunction) {
            this.activationFunction = activationFunction;
        }

        @Override
        public void resetLayer() {
            this.output = FloatMatrix.rand(this.weightMatrix.rows);
        }

    }
    /**
     * Extends the Layer class to GRU,
     * which is a recurrent layer, for which a special class is not created
     */
    public class GRU extends Layer {

        public GRU(int index) {
            super(index);
        }

        /**
         * The updateGate decides how much the unit updates it's activation from the previous time step to the present one
         */
        FloatMatrix updateGate;
        /**
         * The resetGate is a way to reset the activation, when 0 it makes the unit as if reading the first symbol of the sequence
         */
        FloatMatrix resetGate;
        /**
         * Matrix to be multiplied to the input while calculating the updateGate
         */
        FloatMatrix updateW;
        /**
         * Matrix to be multiplied to the hidden activation while calculating the updateGate
         */
        FloatMatrix updateU;
        /**
         * Bias matrix for the update date
         */
        FloatMatrix updateBias;
        /**
         * Matrix to be multiplied to the input while calculating the resetGate
         */
        FloatMatrix resetW;
        /**
         * Matrix to be multiplied to the hidden activation while calculating the resetGate
         */
        FloatMatrix resetU;
        /**
         * Bias matrix for the reset gate
         */
        FloatMatrix resetBias;
        /**
         * Matrix to be multiplied to the input while calculating the candidate activations
         */
        FloatMatrix hiddenW;
        /**
         * Matrix to be multiplied to the hidden activation while calculating the candidate activations
         */
        FloatMatrix hiddenU;
        /**
         * Bias matrix for the candidate activation
         */
        FloatMatrix hiddenBias;
        /**
         * Activation function in calculating the activation of the update gate,
         * initialized to be a sigmoid activation
         */
        Activation updateActivation = new Sigmoid();
        /**
         * Activation function in calculating the activation of the reset gate,
         * initialized to be a sigmoid activation
         */
        Activation resetActivation = new Sigmoid();
        /**
         * Activation function in calculating the candidate activation,
         * initialized to be a tanh activation
         */
        Activation activationFunction = new Tanh();
        /**
         * Computes the output of the GRU layer
         */
        @Override
        public void compute(Layer input) {
            //Temporary variable to calculate the updateGate, for input x and previous activation h, calculates updateW*x+updateU*h
            FloatMatrix updateTemp = this.updateW.mmul(input.output);
            updateTemp.addi(this.updateU.mmul(this.output));
            updateTemp.addi(this.updateBias);
            this.updateGate = this.updateActivation.apply(updateTemp);
            // Temporary variable to calculate the resetGate, for input x and previous activation h, calculates resetW*x+resetU*h
            FloatMatrix resetTemp = this.resetW.mmul(input.output);
            resetTemp.addi(this.resetU.mmul(this.output));
            resetTemp.addi(this.resetBias);
            this.resetGate = this.resetActivation.apply(resetTemp);
            // Temporary variable which holds the linear combination of input and hidden activation to calculate the candidate activation
            FloatMatrix candidateActivation = this.hiddenU.mmul(this.resetGate.mul(this.output));
            candidateActivation.addi(this.hiddenW.mmul(input.output));
            candidateActivation.addi(this.hiddenBias);
            // Temporary variable which holds the linear combination of input and candidate activation to calculate the final activation
            FloatMatrix outputTemp = this.updateGate.mul(-1);
            outputTemp.addi(1);
            outputTemp.muli(this.activationFunction.apply(candidateActivation));
            this.output = outputTemp.add(this.updateGate.mul(this.output));
        }
        
        @Override
        public void resetLayer() {
            this.updateGate = FloatMatrix.zeros(this.updateGate.length);
            this.output = FloatMatrix.zeros(this.hiddenU.rows);
            this.resetGate = FloatMatrix.zeros(this.resetGate.length);
        }

        /**
         * Initializes the GRU layer
         * @param hiddenW1 multiplies x_t in the expression to calculate candidate activation
         * @param hiddenU1 multiplies r_t.h_t-1 in the expression to calculate candidate activation
         * @param updateW1 multiplies x_t in the expression to calculate update gate
         * @param updateU1 multiplies h_t-1 in the expression to calculate update gate
         * @param resetW1 multiplies x_t in the expression to calculate reset gate
         * @param resetU1 multiplies h_t-1 in the expression to calculate reset gate
         * @param hiddenBias1 bias values of the neurons to calculate the candidate activation
         * @param updateBias1 bias values of the neurons to calculate the update gate
         * @param resetBias1 bias values of the neurons to calculate the reset gate
         */
        public void initialize(float[][] hiddenW1, float[][] hiddenU1, float[][] updateW1, float[][] updateU1, float[][] resetW1, float[][] resetU1, float[] hiddenBias1, float[] updateBias1, float[] resetBias1) {
            this.hiddenW = new FloatMatrix(hiddenW1);
            this.hiddenU = new FloatMatrix(hiddenU1);
            this.resetW = new FloatMatrix(resetW1);
            this.resetU = new FloatMatrix(resetU1);
            this.updateW = new FloatMatrix(updateW1);
            this.updateU = new FloatMatrix(updateU1);
            this.hiddenBias = new FloatMatrix(hiddenBias1);
            this.updateBias = new FloatMatrix(updateBias1);
            this.resetBias = new FloatMatrix(resetBias1);
            this.output = FloatMatrix.zeros(this.hiddenU.rows);
            this.updateGate = FloatMatrix.zeros(this.hiddenBias.length);
            this.resetGate = FloatMatrix.zeros(this.hiddenBias.length);            
        }

        /**
         * Initializes the GRU layer
         * @param hiddenW1 multiplies x_t in the expression to calculate candidate activation
         * @param hiddenU1 multiplies r_t.h_t-1 in the expression to calculate candidate activation
         * @param updateW1 multiplies x_t in the expression to calculate update gate
         * @param updateU1 multiplies h_t-1 in the expression to calculate update gate
         * @param resetW1 multiplies x_t in the expression to calculate reset gate
         * @param resetU1 multiplies h_t-1 in the expression to calculate reset gate
         * @param hiddenBias1 bias values of the neurons to calculate the candidate activation
         * @param updateBias1 bias values of the neurons to calculate the update gate
         * @param resetBias1 bias values of the neurons to calculate the reset gate
         * @param activationFunction1 activation function to calculate the candidate activation
         * @param updateActivation1 activation function to calculate the update gate
         * @param resetActivation1 activation function to calculate the reset gate
         */
        public void initialize(float[][] hiddenW1, float[][] hiddenU1, float[][] updateW1, float[][] updateU1, float[][] resetW1, float[][] resetU1, float[] hiddenBias1, float[] updateBias1, float[] resetBias1, Activation activationFunction1, Activation updateActivation1, Activation resetActivation1) {
            this.hiddenW = new FloatMatrix(hiddenW1);
            this.hiddenU = new FloatMatrix(hiddenU1);
            this.resetW = new FloatMatrix(resetW1);
            this.resetU = new FloatMatrix(resetU1);
            this.updateW = new FloatMatrix(updateW1);
            this.updateU = new FloatMatrix(updateU1);
            this.hiddenBias = new FloatMatrix(hiddenBias1);
            this.updateBias = new FloatMatrix(updateBias1);
            this.resetBias = new FloatMatrix(resetBias1);
            this.output = FloatMatrix.zeros(this.hiddenU.rows);
            this.activationFunction = activationFunction1;
            this.updateActivation = updateActivation1;
            this.resetActivation = resetActivation1;
            this.updateGate = FloatMatrix.zeros(this.hiddenBias.length);
            this.resetGate = FloatMatrix.zeros(this.hiddenBias.length);            
        }

        /**
         * Gets the matrix to be multiplied to the input while calculating the updateGate
         * @return updateW
         */
        public FloatMatrix getUpdateW() {
            return updateW;
        }
        /**
         * Sets the matrix to be multiplied to the input while calculating the updateGate
         * @param updateW
         */
        public void setUpdateW(FloatMatrix updateW) {
            this.updateW = updateW;
        }
        /**
         * Gets the matrix to be multiplied to the hidden activation while calculating the updateGate
         * @return updateU
         */
        public FloatMatrix getUpdateU() {
            return updateU;
        }
        /**
         * Sets the matrix to be multiplied to the hidden activation while calculating the updateGate
         * @param updateU
         */
        public void setUpdateU(FloatMatrix updateU) {
            this.updateU = updateU;
        }
        /**
         * Gets the matrix to be multiplied to the input while calculating the resetGate
         * @return resetW
         */
        public FloatMatrix getResetW() {
            return resetW;
        }
        /**
         * Sets the matrix to be multiplied to the input while calculating the resetGate
         * @param resetW
         */
        public void setResetW(FloatMatrix resetW) {
            this.resetW = resetW;
        }
        /**
         * Gets the matrix to be multiplied to the hidden activation while calculating the resetGate
         * @return resetU
         */
        public FloatMatrix getResetU() {
            return resetU;
        }
        /**
         * Sets the matrix to be multiplied to the hidden activation while calculating the resetGate
         * @param resetU
         */
        public void setResetU(FloatMatrix resetU) {
            this.resetU = resetU;
        }
        /**
         * Gets the matrix to be multiplied to the input while calculating the candidate activations
         * @return hiddenW
         */
        public FloatMatrix getHiddenW() {
            return hiddenW;
        }
        /**
         * Sets the matrix to be multiplied to the input while calculating the candidate activations
         * @param hiddenW
         */
        public void setHiddenW(FloatMatrix hiddenW) {
            this.hiddenW = hiddenW;
        }
        /**
         * Gets the matrix to be multiplied to the hidden activation while calculating the candidate activations
         * @return hiddenU
         */
        public FloatMatrix getHiddenU() {
            return hiddenU;
        }
        /**
         * Sets the matrix to be multiplied to the hidden activation while calculating the candidate activations
         * @param hiddenU
         */
        public void setHiddenU(FloatMatrix hiddenU) {
            this.hiddenU = hiddenU;
        }
        /**
         * Gets the activation function in calculating the activation of the update gate
         * @return getUpdateActivation
         */
        public Activation getUpdateActivation() {
            return updateActivation;
        }
        /**
         * Sets the activation function in calculating the activation of the update gate
         * @param updateActivation
         */
        public void setUpdateActivation(Activation updateActivation) {
            this.updateActivation = updateActivation;
        }
        /**
         * Gets the activation function in calculating the activation of the reset gate
         * @return resetActivation
         */
        public Activation getResetActivation() {
            return resetActivation;
        }
        /**
         * Sets the activation function in calculating the activation of the reset gate
         * @param resetActivation
         */
        public void setResetActivation(Activation resetActivation) {
            this.resetActivation = resetActivation;
        }
        /**
         * Gets the activation function in calculating the candidate activation
         * @return activationFunction
         */
        public Activation getActivationFunction() {
            return activationFunction;
        }
        /**
         * Set the activation function in calculating the candidate activation
         * @param activationFunction
         */
        public void setActivationFunction(Activation activationFunction) {
            this.activationFunction = activationFunction;
        }

        public FloatMatrix getUpdateBias() {
            return updateBias;
        }

        public void setUpdateBias(FloatMatrix updateBias) {
            this.updateBias = updateBias;
        }

        public FloatMatrix getResetBias() {
            return resetBias;
        }

        public void setResetBias(FloatMatrix resetBias) {
            this.resetBias = resetBias;
        }

        public FloatMatrix getHiddenBias() {
            return hiddenBias;
        }

        public void setHiddenBias(FloatMatrix hiddenBias) {
            this.hiddenBias = hiddenBias;
        }

        @Override
        public void computeFromInput(FloatMatrix input) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }
    /**
     * Extends the Layer class to LSTM,
     * which is a recurrent layer, for which a special class is not created
     */
    public class LSTM extends Layer {

        public LSTM(int index) {
            super(index);
        }
        
        FloatMatrix memoryCell;
        FloatMatrix outputGate;
        
        private FloatMatrix Wo;
        private FloatMatrix Uo;
        private FloatMatrix Vo;
        private FloatMatrix bo;
        private FloatMatrix Wc;
        private FloatMatrix Uc;
        private FloatMatrix bc;
        
        FloatMatrix forgetGate;
        FloatMatrix inputGate;
        
        
        private FloatMatrix Wf;
        private FloatMatrix Uf;
        private FloatMatrix Vf;
        private FloatMatrix bf;
        private FloatMatrix Wi;
        private FloatMatrix Ui;
        private FloatMatrix Vi;
        private FloatMatrix bi;
        
        private Activation newMemoryActivation = new Tanh();
        private Activation outputActivation = new Tanh();
        private Activation outputGateActivation = new Sigmoid();
        private Activation forgetGateActivation = new Sigmoid();
        private Activation inputGateActivation = new Sigmoid();
        
        @Override
        public void compute(Layer input) {
            FloatMatrix forgetTemp = this.getWf().mmul(input.output);
            forgetTemp.addi(this.getUf().mmul(this.output));
            forgetTemp.addi(this.getVf().mmul(this.memoryCell));
            forgetTemp.addi(this.getBf());
            this.forgetGate = this.getForgetGateActivation().apply(forgetTemp);
            FloatMatrix inputTemp = this.getWi().mmul(input.output);
            inputTemp.addi(this.getUi().mmul(this.output));
            inputTemp.addi(this.getVi().mmul(this.memoryCell));
            inputTemp.addi(this.getBi());
            this.inputGate = this.getInputGateActivation().apply(inputTemp);
            FloatMatrix newMemoryTemp = this.getWc().mmul(input.output);
            newMemoryTemp.addi(this.getUc().mmul(this.output));
            newMemoryTemp.addi(this.getBc());
            newMemoryTemp = this.getNewMemoryActivation().apply(newMemoryTemp);
            FloatMatrix memoryTemp = this.forgetGate.mul(this.memoryCell);
            memoryTemp.addi(this.inputGate.mul(newMemoryTemp));
            this.memoryCell = memoryTemp;
            FloatMatrix outputGateTemp = this.getWo().mmul(input.output);
            outputGateTemp.addi(this.getUo().mmul(this.output));
            outputGateTemp.addi(this.getVo().mmul(this.memoryCell));
            outputGateTemp.addi(this.getBo());
            this.outputGate = this.getOutputGateActivation().apply(outputGateTemp);
            this.output = this.outputGate.mul(this.getOutputActivation().apply(this.memoryCell));
        }

        @Override
        public void computeFromInput(FloatMatrix input) {
            throw new UnsupportedOperationException("Not supported on any layer but the input layer"); //To change body of generated methods, choose Tools | Templates.
        }
        /**
         * Initializes the LSTM layer
         * @param Wo1
         * @param Uo1
         * @param Vo1
         * @param Wf1
         * @param Uf1
         * @param Vf1
         * @param Wi1
         * @param Ui1
         * @param Vi1
         * @param Wc1
         * @param Uc1
         * @param bo1
         * @param bf1
         * @param bi1
         * @param bc1 
         */
        public void initialize(float[][] Wo1, float[][] Uo1, float[][] Vo1, float[][] Wf1, float[][] Uf1, float[][] Vf1, float[][] Wi1, float[][] Ui1, float[][] Vi1, float[][] Wc1, float[][] Uc1, float[] bo1, float[] bf1, float[] bi1, float[] bc1) {
            this.setWo(new FloatMatrix(Wo1));
            this.setUo(new FloatMatrix(Uo1));
            this.setVo(new FloatMatrix(Vo1));
            this.setWf(new FloatMatrix(Wf1));
            this.setUf(new FloatMatrix(Uf1));
            this.setVf(new FloatMatrix(Vf1));
            this.setWi(new FloatMatrix(Wi1));
            this.setUi(new FloatMatrix(Ui1));
            this.setVi(new FloatMatrix(Vi1));
            this.setWc(new FloatMatrix(Wc1));
            this.setUc(new FloatMatrix(Uc1));
            this.setBo(new FloatMatrix(bo1));
            this.setBf(new FloatMatrix(bf1));
            this.setBi(new FloatMatrix(bi1));
            this.setBc(new FloatMatrix(bc1));
            this.output = FloatMatrix.zeros(this.getUo().rows);
            this.memoryCell = FloatMatrix.zeros(this.getUo().rows);
            this.forgetGate = FloatMatrix.zeros(this.getUo().rows);
            this.inputGate = FloatMatrix.zeros(this.getUo().rows);
            this.outputGate = FloatMatrix.zeros(this.getUo().rows);
        }

        /**
         * @return the Wo
         */
        public FloatMatrix getWo() {
            return Wo;
        }

        /**
         * @param Wo the Wo to set
         */
        public void setWo(FloatMatrix Wo) {
            this.Wo = Wo;
        }

        /**
         * @return the Uo
         */
        public FloatMatrix getUo() {
            return Uo;
        }

        /**
         * @param Uo the Uo to set
         */
        public void setUo(FloatMatrix Uo) {
            this.Uo = Uo;
        }

        /**
         * @return the Vo
         */
        public FloatMatrix getVo() {
            return Vo;
        }

        /**
         * @param Vo the Vo to set
         */
        public void setVo(FloatMatrix Vo) {
            this.Vo = Vo;
        }

        /**
         * @return the bo
         */
        public FloatMatrix getBo() {
            return bo;
        }

        /**
         * @param bo the bo to set
         */
        public void setBo(FloatMatrix bo) {
            this.bo = bo;
        }

        /**
         * @return the Wc
         */
        public FloatMatrix getWc() {
            return Wc;
        }

        /**
         * @param Wc the Wc to set
         */
        public void setWc(FloatMatrix Wc) {
            this.Wc = Wc;
        }

        /**
         * @return the Uc
         */
        public FloatMatrix getUc() {
            return Uc;
        }

        /**
         * @param Uc the Uc to set
         */
        public void setUc(FloatMatrix Uc) {
            this.Uc = Uc;
        }

        /**
         * @return the bc
         */
        public FloatMatrix getBc() {
            return bc;
        }

        /**
         * @param bc the bc to set
         */
        public void setBc(FloatMatrix bc) {
            this.bc = bc;
        }

        /**
         * @return the Wf
         */
        public FloatMatrix getWf() {
            return Wf;
        }

        /**
         * @param Wf the Wf to set
         */
        public void setWf(FloatMatrix Wf) {
            this.Wf = Wf;
        }

        /**
         * @return the Uf
         */
        public FloatMatrix getUf() {
            return Uf;
        }

        /**
         * @param Uf the Uf to set
         */
        public void setUf(FloatMatrix Uf) {
            this.Uf = Uf;
        }

        /**
         * @return the Vf
         */
        public FloatMatrix getVf() {
            return Vf;
        }

        /**
         * @param Vf the Vf to set
         */
        public void setVf(FloatMatrix Vf) {
            this.Vf = Vf;
        }

        /**
         * @return the bf
         */
        public FloatMatrix getBf() {
            return bf;
        }

        /**
         * @param bf the bf to set
         */
        public void setBf(FloatMatrix bf) {
            this.bf = bf;
        }

        /**
         * @return the Wi
         */
        public FloatMatrix getWi() {
            return Wi;
        }

        /**
         * @param Wi the Wi to set
         */
        public void setWi(FloatMatrix Wi) {
            this.Wi = Wi;
        }

        /**
         * @return the Ui
         */
        public FloatMatrix getUi() {
            return Ui;
        }

        /**
         * @param Ui the Ui to set
         */
        public void setUi(FloatMatrix Ui) {
            this.Ui = Ui;
        }

        /**
         * @return the Vi
         */
        public FloatMatrix getVi() {
            return Vi;
        }

        /**
         * @param Vi the Vi to set
         */
        public void setVi(FloatMatrix Vi) {
            this.Vi = Vi;
        }

        /**
         * @return the bi
         */
        public FloatMatrix getBi() {
            return bi;
        }

        /**
         * @param bi the bi to set
         */
        public void setBi(FloatMatrix bi) {
            this.bi = bi;
        }

        /**
         * @return the newMemoryActivation
         */
        public Activation getNewMemoryActivation() {
            return newMemoryActivation;
        }

        /**
         * @param newMemoryActivation the newMemoryActivation to set
         */
        public void setNewMemoryActivation(Activation newMemoryActivation) {
            this.newMemoryActivation = newMemoryActivation;
        }

        /**
         * @return the outputActivation
         */
        public Activation getOutputActivation() {
            return outputActivation;
        }

        /**
         * @param outputActivation the outputActivation to set
         */
        public void setOutputActivation(Activation outputActivation) {
            this.outputActivation = outputActivation;
        }

        /**
         * @return the outputGateActivation
         */
        public Activation getOutputGateActivation() {
            return outputGateActivation;
        }

        /**
         * @param outputGateActivation the outputGateActivation to set
         */
        public void setOutputGateActivation(Activation outputGateActivation) {
            this.outputGateActivation = outputGateActivation;
        }

        /**
         * @return the forgetGateActivation
         */
        public Activation getForgetGateActivation() {
            return forgetGateActivation;
        }

        /**
         * @param forgetGateActivation the forgetGateActivation to set
         */
        public void setForgetGateActivation(Activation forgetGateActivation) {
            this.forgetGateActivation = forgetGateActivation;
        }

        /**
         * @return the inputGateActivation
         */
        public Activation getInputGateActivation() {
            return inputGateActivation;
        }

        /**
         * @param inputGateActivation the inputGateActivation to set
         */
        public void setInputGateActivation(Activation inputGateActivation) {
            this.inputGateActivation = inputGateActivation;
        }

        @Override
        public void resetLayer() {
            this.output = FloatMatrix.zeros(this.Uo.rows);
            this.memoryCell = FloatMatrix.zeros(this.Uc.rows);
            this.forgetGate = FloatMatrix.zeros(this.Uf.rows);
            this.inputGate = FloatMatrix.zeros(this.Ui.rows);
        }
        
    }
    /**
     * Extends the Layer class to Dense,
     * which is the usual fully connected hidden layer.
     */
    public class Dense extends Layer {

        public Dense(int index) {
            super(index);
        }

        /**
         * Holds the connection weights from the previous layer
         */
        FloatMatrix weightMatrix;
        /**
         * Holds the biases of the neurons in the layer
         */
        FloatMatrix biases;
        /**
         * Activation for the Dense layer,
         * usually a relu function and hence is initialized so
         */
        Activation activationFunction = new Relu();
        /**
         * Computes the output of the Dense layer
         */
        @Override
        public void compute(Layer input) {
            FloatMatrix tempOutput = this.weightMatrix.mmul(input.output);
            tempOutput.addi(biases);
            this.output = this.activationFunction.apply(tempOutput);
        }

        /**
         * Initializes the dense layer given weightMatrix
         * @param weightMatrix1 - weightMatrix which multiplies the previous input layer
         * @param biases1 - biases for the neurons in the layer
         */
        public void initialize(float[][] weightMatrix1, float[] biases1) {
            this.weightMatrix = new FloatMatrix(weightMatrix1);
            this.biases = new FloatMatrix(biases1);
            this.output = FloatMatrix.zeros(this.weightMatrix.rows);
        }
        /**
         * Initializes the dense layer given weightMatrix and the activation function
         * @param weightMatrix1 - weightMatrix which multiplies the previous input layer
         * @param biases1 - biases for the neurons in the layer
         * @param activationFunction1 - activation function of the layer
         */
        public void initialize(float[][] weightMatrix1, float[][] biases1, Activation activationFunction1) {
            this.weightMatrix = new FloatMatrix(weightMatrix1);
            this.biases = new FloatMatrix(biases1);
            this.output = FloatMatrix.zeros(this.weightMatrix.rows);
            this.activationFunction = activationFunction1;
        }
        
        public FloatMatrix getWeightMatrix() {
            return weightMatrix;
        }

        public void setWeightMatrix(FloatMatrix weightMatrix) {
            this.weightMatrix = weightMatrix;
        }

        public Activation getActivationFunction() {
            return activationFunction;
        }

        public void setActivationFunction(Activation activationFunction) {
            this.activationFunction = activationFunction;
        }

        public FloatMatrix getBiases() {
            return biases;
        }

        public void setBiases(FloatMatrix biases) {
            this.biases = biases;
        }

        @Override
        public void computeFromInput(FloatMatrix input) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void resetLayer() {
            this.output = FloatMatrix.zeros(this.biases.length);
        }

    }
    /**
     * Converts a 1D float array to a 2D float array, given the reshape parameter
     * @param tmpW
     * @param hiddenSize - 2nd dimension of the output 2D array
     * @return 
     */
    public static float[][] convertFlatToDim2 (float[] tmpW, int hiddenSize) {
        int inputSize = tmpW.length/hiddenSize;
        float[][] output = new float[inputSize][hiddenSize];
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                output[i][j] = tmpW[i*hiddenSize+j];
            }
        }
        output = RNNetwork.transposeFloatMatrix(output);
        return output;
    }
    /**
     * Transposes a 2D float array
     * @param input
     * @return 
     */
    public static float[][] transposeFloatMatrix (float[][] input) {
        int rows = input.length;
        int columns = input[0].length;
        float[][] output = new float[columns][rows];
        for(int i=0; i<rows; i++) {
            for(int j=0; j<columns; j++) {
                output[j][i] = input[i][j];
            }
        }
        return output;
    }
    /**
     * Converts 1D float array to 1D float array
     * @param input
     * @return 
     */
    public static float[] convertFloatsToFloatsDim1(float[] input) {
        if (input == null) {
            return null; 
        }
        float[] output = new float[input.length];
        System.arraycopy(input, 0, output, 0, input.length);
        return output;
    }
    /**
     * Converts a 2D float array to 2D float array
     * @param input
     * @return 
     */
    public static float[][] convertFloatsToFloatsDim2(float[][] input) {
        if (input == null) {
            return null; 
        }
        float[][] output = new float[input.length][input[0].length];
        for (int i = 0; i < input.length; i++) {
            System.arraycopy(input[i], 0, output[i], 0, input[i].length);
        }
        return output;
    }
    /**
     * Reads float arrays from the XML file; copied directly from Tobi's XML parser written in the convnet filter
     * @param layerReader
     * @param name
     * @return 
     */
    private float[] readFloatArray(EasyXMLReader layerReader, String name) {
        String dt = layerReader.getAttrValue(name, "dt");
        float[] f = null;
        switch (dt) {
            case "base64-single":
                f = layerReader.getBase64FloatArr(name);
                break;
            case "ASCII-float32":
                f = layerReader.getAsciiFloatArr(name);
                break;
            default:
                throw new RuntimeException("Given a bad datatype dt=" + dt + "; should be base64-single or ASCII-float32");
        }
        return f;
    }
    
    /**
     * Resets all the layers in the network
     */
    public void resetNetworkLayers () {
        for(int i=0;i<this.nLayers;i++) {
            this.layers[i].resetLayer();
        }
    }
    
    /**
     * @return the xmlFileName
     */
    public String getXmlFileName() {
        return xmlFileName;
    }
    /**
     * @param xmlFileName the xmlFileName to set
     */
    public void setXmlFileName(String xmlFileName) {
        this.xmlFileName = xmlFileName;
    }
    
}
