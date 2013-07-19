/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


package ch.unizh.ini.jaer.projects.integrateandfire;

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
import java.util.Random;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;








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

    public float maxFreq=getPrefs().getFloat("Sparsify.maxFreq", 1000.0f);  // Frequency (Hz) at which half the events are discarded

   // private int ababab = getPrefs().getInt("Sparsify.ababab", 1050);

/*
    private boolean usePIDController=getPrefs().getBoolean("Sparsify.usePIDController",true);
    {setPropertyTooltip("usePIDController","use the PID controller");}
*/

    
    public int polarityPass=0;  // (a)ll, (b)lack, (w)hite

    Random rand = new Random();

    
    // Deal with incoming packet
    @SuppressWarnings("unchecked")
	@Override 
    public EventPacket<?> filterPacket( EventPacket<?> P){
		if (!filterEnabled)
			return P;
        //out.outputIterator()
        
        float rate=P.getEventRateHz();


        if (rate<maxFreq && polarityPass == 0)
            return P;
        else
        {
			OutputEventIterator<?> outItr=out.outputIterator();

            out.setEventClass(PolarityEvent.class);
            int eventLimit = (maxFreq>=0)?(int)(P.getSize()*maxFreq/rate):P.getSize();
            int totalEvents = P.getSize();
        	PolarityEvent.Polarity pol = (polarityPass < 0)?PolarityEvent.Polarity.Off:PolarityEvent.Polarity.Off;
            if (polarityPass != 0) {
            	totalEvents = 0;
	            for (Object p:P)  {
	            	PolarityEvent e=(PolarityEvent) p;
	            	if (e.polarity == pol) 
	            		totalEvents++;
	            }
            }
            for (Object p:P)  { 
            	// iterate over the input packet**
            	PolarityEvent e=(PolarityEvent) p;// P.getEvent(i);
				if ((polarityPass == 0) || (e.polarity == pol)) {
					if (rand.nextInt(totalEvents) < eventLimit) {
	            		PolarityEvent x=(PolarityEvent)outItr.nextOutput();  // make an output event**
	            		x.copyFrom(e);
	            		eventLimit--;
	            		if (eventLimit <= 0)
	            			break;
					}
					totalEvents--;
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
        getPrefs().putFloat("Sparsify.maxFreq",dt);
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
    

}
