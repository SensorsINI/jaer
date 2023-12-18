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
    
    /** The default thermal voltage */
    public static final float U_T_THERMAL_VOLTAGE_ROOM_TEMPERATURE=25e-3f;

    /**
     * Tweaks front end bandwidth, larger is higher bandwidth.
     * 
     * Adjust the source follower bandwidth by changing PRSf bias current. 
     * 
     * <p> For minimum shot noise under low illumination, the photoreceptor bias should be large and 
     * the bandwidth should be limited by the source follower buffer. 
     * See <a href="https://arxiv.org/abs/2304.04019">Optimal biasing and physical limits of DVS event noise</a>. 
     *
     * @param val -1 to 1 range
     */
    public void setBandwidthTweak(float val);

    public float getBandwidthTweak();
    
    /** Returns the estimated DVS photoreceptor cutoff frequency (bandwidth).
     * It is computed based on assumption that source follower limits the bandwidth, 
     * not the photoreceptor limited by its bias or low photocurrent.
     * By default the computation is based on BW 
     * like f3dB=(1/2pi) g/C=(1/2pi) Ib/UT/C in Hz where Ib is bias current, 
     * UT is thermal voltage, and C is load capacitance, and assuming SF is biased in subthreshold.
     * 
     * @return f3dB bandwidth in Hz
     */
    public float getPhotoreceptorSourceFollowerBandwidthHz();

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
    
    /** Returns estimated refractory period in s, computed from estimated capacitance and refractory current, assuming some voltage range.
     * 
     * @return refractory period in seconds
     */
    public float getRefractoryPeriodS();

}
