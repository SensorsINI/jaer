/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


package ch.unizh.ini.jaer.projects.ClassItUp;

/*
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JComponent;
import java.lang.String;
import static java.lang.System.out;
import jplot.*;
 import java.util.Scanner;
import javax.swing.*;
 *
*/


// JAER Stuff
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.event.EventPacket;
import java.util.*;


import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.*;
import net.sf.jaer.hardwareinterface.*;
import net.sf.jaer.hardwareinterface.usb.toradex.ToradexOakG3AxisAccelerationSensor;
import net.sf.jaer.hardwareinterface.usb.toradex.ToradexOakG3AxisAccelerationSensorGUI;
//import ch.unizh.ini.caviar.eventio.AEServerSocket;
import net.sf.jaer.eventprocessing.*;
//import ch.unizh.ini.caviar.eventprocessing.label.SimpleOrientationFilter;
import net.sf.jaer.graphics.FrameAnnotater;
//import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import java.awt.Graphics2D;
import java.io.*;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;
import net.sf.jaer.util.TobiLogger;





/**
 *
 * @author Peter
 */
public class Sparsify extends EventFilter2D {
    /* Network
     * Every Event-Source/Neuron has an integer "address".  When the event source
     * generates an event, it is propagated through the network.
     *
     * */

    //------------------------------------------------------

    //------------------------------------------------------
    // Main Methods

    public float maxFreq=1000;  // Frequency (Hz) at which half the events are discarded

   // private int ababab = getPrefs().getInt("Sparsify.ababab", 1050);

/*
    private boolean usePIDController=getPrefs().getBoolean("Sparsify.usePIDController",true);
    {setPropertyTooltip("usePIDController","use the PID controller");}
*/

    
    public int polarityPass=0;  // (a)ll, (b)lack, (w)hite


    // Deal with incoming packet
    @Override public EventPacket<?> filterPacket( EventPacket<?> P){
        if(!filterEnabled) return P;
        //out.outputIterator()
        
        float rate=P.getEventRateHz();


        if (rate<maxFreq)
            return P;
        else
        {
            OutputEventIterator outItr=out.outputIterator();

            out.setEventClass(PolarityEvent.class);

            for (int i=0; i<P.getSize()*maxFreq/rate; i++)
            { // iterate over the input packet**
                PolarityEvent e=(PolarityEvent) P.getEvent(i);
                if ((polarityPass==0) || (polarityPass==-1 && (e.polarity==e.polarity.Off)) || (polarityPass==1 && (e.polarity==e.polarity.On)))
                {   PolarityEvent x=(PolarityEvent)outItr.nextOutput();  // make an output event**
                    x.copyFrom(e);
                }
            }

            //System.out.print(rate+" Hz");
            //System.out.println(P.getSize()+"  "+out.getSize());

            return out;
        }

        
        
    }



    // Read the Network File on filter Reset
    @Override public void resetFilter(){
       
    }

    //------------------------------------------------------
    // Obligatory method overrides

    //  Initialize the filter
    public Sparsify(AEChip  chip){
        super(chip);
        
        //.addObserver(this);
        //C=new Sparsify(chip);
        //final String prop="Properties"; 
        
        //setPropertyTooltip("ababab", "ABABABAB");
        setPropertyTooltip("maxFreq", "Max Event Rate");
        setPropertyTooltip("polarityPass", "Polarity to accept.  (a)ll, (b)lack, (w)hite");
/*        setPropertyTooltip("maxFreq", "Maximum frequency at which to pass events");
        
*/
        


    }
    
    public float getMaxFreq() {
        return this.maxFreq;
    }
    
    public void setMaxFreq(float dt) {
        getPrefs().putFloat("Sparsify.MaxFreq",dt);
        support.firePropertyChange("maxFreq",this.maxFreq,dt);
        this.maxFreq = dt;
    }


    // Nothing
    @Override public void initFilter(){

    }

    // Nothing
    public Sparsify getFilterState(){
        return null;
    }
/*
    public int getAbabab()
    { return this.ababab;

    }

    public void setAbabab(int a)
    {   this.ababab=a;
        getPrefs().putInt("Sparsify.ababab",a);

    }*/

    public int getPolarityPass()
    {   return getPrefs().getInt("Sparsify.PolarityPass",0);
        
    }

    public void setPolarityPass(int a) {
        this.polarityPass=a;
        getPrefs().putInt("Sparsify.PolarityPass",a);
    }
    
/*
    public void setUsePIDController(boolean usePIDController) {
        this.usePIDController = usePIDController;
        //pidController = new PIDController(chip);
        getPrefs().putBoolean("Sparsify.usePIDController",usePIDController);
    }

    public boolean getUsePIDController() {
        return usePIDController;
    }*/

    //------------------------------------------------------


}
