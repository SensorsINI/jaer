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
import ch.unizh.ini.caviar.chip.retinaCochlea.Tmpdiff64AndCochleaAERb;
import ch.unizh.ini.caviar.eventprocessing.FilterChain;
import ch.unizh.ini.caviar.eventprocessing.FilterFrame;
import ch.unizh.ini.caviar.eventprocessing.filter.RotateFilter;
import ch.unizh.ini.caviar.eventprocessing.filter.XYTypeFilter;
import ch.unizh.ini.caviar.chip.learning.Learning;
import ch.unizh.ini.caviar.chip.foveated.UioFoveatedImager;
import ch.unizh.ini.caviar.chip.object.Tnc3;
import ch.unizh.ini.caviar.chip.retina.TestchipARCSLineSensor;
import ch.unizh.ini.caviar.chip.retina.TestchipARCsPixelTestArray;
import ch.unizh.ini.caviar.chip.retina.Tmpdiff128;
import ch.unizh.ini.caviar.chip.retina.Tmpdiff64;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventio.*;
import ch.unizh.ini.caviar.graphics.*;
import ch.unizh.ini.caviar.stereopsis.Tmpdiff128StereoPair;
import java.util.prefs.*;

/**
 * Describes a generic address-event chip, and includes fields for associated classes like its renderer, 
 * its rendering paint surface, file input and output event streams, 
 * and the event filters that can operate on its output.
 * 
 * @author tobi
 */
public class AEChip extends Chip2D  {

    static Preferences prefs=Preferences.userNodeForPackage(AEChip.class);    protected EventExtractor2D eventExtractor=null;
    protected AEChipRenderer renderer=null;    protected AEFileInputStream aeInputStream=null;
    protected AEOutputStream aeOutputStream=null;
    protected FilterChain filterChain=null;    protected AEViewer aeViewer=null;
    private boolean subSamplingEnabled=prefs.getBoolean("AEChip.subSamplingEnabled",false);
    private Class<? extends BasicEvent> eventClass=BasicEvent.class;

    /** a static array of chip classes. Used by e.g. AEViewer to know what chips it can display -- they are put in the AEChip menu for user selection.
     TODO - replace this with mechanism for browsing class tree and persistently storing classes to be available
     */
    public static Class[] CHIP_CLASSSES={
        Tmpdiff128.class,
        TestchipARCsPixelTestArray.class,
        Tmpdiff64.class,
        Conv32.class,
        Conv64.class,
        Conv64NoNegativeEvents.class,
        Conv64InOut.class,
        Tnc3.class,
        Learning.class,
        CochleaAERb.class,
        Tmpdiff128StereoPair.class,
        TestchipARCSLineSensor.class,
        UioFoveatedImager.class,
        CochleaAMSNoBiasgen.class,
        CochleaAMSWithBiasgen.class,
        Tmpdiff64AndCochleaAERb.class,
    };

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
 
        filterChain=new FilterChain(this);
        filterChain.add(new XYTypeFilter(this));
        filterChain.add(new RotateFilter(this));
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

    public void setNumCellTypes(int numCellTypes) {
        this.numCellTypes = numCellTypes;
        setChanged();
        notifyObservers("numCellTypes");
    }
    
    @Override public String toString(){
        return getClass().getSimpleName()+" sizeX="+sizeX+" sizeY="+sizeY+" eventClass="+getEventClass().getSimpleName();
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

    public AEOutputStream getAeOutputStream() {
        return aeOutputStream;
    }

    public void setAeOutputStream(AEOutputStream aeOutputStream) {
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
     @see #setSubsampleThresholdEventCount
     */
    public void setSubSamplingEnabled(boolean subSamplingEnabled) {
        this.subSamplingEnabled = subSamplingEnabled;
        if(renderer!=null) renderer.setSubsamplingEnabled(subSamplingEnabled);
        if(eventExtractor!=null) eventExtractor.setSubsamplingEnabled(subSamplingEnabled);
        prefs.putBoolean("AEChip.subSamplingEnabled",subSamplingEnabled);
        setChanged();
        notifyObservers("subsamplingEnabled");
    }

    public FilterChain getFilterChain() {
        return filterChain;
    }

    public void setFilterChain(FilterChain filterChain) {
        this.filterChain = filterChain;
    }

    public Class<? extends BasicEvent> getEventClass() {
        return eventClass;
    }

    public void setEventClass(Class<? extends BasicEvent> eventClass) {
        this.eventClass = eventClass;
    }
    

    
}
