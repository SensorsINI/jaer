/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.awt.Color;
import java.awt.EventQueue;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import net.sf.jaer.graphics.AEViewerConsoleOutputFrame;

/**
 * Handles logging messages for AEViewer status line.
 *
 * @author tobi
 */
public class AEViewerLoggingHandler extends java.util.logging.Handler {

    private final AEViewer viewer;
    private final AEViewerConsoleOutputFrame consoleWindow;
    private final Formatter consoleFormatter;

    public AEViewerLoggingHandler(final AEViewer v) {
        viewer = v;
        statusFormatter = new AEViewerStatusFormatter();
        consoleFormatter = new SimpleFormatter();
        setFormatter(new AEViewerStatusFormatter());
        consoleWindow = new AEViewerConsoleOutputFrame();
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
            final String smsg = statusMessage,  cmsg = consoleMessage;
            Runnable r = new Runnable() {

                public void run() {
                    try {
                        viewer.setStatusMessage(smsg);
                        if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                            viewer.setStatusColor(Color.red);
                        } else {
                            viewer.setStatusColor(Color.black);
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
}

