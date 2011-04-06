/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.util.externaladc;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Interface to external ADC on PCB.
 *
 * @author tobi
 */
public interface ADCHardwareInterface {
    public String EVENT_ADC_CHANNEL = "ADCchannel";
    public String EVENT_ADC_ENABLED = "adcEnabled";
    public String EVENT_IDLE_TIME = "IdleTime";
    public String EVENT_REF_OFF_TIME = "RefOffTime";
    public String EVENT_REF_ON_TIME = "RefOnTime";
    public String EVENT_TRACK_TIME = "TrackTime";

    /**
     * @return the ADCchannel
     */
    public byte getADCchannel();

    /**
     * @return the IdleTime
     */
    public short getIdleTime();

    /**
     * @return the RefOffTime
     */
    public short getRefOffTime();

    /**
     * @return the RefOnTime
     */
    public short getRefOnTime();

    /**
     * @return the TrackTime
     */
    public short getTrackTime();

    public boolean isADCEnabled();

    public void setADCEnabled(boolean yes) throws HardwareInterfaceException;

    public void setADCchannel(byte chan);

    public void setIdleTime(short trackTimeUs);

    public void setRefOffTime(short trackTimeUs);

    public void setRefOnTime(short trackTimeUs);

    public void setTrackTime(short trackTimeUs);

    public void startADC() throws HardwareInterfaceException;

    public void stopADC() throws HardwareInterfaceException;

    public void sendADCConfiguration() throws HardwareInterfaceException;
}
