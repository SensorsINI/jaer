/*
 * FilterChain.java
 *
 * Created on January 30, 2006, 7:58 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.eventprocessing;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.util.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.logging.*;
import java.util.prefs.*;
import javax.crypto.Cipher;

/**
 * A chain of EventFilter that serially filters or processes packets of AEPacket2D. An instance of
 * this object can be passed to FilterFrame and is an instance field of e.g. AERetina. Filters know which chain they are part of and can
 *find out what filters come before and afterwards, allowing them to enable or disable them according to their needs, e.g. NearestEventMotionFilter needs
 *SimpleOrientationFilter to be enabled.
 *
 * @author tobi
 */
public class FilterChain extends LinkedList<EventFilter2D> {
    private boolean measurePerformanceEnabled=false;
    Logger log=Logger.getLogger("FilterChain");
    AEChip chip;
    static Preferences prefs=Preferences.userNodeForPackage(FilterChain.class);
    private boolean filteringEnabled=true;
    
    private boolean timeLimitEnabled=prefs.getBoolean("FilterChain.timeLimitEnabled",false);
    private int timeLimitMs=prefs.getInt("FilterChain.timeLimitMs",10);
    private boolean timedOut=false;
    
    /** Filters can either be processed in the rendering or the data acquisition cycle. Procesing in the rendering cycle is certainly more efficient because
     events are processed in larger packets, but latency is increased to the rendering frame rate delay. Processing in the data acquisition thread has the
     shortest possible latency and if the filter annotates graphics this processing can cause threading problems, e.g. if the annotation modifies the graphics
     buffer while the image is being rendered.
     */
    public enum ProcessingMode {RENDERING, ACQUISITION};
    
    private ProcessingMode processingMode=ProcessingMode.RENDERING;
    
    /** Creates a new instance of FilterChain. Use <@link #contructPreferredFilters> to build the
     stored preferences for filters.
     @param chip the chip that uses this filter chain
     */
    public FilterChain(AEChip chip) {
        this.chip=chip;
        setTimeLimitEnabled(timeLimitEnabled);
        setTimeLimitMs(timeLimitMs);
    }
    
    /** resets all the filters */
    public void reset(){
        for(EventFilter2D f:this){
            f.resetFilter();
        }
    }
    
    /**
     * applies all the filters in the chain to the packet in the order of the enabled filters.
     *     If timeLimitEnabled=true then the timeLimiter is started on the first packet. Any subsequent
     *     input iterator for events will then timeout when the time limit has been reached.
     *
     * @param in the input packet of events
     * @return the resulting output.
     */
    synchronized public EventPacket filterPacket(EventPacket in){
        if(!filteringEnabled) return in;
        EventPacket out;
        if(timeLimitEnabled ){
            if(chip.getAeViewer().isPaused()){
                EventPacket.setTimeLimitEnabled(false);
            }else{
                EventPacket.setTimeLimitEnabled(true);
                EventPacket.restartTimeLimiter(timeLimitMs);
            }
        }
        for(EventFilter2D f:this){
            if(measurePerformanceEnabled && f.isFilterEnabled()){
                if(f.perf==null){
                     f.perf=new EventProcessingPerformanceMeter(f);
                }
                 f.perf.start(in);
            }
            out=f.filterPacket(in);
            if(measurePerformanceEnabled && f.isFilterEnabled() && f.perf!=null){
                f.perf.stop();
                System.out.println(f.perf);
            }
            in=out;
        }
        timedOut=EventPacket.isTimedOut();
        EventPacket.setTimeLimitEnabled(false);
        return in;
    }
    
    /**@param filterClass the class to search for
     * @return the first filter with class filterClass, or null if there is none
     */
    public EventFilter2D findFilter(Class filterClass){
        for(EventFilter2D f:this){
            if(f.getClass()==filterClass) return f;
        }
        return null;
    }
    
    /** adds a filter to the end of the chain. You should rebuildContents on the Frame when finished adding filters.
     @param filter the filter to add
     @return true
     */
    public boolean add(EventFilter2D filter){
//        log.info("adding "+filter+" to "+this);
        boolean ret=super.add(filter);
//        if(chip!=null && chip.getFilterFrame()!=null){
//            chip.getFilterFrame().rebuildContents();
//        }
        return ret;
    }
    
    /** remove the filter
     @return true if successful
     */
    public boolean remove(EventFilter2D filter){
        boolean ret=super.remove(filter);
        return ret;
    }
    
    public boolean isTimeLimitEnabled() {
        return timeLimitEnabled;
    }
    
    /** Enables/disables limit on processing time for packets.
     */
    public void setTimeLimitEnabled(boolean timeLimitEnabled) {
        this.timeLimitEnabled = timeLimitEnabled;
        prefs.putBoolean("FilterChain.timeLimitEnabled",timeLimitEnabled);
        EventPacket.setTimeLimitEnabled(timeLimitEnabled);
    }
    
    public int getTimeLimitMs() {
        return timeLimitMs;
    }
    
    /** Set the time limit in ms for packet processing if time limiting is enabled.
     */
    public void setTimeLimitMs(int timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
        prefs.putInt("FilterChain.timeLimitMs",timeLimitMs);
        EventPacket.setTimeLimitMs(timeLimitMs);
    }
    
    public ProcessingMode getProcessingMode() {
        return processingMode;
    }
    
    /** @see #processingMode
     */
    synchronized public void setProcessingMode(ProcessingMode processingMode) {
        this.processingMode = processingMode;
    }
    
    /** Iterates over all filters and returns true if any filter is enabled.
     @return true if any filter is enabled, false otherwise.
     */
    public boolean isAnyFilterEnabled(){
        boolean any=false;
        for(EventFilter2D f:this){
            if(f.isFilterEnabled()) {
                any=true;
                break;
            }
        }
        return any;
    }
    
    public boolean isMeasurePerformanceEnabled() {
        return measurePerformanceEnabled;
    }
    
    synchronized public void setMeasurePerformanceEnabled(boolean measurePerformanceEnabled) {
        this.measurePerformanceEnabled = measurePerformanceEnabled;
    }
    
    /** disables all filters individually, which will turn off each of them.
     @see #setFilteringEnabled
     */
    private void disableAllFilters(){
        for(EventFilter2D f:this){
            f.setFilterEnabled(false);
        }
    }
    
    /** Globally sets whether filters are applied in this FilterChain.
     @param b true to enable (default) or false to disable all filters
     */
    public void setFilteringEnabled(boolean b) {
        filteringEnabled=b;
    }
    
    public boolean isFilteringEnabled() {
        return filteringEnabled;
    }
    
    static final Class[] filterConstructorParams={AEChip.class}; // params to constructor of an EventFilter2D
    
    
    /** makes a new FilterChain, which constructs the default filters as stored in preferences or as coming from Chip defaultFilterClasses
     */
    synchronized void renewChain() {
//        disableAllFilters();
//        Constructor c;
//        Class[] par={chip.getClass()}; // argument to filter constructor
        FilterChain nfc=new FilterChain(chip); // this call already builds the preferred filters for this chip
        
        
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
        for(EventFilter2D f:nfc){
            add(f);
        }
        nfc=null;
    }
    
    String prefsKey(){
        return "FilterFrame.filters";
    }
    
    /** Constructs the preferred filters for the FilterChain as stored in user Preferences.
     */
    @SuppressWarnings("unchecked")
    synchronized public void contructPreferredFilters() {
        clear();
        ArrayList<String> classNames;
        Preferences prefs=Preferences.userNodeForPackage(chip.getClass()); // get prefs for the Chip, not for the FilterChain class
        try {
            byte[] bytes=prefs.getByteArray(prefsKey(),null);
            if(bytes!=null){
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                classNames = (ArrayList<String>) in.readObject();
                in.close();
            }else{
                classNames=chip.getDefaultEventFilterClassNames();
            }
            Class[] par={chip.getClass()}; // argument to filter constructor
            for(String s:classNames){
                try{
                    Class cl=Class.forName(s);
                    Constructor co=cl.getConstructor(filterConstructorParams);
                    EventFilter2D fi=(EventFilter2D)co.newInstance(chip);
                    add(fi);
                }catch(Exception e){
                    log.warning("couldn't construct filter "+s+" for chip "+chip.getClass().getName()+" : "+e.getCause());
                    if(e.getCause()!=null){
                        Throwable t=e.getCause();
                        t.printStackTrace();
                    }
                }
            }
        }catch(Exception e){
            log.warning(e.getMessage());
        }
    }
    
    synchronized void customize() {
        log.info("customizing filter chain for chip class="+chip.getClass());
        ArrayList<String> currentFilterNames=new ArrayList<String>();
        for(EventFilter2D f:this){
            currentFilterNames.add(f.getClass().getName());
        }
        
        ClassChooserDialog chooser=new ClassChooserDialog(chip.getFilterFrame(),EventFilter2D.class,currentFilterNames, chip.getDefaultEventFilterClassNames());
        chooser.setVisible(true);
        if(chooser.getReturnStatus()==ClassChooserDialog.RET_OK){
            Preferences prefs=Preferences.userNodeForPackage(chip.getClass()); // get prefs for the Chip, not for the FilterChain class
            ArrayList<String> newClassNames=chooser.getList();
            try {
                // Serialize to a byte array
                ByteArrayOutputStream bos = new ByteArrayOutputStream() ;
                ObjectOutput out = new ObjectOutputStream(bos) ;
                out.writeObject(newClassNames);
                out.close();
                byte[] buf = bos.toByteArray();
                prefs.putByteArray(prefsKey(), buf);
                contructPreferredFilters();
                if(chip.getFilterFrame()==null){
                    log.warning(chip+" has no FilterFrame, cannot renew contents");
                }else{
                    chip.getFilterFrame().renewContents();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /** Returns status of timeout of event processing time limit during filter processing.
     @return true if time limit is enabled and timeout occured during processing of last packet,
     false otherwise
     */
    public boolean isTimedOut(){
        return timedOut;
    }
    
}
