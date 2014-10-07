/*
 * Chip.java
 *
 * Created on October 5, 2005, 11:34 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.chip;

import java.beans.PropertyChangeSupport;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Observable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.util.RemoteControl;

/**
 * A chip, having possibly a hardware interface and a bias generator.
 This class extends Observable and signals changes in
 its parameters via notifyObservers.
 * <p>
 A Chip also has Preferences; the Preferences node is based on the package of the
 actual chip class.
 *
 *<p>
 * A Chip may be remote-controllable via its remote control, see getRemoteControl().
 * <p>
 * @author tobi
 */
@Description("Base class for all devices/chips etc.")
public class Chip extends Observable {
    /** Preferences key for blank device filename */
    public static final String DEFAULT_FIRMWARE_BIX_FILE_FOR_BLANK_DEVICE = "CypressFX2Blank.defaultFirmwareBixFileForBlankDevice";

    /** The root preferences for this Chip.
     * @see Chip#getPrefs()
     */
    private Preferences prefs=Preferences.userNodeForPackage(Chip.class);

    /** Preferences key which is used to store the preferences boolean that preferred values have been loaded at least
     * once for this Chip.
     */
    public static final String PREFERENCES_LOADED_ONCE_KEY="defaultPreferencesWereLoaded";

    /** The default preferences file location for initial import of preferred values. By default it is null. */
    private String defaultPreferencesFile=null;

    /** The bias generator for this chip or object that holds any other kind of configuration information. (Originally this was just the
     actual digital bias values, but since this original definition it has grealy expanded to include board-level configuration such
     as scanner control, external ADC control, and control of off-chip DACs.
     */
    protected Biasgen biasgen=null;

    /** A String name */
    protected String name="unnamed chip";

    /** The Chip's HardwareInterface */
    protected HardwareInterface hardwareInterface=null;

    private static Class<? extends HardwareInterface> preferredHardwareInterface=null;

//    /** Should be overridden by a subclass of Chip to specify the preferred HardwareInterface. In the case of chips
//     * that use a variety of generic interfaces the factory will construct a default interface if getPreferredHardwareInterface
//     * return null.
//     * @return a HardwareInterface class.
//     */
//    static public Class<? extends HardwareInterface> getPreferredHardwareInterface(){
//        return Chip.preferredHardwareInterface;
//    }
//
//    /** Sets the preferred HardwareInterface class. Warning: this can call static initializers of a class, which can cause problems
//     * especially in non-standard classloader contexts, e.g. applets.
//     *
//     * @param clazz the class that must extend HardwareInterface.
//     */
//    static public void setPreferredHardwareInterface(Class<? extends HardwareInterface> clazz){
//        Chip.preferredHardwareInterface=clazz;
//    }

     /** Default firmware file for blank devices.
     * For CypressFX2-based USB devices, this file must be a bix (raw binary) firmware file, not an iic or hex file */
    protected  String defaultFirmwareBixFileForBlankDevice=null;


    /** The remote control allows control of this Chip via a UDP connection
     *
     */
    private RemoteControl remoteControl;

    /** This built in Logger should be used for logging, e.g. via log.info or log.warn
     *
     */
    protected static Logger log=Logger.getLogger("Chip");


    /** Built-in PropertyChangeSupport to allow this Chip to generate PropertyChangeEvents. */
    protected PropertyChangeSupport support=new PropertyChangeSupport(this);

    /** Can be used to hold a reference to the last data associated with this Chip2D */
    private Object lastData=null;

    /** Creates a new instance of Chip */
    public Chip() {
//        try {
//            if (!prefs.nodeExists(getClass().getPackage().getName())) {
//                log.info("no existing Preferences node for " + getClass().getCanonicalName());
//            }
            setPrefs(Preferences.userNodeForPackage(getClass())); // set prefs here based on actual class
//        } catch (BackingStoreException ex) {
//            log.warning(ex.toString());
//        }
       defaultFirmwareBixFileForBlankDevice  = getPrefs().get(DEFAULT_FIRMWARE_BIX_FILE_FOR_BLANK_DEVICE, null);
       try {
            remoteControl = new RemoteControl();
            log.info("Created "+remoteControl+" for control of "+this);
        } catch (IOException e) {
            log.warning("couldn't make remote control for "+this+" : "+e);
        }
    }

    /** Check if this Chip has default preferences, and if so and they have not yet been loaded, loads them into the Preferences node
     * for this Chip.
     * <p>
     * Warning: If this method is called in a Chip's constructor and previous preferences exist from before
     * the use of this method, they could be deleted because the key PREFERENCES_LOADED_ONCE_KEY has not yet been written to signal that
     * preferences were loaded at least once. To use this method for existing Chip classes, the Chip's constructor can also call
     * the Biasgen isInitalized method to check if any Pot has been set to a non-zero value.
     * @see ch.unizh.ini.jaer.chip.retina.DVS128 for an example of use
     * @see #getDefaultPreferencesFile()
     */
    protected void maybeLoadDefaultPreferences() {
        if((getDefaultPreferencesFile()!=null) && !isDefaultPreferencesLoadedOnce()){
             InputStream is = null;
             try {
                 log.warning("no default preferences were loaded so far - importing from "+getDefaultPreferencesFile()+" to Preferences node "+getPrefs());

                 is = new BufferedInputStream(new FileInputStream(getDefaultPreferencesFile()));
                 Preferences.importPreferences(is);  // this uses the Preferences object to load all preferences from the input stream which an xml file
                 getPrefs().putBoolean(PREFERENCES_LOADED_ONCE_KEY, true);
             } catch (Exception ex) {
                 Logger.getLogger(Chip.class.getName()).log(Level.SEVERE, null, ex);
             } finally {
                 try {
                     if(is!=null) {
						is.close();
					}
                 } catch (IOException ex) {
                     Logger.getLogger(Chip.class.getName()).log(Level.SEVERE, null, ex);
                 }
             }
         }
    }


    /** This empty method can be called to clean up if the Chip is no longer
     * used or need to un-install some registered GUI elements or clean up memory. */
    public void cleanup(){

    }

    public Biasgen getBiasgen() {
        return biasgen;
    }

    public void setBiasgen(Biasgen biasgen) {
        this.biasgen = biasgen;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** gets the hardware interface for this Chip
     @return the hardware interface
     */
    public HardwareInterface getHardwareInterface() {
        return this.hardwareInterface;
    }

    /** Sets the hardware interface and the bias generators hardware interface
     * (if the interface supports the bias generator). Notifies Observers with the new HardwareInterface.
     *@param hardwareInterface the interface
     */
    public void setHardwareInterface(final HardwareInterface hardwareInterface) {
//        System.out.println(Thread.currentThread()+" : Chip.setHardwareInterface("+hardwareInterface+")");
        this.hardwareInterface = hardwareInterface;
        if((getBiasgen()!=null) && (hardwareInterface instanceof BiasgenHardwareInterface)) {
            biasgen.setHardwareInterface((BiasgenHardwareInterface)hardwareInterface);
        }
        setChanged();
        notifyObservers(hardwareInterface);
        if((hardwareInterface instanceof AEMonitorInterface) && (this instanceof AEChip)){
            ((AEMonitorInterface)hardwareInterface).setChip((AEChip)this);
        }
    }

    /** Gets the last data associated with this Chip object. Whatever method obtains this data is responsible for setting this reference.
     @return the last data object.
     */
    public Object getLastData() {
        return lastData;
    }

    /** Sets the last data captured or rendered by this Chip. Can be used to reference this data through the Chip instance.
     @param lastData the data. Usually but not always (e.g. MotionData) this object is of type EventPacket.
     * @see net.sf.jaer.event.EventPacket
     */
    public void setLastData(Object lastData) {
        this.lastData = lastData;
    }


    /** Returns the Preferences node for this Chip. All preferred configuration should be stored and retrieved with this node.
     @return the node
     */
    public Preferences getPrefs() {
        return prefs;
    }

    /** Sets the Preferences node for the Chip
     @param prefs the node
     */
    public void setPrefs(Preferences prefs) {
        this.prefs = prefs;
//        log.info(this+" has prefs="+prefs);
    }

    /** This remote control can be used for remote (via UDP) control of the Chip, e.g. the biases. */
    public RemoteControl getRemoteControl() {
        return remoteControl;
    }

    /** This remote control can be used for remote (via UDP) control of the Chip, e.g. the biases. */
    public void setRemoteControl(RemoteControl remoteControl) {
        this.remoteControl = remoteControl;
    }

    /** Returns some default firmware file for soft-download to Cypress FX2/3 blank device.  Default is null.
     *
     * @return full (or relative to start folder "java") path to firmware .bix file for Cypress FX2 based devices.
     */
    public  String getDefaultFirmwareBixFileForBlankDevice() {
        return defaultFirmwareBixFileForBlankDevice;
    }

   /** Sets some default firmware file for soft-download to device.
     *
     * @return full (or relative to start folder "java") path to firmware .bix file for Cypress FX2 based devices.
     */
   public  void setDefaultFirmwareBixFileForBlankDevice(String aDefaultFirmwareBixFileForBlankDevice) {
        this.defaultFirmwareBixFileForBlankDevice = aDefaultFirmwareBixFileForBlankDevice;
        getPrefs().put(DEFAULT_FIRMWARE_BIX_FILE_FOR_BLANK_DEVICE, defaultFirmwareBixFileForBlankDevice);
    }

    /**
    * This file, if not null, is used to import preferences if they have not been initialized.
    * A Chip can set this path relative to the startup folder (in jAER the startup folder is host/java) to
    * automatically have preferences imported on first use. For example set the file path to "biasgenSettings/DVS128/DVS128Fast.xml".
    *
     * @return the defaultPreferencesFile
     */
    public String getDefaultPreferencesFile() {
        return defaultPreferencesFile;
    }

    /**
     * This file, if not null, is used to import preferences if they have not been initialized.
    * A Chip can set this path relative to the startup folder (in jAER the startup folder is host/java) to
    * automatically have preferences imported on first use.
    *
     * @param defaultPreferencesFile the defaultPreferencesFile to set
     */
    public void setDefaultPreferencesFile(String defaultPreferencesFile) {
        this.defaultPreferencesFile = defaultPreferencesFile;
    }

    /** Returns true if default preferences were loaded at least once.
     *
     * @return true if preferences were loaded.
     */
    public boolean isDefaultPreferencesLoadedOnce(){
        return getPrefs().getBoolean(PREFERENCES_LOADED_ONCE_KEY, false);
    }


    /** Returns the logger used to log info
     *
     * @return logger of the chip
     */
    public Logger getLog(){
        return Chip.log;
    }

    /**
     *  Returns built-in PropertyChangeSupport to allow this Chip to generate PropertyChangeEvents.

     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }

    /**
     * Sets built-in PropertyChangeSupport that allows this Chip to generate PropertyChangeEvents.
     * @param support the support to set
     */
    public void setSupport(PropertyChangeSupport support) {
        this.support = support;
    }

}
