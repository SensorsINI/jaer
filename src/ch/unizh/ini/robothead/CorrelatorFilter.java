package ch.unizh.ini.robothead;
/*
 * CorrelatorFilter.java
 *
 * Created on 28. November 2007, 14:13
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */


import ch.unizh.ini.robothead.Bins;
import ch.unizh.ini.robothead.Angle;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
//import com.sun.org.apache.xpath.internal.operations.Mod;
import java.util.*;
//import experiment1.PanTilt;
import java.util.Vector;
//import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import java.io.*;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.util.EngineeringFormat;


/**
 * Correlator Filter
 *
 * @author jaeckeld
 */
public class CorrelatorFilter extends EventFilter2D implements Observer, FrameAnnotater  {
    
    private int shiftSize=getPrefs().getInt("CorrelatorFilter.shiftSize",800);
    private int binSize=getPrefs().getInt("CorrelatorFilter.binSize",40);
    private int numberOfPairs=getPrefs().getInt("CorrelatorFilter.numberOfPairs",1000);
    private int dimLastTs=getPrefs().getInt("CorrelatorFilter.dimLastTs",5);
    private boolean display=getPrefs().getBoolean("CorrelatorFilter.display",false);
    
    HmmFilter BDFilter;
    
    
    /** Creates a new instance of CorrelatorFilter */
    public CorrelatorFilter(AEChip chip) {
        super(chip);
        //initFilter();
        resetFilter();
        setPropertyTooltip("shiftSize", "maximum shift size for autocorrelation");
        setPropertyTooltip("binSize", "size for one Bin");
        setPropertyTooltip("numberOfPairs", "how many left/right pairs used");
        setPropertyTooltip("dimLastTs", "how many lastTs save");
        setPropertyTooltip("display","Display Bins and ITD/Angle");
        
        BDFilter = new HmmFilter(chip);
        //setEnclosedFilter(BDFilter);
        
    }
    
    int radius = 3;
    Angle myAngle = new Angle(radius);
    
    Bins myBins = new Bins();
    int[][][] lastTs = new int[32][2][dimLastTs];
    
    double ITD;
    int ANG;
    boolean side;
    
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
              
        if(!isFilterEnabled()){
            //System.out.print("TEST 2");
            return in;       // only use if filter enabled
        }

        if(in.getSize()==0) return in;       // do nothing if no spikes came in...., this means empty EventPacket
        //resetFilter();
        checkOutputPacketEventType(in);
        
        for(Object e:in){
            
            BasicEvent i =(BasicEvent)e;
            
            //System.out.println(i.timestamp+" "+i.x+" "+i.y);
            //  try{
            for (int j=0; j<this.lastTs[i.x][1-i.y].length; j++){
                int diff=i.timestamp-lastTs[i.x][1-i.y][j];     // compare actual ts with last complementary ts of that channel
                // x = channel y = side!!
                if (i.y==0)  diff=-diff;                        // to distingiuish plus- and minus-delay
                
                //System.out.println(diff);
                if (java.lang.Math.abs(diff)<shiftSize){
                    myBins.addToBin(diff);
                }
            }
            for (int j=lastTs[i.x][i.y].length-1; j>0; j--){                  // shift values in lastTs
                lastTs[i.x][i.y][j]=lastTs[i.x][i.y][j-1];
            }
            lastTs[i.x][i.y][0]=i.timestamp;
            //}catch(ArrayIndexOutOfBoundsException eeob){
            //allocateMaps(chip);
            //}
        }
        
        
        ITD=myBins.getITD();
        ANG=myAngle.getAngle(ITD);
        
        
        if (display){
            myBins.dispBins();
            System.out.println(ITD+" "+ANG);
        }
        
        return in;
        
    }
    
    public int getAngle(){
        double ITD=myBins.getITD();
        return myAngle.getAngle(ITD);
    }
    
    public Object getFilterState() {
        return null;
    }
    public void resetFilter(){
        
        myBins.genBins(shiftSize,binSize,numberOfPairs);
//        System.out.println(this.getBinSize());
//        System.out.println(this.getShiftSize());
        int[][][] lastTs = new int[32][2][dimLastTs];
        //dreher.Reset();
        
    }
    public void initFilter(){
        System.out.println("init!");
        myBins.genBins(shiftSize,binSize,numberOfPairs);
        
    }
    
    public void update(Observable o, Object arg){
        initFilter();
    }
    
    
    public int getShiftSize(){
        return this.shiftSize;
    }
    public void setShiftSize(int shiftSize){
        getPrefs().putInt("CorrelatorFilter.shiftSize",shiftSize);
        support.firePropertyChange("shiftSize",this.shiftSize,shiftSize);
        this.shiftSize=shiftSize;
        myBins.genBins(shiftSize,binSize,numberOfPairs);
        
    }
    public int getBinSize(){
        return this.binSize;
    }
    public void setBinSize(int binSize){
        getPrefs().putInt("CorrelatorFilter.binSize",binSize);
        support.firePropertyChange("binSize",this.binSize,binSize);
        this.binSize=binSize;
        myBins.genBins(shiftSize,binSize,numberOfPairs);
        
    }
    public int getNumberOfPairs(){
        return this.numberOfPairs;
    }
    public void setNumberOfPairs(int numberOfPairs){
        getPrefs().putInt("CorrelatorFilter.numberOfPairs",numberOfPairs);
        support.firePropertyChange("numberOfPairs",this.numberOfPairs,numberOfPairs);
        this.numberOfPairs=numberOfPairs;
        myBins.genBins(shiftSize,binSize,numberOfPairs);
        
    }
    public int getDimLastTs(){
        return this.dimLastTs;
    }
    public void setDimLastTs(int dimLastTs){
        getPrefs().putInt("CorrelatorFilter.dimLastTs",dimLastTs);
        support.firePropertyChange("dimLastTs",this.dimLastTs,dimLastTs);
        int[][][] lastTs = new int[32][2][dimLastTs];
        this.dimLastTs=dimLastTs;
    }
    public boolean isDisplay(){
        return this.display;
    }
    public void setDisplay(boolean display){
        this.display=display;
        getPrefs().putBoolean("CorrelatorFilter.display",display);
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
        
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  Angle=%s",ANG));
        gl.glPopMatrix();
    }
    
}

