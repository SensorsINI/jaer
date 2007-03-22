/*
 * CypressFX2EEPROM.java
 *
 * Created on December 9, 2005, 5:08 PM
 */

package ch.unizh.ini.caviar.hardwareinterface.usb;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.util.HexString;
import de.thesycon.usbio.*;
import java.text.ParseException;
import java.awt.*;

/**
 * Utility GUI for dealing with CypressFX2 EEPROM stuff. Using this rudimentary tool, you can scan for USBIO devices. If the device is virgin (not had
 *its EEPROM programmed) then it will be found and the retina firmware will be downloaded to it. This firmware (at least on the retina board) allows for
 *programming the Cypress's EEPROM. You can then program a desired C0 (VID/PID) or C2 (firmware) load into the device's EEPROM.
 * <p>
 * You can also program firmware into
 *the device RAM directly (using the built-in Cypress hardare vendor request to write to device RAM). Firmware files can be downloaded to the device's RAM
 *from two source formats, a flat binary file or an Intel hex file that has 'records' telling what bytes to write where, exactly.
 *<p>
 *This tool is not very well-developed but can do the job for knowledgable users, at least for the retina.
 *
 * @author  tobi
 */
public class CypressFX2EEPROM extends javax.swing.JFrame implements UsbIoErrorCodes, PnPNotifyInterface {
    AEChip chip;
    PnPNotify pnp=null;
    short VID=(short)0x547,PID=(short)0x8700,DID=0;
    
    CypressFX2 cypress=null;
    HardwareInterface hw=null;
    
    int numDevices;
    boolean exitOnCloseEnabled=false;
    
    /**
     * Creates new form CypressFX2EEPROM
     */
    public CypressFX2EEPROM() {
        initComponents();
        setButtonsEnabled(false);
        // note the PNP notification will only check for device after the user unplugs it and replugs it (or just plugs it in). Initial check
        // is not done because this can lead to problems with cycles
        pnp=new PnPNotify(this);
        pnp.enablePnPNotification(CypressFX2.GUID);
    }
    
    /**
     * checks for any Thesycon USBIO devices. They may not be devices we can recognize as particular devices, e.g. retina, because the device
     *may not have the VID/PID programmed yet. The device needs firmware before it can have the EEPROM programmed, making this a bit of chicken and egg
     *problem... Here we just check for USBIO devices. Then we leave it up to the user to download the correct firmware so that the C0 or C2 loads can be
     *programmed.
     */
    void scanForKnownDevices(){
        numDevices=CypressFX2TmpdiffRetinaFactory.instance().getNumInterfacesAvailable();
        System.out.println(numDevices+" devices found");
        if(numDevices==0){
            setButtonsEnabled(false);
            return;
        }
        
        hw=HardwareInterfaceFactory.instance().getFirstAvailableInterface();
        if(hw!=null && hw instanceof CypressFX2){
            cypress=(CypressFX2)hw;
            VID=cypress.getVID();
            PID=cypress.getPID();
            DID=cypress.getDID();
            try{
                hw.open();
                VIDtextField.setText(HexString.toString(cypress.getVID()));
                PIDtextField.setText(HexString.toString(cypress.getPID()));
                DIDtextField.setText(HexString.toString(cypress.getDID()));
                setButtonsEnabled(true);
            }catch(HardwareInterfaceException e){
                setButtonsEnabled(false);
                e.printStackTrace();
            }
        }
    }
    
    /** scans for any USBIO device */
    void scanForUsbIoDevices(){
        cypress=new CypressFX2(0);
        try{
            cypress.open();
            VID=cypress.getVID();
            PID=cypress.getPID();
            DID=cypress.getDID();
            VIDtextField.setText(HexString.toString(cypress.getVID()));
            PIDtextField.setText(HexString.toString(cypress.getPID()));
            DIDtextField.setText(HexString.toString(cypress.getDID()));
            setButtonsEnabled(true);
            hw=cypress;
            if(PID==CypressFX2.PID_USBAERmini2){
                enableDeviceIDProgramming(true);
            }
        }catch(HardwareInterfaceException e){
            setButtonsEnabled(false);
            e.printStackTrace();
            enableDeviceIDProgramming(false);
        }
    }
    
    void enableDeviceIDProgramming(boolean yes){
        if(PID==CypressFX2.PID_USBAERmini2){
            writeDeviceIDButton.setEnabled(yes);
            writeDeviceIDTextField.setEnabled(yes);
        }else{
            writeDeviceIDButton.setEnabled(yes);
            writeDeviceIDTextField.setEnabled(yes);
        }
    }
    
    void setButtonsEnabled(boolean yes){
        writeRetinaFirmwareButton.setEnabled(yes);
        writeRetinaFirmwareButtonBinaryToRAM.setEnabled(yes);
        writeRetinaFirmwareButtonHexToRAM.setEnabled(yes);
        writeVIDPIDDIDButton.setEnabled(yes);
        
        writeMonitorSequencerFirmwareButton.setEnabled(yes);
        writeMapperFirmware.setEnabled(yes);
        //writeMonitorSequencerFirmwareButton.setEnabled(yes);
        //writeMonitorSequencerFirmwareButton1.setEnabled(yes);
        //writeMonitorSequencerFirmwareButton6.setEnabled(yes);
        
        eraseButton.setEnabled(yes);
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        scanPanel = new javax.swing.JPanel();
        scanButton = new javax.swing.JButton();
        erasePanel = new javax.swing.JPanel();
        eraseButton = new javax.swing.JButton();
        vidpiddidPanel = new javax.swing.JPanel();
        writeVIDPIDDIDButton = new javax.swing.JButton();
        VIDtextField = new javax.swing.JTextField();
        PIDtextField = new javax.swing.JTextField();
        DIDtextField = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        writeRetinaFirmwareButton = new javax.swing.JButton();
        writeMonitorSequencerFirmwareButton = new javax.swing.JButton();
        writeMapperFirmware = new javax.swing.JButton();
        writeStereoboardFirmware = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        writeRetinaFirmwareButtonBinaryToRAM = new javax.swing.JButton();
        writeMonitorSequencerFirmwareButton1 = new javax.swing.JButton();
        jPanel6 = new javax.swing.JPanel();
        jPanel8 = new javax.swing.JPanel();
        writeRetinaFirmwareButtonHexToRAM = new javax.swing.JButton();
        writeMonitorSequencerFirmwareButton6 = new javax.swing.JButton();
        jPanel7 = new javax.swing.JPanel();
        deviceIDPanel = new javax.swing.JPanel();
        writeDeviceIDButton = new javax.swing.JButton();
        writeDeviceIDTextField = new javax.swing.JTextField();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        exitMenuItem = new javax.swing.JMenuItem();

        getContentPane().setLayout(new javax.swing.BoxLayout(getContentPane(), javax.swing.BoxLayout.Y_AXIS));

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("CypressFX2EEPROM");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        scanButton.setText("Scan for device");
        scanButton.setToolTipText("Looks for CypressFX2 device");
        scanButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scanButtonActionPerformed(evt);
            }
        });

        scanPanel.add(scanButton);

        getContentPane().add(scanPanel);

        eraseButton.setText("Erase EEPROM");
        eraseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eraseButtonActionPerformed(evt);
            }
        });

        erasePanel.add(eraseButton);

        getContentPane().add(erasePanel);

        vidpiddidPanel.setLayout(new javax.swing.BoxLayout(vidpiddidPanel, javax.swing.BoxLayout.X_AXIS));

        vidpiddidPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("VID/PID/DID (C0 load)"));
        writeVIDPIDDIDButton.setText("writeVIDPIDDID");
        writeVIDPIDDIDButton.setToolTipText("writes VID/PID/DID to flash memory EEPROM on CypressFX2");
        writeVIDPIDDIDButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeVIDPIDDIDButtonActionPerformed(evt);
            }
        });

        vidpiddidPanel.add(writeVIDPIDDIDButton);

        VIDtextField.setColumns(5);
        VIDtextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        VIDtextField.setToolTipText("hex format value");
        VIDtextField.setMaximumSize(new java.awt.Dimension(2147483647, 50));
        vidpiddidPanel.add(VIDtextField);

        PIDtextField.setColumns(5);
        PIDtextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        PIDtextField.setToolTipText("hex format value");
        PIDtextField.setMaximumSize(new java.awt.Dimension(2147483647, 50));
        vidpiddidPanel.add(PIDtextField);

        DIDtextField.setColumns(5);
        DIDtextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        DIDtextField.setToolTipText("hex format value");
        DIDtextField.setMaximumSize(new java.awt.Dimension(2147483647, 50));
        vidpiddidPanel.add(DIDtextField);

        jPanel2.setMinimumSize(new java.awt.Dimension(0, 10));
        jPanel2.setPreferredSize(new java.awt.Dimension(0, 10));
        vidpiddidPanel.add(jPanel2);

        getContentPane().add(vidpiddidPanel);

        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.X_AXIS));

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("EEPROM firmware (C2 load)"));
        jPanel1.setAlignmentX(1.0F);
        writeRetinaFirmwareButton.setText("EEPROM Retina firmware");
        writeRetinaFirmwareButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeRetinaFirmwareButtonActionPerformed(evt);
            }
        });

        jPanel1.add(writeRetinaFirmwareButton);

        writeMonitorSequencerFirmwareButton.setText("Mon/Seq firmware");
        writeMonitorSequencerFirmwareButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeMonitorSequencerFirmwareButtonActionPerformed(evt);
            }
        });

        jPanel1.add(writeMonitorSequencerFirmwareButton);

        writeMapperFirmware.setText("Mapper firmware");
        writeMapperFirmware.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeMapperFirmwareActionPerformed(evt);
            }
        });

        jPanel1.add(writeMapperFirmware);

        writeStereoboardFirmware.setText("Stereoboard firmware");
        writeStereoboardFirmware.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeStereoboardFirmwareActionPerformed(evt);
            }
        });

        jPanel1.add(writeStereoboardFirmware);

        jPanel1.add(jPanel5);

        getContentPane().add(jPanel1);

        jPanel3.setLayout(new javax.swing.BoxLayout(jPanel3, javax.swing.BoxLayout.X_AXIS));

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("RAM firmware binary"));
        jPanel3.setAlignmentX(1.0F);
        writeRetinaFirmwareButtonBinaryToRAM.setText("RAM Retina firmware");
        writeRetinaFirmwareButtonBinaryToRAM.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeRetinaFirmwareButtonBinaryToRAMActionPerformed(evt);
            }
        });

        jPanel3.add(writeRetinaFirmwareButtonBinaryToRAM);

        writeMonitorSequencerFirmwareButton1.setText("RAM  Monitor/Sequencer firmware");
        writeMonitorSequencerFirmwareButton1.setEnabled(false);
        jPanel3.add(writeMonitorSequencerFirmwareButton1);

        jPanel3.add(jPanel6);

        getContentPane().add(jPanel3);

        jPanel8.setLayout(new javax.swing.BoxLayout(jPanel8, javax.swing.BoxLayout.X_AXIS));

        jPanel8.setBorder(javax.swing.BorderFactory.createTitledBorder("RAM firmware hex"));
        jPanel8.setAlignmentX(1.0F);
        writeRetinaFirmwareButtonHexToRAM.setText("RAM Retina firmware");
        writeRetinaFirmwareButtonHexToRAM.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeRetinaFirmwareButtonHexToRAMActionPerformed(evt);
            }
        });

        jPanel8.add(writeRetinaFirmwareButtonHexToRAM);

        writeMonitorSequencerFirmwareButton6.setText("RAM  Monitor/Sequencer firmware");
        writeMonitorSequencerFirmwareButton6.setEnabled(false);
        jPanel8.add(writeMonitorSequencerFirmwareButton6);

        jPanel8.add(jPanel7);

        getContentPane().add(jPanel8);

        deviceIDPanel.setLayout(new javax.swing.BoxLayout(deviceIDPanel, javax.swing.BoxLayout.X_AXIS));

        deviceIDPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Device ID String"));
        writeDeviceIDButton.setText("Write Device ID string");
        writeDeviceIDButton.setEnabled(false);
        writeDeviceIDButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeDeviceIDButtonActionPerformed(evt);
            }
        });

        deviceIDPanel.add(writeDeviceIDButton);

        writeDeviceIDTextField.setColumns(20);
        writeDeviceIDTextField.setEnabled(false);
        writeDeviceIDTextField.setMaximumSize(new java.awt.Dimension(2147483647, 50));
        deviceIDPanel.add(writeDeviceIDTextField);

        getContentPane().add(deviceIDPanel);

        fileMenu.setText("File");
        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });

        fileMenu.add(exitMenuItem);

        jMenuBar1.add(fileMenu);

        setJMenuBar(jMenuBar1);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void writeStereoboardFirmwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeStereoboardFirmwareActionPerformed
        try{
            setWaitCursor(true);
            byte[] fw;
            
            fw=cypress.loadBinaryFirmwareFile(CypressFX2.FIRMWARE_FILENAME_STEREO_IIC);
            
            cypress.writeEEPROM(0,fw);
            System.out.println("New firmware written to EEPROM");
        }catch(Exception e){
            e.printStackTrace();
        }
        setWaitCursor(false);
    }//GEN-LAST:event_writeStereoboardFirmwareActionPerformed
    
    private void writeDeviceIDButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeDeviceIDButtonActionPerformed
        if(hw==null) {
            System.err.println("no device");
            return;
        }
        hw.close();
        try{
            CypressFX2MonitorSequencer cypress=new CypressFX2MonitorSequencer(0);
            cypress.open();
            cypress.setDeviceName(writeDeviceIDTextField.getText());
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }
    }//GEN-LAST:event_writeDeviceIDButtonActionPerformed
    
    private void writeMapperFirmwareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeMapperFirmwareActionPerformed
        try{
            setWaitCursor(true);
            byte[] fw;
            
            fw=cypress.loadBinaryFirmwareFile(CypressFX2.FIRMWARE_FILENAME_MAPPER_IIC);
            
            cypress.writeEEPROM(0,fw);
            System.out.println("New firmware written to EEPROM");
        }catch(Exception e){
            e.printStackTrace();
        }
        setWaitCursor(false);
    }//GEN-LAST:event_writeMapperFirmwareActionPerformed
    
    private void writeMonitorSequencerFirmwareButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeMonitorSequencerFirmwareButtonActionPerformed
        try{
            setWaitCursor(true);
            byte[] fw;
            
            fw=cypress.loadBinaryFirmwareFile(CypressFX2.FIRMWARE_FILENAME_MONITOR_SEQUENCER_IIC);
            
            cypress.writeEEPROM(0,fw);
            System.out.println("New firmware written to EEPROM");
        }catch(Exception e){
            e.printStackTrace();
        }
        setWaitCursor(false);
    }//GEN-LAST:event_writeMonitorSequencerFirmwareButtonActionPerformed
    
    private void eraseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eraseButtonActionPerformed
        try{
            setWaitCursor(true);
            cypress.eraseEEPROM();
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }
        setWaitCursor(false);
    }//GEN-LAST:event_eraseButtonActionPerformed
    
    private void writeRetinaFirmwareButtonHexToRAMActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeRetinaFirmwareButtonHexToRAMActionPerformed
        try{
            String fw=cypress.getFirmwareFilenameHexFromVIDPID();
            cypress.downloadFirmwareHex(fw);
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }
    }//GEN-LAST:event_writeRetinaFirmwareButtonHexToRAMActionPerformed
    
    private void writeRetinaFirmwareButtonBinaryToRAMActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeRetinaFirmwareButtonBinaryToRAMActionPerformed
        try{
            String fw=cypress.getFirmwareFilenameBinaryFromVIDPID();
            cypress.downloadFirmwareBinary(fw);
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }
    }//GEN-LAST:event_writeRetinaFirmwareButtonBinaryToRAMActionPerformed
    
    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        System.out.println("window closing");
        if(hw!=null) hw.close();
    }//GEN-LAST:event_formWindowClosing
    
    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        System.out.println("window closed");
        if(hw!=null) hw.close();
    }//GEN-LAST:event_formWindowClosed
    
    void setWaitCursor(boolean yes){
        if(yes){
            Cursor hourglassCursor = new Cursor(Cursor.WAIT_CURSOR);
            setCursor(hourglassCursor);
        }else{
            Cursor normalCursor = new Cursor(Cursor.DEFAULT_CURSOR);
            setCursor(normalCursor);
        }
    }
    
    private void writeRetinaFirmwareButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeRetinaFirmwareButtonActionPerformed
        try{
            setWaitCursor(true);
            cypress.writeHexFileToEEPROM(CypressFX2.FIRMWARE_FILENAME_TMPDIFF128_HEX, VID, PID, DID);
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }
        setWaitCursor(false);
    }//GEN-LAST:event_writeRetinaFirmwareButtonActionPerformed
    
    
    private void scanButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scanButtonActionPerformed
        scanForUsbIoDevices();
    }//GEN-LAST:event_scanButtonActionPerformed
    
    private void writeVIDPIDDIDButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeVIDPIDDIDButtonActionPerformed
        if(hw==null) {
            System.err.println("null device");
            return;
        }
        short VID,PID,DID;
        try{
            VID=HexString.parseShort(VIDtextField.getText());
        }catch(ParseException e){
            System.out.println(e);
            VIDtextField.selectAll();
            return;
        }
        try{
            PID=HexString.parseShort(PIDtextField.getText());
        }catch(ParseException e){
            System.out.println(e);
            PIDtextField.selectAll();
            return;
        }
        try{
            DID=HexString.parseShort(DIDtextField.getText());
        }catch(ParseException e){
            System.out.println(e);
            DIDtextField.selectAll();
            return;
        }
        System.out.println("Writing VID/PID/DID "+HexString.toString(VID)+"/"+HexString.toString(PID)+"/"+HexString.toString(DID));
        
        try{
            cypress.writeVIDPIDDID(VID,PID,DID);
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
            scanForUsbIoDevices();
        }
        
    }//GEN-LAST:event_writeVIDPIDDIDButtonActionPerformed
    
    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        System.out.println("exit menu");
        if(hw!=null) hw.close();
        
        if(exitOnCloseEnabled) {
            System.exit(0); // TODO add your handling code here:
        }else{
            dispose();
        }
    }//GEN-LAST:event_exitMenuItemActionPerformed
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new CypressFX2EEPROM().setVisible(true);
            }
        });
    }
    
    // for bug in USBIO 2.30, need both cases, one for interface and other for JNI
    public void onAdd() {
        System.out.println("device added, scanning for devices");
        scanForUsbIoDevices();
    }
    
    public void onRemove() {
        scanForUsbIoDevices();
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField DIDtextField;
    private javax.swing.JTextField PIDtextField;
    private javax.swing.JTextField VIDtextField;
    private javax.swing.JPanel deviceIDPanel;
    private javax.swing.JButton eraseButton;
    private javax.swing.JPanel erasePanel;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JButton scanButton;
    private javax.swing.JPanel scanPanel;
    private javax.swing.JPanel vidpiddidPanel;
    private javax.swing.JButton writeDeviceIDButton;
    private javax.swing.JTextField writeDeviceIDTextField;
    private javax.swing.JButton writeMapperFirmware;
    private javax.swing.JButton writeMonitorSequencerFirmwareButton;
    private javax.swing.JButton writeMonitorSequencerFirmwareButton1;
    private javax.swing.JButton writeMonitorSequencerFirmwareButton6;
    private javax.swing.JButton writeRetinaFirmwareButton;
    private javax.swing.JButton writeRetinaFirmwareButtonBinaryToRAM;
    private javax.swing.JButton writeRetinaFirmwareButtonHexToRAM;
    private javax.swing.JButton writeStereoboardFirmware;
    private javax.swing.JButton writeVIDPIDDIDButton;
    // End of variables declaration//GEN-END:variables
    
    
    public boolean isExitOnCloseEnabled() {
        return this.exitOnCloseEnabled;
    }
    
    /**@param exitOnCloseEnabled set true so that exit really exits JVM. default false only disposes window */
    public void setExitOnCloseEnabled(final boolean exitOnCloseEnabled) {
        this.exitOnCloseEnabled = exitOnCloseEnabled;
        if(exitOnCloseEnabled){
            setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        }
    }
} // CypressFX2EEPROM
