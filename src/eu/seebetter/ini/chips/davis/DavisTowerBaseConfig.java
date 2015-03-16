/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import net.sf.jaer.chip.Chip;

/**
 * Base configuration for tower wafer Davis chips that use the Tower wafer bias generator
 * @author tobi
 */
public class DavisTowerBaseConfig extends DavisConfig{

    public DavisTowerBaseConfig(Chip chip) {
        super(chip);
    }
    
}
