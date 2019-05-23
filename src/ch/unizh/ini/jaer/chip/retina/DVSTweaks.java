/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.retina;

/**
 * Specifies tweaks that can be done to the parameters of a DVS sensor, for use
 * in defining user interfaces to control the biases.
 *
 * @author tobi
 */
public interface DVSTweaks {

    /**
     * PropertyChangeEvents that can be fired by implementations. The "new"
     * value is the new tweak value.
     */
    public static final String BANDWIDTH = "bandwidth", MAX_FIRING_RATE = "maxFiringRate", THRESHOLD = "threshold", ON_OFF_BALANCE = "onOffBalance";
    
    /** Remote control command */
    public static final String CMD_BANDWIDTH_TWEAK="setBandwidthTweak", CMD_THRESHOLD_TWEAK="setThresholdTweak", CMD_ONOFFBALANCE_TWEAK="setOnOffBalanceTweak", CMD_MAXFIRINGRATE_TWEAK="setMaxFiringRateTweak", CMD_GETONTHRESHOLDLOGE="getOnThresholdLogE", CMD_GETOFFTHRSHOLDLOGE="getOffThresholdLogE";

    /**
     * Tweaks front end bandwidth, larger is higher bandwidth.
     *
     * @param val -1 to 1 range
     */
    public void setBandwidthTweak(float val);

    public float getBandwidthTweak();

    /**
     * Tweaks max firing rate (refractory period), larger is higher firing rate.
     *
     * @param val -1 to 1 range
     */
    public void setMaxFiringRateTweak(float val);

    public float getMaxFiringRateTweak();

    /**
     * Tweaks threshold, larger is higher threshold.
     *
     * @param val -1 to 1 range
     */
    public void setThresholdTweak(float val);

    public float getThresholdTweak();

    /**
     * Tweaks on off balance, larger is more ON events.
     *
     * @param val -1 to 1 range
     */
    public void setOnOffBalanceTweak(float val);

    public float getOnOffBalanceTweak();

    /**
     * Returns estimated ON event threshold in log base e units.
     * <p>
     * Theory is based on paper
     * <a href="https://ieeexplore.ieee.org/document/7962235">Temperature and
     * Parasitic Photocurrent Effects in Dynamic Vision Sensors, Y Nozaki, T
     * Delbruck. IEEE Trans. on Electron Devices, 2018</a>.
     *
     * @return ON threshold (positive value)
     */
    public float getOnThresholdLogE();

    /**
     * Returns estimated OFF event threshold in log base e units
     * <p>
     * Theory is based on paper
     * <a href="https://ieeexplore.ieee.org/document/7962235">Temperature and
     * Parasitic Photocurrent Effects in Dynamic Vision Sensors, Y Nozaki, T
     * Delbruck. IEEE Trans. on Electron Devices, 2018</a>.
     *
     * @return OFF threshold (negative value)
     */
    public float getOffThresholdLogE();

}
