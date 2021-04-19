/*
 * CochleaCrossCorrelator.java
 *
 * Created on July 11, 2006, 1:43 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.cochlea;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.ServoInterfaceFactory;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.filter.LowpassFilter;

import com.jogamp.opengl.util.gl2.GLUT;
import net.sf.jaer.event.BasicEvent;

/**
 * Computes cross corr between binaural cochleas
 * @author yjchung
 */
public class CochleaCrossCorrelator extends EventFilter2D implements FrameAnnotater {

	private int itdMax=getPrefs().getInt("CochleaCrossCorrelator.itdMax",500);
	private int ildMax=getPrefs().getInt("CochleaCrossCorrelator.ildMax",500);
	private int iDis=getPrefs().getInt("CochleaCrossCorrelator.iDis",20);
	private float lpFilter3dBFreqHz=getPrefs().getFloat("CochleaCrossCorrelator.lpFilter3dBFreqHz",10);

	HardwareInterface servo=null;

	private boolean servoEnabled;

	/* to log estimated ITDs */
	public File outFile = null;
	public BufferedWriter outFileWriter = null;

	/** Creates a new instance of CochleaCrossCorrelator */
	public CochleaCrossCorrelator(AEChip chip) {
		super(chip);
		lpFilterITD.set3dBFreqHz(lpFilter3dBFreqHz);
		lpFilterILD.set3dBFreqHz(lpFilter3dBFreqHz);
		try{
			outFile = new File("plotdata.txt");
			outFileWriter = new BufferedWriter(new FileWriter(outFile));
		} catch (IOException e){
		}
	}

	public int ILD=0;
	public float azm=0;

	LowpassFilter lpFilterITD=new LowpassFilter();
	LowpassFilter lpFilterILD=new LowpassFilter();
	LowpassFilter lpFilterAzm=new LowpassFilter();


	@Override
	public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
		if(!isFilterEnabled()) {
			return in;
		}
		int nleft=0,nright=0;
		int leftTap=0, rightTap=0;
		int leftTime=0,rightTime=0;
		int[][] times = null;
		int i=0;
		int ITD=0;// ILD=0;
		float azm_itd=0, azm_ild;

		if(times==null) {
			times=new int[chip.getSizeX()][chip.getSizeY()];
		}

		for(Object o:in){
			TypedEvent e=(TypedEvent)o;

			//            if(e.type==0) nright++; else nleft++;
			if(e.type==0){
				//                leftTap = e.x;
				//                leftTime = e.timestamp;
				times[e.x][0]=e.timestamp;
				ITD=e.timestamp-times[e.x][1];
				nleft++;
			} else {
				//                rightTap=e.x;
				//                rightTime = e.timestamp;
				times[e.x][1]=e.timestamp;
				ITD=times[e.x][0]-e.timestamp;
				nright++;
			}

			ILD = (nright-nleft)+65;
			ITD = ITD-70;
			ILD = isILDOK(ILD);

			//           if (isITDOK(ITD)) {
			if(isITDOK(ITD)) {
				lpFilterITD.filter(ITD,e.timestamp);
			}
			lpFilterILD.filter(ILD,e.timestamp);
			azm = -(lpFilterITD.getValue()+lpFilterILD.getValue())/(ildMax+itdMax);
			//            }
			//           else {
			//               lpFilterILD.filter(ILD,e.timestamp);
			//               azm = lpFilterILD.getValue()/1000;
			//           }
			lpFilterAzm.filter(azm,e.timestamp);

			//lpFilter.filter(ITD_avr,in.getLastTimestamp());
			//            System.out.println("lChn="+nleft+" RChn="+nright);
			//            System.out.println("lChn="+leftTap+" RChn="+rightTap);
			//            System.out.println("lTime="+leftTime+" RTime="+rightTime);
			//            System.out.println("ITD="+ITD_avr);
		}

		try{
			outFileWriter.write(ITD+" \n");
			//            outFileWriter.write(fmt.format(lpFilterITD.getValue())+" ");
		} catch (IOException e){
		}

		if(isServoEnabled()){
			checkHardware();
			try{
				ServoInterface s=(ServoInterface)servo;
				s.setServoValue(0,lpFilterAzm.getValue()+.5f);
			}catch(HardwareInterfaceException e){
				e.printStackTrace();
			}
		}
		try{
			outFileWriter.flush();
		} catch (IOException e){
		}
		return in;
	}

	boolean  isITDOK(int ITD){
		return ((ITD>-itdMax) && (ITD<itdMax));
	}

	int  isILDOK(int ILD){
		if (ILD>ildMax) {
			return ildMax;
		}
		else {
			if (ILD<-ildMax) {
				return -ildMax;
			}
			else {
				return ILD;
			}
		}
	}

	public Object getFilterState() {
		return null;
	}

	@Override
	public void resetFilter() {
	}

	@Override
	public void initFilter() {
	}

	public int getItdMax() {
		return itdMax;
	}

	public void setItdMax(int itdMax) {
		this.itdMax = itdMax;
		getPrefs().putInt("CochleaCrossCorrelator.itdMax",itdMax);
	}

	public int getIldMax() {
		return ildMax;
	}

	public void setIldMax(int ildMax) {
		this.ildMax = ildMax;
		getPrefs().putInt("CochleaCrossCorrelator.ildMax",ildMax);
	}

	public int getIDis() {
		return iDis;
	}

	public void setIDis(int iDis) {
		this.iDis = iDis;
		getPrefs().putInt("CochleaCrossCorrelator.iDis",iDis);
	}

	void checkHardware(){
		try{
			if(servo==null){
				servo=ServoInterfaceFactory.instance().getFirstAvailableInterface();
				if(servo==null) {
					return;
				}
				if(!(servo instanceof ServoInterface)) {
					servo=null;
				}
			}
		}catch(HardwareInterfaceException e){
			e.printStackTrace();
			servo=null;
		}
	}

	@Override public synchronized void setFilterEnabled(boolean yes){
		super.setFilterEnabled(yes);
		if(!yes && (servo!=null)){
			ServoInterface s=(ServoInterface)servo;
			try{
				s.disableServo(0);
				s.disableServo(1);
			}catch(HardwareInterfaceException e){
				e.printStackTrace();
			}
		}
	}

	public void annotate(float[][][] frame) {
	}

	public void annotate(Graphics2D g) {
	}


	EngineeringFormat fmt=new EngineeringFormat();

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if(!isFilterEnabled()) {
			return;
		}
		GL2 gl=drawable.getGL().getGL2();
		gl.glPushMatrix();
		final GLUT glut=new GLUT();
		gl.glColor3f(1,1,1); // must set color before raster position (raster position is like glVertex)
		gl.glRasterPos3f(0,0,0);
		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("ITD(us)=%s",fmt.format(lpFilterITD.getValue())));
		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  ILD=%s",fmt.format(lpFilterILD.getValue())));
		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  azm=%s",azm));
		gl.glPopMatrix();
	}

	public float getLpFilter3dBFreqHz() {
		return lpFilter3dBFreqHz;
	}

	public void setLpFilter3dBFreqHz(float lpFilter3dBFreqHz) {
		this.lpFilter3dBFreqHz = lpFilter3dBFreqHz;
		getPrefs().putFloat("CochleaCrossCorrelator.lpFilter3dBFreqHz",lpFilter3dBFreqHz);
		lpFilterITD.set3dBFreqHz(lpFilter3dBFreqHz);
		lpFilterILD.set3dBFreqHz(lpFilter3dBFreqHz);
		lpFilterAzm.set3dBFreqHz(lpFilter3dBFreqHz);
	}

	public boolean isServoEnabled() {
		return servoEnabled;
	}

	public void setServoEnabled(boolean servoEnabled) {
		this.servoEnabled = servoEnabled;
	}

	public LowpassFilter getLpFilterITD() {
		return lpFilterITD;
	}
}
