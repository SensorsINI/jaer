/*
 * OpticalFlowDisplayControlPanel.java
 *
 * Created on December 17, 2006, 8:30 AM
 * Modified on November 12, 2010, 10:10 AM: additional controls for MDC2D chip
 */

package ch.unizh.ini.jaer.projects.opticalflow.graphics;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.KeyStroke;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.jaer.hardware.pantilt.PanTiltParserPanel;
import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MotionDataMDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.OpticalFlowHardwareInterfaceFactory;
import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.dsPIC33F_COM_ConfigurationPanel;

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
public final class OpticalFlowDisplayControlPanel extends javax.swing.JPanel {
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
        enableAbsoluteCoordinates.setSelected(displayMethod.isAbsoluteCoordinates());
        enableAngularRate.setSelected(displayMethod.isAngularRateEnabled());
        
        OFICPContainer.setLayout(new FlowLayout());
        OFICPContainer.add( chip.integrator.getControlPanel() );
        OFICPContainer.setVisible(displayMethod.isAbsoluteCoordinates());
        
        servoPanel.setLayout(new FlowLayout());
        PanTiltParserPanel parserPanel= new PanTiltParserPanel();
        servoPanel.add(parserPanel);
        chip.integrator.setPanTiltParserPanel(parserPanel);
        //servoPanel.add(new PanTiltControlPanel());
        
        registerKeyBindings();
        

        enableComputerMotionCalculation.setSelected(MotionDataMDC2D.enabled);
        enableComputerMotionCalculationActionPerformed(null);
        
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
     * creates new window-wide keybindings by inserting elemnts into the
     * <code>WHEN_IN_FOCUSED_WINDOW</code> action map -- <b>beware</b> that
     * the execution of these actions is unpredictable if the same keybinding
     * is registered more than once
     * <br /><br />
     * 
     * in this class, the action keys <code>F5-F8</code> are mapped to often
     * used methods; feel free to changes this
     */
    protected void registerKeyBindings() {
        
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F5"), "toggleAbsoluteCoordinates");
        getActionMap().put("toggleAbsoluteCoordinates", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                enableAbsoluteCoordinates.setSelected(!enableAbsoluteCoordinates.isSelected());
                enableAbsoluteCoordinatesActionPerformed(e);
            }
        });
        keyLabel1.setText("F5 : toggle absolute Coordinates");

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F6"), "toggleStreaming");
        getActionMap().put("toggleStreaming", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JPanel p= viewer.hardware.getConfigPanel();
                if (p instanceof dsPIC33F_COM_ConfigurationPanel)
                    ((dsPIC33F_COM_ConfigurationPanel) p).toggleStreaming();
            }   
        });
        keyLabel2.setText("F6 : toggle streaming");
    }

    
    /*
     * synchronize controls with interface <code>viewer.hardware</code>
     * 
     * due to different hardware interfaces and continued "support" for older
     * interfaces not used anymore, this is somewhat of a hack...
    protected void updateHardwareControls() {
        if (viewer.hardware instanceof dsPIC33F_COM_OpticalFlowHardwareInterface) {
            dsPIC33F_COM_OpticalFlowHardwareInterface hwi= (dsPIC33F_COM_OpticalFlowHardwareInterface) viewer.hardware;

            switch( hwi.getChannel() ) {
                case MotionDataMDC2D.PHOTO:
                    channelRecep.setSelected(true);
                    break;
                case MotionDataMDC2D.LMC1:
                    channelLmc1.setSelected(true);
                    break;
                case MotionDataMDC2D.LMC2:
                    channelLmc2.setSelected(true);
                    break;
            }

            onChipADCSelector.setSelected(hwi.isOnChipADC());
        }
    }
     */
    
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
        photoPanel = new javax.swing.JPanel();
        jPanel8 = new javax.swing.JPanel();
        photoOffsetSlider = new javax.swing.JSlider();
        jPanel9 = new javax.swing.JPanel();
        photoGainSlider = new javax.swing.JSlider();
        showHideToggleButton = new javax.swing.JToggleButton();
        hardwareInterfacePanel = new javax.swing.JPanel();
        hardwareChoose = new javax.swing.JComboBox();
        hardwareConfigurationPanelContainer = new javax.swing.JPanel();
        placeHolder = new javax.swing.JPanel();
        OFICPContainer = new javax.swing.JPanel();
        keyBindingsPanel = new javax.swing.JPanel();
        keyLabel1 = new javax.swing.JLabel();
        keyLabel2 = new javax.swing.JLabel();
        keyLabel3 = new javax.swing.JLabel();
        keyLabel4 = new javax.swing.JLabel();
        servoPanel = new javax.swing.JPanel();
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
        enableComputerMotionCalculation = new javax.swing.JCheckBox();
        displayPanel = new javax.swing.JPanel();
        enableGlobalMotionCheckBox = new javax.swing.JCheckBox();
        enableLocalMotionCheckBox = new javax.swing.JCheckBox();
        enabledLocalMotionColorsCheckBox = new javax.swing.JCheckBox();
        enablePhotoreceptorCheckBox = new javax.swing.JCheckBox();
        enableGlobalMotionCheckBox2 = new javax.swing.JCheckBox();
        enableAbsoluteCoordinates = new javax.swing.JCheckBox();
        enableAngularRate = new javax.swing.JCheckBox();

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
                .addContainerGap(20, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(vectorScaleSliider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout localPanelLayout = new org.jdesktop.layout.GroupLayout(localPanel);
        localPanel.setLayout(localPanelLayout);
        localPanelLayout.setHorizontalGroup(
            localPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(localPanelLayout.createSequentialGroup()
                .add(jPanel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 157, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(18, Short.MAX_VALUE))
        );
        localPanelLayout.setVerticalGroup(
            localPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(localPanelLayout.createSequentialGroup()
                .add(localPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel5, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
            .add(jPanel8, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jPanel9, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        showHideToggleButton.setText("Show rendering controls");
        showHideToggleButton.setToolTipText("Shows controls for display of motion data");
        showHideToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showHideToggleButtonActionPerformed(evt);
            }
        });

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
            .add(0, 470, Short.MAX_VALUE)
        );
        placeHolderLayout.setVerticalGroup(
            placeHolderLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 0, Short.MAX_VALUE)
        );

        org.jdesktop.layout.GroupLayout hardwareConfigurationPanelContainerLayout = new org.jdesktop.layout.GroupLayout(hardwareConfigurationPanelContainer);
        hardwareConfigurationPanelContainer.setLayout(hardwareConfigurationPanelContainerLayout);
        hardwareConfigurationPanelContainerLayout.setHorizontalGroup(
            hardwareConfigurationPanelContainerLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(hardwareConfigurationPanelContainerLayout.createSequentialGroup()
                .add(placeHolder, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(356, Short.MAX_VALUE))
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
                .addContainerGap())
            .add(hardwareConfigurationPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        OFICPContainer.setBorder(javax.swing.BorderFactory.createTitledBorder("OFI settings"));

        org.jdesktop.layout.GroupLayout OFICPContainerLayout = new org.jdesktop.layout.GroupLayout(OFICPContainer);
        OFICPContainer.setLayout(OFICPContainerLayout);
        OFICPContainerLayout.setHorizontalGroup(
            OFICPContainerLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 179, Short.MAX_VALUE)
        );
        OFICPContainerLayout.setVerticalGroup(
            OFICPContainerLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 152, Short.MAX_VALUE)
        );

        keyBindingsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("key bindings"));

        keyLabel1.setText("F5 : not used");

        keyLabel2.setText("F6 : not used");

        keyLabel3.setText("F7 : not used");

        keyLabel4.setText("F8 : not used");

        org.jdesktop.layout.GroupLayout keyBindingsPanelLayout = new org.jdesktop.layout.GroupLayout(keyBindingsPanel);
        keyBindingsPanel.setLayout(keyBindingsPanelLayout);
        keyBindingsPanelLayout.setHorizontalGroup(
            keyBindingsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(keyBindingsPanelLayout.createSequentialGroup()
                .add(keyBindingsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(keyLabel1)
                    .add(keyLabel2)
                    .add(keyLabel3)
                    .add(keyLabel4))
                .addContainerGap(61, Short.MAX_VALUE))
        );
        keyBindingsPanelLayout.setVerticalGroup(
            keyBindingsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(keyBindingsPanelLayout.createSequentialGroup()
                .add(keyLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(keyLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(keyLabel3)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(keyLabel4))
        );

        servoPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("servo"));

        org.jdesktop.layout.GroupLayout servoPanelLayout = new org.jdesktop.layout.GroupLayout(servoPanel);
        servoPanel.setLayout(servoPanelLayout);
        servoPanelLayout.setHorizontalGroup(
            servoPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 224, Short.MAX_VALUE)
        );
        servoPanelLayout.setVerticalGroup(
            servoPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 0, Short.MAX_VALUE)
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
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .add(jLabel2))
                    .add(timeOfTravelControlPanelLayout.createSequentialGroup()
                        .add(jLabel3)
                        .add(timeOfTravelControlPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(timeOfTravelControlPanelLayout.createSequentialGroup()
                                .add(107, 107, 107)
                                .add(jLabel1))
                            .add(timeOfTravelControlPanelLayout.createSequentialGroup()
                                .add(2, 2, 2)
                                .add(matchSlider, 0, 0, Short.MAX_VALUE))
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

        enableComputerMotionCalculation.setText("enable");
        enableComputerMotionCalculation.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableComputerMotionCalculationActionPerformed(evt);
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
                            .add(jPanel6Layout.createSequentialGroup()
                                .add(jComboBox1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(enableComputerMotionCalculation))
                            .add(jPanel6Layout.createSequentialGroup()
                                .add(averagingLabel)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(averagingCombo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .add(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())
                    .add(jPanel6Layout.createSequentialGroup()
                        .add(timeOfTravelControlPanel, 0, 0, Short.MAX_VALUE)
                        .add(29, 29, 29))))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel6Layout.createSequentialGroup()
                .add(jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jComboBox1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(enableComputerMotionCalculation))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel6Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(averagingLabel)
                    .add(averagingCombo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(timeOfTravelControlPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

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
        enableGlobalMotionCheckBox2.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        enableGlobalMotionCheckBox2.setMargin(new java.awt.Insets(0, 0, 0, 0));
        enableGlobalMotionCheckBox2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableGlobalMotionCheckBox2ActionPerformed(evt);
            }
        });

        enableAbsoluteCoordinates.setText("absolute Coordinates");
        enableAbsoluteCoordinates.setToolTipText("\"scan mode\" : optical flow is integrated and view moves accordingly");
        enableAbsoluteCoordinates.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        enableAbsoluteCoordinates.setMargin(new java.awt.Insets(0, 0, 0, 0));
        enableAbsoluteCoordinates.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableAbsoluteCoordinatesActionPerformed(evt);
            }
        });

        enableAngularRate.setText("angular rate (SpatialPhidget)");
        enableAngularRate.setToolTipText("\"scan mode\" : optical flow is integrated and view moves accordingly");
        enableAngularRate.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        enableAngularRate.setMargin(new java.awt.Insets(0, 0, 0, 0));
        enableAngularRate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableAngularRateActionPerformed(evt);
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
                    .add(enableLocalMotionCheckBox)
                    .add(enableAbsoluteCoordinates)
                    .add(enableAngularRate)))
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
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(enableAbsoluteCoordinates)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(enableAngularRate)
                .addContainerGap())
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(showHideToggleButton)
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(photoPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(localPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                .add(120, 120, 120)
                                .add(displayPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(servoPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(hardwareInterfacePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(OFICPContainer, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, keyBindingsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(jPanel6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(showHideToggleButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(OFICPContainer, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(0, 0, Short.MAX_VALUE))
                    .add(layout.createSequentialGroup()
                        .add(jPanel6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(keyBindingsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(184, 184, 184))
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, servoPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, displayPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                                .add(localPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(photoPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(hardwareInterfacePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())))
        );

        getAccessibleContext().setAccessibleName("");
    }// </editor-fold>//GEN-END:initComponents
    
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
        hardwareInterfacePanel.setVisible(yes);
        servoPanel.setVisible(yes);
        keyBindingsPanel.setVisible(yes);
        OFICPContainer.setVisible(yes);
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

    private void enableAbsoluteCoordinatesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableAbsoluteCoordinatesActionPerformed
        displayMethod.setAbsoluteCoordinates(enableAbsoluteCoordinates.isSelected());
        chip.integrator.reset(); // reset viewport
        OFICPContainer.setVisible(enableAbsoluteCoordinates.isSelected());
    }//GEN-LAST:event_enableAbsoluteCoordinatesActionPerformed

    private void enableAngularRateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableAngularRateActionPerformed
        displayMethod.setAngularRateEnabled(enableAngularRate.isSelected());
    }//GEN-LAST:event_enableAngularRateActionPerformed

    private void enableComputerMotionCalculationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableComputerMotionCalculationActionPerformed
        MotionDataMDC2D.enabled = enableComputerMotionCalculation.isSelected();
        jComboBox1.setEnabled(MotionDataMDC2D.enabled);
        averagingCombo.setEnabled(MotionDataMDC2D.enabled);
        thresholdSlider.setEnabled(MotionDataMDC2D.enabled);
        matchSlider.setEnabled(MotionDataMDC2D.enabled);
    }//GEN-LAST:event_enableComputerMotionCalculationActionPerformed
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel OFICPContainer;
    private javax.swing.JComboBox averagingCombo;
    private javax.swing.JLabel averagingLabel;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.JPanel displayPanel;
    private javax.swing.JCheckBox enableAbsoluteCoordinates;
    private javax.swing.JCheckBox enableAngularRate;
    private javax.swing.JCheckBox enableComputerMotionCalculation;
    private javax.swing.JCheckBox enableGlobalMotionCheckBox;
    private javax.swing.JCheckBox enableGlobalMotionCheckBox2;
    private javax.swing.JCheckBox enableLocalMotionCheckBox;
    private javax.swing.JCheckBox enablePhotoreceptorCheckBox;
    private javax.swing.JCheckBox enabledLocalMotionColorsCheckBox;
    private javax.swing.JComboBox hardwareChoose;
    private javax.swing.JPanel hardwareConfigurationPanelContainer;
    private javax.swing.JPanel hardwareInterfacePanel;
    private javax.swing.JComboBox jComboBox1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JPanel keyBindingsPanel;
    private javax.swing.JLabel keyLabel1;
    private javax.swing.JLabel keyLabel2;
    private javax.swing.JLabel keyLabel3;
    private javax.swing.JLabel keyLabel4;
    private javax.swing.JSlider localGainSlider;
    private javax.swing.JSlider localOffsetSlider;
    private javax.swing.JPanel localPanel;
    private javax.swing.JSlider matchSlider;
    private javax.swing.JSlider photoGainSlider;
    private javax.swing.JSlider photoOffsetSlider;
    private javax.swing.JPanel photoPanel;
    private javax.swing.JPanel placeHolder;
    private javax.swing.JPanel servoPanel;
    private javax.swing.JToggleButton showHideToggleButton;
    private javax.swing.JSlider thresholdSlider;
    private javax.swing.JPanel timeOfTravelControlPanel;
    private javax.swing.JSlider vectorScaleSliider;
    // End of variables declaration//GEN-END:variables
    
}
