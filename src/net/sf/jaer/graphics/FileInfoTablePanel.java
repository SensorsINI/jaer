package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import net.sf.jaer.eventio.aedat4.Aedat4FileInfo;

/**
 * File → Show file info body: one property/value table per recording, plus Copy.
 * Path cells show the start and end of the path; the tooltip and the clipboard
 * keep the full path.
 */
public final class FileInfoTablePanel extends JPanel {

    private static final int VALUE_COLUMN_WIDTH = 520;
    private static final int PROPERTY_COLUMN_WIDTH = 200;
    private static final Color ROW_ALT = new Color(0xF4F7FB);

    private final JPanel stack = new JPanel();
    private final JScrollPane scroll;
    private final JButton copyButton = new JButton("Copy");
    private final JButton closeButton = new JButton("Close");
    private List<Aedat4FileInfo.Section> sections = List.of();
    private Timer copiedTimer;

    public FileInfoTablePanel(Runnable onClose) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(stack, BorderLayout.NORTH);
        scroll = new JScrollPane(top);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);

        copyButton.setMnemonic(KeyEvent.VK_C);
        copyButton.setToolTipText("Copy the table as tab-separated text (full paths) to paste into a spreadsheet or a paper");
        closeButton.setToolTipText("Hide this window");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(copyButton);
        buttons.add(closeButton);
        add(buttons, BorderLayout.SOUTH);

        AbstractAction copyAction = new AbstractAction("Copy") {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyToClipboard();
            }
        };
        copyButton.addActionListener(copyAction);
        closeButton.addActionListener(e -> {
            if (onClose != null) {
                onClose.run();
            }
        });
        KeyStroke copyKey = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK);
        KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(copyKey, "copy-file-info");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(escape, "close-file-info");
        getActionMap().put("copy-file-info", copyAction);
        getActionMap().put("close-file-info", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (onClose != null) {
                    onClose.run();
                }
            }
        });
    }

    public JButton closeButton() {
        return closeButton;
    }

    public void setSections(List<Aedat4FileInfo.Section> next) {
        if (next == null || next.isEmpty()) {
            sections = List.of(Aedat4FileInfo.message(null, "File info is not available for this recording format."));
        } else {
            sections = List.copyOf(next);
        }
        rebuild();
        revalidate();
        repaint();
    }

    private void rebuild() {
        stack.removeAll();
        boolean first = true;
        int preferredWidth = PROPERTY_COLUMN_WIDTH + VALUE_COLUMN_WIDTH + 28;
        for (Aedat4FileInfo.Section section : sections) {
            if (section == null || section.rows.isEmpty()) {
                continue;
            }
            if (!first) {
                stack.add(Box.createVerticalStrut(14));
            }
            first = false;
            if (section.title != null && !section.title.isEmpty()) {
                JLabel title = new JLabel(section.title);
                title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
                title.setAlignmentX(Component.LEFT_ALIGNMENT);
                title.setBorder(BorderFactory.createEmptyBorder(2, 2, 4, 2));
                stack.add(title);
            }
            JScrollPane tableScroll = tableScroll(section);
            tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            stack.add(tableScroll);
            preferredWidth = Math.max(preferredWidth, tableScroll.getPreferredSize().width);
        }
        int height = 0;
        for (Component child : stack.getComponents()) {
            Dimension pref = child.getPreferredSize();
            height += pref.height;
        }
        if (height < 80) {
            height = 80;
        }
        scroll.setPreferredSize(new Dimension(preferredWidth, Math.min(height, 640)));
    }

    private JScrollPane tableScroll(Aedat4FileInfo.Section section) {
        InfoModel model = new InfoModel(section.rows);
        JTable table = new JTable(model);
        table.setFont(table.getFont().deriveFont(14f));
        table.setRowHeight(24);
        table.setShowGrid(true);
        table.setGridColor(new Color(0xD5DDE6));
        table.setBackground(Color.WHITE);
        table.setForeground(Color.BLACK);
        table.setSelectionBackground(new Color(0xD6E4F5));
        table.setSelectionForeground(Color.BLACK);
        table.setFillsViewportHeight(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD));
        table.setDefaultRenderer(Object.class, new InfoRenderer(model));
        table.getColumnModel().getColumn(0).setMinWidth(150);
        table.getColumnModel().getColumn(0).setPreferredWidth(PROPERTY_COLUMN_WIDTH);
        table.getColumnModel().getColumn(0).setWidth(PROPERTY_COLUMN_WIDTH);
        table.getColumnModel().getColumn(1).setMinWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(VALUE_COLUMN_WIDTH);
        table.getColumnModel().getColumn(1).setWidth(VALUE_COLUMN_WIDTH);
        KeyStroke copyKey = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK);
        table.getInputMap(JComponent.WHEN_FOCUSED).put(copyKey, "copy-file-info");
        table.getActionMap().put("copy-file-info", getActionMap().get("copy-file-info"));

        int header = table.getTableHeader().getPreferredSize().height;
        if (header < 18) {
            header = 26;
        }
        int tableHeight = header + table.getRowHeight() * model.getRowCount() + 4;
        JScrollPane pane = new JScrollPane(table);
        pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(BorderFactory.createLineBorder(new Color(0xC5CED6)));
        Dimension size = new Dimension(PROPERTY_COLUMN_WIDTH + VALUE_COLUMN_WIDTH + 24, tableHeight);
        pane.setPreferredSize(size);
        pane.setMinimumSize(new Dimension(480, tableHeight));
        pane.setMaximumSize(new Dimension(Integer.MAX_VALUE, tableHeight));
        return pane;
    }

    private void copyToClipboard() {
        String tsv = Aedat4FileInfo.toTabSeparated(sections);
        String html = Aedat4FileInfo.toHtml(sections);
        boolean ok = true;
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new HtmlAndText(tsv, html), null);
        } catch (IllegalStateException ex) {
            ok = false;
        }
        copyButton.setText(ok ? "Copied" : "Copy failed");
        if (copiedTimer == null) {
            copiedTimer = new Timer(1400, e -> copyButton.setText("Copy"));
            copiedTimer.setRepeats(false);
        }
        copiedTimer.restart();
    }

    private static String htmlTip(String text) {
        return "<html><body style='width:480px'>" + Aedat4FileInfo.escapeHtml(text) + "</body></html>";
    }

    private static final class InfoModel extends AbstractTableModel {
        private final List<Aedat4FileInfo.Row> rows;

        InfoModel(List<Aedat4FileInfo.Row> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Property" : "Value";
        }

        @Override
        public Object getValueAt(int row, int column) {
            Aedat4FileInfo.Row r = rows.get(row);
            return column == 0 ? r.property : r.value;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        Aedat4FileInfo.Row row(int row) {
            return rows.get(row);
        }
    }

    private static final class InfoRenderer extends DefaultTableCellRenderer {
        private final InfoModel model;

        InfoRenderer(InfoModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!(c instanceof JLabel label)) {
                return c;
            }
            Aedat4FileInfo.Row info = model.row(table.convertRowIndexToModel(row));
            String tip = info.path ? info.value : info.tooltip;
            label.setToolTipText(tip == null || tip.isEmpty() ? null : htmlTip(tip));
            if (column == 1 && info.path) {
                int w = table.getColumnModel().getColumn(column).getWidth();
                if (w < 80) {
                    w = table.getColumnModel().getColumn(column).getPreferredWidth();
                }
                label.setText(Aedat4FileInfo.truncatePath(info.value, label.getFontMetrics(label.getFont()),
                        Math.max(40, w - 12)));
            }
            if (!isSelected) {
                label.setForeground(Color.BLACK);
                label.setBackground((row % 2 == 0) ? Color.WHITE : ROW_ALT);
            }
            label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            return label;
        }
    }

    private static final class HtmlAndText implements Transferable {
        private static final DataFlavor HTML_FLAVOR = new DataFlavor(
                "text/html;charset=utf-8;class=java.lang.String", "HTML");
        private final String plain;
        private final String html;

        HtmlAndText(String plain, String html) {
            this.plain = plain == null ? "" : plain;
            this.html = html == null ? "" : html;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DataFlavor.stringFlavor, HTML_FLAVOR, DataFlavor.allHtmlFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.stringFlavor.equals(flavor) || flavor.isMimeTypeEqual("text/html");
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (DataFlavor.stringFlavor.equals(flavor)) {
                return plain;
            }
            if (flavor.isMimeTypeEqual("text/html")) {
                return html;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
