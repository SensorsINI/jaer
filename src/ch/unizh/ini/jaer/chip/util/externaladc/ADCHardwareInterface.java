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
    public static final String EVENT_ADC_CHANGED="adcChanged";
    public static final String EVENT_ADC_CHANNEL_MASK = "adcChannelMask";
    public static final String EVENT_ADC_ENABLED = "adcEnabled";
    public static final String EVENT_IDLE_TIME = "idleTime";
    public static final String EVENT_TRACK_TIME = "trackTime";
    public static final String EVENT_SEQUENCING = "sequencingEnabled"; // method not yet in this interface
    

    /**
     * @return the ADCchannel
     */
    public int getADCChannel();

    /**
     * @return the IdleTime
     */
    public int getIdleTime();


    /**
     * @return the TrackTime
     */
    public int getTrackTime();

    public boolean isADCEnabled();

    public void setADCEnabled(boolean yes) throws HardwareInterfaceException ;

    public void setADCChannel(int chan);

    public void setIdleTime(int trackTimeUs);

  
    public void setTrackTime(int trackTimeUs);

    public void startADC() throws HardwareInterfaceException;

    public void stopADC()  throws HardwareInterfaceException;

    public void sendADCConfiguration() throws HardwareInterfaceException ;
    
//    public boolean isSequencingEnabled(); // not in general used
//    
//    public void setSequencingEnabled(boolean yes);
}
