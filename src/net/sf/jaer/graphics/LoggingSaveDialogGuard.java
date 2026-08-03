/*
 * Guards JFileChooser save dialogs against stray logging-shortcut keystrokes.
 */
package net.sf.jaer.graphics;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Prevents the logging shortcut key ({@code L} menu accelerator / {@code l}
 * button mnemonic) from replacing the preselected chip-datestamp filename in
 * a save dialog (JDK-6391688 / JDK-6493715).
 * <p>
 * The accelerator fires on {@code KEY_PRESSED}; the matching {@code KEY_TYPED}
 * often arrives after the dialog opens and lands in the filename field. This
 * guard defers opening the dialog until after that keystroke is processed,
 * suppresses residual {@code L}/{@code l} events briefly, and rewrites the
 * filename field text directly (pathless {@link JFileChooser#setSelectedFile}
 * often fails to update the Windows L&amp;F text field).
 */
public final class LoggingSaveDialogGuard {

    /** How long after dialog open to treat lone {@code l}/{@code L} as stray. */
    private static final long STRAY_KEY_GUARD_MS = 1500;

    private LoggingSaveDialogGuard() {
    }

    /**
     * True when the filename is only repeated lowercase {@code l} characters
     * (the logging shortcut leaking into the field).
     */
    public static boolean isStrayLoggingShortcutFilename(String filename) {
        return filename != null && filename.matches("(?i)l+");
    }

    /**
     * Shows a save dialog with guards installed. Returns the dialog result from
     * {@link JFileChooser#showSaveDialog(Component)}.
     * <p>
     * When called on the EDT, opening is deferred via a secondary event loop so
     * the logging shortcut's {@code KEY_TYPED} is flushed before the filename
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

        final long dialogOpenTimeMs = System.currentTimeMillis();
        final DocumentListener[] filenameDocumentListener = new DocumentListener[1];
        final Timer[] restoreTimer = new Timer[1];

        final PropertyChangeListener ancestorListener = evt -> {
            if (evt.getNewValue() != null) {
                SwingUtilities.invokeLater(() -> {
                    restoreSelectedFilename(chooser, defaultBase);
                    installFilenameGuard(chooser, defaultBase, dialogOpenTimeMs, filenameDocumentListener);
                    if (restoreTimer[0] == null) {
                        restoreTimer[0] = new Timer(50, e -> {
                            if (System.currentTimeMillis() - dialogOpenTimeMs > STRAY_KEY_GUARD_MS) {
                                ((Timer) e.getSource()).stop();
                                return;
                            }
                            JTextField field = findFilenameTextField(chooser);
                            if (field != null && isStrayLoggingShortcutFilename(field.getText().trim())) {
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
     * Prefer the field next to a "File Name" label; else a field matching the
     * selected name; else the last {@link JTextField} (Windows L&amp;F often
     * puts the path field first).
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
                if (text != null && text.toLowerCase().contains("file name")) {
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
        if (isStrayLoggingShortcutFilename(filenameField.getText().trim())) {
            restoreSelectedFilename(chooser, defaultBase);
        }
        listenerHolder[0] = new DocumentListener() {
            private void maybeRestoreDefaultFilename() {
                if (System.currentTimeMillis() - dialogOpenTimeMs > STRAY_KEY_GUARD_MS) {
                    return;
                }
                if (isStrayLoggingShortcutFilename(filenameField.getText().trim())) {
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
