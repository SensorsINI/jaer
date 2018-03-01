/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.filter.RotateFilter;
import org.ine.telluride.jaer.tell2011.head6axis.Head6DOF_ServoController;


/**
 * Spins the eyes of the 6DOF robothead around in a circle
 *
 * @author Philipp
 */
@Description("Spins the eyes of the 6DOF robothead around in a circle")
public class Head6DOF_SpinAround extends EventFilter2D { // extends EventFilter only to allow enclosing in filter

    protected static final Logger log = Logger.getLogger("Head6DOF_SpinAround");
    int eyeSpinCounter = 0;
    float eyeSpinValue = .0f;
    EyeSpinAround eyeSpinAround = null;
    
    FilterChain filterChain = null;
    Head6DOF_ServoController headControl = null;

    public Head6DOF_SpinAround(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(new RotateFilter(chip));
        filterChain.add(new BackgroundActivityFilter(chip));
        headControl = new Head6DOF_ServoController(chip);
        filterChain.add(headControl);
        setEnclosedFilterChain(filterChain);
        setPropertyTooltip("starteyeSpinAround", "performs automatically a circle movement with the cameras");
        setPropertyTooltip("stopeyeSpinAround", "stops the spinning cameras");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = filterChain.filterPacket(in);
        return out;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public void doStartEyeSpinAround() {
        eyeSpinValue = 0; // resets the position value for spinning
        eyeSpinCounter = 0; // resets the counter for spinning
        eyeSpinAround = new EyeSpinAround();
        eyeSpinAround.start(); // starts the thread            
    }

    public void doStopEyeSpinAround() {
        eyeSpinAround.stopThread();
    }

    // <editor-fold defaultstate="collapsed" desc="EYE_SpinAroundThread">
    private class EyeSpinAround extends Thread {
        
        EyeSpinAround(){
            setName("EyeSpinAround");
        }

        volatile boolean stop = false;
        int sleepTime = 13; // sleep after each step in ms; eyes spin around in a circle within 3 seconds and 231 steps

        public void stopThread() {
            stop = true;
            interrupt();
        }

        public void run() {
            while (!stop) {
                for (; eyeSpinCounter <= 70; eyeSpinCounter = eyeSpinCounter + 1) {
                    try {
                        headControl.setEyeGazeDirection(-headControl.getEYE_PAN_LIMIT() + (eyeSpinValue), -headControl.getEYE_TILT_LIMIT());    //moves the eyes to the right
                        eyeSpinValue = eyeSpinValue + .01f;
                        Thread.sleep(sleepTime);
                    } catch (Exception e) {
                        log.warning(e.toString());
                    }
                }
                for (; eyeSpinCounter > 70 && eyeSpinCounter <= 115; eyeSpinCounter = eyeSpinCounter + 1) {
                    try {
                        headControl.setEyeGazeDirection(headControl.getEYE_PAN_LIMIT(),-headControl.getEYE_TILT_LIMIT() + (eyeSpinValue - .7f));    //moves the eyes upwards
                        eyeSpinValue = eyeSpinValue + .01f;
                        Thread.sleep(sleepTime);
                    } catch (Exception e) {
                        log.warning(e.toString());
                    }
                }
                for (; eyeSpinCounter > 115 && eyeSpinCounter <= 185; eyeSpinCounter = eyeSpinCounter + 1) {
                    try {
                        headControl.setEyeGazeDirection(headControl.getEYE_PAN_LIMIT() - (eyeSpinValue - 1.15f), headControl.getEYE_TILT_LIMIT());  //moves the eyes to the left
                        eyeSpinValue = eyeSpinValue + .01f;
                        Thread.sleep(sleepTime);
                    } catch (Exception e) {
                        log.warning(e.toString());
                    }
                }
                for (; eyeSpinCounter > 185 && eyeSpinCounter <= 230; eyeSpinCounter = eyeSpinCounter + 1) {
                    try {
                        headControl.setEyeGazeDirection(-headControl.getEYE_PAN_LIMIT(), headControl.getEYE_TILT_LIMIT() - (eyeSpinValue - 1.85f)); //moves the eyes downwards
                        eyeSpinValue = eyeSpinValue + .01f;
                        Thread.sleep(sleepTime);
                    } catch (Exception e) {
                        log.warning(e.toString());
                    }
                }
                if (eyeSpinCounter >= 230) {
                    eyeSpinCounter = 0;
                    eyeSpinValue = 0;
                }

            }
        }
    }// </editor-fold>
}
