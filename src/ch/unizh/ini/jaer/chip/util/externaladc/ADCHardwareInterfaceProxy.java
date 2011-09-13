package ch.unizh.ini.jaer.chip.util.externaladc;

import java.util.logging.Logger;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceProxy;

/**
 * A proxy to wrap around the actual hardware interface to expose the ADC controls
 * for purposes of GUI building using ParameterControlPanel.
 * It  stores
 * in preferences the state of the ADC, and calls update listener(s) when the state is changed.
 * A listener (e.g. a Chip's configuration control (biasgen)) registers itself as a listener
 * on this. In the update() method it reads the desired ADC state and sends appropriate messages
 * to the hardware.
 */
public class ADCHardwareInterfaceProxy extends HardwareInterfaceProxy implements ADCHardwareInterface {

    static final Logger log = Logger.getLogger("HardwareInterfaceProxy");
    private boolean adcEnabled;
    private int trackTime, refOnTime, refOffTime, idleTime;
//    private boolean UseCalibration;
    // following define limits for slider controls that are automagically constucted by ParameterControlPanel
    private final int minRefOffTime = 0;
    private final int maxRefOffTime = 100;
    private final int minRefOnTime = 1;
    private final int maxRefOnTime = 100;
    private final int minTrackTime = 0;
    private final int maxTrackTime = 100;
    private final int minIdleTime = 0;
    private final int maxIdleTime = 100;
    private final int minADCchannel = 0;
    private final int maxADCchannel = 3;
    private int adcChannelMask = 1;

    public ADCHardwareInterfaceProxy(Chip chip) {
        super(chip);
        adcChannelMask = getPrefs().getInt("ADCHardwareInterfaceProxy.ADCchannelMask", 3);
        adcEnabled = getPrefs().getBoolean("ADCHardwareInterfaceProxy.adcEnabled", true);

//        UseCalibration = getPrefs().getBoolean("ADCHardwareInterfaceProxy.UseCalibration", false);
        adcEnabled = getPrefs().getBoolean("ADCHardwareInterfaceProxy.adcEnabled", true);
        trackTime = getPrefs().getInt("ADCHardwareInterfaceProxy.TrackTime", 50);
        refOnTime = getPrefs().getInt("ADCHardwareInterfaceProxy.RefOnTime", 20);
        refOffTime = getPrefs().getInt("ADCHardwareInterfaceProxy.RefOffTime", 20);
        idleTime = getPrefs().getInt("ADCHardwareInterfaceProxy.IdleTime", 10);
    }

    @Override
    public void setADCEnabled(boolean yes) throws HardwareInterfaceException {
        this.adcEnabled = yes;
        getPrefs().putBoolean("ADCHardwareInterfaceProxy.adcEnabled", yes);
        notifyChange(EVENT_ADC_ENABLED);
    }

    @Override
    public boolean isADCEnabled() {
        return adcEnabled;
    }

    @Override
    public void setTrackTime(int trackTime) {
        this.trackTime = trackTime;
        getPrefs().putInt("ADCHardwareInterfaceProxy.trackTime", trackTime);
        notifyChange(EVENT_TRACK_TIME);
    }

    @Override
    public void setIdleTime(int idleTime) {
        this.idleTime = idleTime;
        getPrefs().putInt("ADCHardwareInterfaceProxy.idleTime", idleTime);
        notifyChange(EVENT_IDLE_TIME);
    }

    public void setADCChannelMask(int adcChannelMask) {
        this.adcChannelMask = adcChannelMask;
        getPrefs().putInt("ADCHardwareInterfaceProxy.adcChannelMask", adcChannelMask);
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
    public int getADCChannelMask() {
        return adcChannelMask;
    }

    public int getMinRefOnTime() {
        return minRefOnTime;
    }

    public int getMaxRefOnTime() {
        return maxRefOnTime;
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

    public int getMaxADCchannel() {
        return maxADCchannel;
    }

    public int getMinRefOffTime() {
        return minRefOffTime;
    }

    public int getMaxRefOffTime() {
        return maxRefOffTime;
    }

    @Override
    public void startADC() throws HardwareInterfaceException {
        setADCEnabled(true);
    }

    @Override
    public void stopADC() throws HardwareInterfaceException {
        setADCEnabled(false);
    }

    @Override
    public void sendADCConfiguration() throws HardwareInterfaceException {
        notifyChange(EVENT_ADC_CHANGED);
    }

    @Override
    public String toString() {
        return "ADCHardwareInterfaceProxy{" + "adcEnabled=" + adcEnabled + ", trackTime=" + trackTime + ", refOnTime=" + refOnTime + ", refOffTime=" + refOffTime + ", idleTime=" + idleTime + ", adcChannelMask=" + adcChannelMask + '}';
    }
    
    
}
