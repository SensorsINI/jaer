package ch.unizh.ini.jaer.chip.util.externaladc;

import java.util.logging.Logger;

import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceProxy;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.PropertyTooltipSupport;

/**
 * A proxy to wrap around the actual hardware interface to expose the ADC controls
 * for purposes of GUI building using ParameterControlPanel.
 * It  stores
 * in preferences the state of the ADC, and calls update listener(s) when the state is changed.
 * A listener (e.g. a Chip's configuration control (biasgen)) registers itself as a listener
 * on this. In the update() method it reads the desired ADC state and sends appropriate messages
 * to the hardware.
 */
public class ADCHardwareInterfaceProxy extends HardwareInterfaceProxy implements ADCHardwareInterface, HasPropertyTooltips {

    static final Logger log = Logger.getLogger("HardwareInterfaceProxy");
    private boolean adcEnabled;
    private int trackTime,  idleTime;
    private boolean sequencingEnabled;
//    private boolean UseCalibration;
    // following define limits for slider controls that are automagically constucted by ParameterControlPanel

    private int minTrackTime = 0;
    private int maxTrackTime = 100;
    private int minIdleTime = 0;
    private int maxIdleTime = 100;
    private int minADCchannel = 0;
    private int maxADCchannel = 3;
    private int adcChannel = 0;
    
    private PropertyTooltipSupport tooltipSupport=new PropertyTooltipSupport();

    public ADCHardwareInterfaceProxy(Chip chip) {
        super(chip);
        adcChannel = getPrefs().getInt("ADCHardwareInterfaceProxy.adcChannel", 0);
        adcEnabled = getPrefs().getBoolean("ADCHardwareInterfaceProxy.adcEnabled", true);
        trackTime = getPrefs().getInt("ADCHardwareInterfaceProxy.trackTime", 50);
        idleTime = getPrefs().getInt("ADCHardwareInterfaceProxy.idleTime", 10);
        sequencingEnabled=getPrefs().getBoolean("ADCHardwareInterfaceProxy.sequencingEnabled",false);
        
        tooltipSupport.setPropertyTooltip("adcChannel", "ADC channel number, 0-based");
        tooltipSupport.setPropertyTooltip("adcEnabled", "check to enable ADC converter operation");
        tooltipSupport.setPropertyTooltip("trackTime", "ADC track time, before sample hold switch is closed to sample data, in clock cycles");
        tooltipSupport.setPropertyTooltip("idleTime", "ADC idle time between sample, in clock cycles");
        tooltipSupport.setPropertyTooltip("sequencingEnabled", "if enabled, then channels are sampled in sequence starting from channel 0 up to adcChannel. If cleared, then only the adcChannel is sampled.");
    }

    @Override
    public void setADCEnabled(boolean yes) {
        this.adcEnabled = yes;
        getPrefs().putBoolean("ADCHardwareInterfaceProxy.adcEnabled", yes);
        notifyChange(EVENT_ADC_ENABLED);
    }

    @Override
    public boolean isADCEnabled() {
        return adcEnabled;
    }

    /** Sets the time in us that track and hold should should track before closing switch to sample signal.
     * 
     * @param trackTime in us
     */
    @Override
    public void setTrackTime(int trackTime) {
        this.trackTime = trackTime;
        getPrefs().putInt("ADCHardwareInterfaceProxy.trackTime", trackTime);
        notifyChange(EVENT_TRACK_TIME);
    }

    /** Sets the time in us that ADC should idle after conversion is finished, before starting next track and hold.
     * 
     * @param idleTime in us
     */
    @Override
    public void setIdleTime(int idleTime) {
        this.idleTime = idleTime;
        getPrefs().putInt("ADCHardwareInterfaceProxy.idleTime", idleTime);
        notifyChange(EVENT_IDLE_TIME);
    }

    /** Sets either the maximum ADC channel (if sequencing) or the selected channel
     * 
     * @param channel the max (if sequencing) or selected channel. 0 based.
     */
    public void setADCChannel(int channel) {
        if(adcChannel<minADCchannel) adcChannel=minADCchannel; else if(adcChannel>maxADCchannel) adcChannel=maxADCchannel;
        this.adcChannel = channel;
        getPrefs().putInt("ADCHardwareInterfaceProxy.adcChannel", channel);
        notifyChange(EVENT_ADC_CHANNEL_MASK);
    }

    @Override
    public int getTrackTime() {
        return trackTime;
    }

    @Override
    public int getIdleTime() {
        return idleTime;
    }

    @Override
    public int getADCChannel() {
        return adcChannel;
    }

    public int getMinTrackTime() {
        return minTrackTime;
    }

    public int getMaxTrackTime() {
        return maxTrackTime;
    }

    public int getMinIdleTime() {
        return minIdleTime;
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    public int getMinADCchannel() {
        return minADCchannel;
    }

    public int getMaxADCChannel() {
        return maxADCchannel;
    }

    @Override
    public void startADC() {
        setADCEnabled(true);
    }

    @Override
    public void stopADC()  {
        setADCEnabled(false);
    }

    @Override
    public void sendADCConfiguration()  {
        notifyChange(EVENT_ADC_CHANGED);
    }

    @Override
    public String toString() {
        return "ADCHardwareInterfaceProxy{" + "adcEnabled=" + adcEnabled + ", trackTime=" + trackTime + ", idleTime=" + idleTime + ", lastChannel=" + adcChannel + '}';
    }

    /**
     * @return the sequencingEnabled
     */
    public boolean isSequencingEnabled() {
        return sequencingEnabled;
    }

    /**
     * @param sequencingEnabled the sequencingEnabled to set
     */
    public void setSequencingEnabled(boolean sequencingEnabled) {
        this.sequencingEnabled = sequencingEnabled;
        getPrefs().putBoolean("ADCHardwareInterfaceProxy.sequencingEnabled",sequencingEnabled);
        notifyChange(EVENT_SEQUENCING);
    }

    /**
     * @param minTrackTime the minTrackTime to set
     */
    public void setMinTrackTimeValue(int minTrackTime) { // named this to avoid javabeans property
        this.minTrackTime = minTrackTime;
    }

    /**
     * @param maxTrackTime the maxTrackTime to set
     */
    public void setMaxTrackTimeValue(int maxTrackTime) { // named this to avoid javabeans property
        this.maxTrackTime = maxTrackTime;
    }

    /**
     * @param minIdleTime the minIdleTime to set
     */
    public void setMinIdleTimeValue(int minIdleTime) { // named this to avoid javabeans property
        this.minIdleTime = minIdleTime;
    }

    /**
     * @param maxIdleTime the maxIdleTime to set
     */
    public void setMaxIdleTimeValue(int maxIdleTime) { // named this to avoid javabeans property
        this.maxIdleTime = maxIdleTime;
    }


    /**
     * @param maxADCchannel the maxADCchannel to set
     */
    public void setMaxADCchannelValue(int maxADCchannel) { // named this to avoid javabeans property
        this.maxADCchannel = maxADCchannel;
    }

    @Override
    public String getPropertyTooltip(String propertyName) {
        return tooltipSupport.getPropertyTooltip(propertyName);
    }
    
    
    
    
}
