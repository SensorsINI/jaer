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

    /**
     * PropertyChangeEvents that can be fired by implementations. The "new"
     * value is the new tweak value.
     */
    public static final String BANDWIDTH = "bandwidth", MAX_FIRING_RATE = "maxFiringRate", THRESHOLD = "threshold", ON_OFF_BALANCE = "onOffBalance";

    public void setBandwidthTweak(float val);

    public float getBandwidthTweak();

    public void setMaxFiringRateTweak(float val);

    public float getMaxFiringRateTweak();

    public void setThresholdTweak(float val);

    public float getThresholdTweak();

    public void setOnOffBalanceTweak(float val);

    public float getOnOffBalanceTweak();


}
