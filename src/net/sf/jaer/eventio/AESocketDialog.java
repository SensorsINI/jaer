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
public class AESocketDialog extends javax.swing.JDialog{
    Logger log = Logger.getLogger("AESocketDialog");
    private AESocketSettings socketInterface;
    private int returnStatus = RET_CANCEL;
    /** A return status code - returned if Cancel button has been pressed */
    public static final int RET_CANCEL = 0;
    /** A return status code - returned if OK button has been pressed */
    public static final int RET_OK = 1;

    /** Creates new form AEUnicastDialog.
    @param parent the parent frame
    @param modal true to be modal (not allow access to parent GUI until dismissed
    @param socketInterface the interface to control
     */
    public AESocketDialog (java.awt.Frame parent,boolean modal,AESocketSettings socketInterface){
        super(parent,modal);
        this.socketInterface = socketInterface;
        initComponents();
        addressFirstEnabledCheckBox.setSelected(socketInterface.isAddressFirstEnabled());
        sequenceNumberEnabledCheckBox.setSelected(socketInterface.isSequenceNumberEnabled());
        hostnameTextField.setText(socketInterface.getHost());
        portTextField.setText(Integer.toString(socketInterface.getPort()));
        swapBytesCheckBox.setSelected(socketInterface.isSwapBytesEnabled());
        timestampMultiplierTextBox.setText(String.format("%.4f",socketInterface.getTimestampMultiplier()));
        use4ByteAddrTsCheckBox.setSelected(socketInterface.is4ByteAddrTimestampEnabled());
        bufferSizeTextBox.setText(Integer.toString(socketInterface.getSendBufferSize()));
        receiveBufferSizeTextBox.setText(Integer.toString(socketInterface.getReceiveBufferSize()));
        includeTimestampsCheckBox.setSelected(socketInterface.isTimestampsEnabled());
        useLocalTimestampsEnabledCheckBox.setSelected(socketInterface.isLocalTimestampEnabled());
         useBufferedStreamsCheckBox.setSelected(socketInterface.isUseBufferedStreams());
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
        int port = socketInterface.getPort();
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
        socketInterface.setHost(hostname);
        socketInterface.setPort(port);
        socketInterface.setAddressFirstEnabled(addressFirstEnabledCheckBox.isSelected());
        socketInterface.setSequenceNumberEnabled(sequenceNumberEnabledCheckBox.isSelected());
        socketInterface.setSwapBytesEnabled(swapBytesCheckBox.isSelected());
        socketInterface.set4ByteAddrTimestampEnabled(use4ByteAddrTsCheckBox.isSelected());
        socketInterface.setTimestampsEnabled(includeTimestampsCheckBox.isSelected());
        socketInterface.setLocalTimestampEnabled(useLocalTimestampsEnabledCheckBox.isSelected());
        socketInterface.setISIEnabled(useISIEnabledCheckBox.isSelected());
        socketInterface.setUseBufferedStreams(useBufferedStreamsCheckBox.isSelected());


        if(!parseBufferSize(receiveBufferSizeTextBox)){
            return false;
        }

        if(!parseBufferSize(bufferSizeTextBox)){
            return false;
        }


        try{
            float tsm = Float.parseFloat(timestampMultiplierTextBox.getText());
            socketInterface.setTimestampMultiplier(tsm);
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

    private boolean parseBufferSize(javax.swing.JTextField bufferSizeTextBox){
        try{
        int size = Integer.parseInt(bufferSizeTextBox.getText());
            socketInterface.setSendBufferSize(size);
            return true;
        } catch ( NumberFormatException e ){
            log.warning("bad buffer size:" +e.toString());
            bufferSizeTextBox.selectAll();
            return false;
        }
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
        use4ByteAddrTsCheckBox = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        bufferSizeTextBox = new javax.swing.JTextField();
        includeTimestampsCheckBox = new javax.swing.JCheckBox();
        applyButton = new javax.swing.JButton();
        useLocalTimestampsEnabledCheckBox = new javax.swing.JCheckBox();
        useISIEnabledCheckBox = new javax.swing.JCheckBox();
        receiveBufferSizeTextBox = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        useBufferedStreamsCheckBox = new javax.swing.JCheckBox();

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

        portTextField.setToolTipText("port number on host");
        portTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                portTextFieldActionPerformed(evt);
            }
        });

        jLabel5.setText("Port (default "+net.sf.jaer.eventio.AENetworkInterfaceConstants.DATAGRAM_PORT+")");

        sequenceNumberEnabledCheckBox.setText("sequenceNumberEnabled (not implemented)");
        sequenceNumberEnabledCheckBox.setToolTipText("input packets have sequence nubers as first int32 and this value is checked to detect dropped packets (default)");
        sequenceNumberEnabledCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sequenceNumberEnabledCheckBoxActionPerformed(evt);
            }
        });

        addressFirstEnabledCheckBox.setText("addressFirstEnabled (not implemented)");
        addressFirstEnabledCheckBox.setToolTipText("AEs come in address,timestamp order (default)");

        swapBytesCheckBox.setText("swapBytesEnabled");
        swapBytesCheckBox.setToolTipText("<html>Enable to swap bytes of addresses and timestamps to deal with little endian clients/servers (e.g. native intel code). <br>Java and jAER are big endian.</html>");

        jLabel2.setLabelFor(timestampMultiplierTextBox);
        jLabel2.setText("timestampMultiplier (not impl.)");

        timestampMultiplierTextBox.setToolTipText("<html>Timstamps are multiplied by this factor for incoming AEs and divided by this number for outgoing AEs.<br> jAER uses 1us timestamp ticks. <br>If the remote system uses 1ms ticks, then set timestampMultiplier to 1000.</html>");
        timestampMultiplierTextBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timestampMultiplierTextBoxActionPerformed(evt);
            }
        });

        use4ByteAddrTsCheckBox.setText("Use 4 byte addresses and timestamps (not implemented)");
        use4ByteAddrTsCheckBox.setToolTipText("jAER default is int32 addresses and timestamps  (default), but other systems can use 16 bit addresses and timestamps.");

        jLabel3.setLabelFor(bufferSizeTextBox);
        jLabel3.setText("Send BufferSize");

        bufferSizeTextBox.setToolTipText("The buffer size to use in bytes");
        bufferSizeTextBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bufferSizeTextBoxActionPerformed(evt);
            }
        });

        includeTimestampsCheckBox.setText("includeTimestamps(not implemented)");
        includeTimestampsCheckBox.setToolTipText("enable to include timestamps, disable to xmt/recieve only addresses");

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

        useISIEnabledCheckBox.setText("useInterSpikeIntervalsEnabled");
        useISIEnabledCheckBox.setToolTipText("<html>Enable to use System.nanoTime/1000 for all sent or received timstamps. <br>\nCan be useful for unsynchronized input from multple sources.");
        useISIEnabledCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useISIEnabledCheckBoxActionPerformed(evt);
            }
        });

        receiveBufferSizeTextBox.setToolTipText("The buffer size to use in bytes");
        receiveBufferSizeTextBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                receiveBufferSizeTextBoxActionPerformed(evt);
            }
        });

        jLabel4.setLabelFor(bufferSizeTextBox);
        jLabel4.setText("Receive bufferSize");

        useBufferedStreamsCheckBox.setSelected(true);
        useBufferedStreamsCheckBox.setText("useBufferedStreams");
        useBufferedStreamsCheckBox.setToolTipText("<html>Enable to use System.nanoTime/1000 for all sent or received timstamps. <br>\nCan be useful for unsynchronized input from multple sources.");
        useBufferedStreamsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useBufferedStreamsCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(use4ByteAddrTsCheckBox)
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addressFirstEnabledCheckBox)
                        .addContainerGap())
                    .addComponent(sequenceNumberEnabledCheckBox)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(swapBytesCheckBox, javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel1)
                                    .addComponent(hostnameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 252, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel5)
                                    .addComponent(portTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addComponent(jAERDefaultsButton, javax.swing.GroupLayout.Alignment.LEADING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addComponent(applyButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)
                        .addContainerGap())
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(includeTimestampsCheckBox)
                        .addGap(33, 33, 33)
                        .addComponent(useLocalTimestampsEnabledCheckBox)
                        .addContainerGap(181, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(useISIEnabledCheckBox)
                        .addContainerGap(403, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel3)
                            .addComponent(jLabel2))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(bufferSizeTextBox, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(timestampMultiplierTextBox, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel4)
                                .addGap(18, 18, 18)
                                .addComponent(receiveBufferSizeTextBox, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(useBufferedStreamsCheckBox))
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(hostnameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(portTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(sequenceNumberEnabledCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(addressFirstEnabledCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(useISIEnabledCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(swapBytesCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(use4ByteAddrTsCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(includeTimestampsCheckBox)
                    .addComponent(useLocalTimestampsEnabledCheckBox))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bufferSizeTextBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(receiveBufferSizeTextBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(timestampMultiplierTextBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(useBufferedStreamsCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 20, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jAERDefaultsButton)
                    .addComponent(cancelButton)
                    .addComponent(okButton)
                    .addComponent(applyButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    private void jAERDefaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jAERDefaultsButtonActionPerformed
        hostnameTextField.setText(AESocketSettings.DEFAULT_HOST);
        portTextField.setText(Integer.toString(AESocketSettings.DEFAULT_PORT));
        sequenceNumberEnabledCheckBox.setSelected(AESocketSettings.DEFAULT_USE_SEQUENCE_NUMBER);
        swapBytesCheckBox.setSelected(AESocketSettings.DEFAULT_SWAPBYTES_ENABLED);
        addressFirstEnabledCheckBox.setSelected(AESocketSettings.DEFAULT_ADDRESS_FIRST);
        timestampMultiplierTextBox.setText(String.format("%.4f",AESocketSettings.DEFAULT_TIMESTAMP_MULTIPLIER));
        use4ByteAddrTsCheckBox.setSelected(AESocketSettings.DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP);
        bufferSizeTextBox.setText(Integer.toString(AESocketSettings.DEFAULT_BUFFERED_STREAM_SIZE_BYTES)); // TODO mixup between AEUnicastSettings and AENetworkInterfaceConstants
        includeTimestampsCheckBox.setSelected(AESocketSettings.DEFAULT_TIMESTAMPS_ENABLED);
        useLocalTimestampsEnabledCheckBox.setSelected(AESocketSettings.DEFAULT_USE_LOCAL_TIMESTAMPS_ENABLED);
        useISIEnabledCheckBox.setSelected(AESocketSettings.DEFAULT_USE_LOCAL_TIMESTAMPS_ENABLED);
        useBufferedStreamsCheckBox.setSelected(AESocketSettings.DEFAULT_USE_BUFFERED_STREAM);
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

private void defaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_defaultsButtonActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_defaultsButtonActionPerformed

private void bufferSizeTextBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bufferSizeTextBoxActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_bufferSizeTextBoxActionPerformed

private void applyButtonActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyButtonActionPerformed
    applyChanges();
}//GEN-LAST:event_applyButtonActionPerformed

private void useISIEnabledCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useISIEnabledCheckBoxActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_useISIEnabledCheckBoxActionPerformed

private void receiveBufferSizeTextBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_receiveBufferSizeTextBoxActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_receiveBufferSizeTextBoxActionPerformed

private void useBufferedStreamsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useBufferedStreamsCheckBoxActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_useBufferedStreamsCheckBoxActionPerformed

private void sequenceNumberEnabledCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sequenceNumberEnabledCheckBoxActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_sequenceNumberEnabledCheckBoxActionPerformed

    private void doClose (int retStatus){
        returnStatus = retStatus;
        setVisible(false);
        dispose();
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox addressFirstEnabledCheckBox;
    private javax.swing.JButton applyButton;
    private javax.swing.JTextField bufferSizeTextBox;
    private javax.swing.JButton cancelButton;
    private javax.swing.JTextField hostnameTextField;
    private javax.swing.JCheckBox includeTimestampsCheckBox;
    private javax.swing.JButton jAERDefaultsButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JButton okButton;
    private javax.swing.JTextField portTextField;
    private javax.swing.JTextField receiveBufferSizeTextBox;
    private javax.swing.JCheckBox sequenceNumberEnabledCheckBox;
    private javax.swing.JCheckBox swapBytesCheckBox;
    private javax.swing.JTextField timestampMultiplierTextBox;
    private javax.swing.JCheckBox use4ByteAddrTsCheckBox;
    private javax.swing.JCheckBox useBufferedStreamsCheckBox;
    private javax.swing.JCheckBox useISIEnabledCheckBox;
    private javax.swing.JCheckBox useLocalTimestampsEnabledCheckBox;
    // End of variables declaration//GEN-END:variables

    public int getReturnStatus (){
        return returnStatus;
    }
}
