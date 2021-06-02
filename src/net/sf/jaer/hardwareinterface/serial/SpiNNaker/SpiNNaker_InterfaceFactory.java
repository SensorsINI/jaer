/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * SpiNNaker_InterfaceFactory.java
 *
 * Created on Jul 19, 2011, 5:09:24 AM
 */
package net.sf.jaer.hardwareinterface.serial.SpiNNaker;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
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
 * Factory dialog for interfaces to SpiNNaker.
 * @author willconstable
 */
public class SpiNNaker_InterfaceFactory extends javax.swing.JDialog implements HardwareInterfaceFactoryChooserDialog {

    private static Preferences prefs = Preferences.userNodeForPackage(SpiNNaker_InterfaceFactory.class);
    private static final Logger log = Logger.getLogger("SpiNNaker");
    public static final String HOST = "192.168.240.10"; // spinn10 
    public static final int UDP_PORT = 17386; //TODO confirm this is right
    //public static final int TCP_RECEIVE_BUFFER_SIZE_BYTES = 8192;
    //public static final int TCP_SEND_BUFFER_SIZE_BYTES = 1024;
    public static final boolean DEFAULT_USE_BUFFERED_STREAM = false;
   
    /** timeout in ms for read/write attempts */
    public static final int SO_TIMEOUT = 100; // 1 means we should timeout as soon as there are no more events in the datainputstream
    /** A return status code - returned if Cancel button has been pressed */
    public static final int RET_CANCEL = 0;
    /** A return status code - returned if OK button has been pressed */
    public static final int RET_OK = 1;
    // singleton
    private static SpiNNaker_InterfaceFactory instance = new SpiNNaker_InterfaceFactory();
    private HardwareInterface chosenInterface = null;
    private static final String RESCAN = "-rescan-";
    private static final String LAST_SELECTED = "lastSelected";
    private JButton okSerPortButton;

    /** Creates new form SpiNNaker_InterfaceFactory */
    private SpiNNaker_InterfaceFactory() {
        super();
        setModal(true);
        initComponents();
        setName("SpiNNaker hardware interface chooser");

        // Close the dialog when Esc is pressed
        String cancelName = "cancel";
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancelName);
        ActionMap actionMap = getRootPane().getActionMap();
        actionMap.put(cancelName, new AbstractAction() {

            @Override
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
        hostTF.setText(prefs.get("SpiNNaker_InterfaceFactory.HOST", HOST));
        portTF.setText(prefs.get("SpiNNaker_InterfaceFactory.UDP_PORT", Integer.toString(UDP_PORT)));
        focusLast();

    }
    private HashMap<String, HardwareInterface> closemap = new HashMap();

    private void closePrevious(String s) {
        HardwareInterface hardwareInterface = closemap.get(s);
        if (hardwareInterface == null) {
            return;
        }
        try {
            hardwareInterface.close();
            log.log(Level.INFO, "closed old interface {0} = {1}", new Object[]{s, hardwareInterface});
            Thread.sleep(300); // wait added because perhaps it helps close serial port TODO check this
        } catch (Exception e) {
            log.warning(e.toString());
        }
        closemap.remove(s);
        hardwareInterface = null;
    }

    /** Use this singleton instance to make new interfaces */
    public static HardwareInterfaceFactoryInterface instance() {
        return instance;
    }

    /** Always returns 0. */
    @Override
    public int getNumInterfacesAvailable() {
        return 0;

    }

    /** Always returns null */
    @Override
    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException {
        return null;
    }

    /** Always returns null */
    @Override
    public HardwareInterface getInterface(int n) throws HardwareInterfaceException {
        return null;
    }

    @Override
    public String getGUID() {
        return "SpiNNaker network interface chooser";
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

    /** @return the return status of this dialog - one of RET_OK or RET_CANCEL */
    public int getReturnStatus() {
        return returnStatus;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        cancelButton = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        hostTF = new javax.swing.JTextField();
        portTF = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        okSocketButton = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        defaultsButton = new javax.swing.JButton();
        pingButton = new javax.swing.JButton();

        setTitle("Interface Chooser");
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

        jPanel2.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel2.setText("Hostname or IP address");

        hostTF.setToolTipText("The host IP address or hostname");

        portTF.setText("jTextField1");
        portTF.setToolTipText("Choose the TCP port - default is 56000");

        jLabel3.setText("UDP port");

        okSocketButton.setText("Open network interface");
        okSocketButton.setToolTipText("Tries to open the TCP socket. ");
        okSocketButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okSocketButtonActionPerformed(evt);
            }
        });

        jLabel4.setText("<html>SpiNNaker sends UDP AER packets. <p> Currently, there is no control protocol to enable/disable or configure the SpiNNaker device via jAER.");

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
                .addContainerGap(22, Short.MAX_VALUE))
        );

        getRootPane().setDefaultButton(okSerPortButton);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(cancelButton)
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cancelButton)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        doCloseCancel();
    }//GEN-LAST:event_cancelButtonActionPerformed

    /** Closes the dialog */
    private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
        doCloseCancel();
    }//GEN-LAST:event_closeDialog

    private void okSocketButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okSocketButtonActionPerformed
        doChooseSocket(RET_OK);
    }//GEN-LAST:event_okSocketButtonActionPerformed

    private void defaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_defaultsButtonActionPerformed
        hostTF.setText(HOST);
        portTF.setText(Integer.toString(UDP_PORT));
    }//GEN-LAST:event_defaultsButtonActionPerformed

    private void pingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pingButtonActionPerformed
           String host = hostTF.getText();
         try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            InetAddress adr = Inet4Address.getByName(host);
            try{
                adr.isReachable(3000);
                JOptionPane.showMessageDialog(this, host+" is reachable, but it may not be the SpiNNaker!");
            }catch(IOException notReachable){
                JOptionPane.showMessageDialog(this, host+" is not reachable: "+notReachable.toString(), "Not reachable", JOptionPane.WARNING_MESSAGE);
            }
        } catch (UnknownHostException ex) {
                  JOptionPane.showMessageDialog(this, host+" is unknown host: "+ex.toString(), "Host not found", JOptionPane.WARNING_MESSAGE);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }

    }//GEN-LAST:event_pingButtonActionPerformed

    private void focusLast() {
        //set last focus
        String lastSelected;
        if ((lastSelected = prefs.get(LAST_SELECTED, null)) != null) {
            if (lastSelected.equals("doChooseSocket")) {
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
                prefs.put(LAST_SELECTED, "doChooseSocket");
                try {
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                    String host = hostTF.getText();
                    closePrevious(host);
                    int udpport = Integer.parseInt(portTF.getText());
                    prefs.put("SpiNNaker_InterfaceFactory.HOST", host);
                    prefs.putInt("SpiNNaker_InterfaceFactory.UDP_PORT", udpport);
                    final DatagramSocket socket = new DatagramSocket(udpport);
                    log.log(Level.INFO, "UDP socket bound to port {0}", udpport);
                    
                    chosenInterface = new SpiNNaker_HardwareInterface(socket);
                    closemap.put(host, chosenInterface);
                    success = true;
                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(this, e.toString(), "SpiNNaker_HardwareInterface", JOptionPane.WARNING_MESSAGE);
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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton defaultsButton;
    private javax.swing.JTextField hostTF;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JButton okSocketButton;
    private javax.swing.JButton pingButton;
    private javax.swing.JTextField portTF;
    // End of variables declaration//GEN-END:variables
    private int returnStatus = RET_CANCEL;


    @Override
    public String getDescription() {
        return "SpiNNaker Interface chooser";
    }
}
