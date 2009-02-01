/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.awt.Color;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Handles logging messages for AEViewer status line.
 *
 * @author tobi
 */
public class AEViewerStatusHandler extends java.util.logging.Handler {

    AEViewer viewer;
    public AEViewerStatusHandler(AEViewer v){
        viewer=v;
    }

    @Override
    public void publish(LogRecord record) {
        String message = null;
        if (isLoggable(record)) {
            try {
                message=getFormatter().format(record);
//                message = record.getMessage(); // getFormatter().format(record);
            } catch (Exception e) {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
                return;
            }
            try {
                viewer.setStatusMessage(message);
                if(record.getLevel().intValue()>=Level.WARNING.intValue()){
                    viewer.setStatusColor(Color.red);
                }else{
                    viewer.setStatusColor(Color.black);
                }
            } catch (Exception e) {
                reportError(null, e, ErrorManager.WRITE_FAILURE);
            }
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }
}

