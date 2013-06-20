/*
 * HardwareInterfaceFactoryInterface.java
 * 
 * Created on October 3, 2005, 11:45 AM
 * 
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.hardwareinterface;

/**
 * Defines the interface a hardware interface factory has to have to be
 * included in the list in HardwareInterfaceFactory.
 * 
 * @author tobi
 */
public interface HardwareInterfaceFactoryInterface {

	/**
	 * Returns the number of available interfaces, i.e., the number of available hardware devices.
	 * If the driver only supports one interface, then 1 will always be returned.
	 * 
	 * @return number of interfaces
	 */
	public int getNumInterfacesAvailable();

	/**
	 * Gets the first available interface.
	 * 
	 * @return first available interface, or null if no interfaces are found.
	 */
	public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException;

	/**
	 * Returns one of the interfaces
	 * 
	 * @param n
	 *            the number starting from 0
	 * @return the HardwareInterface
	 * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
	 *             if there is some error
	 */
	public HardwareInterface getInterface(int n) throws HardwareInterfaceException;

	/**
	 * Returns the windows GUID for the interface, e.g. "{2013DFAA-ED13-4775-9967-8C3FEC412E2C}".
	 * 
	 * @return the String GUID or null if it is not relevant for the class.
	 */
	public String getGUID();
}
