package ch.unizh.ini.jaer.projects.robothead;

/*
 * CorrelatorFilter.java
 *
 * Created on 28. November 2007, 14:13
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

import java.awt.Graphics2D;
import java.util.Observable;

import java.util.Observer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Correlator Filter
 *
 * @author jaeckeld
 */
public class CorrelatorFilter extends EventFilter2D implements FrameAnnotater {

    private int shiftSize = getPrefs().getInt("CorrelatorFilter.shiftSize", 800);
    private int binSize = getPrefs().getInt("CorrelatorFilter.binSize", 40);
    private int numberOfPairs = getPrefs().getInt("CorrelatorFilter.numberOfPairs", 1000);
    private int dimLastTs = getPrefs().getInt("CorrelatorFilter.dimLastTs", 5);
    private boolean display = getPrefs().getBoolean("CorrelatorFilter.display", false);

    HmmFilter BDFilter;

    /**
     * Creates a new instance of CorrelatorFilter
     */
    public CorrelatorFilter(AEChip chip) {
        super(chip);
        resetFilter();
        setPropertyTooltip("shiftSize", "maximum shift size for autocorrelation");
        setPropertyTooltip("binSize", "size for one Bin");
        setPropertyTooltip("numberOfPairs", "how many left/right pairs used");
        setPropertyTooltip("dimLastTs", "how many lastTs save");
        setPropertyTooltip("display", "Display Bins and ITD/Angle");
        BDFilter = new HmmFilter(chip);
    }

    int radius = 3;
    Angle myAngle = new Angle(radius);

    Bins myBins = new Bins();
    int[][][] lastTs = new int[32][2][dimLastTs];

    double ITD;
    int ANG;
    boolean side;

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }

        if (!isFilterEnabled()) {
            //System.out.print("TEST 2");
            return in;       // only use if filter enabled
        }

        if (in.getSize() == 0) {
            return in;       // do nothing if no spikes came in...., this means empty EventPacket
        }
        //resetFilter();
        checkOutputPacketEventType(in);

        for (Object e : in) {

            BasicEvent i = (BasicEvent) e;

            //System.out.println(i.timestamp+" "+i.x+" "+i.y);
            //  try{
            for (int j = 0; j < lastTs[i.x][1 - i.y].length; j++) {
                int diff = i.timestamp - lastTs[i.x][1 - i.y][j];     // compare actual ts with last complementary ts of that channel
                // x = channel y = side!!
                if (i.y == 0) {
                    diff = -diff;                        // to distingiuish plus- and minus-delay
                }

                //System.out.println(diff);
                if (java.lang.Math.abs(diff) < shiftSize) {
                    myBins.addToBin(diff);
                }
            }
            for (int j = lastTs[i.x][i.y].length - 1; j > 0; j--) {                  // shift values in lastTs
                lastTs[i.x][i.y][j] = lastTs[i.x][i.y][j - 1];
            }
            lastTs[i.x][i.y][0] = i.timestamp;
            //}catch(ArrayIndexOutOfBoundsException eeob){
            //allocateMaps(chip);
            //}
        }

        ITD = myBins.getITD();
        ANG = myAngle.getAngle(ITD);

        if (display) {
            Bins.dispBins();
            System.out.println(ITD + " " + ANG);
        }

        return in;

    }

    public int getAngle() {
        double ITD = myBins.getITD();
        return myAngle.getAngle(ITD);
    }

    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {

        myBins.genBins(shiftSize, binSize, numberOfPairs);
        //        System.out.println(this.getBinSize());
        //        System.out.println(this.getShiftSize());
        int[][][] lastTs = new int[32][2][dimLastTs];
        //dreher.Reset();

    }

    @Override
    public void initFilter() {
        System.out.println("init!");
        myBins.genBins(shiftSize, binSize, numberOfPairs);

    }

    public int getShiftSize() {
        return shiftSize;
    }

    public void setShiftSize(int shiftSize) {
        getPrefs().putInt("CorrelatorFilter.shiftSize", shiftSize);
        getSupport().firePropertyChange("shiftSize", this.shiftSize, shiftSize);
        this.shiftSize = shiftSize;
        myBins.genBins(shiftSize, binSize, numberOfPairs);

    }

    public int getBinSize() {
        return binSize;
    }

    public void setBinSize(int binSize) {
        getPrefs().putInt("CorrelatorFilter.binSize", binSize);
        getSupport().firePropertyChange("binSize", this.binSize, binSize);
        this.binSize = binSize;
        myBins.genBins(shiftSize, binSize, numberOfPairs);

    }

    public int getNumberOfPairs() {
        return numberOfPairs;
    }

    public void setNumberOfPairs(int numberOfPairs) {
        getPrefs().putInt("CorrelatorFilter.numberOfPairs", numberOfPairs);
        getSupport().firePropertyChange("numberOfPairs", this.numberOfPairs, numberOfPairs);
        this.numberOfPairs = numberOfPairs;
        myBins.genBins(shiftSize, binSize, numberOfPairs);

    }

    public int getDimLastTs() {
        return dimLastTs;
    }

    public void setDimLastTs(int dimLastTs) {
        getPrefs().putInt("CorrelatorFilter.dimLastTs", dimLastTs);
        getSupport().firePropertyChange("dimLastTs", this.dimLastTs, dimLastTs);
        int[][][] lastTs = new int[32][2][dimLastTs];
        this.dimLastTs = dimLastTs;
    }

    public boolean isDisplay() {
        return display;
    }

    public void setDisplay(boolean display) {
        this.display = display;
        getPrefs().putBoolean("CorrelatorFilter.display", display);
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }

    EngineeringFormat fmt = new EngineeringFormat();

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
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("ITD(us)=%s", fmt.format(ITD)));

        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("  Angle=%s", ANG));
        gl.glPopMatrix();
    }

}
