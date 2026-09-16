package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.NumberFormat;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import org.apache.commons.text.WordUtils;

import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.hardwareinterface.usb.HasLiveDisplayEventCap;
import net.sf.jaer.hardwareinterface.usb.HasUsbStatistics;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.USBPacketStatistics;
import net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycle;
import net.sf.jaer.hardwareinterface.usb.UsbReaderBufferSettings;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.WindowSaver;

/**
 * Separate top-level window for live USB FIFO / buffer count / AE render-packet
 * size (and Prophesee live keep limit when available). Spinner and typed edits
 * auto-apply after a short pause so touchpad / arrow-key adjustments stay usable
 * while the camera runs. While this window is open, USB IN transfer statistics
 * are collected and shown in the table (~1 s windows).
 */
public class UsbTuningFrame extends JFrame implements PropertyChangeListener, WindowSaver.DontResize {

    private static final int UI_DEBOUNCE_MS = 350;
    private static final int STATS_POLL_MS = 1000;
    private static final int RENDER_MIN = 1 << 16;
    private static final int RENDER_MAX = 1 << 23;
    private static final int NOTE_WRAP = 32;
    private static final String DASH = "—";
    private static final int ROW_FIFO = 0;
    private static final int ROW_BUFFERS = 1;
    private static final int ROW_FILL = 2;
    private static final int ROW_THROUGHPUT = 7;
    private static final int ROW_ERRORS = 11;
    private static final int ROW_NOTE = 12;
    private static final Color FILL_YELLOW = new Color(255, 230, 80);
    private static final Color FILL_ORANGE = new Color(255, 160, 40);
    private static final Color FILL_RED = new Color(220, 50, 50);
    private static final String[] STAT_ROWS = {
        "FIFO",
        "Buffers",
        "Fill",
        "Avg size",
        "Min size",
        "Max size",
        "Interval",
        "Throughput",
        "Completions",
        "Empty",
        "Short",
        "Errors",
        "Note"
    };
    private static final String[] STAT_TIPS = {
        "Host bulk-IN buffer (URB) size you set on the left. The camera may complete far less than this.",
        "Number of those URBs queued at once.",
        "Avg completed size / FIFO. Low fill with min=max means the camera always finishes a small, fixed burst.",
        "Mean bytes per completed USB bulk IN in the last second. Not event count.",
        "Smallest completed bulk IN in the last second.",
        "Largest completed bulk IN in the last second.",
        "Mean time between completed bulk INs.",
        "Bytes completed per second (size × completions).",
        "Completed bulk INs per second.",
        "Completions with 0 bytes (ZLP).",
        "Completions shorter than FIFO (normal if the camera sends less than the URB).",
        "Failed USB transfers in the last second.",
        "Hint from fill vs FIFO."
    };

    private final AEViewer viewer;
    private final NumberFormat intFormat = NumberFormat.getIntegerInstance();
    private final EngineeringFormat engFmt = new EngineeringFormat();

    private JSpinner fifoSpinner;
    private JSpinner buffersSpinner;
    private JSpinner renderSpinner;
    private JSpinner keepSpinner;
    private JLabel keepLabel;
    private JLabel requestedLabel;
    private JLabel activeLabel;
    private JLabel allocationLabel;
    private JLabel statusLabel;
    private JLabel statsStatusLabel;
    private DefaultTableModel statsModel;
    private JTable statsTable;
    private float lastFillFraction = Float.NaN;
    private int lastErrorCount;

    private boolean updatingUi;
    private Timer applyTimer;
    private Timer statsTimer;
    private PropertyChangeSupport subscribedSupport;
    private AEMonitorInterface boundMonitor;
    private HasUsbStatistics boundStats;

    public UsbTuningFrame(AEViewer viewer) {
        super("USB tuning" + (viewer != null && viewer.getTitle() != null ? " — " + viewer.getTitle() : ""));
        this.viewer = viewer;
        setName("UsbTuning");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        if (viewer != null && viewer.getIconImage() != null) {
            setIconImage(viewer.getIconImage());
        }
        buildUi();

        applyTimer = new Timer(UI_DEBOUNCE_MS, this::applyPendingEdits);
        applyTimer.setRepeats(false);
        statsTimer = new Timer(STATS_POLL_MS, e -> pollStatisticsTable());
        statsTimer.setRepeats(true);
        statsTimer.setInitialDelay(STATS_POLL_MS);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                teardown();
            }
        });
    }

    public void showForCurrentDevice() {
        refreshFromHardware();
        resubscribe();
        bindStatisticsCollection();
        packToContent();
        if (!isVisible()) {
            setLocationRelativeTo(viewer);
            setVisible(true);
            // Windows: decorations exist only after the peer is created.
            packToContent();
            setLocationRelativeTo(viewer);
        }
        if (statsTimer != null && !statsTimer.isRunning()) {
            statsTimer.start();
        }
        toFront();
        requestFocus();
    }

    /**
     * Rebind after the viewer opens or switches a camera while this window is
     * already showing.
     */
    public void deviceChanged() {
        if (!isDisplayable()) {
            return;
        }
        refreshFromHardware();
        resubscribe();
        bindStatisticsCollection();
    }

    /** Size the frame to the layout after all components (and their values) are in place. */
    private void packToContent() {
        invalidate();
        pack();
        setMinimumSize(getPreferredSize());
    }

    private void buildUi() {
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        final JPanel form = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        fifoSpinner = new JSpinner(new OctaveSpinnerNumberModel(
                UsbReaderBufferSettings.MIN_FIFO_SIZE,
                UsbReaderBufferSettings.MIN_FIFO_SIZE,
                UsbReaderBufferSettings.MAX_FIFO_SIZE,
                UsbReaderBufferSettings.MIN_FIFO_SIZE));
        buffersSpinner = new JSpinner(new SpinnerNumberModel(
                UsbReaderBufferSettings.MIN_NUM_BUFFERS,
                UsbReaderBufferSettings.MIN_NUM_BUFFERS,
                UsbReaderBufferSettings.MAX_NUM_BUFFERS,
                1));
        renderSpinner = new JSpinner(new OctaveSpinnerNumberModel(RENDER_MIN, RENDER_MIN, RENDER_MAX, RENDER_MIN));
        keepSpinner = new JSpinner(new OctaveSpinnerNumberModel(
                HasLiveDisplayEventCap.DEFAULT_LIVE_DISPLAY_EVENT_CAP,
                RENDER_MIN, RENDER_MAX, RENDER_MIN));

        configureSpinnerEditor(fifoSpinner);
        configureSpinnerEditor(buffersSpinner);
        configureSpinnerEditor(renderSpinner);
        configureSpinnerEditor(keepSpinner);

        fifoSpinner.setToolTipText("Host USB FIFO bytes per async bulk transfer (4 KiB–2 MiB, powers of two).");
        buffersSpinner.setToolTipText("Number of overlapped USB read buffers (1–32).");
        renderSpinner.setToolTipText("AEPacketRaw pool size in events (2 buffers).");
        keepSpinner.setToolTipText("<html>Prophesee only: max polarity events kept per live display frame.<br>"
                + "Effective keep is min(Render events, Live keep). Raising this past ~256k can hitch the UI<br>"
                + "at high event rates; AEDAT logging uses the same capped packet.</html>");

        int row = 0;
        addRow(form, c, row++, "FIFO bytes", fifoSpinner);
        addRow(form, c, row++, "Buffers", buffersSpinner);
        addRow(form, c, row++, "Render events", renderSpinner);
        keepLabel = new JLabel("Live keep");
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(keepLabel, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(keepSpinner, c);
        row++;

        requestedLabel = new JLabel("Requested: —");
        activeLabel = new JLabel("Active: —");
        allocationLabel = new JLabel("Total: —");
        statusLabel = new JLabel("Status: —");

        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(requestedLabel, c);
        c.gridy = row++;
        form.add(activeLabel, c);
        c.gridy = row++;
        form.add(allocationLabel, c);
        c.gridy = row++;
        form.add(statusLabel, c);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        final JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> {
            refreshFromHardware();
            pollStatisticsTable();
        });
        final JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        buttons.add(refresh);
        buttons.add(close);

        final JPanel body = new JPanel(new BorderLayout(12, 8));
        body.add(form, BorderLayout.WEST);
        body.add(buildStatsPanel(), BorderLayout.CENTER);

        root.add(body, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        fifoSpinner.addChangeListener(this::onSpinnerChanged);
        buffersSpinner.addChangeListener(this::onSpinnerChanged);
        renderSpinner.addChangeListener(this::onSpinnerChanged);
        keepSpinner.addChangeListener(this::onSpinnerChanged);
    }

    private JPanel buildStatsPanel() {
        statsModel = new DefaultTableModel(new Object[] {"Metric", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (String name : STAT_ROWS) {
            statsModel.addRow(new Object[] {name, DASH});
        }
        statsTable = new JTable(statsModel);
        statsTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        statsTable.setRowHeight(20);
        statsTable.setShowGrid(true);
        statsTable.setFillsViewportHeight(true);
        statsTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        statsTable.setCellSelectionEnabled(true);
        statsTable.getTableHeader().setReorderingAllowed(false);
        statsTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statsTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        statsTable.setToolTipText("<html>USB bulk IN completions for the last 1 s (updates at 1 Hz).<br>"
                + "Size rows are bytes delivered when libusb finishes one read, not DVS events.<br>"
                + "Fill is that size divided by the host FIFO. Hover a row for details.</html>");
        TableColumn metricCol = statsTable.getColumnModel().getColumn(0);
        metricCol.setPreferredWidth(120);
        metricCol.setMaxWidth(150);
        statsTable.getColumnModel().getColumn(1).setPreferredWidth(240);
        StatsCellRenderer renderer = new StatsCellRenderer();
        statsTable.getColumnModel().getColumn(0).setCellRenderer(renderer);
        statsTable.getColumnModel().getColumn(1).setCellRenderer(renderer);

        final JScrollPane scroll = new JScrollPane(statsTable);
        scroll.setPreferredSize(new Dimension(360, 13 * 20 + 48));

        statsStatusLabel = new JLabel("USB IN: waiting for device");
        statsStatusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));

        final JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("USB IN statistics"));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(statsStatusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private final class StatsCellRenderer extends DefaultTableCellRenderer {
        private final Font paramFont = new Font(Font.SANS_SERIF, Font.ITALIC, 12);
        private final Font paramValueFont = new Font(Font.MONOSPACED, Font.ITALIC, 12);
        private final Font measureFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        private final Font measureValueFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        private final Font criticalFont = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        private final Font criticalValueFont = new Font(Font.MONOSPACED, Font.BOLD, 12);
        private final Font noteFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            boolean valueCol = column == 1;
            boolean note = row == ROW_NOTE;
            setHorizontalAlignment(note ? SwingConstants.LEFT : (valueCol ? SwingConstants.RIGHT : SwingConstants.LEFT));
            setVerticalAlignment(note ? SwingConstants.TOP : SwingConstants.CENTER);
            if (row == ROW_FIFO || row == ROW_BUFFERS) {
                setFont(valueCol ? paramValueFont : paramFont);
            } else if (row == ROW_FILL || row == ROW_THROUGHPUT) {
                setFont(valueCol ? criticalValueFont : criticalFont);
            } else if (note) {
                setFont(noteFont);
            } else {
                setFont(valueCol ? measureValueFont : measureFont);
            }
            if (row >= 0 && row < STAT_TIPS.length) {
                setToolTipText(STAT_TIPS[row]);
            }
            if (isSelected) {
                return this;
            }
            setForeground(table.getForeground());
            setBackground(table.getBackground());
            if (row == ROW_FILL && !Float.isNaN(lastFillFraction)) {
                if (lastFillFraction > 1f) {
                    setBackground(FILL_RED);
                    setForeground(Color.WHITE);
                } else if (lastFillFraction > 0.75f) {
                    setBackground(FILL_ORANGE);
                    setForeground(Color.BLACK);
                } else if (lastFillFraction > 0.50f) {
                    setBackground(FILL_YELLOW);
                    setForeground(Color.BLACK);
                }
            } else if (row == ROW_ERRORS && lastErrorCount > 0) {
                setForeground(Color.RED);
            }
            return this;
        }
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String label, JSpinner spinner) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(spinner, c);
    }

    private static void configureSpinnerEditor(JSpinner spinner) {
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#,##0"));
        final JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        editor.getTextField().setColumns(11);
        installMouseWheel(spinner);
    }

    /** Hover and scroll to step the spinner (octave or ±1 depending on model). */
    private static void installMouseWheel(JSpinner spinner) {
        final MouseWheelListener wheel = (MouseWheelEvent e) -> {
            if (!spinner.isEnabled()) {
                return;
            }
            e.consume();
            final int rotation = e.getWheelRotation();
            if (rotation == 0) {
                return;
            }
            // Wheel up / away from user -> increase; wheel down -> decrease.
            final Object next = rotation < 0 ? spinner.getNextValue() : spinner.getPreviousValue();
            if (next != null) {
                spinner.setValue(next);
            }
        };
        spinner.addMouseWheelListener(wheel);
        for (Component child : spinner.getComponents()) {
            child.addMouseWheelListener(wheel);
            if (child instanceof JSpinner.DefaultEditor) {
                ((JSpinner.DefaultEditor) child).getTextField().addMouseWheelListener(wheel);
            }
        }
    }

    private void onSpinnerChanged(ChangeEvent e) {
        if (updatingUi) {
            return;
        }
        updateAllocationLabel();
        applyTimer.restart();
    }

    private void applyPendingEdits(ActionEvent e) {
        if (updatingUi) {
            return;
        }
        final AEMonitorInterface monitor = viewer.aemon;
        if (monitor == null) {
            statusLabel.setText("Status: no device open");
            return;
        }

        final int render = ((Number) renderSpinner.getValue()).intValue();
        if (monitor.getAEBufferSize() != render) {
            monitor.setAEBufferSize(render);
        }

        if (monitor instanceof HasLiveDisplayEventCap) {
            final HasLiveDisplayEventCap keep = (HasLiveDisplayEventCap) monitor;
            final int value = ((Number) keepSpinner.getValue()).intValue();
            if (keep.getLiveDisplayEventCap() != value) {
                keep.setLiveDisplayEventCap(value);
            }
        }

        if (monitor instanceof ReaderBufferControl) {
            final ReaderBufferControl reader = (ReaderBufferControl) monitor;
            final int fifo = ((Number) fifoSpinner.getValue()).intValue();
            final int buffers = ((Number) buffersSpinner.getValue()).intValue();
            if (reader.getFifoSize() != fifo) {
                reader.setFifoSize(fifo);
            }
            if (reader.getNumBuffers() != buffers) {
                reader.setNumBuffers(buffers);
            }
        }
        refreshStatusLabels();
    }

    public void refreshFromHardware() {
        final AEMonitorInterface monitor = viewer.aemon;
        boundMonitor = monitor;
        updatingUi = true;
        try {
            final boolean hasReader = monitor instanceof ReaderBufferControl;
            final boolean hasKeep = monitor instanceof HasLiveDisplayEventCap;
            fifoSpinner.setEnabled(hasReader);
            buffersSpinner.setEnabled(hasReader);
            renderSpinner.setEnabled(monitor != null);
            keepLabel.setVisible(hasKeep);
            keepSpinner.setVisible(hasKeep);
            keepSpinner.setEnabled(hasKeep);

            if (monitor != null) {
                int render = monitor.getAEBufferSize();
                render = Math.max(RENDER_MIN, Math.min(RENDER_MAX, render));
                renderSpinner.setValue(render);
            }
            if (hasKeep) {
                final HasLiveDisplayEventCap keep = (HasLiveDisplayEventCap) monitor;
                int value = keep.getLiveDisplayEventCap();
                value = Math.max(keep.getMinLiveDisplayEventCap(),
                        Math.min(keep.getMaxLiveDisplayEventCap(), value));
                keepSpinner.setValue(value);
            }
            if (hasReader) {
                final ReaderBufferControl reader = (ReaderBufferControl) monitor;
                int fifo = reader.getPendingFifoSize();
                fifo = Math.max(UsbReaderBufferSettings.MIN_FIFO_SIZE,
                        Math.min(UsbReaderBufferSettings.MAX_FIFO_SIZE, fifo));
                int buffers = reader.getPendingNumBuffers();
                buffers = Math.max(UsbReaderBufferSettings.MIN_NUM_BUFFERS,
                        Math.min(UsbReaderBufferSettings.MAX_NUM_BUFFERS, buffers));
                fifoSpinner.setValue(fifo);
                buffersSpinner.setValue(buffers);
            }
        } finally {
            updatingUi = false;
        }
        refreshStatusLabels();
        updateAllocationLabel();
        if (isDisplayable()) {
            packToContent();
        }
    }

    private void refreshStatusLabels() {
        final AEMonitorInterface monitor = viewer.aemon;
        if (!(monitor instanceof ReaderBufferControl)) {
            requestedLabel.setText("Requested: —");
            activeLabel.setText("Active: —");
            statusLabel.setText(monitor == null ? "Status: no device open" : "Status: render packet only");
            return;
        }
        final ReaderBufferControl reader = (ReaderBufferControl) monitor;
        final int reqFifo = reader.getPendingFifoSize();
        final int reqBuf = reader.getPendingNumBuffers();
        final int actFifo = reader.getActiveFifoSize();
        final int actBuf = reader.getActiveNumBuffers();
        requestedLabel.setText(String.format("Requested: %s × %s",
                intFormat.format(reqFifo), intFormat.format(reqBuf)));
        activeLabel.setText(String.format("Active: %s × %s",
                intFormat.format(actFifo), intFormat.format(actBuf)));

        final UsbAsyncBulkReaderLifecycle.Status status = reader.getUsbBufferConfigStatus();
        String phase;
        if (status == null) {
            phase = reader.isUsbBufferReconfigPending() ? "Applying…" : "Active";
        } else {
            final String detail = status.detail != null && !status.detail.isEmpty()
                    ? " — " + status.detail
                    : "";
            phase = status.shortLabel() + detail;
        }
        if (monitor instanceof HasLiveDisplayEventCap) {
            final int render = monitor.getAEBufferSize();
            final int keep = ((HasLiveDisplayEventCap) monitor).getLiveDisplayEventCap();
            final int effective = Math.min(render, keep);
            statusLabel.setText(String.format("Status: %s; keep %s/frame",
                    phase, intFormat.format(effective)));
        } else {
            statusLabel.setText("Status: " + phase);
        }
    }

    private void updateAllocationLabel() {
        if (!fifoSpinner.isEnabled()) {
            allocationLabel.setText("Total: —");
            return;
        }
        final long fifo = ((Number) fifoSpinner.getValue()).longValue();
        final long buffers = ((Number) buffersSpinner.getValue()).longValue();
        final long total = fifo * buffers;
        final boolean overCap = total > UsbReaderBufferSettings.MAX_TOTAL_USB_BUFFER_BYTES;
        allocationLabel.setText(String.format("Total: %s bytes%s",
                intFormat.format(total), overCap ? " (over cap)" : ""));
        allocationLabel.setToolTipText(overCap
                ? "Over the 8 MiB host buffer cap; the driver will clamp this."
                : null);
    }

    private void bindStatisticsCollection() {
        final HasUsbStatistics next = viewer.aemon instanceof HasUsbStatistics
                ? (HasUsbStatistics) viewer.aemon
                : null;
        if (boundStats != next) {
            if (boundStats != null) {
                boundStats.setShowUsbStatistics(false);
            }
            boundStats = next;
            if (boundStats != null) {
                boundStats.setShowUsbStatistics(true);
            }
        } else if (boundStats != null && !boundStats.isShowUsbStatistics()) {
            boundStats.setShowUsbStatistics(true);
        }
    }

    private void unbindStatisticsCollection() {
        if (boundStats != null) {
            boundStats.setShowUsbStatistics(false);
            boundStats = null;
        }
    }

    private void pollStatisticsTable() {
        final HasUsbStatistics current = viewer.aemon instanceof HasUsbStatistics
                ? (HasUsbStatistics) viewer.aemon
                : null;
        if (current != boundStats) {
            bindStatisticsCollection();
        }
        if (boundStats == null) {
            fillStatsDashes();
            statsStatusLabel.setText(viewer.aemon == null
                    ? "USB IN: no device open"
                    : "USB IN: this interface does not report transfer sizes");
            return;
        }
        applySnapshotToTable(boundStats.takeUsbStatisticsSnapshot());
    }

    private void applySnapshotToTable(USBPacketStatistics.Snapshot s) {
        if (statsModel == null) {
            return;
        }
        if (s == null || !s.ready) {
            fillStatsDashes();
            statsStatusLabel.setText("USB IN: collecting… (1 s)");
            return;
        }
        float fill = s.fillFraction();
        lastFillFraction = fill;
        lastErrorCount = s.errorCount;
        setStat(ROW_FIFO, exactBytes(s.fifoSizeBytes));
        setStat(ROW_BUFFERS, s.numBuffers > 0 ? intFormat.format(s.numBuffers) : DASH);
        setStat(ROW_FILL, Float.isNaN(fill) ? DASH : Math.round(100 * fill) + "%");
        setStat(3, exactBytes(s.avgPacketBytes));
        setStat(4, s.minBytes > 0 ? exactBytes(s.minBytes) : DASH);
        setStat(5, s.maxBytes > 0 ? exactBytes(s.maxBytes) : DASH);
        setStat(6, s.avgIntervalUs > 0 ? eng(s.avgIntervalUs * 1e-6) + "s" : DASH);
        setStat(ROW_THROUGHPUT, s.bytesPerSec > 0 ? eng(s.bytesPerSec) + "B/s" : DASH);
        setStat(8, intFormat.format(s.transfers) + "/s");
        setStat(9, intFormat.format(s.emptyCount) + "/s");
        setStat(10, intFormat.format(s.shortCount) + "/s");
        setStat(ROW_ERRORS, intFormat.format(s.errorCount) + "/s");
        setNote(s.hint);
        statsStatusLabel.setText(String.format("USB IN: 1 Hz  #%d  (%.2f s)", s.seq, s.elapsedS));
        if (statsTable != null) {
            statsTable.repaint();
        }
    }

    private void fillStatsDashes() {
        lastFillFraction = Float.NaN;
        lastErrorCount = 0;
        for (int i = 0; i < STAT_ROWS.length; i++) {
            setStat(i, DASH);
        }
        if (statsTable != null) {
            statsTable.setRowHeight(ROW_NOTE, 20);
            statsTable.repaint();
        }
    }

    private void setStat(int row, String value) {
        statsModel.setValueAt(value, row, 1);
    }

    private void setNote(String hint) {
        if (hint == null || hint.isEmpty()) {
            setStat(ROW_NOTE, DASH);
            if (statsTable != null) {
                statsTable.setRowHeight(ROW_NOTE, 20);
            }
            return;
        }
        String wrapped = WordUtils.wrap(hint, NOTE_WRAP);
        int lines = wrapped.split("\n", -1).length;
        setStat(ROW_NOTE, "<html>" + wrapped.replace("\n", "<br>") + "</html>");
        if (statsTable != null) {
            statsTable.setRowHeight(ROW_NOTE, Math.max(20, lines * 18 + 6));
        }
    }

    private String exactBytes(double n) {
        if (n <= 0) {
            return DASH;
        }
        return intFormat.format(Math.round(n)) + " B";
    }

    private String eng(double n) {
        return engFmt.format(n).trim();
    }

    private void resubscribe() {
        unsubscribe();
        final AEMonitorInterface monitor = viewer.aemon;
        if (!(monitor instanceof ReaderBufferControl)) {
            return;
        }
        subscribedSupport = ((ReaderBufferControl) monitor).getReaderSupport();
        if (subscribedSupport != null) {
            subscribedSupport.addPropertyChangeListener(UsbAsyncBulkReaderLifecycle.EVENT_CONFIG_PENDING, this);
            subscribedSupport.addPropertyChangeListener(UsbAsyncBulkReaderLifecycle.EVENT_CONFIG_APPLIED, this);
            subscribedSupport.addPropertyChangeListener(UsbAsyncBulkReaderLifecycle.EVENT_CONFIG_STATUS, this);
            subscribedSupport.addPropertyChangeListener("readerStarted", this);
            subscribedSupport.addPropertyChangeListener("readerStopped", this);
            subscribedSupport.addPropertyChangeListener("liveDisplayEventCap", this);
        }
    }

    private void unsubscribe() {
        if (subscribedSupport != null) {
            subscribedSupport.removePropertyChangeListener(UsbAsyncBulkReaderLifecycle.EVENT_CONFIG_PENDING, this);
            subscribedSupport.removePropertyChangeListener(UsbAsyncBulkReaderLifecycle.EVENT_CONFIG_APPLIED, this);
            subscribedSupport.removePropertyChangeListener(UsbAsyncBulkReaderLifecycle.EVENT_CONFIG_STATUS, this);
            subscribedSupport.removePropertyChangeListener("readerStarted", this);
            subscribedSupport.removePropertyChangeListener("readerStopped", this);
            subscribedSupport.removePropertyChangeListener("liveDisplayEventCap", this);
            subscribedSupport = null;
        }
    }

    private void teardown() {
        if (applyTimer != null) {
            applyTimer.stop();
        }
        if (statsTimer != null) {
            statsTimer.stop();
        }
        unsubscribe();
        unbindStatisticsCollection();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!isDisplayable()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!isDisplayable()) {
                return;
            }
            final AEMonitorInterface monitor = viewer.aemon;
            if (monitor != boundMonitor) {
                deviceChanged();
                return;
            }
            if (!updatingUi && !applyTimer.isRunning()) {
                updatingUi = true;
                try {
                    if (monitor instanceof ReaderBufferControl) {
                        final ReaderBufferControl reader = (ReaderBufferControl) monitor;
                        fifoSpinner.setValue(Math.max(UsbReaderBufferSettings.MIN_FIFO_SIZE,
                                Math.min(UsbReaderBufferSettings.MAX_FIFO_SIZE, reader.getPendingFifoSize())));
                        buffersSpinner.setValue(Math.max(UsbReaderBufferSettings.MIN_NUM_BUFFERS,
                                Math.min(UsbReaderBufferSettings.MAX_NUM_BUFFERS, reader.getPendingNumBuffers())));
                    }
                    if (monitor instanceof HasLiveDisplayEventCap) {
                        final HasLiveDisplayEventCap keep = (HasLiveDisplayEventCap) monitor;
                        keepSpinner.setValue(Math.max(keep.getMinLiveDisplayEventCap(),
                                Math.min(keep.getMaxLiveDisplayEventCap(), keep.getLiveDisplayEventCap())));
                    }
                } finally {
                    updatingUi = false;
                }
            }
            refreshStatusLabels();
            updateAllocationLabel();
        });
    }
}
