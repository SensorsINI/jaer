/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.dac;

/**
 *
 * @author Minhao
 */
public class DAC_AD5391 extends DAC{
    
    public DAC_AD5391(float refMinVolts, float refMaxVolts, float vdd){
        super(32,12,refMinVolts,refMaxVolts,vdd);
    }
}
