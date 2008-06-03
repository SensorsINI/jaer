/*
 * HardwareInterfaceFactory.java
 *
 * Created on October 2, 2005, 5:38 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.hardwareinterface;

import ch.unizh.ini.caviar.hardwareinterface.usb.*;
import ch.unizh.ini.caviar.hardwareinterface.usb.linux.HardwareInterfaceFactoryLinux;
import java.lang.reflect.*;
import java.util.*;

/**
 * This class builds a list of all available devices and lets you get one of them.
 *It is a singleton: get the instance() and ask it to make an interface for you.
 *
 * @author tobi
 */
public class HardwareInterfaceFactory extends HashSet<Class> implements HardwareInterfaceFactoryInterface{
    
    HashSet<Class> factoryHashSet =new HashSet<Class>();
    private ArrayList<HardwareInterface> interfaceList = null;
    
    // these are devices that can be enumerated and opened
    static Class[] factories={
        //CypressFX2TmpdiffRetinaFactory.class, 
            SiLabsC8051F320Factory.class, 
            CypressFX2Factory.class//,
//            HardwareInterfaceFactoryLinux.class
          //  CypressFX2MonitorSequencerFactory.class  // this removed because all CypressFX2 devices are found by their common GUID now at the same time
    }; // raphael: added my class so i can still test before having refactored
    
    private static HardwareInterfaceFactory instance=new HardwareInterfaceFactory();
    
    /** Creates a new instance of HardwareInterfaceFactory, private because this is a singleton factory class */
    private HardwareInterfaceFactory() {
    }
    
    /** Use this instance to access the methods, e.g. <code>HardwareInterfaceFactory.instance().getNumInterfacesAvailable()</code>.
     @return the singleton instance.
     */
    public static HardwareInterfaceFactory instance() {
        return instance;
    }
    
    
    private void buildInterfaceList(){
        interfaceList=new ArrayList<HardwareInterface>();
        HardwareInterface u;
//        System.out.println("****** HardwareInterfaceFactory.building interface list");
        for(int i=0;i<factories.length;i++){
            try{
                Method m=((factories[i]).getMethod("instance")); // get singleton instance of factory
                HardwareInterfaceFactoryInterface inst=(HardwareInterfaceFactoryInterface)m.invoke(factories[i]);
                int num=inst.getNumInterfacesAvailable(); // ask it how many devices are out there
//                System.out.println("interface "+inst+" has "+num+" devices available");
                for(int j=0;j<num;j++){
                    u=inst.getInterface(j); // for each one, construct the HardwareInterface and put it in a list
                    interfaceList.add(u);
//                    System.out.println("HardwareInterfaceFactory.buildInterfaceList: added "+u);
                }
            }catch(NoSuchMethodException e){
                e.printStackTrace();
            }catch(IllegalAccessException e3){
                e3.printStackTrace();
            }catch(InvocationTargetException e4){
                e4.printStackTrace();
            }catch(HardwareInterfaceException e5){
                e5.printStackTrace();
            }
        }
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
//            System.out.println("HardwareInterfaceFactory.getInterace("+n+")="+hw);
            return hw;
        }
    }
    public static void main(String  [] arg) {
        HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
    }
            
//    /** Adds a factory to the list of factory classes. Subclasse can use this to add themselves.
//     
//     @param factoryClass the Class of the factory
//     */
//    public static void addFactory(Class<HardwareInterfaceFactory> factoryClass){
//        factoryHashSet.add(factoryClass);
//    }
    
}
