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
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.graphics.AbstractAEPlayer.PlaybackMode;
/**
 *  All the controls for playback are in this GUI.
 * 
 * @author tobi
 */
public class AePlayerAdvancedControlsPanel extends javax.swing.JPanel implements PropertyChangeListener{
    static Logger log = Logger.getLogger("AEViewerPlaybackControlDialog");
    private AbstractAEPlayer aePlayer;
    private AEViewer aeViewer;
    MoreLessAction moreLessAction = new MoreLessAction();
    private volatile boolean sliderDontProcess = false; // semaphore used to prevent slider actions when slider is set programmatically

    /** Creates new form AePlayerAdvancedControlsPanel.
    @param viewer the viewer to control.
     */
    public AePlayerAdvancedControlsPanel (AEViewer viewer){
        this.aeViewer = viewer;
        this.aePlayer = viewer.getAePlayer();
        initComponents();
        setAePlayer(viewer.getAePlayer()); // TODO double set needed because aePlayer is needed in initComponents and we still need to do more component binding in setAePlayer
        moreControlsPanel.setVisible(false);
    }

    /** Use this method to add this to the AEFileInputStream listener list for position updates.
     *
     * @param is the input stream.
     */
    public void addMeToPropertyChangeListeners (AEFileInputStream is){
        is.getSupport().addPropertyChangeListener("position",this);
        is.getSupport().addPropertyChangeListener("markset",this);
        is.getSupport().addPropertyChangeListener("markcleared",this);
    }

    /** Messages come back here from e.g. programmatic state changes, like a new aePlayer file posiiton.
     * This methods sets the GUI components to a consistent state, using a flag to tell the slider that it has not been set by
     * a user mouse action
     */
    public void propertyChange (PropertyChangeEvent evt){
        if ( evt.getPropertyName().equals("position") ){ // comes from AEFileInputStream
            sliderDontProcess = true;
            // note this cool semaphore/flag trick to avoid processing the
            // event generated when we programmatically set the slider position here
            playerSlider.setValue(Math.round(getFractionalPosition() * playerSlider.getMaximum()));
            if ( moreControlsPanel.isVisible() || aeViewer.aePlayer.getAEInputStream() != null ){
                eventField.setText(Integer.toString(aeViewer.aePlayer.position()));
                timeField.setText(Integer.toString(aeViewer.aePlayer.getTime()));
            }
        } else if ( evt.getPropertyName().equals("markset") ){
            synchronized ( aePlayer ){
                Hashtable<Integer,JLabel> markTable = new Hashtable<Integer,JLabel>();
                markTable.put(playerSlider.getValue(),new JLabel("^"));
                playerSlider.setLabelTable(markTable);
                playerSlider.setPaintLabels(true);
            }
        } else if ( evt.getPropertyName().equals("markcleared") ){
            playerSlider.setPaintLabels(false);
        } else if ( evt.getPropertyName().equals("timesliceUs") ){
            timesliceSpinner.setValue(aePlayer.getTimesliceUs());
        } else if ( evt.getPropertyName().equals("packetSizeEvents") ){
            packetSizeSpinner.setValue(aePlayer.getPacketSizeEvents());
        } else if ( evt.getPropertyName().equals("paused") ){
            aePlayer.pausePlayAction.setPlayAction();
        } else if ( evt.getPropertyName().equals("resumed") ){
            aePlayer.pausePlayAction.setPauseAction();
        }
    }

    public JCheckBox getSyncPlaybackCheckBox (){
        return syncPlaybackCheckBox;
    }

    private float getFractionalPosition (){
        if ( aeViewer.aePlayer.getAEInputStream() == null ){
            log.warning("AEViewer.AEPlayer.getFractionalPosition: null fileAEInputStream, returning 0");
            return 0;
        }
        float fracPos = aeViewer.aePlayer.getAEInputStream().getFractionalPosition();
        return fracPos;
    }

    /**
     * @return the playerSlider
     */
    public javax.swing.JSlider getPlayerSlider (){
        return playerSlider;
    }

    /**
     * Use to set player if player changes, e.g. when SyncPlayer replaces player for player functionality.
     * 
     * @param aePlayer the aePlayer to set
     */
    public void setAePlayer (AbstractAEPlayer aePlayer){
        if ( this.aePlayer != null ){
            this.aePlayer.getSupport().removePropertyChangeListener(this);
        }
        this.aePlayer = aePlayer;
        aePlayer.getSupport().addPropertyChangeListener(this);
        switch ( aePlayer.getPlaybackMode() ){
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
        playBackwardsButton1.setAction(aePlayer.playAction);
        reverseButton.setAction(aePlayer.reverseAction);
        playBackwardsButton.setAction(aePlayer.playBackwardsAction);
        playFasterButton.setAction(aePlayer.fasterAction);
        playSlowerButton.setAction(aePlayer.slowerAction);
        stepBackwardsButon.setAction(aePlayer.stepBackwardAction);
        stepForwardsButton.setAction(aePlayer.stepForwardAction);
        rewindButton.setAction(aePlayer.rewindAction);
        jToggleButton1.setAction(aePlayer.markUnmarkAction);

        showMoreControlsButton.setAction(moreLessAction);

    }
    /** The action that shows more or less controls. */
    public class MoreLessAction extends AbstractAction{
        public MoreLessAction (){
            putValue(Action.NAME,"More");
            putValue(Action.SHORT_DESCRIPTION,"Shows more or less controls for playback");
        }

        public void actionPerformed (ActionEvent e){
            moreControlsPanel.setVisible(!moreControlsPanel.isVisible());
            if ( moreControlsPanel.isVisible() ){
                putValue(Action.NAME,"Less");
            } else{
                putValue(Action.NAME,"More");
            }
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings ("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        controlsPanel = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        playerSlider = new javax.swing.JSlider();
        showMoreControlsButton = new javax.swing.JButton();
        moreControlsPanel = new javax.swing.JPanel();
        playerControlPanel = new javax.swing.JPanel();
        pauseButton = new javax.swing.JButton();
        playBackwardsButton1 = new javax.swing.JButton();
        reverseButton = new javax.swing.JButton();
        playBackwardsButton = new javax.swing.JButton();
        playFasterButton = new javax.swing.JButton();
        playSlowerButton = new javax.swing.JButton();
        stepBackwardsButon = new javax.swing.JButton();
        stepForwardsButton = new javax.swing.JButton();
        rewindButton = new javax.swing.JButton();
        jToggleButton1 = new javax.swing.JToggleButton();
        playerStatusPanel = new javax.swing.JPanel();
        timeField = new javax.swing.JTextField();
        timeFieldLabel = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        eventField = new javax.swing.JTextField();
        eventFieldLabel = new javax.swing.JLabel();
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

        jPanel1.setAlignmentX(0.0F);
        jPanel1.setPreferredSize(new java.awt.Dimension(100, 40));
        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.LINE_AXIS));

        playerSlider.setMaximum(1000);
        playerSlider.setToolTipText("Shows and controls playback position (in events, not time)");
        playerSlider.setValue(0);
        playerSlider.setMaximumSize(new java.awt.Dimension(800, 25));
        playerSlider.setPreferredSize(new java.awt.Dimension(600, 25));
        playerSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                playerSliderMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                playerSliderMouseReleased(evt);
            }
        });
        playerSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                playerSliderStateChanged(evt);
            }
        });
        jPanel1.add(playerSlider);

        showMoreControlsButton.setAction(moreLessAction);
        showMoreControlsButton.setToolTipText("");
        showMoreControlsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showMoreControlsButtonActionPerformed(evt);
            }
        });
        jPanel1.add(showMoreControlsButton);

        controlsPanel.add(jPanel1);

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

        playBackwardsButton1.setAction(aePlayer.playAction);
        playBackwardsButton1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/Play16.gif"))); // NOI18N
        playBackwardsButton1.setToolTipText("Play forwards");
        playBackwardsButton1.setHideActionText(true);
        playBackwardsButton1.setIconTextGap(2);
        playBackwardsButton1.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(playBackwardsButton1);

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

        stepBackwardsButon.setAction(aePlayer.stepBackwardAction);
        stepBackwardsButon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/StepBack16.gif"))); // NOI18N
        stepBackwardsButon.setToolTipText("Step backwards");
        stepBackwardsButon.setHideActionText(true);
        stepBackwardsButon.setIconTextGap(2);
        stepBackwardsButon.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(stepBackwardsButon);

        stepForwardsButton.setAction(aePlayer.stepForwardAction);
        stepForwardsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/StepForward16.gif"))); // NOI18N
        stepForwardsButton.setToolTipText("Step fowrads");
        stepForwardsButton.setHideActionText(true);
        stepForwardsButton.setIconTextGap(2);
        stepForwardsButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(stepForwardsButton);

        rewindButton.setAction(aePlayer.rewindAction);
        rewindButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/Rewind16.gif"))); // NOI18N
        rewindButton.setToolTipText("Rewind");
        rewindButton.setHideActionText(true);
        rewindButton.setIconTextGap(2);
        rewindButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(rewindButton);

        jToggleButton1.setAction(aePlayer.markUnmarkAction);
        jToggleButton1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/graphics/icons/Mark16.gif"))); // NOI18N
        jToggleButton1.setHideActionText(true);
        jToggleButton1.setMargin(new java.awt.Insets(2, 5, 2, 5));
        playerControlPanel.add(jToggleButton1);

        moreControlsPanel.add(playerControlPanel);

        playerStatusPanel.setLayout(new javax.swing.BoxLayout(playerStatusPanel, javax.swing.BoxLayout.X_AXIS));

        timeField.setColumns(20);
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

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 77, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 20, Short.MAX_VALUE)
        );

        playerStatusPanel.add(jPanel2);

        eventField.setColumns(20);
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

        timesliceSpinner.setModel(new OctaveSpinnerNumberModel(20000, 1, 300000, 100));
        timesliceSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                timesliceSpinnerStateChanged(evt);
            }
        });

        msLabel.setText("us");

        packetSizeSpinner.setModel(new OctaveSpinnerNumberModel(256, 1, 300000, 128));
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
                    .addComponent(fixedTimeSliceButton)
                    .addGroup(playbackModePanelLayout.createSequentialGroup()
                        .addComponent(timesliceSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 76, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(msLabel)))
                .addGap(17, 17, 17)
                .addGroup(playbackModePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(playbackModePanelLayout.createSequentialGroup()
                        .addComponent(fixedPacketSizeButton)
                        .addGap(18, 18, 18)
                        .addComponent(realtimeButton))
                    .addGroup(playbackModePanelLayout.createSequentialGroup()
                        .addComponent(packetSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(eventsLabel)))
                .addContainerGap(9, Short.MAX_VALUE))
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
                    .addComponent(packetSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(msLabel)
                    .addComponent(eventsLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        moreControlsPanel.add(playbackModePanel);

        syncPanel.setAlignmentX(0.0F);

        syncPlaybackCheckBox.setToolTipText("");
        syncPlaybackCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                syncPlaybackCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout syncPanelLayout = new javax.swing.GroupLayout(syncPanel);
        syncPanel.setLayout(syncPanelLayout);
        syncPanelLayout.setHorizontalGroup(
            syncPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(syncPanelLayout.createSequentialGroup()
                .addGap(26, 26, 26)
                .addComponent(syncPlaybackCheckBox)
                .addContainerGap(358, Short.MAX_VALUE))
        );
        syncPanelLayout.setVerticalGroup(
            syncPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, syncPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(syncPlaybackCheckBox))
        );

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
        try{
            aePlayer.setTimesliceUs((Integer)( timesliceSpinner.getValue() ));
        } catch ( Exception e ){
            log.warning(e.toString());
        }
}//GEN-LAST:event_timesliceSpinnerStateChanged

    private void packetSizeSpinnerStateChanged (javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_packetSizeSpinnerStateChanged
        try{
            aePlayer.setPacketSizeEvents((Integer)( packetSizeSpinner.getValue() ));
        } catch ( Exception e ){
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
        if ( !playerSliderWasPaused ){
            synchronized ( aePlayer ){
                aePlayer.setDoSingleStepEnabled(false);
                aePlayer.resume(); // might be in middle of single step in viewLoop, which will just pause again
            }
        }
}//GEN-LAST:event_playerSliderMouseReleased

    private void playerSliderStateChanged (javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_playerSliderStateChanged
        if ( sliderDontProcess ){
            sliderDontProcess = false; // to avoid player callbacks generating more AWT events
            return;

        }

        float fracPos = (float)playerSlider.getValue() / ( playerSlider.getMaximum() );

        if (aeViewer.aePlayer.getAEInputStream()==null)
            return;
        
        synchronized ( aePlayer ){
            try{
                int oldtime = aeViewer.aePlayer.getAEInputStream().getMostRecentTimestamp();
                aeViewer.aePlayer.setFractionalPosition(fracPos); // sets position in events
                int time = aeViewer.aePlayer.getAEInputStream().getMostRecentTimestamp();
                aeViewer.aePlayer.getAEInputStream().setCurrentStartTimestamp(time);
                //                log.info(this+" slider set time to "+time);
                if ( aeViewer.getJaerViewer().getViewers().size() > 1 ){
                    if ( time < oldtime ){
                        // we need to set position in all viewers so that we catch up to present desired time
                        AbstractAEPlayer p;
                        AEFileInputStream is;

                        try{
                            for ( AEViewer v:aeViewer.getJaerViewer().getViewers() ){
                                if ( true ){
                                    p = v.aePlayer; // we want local play here!
                                    is = p.getAEInputStream();
                                    if ( is != null ){
                                        is.rewind();
                                    } else{
                                        log.warning("null ae input stream on reposition");
                                    }

                                }
                            }
                            aeViewer.getJaerViewer().getSyncPlayer().setTime(time);
                        } catch ( Exception e ){
                            e.printStackTrace();
                        }

                    }
                }
            } catch ( IllegalArgumentException e ){
                e.printStackTrace();
            }

            aeViewer.getJaerViewer().getSyncPlayer().doSingleStep();
        }
}//GEN-LAST:event_playerSliderStateChanged

    private void showMoreControlsButtonActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showMoreControlsButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_showMoreControlsButtonActionPerformed

    private void syncPlaybackCheckBoxActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syncPlaybackCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_syncPlaybackCheckBoxActionPerformed

    private void timeFieldActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeFieldActionPerformed
        if ( aePlayer == null ){
            return;
        }
        try{
            int t = Integer.parseInt(timeField.getText());
            aePlayer.setTime(t);
        } catch ( Exception e ){
            log.warning(e.toString());
            timeField.selectAll();
        }
    }//GEN-LAST:event_timeFieldActionPerformed

    private void eventFieldActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eventFieldActionPerformed
        if ( aePlayer == null ){
            return;
        }
        try{
            int pos = Integer.parseInt(eventField.getText());
            aePlayer.getAEInputStream().position(pos);
        } catch ( Exception e ){
            log.warning(e.toString());
            eventField.selectAll();
        }
    }//GEN-LAST:event_eventFieldActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JPanel controlsPanel;
    private javax.swing.JTextField eventField;
    private javax.swing.JLabel eventFieldLabel;
    private javax.swing.JLabel eventsLabel;
    private javax.swing.JRadioButton fixedPacketSizeButton;
    private javax.swing.JRadioButton fixedTimeSliceButton;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JToggleButton jToggleButton1;
    private javax.swing.JPanel moreControlsPanel;
    private javax.swing.JLabel msLabel;
    private javax.swing.JSpinner packetSizeSpinner;
    private javax.swing.JButton pauseButton;
    private javax.swing.JButton playBackwardsButton;
    private javax.swing.JButton playBackwardsButton1;
    private javax.swing.JButton playFasterButton;
    private javax.swing.JButton playSlowerButton;
    private javax.swing.JPanel playbackModePanel;
    private javax.swing.JPanel playerControlPanel;
    private javax.swing.JSlider playerSlider;
    private javax.swing.JPanel playerStatusPanel;
    private javax.swing.JRadioButton realtimeButton;
    private javax.swing.JButton reverseButton;
    private javax.swing.JButton rewindButton;
    private javax.swing.JButton showMoreControlsButton;
    private javax.swing.JButton stepBackwardsButon;
    private javax.swing.JButton stepForwardsButton;
    private javax.swing.JPanel syncPanel;
    private javax.swing.JCheckBox syncPlaybackCheckBox;
    private javax.swing.JTextField timeField;
    private javax.swing.JLabel timeFieldLabel;
    private javax.swing.JSpinner timesliceSpinner;
    // End of variables declaration//GEN-END:variables
}
