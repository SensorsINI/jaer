/*
 * TimeLimiter.java
 *
 * Created on December 16, 2006, 9:12 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright December 16, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.eventprocessing;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * This utility class allows setting a limiting time and 
 cheap checking for whether this time is exceeded. Event processors use
 it to limit their own execution time. The caller initializes the 
 TimeLimiter and then calls to see if this time has been exceeded.
 
 * @author tobi
 */
final public class TimeLimiter extends Timer{
    private static Logger log=Logger.getLogger("TimeLimiter");
//    int counter=0;
    volatile public boolean timedOut=false;
    private int timeLimitMs=10;
    volatile private boolean enabled=false;
    
//    /** only check System.nanoTime every checkTimeInterval calls to isTimedOut */
//    public static final int checkTimeInterval=100;
//
//    private long startTime, endTime;
//    private int checkCounter=checkTimeInterval;
//    private int limitMs=10;
    
    /** Creates a new time limiter with timeout ms. Doesn't start it. */
    public TimeLimiter(){
    }
    
    /** Start timer
     @param ms the duration of the timer in ms
     */
    private void start(int ms){
        timedOut=false;
        setTimeLimitMs(ms);
        if(enabled){
            schedule(new TimerTask(){
                public void run(){
                    if(enabled) {
//                        log.info("timeout after "+timeLimitMs+" ms");
                        timedOut=true;
                    }
                }
            },ms);
        }
    }
    
    final public void restart(){
        start(timeLimitMs);
    }
    
    /** check if timer expired
     @return true if time exceeded and timeout is enabled, otherwise false
     */
    final public boolean isTimedOut(){
        return enabled && timedOut;
    }
    
    final public int getTimeLimitMs() {
        return timeLimitMs;
    }
    
    final public void setTimeLimitMs(int timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
    }
    
    final public boolean isEnabled() {
        return enabled;
    }
    
    /** Set true to enable time timeouts, false to disable starting timer */
    final public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if(!enabled) timedOut=false;
    }

    @Override
    public String toString() {
        return "TimeLimiter{" + "timedOut=" + timedOut + ", timeLimitMs=" + timeLimitMs + ", enabled=" + enabled + '}';
    }
    
    
    
}
