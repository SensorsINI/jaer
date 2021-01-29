/*
 * CochleaXCorrelator.java
 *
 * Created on July 14, 2007, 4:43 PM
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

import com.jogamp.opengl.util.gl2.GLUT;
import net.sf.jaer.event.BasicEvent;

/**
 * Computes cross corr between binaural cochleas
 * This is a JAVA version of jtapson's MATLAB code iatdout.m
 */
public class CochleaXCorrelator extends EventFilter2D implements FrameAnnotater {

	private int itdMax=getPrefs().getInt("CochleaCrossCorrelator.itdMax",500); //us
	private int ildMax=getPrefs().getInt("CochleaCrossCorrelator.ildMax",500); //m
	private int iDis=getPrefs().getInt("CochleaCrossCorrelator.iDis",20);
	private float lpFilter3dBFreqHz=getPrefs().getFloat("CochleaCrossCorrelator.lpFilter3dBFreqHz",10);

	HardwareInterface servo=null;

	private boolean servoEnabled;

	/* to log estimated ITDs */
	public File outFile = null;
	public BufferedWriter outFileWriter = null;

	/**
	 * Creates a new instance of CochleaXCorrelator
	 */
	public CochleaXCorrelator(AEChip chip) {
		super(chip);
		try{
			outFile = new File("plotdata.txt");
			outFileWriter = new BufferedWriter(new FileWriter(outFile));
			//lpFilterITD.set3dBFreqHz(lpFilter3dBFreqHz);
			//lpFilterILD.set3dBFreqHz(lpFilter3dBFreqHz);
		} catch (IOException e){
		}

		initialization();
	}

	public int ITD=0,ILD=0;
	public double azm=0;

	private int maxt = 500;   // maximum length of ISIHs
	private int numchannels = chip.getSizeX();

	private int[][] ISIH2f = null;         // empty ISIH accumulator
	private int[][] ISIH2g = null;
	private int[] isihf = null;
	private int[] isihg = null;
	private int[] iterf = null;            // local counters for spike time
	private int[] iterg = null;
	private int[] holdf = null;            // hold states
	private int[] holdg = null;
	private int[] whole = null;
	private double[] avgi = null;
	private int[] laddr = null;
	private int[] raddr = null;
	private int[] tl = null;
	private int[] tr = null;

	private int leve=0, reve=0;
	private int maxtime=0,mintime=0;
	private int lspike=0, rspike=0;
	private int TLL=0, TRR=0;
	private int channell=-1, channelr=-1;
	private int t=0;
	private int i=0,j=0;
	private int sumind=0;
	private double mavgi=0;

	//LowpassFilter lpFilterITD=new LowpassFilter();
	//LowpassFilter lpFilterILD=new LowpassFilter();


	@Override
	public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
		if(!isFilterEnabled()) {
			return in;
		}

		leve=0; reve=0;
		maxtime=0; mintime=0;
		lspike=0; rspike=0;
		TLL=0; TRR=0;
		channell=-1; channelr=-1;
		t=0;
		i=0; j=0;
		sumind=0;
		mavgi=0;

		maxt = 500;   // maximum length of ISIHs
		numchannels = chip.getSizeX();

		//double nscalef=1e-4;                    // noise scale
		//double driftf=1e-5;                    // neuron drift terms
		//double thresh=1;                         // firing threshold
		//int af=2;                            // amplitude scaling of neural input
		//int halfwindow=100;
		//int a = 86;                          // the radius of the head // mm
		//int c = 344000;                      // the sound speed // mm

		for (j=0;  j<maxt; j++) {
			isihf[j]=0;
			isihg[j]=0;
			avgi[j]=0;
			avgi[maxt+j]=0;
			for (i=0; i<numchannels; i++){
				ISIH2f[i][j]=0;
				ISIH2g[i][j]=0;
			}
		}

		for(i=0;i<numchannels;i++){
			iterf[i]=0;
			iterg[i]=0;
			holdf[i]=0;
			holdg[i]=0;
		}


		for(Object o:in){
			TypedEvent e=(TypedEvent)o;
			if(e.type==0) {
				raddr[reve] = e.x;
				tr[reve] = e.timestamp;
				//System.out.println("tr="+e.timestamp);
				//System.out.println("raddr="+e.x);
				reve++;
			}
			else{
				laddr[leve] = e.x;
				tl[leve] = e.timestamp;
				//System.out.println("tl="+e.timestamp);
				//System.out.println("laddr="+e.x);
				leve++;
			}
		}

		//System.out.println("leve="+leve);
		//System.out.println("reve="+reve);

		if((leve==0) || (reve==0)) {
			return in;
		}

		if (tr[reve-1]>tl[leve-1]) {
			maxtime=tr[reve-1];
		}
		else {
			maxtime=tl[leve-1];
		}
		if (tr[0]<tl[0]) {
			mintime=tr[0];
		}
		else {
			mintime=tl[0];
		}

		//System.out.println("maxtime="+maxtime);
		//System.out.println("mintime="+mintime);

		for(t=mintime;t<(mintime+10000);t++){

			TLL=tl[lspike];
			TRR=tr[rspike];

			if (t>=TLL){
				// if there was a spike
				channell=laddr[lspike];
				if (lspike<(leve-1)) {
					lspike=lspike+1;
				}
			}
			if (t>=TRR){
				// if there was a spike
				channelr=raddr[rspike];
				if (rspike<(reve-1)) {
					rspike=rspike+1;
				}
			}

			for (i=0;i<numchannels; i++){

				if (holdf[i]==0) {
					iterf[i]=iterf[i]+1;
				}
				if ((t>=TLL) && (iterf[i] < maxt) && (i==channell) ){       // spike event
					ISIH2f[i][iterf[i]]=ISIH2f[i][iterf[i]]+1;  //add to ISIH
					iterf[i]=0;        //rest neuron counter
					holdf[i]=1;
					holdg[i]=0;
				}
				//if (i==17) System.out.println("fm[17]="+fm[17]);

				if  (holdg[i]==0) {
					iterg[i]=iterg[i]+1;
				}
				if ((t>=TRR) && (iterg[i] < maxt)  && (i==channelr)){        // spike event
					ISIH2g[i][iterg[i]]=ISIH2g[i][iterg[i]]+1;  //add to ISIH
					iterg[i]=0;        // rest neuron counter
					holdf[i]=0;
					holdg[i]=1;
				}
			}
		}


		for (j=0;  j<maxt; j++) {
			for (i=5; i<26;i++){
				isihf[j]=isihf[j]+ISIH2f[i][j];
				isihg[j]=isihg[j]+ISIH2g[i][j];
			}
		}


		for (j=0; j<maxt; j++) {
			whole[j]= isihf[maxt-1-j];
			whole[maxt+j] = isihg[j];
		}

		for (j=100;j<((maxt*2)-100);j++){
			for (i=(j-100); i<(j+100); i++){
				avgi[j]=avgi[j]+(whole[i]/(2.0*100));  // m
			}
			//System.out.println("avgi="+avgi[j]);
		}

		mavgi = maxvalue(avgi);
		j=0;
		for(i=0;i<avgi.length;i++){
			if(avgi[i]==mavgi){
				sumind = sumind+((i-maxt)+1);
				j++;
			}
		}
		ITD=sumind/j;

		ITD=isITDOK(ITD);
		//lpFilterITD.filter(ITD,mintime);
		log.fine("ITD="+ITD);

		ILD=(((leve-reve)*2)*1000)/(leve+reve);
		ILD = isILDOK(ILD);
		//lpFilterILD.filter(ILD,mintime);
		//System.out.println("ILD="+ILD);

		azm = Math.asin((ITD*344000)/86/2/1000.0/1000.0);

		try{
			outFileWriter.write(azm+" ");
		} catch (IOException e){
		}

		if(isServoEnabled()){
			checkHardware();
			try{
				ServoInterface s=(ServoInterface)servo;
				s.setServoValue(0,(float)azm+.5f);
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

	int  isITDOK(int ITD){
		int ITDMax = 500;
		if (ITD>ITDMax) {
			return ITDMax;
		}
		else {
			if (ITD<-ITDMax) {
				return -ITDMax;
			}
			else {
				return ITD;
			}
		}
	}

	int  isILDOK(int ILD){
		int ILDMax = 500;
		if (ILD>ILDMax) {
			return ILDMax;
		}
		else {
			if (ILD<-ILDMax) {
				return -ILDMax;
			}
			else {
				return ILD;
			}
		}
	}

	double maxvalue(double[] value){
		double mv = value[0];
		for (double element : value) {
			if (element>mv) {
				mv = element;
			}
		}
		return mv;
	}

	int minvalue(int[] value){
		int mv = value[0];
		for (int element : value) {
			if (element<mv) {
				mv = element;
			}
		}
		return mv;
	}

	void initialization(){
		maxt = 500;   // maximum length of ISIHs
		numchannels = 32;//chip.getSizeX();

		if(ISIH2f==null) {
			ISIH2f=new int[numchannels][maxt];
		}
		if(ISIH2g==null) {
			ISIH2g=new int[numchannels][maxt];
		}
		if(isihf==null) {
			isihf=new int[maxt];
		}
		if(isihg==null) {
			isihg=new int[maxt];
		}
		if(iterf==null) {
			iterf=new int[numchannels];
		}
		if(iterg==null) {
			iterg=new int[numchannels];
		}
		if(holdf==null) {
			holdf=new int[numchannels];
		}
		if(holdg==null) {
			holdg=new int[numchannels];
		}
		if(whole==null) {
			whole=new int[maxt*2];
		}
		if(avgi==null) {
			avgi=new double[maxt*2];
		}
		if(laddr==null) {
			laddr=new int[maxt];
		}
		if(raddr==null) {
			raddr=new int[maxt];
		}
		if(tl==null) {
			tl=new int[maxt];
		}
		if(tr==null) {
			tr=new int[maxt];
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
		gl.glColor3f(1,1,1);
		gl.glRasterPos3f(0,0,0);
		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("ITD(us)=%s",fmt.format(ITD)));
		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  ILD(m)=%s",fmt.format(ILD)));
		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  azm=%s",azm));
		gl.glPopMatrix();
	}

	/*
    public void setLpFilter3dBFreqHz(float lpFilter3dBFreqHz) {
        this.lpFilter3dBFreqHz = lpFilter3dBFreqHz;
        getPrefs().putFloat("CochleaCrossCorrelator.lpFilter3dBFreqHz",lpFilter3dBFreqHz);
        lpFilterITD.set3dBFreqHz(lpFilter3dBFreqHz);
        lpFilterILD.set3dBFreqHz(lpFilter3dBFreqHz);
     }
	 */


	public boolean isServoEnabled() {
		return servoEnabled;
	}

	public void setServoEnabled(boolean servoEnabled) {
		this.servoEnabled = servoEnabled;
	}

	public int getITD() {
		return ITD;
	}
}
