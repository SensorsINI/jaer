package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import net.sf.jaer.Help;
import net.sf.jaer.JaerConstants;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.hardwareinterface.usb.HasUsbStatistics;
import net.sf.jaer.hardwareinterface.usb.LibUsbLinkInfo;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.USBPacketStatistics;
import net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycle;
import net.sf.jaer.hardwareinterface.usb.UsbReaderBufferSettings;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.HtmlHelpFrame;
import net.sf.jaer.util.WindowSaver;

/**
 * Separate top-level window for live USB FIFO / buffer count / AE render-packet
 * size. Spinner and typed edits
 * auto-apply after a short pause so touchpad / arrow-key adjustments stay usable
 * while the camera runs. The USB link block is the negotiated speed captured
 * when the camera was opened. While this window is open, USB IN transfer
 * statistics are collected and shown in the table (~1 s windows).
 * {@link WindowSaver.DontResize} keeps {@link #pack()} size; last position may
 * still restore. {@link WindowSaver} applies bounds on a later EDT turn than
 * {@code WINDOW_OPENED}, so this frame packs again after that restore.
 */
@Help("""
<html>
<body>
<h2>USB tuning</h2>
<p>USB &rarr; USB tuning sets how jAER reads the camera and how many events one display frame can hold.
Edits apply a moment after you stop changing a field, while the camera is running.</p>
<h3>Controls</h3>
<ul>
<li><b>FIFO bytes</b> &mdash; size of each host USB read (4&nbsp;KiB to 2&nbsp;MiB).
The camera ends a transfer when its own chunk is finished, or when this buffer is full.
A larger FIFO does not make the camera send larger packets.
NRV is fixed at 64&nbsp;KiB: the camera ends each read at 65,504 bytes, so a larger host buffer stays empty.</li>
<li><b>Buffers</b> &mdash; how many of those reads are queued at once (1&ndash;32).
This covers a short pause in jAER. It does not change packet size.
FIFO &times; buffers is limited to 8&nbsp;MB.</li>
<li><b>Render events</b> &mdash; how many polarity events one display frame can hold.
If it fills, events are dropped even when USB itself is fine.
Increase it when the display says events are dropping.</li>
</ul>
<h3>USB link</h3>
<p>Negotiated speed from when the camera was opened, the device USB version (bcdUSB),
bus, address, port, and the parent hub speed. Editing FIFO or buffers does not change it.
10&nbsp;Gbit/s can be one 10&nbsp;Gbit/s lane or two 5&nbsp;Gbit/s lanes.
20&nbsp;Gbit/s is USB 3.2 Gen2x2.</p>
<h3>USB IN statistics</h3>
<p>The table is the last second of completed USB reads. Numbers are collected only while this window is open.
Each completed read is added to that window. Once a second the table shows the window and a new one starts.
Nothing is averaged across seconds, or since the camera was opened.
<b>Refresh</b> reloads the controls on the left from the camera and clears this window.
The table stays blank until the next second of reads.</p>
<ul>
<li><b>Fill</b> &mdash; average completed size divided by FIFO.
The note &ldquo;FIFO full&rdquo; means that average is at least 85%.
It is not a report that the camera overflowed.
A low fill means the camera sent less than the FIFO you requested.</li>
<li><b>Avg read size</b> &mdash; mean bytes in each completed read.</li>
<li><b>Interval</b> &mdash; average time between completed reads.</li>
<li><b>Throughput</b> &mdash; bytes per second.</li>
<li><b>Effective</b> &mdash; that throughput in megabits or gigabits per second (bytes/s × 8).
This is the measured payload rate, not the negotiated link speed.</li>
<li><b>Completions</b> &mdash; completed reads per second.</li>
<li><b>Empty</b> &mdash; reads that contained no data.</li>
<li><b>Short</b> &mdash; reads shorter than the FIFO. Normal when the camera finishes early.</li>
<li><b>Errors</b> &mdash; failed USB reads.
Zero errors does not prove every sensor event was kept.
NRV does not put an overflow marker in the USB stream.</li>
</ul>
<h3>How a one-second window is computed</h3>
<ul>
<li><b>Avg read size</b> &mdash; sum of bytes in non-empty reads, divided by how many of those reads completed.</li>
<li><b>Interval</b> &mdash; window length divided by (completions &minus; 1).</li>
<li><b>Throughput</b> &mdash; total bytes in the window divided by the window length.</li>
<li><b>Effective</b> &mdash; throughput &times; 8, shown in Mbit/s or Gbit/s.</li>
<li><b>Completions</b>, <b>Empty</b>, <b>Short</b>, <b>Errors</b> &mdash; counts in the window divided by the window length, so the column says &ldquo;per second&rdquo;.</li>
<li><b>Fill</b> &mdash; avg read size divided by FIFO bytes.</li>
</ul>
<p>The line under the table is the window number and how long that window actually was (about 1&nbsp;s).</p>
<p>A red <b>(overrun)</b> on the status line means the display frame filled
and later events were discarded. Increase <b>Render events</b>,
or raise the DVS threshold or refractory period.</p>
</body>
</html>
""")
public class UsbTuningFrame extends JFrame implements PropertyChangeListener, WindowSaver.DontResize {

    private static final int UI_DEBOUNCE_MS = 350;
    private static final int STATS_POLL_MS = 1000;
    private static final int RENDER_MIN = 1 << 16;
    private static final int RENDER_MAX = 1 << 23;
    private static final int SPINNER_COLUMNS = 9;
    private static final int METRIC_COL_WIDTH = 124;
    private static final int VALUE_COL_WIDTH = 112;
    private static final int NOTE_PREF_WIDTH = 400;
    private static final int NOTE_PREF_HEIGHT = 56;
    private static final int TABLE_ROW_HEIGHT = 20;
    private static final String DASH = "—";
    private static final String USB_STATS_TIP = "<html>Each row is one second of completed USB reads."
            + " The window is shown, then cleared, once a second. Nothing carries over from the previous second."
            + "<br>Avg read size is the mean of non-empty reads. Throughput is total bytes / window length."
            + " Effective is that rate × 8."
            + "<br>Completions, empty, short, and errors are window counts / window length."
            + "<br>Refresh clears the window immediately.</html>";
    private static final int ROW_FIFO = 0;
    private static final int ROW_BUFFERS = 1;
    private static final int ROW_FILL = 2;
    private static final int ROW_AVG_READ = 3;
    private static final int ROW_INTERVAL = 4;
    private static final int ROW_THROUGHPUT = 5;
    private static final int ROW_EFFECTIVE = 6;
    private static final int ROW_COMPLETIONS = 7;
    private static final int ROW_EMPTY = 8;
    private static final int ROW_SHORT = 9;
    private static final int ROW_ERRORS = 10;
    private static final Color FILL_YELLOW = new Color(255, 230, 80);
    private static final Color FILL_ORANGE = new Color(255, 160, 40);
    private static final Color FILL_RED = new Color(220, 50, 50);
    private static final String[] STAT_ROWS = {
        "FIFO",
        "Buffers",
        "Fill",
        "Avg read size",
        "Interval",
        "Throughput",
        "Effective",
        "Completions",
        "Empty",
        "Short",
        "Errors"
    };
    private static final String[] STAT_TIPS = {
        "Host bulk-IN buffer (URB) size you set on the left. The camera may complete far less than this.",
        "Number of those URBs queued at once.",
        "Avg completed size / FIFO. A low fill means the camera finishes each read before the FIFO is full.",
        "Mean bytes per completed USB bulk IN in the last second. Not event count.",
        "Mean time between completed bulk INs.",
        "Bytes completed per second (size × completions).",
        "Throughput as bits per second (bytes/s × 8). Payload rate, not the negotiated link speed.",
        "Completed bulk INs per second.",
        "Completions with 0 bytes (ZLP).",
        "Completions shorter than FIFO (normal if the camera sends less than the URB).",
        "Failed USB transfers in the last second."
    };

    private final AEViewer viewer;
    private final NumberFormat intFormat = NumberFormat.getIntegerInstance();
    private final EngineeringFormat engFmt = new EngineeringFormat();

    private static final String GENERIC_FIFO_TIP =
            "Host USB FIFO bytes per async bulk transfer (4 KiB–2 MiB, powers of two).";
    private JLabel fifoLabel;
    private JSpinner fifoSpinner;
    private JSpinner buffersSpinner;
    private JSpinner renderSpinner;
    private JLabel requestedLabel;
    private JLabel activeLabel;
    private JLabel allocationLabel;
    private JLabel statusLabel;
    private JLabel statsStatusLabel;
    private JTextArea linkArea;
    private DefaultTableModel statsModel;
    private JTable statsTable;
    private JScrollPane statsScroll;
    private JTextArea noteArea;
    private JPanel notePanel;
    private float lastFillFraction = Float.NaN;
    private int lastErrorCount;

    private boolean updatingUi;
    private Timer applyTimer;
    private Timer statsTimer;
    private PropertyChangeSupport subscribedSupport;
    private AEMonitorInterface boundMonitor;
    private HasUsbStatistics boundStats;
    private HtmlHelpFrame helpFrame;

    public UsbTuningFrame(AEViewer viewer) {
        super("USB tuning" + (viewer != null && viewer.getTitle() != null ? " — " + viewer.getTitle() : ""));
        this.viewer = viewer;
        setName("UsbTuning");
        setDefaultCloseOperation(HIDE_ON_CLOSE);
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
            public void windowClosing(WindowEvent e) {
                teardown();
            }

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
        if (!isVisible()) {
            boolean firstShow = !isDisplayable();
            packToContent();
            if (firstShow) {
                setLocationRelativeTo(viewer);
                setVisible(true);
                WindowSaver.runAfterQueuedRestores(() -> {
                    packToContent();
                    if (!hasSavedUsbTuningOrigin()) {
                        setLocationRelativeTo(viewer);
                    }
                    WindowSaver.clampToScreen(this);
                });
            } else {
                setVisible(true);
            }
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
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension pref = getPreferredSize();
        int w = pref.width;
        int h = pref.height;
        if (usable != null) {
            w = Math.min(w, usable.width);
            h = Math.min(h, usable.height);
        }
        if (getWidth() != w || getHeight() != h) {
            setSize(w, h);
        }
        setMinimumSize(new Dimension(Math.min(pref.width, w), Math.min(pref.height, h)));
        WindowSaver.clampToScreen(this);
    }

    private static boolean hasSavedUsbTuningOrigin() {
        return JaerConstants.PREFS_ROOT.node("WindowSaver").get("UsbTuning.x", null) != null;
    }

    private void buildUi() {
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        linkArea = new JTextArea("No USB device open");
        linkArea.setEditable(false);
        linkArea.setOpaque(false);
        linkArea.setLineWrap(true);
        linkArea.setWrapStyleWord(true);
        linkArea.setRows(3);
        linkArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        linkArea.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        linkArea.setToolTipText("Negotiated USB link from when this camera was opened.");
        final JPanel linkPanel = new JPanel(new BorderLayout());
        linkPanel.setBorder(BorderFactory.createTitledBorder("USB link"));
        linkPanel.add(linkArea, BorderLayout.CENTER);
        root.add(linkPanel, BorderLayout.NORTH);

        final JPanel form = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;

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

        configureSpinnerEditor(fifoSpinner);
        configureSpinnerEditor(buffersSpinner);
        configureSpinnerEditor(renderSpinner);

        fifoSpinner.setToolTipText(GENERIC_FIFO_TIP);
        buffersSpinner.setToolTipText("Number of overlapped USB read buffers (1–32).");
        renderSpinner.setToolTipText("Polarity events kept per display frame. Later events are dropped until the next frame.");

        int row = 0;
        fifoLabel = new JLabel("FIFO bytes");
        fifoLabel.setToolTipText(GENERIC_FIFO_TIP);
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        form.add(fifoLabel, c);
        c.gridx = 1;
        form.add(fifoSpinner, c);
        row++;
        addRow(form, c, row++, "Buffers", buffersSpinner);
        addRow(form, c, row++, "Render events", renderSpinner);

        requestedLabel = new JLabel("Requested: —");
        activeLabel = new JLabel("Active: —");
        allocationLabel = new JLabel("Total: —");
        statusLabel = new JLabel("Status: —");

        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(requestedLabel, c);
        c.gridy = row++;
        form.add(activeLabel, c);
        c.gridy = row++;
        form.add(allocationLabel, c);
        c.gridy = row++;
        form.add(statusLabel, c);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        final JButton help = new JButton("Help");
        help.setToolTipText("How to use FIFO, buffers, the rendering buffer, and the USB IN statistics.");
        help.addActionListener(e -> toggleHelp());
        final JButton refresh = new JButton("Refresh");
        refresh.setToolTipText("<html>Reload FIFO, buffers, and render size from the camera,"
                + "<br>and clear the USB IN average so the next second starts from zero.</html>");
        refresh.addActionListener(e -> {
            refreshFromHardware();
            resetStatisticsAveraging();
        });
        final JButton close = new JButton("Close");
        close.addActionListener(e -> dispatchEvent(new WindowEvent(UsbTuningFrame.this, WindowEvent.WINDOW_CLOSING)));
        buttons.add(help);
        buttons.add(refresh);
        buttons.add(close);

        noteArea = new JTextArea(2, 20);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.setEditable(false);
        noteArea.setOpaque(false);
        noteArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        noteArea.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        noteArea.setToolTipText("Hint from USB fill vs FIFO.");
        noteArea.setPreferredSize(new Dimension(NOTE_PREF_WIDTH, NOTE_PREF_HEIGHT));
        notePanel = new JPanel(new BorderLayout());
        notePanel.setBorder(BorderFactory.createTitledBorder("Note"));
        notePanel.add(noteArea, BorderLayout.CENTER);
        notePanel.setPreferredSize(new Dimension(NOTE_PREF_WIDTH, NOTE_PREF_HEIGHT + 22));
        notePanel.setVisible(false);

        final JPanel left = new JPanel(new BorderLayout());
        left.add(form, BorderLayout.NORTH);
        final JPanel body = new JPanel(new GridBagLayout());
        final GridBagConstraints bc = new GridBagConstraints();
        bc.gridy = 0;
        bc.insets = new Insets(0, 0, 0, 0);
        bc.anchor = GridBagConstraints.NORTHWEST;
        bc.fill = GridBagConstraints.NONE;
        bc.weightx = 0;
        bc.weighty = 0;
        bc.gridx = 0;
        body.add(left, bc);
        bc.gridx = 1;
        bc.insets = new Insets(0, 12, 0, 0);
        body.add(buildStatsPanel(), bc);

        final JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(notePanel, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        final JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.add(body, BorderLayout.NORTH);
        root.add(bodyWrap, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        fifoSpinner.addChangeListener(this::onSpinnerChanged);
        buffersSpinner.addChangeListener(this::onSpinnerChanged);
        renderSpinner.addChangeListener(this::onSpinnerChanged);
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
        statsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        statsTable.setRowHeight(TABLE_ROW_HEIGHT);
        statsTable.setShowGrid(true);
        statsTable.setFillsViewportHeight(false);
        statsTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        statsTable.setCellSelectionEnabled(true);
        statsTable.getTableHeader().setReorderingAllowed(false);
        statsTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statsTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        statsTable.setToolTipText("<html>USB bulk IN completions for the last 1 s (updates at 1 Hz).<br>"
                + "Avg read size is bytes delivered when libusb finishes one read, not DVS events.<br>"
                + "Fill is that size divided by the host FIFO. Hover a row for details.</html>");
        TableColumn metricCol = statsTable.getColumnModel().getColumn(0);
        metricCol.setMinWidth(METRIC_COL_WIDTH);
        metricCol.setPreferredWidth(METRIC_COL_WIDTH);
        metricCol.setMaxWidth(METRIC_COL_WIDTH);
        TableColumn valueCol = statsTable.getColumnModel().getColumn(1);
        valueCol.setMinWidth(VALUE_COL_WIDTH);
        valueCol.setPreferredWidth(VALUE_COL_WIDTH);
        valueCol.setMaxWidth(VALUE_COL_WIDTH);
        StatsCellRenderer renderer = new StatsCellRenderer();
        metricCol.setCellRenderer(renderer);
        valueCol.setCellRenderer(renderer);

        statsScroll = new JScrollPane(statsTable);
        statsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        statsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        int tableW = METRIC_COL_WIDTH + VALUE_COL_WIDTH + 4;
        int tableH = STAT_ROWS.length * TABLE_ROW_HEIGHT + 28;
        Dimension tableSize = new Dimension(tableW, tableH);
        statsScroll.setPreferredSize(tableSize);
        statsScroll.setMinimumSize(tableSize);

        statsStatusLabel = new JLabel("USB IN: waiting for device");
        statsStatusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));

        final JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("USB IN statistics"));
        panel.setToolTipText(USB_STATS_TIP);
        panel.add(statsScroll, BorderLayout.CENTER);
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

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            boolean valueCol = column == 1;
            setHorizontalAlignment(valueCol ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setVerticalAlignment(SwingConstants.CENTER);
            if (row == ROW_FIFO || row == ROW_BUFFERS) {
                setFont(valueCol ? paramValueFont : paramFont);
            } else if (row == ROW_FILL || row == ROW_THROUGHPUT || row == ROW_EFFECTIVE) {
                setFont(valueCol ? criticalValueFont : criticalFont);
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
        form.add(spinner, c);
    }

    private static void configureSpinnerEditor(JSpinner spinner) {
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#,##0"));
        final JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        editor.getTextField().setColumns(SPINNER_COLUMNS);
        editor.getTextField().setHorizontalAlignment(SwingConstants.RIGHT);
        Dimension pref = spinner.getPreferredSize();
        spinner.setPreferredSize(pref);
        spinner.setMaximumSize(pref);
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
            buffersSpinner.setEnabled(hasReader);
            renderSpinner.setEnabled(monitor != null);

            if (monitor != null) {
                int render = monitor.getAEBufferSize();
                render = Math.max(RENDER_MIN, Math.min(RENDER_MAX, render));
                renderSpinner.setValue(render);
            }
            if (hasReader) {
                final ReaderBufferControl reader = (ReaderBufferControl) monitor;
                final int minFifo = reader.getMinFifoSize();
                final int maxFifo = reader.getMaxFifoSize();
                int fifo = reader.getPendingFifoSize();
                fifo = Math.max(minFifo, Math.min(maxFifo, fifo));
                applyFifoSpinnerRange(fifo, minFifo, maxFifo);
                final String tip = reader.getFifoSizeToolTip();
                final String fifoTip = tip != null ? tip : GENERIC_FIFO_TIP;
                fifoSpinner.setToolTipText(fifoTip);
                fifoLabel.setToolTipText(fifoTip);
                fifoSpinner.setEnabled(minFifo < maxFifo);
                int buffers = reader.getPendingNumBuffers();
                buffers = Math.max(UsbReaderBufferSettings.MIN_NUM_BUFFERS,
                        Math.min(UsbReaderBufferSettings.MAX_NUM_BUFFERS, buffers));
                buffersSpinner.setValue(buffers);
            } else {
                fifoSpinner.setEnabled(false);
                fifoSpinner.setToolTipText(GENERIC_FIFO_TIP);
                fifoLabel.setToolTipText(GENERIC_FIFO_TIP);
            }
        } finally {
            updatingUi = false;
        }
        refreshStatusLabels();
        updateAllocationLabel();
        refreshLinkInfo();
        if (isDisplayable()) {
            packToContent();
        }
    }

    /**
     * Puts {@code fifo} into the spinner, then tightens the model range.
     * {@link SpinnerNumberModel} rejects a value outside the current range.
     */
    private void applyFifoSpinnerRange(int fifo, int minFifo, int maxFifo) {
        final SpinnerNumberModel model = (SpinnerNumberModel) fifoSpinner.getModel();
        final int currentMin = ((Number) model.getMinimum()).intValue();
        final int currentMax = ((Number) model.getMaximum()).intValue();
        if (fifo < currentMin) {
            model.setMinimum(fifo);
        }
        if (fifo > currentMax) {
            model.setMaximum(fifo);
        }
        model.setValue(fifo);
        model.setMinimum(minFifo);
        model.setMaximum(maxFifo);
    }

    private void refreshLinkInfo() {
        if (viewer == null || viewer.aemon == null) {
            linkArea.setText("No USB device open");
            return;
        }
        final LibUsbLinkInfo.Snapshot snap = LibUsbLinkInfo.lastOpen();
        if (snap == null) {
            linkArea.setText("Link information was not captured for this open.");
            return;
        }
        linkArea.setText(snap.tuningText());
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
        statusLabel.setText("Status: " + phase);
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

    /**
     * Drop the current one-second USB IN window. The table stays blank until
     * the next poll fills a new window.
     */
    private void resetStatisticsAveraging() {
        if (viewer != null && viewer.aemon instanceof HasUsbStatistics && boundStats == null) {
            bindStatisticsCollection();
        }
        if (boundStats != null) {
            boundStats.resetUsbStatistics();
        }
        fillStatsDashes();
        if (statsStatusLabel == null) {
            return;
        }
        if (boundStats == null) {
            statsStatusLabel.setText(viewer == null || viewer.aemon == null
                    ? "USB IN: no device open"
                    : "USB IN: this interface does not report transfer sizes");
        } else {
            statsStatusLabel.setText("USB IN: collecting… (1 s)");
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
        setStat(ROW_AVG_READ, exactBytes(s.avgPacketBytes));
        setStat(ROW_INTERVAL, s.avgIntervalUs > 0 ? eng(s.avgIntervalUs * 1e-6) + "s" : DASH);
        setStat(ROW_THROUGHPUT, s.bytesPerSec > 0 ? eng(s.bytesPerSec) + "B/s" : DASH);
        setStat(ROW_EFFECTIVE, s.bytesPerSec > 0 ? bitRateLabel(s.bytesPerSec) : DASH);
        setStat(ROW_COMPLETIONS, intFormat.format(s.transfers) + "/s");
        setStat(ROW_EMPTY, intFormat.format(s.emptyCount) + "/s");
        setStat(ROW_SHORT, intFormat.format(s.shortCount) + "/s");
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
        setNote("");
        if (statsTable != null) {
            statsTable.repaint();
        }
    }

    /**
     * Bytes per second as megabits or gigabits per second ({@code bytes/s × 8}).
     */
    static String bitRateLabel(double bytesPerSec) {
        double mbit = bytesPerSec * 8.0 / 1.0e6;
        if (mbit >= 1000.0) {
            double gbit = mbit / 1000.0;
            if (Math.abs(gbit - Math.rint(gbit)) < 0.05) {
                return String.format("%.0f Gbit/s", gbit);
            }
            return String.format("%.2f Gbit/s", gbit);
        }
        if (mbit >= 10.0) {
            return String.format("%.0f Mbit/s", mbit);
        }
        if (mbit >= 0.05) {
            return String.format("%.1f Mbit/s", mbit);
        }
        return String.format("%.0f kbit/s", bytesPerSec * 8.0 / 1.0e3);
    }

    private void setStat(int row, String value) {
        statsModel.setValueAt(value, row, 1);
    }

    /** Show a wrapping bottom panel only when the sampler has a hint. */
    private void setNote(String hint) {
        if (notePanel == null || noteArea == null) {
            return;
        }
        boolean has = hint != null && !hint.isEmpty();
        noteArea.setText(has ? hint : "");
        if (notePanel.isVisible() == has) {
            return;
        }
        notePanel.setVisible(has);
        if (isDisplayable()) {
            packToContent();
        } else {
            getContentPane().revalidate();
            getContentPane().repaint();
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
        }
    }

    private void unsubscribe() {
        if (subscribedSupport != null) {
            subscribedSupport.removePropertyChangeListener(UsbAsyncBulkReaderLifecycle.EVENT_CONFIG_PENDING, this);
            subscribedSupport.removePropertyChangeListener(UsbAsyncBulkReaderLifecycle.EVENT_CONFIG_APPLIED, this);
            subscribedSupport.removePropertyChangeListener(UsbAsyncBulkReaderLifecycle.EVENT_CONFIG_STATUS, this);
            subscribedSupport.removePropertyChangeListener("readerStarted", this);
            subscribedSupport.removePropertyChangeListener("readerStopped", this);
            subscribedSupport = null;
        }
    }

    private void toggleHelp() {
        if (helpFrame != null && helpFrame.isDisplayable() && helpFrame.isVisible()) {
            helpFrame.setVisible(false);
            return;
        }
        String html = Help.Html.of(UsbTuningFrame.class);
        if (html == null) {
            JOptionPane.showMessageDialog(this, "USB tuning help is missing.",
                    "Help", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (helpFrame == null || !helpFrame.isDisplayable()) {
            helpFrame = new HtmlHelpFrame("USB tuning", this, "UsbTuningHelp", 640, 560);
        }
        helpFrame.setHtml(html);
        helpFrame.setVisible(true);
        helpFrame.toFront();
    }

    private void teardown() {
        if (helpFrame != null && helpFrame.isVisible()) {
            helpFrame.setVisible(false);
        }
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
                } finally {
                    updatingUi = false;
                }
            }
            refreshStatusLabels();
            updateAllocationLabel();
        });
    }
}
