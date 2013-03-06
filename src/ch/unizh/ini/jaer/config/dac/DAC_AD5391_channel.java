/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.dac;

import net.sf.jaer.chip.Chip;

/**
 *
 * @author Minhao
 */
public class DAC_AD5391_channel extends DACchannel{
    
    public DAC_AD5391_channel(Chip chip, String name, DAC dac, int channel, int bitValue, int displayPosition, String tooltipString){
        super(chip, name, dac, channel, bitValue, displayPosition, tooltipString);
    }

}
