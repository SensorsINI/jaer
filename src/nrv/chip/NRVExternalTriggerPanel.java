package nrv.chip;

import java.awt.BorderLayout;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import net.sf.jaer.biasgen.Biasgen;
import nrv.usb.NRVRegisterSetting;

/** User controls for configuring the sensor's external trigger input and mode. */
public class NRVExternalTriggerPanel extends JPanel implements PropertyChangeListener {

    private final NRVConfig config;
    private final JCheckBox externalTriggerInCheckBox = new JCheckBox("External Trigger In");
    private final JRadioButton singleButton = new JRadioButton("Single");
    private final JRadioButton burstButton = new JRadioButton("Burst");
    private final JRadioButton burstSingleButton = new JRadioButton("Burst Single");
    private boolean updatingFromConfig;

    public NRVExternalTriggerPanel(NRVConfig config) {
        super(new BorderLayout());
        this.config = config;

        externalTriggerInCheckBox.setToolTipText(
                "Capture frames when the external trigger input is high. If unchecked, the sensor will capture frames continuously.");
        singleButton.setToolTipText("Sets 0x3A02 to 0x00.");
        burstButton.setToolTipText("Sets 0x3A02 to 0x01.");
        burstSingleButton.setToolTipText("Sets 0x3A02 to 0x11.");

        final ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(singleButton);
        modeGroup.add(burstButton);
        modeGroup.add(burstSingleButton);

        externalTriggerInCheckBox.addActionListener(e -> {
            if (!updatingFromConfig) {
                config.setExternalTriggerInEnabled(externalTriggerInCheckBox.isSelected());
            }
        });
        singleButton.addActionListener(e -> setMode(NRVConfig.EXTERNAL_TRIGGER_MODE_SINGLE));
        burstButton.addActionListener(e -> setMode(NRVConfig.EXTERNAL_TRIGGER_MODE_BURST));
        burstSingleButton.addActionListener(e -> setMode(NRVConfig.EXTERNAL_TRIGGER_MODE_BURST_SINGLE));

        final JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("External Trigger"));
        section.setToolTipText("Configure the sensor external-trigger input and capture mode.");
        final JLabel modeLabel = new JLabel("External Trigger Mode");
        modeLabel.setToolTipText("Single: capture one frame per trigger; Burst: capture frames continuously after the first trigger; Burst Single: capture multiple frames per trigger.");
        section.add(externalTriggerInCheckBox);
        section.add(Box.createVerticalStrut(6));
        section.add(modeLabel);
        section.add(singleButton);
        section.add(burstButton);
        section.add(burstSingleButton);
        for (Component component : section.getComponents()) {
            if (component instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
        add(section, BorderLayout.NORTH);

        config.getSupport().addPropertyChangeListener(this);
        syncFromConfig();
    }

    void syncFromConfig() {
        updatingFromConfig = true;
        externalTriggerInCheckBox.setSelected(config.isExternalTriggerInEnabled());
        switch (config.getExternalTriggerMode()) {
            case NRVConfig.EXTERNAL_TRIGGER_MODE_BURST:
                burstButton.setSelected(true);
                break;
            case NRVConfig.EXTERNAL_TRIGGER_MODE_BURST_SINGLE:
                burstSingleButton.setSelected(true);
                break;
            default:
                singleButton.setSelected(true);
                break;
        }
        updatingFromConfig = false;
    }

    private void setMode(int mode) {
        if (!updatingFromConfig) {
            config.setExternalTriggerMode(mode);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (Biasgen.PROPERTY_CHANGE_PREFERENCES_LOADED.equals(evt.getPropertyName())) {
            syncFromConfig();
            return;
        }
        if (NRVConfig.PROPERTY_REGISTER_UPDATED.equals(evt.getPropertyName())
                && evt.getNewValue() instanceof NRVRegisterSetting) {
            final int address = ((NRVRegisterSetting) evt.getNewValue()).getRegAddr();
            if (address == NRVConfig.REG_EXTERNAL_TRIGGER_IN || address == NRVConfig.REG_EXTERNAL_TRIGGER_MODE
                    || address == NRVConfig.REG_EXTERNAL_TRIGGER_GATE
                    || address == NRVConfig.REG_EXTERNAL_TRIGGER_CONTROL) {
                syncFromConfig();
            }
        }
    }
}
