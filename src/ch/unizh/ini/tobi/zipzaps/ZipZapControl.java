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
 * @author tobi
 */
public class ZipZapControl implements HardwareInterface {
    
    Logger log=Logger.getLogger("ZipZapControl");

    private final byte STEER_RIGHT_BIT = 4,  
            STEER_LEFT_BIT = 5,  
            GO_FORWARD_BIT = 2,  
            GO_BACKWARD_BIT = 3; // bang bang, lowering this bit in port 2 does this by activating open drain pulldown on this intput to the epoxy dot controller
    private SiLabsC8051F320_USBIO_ServoController hw;

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
    
    public void steerRight(){
         checkOpen();
        hw.setPort2(bit2cmd(STEER_RIGHT_BIT));
       
    }
    
    public void steerLeft(){
          checkOpen();
        hw.setPort2(bit2cmd(STEER_LEFT_BIT));
      
    }
    
    public void goForward(){
        checkOpen();
        hw.setPort2(bit2cmd(GO_FORWARD_BIT));
        
    }
    
    public void goBackward(){
        checkOpen();
        hw.setPort2(bit2cmd(GO_BACKWARD_BIT));
    }
    
    public void stop(){
        checkOpen();
        hw.setPort2(0xff);
        
    }

    private int bit2cmd(int bit){
        int v= ~(1<<bit);
        log.info("sending "+HexString.toString((byte)v));
        return v;
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
}
