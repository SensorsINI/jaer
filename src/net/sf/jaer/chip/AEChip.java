/*
 * AEChip.java
 *
 * Created on October 5, 2005, 11:33 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.chip;

import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import eu.seebetter.ini.chips.davis.HotPixelFilter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.ProgressMonitor;

import net.sf.jaer.Description;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEFileOutputStream;
import net.sf.jaer.eventio.TextFileInputStream;
import net.sf.jaer.eventio.ros.RosbagFileInputStream;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.FilterFrame;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.filter.Info;
import net.sf.jaer.eventprocessing.filter.RefractoryFilter;
import net.sf.jaer.eventprocessing.filter.RotateFilter;
import net.sf.jaer.eventprocessing.filter.XYTypeFilter;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.SpaceTimeEventDisplayMethod;
import net.sf.jaer.graphics.SpaceTimeRollingEventDisplayMethod;
import net.sf.jaer.util.avioutput.JaerAviWriter;
import org.apache.commons.io.FilenameUtils;

/**
 * Describes a generic address-event chip, and includes fields for associated
 * classes like its renderer, its rendering paint surface, file input and output
 * event streams, and the event filters that can operate on its output. A
 * subclass can add it's own default EventFilters so that users need not
 * customize the FilterChain.\
 * <p>
 * The {@link #onRegistration()} and {@link #onDeregistration() } allows
 * arbitrary actions after the chip is constructed and registered in the
 * AEViewer.
 *
 * @author tobi
 */
@Description("Address-Event Chip")
public class AEChip extends Chip2D {

    /**
     * The current event extractor.
     */
    protected EventExtractor2D eventExtractor = null;
    protected AEChipRenderer renderer = null;
    protected AEFileInputStreamInterface aeInputStream = null;
    protected AEFileOutputStream aeOutputStream = null;
    protected FilterChain filterChain = null;
    protected AEViewer aeViewer = null;
    private boolean subSamplingEnabled = getPrefs().getBoolean("AEChip.subSamplingEnabled", false);
    private Class<? extends BasicEvent> eventClass = BasicEvent.class;
    /** Full scale value for rendering events; chips can override to provide finer or coarser scale */
    private int fullScaleForEventAccumulationRendering=32;
    
    /**
     * List of default EventFilter2D filters
     */
    protected ArrayList<Class> defaultEventFilters = new ArrayList<Class>();
    /**
     * The number of bits on an AE bus used for the raw device address.
     * rawAddressNumBits/16 should set the number of bytes used to read and log
     * captured data. E.g. 16 bits reads and writes <code>short</code>, and 32
     * bits reads and writes <code>int</code>. At present all chips write and
     * read the same address data width, int (32 bits) as of data file format
     * 2.0. Old data files will still be read correctly.
     */
    private int rawAddressNumBits = 16;

    /**
     * @return list of default filter classes
     * @return list of Class default filter classes for this AEChip
     */
    public ArrayList<Class> getDefaultEventFilterClasses() {
        return defaultEventFilters;
    }

    /**
     * return list of default filter class names (strings)
     *
     * @return list of String fully qualified class names
     */
    public ArrayList<String> getDefaultEventFilterClassNames() {
        ArrayList<String> list = new ArrayList<String>();
        for (Class c : defaultEventFilters) {
            list.add(c.getName());
        }
        return list;
    }

    /**
     * Adds a filter that is available by default
     *
     * @param f the event filter to add
     */
    public void addDefaultEventFilter(Class<? extends EventFilter> f) {
        if (!EventFilter.class.isAssignableFrom(f)) {
            log.warning("In trying to addDefaultEventFilter, " + f + " is not an EventFilter, ignoring");
            return;
        }
        defaultEventFilters.add(f);
    }

    /**
     * Removes a filter that is available by default, for example to reorder
     * list to put a desired filter at the end of the default filter chain
     *
     * @param f the EventFilter to remove
     */
    public void removeDefaultEventFilter(Class<? extends EventFilter> f) {
        if (defaultEventFilters == null) {
            log.warning("null defaultEventFilters, doing nothing");
            return;
        }
        if (defaultEventFilters.remove(f)) {
            log.info("removed " + f + " from defaultEventFilters");
        }
    }

    /**
     * Creates a new instance of AEChip
     */
    public AEChip() {
//        setName("unnamed AEChip");
        setRenderer(new AEChipRenderer(this));

        // add canvas before filters so that filters have a canvas to add annotator to
        setCanvas(new ChipCanvas(this)); // note that we need to do this again even though Chip2D did it, because the AEChipRenderer here shadows the Chip2D renderer and the renderer will be returned null, preventing installation of mouse listeners
        // instancing there display methods does NOT add them to the menu automatically

        getCanvas().addDisplayMethod(new ChipRendererDisplayMethod(getCanvas()));
        getCanvas().addDisplayMethod(new SpaceTimeEventDisplayMethod(getCanvas()));
        getCanvas().addDisplayMethod(new SpaceTimeRollingEventDisplayMethod(getCanvas()));
//        getCanvas().addDisplayMethod(new Histogram3dDisplayMethod(getCanvas())); // preesntly broken - tobi

        //set default display method
        DisplayMethod m = getPreferredDisplayMethod();
        m.setChipCanvas(getCanvas());
        getCanvas().setDisplayMethod(m);

        // add default filters
        addDefaultEventFilter(XYTypeFilter.class);
        addDefaultEventFilter(RotateFilter.class);
//        addDefaultEventFilter(RepetitiousFilter.class);
        addDefaultEventFilter(BackgroundActivityFilter.class);
//        addDefaultEventFilter(SubSampler.class);
        addDefaultEventFilter(RefractoryFilter.class);
        addDefaultEventFilter(HotPixelFilter.class);
        addDefaultEventFilter(Info.class);
        addDefaultEventFilter(JaerAviWriter.class);

        filterChain = new FilterChain(this);
        filterChain.contructPreferredFilters();
    }

    /**
     * Closes the RemoteControl if there is one.
     */
    @Override
    public void cleanup() {
        super.cleanup();
        if (getRemoteControl() != null) {
            getRemoteControl().close();
        }
        if(getFilterChain()!=null){
            for(EventFilter f:getFilterChain()){
                try{
                    f.cleanup();
                }catch(Exception e){
                    log.warning(String.format("cleanup %s: caught %s",f,e.toString()));
                }
            }
        }
    }

    /**
     * Gets the current extractor of the chip.
     *
     * @return the extractor
     */
    public EventExtractor2D getEventExtractor() {
        return eventExtractor;
    }

    /**
     * Sets the EventExtractor2D and notifies Observers with the new extractor.
     * The previousEventExtractor is set to the prior value unless the prior
     * value is null; then it is set to the supplied eventExtractor. This way,
     * the previousEventExtractor is never set to a null.
     *
     * @param eventExtractor the extractor; notifies Observers.
     * @see #previousEventExtractor
     */
    public void setEventExtractor(EventExtractor2D eventExtractor) {
        this.eventExtractor = eventExtractor;
        setChanged();
        notifyObservers(eventExtractor);
    }

    public int getNumCellTypes() {
        return numCellTypes;
    }

    /**
     * Sets the number of cell types from each x,y location that this AEChip
     * has. Observers are called with the string "numCellTypes".
     *
     * @param numCellTypes the number of types, e.g. 2 for the temporal contrast
     * retina with on/off types
     */
    public void setNumCellTypes(int numCellTypes) {
        int oldsize = this.sizeX * this.sizeY * this.numCellTypes;
        this.numCellTypes = numCellTypes;
        setChanged();
        notifyObservers(EVENT_NUM_CELL_TYPES);
        int newsize = sizeX * sizeY * numCellTypes;
        if (newsize > 0) {
            getSupport().firePropertyChange(EVENT_SIZE_SET, oldsize, newsize);
        }
    }

    @Override
    public String toString() {
        if (getClass() == null) {
            return null;
        }
        Class eventClass = getEventClass();
        String eventClassString = eventClass != null ? eventClass.getSimpleName() : null;
        return getClass().getSimpleName() + " sizeX=" + sizeX + " sizeY=" + sizeY + " eventClass=" + eventClassString;
    }

    /**
     * Returns the renderer. Note that this field shadows the Chip2D renderer.
     *
     * @return
     */
    @Override
    public AEChipRenderer getRenderer() {
        return renderer;
    }

    /**
     * sets the class that renders the event histograms and notifies Observers
     * with the new Renderer.
     *
     * @param renderer the AEChipRenderer. Note this field shadows the Chip2D
     * renderer.
     */
    public void setRenderer(AEChipRenderer renderer) {
        this.renderer = renderer;
        setChanged();
        notifyObservers(renderer);
    }

    /**
     * The AEFileInputStream currently being fed to the AEChip. Note this field
     * must be set by someone.
     *
     * @return the stream
     */
    public AEFileInputStreamInterface getAeInputStream() {
        return aeInputStream;
    }

    /**
     * Sets the file input stream and notifies Observers with the new
     * AEFileInputStream.
     *
     * @param aeInputStream
     */
    public void setAeInputStream(AEFileInputStreamInterface aeInputStream) {
        this.aeInputStream = aeInputStream;
        setChanged();
        notifyObservers(aeInputStream);
    }

    public AEFileOutputStream getAeOutputStream() {
        return aeOutputStream;
    }

    /**
     * Sets the file output stream and notifies Observers with the new
     * AEFileOutputStream.
     *
     * @param aeOutputStream
     */
    public void setAeOutputStream(AEFileOutputStream aeOutputStream) {
        this.aeOutputStream = aeOutputStream;
        setChanged();
        notifyObservers(aeOutputStream);
    }

    public AEViewer getAeViewer() {
        return aeViewer;
    }

    /**
     * Sets the AEViewer that will display this chip. Notifies Observers of this
     * chip with the aeViewer instance. Subclasses can override this method to
     * do things such as adding menu items to AEViewer.
     *
     * @param aeViewer the viewer
     */
    public void setAeViewer(AEViewer aeViewer) {
        this.aeViewer = aeViewer;
        setChanged();
        notifyObservers(aeViewer);
    }

    public FilterFrame getFilterFrame() {
        return filterFrame;
    }

    /**
     * Sets the FilterFrame and notifies Observers with the new FilterFrame.
     *
     * @param filterFrame
     */
    public void setFilterFrame(FilterFrame filterFrame) {
        this.filterFrame = filterFrame;
        setChanged();
        notifyObservers(filterFrame);
    }

    public boolean isSubSamplingEnabled() {
        return subSamplingEnabled;
    }

    /**
     * Enables subsampling of the events in event extraction, rendering, etc.
     * Observers are notified with the string "subSamplingEnabled".
     *
     * @param subSamplingEnabled true to enable sub sampling
     */
    public void setSubSamplingEnabled(boolean subSamplingEnabled) {
        this.subSamplingEnabled = subSamplingEnabled;
        if (renderer != null) {
            renderer.setSubsamplingEnabled(subSamplingEnabled);
        }
        if (eventExtractor != null) {
            eventExtractor.setSubsamplingEnabled(subSamplingEnabled);
        }
        getPrefs().putBoolean("AEChip.subSamplingEnabled", subSamplingEnabled);
        setChanged();
        notifyObservers("subsamplingEnabled");
    }

    /**
     * This chain of filters for this AEChip
     *
     * @return the chain
     */
    public FilterChain getFilterChain() {
        return filterChain;
    }

    public void setFilterChain(FilterChain filterChain) {
        this.filterChain = filterChain;
    }

    /**
     * A chip has this intrinsic class of output events.
     *
     * @return Class of event type that extends BasicEvent
     */
    public Class<? extends BasicEvent> getEventClass() {
        return eventClass;
    }

    /**
     * The AEChip produces this type of event.
     *
     * @param eventClass the class of event, extending BasicEvent
     */
    public void setEventClass(Class<? extends BasicEvent> eventClass) {
        this.eventClass = eventClass;
        setChanged();
        notifyObservers("eventClass");
    }

    /**
     * The number of bits on an AE bus used for the raw device address.
     * rawAddressNumBits/16 should set the number of bytes used to read and log
     * captured data. E.g. 16 bits reads and writes <code>short</code>, and 32
     * bits reads and writes <code>int</code>. At present all chips write and
     * read the same address data width, int (32 bits) as of data file format
     * 2.0. Old data files will still be read correctly.
     */
    public int getRawAddressNumBits() {
        return rawAddressNumBits;
    }

    /**
     * The number of bits on an AE bus used for the raw device address.
     * rawAddressNumBits/16 should set the number of bytes used to read and log
     * captured data. E.g. 16 bits reads and writes <code>short</code>, and 32
     * bits reads and writes <code>int</code>. At present all chips write and
     * read the same address data width, int (32 bits) as of data file format
     * 2.0. Old data files will still be read correctly.
     */
    public void setRawAddressNumBits(int rawAddressNumBits) {
        this.rawAddressNumBits = rawAddressNumBits;
    }

    /**
     * This method (empty by default) called on registration of AEChip in
     * AEViewer at end of setAeChipClass, after other initialization routines.
     * Can be used for instance to register new help menu items or chip
     * controls.
     *
     */
    public void onRegistration() {
        log.info("registering " + this);
    }

    /**
     * This method (empty by default) called on de-registration of AEChip in
     * AEViewer, just before making a the new AEChip.
     *
     */
    public void onDeregistration() {
        log.info("unregistering " + this);
    }

    /**
     * Constructs a new AEFileInputStream or RosbagFileInputStream given a File.
     * By default this just constructs a new AEFileInputStream, but it can be
     * overridden by subclasses of AEChip to construct their own specialized
     * readers that are implement the same interface.
     *
     * @param file the file to open.
     * @param progressMonitor pass this in to monitor progress of long-running
     * file opening, to allow canceling it
     * @return the stream
     * @throws IOException on any IO exception
     * @throws java.lang.InterruptedException if the operation is interrupted by user using the ProgressMonitor cancel button
     */
    public AEFileInputStreamInterface constuctFileInputStream(File file, ProgressMonitor progressMonitor) throws IOException, InterruptedException {
        // usually called from EDT.. makes it tricky to update any progress GUI
        if (FilenameUtils.isExtension(file.getName(),TextFileInputStream.FILE_EXTENSION_TXT) 
                ||FilenameUtils.isExtension(file.getName(),TextFileInputStream.FILE_EXTENSION_CSV) ) {
            // for text file, since counting events is slow, show progress dialog and let user cancel it
            try {
                log.info(String.format("Opening file %s as a text CSV file",file));
                aeInputStream = new TextFileInputStream(file, this, progressMonitor);
            } catch (IOException ex) {
                log.warning(ex.toString());
                throw new IOException("Could not open " + file + ": got " + ex.toString(), ex);
            }
        } else if (FilenameUtils.isExtension(file.getName(), RosbagFileInputStream.DATA_FILE_EXTENSION)) {
            // for rosbag, since creating index is slow, show progress dialog and let user cancel it
            try {
                aeInputStream = new RosbagFileInputStream(file, this, progressMonitor);
            } catch (BagReaderException ex) {
                log.warning(ex.toString());
                throw new IOException("Could not open " + file + ": got " + ex.toString(), ex);
            }
        } else if (FilenameUtils.isExtension(file.getName(), AEDataFile.DATA_FILE_EXTENSION.substring(1))
                || FilenameUtils.isExtension(file.getName(), AEDataFile.DATA_FILE_EXTENSION_AEDAT2.substring(1))
                || FilenameUtils.isExtension(file.getName(), AEDataFile.OLD_DATA_FILE_EXTENSION.substring(1))
                ) {
            aeInputStream = new AEFileInputStream(file, this);
        }else{
            throw new FileNotFoundException("file "+file+" file type is not known; .dat, .aedat, .aedat2, or .bag files are currently supported");
        }
        return aeInputStream;
    }

    /**
     * This method writes additional header lines to a newly created
     * AEFileOutputStream that logs data from this AEChip. The default
     * implementation writes the AEChip class name and the particular AEChip hardware settings.
     *
     * @param os the AEFileOutputStream that is being written to
     * @see AEFileOutputStream#writeHeaderLine(java.lang.String)
     * @throws IOException
     */
    public void writeAdditionalAEFileOutputStreamHeader(AEFileOutputStream os) throws IOException, BackingStoreException {
        log.info("writing preferences for " + this.toString());
        long start=System.currentTimeMillis();
        os.writeHeaderLine(" AEChip: " + this.getClass().getName());
        os.writeHeaderLine("Start of Preferences for this AEChip (search for \"End of Preferences\" to find end of this block)"); // write header to AE data file for prefs
        // write only the hardware preferences for this particular device, which is mixed with all other devices in same package in Java Preferences.
        /// therefore just filter and save the keya
        String header=prefsHeader()+".";
        final String[] keys=getPrefs().keys();
        final Preferences p=getPrefs();
        for(String k:keys){
            if(k.startsWith(header)){
                os.writeHeaderLine(String.format("<entry key=\"%s\" value=\"%s\"/>",k,p.get(k, null)));
            }
        }
        // code below was extremely slow, e.g. 2.5 seconds to write the header
//        ByteArrayOutputStream bos = new ByteArrayOutputStream(500000);  // bos to hold preferences XML as byte array, tobi sized prefs as 186kB of text and about 2200 lines for set of preferences at INI
//        getPrefs().exportNode(bos);
//        log.info("done exporting to byte array stream after " + (System.currentTimeMillis()-start)+ " ms");
////        bos.flush(); // should not need to flush
//
//        // make a reader to read the prefs text line by line
//        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bos.toByteArray())));
//        ByteArrayOutputStream bos2 = new ByteArrayOutputStream(500000); // make a byte stream to hold lines prepended by comment char
//        DataOutputStream dos = new DataOutputStream(bos2); // wrap in DOS to write comment chars and line endings
//
//        String line = null;
//        while ((line = reader.readLine()) != null) { // get a line of prefs from prefs in memory
//            dos.writeByte(AEDataFile.COMMENT_CHAR); // '#' // prepend comment
//            dos.writeBytes(line);
//            dos.writeByte(AEDataFile.EOL[0]); // '\r' 
//            dos.writeByte(AEDataFile.EOL[1]); // '\n'
//        }
//        os.write(bos2.toByteArray()); // write out entire reformatted prefs header
        os.writeHeaderLine("End of Preferences for this AEChip"); // write end of prefs header
//        os.flush();  // shouldn't need to flush here
        log.info("done writing preferences to " + os + " after " + (System.currentTimeMillis()-start)+ " ms");

    }

    /**
     * Default implementation of a method to translate the bit locations of data
     * from jAER3.0 source like cAER to jAER internal address format, as
     * specified in http://inilabs.com/support/software/fileformat/ .
     *
     * @param address the address from cAER 3.0 format sources.
     * @return the transformed address. The default implementation returns the
     * same address.
     */
    public int translateJaer3AddressToJaerAddress(int address) {
        return address;
    }

    /**
     * @return the fullScaleForEventAccumulationRendering
     */
    public int getFullScaleForEventAccumulationRendering() {
        return fullScaleForEventAccumulationRendering;
    }

    /**
     * @param fullScaleForEventAccumulationRendering the fullScaleForEventAccumulationRendering to set
     */
    public void setFullScaleForEventAccumulationRendering(int fullScaleForEventAccumulationRendering) {
        this.fullScaleForEventAccumulationRendering = fullScaleForEventAccumulationRendering;
    }
}
