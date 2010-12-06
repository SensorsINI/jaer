/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.retina;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;

/**
 * Periodically resends bias values for debugging bias generator.
 * @author tobi
 */
public class BiasKicker extends EventFilter2D {

    public static String getDescription(){ return "Sends bias values periodically to debug bias generator";}
    
    private int sendDelayMs = 1000;
    Timer timer = new Timer("BadRetKickerMonitor");
    KickTask kickTask=null;
    long lastEventSysTime = System.currentTimeMillis();

    public BiasKicker(AEChip chip) {
        super(chip);
        setPropertyTooltip("sendDelayMs", "how long to wait in ms between resending biases");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if(yes){
            if(kickTask!=null) kickTask.cancel();
            timer.schedule(kickTask=new KickTask(), sendDelayMs, sendDelayMs);
        }else{
            if(kickTask!=null) kickTask.cancel();
        }

    }

    /**
     * @return the sendDelayMs
     */
    public int getSendDelayMs() {
        return sendDelayMs;
    }

    /**
     * @param sendDelayMs the sendDelayMs to set
     */
    public void setSendDelayMs(int sendDelayMs) {
        if(sendDelayMs<1)sendDelayMs=1;  // bound at 1 since Timer throws Exception otherwise
        this.sendDelayMs = sendDelayMs;
    }

    private class KickTask extends TimerTask {

        @Override
        public void run() {
                if (chip != null && chip.getHardwareInterface() != null && chip.getHardwareInterface() instanceof BiasgenHardwareInterface) {
                    try {
                        ((BiasgenHardwareInterface) chip.getHardwareInterface()).sendConfiguration(chip.getBiasgen());
                    } catch (HardwareInterfaceException ex) {
                        log.warning("caught "+ex+" when trying to send biases");
                    }
                }
            }
    }
}
