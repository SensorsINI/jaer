/*
 * WarningDialogWithDontShowPreference.java
 *
 * Created on October 2, 2008, 5:31 PM
 */
package net.sf.jaer.util;

import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
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

    private Preferences prefs = net.sf.jaer.JaerConstants.PREFS_ROOT;
    private Logger log = Logger.getLogger("net.sf.jaer");
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
    public WarningDialogWithDontShowPreference(JFrame parent, boolean modal) {
        super(parent, modal);
        initComponents();
    }

    /** Creates new form WarningDialogWithDontShowPreference 
       * @param parent parent frame to center on, or null
     * @param modal true to make dialog model, i.e. to stop other GUI interaction
     */
    public WarningDialogWithDontShowPreference(java.awt.Frame parent, boolean modal, String title, String text) {
        this(parent, modal, title, text, JOptionPane.WARNING_MESSAGE, false, JOptionPane.DEFAULT_OPTION);
    }

    /**
     * @param parent parent frame to center on, or null
     * @param modal true to make dialog modal
     * @param title dialog title (also used as don't-show-again prefs key basis)
     * @param text message body (may be HTML)
     * @param messageType {@link JOptionPane} message type (e.g. INFORMATION_MESSAGE)
     */
    public WarningDialogWithDontShowPreference(java.awt.Frame parent, boolean modal, String title, String text, int messageType) {
        this(parent, modal, title, text, messageType, false, JOptionPane.DEFAULT_OPTION);
    }

    /**
     * @param parent parent frame to center on, or null
     * @param modal true to make dialog modal
     * @param title dialog title (also used as don't-show-again prefs key basis)
     * @param text message body (may be HTML)
     * @param messageType {@link JOptionPane} message type (e.g. QUESTION_MESSAGE)
     * @param defaultDontShowAgain if no preference is stored yet, initial state of Don't show again (true = checked)
     */
    public WarningDialogWithDontShowPreference(java.awt.Frame parent, boolean modal, String title, String text, int messageType, boolean defaultDontShowAgain) {
        this(parent, modal, title, text, messageType, defaultDontShowAgain, JOptionPane.DEFAULT_OPTION);
    }

    /**
     * @param parent parent frame to center on, or null
     * @param modal true to make dialog modal
     * @param title dialog title (also used as don't-show-again prefs key basis)
     * @param text message body (may be HTML)
     * @param messageType {@link JOptionPane} message type (e.g. QUESTION_MESSAGE)
     * @param defaultDontShowAgain if no preference is stored yet, initial state of Don't show again (true = checked)
     * @param optionType {@link JOptionPane} option type (e.g. OK_CANCEL_OPTION); {@link JOptionPane#DEFAULT_OPTION} leaves the form default
     */
    public WarningDialogWithDontShowPreference(java.awt.Frame parent, boolean modal, String title, String text, int messageType, boolean defaultDontShowAgain, int optionType) {
        super(parent, modal);
        initComponents();
        optionPane.setMessage(text);
        key = title;
        setTitle(title);
        optionPane.setMessageType(messageType);
        if (optionType != JOptionPane.DEFAULT_OPTION) {
            optionPane.setOptionType(optionType);
        }
        dontShowAgainCheckBox.setSelected(defaultDontShowAgain);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
    }

    /**
     * True if the user confirmed (OK/Yes) or the warning is already disabled.
     * Call after {@link #setVisible(boolean)} for a confirmation dialog.
     */
    public boolean isConfirmed() {
        if (isWarningDisabled()) {
            return true;
        }
        Object v = optionPane.getValue();
        if (v == null || v == JOptionPane.UNINITIALIZED_VALUE) {
            return false;
        }
        if (Integer.valueOf(JOptionPane.OK_OPTION).equals(v)) {
            return true;
        }
        return JOptionPane.OK_OPTION == returnStatus;
    }

    private boolean shouldStoreDontShowPreference(Object optionValue) {
        if (optionPane.getOptionType() == JOptionPane.OK_CANCEL_OPTION
                || optionPane.getOptionType() == JOptionPane.YES_NO_OPTION
                || optionPane.getOptionType() == JOptionPane.YES_NO_CANCEL_OPTION) {
            return Integer.valueOf(JOptionPane.OK_OPTION).equals(optionValue)
                    || Integer.valueOf(JOptionPane.YES_OPTION).equals(optionValue);
        }
        return true;
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
        if (shouldStoreDontShowPreference(JOptionPane.CANCEL_OPTION)) {
            log.info("storing preference for " + prefsKey() + "=" + dontShowAgainCheckBox.isSelected());
            prefs.putBoolean(prefsKey(), dontShowAgainCheckBox.isSelected());
        }
        doClose(RET_CANCEL);
    }//GEN-LAST:event_closeDialog

private void optionPanePropertyChange (java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_optionPanePropertyChange
    String prop = evt.getPropertyName();

    if (isVisible()
            && (evt.getSource() == optionPane)
            && (prop.equals(JOptionPane.VALUE_PROPERTY))) {
        Object v = optionPane.getValue();
        if (Integer.valueOf(JOptionPane.OK_OPTION).equals(v) || Integer.valueOf(JOptionPane.YES_OPTION).equals(v)) {
            returnStatus = RET_OK;
        } else {
            returnStatus = RET_CANCEL;
        }
        if (shouldStoreDontShowPreference(v)) {
            log.info("storing preference for " + prefsKey() + "=" + dontShowAgainCheckBox.isSelected());
            prefs.putBoolean(prefsKey(), dontShowAgainCheckBox.isSelected());
        }
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
