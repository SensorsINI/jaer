/*
 * Guards JFileChooser save dialogs against stray logging-shortcut keystrokes.
 */
package net.sf.jaer.graphics;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Prevents the logging shortcut key ({@code L} menu accelerator / {@code l}
 * button mnemonic) from replacing the preselected chip-datestamp filename in
 * a save dialog (JDK-6391688 / JDK-6493715).
 */
public final class LoggingSaveDialogGuard {

    private static final long STRAY_KEY_GUARD_MS = 500;

    private LoggingSaveDialogGuard() {
    }

    /**
     * True when the filename is only repeated lowercase {@code l} characters.
     */
    public static boolean isStrayLoggingShortcutFilename(String filename) {
        return filename != null && filename.matches("l+");
    }

    /**
     * Shows a save dialog with guards installed. Returns the dialog result from
     * {@link JFileChooser#showSaveDialog(Component)}.
     */
    public static int showSaveDialog(JFileChooser chooser, Component parent, String defaultBase) {
        final long dialogOpenTimeMs = System.currentTimeMillis();
        final DocumentListener[] filenameDocumentListener = new DocumentListener[1];
        final PropertyChangeListener ancestorListener = evt -> {
            if (evt.getNewValue() != null) {
                SwingUtilities.invokeLater(() -> installFilenameGuard(
                        chooser, defaultBase, dialogOpenTimeMs, filenameDocumentListener));
            } else if (filenameDocumentListener[0] != null) {
                JTextField filenameField = findFilenameTextField(chooser);
                if (filenameField != null) {
                    filenameField.getDocument().removeDocumentListener(filenameDocumentListener[0]);
                }
                filenameDocumentListener[0] = null;
            }
        };
        final KeyEventDispatcher strayKeyTypedSuppressor = event -> {
            if (event.getID() != KeyEvent.KEY_TYPED) {
                return false;
            }
            if (System.currentTimeMillis() - dialogOpenTimeMs > STRAY_KEY_GUARD_MS) {
                return false;
            }
            char keyChar = event.getKeyChar();
            if (keyChar != 'l' && keyChar != 'L') {
                return false;
            }
            JTextField filenameField = findFilenameTextField(chooser);
            if (filenameField == null || event.getComponent() != filenameField) {
                return false;
            }
            if (isStrayLoggingShortcutFilename(filenameField.getText())) {
                SwingUtilities.invokeLater(() -> chooser.setSelectedFile(new File(defaultBase)));
                return true;
            }
            return false;
        };

        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.addKeyEventDispatcher(strayKeyTypedSuppressor);
        chooser.addPropertyChangeListener("ancestor", ancestorListener);
        try {
            return chooser.showSaveDialog(parent);
        } finally {
            chooser.removePropertyChangeListener("ancestor", ancestorListener);
            if (filenameDocumentListener[0] != null) {
                JTextField filenameField = findFilenameTextField(chooser);
                if (filenameField != null) {
                    filenameField.getDocument().removeDocumentListener(filenameDocumentListener[0]);
                }
            }
            focusManager.removeKeyEventDispatcher(strayKeyTypedSuppressor);
        }
    }

    static JTextField findFilenameTextField(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JTextField) {
                return (JTextField) component;
            }
            if (component instanceof Container) {
                JTextField found = findFilenameTextField((Container) component);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
        if (isStrayLoggingShortcutFilename(filenameField.getText())) {
            chooser.setSelectedFile(new File(defaultBase));
        }
        listenerHolder[0] = new DocumentListener() {
            private void maybeRestoreDefaultFilename() {
                if (System.currentTimeMillis() - dialogOpenTimeMs > STRAY_KEY_GUARD_MS) {
                    return;
                }
                if (isStrayLoggingShortcutFilename(filenameField.getText())) {
                    chooser.setSelectedFile(new File(defaultBase));
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
