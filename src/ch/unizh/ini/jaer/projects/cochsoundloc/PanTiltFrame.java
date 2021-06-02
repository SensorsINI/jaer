/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * PanTiltFrame.java
 *
 * Created on 27.05.2009, 13:08:56
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import gnu.io.CommPortIdentifier;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

/**
 * Shows options for the pan tilt and the sensory fusion
 * 
 * @author Holger
 */
public class PanTiltFrame extends javax.swing.JFrame {

    public DatagramSocket datagramSocket = null;
    private Logger log = Logger.getLogger("PanTiltFrame");
    public PanTiltControl panTiltControl = null;
    final JFileChooser fc;
    private Clip clip;
    private boolean isCalibratingAuditoryMap = false;
    private boolean isCalibratingCochleaChannels = false;
    private int cochleaCalibrateAngles[];
    private float cochleaCalibrateITDs[];
    private int numCalibrationPoints = 0;
    private int curCalibrationPoint = 0;
    private float lastCochleaPanOffset;
    public PanTilt panTilt = null;
    private double maxPan = 1500;
    private double minPan = -700;
    private double maxTilt= 250;
    private double minTilt= -400;
    private double panPos = 0;
    private double tiltPos = 0;
    private double panPosThreshold = 0.1;
    private boolean servoLimitTouched;

    

    /** Creates new form PanTiltFrame */
    public PanTiltFrame(PanTilt panTilt) {
        this.panTilt = panTilt;
        initComponents();
        fc = new JFileChooser();
        fc.setDialogTitle("DateiAuswahl");
        fc.setFileFilter(new FileFilter() {

            public String getDescription() {
                return "audio file (*.wav)";
            }

            @Override
            public boolean accept(File f) {
                return f.getName().toLowerCase().endsWith(".wav") || f.isDirectory();
            }
        });

        Enumeration pList = CommPortIdentifier.getPortIdentifiers();
        while (pList.hasMoreElements()) {
            CommPortIdentifier cpi = (CommPortIdentifier) pList.nextElement();
            if (cpi.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                cbxComPort.addItem(cpi.getName());
            }
        }
        
        cbxComPort.setSelectedIndex(-1);
        updateBoundaries();
        this.pack();
//        this.setSize(40, 100);
    }

    public void setRetinaPanOffset(float retinaPanOffset) {
        txtRetinaPanOffset.setText(String.format("%.1f", retinaPanOffset));
        repaint();
    }

    public void setCochleaPanOffset(float cochleaPanOffset) {
        lastCochleaPanOffset = cochleaPanOffset;
        txtCochleaPanOffset.setText(String.format("%.1f", cochleaPanOffset));
        repaint();
    }

    public void setRetinaTiltOffset(float retinaTiltOffset) {
        txtRetinaTiltOffset.setText(String.format("%.1f", retinaTiltOffset));
        repaint();
    }

    public void setCochleaTiltOffset(float cochleaTiltOffset) {
        txtCochleaTiltOffset.setText(String.format("%.1f", cochleaTiltOffset));
        repaint();
    }

    public void setRetinaConfidence(float retinaConfidence) {
        txtRetinaConfidence.setText(String.format("%.1f", retinaConfidence));
        repaint();
    }

    public void setCochleaConfidence(float cochleaConfidence) {
        txtCochleaConfidence.setText(String.format("%.1f", cochleaConfidence));
        repaint();
    }

    public boolean isLogResponse() {
        return cbxLogResponse.isSelected();
    }

    public boolean isUseCochlea() {
        return cbxUseCochlea.isSelected();
    }

    public boolean isUseRetina() {
        return cbxUseRetina.isSelected();
    }

    public boolean isLearnCochlea() {
        return cbxLearnCochlea.isSelected();
    }

    public boolean isLearnRetina() {
        return cbxLearnRetina.isSelected();
    }

    public int getCochleaThreshold() {
        return Integer.parseInt(txtCochleaThreshold.getText());
    }

    public int getRetinaThreshold() {
        return Integer.parseInt(txtRetinaThreshold.getText());
    }

    public double getCochleaPanScaling() {
        return Double.parseDouble(txtCochleaPanScaling.getText());
    }

    public double getRetinaPanScaling() {
        return Double.parseDouble(txtRetinaPanScaling.getText());
    }

    public double getCochleaTiltScaling() {
        return Double.parseDouble(txtCochleaTiltScaling.getText());
    }

    public double getRetinaTiltScaling() {
        return Double.parseDouble(txtRetinaTiltScaling.getText());
    }

    public void setCochleaPanScaling(double scaling) {
        txtCochleaPanScaling.setText(String.valueOf(scaling));
    }
    public void setCochleaTiltScaling(double scaling) {
        txtCochleaTiltScaling.setText(String.valueOf(scaling));
    }
    public void setRetinaPanScaling(double scaling) {
        txtRetinaPanScaling.setText(String.valueOf(scaling));
    }
    public void setRetinaTiltScaling(double scaling) {
        txtRetinaTiltScaling.setText(String.valueOf(scaling));
    }

    //input between -1 and 1
    public void setPanPos(double pos) {
        pos=checkServoLimits(pos);
        double scaled = scalePanToDevice(pos);
        this.panPos = scalePanFromDevice(scaled);
        DecimalFormat format = new DecimalFormat("#.##");
        this.txtPanPos.setText(format.format(new Double(scaled)));
        sldPanPos.setValue((int)(this.panPos*1000));
        if (panTiltControl != null && panTiltControl.isConnected()) {
            panTiltControl.setPanPos(scaled);
        }
    }

    //input between -1 and 1
    public void setTiltPos(double pos) {
        pos=checkServoLimits(pos);
        double scaled = scaleTiltToDevice(pos);
        this.tiltPos = scaleTiltFromDevice(scaled);
        DecimalFormat format = new DecimalFormat("#.##");
        this.txtTiltPos.setText(format.format(new Double(scaled)));
        sldTiltPos.setValue((int)(this.tiltPos*1000));
        if (panTiltControl != null && panTiltControl.isConnected()) {
            panTiltControl.setTiltPos(scaled);
        }
    }

    private double checkServoLimits(double pos)
    {
        setServoLimitTouched(false);
        if(pos>1.0)
        {
            pos=1.0;
            setServoLimitTouched(true);
        }
        if(pos<-1.0)
        {
            pos=-1.0;
            setServoLimitTouched(true);
        }
        return pos;
    }

    private double scalePanToDevice(double posNorm)
    {
        double devicePos = minPan+((posNorm+1.0)/2.0)*(maxPan-minPan);
        return devicePos;
    }

    private double scalePanFromDevice(double posDevice)
    {
        return 2.0*(posDevice-minPan)/(maxPan-minPan)-1.0;
    }

    private double scaleTiltToDevice(double posNorm)
    {
        double devicePos = minTilt+((posNorm+1.0)/2.0)*(maxTilt-minTilt);
        return devicePos;
    }

    private double scaleTiltFromDevice(double posDevice)
    {
        return 2.0*(posDevice-minTilt)/(maxTilt-minTilt)-1.0;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        FilterOutput = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        txtRetinaPanOffset = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        txtCochleaPanOffset = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        txtRetinaTiltOffset = new javax.swing.JTextField();
        txtCochleaTiltOffset = new javax.swing.JTextField();
        txtCochleaConfidence = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        txtRetinaConfidence = new javax.swing.JTextField();
        txtCochleaThreshold = new javax.swing.JTextField();
        txtRetinaThreshold = new javax.swing.JTextField();
        jLabel7 = new javax.swing.JLabel();
        cbxUseRetina = new javax.swing.JCheckBox();
        cbxUseCochlea = new javax.swing.JCheckBox();
        jLabel10 = new javax.swing.JLabel();
        txtCochleaPanScaling = new javax.swing.JTextField();
        txtRetinaPanScaling = new javax.swing.JTextField();
        txtCochleaTiltScaling = new javax.swing.JTextField();
        txtRetinaTiltScaling = new javax.swing.JTextField();
        cbxLearnCochlea = new javax.swing.JCheckBox();
        cbxLearnRetina = new javax.swing.JCheckBox();
        jLabel11 = new javax.swing.JLabel();
        PanTiltCommands = new javax.swing.JPanel();
        jLabel16 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        cbxComPort = new javax.swing.JComboBox();
        txtSpeed = new javax.swing.JTextField();
        txtCommand = new javax.swing.JTextField();
        btnSetSpeed = new javax.swing.JButton();
        btnExecuteCommand = new javax.swing.JButton();
        btnConnect = new javax.swing.JButton();
        btnHalt = new javax.swing.JButton();
        txtRUBIServer = new javax.swing.JTextField();
        btnConnectRUBIServer = new javax.swing.JButton();
        jLabel22 = new javax.swing.JLabel();
        btnConnectDynamixel = new javax.swing.JButton();
        PanTiltPosition = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        sldPanPos = new javax.swing.JSlider();
        sldTiltPos = new javax.swing.JSlider();
        txtPanPos = new javax.swing.JTextField();
        txtTiltPos = new javax.swing.JTextField();
        btnSetTiltPos = new javax.swing.JButton();
        btnSetPanPos = new javax.swing.JButton();
        jLabel13 = new javax.swing.JLabel();
        txtTiltPosMin = new javax.swing.JTextField();
        txtPanPosMin = new javax.swing.JTextField();
        jLabel14 = new javax.swing.JLabel();
        txtPanPosMax = new javax.swing.JTextField();
        btnResetPan = new javax.swing.JButton();
        txtTiltPosMax = new javax.swing.JTextField();
        btnResetTilt = new javax.swing.JButton();
        txtPanOffsetThreshold = new javax.swing.JTextField();
        jLabel23 = new javax.swing.JLabel();
        FilterCalibration = new javax.swing.JPanel();
        LoadWave = new javax.swing.JButton();
        btnCalibrate = new javax.swing.JButton();
        txtNumCalibratePoints = new javax.swing.JTextField();
        jLabel19 = new javax.swing.JLabel();
        btnLocateAudioSource = new javax.swing.JButton();
        btnCalibrateCochlea = new javax.swing.JButton();
        PanTiltProperties = new javax.swing.JPanel();
        jLabel17 = new javax.swing.JLabel();
        txtWaitPeriod = new javax.swing.JTextField();
        jLabel18 = new javax.swing.JLabel();
        cbxLogResponse = new javax.swing.JCheckBox();
        cbxOpenUDP = new javax.swing.JCheckBox();
        btnSendCommand = new javax.swing.JButton();
        txtUDPServer = new javax.swing.JTextField();
        txtUDPPort = new javax.swing.JTextField();
        jLabel20 = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        btnSendRubi = new javax.swing.JButton();
        btnIsMoving = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Pan-TIlt");
        setName("Pan-Tilt"); // NOI18N

        jScrollPane1.setBorder(null);
        jScrollPane1.setPreferredSize(new java.awt.Dimension(759, 463));

        jPanel1.setPreferredSize(new java.awt.Dimension(759, 463));

        FilterOutput.setBorder(javax.swing.BorderFactory.createTitledBorder("Filter Outputs"));

        jLabel2.setText("Retina:");

        jLabel1.setText("Cochlea:");

        txtRetinaPanOffset.setEditable(false);
        txtRetinaPanOffset.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtRetinaPanOffset.setText("1");

        jLabel5.setText("Pan-Offset");

        txtCochleaPanOffset.setEditable(false);
        txtCochleaPanOffset.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtCochleaPanOffset.setText("1");

        jLabel8.setText("Tilt-Offset");

        txtRetinaTiltOffset.setEditable(false);
        txtRetinaTiltOffset.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtRetinaTiltOffset.setText("1");

        txtCochleaTiltOffset.setEditable(false);
        txtCochleaTiltOffset.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtCochleaTiltOffset.setText("1");

        txtCochleaConfidence.setEditable(false);
        txtCochleaConfidence.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtCochleaConfidence.setText("1");

        jLabel6.setText("Confidence");

        txtRetinaConfidence.setEditable(false);
        txtRetinaConfidence.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtRetinaConfidence.setText("1");

        txtCochleaThreshold.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtCochleaThreshold.setText("1");

        txtRetinaThreshold.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtRetinaThreshold.setText("1");

        jLabel7.setText("Threshold");

        jLabel10.setText("Use");

        txtCochleaPanScaling.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtCochleaPanScaling.setText("2");

        txtRetinaPanScaling.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtRetinaPanScaling.setText("0.5");

        txtCochleaTiltScaling.setEditable(false);
        txtCochleaTiltScaling.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtCochleaTiltScaling.setText("1");

        txtRetinaTiltScaling.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtRetinaTiltScaling.setText("0.5");

        jLabel11.setText("Learn");

        javax.swing.GroupLayout FilterOutputLayout = new javax.swing.GroupLayout(FilterOutput);
        FilterOutput.setLayout(FilterOutputLayout);
        FilterOutputLayout.setHorizontalGroup(
            FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(FilterOutputLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel5)
                    .addGroup(FilterOutputLayout.createSequentialGroup()
                        .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(txtRetinaPanOffset, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtCochleaPanOffset, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 28, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtRetinaPanScaling, javax.swing.GroupLayout.DEFAULT_SIZE, 21, Short.MAX_VALUE)
                            .addComponent(txtCochleaPanScaling, javax.swing.GroupLayout.DEFAULT_SIZE, 21, Short.MAX_VALUE))))
                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(FilterOutputLayout.createSequentialGroup()
                        .addGap(33, 33, 33)
                        .addComponent(jLabel8))
                    .addGroup(FilterOutputLayout.createSequentialGroup()
                        .addGap(18, 18, 18)
                        .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(FilterOutputLayout.createSequentialGroup()
                                .addComponent(txtRetinaTiltOffset, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtRetinaTiltScaling, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(FilterOutputLayout.createSequentialGroup()
                                .addComponent(txtCochleaTiltOffset, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtCochleaTiltScaling, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addGap(18, 18, 18)
                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtCochleaConfidence, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6)
                    .addComponent(txtRetinaConfidence, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtCochleaThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtRetinaThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7))
                .addGap(18, 18, 18)
                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(FilterOutputLayout.createSequentialGroup()
                        .addComponent(cbxUseRetina)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(cbxLearnRetina))
                    .addGroup(FilterOutputLayout.createSequentialGroup()
                        .addComponent(cbxUseCochlea)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(cbxLearnCochlea))
                    .addGroup(FilterOutputLayout.createSequentialGroup()
                        .addComponent(jLabel10)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel11)))
                .addGap(13, 13, 13))
        );
        FilterOutputLayout.setVerticalGroup(
            FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(FilterOutputLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(FilterOutputLayout.createSequentialGroup()
                        .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel6)
                            .addComponent(jLabel7)
                            .addComponent(jLabel10)
                            .addComponent(jLabel11))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(FilterOutputLayout.createSequentialGroup()
                                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(txtCochleaConfidence, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(txtCochleaThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(18, 18, 18)
                                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(txtRetinaConfidence, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(txtRetinaThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(FilterOutputLayout.createSequentialGroup()
                                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(cbxUseCochlea)
                                    .addComponent(cbxLearnCochlea))
                                .addGap(18, 18, 18)
                                .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(cbxLearnRetina)
                                    .addComponent(cbxUseRetina)))))
                    .addGroup(FilterOutputLayout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel1)
                            .addComponent(txtCochleaPanOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtCochleaPanScaling, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtCochleaTiltOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtCochleaTiltScaling, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(FilterOutputLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel2)
                            .addComponent(txtRetinaPanOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtRetinaPanScaling, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtRetinaTiltOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtRetinaTiltScaling, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jLabel8))
                .addContainerGap(14, Short.MAX_VALUE))
        );

        PanTiltCommands.setBorder(javax.swing.BorderFactory.createTitledBorder("Pan-Tilt Properties"));

        jLabel16.setText("Speed:");

        jLabel15.setText("Command:");

        jLabel3.setText("COM-Port:");

        cbxComPort.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "COM10", "COM11", "COM12" }));

        txtSpeed.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtSpeed.setText("1000");

        txtCommand.setText("PP100");

        btnSetSpeed.setText("Set Pan-Tilt-Speed");
        btnSetSpeed.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSetSpeedActionPerformed(evt);
            }
        });

        btnExecuteCommand.setText("Execute Command");
        btnExecuteCommand.setMaximumSize(new java.awt.Dimension(1000, 1000));
        btnExecuteCommand.setMinimumSize(new java.awt.Dimension(200, 200));
        btnExecuteCommand.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExecuteCommandActionPerformed(evt);
            }
        });

        btnConnect.setText("Connect to Pan-Tilt-Unit");
        btnConnect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnConnectActionPerformed(evt);
            }
        });

        btnHalt.setText("Halt Pan-Tilt");
        btnHalt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnHaltActionPerformed(evt);
            }
        });

        txtRUBIServer.setText("172.19.115.48");

        btnConnectRUBIServer.setText("Connect to RUBIOS");
        btnConnectRUBIServer.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnConnectRUBIServerActionPerformed(evt);
            }
        });

        jLabel22.setText("IP:");

        btnConnectDynamixel.setText("Dynamixel");
        btnConnectDynamixel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnConnectDynamixelActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout PanTiltCommandsLayout = new javax.swing.GroupLayout(PanTiltCommands);
        PanTiltCommands.setLayout(PanTiltCommandsLayout);
        PanTiltCommandsLayout.setHorizontalGroup(
            PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PanTiltCommandsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(PanTiltCommandsLayout.createSequentialGroup()
                        .addComponent(jLabel22)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtRUBIServer, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(PanTiltCommandsLayout.createSequentialGroup()
                        .addGroup(PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel16, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel15, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(cbxComPort, 0, 0, Short.MAX_VALUE)
                            .addComponent(txtSpeed)
                            .addComponent(txtCommand, javax.swing.GroupLayout.PREFERRED_SIZE, 59, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addGroup(PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(PanTiltCommandsLayout.createSequentialGroup()
                        .addGap(8, 8, 8)
                        .addGroup(PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(btnSetSpeed, javax.swing.GroupLayout.DEFAULT_SIZE, 203, Short.MAX_VALUE)
                            .addComponent(btnExecuteCommand, javax.swing.GroupLayout.DEFAULT_SIZE, 203, Short.MAX_VALUE)
                            .addComponent(btnHalt, javax.swing.GroupLayout.DEFAULT_SIZE, 203, Short.MAX_VALUE)
                            .addGroup(PanTiltCommandsLayout.createSequentialGroup()
                                .addComponent(btnConnect)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnConnectDynamixel))))
                    .addGroup(PanTiltCommandsLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnConnectRUBIServer, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        PanTiltCommandsLayout.setVerticalGroup(
            PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PanTiltCommandsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                    .addComponent(btnConnect)
                    .addComponent(cbxComPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(btnConnectDynamixel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                    .addComponent(txtCommand, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnExecuteCommand, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel15))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                    .addComponent(btnSetSpeed)
                    .addComponent(txtSpeed, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel16))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnHalt, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(PanTiltCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtRUBIServer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel22)
                    .addComponent(btnConnectRUBIServer))
                .addContainerGap())
        );

        PanTiltPosition.setBorder(javax.swing.BorderFactory.createTitledBorder("Pan-Tilt Positioning"));

        jLabel4.setText("Set Pan Position:");

        jLabel9.setText("Set Tilt Position:");

        sldPanPos.setMaximum(1000);
        sldPanPos.setMinimum(-1000);
        sldPanPos.setValue(0);
        sldPanPos.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sldPanPosStateChanged(evt);
            }
        });

        sldTiltPos.setMaximum(1000);
        sldTiltPos.setMinimum(-1000);
        sldTiltPos.setValue(0);
        sldTiltPos.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sldTiltPosStateChanged(evt);
            }
        });

        txtPanPos.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtPanPos.setText("0");
        txtPanPos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtPanPosActionPerformed(evt);
            }
        });

        txtTiltPos.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtTiltPos.setText("0");
        txtTiltPos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtTiltPosActionPerformed(evt);
            }
        });

        btnSetTiltPos.setText("Set");
        btnSetTiltPos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSetTiltPosActionPerformed(evt);
            }
        });

        btnSetPanPos.setText("Set");
        btnSetPanPos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSetPanPosActionPerformed(evt);
            }
        });

        jLabel13.setText("Min:");

        txtTiltPosMin.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtTiltPosMin.setText("-400");
        txtTiltPosMin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtTiltPosMinActionPerformed(evt);
            }
        });
        txtTiltPosMin.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtTiltPosMinFocusLost(evt);
            }
        });

        txtPanPosMin.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtPanPosMin.setText("-700");
        txtPanPosMin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtPanPosMinActionPerformed(evt);
            }
        });
        txtPanPosMin.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtPanPosMinFocusLost(evt);
            }
        });

        jLabel14.setText("Max:");

        txtPanPosMax.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtPanPosMax.setText("1500");
        txtPanPosMax.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtPanPosMaxActionPerformed(evt);
            }
        });
        txtPanPosMax.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtPanPosMaxFocusLost(evt);
            }
        });

        btnResetPan.setText("Reset Pan");
        btnResetPan.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnResetPanActionPerformed(evt);
            }
        });

        txtTiltPosMax.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtTiltPosMax.setText("250");
        txtTiltPosMax.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtTiltPosMaxActionPerformed(evt);
            }
        });
        txtTiltPosMax.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtTiltPosMaxFocusLost(evt);
            }
        });

        btnResetTilt.setText("Reset Tilt");
        btnResetTilt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnResetTiltActionPerformed(evt);
            }
        });

        txtPanOffsetThreshold.setText("0.1");
        txtPanOffsetThreshold.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtPanOffsetThresholdActionPerformed(evt);
            }
        });

        jLabel23.setText("Threshold");

        javax.swing.GroupLayout PanTiltPositionLayout = new javax.swing.GroupLayout(PanTiltPosition);
        PanTiltPosition.setLayout(PanTiltPositionLayout);
        PanTiltPositionLayout.setHorizontalGroup(
            PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PanTiltPositionLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel4)
                    .addComponent(jLabel9))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(sldPanPos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sldTiltPos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(txtPanPos)
                    .addComponent(txtTiltPos, javax.swing.GroupLayout.DEFAULT_SIZE, 61, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnSetTiltPos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnSetPanPos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtPanPosMin, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtTiltPosMin, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel13))
                .addGap(18, 18, 18)
                .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(PanTiltPositionLayout.createSequentialGroup()
                        .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(PanTiltPositionLayout.createSequentialGroup()
                                .addComponent(txtTiltPosMax, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnResetTilt, javax.swing.GroupLayout.DEFAULT_SIZE, 83, Short.MAX_VALUE))
                            .addGroup(PanTiltPositionLayout.createSequentialGroup()
                                .addComponent(txtPanPosMax, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnResetPan)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED))
                    .addGroup(PanTiltPositionLayout.createSequentialGroup()
                        .addComponent(jLabel14)
                        .addGap(123, 123, 123)))
                .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtPanOffsetThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel23))
                .addContainerGap(62, Short.MAX_VALUE))
        );
        PanTiltPositionLayout.setVerticalGroup(
            PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PanTiltPositionLayout.createSequentialGroup()
                .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(jLabel14)
                    .addComponent(jLabel23))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel4)
                    .addComponent(sldPanPos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtPanPos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSetPanPos)
                    .addComponent(txtPanPosMin, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtPanPosMax, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnResetPan)
                    .addComponent(txtPanOffsetThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(PanTiltPositionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel9)
                    .addComponent(sldTiltPos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtTiltPos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSetTiltPos)
                    .addComponent(txtTiltPosMin, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtTiltPosMax, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnResetTilt))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        FilterCalibration.setBorder(javax.swing.BorderFactory.createTitledBorder("Calibration"));

        LoadWave.setText("Load Audio File");
        LoadWave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LoadWaveActionPerformed(evt);
            }
        });

        btnCalibrate.setText("Calibrate Auditory Map");
        btnCalibrate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCalibrateActionPerformed(evt);
            }
        });

        txtNumCalibratePoints.setText("10");

        jLabel19.setText("Number of Points to calibrate:");

        btnLocateAudioSource.setText("Locate Audio Source");

        btnCalibrateCochlea.setText("Calibrate Cochlea");
        btnCalibrateCochlea.setEnabled(false);
        btnCalibrateCochlea.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCalibrateCochleaActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout FilterCalibrationLayout = new javax.swing.GroupLayout(FilterCalibration);
        FilterCalibration.setLayout(FilterCalibrationLayout);
        FilterCalibrationLayout.setHorizontalGroup(
            FilterCalibrationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, FilterCalibrationLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(FilterCalibrationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel19, javax.swing.GroupLayout.PREFERRED_SIZE, 145, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(LoadWave)
                    .addComponent(btnCalibrate))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(FilterCalibrationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtNumCalibratePoints, javax.swing.GroupLayout.PREFERRED_SIZE, 145, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnLocateAudioSource, javax.swing.GroupLayout.PREFERRED_SIZE, 145, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnCalibrateCochlea, javax.swing.GroupLayout.PREFERRED_SIZE, 0, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        FilterCalibrationLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {LoadWave, btnCalibrate, btnCalibrateCochlea, btnLocateAudioSource, jLabel19, txtNumCalibratePoints});

        FilterCalibrationLayout.setVerticalGroup(
            FilterCalibrationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(FilterCalibrationLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(FilterCalibrationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel19, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtNumCalibratePoints, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(FilterCalibrationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(LoadWave)
                    .addComponent(btnLocateAudioSource))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(FilterCalibrationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnCalibrateCochlea)
                    .addComponent(btnCalibrate))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        FilterCalibrationLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {LoadWave, btnCalibrate, btnCalibrateCochlea, btnLocateAudioSource, jLabel19, txtNumCalibratePoints});

        PanTiltProperties.setBorder(javax.swing.BorderFactory.createTitledBorder("Options"));

        jLabel17.setText("Wait Period after Movement:");

        txtWaitPeriod.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtWaitPeriod.setText("500");
        txtWaitPeriod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtWaitPeriodActionPerformed(evt);
            }
        });

        jLabel18.setText("ms");

        cbxLogResponse.setText("Log Pan-Tilt Responses");
        cbxLogResponse.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbxLogResponseActionPerformed(evt);
            }
        });

        cbxOpenUDP.setText("Send Filteroutputs to UDP-Server");
        cbxOpenUDP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbxOpenUDPActionPerformed(evt);
            }
        });

        btnSendCommand.setText("Test");
        btnSendCommand.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSendCommandActionPerformed(evt);
            }
        });

        txtUDPServer.setText("localhost");

        txtUDPPort.setText("7778");

        jLabel20.setText("UDP-Server:");

        jLabel21.setText("UDP-Port:");

        btnSendRubi.setText("Send Hello to RUBIOS Server");
        btnSendRubi.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSendRubiActionPerformed(evt);
            }
        });

        btnIsMoving.setText("Check if Moving");
        btnIsMoving.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnIsMovingActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout PanTiltPropertiesLayout = new javax.swing.GroupLayout(PanTiltProperties);
        PanTiltProperties.setLayout(PanTiltPropertiesLayout);
        PanTiltPropertiesLayout.setHorizontalGroup(
            PanTiltPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PanTiltPropertiesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(PanTiltPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(PanTiltPropertiesLayout.createSequentialGroup()
                        .addComponent(cbxOpenUDP)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 58, Short.MAX_VALUE)
                        .addComponent(btnSendCommand))
                    .addComponent(cbxLogResponse, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, PanTiltPropertiesLayout.createSequentialGroup()
                        .addComponent(btnIsMoving)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 18, Short.MAX_VALUE)
                        .addComponent(btnSendRubi))
                    .addGroup(PanTiltPropertiesLayout.createSequentialGroup()
                        .addComponent(jLabel17)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtWaitPeriod, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel18))
                    .addGroup(PanTiltPropertiesLayout.createSequentialGroup()
                        .addComponent(jLabel20)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(txtUDPServer, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel21)
                        .addGap(4, 4, 4)
                        .addComponent(txtUDPPort, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        PanTiltPropertiesLayout.setVerticalGroup(
            PanTiltPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PanTiltPropertiesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(PanTiltPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel17)
                    .addComponent(txtWaitPeriod, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel18))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cbxLogResponse, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(PanTiltPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel20)
                    .addComponent(txtUDPServer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel21)
                    .addComponent(txtUDPPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 8, Short.MAX_VALUE)
                .addGroup(PanTiltPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbxOpenUDP)
                    .addComponent(btnSendCommand))
                .addGap(11, 11, 11)
                .addGroup(PanTiltPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnSendRubi)
                    .addComponent(btnIsMoving))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(PanTiltPosition, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(FilterOutput, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(PanTiltCommands, javax.swing.GroupLayout.PREFERRED_SIZE, 435, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(PanTiltProperties, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(FilterCalibration, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(FilterCalibration, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(FilterOutput, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(8, 8, 8)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(PanTiltProperties, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(PanTiltCommands, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(PanTiltPosition, javax.swing.GroupLayout.PREFERRED_SIZE, 114, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectActionPerformed
        //if (panTiltControl == null) {
            panTiltControl = new PanTiltControlPTU();
        //}
        panTiltControl.setWaitPeriod(Integer.parseInt(txtWaitPeriod.getText()));
        if (panTiltControl.isConnected() == false) {
            //it first tries to connect to the 'original', serial PTU unit - if this does not work, it tries to connect to a USB unit and if this also fails it prints the error report.
            try {
                panTiltControl.connect((String) this.cbxComPort.getSelectedItem());
            } catch (Exception e1) {
                panTiltControl = new PanTiltControlUSB();
                 try {
                    panTiltControl.connect((String) this.cbxComPort.getSelectedItem());
                } catch (Exception e2) {
                    e1.printStackTrace();
                    e2.printStackTrace();
                }
            }
        }
        panTiltControl.addPanTiltListener(new PanTiltListener() {

            public void panTiltAction(PanTiltEvent evt) {
                if (evt.getStatus() == 0) {
                   // log.info("Movement Done!");
                    if (isCalibratingAuditoryMap == true) {
                        sendMessageToITDFilter(5, 0);
                        clip.setFramePosition(0);
                        clip.start();
                    }
                    if (isCalibratingCochleaChannels == true) {
                        sendMessageToITDFilter(1, cochleaCalibrateITDs[curCalibrationPoint]);
                        clip.setFramePosition(0);
                        clip.start();
                    }
                }
                else if (evt.getStatus() == 1) {
                    //log.info("Still Moving!");
                }
            }
        });
    }//GEN-LAST:event_btnConnectActionPerformed

    private void btnExecuteCommandActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExecuteCommandActionPerformed
        if (panTiltControl != null && panTiltControl.isConnected()) {
            panTiltControl.executeCommand(this.txtCommand.getText());
        }
    }//GEN-LAST:event_btnExecuteCommandActionPerformed

    private void btnSetSpeedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetSpeedActionPerformed
        if (panTiltControl == null || panTiltControl.isConnected() == false) {
            JOptionPane.showMessageDialog(null, "Not Connected to Pan-Tilt-Unit", "Not Connected", JOptionPane.OK_CANCEL_OPTION);
        } else {
            panTiltControl.setPanSpeed(Integer.parseInt(this.txtSpeed.getText()));
        }
    }//GEN-LAST:event_btnSetSpeedActionPerformed

    private void btnHaltActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnHaltActionPerformed
        if (panTiltControl == null || panTiltControl.isConnected() == false) {
            JOptionPane.showMessageDialog(null, "Not Connected to Pan-Tilt-Unit", "Not Connected", JOptionPane.OK_CANCEL_OPTION);
        } else {
            panTiltControl.halt();
        }
    }//GEN-LAST:event_btnHaltActionPerformed

    private void sldPanPosStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_sldPanPosStateChanged
        DecimalFormat format = new DecimalFormat("#.##");
        txtPanPos.setText(format.format(new Double(this.scalePanToDevice(sldPanPos.getValue()/1000.0))));
    }//GEN-LAST:event_sldPanPosStateChanged

    private void sldTiltPosStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_sldTiltPosStateChanged
DecimalFormat format = new DecimalFormat("#.##");
        txtTiltPos.setText(format.format(new Double(this.scaleTiltToDevice(sldTiltPos.getValue()/1000.0))));
    }//GEN-LAST:event_sldTiltPosStateChanged

    private void btnSetPanPosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetPanPosActionPerformed
        this.setPanPos(this.scalePanFromDevice(Double.parseDouble(txtPanPos.getText())));
    }//GEN-LAST:event_btnSetPanPosActionPerformed

    private void btnSetTiltPosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetTiltPosActionPerformed
       this.setTiltPos(this.scaleTiltFromDevice(Double.parseDouble(txtTiltPos.getText())));
    }//GEN-LAST:event_btnSetTiltPosActionPerformed

    private void txtPanPosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtPanPosActionPerformed
        btnSetPanPosActionPerformed(null);
    }//GEN-LAST:event_txtPanPosActionPerformed

    private void txtTiltPosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtTiltPosActionPerformed
        btnSetTiltPosActionPerformed(null);
    }//GEN-LAST:event_txtTiltPosActionPerformed

    private void txtTiltPosMinActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtTiltPosMinActionPerformed

        updateBoundaries();
	}//GEN-LAST:event_txtTiltPosMinActionPerformed

    private void txtTiltPosMaxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtTiltPosMaxActionPerformed

        updateBoundaries();
	}//GEN-LAST:event_txtTiltPosMaxActionPerformed

    private void txtPanPosMinActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtPanPosMinActionPerformed

        updateBoundaries();
	}//GEN-LAST:event_txtPanPosMinActionPerformed

    private void txtPanPosMaxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtPanPosMaxActionPerformed

        updateBoundaries();
	}//GEN-LAST:event_txtPanPosMaxActionPerformed

    private void cbxLogResponseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbxLogResponseActionPerformed
        if (panTiltControl != null) {
            this.panTiltControl.setLogResponses(cbxLogResponse.isSelected());
        }
    }//GEN-LAST:event_cbxLogResponseActionPerformed

    private void txtWaitPeriodActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtWaitPeriodActionPerformed
        if (panTiltControl != null) {
            panTiltControl.setWaitPeriod(Integer.parseInt(txtWaitPeriod.getText()));
        }
    }//GEN-LAST:event_txtWaitPeriodActionPerformed

    private void btnResetPanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnResetPanActionPerformed
        if (panTiltControl == null || panTiltControl.isConnected() == false) {
            JOptionPane.showMessageDialog(null, "Not Connected to Pan-Tilt-Unit", "Not Connected", JOptionPane.OK_CANCEL_OPTION);
        } else {
            panTiltControl.executeCommand("RP");
        }
    }//GEN-LAST:event_btnResetPanActionPerformed

    private void btnResetTiltActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnResetTiltActionPerformed
        if (panTiltControl == null || panTiltControl.isConnected() == false) {
            JOptionPane.showMessageDialog(null, "Not Connected to Pan-Tilt-Unit", "Not Connected", JOptionPane.OK_CANCEL_OPTION);
        } else {
            if (panTiltControl.getClass() == PanTiltControlPTU.class) {
                panTiltControl.executeCommand("RT");
            }
            //if (panTiltControl.getClass() == PanTiltControlDynamixel.class) {
              //  (PanTiltControlDynamixel)panTiltControl.
            //}
        }
    }//GEN-LAST:event_btnResetTiltActionPerformed

    private void LoadWaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LoadWaveActionPerformed
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            try {
                AudioInputStream stream = AudioSystem.getAudioInputStream(file);
                AudioFormat format = stream.getFormat();
                if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                    format = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            format.getSampleRate(),
                            format.getSampleSizeInBits() * 2,
                            format.getChannels(),
                            format.getFrameSize() * 2,
                            format.getFrameRate(),
                            true);        // big endian
                    stream = AudioSystem.getAudioInputStream(format, stream);
                }
                DataLine.Info info = new DataLine.Info(Clip.class, stream.getFormat(), ((int) stream.getFrameLength() * format.getFrameSize()));
                clip = (Clip) AudioSystem.getLine(info);
                clip.open(stream);

                clip.addLineListener(new LineListener() {

                    public void update(LineEvent event) {
                        if (event.getType() == LineEvent.Type.STOP) {
                            // play done!
                            log.info("Play Done!");
                            if (isCalibratingAuditoryMap == true) {
                                cochleaCalibrateITDs[curCalibrationPoint] = lastCochleaPanOffset;
                                if (curCalibrationPoint < numCalibrationPoints - 1) {
                                    curCalibrationPoint++;
                                    startNextCalibration();
                                } else {
                                    log.info("Calibration of auditory map finished. writing to file...");
                                    try {
                                        FileWriter fstream = new FileWriter("AuditoryMap.dat");
                                        BufferedWriter ITDFile = new BufferedWriter(fstream);
                                        ITDFile.write("PanPos\tITD\n");
                                        for (int i=0; i<numCalibrationPoints;i++) {
                                            ITDFile.write(cochleaCalibrateAngles[i] + "\t" + cochleaCalibrateITDs[i] +"\n");
                                        }
                                        ITDFile.close();
                                    } catch (Exception e) {
                                        System.err.println("Error: " + e.getMessage());
                                    }
                                    btnCalibrateCochlea.setEnabled(true);
                                    isCalibratingAuditoryMap = false;
                                    sendMessageToITDFilter(4, 0);
                                }
                            }
                            if (isCalibratingCochleaChannels == true) {
                                if (curCalibrationPoint < numCalibrationPoints - 1) {
                                    curCalibrationPoint++;
                                    startNextCalibration();
                                } else {
                                    log.info("Calibration of Channels finished!");
                                    isCalibratingCochleaChannels = false;
                                }
                            }

                        }
                    }
                });

            } catch (java.net.MalformedURLException e) {
            } catch (java.io.IOException e) {
            } catch (LineUnavailableException e) {
            } catch (UnsupportedAudioFileException e) {
            }
        }
    }//GEN-LAST:event_LoadWaveActionPerformed

    private void startNextCalibration() {
        log.info("StartnextCalibrationPoint! PanPos:" + cochleaCalibrateAngles[this.curCalibrationPoint]);
        this.sldPanPos.setValue(cochleaCalibrateAngles[this.curCalibrationPoint]);
        this.txtPanPos.setText(String.valueOf(cochleaCalibrateAngles[this.curCalibrationPoint]));
        btnSetPanPosActionPerformed(null);
    }

    private void btnCalibrateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCalibrateActionPerformed
        this.numCalibrationPoints = Integer.parseInt(txtNumCalibratePoints.getText());
        this.curCalibrationPoint = 0;
        cochleaCalibrateAngles = new int[this.numCalibrationPoints];
        cochleaCalibrateITDs = new float[this.numCalibrationPoints];
        int stepSize = (sldPanPos.getMaximum() - sldPanPos.getMinimum()) / this.numCalibrationPoints;
        cochleaCalibrateAngles[0] = sldPanPos.getMinimum();
        cochleaCalibrateAngles[this.numCalibrationPoints - 1] = sldPanPos.getMaximum();
        for (int i = 1; i < this.numCalibrationPoints - 1; i++) {
            cochleaCalibrateAngles[i] = cochleaCalibrateAngles[i - 1] + stepSize;
        }
        isCalibratingAuditoryMap = true;
        sendMessageToITDFilter(3, 0);
        startNextCalibration();
    }//GEN-LAST:event_btnCalibrateActionPerformed

    private void sendMessageToITDFilter(int command, float arg) {
        CommObjForITDFilter comObjForITDFilter = new CommObjForITDFilter();
        comObjForITDFilter.setPlayingITD(arg);
        comObjForITDFilter.setCommand(command);
        boolean success = panTilt.offerBlockingQForITDFilter(comObjForITDFilter);
        if (success == false) {
            JOptionPane.showMessageDialog(null, "No success when sending message to ITDFilter. Either ITDFilter not running or no cochlea spikes incomming?", "No Connection to ITDFilter", JOptionPane.OK_CANCEL_OPTION);
        }
    }

    private void btnCalibrateCochleaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCalibrateCochleaActionPerformed
        this.numCalibrationPoints = Integer.parseInt(txtNumCalibratePoints.getText());
        this.curCalibrationPoint = 0;
        isCalibratingCochleaChannels = true;
        startNextCalibration();
    }//GEN-LAST:event_btnCalibrateCochleaActionPerformed

    private void cbxOpenUDPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbxOpenUDPActionPerformed
        if (this.cbxOpenUDP.isSelected()) {
            if (datagramSocket == null) {
                try {
                    datagramSocket = new DatagramSocket();
                    log.info("opened port " + datagramSocket.getLocalPort());
                } catch (Exception ex) {
                    log.warning(ex.toString());
                }
            }
        }
        else {
            datagramSocket.close();
        }
    }//GEN-LAST:event_cbxOpenUDPActionPerformed

    private void btnSendCommandActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSendCommandActionPerformed
        try {
            CommObjForPanTilt test = new CommObjForPanTilt();
            test.setConfidence(9.2334f);
            test.setFromCochlea(true);
            test.setFromRetina(false);
            test.setPanOffset(-434.55f);
            test.setTiltOffset(6.983f);
            byte[] buf;
            buf = test.getBytes();
            InetAddress addr = InetAddress.getByName(txtUDPServer.getText());
            DatagramPacket packet = new DatagramPacket(buf, buf.length, addr, Integer.parseInt(txtUDPPort.getText()));
            datagramSocket.send(packet);
        } catch (IOException ex) {
            log.warning(ex.toString());
        }
    }//GEN-LAST:event_btnSendCommandActionPerformed

    private void btnSendRubiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSendRubiActionPerformed

    }//GEN-LAST:event_btnSendRubiActionPerformed

    private void btnConnectRUBIServerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectRUBIServerActionPerformed

        panTiltControl.setWaitPeriod(Integer.parseInt(txtWaitPeriod.getText()));
        if (panTiltControl.isConnected() == false) {
            try {
                panTiltControl.connect(txtRUBIServer.getText());
            } catch (Exception e) {
                //e.printStackTrace();
                log.warning("could not connect! Exeption: "+e);
                panTiltControl = null;
                return;
            }
        }

        panTiltControl.addPanTiltListener(new PanTiltListener() {

            public void panTiltAction(PanTiltEvent evt) {
                if (evt.getStatus() == 0) {
                    // Movement done!
                    //log.info("Movement Done!");
                    if (isCalibratingAuditoryMap == true) {
                        sendMessageToITDFilter(5, 0);
                        clip.setFramePosition(0);
                        clip.start();
                    }

                    if (isCalibratingCochleaChannels == true) {
                        sendMessageToITDFilter(1, cochleaCalibrateITDs[curCalibrationPoint]);
                        clip.setFramePosition(0);
                        clip.start();
                    }

                }
            }
        });
    }//GEN-LAST:event_btnConnectRUBIServerActionPerformed

    private void btnConnectDynamixelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectDynamixelActionPerformed
        //if (panTiltControl == null) {
            panTiltControl = new PanTiltControlDynamixel();
        //}
        panTiltControl.setWaitPeriod(Integer.parseInt(txtWaitPeriod.getText()));
        if (panTiltControl.isConnected() == false) {
            try {
                panTiltControl.connect((String) this.cbxComPort.getSelectedItem());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        panTiltControl.addPanTiltListener(new PanTiltListener() {

            public void panTiltAction(PanTiltEvent evt) {
                if (evt.getStatus() == 0) {
                    // Movement done!
                    //log.info("Not Moving!");
                    if (isCalibratingAuditoryMap == true) {
                        sendMessageToITDFilter(5, 0);
                        clip.setFramePosition(0);
                        clip.start();
                    }

                    if (isCalibratingCochleaChannels == true) {
                        sendMessageToITDFilter(1, cochleaCalibrateITDs[curCalibrationPoint]);
                        clip.setFramePosition(0);
                        clip.start();
                    }

                }
                if (evt.getStatus() == 1) {
                    log.info("Still Moving!");
                }
            }
        });
    }//GEN-LAST:event_btnConnectDynamixelActionPerformed

    private void btnIsMovingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIsMovingActionPerformed
        if (panTiltControl.getClass()==PanTiltControlDynamixel.class) {
            ((PanTiltControlDynamixel)panTiltControl).checkIfMoving((byte)1);
        }
    }//GEN-LAST:event_btnIsMovingActionPerformed

    private void txtPanOffsetThresholdActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtPanOffsetThresholdActionPerformed
        setPanPosThreshold(Double.parseDouble(txtPanOffsetThreshold.getText()));
    }//GEN-LAST:event_txtPanOffsetThresholdActionPerformed

    private void txtPanPosMinFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtPanPosMinFocusLost
        updateBoundaries();
    }//GEN-LAST:event_txtPanPosMinFocusLost

    private void txtPanPosMaxFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtPanPosMaxFocusLost
        updateBoundaries();
    }//GEN-LAST:event_txtPanPosMaxFocusLost

    private void txtTiltPosMaxFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtTiltPosMaxFocusLost
        updateBoundaries();
    }//GEN-LAST:event_txtTiltPosMaxFocusLost

    private void txtTiltPosMinFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtTiltPosMinFocusLost
        updateBoundaries();
    }//GEN-LAST:event_txtTiltPosMinFocusLost

    private void updateBoundaries() {
        minPan = Double.parseDouble(txtPanPosMin.getText());
        maxPan = Double.parseDouble(txtPanPosMax.getText());
        minTilt = Double.parseDouble(txtTiltPosMin.getText());
        maxTilt = Double.parseDouble(txtTiltPosMax.getText());
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
                new PanTiltFrame(new PanTilt()).setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel FilterCalibration;
    private javax.swing.JPanel FilterOutput;
    private javax.swing.JButton LoadWave;
    private javax.swing.JPanel PanTiltCommands;
    private javax.swing.JPanel PanTiltPosition;
    private javax.swing.JPanel PanTiltProperties;
    private javax.swing.JButton btnCalibrate;
    private javax.swing.JButton btnCalibrateCochlea;
    private javax.swing.JButton btnConnect;
    private javax.swing.JButton btnConnectDynamixel;
    private javax.swing.JButton btnConnectRUBIServer;
    private javax.swing.JButton btnExecuteCommand;
    private javax.swing.JButton btnHalt;
    private javax.swing.JButton btnIsMoving;
    private javax.swing.JButton btnLocateAudioSource;
    private javax.swing.JButton btnResetPan;
    private javax.swing.JButton btnResetTilt;
    private javax.swing.JButton btnSendCommand;
    private javax.swing.JButton btnSendRubi;
    private javax.swing.JButton btnSetPanPos;
    private javax.swing.JButton btnSetSpeed;
    private javax.swing.JButton btnSetTiltPos;
    private javax.swing.JComboBox cbxComPort;
    private javax.swing.JCheckBox cbxLearnCochlea;
    private javax.swing.JCheckBox cbxLearnRetina;
    private javax.swing.JCheckBox cbxLogResponse;
    private javax.swing.JCheckBox cbxOpenUDP;
    private javax.swing.JCheckBox cbxUseCochlea;
    private javax.swing.JCheckBox cbxUseRetina;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSlider sldPanPos;
    private javax.swing.JSlider sldTiltPos;
    private javax.swing.JTextField txtCochleaConfidence;
    private javax.swing.JTextField txtCochleaPanOffset;
    private javax.swing.JTextField txtCochleaPanScaling;
    private javax.swing.JTextField txtCochleaThreshold;
    private javax.swing.JTextField txtCochleaTiltOffset;
    private javax.swing.JTextField txtCochleaTiltScaling;
    private javax.swing.JTextField txtCommand;
    private javax.swing.JTextField txtNumCalibratePoints;
    private javax.swing.JTextField txtPanOffsetThreshold;
    private javax.swing.JTextField txtPanPos;
    private javax.swing.JTextField txtPanPosMax;
    private javax.swing.JTextField txtPanPosMin;
    private javax.swing.JTextField txtRUBIServer;
    private javax.swing.JTextField txtRetinaConfidence;
    private javax.swing.JTextField txtRetinaPanOffset;
    private javax.swing.JTextField txtRetinaPanScaling;
    private javax.swing.JTextField txtRetinaThreshold;
    private javax.swing.JTextField txtRetinaTiltOffset;
    private javax.swing.JTextField txtRetinaTiltScaling;
    private javax.swing.JTextField txtSpeed;
    private javax.swing.JTextField txtTiltPos;
    private javax.swing.JTextField txtTiltPosMax;
    private javax.swing.JTextField txtTiltPosMin;
    private javax.swing.JTextField txtUDPPort;
    private javax.swing.JTextField txtUDPServer;
    private javax.swing.JTextField txtWaitPeriod;
    // End of variables declaration//GEN-END:variables

    /**
     * @return the panPosThreshold
     */
    public double getPanPosThreshold() {
        return panPosThreshold;
    }

    /**
     * @param panPosThreshold the panPosThreshold to set
     */
    public void setPanPosThreshold(double panPosThreshold) {
        this.panPosThreshold = panPosThreshold;
    }

    /**
     * @return the panPos
     */
    public double getPanPos() {
        return panPos;
    }

    /**
     * @return the tiltPos
     */
    public double getTiltPos() {
        return tiltPos;
    }

    /**
     * @return the servoLimitTouched
     */
    public boolean isServoLimitTouched() {
        return servoLimitTouched;
    }

    /**
     * @param servoLimitTouched the servoLimitTouched to set
     */
    public void setServoLimitTouched(boolean servoLimitTouched) {
        this.servoLimitTouched = servoLimitTouched;
    }
}
