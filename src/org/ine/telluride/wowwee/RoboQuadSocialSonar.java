/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.wowwee;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.caviar.util.filter.BandpassFilter;
import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
/**
 * Demonstrates Javier Movellen's social sonar ideas using the RoboQuad from WowWee and an AE Sensor.
 * The AE sensor is used to detect a response to an utterance from the RoboQuad based on simple measures of event rate.
 * 
 * @author tobi
 */
public class RoboQuadSocialSonar extends EventFilter2D implements FrameAnnotater {
    WowWeeRSHardwareInterface wowwee;
    Timer timer=null;
    BandpassFilter rateModulationFilter=new BandpassFilter();
    LowpassFilter speechinessFilter=new LowpassFilter();
    private boolean printStats=false;
    int lastTs=0;
    
    private void computeSpeechiness(EventPacket<?> in) {
        float eventRate=in.getEventRateHz();
        int time=in.getSize()>0?in.getLastTimestamp():lastTs;
        lastTs=time;
        rateModulationFilter.filter(eventRate, time);
        float ratePower=rateModulationFilter.getValue();
        ratePower=ratePower>0?ratePower:0;
        speechinessFilter.filter(ratePower, in.getLastTimestamp());
        if(printStats){
            System.out.println(String.format("%10.0f %10.0f%10.0f %s %s",in.getEventRateHz(),ratePower, speechinessFilter.getValue(),isResponseDetected()?"response":"       ",isSomeoneThere()?"someone":"no one  "));
        }

    }

    public float getModulationCornerFreqHz() {
        return modulationCornerFreqHz;
    }

    public void setModulationCornerFreqHz(float modulationCornerFreqHz) {
        this.modulationCornerFreqHz=modulationCornerFreqHz;
        rateModulationFilter.set3dBCornerFrequencyHz(modulationCornerFreqHz);
        getPrefs().putFloat("RoboQuadSocialSonar.modulationCornerFreqHz", modulationCornerFreqHz);
    }

    public float getModulationPoleFreqHz() {
        return modulationPoleFreqHz;
    }

    public void setModulationPoleFreqHz(float modulationPoleFreqHz) {
        this.modulationPoleFreqHz=modulationPoleFreqHz;
        rateModulationFilter.set3dBPoleFrequencyHz(modulationPoleFreqHz);
        getPrefs().putFloat("RoboQuadSocialSonar.modulationPoleFreqHz",modulationPoleFreqHz);
    }

    public float getModulationAveragingTauMs() {
        return modulationAveragingTauMs;
    }

    public void setModulationAveragingTauMs(float modulationAveragingTauMs) {
        this.modulationAveragingTauMs=modulationAveragingTauMs;
        speechinessFilter.setTauMs(modulationAveragingTauMs);
        getPrefs().putFloat("RoboQuadSocialSonar.modulationAveragingTauMs",modulationAveragingTauMs);
    }

    public boolean isPrintStats() {
        return printStats;
    }

    public void setPrintStats(boolean printStats) {
        this.printStats=printStats;
    }
    enum OverallState {
        Sleeping, Pinging, Conversing
    };
//    enum PingState {
//        Inactive, SendingPing, WaitingForPingEnd, WaitingForResponse
//    };
    OverallState overallState=OverallState.Sleeping;
    private int pingDurationMs=getPrefs().getInt("RoboQuadSocialSonar.pingDurationMs", 1000);
    private int waitForResponseDurationMs=getPrefs().getInt("RoboQuadSocialSonar.waitForResponseDurationMs",3000);
    long pingStartedTime=0;
    Random r=new Random();
    private float pingProb=getPrefs().getFloat("RoboQuadSocialSonar.pingProb",0.03f);
    private float responseFractionThreshold=getPrefs().getFloat("RoboQuadSocialSonar",0.3f);
    private float responseModulationThreshold=getPrefs().getFloat("RoboQuadSocialSonar.responseModulationThreshold",50);
    int responseCount=0, noResponseCount=0;
    int pingCmd=RoboQuadCommands.Single_Shot;
    int conversingCmd=RoboQuadCommands.Dance_Demo;
    int[] pingCmds={RoboQuadCommands.Roar, RoboQuadCommands.Single_Shot, RoboQuadCommands.Surprise, RoboQuadCommands.Twitch, RoboQuadCommands.Wave};
    private float modulationCornerFreqHz=getPrefs().getFloat("RoboQuadSocialSonar.modulationCornerFreqHz",4);
    private float modulationPoleFreqHz=getPrefs().getFloat("RoboQuadSocialSonar.modulationPoleFreqHz",12);
    private float modulationAveragingTauMs=getPrefs().getFloat("RoboQuadSocialSonar.modulationAveragingTauMs",1000);

    public RoboQuadSocialSonar(AEChip chip) {
        super(chip);
        initFilter();
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
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) {
            return in;
        }
        computeSpeechiness(in);
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
        // need to init taus for filters, since just setting parameters doesn't pass to filters
         speechinessFilter.setTauMs(modulationAveragingTauMs);
       rateModulationFilter.set3dBCornerFrequencyHz(modulationCornerFreqHz);
        rateModulationFilter.set3dBPoleFrequencyHz(modulationPoleFreqHz);
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
            {
                switch(overallState) {
                    case Sleeping:
                        if(r.nextFloat()<getPingProb()) {
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
                            if(isSomeoneThere()) {
                                overallState=OverallState.Conversing;
                            } else {
                                overallState=OverallState.Sleeping;
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

    private boolean isSomeoneThere() {
        return getResponseFraction()>responseFractionThreshold;
    }

    float getResponseFraction() {
        return (float) responseCount/(responseCount+noResponseCount);
    }
    int lastPingCmd=0;

    void startPing() {
        overallState=OverallState.Pinging;
        pingStartedTime=System.currentTimeMillis();
        int i=r.nextInt(pingCmds.length);
        sendCmd(pingCmds[i]);
    }

    boolean isPingDone() {
        if(System.currentTimeMillis()-pingStartedTime>pingDurationMs) {
            return true;
        } else {
            return false;
        }
    }

    synchronized boolean isResponseDetected() {
        if(speechinessFilter.getValue()>responseModulationThreshold) {
            return true;
        } else {
            return false;
        }
    }

    private void showConversingResponse() {
        sendCmd(conversingCmd);
    }

    private void sendCmd(int cmd) {
        if(checkHardware()) {
            try {
                wowwee.sendWowWeeCmd((short) cmd);
            } catch(Exception e) {
                log.warning(e.toString());
            }
        }
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }
    private GLU glu=new GLU();
    ;

    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) {
            return;
        //   if(isRelaxed) return;
        }
        GL gl=drawable.getGL();

        gl.glColor3d(0.8, 0.8, 0.8);

        gl.glPushMatrix();
        float f=((speechinessFilter.getValue()/(2*responseModulationThreshold)))*(chip.getSizeX());
        gl.glRectf(2, 2, 2+f, 3);
        gl.glPopMatrix();

        // show state of Goalie (arm shows its own state)
//        gl.glColor3d(1, 1, 1);
//       gl.glPushMatrix();
//        int font=GLUT.BITMAP_HELVETICA_12;
//        gl.glRasterPos3f(1, 1, 0);
        // annotate the cluster with the arm state, e.g. relaxed or learning
//        String stats=String.format("%s rateFilter=%.2f speechinessFilter=%.2f isSomeoneThere=%s responseFraction=%.1f",
//                overallState.toString(),rateModulationFilter.getValue(),speechinessFilter.getValue(),isSomeoneThere(),getResponseFraction());
//        chip.getCanvas().getGlut().glutBitmapString(font, stats);
//        gl.glPopMatrix();
    }

    public float getPingProb() {
        return pingProb;
    }

    public void setPingProb(float pingProb) {
        this.pingProb=pingProb;
        getPrefs().putFloat("RoboQuadSocialSonar.pingProb", pingProb);
    }

    public//    PingState pingState=PingState.Inactive;
            int getPingDurationMs() {
        return pingDurationMs;
    }

    public void setPingDurationMs(int pingDurationMs) {
        this.pingDurationMs=pingDurationMs;
        getPrefs().putInt("RoboQuadSocialSonar.pingDurationMs",pingDurationMs);
    }

    public int getWaitForResponseDurationMs() {
        return waitForResponseDurationMs;
    }

    public void setWaitForResponseDurationMs(int waitForResponseDurationMs) {
        this.waitForResponseDurationMs=waitForResponseDurationMs;
        getPrefs().putInt("RoboQuadSocialSonar.waitForResponseDurationMs",waitForResponseDurationMs);
    }

    public float getResponseFractionThreshold() {
        return responseFractionThreshold;
    }

    public void setResponseFractionThreshold(float responseFractionThreshold) {
        this.responseFractionThreshold=responseFractionThreshold;
        getPrefs().putFloat("RoboQuadSocialSonar.responseFractionThreshold",responseFractionThreshold);
    }

    public float getResponseModulationThreshold() {
        return responseModulationThreshold;
    }

    public void setResponseModulationThreshold(float responseModulationThreshold) {
        this.responseModulationThreshold=responseModulationThreshold;
        getPrefs().putFloat("RoboQuadSocialSonar.responseModulationThreshold", responseModulationThreshold);
    }
}
