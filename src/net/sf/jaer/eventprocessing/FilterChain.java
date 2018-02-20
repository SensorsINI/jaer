/* FilterChain.java
 *
 * Created on January 30, 2006, 7:58 PM */
package net.sf.jaer.eventprocessing;

import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.util.ClassChooserDialog;

/**
 * A chain of EventFilter that serially filters or processes packets of
 * AEPacket2D. An instance of this object can be passed to FilterFrame and is an
 * instance field of e.g. AERetina. Filters know which chain they are part of
 * and can find out what filters come before and afterwards, allowing them to
 * enable or disable them according to their needs, e.g.
 * NearestEventMotionFilter needs SimpleOrientationFilter to be enabled.
 * <p>
 * FilterChain fires the following PropertyChangeEvents
 * <ul>
 * <li> processingmode - when the processing mode is changed
 * </ul>
 * FilterChains should be constructed as in the following example taken from a
 * filter:
 * <pre>
 * //build hierarchy
 * FilterChain trackingFilterChain = new FilterChain(chip);
 * EventFilter tracker=new RectangularClusterTracker(chip);
 * EventFilter servoArm = new ServoArm(chip);
 * EventFilter xYFilter = new XYTypeFilter(chip);
 * EventFilter tableFilter=new GoalieTableFilter(chip);
 *
 * trackingFilterChain.add(new BackgroundActivityFilter(chip));
 * trackingFilterChain.add(tableFilter);
 * trackingFilterChain.add(tracker);
 * trackingFilterChain.add(servoArm);
 * setEnclosedFilterChain(trackingFilterChain); // labels enclosed filters as being enclosed
 * tracker.setEnclosedFilter(xYFilter); // marks xYFilter as enclosed by tracker
 * tracker.setEnclosed(true, this);    // tracker is enclosed by this
 * servoArm.setEnclosed(true, this);   // same for servoArm
 * xYFilter.setEnclosed(true, tracker); // but xYFilter is enclosed by tracker
 * </pre> Another, simpler, example is as follows, as part of an EventFilter's
 * constructor:
 * <pre>
 * setEnclosedFilterChain(new FilterChain(chip)); // make a new FilterChain for this EventFilter
 * RefractoryFilter rf=new RefractoryFilter(chip); // make a filter to go in the chain
 * rf.setEnclosed(true, this);                     // set rf to be enclosed and inside this filter
 * getEnclosedFilterChain().add(rf);               // add rf to this EventFilter's FilterChain
 * </pre>
 *
 * @author tobi
 */
public class FilterChain extends LinkedList<EventFilter2D> {

    private PropertyChangeSupport support = new PropertyChangeSupport(this);
    private boolean measurePerformanceEnabled = false;
    volatile private boolean resetPerformanceMeasurementStatistics = false; // flag to reset everyone on this cycle
    static final Logger log = Logger.getLogger("FilterChain");
    AEChip chip;
    private boolean filteringEnabled = true;
    /**
     * true if filter is enclosed by another filter
     */
    private boolean enclosed = false;
    /**
     * The enclosing filter
     */
    private EventFilter enclosingFilter = null;
    private boolean timeLimitEnabled;
    private int timeLimitMs;

    private boolean timedOut = false;

    /**
     * The updateIntervalMs is used by EventFilter2D's to ensure maximum update
     * intervals while iterating over packets of events. Subclasses of
     * EventFilter2D should check for Observers which may wish to be informed of
     * these updates during iteration over packets.
     */
    protected float updateIntervalMs;

    /**
     * Call this method to reset the performance measurements on the next
     * <code>filterPacket</code> call.
     *
     * @param resetPerformanceMeasurementStatistics the
     * resetPerformanceMeasurementStatistics to set
     */
    synchronized public void resetResetPerformanceMeasurementStatistics() {
        this.resetPerformanceMeasurementStatistics = true;
    }

    /**
     * Filters can either be processed in the rendering or the data acquisition
     * cycle. Procesing in the rendering cycle is certainly more efficient
     * because events are processed in larger packets, but latency is increased
     * to the rendering frame rate delay. Processing in the data acquisition
     * thread has the shortest possible latency and if the filter annotates
     * graphics this processing can cause threading problems, e.g. if the
     * annotation modifies the graphics buffer while the image is being
     * rendered.
     */
    public enum ProcessingMode {

        RENDERING, ACQUISITION
    };
    private ProcessingMode processingMode = ProcessingMode.RENDERING;

    /**
     * Creates a new instance of FilterChain. Use
     * <@link #contructPreferredFilters> to build the stored preferences for
     * filters.
     *
     * @param chip the chip that uses this filter chain
     */
    public FilterChain(AEChip chip) {
        this.chip = chip;
//        AEViewer aeViewer=chip.getAeViewer();
        getSupport().addPropertyChangeListener(chip.getFilterFrame());
        timeLimitEnabled = chip.getPrefs().getBoolean("FilterChain.timeLimitEnabled", false);
        timeLimitMs = chip.getPrefs().getInt("FilterChain.timeLimitMs", 10);
        updateIntervalMs = chip.getPrefs().getFloat("FilterChain.updateIntervalMs", 10);

        timedOut = false;
        try {
            processingMode = ProcessingMode.valueOf(
                    chip.getPrefs().get("FilterChain.processingMode", FilterChain.ProcessingMode.RENDERING.toString())); // ProcessingMode.RENDERING;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * resets all the filters
     */
    public void reset() {
        for (EventFilter2D f : this) {
            f.resetFilter();
        }
    }

    /**
     * Cleans up by calling each EventFilter's cleanup method. This is where
     * filters can do things like closing sockets (making sure they check if
     * they are non-null first), closing files, disposing of graphics, etc.
     *
     * @see EventFilter#cleanup()
     */
    public void cleanup() {
        for (EventFilter f : this) {
            f.cleanup();
        }
    }

    /**
     * applies all the filters in the chain to the packet in the order of the
     * enabled filters and only if input packet in is non-null. If
     * timeLimitEnabled=true then the timeLimiter is started on the first
     * packet. Any subsequent input iterator for events will then timeout when
     * the time limit has been reached.
     *
     * @param in the input packet of events
     * @return the resulting output.
     */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (!filteringEnabled || size() == 0) {
            return in;
        }
        EventPacket out;
//        if (timeLimitEnabled) {
//            if (chip.getAeViewer().isPaused()) {
//                in.setTimeLimitEnabled(false);
//            } else {
//                in.setTimeLimitEnabled(true);
//                in.restartTimeLimiter(timeLimitMs);
//            }
//        } else {
//            in.setTimeLimitEnabled(false);
//        }
        if (resetPerformanceMeasurementStatistics) {
            for (EventFilter2D f : this) {
                if (f.perf != null && f.isFilterEnabled()) { // check to reset performance meter
                    f.perf.resetStatistics();
                }
            }
            log.info("compute performance statistics reset");
            resetPerformanceMeasurementStatistics = false;
        }
        for (EventFilter2D f : this) {
            if (!f.isFilterEnabled() || in == null) {
                continue;  // tobi added so that each filter doesn't need to check if enabled and non-null packet
            }
            if (measurePerformanceEnabled) {
                if (f.perf == null) {
                    f.perf = new EventProcessingPerformanceMeter(f);
                }
                f.perf.start(in);
            }
            out = f.filterPacket(in);
            timedOut = in.isTimedOut();
            if (measurePerformanceEnabled && f.perf != null) {
                f.perf.stop();
                System.out.println(f.perf);
            }
            in = out;
        }
        return in;
    }

    /**
     * @param filterClass the class to search for
     * @return the first filter with class filterClass, or null if there is none
     */
    public EventFilter2D findFilter(Class filterClass) {
        for (EventFilter2D f : this) {
            if (f.getClass() == filterClass) {
                return f;
            }
        }
        return null;
    }

    /**
     * Adds a filter to the end of the chain. Filters also need to have their
     * enclosing state set manually: whether they are flagged as "enclosed" and
     * who encloses them.
     *
     * @param filter the filter to add
     * @return true
     * @see net.sf.jaer.eventprocessing.EventFilter#setEnclosed(boolean,
     * net.sf.jaer.eventprocessing.EventFilter) )
     */
    @Override
    public boolean add(EventFilter2D filter) {
//        log.info("adding "+filter+" to "+this);
//        filter.setEnclosed(true, filter.getEnclosingFilter()); // all filters are enclosed (in a sense) in this filter chain but they may
        // not be enclosed in another filter
        boolean ret = super.add(filter);
//        if(chip!=null && chip.getFilterFrame()!=null){
//            chip.getFilterFrame().rebuildContents();
//        }
        return ret;
    }

    /**
     * remove the filter
     *
     * @return true if successful
     */
    public boolean remove(EventFilter2D filter) {
        boolean ret = super.remove(filter);
        return ret;
    }

    public boolean isTimeLimitEnabled() {
        return timeLimitEnabled;
    }

    public ProcessingMode getProcessingMode() {
        return processingMode;
    }

    /**
     * Sets whether this chain is processed in the acquisition or rendering
     * thread. For more real-time performance the data should be processed as it
     * is acquired, not later when it is rendered.
     * <p>
     * Fires PropertyChangeEvent "processingmode"
     *
     * @see #processingMode
     */
    synchronized public void setProcessingMode(ProcessingMode processingMode) {
        getSupport().firePropertyChange("processingmode", this.processingMode, processingMode);
        this.processingMode = processingMode;
        chip.getPrefs().put("FilterChain.processingMode", processingMode.toString());
    }

    /**
     * Iterates over all filters and returns true if any filter is enabled.
     *
     * @return true if any filter is enabled, false otherwise.
     */
    public boolean isAnyFilterEnabled() {
        boolean any = false;
        try {
            for (EventFilter2D f : this) {
                if (f.isFilterEnabled()) {
                    any = true;
                    break;
                }
            }
        } catch (ConcurrentModificationException e) {
            log.warning(e + " during check");
        }
        return any;
    }

    public boolean isMeasurePerformanceEnabled() {
        return measurePerformanceEnabled;
    }

    synchronized public void setMeasurePerformanceEnabled(boolean measurePerformanceEnabled) {
        this.measurePerformanceEnabled = measurePerformanceEnabled;
    }

    /**
     * disables all filters individually, which will turn off each of them.
     *
     * @see #setFilteringEnabled
     */
    private void disableAllFilters() {
        for (EventFilter2D f : this) {
            f.setFilterEnabled(false);
        }
    }

    /**
     * Globally sets whether filters are applied in this FilterChain.
     *
     * @param b true to enable (default) or false to disable all filters
     */
    public void setFilteringEnabled(boolean b) {
        filteringEnabled = b;
    }

    public boolean isFilteringEnabled() {
        return filteringEnabled;
    }
    static final Class[] filterConstructorParams = {AEChip.class}; // params to constructor of an EventFilter2D

    /**
     * makes a new FilterChain, which constructs the default filters as stored
     * in preferences or as coming from Chip defaultFilterClasses.
     */
    synchronized void renewChain() {
//        disableAllFilters();
//        Constructor c;
//        Class[] par={chip.getClass()}; // argument to filter constructor
        FilterChain nfc = new FilterChain(chip); // this call already builds the preferred filters for this chip

//        for(EventFilter2D f:this){
//            try{
//                c=f.getClass().getConstructor(filterConstructorParams);
//                EventFilter2D nf=(EventFilter2D)(c.newInstance(chip));
//                nfc.add(nf);
//            }catch(Exception e){
//                log.warning("couldn't renew filter "+f+", didn't have constructor taking AEChip parameter");
//            }
//        }
        clear();
        for (EventFilter2D f : nfc) {
            add(f);
        }
        nfc = null;
    }

    String prefsKey() {
        return "FilterFrame.filters";
    }

    /**
     * Constructs the preferred filters for the FilterChain as stored in user
     * Preferences.
     */
    @SuppressWarnings("unchecked")
    synchronized public void contructPreferredFilters() {
        clear();
        ArrayList<String> classNames;
        Preferences prefs = chip.getPrefs(); // Preferences.userNodeForPackage(chip.getClass()); // getString prefs for the Chip, not for the FilterChain class
        try {
            byte[] bytes = prefs.getByteArray(prefsKey(), null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                classNames = (ArrayList<String>) in.readObject();
                in.close();
            } else {
                classNames = chip.getDefaultEventFilterClassNames();
            }
            Class[] par = {chip.getClass()}; // argument to filter constructor
            ArrayList<String> toRemove = new ArrayList<String>();
            for (String s : classNames) {
                try {
                    Class cl = Class.forName(s);
                    Constructor co = cl.getConstructor(filterConstructorParams);
                    EventFilter2D fi = (EventFilter2D) co.newInstance(chip);
                    add(fi);
                } catch (Exception e) {
                    log.warning("couldn't construct filter " + s + " for chip " + chip.getClass().getName() + " : " + e.toString() + " will remove this filter from Preferences");
                    toRemove.add(s);
                    if (e.getCause() != null) {
                        Throwable t = e.getCause();
                        t.printStackTrace();
                    }
                } catch (NoClassDefFoundError err) {
                    log.warning("couldn't construct filter " + s + " for chip " + chip.getClass().getName() + " : " + err.toString() + " will remove this filter from Preferences");
                    toRemove.add(s);
                    if (err.getCause() != null) {
                        Throwable t = err.getCause();
                        t.printStackTrace();
                    }

                }
            }
            if (toRemove.size() > 0) {
                classNames.removeAll(toRemove);
                try {
                    storePreferredFilterPreferences(classNames);
                } catch (IOException e) {
                    log.warning(e.toString());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            log.warning(e.getMessage());
        }
    }

    synchronized void customize() {
        log.info("customizing filter chain for chip class=" + chip.getClass());
        ArrayList<String> currentFilterNames = new ArrayList<String>();
        for (EventFilter2D f : this) {
            currentFilterNames.add(f.getClass().getName());
        }

        ClassChooserDialog chooser = new ClassChooserDialog(chip.getFilterFrame(), EventFilter2D.class, currentFilterNames, chip.getDefaultEventFilterClassNames());
        chooser.setVisible(true);
        if (chooser.getReturnStatus() == ClassChooserDialog.RET_OK) {
            Preferences prefs = chip.getPrefs(); // getString prefs for the Chip, not for the FilterChain class
            ArrayList<String> newClassNames = chooser.getList();
            try {
                storePreferredFilterPreferences(newClassNames);
//                contructPreferredFilters();
                if (chip.getFilterFrame() == null) {
                    log.warning(chip + " has no FilterFrame, cannot renew contents");
                } else {
                    chip.getFilterFrame().renewContents();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void storePreferredFilterPreferences(ArrayList<String> newClassNames) throws IOException, BackingStoreException {
        log.info("storing preferred filters to preferences");
        // Serialize to a byte array
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(bos);
        out.writeObject(newClassNames);
        out.close();
        byte[] buf = bos.toByteArray();
        chip.getPrefs().putByteArray(prefsKey(), buf);
        chip.getPrefs().sync();
    }

    /**
     * Returns status of timeout of event processing time limit during filter
     * processing.
     *
     * @return true if time limit is enabled and timeout occured during
     * processing of last packet, false otherwise
     */
    public boolean isTimedOut() {
        return timedOut;
    }

    /**
     * Is filter enclosed inside another filter?
     *
     * @return true if this filter is enclosed inside another
     */
    public boolean isEnclosed() {
        return enclosed;
    }

    /**
     * Sets flag to show this instance is enclosed. If this flag is set to true,
     * then preferences node is changed to a node unique for the enclosing
     * filter class.
     *
     * @param enclosingFilter the filter that is enclosing this
     * @param enclosed true if this filter is enclosed
     */
    public void setEnclosed(boolean enclosed, final EventFilter enclosingFilter) {
        this.enclosed = enclosed;
        this.enclosingFilter = enclosingFilter;
    }

    /**
     * FilterChain fires the following PropertyChangeEvents
     * <ul>
     * <li> processingmode - when the processing mode is changed
     * </ul>
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }

    /**
     * Sets max for slider.
     */
    /**
     * Sets max for slider.
     */
    public float getMaxUpdateIntervalMs() {
        return 100;
    }

    /**
     * Sets min for slider.
     */
    /**
     * Sets min for slider.
     */
    public float getMinUpdateIntervalMs() {
        return 1;
    }

    /**
     * The list of clusters is updated at least this often in us, but also at
     * least once per event packet.
     *
     * @return the updateIntervalMs
     */
    /**
     * The list of clusters is updated at least this often in ms, but also at
     * least once per event packet.
     *
     * @return the updateIntervalMs the update interval in milliseconds (float
     * values <1 are allowed, e.g. .1f for 100us)
     */
    public float getUpdateIntervalMs() {
        return updateIntervalMs;
    }

    /**
     * The minimum interval between cluster list updating for purposes of
     * pruning list and merging clusters. Allows for fast playback of data and
     * analysis with large packets of data.
     *
     * @param updateIntervalMs the updateIntervalMs to set
     */
    /**
     * The minimum interval between cluster list updating for purposes of
     * pruning list and merging clusters. Allows for fast playback of data and
     * analysis with large packets of data.
     *
     * @param updateIntervalMs the updateIntervalMs to set
     */
    public void setUpdateIntervalMs(float updateIntervalMs) {
//        support.firePropertyChange("updateIntervalMs", this.updateIntervalMs, updateIntervalMs); // TODO add propertyChange here?
        this.updateIntervalMs = updateIntervalMs;
        chip.getPrefs().putFloat("FilterChain.updateIntervalMs", updateIntervalMs);
    }
}
