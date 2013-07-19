/*
 * USBAEMonitorException.java
 *
 * Created on February 17, 2005, 8:01 AM
 */

package net.sf.jaer.hardwareinterface;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.sf.jaer.util.ExceptionListener;

/**
 *  An exception in the hardware interface is signaled with this exception.
 * @author  tobi
 */
public class HardwareInterfaceException extends java.lang.Exception {
    
    static private Set exceptionListeners = Collections.synchronizedSet(new HashSet<ExceptionListener>());
    
    /** Creates a new instance of HardwareInterfaceException
     * 
     */
    public HardwareInterfaceException() {
        super();
        sendException(this);
    }
    
    /** Creates a new instance of HardwareInterfaceException with a message
     * 
     * @param s the message
     */
    public HardwareInterfaceException(String s){
        super(s);
        sendException(this);
    }

    /** Creates a new instance of HardwareInterfaceException with a message and cause
     * 
     * @param s the message
     * @param cause the cause
     */
     public HardwareInterfaceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    
    
    private void sendException(Exception x) {
        if (exceptionListeners.size() == 0) {
//            System.out.println("HardwareInterfaceException caught in HardwareInterfaceException listeners, stack trace is");
//            x.printStackTrace();
            return;
        }
        
        synchronized (exceptionListeners) {
            Iterator iter = exceptionListeners.iterator();
            while (iter.hasNext()) {
                ExceptionListener l = (ExceptionListener) iter.next();
                
                l.exceptionOccurred(x, this);
            }
        }
    }
    
    /** Listeners can be added for exceptions - e.g. GUI elements that show status 
     @param l the listener
     */
    @SuppressWarnings("unchecked")
    static public void addExceptionListener(ExceptionListener l) {
        if (l != null) {
            exceptionListeners.add(l);
        }
    }
    
    /** Removes exception listener
     * 
     * @param l the listener
     */
    static public void removeExceptionListener(ExceptionListener l) {
        exceptionListeners.remove(l);
    }
    
    /** This static method sends a null message to all ExceptionListeners to signify that the exception condition is gone */
    static public void clearException(){
        // TODO made this method do nothing since this is really a logging approach
        // new HardwareInterfaceException();
    }
    
}
