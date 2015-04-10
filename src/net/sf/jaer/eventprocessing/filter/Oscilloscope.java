/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.util.Iterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.InputEventIterator;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * A real-time oscilloscope, which can play back selected time or event slices
 * during live or recorded playback.
 *
 * @author tobi
 */
@Description("<html>A real-time oscilloscope, which can play back selected time or event slices during live or recorded playback.<p>Trigger input provide possibilites for synchronizing on special events")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class Oscilloscope extends EventFilter2D {

    public enum TriggerType {

        TimeInterval, EventInterval
    };

    public enum BufferType {

        TimeInterval, EventNumber
    };

    protected TriggerType triggerType = TriggerType.EventInterval;
    protected BufferType bufferType = BufferType.EventNumber;

    protected int numberOfEvents = getInt("numberOfEvents", 10000);
    protected int sampleTimeUs = getInt("sampleTimeUs", 1000);
    protected float timeScale = getInt("timeScale", 10);

    private int triggerTimestamp;

    private boolean sampling = true; // is new data being recorded?
    private boolean playing=false; // is recorded buffer being output still?
    private final EventPacket<BasicEvent> buffer;
    private OutputEventIterator recordingIterator;
    private Iterator<BasicEvent> playingIterator;

    public Oscilloscope(AEChip chip) {
        super(chip);
        this.buffer = new EventPacket<>();
        this.recordingIterator = this.buffer.outputIterator();
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (sampling) {
            sampleEvents(in.inputIterator());
        } else {
            Iterator<?> inputIterator = in.inputIterator();
            while(inputIterator.hasNext()){
                BasicEvent e=(BasicEvent)inputIterator.next();
                if(isTrigger(e))
                    sampleEvents(inputIterator);
            }
           
        }
        if(playing){
            if(playingIterator==null){
                playingIterator=buffer.inputIterator();
            }
            OutputEventIterator outItr=getOutputPacket().outputIterator();
            while(playingIterator.hasNext()){
                BasicEvent e=playingIterator.next();
                BasicEvent oe=outItr.nextOutput();
                oe.copyFrom(e);
                oe.timestamp*=getTimeScale();
            }
            return getOutputPacket();
        }

        return in;
    }

    private void sampleEvents(Iterator<?> in) {
        
        while(in.hasNext()) {
            BasicEvent e=(BasicEvent)in.next();
            recordingIterator.writeToNextOutput(e);
            if (sampling = finishedSampling(e)) {
                log.info("finished sampling at "+e);
                break;
            }
        }
    }

    private boolean finishedSampling(BasicEvent e) {
        switch (getBufferType()) {
            case EventNumber:
                if (buffer.getSize() > getNumberOfEvents()) {
                    return true;
                }
                break;
            case TimeInterval:
                if (buffer.getLastTimestamp() > triggerTimestamp + getSampleTimeUs()) {
                    return true;
                }
                break;
            default:
        }
        return false;
    }

    private boolean isTrigger(BasicEvent e) {
        if(sampling) return false;
        if(playing) return false;
        triggerTimestamp=e.timestamp;
        return true;
    }

    @Override
    synchronized public void resetFilter() {
        buffer.clear();
        sampling=true;
        playing=false;
    }

    @Override
    public void initFilter() {
    }

    /**
     * @return the triggerType
     */
    public TriggerType getTriggerType() {
        return triggerType;
    }

    /**
     * @param triggerType the triggerType to set
     */
    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    /**
     * @return the bufferType
     */
    public BufferType getBufferType() {
        return bufferType;
    }

    /**
     * @param bufferType the bufferType to set
     */
    public void setBufferType(BufferType bufferType) {
        this.bufferType = bufferType;
    }

    /**
     * @return the numberOfEvents
     */
    public int getNumberOfEvents() {
        return numberOfEvents;
    }

    /**
     * @param numberOfEvents the numberOfEvents to set
     */
    public void setNumberOfEvents(int numberOfEvents) {
        this.numberOfEvents = numberOfEvents;
    }

    /**
     * @return the sampleTimeUs
     */
    public int getSampleTimeUs() {
        return sampleTimeUs;
    }

    /**
     * @param sampleTimeUs the sampleTimeUs to set
     */
    public void setSampleTimeUs(int sampleTimeUs) {
        this.sampleTimeUs = sampleTimeUs;
    }

    /**
     * @return the timeScale
     */
    public float getTimeScale() {
        return timeScale;
    }

    /**
     * @param timeScale the timeScale to set
     */
    public void setTimeScale(float timeScale) {
        this.timeScale = timeScale;
    }

}
