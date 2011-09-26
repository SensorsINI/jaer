/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.ClassItUp;

/**
 *
 * @author tobi
 */



import net.sf.jaer.chip.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
//import java.lang.Thread;

public class InterfaceTest extends EventFilter2D {

    private int loopcount=100;
    private int state=1;

    public void increment(){

        state=(state+1)%loopcount;

        if (state==1){
            
            System.out.println("Events are happening!");

        }

    }

    public  InterfaceTest(AEChip  chip){
        super(chip);
    }

    public EventPacket<?> filterPacket( EventPacket<?> P){
        increment();
        return P;
    }

    public void initFilter(){
    }

    public void resetFilter(){
    }

    public InterfaceTest getFilterState(){
        return this;
    }
public void eventLoop(){



}




}
