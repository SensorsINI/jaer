/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.tobi.zipzaps;

import ch.unizh.ini.caviar.hardwareinterface.HardwareInterface;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.caviar.hardwareinterface.usb.ServoInterfaceFactory;
import ch.unizh.ini.caviar.hardwareinterface.usb.SiLabsC8051F320_USBIO_ServoController;
import ch.unizh.ini.caviar.util.HexString;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 * Controls the Radio Shack ZipZaps micro RC Transformers car via the ServoController port 2, which pulls down on appropriate port 2 bits
 * to activate either steering or acceleration. Control is bang bang.
 * <p>
 * If steering and speed are independent. Setting speed (fwd or back) does not affect steering, and same for steering.
 * @author tobi
 */
public class ZipZapControl implements HardwareInterface {
    
    Logger log=Logger.getLogger("ZipZapControl");

    private final int RIGHT = 1<<4,  
            LEFT = 1<<5,  
            FWD = 1<<2,  
            BACK = 1<<3; // bang bang, lowering this bit in port 2 does this by activating open drain pulldown on this intput to the epoxy dot controller
    
    private final int STEERING=(RIGHT)|(LEFT);
    private final int SPEED=(FWD)|(BACK);
    
    private SiLabsC8051F320_USBIO_ServoController hw;

    private int lastBits=0; // last bits set (positive value, actually ~this is sent to bring bits low)
    
    public ZipZapControl() {

    }

    public String getTypeName() {
        return "ZipZap micro RC car controller";
    }

    public void close() {
        if (hw != null) {
            hw.close();
        }
    }

    public void open() throws HardwareInterfaceException {
        try {
            hw = (SiLabsC8051F320_USBIO_ServoController) ServoInterfaceFactory.instance().getFirstAvailableInterface(); //new SiLabsC8051F320_USBIO_ServoController();
        } catch (ClassCastException e) {
            throw new HardwareInterfaceException("Can't find a SiLabsC8051F320_USBIO_ServoController to open");
        }
        if (hw == null) {
            JOptionPane.showMessageDialog(null, "No SiLabsC8051F320_USBIO_ServoController found");
        }
        hw.open();
    }

    public boolean isOpen() {
        return hw != null && hw.isOpen();
    }
    
    public void right(){
        bit2cmd(RIGHT, STEERING);
       
    }
    
    public void left(){
        bit2cmd(LEFT, STEERING);
      
    }
    
    public void straight(){
        bit2cmd(0, STEERING);
    }
    
    public void fwd(){
        bit2cmd(FWD, SPEED);
    }
    
    public void back(){
        bit2cmd(BACK, SPEED);
    }
    
    public void coast(){ // take off the gas
        bit2cmd(0, SPEED);
    }
    
    public void stop(){
        bit2cmd(0, SPEED&STEERING);
    }

    private void bit2cmd(int bit, int mask){ // sets bit exclusively in mask
        System.out.println("bit="+bit+" mask="+mask);
        checkOpen();
        lastBits= bit|(lastBits&~mask);
        hw.setPort2(~lastBits);
    }
    
    private void checkOpen(){
        if(hw==null||(hw!=null && !hw.isOpen())){
            try{
                open();
            }catch(Exception e){
                log.warning(e.toString());
            }
        }
    }
    
    public static void main(String[] args){
        new ZipZapControlGUI().setVisible(true);
    }
}
