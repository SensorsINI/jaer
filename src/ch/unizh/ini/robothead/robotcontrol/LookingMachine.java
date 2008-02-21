/*
 * LookingMachine.java
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
public class LookingMachine {
    
    PanTilt mydreher;
    
    String state;
    int deg;
    int degStep=38;
    int pos;
    int range=20;
    
    double[] storedPos;
    int numStoredPos=20;
    int countStoredPos;
    
    int countNotLocallized;
    
    double finalPos=0;
    
    
    /** Creates a new instance of LookingMachine */
    public LookingMachine() {
        
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
        
        deg=-1*degStep;
        pos=0;
        state="moving";
        
    }
    
    public boolean running(RectangularClusterTracker.Cluster LED){
        
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
                if(countStoredPos==numStoredPos){       // buffer is filled, that means LED found
                    double sum=0;
                    for(int i=0;i<storedPos.length;i++){
                        sum=sum+storedPos[i];
                    }
                    finalPos=sum/storedPos.length;       // take the mean of buffer values
                    
                    return true;    // programm basically finnished, dont open this method again if return true!
                  
                }
                
            }else{              // LED not found
                countNotLocallized++;
                if (this.countNotLocallized>20){       // lets say...
                    
                    
                    deg=deg+degStep;
                    
                    if(deg>degStep){
                        state="ending";
                    } else state="moving";
                }
            }
            
        }
        
        if(state=="ending"){
            ControlFilter.setState("hearing");
            return true;
        }
        
        
        return false;
    }
    

    public void setBufferSize(int value){
        this.numStoredPos=value;
    }
    
}