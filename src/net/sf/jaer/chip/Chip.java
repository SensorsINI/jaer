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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Observable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.sf.jaer.Description;
import net.sf.jaer.JaerConstants;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.eventprocessing.filter.PreferencesMover;
import net.sf.jaer.util.RemoteControl;

/**
 * A chip, having possibly a hardware interface and a bias generator. This class
 * extends Observable and signals changes in its parameters via notifyObservers.
 * <p>
 * A Chip also has Preferences; the Preferences node is based on the package of
 * the actual chip class.
 *
 * <p>
 * A Chip may be remote-controllable via its remote control, see
 * getRemoteControl().
 * <p>
 * @author tobi
 */
@Description("Base class for all devices/chips etc.")
public class Chip extends Observable {

    /**
     * Preferences key for blank device filename
     */
    public static final String DEFAULT_FIRMWARE_BIX_FILE_FOR_BLANK_DEVICE = "CypressFX2Blank.defaultFirmwareBixFileForBlankDevice";

    /**
     * Fired when biases or other hardware changes
     *
     * @see #getSupport()
     */
    public static final String EVENT_HARDWARE_CHANGE = "EVENT_HARDWARE_CHANGE";
    /**
     * The root preferences for this Chip.
     *
     * @see Chip#getPrefs()
     */
    private Preferences prefs = null; // constructed in constructor

    /**
     * Preferences key which is used to store the preferences boolean that
     * preferred values have been loaded at least once for this Chip.
     */
    public static final String PREFERENCES_LOADED_ONCE_KEY = "defaultPreferencesWereLoaded";

    /**
     * Preferences key: first live hardware open for this chip already ran
     * first-use UX (default prefs import + Hardware Configuration panel).
     */
    public static final String FIRST_HARDWARE_USE_HANDLED_KEY = "firstHardwareUseHandled";

    /**
     * The default preferences file location for initial import of preferred
     * values. By default it is null.
     */
    private String defaultPreferencesFile = null;

    /**
     * The bias generator for this chip or object that holds any other kind of
     * configuration information. (Originally this was just the actual digital
     * bias values, but since this original definition it has grealy expanded to
     * include board-level configuration such as scanner control, external ADC
     * control, and control of off-chip DACs.
     */
    protected Biasgen biasgen = null;

    /**
     * A String name
     */
    protected String name = "unnamed chip";

    /**
     * The Chip's HardwareInterface
     */
    protected HardwareInterface hardwareInterface = null;

    private static Class<? extends HardwareInterface> preferredHardwareInterface = null;

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
    /**
     * Default firmware file for blank devices. For CypressFX2-based USB
     * devices, this file must be a bix (raw binary) firmware file, not an iic
     * or hex file
     */
    protected String defaultFirmwareBixFileForBlankDevice = null;

    /**
     * The remote control allows control of this Chip via a UDP connection
     *
     */
    private RemoteControl remoteControl;

    /**
     * This built in Logger should be used for logging, e.g. via log.info or
     * log.warn
     *
     */
    protected static Logger log = Logger.getLogger("net.sf.jaer");

    /**
     * Built-in PropertyChangeSupport to allow this Chip to generate
     * PropertyChangeEvents.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * Can be used to hold a reference to the last data associated with this
     * Chip2D
     */
    private Object lastData = null;
    /** jAER 3.0: last typed packet bundle from ViewLoop (may be null on older paths). */
    private net.sf.jaer.event.PacketBundle lastBundle = null;

    /**
     * Creates a new instance of Chip
     */
    public Chip() {
        try {
//            if (!prefs.nodeExists(getClass().getPackage().getName())) {
//                log.info("no existing Preferences node for " + getClass().getCanonicalName());
//            }

            if (Preferences.userRoot().nodeExists(prefsNodeNameOriginal()) && !Preferences.userRoot().nodeExists(prefsNodeName())) {
                setPrefs(Preferences.userRoot().node(prefsNodeNameOriginal())); // set prefs here based on actual class
//                PreferencesMover.moveChipPreferences(getPrefs());
                log.warning(String.format("For chip %s, older prefs %s and not newer prefs %s existed, using older %s", getClass().getSimpleName(), prefsNodeNameOriginal(), prefsNodeName(), prefs.absolutePath()));
            } else if (Preferences.userNodeForPackage(Chip.class).nodeExists(prefsNodeName())) {
                setPrefs(Preferences.userRoot().node(prefsNodeName()));
                log.info(String.format("Chip-specific Preference node %s for chip %s exists, will use it", prefs.absolutePath(), getClass().getSimpleName()));
            } else {
                setPrefs(Preferences.userRoot().node(prefsNodeName())); // set prefs here based on actual class
                log.info(String.format("Made new Preference node %s for chip %s", prefs.absolutePath(), getClass().getSimpleName()));
            }
        } catch (BackingStoreException ex) {
            log.warning(String.format("Got exception when checking if Preference node exists: %s", ex.toString()));
        }

        PreferencesMover.OldPrefsCheckResult result = PreferencesMover.hasOldChipPreferences(this);
        if (result.hasOldPrefs()) {
            log.warning(result.message());
            PreferencesMover.migratePreferencesDialog(null,this,true,false,result.message());
        } else {
            log.fine(result.message());
        }

        try {
            remoteControl = new RemoteControl();
            log.info("Created " + remoteControl + " for control of " + this);
        } catch (IOException e) {
            log.warning("couldn't make remote control for " + this + " : " + e);
        }
        defaultFirmwareBixFileForBlankDevice = getPrefs().get(DEFAULT_FIRMWARE_BIX_FILE_FOR_BLANK_DEVICE, null);
    }

    /**
     * Name of unique Preferences node for this Chip, so that all Preferences
     * are isolated to the Chip, not just the package
     *
     * @return getClass().getPackageName().replace('.',
     * '/')+"/"+getClass().getSimpleName(), e.g., chip
     * eu.seebetter.ini.chips.davis.Davis346Blue
     */
    public String prefsNodeName() {
        return JaerConstants.PREFS_ROOT_CHIPS.node(getClass().getSimpleName()).absolutePath();
    }

    /**
     * The original preference node name for a Chip, which was the chip package,
     * which contains typically many chips
     *
     * @return getClass().getPackageName().replace('.', '/'), e.g. chip
     * eu.seebetter.ini.chips.davis
     */
    public String prefsNodeNameOriginal() {
        return getClass().getPackageName().replace('.', '/');
    }

    /**
     * Sets the default preferences path to
     * {@code deviceSettings/<familyFolder>/<ChipSimpleName>.xml}.
     *
     * @param familyFolder short family folder under {@code deviceSettings/}
     *            (e.g. {@code Davis240}, {@code Davis346}, {@code DVS128})
     */
    protected void setDefaultPreferencesFileForFamily(String familyFolder) {
        if (familyFolder == null || familyFolder.isEmpty()) {
            setDefaultPreferencesFile(null);
            return;
        }
        setDefaultPreferencesFile("deviceSettings/" + familyFolder + "/" + getClass().getSimpleName() + ".xml");
    }

    /**
     * Resolves the default preferences file path: explicit
     * {@link #getDefaultPreferencesFile()}, else conventional
     * {@code deviceSettings/<SimpleName>/<SimpleName>.xml} if that file exists.
     *
     * @return path string, or null if none
     */
    public String resolveDefaultPreferencesFile() {
        if (defaultPreferencesFile != null && !defaultPreferencesFile.isEmpty()) {
            return defaultPreferencesFile;
        }
        String conventional = "deviceSettings/" + getClass().getSimpleName() + "/" + getClass().getSimpleName() + ".xml";
        if (new File(conventional).isFile()) {
            return conventional;
        }
        return null;
    }

    /**
     * Check if this Chip has default preferences, and if so and they have not
     * yet been loaded, loads them into the Preferences node for this Chip.
     * <p>
     * Warning: If this method is called in a Chip's constructor and previous
     * preferences exist from before the use of this method, they could be
     * deleted because the key PREFERENCES_LOADED_ONCE_KEY has not yet been
     * written to signal that preferences were loaded at least once. To use this
     * method for existing Chip classes, the Chip's constructor can also call
     * the Biasgen isInitalized method to check if any Pot has been set to a
     * non-zero value. Prefer calling from first hardware open (AEViewer) after
     * biasgen is built.
     *
     * @return true if a default preferences file was imported
     * @see #getDefaultPreferencesFile()
     * @see #resolveDefaultPreferencesFile()
     */
    public boolean maybeLoadDefaultPreferences() {
        String path = resolveDefaultPreferencesFile();
        if (path == null || isDefaultPreferencesLoadedOnce()) {
            return false;
        }
        File file = new File(path);
        if (!file.isFile()) {
            log.warning("default preferences file not found for " + getClass().getSimpleName() + ": " + path);
            return false;
        }
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            log.info("importing initial preferences for " + getClass().getSimpleName() + " from " + path
                    + " into Preferences node " + getPrefs().absolutePath());
            // Prefer Biasgen.importPreferences: batches USB configuration instead of sending
            // on every preferenceChange during Preferences.importPreferences (which wedges USB/EDT).
            if (biasgen != null) {
                biasgen.importPreferences(is);
            } else {
                Preferences.importPreferences(is);
            }
            getPrefs().putBoolean(PREFERENCES_LOADED_ONCE_KEY, true);
            return true;
        } catch (Exception ex) {
            log.log(Level.SEVERE, "failed to import default preferences from " + path, ex);
            return false;
        }
    }

    /**
     * Shows an informational dialog that initial preferences were loaded from a
     * default deviceSettings XML file.
     *
     * @param parent parent frame (may be null)
     * @param path path that was imported
     */
    public void showDefaultPreferencesLoadedDialog(final JFrame parent, final String path) {
        final String chipName = getClass().getSimpleName();
        final String message = "<html>Initial hardware preferences for <b>" + chipName
                + "</b> were loaded from<p><code>" + path + "</code>."
                + "<p>The Hardware Configuration panel will open so you can review biases and other options."
                + "<p>You can change settings later via that panel (File → Load/Save settings).</html>";
        final String title = "Initial preferences loaded for " + chipName;
        Runnable r = () -> {
            // Prefer simple JOptionPane: WarningDialogWithDontShowPreference can be suppressed and
            // is easier to miss when BiasgenFrame construction races the EDT.
            log.info("showing initial-preferences dialog for " + chipName + " loaded from " + path);
            JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /**
     * @return true if first live hardware-open UX already ran for this chip
     */
    public boolean isFirstHardwareUseHandled() {
        return getPrefs().getBoolean(FIRST_HARDWARE_USE_HANDLED_KEY, false);
    }

    /**
     * Marks that first live hardware-open UX has run for this chip.
     */
    public void setFirstHardwareUseHandled(boolean handled) {
        getPrefs().putBoolean(FIRST_HARDWARE_USE_HANDLED_KEY, handled);
    }

    /**
     * This empty method can be called to clean up if the Chip is no longer used
     * or need to un-install some registered GUI elements or clean up memory.
     */
    public void cleanup() {

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

    /**
     * gets the hardware interface for this Chip
     *
     * @return the hardware interface
     */
    public HardwareInterface getHardwareInterface() {
        return this.hardwareInterface;
    }

    /**
     * Sets the hardware interface and the bias generators hardware interface
     * (if the interface supports the bias generator). Notifies Observers with
     * the new HardwareInterface.
     *
     * @param hardwareInterface the interface
     */
    public void setHardwareInterface(final HardwareInterface hardwareInterface) {
//        System.out.println(Thread.currentThread()+" : Chip.setHardwareInterface("+hardwareInterface+")");
        this.hardwareInterface = hardwareInterface;
        if ((getBiasgen() != null) && (hardwareInterface instanceof BiasgenHardwareInterface)) {
            biasgen.setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
        }
        setChanged();
        notifyObservers(hardwareInterface);
        if ((hardwareInterface instanceof AEMonitorInterface) && (this instanceof AEChip)) {
            ((AEMonitorInterface) hardwareInterface).setChip((AEChip) this);
        }
    }

    /**
     * Gets the last data associated with this Chip object. Whatever method
     * obtains this data is responsible for setting this reference.
     *
     * @return the last data object.
     */
    public Object getLastData() {
        return lastData;
    }

    /**
     * Sets the last data captured or rendered by this Chip. Can be used to
     * reference this data through the Chip instance.
     *
     * @param lastData the data. Usually but not always (e.g. MotionData) this
     * object is of type EventPacket.
     * @see net.sf.jaer.event.EventPacket
     */
    public void setLastData(Object lastData) {
        this.lastData = lastData;
    }

    /**
     * jAER 3.0: last {@link net.sf.jaer.event.PacketBundle} from the view loop.
     */
    public net.sf.jaer.event.PacketBundle getLastBundle() {
        return lastBundle;
    }

    public void setLastBundle(net.sf.jaer.event.PacketBundle lastBundle) {
        this.lastBundle = lastBundle;
    }

    /**
     * Returns the Preferences node for this Chip. All preferred configuration
     * should be stored and retrieved with this node.
     *
     * @return the node
     */
    public Preferences getPrefs() {
        return prefs;
    }

    /**
     * Returns a string header for all preferences keys associated with this
     * chip configuration, e.g. "Davis346B."
     *
     * @return the key
     */
    public String prefsHeader() {
        return this.getClass().getSimpleName();
    }

    /**
     * Sets the Preferences node for the Chip
     *
     * @param prefs the node
     */
    public void setPrefs(Preferences prefs) {
        this.prefs = prefs;
        log.fine(this + " has prefs=" + prefs);
    }

    /**
     * This remote control can be used for remote (via UDP) control of the Chip,
     * e.g. the biases.
     */
    public RemoteControl getRemoteControl() {
        return remoteControl;
    }

    /**
     * This remote control can be used for remote (via UDP) control of the Chip,
     * e.g. the biases.
     */
    public void setRemoteControl(RemoteControl remoteControl) {
        this.remoteControl = remoteControl;
    }

    /**
     * Returns some default firmware file for soft-download to Cypress FX2/3
     * blank device. Default is null.
     *
     * @return full (or relative to start folder "java") path to firmware .bix
     * file for Cypress FX2 based devices.
     */
    public String getDefaultFirmwareBixFileForBlankDevice() {
        return defaultFirmwareBixFileForBlankDevice;
    }

    /**
     * Sets some default firmware file for soft-download to device.
     *
     * @return full (or relative to start folder "java") path to firmware .bix
     * file for Cypress FX2 based devices.
     */
    public void setDefaultFirmwareBixFileForBlankDevice(String aDefaultFirmwareBixFileForBlankDevice) {
        this.defaultFirmwareBixFileForBlankDevice = aDefaultFirmwareBixFileForBlankDevice;
        getPrefs().put(DEFAULT_FIRMWARE_BIX_FILE_FOR_BLANK_DEVICE, defaultFirmwareBixFileForBlankDevice);
    }

    /**
     * This file, if not null, is used to import preferences if they have not
     * been initialized. A Chip can set this path relative to the startup folder
     * (in jAER the startup folder is host/java) to automatically have
     * preferences imported on first use. For example set the file path to
     * "deviceSettings/DVS128/DVS128Fast.xml".
     *
     * @return the defaultPreferencesFile
     */
    public String getDefaultPreferencesFile() {
        return defaultPreferencesFile;
    }

    /**
     * This file, if not null, is used to import preferences if they have not
     * been initialized. A Chip can set this path relative to the startup folder
     * (in jAER the startup folder is host/java) to automatically have
     * preferences imported on first use.
     *
     * @param defaultPreferencesFile the defaultPreferencesFile to set
     */
    public void setDefaultPreferencesFile(String defaultPreferencesFile) {
        this.defaultPreferencesFile = defaultPreferencesFile;
    }

    /**
     * Returns true if default preferences were loaded at least once.
     *
     * @return true if preferences were loaded.
     */
    public boolean isDefaultPreferencesLoadedOnce() {
        return getPrefs().getBoolean(PREFERENCES_LOADED_ONCE_KEY, false);
    }

    /**
     * Returns the logger used to log info
     *
     * @return logger of the chip
     */
    public Logger getLog() {
        return Chip.log;
    }

    /**
     * Returns built-in PropertyChangeSupport to allow this Chip to generate
     * PropertyChangeEvents.
     *
     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }

    /**
     * Sets built-in PropertyChangeSupport that allows this Chip to generate
     * PropertyChangeEvents.
     *
     * @param support the support to set
     */
    public void setSupport(PropertyChangeSupport support) {
        this.support = support;
    }

}
