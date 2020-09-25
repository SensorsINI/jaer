/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import java.util.Arrays;
import java.util.Observable;

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

    final int MAX_DT = 100000, MIN_DT = 10;
    final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;

    /**
     * the time in timestamp ticks (1us at present) that a spike needs to be
     * supported by a prior event in the neighborhood by to pass through
     */
    protected int dt = getInt("dt", 30000);
    private boolean letFirstEventThrough = getBoolean("letFirstEventThrough", true);

    /**
     * the amount to subsample x and y event location by in bit shifts when
     * writing to past event times map. This effectively increases the range of
     * support. E.g. setting subSamplingShift to 1 quadruples range because both
     * x and y are shifted right by one bit
     */
    private int subsampleBy = getInt("subsampleBy", 0);

    int[][] lastTimesMap;
    private int ts = 0; // used to reset filter
    private int sx;
    private int sy;

    public BackgroundActivityFilter(AEChip chip) {
        super(chip);
        initFilter();
        setPropertyTooltip("dt", "Events with less than this delta time in us to neighbors pass through");
        setPropertyTooltip("subsampleBy", "Past events are spatially subsampled (address right shifted) by this many bits");
        setPropertyTooltip("letFirstEventThrough", "After reset, let's first event through; if false, first event from each pixel is blocked");
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
        if (lastTimesMap == null) {
            allocateMaps(chip);
        }
        totalEventCount = 0;
        filteredOutEventCount = 0;

        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        for (Object eIn : in) {
            if (eIn == null) {
                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            }
            BasicEvent e = (BasicEvent) eIn;
            if (e.isSpecial()) {
                continue;
            }

            totalEventCount++;
            short x = (short) (e.x >>> subsampleBy), y = (short) (e.y >>> subsampleBy);
            if ((x < 0) || (x > sx) || (y < 0) || (y > sy)) {
                filteredOutEventCount++;
                continue;
            }

            ts = e.timestamp;
            int lastT = lastTimesMap[x][y];
            int deltaT = (ts - lastT);

            if (!((deltaT < dt) && (lastT != DEFAULT_TIMESTAMP)) && !(letFirstEventThrough && lastT == DEFAULT_TIMESTAMP)) {
                e.setFilteredOut(true);
                filteredOutEventCount++;
            }

            // For each event write the event's timestamp into the
            // lastTimesMap array at neighboring locations lastTimesMap[x][y]=ts;
            // Don't write to ourselves, we need support from neighbor for
            // next event.
            // Bounds checking here to avoid throwing expensive exceptions.
            if (((x > 0) && (x < sx)) && ((y > 0) && (y < sy))) {
                lastTimesMap[x - 1][y] = ts;
                lastTimesMap[x + 1][y] = ts;
                lastTimesMap[x][y - 1] = ts;
                lastTimesMap[x][y + 1] = ts;
                lastTimesMap[x - 1][y - 1] = ts;
                lastTimesMap[x + 1][y + 1] = ts;
                lastTimesMap[x - 1][y + 1] = ts;
                lastTimesMap[x + 1][y - 1] = ts;
            }
        }

        return in;
    }

    @Override
    public synchronized final void resetFilter() {
        initFilter();
    }

    @Override
    public final void initFilter() {
        allocateMaps(chip);
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
    }

    private void allocateMaps(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0)) {
            lastTimesMap = new int[chip.getSizeX()][chip.getSizeY()];
            for (int[] arrayRow : lastTimesMap) {
                Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
            }
        }
    }

    public Object getFilterState() {
        return lastTimesMap;
    }

    // <editor-fold defaultstate="collapsed" desc="getter-setter / Min-Max for --Dt--">
    /**
     * gets the background allowed delay in us
     *
     * @return delay allowed for spike since last in neighborhood to pass (us)
     */
    public int getDt() {
        return this.dt;
    }

    /**
     * sets the background delay in us. If param is larger then getMaxDt() or
     * smaller getMinDt() the boundary value are used instead of param.
     * <p>
     * Fires a PropertyChangeEvent "dt"
     *
     * @see #getDt
     * @param dt delay in us
     */
    public void setDt(int dt) {
        int setValue = dt;
        if (dt < getMinDt()) {
            setValue = getMinDt();
        }
        if (dt > getMaxDt()) {
            setValue = getMaxDt();
        }

        putInt("dt", setValue);
        getSupport().firePropertyChange("dt", this.dt, setValue);
        this.dt = setValue;
    }

    public int getMinDt() {
        return MIN_DT;
    }

    public int getMaxDt() {
        return MAX_DT;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter-setter for --SubsampleBy--">
    public int getSubsampleBy() {
        return subsampleBy;
    }

    /**
     * Sets the number of bits to subsample by when storing events into the map
     * of past events. Increasing this value will increase the number of events
     * that pass through and will also allow passing events from small sources
     * that do not stimulate every pixel.
     *
     * @param subsampleBy the number of bits, 0 means no subsampling, 1 means
     * cut event time map resolution by a factor of two in x and in y
     */
    public void setSubsampleBy(int subsampleBy) {
        if (subsampleBy < 0) {
            subsampleBy = 0;
        } else if (subsampleBy > 4) {
            subsampleBy = 4;
        }
        
        putInt("subsampleBy", subsampleBy);
        getSupport().firePropertyChange("subsampleBy", this.subsampleBy, subsampleBy);
        this.subsampleBy = subsampleBy;
    }
    // </editor-fold>

    /**
     * @return the letFirstEventThrough
     */
    public boolean isLetFirstEventThrough() {
        return letFirstEventThrough;
    }

    /**
     * @param letFirstEventThrough the letFirstEventThrough to set
     */
    public void setLetFirstEventThrough(boolean letFirstEventThrough) {
        this.letFirstEventThrough = letFirstEventThrough;
        putBoolean("letFirstEventThrough", letFirstEventThrough);
    }
    
        
    private String USAGE = "BackgroundFilter needs at least 2 arguments: noisefilter <command> <args>\nCommands are: setParameters dt xx subsample xx\n";
    
    @Override
    public String setParameters(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s");
        
        if (tok.length < 3) {
            return USAGE;
        }
        try {

            if ((tok.length -1) % 2 == 0) {
                for (int i = 1; i < tok.length; i++) {
                    if (tok[i].equals("dt")) {                
                        setDt(Integer.parseInt(tok[i+1]));
                    }
                    if (tok[i].equals("subsample")) {
                        setSubsampleBy(Integer.parseInt(tok[i+1]));
                    }
                }
                String out = "successfully set BackgroundFilter parameters dt " + String.valueOf(dt) + " and subsampleBy " + String.valueOf(subsampleBy); 
                return out;
            } else {
                return USAGE;
            }

        } catch (Exception e) {
            return "IOExeption in remotecontrol " + e.toString() + "\n";
        }
    }

}
