/*
 * AbstractEventFilter.java
 *
 * Created on October 30, 2005, 4:58 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.eventprocessing;

import java.util.ArrayList;
import net.sf.jaer.chip.*;
import java.beans.*;
import java.util.HashMap;
import java.util.Observable;
import java.util.Set;
import java.util.logging.Logger;
import java.util.prefs.*;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * An abstract class that all event processing methods should subclass.
 * Subclasses are introspected to build a GUI to control the filter in {@link FilterPanel} - see this class to see how to
 * add these introspected controls to an EventFilter GUI control panel.
 * 
 * <p>
 * Filters that are enclosed inside another filter are given a
 * preferences node that is derived from
 * the the chip class that the filter is used on and the enclosing filter class.
 * The same preferences node name is used for FilterChain's that are enclosed inside an EventFilter.
 *<p>
Fires PropertyChangeEvent for the following
<ul>
<li> "filterEnabled" - when the filter is enabled or disabled this event is fired, if the subclass has not overridden the setFilterEnabled method
</ul>

 *@see FilterPanel FilterPanel - which is where EventFilter's GUIs are built.
 * @see net.sf.jaer.graphics.FrameAnnotater FrameAnnotator - to annotate the graphical output.
 * @see net.sf.jaer.eventprocessing.EventFilter2D EventFilter2D - which processes events.
 * @see net.sf.jaer.eventprocessing.FilterChain FilterChain - about enclosing filters inside other filters.
 * @author tobi
 */
public abstract class EventFilter extends Observable {

    public EventProcessingPerformanceMeter perf;
    /** The preferences for this filter, by default in the EventFilter package node
     * @see setEnclosed
     */
    private Preferences prefs = null; // default null, constructed when AEChip is known Preferences.userNodeForPackage(EventFilter.class);
    /** Can be used to provide change support, e.g. for enabled state */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);
    /** All filters can log to this logger */
    public Logger log = Logger.getLogger("EventFilter");
    /** true if filter is enclosed by another filter */
    private boolean enclosed = false;
    /** The enclosing filter */
    private EventFilter enclosingFilter = null;
    /** Used by filterPacket to say whether to filter events; default false */
    protected boolean filterEnabled = false;
//    /** true means the events are filtered in place, replacing the contents of the input packet and more
//     *efficiently using memory. false means a new event packet is created and populated for the output of the filter.
//     *<p>
//     *default is false
//     */
//    protected boolean filterInPlaceEnabled=false;
    /** chip that we are filtering for */
    protected AEChip chip;

    /** default constructor
     * @deprecated - all filters need an AEChip object
     */
    public EventFilter() {
//        perf=new EventProcessingPerformanceMeter(this);
//        setFilterEnabled(prefs.getBoolean(prefsKey(),false)); // this cannot easily be called here because it will be called during init of subclasses which have
        // not constructed themselves fully yet, e.g. field objects will not have been constructed. therefore, we set initial active states of all filters in FilterFrame after they are
        // all constructed with a Chip object.
    }

    /** Creates a new instance of AbstractEventFilter but does not enable it.
     *@param chip the chip to filter for
     * @see #setPreferredEnabledState
     */
    public EventFilter(AEChip chip) {
        this.chip = chip;
        try {
            prefs = constructPrefsNode();
//            log.info(this+" has prefs="+prefs);
        } catch (Exception e) {
            log.warning("Constructing prefs for " + this + ": " + e.getMessage() + " cause=" + e.getCause());
        }
    }

    /** Returns the prefernces key for the filter
     * @return "<SimpleClassName>.filterEnabled" e.g. DirectionSelectiveFilter.filterEnabled
     */
    public String prefsEnabledKey() {
        String key = this.getClass().getSimpleName() + ".filterEnabled";
        return key;
    }

////    /**
////     * filters in to out. if filtering is enabled, the number of out may be less
////     * than the number put in
////     *@param in input events can be null or empty.
////     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
////     */
////    abstract public AEPacket filter(AEPacket in) ;
////    abstract public ch.unizh.ini.caviar.aemonitor.AEPacket2D filter(ch.unizh.ini.caviar.aemonitor.AEPacket2D in);
//    /** should return the filter state in some useful form
//     * @deprecated - no one uses this
//     */
//    abstract public Object getFilterState();

    /** should reset the filter to initial state */
    abstract public void resetFilter();

    /** Should allocate and initialize memory;
     * it may be called when the chip e.g. size parameters are changed
     * after creation of the filter. */
    abstract public void initFilter();

    /** Clean up that should run when before filter is finalized, e.g. dispose of Components.
     Subclasses can override this method which does nothing by default.
     */
    synchronized public void cleanup(){

    }

    /** Filters can be enabled for processing.
     * @return true if filter is enabled */
    synchronized public boolean isFilterEnabled() {
        return filterEnabled;
    }

    /** Filters can be enabled for processing.
     * Setting enabled also sets an enclosed filter to the same state.
     * Setting filter enabled state only stores the preference value for enabled state
     * if the filter is not enclosed inside another filter,
     * to avoid setting global preferences for the filter enabled state.
    <p>
     * Fires a property change event "filterEnabled" so that GUIs can be updated.
     *</p>
     * @param enabled true to enable filter. false should have effect that
     * output events are the same as input.
     * @see #setPreferredEnabledState
     */
    synchronized public void setFilterEnabled(boolean enabled) {
        boolean wasEnabled = this.filterEnabled;
        this.filterEnabled = enabled;
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
        support.firePropertyChange("filterEnabled", new Boolean(wasEnabled), new Boolean(enabled));
    }

    /** Sets the filter enabled according to the preference for enabled */
    public void setPreferredEnabledState() {
        setFilterEnabled(prefs.getBoolean(prefsEnabledKey(), filterEnabled));
    }

    /** @return the chip this filter is filtering for */
    public AEChip getChip() {
        return chip;
    }

    /** @param chip the chip to filter */
    public void setChip(AEChip chip) {
        this.chip = chip;
    }

    /** Fires PropertyChangeEvents when filter is enabled or disabled with key "filterEnabled"
     * @return change support
     */
    public PropertyChangeSupport getPropertyChangeSupport() {
        return support;
    }
//    /** @deprecated - no one uses this */
//    public boolean isFilterInPlaceEnabled() {
//        return this.filterInPlaceEnabled;
//    }
//
//    /** @deprecated - not used */
//    public void setFilterInPlaceEnabled(final boolean filterInPlaceEnabled) {
//        support.firePropertyChange("filterInPlaceEnabled",new Boolean(this.filterInPlaceEnabled),new Boolean(filterInPlaceEnabled));
//        this.filterInPlaceEnabled = filterInPlaceEnabled;
//    }
    /** The enclosed single filter. This object is used for GUI building - any processing must be handled in filterPacket */
    protected EventFilter enclosedFilter;
    /** An enclosed filterChain - these filters must be manually applied (coded) in the filterPacket method but
     * a GUI for them is automagically built. Initially null - subclasses must make a new FilterChain and set it for this filter.
     */
    protected FilterChain enclosedFilterChain;

    /** Gets the enclosed filter
     * @return the enclosed filter
     */
    public EventFilter getEnclosedFilter() {
        return this.enclosedFilter;
    }

    /** Sets another filter to be enclosed inside this one - this enclosed filter should be applied first and must be applied by the filter.
     *This enclosed filter is displayed hierarchically in the FilterPanel used in FilterFrame.
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
    
    /** This boolean controls annotation for filters that are FrameAnnotator */
    protected boolean annotationEnabled = true;

    /** Each filter has an annotationEnabled flag that is used to graphical annotation
     * of the filter, e.g. a spatial border, text strings, global graphical overlay, etc.
     *
     * isAnnotationEnabled returns
     * <ul>
     * <li>false if filter is not FrameAnnotater.
     * <li>true if the filter is not enclosed and the filter.
     * <li>is enabled and annotation is enabled.
     * <li>It returns false if the filter is enclosed and the enclosing filter is not enabled.
     * </ul>
     * @return true to show filter annotation should be shown
     */
    public boolean isAnnotationEnabled() {
        if(!(this instanceof FrameAnnotater)) return false;
        if (annotationEnabled && isFilterEnabled() && !isEnclosed()) {
            return true;
        }
        if (annotationEnabled && isFilterEnabled() && isEnclosed() && getEnclosingFilter().isFilterEnabled()) {
            return true;
        }
        return false;
    }

    /**@param annotationEnabled true to draw annotations */
    public void setAnnotationEnabled(boolean annotationEnabled) {
        this.annotationEnabled = annotationEnabled;
    }

    /** Is filter enclosed inside another filter?
     * @return true if this filter is enclosed inside another */
    public boolean isEnclosed() {
        return enclosed;
    }

    /** Sets flag to show this instance is enclosed. If this flag is set to true, then
     * preferences node is changed to a node unique for the enclosing filter class.
     *
     * @param enclosingFilter the filter that is enclosing this
     * @param enclosed true if this filter is enclosed
     */
    public void setEnclosed(boolean enclosed, final EventFilter enclosingFilter) {
        this.enclosed = enclosed;
        this.enclosingFilter = enclosingFilter;
    }
    /** The key,value table of property tooltip strings. */
    protected HashMap<String, String> propertyTooltipMap = null;

    /** The key,value table of propery group associations.
     * The key the property name and the value is the group name.*/
    protected HashMap<String, String> property2GroupMap = null;

    /** The keys are the names of property groups, and the values are lists of properties in the key's group.*/
    protected HashMap<String, ArrayList<String>> group2PropertyListMap=null;




    /** Developers can use setPropertyTooltip to add an optional tooltip for a filter property so that the tip is shown
     * as the tooltip for the label or checkbox property in the generated GUI.
     * <p>
     * In netbeans, you can add this macro to ease entering tooltips for filter parameters:
     * <pre>
     * select-word copy-to-clipboard caret-begin-line caret-down "{setPropertyTooltip(\"" paste-from-clipboard "\",\"\");}" insert-break caret-up caret-end-line caret-backward caret-backward caret-backward caret-backward
     * </pre>
     *
     * @param propertyName the name of the property (e.g. an int, float, or boolean, e.g. "dt")
     * @param tooltip the tooltip String to display
     */
    protected void setPropertyTooltip(String propertyName, String tooltip) {
        if (propertyTooltipMap == null) {
            propertyTooltipMap = new HashMap<String, String>();
        }
        propertyTooltipMap.put(propertyName, tooltip);
    }

    /** Use this key for global parameters in your filter constructor, as in
     * <pre>
           setPropertyTooltip(PARAM_GROUP_GLOBAL, "propertyName", "property tip string");
     </pre>
     */
    public static final String PARAM_GROUP_GLOBAL="Global";

    /** Convenience method to add properties to groups along with adding a tip for the property.
     *
     * @param groupName the property group name.
     * @param propertyName the property name.
     * @param tooltip the tip.
     * @see #PARAM_GROUP_GLOBAL
     */
    protected void setPropertyTooltip(String groupName, String propertyName, String tooltip) {
        setPropertyTooltip(propertyName,tooltip);
        addPropertyToGroup(groupName, propertyName);
    }

    /** @return the tooltip for the property */
    protected String getPropertyTooltip(String propertyName) {
        if (propertyTooltipMap == null) {
            return null;
        }
        return propertyTooltipMap.get(propertyName);
    }

    /** Adds a property to a group, creating the group if needed.
     * 
     * @param groupName a named parameter group.
     * @param propertyName the property name.
     */
    protected void addPropertyToGroup(String groupName, String propertyName) {
        if (property2GroupMap == null) {
            property2GroupMap = new HashMap();
        }
        if(group2PropertyListMap==null){
            group2PropertyListMap=new HashMap();
        }
        // add the mapping from property to group
        property2GroupMap.put(propertyName,groupName);

        // create the list of properties in the group
        ArrayList<String> propList;
        if(!group2PropertyListMap.containsKey(groupName)){
            propList=new ArrayList<String>(2);
            group2PropertyListMap.put(groupName,propList);
        }else{
            propList=group2PropertyListMap.get(groupName);
        }
        propList.add(propertyName);
    }

    /** Returns the name of the property group for a property.
     *
     * @param propertyName the property name string.
     * @return the property group name.
     */
    public String getPropertyGroup(String propertyName){
        if(property2GroupMap==null) return null;
        return property2GroupMap.get(propertyName);
    }

    /** Gets the list of property names in a particular group.
     *
     * @param groupName the name of the group.
     * @return the ArrayList of property names in the group.
     */
    protected ArrayList<String> getPropertyGroupList(String groupName) {
        if(group2PropertyListMap==null) return null;
        return group2PropertyListMap.get(groupName);
    }

    /** Returns the set of property groups.
     * 
     * @return Set view of property groups.
     */
    protected Set<String> getPropertyGroupSet(){
        if(group2PropertyListMap==null) return null;
        return group2PropertyListMap.keySet();
    }
    
    /**
     * Returns the mapping from property name to group name.
     * If null, no groups have been declared.
     * 
     * @return the map, or null if no groups have been declared by adding any properties.
     * @see #property2GroupMap
     */
    public HashMap<String,String> getPropertyGroupMap(){
        return property2GroupMap;
    }

    /** Returns true if the filter has property groups.
     *
     */
    public boolean hasPropertyGroups(){
        return property2GroupMap!=null;
    }

    /** Returns the enclosed filter chain
     *@return the chain
     **/
    public FilterChain getEnclosedFilterChain() {
        return enclosedFilterChain;
    }

    /** Sets an enclosed filter chain which should by convention be processed first by the filter (but need not be).
     * Also flags all the filters in the chain as enclosed.
     *@param enclosedFilterChain the chain
     **/
    public void setEnclosedFilterChain(FilterChain enclosedFilterChain) {
        if(this.enclosedFilterChain!=null){
            log.warning("replacing existing enclosedFilterChain= "+this.enclosedFilterChain+" with new enclosedFilterChain= "+enclosedFilterChain);
        }
        this.enclosedFilterChain = enclosedFilterChain;
        if (enclosedFilterChain != null) {
            for (EventFilter f : enclosedFilterChain) {
                f.setEnclosed(true, this);
            }
        }
    }

    /** Returns the Preferences node for this filter.
     * This node is based on the chip class package
     * but may be modified to a sub-node if the filter is
     * enclosed inside another filter.
     * @return the preferences node
     * @see #setEnclosed
     */
    public Preferences getPrefs() {
        return prefs;
    }

    /** Sets the preferences node for this filter
     * @param prefs the node
     */
    public void setPrefs(Preferences prefs) {
        this.prefs = prefs;
    }

    /** Constructs the prefs node for this EventFilter. It is based on the
     * Chip preferences node if the chip exists, otherwise on the EventFilter class package.
     * If the filter is enclosed, then the node includes the package of the enclosing filter class so that
     *enclosed filters take in individualized preferences depending on where they are enclosed.
     */
    private Preferences constructPrefsNode() {
        Preferences prefs = null;
        if (chip == null) {
            prefs = Preferences.userNodeForPackage(getClass()); // base on EventFilter.class package
            log.warning("null chip, basing prefs on EventFilter package");
        } else {
            prefs = chip.getPrefs(); // base on chip class
        }
        // are we being constructed by the initializer of an enclosing filter?
        // if so, we should set up our preferences node so that we use a preferences node
        // that is unique for the enclosing filter
        // if we are being constucted inside another filter's init, then after we march
        // down the stack trace and find ourselves, the next element should be another
        // filter's init

        // Checks if we are being constucted by another filter's initializer. If so, make a new
        // prefs node that is derived from the enclosing filter class name.
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        boolean next = false;
        String enclClassName = null;
        for (StackTraceElement e : trace) {
            if (e.getMethodName().contains("<init>")) {
                if (next) {
                    enclClassName = e.getClassName();
                    break;
                }
                if (e.getClassName().equals(getClass().getName())) {
                    next = true;
                }
            }
        }
//        System.out.println("enclClassName="+enclClassName);
        try {
            if (enclClassName != null) {
                Class enclClass = Class.forName(enclClassName);
                if (EventFilter.class.isAssignableFrom(enclClass)) {
                    prefs = getPrefsForEnclosedFilter(prefs, enclClassName);
//                    log.info("This filter "+this.getClass()+" is enclosed in "+enclClass+" and has new Preferences node="+prefs);
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return prefs;
    }

    /** if the filter is enclosed, it's prefs node is the enclosing node plus the enclosing filter class node */
    private Preferences getPrefsForEnclosedFilter(Preferences prefs, String enclClassName) {
//        int clNaInd=enclClassName.lastIndexOf(".");
//        enclClassName=enclClassName.substring(clNaInd,enclClassName.length());
        prefs = Preferences.userRoot().node(prefs.absolutePath() + "/" + enclClassName.replace(".", "/"));
        return prefs;
    }

    /** @return the enclosing filter if this filter is enclosed */
    public EventFilter getEnclosingFilter() {
        return enclosingFilter;
    }

    /** Sets the enclosing filter for this */
    public void setEnclosingFilter(EventFilter enclosingFilter) {
        this.enclosingFilter = enclosingFilter;
    }

    /** Override this String (can be html formatted) to show it as the filter description in the GUI control FilterPanel. */
    public static String getDescription() {
        return null;
    }

    /** The development status of an EventFilter. An EventFilter can implement the static method getDevelopmentStatus which
     * returns the filter's DevelopmentStatus so that the EventFilter can be tagged or sorted for selection.
     * <ul>
     * <li>Alpha - the filter is experimental.
     * <li>Beta - the filter functions but may have bugs.
     * <li>Released - the filter is in regular use.
     * <li>Unknown - the status is not known.
     * </ul>
     */
    public enum DevelopmentStatus {Alpha,Beta,Released,Unknown};

   /** Override this enum to show the EventFilter's developement status.
    @see DevelopmentStatus
    */
    public static DevelopmentStatus getDevelopmentStatus() {
        return DevelopmentStatus.Unknown;
    }

}
