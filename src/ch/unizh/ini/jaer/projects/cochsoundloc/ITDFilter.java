/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent;
import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent.Ear;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.*;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;
import java.io.*;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ArrayBlockingQueue;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

/**
 * Extracts interaural time difference (ITD) from a binaural cochlea input.
 * 
 * @author Holger
 */
@Description("Measures ITD (Interaural time difference) using a variety of methods")
public class ITDFilter extends EventFilter2D implements Observer, FrameAnnotater, RemoteControlled {

    private ITDCalibrationGaussians calibration = null;
    private float averagingDecay = getPrefs().getFloat("ITDFilter.averagingDecay", 1);
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
    /// beamforming
    private boolean beamFormingEnabled = getBoolean("beamFormingEnabled", false);
    private int beamFormingRangeUs = getInt("beamFormingRangeUs", 100);
    private float beamFormingITDUs = getFloat("beamFormingITDUs", Float.NaN);
    // UDP messages
    private String sendITD_UDP_port = getString("sendITD_UDP_port", "localhost:9999");
    private boolean sendITD_UDP_Messages = getBoolean("sendITD_UDP_Messages", false);
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
    /** filled in with measured best ITD according to selected method (max, median, mean) */
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
    public PanTilt panTilt = null;
    private ITDBins[] freqBins;
    private double ConfidenceRecentMax = 0;
    private int ConfidenceRecentMaxTime = 0;
    private double ConfidenceRecentMin = 1e6;
    private int ConfidenceRecentMinTime = 0;
    private boolean ConfidenceRising = true;
 
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s", 3);
        if (tok.length < 2) {
            return "not enough arguments\n";
        }

        try {
            if (tok[1].equals("saveitd")) {
                if (tok.length < 2) {
                    return "not enough arguments\n";
                } else {
                    startAvgITD2File(tok[2]);
                    return "starting to save itds\n";
                }
            }
            if (tok[1].equals("saveitdandreset")) {
                if (tok.length < 2) {
                    return "not enough arguments\n";
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
                String filename = tok[2];
                log.info("save bins to: " + filename);
                if (tok.length < 3) {
                    return "not enough arguments\n";
                } else {
                    startWriteBin2File(filename);
                    return "starting to save bins\n";
                }
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
                    return "not enough arguments\n";
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
        return "not a valid command.";
    }

    private void startAvgITD2File(String path) throws IOException {
        // Create file
        fstream = new FileWriter(path);
        AvgITDFile = new BufferedWriter(fstream);
        AvgITDFile.write("time\tITD\tconf\n");
        getPrefs().putBoolean("ITDFilter.writeAvgITD2File", true);
        getSupport().firePropertyChange("writeAvgITD2File", this.writeAvgITD2File, true);
        this.writeAvgITD2File = true;
    }

    private void startSaveFreq(String path) throws IOException {
        freqBins = new ITDBins[64];
        for (int i = 0; i < 64; i++) {
            freqBins[i] = new ITDBins((float) averagingDecay * 1000000, 1, maxITD, numOfBins);
        }

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
        getSupport().firePropertyChange("saveFrequenciesSeperately", this.saveFrequenciesSeperately, true);
        this.saveFrequenciesSeperately = true;
    }

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
                freqBins[i].updateTime(0, myBins.getTimestamp());
                if (freqBinFile != null) {
                    try {
                        BinFile.write(myBins.getTimestamp() + "\t" + i + "\t" + freqBins[i].toString() + "\n");
                    } catch (IOException ex) {
                        Logger.getLogger(ITDFilter.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } else {
            String titles = "time\t";
            for (int i = 0; i < this.numOfBins; i++) {
                titles += "Bin" + i + "\t";
            }
            titles += "\n";
            BinFile.write(titles);
            BinFile.write(0 + "\t" + myBins.toString() + "\n");
        }
        BinFile.close();

    }

    /**
     * Returns the overall best ITD. See {@link #getITDBins()} to access the histogram data and statistics.
     * Filled in with measured best ITD according to selected method (max, median, mean)
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
    private EstimationMethod estimationMethod = EstimationMethod.valueOf(getPrefs().get("ITDFilter.estimationMethod", "useMax"));

    public enum AMSprocessingMethod {

        NeuronsIndividually, AllNeuronsTogether, StoreSeparetlyCompareEvery
    };
    private AMSprocessingMethod amsProcessingMethod = AMSprocessingMethod.valueOf(getPrefs().get("ITDFilter.amsProcessingMethod", "NeuronsIndividually"));
//    public enum UseGanglionCellType {
//        LPF, BPF
//    };
    private CochleaAMSEvent.FilterType useGanglionCellType = CochleaAMSEvent.FilterType.valueOf(getPrefs().get("ITDFilter.useGanglionCellType", "LPF"));
    private boolean hasMultipleGanglionCellTypes = false;
//    private ActionListener updateBinFrame = new ActionListener() {
//
//        public void actionPerformed(ActionEvent evt) {
//                //wasMoving = false;
//            }
//        };
//    private javax.swing.Timer timer = new javax.swing.Timer(5, updateBinFrame);
    private float averagingDecayTmp = 0;

    public ITDFilter(AEChip chip) {
        super(chip);
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
            chip.getRemoteControl().addCommandListener(this, "itdfilter", "Testing remotecontrol of itdfilter.");
        }

        setSendITD_UDP_port(sendITD_UDP_port);
        setSendITD_UDP_Messages(sendITD_UDP_Messages); // init port for datagram if used
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (connectToPanTiltThread) {
            CommObjForITDFilter commObjIncomming = panTilt.pollBlockingQForITDFilter();
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
                        averagingDecayTmp = this.averagingDecay;
                        this.setAveragingDecay(0);
                        break;
                    case 4:
                        this.setAveragingDecay(averagingDecayTmp);
                        break;
                    case 5:
                        this.myBins.clear();
                        break;
                }
                commObjIncomming = panTilt.pollBlockingQForITDFilter();
            }
        }
        if (connectToPanTiltThread && (panTilt.isMoving() || panTilt.isWasMoving())) {
            this.wasMoving = true;
            return in;
        }
        if (this.wasMoving == true) {
            this.wasMoving = false;
            this.myBins.clear();
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
                    && (this.amsProcessingMethod == AMSprocessingMethod.NeuronsIndividually
                    || this.amsProcessingMethod == AMSprocessingMethod.StoreSeparetlyCompareEvery)) {
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

                if (this.invert) {
                    ear = (ear + 1) % 2;
                }

                if (i.x >= numOfCochleaChannels) {
                    log.warning("there was a BasicEvent i with i.x=" + i.x + " >= " + numOfCochleaChannels + "=numOfCochleaChannels! Therefore set numOfCochleaChannels=" + (i.x + 1));
                    setNumOfCochleaChannels(i.x + 1);
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
                                lastWeight *= ((weightTimeThisSide * (maxWeight - 1f)) / (float) maxWeightTime) + 1f;
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
                                lastWeight *= ((weightTimeOtherSide * (maxWeight - 1f)) / (float) maxWeightTime) + 1f;
                                if (weightTimeOtherSide < 0) {
                                    //log.warning("weightTimeOtherSide < 0");
                                    lastWeight = 0;
                                }
                            }
                            if (this.weightFrequencies && frequencyWeights != null) {
                                lastWeight *= frequencyWeights[i.x];
                            }
                            if (this.normToConfThresh == true) {
                                myBins.addITD(diff, i.timestamp, i.x, lastWeight, this.confidenceThreshold);
                            } else {
                                myBins.addITD(diff, i.timestamp, i.x, lastWeight, 0);
                            }
                            if (freqBins != null) {
                                freqBins[i.x].addITD(diff, i.timestamp, i.x, lastWeight, 0);
                            }
                            if (this.writeITD2File == true && ITDFile != null) {
                                ITDFile.write(i.timestamp + "\t" + diff + "\t" + i.x + "\t" + lastWeight + "\n");
                            }
                            if (this.sendITDsToOtherThread) {
                                if (ITDEventQueue == null) {
                                    ITDEventQueue = new ArrayBlockingQueue(this.itdEventQueueSize);
                                }
                                ITDEvent itdEvent = new ITDEvent(diff, i.timestamp, i.x, lastWeight);
                                boolean success = ITDEventQueue.offer(itdEvent);
                                if (success == false) {
                                    ITDEventQueueFull = true;
                                    log.warning("Could not add ITD-Event to the ITDEventQueue. Probably itdEventQueueSize is too small!!!");
                                } else {
                                    ITDEventQueueFull = false;
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

                    RubiEcho.time = i.timestamp;

                    if (this.write2FileForEverySpike == true) {
                        if (this.writeAvgITD2File == true && AvgITDFile != null) {
                            refreshITD();
                            AvgITDFile.write(i.timestamp + "\t" + bestITD + "\t" + avgITDConfidence + "\n");
                        }
                        if (this.writeBin2File == true && BinFile != null) {
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
            if (this.normToConfThresh == true) {
                myBins.updateTime(this.confidenceThreshold, in.getLastTimestamp());
            } else {
                myBins.updateTime(0, in.getLastTimestamp());
            }

            refreshITD();
            ILD = (float) (nleft - nright) / (float) (nright + nleft); //Max ILD is 1 (if only one side active)
            if (this.write2FileForEverySpike == false) {
                if (this.writeAvgITD2File == true && AvgITDFile != null) {
                    AvgITDFile.write(in.getLastTimestamp() + "\t" + bestITD + "\t" + avgITDConfidence + "\n");
                }
                if (this.writeBin2File == true && BinFile != null) {
                    BinFile.write(in.getLastTimestamp() + "\t" + myBins.toString() + "\n");
                }
            }
        } catch (Exception e) {
            log.warning("In filterPacket caught exception " + e);
            e.printStackTrace();
        }
        
        // send udp messages
        long now = System.currentTimeMillis();
        if ((now - lastUdpMsgTime > MIN_UPD_PACKET_INTERVAL_MS) && sendITD_UDP_Messages && client != null) {
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

        if (this.ConfidenceRising) {
            if (avgITDConfidence > this.ConfidenceRecentMax) {
                this.ConfidenceRecentMax = avgITDConfidence;
                this.ConfidenceRecentMaxTime = myBins.getTimestamp();
            } else {
                if (this.ConfidenceRecentMaxTime + timeLocalExtremaDetection < myBins.getTimestamp()) {
                    //if (myBins.getBin((int) myBins.convertITD2BIN(avgITDtemp)) > activityThreshold) {
                    if (avgITDConfidence > confidenceThreshold) {
                        //Speech Detected!
                        if (frame != null) {
                            this.frame.binsPanel.setLocalisedPos(avgITDtemp);
                        }
                        this.ConfidenceRising = false;

                        bestITD = avgITDtemp;

                        if (this.saveFrequenciesSeperately) {
                            for (int i = 0; i < 64; i++) {
                                freqBins[i].updateTime(0, myBins.getTimestamp());
                                if (freqBinFile != null) {
                                    try {
                                        freqBinFile.write(myBins.getTimestamp() + "\t" + i + "\t" + freqBins[i].toString() + "\n");
                                    } catch (IOException ex) {
                                        Logger.getLogger(ITDFilter.class.getName()).log(Level.SEVERE, null, ex);
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
                                filterOutput.setPanOffset((float) servopos * 2f - 1f);
                            } else if (use1DRegression) {
                                double servopos = ridgeWeights[0] + (myBins.getITDMaxIndex() + 1) * ridgeWeights[1];
                                filterOutput.setPanOffset((float) servopos * 2f - 1f);
                            } else {
                                filterOutput.setPanOffset((float) bestITD / (float) this.maxITD);
                            }
                            filterOutput.setConfidence(avgITDConfidence);
                            panTilt.offerBlockingQ(filterOutput);
                        }

                        this.ConfidenceRecentMin = 100000000;
                        this.ConfidenceRecentMax = 0;
                    }

                    //}
                }
            }
        } else {
            if (avgITDConfidence < this.ConfidenceRecentMin) {
                this.ConfidenceRecentMin = avgITDConfidence;
                this.ConfidenceRecentMinTime = myBins.getTimestamp();
            } else {
                if (this.ConfidenceRecentMinTime + timeLocalExtremaDetection < myBins.getTimestamp()) {
                    //Min detected;
                    this.ConfidenceRising = true;
                    this.ConfidenceRecentMin = 100000000;
                    this.ConfidenceRecentMax = 0;
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
                dim = this.numNeuronTypes;
                break;
            case StoreSeparetlyCompareEvery:
                dim = this.numNeuronTypes;
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

    @Override
    public void update(Observable o, Object arg) {
        if (arg != null) {
//            log.info("ITDFilter.update() is called from " + o + " with arg=" + arg);
            if (arg.equals("eventClass")) {
                if (chip.getEventClass() == CochleaAMSEvent.class) {
                    hasMultipleGanglionCellTypes = true;
                    this.numNeuronTypes = 4;
                } else {
                    hasMultipleGanglionCellTypes = false;
                    this.numNeuronTypes = 1;
                }
                this.initFilter();
            }
        }
    }

    public int getMaxITD() {
        return this.maxITD;
    }

    public void setMaxITD(int maxITD) {
        getPrefs().putInt("ITDFilter.maxITD", maxITD);
        getSupport().firePropertyChange("maxITD", this.maxITD, maxITD);
        this.maxITD = maxITD;
        createBins();
    }

    public int getNumOfBins() {
        return this.numOfBins;
    }

    public void setNumOfBins(int numOfBins) {
        getPrefs().putInt("ITDFilter.numOfBins", numOfBins);
        getSupport().firePropertyChange("numOfBins", this.numOfBins, numOfBins);
        this.numOfBins = numOfBins;
        createBins();
    }

    public int getMaxWeight() {
        return this.maxWeight;
    }

    public void setMaxWeight(int maxWeight) {
        getPrefs().putInt("ITDFilter.maxWeight", maxWeight);
        getSupport().firePropertyChange("maxWeight", this.maxWeight, maxWeight);
        this.maxWeight = maxWeight;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public int getConfidenceThreshold() {
        return this.confidenceThreshold;
    }

    public void setConfidenceThreshold(int confidenceThreshold) {
        getPrefs().putInt("ITDFilter.confidenceThreshold", confidenceThreshold);
        getSupport().firePropertyChange("confidenceThreshold", this.confidenceThreshold, confidenceThreshold);
        this.confidenceThreshold = confidenceThreshold;
    }

    public int getActivityThreshold() {
        return this.activityThreshold;
    }

    public void setActivityThreshold(int activityThreshold) {
        getPrefs().putInt("ITDFilter.activityThreshold", activityThreshold);
        getSupport().firePropertyChange("activityThreshold", this.activityThreshold, activityThreshold);
        this.activityThreshold = activityThreshold;
    }

    public int getItdEventQueueSize() {
        return this.itdEventQueueSize;
    }

    public void setItdEventQueueSize(int itdEventQueueSize) {
        getPrefs().putInt("ITDFilter.itdEventQueueSize", itdEventQueueSize);
        getSupport().firePropertyChange("itdEventQueueSize", this.itdEventQueueSize, itdEventQueueSize);
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
        getSupport().firePropertyChange("timeLocalExtremaDetection", this.timeLocalExtremaDetection, timeLocalExtremaDetection);
        this.timeLocalExtremaDetection = timeLocalExtremaDetection;
    }

    public int getMaxWeightTime() {
        return this.maxWeightTime;
    }

    public void setMaxWeightTime(int maxWeightTime) {
        getPrefs().putInt("ITDFilter.maxWeightTime", maxWeightTime);
        getSupport().firePropertyChange("maxWeightTime", this.maxWeightTime, maxWeightTime);
        this.maxWeightTime = maxWeightTime;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public int getDimLastTs() {
        return this.dimLastTs;
    }

    public void setDimLastTs(int dimLastTs) {
        getPrefs().putInt("ITDFilter.dimLastTs", dimLastTs);
        getSupport().firePropertyChange("dimLastTs", this.dimLastTs, dimLastTs);
        //lastTs = new int[numOfCochleaChannels][numNeuronTypes][2][dimLastTs];
        this.dimLastTs = dimLastTs;
        this.initFilter();
    }

    public int getNumLoopMean() {
        return this.numLoopMean;
    }

    public void setNumLoopMean(int numLoopMean) {
        getPrefs().putInt("ITDFilter.numLoopMean", numLoopMean);
        getSupport().firePropertyChange("numLoopMean", this.numLoopMean, numLoopMean);
        this.numLoopMean = numLoopMean;
        if (!isFilterEnabled() || this.computeMeanInLoop == false) {
            return;
        }
        if (myBins == null) {
            createBins();
        } else {
            myBins.setNumLoopMean(numLoopMean);
        }
    }

    public int getNumOfCochleaChannels() {
        return this.numOfCochleaChannels;
    }

    public void setNumOfCochleaChannels(int numOfCochleaChannels) {
        getPrefs().putInt("ITDFilter.numOfCochleaChannels", numOfCochleaChannels);
        getSupport().firePropertyChange("numOfCochleaChannels", this.numOfCochleaChannels, numOfCochleaChannels);
        this.numOfCochleaChannels = numOfCochleaChannels;
        lastTs = new int[numOfCochleaChannels][numNeuronTypes][2][dimLastTs];
        lastTsCursor = new int[numOfCochleaChannels][numNeuronTypes][2];
    }

    public float getAveragingDecay() {
        return this.averagingDecay;
    }

    public void setAveragingDecay(float averagingDecay) {
        getPrefs().putDouble("ITDFilter.averagingDecay", averagingDecay);
        getSupport().firePropertyChange("averagingDecay", this.averagingDecay, averagingDecay);
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

    /** Adds button to show display */
    public void doToggleITDDisplay() {
        boolean old = isDisplay();
        setDisplay(!isDisplay());
        getSupport().firePropertyChange("display", old, display);
    }

    /** Adds button to show display */
    public void doResetITDDisplay() {
        frame.binsPanel.maxActivity = 0f;
    }

    public void doConnectToPanTiltThread() {
        panTilt = PanTilt.findExistingPanTiltThread(chip.getAeViewer());
        if (panTilt == null) {
            panTilt = new PanTilt();
            panTilt.initPanTilt();
        }
        this.connectToPanTiltThread = true;
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
        //double yData[] = {4,6,5.6,3,2,1,0.1,1,1.4,1,0.6,1,1,0,0.5,0};
        flanagan.analysis.Regression reg = new flanagan.analysis.Regression(xData, yData);
        reg.gaussian();
        double[] regResult = reg.getBestEstimates();
        log.info("mean=" + regResult[0] + " standardDeviation=" + regResult[1] + " scale=" + regResult[2]);
    }

    public boolean isDisplay() {
        return this.display;
    }

    public void setDisplay(boolean display) {
        getPrefs().putBoolean("ITDFilter.display", display);
        getSupport().firePropertyChange("display", this.display, display);
        this.display = display;
        if (!isFilterEnabled()) {
            return;
        }
        if (display == false && frame != null) {
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
//                            ITDFilter.this.display = false; // set this so we know that itdframe has been disposed so that next button press on doToggleITDDisplay works correctly
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
        return this.useLaterSpikeForWeight;
    }

    public void setUseLaterSpikeForWeight(boolean useLaterSpikeForWeight) {
        getPrefs().putBoolean("ITDFilter.useLaterSpikeForWeight", useLaterSpikeForWeight);
        getSupport().firePropertyChange("useLaterSpikeForWeight", this.useLaterSpikeForWeight, useLaterSpikeForWeight);
        this.useLaterSpikeForWeight = useLaterSpikeForWeight;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public boolean isUsePriorSpikeForWeight() {
        return this.usePriorSpikeForWeight;
    }

    public void setUsePriorSpikeForWeight(boolean usePriorSpikeForWeight) {
        getPrefs().putBoolean("ITDFilter.usePriorSpikeForWeight", usePriorSpikeForWeight);
        getSupport().firePropertyChange("usePriorSpikeForWeight", this.usePriorSpikeForWeight, usePriorSpikeForWeight);
        this.usePriorSpikeForWeight = usePriorSpikeForWeight;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public boolean isWriteBin2File() {
        return this.writeBin2File;
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
        getPrefs().putBoolean("ITDFilter.writeBin2File", writeBin2File);
        getSupport().firePropertyChange("writeBin2File", this.writeBin2File, writeBin2File);
        this.writeBin2File = writeBin2File;
    }

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
            freqBins = null;
        }
        getPrefs().putBoolean("ITDFilter.saveFrequenciesSeperately", saveFrequenciesSeperately);
        getSupport().firePropertyChange("saveFrequenciesSeperately", this.saveFrequenciesSeperately, saveFrequenciesSeperately);
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
                getSupport().firePropertyChange("writeAvgITD2File", this.writeAvgITD2File, writeAvgITD2File);
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
        getSupport().firePropertyChange("writeITD2File", this.writeITD2File, writeITD2File);
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
                    getSupport().firePropertyChange("writeITD2File", this.writeITD2File, writeITD2File);
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
        getSupport().firePropertyChange("sendITDsToOtherThread", this.sendITDsToOtherThread, sendITDsToOtherThread);
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
        getSupport().firePropertyChange("displaySoundDetected", this.displaySoundDetected, displaySoundDetected);
        this.displaySoundDetected = displaySoundDetected;
        if (frame != null) {
            this.frame.binsPanel.setDisplaySoundDetected(displaySoundDetected);
        }
    }

    public boolean isDisplayNormalize() {
        return this.displayNormalize;
    }

    public void setDisplayNormalize(boolean displayNormalize) {
        getPrefs().putBoolean("ITDFilter.displayNormalize", displayNormalize);
        getSupport().firePropertyChange("displayNormalize", this.displayNormalize, displayNormalize);
        this.displayNormalize = displayNormalize;
        if (frame != null) {
            this.frame.binsPanel.setDisplayNormalize(displayNormalize);
        }
    }

    public boolean isWrite2FileForEverySpike() {
        return this.write2FileForEverySpike;
    }

    public void setWrite2FileForEverySpike(boolean write2FileForEverySpike) {
        getPrefs().putBoolean("ITDFilter.write2FileForEverySpike", write2FileForEverySpike);
        getSupport().firePropertyChange("write2FileForEverySpike", this.write2FileForEverySpike, write2FileForEverySpike);
        this.write2FileForEverySpike = write2FileForEverySpike;
    }

    public boolean isWeightFrequencies() {
        return this.weightFrequencies;
    }

    public void setWeightFrequencies(boolean weightFrequencies) {
        getPrefs().putBoolean("ITDFilter.weightFrequencies", weightFrequencies);
        getSupport().firePropertyChange("weightFrequencies", this.weightFrequencies, weightFrequencies);
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
        getSupport().firePropertyChange("use1DRegression", this.use1DRegression, use1DRegression);
        this.use1DRegression = use1DRegression;
    }

    public boolean isUseRidgeRegression() {
        return this.useRidgeRegression;
    }

    public void setUseRidgeRegression(boolean useRidgeRegression) {
        if (useRidgeRegression) {
            if (freqBins == null) {
                freqBins = new ITDBins[64];
                for (int i = 0; i < 64; i++) {
                    freqBins[i] = new ITDBins((float) averagingDecay * 1000000, 1, maxITD, numOfBins);
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
        getPrefs().putBoolean("ITDFilter.useRidgeRegression", useRidgeRegression);
        getSupport().firePropertyChange("useRidgeRegression", this.useRidgeRegression, useRidgeRegression);
        this.useRidgeRegression = useRidgeRegression;
    }

    public boolean isNormToConfThresh() {
        return this.normToConfThresh;
    }

    public void setNormToConfThresh(boolean normToConfThresh) {
        getPrefs().putBoolean("ITDFilter.normToConfThresh", normToConfThresh);
        getSupport().firePropertyChange("normToConfThresh", this.normToConfThresh, normToConfThresh);
        this.normToConfThresh = normToConfThresh;
    }

    public boolean isShowAnnotations() {
        return this.showAnnotations;
    }

    public void setShowAnnotations(boolean showAnnotations) {
        getPrefs().putBoolean("ITDFilter.showAnnotations", showAnnotations);
        getSupport().firePropertyChange("showAnnotations", this.showAnnotations, showAnnotations);
        this.showAnnotations = showAnnotations;
    }

    public boolean isComputeMeanInLoop() {
        return this.useLaterSpikeForWeight;
    }

    public void setComputeMeanInLoop(boolean computeMeanInLoop) {
        getPrefs().putBoolean("ITDFilter.computeMeanInLoop", computeMeanInLoop);
        getSupport().firePropertyChange("computeMeanInLoop", this.computeMeanInLoop, computeMeanInLoop);
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
        return this.useCalibration;
    }

    public void setUseCalibration(boolean useCalibration) {
        getPrefs().putBoolean("ITDFilter.useCalibration", useCalibration);
        getSupport().firePropertyChange("useCalibration", this.useCalibration, useCalibration);
        this.useCalibration = useCalibration;
        createBins();
    }

    public boolean isInvert() {
        return this.invert;
    }

    public void setInvert(boolean invert) {
        getPrefs().putBoolean("ITDFilter.invert", invert);
        getSupport().firePropertyChange("invert", this.invert, invert);
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
        getSupport().firePropertyChange("calibrationFilePath", this.calibrationFilePath, calibrationFilePath);
        this.calibrationFilePath = calibrationFilePath;
        getPrefs().put("ITDFilter.calibrationFilePath", calibrationFilePath);
    }

    public EstimationMethod getEstimationMethod() {
        return estimationMethod;
    }

    synchronized public void setEstimationMethod(EstimationMethod estimationMethod) {
        getSupport().firePropertyChange("estimationMethod", this.estimationMethod, estimationMethod);
        getPrefs().put("ITDfilter.estimationMethod", estimationMethod.toString());
        this.estimationMethod = estimationMethod;
    }

    public AMSprocessingMethod getAmsProcessingMethod() {
        return amsProcessingMethod;
    }

    synchronized public void setAmsProcessingMethod(AMSprocessingMethod amsProcessingMethod) {
        getSupport().firePropertyChange("amsProcessingMethod", this.amsProcessingMethod, amsProcessingMethod);
        getPrefs().put("ITDfilter.amsProcessingMethod", amsProcessingMethod.toString());
        this.amsProcessingMethod = amsProcessingMethod;
        this.initFilter();
    }

    public CochleaAMSEvent.FilterType getUseGanglionCellType() {
        return useGanglionCellType;
    }

    synchronized public void setUseGanglionCellType(CochleaAMSEvent.FilterType useGanglionCellType) {
        getSupport().firePropertyChange("useGanglionCellType", this.useGanglionCellType, useGanglionCellType);
        getPrefs().put("ITDfilter.useGanglionCellType", useGanglionCellType.toString());
        this.useGanglionCellType = useGanglionCellType;
    }

    public static ITDEvent takeITDEvent() throws InterruptedException {
        return (ITDEvent) ITDEventQueue.take();
    }

    public static ITDEvent pollITDEvent() {
        if (ITDEventQueue != null) {
            return (ITDEvent) ITDEventQueue.poll();
        } else {
            return null;
        }
    }

    private void createBins() {
        int numLoop;
        if (this.computeMeanInLoop == true) {
            numLoop = numLoopMean;
        } else {
            numLoop = 1;
        }
        if (useCalibration == false) {
            //log.info("create Bins with averagingDecay=" + averagingDecay + " and maxITD=" + maxITD + " and numOfBins=" + numOfBins);
            myBins = new ITDBins((float) averagingDecay * 1000000, numLoop, maxITD, numOfBins);
        } else {
            if (calibration == null) {
                calibration = new ITDCalibrationGaussians();
                calibration.loadCalibrationFile(calibrationFilePath);
                getSupport().firePropertyChange("numOfBins", this.numOfBins, calibration.getNumOfBins());
                this.numOfBins = calibration.getNumOfBins();
            }
            //log.info("create Bins with averagingDecay=" + averagingDecay + " and calibration file");
            myBins = new ITDBins((float) averagingDecay * 1000000, numLoop, calibration);
        }
        if (display == true && frame != null) {
            frame.binsPanel.updateBins(myBins);
        }
    }

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
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("avgITD(us)=%s", fmt.format(bestITD)));
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ITDConfidence=%f", avgITDConfidence));
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ILD=%f", ILD));
            if (useLaterSpikeForWeight == true || usePriorSpikeForWeight == true) {
                glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  lastWeight=%f", lastWeight));
            }
        }
        if (display == true && frame != null) {
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

    /** Returns the ITDBins object.
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
        putBoolean("beamFormingEnabled", beamFormingEnabled);
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
        putFloat("beamFormingRangeUs", beamFormingRangeUs);
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
        putFloat("beamFormingITDUs", beamFormingITDUs);
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
            log.info("set client to "+client);
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
                putBoolean("sendITD_UDP_Messages", sendITD_UDP_Messages);
                packetSequenceNumber=0;
                
            } catch (IOException ex) {
                log.warning("couldn't get datagram channel: " + ex.toString());
            }

        } else {
            this.sendITD_UDP_Messages=sendITD_UDP_Messages;
            if(socket!=null) socket.close();
            try {
                if(channel!=null) channel.close();
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
        getSupport().firePropertyChange("sendITD_UDP_Messages", old, this.sendITD_UDP_Messages);
        printedFirstUdpMessage=false;
    }
}
