package ch.unizh.ini.jaer.projects.ziyispikingcnn;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import eu.visualize.ini.convnet.EasyXMLReader;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by ziyihua on 29/03/16.
 *
 * This filter uses a spiking convolutional neural network for online classification of visual images. The major application
 * of this filter is MNIST_DVS database.
 *
 * Options and parameters:
 *
 *  -Process Input
 *
 *  Please select this before loading a network. These two methods have different network structures.
 *
 *  batch: One packet of spikes is processed synchronously. Classification therefore depends on packet size.
 *
 *  spike: A purely spike-based version. Each spike is fed into network sequetially. This method does not depend on packet size.
 *
 * -Filter actions
 *
 *  LoadLabelFile: Load a txt file containing timestamps and label of digits. The file should be comprised of three columns:
 *  starting time of a digit, ending time of a digit and its label. They should be separated by space. If you load a label,
 *  please remeber to also tick the "labelsAvailable" in the "Files" section.
 *
 *  LoadCNNFromXML: Load an XML file containing a CNN exported from DeepLearnToolbox by cnntoxml.m.
 *  Please select batch or spike for processing input before loading a network.
 *
 * -Application (only one option should be chosen)
 *
 *  MNIST: If the loaded network is trained on MNIST and the aedat file is MNIST_DVS database.
 *
 *  robotSteering: If the loaded network is trained on robot steering videos and the aedat file is robot steering.
 *
 * -Display
 *
 *  showAccuracy: Shows the classification accuracy up to the current digit. Please only select this if you've loaded a
 *  label file and also select "labelsAvailable".
 *
 *  showDigitsAccuracy: Shows the classification accuracy of each digit up to the current digit.
 *  Please only select this if you've loaded a label file and also select "labelsAvailable".
 *
 *  showLatency: Shows the latency of the current digit and the average latency of all digits up to now for getting the correct prediction.
 *  Please only select this if you've loaded a label file and also select "labelsAvailable".
 *
 *  showOutputAsBarChart: Displays activity of output units as bar chart, where height indicates activation.
 *
 * -Files
 *
 *  labelsAvailable: Please select this if a label file has been loaded. If you select this without loading a label file,
 *  you might get NullPointException error. Reseting network between digits is only possible if labelsAvailable is selected.
 *
 * -Median Tracker (only applied to MNIST)
 *
 *  medianTracker: Get the median of a moving digit which is dependent on packet size. If you don't select this, please
 *  specify centerX and centerY.
 *
 *  centerX/centerY: Specify the x and y coordinates of the center of digits. This approximates a static center for moving digits.
 *
 * -Parameters
 *
 *  decayConstOutput: Decay constant of output neurons. A reasonable range is 0.05-0.2. This is only applied to output neuron
 *  if you don't select "reset".
 *
 *  negLimit: Negative limit on membrane potential. If you select "reset", set this number to a large negative number or -Inf.
 *  Otherwise a reasonable range is from -0.5 to -2.0.
 *
 *  reset: Resets the network between each digit. Select this only when you load a label file and also select "labelsAvailable".
 *
 *  tRef: Refractory period. Only applicable to "spike" in "Processing Input". It should be a small number in the order of e-2 to e-3
 *  and negLimit as well as threshold should be tuned accordingly if tRef is not set to 0.
 *
 *  threshold: Threshold for neuron firing. For batch version, a reasonable range is 0.5-1.5. For purely spike-based version,
 *  a reasonable range is 0.9-1.7.
 *
 *
 *
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SpikingCNN extends EventFilter2D implements FrameAnnotater {

    private static Logger log = Logger.getLogger("net.sf.jaer");

    //application
    private boolean MNIST = getBoolean("MNIST",true);
    private boolean robotSteering = getBoolean("robotSteering",false);

    //files: convnet xml file and label txt file
    private String lastXMLFilename = getString("lastXMLFilename", "cnn_test.xml");
    private String lastTXTFilename = getString("lastTXTFilename", "labels_new.txt");
    private boolean labelsAvailable = getBoolean("labelsAvailable",false);

    //display
    private boolean showOutputAsBarChart = getBoolean("showOutputAsBarChart", true);
    private boolean showLatency = getBoolean("showLatency",false);
    private boolean showAccuracy = getBoolean("showAccuracy",false);
    private boolean showDigitsAccuracy = getBoolean("showDigitsAccuracy",false);

    //network parameters
    public SpikingCnnStructure.Network net = new SpikingCnnStructure.Network();
    private boolean reset = getBoolean("reset",false);
    private float tRef = getFloat("tRef", 0.0f); //refractory period
    private float threshold = getFloat("threshold", 1.5f); //threshold for neuron firing
    private float negLimit = getFloat("negLimit", -1.3f); //negative limit for membrane potential, used when network is not reset
    private float decayConstOutput = getFloat("decayConstOutput",0.001f); //decay constant for output neurons, used when network is not reset

    //median tracker
    private boolean medianTracker = getBoolean("medianTracker",false);
    private int centerX = getInt("centerX",108);
    private int centerY = getInt("centerY",20);

    //ways of processing input spikes
    private boolean batch = getBoolean("batch",false);
    private float batchsize = getFloat("batchsize",0.02f);
    private boolean spike = getBoolean("spike",true);

    final String app = "Application", disp = "Display", param = "Parameters", inPro = "Processing Input", medTr = "Median Tracker", file = "Files";

    public SpikingCNN(AEChip chip) throws IOException {
        super(chip);
        //applications
        setPropertyTooltip(app, "robotSteering","the cnn is for robot steering");
        setPropertyTooltip(app, "MNIST","the cnn is for MNIST");
        //display
        setPropertyTooltip(disp, "showOutputAsBarChart", "displays activity of output units as bar chart, where height indicates activation");
        setPropertyTooltip(disp, "showLatency","show latency of getting the correct prediction");
        setPropertyTooltip(disp, "showAccuracy","show accuracy");
        setPropertyTooltip(disp, "showDigitsAccuracy","show accuracy of each digit in the case of MNIST");
        //processing input
        setPropertyTooltip(inPro, "batch", "process input spikes in a batched version; accuracy and latency depend on batchsize and refractory period is set to zero; please specify batchsize below");
        setPropertyTooltip(inPro, "batchsize", "unit:second; all spikes/events in this time interval are processed synchronously");
        setPropertyTooltip(inPro, "spike", "process input spikes in a purely spike based manner; no dependency on packet size");
        //parameters
        setPropertyTooltip(param, "reset","reset network between each digits: select this only when label file is available");
        setPropertyTooltip(param, "negLimit","bound on negative potential");
        setPropertyTooltip(param, "tRef","refractory period");
        setPropertyTooltip(param, "threshold","threshold for spiking");
        setPropertyTooltip(param, "decayConstOutput","decay constant for the output layer");
        //median tracker
        setPropertyTooltip(medTr, "medianTracker", "get the median of a moving digit; dependent on packet size; you can untick this option and specify the coordinates in fields above");
        setPropertyTooltip(medTr, "centerX", "x coordinate of the digit");
        setPropertyTooltip(medTr, "centerY", "y coordinate of the digit");
        //load files
        setPropertyTooltip(file,"LoadCNNFromXML","Load an XML file containing a CNN exported from DeepLearnToolbox by cnntoxml.m; please select batch or spike for processing input before loading a network");
        setPropertyTooltip(file,"LoadLabel","Load a txt file containing timestamps and label of digits");
        setPropertyTooltip(file,"labelsAvailable", "a file with label and corresponding ts is present and loaded");
        initFilter();

    }

    //median tracker
    int xMedian, yMedian;
    //for calculation of accuracy and latency
    int totalCount = 0;
    int correctTotalCount = 0;
    int label = 0;
    int finalPredict = 0;
    int predict = 0;
    float prevTimeBatch  = 0f;
    float currTimeBatch = batchsize;
    float[][] digitsAccuracy = new float[10][2];
    List<Float> endingTimes = new ArrayList<>();
    List<Float> startingTimes = new ArrayList<>();
    List<Integer> labels = new ArrayList<>();
    float digitStart = 0.0f;
    float latency = 0.0f;
    float sumLatency = 0.0f;
    float meanLatency = 0.0f;
    boolean changeDigit = false;
    boolean winnerGot = false;
    int numLabel = 0;
    //timestamps of the current spike and the previous spike
    float currTime = 0.0f;
    float prevTime = 0.0f;
    //store spikes and active neurons for propagateSpikingCnn
    public List<Pair> spikeList = new ArrayList<>();
    public List<Integer> activeSet = new ArrayList<>();
    public List<Pair> activeSetSub = new ArrayList<>();
    //store addresses and timestamps for the batched method
    public List<Float> batchTimes = new ArrayList<>();
    public List<Float> batchTimesRest = new ArrayList<>();
    public List<Integer> batchX = new ArrayList<>();
    public List<Integer> batchXRest = new ArrayList<>();
    public List<Integer> batchY = new ArrayList<>();
    public List<Integer> batchYRest = new ArrayList<>();
    int countEvents = 0;



    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        if(medianTracker){
            xMedian=0;
            yMedian=0;
        }else{
            xMedian=centerX;
            yMedian=centerY;
        }
    }


    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in){
        List<Float> times = new ArrayList<>();
        List<Integer> y = new ArrayList<>();
        List<Integer> x = new ArrayList<>();
        List<Integer> y_clone = new ArrayList<>();
        List<Integer> x_clone = new ArrayList<>();


        for(BasicEvent o: in) {
            if (((PolarityEvent) o).polarity == PolarityEvent.Polarity.On){
                float ts;
                if (o.timestamp>=0) {
                    ts = (float) (o.timestamp / 1e6);
                }else ts = (float)(o.timestamp/1e6+4294.967296f);
                times.add(ts);
                y.add((int) o.y + 1);
                x.add((int) o.x + 1);
                y_clone.add((int) o.y + 1);
                x_clone.add((int) o.x + 1);
            }
        }

        //reset network if digit changed and reset==true
        if (reset){
            if (changeDigit){
                resetNetwork();
                if (!showLatency){
                    changeDigit = false;
                }
            }
        }

        //calculate latency and reset if the digit is changed
        if(showLatency) {
            if (changeDigit) {
                if (!times.isEmpty()) {
                    digitStart = startingTimes.get(0);
                    changeDigit = false;
                    winnerGot = false;
                }
            }
        }


        if(spike) {
            if (!x_clone.isEmpty()) {

                //use median tracker to process input
                if (MNIST && medianTracker) {
                    xMedian = getMedian(x_clone);
                    yMedian = getMedian(y_clone);
                }

                //process spikes such that they're consistent with the size of input layer
                List<List<Integer>> coordinates_index_new = DVStoPixelInputSpace(x, y, xMedian, yMedian);
                List<Float> times_new = new ArrayList<>();
                if (MNIST) {
                    for (int i = 0; i < coordinates_index_new.size(); i++) {
                        times_new.add(times.get(coordinates_index_new.get(i).get(2)));
                    }
                } else if (robotSteering) {
                    times_new = times;
                }

                //propagate through network
                propagateSpikingCnn(coordinates_index_new, tRef, threshold, times_new);


                //make prediction based on output scores
                prevTimeBatch = times.get(times.size() - 1);
                double max = 0;
                int prediction = 0;
                for (int i = 0; i < net.outSumSpikes.length; i++) {
                    if (net.outSumSpikes[i] >= max) {
                        max = net.outSumSpikes[i];
                        prediction = i;
                    }
                }
                predict = prediction;
                //check if the neuron with the max. score corresponds to label
                if (showLatency) {
                    if (winnerGot == false) {
                        if (predict == labels.get(0)) {
                            latency = prevTimeBatch - digitStart;
                            winnerGot = true;
                            sumLatency += latency;
                        }
                    }
                }


                if (labelsAvailable) {
                    if (times.get(times.size() - 1) > endingTimes.get(0)) {
                        finalPredict = predict;
                        label = labels.get(0);
                        totalCount++;
                        digitsAccuracy[label][1] += 1;
                        if (label == predict) {
                            correctTotalCount++;
                            digitsAccuracy[label][0] += 1;
                        }
                        labels.remove(0);
                        endingTimes.remove(0);
                        startingTimes.remove(0);
                        numLabel++;
                        if (showLatency) {
                            if (winnerGot == false) {
                                latency = prevTimeBatch - digitStart;
                                sumLatency += latency;
                            }
                            meanLatency = sumLatency / numLabel;
                        }
                        changeDigit = true;
                    }
                }
            }
        }else if (batch) {
            if (!times.isEmpty()) {
                if (times.get(times.size()-1) <= currTimeBatch) {
                    batchTimes.addAll(times);
                    batchX.addAll(x);
                    batchY.addAll(y);
                    countEvents+=times.size();
                } else {
                    for (int i = 0; i < times.size(); i++) {
                        if (times.get(i)<=currTimeBatch){
                            batchTimes.add(times.get(i));
                            batchX.add(x.get(i));
                            batchY.add(y.get(i));
                        }else{
                            batchTimesRest.add(times.get(i));
                            batchXRest.add(x.get(i));
                            batchYRest.add(y.get(i));
                        }
                    }
                }

                while(!batchTimesRest.isEmpty()){

                    List<Integer> batchXClone = new ArrayList<>();
                    List<Integer> batchYClone = new ArrayList<>();

                    batchXClone.addAll(batchX);
                    batchYClone.addAll(batchY);

                    //use median tracker to process input
                    if (MNIST && medianTracker) {
                        xMedian = getMedian(x_clone);
                        yMedian = getMedian(y_clone);
                    }

                    //process spikes such that they're consistent with the size of input layer
                    List<List<Integer>> coordinates_index_new = DVStoPixelInputSpace(batchX, batchY, xMedian, yMedian);
                    List<Float> times_new = new ArrayList<>();
                    if (MNIST) {
                        for (int i = 0; i < coordinates_index_new.size(); i++) {
                            times_new.add(batchTimes.get(coordinates_index_new.get(i).get(2)));
                        }
                    } else if (robotSteering) {
                        times_new = batchTimes;
                    }

                    //propagate through network
                    propagateBatchSpikingCnn(coordinates_index_new, tRef, threshold, times_new);


                    //make prediction based on output scores
                    prevTimeBatch = currTimeBatch;
                    currTimeBatch += batchsize;
                    double max = 0;
                    int prediction = 0;
                    for (int i = 0; i < net.outSumSpikes.length; i++) {
                        if (net.outSumSpikes[i] >= max) {
                            max = net.outSumSpikes[i];
                            prediction = i;
                        }
                    }
                    predict = prediction;
                    //check if the neuron with the max. score corresponds to label
                    if (showLatency) {
                        if (winnerGot == false) {
                            if (predict == labels.get(0)) {
                                latency = prevTimeBatch - digitStart;
                                winnerGot = true;
                                sumLatency += latency;
                            }
                        }
                    }


                    if (labelsAvailable) {
                        if (prevTimeBatch > endingTimes.get(0)) {
                            finalPredict = predict;
                            label = labels.get(0);
                            totalCount++;
                            digitsAccuracy[label][1] += 1;
                            if (label == predict) {
                                correctTotalCount++;
                                digitsAccuracy[label][0] += 1;
                            }
                            labels.remove(0);
                            endingTimes.remove(0);
                            startingTimes.remove(0);
                            numLabel++;
                            if (showLatency) {
                                if (winnerGot == false) {
                                    latency = prevTimeBatch - digitStart;
                                    sumLatency += latency;
                                }
                                meanLatency = sumLatency / numLabel;
                            }
                            changeDigit = true;
                        }
                    }

                    batchTimes.clear();
                    batchX.clear();
                    batchY.clear();

                    if (!batchTimesRest.isEmpty()) {
                        if (batchTimesRest.get(batchTimesRest.size()-1) <= currTimeBatch) {
                            batchTimes.addAll(batchTimesRest);
                            batchX.addAll(batchXRest);
                            batchY.addAll(batchYRest);
                            batchTimesRest.clear();
                            batchXRest.clear();
                            batchYRest.clear();
                        } else {
                            int i = 0;
                            while(batchTimesRest.get(i)<=currTimeBatch){
                                batchTimes.add(batchTimesRest.get(i));
                                batchX.add(batchXRest.get(i));
                                batchY.add(batchYRest.get(i));
                                i++;
                            }
                            batchTimesRest.subList(0,i).clear();
                            batchXRest.subList(0,i).clear();
                            batchYRest.subList(0,i).clear();
                        }
                    }
                }

            }
        }
        return in;
    }

    public void doLoadNetworkFromXML() throws IOException {
        JFileChooser c = new JFileChooser(lastXMLFilename);
        FileFilter filt = new FileNameExtensionFilter("XML File", "xml");
        c.addChoosableFileFilter(filt);
        c.setSelectedFile(new File(lastXMLFilename));
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastXMLFilename = c.getSelectedFile().toString();
        putString("lastXMLFilename", lastXMLFilename);
        if (spike!=batch) {
            loadFromXMLFile(c.getSelectedFile());
        }else{
            log.info("Please select exact one way of processing input.");
        }
    }

    public void doLoadLabelFile() throws IOException {
        JFileChooser c = new JFileChooser(lastTXTFilename);
        FileFilter filt = new FileNameExtensionFilter("TXT File", "txt");
        c.addChoosableFileFilter(filt);
        c.setSelectedFile(new File(lastTXTFilename));
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastTXTFilename = c.getSelectedFile().toString();
        putString("lastTXTFilename", lastTXTFilename);
        ReadInput(lastTXTFilename);
    }

    public void ReadInput(String filename) throws IOException{
        String line;
        try (
                InputStream fis = new FileInputStream(filename);
                InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
                BufferedReader br = new BufferedReader(isr);
        ) {
            while ((line = br.readLine()) != null) {
                endingTimes.add((float)(Float.parseFloat(line.split(" ")[2])/1e6));
                startingTimes.add((float)(Float.parseFloat(line.split(" ")[1])/1e6));
                labels.add((int)(Float.parseFloat(line.split(" ")[0])));
            }
        }
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 2; j++) {
                digitsAccuracy[i][i]=0f;
            }
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable){

        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        gl.glColor3f(0, 0, 1);
        gl.glLineWidth(4);

        if (labelsAvailable) {
                GLUT glut1 = new GLUT();
                gl.glRasterPos2f(0, 50);
                glut1.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("label = %s", Integer.toString(label)));
                GLUT glut2 = new GLUT();
                gl.glRasterPos2f(0, 45);
                glut2.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("prediction = %s", Integer.toString(finalPredict)));
                if (showAccuracy) {
                    GLUT glut3 = new GLUT();
                    gl.glRasterPos2f(0, 40);
                    glut3.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("accuracy = %s", String.format("%.2f", ((float) correctTotalCount * 100f) / ((float) totalCount))));
                }
                if (showDigitsAccuracy) {
                    GLUT glut4 = new GLUT();
                    gl.glRasterPos2f(0, 35);
                    glut4.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("0:%s " + " 1:%s " + " 2:%s " + " 3:%s " + " 4:%s " + " 5:%s " + " 6:%s " + " 7:%s " + " 8:%s " + " 9:%s ", String.format("%.1f", (100f * digitsAccuracy[0][0] / digitsAccuracy[0][1])), String.format("%.1f", (100f * digitsAccuracy[1][0] / digitsAccuracy[1][1])), String.format("%.1f", (100f * digitsAccuracy[2][0] / digitsAccuracy[2][1])), String.format("%.1f", (100f * digitsAccuracy[3][0] / digitsAccuracy[3][1])), String.format("%.1f", (100f * digitsAccuracy[4][0] / digitsAccuracy[4][1])), String.format("%.1f", (100f * digitsAccuracy[5][0] / digitsAccuracy[5][1])), String.format("%.1f", (100f * digitsAccuracy[6][0] / digitsAccuracy[6][1])), String.format("%.1f", (100f * digitsAccuracy[7][0] / digitsAccuracy[7][1])), String.format("%.1f", (100f * digitsAccuracy[8][0] / digitsAccuracy[8][1])), String.format("%.1f", (100f * digitsAccuracy[9][0] / digitsAccuracy[9][1]))));
                }
                if (showLatency) {
                    GLUT glut5 = new GLUT();
                    gl.glRasterPos2f(0, 30);
                    glut5.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("current_latency = %s " + " average_latency = %s", String.format("%.3f", latency), String.format("%.3f", meanLatency)));
                }
        }else{
            GLUT glut2 = new GLUT();
            gl.glRasterPos2f(0, 45);
            glut2.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("prediction = %s", Integer.toString(predict)));
        }

        if (MNIST) {
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glVertex2d(xMedian - 13, yMedian + 14);
            gl.glVertex2d(xMedian + 14, yMedian + 14);
            gl.glVertex2d(xMedian + 14, yMedian - 13);
            gl.glVertex2d(xMedian - 13, yMedian - 13);
            gl.glEnd();
        }


        if(showOutputAsBarChart) {
            final float lineWidth = 2;
            annotateHistogram(gl, chip.getSizeX(), chip.getSizeY(), lineWidth, Color.RED);
        }

        gl.glPopMatrix();
    }

    public void outputHistogram(GL2 gl, int width, int height){

        float dx = (float) width/net.outSumSpikes.length;
        float sy = (float) height/8;

        gl.glBegin(GL.GL_LINE_STRIP);
        for (int i = 0; i < net.outSumSpikes.length; i++) {
            float y = 1 + (sy * (float)net.outSumSpikes[i]);
            float x1 = 1 + (dx * i), x2 = x1 + dx;
            gl.glVertex2f(x1, 1);
            gl.glVertex2f(x1, y);
            gl.glVertex2f(x2, y);
            gl.glVertex2f(x2, 1);
        }
        gl.glEnd();
    }

    public void annotateHistogram(GL2 gl, int width, int height, float lineWidth, Color color) {
        gl.glPushAttrib(GL2ES3.GL_COLOR | GL.GL_LINE_WIDTH);
        gl.glLineWidth(lineWidth);
        float[] ca = color.getColorComponents(null);
        gl.glColor4fv(ca, 0);
        outputHistogram(gl, width, height);
        gl.glPopAttrib();
    }

    //load network from xml file
    public void loadFromXMLFile(File f) throws IOException{
        try {
            EasyXMLReader networkReader = new EasyXMLReader(f);
            if (!networkReader.hasFile()) {
                log.warning("No file for reader; file=" + networkReader.getFile());
                return;
            }

            int nLayers = networkReader.getNodeCount("Layer");
            for (int i = 0; i < nLayers; i++) {
                SpikingCnnStructure.Layer layer = new SpikingCnnStructure.Layer();
                net.layers.add(i,layer);
            }

            for (int i = 0; i < nLayers; i++) {
                EasyXMLReader layerReader = networkReader.getNode("Layer", i);
                String type = layerReader.getRaw("type");
                switch (type) {
                    case "i": {
                        net.layers.get(i).type = "i";
                        net.layers.get(i).dimX = layerReader.getInt("dimx");
                        net.layers.get(i).dimY = layerReader.getInt("dimy");
                        int dim = net.layers.get(i).dimX;
                        if (spike){
                            float[][] zeros = new float[dim][dim];
                            for (int j = 0; j < dim; j++) {
                                for (int k = 0; k < dim; k++) {
                                    zeros[j][k]=0.0f;
                                }
                            }
                            net.layers.get(i).refracEnd.add(zeros);
                        }
                    }
                    break;
                    case "c": {
                        net.layers.get(i).type = "c";
                        net.layers.get(i).inMaps= layerReader.getInt("inputMaps");
                        net.layers.get(i).outMaps = layerReader.getInt("outputMaps");
                        net.layers.get(i).kernelSize = layerReader.getInt("kernelSize");
                        int kernelSize = layerReader.getInt("kernelSize");
                        net.layers.get(i).bias = layerReader.getBase64FloatArr("biases");
                        float[] kernels = layerReader.getBase64FloatArr("kernels");
                        for (int j = 0; j < net.layers.get(i).inMaps; j++) {
                            for (int k = 0; k < net.layers.get(i).outMaps; k++) {
                                int a = (j*net.layers.get(i).outMaps+k)*kernelSize*kernelSize;
                                int b = a+kernelSize*kernelSize;
                                float[][] mat = ArrToMat(Arrays.copyOfRange(kernels,a,b),kernelSize);
                                net.layers.get(i).kernel.add(flipKernel(mat));
                            }
                        }
                        net.layers.get(i).dimX = net.layers.get(i-1).dimX-kernelSize+1;
                        net.layers.get(i).dimY = net.layers.get(i-1).dimY-kernelSize+1;
                        int correct_size = net.layers.get(i).dimX;
                        if (batch) {
                            float[][] correctly_sized_zeros = new float[correct_size][correct_size];
                            for (int j = 0; j < correct_size; j++) {
                                for (int k = 0; k < correct_size; k++) {
                                    correctly_sized_zeros[j][k] = 0.0f;
                                }
                            }
                            for (int j = 0; j < net.layers.get(i).outMaps; j++) {
                                net.layers.get(i).memPot.add(correctly_sized_zeros);
                                net.layers.get(i).spikes.add(correctly_sized_zeros);
                            }
                        }else if (spike){
                            net.layers.get(i).membranePot = new float[net.layers.get(i).outMaps][correct_size*correct_size];
                            float[][] zeros = new float[net.layers.get(i).outMaps][correct_size*correct_size];
                            for (int j = 0; j < correct_size*correct_size; j++) {
                                for (int k = 0; k < net.layers.get(i).outMaps; k++) {
                                    net.layers.get(i).membranePot[k][j]=0.0f;
                                    zeros[k][j]=0.0f;
                                }
                            }
                            net.layers.get(i).refracEnd.add(zeros);
                        }
                    }
                    break;
                    case "s": {
                        net.layers.get(i).inMaps=net.layers.get(i-1).outMaps;
                        net.layers.get(i).outMaps=net.layers.get(i).inMaps;
                        net.layers.get(i).type= "s";
                        net.layers.get(i).scale = layerReader.getInt("averageOver");
                        net.layers.get(i).bias = layerReader.getBase64FloatArr("biases");
                        net.layers.get(i).dimX = net.layers.get(i-1).dimX/net.layers.get(i).scale;
                        net.layers.get(i).dimY = net.layers.get(i-1).dimY/net.layers.get(i).scale;
                        int correct_size = net.layers.get(i).dimX;
                        if (batch) {
                            float[][] correctly_sized_zeros = new float[correct_size][correct_size];
                            for (int j = 0; j < correct_size; j++) {
                                for (int k = 0; k < correct_size; k++) {
                                    correctly_sized_zeros[j][k] = 0.0f;
                                }
                            }
                            for (int j = 0; j < net.layers.get(i).outMaps; j++) {
                                net.layers.get(i).memPot.add(correctly_sized_zeros);
                                net.layers.get(i).spikes.add(correctly_sized_zeros);
                            }
                        }else if (spike){
                            net.layers.get(i).membranePot = new float[net.layers.get(i).outMaps][correct_size*correct_size];
                            float[][] zeros = new float[net.layers.get(i).outMaps][correct_size*correct_size];
                            for (int j = 0; j < correct_size*correct_size; j++) {
                                for (int k = 0; k < net.layers.get(i).outMaps; k++) {
                                    net.layers.get(i).membranePot[k][j]=0.0f;
                                    zeros[k][j]=0.0f;
                                }
                            }
                            net.layers.get(i).refracEnd.add(zeros);
                        }
                    }
                    break;
                }
            }
            net.fcBias=networkReader.getBase64FloatArr("outputBias");
            int outputclass = net.fcBias.length;
            net.fcWeights=ArrToMatffW(networkReader.getBase64FloatArr("outputWeights"),net.layers.get(nLayers-1).dimX,net.layers.get(nLayers-1).outMaps,outputclass);
            net.outMemPot = new float[outputclass];
            net.outSumSpikes = new double[outputclass];
            net.outRefracEnd = new float[outputclass];
            for (int i = 0; i < outputclass; i++) {
                net.outMemPot[i]=0.0f;
                net.outRefracEnd[i]=0.0f;
                net.outSumSpikes[i]=0;
            }
            log.info(toString(networkReader));
        } catch (RuntimeException e) {
            log.warning("couldn't load net from file: caught " + e.toString());
            e.printStackTrace();
        }
    }

    //log information about the network loaded
    public String toString(EasyXMLReader networkReader) {
        StringBuilder sb = new StringBuilder("DeepLearnCnnNetwork: \n");
        sb.append(String.format("name=%s, dob=%s, type=%s\nnotes=%s\n", networkReader.getRaw("name"), networkReader.getRaw("dob"), networkReader.getRaw("type"), networkReader.getRaw("notes")));
        sb.append(String.format("nLayers=%d\n", networkReader.getNodeCount("Layer"))+"\n");
        sb.append(String.format("index=0 Input dimx=%s dimy=%s",net.layers.get(0).dimX,net.layers.get(0).dimY)+"\n");
        for (int i = 1; i < networkReader.getNodeCount("Layer"); i++) {
            SpikingCnnStructure.Layer layer = net.layers.get(i);
            if (layer.type=="c"){
                sb.append(String.format("index=%s Convolution nInputMaps=%s nOutputMaps=%s kernelsize=%s",i,layer.inMaps,layer.outMaps,layer.kernelSize)+"\n");
            }else{
                sb.append(String.format("index=%s Subsample nInputMaps=%s nOutputMaps=%s scale=%s",i,layer.inMaps,layer.outMaps,layer.scale)+"\n");
            }
        }
        sb.append(String.format("Output bias=float[%s] weight=float[%s][%s]",net.fcBias.length,net.fcWeights.length,net.fcWeights[0].length));
        return sb.toString();

    }

    //convert array in xml to matrix
    public float[][] ArrToMat(float[] array, int kernelsize){
        float[][] matrix = new float[kernelsize][kernelsize];
        for (int i = 0; i < kernelsize; i++) {
            for (int j = 0; j < kernelsize; j++) {
                matrix[j][i] = array[i*kernelsize+j];
            }
        }
        return matrix;
    }

    //convert array in xml to matrix (weight to output layer)
    public float[][] ArrToMatffW(float[] array, int mapsize, int inmaps, int outputclass){
        float[][] matrix = new float[outputclass][mapsize*mapsize*inmaps];
        for (int i = 0; i < matrix[0].length; i++) {
            for (int j = 0; j < matrix.length; j++) {
                matrix[j][i] = array[i*outputclass+j];
            }
        }
        return matrix;
    }

    //flip kernel for convolution
    public float[][] flipKernel(float[][] kernel){
        int kernelWidth = kernel.length;
        int kernelHeight = kernel[0].length;
        float[][] kernel_flipped = new float[kernelWidth][kernelHeight];
        for (int i = 0; i < kernelWidth; i++) {
            for (int j = 0; j < kernelHeight; j++) {
                kernel_flipped[i][j]=kernel[kernelWidth-1-i][kernelHeight-1-j];
            }
        }
        return kernel_flipped;
    }

    //reset network if reset==true
    public void resetNetwork(){
        for (int i = 0; i < net.layers.size(); i++) {
            SpikingCnnStructure.Layer current_layer = net.layers.get(i);
            if (batch) {
                float[][] correctly_sized_zeros = new float[current_layer.dimX][current_layer.dimY];
                for (int j = 0; j < current_layer.dimX; j++) {
                    for (int k = 0; k < current_layer.dimY; k++) {
                        correctly_sized_zeros[j][k] = 0.0f;
                    }
                }
                for (int j = 0; j < current_layer.outMaps; j++) {
                    current_layer.memPot.set(j, correctly_sized_zeros);
                    current_layer.spikes.set(j, correctly_sized_zeros);
                }
            }else if (spike){
                float[][] correctly_sized_zeros = new float[current_layer.outMaps][current_layer.dimX * current_layer.dimY];
                float[][] zeros = new float[current_layer.outMaps][current_layer.dimX * current_layer.dimY];
                for (int j = 0; j < correctly_sized_zeros.length; j++) {
                    for (int k = 0; k < correctly_sized_zeros[0].length; k++) {
                        zeros[j][k] = 0.0f;
                    }
                }
                current_layer.membranePot = zeros;
                net.layers.get(i).refracEnd.add(zeros);
            }
        }
        int outputclass = net.outMemPot.length;
        net.outMemPot = new float[outputclass];
        if (spike) {
            net.outRefracEnd = new float[outputclass];
        }
        net.outSumSpikes = new double[outputclass];
        for (int i = 0; i < outputclass; i++) {
            net.outMemPot[i]=0.0f;
            net.outRefracEnd[i]=0.0f;
            net.outSumSpikes[i]=0;
        }
    }

    //use median tracker to process input
    public int getMedian(List<Integer> l){
        int median;
        HashSet<Integer> lhs= new HashSet<>();
        lhs.addAll(l);
        l.clear();
        l.addAll(lhs);
        Collections.sort(l);
        if (l.size()%2==0){
            median = (int)Math.ceil((double)((l.get(l.size()/2)+l.get(l.size()/2-1)))/2.0);
        }else{
            median = l.get((l.size()-1)/2);
        }
        return median;
    }

    //convert input spikes to be consistent with size of the input layer for median tracker
    public List<List<Integer>> DVStoPixelInputSpace(List<Integer> x, List<Integer> y, int x_median, int y_median){
        List<List<Integer>> InputSpace = new ArrayList<>();
        if (MNIST) {
            for (int i = 0; i < x.size(); i++) {
                int x_new = x.get(i) - x_median + 14;
                int y_new = y.get(i) - y_median + 14;
                if (x_new >= 1 && x_new <= 28 && y_new >= 1 && y_new <= 28) {
                    int x_new_transformed = 28-y_new;
                    int y_new_transformed = x_new-1;
                    List<Integer> triplets = Arrays.asList(x_new_transformed, y_new_transformed, i);
                    InputSpace.add(triplets);
                }
            }
        }
        if (robotSteering){
            int dimx = net.layers.get(0).dimX;
            int dimy = net.layers.get(0).dimY;
            for (int i = 0; i < x.size(); i++) {
                int x_new = (int) Math.floor(x.get(i) * (dimx-1) * 1.0 / (chip.getSizeX()-1) + (chip.getSizeX()-dimx)*1.0 / (chip.getSizeX()-1));
                int y_new = (int) Math.floor(y.get(i) * (dimy-1) * 1.0 / (chip.getSizeY()-1) + (chip.getSizeY()-dimy)*1.0 / (chip.getSizeY()-1));
                List<Integer> triplets = Arrays.asList(x_new, y_new, i);
                InputSpace.add(triplets);
            }
        }
        return InputSpace;
    }

    public class Pair<S, T> {
        public final S x;
        public final T y;

        public Pair(S x, T y) {
            this.x = x;
            this.y = y;
        }
    }

    public void propagateSpikingCnn(List<List<Integer>> input, float tRef, float threshold, List<Float> ts) {

        for (int i = 0; i < ts.size(); i++) {

            //inputlayer
            prevTime = currTime;
            currTime = ts.get(i);

            if (!spikeList.isEmpty()) {
                spikeList.clear();
            }

            float[][] inputRef = net.layers.get(0).refracEnd.get(0);
            if (inputRef[input.get(i).get(1)][input.get(i).get(0)]<=currTime){
                spikeList.add(new Pair(1, coordinateConversionMatrixToList(input.get(i).get(1) + 1, input.get(i).get(0) + 1, net.layers.get(0).dimX)));
                inputRef[input.get(i).get(1)][input.get(i).get(0)]=currTime+tRef;
                net.layers.get(0).refracEnd.clear();
                net.layers.get(0).refracEnd.add(inputRef);
            }

            for (int j = 0; j < net.layers.size(); j++) {
                //convlayer
                if ("c".equals(net.layers.get(j).type)) {

                    if (!activeSet.isEmpty()) {
                        activeSet.clear();
                    }

                    int kSize = net.layers.get(j).kernelSize;
                    int prevDimX = net.layers.get(j-1).dimX;
                    int currDimX = net.layers.get(j).dimX;

                    List<Integer> uniqueSpikes = new ArrayList<>();
                    for (int k = 0; k < spikeList.size(); k++) {
                        int pos = (int) spikeList.get(k).y;
                        if (!uniqueSpikes.contains(pos)){
                            uniqueSpikes.add(pos);
                        }
                    }

                    //define active set and convolution
                    for (int k = 0; k < uniqueSpikes.size(); k++) {
                        int x = (int) coordinateConversionListToMatrix(uniqueSpikes.get(k), prevDimX).x;
                        int y = (int) coordinateConversionListToMatrix(uniqueSpikes.get(k), prevDimX).y;

                        for (int l = Math.max(1,x-kSize+1); l <= Math.min(x,currDimX); l++) {
                            for (int m = Math.max(1,y-kSize+1); m <= Math.min(y,currDimX); m++) {
                                int pos = coordinateConversionMatrixToList(l,m,currDimX);
                                if (!activeSet.contains(pos)){
                                    activeSet.add(pos);
                                }
                            }
                        }

                    }

                    float[][] mem = net.layers.get(j).membranePot;
                    float[][] ref = net.layers.get(j).refracEnd.get(0);

                    for (int k = 0; k < spikeList.size(); k++) {
                        int inputrow = (int) spikeList.get(k).x;
                        int x = (int) coordinateConversionListToMatrix((int)spikeList.get(k).y,prevDimX).x;
                        int y = (int) coordinateConversionListToMatrix((int)spikeList.get(k).y,prevDimX).y;

                        for (int l = 0; l < net.layers.get(j).outMaps; l++) {
                            float[][] kernel = net.layers.get(j).kernel.get((inputrow-1)*net.layers.get(j).outMaps+l);
                            for (int m = Math.max(0,x-currDimX); m < Math.min(x,kSize); m++) {
                                for (int n = Math.max(0,y-currDimX); n < Math.min(y,kSize); n++) {
                                    int col = coordinateConversionMatrixToList(x-m,y-n,currDimX);
                                    if (ref[l][col-1]<=currTime) {
                                        mem[l][col-1] = mem[l][col-1] + kernel[m][n];
                                    }
                                }
                            }
                        }
                    }

                    spikeList.clear();

                    //check for spiking and negative limit
                    for (int k = 0; k < net.layers.get(j).outMaps; k++) {
                        for (int l = 0; l < activeSet.size(); l++) {
                            int pos = activeSet.get(l)-1;
                            if (mem[k][pos]>=threshold){
                                spikeList.add(new Pair(k+1,pos+1));
                                mem[k][pos]=0.0f;
                                ref[k][pos]=currTime+tRef;
                            }else if (mem[k][pos]<negLimit){
                                mem[k][pos]=negLimit;
                            }
                        }
                    }

                    net.layers.get(j).refracEnd.clear();
                    net.layers.get(j).membranePot=mem;
                    net.layers.get(j).refracEnd.add(ref);

                }else if ("s".equals(net.layers.get(j).type)){

                    if (!activeSetSub.isEmpty()){
                        activeSetSub.clear();
                    }

                    int scale = net.layers.get(j).scale;
                    int prevdimx = net.layers.get(j-1).dimX;
                    int currdimx = net.layers.get(j).dimX;

                    float mem_pot = 1/(((float) scale)*((float) scale));

                    float[][] mem = net.layers.get(j).membranePot;
                    float[][] ref = net.layers.get(j).refracEnd.get(0);

                    for (int k = 0; k < spikeList.size(); k++) {
                        int outmaps = (int) spikeList.get(k).x;
                        int x = (int) coordinateConversionListToMatrix((int)spikeList.get(k).y, prevdimx).x;
                        int y = (int) coordinateConversionListToMatrix((int)spikeList.get(k).y, prevdimx).y;

                        int x_new = (x-1)/scale+1;
                        int y_new = (y-1)/scale+1;
                        int pos_new = coordinateConversionMatrixToList(x_new,y_new,currdimx);
                        Pair p = new Pair(outmaps,pos_new);

                        if (!activeSetSub.contains(p)){
                            activeSetSub.add(p);
                        }

                        if (ref[outmaps-1][pos_new-1]<=currTime) {
                            mem[outmaps - 1][pos_new - 1] = mem[outmaps - 1][pos_new - 1] + mem_pot;
                        }
                    }

                    if (!spikeList.isEmpty()){
                        spikeList.clear();
                    }

                    //check for spiking
                    for (int k = 0; k < activeSetSub.size(); k++) {
                        if (mem[(int)activeSetSub.get(k).x-1][(int)activeSetSub.get(k).y-1]>=threshold){
                            spikeList.add(activeSetSub.get(k));
                            mem[(int)activeSetSub.get(k).x-1][(int)activeSetSub.get(k).y-1]=0.0f;
                            ref[(int)activeSetSub.get(k).x-1][(int)activeSetSub.get(k).y-1]=currTime+tRef;
                        }
                    }

                    net.layers.get(j).membranePot=mem;
                    net.layers.get(j).refracEnd.clear();
                    net.layers.get(j).refracEnd.add(ref);
                }
            }

            int prevdimx = net.layers.get(net.layers.size()-1).dimX;
            List<Integer> spikes = new ArrayList<>();
            for (int j = 0; j < spikeList.size(); j++) {
                int pos_new = coordinateConversionMatrixToList((int)spikeList.get(j).y,(int)spikeList.get(j).x,prevdimx*prevdimx);
                spikes.add(pos_new);
            }

            //ffw*fv
            int d = net.fcWeights.length;

            float[] impulse = new float[d];
            for (int j = 0; j < d; j++) {
                impulse[j]=0.0f;
            }

            for (int j = 0; j < d; j++) {
                for (int k = 0; k < spikes.size(); k++) {
                    impulse[j]=impulse[j]+net.fcWeights[j][spikes.get(k)-1];
                }
            }

            //add bias
            for (int j = 0; j < impulse.length; j++) {
                impulse[j] = impulse[j] + net.fcBias[j];
            }

            //only add input from neurons past their refractory point
            for (int j = 0; j < d; j++) {
                if (net.outRefracEnd[j] >= currTime) {
                    impulse[j] = 0.0f;
                }
            }

            //add input to membrane potential
            for (int j = 0; j < d; j++) {
                net.outMemPot[j]=net.outMemPot[j]+impulse[j];
                if (net.outMemPot[j] < negLimit) {
                    net.outMemPot[j] = negLimit;
                }
            }

            //check for spiking
            net.outSpikes = new int[d];
            for (int j = 0; j < d; j++) {
                if (net.outMemPot[j] >= threshold) {
                    net.outSpikes[j] = 1;
                } else {
                    net.outSpikes[j] = 0;
                }
            }

            //reset
            for (int j = 0; j < d; j++) {
                if (net.outSpikes[j] == 1) {
                    net.outMemPot[j] = 0.0f;
                }
            }

            //ban updates until
            for (int j = 0; j < d; j++) {
                if (net.outSpikes[j] == 1) {
                    net.outRefracEnd[j] = currTime + tRef;
                }
            }


            //store results for analysis later
            for (int j = 0; j < d; j++) {
                if (!reset) {
                    net.outSumSpikes[j] = net.outSumSpikes[j] * (float) Math.exp(-(currTime - prevTime) / decayConstOutput) + net.outSpikes[j];
                } else {
                    net.outSumSpikes[j] = net.outSumSpikes[j] + net.outSpikes[j];
                }
            }
        }
    }

    public int coordinateConversionMatrixToList(int x, int y, int dimx){
        return dimx*(y-1)+x;
    }

    public Pair coordinateConversionListToMatrix(int pos, int dimx){
        if (pos%dimx!=0) {
            return new Pair(pos % dimx, pos / dimx + 1);
        }else
            return new Pair(dimx,pos/dimx);
    }

    //convert list of input spikes to input matrix
    public float[][] InputListToSpike(List<List<Integer>> input) {
        float[][] output = new float[net.layers.get(0).dimX][net.layers.get(0).dimY];
        for (int i = 0; i < output.length; i++) {
            for (int j = 0; j < output[0].length; j++) {
                output[i][j] = 0.0f;
            }
        }
        for (int i = 0; i < input.size(); i++) {
            int x = input.get(i).get(0);
            int y = input.get(i).get(1);
            output[y][x] = 1.0f;
        }
        return output;
    }

    public void propagateBatchSpikingCnn(List<List<Integer>> input, float tRef, float threshold, List<Float> ts) {

        //InputLayer
        float[][] InputSpikes = InputListToSpike(input);

        net.layers.get(0).spikes.clear();
        net.layers.get(0).spikes.add(0, InputSpikes);


        for (int i = 0; i < net.layers.size(); i++) {
            //ConvLayer
            if ("c".equals(net.layers.get(i).type)) {
                //size of matrix after convolution
                int dimX = net.layers.get(i - 1).dimX - net.layers.get(i).kernelSize + 1;
                int dimY = net.layers.get(i - 1).dimY - net.layers.get(i).kernelSize + 1;


                //output a map for each convolution
                for (int j = 0; j < net.layers.get(i).outMaps; j++) {

                    //sum up input maps
                    float[][] z = new float[dimX][dimY];
                    for (int k = 0; k < dimX; k++) {
                        for (int l = 0; l < dimY; l++) {
                            z[k][l] = 0.0f;
                        }
                    }

                    for (int k = 0; k < net.layers.get(i).inMaps; k++) {
                        //for each input map convolve with corresponding kernel and add to temp output map
                        float[][] sp_in = net.layers.get(i - 1).spikes.get(k);
                        float[][] z_conv = convolution2D(sp_in, sp_in.length, sp_in[0].length, net.layers.get(i).kernel.get(k * net.layers.get(i).outMaps + j), net.layers.get(i).kernelSize, net.layers.get(i).kernelSize);
                        for (int l = 0; l < dimX; l++) {
                            for (int m = 0; m < dimY; m++) {
                                z[l][m] = z[l][m] + z_conv[l][m];
                            }
                        }
                    }

                    //add bias
                    for (int k = 0; k < z.length; k++) {
                        for (int l = 0; l < z[0].length; l++) {
                            z[k][l] = z[k][l] + net.layers.get(i).bias[j];
                        }
                    }


                    //add input
                    float[][] newMem = new float[dimX][dimY];
                    for (int k = 0; k < dimX; k++) {
                        for (int l = 0; l < dimY; l++) {
                            newMem[k][l] = net.layers.get(i).memPot.get(j)[k][l] + z[k][l];
                            if (newMem[k][l] < negLimit)
                                newMem[k][l] = negLimit;
                        }
                    }

                    //check for spiking
                    float[][] newSpikes = new float[dimX][dimY];
                    for (int k = 0; k < dimX; k++) {
                        for (int l = 0; l < dimY; l++) {
                            if (newMem[k][l] >= threshold) {
                                newSpikes[k][l] = 1.0f;
                            } else {
                                newSpikes[k][l] = 0.0f;
                            }
                        }
                    }

                    //reset to resting potential
                    for (int k = 0; k < dimX; k++) {
                        for (int l = 0; l < dimY; l++) {
                            if (newSpikes[k][l] == 1.0f) {
                                newMem[k][l] = 0.0f;
                            }
                        }
                    }


                    net.layers.get(i).memPot.set(j, newMem);
                    net.layers.get(i).spikes.set(j, newSpikes);
                }
            } else if ("s".equals(net.layers.get(i).type)) {
                //subsample by averaging
                int dimX = net.layers.get(i).dimX;
                int dimY = net.layers.get(i).dimY;

                float[][] subsample = new float[net.layers.get(i).scale][net.layers.get(i).scale];
                for (int j = 0; j < subsample.length; j++) {
                    for (int k = 0; k < subsample[0].length; k++) {
                        subsample[j][k] = (float) 1 / (net.layers.get(i).scale * net.layers.get(i).scale);
                    }
                }

                for (int j = 0; j < net.layers.get(i).inMaps; j++) {
                    float[][] inputSp = net.layers.get(i - 1).spikes.get(j);
                    float[][] zConv = convolution2D(inputSp, inputSp.length, inputSp[0].length, subsample, subsample.length, subsample[0].length);
                    //downsampe
                    float[][] z = new float[inputSp.length / subsample.length][inputSp[0].length / subsample[0].length];
                    for (int k = 0; k < z.length; k++) {
                        for (int l = 0; l < z[0].length; l++) {
                            z[k][l] = zConv[k * 2][l * 2];
                        }
                    }


                    //add input
                    float[][] newMem = new float[dimX][dimY];
                    for (int k = 0; k < dimX; k++) {
                        for (int l = 0; l < dimY; l++) {
                            newMem[k][l] = net.layers.get(i).memPot.get(j)[k][l] + z[k][l];
                        }
                    }

                    //check for spiking
                    float[][] newSpikes = new float[dimX][dimY];
                    for (int k = 0; k < dimX; k++) {
                        for (int l = 0; l < dimY; l++) {
                            if (newMem[k][l] >= threshold) {
                                newSpikes[k][l] = 1.0f;
                            } else {
                                newSpikes[k][l] = 0.0f;
                            }
                        }
                    }

                    //reset to resting potential
                    for (int k = 0; k < dimX; k++) {
                        for (int l = 0; l < dimY; l++) {
                            if (newSpikes[k][l] == 1.0f) {
                                newMem[k][l] = 0.0f;
                            }
                        }
                    }

                    net.layers.get(i).spikes.set(j, newSpikes);
                    net.layers.get(i).memPot.set(j, newMem);
                }
            }
        }

        //concatenate all end layer feature maps into vector
        //fv
        SpikingCnnStructure.Layer currentLayer = net.layers.get(net.layers.size() - 1);
        net.fVec = new float[currentLayer.dimX * currentLayer.dimY * currentLayer.outMaps];
        int idx = 0;
        for (int i = 0; i < currentLayer.outMaps; i++) {
            for (int j = 0; j < currentLayer.dimY; j++) {
                for (int k = 0; k < currentLayer.dimX; k++) {
                    net.fVec[idx] = currentLayer.spikes.get(i)[k][j];
                    idx++;
                }
            }
        }

        //run the output layer neurons
        //add inputs multiplied by weights
        //ffw*fv
        int d = net.fcWeights.length;
        int e = net.fVec.length;

        float[] impulse = new float[d];
        for (int i = 0; i < d; i++) {
            impulse[i] = 0.0f;
        }

        for (int i = 0; i < d; i++) {
            for (int j = 0; j < e; j++) {
                impulse[i] = impulse[i] + net.fcWeights[i][j] * net.fVec[j];
            }
        }

        //add bias
        for (int i = 0; i < impulse.length; i++) {
            impulse[i] = impulse[i] + net.fcBias[i];
        }


        //add input to membrane potential
        for (int i = 0; i < d; i++) {
            net.outMemPot[i] = net.outMemPot[i] + impulse[i];
            if (net.outMemPot[i] < negLimit) {
                net.outMemPot[i] = negLimit;
            }
        }

        //check for spiking
        net.outSpikes = new int[d];
        for (int i = 0; i < d; i++) {
            if (net.outMemPot[i] >= threshold) {
                net.outSpikes[i] = 1;
            } else {
                net.outSpikes[i] = 0;
            }
        }

        //reset
        for (int i = 0; i < d; i++) {
            if (net.outSpikes[i] == 1) {
                net.outMemPot[i] = 0.0f;
            }
        }


        //store results for analysis later
        for (int i = 0; i < d; i++) {
            if (!reset) {
                net.outSumSpikes[i] = net.outSumSpikes[i] * (float) Math.exp(-(currTimeBatch-prevTimeBatch)/ decayConstOutput) + net.outSpikes[i];
            } else {
                net.outSumSpikes[i] = net.outSumSpikes[i] + net.outSpikes[i];
            }
        }
    }

    public static float[][] convolution2D(float[][] input, int width, int height, float[][] kernel, int kernelWidth, int kernelHeight){

        int smallWidth = width - kernelWidth + 1;
        int smallHeight = height - kernelHeight + 1;
        float[][] output = new float[smallWidth][smallHeight];
        for(int i=0;i<smallWidth;++i){
            for(int j=0;j<smallHeight;++j){
                output[i][j]=0;
            }
        }
        for(int i=0;i<smallWidth;++i){
            for(int j=0;j<smallHeight;++j){
                output[i][j] = singlePixelConvolution(input,i,j,kernel,
                        kernelWidth,kernelHeight);
            }
        }
        return output;
    }

    public static float singlePixelConvolution(float [][] input, int x, int y, float [][] k, int kernelWidth, int kernelHeight){
        float output = 0;
        for(int i=0;i<kernelWidth;++i){
            for(int j=0;j<kernelHeight;++j){
                output = output + (input[x+i][y+j] * k[i][j]);
            }
        }
        return output;
    }

    public boolean isMNIST(){return MNIST;}

    public void setMNIST(boolean mnist){
        this.MNIST = mnist;
        putBoolean("MNIST",mnist);
    }

    public boolean isRobotSteering(){return robotSteering;}

    public void setRobotSteering(boolean robotSteering){
        this.robotSteering = robotSteering;
        putBoolean("robotSteering",robotSteering);
    }

    public boolean islabelsAvailable() {
        return labelsAvailable;
    }

    public void setlabelsAvailable(boolean LabelsAvailable) {
        this.labelsAvailable = LabelsAvailable;
        putBoolean("labelsAvailable", LabelsAvailable);
    }

    public boolean isShowOutputAsBarChart() {
        return showOutputAsBarChart;
    }

    public void setShowOutputAsBarChart(boolean showOutputAsBarChart) {
        this.showOutputAsBarChart = showOutputAsBarChart;
        putBoolean("showOutputAsBarChart", showOutputAsBarChart);
    }

    public boolean isShowLatency(){return showLatency;}

    public void setShowLatency(boolean showLatency){
        this.showLatency = showLatency;
        putBoolean("showLatency",showLatency);
    }

    public boolean isShowAccuracy(){return showAccuracy;}

    public void setShowAccuracy(boolean showAccuracy){
        this.showAccuracy = showAccuracy;
        putBoolean("showAccuracy",showAccuracy);
    }

    public boolean isShowDigitsAccuracy(){return showDigitsAccuracy;}

    public void setShowDigitsAccuracy(boolean showDigitsAccuracy){
        this.showDigitsAccuracy = showDigitsAccuracy;
        putBoolean("showDigitsAccuracy",showDigitsAccuracy);
    }

    public boolean isReset(){return reset;}

    public void setReset(boolean reset){
        this.reset = reset;
        putBoolean("reset",reset);
    }

    public float gettRef(){
        return tRef;
    }

    public void settRef(float tref){
        tRef = tref;
        putFloat("tRef",tref);
    }

    public float getthreshold(){
        return threshold;
    }

    public void setthreshold(float thres){
        threshold = thres;
        putFloat("threshold",thres);
    }

    public float getnegLimit() {
        return negLimit;
    }

    public void setnegLimit(float negativeLimit){
        negLimit = negativeLimit;
        putFloat("negLimit",negativeLimit);
    }

    public float getdecayConstOutput(){
        return decayConstOutput;
    }

    public void setdecayConstOutput(float decay){
        decayConstOutput = decay;
        putFloat("decayConstOutput",decay);
    }

    public boolean isMedianTracker(){return medianTracker;}

    public void setMedianTracker(boolean medianTr){
        this.medianTracker=medianTr;
        putBoolean("medianTracker",medianTr);
    }

    public int getcenterX(){return centerX;}

    public void setcenterX(int x){
        centerX=x;
        putInt("centerX",x);
    }

    public int getcenterY(){return centerY;}

    public void setcenterY(int y){
        centerY=y;
        putInt("centerY",y);
    }

    public boolean getBatch(){return batch;}

    public void setBatch(boolean batch){
        this.batch=batch;
        putBoolean("batch",batch);
    }

    public float getbatchsize(){return batchsize;}

    public void setbatchsize(float batchsize){
        this.batchsize=batchsize;
        putFloat("batchsize",batchsize);
    }

    public boolean getSpike(){return spike;}

    public void setSpike(boolean spike){
        this.spike=spike;
        putBoolean("spike",spike);
    }


}
