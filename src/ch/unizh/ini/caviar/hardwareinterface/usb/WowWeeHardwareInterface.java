/*
 * WowWeeHardwareInterface.java
 *
 * Created on July 16, 2007, 1:59 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 16, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.hardwareinterface.usb;

import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.*;

/**
 * For controlling WowWee toys using the USB Servo board using UsbIo interface to USB. For Telluride 2007.
 <p> 
 To use it, construct this type directly because ServoInterfaceFactory will not make these objects for you yet.
 
 * @author tobi
 */
public class WowWeeHardwareInterface extends SiLabsC8051F320_USBIO_ServoController {
    
    public final byte CMD_WOWWEE=(byte)0xbe; // vendor command to sent command to toy

    /** Creates a new instance of WowWeeHardwareInterface */
    public WowWeeHardwareInterface() {
    }
    
        
     /** send command to toy. The first byte (0x3) of command is sent by default and does not need to 
     * be included in the cmd array.
     * @param cmd last 2 bytes of the command.
     */
    synchronized public void sendWowWeeCmd(short cmdByte) {
        ServoCommand cmd=new ServoCommand();
        byte[] b=new byte[2];
        b[0]=CMD_WOWWEE;
        b[1]=(byte)(cmdByte&0xff);
        submitCommand(cmd);
    }
    
}
