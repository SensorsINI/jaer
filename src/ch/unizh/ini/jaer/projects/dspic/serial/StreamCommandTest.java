/*
Copyright June 13, 2011 Andreas Steiner, Inst. of Neuroinformatics, UNI-ETH Zurich

This file is part of dsPICserial.

dsPICserial is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

dsPICserial is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with dsPICserial.  If not, see <http://www.gnu.org/licenses/>.
*/


package ch.unizh.ini.jaer.projects.dspic.serial;

import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * simple GUI for playing around with <code>StreamCommand</code> that
 * lets you send text commands via a text field and shows the answers
 * <br /><br />
 *
 * additionally, the streamed messages can be displayed live; at the moment,
 * this is done by RetinaPanel, but the program can easily be extended to
 * show other streamed content. this example here should work with
 * https://sourceforge.net/p/jaer/code/HEAD/tree/devices/firmware/dsPICserial_MDC2D/
 * version 6.2
 * <br /><br />
 *
 * <b>TROUBLE SHOOTING</b>
 * <ul>
 *  <li>    If not connection can be established, double-check the baudrate
 *          setting in the firmware </li>
 *  <li>    If still no connection can be established, try a "standard" baudrate
 *          setting, such as 115.2 kbaud (and change the firmware accordingly) </li>
 * </ul>
 *
 * @see StreamCommand
 * @author andstein
 */
public class StreamCommandTest extends javax.swing.JFrame
        implements StreamCommandListener,ChangeListener {

    // defines the message type that in turn affects how the message
    // is interpreted
    public final static int MSG_RESET			=0x0000;
    public final static int MSG_FRAME_MOTION            =0x0005;
    public final static int MSG_ANSWER                  =0x2020;

    private StreamCommand streamer;
    private RetinaPanel retinaPanel;
    private boolean checkingVersion;

    /** Creates new form StreamCommandTest */
    public StreamCommandTest() {
        initComponents();

        // initialize available ports
        streamer= new StreamCommand(this,Integer.parseInt(baudText.getText()));
        portsCombo.removeAllItems();
        for(String portName : streamer.getPortNames()) {
			portsCombo.addItem(portName);
		}

        // add display
        retinaPanel= new RetinaPanel(20,20);
        imagePane.setLayout(new BorderLayout());
        imagePane.add(retinaPanel,BorderLayout.CENTER);

        vectorScalingSlider.addChangeListener(this);

        // initialize other elements with streamer
        syncCB.setSelected(streamer.getSynced());

        // automatically update messages/s display
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
			public void run()
            {
                StreamCommandTest.this.updateMps();
            }
        }, 1000, 1000);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                streamer.close();
                System.exit(0);
            }
        });
    }

    private void logString(String str)
    {
        logText.append( str + "\n");
        logText.scrollRectToVisible(new Rectangle(0,logText.getHeight()-2,1,1));
    }


    public void commandSent(String cmd) {
        logString("> " + cmd);
    }

    @Override
	public void answerReceived(String cmd,String answer) {
        if (checkingVersion)
        {
            if ((answer.charAt(0)!='!') && answer.contains("version")) {
                portStatusText.setText("(verified)");
                cmdText.setEnabled(true);
            }
			else {
				portStatusText.setText("ERROR");
			}
            checkingVersion= false;
        }

        // make help message more readable
        if (answer.contains("help")) {
			answer = answer.replace("; ", "\n\t");
		}

        logString("("+cmd+")< " + answer);
    }

    public void updateMps() {
        mpsText.setText(Double.toString(streamer.getMps()) + "/s");
    }

    private String exactTimeString() {
        Calendar x= Calendar.getInstance();
        return String.format("%02d:%02d:%02d.%03d",
                x.get(Calendar.HOUR_OF_DAY),x.get(Calendar.MINUTE),
                x.get(Calendar.SECOND),x.get(Calendar.MILLISECOND));
    }

    @Override
	public void messageReceived(StreamCommandMessage message) {
//        logString("[" + values.length + "x" + values[0].length + "]");

        if (logMessageCB.isSelected()) {
			logString(String.format("%s: type=0x%02x length=%d",
                    exactTimeString(),message.getType(),message.getLength()));
		}

        // char array of pixel values
        if (RetinaMessage.canParse(message)) {
            RetinaMessage rmsg= new RetinaMessage(message);
            retinaPanel.setDataFromMessage(rmsg);
            retinaStatusPanel.setText(rmsg.getErrorInformation());
        }
		else {
			retinaStatusPanel.setText("");
		}


        updateMps();
    }

    @Override
	public void streamingError(int error,String msg) {
        logString("*** " + msg);
    }

    private String hex4(char c) {
        if (c>=10) {
			return Character.toString((char) ((c - 10) + 'a'));
		}
        return Character.toString((char) (c + '0'));
    }

    @Override
	public void unsyncedChar(char c) {
        if (hexCB.isSelected())
        {
            logText.append(hex4((char) (c >> 4)) + hex4((char) (c & 0xf)) + " ");
        }
        else
        {
            logText.append(Character.toString(c));
        }
    }


    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        logText = new javax.swing.JTextArea();
        cmdText = new javax.swing.JTextField();
        clearButton = new javax.swing.JButton();
        hexCB = new javax.swing.JCheckBox();
        connectionPanel = new javax.swing.JPanel();
        portsCombo = new javax.swing.JComboBox();
        portStatusText = new javax.swing.JTextField();
        checkBox = new javax.swing.JCheckBox();
        baudText = new javax.swing.JTextField();
        baudLabel = new javax.swing.JLabel();
        messagesPanel = new javax.swing.JPanel();
        imagePane = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        mpsText = new javax.swing.JTextField();
        syncCB = new javax.swing.JCheckBox();
        resetCode = new javax.swing.JButton();
        dumpButton = new javax.swing.JButton();
        autogainCB = new javax.swing.JCheckBox();
        vectorScalingSlider = new javax.swing.JSlider();
        retinaStatusPanel = new javax.swing.JTextField();
        multiButton = new javax.swing.JButton();
        logMessageCB = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("dsPIC MDC2D test bench");

        jLabel1.setFont(new java.awt.Font("Monospaced", 0, 11));
        jLabel1.setText("use either with MDC2Dv2 or EPFL's develBoard (see documentation jAER_test.mcp)");

        logText.setColumns(20);
        logText.setEditable(false);
        logText.setFont(new java.awt.Font("Monospaced", 0, 11)); // NOI18N
        logText.setLineWrap(true);
        logText.setRows(5);
        jScrollPane1.setViewportView(logText);

        cmdText.setFont(new java.awt.Font("Monospaced", 0, 11));
        cmdText.setEnabled(false);
        cmdText.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdTextActionPerformed(evt);
            }
        });

        clearButton.setText("clear");
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });

        hexCB.setText("hex");

        connectionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Connection"));

        portsCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "error!" }));
        portsCombo.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                portsComboActionPerformed(evt);
            }
        });

        portStatusText.setEditable(false);
        portStatusText.setFont(new java.awt.Font("Monospaced", 0, 11));
        portStatusText.setText("status");
        portStatusText.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                portStatusTextActionPerformed(evt);
            }
        });

        checkBox.setSelected(true);
        checkBox.setText("check");

        baudText.setFont(new java.awt.Font("Monospaced", 0, 11));
        baudText.setHorizontalAlignment(SwingConstants.RIGHT);
        baudText.setText("618964");
        baudText.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                baudTextActionPerformed(evt);
            }
        });

        baudLabel.setText("baud");

        javax.swing.GroupLayout connectionPanelLayout = new javax.swing.GroupLayout(connectionPanel);
        connectionPanel.setLayout(connectionPanelLayout);
        connectionPanelLayout.setHorizontalGroup(
            connectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, connectionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(connectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(connectionPanelLayout.createSequentialGroup()
                        .addComponent(baudText, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(baudLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 11, Short.MAX_VALUE)
                        .addComponent(checkBox))
                    .addGroup(connectionPanelLayout.createSequentialGroup()
                        .addComponent(portsCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(portStatusText, javax.swing.GroupLayout.DEFAULT_SIZE, 121, Short.MAX_VALUE)))
                .addContainerGap())
        );
        connectionPanelLayout.setVerticalGroup(
            connectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(connectionPanelLayout.createSequentialGroup()
                .addGroup(connectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(portsCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(portStatusText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(connectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBox)
                    .addComponent(baudText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(baudLabel)))
        );

        messagesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Messages"));

        imagePane.setPreferredSize(new java.awt.Dimension(400, 400));

        javax.swing.GroupLayout imagePaneLayout = new javax.swing.GroupLayout(imagePane);
        imagePane.setLayout(imagePaneLayout);
        imagePaneLayout.setHorizontalGroup(
            imagePaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 200, Short.MAX_VALUE)
        );
        imagePaneLayout.setVerticalGroup(
            imagePaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 200, Short.MAX_VALUE)
        );

        jLabel2.setText("receiving");

        mpsText.setEditable(false);
        mpsText.setFont(new java.awt.Font("Monospaced", 0, 11));
        mpsText.setText("00.0/s");

        syncCB.setText("sync");
        syncCB.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                syncCBActionPerformed(evt);
            }
        });

        resetCode.setText("reset");
        resetCode.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetCodeActionPerformed(evt);
            }
        });

        dumpButton.setText("dump");
        dumpButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                dumpButtonActionPerformed(evt);
            }
        });

        autogainCB.setText("autogain");
        autogainCB.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                autogainCBActionPerformed(evt);
            }
        });

        vectorScalingSlider.setMajorTickSpacing(1);
        vectorScalingSlider.setMaximum(5);
        vectorScalingSlider.setValue(0);

        retinaStatusPanel.setEditable(false);
        retinaStatusPanel.setFont(new java.awt.Font("Monospaced", 0, 11)); // NOI18N

        javax.swing.GroupLayout messagesPanelLayout = new javax.swing.GroupLayout(messagesPanel);
        messagesPanel.setLayout(messagesPanelLayout);
        messagesPanelLayout.setHorizontalGroup(
            messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(messagesPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(syncCB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 31, Short.MAX_VALUE)
                .addComponent(jLabel2)
                .addGap(18, 18, 18)
                .addComponent(mpsText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(19, 19, 19))
            .addGroup(messagesPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(resetCode)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dumpButton)
                .addContainerGap(80, Short.MAX_VALUE))
            .addGroup(messagesPanelLayout.createSequentialGroup()
                .addGroup(messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, messagesPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(vectorScalingSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(autogainCB))
                    .addComponent(imagePane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(10, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, messagesPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(retinaStatusPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 192, Short.MAX_VALUE)
                .addContainerGap())
        );
        messagesPanelLayout.setVerticalGroup(
            messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(messagesPanelLayout.createSequentialGroup()
                .addGroup(messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(syncCB)
                    .addComponent(jLabel2)
                    .addComponent(mpsText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(imagePane, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(vectorScalingSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(autogainCB))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 6, Short.MAX_VALUE)
                .addComponent(retinaStatusPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(resetCode)
                    .addComponent(dumpButton))
                .addContainerGap())
        );

        multiButton.setText("multi !");
        multiButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                multiButtonActionPerformed(evt);
            }
        });

        logMessageCB.setText("log msgs");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, 715, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(messagesPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 216, Short.MAX_VALUE)
                            .addComponent(connectionPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(logMessageCB)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(hexCB)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(clearButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 265, Short.MAX_VALUE)
                                .addComponent(multiButton))
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 493, Short.MAX_VALUE)
                            .addComponent(cmdText, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 493, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(cmdText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 394, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(multiButton)
                                .addComponent(hexCB)
                                .addComponent(clearButton))
                            .addComponent(logMessageCB)))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(connectionPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(messagesPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cmdTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdTextActionPerformed
        streamer.sendCommand(cmdText.getText());
        cmdText.setText("");
    }//GEN-LAST:event_cmdTextActionPerformed

    private void syncCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syncCBActionPerformed
        logString("---");
        streamer.setSynced(syncCB.isSelected());
    }//GEN-LAST:event_syncCBActionPerformed

    private void resetCodeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetCodeActionPerformed
        logString("NOT IMPLEMENTED YET");
    }//GEN-LAST:event_resetCodeActionPerformed

    private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearButtonActionPerformed
        logText.setText("");
    }//GEN-LAST:event_clearButtonActionPerformed

    private void dumpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dumpButtonActionPerformed
        logText.append("\n---dump\n");
        logText.append(streamer.dump());
        logText.append("\n---end\n");
    }//GEN-LAST:event_dumpButtonActionPerformed

    private void portStatusTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_portStatusTextActionPerformed
        // TODO add your handling code here:
}//GEN-LAST:event_portStatusTextActionPerformed

    private void portsComboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_portsComboActionPerformed
        if (portsCombo.getItemCount() <1) {
			return;
		}

        String portName= (String) portsCombo.getSelectedItem();
        logString("\nCONNECTING TO "+portName+"...");

        if (streamer.isConnected()) {
			streamer.close();
		}

        try {
            streamer.connect(portName);

            cmdText.setEnabled(true);
            portStatusText.setText("(connected)");

            if (checkBox.isSelected()) {
                portStatusText.setText("checking version...");
                checkingVersion= true;
                streamer.sendCommand("version");
                cmdText.setEnabled(false);
            }

        } catch (PortInUseException ex) {
            portStatusText.setText("port in use");
            cmdText.setEnabled(false);
        } catch (Exception ex) {
            portStatusText.setText("i/o error!");
            cmdText.setEnabled(false);
        }
}//GEN-LAST:event_portsComboActionPerformed

    private void autogainCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autogainCBActionPerformed
        retinaPanel.setAutoGain(autogainCB.isSelected());
    }//GEN-LAST:event_autogainCBActionPerformed

    private void multiButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_multiButtonActionPerformed
        streamer.sendCommand("version\nversion\nversion\nhelp");
    }//GEN-LAST:event_multiButtonActionPerformed

    private void baudTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_baudTextActionPerformed
        try {
            streamer.setBaudRate(Integer.parseInt(baudText.getText()));
        } catch (UnsupportedCommOperationException ex) {
            logString("*** cannot set baud rate : " + ex + "\n");
        }
    }//GEN-LAST:event_baudTextActionPerformed

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
			public void run() {
                new StreamCommandTest().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox autogainCB;
    private javax.swing.JLabel baudLabel;
    private javax.swing.JTextField baudText;
    private javax.swing.JCheckBox checkBox;
    private javax.swing.JButton clearButton;
    private javax.swing.JTextField cmdText;
    private javax.swing.JPanel connectionPanel;
    private javax.swing.JButton dumpButton;
    private javax.swing.JCheckBox hexCB;
    private javax.swing.JPanel imagePane;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JCheckBox logMessageCB;
    private javax.swing.JTextArea logText;
    private javax.swing.JPanel messagesPanel;
    private javax.swing.JTextField mpsText;
    private javax.swing.JButton multiButton;
    private javax.swing.JTextField portStatusText;
    private javax.swing.JComboBox portsCombo;
    private javax.swing.JButton resetCode;
    private javax.swing.JTextField retinaStatusPanel;
    private javax.swing.JCheckBox syncCB;
    private javax.swing.JSlider vectorScalingSlider;
    // End of variables declaration//GEN-END:variables

    @Override
	public void commandTimeout(String cmd) {
        if (checkingVersion)
        {
            portStatusText.setText("timeout");
            checkingVersion= false;
        }

        logString("*** command timeout : " + cmd);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        retinaPanel.setStretchVector((float)(Math.exp(Math.log(10)*vectorScalingSlider.getValue())));
    }


}
