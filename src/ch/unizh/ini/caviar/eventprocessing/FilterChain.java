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
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.logging.*;

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
    private boolean limitTimeEnabled=false;
    
    /** Filters can either be processed in the rendering or the data acquisition cycle. Procesing in the rendering cycle is certainly more efficient because
     events are processed in larger packets, but latency is increased to the rendering frame rate delay. Processing in the data acquisition thread has the
     shortest possible latency and if the filter annotates graphics this processing can cause threading problems, e.g. if the annotation modifies the graphics
     buffer while the image is being rendered.
     */
    public enum ProcessingMode {RENDERING, ACQUISITION};
    
    private ProcessingMode processingMode=ProcessingMode.RENDERING;
    
    /** Creates a new instance of FilterChain
     @param chip the chip that uses this filter chain
     */
    public FilterChain(AEChip chip) {
        this.chip=chip;
    }
    
    /** resets all the filters */
    public void reset(){
        for(EventFilter2D f:this){
            f.resetFilter();
        }
    }
    
//    /** applies all the filters in the chain to the packet
//     *@param in the input packet of events
//     *@return the resulting output. Depending on the state of the filterInPlaceEnabled flags in the filters, the input packet may be modified. Not all filters actually implement in-place filtering.
//     **/
//    public AEPacket2D filter(AEPacket2D in){
//        AEPacket2D out=in;
//        for(EventFilter2D f:this){
//            out=f.filter(out);
//        }
//        return out;
//    }
    
    /** applies all the filters in the chain to the packet
     *@param in the input packet of events
     *@return the resulting output.
     **/
    synchronized public EventPacket filterPacket(EventPacket in){
        EventPacket out;
        for(EventFilter2D f:this){
            if(measurePerformanceEnabled && f.isFilterEnabled()){
                f.perf.start(in);
            }
            out=f.filterPacket(in);
            if(measurePerformanceEnabled && f.isFilterEnabled()){
                f.perf.stop();
                System.out.println(f.perf);
            }
            in=out;
        }
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
    
//    void rebuildFilters() {
//        for(EventFilter2D f:this){
//        }
//    }
    
    /** disables all filters */
    private void disableAllFilters(){
        for(EventFilter2D f:this){
            f.setFilterEnabled(false);
        }
    }
    
    void renewChain() {
        disableAllFilters();
        Constructor c;
        Class[] par={AEChip.class};
        FilterChain nfc=new FilterChain(chip);
        
        for(EventFilter2D f:this){
            try{
                c=f.getClass().getConstructor(par);
                EventFilter2D nf=(EventFilter2D)(c.newInstance(chip));
                nfc.add(nf);
            }catch(Exception e){
                log.warning("couldn't renew filter "+f+", didn't have constructor taking AEChip parameter");
            }
        }
        clear();
        for(EventFilter2D f:nfc){
            add(f);
        }
    }

    public boolean isLimitTimeEnabled() {
        return limitTimeEnabled;
    }

    public void setLimitTimeEnabled(boolean limitTimeEnabled) {
        this.limitTimeEnabled = limitTimeEnabled;
    }
    
    
}
