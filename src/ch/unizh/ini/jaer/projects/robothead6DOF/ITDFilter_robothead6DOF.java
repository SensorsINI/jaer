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
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
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
public class ITDFilter_robothead6DOF extends EventFilter2D implements Observer, FrameAnnotater {
    
    protected static final Logger log = Logger.getLogger("ITDFilter_robothead6DOF");
    private ITDCalibrationGaussians calibration = null;
    private float averagingDecay = getPrefs().getFloat("averagingDecay", 1);
    private int maxITD = getPrefs().getInt("maxITD", 800);
    private int numOfBins = getPrefs().getInt("numOfBins", 16);
    private int maxWeight = getPrefs().getInt("maxWeight", 5);
    private int dimLastTs = getPrefs().getInt("dimLastTs", 4);
    private int maxWeightTime = getPrefs().getInt("maxWeightTime", 500000);
    private boolean display = getPrefs().getBoolean("display", false);
    private boolean displayNormalize = getPrefs().getBoolean("displayNormalize", true);
    private boolean displaySoundDetected = getPrefs().getBoolean("displaySoundDetected", true);
    private boolean useLaterSpikeForWeight = getPrefs().getBoolean("useLaterSpikeForWeight", true);
    private boolean usePriorSpikeForWeight = getPrefs().getBoolean("usePriorSpikeForWeight", true);
    private boolean computeMeanInLoop = getPrefs().getBoolean("computeMeanInLoop", true);
    private boolean sendITDsToOtherThread = getPrefs().getBoolean("sendITDsToOtherThread", false);
    private int itdEventQueueSize = getPrefs().getInt("itdEventQueueSize", 1000);
    private int timeLocalExtremaDetection = getPrefs().getInt("timeLocalExtremaDetection", 200000);
    private boolean invert = getPrefs().getBoolean("invert", false);
    private boolean weightFrequencies = getPrefs().getBoolean("weightFrequencies", false);
    private boolean showAnnotations = getPrefs().getBoolean("showAnnotations", false);
    private int confidenceThreshold = getPrefs().getInt("confidenceThreshold", 30);
    private int activityThreshold = getPrefs().getInt("activityThreshold", 30);
    private int numLoopMean = getPrefs().getInt("numLoopMean", 2);
    private int useLowerChannel = getPrefs().getInt("useLowerChannel", 0);
    private int useUpperChannel = getPrefs().getInt("useUpperChannel", 63);
    private int numOfCochleaChannels = 64;

    //////////////////////
    private double[] frequencyWeights;
    ITDFrame frame;
    private ITDBins myBins;
    private boolean connectToPanTiltThread = false;
    private int[][][][] lastTs;
    private int[][][] lastTsCursor;
    Iterator iterator;
    private float lastWeight = 1f;
    /** filled in with measured best ITD according to selected method (max, median, mean) */
    private int bestITD;
    private float avgITDConfidence = 0;
    private float ILD;
    EngineeringFormat fmt = new EngineeringFormat();
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
    private EstimationMethod estimationMethod = EstimationMethod.valueOf(getPrefs().get("estimationMethod", "useMax"));
    public enum AMSprocessingMethod {
        NeuronsIndividually, AllNeuronsTogether, StoreSeparetlyCompareEvery
    };
    private AMSprocessingMethod amsProcessingMethod = AMSprocessingMethod.valueOf(getPrefs().get("amsProcessingMethod", "NeuronsIndividually"));
    private CochleaAMSEvent.FilterType useGanglionCellType = CochleaAMSEvent.FilterType.valueOf(getPrefs().get("useGanglionCellType", "LPF"));
    private boolean hasMultipleGanglionCellTypes = false;
    private float averagingDecayTmp = 0;

    public ITDFilter_robothead6DOF(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);        
        headControl = new Head6DOF_ServoController(chip);       //add the filter to communicate with the UART port to this filter
        filterChain.add(headControl);
        setEnclosedFilterChain(filterChain);
        chip.addObserver(this);
        initFilter();
        setPropertyTooltip("useLowerChannel", "sets the lowest channel to use");
        setPropertyTooltip("useUpperChannel", "sets the highest channel to use");
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
                  log.warning("there was a BasicEvent i with i.x=" + i.x + " that was outside of the selceted channel range. Not working yet");
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
                            myBins.addITD(diff, i.timestamp, i.x, lastWeight, 0);
                            if (freqBins != null) {
                                freqBins[i.x].addITD(diff, i.timestamp, i.x, lastWeight, 0);
                            }
                            if (sendITDsToOtherThread) {        //offers this ITD to other threads using the Queue
                                if (ITDEventQueue == null) {
                                    ITDEventQueue = new ArrayBlockingQueue(itdEventQueueSize);
                                }
                                ITDEvent itdEvent = new ITDEvent(diff, i.timestamp, i.x, lastWeight);
                                if (itdEvent.getChannel() < 38) {      //used to filter out motor noise
                                    boolean success = ITDEventQueue.offer(bestITD);
                                    if (success == false) {
                                        ITDEventQueue.take();
                                    } else {
                                        ITDEventQueueFull = false;
                                    }
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
                }

            } catch (Exception e1) {
                log.warning("In for-loop in filterPacket caught exception " + e1);
                e1.printStackTrace();
            }
        }
        try {
            myBins.updateTime(0, in.getLastTimestamp());
            refreshITD();
            ILD = (float) (nleft - nright) / (float) (nright + nleft); //Max ILD is 1 (if only one side active)
        } catch (Exception e) {
            log.warning("In filterPacket caught exception " + e);
            e.printStackTrace();
        }
        return in;
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
                        if (connectToPanTiltThread == true) {

                            CommObjForPanTilt filterOutput = new CommObjForPanTilt();
                            filterOutput.setFromCochlea(true);
                            filterOutput.setPanOffset((float) bestITD / (float) maxITD);
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
        getPrefs().putInt("maxITD", maxITD);
        getSupport().firePropertyChange("maxITD", this.maxITD, maxITD);
        this.maxITD = maxITD;
        createBins();
    }

    public int getNumOfBins() {
        return numOfBins;
    }

    public void setNumOfBins(int numOfBins) {
        getPrefs().putInt("numOfBins", numOfBins);
        getSupport().firePropertyChange("numOfBins", this.numOfBins, numOfBins);
        this.numOfBins = numOfBins;
        createBins();
    }

    public int getMaxWeight() {
        return maxWeight;
    }

    public void setMaxWeight(int maxWeight) {
        getPrefs().putInt("maxWeight", maxWeight);
        getSupport().firePropertyChange("maxWeight", this.maxWeight, maxWeight);
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
        getPrefs().putInt("confidenceThreshold", confidenceThreshold);
        getSupport().firePropertyChange("confidenceThreshold", this.confidenceThreshold, confidenceThreshold);
        this.confidenceThreshold = confidenceThreshold;
    }

    public int getActivityThreshold() {
        return activityThreshold;
    }

    public void setActivityThreshold(int activityThreshold) {
        getPrefs().putInt("activityThreshold", activityThreshold);
        getSupport().firePropertyChange("activityThreshold", this.activityThreshold, activityThreshold);
        this.activityThreshold = activityThreshold;
    }

    public int getItdEventQueueSize() {
        return itdEventQueueSize;
    }

    public void setItdEventQueueSize(int itdEventQueueSize) {
        getPrefs().putInt("itdEventQueueSize", itdEventQueueSize);
        getSupport().firePropertyChange("itdEventQueueSize", this.itdEventQueueSize, itdEventQueueSize);
        this.itdEventQueueSize = itdEventQueueSize;
        if (sendITDsToOtherThread) {
            ITDEventQueue = new ArrayBlockingQueue(itdEventQueueSize);
        }
    }

    public int getTimeLocalExtremaDetection() {
        return timeLocalExtremaDetection;
    }

    public void setTimeLocalExtremaDetection(int timeLocalExtremaDetection) {
        getPrefs().putInt("timeLocalExtremaDetection", timeLocalExtremaDetection);
        getSupport().firePropertyChange("timeLocalExtremaDetection", this.timeLocalExtremaDetection, timeLocalExtremaDetection);
        this.timeLocalExtremaDetection = timeLocalExtremaDetection;
    }

    public int getMaxWeightTime() {
        return maxWeightTime;
    }

    public void setMaxWeightTime(int maxWeightTime) {
        getPrefs().putInt("maxWeightTime", maxWeightTime);
        getSupport().firePropertyChange("maxWeightTime", this.maxWeightTime, maxWeightTime);
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
        getPrefs().putInt("dimLastTs", dimLastTs);
        getSupport().firePropertyChange("dimLastTs", this.dimLastTs, dimLastTs);
        this.dimLastTs = dimLastTs;
        initFilter();
    }

    public int getNumLoopMean() {
        return numLoopMean;
    }

    public void setNumLoopMean(int numLoopMean) {
        getPrefs().putInt("numLoopMean", numLoopMean);
        getSupport().firePropertyChange("numLoopMean", this.numLoopMean, numLoopMean);
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
        getPrefs().putInt("useLowerChannel", useLowerChannel);
        getSupport().firePropertyChange("useLowerChannel", this.useLowerChannel, useLowerChannel);
        this.useLowerChannel = useLowerChannel;       
        numOfCochleaChannels = 64;
        lastTs = new int[numOfCochleaChannels][numNeuronTypes][2][dimLastTs];
        lastTsCursor = new int[numOfCochleaChannels][numNeuronTypes][2];
    }
    
    public int getUseUpperChannel() {
        return useUpperChannel;
    }

    public void setUseUpperChannel(int useUpperChannel) {
        getPrefs().putInt("numOfCochleaChannels", useUpperChannel);
        getSupport().firePropertyChange("numOfCochleaChannels", this.useUpperChannel, useUpperChannel);
        this.useUpperChannel = useUpperChannel;
        numOfCochleaChannels = 64;
        lastTs = new int[numOfCochleaChannels][numNeuronTypes][2][dimLastTs];
        lastTsCursor = new int[numOfCochleaChannels][numNeuronTypes][2];
    }
    

    public float getAveragingDecay() {
        return averagingDecay;
    }

    public void setAveragingDecay(float averagingDecay) {
        getPrefs().putDouble("averagingDecay", averagingDecay);
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

    /**
     * Adds button to show display
     */
    public void doToggleITDDisplay() {
        boolean old = isDisplay();
        setDisplay(!isDisplay());
        getSupport().firePropertyChange("display", old, display);
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

    public void connectToPanTiltThread(ITDImageCreator creator) {  //has to be called from a ITDImageCreator filter
        if(headControl.isConnected() == true){
        panTilt = PanTilt_robothead6DOF.findExistingPanTiltThread(chip.getAeViewer());  //find eventually avaible panTiltThreads
        if (panTilt == null) {
            ITDImageCreator = creator;
            panTilt = new PanTilt_robothead6DOF();  //create a new pan tilt thread
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

    public boolean isDisplay() {
        return display;
    }

    public void setDisplay(boolean display) {
        getPrefs().putBoolean("display", display);
        getSupport().firePropertyChange("display", this.display, display);
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
        getPrefs().putBoolean("useLaterSpikeForWeight", useLaterSpikeForWeight);
        getSupport().firePropertyChange("useLaterSpikeForWeight", this.useLaterSpikeForWeight, useLaterSpikeForWeight);
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
        getPrefs().putBoolean("usePriorSpikeForWeight", usePriorSpikeForWeight);
        getSupport().firePropertyChange("usePriorSpikeForWeight", this.usePriorSpikeForWeight, usePriorSpikeForWeight);
        this.usePriorSpikeForWeight = usePriorSpikeForWeight;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }
    
    public boolean isSendITDsToOtherThread() {
        return sendITDsToOtherThread;
    }

    public void setSendITDsToOtherThread(boolean sendITDsToOtherThread) {
        getPrefs().putBoolean("sendITDsToOtherThread", sendITDsToOtherThread);
        getSupport().firePropertyChange("sendITDsToOtherThread", this.sendITDsToOtherThread, sendITDsToOtherThread);
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
        getPrefs().putBoolean("displaySoundDetected", displaySoundDetected);
        getSupport().firePropertyChange("displaySoundDetected", this.displaySoundDetected, displaySoundDetected);
        this.displaySoundDetected = displaySoundDetected;
        if (frame != null) {
            frame.binsPanel.setDisplaySoundDetected(displaySoundDetected);
        }
    }

    public boolean isComputeMeanInLoop() {
        return useLaterSpikeForWeight;
    }

    public void setComputeMeanInLoop(boolean computeMeanInLoop) {
        getPrefs().putBoolean("computeMeanInLoop", computeMeanInLoop);
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

    public boolean isInvert() {
        return invert;
    }

    public void setInvert(boolean invert) {
        getPrefs().putBoolean("invert", invert);
        getSupport().firePropertyChange("invert", this.invert, invert);
        this.invert = invert;
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
        initFilter();
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

    public static Integer pollITDEvent() {
        if (ITDEventQueue != null) {
            ITDEventQueue.clear();  //clear the current ITD Queue and wait for a new ITD value
        }
        try {
            return (Integer) ITDEventQueue.poll(100, TimeUnit.SECONDS); //wait 100sec for new ITD Event
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
        myBins = new ITDBins(averagingDecay * 1000000, numLoop, maxITD, numOfBins);
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

    public ITDBins getITDBins() {
        return myBins;
    }
}
