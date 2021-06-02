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

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.logging.Logger;
import org.tensorflow.DataType;
import org.tensorflow.Graph;
import org.tensorflow.Output;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Shape;
import org.tensorflow.Tensor;
import org.tensorflow.types.UInt8;
import org.tensorflow.Operation;

/**
 * From LabelImage example: static methods to execute TensorFlow stuff.
 *
 * @author Tobi, Gemma, Enrico
 */
public class TensorFlow {
    private static Logger log=Logger.getLogger("TensorFlow");
    
    public static float[] maxIndex(float[][] mapActivations) {
        int maxLocY = 0;
        int maxLocX = 0;
        float maxValue = 0;

        for (int i = 0; i < mapActivations.length; i++) {
            for (int j = 0; j < mapActivations[0].length; j++) {
                if (mapActivations[i][j] > mapActivations[maxLocY][maxLocX]) {
                    maxLocY = i;
                    maxLocX = j;
                    maxValue = mapActivations[i][j];
                }
            }
        }
        //float[] maxValAndLoc={maxValue, maxLocY, maxLocX}; // max value of the map and its location
        float[] maxValAndLoc = new float[3]; // hardcoded, max value and x,y position.
        maxValAndLoc[0] = maxValue;
        maxValAndLoc[1] = maxLocY;
        maxValAndLoc[2] = maxLocX;
        return maxValAndLoc;
    }

    public static Tensor<Float> constructAndExecuteGraphToNormalizeRGBImage(byte[] imageBytes, int W, int H, float mean, float scale) {
        try (Graph g = new Graph()) {
            GraphBuilder b = new GraphBuilder(g);
            // Some constants specific to the pre-trained model at:
            // https://storage.googleapis.com/download.tensorflow.org/models/inception5h.zip
            //
            // - The model was trained with images scaled to 224x224 pixels.
            // - The colors, represented as R, G, B in 1-byte each were converted to
            //   float using (value - Mean)/Scale.
//            final int H = 224;
//            final int W = 224;
//            final float mean = 117f;
//            final float scale = 1f;

            // Since the graph is being constructed once per execution here, we can use a constant for the
            // input image. If the graph were to be re-used for multiple input images, a placeholder would
            // have been more appropriate.
            final Output<String> input = b.constant("input", imageBytes);
            final Output<Float> output
                    = b.div(
                            b.sub(
                                    b.resizeBilinear(
                                            b.expandDims(
                                                    b.cast(b.decodeJpeg(input, 3), Float.class),
                                                    b.constant("make_batch", 0)),
                                            b.constant("size", new int[]{H, W})),
                                    b.constant("mean", mean)),
                            b.constant("scale", scale));
            try (Session s = new Session(g)) {
                return s.runner().fetch(output.op().name()).run().get(0).expect(Float.class);
            }
        }
    }

    private static Session s = null;
    //private static float[][][][] resultHeatmaps=null;
    private static float[] result1d = null;
    
    public static float[] executeGraph(Graph graph, Tensor<Float> image, String inputLayerName, String outputLayerName) {
        long startTime = System.nanoTime();
 
        //TODO: CHECK IF THIS IS NEEDED OR NOT. s should be initialized inside executeGraphStartup method, called before this.
        //if (s==null){ s = new Session(graph); }
        
        try ( //Session s = new Session(graph);
                Tensor<Float> result = s.runner().feed(inputLayerName, image).fetch(outputLayerName).run().get(0).expect(Float.class)) {

            /*  // UNCOMMENT THIS SECTION IF THE NETWORK ALREADY EXTRACTS MAX POSITION AND ACTIVATION //
            int nMaxValAndPos = (int) rshape[1]; // this must be 3: [uPosition, vPosition, confidence]
            int nMaps = (int) rshape[2];
            if (resultPositions == null) {
                resultPositions = new float[1][nMaxValAndPos][nMaps];
            }
            long dtUs_run=(System.nanoTime()-startTime)/1000;
            result.copyTo(resultPositions);
            long dtUs_copyTo=(System.nanoTime()-dtUs_run)/1000;
            log.info("inference took "+(dtUs_run*1e-3f)+" ms");
            log.info("copyTo took "+(dtUs_copyTo*1e-3f)+" ms");
            return resultPositions; */

            /* // UNCOMMENT THIS SECTION IF THE NETWORK RETURNS 3D HEATMAPS TENSOR AND IT IS COPIED INTO 3D ARRAY //
            if(resultHeatmaps==null){
                final long[] rshape = result.shape();
                String.format("Output tensor shape is %s", Arrays.toString(rshape));
                int hmapH = (int) rshape[1];
                int hmapW = (int) rshape[2]; 
                int nMaps = (int) rshape[3];
                resultHeatmaps=new float[1][hmapH][hmapW][nMaps];
            }
            */ 

            // THE NETWORK RETURNS 3D HEATMAPS TENSOR AND IT IS COPIED INTO 1D ARRAY //
            if(result1d==null){
                final long[] rshape = result.shape();
                String.format("Output tensor shape is %s", Arrays.toString(rshape));
                int hmapH = (int) rshape[1];
                int hmapW = (int) rshape[2]; 
                int nMaps = (int) rshape[3];
                result1d=new float[1*hmapH*hmapW*nMaps];
                
            }  
            FloatBuffer fb=FloatBuffer.wrap(result1d);
            
            //TIMING
            long dtNs_run=(System.nanoTime()-startTime);
            //result.copyTo(resultHeatmaps); // THE NETWORK RETURNS 3D HEATMAPS TENSOR AND IT IS COPIED INTO 3D ARRAY //
            result.writeTo(fb); // THE NETWORK RETURNS 3D HEATMAPS TENSOR AND IT IS COPIED INTO 1D ARRAY //
            long dtNs_copyTo=(System.nanoTime()-startTime-dtNs_run);
            log.info("inference took "+(dtNs_run*1e-6f)+" ms");
            log.info("copyTo took "+(dtNs_copyTo*1e-6f)+" ms");
            
            //return resultHeatmaps[0]; // THE NETWORK RETURNS 3D HEATMAPS TENSOR AND IT IS COPIED INTO 3D ARRAY //
            return result1d; // THE NETWORK RETURNS 3D HEATMAPS TENSOR AND IT IS COPIED INTO 1D ARRAY //
            
            //return result.copyTo(resultHeatmaps)[0]; 
            //return result.copyTo(new float[1][hmapH][hmapW][nMaps])[0];
        }
    }
    
    
    // execute graph just one time to get the shape of the network output
    public static int[] executeGraphStartup(Graph graph, Tensor<Float> image, String inputLayerName, String outputLayerName) {
        if (s==null){ s = new Session(graph); }
        try ( Tensor<Float> result = s.runner().feed(inputLayerName, image).fetch(outputLayerName).run().get(0).expect(Float.class)) {
            long[] outputShapeLong= result.shape(); 
            // cast the output shape of the network to integers.
            int[] outputShape = {(int) outputShapeLong[1],(int) outputShapeLong[2],(int) outputShapeLong[3]};
            return outputShape;
            }
    }
    
    /*    public static float[][][] executeGraph(Graph graph, Tensor<Float> image, String inputLayerName, String outputLayerName) {
        try (Session s = new Session(graph);
                Tensor<Float> result = s.runner().feed(inputLayerName, image).fetch(outputLayerName).run().get(0).expect(Float.class)) {
                    final long[] rshape = result.shape();
                    String.format("Output tensor shape is %s", Arrays.toString(rshape));
                    int hmapH = (int) rshape[1];
                    int hmapW = (int) rshape[2]; 
                    int nMaps = (int) rshape[3]; // how many maps
                    return result.copyTo(new float[1][hmapH][hmapW][nMaps])[0];
        }
    }*/

    static float[][][] executeSession(SavedModelBundle savedModelBundle, Tensor<Float> image, String inputLayerName, String outputLayerName) {
        try (Session s = savedModelBundle.session();
            Tensor<Float> result = s.runner().feed(inputLayerName, image).fetch(outputLayerName).run().get(0).expect(Float.class)) {
            final long[] rshape = result.shape();
            String.format("Output tensor shape is %s", Arrays.toString(rshape));
            int hmapH = (int) rshape[1];
            int hmapW = (int) rshape[2]; 
            int nMaps = (int) rshape[3]; // how many maps
            return result.copyTo(new float[1][hmapH][hmapW][nMaps])[0];
        }
    }
    

    static Tensor executeGraphAndReturnTensor(Graph graph, Tensor<Float> image, String inputLayerName, String outputLayerName) {
        try (Session s = new Session(graph);
                Tensor<Float> result = s.runner().feed(inputLayerName, image).fetch(outputLayerName).run().get(0).expect(Float.class)) {
            return result;
            }
    }
    
    static Tensor executeGraphAndReturnTensorWithBoolean(Graph graph, Tensor<Float> image, String inputLayerName, Tensor<Boolean> inputBool, String inputBoolName, String outputLayerName) {
        try (Session s = new Session(graph);
            Tensor<Float> result = s.runner().feed(inputLayerName, image).feed(inputBoolName, inputBool).fetch(outputLayerName).run().get(0).expect(Float.class)) {
            //result.copyTo(heatMap);
            return result;
        }

    }
    
    static void executeGraphAndReturnTensorWithBooleanArray(float[][][] array, Graph graph, Tensor<Float> image, String inputLayerName, Tensor<Boolean> inputBool, String inputBoolName, String outputLayerName) {
        try (Session s = new Session(graph);
            Tensor<Long> result = s.runner().feed(inputLayerName, image).feed(inputBoolName, inputBool).fetch(outputLayerName).run().get(0).expect(Long.class)) {
            //result.copyTo(heatMap);
            if(array != null){
                result.copyTo(array);
                /*
                for (int i = 0; i < 90; i++) {
                    for (int j = 0; j < 120; j++) {       
                            if (array[0][i][j] == 1)
                                System.out.println(" Ball : " + Float.toString(array[0][i][j]));
                    }
                }
                */
            }
        }
    }

    // In the fullness of time, equivalents of the methods of this class should be auto-generated from
    // the OpDefs linked into libtensorflow_jni.so. That would match what is done in other languages
    // like Python, C++ and Go.
    // see 
    public static class GraphBuilder {

        private Graph g;

        GraphBuilder(Graph g) {
            this.g = g;
        }

        Output<Float> div(Output<Float> x, Output<Float> y) {
            return binaryOp("Div", x, y);
        }

        <T> Output<T> sub(Output<T> x, Output<T> y) {
            return binaryOp("Sub", x, y);
        }

        <T> Output<Float> resizeBilinear(Output<T> images, Output<Integer> size) {
            return binaryOp3("ResizeBilinear", images, size);
        }

        <T> Output<T> expandDims(Output<T> input, Output<Integer> dim) {
            return binaryOp3("ExpandDims", input, dim);
        }

        <T> Output<T> concat(Output<T> x, Output<T> y) {
            return binaryOp("Concat", x, y);
        }

        <T, U> Output<U> cast(Output<T> value, Class<U> type) {
            DataType dtype = DataType.fromClass(type);
            return g.opBuilder("Cast", "Cast")
                    .addInput(value)
                    .setAttr("DstT", dtype)
                    .build()
                    .<U>output(0);
        }

        Output<UInt8> decodeJpeg(Output<String> contents, long channels) {
            return g.opBuilder("DecodeJpeg", "DecodeJpeg")
                    .addInput(contents)
                    .setAttr("channels", channels)
                    .build()
                    .<UInt8>output(0);
        }

        <T> Output<T> constant(String name, Object value, Class<T> type) {
            try (Tensor<T> t = Tensor.<T>create(value, type)) {
                return g.opBuilder("Const", name)
                        .setAttr("dtype", DataType.fromClass(type))
                        .setAttr("value", t)
                        .build()
                        .<T>output(0);
            }
        }

        Output<String> constant(String name, byte[] value) {
            return this.constant(name, value, String.class);
        }

        Output<Integer> constant(String name, int value) {
            return this.constant(name, value, Integer.class);
        }

        Output<Integer> constant(String name, int[] value) {
            return this.constant(name, value, Integer.class);
        }

        Output<Float> constant(String name, float value) {
            return this.constant(name, value, Float.class);
        }

        private <T> Output<T> binaryOp(String type, Output<T> in1, Output<T> in2) {
            return g.opBuilder(type, type).addInput(in1).addInput(in2).build().<T>output(0);
        }

        private <T, U, V> Output<T> binaryOp3(String type, Output<U> in1, Output<V> in2) {
            return g.opBuilder(type, type).addInput(in1).addInput(in2).build().<T>output(0);
        }
    }

}
