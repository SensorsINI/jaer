package net.sf.jaer.util;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


/** From http://www.jroller.com/santhosh/entry/tooltips_can_say_more. 
 * Adds slider tooltips that change as slider is dragged
 * @author @author Santhosh Kumar T - santhosh@in.fiorano.com 
 */
public class SliderToolTips{ 
 
    public static void enableSliderToolTips(final JSlider slider){ 
        slider.addChangeListener(new ChangeListener(){ 
            private boolean adjusting = false; 
            private String oldTooltip; 
            public void stateChanged(ChangeEvent e){ 
                if(slider.getModel().getValueIsAdjusting()){ 
                    if(!adjusting){ 
                        oldTooltip = slider.getToolTipText(); 
                        adjusting = true; 
                    } 
                    slider.setToolTipText(String.valueOf(slider.getValue())); 
                    hideToolTip(slider); // to avoid flickering :) 
                    postToolTip(slider); 
                }else{ 
                    hideToolTip(slider); 
                    slider.setToolTipText(oldTooltip); 
                    adjusting = false; 
                    oldTooltip = null; 
                } 
            } 
        }); 
    } 
 
    /*-------------------------------------------------[ Manual ToolTips ]---------------------------------------------------*/ 
 
    
    public static void postToolTip(JComponent comp){ 
        Action action = comp.getActionMap().get("postTip"); 
        if(action==null) // no tooltip 
            return; 
        ActionEvent ae = new ActionEvent(comp, ActionEvent.ACTION_PERFORMED, 
                                "postTip", EventQueue.getMostRecentEventTime(), 0); 
        action.actionPerformed(ae); 
    } 
 
    public static void hideToolTip(JComponent comp){ 
        Action action = comp.getActionMap().get("hideTip"); 
        if(action==null) // no tooltip 
            return; 
        ActionEvent ae = new ActionEvent(comp, ActionEvent.ACTION_PERFORMED, 
                                "hideTip", EventQueue.getMostRecentEventTime(), 0); 
        action.actionPerformed(ae); 
    } 
}