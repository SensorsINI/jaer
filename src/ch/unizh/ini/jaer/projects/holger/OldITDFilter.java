package ch.unizh.ini.jaer.projects.holger;
/*
 * OldITDFilter.java
 *
 * Created on 28. November 2007, 14:13
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
//import com.sun.org.apache.xpath.internal.operations.Mod;
import java.util.*;
//import experiment1.PanTilt;
//import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Correlator Filter
 *
 * @author Holger/jaeckeld
 */
public class OldITDFilter extends EventFilter2D implements Observer, FrameAnnotater {

    OldITDFrame frame;
    private int shiftSize;
    private int maxWeight;
    private int maxWeightTime;
    private int binSize;
    private int numberOfPairs;
    private int dimLastTs;
    private boolean display;
    private boolean useWeights;
    private int lastWeight = 0;
    int radius = 3;
    OldITDBins myBins = new OldITDBins();
    int[][][] lastTs;
    double ITD;
    double ILD;
    int ANG;
    boolean side;

    /** Creates a new instance of OldITDFilter */
    public OldITDFilter(AEChip chip) {
        super(chip);
        initFilter();
        lastTs = new int[32][2][dimLastTs];
        //resetFilter();
        setPropertyTooltip("maxWeight", "maximum weight for ITDs");
        setPropertyTooltip("maxWeightTime", "maximum time to use for weighting ITDs");
        setPropertyTooltip("shiftSize", "maximum shift size for autocorrelation");
        setPropertyTooltip("binSize", "size for one Bin");
        setPropertyTooltip("numberOfPairs", "how many left/right pairs used");
        setPropertyTooltip("dimLastTs", "how many lastTs save");
        setPropertyTooltip("display", "Display Bins");
        setPropertyTooltip("useWeights", "Use weights for ITD");
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
        if (!isFilterEnabled()) {
            return in;       // only use if filter enabled
        }

        if (in.getSize() == 0) {
            return in;       // do nothing if no spikes came in...., this means empty EventPacket
        }        //resetFilter();
        checkOutputPacketEventType(in);

        int nleft = 0, nright = 0;

        for (Object e : in) {

            BasicEvent i = (BasicEvent) e;

            //System.out.println(i.timestamp+" "+i.x+" "+i.y);
            //  try{
            try {
                for (int j = 0; j < this.lastTs[i.x][1 - i.y].length; j++) {
                    int diff = i.timestamp - lastTs[i.x][1 - i.y][j];     // compare actual ts with last complementary ts of that channel
                    // x = channel y = side!!
                    if (i.y == 0) {
                        diff = -diff;     // to distingiuish plus- and minus-delay
                        nright++;
                    } else {
                        nleft++;
                    }

                    //System.out.println(diff);
                    if (java.lang.Math.abs(diff) < shiftSize) {
                        if (useWeights == false) {
                            myBins.addToBin(diff);
                        } else {
                            //Compute weights
                            int weightTime = i.timestamp - lastTs[i.x][i.y][0];
                            if (weightTime > maxWeightTime) {
                                weightTime = maxWeightTime;
                            }
                            lastWeight = ((weightTime*maxWeight)/maxWeightTime);
                            for (int k=0; k<lastWeight; k++)
                            {
                                myBins.addToBin(diff);
                            }
                            //log.info("weight:" + weight);
                        }
                    //log.info("added:" + diff);
                    }
                }
                for (int j = lastTs[i.x][i.y].length - 1; j > 0; j--) {                  // shift values in lastTs
                    lastTs[i.x][i.y][j] = lastTs[i.x][i.y][j - 1];
                }
                lastTs[i.x][i.y][0] = i.timestamp;
            } catch (Exception e1) {
                log.warning("In for-loop in filterPacket caught exception " + e1);
                e1.printStackTrace();
            }
        //}catch(ArrayIndexOutOfBoundsException eeob){
        //allocateMaps(chip);
        //}
        }

        try {
            ITD = myBins.getITD();
            log.info("ITD: "+ITD);
            ILD = (double) (nright - nleft) / (double) (nright + nleft); //Max ILD is 1 (if only one side active)
            //log.info("usedpairs:" + myBins.usedPairs.size() +" max:"+myBins.numberOfPairs+" bins:"+myBins.bins[0]+":"+myBins.bins[1]+":"+myBins.bins[2]+":"+myBins.bins[3]+":"+myBins.bins[4]+":"+myBins.bins[5]+":"+myBins.bins[6]+":"+myBins.bins[7]+":"+myBins.bins[8]+":"+myBins.bins[9]);
            //log.info("ddusedpairs:" + panel.myBins.usedPairs.size() +" max:"+panel.myBins.numberOfPairs+" bins:"+panel.myBins.bins[0]+":"+panel.myBins.bins[1]+":"+panel.myBins.bins[2]+":"+panel.myBins.bins[3]+":"+panel.myBins.bins[4]+":"+panel.myBins.bins[5]+":"+panel.myBins.bins[6]+":"+panel.myBins.bins[7]+":"+panel.myBins.bins[8]+":"+panel.myBins.bins[9]);
            if (display == true && frame!=null) {
                frame.setITD(ITD);
            }
        } catch (Exception e) {
            log.warning("In filterPacket caught exception " + e);
            e.printStackTrace();
        }
        return in;
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
        maxWeight = getPrefs().getInt("OldITDFilter.maxWeight", 5);
        maxWeightTime = getPrefs().getInt("OldITDFilter.maxWeightTime", 3000);
        shiftSize = getPrefs().getInt("OldITDFilter.shiftSize", 800);
        binSize = getPrefs().getInt("OldITDFilter.binSize", 100);
        numberOfPairs = getPrefs().getInt("OldITDFilter.numberOfPairs", 100);
        dimLastTs = getPrefs().getInt("OldITDFilter.dimLastTs", 4);
        display = getPrefs().getBoolean("OldITDFilter.display", false);
        useWeights = getPrefs().getBoolean("OldITDFilter.useWeights", false);
        if (isFilterEnabled()) {
            createBins();
            setDisplay(display);
        }
    }

    @Override
    public void setFilterEnabled(boolean yes) {
        log.info("OldITDFilter.setFilterEnabled() is called");
        super.setFilterEnabled(yes);
        if (yes) {
            try {
                createBins();
            } catch (Exception e) {
                log.warning("In genBins() caught exception " + e);
                e.printStackTrace();
            }
            display = getPrefs().getBoolean("OldITDFilter.display", false);
            setDisplay(display);
        }
    }

    public void update(Observable o, Object arg) {
        log.info("OldITDFilter.update() is called");
    }

    public int getShiftSize() {
        return this.shiftSize;
    }

    public void setShiftSize(int shiftSize) {
        getPrefs().putInt("OldITDFilter.shiftSize", shiftSize);
        support.firePropertyChange("shiftSize", this.shiftSize, shiftSize);
        this.shiftSize = shiftSize;
        createBins();
    }

    public int getBinSize() {
        return this.binSize;
    }

    public void setBinSize(int binSize) {
        getPrefs().putInt("OldITDFilter.binSize", binSize);
        support.firePropertyChange("binSize", this.binSize, binSize);
        this.binSize = binSize;
        createBins();
    }

    public int getNumberOfPairs() {
        return this.numberOfPairs;
    }

    public void setNumberOfPairs(int numberOfPairs) {
        getPrefs().putInt("OldITDFilter.numberOfPairs", numberOfPairs);
        support.firePropertyChange("numberOfPairs", this.numberOfPairs, numberOfPairs);
        this.numberOfPairs = numberOfPairs;
        createBins();
    }

    public int getDimLastTs() {
        return this.dimLastTs;
    }

    public void setDimLastTs(int dimLastTs) {
        getPrefs().putInt("OldITDFilter.dimLastTs", dimLastTs);
        support.firePropertyChange("dimLastTs", this.dimLastTs, dimLastTs);
        lastTs = new int[32][2][dimLastTs];
        this.dimLastTs = dimLastTs;
    }

    public int getMaxWeight() {
        return this.maxWeight;
    }

    public void setMaxWeight(int maxWeight) {
        getPrefs().putInt("OldITDFilter.maxWeights", maxWeight);
        support.firePropertyChange("maxWeight", this.maxWeight, maxWeight);
        this.maxWeight = maxWeight;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public int getMaxWeightTime() {
        return this.maxWeightTime;
    }

    public void setMaxWeightTime(int maxWeightTime) {
        getPrefs().putInt("OldITDFilter.maxWeightsTime", maxWeightTime);
        support.firePropertyChange("maxWeightTime", this.maxWeightTime, maxWeightTime);
        this.maxWeightTime = maxWeightTime;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    public boolean isDisplay() {
        return this.display;
    }

    public void setDisplay(boolean display) {
        log.info("OldITDFilter.setDisplay() is called");
        this.display = display;
        if (!isFilterEnabled()) {
            return;
        }
        if (display == true && frame == null) {
            try {
                frame = new OldITDFrame();
                frame.binsPanel1.updateBins(myBins);
                frame.binsPanel1.init();
                frame.binsPanel1.start();
                frame.setVisible(true);
                frame.setBounds(20, 20, 400, 400);
                log.info("ITD-Jframe created with heigth="+frame.getHeight()+" and width:"+frame.getWidth());
            } catch (Exception e) {
                log.warning("while creating ITD-Jframe, caught exception " + e);
                e.printStackTrace();
            }
        } else if (display == false && frame != null) {
            frame.setVisible(false);
            frame.binsPanel1.stop();
            frame = null;
        }
    }
    public boolean isUseWeights() {
        return this.useWeights;
    }

    public void setUseWeights(boolean useWeights) {
        log.info("OldITDFilter.setUseWeights() is called");
        this.useWeights = useWeights;
        if (!isFilterEnabled()) {
            return;
        }
        createBins();
    }

    private void createBins()
    {
        if (useWeights == false) {
            myBins.genBins(shiftSize, binSize, numberOfPairs);
        } else {
            myBins.genBins(shiftSize, binSize, (numberOfPairs*maxWeight)/2); //for normalization
        }
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }
    EngineeringFormat fmt = new EngineeringFormat();

    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        GL gl = drawable.getGL();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(1, 1, 1);
        gl.glRasterPos3f(0, 0, 0);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("ITD(us)=%s", fmt.format(ITD)));
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  ILD=%f", ILD));
        if (useWeights == true)
        {
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, "  lastWeight="+lastWeight);
        }
        gl.glPopMatrix();
    }
}

