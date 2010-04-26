/*
 * Biasgen.java
 *
 * Created on September 23, 2005, 8:52 PM
 */
package net.sf.jaer.biasgen;

import java.io.IOException;
import net.sf.jaer.chip.*;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.*;
import java.awt.Frame;
import java.util.*;
import java.util.logging.*;
import java.util.prefs.*;
import javax.swing.*;
import net.sf.jaer.util.RemoteControl;

/**
 * Describes a complete bias
 *generator, with a masterbias. a bunch of IPots, and a hardware interface.
 * This class handles the USB commands for the masterbias and ipots.
 *<p>
 *When using this class in conjunction with another use of the HardwareInterface, it is much better to share the same
 *interface if possible. Do this by constructing the Biasgen using an existing HardwareInterface.
 * <p>
 * Users of this class should also check for unitialized bias values and warn the user that bias settings should be loaded.
 *<p>
 * Biasgen can implement the formatting of the raw bytes that are sent, although for historical reasons the default implementation is
 * that it asks the BiasgenHardwareInterface to do this formatting. A particular system can override formatConfigurationBytes to 
 * implement a particular low level format.
 * <p>
 * Biasgen also implements a default method getControlPanel for building it's own user GUI interface, but subclasses 
 * can override this method to build their own arbitrarily-complex JPanel for control.
 * @author tobi
 */
public class Biasgen implements BiasgenPreferences, Observer, BiasgenHardwareInterface {

    transient protected PotArray potArray = null; // this is now PotArray instead of IPotArray, to make this class more generic
    transient private Masterbias masterbias = null;
    private String name = null;
    /** The hardware interface for this Biasgen object */
    protected BiasgenHardwareInterface hardwareInterface = null;
    private boolean batchEditOccurring = false;
    private Chip chip;
    private Preferences prefs;
    private static Logger log = Logger.getLogger("Biasgen");
    private ArrayList<IPotGroup> iPotGroups = new ArrayList<IPotGroup>(); // groups of pots

    /**
     *  Constructs a new biasgen. A BiasgenHardwareInterface is constructed when needed.
     *This biasgen adds itself as a PropertyChangeListener to the IPotArray.
     *It also adds itself as an Observer for the Masterbias.
     *@see HardwareInterfaceException
     */
    public Biasgen(Chip chip) {
        this.setChip(chip);
        prefs = chip.getPrefs();
        setHardwareInterface((BiasgenHardwareInterface) chip.getHardwareInterface());
        masterbias = new Masterbias(this);
        masterbias.addObserver(this);
//        Pot.setModificationTrackingEnabled(false);
        loadPreferences();
//        Pot.setModificationTrackingEnabled(true);

    }
    /** The built-in control panel that is built by getControlPanel on first call */
    protected JPanel controlPanel = null;

    /** Returns the graphical control panel for this Biasgen. The control panel must be first built using, e.g. the default
     * buildControlPanel.
     * @return the control panel
     */
    public JPanel getControlPanel() {
        return controlPanel;
    }

    /** 
     * Builds the default control panel and returns it.
     * This method builds a BiasgenPanel that encloses the PotArray in a PotPanel and the Masterbias in a MasterbiasPanel
     * and returns a tabbed pane for these two components.
     * Subclasses can override buildControlPanel to build their own control panel.
    @return the default control panel
     */
    public JPanel buildControlPanel() {
        startBatchEdit();
        BiasgenFrame frame = null;
        if (chip instanceof AEChip) {
            AEViewer viewer = ((AEChip) chip).getAeViewer();
            if (viewer != null) {
                frame = viewer.getBiasgenFrame();
            } else {
                log.warning("no BiasgenFrame to build biasgen control panel for");
                return null;
            }
        }
        JPanel panel = new BiasgenPanel(this, frame);    /// makes a panel for the pots and populates it, the frame handles undo support
        try {
            endBatchEdit();
        } catch (HardwareInterfaceException e) {
            log.warning(e.toString());
        }
        return panel;
    }

    /** Sets the control panel but doesn't do anything to rebuild the GUI interface. To rebuild the control
    panel, set the control panel to null and call getControlPanel.
    @param panel the new panel
     */
    public void setControlPanel(JPanel panel) {
        this.controlPanel = panel; // TODO - useless method since once it's set the GUI won't rebuild
    }

    /** A Biasgen has a single PotArray of biases.
     * 
     * @return the PotArray
     */
    public PotArray getPotArray() {
        return this.potArray;
    }

    public void setPotArray(final PotArray PotArray) {
        this.potArray = PotArray;
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

    /** exports preference values for this node of the chip preferences. Bias values should be stored at the root of the Chip, since
     * nodes branching from this root are not exported, in order to avoid cluttering the bias settings files with other preferences, e.g. for
     * EventFilter's.
     * <p>
     * Biases and other settings (e.g. master bias resistor) are written to the output stream as an XML file
     *@param os an output stream, typically constructed for a FileOutputStream
     *@throws IOException if the output stream cannot be written
     */
    public void exportPreferences(java.io.OutputStream os) throws java.io.IOException {
        storePreferences();
        try {
            prefs.exportNode(os);
            prefs.flush();
            log.info("exported prefs=" + prefs + " to os=" + os);
        } catch (BackingStoreException bse) {
            bse.printStackTrace();
        }

    }

    /** Imports preference values for this subtree of all Preferences (the biasgen package subtreee).
     * Biases and other settings (e.g. master bias resistor) are read in from an XML file. Bias values are sent as a batch to the device after values
     *are imported.
     *@param is an input stream, typically constructed for a FileInputStream
     *@throws IOException if the output stream cannot be read
     */
    public void importPreferences(java.io.InputStream is) throws java.io.IOException, InvalidPreferencesFormatException, HardwareInterfaceException {
        log.info("importing preferences from InputStream=" + is + " to prefs=" + prefs);
        startBatchEdit();
        Preferences.importPreferences(is);  // this uses the Preferences object to load all preferences from the input stream which an xml file
        loadPreferences();
        // the preference change listeners may not have been called by the time this endBatchEdit is called
        // therefore we start a thread to end the batch edit a bit later
        new Thread("Biasgen.endBatchEdit") {

            @Override
            public void run() {
                try {
                    Thread.sleep(1000); // sleep a bit for preference change listeners
                } catch (InterruptedException e) {
                }
                try {
                    setBatchEditOccurring(false);
                    sendConfiguration(Biasgen.this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /** Loads preferences (preferred values) for the potArray and masterbias. Subclasses should override this method
     * to load additional information.
     */
    public void loadPreferences() {
//        log.info("Biasgen.loadPreferences()");
        startBatchEdit();
        if (getPotArray() != null) {
            getPotArray().loadPreferences();
            masterbias.loadPreferences();
        }

        try {
            endBatchEdit();
        } catch (HardwareInterfaceException e) {
            log.warning(e.toString());
        }
    }

    /** Stores preferences to the Preferences node for the potArray and masterbias. Subclasses must override this method
     * to store additional information!!! For example, a subclass that defines additional configuration information should
     * call storePreferences explicitly for additional configuration.
     */
    public void storePreferences() {
        potArray.storePreferences();
        masterbias.storePreferences();
        log.info("stored preferences to preferences tree");
    }

    /** Use this method to put a value only if the value is different than the stored Preference value.
     * If the value has never been put to the Preferences then it will be put. If the stored Preference value
     * is different than the value being put it will be put.
     * 
     * @param key your key.
     * @param value your value.
     */
    public void putPref(String key, String value) {
        String s = prefs.get(key, null);
        if (s == null || !s.equals(value)) {
            prefs.put(key, value);
//            Thread.yield(); // sometimes let's preference change listeners run, but not always
        }
    }

    /** Use this method to put a value only if the value is different than the stored Preference value.
     * 
     * @param key your key.
     * @param value your value.
     */
    public void putPref(String key, boolean value) {
        putPref(key, String.valueOf(value));
    }

    /** Use this method to put a value to the preferences 
     * only if the value is different than the stored Preference value. 
     * Using this method will thus not call preference change listeners unless the value has changed.
     * If the value has never been put to the Preferences then it will be put and listeners will be called. 
     * If the stored Preference value
     * is different than the value being put it will be put and listeners will be called. 
     * Listeners will not be called if the value has previously been stored and is the same as the value being put.
     * 
     * @param key your key.
     * @param value your value.
     */
    public void putPref(String key, int value) {
         putPref(key, String.valueOf(value));       
    }

    /** Use this method to put a value to the preferences only if the value is different than the stored Preference value. Using this method will thus not call preference change listeners unless the value has changed.
     * 
     * @param key your key.
     * @param value your value.
     */
    public void putPref(String key, float value) {
          putPref(key, String.valueOf(value));
    }

    /** Use this method to put a value to the preferences only if the value is different than the stored Preference value. Using this method will thus not call preference change listeners unless the value has changed.
     * 
     * @param key your key.
     * @param value your value.
     */
    public void putPref(String key, double value) {
             putPref(key, String.valueOf(value));
    }

    @Override
    public String toString() {
        String s = this.getClass().getSimpleName() + " with ";
        s = s + potArray.toString();
        return s;
    }

    /** call this when starting a set of related pot value changes.
     *@see #endBatchEdit
     */
    public void startBatchEdit() {
        setBatchEditOccurring(true);
    }

    /** call this to end the edit and send the values over the hardware interface.
     *@see #startBatchEdit
     */
    public void endBatchEdit() throws HardwareInterfaceException {
        if (isBatchEditOccurring()) {
            setBatchEditOccurring(false);
            sendConfiguration(this);
        }
    }

    /** called when observable (masterbias) calls notifyObservers. 
    Sets the powerDown state. 
    If there is not a batch edit occuring, opens device if not open and calls sendConfiguration.
     */
    public void update(Observable observable, Object object) {
//        if(observable!=masterbias) {
//            log.warning("Biasgen.update(): unknown observable "+observable);
//            return;
//        }
        if (object != null && object.equals("powerDownEnabled")) {
//            log.info("Biasgen.update(): setting powerdown");
            try {
                if (!isBatchEditOccurring()) {
                    if (!isOpen()) {
                        open();
                    }
                    hardwareInterface.setPowerDown(masterbias.isPowerDownEnabled());
                }
            } catch (HardwareInterfaceException e) {
                log.warning("error setting powerDown: " + e);
            }
        } else {
            try {
                if (!isBatchEditOccurring()) {
                    if (!isOpen()) {
                        open();
                    }
                    hardwareInterface.sendConfiguration(this);
                }
            } catch (HardwareInterfaceException e) {
                log.warning("error sending pot values: " + e);
            }

        }
    }

    /** Get an IPot by name.
     * @param name name of pot as assigned in IPot
     *@return the IPot, or null if there isn't one named that
     */
    public Pot getPotByName(String name) {
        return getPotArray().getPotByName(name);
    }

    /** Get an IPot by number in IPotArray. Note that first entry is last one in shift register.
     * @param number name of pot as assigned in IPot
     *@return the IPot, or null if there isn't one named that
     */
    public Pot getPotByNumber(int number) {
        return getPotArray().getPotByNumber(number);
    }

    /** @return interface, or null if none has been sucessfully opened */
    public BiasgenHardwareInterface getHardwareInterface() {
        return this.hardwareInterface;
    }

    /** Assigns the HardwareInterface to this Biasgen. If non-null, the configuration information (e.g. biases) are also sent to the device.
     * @param hardwareInterface the hardware interface. 
     */
    public void setHardwareInterface(final BiasgenHardwareInterface hardwareInterface) {
        this.hardwareInterface = hardwareInterface;
        if (hardwareInterface != null) {
//            log.info(Thread.currentThread()+": Biasgen.setHardwareInterface("+hardwareInterface+"): sendIPotValues()");
            try {
                sendConfiguration(this); // make sure after we set hardware interface that new bias values are sent to device, which may have been just connected.
            } catch (HardwareInterfaceException e) {
                log.warning(e.getMessage() + ": sending configuration values after setting hardware interface");
            }
        }
    }

    public int getNumPots() {
        return getPotArray().getNumPots();
    }

    public void close() {
        if (hardwareInterface != null) {
            hardwareInterface.close();
        }
    }

    /** flashes the the ipot values onto the hardware interface.
     *@param biasgen the bias generator object.
     * This parameter is necessary because the same method is used in the hardware interface,
     * which doesn't know about the particular bias generator instance.
     *@throws HardwareInterfaceException if there is a hardware error. If there is no interface, prints a message and just returns.
     **/
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (!isOpen()) {
            open();
        }
        if (isOpen()) {
            hardwareInterface.flashConfiguration(biasgen);
        }
    }

    /** opens the first available hardware interface found */
    public void open() throws HardwareInterfaceException {
        if (hardwareInterface == null) {
//            log.info("Biasgen.open(): hardwareInterface is null, creating a new interface to open");
            try {
                hardwareInterface = (BiasgenHardwareInterface) (HardwareInterfaceFactory.instance().getFirstAvailableInterface());
            } catch (ClassCastException e) {
                log.warning(this + " is not a BiasgenHardwareInterface, ignoring open(): " + e.toString());
            }
        }
        // doesn't throw exception, just returns null if there is no device
        if (hardwareInterface == null) {
//            log.warning("Biasgen.open(): no device found");
            throw new HardwareInterfaceException("Biasgen.open(): can't find device to open");
        }
        hardwareInterface.open();
    }

    /** sends the ipot values over the hardware interface if there is not a batch edit occuring.
     *@param biasgen the bias generator object.
     * This parameter is necessary because the same method is used in the hardware interface,
     * which doesn't know about the particular bias generator instance.
     *@throws HardwareInterfaceException if there is a hardware error. If there is a null HardwareInterface, just returns.
     *@see #startBatchEdit
     *@see #endBatchEdit
     **/
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (hardwareInterface == null) {
//            log.warning("Biasgen.sendIPotValues(): no hardware interface");
            return;
        }
        if (!isBatchEditOccurring() && hardwareInterface != null && hardwareInterface.isOpen()) {
            hardwareInterface.sendConfiguration(biasgen);
        }
    }

    /** This method formats and returns a byte array of configuration information (e.g. biases, scanner or diagnostic bits) that
     * can be sent over the hardware interface using {@link #sendConfiguration}. This method delegates the task of formatting these
     * bytes to the Biasgen object rather than the more generic HardwareInterface. 
     * <p>
     * A Biasgen can override this method to 
     * customize the bytes that are sent. The default implementation asks the BiasgenHardwareInterface for the bytes.
     * @param biasgen source of the configuration
     * @return array of bytes to be sent.
     */
    @Override
    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        if (hardwareInterface == null) {
            return null;
        }
        return hardwareInterface.formatConfigurationBytes(this);
    }

    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        if (hardwareInterface == null) {
            log.warning("Biasgen.setPowerDown(): no hardware interface");
            return;
        }
        hardwareInterface.setPowerDown(powerDown);
    }

    public String getTypeName() {
        if (hardwareInterface == null) {
            log.warning("Biasgen.getTypeName(): no hardware interface, returning empty string");
            return "";
        }
        return hardwareInterface.getTypeName();
    }

    public boolean isOpen() {
        if (hardwareInterface == null) {
//            log.info("Biasgen.isOpen(): no hardware interface, returning false");
            return false;
        }
        return hardwareInterface.isOpen();
    }

    /** sets all the biases to zero current
    @see #resume
     */
    public void suspend() {
        startBatchEdit();
        for (Pot p : potArray.getPots()) {
            p.suspend();
        }
        try {
            endBatchEdit();
        } catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
    }

    /** restores biases after suspend
     *@see #suspend
     */
    public void resume() {
        startBatchEdit();
        for (Pot p : potArray.getPots()) {
            p.resume();
        }
        try {
            endBatchEdit();
        } catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
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
//        log.info("batchEditOccurring="+batchEditOccurring);
    }

    /** @return the list of IPotGroup lists for this Biasgen */
    public ArrayList<IPotGroup> getIPotGroups() {
        return iPotGroups;
    }

    public void setIPotGroups(ArrayList<IPotGroup> iPotGroups) {
        this.iPotGroups = iPotGroups;
    }

    /** Returns chip associated with this biasgen. Used, e.g. for preference keys.
    @return chip
     */
    public Chip getChip() {
        return chip;
    }

    /** Sets chip associated with this biasgen
    @param chip the chip
     */
    public void setChip(Chip chip) {
        this.chip = chip;
    }

    /** Checks for unitialized biases (no non-zero preference values).
     * 
     * @return true if any Pot value is non-zero.
     */
    public boolean isInitialized() {
        ArrayList<Pot> pots = getPotArray().getPots();
        if (getNumPots() == 0) {
            return true;
        }
        for (Pot p : pots) {
            if (p.getBitValue() != 0) {
                return true;
            }
        }
        return false;
    }

    /** Shows a dialog centered on the screen warning user to load bias settings
    @param container the window or panel that should contain the dialog
     */
    public void showUnitializedBiasesWarningDialog(Frame container) {
//        WarningDialogWithDontShowPreference dialog=new WarningDialogWithDontShowPreference(container,true,"Unitialized biases","<html>No bias values have been set.<p>To run your hardware you probably need to set biases.<p>To load existing bias values, open Biases panel and set or load values from a file in the folder <i>biasgenSettings</i><p>For the DVS128 sensor, using one of the <i>dvs128_*.html</i> files.<p>Otherwise, to remove this message, set any bias to a non-zero value.</html>");
//        dialog.setVisible(true);
        JOptionPane.showMessageDialog(container, "<html>No bias values have been set.<p>To run your hardware you probably need to set biases.<p>To load existing bias values, open Biases panel and set or load values from a file in the folder <i>biasgenSettings</i><p>For the DVS128 sensor, using one of the <i>DVS128*.xml</i> files.<p>Or, to remove this message, set any bias to a non-zero value.</html>", "Biases unitialized", JOptionPane.WARNING_MESSAGE);
    }
    
    /** Marks a class (for example, some object in a subclass of Biasgen) as having a preference that can be loaded and stored. Classes do *not* store preferences unless
     * explicitly asked to do so. E.g. setters do not store preferences. Otherwise this can lead to an infinite loop of 
     * set/notify/set.
     */
    protected interface HasPreference {

        void loadPreference();

        void storePreference();
    }

}
