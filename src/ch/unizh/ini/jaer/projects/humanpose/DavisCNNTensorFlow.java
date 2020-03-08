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
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.tensorflow.DataType;
import org.tensorflow.Graph;
import org.tensorflow.Operation;
import org.tensorflow.Output;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import net.sf.jaer.graphics.ImageDisplay;

import com.jogamp.opengl.GL2;

import ch.unizh.ini.jaer.projects.humanpose.TensorFlow.GraphBuilder;
import net.sf.jaer.graphics.DavisRenderer;

// to safely convert network output shape dimensions to int.
import static java.lang.Math.toIntExact;

/**
 * Runs a CNN using tensorflow
 *
 * https://www.tensorflow.org/api_docs/java/reference/org/tensorflow/package-summary
 *
 * https://www.tensorflow.org/install/
 *
 * @author Tobi, Gemma, Enrico
 */
public class DavisCNNTensorFlow extends AbstractDavisCNN {

    protected OutputLayer outputLayer = null;
    protected InputLayer inputLayer = null;

    private byte[] graphDef = null;
    private Graph executionGraph = null;
    private Graph inputNormalizationGraph = null;
    private ArrayList<String> ioLayers = new ArrayList();
    SavedModelBundle savedModelBundle = null;
    private ImageDisplay imageDisplay;
    
    private boolean showHeatmapNotSkeletonFlag;
    private int showWhichHeatmap;

    public DavisCNNTensorFlow(AbstractDavisCNNProcessor processor) {
        super(processor);
    }

    @Override
    public Tensor processAPSDVSFrame(APSDVSFrame frame) {
        final int numChannels = 3; //frame.NUM_CHANNELS;
      final int sx = frame.getWidth(), sy = frame.getHeight();
        FloatBuffer fb = FloatBuffer.allocate(sx * sy * numChannels);
        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                for (int c = 0; c < numChannels; c++) {
                    final int newIdx = c + (numChannels * (x + (sx * (sy - y - 1))));
                    if (c == 2) {
                        fb.put(newIdx, 0);
                    } else {
                        fb.put(newIdx, frame.getValue(c, x, y));
                    }
                }
            }
        }
        fb.rewind();
        Tensor<Float> inputImageTensor = Tensor.create(new long[]{1, sy, sx, numChannels}, fb);
        Boolean b = false;
        Tensor<Boolean> t = Tensor.create(b, Boolean.class);
        //executionGraph.opBuilder("MaxPoolWithArgmax", "MyMaxPoolWithArgmax").setAttr("dtype", inputImageTensor.dataType()).setAttr("value", inputImageTensor).build();
        //Tensor results = TensorFlow.executeGraphAndReturnTensor(executionGraph, inputImageTensor, processor.getInputLayerName(), processor.getOutputLayerName());
        Tensor results = TensorFlow.executeGraphAndReturnTensorWithBoolean(executionGraph, inputImageTensor, processor.getInputLayerName(), t, "phase_train", processor.getOutputLayerName());
        getSupport().firePropertyChange(EVENT_MADE_DECISION, null, this);
        return results;   
    }
    
    @Override
    public void processAPSDVSFrameArray(APSDVSFrame frame, float[] array) {
      final int numChannels = 3; //frame.NUM_CHANNELS;
      final int sx = frame.getWidth(), sy = frame.getHeight();
        FloatBuffer fb = FloatBuffer.allocate(sx * sy * numChannels);
        float[][][][] buf = new float[1][260][344][1]; // TODO: this is hardcoded, btw processAPSDVSFrameArray function is never used.
        float nbNulPix = 0;
        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                for (int c = 0; c < numChannels; c++) {
                    if (c == 2) {
                        buf[0][y][x][c] = 0;
                    } else {
                        buf[0][y][x][c] = frame.getValue(c, x, y) * 255;
                        //if( c==1 && frame.getValue(c,x,y)== 0)
                           //nbNulPix++;
                            
                    }
                }
                //nbNulPix+=buf[0][y][x][0];
                
            }
        }
        System.out.println(Float.toString(nbNulPix / (90 * 120)));
        fb.rewind();
        //Tensor<Float> inputImageTensor = Tensor.create(new long[]{1, sy, sx, numChannels}, fb);
        Tensor<Float> inputImageTensor = Tensor.create(buf,  Float.class);
        Boolean b = false;
        Tensor<Boolean> t = Tensor.create(b, Boolean.class);
        //executionGraph.opBuilder("MaxPoolWithArgmax", "MyMaxPoolWithArgmax").setAttr("dtype", inputImageTensor.dataType()).setAttr("value", inputImageTensor).build();
        //Tensor results = TensorFlow.executeGraphAndReturnTensor(executionGraph, inputImageTensor, processor.getInputLayerName(), processor.getOutputLayerName());
        //TensorFlow.executeGraphAndReturnTensorWithBooleanArray(array, executionGraph, inputImageTensor, processor.getInputLayerName(),t,"phase_train", processor.getOutputLayerName());
        getSupport().firePropertyChange(EVENT_MADE_DECISION, null, this);  
    }

    @Override
    public float[] processDvsFrame(DvsFramer.DvsFrame frame) {
        FloatBuffer b = FloatBuffer.wrap(frame.getImage());
        float[] results = executeDvsFrameGraph(b, frame.getWidth(), frame.getHeight());
        return results;
    }

    private Output<Float> normalizedImageOutput = null; // used to reference the graph

    @Override
    public float[] processAPSFrame(ApsFrameExtractor frameExtractor) {
        final int numChannels = processor.isMakeRGBFrames() ? 3 : 1;
        float mean = processor.getImageMean(), scale = processor.getImageScale();
        int width = processor.getImageWidth(), height = processor.getImageHeight();
        if (inputNormalizationGraph == null) {
            Graph g = new Graph();
            GraphBuilder b = new GraphBuilder(g);
            // see https://github.com/tensorflow/tensorflow/issues/6781
            final Output<Float> imagePH = g.opBuilder("Placeholder", "input").setAttr("dtype", DataType.FLOAT).build().output(0);
            final Output<Float> meanPH = g.opBuilder("Placeholder", "mean").setAttr("dtype", DataType.FLOAT).build().output(0);
            final Output<Float> scalePH = g.opBuilder("Placeholder", "scale").setAttr("dtype", DataType.FLOAT).build().output(0);
//            final Output<Integer> widthPH = g.opBuilder("Placeholder", "width").setAttr("dtype", DataType.INT32).build().output(0);
//            final Output<Integer> heightPH = g.opBuilder("Placeholder", "height").setAttr("dtype", DataType.INT32).build().output(0);
            final Output<Integer> sizePH = g.opBuilder("Placeholder", "size").setAttr("dtype", DataType.INT32).build().output(0);
            normalizedImageOutput
                    = b.div(
                            b.sub(
                                    b.resizeBilinear(
                                            imagePH,
                                            sizePH),
                                    meanPH),
                            scalePH);
            inputNormalizationGraph = g;
        }
        final int sx = frameExtractor.getWidth(), sy = frameExtractor.getHeight();
        FloatBuffer fb = FloatBuffer.allocate(sx * sy * numChannels);
        float[] rgb = null;
        if (processor.isMakeRGBFrames()) {
            rgb = new float[]{1, 1, 1};
        } else {
            rgb = new float[]{1};
        }

        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                for (int c = 0; c < numChannels; c++) {
                    final int newIdx = c + (numChannels * (x + (sx * (sy - y - 1))));
                    fb.put(newIdx, rgb[c] * frameExtractor.getNewFrame()[frameExtractor.getIndex(x, y)]);
                }
            }
        }
        fb.rewind();
        Tensor<Float> inputImageTensor = Tensor.create(new long[]{1, sy, sx, numChannels}, fb);
        Tensor meanT = Tensor.create(mean);
        Tensor scaleT = Tensor.create(scale);
        Tensor sizeT = Tensor.create(new int[]{height, width});
        Tensor<Float> normalizedImage = new Session(inputNormalizationGraph)
                .runner()
                .feed("input", inputImageTensor)
                .feed("mean", meanT)
                .feed("scale", scaleT)
                .feed("size", sizeT)
                .fetch(normalizedImageOutput.op().name())
                .run()
                .get(0).expect(Float.class);

        float[] results = null;
        
        /*if (savedModelBundle == null) {
            results = TensorFlow.executeGraph(executionGraph, normalizedImage, processor.getInputLayerName(), processor.getOutputLayerName());
        } else {
            results = TensorFlow.executeSession(savedModelBundle, normalizedImage, processor.getInputLayerName(), processor.getOutputLayerName());
        }*/
        
        outputLayer = new OutputLayer(results);
        getSupport().firePropertyChange(EVENT_MADE_DECISION, null, this);
        return results;
    }

    // added to extract the output shape at first inference.
    private static int[] outShape = null;
    
    /**
     * Executes the stored Graph of the CNN.
     *
     * //https://github.com/tensorflow/tensorflow/blob/master/tensorflow/java/src/main/java/org/tensorflow/op/Operands.java
     * // https://github.com/tensorflow/tensorflow/issues/7149
     * https://stackoverflow.com/questions/44774234/why-tensorflow-uses-channel-last-ordering-instead-of-row-major
     *
     * @param pixbuf the pixel buffer holding the frame, as collected from
     * DVSFramer in DVSFrame.
     *
     * @param width width of image
     * @param height height of image
     * @return activations of output
     */
    private float[] executeDvsFrameGraph(FloatBuffer pixbuf, int width, int height) {
//        final float mean = processor.getImageMean(), scale = processor.getImageScale();
        final int numChannels = processor.isMakeRGBFrames() ? 3 : 1;
        inputLayer = new InputLayer(width, height, numChannels); // TODO hack since we don't know the input size yet until network runs

        // TODO super hack brute force to flip image vertically because tobi cannot see how to flip an image in TensorFlow.
        // Also, make RGB frame from gray dvs image by cloning the gray value to each channel in WHC order
        final float[] origarray = pixbuf.array();
        FloatBuffer flipped = FloatBuffer.allocate(pixbuf.limit() * numChannels);
        final float[] flippedarray = flipped.array();
        // prepare rgb scaling factors to make RGB channels from grayscale. each channel has different weighting
        float[] rgb = null;
        if (processor.isMakeRGBFrames()) {
            rgb = new float[]{1, 1, 1};
        } else {
            rgb = new float[]{1};
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int origIdx = x + (width * y);
                for (int c = 0; c < numChannels; c++) {
                    final int newIdx = c + (numChannels * (x + (width * (height - y - 1))));
                    flippedarray[newIdx] = ((origarray[origIdx] * rgb[c]));
                }
            }
        }
        flipped = FloatBuffer.wrap(flippedarray);

        try (Tensor<Float> imageTensor = Tensor.create(new long[]{1, height, width, numChannels}, flipped);) { // use NHWC order according to last post above
//            int numElements = imageTensor.numElements();
//            long[] shape = imageTensor.shape();

            if (outShape == null) {
                outShape = TensorFlow.executeGraphStartup(executionGraph, imageTensor, processor.getInputLayerName(), processor.getOutputLayerName());
            }

            long startTimeexecuteGraph = System.nanoTime();
            float[] output = TensorFlow.executeGraph(executionGraph, imageTensor, processor.getInputLayerName(), processor.getOutputLayerName());
            long dtNs_executeGraph = (System.nanoTime() - startTimeexecuteGraph);
            log.info("executeGraph took " + (dtNs_executeGraph * 1e-6f) + " ms");

            //TIMING
            long startTime = System.nanoTime();
            
            outputLayer = new OutputLayer(output);
            
            long dtNs_outputLayer = (System.nanoTime() - startTime);
            log.info("outputLayer took " + (dtNs_outputLayer * 1e-6f) + " ms");  
            
            //if (isSoftMaxOutput()) {
                //computeSoftMax();
            //    throw new UnsupportedOperationException("Removed implementation.");
            //}
            getSupport().firePropertyChange(EVENT_MADE_DECISION, null, this);
            return output;
        } catch (IllegalArgumentException ex) {
            String exhtml = ex.toString().replaceAll("<", "&lt").replaceAll(">", "&gt").replaceAll("&", "&amp").replaceAll("\n", "<br>");
            final StringBuilder msg = new StringBuilder("<html>Caught exception <p>" + exhtml + "</p>");
            msg.append("<br> Did you set <i>inputLayerName</i> and <i>outputLayerName</i>?");
            msg.append("<br>The IO layer names could be as follows (the string inside the single quotes): <ul> ");
            for (String s : ioLayers) {
                msg.append("<li>" + (s.replaceAll("<", "").replaceAll(">", "")) + "</li>");
            }
            msg.append("</ul></html>");
            log.warning(msg.toString());
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(processor.getChip().getAeViewer(), msg.toString(),
                            "Error computing network", JOptionPane.WARNING_MESSAGE);
                }
            });
            throw new IllegalArgumentException(ex.getCause());
        }
    }

    /**
     * computes the softmax on the existing activations. * Computes softmax on
     * its input activations, by o_i= exp(a_i)/sum_k(exp(a_k)) where o_i is the
     * i'th output and a_k is the k'th input.
     
    private void computeSoftMax() {
        float[] activations = outputLayer.getActivations();
        if ((activations == null) || (activations.length == 0)) {
            log.warning("tried to compute softmax on null or empty output layer activations");
            return;
        }
        float sum = 0;
        for (int k = 0; k < activations.length; k++) { // simply MAC the weight times the input activation
            float f = (float) Math.exp(activations[k]);
            if (Float.isInfinite(f)) {
                f = Float.MAX_VALUE; // handle exponential overflow
            }
            sum += f;
            activations[k] = f;
        }
        outputLayer.maxActivation = Float.NEGATIVE_INFINITY;
        float r = 1 / sum;
        for (int k = 0; k < activations.length; k++) { // simply MAC the weight times the input activation
            activations[k] *= r;
            if (activations[k] > outputLayer.maxActivation) {
                outputLayer.maxActivatedUnit = k;
                outputLayer.maxActivation = activations[k];
            }
        }
    }
    */

    @Override
    public float[][][] processInputPatchFrame(DavisRenderer frame, int offX, int offY) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public InputLayer getInputLayer() {
        return inputLayer;
    }

    @Override
    public OutputLayer getOutputLayer() {
        return outputLayer;
    }

    @Override
    public void drawActivations() {
    }

    @Override
    public void cleanup() {
        if (executionGraph != null) {
            executionGraph.close();
        }
        if (inputNormalizationGraph != null) {
            inputNormalizationGraph.close();
        }
    }

    @Override
    public JFrame drawKernels() {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void loadNetwork(File f) throws IOException {
        if (f == null) {
            throw new IOException("null file");
        }
        try {
            if (f.isDirectory()) {
                log.info("loading \"serve\" graph from tensorflow SavedModelBundle folder " + f);
                savedModelBundle = SavedModelBundle.load(f.getCanonicalPath(), "serve");
                executionGraph = savedModelBundle.graph();
            } else {
                log.info("loading network from file " + f);
                graphDef = Files.readAllBytes(Paths.get(f.getAbsolutePath())); // "tensorflow_inception_graph.pb"
                executionGraph = new Graph();
                executionGraph.importGraphDef(graphDef);
            }
            setFilename(f.toString());
            setNettype("TensorFlow");
            setNetname(f.getName());

            Iterator<Operation> itr = executionGraph.operations();
            StringBuilder b = new StringBuilder("TensorFlow Graph: \n");
            int opnum = 0;
            ioLayers.clear();
            while (itr.hasNext()) {
                Operation o = itr.next();
                final String s = o.toString().toLowerCase();
//                if(s.contains("input") || s.contains("output") || s.contains("placeholder")){
                if (s.contains("input")
                        || s.contains("placeholder")
                        || s.contains("output")
                        || s.contains("prediction")) {  // find input placeholder & output
//                    int numOutputs = o.numOutputs();
//                    if(! s.contains("output_shape") && !s.contains("conv2d_transpos")){
                        b.append("********** ");
                        ioLayers.add(s);
//                    for (int onum = 0; onum < numOutputs; onum++) {
//                        Output output = o.output(onum);
//                        Shape shape = output.shape();
//                        int numDimensions = shape.numDimensions();
//                        for (int dimidx = 0; dimidx < numDimensions; dimidx++) {
//                            long dim = shape.size(dimidx);
//                        }
//                    }
//                    int inputLength=o.inputListLength("");
                        b.append(opnum++ + ": " + o.toString() + "\n");
//                    }
                }
            }
            log.info(b.toString());
        } catch (Exception e) {
            log.warning(e.toString());
            e.printStackTrace();
        }
    }

    @Override
    public void printActivations() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void printWeights() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void setShowHeatmapNotSkeleton(boolean showHeatmapNotSkeleton) {
        showHeatmapNotSkeletonFlag = showHeatmapNotSkeleton;
    }
    
    @Override
    public void setShowWhichHeatmap(int showWhichHeatmap) {
        showWhichHeatmap = showWhichHeatmap;
    }
    
    
    
    float[] outActivations;
    int numUnits, height, width, chans, nPixPerRow;
    float[][] tmpMaxActAndLocPerMap = null;    

    float[][] maxActAndLocPerMap; // stores max activation and location for each map.
    float[][] heatmapCNNSize; // to store sum of heatmaps, with CNN output size. To be upsampled for plotting.
    
    public class OutputLayer implements AbstractDavisCNN.OutputLayer {
        
        // This method loops over the network output (1d array output, previously 3d array) 
        // and extracts max position and activation for each heatmap.
        public OutputLayer(float[] output) {

            //float[] tmpMapActivations = new float[output.length][output[0].length]; 
            // outShape is [ hmapH hmapW nMaps]
            //float[][] tmpMapActivations = new float[outShape[0]][outShape[1]];
            
            // problem-specific sizes.
            if (tmpMaxActAndLocPerMap == null) {
                log.info(String.format("*** Instantiating problem-specific constants: [height, width, chans], nPixPerRow, size tmpMaxActAndLocPerMap, size heatmap."));
                // instead instantiating vars in class.
                //final int height = outShape[0], width = outShape[1], chans = outShape[2], nPixPerRow=chans*width;
                //float[][] tmpMaxActAndLocPerMap = new float[chans][3]; // hardcoded, max value and x,y position.
                height = outShape[0];
                width = outShape[1];
                chans = outShape[2];
                numUnits = height * width * chans; // check if needed or can be removed.
                nPixPerRow = chans * width;
                tmpMaxActAndLocPerMap = new float[chans][3]; // hardcoded, max value and x,y position.
                heatmapCNNSize = new float[height][width];
                log.info(String.format("*** DONE Instantiating problem-specific constants: [height, width, chans], nPixPerRow, size tmpMaxActAndLocPerMap, size heatmap."));
            }
            
            outActivations = output;
            
            // loop over 1D vector of activations to extract max per each heatmap.
            //long t0 = System.nanoTime();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int c = 0; c < chans; c++) {
                        final int idx=c+x*chans+y*nPixPerRow;
                        final float act=outActivations[idx];
                        
                        // to display heatmap
                        if (showHeatmapNotSkeletonFlag){
                            // only instantaneous heatmap is displayed
                            if (showWhichHeatmap == -1){ // -1 for showing all heatmaps
                                if (c==0){ heatmapCNNSize[y][x]=act;} //to avoid initializing the map to zero.
                                else{ heatmapCNNSize[y][x] += act; }
                            }
                            else{
                                if (c==showWhichHeatmap){ heatmapCNNSize[y][x]=act;}
                            }
                        }
                        else{ // to display skeleton
                            // initialize the value of max for each CNN output.
                            if ((y==0) && (x==0)) {
                                tmpMaxActAndLocPerMap[c][0]=act;
                                tmpMaxActAndLocPerMap[c][1]=0;
                                tmpMaxActAndLocPerMap[c][2]=0;
                            }
                            else{
                                if(act>tmpMaxActAndLocPerMap[c][0]){
                                    //order is: [mapIdx][activation y x]
                                    tmpMaxActAndLocPerMap[c][0]=act;
                                    tmpMaxActAndLocPerMap[c][1]=y;
                                    tmpMaxActAndLocPerMap[c][2]=x;
                                }
                            }
                        }
                    }
                }
            }

            //long t1 = System.nanoTime();
            maxActAndLocPerMap = tmpMaxActAndLocPerMap;
            
            //log.info(String.format("max (in tmp) took %.3fms", 1e-6f * (t1 - t0)));
            //log.info(String.format("max copy  from tmp %.3fms", 1e-6f * (System.nanoTime() - t1)));
        }
        
        
        @Override
        public int getNumUnits() {
            return numUnits;
        }
        
        //@Override
        //public int[] getMaxActivatedUnit() {
        //    return maxActivatedUnitPerMap;
        //}

        //@Override
        //public void setMaxActivatedUnit(int unit) {
        //    maxActivatedUnitPerMap = unit;
        //}

        //@Override
        //public float[] getMaxActivation() {
        //    return maxActivationPerMap;
        //}

        //@Override
        //public void drawHistogram(GL2 gl, int width, int height, float lineWidth, Color color) {
        //    AbstractDavisCNN.drawHistogram(gl, outActivations, width, height, lineWidth, color);
        //}
        
        
        @Override
        public float[][] getMaxActAndLocPerMap(){
            return maxActAndLocPerMap;
        }
        
        @Override
        public float[][] getHeatmapCNNSize(){
            return heatmapCNNSize;
        }

        @Override
        public float[] getActivations() {
            return outActivations;
        }
        
    }

    public class InputLayer implements AbstractDavisCNN.InputLayer {

        int height, width, numChannels;

        // set one of these nonnull depending on application
        public DvsFramerSingleFrame dvsFramerSingleFrame = null;
        //public DvsFramerROIGenerator dvsFramerROIGenerator = null;

        public InputLayer(int width, int height, int numChannels) {
            this.width = width;
            this.height = height;
            this.numChannels = numChannels;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getNumChannels() {
            return numChannels;
        }

    }

}
