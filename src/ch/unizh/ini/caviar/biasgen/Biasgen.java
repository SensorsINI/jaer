/*
 * Biasgen.java
 *
 * Created on September 23, 2005, 8:52 PM
 */

package ch.unizh.ini.caviar.biasgen;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.hardwareinterface.*;
import java.beans.*;
import java.io.Serializable;
import java.util.*;
import java.util.logging.*;
import java.util.prefs.*;

/**
 * Describes a complete bias
 *generator, with a masterbias. a bunch of IPots, and a hardware interface.
 * This class handles the USB commands for the masterbias and ipots.
 *<p>
 *When using this class in conjunction with another use of the HardwareInterface, it is much better to share the same
 *interface if possible. Do this by constructing the Biasgen using an existing HardwareInterface.
 *
 * @author tobi
 */
public class Biasgen implements BiasgenPreferences, PropertyChangeListener, Observer, BiasgenHardwareInterface, Serializable  {
    transient public IPotArray iPotArray=null;
    transient private Masterbias masterbias=null;
    private String name=null;
    transient private BiasgenHardwareInterface hardwareInterface=null;
    private boolean batchEditOccurring=false;
    private Chip chip;
    
   private static Preferences prefs=Preferences.userNodeForPackage(ch.unizh.ini.caviar.biasgen.Biasgen.class); // preferences for bias values are in this node of Preferences object
    private static Logger log=Logger.getLogger("Biasgen");
    
    
    private ArrayList<IPotGroup> iPotGroups=new ArrayList<IPotGroup>(); // groups of pots
    
    /**
     *  Constructs a new biasgen. A BiasgenHardwareInterface is constructed when needed. 
     *This biasgen adds itself as a PropertyChangeListener to the IPotArray.
     *It also adds itself as an Observer for the Masterbias.
     *@see HardwareInterfaceException
     */
    public Biasgen(Chip chip){
        this.chip=chip;
        setHardwareInterface((BiasgenHardwareInterface)chip.getHardwareInterface());
        masterbias=new Masterbias(this);
        iPotArray=new IPotArray(this);
        masterbias.addObserver(this);
        iPotArray.getSupport().addPropertyChangeListener(this);
        loadPreferences();
    }
    
//    /**
//     *  Constructs a new biasgen.
//     *@param hardwareInterface the hardware interface to use to connect to this bias generator
//     */
//    public Biasgen(BiasgenHardwareInterface hardwareInterface){
//        this();
//        this.hardwareInterface=hardwareInterface;
//    }
    
    public IPotArray getPotArray() {
        return this.iPotArray;
    }
    
    public void setIPotArray(final IPotArray iPotArray) {
        this.iPotArray = iPotArray;
    }
    
    public Masterbias getMasterbias() {
        return this.masterbias;
    }
    
    public void setMasterbias(final Masterbias masterbias) {
        this.masterbias = masterbias;
    }
    
    public String getName() {
        return this.name;
    }
    
    public void setName(final String name) {
        this.name = name;
    }
    
    /** exports preference values for this subtree of all Preferences (the biasgen package subtreee). 
     * Biases and other settings (e.g. master bias resistor) are written to the output stream as an XML file
     *@param os an output stream, typically constructed for a FileOutputStream
     *@throws IOException if the output stream cannot be written
     */
    public void exportPreferences(java.io.OutputStream os) throws java.io.IOException {
        try{
            prefs.exportSubtree(os);
            prefs.flush();
        }catch(BackingStoreException bse){
            bse.printStackTrace();
        }
        
    }
    
    /** imports preference values for this subtree of all Preferences (the biasgen package subtreee). 
     * Biases and other settings (e.g. master bias resistor) are read in from an XML file. Bias values are sent as a batch to the device after values
     *are imported.
     *@param is an input stream, typically constructed for a FileInputStream
     *@throws IOException if the output stream cannot be read
     */
    public void importPreferences(java.io.InputStream is) throws java.io.IOException, InvalidPreferencesFormatException, HardwareInterfaceException {
        log.info("Biasgen.importPreferences");
        startBatchEdit();
        prefs.importPreferences(is);  // this uses the Preferences object to load all preferences from the input stream which an xml file
        endBatchEdit();
    }
    
    public void loadPreferences() {
//        log.info("Biasgen.loadPreferences()");
        startBatchEdit();
        iPotArray.loadPreferences();
        masterbias.loadPreferences();
        try{
            endBatchEdit();
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }
    }
    
    public void storePreferences() {
        iPotArray.storePreferences();
        masterbias.storePreferences();
    }
    
    
    public String toString(){
        String s="Biasgen with ";
        s=s+iPotArray.toString();
        return s;
    }
    
    /** call this when starting a set of related pot value changes.
     *@see #endBatchEdit
     */
    public void startBatchEdit(){
        setBatchEditOccurring(true);
    }
    
    /** call this to end the edit and send the values over the hardware interface.
     *@see #startBatchEdit
     */
    public void endBatchEdit() throws HardwareInterfaceException {
        if(isBatchEditOccurring()){
            setBatchEditOccurring(false);
            sendPotValues(this);
        }
    }
    
    /** called when observable (masterbias) calls notifyObservers. Sets the powerDown state. */
    public void update(Observable observable, Object object){
        if(observable!=masterbias) {
            log.warning("Biasgen.update(): unknown observable "+observable);
            return;
        }
        if(object.equals("powerDownEnabled")){
//            log.info("Biasgen.update(): setting powerdown");
            try{
                if(!isOpen()) open();
                hardwareInterface.setPowerDown(masterbias.isPowerDownEnabled());
            }catch(HardwareInterfaceException e){
                log.warning("Biasgen.update(): error setting powerDown: "+e);
            }
        }
    }
    
    /** called when the individual biases are changed. Sends ipot values. */
    public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getSource()==iPotArray){
//            log.info("Biasgen.propertyChange(): sending ipot values");
            try{
                if(!isOpen()) open();
                if(!isBatchEditOccurring() && hardwareInterface!=null && isOpen()) 
                    hardwareInterface.sendPotValues(this);
            }catch(HardwareInterfaceException e){
//                log.warning("Biasgen.propertyChange(): "+e);
                java.awt.Toolkit.getDefaultToolkit().beep();
            }
        }else{
            log.warning("Biasgen.propertyChange(): unknown source "+evt.getSource());
        }
    }
    
    /** Get an IPot by name.
     * @param name name of pot as assigned in IPot
     *@return the IPot, or null if there isn't one named that
     */
    public Pot getPotByName(String name){
        return getPotArray().getPotByName(name);
    }
    
    /** Get an IPot by number in IPotArray. Note that first entry is last one in shift register.
     * @param number name of pot as assigned in IPot
     *@return the IPot, or null if there isn't one named that
     */
    public Pot getPotByNumber(int number){
        return getPotArray().getPotByNumber(number);
    }
    
    
    /** @return interface, or null if none has been sucessfully opened */
    public BiasgenHardwareInterface getHardwareInterface() {
        return this.hardwareInterface;
    }
    
    /** @param hardwareInterface the hardware interface */
    public void setHardwareInterface(final BiasgenHardwareInterface hardwareInterface) {
        this.hardwareInterface = hardwareInterface;
        if(hardwareInterface!=null){
            log.info(Thread.currentThread()+": Biasgen.setHardwareInterface("+hardwareInterface+"): sendIPotValues()");
           try{
               sendPotValues(this); // make sure after we set hardware interface that new bias values are sent to device, which may have been just connected.
           }catch(HardwareInterfaceException e){
               log.warning(e.getMessage()+ ": sending bias values after setting hardware interface");
           }
        }
    }
        
    public int getNumPots(){
        return getPotArray().getNumPots();
    }
    
    public void close() {
        if(hardwareInterface!=null) hardwareInterface.close();
    }
    
       /** flashes the the ipot values onto the hardware interface. 
     *@param biasgen the bias generator object. 
     * This parameter is necessary because the same method is used in the hardware interface, 
     * which doesn't know about the particular bias generator instance.
     *@throws HardwareInterfaceException if there is a hardware error. If there is no interface, prints a message and just returns.
     **/
    public void flashPotValues(Biasgen biasgen) throws HardwareInterfaceException {
        if(!isOpen()) open();
        if(isOpen()) hardwareInterface.flashPotValues(biasgen);
    }
    
    /** opens the first available hardware interface found */
    public void open() throws HardwareInterfaceException {
        if(hardwareInterface==null){
            log.info("Biasgen.open(): hardwareInterface is null, creating a new interface to open");
            hardwareInterface=(BiasgenHardwareInterface)(HardwareInterfaceFactory.instance().getFirstAvailableInterface());
        }
        // doesn't throw exception, just returns null if there is no device
        if(hardwareInterface==null){
            log.warning("Biasgen.open(): no device found");
            throw new HardwareInterfaceException("Biasgen.open(): can't find device to open");
        }
        hardwareInterface.open();
    }
    
    /** sends the ipot values over the hardware interface if there is not a batch edit occuring. 
     *@param biasgen the bias generator object. 
     * This parameter is necessary because the same method is used in the hardware interface, 
     * which doesn't know about the particular bias generator instance.
     *@throws HardwareInterfaceException if there is a hardware error. If there is no interface, prints a message and just returns.
     *@see #startBatchEdit
     *@see #endBatchEdit
     **/
    public void sendPotValues(Biasgen biasgen) throws HardwareInterfaceException {
        if(hardwareInterface==null){
            log.warning("Biasgen.sendIPotValues(): no hardware interface");
            return;
        }
        if(!isBatchEditOccurring() && hardwareInterface!=null ) {
//            log.info("calling hardwareInterface.sendPotValues");
            hardwareInterface.sendPotValues(biasgen);
        }
    }
    
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
         if(hardwareInterface==null){
            log.warning("Biasgen.setPowerDown(): no hardware interface");
            return;
        }
       hardwareInterface.setPowerDown(powerDown);
    }
    
    public String getTypeName() {
        if(hardwareInterface==null){
            log.warning("Biasgen.getTypeName(): no hardware interface, returning empty string");
            return "";
        }
        return hardwareInterface.getTypeName();
    }
    
    public boolean isOpen() {
        if(hardwareInterface==null) {
//            log.info("Biasgen.isOpen(): no hardware interface, returning false");
            return false;
        }
        return hardwareInterface.isOpen();
    }
    
    /** sets all the biases to zero current
     @see #resume
     */
    public void suspend(){
        startBatchEdit();
        for(Pot p:iPotArray.getPots()){
            p.suspend();
        }
        try{ endBatchEdit(); } catch(HardwareInterfaceException e){ e.printStackTrace();}
    }
    
    /** restores biases after suspend
     *@see #suspend
     */
    public void resume(){
        startBatchEdit();
         for(Pot p:iPotArray.getPots()){
            p.resume();
        }
        try{ endBatchEdit(); } catch(HardwareInterfaceException e){ e.printStackTrace();}
   }

    /** boolean that flags that a batch edit is occurring
     *@return true if there is a batch edit occuring
     *@see #startBatchEdit
     *@see #endBatchEdit
     */
    public boolean isBatchEditOccurring() {
        return batchEditOccurring;
    }

    /** sets boolean to flag batch edit occuring
     *@param batchEditOccurring true to signal that it is occuring
     *@see #startBatchEdit
     *@see #endBatchEdit
     */
    public void setBatchEditOccurring(boolean batchEditOccurring) {
        this.batchEditOccurring = batchEditOccurring;
    }

    /** @return the list of IPotGroup lists for this Biasgen */
    public ArrayList<IPotGroup> getIPotGroups() {
        return iPotGroups;
    }

    public void setIPotGroups(ArrayList<IPotGroup> iPotGroups) {
        this.iPotGroups = iPotGroups;
    }
    
    
}
