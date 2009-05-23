/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.holger;

import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent;
import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent.Ear;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
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
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

/**
 * Extracts interaural time difference (ITD) from a binaural cochlea input.
 * 
 * @author Holger
 */
public class ITDFilter extends EventFilter2D implements Observer, FrameAnnotater {

   public static String getDescription(){
        return "Measures ITD (Interaural time difference) using a variety of methods";
    }
    private PanTiltControl pantilt;
    private ITDCalibrationGaussians calibration = null;
    private float averagingDecay = getPrefs().getFloat("ITDFilter.averagingDecay", 1000000);
    private int maxITD = getPrefs().getInt("ITDFilter.maxITD", 800);
    private int numOfBins = getPrefs().getInt("ITDFilter.numOfBins", 16);
    private int maxWeight = getPrefs().getInt("ITDFilter.maxWeight", 5);
    private int dimLastTs = getPrefs().getInt("ITDFilter.dimLastTs", 4);
    private int maxWeightTime = getPrefs().getInt("ITDFilter.maxWeightTime", 500000);
    private boolean display = getPrefs().getBoolean("ITDFilter.display", false);
    private boolean useLaterSpikeForWeight = getPrefs().getBoolean("ITDFilter.useLaterSpikeForWeight", true);
    private boolean usePriorSpikeForWeight = getPrefs().getBoolean("ITDFilter.usePriorSpikeForWeight", true);
    private boolean computeMeanInLoop = getPrefs().getBoolean("ITDFilter.computeMeanInLoop", true);
    private boolean writeITD2File = getPrefs().getBoolean("ITDFilter.writeITD2File", false);
    private boolean writeBin2File = getPrefs().getBoolean("ITDFilter.writeBin2File", false);
    private boolean normToConfThresh = getPrefs().getBoolean("ITDFilter.normToConfThresh", false);
    private boolean usePanTilt = getPrefs().getBoolean("ITDFilter.usePanTilt", false);
    private boolean logPanTiltResponse = getPrefs().getBoolean("ITDFilter.logPanTiltResponse", false);
    private int confidenceThreshold = getPrefs().getInt("ITDFilter.confidenceThreshold", 30);
    private int numLoopMean = getPrefs().getInt("ITDFilter.numLoopMean", 2);
    private int numOfCochleaChannels = getPrefs().getInt("ITDFilter.numOfCochleaChannels", 32);
    private int panTiltPos = getPrefs().getInt("ITDFilter.panTiltPos", 0);
    private boolean useCalibration = getPrefs().getBoolean("ITDFilter.useCalibration", false);
    private String calibrationFilePath = getPrefs().get("ITDFilter.calibrationFilePath", null);
    private String pantiltPort = getPrefs().get("ITDFilter.pantiltPort", "COM9");
    private String pantiltCommand = getPrefs().get("ITDFilter.pantiltCommand", "H");
    ITDFrame frame;
    private ITDBins myBins;
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
    BufferedWriter BinFile;
    private boolean isMoving = false;
    private boolean wasMoving = false;
    private long lastTimeMotorMovement = 0;
    private int numNeuronTypes = 1;
    public enum EstimationMethod {
        useMedian, useMean, useMax
    };
    private EstimationMethod estimationMethod = EstimationMethod.valueOf(getPrefs().get("ITDFilter.estimationMethod", "useMedian"));
    public enum AMSprocessingMethod {
        NeuronsIndividually, AllNeuronsTogether, StoreSeparetlyCompareEvery
    };
    private AMSprocessingMethod amsProcessingMethod = AMSprocessingMethod.valueOf(getPrefs().get("ITDFilter.amsProcessingMethod", "NeuronsIndividually"));
//    public enum UseGanglionCellType {
//        LPF, BPF
//    };
    private CochleaAMSEvent.FilterType useGanglionCellType = CochleaAMSEvent.FilterType.valueOf(getPrefs().get("ITDFilter.useGanglionCellType", "LPF"));
    private boolean hasMultipleGanglionCellTypes=false;

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
        setPropertyTooltip("averagingDecay", "The decay constant of the fade out of old ITDs (in us)");
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
        setPropertyTooltip("writeITD2File", "Write the ITD-values to a File");
        setPropertyTooltip("writeBin2File", "Write the Bin-values to a File");
        setPropertyTooltip("SelectCalibrationFile", "select the xml file which can be created by matlab");
        setPropertyTooltip("calibrationFilePath", "Full path to xml calibration file");
        setPropertyTooltip("estimationMethod", "Method used to compute the ITD");
        setPropertyTooltip("useGanglionCellType", "If CochleaAMS which Ganglion cells to use");
        setPropertyTooltip("amsProcessingMethod", "If CochleaAMS how to process different neurons");
        setPropertyTooltip("numLoopMean", "Method used to compute the ITD");
        setPropertyTooltip("numOfCochleaChannels", "The number of frequency channels of the cochleae");
        setPropertyTooltip("normToConfThresh", "Normalize the bins before every spike to the value of the confidence Threshold");
        setPropertyTooltip("usePanTilt", "PanTilt");
        setPropertyTooltip("logPanTiltResponse", "PanTilt to log");
        setPropertyTooltip("pantiltPort", "PanTilt COM Port");
        setPropertyTooltip("panTiltPos", "Set PanTilt to this Position");
        setPropertyTooltip("pantiltCommand", "PanTilt-Command to execute");
        setPropertyTooltip("SetPanSpeed1000", "Set the speed to 1000");
        setPropertyTooltip("PanTiltHalt", "send the halt command to the pan-tilt-unit");
        setPropertyTooltip("SetPanTiltPos", "Set the selected position");
        setPropertyTooltip("ExecutePanTiltCommand", "execute the command");
        setPropertyTooltip("ToggleITDDisplay","Toggles graphical display of ITD");
        addPropertyToGroup("PanTiltUnit", "pantiltCommand");
        addPropertyToGroup("PanTiltUnit", "panTiltPos");
        addPropertyToGroup("PanTiltUnit", "pantiltPort");
        addPropertyToGroup("PanTiltUnit", "logPanTiltResponse");
        addPropertyToGroup("PanTiltUnit", "usePanTilt");

        addPropertyToGroup("ITDWeighting", "useLaterSpikeForWeight");
        addPropertyToGroup("ITDWeighting", "usePriorSpikeForWeight");
        addPropertyToGroup("ITDWeighting", "maxWeight");
        addPropertyToGroup("ITDWeighting", "maxWeightTime");
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
        if (usePanTilt==true && pantilt != null && pantilt.isConnected()) {
            String pantiltResponse = pantilt.getResponse();
            if (logPanTiltResponse==true && !pantiltResponse.isEmpty()) {
                log.info(pantiltResponse);
            }
            if (pantilt.isMoving() == true) {
                return in;
            } else if (this.isMoving == true) { //that means it was moving
                this.wasMoving = true;
                this.isMoving = false;
                lastTimeMotorMovement = System.currentTimeMillis();
                createBins();
                return in;
            } else if (this.wasMoving == true) {
                if (lastTimeMotorMovement + 500 < System.currentTimeMillis()) {
                    this.wasMoving = false;
                    log.info("restarting ITD detection!");
                }
                else
                {
                    return in;
                }
            }
        }
        if (!isFilterEnabled() || in.getSize() == 0) {
            return in;
        }
        checkOutputPacketEventType(in);

        int nleft = 0, nright = 0;
        for (Object e : in) {
            BinauralCochleaEvent i = (BinauralCochleaEvent) e;
            int ganglionCellThreshold;
            if(hasMultipleGanglionCellTypes &&
                    (this.amsProcessingMethod==AMSprocessingMethod.NeuronsIndividually ||
                    this.amsProcessingMethod==AMSprocessingMethod.StoreSeparetlyCompareEvery)){
                CochleaAMSEvent camsevent=((CochleaAMSEvent)i);
                ganglionCellThreshold=camsevent.getThreshold();
                if (useGanglionCellType != camsevent.getFilterType()) {
                    continue;
                }
            }else{
                  ganglionCellThreshold=0;
            }
            try {
                int ear;
                if (i.getEar()==Ear.RIGHT)
                    ear=0;
                else
                    ear=1;
                //log.info("ear="+i.getEar()+" type="+i.getType());
                if (i.x >= numOfCochleaChannels) {
                    log.warning("there was a BasicEvent i with i.x=" + i.x + " >= " + numOfCochleaChannels + "=numOfCochleaChannels! Therefore set numOfCochleaChannels=" + (i.x + 1));
                    setNumOfCochleaChannels(i.x + 1);
                } else {
                    int cursor = lastTsCursor[i.x][ganglionCellThreshold][1 - ear];
                    do {
                        int diff = i.timestamp - lastTs[i.x][ganglionCellThreshold][1 - ear][cursor];     // compare actual ts with last complementary ts of that channel
                        // x = channel y = side!!
                        if (ear == 0) {
                            diff = -diff;     // to distingiuish plus- and minus-delay
                            nright++;
                        } else {
                            nleft++;
                        }
                        if (java.lang.Math.abs(diff) < maxITD) {
                            lastWeight = 1f;
                            //Compute weight:
                            if (useLaterSpikeForWeight == true) {
                                int weightTimeThisSide = i.timestamp -lastTs[i.x][ganglionCellThreshold][ear][lastTsCursor[i.x][ganglionCellThreshold][ear]];
                                if (weightTimeThisSide > maxWeightTime) {
                                    weightTimeThisSide = maxWeightTime;
                                }
                                lastWeight *= ((weightTimeThisSide * (maxWeight - 1f)) / (float) maxWeightTime) + 1f;
                                if (weightTimeThisSide < 0) {
                                    log.warning("weightTimeThisSide < 0");
                                    lastWeight=0;
                                }
                            }
                            if (usePriorSpikeForWeight == true) {
                                int weightTimeOtherSide = lastTs[i.x][ganglionCellThreshold][1 - ear][cursor] - lastTs[i.x][ganglionCellThreshold][1 - ear][(cursor + 1) % dimLastTs];
                                if (weightTimeOtherSide > maxWeightTime) {
                                    weightTimeOtherSide = maxWeightTime;
                                }
                                lastWeight *= ((weightTimeOtherSide * (maxWeight - 1f)) / (float) maxWeightTime) + 1f;
                                if (weightTimeOtherSide < 0) {
                                    log.warning("weightTimeOtherSide < 0");
                                    lastWeight=0;
                                }
                            }
                            if (this.normToConfThresh == true) {
                                myBins.addITD(diff, i.timestamp, i.x, lastWeight, this.confidenceThreshold);
                            } else {
                                myBins.addITD(diff, i.timestamp, i.x, lastWeight, 0);
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

                    if (this.writeITD2File == true) {
                        refreshITD();
                        ITDFile.write(i.timestamp + "\t" + avgITD + "\t" + avgITDConfidence + "\n");
                    }
                    if (this.writeBin2File == true) {
                        refreshITD();
                        BinFile.write(i.timestamp + "\t" + myBins.toString() + "\n");
                    }
                }

            } catch (Exception e1) {
                log.warning("In for-loop in filterPacket caught exception " + e1);
                e1.printStackTrace();
            }
        }
        try {
            refreshITD();
            if (display == true && frame != null) {
                frame.setITD(avgITD);
            }
            ILD = (float) (nright - nleft) / (float) (nright + nleft); //Max ILD is 1 (if only one side active)
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
        if (avgITDConfidence > confidenceThreshold) {
            avgITD = avgITDtemp;
            if (this.usePanTilt == true && java.lang.Math.abs(pantilt.getPanPosTransformed()-avgITD)>200) {
                pantilt.setPanPosTransformed(avgITD);
                isMoving = true;
                log.info("set pan pos to" + avgITD);
            }
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
        log.info("init() called");
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

        //Now fill the lastTs with Integer.MIN_VALUE:
        //But this isn't working, because fill creates references not values!
//        Arrays.fill(lastTs[0][0][0], Integer.MIN_VALUE);
//        Arrays.fill(lastTs[0][0], lastTs[0][0][0]);
//        Arrays.fill(lastTs[0], lastTs[0][0]);
//        Arrays.fill(lastTs, lastTs[0]);
        for (int i = 0; i < numOfCochleaChannels; i++) {
            for (int j = 0; j < dim; j++) {
                for (int k = 0; k < 2; k++) {
                    for (int l = 0; l < dimLastTs; l++) {
                        lastTs[i][j][k][l] = Integer.MIN_VALUE;
                    }
                }
            }
        }


        if (isFilterEnabled()) {
            createBins();
            setDisplay(display);
        }
    }

    @Override
    public void setFilterEnabled(boolean yes) {
        log.info("ITDFilter.setFilterEnabled() is called");
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
            log.info("ITDFilter.update() is called from " + o + " with arg=" + arg);
            if (arg.equals("eventClass")) {
                if (chip.getEventClass() == CochleaAMSEvent.class) {
                    hasMultipleGanglionCellTypes = true;
                    this.numNeuronTypes=4;
                } else {
                    hasMultipleGanglionCellTypes = false;
                    this.numNeuronTypes=1;
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
            myBins.setAveragingDecay(averagingDecay);
        }
    }

    /** Adds button to show display */
    public void doToggleITDDisplay(){
        boolean old=isDisplay();
        setDisplay(!isDisplay());
        support.firePropertyChange("display",old, display);
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
            frame.setVisible(false);
            frame = null;
        } else if (display == true) {
            if (frame == null) {
                try {
                    frame = new ITDFrame();
                    frame.binsPanel.updateBins(myBins);
                    log.info("ITD-Jframe created with height=" + frame.getHeight() + " and width:" + frame.getWidth());
                } catch (Exception e) {
                    log.warning("while creating ITD-Jframe, caught exception " + e);
                    e.printStackTrace();
                }
            }
            frame.setVisible(true);
        }
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

    public boolean getLogPanTiltResponse() {
        return this.logPanTiltResponse;
    }

    public void setLogPanTiltResponse(boolean logPanTiltResponse) {
        this.logPanTiltResponse = logPanTiltResponse;
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
        getPrefs().putBoolean("ITDFilter.writeBin2File", writeBin2File);
        support.firePropertyChange("writeBin2File", this.writeBin2File, writeBin2File);
        this.writeBin2File = writeBin2File;
        if (writeBin2File == true) {
            try {
                // Create file
                fstreamBins = new FileWriter("BinOutput.dat");
                BinFile = new BufferedWriter(fstreamBins);
                String titles = "time\t";
                for (int i=0; i<this.numOfBins; i++) {
                    titles += "Bin" + i + "\t";
                }
                BinFile.write(titles);
            } catch (Exception e) {//Catch exception if any
                System.err.println("Error: " + e.getMessage());
            }
        } else {
            try {
                //Close the output stream
                BinFile.close();
            } catch (Exception e) {//Catch exception if any
                System.err.println("Error: " + e.getMessage());
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
                // Create file
                fstream = new FileWriter("ITDoutput.dat");
                ITDFile = new BufferedWriter(fstream);
                ITDFile.write("time\tITD\tconf\n");
            } catch (Exception e) {//Catch exception if any
                System.err.println("Error: " + e.getMessage());
            }
        } else {
            try {
                //Close the output stream
                ITDFile.close();
            } catch (Exception e) {//Catch exception if any
                System.err.println("Error: " + e.getMessage());
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

    public boolean isUsePanTilt() {
        return this.usePanTilt;
    }

    public void setUsePanTilt(boolean usePanTilt) {
        getPrefs().putBoolean("ITDFilter.usePanTilt", usePanTilt);
        support.firePropertyChange("usePanTilt", this.usePanTilt, usePanTilt);
        this.usePanTilt = usePanTilt;
        if (usePanTilt == true) {
            if (pantilt == null) {
                pantilt = new PanTiltControl();
            }
            if (pantilt.isConnected() == false) {
                try {
                    pantilt.connect(this.pantiltPort);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void doSetPanSpeed1000() {
        pantilt.setPanSpeed(1000);
    }

    public void doPanTiltHalt() {
        pantilt.halt();
    }
        
    public void doSetPanTiltPos() {
        pantilt.setPanPosTransformed(this.panTiltPos);
    }

    public void doExecutePanTiltCommand() {
        pantilt.executeCommand(this.pantiltCommand);
    }

	public int getMinPanTiltPos(){
        return -800;
    }

    public int getMaxPanTiltPos(){
        return 800;
    }

    public int getPanTiltPos() {
        return this.panTiltPos;
    }

    public void setPanTiltPos(int panTiltPos) {
        getPrefs().putInt("ITDFilter.panTiltPos", panTiltPos);
        support.firePropertyChange("panTiltPos", this.panTiltPos, panTiltPos);
        this.panTiltPos = panTiltPos;
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

    public String getPantiltPort() {
        return pantiltPort;
    }

    public void setpantiltPort(String pantiltPort) {
        support.firePropertyChange("pantiltPort", this.pantiltPort, pantiltPort);
        this.pantiltPort = pantiltPort;
        getPrefs().put("ITDFilter.pantiltPort", pantiltPort);
    }

    public String getPantiltCommand() {
        return pantiltCommand;
    }

    public void setPantiltCommand(String pantiltCommand) {
        support.firePropertyChange("pantiltCommand", this.pantiltCommand, pantiltCommand);
        this.pantiltCommand = pantiltCommand;
        getPrefs().put("ITDFilter.pantiltCommand", pantiltCommand);
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


    private void createBins() {
        int numLoop;
        if (this.computeMeanInLoop == true) {
            numLoop = numLoopMean;
        } else {
            numLoop = 1;
        }
        if (useCalibration == false) {
            //log.info("create Bins with averagingDecay=" + averagingDecay + " and maxITD=" + maxITD + " and numOfBins=" + numOfBins);
            myBins = new ITDBins((float) averagingDecay, numLoop, maxITD, numOfBins);
        } else {
            if (calibration == null) {
                calibration = new ITDCalibrationGaussians();
                calibration.loadCalibrationFile(calibrationFilePath);
                support.firePropertyChange("numOfBins", this.numOfBins, calibration.getNumOfBins());
                this.numOfBins = calibration.getNumOfBins();
            //getPrefs().putInt("numOfBins", this.numOfBins);
            }
            //log.info("create Bins with averagingDecay=" + averagingDecay + " and calibration file");
            myBins = new ITDBins((float) averagingDecay, numLoop, calibration);
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
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("avgITD(us)=%s", fmt.format(avgITD)));
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ITDConfidence=%f", avgITDConfidence));
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ILD=%f", ILD));
        if (useLaterSpikeForWeight == true) {
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  lastWeight=%f", lastWeight));
        }
        gl.glPopMatrix();
    }

    public void annotate(float[][][] frame) {
        throw new UnsupportedOperationException("Not supported yet, use openGL rendering.");
    }

    public void annotate(Graphics2D g) {
        throw new UnsupportedOperationException("Not supported yet, use openGL rendering..");
    }
}
