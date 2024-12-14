/* AbstractEventFilter.java
 *
 * Created on October 30, 2005, 4:58 PM */
package net.sf.jaer.eventprocessing;

import java.awt.Cursor;
import java.awt.Desktop;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;
import java.util.Set;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLException;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.prefs.BackingStoreException;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.filter.PreferencesMover;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.MessageWithLink;
import net.sf.jaer.util.PrefObj;
import net.sf.jaer.util.PropertyTooltipSupport;

/**
 * An abstract class that all event processing methods should subclass.
 * Subclasses are introspected to build a GUI to control the filter in
 * {@link FilterPanel} - see this class to see how to add these introspected
 * controls to an EventFilter GUI control panel.
 * <p>
 * Filters that are enclosed inside another filter are given a preferences node
 * that is derived from the the chip class that the filter is used on and the
 * enclosing filter class. The same preferences node name is used for
 * FilterChain's that are enclosed inside an EventFilter.
 * <p>
 * Fires PropertyChangeEvent for the following
 * <ul>
 * <li> "filterEnabled" - when the filter is enabled or disabled this event is
 * fired, if the subclass has not overridden the setFilterEnabled method
 * </ul>
 *
 * @see FilterPanel FilterPanel - which is where EventFilter's GUIs are built.
 * @see net.sf.jaer.graphics.FrameAnnotater FrameAnnotator - to annotate the
 * graphical output.
 * @see net.sf.jaer.eventprocessing.EventFilter2D EventFilter2D - which
 * processes events.
 * @see net.sf.jaer.eventprocessing.FilterChain FilterChain - about enclosing
 * filters inside other filters.
 * @author tobi
 */
@Description("Base event processing class")
public abstract class EventFilter extends Observable implements HasPropertyTooltips, PropertyChangeListener {

    /**
     * URL for jAER wiki help page for event filters
     */
    public static final String HELP_WIKI_URL = "http://sourceforge.net/p/jaer/wiki/";
    /**
     * Use this key for global parameters in your filter constructor, as in
     * <pre> setPropertyTooltip(TOOLTIP_GROUP_GLOBAL, "propertyName", "property tip string");
     * </pre>
     */
    public static final String TOOLTIP_GROUP_GLOBAL = PropertyTooltipSupport.TOOLTIP_GROUP_GLOBAL;

    public EventProcessingPerformanceMeter perf;
    /**
     * The preferences for this filter, by default in the EventFilter package
     * node
     *
     * @see setEnclosed
     */
    private Preferences prefs = null; // default null, constructed when AEChip is known Preferences.userNodeForPackage(EventFilter.class);

    /**
     * Set of all preference values, filled by getXXX() method calls
     */
    protected HashMap<String, Class> preferencesMap = new HashMap();

    /**
     * Provides change support, e.g. for enabled state. Filters can cause their
     * FilterPanel GUI control for a property to update if they fire a
     * PropertyChangeEvent in the property setter, giving the name of the
     * property as the event. For example, Filters can also use support to
     * inform each other about changes in state.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);

    private boolean addedViewerPropertyChangeListener = false;
    private boolean addTimeStampsResetPropertyChangeListener = false;

    /**
     * All filters can log to this logger
     */
    public static final Logger log = Logger.getLogger("net.sf.jaer");

    /**
     * true if filter is enclosed by another filter
     */
    private boolean enclosed = false;
    /**
     * The enclosing filter
     */
    private EventFilter enclosingFilter = null;
    /**
     * The enclosed single filter. This object is used for GUI building - any
     * processing must be handled in filterPacket
     */
    protected EventFilter enclosedFilter;
    /**
     * An enclosed filterChain - these filters must be manually applied (coded)
     * in the filterPacket method but a GUI for them is automatically built.
     * Initially null - subclasses must make a new FilterChain and set it for
     * this filter.
     */
    protected FilterChain enclosedFilterChain;
    /**
     * This boolean controls annotation for filters that are FrameAnnotator
     */
    protected boolean annotationEnabled = true;
    /**
     * Used by filterPacket to say whether to filter events; default false
     */
    protected boolean filterEnabled = false;
    /**
     * Flags this EventFilter as "selected" for purposes of control
     */
    public boolean selected = false;
    //    /** true means the events are filtered in place, replacing the contents of the input packet and more
    //     *efficiently using memory. false means a new event packet is created and populated for the output of the filter.
    //     *<p>
    //     *default is false
    //     */
    //    protected boolean filterInPlaceEnabled=false;
    /**
     * chip that we are filtering for
     */
    protected AEChip chip;

    // for checkBlend()
    protected boolean hasBlendChecked = false;
    protected boolean hasBlend = false;

    protected PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();

    private static String defaultSeettingsFolder = System.getProperty("user.dir");

    static {
        try {
            File f = new File(System.getProperty("user.dir") + File.separator + "filterSettings");
            defaultSeettingsFolder = f.getPath();
            log.info("default filter settings file path is " + defaultSeettingsFolder);
        } catch (Exception e) {
            log.warning("could not locate default folder for filter settings relative to starting folder, using startup folder");
        }
    }

    /**
     * Returns path to default settings folder where .xml settings and other
     * data such as networks can be stored
     */
    protected String getDefaultSettingsFolder() {
        return defaultSeettingsFolder;
    }

    /**
     * Creates a new instance of AbstractEventFilter but does not enable it.
     *
     * @param chip the chip to filter for
     * @see #setPreferredEnabledState
     */
    public EventFilter(AEChip chip) {
        this.chip = chip;
        try {
            prefs = constructPrefsNode();
        } catch (Exception e) {
            log.warning("Constructing prefs for " + this + ": " + e.getMessage() + " cause=" + e.getCause());
        }
    }

    /**
     * should reset the filter to initial state
     */
    abstract public void resetFilter();

    /**
     * Can be used for lazy allocation and initialization of memory. It is
     * called after the AEChip is constructed and preferred filters are added to
     * the AEChip and after the EventFilter is added to the chip's FilterChain
     * via the ClassChooserDialog, when filterChain.contructPreferredFilters()
     * is called. The AEViewer will also exist before initFilter is called.
     */
    abstract public void initFilter();

    /**
     * Clean up that should run when before filter is finalized, e.g. dispose of
     * Components. Subclasses can override this method which does nothing by
     * default, to perform some action on shutdown and exit.
     */
    synchronized public void cleanup() {
        if (getEnclosedFilter() != null) {
            getEnclosedFilter().cleanup();
        }
        if (getEnclosedFilterChain() != null) {
            getEnclosedFilterChain().cleanup();
        }
    }

    /**
     * Filters can be enabled for processing.
     *
     * @return true if filter is enabled
     */
    public boolean isFilterEnabled() {
        return filterEnabled;
    }

    /**
     * Filters can be enabled for processing. Setting enabled also sets an
     * enclosed filter to the same state. Setting filter enabled state only
     * stores the preference value for enabled state if the filter is not
     * enclosed inside another filter, to avoid setting global preferences for
     * the filter enabled state.
     * <p>
     * Fires a property change event "filterEnabled" so that GUIs can be
     * updated.
     * </p>
     *
     * @param enabled true to enable filter. false should have effect that
     * output events are the same as input.
     * @see #setPreferredEnabledState
     */
    synchronized public void setFilterEnabled(boolean enabled) {
        boolean wasEnabled = filterEnabled;
        filterEnabled = enabled;
        if (getEnclosedFilter() != null) {
            getEnclosedFilter().setFilterEnabled(filterEnabled);
        }
        if (getEnclosedFilterChain() != null) {
            for (EventFilter f : getEnclosedFilterChain()) {
                f.setFilterEnabled(enabled);
            }
        }
        //        log.info(getClass().getName()+".setFilterEnabled("+filterEnabled+")");
        if (!isEnclosed()) {
            String key = prefsEnabledKey();
            prefs.putBoolean(key, enabled);
        }
        support.firePropertyChange("filterEnabled", wasEnabled, enabled);
    }

    /**
     * Set the controls visible for this filter
     *
     * @param yes true to show controls, false to collapse them
     */
    public void setControlsVisible(boolean yes) {
        FilterPanel p = getFilterPanel();
        if (p == null) {
            log.warning("FilterPanel for " + this + " is null; cannot set visibilty");
            return;
        }
        p.setControlsVisible(yes);
    }

    /**
     * Checks if controls are visible (expanded).
     *
     * @return true if expanded, false if null or collapsed.
     */
    public boolean isControlsVisible() {
        FilterPanel p = getFilterPanel();
        if (p == null) {
            return false;
        }
        return p.isControlsVisible();
    }

    /**
     * @return the chip this filter is filtering for
     */
    public AEChip getChip() {
        return chip;
    }

    /**
     * Get the FilterPanel view for controlling this filter if available.
     *
     * @return the FilterPanel or null if EventFilter does not have a view.
     */
    public FilterPanel getFilterPanel() {
        if (chip == null || chip.getFilterFrame() == null || chip.getFilterFrame().getFilterPanelForFilter(this) == null) {
            return null;
        }
        return chip.getFilterFrame().getFilterPanelForFilter(this);
    }

    /**
     * @param chip the chip to filter
     */
    public void setChip(AEChip chip) {
        this.chip = chip;
    }

    /**
     * Gets the enclosed filter
     *
     * @return the enclosed filter
     */
    public EventFilter getEnclosedFilter() {
        return enclosedFilter;
    }

    /**
     * Sets another filter to be enclosed inside this one - this enclosed filter
     * should be applied first and must be applied by the filter. This enclosed
     * filter is displayed hierarchically in the FilterPanel used in
     * FilterFrame.
     *
     * @param enclosedFilter the filter to enclose
     * @param enclosingFilter the filter that is enclosing this filter
     * @see #setEnclosed
     */
    public void setEnclosedFilter(final EventFilter enclosedFilter, final EventFilter enclosingFilter) {
        this.enclosedFilter = enclosedFilter;
        this.enclosingFilter = enclosingFilter;
        if (enclosedFilter != null) {
            enclosedFilter.setEnclosed(true, enclosingFilter);
        }
    }

    /**
     * Each filter has an annotationEnabled flag that is used to graphical
     * annotation of the filter, e.g. a spatial border, text strings, global
     * graphical overlay, etc. isAnnotationEnabled returns
     * <ul>
     * <li>false if filter is not FrameAnnotater.
     * <li>true if the filter is not enclosed and the filter. is enabled and
     * annotation is enabled.
     * <li>It returns false if the filter is enclosed and the enclosing filter
     * is not enabled.
     * </ul>
     *
     * @return true to show filter annotation should be shown
     */
    public boolean isAnnotationEnabled() {
        if (!(this instanceof FrameAnnotater)) {
            return false;
        }
        if (annotationEnabled && isFilterEnabled() && !isEnclosed()) {
            return true;
        }
        if (annotationEnabled && isFilterEnabled() && isEnclosed() && getEnclosingFilter().isFilterEnabled()) {
            return true;
        }
        return false;
    }

    /**
     * @param annotationEnabled true to draw annotations
     */
    public void setAnnotationEnabled(boolean annotationEnabled) {
        this.annotationEnabled = annotationEnabled;
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
     * Returns the enclosed filter chain
     *
     * @return the chain
     */
    public FilterChain getEnclosedFilterChain() {
        return enclosedFilterChain;
    }

    /**
     * Sets an enclosed filter chain which should by convention be processed
     * first by the filter (but need not be). Also flags all the filters in the
     * chain as enclosed.
     *
     * @param enclosedFilterChain the chain
     */
    public void setEnclosedFilterChain(FilterChain enclosedFilterChain) {
        if (this.enclosedFilterChain != null) {
            log.info("replacing existing enclosedFilterChain\n\t" + this.enclosedFilterChain + "\n with new enclosedFilterChain=\n\t " + enclosedFilterChain);
        }
        if (enclosedFilterChain.isEmpty()) {
            log.warning("empty filter chain in " + this + " - you should set the filter chain after all filters have been added to it so that enclosed filters can be processed");
        }
        this.enclosedFilterChain = enclosedFilterChain;
        for (EventFilter f : enclosedFilterChain) {
            f.setEnclosed(true, this);
        }
    }

    /**
     * @return the enclosing filter if this filter is enclosed
     */
    public EventFilter getEnclosingFilter() {
        return enclosingFilter;
    }

    /**
     * Sets the enclosing filter for this
     */
    public void setEnclosingFilter(EventFilter enclosingFilter) {
        this.enclosingFilter = enclosingFilter;
    }

    /**
     * Flags this EventFilter as "selected" by exposing the GUI controls for
     * filter parameters. This is different than "enabled".
     *
     * @return the selected
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Flags this EventFilter as selected. Used e.g. for GUI so that this can
     * control its user interface, e.g. so that mouse events are ignored if this
     * is not selected.
     *
     * @param selected the selected to set, true means this is "selected".
     */
    public void setSelected(boolean selected) {
        boolean old = this.selected;
        this.selected = selected;
        support.firePropertyChange("selected", old, selected);
    }

    /**
     * Every filter has a PropertyChangeSupport object. This support is used by
     * the FilterPanel GUI to be informed of property changes when property
     * setters fire a property change with the property name and old and new
     * values. This change in the property then will update the FilterPanel GUI
     * control value. For example, the following shows how the boolean property
     * <code>pathsEnabled</code> is handled.
     * <pre>
     *  public void setPathsEnabled(boolean pathsEnabled) {
     *      boolean old=this.pathsEnabled;
     *      this.pathsEnabled = pathsEnabled;
     *      getSupport().firePropertyChange("pathsEnabled", old, pathsEnabled);
     *      putBoolean("pathsEnabled", pathsEnabled);
     *  }
     * </pre> EventFilters can also use support for informing each other about
     * changes in state. An EventFilter declares itself to be a
     * PropertyChangeListener and then adds itself to another EventFilters
     * support as a PropertyChangeListener.
     *
     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }

    /**
     * Convenience method to set cursor on containing AEViewer window if it
     * exists, in case of long running operations. Typical usage is as follows:
     * <pre><code>
     * try{
     *     setCursor(new Cursor(Cursor.WAIT_CURSOR));
     *      // code to execute, in Swing thread
     * } finally {
     *      setCursor(Cursor.getDefaultCursor());
     * }
     * </code>
     * </pre>
     *
     * @param cursor
     */
    protected void setCursor(Cursor cursor) {
        if ((chip != null) && (chip.getAeViewer() != null)) {
            chip.getAeViewer().setCursor(cursor);
        }
    }

    /**
     * Convenience method to check if blending in OpenGL is available, and if
     * so, to turn it on.
     *
     * @param gl the GL2 context
     */
    protected void checkBlend(GL2 gl) {
        if (!hasBlendChecked) {
            hasBlendChecked = true;
            String glExt = gl.glGetString(GL.GL_EXTENSIONS);
            if (glExt.indexOf("GL_EXT_blend_color") != -1) {
                hasBlend = true;
            }
        }
        if (hasBlend) {
            try {
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
                gl.glBlendEquation(GL.GL_FUNC_ADD);
            } catch (GLException e) {
                log.warning("tried to use glBlend which is supposed to be available but got following exception");
                gl.glDisable(GL.GL_BLEND);
                e.printStackTrace();
                hasBlend = false;
            }
        }
    }

    /**
     * Checks if a preference value exists.
     *
     * @param key - the filter preference key header is prepended.
     * @return true if a non-null value exists
     * @since 1.6.0
     */
    protected boolean preferenceExists(String key) {
        return getPrefs().get(prefsKeyHeader() + key, null) != null;
    }

    /**
     * Shows a information-type message dialog in Swing thread; safe to call
     * from event filter processing thread.
     *
     * @param msg the string, can use HTML format for multiline or accenting. If
     * string contains newline(s), these are automatically converted to <br> in
     * HTML format. You can use MessageWithLink to include URL hyperlink.
     * @param title the dialog title, should be short
     *
     * @see MessageWithLink
     */
    protected void showPlainMessageDialogInSwingThread(final Object msg, final String title) {
        SwingUtilities.invokeLater(new Runnable() {
            // outside swing thread, must do this
            @Override
            public void run() {
                if ((msg instanceof String) && (((String) msg).contains("\n"))) {
                    String m = "<html>" + (String) msg;
                    String m2 = m.replaceAll("\n", "<br>");
                    JOptionPane.showMessageDialog(chip.getFilterFrame(), m2, title, JOptionPane.PLAIN_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(chip.getFilterFrame(), msg, title, JOptionPane.PLAIN_MESSAGE);
                }
            }
        });
    }

    /**
     * Shows a warning-type message dialog in Swing thread; safe to call from
     * event filter processing thread.
     *
     * @param msg the string object, can use HTML format for multiline or
     * accenting and MessageWithLink to include URL hyperlink
     * @param title the dialog title, should be short
     * @see MessageWithLink
     */
    protected void showWarningDialogInSwingThread(final Object msg, final String title) {
        SwingUtilities.invokeLater(new Runnable() {
            // outside swing thread, must do this
            @Override
            public void run() {
                if ((msg instanceof String) && (((String) msg).contains("\n"))) {
                    String m = "<html>" + (String) msg;
                    String m2 = m.replaceAll("\n", "<br>");
                    JOptionPane.showMessageDialog(chip.getFilterFrame(), m2, title, JOptionPane.WARNING_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(chip.getFilterFrame(), msg, title, JOptionPane.WARNING_MESSAGE);
                }
            }
        });
    }

    /**
     * Use this method in initFilter to add the property change listeners if
     * needed.
     *
     * @param chip
     * @see #propertyChange(java.beans.PropertyChangeEvent)
     */
    protected void maybeAddListeners(AEChip chip) {
        if (chip.getAeViewer() != null) {
            if (!addedViewerPropertyChangeListener) {
                chip.getAeViewer().getSupport().addPropertyChangeListener(this);
                addedViewerPropertyChangeListener = true;
            }
            if (!addTimeStampsResetPropertyChangeListener) {
                chip.getAeViewer().getSupport().addPropertyChangeListener(AEViewer.EVENT_TIMESTAMPS_RESET, this);
                addTimeStampsResetPropertyChangeListener = true;
            }
        }
    }

    /**
     * Handles PropertyChangeEvent sent to us from various sources.
     * <p>
     * Unfortunately we need to lazy add ourselves as listeners for these
     * property changes in the initFilter() method, or use maybeAddListeners in
     * filterPacket to ensure PropertyChangeEvent are sent to us.
     * <p>
     * The default implementation handles
     * <code>AEInputStreamEVENT_REWOUND</code> and
     * <code>AEViewer.EVENT_TIMESTAMPS_RESET</code> by resetting filter. It
     * handles <code>AEViewer.EVENT_FILEOPEN</code> by adding the filter to the
     * AEFileInputStream.
     *
     * @param evt
     * @see #maybeAddListeners(net.sf.jaer.chip.AEChip)
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if (this.filterEnabled) {
            switch (evt.getPropertyName()) {
                case AEViewer.EVENT_TIMESTAMPS_RESET:
                case AEInputStream.EVENT_REWOUND:
//                    resetFilter(); // already done by AEPlayer
                    break;
                case AEViewer.EVENT_FILEOPEN:
                    log.info("File Open");
                    AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
                    AEFileInputStreamInterface in = player.getAEInputStream();
                    in.getSupport().addPropertyChangeListener(this);
                    // Treat FileOpen same as a rewind
                    resetFilter();
                    break;
            }
        }
    }

    /**
     * By default returns getClass().getSimpleName().Subclasses can override.
     *
     * @return getClass().getSimpleName(), e.g. "BackgroundActivityFilter"
     */
    public String getShortName() {
        return getClass().getSimpleName();
    }

    /**
     * Export preferences for this EventFilter
     *
     * @param file
     */
    public void exportPrefs(File file) {
        try {
            String suffix = "";
            if (!file.getName().endsWith(".xml")) {
                suffix = ".xml";
            }
            file = new File(file.getPath() + suffix);
            // examine prefs for filters
            //                String path=null;
            //                for(EventFilter f:filterChain){
            //                    Preferences p=f.getPrefs();
            //                    path=p.absolutePath();
            ////                    System.out.println("filter "+f+" has prefs node name="+p.name()+" and absolute path="+p.absolutePath());
            //                }

            //                Preferences prefs=Preferences.userNodeForPackage(JAERViewer.class); // exports absolutely everything, which is not so good
            FileOutputStream fos = new FileOutputStream(file);
            prefs.exportSubtree(fos);
            log.info("exported prefs subTree " + prefs.absolutePath() + " to file " + file.getAbsolutePath());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Import preferences for this EventFilter from a file
     *
     * @param file
     */
    public void importPrefs(File file) {
        try {
            PreferencesMover.immportPrefs(file);  // we import the tree into *this* preference node, which is not the one exported (which is root node)
            log.info("imported preferences from " + file.toPath().toString());
            chip.getFilterFrame().renewContents();
            JOptionPane.showMessageDialog(chip.getFilterFrame(), String.format("<html>Loaded Preferences from <br>\t%s<br>and reconstructed the entire FilterChain", file.toPath()));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * The development status of an EventFilter. An EventFilter can implement
     * the static method getDevelopmentStatus which returns the filter's
     * DevelopmentStatus so that the EventFilter can be tagged or sorted for
     * selection.
     * <ul>
     * <li>Alpha - the filter is experimental.
     * <li>Beta - the filter functions but may have bugs.
     * <li>Released - the filter is in regular use.
     * <li>Unknown - the status is not known.
     * </ul>
     */
    public enum DevelopmentStatus {

        Alpha, Beta, Released, Unknown
    };

    /**
     * Override this enum to show the EventFilter's developement status.
     *
     * @see DevelopmentStatus
     */
    public static DevelopmentStatus getDevelopmentStatus() {
        return DevelopmentStatus.Unknown;
    }

    // preferences methods that add the filter name to the key automatically
    /**
     * Returns the Preferences node for this filter. This node is based on the
     * chip class package but may be modified to a sub-node if the filter is
     * enclosed inside another filter.
     *
     * @return the preferences node
     * @see #setEnclosed
     */
    public Preferences getPrefs() {
        return prefs;
    }

    /**
     * Synonym for getPrefs().
     *
     * @return the prefs node
     * @see #getPrefs()
     */
    public Preferences prefs() {
        return prefs;
    }

    /**
     * Sets the preferences node for this filter
     *
     * @param prefs the node
     */
    public void setPrefs(Preferences prefs) {
        this.prefs = prefs;
    }

    /**
     * Constructs the prefs node for this EventFilter. It is based on the Chip
     * preferences node if the chip exists, otherwise on the EventFilter class
     * package. If the filter is enclosed, then the node is named for the enclosed filter (this one) as a 
     * child of the enclosing filter node.
     * 
     * This way, filters that are enclosed have
     * individualized preferences depending on where they are enclosed.
     * 
     * Example: Info filter encloses TypedEventRateEstimator filter and is a filter for Davis346Red chip, 
     * so we have jaer/chips/Davis346Red/Info/TypedEventRateEstimator
     * 
     */
    private Preferences constructPrefsNode() {
        Preferences prefs;
        if (chip == null) {
            prefs = Preferences.userNodeForPackage(getClass()); // base on EventFilter.class package
            log.warning("null chip, basing prefs on this EventFilter package");
        } else {
            prefs = chip.getPrefs(); // base on chip class
        }

        // At this point, prefs is the chip prefs
        // are we being constructed by the initializer of an enclosing filter?
        // if so, we should set up our preferences node so that we use a preferences node
        // that is unique for the enclosing filter
        // if we are being constucted inside another filter's init, make a new
        // prefs node that is derived from the enclosing filter class name.
        // march down the trace from the root (start of java) 
        // until we find the first constructor that is an EventFitler (could be ourselves).
        // then construct a prefs node starting from chip prefs and that root to ourselves.
        // The prefs node expresses that tree chipprefs/top/next/.../us, all using class simple names to keep it managably short
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        Class enclosingClass = null;
        Class aeChipClass = null;
        int aeChipOrEventFilterTraceIdx = -1;
        // find the constructor of AEChip that made us by marching backwards up on the stack trace
        for (int i = trace.length - 1; i >= 0; i--) {
            if (trace[i].getMethodName().equals("<init>")) { // constructor
                try {
                    Class c = Class.forName(trace[i].getClassName());
                    if (c == chip.getClass()) { // we found the place in stack trace that is the AEChip for this filter
                        aeChipClass = c;
                        aeChipOrEventFilterTraceIdx = i;
                        break;
                    }
                } catch (ClassNotFoundException e) {
                    log.severe(e.toString());
                }
            }
        }
        enclosingClass = null;
        if (aeChipClass == null) { // this happens if we renew the FilterChain for a chip by e.g. changing it by FilterFrame.renewContents()
            log.fine(String.format("Assuming that prefs starts at the current AEChip prefs node because we could not find constructor AEchip for %s", getClass().getSimpleName()));
            // no chip constructor, look for first concrete EventFilter
            for (int i = trace.length - 1; i >= 0; i--) {
                if (trace[i].getMethodName().equals("<init>")) { // constructor
                    try {
                        Class c = Class.forName(trace[i].getClassName());
                        if (!Modifier.isAbstract(c.getModifiers()) && EventFilter.class.isAssignableFrom(c)) { // we found the top EventFilter
                            enclosingClass = c;
                            aeChipOrEventFilterTraceIdx = i;
                            break;
                        }
                    } catch (ClassNotFoundException e) {
                        log.severe(e.toString());
                    }
                }

            }
        }
        enclosingClass = null;
        // now continue up the stack trace until we are at ourselves, adding a node for each concrete EventFilter
        for (int i = aeChipOrEventFilterTraceIdx; i >= 0; i--) {
            if (trace[i].getMethodName().equals("<init>")) {  // constructor
                try {
                    Class c = Class.forName(trace[i].getClassName());
                    if (!Modifier.isAbstract(c.getModifiers()) && EventFilter.class.isAssignableFrom(c)) {
                        if (enclosingClass == null) {
                            enclosingClass = c; // mark the top EventFilter that made us (maybe ourselves)
                        }
                        prefs = prefs.node(c.getSimpleName()); // add a node to this chip's prefs for this event filter, might enclose us or might be us
                        if (c == this.getClass()) {
                            break; // stop when we reach ourselves
                        }
                    }
                } catch (ClassNotFoundException e) {
                    log.severe(e.toString());
                }
            }
        }
        boolean isEnclosed = enclosingClass != getClass();
        String chipName = (aeChipClass == null ? "(unknown)" : aeChipClass.getSimpleName());
        if (isEnclosed) {
            log.fine(String.format("Chip %s prefs node for %s enclosed in %s is %s", chipName, getClass().getSimpleName(), enclosingClass.getSimpleName(), prefs.absolutePath()));
        } else {
            log.fine(String.format("Chip %s prefs node for %s is %s", chipName, getClass().getSimpleName(), prefs.absolutePath()));
        }
//        if (PreferencesMover.hasOldChipPreferences(chip)) {
//            log.warning(String.format("Chip %s has old style preferences", chip.getClass().getSimpleName()));
//        }
        return prefs;
    }

    /**
     * Sets the filter enabled according to the preference for enabled
     */
    public void setPreferredEnabledState() {
        setFilterEnabled(prefs.getBoolean(prefsEnabledKey(), filterEnabled));
    }

    /**
     * Returns the prefernces key for the filter
     *
     * @return "<SimpleClassName>.filterEnabled" e.g.
     * DirectionSelectiveFilter.filterEnabled
     */
    public String prefsEnabledKey() {
        String key = this.getClass().getSimpleName() + ".filterEnabled";
        return key;
    }

    /**
     * Returns if the property preference has previously been stored. Can be
     * used to set a default value for a property if it has not yet been
     * initialized.
     * <p>
     * For example, to check if a parameter deltaTime has already been set by
     * user, use <code>isPreferenceStored("deltaTime")</code>.
     *
     * @return true is a String "getString" on the key returns non-null.
     */
    public boolean isPreferenceStored(String key) {
        return getString(key, null) != null;
    }

    /**
     * The header part of the Preferences key, e.g. "BackgroundActivityFilter.".
     *
     * @return the string header for built-in preferences.
     */
    protected String prefsKeyHeader() {
        return getClass().getSimpleName() + ".";
    }

    /**
     * Puts initial preference value if the value has not already been set to a
     * value different that default value. This method is intended for
     * subclasses to override the default preferred value. For instance, a
     * subclass, or a filter that uses another one, can select the default
     * starting value, for example if using a tracker, the tracker could be
     * configured for to track only a single object.
     *
     *
     * @param key the preference key, e.g. "dt"
     * @param value the initial preference value, e.g. 10
     * @return true if successful, false if there was already a preference value
     * there that is not the default one.
     */
    public boolean putInitialPreferenceValue(String key, Object value) {
        if ((key == null) || (value == null)) {
            log.warning("null key or value, doing nothing");
            return false;
        }
        if (value instanceof String) {
            if (!getString(key, (String) value).equals(value)) {
                putString(key, (String) value);
                return true;
            } else {
                return false;
            }
        } else if (value instanceof Boolean) {
            if ((getBoolean(key, (Boolean) value) != (Boolean) value)) {
                putBoolean(key, (Boolean) value);
                return true;
            } else {
                return false;
            }
        } else if (value instanceof Integer) {
            if ((getInt(key, (Integer) value) != (Integer) value)) {
                putInt(key, (Integer) value);
                return true;
            } else {
                return false;
            }
        } else if (value instanceof Long) {
            if ((getLong(key, (Long) value) != (Long) value)) {
                putLong(key, (Long) value);
                return true;
            } else {
                return false;
            }
        } else if (value instanceof Float) {
            if ((getFloat(key, (Float) value) != (Float) value)) {
                putFloat(key, (Float) value);
                return true;
            } else {
                return false;
            }
        } else if (value instanceof Double) {
            if ((getDouble(key, (Double) value) != (Double) value)) {
                putDouble(key, (Double) value);
                return true;
            } else {
                return false;
            }
        } else {
            log.warning("cannot handle initial preferred value of type " + value.getClass() + " of value " + value);
            return false;
        }

    }

    // <editor-fold defaultstate="collapsed" desc="-- putter Methods for types of preference variables --">
    /**
     * Puts a preference for arbitrary Object, using PrefObj utility.
     *
     * @param key the property name, e.g. "hotPixels"
     * @param o the Serializable Object to be stored, e.g. some instance of
     * HotPixelMap
     */
    public void putObject(String key, Serializable o) {
        try {
            PrefObj.putObject(prefs, prefsKeyHeader() + key, o);
        } catch (IOException e) {
            log.warning(String.format("Could not store preference for %s; got %s", key, e));
        } catch (BackingStoreException e) {
            log.warning(String.format("Could not store preference for %s; got %s", key, e));
        } catch (ClassNotFoundException e) {
            log.warning(String.format("Could not store preference for %s; got %s", key, e));
        }
    }

    /**
     * Gets a preference for arbitrary Object, using PrefObj utility.
     *
     * @param key the property name, e.g. "hotPixels"
     * @param defObject the default Object
     */
    public Object getObject(String key, Object defObject) {
        try {
            preferencesMap.put(key, Object.class);
            Object o = PrefObj.getObject(getPrefs(), prefsKeyHeader() + key);
            if (o == null) {
                return defObject;
            }
            return o;
        } catch (IOException ex) {
            log.finer(String.format("%s has no stored preference for %s", getShortName(), key));
        } catch (BackingStoreException ex) {
            log.warning(String.format("Could not load preference for %s; got %s", key, ex));
        } catch (ClassNotFoundException ex) {
            log.warning(String.format("Could not load preference for %s; got %s", key, ex));
        }
        return defObject;
    }

    /**
     * Puts a preference.
     *
     * @param key the property name, e.g. "tauMs"
     * @param value the value to be stored
     */
    public void putLong(String key, long value) {
        prefs.putLong(prefsKeyHeader() + key, value);
    }

    /**
     * Puts a preference.
     *
     * @param key the property name, e.g. "tauMs"
     * @param value the value to be stored
     */
    public void putInt(String key, int value) {
        prefs.putInt(prefsKeyHeader() + key, value);
        log.finer(String.format("Put %s for key %s to Preferences node %s", value, key, prefs.absolutePath()));
    }

    /**
     * Puts a preference.
     *
     * @param key the property name, e.g. "tauMs"
     * @param value the value to be stored
     */
    public void putFloat(String key, float value) {
        prefs.putFloat(prefsKeyHeader() + key, value);
    }

    /**
     * Puts a preference.
     *
     * @param key the property name, e.g. "tauMs"
     * @param value the value to be stored
     */
    public void putDouble(String key, double value) {
        prefs.putDouble(prefsKeyHeader() + key, value);
        log.finer(String.format("Put %s for key %s to Preferences node %s", value, key, prefs.absolutePath()));
    }

    /**
     * Stores a byteArray. Can only store up to maximum size for Preferences
     * value.
     *
     * @param key the property name, e.g. "tauMs"
     * @param value the value to be stored
     */
    public void putByteArray(String key, byte[] value) {
        prefs.putByteArray(prefsKeyHeader() + key, value);
    }

    /**
     * Puts a preference for float array, up to max size supported by
     * Preferences value.
     *
     * @param key the property name, e.g. "tauMs"
     * @param value the value to be stored
     */
    public void putFloatArray(String key, float[] value) {
        prefs.putInt(prefsKeyHeader() + key + "Length", value.length);
        for (int i = 0; i < value.length; i++) {
            prefs.putFloat(prefsKeyHeader() + key + i, value[i]);
        }
    }

    /**
     * Puts a preference.
     *
     * @param key the property name, e.g. "tauMs"
     * @param value the value to be stored
     */
    public void putBoolean(String key, boolean value) {
        prefs.putBoolean(prefsKeyHeader() + key, value);
        log.finer(String.format("Put %s for key %s to Preferences node %s", value, key, prefs.absolutePath()));
    }

    /**
     * Puts a preference string.
     *
     * @param key the property name, e.g. "tauMs"
     * @param value the value to be stored
     */
    public void putString(String key, String value) {
        prefs.put(prefsKeyHeader() + key, value);
        log.finer(String.format("Put %s for key %s to Preferences node %s", value, key, prefs.absolutePath()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="-- getter Methods for all types of preference variables --">
    /**
     * Gets a preference from the built in preferences node.
     *
     * @param key the property name, e.g. "tauMs"
     * @param def the value to be stored
     * @return long value
     */
    public long getLong(String key, long def) {
        preferencesMap.put(key, Long.TYPE);
        return prefs.getLong(prefsKeyHeader() + key, def);
    }

    /**
     * Gets a preference from the built in preferences node.
     *
     * @param key the property name, e.g. "tauMs".
     * @param def the default value if there is no preference already stored.
     * @return int value
     */
    public int getInt(String key, int def) {
        preferencesMap.put(key, Integer.TYPE);
        return prefs.getInt(prefsKeyHeader() + key, def);
    }

    /**
     * Gets a preference from the built in preferences node.
     *
     * @param key the property name, e.g. "tauMs".
     * @param def the default value if there is no preference already stored.
     * @return float value
     */
    public float getFloat(String key, float def) {
        preferencesMap.put(key, Float.TYPE);
        return prefs.getFloat(prefsKeyHeader() + key, def);
    }

    /**
     * Gets a preference from the built in preferences node.
     *
     * @param key the property name, e.g. "tauMs".
     * @param def the default value if there is no preference already stored.
     * @return double value
     */
    public double getDouble(String key, double def) {
        preferencesMap.put(key, Double.TYPE);
        return prefs.getDouble(prefsKeyHeader() + key, def);
    }

    /**
     * Gets a preference from the built in preferences node.
     *
     * @param key the property name, e.g. "tauMs".
     * @param def the default value if there is no preference already stored.
     * @return byte[] in preferences
     */
    public byte[] getByteArray(String key, byte[] def) {
        preferencesMap.put(key, byte[].class);
        return prefs.getByteArray(prefsKeyHeader() + key, def);
    }

    /**
     * Gets a preference from the built in preferences node.
     *
     * @param key the property name, e.g. "tauMs".
     * @param def the default value if there is no preference already stored.
     * @return float[] in preferences
     */
    public float[] getFloatArray(String key, float[] def) {
        preferencesMap.put(key, float[].class);
        int length = prefs.getInt(prefsKeyHeader() + key + "Length", 0);
        if (def.length != length) {
            return def;
        }
        float[] outArray = new float[length];
        for (int i = 0; i < length; i++) {
            outArray[i] = prefs.getFloat(prefsKeyHeader() + key + i, 0.0f);
        }
        return outArray;
    }

    /**
     * Gets a preference from the built in preferences node.
     *
     * @param key the property name, e.g. "tauMs".
     * @param def the default value if there is no preference already stored.
     * @return boolean value
     */
    public boolean getBoolean(String key, boolean def) {
        preferencesMap.put(key, Boolean.TYPE);
        return prefs.getBoolean(prefsKeyHeader() + key, def);
    }

    /**
     * Gets a preference string from the built in preferences node.
     *
     * @param key the property name, e.g. "tauMs".
     * @param def the default value if there is no preference already stored.
     * @return string value
     */
    public String getString(String key, String def) {
        preferencesMap.put(key, String.class);
        return prefs.get(prefsKeyHeader() + key, def);
    }
    // </editor-fold>

    /**
     * Returns Description value of this filter as annotated by Description
     * annotation. Note this method is not static and requires the EventFilter
     * to already be constructed. The ClassChooser dialog uses the annotation to
     * obtain class Descriptions without constructing the objects first.
     *
     * @return the String description (the value() of the Description) or null
     * if no description is available or any exception is thrown.
     */
    public String getDescription() {
        try {
            Class c = this.getClass();
            Description d = (Description) c.getAnnotation(Description.class);
            if (d == null) {
                return null;
            }

            return d.value();
        } catch (Exception e) {
            return null;
        }
    }

    private void showInBrowser(String url) {
        if (!Desktop.isDesktopSupported()) {
            log.warning("No Desktop support, can't show help from " + url);
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException ex) {
            log.warning("Couldn't show " + url + "; caught " + ex);
        }
    }

    /**
     * Shows help from the jAER wiki page for filter documentation
     */
    public void showHelpInBrowser() {
        showInBrowser(EventFilter.HELP_WIKI_URL + "filt." + getClass().getName());
    }

    /**
     * Adds a property to a group, creating the group if needed.
     *
     * @param groupName a named parameter group.
     * @param propertyName the property name.
     */
    public void addPropertyToGroup(String groupName, String propertyName) {
        tooltipSupport.addPropertyToGroup(groupName, propertyName);
    }

    /**
     * Developers can use setPropertyTooltip to add an optional tool-tip for a
     * filter property so that the tip is shown as the tool-tip for the label or
     * check-box property in the generated GUI.
     * <p>
     * In netbeans, you can add this macro to ease entering tooltips for filter
     * parameters:
     * <pre>
     * select-word copy-to-clipboard caret-begin-line caret-down "{setPropertyTooltip(\"" paste-from-clipboard "\",\"\");}" insert-break caret-up caret-end-line caret-backward caret-backward caret-backward caret-backward
     * </pre>
     *
     * @param propertyName the name of the property (e.g. an int, float, or
     * boolean, e.g. "dt")
     * @param tooltip the tooltip String to display
     */
    final public void setPropertyTooltip(String propertyName, String tooltip) {
        tooltipSupport.setPropertyTooltip(propertyName, tooltip);
    }

    /**
     * Convenience method to add properties to groups along with adding a tip
     * for the property.
     *
     * @param groupName the property group name.
     * @param propertyName the property name.
     * @param tooltip the tip.
     * @see #TOOLTIP_GROUP_GLOBAL
     */
    final public void setPropertyTooltip(String groupName, String propertyName, String tooltip) {
        tooltipSupport.setPropertyTooltip(groupName, propertyName, tooltip);
    }

    /**
     * Convenience method to add properties to groups along with adding a tip
     * for the property and marking the property as commonly used.
     *
     * @param groupName the property group name.
     * @param propertyName the property name.
     * @param tooltip the tip.
     * @see #TOOLTIP_GROUP_GLOBAL
     */
    final public void setPropertyTooltipBold(String groupName, String propertyName, String tooltip) {
        tooltipSupport.setPropertyTooltip(groupName, propertyName, tooltip);
        tooltipSupport.markPropertyAsPreferred(propertyName);
    }

    /**
     * Mark a property to be rendered in bold to make it easier to see
     *
     * @param propertyName
     */
    final public void markPropertyAsPreferred(String propertyName) {
        tooltipSupport.markPropertyAsPreferred(propertyName);
    }

    /**
     * @return the tooltip for the property
     */
    @Override
    final public String getPropertyTooltip(String propertyName) {
        return tooltipSupport.getPropertyTooltip(propertyName);
    }

    /**
     * Returns true if property is marked bold (preferred)
     *
     * @param propertyName
     * @return true if should be rendered bold
     * @see net.sf.jaer.Preferred
     */
    public boolean isPropertyPreferred(String propertyName) {
        return tooltipSupport.isPropertyPreferred(propertyName);
    }

    /**
     * Returns the name of the property group for a property.
     *
     * @param propertyName the property name string.
     * @return the property group name.
     */
    public String getPropertyGroup(String propertyName) {
        return tooltipSupport.getPropertyGroup(propertyName);
    }

    /**
     * Gets the list of property names in a particular group.
     *
     * @param groupName the name of the group.
     * @return the ArrayList of property names in the group.
     */
    public ArrayList<String> getPropertyGroupList(String groupName) {
        return tooltipSupport.getPropertyGroupList(groupName);
    }

    /**
     * Returns the set of property groups.
     *
     * @return Set view of property groups.
     */
    public Set<String> getPropertyGroupSet() {
        return tooltipSupport.getPropertyGroupSet();
    }

    /**
     * Returns the mapping from property name to group name. If null, no groups
     * have been declared.
     *
     * @return the map, or null if no groups have been declared by adding any
     * properties.
     * @see #property2GroupMap
     */
    public HashMap<String, String> getPropertyGroupMap() {
        return tooltipSupport.getPropertyGroupMap();
    }

    /**
     * Returns true if the filter has property groups.
     */
    public boolean hasPropertyGroups() {
        return tooltipSupport.hasPropertyGroups();
    }

    /**
     * Finds in this filter's enclosing filter chain an instance of a particular
     * class of filter
     *
     * @param filterClass the class of the filter to be searched for
     * @return the last instance of the class of filter, or null if there is no
     * instance
     */
    protected EventFilter findFilter(Class<? extends EventFilter> filterClass) {
        EventFilter rtn = null;
        for (EventFilter f : chip.getFilterChain()) {
            if (f.getClass() == filterClass) {
                rtn = f;
            }
        }
        return rtn;
    }

    /**
     * List of hidden properties that subclasses can populate to hide properties
     * in FilterPanel GUI.
     *
     * @see FilterPanel
     * @see #hideProperty(java.lang.String)
     */
    protected ArrayList<String> hiddenProperties = new ArrayList();

    /**
     * Exclude a property from Introspector to hide it from FilterPanel GUI.
     *
     * @param propertyName the property name.
     *
     * @see FilterPanel
     */
    protected void hideProperty(String propertyName) {
        if (!hiddenProperties.contains(propertyName)) {
            hiddenProperties.add(propertyName);
        }
    }

    /**
     * Returns true if the propertyName is meant to be hidden from GUI
     *
     * @param propertyName
     * @return true if in hidden list
     */
    public boolean isPropertyHidden(String propertyName) {
        return hiddenProperties.contains(propertyName);
    }

    public class CopiedProps {

        public Class sourceClass = null;
        public HashMap<String, Object> properties;

        public CopiedProps(Class sourceClass, HashMap<String, Object> copiedProps) {
            this.properties = copiedProps;
            this.sourceClass = sourceClass;
        }

        public String toString() {
            return String.format("CopiedProps for %s with %d properties", sourceClass.getSimpleName(), properties.size());
        }

        public boolean isEmpty() {
            return properties == null || properties.isEmpty();
        }

        public int size() {
            return properties == null ? 0 : properties.size();
        }
    }

    public CopiedProps copyProperties() throws IntrospectionException, IllegalAccessException, InvocationTargetException {
        BeanInfo info = Introspector.getBeanInfo(getClass());
        PropertyDescriptor[] props = info.getPropertyDescriptors();
        HashMap<String, Object> copiedProps = new HashMap();
        for (PropertyDescriptor p : props) {
            String propName = p.getName();
            if (excludeProp(propName)) {
                continue;
            }
            if ((p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                Method r = p.getReadMethod();
                Object value = r.invoke(this);
                copiedProps.put(propName, value);
                log.finer(String.format("Copied %s=%s", propName, value));
            }
        }
        log.fine(String.format("copied %d properties", copiedProps.size()));
        return new CopiedProps(getClass(), copiedProps);
    }

    public void pasteProperties(CopiedProps copiedProps) throws IntrospectionException, IllegalAccessException, InvocationTargetException, ClassCastException {
        if (copiedProps == null || copiedProps.sourceClass == null || copiedProps.properties == null || copiedProps.properties.isEmpty()) {
            log.warning("Nothing to paste");
            return;
        }
        if (copiedProps.sourceClass != this.getClass()) {
            throw new ClassCastException(String.format("Cannot paste from %s to %s", copiedProps.sourceClass, getClass().toString()));
        }
        BeanInfo info = Introspector.getBeanInfo(getClass());
        PropertyDescriptor[] props = info.getPropertyDescriptors();
        for (PropertyDescriptor p : props) {
            String propName = p.getName();
            if (excludeProp(propName)) {
                continue;
            }
            Object value = copiedProps.properties.get(propName);
            if (value != null) {
                if ((p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                    Method w = p.getWriteMethod();
                    w.invoke(this, value);
                    log.finer(String.format("Pasted %s=%s", propName, value));
                }
            }
        }
        log.fine(String.format("pasted %d properties", copiedProps.properties.size()));
    }

    private boolean excludeProp(String propName) {
        return "chip".equals(propName)
                || "annotationEnabled".equals(propName)
                || "filterEnabled".equals(propName)
                || "prefs".equals(propName)
                || "enclosedFilter".equals(propName)
                || "enclosedFilterChain".equals(propName)
                || "selected".equals(propName);
    }

}
