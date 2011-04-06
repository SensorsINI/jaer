/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.util.scanner;

import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.HardwareInterfaceProxy;
import java.util.prefs.Preferences;
import net.sf.jaer.chip.Chip;

/**
 * Represents the hardware scanner interface as a host-side software object that sets hardware when available.
 *
 * @author tobi
 */
public class ScannerHardwareInterfaceProxy extends HardwareInterfaceProxy {

     static final Logger log = Logger.getLogger("HardwareInterfaceProxy");
   protected ScannerHardwareInterface hw;
    protected int scanX, scanY;
    protected boolean scanContinuouslyEnabled;
    private int minScanX = 0;
    private int maxScanX = 32;
    private int minScanY = 0;
    private int maxScanY = 32;
    private boolean printedWarning = false;

    public ScannerHardwareInterfaceProxy(Chip chip) {
        super(chip);
        scanContinuouslyEnabled = getPrefs().getBoolean("CochleaAMS1cHardwareInterface.scanContinuouslyEnabled", true);
        scanX = getPrefs().getInt("CochleaAMS1cHardwareInterface.scanX", 0);
        scanY = getPrefs().getInt("CochleaAMS1cHardwareInterface.scanY", 0);
    }

    private boolean checkHw() {
        if (hw == null) {
            if (!printedWarning) {
                printedWarning = true;
                log.warning("null hardware, not doing anything with ADC hardware");
            }
            return false;
        }
        return true;
    }

    public ScannerHardwareInterface getHw() {
        return hw;
    }

    /**
     * @param hw the hardware interface to set
     */
    public void setHw(ScannerHardwareInterface hw) {
        this.hw = hw;
    }

    public void setScanY(int scanY) {
        if (!checkHw()) {
            return;
        }
        hw.setScanY(scanY);
    }

    public void setScanX(int scanX) {
        if (!checkHw()) {
            return;
        }
        hw.setScanX(scanX);
    }

    public void setScanContinuouslyEnabled(boolean scanContinuouslyEnabled) {
        if (!checkHw()) {
            return;
        }
        hw.setScanContinuouslyEnabled(scanContinuouslyEnabled);
    }

    public boolean isScanContinuouslyEnabled() {
        if (!checkHw()) {
            return false;
        }
        return hw.isScanContinuouslyEnabled();
    }

    public int getScanY() {
        if (!checkHw()) {
            return -1;
        }
        return hw.getScanY();
    }

    public int getScanX() {
        if (!checkHw()) {
            return -1;
        }
        return hw.getScanX();
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
