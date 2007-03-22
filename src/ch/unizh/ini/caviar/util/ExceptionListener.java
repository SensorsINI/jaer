/*
 */
package ch.unizh.ini.caviar.util;
       
/** 
 * The interface for an ExceptionListener
 *@see ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException
 */
public interface ExceptionListener {
    public void exceptionOccurred(Exception x, Object source);
}


