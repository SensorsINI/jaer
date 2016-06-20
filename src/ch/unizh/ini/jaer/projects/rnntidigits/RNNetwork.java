package ch.unizh.ini.jaer.projects.rnntidigits;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import static net.sf.jaer.eventprocessing.EventFilter.log;

import java.io.File;
import java.io.IOException;

import org.jblas.DoubleMatrix;
import org.jblas.MatrixFunctions;

import eu.visualize.ini.convnet.EasyXMLReader;
import java.util.logging.Level;

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
     * @param input - A one dimensional double array as the input frame
     * @return - the activation of the last layer as a DoubleMatrix (jblas)
     */
    public DoubleMatrix output(double[] input) {
        DoubleMatrix tempOutput = new DoubleMatrix(input);
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
            log.log(Level.INFO, "Loading the layer {0}", i);
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
                    double[][] tmpWz = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "U_z");
                    double[][] tmpUz = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "W_r");
                    double[][] tmpWr = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "U_r");
                    double[][] tmpUr = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "W_h");
                    double[][] tmpWh = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "U_h");
                    double[][] tmpUh = RNNetwork.convertFlatToDim2(tmpFloat,tmpHidden);
                    tmpFloat = readFloatArray(layerReader, "b_z");
                    double[] tmpBz = RNNetwork.convertFloatsToDoublesDim1(tmpFloat);
                    tmpFloat = readFloatArray(layerReader, "b_r");
                    double[] tmpBr = RNNetwork.convertFloatsToDoublesDim1(tmpFloat);
                    tmpFloat = readFloatArray(layerReader, "b_h");
                    double[] tmpBh = RNNetwork.convertFloatsToDoublesDim1(tmpFloat);
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
                    double[][] tmpW = RNNetwork.convertFlatToDim2(tmpFloat, tmpSize);
                    tmpFloat = this.readFloatArray(layerReader, "b");
                    double[] tmpB = RNNetwork.convertFloatsToDoublesDim1(tmpFloat);
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
                    double[][] tmpW = RNNetwork.convertFlatToDim2(tmpFloat, tmpSize);
                    tmpFloat = this.readFloatArray(layerReader, "b");
                    double[] tmpB = RNNetwork.convertFloatsToDoublesDim1(tmpFloat);
                    tmpLayer.initialize(tmpW, tmpB);
                    this.outputLayer = tmpLayer;
                    this.layers[index] = this.outputLayer;
                }
                break;
                default:
                    throw new IOException("An unknown layer type \"" + type + "\"");
            }

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
         * Applying the activation on a double matrix
         * @param input - DoubleMatrix containing the linear combination of the previous layer activations/relevant expression
         * @return DoubleMatrix with the activations
         */
        public DoubleMatrix apply(DoubleMatrix input);
        /**
         * Applying the activation on a double element
         * @param input - DoubleMatrix containing the linear combination of the previous layer activations/relevant expression
         * @return DoubleMatrix with the activations
         */
        public double apply(double input);
    }

    /**
     * Implements the sigmoid activation function.
     * The function is 1/(1+exp(x))
     */
    public class Sigmoid implements Activation {

        public Sigmoid() {
        }

        @Override
        public DoubleMatrix apply(DoubleMatrix input) {
            DoubleMatrix denom = new DoubleMatrix().copy(input);
            denom.muli(-1);
            MatrixFunctions.expi(denom);
            denom.addi(1);
            DoubleMatrix num = DoubleMatrix.ones(denom.rows, denom.columns);
            return num.divi(denom);
        }
        
        @Override
        public double apply(double input) {
            return (1.0 / (1.0 + Math.exp(-input)));
        }
    }
    
    public class HardSigmoid implements Activation {
         public HardSigmoid() {
         }

        @Override
        public DoubleMatrix apply(DoubleMatrix input) {
            DoubleMatrix output = new DoubleMatrix().copy(input);
            output.muli(0.2);
            output.addi(0.5);
            output.mini(1);
            output.maxi(0);
            return output;
        }

        @Override
        public double apply(double input) {
            return Math.max(0, Math.min(1, input*0.2 + 0.5));
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
        public DoubleMatrix apply(DoubleMatrix input) {
            return MatrixFunctions.tanhi(input);
        }

        @Override
        public double apply(double input) {
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
        public DoubleMatrix apply(DoubleMatrix input) {
            DoubleMatrix output = new DoubleMatrix().copy(input);
            output.maxi(0);
            return output;
        }

        @Override
        public double apply(double input) {
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
        public DoubleMatrix apply(DoubleMatrix input) {
            DoubleMatrix exp = new DoubleMatrix().copy(input);
            MatrixFunctions.expi(exp);
            return exp.divi(exp.sum());
        }

        @Override
        public double apply(double input) {
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
        public DoubleMatrix apply(DoubleMatrix input) {
            DoubleMatrix denom = new DoubleMatrix().copy(input);
            DoubleMatrix num = new DoubleMatrix().copy(input);
            MatrixFunctions.absi(denom);
            denom.addi(1);
            return num.divi(denom);
        }

        @Override
        public double apply(double input) {
            return (input / (1.0 + Math.abs(input)));
        }

    }
    /**
     * Implements the linear activation function
     */
    public class Linear implements Activation {
        public Linear() {
        }

        @Override
        public DoubleMatrix apply(DoubleMatrix input) {
            return input;
        }

        @Override
        public double apply(double input) {
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
        DoubleMatrix output;
        /**
         * Computes the output activations of the layer for a given input activation,
         * @param input - for the case of the InputLayer, it should throw an exception for there is no previous layer, for every other layer it would be activations of the previous layer
         * Internally updates the output variable on arrival of the input
         */
        abstract public void compute(Layer input);

        /**
         * Sets the input to the network as the output of the InputLayer
         * @param input - a DoubleMatrix column vector which represents the binned cochlea data, it should throw an exception when called on a different layer
         * Internally updates the output variable of the InputLayer on the arrival of input
         */
        abstract public void computeFromInput(DoubleMatrix input);
        
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
        public void computeFromInput(DoubleMatrix input) {
                this.output = input;
        }
        /**
         * Initializes an input layer given the input dimension, sets layer index to 0 and creates a DoubleMatrix of size inputDimension x 1
         * @param inputDimension
         */
        public void initialize(int inputDimension) {
                this.output = DoubleMatrix.zeros(inputDimension);
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
        DoubleMatrix weightMatrix;
        /**
         * Stores the biases for the neurons in the present layer
         */
        DoubleMatrix biases;
        /**
         * Computes the activation for the final layer
         */
        @Override
        public void compute(Layer input) {
            DoubleMatrix temp = this.weightMatrix.mmul(input.output);
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
                    double tmpOutput = this.output.get(i);
                    double tmpOutputMax = this.output.max();
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
        public DoubleMatrix getWeightMatrix() {
            return weightMatrix;
        }
        /**
         *
         * @param weightMatrix DoubleMatrix of appropriate size
         * Internally sets the weightMatrix of the OutputLayer to the input DoubleMatrix
         */
        public void setWeightMatrix(DoubleMatrix weightMatrix) {
            this.weightMatrix = weightMatrix;
        }
        /**
         *
         * @return - the biases of the OutputLayer
         */
        public DoubleMatrix getBiases() {
            return biases;
        }
        /**
         *
         * @param biases - a double matrix of appropriate size
         */
        public void setBiases(DoubleMatrix biases) {
            this.biases = biases;
        }
        /**
         * Initializes the OutputLayer given the weightMatrix and the biases
         * @param weightMatrix1 - 2 dimensional double array containing the weights
         * @param biases1 - 1 dimensional double array containing the biases of the neurons
         */
        public void initialize(double[][] weightMatrix1, double[] biases1) {
            this.weightMatrix = new DoubleMatrix(weightMatrix1);
            this.biases = new DoubleMatrix(biases1);
            this.output = DoubleMatrix.zeros(this.weightMatrix.rows);
        }
        /**
         * Initializes the OutputLayer given the weightMatrix, the biases and the ActivationFunction of the layer
         * @param weightMatrix1 - 2 dimensional double array containing the weights
         * @param biases1 - 1 dimensional double array containing the biases
         * @param activationFunction1 - activation function of the layer
         */
        public void initialize(double[][] weightMatrix1, double[] biases1, Activation activationFunction1) {
            this.weightMatrix = new DoubleMatrix(weightMatrix1);
            this.biases = new DoubleMatrix(biases1);
            this.output = DoubleMatrix.zeros(this.weightMatrix.rows);
            this.setActivationFunction(activationFunction1);
        }

        @Override
        public void computeFromInput(DoubleMatrix input) {
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
            this.output = DoubleMatrix.rand(this.weightMatrix.rows);
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
        DoubleMatrix updateGate;
        /**
         * The resetGate is a way to reset the activation, when 0 it makes the unit as if reading the first symbol of the sequence
         */
        DoubleMatrix resetGate;
        /**
         * Matrix to be multiplied to the input while calculating the updateGate
         */
        DoubleMatrix updateW;
        /**
         * Matrix to be multiplied to the hidden activation while calculating the updateGate
         */
        DoubleMatrix updateU;
        /**
         * Bias matrix for the update date
         */
        DoubleMatrix updateBias;
        /**
         * Matrix to be multiplied to the input while calculating the resetGate
         */
        DoubleMatrix resetW;
        /**
         * Matrix to be multiplied to the hidden activation while calculating the resetGate
         */
        DoubleMatrix resetU;
        /**
         * Bias matrix for the reset gate
         */
        DoubleMatrix resetBias;
        /**
         * Matrix to be multiplied to the input while calculating the candidate activations
         */
        DoubleMatrix hiddenW;
        /**
         * Matrix to be multiplied to the hidden activation while calculating the candidate activations
         */
        DoubleMatrix hiddenU;
        /**
         * Bias matrix for the candidate activation
         */
        DoubleMatrix hiddenBias;
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
            DoubleMatrix updateTemp = this.updateW.mmul(input.output);
            updateTemp.addi(this.updateU.mmul(this.output));
            updateTemp.addi(this.updateBias);
            this.updateGate = this.updateActivation.apply(updateTemp);
            // Temporary variable to calculate the resetGate, for input x and previous activation h, calculates resetW*x+resetU*h
            DoubleMatrix resetTemp = this.resetW.mmul(input.output);
            resetTemp.addi(this.resetU.mmul(this.output));
            resetTemp.addi(this.resetBias);
            this.resetGate = this.resetActivation.apply(resetTemp);
            // Temporary variable which holds the linear combination of input and hidden activation to calculate the candidate activation
            DoubleMatrix candidateActivation = this.hiddenU.mmul(this.resetGate.mul(this.output));
            candidateActivation.addi(this.hiddenW.mmul(input.output));
            candidateActivation.addi(this.hiddenBias);
            // Temporary variable which holds the linear combination of input and candidate activation to calculate the final activation
            DoubleMatrix outputTemp = this.updateGate.mul(-1);
            outputTemp.addi(1);
            outputTemp.muli(this.activationFunction.apply(candidateActivation));
            this.output = outputTemp.add(this.updateGate.mul(this.output));
        }
        
        @Override
        public void resetLayer() {
            this.updateGate = DoubleMatrix.zeros(this.updateGate.length);
            this.output = DoubleMatrix.zeros(this.hiddenU.rows);
            this.resetGate = DoubleMatrix.zeros(this.resetGate.length);
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
        public void initialize(double[][] hiddenW1, double[][] hiddenU1, double[][] updateW1, double[][] updateU1, double[][] resetW1, double[][] resetU1, double[] hiddenBias1, double[] updateBias1, double[] resetBias1) {
            this.hiddenW = new DoubleMatrix(hiddenW1);
            this.hiddenU = new DoubleMatrix(hiddenU1);
            this.resetW = new DoubleMatrix(resetW1);
            this.resetU = new DoubleMatrix(resetU1);
            this.updateW = new DoubleMatrix(updateW1);
            this.updateU = new DoubleMatrix(updateU1);
            this.hiddenBias = new DoubleMatrix(hiddenBias1);
            this.updateBias = new DoubleMatrix(updateBias1);
            this.resetBias = new DoubleMatrix(resetBias1);
            this.output = DoubleMatrix.zeros(this.hiddenU.rows);
            this.updateGate = DoubleMatrix.zeros(this.hiddenBias.length);
            this.resetGate = DoubleMatrix.zeros(this.hiddenBias.length);            
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
        public void initialize(double[][] hiddenW1, double[][] hiddenU1, double[][] updateW1, double[][] updateU1, double[][] resetW1, double[][] resetU1, double[] hiddenBias1, double[] updateBias1, double[] resetBias1, Activation activationFunction1, Activation updateActivation1, Activation resetActivation1) {
            this.hiddenW = new DoubleMatrix(hiddenW1);
            this.hiddenU = new DoubleMatrix(hiddenU1);
            this.resetW = new DoubleMatrix(resetW1);
            this.resetU = new DoubleMatrix(resetU1);
            this.updateW = new DoubleMatrix(updateW1);
            this.updateU = new DoubleMatrix(updateU1);
            this.hiddenBias = new DoubleMatrix(hiddenBias1);
            this.updateBias = new DoubleMatrix(updateBias1);
            this.resetBias = new DoubleMatrix(resetBias1);
            this.output = DoubleMatrix.zeros(this.hiddenU.rows);
            this.activationFunction = activationFunction1;
            this.updateActivation = updateActivation1;
            this.resetActivation = resetActivation1;
            this.updateGate = DoubleMatrix.zeros(this.hiddenBias.length);
            this.resetGate = DoubleMatrix.zeros(this.hiddenBias.length);            
        }

        /**
         * Gets the matrix to be multiplied to the input while calculating the updateGate
         * @return updateW
         */
        public DoubleMatrix getUpdateW() {
            return updateW;
        }
        /**
         * Sets the matrix to be multiplied to the input while calculating the updateGate
         * @param updateW
         */
        public void setUpdateW(DoubleMatrix updateW) {
            this.updateW = updateW;
        }
        /**
         * Gets the matrix to be multiplied to the hidden activation while calculating the updateGate
         * @return updateU
         */
        public DoubleMatrix getUpdateU() {
            return updateU;
        }
        /**
         * Sets the matrix to be multiplied to the hidden activation while calculating the updateGate
         * @param updateU
         */
        public void setUpdateU(DoubleMatrix updateU) {
            this.updateU = updateU;
        }
        /**
         * Gets the matrix to be multiplied to the input while calculating the resetGate
         * @return resetW
         */
        public DoubleMatrix getResetW() {
            return resetW;
        }
        /**
         * Sets the matrix to be multiplied to the input while calculating the resetGate
         * @param resetW
         */
        public void setResetW(DoubleMatrix resetW) {
            this.resetW = resetW;
        }
        /**
         * Gets the matrix to be multiplied to the hidden activation while calculating the resetGate
         * @return resetU
         */
        public DoubleMatrix getResetU() {
            return resetU;
        }
        /**
         * Sets the matrix to be multiplied to the hidden activation while calculating the resetGate
         * @param resetU
         */
        public void setResetU(DoubleMatrix resetU) {
            this.resetU = resetU;
        }
        /**
         * Gets the matrix to be multiplied to the input while calculating the candidate activations
         * @return hiddenW
         */
        public DoubleMatrix getHiddenW() {
            return hiddenW;
        }
        /**
         * Sets the matrix to be multiplied to the input while calculating the candidate activations
         * @param hiddenW
         */
        public void setHiddenW(DoubleMatrix hiddenW) {
            this.hiddenW = hiddenW;
        }
        /**
         * Gets the matrix to be multiplied to the hidden activation while calculating the candidate activations
         * @return hiddenU
         */
        public DoubleMatrix getHiddenU() {
            return hiddenU;
        }
        /**
         * Sets the matrix to be multiplied to the hidden activation while calculating the candidate activations
         * @param hiddenU
         */
        public void setHiddenU(DoubleMatrix hiddenU) {
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

        public DoubleMatrix getUpdateBias() {
            return updateBias;
        }

        public void setUpdateBias(DoubleMatrix updateBias) {
            this.updateBias = updateBias;
        }

        public DoubleMatrix getResetBias() {
            return resetBias;
        }

        public void setResetBias(DoubleMatrix resetBias) {
            this.resetBias = resetBias;
        }

        public DoubleMatrix getHiddenBias() {
            return hiddenBias;
        }

        public void setHiddenBias(DoubleMatrix hiddenBias) {
            this.hiddenBias = hiddenBias;
        }

        @Override
        public void computeFromInput(DoubleMatrix input) {
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
        
        DoubleMatrix memoryCell;
        DoubleMatrix outputGate;
        
        private DoubleMatrix Wo;
        private DoubleMatrix Uo;
        private DoubleMatrix Vo;
        private DoubleMatrix bo;
        private DoubleMatrix Wc;
        private DoubleMatrix Uc;
        private DoubleMatrix bc;
        
        DoubleMatrix forgetGate;
        DoubleMatrix inputGate;
        
        
        private DoubleMatrix Wf;
        private DoubleMatrix Uf;
        private DoubleMatrix Vf;
        private DoubleMatrix bf;
        private DoubleMatrix Wi;
        private DoubleMatrix Ui;
        private DoubleMatrix Vi;
        private DoubleMatrix bi;
        
        private Activation newMemoryActivation = new Tanh();
        private Activation outputActivation = new Tanh();
        private Activation outputGateActivation = new Sigmoid();
        private Activation forgetGateActivation = new Sigmoid();
        private Activation inputGateActivation = new Sigmoid();
        
        @Override
        public void compute(Layer input) {
            DoubleMatrix forgetTemp = this.getWf().mmul(input.output);
            forgetTemp.addi(this.getUf().mmul(this.output));
            forgetTemp.addi(this.getVf().mmul(this.memoryCell));
            forgetTemp.addi(this.getBf());
            this.forgetGate = this.getForgetGateActivation().apply(forgetTemp);
            DoubleMatrix inputTemp = this.getWi().mmul(input.output);
            inputTemp.addi(this.getUi().mmul(this.output));
            inputTemp.addi(this.getVi().mmul(this.memoryCell));
            inputTemp.addi(this.getBi());
            this.inputGate = this.getInputGateActivation().apply(inputTemp);
            DoubleMatrix newMemoryTemp = this.getWc().mmul(input.output);
            newMemoryTemp.addi(this.getUc().mmul(this.output));
            newMemoryTemp.addi(this.getBc());
            newMemoryTemp = this.getNewMemoryActivation().apply(newMemoryTemp);
            DoubleMatrix memoryTemp = this.forgetGate.mul(this.memoryCell);
            memoryTemp.addi(this.inputGate.mul(newMemoryTemp));
            this.memoryCell = memoryTemp;
            DoubleMatrix outputGateTemp = this.getWo().mmul(input.output);
            outputGateTemp.addi(this.getUo().mmul(this.output));
            outputGateTemp.addi(this.getVo().mmul(this.memoryCell));
            outputGateTemp.addi(this.getBo());
            this.outputGate = this.getOutputGateActivation().apply(outputGateTemp);
            this.output = this.outputGate.mul(this.getOutputActivation().apply(this.memoryCell));
        }

        @Override
        public void computeFromInput(DoubleMatrix input) {
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
        public void initialize(double[][] Wo1, double[][] Uo1, double[][] Vo1, double[][] Wf1, double[][] Uf1, double[][] Vf1, double[][] Wi1, double[][] Ui1, double[][] Vi1, double[][] Wc1, double[][] Uc1, double[] bo1, double[] bf1, double[] bi1, double[] bc1) {
            this.setWo(new DoubleMatrix(Wo1));
            this.setUo(new DoubleMatrix(Uo1));
            this.setVo(new DoubleMatrix(Vo1));
            this.setWf(new DoubleMatrix(Wf1));
            this.setUf(new DoubleMatrix(Uf1));
            this.setVf(new DoubleMatrix(Vf1));
            this.setWi(new DoubleMatrix(Wi1));
            this.setUi(new DoubleMatrix(Ui1));
            this.setVi(new DoubleMatrix(Vi1));
            this.setWc(new DoubleMatrix(Wc1));
            this.setUc(new DoubleMatrix(Uc1));
            this.setBo(new DoubleMatrix(bo1));
            this.setBf(new DoubleMatrix(bf1));
            this.setBi(new DoubleMatrix(bi1));
            this.setBc(new DoubleMatrix(bc1));
            this.output = DoubleMatrix.zeros(this.getUo().rows);
            this.memoryCell = DoubleMatrix.zeros(this.getUo().rows);
            this.forgetGate = DoubleMatrix.zeros(this.getUo().rows);
            this.inputGate = DoubleMatrix.zeros(this.getUo().rows);
            this.outputGate = DoubleMatrix.zeros(this.getUo().rows);
        }

        /**
         * @return the Wo
         */
        public DoubleMatrix getWo() {
            return Wo;
        }

        /**
         * @param Wo the Wo to set
         */
        public void setWo(DoubleMatrix Wo) {
            this.Wo = Wo;
        }

        /**
         * @return the Uo
         */
        public DoubleMatrix getUo() {
            return Uo;
        }

        /**
         * @param Uo the Uo to set
         */
        public void setUo(DoubleMatrix Uo) {
            this.Uo = Uo;
        }

        /**
         * @return the Vo
         */
        public DoubleMatrix getVo() {
            return Vo;
        }

        /**
         * @param Vo the Vo to set
         */
        public void setVo(DoubleMatrix Vo) {
            this.Vo = Vo;
        }

        /**
         * @return the bo
         */
        public DoubleMatrix getBo() {
            return bo;
        }

        /**
         * @param bo the bo to set
         */
        public void setBo(DoubleMatrix bo) {
            this.bo = bo;
        }

        /**
         * @return the Wc
         */
        public DoubleMatrix getWc() {
            return Wc;
        }

        /**
         * @param Wc the Wc to set
         */
        public void setWc(DoubleMatrix Wc) {
            this.Wc = Wc;
        }

        /**
         * @return the Uc
         */
        public DoubleMatrix getUc() {
            return Uc;
        }

        /**
         * @param Uc the Uc to set
         */
        public void setUc(DoubleMatrix Uc) {
            this.Uc = Uc;
        }

        /**
         * @return the bc
         */
        public DoubleMatrix getBc() {
            return bc;
        }

        /**
         * @param bc the bc to set
         */
        public void setBc(DoubleMatrix bc) {
            this.bc = bc;
        }

        /**
         * @return the Wf
         */
        public DoubleMatrix getWf() {
            return Wf;
        }

        /**
         * @param Wf the Wf to set
         */
        public void setWf(DoubleMatrix Wf) {
            this.Wf = Wf;
        }

        /**
         * @return the Uf
         */
        public DoubleMatrix getUf() {
            return Uf;
        }

        /**
         * @param Uf the Uf to set
         */
        public void setUf(DoubleMatrix Uf) {
            this.Uf = Uf;
        }

        /**
         * @return the Vf
         */
        public DoubleMatrix getVf() {
            return Vf;
        }

        /**
         * @param Vf the Vf to set
         */
        public void setVf(DoubleMatrix Vf) {
            this.Vf = Vf;
        }

        /**
         * @return the bf
         */
        public DoubleMatrix getBf() {
            return bf;
        }

        /**
         * @param bf the bf to set
         */
        public void setBf(DoubleMatrix bf) {
            this.bf = bf;
        }

        /**
         * @return the Wi
         */
        public DoubleMatrix getWi() {
            return Wi;
        }

        /**
         * @param Wi the Wi to set
         */
        public void setWi(DoubleMatrix Wi) {
            this.Wi = Wi;
        }

        /**
         * @return the Ui
         */
        public DoubleMatrix getUi() {
            return Ui;
        }

        /**
         * @param Ui the Ui to set
         */
        public void setUi(DoubleMatrix Ui) {
            this.Ui = Ui;
        }

        /**
         * @return the Vi
         */
        public DoubleMatrix getVi() {
            return Vi;
        }

        /**
         * @param Vi the Vi to set
         */
        public void setVi(DoubleMatrix Vi) {
            this.Vi = Vi;
        }

        /**
         * @return the bi
         */
        public DoubleMatrix getBi() {
            return bi;
        }

        /**
         * @param bi the bi to set
         */
        public void setBi(DoubleMatrix bi) {
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
            this.output = DoubleMatrix.zeros(this.Uo.rows);
            this.memoryCell = DoubleMatrix.zeros(this.Uc.rows);
            this.forgetGate = DoubleMatrix.zeros(this.Uf.rows);
            this.inputGate = DoubleMatrix.zeros(this.Ui.rows);
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
        DoubleMatrix weightMatrix;
        /**
         * Holds the biases of the neurons in the layer
         */
        DoubleMatrix biases;
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
            DoubleMatrix tempOutput = this.weightMatrix.mmul(input.output);
            tempOutput.addi(biases);
            this.output = this.activationFunction.apply(tempOutput);
        }

        /**
         * Initializes the dense layer given weightMatrix
         * @param weightMatrix1 - weightMatrix which multiplies the previous input layer
         * @param biases1 - biases for the neurons in the layer
         */
        public void initialize(double[][] weightMatrix1, double[] biases1) {
            this.weightMatrix = new DoubleMatrix(weightMatrix1);
            this.biases = new DoubleMatrix(biases1);
            this.output = DoubleMatrix.zeros(this.weightMatrix.rows);
        }
        /**
         * Initializes the dense layer given weightMatrix and the activation function
         * @param weightMatrix1 - weightMatrix which multiplies the previous input layer
         * @param biases1 - biases for the neurons in the layer
         * @param activationFunction1 - activation function of the layer
         */
        public void initialize(double[][] weightMatrix1, double[][] biases1, Activation activationFunction1) {
            this.weightMatrix = new DoubleMatrix(weightMatrix1);
            this.biases = new DoubleMatrix(biases1);
            this.output = DoubleMatrix.zeros(this.weightMatrix.rows);
            this.activationFunction = activationFunction1;
        }
        
        public DoubleMatrix getWeightMatrix() {
            return weightMatrix;
        }

        public void setWeightMatrix(DoubleMatrix weightMatrix) {
            this.weightMatrix = weightMatrix;
        }

        public Activation getActivationFunction() {
            return activationFunction;
        }

        public void setActivationFunction(Activation activationFunction) {
            this.activationFunction = activationFunction;
        }

        public DoubleMatrix getBiases() {
            return biases;
        }

        public void setBiases(DoubleMatrix biases) {
            this.biases = biases;
        }

        @Override
        public void computeFromInput(DoubleMatrix input) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void resetLayer() {
            this.output = DoubleMatrix.zeros(this.biases.length);
        }

    }
    /**
     * Converts a 1D float array to a 2D double array, given the reshape parameter
     * @param tmpW
     * @param hiddenSize - 2nd dimension of the output 2D array
     * @return 
     */
    public static double[][] convertFlatToDim2 (float[] tmpW, int hiddenSize) {
        int inputSize = tmpW.length/hiddenSize;
        double[][] output = new double[inputSize][hiddenSize];
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                output[i][j] = tmpW[i*hiddenSize+j];
            }
        }
        output = RNNetwork.transposeDoubleMatrix(output);
        return output;
    }
    /**
     * Transposes a 2D double array
     * @param input
     * @return 
     */
    public static double[][] transposeDoubleMatrix (double[][] input) {
        int rows = input.length;
        int columns = input[0].length;
        double[][] output = new double[columns][rows];
        for(int i=0; i<rows; i++) {
            for(int j=0; j<columns; j++) {
                output[j][i] = input[i][j];
            }
        }
        return output;
    }
    /**
     * Converts 1D float array to 1D double array
     * @param input
     * @return 
     */
    public static double[] convertFloatsToDoublesDim1(float[] input) {
        if (input == null) {
            return null; 
        }
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
    /**
     * Converts a 2D float array to 2D double array
     * @param input
     * @return 
     */
    public static double[][] convertFloatsToDoublesDim2(float[][] input) {
        if (input == null) {
            return null; 
        }
        double[][] output = new double[input.length][input[0].length];
        for (int i = 0; i < input.length; i++) {
            for (int j = 0; j < input[i].length; j++) {
                output[i][j] = input[i][j];
            }
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
