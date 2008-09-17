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
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.awt.*;
import java.util.prefs.*;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.util.logging.*;
import javax.swing.filechooser.FileFilter;

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
    Logger log=Logger.getLogger("CypressFX2EEPROM");
    
    Preferences prefs=Preferences.userNodeForPackage(CypressFX2EEPROM.class);
    
    AEChip chip;
    PnPNotify pnp=null;
    short VID=(short)0x0547,PID=(short)0x8700,DID=0;  // defaults to tmpdiff128 retina, TO-DO fix this lousy default
    
    CypressFX2 cypress=null;
    HardwareInterface hw=null;
    
    int numDevices;
    private boolean exitOnCloseEnabled=false; // used to exit if we are not run from inside AEViewer
    
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
        filenameTextField.setText(prefs.get("CypressFX2EEPROM.filename",""));      
        filenameTextField.setToolTipText(prefs.get("CypressFX2EEPROM.filename",""));
        CPLDfilenameField.setText(prefs.get("CypressFX2EEPROM_CPLD.filename",""));
        boolean b=prefs.getBoolean("CypressFX2EEPROM.writeEEPROMRadioButton",true);
        if(b)
            writeEEPROMRadioButton.setSelected(b);
        else
            writeRAMRadioButton.setSelected(true);
    }
    
//    /**
//     * checks for any Thesycon USBIO devices. They may not be devices we can recognize as particular devices, e.g. retina, because the device
//     *may not have the VID/PID programmed yet. The device needs firmware before it can have the EEPROM programmed, making this a bit of chicken and egg
//     *problem... Here we just check for USBIO devices. Then we leave it up to the user to download the correct firmware so that the C0 or C2 loads can be
//     *programmed.
//     */
//    void scanForKnownDevices(){
//        numDevices=HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
//        log.info(numDevices+" devices found");
//        if(numDevices==0){
//            setButtonsEnabled(false);
//            return;
//        }
//
//        hw=HardwareInterfaceFactory.instance().getFirstAvailableInterface();
//        if(hw!=null && hw instanceof CypressFX2){
//            cypress=(CypressFX2)hw;
//            VID=cypress.getVID();
//            PID=cypress.getPID();
//            DID=cypress.getDID();
//            try{
//                hw.open();
//                VIDtextField.setText(HexString.toString(cypress.getVID()));
//                PIDtextField.setText(HexString.toString(cypress.getPID()));
//                DIDtextField.setText(HexString.toString(cypress.getDID()));
//                setButtonsEnabled(true);
//            }catch(HardwareInterfaceException e){
//                setButtonsEnabled(false);
//                e.printStackTrace();
//            }
//        }
//    }
    
    /** scans for first available USBIO device */
    synchronized private void scanForUsbIoDevices(){
        try{
            cypress=(CypressFX2)CypressFX2Factory.instance().getFirstAvailableInterface();
            if(cypress==null) {
                log.info("no device found");
                setButtonsEnabled(false);
                VIDtextField.setText("");
                PIDtextField.setText("");
                DIDtextField.setText("");
                writeDeviceIDTextField.setText("");
                return;
            }
            log.info("Found device "+cypress+", opening it");
            cypress.open();
            VID=cypress.getVID();
            PID=cypress.getPID();
            DID=cypress.getDID();
            VIDtextField.setText(HexString.toString(cypress.getVID()));
            PIDtextField.setText(HexString.toString(cypress.getPID()));
            DIDtextField.setText(HexString.toString(cypress.getDID()));
            if (cypress.getNumberOfStringDescriptors()>2)
            {
                this.writeDeviceIDTextField.setText(cypress.getStringDescriptors()[2]);
                enableDeviceIDProgramming(true);
            }
            
            setButtonsEnabled(true);
            hw=cypress;          
            
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
        downloadFirmwareButton.setEnabled(yes);
        downloadCPLDFirmwareButton.setEnabled(yes);
        eraseButton.setEnabled(yes);
        writeVIDPIDDIDButton.setEnabled(yes);
        monSeqCPLDFirmwareButton.setEnabled(yes);
        monSeqFX2FirmwareButton.setEnabled(yes);
        monSeqFX2FirmwareButtonJTAG.setEnabled(yes);
        cyclePortButton.setEnabled(yes);
        writeDeviceIDButton.setEnabled(yes);
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        scanPanel = new javax.swing.JPanel();
        scanButton = new javax.swing.JButton();
        cyclePortButton = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        vidpiddidPanel = new javax.swing.JPanel();
        writeVIDPIDDIDButton = new javax.swing.JButton();
        VIDtextField = new javax.swing.JTextField();
        PIDtextField = new javax.swing.JTextField();
        DIDtextField = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        firmwareDownloadPanel = new javax.swing.JPanel();
        jPanel6 = new javax.swing.JPanel();
        filenameTextField = new javax.swing.JTextField();
        chooseFileButton = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        writeEEPROMRadioButton = new javax.swing.JRadioButton();
        writeRAMRadioButton = new javax.swing.JRadioButton();
        eraseButton = new javax.swing.JButton();
        downloadFirmwareButton = new javax.swing.JButton();
        deviceIDPanel = new javax.swing.JPanel();
        writeDeviceIDButton = new javax.swing.JButton();
        writeDeviceIDTextField = new javax.swing.JTextField();
        USBAERmini2panel = new javax.swing.JPanel();
        monSeqCPLDFirmwareButton = new javax.swing.JButton();
        monSeqFX2FirmwareButton = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        monSeqFX2FirmwareButtonJTAG = new javax.swing.JButton();
        CPLDDownloadPanel = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        CPLDfilenameField = new javax.swing.JTextField();
        chooseCPLDFileButton = new javax.swing.JButton();
        downloadCPLDFirmwareButton = new javax.swing.JButton();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        exitMenuItem = new javax.swing.JMenuItem();

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

        scanPanel.setLayout(new javax.swing.BoxLayout(scanPanel, javax.swing.BoxLayout.LINE_AXIS));

        scanButton.setText("Scan for device");
        scanButton.setToolTipText("Looks for CypressFX2 device");
        scanButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scanButtonActionPerformed(evt);
            }
        });
        scanPanel.add(scanButton);

        cyclePortButton.setText("Cycle port");
        cyclePortButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cyclePortButtonActionPerformed(evt);
            }
        });
        scanPanel.add(cyclePortButton);
        scanPanel.add(jPanel3);

        vidpiddidPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("VID/PID/DID (C0 load)"));

        writeVIDPIDDIDButton.setText("writeVIDPIDDID");
        writeVIDPIDDIDButton.setToolTipText("writes only VID/PID/DID to flash memory EEPROM for CypressFX2 C0 load (ram download of code from host)");
        writeVIDPIDDIDButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeVIDPIDDIDButtonActionPerformed(evt);
            }
        });

        VIDtextField.setColumns(5);
        VIDtextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        VIDtextField.setToolTipText("hex format value for USB vendor ID");
        VIDtextField.setMaximumSize(new java.awt.Dimension(2147483647, 50));

        PIDtextField.setColumns(5);
        PIDtextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        PIDtextField.setToolTipText("hex format value for USB product ID");
        PIDtextField.setMaximumSize(new java.awt.Dimension(2147483647, 50));

        DIDtextField.setColumns(5);
        DIDtextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        DIDtextField.setToolTipText("hex format value for device ID (optional)");
        DIDtextField.setMaximumSize(new java.awt.Dimension(2147483647, 50));

        jPanel2.setMinimumSize(new java.awt.Dimension(0, 10));
        jPanel2.setPreferredSize(new java.awt.Dimension(0, 10));

        org.jdesktop.layout.GroupLayout vidpiddidPanelLayout = new org.jdesktop.layout.GroupLayout(vidpiddidPanel);
        vidpiddidPanel.setLayout(vidpiddidPanelLayout);
        vidpiddidPanelLayout.setHorizontalGroup(
            vidpiddidPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(vidpiddidPanelLayout.createSequentialGroup()
                .add(writeVIDPIDDIDButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(VIDtextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 179, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(PIDtextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 179, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(DIDtextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 179, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(265, Short.MAX_VALUE))
        );
        vidpiddidPanelLayout.setVerticalGroup(
            vidpiddidPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(writeVIDPIDDIDButton)
            .add(vidpiddidPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(VIDtextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(PIDtextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(DIDtextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );

        firmwareDownloadPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("EEPROM firmware (C2 load)"));
        firmwareDownloadPanel.setAlignmentX(1.0F);

        chooseFileButton.setText("Choose...");
        chooseFileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chooseFileButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(writeEEPROMRadioButton);
        writeEEPROMRadioButton.setText("Write to EEPROM");
        writeEEPROMRadioButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        writeEEPROMRadioButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        writeEEPROMRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeEEPROMRadioButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(writeRAMRadioButton);
        writeRAMRadioButton.setText("Write to RAM");
        writeRAMRadioButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        writeRAMRadioButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        writeRAMRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeRAMRadioButtonActionPerformed(evt);
            }
        });

        eraseButton.setText("Erase EEPROM");
        eraseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eraseButtonActionPerformed(evt);
            }
        });

        downloadFirmwareButton.setText("Download firmware");
        downloadFirmwareButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                downloadFirmwareButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(writeEEPROMRadioButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 291, Short.MAX_VALUE)
                .add(writeRAMRadioButton)
                .add(69, 69, 69)
                .add(eraseButton)
                .add(18, 18, 18)
                .add(downloadFirmwareButton)
                .add(91, 91, 91))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(writeEEPROMRadioButton)
                        .add(writeRAMRadioButton))
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(eraseButton)
                        .add(downloadFirmwareButton)))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout jPanel6Layout = new org.jdesktop.layout.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel6Layout.createSequentialGroup()
                        .add(filenameTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 792, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(18, 18, 18)
                        .add(chooseFileButton)))
                .add(81, 81, 81))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel6Layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(filenameTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(chooseFileButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );

        org.jdesktop.layout.GroupLayout firmwareDownloadPanelLayout = new org.jdesktop.layout.GroupLayout(firmwareDownloadPanel);
        firmwareDownloadPanel.setLayout(firmwareDownloadPanelLayout);
        firmwareDownloadPanelLayout.setHorizontalGroup(
            firmwareDownloadPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(firmwareDownloadPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel6, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 908, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(13, Short.MAX_VALUE))
        );
        firmwareDownloadPanelLayout.setVerticalGroup(
            firmwareDownloadPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(firmwareDownloadPanelLayout.createSequentialGroup()
                .add(jPanel6, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        deviceIDPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Serial Number String (4 characters for DVS128, 8 characters for USBAERmini2)"));
        deviceIDPanel.setLayout(new javax.swing.BoxLayout(deviceIDPanel, javax.swing.BoxLayout.LINE_AXIS));

        writeDeviceIDButton.setText("Write Serial Number string");
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

        USBAERmini2panel.setBorder(javax.swing.BorderFactory.createTitledBorder("USBAERmini2 firmware"));

        monSeqCPLDFirmwareButton.setText("Mon/Seq CPLD Firmware");
        monSeqCPLDFirmwareButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                monSeqCPLDFirmwareButtonActionPerformed(evt);
            }
        });

        monSeqFX2FirmwareButton.setText("FX2 Firmware");
        monSeqFX2FirmwareButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                monSeqFX2FirmwareButtonActionPerformed(evt);
            }
        });

        monSeqFX2FirmwareButtonJTAG.setText("FX2LP Firmware with JTAG support");
        monSeqFX2FirmwareButtonJTAG.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                monSeqFX2FirmwareButtonJTAGActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout USBAERmini2panelLayout = new org.jdesktop.layout.GroupLayout(USBAERmini2panel);
        USBAERmini2panel.setLayout(USBAERmini2panelLayout);
        USBAERmini2panelLayout.setHorizontalGroup(
            USBAERmini2panelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(USBAERmini2panelLayout.createSequentialGroup()
                .add(monSeqCPLDFirmwareButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(monSeqFX2FirmwareButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(monSeqFX2FirmwareButtonJTAG)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 452, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        USBAERmini2panelLayout.setVerticalGroup(
            USBAERmini2panelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(USBAERmini2panelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(monSeqFX2FirmwareButton)
                .add(monSeqCPLDFirmwareButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 26, Short.MAX_VALUE)
                .add(monSeqFX2FirmwareButtonJTAG))
            .add(jPanel4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 45, Short.MAX_VALUE)
        );

        CPLDDownloadPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("CPLD firmware download"));
        CPLDDownloadPanel.setAlignmentX(1.0F);

        chooseCPLDFileButton.setText("Choose...");
        chooseCPLDFileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chooseCPLDFileButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel7Layout = new org.jdesktop.layout.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .add(CPLDfilenameField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 791, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(18, 18, 18)
                .add(chooseCPLDFileButton)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel7Layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(CPLDfilenameField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(chooseCPLDFileButton))
                .add(51, 51, 51))
        );

        downloadCPLDFirmwareButton.setText("Download firmware");
        downloadCPLDFirmwareButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                downloadCPLDFirmwareButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout CPLDDownloadPanelLayout = new org.jdesktop.layout.GroupLayout(CPLDDownloadPanel);
        CPLDDownloadPanel.setLayout(CPLDDownloadPanelLayout);
        CPLDDownloadPanelLayout.setHorizontalGroup(
            CPLDDownloadPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(CPLDDownloadPanelLayout.createSequentialGroup()
                .add(CPLDDownloadPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(CPLDDownloadPanelLayout.createSequentialGroup()
                        .add(328, 328, 328)
                        .add(downloadCPLDFirmwareButton))
                    .add(CPLDDownloadPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .add(jPanel7, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(9, Short.MAX_VALUE))
        );
        CPLDDownloadPanelLayout.setVerticalGroup(
            CPLDDownloadPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(CPLDDownloadPanelLayout.createSequentialGroup()
                .add(jPanel7, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 40, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(downloadCPLDFirmwareButton)
                .addContainerGap(12, Short.MAX_VALUE))
        );

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

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(scanPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 664, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(CPLDDownloadPanel, 0, 943, Short.MAX_VALUE)
                    .add(firmwareDownloadPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(USBAERmini2panel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(deviceIDPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 943, Short.MAX_VALUE)
                    .add(vidpiddidPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(scanPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(vidpiddidPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(firmwareDownloadPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(deviceIDPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(USBAERmini2panel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(CPLDDownloadPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void downloadCPLDFirmwareButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_downloadCPLDFirmwareButtonActionPerformed
        File f=new File(CPLDfilenameField.getText());
        
        if(!f.exists()){
            JOptionPane.showMessageDialog(this,"File doesn't exist. Please choose a binary download CPLD image file (.xsvf) first.");
            return;
        }
        
        try {
            setWaitCursor(true);
//            ProgressMonitor progressMonitor=new  ProgressMonitor(chip.getAeViewer(), "Downloading firmware to EEPROM","", 0, task.getLengthOfTask());
        
            cypress.writeCPLDfirmware(CPLDfilenameField.getText());
            JOptionPane.showMessageDialog(this,"Firmware written to CPLD.");
           
        }
        catch(Exception e){
            JOptionPane.showMessageDialog(this,e);
            e.printStackTrace();
        }
        setWaitCursor(false);
    }//GEN-LAST:event_downloadCPLDFirmwareButtonActionPerformed

    private void chooseCPLDFileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chooseCPLDFileButtonActionPerformed
        JFileChooser chooser=new JFileChooser(prefs.get("CypressFX2EEPROM_CPLD.filepath",""));
//        JFileChooser chooser=new JFileChooser();
        FileFilter filter = new FileFilter() {
            public boolean accept(File f) {
                if(f.getName().toLowerCase().endsWith(".xsvf") || f.isDirectory())
                    return true;
                else
                    return false;
            }
            public String getDescription() {
                return "Firmware download file for CPLD";
            }
        };
        chooser.setFileFilter(filter);
        chooser.setApproveButtonText("Choose");
        chooser.setToolTipText("Choose a CPLD download file");
        int returnVal = chooser.showOpenDialog(this);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            File file=chooser.getSelectedFile();
            try {
                log.info("You chose this file: " + file.getCanonicalFile());
                this.CPLDfilenameField.setText(file.getCanonicalPath());
                this.CPLDfilenameField.setToolTipText(file.getCanonicalPath());
                prefs.put("CypressFX2EEPROM_CPLD.filename",file.getCanonicalPath());
                prefs.put("CypressFX2EEPROM_CPLD.filepath",file.getCanonicalPath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex);
            }
        }
    }//GEN-LAST:event_chooseCPLDFileButtonActionPerformed

    private void monSeqFX2FirmwareButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_monSeqFX2FirmwareButtonActionPerformed
   try {
            CypressFX2MonitorSequencer monseq;
            monseq = new CypressFX2MonitorSequencer(0);

            monseq.open();
            setWaitCursor(true);
            monseq.writeMonitorSequencerFirmware();
        } catch (Exception e) {
            e.printStackTrace();
        }
        setWaitCursor(false);
    }//GEN-LAST:event_monSeqFX2FirmwareButtonActionPerformed
    
    private void writeRAMRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeRAMRadioButtonActionPerformed
        prefs.putBoolean("CypressFX2EEPROM.writeEEPROMRadioButton",false);
    }//GEN-LAST:event_writeRAMRadioButtonActionPerformed
    
    private void writeEEPROMRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeEEPROMRadioButtonActionPerformed
        prefs.putBoolean("CypressFX2EEPROM.writeEEPROMRadioButton",true);
    }//GEN-LAST:event_writeEEPROMRadioButtonActionPerformed
    
    private void chooseFileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chooseFileButtonActionPerformed
        JFileChooser chooser=new JFileChooser(prefs.get("CypressFX2EEPROM.filepath",""));
//        JFileChooser chooser=new JFileChooser();
        FileFilter filter = new FileFilter() {
            public boolean accept(File f) {
                //if(f.getName().toLowerCase().endsWith(".iic") || f.getName().toLowerCase().endsWith(".hex") || f.isDirectory())
                if (f.getName().toLowerCase().endsWith(".iic") || f.isDirectory()) // hex download stopped working, only accept iic for the moment
                {
                    return true;
                } else {
                    return false;
                }
            }
            public String getDescription() {
                return "Firmware download file for Cypress FX2";
            }
        };
        chooser.setFileFilter(filter);
        chooser.setApproveButtonText("Choose");
        chooser.setToolTipText("Choose a binary download file (they are in the source path, e.g. ch/unizh/ini/caviar/hardwareinterface/usb)");
        int returnVal = chooser.showOpenDialog(this);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            File file=chooser.getSelectedFile();
            try {
                log.info("You chose this file: " + file.getCanonicalFile());
                filenameTextField.setText(file.getCanonicalPath());
                filenameTextField.setToolTipText(file.getCanonicalPath());
                prefs.put("CypressFX2EEPROM.filename",file.getCanonicalPath());
                prefs.put("CypressFX2EEPROM.filepath",file.getCanonicalPath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex);
            }
        }
    }//GEN-LAST:event_chooseFileButtonActionPerformed
    
    private void monSeqCPLDFirmwareButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_monSeqCPLDFirmwareButtonActionPerformed
        
            try {   
                setWaitCursor(true);
                cypress.writeCPLDfirmware(CypressFX2MonitorSequencer.CPLD_FIRMWARE_MONSEQ);
              
            } catch (Exception e) {
                e.printStackTrace();
            }
            setWaitCursor(false);
    }//GEN-LAST:event_monSeqCPLDFirmwareButtonActionPerformed
    
    private void writeDeviceIDButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeDeviceIDButtonActionPerformed
        if(hw==null) {
            log.warning("no device");
            return;
        }
        //  hw.close();
        try{
            
            cypress.open();
            cypress.setSerialNumber(writeDeviceIDTextField.getText());
            JOptionPane.showMessageDialog(this,"New serial number set, close and reopen the device to see the change.");
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,"Could not write new serial number, see log for info.");
        }
    }//GEN-LAST:event_writeDeviceIDButtonActionPerformed
    
    private void eraseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eraseButtonActionPerformed
        try{
            setWaitCursor(true);
            cypress.eraseEEPROM();
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }
        setWaitCursor(false);
    }//GEN-LAST:event_eraseButtonActionPerformed
    
    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        log.info("window closing");
    }//GEN-LAST:event_formWindowClosing
    
    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        log.info("window closed");
        if(hw!=null) hw.close();
        if(isExitOnCloseEnabled()) {
            System.exit(0);
        }
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
    
    
    private void downloadFirmwareButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_downloadFirmwareButtonActionPerformed
        File f=new File(filenameTextField.getText());
        
        if(!f.exists()){
            JOptionPane.showMessageDialog(this,"File doesn't exist. Please choose a binary download EEPROM image file (.iic) first.");
            return;
        }
        boolean isHexFile=f.getName().toLowerCase().endsWith(".hex");
        
        try{
            setWaitCursor(true);
//            ProgressMonitor progressMonitor=new  ProgressMonitor(chip.getAeViewer(), "Downloading firmware to EEPROM","", 0, task.getLengthOfTask());
            boolean toRam=writeRAMRadioButton.isSelected();
            if(!isHexFile){
                if(toRam){
                    cypress.downloadFirmwareBinary(filenameTextField.getText());
                    cypress.resetUSB();
                    cypress.cyclePort();
                } else if (f.getName().toLowerCase().endsWith(".iic")) {
                    cypress.writeEEPROM(0,cypress.loadBinaryFirmwareFile(filenameTextField.getText()));
                    JOptionPane.showMessageDialog(this,"Firmware written to EEPROM, unplug and replug the device to run it with the new firmware.");
                } else throw new UnsupportedOperationException("can't write binary firmware file to EEPROM");
            }else{
               JOptionPane.showMessageDialog(this,"Please choose an .iic file for downloading to EEPROM.");

               /* if(!toRam){
                    parseVIDPIDDID();
                    cypress.writeHexFileToEEPROM(filenameTextField.getText(),VID,PID,DID);
                }else throw new UnsupportedOperationException("can't write hex file to RAM");*/
            }
        }catch(Exception e){
            JOptionPane.showMessageDialog(this,e);
            e.printStackTrace();
        }
        setWaitCursor(false);
    }//GEN-LAST:event_downloadFirmwareButtonActionPerformed
    
    
    private void scanButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scanButtonActionPerformed
        scanForUsbIoDevices();
    }//GEN-LAST:event_scanButtonActionPerformed
    
    private void writeVIDPIDDIDButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeVIDPIDDIDButtonActionPerformed
        if(hw==null) {
            log.severe("null device");
            return;
        }
        short VID,PID,DID;
        try{
            VID=HexString.parseShort(VIDtextField.getText());
        }catch(ParseException e){
            e.printStackTrace();
            VIDtextField.selectAll();
            return;
        }
        try{
            PID=HexString.parseShort(PIDtextField.getText());
        }catch(ParseException e){
            e.printStackTrace();
            PIDtextField.selectAll();
            return;
        }
        try{
            DID=HexString.parseShort(DIDtextField.getText());
        }catch(ParseException e){
            e.printStackTrace();
            DIDtextField.selectAll();
            return;
        }
        log.info("Writing VID/PID/DID "+HexString.toString(VID)+"/"+HexString.toString(PID)+"/"+HexString.toString(DID));
        
        try{
            cypress.writeVIDPIDDID(VID,PID,DID);
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }
        log.info("done writing VID/PID/DID");
        
    }//GEN-LAST:event_writeVIDPIDDIDButtonActionPerformed
    
    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        log.info("exit");
        if(hw!=null) hw.close();
        
        if(isExitOnCloseEnabled()) {
            System.exit(0); // TODO add your handling code here:
        }else{
            dispose();
        }
    }//GEN-LAST:event_exitMenuItemActionPerformed

private void cyclePortButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cyclePortButtonActionPerformed
      if(cypress!=null){
          cypress.cyclePort();
      }        
}//GEN-LAST:event_cyclePortButtonActionPerformed

private void monSeqFX2FirmwareButtonJTAGActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_monSeqFX2FirmwareButtonJTAGActionPerformed
    int test = JOptionPane.showConfirmDialog(this, "CAUTION: Some of the boards built in Sevilla have an FX2 (instead of the FX2LP), which does not have enough RAM for this firmware. Please check if your device has an FX2LP. Device number of the FX2LP is CY7C68013A or 014A, FX2 is without A.", "FX2LP check", JOptionPane.OK_CANCEL_OPTION);
    if (test == JOptionPane.OK_OPTION) {
        try {
            CypressFX2MonitorSequencer monseq;
            monseq = new CypressFX2MonitorSequencer(0);

            monseq.open();
            setWaitCursor(true);
            monseq.writeMonitorSequencerJTAGFirmware();
        } catch (Exception e) {
            e.printStackTrace();
        }
        setWaitCursor(false);
    }
}//GEN-LAST:event_monSeqFX2FirmwareButtonJTAGActionPerformed
    
    
    // for bug in USBIO 2.30, need both cases, one for interface and other for JNI
    synchronized public void onAdd() {
        log.info("device added");
//        scanForUsbIoDevices();
    }
    
    synchronized public void onRemove() {
        log.info("device removed");
        scanForUsbIoDevices();
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel CPLDDownloadPanel;
    private javax.swing.JTextField CPLDfilenameField;
    private javax.swing.JTextField DIDtextField;
    private javax.swing.JTextField PIDtextField;
    private javax.swing.JPanel USBAERmini2panel;
    private javax.swing.JTextField VIDtextField;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton chooseCPLDFileButton;
    private javax.swing.JButton chooseFileButton;
    private javax.swing.JButton cyclePortButton;
    private javax.swing.JPanel deviceIDPanel;
    private javax.swing.JButton downloadCPLDFirmwareButton;
    private javax.swing.JButton downloadFirmwareButton;
    private javax.swing.JButton eraseButton;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JTextField filenameTextField;
    private javax.swing.JPanel firmwareDownloadPanel;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JButton monSeqCPLDFirmwareButton;
    private javax.swing.JButton monSeqFX2FirmwareButton;
    private javax.swing.JButton monSeqFX2FirmwareButtonJTAG;
    private javax.swing.JButton scanButton;
    private javax.swing.JPanel scanPanel;
    private javax.swing.JPanel vidpiddidPanel;
    private javax.swing.JButton writeDeviceIDButton;
    private javax.swing.JTextField writeDeviceIDTextField;
    private javax.swing.JRadioButton writeEEPROMRadioButton;
    private javax.swing.JRadioButton writeRAMRadioButton;
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
    
    /**
     * @param args the command line arguments (none)
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                CypressFX2EEPROM instance=new CypressFX2EEPROM();
                instance.setExitOnCloseEnabled(true);
                instance.setVisible(true);
            }
        });
    }
    
    private void parseVIDPIDDID() throws ParseException {
        try{
            VID=HexString.parseShort(VIDtextField.getText());
        }catch(ParseException e){
            VIDtextField.selectAll();
            throw new ParseException("bad VID number format",e.getErrorOffset());
        }
        try{
            PID=HexString.parseShort(PIDtextField.getText());
        }catch(ParseException e){
            e.printStackTrace();
            PIDtextField.selectAll();
            throw new ParseException("bad PID number format",e.getErrorOffset());
        }
        try{
            DID=HexString.parseShort(DIDtextField.getText());
        }catch(ParseException e){
            e.printStackTrace();
            DIDtextField.selectAll();
            throw new ParseException("bad DID number format",e.getErrorOffset());
        }
    }
    
} // CypressFX2EEPROM
