/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.wowwee;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.caviar.util.filter.BandpassFilter;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
/**
 * Demonstrates Javier Movellen's social sonar ideas using the RoboQuad from WowWee and an AE Sensor.
 * The AE sensor is used to detect a response to an utterance from the RoboQuad based on simple measures of event rate.
 * 
 * @author tobi
 */
public class RoboQuadSocialSonar extends EventFilter2D {
    WowWeeRSHardwareInterface wowwee;
    Timer timer=null;
    BandpassFilter rateFilter=new BandpassFilter();
    enum OverallState {
        Sleeping, Pinging, Conversing
    };
//    enum PingState {
//        Inactive, SendingPing, WaitingForPingEnd, WaitingForResponse
//    };
    OverallState overallState=OverallState.Sleeping;
//    PingState pingState=PingState.Inactive;
    int pingDurationMs=1000, waitForResponseDurationMs=3000;
    long pingStartedTime=0;
    Random r=new Random();
    float pingProb=0.01f;
    int responseCount=0, noResponseCount=0;
    float responseRatioForConversing=.3f;
    int pingCmd=RoboQuadCommands.Single_Shot;
    int conversingCmd=RoboQuadCommands.Dizzy;

    public RoboQuadSocialSonar(AEChip chip) {
        super(chip);
        rateFilter.setTauMsLow(100);
        rateFilter.setTauMsHigh(1000);
    }
    final int PRINT_INTERVAL=100;
    int checkCount=0;

    boolean checkHardware() {
        if(wowwee==null) {
            wowwee=new WowWeeRSHardwareInterface();
        }
        try {
            if(!wowwee.isOpen()) {
                wowwee.open();
            }
            return true;
        } catch(HardwareInterfaceException e) {
            if(checkCount%PRINT_INTERVAL==0) {
                log.warning(e.toString());
            }
            return false;
        } finally {
            checkCount++;
        }
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) {
            return in;
        }
        if(in==null||in.getSize()<2) {
            return in;
        }
        rateFilter.filter(in.getEventRateHz(), in.getLastTimestamp());
//        log.info("rate="+rateFilter.getValue());
        return in;
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
        overallState=OverallState.Sleeping;
//        pingState=PingState.Inactive;
        responseCount=0;
        noResponseCount=0;
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if(yes) {
            if(timer==null) {
                timer=new Timer("SocialSonarPing");
                timer.scheduleAtFixedRate(new PingTast(), 500, 100);
            }
        } else {
            if(timer!=null) {
                timer.cancel();
            }
        }
    }

    @Override
    public void initFilter() {
        resetFilter();
    }
    class PingTast extends TimerTask {
        public void run() {
            if(checkHardware()) {
                switch(overallState) {
                    case Sleeping:
                        if(r.nextFloat()<pingProb) {
                            startPing();
                        }
                        break;
                    case Pinging:
                        if(isPingDone()) {
                            if(isResponseDetected()) {
                                responseCount++;
                            } else {
                                noResponseCount++;
                            }
                            if(getResponseFraction()>responseRatioForConversing) {
                                overallState=OverallState.Conversing;
                            }
                        }
                        break;
                    case Conversing:
                        showConversingResponse();
                        overallState=OverallState.Sleeping;

                }
            }
        }
    }

    float getResponseFraction() {
        return (float) responseCount/(responseCount+noResponseCount);
    }

    void startPing() {
        overallState=OverallState.Pinging;
        pingStartedTime=System.currentTimeMillis();
        sendCmd(pingCmd);
    }

    boolean isPingDone() {
        if(System.currentTimeMillis()-pingStartedTime>pingDurationMs) {
            return true;
        } else {
            return false;
        }
    }

    boolean isResponseDetected() {
        if(rateFilter.getValue()>100000) {
            return true;
        } else {
            return false;
        }
    }

    private void showConversingResponse() {
        sendCmd(conversingCmd);
    }

    private void sendCmd(int cmd) {
        try {
            wowwee.sendWowWeeCmd((short) cmd);
        } catch(Exception e) {
            log.warning(e.toString());
        }

    }
}
