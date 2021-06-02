/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

/**
 * Stores one ITDEvent
 *
 * @author Holger
 */
public class ITDEvent {
    private int ITD=0;
    private int timestamp=0;
    private int channel=0;
    private float weight=0f;
    
    /**
     * Constructor
     */
    public ITDEvent() {
    }

    /**
     * Constructor
     * @param ITD
     * @param timestamp
     * @param channel
     * @param weight
     */
    public ITDEvent(int ITD, int timestamp, int channel, float weight) {
        this.ITD=ITD;
        this.timestamp=timestamp;
        this.channel=channel;
        this.weight=weight;
    }

    /**
     * @return the ITD
     */
    public int getITD() {
        return ITD;
    }

    /**
     * @param ITD the ITD to set
     */
    public void setITD(int ITD) {
        this.ITD = ITD;
    }

    /**
     * @return the timestamp
     */
    public int getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp the timestamp to set
     */
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * @return the channel
     */
    public int getChannel() {
        return channel;
    }

    /**
     * @param channel the channel to set
     */
    public void setChannel(int channel) {
        this.channel = channel;
    }

    /**
     * @return the weight
     */
    public float getWeight() {
        return weight;
    }

    /**
     * @param weight the weight to set
     */
    public void setWeight(float weight) {
        this.weight = weight;
    }
}
