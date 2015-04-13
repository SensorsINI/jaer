/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Color;
import java.awt.Font;
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
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * A real-time oscilloscope, which can play back selected time or event slices
 * during live or recorded playback.
 *
 * It works a bit like a scope: It waits for a trigger, then starts recording
 * events into a recording packet. During recording, live data is shown in real
 * time.
 *
 * Once the recordingPacket is full, the playback goes to slow motion replay,
 * where instead of returning the input packet, the recordingPacket is returned
 * in small chunks, either in time or event number, until it is completely
 * played out. Then the recording is rewound to start and played again.
 *
 * In the meantime, the filterPacket waits for another trigger.
 *
 * Triggers can be manual, via Trigger button, or by a special event, e.g. from
 * external input pin, or periodic by event number or by time interval.
 *
 *
 *
 * @author tobi
 */
@Description("<html>A real-time oscilloscope, which can play back selected time or event slices during live or recorded playback in slow motion."
        + "<p>Trigger input provide possibilites for synchronizing on special events.")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class Oscilloscope extends EventFilter2D implements Observer, FrameAnnotater {

    public enum TriggerType {

        TimeInterval, EventInterval, Manual, SpecialEvent
    };

    public enum CaptureType {

        TimeInterval, EventNumber
    };

    public enum PlaybackType {

        EventNumber, TimeInterval;
    }

    protected TriggerType triggerType = TriggerType.valueOf(getString("triggerType", TriggerType.Manual.toString()));
    protected CaptureType captureType = CaptureType.valueOf(getString("captureType", CaptureType.EventNumber.toString()));
    private PlaybackType playbackType = PlaybackType.valueOf(getString("playbackType", PlaybackType.TimeInterval.toString()));

    protected int numberOfEventsToCapture = getInt("numberOfEventsToCapture", 10000);
    protected int lengthOfTimeToCaptureUs = getInt("lengthOfTimeToCaptureUs", 1000);
    protected float playbackTimeScale = getInt("playbackTimeScale", 10);

    private int playbackNumEvents = getInt("playbackNumEvents", 100);
    private int playbackTimeIntervalUs = getInt("playbackTimeIntervalUs", 1000);

    private boolean manualTrigger = false;
    private int triggerTimestamp;

    private int lastPacketPlaybackEndTimestamp = 0;
    private int lastPacketPlaybackEndEventNumber = 0;

    private boolean triggered = false; // is new data being recorded?
    private boolean playing = false; // is recorded buffer being output still?
    private EventPacket<BasicEvent> recordingPacket, playbackPacket;
    private OutputEventIterator recordingIterator;
    private Iterator<BasicEvent> playingIterator;

    private TextRenderer textRenderer = null;
    private String statusText = null;

    public Oscilloscope(AEChip chip) {
        super(chip);
        chip.addObserver(this);
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (triggered) {
//            System.out.println(" recording additional " + in.getSize() + " events to "+recordingIterator.toString());
            triggered = recordEvents(in.inputIterator(), recordingIterator);
            if (!triggered) {
                playing = true;
            }
        } else if (!playing) {
            Iterator<?> inputIterator = in.inputIterator();
            while (inputIterator.hasNext()) {
                BasicEvent e = (BasicEvent) inputIterator.next();
                if (e.isSpecial()) {
                    continue;
                }
                if (e.isFilteredOut()) {
                    continue;
                }
                if (isTrigger(e)) {
                    triggerTimestamp = e.timestamp;
                    recordingIterator = recordingPacket.outputIterator(); // clears recording packet
//                    log.info("triggered, recording first " + in.getSize() + " events to "+recordingIterator.toString());
                    triggered = recordEvents(inputIterator, recordingIterator);
                    if (!triggered) {
                        playing = true;
                        break;
                    }
                }
            }
        }

        if (playing) {
            if(playbackPacket==null){
                playbackPacket=new EventPacket<>(recordingPacket.getEventClass());
            }
            if (playingIterator == null) {
                playingIterator = recordingPacket.inputIterator();
            }
            OutputEventIterator outItr = playbackPacket.outputIterator();
            int nPlaybackEventsThisPacket = 0;
            int startTimestamp = lastPacketPlaybackEndTimestamp;
            boolean breakout = false;
            while (playingIterator.hasNext() && !breakout) {
                if (!playingIterator.hasNext()) {
                    playingIterator = recordingPacket.inputIterator();
                    break;
                }
                BasicEvent e = playingIterator.next();
                if (e.isSpecial()) {
                    continue;
                }
                if (e.isFilteredOut()) {
                    continue;
                }
                BasicEvent oe = outItr.nextOutput();
                oe.copyFrom(e);
                nPlaybackEventsThisPacket++;

//                oe.timestamp = triggerTimestamp + (int) (getPlaybackTimeScale() * (e.timestamp - triggerTimestamp));
                switch (playbackType) {
                    case EventNumber:
                        if (nPlaybackEventsThisPacket >= playbackNumEvents) {
                            breakout = true;
                        }
                        break;
                    case TimeInterval:
                        if (e.timestamp - startTimestamp > playbackTimeIntervalUs) {
                            breakout = true;
                        }
                        break;
                    default:
                }
            }
            statusText = String.format("playing %d events at %s", playbackPacket.getSize(), playingIterator.toString());
            if (!playingIterator.hasNext()) {
                playingIterator = recordingPacket.inputIterator();
            }
            lastPacketPlaybackEndTimestamp = getOutputPacket().getLastTimestamp();
            return playbackPacket;
        } else {
            if (triggered) {
                statusText = String.format("triggered: %d events", recordingPacket.getSize());
            } else {
                statusText = String.format("live");
            }
            return in;
        }

    }

    /**
     * Returns true if sampling (recording) should continue
     *
     * @param inItr iterator for incoming events
     * @param recordingIterator iterator for recorded events
     * @return true if sampling (recording) should continue
     */
    private boolean recordEvents(Iterator<?> inItr, OutputEventIterator recordingIterator) {
        boolean finishedSampling = false;
        while (inItr.hasNext()) {
            BasicEvent e = (BasicEvent) inItr.next();
            if (e.isSpecial()) {
                continue;
            }
            if (e.isFilteredOut()) {
                continue;
            }
            recordingIterator.writeToNextOutput(e);
            if (finishedSampling = isFinishedRecording(e)) {
                break;
            }
        }
        return !finishedSampling;
    }

    /**
     * Determines if recording is finished
     *
     * @param e
     * @return true if finished
     */
    private boolean isFinishedRecording(BasicEvent e) {
        switch (getCaptureType()) {
            case EventNumber:
                if (recordingPacket.getSize() > getNumberOfEventsToCapture()) {
                    log.info(getCaptureType().toString() + ": recorded " + recordingPacket.getSize() + " events");
                    return true;
                }
                break;
            case TimeInterval:
                if (recordingPacket.getLastTimestamp() > triggerTimestamp + getLengthOfTimeToCaptureUs()) {
                    log.info(getCaptureType().toString() + ": recorded " + recordingPacket.getSize() + " events over " + recordingPacket.getDurationUs() + " us");
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
                if (e.isSpecial()) {
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    synchronized public void resetFilter() {
        recordingPacket.clear();
        triggered = false;
        playing = false;
    }

    @Override
    public void initFilter() {
        this.recordingPacket = new EventPacket<>(chip.getEventClass());
        recordingPacket.allocate(numberOfEventsToCapture);
        this.recordingIterator = this.recordingPacket.outputIterator();
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof AEChip && arg == AEChip.EVENT_NUM_CELL_TYPES) { // lazy construction, after the actual AEChip subclass has been constructed
            initFilter();
        }
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 10));
        }
        if (statusText != null) {
            textRenderer.begin3DRendering();
            textRenderer.setColor(Color.yellow);
            textRenderer.draw3D(statusText, 0, 0, 0, .5f);
            textRenderer.end3DRendering();
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
     * @return the captureType
     */
    public CaptureType getCaptureType() {
        return captureType;
    }

    /**
     * @param captureType the captureType to set
     */
    public void setCaptureType(CaptureType captureType) {
        this.captureType = captureType;
        putString("captureType", captureType.toString());
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
        recordingPacket.allocate(numberOfEventsToCapture);
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
        putString("playbackType", playbackType.toString());
    }

}
