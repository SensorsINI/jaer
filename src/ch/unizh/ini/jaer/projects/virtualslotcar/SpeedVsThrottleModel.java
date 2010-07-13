/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

/**
 * Empirical model of speed of car vs throttle, measured from recorded data.
 *  Blue car, intermediate power, inside lane of oval track.
 *
 * @author tobi
 */
public class SpeedVsThrottleModel {
    private float speedUpTimeMs=80;
    private float slowDownTimeMs = 200;
    private float maxSpeedPPS = 500;
    private float minSpeedPPS = 250;
    private float thresholdThrottle = .07f;
    private float fullThrottle = .35f;
    private float stopSpeed = 100;
    
    public float computeThrottle(float speedPPS){
        float throttle=0;
        if(speedPPS<stopSpeed) throttle=0;
        else if(speedPPS < minSpeedPPS) throttle = thresholdThrottle;
        else if(speedPPS>maxSpeedPPS) throttle=fullThrottle;
        else throttle=(fullThrottle-thresholdThrottle)*(speedPPS-minSpeedPPS)/(maxSpeedPPS-minSpeedPPS);
        return throttle;
    }

    /**
     * @return the speedUpTimeMs
     */
    public float getSpeedUpTimeMs() {
        return speedUpTimeMs;
    }

    /**
     * @param speedUpTimeMs the speedUpTimeMs to set
     */
    public void setSpeedUpTimeMs(float speedUpTimeMs) {
        this.speedUpTimeMs = speedUpTimeMs;
    }

    /**
     * @return the slowDownTimeMs
     */
    public float getSlowDownTimeMs() {
        return slowDownTimeMs;
    }

    /**
     * @param slowDownTimeMs the slowDownTimeMs to set
     */
    public void setSlowDownTimeMs(float slowDownTimeMs) {
        this.slowDownTimeMs = slowDownTimeMs;
    }

    /**
     * @return the maxSpeedPPS
     */
    public float getMaxSpeedPPS() {
        return maxSpeedPPS;
    }

    /**
     * @param maxSpeedPPS the maxSpeedPPS to set
     */
    public void setMaxSpeedPPS(float maxSpeedPPS) {
        this.maxSpeedPPS = maxSpeedPPS;
    }

    /**
     * @return the minSpeedPPS
     */
    public float getMinSpeedPPS() {
        return minSpeedPPS;
    }

    /**
     * @param minSpeedPPS the minSpeedPPS to set
     */
    public void setMinSpeedPPS(float minSpeedPPS) {
        this.minSpeedPPS = minSpeedPPS;
    }

    /**
     * @return the thresholdThrottle
     */
    public float getThresholdThrottle() {
        return thresholdThrottle;
    }

    /**
     * @param thresholdThrottle the thresholdThrottle to set
     */
    public void setThresholdThrottle(float thresholdThrottle) {
        this.thresholdThrottle = thresholdThrottle;
    }

    /**
     * @return the fullThrottle
     */
    public float getFullThrottle() {
        return fullThrottle;
    }

    /**
     * @param fullThrottle the fullThrottle to set
     */
    public void setFullThrottle(float fullThrottle) {
        this.fullThrottle = fullThrottle;
    }

    /**
     * @return the stopSpeed
     */
    public float getStopSpeed() {
        return stopSpeed;
    }

    /**
     * @param stopSpeed the stopSpeed to set
     */
    public void setStopSpeed(float stopSpeed) {
        this.stopSpeed = stopSpeed;
    }

}
