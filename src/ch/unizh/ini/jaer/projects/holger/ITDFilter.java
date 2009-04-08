/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.holger;

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

/**
 * Extracts interaural time difference (ITD) from a binaural cochlea input.
 * 
 * @author Holger
 */
public class ITDFilter extends EventFilter2D implements Observer, FrameAnnotater {

    private float averagingDecay;
    private int maxITD;
    private int numOfBins;
    private int maxWeight;
    private int dimLastTs;
    private int maxWeightTime;
    private int confidenceThreshold;
    private boolean display;
    private boolean useWeights;
    private boolean useMedian;
    private boolean writeITD2File;
    ITDFrame frame;
    private int lastWeight = 0;
    private ITDBins myBins;
    //private LinkedList[][] lastTimestamps;
    //private ArrayList<LinkedList<Integer>> lastTimestamps0;
    //private ArrayList<LinkedList<Integer>> lastTimestamps1;
    private int[][][] lastTs;
    private int[][] lastTsCursor;
    //private int[][] AbsoluteLastTimestamp;
    Iterator iterator;
    private int avgITD;
    private float avgITDConfidence = 0;
    private float ILD;
    EngineeringFormat fmt = new EngineeringFormat();
    FileWriter fstream;
    BufferedWriter ITDFile;

    public ITDFilter(AEChip chip) {
        super(chip);
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
        lastTs = new int[32][2][dimLastTs];
        lastTsCursor = new int[32][2];
        //AbsoluteLastTimestamp = new int[32][2];
        setPropertyTooltip("averagingDecay", "The decay constant of the fade out of old ITDs (in us)");
        setPropertyTooltip("maxITD", "maximum ITD to compute in us");
        setPropertyTooltip("numOfBins", "total number of bins");
        setPropertyTooltip("dimLastTs", "how many lastTs save");
        setPropertyTooltip("maxWeight", "maximum weight for ITDs");
        setPropertyTooltip("maxWeightTime", "maximum time to use for weighting ITDs");
        setPropertyTooltip("display", "display bins");
        setPropertyTooltip("useWeights", "use weights for ITD");
        setPropertyTooltip("useMedian", "use median to compute average ITD");
        setPropertyTooltip("confidenceThreshold", "ITDs with confidence below this threshold are neglected");
        setPropertyTooltip("writeITD2File", "Write the ITD-values to a File");
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
        if (!isFilterEnabled() || in.getSize() == 0) {
            return in;
        }
        checkOutputPacketEventType(in);

        int nleft = 0, nright = 0;
        for (Object e : in) {
            BasicEvent i = (BasicEvent) e;
            try {
                int cursor = lastTsCursor[i.x][1 - i.y];
                do {
                    int diff = i.timestamp - lastTs[i.x][1 - i.y][cursor];     // compare actual ts with last complementary ts of that channel
                    // x = channel y = side!!
                    if (i.y == 0) {
                        diff = -diff;     // to distingiuish plus- and minus-delay
                        nright++;
                    } else {
                        nleft++;
                    }
                    if (java.lang.Math.abs(diff) < maxITD) {
                        if (useWeights == false) {
                            myBins.addITD(diff, i.timestamp);
                        } else {
                            //Compute weights
                            int weightTime = i.timestamp - lastTs[i.x][i.y][lastTsCursor[i.x][i.y]];
                            if (weightTime > maxWeightTime) {
                                weightTime = maxWeightTime;
                            }
                            lastWeight = ((weightTime * (maxWeight - 1)) / maxWeightTime) + 1;
                            //log.info("lastweight="+lastWeight);
                            for (int k = 0; k < lastWeight; k++) {
                                myBins.addITD(diff, i.timestamp);
                            }
                        }
                    } else {
                        break;
                    }
                    cursor = (++cursor) % dimLastTs;
                } while (cursor != lastTsCursor[i.x][1 - i.y]);
                //Now decrement the cursor (circularly)
                if (lastTsCursor[i.x][i.y] == 0) {
                    lastTsCursor[i.x][i.y] = dimLastTs;
                }
                lastTsCursor[i.x][i.y]--;
                //Add the new timestamp to the list
                lastTs[i.x][i.y][lastTsCursor[i.x][i.y]] = i.timestamp;

                if (this.writeITD2File == true) {
                    refreshITD();
                    ITDFile.write(i.timestamp+"\t" + avgITD + "\t"+ avgITDConfidence +"\n");
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
        int avgITDtemp;
        if (useMedian == false) {
            avgITDtemp = myBins.getMeanITD();
        } else {
            avgITDtemp = myBins.getMedianITD();
        }
        avgITDConfidence = myBins.getITDConfidence();
        if (avgITDConfidence > confidenceThreshold) {
            avgITD = avgITDtemp;
        }
        else
        {
            avgITD = java.lang.Integer.MAX_VALUE;
        }
    }

    public Object getFilterState() {
        return null;
    }

    public void resetFilter() {
        createBins();
        lastTs = new int[32][2][dimLastTs];
    }

    @Override
    public void initFilter() {
        log.info("init() called");
        averagingDecay = getPrefs().getFloat("ITDFilter.averagingDecay", 50000);
        maxITD = getPrefs().getInt("ITDFilter.maxITD", 800);
        numOfBins = getPrefs().getInt("ITDFilter.numOfBins", 16);
        maxWeight = getPrefs().getInt("ITDFilter.maxWeight", 5);
        dimLastTs = getPrefs().getInt("ITDFilter.dimLastTs", 4);
        maxWeightTime = getPrefs().getInt("ITDFilter.maxWeightTime", 30000);
        display = getPrefs().getBoolean("ITDFilter.display", false);
        useWeights = getPrefs().getBoolean("ITDFilter.useWeights", false);
        useMedian = getPrefs().getBoolean("ITDFilter.useMedian", true);
        writeITD2File = getPrefs().getBoolean("ITDFilter.writeITD2File", false);
        confidenceThreshold = getPrefs().getInt("ITDFilter.confidenceThreshold", 3);
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
            try {
                createBins();
            } catch (Exception e) {
                log.warning("In genBins() caught exception " + e);
                e.printStackTrace();
            }
            display = getPrefs().getBoolean("ITDFilter.display", false);
            setDisplay(display);
        }
    }

    public void update(Observable o, Object arg) {
        log.info("ITDFilter.update() is called");
    }

    public int getMaxITD() {
        return this.maxITD;
    }

    public void setMaxITD(int maxITD) {
        getPrefs().putInt("ITDFilter.shiftSize", maxITD);
        support.firePropertyChange("shiftSize", this.maxITD, maxITD);
        this.maxITD = maxITD;
        createBins();
    }

    public int getNumOfBins() {
        return this.numOfBins;
    }

    public void setNumOfBins(int numOfBins) {
        getPrefs().putInt("ITDFilter.binSize", numOfBins);
        support.firePropertyChange("binSize", this.numOfBins, numOfBins);
        this.numOfBins = numOfBins;
        createBins();
    }

    public int getMaxWeight() {
        return this.maxWeight;
    }

    public void setMaxWeight(int maxWeight) {
        getPrefs().putInt("ITDFilter.maxWeights", maxWeight);
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
        getPrefs().putInt("ITDFilter.maxWeights", confidenceThreshold);
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
        lastTs = new int[32][2][dimLastTs];
        this.dimLastTs = dimLastTs;
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
        createBins();
    }

    public boolean isDisplay() {
        return this.display;
    }

    public void setDisplay(boolean display) {
        this.display = display;
        if (!isFilterEnabled()) {
            return;
        }
        if (display == false && frame != null) {
            frame.setVisible(false);
            frame = null;
        }
        else if (display == true) {
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

    public boolean isUseWeights() {
        return this.useWeights;
    }

    public void setUseWeights(boolean useWeights) {
        log.info("ITDFilter.setUseWeights() is called");
        this.useWeights = useWeights;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public boolean isWriteITD2File() {
        return this.writeITD2File;
    }

    public void setWriteITD2File(boolean writeITD2File) {
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

    public boolean isUseMedian() {
        return this.useWeights;
    }

    public void setUseMedian(boolean useMedian) {
        this.useMedian = useMedian;
    }

    private void createBins() {
        log.info("create Bins with averagingDecay=" + averagingDecay + " and maxITD=" + maxITD + " and numOfBins=" + numOfBins);
        myBins = new ITDBins((float) averagingDecay, maxITD, numOfBins);
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
        if (useWeights == true) {
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, "  lastWeight=" + lastWeight);
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
