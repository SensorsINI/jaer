/*
 * Guards JFileChooser save dialogs against stray recording-shortcut keystrokes.
 */
package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Prevents the recording shortcut key ({@code L} menu accelerator / {@code l}
 * button mnemonic) from replacing the preselected chip-datestamp filename in
 * a save dialog (JDK-6391688 / JDK-6493715).
 * <p>
 * The accelerator fires on {@code KEY_PRESSED}; the matching {@code KEY_TYPED}
 * often arrives after the dialog opens and lands in the filename field. This
 * guard defers opening the dialog until after that keystroke is processed,
 * suppresses residual {@code L}/{@code l} events briefly, and rewrites the
 * filename field text directly (pathless {@link JFileChooser#setSelectedFile}
 * often fails to update the Windows L&amp;F text field).
 * <p>
 * On macOS Aqua, the Save As filename row is a centered {@link FlowLayout} with
 * a 250px field ({@code AquaFileChooserUI}), which clips chip-datestamp names.
 * When the dialog opens, that row is left-aligned and stretched to the pane.
 */
public final class RecordingSaveDialogGuard {

    /** How long after dialog open to treat lone {@code l}/{@code L} as stray. */
    static final long STRAY_KEY_GUARD_MS = 1500;

    /** AquaFileChooserUI hard-codes the Save As field to this width. */
    static final int AQUA_SAVE_FILENAME_FIELD_WIDTH = 250;

    /** Horizontal inset matching Aqua's New Folder / Save button struts. */
    static final int AQUA_SAVE_FILENAME_INSET = 20;

    private static final String MAC_FILENAME_LAYOUT_KEY = "jaer.macSaveFilenameLaidOut";

    private RecordingSaveDialogGuard() {
    }

    /**
     * True when the filename is only repeated lowercase {@code l} characters
     * (the recording shortcut leaking into the field).
     */
    public static boolean isStrayRecordingShortcutFilename(String filename) {
        return filename != null && filename.matches("(?i)l+");
    }

    /**
     * Shows a save dialog with guards installed. Returns the dialog result from
     * {@link JFileChooser#showSaveDialog(Component)}.
     * <p>
     * When called on the EDT, opening is deferred via a secondary event loop so
     * the recording shortcut's {@code KEY_TYPED} is flushed before the filename
     * field takes focus.
     */
    public static int showSaveDialog(JFileChooser chooser, Component parent, String defaultBase) {
        if (SwingUtilities.isEventDispatchThread()) {
            final int[] result = {JFileChooser.CANCEL_OPTION};
            final java.awt.SecondaryLoop loop
                    = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            SwingUtilities.invokeLater(() -> {
                try {
                    result[0] = showSaveDialogNow(chooser, parent, defaultBase);
                } finally {
                    loop.exit();
                }
            });
            loop.enter(); // pump events: KEY_TYPED finishes, then dialog opens
            return result[0];
        }
        return showSaveDialogNow(chooser, parent, defaultBase);
    }

    private static int showSaveDialogNow(JFileChooser chooser, Component parent, String defaultBase) {
        restoreSelectedFilename(chooser, defaultBase);
        layoutMacSaveFilenameField(chooser);

        final long dialogOpenTimeMs = System.currentTimeMillis();
        final DocumentListener[] filenameDocumentListener = new DocumentListener[1];
        final Timer[] restoreTimer = new Timer[1];

        final PropertyChangeListener ancestorListener = evt -> {
            if (evt.getNewValue() != null) {
                SwingUtilities.invokeLater(() -> {
                    layoutMacSaveFilenameField(chooser);
                    restoreSelectedFilename(chooser, defaultBase);
                    installFilenameGuard(chooser, defaultBase, dialogOpenTimeMs, filenameDocumentListener);
                    if (restoreTimer[0] == null) {
                        restoreTimer[0] = new Timer(50, e -> {
                            if (System.currentTimeMillis() - dialogOpenTimeMs > STRAY_KEY_GUARD_MS) {
                                ((Timer) e.getSource()).stop();
                                return;
                            }
                            JTextField field = findFilenameTextField(chooser);
                            if (field != null && isStrayRecordingShortcutFilename(field.getText().trim())) {
                                restoreSelectedFilename(chooser, defaultBase);
                            }
                        });
                        restoreTimer[0].start();
                    }
                });
            } else if (filenameDocumentListener[0] != null) {
                JTextField filenameField = findFilenameTextField(chooser);
                if (filenameField != null) {
                    filenameField.getDocument().removeDocumentListener(filenameDocumentListener[0]);
                }
                filenameDocumentListener[0] = null;
            }
        };

        final KeyEventDispatcher strayKeySuppressor = event -> {
            if (System.currentTimeMillis() - dialogOpenTimeMs > STRAY_KEY_GUARD_MS) {
                return false;
            }
            int id = event.getID();
            if (id != KeyEvent.KEY_TYPED && id != KeyEvent.KEY_PRESSED && id != KeyEvent.KEY_RELEASED) {
                return false;
            }
            boolean isL = (id == KeyEvent.KEY_TYPED)
                    ? (event.getKeyChar() == 'l' || event.getKeyChar() == 'L')
                    : (event.getKeyCode() == KeyEvent.VK_L);
            if (!isL) {
                return false;
            }
            SwingUtilities.invokeLater(() -> restoreSelectedFilename(chooser, defaultBase));
            return true;
        };

        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.addKeyEventDispatcher(strayKeySuppressor);
        chooser.addPropertyChangeListener("ancestor", ancestorListener);
        try {
            return chooser.showSaveDialog(parent);
        } finally {
            if (restoreTimer[0] != null) {
                restoreTimer[0].stop();
            }
            chooser.removePropertyChangeListener("ancestor", ancestorListener);
            if (filenameDocumentListener[0] != null) {
                JTextField filenameField = findFilenameTextField(chooser);
                if (filenameField != null) {
                    filenameField.getDocument().removeDocumentListener(filenameDocumentListener[0]);
                }
            }
            focusManager.removeKeyEventDispatcher(strayKeySuppressor);
        }
    }

    /**
     * Writes {@code defaultBase} into the chooser selection and the visible
     * filename field (path = chooser current directory).
     */
    public static void restoreSelectedFilename(JFileChooser chooser, String defaultBase) {
        if (chooser == null || defaultBase == null || defaultBase.isEmpty()) {
            return;
        }
        File dir = chooser.getCurrentDirectory();
        File selected = (dir != null) ? new File(dir, defaultBase) : new File(defaultBase);
        chooser.setSelectedFile(selected);
        JTextField filenameField = findFilenameTextField(chooser);
        if (filenameField != null) {
            String current = filenameField.getText();
            if (!defaultBase.equals(current)) {
                filenameField.setText(defaultBase);
            }
            filenameField.setCaretPosition(defaultBase.length());
        }
    }

    /**
     * On macOS Aqua, left-align the Save As filename row and stretch the field
     * to the chooser pane width. No-op on other platforms.
     */
    public static void layoutMacSaveFilenameField(JFileChooser chooser) {
        if (chooser == null || !isMacOs()) {
            return;
        }
        JTextField field = findFilenameTextField(chooser);
        if (field != null) {
            stretchFilenameRowToPaneWidth(field);
        }
    }

    static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    /**
     * Rewrites Aqua's centered 250px Save As row so the label is on the left
     * and the text field fills the remaining pane width. Returns false if the
     * parent is not that Aqua row (or was already rewritten).
     */
    static boolean stretchFilenameRowToPaneWidth(JTextField filenameField) {
        if (filenameField == null) {
            return false;
        }
        if (Boolean.TRUE.equals(filenameField.getClientProperty(MAC_FILENAME_LAYOUT_KEY))) {
            return true;
        }
        Container parent = filenameField.getParent();
        if (parent == null || !isAquaSaveFilenameRow(parent, filenameField)) {
            return false;
        }
        JLabel label = findSaveFilenameLabel(parent);
        parent.removeAll();
        parent.setLayout(new BorderLayout(8, 0));
        if (parent instanceof JComponent jc) {
            jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            jc.setBorder(BorderFactory.createEmptyBorder(0, AQUA_SAVE_FILENAME_INSET, 0, AQUA_SAVE_FILENAME_INSET));
        }
        int h = Math.max(filenameField.getPreferredSize().height, filenameField.getMinimumSize().height);
        filenameField.setMinimumSize(new Dimension(120, h));
        filenameField.setPreferredSize(new Dimension(120, h));
        filenameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        if (label != null) {
            parent.add(label, BorderLayout.LINE_START);
        }
        parent.add(filenameField, BorderLayout.CENTER);
        if (parent instanceof JComponent jc) {
            jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(h + 4, jc.getPreferredSize().height)));
        }
        filenameField.putClientProperty(MAC_FILENAME_LAYOUT_KEY, Boolean.TRUE);
        parent.invalidate();
        parent.validate();
        parent.repaint();
        return true;
    }

    /**
     * Aqua {@code labelArea}: centered {@link FlowLayout} holding the Save As
     * label and a field capped at {@link #AQUA_SAVE_FILENAME_FIELD_WIDTH}.
     */
    static boolean isAquaSaveFilenameRow(Container parent, JTextField filenameField) {
        LayoutManager layout = parent.getLayout();
        if (!(layout instanceof FlowLayout flow) || flow.getAlignment() != FlowLayout.CENTER) {
            return false;
        }
        int maxW = filenameField.getMaximumSize().width;
        int prefW = filenameField.getPreferredSize().width;
        return maxW <= AQUA_SAVE_FILENAME_FIELD_WIDTH && prefW <= AQUA_SAVE_FILENAME_FIELD_WIDTH;
    }

    private static JLabel findSaveFilenameLabel(Container parent) {
        JLabel only = null;
        int labels = 0;
        for (Component c : parent.getComponents()) {
            if (!(c instanceof JLabel label)) {
                continue;
            }
            labels++;
            only = label;
            if (isFilenameChooserLabel(label.getText())) {
                return label;
            }
        }
        return labels == 1 ? only : null;
    }

    static boolean isFilenameChooserLabel(String text) {
        if (text == null) {
            return false;
        }
        String n = text.toLowerCase(Locale.ROOT);
        return n.contains("file name") || n.contains("filename") || n.contains("save as");
    }

    /**
     * Prefer the field next to a "File Name" / "Save As" label; else a field
     * matching the selected name; else the last {@link JTextField} (Windows
     * L&amp;F often puts the path field first).
     */
    static JTextField findFilenameTextField(Container parent) {
        JTextField afterFileNameLabel = findTextFieldAfterFileNameLabel(parent);
        if (afterFileNameLabel != null) {
            return afterFileNameLabel;
        }
        if (parent instanceof JFileChooser) {
            File sel = ((JFileChooser) parent).getSelectedFile();
            if (sel != null) {
                JTextField match = findTextFieldWithText(parent, sel.getName());
                if (match != null) {
                    return match;
                }
            }
        }
        return findLastTextField(parent);
    }

    private static JTextField findTextFieldAfterFileNameLabel(Container parent) {
        Component[] comps = parent.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (c instanceof JLabel) {
                String text = ((JLabel) c).getText();
                if (isFilenameChooserLabel(text)) {
                    for (int j = i + 1; j < comps.length; j++) {
                        if (comps[j] instanceof JTextField) {
                            return (JTextField) comps[j];
                        }
                        if (comps[j] instanceof Container) {
                            JTextField nested = findLastTextField((Container) comps[j]);
                            if (nested != null) {
                                return nested;
                            }
                        }
                    }
                }
            }
            if (c instanceof Container) {
                JTextField found = findTextFieldAfterFileNameLabel((Container) c);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JTextField findTextFieldWithText(Container parent, String expected) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JTextField) {
                JTextField tf = (JTextField) component;
                if (expected.equals(tf.getText())) {
                    return tf;
                }
            }
            if (component instanceof Container) {
                JTextField found = findTextFieldWithText((Container) component, expected);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JTextField findLastTextField(Container parent) {
        JTextField last = null;
        for (Component component : parent.getComponents()) {
            if (component instanceof JTextField) {
                last = (JTextField) component;
            } else if (component instanceof Container) {
                JTextField nested = findLastTextField((Container) component);
                if (nested != null) {
                    last = nested;
                }
            }
        }
        return last;
    }

    private static void installFilenameGuard(
            final JFileChooser chooser,
            final String defaultBase,
            final long dialogOpenTimeMs,
            DocumentListener[] listenerHolder) {
        if (listenerHolder[0] != null) {
            return;
        }
        final JTextField filenameField = findFilenameTextField(chooser);
        if (filenameField == null) {
            return;
        }
        if (isStrayRecordingShortcutFilename(filenameField.getText().trim())) {
            restoreSelectedFilename(chooser, defaultBase);
        }
        listenerHolder[0] = new DocumentListener() {
            private void maybeRestoreDefaultFilename() {
                if (System.currentTimeMillis() - dialogOpenTimeMs > STRAY_KEY_GUARD_MS) {
                    return;
                }
                if (isStrayRecordingShortcutFilename(filenameField.getText().trim())) {
                    SwingUtilities.invokeLater(() -> restoreSelectedFilename(chooser, defaultBase));
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                maybeRestoreDefaultFilename();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                maybeRestoreDefaultFilename();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                maybeRestoreDefaultFilename();
            }
        };
        filenameField.getDocument().addDocumentListener(listenerHolder[0]);
    }
}
