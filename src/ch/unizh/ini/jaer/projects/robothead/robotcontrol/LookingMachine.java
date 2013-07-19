/*
 * LookingMachine.java
 *
 * Created on 21. Februar 2008, 03:20
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead.robotcontrol;

import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;





/**
 * State Machine that works with ControlFilter. When ControlFilter is in the state "looking", Pan Tilt moves
 * and looks for the LED..
 * 
 * @author jaeckeld
 */
public class LookingMachine {
    
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
    int panTiltPos=0;
    
    
    /** Creates a new instance of LookingMachine */
    public LookingMachine() {
        
        
        // this is like the starting state
        
        deg=-1*degStep;
        pos=0;
        state="moving";
        
    }
    
    public boolean running(RectangularClusterTracker.Cluster LED){
        
        if (state == "moving"){     // move to position deg
            
            KoalaControl.setDegreePT(1,deg);
            
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
                    panTiltPos=deg;
                    return true;    // programm basically finnished, dont open this method again if return true!
                  
                }
                
            }else{              // LED not found
                countNotLocallized++;
                if (this.countNotLocallized>60){       // lets say...
                    
                    
                    deg=deg+degStep;
                    
                    if(deg>degStep){
                        state="ending";
                    } else state="moving";
                }
            }
            
        }
        
        if(state=="ending"){
            return true;
        }else   return false;
    }
    

    public void setBufferSize(int value){
        this.numStoredPos=value;
    }
    
}