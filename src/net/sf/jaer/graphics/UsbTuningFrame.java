package net.sf.jaer.graphics;

import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
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
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;

import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.hardwareinterface.usb.HasLiveDisplayEventCap;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycle;
import net.sf.jaer.hardwareinterface.usb.UsbReaderBufferSettings;

/**
 * Separate top-level window for live USB FIFO / buffer count / AE render-packet
 * size (and Prophesee live keep limit when available). Spinner and typed edits
 * auto-apply after a short pause so touchpad / arrow-key adjustments stay usable
 * while the camera runs.
 */
public class UsbTuningFrame extends JFrame implements PropertyChangeListener {

    private static final int UI_DEBOUNCE_MS = 350;
    private static final int RENDER_MIN = 1 << 16;
    private static final int RENDER_MAX = 1 << 23;

    private final AEViewer viewer;
    private final NumberFormat intFormat = NumberFormat.getIntegerInstance();

    private JSpinner fifoSpinner;
    private JSpinner buffersSpinner;
    private JSpinner renderSpinner;
    private JSpinner keepSpinner;
    private JLabel keepLabel;
    private JLabel requestedLabel;
    private JLabel activeLabel;
    private JLabel allocationLabel;
    private JLabel statusLabel;

    private boolean updatingUi;
    private Timer applyTimer;
    private PropertyChangeSupport subscribedSupport;
    private AEMonitorInterface boundMonitor;

    public UsbTuningFrame(AEViewer viewer) {
        super("USB tuning" + (viewer != null && viewer.getTitle() != null ? " — " + viewer.getTitle() : ""));
        this.viewer = viewer;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        if (viewer != null && viewer.getIconImage() != null) {
            setIconImage(viewer.getIconImage());
        }
        buildUi();

        applyTimer = new Timer(UI_DEBOUNCE_MS, this::applyPendingEdits);
        applyTimer.setRepeats(false);

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
        packToContent();
        if (!isVisible()) {
            setLocationRelativeTo(viewer);
            setVisible(true);
            // Windows: decorations exist only after the peer is created.
            packToContent();
            setLocationRelativeTo(viewer);
        }
        toFront();
        requestFocus();
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
        refresh.addActionListener(e -> refreshFromHardware());
        final JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        buttons.add(refresh);
        buttons.add(close);

        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        fifoSpinner.addChangeListener(this::onSpinnerChanged);
        buffersSpinner.addChangeListener(this::onSpinnerChanged);
        renderSpinner.addChangeListener(this::onSpinnerChanged);
        keepSpinner.addChangeListener(this::onSpinnerChanged);
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
        unsubscribe();
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
                refreshFromHardware();
                resubscribe();
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
