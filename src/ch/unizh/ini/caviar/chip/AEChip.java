/*
 * AEChip.java
 *
 * Created on October 5, 2005, 11:33 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.chip;

import ch.unizh.ini.caviar.chip.cochlea.CochleaAERb;
import ch.unizh.ini.caviar.chip.cochlea.CochleaAMSNoBiasgen;
import ch.unizh.ini.caviar.chip.cochlea.CochleaAMSWithBiasgen;
import ch.unizh.ini.caviar.chip.convolution.*;
import ch.unizh.ini.caviar.chip.convolution.Conv32;
import ch.unizh.ini.caviar.chip.convolution.Conv64;
import ch.unizh.ini.caviar.chip.convolution.Conv64InOut;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.eventprocessing.FilterChain;
import ch.unizh.ini.caviar.eventprocessing.FilterFrame;
import ch.unizh.ini.caviar.eventprocessing.filter.BackgroundActivityFilter;
import ch.unizh.ini.caviar.eventprocessing.filter.RepetitiousFilter;
import ch.unizh.ini.caviar.eventprocessing.filter.RotateFilter;
import ch.unizh.ini.caviar.eventprocessing.filter.SubSampler;
import ch.unizh.ini.caviar.eventprocessing.filter.XYTypeFilter;
import ch.unizh.ini.caviar.chip.learning.Learning;
import ch.unizh.ini.caviar.chip.foveated.UioFoveatedImager;
import ch.unizh.ini.caviar.chip.staticbiovis.UioStaticBioVis;
import ch.unizh.ini.caviar.chip.object.Tnc3;
import ch.unizh.ini.caviar.chip.retina.TestchipARCSLineSensor;
import ch.unizh.ini.caviar.chip.retina.TestchipARCsPixelTestArray;
import ch.unizh.ini.caviar.chip.retina.Tmpdiff128;
import ch.unizh.ini.caviar.chip.retina.Tmpdiff64;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventio.*;
import ch.unizh.ini.caviar.graphics.*;
import ch.unizh.ini.caviar.stereopsis.Tmpdiff128StereoPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.*;

/**
 * Describes a generic address-event chip, and includes fields for associated classes like its renderer,
 * its rendering paint surface, file input and output event streams,
 * and the event filters that can operate on its output. A subclass can add it's own default EventFilters so that users
 need not customize the FilterChain.
 *
 * @author tobi
 */
public class AEChip extends Chip2D  {
    
    protected EventExtractor2D eventExtractor=null;
    protected AEChipRenderer renderer=null;    protected AEFileInputStream aeInputStream=null;
    protected AEFileOutputStream aeOutputStream=null;
    protected FilterChain filterChain=null;    protected AEViewer aeViewer=null;
    private boolean subSamplingEnabled=getPrefs().getBoolean("AEChip.subSamplingEnabled",false);
    private Class<? extends BasicEvent> eventClass=BasicEvent.class;
    /** List of default EventFilter2D filters */
    protected ArrayList<Class> defaultEventFilters=new ArrayList<Class>();
    
    /** The number of bits on an AE bus used for the raw device address. 
     rawAddressNumBits/16 should set the number of bytes used to read and log captured data.
     E.g. 16 bits reads and writes <code>short</code>, and 32 bits reads and writes <code>int</code>.
     At present
     all chips write and read the same address data width, int (32 bits) 
     as of data file format 2.0. Old data files
     will still be read correctly.*/
    private int rawAddressNumBits=16;
    
    /** @return list of default filter classes
     @return list of Class default filter classes for this AEChip
     */
    public ArrayList<Class> getDefaultEventFilterClasses() {
        return defaultEventFilters;
    }
    /** return list of default filter class names (strings) 
     @return list of String fully qualified class names
     */
    public ArrayList<String> getDefaultEventFilterClassNames(){
        ArrayList<String> list=new ArrayList<String>();
        for(Class c:defaultEventFilters){
            list.add(c.getName());
        }
        return list;
    }
    
    /** add a filter that is available by default */
   public void addDefaultEventFilter(Class f){
       if(!EventFilter.class.isAssignableFrom(f)){
           log.warning(f+" is not an EventFilter");
       }
        defaultEventFilters.add(f);
    }
    
    /** Creates a new instance of AEChip */
    public AEChip() {
//        setName("unnamed AEChip");
        // add canvas before filters so that filters have a canvas to add annotator to
        setRenderer(new AEChipRenderer(this));
        setCanvas(new ChipCanvas(this));
        // instancing there display methods does NOT add them to the menu automatically
        
        DisplayMethod defaultMethod;
        getCanvas().addDisplayMethod(defaultMethod=new ChipRendererDisplayMethod(getCanvas()));
        getCanvas().addDisplayMethod(new SpaceTimeEventDisplayMethod(getCanvas()));
//        getCanvas().addDisplayMethod(new Histogram3dDisplayMethod(getCanvas())); // preesntly broken - tobi
        
        //set default method
        getCanvas().setDisplayMethod(defaultMethod);
        addDefaultEventFilter(XYTypeFilter.class);
        addDefaultEventFilter(RotateFilter.class);
//        addDefaultEventFilter(RepetitiousFilter.class);
        addDefaultEventFilter(BackgroundActivityFilter.class);
//        addDefaultEventFilter(SubSampler.class);
//        addDefaultEventFilter(ServoArm.class);
        //addDefaultEventFilter(Goalie.class);
        
        filterChain=new FilterChain(this);
        filterChain.contructPreferredFilters();
    }
    
    public EventExtractor2D getEventExtractor() {
        return eventExtractor;
    }
    
    public void setEventExtractor(EventExtractor2D eventExtractor) {
        this.eventExtractor = eventExtractor;
        setChanged();
        notifyObservers(eventExtractor);
    }
    
    public int getNumCellTypes() {
        return numCellTypes;
    }
    
    /** Sets the number of cell types from each x,y location that this AEChip has
     @param numCellTypes the number of types, e.g. 2 for the temporal contrast retina with on/off types
     */
    public void setNumCellTypes(int numCellTypes) {
        this.numCellTypes = numCellTypes;
        setChanged();
        notifyObservers("numCellTypes");
    }
    
    @Override public String toString(){
        if(getClass()==null) return null;
        Class eventClass=getEventClass();
        String eventClassString=eventClass!=null? eventClass.getSimpleName(): null;
        return getClass().getSimpleName()+" sizeX="+sizeX+" sizeY="+sizeY+" eventClass="+eventClassString;
    }
    
    public AEChipRenderer getRenderer() {
        return renderer;
    }
    
    /** sets the class that renders the event histograms and notifies Observers */
    public void setRenderer(AEChipRenderer renderer) {
        this.renderer = renderer;
        setChanged();
        notifyObservers(renderer);
    }
    
    public AEFileInputStream getAeInputStream() {
        return aeInputStream;
    }
    
    public void setAeInputStream(AEFileInputStream aeInputStream) {
        this.aeInputStream = aeInputStream;
        setChanged();
        notifyObservers(aeInputStream);
    }
    
    public AEFileOutputStream getAeOutputStream() {
        return aeOutputStream;
    }
    
    public void setAeOutputStream(AEFileOutputStream aeOutputStream) {
        this.aeOutputStream = aeOutputStream;
        setChanged();
        notifyObservers(aeOutputStream);
    }
    
    public AEViewer getAeViewer() {
        return aeViewer;
    }
    
    /** Sets the AEViewer that will display this chip. Notifies Observers of this chip with the aeViewer instance.
     @param aeViewer the viewer
     */
    public void setAeViewer(AEViewer aeViewer) {
        this.aeViewer = aeViewer;
        setChanged();
        notifyObservers(aeViewer);
    }
    
    public FilterFrame getFilterFrame() {
        return filterFrame;
    }
    
    public void setFilterFrame(FilterFrame filterFrame) {
        this.filterFrame = filterFrame;
        setChanged();
        notifyObservers(filterFrame);
    }
    
    public boolean isSubSamplingEnabled() {
        return subSamplingEnabled;
    }
    
    /** Enables subsampling of the events in event extraction, rendering, etc.
     @param subSamplingEnabled true to enable sub sampling
     */
    public void setSubSamplingEnabled(boolean subSamplingEnabled) {
        this.subSamplingEnabled = subSamplingEnabled;
        if(renderer!=null) renderer.setSubsamplingEnabled(subSamplingEnabled);
        if(eventExtractor!=null) eventExtractor.setSubsamplingEnabled(subSamplingEnabled);
        getPrefs().putBoolean("AEChip.subSamplingEnabled",subSamplingEnabled);
        setChanged();
        notifyObservers("subsamplingEnabled");
    }
    
    /** This chain of filters for this AEChip 
     @return the chain
     */
    public FilterChain getFilterChain() {
        return filterChain;
    }
    
    public void setFilterChain(FilterChain filterChain) {
        this.filterChain = filterChain;
    }
    
    /** A chip has this intrinsic class of output events. 
     @return Class of event type that extends BasicEvent
     */
    public Class<? extends BasicEvent> getEventClass() {
        return eventClass;
    }
    
    /** The AEChip produces this type of event.
     @param eventClass the class of event, extending BasicEvent
     */
    public void setEventClass(Class<? extends BasicEvent> eventClass) {
        this.eventClass = eventClass;
    }

    /** The number of bits on an AE bus used for the raw device address. 
     rawAddressNumBits/16 should set the number of bytes used to read and log captured data.
     E.g. 16 bits reads and writes <code>short</code>, and 32 bits reads and writes <code>int</code>.
     At present
     all chips write and read the same address data width, int (32 bits) 
     as of data file format 2.0. Old data files
     will still be read correctly.*/
    public int getRawAddressNumBits() {
        return rawAddressNumBits;
    }

    /** The number of bits on an AE bus used for the raw device address. 
     rawAddressNumBits/16 should set the number of bytes used to read and log captured data.
     E.g. 16 bits reads and writes <code>short</code>, and 32 bits reads and writes <code>int</code>.
     At present
     all chips write and read the same address data width, int (32 bits) 
     as of data file format 2.0. Old data files
     will still be read correctly.*/
    public void setRawAddressNumBits(int rawAddressNumBits) {
        this.rawAddressNumBits = rawAddressNumBits;
    }

    
    
}
