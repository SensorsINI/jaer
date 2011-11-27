
package uk.ac.imperial.pseye;

import java.awt.BorderLayout;
import java.util.Observer;
import java.util.Observable;

/**
 * Panel to contain raw PSEye image.
 * 
 * @author tobi - modified mlk
 */
public class PSEyeViewPanel extends javax.swing.JPanel implements Observer {
    private PSEyeModelChip chip;
    private PSEyeHardwareInterface hardware;
    private PSEyeRawFramePanel rawCameraPanel;

    public PSEyeViewPanel(PSEyeModelChip chip) {
        this.chip = chip;
        initComponents();
    }

    private boolean checkHardware() {
        hardware = (PSEyeHardwareInterface) chip.getHardwareInterface();
        return hardware!=null;
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        showRawInputCB = new javax.swing.JCheckBox();
        jPanel1 = new javax.swing.JPanel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(20, 0), new java.awt.Dimension(20, 0), new java.awt.Dimension(20, 32767));
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(20, 0), new java.awt.Dimension(20, 0), new java.awt.Dimension(20, 32767));
        filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(32767, 20));
        filler4 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 10), new java.awt.Dimension(0, 10), new java.awt.Dimension(32767, 10));

        setBorder(javax.swing.BorderFactory.createTitledBorder("Raw camera input (when enabled)"));
        setPreferredSize(new java.awt.Dimension(320, 329));
        setLayout(new java.awt.BorderLayout());

        showRawInputCB.setText("Show raw input");
        showRawInputCB.setToolTipText("Activates raw input panel to show camera output");
        showRawInputCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showRawInputCBActionPerformed(evt);
            }
        });
        add(showRawInputCB, java.awt.BorderLayout.PAGE_START);

        jPanel1.setLayout(new java.awt.BorderLayout());
        jPanel1.add(filler1, java.awt.BorderLayout.LINE_START);
        jPanel1.add(filler2, java.awt.BorderLayout.LINE_END);
        jPanel1.add(filler3, java.awt.BorderLayout.PAGE_END);
        jPanel1.add(filler4, java.awt.BorderLayout.PAGE_START);

        add(jPanel1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void showRawInputCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showRawInputCBActionPerformed
        // check if raw data panel already constructedd
        if (rawCameraPanel == null) {
            // create raw data panel and fit in holder panel jPanel1 (used for layout)
            rawCameraPanel = new PSEyeRawFramePanel(chip);
            rawCameraPanel.reshape(0, 0, jPanel1.getWidth(), jPanel1.getHeight());
            jPanel1.add(rawCameraPanel, BorderLayout.CENTER);
            revalidate();
        }
        if (!checkHardware()) {
            return;
        }
        else {
            hardware.addObserver(this);
        }
        if (showRawInputCB.isSelected()) {
            ((PSEyeHardwareInterface) chip.getHardwareInterface()).addAEListener(rawCameraPanel);
        } else {
            ((PSEyeHardwareInterface) chip.getHardwareInterface()).removeAEListener(rawCameraPanel);
        }
}//GEN-LAST:event_showRawInputCBActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.Box.Filler filler3;
    private javax.swing.Box.Filler filler4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JCheckBox showRawInputCB;
    // End of variables declaration//GEN-END:variables
    
    @Override
    public void update(Observable o, Object arg) {
        // check to see if camera resolution chaged and if so reshape
        if (o != null && o == hardware && arg instanceof PSEyeCamera.EVENT) {
            PSEyeCamera.EVENT event = (PSEyeCamera.EVENT) arg;
            // disable raw view during refresh
            if (showRawInputCB.isSelected())
                ((PSEyeHardwareInterface) o).removeAEListener(rawCameraPanel);
            
            switch (event) {
                case RESOLUTION: 
                    if (rawCameraPanel != null)
                        rawCameraPanel.reshape(0, 0, getWidth(), getHeight());
                    break;
            }
            // re-enable raw view
            if (showRawInputCB.isSelected())
                ((PSEyeHardwareInterface) o).addAEListener(rawCameraPanel);
        }
    }
}
