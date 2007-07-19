/*
 * CochleaXCorrelator.java
 *
 * Created on July 14, 2007, 4:43 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.chip.cochlea;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.event.TypedEvent;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.hardwareinterface.usb.*;
import ch.unizh.ini.caviar.util.EngineeringFormat;
import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import java.io.*;

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
        chip.getCanvas().addAnnotator(this);
        try{
            outFile = new File("plotdata.txt");
            outFileWriter = new BufferedWriter(new FileWriter(outFile));
            lpFilterITD.set3dBFreqHz(lpFilter3dBFreqHz);
            lpFilterILD.set3dBFreqHz(lpFilter3dBFreqHz);
        } catch (IOException e){
        }
    }
    
    public int ITD=0,ILD=0;
    public double azm=0;
    
    LowpassFilter lpFilterITD=new LowpassFilter();
    LowpassFilter lpFilterILD=new LowpassFilter();
    
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        
        int nleft=0, nright=0; 
        int leve=0, reve=0;
        int numchannels=chip.getSizeX();
        int maxtime=0,mintime=0;
        int lspike=0, rspike=0;
        int TLL=0, TRR=0;
        int channell=0, channelr=0;
        int t=0;
        int i=0,j=0;
        int sumind=0;
        double mavgi=0;
                
        int maxt=500;                          // maximum length of ISIHs
        double nscalef=1e-4;                    // noise scale
        double driftf=1e-5;                    // neuron drift terms
        double thresh=1;                         // firing threshold
        int af=2;                            // amplitude scaling of neural input
        int halfwindow=100;
        int a = 86;                          // the radius of the head // mm
        int c = 344000;                      // the sound speed // mm
        
        int[] laddr = null; 
        int[] raddr = null;
        int[] tl = null;
        int[] tr = null;
        int[][] ISIH2f = null;         // empty ISIH accumulator
        int[][] ISIH2g = null;
        int[] isihf = null;
        int[] isihg = null;
        int[] iterf = null;            // local counters for spike time
        int[] iterg = null;
        double[] fm = null;               // membrane potentials
        double[] gm = null;
        int[] holdf = null;            // hold states
        int[] holdg = null;
        int[] pulsef = null;            // spike states
        int[] pulseg = null;           
        int[] sp_left = null;
        int[] sp_right = null;
        int[] whole = null;
        double[] avgi = null;
                
        for(Object o:in){
            TypedEvent e=(TypedEvent)o;
            if(e.type==0) nright++; else nleft++;
        }
        leve=nleft;
        reve=nright;
        
        if(laddr==null) laddr=new int[leve];
        if(raddr==null) raddr=new int[reve];
        if(tl==null) tl=new int[leve];
        if(tr==null) tr=new int[reve];
        
        if(ISIH2f==null) ISIH2f=new int[numchannels][maxt];
        if(ISIH2g==null) ISIH2g=new int[numchannels][maxt];
        if(isihf==null) isihf=new int[maxt];
        if(isihg==null) isihg=new int[maxt];
        if(iterf==null) iterf=new int[numchannels];
        if(iterg==null) iterg=new int[numchannels];
        if(fm==null) fm=new double[numchannels];
        if(gm==null) gm=new double[numchannels];
        if(holdf==null) holdf=new int[numchannels];
        if(holdg==null) holdg=new int[numchannels];
        if(pulsef==null) pulsef=new int[numchannels];
        if(pulseg==null) pulseg=new int[numchannels];
        if(sp_left==null) sp_left=new int[numchannels];
        if(sp_right==null) sp_right=new int[numchannels];
        if(whole==null) whole=new int[maxt*2];
        if(avgi==null) avgi=new double[maxt*2];
        
        //System.out.println("leve="+leve);
        //System.out.println("reve="+reve);
        
        if(leve==0 || reve==0) return in;
        
        nleft = 0;
        nright = 0;
        for(Object o:in){
            TypedEvent e=(TypedEvent)o;
            if(e.type==0){
                raddr[nright] = e.x;
                tr[nright] = e.timestamp;
                //System.out.println("tr="+e.timestamp);
                //System.out.println("raddr="+e.x);
                nright++;
            } else {
                laddr[nleft] = e.x;
                tl[nleft] = e.timestamp;
                //System.out.println("tl="+e.timestamp);
                //System.out.println("laddr="+e.x);
                nleft++;
            }   
         }
                   
         if (tr[tr.length-1]>tl[tl.length-1])  maxtime=tr[tr.length-1];
         else maxtime=tl[tl.length-1];
        if (tr[0]<tl[0])  mintime=tr[0];
         else mintime=tl[0];
        
        //System.out.println("maxtime="+maxtime);
        //System.out.println("mintime="+mintime);
         
        for(t=mintime;t<maxtime;t++){
            
            for (i=0; i<numchannels; i++){
                sp_left[i]=0;
                sp_right[i]=0;  
            }
            TLL=tl[lspike];
            TRR=tr[rspike];
            
            if (t>=TLL){
                // if there was a spike
                channell=laddr[lspike];
                sp_left[channell]=1;       // set that channel to 1
            if (lspike<leve-1) lspike=lspike+1;
            }
            if (t>=TRR){       
               // if there was a spike
               channelr=raddr[rspike];
               sp_right[channelr]=1;       // set that channel to 1
            if (rspike<reve-1) rspike=rspike+1;
            }
           
            for (i=0;i<numchannels; i++){
                
                 if (holdf[i]==0){
                    fm[i]=fm[i]+Math.random()*nscalef+driftf+af*sp_left[i]; //update membrane
                    iterf[i]=iterf[i]+1;
                 }
                 if (fm[i]<0)  fm[i]=0;     // set minimum potential          
                 if (fm[i]>thresh){        // spike event
                    if (iterf[i]<maxt) // if within max ISIH count
                        ISIH2f[i][iterf[i]]=ISIH2f[i][iterf[i]]+1;  //add to ISIH
                    
                    pulsef[i]=1;       // set spike out
                    fm[i]=0;           //reset potential
                    iterf[i]=0;        //rest neuron counter
                    holdf[i]=1;
                    holdg[i]=0;
                 }
                // if (i==17) System.out.println("fm[17]="+fm[17]);
                               
                if  (holdg[i]==0){
                    gm[i]=gm[i]+Math.random()*nscalef+driftf+af*sp_right[i]; //update membrane
                    iterg[i]=iterg[i]+1;
                }
                if (gm[i]<0)   gm[i]=0;   // set minimum potential
                if (gm[i]>thresh){        // spike event
                    if (iterg[i]<maxt) // if within max ISIH count
                        ISIH2g[i][iterg[i]]=ISIH2g[i][iterg[i]]+1;  //add to ISIH
                
                    pulseg[i]=1;         // set spike out
                    gm[i]=0;           // reset potential
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
        
        for (j=halfwindow;j<(maxt*2-halfwindow);j++){
            for (i=(j-halfwindow); i<j+halfwindow; i++){
                avgi[j]=avgi[j]+whole[i]/(2.0*halfwindow);  // m
            }
             //System.out.println("avgi="+avgi[j]);
        }

        mavgi = maxvalue(avgi);
        j=0;
        for(i=0;i<avgi.length;i++){
            if(avgi[i]==mavgi){
                sumind = sumind+(i-maxt+1);
                j++;
            }
        }         
        ITD=sumind/j;
      
        ITD=isITDOK(ITD);
        lpFilterITD.filter(ITD,mintime);
        System.out.println("ITD="+ITD);
        
        ILD=((tl.length-tr.length)*2)*1000/(tl.length+tr.length);
        ILD = isILDOK(ILD);
        //lpFilterILD.filter(ILD,mintime);
        //System.out.println("ILD="+ILD);
        
        azm = Math.asin(ITD*c/a/2/1000.0/1000.0);
        
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
        if (ITD>ITDMax) return ITDMax;
        else {
            if (ITD<-ITDMax) return -ITDMax;
            else return ITD;
        }
    }
    
    int  isILDOK(int ILD){
        int ILDMax = 500;
        if (ILD>ILDMax) return ILDMax;
        else {
            if (ILD<-ILDMax) return -ILDMax;
            else return ILD;
            }
    }
    
    double maxvalue(double[] value){
       double mv = value[0];
       for(int i=0;i<value.length;i++){
            if (value[i]>mv) mv = value[i];
        }
        return mv;
    }
  
    int minvalue(int[] value){
       int mv = value[0];
       for(int i=0;i<value.length;i++){
            if (value[i]<mv) mv = value[i];
        }
        return mv;
    }
    
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter() {
    }
    
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
                servo=SiLabsC8051F320Factory.instance().getFirstAvailableInterface();
                if(servo==null) return;
                if(!(servo instanceof ServoInterface)) servo=null;
            }
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
            servo=null;
        }
    }
    
    @Override public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        if(!yes && servo!=null){
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
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL();
        gl.glPushMatrix();
        final GLUT glut=new GLUT();
        gl.glColor3f(1,1,1); 
        gl.glRasterPos3f(0,0,0);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("ITD(us)=%s",fmt.format(ITD)));
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  ILD(m)=%s",fmt.format(ILD)));
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  azm=%s",azm));
        gl.glPopMatrix();
    }
    
    public void setLpFilter3dBFreqHz(float lpFilter3dBFreqHz) {
        this.lpFilter3dBFreqHz = lpFilter3dBFreqHz;
        getPrefs().putFloat("CochleaCrossCorrelator.lpFilter3dBFreqHz",lpFilter3dBFreqHz);
        lpFilterITD.set3dBFreqHz(lpFilter3dBFreqHz);
        lpFilterILD.set3dBFreqHz(lpFilter3dBFreqHz);
    }
    
    
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
