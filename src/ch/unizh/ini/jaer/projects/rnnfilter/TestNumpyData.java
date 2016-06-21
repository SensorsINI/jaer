/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.rnnfilter;

import eu.visualize.ini.convnet.EasyXMLReader;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import org.jblas.DoubleMatrix;

/**
 * For debugging the RNNetwork class
 * To import numpy train/test data sets into java framework to test the network implementation on java
 * @author jithendar
 */
public class TestNumpyData {
    
    String name;
    String type;
    String dob;
    
    int nSamples;
    
    RNNetwork rnnetwork = new RNNetwork();
    
    
    double[][][] testData; //double matrix to store the test data
    int[] testLabel; //double matrix (single dimension) to store the test label
    
    public TestNumpyData() {
    }
    
    public TestNumpyData(File f) throws IOException {
        this.loadFromXML(f);
    }
    
    public void loadFromXML(File f) throws IOException{
        EasyXMLReader dataReader;
        dataReader = new EasyXMLReader(f);
        if (!dataReader.hasFile()) {
            log.log(Level.WARNING, "No file for reader; file={0}", dataReader.getFile());
            throw new IOException("Exception thrown in EasyXMLReader for file " + f);
        }
        this.name = dataReader.getRaw("name");
        this.type = dataReader.getRaw("type");
        this.dob = dataReader.getRaw("dob");
        
        this.nSamples = dataReader.getInt("nSamples");
        
        log.info(String.format("Reading a network with name=%s, type=%s, dob=%s nSamples=%s", this.name, this.type, this.dob, this.nSamples));
        
        int timeLength = dataReader.getInt("timeLength");
        int freqLength = dataReader.getInt("freqLength");
        
        this.testLabel = new int[this.nSamples];
        this.testData = new double[this.nSamples][timeLength][freqLength];
        for(int i = 0;i < this.nSamples;i++) {
            EasyXMLReader sampleReader = dataReader.getNode("Sample",i);
            int index = sampleReader.getInt("index");
            float[] tmpSample = sampleReader.getAsciiFloatArr("data");
            this.testData[index] = TestNumpyData.convertFlatToDim2(tmpSample, freqLength);
            EasyXMLReader labelReader = dataReader.getNode("Label",i);
            index = labelReader.getInt("index");
            float[] tmpLabel = labelReader.getAsciiFloatArr("data");
            this.testLabel[index] = TestNumpyData.indexOfMaxValue(tmpLabel);
        }
    }
    
    public static double[][] convertFlatToDim2 (float[] tmpW, int hiddenSize) {
        int inputSize = tmpW.length/hiddenSize;
        double[][] output = new double[inputSize][hiddenSize];
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                output[i][j] = (double) tmpW[i*hiddenSize+j];
            }
        }
        return output;
    }
    
    public static int indexOfMaxValue (float[] input) {
        int index = 0;
        float tmpMax = input[0];
        for (int i = 1; i<input.length; i++) {
            if (input[i] > tmpMax) {
                tmpMax = input[i];
                index = i;
            }
        }
        return index;
    }
    

    public static float[] DMToFloat(DoubleMatrix doubleMatrix) {
        int length = doubleMatrix.length;
        float[] floatArray = new float[length];
        for (int i = 0;i<length;i++) {
            floatArray[i] = (float) doubleMatrix.get(i);
        }
        return floatArray;
    }
    
    public void testingNetwork() {
        int count = 0;
        for (int i = 0;i < this.nSamples; i++) {
            DoubleMatrix tempOutput;
            tempOutput = DoubleMatrix.zeros(this.nSamples);
            for (int j = 0; j < this.testData[i].length; j++) {
                tempOutput = this.rnnetwork.output(this.testData[i][j]);
            }
            int index = TestNumpyData.indexOfMaxValue(TestNumpyData.DMToFloat(tempOutput));
            if (index == this.testLabel[i]) { count++; }
            this.rnnetwork.resetNetworkLayers();
        }
    }
    
}
