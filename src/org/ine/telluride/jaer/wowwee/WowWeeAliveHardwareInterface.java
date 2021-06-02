/*
 * WowWeeAliveHardwareInterface.java
 *
 * Created on July 16, 2007, 1:59 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 16, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package org.ine.telluride.jaer.wowwee;

import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_ServoController;

/**
 * For controlling WowWee Alive toys (specifically the WowWee Alive Chimp toy)
 using the USB Servo board using UsbIo interface to USB. For Telluride 2007.
 Note that the Chimp definitely uses a different code than the Robosapiens. The Robosapiens uses
 a 12 bit code based on 1200 baud 4T/1T long/short 0/1 bits and an 8T start bit. The Chimp uses an 11 bit code
 with equal-length 0/1 bit codes.
 <p>
 To use WowWeeAliveHardwareInterface, construct this type directly because ServoInterfaceFactory will not make these objects for you yet.
 <p>
 The byte codes for control of the WowWee Alive chimp can be found in the
 INI jAER archives or by searching old SVN revisions. This file is also reproduced here
 <pre>
 Control Code Modulation

The RF radio control is modulated with a simple digital code to specify the different features. For modulating the signals yourself, the signal looks something like this (timing values are approximate, but they work for me):

        Each code is 11 data bits with a specific start and stop sequence. Signal from the radio is normally random noise. When driven from the PC, keep the signal low until needed (ie. the start bit will start with a low->high transition). Start bit: (assuming the signal has been low for some time), the signal goes high for 8.4ms. Data bits: for each of 11 data bits, an encoded signal is sent (total time per bit ~6.4ms) Sends the most significant data bit first If the data bit is 0: signal goes low for ~2.2ms, and high for 4.2ms If the data bit is 1: signal goes low for ~4.2ms, and high for ~2.2s Stop bit: When completed, signal goes low for at least 6.4ms.

        The signal described above is the demodulated digital signal found on the pink wire described above.


        Control Codes

        Here are the code numbers (as used by the driver software).
        There are 3 different types of control codes.
        The first kind is a simple button press (eg: Demo), the second kind is on of the 6 skits (depends on mood).
        For the first two modes, when you press a button, the code is usually sent 8 times by the RF transmitter (the remote) to make sure it gets through.

LSB first in controller, codes assume MSB first!!!!!!!!!!!!!!!!!


              Simple Controls (same regardless of mood switches)

            * $009 = DEMO button
            * $00A = Program button
            * $00B = Alive button

        Undocumented/Internal

      # $006 = enter "chirp" mode (debug mode)
      # $00? = exit "chirp" mode

        Skit buttons (influenced by current mood)

      # $000 + MOOD = "X" button
      # $001 + MOOD = "Y" button
      # $002 + MOOD = "Z" button
      # $003 + MOOD = "A" button
      # $004 + MOOD = "B" button
      # $005 + MOOD - "C" button

        NOTE: MOOD = $000 (Curious) or $100 (Happy) or $200 (Fearful) or $300 (Feisty)
        The last kind is the most complicated where two different joystick settings are sent in the same 11 bit code. Which joystick is sent depends on the mood settings (see the manual for how the mood switches influence the functions).
        For the full range of motions, you need to send two different kinds of codes (eg: Mood $000 and Mood $300)
        In the regular remote, the code is sent while the joystick is down (???IIRC).


            * $400 + MOOD + RIGHT_DIRECTION*16 + LEFT_DIRECTION


        MOOD is one of the values described above ($000, $100, $200 or $300).
        RIGHT_DIRECTION is the position of the right joystick (value 0->8).
        LEFT_DIRECTION is the position of the left joystick (value 0->8)>


        The joystick positions (RIGHT_DIRECTION & LEFT_DIRECTION) are specified in 4 bits each:


            * 0 = joystick in center position
            * 1 = joystick up
            * 2 = joystick down
            * 3 = joystick left
            * 4 = joystick right
            * 5 = joystick up and left
            * 6 = joystick up and right
            * 7 = joystick down and left
            * 8 = joystick down and right

****************
Addendum by Ping Wang - 7/2007

We were not able to get the codes above to work using the wowwee.java class object.
Instead, we did a complete remapping of the behaviors through a systematic test of its reactions.

Some of the codes are:
b, c, d, f, 2, 4, 6, 8 - various crys and laughs
205 - possible head turn
400, 402, etc. - longer laughs and crys, set movements

Here are the codes that were used for the gesture tracking demo:
"909" - nose sniff
"904" - happy laughing
"102" - happy chrip
"906" - angry cry



</pre>

 * @author tobi delbruck, christina savin, ping wang, Telluride 2007
 */
public class WowWeeAliveHardwareInterface extends SiLabsC8051F320_USBIO_ServoController {

    public final byte CMD_WOWWEE=(byte)15; // vendor command to sent command to toy

    /** Creates a new instance of WowWeeAliveHardwareInterface */
    public WowWeeAliveHardwareInterface() {
    }


     /** send command to toy. The first byte (0x3) of command is sent by default and does not need to
     * be included in the cmd array.
     * @param command last 2 bytes of the command.
     */
    synchronized public void sendWowWeeCmd(short command) {
        checkServoCommandThread();
        ServoCommand cmd=new ServoCommand();
        byte[] b=new byte[3];
        cmd.bytes=b;
        b[0]=CMD_WOWWEE;
        b[1]=(byte)(command&0xff);
        b[2]=(byte)(command>>>8);
        submitCommand(cmd);
    }

}
