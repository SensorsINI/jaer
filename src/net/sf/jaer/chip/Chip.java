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

import java.io.IOException;
import net.sf.jaer.aemonitor.*;
import net.sf.jaer.biasgen.*;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.hardwareinterface.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.prefs.*;
import net.sf.jaer.util.RemoteControl;

/**
 * A chip, having possibly a hardware interface and a bias generator. 
 This class extends Observable and signals changes in 
 its parameters via notifyObservers.
 * <p>
 A Chip also has Preferences; the Preferences node is based on the package of the 
 actual chip class.
 * <p>
 * A Chip has a preferred hardware interface class which is used in the HardwareInterfaceFactory or its subclasses
 * to construct the actual hardware interface class when the hardware interface is enumerated.
 *
 *<p>
 * A Chip may be remote-controllable via its remote control, see getRemoteControl().
 * @author tobi
 */
public class Chip extends Observable {
    
     /** Override this String (can be html formatted) to show it as the Chip description in some GUI controls. */
     public static String getDescription(){
         return "Chip";
     }
     
    private Preferences prefs=Preferences.userNodeForPackage(Chip.class);    

    /** The bias generator for this chip */
    protected Biasgen biasgen=null;
    
    /** A String name */
    protected String name="unnamed chip";
    
    /** The Chip's HardwareInterface */
    protected HardwareInterface hardwareInterface=null;
    
    private static Class<? extends HardwareInterface> preferredHardwareInterface=null;
    
    /** Should be overridden by a subclass of Chip to specify the preferred HardwareInterface. In the case of chips
     * that use a variety of generic interfaces the factory will construct a default interface if getPreferredHardwareInterface
     * return null.
     * @return a HardwareInterface class.
     */
    static public Class<? extends HardwareInterface> getPreferredHardwareInterface(){
        return Chip.preferredHardwareInterface;
    }
    
    /** Sets the preferred HardwareInterface class.
     * 
     * @param clazz the class that must extend HardwareInterface.
     */
    static public void setPreferredHardwareInterface(Class<? extends HardwareInterface> clazz){
        Chip.preferredHardwareInterface=clazz;
    }
    
    private RemoteControl remoteControl;

    protected static Logger log=Logger.getLogger("Chip");
    
    /** Can be used to hold a reference to the last data associated with this Chip2D */
    private Object lastData=null;
    
    /** Creates a new instance of Chip */
    public Chip() {
        try {
            if (!prefs.nodeExists(getClass().getPackage().getName())) {
                log.info("no existing Preferences node for " + getClass().getCanonicalName());
            }            
            setPrefs(Preferences.userNodeForPackage(getClass())); // set prefs here based on actual class
        } catch (BackingStoreException ex) {
            log.warning(ex.toString());
        }
       try {
            remoteControl = new RemoteControl();
        } catch (IOException e) {
            log.warning("couldn't make remote control: "+e);
        }
    }
    
    /** Creates a new instance of Chip */
    public Chip(HardwareInterface hardwareInterface) {
        this();
        this.hardwareInterface=hardwareInterface;
    }
    
    public Chip(Biasgen biasgen){
        this();
        this.biasgen=biasgen;
    }
    
    public Chip(HardwareInterface hardwareInterface, Biasgen biasgen){
        this();
        this.biasgen=biasgen;
        setHardwareInterface(hardwareInterface);
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
        if(getBiasgen()!=null && hardwareInterface instanceof BiasgenHardwareInterface) {
            biasgen.setHardwareInterface((BiasgenHardwareInterface)hardwareInterface);
        }
        setChanged();
        notifyObservers(hardwareInterface);
        if(hardwareInterface instanceof AEMonitorInterface && this instanceof AEChip){
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


    /** Returns the Preferences node for this Chip. 
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

    public void setRemoteControl(RemoteControl remoteControl) {
        this.remoteControl = remoteControl;
    }

}
