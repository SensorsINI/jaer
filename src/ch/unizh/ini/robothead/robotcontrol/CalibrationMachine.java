/*
 * CalibrationMachine.java
 *
 * Created on 21. Februar 2008, 03:20
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.robothead.robotcontrol;

import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.tracking.*;


//import ch.unizh.ini.caviar.util.StateMachineStates;


/**
 * Calibration procedure for retina/pantilt System, in order to get position vs. angle
 * @author jaeckeld
 */
public class CalibrationMachine {
    
    PanTilt mydreher;
    
    String state;
    int deg;
    int pos;
    int range=20;
    
    double[] storedPos;
    int numStoredPos=20;
    int countStoredPos;
    
    int countNotLocallized;
    
    double[][] LUT;
    
    
    /** Creates a new instance of CalibrationMachine */
    public CalibrationMachine() {
        
        // initialize Pan-Tilt
        
        mydreher=new PanTilt();
        int portPT = 7;
        int baud=38400;
        int databits=8;
        int parity=0;
        int stop=1;
        
        boolean BoolBuf;
        BoolBuf = mydreher.init(portPT,baud,databits,parity,stop);
        
        
        // this is like the starting state
        
        deg=-1*range;
        pos=0;
        state="moving";
        
        LUT=new double[2][2*range+1];
        
    }
    
    public void running(RectangularClusterTracker.Cluster LED){
        
        if (state == "moving"){     // move to position deg
            
            boolean side;
            if (deg<0) side=true;
            else side=false;
            
            mydreher.setDegree(1,java.lang.Math.abs(deg),side);
            storedPos=new double[numStoredPos];
            countStoredPos=0;
            countNotLocallized=0;
            
            state="look";
        }
        if (state=="look"){
            
            if(LED!=null){      // LED localized
                storedPos[countStoredPos]=LED.getLocation().getX();
                countStoredPos++;
                if(countStoredPos==numStoredPos){       // buffer is filled
                    double sum=0;
                    for(int i=0;i<storedPos.length;i++){
                        sum=sum+storedPos[i];
                    }
                    double finalPos=sum/storedPos.length;       // take the mean of buffer values
                    
                    LUT[0][pos]=deg;    // set LUT values
                    LUT[1][pos]=finalPos;
                    
                    pos++;
                    deg++;
                    if(deg>range){
                        state="ending";
                    } else state="moving";
                }
                
            }else{              // LED not found
                countNotLocallized++;
                if (this.countNotLocallized>20){       // lets say...
                    LUT[0][pos]=deg;    // set LUT values
                    LUT[1][pos]=100;    // i'll never use this value...
                    
                    pos++;
                    deg++;
                    if(deg>range){
                        state="ending";
                    } else state="moving";
                }
            }
            
        }
        
        if(state=="ending"){
            dispLUT();
            ControlFilter.setState("hearing");
        }
        
        
        
    }
    
    public void dispLUT(){
        for(int i=0;i<LUT[0].length;i++){
            System.out.println(LUT[0][i]+" => "+LUT[1][i]);
        }
    }
    
    public void setBufferSize(int value){
        this.numStoredPos=value;
    }
    
}
