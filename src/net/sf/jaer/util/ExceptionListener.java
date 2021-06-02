/*
 */
package net.sf.jaer.util;
       
/** 
 * The interface for an ExceptionListener
 *@see net.sf.jaer.hardwareinterface.HardwareInterfaceException
 */
public interface ExceptionListener {
    public void exceptionOccurred(Exception x, Object source);
}


