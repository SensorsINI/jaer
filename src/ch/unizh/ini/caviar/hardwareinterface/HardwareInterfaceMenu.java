/*
 * HardwareInterfaceMenu.java
 *
 * Created on January 30, 2006, 10:56 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.hardwareinterface;

import de.thesycon.usbio.*;
import javax.swing.*;

/**
 * A menu that allows selection of a particular HardwareInterface. This menu is automatically constructed on instance creation and updates itself whenever asked or when new
 *devices are added of USBIO type.
 *
 * @author tobi
 */
public class HardwareInterfaceMenu extends JMenu implements PnPNotifyInterface{

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
        System.out.println("HardwareInterfaceMenu.buildMenu found "+numDevices+" devices");
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
