/*
 * AEUnicastDialog.java
 *
 * Created on April 25, 2008, 8:40 AM
 */
package net.sf.jaer.eventio;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
/**
 * Dialog for unicast connections.
 * @author  tobi
 */
public class AEUnicastDialog extends javax.swing.JDialog{
    Logger log = Logger.getLogger("AEUnicastDialog");
    private AEUnicastSettings unicastInterface;
    private int returnStatus = RET_CANCEL;
    /** A return status code - returned if Cancel button has been pressed */
    public static final int RET_CANCEL = 0;
    /** A return status code - returned if OK button has been pressed */
    public static final int RET_OK = 1;

    /** Creates new form AEUnicastDialog.
    @param parent the parent frame
    @param modal true to be modal (not allow access to parent GUI until dismissed
    @param unicastInterface the interface to control
     */
    public AEUnicastDialog (java.awt.Frame parent,boolean modal,AEUnicastSettings unicastInterface){
        super(parent,modal);
        this.unicastInterface = unicastInterface;
        initComponents();
        addressFirstEnabledCheckBox.setSelected(unicastInterface.isAddressFirstEnabled());
        sequenceNumberEnabledCheckBox.setSelected(unicastInterface.isSequenceNumberEnabled());
        cAERDisplayEnabledCheckBox.setSelected(unicastInterface.iscAERDisplayEnabled());
        hostnameTextField.setText(unicastInterface.getHost());
        portTextField.setText(Integer.toString(unicastInterface.getPort()));
        swapBytesCheckBox.setSelected(unicastInterface.isSwapBytesEnabled());
        timestampMultiplierTextBox.setText(String.format("%.4f",unicastInterface.getTimestampMultiplier()));
        use4ByteAddrTsCheckBox.setSelected(unicastInterface.is4ByteAddrTimestampEnabled());
        bufferSizeTextBox.setText(Integer.toString(unicastInterface.getBufferSize()));
        includeTimestampsCheckBox.setSelected(unicastInterface.isTimestampsEnabled());
        useLocalTimestampsEnabledCheckBox.setSelected(unicastInterface.isLocalTimestampEnabled());
        spinnakerProtocolEnabledCB.setSelected(unicastInterface.isSpinnakerProtocolEnabled());
        KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0,false);
        Action escapeAction = new AbstractAction(){
            public void actionPerformed (ActionEvent e){
                dispose();
            }
        };
        addWindowListener(new WindowAdapter(){
            public void windowActivated (WindowEvent evt){
                okButton.requestFocusInWindow();
                removeWindowListener(this);
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escape,"ESCAPE");
        getRootPane().getActionMap().put("ESCAPE",escapeAction);

    }

    /** Returns false on error, true on success */
    private boolean applyChanges (){
//        unicastInterface.close();
        int port = unicastInterface.getPort();
        if ( hostnameTextField.getText() == null ){
            log.warning("hostname can not be blank");
            hostnameTextField.selectAll();
            return false;
        }
        String hostname = hostnameTextField.getText();
        try{
            port = Integer.parseInt(portTextField.getText());
        } catch ( NumberFormatException e ){
             log.warning(e.toString());
           portTextField.selectAll();
            return false;
        }
        unicastInterface.setHost(hostname);
        unicastInterface.setPort(port);
        unicastInterface.setAddressFirstEnabled(addressFirstEnabledCheckBox.isSelected());
        unicastInterface.setSequenceNumberEnabled(sequenceNumberEnabledCheckBox.isSelected());
        unicastInterface.setCAERDisplayEnabled(cAERDisplayEnabledCheckBox.isSelected());
        unicastInterface.setSwapBytesEnabled(swapBytesCheckBox.isSelected());
        unicastInterface.set4ByteAddrTimestampEnabled(use4ByteAddrTsCheckBox.isSelected());
        unicastInterface.setTimestampsEnabled(includeTimestampsCheckBox.isSelected());
        unicastInterface.setLocalTimestampEnabled(useLocalTimestampsEnabledCheckBox.isSelected());
        try{
            int size = Integer.parseInt(bufferSizeTextBox.getText());
            unicastInterface.setBufferSize(size);
        } catch ( NumberFormatException e ){
            log.warning("bad buffer size:" +e.toString());
            bufferSizeTextBox.selectAll();
            return false;
        }
        try{
            float tsm = Float.parseFloat(timestampMultiplierTextBox.getText());
            unicastInterface.setTimestampMultiplier(tsm);
        } catch ( NumberFormatException e ){
            log.warning("bad timestamp multiplier (Are you using \",\" as the decimal point? Use \".\" instead.): "+e.toString());
            timestampMultiplierTextBox.selectAll();
            return false;
        }
//        try{
//            unicastInterface.open();
//        } catch ( IOException e ){
//            log.warning(e.toString());
//            return false;
//        }
        return true;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jAERDefaultsButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        hostnameTextField = new javax.swing.JTextField();
        portTextField = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        sequenceNumberEnabledCheckBox = new javax.swing.JCheckBox();
        addressFirstEnabledCheckBox = new javax.swing.JCheckBox();
        swapBytesCheckBox = new javax.swing.JCheckBox();
        jLabel2 = new javax.swing.JLabel();
        timestampMultiplierTextBox = new javax.swing.JTextField();
        tdsDefaultsButton = new javax.swing.JButton();
        use4ByteAddrTsCheckBox = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        bufferSizeTextBox = new javax.swing.JTextField();
        includeTimestampsCheckBox = new javax.swing.JCheckBox();
        applyButton = new javax.swing.JButton();
        useLocalTimestampsEnabledCheckBox = new javax.swing.JCheckBox();
        spinnakerProtocolEnabledCB = new javax.swing.JCheckBox();
        cAERDisplayEnabledCheckBox = new javax.swing.JCheckBox();
        secDvsButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("AEUnicastDialog");
        setName("AEUnicastDialog"); // NOI18N

        jAERDefaultsButton.setMnemonic('d');
        jAERDefaultsButton.setText("jAER Defaults");
        jAERDefaultsButton.setToolTipText("Load default values for jAER applications into dialog");
        jAERDefaultsButton.setDefaultCapable(false);
        jAERDefaultsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jAERDefaultsButtonActionPerformed(evt);
                defaultsButtonActionPerformed(evt);
            }
        });

        okButton.setMnemonic('o');
        okButton.setText("OK");
        okButton.setToolTipText("Apply new settings and close dialog");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });
        okButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                okButtonKeyPressed(evt);
            }
        });

        cancelButton.setText("Cancel");
        cancelButton.setToolTipText("Cancel changes");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        jLabel1.setText("Hostname e.g. localhost");

        hostnameTextField.setText("localhost");
        hostnameTextField.setToolTipText("host from which to recieve events");
        hostnameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hostnameTextFieldActionPerformed(evt);
            }
        });

        portTextField.setToolTipText("port number on host");
        portTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                portTextFieldActionPerformed(evt);
            }
        });

        jLabel5.setText("Port (default "+net.sf.jaer.eventio.AENetworkInterfaceConstants.DATAGRAM_PORT+")");

        sequenceNumberEnabledCheckBox.setText("sequenceNumberEnabled");
        sequenceNumberEnabledCheckBox.setToolTipText("input packets have sequence nubers as first int32 and this value is checked to detect dropped packets (default)");

        addressFirstEnabledCheckBox.setText("addressFirstEnabled (uncheck to xmit/receive timestamp first, if enabled)");
        addressFirstEnabledCheckBox.setToolTipText("AEs come in address,timestamp order (default)");
        addressFirstEnabledCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addressFirstEnabledCheckBoxActionPerformed(evt);
            }
        });

        swapBytesCheckBox.setText("swapBytesEnabled (check for little endian, uncheck for big endian Java order");
        swapBytesCheckBox.setToolTipText("<html>Enable to swap bytes of addresses and timestamps to deal with little endian clients/servers (e.g. native intel code). <br>Java and jAER are big endian.</html>");
        swapBytesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                swapBytesCheckBoxActionPerformed(evt);
            }
        });

        jLabel2.setLabelFor(timestampMultiplierTextBox);
        jLabel2.setText("timestampMultiplier");

        timestampMultiplierTextBox.setToolTipText("<html>Timstamps are multiplied by this factor for incoming AEs and divided by this number for outgoing AEs.<br> jAER uses 1us timestamp ticks. <br>If the remote system uses 1ms ticks, then set timestampMultiplier to 1000.</html>");
        timestampMultiplierTextBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timestampMultiplierTextBoxActionPerformed(evt);
            }
        });

        tdsDefaultsButton.setMnemonic('d');
        tdsDefaultsButton.setText("ARC TDS Defaults");
        tdsDefaultsButton.setToolTipText("Load default values for ARC smart eye TDS sensor into dialog");
        tdsDefaultsButton.setDefaultCapable(false);
        tdsDefaultsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tdsDefaultsButtonActionPerformed(evt);
            }
        });

        use4ByteAddrTsCheckBox.setText("Use 4 byte addresses and timestamps (uncheck to use 2 byte addr/timestamps)");
        use4ByteAddrTsCheckBox.setToolTipText("jAER default is int32 addresses and timestamps  (default), but other systems can use 16 bit addresses and timestamps. If 2 byte option is selected, then addressFirstEnabled option is the only possible one.");

        jLabel3.setLabelFor(bufferSizeTextBox);
        jLabel3.setText("bufferSize");

        bufferSizeTextBox.setToolTipText("The buffer size to use in bytes");
        bufferSizeTextBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bufferSizeTextBoxActionPerformed(evt);
            }
        });

        includeTimestampsCheckBox.setText("includeTimestamps");
        includeTimestampsCheckBox.setToolTipText("Enable to include timestamps, disable to xmt/recieve only addresses. If timestamps are not enabled, then each event is either 4 byte or 2 byte value in UDP packet.");

        applyButton.setMnemonic('a');
        applyButton.setText("Apply");
        applyButton.setToolTipText("Apply new settings immediately (not working yet)");
        applyButton.setEnabled(false);
        applyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyButtonActionPerformed(evt);
            }
        });

        useLocalTimestampsEnabledCheckBox.setText("useLocalTimestampsEnabled");
        useLocalTimestampsEnabledCheckBox.setToolTipText("<html>Enable to use System.nanoTime/1000 for all sent or received timstamps. <br>\nCan be useful for unsynchronized input from multple sources.");

        spinnakerProtocolEnabledCB.setText("spinnakerProtocolEnabled (experimental)");
        spinnakerProtocolEnabledCB.setToolTipText("Enables output in SpiNNaker protocol as determined in CapoCaccia 2015");
        spinnakerProtocolEnabledCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                spinnakerProtocolEnabledCBActionPerformed(evt);
            }
        });

        cAERDisplayEnabledCheckBox.setText("cAERDisplayEnabled");
        cAERDisplayEnabledCheckBox.setToolTipText("Enable for UDP streams coming from cAER, which have a 28 byte header and use the Jaer3BufferParser to parse the stream. Has no effect for AEUnicastOutput streams. ");
        cAERDisplayEnabledCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cAERDisplayEnabledCheckBoxActionPerformed(evt);
            }
        });

        secDvsButton.setMnemonic('d');
        secDvsButton.setText("SEC DVS defaults ");
        secDvsButton.setToolTipText("Load defaults for recieving data from SEC DVS via Gen2 streamer via UDP");
        secDvsButton.setActionCommand("set options for SEC DVS UDP input");
        secDvsButton.setDefaultCapable(false);
        secDvsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                secDvsDefaultsButAction(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(swapBytesCheckBox)
                    .addComponent(spinnakerProtocolEnabledCB)
                    .addComponent(useLocalTimestampsEnabledCheckBox)
                    .addComponent(use4ByteAddrTsCheckBox)
                    .addComponent(addressFirstEnabledCheckBox)
                    .addComponent(sequenceNumberEnabledCheckBox)
                    .addComponent(includeTimestampsCheckBox)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel3)
                            .addComponent(jLabel2))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(bufferSizeTextBox, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(timestampMultiplierTextBox, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(cAERDisplayEnabledCheckBox)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(hostnameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 416, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel1)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jAERDefaultsButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tdsDefaultsButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(secDvsButton)))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(applyButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(cancelButton))
                            .addComponent(jLabel5)
                            .addComponent(portTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hostnameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(portTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(sequenceNumberEnabledCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(addressFirstEnabledCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(swapBytesCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(use4ByteAddrTsCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(includeTimestampsCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(useLocalTimestampsEnabledCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(spinnakerProtocolEnabledCB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cAERDisplayEnabledCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bufferSizeTextBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(timestampMultiplierTextBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jAERDefaultsButton)
                    .addComponent(cancelButton)
                    .addComponent(tdsDefaultsButton)
                    .addComponent(okButton)
                    .addComponent(applyButton)
                    .addComponent(secDvsButton))
                .addGap(29, 29, 29))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    private void jAERDefaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jAERDefaultsButtonActionPerformed
        hostnameTextField.setText(AEUnicastSettings.DEFAULT_HOST);
        portTextField.setText(Integer.toString(AEUnicastSettings.DEFAULT_PORT));
        sequenceNumberEnabledCheckBox.setSelected(AEUnicastSettings.DEFAULT_USE_SEQUENCE_NUMBER);
        swapBytesCheckBox.setSelected(AEUnicastSettings.DEFAULT_SWAPBYTES_ENABLED);
        addressFirstEnabledCheckBox.setSelected(AEUnicastSettings.DEFAULT_ADDRESS_FIRST);
        timestampMultiplierTextBox.setText(String.format("%.4f",AEUnicastSettings.DEFAULT_TIMESTAMP_MULTIPLIER));
        use4ByteAddrTsCheckBox.setSelected(AEUnicastSettings.DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP);
        bufferSizeTextBox.setText(Integer.toString(AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES)); // TODO mixup between AEUnicastSettings and AENetworkInterfaceConstants
        includeTimestampsCheckBox.setSelected(AEUnicastSettings.DEFAULT_TIMESTAMPS_ENABLED);
        useLocalTimestampsEnabledCheckBox.setSelected(AEUnicastSettings.DEFAULT_USE_LOCAL_TIMESTAMPS_ENABLED);
}//GEN-LAST:event_jAERDefaultsButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        if ( !applyChanges() ){
            return;
        }

        doClose(RET_OK);
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        doClose(RET_CANCEL);
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void portTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_portTextFieldActionPerformed
        try{
            int port = Integer.parseInt(portTextField.getText());
        } catch ( NumberFormatException e ){
            log.warning(e.toString());
            portTextField.selectAll();
        }
    }//GEN-LAST:event_portTextFieldActionPerformed

    private void timestampMultiplierTextBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timestampMultiplierTextBoxActionPerformed
        // TODO add your handling code here:
}//GEN-LAST:event_timestampMultiplierTextBoxActionPerformed

    private void okButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_okButtonKeyPressed
        if ( evt.getKeyCode() == KeyEvent.VK_ENTER ){
            okButtonActionPerformed(null);
        }
    }//GEN-LAST:event_okButtonKeyPressed

private void tdsDefaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tdsDefaultsButtonActionPerformed
    hostnameTextField.setText(AEUnicastSettings.DEFAULT_HOST);
    portTextField.setText(Integer.toString(AEUnicastSettings.ARC_TDS_STREAM_PORT));
    sequenceNumberEnabledCheckBox.setSelected(AEUnicastSettings.ARC_TDS_SEQUENCE_NUMBERS_ENABLED);
    swapBytesCheckBox.setSelected(AEUnicastSettings.ARC_TDS_SWAPBYTES_ENABLED);
    addressFirstEnabledCheckBox.setSelected(AEUnicastSettings.ARC_TDS_ADDRESS_BYTES_FIRST_ENABLED);
    timestampMultiplierTextBox.setText(String.format("%.6f",AEUnicastSettings.ARC_TDS_TIMESTAMP_MULTIPLIER));
    use4ByteAddrTsCheckBox.setSelected(AEUnicastSettings.ARC_TDS_4_BYTE_ADDR_AND_TIMESTAMPS);
    bufferSizeTextBox.setText(Integer.toString(AEUnicastSettings.ARC_TDS_BUFFER_SIZE));
    includeTimestampsCheckBox.setSelected(AEUnicastSettings.DEFAULT_TIMESTAMPS_ENABLED);
    useLocalTimestampsEnabledCheckBox.setSelected(AEUnicastSettings.DEFAULT_USE_LOCAL_TIMESTAMPS_ENABLED);

}//GEN-LAST:event_tdsDefaultsButtonActionPerformed

private void defaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_defaultsButtonActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_defaultsButtonActionPerformed

private void bufferSizeTextBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bufferSizeTextBoxActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_bufferSizeTextBoxActionPerformed

private void applyButtonActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyButtonActionPerformed
    applyChanges();
}//GEN-LAST:event_applyButtonActionPerformed

    private void spinnakerProtocolEnabledCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spinnakerProtocolEnabledCBActionPerformed
        unicastInterface.setSpinnakerProtocolEnabled(spinnakerProtocolEnabledCB.isSelected());
    }//GEN-LAST:event_spinnakerProtocolEnabledCBActionPerformed

    private void cAERDisplayEnabledCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cAERDisplayEnabledCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_cAERDisplayEnabledCheckBoxActionPerformed

    private void addressFirstEnabledCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addressFirstEnabledCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_addressFirstEnabledCheckBoxActionPerformed

    private void hostnameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hostnameTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_hostnameTextFieldActionPerformed

    private void swapBytesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_swapBytesCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_swapBytesCheckBoxActionPerformed

    private void secDvsDefaultsButAction(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_secDvsDefaultsButAction
        unicastInterface.setSecDvsProtocolEnabled(secDvsButton.isSelected());
        portTextField.setText(Integer.toString(AEUnicastSettings.SEC_DVS_STREAMER_PORT));
    }//GEN-LAST:event_secDvsDefaultsButAction

    private void doClose (int retStatus){
        returnStatus = retStatus;
        setVisible(false);
        dispose();
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox addressFirstEnabledCheckBox;
    private javax.swing.JButton applyButton;
    private javax.swing.JTextField bufferSizeTextBox;
    private javax.swing.JCheckBox cAERDisplayEnabledCheckBox;
    private javax.swing.JButton cancelButton;
    private javax.swing.JTextField hostnameTextField;
    private javax.swing.JCheckBox includeTimestampsCheckBox;
    private javax.swing.JButton jAERDefaultsButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JButton okButton;
    private javax.swing.JTextField portTextField;
    private javax.swing.JButton secDvsButton;
    private javax.swing.JCheckBox sequenceNumberEnabledCheckBox;
    private javax.swing.JCheckBox spinnakerProtocolEnabledCB;
    private javax.swing.JCheckBox swapBytesCheckBox;
    private javax.swing.JButton tdsDefaultsButton;
    private javax.swing.JTextField timestampMultiplierTextBox;
    private javax.swing.JCheckBox use4ByteAddrTsCheckBox;
    private javax.swing.JCheckBox useLocalTimestampsEnabledCheckBox;
    // End of variables declaration//GEN-END:variables

    public int getReturnStatus (){
        return returnStatus;
    }
}
