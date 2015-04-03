/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.wowwee;
import java.awt.Graphics2D;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.filter.BandpassFilter;
import net.sf.jaer.util.filter.LowpassFilter;
/**
 * Demonstrates Javier Movellen's social sonar ideas (simplified grossly) using the RoboQuad from WowWee and an AE Sensor.
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
	long lastFilterTime=0;
	private int computeIntervalMs=getPrefs().getInt("RoboQuadSocialSonar.computeIntervalMs", 20);
	enum OverallState {
		Sleeping, Pinging, Conversing
	};
	OverallState overallState=OverallState.Sleeping;
	private int pingDurationMs=getPrefs().getInt("RoboQuadSocialSonar.pingDurationMs", 4000);
	private int waitForResponseDurationMs=getPrefs().getInt("RoboQuadSocialSonar.waitForResponseDurationMs", 3000);
	long pingStartedTime=0;
	Random r=new Random();
	private float pingProb=getPrefs().getFloat("RoboQuadSocialSonar.pingProb", 0.03f);
	private float responseFractionThreshold=getPrefs().getFloat("RoboQuadSocialSonar", 0.3f);
	private float responseModulationThreshold=getPrefs().getFloat("RoboQuadSocialSonar.responseModulationThreshold", 50);
	int responseCount=0, noResponseCount=0;
	int pingCmd=RoboQuadCommands.Single_Shot;
	int conversingCmd=RoboQuadCommands.Dance_Demo;
	int[] pingCmds={RoboQuadCommands.Surprise, RoboQuadCommands.Burst, RoboQuadCommands.Surprise};
	private float modulationCornerFreqHz=getPrefs().getFloat("RoboQuadSocialSonar.modulationCornerFreqHz", 4);
	private float modulationPoleFreqHz=getPrefs().getFloat("RoboQuadSocialSonar.modulationPoleFreqHz", 12);
	private float modulationAveragingTauMs=getPrefs().getFloat("RoboQuadSocialSonar.modulationAveragingTauMs", 1000);
	private long lastSpeechDetectedTime=0;
	private int quietDurationForPingMs=getPrefs().getInt("RoboQuadSocialSonar.quietDurationForPingMs", 5000);
	long timeConversingStartedMs=0;
	final long conversingDurationMs=12000;
	long realtimeStart=System.currentTimeMillis();
	int nSpikes=0;

	private void computeSpeechiness(EventPacket<?> in) {
		//        // compute real average event rate based on ISIs of spikes in packet
		//        // the in.getEventRate does a poor approximation.
		//        float eventRate=in.getEventRateHz();
		//        if(in.getSize()==0){
		//            eventRate=0;
		//        }else{
		//            for(BasicEvent e:in){
		//                int isi=e.timestamp-lastTs;
		//                if(isi==0){
		//                    isi=1;
		//                }
		//                eventRate+=1f/(isi*1e-6f);
		//                lastTs=e.timestamp;
		//            }
		//            eventRate/=in.getSize();
		//        }

		nSpikes+=in.getSize();
		long now=System.currentTimeMillis();
		if((now-lastFilterTime)>computeIntervalMs) {
			if(in.getSize()>0) {
				lastTs=in.getLastTimestamp();
			}else{
				lastTs+=1000*computeIntervalMs; // update lastTs even if there are no spikes
			}
			float eventRate= (1000f*nSpikes)/(now-lastFilterTime);
			//            int realTime=(int) (now-realtimeStart)*1000; // us from ms
			rateModulationFilter.filter(eventRate, lastTs); // compute bandpass filtered event rate
			float ratePower=rateModulationFilter.getValue();
			ratePower=ratePower>0?ratePower:0;  // rectify
			speechinessFilter.filter(ratePower, lastTs); // lowpass filter
			if(printStats) {
				System.out.println(String.format("%10.0f %10.0f%10.0f %s %10.2f %s %s", eventRate, ratePower, speechinessFilter.getValue(), isSpeechDetected()?"response":"       ", getResponseFraction(), isSomeoneThere()?"someone":"no one  ", overallState.toString()));
			}
			lastFilterTime=now;
			nSpikes=0;
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
		getPrefs().putFloat("RoboQuadSocialSonar.modulationPoleFreqHz", modulationPoleFreqHz);
	}

	public float getModulationAveragingTauMs() {
		return modulationAveragingTauMs;
	}

	public void setModulationAveragingTauMs(float modulationAveragingTauMs) {
		this.modulationAveragingTauMs=modulationAveragingTauMs;
		speechinessFilter.setTauMs(modulationAveragingTauMs);
		getPrefs().putFloat("RoboQuadSocialSonar.modulationAveragingTauMs", modulationAveragingTauMs);
	}

	public boolean isPrintStats() {
		return printStats;
	}

	@Override
	public synchronized boolean isFilterEnabled() {
		realtimeStart=System.currentTimeMillis();
		return super.isFilterEnabled();
	}

	public void setPrintStats(boolean printStats) {
		this.printStats=printStats;
	}

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
			if((checkCount%PRINT_INTERVAL)==0) {
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
		@Override
		public void run() {
			{
				switch(overallState) {
					case Sleeping:
						if(!isSpeechDetected()&&(r.nextFloat()<getPingProb())) {
							startPing();
						}
						break;
					case Pinging:
						if(isPingDone()) {
							if(isSpeechDetected()) {
								responseCount++;
							} else {
								noResponseCount++;
							}
							if(isSomeoneThere()) {
								showConversingResponse();
							} else {
								overallState=OverallState.Sleeping;
							}
						}
						break;
					case Conversing:
						if((System.currentTimeMillis()-timeConversingStartedMs)>conversingDurationMs) {
							overallState=OverallState.Sleeping;
						}

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
		log.info("pinging");
		overallState=OverallState.Pinging;
		pingStartedTime=System.currentTimeMillis();
		int i=r.nextInt(pingCmds.length);
		sendCmd(pingCmds[i]);
	}

	boolean isPingDone() {
		if((System.currentTimeMillis()-pingStartedTime)>pingDurationMs) {
			return true;
		} else {
			return false;
		}
	}

	synchronized boolean isSpeechDetected() {
		if(speechinessFilter.getValue()>responseModulationThreshold) {
			lastSpeechDetectedTime=System.currentTimeMillis();
			return true;
		} else {
			return false;
		}
	}

	synchronized long getTimeSinceSpeechDetected() {
		return System.currentTimeMillis()-lastSpeechDetectedTime;
	}

	private void showConversingResponse() {
		log.info("started conversing");
		sendCmd(conversingCmd);
		overallState=OverallState.Conversing;
		timeConversingStartedMs=System.currentTimeMillis();
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

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if(!isFilterEnabled()) {
			return;
			//   if(isRelaxed) return;
		}
		GL2 gl=drawable.getGL().getGL2();


		// draw bar for speechiness signal, part in grey and part above threshold in red
		gl.glPushMatrix();
		float f=((speechinessFilter.getValue()/(2*responseModulationThreshold))); // 1/2 for threshold
		int sx=chip.getSizeX();
		if(f<=.5f) {
			gl.glColor3d(0.7, 0.7, 0.7);
			gl.glRectf(2, 2, 2+(f*sx), 3);
		} else {
			gl.glColor3d(0.7, 0.7, 0.7);
			gl.glRectf(2, 2, sx/2, 3);
			gl.glColor3d(1, 0, 0); // red
			gl.glRectf(sx/2, 2, f*sx, 3);
		}
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
		getPrefs().putInt("RoboQuadSocialSonar.pingDurationMs", pingDurationMs);
	}

	public int getWaitForResponseDurationMs() {
		return waitForResponseDurationMs;
	}

	public void setWaitForResponseDurationMs(int waitForResponseDurationMs) {
		this.waitForResponseDurationMs=waitForResponseDurationMs;
		getPrefs().putInt("RoboQuadSocialSonar.waitForResponseDurationMs", waitForResponseDurationMs);
	}

	public float getResponseFractionThreshold() {
		return responseFractionThreshold;
	}

	public void setResponseFractionThreshold(float responseFractionThreshold) {
		this.responseFractionThreshold=responseFractionThreshold;
		getPrefs().putFloat("RoboQuadSocialSonar.responseFractionThreshold", responseFractionThreshold);
	}

	public float getResponseModulationThreshold() {
		return responseModulationThreshold;
	}

	public void setResponseModulationThreshold(float responseModulationThreshold) {
		this.responseModulationThreshold=responseModulationThreshold;
		getPrefs().putFloat("RoboQuadSocialSonar.responseModulationThreshold", responseModulationThreshold);
	}

	public int getQuietDurationForPingMs() {
		return quietDurationForPingMs;
	}

	public void setQuietDurationForPingMs(int quietDurationForPingMs) {
		this.quietDurationForPingMs=quietDurationForPingMs;
		getPrefs().putInt("RoboQuadSocialSonar.quietDurationForPingMs", quietDurationForPingMs);
	}

	public int getComputeIntervalMs() {
		return computeIntervalMs;
	}

	public void setComputeIntervalMs(int computeIntervalMs) {
		this.computeIntervalMs=computeIntervalMs;
		getPrefs().putInt("RoboQuadSocialSonar.computeIntervalMs",computeIntervalMs);
	}
}
