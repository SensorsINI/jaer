/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.awt.Color;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * Handles logging messages for AEViewer status line. Passes on
 * PropertyChangeEvents from AEViewerConsoleOutputFrame.
 *
 * @author tobi
 */
public class AEViewerLoggingHandler extends java.util.logging.Handler implements PropertyChangeListener {

    private final AEViewer viewer;
    private final AEViewerConsoleOutputFrame consoleWindow;
    private final Formatter consoleFormatter;
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    public AEViewerLoggingHandler(final AEViewer v) {
        viewer = v;
        consoleFormatter = new AEConsoleFormatter();
        setFormatter(new AEViewerStatusFormatter());
        consoleWindow = new AEViewerConsoleOutputFrame();
        consoleWindow.getSupport().addPropertyChangeListener(this);
        // Handler default is ALL. Read conf/Logging.properties; fall back to INFO.
        // LogManager.getLevelProperty is package-private; use the public getProperty API.
        setLevel(readConfiguredLevel(getClass().getName() + ".level", Level.INFO));
    }

    /** Reads a JUL level from LogManager properties ({@code getLevelProperty} is package-private). */
    private static Level readConfiguredLevel(String propertyName, Level fallback) {
        String value = LogManager.getLogManager().getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Level.parse(value.trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public AEViewerConsoleOutputFrame getConsoleWindow() {
        return consoleWindow;
    }

    @Override
    public void publish(final LogRecord record) {
        String statusMessage = null, consoleMessage = null;
        if (isLoggable(record)) {
            try {
                statusMessage = getFormatter().format(record);
                consoleMessage = consoleFormatter.format(record);
//                message = record.getMessage(); // getFormatter().format(record);
            } catch (Exception e) {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
                return;
            }
            final String smsg = statusMessage, cmsg = consoleMessage;
            Runnable r = new Runnable() {

                @Override
                public void run() {
                    try {
                        viewer.setStatusMessage(smsg);
                        if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                            viewer.setStatusColor(Color.red);
                        } else if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                            viewer.setStatusColor(Color.green.darker().darker());
                        } else {
                            viewer.setStatusColor(Color.gray);
                        }
                        consoleWindow.append(cmsg, record.getLevel());
                    } catch (Exception e) {
                        reportError(null, e, ErrorManager.WRITE_FAILURE);
                    }
                }
            };
            EventQueue.invokeLater(r);
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }

    /**
     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        support.firePropertyChange(evt);
    }
}
