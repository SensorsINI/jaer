package ch.unizh.ini.jaer.chip.nrv;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.nrv.NRVRegisterSetting;

/**
 * Register settings table for NRV cameras. Value column edits are sent to the
 * device on commit (Enter); the source settings file is not modified.
 */
public class NRVControlPanel extends JPanel {

    private static final Logger log = Logger.getLogger(NRVControlPanel.class.getName());
    private static final int COL_SLAVE = 0;
    private static final int COL_ADDRESS = 1;
    private static final int COL_VALUE = 2;
    private static final int COL_COMMENT = 3;
    private static final int COL_APPLIED = 4;

    private static final String[] COLUMN_NAMES = {
        "Slave", "Address", "Value", "Comment", "Applied"
    };

    private final NRVConfig config;
    private final List<NRVRegisterSetting> rowSettings = new ArrayList<>();
    private final JLabel summaryLabel = new JLabel("No settings loaded");
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == COL_VALUE && isRegisterRow(row);
        }

        @Override
        public void setValueAt(Object aValue, int row, int column) {
            if (column != COL_VALUE || !isRegisterRow(row)) {
                return;
            }
            final NRVRegisterSetting setting = rowSettings.get(row);
            final int oldValue = setting.getValue() & 0xff;
            int newValue;
            try {
                newValue = parseRegisterValue(String.valueOf(aValue));
            } catch (NumberFormatException e) {
                summaryLabel.setForeground(Color.RED);
                summaryLabel.setText("Invalid value (use 0x00..0xFF): " + aValue);
                super.setValueAt(String.format("0x%02X", oldValue), row, COL_VALUE);
                return;
            }
            if (newValue == oldValue) {
                return;
            }
            try {
                config.writeRegisterValue(setting, newValue);
            } catch (HardwareInterfaceException e) {
                log.warning("NRV register write failed: " + e.getMessage());
                summaryLabel.setForeground(Color.RED);
                summaryLabel.setText("Write failed " + setting + ": " + e.getMessage());
                setting.setApplied(false);
                super.setValueAt(String.format("0x%02X", oldValue), row, COL_VALUE);
                super.setValueAt(Boolean.FALSE, row, COL_APPLIED);
                return;
            }
            super.setValueAt(String.format("0x%02X", newValue), row, COL_VALUE);
            super.setValueAt(Boolean.TRUE, row, COL_APPLIED);
            summaryLabel.setForeground(Color.DARK_GRAY);
            summaryLabel.setText(String.format("Sent %02x:%04x=%02x (file unchanged)",
                    setting.getSlaveAddr(), setting.getRegAddr(), newValue));
        }
    };
    private final JTable table = new JTable(tableModel);

    public NRVControlPanel(NRVConfig config) {
        super(new BorderLayout());
        this.config = config;
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(summaryLabel, BorderLayout.NORTH);
        if (config.getLoadedSettings() != null) {
            updateSettings(config.getLoadedSettings(), config.getSettingsDescription(), config.getLoadedFile());
        }
    }

    private boolean isRegisterRow(int row) {
        return row >= 0 && row < rowSettings.size() && !rowSettings.get(row).isWait();
    }

    static int parseRegisterValue(String text) throws NumberFormatException {
        if (text == null) {
            throw new NumberFormatException("empty");
        }
        String s = text.trim();
        if (s.isEmpty()) {
            throw new NumberFormatException("empty");
        }
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2).trim();
        }
        final int value = Integer.parseInt(s, 16);
        if (value < 0 || value > 0xFF) {
            throw new NumberFormatException("out of byte range");
        }
        return value;
    }

    public void updateSettings(List<NRVRegisterSetting> settings, String description, File file) {
        rowSettings.clear();
        tableModel.setRowCount(0);
        if (settings == null) {
            summaryLabel.setForeground(Color.DARK_GRAY);
            summaryLabel.setText("No settings loaded");
            return;
        }
        rowSettings.addAll(settings);
        for (NRVRegisterSetting setting : settings) {
            if (setting.isWait()) {
                tableModel.addRow(new Object[]{
                    "-", "wait", setting.getValue(), setting.getComment(), setting.isApplied()
                });
            } else {
                tableModel.addRow(new Object[]{
                    String.format("0x%02X", setting.getSlaveAddr()),
                    String.format("0x%04X", setting.getRegAddr()),
                    String.format("0x%02X", setting.getValue() & 0xff),
                    setting.getComment(),
                    setting.isApplied()
                });
            }
        }
        final String fileName = file == null ? "" : file.getName();
        summaryLabel.setForeground(Color.DARK_GRAY);
        summaryLabel.setText(String.format("%s — %s (%d entries) — edit Value and press Enter to send",
                description == null || description.isEmpty() ? "NRV settings" : description,
                fileName,
                settings.size()));
    }
}
