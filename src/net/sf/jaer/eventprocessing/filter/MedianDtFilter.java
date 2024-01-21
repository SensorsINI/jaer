/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.Desktop;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Observable;
import java.util.Random;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.TobiLogger;

/**
 * A BA noise filter based on median time (robust to outliers) to past events in
 * neighborhood.
 *
 * @author tobi delbruck jan 2020
 */
@Description("Filters out uncorrelated noise events by criterion sum of nearest-in-time N neighbors must less than correlation time dt ")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MedianDtFilter extends AbstractNoiseFilter {

    private int positionInSortedDts = getInt("positionInSortedDts", 5);

    private int sx; // size of chip minus 1
    private int sy;
    private int ssx; // size of subsampled timestamp map
    private int ssy;

    int[][] timestampImage;
    private int ts = 0, lastTimestamp = DEFAULT_TIMESTAMP; // used to reset filter

    private int[] nnbDts = null;

    public MedianDtFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip(TT_FILT_CONTROL, "positionInSortedDts", "Position to check in list of sorted dts to NNbs");
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. filtering may occur
     * in place in the in packet.
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        super.filterPacket(in);
        if (timestampImage == null) {
            allocateMaps(chip);
        }
        final int dt = (int) Math.round(getCorrelationTimeS() * 1e6f);
        final int thresholdSumDt = positionInSortedDts * dt;
        ssx = sx >> subsampleBy;
        ssy = sy >> subsampleBy;
        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        final NnbRange nnbRange = new NnbRange();
        for (Object eIn : in) {
            if (eIn == null) {
                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            }
            BasicEvent e = (BasicEvent) eIn;
//            if (e.isSpecial()) {
//                continue;
//            }
            totalEventCount++;
            int ts = e.timestamp;
            lastTimestamp = ts;
            final int x = (e.x >> subsampleBy), y = (e.y >> subsampleBy); // subsampling address
            if ((x < 0) || (x > ssx) || (y < 0) || (y > ssy)) { // out of bounds, discard (maybe bad USB or something)
                filterOut(e);
                continue;
            }
            if (timestampImage[x][y] == DEFAULT_TIMESTAMP) {
                timestampImage[x][y] = ts;
                if (letFirstEventThrough) {
                    filterIn(e);
                    continue;
                } else {
                    filterOut(e);
                    continue;
                }
            }

            // finally the real denoising starts here
            int neighborNum = 0;
            outerloop:
            for (int xx = nnbRange.x0; xx <= nnbRange.x1; xx++) {
                final int[] col = timestampImage[xx];
                for (int yy = nnbRange.y0; yy <= nnbRange.y1; yy++) {
                    if (filterHotPixels && xx == x && yy == y) {
                        continue; // like BAF, don't correlate with ourself
                    }
                    final int lastT = col[yy];
                    final int deltaT = col[yy] != DEFAULT_TIMESTAMP ? (ts - lastT) : Integer.MAX_VALUE;
                    nnbDts[neighborNum++] = deltaT;
                }
            }
            Arrays.sort(nnbDts);
            int sumDt = 0;
            for (int i = 0; i < positionInSortedDts; i++) {
                sumDt += nnbDts[i];
            }
            if (sumDt > thresholdSumDt) {
                filterOut(e);
            } else {
                filterIn(e);
            }
            timestampImage[x][y] = ts;
        }
        getNoiseFilterControl().maybePerformControl(in);
        return in;
    }

    @Override
    public synchronized final void resetFilter() {
        super.resetFilter();
        for (int[] arrayRow : timestampImage) {
            Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
        }
        Arrays.fill(nnbDts, DEFAULT_TIMESTAMP);
    }

    @Override
    public final void initFilter() {
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
        ssx = sx >> subsampleBy;
        ssy = sy >> subsampleBy;
        nnbDts = new int[getNumNeighbors()];
        allocateMaps(chip);
        resetFilter();
    }

    private void allocateMaps(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0) && (timestampImage == null || timestampImage.length != chip.getSizeX() >> subsampleBy)) {
            timestampImage = new int[chip.getSizeX()][chip.getSizeY()]; // TODO handle subsampling to save memory (but check in filterPacket for range check optomization)
        }
        lastTimestamp = DEFAULT_TIMESTAMP;
    }

    /**
     * Fills timestampImage with waiting times drawn from Poisson process with
     * rate noiseRateHz
     *
     * @param noiseRateHz rate in Hz
     * @param lastTimestampUs the last timestamp; waiting times are created
     * before this time
     */
    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        Random random = new Random();
        for (final int[] arrayRow : timestampImage) {
            for (int i = 0; i < arrayRow.length; i++) {
                final double p = random.nextDouble();
                final double t = -noiseRateHz * Math.log(1 - p);
                final int tUs = (int) (1000000 * t);
                arrayRow[i] = lastTimestampUs - tUs;
            }
        }
    }

    /**
     * @return the positionInSortedDts
     */
    public int getPositionInSortedDts() {
        return positionInSortedDts;
    }

    /**
     * @param positionInSortedDts the positionInSortedDts to set
     */
    public void setPositionInSortedDts(int positionInSortedDts) {
        if (positionInSortedDts < 0) {
            positionInSortedDts = 0;
        } else if (positionInSortedDts > getNumNeighbors()) {
            positionInSortedDts = getNumNeighbors();
        }
        putInt("positionInSortedDts", positionInSortedDts);
        this.positionInSortedDts = positionInSortedDts;
        getSupport().firePropertyChange("positionInSortedDts", this.positionInSortedDts, positionInSortedDts);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            resetFilter();
        }
    }

    private String USAGE = "MedianDtFilter needs at least 2 arguments: noisefilter <command> <args>\nCommands are: setParameters dt xx \n";

    // remote control for experiments e.g. with python / UDP remote control 
    @Override
    public String setParameters(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s");

        if (tok.length < 3) {
            return USAGE;
        }
        try {

            if ((tok.length - 1) % 2 == 0) {
                for (int i = 1; i < tok.length; i++) {
                    if (tok[i].equals("dt")) {
                        setCorrelationTimeS(1e-6f * Integer.parseInt(tok[i + 1]));
                    }
                }
                String out = "successfully set MedianDtFilter parameters dt " + String.valueOf(getCorrelationTimeS());
                return out;
            } else {
                return USAGE;
            }

        } catch (Exception e) {
            return "IOExeption in remotecontrol" + e.toString() + "\n";
        }
    }

    @Override
    public String infoString() {
        String s = super.infoString();
        return s;
    }

    @Override
    synchronized public void setSigmaDistPixels(int sigmaDistPixels) {
        super.setSigmaDistPixels(sigmaDistPixels);
        nnbDts = new int[getNumNeighbors()]; // must resize when neighborhood changes
    }

}
