/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.retina;

/**
 *  Specifies tweaks that can be done to the parameters of a DVS sensor, for use in defining
 *  user interfaces to control the biases.
 *
 * @author tobi
 */
public interface DVSTweaks {

    public void tweakBandwidth(float val);

    public void tweakMaximumFiringRate(float val);

    public void tweakThreshold(float val);

    public void tweakOnOffBalance(float val);


}
