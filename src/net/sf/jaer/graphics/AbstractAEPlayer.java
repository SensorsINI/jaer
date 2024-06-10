/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Base class for AEPlayers for playing back AER data files that implements some
 * parts of the interface.
 *
 * @author tobi
 *
 * This is part of jAER
 * <a href="http://jaerproject.net/">jaerproject.net</a>, licensed under the
 * LGPL (<a
 * href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public abstract class AbstractAEPlayer {

    protected AEViewer viewer = null;
    protected static Logger log = Logger.getLogger("AbstractAEPlayer");
    /**
     * The AE file input stream.
     */
    protected AEFileInputStreamInterface aeFileInputStream = null;
    /**
     * The input file.
     */
    protected File inputFile = null;
    public final PausePlayAction pausePlayAction = new PausePlayAction();
    public final PlayAction playAction = new PlayAction();
    public final PlayBackwardsAction playBackwardsAction = new PlayBackwardsAction();
    public final PauseAction pauseAction = new PauseAction();
    public final RewindAction rewindAction = new RewindAction();
    public final FasterAction fasterAction = new FasterAction();
    public final SlowerAction slowerAction = new SlowerAction();
    public final ReverseAction reverseAction = new ReverseAction();
    public final StepForwardAction stepForwardAction = new StepForwardAction();
    public final StepBackwardAction stepBackwardAction = new StepBackwardAction();
    public final JogForwardAction jogForwardAction = new JogForwardAction();
    public final JogBackwardAction jogBackwardAction = new JogBackwardAction();
    public final ToggleFlextimeAction toggleFlextimeAction = new ToggleFlextimeAction();
//    public final SyncPlaybackAction syncPlaybackAction = new SyncPlaybackAction();
    public final MarkInAction markInAction = new MarkInAction();
    public final MarkOutAction markOutAction = new MarkOutAction();
    public final ClearMarksAction clearMarksAction = new ClearMarksAction();

    /**
     * PropertyChangeEvent.
     */
    public static final String EVENT_PLAYBACKMODE = "playbackMode", EVENT_TIMESLICE_US = "timesliceUs",
            EVENT_PACKETSIZEEVENTS = "packetSizeEvents",
            EVENT_PLAYBACKDIRECTION = "playbackDirection", EVENT_PAUSED = "paused", EVENT_RESUMED = "resumed", EVENT_STOPPED = "stopped", EVENT_FILEOPEN = "fileopen", EVENT_REPEAT = "repeat"; // TODO not used yet in code

    /**
     * Creates new instance of AbstractAEPlayer and adds the viewer (if not
     * null) to the list of listeners.
     *
     * @param viewer must be instance of AEViewer.
     */
    public AbstractAEPlayer(AEViewer viewer) {
        if (viewer == null) {
            throw new RuntimeException("null AEViewer; you must control a viewer");
        }
        this.viewer = viewer;
        support.addPropertyChangeListener(viewer);
    }

    protected PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * Fires the following change events: (see EVENT_ public static final
     * Strings)
     * <ul>
     * <li> timesliceUs - when timeslice changes.
     * <li> packetSizeEvents - when packet size changes.
     * <li> playbackMode - when {@link #playbackMode} changes.
     * <li> playbackDirection - when {@link #playbackDirection} changes.
     * <li> paused - when paused.
     * <li> resumed - when resumed.
     * <li> stopped - when playback is stopped.
     * </ul>
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }

    /**
     * Adds a listener for property changes
     *
     * @param listener the listener
     * @see #getSupport()
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.support.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener
     *
     * @param listener the listener
     * @see #getSupport()
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.support.removePropertyChangeListener(listener);
    }

    /**
     * Flog for all pause/resume state.
     */
    volatile protected boolean paused = false; // multiple threads will access

    /**
     * Whether playback repeats after mark out or EOF is reached
     */
    volatile protected boolean repeat = viewer.prefs.getBoolean("AbstractAEPlayer.repeat", true); // multiple threads will access

    public abstract void setFractionalPosition(float fracPos);

    abstract public void setDoSingleStepEnabled(boolean b);

    abstract public void doSingleStep();

    /**
     * Returns the associated viewer.
     *
     * @return the viewer
     */
    public AEViewer getViewer() {
        return viewer;
    }

    /**
     * Use this method to set the viewer if it has changed since construction.
     *
     * @param viewer the viewer to set
     */
    public void setViewer(AEViewer viewer) {
        this.viewer = viewer;
    }

    public enum PlaybackMode {

        FixedTimeSlice, FixedPacketSize, RealTime
    }

    public enum PlaybackDirection {

        Forward, Backward
    }
    protected PlaybackMode playbackMode = PlaybackMode.FixedTimeSlice;
    protected PlaybackDirection playbackDirection = PlaybackDirection.Forward;
    protected int timesliceUs = 20000;
    protected int packetSizeEvents = 256;
    protected int jogPacketCount = viewer.prefs.getInt("AbstractAEPlayer.jogPacketCount", 20);
    protected int jogPacketsLeft = 0;
    protected boolean jogOccuring = false;

    abstract public void openAEInputFileDialog();

    abstract public AEPacketRaw getNextPacket(AbstractAEPlayer player);

    abstract public AEPacketRaw getNextPacket();
    private double SPEED_UP_SLOW_DOWN_FACTOR=Math.pow(2,0.25);
    
    /**
     * Speeds up the playback so that more time or more events are displayed per
     * slice.
     *
     */
    public void speedUp() {
        if (isFlexTimeEnabled()) {
            setPacketSizeEvents((int)Math.round(getPacketSizeEvents() * SPEED_UP_SLOW_DOWN_FACTOR));
        } else {
            long newTimeSlice = (long) Math.round(getTimesliceUs() * SPEED_UP_SLOW_DOWN_FACTOR);
            if (newTimeSlice > (long) Integer.MAX_VALUE) {
                newTimeSlice = Integer.MAX_VALUE; // clip to avoid negative slices sizes for slices > 2G us
            }
            setTimesliceUs((int) newTimeSlice);
        }
        log.info("new time and event slice durations are " + getTimesliceUs() + " us and " + getPacketSizeEvents() + "events");
    }

    /**
     * Slows down the playback so that less time or fewer events are displayed
     * per slice.
     *
     */
    public void slowDown() {
        if (isFlexTimeEnabled()) {
            setPacketSizeEvents((int)Math.round(getPacketSizeEvents() / SPEED_UP_SLOW_DOWN_FACTOR));
            if (getPacketSizeEvents() == 0) {
                setPacketSizeEvents(1);
            }
        } else {
            setTimesliceUs((int) Math.round(getTimesliceUs() / SPEED_UP_SLOW_DOWN_FACTOR));
            if (getTimesliceUs() == 0) {
                log.info("tried to reduce timeslice below 1us, clipped to 1us");
                setTimesliceUs(1);
            }
        }
        if (Math.abs(getPacketSizeEvents()) < 1) {
            setPacketSizeEvents((int) Math.signum(getPacketSizeEvents()));
        }
        if (Math.abs(getTimesliceUs()) < 1) {
            setTimesliceUs((int) Math.signum(getTimesliceUs()));
        }
//        log.info("new time and event slice durations are " + getTimesliceUs() + " us and " + getPacketSizeEvents() + "events");
    }

    public void jogForwards(int packets) {
        jogOccuring = true;
        jogPacketsLeft += packets;
    }

    public void jogBackwards(int packets) {
        jogOccuring = true;
        jogPacketsLeft -= packets;
    }

    public void cancelJog() {
        jogOccuring = false;
        jogPacketsLeft = 0;
    }

    /**
     * Should adjust the playback timeslice interval to approach real time
     * playback.
     */
    abstract public void adjustTimesliceForRealtimePlayback();

    public boolean isRealtimePlaybackEnabled() {
        return playbackMode == PlaybackMode.RealTime;
    }

    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    /**
     * Changes playback mode and fires PropertyChange EVENT_PLAYBACKMODE
     *
     * @param playbackMode
     */
    public void setPlaybackMode(PlaybackMode playbackMode) {
        PlaybackMode old = this.playbackMode;
        this.playbackMode = playbackMode;
        support.firePropertyChange(EVENT_PLAYBACKMODE, old, playbackMode);
    }

    public AEFileInputStreamInterface getAEInputStream() {
        return aeFileInputStream;
    }

    abstract public int getTime();

    abstract public boolean isChoosingFile();

    public boolean isPlayingForwards() {
        return getTimesliceUs() > 0;
    }

    abstract public void clearMarks();

    abstract public long setMarkIn();

    abstract public long setMarkOut();

    public void pause() {
        setPaused(true);
    }

    public void resume() {
        setPaused(false);
    }

    abstract public void rewind();

    /**
     * Returns state of playback paused.
     *
     * @return true if the playback is paused.
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Pauses/un-pauses playback. Fires property change "paused" or "resumed".
     *
     * @param yes true to pause, false to resume.
     */
    public void setPaused(boolean yes) {
        boolean old = paused;
        paused = yes;
        support.firePropertyChange(paused ? EVENT_PAUSED : EVENT_RESUMED, old, paused);
        if (yes) {
            pausePlayAction.setPlayAction();
        } else {
            pausePlayAction.setPauseAction();
        }
    }

    /**
     * Returns state of repeat.
     *
     * @return true if the playback is repeated.
     */
    public boolean isRepeat() {
        return repeat;
    }

    /**
     * repeats/unrepeats playback. Fires property change "repeatOn" or
     * "repeatOff".
     *
     * @param yes true to repeat, false to stop after playback.
     */
    public void setRepeat(boolean yes) {
        boolean old = this.repeat;
        this.repeat = yes;
        support.firePropertyChange(EVENT_REPEAT, old, this.repeat);
        viewer.prefs.putBoolean("AbstractAEPlayer.repeat", this.repeat);
    }

    abstract public void setTime(int time);

    /**
     * Opens an input stream and starts playing it.
     *
     * @param file the file to play.
     * @param progressMonitor to monitor progress and allow canceling it
     * @throws IOException if there is some problem opening file.
     * @throws java.lang.InterruptedException if a long-running open operation
     * is interrupted
     */
    abstract public void startPlayback(File file) throws IOException, InterruptedException;

    /**
     * Should close the input stream.
     */
    abstract public void stopPlayback();

    /**
     * Toggles the direction of playback
     */
    public void toggleDirection() {
        setPacketSizeEvents(getPacketSizeEvents() * -1);
        setTimesliceUs(getTimesliceUs() * -1);
        if (getPlaybackDirection() == PlaybackDirection.Forward) {
            setPlaybackDirection(PlaybackDirection.Backward);
        } else {
            setPlaybackDirection(PlaybackDirection.Forward);
        }
    }

    /**
     * Sets the playback direction
     *
     * @param forwards true for forwards, false for backwards
     */
    public void setDirectionForwards(boolean forwards) {
        int sign = forwards ? 1 : -1;
        setPacketSizeEvents(sign * Math.abs(getPacketSizeEvents()));
        setTimesliceUs(sign * Math.abs(getTimesliceUs()));
        setPlaybackDirection(PlaybackDirection.Forward);
    }

    /**
     * Sets the playback direction
     *
     * @param forwards true for backwards, false for forwards
     */
    public void setDirectionBackwards(boolean forwards) {
        int sign = forwards ? 1 : -1;
        setPacketSizeEvents(-sign * Math.abs(getPacketSizeEvents()));
        setTimesliceUs(-sign * Math.abs(getTimesliceUs()));
        setPlaybackDirection(PlaybackDirection.Backward);
    }

    /** Return the flextime packet size in events */
    public int getPacketSizeEvents() {
        return packetSizeEvents;
    }

    public void setPacketSizeEvents(int packetSizeEvents) {
        int old = this.packetSizeEvents;
        this.packetSizeEvents = packetSizeEvents;
        support.firePropertyChange(EVENT_PACKETSIZEEVENTS, old, packetSizeEvents);
    }

    public boolean isFlexTimeEnabled() {
        return playbackMode == PlaybackMode.FixedPacketSize;
    }

    public void setFlexTimeEnabled() {
        setPlaybackMode(PlaybackMode.FixedPacketSize);
    }

    public void setFixedTimesliceEnabled() {
        setPlaybackMode(PlaybackMode.FixedTimeSlice);
    }

    public boolean isRealtimeEnabled() {
        return playbackMode == PlaybackMode.RealTime;
    }

    public void setRealtimeEnabled() {
        setPlaybackMode(PlaybackMode.RealTime);
    }

    public void setTimesliceUs(int samplePeriodUs) {
        int old = this.timesliceUs;
        this.timesliceUs = samplePeriodUs;
        support.firePropertyChange(EVENT_TIMESLICE_US, old, timesliceUs);
//        log.info(this + "      set time slice =" + this.timesliceUs);
    }

    /**
     * Toggles between fixed packet size and fixed time slice. If mode is
     * RealTime, has no effect.
     *
     */
    void toggleFlexTime() {
        if (playbackMode == PlaybackMode.FixedPacketSize) {
            setFixedTimesliceEnabled();
        } else if (playbackMode == PlaybackMode.FixedTimeSlice) {
            setFlexTimeEnabled();
        } else {
            log.warning("cannot toggle flex time since we are in RealTime playback mode now");
        }
    }

    public int getTimesliceUs() {
        return timesliceUs;
    }

    public PlaybackDirection getPlaybackDirection() {
        return playbackDirection;
    }

    public void setPlaybackDirection(PlaybackDirection direction) {
        PlaybackDirection old = playbackDirection;

        this.playbackDirection = direction;
        support.firePropertyChange(EVENT_PLAYBACKDIRECTION, old, this.playbackDirection);
    }

    abstract public class MyAction extends AbstractAction {

        protected final String path = "/net/sf/jaer/graphics/icons/";

        public MyAction() {
            super();
        }

        public MyAction(String name, String icon) {
            putValue(Action.NAME, name);
            if (icon != null) {
                putValue(Action.SMALL_ICON, new javax.swing.ImageIcon(getClass().getResource(path + icon + ".gif")));
            }
            putValue("hideActionText", "true");
            putValue(Action.SHORT_DESCRIPTION, name);
        }

        protected void showAction() {
            if (viewer != null) {
                viewer.showActionText((String) getValue(Action.SHORT_DESCRIPTION));
            }
        }

        protected void showAction(String s) {
            if (viewer != null && s != null) {
                viewer.showActionText(s);
            }
        }
    }

    final public class PausePlayAction extends MyAction {

        final String pauseIcon = "Pause16", playIcon = "Play16";

        public PausePlayAction() {
            super("Pause", "Pause16");
        }

        public void actionPerformed(ActionEvent e) {
            showAction();
            if (isPaused()) {
                setPaused(false);
                setPauseAction();
            } else {
                setPaused(true);
                setPlayAction();
            }
        }

        protected void setPauseAction() {
            putValue(Action.NAME, "Pause");
            setIcon(pauseIcon);
            putValue(Action.SHORT_DESCRIPTION, "Pause");
        }

        protected void setPlayAction() {
            putValue(Action.NAME, "Play");
            setIcon(playIcon);
            putValue(Action.SHORT_DESCRIPTION, "Play");
        }

        private void setIcon(String icon) {
            putValue(Action.SMALL_ICON, new javax.swing.ImageIcon(getClass().getResource(path + icon + ".gif")));
        }
    }

    final public class ClearMarksAction extends MyAction {

        public ClearMarksAction() {
            super("Clear Marks", "ClearMarks16");
        }

        public void actionPerformed(ActionEvent e) {
            clearMarks();
            putValue(Action.SELECTED_KEY, true);
            putValue(Action.SHORT_DESCRIPTION, "Clear IN and OUT markers");
            showAction();
        }
    }

    final public class MarkInAction extends MyAction {

        public MarkInAction() {
            super("Mark IN", "MarkIn16");
        }

        public void actionPerformed(ActionEvent e) {
            showAction();
            setMarkIn();
            putValue(Action.SELECTED_KEY, true);
            putValue(Action.SHORT_DESCRIPTION, "Mark IN marker");
        }
    }

    final public class MarkOutAction extends MyAction {

        public MarkOutAction() {
            super("Mark OUT", "MarkOut16");
        }

        public void actionPerformed(ActionEvent e) {
            showAction();
            setMarkOut();
            putValue(Action.SELECTED_KEY, true);
            putValue(Action.SHORT_DESCRIPTION, "Set OUT marker");
        }
    }

    final public class PlayAction extends MyAction {

        public PlayAction() {
            super("Play", "Play16");
        }

        public void actionPerformed(ActionEvent e) {
            showAction();
            setPlaybackDirection(PlaybackDirection.Forward);
            setPaused(false);
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class PauseAction extends MyAction {

        public PauseAction() {
            super("Pause", "Pause16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
        }

        public void actionPerformed(ActionEvent e) {
            showAction();
            setPaused(true);
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class RewindAction extends MyAction {

        public RewindAction() {
            super("Rewind", "Rewind16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_R, 0));
        }

        public void actionPerformed(ActionEvent e) {
            showAction();
            rewind();
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class ReverseAction extends MyAction {

        public ReverseAction() {
            super("Reverse", "Reverse16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_B, 0));
        }

        public void actionPerformed(ActionEvent e) {
            showAction();
            toggleDirection();
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class PlayBackwardsAction extends MyAction {

        public PlayBackwardsAction() {
            super("Play Backwards", "PlayBackwards16");
        }

        public void actionPerformed(ActionEvent e) {
            showAction();
            setPlaybackDirection(PlaybackDirection.Backward);
            setPaused(false);
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class FasterAction extends MyAction {

        public FasterAction() {
            super("Play faster", "Faster16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F, 0));
        }

        public void actionPerformed(ActionEvent e) {
            speedUp();
            showAction(speedText(true));
            putValue(Action.SELECTED_KEY, true);
        }
    }

    private final EngineeringFormat engFmt = new EngineeringFormat();

    private final String speedText(boolean faster) {
        return String.format("DVS frames %s to %s%s", faster ? "increased" : "reduced",
                isFlexTimeEnabled() ? Integer.toString(getPacketSizeEvents()) : engFmt.format(1e-6f*getTimesliceUs()),
                isFlexTimeEnabled() ? " events" : "s"
        );
    }

    final public class SlowerAction extends MyAction {

        public SlowerAction() {
            super("Play slower", "Slower16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, 0));
        }

        public void actionPerformed(ActionEvent e) {
            slowDown();
            showAction(speedText(false));
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class StepForwardAction extends MyAction {

        public StepForwardAction() {
            super("Step forward", "StepForward16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, 0));
        }

        public void actionPerformed(ActionEvent e) {
            showAction();
            setDirectionForwards(true);
            doSingleStep();
//            if (viewer != null) {
//                viewer.interruptViewloop();
//            }
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class StepBackwardAction extends MyAction {

        public StepBackwardAction() {
            super("Step backward", "StepBack16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, 0));
        }

        public void actionPerformed(ActionEvent e) {
            showAction();
            setDirectionBackwards(true);
            doSingleStep();
//            if (viewer != null) {
//                viewer.interruptViewloop();
//            }
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class JogForwardAction extends MyAction {

        public JogForwardAction() {
            super("Jog forward", "StepForward16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        }

        public void actionPerformed(ActionEvent e) {
            showAction(String.format("Jog forwards %d",getJogPacketCount()));
            jogForwards(getJogPacketCount());
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class JogBackwardAction extends MyAction {

        public JogBackwardAction() {
            super("Jog backward", "StepBack16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        }

        public void actionPerformed(ActionEvent e) {
            showAction(String.format("Jog backwards %d",getJogPacketCount()));
            jogBackwards(getJogPacketCount());
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class ToggleFlextimeAction extends MyAction {

        public ToggleFlextimeAction() {
            super("ToggleFlextimeAction", null);
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_T, 0));
        }

        public void actionPerformed(ActionEvent e) {
            toggleFlexTime();
            String s=null;
            if(isFlexTimeEnabled()){
                s=String.format("Flextime: Constant-count DVS frames with %d events/frame",getPacketSizeEvents());
            }else{
                s=String.format("Flextime: Flextime: Constant-duration DVS frames with %ss/frame",engFmt.format(getTimesliceUs()*1e-6f));
            }
            showAction(s);
            putValue(Action.SELECTED_KEY, true);
        }
    }

//    final public class SyncPlaybackAction extends AbstractAction{
//        public SyncPlaybackAction (){
//            super("Synchronized playback");
//        }
//
//        public void actionPerformed (ActionEvent e){
//            log.info(e.toString());
//
//            if ( getViewer() == null ){
//                return;
//            }
//            getViewer().getJaerViewer().getToggleSyncEnabledAction().actionPerformed(e);
//        }
//    }
    @Override
    public String toString() {
        return String.format("AEPlayer paused=%s repeat=%s playBackDirection=%s playBackMode=%s timesliceUs=%d packetSizeEvents=%d ",
                paused, repeat, playbackDirection, playbackMode, timesliceUs, packetSizeEvents);
    }

    /**
     * @return the jogPacketCount
     */
    public int getJogPacketCount() {
        return this.jogPacketCount;
    }

    /**
     * @param jogPacketCount the jogPacketCount to set
     */
    public void setJogPacketCount(int jogPacketCount) {
        this.jogPacketCount = jogPacketCount;
        viewer.prefs.putInt("AbstractAEPlayer.jogPacketCount", jogPacketCount);
    }
}
