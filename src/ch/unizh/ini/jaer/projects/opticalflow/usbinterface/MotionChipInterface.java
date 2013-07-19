/*
 * MotionChipInterface.java
 *
 * Created on November 24, 2006, 6:48 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.opticalflow.usbinterface;

import javax.swing.JPanel;

import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.MotionData;

/**
 * The hardware interface to the motion chip.
 
 * @author tobi
 * 
 * changes by andstein
 * <ul>
 * <li>added <code>getRawDataIndex()</code> and <code>setChannel</code> for 
 *     additional hardware configuration.</li>
 * <li>added an optional configuration panel through which hardware interfaces can
 *     interact with the user (see <code>OpticalFlowDisplayControlPanel</code>)</li>
 * </ul>
 */
public interface MotionChipInterface extends BiasgenHardwareInterface {

    /**
     * this method is called by Chip2DMotion.setCaptureMode -- the capture
     * mode is a or-ed combination of the flags defined in MotionData; neither
     * the SiLabsC8051F320 nor the dsPIC33F_COM properly implement this
     * function...
     *
     @param mask or-ed mask of bits defined in ChipMotion (and its child classes)
     */
    public void setCaptureMode(int mask) throws HardwareInterfaceException;

    /**
     * returns an index into the array returned by MotionData.getRawDataPixel()
     * that corresponds to the data specified by the bit
     *
     * @param bit single bit of the bit-mask used in setCaptureMode()
     * @return index into MotionData.getRawDataPixel() data array,
     *  (-1) in case of error
     */
    public int getRawDataIndex(int bit);

    /**
     * use this method to influence what data is transmitted by the chip
     *
     * bear in mind that the different hardware interfaces will transmit at
     * least the specified channel, but eventually more data is streamed in
     * any case -- see the respective documentations for details
     *
     * @param bit single bit of the bit-mask used in setCaptureMode()
     *      specifying what data should be transmitted
     * @param onChip whether to use the on-chip ADC
     * @throws HardwareInterfaceException
     */
    public void setChannel(int bit,boolean onChip) throws HardwareInterfaceException;
    
    /** Returns the latest data from the device
     @return the data
     @throws TimeOutException if the exchange with the device times out
     */
    public MotionData getData() throws java.util.concurrent.TimeoutException;

    /** Sets the Chip that is connected to the hardware interface.
     *
     * This chip is used to create chip specific MotionData etc.
     * 
     * @param chip
     */
    public void setChip(Chip2DMotion chip);

    /** create configuration GUI
     *
     * @return a panel that will configure this hardware interface
     */
    public JPanel getConfigPanel();


}
