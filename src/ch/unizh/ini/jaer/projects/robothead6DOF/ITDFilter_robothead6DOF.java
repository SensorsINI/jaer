/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent;
import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent.Ear;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent;
import ch.unizh.ini.jaer.projects.cochsoundloc.CommObjForPanTilt;
import ch.unizh.ini.jaer.projects.cochsoundloc.ITDBins;
import ch.unizh.ini.jaer.projects.cochsoundloc.ITDCalibrationGaussians;
import ch.unizh.ini.jaer.projects.cochsoundloc.ITDEvent;
import ch.unizh.ini.jaer.projects.cochsoundloc.ITDFrame;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import org.ine.telluride.jaer.tell2011.head6axis.Head6DOF_ServoController;


/**
 * Extracts interaural time difference (ITD) from a binaural cochlea input.
 *
 * @author Holger
 * see original at ch.unizh.ini.jaer.projects.cochsoundloc
 *
 * changes for the use with the 6DOF robot head from Jörg Conradt
 * @editor Philipp
 */
@Description("Measures ITD (Interaural time difference) using a variety of methods")
public class ITDFilter_robothead6DOF extends EventFilter2D implements Observer, FrameAnnotater, RemoteControlled {
    
    protected static final Logger log = Logger.getLogger("ITDFilter_robothead6DOF");
    private ITDCalibrationGaussians calibration = null;
    private float averagingDecay = getPrefs().getFloat("ITDFilter_robothead6DOF.averagingDecay", 1);
    private int maxITD = getPrefs().getInt("ITDFilter_robothead6DOF.maxITD", 800);
    private int numOfBins = getPrefs().getInt("ITDFilter_robothead6DOF.numOfBins", 16);
    private int maxWeight = getPrefs().getInt("ITDFilter_robothead6DOF.maxWeight", 5);
    private int dimLastTs = getPrefs().getInt("ITDFilter_robothead6DOF.dimLastTs", 4);
    private int maxWeightTime = getPrefs().getInt("ITDFilter_robothead6DOF.maxWeightTime", 500000);
    private boolean display = getPrefs().getBoolean("ITDFilter_robothead6DOF.display", false);
    private boolean displayNormalize = getPrefs().getBoolean("ITDFilter_robothead6DOF.displayNormalize", true);
    private boolean displaySoundDetected = getPrefs().getBoolean("ITDFilter_robothead6DOF.displaySoundDetected", true);
    private boolean useLaterSpikeForWeight = getPrefs().getBoolean("ITDFilter_robothead6DOF.useLaterSpikeForWeight", true);
    private boolean usePriorSpikeForWeight = getPrefs().getBoolean("ITDFilter_robothead6DOF.usePriorSpikeForWeight", true);
    private boolean computeMeanInLoop = getPrefs().getBoolean("ITDFilter_robothead6DOF.computeMeanInLoop", true);
    private boolean writeAvgITD2File = getPrefs().getBoolean("ITDFilter_robothead6DOF.writeAvgITD2File", false);
    private boolean writeITD2File = getPrefs().getBoolean("ITDFilter_robothead6DOF.writeITD2File", false);
    private boolean sendITDsToOtherThread = getPrefs().getBoolean("ITDFilter_robothead6DOF.sendITDsToOtherThread", false);
    private int itdEventQueueSize = getPrefs().getInt("ITDFilter_robothead6DOF.itdEventQueueSize", 1000);
    private int timeLocalExtremaDetection = getPrefs().getInt("ITDFilter_robothead6DOF.timeLocalExtremaDetection", 200000);
    private boolean writeBin2File = getPrefs().getBoolean("ITDFilter_robothead6DOF.writeBin2File", false);
    private boolean saveFrequenciesSeperately = getPrefs().getBoolean("ITDFilter_robothead6DOF.saveFrequenciesSeperately", false);
    private boolean invert = getPrefs().getBoolean("ITDFilter_robothead6DOF.invert", false);
    private boolean write2FileForEverySpike = getPrefs().getBoolean("ITDFilter_robothead6DOF.write2FileForEverySpike", false);
    private boolean weightFrequencies = getPrefs().getBoolean("ITDFilter_robothead6DOF.weightFrequencies", false);
    private boolean useRidgeRegression = getPrefs().getBoolean("ITDFilter_robothead6DOF.useRidgeRegression", false);
    private boolean use1DRegression = getPrefs().getBoolean("ITDFilter_robothead6DOF.use1DRegression", false);
    private boolean normToConfThresh = getPrefs().getBoolean("ITDFilter_robothead6DOF.normToConfThresh", false);
    private boolean showAnnotations = getPrefs().getBoolean("ITDFilter_robothead6DOF.showAnnotations", false);
    private int confidenceThreshold = getPrefs().getInt("ITDFilter_robothead6DOF.confidenceThreshold", 30);
    private int activityThreshold = getPrefs().getInt("ITDFilter_robothead6DOF.activityThreshold", 30);
    private int numLoopMean = getPrefs().getInt("ITDFilter_robothead6DOF.numLoopMean", 2);
    private int useLowerChannel = getPrefs().getInt("ITDFilter_robothead6DOF.useLowerChannel", 0);
    private int useUpperChannel = getPrefs().getInt("ITDFilter_robothead6DOF.useUpperChannel", 63);
    private int numOfCochleaChannels = 64;
    private boolean useCalibration = getPrefs().getBoolean("ITDFilter_robothead6DOF.useCalibration", false);
    private String calibrationFilePath = getPrefs().get("ITDFilter_robothead6DOF.calibrationFilePath", null);
    /// beamforming
    private boolean beamFormingEnabled = getBoolean("ITDFilter_robothead6DOF.beamFormingEnabled", false);
    private int beamFormingRangeUs = getInt("ITDFilter_robothead6DOF.beamFormingRangeUs", 100);
    private float beamFormingITDUs = getFloat("ITDFilter_robothead6DOF.beamFormingITDUs", Float.NaN);
    // UDP messages
    private String sendITD_UDP_port = getString("ITDFilter_robothead6DOF.sendITD_UDP_port", "localhost:9999");
    private boolean sendITD_UDP_Messages = getBoolean("ITDFilter_robothead6DOF.sendITD_UDP_Messages", false);
    protected DatagramChannel channel = null;
    protected DatagramSocket socket = null;
    int packetSequenceNumber = 0;
    InetSocketAddress client = null;
    private int UDP_BUFFER_SIZE = 1024;
    private ByteBuffer udpBuffer = ByteBuffer.allocateDirect(UDP_BUFFER_SIZE);
    private boolean printedFirstUdpMessage = false;
    long lastUdpMsgTime = 0;
    int MIN_UPD_PACKET_INTERVAL_MS = 15;

    //////////////////////
    private double[] frequencyWeights;
    private double[] ridgeWeights;
    ITDFrame frame;
    private ITDBins myBins;
    private boolean connectToPanTiltThread = false;
    //private LinkedList[][] lastTimestamps;
    //private ArrayList<LinkedList<Integer>> lastTimestamps0;
    //private ArrayList<LinkedList<Integer>> lastTimestamps1;
    private int[][][][] lastTs;
    private int[][][] lastTsCursor;
    //private int[][] AbsoluteLastTimestamp;
    Iterator iterator;
    private float lastWeight = 1f;
    /**
     * filled in with measured best ITD according to selected method (max,
     * median, mean)
     */
    private int bestITD;
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
    public PanTilt_robothead6DOF panTilt = null;
    private ITDBins[] freqBins;
    private double ConfidenceRecentMax = 0;
    private int ConfidenceRecentMaxTime = 0;
    private double ConfidenceRecentMin = 1e6;
    private int ConfidenceRecentMinTime = 0;
    private boolean ConfidenceRising = true;
    FilterChain filterChain = null;
    public Head6DOF_ServoController headControl = null;
    public ITDImageCreator ITDImageCreator;

    private String USAGE = "Need at least 2 arguments: itdfilter <command> <args>\nCommands are: saveitd, saveitdandreset, stopsaveitd, savefreqbins <filename>, stopsavefreqbins, savebin <filename>, stopsavebin, resetbins, savebinnow <filename>, zerotimestamps\n";

    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s", 3);
        if (tok.length < 2) {
            return USAGE;
        }

        try {
            if (tok[1].equals("saveitd")) {
                if (tok.length < 2) {
                    return USAGE;
                } else {
                    startAvgITD2File(tok[2]);
                    return "starting to save itds\n";
                }
            }
            if (tok[1].equals("saveitdandreset")) {
                if (tok.length < 2) {
                    return USAGE;
                } else {
                    chip.getAeViewer().zeroTimestamps();
                    createBins();
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
                    return USAGE;
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
                    return USAGE;
                }
                String filename = tok[2];
                log.info("save bins to: " + filename);
                startWriteBin2File(filename);
                return "starting to save bins\n";
            }
            if (tok[1].equals("stopsavebin")) {
                setWriteBin2File(false);
                return "stop saving bins\n";
            }
            if (tok[1].equals("resetbins")) {
                createBins();
                return "bins reseted.\n";
            }
            if (tok[1].equals("savebinnow")) {
                if (tok.length < 3) {
                    return USAGE;
                } else {
                    writeBin2FileNow(tok[2]);
                    return "writing bins now\n";
                }
            }
            if (tok[1].equals("zerotimestamps")) {
                chip.getAeViewer().zeroTimestamps();
                return "zeroed time\n";
            }
            log.info("Received Command:" + input);
        } catch (IOException e) {
            return "IOExeption in remotecontrol\n";
        }
        return USAGE;
    }

    private void startAvgITD2File(String path) throws IOException {
        // Create file
        fstream = new FileWriter(path);
        AvgITDFile = new BufferedWriter(fstream);
        AvgITDFile.write("time\tITD\tconf\n");
        getPrefs().putBoolean("ITDFilter_robothead6DOF.writeAvgITD2File", true);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.writeAvgITD2File", writeAvgITD2File, true);
        writeAvgITD2File = true;
    }

    private void startSaveFreq(String path) throws IOException {
        freqBins = new ITDBins[64];
        for (int i = 0; i < 64; i++) {
            freqBins[i] = new ITDBins(averagingDecay * 1000000, 1, maxITD, numOfBins);
        }

        // Create file
        freqfstreamBins = new FileWriter(path);
        freqBinFile = new BufferedWriter(freqfstreamBins);
        String titles = "time\tfreq\t";
        for (int i = 0; i < numOfBins; i++) {
            titles += "Bin" + i + "\t";
        }
        titles += "\n";
        freqBinFile.write(titles);
        getPrefs().putBoolean("ITDFilter_robothead6DOF.saveFrequenciesSeperately", true);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.saveFrequenciesSeperately", saveFrequenciesSeperately, true);
        saveFrequenciesSeperately = true;
    }

    private void startWriteBin2File(String path) throws IOException {
        // Create file
        fstreamBins = new FileWriter(path);
        BinFile = new BufferedWriter(fstreamBins);
        String titles = "time\t";
        for (int i = 0; i < numOfBins; i++) {
            titles += "Bin" + i + "\t";
        }
        BinFile.write(titles);
    }

    private void writeBin2FileNow(String path) throws IOException {
        // Create file
        fstreamBins = new FileWriter(path);
        BinFile = new BufferedWriter(fstreamBins);

        if (isSaveFrequenciesSeperately()) {
            String titles = "time\tfreq\t";
            for (int i = 0; i < numOfBins; i++) {
                titles += "Bin" + i + "\t";
            }
            titles += "\n";
            BinFile.write(titles);
            for (int i = 0; i < 64; i++) {
                freqBins[i].updateTime(0, myBins.getTimestamp());
                if (freqBinFile != null) {
                    try {
                        BinFile.write(myBins.getTimestamp() + "\t" + i + "\t" + freqBins[i].toString() + "\n");
                    } catch (IOException ex) {
                        log.severe("can not write bin to file: " + ex.toString());
                    }
                }
            }
        } else {
            String titles = "time\t";
            for (int i = 0; i < numOfBins; i++) {
                titles += "Bin" + i + "\t";
            }
            titles += "\n";
            BinFile.write(titles);
            BinFile.write(0 + "\t" + myBins.toString() + "\n");
        }
        BinFile.close();

    }

    /**
     * Returns the overall best ITD. See {@link #getITDBins()} to access the
     * histogram data and statistics. Filled in with measured best ITD according
     * to selected method (max, median, mean)
     *
     * @return the avgITD in us
     * @see #getNumOfBins()
     * @see #getMaxITD()
     */
    public int getBestITD() {
        return bestITD;
    }

    /**
     * Returns the overall confidence measure of the average ITD.
     *
     * @return the avgITDConfidence
     */
    public float getAvgITDConfidence() {
        return avgITDConfidence;
    }

    /**
     * @param avgITDConfidence the avgITDConfidence to set
     */
    public void setAvgITDConfidence(float avgITDConfidence) {
        this.avgITDConfidence = avgITDConfidence;
    }

    public enum EstimationMethod {

        useMedian, useMean, useMax
    };
    private EstimationMethod estimationMethod = EstimationMethod.valueOf(getPrefs().get("ITDFilter_robothead6DOF.estimationMethod", "useMax"));

    public enum AMSprocessingMethod {

        NeuronsIndividually, AllNeuronsTogether, StoreSeparetlyCompareEvery
    };
    private AMSprocessingMethod amsProcessingMethod = AMSprocessingMethod.valueOf(getPrefs().get("ITDFilter_robothead6DOF.amsProcessingMethod", "NeuronsIndividually"));
    //    public enum UseGanglionCellType {
    //        LPF, BPF
    //    };
    private CochleaAMSEvent.FilterType useGanglionCellType = CochleaAMSEvent.FilterType.valueOf(getPrefs().get("ITDFilter_robothead6DOF.useGanglionCellType", "LPF"));
    private boolean hasMultipleGanglionCellTypes = false;
    //    private ActionListener updateBinFrame = new ActionListener() {
    //
    //        public void actionPerformed(ActionEvent evt) {
    //                //wasMoving = false;
    //            }
    //        };
    //    private javax.swing.Timer timer = new javax.swing.Timer(5, updateBinFrame);
    private float averagingDecayTmp = 0;

    public ITDFilter_robothead6DOF(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        headControl = new Head6DOF_ServoController(chip);
        // headControl.getSupport().addPropertyChangeListener(Head6DOF_ServoController_robothead6DOF.Message.PanTiltSet.name(), this);
        filterChain.add(headControl);
        setEnclosedFilterChain(filterChain);
        chip.addObserver(this);
        initFilter();
		//resetFilter();
        //lastTimestamps = (LinkedList[][])new LinkedList[32][2];
        //LinkedList[][] <Integer>lastTimestamps = new LinkedList<Integer>[1][2]();
        //        lastTimestamps0 = new ArrayList<LinkedList<Integer>>(32);
        //        lastTimestamps1 = new ArrayList<LinkedList<Integer>>(32);
        //        for (int k=0;k<32;k++) {
        //            lastTimestamps0.add(new LinkedList<Integer>());
        //            lastTimestamps1.add(new LinkedList<Integer>());
        //        }

        //AbsoluteLastTimestamp = new int[32][2];
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
        setPropertyTooltip("sendITDsToOtherThread", "send ITD messages to another thread via an ArrayBlockingQueue available from static method pollITDEvent");
        String udp = "UDP Messages";
        setPropertyTooltip(udp, "sendITD_UDP_Messages", "send ITD messages via UDP datagrams to a chosen host and port");
        setPropertyTooltip(udp, "sendITD_UDP_port", "hostname:port (e.g. localhost:9999) to send UDP ITD histograms to; messages are int32 seq # followed by int32 bin values");
        String bf = "Beam Forming";
        setPropertyTooltip(bf, "beamFormingEnabled", "filters out events that are not near the peak ITD");
        setPropertyTooltip(bf, "beamFormingRangeUs", "range of time in us for which events are passed around best ITD");
        setPropertyTooltip(bf, "beamFormingITDUs", "explicit ITD to pass through; set to NaN to pass around peak measured ITD");

        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().addCommandListener(this, "ITDFilter_robothead6DOF.itdfilter", "Testing remotecontrol of itdfilter.");
        }

        setSendITD_UDP_port(sendITD_UDP_port);
        setSendITD_UDP_Messages(sendITD_UDP_Messages); // init port for datagram if used
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (connectToPanTiltThread) {
            CommObjForITDFilter_robothead6DOF commObjIncomming = panTilt.pollBlockingQForITDFilter();
            while (commObjIncomming != null) {
                log.info("Got a commObj from the PanTiltThread!");
                switch (commObjIncomming.getCommand()) {
                    case 0:
                        break;
                    case 1:
                        break;
                    case 2:
                        break;
                    case 3:
                        averagingDecayTmp = averagingDecay;
                        setAveragingDecay(0);
                        break;
                    case 4:
                        setAveragingDecay(averagingDecayTmp);
                        break;
                    case 5:
                        myBins.clear();
                        break;
                }
                commObjIncomming = panTilt.pollBlockingQForITDFilter();
            }
        }
        if (wasMoving == true) {
            wasMoving = false;
            myBins.clear();
            //log.info("clear bins!");
        }
        checkOutputPacketEventType(in);
        if (in.isEmpty()) {
            return in;
        }

        OutputEventIterator outItr = out.outputIterator();
        int nleft = 0, nright = 0;
        for (Object e : in) {
            BinauralCochleaEvent i = (BinauralCochleaEvent) e;
            int ganglionCellThreshold;
            if (hasMultipleGanglionCellTypes
                    && ((amsProcessingMethod == AMSprocessingMethod.NeuronsIndividually)
                    || (amsProcessingMethod == AMSprocessingMethod.StoreSeparetlyCompareEvery))) {
                CochleaAMSEvent camsevent = ((CochleaAMSEvent) i);
                ganglionCellThreshold = camsevent.getThreshold();
                //                if (useGanglionCellType != camsevent.getFilterType()) {
                //                    continue;
                //                }
            } else {
                ganglionCellThreshold = 0;
            }
            try {
                int ear;
                if (i.getEar() == Ear.RIGHT) {
                    ear = 0;
                } else {
                    ear = 1;
                }

                if (invert) {
                    ear = (ear + 1) % 2;
                }

                if (i.x < useLowerChannel || i.x > useUpperChannel)  {
                  //  log.warning("there was a BasicEvent i with i.x=" + i.x + " that was outside of the selceted channel range. Therefore this event gets discarded");
                } else {
                    int cursor = lastTsCursor[i.x][ganglionCellThreshold][1 - ear];
                    do {
                        int diff = i.timestamp - lastTs[i.x][ganglionCellThreshold][1 - ear][cursor];
                        if (ear == 0) {
                            diff = -diff;
                            nright++;
                        } else {
                            nleft++;
                        }
                        int absdiff = java.lang.Math.abs(diff);
                        if (absdiff < maxITD) {

                            lastWeight = 1f;
                            //Compute weight:
                            if (useLaterSpikeForWeight == true) {
                                int weightTimeThisSide = i.timestamp - lastTs[i.x][ganglionCellThreshold][ear][lastTsCursor[i.x][ganglionCellThreshold][ear]];
                                if (weightTimeThisSide > maxWeightTime) {
                                    weightTimeThisSide = maxWeightTime;
                                }
                                lastWeight *= ((weightTimeThisSide * (maxWeight - 1f)) / maxWeightTime) + 1f;
                                if (weightTimeThisSide < 0) {
                                    //log.warning("weightTimeThisSide < 0");
                                    lastWeight = 0;
                                }
                            }
                            if (usePriorSpikeForWeight == true) {
                                int weightTimeOtherSide = lastTs[i.x][ganglionCellThreshold][1 - ear][cursor] - lastTs[i.x][ganglionCellThreshold][1 - ear][(cursor + 1) % dimLastTs];
                                if (weightTimeOtherSide > maxWeightTime) {
                                    weightTimeOtherSide = maxWeightTime;
                                }
                                lastWeight *= ((weightTimeOtherSide * (maxWeight - 1f)) / maxWeightTime) + 1f;
                                if (weightTimeOtherSide < 0) {
                                    //log.warning("weightTimeOtherSide < 0");
                                    lastWeight = 0;
                                }
                            }
                            if (weightFrequencies && (frequencyWeights != null)) {
                                lastWeight *= frequencyWeights[i.x];
                            }
                            if (normToConfThresh == true) {
                                myBins.addITD(diff, i.timestamp, i.x, lastWeight, confidenceThreshold);
                            } else {
                                myBins.addITD(diff, i.timestamp, i.x, lastWeight, 0);
                            }
                            if (freqBins != null) {
                                freqBins[i.x].addITD(diff, i.timestamp, i.x, lastWeight, 0);
                            }
                            if ((writeITD2File == true) && (ITDFile != null)) {
                                ITDFile.write(i.timestamp + "\t" + diff + "\t" + i.x + "\t" + lastWeight + "\n");
                            }
                            if (sendITDsToOtherThread) {
                                if (ITDEventQueue == null) {
                                    ITDEventQueue = new ArrayBlockingQueue(itdEventQueueSize);
                                }
                                ITDEvent itdEvent = new ITDEvent(diff, i.timestamp, i.x, lastWeight);
                                if (itdEvent.getChannel() < 38) {      //frequency selective movement of the head, set to 0 for no selectivity
                                    boolean success = ITDEventQueue.offer(bestITD);
                                    if (success == false) {
//                                   ITDEventQueue.clear();
                                        ITDEventQueue.take();
//                                    ITDEventQueueFull = true;
//                                    log.warning("Could not add ITD-Event to the ITDEventQueue. Probably itdEventQueueSize is too small!!!");
                                    } else {
                                        ITDEventQueueFull = false;
                                        //log.info("added ITD Event: " + Integer.toString(itdEvent.getITD()) + " on channel: " + Integer.toString(i.x));
                                    }
                                }

                            }

                            if (isBeamFormingEnabled()) {
                                // if
                                int bestITD = Float.isNaN(beamFormingITDUs) ? (int) beamFormingITDUs : getBestITD();
                                if (Math.abs(diff - bestITD) < beamFormingRangeUs) {
                                    BinauralCochleaEvent oe = (BinauralCochleaEvent) outItr.nextOutput();
                                    oe.copyFrom(i);
                                }
                            }
                        } else {
                            break;
                        }
                        cursor = (++cursor) % dimLastTs;
                    } while (cursor != lastTsCursor[i.x][ganglionCellThreshold][1 - ear]);
                    //Now decrement the cursor (circularly)
                    if (lastTsCursor[i.x][ganglionCellThreshold][ear] == 0) {
                        lastTsCursor[i.x][ganglionCellThreshold][ear] = dimLastTs;
                    }
                    lastTsCursor[i.x][ganglionCellThreshold][ear]--;
                    //Add the new timestamp to the list
                    lastTs[i.x][ganglionCellThreshold][ear][lastTsCursor[i.x][ganglionCellThreshold][ear]] = i.timestamp;

                    if (write2FileForEverySpike == true) {
                        if ((writeAvgITD2File == true) && (AvgITDFile != null)) {
                            refreshITD();
                            AvgITDFile.write(i.timestamp + "\t" + bestITD + "\t" + avgITDConfidence + "\n");
                        }
                        if ((writeBin2File == true) && (BinFile != null)) {
                            refreshITD();
                            BinFile.write(i.timestamp + "\t" + myBins.toString() + "\n");
                        }
                    }
                }

            } catch (Exception e1) {
                log.warning("In for-loop in filterPacket caught exception " + e1);
                e1.printStackTrace();
            }
        }
        try {
            if (normToConfThresh == true) {
                myBins.updateTime(confidenceThreshold, in.getLastTimestamp());
            } else {
                myBins.updateTime(0, in.getLastTimestamp());
            }

            refreshITD();
            ILD = (float) (nleft - nright) / (float) (nright + nleft); //Max ILD is 1 (if only one side active)
            if (write2FileForEverySpike == false) {
                if ((writeAvgITD2File == true) && (AvgITDFile != null)) {
                    AvgITDFile.write(in.getLastTimestamp() + "\t" + bestITD + "\t" + avgITDConfidence + "\n");
                }
                if ((writeBin2File == true) && (BinFile != null)) {
                    BinFile.write(in.getLastTimestamp() + "\t" + myBins.toString() + "\n");
                }
            }
        } catch (Exception e) {
            log.warning("In filterPacket caught exception " + e);
            e.printStackTrace();
        }

        // send udp messages
        long now = System.currentTimeMillis();
        if (((now - lastUdpMsgTime) > MIN_UPD_PACKET_INTERVAL_MS) && sendITD_UDP_Messages && (client != null)) {
            lastUdpMsgTime = now;
            try {
                udpBuffer.clear();
                udpBuffer.putInt(packetSequenceNumber++);
                udpBuffer.putLong(now);
                udpBuffer.putInt(maxITD);
                float[] bins = getITDBins().getBins();
                for (float f : bins) {
                    udpBuffer.putFloat(f);
                }
                if (!printedFirstUdpMessage) {
                    log.info("sending buf=" + udpBuffer + " to client=" + client);
                    printedFirstUdpMessage = true;
                }
                udpBuffer.flip();
                channel.send(udpBuffer, client);
            } catch (IOException udpEx) {
                log.warning(udpEx.toString());
                setSendITD_UDP_Messages(false);
            } catch (BufferOverflowException boe) {
                log.warning(boe.toString() + ": decrease number of histogram bins to fit 1024 byte datagrams");
            }
        }
        return isBeamFormingEnabled() ? out : in;
    }

    public void refreshITD() {

        int avgITDtemp = 0;
        switch (estimationMethod) {
            case useMedian:
                avgITDtemp = myBins.getITDMedian();
                break;
            case useMean:
                avgITDtemp = myBins.getITDMean();
                break;
            case useMax:
                avgITDtemp = myBins.getITDMax();
        }
        avgITDConfidence = myBins.getITDConfidence();
        bestITD = avgITDtemp;  //set new best ITD without checking for confidence

        if (ConfidenceRising) {
            if (avgITDConfidence > ConfidenceRecentMax) {
                ConfidenceRecentMax = avgITDConfidence;
                ConfidenceRecentMaxTime = myBins.getTimestamp();
            } else {
                if ((ConfidenceRecentMaxTime + timeLocalExtremaDetection) < myBins.getTimestamp()) {
                    //if (myBins.getBin((int) myBins.convertITD2BIN(avgITDtemp)) > activityThreshold) {
                    if (avgITDConfidence > confidenceThreshold) {
                        //Speech Detected!
                        if (frame != null) {
                            frame.binsPanel.setLocalisedPos(avgITDtemp);
                        }
                        ConfidenceRising = false;

                        bestITD = avgITDtemp;

                        if (saveFrequenciesSeperately) {
                            for (int i = 0; i < 64; i++) {
                                freqBins[i].updateTime(0, myBins.getTimestamp());
                                if (freqBinFile != null) {
                                    try {
                                        freqBinFile.write(myBins.getTimestamp() + "\t" + i + "\t" + freqBins[i].toString() + "\n");
                                    } catch (IOException ex) {
                                        log.severe("can not refresh ITDs: " + ex.toString());
                                    }
                                }
                            }
                        }

                        if (connectToPanTiltThread == true) {

                            CommObjForPanTilt filterOutput = new CommObjForPanTilt();
                            filterOutput.setFromCochlea(true);
                            if (useRidgeRegression) {
                                double servopos = ridgeWeights[0];
                                for (int i = 0; i < 64; i++) {
                                    freqBins[i].updateTime(0, myBins.getTimestamp());
                                    servopos += (freqBins[i].getITDMaxIndex() + 1) * ridgeWeights[i + 1];
                                }
                                filterOutput.setPanOffset(((float) servopos * 2f) - 1f);
                            } else if (use1DRegression) {
                                double servopos = ridgeWeights[0] + ((myBins.getITDMaxIndex() + 1) * ridgeWeights[1]);
                                filterOutput.setPanOffset(((float) servopos * 2f) - 1f);
                            } else {
                                filterOutput.setPanOffset((float) bestITD / (float) maxITD);
                            }
                            filterOutput.setConfidence(avgITDConfidence);
                            panTilt.offerBlockingQ(filterOutput);
                        }

                        ConfidenceRecentMin = 100000000;
                        ConfidenceRecentMax = 0;
                    }

                    //}
                }
            }
        } else {
            if (avgITDConfidence < ConfidenceRecentMin) {
                ConfidenceRecentMin = avgITDConfidence;
                ConfidenceRecentMinTime = myBins.getTimestamp();
            } else {
                if ((ConfidenceRecentMinTime + timeLocalExtremaDetection) < myBins.getTimestamp()) {
                    //Min detected;
                    ConfidenceRising = true;
                    ConfidenceRecentMin = 100000000;
                    ConfidenceRecentMax = 0;
                }
            }
        }

        if (frame != null) {
            frame.binsPanel.repaint();
        }
    }

    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
        initFilter();
    }

    @Override
    public void initFilter() {
        //        log.info("init() called");
        int dim = 1;
        switch (amsProcessingMethod) {
            case AllNeuronsTogether:
                dim = 1;
                break;
            case NeuronsIndividually:
                dim = numNeuronTypes;
                break;
            case StoreSeparetlyCompareEvery:
                dim = numNeuronTypes;
        }
        lastTs = new int[numOfCochleaChannels][dim][2][dimLastTs];
        lastTsCursor = new int[numOfCochleaChannels][dim][2];

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

        if (isFilterEnabled()) {
            createBins();
            setDisplay(display);
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        //        log.info("ITDFilter_robothead6DOF.setFilterEnabled() is called");
        super.setFilterEnabled(yes);
        if (yes) {
            //            try {
            //                createBins();
            //            } catch (Exception e) {
            //                log.warning("In genBins() caught exception " + e);
            //                e.printStackTrace();
            //            }
            //            display = getPrefs().getBoolean("ITDFilter_robothead6DOF.display", false);
            //            setDisplay(display);
            initFilter();
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg != null) {
            //            log.info("ITDFilter_robothead6DOF.update() is called from " + o + " with arg=" + arg);
            if (arg.equals("eventClass")) {
                if (chip.getEventClass() == CochleaAMSEvent.class) {
                    hasMultipleGanglionCellTypes = true;
                    numNeuronTypes = 4;
                } else {
                    hasMultipleGanglionCellTypes = false;
                    numNeuronTypes = 1;
                }
                initFilter();
            }
        }
    }

    public int getMaxITD() {
        return maxITD;
    }

    public void setMaxITD(int maxITD) {
        getPrefs().putInt("ITDFilter_robothead6DOF.maxITD", maxITD);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.maxITD", this.maxITD, maxITD);
        this.maxITD = maxITD;
        createBins();
    }

    public int getNumOfBins() {
        return numOfBins;
    }

    public void setNumOfBins(int numOfBins) {
        getPrefs().putInt("ITDFilter_robothead6DOF.numOfBins", numOfBins);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.numOfBins", this.numOfBins, numOfBins);
        this.numOfBins = numOfBins;
        createBins();
    }

    public int getMaxWeight() {
        return maxWeight;
    }

    public void setMaxWeight(int maxWeight) {
        getPrefs().putInt("ITDFilter_robothead6DOF.maxWeight", maxWeight);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.maxWeight", this.maxWeight, maxWeight);
        this.maxWeight = maxWeight;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public int getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(int confidenceThreshold) {
        getPrefs().putInt("ITDFilter_robothead6DOF.confidenceThreshold", confidenceThreshold);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.confidenceThreshold", this.confidenceThreshold, confidenceThreshold);
        this.confidenceThreshold = confidenceThreshold;
    }

    public int getActivityThreshold() {
        return activityThreshold;
    }

    public void setActivityThreshold(int activityThreshold) {
        getPrefs().putInt("ITDFilter_robothead6DOF.activityThreshold", activityThreshold);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.activityThreshold", this.activityThreshold, activityThreshold);
        this.activityThreshold = activityThreshold;
    }

    public int getItdEventQueueSize() {
        return itdEventQueueSize;
    }

    public void setItdEventQueueSize(int itdEventQueueSize) {
        getPrefs().putInt("ITDFilter_robothead6DOF.itdEventQueueSize", itdEventQueueSize);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.itdEventQueueSize", this.itdEventQueueSize, itdEventQueueSize);
        this.itdEventQueueSize = itdEventQueueSize;
        if (sendITDsToOtherThread) {
            ITDEventQueue = new ArrayBlockingQueue(itdEventQueueSize);
        }
    }

    public int getTimeLocalExtremaDetection() {
        return timeLocalExtremaDetection;
    }

    public void setTimeLocalExtremaDetection(int timeLocalExtremaDetection) {
        getPrefs().putInt("ITDFilter_robothead6DOF.timeLocalExtremaDetection", timeLocalExtremaDetection);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.timeLocalExtremaDetection", this.timeLocalExtremaDetection, timeLocalExtremaDetection);
        this.timeLocalExtremaDetection = timeLocalExtremaDetection;
    }

    public int getMaxWeightTime() {
        return maxWeightTime;
    }

    public void setMaxWeightTime(int maxWeightTime) {
        getPrefs().putInt("ITDFilter_robothead6DOF.maxWeightTime", maxWeightTime);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.maxWeightTime", this.maxWeightTime, maxWeightTime);
        this.maxWeightTime = maxWeightTime;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public int getDimLastTs() {
        return dimLastTs;
    }

    public void setDimLastTs(int dimLastTs) {
        getPrefs().putInt("ITDFilter_robothead6DOF.dimLastTs", dimLastTs);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.dimLastTs", this.dimLastTs, dimLastTs);
        this.dimLastTs = dimLastTs;
        initFilter();
    }

    public int getNumLoopMean() {
        return numLoopMean;
    }

    public void setNumLoopMean(int numLoopMean) {
        getPrefs().putInt("ITDFilter_robothead6DOF.numLoopMean", numLoopMean);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.numLoopMean", this.numLoopMean, numLoopMean);
        this.numLoopMean = numLoopMean;
        if (!isFilterEnabled() || (computeMeanInLoop == false)) {
            return;
        }
        if (myBins == null) {
            createBins();
        } else {
            myBins.setNumLoopMean(numLoopMean);
        }
    }
   
    public int getUseLowerChannel() {
        return useLowerChannel;
    }

    public void setUseLowerChannel(int useLowerChannel) {
        getPrefs().putInt("ITDFilter_robothead6DOF.useLowerChannel", useLowerChannel);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.useLowerChannel", this.useLowerChannel, useLowerChannel);
        this.useLowerChannel = useLowerChannel;       
        numOfCochleaChannels = 64;
        lastTs = new int[numOfCochleaChannels][numNeuronTypes][2][dimLastTs];
        lastTsCursor = new int[numOfCochleaChannels][numNeuronTypes][2];
    }
    
    public int getUseUpperChannel() {
        return useUpperChannel;
    }

    public void setUseUpperChannel(int useUpperChannel) {
        getPrefs().putInt("ITDFilter_robothead6DOF.numOfCochleaChannels", useUpperChannel);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.numOfCochleaChannels", this.useUpperChannel, useUpperChannel);
        this.useUpperChannel = useUpperChannel;
        numOfCochleaChannels = 64;
        lastTs = new int[numOfCochleaChannels][numNeuronTypes][2][dimLastTs];
        lastTsCursor = new int[numOfCochleaChannels][numNeuronTypes][2];
    }
    

    public float getAveragingDecay() {
        return averagingDecay;
    }

    public void setAveragingDecay(float averagingDecay) {
        getPrefs().putDouble("ITDFilter_robothead6DOF.averagingDecay", averagingDecay);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.averagingDecay", this.averagingDecay, averagingDecay);
        this.averagingDecay = averagingDecay;
        if (!isFilterEnabled()) {
            return;
        }
        if (myBins == null) {
            createBins();
        } else {
            myBins.setAveragingDecay(averagingDecay * 1000000);
        }
    }

    /**
     * Adds button to show display
     */
    public void doToggleITDDisplay() {
        boolean old = isDisplay();
        setDisplay(!isDisplay());
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.display", old, display);
    }

    /**
     * Adds button to show display
     */
    public void doResetITDDisplay() {
        if (frame == null) {
            log.warning("no display to reset - null frame");
            return;
        }
        frame.binsPanel.maxActivity = 0f;
    }

    public void doConnectToPanTiltThread(ITDImageCreator creator) {  //has to be called from a ITDImageCreator filter
        if(headControl.isConnected() == true){
        panTilt = PanTilt_robothead6DOF.findExistingPanTiltThread(chip.getAeViewer());
        if (panTilt == null) {
            ITDImageCreator = creator;
            panTilt = new PanTilt_robothead6DOF();
            panTilt.initPanTilt(this);
        }
        connectToPanTiltThread = true;
        } else {
            log.info("not connected to robothead");
        }
    }
        
    public void doDisconnectFromPanTiltThread() {
        if(panTilt != null){
            connectToPanTiltThread = false;
            panTilt.doStopPanTiltThread();
            panTilt = null;
            log.info("Disconnected from PanTilt");
        } else {
            log.info("no PanTiltThread running");
        }
    }

    public void doFitGaussianToBins() {
        numOfBins = myBins.getNumOfBins();
        double xData[] = new double[numOfBins];
        double yData[] = new double[numOfBins];
        for (int i = 0; i < numOfBins; i++) {
            xData[i] = i;
        }
        for (int i = 0; i < numOfBins; i++) {
            yData[i] = myBins.getBin(i);
        }
    }

    public boolean isDisplay() {
        return display;
    }

    public void setDisplay(boolean display) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.display", display);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.display", this.display, display);
        this.display = display;
        if (!isFilterEnabled()) {
            return;
        }
        if ((display == false) && (frame != null)) {
            //            frame.setVisible(false);
            frame.dispose();
            frame = null;
        } else if (display == true) {
            if (frame == null) {
                try {
                    frame = new ITDFrame();
                    frame.binsPanel.updateBins(myBins);
                    frame.binsPanel.setDisplayNormalize(displayNormalize);
                    frame.binsPanel.setDisplaySoundDetected(displaySoundDetected);
                    //                    getChip().getFilterFrame().addWindowListener(new java.awt.event.WindowAdapter() {
                    //
                    //                        @Override
                    //                        public void windowClosed(java.awt.event.WindowEvent evt) {
                    //                            if (frame == null) {
                    //                                return;
                    //                            }
                    //                            log.info("disposing of " + frame);
                    //                            frame.dispose(); // close ITD frame if filter frame is closed.
                    //                            frame = null;
                    //                            ITDFilter_robothead6DOF.this.display = false; // set this so we know that itdframe has been disposed so that next button press on doToggleITDDisplay works correctly
                    //                        }
                    //                    });
                    log.info("ITD-Jframe created with height=" + frame.getHeight() + " and width:" + frame.getWidth());
                } catch (Exception e) {
                    log.warning("while creating ITD-Jframe, caught exception " + e);
                    e.printStackTrace();
                }
            }
            if (!frame.isVisible()) {
                frame.setVisible(true); // only grab focus by setting frame visible 0if frame is not already visible
            }
        }
    }

    @Override
    public synchronized void cleanup() {
        setDisplay(false);
    }

    public boolean getUseLaterSpikeForWeight() {
        return useLaterSpikeForWeight;
    }

    public void setUseLaterSpikeForWeight(boolean useLaterSpikeForWeight) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.useLaterSpikeForWeight", useLaterSpikeForWeight);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.useLaterSpikeForWeight", this.useLaterSpikeForWeight, useLaterSpikeForWeight);
        this.useLaterSpikeForWeight = useLaterSpikeForWeight;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public boolean isUsePriorSpikeForWeight() {
        return usePriorSpikeForWeight;
    }

    public void setUsePriorSpikeForWeight(boolean usePriorSpikeForWeight) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.usePriorSpikeForWeight", usePriorSpikeForWeight);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.usePriorSpikeForWeight", this.usePriorSpikeForWeight, usePriorSpikeForWeight);
        this.usePriorSpikeForWeight = usePriorSpikeForWeight;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public boolean isWriteBin2File() {
        return writeBin2File;
    }

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
        getPrefs().putBoolean("ITDFilter_robothead6DOF.writeBin2File", writeBin2File);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.writeBin2File", this.writeBin2File, writeBin2File);
        this.writeBin2File = writeBin2File;
    }

    public boolean isSaveFrequenciesSeperately() {
        return saveFrequenciesSeperately;
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
            freqBins = null;
        }
        getPrefs().putBoolean("ITDFilter_robothead6DOF.saveFrequenciesSeperately", saveFrequenciesSeperately);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.saveFrequenciesSeperately", this.saveFrequenciesSeperately, saveFrequenciesSeperately);
        this.saveFrequenciesSeperately = saveFrequenciesSeperately;
    }

    public boolean isWriteAvgITD2File() {
        return writeAvgITD2File;
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
                getPrefs().putBoolean("ITDFilter_robothead6DOF.writeAvgITD2File", writeAvgITD2File);
                getSupport().firePropertyChange("ITDFilter_robothead6DOF.writeAvgITD2File", this.writeAvgITD2File, writeAvgITD2File);
                this.writeAvgITD2File = writeAvgITD2File;
            } catch (Exception e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        }
    }

    public boolean isWriteITD2File() {
        return writeITD2File;
    }

    public void setWriteITD2File(boolean writeITD2File) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.writeITD2File", writeITD2File);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.writeITD2File", this.writeITD2File, writeITD2File);
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

                    getPrefs().putBoolean("ITDFilter_robothead6DOF.writeITD2File", writeITD2File);
                    getSupport().firePropertyChange("ITDFilter_robothead6DOF.writeITD2File", this.writeITD2File, writeITD2File);
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
        return sendITDsToOtherThread;
    }

    public void setSendITDsToOtherThread(boolean sendITDsToOtherThread) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.sendITDsToOtherThread", sendITDsToOtherThread);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.sendITDsToOtherThread", this.sendITDsToOtherThread, sendITDsToOtherThread);
        this.sendITDsToOtherThread = sendITDsToOtherThread;
        if (sendITDsToOtherThread == true) {
            ITDEventQueue = new ArrayBlockingQueue(itdEventQueueSize);
        } else {
            ITDEventQueue = null;
        }
    }

    public boolean isDisplaySoundDetected() {
        return displaySoundDetected;
    }

    public void setDisplaySoundDetected(boolean displaySoundDetected) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.displaySoundDetected", displaySoundDetected);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.displaySoundDetected", this.displaySoundDetected, displaySoundDetected);
        this.displaySoundDetected = displaySoundDetected;
        if (frame != null) {
            frame.binsPanel.setDisplaySoundDetected(displaySoundDetected);
        }
    }

    public boolean isDisplayNormalize() {
        return displayNormalize;
    }

    public void setDisplayNormalize(boolean displayNormalize) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.displayNormalize", displayNormalize);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.displayNormalize", this.displayNormalize, displayNormalize);
        this.displayNormalize = displayNormalize;
        if (frame != null) {
            frame.binsPanel.setDisplayNormalize(displayNormalize);
        }
    }

    public boolean isWrite2FileForEverySpike() {
        return write2FileForEverySpike;
    }

    public void setWrite2FileForEverySpike(boolean write2FileForEverySpike) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.write2FileForEverySpike", write2FileForEverySpike);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.write2FileForEverySpike", this.write2FileForEverySpike, write2FileForEverySpike);
        this.write2FileForEverySpike = write2FileForEverySpike;
    }

    public boolean isWeightFrequencies() {
        return weightFrequencies;
    }

    public void setWeightFrequencies(boolean weightFrequencies) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.weightFrequencies", weightFrequencies);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.weightFrequencies", this.weightFrequencies, weightFrequencies);
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
        return use1DRegression;
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
        getPrefs().putBoolean("ITDFilter_robothead6DOF.use1DRegression", use1DRegression);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.use1DRegression", this.use1DRegression, use1DRegression);
        this.use1DRegression = use1DRegression;
    }

    public boolean isUseRidgeRegression() {
        return useRidgeRegression;
    }

    public void setUseRidgeRegression(boolean useRidgeRegression) {
        if (useRidgeRegression) {
            if (freqBins == null) {
                freqBins = new ITDBins[64];
                for (int i = 0; i < 64; i++) {
                    freqBins[i] = new ITDBins(averagingDecay * 1000000, 1, maxITD, numOfBins);
                }
            }
            JFileChooser fc = new JFileChooser();
            fc.setDialogType(JFileChooser.SAVE_DIALOG);
            int state = fc.showSaveDialog(null);
            if (state == JFileChooser.APPROVE_OPTION) {
                String path = fc.getSelectedFile().getPath();
                try {

                    File file = new File(path);
                    BufferedReader bufRdr = new BufferedReader(new FileReader(file));
                    ridgeWeights = new double[65];
                    for (int i = 0; i < 65; i++) {
                        ridgeWeights[i] = Double.parseDouble(bufRdr.readLine());
                    }
                    bufRdr.close();
                } catch (IOException ex) {
                    log.warning("while loading weights, caught exception " + ex);
                    ex.printStackTrace();
                }
            }
        }
        getPrefs().putBoolean("ITDFilter_robothead6DOF.useRidgeRegression", useRidgeRegression);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.useRidgeRegression", this.useRidgeRegression, useRidgeRegression);
        this.useRidgeRegression = useRidgeRegression;
    }

    public boolean isNormToConfThresh() {
        return normToConfThresh;
    }

    public void setNormToConfThresh(boolean normToConfThresh) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.normToConfThresh", normToConfThresh);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.normToConfThresh", this.normToConfThresh, normToConfThresh);
        this.normToConfThresh = normToConfThresh;
    }

    public boolean isShowAnnotations() {
        return showAnnotations;
    }

    public void setShowAnnotations(boolean showAnnotations) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.showAnnotations", showAnnotations);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.showAnnotations", this.showAnnotations, showAnnotations);
        this.showAnnotations = showAnnotations;
    }

    public boolean isComputeMeanInLoop() {
        return useLaterSpikeForWeight;
    }

    public void setComputeMeanInLoop(boolean computeMeanInLoop) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.computeMeanInLoop", computeMeanInLoop);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.computeMeanInLoop", this.computeMeanInLoop, computeMeanInLoop);
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

    public boolean isUseCalibration() {
        return useCalibration;
    }

    public void setUseCalibration(boolean useCalibration) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.useCalibration", useCalibration);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.useCalibration", this.useCalibration, useCalibration);
        this.useCalibration = useCalibration;
        createBins();
    }

    public boolean isInvert() {
        return invert;
    }

    public void setInvert(boolean invert) {
        getPrefs().putBoolean("ITDFilter_robothead6DOF.invert", invert);
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.invert", this.invert, invert);
        this.invert = invert;
    }

    public void doSelectCalibrationFile() {
        if ((calibrationFilePath == null) || calibrationFilePath.isEmpty()) {
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
            if ((f != null) && f.isFile()) {
                setCalibrationFilePath(f.toString());
                log.info("selected xml calibration file " + calibrationFilePath);
                setUseCalibration(true);

            }
        }
    }

    /**
     * @return the calibrationFilePath
     */
    public String getCalibrationFilePath() {
        return calibrationFilePath;
    }

    /**
     * @param calibrationFilePath the calibrationFilePath to set
     */
    public void setCalibrationFilePath(String calibrationFilePath) {
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.calibrationFilePath", this.calibrationFilePath, calibrationFilePath);
        this.calibrationFilePath = calibrationFilePath;
        getPrefs().put("ITDFilter_robothead6DOF.calibrationFilePath", calibrationFilePath);
    }

    public EstimationMethod getEstimationMethod() {
        return estimationMethod;
    }

    synchronized public void setEstimationMethod(EstimationMethod estimationMethod) {
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.estimationMethod", this.estimationMethod, estimationMethod);
        getPrefs().put("ITDfilter.estimationMethod", estimationMethod.toString());
        this.estimationMethod = estimationMethod;
    }

    public AMSprocessingMethod getAmsProcessingMethod() {
        return amsProcessingMethod;
    }

    synchronized public void setAmsProcessingMethod(AMSprocessingMethod amsProcessingMethod) {
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.amsProcessingMethod", this.amsProcessingMethod, amsProcessingMethod);
        getPrefs().put("ITDfilter.amsProcessingMethod", amsProcessingMethod.toString());
        this.amsProcessingMethod = amsProcessingMethod;
        initFilter();
    }

    public CochleaAMSEvent.FilterType getUseGanglionCellType() {
        return useGanglionCellType;
    }

    synchronized public void setUseGanglionCellType(CochleaAMSEvent.FilterType useGanglionCellType) {
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.useGanglionCellType", this.useGanglionCellType, useGanglionCellType);
        getPrefs().put("ITDfilter.useGanglionCellType", useGanglionCellType.toString());
        this.useGanglionCellType = useGanglionCellType;
    }

    public static ITDEvent takeITDEvent() throws InterruptedException {
        return (ITDEvent) ITDEventQueue.take();
    }

    public static Integer pollITDEvent() {
        if (ITDEventQueue != null) {
            ITDEventQueue.clear();
        }
        try {
            //return (ITDEvent) ITDEventQueue.poll(100, TimeUnit.SECONDS); //wait "forever" for new ITD Event
            return (Integer) ITDEventQueue.poll(100, TimeUnit.SECONDS); //wait "forever" for new ITD Event
        } catch (InterruptedException ex) {
            log.severe("no ITD event to poll: " + ex.toString());
        }
        return null;
    }

    private void createBins() {
        int numLoop;
        if (computeMeanInLoop == true) {
            numLoop = numLoopMean;
        } else {
            numLoop = 1;
        }
        if (useCalibration == false) {
            //log.info("create Bins with averagingDecay=" + averagingDecay + " and maxITD=" + maxITD + " and numOfBins=" + numOfBins);
            myBins = new ITDBins(averagingDecay * 1000000, numLoop, maxITD, numOfBins);
        } else {
            if (calibration == null) {
                calibration = new ITDCalibrationGaussians();
                calibration.loadCalibrationFile(calibrationFilePath);
                getSupport().firePropertyChange("ITDFilter_robothead6DOF.numOfBins", numOfBins, calibration.getNumOfBins());
                numOfBins = calibration.getNumOfBins();
            }
            //log.info("create Bins with averagingDecay=" + averagingDecay + " and calibration file");
            myBins = new ITDBins(averagingDecay * 1000000, numLoop, calibration);
        }
        if ((display == true) && (frame != null)) {
            frame.binsPanel.updateBins(myBins);
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(1, 1, 1);
        gl.glRasterPos3f(0, 0, 0);
        if (showAnnotations == true) {
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("avgITD(us)=%s", fmt.format(bestITD)));
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ITDConfidence=%f", avgITDConfidence));
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ILD=%f", ILD));
            if ((useLaterSpikeForWeight == true) || (usePriorSpikeForWeight == true)) {
                glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  lastWeight=%f", lastWeight));
            }
        }
        if ((display == true) && (frame != null)) {
            //frame.setITD(avgITD);
            frame.setText(String.format("avgITD(us)=%s   ITDConfidence=%f   ILD=%f", fmt.format(bestITD), avgITDConfidence, ILD));
        }
        gl.glPopMatrix();
    }

    public void annotate(float[][][] frame) {
        throw new UnsupportedOperationException("Not supported yet, use openGL rendering.");
    }

    public void annotate(Graphics2D g) {
        throw new UnsupportedOperationException("Not supported yet, use openGL rendering..");
    }

    /**
     * Returns the ITDBins object.
     *
     * @return the ITDBins object.
     */
    public ITDBins getITDBins() {
        return myBins;
    }

    /**
     * @return the beamFormingEnabled
     */
    public boolean isBeamFormingEnabled() {
        return beamFormingEnabled;
    }

    /**
     * @param beamFormingEnabled the beamFormingEnabled to set
     */
    public void setBeamFormingEnabled(boolean beamFormingEnabled) {
        this.beamFormingEnabled = beamFormingEnabled;
        putBoolean("ITDFilter_robothead6DOF.beamFormingEnabled", beamFormingEnabled);
    }

    /**
     * @return the beamFormingRangeUs
     */
    public int getBeamFormingRangeUs() {
        return beamFormingRangeUs;
    }

    /**
     * @param beamFormingRangeUs the beamFormingRangeUs to set
     */
    public void setBeamFormingRangeUs(int beamFormingRangeUs) {
        this.beamFormingRangeUs = beamFormingRangeUs;
        putFloat("ITDFilter_robothead6DOF.beamFormingRangeUs", beamFormingRangeUs);
    }

    /**
     * @return the beamFormingITDUs
     */
    public float getBeamFormingITDUs() {
        return beamFormingITDUs;
    }

    /**
     * @param beamFormingITDUs the beamFormingITDUs to set
     */
    public void setBeamFormingITDUs(float beamFormingITDUs) {
        this.beamFormingITDUs = beamFormingITDUs;
        putFloat("ITDFilter_robothead6DOF.beamFormingITDUs", beamFormingITDUs);
    }

    /**
     * @return the sendITD_UDP_port
     */
    public String getSendITD_UDP_port() {
        return sendITD_UDP_port;
    }

    /**
     * @param sendITD_UDP_port the sendITD_UDP_port to set
     */
    public final void setSendITD_UDP_port(String sendITD_UDP_port) { // TODO call in constructor
        try {
            String[] parts = sendITD_UDP_port.split(":");
            if (parts.length != 2) {
                log.warning(sendITD_UDP_port + " is not a valid hostname:port address");
                return;
            }
            String host = parts[0];
            try {
                int port = Integer.parseInt(parts[1]);
                client = new InetSocketAddress(host, port);
            } catch (NumberFormatException e) {
                log.warning(parts[1] + " is not a valid port number in " + sendITD_UDP_port);
                return;
            }
            this.sendITD_UDP_port = sendITD_UDP_port;
            putString("sendITD_UDP_port", sendITD_UDP_port);
            log.info("set client to " + client);
        } catch (Exception e) {
            log.warning("caught exception " + e.toString());
        }
    }

    /**
     * @return the sendITD_UDP_Messages
     */
    public boolean isSendITD_UDP_Messages() {
        return sendITD_UDP_Messages;
    }

    /**
     * @param sendITD_UDP_Messages the sendITD_UDP_Messages to set
     */
    public final synchronized void setSendITD_UDP_Messages(boolean sendITD_UDP_Messages) { // TODO call this in constructor to set up socket
        boolean old = this.sendITD_UDP_Messages;
        if (sendITD_UDP_Messages) {
            try {
                channel = DatagramChannel.open();
                socket = channel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
                this.sendITD_UDP_Messages = sendITD_UDP_Messages;
                putBoolean("ITDFilter_robothead6DOF.sendITD_UDP_Messages", sendITD_UDP_Messages);
                packetSequenceNumber = 0;

            } catch (IOException ex) {
                log.warning("couldn't get datagram channel: " + ex.toString());
            }

        } else {
            this.sendITD_UDP_Messages = sendITD_UDP_Messages;
            if (socket != null) {
                socket.close();
            }
            try {
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
        getSupport().firePropertyChange("ITDFilter_robothead6DOF.sendITD_UDP_Messages", old, this.sendITD_UDP_Messages);
        printedFirstUdpMessage = false;
    }
}
