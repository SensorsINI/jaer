/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * AePlayerAdvancedControlsPanel.java
 *
 * Created on Aug 3, 2009, 12:34:34 AM
 */
package net.sf.jaer.graphics;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Hashtable;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.graphics.AbstractAEPlayer.PlaybackMode;

/**
 * All the controls for playback are in this GUI.
 *
 * @author tobi
 */
public class AePlayerAdvancedControlsPanel extends javax.swing.JPanel implements PropertyChangeListener {

    static final Logger log = Logger.getLogger("AEViewerPlaybackControlDialog");
    private AbstractAEPlayer aePlayer;
    private final AEViewer aeViewer;
    MoreLessAction moreLessAction = new MoreLessAction();
    private volatile boolean sliderDontProcess = false; // semaphore used to prevent slider actions when slider is set programmatically
    private final Hashtable<Integer, JLabel> markTable = new Hashtable<Integer, JLabel>(); // lookup from slider position to label, given to slider to draw labels at markers
    private final JLabel markInLabel, markOutLabel;
    private Integer markInPosition = null, markOutPosition = null; // store keys in markTable so we can remove them
    private JPopupMenu markerPopupMenu=null;

    /**
     * Creates new form AePlayerAdvancedControlsPanel.
     *
     * @param viewer the viewer to control.
     */
    public AePlayerAdvancedControlsPanel(AEViewer viewer) {
        this.markOutLabel = new JLabel("]");
        this.markInLabel = new JLabel("[");
        markInLabel.setToolTipText("IN marker");
        markOutLabel.setToolTipText("OUT marker");
        this.aeViewer = viewer;
        this.aePlayer = viewer.getAePlayer();
        initComponents();
        setAePlayer(viewer.getAePlayer()); // TODO double set needed because aePlayer is needed in initComponents and we still need to do more component binding in setAePlayer
        moreControlsPanel.setVisible(false);
        markerPopupMenu=new JPopupMenu("Markers");
        markerPopupMenu.add(aePlayer.markInAction);
        markerPopupMenu.add(aePlayer.markOutAction);
        markerPopupMenu.add(aePlayer.clearMarksAction);
        playerSlider.setComponentPopupMenu(markerPopupMenu);
//        playerSlider.setExtent(100);
        repeatPlaybackButton.setSelected(aePlayer.isRepeat());
    }
    
    /** Utility method to find out if the slider is being manipulated, so that event filters and other processing can be 
     * turned off for better responsiveness.
     * @return true if the slider is currently being manipulated.
     */
    public boolean isSliderBeingAdjusted(){
        return playerSlider.getValueIsAdjusting();
    }

    /**
     * Use this method to add this to the AEFileInputStream listener list for
     * position updates.
     *
     * @param is the input stream.
     */
    public void addMeToPropertyChangeListeners(AEFileInputStreamInterface is) {
//        is.getSupport().addPropertyChangeListener(AEInputStream.EVENT_MARK_IN_SET, this);
//        is.getSupport().addPropertyChangeListener(AEInputStream.EVENT_MARK_OUT_SET, this);
//        is.getSupport().addPropertyChangeListener(AEInputStream.EVENT_MARKS_CLEARED, this);
//        is.getSupport().addPropertyChangeListener(AEInputStream.EVENT_POSITION, this);
        is.getSupport().addPropertyChangeListener(this);
    }

    /**
     * Messages come back here from e.g. programmatic state changes, like a new
     * aePlayer file position. This methods sets the GUI components to a
     * consistent state, using a flag to tell the slider that it has not been
     * set by a user mouse action
     */
    public void propertyChange(PropertyChangeEvent evt) {
        try {
            if (evt.getSource() instanceof AEFileInputStreamInterface) {
                if (evt.getPropertyName().equals(AEInputStream.EVENT_POSITION)) { // comes from AEFileInputStream
                    sliderDontProcess = true;
                    // note this cool semaphore/flag trick to avoid processing the
                    // event generated when we programmatically set the slider position here
                    playerSlider.setValue(Math.round(getFractionalPosition() * playerSlider.getMaximum()));
                    if (moreControlsPanel.isVisible() || aeViewer.aePlayer.getAEInputStream() != null) {
                        eventField.setText(Long.toString(aeViewer.aePlayer.position()));
                        timeField.setText(Integer.toString(aeViewer.aePlayer.getTime()));
                    }
                } else if (evt.getPropertyName().equals(AEInputStream.EVENT_MARK_IN_SET)) {
                    synchronized (aePlayer) {
                        if (markInPosition != null) {
                            markTable.remove(markInPosition);
                        }
                        markInPosition = playerSlider.getValue();
                        markTable.put(markInPosition, markInLabel);
                        playerSlider.setLabelTable(markTable);
                        playerSlider.setPaintLabels(true);
                    }
                } else if (evt.getPropertyName().equals(AEInputStream.EVENT_MARK_OUT_SET)) {
                    synchronized (aePlayer) {
                        if (markOutPosition != null) {
                            markTable.remove(markOutPosition);
                        }
                        markOutPosition = playerSlider.getValue();
                        markTable.put(markOutPosition, markOutLabel);
                        playerSlider.setLabelTable(markTable);
                        playerSlider.setPaintLabels(true);
                    }
                } else if (evt.getPropertyName().equals(AEInputStream.EVENT_MARKS_CLEARED)) {
                    playerSlider.setPaintLabels(false);
                    markTable.clear();
                    markInPosition = null;
                    markOutPosition = null;
                }
            } else if (evt.getPropertyName().equals(AbstractAEPlayer.EVENT_TIMESLICE_US)) { // TODO replace with public static Sttring
                timesliceSpinner.setValue(aePlayer.getTimesliceUs());
            } else if (evt.getPropertyName().equals(AbstractAEPlayer.EVENT_PACKETSIZEEVENTS)) {
                packetSizeSpinner.setValue(aePlayer.getPacketSizeEvents());
            } else if (evt.getPropertyName().equals(AbstractAEPlayer.EVENT_PAUSED)) {
                aePlayer.pausePlayAction.setPlayAction();
            } else if (evt.getPropertyName().equals(AbstractAEPlayer.EVENT_RESUMED)) {
                aePlayer.pausePlayAction.setPauseAction();
            }else if (evt.getPropertyName().equals(AbstractAEPlayer.EVENT_REPEAT)) {
                repeatPlaybackButton.setSelected((boolean)evt.getNewValue());
            }
        } catch (Throwable t) {
            log.warning("caught error in player control panel - probably another thread is modifying the text field at the same time: " + t.toString());
        }
    }
        

    public JCheckBox getSyncPlaybackCheckBox() {
        return syncPlaybackCheckBox;
    }

    private float getFractionalPosition() {
        if (aeViewer.aePlayer.getAEInputStream() == null) {
            log.warning("AEViewer.AEPlayer.getFractionalPosition: null fileAEInputStream, returning 0");
            return 0;
        }
        float fracPos = aeViewer.aePlayer.getAEInputStream().getFractionalPosition();
        return fracPos;
    }

    /**
     * @return the playerSlider
     */
    public javax.swing.JSlider getPlayerSlider() {
        return playerSlider;
    }

    /**
     * Use to set player if player changes, e.g. when SyncPlayer replaces player
     * for player functionality.
     *
     * @param aePlayer the aePlayer to set
     */
    public void setAePlayer(AbstractAEPlayer aePlayer) {
        if (this.aePlayer != null) {
            this.aePlayer.getSupport().removePropertyChangeListener(this);
        }
        this.aePlayer = aePlayer;
        aePlayer.getSupport().addPropertyChangeListener(this);
        switch (aePlayer.getPlaybackMode()) {
            case FixedPacketSize:
                fixedPacketSizeButton.setSelected(true);
                break;
            case FixedTimeSlice:
                fixedTimeSliceButton.setSelected(true);
                break;
            case RealTime:
                realtimeButton.setSelected(true);
        }
        timesliceSpinner.setValue(aePlayer.getTimesliceUs());
        packetSizeSpinner.setValue(aePlayer.getPacketSizeEvents());

        pauseButton.setAction(aePlayer.pausePlayAction);
        playForwardsButton.setAction(aePlayer.playAction);
        reverseButton.setAction(aePlayer.reverseAction);
        playBackwardsButton.setAction(aePlayer.playBackwardsAction);
        playFasterButton.setAction(aePlayer.fasterAction);
        playSlowerButton.setAction(aePlayer.slowerAction);
        stepBackwardsButon.setAction(aePlayer.stepBackwardAction);
        stepForwardsButton.setAction(aePlayer.stepForwardAction);
        rewindButton.setAction(aePlayer.rewindAction);
        clearMarksB.setAction(aePlayer.clearMarksAction);
        setInB.setAction(aePlayer.markInAction);
        setOutB.setAction(aePlayer.markOutAction);

        showMoreControlsButton.setAction(moreLessAction);

    }

    /**
     * The action that shows more or less controls.
     */
    public class MoreLessAction extends AbstractAction {

        public MoreLessAction() {
            putValue(Action.NAME, "More");
            putValue(Action.SHORT_DESCRIPTION, "Shows more or less controls for playback");
        }

        public void actionPerformed(ActionEvent e) {
            moreControlsPanel.setVisible(!moreControlsPanel.isVisible());
            if (moreControlsPanel.isVisible()) {
                putValue(Action.NAME, "Less");
            } else {
                putValue(Action.NAME, "More");
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        controlsPanel = new javax.swing.JPanel();
        sliderPanel = new javax.swing.JPanel();
        playerSlider = new javax.swing.JSlider();
        showMoreControlsButton = new javax.swing.JButton();
        moreControlsPanel = new javax.swing.JPanel();
        playerControlPanel = new javax.swing.JPanel();
        pauseButton = new javax.swing.JButton();
        playForwardsButton = new javax.swing.JButton();
        reverseButton = new javax.swing.JButton();
        playBackwardsButton = new javax.swing.JButton();
        playFasterButton = new javax.swing.JButton();
        playSlowerButton = new javax.swing.JButton();
        stepForwardsButton = new javax.swing.JButton();
        stepBackwardsButon = new javax.swing.JButton();
        rewindButton = new javax.swing.JButton();
        clearMarksB = new javax.swing.JButton();
        setInB = new javax.swing.JButton();
        setOutB = new javax.swing.JButton();
        repeatPlaybackButton = new javax.swing.JToggleButton();
        jPanel3 = new javax.swing.JPanel();
        playerStatusPanel = new javax.swing.JPanel();
        timeField = new javax.swing.JTextField();
        timeFieldLabel = new javax.swing.JLabel();
        eventField = new javax.swing.JTextField();
        eventFieldLabel = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        playbackModePanel = new javax.swing.JPanel();
        fixedTimeSliceButton = new javax.swing.JRadioButton();
        fixedPacketSizeButton = new javax.swing.JRadioButton();
        realtimeButton = new javax.swing.JRadioButton();
        timesliceSpinner = new javax.swing.JSpinner();
        msLabel = new javax.swing.JLabel();
        packetSizeSpinner = new javax.swing.JSpinner();
        eventsLabel = new javax.swing.JLabel();
        syncPanel = new javax.swing.JPanel();
        syncPlaybackCheckBox = new javax.swing.JCheckBox();

        setLayout(new java.awt.BorderLayout());

        controlsPanel.setLayout(new javax.swing.BoxLayout(controlsPanel, javax.swing.BoxLayout.Y_AXIS));

        sliderPanel.setAlignmentX(0.0F);
        sliderPanel.setPreferredSize(new java.awt.Dimension(100, 40));
        sliderPanel.setLayout(new javax.swing.BoxLayout(sliderPanel, javax.swing.BoxLayout.LINE_AXIS));

        playerSlider.setMaximum(1000);
        playerSlider.setToolTipText("Shows and controls playback position (in events, not time)");
        playerSlider.setValue(0);
        playerSlider.setMaximumSize(new java.awt.Dimension(800, 25));
        playerSlider.setPreferredSize(new java.awt.Dimension(600, 25));
        playerSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                playerSliderStateChanged(evt);
            }
        });
        playerSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                playerSliderMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                playerSliderMouseReleased(evt);
            }
        });
        sliderPanel.add(playerSlider);

        showMoreControlsButton.setAction(moreLessAction);
        showMoreControlsButton.setToolTipText("");
        sliderPanel.add(showMoreControlsButton);

        controlsPanel.add(sliderPanel);

        moreControlsPanel.setAlignmentX(0.0F);
        moreControlsPanel.setLayout(new javax.swing.BoxLayout(moreControlsPanel, javax.swing.BoxLayout.Y_AXIS));

        playerControlPanel.setToolTipText("playback controls");
        playerControlPanel.setAlignmentX(0.0F);
        playerControlPanel.setPreferredSize(new java.awt.Dimension(600, 40));
        playerControlPanel.setLayout(new javax.swing.BoxLayout(playerControlPanel, javax.swing.BoxLayout.LINE_AXIS));

        pauseButton.setAction(aePlayer.pausePlayAction);
        pauseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/Pause16.gif"))); // NOI18N
        pauseButton.setToolTipText("");
        pauseButton.setHideActionText(true);
        pauseButton.setIconTextGap(2);
        pauseButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(pauseButton);

        playForwardsButton.setAction(aePlayer.playAction);
        playForwardsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/Play16.gif"))); // NOI18N
        playForwardsButton.setToolTipText("Play forwards");
        playForwardsButton.setHideActionText(true);
        playForwardsButton.setIconTextGap(2);
        playForwardsButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(playForwardsButton);

        reverseButton.setAction(aePlayer.reverseAction);
        reverseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/Reverse16.gif"))); // NOI18N
        reverseButton.setToolTipText("");
        reverseButton.setHideActionText(true);
        reverseButton.setIconTextGap(2);
        reverseButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(reverseButton);

        playBackwardsButton.setAction(aePlayer.playBackwardsAction);
        playBackwardsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/PlayBackwards16.gif"))); // NOI18N
        playBackwardsButton.setToolTipText("Play backwards");
        playBackwardsButton.setHideActionText(true);
        playBackwardsButton.setIconTextGap(2);
        playBackwardsButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(playBackwardsButton);

        playFasterButton.setAction(aePlayer.fasterAction);
        playFasterButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/Faster16.gif"))); // NOI18N
        playFasterButton.setToolTipText("Faster");
        playFasterButton.setHideActionText(true);
        playFasterButton.setIconTextGap(2);
        playFasterButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(playFasterButton);

        playSlowerButton.setAction(aePlayer.slowerAction);
        playSlowerButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/Slower16.gif"))); // NOI18N
        playSlowerButton.setToolTipText("Slower");
        playSlowerButton.setHideActionText(true);
        playSlowerButton.setIconTextGap(2);
        playSlowerButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(playSlowerButton);

        stepForwardsButton.setAction(aePlayer.stepForwardAction);
        stepForwardsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/StepForward16.gif"))); // NOI18N
        stepForwardsButton.setToolTipText("Step fowrads");
        stepForwardsButton.setHideActionText(true);
        stepForwardsButton.setIconTextGap(2);
        stepForwardsButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(stepForwardsButton);

        stepBackwardsButon.setAction(aePlayer.stepBackwardAction);
        stepBackwardsButon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/StepBack16.gif"))); // NOI18N
        stepBackwardsButon.setToolTipText("Step backwards");
        stepBackwardsButon.setHideActionText(true);
        stepBackwardsButon.setIconTextGap(2);
        stepBackwardsButon.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(stepBackwardsButon);

        rewindButton.setAction(aePlayer.rewindAction);
        rewindButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/Rewind16.gif"))); // NOI18N
        rewindButton.setToolTipText("Rewind");
        rewindButton.setHideActionText(true);
        rewindButton.setIconTextGap(2);
        rewindButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(rewindButton);

        clearMarksB.setAction(aePlayer.clearMarksAction);
        clearMarksB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/ClearMarks16.gif"))); // NOI18N
        clearMarksB.setHideActionText(true);
        clearMarksB.setIconTextGap(2);
        clearMarksB.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(clearMarksB);

        setInB.setAction(aePlayer.markInAction);
        setInB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/MarkIn16.gif"))); // NOI18N
        setInB.setHideActionText(true);
        setInB.setIconTextGap(2);
        setInB.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(setInB);

        setOutB.setAction(aePlayer.markOutAction);
        setOutB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/MarkOut16.gif"))); // NOI18N
        setOutB.setHideActionText(true);
        setOutB.setIconTextGap(2);
        setOutB.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(setOutB);

        repeatPlaybackButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/Repeat.gif"))); // NOI18N
        repeatPlaybackButton.setSelected(true);
        repeatPlaybackButton.setToolTipText("");
        repeatPlaybackButton.setIconTextGap(2);
        repeatPlaybackButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        repeatPlaybackButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                repeatPlaybackButtonActionPerformed(evt);
            }
        });
        playerControlPanel.add(repeatPlaybackButton);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 28, Short.MAX_VALUE)
        );

        playerControlPanel.add(jPanel3);

        moreControlsPanel.add(playerControlPanel);

        playerStatusPanel.setAlignmentX(0.0F);
        playerStatusPanel.setLayout(new javax.swing.BoxLayout(playerStatusPanel, javax.swing.BoxLayout.X_AXIS));

        timeField.setColumns(15);
        timeField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        timeField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeFieldActionPerformed(evt);
            }
        });
        playerStatusPanel.add(timeField);

        timeFieldLabel.setLabelFor(timeField);
        timeFieldLabel.setText("Time(us)");
        playerStatusPanel.add(timeFieldLabel);

        eventField.setColumns(15);
        eventField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        eventField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eventFieldActionPerformed(evt);
            }
        });
        playerStatusPanel.add(eventField);

        eventFieldLabel.setLabelFor(eventField);
        eventFieldLabel.setText("event");
        playerStatusPanel.add(eventFieldLabel);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 70, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 20, Short.MAX_VALUE)
        );

        playerStatusPanel.add(jPanel2);

        moreControlsPanel.add(playerStatusPanel);

        playbackModePanel.setAlignmentX(0.0F);
        playbackModePanel.setPreferredSize(new java.awt.Dimension(600, 60));

        buttonGroup1.add(fixedTimeSliceButton);
        fixedTimeSliceButton.setText("Fixed time slice");
        fixedTimeSliceButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixedTimeSliceButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(fixedPacketSizeButton);
        fixedPacketSizeButton.setText("Fixed number of events");
        fixedPacketSizeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixedPacketSizeButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(realtimeButton);
        realtimeButton.setText("Real time playback");
        realtimeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                realtimeButtonActionPerformed(evt);
            }
        });

        timesliceSpinner.setModel(new OctaveSpinnerNumberModel(20000, 1, 3000000, 100));
        timesliceSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                timesliceSpinnerStateChanged(evt);
            }
        });

        msLabel.setText("us");

        packetSizeSpinner.setModel(new OctaveSpinnerNumberModel(256, 1, 1000000, 128));
        packetSizeSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                packetSizeSpinnerStateChanged(evt);
            }
        });

        eventsLabel.setText("events");

        javax.swing.GroupLayout playbackModePanelLayout = new javax.swing.GroupLayout(playbackModePanel);
        playbackModePanel.setLayout(playbackModePanelLayout);
        playbackModePanelLayout.setHorizontalGroup(
            playbackModePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(playbackModePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(playbackModePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(playbackModePanelLayout.createSequentialGroup()
                        .addComponent(timesliceSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 114, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(msLabel)
                        .addGap(18, 18, 18)
                        .addComponent(packetSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(playbackModePanelLayout.createSequentialGroup()
                        .addComponent(fixedTimeSliceButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fixedPacketSizeButton)))
                .addGroup(playbackModePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(playbackModePanelLayout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addComponent(realtimeButton))
                    .addGroup(playbackModePanelLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(eventsLabel)))
                .addContainerGap(180, Short.MAX_VALUE))
        );
        playbackModePanelLayout.setVerticalGroup(
            playbackModePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(playbackModePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(playbackModePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fixedTimeSliceButton)
                    .addComponent(fixedPacketSizeButton)
                    .addComponent(realtimeButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(playbackModePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timesliceSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(msLabel)
                    .addComponent(packetSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(eventsLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        moreControlsPanel.add(playbackModePanel);

        syncPanel.setAlignmentX(0.0F);
        syncPanel.setLayout(new java.awt.BorderLayout());

        syncPlaybackCheckBox.setToolTipText("");
        syncPanel.add(syncPlaybackCheckBox, java.awt.BorderLayout.WEST);

        moreControlsPanel.add(syncPanel);

        controlsPanel.add(moreControlsPanel);

        add(controlsPanel, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void fixedTimeSliceButtonActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedTimeSliceButtonActionPerformed
        aePlayer.setPlaybackMode(PlaybackMode.FixedTimeSlice);
        timesliceSpinner.setValue(aePlayer.getTimesliceUs());
}//GEN-LAST:event_fixedTimeSliceButtonActionPerformed

    private void fixedPacketSizeButtonActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedPacketSizeButtonActionPerformed
        aePlayer.setPlaybackMode(PlaybackMode.FixedPacketSize);
}//GEN-LAST:event_fixedPacketSizeButtonActionPerformed

    private void realtimeButtonActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_realtimeButtonActionPerformed
        aePlayer.setPlaybackMode(PlaybackMode.RealTime);
}//GEN-LAST:event_realtimeButtonActionPerformed

    private void timesliceSpinnerStateChanged (javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_timesliceSpinnerStateChanged
        try {
            aePlayer.setTimesliceUs((Integer) (timesliceSpinner.getValue()));
        } catch (Exception e) {
            log.warning(e.toString());
        }
}//GEN-LAST:event_timesliceSpinnerStateChanged

    private void packetSizeSpinnerStateChanged (javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_packetSizeSpinnerStateChanged
        try {
            aePlayer.setPacketSizeEvents((Integer) (packetSizeSpinner.getValue()));
        } catch (Exception e) {
            log.warning(e.toString());
        }
}//GEN-LAST:event_packetSizeSpinnerStateChanged
    private boolean playerSliderWasPaused = false;

    private void playerSliderMousePressed (java.awt.event.MouseEvent evt) {//GEN-FIRST:event_playerSliderMousePressed
        //        playerSliderMousePressed=true;
        playerSliderWasPaused = aePlayer.isPaused();
        //        log.info("playerSliderWasPaused="+playerSliderWasPaused);
}//GEN-LAST:event_playerSliderMousePressed

    private void playerSliderMouseReleased (java.awt.event.MouseEvent evt) {//GEN-FIRST:event_playerSliderMouseReleased
        //        playerSliderMousePressed=false;
        //        log.info("playerSliderWasPaused="+playerSliderWasPaused);
        if (!playerSliderWasPaused) {
            synchronized (aePlayer) {
                aePlayer.setDoSingleStepEnabled(false);
                aePlayer.resume(); // might be in middle of single step in viewLoop, which will just pause again
            }
        }
}//GEN-LAST:event_playerSliderMouseReleased

    private void playerSliderStateChanged (javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_playerSliderStateChanged
        if (sliderDontProcess) {
            sliderDontProcess = false; // to avoid player callbacks generating more AWT events
            return;

        }

        float fracPos = (float) playerSlider.getValue() / (playerSlider.getMaximum());

        if (aeViewer.aePlayer.getAEInputStream() == null) {
            return;
        }

        synchronized (aePlayer) {
            try {
                int oldtime = aeViewer.aePlayer.getAEInputStream().getMostRecentTimestamp();
                aeViewer.aePlayer.setFractionalPosition(fracPos); // sets position in events
                int time = aeViewer.aePlayer.getAEInputStream().getMostRecentTimestamp();
                aeViewer.aePlayer.getAEInputStream().setCurrentStartTimestamp(time); // tobi commented out to support RosbagFileInputStream
                String s=String.format("%8.3f s, %10d position",time*1e-6f,aeViewer.aePlayer.getAEInputStream().position());
                aeViewer.setStatusMessage(s);
//                log.info("slider position "+s);
                //                log.info(this+" slider set time to "+time);
                if (aeViewer.getJaerViewer().getViewers().size() > 1) {
                    if (time < oldtime) {
                        // we need to set position in all viewers so that we catch up to present desired time
                        AbstractAEPlayer p;
                        AEFileInputStreamInterface is;

                        try {
                            for (AEViewer v : aeViewer.getJaerViewer().getViewers()) {
                                if (true) {
                                    p = v.aePlayer; // we want local play here!
                                    is = p.getAEInputStream();
                                    if (is != null) {
                                        is.rewind();
                                    } else {
                                        log.warning("null ae input stream on reposition");
                                    }

                                }
                            }
                            aeViewer.getJaerViewer().getSyncPlayer().setTime(time);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
            boolean wasPaused=aeViewer.getJaerViewer().getSyncPlayer().isPaused();
            aeViewer.getJaerViewer().getSyncPlayer().doSingleStep();
            aeViewer.getJaerViewer().getSyncPlayer().setPaused(wasPaused);
            // inform all listeners on the players that they have been repositioned
            for (AEViewer v : aeViewer.getJaerViewer().getViewers()) {
                if(v.getAePlayer()!=null && v.getAePlayer().getAEInputStream()!=null){
                    v.getAePlayer().getSupport().firePropertyChange(AEInputStream.EVENT_REPOSITIONED, null, v.getAePlayer().getAEInputStream().position());
                }
            }
        }
}//GEN-LAST:event_playerSliderStateChanged

    private void timeFieldActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeFieldActionPerformed
        if (aePlayer == null) {
            return;
        }
        try {
            int t = Integer.parseInt(timeField.getText());
            aePlayer.setTime(t);
        } catch (Exception e) {
            log.warning(e.toString());
            timeField.selectAll();
        }
    }//GEN-LAST:event_timeFieldActionPerformed

    private void eventFieldActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eventFieldActionPerformed
        if (aePlayer == null) {
            return;
        }
        try {
            long pos = Long.parseLong(eventField.getText());
            aePlayer.getAEInputStream().position(pos);
        } catch (Exception e) {
            log.warning(e.toString());
            eventField.selectAll();
        }
    }//GEN-LAST:event_eventFieldActionPerformed

    private void repeatPlaybackButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_repeatPlaybackButtonActionPerformed
        if (aePlayer == null) {
            return;
        }
        aePlayer.setRepeat(repeatPlaybackButton.isSelected());
    }//GEN-LAST:event_repeatPlaybackButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton clearMarksB;
    private javax.swing.JPanel controlsPanel;
    private javax.swing.JTextField eventField;
    private javax.swing.JLabel eventFieldLabel;
    private javax.swing.JLabel eventsLabel;
    private javax.swing.JRadioButton fixedPacketSizeButton;
    private javax.swing.JRadioButton fixedTimeSliceButton;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel moreControlsPanel;
    private javax.swing.JLabel msLabel;
    private javax.swing.JSpinner packetSizeSpinner;
    private javax.swing.JButton pauseButton;
    private javax.swing.JButton playBackwardsButton;
    private javax.swing.JButton playFasterButton;
    private javax.swing.JButton playForwardsButton;
    private javax.swing.JButton playSlowerButton;
    private javax.swing.JPanel playbackModePanel;
    private javax.swing.JPanel playerControlPanel;
    private javax.swing.JSlider playerSlider;
    private javax.swing.JPanel playerStatusPanel;
    private javax.swing.JRadioButton realtimeButton;
    private javax.swing.JToggleButton repeatPlaybackButton;
    private javax.swing.JButton reverseButton;
    private javax.swing.JButton rewindButton;
    private javax.swing.JButton setInB;
    private javax.swing.JButton setOutB;
    private javax.swing.JButton showMoreControlsButton;
    private javax.swing.JPanel sliderPanel;
    private javax.swing.JButton stepBackwardsButon;
    private javax.swing.JButton stepForwardsButton;
    private javax.swing.JPanel syncPanel;
    private javax.swing.JCheckBox syncPlaybackCheckBox;
    private javax.swing.JTextField timeField;
    private javax.swing.JLabel timeFieldLabel;
    private javax.swing.JSpinner timesliceSpinner;
    // End of variables declaration//GEN-END:variables
}
