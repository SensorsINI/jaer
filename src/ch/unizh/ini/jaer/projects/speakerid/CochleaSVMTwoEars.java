package ch.unizh.ini.jaer.projects.speakerid;

import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Observer;
import java.util.StringTokenizer;
import java.util.Vector;
import com.jogamp.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.logging.Logger;
import com.jogamp.opengl.GL2;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.projects.speakerid.libsvm320.*;

/**
 * This class allows for speaker identification using a SVM. The feature vectors
 * are based on incoming spikes and consist of features of the ISI bins and
 * channel activity rate. These vectors can be saved, used to train a SVM model,
 * or fed into an existing SVM model to predict the class label. The prediction
 * algorithm is based on the paper: C.-H. Li, T. Delbruck, and S.-C. Liu, “Real-time speaker identification using the AEREAR2 event-based silicon cochlea,” in Proc. IEEE Int. Symp. Circuits and Systems, 2012, pp. 1159–1162
 *
 * @author Philipp Klein
 */
@Description("Create feature vectors for a SVM based on spikes; train SVM models and classify new feature vectors")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class CochleaSVMTwoEars extends ISIFilterTwoEars implements FrameAnnotater, Observer, PropertyChangeListener {

    protected static final Logger log = Logger.getLogger("CochleaSVM");
    public FilterChain filterChain; //filter chain that holds the enclosed filters
    //static public ChannelEventRateEstimator channelEventRateFilter = null;  //the CahnnelEventRateEstimator
    private BufferedWriter writer;  //file writer that writes into the training and model files
    public ChannelEventRateEstimator channelEventRateFilter = new ChannelEventRateEstimator(chip);   //create a new ChannelEventRateEstimator

    //variable for recording features
    private int numberOfEvents;
    private float[] normBinsLeft;    //bins that hold the ISI information
    private float[] normBinsRight;    //bins that hold the ISI information
    private float[] deltaBins;
    private float timeOfLastWrite;
    public boolean recordFeatures = false;
    private final int numOfBins = getInt("numOfBins", 100); //number of ISI bins
    public float eventLimit = getFloat("eventLimit", 20);
    public float maxTimeIntervalToWriteMS = getFloat("maxTimeIntervalToWriteMS", 100);
    protected boolean useISI = getBoolean("useISI", true);
    protected boolean usePerChannelEventRate = getBoolean("usePerChannelEventRate", true);   //add the event rate for each channel to the training data
    protected boolean normalizePerChannelEventRate = getBoolean("normalizePerChannelEventRate", true);
    protected boolean normalizeISIBins = getBoolean("normalizeISIBins", true);
    public boolean useEventLimit = getBoolean("useEventLimit", true);
    public boolean useTimeLimit = getBoolean("useTimeLimit", true);

    //svm parameters and problems
    private final svm_parameter param;  // set by parse_command_line
    private svm_problem prob, prob2, prob_temp; // set by read_problem
    private svm_model model;    //the trained SVM model

    //variables for prediction
    protected int classLabel;   //label of the data
    private double[] pred = new double[64];
    private double[] predClass = new double[64];
    public boolean hasModel = false;
    protected boolean classifyEvents = false;   // indicates if current incoming spikes are classified
    public int tauMS = getInt("tauMS", 500);    // tau value in ms used to weight old prediction into new prediction
    public int labeledClass = getInt("labeledClass", -1);  //predicted class (-1 = invalid class = no prediction)
    private double timeSinceLastPrediction = getDouble("timeSinceLastPrediction", 0);  //defines the time since the last prediction; used to weight old data into new predictions
    
    //variables to show label in jAER viewer
    private TextRenderer titleRenderer;
    private Rectangle titleArea;
    volatile float lastdot = 0;

    //variable for choosing working directory and loading models   
    private JFileChooser chooser;
    private int state;
    private File workingDirectory;

    //label frame
    JFrame labelBarChart = new JFrame();
    private double[] values;
    private String[] names;
    private String title;

    private boolean propertyChangeListenerAdded = false;

    public CochleaSVMTwoEars(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(channelEventRateFilter);    //add the ChannelEventRatefilter to the filterchain of this filte
        setEnclosedFilterChain(filterChain);    //set the filterchain as enclosed filters of the CochleaSVMTwoEars filter
        // do buttons
        setPropertyTooltip("TrainSVMWithOnlineData", "use the collected feature vectors to train a SVM model");
        setPropertyTooltip("chooseWorkingDirectory", "choose the working directory where the model and training files are saved in");
        setPropertyTooltip("LoadModel", "loads a precomputed SVM model");
        setPropertyTooltip("PrintBins", "show the current value of all ISI bins");
        setPropertyTooltip("Reset", "resets the training and prediction system");
        setPropertyTooltip("ResetPrediction", "resets the prediction and time");
        setPropertyTooltip("ToggleRecordingFeatures", "switch recording features ON and OFF");
        setPropertyTooltip("LoadDataSetAndTrainSVM", "load a prerecorded training file and train a SVM with it");
        setPropertyTooltip("ResetFeatureVectors", "deletes the collected feature vectors");
        setPropertyTooltip("TogglePrediction", "switch between perdiction ON and OFF");
        setPropertyTooltip("SaveRecordedFeatures", "save the currently recorded feature vectors in a .txt file");
        setPropertyTooltip("nextClass", "go to the next class, useful for training");
        // int/float variables
        setPropertyTooltip("maxTimeIntervalToWriteMS", "time interval to wait before attempting to write a new feature vector; only if event limit is reached");
        setPropertyTooltip("eventLimit", "number of events required before writing a new feature vector");
        setPropertyTooltip("tauMS", "time constant for prediction; defines how much the past probabilities determine the current probabilities");
        setPropertyTooltip("classLabel", "set which speaker is speaking; for collecting training data");
        // boolean variables
        setPropertyTooltip("classifyEvents", "starts collecting feature vectors and classifies them using a SVM model");
        setPropertyTooltip("useISI", "include ISI features in the feature vector");
        setPropertyTooltip("usePerChannelEventRate", "include channel event rates in the feature vector");
        setPropertyTooltip("normalizePerChannelEventRate", "normalize the channel event rates between 0:1");
        setPropertyTooltip("normalizeISIBins", "normalize the ISI bins between 0:1");
        setPropertyTooltip("recordFeatures", "indicates if feature vectors are currently recorded");
        setPropertyTooltip("useEventLimit", "use event limit to decide if a new feature vector is recorded");
        setPropertyTooltip("useTimeLimit", "use time inteval to decide if a new feature vector is recorded");
        setPropertyTooltip("hasModel", "indicates if a valid model is loaded");
        channelEventRateFilter.addObserver(this);
        setNBins(numOfBins);
        chooser = new JFileChooser();   //file chooser for loading and saving files
        param = new svm_parameter();            //initialize SVM parameters
        param.svm_type = svm_parameter.C_SVC;   //type of the SVM (C_SVC, NU_SVC, ONE_CLASS, EPSILON_SVR, NU_SVR)
        param.kernel_type = svm_parameter.RBF;  //kernel type (LINEAR, POLY, RBF, SIGMOID, PRECOMUTED)
        param.degree = 3;                       //degree of the kernel function (for POLY kernel) (default 3)
        param.gamma = 0;                        //gamma in the kernel function (for POLY, RBF, SIGMOID) (default 1/num_features)
        param.coef0 = 0;                        //coef0 in the kernel function (for POLY, SIGMOID) (default 0)
        param.C = 1024;                            //cost parameter (C) for C-SVC, EPSILON-SVR, nu-SVR (default 1)
        param.nu = 0.5;                         //nu parameter for nu-SVC, one-class SVM, nu-SVR (default 0.5)
        param.p = 0.1;                          //epsilon in loss function of epsilon-SVR (default 0.1)
        param.cache_size = 1000;                 //cache memomry in MB (default 100)
        param.eps = 1e-3;                       //tolerance of termination criterion (default 0.001)
        param.shrinking = 1;                    //whether to use shrinking heuristics, 0 or 1 (default 1)
        param.probability = 1;                  //wether to train a SVC or SVR model for probability estimates, 0 or 1 (default 0)
        param.nr_weight = 0;                    //parameter C of class i to weight*C (for SVC) (default 1)
        param.weight_label = new int[0];
        param.weight = new double[0];
        setTauDecayMs(1000);
        while (workingDirectory == null) {
            doChooseWorkingDirectory();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            chip.getAeViewer().getAePlayer().setPaused(true);
            timeSinceLastPrediction = 0;
            timeOfLastWrite = 0;
        }
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!propertyChangeListenerAdded) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
                propertyChangeListenerAdded = true;
            }
        }
        channelEventRateFilter.filterPacket(in);    //uses the current EventPacket to get the estimated event rate (with the ChannelEventRateEstimator) 
        super.filterPacket(in); //uses the current EventPacket to get the ISIs (with ISI filter (super))
        float[] binsLeft = getLeftBins();    //get the ISI values from the ISI filter and saves it in the binsOne variable
        float[] binsRight = getRightBins();
        numberOfEvents += in.getSize();
        float now = in.getLastTimestamp();
        //if (numberOfEvents > eventLimit) {    //only write/online-train if the number of events since last writing exceeds the threshold or the time since last writing exceeds the threshold
        if (((isUseTimeLimit() == true && now - timeOfLastWrite > (maxTimeIntervalToWriteMS * 1000)) && (isUseEventLimit() == true && numberOfEvents > eventLimit))
                || (isUseTimeLimit() == false && isUseEventLimit() == true && numberOfEvents > eventLimit)
                || (isUseTimeLimit() == true && isUseEventLimit() == false && now - timeOfLastWrite > (maxTimeIntervalToWriteMS * 1000))) {
            if (isRecordFeatures()) {
                prob2 = new svm_problem();  //creates a new problem set of feature vectors
                if (prob == null) {
                    prob = new svm_problem();   //create a new problem set
                }
                prob2.l = prob.l + 1;   //prob2 holds one more data item than prob
                prob2.x = new svm_node[prob2.l][];  //creates a svm_node for all old feature vectors of prob and 1 new vector
                for (int i = 0; i < prob.l; i++) {  //copies the feature vectors from prob to prob2
                    prob2.x[i] = prob.x[i];
                }
                prob2.y = new double[prob2.l];
                for (int i = 0; i < prob.l; i++) {  //copies the label items from prob to prob2
                    prob2.y[i] = prob.y[i];
                }
                prob2.y[prob2.l - 1] = classLabel;  //assigns the current classLabel to the last vector
                int binsLength = 0;
                if (useISI) {
                    binsLength += binsLeft.length;    //save the number of bins
                    binsLength += binsRight.length;    //save the number of bins
                }
                if (usePerChannelEventRate) {
                    binsLength += this.chip.getSizeX(); //increase the number of vector items by the number of cochlea channels
                }
                svm_node[] x = new svm_node[binsLength];       //declare and initialize a new array x of svm_nodes with a size that equals the number of features (vector items)
                int counter = 1;
                if (useISI) {
                    if (normalizeISIBins) {
                        normalizeISIBins(binsLeft, binsRight);
                        for (int j = 0; j < normBinsLeft.length; j++) {
                            x[counter - 1] = new svm_node();              //create a new node in the array for each ISI bin
                            x[counter - 1].index = counter;               //assigns the current feature number to the node
                            x[counter - 1].value = normBinsLeft[j];        //assigns the bin value to the node;
                            counter++;
                        }
                        for (int j = 0; j < normBinsRight.length; j++) {
                            x[counter - 1] = new svm_node();              //create a new node in the array for each ISI bin
                            x[counter - 1].index = counter;               //assigns the current feature number to the node
                            x[counter - 1].value = normBinsRight[j];        //assigns the bin value to the node;
                            counter++;
                        }
                    } else {
                        for (int j = 0; j < binsLeft.length; j++) {
                            x[counter - 1] = new svm_node();
                            x[counter - 1].index = counter;
                            x[counter - 1].value = binsLeft[j];
                            counter++;
                        }
                        for (int j = 0; j < binsRight.length; j++) {
                            x[counter - 1] = new svm_node();              //create a new node in the array for each ISI bin
                            x[counter - 1].index = counter;               //assigns the current feature number to the node
                            x[counter - 1].value = binsRight[j];        //assigns the bin value to the node;
                            counter++;
                        }
                    }
                }
                if (usePerChannelEventRate) {
                    float[] channelEventRates = perChannelEventRate();
                    for (int i = 0; i < this.chip.getSizeX(); i++) {
                        x[counter - 1] = new svm_node();
                        x[counter - 1].index = counter;                     //assigns the current feature number to the node
                        x[counter - 1].value = channelEventRates[i];        //add per channel activity to the array
                        counter++;
                    }
                }
                prob2.x[prob2.l - 1] = x;                   //add the new feature vector to the problem set
                //copy from prob2 to prob
                prob = new svm_problem();   //new problem
                prob.l = prob2.l;   //copies and saves prob2 in prob
                prob.x = new svm_node[prob.l][];
                for (int i = 0; i < prob.l; i++) {
                    prob.x[i] = prob2.x[i];
                }
                prob.y = new double[prob.l];
                for (int i = 0; i < prob.l; i++) {
                    prob.y[i] = prob2.y[i];
                }
                timeOfLastWrite = now;    //save the current time
                numberOfEvents = 0;       //resets the number of events since the last line was written
            }
            if (isClassifyEvents()) {
                if (prob != null) {
                    timeSinceLastPrediction = now - timeSinceLastPrediction;
                    predict();    //predict the class of the incoming data
                    timeSinceLastPrediction = now;    //updates the variable containing the last time a line was written
                    doResetFeatureVectors();
                } else {
                    log.info("No recorded features");
                    setClassifyEvents(false);
                }
            }
        }
        return in;
    }

    public void doToggleRecordingFeatures() {
        if (isRecordFeatures() == true) {
            setRecordFeatures(false);
        } else {
            setRecordFeatures(true);
        }
    }

    public void doSaveRecordedFeatures() {
        if (prob != null) {
            setRecordFeatures(false);
            try {
                saveTrainingData(prob);   //saves the online recorded data
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        } else {
            log.info("No recorded features");
        }
    }

    public void doTrainSVMWithOnlineData() {
        if (prob != null) {
            setRecordFeatures(false);
            try {
                saveTrainingData(prob);   //saves the online recorded data
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
            param.gamma = 1.0 / prob.x[prob.x.length - 1].length;   //calculate the gamma parameter
            if (param.kernel_type == svm_parameter.PRECOMPUTED) {                       //if using a precomputed kernel check if the parameters are correct
                for (int i = 0; i < prob2.l; i++) {
                    if (prob2.x[i][0].index != 0) {
                        log.severe("Wrong kernel matrix: first column must be 0:sample_serial_number\n");
                        System.exit(1);
                    }
                    if ((int) prob2.x[i][0].value <= 0 || (int) prob2.x[i][0].value > prob.x[0].length) {
                        log.severe("Wrong input format: sample_serial_number out of range\n");
                        System.exit(1);
                    }
                }
            }
            trainSVM(prob, param);   //starts the training of the SVM with each new problem set
        } else {
            log.info("No recorded features");
        }
    }

    public void doTogglePrediction() {
        if (isClassifyEvents() == true) {
            setClassifyEvents(false);
        } else {
            setClassifyEvents(true);
        }
    }

    public void doResetFeatureVectors() {
        prob = null;
        prob2 = null;
    }

    public void doChooseWorkingDirectory() {                            //choose the working directory
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        state = chooser.showDialog(null, "Select working Directory");
        if (state == JFileChooser.APPROVE_OPTION) {
            workingDirectory = new File(chooser.getSelectedFile().getAbsolutePath() + "\\");
            chooser.setSelectedFile(new File(workingDirectory + "\\ "));
            log.info(String.format("Set working directory to: %s", workingDirectory));
        }
    }

    public void doLoadDataSetAndTrainSVM() {                          //trains a SVM based on a prerecorded training file
        prob = null;
        prob2 = null;
        try {
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);  //choose a training data set
            chooser.setMultiSelectionEnabled(false);
            state = chooser.showOpenDialog(null);
            if (state == JFileChooser.APPROVE_OPTION) {
                int extInd = chooser.getSelectedFile().getCanonicalPath().lastIndexOf(".txt");
                String base = chooser.getSelectedFile().getCanonicalPath();
                if (extInd > 0) {
                    base = chooser.getSelectedFile().getCanonicalPath().substring(0, extInd);   //ensure that the selected training file is a .txt file 
                }
                String filePath = String.format(base + ".txt");
                loadTrainingSet(filePath);                                                          //load the training data in a new problem
                log.info("Opened training data file: " + filePath);
                trainSVM(prob, param);
            }
        } catch (IOException e) {
            log.warning(e.toString());
        }
    }

    private void trainSVM(svm_problem prob, svm_parameter param) {
        if (prob == null) {
            return;
        }
        try {
            log.info("start training SVM");
            model = svm.svm_train(prob, param);        //start the training with the acquired training data
            log.info("finished training SVM");
            SimpleDateFormat sdfDate = new SimpleDateFormat("_yyyy_MM_dd_HH_mm_ss");                //get the current time in yyyy_MM_dd_HH_mm_ss format
            Date now = new Date();
            String strDate = sdfDate.format(now);
            String filePath = String.format("%s/model%s.txt", workingDirectory, strDate);
            svm.svm_save_model(filePath, model);                                                    //save the model with the current timestamp 
            log.info("Saved model to: " + filePath);
            setHasModel(true);
        } catch (IOException e) {
            log.warning(e.toString());
        }
        if (model != null && classifyEvents == true) {
            labelBarChart.getContentPane().removeAll();
            labelBarChart.setSize(400, 300);
            double[] values = new double[model.nr_class];
            String[] names = new String[model.nr_class];
            for (int i = 0; i < names.length; i++) {
                names[i] = Integer.toString(i);
            }
            labelBarChart.getContentPane().add(new ChartPanel(values, names, "class probabilities"));
            labelBarChart.setVisible(true);
        }
    }

    public void doLoadModel() {                         //load a precomputed model for the SVM
        try {
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(false);
            state = chooser.showOpenDialog(null);
            if (state == JFileChooser.APPROVE_OPTION) {
                int extInd = chooser.getSelectedFile().getCanonicalPath().lastIndexOf(".txt");
                String base = chooser.getSelectedFile().getCanonicalPath();
                if (extInd > 0) {
                    base = chooser.getSelectedFile().getCanonicalPath().substring(0, extInd);   //ensure that the selected file is a .txt file
                }
                String filePath = String.format(base + ".txt");
                model = svm.svm_load_model(filePath);           //load the svm model
                log.info("Opened model file: " + filePath);
                setHasModel(true);
            }
        } catch (IOException e) {
            log.warning(e.toString());
        }
        if (model != null && classifyEvents == true) {
            labelBarChart.getContentPane().removeAll();
            labelBarChart.setSize(400, 300);
            double[] values = new double[model.nr_class];
            String[] names = new String[model.nr_class];
            for (int i = 0; i < names.length; i++) {
                names[i] = Integer.toString(i);
            }
            labelBarChart.getContentPane().add(new ChartPanel(values, names, "class probabilities"));
            labelBarChart.setVisible(true);
        }
    }

    public void predict() {                               //predict the label of the incoming data                          
        if (model == null) {                                //check that a model is loaded
            log.info("no model loaded");
            setClassifyEvents(false);
            return;
        }
        double[] prob_estimates = new double[model.nr_class];
        svm.svm_predict_probability(model, prob.x[prob.x.length - 1], prob_estimates); //use the loaded model to predict a new feature vector (prob.x) and write the probability estimates for each class into an array (prob_estimates)
        for (int i = 0; i < prob_estimates.length; i++) {
            pred[i] = prob_estimates[i] * maxOfArray(prob_estimates) + pred[i] * Math.exp(-timeSinceLastPrediction / (getTauMS() * 1000));  //use the probaility for class(i) multiplied by the max probability and add some history to it multiplied by a decay
            values[i] = pred[i];
        }
        labeledClass = maxOfArrayIndex(pred);
        labelBarChart.repaint();
    }

    private double maxOfArray(double[] array) {
        double max = Double.MIN_VALUE;
        for (double i : array) {
            max = Math.max(max, i);
        }
        return max;
    }

    private int maxOfArrayIndex(double[] array) {
        double max = Double.MIN_VALUE;
        int index = -1;
        for (int i = 0; i < array.length; i++) {
            max = Math.max(max, array[i]);
            if (max == array[i]) {
                index = i;
            }
        }
        return index;
    }

    private void saveTrainingData(svm_problem prob) throws IOException {
        try {   //initialize a new .txt file to write the features vectors into
            if (state == JFileChooser.APPROVE_OPTION) {
                SimpleDateFormat sdfDate = new SimpleDateFormat("_yyyy_MM_dd_HH_mm_ss");            //dd/MM/yyyy    
                Date now = new Date();
                String strDate = sdfDate.format(now);
                FileWriter fstream;
                String filePath = String.format("%s/features%s.txt", workingDirectory, strDate);    //saves the training file at selected position with current timestamp
                fstream = new FileWriter(filePath);
                log.info("Start saving training data in:" + filePath);
                writer = new BufferedWriter(fstream);
            }
        } catch (IOException e) {
            log.warning(e.toString());
        }
        for (int i = 0; i < prob.l; i++) {
            String line = "";
            for (int j = 0; j < prob.x[i].length; j++) {
                line += prob.x[i][j].index + ":" + prob.x[i][j].value + " ";     //add new index:value to the line
            }
            writer.write(prob.y[i] + " " + line + "\n");   //writes the class label and the line (containing the training information) to the training file. Finishes the line 
        }
        writer.close();
        log.info("finished saving training data");
    }

    private static double atof(String s) {              //ASCI string to floatingpoint number
        double d = Double.valueOf(s).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            log.severe("NaN or Infinity in input\n");
            System.exit(1);
        }
        return (d);
    }

    private static int atoi(String s) {                 //ASCI string to integer number
        return Integer.parseInt(s);
    }

    private void loadTrainingSet(String input_file_name) throws IOException {           //loads a prerecorded training file
        BufferedReader reader = new BufferedReader(new FileReader(input_file_name));
        Vector<Double> vy = new Vector<Double>();                                   //vector to hold the labels of each problem
        Vector<svm_node[]> vx = new Vector<svm_node[]>();                           //vector to hold the features of each problem        
        int max_index = 0;                                                          //max index in the problem
        while (true) {
            String line = reader.readLine();                                        //read a line from the .txt file
            if (line == null) {                                                     //if no line is present end the while loop
                break;
            }
            StringTokenizer st = new StringTokenizer(line, " \t\n\r\f:");           //split each line into tokens; first token = class; other tokens feature indecies and values
            vy.addElement(atof(st.nextToken()));                                    //save the first token of the line as the label
            int m = st.countTokens() / 2;
            svm_node[] x = new svm_node[m];                                         //create a node for each feature of a problem
            for (int j = 0; j < m; j++) {
                x[j] = new svm_node();
                x[j].index = atoi(st.nextToken());                                  //index of the feature (1-50 = ISI bins) (others = activity bins)
                x[j].value = atof(st.nextToken());                                  //value of the feature
            }
            if (m > 0) {
                max_index = Math.max(max_index, x[m - 1].index);                    //max index
            }
            vx.addElement(x);                                                       //add the problem x to the problem vector
        }
        prob = new svm_problem();                                                   //create a svm problem    
        prob.l = vy.size();                                                         //numbers of lines = numbers of line in the vy vector
        prob.x = new svm_node[prob.l][];                                            //create a node for each feature line
        for (int i = 0; i < prob.l; i++) {
            prob.x[i] = vx.elementAt(i);                                            //copy the features to the problem
        }
        prob.y = new double[prob.l];
        for (int i = 0; i < prob.l; i++) {
            prob.y[i] = vy.elementAt(i);                                            //copy the labels to the problem
        }
        if (param.gamma == 0 && prob.x[0].length > 0) {                                    //calculate the gamma parameter
            param.gamma = 1.0 / prob.x[0].length;
        }
        if (param.kernel_type == svm_parameter.PRECOMPUTED) {                       //if using a precomputed kernel check if the parameters are correct
            for (int i = 0; i < prob.l; i++) {
                if (prob.x[i][0].index != 0) {
                    log.severe("Wrong kernel matrix: first column must be 0:sample_serial_number\n");
                    System.exit(1);
                }
                if ((int) prob.x[i][0].value <= 0 || (int) prob.x[i][0].value > max_index) {
                    log.severe("Wrong input format: sample_serial_number out of range\n");
                    System.exit(1);
                }
            }
        }
        reader.close();                                                             //end the file reader
    }

    private void normalizeISIBins(float[] leftBins, float[] rightBins) {               //normalize the ISI bins by the maximum bin value
        normBinsLeft = new float[leftBins.length]; //array to hold the normalized ISI bins
        normBinsRight = new float[rightBins.length]; //array to hold the normalized ISI bins
        for (int i = 0; i < leftBins.length; i++) {
            float maxBin = super.getLeftMaxBin();   //find the bin with the highest number
            if (maxBin != 0) {
                normBinsLeft[i] = (float) leftBins[i] / maxBin; //normalize between 0:1
            } else {
                normBinsLeft[i] = 0;
            }
        }
        for (int i = 0; i < rightBins.length; i++) {
            float maxBin = super.getRightMaxBin();
            if (maxBin != 0) {
                normBinsRight[i] = (float) rightBins[i] / maxBin;
            } else {
                normBinsRight[i] = 0;
            }
        }        
    }

    float[] perChannelEventRate() {
        float[] channelEventRates = new float[this.getChip().getSizeX()];
        float maxChannelEventRate = 0;
        for (int i = 0; i < channelEventRates.length; i++) {
            channelEventRates[i] = channelEventRateFilter.getFilteredEventRate(i);     //get the event rate of channel i
            if (channelEventRates[i] > maxChannelEventRate) {
                maxChannelEventRate = channelEventRates[i];
            }
        }
        if (normalizePerChannelEventRate && maxChannelEventRate != 0) {
            for (int i = 0; i < channelEventRates.length; i++) {
                channelEventRates[i] = channelEventRates[i] / maxChannelEventRate;        //normalize each channel event rate by the average event rate
            }
        }
        return channelEventRates;
    }
    
    public void doNextClass(){      //increases the current class number, useful for training
        int currentClass = getClassLabel();
        int nextClass = currentClass + 1;
        setClassLabel(nextClass);
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter">
    public float getEventLimit() {
        return this.eventLimit;
    }

    public void setEventLimit(float eventLimit) {
        putFloat("eventLimit", eventLimit);
        float oldeventLimit = this.eventLimit;
        this.eventLimit = eventLimit;
        support.firePropertyChange("eventLimit", oldeventLimit, eventLimit);
    }

    public int getClassLabel() {
        return this.classLabel;
    }

    public void setClassLabel(int classLabel) {
        putInt("classLabel", classLabel);
        int oldclassLabel = this.classLabel;
        this.classLabel = classLabel;
        support.firePropertyChange("classLabel", oldclassLabel, classLabel);
        timeOfLastWrite = 0;
        numberOfEvents = 0;
        this.resetBins();
    }

    public float getMaxTimeIntervalToWriteMS() {
        return this.maxTimeIntervalToWriteMS;
    }

    public void setMaxTimeIntervalToWriteMS(float maxTimeIntervalToWriteMS) {
        putFloat("maxTimeIntervalToWriteMS", maxTimeIntervalToWriteMS);
        float oldmaxTimeIntervalToWriteMS = this.maxTimeIntervalToWriteMS;
        this.maxTimeIntervalToWriteMS = maxTimeIntervalToWriteMS;
        support.firePropertyChange("maxTimeIntervalToWriteMS", oldmaxTimeIntervalToWriteMS, maxTimeIntervalToWriteMS);
    }

    public int getTauMS() {
        return this.tauMS;
    }

    public void setTauMS(int tauMS) {
        putInt("tauMS", tauMS);
        int oldtauMS = this.tauMS;
        this.tauMS = tauMS;
        support.firePropertyChange("tauMS", oldtauMS, tauMS);
    } // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter">
    public boolean isClassifyEvents() {
        return this.classifyEvents;
    }

    public void setClassifyEvents(boolean classifyEvents) {
        if (model != null && classifyEvents == true) {
            if (model == null) {                                //check that a model is loaded
                log.info("no model loaded");
                return;
            }
            labelBarChart.getContentPane().removeAll();
            labelBarChart.setSize(400, 300);
            double[] values = new double[model.nr_class];
            String[] names = new String[model.nr_class];
            for (int i = 0; i < names.length; i++) {
                names[i] = Integer.toString(i);
            }
            labelBarChart.getContentPane().add(new ChartPanel(values, names, "class probabilities"));
            labelBarChart.setVisible(true);
        }
        putBoolean("classifyEvents", classifyEvents);
        boolean oldclassifyEvents = this.classifyEvents;
        this.classifyEvents = classifyEvents;
        support.firePropertyChange("classifyEvents", oldclassifyEvents, classifyEvents);
    }

    public boolean isNormalizePerChannelEventRate() {
        return this.normalizePerChannelEventRate;
    }

    public void setNormalizePerChannelEventRate(boolean normalizePerChannelEventRate) {
        putBoolean("normalizePerChannelEventRate", normalizePerChannelEventRate);
        boolean oldnormalizePerChannelEventRate = this.normalizePerChannelEventRate;
        this.normalizePerChannelEventRate = normalizePerChannelEventRate;
        support.firePropertyChange("normalizePerChannelEventRate", oldnormalizePerChannelEventRate, normalizePerChannelEventRate);
    }

    public boolean isUsePerChannelEventRate() {
        return this.usePerChannelEventRate;
    }

    public void setUsePerChannelEventRate(boolean usePerChannelEventRate) {
        putBoolean("usePerChannelEventRate", usePerChannelEventRate);
        boolean oldusePerChannelEventRate = this.usePerChannelEventRate;
        this.usePerChannelEventRate = usePerChannelEventRate;
        support.firePropertyChange("usePerChannelEventRate", oldusePerChannelEventRate, usePerChannelEventRate);
    }

    public boolean isNormalizeISIBins() {
        return this.normalizeISIBins;
    }

    public void setNormalizeISIBins(boolean normalizeISIBins) {
        putBoolean("normalizeISIBins", normalizeISIBins);
        boolean oldnormalizeISIBins = this.normalizeISIBins;
        this.normalizeISIBins = normalizeISIBins;
        support.firePropertyChange("normalizeISIBins", oldnormalizeISIBins, normalizeISIBins);
    }

    public boolean isUseISI() {
        return this.useISI;
    }

    public void setUseISI(boolean useISI) {
        putBoolean("useISI", useISI);
        boolean olduseISI = this.useISI;
        this.useISI = useISI;
        support.firePropertyChange("useISI", olduseISI, useISI);
    }

    public boolean isUseEventLimit() {
        return this.useEventLimit;
    }

    public void setUseEventLimit(boolean useEventLimit) {
        putBoolean("useEventLimit", useEventLimit);
        boolean olduseEventLimit = this.useEventLimit;
        this.useEventLimit = useEventLimit;
        support.firePropertyChange("useEventLimit", olduseEventLimit, useEventLimit);
    }

    public boolean isUseTimeLimit() {
        return this.useTimeLimit;
    }

    public void setUseTimeLimit(boolean useTimeLimit) {
        putBoolean("useTimeLimit", useTimeLimit);
        boolean olduseTimeLimit = this.useTimeLimit;
        this.useTimeLimit = useTimeLimit;
        support.firePropertyChange("useTimeLimit", olduseTimeLimit, useTimeLimit);
    }

    public boolean isRecordFeatures() {
        return this.recordFeatures;
    }

    public void setRecordFeatures(boolean recordFeatures) {
        putBoolean("recordFeatures", recordFeatures);
        boolean oldrecordFeatures = this.recordFeatures;
        this.recordFeatures = recordFeatures;
        support.firePropertyChange("recordFeatures", oldrecordFeatures, recordFeatures);
    }

    public boolean isHasModel() {
        return this.hasModel;
    }

    public void setHasModel(boolean hasModel) {
        putBoolean("hasModel", hasModel);
        boolean oldhasModel = this.hasModel;
        this.hasModel = hasModel;
        support.firePropertyChange("hasModel", oldhasModel, hasModel);
    }// </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Graphics to show label in jAER Viewer">
    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }
    private GLUT glut = new GLUT();

    @Override
    public void annotate(GLAutoDrawable drawable) {         //shows the class in the jAER window
        if ((drawable == null) || (chip.getCanvas() == null) || !classifyEvents || model == null) {
            return;
        }
        titleRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN, 40));
        Rectangle2D bounds = titleRenderer.getBounds("Unkown");
        titleArea = new Rectangle((int) bounds.getWidth(), (int) bounds.getHeight());
        GL2 gl = drawable.getGL().getGL2();
        gl.glColor3f(1, 1, 1);
        gl.glRasterPos3f(10, 10, 0);
        titleRenderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        titleRenderer.setColor(Color.WHITE);
        titleRenderer.draw(String.format("Class: %d", labeledClass), titleArea.x, titleArea.y);
        titleRenderer.endRendering();
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glTranslatef(drawable.getSurfaceWidth() / 2, drawable.getSurfaceHeight() / 2, 0);
        gl.glColor3f(1, 1, 1);
        float w = drawable.getSurfaceWidth() * lastdot * 5;
        gl.glRectf(0, -10, w, 10);
        gl.glPopMatrix();
    } // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="label bar chart">
    public class ChartPanel extends JPanel {

        public ChartPanel(double[] v, String[] n, String t) {
            names = n;
            values = v;
            title = t;
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (values == null || values.length == 0) {
                return;
            }
            double minValue = 0;
            double maxValue = 0;
            for (int i = 0; i < values.length; i++) {
                if (maxValue < values[i]) {
                    maxValue = values[i];
                }
            }
            Dimension d = getSize();
            int clientWidth = d.width;
            int clientHeight = d.height;
            int barWidth = (clientWidth / values.length);

            Font titleFont = new Font("SansSerif", Font.BOLD, 50);
            FontMetrics titleFontMetrics = g.getFontMetrics(titleFont);
            Font labelFont = new Font("SansSerif", Font.PLAIN, 30);
            FontMetrics labelFontMetrics = g.getFontMetrics(labelFont);

            int titleWidth = titleFontMetrics.stringWidth(title);
            int y = titleFontMetrics.getAscent();
            int x = (clientWidth - titleWidth) / 2;
            g.setFont(titleFont);
            g.drawString(title, x, y);

            int top = titleFontMetrics.getHeight();
            int bottom = labelFontMetrics.getHeight();
            if (maxValue == minValue) {
                return;
            }
            double scale = (clientHeight - top - bottom) / (maxValue - minValue);
            y = clientHeight - labelFontMetrics.getDescent();
            g.setFont(labelFont);

            for (int i = 0; i < values.length; i++) {
                int valueX = i * barWidth + 1;
                int valueY = top;
                int height = (int) (values[i] * scale);
                if (values[i] >= 0) {
                    valueY += (int) ((maxValue - values[i]) * scale);
                } else {
                    valueY += (int) (maxValue * scale);
                    height = -height;
                }
                g.setColor(Color.red);
                g.fillRect(valueX, valueY, barWidth - 2, height);
                g.setColor(Color.black);
                g.drawRect(valueX, valueY, barWidth - 2, height);
                int labelWidth = labelFontMetrics.stringWidth(names[i]);
                x = i * barWidth + (barWidth - labelWidth) / 2;
                g.drawString(names[i], x, y);
            }
        }
    } // </editor-fold>

    /*
     private void calculateDeltas() {
     deltaBins = new float[normBinsOne.length / 2];
     for (int i = 0; i < (normBinsOne.length / 2); i++) {
     deltaBins[i] = (normBinsOne[i + 1] - normBinsOne[i]);
     }
     }

     private String runCommandLine(String command) {
     String myString = "";
     try {
     Runtime rt = Runtime.getRuntime();
     //Process pr = rt.exec("cmd /c dir");
     Process pr = rt.exec(command);
     BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
     String line = null;
     while ((line = input.readLine()) != null) {
     myString += line;
     }
     int exitVal = pr.waitFor();
     log.warning("Exited with error code " + exitVal);
     } catch (Exception e) {
     log.warning(e.toString());
     e.printStackTrace();
     }
     return myString;
     }
     */
}
