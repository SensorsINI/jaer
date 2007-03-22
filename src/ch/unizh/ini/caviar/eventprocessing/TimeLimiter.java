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

package ch.unizh.ini.caviar.eventprocessing;

import java.util.*;
import java.util.logging.*;

/**
 * This utility class allows setting a limiting time and cheap checking for whether this time is exceeded. Event processors use
 it to limit their own execution time. The caller initializes the TimeLimiter and then calls to see if this time has been exceeded.
 Filters that use this class should label themselves as {@link TimeLimitingFilter} so that GUI can be constructed with controls.
 
 * @author tobi
 */
public class TimeLimiter extends Timer{
    private static Logger log=Logger.getLogger("TimeLimiter");
//    int counter=0;
    boolean timedOut=false;
    
//    /** only check System.nanoTime every checkTimeInterval calls to isTimedOut */
//    public static final int checkTimeInterval=100; 
//    
//    private long startTime, endTime;
//    private int checkCounter=checkTimeInterval;
//    private int limitMs=10;
    
    /** Creates a new instance of TimeLimiter */
    public TimeLimiter() {
    }

    /** Start timer
     @param ms the duration of the timer in ms
     */
    final public void start(int ms){
        timedOut=false;
        schedule(new TimerTask(){
            public void run(){
                timedOut=true;
            }
        },ms);
//        this.limitMs=ms;
//        startTime=System.nanoTime();
//        endTime=startTime+(ms<<20); // ms * 2^20 approx 1e6 to get ns
    }
    
    /** check if timer expired
     @return true if time exceeded
     */
    final public boolean isTimedOut(){
//        if(timedOut) log.info("timer time out "+(++counter));
        return timedOut;
//        if(checkCounter--==0){
//            checkCounter=checkTimeInterval;
//            long t=System.nanoTime();
//            boolean timedOut=t>endTime?true:false;
////            if(timedOut){
////                log.info("timed out: limit="+limitMs+" ms, actual="+(t-startTime)/1e6f+" ms");
////            }
//            return timedOut;
//        }else{
//            return false;
//        }
    }
    
}
