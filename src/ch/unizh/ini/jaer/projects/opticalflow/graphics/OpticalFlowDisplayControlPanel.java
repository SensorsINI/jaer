/*
 * OpticalFlowDisplayControlPanel.java
 *
 * Created on December 17, 2006, 8:30 AM
 * Modified on November 12, 2010, 10:10 AM: additional controls for MDC2D chip
 */

package ch.unizh.ini.jaer.projects.opticalflow.graphics;

import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MotionDataMDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.MotionChipInterface;
import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.OpticalFlowHardwareInterfaceFactory;
import java.awt.FlowLayout;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;

/**
 *
 * @author  tobi
 * 
 * changes by andstein :
 * <ul>
 * <li>introduced new panel that lets you choose between different hardware
 *     interfaces</li>
 * <li>fused redundant channel selection combo boxes into one selection</li>
 * <li>added the GUI elements for a second global motion vector</li>
 * </ul>
 */
public class OpticalFlowDisplayControlPanel extends javax.swing.JPanel {
    OpticalFlowDisplayMethod displayMethod=null;
    Chip2DMotion chip=null;
    MotionViewer viewer;
    int selectedChannel; // will be set by radio buttons
    static final Logger log=Logger.getLogger(OpticalFlowDisplayControlPanel.class.getName());
    

    
    /** Creates new form OpticalFlowDisplayControlPanel
     @param displayMethod the OpticalFlowDisplayMethod to control
     */
    public OpticalFlowDisplayControlPanel(OpticalFlowDisplayMethod displayMethod, Chip2DMotion chip,MotionViewer viewer) {
        this.displayMethod=displayMethod;
        this.chip=chip;
        this.viewer=viewer;
        initComponents();
        enableGlobalMotionCheckBox.setSelected(displayMethod.isGlobalDisplayEnabled());
        enableGlobalMotionCheckBox2.setSelected(displayMethod.isGlobalDisplay2Enabled());
        enableLocalMotionCheckBox.setSelected(displayMethod.isLocalDisplayEnabled());
        enablePhotoreceptorCheckBox.setSelected(displayMethod.isPhotoDisplayEnabled());
        enabledLocalMotionColorsCheckBox.setSelected(displayMethod.isLocalMotionColorsEnabled());
        
        // we have one combo group for the choice of the channel
        // displayed (and used for the motion calculations)
        this.buttonGroup1.add(channelRecep);
        this.buttonGroup1.add(channelLmc1);
        this.buttonGroup1.add(channelLmc2);
        
        if("MDC2D".equals(chip.CHIPNAME)){
            this.jComboBox1.setModel(new javax.swing.DefaultComboBoxModel(MDC2D.MOTIONMETHODLIST ));
            this.jComboBox1.setSelectedIndex(2);// select global Srinivasan as default...
        }
        
        // hardware is initialized because .hardwareChooseActionPerformed() is called
        
        // init the data used for motion calculation
        String item= (String) averagingCombo.getSelectedItem();
        MotionDataMDC2D.temporalAveragesNum= Integer.parseInt(item);

        // generate combo box for hardware interfaces via factory
        hardwareChoose.removeAllItems();
        OpticalFlowHardwareInterfaceFactory factory= (OpticalFlowHardwareInterfaceFactory) OpticalFlowHardwareInterfaceFactory.instance();
        for(int i=0; i<factory.getNumInterfacesAvailable(); i++)
        {
            try {
                hardwareChoose.addItem(factory.getInterface(i).getTypeName());
            } catch (HardwareInterfaceException e) {
                log.warning("could not get interface name : "+e);
                hardwareChoose.addItem("**ERROR**");
            }
        }
        setControlsVisible(true);
    }


    /**
     * updates the channel used for display & motion calculations
     * 
     * this method should be called whenever the GUI selection of the
     * channel (or the use of the on-chip ADC) change; it updates the hardware 
     * as well as the motion display
     */
    protected void updateChannel()
    {
        int mask=0,rawIndex=0;

        // generate the bit-mask for the selected channel
        if (channelRecep.isSelected())
            mask |= MotionDataMDC2D.PHOTO;
        if (channelLmc1.isSelected())
            mask |= MotionDataMDC2D.LMC1;
        if (channelLmc2.isSelected())
            mask |= MotionDataMDC2D.LMC2;

        // enable channel in hardware interface
        try {
            viewer.hardware.setChannel(mask, onChipADCSelector.isSelected());
        } catch( HardwareInterfaceException ex ) {
            log.warning("could not set channel to transmit : " + ex);
        }

        // make motion viewer display that selected channel
        rawIndex= viewer.hardware.getRawDataIndex(mask);
        displayMethod.setRawChannelDisplayed(rawIndex);
        MDC2D.setChannelForMotionAlgorithm(rawIndex);

//        System.err.println("set channel displayed/calculated to " + rawIndex); //DBG
    }
    
    // returns int 0-100 from float 0-1
    int intFrom(float f){
        return (int)(f*100);
    }
    
    // returns a float 0-1 range value from the event assuming it is from slider that has range 0-100
    float floatFrom(javax.swing.event.ChangeEvent e){
        float f=0.01f*((JSlider)e.getSource()).getValue();
        return f;
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();
        localPanel = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        localOffsetSlider = new javax.swing.JSlider();
        jPanel5 = new javax.swing.JPanel();
        localGainSlider = new javax.swing.JSlider();
        jPanel1 = new javax.swing.JPanel();
        vectorScaleSliider = new javax.swing.JSlider();
        jPanel2 = new javax.swing.JPanel();
        globalVectorScaleSlider = new javax.swing.JSlider();
        photoPanel = new javax.swing.JPanel();
        jPanel8 = new javax.swing.JPanel();
        photoOffsetSlider = new javax.swing.JSlider();
        jPanel9 = new javax.swing.JPanel();
        photoGainSlider = new javax.swing.JSlider();
        showHideToggleButton = new javax.swing.JToggleButton();
        displayPanel = new javax.swing.JPanel();
        enableGlobalMotionCheckBox = new javax.swing.JCheckBox();
        enableLocalMotionCheckBox = new javax.swing.JCheckBox();
        enabledLocalMotionColorsCheckBox = new javax.swing.JCheckBox();
        enablePhotoreceptorCheckBox = new javax.swing.JCheckBox();
        enableGlobalMotionCheckBox2 = new javax.swing.JCheckBox();
        jPanel3 = new javax.swing.JPanel();
        rawChannelControlPanel1 = new javax.swing.JPanel();
        channelRecep = new javax.swing.JRadioButton();
        channelLmc1 = new javax.swing.JRadioButton();
        channelLmc2 = new javax.swing.JRadioButton();
        onChipADCSelector = new javax.swing.JCheckBox();
        jPanel6 = new javax.swing.JPanel();
        jComboBox1 = new javax.swing.JComboBox();
        timeOfTravelControlPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        thresholdSlider = new javax.swing.JSlider();
        matchSlider = new javax.swing.JSlider();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        averagingLabel = new javax.swing.JLabel();
        averagingCombo = new javax.swing.JComboBox();
        hardwareInterfacePanel = new javax.swing.JPanel();
        hardwareChoose = new javax.swing.JComboBox();
        hardwareConfigurationPanelContainer = new javax.swing.JPanel();
        placeHolder = new javax.swing.JPanel();

        localPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Local motion"));

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("Offset"));

        localOffsetSlider.setMinimumSize(new java.awt.Dimension(36, 12));
        localOffsetSlider.setPreferredSize(new java.awt.Dimension(200, 15));
        localOffsetSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                localOffsetSliderStateChanged(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(localOffsetSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 133, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(77, 77, 77))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel4Layout.createSequentialGroup()
                .add(localOffsetSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder("Gain"));

        localGainSlider.setMinimumSize(new java.awt.Dimension(36, 12));
        localGainSlider.setPreferredSize(new java.awt.Dimension(200, 15));
        localGainSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                localGainSliderStateChanged(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel5Layout = new org.jdesktop.layout.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel5Layout.createSequentialGroup()
                .add(localGainSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 110, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel5Layout.createSequentialGroup()
                .add(localGainSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("local vector scale"));

        vectorScaleSliider.setMinimumSize(new java.awt.Dimension(36, 12));
        vectorScaleSliider.setPreferredSize(new java.awt.Dimension(200, 15));
        vectorScaleSliider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                vectorScaleSliiderStateChanged(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(vectorScaleSliider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 121, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(vectorScaleSliider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("global average scale"));

        globalVectorScaleSlider.setMinimumSize(new java.awt.Dimension(36, 12));
        globalVectorScaleSlider.setPreferredSize(new java.awt.Dimension(200, 15));
        globalVectorScaleSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                globalVectorScaleSliderStateChanged(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(globalVectorScaleSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 98, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(26, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(globalVectorScaleSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 15, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
        );

        org.jdesktop.layout.GroupLayout localPanelLayout = new org.jdesktop.layout.GroupLayout(localPanel);
        localPanel.setLayout(localPanelLayout);
        localPanelLayout.setHorizontalGroup(
            localPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(localPanelLayout.createSequentialGroup()
                .add(localPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(localPanelLayout.createSequentialGroup()
                        .add(jPanel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 157, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(localPanelLayout.createSequentialGroup()
                        .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        localPanelLayout.setVerticalGroup(
            localPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(localPanelLayout.createSequentialGroup()
                .add(localPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel5, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(localPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jPanel1, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        photoPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Photoreceptor"));

        jPanel8.setBorder(javax.swing.BorderFactory.createTitledBorder("Offset"));

        photoOffsetSlider.setMinimumSize(new java.awt.Dimension(36, 12));
        photoOffsetSlider.setPreferredSize(new java.awt.Dimension(200, 15));
        photoOffsetSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                photoOffsetSliderStateChanged(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel8Layout = new org.jdesktop.layout.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel8Layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(photoOffsetSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel8Layout.createSequentialGroup()
                .add(photoOffsetSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel9.setBorder(javax.swing.BorderFactory.createTitledBorder("Gain"));

        photoGainSlider.setMinimumSize(new java.awt.Dimension(36, 12));
        photoGainSlider.setPreferredSize(new java.awt.Dimension(200, 15));
        photoGainSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                photoGainSliderStateChanged(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel9Layout = new org.jdesktop.layout.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel9Layout.createSequentialGroup()
                .add(photoGainSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel9Layout.createSequentialGroup()
                .add(photoGainSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout photoPanelLayout = new org.jdesktop.layout.GroupLayout(photoPanel);
        photoPanel.setLayout(photoPanelLayout);
        photoPanelLayout.setHorizontalGroup(
            photoPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(photoPanelLayout.createSequentialGroup()
                .add(jPanel8, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel9, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        photoPanelLayout.setVerticalGroup(
            photoPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel8, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 45, Short.MAX_VALUE)
            .add(jPanel9, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 45, Short.MAX_VALUE)
        );

        showHideToggleButton.setText("Show rendering controls");
        showHideToggleButton.setToolTipText("Shows controls for display of motion data");
        showHideToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showHideToggleButtonActionPerformed(evt);
            }
        });

        displayPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Display Control"));

        enableGlobalMotionCheckBox.setText("global motion average (computer)");
        enableGlobalMotionCheckBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        enableGlobalMotionCheckBox.setMargin(new java.awt.Insets(0, 0, 0, 0));
        enableGlobalMotionCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableGlobalMotionCheckBoxActionPerformed(evt);
            }
        });

        enableLocalMotionCheckBox.setText("local motion vectors");
        enableLocalMotionCheckBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        enableLocalMotionCheckBox.setMargin(new java.awt.Insets(0, 0, 0, 0));
        enableLocalMotionCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableLocalMotionCheckBoxActionPerformed(evt);
            }
        });

        enabledLocalMotionColorsCheckBox.setText("local motion colors");
        enabledLocalMotionColorsCheckBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        enabledLocalMotionColorsCheckBox.setMargin(new java.awt.Insets(0, 0, 0, 0));
        enabledLocalMotionColorsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enabledLocalMotionColorsCheckBoxActionPerformed(evt);
            }
        });

        enablePhotoreceptorCheckBox.setText("raw channel");
        enablePhotoreceptorCheckBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        enablePhotoreceptorCheckBox.setMargin(new java.awt.Insets(0, 0, 0, 0));
        enablePhotoreceptorCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enablePhotoreceptorCheckBoxActionPerformed(evt);
            }
        });

        enableGlobalMotionCheckBox2.setText("global motion average (firmware)");
        enableGlobalMotionCheckBox2.setActionCommand("global motion average (firmware)");
        enableGlobalMotionCheckBox2.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        enableGlobalMotionCheckBox2.setMargin(new java.awt.Insets(0, 0, 0, 0));
        enableGlobalMotionCheckBox2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableGlobalMotionCheckBox2ActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout displayPanelLayout = new org.jdesktop.layout.GroupLayout(displayPanel);
        displayPanel.setLayout(displayPanelLayout);
        displayPanelLayout.setHorizontalGroup(
            displayPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(displayPanelLayout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(displayPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(enableGlobalMotionCheckBox)
                    .add(enableGlobalMotionCheckBox2)
                    .add(enablePhotoreceptorCheckBox)
                    .add(enabledLocalMotionColorsCheckBox)
                    .add(enableLocalMotionCheckBox)))
        );
        displayPanelLayout.setVerticalGroup(
            displayPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(displayPanelLayout.createSequentialGroup()
                .add(enableGlobalMotionCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(enableGlobalMotionCheckBox2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(enableLocalMotionCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(enabledLocalMotionColorsCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(enablePhotoreceptorCheckBox)
                .addContainerGap(37, Short.MAX_VALUE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("MDC2D Control"));

        rawChannelControlPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Select Channel (Device)"));

        channelRecep.setSelected(true);
        channelRecep.setText("recep");
        channelRecep.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                channelRecepActionPerformed(evt);
            }
        });

        channelLmc1.setText("lmc1");
        channelLmc1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                channelLmc1ActionPerformed(evt);
            }
        });

        channelLmc2.setText("lmc2");
        channelLmc2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                channelLmc2ActionPerformed(evt);
            }
        });

        onChipADCSelector.setText("use on-chip ADC");
        onChipADCSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onChipADCSelectorActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout rawChannelControlPanel1Layout = new org.jdesktop.layout.GroupLayout(rawChannelControlPanel1);
        rawChannelControlPanel1.setLayout(rawChannelControlPanel1Layout);
        rawChannelControlPanel1Layout.setHorizontalGroup(
            rawChannelControlPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(rawChannelControlPanel1Layout.createSequentialGroup()
                .add(19, 19, 19)
                .add(rawChannelControlPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(rawChannelControlPanel1Layout.createSequentialGroup()
                        .add(channelRecep)
                        .add(10, 10, 10)
                        .add(channelLmc1)
                        .add(18, 18, 18)
                        .add(channelLmc2))
                    .add(onChipADCSelector))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        rawChannelControlPanel1Layout.linkSize(new java.awt.Component[] {channelLmc1, channelLmc2, channelRecep}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        rawChannelControlPanel1Layout.setVerticalGroup(
            rawChannelControlPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(rawChannelControlPanel1Layout.createSequentialGroup()
                .add(onChipADCSelector)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(rawChannelControlPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(channelRecep)
                    .add(channelLmc1)
                    .add(channelLmc2)))
        );

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder("Motion Algorithm (Computer)"));

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        jComboBox1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox1ActionPerformed(evt);
            }
        });

        jLabel3.setText("thresh");

        jLabel4.setText("match");

        thresholdSlider.setMaximum(50);
        thresholdSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                thresholdSliderStateChanged(evt);
            }
        });

        matchSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                matchSliderStateChanged(evt);
            }
        });

        org.jdesktop.layout.GroupLayout timeOfTravelControlPanelLayout = new org.jdesktop.layout.GroupLayout(timeOfTravelControlPanel);
        timeOfTravelControlPanel.setLayout(timeOfTravelControlPanelLayout);
        timeOfTravelControlPanelLayout.setHorizontalGroup(
            timeOfTravelControlPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(timeOfTravelControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(timeOfTravelControlPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, timeOfTravelControlPanelLayout.createSequentialGroup()
                        .add(jLabel4)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 124, Short.MAX_VALUE)
                        .add(jLabel2))
                    .add(timeOfTravelControlPanelLayout.createSequentialGroup()
                        .add(jLabel3)
                        .add(timeOfTravelControlPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(timeOfTravelControlPanelLayout.createSequentialGroup()
                                .add(107, 107, 107)
                                .add(jLabel1))
                            .add(timeOfTravelControlPanelLayout.createSequentialGroup()
                                .add(2, 2, 2)
                                .add(matchSlider, 0, 0, Short.MAX_VALUE)
                                .add(0, 0, 0))
                            .add(timeOfTravelControlPanelLayout.createSequentialGroup()
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(thresholdSlider, 0, 0, Short.MAX_VALUE)))))
                .addContainerGap())
        );
        timeOfTravelControlPanelLayout.setVerticalGroup(
            timeOfTravelControlPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(timeOfTravelControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(timeOfTravelControlPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(thresholdSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(timeOfTravelControlPanelLayout.createSequentialGroup()
                        .add(timeOfTravelControlPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jLabel3)
                            .add(jLabel1))
                        .add(18, 18, 18)
                        .add(timeOfTravelControlPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jLabel2)
                            .add(timeOfTravelControlPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                                .add(matchSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .add(jLabel4)))))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        averagingLabel.setText("temporal averaging");

        averagingCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" }));
        averagingCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                averagingComboActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel6Layout = new org.jdesktop.layout.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel6Layout.createSequentialGroup()
                        .add(jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jComboBox1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(jPanel6Layout.createSequentialGroup()
                                .add(averagingLabel)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(averagingCombo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap(68, Short.MAX_VALUE))
                    .add(jPanel6Layout.createSequentialGroup()
                        .add(timeOfTravelControlPanel, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .add(29, 29, 29))))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel6Layout.createSequentialGroup()
                .add(jComboBox1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(averagingLabel)
                    .add(averagingCombo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(timeOfTravelControlPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, rawChannelControlPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(rawChannelControlPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        hardwareInterfacePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Hardware Interface Control"));

        hardwareChoose.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        hardwareChoose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hardwareChooseActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout placeHolderLayout = new org.jdesktop.layout.GroupLayout(placeHolder);
        placeHolder.setLayout(placeHolderLayout);
        placeHolderLayout.setHorizontalGroup(
            placeHolderLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 486, Short.MAX_VALUE)
        );
        placeHolderLayout.setVerticalGroup(
            placeHolderLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 91, Short.MAX_VALUE)
        );

        org.jdesktop.layout.GroupLayout hardwareConfigurationPanelContainerLayout = new org.jdesktop.layout.GroupLayout(hardwareConfigurationPanelContainer);
        hardwareConfigurationPanelContainer.setLayout(hardwareConfigurationPanelContainerLayout);
        hardwareConfigurationPanelContainerLayout.setHorizontalGroup(
            hardwareConfigurationPanelContainerLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(placeHolder, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        hardwareConfigurationPanelContainerLayout.setVerticalGroup(
            hardwareConfigurationPanelContainerLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(placeHolder, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        org.jdesktop.layout.GroupLayout hardwareInterfacePanelLayout = new org.jdesktop.layout.GroupLayout(hardwareInterfacePanel);
        hardwareInterfacePanel.setLayout(hardwareInterfacePanelLayout);
        hardwareInterfacePanelLayout.setHorizontalGroup(
            hardwareInterfacePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(hardwareInterfacePanelLayout.createSequentialGroup()
                .add(hardwareChoose, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(hardwareConfigurationPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        hardwareInterfacePanelLayout.setVerticalGroup(
            hardwareInterfacePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(hardwareInterfacePanelLayout.createSequentialGroup()
                .add(hardwareChoose, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(71, Short.MAX_VALUE))
            .add(hardwareConfigurationPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(hardwareInterfacePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(localPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(showHideToggleButton)
                            .add(displayPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(20, 20, 20))
                    .add(photoPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jPanel3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(137, 137, 137))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, localPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(showHideToggleButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                        .add(displayPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(photoPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(hardwareInterfacePanel, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap(82, Short.MAX_VALUE))
            .add(layout.createSequentialGroup()
                .add(jPanel3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(214, Short.MAX_VALUE))
        );

        getAccessibleContext().setAccessibleName("");
    }// </editor-fold>//GEN-END:initComponents

    private void globalVectorScaleSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_globalVectorScaleSliderStateChanged
        displayMethod.setGlobalMotionGain(floatFrom(evt));
    }//GEN-LAST:event_globalVectorScaleSliderStateChanged
    
    private void enabledLocalMotionColorsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enabledLocalMotionColorsCheckBoxActionPerformed
        displayMethod.setLocalMotionColorsEnabled(enabledLocalMotionColorsCheckBox.isSelected());
    }//GEN-LAST:event_enabledLocalMotionColorsCheckBoxActionPerformed
    
    private void vectorScaleSliiderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_vectorScaleSliiderStateChanged
        displayMethod.setVectorLengthScale(floatFrom(evt));
    }//GEN-LAST:event_vectorScaleSliiderStateChanged
    
    private void enablePhotoreceptorCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enablePhotoreceptorCheckBoxActionPerformed
        displayMethod.setPhotoDisplayEnabled(enablePhotoreceptorCheckBox.isSelected());
    }//GEN-LAST:event_enablePhotoreceptorCheckBoxActionPerformed
    
    private void enableLocalMotionCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableLocalMotionCheckBoxActionPerformed
        displayMethod.setLocalDisplayEnabled(enableLocalMotionCheckBox.isSelected());
    }//GEN-LAST:event_enableLocalMotionCheckBoxActionPerformed
    
    private void enableGlobalMotionCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableGlobalMotionCheckBoxActionPerformed
        displayMethod.setGlobalDisplayEnabled(enableGlobalMotionCheckBox.isSelected());
    }//GEN-LAST:event_enableGlobalMotionCheckBoxActionPerformed
    
    void setControlsVisible(boolean yes){
        if(yes) showHideToggleButton.setText("Hide"); else showHideToggleButton.setText("Show motion rendering/acquisition controls");
        localPanel.setVisible(yes);
        photoPanel.setVisible(yes);
        displayPanel.setVisible(yes);
        if("MDC2D".equals(chip.CHIPNAME)){
            this.jPanel3.setVisible(yes);//set MDC2D controls visible
        }
        invalidate();
    }
    
    private void showHideToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showHideToggleButtonActionPerformed
        setControlsVisible(showHideToggleButton.isSelected());
    }//GEN-LAST:event_showHideToggleButtonActionPerformed
    
    private void photoGainSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_photoGainSliderStateChanged
        displayMethod.setPhotoGain(floatFrom(evt));
    }//GEN-LAST:event_photoGainSliderStateChanged
    
    private void photoOffsetSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_photoOffsetSliderStateChanged
        displayMethod.setPhotoOffset(floatFrom(evt));
    }//GEN-LAST:event_photoOffsetSliderStateChanged
            
    private void localGainSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_localGainSliderStateChanged
        displayMethod.setLocalMotionGain(floatFrom(evt));
    }//GEN-LAST:event_localGainSliderStateChanged
    
    
    
    private void localOffsetSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_localOffsetSliderStateChanged
        displayMethod.setLocalMotionOffset(floatFrom(evt));
    }//GEN-LAST:event_localOffsetSliderStateChanged

    private void channelRecepActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_channelRecepActionPerformed

        updateChannel();

}//GEN-LAST:event_channelRecepActionPerformed

    private void channelLmc1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_channelLmc1ActionPerformed

        updateChannel();
        
}//GEN-LAST:event_channelLmc1ActionPerformed

    private void channelLmc2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_channelLmc2ActionPerformed

        updateChannel();

}//GEN-LAST:event_channelLmc2ActionPerformed

    private void jComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox1ActionPerformed
        // update GUI for the selected algorithm
        
        int index=jComboBox1.getSelectedIndex();
        MDC2D.setMotionMethod(index);

        if (index==4) // time of travel method
            timeOfTravelControlPanel.setVisible(true);
        else
            timeOfTravelControlPanel.setVisible(false);

    }//GEN-LAST:event_jComboBox1ActionPerformed

    private void thresholdSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_thresholdSliderStateChanged
        MotionDataMDC2D.thresh=(float)this.thresholdSlider.getValue();
        String val=java.lang.String.valueOf(this.thresholdSlider.getValue());
        this.jLabel1.setText(val);// TODO add your handling code here:
    }//GEN-LAST:event_thresholdSliderStateChanged

    private void matchSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_matchSliderStateChanged
        MotionDataMDC2D.match=(float)this.matchSlider.getValue();
        String val=java.lang.String.valueOf(this.matchSlider.getValue());
        this.jLabel2.setText(val);
    }//GEN-LAST:event_matchSliderStateChanged

    private void onChipADCSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onChipADCSelectorActionPerformed

        updateChannel();

    }//GEN-LAST:event_onChipADCSelectorActionPerformed

    private void hardwareChooseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hardwareChooseActionPerformed
        // do nothing on initialization
        if (hardwareChoose.getItemCount() <1)
            return;
        // by default, the first interface will be opened at startup

        // do nothing if same item was reselected
        if (hardwareChoose.getSelectedIndex() == viewer.hardwareInterfaceNum)
            return;

        // close current interface before opening the next one
        if (viewer.hardwareIsOpen())
            viewer.closeHardware();

//        System.err.println("chose " + hardwareChoose.getItemAt(hardwareChoose.getSelectedIndex()));
        
        // set the new hardware interface, open it
        viewer.hardwareInterfaceNum= hardwareChoose.getSelectedIndex();
        viewer.openHardware();
        
        // evtl this hardware interface has some controls of its own to
        // display
        hardwareConfigurationPanelContainer.removeAll();
        JPanel configPanel= viewer.hardware.getConfigPanel();
        if (configPanel != null) {
            hardwareConfigurationPanelContainer.setLayout(new FlowLayout());
            hardwareConfigurationPanelContainer.add(configPanel);
            hardwareConfigurationPanelContainer.setVisible(false);
            hardwareConfigurationPanelContainer.setVisible(true);
        }
        
        // the hardwareInterface has now its .chip set and therefore we can
        // update its internal variables concerning channel, on-chip ADC
        updateChannel();
        
        // exit from blocking exchange
        if (viewer.viewLoop != null)
            viewer.viewLoop.interrupt();
                
    }//GEN-LAST:event_hardwareChooseActionPerformed

    private void averagingComboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_averagingComboActionPerformed
        String item= (String) averagingCombo.getSelectedItem();
        MotionDataMDC2D.temporalAveragesNum= Integer.parseInt(item);
    }//GEN-LAST:event_averagingComboActionPerformed

    private void enableGlobalMotionCheckBox2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableGlobalMotionCheckBox2ActionPerformed
        displayMethod.setGlobalDisplay2Enabled(enableGlobalMotionCheckBox2.isSelected());
    }//GEN-LAST:event_enableGlobalMotionCheckBox2ActionPerformed
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox averagingCombo;
    private javax.swing.JLabel averagingLabel;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.JRadioButton channelLmc1;
    private javax.swing.JRadioButton channelLmc2;
    private javax.swing.JRadioButton channelRecep;
    private javax.swing.JPanel displayPanel;
    private javax.swing.JCheckBox enableGlobalMotionCheckBox;
    private javax.swing.JCheckBox enableGlobalMotionCheckBox2;
    private javax.swing.JCheckBox enableLocalMotionCheckBox;
    private javax.swing.JCheckBox enablePhotoreceptorCheckBox;
    private javax.swing.JCheckBox enabledLocalMotionColorsCheckBox;
    private javax.swing.JSlider globalVectorScaleSlider;
    private javax.swing.JComboBox hardwareChoose;
    private javax.swing.JPanel hardwareConfigurationPanelContainer;
    private javax.swing.JPanel hardwareInterfacePanel;
    private javax.swing.JComboBox jComboBox1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JSlider localGainSlider;
    private javax.swing.JSlider localOffsetSlider;
    private javax.swing.JPanel localPanel;
    private javax.swing.JSlider matchSlider;
    private javax.swing.JCheckBox onChipADCSelector;
    private javax.swing.JSlider photoGainSlider;
    private javax.swing.JSlider photoOffsetSlider;
    private javax.swing.JPanel photoPanel;
    private javax.swing.JPanel placeHolder;
    private javax.swing.JPanel rawChannelControlPanel1;
    private javax.swing.JToggleButton showHideToggleButton;
    private javax.swing.JSlider thresholdSlider;
    private javax.swing.JPanel timeOfTravelControlPanel;
    private javax.swing.JSlider vectorScaleSliider;
    // End of variables declaration//GEN-END:variables
    
}
