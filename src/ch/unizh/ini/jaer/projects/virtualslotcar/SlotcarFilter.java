/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.event.*;

/**
 * Filter for displaying virtual slot car movement
 * 
 * @author Michael Pfeiffer
 */
public class SlotcarFilter extends EventFilter2D implements Observer {

    JFrame drawingWindow = null;

    /** Time constant for LIF Neurons */
    //    protected float tau=getPrefs().getFloat("SlotcarFilter.tau",0.005f);

    /** Spatial scaling of filter window */
    //    protected int spatialscale = getPrefs().getInt("SlotcarFilter.spatialscale",10);

    public SlotcarFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();

        // setPropertyTooltip("tau","Time constant of LIF neuron");


    }


    /** Filters events */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(OrientationEvent.class);

        OutputEventIterator outItr=out.outputIterator();

        // Cycle through events
        for(Object e:in){
           BasicEvent i=(BasicEvent)e;
           float ts=i.timestamp;
           short x=(short)(i.x), y=(short)(i.y);


        }

        return out; // Hope this is correct
   }


    public static String getDescription() {
        return "Display of Slot-Car Movement";
    }

    final int DEFAULT_TIMESTAMP=Integer.MIN_VALUE;

/*    public float getVleak() {
        return vleak;
    }

    synchronized public void setVleak(float vleak) {
        getPrefs().putFloat("SlotcarFilter.vleak",vleak);
        support.firePropertyChange("vleak",this.vleak,vleak);

        this.vleak = vleak;
        if (horizontal_cells != null) {
            for (int i=0; i<dim_pixels*dim_pixels; i++) {
                horizontal_cells[i].setVleak(vleak);
                vertical_cells[i].setVleak(vleak);
            }
        }
    }
*/

    public void initFilter() {
        // Do what is necessary to initialize filter

    }


    /** Reset filter */
    synchronized public void resetFilter() {
        // Do what is necessary to reset filter
    }

    public Object getFilterState() {
        return null;
    }

    synchronized public void update(Observable o, Object arg) {
//        if(!isFilterEnabled()) return;
        initFilter();
        resetFilter();
    }

    /** Turns the additional display on or off */
    private void setDrawingWindow(boolean yes) {
        System.out.println("Drawing window " + yes + " : " + drawingWindow);
        if ( !yes ){
            if ( drawingWindow != null ){
                drawingWindow.dispose();
                drawingWindow = null;
            }
        } else{
            if (drawingWindow == null) {
                System.out.println("Creating Window!");
                drawingWindow = new SlotcarFrame();
                System.out.println("Finished creating window!");
                drawingWindow.setVisible(yes);
            }
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);

        // Create Frame for drawing
        setDrawingWindow(yes);
        
    }

}
