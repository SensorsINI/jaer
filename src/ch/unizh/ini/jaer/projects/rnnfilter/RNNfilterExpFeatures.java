package ch.unizh.ini.jaer.projects.rnnfilter;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import javax.swing.JOptionPane;

import org.apache.commons.lang3.StringUtils;
import org.jblas.FloatMatrix;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent.Ear;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent.FilterType;
import ch.unizh.ini.jaer.chip.cochlea.RollingCochleaGramDisplayMethod;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Extracts exponential features from CochleaAMS sensor and processes them
 * through a recurrent network
 *
 * @author jithendar
 */
@Description("Extracts binned spike features from CochleaAMS sensor and processes them through a recurrent network")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class RNNfilterExpFeatures extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    /**
     * Chooses the time length for the bin, the current network is trained on
     * 5ms data, hence the variable is initialized appropriately
     */
    private int binTimeLength = getPrefs().getInt("binTimeLength", 5000); // default value set to 5 ms
    /**
     * Chooses the tau for the generation of exponential features
     */
    private int tau = getPrefs().getInt("tau", 5000);
    /**
     * If data from only one ear is chosen, this variable gets the preference
     * from the user
     */
    private int chooseEar = getPrefs().getInt("chooseEar", 1); // 0 corresponds to left, 1 corresponds to right
    /**
     * If data from only one neuron is chosen, this variable gets the preference
     * from the user
     */
    private int chooseNeuron = getPrefs().getInt("chooseNeuron", 1); // choose a value between 0 and 3, otherwise a
    // random value will be chosen
    /**
     * If data from only one filter type is to be binned, this variable gets the
     * preference from the user
     */
    private int chooseFilterType = getPrefs().getInt("chooseFilterType", 1); // 0 corresponds to LPF, 1 corresponds to
    // BPF
    /**
     * The XML file to load the RNN network from
     */
    private String lastRNNXMLFile = getString("lastRNNXMLFile", "rnn_cochlea_v3b_jAER.xml");
    /**
     * Choose whether to display activations or not
     */
    private boolean displayActivations = getBoolean("displayActivations", true);
    /**
     * Choose whether to display the softmax probability of the prediction
     */
    private boolean displayAccuracy = getBoolean("displayAccuracy", true);
    /**
     * If the filter saves the feature as an array list, then choose whether to
     * display it once the feature is recorded
     */
    private boolean displayFeature = getBoolean("displayFeature", true);
    /**
     * If true, the frame is annotated with the label prediction only if the
     * softmax accuracy is more than 0.9
     */
    private boolean showPredictionOnlyIfGood = getBoolean("showPredictionOnlyIfGood", false);
    /**
     * True if the filter intends to process RNN only when you click a button,
     * if false it is a continuous RNN processing
     */
    private boolean clickToProcess = getBoolean("clickToProcess", true);
    /**
     * If needed to click to process RNN, choose if the RNN process happens as a
     * batch at the end or in a continuous live setting
     */
    private boolean batchRNNProcess = getBoolean("batchRNNProcess", false);
    /**
     * Is the filter binning the events
     */
    private boolean processingEnabled = getBoolean("processingEnabled", true);

    /**
     * Boolean variable which checks if the filter is initialized
     */
    private boolean isInitialized = false;
    /**
     * Number of channels (frequencies), when a user chooses a number other 64
     * an appropriately trained network has to be initialized
     */
    private int nChannels = 64;
    /**
     * Number of neurons for each neuron, default value of 4
     */
    private int nNeurons = 4;
    /**
     * Number of ears, default value of 2
     */
    private int nEars = 2;
    /**
     * Number of filter types, default value of 2
     */
    private int nFilterTypes = 2;

    /**
     * The last time when RNN was processed
     */
    private int lastBinCompleteTime;
    /**
     * Array to store the binned data, this has the data from the latest bin
     */
    private float[] binnedData = new float[this.getnChannels()];
    /**
     * Array to store the pipelined feature for every event
     */
    private float[] pipelineFeature = new float[this.getnChannels()];
    /**
     * The event count in the current bin
     */
    private int binEventCount = 0;
    /**
     * When not using continuous live recording, this array list holds the list
     * of binned data at all previous times, which is then given to an RNN
     * network
     */
    private ArrayList<float[]> binnedDataList;
    /**
     * RNN network for the filter
     */
    protected RNNetwork rnnetwork;
    /**
     * Output of the network;
     */
    private float[] networkOutput;
    /**
     * Corresponding label given the network output
     */
    private int label = -1;
    /**
     * The ear to choose the events from, 0 for left and 1 for right
     */
    private int whichEar;
    /**
     * The neuron to choose the events from, values in 0-3
     */
    private int whichNeuron;
    /**
     * The filter type to choose events from, 0 for LPF and 1 for BPF
     */
    private int whichFilter;
    /**
     * Choose which type of function you want the filter to perform, 0 stands
     * for continuous recording, 1 stands for click and record continuous RNN
     * processing, 2 stands for click and record batch RNN processing
     */
    private int whichFunction = 3;

    private boolean isFirstEventDone = false;
    private int counter = 0; // counts total number of basic events
    private int counter1 = 0; // counts total number of processed events
    private int lastEventTime;
    private int firstEventTime;
    private ArrayList<Integer> rnnProcessTimeStampList;
    private int predCounter = 0;
    private int predCorrectCounter = 0;
    private ArrayList<float[]> rnnOutputList;
    private boolean addedDisplayMethodPropertyChangeListener = false;
    private boolean screenCleared = false; // set by RollingCochleaGramDisplayMethod

    // TestNumpyData testNumpyData; //debug
    // private String lastTestDataXMLFile = "ti_train_cochlea_data_nozeroslices_20_jAER_input.xml"; //debug
    public RNNfilterExpFeatures(AEChip chip) {
        super(chip);
        String xmlnetwork = "1. XML Network", function = "2. Filter function", parameters = "3. Parameter options", display = "4. Display";
        setPropertyTooltip("loadFromXML", "Load an XML file containing the network in an appropriate format");
        setPropertyTooltip("runRNN",
                "If clickToProcess is set to true, the filter will process events when this button is kept pressed, also the network will be reset when you press the button. So press the button and hold it, speak and release the button when you want the filter to stop processing.");
        setPropertyTooltip("toggleBinning",
                "If clickToProcess is set to true, this button will toggle the boolean variable - processingEnabled - which enables the events to be processed. So when processingEnabled is false, clicking on the button will make the filter start processing the events and when processingEnabled is true, clicking the button will make the filter stop processing events.");
        setPropertyTooltip(xmlnetwork, "lastRNNXMLFile", "The XML file containing an RNN network exported from keras/somewhere else");
        setPropertyTooltip(parameters, "binTimeLength",
                "Choose the bin size (in micro seconds) for the binning of the data, choose a network appropriately");
        setPropertyTooltip(parameters, "chooseEar",
                "If processing events from only one ear, choose which ear to process them from, 0 for left and 1 for right");
        setPropertyTooltip(parameters, "chooseNeuron",
                "If events from all neurons are not to be processed, which neuron's events should be chosen, choose a value in {0,1,2,3}");
        setPropertyTooltip(parameters, "chooseFilterType",
                "If processing is to be done only on one filter type, which filter type should be chosen; 0 for low pass and 1 for band pass");
        setPropertyTooltip(function, "clickToProcess",
                "True if the filter wants to only process the events when the RunRNN filter action is kept pressed; default: false");
        setPropertyTooltip(function, "batchRNNProcess",
                "True if you want to save the sound feature and then process the RNN in one go when clickToProcess is true, false otherwise; default: false");
        setPropertyTooltip(function, "displayActivations", "Choose whether to display the softmax activations as a bar chart");
        setPropertyTooltip(function, "displayAccuracy", "Choose whether to display the softmax accuracy of the current prediction");
        setPropertyTooltip(function, "displayFeature", "Choose whether to display any recorded feature once recording is completed");
        setPropertyTooltip(function, "showPredictionOnlyIfGood", "Show the output prediction only if the prediction is good (>0.9)");
        setPropertyTooltip("processingEnabled", "The boolean variable which enables the events to be processed");
        initFilter();
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (chip.getCanvas().getDisplayMethod() instanceof RollingCochleaGramDisplayMethod) {
            // what to display on rolling cochlea gram display
            return;
        }
        // prints the name of the network being used, and if the network is processing it also prints the time for which the network was being processed
        if ((this.rnnetwork != null) & (this.rnnetwork.netname != null)) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .1f);
            MultilineAnnotationTextRenderer.setScale(.1f);
            if (this.isProcessingEnabled()) {
                int timeElapsed = this.lastEventTime - this.firstEventTime;
                timeElapsed = timeElapsed / 1000;
                MultilineAnnotationTextRenderer.renderMultilineString(
                        StringUtils.join("Network: ", this.rnnetwork.netname, "  ;  ", "Time elapsed:", Float.toString(timeElapsed), "ms"));
            } else {
                MultilineAnnotationTextRenderer.renderMultilineString(this.rnnetwork.netname);
            }
        }
        // 
        if (this.networkOutput != null) {
            float prediction_probability_percentage = RNNfilterExpFeatures.maxValue(this.networkOutput) * 100;
            prediction_probability_percentage = ((int) prediction_probability_percentage);
            prediction_probability_percentage = prediction_probability_percentage / 100;
            if (!this.isShowPredictionOnlyIfGood() | (this.isShowPredictionOnlyIfGood() & (prediction_probability_percentage > 0.9))) {
                MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * 1f);
                MultilineAnnotationTextRenderer.setScale(.1f);
                if (this.isDisplayAccuracy()) {
                    // MultilineAnnotationTextRenderer.renderMultilineString(String.join("", Integer.toString(this.label
                    // + 1),";",Float.toString(tmpValue)));
                    MultilineAnnotationTextRenderer
                            .renderMultilineString(StringUtils.join("Recognized digit ", Integer.toString(this.label), "; Probability ", Float.toString(prediction_probability_percentage)));
                } else {
                    // MultilineAnnotationTextRenderer.renderMultilineString(Integer.toString(this.label + 1));
                    MultilineAnnotationTextRenderer.renderMultilineString(Integer.toString(this.label));
                }
            }
        }
        if ((this.networkOutput != null) & this.isDisplayActivations()) {
            this.showActivations(gl, chip.getSizeX(), chip.getSizeY());
        }
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        for (BasicEvent e : in) {
            this.counter++;
            try {
                if (!this.isInitialized) {
                    initFilter();
                } // making sure the filter is initialized
                CochleaAMSEvent cochleaAMSEvent = ((CochleaAMSEvent) e);
                // int tmpByte = e.getType();
                int ear;
                if (cochleaAMSEvent.getEar() == Ear.LEFT) {
                    ear = 0;
                } else {
                    ear = 1;
                } // sets the ear variable to 0 if from left ear, 1 if right ear
                int filterType;
                if (cochleaAMSEvent.getFilterType() == FilterType.LPF) {
                    filterType = 0;
                } else {
                    filterType = 1;
                } // sets the filterType variable to 0 if from low pass, 1 if from band pass
                int neuron = cochleaAMSEvent.getThreshold(); // returns the index corresponding to the ganglion cell
                // threshold level
                int timeStamp = e.timestamp; // the timestamp of the event
                int channel = e.x; // the channel address (0-63) of the event
                if ((this.rnnetwork.initialized) & this.isProcessingEnabled()) {
                    processEvent(timeStamp, channel, ear, neuron, filterType);
                } // updates the bin data and performs additional computation as and when required
            } catch (Exception e1) {
                log.log(Level.WARNING, "In for-loop in filterPacket caught exception {0}", e1);
            }
        }
        return in;
    }

    @Override
    public void resetFilter() {
        this.resetNetwork();
        this.resetBins();
        switch (this.getWhichFunction()) {
            case 2:
                this.setProcessingEnabled(false);
                break;
            case 1:
                this.setProcessingEnabled(false);
                break;
            case 0:
                this.setProcessingEnabled(true);
                break;
            default:
                break;
        }
        this.rnnProcessTimeStampList = new ArrayList<>();
        this.rnnOutputList = new ArrayList<>();
        this.binnedDataList = new ArrayList<>();
        this.counter = 0;
        this.counter1 = 0;
        this.isFirstEventDone = false;
        this.firstEventTime = 0;
        this.lastEventTime = 0;
        this.pipelineFeature = new float[this.getnChannels()];
    }

    @Override
    public void initFilter() {
        this.rnnetwork = new RNNetwork();
        // this.testNumpyData = new TestNumpyData(); //debug
        this.resetBins();
        this.pipelineFeature = new float[this.getnChannels()];
        // if whichEar preference given by user is out of bounds, choose a random value
        if ((this.getChooseEar() > -1) & (this.getChooseEar() < this.getnEars())) {
            this.whichEar = this.getChooseEar();
        } else {
            this.whichEar = (int) (Math.random() * (this.getnEars()));
        }
        // if whichNeuron preference given by user is out of bounds, choose a random value
        if ((this.getChooseNeuron() > -1) & (this.getChooseNeuron() < this.getnNeurons())) {
            this.whichNeuron = this.getChooseNeuron();
        } else {
            this.whichNeuron = (int) (Math.random() * (this.getnNeurons()));
        }
        // if whichFilter preference given by user is out of bounds, choose a random value
        if ((this.getChooseFilterType() > -1) & (this.getChooseFilterType() < this.getnFilterTypes())) {
            this.whichFilter = this.getChooseFilterType();
        } else {
            this.whichFilter = (int) (Math.random() * (this.getnFilterTypes()));
        }
        // initializes the function of the filter from the user preferences
        if (!this.clickToProcess) {
            this.whichFunction = 0;
        } else if (this.isBatchRNNProcess()) {
            this.whichFunction = 2;
        } else {
            this.whichFunction = 1;
        }
        // sets the binning variable to either start binning right away or wait for a signal from the GUI, depending on
        // the function
        switch (this.getWhichFunction()) {
            case 2:
                this.setProcessingEnabled(false);
                break;
            case 1:
                this.setProcessingEnabled(false);
                break;
            case 0:
                this.setProcessingEnabled(true);
                break;
            default:
                break;
        }
        // this.loadTestDataFromXML(); //debug
        // this.testNumpyData.testingNetwork(); //debug
        this.rnnProcessTimeStampList = new ArrayList<>();
        this.rnnOutputList = new ArrayList<>();
        this.binnedDataList = new ArrayList<>();
        this.label = -1;
        this.isInitialized = true;
        this.isFirstEventDone = false;
        this.counter = 0;
        this.counter1 = 0;
    }

    synchronized public void loadFromXML() {
        this.setProcessingEnabled(false);
        JFileChooser c = new JFileChooser(this.getLastRNNXMLFile());
        FileFilter filter = new FileNameExtensionFilter("XML File", "xml");
        c.addChoosableFileFilter(filter);
        c.setFileFilter(filter);
        c.setSelectedFile(new File(this.getLastRNNXMLFile()));
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = c.getSelectedFile();
        try {
            this.rnnetwork.loadFromXML(f);
            this.setLastRNNXMLFile(f.toString());
            putString("lastRNNXMLFile", this.getLastRNNXMLFile());
            // this.testNumpyData.rnnetwork.loadFromXML(c.getSelectedFile()); //debug
        } catch (Exception e) {
            log.log(Level.WARNING, "Couldn't upload the xml file, please check. Caught exception {0}", e);
        }
        this.resetFilter();
    }

    /**
     * Updates the bin data and if the timeStamp is past the bin time length,
     * either the RNN network is processed or the bin is updated in the
     * arraylist of bins
     *
     * @param timeStamp - timestamp of the event
     * @param channel - which channel is the event from
     * @param ear - which ear is the event from
     * @param neuron - which neuron is the event from
     * @param filterType - which filter type is the event from
     * @throws java.io.IOException
     */
    public void processEvent(int timeStamp, int channel, int ear, int neuron, int filterType) throws IOException {
        // makes sure that the address is ok
        if (!isAddressOk(channel, ear, neuron, filterType)) {
            return;
        }
        this.counter1++;
        if (!this.isFirstEventDone) {
            this.ifFirstEventNotDone(timeStamp, channel);
        }
        if (timeStamp < this.lastBinCompleteTime) {
            switch (this.getWhichFunction()) {
                case 1:
                    log.log(Level.SEVERE, "The present timestamp is less than a previous timestamp, this isn't supposed to happen");
                    this.resetFilter();
                    break;
                case 0:
                    log.log(Level.SEVERE, "The present timestamp is less than a previous timestamp, this isn't supposed to happen");
                    this.resetFilter();
                    this.ifFirstEventNotDone(timeStamp, channel);
                    this.updateBin(channel);
                    break;
                case 2:
                    log.log(Level.SEVERE, "The present timestamp is less than a previous timestamp, this isn't supposed to happen");
                    this.resetFilter();
                    break;
                default:
                    break;
            }
            return;
        } else {
            int timeElapsed = timeStamp - this.lastEventTime;
            this.updatePipelineFeature(timeElapsed, channel);
            this.lastEventTime = timeStamp;
        }
        // if the present spike's timestamp is within a binTimeLength of the last binned data, we update the bin
        if ((timeStamp >= this.lastBinCompleteTime) & (timeStamp < (this.lastBinCompleteTime + this.getBinTimeLength()))) {
            this.updateBin(channel);
        } else if (timeStamp > (this.lastBinCompleteTime + this.getBinTimeLength())) {
            // if the timestamp is more than a binTimeLength, then reset the bins to zero
            switch (this.getWhichFunction()) {
                case 1:
                    this.updateBinnedDataListNoReset(timeStamp);
                    this.processRNN(timeStamp);
                    break;
                case 0:
                    this.processRNN(timeStamp);
                    break;
                case 2:
                    this.updateBinnedDataList(timeStamp);
                    break;
                default:
                    break;
            }
            this.updateBin(channel);
        }
    }

    /**
     * Increase the updateBin counter at the given channel
     *
     * @param channel
     */
    public void updateBin(int channel) {
        this.binEventCount += 1;
        for (int i = 0; i < this.binnedData.length; i++) {
            this.binnedData[i] += this.pipelineFeature[i];
        }
    }

    /**
     * Updates the binned data list, given the latest time stamp it even adds
     * the necessary zeros to the network
     *
     * @param timeStamp
     */
    public void updateBinnedDataList(int timeStamp) {
        float[] tmpArr = Arrays.copyOf(this.binnedData, this.getnChannels());
        for (int i = 0; i < tmpArr.length; i++) {
            tmpArr[i] /= this.binEventCount;
        }
        this.binnedDataList.add(tmpArr);
        this.lastBinCompleteTime += this.getBinTimeLength();
        this.resetBins();
        while (timeStamp > (this.lastBinCompleteTime + this.getBinTimeLength())) {
            tmpArr = new float[this.getnChannels()];
            this.binnedDataList.add(tmpArr);
            this.lastBinCompleteTime += this.getBinTimeLength();
        }
    }

    /**
     * Updates the binned data list, given the latest time stamp it even adds
     * the necessary zeros to the network, but does not reset the current bin
     * data
     *
     * @param timeStamp
     */
    public void updateBinnedDataListNoReset(int timeStamp) {
        float[] tmpArr = Arrays.copyOf(this.binnedData, this.getnChannels());
        for (int i = 0; i < tmpArr.length; i++) {
            tmpArr[i] /= this.binEventCount;
        }
        this.binnedDataList.add(tmpArr);
        int tmpRNNTimeStamp = this.lastBinCompleteTime;
        tmpRNNTimeStamp += this.getBinTimeLength();
        while (timeStamp > (tmpRNNTimeStamp + this.getBinTimeLength())) {
            tmpArr = new float[this.getnChannels()];
            this.binnedDataList.add(tmpArr);
            tmpRNNTimeStamp += this.getBinTimeLength();
        }
    }

    /**
     * Removes the zero slices from the beginning and the ending and gives the
     * resulting binned array list of arrays; currently not used
     *
     * @return
     */
    public ArrayList<float[]> processDataListSingleDigit() { // removes zero slices from the beginning and the ending
        if (this.binnedDataList.isEmpty()) {
            return this.binnedDataList;
        }
        boolean removeStart = true;
        ArrayList<float[]> tmpBinnedDataList = new ArrayList<>();
        for (float[] currentList : this.binnedDataList) {
            float sum = RNNfilterExpFeatures.sumOfArray(currentList);
            if ((removeStart == true) & (sum != 0)) {
                removeStart = false;
            }
            if (!removeStart) {
                tmpBinnedDataList.add(currentList);
            }
        }
        // tmpBinnedDataList doesn't have zero frames at the beginning
        for (int i = tmpBinnedDataList.size() - 1; i > -1; i--) {
            float sum = RNNfilterExpFeatures.sumOfArray(tmpBinnedDataList.get(i));
            if (sum == 0) {
                tmpBinnedDataList.remove(i);
            } else {
                break;
            }
        }
        return tmpBinnedDataList;
    }

    public void resetBinnedDataList() {
        this.binnedDataList = new ArrayList<>();
    }

    /**
     * Process the RNN in a batch manner
     */
    public void processRNNList() {
        if (this.binnedDataList.isEmpty()) {
            return;
        }
        FloatMatrix tempOutput;
        tempOutput = FloatMatrix.zeros(this.getnChannels());
        for (float[] currentBinnedData : this.binnedDataList) {
            tempOutput = this.rnnetwork.output(currentBinnedData);
        }
        this.networkOutput = RNNfilterExpFeatures.DMToFloat(tempOutput);
        this.label = RNNfilterExpFeatures.indexOfMaxValue(this.networkOutput);
    }

    /**
     * Returns true if the channel, ear and neuron attributes of the event are
     * ok
     *
     * @param channel1 - frequency channel of the input event
     * @param ear1 - the ear the input event is from
     * @param neuron1 - the neuron the input event is from
     * @param filterType1 - the filterType the input event is from
     * @return - true if all the inputs are not abnormal
     */
    public boolean isAddressOk(int channel1, int ear1, int neuron1, int filterType1) {
        if ((channel1 < 0) | (channel1 > this.getnChannels())) {
            return false;
        } // making sure the channel value is not out of bounds
        if ((ear1 < 0) | (ear1 > this.getnEars())) {
            return false;
        } // making sure the ear value is out of bounds
        if ((neuron1 < 0) | (neuron1 > this.getnNeurons())) {
            return false;
        } // making sure the neuron value is not out of bounds
        if ((filterType1 < 0) | (filterType1 > this.getnFilterTypes())) {
            return false;
        } // making sure the filterType value is not out of bounds
        if (ear1 != this.whichEar) { // if both ears are not to be used and the ear value is not what is asked for,
            // return false
            return false;
        }
        if (neuron1 != this.whichNeuron) { // if all neurons are not to be used and the neuron value is not what is
            // asked for, return false
            return false;
        }
        if (filterType1 != this.whichFilter) { // if all filters are not to be used and the filter value is not what
            // was asked for, return false
            return false;
        }
        return true;
    }
    
    /**
     * Update the pipeline feature
     * @param timeElapsed
     * @param channel
     */
    public void updatePipelineFeature(int timeElapsed, int channel) {
        float tmpValue = (float) Math.exp(-1 * ((double) (timeElapsed)) / this. tau);
        for (int i = 0; i < this.pipelineFeature.length; i++) {
            this.pipelineFeature[i] *= tmpValue;
        }
        this.pipelineFeature[channel] = 1;
    }

    /**
     * Processes the RNN network for continuous recording setting
     *
     * @param timeStamp - the timestamp of the present event
     */
    public void processRNN(int timeStamp) {
        for (int i = 0; i < this.binnedData.length; i++) {
            this.binnedData[i] /= this.binEventCount;
        }
        FloatMatrix tempOutput = this.rnnetwork.output(this.binnedData);
        this.networkOutput = RNNfilterExpFeatures.DMToFloat(tempOutput);
        if (chip.getCanvas().getDisplayMethod() instanceof RollingCochleaGramDisplayMethod) {
            if (!addedDisplayMethodPropertyChangeListener) {
                chip.getCanvas().getDisplayMethod().getSupport().addPropertyChangeListener(this);
                addedDisplayMethodPropertyChangeListener = true;
            }
            // save outputs for rendering
            if (screenCleared) {
                // reset memory
            }
            // save results

        }
        this.label = RNNfilterExpFeatures.indexOfMaxValue(this.networkOutput);
        this.lastBinCompleteTime += this.getBinTimeLength();
        this.resetBins();
        // if the present timeStamp is very far from the last time RNN was processed, that means an appropriate number
        // of zero bins have to be sent to the network
        while (timeStamp > (this.lastBinCompleteTime + this.getBinTimeLength())) {
            tempOutput = this.rnnetwork.output(this.binnedData);
            this.networkOutput = RNNfilterExpFeatures.DMToFloat(tempOutput);
            this.rnnOutputList.add(this.networkOutput);
            this.label = RNNfilterExpFeatures.indexOfMaxValue(this.networkOutput);
            this.lastBinCompleteTime += this.getBinTimeLength();
        }
    }

    /**
     * Resets the binnedData to zero
     */
    public void resetBins() {
        Arrays.fill(this.binnedData, 0); // initialized the bin data array to zero
        this.binEventCount = 0;
    }

    /**
     * Resets the internal states in the network
     */
    public void resetNetwork() {
        this.rnnetwork.resetNetworkLayers();
    }

    /**
     * Copies int array to a float array
     *
     * @param intArray - 1 dimensional int array
     * @return floatArray - 1 dimensional float array
     */
    public static float[] intToFloat(int[] intArray) {
        float[] floatArray = new float[intArray.length];
        for (int i = 0; i < intArray.length; i++) {
            floatArray[i] = intArray[i];
        }
        return floatArray;
    }

    /**
     * Copies a 1 dimensional FloatMatrix (jblas) into a 1 dimensional float
     * array
     *
     * @param floatMatrix - 1 dimensional float matrix, there is no check make
     * sure the input is indeed 1 dimensional
     * @return 1 dimensional float array
     */
    public static float[] DMToFloat(FloatMatrix floatMatrix) {
        int length = floatMatrix.length;
        float[] floatArray = new float[length];
        for (int i = 0; i < length; i++) {
            floatArray[i] = floatMatrix.get(i);
        }
        return floatArray;
    }

    /**
     * Returns the index of the maximum value in the array
     *
     * @param input
     * @return
     */
    public static int indexOfMaxValue(float[] input) {
        int index = 0;
        float tmpMax = input[0];
        for (int i = 1; i < input.length; i++) {
            if (input[i] > tmpMax) {
                tmpMax = input[i];
                index = i;
            }
        }
        return index;
    }

    /**
     * Returns the maximum value in the array
     *
     * @param input
     * @return
     */
    public static float maxValue(float[] input) {
        float tmpMax = input[0];
        for (int i = 1; i < input.length; i++) {
            if (input[i] > tmpMax) {
                tmpMax = input[i];
            }
        }
        return tmpMax;
    }

    /**
     * Returns the maximum value in a 2D array
     *
     * @param input
     * @return
     */
    public static float maxValueIn2DArray(float[][] input) {
        float output = 0;
        int xLength = input.length;
        int yLength = input[0].length;
        for (int x = 0; x < xLength; x++) {
            for (int y = 0; y < yLength; y++) {
                if (output < input[x][y]) {
                    output = input[x][y];
                }
            }
        }
        return output;
    }

    /**
     * Calculates the sum of elements in an integer array
     *
     * @param input an integer array
     * @return sum of elements in input integer array
     */
    public static float sumOfArray(float[] input) {
        float sum = 0;
        for (float i : input) {
            sum += i;
        }
        return sum;
    }

    /**
     * Returns an array with the sum of each array in an arraylist of arrays
     *
     * @param input
     * @return sum of array elements in arraylist
     */
    public static float[] sumOfArrayList(ArrayList<float[]> input) {
        int size = input.size();
        float[] output = new float[size];
        for (int i = 0; i < size; i++) {
            output[i] = RNNfilterExpFeatures.sumOfArray(input.get(i));
        }
        return output;
    }

    /**
     * Converts the arraylist holding the recorded features and prints it as an
     * image in a new JFrame
     *
     * @param input
     * @throws IOException
     */
    public void printDigit(ArrayList<float[]> input) throws IOException {
        if (input.isEmpty()) {
            return;
        }
        int xLength = input.size();
        int yLength = input.get(0).length;
        float maxValue;
        float[][] tmpImage = new float[xLength][yLength];
        for (int i = 0; i < xLength; i++) {
            tmpImage[i] = input.get(i);
        }
        maxValue = RNNfilterExpFeatures.maxValueIn2DArray(tmpImage);
        float[][] tmpImageScaled = RNNfilterExpFeatures.rescale2DArray(tmpImage, 255 / maxValue);
        BufferedImage newImage = new BufferedImage(xLength, yLength, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < xLength; x++) {
            for (int y = 0; y < yLength; y++) {
                newImage.setRGB(x, y, (int) tmpImageScaled[x][y]);
            }
        }
        ImageIcon icon = new ImageIcon(newImage);
        ScaledImagePanel scaledImage = new ScaledImagePanel(icon);
        JFrame frame = new JFrame();
        frame.setSize(xLength, yLength);
        frame.add(scaledImage);
        frame.setVisible(true);

    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        if (pce.getPropertyName() == null ? RollingCochleaGramDisplayMethod.EVENT_SCREEN_CLEARED == null : pce.getPropertyName().equals(RollingCochleaGramDisplayMethod.EVENT_SCREEN_CLEARED)) {
            screenCleared = true;
        }
    }

    /**
     * A class which helps scale the image to fit the size of the JFrame
     */
    public class ScaledImagePanel extends JPanel {

        ImageIcon image;

        public ScaledImagePanel() {
        }

        public ScaledImagePanel(ImageIcon image1) {
            this.image = image1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            BufferedImage scaledImage = getScaledImage();
            super.paintComponent(g);
            g.drawImage(scaledImage, 0, 0, null);
        }

        private BufferedImage getScaledImage() {
            BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            g2d.addRenderingHints(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY));
            g2d.drawImage(this.image.getImage(), 0, 0, getWidth(), getHeight(), null);
            return image;
        }
    }

    /**
     * Rescales the values in an float 2D array to an approximated float 2D
     * array
     *
     * @param input
     * @param scale
     * @return
     */
    public static float[][] rescale2DArray(float[][] input, float scale) {
        int xLength = input.length;
        int yLength = input[0].length;
        float[][] output = new float[xLength][yLength];
        for (int x = 0; x < xLength; x++) {
            for (int y = 0; y < yLength; y++) {
                output[x][y] = scale * input[x][y];
            }
        }
        return output;
    }

    /**
     * Annotates the frame with the activations of the softmax layer as a bar
     * chart
     *
     * @param gl
     * @param width
     * @param height
     */
    public void showActivations(GL2 gl, int width, int height) {
        if (this.networkOutput == null) {
        } else {
            float dx = (float) (width) / this.networkOutput.length;
            float dy = 0.8f * (height);

            gl.glBegin(GL.GL_LINE_STRIP);
            for (int i = 0; i < this.networkOutput.length; i++) {
                float tmpOutput = this.networkOutput[i];
                float tmpOutputMax = RNNfilterExpFeatures.maxValue(this.networkOutput);
                float y_end = 1 + ((dy * tmpOutput) / tmpOutputMax); // draws the relative activations of the neurons in
                // the layer
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
     * @return the nChannels
     */
    public int getnChannels() {
        return nChannels;
    }

    /**
     * @return the nNeurons
     */
    public int getnNeurons() {
        return nNeurons;
    }

    /**
     * @return the nEars
     */
    public int getnEars() {
        return nEars;
    }

    /**
     * @return the binTimeLength
     */
    public int getBinTimeLength() {
        return binTimeLength;
    }

    /**
     * @param binTimeLength1 the binTimeLength to set
     */
    public void setBinTimeLength(int binTimeLength1) {
        getPrefs().putInt("binTimeLength", binTimeLength1);
        getSupport().firePropertyChange("binTimeLength", this.binTimeLength, binTimeLength1);
        this.binTimeLength = binTimeLength1;
    }

    public int getTau() {
        return tau;
    }
    
    public void setTau(int tau1) {
        getPrefs().putInt("tau", tau1);
        getSupport().firePropertyChange("tau", this.tau, tau1);
        this.tau = tau1;
    }
    
    /**
     * @return the chooseEar
     */
    public int getChooseEar() {
        return chooseEar;
    }

    /**
     * @param chooseEar1 the chooseEar to set
     */
    public void setChooseEar(int chooseEar1) {
        getPrefs().putInt("chooseEar", chooseEar1);
        getSupport().firePropertyChange("chooseEar", this.chooseEar, chooseEar1);
        this.chooseEar = chooseEar1;
        if ((this.getChooseEar() > -1) & (this.getChooseEar() < this.getnEars())) {
            this.whichEar = this.getChooseEar();
        } else {
            this.whichEar = (int) (Math.random() * (this.getnEars()));
        }
    }

    /**
     * @return the chooseNeuron
     */
    public int getChooseNeuron() {
        return chooseNeuron;
    }

    /**
     * @param chooseNeuron1 the chooseNeuron to set
     */
    public void setChooseNeuron(int chooseNeuron1) {
        getPrefs().putInt("chooseNeuron", chooseNeuron1);
        getSupport().firePropertyChange("chooseNeuron", this.chooseNeuron, chooseNeuron1);
        this.chooseNeuron = chooseNeuron1;
        if ((this.getChooseNeuron() > -1) & (this.getChooseNeuron() < this.getnNeurons())) {
            this.whichNeuron = this.getChooseNeuron();
        } else {
            this.whichNeuron = (int) (Math.random() * (this.getnNeurons()));
        }
    }

    /**
     * @return the lastRNNXMLFile
     */
    public String getLastRNNXMLFile() {
        return lastRNNXMLFile;
    }

    /**
     * @param lastRNNXMLFile1 the lastRNNXMLFile to set
     */
    public void setLastRNNXMLFile(String lastRNNXMLFile1) {
        putString("lastRNNXMLFile", lastRNNXMLFile1);
        this.lastRNNXMLFile = lastRNNXMLFile1;
    }

    /**
     * @return the binning
     */
    public boolean isProcessingEnabled() {
        return processingEnabled;
    }

    /**
     * @param binning1 the binning to set
     */
    public void setProcessingEnabled(boolean binning1) {
        putBoolean("processingEnabled", binning1);
        boolean oldBinning = this.processingEnabled;
        this.processingEnabled = binning1;
        support.firePropertyChange("processingEnabled", oldBinning, binning1);
    }

    /**
     * @return the chooseFilterType
     */
    public int getChooseFilterType() {
        return chooseFilterType;
    }

    /**
     * @param chooseFilterType1 the chooseFilterType to set
     */
    public void setChooseFilterType(int chooseFilterType1) {
        getPrefs().putInt("chooseFilterType", chooseFilterType1);
        getSupport().firePropertyChange("chooseFilterType", this.chooseFilterType, chooseFilterType1);
        this.chooseFilterType = chooseFilterType1;
        if ((this.getChooseFilterType() > -1) & (this.getChooseFilterType() < this.getnFilterTypes())) {
            this.whichFilter = this.getChooseFilterType();
        } else {
            this.whichFilter = (int) (Math.random() * (this.getnFilterTypes()));
        }

    }

    /**
     * @return the nFilterTypes
     */
    public int getnFilterTypes() {
        return nFilterTypes;
    }

    /**
     * @return the whichFunction
     */
    public int getWhichFunction() {
        return whichFunction;
    }

    public void ifFirstEventNotDone(int timeStamp, int channel) {
        this.firstEventTime = timeStamp;
        this.isFirstEventDone = true;
        this.lastBinCompleteTime = timeStamp;
        this.lastEventTime = timeStamp;
    }

    /**
     * @return the displayActivations
     */
    public boolean isDisplayActivations() {
        return displayActivations;
    }

    /**
     * @param displayActivations the displayActivations to set
     */
    public void setDisplayActivations(boolean displayActivations) {
        putBoolean("displayActivations", displayActivations);
        this.displayActivations = displayActivations;
    }

    /**
     * @return the displayAccuracy
     */
    public boolean isDisplayAccuracy() {
        return displayAccuracy;
    }

    /**
     * @param displayAccuracy the displayAccuracy to set
     */
    public void setDisplayAccuracy(boolean displayAccuracy) {
        putBoolean("displayAccuracy", displayAccuracy);
        this.displayAccuracy = displayAccuracy;
    }

    /**
     * @return the displayFeature
     */
    public boolean isDisplayFeature() {
        return displayFeature;
    }

    /**
     * @param displayFeature the displayFeature to set
     */
    public void setDisplayFeature(boolean displayFeature) {
        putBoolean("displayFeature", displayFeature);
        this.displayFeature = displayFeature;
    }

    /**
     * @return the showPredictionOnlyIfGood
     */
    public boolean isShowPredictionOnlyIfGood() {
        return showPredictionOnlyIfGood;
    }

    /**
     * @param showPredictionOnlyIfGood the showPredictionOnlyIfGood to set
     */
    public void setShowPredictionOnlyIfGood(boolean showPredictionOnlyIfGood) {
        putBoolean("showPredictionOnlyIfGood", showPredictionOnlyIfGood);
        this.showPredictionOnlyIfGood = showPredictionOnlyIfGood;
    }

    /**
     * @return the singleDigits
     */
    public boolean isClickToProcess() {
        return clickToProcess;
    }

    /**
     * @param clickToProcess the singleDigits to set
     */
    public void setClickToProcess(boolean clickToProcess) {
        putBoolean("clickToProcess", clickToProcess);
        this.clickToProcess = clickToProcess;
        if (!clickToProcess) {
            this.whichFunction = 0;
        } else if (this.isBatchRNNProcess()) {
            this.whichFunction = 2;
        } else {
            this.whichFunction = 1;
        }
        this.resetFilter();
    }

    /**
     * @return the batchRNNProcess
     */
    public boolean isBatchRNNProcess() {
        return batchRNNProcess;
    }

    /**
     * @param batchRNNProcess the batchRNNProcess to set
     */
    public void setBatchRNNProcess(boolean batchRNNProcess) {
        putBoolean("batchRNNProcess", batchRNNProcess);
        this.batchRNNProcess = batchRNNProcess;
        if (this.clickToProcess) {
            if (batchRNNProcess) {
                this.whichFunction = 2;
            } else {
                this.whichFunction = 1;
            }
        }
        this.resetFilter();
    }

    /**
     * Implements a toggleBinning function which would be used to build the
     * button with the same name
     *
     * @throws IOException
     */
    synchronized public void toggleBinning() throws IOException {
        if ((this.getWhichFunction() == 2) & (this.isProcessingEnabled() == false)) {
            this.resetNetwork();
            this.setProcessingEnabled(true);
        } else if ((this.getWhichFunction() == 2) & (this.isProcessingEnabled() == true)) { // if the filter was binning
            // the events before, then
            // stop binning and process
            // the digit recorded
            this.setProcessingEnabled(false);
            if (this.displayFeature) {
                this.printDigit(binnedDataList);
            }
            this.processRNNList();
            this.resetBins();
            this.rnnProcessTimeStampList = new ArrayList<>();
            this.rnnOutputList = new ArrayList<>();
            this.binnedDataList = new ArrayList<>();
            this.counter = 0;
            this.counter1 = 0;
            this.isFirstEventDone = false;
        }
        if ((this.getWhichFunction() == 1) & (this.isProcessingEnabled() == false)) {
            this.resetNetwork();
            this.setProcessingEnabled(true);
        } else if ((this.getWhichFunction() == 1) & (this.isProcessingEnabled() == true)) {
            this.setProcessingEnabled(false);
            if (this.displayFeature) {
                this.printDigit(binnedDataList);
            }
            this.resetBins();
            this.rnnProcessTimeStampList = new ArrayList<>();
            this.rnnOutputList = new ArrayList<>();
            this.binnedDataList = new ArrayList<>();
            this.counter = 0;
            this.counter1 = 0;
            this.isFirstEventDone = false;
        }
    }

    /**
     * Implements the toggleBinning button, runs the toggleBinning function
     *
     * @throws IOException
     */
    public void doToggleBinning() throws IOException {
        this.toggleBinning();
    }

    /**
     * Implements the function which would run when the RunRNN button is
     * pressed; as suggested by Prof. Tobi
     */
    public void doPressRunRNN() {
        log.info("RunRNN button pressed");
        if ((this.getWhichFunction() == 1) | (this.getWhichFunction() == 2)) {
            this.resetNetwork();
            this.setProcessingEnabled(true);
        }
    }

    /**
     * Implements the function which would run when the RunRNN button is
     * released; as suggested by Prof. Tobi
     *
     * @throws IOException
     */
    public void doReleaseRunRNN() throws IOException {
        log.info("RunRNN button released");
        if ((this.getWhichFunction() == 1) | (this.getWhichFunction() == 2)) {
            this.setProcessingEnabled(false);
            if (this.displayFeature) {
                this.printDigit(binnedDataList);
            }
            if (this.getWhichFunction() == 2) {
                this.processRNNList();
            }
            this.resetBins();
            this.rnnProcessTimeStampList = new ArrayList<>();
            this.rnnOutputList = new ArrayList<>();
            this.binnedDataList = new ArrayList<>();
            this.counter = 0;
            this.counter1 = 0;
            this.isFirstEventDone = false;
        }
    }

    /**
     * Implements the loadFromXML function as a button
     */
    public void doLoadFromXML() {
        this.loadFromXML();
    }

    public void doPredCorrect() {
        this.predCounter += 1;
        this.predCorrectCounter += 1;
    }

    public void doPredWrong() {
        this.predCounter += 1;
    }

    public void doPredAccuracy() {
        Float accuracy = (float) 0;
        if (this.predCounter > 0) {
            accuracy = (float) this.predCorrectCounter / this.predCounter;
        }
        String toDisplay = StringUtils.join("Accuracy is ", Float.toString(accuracy), " on ", Integer.toString(this.predCounter), " total predictions");
        JOptionPane.showMessageDialog(null, toDisplay);
    }

    public void doPredReset() {
        this.predCounter = 0;
        this.predCorrectCounter = 0;
    }

}
