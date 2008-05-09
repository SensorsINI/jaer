/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.hardwareinterface.usb.linux;

import ch.unizh.ini.caviar.hardwareinterface.HardwareInterface;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceFactoryInterface;
import java.util.ArrayList;

/**
 * Makes hardware interfaces under linux.
 * @author tobi
 */
public class HardwareInterfaceFactoryLinux implements HardwareInterfaceFactoryInterface {

    private static HardwareInterfaceFactoryLinux instance=new HardwareInterfaceFactoryLinux();
    private ArrayList<HardwareInterface> interfaceList = new ArrayList<HardwareInterface>();

    /** Creates a new instance of HardwareInterfaceFactoryLinux, private because this is a singleton factory class */
    private HardwareInterfaceFactoryLinux() {
    }
    
    /** Use this instance to access the methods, e.g. <code>HardwareInterfaceFactoryLinux.instance().getNumInterfacesAvailable()</code>.
     @return the singleton instance.
     */
    public static HardwareInterfaceFactoryLinux instance() {
        return instance;
    }
    
    
    private void buildInterfaceList(){
        if(!System.getProperty("os.name").startsWith("linux")) return; // only under linux
        
        HardwareInterface u;
        // build a list of linux USB compatible devices, store it in interfaceList
        interfaceList.add(new CypressFX2RetinaLinux(0));
    }
    
    /** Says how many total of all types of hardware are available
     @return number of devices 
     */
    public int getNumInterfacesAvailable(){
        buildInterfaceList();
        return interfaceList.size();
    }
    
    /** @return first available interface, starting with CypressFX2 and then going to SiLabsC8051F320 */
    public HardwareInterface getFirstAvailableInterface(){
        return getInterface(0);
    }
    
    /** build list of devices and return the n'th one, 0 based */
    public HardwareInterface getInterface(int n) {
//        buildInterfaceList();
        if(interfaceList==null || interfaceList.size()==0) return null;
        if(n>interfaceList.size()-1) return null;
        else {
            HardwareInterface hw=interfaceList.get(n);
//            System.out.println("HardwareInterfaceFactoryLinux.getInterace("+n+")="+hw);
            return hw;
        }
    }
    


}
