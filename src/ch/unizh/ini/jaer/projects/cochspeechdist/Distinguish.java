

/**Distinguish between different speakers
 *
 * @author ssommer
 */

package ch.unizh.ini.jaer.projects.cochspeechdist;

import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent;
import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent.Ear;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Arrays;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.*;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import javax.swing.*;

import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;
import java.io.*;
import java.util.concurrent.ArrayBlockingQueue;
import javax.media.opengl.glu.GLU;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

import java.util.LinkedList;
import java.util.Queue;


/**
 * Creates Mapping between auditive and visual Events
 *
 * @author Stefan Sommer
 */
public class Distinguish extends EventFilter2D implements Observer, FrameAnnotater {

    public static String getDescription() {
        return "Distinguish between different speakers in a scene";
    }
//    private ITDCalibrationGaussians calibration = null;
/*    private float averagingDecay = getPrefs().getFloat("ITDFilter.averagingDecay", 1);
    private int maxITD = getPrefs().getInt("ITDFilter.maxITD", 800);
    private int numOfBins = getPrefs().getInt("ITDFilter.numOfBins", 16);
    private int maxWeight = getPrefs().getInt("ITDFilter.maxWeight", 5);
    private int dimLastTs = getPrefs().getInt("ITDFilter.dimLastTs", 4);
    private int maxWeightTime = getPrefs().getInt("ITDFilter.maxWeightTime", 500000);
    private boolean display = getPrefs().getBoolean("ITDFilter.display", false);
    private boolean displayNormalize = getPrefs().getBoolean("ITDFilter.displayNormalize", true);
    private boolean displaySoundDetected = getPrefs().getBoolean("ITDFilter.displaySoundDetected", true);
    private boolean useLaterSpikeForWeight = getPrefs().getBoolean("ITDFilter.useLaterSpikeForWeight", true);
    private boolean usePriorSpikeForWeight = getPrefs().getBoolean("ITDFilter.usePriorSpikeForWeight", true);
    private boolean computeMeanInLoop = getPrefs().getBoolean("ITDFilter.computeMeanInLoop", true);
    private boolean writeAvgITD2File = getPrefs().getBoolean("ITDFilter.writeAvgITD2File", false);
    private boolean writeITD2File = getPrefs().getBoolean("ITDFilter.writeITD2File", false);
    private boolean sendITDsToOtherThread = getPrefs().getBoolean("ITDFilter.sendITDsToOtherThread", false);
    private int itdEventQueueSize = getPrefs().getInt("ITDFilter.itdEventQueueSize", 1000);
    private int timeLocalExtremaDetection = getPrefs().getInt("ITDFilter.timeLocalExtremaDetection", 200000);
    private boolean writeBin2File = getPrefs().getBoolean("ITDFilter.writeBin2File", false);
    private boolean saveFrequenciesSeperately = getPrefs().getBoolean("ITDFilter.saveFrequenciesSeperately", false);
    private boolean invert = getPrefs().getBoolean("ITDFilter.invert", false);
    private boolean write2FileForEverySpike = getPrefs().getBoolean("ITDFilter.write2FileForEverySpike", false);
    private boolean weightFrequencies = getPrefs().getBoolean("ITDFilter.weightFrequencies", false);
    private boolean useRidgeRegression = getPrefs().getBoolean("ITDFilter.useRidgeRegression", false);
    private boolean use1DRegression = getPrefs().getBoolean("ITDFilter.use1DRegression", false);
    private boolean normToConfThresh = getPrefs().getBoolean("ITDFilter.normToConfThresh", false);
    private boolean showAnnotations = getPrefs().getBoolean("ITDFilter.showAnnotations", false);
    private int confidenceThreshold = getPrefs().getInt("ITDFilter.confidenceThreshold", 30);
    private int activityThreshold = getPrefs().getInt("ITDFilter.activityThreshold", 30);
    private int numLoopMean = getPrefs().getInt("ITDFilter.numLoopMean", 2);
    private int numOfCochleaChannels = getPrefs().getInt("ITDFilter.numOfCochleaChannels", 32);
    private boolean useCalibration = getPrefs().getBoolean("ITDFilter.useCalibration", false);
    private String calibrationFilePath = getPrefs().get("ITDFilter.calibrationFilePath", null);
    private double[] frequencyWeights;
    private double[] ridgeWeights;
    private boolean connectToPanTiltThread = false;
*/
    private int[][][][] lastTs;
    private int[][][] lastTsCursor;

    private double [][][] visualArray = new double[128][128][2];
    private double [][][] audioArray = new double[128][128][2];

    private double [][] coherenceMap = new double[128][128];

    private Queue CoherenceQueue = new LinkedList();

    private int LastQueueTimestamp = 0;
    private int CoherenceWindow = getPrefs().getInt("Coherent Timing Window", 1000);

    private float ClusterThresh = getPrefs().getFloat("Clustering Threshold", 0.3f);

    private double a_decay = 0.9;
    private float v_decay = getPrefs().getFloat("Retina Decaying Factor", 0.9f);
    private double c_decay = 0.95;
    
    private double prev_timestamp = 0;
    private int LastAudioSpike = 0;
    
    Iterator iterator;
    private float lastWeight = 1f;
    private int avgITD;
    private float avgITDConfidence = 0;
    private float ILD;
    EngineeringFormat fmt = new EngineeringFormat();
    FileWriter fstream;
    FileWriter fstreamBins;
    FileWriter freqfstreamBins;
    BufferedWriter ITDFile;
    BufferedWriter AvgITDFile;
    BufferedWriter BinFile;
    BufferedWriter freqBinFile;
    private boolean wasMoving = false;
    private int numNeuronTypes = 1;
    private static ArrayBlockingQueue ITDEventQueue = null;
    private boolean ITDEventQueueFull = false;

    private double ConfidenceRecentMax = 0;
    private int ConfidenceRecentMaxTime = 0;
    private double ConfidenceRecentMin = 1e6;
    private int ConfidenceRecentMinTime = 0;
    private boolean ConfidenceRising = true;

    GLU glu;

    private void init_arrays(){
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                visualArray[i][j][0]=0;
                audioArray[i][j][0]=0;
            }
        }
    }
/*
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s");
        if (tok.length < 2) {
            return "not enough arguments\n";
        }

        try {
            if (tok[1].equals("saveitd")) {
                if (tok.length < 3) {
                    return "not enough arguments\n";
                } else {
                    startAvgITD2File(tok[2]);
                    return "starting to save itds\n";
                }
            }
            if (tok[1].equals("stopsaveitd")) {
                setWriteAvgITD2File(false);
                return "stop saving itds\n";
            }
            if (tok[1].equals("savefreqbins")) {
                if (tok.length < 3) {
                    return "not enough arguments\n";
                } else {
                    startSaveFreq(tok[2]);
                    return "starting to save freqbins\n";
                }
            }
            if (tok[1].equals("stopsavefreqbins")) {
                setSaveFrequenciesSeperately(false);
                return "stop saving freqbins\n";
            }
            if (tok[1].equals("savebin")) {
                if (tok.length < 3) {
                    return "not enough arguments\n";
                } else {
                    startWriteBin2File(tok[2]);
                    return "starting to save bins\n";
                }
            }
            if (tok[1].equals("stopsavebin")) {
                setWriteBin2File(false);
                return "stop saving bins\n";
            }

            if (tok[1].equals("savebinnow")) {
                if (tok.length < 3) {
                    return "not enough arguments\n";
                } else {
                    writeBin2FileNow(tok[2]);
                    return "writing bins now\n";
                }
            }
            log.info("Received Command:" + input);
        } catch (IOException e) {
            return "IOExeption in remotecontrol\n";
        }
        return "not a valid command.";
    }

 */
/*
    private void startAvgITD2File(String path) throws IOException {
        // Create file
        fstream = new FileWriter(path);
        AvgITDFile = new BufferedWriter(fstream);
        AvgITDFile.write("time\tITD\tconf\n");
        getPrefs().putBoolean("ITDFilter.writeAvgITD2File", true);
        support.firePropertyChange("writeAvgITD2File", this.writeAvgITD2File, true);
        this.writeAvgITD2File = true;
    }
*/
/*    private void startSaveFreq(String path) throws IOException {

        // Create file
        freqfstreamBins = new FileWriter(path);
        freqBinFile = new BufferedWriter(freqfstreamBins);
        String titles = "time\tfreq\t";
        for (int i = 0; i < this.numOfBins; i++) {
            titles += "Bin" + i + "\t";
        }
        titles += "\n";
        freqBinFile.write(titles);
        getPrefs().putBoolean("ITDFilter.saveFrequenciesSeperately", true);
        support.firePropertyChange("saveFrequenciesSeperately", this.saveFrequenciesSeperately, true);
        this.saveFrequenciesSeperately = true;
    }
*/
/*
    private void startWriteBin2File(String path) throws IOException {
        // Create file
        fstreamBins = new FileWriter(path);
        BinFile = new BufferedWriter(fstreamBins);
        String titles = "time\t";
        for (int i = 0; i < this.numOfBins; i++) {
            titles += "Bin" + i + "\t";
        }
        BinFile.write(titles);
    }
*/
/*
    private void writeBin2FileNow(String path) throws IOException {
        // Create file
        fstreamBins = new FileWriter(path);
        BinFile = new BufferedWriter(fstreamBins);

        if (this.isSaveFrequenciesSeperately()) {
            String titles = "time\tfreq\t";
            for (int i = 0; i < this.numOfBins; i++) {
                titles += "Bin" + i + "\t";
            }
            titles += "\n";
            BinFile.write(titles);
            for (int i = 0; i < 64; i++) {
                if (freqBinFile != null) {
                   }
                }
            }

    }
*/
/*
    public enum EstimationMethod {

        useMedian, useMean, useMax
    };
    private EstimationMethod estimationMethod = EstimationMethod.valueOf(getPrefs().get("ITDFilter.estimationMethod", "useMax"));

    public enum AMSprocessingMethod {

        NeuronsIndividually, AllNeuronsTogether, StoreSeparetlyCompareEvery
    };
    private AMSprocessingMethod amsProcessingMethod = AMSprocessingMethod.valueOf(getPrefs().get("ITDFilter.amsProcessingMethod", "NeuronsIndividually"));
    private CochleaAMSEvent.FilterType useGanglionCellType = CochleaAMSEvent.FilterType.valueOf(getPrefs().get("ITDFilter.useGanglionCellType", "LPF"));
    private boolean hasMultipleGanglionCellTypes = false;

    private float averagingDecayTmp = 0;
*/
    public Distinguish(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
/*
        setPropertyTooltip("averagingDecay", "The decay constant of the fade out of old ITDs (in sec). Set to 0 to disable fade out.");
        setPropertyTooltip("maxITD", "maximum ITD to compute in us");
        setPropertyTooltip("numOfBins", "total number of bins");
        setPropertyTooltip("dimLastTs", "how many lastTs save");
        setPropertyTooltip("maxWeight", "maximum weight for ITDs");
        setPropertyTooltip("maxWeightTime", "maximum time to use for weighting ITDs");
        setPropertyTooltip("display", "display bins");
        setPropertyTooltip("useLaterSpikeForWeight", "use the side of the later arriving spike to weight the ITD");
        setPropertyTooltip("usePriorSpikeForWeight", "use the side of the prior arriving spike to weight the ITD");
        setPropertyTooltip("computeMeanInLoop", "use a loop to compute the mean or median to avoid biasing");
        setPropertyTooltip("useCalibration", "use xml calibration file");
        setPropertyTooltip("confidenceThreshold", "ITDs with confidence below this threshold are neglected");
        setPropertyTooltip("activityThreshold", "the bin maximum has to be above this threshold to trigger sound detection");
        setPropertyTooltip("writeAvgITD2File", "Write the average ITD-values to a File");
        setPropertyTooltip("writeITD2File", "Write the ITD-values to a File");
        setPropertyTooltip("writeBin2File", "Write the Bin-values to a File");
        setPropertyTooltip("write2FileForEverySpike", "Write the values to file after every spike or after every packet");
        setPropertyTooltip("weightFrequencies", "Read weights for the frequencies from a csv-file");
        setPropertyTooltip("invert", "exchange right and left ear.");
        setPropertyTooltip("SelectCalibrationFile", "select the xml file which can be created by matlab");
        setPropertyTooltip("calibrationFilePath", "Full path to xml calibration file");
        setPropertyTooltip("estimationMethod", "Method used to compute the ITD");
        setPropertyTooltip("useGanglionCellType", "If CochleaAMS which Ganglion cells to use");
        setPropertyTooltip("amsProcessingMethod", "If CochleaAMS how to process different neurons");
        setPropertyTooltip("numLoopMean", "Method used to compute the ITD");
        setPropertyTooltip("numOfCochleaChannels", "The number of frequency channels of the cochleae");
        setPropertyTooltip("normToConfThresh", "Normalize the bins before every spike to the value of the confidence Threshold");
        setPropertyTooltip("ToggleITDDisplay", "Toggles graphical display of ITD");
        setPropertyTooltip("TimeLocalExtremaDetection", "Sets the timescale in which local extrema in the ITD Confidence are detected (in us)");

        addPropertyToGroup("ITDWeighting", "useLaterSpikeForWeight");
        addPropertyToGroup("ITDWeighting", "usePriorSpikeForWeight");
        addPropertyToGroup("ITDWeighting", "maxWeight");
        addPropertyToGroup("ITDWeighting", "maxWeightTime");
*/
        setPropertyTooltip("ClusterThresh", "Clustering Threshold relative to Maximum");
        setPropertyTooltip("v_decay", "Decayingfactor of the Retina histogram map");
        setPropertyTooltip("CoherenceWindow", "Timeframe within which Spikes count as correlated");

 /*        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().addCommandListener(this, "itdfilter", "Testing remotecontrol of itdfilter.");
        }
 
 */
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {

        if (!filterEnabled) {
            return in;
        }

        for (Object e : in) {
            
            BasicEvent ev = (BasicEvent)e;
            try {

	             if (ev.y > 128){
	                // cochlea

	                audioArray[ev.x][ev.y-128][1] = audioArray[ev.x][ev.y-128][1]+1;
                        LastAudioSpike = ev.timestamp;
                        //log.info("audio spike " + ev.x + " " + ev.y);
	            } else {
	                // retina
	                visualArray[ev.x][ev.y][1] = visualArray[ev.x][ev.y][1]+1;
                        if (Math.abs(ev.timestamp-LastAudioSpike)<CoherenceWindow){
                            process_coherent_spike(ev);
                        }
                        else {
                            CoherenceQueue.offer(ev);
                            LastQueueTimestamp = ev.timestamp;
                            BasicEvent CoItem = (BasicEvent)CoherenceQueue.peek();
                            while (CoItem.timestamp+2*CoherenceWindow<LastQueueTimestamp){
                                CoherenceQueue.remove();
                                CoItem = (BasicEvent)CoherenceQueue.peek();
                            }
                        }
                        
	            }
                    if (prev_timestamp > ev.timestamp){
                        prev_timestamp = 0;
                    }
                    if (ev.timestamp - prev_timestamp > 100000){
                        double time_diff = ev.timestamp - prev_timestamp;
                        prev_timestamp = ev.timestamp;
                        double decay = 0.9;//1/(time_diff*0.00002);
                        //log.info("diff" + decay);
                        decay_audio(a_decay);
                        decay_video(v_decay);
                        decay_coherence(c_decay);
//                        log.info("max " + max_video() + "decay " + decay);
                    }


	        } catch (Exception ex) {
	            log.warning("In filterPacket caught exception " + ex + " " + ev.x + " " + ev.y);
	            ex.printStackTrace();
	        }

        }
        //log.info("size of coherence Queue: "+CoherenceQueue.size());
        median_filter(3);
        return in;
    }

    public void process_coherent_spike(BasicEvent ev){
        //log.info("coherent event" + ev.x + " " + ev.y);
        //coherenceMap[ev.x][ev.y]=coherenceMap[ev.x][ev.y]+3;
        double max = max_video();
        if (ev.x>2 && ev.x<126 && ev.y>2 && ev.y<126){
            for (int i=ev.x-1; i<=ev.x+1; i++){
                for (int j=ev.y-1; j<=ev.y+1; j++){
                    if (visualArray[i][j][0]>0.5*max){
                        coherenceMap[i][j]=coherenceMap[i][j]+1;
                    }
                }
            }
        }

    }

    public void decay_audio(double decay_factor){
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                audioArray[i][j][1]=(double)audioArray[i][j][1]*decay_factor;
            }
        }

    }
    public void decay_video(double decay_factor){
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                visualArray[i][j][1]=(double)visualArray[i][j][1]*decay_factor;
            }
        }

    }

    public void decay_coherence(double decay_factor){
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                coherenceMap[i][j]=(double)coherenceMap[i][j]*decay_factor;
            }
        }

    }

    public void median_filter(int win_size){
        double[] frame = new double[win_size*win_size];
        int half_win = (int)(win_size/2);
        // clear border
/*        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++){
                if (i<half_win || j<half_win || i>128-half_win || j>128-half_win){
                    visualArray[i][j][0]=0;
                    visualArray[i][j][1]=0;
                }
            }
        }
*/
        for (int i=half_win; i<128-half_win; i++){
            for (int j=half_win; j<128-half_win; j++) {
                int a=0;
                for (int k=i-half_win; k<=i+half_win; k++){
                    for (int l=j-half_win; l<=j+half_win; l++) {
                        frame[a] = visualArray[k][l][1];
                        a = a + 1;
                    }
                }
                Arrays.sort(frame);
                visualArray[i][j][0] = frame[(int)((win_size*win_size)/2)];
            }
        }
        //flip_zeronone();
    }

    public void flip_zeronone(){
        double[][] temp = new double[128][128];
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                temp[i][j]=visualArray[i][j][1];
                visualArray[i][j][1] = visualArray[i][j][0];
                visualArray[i][j][0] = temp[i][j];
            }
        }
    }

    public double max_video(){
        double max = 0;
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (visualArray[i][j][1]>max){
                    max = visualArray[i][j][1];
                }
            }
        }
        return max;
    }

    public double max_audio(){
        double max = 0;
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (audioArray[i][j][1]>max){
                    max = audioArray[i][j][1];
                }
            }
        }
        return max;
    }

      public double max_coherence(){
        double max = 0;
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (coherenceMap[i][j]>max){
                    max = coherenceMap[i][j];
                }
            }
        }
        return max;
    }

        /**
	*	Drawing routine
	*
	*/
    public void annotate(GLAutoDrawable drawable) {


        double maximum = max_video();
        double a_maximum = max_audio();
        double c_maximum = max_coherence();
        double threshold = maximum*0.5;
        GL gl=drawable.getGL();

        gl.glLineWidth(1);
        
        //gl.glClearColor(0.f,0.f,0.f,1.f);
        //gl.glClear(gl.GL_COLOR_BUFFER_BIT);

        gl.glPushMatrix();

        gl.glBegin(GL.GL_POINTS);
        gl.glColor4f(0,0,1,.3f);
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                double dratio = visualArray[i][j][1]/maximum;
                float ratio = (float)dratio;
                if (ratio<0.25){
                    gl.glColor4f(0,ratio*4,1,.8f);
                }
                if (ratio<0.5 && ratio>=0.25){
                    gl.glColor4f(0,1,1/(2*ratio)-1,.8f);
                }
                if (ratio<0.75 && ratio>=0.5){
                    gl.glColor4f(4*ratio-2,1,0,.8f);
                }
                if (ratio>=0.65){
                    gl.glColor4f(1,3/ratio-3,0,.8f);
                }
                gl.glVertex2d(130+i, j);
            }
        }
        gl.glColor4f(0,1,0,.3f);
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (audioArray[i][j][1]>a_maximum*0.1){
                    gl.glVertex2d(-130+i, j);
                }
            }
        }
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (visualArray[i][j][0]>maximum*ClusterThresh){
                    gl.glVertex2d(-130+i, 130+j);
                }
            }
        }

        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (c_maximum != 0){
                    float c_ratio = (float)(coherenceMap[i][j]/c_maximum);
                    gl.glColor4f(c_ratio,0,0,1f);
                    //if (c_ratio >0.2){
                        gl.glVertex2d(130+i, 130+j);
                    //}
                    
                }

            }
        }

        gl.glEnd();

        gl.glPopMatrix();

        gl.glFlush();
//        gl.glDisable(GL.GL_BLEND);
/*        gl.glPushMatrix();
        gl.glTranslatef(30,30,0);
        gl.glColor4f(0,0,1,.3f);

*/
    }




    public Object getFilterState() {
        return null;
    }

    public void resetFilter() {
        initFilter();
    }

    @Override
    public void initFilter() {
//        log.info("init() called");

        log.info("Cochlea - Retina Mapper Filter started\n");
/*
        int dim = 1;
        switch (amsProcessingMethod) {
            case AllNeuronsTogether:
                dim = 1;
                break;
            case NeuronsIndividually:
                dim = this.numNeuronTypes;
                break;
            case StoreSeparetlyCompareEvery:
                dim = this.numNeuronTypes;
        }
        lastTs = new int[numOfCochleaChannels][dim][2][dimLastTs];
        lastTsCursor = new int[numOfCochleaChannels][dim][2];

        init_arrays();

        for (int i = 0; i < numOfCochleaChannels; i++) {
            for (int j = 0; j < dim; j++) {
                for (int k = 0; k < 2; k++) {
                    for (int l = 0; l < dimLastTs; l++) {
                        lastTs[i][j][k][l] = Integer.MIN_VALUE;
                    }
                }
            }
        }

        ConfidenceRecentMax = 0;
        ConfidenceRecentMaxTime = 0;
        ConfidenceRecentMin = 0;
        ConfidenceRecentMinTime = 0;
        ConfidenceRising = true;
*/
    }

    @Override
    public void setFilterEnabled(boolean yes) {
//        log.info("ITDFilter.setFilterEnabled() is called");
        super.setFilterEnabled(yes);
        if (yes) {
//            try {
//                createBins();
//            } catch (Exception e) {
//                log.warning("In genBins() caught exception " + e);
//                e.printStackTrace();
//            }
//            display = getPrefs().getBoolean("ITDFilter.display", false);
//            setDisplay(display);
            initFilter();
        }
    }

    public void update(Observable o, Object arg) {
        if (arg != null) {
//            log.info("ITDFilter.update() is called from " + o + " with arg=" + arg);
/*            if (arg.equals("eventClass")) {
                if (chip.getEventClass() == CochleaAMSEvent.class) {
                    hasMultipleGanglionCellTypes = true;
                    this.numNeuronTypes = 4;
                } else {
                    hasMultipleGanglionCellTypes = false;
                    this.numNeuronTypes = 1;
                }
                this.initFilter();
            }*/
        }
    }
    public float getV_decay(){
        return this.v_decay;
    }
    public void setV_decay(float v_decay){
        getPrefs().putFloat("Distinguish.v_decay", v_decay);
        support.firePropertyChange("v_decay", this.v_decay, v_decay);
        this.v_decay = v_decay;
    }

    public float getClusterThresh(){
        return this.ClusterThresh;
    }
    public void setClusterThresh(float ClusterThresh){
        getPrefs().putFloat("Distinguish.ClusterThresh", ClusterThresh);
        support.firePropertyChange("ClusterThresh", this.ClusterThresh, ClusterThresh);
        this.ClusterThresh = ClusterThresh;
    }

    public int getCoherenceWindow() {
        return this.CoherenceWindow;
    }
    public void setCoherenceWindow(int CoherenceWindow) {
        getPrefs().putInt("Distinguish.CoherenceWindow", CoherenceWindow);
        support.firePropertyChange("CoherenceWindow", this.CoherenceWindow, CoherenceWindow);
        this.CoherenceWindow = CoherenceWindow;
    }
/*
    public int getMaxITD() {
        return this.maxITD;
    }



    public int getNumOfBins() {
        return this.numOfBins;
    }


    public int getMaxWeight() {
        return this.maxWeight;
    }



    public int getConfidenceThreshold() {
        return this.confidenceThreshold;
    }

    public void setConfidenceThreshold(int confidenceThreshold) {
        getPrefs().putInt("ITDFilter.confidenceThreshold", confidenceThreshold);
        support.firePropertyChange("confidenceThreshold", this.confidenceThreshold, confidenceThreshold);
        this.confidenceThreshold = confidenceThreshold;
    }

    public int getActivityThreshold() {
        return this.activityThreshold;
    }

    public void setActivityThreshold(int activityThreshold) {
        getPrefs().putInt("ITDFilter.activityThreshold", activityThreshold);
        support.firePropertyChange("activityThreshold", this.activityThreshold, activityThreshold);
        this.activityThreshold = activityThreshold;
    }

    public int getItdEventQueueSize() {
        return this.itdEventQueueSize;
    }

    public void setItdEventQueueSize(int itdEventQueueSize) {
        getPrefs().putInt("ITDFilter.itdEventQueueSize", itdEventQueueSize);
        support.firePropertyChange("itdEventQueueSize", this.itdEventQueueSize, itdEventQueueSize);
        this.itdEventQueueSize = itdEventQueueSize;
        if (this.sendITDsToOtherThread) {
            ITDEventQueue = new ArrayBlockingQueue(itdEventQueueSize);
        }
    }

    public int getTimeLocalExtremaDetection() {
        return this.timeLocalExtremaDetection;
    }

    public void setTimeLocalExtremaDetection(int timeLocalExtremaDetection) {
        getPrefs().putInt("ITDFilter.timeLocalExtremaDetection", timeLocalExtremaDetection);
        support.firePropertyChange("timeLocalExtremaDetection", this.timeLocalExtremaDetection, timeLocalExtremaDetection);
        this.timeLocalExtremaDetection = timeLocalExtremaDetection;
    }

    public int getMaxWeightTime() {
        return this.maxWeightTime;
    }



    public int getDimLastTs() {
        return this.dimLastTs;
    }

    public void setDimLastTs(int dimLastTs) {
        getPrefs().putInt("ITDFilter.dimLastTs", dimLastTs);
        support.firePropertyChange("dimLastTs", this.dimLastTs, dimLastTs);
        //lastTs = new int[numOfCochleaChannels][numNeuronTypes][2][dimLastTs];
        this.dimLastTs = dimLastTs;
        this.initFilter();
    }

    public int getNumLoopMean() {
        return this.numLoopMean;
    }



    public int getNumOfCochleaChannels() {
        return this.numOfCochleaChannels;
    }

    public void setNumOfCochleaChannels(int numOfCochleaChannels) {
        getPrefs().putInt("ITDFilter.numOfCochleaChannels", numOfCochleaChannels);
        support.firePropertyChange("numOfCochleaChannels", this.numOfCochleaChannels, numOfCochleaChannels);
        this.numOfCochleaChannels = numOfCochleaChannels;
        lastTs = new int[numOfCochleaChannels][numNeuronTypes][2][dimLastTs];
        lastTsCursor = new int[numOfCochleaChannels][numNeuronTypes][2];
    }

    public float getAveragingDecay() {
        return this.averagingDecay;
    }

*/

    /** Adds button to show display */






/*
    public boolean isDisplay() {
        return this.display;
    }
*/
  

/*
    public boolean getUseLaterSpikeForWeight() {
        return this.useLaterSpikeForWeight;
    }
*/
/*
    public void setUseLaterSpikeForWeight(boolean useLaterSpikeForWeight) {
        getPrefs().putBoolean("ITDFilter.useLaterSpikeForWeight", useLaterSpikeForWeight);
        support.firePropertyChange("useLaterSpikeForWeight", this.useLaterSpikeForWeight, useLaterSpikeForWeight);
        this.useLaterSpikeForWeight = useLaterSpikeForWeight;
        if (!isFilterEnabled()) {
            return;
        }
    }
*/
/*
    public boolean isUsePriorSpikeForWeight() {
        return this.usePriorSpikeForWeight;
    }


    public boolean isWriteBin2File() {
        return this.writeBin2File;
    }
*/
/*
    public void setWriteBin2File(boolean writeBin2File) {
        if (writeBin2File == true) {
            try {
                JFileChooser fc = new JFileChooser();
                fc.setDialogType(JFileChooser.SAVE_DIALOG);
                int state = fc.showSaveDialog(null);
                if (state == JFileChooser.APPROVE_OPTION) {
                    String path = fc.getSelectedFile().getPath();
                    startWriteBin2File(path);
                }
            } catch (Exception e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        } else if (BinFile != null) {
            try {
                //Close the output stream
                BinFile.close();
                BinFile = null;
            } catch (Exception e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        }
        getPrefs().putBoolean("ITDFilter.writeBin2File", writeBin2File);
        support.firePropertyChange("writeBin2File", this.writeBin2File, writeBin2File);
        this.writeBin2File = writeBin2File;
    }
*/
/*
    public boolean isSaveFrequenciesSeperately() {
        return this.saveFrequenciesSeperately;
    }

    public void setSaveFrequenciesSeperately(boolean saveFrequenciesSeperately) {
        if (saveFrequenciesSeperately) {

            try {
                JFileChooser fc = new JFileChooser();
                fc.setDialogType(JFileChooser.SAVE_DIALOG);
                int state = fc.showSaveDialog(null);
                if (state == JFileChooser.APPROVE_OPTION) {
                    String path = fc.getSelectedFile().getPath();
                    startSaveFreq(path);
                }
            } catch (Exception e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        } else {
            try {
                //Close the output stream
                freqBinFile.close();
                freqBinFile = null;
            } catch (Exception e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
            
        }
        getPrefs().putBoolean("ITDFilter.saveFrequenciesSeperately", saveFrequenciesSeperately);
        support.firePropertyChange("saveFrequenciesSeperately", this.saveFrequenciesSeperately, saveFrequenciesSeperately);
        this.saveFrequenciesSeperately = saveFrequenciesSeperately;
    }

    public boolean isWriteAvgITD2File() {
        return this.writeAvgITD2File;
    }

    public void setWriteAvgITD2File(boolean writeAvgITD2File) {

        if (writeAvgITD2File == true) {
            try {
                JFileChooser fc = new JFileChooser();
                fc.setDialogType(JFileChooser.SAVE_DIALOG);
                int state = fc.showSaveDialog(null);
                if (state == JFileChooser.APPROVE_OPTION) {
                    String path = fc.getSelectedFile().getPath();
                    startAvgITD2File(path);
                }
            } catch (Exception e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        } else if (AvgITDFile != null) {
            try {
                //Close the output stream
                AvgITDFile.close();
                AvgITDFile = null;
                getPrefs().putBoolean("ITDFilter.writeAvgITD2File", writeAvgITD2File);
                support.firePropertyChange("writeAvgITD2File", this.writeAvgITD2File, writeAvgITD2File);
                this.writeAvgITD2File = writeAvgITD2File;
            } catch (Exception e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        }
    }

    public boolean isWriteITD2File() {
        return this.writeITD2File;
    }

    public void setWriteITD2File(boolean writeITD2File) {
        getPrefs().putBoolean("ITDFilter.writeITD2File", writeITD2File);
        support.firePropertyChange("writeITD2File", this.writeITD2File, writeITD2File);
        this.writeITD2File = writeITD2File;

        if (writeITD2File == true) {
            try {
                JFileChooser fc = new JFileChooser();
                fc.setDialogType(JFileChooser.SAVE_DIALOG);
                int state = fc.showSaveDialog(null);
                if (state == JFileChooser.APPROVE_OPTION) {
                    String path = fc.getSelectedFile().getPath();

                    // Create file
                    fstream = new FileWriter(path);
                    ITDFile = new BufferedWriter(fstream);
                    ITDFile.write("time\tITD\tchan\tweight\n");

                    getPrefs().putBoolean("ITDFilter.writeITD2File", writeITD2File);
                    support.firePropertyChange("writeITD2File", this.writeITD2File, writeITD2File);
                    this.writeITD2File = writeITD2File;
                }
            } catch (Exception e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        } else if (ITDFile != null) {
            try {
                //Close the output stream
                ITDFile.close();
                ITDFile = null;
            } catch (Exception e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        }
    }

    public boolean isSendITDsToOtherThread() {
        return this.sendITDsToOtherThread;
    }

    public void setSendITDsToOtherThread(boolean sendITDsToOtherThread) {
        getPrefs().putBoolean("ITDFilter.sendITDsToOtherThread", sendITDsToOtherThread);
        support.firePropertyChange("sendITDsToOtherThread", this.sendITDsToOtherThread, sendITDsToOtherThread);
        this.sendITDsToOtherThread = sendITDsToOtherThread;
        if (sendITDsToOtherThread == true) {
            ITDEventQueue = new ArrayBlockingQueue(this.itdEventQueueSize);
        } else {
            ITDEventQueue = null;
        }
    }

    public boolean isDisplaySoundDetected() {
        return this.displaySoundDetected;
    }

    public void setDisplaySoundDetected(boolean displaySoundDetected) {
        getPrefs().putBoolean("ITDFilter.displaySoundDetected", displaySoundDetected);
        support.firePropertyChange("displaySoundDetected", this.displaySoundDetected, displaySoundDetected);
        this.displaySoundDetected = displaySoundDetected;
    }

    public boolean isDisplayNormalize() {
        return this.displayNormalize;
    }

    public void setDisplayNormalize(boolean displayNormalize) {
        getPrefs().putBoolean("ITDFilter.displayNormalize", displayNormalize);
        support.firePropertyChange("displayNormalize", this.displayNormalize, displayNormalize);
        this.displayNormalize = displayNormalize;

    }

    public boolean isWrite2FileForEverySpike() {
        return this.write2FileForEverySpike;
    }

    public void setWrite2FileForEverySpike(boolean write2FileForEverySpike) {
        getPrefs().putBoolean("ITDFilter.write2FileForEverySpike", write2FileForEverySpike);
        support.firePropertyChange("write2FileForEverySpike", this.write2FileForEverySpike, write2FileForEverySpike);
        this.write2FileForEverySpike = write2FileForEverySpike;
    }

    public boolean isWeightFrequencies() {
        return this.weightFrequencies;
    }

    public void setWeightFrequencies(boolean weightFrequencies) {
        getPrefs().putBoolean("ITDFilter.weightFrequencies", weightFrequencies);
        support.firePropertyChange("weightFrequencies", this.weightFrequencies, weightFrequencies);
        this.weightFrequencies = weightFrequencies;
        if (weightFrequencies) {
            JFileChooser fc = new JFileChooser();
            fc.setDialogType(JFileChooser.SAVE_DIALOG);
            int state = fc.showSaveDialog(null);
            if (state == JFileChooser.APPROVE_OPTION) {
                String path = fc.getSelectedFile().getPath();
                try {

                    File file = new File(path);
                    BufferedReader bufRdr = new BufferedReader(new FileReader(file));
                    int row = 0;
                    String line = bufRdr.readLine();
                    StringTokenizer st = new StringTokenizer(line, ",");
                    frequencyWeights = new double[64];
                    while (st.hasMoreTokens()) {
                        frequencyWeights[row] = Double.parseDouble(st.nextToken());
                        row++;
                    }
                    bufRdr.close();
                } catch (IOException ex) {
                    log.warning("while loading weights, caught exception " + ex);
                    ex.printStackTrace();
                }
            }
        }
    }

    public boolean isUse1DRegression() {
        return this.use1DRegression;
    }

    public void setUse1DRegression(boolean use1DRegression) {
        if (use1DRegression) {
            JFileChooser fc = new JFileChooser();
            fc.setDialogType(JFileChooser.SAVE_DIALOG);
            int state = fc.showSaveDialog(null);
            if (state == JFileChooser.APPROVE_OPTION) {
                String path = fc.getSelectedFile().getPath();
                try {

                    File file = new File(path);
                    BufferedReader bufRdr = new BufferedReader(new FileReader(file));
                    ridgeWeights = new double[2];
                    for (int i = 0; i < 2; i++) {
                        ridgeWeights[i] = Double.parseDouble(bufRdr.readLine());
                    }
                    bufRdr.close();
                } catch (IOException ex) {
                    log.warning("while loading weights, caught exception " + ex);
                    ex.printStackTrace();
                }
            }
        }
        getPrefs().putBoolean("ITDFilter.use1DRegression", use1DRegression);
        support.firePropertyChange("use1DRegression", this.use1DRegression, use1DRegression);
        this.use1DRegression = use1DRegression;
    }


    public boolean isComputeMeanInLoop() {
        return this.useLaterSpikeForWeight;
    }
 */
/*   public void setComputeMeanInLoop(boolean computeMeanInLoop) {
        getPrefs().putBoolean("ITDFilter.computeMeanInLoop", computeMeanInLoop);
        support.firePropertyChange("computeMeanInLoop", this.computeMeanInLoop, computeMeanInLoop);
        this.computeMeanInLoop = computeMeanInLoop;
        if (!isFilterEnabled()) {
            return;
        }
        if (computeMeanInLoop == true) {
            if (myBins == null) {
                createBins();
            } else {
                myBins.setNumLoopMean(numLoopMean);
            }
        } else {
            if (myBins == null) {
                createBins();
            } else {
                myBins.setNumLoopMean(1);
            }
        }
    }
*/
/*
    public boolean isUseCalibration() {
        return this.useCalibration;
    }
*/
/*
    public void setUseCalibration(boolean useCalibration) {
        getPrefs().putBoolean("ITDFilter.useCalibration", useCalibration);
        support.firePropertyChange("useCalibration", this.useCalibration, useCalibration);
        this.useCalibration = useCalibration;
        //createBins();
    }

    public boolean isInvert() {
        return this.invert;
    }

    public void setInvert(boolean invert) {
        getPrefs().putBoolean("ITDFilter.invert", invert);
        support.firePropertyChange("invert", this.invert, invert);
        this.invert = invert;
    }

    public void doSelectCalibrationFile() {
        if (calibrationFilePath == null || calibrationFilePath.isEmpty()) {
            calibrationFilePath = System.getProperty("user.dir");
        }
        JFileChooser chooser = new JFileChooser(calibrationFilePath);
        chooser.setDialogTitle("Choose calibration .xml file (created with matlab)");
        chooser.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".xml");
            }

            @Override
            public String getDescription() {
                return "Executables";
            }
        });
        chooser.setMultiSelectionEnabled(false);
        int retval = chooser.showOpenDialog(getChip().getAeViewer().getFilterFrame());
        if (retval == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (f != null && f.isFile()) {
                setCalibrationFilePath(f.toString());
                log.info("selected xml calibration file " + calibrationFilePath);
                setUseCalibration(true);

            }
        }
    }
*/
    /**
     * @return the calibrationFilePath
     */
/*    public String getCalibrationFilePath() {
        return calibrationFilePath;
    }
*/
    /**
     * @param calibrationFilePath the calibrationFilePath to set
     */
/*
    public void setCalibrationFilePath(String calibrationFilePath) {
        support.firePropertyChange("calibrationFilePath", this.calibrationFilePath, calibrationFilePath);
        this.calibrationFilePath = calibrationFilePath;
        getPrefs().put("ITDFilter.calibrationFilePath", calibrationFilePath);
    }
*/
/*
    public EstimationMethod getEstimationMethod() {
        return estimationMethod;
    }
*/
/*
    synchronized public void setEstimationMethod(EstimationMethod estimationMethod) {
        support.firePropertyChange("estimationMethod", this.estimationMethod, estimationMethod);
        getPrefs().put("ITDfilter.estimationMethod", estimationMethod.toString());
        this.estimationMethod = estimationMethod;
    }
*/
/*
    public AMSprocessingMethod getAmsProcessingMethod() {
        return amsProcessingMethod;
    }
*/
/*
    synchronized public void setAmsProcessingMethod(AMSprocessingMethod amsProcessingMethod) {
        support.firePropertyChange("amsProcessingMethod", this.amsProcessingMethod, amsProcessingMethod);
        getPrefs().put("ITDfilter.amsProcessingMethod", amsProcessingMethod.toString());
        this.amsProcessingMethod = amsProcessingMethod;
        this.initFilter();
    }
*/
/*
    public CochleaAMSEvent.FilterType getUseGanglionCellType() {
        return useGanglionCellType;
    }
*/
/*
    synchronized public void setUseGanglionCellType(CochleaAMSEvent.FilterType useGanglionCellType) {
        support.firePropertyChange("useGanglionCellType", this.useGanglionCellType, useGanglionCellType);
        getPrefs().put("ITDfilter.useGanglionCellType", useGanglionCellType.toString());
        this.useGanglionCellType = useGanglionCellType;
    }
*/
 /*   public static ITDEvent takeITDEvent() throws InterruptedException {
        return (ITDEvent) ITDEventQueue.take();
    }

    public static ITDEvent pollITDEvent() {
        if (ITDEventQueue != null) {
            return (ITDEvent) ITDEventQueue.poll();
        } else {
            return null;
        }
    }
*/
/*
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        GL gl = drawable.getGL();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(1, 1, 1);
        gl.glRasterPos3f(0, 0, 0);
        if (showAnnotations == true) {
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("avgITD(us)=%s", fmt.format(avgITD)));
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ITDConfidence=%f", avgITDConfidence));
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ILD=%f", ILD));
            if (useLaterSpikeForWeight == true || usePriorSpikeForWeight == true) {
                glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  lastWeight=%f", lastWeight));
            }
        }*/
/*        if (display == true && frame != null) {
            //frame.setITD(avgITD);
            frame.setText(String.format("avgITD(us)=%s   ITDConfidence=%f   ILD=%f", fmt.format(avgITD), avgITDConfidence, ILD));
        }*/
/*        gl.glPopMatrix();
    }
*/
   public void annotate(float[][][] frame) {
        throw new UnsupportedOperationException("Not supported yet, use openGL rendering.");
    }

    public void annotate(Graphics2D g) {
        throw new UnsupportedOperationException("Not supported yet, use openGL rendering..");
    }

    /** Returns the ITDBins object.
     *
     * @return the ITDBins object.
     */
}




