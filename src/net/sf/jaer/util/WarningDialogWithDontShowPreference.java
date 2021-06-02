/*
 * WarningDialogWithDontShowPreference.java
 *
 * Created on October 2, 2008, 5:31 PM
 */
package net.sf.jaer.util;

import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * A warning dialog with a check box to let users choose to not show the warning in the future.
 * <p>
 * <h2>Usage:</h2>
 * If the warning dialog is just shown once, then handling can be as simple as following
 *<pre>
 * </pre>
 *                new WarningDialogWithDontShowPreference(null, false, "Usbio Library warning", s).setVisible(true);
 
 * <p>
 * If the warning dialog is to be shown repeatedly, the following code will make the previous instance disappear and a new one appear. 
 * This handling is necessary because once the OK button is pressed, no more actions are generated from it, so it cannot simply be made visible again.
 * Note also how the Swing code is called in the AWT thread safely using SwingUtilities. A reference to the dialog must be kept so that it can be later used to check
 * for a previous instance and to close it.
 * <pre>
 *            if(imuWarningDialog!=null){
                imuWarningDialog.setVisible(false);
                imuWarningDialog.dispose();
            }
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    imuWarningDialog=new WarningDialogWithDontShowPreference(null, false, "Uncalibrated IMU",
                        "<html>IMU has not been calibrated yet! <p>Load a file with no camera motion and hit the StartIMUCalibration button");
                    imuWarningDialog.setVisible(true);

                }
            });
</pre>
* 
 * 
 * @author  tobi
 */
public class WarningDialogWithDontShowPreference extends javax.swing.JDialog {

    private Preferences prefs = Preferences.userNodeForPackage(WarningDialogWithDontShowPreference.class);
    private Logger log = Logger.getLogger("WarningDialogWithDontShowPreference");
    /** A return status code - returned if Cancel button has been pressed */
    public static final int RET_CANCEL = 0;
    /** A return status code - returned if OK button has been pressed */
    public static final int RET_OK = 1;
    private String key = "WarningDialogWithDontShowPreference";
    ImageIcon imageIcon;

    /** Creates new form WarningDialogWithDontShowPreference
     * 
     * @param parent parent frame to center on, or null
     * @param modal true to make dialog model, i.e. to stop other GUI interaction
     */
    public WarningDialogWithDontShowPreference(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
    }

    /** Creates new form WarningDialogWithDontShowPreference 
       * @param parent parent frame to center on, or null
     * @param modal true to make dialog model, i.e. to stop other GUI interaction
     */
    public WarningDialogWithDontShowPreference(java.awt.Frame parent, boolean modal, String title, String text) {
        super(parent, modal);
        initComponents();
        optionPane.setMessage(text);
        key = title;
        setTitle(title);
        optionPane.setMessageType(JOptionPane.WARNING_MESSAGE);
        pack();
    }

    /** @return the return status of this dialog - one of RET_OK or RET_CANCEL */
    public Object getValue() {
        dispose();
        return optionPane.getValue();
    }

    /** Overrides default setVisible so that if warning is disabled and we try to show, only a log.info is printed and dialog is never made visible.
     * Otherwise, if show is false or warning is not disabled, setVisible acts as normal.
     * @param show true to show (if warning not disabled), false to hide.
     */
    @Override
    public void setVisible(boolean show) {
        if (!SwingUtilities.isEventDispatchThread()) {
            log.warning("You should not be calling this logic outside the Swing Event Thread!");
        }
        if (show && isWarningDisabled()) {
            log.info("not showing WarningDialogWithDontShowPreference " + getTitle() + " because warning was disabled. To turn on this warning, remove the Preferences key " + prefsKey());
            return;
        }
        super.setVisible(show);
    }

    /** returns true if user has disabled this warning */
    public boolean isWarningDisabled() {
        if (prefs.get(prefsKey(), null) == null) {
            return false;
        } else {
            return prefs.getBoolean(prefsKey(), false);
        }
    }

    private String prefsKey() {
        String s = key;
        if (s.length() > 20) {
            s = s.substring(0, 10) + s.substring(s.length() - 10, s.length());
        }
        return "WarningDialogWithDontShowPreference." + s;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        iconPanel = new javax.swing.JPanel();
        optionPane = new javax.swing.JOptionPane();
        dontShowAgainCheckBox = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        iconPanel.setLayout(new java.awt.BorderLayout());

        optionPane.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                optionPanePropertyChange(evt);
            }
        });
        iconPanel.add(optionPane, java.awt.BorderLayout.CENTER);

        dontShowAgainCheckBox.setText("Don't show again");
        dontShowAgainCheckBox.setToolTipText("Select to supress this warning");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(dontShowAgainCheckBox)
                .addContainerGap(328, Short.MAX_VALUE))
            .addComponent(iconPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 435, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(iconPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 108, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dontShowAgainCheckBox))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /** Closes the dialog */
    private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
        log.info("storing preference for " + prefsKey() + "=" + dontShowAgainCheckBox.isSelected());
        prefs.putBoolean(prefsKey(), dontShowAgainCheckBox.isSelected());
        doClose(RET_CANCEL);
    }//GEN-LAST:event_closeDialog

private void optionPanePropertyChange (java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_optionPanePropertyChange
    String prop = evt.getPropertyName();

    if (isVisible()
            && (evt.getSource() == optionPane)
            && (prop.equals(JOptionPane.VALUE_PROPERTY))) {
        //If you were going to check something
        //before closing the window, you'd do
        //it here.
        prefs.putBoolean(prefsKey(), dontShowAgainCheckBox.isSelected());
        setVisible(false);
        dispose();
    }
}//GEN-LAST:event_optionPanePropertyChange

    private void doClose(int retStatus) {
        returnStatus = retStatus;
        dispose();
//        setVisible(false);
//        closeDialog(null);
//        dispose();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
                WarningDialogWithDontShowPreference dialog = new WarningDialogWithDontShowPreference(new javax.swing.JFrame(), true, "Test Warning", "<html>This is a <p>test warning message</html>");
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {

                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
                if (dialog.isWarningDisabled()) {
                    System.exit(0);
                }
            }
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox dontShowAgainCheckBox;
    private javax.swing.JPanel iconPanel;
    private javax.swing.JOptionPane optionPane;
    // End of variables declaration//GEN-END:variables
    private int returnStatus = RET_CANCEL;

}
