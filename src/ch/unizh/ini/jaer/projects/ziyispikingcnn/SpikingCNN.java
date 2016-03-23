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
import java.awt.geom.Arc2D;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;

/**
 * Created by ziyihua on 20/01/16.
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SpikingCNN extends EventFilter2D implements FrameAnnotater {

    //general
    public ch.unizh.ini.jaer.projects.ziyispikingcnn.SpikingNetStructure.network net = new ch.unizh.ini.jaer.projects.ziyispikingcnn.SpikingNetStructure.network();
    private String lastXMLFilename = getString("lastXMLFilename", "cnn_test.xml");
    private String lastTXTFilename = getString("lastTXTFilename", "test_set_labels.txt");
    private boolean showOutputAsBarChart = getBoolean("showOutputAsBarChart", true);
    private boolean LabelsAvailable = getBoolean("LabelsAvailable",false);
    //private boolean LeakAllNeurons = getBoolean("LeakAllNeurons",false);
    //private boolean decayOutput = getBoolean("DecayOutput",false);
    private boolean showLatency = getBoolean("showLatency",true);
    private boolean showAccuracy = getBoolean("showAccuracy",true);
    private boolean showDigitsAccuracy = getBoolean("showDigitsAccuracy",true);
    private boolean robotSteering = getBoolean("robotSteering",true);
    private boolean MNIST = getBoolean("MNIST",true);
    //private boolean takeAllInput = getBoolean("takeAllInput",true);
    //private boolean subsampleInput = getBoolean("subsampleInput",true);
    private boolean reset = getBoolean("reset",true);
    private float tref = getFloat("tref", 0.0f);
    private float threshold = getFloat("threshold", 0.5f);
    private float negative_threshold = getFloat("negativeThreshold", Float.NEGATIVE_INFINITY);
    private float decay_output = getFloat("decayConstOutput",0.0f);
    //private float decay_neuron = getFloat("decayConstNeuron",0.0f);
    //private float timeconst_output = getFloat("timeConstOutput",0.0f);
    //private float timeconst_neuron = getFloat("timeConstNeuron",0.0f);



    //MNIST
    int xmedian = 0;
    int ymedian = 0;
    int count_total = 0;
    int correct_count_total = 0;
    int label = 0;
    int predict_final = 0;
    float t_previous  = 0f;
    float[][] digit_accuracy = new float[10][2];
    List<Float> ending_times = new ArrayList<>();
    List<Float> starting_times = new ArrayList<>();
    List<Integer> labels = new ArrayList<>();
    float digit_start = 0.0f;
    float latency = 0.0f;
    float latency_sum = 0.0f;
    float latency_average = 0.0f;
    boolean changedigit = false;
    boolean winnergot = false;
    int num_label = 0;

    //RobotSteering
    private TargetLabeler targetLabeler = null;
    private Error error = new Error();
    private static final int LEFT = 0, CENTER = 1, RIGHT = 2, INVISIBLE = 3; // define output cell types

    final String param = "Parameter", disp = "Display", opt = "Option";

    public SpikingCNN(AEChip chip) throws IOException {
        super(chip);
        setPropertyTooltip("LoadCNNFromXML","Load an XML file containing a CNN exported from DeepLearnToolbox by cnntoxml.m");
        setPropertyTooltip("LoadLabel","Load a txt file containing timestamps and label of digits");
        setPropertyTooltip(disp, "showOutputAsBarChart", "displays activity of output units as bar chart, where height indicates activation");
        setPropertyTooltip(opt, "LabelsAvailable", "a file with label and corresponding ts is present and loaded");
        setPropertyTooltip(opt, "showLatency","latency of getting the correct prediction");
        setPropertyTooltip(opt, "showAccuracy","accuracy");
        setPropertyTooltip(opt, "showDigitsAccuracy","accuracy of each digits in case of MNIST");
        //setPropertyTooltip(opt, "DecayOutput","apply decay functions to output");
        //setPropertyTooltip(opt, "LeakAllNeurons","apply decay functions to all neurons");
        setPropertyTooltip(opt, "robotSteering","the cnn is for robot steering");
        setPropertyTooltip(opt, "MNIST","the cnn is for MNIST");
        //setPropertyTooltip(opt, "takeAllInput","the input is about the size of input layer in the network loaded");
        //setPropertyTooltip(opt, "subsampleInput","the input is much larger than the size of input layer in the network loaded");
        setPropertyTooltip(opt, "reset","reset network");
        setPropertyTooltip(param, "negativeThreshold","bound on negative potential");
        setPropertyTooltip(param, "tref","refractory period");
        setPropertyTooltip(param, "threshold","threshold for spiking");
        //setPropertyTooltip(param, "decayConstNeuron","decay constant for leaky neurons");
        setPropertyTooltip(param, "decayConstOutput","decay constant for the output layer");
        //setPropertyTooltip(param, "timeConstNeuron","time constant for leaky neurons");
        //setPropertyTooltip(param, "timeConstOutput","time constant for the output layer");
        if (robotSteering){
            FilterChain chain = new FilterChain(chip);
            targetLabeler = new TargetLabeler(chip); // used to validate whether descisions are correct or not
            chain.add(targetLabeler);
            setEnclosedFilterChain(chain);
        }
        initFilter();
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
        loadFromXMLFile(c.getSelectedFile());
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
                ending_times.add((float)(Float.parseFloat(line.split(" ")[2])/1e6));
                starting_times.add((float)(Float.parseFloat(line.split(" ")[1])/1e6));
                labels.add((int)(Float.parseFloat(line.split(" ")[0])));
            }
        }
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 2; j++) {
                digit_accuracy[i][i]=0f;
            }
        }
    }

    /*public boolean isDecayOutput(){return decayOutput;}

    public void setDecayOutput(boolean decayoutput){
        this.decayOutput = decayoutput;
        putBoolean("DecayOutput",decayoutput);
    }*/

    public boolean isReset(){return reset;}

    public void setReset(boolean reset){
        this.reset = reset;
        putBoolean("reset",reset);
    }

    /*public boolean isTakeAllInput(){return takeAllInput;}

    public void setTakeAllInput(boolean takeAllInput){
        this.takeAllInput=takeAllInput;
        putBoolean("takeAllInput",takeAllInput);
    }

    public boolean isSubsampleInput(){return subsampleInput;}

    public void setSubsampleInput(boolean subsampleInput){
        this.subsampleInput = subsampleInput;
        putBoolean("subsampleInput",subsampleInput);
    }*/

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

    public boolean isLabelsAvailable() {
        return LabelsAvailable;
    }

    public void setLabelsAvailable(boolean LabelsAvailable) {
        this.LabelsAvailable = LabelsAvailable;
        putBoolean("LabelsAvailable", LabelsAvailable);
    }

    /*public boolean isLeakAllNeurons(){
        return LeakAllNeurons;
    }

    public void setLeakAllNeurons(boolean leakallneurons){
        this.LeakAllNeurons = leakallneurons;
        putBoolean("LeakAllNeurons",leakallneurons);
    }*/

    public boolean isRobotSteering(){return robotSteering;}

    public void setRobotSteering(boolean robotSteering){
        this.robotSteering = robotSteering;
        putBoolean("robotSteering",robotSteering);
    }

    public boolean isMNIST(){return MNIST;}

    public void setMNIST(boolean mnist){
        this.MNIST = mnist;
        putBoolean("MNIST",mnist);
    }

    public float gettref(){
        return tref;
    }

    public void settref(float t_ref){
        tref = t_ref;
        putFloat("tref",t_ref);
    }

    public float getnegativeThreshold() {
        return negative_threshold;
    }

    public void setnegativeThreshold(float negativethres){
        negative_threshold = negativethres;
        putFloat("negativeThreshold",negativethres);
    }

    public float getthreshold(){
        return threshold;
    }

    public void setthreshold(float thres){
        threshold = thres;
        putFloat("threshold",thres);
    }

    /*public float getdecayConstNeuron(){
        return decay_neuron;
    }

    public void setdecayConstNeuron(float decayN){
        decay_neuron = decayN;
        putFloat("decayConstNeuron",decayN);
    }*/

    public float getdecayConstOutput(){
        return decay_output;
    }

    public void setdecayConstOutput(float decayO){
        decay_output = decayO;
        putFloat("decayConstOutput",decayO);
    }

    /*public float gettimeConstNeuron(){
        return timeconst_neuron;
    }

    public void settimeConstNeuron(float timeCN){
        timeconst_neuron = timeCN;
        putFloat("timeConstNeuron",timeCN);
    }

    public float gettimeConstOutput(){
        return timeconst_output;
    }

    public void settimeConstOutput(float timeCO){
        timeconst_output = timeCO;
        putFloat("timeConstOutput",timeCO);
    }*/

    @Override
    public void resetFilter() {
        error.reset();
    }

    @Override
    public void initFilter() {
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
            if (changedigit){
                Initialization();
                if (!showLatency){
                    changedigit = false;
                }
            }
        }

        //calculate latency and reset if the digit is changed
        if(showLatency) {
            if (changedigit) {
                if (!times.isEmpty()) {
                    digit_start = times.get(0);
                    changedigit = false;
                    winnergot = false;
                }
            }
        }


        //update network only when spike presents
        if (!x_clone.isEmpty()) {

            //use median tracker to process input
            if(MNIST) {
                xmedian = getMedian(x_clone);
                ymedian = getMedian(y_clone);
            }

            //process spikes such that they're consistent with the size of input layer
            List<List<Integer>> coordinates_index_new = DVStoPixelInputSpace(x, y, xmedian, ymedian);
            List<Float> times_new = new ArrayList<>();
            if (MNIST){
                for (int i = 0; i < coordinates_index_new.size(); i++) {
                    times_new.add(times.get(coordinates_index_new.get(i).get(2)));
                }
            }else if (robotSteering){
                times_new = times;
            }

            Convlifsim(coordinates_index_new, tref, threshold, times_new);
            t_previous = times.get(times.size()-1);
            double max = 0;
            int predict = 0;
            for (int i = 0; i < net.o_sum_spikes.length; i++) {
                if (net.o_sum_spikes[i] >= max) {
                    max = net.o_sum_spikes[i];
                    predict = i;
                }
            }
            if (showLatency) {
                if (winnergot == false) {
                    if (predict == labels.get(0)) {
                        latency = t_previous - digit_start;
                        winnergot = true;
                        latency_sum += latency;
                    }
                }
            }


            if (LabelsAvailable) {
                if (times.get(times.size() - 1) > ending_times.get(0)) {
                    predict_final = predict;
                    label = labels.get(0);
                    count_total++;
                    digit_accuracy[label][1]+=1;
                    if (label == predict) {
                        correct_count_total++;
                        digit_accuracy[label][0] += 1;
                    }
                    labels.remove(0);
                    ending_times.remove(0);
                    starting_times.remove(0);
                    num_label++;
                    if (showLatency) {
                        if (winnergot == false) {
                            latency = t_previous - digit_start;
                            latency_sum += latency;
                        }
                        latency_average = latency_sum/num_label;
                    }
                    changedigit=true;
                }
            }
        }
        return in;
    }

    @Override
    public void annotate(GLAutoDrawable drawable){

        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        gl.glColor3f(0, 0, 1);
        gl.glLineWidth(4);

        if (LabelsAvailable) {
            if (LabelsAvailable) {
                GLUT glut1 = new GLUT();
                gl.glRasterPos2f(0, 50);
                glut1.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("label = %s", Integer.toString(label)));
                GLUT glut2 = new GLUT();
                gl.glRasterPos2f(0, 45);
                glut2.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("prediction = %s", Integer.toString(predict_final)));
                if (showAccuracy) {
                    GLUT glut3 = new GLUT();
                    gl.glRasterPos2f(0, 40);
                    glut3.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("accuracy = %s", String.format("%.2f", ((float) correct_count_total * 100f) / ((float) count_total))));
                }
                if (showDigitsAccuracy) {
                    GLUT glut4 = new GLUT();
                    gl.glRasterPos2f(0, 35);
                    glut4.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("0:%s " + " 1:%s " + " 2:%s " + " 3:%s " + " 4:%s " + " 5:%s " + " 6:%s " + " 7:%s " + " 8:%s " + " 9:%s ", String.format("%.1f", (100f * digit_accuracy[0][0] / digit_accuracy[0][1])), String.format("%.1f", (100f * digit_accuracy[1][0] / digit_accuracy[1][1])), String.format("%.1f", (100f * digit_accuracy[2][0] / digit_accuracy[2][1])), String.format("%.1f", (100f * digit_accuracy[3][0] / digit_accuracy[3][1])), String.format("%.1f", (100f * digit_accuracy[4][0] / digit_accuracy[4][1])), String.format("%.1f", (100f * digit_accuracy[5][0] / digit_accuracy[5][1])), String.format("%.1f", (100f * digit_accuracy[6][0] / digit_accuracy[6][1])), String.format("%.1f", (100f * digit_accuracy[7][0] / digit_accuracy[7][1])), String.format("%.1f", (100f * digit_accuracy[8][0] / digit_accuracy[8][1])), String.format("%.1f", (100f * digit_accuracy[9][0] / digit_accuracy[9][1]))));
                }
                if (showLatency) {
                    GLUT glut5 = new GLUT();
                    gl.glRasterPos2f(0, 30);
                    glut5.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, String.format("current_latency = %s " + " average_latency = %s", String.format("%.3f", latency), String.format("%.3f", latency_average)));
                }
            }
        }

        if (MNIST) {
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glVertex2d(xmedian - 13, ymedian + 14);
            gl.glVertex2d(xmedian + 14, ymedian + 14);
            gl.glVertex2d(xmedian + 14, ymedian - 13);
            gl.glVertex2d(xmedian - 13, ymedian - 13);
            gl.glEnd();
        }


        if(showOutputAsBarChart) {
            final float lineWidth = 2;
            annotateHistogram(gl, chip.getSizeX(), chip.getSizeY(), lineWidth, Color.RED);
        }

        gl.glPopMatrix();
    }

    public void outputHistogram(GL2 gl, int width, int height){

        float dx = (float) width/net.o_sum_spikes.length;
        float sy = (float) height/8;

        gl.glBegin(GL.GL_LINE_STRIP);
        for (int i = 0; i < net.o_sum_spikes.length; i++) {
            float y = 1 + (sy * (float)net.o_sum_spikes[i]);
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

    //reset network if reset==true
    public void Initialization(){
        for (int i = 0; i < net.layers.size(); i++) {
            ch.unizh.ini.jaer.projects.ziyispikingcnn.SpikingNetStructure.LAYER current_layer = net.layers.get(i);
            float[][] correctly_sized_zeros = new float[current_layer.dimx][current_layer.dimy];
            for (int j = 0; j < current_layer.dimx; j++) {
                for (int k = 0; k < current_layer.dimy; k++) {
                    correctly_sized_zeros[j][k]=0.0f;
                }
            }
            for (int j = 0; j < current_layer.outmaps; j++) {
                current_layer.m.set(j,correctly_sized_zeros);
                current_layer.r.set(j,correctly_sized_zeros);
                current_layer.s.set(j,correctly_sized_zeros);
                current_layer.sp.set(j,correctly_sized_zeros);
            }
        }
        net.sum_fv = new float[net.ffw[0].length];
        for (int i = 0; i < net.sum_fv.length; i++) {
            net.sum_fv[i]=0.0f;
        }
        int outputclass = net.o_mem.length;
        net.o_mem = new float[outputclass];
        net.o_refrac_end = new float[outputclass];
        net.o_sum_spikes = new double[outputclass];
        for (int i = 0; i < outputclass; i++) {
            net.o_mem[i]=0.0f;
            net.o_refrac_end[i]=0.0f;
            net.o_sum_spikes[i]=0;
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
            int dimx = net.layers.get(0).dimx;
            int dimy = net.layers.get(0).dimy;
            for (int i = 0; i < x.size(); i++) {
                int x_new = (int) Math.floor(x.get(i) * (dimx-1) * 1.0 / (chip.getSizeX()-1) + (chip.getSizeX()-dimx)*1.0 / (chip.getSizeX()-1));
                int y_new = (int) Math.floor(y.get(i) * (dimy-1) * 1.0 / (chip.getSizeY()-1) + (chip.getSizeY()-dimy)*1.0 / (chip.getSizeY()-1));
                List<Integer> triplets = Arrays.asList(x_new, y_new, i);
                InputSpace.add(triplets);
            }
        }
        return InputSpace;
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
               ch.unizh.ini.jaer.projects.ziyispikingcnn.SpikingNetStructure.LAYER layer = new ch.unizh.ini.jaer.projects.ziyispikingcnn.SpikingNetStructure.LAYER();
                net.layers.add(i,layer);
            }

            for (int i = 0; i < nLayers; i++) {
                EasyXMLReader layerReader = networkReader.getNode("Layer", i);
                int index = layerReader.getInt("index");
                String type = layerReader.getRaw("type");
                switch (type) {
                    case "i": {
                        net.layers.get(i).type = "i";
                        net.layers.get(i).dimx = layerReader.getInt("dimx");
                        net.layers.get(i).dimy = layerReader.getInt("dimy");
                        int correct_size = net.layers.get(i).dimx;
                        float[][] correctly_sized_zeros = new float[correct_size][correct_size];
                        for (int j = 0; j < correct_size; j++) {
                            for (int k = 0; k < correct_size; k++) {
                                correctly_sized_zeros[j][k]=0.0f;
                            }
                        }

                        net.layers.get(i).m.add(correctly_sized_zeros);
                        net.layers.get(i).r.add(correctly_sized_zeros);
                        net.layers.get(i).s.add(correctly_sized_zeros);
                        net.layers.get(i).sp.add(correctly_sized_zeros);

                    }
                    break;
                    case "c": {
                        net.layers.get(i).type = "c";
                        net.layers.get(i).inmaps= layerReader.getInt("inputMaps");
                        net.layers.get(i).outmaps = layerReader.getInt("outputMaps");
                        net.layers.get(i).kernelsize = layerReader.getInt("kernelSize");
                        int kernelsize = layerReader.getInt("kernelSize");
                        net.layers.get(i).b = layerReader.getBase64FloatArr("biases");
                        float[] kernels = layerReader.getBase64FloatArr("kernels");
                        for (int j = 0; j < net.layers.get(i).inmaps; j++) {
                            for (int k = 0; k < net.layers.get(i).outmaps; k++) {
                                int a = (j*net.layers.get(i).outmaps+k)*kernelsize*kernelsize;
                                int b = a+kernelsize*kernelsize;
                                float[][] mat = ArrToMat(Arrays.copyOfRange(kernels,a,b),kernelsize);
                                net.layers.get(i).k.add(mat);
                            }
                        }
                        net.layers.get(i).dimx = net.layers.get(i-1).dimx-kernelsize+1;
                        net.layers.get(i).dimy = net.layers.get(i-1).dimy-kernelsize+1;
                        int correct_size = net.layers.get(i).dimx;
                        float[][] correctly_sized_zeros = new float[correct_size][correct_size];
                        for (int j = 0; j < correct_size; j++) {
                            for (int k = 0; k < correct_size; k++) {
                                correctly_sized_zeros[j][k]=0.0f;
                            }
                        }
                        for (int j = 0; j < net.layers.get(i).outmaps; j++) {
                            net.layers.get(i).m.add(correctly_sized_zeros);
                            net.layers.get(i).r.add(correctly_sized_zeros);
                            net.layers.get(i).s.add(correctly_sized_zeros);
                            net.layers.get(i).sp.add(correctly_sized_zeros);
                        }
                    }
                    break;
                    case "s": {
                        net.layers.get(i).inmaps=net.layers.get(i-1).outmaps;
                        net.layers.get(i).outmaps=net.layers.get(i).inmaps;
                        net.layers.get(i).type= "s";
                        net.layers.get(i).scale = layerReader.getInt("averageOver");
                        net.layers.get(i).b = layerReader.getBase64FloatArr("biases");
                        net.layers.get(i).dimx = net.layers.get(i-1).dimx/net.layers.get(i).scale;
                        net.layers.get(i).dimy = net.layers.get(i-1).dimy/net.layers.get(i).scale;
                        int correct_size = net.layers.get(i).dimx;
                        float[][] correctly_sized_zeros = new float[correct_size][correct_size];
                        for (int j = 0; j < correct_size; j++) {
                            for (int k = 0; k < correct_size; k++) {
                                correctly_sized_zeros[j][k]=0.0f;
                            }
                        }
                        for (int j = 0; j < net.layers.get(i).outmaps; j++) {
                            net.layers.get(i).m.add(correctly_sized_zeros);
                            net.layers.get(i).r.add(correctly_sized_zeros);
                            net.layers.get(i).s.add(correctly_sized_zeros);
                            net.layers.get(i).sp.add(correctly_sized_zeros);
                        }
                    }
                    break;
                }
            }
            net.ffb=networkReader.getBase64FloatArr("outputBias");
            int outputclass = net.ffb.length;
            net.ffw=ArrToMatffW(networkReader.getBase64FloatArr("outputWeights"),net.layers.get(nLayers-1).dimx,net.layers.get(nLayers-1).outmaps,outputclass);
            net.sum_fv = new float[net.ffw[0].length];
            for (int i = 0; i < net.sum_fv.length; i++) {
                net.sum_fv[i]=0.0f;
            }
            net.o_mem = new float[outputclass];
            net.o_refrac_end = new float[outputclass];
            net.o_sum_spikes = new double[outputclass];
            for (int i = 0; i < outputclass; i++) {
                net.o_mem[i]=0.0f;
                net.o_refrac_end[i]=0.0f;
                net.o_sum_spikes[i]=0;
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
        sb.append(String.format("index=0 Input dimx=%s dimy=%s",net.layers.get(0).dimx,net.layers.get(0).dimy)+"\n");
        for (int i = 1; i < networkReader.getNodeCount("Layer"); i++) {
            ch.unizh.ini.jaer.projects.ziyispikingcnn.SpikingNetStructure.LAYER layer = net.layers.get(i);
            if (layer.type=="c"){
                sb.append(String.format("index=%s Convolution nInputMaps=%s nOutputMaps=%s kernelsize=%s",i,layer.inmaps,layer.outmaps,layer.kernelsize)+"\n");
            }else{
                sb.append(String.format("index=%s Subsample nInputMaps=%s nOutputMaps=%s scale=%s",i,layer.inmaps,layer.outmaps,layer.scale)+"\n");
            }
        }
        sb.append(String.format("Output bias=float[%s] weight=float[%s][%s]",net.ffb.length,net.ffw.length,net.ffw[0].length));
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

    //convert list of input spikes to input matrix
    public float[][] InputListToSpike(List<List<Integer>> input) {
        float[][] output = new float[net.layers.get(0).dimx][net.layers.get(0).dimy];
        for (int i = 0; i < output.length; i++) {
            for (int j = 0; j < output[0].length; j++) {
                output[i][j] = 0.0f;
            }
        }
        for (int i = 0; i < input.size(); i++) {
            int x = input.get(i).get(0);
            int y = input.get(i).get(1);
            //output[y][x] = output[y][x] + 1.0f;
            output[y][x] = 1.0f;
        }
        return output;
    }

    //transform (transpose+mirroring) input matrix such that it's consistent with the MNIST digits used to train the network
    public float[][] transform(float[][] input){
        float[][] input_new_1 = new float[net.layers.get(0).dimx][net.layers.get(0).dimy];
        for (int i = 0; i < input_new_1.length; i++) {
            for (int j = 0; j < input_new_1[0].length; j++) {
                input_new_1[i][input_new_1.length-1-j] = input[j][i];
            }
        }
        return input_new_1;
    }


    //feed spikes to the network
    public void Convlifsim(List<List<Integer>> input, float t_ref, float threshold, List<Float> ts) {
        //current time
        float currenttime = ts.get(ts.size() - 1);

        //InputLayer
        float[][] InputSpikes = InputListToSpike(input);
        /*if (takeAllInput){
            InputSpikes = transform(InputSpikes);
        }*/
        net.layers.get(0).sp.set(0, InputSpikes);
        float[][] mem = new float[InputSpikes.length][InputSpikes[0].length];
        for (int i = 0; i < mem.length; i++) {
            for (int j = 0; j < mem[0].length; j++) {
                mem[i][j] = net.layers.get(0).m.get(0)[i][j] + InputSpikes[i][j];
            }
        }
        net.layers.get(0).m.set(0, mem);
        float[][] sum = new float[InputSpikes.length][InputSpikes[0].length];
        for (int i = 0; i < sum.length; i++) {
            for (int j = 0; j < sum[0].length; j++) {
                sum[i][j] = net.layers.get(0).s.get(0)[i][j] + InputSpikes[i][j];
            }
        }
        net.layers.get(0).s.set(0, sum);

        for (int i = 0; i < net.layers.size(); i++) {
            //ConvLayer
            if ("c".equals(net.layers.get(i).type)) {
                //size of matrix after convolution
                int dimx = net.layers.get(i - 1).dimx - net.layers.get(i).kernelsize + 1;
                int dimy = net.layers.get(i - 1).dimy - net.layers.get(i).kernelsize + 1;


                //output a map for each convolution
                for (int j = 0; j < net.layers.get(i).outmaps; j++) {

                    //sum up input maps
                    float[][] z = new float[dimx][dimy];
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            z[k][l] = 0.0f;
                        }
                    }

                    for (int k = 0; k < net.layers.get(i).inmaps; k++) {
                        //for each input map convolve with corresponding kernel and add to temp output map
                        float[][] sp_in = net.layers.get(i - 1).sp.get(k);
                        float[][] z_conv = convolution2D(sp_in, sp_in.length, sp_in[0].length, net.layers.get(i).k.get(k * net.layers.get(i).outmaps + j), net.layers.get(i).kernelsize, net.layers.get(i).kernelsize);
                        for (int l = 0; l < dimx; l++) {
                            for (int m = 0; m < dimy; m++) {
                                z[l][m] = z[l][m] + z_conv[l][m];
                            }
                        }
                    }

                    //add bias
                    for (int k = 0; k < z.length; k++) {
                        for (int l = 0; l < z[0].length; l++) {
                            z[k][l] = z[k][l] + net.layers.get(i).b[j];
                        }
                    }

                    //only allow non-refractory neurons to get input
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            if (net.layers.get(i).r.get(j)[k][l] > currenttime) {
                                z[k][l] = 0.0f;
                            }
                        }
                    }

                    //add input
                    float[][] mem_new = new float[dimx][dimy];
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            mem_new[k][l]=net.layers.get(i).m.get(j)[k][l]+z[k][l];
                            if (mem_new[k][l] < negative_threshold)
                                mem_new[k][l] = negative_threshold;
                        }
                    }

                    //check for spiking
                    float[][] spikes_new = new float[dimx][dimy];
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            if (mem_new[k][l] >= threshold) {
                                spikes_new[k][l] = 1.0f;
                            } else {
                                spikes_new[k][l] = 0.0f;
                            }
                        }
                    }

                    //reset
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            if (spikes_new[k][l] == 1.0f) {
                                mem_new[k][l] = 0.0f;
                            }
                        }
                    }

                    //ban updates until
                    float[][] refrac_new = new float[dimx][dimy];
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            if (spikes_new[k][l] == 1) {
                                refrac_new[k][l] = currenttime + t_ref;
                            } else {
                                refrac_new[k][l] = net.layers.get(i).r.get(j)[k][l];
                            }
                        }
                    }

                    //store results for analysis later
                    float[][] sum_new = new float[dimx][dimy];
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            sum_new[k][l] = net.layers.get(i).s.get(j)[k][l] + spikes_new[k][l];
                        }
                    }

                    net.layers.get(i).m.set(j, mem_new);
                    net.layers.get(i).s.set(j, sum_new);
                    net.layers.get(i).sp.set(j, spikes_new);
                    net.layers.get(i).r.set(j, refrac_new);
                }
            } else if ("s".equals(net.layers.get(i).type)) {
                //subsample by averaging
                int width = net.layers.get(i - 1).dimx - net.layers.get(i).scale + 1;
                int height = net.layers.get(i - 1).dimy - net.layers.get(i).scale + 1;
                int dimx = net.layers.get(i).dimx;
                int dimy = net.layers.get(i).dimy;

                float[][] subsample = new float[net.layers.get(i).scale][net.layers.get(i).scale];
                for (int j = 0; j < subsample.length; j++) {
                    for (int k = 0; k < subsample[0].length; k++) {
                        subsample[j][k] = (float) 1 / (net.layers.get(i).scale * net.layers.get(i).scale);
                    }
                }

                for (int j = 0; j < net.layers.get(i).inmaps; j++) {
                    float[][] sp_in = net.layers.get(i - 1).sp.get(j);
                    float[][] z_conv = convolution2D(sp_in, sp_in.length, sp_in[0].length, subsample, subsample.length, subsample[0].length);
                    //downsampe
                    float[][] z = new float[sp_in.length / subsample.length][sp_in[0].length / subsample[0].length];
                    for (int k = 0; k < z.length; k++) {
                        for (int l = 0; l < z[0].length; l++) {
                            z[k][l] = z_conv[k * 2][l * 2];
                        }
                    }

                    //only allow non-refractory neurons to get input
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            if (net.layers.get(i).r.get(j)[k][l] > currenttime) {
                                z[k][l] = 0.0f;
                            }
                        }
                    }

                    //add input
                    float[][] mem_new = new float[dimx][dimy];
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            mem_new[k][l]=net.layers.get(i).m.get(j)[k][l]+z[k][l];
                        }
                    }

                    //check for spiking
                    float[][] spikes_new = new float[dimx][dimy];
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            if (mem_new[k][l] >= threshold) {
                                spikes_new[k][l] = 1.0f;
                            } else {
                                spikes_new[k][l] = 0.0f;
                            }
                        }
                    }

                    //reset
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            if (spikes_new[k][l] == 1.0f) {
                                mem_new[k][l] = 0.0f;
                            }
                        }
                    }

                    //ban updates until
                    float[][] refrac_new = new float[dimx][dimy];
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            if (spikes_new[k][l] == 1.0f) {
                                refrac_new[k][l] = currenttime + t_ref;
                            } else {
                                refrac_new[k][l] = net.layers.get(i).r.get(j)[k][l];
                            }
                        }
                    }

                    //store results for analysis later
                    float[][] sum_new = new float[dimx][dimy];
                    for (int k = 0; k < dimx; k++) {
                        for (int l = 0; l < dimy; l++) {
                            sum_new[k][l] = net.layers.get(i).s.get(j)[k][l] + spikes_new[k][l];
                        }
                    }

                    net.layers.get(i).s.set(j, sum_new);
                    net.layers.get(i).sp.set(j, spikes_new);
                    net.layers.get(i).m.set(j, mem_new);
                    net.layers.get(i).r.set(j, refrac_new);
                }
            }
        }

        //concatenate all end layer feature maps into vector
        //fv
        ch.unizh.ini.jaer.projects.ziyispikingcnn.SpikingNetStructure.LAYER currentLayer = net.layers.get(net.layers.size() - 1);
        net.fv = new float[currentLayer.dimx * currentLayer.dimy * currentLayer.outmaps];
        int idx = 0;
        for (int i = 0; i < currentLayer.outmaps; i++) {
            for (int j = 0; j < currentLayer.dimy; j++) {
                for (int k = 0; k < currentLayer.dimx; k++) {
                    net.fv[idx] = currentLayer.sp.get(i)[k][j];
                    idx++;
                }
            }
        }

        //sum_fv
        for (int i = 0; i < net.sum_fv.length; i++) {
            net.sum_fv[i] = net.sum_fv[i] + net.fv[i];
        }

        //run the output layer neurons
        //add inputs multiplied by weights
        //ffw*fv
        int d = net.ffw.length;
        int e = net.fv.length;

        float[] impulse = new float[d];
        for (int i = 0; i < d; i++) {
            impulse[i] = 0.0f;
        }

        for (int i = 0; i < d; i++) {
            for (int j = 0; j < e; j++) {
                impulse[i] = impulse[i] + net.ffw[i][j] * net.fv[j];
            }
        }

        //add bias
        for (int i = 0; i < impulse.length; i++) {
            impulse[i] = impulse[i] + net.ffb[i];
        }

        //only add input from neurons past their refractory point
        for (int i = 0; i < d; i++) {
            if (net.o_refrac_end[i] >= currenttime) {
                impulse[i] = 0.0f;
            }
        }

        //add input to membrane potential
        for (int i = 0; i < d; i++) {
            net.o_mem[i]=net.o_mem[i]+impulse[i];
            if (net.o_mem[i] < negative_threshold) {
                net.o_mem[i] = negative_threshold;
            }
        }

        //check for spiking
        net.o_spikes = new int[d];
        for (int i = 0; i < d; i++) {
            if (net.o_mem[i] >= threshold) {
                net.o_spikes[i] = 1;
            } else {
                net.o_spikes[i] = 0;
            }
        }

        //reset
        for (int i = 0; i < d; i++) {
            if (net.o_spikes[i] == 1) {
                net.o_mem[i] = 0.0f;
            }
        }

        //ban updates until
        for (int i = 0; i < d; i++) {
            if (net.o_spikes[i] == 1) {
                net.o_refrac_end[i] = currenttime + t_ref;
            }
        }

        //store results for analysis later
        for (int i = 0; i < d; i++) {
            if (!reset) {
                net.o_sum_spikes[i] = net.o_sum_spikes[i] * (float) Math.exp(- (currenttime-t_previous) / decay_output) + net.o_spikes[i];
            }else{
                net.o_sum_spikes[i] = net.o_sum_spikes[i] + net.o_spikes[i];
            }
        }
    }

    public static float[][] convolution2D(float[][] input, int width, int height, float[][] kernel, int kernelWidth, int kernelHeight){

        /**
         * flip kernel
         */
        float[][] kernel_flipped = new float[kernelWidth][kernelHeight];
        for (int i = 0; i < kernelWidth; i++) {
            for (int j = 0; j < kernelHeight; j++) {
                kernel_flipped[i][j]=kernel[kernelWidth-1-i][kernelHeight-1-j];
            }
        }


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
                output[i][j] = singlePixelConvolution(input,i,j,kernel_flipped,
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

    public static float[][] MaxConvolution(float[][] input, int x, int y, int kernelWidth, int kernelHeight){
        float output[][] = new float[x-kernelHeight+1][y-kernelHeight+1];
        for (int i = 0; i < output.length; i++) {
            for (int j = 0; j < output[0].length; j++) {
                float max=0.0f;
                for (int k = i; k < i+kernelWidth; k++) {
                    for (int l = j; l < i+kernelHeight; l++) {
                        if (input[k][l]>max) {
                            max = input[k][l];
                        }
                    }
                }
                output[i][j]=max;
            }
        }
        return output;
    }

    public static float[][] MaxSubsample(float[][] input, int x, int y, int scale){
        float output[][] = new float[x/scale][y/scale];
        for (int i = 0; i < x/scale; i++) {
            for (int j = 0; j < y/scale; j++) {
                float max = 0.0f;
                for (int k = i*scale; k < (1+i)*scale; k++) {
                    for (int l = j*scale; l < (1+j)*scale; l++) {
                        if (input[k][l]>max){
                            max=input[k][l];
                        }
                    }
                }
                output[i][j]=max;
            }
        }
        return output;
    }

    private class Error {

        int totalCount, totalCorrect, totalIncorrect;
        int[] correct = new int[4], incorrect = new int[4], count = new int[4];
        protected int pixelErrorAllowedForSteering = getInt("pixelErrorAllowedForSteering", 10);
        int dvsTotalCount, dvsCorrect, dvsIncorrect;
        int apsTotalCount, apsCorrect, apsIncorrect;

        public Error() {
            reset();
        }

        void reset() {
            totalCount = 0;
            totalCorrect = 0;
            totalIncorrect = 0;
            Arrays.fill(correct, 0);
            Arrays.fill(incorrect, 0);
            Arrays.fill(count, 0);
            dvsTotalCount = 0;
            dvsCorrect = 0;
            dvsIncorrect = 0;
            apsTotalCount = 0;
            apsCorrect = 0;
            apsIncorrect = 0;
        }

        void addSample(TargetLabeler.TargetLocation gtTargetLocation, int descision, boolean apsType) {
            totalCount++;
            if ((gtTargetLocation == null)) {
                return;
            }
            if (apsType) {
                apsTotalCount++;
            } else {
                dvsTotalCount++;
            }

            int third = chip.getSizeX() / 3;

            if (gtTargetLocation.location != null) {
                int x = (int) Math.floor(gtTargetLocation.location.x);
                int gtDescision = x / third;
                if (gtDescision < 0 || gtDescision > 3) {
                    return; // bad descision output, should not happen
                }
                count[gtDescision]++;
                if (gtDescision == descision) {
                    correct[gtDescision]++;
                    totalCorrect++;
                    if (apsType) {
                        apsCorrect++;
                    } else {
                        dvsCorrect++;
                    }
                } else {
                    if (/*getPixelErrorAllowedForSteering() == 0*/true) {
                        incorrect[gtDescision]++;
                        totalIncorrect++;
                        if (apsType) {
                            apsIncorrect++;
                        } else {
                            dvsIncorrect++;
                        }
                    } else {
                        boolean wrong = true;
                        // might be error but maybe not if the descision is e.g. to left and the target location is just over the border to middle
                        float gtX = gtTargetLocation.location.x;
                        if (descision == LEFT && gtX < third + pixelErrorAllowedForSteering) {
                            wrong = false;
                        } else if (descision == CENTER && gtX >= third - pixelErrorAllowedForSteering && gtX <= 2 * third + pixelErrorAllowedForSteering) {
                            wrong = false;
                        } else if (descision == RIGHT && gtX >= 2 * third - pixelErrorAllowedForSteering) {
                            wrong = false;
                        }
                        if (wrong) {
                            incorrect[gtDescision]++;
                            totalIncorrect++;
                            if (apsType) {
                                apsIncorrect++;
                            } else {
                                dvsIncorrect++;
                            }

                        }
                    }
                }

            } else {
                count[INVISIBLE]++;
                if (descision == INVISIBLE) {
                    correct[INVISIBLE]++;
                    totalCorrect++;
                    if (apsType) {
                        apsCorrect++;
                    } else {
                        dvsCorrect++;
                    }
                } else {
                    incorrect[INVISIBLE]++;
                    totalIncorrect++;
                    if (apsType) {
                        apsIncorrect++;
                    } else {
                        dvsIncorrect++;
                    }
                }
            }
        }

    }
}
