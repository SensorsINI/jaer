/*
 * CochleaAMS1bControlPanel.java
 *
 * Created on October 24, 2008, 5:39 PM
 */
package ch.unizh.ini.jaer.chip.cochlea;

import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1b.Biasgen.BufferIPot;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1b.Biasgen.ConfigBit;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1b.Biasgen.Equalizer;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1b.Biasgen.Scanner;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1b.Biasgen.TriStateableConfigBit;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.SpinnerNumberModel;
import net.sf.jaer.biasgen.BiasgenPanel;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * The custom control panel for CochleaAMS1b which includes IPots, VPots, local
 * IPots, scanner, and digital control.
 *
 * @author tobi
 */
public class CochleaAMS1bControlPanel extends javax.swing.JPanel implements Observer {

    Preferences prefs = Preferences.userNodeForPackage(CochleaAMS1bControlPanel.class);
    Logger log = Logger.getLogger("CochleaAMS1bControlPanel");
    CochleaAMS1b chip;
    CochleaAMS1b.Biasgen biasgen;
    SpinnerNumberModel scannerChannelSpinnerModel = null, scannerPeriodSpinnerModel = null;
    HashMap<Equalizer.EqualizerChannel, EqualizerControls> eqMap = new HashMap<Equalizer.EqualizerChannel, EqualizerControls>();
    HashMap<ConfigBit, AbstractButton> configBitMap = new HashMap<ConfigBit, AbstractButton>();
    HashMap<TriStateableConfigBit, TristateableConfigBitButtons> tristateableButtonsMap = new HashMap();

    class TristateableConfigBitButtons {

        JRadioButton zeroOneButton, hiZButton;

        TristateableConfigBitButtons(JRadioButton zb, JRadioButton hb) {
            zeroOneButton = zb;
            hiZButton = hb;
        }
    }

    private void setFileModified() {
        if (chip != null && chip.getAeViewer() != null && chip.getAeViewer().getBiasgenFrame() != null) {
            chip.getAeViewer().getBiasgenFrame().setFileModified(true);
        }
    }

    private class EqualizerControls {

        QSOSSlider qSOSSlider;
        QBPFSlider qBPFSlider;
        LPFKillBox lPFKillBox;
        BPFKillBox bPFKillBox;

        EqualizerControls(QSOSSlider qSOSSlider, QBPFSlider qBPFSlider, LPFKillBox lPFKillBox, BPFKillBox bPFKillBox) {
            this.qSOSSlider = qSOSSlider;
            this.qBPFSlider = qBPFSlider;
            this.lPFKillBox = lPFKillBox;
            this.bPFKillBox = bPFKillBox;
        }
    }

    /**
     * Creates new form CochleaAMS1bControlPanel
     * @param chip
     */
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
        continuousScanningEnabledCheckBox.setSelected(biasgen.scanner.isContinuousScanningEnabled());
        biasgen.scanner.addObserver(this);

        biasgen.setPotArray(biasgen.ipots);
        onchipBiasgenPanel.add(new BiasgenPanel(biasgen));

        bufferBiasSlider.setMaximum(biasgen.bufferIPot.max);
        bufferBiasSlider.setMinimum(0);
        bufferBiasSlider.setValue(biasgen.bufferIPot.getValue());
        bufferBiasTextField.setText(Integer.toString(biasgen.bufferIPot.getValue()));
        biasgen.bufferIPot.addObserver(this);

        biasgen.setPotArray(biasgen.vpots);
        offchipDACPanel.add(new BiasgenPanel(biasgen));
        for (CochleaAMS1b.Biasgen.ConfigBit bit : biasgen.configBits) {
            if (bit instanceof TriStateableConfigBit) {
                TriStateableConfigBit b2 = (TriStateableConfigBit) bit;
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                panel.setAlignmentX(Component.LEFT_ALIGNMENT);
                JLabel label = new JLabel(bit.name + ": " + bit.tip);
                JRadioButton but = new JRadioButton("Set/Clear");
                but.setToolTipText("Select to set bit, clear to clear bit");
                JRadioButton hiZButton = new JRadioButton("HiZ");
                hiZButton.setToolTipText("Select to set pin to hiZ state");
//                group.add(hiZButton);  // independent from other two buttons, can be hiz and 0 or 1
                if (b2.isHiZEnabled()) {
                    hiZButton.setSelected(true);
                }
                if (b2.get()) {
                    but.setSelected(true);
                } else {
                    but.setSelected(true);
                }
                panel.add(but);
                panel.add(hiZButton);
                panel.add(label);
                configPanel.add(panel);
                tristateableButtonsMap.put(b2, new TristateableConfigBitButtons(but, hiZButton));
                but.addActionListener(new ConfigBitAction(bit));
                hiZButton.addActionListener(new TristateableConfigBitAction(b2));
                bit.addObserver(this);
            } else {
                JRadioButton but = new JRadioButton(bit.name + ": " + bit.tip);
                but.setAlignmentX(Component.LEFT_ALIGNMENT);
                but.setToolTipText("Select to set bit, clear to clear bit");
                but.setSelected(bit.get()); // pref value
                configPanel.add(but);
                configBitMap.put(bit, but);
                but.addActionListener(new ConfigBitAction(bit));
                bit.addObserver(this);

            }
        }
        dacPoweronButton.setSelected(biasgen.isDACPowered());
        for (CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel c : biasgen.equalizer.channels) {
            EqualizerControls cont = new EqualizerControls(new QSOSSlider(c), new QBPFSlider(c), new LPFKillBox(c), new BPFKillBox(c));
            gainSlidersPanel.add(cont.qSOSSlider);
            qualSlidersPanel.add(cont.qBPFSlider);
            lpfKilledPanel.add(cont.lPFKillBox);
            bpfKilledPanel.add(cont.bPFKillBox);
            eqMap.put(c, cont);
            c.addObserver(this);
        }
        biasgen.equalizer.addObserver(this);
        int prevTab=prefs.getInt("CochleaAMS1bControlPanel.selectedPaneIndex", 0);
        if(prevTab>=tabbedPane.getTabCount()) prevTab=0;
        tabbedPane.setSelectedIndex(prevTab);
    }
    final Dimension sliderDimPref = new Dimension(2, 200), sliderDimMin = new Dimension(1, 35), killDimPref = new Dimension(2, 15), killDimMax = new Dimension(6, 15), killDimMin = new Dimension(1, 8);
    final Insets zeroInsets = new Insets(0, 0, 0, 0);

    /**
     * Handles updates to GUI controls from any source, including preference
     * changes
     * @param observable
     * @param object  */
    @Override
    synchronized public void update(Observable observable, Object object) {  // thread safe to ensure gui cannot retrigger this while it is sending something
//            log.info(observable + " sent " + object);
        try {
            if (observable instanceof TriStateableConfigBit) {
                TriStateableConfigBit b = (TriStateableConfigBit) observable;
                TristateableConfigBitButtons but = tristateableButtonsMap.get(b);
                if (b.isHiZEnabled()) {
                    but.hiZButton.setSelected(true);
                }
                but.zeroOneButton.setSelected(b.get());
//                log.info("set button for "+b+" to selected="+but.isSelected());
            } else if (observable instanceof ConfigBit) {
                ConfigBit b = (ConfigBit) observable;
                AbstractButton but = configBitMap.get(b);
                but.setSelected(b.get());
//                log.info("set button for "+b+" to selected="+but.isSelected());
            } else if (observable instanceof Scanner) {
                Scanner scanner = (Scanner) observable;
                continuousScanningEnabledCheckBox.setSelected(scanner.isContinuousScanningEnabled());
                scanSpinner.setValue(scanner.getCurrentStage());
                scanSlider.setValue(scanner.getCurrentStage());

            } else if (observable instanceof CochleaAMS1b.Biasgen.BufferIPot) {
                BufferIPot bufferIPot = (BufferIPot) observable;
                bufferBiasSlider.setValue(bufferIPot.getValue());
            } else if (observable instanceof Equalizer.EqualizerChannel) {
                // sends 0 byte message (no data phase for speed)
                Equalizer.EqualizerChannel c = (Equalizer.EqualizerChannel) observable;
                EqualizerControls cont = eqMap.get(c);
                cont.bPFKillBox.setSelected(c.isBpfkilled());
                cont.lPFKillBox.setSelected(c.isLpfKilled());
                cont.qBPFSlider.setValue(c.getQBPF());
                cont.qSOSSlider.setValue(c.getQSOS());
            } else {
                log.warning("unknown observable " + observable + " , not sending anything");
            }
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }

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
//    boolean lastKillSelection = false; // remembers last kill box action so that drag can copy it

    /**
     * The kill box that turn green when neuron channel is enabled and red if
     * disabled.
     *
     */
    class KillBox extends JButton {

        CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel channel;

        KillBox(final CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel channel) {
            this.channel = channel;
            addChangeListener(channel); // this channel gets called with a ChangeEvent when the button state is changed
            setMaximumSize(killDimMax);
            setMinimumSize(killDimMin);
            setPreferredSize(killDimPref);
            setSize(killDimPref);
            setIconTextGap(0);
            setMargin(zeroInsets);
            setBorderPainted(false);
            setToolTipText("green=enabled, red=disabled. left click/drag to disable, right click/drag to enable");
            setDoubleBuffered(false);
            setOpaque(true);
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
                    set(e);
                }

                public void mouseReleased(MouseEvent e) {
                }

                public void mouseEntered(MouseEvent e) {
                    set(e);
                }

                public void mouseExited(MouseEvent e) {
                }

                void set(MouseEvent e) {
                    channelLabel.setText(channel.toString());
                    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
                        setSelected(true);
                        setFileModified();
                    } else if ((e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) == MouseEvent.BUTTON3_DOWN_MASK) {
                        setSelected(false);
                        setFileModified();
                    }
                }
            });

        }

        @Override
        public void setSelected(boolean b) {
            super.setSelected(b);
            setBackground(b ? Color.red : Color.GREEN);
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
            setToolTipText("left click to drag a single slider, right click to adjust many sliders");
            for (MouseListener m : getMouseListeners()) {
                removeMouseListener(m);
            }
            addChangeListener(channel);
            addMouseListener(new MouseListener() {

                public void mouseClicked(MouseEvent e) {
                }

                public void mousePressed(MouseEvent e) {
                }

                public void mouseReleased(MouseEvent e) {
                }

                public void mouseEntered(MouseEvent e) {
                    channelLabel.setText(channel.toString());
                    if ((e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) == MouseEvent.BUTTON3_DOWN_MASK) {
                        int v = (int) (getMaximum() * (float) (getHeight() - e.getY()) / getHeight());
                        setValue(v);
                        setFileModified();
                    }
                }

                public void mouseExited(MouseEvent e) {
                }
            });
            addMouseMotionListener(new MouseMotionListener() {

                public void mouseDragged(MouseEvent e) {
                    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
                        int v = (int) (getMaximum() * (float) (getHeight() - e.getY()) / getHeight());
                        setValue(v);
                        setFileModified();
                    }
                }

                public void mouseMoved(MouseEvent e) {
                    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
                        int v = (int) (getMaximum() * (float) (getHeight() - e.getY()) / getHeight());
                        setValue(v);
                        setFileModified();
                    }
                }
            });
        }
    }

    class TristateableConfigBitAction implements ActionListener {

        CochleaAMS1b.Biasgen.TriStateableConfigBit bit;

        TristateableConfigBitAction(CochleaAMS1b.Biasgen.TriStateableConfigBit bit) {
            this.bit = bit;
        }

        public void actionPerformed(ActionEvent e) {
            AbstractButton button = (AbstractButton) e.getSource();
            bit.setHiZEnabled(button.isSelected()); // TODO fix here
            setFileModified();
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
            setFileModified();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        dacPowerButtonGroup = new javax.swing.ButtonGroup();
        tabbedPane = new javax.swing.JTabbedPane();
        onchipBiasgenPanel = new javax.swing.JPanel();
        bufferBiasPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        bufferBiasSlider = new javax.swing.JSlider();
        bufferBiasTextField = new javax.swing.JTextField();
        offchipDACPanel = new javax.swing.JPanel();
        dacCmdPanel = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        dacCmdComboBox = new javax.swing.JComboBox();
        jPanel3 = new javax.swing.JPanel();
        initDACButton = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        dacPoweredPanel = new javax.swing.JPanel();
        dacPoweronButton = new javax.swing.JRadioButton();
        dacPoweroffButton = new javax.swing.JRadioButton();
        configPanel = new javax.swing.JPanel();
        specialResetButton = new javax.swing.JButton();
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

        setName("CochleaAMS1bControlPanel"); // NOI18N
        addAncestorListener(new javax.swing.event.AncestorListener() {
            public void ancestorMoved(javax.swing.event.AncestorEvent evt) {
            }
            public void ancestorAdded(javax.swing.event.AncestorEvent evt) {
                formAncestorAdded(evt);
            }
            public void ancestorRemoved(javax.swing.event.AncestorEvent evt) {
            }
        });
        setLayout(new java.awt.BorderLayout());

        tabbedPane.setToolTipText("");
        tabbedPane.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tabbedPaneMouseClicked(evt);
            }
        });

        onchipBiasgenPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("On-chip IPot biases"));
        onchipBiasgenPanel.setToolTipText("Set on-chip IPot values");
        onchipBiasgenPanel.setLayout(new java.awt.BorderLayout());

        jLabel3.setText("Buffer bias");
        bufferBiasPanel.add(jLabel3);

        bufferBiasSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                bufferBiasSliderStateChanged(evt);
            }
        });
        bufferBiasPanel.add(bufferBiasSlider);

        bufferBiasTextField.setColumns(2);
        bufferBiasTextField.setEditable(false);
        bufferBiasTextField.setToolTipText("globally-shared bias buffer bias");
        bufferBiasPanel.add(bufferBiasTextField);

        onchipBiasgenPanel.add(bufferBiasPanel, java.awt.BorderLayout.PAGE_START);

        tabbedPane.addTab("on-chip biases", onchipBiasgenPanel);

        offchipDACPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Off-chip DAC biases"));
        offchipDACPanel.setToolTipText("Set off-chip DAC voltages");
        offchipDACPanel.setLayout(new java.awt.BorderLayout());

        dacCmdPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Driect DAC command"));
        dacCmdPanel.setMaximumSize(new java.awt.Dimension(32767, 75));
        dacCmdPanel.setPreferredSize(new java.awt.Dimension(100, 75));
        dacCmdPanel.setLayout(new javax.swing.BoxLayout(dacCmdPanel, javax.swing.BoxLayout.LINE_AXIS));

        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel4.setText("<html>Sends a 48 bit input to the 2 daisy-chained DACs. <p>Enter the 12 character hex value and hit enter.</html>");
        dacCmdPanel.add(jLabel4);

        dacCmdComboBox.setEditable(true);
        dacCmdComboBox.setFont(new java.awt.Font("Courier New", 0, 11)); // NOI18N
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
        dacCmdPanel.add(dacCmdComboBox);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 48, Short.MAX_VALUE)
        );

        dacCmdPanel.add(jPanel3);

        initDACButton.setText("initDAC()");
        initDACButton.setToolTipText("sends vendor request to initialize DAC to default firmware state");
        initDACButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                initDACButtonActionPerformed(evt);
            }
        });
        dacCmdPanel.add(initDACButton);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 48, Short.MAX_VALUE)
        );

        dacCmdPanel.add(jPanel2);

        offchipDACPanel.add(dacCmdPanel, java.awt.BorderLayout.SOUTH);

        dacPoweredPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Power DACs"));
        dacPoweredPanel.setToolTipText("Sets the DACs to be powered (on) or with high impedance outputs (off)");
        dacPoweredPanel.setMaximumSize(new java.awt.Dimension(32767, 100));
        dacPoweredPanel.setPreferredSize(new java.awt.Dimension(100, 50));
        dacPoweredPanel.setLayout(new javax.swing.BoxLayout(dacPoweredPanel, javax.swing.BoxLayout.LINE_AXIS));

        dacPowerButtonGroup.add(dacPoweronButton);
        dacPoweronButton.setText("Power on");
        dacPoweronButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dacPoweronButtonActionPerformed(evt);
            }
        });
        dacPoweredPanel.add(dacPoweronButton);

        dacPowerButtonGroup.add(dacPoweroffButton);
        dacPoweroffButton.setText("Power off");
        dacPoweroffButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dacPoweroffButtonActionPerformed(evt);
            }
        });
        dacPoweredPanel.add(dacPoweroffButton);

        offchipDACPanel.add(dacPoweredPanel, java.awt.BorderLayout.NORTH);

        tabbedPane.addTab("off-chip biases", offchipDACPanel);

        configPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Configuration"));
        configPanel.setToolTipText("Set special config bits");
        configPanel.setLayout(new javax.swing.BoxLayout(configPanel, javax.swing.BoxLayout.Y_AXIS));

        specialResetButton.setText("Do special AER reset");
        specialResetButton.setToolTipText("puts  AERKillBit low, toggles Vreset, then raises AEKillBit");
        specialResetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                specialResetButtonActionPerformed(evt);
            }
        });
        configPanel.add(specialResetButton);

        tabbedPane.addTab("config", configPanel);

        scannerPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Scanner control"));
        scannerPanel.setToolTipText("Control the analog scanner");
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
                .addContainerGap(409, Short.MAX_VALUE))
        );
        continuousScanningPanelLayout.setVerticalGroup(
            continuousScanningPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(continuousScanningPanelLayout.createSequentialGroup()
                .addComponent(continuousScanningEnabledCheckBox)
                .addGap(9, 9, 9)
                .addGroup(continuousScanningPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(periodSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(71, Short.MAX_VALUE))
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
            .addGap(0, 713, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 131, Short.MAX_VALUE)
        );

        scannerPanel.add(jPanel1);

        tabbedPane.addTab("scanner", scannerPanel);

        equalizerPanel.setToolTipText("Control the equalizer (QDAC and SOS local quality factor DACs)");
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
        lpfKilledPanel.setMaximumSize(new java.awt.Dimension(32767, 60));
        lpfKilledPanel.setLayout(new java.awt.GridLayout(1, 0));
        equalizerSlidersPanel.add(lpfKilledPanel);

        bpfKilledPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("BPF killed"));
        bpfKilledPanel.setToolTipText("Kills the bandpass filter neurons");
        bpfKilledPanel.setAlignmentX(0.0F);
        bpfKilledPanel.setMaximumSize(new java.awt.Dimension(32767, 60));
        bpfKilledPanel.setLayout(new java.awt.GridLayout(1, 0));
        equalizerSlidersPanel.add(bpfKilledPanel);

        channelLabel.setFont(new java.awt.Font("Bitstream Vera Sans Mono", 0, 11)); // NOI18N
        channelLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        channelLabel.setText("                                       ");
        equalizerSlidersPanel.add(channelLabel);

        equalizerPanel.add(equalizerSlidersPanel, java.awt.BorderLayout.CENTER);

        tabbedPane.addTab("equalizer", equalizerPanel);

        add(tabbedPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

private void continuousScanningEnabledCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_continuousScanningEnabledCheckBoxActionPerformed
    biasgen.scanner.setContinuousScanningEnabled(continuousScanningEnabledCheckBox.isSelected());
    setFileModified();
}//GEN-LAST:event_continuousScanningEnabledCheckBoxActionPerformed

private void jCheckBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBox1ActionPerformed
    biasgen.scanner.setContinuousScanningEnabled(continuousScanningEnabledCheckBox.isSelected());
}//GEN-LAST:event_jCheckBox1ActionPerformed

private void scanSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_scanSpinnerStateChanged
    int stage = scannerChannelSpinnerModel.getNumber().intValue();
    scanSlider.setValue(stage); // let slider generate event
    continuousScanningEnabledCheckBox.setSelected(false);
    setFileModified();
}//GEN-LAST:event_scanSpinnerStateChanged

private void scanSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_scanSliderStateChanged
    int stage = scanSlider.getValue();
    scanSpinner.setValue(stage);
    biasgen.scanner.setCurrentStage(stage);
    continuousScanningEnabledCheckBox.setSelected(false);
    setFileModified();
}//GEN-LAST:event_scanSliderStateChanged

private void tabbedPaneMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tabbedPaneMouseClicked
    prefs.putInt("CochleaAMS1bControlPanel.selectedPaneIndex", tabbedPane.getSelectedIndex());
}//GEN-LAST:event_tabbedPaneMouseClicked

private void bufferBiasSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_bufferBiasSliderStateChanged
    biasgen.bufferIPot.setValue(bufferBiasSlider.getValue());
    bufferBiasTextField.setText(Integer.toString(biasgen.bufferIPot.getValue()));
    setFileModified();

}//GEN-LAST:event_bufferBiasSliderStateChanged

private void periodSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_periodSpinnerStateChanged
    biasgen.scanner.setPeriod(scannerPeriodSpinnerModel.getNumber().intValue());
    setFileModified();
}//GEN-LAST:event_periodSpinnerStateChanged

private void dacCmdComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dacCmdComboBoxActionPerformed
//    log.info(evt.toString());
    try {
        String s = (String) dacCmdComboBox.getSelectedItem();
        s = s.replaceAll("\\s", "");
        long v = Long.parseLong(s, 16);
        byte[] b = new byte[6];
        for (int i = 5; i >= 0; i--) {
            b[i] = (byte) (0xff & v);
            v = v >>> 8;
        }
//        System.out.print(String.format("sending 0x%s = ",s));
//        for (byte bi : b) {
//            System.out.print(String.format("%2h ", bi & 0xff));
//        }
//        System.out.println();
        biasgen.sendCmd(biasgen.CMD_VDAC, 0, b);
        boolean isNew = true;
        for (int i = 1; i < dacCmdComboBox.getItemCount(); i++) {
            if (dacCmdComboBox.getItemAt(i).equals(s)) {
                isNew = false;
                break;
            }
        }
        if (isNew) {
            dacCmdComboBox.addItem(s);
        }
    } catch (NumberFormatException e) {
        log.warning(e.toString());
        Toolkit.getDefaultToolkit().beep();
    } catch (HardwareInterfaceException he) {
        log.warning(he.toString());
    } catch (Exception ex) {
        log.warning(ex.toString());
    }
}//GEN-LAST:event_dacCmdComboBoxActionPerformed

private void dacCmdComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_dacCmdComboBoxItemStateChanged
}//GEN-LAST:event_dacCmdComboBoxItemStateChanged

private void initDACButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_initDACButtonActionPerformed
    try {
        biasgen.initDAC();
    } catch (Exception e) {
        log.warning(e.toString());
    }
}//GEN-LAST:event_initDACButtonActionPerformed

private void dacPoweroffButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dacPoweroffButtonActionPerformed
    try {
        biasgen.setDACPowered(!dacPoweroffButton.isSelected()); // ! because this should set powered off if selected
    } catch (HardwareInterfaceException ex) {
        log.warning(ex.toString());
    }
}//GEN-LAST:event_dacPoweroffButtonActionPerformed

private void dacPoweronButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dacPoweronButtonActionPerformed
    try {
        biasgen.setDACPowered(dacPoweronButton.isSelected());
    } catch (HardwareInterfaceException ex) {
        log.warning(ex.toString());
    }
}//GEN-LAST:event_dacPoweronButtonActionPerformed

private void specialResetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specialResetButtonActionPerformed
    // does special cycle of toggling bits to reset communcation
    biasgen.resetAERComm();
}//GEN-LAST:event_specialResetButtonActionPerformed
    private boolean addedUndoListener = false;

private void formAncestorAdded(javax.swing.event.AncestorEvent evt) {//GEN-FIRST:event_formAncestorAdded
//    if (addedUndoListener) {
//        return;
//    }
//    addedUndoListener = true;
//    if (evt.getComponent() instanceof Container) {
//        Container anc = (Container) evt.getComponent();
//        while (anc != null && anc instanceof Container) {
//            if (anc instanceof UndoableEditListener) {
//                editSupport.addUndoableEditListener((UndoableEditListener) anc);
//                break;
//            }
//            anc = anc.getParent();
//        }
//    }
}//GEN-LAST:event_formAncestorAdded


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel bpfKilledPanel;
    private javax.swing.JPanel bufferBiasPanel;
    private javax.swing.JSlider bufferBiasSlider;
    private javax.swing.JTextField bufferBiasTextField;
    private javax.swing.JLabel channelLabel;
    private javax.swing.JPanel configPanel;
    private javax.swing.JCheckBox continuousScanningEnabledCheckBox;
    private javax.swing.JPanel continuousScanningPanel;
    private javax.swing.JComboBox dacCmdComboBox;
    private javax.swing.JPanel dacCmdPanel;
    private javax.swing.ButtonGroup dacPowerButtonGroup;
    private javax.swing.JPanel dacPoweredPanel;
    private javax.swing.JRadioButton dacPoweroffButton;
    private javax.swing.JRadioButton dacPoweronButton;
    private javax.swing.JPanel equalizerPanel;
    private javax.swing.JPanel equalizerSlidersPanel;
    private javax.swing.JPanel gainSlidersPanel;
    private javax.swing.JButton initDACButton;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel lpfKilledPanel;
    private javax.swing.JPanel offchipDACPanel;
    private javax.swing.JPanel onchipBiasgenPanel;
    private javax.swing.JSpinner periodSpinner;
    private javax.swing.JPanel qualSlidersPanel;
    private javax.swing.JSlider scanSlider;
    private javax.swing.JSpinner scanSpinner;
    private javax.swing.JPanel scannerPanel;
    private javax.swing.JPanel singleChannelSelectionPanel;
    private javax.swing.JButton specialResetButton;
    private javax.swing.JTabbedPane tabbedPane;
    // End of variables declaration//GEN-END:variables
//         UndoableEditSupport editSupport = new UndoableEditSupport();
//    StateEdit edit = null;
//
//    void startEdit(){
////        System.out.println("ipot start edit "+pot);
//        edit=new MyStateEdit(this, "configuration change");
//        oldPotValue=pot.getBitValue();
//    }
//    
//    void endEdit(){
//        if(oldPotValue==pot.getBitValue()){
////            System.out.println("no edit, because no change in "+pot);
//            return;
//        }
////        System.out.println("ipot endEdit "+pot);
//        if(edit!=null) edit.end();
////        System.out.println("ipot "+pot+" postEdit");
//        editSupport.postEdit(edit);
//    }
//    
//    String STATE_KEY="pot state";
//    
//    public void restoreState(Hashtable<?,?> hashtable) {
////        System.out.println("restore state");
//        if(hashtable==null) throw new RuntimeException("null hashtable");
//        if(hashtable.get(STATE_KEY)==null) {
////            System.err.println("pot "+pot+" not in hashtable "+hashtable+" with size="+hashtable.size());
////            Set s=hashtable.entrySet();
////            System.out.println("hashtable entries");
////            for(Iterator i=s.iterator();i.hasNext();){
////                Map.Entry me=(Map.Entry)i.next();
////                System.out.println(me);
////            }
//            return;
//        }
//        int v=(Integer)hashtable.get(STATE_KEY);
//        pot.setBitValue(v);
//    }
//    
//    public void storeState(Hashtable<Object, Object> hashtable) {
////        System.out.println(" storeState "+pot);
//        hashtable.put(STATE_KEY, new Integer(pot.getBitValue()));
//    }
//    
//    class MyStateEdit extends StateEdit{
//        public MyStateEdit(StateEditable o, String s){
//            super(o,s);
//        }
//        protected void removeRedundantState(){}; // override this to actually get a state stored!!
//    }
}
