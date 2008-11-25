/*
 * BiasgenPreferences.java
 *
 * Created on September 25, 2005, 2:42 PM
 */

package net.sf.jaer.biasgen;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import java.io.*;
import java.util.prefs.InvalidPreferencesFormatException;


/**
 * Interface for biasgen preference storage and export/import
 * @author tobi
 */
public interface BiasgenPreferences {
       /** store the present values as the preferred values */
    public void storePreferences();
    
    /** load  the stored preferred values -- e.g. revert to the last stored values */
    public void loadPreferences();
    
    /** export the prefered values to an OutputStream */
    public void exportPreferences(OutputStream os) throws java.io.IOException ;
    
    /** import prefs from an InputStream */
    public void importPreferences(InputStream is) throws java.io.IOException, InvalidPreferencesFormatException, HardwareInterfaceException ;

}
