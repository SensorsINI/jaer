/*
 * Tmpdiff128RateController.java
 *
 * Created on January 9, 2006, 10:41 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.chip.retina;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.util.*;
import ch.unizh.ini.caviar.util.filter.*;
import com.sun.opengl.util.*;
import java.awt.Graphics2D;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.prefs.*;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;

/**
 * Controls the rate of events from the retina by controlling retina biases.
 *
 * @author tobi
 */
public class Tmpdiff128RateController extends EventFilter2D implements FrameAnnotater {
    protected Preferences prefs=Preferences.userNodeForPackage(this.getClass());
    
    protected int rateHigh=prefs.getInt("Tmpdiff128RateController.rateHigh",400);
//    private int rateMid=prefs.getInt("Tmpdiff128RateController.rateMid",300);
    private int rateLow=prefs.getInt("Tmpdiff128RateController.rateLow",100);
//    private int rateHysteresis=prefs.getInt("Tmpdiff128RateController.rateHysteresis",50);
    private float hysteresisFactor=prefs.getFloat("Tmpdiff128RateController.hysteresisFactor",1.3f);
    private float rateFilter3dBFreqHz=prefs.getFloat("Tmpdiff128RateController.rateFilter3dBFreqHz",1);
    enum State {
        INITIAL, LOW, MEDIUM, HIGH;
        private long timeChanged=0;
        long getTimeChanged(){ return timeChanged;}
        void setTimeChanged(long t){ timeChanged=t;}
    };
    State state=State.INITIAL, lastState=State.INITIAL;
    float lastrate=0;
    LowpassFilter filter=new LowpassFilter();
    Writer logWriter;
    private boolean writeLogEnabled=false;
    int lastt=0;
    
    /**
     * Creates a new instance of Tmpdiff128RateController
     */
    public Tmpdiff128RateController(AEChip chip) {
        super(chip);
        if(!(chip instanceof Tmpdiff128)) {
            log.warning(chip+" is not of type Tmpdiff128");
        }
        filter.set3dBFreqHz(rateFilter3dBFreqHz);
        chip.getCanvas().addAnnotator(this);
    }
    
    public Object getFilterState() {
        return null;
    }
    
    synchronized public void resetFilter() {
        if(chip.getHardwareInterface()==null) return;  // avoid sending hardware commands unless the hardware is there and we are active
        Tmpdiff128.Biasgen biasgen=(Tmpdiff128.Biasgen)getChip().getBiasgen();
        if(biasgen==null) {
//            log.warning("null biasgen, not doing anything");
            return;
        }
        biasgen.loadPreferences();
        state=State.INITIAL;
    }
    
    public int getRateHigh(){
        return rateHigh;
    }
    
    synchronized public void setRateHigh(int upperThreshKEPS){
        this.rateHigh=upperThreshKEPS;
        prefs.putInt("Tmpdiff128RateController.rateHigh",upperThreshKEPS);
    }
    
//    public int getRateMid() {
//        return rateMid;
//    }
//
//    public void setRateMid(int midThreshKEPS) {
//        this.rateMid = midThreshKEPS;
//        prefs.putInt("Tmpdiff128RateController.rateMid",midThreshKEPS);
//    }
    
    public int getRateLow() {
        return rateLow;
    }
    
    synchronized public void setRateLow(int lowerThreshKEPS) {
        this.rateLow = lowerThreshKEPS;
        prefs.putInt("Tmpdiff128RateController.rateLow",lowerThreshKEPS);
    }
    
//    public int getRateHysteresis() {
//        return rateHysteresis;
//    }
//
//    public void setRateHysteresis(int hysteresisKEPS) {
//        this.rateHysteresis = hysteresisKEPS;
//        prefs.putInt("Tmpdiff128RateController.rateHysteresis",hysteresisKEPS);
//    }
    
    
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!isFilterEnabled()) return in;
        float r=in.getEventRateHz()/1e3f;
        
        lastt=(int)(System.currentTimeMillis()*1000);
        float lpRate=filter.filter(r,lastt);
        
        setState(lpRate);
        setBiases();
        if(writeLogEnabled){
            if(logWriter==null) logWriter=openLoggingOutputFile();
            try{
                logWriter.write(in.getLastTimestamp()+" "+r+" "+lpRate+" "+state.ordinal()+"\n");
            }catch(Exception e){
                e.printStackTrace();
            }
        }
//        System.out.println(lastt+" "+r+" "+lpRate+" "+state);
        return in;
    }
    
    void setState(float r){
        lastState=state;
        switch(state){
            case LOW:
                if(r>rateLow*hysteresisFactor) state=State.MEDIUM;
                break;
            case MEDIUM:
                if(r<rateLow/hysteresisFactor) state=State.LOW;
                else if(r>rateHigh*hysteresisFactor) state=State.HIGH;
                break;
            case HIGH:
                if(r<rateHigh/hysteresisFactor) state=State.MEDIUM;
                break;
            default:
                state=State.MEDIUM;
        }
    }
    
    void setBiases(){
//        if(state==lastState) return;
        Tmpdiff128.Biasgen biasgen=(Tmpdiff128.Biasgen)getChip().getBiasgen();
        if(biasgen==null) {
            log.warning("null biasgen, not doing anything");
            return;
        }
        switch(state){
            case LOW:
                biasgen.decreaseThreshold();
                break;
            case HIGH:
                biasgen.increaseThreshold();
                break;
            default:
        }
        
    }
    
    public float getRateFilter3dBFreqHz() {
        return rateFilter3dBFreqHz;
    }
    
    synchronized public void setRateFilter3dBFreqHz(float rateFilter3dBFreqHz) {
        if(rateFilter3dBFreqHz<.01) rateFilter3dBFreqHz=0.01f; else if(rateFilter3dBFreqHz>20) rateFilter3dBFreqHz=20;
        this.rateFilter3dBFreqHz = rateFilter3dBFreqHz;
        prefs.putFloat("Tmpdiff128RateController.rateFilter3dBFreqHz",rateFilter3dBFreqHz);
        filter.set3dBFreqHz(rateFilter3dBFreqHz);
        
    }
    
    public void initFilter() {
    }
    
    public float getHysteresisFactor() {
        return hysteresisFactor;
    }
    
    synchronized public void setHysteresisFactor(float h) {
        if(h<1) h=1; else if(h>5) h=5;
        this.hysteresisFactor = h;
        prefs.putFloat("Tmpdiff128RateController.hysteresisFactor",hysteresisFactor);
    }
    
    
    Writer openLoggingOutputFile(){
        DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
        String dateString=loggingFilenameDateFormat.format(new Date());
        String className="Tmpdiff128RateController";
        int suffixNumber=0;
        boolean suceeded=false;
        String filename;
        Writer writer=null;
        File loggingFile;
        do{
            filename=className+"-"+dateString+"-"+suffixNumber+".txt";
            loggingFile=new File(filename);
            if(!loggingFile.isFile()) suceeded=true;
        }while(suceeded==false && suffixNumber++<=5);
        if(suceeded==false){
            log.warning("could not open a unigue new file for logging after trying up to "+filename);
            return null;
        }
        try{
            writer=new FileWriter(loggingFile);
            log.info("starting logging bias control at "+dateString);
            writer.write("# time rate lpRate state\n");
            writer.write(String.format("# rateLow=%f rateHigh=%f hysteresisFactor=%f, 3dbCornerFreqHz=%f\n",rateLow, rateHigh, hysteresisFactor, rateFilter3dBFreqHz));
        }catch(Exception e){
            e.printStackTrace();
        }
        return writer;
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
        gl.glColor3f(1,1,1); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0,0,0);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("lpRate=%s, state=%s",fmt.format(filter.getValue()),state.toString()));
        gl.glPopMatrix();
    }
    
    public boolean isWriteLogEnabled() {
        return writeLogEnabled;
    }
    
    public void setWriteLogEnabled(boolean writeLogEnabled) {
        this.writeLogEnabled = writeLogEnabled;
    }
}
