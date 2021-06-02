/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.util.scanner;

import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceProxy;

/**
 * Represents the hardware scanner interface as a host-side software object that stores
 * in preferences the state of the scanner, and calls update listener(s) when the state is changed.
 * A listener (e.g. a Chip's configuration control (biasgen)) registers itself as a listener
 * on this. In the update() method it reads the desired scanner state and sends appropriate messages
 * to the hardware.
 *
 * @author tobi
 */
public class ScannerHardwareInterfaceProxy extends HardwareInterfaceProxy implements ScannerHardwareInterface {

//     static final Logger log = Logger.getLogger("HardwareInterfaceProxy");
    protected ScannerHardwareInterface hw;
    protected int scanX, scanY;
    protected boolean scanContinuouslyEnabled;
    protected int minScanX = 0;
    protected int maxScanX = 32;
    protected int minScanY = 0;
    protected int maxScanY = 32;

    public ScannerHardwareInterfaceProxy(Chip chip) {
        super(chip);
        scanContinuouslyEnabled = getPrefs().getBoolean("CochleaAMS1cHardwareInterface.scanContinuouslyEnabled", true);
        scanX = getPrefs().getInt("CochleaAMS1cHardwareInterface.scanX", 0);
        scanY = getPrefs().getInt("CochleaAMS1cHardwareInterface.scanY", 0);
    }

    public void setScanY(int scanY) {
        int old=this.scanY;
        this.scanY = scanY;
        getPrefs().putInt("ScannerHardwareInterface.scanY", scanY);
        setScanContinuouslyEnabled(false);
        if(old!=this.scanY) notifyChange(EVENT_SCAN_Y);
    }

    public void setScanX(int scanX) {
        int old=this.scanX;
        this.scanX = scanX;
        getPrefs().putInt("ScannerHardwareInterface.scanX", scanX);
        setScanContinuouslyEnabled(false);
        if(old!=this.scanX) notifyChange(EVENT_SCAN_X);
    }

    public void setScanContinuouslyEnabled(boolean scanContinuouslyEnabled) {
        boolean old=this.scanContinuouslyEnabled;
        this.scanContinuouslyEnabled = scanContinuouslyEnabled;
        getPrefs().putBoolean("ScannerHardwareInterface.scanContinuouslyEnabled", scanContinuouslyEnabled);
        if(old!=this.scanContinuouslyEnabled) notifyChange(EVENT_SCAN_CONTINUOUSLY_ENABLED);
    }

    public boolean isScanContinuouslyEnabled() {
        return scanContinuouslyEnabled;
    }

    public int getScanY() {
        return scanY;
    }

    public int getScanX() {
        return scanX;
    }

    /**
     * @return the minScanX
     */
    public int getMinScanX() {
        return minScanX;
    }

    /**
     * @return the maxScanX
     */
    public int getMaxScanX() {
        return maxScanX;
    }

    /**
     * @return the minScanY
     */
    public int getMinScanY() {
        return minScanY;
    }

    /**
     * @return the maxScanY
     */
    public int getMaxScanY() {
        return maxScanY;
    }

    /**
     * @param minScanX the minScanX to set
     */
    public void setMinScanX(int minScanX) {
        this.minScanX = minScanX;
    }

    /**
     * @param maxScanX the maxScanX to set
     */
    public void setMaxScanX(int maxScanX) {
        this.maxScanX = maxScanX;
    }

    /**
     * @param minScanY the minScanY to set
     */
    public void setMinScanY(int minScanY) {
        this.minScanY = minScanY;
    }

    /**
     * @param maxScanY the maxScanY to set
     */
    public void setMaxScanY(int maxScanY) {
        this.maxScanY = maxScanY;
    }
}
