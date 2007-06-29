/*
 * Chip.java
 *
 * Created on October 5, 2005, 11:34 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.chip;

import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.biasgen.*;
import ch.unizh.ini.caviar.biasgen.Biasgen;
import ch.unizh.ini.caviar.hardwareinterface.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.prefs.*;

/**
 * A chip, having possibly a hardware interface and a bias generator. 
 Note this class extends Observable and signals changes in 
 its parameters via notifyObservers.
 A Chip also has Preferences; the Preferences node is based on the package of the 
 actual chip class.
 *
 * @author tobi
 */
public class Chip extends Observable {
    
    private Preferences prefs=Preferences.userNodeForPackage(Chip.class);    

    /** The bias generator for this chip */
    protected Biasgen biasgen=null;
    
    /** A String name */
    protected String name="unnamed chip";
    
    /** The Chip's HardwareInterface */
    protected HardwareInterface hardwareInterface=null;
    
    protected static Logger log=Logger.getLogger("Chip");
    
    /** Can be used to hold a reference to the last data associated with this Chip2D */
    private Object lastData=null;
    
    /** Creates a new instance of Chip */
    public Chip() {
        setPrefs(Preferences.userNodeForPackage(getClass())); // set prefs here based on actual class   
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
    
    /** sets the hardware interface and the bias generators hardware interface (if the interface supports the bias generator)
     *<p>
     *Notifies observers with the new hardwareInterface
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
     @param lastData the data
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
        log.info(this+" has prefs="+prefs);
    }
    

}
