/*
 * CochleaAMS1bControlPanel.java
 *
 * Created on October 24, 2008, 5:39 PM
 */
package ch.unizh.ini.caviar.chip.cochlea;

import ch.unizh.ini.caviar.biasgen.BiasgenPanel;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.AbstractButton;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;

/**
 * The custom control panel for CochleaAMS1b which includes IPots, VPots, local IPots, scanner, and digital control.
 * @author  tobi
 */
public class CochleaAMS1bControlPanel extends javax.swing.JPanel {

    Preferences prefs = Preferences.userNodeForPackage(CochleaAMS1bControlPanel.class);
    Logger log = Logger.getLogger("CochleaAMS1bControlPanel");
    CochleaAMS1b chip;
    CochleaAMS1b.Biasgen biasgen;
    SpinnerNumberModel scannerChannelSpinnerModel = null, scannerPeriodSpinnerModel = null;

    /** Creates new form CochleaAMS1bControlPanel */
    public CochleaAMS1bControlPanel(CochleaAMS1b chip) {
        this.chip = chip;
        biasgen = (CochleaAMS1b.Biasgen) chip.getBiasgen();
        initComponents();
        Integer value = new Integer(0);
        Integer min = new Integer(0);
        Integer max = new Integer(biasgen.scanner.nstages - 1);
        Integer step = new Integer(1);
        scannerChannelSpinnerModel = new SpinnerNumberModel(value, min, max, step);
        scanSpinner.setModel(scannerChannelSpinnerModel);
        scanSlider.setMinimum(0);
        scanSlider.setMaximum(max);

        scannerPeriodSpinnerModel = new SpinnerNumberModel(biasgen.scanner.getPeriod(), biasgen.scanner.minPeriod, biasgen.scanner.maxPeriod, 1);
        periodSpinner.setModel(scannerPeriodSpinnerModel);

        biasgen.setPotArray(biasgen.ipots);
        onchipBiasgenPanel.add(new BiasgenPanel(biasgen, chip.getAeViewer().getBiasgenFrame())); // TODO fix panel contructor to not need parent

        bufferBiasSlider.setMaximum(biasgen.bufferIPot.max);
        bufferBiasSlider.setValue(biasgen.bufferIPot.getValue());

        biasgen.setPotArray(biasgen.vpots);
        offchipDACPanel.add(new BiasgenPanel(biasgen, chip.getAeViewer().getBiasgenFrame()));
        for (CochleaAMS1b.Biasgen.ConfigBit bit : biasgen.configBits) {
            JRadioButton but = new JRadioButton(bit.name + ": " + bit.tip);
            but.setToolTipText("Select to set bit, clear to clear bit");
            but.setSelected(bit.get()); // pref value
            bit.notifyObservers();
            configPanel.add(but);
            but.addActionListener(new ConfigBitAction(bit));
        }

        for (CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel c : biasgen.equalizer.channels) {
            gainSlidersPanel.add(new QSOSSlider(c));
            qualSlidersPanel.add(new QBPFSlider(c));
            lpfKilledPanel.add(new LPFKillBox(c));
            bpfKilledPanel.add(new BPFKillBox(c));
        }

        tabbedPane.setSelectedIndex(prefs.getInt("CochleaAMS1bControlPanel.selectedPaneIndex", 0));

    }
    final Dimension sliderDimPref = new Dimension(2, 200),  sliderDimMin = new Dimension(1, 35),  killDimPref = new Dimension(2, 10),  killDimMax = new Dimension(6, 15),  killDimMin = new Dimension(1, 4);
    final Insets zeroInsets = new Insets(0, 0, 0, 0);

    class QSOSSlider extends EqualizerSlider {

        QSOSSlider(CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel channel) {
            super(channel);
            setValue(channel.getQSOS());
        }
    }

    class QBPFSlider extends EqualizerSlider {

        QBPFSlider(CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel channel) {
            super(channel);
            setValue(channel.getQBPF());
        }
    }

    class BPFKillBox extends KillBox {

        BPFKillBox(CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel channel) {
            super(channel);
            setSelected(channel.isBpfkilled());
        }
    }

    class LPFKillBox extends KillBox {

        LPFKillBox(CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel channel) {
            super(channel);
            setSelected(channel.isLpfKilled());
        }
    }
//    boolean firstKillBoxTouched=false;
    boolean lastKillSelection = false; // remembers last kill box action so that drag can copy it

    class KillBox extends JToggleButton {

        CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel channel;

        KillBox(final CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel channel) {
            this.channel = channel;
            addChangeListener(channel);
            setMaximumSize(killDimMax);
            setMinimumSize(killDimMin);
            setPreferredSize(killDimPref);
            setSize(killDimPref);
            setIconTextGap(0);
            setMargin(zeroInsets);
            setBorderPainted(false);
//            setSelected(isSelected()); // to set bg color
            MouseListener[] a = getMouseListeners();
            for (MouseListener m : a) {
                removeMouseListener(m);
            }
            addMouseListener(new MouseListener() {

                public void mouseDragged(MouseEvent e) {
                }

                public void mouseMoved(MouseEvent e) {
                }

                public void mouseClicked(MouseEvent e) {
                }

                public void mousePressed(MouseEvent e) {
                    lastKillSelection = !isSelected();
                    setSelected(lastKillSelection);
                }

                public void mouseReleased(MouseEvent e) {
                }

                public void mouseEntered(MouseEvent e) {
                    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
                        setSelected(lastKillSelection);
                    }
                    channelLabel.setText(channel.toString());
                }

                public void mouseExited(MouseEvent e) {
                }
            });

        }

        @Override
        public void setSelected(boolean b) {
            super.setSelected(b);
            setBackground(b ? Color.RED : Color.GREEN);
//            repaint();
//            log.info(this.toString());
        }
    }

    class EqualizerSlider extends JSlider {

        CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel channel;

        EqualizerSlider(final CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel channel) {
            super();
            this.channel = channel;
            setOrientation(JSlider.VERTICAL);
            setMaximum(channel.max);
            setMinimum(0);
            setMinimumSize(sliderDimMin);
            setPreferredSize(sliderDimPref);
            setPaintLabels(false);
            setPaintTicks(false);
            setPaintTrack(false);
            for (MouseListener m : getMouseListeners()) {
                removeMouseListener(m);
            }
            addChangeListener(channel);
            addMouseListener(new MouseListener() {

                public void mouseDragged(MouseEvent e) {
                    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
                        int v = (int) (getMaximum() * (float) (getHeight() - e.getY()) / getHeight());
                        setValue(v);
                    }
                }

                public void mouseMoved(MouseEvent e) {
                    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
                        int v = (int) (getMaximum() * (float) (getHeight() - e.getY()) / getHeight());
                        setValue(v);
                    }
                }

                public void mouseClicked(MouseEvent e) {
                    int v = (int) (getMaximum() * (float) (getHeight() - e.getY()) / getHeight());
                    setValue(v);
                }

                public void mousePressed(MouseEvent e) {
                }

                public void mouseReleased(MouseEvent e) {
                }

                public void mouseEntered(MouseEvent e) {
                    channelLabel.setText(channel.toString());
                    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
                        int v = (int) (getMaximum() * (float) (getHeight() - e.getY()) / getHeight());
                        setValue(v);
                    }
                }

                public void mouseExited(MouseEvent e) {
                }
            });
        }
    }

    class ConfigBitAction implements ActionListener {

        CochleaAMS1b.Biasgen.ConfigBit bit;

        ConfigBitAction(CochleaAMS1b.Biasgen.ConfigBit bit) {
            this.bit = bit;
        }

        public void actionPerformed(ActionEvent e) {
            AbstractButton button = (AbstractButton) e.getSource();
            bit.set(button.isSelected());
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

        tabbedPane = new javax.swing.JTabbedPane();
        onchipBiasgenPanel = new javax.swing.JPanel();
        bufferBiasPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        bufferBiasSlider = new javax.swing.JSlider();
        offchipDACPanel = new javax.swing.JPanel();
        dacCmdPanel = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        dacCmdComboBox = new javax.swing.JComboBox();
        configPanel = new javax.swing.JPanel();
        scannerPanel = new javax.swing.JPanel();
        continuousScanningPanel = new javax.swing.JPanel();
        continuousScanningEnabledCheckBox = new javax.swing.JCheckBox();
        periodSpinner = new javax.swing.JSpinner();
        jLabel2 = new javax.swing.JLabel();
        singleChannelSelectionPanel = new javax.swing.JPanel();
        scanSlider = new javax.swing.JSlider();
        scanSpinner = new javax.swing.JSpinner();
        jPanel1 = new javax.swing.JPanel();
        equalizerPanel = new javax.swing.JPanel();
        equalizerSlidersPanel = new javax.swing.JPanel();
        gainSlidersPanel = new javax.swing.JPanel();
        qualSlidersPanel = new javax.swing.JPanel();
        lpfKilledPanel = new javax.swing.JPanel();
        bpfKilledPanel = new javax.swing.JPanel();
        channelLabel = new javax.swing.JLabel();

        setLayout(new java.awt.BorderLayout());

        tabbedPane.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tabbedPaneMouseClicked(evt);
            }
        });

        onchipBiasgenPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("On-chip IPot biases"));
        onchipBiasgenPanel.setLayout(new java.awt.BorderLayout());

        jLabel3.setText("Buffer bias");
        bufferBiasPanel.add(jLabel3);

        bufferBiasSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                bufferBiasSliderStateChanged(evt);
            }
        });
        bufferBiasPanel.add(bufferBiasSlider);

        onchipBiasgenPanel.add(bufferBiasPanel, java.awt.BorderLayout.PAGE_START);

        tabbedPane.addTab("on-chip biases", onchipBiasgenPanel);

        offchipDACPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Off-chip DAC biases"));
        offchipDACPanel.setLayout(new java.awt.BorderLayout());

        dacCmdPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Driect DAC command"));
        dacCmdPanel.setMaximumSize(new java.awt.Dimension(32767, 50));
        dacCmdPanel.setPreferredSize(new java.awt.Dimension(100, 50));

        jLabel4.setText("Sends a 48 bit input to the 2 daisy-chained DACs. Enter the 12 character hex value and hit enter. 0x");

        dacCmdComboBox.setEditable(true);
        dacCmdComboBox.setFont(new java.awt.Font("Courier New", 0, 11));
        dacCmdComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "000000 000000", "ffffff ffffff" }));
        dacCmdComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                dacCmdComboBoxItemStateChanged(evt);
            }
        });
        dacCmdComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dacCmdComboBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout dacCmdPanelLayout = new javax.swing.GroupLayout(dacCmdPanel);
        dacCmdPanel.setLayout(dacCmdPanelLayout);
        dacCmdPanelLayout.setHorizontalGroup(
            dacCmdPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dacCmdPanelLayout.createSequentialGroup()
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dacCmdComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(72, Short.MAX_VALUE))
        );
        dacCmdPanelLayout.setVerticalGroup(
            dacCmdPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dacCmdPanelLayout.createSequentialGroup()
                .addGroup(dacCmdPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(dacCmdComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        offchipDACPanel.add(dacCmdPanel, java.awt.BorderLayout.NORTH);

        tabbedPane.addTab("off-chip biases", offchipDACPanel);

        configPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Configuration"));
        configPanel.setLayout(new javax.swing.BoxLayout(configPanel, javax.swing.BoxLayout.PAGE_AXIS));
        tabbedPane.addTab("config", configPanel);

        scannerPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Scanner control"));
        scannerPanel.setLayout(new javax.swing.BoxLayout(scannerPanel, javax.swing.BoxLayout.PAGE_AXIS));

        continuousScanningPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Continuous scanning"));
        continuousScanningPanel.setMaximumSize(new java.awt.Dimension(32767, 300));
        continuousScanningPanel.setPreferredSize(new java.awt.Dimension(0, 150));

        continuousScanningEnabledCheckBox.setText("Enable continuous scanning");
        continuousScanningEnabledCheckBox.setToolTipText("Turns on scanner to clock continuously");
        continuousScanningEnabledCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                continuousScanningEnabledCheckBoxActionPerformed(evt);
                jCheckBox1ActionPerformed(evt);
            }
        });

        periodSpinner.setToolTipText("Sets the period as some multiple of a timer interrupt");
        periodSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                periodSpinnerStateChanged(evt);
            }
        });

        jLabel2.setText("Inter-pixel period - 255 gives about 64us period");

        javax.swing.GroupLayout continuousScanningPanelLayout = new javax.swing.GroupLayout(continuousScanningPanel);
        continuousScanningPanel.setLayout(continuousScanningPanelLayout);
        continuousScanningPanelLayout.setHorizontalGroup(
            continuousScanningPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(continuousScanningPanelLayout.createSequentialGroup()
                .addGroup(continuousScanningPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(continuousScanningPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(periodSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(continuousScanningEnabledCheckBox))
                .addContainerGap(392, Short.MAX_VALUE))
        );
        continuousScanningPanelLayout.setVerticalGroup(
            continuousScanningPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(continuousScanningPanelLayout.createSequentialGroup()
                .addComponent(continuousScanningEnabledCheckBox)
                .addGap(9, 9, 9)
                .addGroup(continuousScanningPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(periodSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(68, Short.MAX_VALUE))
        );

        scannerPanel.add(continuousScanningPanel);

        singleChannelSelectionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Single channel selection"));
        singleChannelSelectionPanel.setMaximumSize(new java.awt.Dimension(32767, 300));
        singleChannelSelectionPanel.setPreferredSize(new java.awt.Dimension(0, 150));
        singleChannelSelectionPanel.setLayout(new javax.swing.BoxLayout(singleChannelSelectionPanel, javax.swing.BoxLayout.LINE_AXIS));

        scanSlider.setMajorTickSpacing(10);
        scanSlider.setMaximum(127);
        scanSlider.setMinorTickSpacing(1);
        scanSlider.setPaintLabels(true);
        scanSlider.setPaintTicks(true);
        scanSlider.setPaintTrack(false);
        scanSlider.setToolTipText("Sets the scanned channel");
        scanSlider.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        scanSlider.setMaximumSize(new java.awt.Dimension(32767, 40));
        scanSlider.setPreferredSize(new java.awt.Dimension(32767, 47));
        scanSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                scanSliderStateChanged(evt);
            }
        });
        singleChannelSelectionPanel.add(scanSlider);

        scanSpinner.setToolTipText("Sets the scanned channel");
        scanSpinner.setMaximumSize(new java.awt.Dimension(32767, 40));
        scanSpinner.setPreferredSize(new java.awt.Dimension(200, 30));
        scanSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                scanSpinnerStateChanged(evt);
            }
        });
        singleChannelSelectionPanel.add(scanSpinner);

        scannerPanel.add(singleChannelSelectionPanel);

        jPanel1.setMinimumSize(new java.awt.Dimension(0, 0));
        jPanel1.setPreferredSize(new java.awt.Dimension(0, 0));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 700, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 128, Short.MAX_VALUE)
        );

        scannerPanel.add(jPanel1);

        tabbedPane.addTab("scanner", scannerPanel);

        equalizerPanel.setLayout(new java.awt.BorderLayout());

        equalizerSlidersPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        equalizerSlidersPanel.setLayout(new javax.swing.BoxLayout(equalizerSlidersPanel, javax.swing.BoxLayout.Y_AXIS));

        gainSlidersPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("SOS quality"));
        gainSlidersPanel.setToolTipText("Second order section feedback transconductance tweak, increase to increase Q");
        gainSlidersPanel.setAlignmentX(0.0F);
        gainSlidersPanel.setLayout(new java.awt.GridLayout(1, 0));
        equalizerSlidersPanel.add(gainSlidersPanel);

        qualSlidersPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("BPF quality"));
        qualSlidersPanel.setToolTipText("Bandpass filter quality, increase for more ringiness");
        qualSlidersPanel.setAlignmentX(0.0F);
        qualSlidersPanel.setLayout(new java.awt.GridLayout(1, 0));
        equalizerSlidersPanel.add(qualSlidersPanel);

        lpfKilledPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("LPF killed"));
        lpfKilledPanel.setToolTipText("Kills the lowpass filter neurons (Green=go, Red=killed)");
        lpfKilledPanel.setAlignmentX(0.0F);
        lpfKilledPanel.setMaximumSize(new java.awt.Dimension(32767, 40));
        lpfKilledPanel.setLayout(new java.awt.GridLayout(1, 0));
        equalizerSlidersPanel.add(lpfKilledPanel);

        bpfKilledPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("BPF killed"));
        bpfKilledPanel.setToolTipText("Kills the bandpass filter neurons");
        bpfKilledPanel.setAlignmentX(0.0F);
        bpfKilledPanel.setMaximumSize(new java.awt.Dimension(32767, 40));
        bpfKilledPanel.setLayout(new java.awt.GridLayout(1, 0));
        equalizerSlidersPanel.add(bpfKilledPanel);

        channelLabel.setFont(new java.awt.Font("Bitstream Vera Sans Mono", 0, 11));
        channelLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        channelLabel.setText("                                       ");
        equalizerSlidersPanel.add(channelLabel);

        equalizerPanel.add(equalizerSlidersPanel, java.awt.BorderLayout.CENTER);

        tabbedPane.addTab("equalizer", equalizerPanel);

        add(tabbedPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

private void continuousScanningEnabledCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_continuousScanningEnabledCheckBoxActionPerformed
    biasgen.scanner.setContinuousScanningEnabled(continuousScanningEnabledCheckBox.isSelected());
}//GEN-LAST:event_continuousScanningEnabledCheckBoxActionPerformed

private void jCheckBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBox1ActionPerformed
    biasgen.scanner.setContinuousScanningEnabled(continuousScanningEnabledCheckBox.isSelected());
}//GEN-LAST:event_jCheckBox1ActionPerformed

private void scanSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_scanSpinnerStateChanged
    int stage = scannerChannelSpinnerModel.getNumber().intValue();
    scanSlider.setValue(stage); // let slider generate event
    continuousScanningEnabledCheckBox.setSelected(false);
}//GEN-LAST:event_scanSpinnerStateChanged

private void scanSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_scanSliderStateChanged
    int stage = scanSlider.getValue();
    scanSpinner.setValue(stage);
    biasgen.scanner.setCurrentStage(stage);
    continuousScanningEnabledCheckBox.setSelected(false);
}//GEN-LAST:event_scanSliderStateChanged

private void tabbedPaneMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tabbedPaneMouseClicked
    prefs.putInt("CochleaAMS1bControlPanel.selectedPaneIndex", tabbedPane.getSelectedIndex());
}//GEN-LAST:event_tabbedPaneMouseClicked

private void bufferBiasSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_bufferBiasSliderStateChanged
    biasgen.bufferIPot.setValue(bufferBiasSlider.getValue());
}//GEN-LAST:event_bufferBiasSliderStateChanged

private void periodSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_periodSpinnerStateChanged
    biasgen.scanner.setPeriod(scannerPeriodSpinnerModel.getNumber().intValue());
}//GEN-LAST:event_periodSpinnerStateChanged

private void dacCmdComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dacCmdComboBoxActionPerformed
    log.info(evt.toString());
    try {
        String s=(String)dacCmdComboBox.getSelectedItem();
        s=s.replaceAll("\\s", "");
        long v = Long.parseLong(s, 16);
        byte[] b = new byte[6];
        for (int i = 5; i >= 0; i--) {
            b[i] = (byte) (0xff & v);
            v = v >>> 8;
        }
        System.out.print(String.format("sending 0x%s = ",s));
        for (byte bi : b) {
            System.out.print(String.format("%2h ", bi & 0xff));
        }
        System.out.println();
        biasgen.sendCmd(biasgen.CMD_VDAC, 0, b);
        boolean isNew=true;
        for(int i=1;i<dacCmdComboBox.getItemCount();i++){
            if(dacCmdComboBox.getItemAt(i).equals(s)){
                isNew=false;
                break;
            }
        }
        if(isNew) dacCmdComboBox.addItem(s);
    } catch (NumberFormatException e) {
        log.warning(e.toString());
        Toolkit.getDefaultToolkit().beep();
    } catch (HardwareInterfaceException he) {
        log.warning(he.toString());
    }catch(Exception ex){
        log.warning(ex.toString());
    }
}//GEN-LAST:event_dacCmdComboBoxActionPerformed

private void dacCmdComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_dacCmdComboBoxItemStateChanged

}//GEN-LAST:event_dacCmdComboBoxItemStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel bpfKilledPanel;
    private javax.swing.JPanel bufferBiasPanel;
    private javax.swing.JSlider bufferBiasSlider;
    private javax.swing.JLabel channelLabel;
    private javax.swing.JPanel configPanel;
    private javax.swing.JCheckBox continuousScanningEnabledCheckBox;
    private javax.swing.JPanel continuousScanningPanel;
    private javax.swing.JComboBox dacCmdComboBox;
    private javax.swing.JPanel dacCmdPanel;
    private javax.swing.JPanel equalizerPanel;
    private javax.swing.JPanel equalizerSlidersPanel;
    private javax.swing.JPanel gainSlidersPanel;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel lpfKilledPanel;
    private javax.swing.JPanel offchipDACPanel;
    private javax.swing.JPanel onchipBiasgenPanel;
    private javax.swing.JSpinner periodSpinner;
    private javax.swing.JPanel qualSlidersPanel;
    private javax.swing.JSlider scanSlider;
    private javax.swing.JSpinner scanSpinner;
    private javax.swing.JPanel scannerPanel;
    private javax.swing.JPanel singleChannelSelectionPanel;
    private javax.swing.JTabbedPane tabbedPane;
    // End of variables declaration//GEN-END:variables
}
