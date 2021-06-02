/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.util.scanner;

/**
 * Scanner control interface.
 * 
 * @author tobi
 */
public interface ScannerHardwareInterface {

    public static final String EVENT_SCANNER_CHANGED = "scannerChanged",
            EVENT_SCAN_X = "scanX",
            EVENT_SCAN_Y = "scanY";
    public static final String EVENT_SCAN_CONTINUOUSLY_ENABLED = "scanContinuouslyEnabled";

    /**
     * @return the scanX
     */
    public int getScanX();

    /**
     * @return the scanY
     */
    public int getScanY();

    /**
     * @return the scanContinuouslyEnabled
     */
    public boolean isScanContinuouslyEnabled();

    /**
     * @param scanContinuouslyEnabled the scanContinuouslyEnabled to set
     */
    public void setScanContinuouslyEnabled(boolean scanContinuouslyEnabled);

    /**
     * @param scanX the scanX to set
     */
    public void setScanX(int scanX);

    /**
     * @param scanY the scanY to set
     */
    public void setScanY(int scanY);
}
