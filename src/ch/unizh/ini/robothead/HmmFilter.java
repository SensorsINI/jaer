package ch.unizh.ini.robothead;
/*
 * HmmFilter.java
 *
 * Created on 1. Januar 2008, 13:33
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */



import ch.unizh.ini.robothead.HmmTools;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import com.sun.org.apache.xpath.internal.operations.Mod;
import java.util.*;
//import experiment1.PanTilt;
import java.util.Vector;
import ch.unizh.ini.caviar.util.EngineeringFormat;
import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import java.io.*;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;

/**
 *
 * @author jaeckeld
 */
public class HmmFilter extends EventFilter2D implements Observer {
    
    private int hmmTime=getPrefs().getInt("HmmFilter.hmmTime",2000);
    private boolean dispVector=getPrefs().getBoolean("HmmFilter.dispVector",false);
    
    
    
    /** Creates a new instance of HmmFilter */
    public HmmFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("hmmTime", "after this amount of time apply viterbi Algo [ms]");
        
        myHmm.loadHmmData();            // load HMM Model arrays
        myHmm.genSoundLUT();
       
        //TODO: constructor
        
        vectSize=(int)myHmm.DATA[0][0];
        chMin=(int)myHmm.DATA[0][1];
        chMax=(int)myHmm.DATA[0][2];
        N=(int)myHmm.DATA[0][3];        // number of vertical Bins
        maxVal=(int)myHmm.DATA[0][4];
         
        resetFilter();
        //dispVector();
        
        
        
    }
    // TODO declare variables, instances...
    int vectSize;   // temporal width of observation Bin [us]
    int numOfBins;
    int maxVal;
    int chMin;
    int chMax;
    int N;          // number of vertical Bins
    boolean isFirstTs;  // FALSE at the beginning or after reset, TRUE after having used one Ts
    int firstTs;
    int actualVectorLeft[];
    int actualVectorRight[];
    int actualObservationLeft;
    int actualObservationRight;
    int actualStart;
    int actualEnd;
    int wiis[];
    HmmTools myHmm = new HmmTools(maxVal);
    int noTsObservation;
    Vector observationBufferLeft;
    Vector observationBufferRight;
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()){
            //System.out.print("TEST 2");
            return in;       // only use if filter enabled
        }
        checkOutputPacketEventType(in);
        
        for(Object e:in){
            
            BasicEvent i =(BasicEvent)e;
            
            if (this.isFirstTs==false){     // detect first TS
                firstTs=i.timestamp;
                isFirstTs=true;
                actualStart=i.timestamp;
                actualEnd=actualStart+vectSize;
            }
            
            while (i.timestamp>=actualEnd+vectSize){        // in this case an observation was jumped because there were no ts
                observationBufferLeft.add(noTsObservation);
                //observationBufferRight.add(noTsObservation);
                actualStart=actualEnd;
                actualEnd=actualStart+vectSize;
            }
            
            if (i.timestamp>=actualEnd){
                                
                actualStart=actualEnd;
                actualEnd=actualStart+vectSize;
               
                // encode observation
                
                actualObservationLeft=myHmm.getObservation(actualVectorLeft[0],actualVectorLeft[1],actualVectorLeft[2],actualVectorLeft[3],actualVectorLeft[4]);
                observationBufferLeft.add(new Integer(actualObservationLeft));
                
                if (dispVector){
                    dispVector();   // display the actual (left) Observation Vector
                    System.out.println("  => "+actualObservationLeft);
                }
                
                if (observationBufferLeft.size()>numOfBins){
                    int piState =1;
                    // call viterbi
                    dispObservations();
                    
                    double[][] statesLeft=myHmm.viterbi(observationBufferLeft,piState,myHmm.TR_Left,myHmm.EMIS_Left);
                    System.out.println("Viterbi States: ");
                    dispStates(statesLeft);
                    
                    
                    // Empty ObservationBuffer:
                    this.observationBufferLeft= new Vector(numOfBins-10,10);
                }
                
                actualVectorLeft= new int[N];       // reset Vectors!
                actualVectorRight= new int[N];
                
            }
            
            if (i.x+1 >= chMin && i.x+1 <= chMax){      // add ts to Output Vector
                // i.x=channel [0 31] !!!
                if (i.y==0)
                    if (this.actualVectorLeft[wiis[i.x+1-chMin]]<maxVal){       // limit Value to maxVal
                        this.actualVectorLeft[wiis[i.x+1-chMin]]=this.actualVectorLeft[wiis[i.x+1-chMin]]+1;
                    }
                else
                    if (this.actualVectorRight[wiis[i.x+1-chMin]]<maxVal){
                        this.actualVectorRight[wiis[i.x+1-chMin]]=this.actualVectorRight[wiis[i.x+1-chMin]]+1;
                    }
            }
            
            
            
        }
        
        return in;
        
    }
    public Object getFilterState() {
        return null;
    }
    public void resetFilter(){
        System.out.println("reset!");
        
        this.isFirstTs=false;
        this.actualVectorLeft= new int[N];
        this.actualVectorRight= new int[N];
        this.wiis=this.genWiis(this.chMin,this.chMax,this.N);
        this.numOfBins=1000*this.hmmTime/this.vectSize;
        //this.observationBuffer = new int[this.numOfBins];
        this.observationBufferLeft= new Vector(numOfBins-10,10);
        myHmm.genCodeArray(maxVal);     // generate array for encoding observations
        this.noTsObservation=myHmm.getObservation(0,0,0,0,0);
    }
    public void initFilter(){
        System.out.println("init!");
        resetFilter();
        // TODO
    }
    public void update(Observable o, Object arg){
        initFilter();
    }
    public int gethmmTime(){
        return this.hmmTime;
    }
    public void setHmmTime(int hmmTime){
        getPrefs().putInt("HmmFilter.hmmTime",hmmTime);
        support.firePropertyChange("hmmTime",this.hmmTime,hmmTime);
        this.hmmTime=hmmTime;
        resetFilter();
    }
     public boolean isDispVector(){
        return this.dispVector;
    }
    public void setDispVector(boolean dispVector){
        this.dispVector=dispVector;
        getPrefs().putBoolean("HmmFilter.dispVector",dispVector);
    }
    
    public int[] genWiis(int minCh, int maxCh, int nNumb){
        int start;
        int numbCh=maxCh-minCh+1;
        int wiis[];
        wiis= new int[numbCh];
        int widthN=numbCh/nNumb;
        for (int i=0; i<N; i++){
            start=i*widthN;
            for (int j=start; j<start+widthN; j++){
                wiis[j]=i;
            }
        }
        
        return wiis;
    }
    public void dispVector(){
        for (int i=0; i<this.actualVectorLeft.length; i++){
            System.out.print(this.actualVectorLeft[i]+" ");
        }
        //System.out.println("");
    }
    public void dispObservations(){
        for (int i=0; i<this.observationBufferLeft.size(); i++){
            System.out.print(this.observationBufferLeft.get(i)+" ");
        }
        System.out.println("");
    }
    public void dispStates(double[][] states){
        for (int i=0; i<states[0].length; i++){
            System.out.print(states[1][i]+" ");
        }
        System.out.println("");
    }
    
    
    
    
    
    
}
