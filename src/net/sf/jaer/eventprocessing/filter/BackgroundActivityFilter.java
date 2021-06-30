/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import java.util.Arrays;
import java.util.Observable;
import java.util.Random;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * An filter that filters slow background activity by only passing events that
 * are supported by another event in the past {@link #setDt dt} in the immediate
 * spatial neighborhood, defined by a subsampling bit shift.
 *
 * @author tobi
 */
@Description("Filters out uncorrelated background activity noise according to Delbruck, Tobi. 2008. “Frame-Free Dynamic Digital Vision.” In Proceedings of Intl. Symp. on Secure-Life Electronics, Advanced Electronics for Quality Life and Society, 1:21–26. Tokyo, Japan: Tokyo. https://drive.google.com/open?id=0BzvXOhBHjRheTS1rSVlZN0l2MDg.")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class BackgroundActivityFilter extends AbstractNoiseFilter {

    private int sx;
    private int sy;

    int[][] timestampImage;
    private int ts = 0, lastTimestamp = DEFAULT_TIMESTAMP; // used to reset filter

    public BackgroundActivityFilter(AEChip chip) {
        super(chip);
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
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        super.filterPacket(in);
        if (timestampImage == null) {
            allocateMaps(chip);
        }

        int dt = (int) Math.round(getCorrelationTimeS() * 1e6f);
        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        for (BasicEvent e : in) {
            if (e == null) {
                continue;
            }
            if (e.isSpecial()) {
                continue;
            }
            totalEventCount++;
            int ts = e.timestamp;
            lastTimestamp = ts;
            final int x = (e.x >> subsampleBy), y = (e.y >> subsampleBy);
            if ((x < 0) || (x > sx) || (y < 0) || (y > sy)) {
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
            final int numMustBeCorrelated = 1;
            int ncorrelated = 0;
            outerloop:
            for (int xx = x - 1; xx <= x + 1; xx++) {
                for (int yy = y - 1; yy <= y + 1; yy++) {
                    if ((xx < 0) || (xx > sx) || (yy < 0) || (yy > sy)) {
                        continue;
                    }
                    if (filterHotPixels && xx == x && yy == y) {
                        continue; // like BAF, don't correlate with ourself
                    }
                    final int lastT = timestampImage[xx][yy];
                    final int deltaT = (ts - lastT);
                    if (deltaT < dt && lastT != DEFAULT_TIMESTAMP) {
                        ncorrelated++;
                        break outerloop; // csn stop checking n                    }
                    }
                }
            }
            if (ncorrelated < numMustBeCorrelated) {
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
//        log.info("resetting BackgroundActivityFilter");
        for (int[] arrayRow : timestampImage) {
            Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
        }
    }

    @Override
    public final void initFilter() {
        allocateMaps(chip);
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
        resetFilter();
    }
    
           /**
     * Fills timestampImage with waiting times drawn from Poisson process with
 rate noiseRateHz
     *
     * @param noiseRateHz rate in Hz
     * @param lastTimestampUs the last timestamp; waiting times are created
     * before this time
     */
    @Override
     public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        Random random=new Random();
        for (final int[] arrayRow : timestampImage) {
            for (int i = 0; i < arrayRow.length; i++) {
                final double p = random.nextDouble();
                final double t = -noiseRateHz * Math.log(1 - p);
                final int tUs = (int) (1000000 * t);
                arrayRow[i] = lastTimestampUs - tUs;
            }
        }
    }


    private void allocateMaps(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0) && (timestampImage == null || timestampImage.length != chip.getSizeX() >> subsampleBy)) {
            timestampImage = new int[chip.getSizeX()][chip.getSizeY()]; // TODO handle subsampling to save memory (but check in filterPacket for range check optomization)
        }
    }

    public Object getFilterState() {
        return timestampImage;
    }

    private String USAGE = "BackgroundFilter needs at least 2 arguments: noisefilter <command> <args>\nCommands are: setParameters dt xx subsample xx\n";

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
                    if (tok[i].equals("subsample")) {
                        setSubsampleBy(Integer.parseInt(tok[i + 1]));
                    }
                }
                String out = "successfully set BackgroundFilter parameters dt " + String.valueOf(getCorrelationTimeS()) + " and subsampleBy " + String.valueOf(subsampleBy);
                return out;
            } else {
                return USAGE;
            }

        } catch (Exception e) {
            return "IOExeption in remotecontrol " + e.toString() + "\n";
        }
    }

}
