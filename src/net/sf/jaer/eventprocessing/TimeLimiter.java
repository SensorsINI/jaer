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
 * This utility class allows setting a limiting time and cheap checking for
 * whether this time is exceeded. Event processors use it to limit their own
 * execution time. The caller initializes the TimeLimiter and then calls to see
 * if this time has been exceeded.
 *
 * @author tobi
 */
final public class TimeLimiter extends Timer {

    private static Logger log = Logger.getLogger("TimeLimiter");
//    int counter=0;
    public final int DEFAULT_TIME_LIMIT_MS = 10;
    volatile public boolean timedOut = false;
    private int timeLimitMs = DEFAULT_TIME_LIMIT_MS;
    volatile private boolean enabled = false;
    private TimerTask currentTask = null;

//    /** only check System.nanoTime every checkTimeInterval calls to isTimedOut */
//    public static final int checkTimeInterval=100;
//
//    private long startTime, endTime;
//    private int checkCounter=checkTimeInterval;
//    private int limitMs=10;
    /**
     * Creates a new time limiter with timeout set to default value. Doesn't
     * start it.
     */
    public TimeLimiter() {
    }

    /**
     * Start timer
     *
     * @param ms the duration of the timer in ms. Time limit <=0 means no time
     * limit and no timer.
     */
    private void start(int ms) {
        if (ms <= 0) {
            if (currentTask != null) {
                currentTask.cancel();
                purge();
            }
            return;
        }
        timedOut = false;
        setTimeLimitMs(ms);
        if (currentTask != null) {
            currentTask.cancel();
        }
        if (enabled) {
            schedule(currentTask = new TimerTask() {
                public void run() {
                    if (enabled) {
//                        log.info("timeout after "+timeLimitMs+" ms");
                        timedOut = true;
                    }
                }
            }, ms);
        }
    }

    final public void restart() {
        if (timeLimitMs <= 0) {
            setEnabled(false);
            return;
        }
        setEnabled(true);
        start(timeLimitMs);
    }

    /**
     * check if timer expired
     *
     * @return true if time exceeded and timeout is enabled, otherwise false
     */
    final public boolean isTimedOut() {
        return enabled && timedOut;
    }

    final public int getTimeLimitMs() {
        return timeLimitMs;
    }

    /**
     * sets the time limit in ms, but does not start timer
     *
     * @param timeLimitMs - if value is zero, then timeLimiter is disabled
     */
    final public void setTimeLimitMs(int timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
        if (timeLimitMs <= 0) {
            setEnabled(false);
        }
    }

    final public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set true to enable time timeouts, false to disable starting timer. If
     * false, clears the timedOut flag.
     *
     * @param enabled
     */
    final public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            timedOut = false;
        }
    }

    @Override
    public String toString() {
        return "TimeLimiter{" + "timedOut=" + timedOut + ", timeLimitMs=" + timeLimitMs + ", enabled=" + enabled + '}';
    }

}
