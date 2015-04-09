/*
 * CochleaAMS1cControlPanel.java
 *
 * Created on October 24, 2008, 5:39 PM
 */
package ch.unizh.ini.jaer.chip.cochlea;

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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import net.sf.jaer.biasgen.BiasgenPanel;
import net.sf.jaer.biasgen.VDAC.VPotGUIControl;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.ParameterControlPanel;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1c.Biasgen.AbstractConfigValue;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1c.Biasgen.BufferIPot;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1c.Biasgen.Equalizer;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1c.Biasgen.Scanner;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1c.ConfigBit;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1c.ConfigInt;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1c.ConfigTristate;
import ch.unizh.ini.jaer.chip.util.externaladc.ADCHardwareInterfaceProxy;

/**
 * The custom control panel for CochleaAMS1c which includes IPots, VPots, local
 * IPots, scanner, and digital control.
 *
 * @author tobi
 */
public final class CochleaAMS1cControlPanel extends javax.swing.JPanel implements Observer {

    Preferences prefs = Preferences.userNodeForPackage(CochleaAMS1cControlPanel.class);
    Logger log = Logger.getLogger("CochleaAMS1cControlPanel");
    CochleaAMS1c chip;
    private CochleaAMS1c.Biasgen biasgen;
    SpinnerNumberModel scannerChannelSpinnerModel = null, scannerPeriodSpinnerModel = null;
    HashMap<Equalizer.EqualizerChannel, EqualizerControls> eqMap = new HashMap<Equalizer.EqualizerChannel, EqualizerControls>();
    HashMap<AbstractConfigValue, JComponent> configBitMap = new HashMap<AbstractConfigValue, JComponent>();
    HashMap<ConfigTristate, TristateableConfigBitButtons> tristateableButtonsMap = new HashMap();
    Scanner scanner = null;
    ADCHardwareInterfaceProxy adcProxy = null;

    /**
     * Creates new form CochleaAMS1cControlPanel
     */
    public CochleaAMS1cControlPanel(CochleaAMS1c chip) {
        this.chip = chip;
        biasgen = (CochleaAMS1c.Biasgen) chip.getBiasgen();
        scanner = biasgen.getScanner();
        adcProxy = biasgen.getAdcProxy();

        initComponents();
        // scanner
        Integer value = new Integer(0);
        Integer min = new Integer(0);
        Integer max = new Integer(getBiasgen().getScanner().nstages - 1);
        Integer step = new Integer(1);
        scannerChannelSpinnerModel = new SpinnerNumberModel(value, min, max, step);
        scanSpinner.setModel(scannerChannelSpinnerModel);
        scanSlider.setMinimum(0);
        scanSlider.setMaximum(max);

//        continuousScanningEnabledCheckBox.setSelected(biasgen.getScanner().isScanContinuouslyEnabled());
        biasgen.getScanner().addObserver(this);

        biasgen.setPotArray(biasgen.ipots);
        onchipBiasgenPanel.add(new BiasgenPanel(getBiasgen()));

        bufferBiasSlider.setMaximum(biasgen.bufferIPot.max);
        bufferBiasSlider.setMinimum(0);
        bufferBiasSlider.setValue(biasgen.bufferIPot.getValue());
        bufferBiasTextField.setText(Integer.toString(biasgen.bufferIPot.getValue()));
        biasgen.bufferIPot.addObserver(this);

        biasgen.setPotArray(biasgen.vpots);
        offchipDACPanel.add(new BiasgenPanel(getBiasgen()));
        for (CochleaAMS1c.Biasgen.AbstractConfigValue bit : biasgen.config) {
            if (bit instanceof CochleaAMS1c.ConfigTristate) {
                ConfigTristate b2 = (ConfigTristate) bit;
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                panel.setAlignmentX(Component.LEFT_ALIGNMENT);
                JLabel label = new JLabel(bit.getName() + ": " + bit.getDescription());
                JRadioButton but = new JRadioButton("Set/Clear");
                but.setToolTipText("<html>" + b2.toString() + "<br>Select to set bit, clear to clear bit");
                JRadioButton hiZButton = new JRadioButton("HiZ");
                hiZButton.setToolTipText("Select to set pin to hiZ state");
//                group.add(hiZButton);  // independent from other two buttons, can be hiz and 0 or 1
                if (b2.isHiZ()) {
                    hiZButton.setSelected(true);
                }
                if (b2.isSet()) {
                    but.setSelected(true);
                } else {
                    but.setSelected(true);
                }
                panel.add(but);
                panel.add(hiZButton);
                panel.add(label);
                configPanel.add(panel);
                tristateableButtonsMap.put(b2, new TristateableConfigBitButtons(but, hiZButton));
                but.addActionListener(new ConfigBitAction(b2));
                hiZButton.addActionListener(new ConfigTristateAction(b2));
                bit.addObserver(this);
            } else if (bit instanceof CochleaAMS1c.ConfigBit) {
                ConfigBit b2 = (ConfigBit) bit;
                JRadioButton but = new JRadioButton(bit.getName() + ": " + bit.getDescription());
                but.setAlignmentX(Component.LEFT_ALIGNMENT);
                but.setToolTipText("<html>" + b2.toString() + "<br>Select to set bit, clear to clear bit");
                but.setSelected(b2.isSet()); // pref value
                configPanel.add(but);
                configBitMap.put(bit, but);
                but.addActionListener(new ConfigBitAction(b2));
                bit.addObserver(this);

            } else if (bit instanceof CochleaAMS1c.ConfigInt) {
                ConfigInt in = (ConfigInt) bit;
                JPanel pan = new JPanel();
                pan.setAlignmentX(Component.LEFT_ALIGNMENT);
                pan.setLayout(new BoxLayout(pan, BoxLayout.X_AXIS));
                JLabel label = new JLabel(in.getName());
                label.setToolTipText("<html>" + in.toString() + "<br>" + in.getDescription() + "<br>Enter value or use mouse wheel or arrow keys to change value");
                pan.add(label);
                JTextField tf = new JTextField();
                tf.setText(Integer.toString(in.get()));
                tf.setPreferredSize(new Dimension(200, 20));
                tf.setMaximumSize(new Dimension(200, 30));
                pan.add(tf);
                configPanel.add(pan);
                configBitMap.put(bit, tf);
                tf.addActionListener(new ConfigIntAction(in));
                in.addObserver(this);
            }
        }
        dacPoweronButton.setSelected(biasgen.isDACPowered());
        for (CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel c : biasgen.equalizer.channels) {
            EqualizerControls cont = new EqualizerControls(new QSOSSlider(c), new QBPFSlider(c), new LPFKillBox(c), new BPFKillBox(c));
            gainSlidersPanel.add(cont.qSOSSlider);
            qualSlidersPanel.add(cont.qBPFSlider);
            lpfKilledPanel.add(cont.lPFKillBox);
            bpfKilledPanel.add(cont.bPFKillBox);
            eqMap.put(c, cont);
            c.addObserver(this);
        }
        biasgen.equalizer.addObserver(this);

        biasgen.getOnchipPreamp().addObserver(this);
        biasgen.getOffchipPreampLeft().addObserver(this);
        biasgen.getOffchipPreampRight().addObserver(this);

        switch (biasgen.getOnchipGain()) {
            case Higher:
                onchipHighB.setSelected(true);
                break;
            case Medium:
                onchipMedB.setSelected(true);
                break;
            case Low:
                onchipMedB.setSelected(true);
        }

        switch (biasgen.getOffchipLeftGain()) {
            case High:
                offchipLeftGainHighBut.setSelected(true);
                break;
            case Medium:
                offchipLeftGainMedBut.setSelected(true);
                break;
            case Low:
                offchipLeftGainLowBut.setSelected(true);
        }
        switch (biasgen.getOffchipRightGain()) {
            case High:
                offchipRightGainHighBut.setSelected(true);
                break;
            case Medium:
                offchipRightGainMedBut.setSelected(true);
                break;
            case Low:
                offchipRightGainLowBut.setSelected(true);
        }
        switch (biasgen.getArRatio()) {
            case Fast:
                arFastBut.setSelected(true);
                break;
            case Medium:
                arMedBut.setSelected(true);
                break;
            case Slow:
                arSlowBut.setSelected(true);
        }
        adcPanel.add(new ParameterControlPanel(biasgen.getAdcProxy()));
        offchipPanel.add(new VPotGUIControl(biasgen.getPreampAGCThresholdPot()));
        tabbedPane.setSelectedIndex(prefs.getInt("CochleaAMS1cControlPanel.selectedPaneIndex", 0));
        basmemBut.setSelected(biasgen.getScanner().isScanBasMemV());
        gangcellBut.setSelected(biasgen.getScanner().isScanGangCellVMem());
    }

    // sets the selected channel to be displayed in the DisplayMethod to guide user for Equalizer channel selection
    private void setSelectedChannel(int channel){
        if(chip!=null && chip.getCanvas()!=null && chip.getCanvas().getDisplayMethod()!=null && chip.getCanvas().getDisplayMethod() instanceof HasSelectedCochleaChannel){
            ((HasSelectedCochleaChannel)chip.getCanvas().getDisplayMethod()).setSelectedChannel(channel);
        }
    }
    
    /**
     * @return the biasgen
     */
    public CochleaAMS1c.Biasgen getBiasgen() {
        return biasgen;
    }

    public void setScanX(int scanX) {
        scanner.setScanX(scanX);
    }

    public void setScanContinuouslyEnabled(boolean scanContinuouslyEnabled) {
        scanner.setScanContinuouslyEnabled(scanContinuouslyEnabled);
    }

    public boolean isScanContinuouslyEnabled() {
        return scanner.isScanContinuouslyEnabled();
    }

    public int getScanX() {
        return scanner.getScanX();
    }

    public void setPeriod(int period) {
        scanner.setPeriod(period);
    }

    public int getPeriod() {
        return scanner.getPeriod();
    }

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
    final Dimension sliderDimPref = new Dimension(2, 200),
            sliderDimMin = new Dimension(1, 35),
            killDimPref = new Dimension(2, 25),
            killDimMax = new Dimension(6, 15),
            killDimMin = new Dimension(1, 15);
    final Insets zeroInsets = new Insets(0, 0, 0, 0);

    /**
     * Handles updates to GUI controls from any source, including preference
     * changes
     */
    @Override
    public void update(Observable observable, Object object) {  // thread safe to ensure gui cannot retrigger this while it is sending something
//            log.info(observable + " sent " + object);
        try {
            if (observable instanceof ConfigTristate) {
                ConfigTristate b = (ConfigTristate) observable;
                TristateableConfigBitButtons but = tristateableButtonsMap.get(b);
                if (b.isHiZ()) {
                    but.hiZButton.setSelected(true);
                }
                but.zeroOneButton.setSelected(b.isSet());
//                log.info("set button for "+b+" to selected="+but.isSelected());
            } else if (observable instanceof ConfigBit) {
                ConfigBit b = (ConfigBit) observable;
                AbstractButton but = (AbstractButton) configBitMap.get(b);
                but.setSelected(b.isSet());
//                log.info("set button for "+b+" to selected="+but.isSelected());
            } else if (observable instanceof ConfigInt) {
                ConfigInt b = (ConfigInt) observable;
                JTextField tf = (JTextField) configBitMap.get(b);
                tf.setText(Integer.toString(b.get()));
//                log.info("set button for "+b+" to selected="+but.isSelected());
            } else if (observable instanceof CochleaAMS1c.Biasgen.BufferIPot) {
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
            }else if (observable instanceof Equalizer) {
                // sends 0 byte message (no data phase for speed)
                Equalizer c = (Equalizer) observable;
                rubberBandCB.setSelected(c.isRubberBandsEnabled());
            } else if (observable instanceof CochleaAMS1c.Biasgen.OnChipPreamp) {
                switch (biasgen.getOnchipGain()) {
                    case Higher:
                        onchipHighB.setSelected(true);
                        break;
                    case Medium:
                        onchipMedB.setSelected(true);
                        break;
                    case Low:
                        onchipLowB.setSelected(true);
                }

            } else if (observable instanceof CochleaAMS1c.Biasgen.OffChipPreamp) {
                if (observable == biasgen.getOffchipPreampLeft()) {
                    switch (biasgen.getOffchipLeftGain()) {
                        case High:
                            offchipLeftGainHighBut.setSelected(true);
                            break;
                        case Medium:
                            offchipLeftGainMedBut.setSelected(true);
                            break;
                        case Low:
                            offchipLeftGainLowBut.setSelected(true);
                    }
                } else {
                    switch (biasgen.getOffchipRightGain()) {
                        case High:
                            offchipRightGainHighBut.setSelected(true);
                            break;
                        case Medium:
                            offchipRightGainMedBut.setSelected(true);
                            break;
                        case Low:
                            offchipRightGainLowBut.setSelected(true);
                    }
                }

            } else if (observable instanceof CochleaAMS1c.Biasgen.OffChipPreampARRatio) {
                switch (biasgen.getArRatio()) {
                    case Fast:
                        arFastBut.setSelected(true);
                        break;
                    case Medium:
                        arMedBut.setSelected(true);
                        break;
                    case Slow:
                        arSlowBut.setSelected(true);
                }

            } else if (observable instanceof CochleaAMS1c.Biasgen.Scanner) {
                if (isScanContinuouslyEnabled()) {
                    continuousScanningEnabledCheckBox.setSelected(isScanContinuouslyEnabled());
                } else {
                    scanSpinner.setValue(getScanX());
                }

            } else {
                log.warning("unknown observable " + observable + " , not sending anything");
            }
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }

    class QSOSSlider extends EqualizerSlider {

        QSOSSlider(CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel channel) {
            super(channel);
            setValue(channel.getQSOS());
        }
    }

    class QBPFSlider extends EqualizerSlider {

        QBPFSlider(CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel channel) {
            super(channel);
            setValue(channel.getQBPF());
        }
    }

    class BPFKillBox extends KillBox {

        BPFKillBox(CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel channel) {
            super(channel);
            setSelected(channel.isBpfkilled());
        }
    }

    class LPFKillBox extends KillBox {

        LPFKillBox(CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel channel) {
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

        CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel channel;

        KillBox(final CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel channel) {
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

                @Override
                public void mouseClicked(MouseEvent e) {
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    set(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    set(e);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setSelectedChannel(-1);
                }

                void set(MouseEvent e) {
                    setSelectedChannel(channel.channel);
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

        CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel channel;

        EqualizerSlider(final CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel channel) {
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

                @Override
                public void mouseClicked(MouseEvent e) {
                }

                @Override
                public void mousePressed(MouseEvent e) {
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setSelectedChannel(channel.channel);
                    channelLabel.setText(channel.toString());
                    if ((e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) == MouseEvent.BUTTON3_DOWN_MASK) {
                        int v = (int) (getMaximum() * (float) (getHeight() - e.getY()) / getHeight());
                        setValue(v);
                        setFileModified();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setSelectedChannel(-1);
                }
            });
            addMouseMotionListener(new MouseMotionListener() {

                @Override
                public void mouseDragged(MouseEvent e) {
//                    System.out.println("dragged ");
                    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
                        int v = (int) (getMaximum() * (float) (getHeight() - e.getY()) / getHeight());
                        setValue(v);
                        setFileModified();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
//                    System.out.println("moved ");
                    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
                        int v = (int) (getMaximum() * (float) (getHeight() - e.getY()) / getHeight());
                        setValue(v);
                        setFileModified();
                    }
                }
            });
        }
    }

    class ConfigTristateAction implements ActionListener {

        CochleaAMS1c.ConfigTristate bit;

        ConfigTristateAction(CochleaAMS1c.ConfigTristate bit) {
            this.bit = bit;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            AbstractButton button = (AbstractButton) e.getSource();
            bit.setHiZ(button.isSelected()); // TODO fix here
            setFileModified();
        }
    }

    class ConfigBitAction implements ActionListener {

        CochleaAMS1c.ConfigBit bit;

        ConfigBitAction(CochleaAMS1c.ConfigBit bit) {
            this.bit = bit;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            AbstractButton button = (AbstractButton) e.getSource();
            bit.set(button.isSelected());
            setFileModified();
        }
    }

    class ConfigIntAction implements ActionListener {

        CochleaAMS1c.ConfigInt integerConfig;

        ConfigIntAction(CochleaAMS1c.ConfigInt bit) {
            this.integerConfig = bit;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextField tf = null;
            try {
                tf = (JTextField) e.getSource();
                int val = Integer.parseInt(tf.getText());
                integerConfig.set(val);
                setFileModified();
                tf.setBackground(Color.white);
            } catch (Exception ex) {
                tf.selectAll();
                tf.setBackground(Color.red);
                log.warning(ex.toString());
            }
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
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        dacPowerButtonGroup = new javax.swing.ButtonGroup();
        buttonGroup1 = new javax.swing.ButtonGroup();
        onchipGainGroup = new javax.swing.ButtonGroup();
        offchipGainLeftGroup = new javax.swing.ButtonGroup();
        offchipARGroup = new javax.swing.ButtonGroup();
        offchipGainRightGroup = new javax.swing.ButtonGroup();
        scanSelGroup = new javax.swing.ButtonGroup();
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
        equalizerPanel = new javax.swing.JPanel();
        equalizerSlidersPanel = new javax.swing.JPanel();
        channelLabel = new javax.swing.JLabel();
        jPanel10 = new javax.swing.JPanel();
        resetEqBut = new javax.swing.JButton();
        rubberBandCB = new javax.swing.JCheckBox();
        sosQualPan = new javax.swing.JPanel();
        jPanel9 = new javax.swing.JPanel();
        allmaxsosqualbut = new javax.swing.JButton();
        allmidsosqualbut = new javax.swing.JButton();
        allminsosqualbut = new javax.swing.JButton();
        gainSlidersPanel = new javax.swing.JPanel();
        bpfQualPanel = new javax.swing.JPanel();
        jPanel8 = new javax.swing.JPanel();
        allmaxbpfqualbut = new javax.swing.JButton();
        allmidbpfqualbut = new javax.swing.JButton();
        allminbpfqualbut = new javax.swing.JButton();
        qualSlidersPanel = new javax.swing.JPanel();
        lpfkilpan = new javax.swing.JPanel();
        jPanel6 = new javax.swing.JPanel();
        killalllpfbut = new javax.swing.JButton();
        unkillalllpfbut = new javax.swing.JButton();
        lpfKilledPanel = new javax.swing.JPanel();
        bpfkillpan = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        killallbpfbut = new javax.swing.JButton();
        unkillallbpfbut = new javax.swing.JButton();
        bpfKilledPanel = new javax.swing.JPanel();
        scannerPanel = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        continuousScanningPanel = new javax.swing.JPanel();
        continuousScanningEnabledCheckBox = new javax.swing.JCheckBox();
        jPanel5 = new javax.swing.JPanel();
        basmemBut = new javax.swing.JRadioButton();
        gangcellBut = new javax.swing.JRadioButton();
        singleChannelSelectionPanel = new javax.swing.JPanel();
        scanSlider = new javax.swing.JSlider();
        scanSpinner = new javax.swing.JSpinner();
        jPanel1 = new javax.swing.JPanel();
        adcPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        preampPanel = new javax.swing.JPanel();
        onchipPanel = new javax.swing.JPanel();
        onchipGainPanel = new javax.swing.JPanel();
        onchipLowB = new javax.swing.JRadioButton();
        onchipMedB = new javax.swing.JRadioButton();
        onchipHighB = new javax.swing.JRadioButton();
        onchipHighestB = new javax.swing.JRadioButton();
        offchipPanel = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        offchipGainPanelLeft = new javax.swing.JPanel();
        offchipLeftGainLowBut = new javax.swing.JRadioButton();
        offchipLeftGainMedBut = new javax.swing.JRadioButton();
        offchipLeftGainHighBut = new javax.swing.JRadioButton();
        offchipARPanel = new javax.swing.JPanel();
        arFastBut = new javax.swing.JRadioButton();
        arMedBut = new javax.swing.JRadioButton();
        arSlowBut = new javax.swing.JRadioButton();
        offchipGainPanelRight = new javax.swing.JPanel();
        offchipRightGainLowBut = new javax.swing.JRadioButton();
        offchipRightGainMedBut = new javax.swing.JRadioButton();
        offchipRightGainHighBut = new javax.swing.JRadioButton();
        configPanel = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();

        setName("CochleaAMS1cControlPanel"); // NOI18N
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

        tabbedPane.setToolTipText("Select tab for aspect of configuration");
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
            .addGap(0, 52, Short.MAX_VALUE)
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
            .addGap(0, 52, Short.MAX_VALUE)
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

        equalizerPanel.setToolTipText("Control the equalizer (QDAC and SOS local quality factor DACs)");
        equalizerPanel.setLayout(new java.awt.BorderLayout());

        equalizerSlidersPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        equalizerSlidersPanel.setLayout(new javax.swing.BoxLayout(equalizerSlidersPanel, javax.swing.BoxLayout.Y_AXIS));

        channelLabel.setFont(new java.awt.Font("Bitstream Vera Sans Mono", 0, 11)); // NOI18N
        channelLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        channelLabel.setText("                                       ");
        equalizerSlidersPanel.add(channelLabel);

        jPanel10.setLayout(new javax.swing.BoxLayout(jPanel10, javax.swing.BoxLayout.LINE_AXIS));

        resetEqBut.setText("Reset all");
        resetEqBut.setToolTipText("Uses hardware reset to reset all latches to default state");
        resetEqBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetEqButActionPerformed(evt);
            }
        });
        jPanel10.add(resetEqBut);

        rubberBandCB.setText("rubber bands");
        rubberBandCB.setToolTipText("ties channels together with rubber band");
        jPanel10.add(rubberBandCB);

        equalizerSlidersPanel.add(jPanel10);

        sosQualPan.setBorder(javax.swing.BorderFactory.createTitledBorder("SOS quality"));
        sosQualPan.setToolTipText("");
        sosQualPan.setLayout(new javax.swing.BoxLayout(sosQualPan, javax.swing.BoxLayout.LINE_AXIS));

        jPanel9.setMinimumSize(new java.awt.Dimension(40, 63));
        jPanel9.setPreferredSize(new java.awt.Dimension(40, 63));
        jPanel9.setLayout(new javax.swing.BoxLayout(jPanel9, javax.swing.BoxLayout.Y_AXIS));

        allmaxsosqualbut.setText("max");
        allmaxsosqualbut.setToolTipText("Set sliders to maximum value");
        allmaxsosqualbut.setMargin(new java.awt.Insets(1, 1, 1, 1));
        allmaxsosqualbut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allmaxsosqualbutActionPerformed(evt);
            }
        });
        jPanel9.add(allmaxsosqualbut);

        allmidsosqualbut.setText("mid");
        allmidsosqualbut.setMargin(new java.awt.Insets(1, 1, 1, 1));
        allmidsosqualbut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allmidsosqualbutActionPerformed(evt);
            }
        });
        jPanel9.add(allmidsosqualbut);

        allminsosqualbut.setText("min");
        allminsosqualbut.setMargin(new java.awt.Insets(1, 1, 1, 1));
        allminsosqualbut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allminsosqualbutActionPerformed(evt);
            }
        });
        jPanel9.add(allminsosqualbut);

        sosQualPan.add(jPanel9);

        gainSlidersPanel.setToolTipText("Second order section feedback transconductance tweak, increase to increase Q");
        gainSlidersPanel.setAlignmentX(0.0F);
        gainSlidersPanel.setLayout(new java.awt.GridLayout(1, 0));
        sosQualPan.add(gainSlidersPanel);

        equalizerSlidersPanel.add(sosQualPan);

        bpfQualPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("BPF quality"));
        bpfQualPanel.setLayout(new javax.swing.BoxLayout(bpfQualPanel, javax.swing.BoxLayout.LINE_AXIS));

        jPanel8.setPreferredSize(new java.awt.Dimension(40, 63));
        jPanel8.setLayout(new javax.swing.BoxLayout(jPanel8, javax.swing.BoxLayout.Y_AXIS));

        allmaxbpfqualbut.setText("max");
        allmaxbpfqualbut.setMargin(new java.awt.Insets(1, 1, 1, 1));
        allmaxbpfqualbut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allmaxbpfqualbutActionPerformed(evt);
            }
        });
        jPanel8.add(allmaxbpfqualbut);

        allmidbpfqualbut.setText("mid");
        allmidbpfqualbut.setMargin(new java.awt.Insets(1, 1, 1, 1));
        allmidbpfqualbut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allmidbpfqualbutActionPerformed(evt);
            }
        });
        jPanel8.add(allmidbpfqualbut);

        allminbpfqualbut.setText("min");
        allminbpfqualbut.setMargin(new java.awt.Insets(1, 1, 1, 1));
        allminbpfqualbut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allminbpfqualbutActionPerformed(evt);
            }
        });
        jPanel8.add(allminbpfqualbut);

        bpfQualPanel.add(jPanel8);

        qualSlidersPanel.setToolTipText("Bandpass filter quality, increase for more ringiness");
        qualSlidersPanel.setAlignmentX(0.0F);
        qualSlidersPanel.setLayout(new java.awt.GridLayout(1, 0));
        bpfQualPanel.add(qualSlidersPanel);

        equalizerSlidersPanel.add(bpfQualPanel);

        lpfkilpan.setBorder(javax.swing.BorderFactory.createTitledBorder("LPF killed"));
        lpfkilpan.setLayout(new javax.swing.BoxLayout(lpfkilpan, javax.swing.BoxLayout.LINE_AXIS));

        jPanel6.setMinimumSize(new java.awt.Dimension(60, 42));
        jPanel6.setPreferredSize(new java.awt.Dimension(40, 48));
        jPanel6.setLayout(new javax.swing.BoxLayout(jPanel6, javax.swing.BoxLayout.Y_AXIS));

        killalllpfbut.setText("all");
        killalllpfbut.setMargin(new java.awt.Insets(1, 1, 1, 1));
        killalllpfbut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                killalllpfbutActionPerformed(evt);
            }
        });
        jPanel6.add(killalllpfbut);

        unkillalllpfbut.setText("none");
        unkillalllpfbut.setMargin(new java.awt.Insets(1, 1, 1, 1));
        unkillalllpfbut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unkillalllpfbutActionPerformed(evt);
            }
        });
        jPanel6.add(unkillalllpfbut);

        lpfkilpan.add(jPanel6);

        lpfKilledPanel.setToolTipText("Kills the lowpass filter neurons (Green=go, Red=killed)");
        lpfKilledPanel.setAlignmentX(0.0F);
        lpfKilledPanel.setMaximumSize(new java.awt.Dimension(32767, 60));
        lpfKilledPanel.setLayout(new java.awt.GridLayout(1, 0));
        lpfkilpan.add(lpfKilledPanel);

        equalizerSlidersPanel.add(lpfkilpan);

        bpfkillpan.setBorder(javax.swing.BorderFactory.createTitledBorder("BPF killed"));
        bpfkillpan.setLayout(new javax.swing.BoxLayout(bpfkillpan, javax.swing.BoxLayout.LINE_AXIS));

        jPanel7.setPreferredSize(new java.awt.Dimension(40, 42));
        jPanel7.setLayout(new javax.swing.BoxLayout(jPanel7, javax.swing.BoxLayout.Y_AXIS));

        killallbpfbut.setText("all");
        killallbpfbut.setMargin(new java.awt.Insets(1, 1, 1, 1));
        killallbpfbut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                killallbpfbutActionPerformed(evt);
            }
        });
        jPanel7.add(killallbpfbut);

        unkillallbpfbut.setText("none");
        unkillallbpfbut.setMargin(new java.awt.Insets(1, 1, 1, 1));
        unkillallbpfbut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unkillallbpfbutActionPerformed(evt);
            }
        });
        jPanel7.add(unkillallbpfbut);

        bpfkillpan.add(jPanel7);

        bpfKilledPanel.setToolTipText("Kills the bandpass filter neurons");
        bpfKilledPanel.setAlignmentX(0.0F);
        bpfKilledPanel.setMaximumSize(new java.awt.Dimension(32767, 60));
        bpfKilledPanel.setLayout(new java.awt.GridLayout(1, 0));
        bpfkillpan.add(bpfKilledPanel);

        equalizerSlidersPanel.add(bpfkillpan);

        equalizerPanel.add(equalizerSlidersPanel, java.awt.BorderLayout.CENTER);

        tabbedPane.addTab("equalizer", equalizerPanel);

        scannerPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Scanner control"));
        scannerPanel.setToolTipText("Control the analog scanner");
        scannerPanel.setLayout(new javax.swing.BoxLayout(scannerPanel, javax.swing.BoxLayout.PAGE_AXIS));

        jLabel2.setForeground(new java.awt.Color(204, 0, 51));
        jLabel2.setText("<html>ADC must be enabled to run Scanner");
        jLabel2.setAlignmentX(1.0F);
        scannerPanel.add(jLabel2);

        continuousScanningPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Continuous scanning"));
        continuousScanningPanel.setMaximumSize(new java.awt.Dimension(32767, 300));
        continuousScanningPanel.setPreferredSize(new java.awt.Dimension(0, 150));

        continuousScanningEnabledCheckBox.setText("Enable continuous scanning");
        continuousScanningEnabledCheckBox.setToolTipText("Turns on scanner to clock continuously");

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, this, org.jdesktop.beansbinding.ELProperty.create("${scanContinuouslyEnabled}"), continuousScanningEnabledCheckBox, org.jdesktop.beansbinding.BeanProperty.create("selected"));
        bindingGroup.addBinding(binding);

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder("Scanner selection"));
        jPanel5.setToolTipText("Selects which of the two on-chip scanner sync outputs should be monitored to count scan location. Selection must be correct to monitor given type when scanning is stopped!");

        scanSelGroup.add(basmemBut);
        basmemBut.setText("Basilar membrane voltage");
        basmemBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                basmemButActionPerformed(evt);
            }
        });

        scanSelGroup.add(gangcellBut);
        gangcellBut.setText("Ganglion cell Vmem");
        gangcellBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gangcellButActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(basmemBut)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(gangcellBut)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(basmemBut)
                    .addComponent(gangcellBut)))
        );

        javax.swing.GroupLayout continuousScanningPanelLayout = new javax.swing.GroupLayout(continuousScanningPanel);
        continuousScanningPanel.setLayout(continuousScanningPanelLayout);
        continuousScanningPanelLayout.setHorizontalGroup(
            continuousScanningPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(continuousScanningPanelLayout.createSequentialGroup()
                .addGroup(continuousScanningPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(continuousScanningEnabledCheckBox)
                    .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(686, Short.MAX_VALUE))
        );
        continuousScanningPanelLayout.setVerticalGroup(
            continuousScanningPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(continuousScanningPanelLayout.createSequentialGroup()
                .addComponent(continuousScanningEnabledCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(68, 68, 68))
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

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, scanSpinner, org.jdesktop.beansbinding.ELProperty.create("${value}"), scanSlider, org.jdesktop.beansbinding.BeanProperty.create("value"));
        bindingGroup.addBinding(binding);

        singleChannelSelectionPanel.add(scanSlider);

        scanSpinner.setToolTipText("Sets the scanned channel");
        scanSpinner.setMaximumSize(new java.awt.Dimension(32767, 40));
        scanSpinner.setPreferredSize(new java.awt.Dimension(200, 30));

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, this, org.jdesktop.beansbinding.ELProperty.create("${scanX}"), scanSpinner, org.jdesktop.beansbinding.BeanProperty.create("value"));
        bindingGroup.addBinding(binding);

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
            .addGap(0, 986, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 204, Short.MAX_VALUE)
        );

        scannerPanel.add(jPanel1);

        tabbedPane.addTab("scanner", scannerPanel);

        adcPanel.setLayout(new javax.swing.BoxLayout(adcPanel, javax.swing.BoxLayout.Y_AXIS));

        jLabel1.setText("Enables off-chip ADC, selects ADC channels, and thus controls graphical display of this data");
        adcPanel.add(jLabel1);

        jLabel6.setForeground(new java.awt.Color(204, 0, 0));
        jLabel6.setText("<html>Warnings: ADC must be enabled to run Scanner. <br> State changes made here can leave other GUI controls in inconsistent state");
        adcPanel.add(jLabel6);

        tabbedPane.addTab("ADC", adcPanel);

        onchipPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("On-chip preamp"));

        onchipGainPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("On-chip preamp gain"));
        onchipGainPanel.setLayout(new javax.swing.BoxLayout(onchipGainPanel, javax.swing.BoxLayout.LINE_AXIS));

        onchipGainGroup.add(onchipLowB);
        onchipLowB.setText("low");
        onchipLowB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onchipLowBActionPerformed(evt);
            }
        });
        onchipGainPanel.add(onchipLowB);

        onchipGainGroup.add(onchipMedB);
        onchipMedB.setText("medium");
        onchipMedB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onchipMedBActionPerformed(evt);
            }
        });
        onchipGainPanel.add(onchipMedB);

        onchipGainGroup.add(onchipHighB);
        onchipHighB.setText("high");
        onchipHighB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onchipHighBActionPerformed(evt);
            }
        });
        onchipGainPanel.add(onchipHighB);

        onchipGainGroup.add(onchipHighestB);
        onchipHighestB.setText("highest");
        onchipHighestB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onchipHighestBActionPerformed(evt);
            }
        });
        onchipGainPanel.add(onchipHighestB);

        javax.swing.GroupLayout onchipPanelLayout = new javax.swing.GroupLayout(onchipPanel);
        onchipPanel.setLayout(onchipPanelLayout);
        onchipPanelLayout.setHorizontalGroup(
            onchipPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(onchipPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(onchipGainPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(736, Short.MAX_VALUE))
        );
        onchipPanelLayout.setVerticalGroup(
            onchipPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(onchipGainPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        offchipPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Off-chip preamp"));
        offchipPanel.setLayout(new javax.swing.BoxLayout(offchipPanel, javax.swing.BoxLayout.Y_AXIS));

        jPanel4.setLayout(new javax.swing.BoxLayout(jPanel4, javax.swing.BoxLayout.LINE_AXIS));

        offchipGainPanelLeft.setBorder(javax.swing.BorderFactory.createTitledBorder("Gain - Left"));
        offchipGainPanelLeft.setLayout(new javax.swing.BoxLayout(offchipGainPanelLeft, javax.swing.BoxLayout.Y_AXIS));

        offchipGainLeftGroup.add(offchipLeftGainLowBut);
        offchipLeftGainLowBut.setText("low (40dB)");
        offchipLeftGainLowBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                offchipLeftGainLowButActionPerformed(evt);
            }
        });
        offchipGainPanelLeft.add(offchipLeftGainLowBut);

        offchipGainLeftGroup.add(offchipLeftGainMedBut);
        offchipLeftGainMedBut.setText("medium (50dB)");
        offchipLeftGainMedBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                offchipLeftGainMedButActionPerformed(evt);
            }
        });
        offchipGainPanelLeft.add(offchipLeftGainMedBut);

        offchipGainLeftGroup.add(offchipLeftGainHighBut);
        offchipLeftGainHighBut.setText("high (60dB)");
        offchipLeftGainHighBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                offchipLeftGainHighButActionPerformed(evt);
            }
        });
        offchipGainPanelLeft.add(offchipLeftGainHighBut);

        jPanel4.add(offchipGainPanelLeft);

        offchipARPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("AGC Attack/Release Ratio"));
        offchipARPanel.setLayout(new javax.swing.BoxLayout(offchipARPanel, javax.swing.BoxLayout.Y_AXIS));

        offchipARGroup.add(arFastBut);
        arFastBut.setText("fast release (1:500)");
        arFastBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                arFastButActionPerformed(evt);
            }
        });
        offchipARPanel.add(arFastBut);

        offchipARGroup.add(arMedBut);
        arMedBut.setText("medium release (1:2000)");
        arMedBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                arMedButActionPerformed(evt);
            }
        });
        offchipARPanel.add(arMedBut);

        offchipARGroup.add(arSlowBut);
        arSlowBut.setText("slow release (1:4000)");
        arSlowBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                arSlowButActionPerformed(evt);
            }
        });
        offchipARPanel.add(arSlowBut);

        jPanel4.add(offchipARPanel);

        offchipGainPanelRight.setBorder(javax.swing.BorderFactory.createTitledBorder("Gain - Right"));
        offchipGainPanelRight.setLayout(new javax.swing.BoxLayout(offchipGainPanelRight, javax.swing.BoxLayout.Y_AXIS));

        offchipGainRightGroup.add(offchipRightGainLowBut);
        offchipRightGainLowBut.setText("low (40dB)");
        offchipRightGainLowBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                offchipRightGainLowButActionPerformed(evt);
            }
        });
        offchipGainPanelRight.add(offchipRightGainLowBut);

        offchipGainRightGroup.add(offchipRightGainMedBut);
        offchipRightGainMedBut.setText("medium (50dB)");
        offchipRightGainMedBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                offchipRightGainMedButActionPerformed(evt);
            }
        });
        offchipGainPanelRight.add(offchipRightGainMedBut);

        offchipGainRightGroup.add(offchipRightGainHighBut);
        offchipRightGainHighBut.setText("high (60dB)");
        offchipRightGainHighBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                offchipRightGainHighButActionPerformed(evt);
            }
        });
        offchipGainPanelRight.add(offchipRightGainHighBut);

        jPanel4.add(offchipGainPanelRight);

        offchipPanel.add(jPanel4);

        javax.swing.GroupLayout preampPanelLayout = new javax.swing.GroupLayout(preampPanel);
        preampPanel.setLayout(preampPanelLayout);
        preampPanelLayout.setHorizontalGroup(
            preampPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, preampPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(preampPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(offchipPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 978, Short.MAX_VALUE)
                    .addComponent(onchipPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        preampPanelLayout.setVerticalGroup(
            preampPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(preampPanelLayout.createSequentialGroup()
                .addComponent(onchipPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(offchipPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 241, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(215, Short.MAX_VALUE))
        );

        tabbedPane.addTab("Mic Preamp", preampPanel);

        configPanel.setLayout(new javax.swing.BoxLayout(configPanel, javax.swing.BoxLayout.Y_AXIS));

        jLabel5.setForeground(new java.awt.Color(204, 0, 0));
        jLabel5.setText("Warning: State changes made here can leave other GUI controls in inconsistent state");
        configPanel.add(jLabel5);

        tabbedPane.addTab("Config", configPanel);

        add(tabbedPane, java.awt.BorderLayout.CENTER);

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

private void tabbedPaneMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tabbedPaneMouseClicked
    prefs.putInt("CochleaAMS1cControlPanel.selectedPaneIndex", tabbedPane.getSelectedIndex());
}//GEN-LAST:event_tabbedPaneMouseClicked

private void bufferBiasSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_bufferBiasSliderStateChanged
    getBiasgen().bufferIPot.setValue(bufferBiasSlider.getValue());
    bufferBiasTextField.setText(Integer.toString(getBiasgen().bufferIPot.getValue()));
    setFileModified();

}//GEN-LAST:event_bufferBiasSliderStateChanged

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
        getBiasgen().sendConfig(getBiasgen().CMD_VDAC, 0, b);
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
        getBiasgen().initDAC();
    } catch (Exception e) {
        log.warning(e.toString());
    }
}//GEN-LAST:event_initDACButtonActionPerformed

private void dacPoweroffButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dacPoweroffButtonActionPerformed
    try {
        getBiasgen().setDACPowered(!dacPoweroffButton.isSelected()); // ! because this should set powered off if selected
    } catch (HardwareInterfaceException ex) {
        log.warning(ex.toString());
    }
}//GEN-LAST:event_dacPoweroffButtonActionPerformed

private void dacPoweronButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dacPoweronButtonActionPerformed
    try {
        getBiasgen().setDACPowered(dacPoweronButton.isSelected());
    } catch (HardwareInterfaceException ex) {
        log.warning(ex.toString());
    }
}//GEN-LAST:event_dacPoweronButtonActionPerformed
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

    private void offchipLeftGainLowButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offchipLeftGainLowButActionPerformed
        biasgen.getOffchipPreampLeft().setGain(CochleaAMS1c.OffChipPreampGain.Low);
    }//GEN-LAST:event_offchipLeftGainLowButActionPerformed

    private void offchipLeftGainMedButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offchipLeftGainMedButActionPerformed
        biasgen.getOffchipPreampLeft().setGain(CochleaAMS1c.OffChipPreampGain.Medium);
    }//GEN-LAST:event_offchipLeftGainMedButActionPerformed

    private void offchipLeftGainHighButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offchipLeftGainHighButActionPerformed
        biasgen.getOffchipPreampLeft().setGain(CochleaAMS1c.OffChipPreampGain.High);
    }//GEN-LAST:event_offchipLeftGainHighButActionPerformed

    private void offchipRightGainLowButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offchipRightGainLowButActionPerformed
        biasgen.getOffchipPreampRight().setGain(CochleaAMS1c.OffChipPreampGain.Low);
    }//GEN-LAST:event_offchipRightGainLowButActionPerformed

    private void offchipRightGainMedButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offchipRightGainMedButActionPerformed
        biasgen.getOffchipPreampRight().setGain(CochleaAMS1c.OffChipPreampGain.Medium);
    }//GEN-LAST:event_offchipRightGainMedButActionPerformed

    private void offchipRightGainHighButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offchipRightGainHighButActionPerformed
        biasgen.getOffchipPreampRight().setGain(CochleaAMS1c.OffChipPreampGain.High);
    }//GEN-LAST:event_offchipRightGainHighButActionPerformed

    private void arFastButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_arFastButActionPerformed
        biasgen.setArRatio(CochleaAMS1c.OffChipPreamp_AGC_AR_Ratio.Fast);
    }//GEN-LAST:event_arFastButActionPerformed

    private void arMedButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_arMedButActionPerformed
        biasgen.setArRatio(CochleaAMS1c.OffChipPreamp_AGC_AR_Ratio.Medium);
    }//GEN-LAST:event_arMedButActionPerformed

    private void arSlowButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_arSlowButActionPerformed
        biasgen.setArRatio(CochleaAMS1c.OffChipPreamp_AGC_AR_Ratio.Slow);
    }//GEN-LAST:event_arSlowButActionPerformed

    private void onchipLowBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onchipLowBActionPerformed
        biasgen.getOnchipPreamp().setGain(CochleaAMS1c.OnChipPreampGain.Low);
    }//GEN-LAST:event_onchipLowBActionPerformed

    private void onchipMedBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onchipMedBActionPerformed
        biasgen.getOnchipPreamp().setGain(CochleaAMS1c.OnChipPreampGain.Medium);
    }//GEN-LAST:event_onchipMedBActionPerformed

    private void onchipHighBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onchipHighBActionPerformed
        biasgen.getOnchipPreamp().setGain(CochleaAMS1c.OnChipPreampGain.Higher);
    }//GEN-LAST:event_onchipHighBActionPerformed

    private void setClrADCChannel(ActionEvent evt, int chan) {
        if (!(evt.getSource() instanceof AbstractButton)) {
            return;
        }
        boolean yes = ((AbstractButton) evt.getSource()).isSelected();
        if (yes) {
            biasgen.getAdcProxy().setADCChannel(biasgen.getAdcProxy().getADCChannel() | (1 << chan));
        } else {
            biasgen.getAdcProxy().setADCChannel(biasgen.getAdcProxy().getADCChannel() & ~(1 << chan));
        }
    }
    private void scanSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_scanSpinnerStateChanged
        continuousScanningEnabledCheckBox.setSelected(false);
    }//GEN-LAST:event_scanSpinnerStateChanged

    private void basmemButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_basmemButActionPerformed
        biasgen.getScanner().setScanBasMemV(basmemBut.isSelected());
    }//GEN-LAST:event_basmemButActionPerformed

    private void gangcellButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gangcellButActionPerformed
        biasgen.getScanner().setScanGanglionCellVMem(gangcellBut.isSelected());
    }//GEN-LAST:event_gangcellButActionPerformed

    private void resetEqButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetEqButActionPerformed
        biasgen.resetEqualizer();
    }//GEN-LAST:event_resetEqButActionPerformed

    private void onchipHighestBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onchipHighestBActionPerformed
        biasgen.getOnchipPreamp().setGain(CochleaAMS1c.OnChipPreampGain.Highest);
    }//GEN-LAST:event_onchipHighestBActionPerformed

    private void unkillallbpfbutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unkillallbpfbutActionPerformed
        biasgen.equalizer.setAllBPFKilled(false);
    }//GEN-LAST:event_unkillallbpfbutActionPerformed

    private void killallbpfbutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_killallbpfbutActionPerformed
        biasgen.equalizer.setAllBPFKilled(true);
    }//GEN-LAST:event_killallbpfbutActionPerformed

    private void killalllpfbutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_killalllpfbutActionPerformed
        biasgen.equalizer.setAllLPFKilled(true);
    }//GEN-LAST:event_killalllpfbutActionPerformed

    private void unkillalllpfbutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unkillalllpfbutActionPerformed
        biasgen.equalizer.setAllLPFKilled(false);
    }//GEN-LAST:event_unkillalllpfbutActionPerformed

    private void allmaxsosqualbutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allmaxsosqualbutActionPerformed
        biasgen.equalizer.setAllQSOS(Equalizer.MAX_VALUE);
    }//GEN-LAST:event_allmaxsosqualbutActionPerformed

    private void allmidsosqualbutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allmidsosqualbutActionPerformed
        biasgen.equalizer.setAllQSOS(Equalizer.MAX_VALUE / 2);
    }//GEN-LAST:event_allmidsosqualbutActionPerformed

    private void allminsosqualbutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allminsosqualbutActionPerformed
        biasgen.equalizer.setAllQSOS(0);
    }//GEN-LAST:event_allminsosqualbutActionPerformed

    private void allmaxbpfqualbutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allmaxbpfqualbutActionPerformed
        biasgen.equalizer.setAllQBPF(Equalizer.MAX_VALUE);
    }//GEN-LAST:event_allmaxbpfqualbutActionPerformed

    private void allmidbpfqualbutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allmidbpfqualbutActionPerformed
        biasgen.equalizer.setAllQBPF(Equalizer.MAX_VALUE / 2);
    }//GEN-LAST:event_allmidbpfqualbutActionPerformed

    private void allminbpfqualbutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allminbpfqualbutActionPerformed
        biasgen.equalizer.setAllQBPF(0);
    }//GEN-LAST:event_allminbpfqualbutActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel adcPanel;
    private javax.swing.JButton allmaxbpfqualbut;
    private javax.swing.JButton allmaxsosqualbut;
    private javax.swing.JButton allmidbpfqualbut;
    private javax.swing.JButton allmidsosqualbut;
    private javax.swing.JButton allminbpfqualbut;
    private javax.swing.JButton allminsosqualbut;
    private javax.swing.JRadioButton arFastBut;
    private javax.swing.JRadioButton arMedBut;
    private javax.swing.JRadioButton arSlowBut;
    private javax.swing.JRadioButton basmemBut;
    private javax.swing.JPanel bpfKilledPanel;
    private javax.swing.JPanel bpfQualPanel;
    private javax.swing.JPanel bpfkillpan;
    private javax.swing.JPanel bufferBiasPanel;
    private javax.swing.JSlider bufferBiasSlider;
    private javax.swing.JTextField bufferBiasTextField;
    private javax.swing.ButtonGroup buttonGroup1;
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
    private javax.swing.JRadioButton gangcellBut;
    private javax.swing.JButton initDACButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JButton killallbpfbut;
    private javax.swing.JButton killalllpfbut;
    private javax.swing.JPanel lpfKilledPanel;
    private javax.swing.JPanel lpfkilpan;
    private javax.swing.ButtonGroup offchipARGroup;
    private javax.swing.JPanel offchipARPanel;
    private javax.swing.JPanel offchipDACPanel;
    private javax.swing.ButtonGroup offchipGainLeftGroup;
    private javax.swing.JPanel offchipGainPanelLeft;
    private javax.swing.JPanel offchipGainPanelRight;
    private javax.swing.ButtonGroup offchipGainRightGroup;
    private javax.swing.JRadioButton offchipLeftGainHighBut;
    private javax.swing.JRadioButton offchipLeftGainLowBut;
    private javax.swing.JRadioButton offchipLeftGainMedBut;
    private javax.swing.JPanel offchipPanel;
    private javax.swing.JRadioButton offchipRightGainHighBut;
    private javax.swing.JRadioButton offchipRightGainLowBut;
    private javax.swing.JRadioButton offchipRightGainMedBut;
    private javax.swing.JPanel onchipBiasgenPanel;
    private javax.swing.ButtonGroup onchipGainGroup;
    private javax.swing.JPanel onchipGainPanel;
    private javax.swing.JRadioButton onchipHighB;
    private javax.swing.JRadioButton onchipHighestB;
    private javax.swing.JRadioButton onchipLowB;
    private javax.swing.JRadioButton onchipMedB;
    private javax.swing.JPanel onchipPanel;
    private javax.swing.JPanel preampPanel;
    private javax.swing.JPanel qualSlidersPanel;
    private javax.swing.JButton resetEqBut;
    private javax.swing.JCheckBox rubberBandCB;
    private javax.swing.ButtonGroup scanSelGroup;
    private javax.swing.JSlider scanSlider;
    private javax.swing.JSpinner scanSpinner;
    private javax.swing.JPanel scannerPanel;
    private javax.swing.JPanel singleChannelSelectionPanel;
    private javax.swing.JPanel sosQualPan;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JButton unkillallbpfbut;
    private javax.swing.JButton unkillalllpfbut;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
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
//        if(hashtable.isSet(STATE_KEY)==null) {
////            System.err.println("pot "+pot+" not in hashtable "+hashtable+" with size="+hashtable.size());
////            Set s=hashtable.entrySet();
////            System.out.println("hashtable entries");
////            for(Iterator i=s.iterator();i.hasNext();){
////                Map.Entry me=(Map.Entry)i.next();
////                System.out.println(me);
////            }
//            return;
//        }
//        int integerConfig=(Integer)hashtable.isSet(STATE_KEY);
//        pot.setBitValue(integerConfig);
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
//        protected void removeRedundantState(){}; // override this to actually isSet a state stored!!
//    }

 
}
