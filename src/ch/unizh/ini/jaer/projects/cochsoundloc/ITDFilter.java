/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent;
import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent.Ear;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent;
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
import java.util.concurrent.ArrayBlockingQueue;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

/**
 * Extracts interaural time difference (ITD) from a binaural cochlea input.
 * 
 * @author Holger
 */
public class ITDFilter extends EventFilter2D implements Observer, FrameAnnotater {

    public static String getDescription() {
        return "Measures ITD (Interaural time difference) using a variety of methods";
    }
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
    private boolean writeBin2File = getPrefs().getBoolean("ITDFilter.writeBin2File", false);
    private boolean invert = getPrefs().getBoolean("ITDFilter.invert", false);
    private boolean write2FileForEverySpike = getPrefs().getBoolean("ITDFilter.write2FileForEverySpike", false);
    private boolean weigthFrequencies = getPrefs().getBoolean("ITDFilter.weigthFrequencies", false);
    private boolean normToConfThresh = getPrefs().getBoolean("ITDFilter.normToConfThresh", false);
    private boolean showAnnotations = getPrefs().getBoolean("ITDFilter.showAnnotations", false);
    private int confidenceThreshold = getPrefs().getInt("ITDFilter.confidenceThreshold", 30);
    private int activityThreshold = getPrefs().getInt("ITDFilter.activityThreshold", 30);
    private int numLoopMean = getPrefs().getInt("ITDFilter.numLoopMean", 2);
    private int numOfCochleaChannels = getPrefs().getInt("ITDFilter.numOfCochleaChannels", 32);
    private boolean useCalibration = getPrefs().getBoolean("ITDFilter.useCalibration", false);
    private String calibrationFilePath = getPrefs().get("ITDFilter.calibrationFilePath", null);
    private double[] frequencyWeigths;
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
    private int avgITD;
    private float avgITDConfidence = 0;
    private float ILD;
    EngineeringFormat fmt = new EngineeringFormat();
    FileWriter fstream;
    FileWriter fstreamBins;
    BufferedWriter ITDFile;
    BufferedWriter AvgITDFile;
    BufferedWriter BinFile;
    private boolean wasMoving = false;
    private int numNeuronTypes = 1;
    private static ArrayBlockingQueue ITDEventQueue = null;
    private boolean ITDEventQueueFull = false;
    public PanTilt panTilt = null;

    private double ConfidenceRecentMax = 0;
    private int    ConfidenceRecentMaxTime = 0;
    private double ConfidenceRecentMin = 1e6;
    private int    ConfidenceRecentMinTime = 0;
    private boolean ConfidenceRising = true;

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
        setPropertyTooltip("weigthFrequencies", "Read weigths for the frequencies from a csv-file");
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

        addPropertyToGroup("ITDWeighting", "useLaterSpikeForWeight");
        addPropertyToGroup("ITDWeighting", "usePriorSpikeForWeight");
        addPropertyToGroup("ITDWeighting", "maxWeight");
        addPropertyToGroup("ITDWeighting", "maxWeightTime");
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
            return in;
        }
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
            log.info("clear bins!");
        }
        checkOutputPacketEventType(in);
        if (in.isEmpty()) {
            return in;
        }

        int nleft = 0, nright = 0;
        for (Object e : in) {
            BinauralCochleaEvent i = (BinauralCochleaEvent) e;
            int ganglionCellThreshold;
            if (hasMultipleGanglionCellTypes &&
                    (this.amsProcessingMethod == AMSprocessingMethod.NeuronsIndividually ||
                    this.amsProcessingMethod == AMSprocessingMethod.StoreSeparetlyCompareEvery)) {
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
                    ear = (ear+1)%2;
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
                        if (java.lang.Math.abs(diff) < maxITD) {
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
                            if (this.weigthFrequencies && frequencyWeigths != null) {
                                lastWeight*=frequencyWeigths[i.x];
                            }
                            if (this.normToConfThresh == true) {
                                myBins.addITD(diff, i.timestamp, i.x, lastWeight, this.confidenceThreshold);
                            } else {
                                myBins.addITD(diff, i.timestamp, i.x, lastWeight, 0);
                            }
                            if (this.writeITD2File == true && ITDFile != null) {
                                ITDFile.write(i.timestamp + "\t" + diff + "\t" + i.x + "\t" + lastWeight + "\n");
                            }
                            if (this.sendITDsToOtherThread) {
                                if (ITDEventQueue==null) {
                                    ITDEventQueue = new ArrayBlockingQueue(this.itdEventQueueSize);
                                }
                                ITDEvent itdEvent = new ITDEvent(diff, i.timestamp, i.x, lastWeight);
                                boolean success = ITDEventQueue.offer(itdEvent);
                                if (success == false) {
                                    ITDEventQueueFull = true;
                                    log.warning("Could not add ITD-Event to the ITDEventQueue. Probably itdEventQueueSize is too small!!!");
                                }
                                else
                                {
                                    ITDEventQueueFull = false;
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
                            AvgITDFile.write(i.timestamp + "\t" + avgITD + "\t" + avgITDConfidence + "\n");
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
                    AvgITDFile.write(in.getLastTimestamp() + "\t" + avgITD + "\t" + avgITDConfidence + "\n");
                }
                if (this.writeBin2File == true && BinFile != null) {
                    BinFile.write(in.getLastTimestamp() + "\t" + myBins.toString() + "\n");
                }
            }
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

        if (this.ConfidenceRising) {
            if (avgITDConfidence>this.ConfidenceRecentMax) {
                this.ConfidenceRecentMax=avgITDConfidence;
                this.ConfidenceRecentMaxTime=myBins.getTimestamp();
            }
            else {
                if (this.ConfidenceRecentMaxTime + 3e5 < myBins.getTimestamp()) {
                    if (myBins.getBin((int) myBins.convertITD2BIN(avgITDtemp)) > activityThreshold) {
                        if (avgITDConfidence > confidenceThreshold) {
                            //Speech Detected!
                            if (frame != null) {
                                this.frame.binsPanel.setLocalisedPos(avgITDtemp);
                            }
                            this.ConfidenceRising = false;
                        
                            avgITD = avgITDtemp;
                            if (connectToPanTiltThread == true) {
                                CommObjForPanTilt filterOutput = new CommObjForPanTilt();
                                filterOutput.setFromCochlea(true);
                                filterOutput.setPanOffset((float) avgITD);
                                filterOutput.setConfidence(avgITDConfidence);
                                panTilt.offerBlockingQ(filterOutput);
                            }
                        }

                    }
                }
            }
        }
        else {
            if (avgITDConfidence<this.ConfidenceRecentMin) {
                this.ConfidenceRecentMin=avgITDConfidence;
                this.ConfidenceRecentMinTime=myBins.getTimestamp();
            }
            else {
                if (this.ConfidenceRecentMinTime + 1e5 < myBins.getTimestamp()) {
                    //Min detected;
                    this.ConfidenceRising=true;
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
        support.firePropertyChange("maxITD", this.maxITD, maxITD);
        this.maxITD = maxITD;
        createBins();
    }

    public int getNumOfBins() {
        return this.numOfBins;
    }

    public void setNumOfBins(int numOfBins) {
        getPrefs().putInt("ITDFilter.numOfBins", numOfBins);
        support.firePropertyChange("numOfBins", this.numOfBins, numOfBins);
        this.numOfBins = numOfBins;
        createBins();
    }

    public int getMaxWeight() {
        return this.maxWeight;
    }

    public void setMaxWeight(int maxWeight) {
        getPrefs().putInt("ITDFilter.maxWeight", maxWeight);
        support.firePropertyChange("maxWeight", this.maxWeight, maxWeight);
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

    public int getMaxWeightTime() {
        return this.maxWeightTime;
    }

    public void setMaxWeightTime(int maxWeightTime) {
        getPrefs().putInt("ITDFilter.maxWeightTime", maxWeightTime);
        support.firePropertyChange("maxWeightTime", this.maxWeightTime, maxWeightTime);
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
        support.firePropertyChange("dimLastTs", this.dimLastTs, dimLastTs);
        //lastTs = new int[numOfCochleaChannels][numNeuronTypes][2][dimLastTs];
        this.dimLastTs = dimLastTs;
        this.initFilter();
    }

    public int getNumLoopMean() {
        return this.numLoopMean;
    }

    public void setNumLoopMean(int numLoopMean) {
        getPrefs().putInt("ITDFilter.numLoopMean", numLoopMean);
        support.firePropertyChange("numLoopMean", this.numLoopMean, numLoopMean);
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
        support.firePropertyChange("numOfCochleaChannels", this.numOfCochleaChannels, numOfCochleaChannels);
        this.numOfCochleaChannels = numOfCochleaChannels;
        lastTs = new int[numOfCochleaChannels][numNeuronTypes][2][dimLastTs];
        lastTsCursor = new int[numOfCochleaChannels][numNeuronTypes][2];
    }

    public float getAveragingDecay() {
        return this.averagingDecay;
    }

    public void setAveragingDecay(float averagingDecay) {
        getPrefs().putDouble("ITDFilter.averagingDecay", averagingDecay);
        support.firePropertyChange("averagingDecay", this.averagingDecay, averagingDecay);
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
        support.firePropertyChange("display", old, display);
    }

    /** Adds button to show display */
    public void doResetITDDisplay() {
        frame.binsPanel.maxActivity = 0f;
    }

    public void doConnectToPanTiltThread() {
        panTilt = PanTilt.findExistingPanTiltThread(chip.getAeViewer());
        if (panTilt==null) {
            panTilt = new PanTilt();
            panTilt.initPanTilt();
        }
        this.connectToPanTiltThread = true;
    }

    public void doFitGaussianToBins(){
        numOfBins=myBins.getNumOfBins();
        double xData[] = new double[numOfBins];
        double yData[] = new double[numOfBins];
        for(int i=0;i<numOfBins;i++) {
            xData[i]=i;
        }
        for(int i=0;i<numOfBins;i++) {
            yData[i]=myBins.getBin(i);
        }
        //double yData[] = {4,6,5.6,3,2,1,0.1,1,1.4,1,0.6,1,1,0,0.5,0};
        flanagan.analysis.Regression reg = new flanagan.analysis.Regression(xData, yData);
        reg.gaussian();
        double[] regResult = reg.getBestEstimates();
        log.info("mean="+regResult[0]+" standardDeviation="+regResult[1]+" scale="+regResult[2]);
    }

    public boolean isDisplay() {
        return this.display;
    }

    public void setDisplay(boolean display) {
        getPrefs().putBoolean("ITDFilter.display", display);
        support.firePropertyChange("display", this.display, display);
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
        support.firePropertyChange("useLaterSpikeForWeight", this.useLaterSpikeForWeight, useLaterSpikeForWeight);
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
        support.firePropertyChange("usePriorSpikeForWeight", this.usePriorSpikeForWeight, usePriorSpikeForWeight);
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

                    // Create file
                    fstreamBins = new FileWriter(path);
                    BinFile = new BufferedWriter(fstreamBins);
                    String titles = "time\t";
                    for (int i = 0; i < this.numOfBins; i++) {
                        titles += "Bin" + i + "\t";
                    }
                    BinFile.write(titles);

                    getPrefs().putBoolean("ITDFilter.writeBin2File", writeBin2File);
                    support.firePropertyChange("writeBin2File", this.writeBin2File, writeBin2File);
                    this.writeBin2File = writeBin2File;
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
    }

    public boolean isWriteAvgITD2File() {
        return this.writeAvgITD2File;
    }

    public void setWriteAvgITD2File(boolean writeAvgITD2File) {
        getPrefs().putBoolean("ITDFilter.writeAvgITD2File", writeAvgITD2File);
        support.firePropertyChange("writeAvgITD2File", this.writeAvgITD2File, writeAvgITD2File);
        this.writeAvgITD2File = writeAvgITD2File;

        if (writeAvgITD2File == true) {
            try {
                JFileChooser fc = new JFileChooser();
                fc.setDialogType(JFileChooser.SAVE_DIALOG);
                int state = fc.showSaveDialog(null);
                if (state == JFileChooser.APPROVE_OPTION) {
                    String path = fc.getSelectedFile().getPath();

                    // Create file
                    fstream = new FileWriter(path);
                    AvgITDFile = new BufferedWriter(fstream);
                    AvgITDFile.write("time\tITD\tconf\n");

                    getPrefs().putBoolean("ITDFilter.writeAvgITD2File", writeAvgITD2File);
                    support.firePropertyChange("writeAvgITD2File", this.writeAvgITD2File, writeAvgITD2File);
                    this.writeAvgITD2File = writeAvgITD2File;
                }
            } catch (Exception e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        } else if (AvgITDFile != null) {
            try {
                //Close the output stream
                AvgITDFile.close();
                AvgITDFile = null;
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
        if (frame!= null) {
            this.frame.binsPanel.setDisplaySoundDetected(displaySoundDetected);
        }
    }

    public boolean isDisplayNormalize() {
        return this.displayNormalize;
    }

    public void setDisplayNormalize(boolean displayNormalize) {
        getPrefs().putBoolean("ITDFilter.displayNormalize", displayNormalize);
        support.firePropertyChange("displayNormalize", this.displayNormalize, displayNormalize);
        this.displayNormalize = displayNormalize;
        if (frame!= null) {
            this.frame.binsPanel.setDisplayNormalize(displayNormalize);
        }
    }

    public boolean isWrite2FileForEverySpike() {
        return this.write2FileForEverySpike;
    }

    public void setWrite2FileForEverySpike(boolean write2FileForEverySpike) {
        getPrefs().putBoolean("ITDFilter.write2FileForEverySpike", write2FileForEverySpike);
        support.firePropertyChange("write2FileForEverySpike", this.write2FileForEverySpike, write2FileForEverySpike);
        this.write2FileForEverySpike = write2FileForEverySpike;
    }

    public boolean isWeigthFrequencies() {
        return this.weigthFrequencies;
    }

    public void setWeigthFrequencies(boolean weigthFrequencies) {
        getPrefs().putBoolean("ITDFilter.weigthFrequencies", weigthFrequencies);
        support.firePropertyChange("weigthFrequencies", this.weigthFrequencies, weigthFrequencies);
        this.weigthFrequencies = weigthFrequencies;
        if (weigthFrequencies) {
            JFileChooser fc = new JFileChooser();
            fc.setDialogType(JFileChooser.SAVE_DIALOG);
            int state = fc.showSaveDialog(null);
            if (state == JFileChooser.APPROVE_OPTION) {
                String path = fc.getSelectedFile().getPath();
                try {
                    frequencyWeigths = new double[64];
                    File file = new File(path);
                    BufferedReader bufRdr = new BufferedReader(new FileReader(file));
                    int row = 0;
                    String line = bufRdr.readLine();
                    StringTokenizer st = new StringTokenizer(line, ",");
                    while (st.hasMoreTokens()) {
                        frequencyWeigths[row]=Double.parseDouble(st.nextToken());
                        row++;
                    }
                    bufRdr.close();
                } catch (IOException ex) {
                    log.warning("while loading weigths, caught exception " + ex);
                    ex.printStackTrace();
                }
            }
        }
    }

    public boolean isNormToConfThresh() {
        return this.normToConfThresh;
    }

    public void setNormToConfThresh(boolean normToConfThresh) {
        getPrefs().putBoolean("ITDFilter.normToConfThresh", normToConfThresh);
        support.firePropertyChange("normToConfThresh", this.normToConfThresh, normToConfThresh);
        this.normToConfThresh = normToConfThresh;
    }

    public boolean isShowAnnotations() {
        return this.showAnnotations;
    }

    public void setShowAnnotations(boolean showAnnotations) {
        getPrefs().putBoolean("ITDFilter.showAnnotations", showAnnotations);
        support.firePropertyChange("showAnnotations", this.showAnnotations, showAnnotations);
        this.showAnnotations = showAnnotations;
    }

    public boolean isComputeMeanInLoop() {
        return this.useLaterSpikeForWeight;
    }

    public void setComputeMeanInLoop(boolean computeMeanInLoop) {
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

    public boolean isUseCalibration() {
        return this.useCalibration;
    }

    public void setUseCalibration(boolean useCalibration) {
        getPrefs().putBoolean("ITDFilter.useCalibration", useCalibration);
        support.firePropertyChange("useCalibration", this.useCalibration, useCalibration);
        this.useCalibration = useCalibration;
        createBins();
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
        support.firePropertyChange("calibrationFilePath", this.calibrationFilePath, calibrationFilePath);
        this.calibrationFilePath = calibrationFilePath;
        getPrefs().put("ITDFilter.calibrationFilePath", calibrationFilePath);
    }

    public EstimationMethod getEstimationMethod() {
        return estimationMethod;
    }

    synchronized public void setEstimationMethod(EstimationMethod estimationMethod) {
        support.firePropertyChange("estimationMethod", this.estimationMethod, estimationMethod);
        getPrefs().put("ITDfilter.estimationMethod", estimationMethod.toString());
        this.estimationMethod = estimationMethod;
    }

    public AMSprocessingMethod getAmsProcessingMethod() {
        return amsProcessingMethod;
    }

    synchronized public void setAmsProcessingMethod(AMSprocessingMethod amsProcessingMethod) {
        support.firePropertyChange("amsProcessingMethod", this.amsProcessingMethod, amsProcessingMethod);
        getPrefs().put("ITDfilter.amsProcessingMethod", amsProcessingMethod.toString());
        this.amsProcessingMethod = amsProcessingMethod;
        this.initFilter();
    }

    public CochleaAMSEvent.FilterType getUseGanglionCellType() {
        return useGanglionCellType;
    }

    synchronized public void setUseGanglionCellType(CochleaAMSEvent.FilterType useGanglionCellType) {
        support.firePropertyChange("useGanglionCellType", this.useGanglionCellType, useGanglionCellType);
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
                support.firePropertyChange("numOfBins", this.numOfBins, calibration.getNumOfBins());
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
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("avgITD(us)=%s", fmt.format(avgITD)));
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ITDConfidence=%f", avgITDConfidence));
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ILD=%f", ILD));
            if (useLaterSpikeForWeight == true || usePriorSpikeForWeight == true) {
                glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  lastWeight=%f", lastWeight));
            }
        }
        if (display == true && frame != null) {
            //frame.setITD(avgITD);
            frame.setText(String.format("avgITD(us)=%s   ITDConfidence=%f   ILD=%f", fmt.format(avgITD), avgITDConfidence, ILD));
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
    public ITDBins getITDBins(){
        return myBins;
    }
}
