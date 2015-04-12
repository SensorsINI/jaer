/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
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
public class Oscilloscope extends EventFilter2D implements Observer {

    public enum TriggerType {

        TimeInterval, EventInterval, Manual, SpecialEvent
    };

    public enum BufferType {

        TimeInterval, EventNumber
    };

    public enum PlaybackType {

        EventNumber, TimeInterval;
    }

    protected TriggerType triggerType = TriggerType.valueOf(getString("triggerType", TriggerType.Manual.toString()));
    protected BufferType bufferType = BufferType.valueOf(getString("bufferType", BufferType.EventNumber.toString()));
    private PlaybackType playbackType = PlaybackType.valueOf(getString("playbackType", PlaybackType.TimeInterval.toString()));

    protected int numberOfEventsToCapture = getInt("numberOfEventsToCapture", 10000);
    protected int lengthOfTimeToCaptureUs = getInt("lengthOfTimeToCaptureUs", 1000);
    protected float playbackTimeScale = getInt("playbackTimeScale", 10);

    private int playbackNumEvents = getInt("playbackNumEvents", 100);
    private int playbackTimeIntervalUs = getInt("playbackTimeIntervalUs", 1000);

    private boolean manualTrigger = false;
    private int triggerTimestamp;
    
    private int lastPlaybackTimestamp=0;
    private int lastPlaybackEventNumber=0;

    private boolean sampling = false; // is new data being recorded?
    private boolean playing = false; // is recorded buffer being output still?
    private EventPacket<BasicEvent> scopeOutputPacket;
    private OutputEventIterator recordingIterator;
    private Iterator<BasicEvent> playingIterator;

    public Oscilloscope(AEChip chip) {
        super(chip);
        chip.addObserver(this);
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkOutputPacketEventType(in);
        if (sampling) {
            sampling = !sampleEvents(in.inputIterator(), recordingIterator);
        } else {
            Iterator<?> inputIterator = in.inputIterator();
            while (inputIterator.hasNext()) {
                BasicEvent e = (BasicEvent) inputIterator.next();
                if (isTrigger(e)) {
                    triggerTimestamp = e.timestamp;
                    recordingIterator = scopeOutputPacket.outputIterator();
                    recordingIterator.writeToNextOutput(e);
                    log.info("triggered on " + e.toString());
                    sampling = !sampleEvents(inputIterator, recordingIterator);
                }
            }
        }

        if (playingIterator == null) {
            playingIterator = scopeOutputPacket.inputIterator();
        }
        if (!sampling) {
            OutputEventIterator outItr = getOutputPacket().outputIterator();
            while (playingIterator.hasNext()) {
                BasicEvent e = playingIterator.next();
                BasicEvent oe = outItr.nextOutput();
                oe.copyFrom(e);
                oe.timestamp = triggerTimestamp + (int) (getPlaybackTimeScale() * (e.timestamp - triggerTimestamp));
            }
            return getOutputPacket();
        } else {
            return null;
        }

    }

    private boolean sampleEvents(Iterator<?> in, OutputEventIterator recordingIterator) {
        boolean finishedSampling = false;
        while (in.hasNext()) {
            BasicEvent e = (BasicEvent) in.next();
            recordingIterator.writeToNextOutput(e);
            if (finishedSampling = finishedSampling(e)) {
                log.info("finished sampling at " + e);
                break;
            }
        }
        return finishedSampling;
    }

    private boolean finishedSampling(BasicEvent e) {
        switch (getBufferType()) {
            case EventNumber:
                if (scopeOutputPacket.getSize() > getNumberOfEventsToCapture()) {
                    return true;
                }
                break;
            case TimeInterval:
                if (scopeOutputPacket.getLastTimestamp() > triggerTimestamp + getLengthOfTimeToCaptureUs()) {
                    return true;
                }
                break;
            default:
        }
        return false;
    }

    private boolean isTrigger(BasicEvent e) {
        switch (triggerType) {
            case Manual:
                if (manualTrigger) {
                    manualTrigger = false;
                    return true;
                }
                return false;
            case EventInterval:
                return false;
            case TimeInterval:
                return false;
            case SpecialEvent:
                if(e.isSpecial()) return true;
                return false;
            default:
                return false;
        }
    }

    @Override
    synchronized public void resetFilter() {
        scopeOutputPacket.clear();
        sampling = false;
        playing = false;
    }

    @Override
    public void initFilter() {
        this.scopeOutputPacket = new EventPacket<>(chip.getEventClass());
        this.recordingIterator = this.scopeOutputPacket.outputIterator();
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof AEChip && arg == AEChip.EVENT_NUM_CELL_TYPES) { // lazy construction, after the actual AEChip subclass has been constructed
            initFilter();
        }
    }

    public void doTrigger() {
        manualTrigger = true;
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
        putString("triggerType", triggerType.toString());
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
        putString("bufferType", bufferType.toString());
    }

    /**
     * @return the numberOfEventsToCapture
     */
    public int getNumberOfEventsToCapture() {
        return numberOfEventsToCapture;
    }

    /**
     * @param numberOfEventsToCapture the numberOfEventsToCapture to set
     */
    public void setNumberOfEventsToCapture(int numberOfEventsToCapture) {
        this.numberOfEventsToCapture = numberOfEventsToCapture;
        putInt("numberOfEventsToCapture", numberOfEventsToCapture);
    }

    /**
     * @return the lengthOfTimeToCaptureUs
     */
    public int getLengthOfTimeToCaptureUs() {
        return lengthOfTimeToCaptureUs;
    }

    /**
     * @param lengthOfTimeToCaptureUs the lengthOfTimeToCaptureUs to set
     */
    public void setLengthOfTimeToCaptureUs(int lengthOfTimeToCaptureUs) {
        this.lengthOfTimeToCaptureUs = lengthOfTimeToCaptureUs;
        putInt("lengthOfTimeToCaptureUs", lengthOfTimeToCaptureUs);
    }

    /**
     * @return the playbackTimeScale
     */
    public float getPlaybackTimeScale() {
        return playbackTimeScale;
    }

    /**
     * @param playbackTimeScale the playbackTimeScale to set
     */
    public void setPlaybackTimeScale(float playbackTimeScale) {
        this.playbackTimeScale = playbackTimeScale;
        putFloat("playbackTimeScale", playbackTimeScale);
    }

    /**
     * @return the playbackNumEvents
     */
    public int getPlaybackNumEvents() {
        return playbackNumEvents;
    }

    /**
     * @param playbackNumEvents the playbackNumEvents to set
     */
    public void setPlaybackNumEvents(int playbackNumEvents) {
        this.playbackNumEvents = playbackNumEvents;
        putInt("playbackNumEvents", playbackNumEvents);
    }

    /**
     * @return the playbackTimeIntervalUs
     */
    public int getPlaybackTimeIntervalUs() {
        return playbackTimeIntervalUs;
    }

    /**
     * @param playbackTimeIntervalUs the playbackTimeIntervalUs to set
     */
    public void setPlaybackTimeIntervalUs(int playbackTimeIntervalUs) {
        this.playbackTimeIntervalUs = playbackTimeIntervalUs;
        putInt("playbackTimeIntervalUs", playbackTimeIntervalUs);
    }

    /**
     * @return the playbackType
     */
    public PlaybackType getPlaybackType() {
        return playbackType;
    }

    /**
     * @param playbackType the playbackType to set
     */
    public void setPlaybackType(PlaybackType playbackType) {
        this.playbackType = playbackType;
        putString("playbackType",playbackType.toString());
    }

}
