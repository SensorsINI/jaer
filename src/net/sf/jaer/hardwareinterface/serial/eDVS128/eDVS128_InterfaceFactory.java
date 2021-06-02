/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

 /*
 * eDVS128_InterfaceFactory.java
 *
 * Created on Jul 19, 2011, 5:09:24 AM
 */
package net.sf.jaer.hardwareinterface.serial.eDVS128;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryChooserDialog;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;

/**
 * Factory dialog for interfaces to eDVS cameras.
 *
 * @author tobi
 */
public class eDVS128_InterfaceFactory extends javax.swing.JDialog implements HardwareInterfaceFactoryChooserDialog {

    private static Preferences prefs = Preferences.userNodeForPackage(eDVS128_InterfaceFactory.class);
    private static final Logger log = Logger.getLogger("eDVS128");
    public static final int[] SERIAL_BAUD_RATES_MBPS = {1, 2, 4, 8, 12};
    /**
     * The baud rate used by the eDVS FTDI serial port interface
     */
    public static int serialBaudRateMbps = prefs.getInt("serialBaudRateMbps", 4);
    /**
     * The address of the eDVS as it is configured to be assigned at INI on
     * WLAN-INI.
     */
    public static final String HOST = "192.168.91.62";
    /**
     * The default TCP port address of the wifi interface
     */
    public static final int TCP_PORT = 56000;
    public static final int TCP_RECEIVE_BUFFER_SIZE_BYTES = 8192;
    public static final int TCP_SEND_BUFFER_SIZE_BYTES = 1024;
    public static final boolean DEFAULT_USE_BUFFERED_STREAM = false;
    /**
     * timeout in ms for connection attempts
     */
    public static final int CONNECTION_TIMEOUT_MS = 6000; // it takes substantial time to connect to eDVS
    /**
     * timeout in ms for read/write attempts
     */
    public static final int SO_TIMEOUT = 100; // 1 means we should timeout as soon as there are no more events in the datainputstream
    /**
     * A return status code - returned if Cancel button has been pressed
     */
    public static final int RET_CANCEL = 0;
    /**
     * A return status code - returned if OK button has been pressed
     */
    public static final int RET_OK = 1;
    private int lastSerialPortIndex = prefs.getInt("eDVS128_InterfaceFactory.lastPortIndex", 0);
    // singleton
    private static eDVS128_InterfaceFactory instance = new eDVS128_InterfaceFactory();
    private HardwareInterface chosenInterface = null;
    private static final String RESCAN = "-rescan-";
    private static final String LAST_SELECTED = "lastSelected";
    private CommPort commPort = null;
    private SerialPort serialPort = null;
    private CommPortIdentifier portIdentifier = null;

    /**
     * Creates new form eDVS128_InterfaceFactory
     */
    private eDVS128_InterfaceFactory() {
        super();
        setModal(true);
        initComponents();
        setName("eDVS hardware interface chooser");

        // Close the dialog when Esc is pressed
        String cancelName = "cancel";
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancelName);
        ActionMap actionMap = getRootPane().getActionMap();
        actionMap.put(cancelName, new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                doCloseCancel();
            }
        });
//       String lastSelected = null;
//       AbstractButton defaultButton=null;
//        if ((lastSelected = prefs.get(LAST_SELECTED, null)) != null) {
//            if (lastSelected.equals("doChooseSerial")) {
//                defaultButton=okSerPortButton;
//            } else if (lastSelected.equals("doChooseSocket")) {
//                defaultButton=okSocketButton;
//            } else if (lastSelected.equals("doCloseCancel")) {
//                defaultButton=cancelButton;
//            }
//        }
//        
//
//        getRootPane().registerKeyboardAction(new ButtonClickAction(preferrerdButton), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        Thread T = new InitializePortListThread();
        T.start();
        focusLast();

    }

    private class InitializePortListThread extends Thread {

        public InitializePortListThread() {
            setName("InitializePortListThread");
            setPriority(MIN_PRIORITY);
        }

        @Override
        public void run() {
            log.info("starting thread to initialize serial port list");
            refreshSerialPortList();
            hostTF.setText(prefs.get("eDVS128_InterfaceFactory.HOST", HOST));
            portTF.setText(prefs.get("eDVS128_InterfaceFactory.TCP_PORT", Integer.toString(TCP_PORT)));
            Vector<String> brVec = new Vector();
            for (int i : SERIAL_BAUD_RATES_MBPS) {
                brVec.add(Integer.toString(i));
            }
            baudRateCB.setModel(new DefaultComboBoxModel<String>(brVec));
            for (int i = 0; i < SERIAL_BAUD_RATES_MBPS.length; i++) {
                if (SERIAL_BAUD_RATES_MBPS[i] == serialBaudRateMbps) {
                    baudRateCB.setSelectedIndex(i);
                }
            }
            focusLast();
            log.info("serial port initialization thread done");
        }
    }
    
    private HashMap<String, HardwareInterface> closemap = new HashMap();

    private void closePrevious(String s) {
        HardwareInterface hardwareInterface = closemap.get(s);
        if (hardwareInterface == null) {
            return;
        }
        try {
            hardwareInterface.close();
            log.info("closed old interface " + s + " = " + hardwareInterface);
            Thread.sleep(300); // wait added because perhaps it helps close serial port TODO check this
        } catch (Exception e) {
            log.warning(e.toString());
        }
        closemap.remove(s);
        hardwareInterface = null;
    }

    /**
     * Use this singleton instance to make new interfaces
     */
    public static HardwareInterfaceFactoryInterface instance() {
        return instance;
    }

    /**
     * Always returns 0.
     */
    @Override
    public int getNumInterfacesAvailable() {
        return 0;

    }

    /**
     * Always returns null
     */
    @Override
    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException {
        return null;
    }

    /**
     * Always returns null
     */
    @Override
    public HardwareInterface getInterface(int n) throws HardwareInterfaceException {
        return null;
    }

    @Override
    public String getGUID() {
        return "eDVS serial or network interface chooser";
    }

    @Override
    public JDialog getInterfaceChooser(AEChip chip) {
        setTitle("Choose interface for " + chip);
        if (chip != null && chip.getAeViewer() != null) {
            setLocationRelativeTo(chip.getAeViewer());
        }
        return this;
    }

    @Override
    public HardwareInterface getChosenHardwareInterface() {
        return chosenInterface;
    }

    /**
     * @return the return status of this dialog - one of RET_OK or RET_CANCEL
     */
    public int getReturnStatus() {
        return returnStatus;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        cancelButton = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        portCB = new javax.swing.JComboBox();
        okSerPortButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        refreshPortListButton = new javax.swing.JButton();
        baudRateCB = new javax.swing.JComboBox<>();
        jLabel5 = new javax.swing.JLabel();
        closeButton = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        hostTF = new javax.swing.JTextField();
        portTF = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        okSocketButton = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        defaultsButton = new javax.swing.JButton();
        pingButton = new javax.swing.JButton();

        setTitle("Serial Port Chooser");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        jPanel1.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        portCB.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        portCB.setToolTipText("The COM port. Select -rescan- to scan for COM ports.");
        portCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                portCBActionPerformed(evt);
            }
        });

        okSerPortButton.setText("Open serial port interface");
        okSerPortButton.setToolTipText("Tries to open the serial port");
        okSerPortButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okSerPortButtonActionPerformed(evt);
            }
        });

        jLabel1.setText("<html>If you are using the USB interface, then the eDVS will appear on a COM port. <p>Choose the serial port of the eDVS.<br>It is usually the <b> lower numbered port</b> of a large numbered pair of ports.");

        refreshPortListButton.setText("Refresh port list");
        refreshPortListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshPortListButtonActionPerformed(evt);
            }
        });

        baudRateCB.setToolTipText("<html>Sets the serial port baud rate in megabauds. <p>Note that the eDVS must be separately configured to set this baud rate over a serial port console link. <p>The default baud rate is 4 Mbaud.");
        baudRateCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                baudRateCBActionPerformed(evt);
            }
        });

        jLabel5.setText("Baud rate (Mbps)");

        closeButton.setText("Close");
        closeButton.setToolTipText("Closes the selected serial port.");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(portCB, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(baudRateCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(refreshPortListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 117, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(okSerPortButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(closeButton)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(portCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(okSerPortButton)
                    .addComponent(refreshPortListButton)
                    .addComponent(baudRateCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5)
                    .addComponent(closeButton))
                .addContainerGap())
        );

        getRootPane().setDefaultButton(okSerPortButton);

        jPanel2.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel2.setText("Hostname or IP address");

        hostTF.setToolTipText("The host IP address or hostname");

        portTF.setText("jTextField1");
        portTF.setToolTipText("Choose the TCP port - default is 56000");

        jLabel3.setText("TCP port");

        okSocketButton.setText("Open network interface");
        okSocketButton.setToolTipText("Tries to open the TCP socket. ");
        okSocketButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okSocketButtonActionPerformed(evt);
            }
        });

        jLabel4.setText("<html>If you are using an eDVS with wifi, choose the host and port<br> of the eDVS here and then click Open network interface. <p>The eDVS is typically configured to connect to a particular <br>hardcoded SSID with WEP and accepts a DHCP address.");

        defaultsButton.setText("Defaults");
        defaultsButton.setToolTipText("Enters default values");
        defaultsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                defaultsButtonActionPerformed(evt);
            }
        });

        pingButton.setText("Ping");
        pingButton.setToolTipText("Ping this host to see if it is there");
        pingButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pingButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel2))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(portTF, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(52, 52, 52)
                                .addComponent(pingButton))
                            .addComponent(hostTF)))
                    .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(defaultsButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(okSocketButton)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(hostTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(portTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pingButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(okSocketButton)
                    .addComponent(defaultsButton))
                .addContainerGap())
        );

        getRootPane().setDefaultButton(okSerPortButton);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cancelButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(jPanel2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cancelButton)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void okSerPortButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okSerPortButtonActionPerformed
        doChooseSerial(RET_OK);
    }//GEN-LAST:event_okSerPortButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        doCloseCancel();
    }//GEN-LAST:event_cancelButtonActionPerformed

    /**
     * Closes the dialog
     */
    private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
        doCloseCancel();
    }//GEN-LAST:event_closeDialog

    private void portCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_portCBActionPerformed
        Object o = portCB.getSelectedItem();
        if (o == RESCAN) {
            refreshSerialPortList();
        }
    }//GEN-LAST:event_portCBActionPerformed

    private void okSocketButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okSocketButtonActionPerformed
        doChooseSocket(RET_OK);
    }//GEN-LAST:event_okSocketButtonActionPerformed

    private void defaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_defaultsButtonActionPerformed
        hostTF.setText(HOST);
        portTF.setText(Integer.toString(TCP_PORT));
    }//GEN-LAST:event_defaultsButtonActionPerformed

    private void refreshPortListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshPortListButtonActionPerformed
        refreshSerialPortList();
    }//GEN-LAST:event_refreshPortListButtonActionPerformed

    private void pingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pingButtonActionPerformed
        String host = hostTF.getText();
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            InetAddress adr = Inet4Address.getByName(host);
            try {
                adr.isReachable(3000);
                JOptionPane.showMessageDialog(this, host + " is reachable. However it may not be the eDVS!");
            } catch (IOException notReachable) {
                JOptionPane.showMessageDialog(this, host + " is not reachable: " + notReachable.toString(), "Not reachable", JOptionPane.WARNING_MESSAGE);
            }
        } catch (UnknownHostException ex) {
            JOptionPane.showMessageDialog(this, host + " is unknown host: " + ex.toString(), "Host not found", JOptionPane.WARNING_MESSAGE);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }

    }//GEN-LAST:event_pingButtonActionPerformed

    private void baudRateCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_baudRateCBActionPerformed
        serialBaudRateMbps = Integer.parseInt((String) baudRateCB.getSelectedItem());
        prefs.putInt("serialBaudRateMbps", serialBaudRateMbps);
    }//GEN-LAST:event_baudRateCBActionPerformed

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        closePrevious((String) portCB.getSelectedItem());                // TODO add your handling code here:
    }//GEN-LAST:event_closeButtonActionPerformed

    private void focusLast() {
        //set last focus
        String lastSelected = null;
        if ((lastSelected = prefs.get(LAST_SELECTED, null)) != null) {
            if (lastSelected.equals("doChooseSerial")) {
                getRootPane().setDefaultButton(okSerPortButton);
            } else if (lastSelected.equals("doChooseSocket")) {
                getRootPane().setDefaultButton(okSocketButton);
            } else if (lastSelected.equals("doCloseCancel")) {
                getRootPane().setDefaultButton(cancelButton);
            }
        }
//        log.info("focused on " + lastSelected);
    }

    private void doCloseCancel() {
        prefs.put(LAST_SELECTED, "doCloseCancel");
        returnStatus = RET_CANCEL;
        setVisible(false);
        dispose();
    }

    private void doChooseSocket(int retStatus) {
        boolean success = false;
        switch (retStatus) {
            case RET_OK:
                prefs.put(LAST_SELECTED, "doChooseSerial");
                try {
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                    String host = hostTF.getText();
                    closePrevious(host);
                    int tcpport = Integer.parseInt(portTF.getText());
                    prefs.put("eDVS128_InterfaceFactory.HOST", host);
                    prefs.putInt("eDVS128_InterfaceFactory.TCP_PORT", tcpport);
                    final Socket socket = new Socket();
                    socket.setReceiveBufferSize(TCP_RECEIVE_BUFFER_SIZE_BYTES);
                    socket.setSendBufferSize(TCP_SEND_BUFFER_SIZE_BYTES);
                    socket.setSoTimeout(SO_TIMEOUT);
                    log.info("connecting to " + host + ":" + tcpport);
                    socket.connect(new InetSocketAddress(host, tcpport), CONNECTION_TIMEOUT_MS);
                    log.info("success connecting to " + host + ":" + tcpport);
                    if (socket.getSendBufferSize() != TCP_SEND_BUFFER_SIZE_BYTES) {
                        log.warning("requested sendBufferSize=" + TCP_SEND_BUFFER_SIZE_BYTES + " but got sendBufferSize=" + socket.getSendBufferSize());
                    }
                    if (socket.getReceiveBufferSize() != TCP_RECEIVE_BUFFER_SIZE_BYTES) {
                        log.warning("requested receiveBufferSize=" + TCP_RECEIVE_BUFFER_SIZE_BYTES + " but got receiveBufferSize=" + socket.getReceiveBufferSize());
                    }
                    Runtime.getRuntime().addShutdownHook(new Thread() {

                        public void run() {
                            log.info("closing " + socket);
                            try {
                                socket.close();
                            } catch (Exception e) {
                                log.warning(e.toString());
                            }
                        }
                    });
                    chosenInterface = new eDVS128_HardwareInterface(socket.getInputStream(), socket.getOutputStream(), null, socket);
                    closemap.put(host, chosenInterface);
                    success = true;
                } catch (SocketTimeoutException e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(this, "Timeout on connect:" + e.toString(), "eDVS128_HardwareInterface", JOptionPane.WARNING_MESSAGE);
                    chosenInterface = null;

                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(this, e.toString(), "eDVS128_HardwareInterface", JOptionPane.WARNING_MESSAGE);
                    chosenInterface = null;
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
                break;
            default:
                chosenInterface = null;
                success = true;
        }
        returnStatus = retStatus;
        if (success) {
            setVisible(false);
            dispose();
        }
    }

    private void doChooseSerial(int retStatus) {
        boolean success = false;
        returnStatus = retStatus;
        chosenInterface = null;
        if (retStatus == RET_OK) {
            prefs.put(LAST_SELECTED, "doChooseSerial");
            // make interface based on chosen serial port
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            Object o = portCB.getSelectedItem();
            if (o == null || !(o instanceof String)) {
                log.warning("Selected item " + o + " is not a String, can't use it to make eDVS128_HardwareInterface");
            } else {
                String serialPortName = (String) o;
                lastSerialPortIndex = portCB.getSelectedIndex();
                prefs.putInt("eDVS128_InterfaceFactory.lastPortIndex", lastSerialPortIndex);

                try {
                    portIdentifier = CommPortIdentifier.getPortIdentifier(serialPortName);
                    if (commPort != null) {
                        commPort.close();
                    }
                    if (serialPort != null) {
                        serialPort.close();
                    }
                    closePrevious(serialPortName);

                    if (portIdentifier.isCurrentlyOwned()) {
                        throw new IOException("Port " + serialPortName + " is currently in use by " + portIdentifier.getCurrentOwner());
                    } else {
                        commPort = portIdentifier.open(this.getClass().getName(), 2000);

                        if (commPort instanceof SerialPort) {
                            serialPort = (SerialPort) commPort;
                            serialPort.setSerialPortParams(serialBaudRateMbps * 1000000, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN);
                            serialPort.setFlowControlMode(serialPort.FLOWCONTROL_RTSCTS_OUT);

                            chosenInterface = new eDVS128_HardwareInterface(serialPort.getInputStream(), serialPort.getOutputStream(), serialPort, null);
                            closemap.put(serialPortName, chosenInterface);
//                            portIdentifier.addPortOwnershipListener((eDVS128_HardwareInterface)chosenInterface);  // doesn't work because port ownership change nofication is not implemented in our native library
                            serialPort = null;
                            success = true;
                        } else {
                            log.warning("commPort is not a SerialPort");
                        }
                    }
                } catch (Exception e) {
                    log.warning("Caught exception " + e.toString() + "; this can mean port is owned by another process");
                    if (commPort != null) {
                        commPort.close();
                    }
                    if (serialPort != null) {
                        serialPort.close();
                    }
                    JOptionPane.showMessageDialog(this, e.toString(), "eDVS128_HardwareInterface", JOptionPane.WARNING_MESSAGE);
                } catch (Error er) {
                    log.warning("Caught error " + er.toString() + "; this can mean port is owned by another process");
                    if (commPort != null) {
                        commPort.close();
                    }
                    if (serialPort != null) {
                        serialPort.close();
                    }
                    JOptionPane.showMessageDialog(this, "Caught error " + er.toString() + "; this usually means port is owned by another process", "eDVS128_HardwareInterface", JOptionPane.WARNING_MESSAGE);
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
            if (success) {
                setVisible(false);
                dispose();
            }
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> baudRateCB;
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton closeButton;
    private javax.swing.JButton defaultsButton;
    private javax.swing.JTextField hostTF;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JButton okSerPortButton;
    private javax.swing.JButton okSocketButton;
    private javax.swing.JButton pingButton;
    private javax.swing.JComboBox portCB;
    private javax.swing.JTextField portTF;
    private javax.swing.JButton refreshPortListButton;
    // End of variables declaration//GEN-END:variables
    private int returnStatus = RET_CANCEL;

    private void refreshSerialPortList() {
        portCB.removeAllItems();
        // add available COM ports to menu
        CommPortIdentifier portId;
        log.info("enumerating serial ports....");
        Enumeration<CommPortIdentifier> portList = CommPortIdentifier.getPortIdentifiers();
        log.info("done enumerating serial ports");

        while (portList.hasMoreElements()) {
            portId = (CommPortIdentifier) portList.nextElement();

            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                portCB.addItem(portId.getName());
            }
        }
        portCB.addItem(RESCAN);

        if (lastSerialPortIndex >= portCB.getItemCount()) {
            lastSerialPortIndex = portCB.getItemCount() - 1;
        }
        portCB.setSelectedIndex(lastSerialPortIndex);

    }

    @Override
    public String getDescription() {
        return "eDVS camera chooser, either for serial port or wifi interfaces";
    }
}
