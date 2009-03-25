package ch.unizh.ini.jaer.projects.holger;
/*
 * CorrelatorFilter.java
 *
 * Created on 28. November 2007, 14:13
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */


import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
//import com.sun.org.apache.xpath.internal.operations.Mod;
import java.util.*;
//import experiment1.PanTilt;
//import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;


import javax.swing.JFrame;
import javax.swing.JApplet;
import net.sf.jaer.graphics.*;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.Series;
import net.sf.jaer.util.chart.XYChart;

/**
 * Correlator Filter
 *
 * @author jaeckeld/holger
 */
public class CorrelatorFilter extends EventFilter2D implements Observer, FrameAnnotater  {
    BinsApplet applet;
    JFrame frame;

    private int shiftSize=getPrefs().getInt("CorrelatorFilter.shiftSize",800);
    private int binSize=getPrefs().getInt("CorrelatorFilter.binSize",100);
    private int numberOfPairs=getPrefs().getInt("CorrelatorFilter.numberOfPairs",100);
    private int dimLastTs=getPrefs().getInt("CorrelatorFilter.dimLastTs",4);
    private boolean display=getPrefs().getBoolean("CorrelatorFilter.display",false);
    
    /** Creates a new instance of CorrelatorFilter */
    public CorrelatorFilter(AEChip chip) {
        super(chip);
        //initFilter();
        resetFilter();
        setPropertyTooltip("shiftSize", "maximum shift size for autocorrelation");
        setPropertyTooltip("binSize", "size for one Bin");
        setPropertyTooltip("numberOfPairs", "how many left/right pairs used");
        setPropertyTooltip("dimLastTs", "how many lastTs save");
        setPropertyTooltip("display","Display Bins");

        applet = new BinsApplet();
        frame = new ITDJFrame(applet);
        applet.init();
        applet.start();
        applet.updateBins(myBins);
        frame.setVisible(false);
        log.info("BinsApplet created");
    }
    

    int radius = 3;
    Angle myAngle = new Angle(radius);

    Bins myBins = new Bins();
    int[][][] lastTs = new int[32][2][dimLastTs];

    double ITD;
    double ILD;
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

       int nleft=0,nright=0;
            
        for(Object e:in){
            
            BasicEvent i =(BasicEvent)e;
            
            //System.out.println(i.timestamp+" "+i.x+" "+i.y);
            //  try{
            for (int j=0; j<this.lastTs[i.x][1-i.y].length; j++){
                int diff=i.timestamp-lastTs[i.x][1-i.y][j];     // compare actual ts with last complementary ts of that channel
                // x = channel y = side!!
                if (i.y==0)
                {
                    diff=-diff;     // to distingiuish plus- and minus-delay
                    nright++;
                }                        
                else
                    nleft++;

                //Compute weights
                int weight = i.timestamp - lastTs[i.x][i.y][0];

                //System.out.println(diff);
                if (java.lang.Math.abs(diff)<shiftSize){
                    //myBins.addToBin(diff,weight);
                    myBins.addToBin(diff);
                    //log.info("added:" + diff);
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
        ILD=(double)(nright-nleft)/(double)(nright+nleft); //Max ILD is 1 (if only one side active)
        ANG=myAngle.getAngle(ITD);
        //log.info("usedpairs:" + myBins.usedPairs.size() +" max:"+myBins.numberOfPairs+" bins:"+myBins.bins[0]+":"+myBins.bins[1]+":"+myBins.bins[2]+":"+myBins.bins[3]+":"+myBins.bins[4]+":"+myBins.bins[5]+":"+myBins.bins[6]+":"+myBins.bins[7]+":"+myBins.bins[8]+":"+myBins.bins[9]);
        //log.info("ddusedpairs:" + applet.myBins.usedPairs.size() +" max:"+applet.myBins.numberOfPairs+" bins:"+applet.myBins.bins[0]+":"+applet.myBins.bins[1]+":"+applet.myBins.bins[2]+":"+applet.myBins.bins[3]+":"+applet.myBins.bins[4]+":"+applet.myBins.bins[5]+":"+applet.myBins.bins[6]+":"+applet.myBins.bins[7]+":"+applet.myBins.bins[8]+":"+applet.myBins.bins[9]);

        if (display){
            //myBins.dispBins();
            
            
            //System.out.println(ITD+" "+ANG);
        }
        
        return in;
        
    }
    
    public int getAngle(){

        return myAngle.getAngle(myBins.getITD());
    }
    
    public Object getFilterState() {
        return null;
    }
    public void resetFilter(){
        
        myBins.genBins(shiftSize,binSize,numberOfPairs);
//        System.out.println(this.getBinSize());
//        System.out.println(this.getShiftSize());
        lastTs = new int[32][2][dimLastTs];
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
        lastTs = new int[32][2][dimLastTs];
        this.dimLastTs=dimLastTs;
    }
    public boolean isDisplay(){
        return this.display;
    }
    public void setDisplay(boolean display){
        this.display=display;
        getPrefs().putBoolean("CorrelatorFilter.display",display);
        if(!isFilterEnabled()) return;
        frame.setVisible(true);
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
        //glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  Angle=%s",ANG));
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  ILD=%f",ILD));
        gl.glPopMatrix();
    }
    
}

