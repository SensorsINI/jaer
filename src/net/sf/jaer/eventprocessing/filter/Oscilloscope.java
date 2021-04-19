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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.EventPacket.InItr;
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
@Description("<html>A real-time oscilloscope, which can do instant replay of selected time or event slices during live or recorded playback in slow motion."
        + "<p>Trigger input provide possibilites for synchronizing on special events or bias changes.")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class Oscilloscope extends EventFilter2D implements Observer, FrameAnnotater, PropertyChangeListener {

    public enum TriggerType {

        TimeInterval, EventInterval, Manual, SpecialEvent, BiasChange
    };

    public enum CaptureType {

        TimeInterval, EventNumber
    };

    public enum PlaybackType {

        EventNumber, TimeInterval;
    }

    public enum TriggerMode {

        Auto, Normal
    }

    protected TriggerType triggerType = TriggerType.valueOf(getString("triggerType", TriggerType.Manual.toString()));
    protected CaptureType captureType = CaptureType.valueOf(getString("captureType", CaptureType.EventNumber.toString()));
    private PlaybackType playbackType = PlaybackType.valueOf(getString("playbackType", PlaybackType.TimeInterval.toString()));
    private TriggerMode triggerMode = TriggerMode.valueOf(getString("triggerMode", TriggerMode.Auto.toString()));

    protected int numberOfEventsToCapture = getInt("numberOfEventsToCapture", 10000);
    protected int lengthOfTimeToCaptureUs = getInt("lengthOfTimeToCaptureUs", 1000);

    private int playbackNumEvents = getInt("playbackNumEvents", 100);
    private int playbackTimeIntervalUs = getInt("playbackTimeIntervalUs", 1000);
    private int playbackNumberOfCycles = getInt("playbackNumberOfCycles", 3);

    private boolean manualTrigger = false;
    private int triggerTimestamp, eventsSinceLastTriggerCounter = 0;
    private boolean biasChanged = false;

    private int triggerTimeIntervalUs = getInt("triggerTimeIntervalUs", 1000000);
    private int triggerEventInterval = getInt("triggerEventInterval", 1000000);
    private int triggerSpecialEventRawAddress = getInt("triggerSpecialEventRawAddress", 0x80000000);

    private int lastPacketPlaybackEndTimestamp = 0;
    private int lastPacketPlaybackEndEventNumber = 0;
    private int nPlaybacks = 0;

    private boolean triggered = false; // is new data being recorded?
    private boolean playing = false; // is recorded buffer being output still?
    private EventPacket<BasicEvent> recordingPacket, capturedPacket, playbackPacket;

    private OutputEventIterator recordingIterator;
    private Iterator<BasicEvent> playbackIterator;

    private TextRenderer textRenderer = null;
    private String statusText = null;

    public Oscilloscope(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        String t = "Trigger", c = "Capture", p = "Playback";
        setPropertyTooltip(t, "triggerType", "<html><ul>"
                + "<li>Manual: Triggers on button press"
                + "<li>TimeInterval: on time interval triggerIntervalUs"
                + "<li>EventInterval: on every triggerTimeIntervalUs"
                + "<li>SpecialEvent: on every special event"
                + "<li>BiasChange: on every property change from the AEChip's hardware configuration"
                + "</ul>");
        setPropertyTooltip(t, "triggerMode", "<html><ul>"
                + "<li>Auto: Triggers again after playbackNumberOfCycles plays of last capture are played"
                + "<li>Normal: Triggers again only if trigger condition is not met, after replays of current capture have been played"
                + "</ul>");
        setPropertyTooltip(t, "triggerTimeIntervalUs", "time in us between triggers");
        setPropertyTooltip(t, "triggerEventInterval", "# events between triggers");
        setPropertyTooltip(t, "triggerSpecialEventRawAddress", "events with this raw address trigger");
        setPropertyTooltip(t, "doTrigger", "Manually trigger a capture");

        setPropertyTooltip(c, "captureType", "<html><ul>"
                + "<li>TimeInterval: capture time interval lengthOfTimeToCaptureUs in us"
                + "<li>EventInterval: capture numberOfEventsToCapture events"
                + "</ul>");
        setPropertyTooltip(c, "lengthOfTimeToCaptureUs", "duration in us to capture");
        setPropertyTooltip(c, "numberOfEventsToCapture", "number of events to capture");

        setPropertyTooltip(p, "playbackType", "<html><ul>"
                + "<li>EventNumber: play playbackNumEvents per rendered frame"
                + "<li>TimeInterval: play playbackTimeIntervalUs in us per rendered frame"
                + "</ul>");
        setPropertyTooltip(p, "playbackTimeIntervalUs", "duration in us to play per frame");
        setPropertyTooltip(p, "playbackNumEvents", "# events to play per frame");
        setPropertyTooltip(p, "playbackNumberOfCycles", "# times to play each capture");
        initFilter();
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (manualTrigger && in.getSize() > 0) {
            startRecording(in.getFirstTimestamp());
        }
        if (triggered) {
//            System.out.println(" recording additional " + in.getSize() + " events to "+recordingIterator.toString());
            triggered = recordEvents(in.inputIterator(), recordingIterator);
            if (!triggered) {
                startPlayback();
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
                    startRecording(e.timestamp);
//                    log.info("triggered, recording first " + in.getSize() + " events to "+recordingIterator.toString());
                    triggered = recordEvents(inputIterator, recordingIterator);
                    if (!triggered) {
                        startPlayback();
                        break;
                    }
                }
            }
        }

        if (playing) {
            if (playbackPacket == null) {
                playbackPacket = new EventPacket<>(recordingPacket.getEventClass());
            }
            if (playbackIterator == null) {
                playbackIterator = capturedPacket.inputIterator();
            }
            OutputEventIterator outItr = playbackPacket.outputIterator();
            int nPlaybackEventsThisPacket = 0;
            int startTimestamp = lastPacketPlaybackEndTimestamp;
            boolean breakout = false;
            while (playbackIterator.hasNext() && !breakout) {
                if (!playbackIterator.hasNext()) {
                    playbackIterator = capturedPacket.inputIterator();
                    break;
                }
                BasicEvent e = playbackIterator.next();
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
            statusText = String.format("playing %d events at %s", playbackPacket.getSize(), playbackIterator.toString());
            if(playing && triggerType==TriggerType.BiasChange && biasChanged){
                playing=false;
                biasChanged=false;
            }
            if (!playbackIterator.hasNext()) {
                if (++nPlaybacks < playbackNumberOfCycles) {
                    playbackIterator = capturedPacket.inputIterator();
                    lastPacketPlaybackEndTimestamp = recordingPacket.getFirstTimestamp();
                } else {
                    playing = false;
                }
            } else {
                lastPacketPlaybackEndTimestamp = playbackPacket.getLastTimestamp();
            }
            return playbackPacket;
        } else {
            return in;
        }

    }

    private void startRecording(int timestamp) {
        playing = false;
        triggered = true;
        manualTrigger = false;
        triggerTimestamp = timestamp;
        EventPacket tmp = recordingPacket;
        recordingPacket = capturedPacket;
        capturedPacket = tmp;
        recordingIterator = recordingPacket.outputIterator(); // clears recording packet

    }

    private void startPlayback() {
        playing = true;
        nPlaybacks = 0;
        EventPacket tmp = recordingPacket;
        recordingPacket = capturedPacket;
        capturedPacket = tmp;
        playbackIterator = capturedPacket.inputIterator();
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
            BasicEvent oe = recordingIterator.nextOutput();
            oe.copyFrom(e);
            if (finishedSampling = isFinishedRecording(e, recordingPacket, triggerTimestamp)) {
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
    private boolean isFinishedRecording(BasicEvent e, EventPacket recordingPacket, int triggerTimestamp) {
        switch (getCaptureType()) {
            case EventNumber:
                if (recordingPacket.getSize() > getNumberOfEventsToCapture()) {
//                    log.info(getCaptureType().toString() + ": recorded " + recordingPacket.getSize() + " events");
                    return true;
                }
                break;
            case TimeInterval:
                if (recordingPacket.getLastTimestamp() > triggerTimestamp + getLengthOfTimeToCaptureUs()) {
//                    log.info(getCaptureType().toString() + ": recorded " + recordingPacket.getSize() + " events over " + recordingPacket.getDurationUs() + " us");
                    return true;
                }
                break;
            default:
        }
        return false;
    }

    private boolean isTrigger(BasicEvent e) {
        eventsSinceLastTriggerCounter++;
        if (triggerMode == TriggerMode.Auto) {
            return true;
        }
        switch (triggerType) {
            case Manual:
                if (manualTrigger) {
                    eventsSinceLastTriggerCounter = 0;
                    manualTrigger = false;
                    return true;
                }
                return false;
            case EventInterval:
                if (eventsSinceLastTriggerCounter >= triggerEventInterval) {
                    eventsSinceLastTriggerCounter = 0;
                    return true;
                }
                return false;
            case TimeInterval:
                if (e.timestamp - triggerTimestamp > triggerTimeIntervalUs) {
                    eventsSinceLastTriggerCounter = 0;
                    return true;
                }
                return false;
            case SpecialEvent:
                if (e.isSpecial() && e.address == triggerSpecialEventRawAddress) {
                    eventsSinceLastTriggerCounter = 0;
                    return true;
                }
                return false;
            case BiasChange:
                if (biasChanged) {
                    biasChanged = false;
                    return true;
                }
                return false;

            default:
                return false;
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getSource() instanceof AEChip){
            biasChanged=true;
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
        this.capturedPacket = new EventPacket<>(chip.getEventClass());
        recordingPacket.allocate(numberOfEventsToCapture);
        capturedPacket.allocate(numberOfEventsToCapture);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof AEChip && chip.getBiasgen() != null) {
            chip.getBiasgen().getSupport().addPropertyChangeListener(this);
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 10));
        }
        if (playing) {
            statusText = String.format("playing %d events at %s", playbackPacket.getSize(), playbackIterator.toString());
            gl.glColor3f(1, 1, 0);
            gl.glRectf(0, -8, chip.getSizeX() * (float) ((InItr) playbackIterator).getCursor() / capturedPacket.getSize(), -7);
        } else if (triggered) {
            statusText = String.format("triggered: %d events", recordingPacket.getSize());
             gl.glColor3f(0, 1, 0);
            float frac = 0;
            if (captureType == CaptureType.EventNumber) {
                frac = (float) recordingPacket.getSize() / numberOfEventsToCapture;
            } else if (captureType == CaptureType.TimeInterval) {
                frac = (float) recordingPacket.getDurationUs() / lengthOfTimeToCaptureUs;
            }
            gl.glRectf(0, -8, chip.getSizeX() * frac, -7);
        } else {
            statusText = String.format("live");
            gl.glColor3f(1, 0, 0);
        }

        if (statusText != null) {
            textRenderer.begin3DRendering();
            if(triggered)
                       textRenderer.setColor(Color.green);
            else if(playing)
                textRenderer.setColor(Color.yellow);
            else 
                textRenderer.setColor(Color.red);

            textRenderer.draw3D(statusText, 0, -5, 0, .5f);
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
     * @return the playbackNumEvents
     */
    public int getPlaybackNumEvents() {
        return playbackNumEvents;
    }

    /**
     * @param playbackNumEvents the playbackNumEvents to set
     */
    public void setPlaybackNumEvents(int playbackNumEvents) {
        if (playbackNumEvents < 1) {
            playbackNumEvents = 1;
        }
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

    /**
     * @return the triggerTimeIntervalUs
     */
    public int getTriggerTimeIntervalUs() {
        return triggerTimeIntervalUs;
    }

    /**
     * @param triggerTimeIntervalUs the triggerTimeIntervalUs to set
     */
    public void setTriggerTimeIntervalUs(int triggerTimeIntervalUs) {
        this.triggerTimeIntervalUs = triggerTimeIntervalUs;
        putInt("triggerTimeIntervalUs", triggerTimeIntervalUs);
    }

    /**
     * @return the triggerEventInterval
     */
    public int getTriggerEventInterval() {
        return triggerEventInterval;
    }

    /**
     * @param triggerEventInterval the triggerEventInterval to set
     */
    public void setTriggerEventInterval(int triggerEventInterval) {
        this.triggerEventInterval = triggerEventInterval;
        putInt("triggerEventInterval", triggerEventInterval);
    }

    /**
     * @return the triggerSpecialEventRawAddress
     */
    public int getTriggerSpecialEventRawAddress() {
        return triggerSpecialEventRawAddress;
    }

    /**
     * @param triggerSpecialEventRawAddress the triggerSpecialEventRawAddress to
     * set
     */
    public void setTriggerSpecialEventRawAddress(int triggerSpecialEventRawAddress) {
        this.triggerSpecialEventRawAddress = triggerSpecialEventRawAddress;
        putInt("triggerSpecialEventRawAddress", triggerSpecialEventRawAddress);
    }

    /**
     * @return the triggerMode
     */
    public TriggerMode getTriggerMode() {
        return triggerMode;
    }

    /**
     * @param triggerMode the triggerMode to set
     */
    public void setTriggerMode(TriggerMode triggerMode) {
        this.triggerMode = triggerMode;
        putString("triggerMode", triggerMode.toString());
    }

    /**
     * @return the playbackNumberOfCycles
     */
    public int getPlaybackNumberOfCycles() {
        return playbackNumberOfCycles;
    }

    /**
     * @param playbackNumberOfCycles the playbackNumberOfCycles to set
     */
    public void setPlaybackNumberOfCycles(int playbackNumberOfCycles) {
        if (playbackNumberOfCycles < 1) {
            playbackNumberOfCycles = 1;
        }
        this.playbackNumberOfCycles = playbackNumberOfCycles;
        putInt("playbackNumberOfCycles", playbackNumberOfCycles);
    }

}
