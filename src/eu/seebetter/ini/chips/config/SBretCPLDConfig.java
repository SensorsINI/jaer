/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.config;

import ch.unizh.ini.config.fx2.PortBit;
import ch.unizh.ini.config.HasPreferences;
import ch.unizh.ini.config.cpld.CPLDShiftRegister;
import ch.unizh.ini.config.cpld.CPLDConfigValue;
import ch.unizh.ini.config.AbstractConfigValue;
import java.util.ArrayList;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.IPotGroup;
import net.sf.jaer.chip.Chip;

/**
 * Extends the base Biasgen to addConfigValue storage for AbstractConfigValues which form part of the chip configuration.
 * 
 * @author tobi
 */
public class SBretCPLDConfig extends Biasgen implements HasPreferences {

    /** Active container for CPLD configuration, which know how to format the data for the CPLD shift register.
     * 
     */
    protected CPLDShiftRegister cpldConfig = new CPLDShiftRegister();
    /** List of configuration values
     * 
     */
    protected ArrayList<AbstractConfigValue> configValues = new ArrayList<AbstractConfigValue>();
    /** List of direct port bits
     * 
     */
    protected ArrayList<PortBit> portBits = new ArrayList();
    /** List of CPLD configuration values
     * 
     */
    protected ArrayList<CPLDConfigValue> cpldConfigValues = new ArrayList();

    public SBretCPLDConfig(Chip chip) {
        super(chip);
    }

    /** Adds a value, adding it to the appropriate internal containers, and adding this as an observer of the value.
     * 
     * @param value some configuration value
     */
    public void addConfigValue(AbstractConfigValue value) {
        if (value == null) {
            return;
        }
        configValues.add(value);
        if (value instanceof CPLDConfigValue) {
            cpldConfig.add((CPLDConfigValue) value);
        } else if (value instanceof PortBit) {
            portBits.add((PortBit) value);
        }
        value.addObserver(this);
        log.info("Added " + value);
    }

    /** Clears all lists of configuration values.
     * @see AbstractConfigValue
     * 
     */
    public void clearConfigValues() {
        cpldConfig.clear();
        configValues.clear();
        portBits.clear();
        cpldConfigValues.clear();
    }

    @Override
    public void loadPreferences() {
        super.loadPreferences();
        if (configValues != null) {
            for (AbstractConfigValue v : configValues) {
                v.loadPreferences();
            }
        }
    }

    @Override
    public void storePreferences() {
        super.storePreferences();
        if (configValues != null) {
            for (AbstractConfigValue v : configValues) {
                v.storePreferences();
            }
        }
    }
}
