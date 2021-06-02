/*
 * HardwareInterfaceMenu.java
 *
 * Created on January 30, 2006, 10:56 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface;

import java.util.logging.Logger;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import de.thesycon.usbio.PnPNotifyInterface;

/**
 * A menu that allows selection of a particular HardwareInterface. This menu is automatically constructed on instance creation and updates itself whenever asked or when new
 *devices are added of USBIO type.
 *
 * @author tobi
 */
public class HardwareInterfaceMenu extends JMenu implements PnPNotifyInterface{

    static Logger log=Logger.getLogger("HardwareInterfaceMenu");
    /** Creates a new instance of HardwareInterfaceMenu */
    public HardwareInterfaceMenu() {
        setName("Interface");
        buildMenu();
    }

    public void onAdd() {
        buildMenu();
    }

    public void onRemove() {
        buildMenu();
    }
    
    int numDevices=0;
    
    synchronized public void buildMenu(){
        HardwareInterfaceFactory factory=HardwareInterfaceFactory.instance();
        HardwareInterface hw;
        numDevices=factory.getNumInterfacesAvailable();
        log.info("HardwareInterfaceMenu.buildMenu found "+numDevices+" devices");
        removeAll();
        for(int i=0;i<numDevices;i++){
            hw=factory.getInterface(i);
            if(hw==null) continue;
            String typeName=hw.getTypeName();
            JMenuItem mi=new JMenuItem(typeName);
            add(mi);
        }
    }
}
