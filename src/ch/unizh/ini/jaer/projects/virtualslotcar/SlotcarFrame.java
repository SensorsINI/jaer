/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * SlotcarFrame.java
 *
 * Created on Jun 11, 2010, 7:02:14 PM
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Point2D;

/**
 *
 * @author Michael Pfeiffer
 */
public class SlotcarFrame extends javax.swing.JFrame {

    // Track that is designed
    SlotcarTrack track = null;

    // Modes for adding or deleting track points
    public static final int NOTHING_SELECTED_MODE = 0;
    public static final int ADD_POINT_MODE = 1;
    public static final int DELETE_POINT_MODE = 2;

    // Maximal distance to select a point
    public static final float MAX_DIST = 0.1f;

    // Current mode for adding or removing points
    private int currentMode;

    // Current point that is dragged
    private int pointDragged;

    // Display of the race
    RacetrackFrame raceDisplay;


    /** Creates new form SlotcarFrame */
    public SlotcarFrame() {
        track = new SlotcarTrack(null);
        initComponents();
        EditorPanel.setTrack(track);
        pointDragged = -1;
        raceDisplay = null;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        ClearButton = new javax.swing.JButton();
        LoadButton = new javax.swing.JButton();
        SaveButton = new javax.swing.JButton();
        EditorPanel = new ch.unizh.ini.jaer.projects.virtualslotcar.TrackEditor();
        NumPointsLabel = new javax.swing.JLabel();
        NumPointsLabel1 = new javax.swing.JLabel();
        AddPointsButton = new javax.swing.JToggleButton();
        DeletePointsButton = new javax.swing.JToggleButton();
        pointLabel = new javax.swing.JLabel();
        lengthLabel = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        stepsizeValue = new javax.swing.JTextField();
        RunButton = new javax.swing.JButton();
        RefineButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Virtual Slotcar Track Editor");

        ClearButton.setText("Clear Track");
        ClearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ClearButtonActionPerformed(evt);
            }
        });

        LoadButton.setText("Load Track");

        SaveButton.setText("Save Track");

        EditorPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                EditorPanelMouseClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                EditorPanelMouseReleased(evt);
            }
        });
        EditorPanel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                EditorPanelMouseDragged(evt);
            }
        });

        javax.swing.GroupLayout EditorPanelLayout = new javax.swing.GroupLayout(EditorPanel);
        EditorPanel.setLayout(EditorPanelLayout);
        EditorPanelLayout.setHorizontalGroup(
            EditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 567, Short.MAX_VALUE)
        );
        EditorPanelLayout.setVerticalGroup(
            EditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 415, Short.MAX_VALUE)
        );

        NumPointsLabel.setText("Num Points");

        NumPointsLabel1.setText("Track Length");

        AddPointsButton.setText("Add Points");
        AddPointsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddPointsButtonActionPerformed(evt);
            }
        });

        DeletePointsButton.setText("Delete Points");
        DeletePointsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DeletePointsButtonActionPerformed(evt);
            }
        });

        pointLabel.setText("0");

        lengthLabel.setText("0");

        jLabel1.setText("Stepsize");

        stepsizeValue.setText("0.01");
        stepsizeValue.setAlignmentX(1.0F);
        stepsizeValue.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stepsizeValueActionPerformed(evt);
            }
        });

        RunButton.setFont(new java.awt.Font("Tahoma", 1, 14));
        RunButton.setText("GO!");
        RunButton.setEnabled(false);
        RunButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                RunButtonMouseClicked(evt);
            }
        });

        RefineButton.setText("Refine");
        RefineButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RefineButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(EditorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(200, 200, 200)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(RefineButton, javax.swing.GroupLayout.DEFAULT_SIZE, 127, Short.MAX_VALUE)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 35, Short.MAX_VALUE)
                                .addComponent(stepsizeValue, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(RunButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 127, Short.MAX_VALUE)
                            .addComponent(AddPointsButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 127, Short.MAX_VALUE)
                            .addComponent(DeletePointsButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 127, Short.MAX_VALUE)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(NumPointsLabel)
                                        .addGap(28, 28, 28))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(NumPointsLabel1)
                                        .addGap(18, 18, 18)))
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(pointLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(lengthLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 26, Short.MAX_VALUE)))))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(42, 42, 42)
                        .addComponent(ClearButton, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(LoadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(SaveButton, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(EditorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 65, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ClearButton)
                    .addComponent(LoadButton)
                    .addComponent(SaveButton)))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pointLabel)
                    .addComponent(NumPointsLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lengthLabel)
                    .addComponent(NumPointsLabel1))
                .addGap(50, 50, 50)
                .addComponent(AddPointsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(DeletePointsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(RefineButton)
                .addGap(54, 54, 54)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(stepsizeValue, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addGap(39, 39, 39)
                .addComponent(RunButton, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(202, 202, 202))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void ClearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ClearButtonActionPerformed
        track.clear();
        pointLabel.setText(String.valueOf(track.getNumPoints()));
        lengthLabel.setText(String.valueOf(track.getTrackLength()));
        pointDragged = -1;
        RunButton.setEnabled(false);
        repaint();
    }//GEN-LAST:event_ClearButtonActionPerformed

    private void EditorPanelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_EditorPanelMouseClicked
        int ClickX = evt.getX();
        int ClickY = evt.getY();

        Point2D.Float normPoint = normalizedPosition(evt.getPoint());

        switch (currentMode) {
            case ADD_POINT_MODE: {
                System.out.println("Adding point " + normPoint);
                track.addPoint(normPoint);
                track.updateTrack();
                pointLabel.setText(String.valueOf(track.getNumPoints()));
                lengthLabel.setText(String.format("%.2g", track.getTrackLength()));
                if (track.getNumPoints() > 2) {
                    RunButton.setEnabled(true);
                }
                break;
            }
            case DELETE_POINT_MODE: {
                int closestIdx = track.findClosestIndex(normPoint, MAX_DIST, false);
                System.out.println("Deleting Point " + track.getPoint(closestIdx));
                track.deletePoint(closestIdx);
                track.updateTrack();
               pointLabel.setText(String.valueOf(track.getNumPoints()));
                lengthLabel.setText(String.format("%.2g", track.getTrackLength()));

                if (track.getNumPoints() < 3)
                    RunButton.setEnabled(false);
                // EditorPanel.setTrack(track);
                break;
            }
            default: {
                // Do nothing
            }
        }

        pointDragged = -1;

        // Re-paint the track
        EditorPanel.repaint();
    }//GEN-LAST:event_EditorPanelMouseClicked

    private void AddPointsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddPointsButtonActionPerformed
        // TODO add your handling code here:
        if (AddPointsButton.isSelected()) {
            // Make sure that other button is deselected
            DeletePointsButton.setSelected(false);
            DeletePointsButton.repaint();

            currentMode = ADD_POINT_MODE;
        }
    }//GEN-LAST:event_AddPointsButtonActionPerformed

    private void DeletePointsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_DeletePointsButtonActionPerformed
        // TODO add your handling code here:
        if (DeletePointsButton.isSelected()) {
            // Make sure that other button is deselected
            AddPointsButton.setSelected(false);
            AddPointsButton.repaint();

            currentMode = DELETE_POINT_MODE;
        }
    }//GEN-LAST:event_DeletePointsButtonActionPerformed

    private void stepsizeValueActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stepsizeValueActionPerformed
        float step = Float.parseFloat(stepsizeValue.getText());
        if ((step > 0) && (step <1))
            EditorPanel.setStepSize(step);
        if (step >= 1)
            System.out.println("ERROR: Too large step size!");
    }//GEN-LAST:event_stepsizeValueActionPerformed

    private void EditorPanelMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_EditorPanelMouseDragged
        // TODO add your handling code here:
        if (pointDragged < 0) {
            Point2D.Float normPoint = normalizedPosition(evt.getPoint());
            pointDragged=track.findClosestIndex(normPoint, MAX_DIST, false);

            System.out.println("Dragging Point " + pointDragged);
        }
        else {
            // Move dragged point
            Point2D.Float normPoint = normalizedPosition(evt.getPoint());
            track.setPoint(pointDragged, normPoint);
            EditorPanel.repaint();
        }

    }//GEN-LAST:event_EditorPanelMouseDragged

    private void EditorPanelMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_EditorPanelMouseReleased
        // TODO add your handling code here:
        System.out.println("Mouse released!");
        pointDragged = -1;
    }//GEN-LAST:event_EditorPanelMouseReleased

    private void RunButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_RunButtonMouseClicked
        // TODO add your handling code here:
        if (raceDisplay == null) {
            raceDisplay = new RacetrackFrame();
        }
        float step = Float.parseFloat(stepsizeValue.getText());
        raceDisplay.setTrack(track, step);
        raceDisplay.setVisible(true);
    }//GEN-LAST:event_RunButtonMouseClicked

    private void RefineButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RefineButtonActionPerformed
        // TODO add your handling code here:
        float step = Float.parseFloat(stepsizeValue.getText());
        track.refine(step);
        repaint();
    }//GEN-LAST:event_RefineButtonActionPerformed

    /** Computes the normalized position of a point wrt. size and position of the
     * drawing panel.
     * @param pos Position of the mouse click in frame coordinates
     * @return Normalized position of the mouse position
     */
    private Point2D.Float normalizedPosition(Point pos) {
       Dimension d = EditorPanel.getSize();
       Point2D.Float np = new Point2D.Float(((float) pos.x) / d.width, ((float) d.height-pos.y) / d.height);
       return np;
    }

    
    /**
     * Returns the designed track.
     * @return The track designed in the window.
     */
    public SlotcarTrack getTrack() {
        return track;
    }

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new SlotcarFrame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JToggleButton AddPointsButton;
    private javax.swing.JButton ClearButton;
    private javax.swing.JToggleButton DeletePointsButton;
    private ch.unizh.ini.jaer.projects.virtualslotcar.TrackEditor EditorPanel;
    private javax.swing.JButton LoadButton;
    private javax.swing.JLabel NumPointsLabel;
    private javax.swing.JLabel NumPointsLabel1;
    private javax.swing.JButton RefineButton;
    private javax.swing.JButton RunButton;
    private javax.swing.JButton SaveButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel lengthLabel;
    private javax.swing.JLabel pointLabel;
    private javax.swing.JTextField stepsizeValue;
    // End of variables declaration//GEN-END:variables
}
